package mg.galaxam.sirh.paie.application;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.common.exception.SirhErrorCode;
import mg.galaxam.sirh.conge.testutils.CongeTestFixtureHelper;
import java.sql.ResultSet;
import mg.galaxam.sirh.paie.api.dto.stc.ChecklistItemDTO;
import mg.galaxam.sirh.paie.api.dto.stc.DossierSortieDTO;
import mg.galaxam.sirh.paie.api.dto.stc.DossierSortieSaveRequest;
import mg.galaxam.sirh.paie.api.dto.stc.ScellementStcRequest;
import mg.galaxam.sirh.paie.api.dto.stc.SimulationStcResultDTO;
import mg.galaxam.sirh.paie.api.dto.stc.StcReglementRequest;
import mg.galaxam.sirh.paie.persistence.PaiePret;
import mg.galaxam.sirh.paie.domain.engine.MoneyRoundingPolicy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Tests d'intégration validant le dossier de sortie (Phase 1) :
 * 1. Persistance du dossier préparatoire sans effet financier destructeur
 * 2. Évaluation de la checklist dynamique (Loi 2024, Galaxam, Technique SI)
 * 3. Délivrance du certificat de travail (Art. 50) indépendamment du STC scellé
 * 4. Gestion de la décision sur le prêt sans retenue automatique sauvage
 * 5. Traçabilité des formalités d'offboarding
 */
@SpringBootTest
@ActiveProfiles("test")
class DossierSortieIntegrationTest {

    @Autowired private DataSource dataSource;
    @Autowired private DossierSortieService dossierSortieService;
    @Autowired private StcService stcService;

    @Test
    @DisplayName("Dossier de sortie : persistance préparatoire, lecture sources et délivrance certificat")
    void testCycleDossierSortiePhase1() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // 1. Fixture utilisateur et employé
                int adminId = CongeTestFixtureHelper.createAdminUser(c, "rh_dossier_sortie_" + System.currentTimeMillis());
                int empId = CongeTestFixtureHelper.createEmploye(c, "EMP-DS-01", "Andria", "Hery");
                int contratId = CongeTestFixtureHelper.createContratActif(c, empId, LocalDate.of(2023, 1, 1));
                CongeTestFixtureHelper.createAffectation(c, empId, contratId, LocalDate.of(2023, 1, 1), new BigDecimal("1500000.00"));

                LocalDate dateDepart = LocalDate.of(2026, 10, 10);
                try (PreparedStatement ps = c.prepareStatement("UPDATE contrat_travail SET date_fin = ? WHERE id_contrat = ?")) {
                    ps.setDate(1, Date.valueOf(dateDepart));
                    ps.setInt(2, contratId);
                    ps.executeUpdate();
                }

                // 2. Prêt en cours pour tester la détection et l'arbitrage
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO paie_pret (id_employe, reference_pret, montant_total, montant_mensualite, nb_echeances, " +
                        "date_premiere_echeance, capital_rembourse, capital_restant_du, statut, motif) " +
                        "VALUES (?, 'PRET-DS-01', 500000.00, 50000.00, 10, '2026-01-01', 200000.00, 300000.00, 'EN_COURS', 'Équipement')")) {
                    ps.setInt(1, empId);
                    ps.executeUpdate();
                }

                // =============================================================
                // ÉTAPE 1 : Sauvegarde d'un dossier préparatoire (brouillon)
                // =============================================================
                DossierSortieSaveRequest saveReq = new DossierSortieSaveRequest();
                saveReq.setIdEmploye(empId);
                saveReq.setIdContrat(contratId);
                saveReq.setDateDepart(Date.valueOf(dateDepart));
                saveReq.setMotifRupture("DEMISSION");
                saveReq.setStatutDossier("EN_PREPARATION");

                Map<String, Object> prepForm = new HashMap<>();
                prepForm.put("modeReglement", "VIREMENT");
                prepForm.put("justificationSalaireDernierMois", "Présence constatée et vérifiée par DAF");
                prepForm.put("moisPreavis", "1");
                saveReq.setDonneesPreparation(prepForm);

                // Décision formelle sur le prêt (sans retenue automatique sauvage)
                saveReq.setDecisionPret("MAINTIEN_ECHEANCIER");
                saveReq.setJustificationDecisionPret("Reconnaissance de dette n° RD-2026-01 avec prélèvements bancaires");

                DossierSortieDTO created = dossierSortieService.sauvegarderDossier(saveReq, adminId, c);
                assertNotNull(created);
                assertNotNull(created.getIdDossierSortie());
                assertEquals("EN_PREPARATION", created.getStatutDossier());
                assertEquals("ACTIF", created.getStatutContrat(),
                        "La date de fin seule ne doit pas être présentée comme une cessation effective.");
                assertEquals("Andria Hery", created.getNomComplet());
                assertEquals("DEMISSION", created.getMotifRupture());
                assertEquals("MAINTIEN_ECHEANCIER", created.getDecisionPret());
                assertEquals(0, new BigDecimal("300000.00").compareTo(created.getCapitalRestantPrets()));

                // Vérifier que la simulation et la préparation n'ont pas créé de STC financier
                assertNull(created.getIdStc());

                // =============================================================
                // ÉTAPE 2 : Vérification de la Checklist dynamique
                // =============================================================
                assertNotNull(created.getChecklist());
                assertFalse(created.getChecklist().isEmpty());

                // L-01 (Date départ passée ou aujourd'hui) -> Conforme
                ChecklistItemDTO itemDate = created.getChecklist().stream()
                        .filter(i -> "L-01_DATE_DEPART".equals(i.getCode())).findFirst().orElse(null);
                assertNotNull(itemDate);
                assertEquals(ChecklistItemDTO.StatutCheck.CONFORME, itemDate.getStatut());

                // L-02 (Contrat concordant) -> Conforme
                ChecklistItemDTO itemContrat = created.getChecklist().stream()
                        .filter(i -> "L-02_CONCORDANCE_CONTRAT".equals(i.getCode())).findFirst().orElse(null);
                assertNotNull(itemContrat);
                assertEquals(ChecklistItemDTO.StatutCheck.CONFORME, itemContrat.getStatut());

                // G-01 (Décision sur le prêt) -> Conforme car decisionPret est renseignée
                ChecklistItemDTO itemPret = created.getChecklist().stream()
                        .filter(i -> "G-01_DECISION_PRET".equals(i.getCode())).findFirst().orElse(null);
                assertNotNull(itemPret);
                assertEquals(ChecklistItemDTO.StatutCheck.CONFORME, itemPret.getStatut());

                // =============================================================
                // ÉTAPE 3 : Délivrance du Certificat de Travail (Art. 50)
                // =============================================================
                assertFalse(created.isCertificatDelivre());
                assertThrows(SirhBusinessException.class,
                        () -> dossierSortieService.enregistrerDelivranceCertificat(
                                created.getIdDossierSortie(), Date.valueOf(dateDepart), "MAIN_PROPRE", adminId, c),
                        "Une date de fin renseignée ne suffit pas tant que le contrat reste ACTIF.");
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE contrat_travail SET id_statut_rh = " +
                        "(SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'ROMPU') WHERE id_contrat = ?")) {
                    ps.setInt(1, contratId);
                    ps.executeUpdate();
                }
                dossierSortieService.enregistrerDelivranceCertificat(
                        created.getIdDossierSortie(), Date.valueOf(dateDepart), "MAIN_PROPRE", adminId, c);

                DossierSortieDTO reloaded = dossierSortieService.obtenirDossier(created.getIdDossierSortie(), c);
                assertTrue(reloaded.isCertificatDelivre());
                assertEquals(Date.valueOf(dateDepart), reloaded.getDateDelivranceCertificat());
                assertEquals("MAIN_PROPRE", reloaded.getModeRemiseCertificat());

                // =============================================================
                // ÉTAPE 4 : Formalités d'offboarding
                // =============================================================
                Map<String, Object> offboarding = new HashMap<>();
                offboarding.put("badgeRestitue", true);
                offboarding.put("materielPcRestitue", true);
                offboarding.put("accesMailDesactive", true);
                dossierSortieService.mettreAJourOffboarding(created.getIdDossierSortie(), offboarding, adminId, c);

                DossierSortieDTO offReloaded = dossierSortieService.obtenirDossier(created.getIdDossierSortie(), c);
                assertNotNull(offReloaded.getChecklistOffboarding());
                assertEquals(true, offReloaded.getChecklistOffboarding().get("badgeRestitue"));
                assertEquals(true, offReloaded.getChecklistOffboarding().get("materielPcRestitue"));

                // =============================================================
                // ÉTAPE 5 : Idempotence de la persistance (mise à jour du même dossier)
                // =============================================================
                saveReq.setIdDossierSortie(created.getIdDossierSortie());
                saveReq.setStatutDossier("PRET_A_VALIDER");
                DossierSortieDTO updated = dossierSortieService.sauvegarderDossier(saveReq, adminId, c);
                assertEquals(created.getIdDossierSortie(), updated.getIdDossierSortie(), "L'idDossier doit rester identique");
                assertEquals("PRET_A_VALIDER", updated.getStatutDossier());

            } finally {
                c.rollback(); // Isolation stricte de la base de test
            }
        }
    }

    @Test
    @DisplayName("États & Sécurité : rejet des transitions directes vers VALIDE, REGLE ou CLOTURE")
    void testProtectionEtatsTransitionsInterdites() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                int adminId = CongeTestFixtureHelper.createAdminUser(c, "rh_secu_" + System.currentTimeMillis());
                int empId = CongeTestFixtureHelper.createEmploye(c, "EMP-SECU-01", "Rabe", "Jean");
                LocalDate dateDepart = LocalDate.of(2026, 10, 15);

                DossierSortieSaveRequest req = new DossierSortieSaveRequest();
                req.setIdEmploye(empId);
                req.setDateDepart(Date.valueOf(dateDepart));
                req.setMotifRupture("DEMISSION");

                // Tentative 1 : forcer le statut VALIDE par API
                req.setStatutDossier("VALIDE");
                SirhBusinessException exValide = assertThrows(SirhBusinessException.class, () ->
                        dossierSortieService.sauvegarderDossier(req, adminId, c));
                assertEquals(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED, exValide.getErrorCode());

                // Tentative 2 : forcer le statut CLOTURE par API
                req.setStatutDossier("CLOTURE");
                SirhBusinessException exCloture = assertThrows(SirhBusinessException.class, () ->
                        dossierSortieService.sauvegarderDossier(req, adminId, c));
                assertEquals(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED, exCloture.getErrorCode());

                // Tentative 3 : modifier un dossier clôturé
                req.setStatutDossier("EN_PREPARATION");
                DossierSortieDTO saved = dossierSortieService.sauvegarderDossier(req, adminId, c);

                // Simulation clôture en base
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE paie_dossier_sortie SET statut_dossier = 'CLOTURE' WHERE id_dossier_sortie = ?")) {
                    ps.setInt(1, saved.getIdDossierSortie());
                    ps.executeUpdate();
                }

                // La tentative de modification d'un dossier clôturé doit être rejetée
                req.setIdDossierSortie(saved.getIdDossierSortie());
                req.setMotifRupture("LICENCIEMENT_SIMPLE");
                SirhBusinessException exModifClos = assertThrows(SirhBusinessException.class, () ->
                        dossierSortieService.sauvegarderDossier(req, adminId, c));
                assertEquals(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED, exModifClos.getErrorCode());

            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("Prêts & Trésorerie : la décision documentée ne modifie ni ne solde le prêt")
    void testImmutabilitePretLorsEnregistrementDecision() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                int adminId = CongeTestFixtureHelper.createAdminUser(c, "rh_pret_" + System.currentTimeMillis());
                int empId = CongeTestFixtureHelper.createEmploye(c, "EMP-PRET-01", "Soa", "Marie");
                LocalDate dateDepart = LocalDate.of(2026, 10, 20);

                int idPret;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO paie_pret (id_employe, reference_pret, montant_total, montant_mensualite, nb_echeances, " +
                        "date_premiere_echeance, capital_rembourse, capital_restant_du, statut, motif) " +
                        "VALUES (?, 'PRET-TEST-IMMUTABLE', 1000000.00, 100000.00, 10, '2026-01-01', 400000.00, 600000.00, 'EN_COURS', 'Équipement') " +
                        "RETURNING id_pret")) {
                    ps.setInt(1, empId);
                    try (var rs = ps.executeQuery()) {
                        rs.next();
                        idPret = rs.getInt(1);
                    }
                }

                BigDecimal capitalAvant = PaiePret.calculerCapitalRestantTotal(empId, c);
                assertEquals(0, new BigDecimal("600000.00").compareTo(capitalAvant));

                // Sauvegarde d'un dossier avec retenue souhaitée de 200 000 Ar
                DossierSortieSaveRequest req = new DossierSortieSaveRequest();
                req.setIdEmploye(empId);
                req.setDateDepart(Date.valueOf(dateDepart));
                req.setMotifRupture("DEMISSION");
                req.setStatutDossier("EN_PREPARATION");
                req.setDecisionPret("RETENUE_STC_AUTORISEE");
                req.setJustificationDecisionPret("Accord écrit pour retenue dans la limite légale");
                req.setMontantPretRetenu(new BigDecimal("200000.00"));

                DossierSortieDTO dto = dossierSortieService.sauvegarderDossier(req, adminId, c);
                assertEquals("RETENUE_STC_AUTORISEE", dto.getDecisionPret());

                // Vérification stricte en BDD : aucun changement dans paie_pret
                try (PreparedStatement psCheck = c.prepareStatement(
                        "SELECT statut, capital_rembourse, capital_restant_du FROM paie_pret WHERE id_pret = ?")) {
                    psCheck.setInt(1, idPret);
                    try (var rs = psCheck.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("EN_COURS", rs.getString("statut"), "Le statut du prêt doit rester EN_COURS (pas de passage aveugle à SOLDE)");
                        assertEquals(0, new BigDecimal("400000.00").compareTo(rs.getBigDecimal("capital_rembourse")));
                        assertEquals(0, new BigDecimal("600000.00").compareTo(rs.getBigDecimal("capital_restant_du")));
                    }
                }

                BigDecimal capitalApres = PaiePret.calculerCapitalRestantTotal(empId, c);
                assertEquals(0, capitalAvant.compareTo(capitalApres), "Le capital restant du prêt n'a subi aucune altération");

            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("Actualisation dynamique : les données sources modifiées sont immédiatement visibles à la réouverture")
    void testActualisationDynamiqueDesSources() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                int adminId = CongeTestFixtureHelper.createAdminUser(c, "rh_actu_" + System.currentTimeMillis());
                int empId = CongeTestFixtureHelper.createEmploye(c, "EMP-ACTU-01", "Randria", "Paul");
                LocalDate dateDepart = LocalDate.of(2026, 10, 25);

                DossierSortieSaveRequest req = new DossierSortieSaveRequest();
                req.setIdEmploye(empId);
                req.setDateDepart(Date.valueOf(dateDepart));
                req.setMotifRupture("DEMISSION");
                req.setStatutDossier("EN_PREPARATION");

                DossierSortieDTO dto = dossierSortieService.sauvegarderDossier(req, adminId, c);
                assertEquals(0, dto.getDemandesCongesEnAttente());

                // Création d'une demande de congé en attente
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO demande_conge (id_employe, id_type_conge, date_demande, date_debut, date_fin, nb_jours_decomptes, id_statut_demande_conge) " +
                        "VALUES (?, (SELECT id_type_conge FROM ref_type_conge LIMIT 1), CURRENT_DATE, '2026-10-18', '2026-10-20', 2.00, (SELECT id_statut_demande_conge FROM ref_statut_demande_conge WHERE code = 'EN_ATTENTE' LIMIT 1))")) {
                    ps.setInt(1, empId);
                    ps.executeUpdate();
                }

                // Réouverture du dossier : la modification doit être immédiatement reflétée
                DossierSortieDTO reloaded = dossierSortieService.obtenirDossier(dto.getIdDossierSortie(), c);
                assertEquals(1, reloaded.getDemandesCongesEnAttente(), "La demande en attente ajoutée doit être reflétée dynamiquement");

                // La checklist doit basculer W-01 en A_VERIFIER
                ChecklistItemDTO itemConge = reloaded.getChecklist().stream()
                        .filter(i -> "W-01_CONGES_EN_ATTENTE".equals(i.getCode())).findFirst().orElse(null);
                assertNotNull(itemConge);
                assertEquals(ChecklistItemDTO.StatutCheck.A_VERIFIER, itemConge.getStatut());

            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("Validation : rejet si l'employé est inexistant ou les champs requis sont absents")
    void testValidationEmployeInexistantEtChampsObligatoires() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                int adminId = CongeTestFixtureHelper.createAdminUser(c, "rh_val_" + System.currentTimeMillis());

                // Cas 1 : idEmploye inexistant
                DossierSortieSaveRequest req1 = new DossierSortieSaveRequest();
                req1.setIdEmploye(999999);
                req1.setDateDepart(Date.valueOf(LocalDate.of(2026, 10, 1)));
                req1.setMotifRupture("DEMISSION");

                SirhBusinessException ex1 = assertThrows(SirhBusinessException.class, () ->
                        dossierSortieService.sauvegarderDossier(req1, adminId, c));
                assertEquals(SirhErrorCode.RESOURCE_NOT_FOUND, ex1.getErrorCode());

                // Cas 2 : dateDepart null
                DossierSortieSaveRequest req2 = new DossierSortieSaveRequest();
                req2.setIdEmploye(1);
                req2.setDateDepart(null);

                SirhBusinessException ex2 = assertThrows(SirhBusinessException.class, () ->
                        dossierSortieService.sauvegarderDossier(req2, adminId, c));
                assertEquals(SirhErrorCode.VALIDATION_ERROR, ex2.getErrorCode());

            } finally {
                c.rollback();
            }
        }
    }

    // =========================================================================
    // TESTS PHASE 2 : SIMULATION, FRAÎCHEUR, OPTIMISTIC LOCK & SCELLEMENT
    // =========================================================================

    @Test
    @DisplayName("Phase 2 : Simulation déterministe avec référence vérifiable et scellement transactionnel complet")
    void testPhase2SimulationEtScellementConforme() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                int adminId = CongeTestFixtureHelper.createAdminUser(c, "rh_p2_" + System.currentTimeMillis());
                int empId = CongeTestFixtureHelper.createEmploye(c, "EMP-P2-01", "Rabe", "Jean");
                LocalDate dateDepart = LocalDate.of(2026, 10, 10);
                int contratId = CongeTestFixtureHelper.createContratActif(c, empId, dateDepart.minusYears(1));
                CongeTestFixtureHelper.createAffectation(c, empId, contratId, dateDepart.minusYears(1), new BigDecimal("1200000.00"));
                try (PreparedStatement ps = c.prepareStatement("UPDATE contrat_travail SET date_fin = ? WHERE id_contrat = ?")) {
                    ps.setDate(1, Date.valueOf(dateDepart));
                    ps.setInt(2, contratId);
                    ps.executeUpdate();
                }

                // Période de paie
                int periodeId = assurerPeriodePaie(c, dateDepart);

                // Bulletin mensuel validé pour rapprochement
                creerBulletinMensuelValide(c, empId, periodeId, new BigDecimal("1200000.00"));

                // Prêt actif
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO paie_pret (id_employe, reference_pret, montant_total, montant_mensualite, nb_echeances, " +
                        "date_premiere_echeance, capital_rembourse, capital_restant_du, statut, motif) " +
                        "VALUES (?, 'PRET-P2-01', 500000.00, 50000.00, 10, '2026-01-01', 200000.00, 300000.00, 'EN_COURS', 'Équipement')")) {
                    ps.setInt(1, empId);
                    ps.executeUpdate();
                }

                // 1. Sauvegarde du dossier préparatoire avec décision de prêt documentée
                DossierSortieSaveRequest saveReq = new DossierSortieSaveRequest();
                saveReq.setIdEmploye(empId);
                saveReq.setIdContrat(contratId);
                saveReq.setDateDepart(Date.valueOf(dateDepart));
                saveReq.setMotifRupture("DEMISSION");
                saveReq.setDecisionPret("MAINTIEN_ECHEANCIER");
                saveReq.setJustificationDecisionPret("Accord amiable de maintien de l'échéancier bancaire");

                Map<String, Object> prep = new HashMap<>();
                prep.put("montantBulletinDejaPaye", "0");
                prep.put("justificationSalaireDernierMois", "Présence conforme vérifiée par DAF");
                saveReq.setDonneesPreparation(prep);

                DossierSortieDTO dossier = dossierSortieService.sauvegarderDossier(saveReq, adminId, c);
                assertNotNull(dossier.getIdDossierSortie());
                assertEquals("EN_PREPARATION", dossier.getStatutDossier());
                int initialVersion = dossier.getVersion();

                // 2. Lancement de la simulation STC
                SimulationStcResultDTO simRes = dossierSortieService.simulerStcDepuisDossier(dossier.getIdDossierSortie(), c);
                assertNotNull(simRes);
                assertNotNull(simRes.getReferenceSimulation());
                assertTrue(simRes.getReferenceSimulation().startsWith("SIM-STC-" + dossier.getIdDossierSortie()));
                assertEquals(initialVersion + 1, simRes.getVersion());
                assertNotNull(simRes.getDecompte());
                assertTrue(simRes.getDecompte().getNetAPayerStc().compareTo(BigDecimal.ZERO) > 0);
                assertNotNull(simRes.getSnapshotData());
                assertEquals("1200000.00", simRes.getSnapshotData().get("salaireBase"));

                // Le dossier doit encore être préparatoire (aucun scellement financier à ce stade)
                DossierSortieDTO reloaded = dossierSortieService.obtenirDossier(dossier.getIdDossierSortie(), c);
                assertEquals("EN_PREPARATION", reloaded.getStatutDossier());
                assertNull(reloaded.getIdStc());

                // 3. Scellement transactionnel avec confirmation explicite
                ScellementStcRequest scellementReq = new ScellementStcRequest(
                        simRes.getReferenceSimulation(),
                        simRes.getVersion(),
                        "Décompte vérifié et approuvé par la DAF"
                );
                DossierSortieDTO sealed = dossierSortieService.scellerDossierEtStc(dossier.getIdDossierSortie(), scellementReq, adminId, c);
                assertNotNull(sealed);
                assertEquals("VALIDE", sealed.getStatutDossier());
                assertNotNull(sealed.getIdStc());
                assertEquals(0, simRes.getDecompte().getNetAPayerStc().compareTo(sealed.getNetAPayerStc()));

                // Vérification en base : le STC existe et est scellé à VALIDE
                try (PreparedStatement ps = c.prepareStatement("SELECT statut, net_a_payer_stc FROM paie_stc WHERE id_stc = ?")) {
                    ps.setInt(1, sealed.getIdStc());
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("VALIDE", rs.getString("statut"));
                        assertEquals(0, simRes.getDecompte().getNetAPayerStc().compareTo(rs.getBigDecimal("net_a_payer_stc")));
                    }
                }

                // Vérification contrainte impérative : le capital restant du prêt n'a PAS été diminué automatiquement
                try (PreparedStatement ps = c.prepareStatement("SELECT capital_restant_du, statut FROM paie_pret WHERE id_employe = ?")) {
                    ps.setInt(1, empId);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(0, new BigDecimal("300000.00").compareTo(rs.getBigDecimal("capital_restant_du")));
                        assertEquals("EN_COURS", rs.getString("statut"));
                    }
                }

                // 4. Test protection double scellement : un second appel est rejeté car déjà scellé
                SirhBusinessException exDouble = assertThrows(SirhBusinessException.class, () ->
                        dossierSortieService.scellerDossierEtStc(dossier.getIdDossierSortie(), scellementReq, adminId, c));
                assertEquals(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED, exDouble.getErrorCode());

            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("Phase 2 : Rejet du scellement si la référence de simulation est invalide ou obsolète")
    void testPhase2RejetReferenceSimulationInvalide() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                int adminId = CongeTestFixtureHelper.createAdminUser(c, "rh_p2_ref_" + System.currentTimeMillis());
                int empId = CongeTestFixtureHelper.createEmploye(c, "EMP-P2-02", "Ranaivo", "Paul");
                LocalDate dateDepart = LocalDate.of(2026, 10, 10);
                int contratId = CongeTestFixtureHelper.createContratActif(c, empId, dateDepart.minusYears(1));
                CongeTestFixtureHelper.createAffectation(c, empId, contratId, dateDepart.minusYears(1), new BigDecimal("800000.00"));
                try (PreparedStatement ps = c.prepareStatement("UPDATE contrat_travail SET date_fin = ? WHERE id_contrat = ?")) {
                    ps.setDate(1, Date.valueOf(dateDepart));
                    ps.setInt(2, contratId);
                    ps.executeUpdate();
                }

                int periodeId = assurerPeriodePaie(c, dateDepart);
                creerBulletinMensuelValide(c, empId, periodeId, new BigDecimal("800000.00"));

                DossierSortieSaveRequest saveReq = new DossierSortieSaveRequest();
                saveReq.setIdEmploye(empId);
                saveReq.setIdContrat(contratId);
                saveReq.setDateDepart(Date.valueOf(dateDepart));
                saveReq.setMotifRupture("DEMISSION");
                Map<String, Object> prep = new HashMap<>();
                prep.put("montantBulletinDejaPaye", "0");
                prep.put("justificationSalaireDernierMois", "Présence constatée");
                saveReq.setDonneesPreparation(prep);

                DossierSortieDTO dossier = dossierSortieService.sauvegarderDossier(saveReq, adminId, c);
                SimulationStcResultDTO simRes = dossierSortieService.simulerStcDepuisDossier(dossier.getIdDossierSortie(), c);

                // Tentative avec une référence falsifiée
                ScellementStcRequest badReq = new ScellementStcRequest("SIM-STC-FAUSSE-REFERENCE-123", simRes.getVersion(), "Tentative frauduleuse");
                SirhBusinessException ex = assertThrows(SirhBusinessException.class, () ->
                        dossierSortieService.scellerDossierEtStc(dossier.getIdDossierSortie(), badReq, adminId, c));
                assertEquals(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED, ex.getErrorCode());

            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("Phase 2 : Rejet du scellement si la version du dossier ne correspond pas (verrouillage optimiste)")
    void testPhase2RejetConflitOptimisteVersion() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                int adminId = CongeTestFixtureHelper.createAdminUser(c, "rh_p2_ver_" + System.currentTimeMillis());
                int empId = CongeTestFixtureHelper.createEmploye(c, "EMP-P2-03", "Rakoto", "Marc");
                LocalDate dateDepart = LocalDate.of(2026, 10, 10);
                int contratId = CongeTestFixtureHelper.createContratActif(c, empId, dateDepart.minusYears(1));
                CongeTestFixtureHelper.createAffectation(c, empId, contratId, dateDepart.minusYears(1), new BigDecimal("800000.00"));
                try (PreparedStatement ps = c.prepareStatement("UPDATE contrat_travail SET date_fin = ? WHERE id_contrat = ?")) {
                    ps.setDate(1, Date.valueOf(dateDepart));
                    ps.setInt(2, contratId);
                    ps.executeUpdate();
                }

                int periodeId = assurerPeriodePaie(c, dateDepart);
                creerBulletinMensuelValide(c, empId, periodeId, new BigDecimal("800000.00"));

                DossierSortieSaveRequest saveReq = new DossierSortieSaveRequest();
                saveReq.setIdEmploye(empId);
                saveReq.setIdContrat(contratId);
                saveReq.setDateDepart(Date.valueOf(dateDepart));
                saveReq.setMotifRupture("DEMISSION");
                Map<String, Object> prep = new HashMap<>();
                prep.put("montantBulletinDejaPaye", "0");
                prep.put("justificationSalaireDernierMois", "Présence constatée");
                saveReq.setDonneesPreparation(prep);

                DossierSortieDTO dossier = dossierSortieService.sauvegarderDossier(saveReq, adminId, c);
                SimulationStcResultDTO simRes = dossierSortieService.simulerStcDepuisDossier(dossier.getIdDossierSortie(), c);

                // Version obsolète ou discordante
                ScellementStcRequest conflictReq = new ScellementStcRequest(simRes.getReferenceSimulation(), simRes.getVersion() + 5, "Version conflictuelle");
                SirhBusinessException ex = assertThrows(SirhBusinessException.class, () ->
                        dossierSortieService.scellerDossierEtStc(dossier.getIdDossierSortie(), conflictReq, adminId, c));
                assertEquals(SirhErrorCode.DATA_INTEGRITY_VIOLATION, ex.getErrorCode());

            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("Phase 2 : Rejet du scellement si une donnée métier source a changé après simulation (contrôle de fraîcheur)")
    void testPhase2RejetRuptureFraicheurMetier() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                int adminId = CongeTestFixtureHelper.createAdminUser(c, "rh_p2_fresh_" + System.currentTimeMillis());
                int empId = CongeTestFixtureHelper.createEmploye(c, "EMP-P2-04", "Razafy", "Luc");
                LocalDate dateDepart = LocalDate.of(2026, 10, 10);
                int contratId = CongeTestFixtureHelper.createContratActif(c, empId, dateDepart.minusYears(1));
                CongeTestFixtureHelper.createAffectation(c, empId, contratId, dateDepart.minusYears(1), new BigDecimal("900000.00"));
                try (PreparedStatement ps = c.prepareStatement("UPDATE contrat_travail SET date_fin = ? WHERE id_contrat = ?")) {
                    ps.setDate(1, Date.valueOf(dateDepart));
                    ps.setInt(2, contratId);
                    ps.executeUpdate();
                }

                int periodeId = assurerPeriodePaie(c, dateDepart);
                creerBulletinMensuelValide(c, empId, periodeId, new BigDecimal("900000.00"));

                DossierSortieSaveRequest saveReq = new DossierSortieSaveRequest();
                saveReq.setIdEmploye(empId);
                saveReq.setIdContrat(contratId);
                saveReq.setDateDepart(Date.valueOf(dateDepart));
                saveReq.setMotifRupture("DEMISSION");
                Map<String, Object> prep = new HashMap<>();
                prep.put("montantBulletinDejaPaye", "0");
                prep.put("justificationSalaireDernierMois", "Présence constatée");
                saveReq.setDonneesPreparation(prep);

                DossierSortieDTO dossier = dossierSortieService.sauvegarderDossier(saveReq, adminId, c);
                SimulationStcResultDTO simRes = dossierSortieService.simulerStcDepuisDossier(dossier.getIdDossierSortie(), c);

                // Altération furtive d'un champ métier utilisé dans le calcul (salaire de base)
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE employe_affectation_carriere SET salaire_base = 1500000.00 WHERE id_employe = ?")) {
                    ps.setInt(1, empId);
                    ps.executeUpdate();
                }

                // Tentative de scellement avec la référence issue de la simulation initiale
                ScellementStcRequest sealReq = new ScellementStcRequest(simRes.getReferenceSimulation(), simRes.getVersion(), "Scellement sur données obsolètes");
                SirhBusinessException ex = assertThrows(SirhBusinessException.class, () ->
                        dossierSortieService.scellerDossierEtStc(dossier.getIdDossierSortie(), sealReq, adminId, c));
                assertEquals(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED, ex.getErrorCode());
                assertTrue(ex.getMessage().contains("salaire de base") || ex.getMessage().contains("fraîcheur"),
                        "Le message d'erreur doit expliciter la rupture de fraîcheur sur le champ métier");

            } finally {
                c.rollback();
            }
        }
    }

    // =========================================================================
    // HELPERS POUR LES TESTS DE SORTIE
    // =========================================================================

    private int assurerPeriodePaie(Connection c, LocalDate date) throws Exception {
        String sql = "SELECT id_periode_paie FROM periode_paie WHERE ? BETWEEN date_debut AND date_fin LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        LocalDate debut = date.withDayOfMonth(1);
        LocalDate fin = date.withDayOfMonth(date.lengthOfMonth());
        String ins = "INSERT INTO periode_paie (code, libelle, date_debut, date_fin, id_statut_periode) " +
                "VALUES (?, ?, ?, ?, 1) RETURNING id_periode_paie";
        try (PreparedStatement ps = c.prepareStatement(ins)) {
            ps.setString(1, date.getYear() + "-" + String.format("%02d", date.getMonthValue()));
            ps.setString(2, "Période " + date.getYear() + "-" + date.getMonthValue());
            ps.setDate(3, Date.valueOf(debut));
            ps.setDate(4, Date.valueOf(fin));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new IllegalStateException("Impossible d'assurer periode_paie pour " + date);
    }

    private int assurerPeriodePaieExacte(Connection c, String code, LocalDate debut, LocalDate fin) throws Exception {
        String codeTronque = code != null && code.length() > 20 ? code.substring(0, 20) : code;
        String sql = "SELECT id_periode_paie FROM periode_paie WHERE code = ? LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, codeTronque);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        String ins = "INSERT INTO periode_paie (code, libelle, date_debut, date_fin, id_statut_periode) " +
                "VALUES (?, ?, ?, ?, 1) RETURNING id_periode_paie";
        try (PreparedStatement ps = c.prepareStatement(ins)) {
            ps.setString(1, codeTronque);
            ps.setString(2, "Période " + codeTronque);
            ps.setDate(3, Date.valueOf(debut));
            ps.setDate(4, Date.valueOf(fin));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new IllegalStateException("Impossible d'assurer periode_paie pour " + codeTronque);
    }

    private int creerBulletinMensuelValide(Connection c, int empId, int periodeId, BigDecimal salaireBase) throws Exception {
        int snapshotId;
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO pointage_cumul_periode (id_employe, id_periode_paie, nb_jours_ouvrables, nb_jours_presents, total_heures_travaillees) " +
                "VALUES (?, ?, 10, 8, 64) RETURNING id_cumul")) {
            ps.setInt(1, empId);
            ps.setInt(2, periodeId);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); snapshotId = rs.getInt(1); }
        }
        int bulletinId;
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO bulletin_paie (id_periode_paie, id_employe, id_snapshot_pointage, " +
                "type_bulletin, reference_bulletin, matricule, nom_prenom, intitule_poste, nom_site, " +
                "regime_intervenant, salaire_base, taux_horaire, total_gains_bruts, total_cotisations_salariales, " +
                "salaire_net_imposable, irsa_net, net_a_payer, statut) " +
                "VALUES (?, ?, ?, 'MENSUEL', ?, 'EMP-TEST-DS', 'Test Salarie', 'Collaborateur', " +
                "'Antananarivo', 'PERMANENT', ?, 1730, ?, 6000, ?, 3000, ?, 'VALIDE') RETURNING id_bulletin")) {
            ps.setInt(1, periodeId);
            ps.setInt(2, empId);
            ps.setInt(3, snapshotId);
            ps.setString(4, "MENS-DS-" + empId);
            ps.setBigDecimal(5, salaireBase);
            ps.setBigDecimal(6, salaireBase);
            ps.setBigDecimal(7, salaireBase.subtract(new BigDecimal("6000")));
            ps.setBigDecimal(8, salaireBase.subtract(new BigDecimal("9000")));
            try (ResultSet rs = ps.executeQuery()) { rs.next(); bulletinId = rs.getInt(1); }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO bulletin_paie_ligne (id_bulletin, code_rubrique, libelle_rubrique, " +
                "sens, categorie, base_calcul, montant_salarial, montant_patronal) VALUES " +
                "(?, 'COTISATION_CNAPS_SAL', 'CNaPS', 'RETENUE', 'COTISATION_SALARIALE', ?, 3000, 0), " +
                "(?, 'COTISATION_CNAPS_PAT', 'CNaPS employeur', 'PATRONAL', 'COTISATION_PATRONALE', ?, 0, 39000), " +
                "(?, 'COTISATION_OSTIE_SAL', 'Santé', 'RETENUE', 'COTISATION_SALARIALE', ?, 3000, 0), " +
                "(?, 'COTISATION_OSTIE_PAT', 'Santé employeur', 'PATRONAL', 'COTISATION_PATRONALE', ?, 0, 15000), " +
                "(?, 'COTISATION_FMFP_PAT', 'FMFP', 'PATRONAL', 'COTISATION_PATRONALE', ?, 0, 3000)")) {
            for (int i = 1; i <= 5; i++) {
                ps.setInt(i * 2 - 1, bulletinId);
                ps.setBigDecimal(i * 2, salaireBase);
            }
            ps.executeUpdate();
        }
        return bulletinId;
    }

    @Test
    @DisplayName("Phase 2.1 : Départ en milieu de mois avec variables de pointage (HS, Nuit, Panier, Absences) et vérification double paiement")
    void testPhase21DepartMilieuDeMoisAvecVariablesPointage() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                int adminId = CongeTestFixtureHelper.createAdminUser(c, "rh_p21_mid_" + System.currentTimeMillis());
                int empId = CongeTestFixtureHelper.createEmploye(c, "P21-M-" + (System.currentTimeMillis() % 1000000), "Rasoa", "Bakoly");
                LocalDate dateDepart = LocalDate.of(2026, 10, 12);
                int contratId = CongeTestFixtureHelper.createContratActif(c, empId, LocalDate.of(2025, 10, 12));
                BigDecimal salaireBase = new BigDecimal("1200000.00");
                CongeTestFixtureHelper.createAffectation(c, empId, contratId, LocalDate.of(2025, 10, 12), salaireBase);
                try (PreparedStatement ps = c.prepareStatement("UPDATE employe_affectation_carriere SET id_categorie_professionnelle = 2 WHERE id_employe = ?")) {
                    ps.setInt(1, empId);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = c.prepareStatement("UPDATE contrat_travail SET date_fin = ? WHERE id_contrat = ?")) {
                    ps.setDate(1, Date.valueOf(dateDepart));
                    ps.setInt(2, contratId);
                    ps.executeUpdate();
                }

                int periodeId = assurerPeriodePaie(c, dateDepart);

                // Résolution statut pointage
                int idStatutPresent = 1;
                try (PreparedStatement ps = c.prepareStatement("SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = 'PRESENT' LIMIT 1");
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) idStatutPresent = rs.getInt(1);
                }
                int idStatutAbsence = 2;
                try (PreparedStatement ps = c.prepareStatement("SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE decompte_absence = TRUE OR code LIKE '%ABS%' LIMIT 1");
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) idStatutAbsence = rs.getInt(1);
                }
                int idSite = 1;
                try (PreparedStatement ps = c.prepareStatement("SELECT id_site FROM ref_site LIMIT 1");
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) idSite = rs.getInt(1);
                }

                // Insertion de pointages réels du 1er au 12 octobre :
                // - Jours 1 à 5 : 8h normales
                for (int day = 1; day <= 5; day++) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO pointage_jour (id_employe, id_periode_paie, id_site, date_pointage, id_statut_jour, total_heures) " +
                            "VALUES (?, ?, ?, ?, ?, 8.00)")) {
                        ps.setInt(1, empId);
                        ps.setInt(2, periodeId);
                        ps.setInt(3, idSite);
                        ps.setDate(4, Date.valueOf(LocalDate.of(2026, 10, day)));
                        ps.setInt(5, idStatutPresent);
                        ps.executeUpdate();
                    }
                }
                // - Jour 6 : Absence injustifiée décomptée (8h d'absence)
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO pointage_jour (id_employe, id_periode_paie, id_site, date_pointage, id_statut_jour, total_heures) " +
                        "VALUES (?, ?, ?, ?, ?, 0.00)")) {
                    ps.setInt(1, empId);
                    ps.setInt(2, periodeId);
                    ps.setInt(3, idSite);
                    ps.setDate(4, Date.valueOf(LocalDate.of(2026, 10, 6)));
                    ps.setInt(5, idStatutAbsence);
                    ps.executeUpdate();
                }
                // - Jour 7 : Présence 8h + 4h Heures Supplémentaires (+30%)
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO pointage_jour (id_employe, id_periode_paie, id_site, date_pointage, id_statut_jour, total_heures) " +
                        "VALUES (?, ?, ?, ?, ?, 12.00)")) {
                    ps.setInt(1, empId);
                    ps.setInt(2, periodeId);
                    ps.setInt(3, idSite);
                    ps.setDate(4, Date.valueOf(LocalDate.of(2026, 10, 7)));
                    ps.setInt(5, idStatutPresent);
                    ps.executeUpdate();
                }
                // - Jour 8 : Présence avec 3h de nuit
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO pointage_jour (id_employe, id_periode_paie, id_site, date_pointage, id_statut_jour, total_heures, heures_nuit) " +
                        "VALUES (?, ?, ?, ?, ?, 8.00, 3.00)")) {
                    ps.setInt(1, empId);
                    ps.setInt(2, periodeId);
                    ps.setInt(3, idSite);
                    ps.setDate(4, Date.valueOf(LocalDate.of(2026, 10, 8)));
                    ps.setInt(5, idStatutPresent);
                    ps.executeUpdate();
                }
                // - Jour 9 : Présence avec panier repas (25 000 Ar)
                int idPtg9 = 0;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO pointage_jour (id_employe, id_periode_paie, id_site, date_pointage, id_statut_jour, total_heures) " +
                        "VALUES (?, ?, ?, ?, ?, 8.00) RETURNING id_pointage")) {
                    ps.setInt(1, empId);
                    ps.setInt(2, periodeId);
                    ps.setInt(3, idSite);
                    ps.setDate(4, Date.valueOf(LocalDate.of(2026, 10, 9)));
                    ps.setInt(5, idStatutPresent);
                    try (ResultSet rs = ps.executeQuery()) { rs.next(); idPtg9 = rs.getInt(1); }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO pointage_indemnite_jour (id_pointage, id_indemnite, accorde, quantite, montant_calcule) " +
                        "VALUES (?, (SELECT id_indemnite FROM ref_indemnite WHERE code = 'REPAS' LIMIT 1), true, 1, 25000.00)")) {
                    ps.setInt(1, idPtg9);
                    ps.executeUpdate();
                }

                // Préparation du dossier de sortie en milieu de mois
                DossierSortieSaveRequest saveReq = new DossierSortieSaveRequest();
                saveReq.setIdEmploye(empId);
                saveReq.setIdContrat(contratId);
                saveReq.setDateDepart(Date.valueOf(dateDepart));
                saveReq.setMotifRupture("DEMISSION");
                Map<String, Object> prep = new HashMap<>();
                prep.put("joursTravaillesDernierMois", "12");
                prep.put("justificationSalaireDernierMois", "Validation pointage du 01 au 12 octobre avec HS, Nuit et Absences");
                prep.put("moisPreavis", "0");
                prep.put("indemnitePreavis", "0");
                saveReq.setDonneesPreparation(prep);

                DossierSortieDTO dossier = dossierSortieService.sauvegarderDossier(saveReq, adminId, c);
                assertNotNull(dossier);

                // Simulation STC : calcul unifié
                SimulationStcResultDTO sim = dossierSortieService.simulerStcDepuisDossier(dossier.getIdDossierSortie(), c);
                assertNotNull(sim);
                assertNotNull(sim.getReferenceSimulation());

                // Vérification unitaire et précise des rubriques financières du décompte
                var lignesMap = new HashMap<String, BigDecimal>();
                for (var l : sim.getDecompte().getLignes()) {
                    lignesMap.put(l.getCode(), l.getMontant());
                }

                // 1. Prorata contractuel (12 jours calendaires / 30) = 480 000.00 Ar
                assertEquals(0, new BigDecimal("480000.00").compareTo(lignesMap.get("SALAIRE_BASE_PRORATA")),
                        "Le salaire prorata contractuel doit valoir exactement 480 000 Ar (12j/30)");

                // 2. Retenue pour absence
                assertEquals(0, new BigDecimal("27692.84").compareTo(lignesMap.get("RETENUE_ABSENCE")),
                        "La retenue d'absence doit valoir exactement 27 692.84 Ar");

                // 3. Heures supplémentaires (30% et 50%) calculées canoniquement par PointageSyntheseService
                assertEquals(0, new BigDecimal("28800.55").compareTo(lignesMap.get("HEURES_SUP_30")),
                        "Les HS 30% doivent valoir exactement 28 800.55 Ar");
                assertEquals(0, new BigDecimal("49847.11").compareTo(lignesMap.get("HEURES_SUP_50")),
                        "Les HS 50% doivent valoir exactement 49 847.11 Ar");

                // 4. Majoration nuit et travail dimanche
                assertEquals(0, new BigDecimal("6230.89").compareTo(lignesMap.get("MAJORATION_NUIT")),
                        "La majoration nuit doit valoir exactement 6 230.89 Ar");
                assertEquals(0, new BigDecimal("77539.95").compareTo(lignesMap.get("HEURES_DIMANCHE")),
                        "Le travail dimanche doit valoir exactement 77 539.95 Ar");

                // 5. Indemnités de pointage (Repas + Déplacement)
                assertEquals(0, new BigDecimal("25000.00").compareTo(lignesMap.get("INDEMNITE_REPAS")),
                        "L'indemnité repas doit valoir exactement 25 000.00 Ar");
                assertEquals(0, new BigDecimal("31200.00").compareTo(lignesMap.get("INDEMNITE_DEPLACEMENT")),
                        "L'indemnité déplacement doit valoir exactement 31 200.00 Ar");

                // Vérifications des agrégats fiscaux et sociaux
                assertEquals(0, new BigDecimal("670925.66").compareTo(sim.getDecompte().getTotalBrutStc()),
                        "Le brut total STC doit valoir exactement 670 925.66 Ar");

                assertEquals(0, new BigDecimal("13418.52").compareTo(sim.getDecompte().getTotalCotisationsSociales()),
                        "Les cotisations sociales salariales (CNaPS 1% + Santé 1%) doivent valoir 13 418.52 Ar");

                assertEquals(0, new BigDecimal("39000.00").compareTo(sim.getDecompte().getIrsaNet()),
                        "L'IRSA net calculé selon barème CGI doit valoir 39 000.00 Ar");

                assertEquals(0, new BigDecimal("618510.00").compareTo(sim.getDecompte().getNetAPayerStc()),
                        "Le net final STC arrondi à la dizaine d'Ariary doit valoir exactement 618 510.00 Ar");

                // Scellement officiel du STC
                ScellementStcRequest sealReq = new ScellementStcRequest(sim.getReferenceSimulation(), sim.getVersion(), "Scellement départ milieu de mois");
                DossierSortieDTO sealed = dossierSortieService.scellerDossierEtStc(dossier.getIdDossierSortie(), sealReq, adminId, c);
                assertEquals("VALIDE", sealed.getStatutDossier());
                assertNotNull(sealed.getIdStc());

                // Vérification du scellement en BDD
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT net_a_payer_stc, statut FROM paie_stc WHERE id_stc = ?")) {
                    ps.setInt(1, sealed.getIdStc());
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(0, new BigDecimal("618510.00").compareTo(rs.getBigDecimal("net_a_payer_stc")),
                                "Le net scellé en BDD doit valoir exactement 618 510.00 Ar");
                        assertEquals("VALIDE", rs.getString("statut"));
                    }
                }

                // Enregistrement du règlement financier du STC
                StcReglementRequest regReq = new StcReglementRequest(Date.valueOf(dateDepart), "VIR-STC-OCT-2026-001");
                stcService.enregistrerReglement(sealed.getIdStc(), regReq, adminId, c);

                // Vérification du statut 'REGLE' et de l'historique de paiement
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT statut, date_reglement, reference_reglement FROM paie_stc WHERE id_stc = ?")) {
                    ps.setInt(1, sealed.getIdStc());
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("REGLE", rs.getString("statut"));
                        assertEquals(Date.valueOf(dateDepart), rs.getDate("date_reglement"));
                        assertEquals("VIR-STC-OCT-2026-001", rs.getString("reference_reglement"));
                    }
                }

                // Exécution de la résolution de population pour la campagne mensuelle globale
                PayrollPopulationResolver popResolver = new PayrollPopulationResolver();
                var pop = popResolver.resolve(periodeId, c);

                // 1. Le salarié sortant dont le STC a été réglé est formellement exclu de la paie mensuelle
                boolean isExcluded = pop.members().stream().anyMatch(m ->
                        m.employeeId() == empId && "EXCLUDED".equals(m.decision()) && "STC_ALREADY_SETTLED".equals(m.reasonCode()));
                assertTrue(isExcluded, "Le salarié doit être exclu avec le motif STC_ALREADY_SETTLED");

                // 2. La liste des salariés éligibles au calcul ne doit pas contenir l'employé
                assertFalse(pop.includedEmployeeIds().contains(empId),
                        "L'employé avec STC scellé/réglé ne doit jamais figurer parmi les salariés à calculer");

                // 3. Contrôle d'intégrité : aucun bulletin mensuel n'est créé en base pour ce salarié
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT COUNT(*) FROM bulletin_paie WHERE id_employe = ? AND id_periode_paie = ? AND type_bulletin = 'MENSUEL'")) {
                    ps.setInt(1, empId);
                    ps.setInt(2, periodeId);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(0, rs.getInt(1), "Aucun bulletin mensuel ordinaire ne doit exister pour ce salarié");
                    }
                }

            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("Phase 2.1 : Double scellement réellement concurrent (protection concurrence stricte)")
    void testPhase21DoubleScellementConcurrent() throws Exception {
        int adminId;
        int empId;
        int contratId;
        LocalDate dateDepart = LocalDate.of(2026, 10, 10);
        int periodeId;
        int dossierId;
        String refSim;
        int version;

        // Préparation et commit de la fixture dans une transaction dédiée
        try (Connection cSetup = dataSource.getConnection()) {
            cSetup.setAutoCommit(false);
            adminId = CongeTestFixtureHelper.createAdminUser(cSetup, "rh_p21_conc_" + System.currentTimeMillis());
            empId = CongeTestFixtureHelper.createEmploye(cSetup, "P21-C-" + (System.currentTimeMillis() % 1000000), "Rabary", "Jean");
            contratId = CongeTestFixtureHelper.createContratActif(cSetup, empId, dateDepart.minusYears(1));
            CongeTestFixtureHelper.createAffectation(cSetup, empId, contratId, dateDepart.minusYears(1), new BigDecimal("1000000.00"));
            try (PreparedStatement ps = cSetup.prepareStatement("UPDATE contrat_travail SET date_fin = ? WHERE id_contrat = ?")) {
                ps.setDate(1, Date.valueOf(dateDepart));
                ps.setInt(2, contratId);
                ps.executeUpdate();
            }

            periodeId = assurerPeriodePaie(cSetup, dateDepart);
            creerBulletinMensuelValide(cSetup, empId, periodeId, new BigDecimal("1000000.00"));

            DossierSortieSaveRequest saveReq = new DossierSortieSaveRequest();
            saveReq.setIdEmploye(empId);
            saveReq.setIdContrat(contratId);
            saveReq.setDateDepart(Date.valueOf(dateDepart));
            saveReq.setMotifRupture("DEMISSION");
            Map<String, Object> prep = new HashMap<>();
            prep.put("montantBulletinDejaPaye", "0");
            prep.put("justificationSalaireDernierMois", "Présence constatée");
            saveReq.setDonneesPreparation(prep);

            DossierSortieDTO dossier = dossierSortieService.sauvegarderDossier(saveReq, adminId, cSetup);
            dossierId = dossier.getIdDossierSortie();
            SimulationStcResultDTO sim = dossierSortieService.simulerStcDepuisDossier(dossierId, cSetup);
            refSim = sim.getReferenceSimulation();
            version = sim.getVersion();
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
                            ScellementStcRequest sealReq = new ScellementStcRequest(refSim, version, "Scellement concurrent");
                            dossierSortieService.scellerDossierEtStc(dossierId, sealReq, adminId, cTask);
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

            assertEquals(1, successCount.get(), "Exactement une seule tentative de scellement doit aboutir avec succès");
            assertEquals(1, failureCount.get(), "La seconde tentative concurrente doit être rejetée (conflit optimiste / verrou)");

            try (Connection cCheck = dataSource.getConnection()) {
                try (PreparedStatement ps = cCheck.prepareStatement(
                        "SELECT COUNT(*) FROM paie_stc WHERE id_employe = ? AND id_periode_paie = ? AND statut <> 'ANNULE'")) {
                    ps.setInt(1, empId);
                    ps.setInt(2, periodeId);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(1, rs.getInt(1), "Il doit y avoir exactement un seul enregistrement de STC en base");
                    }
                }
            }
        } finally {
            // Nettoyage des données créées
            try (Connection cClean = dataSource.getConnection()) {
                try (PreparedStatement ps = cClean.prepareStatement("DELETE FROM paie_stc_reglement WHERE id_stc IN (SELECT id_stc FROM paie_stc WHERE id_employe = ?)")) {
                    ps.setInt(1, empId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = cClean.prepareStatement("DELETE FROM paie_dossier_sortie WHERE id_employe = ?")) {
                    ps.setInt(1, empId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = cClean.prepareStatement("DELETE FROM paie_stc WHERE id_employe = ?")) {
                    ps.setInt(1, empId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = cClean.prepareStatement("DELETE FROM bulletin_paie_ligne WHERE id_bulletin IN (SELECT id_bulletin FROM bulletin_paie WHERE id_employe = ?)")) {
                    ps.setInt(1, empId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = cClean.prepareStatement("DELETE FROM bulletin_paie WHERE id_employe = ?")) {
                    ps.setInt(1, empId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = cClean.prepareStatement("DELETE FROM contrat_travail WHERE id_employe = ?")) {
                    ps.setInt(1, empId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = cClean.prepareStatement("DELETE FROM employe_affectation_carriere WHERE id_employe = ?")) {
                    ps.setInt(1, empId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = cClean.prepareStatement("DELETE FROM pointage_cumul_periode WHERE id_employe = ?")) {
                    ps.setInt(1, empId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = cClean.prepareStatement("DELETE FROM employe WHERE id_employe = ?")) {
                    ps.setInt(1, empId);
                    ps.executeUpdate();
                }
            }
        }
    }

    @Test
    @DisplayName("Phase 2.1 : Rollback transactionnel complet après écriture financière")
    void testPhase21RollbackTransactionnelApresEcriture() throws Exception {
        int empId;
        int dossierId;
        int periodeId;

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                int adminId = CongeTestFixtureHelper.createAdminUser(c, "rh_p21_roll_" + System.currentTimeMillis());
                empId = CongeTestFixtureHelper.createEmploye(c, "P21-R-" + (System.currentTimeMillis() % 1000000), "Ravelo", "Alain");
                LocalDate dateDepart = LocalDate.of(2026, 10, 10);
                int contratId = CongeTestFixtureHelper.createContratActif(c, empId, dateDepart.minusYears(1));
                CongeTestFixtureHelper.createAffectation(c, empId, contratId, dateDepart.minusYears(1), new BigDecimal("1100000.00"));
                try (PreparedStatement ps = c.prepareStatement("UPDATE contrat_travail SET date_fin = ? WHERE id_contrat = ?")) {
                    ps.setDate(1, Date.valueOf(dateDepart));
                    ps.setInt(2, contratId);
                    ps.executeUpdate();
                }

                periodeId = assurerPeriodePaie(c, dateDepart);
                creerBulletinMensuelValide(c, empId, periodeId, new BigDecimal("1100000.00"));

                DossierSortieSaveRequest saveReq = new DossierSortieSaveRequest();
                saveReq.setIdEmploye(empId);
                saveReq.setIdContrat(contratId);
                saveReq.setDateDepart(Date.valueOf(dateDepart));
                saveReq.setMotifRupture("DEMISSION");
                Map<String, Object> prep = new HashMap<>();
                prep.put("montantBulletinDejaPaye", "0");
                prep.put("justificationSalaireDernierMois", "Présence constatée");
                saveReq.setDonneesPreparation(prep);

                DossierSortieDTO dossier = dossierSortieService.sauvegarderDossier(saveReq, adminId, c);
                dossierId = dossier.getIdDossierSortie();
                SimulationStcResultDTO sim = dossierSortieService.simulerStcDepuisDossier(dossierId, c);

                // Exécution du scellement dans la transaction
                ScellementStcRequest sealReq = new ScellementStcRequest(sim.getReferenceSimulation(), sim.getVersion(), "Scellement avant rollback");
                DossierSortieDTO sealed = dossierSortieService.scellerDossierEtStc(dossierId, sealReq, adminId, c);
                assertNotNull(sealed.getIdStc());

                // Vérification temporaire en cours de transaction
                try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM paie_stc WHERE id_employe = ?")) {
                    ps.setInt(1, empId);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(1, rs.getInt(1));
                    }
                }

                // Déclenchement intentionnel du rollback
                c.rollback();

            } finally {
                if (!c.isClosed() && !c.getAutoCommit()) {
                    c.rollback();
                }
            }
        }

        // Vérification dans une nouvelle connexion : ZERO écriture financière orpheline
        try (Connection cVerify = dataSource.getConnection()) {
            try (PreparedStatement ps = cVerify.prepareStatement("SELECT COUNT(*) FROM paie_stc WHERE id_employe = ?")) {
                ps.setInt(1, empId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(0, rs.getInt(1), "Après rollback, paie_stc ne doit contenir aucune ligne");
                }
            }
            try (PreparedStatement ps = cVerify.prepareStatement("SELECT COUNT(*) FROM paie_dossier_sortie WHERE id_employe = ?")) {
                ps.setInt(1, empId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(0, rs.getInt(1), "Après rollback de la transaction, le dossier ne doit pas être scellé");
                }
            }
        }
    }

    @Test
    @DisplayName("Phase 2.1 : Invalidation stricte d'une simulation précédente après nouvelle simulation")
    void testPhase21InvalidationSimulationPrecedente() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                int adminId = CongeTestFixtureHelper.createAdminUser(c, "rh_p21_inval_" + System.currentTimeMillis());
                int empId = CongeTestFixtureHelper.createEmploye(c, "P21-I-" + (System.currentTimeMillis() % 1000000), "Andrian", "Faly");
                LocalDate dateDepart = LocalDate.of(2026, 10, 10);
                int contratId = CongeTestFixtureHelper.createContratActif(c, empId, dateDepart.minusYears(1));
                CongeTestFixtureHelper.createAffectation(c, empId, contratId, dateDepart.minusYears(1), new BigDecimal("950000.00"));
                try (PreparedStatement ps = c.prepareStatement("UPDATE contrat_travail SET date_fin = ? WHERE id_contrat = ?")) {
                    ps.setDate(1, Date.valueOf(dateDepart));
                    ps.setInt(2, contratId);
                    ps.executeUpdate();
                }

                int periodeId = assurerPeriodePaie(c, dateDepart);
                creerBulletinMensuelValide(c, empId, periodeId, new BigDecimal("950000.00"));

                DossierSortieSaveRequest saveReq = new DossierSortieSaveRequest();
                saveReq.setIdEmploye(empId);
                saveReq.setIdContrat(contratId);
                saveReq.setDateDepart(Date.valueOf(dateDepart));
                saveReq.setMotifRupture("DEMISSION");
                Map<String, Object> prep = new HashMap<>();
                prep.put("montantBulletinDejaPaye", "0");
                prep.put("justificationSalaireDernierMois", "Présence constatée");
                saveReq.setDonneesPreparation(prep);

                DossierSortieDTO dossier = dossierSortieService.sauvegarderDossier(saveReq, adminId, c);
                int dossierId = dossier.getIdDossierSortie();

                // 1. Première simulation A
                SimulationStcResultDTO simA = dossierSortieService.simulerStcDepuisDossier(dossierId, c);
                String refA = simA.getReferenceSimulation();
                assertNotNull(refA);

                // 2. Seconde simulation B (par exemple après nouvelle estimation)
                SimulationStcResultDTO simB = dossierSortieService.simulerStcDepuisDossier(dossierId, c);
                String refB = simB.getReferenceSimulation();
                assertNotNull(refB);
                assertNotEquals(refA, refB, "Chaque simulation successive doit produire une référence distincte");

                // 3. Tentative de scellement avec la référence obsolète A -> doit échouer
                ScellementStcRequest reqA = new ScellementStcRequest(refA, simB.getVersion(), "Tentative avec ancienne référence");
                SirhBusinessException ex = assertThrows(SirhBusinessException.class, () ->
                        dossierSortieService.scellerDossierEtStc(dossierId, reqA, adminId, c));
                assertEquals(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED, ex.getErrorCode(),
                        "Le scellement avec une simulation précédente doit être bloqué");

                // 4. Scellement avec la référence active B -> doit réussir
                ScellementStcRequest reqB = new ScellementStcRequest(refB, simB.getVersion(), "Scellement référence active");
                DossierSortieDTO sealed = dossierSortieService.scellerDossierEtStc(dossierId, reqB, adminId, c);
                assertEquals("VALIDE", sealed.getStatutDossier());
                assertNotNull(sealed.getIdStc());

            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("Phase 2.1 : Cycle 26-25 - Prorata 15j/30 et intégration des variables du 28 septembre")
    void testPhase21Cycle2625AvecPointagesFinSeptembre() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                CongeTestFixtureHelper.cleanDatabase(c);
                int adminId = CongeTestFixtureHelper.createAdminUser(c, "rh_p21_c2625_" + System.currentTimeMillis());
                int empId = CongeTestFixtureHelper.createEmploye(c, "P21-C2625-" + (System.currentTimeMillis() % 1000000), "Ranaivo", "Hery");
                LocalDate dateDepart = LocalDate.of(2026, 10, 10);
                LocalDate debutPeriode = LocalDate.of(2026, 9, 26);
                LocalDate finPeriode = LocalDate.of(2026, 10, 25);
                int periodeId = assurerPeriodePaieExacte(c, "2026-10-C2625-" + (System.currentTimeMillis() % 100000), debutPeriode, finPeriode);

                // Contrat actif depuis le 01/01/2026 (antérieur au 26/09)
                BigDecimal salaireBase = new BigDecimal("1200000.00");
                int contratId = CongeTestFixtureHelper.createContratActif(c, empId, LocalDate.of(2026, 1, 1));
                CongeTestFixtureHelper.createAffectation(c, empId, contratId, LocalDate.of(2026, 1, 1), salaireBase);
                try (PreparedStatement psCat = c.prepareStatement(
                        "UPDATE employe_affectation_carriere SET id_categorie_professionnelle = (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE est_cadre = FALSE LIMIT 1) WHERE id_employe = ?")) {
                    psCat.setInt(1, empId);
                    psCat.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement("UPDATE contrat_travail SET date_fin = ? WHERE id_contrat = ?")) {
                    ps.setDate(1, Date.valueOf(dateDepart));
                    ps.setInt(2, contratId);
                    ps.executeUpdate();
                }

                int idStatutPresent = 1;
                try (PreparedStatement ps = c.prepareStatement("SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = 'PRESENT' LIMIT 1");
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) idStatutPresent = rs.getInt(1);
                }
                int idSite = 1;
                try (PreparedStatement ps = c.prepareStatement("SELECT id_site FROM ref_site LIMIT 1");
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) idSite = rs.getInt(1);
                }

                // Pointage le 28 septembre 2026 (dans le cycle 26-25, mais avant le 1er octobre !)
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO pointage_jour (id_employe, id_periode_paie, id_site, date_pointage, id_statut_jour, total_heures, heures_nuit) " +
                        "VALUES (?, ?, ?, ?, ?, 8.00, 3.00)")) {
                    ps.setInt(1, empId);
                    ps.setInt(2, periodeId);
                    ps.setInt(3, idSite);
                    ps.setDate(4, Date.valueOf(LocalDate.of(2026, 9, 28)));
                    ps.setInt(5, idStatutPresent);
                    ps.executeUpdate();
                }

                DossierSortieSaveRequest saveReq = new DossierSortieSaveRequest();
                saveReq.setIdEmploye(empId);
                saveReq.setIdContrat(contratId);
                saveReq.setDateDepart(Date.valueOf(dateDepart));
                saveReq.setMotifRupture("DEMISSION");

                DossierSortieDTO dossier = dossierSortieService.sauvegarderDossier(saveReq, adminId, c);

                // Simulation STC
                SimulationStcResultDTO sim = dossierSortieService.simulerStcDepuisDossier(dossier.getIdDossierSortie(), c);

                // 1. Prorata contractuel : du 26/09 au 10/10 = 15 jours calendaires / 30 = 600 000.00 Ar
                var lignesMap = new HashMap<String, BigDecimal>();
                for (var l : sim.getDecompte().getLignes()) {
                    lignesMap.put(l.getCode(), l.getMontant());
                }
                assertEquals(0, new BigDecimal("600000.00").compareTo(lignesMap.get("SALAIRE_BASE_PRORATA")),
                        "Le prorata sur le cycle 26-25 pour 15 jours sous contrat doit valoir exactement 600 000 Ar (15j/30)");

                // 2. Preuve de non-troncature au 1er octobre : la majoration de nuit du 28 septembre doit être liquidée
                assertTrue(lignesMap.containsKey("MAJORATION_NUIT"), "La majoration nuit du 28 septembre doit être intégrée dans le STC");
                assertTrue(lignesMap.get("MAJORATION_NUIT").compareTo(BigDecimal.ZERO) > 0,
                        "La majoration nuit de 3h effectuée le 28 septembre doit être positivement valorisée");

            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("Phase 2.1 : Embauche le 11 octobre et départ le 20 octobre (10 jours réels)")
    void testPhase21EmbaucheEtDepartEnCoursDeMemePeriode() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                CongeTestFixtureHelper.cleanDatabase(c);
                int adminId = CongeTestFixtureHelper.createAdminUser(c, "rh_p21_emb11_" + System.currentTimeMillis());
                int empId = CongeTestFixtureHelper.createEmploye(c, "P21-E11-" + (System.currentTimeMillis() % 1000000), "Ratsimba", "Paul");
                LocalDate dateEmbauche = LocalDate.of(2026, 10, 11);
                LocalDate dateDepart = LocalDate.of(2026, 10, 20);
                LocalDate debutPeriode = LocalDate.of(2026, 9, 26);
                LocalDate finPeriode = LocalDate.of(2026, 10, 25);
                assurerPeriodePaieExacte(c, "2026-10-C2625-E-" + (System.currentTimeMillis() % 100000), debutPeriode, finPeriode);

                BigDecimal salaireBase = new BigDecimal("1200000.00");
                int contratId = CongeTestFixtureHelper.createContratActif(c, empId, dateEmbauche);
                CongeTestFixtureHelper.createAffectation(c, empId, contratId, dateEmbauche, salaireBase);
                try (PreparedStatement ps = c.prepareStatement("UPDATE contrat_travail SET date_fin = ? WHERE id_contrat = ?")) {
                    ps.setDate(1, Date.valueOf(dateDepart));
                    ps.setInt(2, contratId);
                    ps.executeUpdate();
                }

                DossierSortieSaveRequest saveReq = new DossierSortieSaveRequest();
                saveReq.setIdEmploye(empId);
                saveReq.setIdContrat(contratId);
                saveReq.setDateDepart(Date.valueOf(dateDepart));
                saveReq.setMotifRupture("FIN_PERIODE_ESSAI");

                DossierSortieDTO dossier = dossierSortieService.sauvegarderDossier(saveReq, adminId, c);
                SimulationStcResultDTO sim = dossierSortieService.simulerStcDepuisDossier(dossier.getIdDossierSortie(), c);

                // Couverture réelle : du 11 au 20 octobre inclus = 10 jours sous contrat
                assertEquals(0, new BigDecimal("10").compareTo(dossier.getJoursSousContrat()),
                        "Les jours sous contrat doivent être de 10 jours (du 11 au 20 octobre)");
                assertEquals(0, new BigDecimal("10").compareTo(sim.getDecompte().getJoursTravaillesDernierMois()),
                        "La simulation STC doit porter sur 10 jours sous contrat");

                // Prorata : 1 200 000 * 10 / 30 = 400 000.00 Ar (et non 25/30 = 1 000 000 Ar !)
                var lignesMap = new HashMap<String, BigDecimal>();
                for (var l : sim.getDecompte().getLignes()) {
                    lignesMap.put(l.getCode(), l.getMontant());
                }
                assertEquals(0, new BigDecimal("400000.00").compareTo(lignesMap.get("SALAIRE_BASE_PRORATA")),
                        "Le salaire prorata pour 10 jours sous contrat doit valoir exactement 400 000.00 Ar");

            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("Phase 2.1 : Départ à la fin de février (année normale 28j et bissextile 29j) donne 100% du salaire")
    void testPhase21DepartFinFevrierNormalEtBissextile() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                CongeTestFixtureHelper.cleanDatabase(c);
                int adminId = CongeTestFixtureHelper.createAdminUser(c, "rh_p21_fev_" + System.currentTimeMillis());

                // Cas A : Février 2026 (28 jours, non bissextile)
                int empIdA = CongeTestFixtureHelper.createEmploye(c, "P21-FEV26-" + (System.currentTimeMillis() % 1000000), "Rasoa", "Noro");
                LocalDate dDebutA = LocalDate.of(2026, 2, 1);
                LocalDate dFinA = LocalDate.of(2026, 2, 28);
                assurerPeriodePaieExacte(c, "2026-02-CIVIL-" + (System.currentTimeMillis() % 100000), dDebutA, dFinA);
                BigDecimal salBase = new BigDecimal("1500000.00");
                int ctA = CongeTestFixtureHelper.createContratActif(c, empIdA, LocalDate.of(2025, 1, 1));
                CongeTestFixtureHelper.createAffectation(c, empIdA, ctA, LocalDate.of(2025, 1, 1), salBase);
                try (PreparedStatement ps = c.prepareStatement("UPDATE contrat_travail SET date_fin = ? WHERE id_contrat = ?")) {
                    ps.setDate(1, Date.valueOf(dFinA));
                    ps.setInt(2, ctA);
                    ps.executeUpdate();
                }

                DossierSortieSaveRequest reqA = new DossierSortieSaveRequest();
                reqA.setIdEmploye(empIdA);
                reqA.setIdContrat(ctA);
                reqA.setDateDepart(Date.valueOf(dFinA));
                reqA.setMotifRupture("DEMISSION");

                DossierSortieDTO dosA = dossierSortieService.sauvegarderDossier(reqA, adminId, c);
                SimulationStcResultDTO simA = dossierSortieService.simulerStcDepuisDossier(dosA.getIdDossierSortie(), c);

                assertEquals(0, new BigDecimal("30").compareTo(simA.getDecompte().getJoursTravaillesDernierMois()),
                        "Un mois complet de février 28 jours doit être compté à 30 jours (mensualisation)");
                var lignesA = new HashMap<String, BigDecimal>();
                for (var l : simA.getDecompte().getLignes()) lignesA.put(l.getCode(), l.getMontant());
                assertEquals(0, salBase.compareTo(lignesA.get("SALAIRE_BASE_PRORATA")),
                        "Un salarié terminant tout le mois de février doit percevoir 100% de son salaire contractuel (1 500 000 Ar)");

                // Cas B : Février 2028 (29 jours, bissextile)
                int empIdB = CongeTestFixtureHelper.createEmploye(c, "P21-FEV28-" + (System.currentTimeMillis() % 1000000), "Razafy", "Mamy");
                LocalDate dDebutB = LocalDate.of(2028, 2, 1);
                LocalDate dFinB = LocalDate.of(2028, 2, 29);
                assurerPeriodePaieExacte(c, "2028-02-BISSEXTILE-" + (System.currentTimeMillis() % 100000), dDebutB, dFinB);
                int ctB = CongeTestFixtureHelper.createContratActif(c, empIdB, LocalDate.of(2027, 1, 1));
                CongeTestFixtureHelper.createAffectation(c, empIdB, ctB, LocalDate.of(2027, 1, 1), salBase);
                try (PreparedStatement ps = c.prepareStatement("UPDATE contrat_travail SET date_fin = ? WHERE id_contrat = ?")) {
                    ps.setDate(1, Date.valueOf(dFinB));
                    ps.setInt(2, ctB);
                    ps.executeUpdate();
                }

                DossierSortieSaveRequest reqB = new DossierSortieSaveRequest();
                reqB.setIdEmploye(empIdB);
                reqB.setIdContrat(ctB);
                reqB.setDateDepart(Date.valueOf(dFinB));
                reqB.setMotifRupture("DEMISSION");

                DossierSortieDTO dosB = dossierSortieService.sauvegarderDossier(reqB, adminId, c);
                SimulationStcResultDTO simB = dossierSortieService.simulerStcDepuisDossier(dosB.getIdDossierSortie(), c);

                assertEquals(0, new BigDecimal("30").compareTo(simB.getDecompte().getJoursTravaillesDernierMois()),
                        "Un mois complet de février 29 jours doit être compté à 30 jours");
                var lignesB = new HashMap<String, BigDecimal>();
                for (var l : simB.getDecompte().getLignes()) lignesB.put(l.getCode(), l.getMontant());
                assertEquals(0, salBase.compareTo(lignesB.get("SALAIRE_BASE_PRORATA")),
                        "Février bissextile complet doit donner 100% du salaire contractuel");

            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("Phase 2.1 : Périodes complètes (30j, 31j) et départ la veille de la fin d'un mois de 31j (29j/30)")
    void testPhase21PeriodesCompletesEtDepartVeille31Jours() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                CongeTestFixtureHelper.cleanDatabase(c);
                int adminId = CongeTestFixtureHelper.createAdminUser(c, "rh_p21_pcomp_" + System.currentTimeMillis());
                BigDecimal salBase = new BigDecimal("1800000.00");

                // Cas 1 : Mois de 31 jours complet (ex: Janvier 2026, départ le 31/01)
                int empId31 = CongeTestFixtureHelper.createEmploye(c, "P21-J31-" + (System.currentTimeMillis() % 1000000), "Rakoto", "Jean");
                LocalDate dDeb31 = LocalDate.of(2026, 1, 1);
                LocalDate dFin31 = LocalDate.of(2026, 1, 31);
                assurerPeriodePaieExacte(c, "2026-01-CIVIL-" + (System.currentTimeMillis() % 100000), dDeb31, dFin31);
                int ct31 = CongeTestFixtureHelper.createContratActif(c, empId31, LocalDate.of(2025, 1, 1));
                CongeTestFixtureHelper.createAffectation(c, empId31, ct31, LocalDate.of(2025, 1, 1), salBase);
                try (PreparedStatement ps = c.prepareStatement("UPDATE contrat_travail SET date_fin = ? WHERE id_contrat = ?")) {
                    ps.setDate(1, Date.valueOf(dFin31));
                    ps.setInt(2, ct31);
                    ps.executeUpdate();
                }

                DossierSortieSaveRequest req31 = new DossierSortieSaveRequest();
                req31.setIdEmploye(empId31);
                req31.setIdContrat(ct31);
                req31.setDateDepart(Date.valueOf(dFin31));
                req31.setMotifRupture("DEMISSION");
                DossierSortieDTO dos31 = dossierSortieService.sauvegarderDossier(req31, adminId, c);
                SimulationStcResultDTO sim31 = dossierSortieService.simulerStcDepuisDossier(dos31.getIdDossierSortie(), c);

                assertEquals(0, salBase.compareTo(sim31.getDecompte().getSalaireProrata()),
                        "Mois complet de 31 jours donne 100% du salaire de base");

                // Cas 2 : Mois de 31 jours mais départ la veille (départ le 30/01)
                int empIdVeille = CongeTestFixtureHelper.createEmploye(c, "P21-V30-" + (System.currentTimeMillis() % 1000000), "Rakoto", "Veille");
                int ctVeille = CongeTestFixtureHelper.createContratActif(c, empIdVeille, LocalDate.of(2025, 1, 1));
                CongeTestFixtureHelper.createAffectation(c, empIdVeille, ctVeille, LocalDate.of(2025, 1, 1), salBase);
                LocalDate dVeille = LocalDate.of(2026, 1, 30);
                try (PreparedStatement ps = c.prepareStatement("UPDATE contrat_travail SET date_fin = ? WHERE id_contrat = ?")) {
                    ps.setDate(1, Date.valueOf(dVeille));
                    ps.setInt(2, ctVeille);
                    ps.executeUpdate();
                }

                DossierSortieSaveRequest reqVeille = new DossierSortieSaveRequest();
                reqVeille.setIdEmploye(empIdVeille);
                reqVeille.setIdContrat(ctVeille);
                reqVeille.setDateDepart(Date.valueOf(dVeille));
                reqVeille.setMotifRupture("DEMISSION");
                DossierSortieDTO dosVeille = dossierSortieService.sauvegarderDossier(reqVeille, adminId, c);
                SimulationStcResultDTO simVeille = dossierSortieService.simulerStcDepuisDossier(dosVeille.getIdDossierSortie(), c);

                // Cas 2 : Mois de 31 jours avec 30 jours réels sous contrat (départ le 30/01)
                // Conformément à l'arbitrage GALAXAM : jours réels sous contrat / 30, plafonnés à 30j (pas de plafonnement à 29j)
                assertEquals(0, new BigDecimal("30").compareTo(simVeille.getDecompte().getJoursTravaillesDernierMois()),
                        "Départ le 30 dans un mois de 31 jours donne 30 jours sous contrat");
                assertEquals(0, salBase.compareTo(simVeille.getDecompte().getSalaireProrata()),
                        "30 jours réels sous contrat sur base 30 donne 100% du salaire contractuel");

                // Cas 3 : Mois de 31 jours avec 29 jours réels sous contrat (départ le 29/01)
                int empId29 = CongeTestFixtureHelper.createEmploye(c, "P21-V29-" + (System.currentTimeMillis() % 1000000), "Rakoto", "J29");
                int ct29 = CongeTestFixtureHelper.createContratActif(c, empId29, LocalDate.of(2025, 1, 1));
                CongeTestFixtureHelper.createAffectation(c, empId29, ct29, LocalDate.of(2025, 1, 1), salBase);
                LocalDate d29 = LocalDate.of(2026, 1, 29);
                try (PreparedStatement ps = c.prepareStatement("UPDATE contrat_travail SET date_fin = ? WHERE id_contrat = ?")) {
                    ps.setDate(1, Date.valueOf(d29));
                    ps.setInt(2, ct29);
                    ps.executeUpdate();
                }
                DossierSortieSaveRequest req29 = new DossierSortieSaveRequest();
                req29.setIdEmploye(empId29);
                req29.setIdContrat(ct29);
                req29.setDateDepart(Date.valueOf(d29));
                req29.setMotifRupture("DEMISSION");
                DossierSortieDTO dos29 = dossierSortieService.sauvegarderDossier(req29, adminId, c);
                SimulationStcResultDTO sim29 = dossierSortieService.simulerStcDepuisDossier(dos29.getIdDossierSortie(), c);

                assertEquals(0, new BigDecimal("29").compareTo(sim29.getDecompte().getJoursTravaillesDernierMois()),
                        "Départ le 29 dans un mois de 31 jours donne 29 jours sous contrat");
                BigDecimal expectedProrata29 = salBase.multiply(new BigDecimal("29")).divide(new BigDecimal("30"), 4, RoundingMode.HALF_UP);
                assertEquals(0, MoneyRoundingPolicy.amount(expectedProrata29).compareTo(sim29.getDecompte().getSalaireProrata()),
                        "29 jours réels sous contrat donne exactement 29j/30");

                // Cas 4 : Cycle non civil 26 décembre – 25 janvier (31 jours)
                LocalDate dDebC26 = LocalDate.of(2026, 12, 26);
                LocalDate dFinC26 = LocalDate.of(2027, 1, 25);
                assurerPeriodePaieExacte(c, "2027-01-C2625-" + (System.currentTimeMillis() % 100000), dDebC26, dFinC26);
                int empIdC26 = CongeTestFixtureHelper.createEmploye(c, "P21-C31-" + (System.currentTimeMillis() % 1000000), "Ravelo", "Cycle31");
                int ctC26 = CongeTestFixtureHelper.createContratActif(c, empIdC26, LocalDate.of(2025, 1, 1));
                CongeTestFixtureHelper.createAffectation(c, empIdC26, ctC26, LocalDate.of(2025, 1, 1), salBase);
                LocalDate dDepC26 = LocalDate.of(2027, 1, 24); // 30 jours du 26/12 au 24/01 inclus
                try (PreparedStatement ps = c.prepareStatement("UPDATE contrat_travail SET date_fin = ? WHERE id_contrat = ?")) {
                    ps.setDate(1, Date.valueOf(dDepC26));
                    ps.setInt(2, ctC26);
                    ps.executeUpdate();
                }
                DossierSortieSaveRequest reqC26 = new DossierSortieSaveRequest();
                reqC26.setIdEmploye(empIdC26);
                reqC26.setIdContrat(ctC26);
                reqC26.setDateDepart(Date.valueOf(dDepC26));
                reqC26.setMotifRupture("DEMISSION");
                DossierSortieDTO dosC26 = dossierSortieService.sauvegarderDossier(reqC26, adminId, c);
                SimulationStcResultDTO simC26 = dossierSortieService.simulerStcDepuisDossier(dosC26.getIdDossierSortie(), c);

                assertEquals(0, new BigDecimal("30").compareTo(simC26.getDecompte().getJoursTravaillesDernierMois()),
                        "Cycle 26-25 de 31 jours avec départ la veille (30j réels) donne 30 jours");
                assertEquals(0, salBase.compareTo(simC26.getDecompte().getSalaireProrata()),
                        "30 jours réels sur le cycle 26-25 donne 100% sur base 30");

            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("Phase 2.1 : Objectif de présence abaissé à 32h par congé payé et retenue absence séparée")
    void testPhase21CongePayeAbaissementObjectifEtAbsenceSansSolde() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                int adminId = CongeTestFixtureHelper.createAdminUser(c, "rh_p21_cp_abs_" + System.currentTimeMillis());
                int empId = CongeTestFixtureHelper.createEmploye(c, "P21-CPABS-" + (System.currentTimeMillis() % 1000000), "Ranaivomanana", "Toky");
                LocalDate dateEmbauche = LocalDate.of(2026, 10, 5);
                LocalDate dateDepart = LocalDate.of(2026, 10, 13);
                int periodeId = assurerPeriodePaie(c, dateDepart);

                BigDecimal salaireBase = new BigDecimal("1200000.00");
                int contratId = CongeTestFixtureHelper.createContratActif(c, empId, dateEmbauche);
                CongeTestFixtureHelper.createAffectation(c, empId, contratId, dateEmbauche, salaireBase);
                try (PreparedStatement ps = c.prepareStatement("UPDATE contrat_travail SET date_fin = ? WHERE id_contrat = ?")) {
                    ps.setDate(1, Date.valueOf(dateDepart));
                    ps.setInt(2, contratId);
                    ps.executeUpdate();
                }

                int idStatutPresent = 1;
                try (PreparedStatement ps = c.prepareStatement("SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = 'PRESENT' LIMIT 1");
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) idStatutPresent = rs.getInt(1);
                }
                int idStatutAbsence = 2;
                try (PreparedStatement ps = c.prepareStatement("SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = 'ABSENCE_NON_JUSTIFIEE' LIMIT 1");
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) idStatutAbsence = rs.getInt(1);
                }
                int idSite = 1;
                try (PreparedStatement ps = c.prepareStatement("SELECT id_site FROM ref_site LIMIT 1");
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) idSite = rs.getInt(1);
                }

                // Semaine 1 : Lundi 5 au Vendredi 9 octobre
                // Mardi 6 : Congé payé (pas de décompte d'absence, objectif abaissé à 32h)
                int idStatutConge = 5;
                try (PreparedStatement ps = c.prepareStatement("SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = 'CONGE' LIMIT 1");
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) idStatutConge = rs.getInt(1);
                }
                for (int day = 5; day <= 9; day++) {
                    LocalDate d = LocalDate.of(2026, 10, day);
                    int statut = (day == 6) ? idStatutConge : idStatutPresent;
                    BigDecimal h = (day == 6) ? BigDecimal.ZERO : new BigDecimal("8.00");
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO pointage_jour (id_employe, id_periode_paie, id_site, date_pointage, id_statut_jour, total_heures) " +
                            "VALUES (?, ?, ?, ?, ?, ?)")) {
                        ps.setInt(1, empId);
                        ps.setInt(2, periodeId);
                        ps.setInt(3, idSite);
                        ps.setDate(4, Date.valueOf(d));
                        ps.setInt(5, statut);
                        ps.setBigDecimal(6, h);
                        ps.executeUpdate();
                    }
                }

                // Semaine 2 : Lundi 12 = 8h normales, Mardi 13 = Absence injustifiée (0h avec decompte_absence = true)
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO pointage_jour (id_employe, id_periode_paie, id_site, date_pointage, id_statut_jour, total_heures) " +
                        "VALUES (?, ?, ?, ?, ?, 8.00)")) {
                    ps.setInt(1, empId);
                    ps.setInt(2, periodeId);
                    ps.setInt(3, idSite);
                    ps.setDate(4, Date.valueOf(LocalDate.of(2026, 10, 12)));
                    ps.setInt(5, idStatutPresent);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO pointage_jour (id_employe, id_periode_paie, id_site, date_pointage, id_statut_jour, total_heures) " +
                        "VALUES (?, ?, ?, ?, ?, 0.00)")) {
                    ps.setInt(1, empId);
                    ps.setInt(2, periodeId);
                    ps.setInt(3, idSite);
                    ps.setDate(4, Date.valueOf(LocalDate.of(2026, 10, 13)));
                    ps.setInt(5, idStatutAbsence);
                    ps.executeUpdate();
                }

                DossierSortieSaveRequest req = new DossierSortieSaveRequest();
                req.setIdEmploye(empId);
                req.setIdContrat(contratId);
                req.setDateDepart(Date.valueOf(dateDepart));
                req.setMotifRupture("DEMISSION");

                DossierSortieDTO dossier = dossierSortieService.sauvegarderDossier(req, adminId, c);
                SimulationStcResultDTO sim = dossierSortieService.simulerStcDepuisDossier(dossier.getIdDossierSortie(), c);

                var lignesMap = new HashMap<String, BigDecimal>();
                for (var l : sim.getDecompte().getLignes()) {
                    lignesMap.put(l.getCode(), l.getMontant());
                }

                // Vérifier que la retenue absence ne concerne QUE l'absence injustifiée du 13/10 (8h)
                // et qu'aucune retenue n'a été prélevée pour la journée de congé payé du 06/10
                assertTrue(lignesMap.containsKey("RETENUE_ABSENCE"), "La retenue absence doit être présente");
                BigDecimal tauxHoraire = sim.getDecompte().getTauxHoraire();
                BigDecimal expectedRetenue8h = MoneyRoundingPolicy.amount(new BigDecimal("8").multiply(tauxHoraire));
                assertEquals(0, expectedRetenue8h.compareTo(lignesMap.get("RETENUE_ABSENCE")),
                        "La retenue d'absence doit exactement correspondre à 8h d'absence sans solde");

            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("Phase 2.1 : Rupture de fraîcheur et rejet du scellement si modification du pointage après simulation")
    void testPhase21RuptureFraicheurApresModificationPointage() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                int adminId = CongeTestFixtureHelper.createAdminUser(c, "rh_p21_ptgmod_" + System.currentTimeMillis());
                int empId = CongeTestFixtureHelper.createEmploye(c, "P21-PMOD-" + (System.currentTimeMillis() % 1000000), "Andry", "Lova");
                LocalDate dateDepart = LocalDate.of(2026, 10, 10);
                int periodeId = assurerPeriodePaie(c, dateDepart);

                BigDecimal salBase = new BigDecimal("1200000.00");
                int ctId = CongeTestFixtureHelper.createContratActif(c, empId, LocalDate.of(2026, 1, 1));
                CongeTestFixtureHelper.createAffectation(c, empId, ctId, LocalDate.of(2026, 1, 1), salBase);
                try (PreparedStatement ps = c.prepareStatement("UPDATE contrat_travail SET date_fin = ? WHERE id_contrat = ?")) {
                    ps.setDate(1, Date.valueOf(dateDepart));
                    ps.setInt(2, ctId);
                    ps.executeUpdate();
                }

                int idStatutPresent = 1;
                try (PreparedStatement ps = c.prepareStatement("SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = 'PRESENT' LIMIT 1");
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) idStatutPresent = rs.getInt(1);
                }
                int idSite = 1;
                try (PreparedStatement ps = c.prepareStatement("SELECT id_site FROM ref_site LIMIT 1");
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) idSite = rs.getInt(1);
                }

                // Insertion pointage 8h
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO pointage_jour (id_employe, id_periode_paie, id_site, date_pointage, id_statut_jour, total_heures) " +
                        "VALUES (?, ?, ?, ?, ?, 8.00)")) {
                    ps.setInt(1, empId);
                    ps.setInt(2, periodeId);
                    ps.setInt(3, idSite);
                    ps.setDate(4, Date.valueOf(LocalDate.of(2026, 10, 5)));
                    ps.setInt(5, idStatutPresent);
                    ps.executeUpdate();
                }

                DossierSortieSaveRequest req = new DossierSortieSaveRequest();
                req.setIdEmploye(empId);
                req.setIdContrat(ctId);
                req.setDateDepart(Date.valueOf(dateDepart));
                req.setMotifRupture("DEMISSION");

                DossierSortieDTO dossier = dossierSortieService.sauvegarderDossier(req, adminId, c);
                SimulationStcResultDTO sim = dossierSortieService.simulerStcDepuisDossier(dossier.getIdDossierSortie(), c);
                String refSim = sim.getReferenceSimulation();
                assertNotNull(refSim);

                // Altération du pointage : on ajoute 4h supplémentaires le 6 octobre
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO pointage_jour (id_employe, id_periode_paie, id_site, date_pointage, id_statut_jour, total_heures) " +
                        "VALUES (?, ?, ?, ?, ?, 12.00)")) {
                    ps.setInt(1, empId);
                    ps.setInt(2, periodeId);
                    ps.setInt(3, idSite);
                    ps.setDate(4, Date.valueOf(LocalDate.of(2026, 10, 6)));
                    ps.setInt(5, idStatutPresent);
                    ps.executeUpdate();
                }

                // Tentative de scellement avec l'ancienne référence de simulation
                ScellementStcRequest sealReq = new ScellementStcRequest(refSim, sim.getVersion(), "Tentative scellement après modif pointage");
                SirhBusinessException ex = assertThrows(SirhBusinessException.class, () ->
                        dossierSortieService.scellerDossierEtStc(dossier.getIdDossierSortie(), sealReq, adminId, c));
                assertEquals(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED, ex.getErrorCode());
                assertTrue(ex.getMessage().contains("fraîcheur") || ex.getMessage().contains("pointées") || ex.getMessage().contains("pointage"),
                        "Le scellement doit être bloqué par le contrôle de fraîcheur");

            } finally {
                c.rollback();
            }
        }
    }
}
