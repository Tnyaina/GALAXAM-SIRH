package mg.galaxam.sirh.paie;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import javax.sql.DataSource;
import mg.galaxam.sirh.api.dto.contrat.EmbaucheInitialeRequest;
import mg.galaxam.sirh.api.dto.employe.EmployeRequest;
import mg.galaxam.sirh.api.dto.paie.stc.StcCalculRequest;
import mg.galaxam.sirh.api.dto.paie.stc.StcCalculResultDTO;
import mg.galaxam.sirh.api.dto.paie.stc.StcPreparationDTO;
import mg.galaxam.sirh.api.dto.paie.stc.StcRecuDTO;
import mg.galaxam.sirh.model.contrat.WorkflowRhCPL;
import mg.galaxam.sirh.model.contrat.WorkflowRhService;
import mg.galaxam.sirh.model.employe.Employe;
import mg.galaxam.sirh.model.employe.EmployeCPL;
import mg.galaxam.sirh.model.employe.service.EmployeService;
import mg.galaxam.sirh.model.paie.BulletinPaie;
import mg.galaxam.sirh.model.paie.PaieAvanceAcompte;
import mg.galaxam.sirh.model.paie.PaiePret;
import mg.galaxam.sirh.model.paie.PaiePretEcheance;
import mg.galaxam.sirh.model.paie.dto.BulletinPaieComplet;
import mg.galaxam.sirh.model.paie.service.PayrollService;
import mg.galaxam.sirh.model.paie.service.StcService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("EndToEndDirectMethodsPaieStcTest — Cycle Complet Paie & STC via Méthodes Java Directes (Base Sans Dataset)")
public class EndToEndDirectMethodsPaieStcTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private EmployeService employeService;

    @Autowired
    private WorkflowRhService workflowRhService;

    @Autowired
    private PayrollService payrollService;

    @Autowired
    private StcService stcService;

    private static Integer idPeriodeMars2026;
    private static Integer idEmployeTest;
    private static Integer idPretTest;
    private static Integer idStcValide;

    @Test
    @Order(1)
    @DisplayName("METHOD-01 : Nettoyage préalable et création de la période de paie Mars 2026")
    void testCreerPeriodePaieDirecte() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // Nettoyage préalable pour garantir l'idempotence sur base de données propre
                String[] sqlClean = {
                        "DELETE FROM paie_stc WHERE id_employe IN (SELECT id_employe FROM employe WHERE matricule = 'EMP-DIR-001')",
                        "DELETE FROM bulletin_paie_ligne WHERE id_bulletin IN (SELECT id_bulletin FROM bulletin_paie WHERE matricule = 'EMP-DIR-001')",
                        "DELETE FROM bulletin_paie WHERE matricule = 'EMP-DIR-001'",
                        "DELETE FROM paie_avance_acompte WHERE id_employe IN (SELECT id_employe FROM employe WHERE matricule = 'EMP-DIR-001')",
                        "DELETE FROM paie_pret_echeance WHERE id_pret IN (SELECT id_pret FROM paie_pret WHERE id_employe IN (SELECT id_employe FROM employe WHERE matricule = 'EMP-DIR-001'))",
                        "DELETE FROM paie_pret WHERE id_employe IN (SELECT id_employe FROM employe WHERE matricule = 'EMP-DIR-001')",
                        "DELETE FROM pointage_cumul_periode WHERE id_employe IN (SELECT id_employe FROM employe WHERE matricule = 'EMP-DIR-001')",
                        "DELETE FROM conge_journal_droit WHERE id_employe IN (SELECT id_employe FROM employe WHERE matricule = 'EMP-DIR-001')",
                        "DELETE FROM mouvement_solde_conge WHERE id_employe IN (SELECT id_employe FROM employe WHERE matricule = 'EMP-DIR-001')",
                        "DELETE FROM notification_rh WHERE id_employe_concerne IN (SELECT id_employe FROM employe WHERE matricule = 'EMP-DIR-001')",
                        "DELETE FROM workflow_rh WHERE id_employe IN (SELECT id_employe FROM employe WHERE matricule = 'EMP-DIR-001')",
                        "DELETE FROM employe_affectation_carriere WHERE id_employe IN (SELECT id_employe FROM employe WHERE matricule = 'EMP-DIR-001')",
                        "DELETE FROM contrat_travail WHERE id_employe IN (SELECT id_employe FROM employe WHERE matricule = 'EMP-DIR-001')",
                        "DELETE FROM employe WHERE matricule = 'EMP-DIR-001'"
                };
                for (String sClean : sqlClean) {
                    try (PreparedStatement psClean = c.prepareStatement(sClean)) {
                        psClean.executeUpdate();
                    }
                }

                String sql = "INSERT INTO periode_paie (code, libelle, date_debut, date_fin, id_statut_periode) " +
                        "VALUES ('2026-03', 'Mars 2026', '2026-03-01', '2026-03-31', " +
                        "(SELECT id_statut_periode FROM ref_statut_periode_paie WHERE code = 'EN_COURS')) " +
                        "ON CONFLICT (code) DO UPDATE SET libelle = EXCLUDED.libelle " +
                        "RETURNING id_periode_paie";
                try (PreparedStatement ps = c.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    idPeriodeMars2026 = rs.getInt(1);
                }
                assertNotNull(idPeriodeMars2026, "La période Mars 2026 doit être créée");

                c.commit();
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            }
        }
    }

    @Test
    @Order(2)
    @DisplayName("METHOD-02 : Création directe du collaborateur via EmployeService.createEmploye")
    void testCreerEmployeDirect() throws Exception {
        EmployeRequest req = new EmployeRequest();
        req.setMatricule("EMP-DIR-001");
        req.setNom("ANDRIANARIVO");
        req.setPrenom("Tolotra");
        req.setSexe("M");
        req.setDateNaissance(Date.valueOf("1992-05-14"));
        req.setLieuNaissance("Antananarivo");
        req.setNationalite("Malgache");
        req.setCinNumero("101234567890");
        req.setCinDateDelivrance(Date.valueOf("2012-06-10"));
        req.setCinLieuDelivrance("Antananarivo I");
        req.setNumAffiliationCnaps("010-098765-C");
        req.setAdresse("Lot II A 45 Soanierana");
        req.setVille("Antananarivo");
        req.setTelephone("+261 34 00 111 22");
        req.setEmail("tolotra.andrianarivo@galaxam.mg");
        req.setDateDeclaration(Date.valueOf("2024-01-15"));

        EmployeCPL emp = employeService.creerEmploye(req);
        assertNotNull(emp);
        assertNotNull(emp.getIdEmploye());
        idEmployeTest = emp.getIdEmploye();
        assertEquals("EMP-DIR-001", emp.getMatricule());
    }

    @Test
    @Order(3)
    @DisplayName("METHOD-03 : Embauche initiale et activation carrière via WorkflowRhService")
    void testEmbaucheCarriereDirecte() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                EmbaucheInitialeRequest req = new EmbaucheInitialeRequest();
                req.setIdFiliale(1);
                req.setIdTypeContrat(1); // CDI
                req.setIdTypeIntervenant(1); // PERMANENT
                req.setDateDebut(Date.valueOf("2024-01-15"));
                req.setDateSignature(Date.valueOf("2024-01-10"));
                req.setNumeroContrat("CT-2024-CDI-DIR-01");
                req.setComptabiliseEffectif(true);

                req.setIdSite(1); // Siège TNR
                req.setIdDepartement(1); // Direction Générale / Opérations
                req.setIdService(1); // Administration & RH
                req.setIdFonction(1); // Responsable Logistique
                req.setIdCategorieProfessionnelle(1); // CADRE
                req.setIdModeCalculPaie(1); // MENSUEL
                req.setSalaireBase(new BigDecimal("1800000.00"));
                req.setMotif("Recrutement CDI Cadre Opérations");

                // 1. Créer le brouillon d'embauche
                WorkflowRhCPL wf = workflowRhService.creerBrouillonEmbauche(idEmployeTest, req, 1, c);
                assertNotNull(wf);
                assertNotNull(wf.getIdWorkflow());

                // 2. Valider l'embauche (activation immédiate du contrat et affectation)
                workflowRhService.validerWorkflow(wf.getIdWorkflow(), null, 1, c);

                c.commit();
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            }
        }

        // Vérification en base : salarié actif et contrat actif
        try (Connection c = dataSource.getConnection()) {
            Employe emp = (Employe) new Employe().rechercherParId(String.valueOf(idEmployeTest), c);
            assertNotNull(emp);
            assertTrue(emp.isEstActif(), "Le salarié doit être activé en base");
        }
    }

    @Test
    @Order(4)
    @DisplayName("METHOD-04 : Saisie pointage et Grand-Livre dynamique des congés (conge_journal_droit)")
    void testPointageEtLedgerConges() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // 1. Enregistrer le scellement de pointage pour Mars 2026
                String sqlPointage = "INSERT INTO pointage_cumul_periode (" +
                        "id_employe, id_periode_paie, nb_jours_ouvrables, nb_jours_presents, nb_jours_absence, " +
                        "nb_jours_conge, total_heures_travaillees, heures_hs_30, heures_hs_50, heures_nuit_total, " +
                        "montant_repas, montant_deplacement, gele_at) " +
                        "VALUES (?, ?, 22, 20, 1, 1, 160.0, 5.0, 0, 0, 0, 31200.00, NOW()) " +
                        "ON CONFLICT (id_employe, id_periode_paie) DO NOTHING";
                try (PreparedStatement ps = c.prepareStatement(sqlPointage)) {
                    ps.setInt(1, idEmployeTest);
                    ps.setInt(2, idPeriodeMars2026);
                    ps.executeUpdate();
                }

                // 2. Enregistrer l'historique dans le Grand-Livre dynamique conge_journal_droit
                // Taux journalier valorisé TCONGE = 60 000 Ar, solde = 15 jours
                String sqlLedger = "INSERT INTO conge_journal_droit (" +
                        "id_employe, id_periode_paie, annee, mois, salaire_base, jours_travailles_formule, " +
                        "jours_droit_acquis, base_valorisation, valorisation_mensuelle, cumul_jours_droit, " +
                        "cumul_valorisation, taux_journalier_conge) " +
                        "VALUES (?, ?, 2026, 3, 1800000.00, 29.0, 2.42, 1800000.00, 145200.00, 15.00, 900000.00, 60000.00) " +
                        "ON CONFLICT (id_employe, annee, mois) DO UPDATE SET " +
                        "cumul_jours_droit = EXCLUDED.cumul_jours_droit, " +
                        "cumul_valorisation = EXCLUDED.cumul_valorisation, " +
                        "taux_journalier_conge = EXCLUDED.taux_journalier_conge";
                try (PreparedStatement ps = c.prepareStatement(sqlLedger)) {
                    ps.setInt(1, idEmployeTest);
                    ps.setInt(2, idPeriodeMars2026);
                    ps.executeUpdate();
                }

                // 3. Alimenter un mouvement de solde de congés pour que VSoldeCongeEmploye renvoie 15.0j
                String sqlMvtConge = "INSERT INTO mouvement_solde_conge (" +
                        "id_employe, type_mouvement, nb_jours, date_mouvement, commentaire) " +
                        "VALUES (?, 'ACQUISITION', 15.0, '2026-03-01', 'Solde acquis cumulé 2024-2026')";
                try (PreparedStatement ps = c.prepareStatement(sqlMvtConge)) {
                    ps.setInt(1, idEmployeTest);
                    ps.executeUpdate();
                }

                c.commit();
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            }
        }
    }

    @Test
    @Order(5)
    @DisplayName("METHOD-05 : Octroi d'un prêt d'entreprise via PaiePret et prime via PaieAvanceAcompte")
    void testPretEtPrimeDirects() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // 1. Accord de prêt entreprise de 600 000 Ar remboursable en 6 mensualités de 100 000 Ar
                PaiePret pret = new PaiePret();
                pret.setIdEmploye(idEmployeTest);
                pret.setReferencePret("PRET-DIR-" + System.currentTimeMillis());
                pret.setMontantTotal(new BigDecimal("600000.00"));
                pret.setMontantMensualite(new BigDecimal("100000.00"));
                pret.setNbEcheances(6);
                pret.setDateAccord(Date.valueOf("2026-02-01"));
                pret.setDateDeblocage(Date.valueOf("2026-02-05"));
                pret.setDatePremiereEcheance(Date.valueOf("2026-03-31"));
                pret.setCapitalRembourse(BigDecimal.ZERO);
                pret.setCapitalRestantDu(new BigDecimal("600000.00"));
                pret.setStatut("EN_COURS");
                pret.setMotif("Prêt installation familiale");
                pret.enregistrerAvecEcheancier(c);

                idPretTest = pret.getIdPret();
                assertNotNull(idPretTest, "Le prêt doit avoir un ID généré");

                // 2. Prime d'assiduité approuvée
                PaieAvanceAcompte prime = new PaieAvanceAcompte();
                prime.setIdEmploye(idEmployeTest);
                prime.setIdPeriodePaie(idPeriodeMars2026);
                prime.setTypeMouvement("PRIME_ASSIDUITE");
                prime.setSens("GAIN");
                prime.setMontant(new BigDecimal("50000.00"));
                prime.setDateDemande(Date.valueOf("2026-03-10"));
                prime.setMotif("Assiduité parfaite Mars 2026");
                prime.setStatut("APPROUVE");
                prime.save(c);

                c.commit();
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            }
        }
    }

    @Test
    @Order(6)
    @DisplayName("METHOD-06 : Calcul du bulletin mensuel normal via PayrollService")
    void testCalculBulletinMensuelNormal() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                BulletinPaieComplet bpc = payrollService.calculerBulletinEmploye(
                        idEmployeTest, idPeriodeMars2026, false, 1, c);

                assertNotNull(bpc);
                BulletinPaie bp = bpc.getBulletin();
                assertNotNull(bp);
                assertEquals("EMP-DIR-001", bp.getMatricule());
                assertEquals(new BigDecimal("1800000.00"), bp.getSalaireBase());
                assertTrue(bp.getTotalGainsBruts().compareTo(BigDecimal.ZERO) > 0);

                // Vérifier la déduction de la mensualité de prêt (100 000 Ar)
                assertTrue(bp.getTotalRetenuesAcomptePret().compareTo(BigDecimal.ZERO) > 0,
                        "La mensualité de prêt doit être déduite dans le total des retenues de trésorerie");

                c.commit();
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            }
        }

        // Vérifier que le capital restant dû du prêt est passé à 500 000 Ar
        try (Connection c = dataSource.getConnection()) {
            BigDecimal capitalRestant = PaiePret.calculerCapitalRestantTotal(idEmployeTest, c);
            assertEquals(0, new BigDecimal("500000.00").compareTo(capitalRestant),
                    "Après le prélèvement d'une échéance de 100 000 Ar, le restant dû doit être 500 000 Ar");
        }
    }

    @Test
    @Order(7)
    @DisplayName("METHOD-07 : Établir le STC — Préparation administrative via StcService.preparerStc")
    void testPreparerStcDirect() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            Date dateDepart = Date.valueOf("2026-03-25");
            StcPreparationDTO prep = stcService.preparerStc(idEmployeTest, dateDepart, "LICENCIEMENT", c);

            assertNotNull(prep);
            assertEquals("EMP-DIR-001", prep.getMatricule());
            assertEquals(new BigDecimal("1800000.00"), prep.getSalaireBase());

            // Ancienneté : embauche 2024-01-15 -> départ 2026-03-25 = 2 ans et 2 mois
            assertEquals(2, prep.getAncienneteAnnees());
            assertEquals(26, prep.getAncienneteMois());

            // Congés : solde 15j, valorisation TCONGE = 60 000 Ar
            assertEquals(0, new BigDecimal("15.00").compareTo(prep.getSoldeCongesRestant()));
            assertEquals(0, new BigDecimal("60000.00").compareTo(prep.getTauxJournalierConge()));
            assertEquals(0, new BigDecimal("900000.00").compareTo(prep.getIccpEstimee()), "15j x 60 000 Ar = 900 000 Ar ICCP");

            // Capital restant dû du prêt : 500 000 Ar
            assertEquals(0, new BigDecimal("500000.00").compareTo(prep.getCapitalRestantPrets()));

            // Préavis suggéré cadre : 3 mois (3 x 1 800 000 = 5 400 000 Ar)
            assertEquals(new BigDecimal("3.0"), prep.getMoisPreavisSuggere());
            assertEquals(0, new BigDecimal("5400000.00").compareTo(prep.getIndemnitePreavisEstimee()));
        }
    }

    @Test
    @Order(8)
    @DisplayName("METHOD-08 : Simulation STC via StcService.simulerStc")
    void testSimulerStcDirect() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            StcCalculRequest req = new StcCalculRequest();
            req.setIdEmploye(idEmployeTest);
            req.setDateDepart(Date.valueOf("2026-03-25"));
            req.setMotifRupture("LICENCIEMENT");
            req.setJoursTravaillesDernierMois(new BigDecimal("25.0"));
            req.setMoisPreavis(new BigDecimal("2.0")); // Accord pour 2 mois de préavis payé
            req.setIndemniteLicenciement(new BigDecimal("400000.00")); // Indemnité légale
            req.setSimulation(true);

            StcCalculResultDTO sim = stcService.simulerStc(req, c);

            assertNotNull(sim);
            assertEquals("SIMULATION", sim.getStatut());

            // 1. Prorata salaire 25j : 1 800 000 * 25 / 30 = 1 500 000 Ar
            assertEquals(0, new BigDecimal("1500000.00").compareTo(sim.getSalaireProrata()));

            // 2. ICCP : 900 000 Ar
            assertEquals(0, new BigDecimal("900000.00").compareTo(sim.getIndemniteCongesPayes()));

            // 3. Préavis : 2 mois x 1 800 000 = 3 600 000 Ar
            assertEquals(0, new BigDecimal("3600000.00").compareTo(sim.getIndemnitePreavis()));

            // 4. Licenciement : 400 000 Ar
            assertEquals(0, new BigDecimal("400000.00").compareTo(sim.getIndemniteLicenciement()));

            // Total Brut = 1 500 000 + 900 000 + 3 600 000 + 400 000 = 6 400 000 Ar
            assertEquals(0, new BigDecimal("6400000.00").compareTo(sim.getTotalBrutStc()));

            // 5. Apurement intégral du prêt à 100% : 500 000 Ar
            assertEquals(0, new BigDecimal("500000.00").compareTo(sim.getSoldePretsDeduit()));

            // Net à Payer > 0
            assertTrue(sim.getNetAPayerStc().compareTo(BigDecimal.ZERO) > 0);
        }
    }

    @Test
    @Order(9)
    @DisplayName("METHOD-09 : Validation STC, numérotation de reçu et apurement complet du prêt")
    void testValiderStcDirect() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                StcCalculRequest req = new StcCalculRequest();
                req.setIdEmploye(idEmployeTest);
                req.setDateDepart(Date.valueOf("2026-03-25"));
                req.setMotifRupture("LICENCIEMENT");
                req.setJoursTravaillesDernierMois(new BigDecimal("25.0"));
                req.setMoisPreavis(new BigDecimal("2.0"));
                req.setIndemniteLicenciement(new BigDecimal("400000.00"));
                req.setModeReglement("VIREMENT");
                req.setCommentaire("Rupture avec quittance libératoire complète");

                StcCalculResultDTO res = stcService.validerStc(req, 1, c);

                assertNotNull(res);
                assertEquals("VALIDE", res.getStatut());
                assertNotNull(res.getNumeroRecu());
                assertTrue(res.getNumeroRecu().startsWith("STC-2026-"));
                idStcValide = res.getIdStc();

                c.commit();
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            }
        }

        // Vérification de la liquidation intégrale du prêt en base :
        // Le prêt doit avoir statut 'SOLDE' et capitalRestantDu = 0
        try (Connection c = dataSource.getConnection()) {
            PaiePret pretApresStc = PaiePret.trouverParId(idPretTest, c);
            assertNotNull(pretApresStc);
            assertEquals("SOLDE", pretApresStc.getStatut(), "Le prêt doit être intégralement soldé par le STC");
            assertEquals(0, new BigDecimal("0.00").compareTo(pretApresStc.getCapitalRestantDu()));

            BigDecimal restantTotal = PaiePret.calculerCapitalRestantTotal(idEmployeTest, c);
            assertEquals(0, BigDecimal.ZERO.compareTo(restantTotal), "Le total restant dû de l'employé doit être 0");
        }
    }

    @Test
    @Order(10)
    @DisplayName("METHOD-10 : Émission de la Quittance Libératoire officielle Art. 53 (StcRecuDTO)")
    void testObtenirRecuOfficielStc() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            StcRecuDTO recu = stcService.obtenirRecuStc(idStcValide, c);

            assertNotNull(recu);
            assertEquals("VALIDE", recu.getStatut());
            assertEquals("EMP-DIR-001", recu.getMatricule());
            assertEquals("ANDRIANARIVO Tolotra", recu.getNomComplet());
            assertEquals("GALAXAM LOGISTICS", recu.getEntrepriseNom());
            assertEquals(0, new BigDecimal("500000.00").compareTo(recu.getSoldePretsApure()), "Prêt de 500 000 Ar apuré");
            assertTrue(recu.getNetAPayer().compareTo(BigDecimal.ZERO) > 0);
            assertNotNull(recu.getNetEnLettres());
            assertTrue(recu.getMentionLiberatoire().contains("pour solde de tout compte et règlement définitif"));
            assertEquals("Bon pour solde de tout compte et quittance finale", recu.getFormuleManuscriteObligatoire());
        }
    }
}
