package mg.galaxam.sirh.paie.application;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.common.exception.SirhErrorCode;
import mg.galaxam.sirh.conge.testutils.CongeTestFixtureHelper;
import mg.galaxam.sirh.paie.api.dto.stc.AnnulationReglementRequest;
import mg.galaxam.sirh.paie.api.dto.stc.DossierSortieDTO;
import mg.galaxam.sirh.paie.api.dto.stc.DossierSortieSaveRequest;
import mg.galaxam.sirh.paie.api.dto.stc.ScellementStcRequest;
import mg.galaxam.sirh.paie.api.dto.stc.SimulationStcResultDTO;
import mg.galaxam.sirh.paie.api.dto.stc.StcReglementDTO;
import mg.galaxam.sirh.paie.api.dto.stc.StcReglementRequest;
import mg.galaxam.sirh.paie.api.dto.stc.StcSituationReglementDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Suite de tests d'intégration validant exhaustivement la Phase 3.1 :
 * 1. Le journal paie_stc_reglement est la source de vérité des paiements réels
 * 2. Paiements complets et paiements partiels/multiples en plusieurs tranches
 * 3. Calcul exact du montant total réglé et du reste à payer
 * 4. Verrouillage transactionnel pessimiste contre les sur-règlements concurrents
 * 5. Protection contre les doubles soumissions (référence active unique)
 * 6. Synchronisation atomique des statuts (paie_stc et paie_dossier_sortie)
 * 7. Traçabilité des corrections et annulations avec réouverture d'état
 * 8. Gestion explicite des STC à solde nul (net = 0 Ar)
 */
@SpringBootTest
@ActiveProfiles("test")
class StcReglementJournalIntegrationTest {

    @Autowired private DataSource dataSource;
    @Autowired private DossierSortieService dossierSortieService;
    @Autowired private StcService stcService;

    private record TestDossierScelle(int idDossier, int idStc, int idEmploye, int adminId, BigDecimal netScelle, LocalDate dateDepart) {}

    private Integer trouverPeriodePaieId(Connection c, LocalDate date) throws Exception {
        String sql = "SELECT id_periode_paie FROM periode_paie WHERE ? BETWEEN date_debut AND date_fin LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    private void assurerPeriodePaie(Connection c, LocalDate date) throws Exception {
        if (trouverPeriodePaieId(c, date) != null) return;
        LocalDate debut = date.getDayOfMonth() >= 26 ? date.withDayOfMonth(26) : date.minusMonths(1).withDayOfMonth(26);
        LocalDate fin = date.getDayOfMonth() >= 26 ? date.plusMonths(1).withDayOfMonth(25) : date.withDayOfMonth(25);
        String code = "P-" + debut + "-" + fin;
        if (code.length() > 20) code = code.substring(0, 20);
        String ins = "INSERT INTO periode_paie (code, libelle, date_debut, date_fin, id_statut_periode) " +
                "VALUES (?, ?, ?, ?, 1) ON CONFLICT DO NOTHING";
        try (PreparedStatement ps = c.prepareStatement(ins)) {
            ps.setString(1, code);
            ps.setString(2, "Période " + debut + " à " + fin);
            ps.setDate(3, Date.valueOf(debut));
            ps.setDate(4, Date.valueOf(fin));
            ps.executeUpdate();
        }
    }

    private TestDossierScelle creerEtScellerDossierTest(Connection c, String matricule, LocalDate dateDepart, BigDecimal joursTravailles) throws Exception {
        assurerPeriodePaie(c, dateDepart);
        int adminId = CongeTestFixtureHelper.createAdminUser(c, "rh_reg_" + System.currentTimeMillis() + "_" + Math.abs(matricule.hashCode()));
        int empId = CongeTestFixtureHelper.createEmploye(c, matricule, "TEST_REG", "Agent");
        int contratId = CongeTestFixtureHelper.createContratActif(c, empId, LocalDate.of(2023, 1, 1));
        CongeTestFixtureHelper.createAffectation(c, empId, contratId, LocalDate.of(2023, 1, 1), new BigDecimal("600000.00"));

        try (PreparedStatement ps = c.prepareStatement("UPDATE contrat_travail SET date_fin = ? WHERE id_contrat = ?")) {
            ps.setDate(1, Date.valueOf(dateDepart));
            ps.setInt(2, contratId);
            ps.executeUpdate();
        }

        // Création dossier
        DossierSortieSaveRequest saveReq = new DossierSortieSaveRequest();
        saveReq.setIdEmploye(empId);
        saveReq.setDateDepart(Date.valueOf(dateDepart));
        saveReq.setMotifRupture("DEMISSION");
        saveReq.setDecisionPret("MAINTIEN_ECHEANCIER");
        saveReq.setJustificationDecisionPret("Aucun prêt");

        Map<String, Object> prep = new HashMap<>();
        prep.put("joursTravaillesDernierMois", joursTravailles.toPlainString());
        prep.put("justificationSalaireDernierMois", "Présence effective");
        prep.put("montantBulletinDejaPaye", "0");
        prep.put("soldeConges", "0");
        saveReq.setDonneesPreparation(prep);

        DossierSortieDTO dossier = dossierSortieService.sauvegarderDossier(saveReq, adminId, c);
        SimulationStcResultDTO sim = dossierSortieService.simulerStcDepuisDossier(dossier.getIdDossierSortie(), c);

        ScellementStcRequest sealReq = new ScellementStcRequest(sim.getReferenceSimulation(), sim.getVersion(), "Scellement pour test règlements");
        DossierSortieDTO scelle = dossierSortieService.scellerDossierEtStc(dossier.getIdDossierSortie(), sealReq, adminId, c);

        return new TestDossierScelle(scelle.getIdDossierSortie(), scelle.getIdStc(), empId, adminId, scelle.getNetAPayerStc(), dateDepart);
    }

    @Test
    @DisplayName("Règlement intégral en une seule fois : écriture dans paie_stc_reglement, reste=0 et bascule synchrone à REGLE")
    void testReglementIntegralUneFois() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                String mat = "EMP-REG-INT-" + System.currentTimeMillis() % 100000;
                LocalDate dDep = LocalDate.of(2026, 9, 25);
                var testDossier = creerEtScellerDossierTest(c, mat, dDep, new BigDecimal("30"));

                assertTrue(testDossier.netScelle().compareTo(BigDecimal.ZERO) > 0, "Le net scellé doit être strictement positif");

                // Enregistrement du règlement total
                StcReglementRequest req = new StcReglementRequest(
                        Date.valueOf(dDep),
                        "VIR-TEST-INT-001",
                        testDossier.netScelle(),
                        "VIREMENT",
                        "Règlement intégral de fin de contrat"
                );

                StcSituationReglementDTO sit = stcService.enregistrerReglement(testDossier.idStc(), req, testDossier.adminId(), c);

                assertNotNull(sit);
                assertEquals(0, testDossier.netScelle().compareTo(sit.getTotalRegle()), "Le total réglé doit être exactement égal au net scellé");
                assertEquals(0, BigDecimal.ZERO.compareTo(sit.getResteAPayer()), "Le reste à payer doit être 0.00 Ar");
                assertTrue(sit.isEstIntegralementRegle());
                assertEquals("REGLE", sit.getStatutStc());
                assertEquals("REGLE", sit.getStatutDossier());
                assertEquals(1, sit.getReglements().size());

                StcReglementDTO rLine = sit.getReglements().get(0);
                assertEquals("VIR-TEST-INT-001", rLine.getReferenceReglement());
                assertEquals("VIREMENT", rLine.getModeReglement());
                assertFalse(rLine.isEstAnnule());

                // Vérification en BDD dans les deux tables
                try (PreparedStatement psStc = c.prepareStatement("SELECT statut, date_reglement, reference_reglement FROM paie_stc WHERE id_stc = ?")) {
                    psStc.setInt(1, testDossier.idStc());
                    try (ResultSet rs = psStc.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("REGLE", rs.getString("statut"));
                        assertEquals(Date.valueOf(dDep), rs.getDate("date_reglement"));
                        assertEquals("VIR-TEST-INT-001", rs.getString("reference_reglement"));
                    }
                }

                try (PreparedStatement psDos = c.prepareStatement("SELECT statut_dossier FROM paie_dossier_sortie WHERE id_dossier_sortie = ?")) {
                    psDos.setInt(1, testDossier.idDossier());
                    try (ResultSet rs = psDos.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("REGLE", rs.getString("statut_dossier"));
                    }
                }

                // Vérification de l'assemblage dans DossierSortieDTO
                DossierSortieDTO dtoConsulte = dossierSortieService.obtenirDossier(testDossier.idDossier(), c);
                assertEquals(0, testDossier.netScelle().compareTo(dtoConsulte.getMontantTotalRegle()));
                assertEquals(0, BigDecimal.ZERO.compareTo(dtoConsulte.getSoldeRestantAPayer()));
                assertTrue(dtoConsulte.isEstIntegralementRegle());

                c.rollback();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    @Test
    @DisplayName("Paiements partiels successifs : reste à payer exact, maintien en VALIDE puis bascule atomique finale en REGLE")
    void testReglementsPartielsMultiples() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                String mat = "EMP-REG-PART-" + System.currentTimeMillis() % 100000;
                LocalDate dDep = LocalDate.of(2026, 9, 25);
                var testDossier = creerEtScellerDossierTest(c, mat, dDep, new BigDecimal("30"));
                BigDecimal netTotal = testDossier.netScelle();

                BigDecimal tranche1 = new BigDecimal("200000.00");
                BigDecimal tranche2 = new BigDecimal("250000.00");
                BigDecimal tranche3 = netTotal.subtract(tranche1).subtract(tranche2);
                assertTrue(tranche3.compareTo(BigDecimal.ZERO) > 0, "La dernière tranche doit être positive");

                // 1. Versement Tranche 1
                StcReglementRequest req1 = new StcReglementRequest(Date.valueOf(dDep), "TR-001", tranche1, "CHEQUE", "Premier versement");
                StcSituationReglementDTO sit1 = stcService.enregistrerReglement(testDossier.idStc(), req1, testDossier.adminId(), c);

                assertEquals(0, tranche1.compareTo(sit1.getTotalRegle()));
                assertEquals(0, netTotal.subtract(tranche1).compareTo(sit1.getResteAPayer()));
                assertFalse(sit1.isEstIntegralementRegle());
                assertEquals("VALIDE", sit1.getStatutStc(), "Le statut STC doit rester VALIDE après un paiement partiel");
                assertEquals("VALIDE", sit1.getStatutDossier(), "Le statut Dossier doit rester VALIDE après un paiement partiel");

                // 2. Versement Tranche 2
                StcReglementRequest req2 = new StcReglementRequest(Date.valueOf(dDep), "TR-002", tranche2, "VIREMENT", "Deuxième versement");
                StcSituationReglementDTO sit2 = stcService.enregistrerReglement(testDossier.idStc(), req2, testDossier.adminId(), c);

                BigDecimal cumul2 = tranche1.add(tranche2);
                assertEquals(0, cumul2.compareTo(sit2.getTotalRegle()));
                assertEquals(0, netTotal.subtract(cumul2).compareTo(sit2.getResteAPayer()));
                assertFalse(sit2.isEstIntegralementRegle());
                assertEquals("VALIDE", sit2.getStatutStc());

                // 3. Versement Tranche 3 finale (solde)
                StcReglementRequest req3 = new StcReglementRequest(Date.valueOf(dDep), "TR-003", tranche3, "ESPECES", "Solde final");
                StcSituationReglementDTO sit3 = stcService.enregistrerReglement(testDossier.idStc(), req3, testDossier.adminId(), c);

                assertEquals(0, netTotal.compareTo(sit3.getTotalRegle()));
                assertEquals(0, BigDecimal.ZERO.compareTo(sit3.getResteAPayer()));
                assertTrue(sit3.isEstIntegralementRegle());
                assertEquals("REGLE", sit3.getStatutStc(), "Le statut STC doit basculer à REGLE dès que reste à payer = 0");
                assertEquals("REGLE", sit3.getStatutDossier(), "Le dossier doit basculer à REGLE dès que reste à payer = 0");
                assertEquals(3, sit3.getReglements().size());

                c.rollback();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    @Test
    @DisplayName("Rejet strict du sur-règlement : tentative de versement supérieur au solde restant refusée")
    void testRejetSurReglement() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                String mat = "EMP-REG-OVER-" + System.currentTimeMillis() % 100000;
                LocalDate dDep = LocalDate.of(2026, 9, 25);
                var testDossier = creerEtScellerDossierTest(c, mat, dDep, new BigDecimal("30"));
                BigDecimal netTotal = testDossier.netScelle();

                BigDecimal montantExcessif = netTotal.add(new BigDecimal("10.00"));
                StcReglementRequest reqTrop = new StcReglementRequest(Date.valueOf(dDep), "VIR-TROP", montantExcessif);

                SirhBusinessException ex = assertThrows(SirhBusinessException.class, () ->
                        stcService.enregistrerReglement(testDossier.idStc(), reqTrop, testDossier.adminId(), c));

                assertEquals(SirhErrorCode.DATA_INTEGRITY_VIOLATION, ex.getErrorCode());
                assertTrue(ex.getMessage().contains("dépasse le solde restant à payer"));

                // Vérifier qu'aucun règlement n'a été inséré
                try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM paie_stc_reglement WHERE id_stc = ?")) {
                    ps.setInt(1, testDossier.idStc());
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(0, rs.getInt(1));
                    }
                }

                c.rollback();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    @Test
    @DisplayName("Anti-double soumission : interdiction de réutiliser une référence active sur le même STC")
    void testAntiDoubleSoumissionReference() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                String mat = "EMP-REG-DUP-" + System.currentTimeMillis() % 100000;
                LocalDate dDep = LocalDate.of(2026, 9, 25);
                var testDossier = creerEtScellerDossierTest(c, mat, dDep, new BigDecimal("30"));

                BigDecimal tranche = new BigDecimal("100000.00");
                StcReglementRequest req1 = new StcReglementRequest(Date.valueOf(dDep), "REF-UNIQUE-01", tranche);
                stcService.enregistrerReglement(testDossier.idStc(), req1, testDossier.adminId(), c);

                // Même référence soumise une 2e fois
                StcReglementRequest req2 = new StcReglementRequest(Date.valueOf(dDep), "REF-UNIQUE-01", tranche);
                SirhBusinessException ex = assertThrows(SirhBusinessException.class, () ->
                        stcService.enregistrerReglement(testDossier.idStc(), req2, testDossier.adminId(), c));

                assertEquals(SirhErrorCode.DATA_INTEGRITY_VIOLATION, ex.getErrorCode());
                assertTrue(ex.getMessage().contains("porte déjà la référence"));

                c.rollback();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    @Test
    @DisplayName("Annulation motivée d'une tranche : recalcule le reste à payer et réouvre l'état de REGLE à VALIDE")
    void testAnnulationTrancheEtReouvertureStatut() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                String mat = "EMP-REG-ANN-" + System.currentTimeMillis() % 100000;
                LocalDate dDep = LocalDate.of(2026, 9, 25);
                var testDossier = creerEtScellerDossierTest(c, mat, dDep, new BigDecimal("30"));
                BigDecimal netTotal = testDossier.netScelle();

                BigDecimal tr1 = new BigDecimal("200000.00");
                BigDecimal tr2 = netTotal.subtract(tr1);

                // Paiement 1
                stcService.enregistrerReglement(testDossier.idStc(), new StcReglementRequest(Date.valueOf(dDep), "TR-A", tr1), testDossier.adminId(), c);
                // Paiement 2 solde -> statut REGLE
                StcSituationReglementDTO sitComplet = stcService.enregistrerReglement(testDossier.idStc(), new StcReglementRequest(Date.valueOf(dDep), "TR-B", tr2), testDossier.adminId(), c);
                assertEquals("REGLE", sitComplet.getStatutStc());

                int idReglementTr2 = sitComplet.getReglements().stream()
                        .filter(r -> "TR-B".equals(r.getReferenceReglement()))
                        .findFirst().orElseThrow().getIdReglement();

                // Annulation de la tranche 2
                AnnulationReglementRequest annReq = new AnnulationReglementRequest("Chèque retourné impayé par la banque");
                StcSituationReglementDTO sitApresAnnul = stcService.annulerReglement(testDossier.idStc(), idReglementTr2, annReq, testDossier.adminId(), c);

                assertEquals(0, tr1.compareTo(sitApresAnnul.getTotalRegle()));
                assertEquals(0, tr2.compareTo(sitApresAnnul.getResteAPayer()));
                assertFalse(sitApresAnnul.isEstIntegralementRegle());
                assertEquals("VALIDE", sitApresAnnul.getStatutStc(), "Le statut STC doit réouvrir à VALIDE suite à l'annulation");
                assertEquals("VALIDE", sitApresAnnul.getStatutDossier(), "Le statut Dossier doit réouvrir à VALIDE suite à l'annulation");

                // Vérifier en BDD
                try (PreparedStatement psStc = c.prepareStatement("SELECT statut FROM paie_stc WHERE id_stc = ?")) {
                    psStc.setInt(1, testDossier.idStc());
                    try (ResultSet rs = psStc.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("VALIDE", rs.getString("statut"));
                    }
                }

                // Tentative de ré-annuler la même ligne -> rejet
                assertThrows(SirhBusinessException.class, () ->
                        stcService.annulerReglement(testDossier.idStc(), idReglementTr2, annReq, testDossier.adminId(), c));

                c.rollback();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    @Test
    @DisplayName("STC à net nul (0 Ar) : acquittement explicite avec montant 0 Ar et bascule à REGLE")
    void testStcNetNulAcquittement() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                String mat = "EMP-REG-ZERO-" + System.currentTimeMillis() % 100000;
                LocalDate dDep = LocalDate.of(2026, 9, 25);
                // 0 jours travaillés -> salaire prorata = 0, congés = 0 -> Net STC = 0.00 Ar
                var testDossier = creerEtScellerDossierTest(c, mat, dDep, BigDecimal.ZERO);
                assertEquals(0, BigDecimal.ZERO.compareTo(testDossier.netScelle()), "Le net scellé doit valoir 0.00 Ar");

                // Tentative avec montant positif sur un STC à 0 -> rejet
                StcReglementRequest reqInvalide = new StcReglementRequest(Date.valueOf(dDep), "REF-ERR", new BigDecimal("1000.00"));
                assertThrows(SirhBusinessException.class, () ->
                        stcService.enregistrerReglement(testDossier.idStc(), reqInvalide, testDossier.adminId(), c));

                // Acquittement conforme à 0 Ar
                StcReglementRequest reqZero = new StcReglementRequest(Date.valueOf(dDep), "CONSTAT-SOLDE-ZERO", BigDecimal.ZERO, "VIREMENT", "Acquittement solde nul sans dette");
                StcSituationReglementDTO sit = stcService.enregistrerReglement(testDossier.idStc(), reqZero, testDossier.adminId(), c);

                assertEquals(0, BigDecimal.ZERO.compareTo(sit.getTotalRegle()));
                assertEquals(0, BigDecimal.ZERO.compareTo(sit.getResteAPayer()));
                assertTrue(sit.isEstIntegralementRegle());
                assertEquals("REGLE", sit.getStatutStc());
                assertEquals("REGLE", sit.getStatutDossier());

                c.rollback();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    @Test
    @DisplayName("Concurrence transactionnelle : deux règlements simultanés ne peuvent jamais sur-régler le STC")
    void testVerrouillageTransactionnelAntiSurReglementConcurrent() throws Exception {
        int idDossierTest;
        int idStcTest;
        int idEmployeTest;
        int idPeriodeTest = -1;
        boolean periodeCreeeParCeTest = false;
        int adminIdTest;
        BigDecimal netScelleTest;
        LocalDate dDep = LocalDate.of(2026, 9, 25);

        try (Connection cSetup = dataSource.getConnection()) {
            cSetup.setAutoCommit(false);
            boolean periodeExistante = trouverPeriodePaieId(cSetup, dDep) != null;
            String mat = "EMP-REG-CONC-" + System.currentTimeMillis() % 100000;
            var testDossier = creerEtScellerDossierTest(cSetup, mat, dDep, new BigDecimal("30"));
            idPeriodeTest = trouverPeriodePaieId(cSetup, dDep);
            periodeCreeeParCeTest = !periodeExistante;
            idDossierTest = testDossier.idDossier();
            idStcTest = testDossier.idStc();
            idEmployeTest = testDossier.idEmploye();
            adminIdTest = testDossier.adminId();
            netScelleTest = testDossier.netScelle();
            cSetup.commit();
        }

        try {
            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch readyLatch = new CountDownLatch(2);
            CountDownLatch startLatch = new CountDownLatch(1);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            // Chaque thread tente de régler l'INTÉGRALITÉ du net scellé au même instant avec des références différentes
            Runnable task1 = () -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    try (Connection cTask = dataSource.getConnection()) {
                        cTask.setAutoCommit(false);
                        try {
                            StcReglementRequest r1 = new StcReglementRequest(Date.valueOf(dDep), "VIR-CONC-THREAD-1", netScelleTest);
                            stcService.enregistrerReglement(idStcTest, r1, adminIdTest, cTask);
                            cTask.commit();
                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            cTask.rollback();
                            failureCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                }
            };

            Runnable task2 = () -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    try (Connection cTask = dataSource.getConnection()) {
                        cTask.setAutoCommit(false);
                        try {
                            StcReglementRequest r2 = new StcReglementRequest(Date.valueOf(dDep), "VIR-CONC-THREAD-2", netScelleTest);
                            stcService.enregistrerReglement(idStcTest, r2, adminIdTest, cTask);
                            cTask.commit();
                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            cTask.rollback();
                            failureCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                }
            };

            Future<?> f1 = executor.submit(task1);
            Future<?> f2 = executor.submit(task2);
            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();

            f1.get(10, TimeUnit.SECONDS);
            f2.get(10, TimeUnit.SECONDS);
            executor.shutdown();

            // Grâce au verrou pessimiste FOR UPDATE sur paie_stc :
            // Exactement UNE tentative réussit, et l'autre est rejetée pour sur-règlement / STC déjà réglé !
            assertEquals(1, successCount.get(), "Exactement un seul règlement intégral doit être accepté");
            assertEquals(1, failureCount.get(), "Le règlement concurrent concurrent doit être rejeté sous verrou");

            // Vérification de la somme finale en base : elle ne doit JAMAIS excéder le net scellé
            try (Connection cCheck = dataSource.getConnection()) {
                StcSituationReglementDTO sit = stcService.obtenirSituationReglements(idStcTest, cCheck);
                assertNotNull(sit);
                assertEquals(0, netScelleTest.compareTo(sit.getTotalRegle()), "Le total réglé doit être rigoureusement égal au net scellé");
                assertEquals(0, BigDecimal.ZERO.compareTo(sit.getResteAPayer()));
                assertTrue(sit.isEstIntegralementRegle());
                assertEquals(1, sit.getReglements().size());
            }

        } finally {
            // Nettoyage
            try (Connection cClean = dataSource.getConnection()) {
                cClean.setAutoCommit(false);
                try (PreparedStatement ps = cClean.prepareStatement("DELETE FROM paie_stc_reglement WHERE id_stc = ?")) {
                    ps.setInt(1, idStcTest);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = cClean.prepareStatement("DELETE FROM paie_dossier_sortie WHERE id_dossier_sortie = ?")) {
                    ps.setInt(1, idDossierTest);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = cClean.prepareStatement("DELETE FROM paie_stc WHERE id_stc = ?")) {
                    ps.setInt(1, idStcTest);
                    ps.executeUpdate();
                }
                if (periodeCreeeParCeTest) {
                    try (PreparedStatement ps = cClean.prepareStatement(
                            "DELETE FROM bulletin_paie_ligne WHERE id_bulletin IN "
                                    + "(SELECT id_bulletin FROM bulletin_paie WHERE id_employe = ? AND id_periode_paie = ?)")) {
                        ps.setInt(1, idEmployeTest);
                        ps.setInt(2, idPeriodeTest);
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = cClean.prepareStatement(
                            "DELETE FROM bulletin_paie WHERE id_employe = ? AND id_periode_paie = ?")) {
                        ps.setInt(1, idEmployeTest);
                        ps.setInt(2, idPeriodeTest);
                        ps.executeUpdate();
                    }
                }
                if (periodeCreeeParCeTest && idPeriodeTest > 0) {
                    // Ne retirer que la période créée par ce test, après ses STC ; une période préexistante reste intacte.
                    try (PreparedStatement ps = cClean.prepareStatement(
                            "DELETE FROM periode_paie WHERE id_periode_paie = ?")) {
                        ps.setInt(1, idPeriodeTest);
                        ps.executeUpdate();
                    }
                }
                cClean.commit();
            }
        }
    }

    @Test
    @DisplayName("Annulation après clôture : interdiction stricte de l'annulation ordinaire si le dossier est CLOTURE")
    void testInterdictionAnnulationOrdinaireApresClotureDossier() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                String mat = "EMP-REG-CLOT-" + System.currentTimeMillis() % 100000;
                LocalDate dDep = LocalDate.of(2026, 9, 25);
                var testDossier = creerEtScellerDossierTest(c, mat, dDep, new BigDecimal("30"));

                // Règlement intégral
                StcReglementRequest req = new StcReglementRequest(Date.valueOf(dDep), "VIR-CLOT-01", testDossier.netScelle());
                StcSituationReglementDTO sit = stcService.enregistrerReglement(testDossier.idStc(), req, testDossier.adminId(), c);
                assertEquals("REGLE", sit.getStatutStc());

                int idReglement = sit.getReglements().get(0).getIdReglement();

                // Clôture administrative du dossier (simulation transition statut CLOTURE)
                try (PreparedStatement psClot = c.prepareStatement(
                        "UPDATE paie_dossier_sortie SET statut_dossier = 'CLOTURE', date_cloture = CURRENT_TIMESTAMP WHERE id_dossier_sortie = ?")) {
                    psClot.setInt(1, testDossier.idDossier());
                    psClot.executeUpdate();
                }

                // Tentative d'annulation ordinaire du règlement
                AnnulationReglementRequest annReq = new AnnulationReglementRequest("Demande tardive post-clôture");
                SirhBusinessException ex = assertThrows(SirhBusinessException.class, () ->
                        stcService.annulerReglement(testDossier.idStc(), idReglement, annReq, testDossier.adminId(), c));

                assertEquals(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED, ex.getErrorCode());
                assertTrue(ex.getMessage().contains("statut CLOTURE"));

                // Vérifier que le règlement est toujours actif et le dossier inchangé
                try (PreparedStatement psCheck = c.prepareStatement("SELECT est_annule FROM paie_stc_reglement WHERE id_reglement = ?")) {
                    psCheck.setInt(1, idReglement);
                    try (ResultSet rs = psCheck.executeQuery()) {
                        assertTrue(rs.next());
                        assertFalse(rs.getBoolean("est_annule"), "Le règlement ne doit pas avoir été annulé");
                    }
                }

                c.rollback();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    @Test
    @DisplayName("Idempotence technique : rejeu d'une requête avec la même cle_idempotence retourne le résultat sans doublon")
    void testIdempotenceRejeuExactSansDoublon() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                String mat = "EMP-REG-IDEM-" + System.currentTimeMillis() % 100000;
                LocalDate dDep = LocalDate.of(2026, 9, 25);
                var testDossier = creerEtScellerDossierTest(c, mat, dDep, new BigDecimal("30"));

                BigDecimal tranche = new BigDecimal("150000.00");
                String cleIdem = "IDEM-TOKEN-UUID-" + System.nanoTime();

                StcReglementRequest req1 = new StcReglementRequest(Date.valueOf(dDep), "VIR-IDEM-01", tranche, "VIREMENT", "Premier essai", cleIdem);

                // Premier appel : enregistrement effectif
                StcSituationReglementDTO sit1 = stcService.enregistrerReglement(testDossier.idStc(), req1, testDossier.adminId(), c);
                assertEquals(1, sit1.getReglements().size());
                assertEquals(0, tranche.compareTo(sit1.getTotalRegle()));

                // Rejeu exact avec la même clé d'idempotence (simulation retry réseau)
                StcSituationReglementDTO sit2 = stcService.enregistrerReglement(testDossier.idStc(), req1, testDossier.adminId(), c);

                assertNotNull(sit2);
                assertEquals(1, sit2.getReglements().size(), "Aucune seconde ligne ne doit avoir été insérée");
                assertEquals(0, tranche.compareTo(sit2.getTotalRegle()), "Le total réglé ne doit pas avoir doublé");
                assertEquals(cleIdem, sit2.getReglements().get(0).getCleIdempotence());

                c.rollback();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    @Test
    @DisplayName("Idempotence stricte : rejet formel si une même clé est rejouée avec montant, mode, référence ou date différent")
    void testIdempotenceRejetPayloadDivergent() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                String mat = "EMP-REG-DIV-" + System.currentTimeMillis() % 100000;
                LocalDate dDep = LocalDate.of(2026, 9, 25);
                var testDossier = creerEtScellerDossierTest(c, mat, dDep, new BigDecimal("30"));

                BigDecimal montantInitial = new BigDecimal("100000.00");
                String cleIdem = "IDEM-DIV-TOKEN-" + System.nanoTime();

                StcReglementRequest reqLegitime = new StcReglementRequest(
                        Date.valueOf(dDep), "VIR-LEGIT-01", montantInitial, "VIREMENT", "Premier versement", cleIdem);

                // 1. Premier enregistrement légitime
                StcSituationReglementDTO sit = stcService.enregistrerReglement(testDossier.idStc(), reqLegitime, testDossier.adminId(), c);
                assertEquals(1, sit.getReglements().size());

                // 2. Rejeu avec montant altéré
                StcReglementRequest reqMontantDifferent = new StcReglementRequest(
                        Date.valueOf(dDep), "VIR-LEGIT-01", new BigDecimal("120000.00"), "VIREMENT", "Montant modifié", cleIdem);
                SirhBusinessException exMontant = assertThrows(SirhBusinessException.class, () ->
                        stcService.enregistrerReglement(testDossier.idStc(), reqMontantDifferent, testDossier.adminId(), c));
                assertEquals(SirhErrorCode.DATA_INTEGRITY_VIOLATION, exMontant.getErrorCode());
                assertTrue(exMontant.getMessage().contains("Conflit d'idempotence"));

                // 3. Rejeu avec référence altérée
                StcReglementRequest reqRefDifferente = new StcReglementRequest(
                        Date.valueOf(dDep), "VIR-ALT-99", montantInitial, "VIREMENT", "Référence modifiée", cleIdem);
                SirhBusinessException exRef = assertThrows(SirhBusinessException.class, () ->
                        stcService.enregistrerReglement(testDossier.idStc(), reqRefDifferente, testDossier.adminId(), c));
                assertEquals(SirhErrorCode.DATA_INTEGRITY_VIOLATION, exRef.getErrorCode());
                assertTrue(exRef.getMessage().contains("Conflit d'idempotence"));

                // 4. Rejeu avec mode de règlement altéré
                StcReglementRequest reqModeDifferent = new StcReglementRequest(
                        Date.valueOf(dDep), "VIR-LEGIT-01", montantInitial, "CHEQUE", "Mode chèque au lieu de virement", cleIdem);
                SirhBusinessException exMode = assertThrows(SirhBusinessException.class, () ->
                        stcService.enregistrerReglement(testDossier.idStc(), reqModeDifferent, testDossier.adminId(), c));
                assertEquals(SirhErrorCode.DATA_INTEGRITY_VIOLATION, exMode.getErrorCode());
                assertTrue(exMode.getMessage().contains("Conflit d'idempotence"));

                c.rollback();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    @Test
    @DisplayName("Concurrence idempotente : deux threads envoyant exactement la même clé d'idempotence aboutissent sans doublon ni conflit")
    void testIdempotenceConcurrenteMemeCleDeuxThreads() throws Exception {
        int idDossierTest;
        int idStcTest;
        int idEmployeTest;
        int idPeriodeTest = -1;
        boolean periodeCreeeParCeTest = false;
        int adminIdTest;
        BigDecimal tranche = new BigDecimal("180000.00");
        String cleIdemPartagee = "IDEM-CONC-TWIN-" + System.nanoTime();
        LocalDate dDep = LocalDate.of(2026, 9, 25);

        try (Connection cSetup = dataSource.getConnection()) {
            cSetup.setAutoCommit(false);
            boolean periodeExistante = trouverPeriodePaieId(cSetup, dDep) != null;
            String mat = "E-IDCONC-" + (System.currentTimeMillis() % 100000);
            var testDossier = creerEtScellerDossierTest(cSetup, mat, dDep, new BigDecimal("30"));
            idPeriodeTest = trouverPeriodePaieId(cSetup, dDep);
            periodeCreeeParCeTest = !periodeExistante;
            idDossierTest = testDossier.idDossier();
            idStcTest = testDossier.idStc();
            idEmployeTest = testDossier.idEmploye();
            adminIdTest = testDossier.adminId();
            cSetup.commit();
        }

        try {
            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch readyLatch = new CountDownLatch(2);
            CountDownLatch startLatch = new CountDownLatch(1);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            Runnable task = () -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    try (Connection cTask = dataSource.getConnection()) {
                        cTask.setAutoCommit(false);
                        try {
                            StcReglementRequest req = new StcReglementRequest(
                                    Date.valueOf(dDep), "VIR-TWIN-01", tranche, "VIREMENT", "Versement concurrent identique", cleIdemPartagee);
                            stcService.enregistrerReglement(idStcTest, req, adminIdTest, cTask);
                            cTask.commit();
                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            cTask.rollback();
                            failureCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                }
            };

            Future<?> f1 = executor.submit(task);
            Future<?> f2 = executor.submit(task);
            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();

            f1.get(10, TimeUnit.SECONDS);
            f2.get(10, TimeUnit.SECONDS);
            executor.shutdown();

            // Les deux threads doivent réussir (l'un a inséré, l'autre a reçu la réponse idempotente sans réinsertion)
            assertEquals(2, successCount.get(), "Les deux requêtes avec la même clé d'idempotence doivent être acceptées");
            assertEquals(0, failureCount.get(), "Aucune exception ne doit être levée lors d'un rejeu concurrent identique");

            // Vérification en base : exactement UNE seule ligne insérée
            try (Connection cCheck = dataSource.getConnection()) {
                StcSituationReglementDTO sit = stcService.obtenirSituationReglements(idStcTest, cCheck);
                assertNotNull(sit);
                assertEquals(1, sit.getReglements().size(), "Exactement 1 seul règlement doit être persisté pour cette clé");
                assertEquals(0, tranche.compareTo(sit.getTotalRegle()), "Le montant ne doit pas avoir doublé");
                assertEquals(cleIdemPartagee, sit.getReglements().get(0).getCleIdempotence());
            }

        } finally {
            // Nettoyage
            try (Connection cClean = dataSource.getConnection()) {
                cClean.setAutoCommit(false);
                try (PreparedStatement ps = cClean.prepareStatement("DELETE FROM paie_stc_reglement WHERE id_stc = ?")) {
                    ps.setInt(1, idStcTest);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = cClean.prepareStatement("DELETE FROM paie_dossier_sortie WHERE id_dossier_sortie = ?")) {
                    ps.setInt(1, idDossierTest);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = cClean.prepareStatement("DELETE FROM paie_stc WHERE id_stc = ?")) {
                    ps.setInt(1, idStcTest);
                    ps.executeUpdate();
                }
                if (periodeCreeeParCeTest) {
                    try (PreparedStatement ps = cClean.prepareStatement(
                            "DELETE FROM bulletin_paie_ligne WHERE id_bulletin IN "
                                    + "(SELECT id_bulletin FROM bulletin_paie WHERE id_employe = ? AND id_periode_paie = ?)")) {
                        ps.setInt(1, idEmployeTest);
                        ps.setInt(2, idPeriodeTest);
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = cClean.prepareStatement(
                            "DELETE FROM bulletin_paie WHERE id_employe = ? AND id_periode_paie = ?")) {
                        ps.setInt(1, idEmployeTest);
                        ps.setInt(2, idPeriodeTest);
                        ps.executeUpdate();
                    }
                    if (idPeriodeTest > 0) {
                        try (PreparedStatement ps = cClean.prepareStatement("DELETE FROM periode_paie WHERE id_periode_paie = ?")) {
                            ps.setInt(1, idPeriodeTest);
                            ps.executeUpdate();
                        }
                    }
                }
                cClean.commit();
            }
        }
    }

    @Test
    @DisplayName("Réutilisation de référence après annulation : autorisée et tracée dans le journal")
    void testReutilisationReferenceApresAnnulationAutorisee() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                String mat = "EMP-REG-REUSE-" + System.currentTimeMillis() % 100000;
                LocalDate dDep = LocalDate.of(2026, 9, 25);
                var testDossier = creerEtScellerDossierTest(c, mat, dDep, new BigDecimal("30"));

                BigDecimal tranche = new BigDecimal("200000.00");
                String refMetier = "CHQ-REUSE-999";

                // 1. Versement initial
                StcReglementRequest req1 = new StcReglementRequest(Date.valueOf(dDep), refMetier, tranche, "CHEQUE", "Chèque rejeté");
                StcSituationReglementDTO sit1 = stcService.enregistrerReglement(testDossier.idStc(), req1, testDossier.adminId(), c);
                int idReg1 = sit1.getReglements().get(0).getIdReglement();

                // 2. Annulation du versement
                AnnulationReglementRequest annReq = new AnnulationReglementRequest("Chèque retourné pour motif technique");
                stcService.annulerReglement(testDossier.idStc(), idReg1, annReq, testDossier.adminId(), c);

                // 3. Réutilisation de la même référence métier (ex: re-présentation du chèque régularisé)
                StcReglementRequest req2 = new StcReglementRequest(Date.valueOf(dDep), refMetier, tranche, "CHEQUE", "Chèque régularisé représenté");
                StcSituationReglementDTO sit2 = stcService.enregistrerReglement(testDossier.idStc(), req2, testDossier.adminId(), c);

                // La situation doit comporter 2 lignes (1 annulée, 1 active)
                assertEquals(2, sit2.getReglements().size());
                long countActifs = sit2.getReglements().stream().filter(r -> !r.isEstAnnule()).count();
                long countAnnules = sit2.getReglements().stream().filter(r -> r.isEstAnnule()).count();
                assertEquals(1, countActifs);
                assertEquals(1, countAnnules);
                assertEquals(0, tranche.compareTo(sit2.getTotalRegle()));

                c.rollback();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }
}
