package mg.galaxam.sirh.paie.application;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.common.exception.SirhErrorCode;
import mg.galaxam.sirh.conge.testutils.CongeTestFixtureHelper;
import mg.galaxam.sirh.contrat.persistence.ContratTravail;
import mg.galaxam.sirh.paie.api.dto.stc.ClotureDossierRequest;
import mg.galaxam.sirh.paie.api.dto.stc.DossierSortieDTO;
import mg.galaxam.sirh.paie.api.dto.stc.DossierSortieSaveRequest;
import mg.galaxam.sirh.paie.api.dto.stc.ScellementStcRequest;
import mg.galaxam.sirh.paie.api.dto.stc.SimulationStcResultDTO;
import mg.galaxam.sirh.paie.api.dto.stc.StcReglementRequest;
import mg.galaxam.sirh.paie.api.dto.stc.StcSituationReglementDTO;
import mg.galaxam.sirh.paie.persistence.PaieStc;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class DossierSortieClotureIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private DossierSortieService dossierSortieService;

    @Autowired
    private StcService stcService;

    @BeforeAll
    static void setupSchema(@Autowired DataSource ds) throws Exception {
        try (Connection c = ds.getConnection()) {
            try (Statement st = c.createStatement()) {
                st.execute("ALTER TABLE paie_dossier_sortie " +
                        "ADD COLUMN IF NOT EXISTS motif_cloture TEXT, " +
                        "ADD COLUMN IF NOT EXISTS derogation_reglement BOOLEAN DEFAULT FALSE, " +
                        "ADD COLUMN IF NOT EXISTS justification_derogation TEXT");
            }
        }
    }

    private record TestDossierScelle(int idDossier, int idStc, int idEmploye, int idContrat, int adminId, BigDecimal netScelle, LocalDate dateDepart) {}

    private Integer trouverPeriodePaieId(Connection c, LocalDate date) throws Exception {
        String sql = "SELECT id_periode_paie FROM periode_paie WHERE ? BETWEEN date_debut AND date_fin LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
                return null;
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

    private void enregistrerCessationContratCanonique(Connection c, int idContrat, LocalDate dateFin, String motif) throws Exception {
        ContratTravail ct = new ContratTravail().rechercherParId(idContrat, c);
        if (ct != null) {
            ct.cloturerContrat(Date.valueOf(dateFin), null, motif, c);
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE contrat_travail SET id_statut_rh = (SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'ROMPU'), date_fin = ? WHERE id_contrat = ?")) {
                ps.setDate(1, Date.valueOf(dateFin));
                ps.setInt(2, idContrat);
                ps.executeUpdate();
            }
            try (PreparedStatement psAff = c.prepareStatement(
                    "UPDATE employe_affectation_carriere SET date_fin = ?, id_statut_rh = (SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'HISTORISE') WHERE id_contrat = ?")) {
                psAff.setDate(1, Date.valueOf(dateFin));
                psAff.setInt(2, idContrat);
                psAff.executeUpdate();
            }
        }
    }

    private TestDossierScelle creerEtScellerDossierTest(Connection c, String matricule, LocalDate dateDepart, BigDecimal joursTravailles) throws Exception {
        assurerPeriodePaie(c, dateDepart);
        int adminId = CongeTestFixtureHelper.createAdminUser(c, "rh_clot_" + System.currentTimeMillis() + "_" + Math.abs(matricule.hashCode()));
        int empId = CongeTestFixtureHelper.createEmploye(c, matricule, "TEST_CLOT", "Agent");
        int contratId = CongeTestFixtureHelper.createContratActif(c, empId, LocalDate.of(2023, 1, 1));
        CongeTestFixtureHelper.createAffectation(c, empId, contratId, LocalDate.of(2023, 1, 1), new BigDecimal("800000.00"));

        // Cessation effective préalable du contrat enregistrée par le mécanisme RH canonique
        enregistrerCessationContratCanonique(c, contratId, dateDepart, "Rupture RH préalable canonique");

        DossierSortieSaveRequest saveReq = new DossierSortieSaveRequest();
        saveReq.setIdEmploye(empId);
        saveReq.setIdContrat(contratId);
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

        ScellementStcRequest sealReq = new ScellementStcRequest(sim.getReferenceSimulation(), sim.getVersion(), "Scellement avant clôture test");
        DossierSortieDTO scelle = dossierSortieService.scellerDossierEtStc(dossier.getIdDossierSortie(), sealReq, adminId, c);

        return new TestDossierScelle(scelle.getIdDossierSortie(), scelle.getIdStc(), empId, contratId, adminId, scelle.getNetAPayerStc(), dateDepart);
    }

    @Test
    @DisplayName("1. Clôture normale : dossier intégralement réglé -> statut CLOTURE, contrat rompu/clos, employé désactivé et audit consigné")
    void testClotureNormaleDossierIntegralementRegle() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                String mat = "E-CLOT-NORM-" + (System.currentTimeMillis() % 100000);
                LocalDate dDep = LocalDate.of(2026, 9, 25);
                var test = creerEtScellerDossierTest(c, mat, dDep, new BigDecimal("30"));

                // 1. Règlement intégral du STC
                StcReglementRequest reqReg = new StcReglementRequest(
                        Date.valueOf(dDep), "VIR-CLOT-SOLDE", test.netScelle(), "VIREMENT", "Règlement complet pour clôture");
                stcService.enregistrerReglement(test.idStc(), reqReg, test.adminId(), c);

                // 2. Clôture administrative formelle
                ClotureDossierRequest clotReq = new ClotureDossierRequest("Départ régularisé, formalités conformes");
                DossierSortieDTO cloture = dossierSortieService.cloturerDossier(test.idDossier(), clotReq, test.adminId(), c);

                // Vérifications sur le dossier
                assertEquals("CLOTURE", cloture.getStatutDossier());
                assertTrue(cloture.isEstCloture());
                assertNotNull(cloture.getDateCloture());
                assertEquals(test.adminId(), cloture.getCloturePar());
                assertEquals("Départ régularisé, formalités conformes", cloture.getMotifCloture());
                assertFalse(cloture.isDerogationReglement());

                // Vérification cessation effective du contrat
                try (PreparedStatement psCt = c.prepareStatement(
                        "SELECT s.code, ct.date_fin FROM contrat_travail ct JOIN ref_statut_rh s ON ct.id_statut_rh = s.id_statut_rh WHERE ct.id_contrat = ?")) {
                    psCt.setInt(1, test.idContrat());
                    try (ResultSet rsCt = psCt.executeQuery()) {
                        assertTrue(rsCt.next());
                        assertTrue(List.of("ROMPU", "CLOTURE").contains(rsCt.getString("code")), "Le contrat doit être ROMPU ou CLOTURE");
                        assertEquals(Date.valueOf(dDep), rsCt.getDate("date_fin"));
                    }
                }

                // Vérification désactivation salarié (car aucun autre contrat)
                try (PreparedStatement psEmp = c.prepareStatement(
                        "SELECT est_actif, motif_desactivation, date_desactivation FROM employe WHERE id_employe = ?")) {
                    psEmp.setInt(1, test.idEmploye());
                    try (ResultSet rsEmp = psEmp.executeQuery()) {
                        assertTrue(rsEmp.next());
                        assertFalse(rsEmp.getBoolean("est_actif"), "L'employé sans autre contrat doit être désactivé");
                        assertEquals("Demission", rsEmp.getString("motif_desactivation"));
                        assertNotNull(rsEmp.getTimestamp("date_desactivation"));
                    }
                }

                // Vérification entrée audit_log
                try (PreparedStatement psAud = c.prepareStatement(
                        "SELECT COUNT(*) FROM audit_log WHERE nom_table = 'paie_dossier_sortie' AND action = 'CLOTURE_ADMINISTRATIVE' AND id_enregistrement = ?")) {
                    psAud.setString(1, String.valueOf(test.idDossier()));
                    try (ResultSet rsAud = psAud.executeQuery()) {
                        assertTrue(rsAud.next());
                        assertTrue(rsAud.getInt(1) >= 1, "Un log d'audit doit être tracé lors de la clôture");
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
    @DisplayName("1.bis Refus de clôture administrative si le contrat est toujours ACTIF (cessation RH préalable obligatoire)")
    void testRefusClotureSiContratEncoreActif() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                String mat = "E-CLOT-ACTIF-" + (System.currentTimeMillis() % 100000);
                LocalDate dDep = LocalDate.of(2026, 9, 25);
                var test = creerEtScellerDossierTest(c, mat, dDep, new BigDecimal("30"));

                // Remettre explicitement le contrat à ACTIF (simulant l'omission de la cessation préalable par le module RH)
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE contrat_travail SET id_statut_rh = (SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'ACTIF') WHERE id_contrat = ?")) {
                    ps.setInt(1, test.idContrat());
                    ps.executeUpdate();
                }

                // Règlement intégral effectué
                stcService.enregistrerReglement(test.idStc(), new StcReglementRequest(
                        Date.valueOf(dDep), "VIR-FULL", test.netScelle()), test.adminId(), c);

                // Tentative de clôture administrative sans cessation effective préalable
                ClotureDossierRequest reqClot = new ClotureDossierRequest("Tentative clôture contrat actif");
                SirhBusinessException ex = assertThrows(SirhBusinessException.class, () ->
                        dossierSortieService.cloturerDossier(test.idDossier(), reqClot, test.adminId(), c));

                assertEquals(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED, ex.getErrorCode());
                assertTrue(ex.getMessage().contains("toujours à l'état ACTIF"), "Le message doit bloquer car le contrat est encore ACTIF");

                c.rollback();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    @Test
    @DisplayName("1.ter Indépendance stricte : contrat terminé par RH alors que son STC n'est pas encore réglé")
    void testContratTermineAlorsQueStcNonRegle() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                String mat = "E-CLOT-DECOUPL-" + (System.currentTimeMillis() % 100000);
                LocalDate dDep = LocalDate.of(2026, 9, 25);
                var test = creerEtScellerDossierTest(c, mat, dDep, new BigDecimal("30"));

                // Le contrat est déjà rompu/clos par RH (via le mécanisme canonique)
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT s.code, ct.date_fin FROM contrat_travail ct JOIN ref_statut_rh s ON ct.id_statut_rh = s.id_statut_rh WHERE ct.id_contrat = ?")) {
                    ps.setInt(1, test.idContrat());
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("ROMPU", rs.getString("code"));
                        assertEquals(Date.valueOf(dDep), rs.getDate("date_fin"));
                    }
                }

                // Mais le STC n'est pas encore réglé (dette financière intégrale en attente)
                StcSituationReglementDTO sit = stcService.obtenirSituationReglements(test.idStc(), c);
                assertNotNull(sit);
                assertEquals("VALIDE", sit.getStatutStc());
                assertEquals(0, test.netScelle().compareTo(sit.getResteAPayer()), "Le reste à payer doit être égal à 100% du net scellé");
                assertFalse(sit.isEstIntegralementRegle());

                c.rollback();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    @Test
    @DisplayName("2. Refus de clôture avec règlement incomplet : rejet strict sans dérogation formelle")
    void testRefusClotureReglementIncompletSansDerogation() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                String mat = "E-CLOT-INC-" + (System.currentTimeMillis() % 100000);
                LocalDate dDep = LocalDate.of(2026, 9, 25);
                var test = creerEtScellerDossierTest(c, mat, dDep, new BigDecimal("30"));

                // Paiement partiel uniquement (50 000 Ar sur le net scellé)
                StcReglementRequest reqPartiel = new StcReglementRequest(
                        Date.valueOf(dDep), "VIR-PARTIEL", new BigDecimal("50000.00"), "VIREMENT", "Paiement partiel");
                stcService.enregistrerReglement(test.idStc(), reqPartiel, test.adminId(), c);

                // Tentative de clôture ordinaire
                ClotureDossierRequest reqClot = new ClotureDossierRequest("Clôture sans dérogation");
                SirhBusinessException ex = assertThrows(SirhBusinessException.class, () ->
                        dossierSortieService.cloturerDossier(test.idDossier(), reqClot, test.adminId(), c));

                assertEquals(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED, ex.getErrorCode());
                assertTrue(ex.getMessage().contains("n'est pas intégralement réglé"), "Le message doit mentionner le reste à payer");

                c.rollback();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    @Test
    @DisplayName("3. Clôture avec dérogation formelle : acceptée si justification formelle motivée fournie")
    void testClotureAutoriseeAvecDerogationFormelleEtJustification() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                String mat = "E-CLOT-DEROG-" + (System.currentTimeMillis() % 100000);
                LocalDate dDep = LocalDate.of(2026, 9, 25);
                var test = creerEtScellerDossierTest(c, mat, dDep, new BigDecimal("30"));

                // Règlement partiel
                StcReglementRequest reqPartiel = new StcReglementRequest(
                        Date.valueOf(dDep), "VIR-DEROG-01", new BigDecimal("100000.00"), "VIREMENT", "Paiement tranche 1");
                stcService.enregistrerReglement(test.idStc(), reqPartiel, test.adminId(), c);

                // Clôture avec dérogation formelle complète
                ClotureDossierRequest reqDerog = new ClotureDossierRequest(
                        "Clôture sous accord de report",
                        true,
                        "Protocole d'accord transactionnel n° DAF-2026-09 stipulant un paiement différé du solde restant au 30/10/2026",
                        "DIRECTION_GENERALE",
                        "PROT-DAF-2026-09",
                        Date.valueOf(dDep),
                        true
                );

                DossierSortieDTO cloture = dossierSortieService.cloturerDossier(test.idDossier(), reqDerog, test.adminId(), c);

                assertEquals("CLOTURE", cloture.getStatutDossier());
                assertTrue(cloture.isDerogationReglement());
                assertNotNull(cloture.getJustificationDerogation());
                assertTrue(cloture.getJustificationDerogation().contains("Protocole d'accord transactionnel"));
                assertEquals("DIRECTION_GENERALE", cloture.getAutoriteDecisionDerogation());
                assertEquals("PROT-DAF-2026-09", cloture.getReferenceDecisionDerogation());
                assertEquals(Date.valueOf(dDep), cloture.getDateDecisionDerogation());
                assertNotNull(cloture.getDateCloture());

                // Vérification entrée audit_log avec action CLOTURE_AVEC_DEROGATION_REGLEMENT
                try (PreparedStatement psAud = c.prepareStatement(
                        "SELECT COUNT(*) FROM audit_log WHERE nom_table = 'paie_dossier_sortie' AND action = 'CLOTURE_AVEC_DEROGATION_REGLEMENT' AND id_enregistrement = ?")) {
                    psAud.setString(1, String.valueOf(test.idDossier()));
                    try (ResultSet rsAud = psAud.executeQuery()) {
                        assertTrue(rsAud.next());
                        assertTrue(rsAud.getInt(1) >= 1, "Un log d'audit CLOTURE_AVEC_DEROGATION_REGLEMENT doit être tracé");
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
    @DisplayName("3.ter Rejet strict de dérogation si autorité, référence ou date manquante")
    void testRejetDerogationSiGouvernanceIncomplete() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                String mat = "E-CDINV-" + (System.currentTimeMillis() % 100000);
                LocalDate dDep = LocalDate.of(2026, 9, 25);
                var test = creerEtScellerDossierTest(c, mat, dDep, new BigDecimal("30"));

                // Règlement partiel
                stcService.enregistrerReglement(test.idStc(), new StcReglementRequest(
                        Date.valueOf(dDep), "VIR-DEROG-INV", new BigDecimal("50000.00"), "VIREMENT", "Paiement tranche"), test.adminId(), c);

                // Cas 1 : Autorité manquante
                ClotureDossierRequest reqSansAutorite = new ClotureDossierRequest(
                        "Clôture sans autorité", true, "Justification quelconque", "", "REF-123", Date.valueOf(dDep), true);
                SirhBusinessException ex1 = assertThrows(SirhBusinessException.class, () ->
                        dossierSortieService.cloturerDossier(test.idDossier(), reqSansAutorite, test.adminId(), c));
                assertEquals(SirhErrorCode.VALIDATION_ERROR, ex1.getErrorCode());
                assertTrue(ex1.getMessage().contains("autorité décisionnaire"));

                // Cas 2 : Référence manquante
                ClotureDossierRequest reqSansRef = new ClotureDossierRequest(
                        "Clôture sans ref", true, "Justification quelconque", "DIRECTION_GENERALE", "   ", Date.valueOf(dDep), true);
                SirhBusinessException ex2 = assertThrows(SirhBusinessException.class, () ->
                        dossierSortieService.cloturerDossier(test.idDossier(), reqSansRef, test.adminId(), c));
                assertEquals(SirhErrorCode.VALIDATION_ERROR, ex2.getErrorCode());
                assertTrue(ex2.getMessage().contains("référence formelle"));

                // Cas 3 : Date manquante
                ClotureDossierRequest reqSansDate = new ClotureDossierRequest(
                        "Clôture sans date", true, "Justification quelconque", "DIRECTION_GENERALE", "REF-123", null, true);
                SirhBusinessException ex3 = assertThrows(SirhBusinessException.class, () ->
                        dossierSortieService.cloturerDossier(test.idDossier(), reqSansDate, test.adminId(), c));
                assertEquals(SirhErrorCode.VALIDATION_ERROR, ex3.getErrorCode());
                assertTrue(ex3.getMessage().contains("date de la décision"));

                c.rollback();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    @Test
    @DisplayName("3.bis Clôture sous dérogation, paiements ultérieurs sur échéancier jusqu'à extinction, et immutabilité STC")
    void testPaiementsUlterieursSurEcheancierJusquaZeroEtImmutabilite() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                String mat = "E-CLOT-ECH-" + (System.currentTimeMillis() % 100000);
                LocalDate dDep = LocalDate.of(2026, 9, 25);
                var test = creerEtScellerDossierTest(c, mat, dDep, new BigDecimal("30"));
                BigDecimal netInitial = test.netScelle();
                assertTrue(netInitial.compareTo(new BigDecimal("100000.00")) > 0, "Le net doit être suffisant pour plusieurs tranches");

                // 1. Paiement initial partiel de 50 000 Ar
                BigDecimal t1 = new BigDecimal("50000.00");
                stcService.enregistrerReglement(test.idStc(), new StcReglementRequest(
                        Date.valueOf(dDep), "VIR-ECH-01", t1, "VIREMENT", "Tranche 1 initiale"), test.adminId(), c);

                // 2. Clôture administrative avec dérogation formelle d'échéancier
                ClotureDossierRequest reqDerog = new ClotureDossierRequest(
                        "Clôture sous protocole échelonné DAF",
                        true,
                        "Protocole d'accord transactionnel n° DAF-2026-09 stipulant un paiement en 2 échéances complémentaires",
                        "DIRECTION_ADMINISTRATIVE_ET_FINANCIERE",
                        "PROT-ECH-2026-09",
                        Date.valueOf(dDep),
                        true
                );
                DossierSortieDTO cloture = dossierSortieService.cloturerDossier(test.idDossier(), reqDerog, test.adminId(), c);
                assertEquals("CLOTURE", cloture.getStatutDossier());
                assertTrue(cloture.isEstCloture());
                assertTrue(cloture.isDerogationReglement());

                // 3. Paiement ultérieur sur l'échéancier (Tranche 2) après clôture
                BigDecimal t2 = new BigDecimal("100000.00");
                StcReglementRequest reqP2 = new StcReglementRequest(
                        Date.valueOf(dDep.plusDays(5)), "VIR-ECH-02", t2, "VIREMENT", "Tranche 2 post-clôture");
                StcSituationReglementDTO sitP2 = stcService.enregistrerReglement(test.idStc(), reqP2, test.adminId(), c);

                // Vérifications après tranche 2 :
                // - Reste à payer a diminué de t2
                BigDecimal resteAttendu2 = netInitial.subtract(t1).subtract(t2);
                assertEquals(0, resteAttendu2.compareTo(sitP2.getResteAPayer()), "Le reste à payer doit avoir diminué de la tranche 2");
                // - Net scellé strictement inchangé
                assertEquals(0, netInitial.compareTo(sitP2.getNetAPayerStc()), "Le net STC scellé doit être strictement inchangé");
                // - Le dossier de sortie DEMEURE 'CLOTURE' (pas de réouverture administrative)
                try (PreparedStatement psDos = c.prepareStatement("SELECT statut_dossier FROM paie_dossier_sortie WHERE id_dossier_sortie = ?")) {
                    psDos.setInt(1, test.idDossier());
                    try (ResultSet rs = psDos.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("CLOTURE", rs.getString("statut_dossier"), "Le dossier doit demeurer CLOTURE");
                    }
                }

                // 4. Paiement final soldant l'intégralité du reste à payer
                BigDecimal tFinal = sitP2.getResteAPayer();
                StcReglementRequest reqFinal = new StcReglementRequest(
                        Date.valueOf(dDep.plusDays(10)), "VIR-ECH-SOLDE", tFinal, "VIREMENT", "Solde final de l'échéancier");
                StcSituationReglementDTO sitFinal = stcService.enregistrerReglement(test.idStc(), reqFinal, test.adminId(), c);

                // Vérifications finales :
                assertEquals(0, BigDecimal.ZERO.compareTo(sitFinal.getResteAPayer()), "Le reste à payer doit être exactement 0 Ar");
                assertEquals("REGLE", sitFinal.getStatutStc(), "Le STC passe au statut REGLE");
                assertTrue(sitFinal.isEstIntegralementRegle());
                assertEquals(0, netInitial.compareTo(sitFinal.getNetAPayerStc()), "Le montant net scellé est resté parfaitement immuable");

                // Le dossier DEMEURE 'CLOTURE' (pas écrasé à 'REGLE')
                try (PreparedStatement psDos = c.prepareStatement("SELECT statut_dossier FROM paie_dossier_sortie WHERE id_dossier_sortie = ?")) {
                    psDos.setInt(1, test.idDossier());
                    try (ResultSet rs = psDos.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("CLOTURE", rs.getString("statut_dossier"), "Le dossier administratif demeure CLOTURE");
                    }
                }

                // 5. Tentative de paiement supplémentaire après extinction totale de la dette -> rejetée
                StcReglementRequest reqOver = new StcReglementRequest(
                        Date.valueOf(dDep.plusDays(15)), "VIR-SUR-PAIEMENT", new BigDecimal("10000.00"), "VIREMENT", "Paiement en trop");
                SirhBusinessException exOver = assertThrows(SirhBusinessException.class, () ->
                        stcService.enregistrerReglement(test.idStc(), reqOver, test.adminId(), c));
                assertEquals(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED, exOver.getErrorCode());
                assertTrue(exOver.getMessage().contains("déjà"));

                c.rollback();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    @Test
    @DisplayName("3.ter Rejet strict de règlement après clôture ordinaire sans dérogation d'échéancier")
    void testRejetReglementApresClotureOrdinaireSansDerogation() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                String mat = "E-CLOT-NODER-" + (System.currentTimeMillis() % 100000);
                LocalDate dDep = LocalDate.of(2026, 9, 25);
                var test = creerEtScellerDossierTest(c, mat, dDep, new BigDecimal("30"));

                // Règlement intégral préalable
                stcService.enregistrerReglement(test.idStc(), new StcReglementRequest(
                        Date.valueOf(dDep), "VIR-FULL", test.netScelle()), test.adminId(), c);

                // Clôture normale (dérogation = false)
                dossierSortieService.cloturerDossier(test.idDossier(), new ClotureDossierRequest("Clôture sans dérogation"), test.adminId(), c);

                // Tentative d'enregistrement d'un nouveau versement
                StcReglementRequest reqPost = new StcReglementRequest(
                        Date.valueOf(dDep.plusDays(2)), "VIR-POST-ILLEGAL", new BigDecimal("5000.00"));
                SirhBusinessException ex = assertThrows(SirhBusinessException.class, () ->
                        stcService.enregistrerReglement(test.idStc(), reqPost, test.adminId(), c));

                assertEquals(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED, ex.getErrorCode());
                assertTrue(ex.getMessage().contains("ne fait l'objet d'aucune dérogation"));

                c.rollback();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    @Test
    @DisplayName("4. Refus de dérogation sans justification : rejet strict avec VALIDATION_ERROR si justification vide")
    void testRefusDerogationSansJustification() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                String mat = "E-CLOT-DER-NOK-" + (System.currentTimeMillis() % 100000);
                LocalDate dDep = LocalDate.of(2026, 9, 25);
                var test = creerEtScellerDossierTest(c, mat, dDep, new BigDecimal("30"));

                // Clôture avec flag dérogation mais justification vide
                ClotureDossierRequest reqSansJustif = new ClotureDossierRequest(
                        "Clôture par dérogation abusive",
                        true,
                        "   ",
                        true
                );

                SirhBusinessException ex = assertThrows(SirhBusinessException.class, () ->
                        dossierSortieService.cloturerDossier(test.idDossier(), reqSansJustif, test.adminId(), c));

                assertEquals(SirhErrorCode.VALIDATION_ERROR, ex.getErrorCode());
                assertTrue(ex.getMessage().contains("justification formelle"));

                c.rollback();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    @Test
    @DisplayName("5. Refus de clôture avec formalités obligatoires manquantes : créance de prêt sans décision formelle")
    void testRefusClotureAvecPretSansDecisionFormelle() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                String mat = "E-CLOT-PRET-" + (System.currentTimeMillis() % 100000);
                LocalDate dDep = LocalDate.of(2026, 9, 25);
                var test = creerEtScellerDossierTest(c, mat, dDep, new BigDecimal("30"));

                // Règlement intégral
                stcService.enregistrerReglement(test.idStc(), new StcReglementRequest(
                        Date.valueOf(dDep), "VIR-FULL", test.netScelle()), test.adminId(), c);

                // Insertion d'un prêt avec capital restant dû
                try (PreparedStatement psPret = c.prepareStatement(
                        "INSERT INTO paie_pret (id_employe, reference_pret, montant_total, montant_mensualite, nb_echeances, " +
                        "date_premiere_echeance, capital_rembourse, capital_restant_du, statut, motif) " +
                        "VALUES (?, 'PRET-TEST-CLOT', 600000.00, 60000.00, 10, '2026-01-01', 100000.00, 500000.00, 'EN_COURS', 'Motif test')")) {
                    psPret.setInt(1, test.idEmploye());
                    psPret.executeUpdate();
                }

                // Effacer la décision sur le prêt dans le dossier de sortie
                try (PreparedStatement psUpDos = c.prepareStatement(
                        "UPDATE paie_dossier_sortie SET decision_pret = NULL, justification_decision_pret = NULL WHERE id_dossier_sortie = ?")) {
                    psUpDos.setInt(1, test.idDossier());
                    psUpDos.executeUpdate();
                }

                ClotureDossierRequest reqClot = new ClotureDossierRequest("Clôture avec prêt orphelin");
                SirhBusinessException ex = assertThrows(SirhBusinessException.class, () ->
                        dossierSortieService.cloturerDossier(test.idDossier(), reqClot, test.adminId(), c));

                assertEquals(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED, ex.getErrorCode());
                assertTrue(ex.getMessage().contains("prêt"), "L'erreur doit bloquer pour prêt d'entreprise non arbitré");

                c.rollback();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    @Test
    @DisplayName("6. Continuité contractuelle : préservation d'un autre contrat ACTIF (employé reste actif)")
    void testCloturePreserveAutreContratActif() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                String mat = "E-CLOT-MULTI-" + (System.currentTimeMillis() % 100000);
                LocalDate dDep = LocalDate.of(2026, 9, 25);
                var test = creerEtScellerDossierTest(c, mat, dDep, new BigDecimal("30"));

                // Règlement intégral
                stcService.enregistrerReglement(test.idStc(), new StcReglementRequest(
                        Date.valueOf(dDep), "VIR-MULTI-01", test.netScelle()), test.adminId(), c);

                // Création d'un second contrat actif pour cet employé (ex: autre mandat ou contrat cumulé)
                int secondContratId = CongeTestFixtureHelper.createContratActif(c, test.idEmploye(), LocalDate.of(2025, 6, 1));
                CongeTestFixtureHelper.createAffectation(c, test.idEmploye(), secondContratId, LocalDate.of(2025, 6, 1), new BigDecimal("500000.00"));

                // Clôture administrative du dossier de sortie
                ClotureDossierRequest reqClot = new ClotureDossierRequest("Fin du contrat 1, maintien du contrat 2");
                DossierSortieDTO cloture = dossierSortieService.cloturerDossier(test.idDossier(), reqClot, test.adminId(), c);
                assertEquals("CLOTURE", cloture.getStatutDossier());

                // Le premier contrat doit être cessé
                try (PreparedStatement psCt1 = c.prepareStatement(
                        "SELECT s.code FROM contrat_travail ct JOIN ref_statut_rh s ON ct.id_statut_rh = s.id_statut_rh WHERE ct.id_contrat = ?")) {
                    psCt1.setInt(1, test.idContrat());
                    try (ResultSet rs = psCt1.executeQuery()) {
                        assertTrue(rs.next());
                        assertTrue(List.of("ROMPU", "CLOTURE").contains(rs.getString("code")));
                    }
                }

                // Le second contrat doit rester ACTIF
                try (PreparedStatement psCt2 = c.prepareStatement(
                        "SELECT s.code FROM contrat_travail ct JOIN ref_statut_rh s ON ct.id_statut_rh = s.id_statut_rh WHERE ct.id_contrat = ?")) {
                    psCt2.setInt(1, secondContratId);
                    try (ResultSet rs = psCt2.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("ACTIF", rs.getString("code"));
                    }
                }

                // L'employé doit RESTER ACTIF car il détient toujours un contrat en cours
                try (PreparedStatement psEmp = c.prepareStatement("SELECT est_actif FROM employe WHERE id_employe = ?")) {
                    psEmp.setInt(1, test.idEmploye());
                    try (ResultSet rs = psEmp.executeQuery()) {
                        assertTrue(rs.next());
                        assertTrue(rs.getBoolean("est_actif"), "L'employé avec un autre contrat actif ne doit PAS être désactivé");
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
    @DisplayName("7. Contrat futur confirmé : préservation du statut actif du salarié (réembauche / continuité)")
    void testCloturePreserveContratFuturConfirme() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                String mat = "E-CLOT-FUTUR-" + (System.currentTimeMillis() % 100000);
                LocalDate dDep = LocalDate.of(2026, 9, 25);
                var test = creerEtScellerDossierTest(c, mat, dDep, new BigDecimal("30"));

                // Règlement intégral
                stcService.enregistrerReglement(test.idStc(), new StcReglementRequest(
                        Date.valueOf(dDep), "VIR-FUTUR-01", test.netScelle()), test.adminId(), c);

                // Création d'un contrat futur confirmé (statut PLANIFIE ou A_SIGNER débutant après dDep)
                int contratFuturId;
                String insFutur = "INSERT INTO contrat_travail (numero_contrat, id_employe, id_filiale, id_type_contrat, id_type_intervenant, date_debut, id_statut_rh) " +
                        "VALUES (?, ?, 1, 1, 1, ?, (SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'PLANIFIE')) RETURNING id_contrat";
                try (PreparedStatement ps = c.prepareStatement(insFutur)) {
                    ps.setString(1, "CT-FUTUR-" + (System.currentTimeMillis() % 100000));
                    ps.setInt(2, test.idEmploye());
                    ps.setDate(3, Date.valueOf(dDep.plusDays(10)));
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        contratFuturId = rs.getInt(1);
                    }
                }

                // Clôture administrative
                ClotureDossierRequest reqClot = new ClotureDossierRequest("Départ du contrat actuel, réembauche planifiée");
                DossierSortieDTO cloture = dossierSortieService.cloturerDossier(test.idDossier(), reqClot, test.adminId(), c);
                assertEquals("CLOTURE", cloture.getStatutDossier());

                // L'employé doit RESTER ACTIF car une continuité future confirmée existe
                try (PreparedStatement psEmp = c.prepareStatement("SELECT est_actif FROM employe WHERE id_employe = ?")) {
                    psEmp.setInt(1, test.idEmploye());
                    try (ResultSet rs = psEmp.executeQuery()) {
                        assertTrue(rs.next());
                        assertTrue(rs.getBoolean("est_actif"), "L'employé avec un contrat futur confirmé ne doit PAS être désactivé");
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
    @DisplayName("7.bis Contrat futur A_SIGNER non confirmé : ne constitue pas une continuité confirmée -> désactivation")
    void testClotureNeConsiderePasContratASignerCommeContinuiteConfirmee() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                String mat = "E-CLOT-ASIGN-" + (System.currentTimeMillis() % 100000);
                LocalDate dDep = LocalDate.of(2026, 9, 25);
                var test = creerEtScellerDossierTest(c, mat, dDep, new BigDecimal("30"));

                // Règlement intégral
                stcService.enregistrerReglement(test.idStc(), new StcReglementRequest(
                        Date.valueOf(dDep), "VIR-ASIGN-01", test.netScelle()), test.adminId(), c);

                // Création d'une simple proposition de contrat non signée (statut A_SIGNER)
                String insASigner = "INSERT INTO contrat_travail (numero_contrat, id_employe, id_filiale, id_type_contrat, id_type_intervenant, date_debut, id_statut_rh) " +
                        "VALUES (?, ?, 1, 1, 1, ?, (SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'A_SIGNER')) RETURNING id_contrat";
                try (PreparedStatement ps = c.prepareStatement(insASigner)) {
                    ps.setString(1, "CT-ASIGN-" + (System.currentTimeMillis() % 100000));
                    ps.setInt(2, test.idEmploye());
                    ps.setDate(3, Date.valueOf(dDep.plusDays(10)));
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                    }
                }

                // Clôture administrative
                ClotureDossierRequest reqClot = new ClotureDossierRequest("Départ sans continuité confirmée");
                DossierSortieDTO cloture = dossierSortieService.cloturerDossier(test.idDossier(), reqClot, test.adminId(), c);
                assertEquals("CLOTURE", cloture.getStatutDossier());

                // Un contrat A_SIGNER non signé ne constitue PAS une continuité confirmée -> salarié DÉSACTIVÉ
                try (PreparedStatement psEmp = c.prepareStatement("SELECT est_actif FROM employe WHERE id_employe = ?")) {
                    psEmp.setInt(1, test.idEmploye());
                    try (ResultSet rs = psEmp.executeQuery()) {
                        assertTrue(rs.next());
                        assertFalse(rs.getBoolean("est_actif"), "Un contrat A_SIGNER non confirmé ne doit PAS empêcher la désactivation");
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
    @DisplayName("8. Rejet d'une seconde clôture : interdiction de re-clôturer un dossier déjà CLOTURE")
    void testRejetSecondeCloture() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                String mat = "E-CLOT-DBL-" + (System.currentTimeMillis() % 100000);
                LocalDate dDep = LocalDate.of(2026, 9, 25);
                var test = creerEtScellerDossierTest(c, mat, dDep, new BigDecimal("30"));

                // Règlement intégral
                stcService.enregistrerReglement(test.idStc(), new StcReglementRequest(
                        Date.valueOf(dDep), "VIR-DBL-01", test.netScelle()), test.adminId(), c);

                // Première clôture : succès
                ClotureDossierRequest reqClot = new ClotureDossierRequest("Première clôture légitime");
                DossierSortieDTO clot1 = dossierSortieService.cloturerDossier(test.idDossier(), reqClot, test.adminId(), c);
                assertEquals("CLOTURE", clot1.getStatutDossier());

                // Deuxième clôture : rejet strict
                SirhBusinessException ex = assertThrows(SirhBusinessException.class, () ->
                        dossierSortieService.cloturerDossier(test.idDossier(), reqClot, test.adminId(), c));

                assertEquals(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED, ex.getErrorCode());
                assertTrue(ex.getMessage().contains("déjà clôturé"));

                c.rollback();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    @Test
    @DisplayName("9. Rejet des modifications interdites après clôture : verrouillage étanche sur tout le module")
    void testRejetModificationsInterditesApresCloture() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                String mat = "E-CLOT-LOCK-" + (System.currentTimeMillis() % 100000);
                LocalDate dDep = LocalDate.of(2026, 9, 25);
                var test = creerEtScellerDossierTest(c, mat, dDep, new BigDecimal("30"));

                // Règlement intégral
                stcService.enregistrerReglement(test.idStc(), new StcReglementRequest(
                        Date.valueOf(dDep), "VIR-LOCK-01", test.netScelle()), test.adminId(), c);

                // Clôture
                dossierSortieService.cloturerDossier(test.idDossier(), new ClotureDossierRequest("Clôturé"), test.adminId(), c);

                // 1. Sauvegarde préparatoire interdite
                DossierSortieSaveRequest saveReq = new DossierSortieSaveRequest();
                saveReq.setIdDossierSortie(test.idDossier());
                saveReq.setIdEmploye(test.idEmploye());
                saveReq.setDateDepart(Date.valueOf(dDep));
                SirhBusinessException exSave = assertThrows(SirhBusinessException.class, () ->
                        dossierSortieService.sauvegarderDossier(saveReq, test.adminId(), c));
                assertEquals(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED, exSave.getErrorCode());

                // 2. Modification certificat interdite
                SirhBusinessException exCert = assertThrows(SirhBusinessException.class, () ->
                        dossierSortieService.enregistrerDelivranceCertificat(test.idDossier(), Date.valueOf(dDep), "COURRIER", test.adminId(), c));
                assertEquals(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED, exCert.getErrorCode());

                // 3. Modification offboarding interdite
                SirhBusinessException exOff = assertThrows(SirhBusinessException.class, () ->
                        dossierSortieService.mettreAJourOffboarding(test.idDossier(), Map.of("badge", true), test.adminId(), c));
                assertEquals(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED, exOff.getErrorCode());

                // 4. Ajout de versement interdit
                StcReglementRequest reqReg = new StcReglementRequest(Date.valueOf(dDep), "VIR-ILLEGAL", new BigDecimal("1000.00"));
                SirhBusinessException exReg = assertThrows(SirhBusinessException.class, () ->
                        dossierSortieService.enregistrerReglementDepuisDossier(test.idDossier(), reqReg, test.adminId(), c));
                assertEquals(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED, exReg.getErrorCode());

                c.rollback();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    @Test
    @DisplayName("10. Inviolabilité du STC scellé : les montants et le décompte de paie_stc restent strictement inchangés")
    void testInviolabiliteStcFinancierApresCloture() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                String mat = "E-CLOT-SEAL-" + (System.currentTimeMillis() % 100000);
                LocalDate dDep = LocalDate.of(2026, 9, 25);
                var test = creerEtScellerDossierTest(c, mat, dDep, new BigDecimal("30"));

                // État du STC avant règlement et clôture
                PaieStc stcAvant = PaieStc.trouverParId(test.idStc(), c);
                assertNotNull(stcAvant);
                BigDecimal brutAvant = stcAvant.getTotalBrutStc();
                BigDecimal netAvant = stcAvant.getNetAPayerStc();
                BigDecimal irsaAvant = stcAvant.getTotalIrsa();
                BigDecimal cotisAvant = stcAvant.getTotalCotisationsSociales();

                // Règlement intégral
                stcService.enregistrerReglement(test.idStc(), new StcReglementRequest(
                        Date.valueOf(dDep), "VIR-SEAL-01", test.netScelle()), test.adminId(), c);

                // Clôture administrative
                dossierSortieService.cloturerDossier(test.idDossier(), new ClotureDossierRequest("Clôturé conforme"), test.adminId(), c);

                // État du STC après clôture : AUCUNE modification financière
                PaieStc stcApres = PaieStc.trouverParId(test.idStc(), c);
                assertNotNull(stcApres);
                assertEquals(0, brutAvant.compareTo(stcApres.getTotalBrutStc()), "Le brut scellé ne doit jamais être altéré");
                assertEquals(0, netAvant.compareTo(stcApres.getNetAPayerStc()), "Le net à payer scellé ne doit jamais être altéré");
                assertEquals(0, irsaAvant.compareTo(stcApres.getTotalIrsa()), "L'IRSA scellé ne doit jamais être altéré");
                assertEquals(0, cotisAvant.compareTo(stcApres.getTotalCotisationsSociales()), "Les cotisations scellées ne doivent jamais être altérées");

                c.rollback();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    @Test
    @DisplayName("11. Rollback transactionnel : aucune persistance partielle en cas d'erreur lors de la clôture")
    void testRollbackTransactionnelEnCasDErreur() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                String mat = "E-CLOT-RBK-" + (System.currentTimeMillis() % 100000);
                LocalDate dDep = LocalDate.of(2026, 9, 25);
                var test = creerEtScellerDossierTest(c, mat, dDep, new BigDecimal("30"));

                // Tentative de clôture invalide (règlement incomplet sans dérogation)
                ClotureDossierRequest reqErr = new ClotureDossierRequest("Clôture en échec");
                assertThrows(SirhBusinessException.class, () ->
                        dossierSortieService.cloturerDossier(test.idDossier(), reqErr, test.adminId(), c));

                // Vérifier que le statut du dossier est toujours VALIDE
                try (PreparedStatement psDos = c.prepareStatement("SELECT statut_dossier, date_cloture FROM paie_dossier_sortie WHERE id_dossier_sortie = ?")) {
                    psDos.setInt(1, test.idDossier());
                    try (ResultSet rs = psDos.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("VALIDE", rs.getString("statut_dossier"));
                        assertNull(rs.getTimestamp("date_cloture"));
                    }
                }

                // Vérifier que le contrat est toujours ACTIF
                try (PreparedStatement psCt = c.prepareStatement(
                        "SELECT s.code FROM contrat_travail ct JOIN ref_statut_rh s ON ct.id_statut_rh = s.id_statut_rh WHERE ct.id_contrat = ?")) {
                    psCt.setInt(1, test.idContrat());
                    try (ResultSet rs = psCt.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("ROMPU", rs.getString("code"));
                    }
                }

                // Vérifier que l'employé est toujours actif
                try (PreparedStatement psEmp = c.prepareStatement("SELECT est_actif FROM employe WHERE id_employe = ?")) {
                    psEmp.setInt(1, test.idEmploye());
                    try (ResultSet rs = psEmp.executeQuery()) {
                        assertTrue(rs.next());
                        assertTrue(rs.getBoolean("est_actif"));
                    }
                }

                c.rollback();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }
}
