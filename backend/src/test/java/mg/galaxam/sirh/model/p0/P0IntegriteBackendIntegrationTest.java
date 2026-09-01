package mg.galaxam.sirh.model.p0;

import mg.galaxam.sirh.api.dto.contrat.TransitionCarriereRequest;
import mg.galaxam.sirh.api.dto.contrat.ValiderWorkflowRequest;
import mg.galaxam.sirh.api.dto.pointage.SaisiePointageJourDTO;
import mg.galaxam.sirh.model.conge.DemandeConge;
import mg.galaxam.sirh.model.conge.RefTypeConge;
import mg.galaxam.sirh.model.conge.service.CongeService;
import mg.galaxam.sirh.model.contrat.ContratTravail;
import mg.galaxam.sirh.model.contrat.EmployeAffectationCarriere;
import mg.galaxam.sirh.model.contrat.WorkflowRhCPL;
import mg.galaxam.sirh.model.contrat.WorkflowRhService;
import mg.galaxam.sirh.model.employe.Employe;
import mg.galaxam.sirh.model.pointage.service.PointageAnomalieService;
import mg.galaxam.sirh.model.pointage.service.PointageSaisieService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class P0IntegriteBackendIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private CongeService congeService;

    @Autowired
    private PointageSaisieService pointageSaisieService;

    @Autowired
    private PointageAnomalieService pointageAnomalieService;

    @Autowired
    private WorkflowRhService workflowRhService;

    // =========================================================================
    // HELPER METHODS FOR TEST SETUP
    // =========================================================================

    private int queryId(Connection c, String sql) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        }
        throw new SQLException("Aucun résultat pour: " + sql);
    }

    private Employe creerEmployeTest(String prefix, Connection c) throws Exception {
        String shortPrefix = prefix.length() > 6 ? prefix.substring(0, 6) : prefix;
        String uniqueMatricule = shortPrefix + (System.nanoTime() % 1000000000L);
        if (uniqueMatricule.length() > 20) {
            uniqueMatricule = uniqueMatricule.substring(0, 20);
        }
        Employe emp = new Employe();
        emp.setMatricule(uniqueMatricule);
        emp.setNom("TestNom_" + uniqueMatricule);
        emp.setPrenom("TestPrenom");
        emp.setSexe("M");
        emp.setDateNaissance(Date.valueOf("1995-05-15"));
        emp.setLieuNaissance("Antananarivo");
        emp.setCinNumero("101" + (System.nanoTime() % 1000000000L));
        emp.setCinDateDelivrance(Date.valueOf("2015-01-01"));
        emp.setCinLieuDelivrance("Antananarivo");
        emp.setTelephone("+261 34 00 000 00");
        emp.setEmail(uniqueMatricule.toLowerCase().replace("-", "").replace("_", "") + "@test-galaxam.mg");
        emp.setAdresse("Lot IV Test Antananarivo");
        emp.setEstActif(true);
        emp.save(c);
        return emp;
    }

    private ContratTravail creerContratTest(int idEmploye, String codeStatut, Date debut, Date fin, Connection c) throws Exception {
        int idStatut = queryId(c, "SELECT id_statut_rh FROM ref_statut_rh WHERE code = '" + codeStatut + "'");
        int idFiliale = queryId(c, "SELECT id_filiale FROM ref_filiale LIMIT 1");
        int idTypeContrat = queryId(c, "SELECT id_type_contrat FROM ref_type_contrat LIMIT 1");
        int idTypeIntervenant = queryId(c, "SELECT id_type_intervenant FROM ref_type_intervenant LIMIT 1");

        ContratTravail ct = new ContratTravail();
        ct.setIdEmploye(idEmploye);
        ct.setNumeroContrat("CT-" + idEmploye + "-" + System.nanoTime() % 100000);
        ct.setIdFiliale(idFiliale);
        ct.setIdTypeContrat(idTypeContrat);
        ct.setIdTypeIntervenant(idTypeIntervenant);
        ct.setIdStatutRh(idStatut);
        ct.setDateDebut(debut);
        ct.setDateFin(fin);
        ct.setComptabiliseEffectif(true);
        ct.save(c);
        return ct;
    }

    private EmployeAffectationCarriere creerAffectationTest(int idEmploye, int idContrat, int idSite, String codeStatut, Date debut, Date fin, Connection c) throws Exception {
        int idStatut = queryId(c, "SELECT id_statut_rh FROM ref_statut_rh WHERE code = '" + codeStatut + "'");
        int idDept = queryId(c, "SELECT id_departement FROM ref_departement LIMIT 1");
        int idService = queryId(c, "SELECT id_service FROM ref_service LIMIT 1");
        int idFonction = queryId(c, "SELECT id_fonction FROM ref_fonction LIMIT 1");
        int idCatPro = queryId(c, "SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle LIMIT 1");
        int idModePaie = queryId(c, "SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie LIMIT 1");
        int idMotifMouv = queryId(c, "SELECT id_motif_mouvement FROM ref_motif_mouvement LIMIT 1");

        EmployeAffectationCarriere aff = new EmployeAffectationCarriere();
        aff.setIdEmploye(idEmploye);
        aff.setIdContrat(idContrat);
        aff.setIdSite(idSite);
        aff.setIdDepartement(idDept);
        aff.setIdService(idService);
        aff.setIdFonction(idFonction);
        aff.setIdCategorieProfessionnelle(idCatPro);
        aff.setIdModeCalculPaie(idModePaie);
        aff.setIdMotifMouvement(idMotifMouv);
        aff.setSalaireBase(new BigDecimal("1500000.00"));
        aff.setIdStatutRh(idStatut);
        aff.setDateDebut(debut);
        aff.setDateFin(fin);
        aff.save(c);
        return aff;
    }

    private int creerPeriodePaieTest(String codeStatut, LocalDate debut, LocalDate fin, Connection c) throws Exception {
        try (Statement stmt = c.createStatement()) {
            stmt.execute("INSERT INTO ref_statut_periode_paie (code, libelle) VALUES ('TRANSMIS_DAF', 'Transmise à la DAF') ON CONFLICT (code) DO NOTHING");
        } catch (Exception ignored) {}

        String sql = "INSERT INTO periode_paie (id_statut_periode, code, libelle, date_debut, date_fin) " +
                "VALUES ((SELECT id_statut_periode FROM ref_statut_periode_paie WHERE code = ?), ?, ?, ?, ?) RETURNING id_periode_paie";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, codeStatut);
            ps.setString(2, "PER-" + System.nanoTime() % 100000);
            ps.setString(3, "Période de test " + codeStatut);
            ps.setDate(4, Date.valueOf(debut));
            ps.setDate(5, Date.valueOf(fin));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("Impossible de créer la période de paie de test");
    }

    // =========================================================================
    // GROUPE A : CONGÉS (P0-CNG-001)
    // =========================================================================

    @Test
    @DisplayName("Scénario 1 : Congé sans contrat couvrant la période -> FAIL")
    void test01_Conge_SansContratCouvrantPeriode_DoitEchouer() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Employe emp = creerEmployeTest("EMP-TEST-CNG-01", c);
                creerContratTest(emp.getIdEmploye(), "ACTIF", Date.valueOf("2026-01-01"), Date.valueOf("2026-06-30"), c);

                DemandeConge d = new DemandeConge();
                d.setIdEmploye(emp.getIdEmploye());
                d.setIdTypeConge(1); // Annuel
                d.setDateDebut(Date.valueOf("2026-08-01"));
                d.setDateFin(Date.valueOf("2026-08-10"));

                IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
                    congeService.creerDemande(d, 1, c);
                });
                assertTrue(ex.getMessage().toLowerCase().contains("contrat") || ex.getMessage().toLowerCase().contains("couvrant"),
                        "Le message doit mentionner l'absence de contrat couvrant : " + ex.getMessage());
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("Scénario 2 : Congés qui se chevauchent pour le même employé -> FAIL")
    void test02_Conge_Chevauchement_DoitEchouer() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Employe emp = creerEmployeTest("EMP-TEST-CNG-02", c);
                creerContratTest(emp.getIdEmploye(), "ACTIF", Date.valueOf("2026-01-01"), Date.valueOf("2026-12-31"), c);

                DemandeConge d1 = new DemandeConge();
                d1.setIdEmploye(emp.getIdEmploye());
                d1.setIdTypeConge(1);
                d1.setDateDebut(Date.valueOf("2026-04-10"));
                d1.setDateFin(Date.valueOf("2026-04-15"));
                congeService.creerDemande(d1, 1, c);

                DemandeConge d2 = new DemandeConge();
                d2.setIdEmploye(emp.getIdEmploye());
                d2.setIdTypeConge(1);
                d2.setDateDebut(Date.valueOf("2026-04-14"));
                d2.setDateFin(Date.valueOf("2026-04-18"));

                IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
                    congeService.creerDemande(d2, 1, c);
                });
                assertTrue(ex.getMessage().toLowerCase().contains("chevauchement"), "Le message doit mentionner le chevauchement : " + ex.getMessage());
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("Scénario 3 : Congé dépassant une limite légale/métier -> FAIL")
    void test03_Conge_DepassantLimiteMetier_DoitEchouer() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Employe emp = creerEmployeTest("EMP-TEST-CNG-03", c);
                creerContratTest(emp.getIdEmploye(), "ACTIF", Date.valueOf("2026-01-01"), Date.valueOf("2026-12-31"), c);

                RefTypeConge mat = RefTypeConge.rechercherParCode("MATERNITE", c);
                if (mat != null) {
                    DemandeConge d = new DemandeConge();
                    d.setIdEmploye(emp.getIdEmploye());
                    d.setIdTypeConge(mat.getIdTypeConge());
                    d.setDateDebut(Date.valueOf("2026-05-01"));
                    d.setDateFin(Date.valueOf("2026-09-01")); // ~123 jours > 98 jours max

                    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
                        congeService.creerDemande(d, 1, c);
                    });
                    assertTrue(ex.getMessage().toLowerCase().contains("maternité") || ex.getMessage().contains("98"),
                            "Le message doit mentionner le dépassement du plafond maternité : " + ex.getMessage());
                }
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("Scénario 4 : Congé valide -> SUCCESS")
    void test04_Conge_Valide_DoitReussir() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Employe emp = creerEmployeTest("EMP-TEST-CNG-04", c);
                creerContratTest(emp.getIdEmploye(), "ACTIF", Date.valueOf("2026-01-01"), Date.valueOf("2026-12-31"), c);

                DemandeConge d = new DemandeConge();
                d.setIdEmploye(emp.getIdEmploye());
                d.setIdTypeConge(1); // Annuel
                d.setDateDebut(Date.valueOf("2026-07-01"));
                d.setDateFin(Date.valueOf("2026-07-05"));
                d.setCommentaire("Congé estival régulier");

                DemandeConge saved = congeService.creerDemande(d, 1, c);
                assertNotNull(saved.getIdDemandeConge(), "L'ID de la demande créée ne doit pas être null");
                assertTrue(saved.getIdDemandeConge() > 0, "L'ID doit être positif");
                assertEquals("EN_ATTENTE", saved.getStatutCode(c));
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("Scénario 5 : Validation d'un congé devenu invalide entre création et validation -> FAIL")
    void test05_Conge_ValidationDevenueInvalideEntreCreationEtValidation_DoitEchouer() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Employe emp = creerEmployeTest("EMP-TEST-CNG-05", c);
                ContratTravail ct = creerContratTest(emp.getIdEmploye(), "ACTIF", Date.valueOf("2026-01-01"), Date.valueOf("2026-12-31"), c);

                DemandeConge d = new DemandeConge();
                d.setIdEmploye(emp.getIdEmploye());
                d.setIdTypeConge(1);
                d.setDateDebut(Date.valueOf("2026-09-01"));
                d.setDateFin(Date.valueOf("2026-09-05"));
                DemandeConge saved = congeService.creerDemande(d, 1, c);

                ct.setDateFin(Date.valueOf("2026-08-31"));
                ct.update(c);

                IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
                    saved.valider(1, c);
                });
                assertTrue(ex.getMessage().toLowerCase().contains("contrat") || ex.getMessage().toLowerCase().contains("couvrant"),
                        "La validation doit échouer car le contrat ne couvre plus la période : " + ex.getMessage());
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("Scénario 5b (Concurrence Réelle) : Double validation simultanée -> exactement 1 succès, 1 rejet, 1 seul mouvement PRISE")
    void test05b_Conge_DoubleValidationConcurrente_ExactementUnMouvementPrise() throws Exception {
        int idDemande;
        int idEmp;
        try (Connection c = dataSource.getConnection()) {
            Employe emp = creerEmployeTest("EMP-TEST-CNG-CONC", c);
            idEmp = emp.getIdEmploye();
            creerContratTest(idEmp, "ACTIF", Date.valueOf("2025-01-01"), Date.valueOf("2026-12-31"), c);

            int idTypeAnnuel = queryId(c, "SELECT id_type_conge FROM ref_type_conge WHERE code = 'ANNUEL'");

            // Alimenter un solde acquis de 20 jours
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO mouvement_solde_conge (id_employe, type_mouvement, nb_jours, date_mouvement, commentaire) VALUES (?, 'ACQUISITION', 20.0, CURRENT_DATE, 'Solde test')")) {
                ps.setInt(1, idEmp);
                ps.executeUpdate();
            }

            DemandeConge d = new DemandeConge();
            d.setIdEmploye(idEmp);
            d.setIdTypeConge(idTypeAnnuel);
            d.setDateDebut(Date.valueOf("2026-10-01"));
            d.setDateFin(Date.valueOf("2026-10-05"));
            DemandeConge saved = congeService.creerDemande(d, 1, c);
            idDemande = saved.getIdDemandeConge();
        }

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        Callable<Void> task = () -> {
            startGate.await();
            try (Connection c = dataSource.getConnection()) {
                c.setAutoCommit(false);
                try {
                    DemandeConge d = DemandeConge.rechercherParId(idDemande, c);
                    d.valider(1, c);
                    c.commit();
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    c.rollback();
                    failureCount.incrementAndGet();
                }
            }
            return null;
        };

        Future<Void> f1 = executor.submit(task);
        Future<Void> f2 = executor.submit(task);

        startGate.countDown(); // Déclenchement simultané des deux threads
        f1.get();
        f2.get();
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactement une validation doit réussir");
        assertEquals(1, failureCount.get(), "L'autre validation concurrente doit échouer");

        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement psCount = c.prepareStatement("SELECT COUNT(*) FROM mouvement_solde_conge WHERE id_demande_conge = ? AND type_mouvement = 'PRISE'")) {
                psCount.setInt(1, idDemande);
                try (ResultSet rs = psCount.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1), "Le ledger ne doit contenir qu'un seul et unique mouvement PRISE");
                }
            }
            // Nettoyage
            try (Statement st = c.createStatement()) {
                st.execute("DELETE FROM mouvement_solde_conge WHERE id_employe = " + idEmp);
                st.execute("DELETE FROM demande_conge WHERE id_employe = " + idEmp);
                st.execute("DELETE FROM contrat_travail WHERE id_employe = " + idEmp);
                st.execute("DELETE FROM employe WHERE id_employe = " + idEmp);
            }
        }
    }

    // =========================================================================
    // GROUPE B : POINTAGE ÉLIGIBILITÉ (P0-PTG-001) - Lecture & Écriture
    // =========================================================================

    @Test
    @DisplayName("Scénario 6 : Employé sans contrat actif -> absent de la grille de pointage")
    void test06_Pointage_EmployeSansContratActif_AbsentDeGrille() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Employe emp = creerEmployeTest("EMP-TEST-PTG-06", c);
                ContratTravail ct = creerContratTest(emp.getIdEmploye(), "BROUILLON", Date.valueOf("2026-01-01"), null, c);
                creerAffectationTest(emp.getIdEmploye(), ct.getIdContrat(), 1, "ACTIF", Date.valueOf("2026-01-01"), null, c);

                Map<String, Object> grille = pointageSaisieService.getGrilleSaisie(1, "2026-08-15", c);
                @SuppressWarnings("unchecked")
                List<SaisiePointageJourDTO> liste = (List<SaisiePointageJourDTO>) grille.get("grilleData");

                boolean present = liste.stream().anyMatch(dto -> emp.getMatricule().equals(dto.getMatricule()));
                assertFalse(present, "L'employé avec contrat BROUILLON ne doit pas apparaître dans la grille de pointage");
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("Scénario 7 : Contrat clôturé avant la date -> absent de la grille de pointage")
    void test07_Pointage_ContratCloture_AbsentDeGrille() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Employe emp = creerEmployeTest("EMP-TEST-PTG-07", c);
                ContratTravail ct = creerContratTest(emp.getIdEmploye(), "ACTIF", Date.valueOf("2026-01-01"), Date.valueOf("2026-06-30"), c);
                creerAffectationTest(emp.getIdEmploye(), ct.getIdContrat(), 1, "ACTIF", Date.valueOf("2026-01-01"), Date.valueOf("2026-06-30"), c);

                Map<String, Object> grille = pointageSaisieService.getGrilleSaisie(1, "2026-08-15", c);
                @SuppressWarnings("unchecked")
                List<SaisiePointageJourDTO> liste = (List<SaisiePointageJourDTO>) grille.get("grilleData");

                boolean present = liste.stream().anyMatch(dto -> emp.getMatricule().equals(dto.getMatricule()));
                assertFalse(present, "L'employé avec contrat échu ne doit pas apparaître dans la grille");
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("Scénario 8 : Contrat actif couvrant la date sur le site -> présent dans la grille")
    void test08_Pointage_ContratActifCouvrantDate_PresentDansGrille() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Employe emp = creerEmployeTest("EMP-TEST-PTG-08", c);
                ContratTravail ct = creerContratTest(emp.getIdEmploye(), "ACTIF", Date.valueOf("2026-01-01"), null, c);
                creerAffectationTest(emp.getIdEmploye(), ct.getIdContrat(), 1, "ACTIF", Date.valueOf("2026-01-01"), null, c);

                Map<String, Object> grille = pointageSaisieService.getGrilleSaisie(1, "2026-08-15", c);
                @SuppressWarnings("unchecked")
                List<SaisiePointageJourDTO> liste = (List<SaisiePointageJourDTO>) grille.get("grilleData");

                boolean present = liste.stream().anyMatch(dto -> emp.getMatricule().equals(dto.getMatricule()));
                assertTrue(present, "L'employé actif avec contrat et affectation valides DOIT être présent dans la grille");
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("Scénario 9 : Affectation inactive (SUSPENDU) -> absent de la grille")
    void test09_Pointage_AffectationInactive_AbsentDeGrille() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Employe emp = creerEmployeTest("EMP-TEST-PTG-09", c);
                ContratTravail ct = creerContratTest(emp.getIdEmploye(), "ACTIF", Date.valueOf("2026-01-01"), null, c);
                creerAffectationTest(emp.getIdEmploye(), ct.getIdContrat(), 1, "SUSPENDU", Date.valueOf("2026-01-01"), null, c);

                Map<String, Object> grille = pointageSaisieService.getGrilleSaisie(1, "2026-08-15", c);
                @SuppressWarnings("unchecked")
                List<SaisiePointageJourDTO> liste = (List<SaisiePointageJourDTO>) grille.get("grilleData");

                boolean present = liste.stream().anyMatch(dto -> emp.getMatricule().equals(dto.getMatricule()));
                assertFalse(present, "L'employé avec affectation non active ne doit pas apparaître dans la grille");
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("Scénario 9a (Écriture Directe) : Saisie batch pour employé INACTIF -> REJET")
    void test09a_Pointage_EcritureDirecteEmployeInactif_DoitEchouer() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Employe emp = creerEmployeTest("EMP-TEST-PTG-WR01", c);
                emp.setEstActif(false);
                emp.update(c);

                SaisiePointageJourDTO dto = new SaisiePointageJourDTO();
                dto.setIdEmploye(emp.getIdEmploye());
                dto.setIdSite(1);
                dto.setDatePointage("2026-08-15");
                dto.setHeureEntreeMatin("08:00");
                dto.setHeureSortieAm("17:00");

                IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
                    pointageSaisieService.enregistrerPointagesBatch(Collections.singletonList(dto), c);
                });
                assertTrue(ex.getMessage().contains("éligible"), "Le message doit mentionner l'inéligibilité : " + ex.getMessage());
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("Scénario 9b (Écriture Directe) : Saisie batch sans contrat actif -> REJET")
    void test09b_Pointage_EcritureDirecteSansContratActif_DoitEchouer() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Employe emp = creerEmployeTest("EMP-TEST-PTG-WR02", c);

                SaisiePointageJourDTO dto = new SaisiePointageJourDTO();
                dto.setIdEmploye(emp.getIdEmploye());
                dto.setIdSite(1);
                dto.setDatePointage("2026-08-15");
                dto.setHeureEntreeMatin("08:00");
                dto.setHeureSortieAm("17:00");

                IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
                    pointageSaisieService.enregistrerPointagesBatch(Collections.singletonList(dto), c);
                });
                assertTrue(ex.getMessage().contains("éligible"), "Le message doit mentionner l'inéligibilité : " + ex.getMessage());
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("Scénario 9c (Écriture Directe) : Saisie batch avec affectation sur un AUTRE site -> REJET")
    void test09c_Pointage_EcritureDirecteMauvaisSite_DoitEchouer() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Employe emp = creerEmployeTest("EMP-TEST-PTG-WR03", c);
                ContratTravail ct = creerContratTest(emp.getIdEmploye(), "ACTIF", Date.valueOf("2026-01-01"), null, c);
                creerAffectationTest(emp.getIdEmploye(), ct.getIdContrat(), 1, "ACTIF", Date.valueOf("2026-01-01"), null, c);

                SaisiePointageJourDTO dto = new SaisiePointageJourDTO();
                dto.setIdEmploye(emp.getIdEmploye());
                dto.setIdSite(2); // Mauvais site
                dto.setDatePointage("2026-08-15");
                dto.setHeureEntreeMatin("08:00");
                dto.setHeureSortieAm("17:00");

                IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
                    pointageSaisieService.enregistrerPointagesBatch(Collections.singletonList(dto), c);
                });
                assertTrue(ex.getMessage().contains("éligible"), "Le message doit bloquer la saisie sur un mauvais site : " + ex.getMessage());
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("Scénario 9d (Écriture Directe) : Régularisation d'anomalie pour employé inéligible -> REJET")
    void test09d_Pointage_RegularisationAnomalieEmployeIneligible_DoitEchouer() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Employe emp = creerEmployeTest("EMP-TEST-PTG-WR04", c);
                emp.setEstActif(false);
                emp.update(c);

                Map<String, Object> payload = new HashMap<>();
                payload.put("idPointage", 0);
                payload.put("idEmploye", emp.getIdEmploye());
                payload.put("idSite", 1);
                payload.put("datePointage", "2026-08-15");
                payload.put("heureEntreeMatin", "08:00");
                payload.put("heureSortieAm", "17:00");

                IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
                    pointageAnomalieService.regulariserPointage(0, payload);
                });
                assertTrue(ex.getMessage().contains("éligible"), "La régularisation doit bloquer un employé inéligible : " + ex.getMessage());
            } finally {
                c.rollback();
            }
        }
    }

    // =========================================================================
    // GROUPE C : PÉRIODE DE PAIE VERROUILLÉE (P0-PTG-002) - Scénarios 10 à 14
    // =========================================================================

    @Test
    @DisplayName("Scénario 10 : Période BROUILLON / OUVERTE -> modification autorisée")
    void test10_Pointage_PeriodeOuverte_ModificationAutorisee() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Employe emp = creerEmployeTest("EMP-TEST-PTG-10", c);
                ContratTravail ct = creerContratTest(emp.getIdEmploye(), "ACTIF", Date.valueOf("2026-01-01"), null, c);
                creerAffectationTest(emp.getIdEmploye(), ct.getIdContrat(), 1, "ACTIF", Date.valueOf("2026-01-01"), null, c);

                int idPer = creerPeriodePaieTest("BROUILLON", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), c);

                SaisiePointageJourDTO dto = new SaisiePointageJourDTO();
                dto.setIdEmploye(emp.getIdEmploye());
                dto.setIdSite(1);
                dto.setIdPeriodePaie(idPer);
                dto.setDatePointage("2026-11-10");
                dto.setHeureEntreeMatin("08:00");
                dto.setHeureSortieMatin("12:00");
                dto.setHeureEntreeAm("13:00");
                dto.setHeureSortieAm("17:00");

                Map<String, Object> res = pointageSaisieService.enregistrerPointagesBatch(Collections.singletonList(dto), c);
                assertTrue(Boolean.TRUE.equals(res.get("success")), "La saisie sur période BROUILLON doit réussir");
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("Scénario 11 : Période EN_COURS -> modification autorisée")
    void test11_Pointage_PeriodeEnCours_ModificationAutorisee() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Employe emp = creerEmployeTest("EMP-TEST-PTG-11", c);
                ContratTravail ct = creerContratTest(emp.getIdEmploye(), "ACTIF", Date.valueOf("2026-01-01"), null, c);
                creerAffectationTest(emp.getIdEmploye(), ct.getIdContrat(), 1, "ACTIF", Date.valueOf("2026-01-01"), null, c);

                int idPer = creerPeriodePaieTest("EN_COURS", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31), c);

                SaisiePointageJourDTO dto = new SaisiePointageJourDTO();
                dto.setIdEmploye(emp.getIdEmploye());
                dto.setIdSite(1);
                dto.setIdPeriodePaie(idPer);
                dto.setDatePointage("2026-12-15");
                dto.setHeureEntreeMatin("08:00");
                dto.setHeureSortieMatin("12:00");
                dto.setHeureEntreeAm("13:00");
                dto.setHeureSortieAm("17:00");

                Map<String, Object> res = pointageSaisieService.enregistrerPointagesBatch(Collections.singletonList(dto), c);
                assertTrue(Boolean.TRUE.equals(res.get("success")), "La saisie sur période EN_COURS doit réussir");
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("Scénario 12 : Période VALIDEE -> modification refusée")
    void test12_Pointage_PeriodeValidee_ModificationRefusee() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Employe emp = creerEmployeTest("EMP-TEST-PTG-12", c);
                ContratTravail ct = creerContratTest(emp.getIdEmploye(), "ACTIF", Date.valueOf("2026-01-01"), null, c);
                creerAffectationTest(emp.getIdEmploye(), ct.getIdContrat(), 1, "ACTIF", Date.valueOf("2026-01-01"), null, c);

                int idPer = creerPeriodePaieTest("VALIDEE", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), c);

                SaisiePointageJourDTO dto = new SaisiePointageJourDTO();
                dto.setIdEmploye(emp.getIdEmploye());
                dto.setIdSite(1);
                dto.setIdPeriodePaie(idPer);
                dto.setDatePointage("2026-07-15");
                dto.setHeureEntreeMatin("08:00");
                dto.setHeureSortieMatin("17:00");

                IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
                    pointageSaisieService.enregistrerPointagesBatch(Collections.singletonList(dto), c);
                });
                assertTrue(ex.getMessage().contains("fermée"), "Le message doit mentionner la fermeture de la période : " + ex.getMessage());
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("Scénario 13 : Période CLOTUREE -> modification refusée")
    void test13_Pointage_PeriodeCloturee_ModificationRefusee() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Employe emp = creerEmployeTest("EMP-TEST-PTG-13", c);
                ContratTravail ct = creerContratTest(emp.getIdEmploye(), "ACTIF", Date.valueOf("2026-01-01"), null, c);
                creerAffectationTest(emp.getIdEmploye(), ct.getIdContrat(), 1, "ACTIF", Date.valueOf("2026-01-01"), null, c);

                int idPer = creerPeriodePaieTest("CLOTUREE", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), c);

                SaisiePointageJourDTO dto = new SaisiePointageJourDTO();
                dto.setIdEmploye(emp.getIdEmploye());
                dto.setIdSite(1);
                dto.setIdPeriodePaie(idPer);
                dto.setDatePointage("2026-05-15");
                dto.setHeureEntreeMatin("08:00");
                dto.setHeureSortieMatin("17:00");

                IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
                    pointageSaisieService.enregistrerPointagesBatch(Collections.singletonList(dto), c);
                });
                assertTrue(ex.getMessage().contains("fermée"), "Le message doit mentionner la fermeture : " + ex.getMessage());
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("Scénario 14 : Période TRANSMIS_DAF -> modification refusée")
    void test14_Pointage_PeriodeTransmisDaf_ModificationRefusee() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Employe emp = creerEmployeTest("EMP-TEST-PTG-14", c);
                ContratTravail ct = creerContratTest(emp.getIdEmploye(), "ACTIF", Date.valueOf("2026-01-01"), null, c);
                creerAffectationTest(emp.getIdEmploye(), ct.getIdContrat(), 1, "ACTIF", Date.valueOf("2026-01-01"), null, c);

                int idPer = creerPeriodePaieTest("TRANSMIS_DAF", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), c);

                SaisiePointageJourDTO dto = new SaisiePointageJourDTO();
                dto.setIdEmploye(emp.getIdEmploye());
                dto.setIdSite(1);
                dto.setIdPeriodePaie(idPer);
                dto.setDatePointage("2026-06-15");
                dto.setHeureEntreeMatin("08:00");
                dto.setHeureSortieMatin("17:00");

                IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
                    pointageSaisieService.enregistrerPointagesBatch(Collections.singletonList(dto), c);
                });
                assertTrue(ex.getMessage().contains("fermée"), "Le message doit mentionner la fermeture : " + ex.getMessage());
            } finally {
                c.rollback();
            }
        }
    }

    // =========================================================================
    // GROUPE D : CONCURRENCE SUR POINTAGES (P0-PTG-003)
    // =========================================================================

    @Test
    @DisplayName("Scénarios 15 à 18 : Concurrence optimiste & protection des données")
    void test15_a_18_Pointage_ConcurrenceOptimisteEtIntegrite() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Employe emp = creerEmployeTest("EMP-TEST-PTG-CNC", c);
                ContratTravail ct = creerContratTest(emp.getIdEmploye(), "ACTIF", Date.valueOf("2026-01-01"), null, c);
                creerAffectationTest(emp.getIdEmploye(), ct.getIdContrat(), 1, "ACTIF", Date.valueOf("2026-01-01"), null, c);
                int idPer = creerPeriodePaieTest("EN_COURS", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), c);

                // Initialisation : création du pointage (version initiale = 1)
                SaisiePointageJourDTO initial = new SaisiePointageJourDTO();
                initial.setIdEmploye(emp.getIdEmploye());
                initial.setIdSite(1);
                initial.setIdPeriodePaie(idPer);
                initial.setDatePointage("2026-08-20");
                initial.setHeureEntreeMatin("08:00");
                initial.setHeureSortieMatin("12:00");
                initial.setHeureEntreeAm("13:00");
                initial.setHeureSortieAm("17:00");
                pointageSaisieService.enregistrerPointagesBatch(Collections.singletonList(initial), c);

                // Vérification version initiale = 1
                int currentVersion = -1;
                try (PreparedStatement psV = c.prepareStatement("SELECT version FROM pointage_jour WHERE id_employe = ? AND date_pointage = ?")) {
                    psV.setInt(1, emp.getIdEmploye());
                    psV.setDate(2, Date.valueOf("2026-08-20"));
                    try (ResultSet rsV = psV.executeQuery()) {
                        if (rsV.next()) currentVersion = rsV.getInt(1);
                    }
                }
                assertEquals(1, currentVersion, "La version initiale doit être 1");

                // Utilisateur A prépare une mise à jour avec version = 1
                SaisiePointageJourDTO updateA = new SaisiePointageJourDTO();
                updateA.setIdEmploye(emp.getIdEmploye());
                updateA.setIdSite(1);
                updateA.setIdPeriodePaie(idPer);
                updateA.setDatePointage("2026-08-20");
                updateA.setVersion(1);
                updateA.setHeureEntreeMatin("08:15");
                updateA.setHeureSortieAm("17:30");
                updateA.setMotifAjustement("Modif Utilisateur A");

                // Utilisateur B prépare en parallèle une mise à jour avec version = 1
                SaisiePointageJourDTO updateB = new SaisiePointageJourDTO();
                updateB.setIdEmploye(emp.getIdEmploye());
                updateB.setIdSite(1);
                updateB.setIdPeriodePaie(idPer);
                updateB.setDatePointage("2026-08-20");
                updateB.setVersion(1);
                updateB.setHeureEntreeMatin("09:00");
                updateB.setHeureSortieAm("18:00");
                updateB.setMotifAjustement("Modif Utilisateur B");

                // Scénario 16 : Utilisateur A sauvegarde en premier -> SUCCESS (version passe à 2)
                Map<String, Object> resA = pointageSaisieService.enregistrerPointagesBatch(Collections.singletonList(updateA), c);
                assertTrue(Boolean.TRUE.equals(resA.get("success")));

                // Scénario 17 : Utilisateur B sauvegarde avec son ancienne version 1 -> FAIL (IllegalStateException / Conflict)
                IllegalStateException exB = assertThrows(IllegalStateException.class, () -> {
                    pointageSaisieService.enregistrerPointagesBatch(Collections.singletonList(updateB), c);
                });
                assertTrue(exB.getMessage().contains("concurrente") || exB.getMessage().contains("version"),
                        "L'erreur doit signaler le conflit de version concurrente : " + exB.getMessage());

                // Scénario 18 : Vérification de l'intégrité des données de l'Utilisateur A
                try (PreparedStatement psCheck = c.prepareStatement("SELECT heure_entree_matin, motif_ajustement, version FROM pointage_jour WHERE id_employe = ? AND date_pointage = ?")) {
                    psCheck.setInt(1, emp.getIdEmploye());
                    psCheck.setDate(2, Date.valueOf("2026-08-20"));
                    try (ResultSet rsCheck = psCheck.executeQuery()) {
                        assertTrue(rsCheck.next());
                        assertEquals("08:15:00", rsCheck.getTime("heure_entree_matin").toString());
                        assertEquals("Modif Utilisateur A", rsCheck.getString("motif_ajustement"));
                        assertEquals(2, rsCheck.getInt("version"), "La version en base doit être 2");
                    }
                }

                // Scénario 18b : Utilisateur B recharge (obtient version 2) et soumet à nouveau -> SUCCESS
                updateB.setVersion(2);
                Map<String, Object> resBRetry = pointageSaisieService.enregistrerPointagesBatch(Collections.singletonList(updateB), c);
                assertTrue(Boolean.TRUE.equals(resBRetry.get("success")), "La mise à jour avec la version rechargée doit réussir");
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("Scénario 18c : UPDATE d'un pointage existant avec version = null -> REJET")
    void test18c_Pointage_UpdateAvecVersionNull_DoitEchouer() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Employe emp = creerEmployeTest("EMP-TEST-PTG-VNULL", c);
                ContratTravail ct = creerContratTest(emp.getIdEmploye(), "ACTIF", Date.valueOf("2026-01-01"), null, c);
                creerAffectationTest(emp.getIdEmploye(), ct.getIdContrat(), 1, "ACTIF", Date.valueOf("2026-01-01"), null, c);
                int idPer = creerPeriodePaieTest("EN_COURS", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), c);

                // Initialisation
                SaisiePointageJourDTO initial = new SaisiePointageJourDTO();
                initial.setIdEmploye(emp.getIdEmploye());
                initial.setIdSite(1);
                initial.setIdPeriodePaie(idPer);
                initial.setDatePointage("2026-08-22");
                initial.setHeureEntreeMatin("08:00");
                initial.setHeureSortieAm("17:00");
                pointageSaisieService.enregistrerPointagesBatch(Collections.singletonList(initial), c);

                // Tentative d'UPDATE avec version = null
                SaisiePointageJourDTO updateNullVer = new SaisiePointageJourDTO();
                updateNullVer.setIdEmploye(emp.getIdEmploye());
                updateNullVer.setIdSite(1);
                updateNullVer.setIdPeriodePaie(idPer);
                updateNullVer.setDatePointage("2026-08-22");
                updateNullVer.setVersion(null); // Explicit null
                updateNullVer.setHeureEntreeMatin("08:30");
                updateNullVer.setHeureSortieAm("17:30");

                IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
                    pointageSaisieService.enregistrerPointagesBatch(Collections.singletonList(updateNullVer), c);
                });
                assertTrue(ex.getMessage().contains("version") && ex.getMessage().contains("obligatoire"),
                        "Le message doit exiger le champ version : " + ex.getMessage());
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("Scénario 18d (Concurrence Réelle Multi-threads) : 2 threads UPDATE simultanés avec version 1 -> exactement 1 succès")
    void test18d_Pointage_UpdateConcurrentMultiThreads_ExactementUnSucces() throws Exception {
        int idEmp;
        int idPer;
        try (Connection c = dataSource.getConnection()) {
            Employe emp = creerEmployeTest("EMP-TEST-PTG-MT", c);
            idEmp = emp.getIdEmploye();
            ContratTravail ct = creerContratTest(idEmp, "ACTIF", Date.valueOf("2026-01-01"), null, c);
            creerAffectationTest(idEmp, ct.getIdContrat(), 1, "ACTIF", Date.valueOf("2026-01-01"), null, c);
            idPer = creerPeriodePaieTest("EN_COURS", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), c);

            SaisiePointageJourDTO init = new SaisiePointageJourDTO();
            init.setIdEmploye(idEmp);
            init.setIdSite(1);
            init.setIdPeriodePaie(idPer);
            init.setDatePointage("2026-08-25");
            init.setHeureEntreeMatin("08:00");
            init.setHeureSortieAm("17:00");
            pointageSaisieService.enregistrerPointagesBatch(Collections.singletonList(init), c);
        }

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        Callable<Void> task1 = () -> {
            startGate.await();
            try (Connection c = dataSource.getConnection()) {
                c.setAutoCommit(false);
                try {
                    SaisiePointageJourDTO dto = new SaisiePointageJourDTO();
                    dto.setIdEmploye(idEmp);
                    dto.setIdSite(1);
                    dto.setIdPeriodePaie(idPer);
                    dto.setDatePointage("2026-08-25");
                    dto.setVersion(1);
                    dto.setHeureEntreeMatin("08:15");
                    dto.setHeureSortieAm("17:15");
                    dto.setMotifAjustement("Thread 1");
                    pointageSaisieService.enregistrerPointagesBatch(Collections.singletonList(dto), c);
                    c.commit();
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    c.rollback();
                    conflictCount.incrementAndGet();
                }
            }
            return null;
        };

        Callable<Void> task2 = () -> {
            startGate.await();
            try (Connection c = dataSource.getConnection()) {
                c.setAutoCommit(false);
                try {
                    SaisiePointageJourDTO dto = new SaisiePointageJourDTO();
                    dto.setIdEmploye(idEmp);
                    dto.setIdSite(1);
                    dto.setIdPeriodePaie(idPer);
                    dto.setDatePointage("2026-08-25");
                    dto.setVersion(1);
                    dto.setHeureEntreeMatin("08:30");
                    dto.setHeureSortieAm("17:30");
                    dto.setMotifAjustement("Thread 2");
                    pointageSaisieService.enregistrerPointagesBatch(Collections.singletonList(dto), c);
                    c.commit();
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    c.rollback();
                    conflictCount.incrementAndGet();
                }
            }
            return null;
        };

        Future<Void> f1 = executor.submit(task1);
        Future<Void> f2 = executor.submit(task2);

        startGate.countDown();
        f1.get();
        f2.get();
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactement une écriture concurrente doit réussir");
        assertEquals(1, conflictCount.get(), "L'autre écriture concurrente avec version obsolète doit recevoir un conflit");

        try (Connection c = dataSource.getConnection()) {
            try (Statement st = c.createStatement()) {
                st.execute("DELETE FROM pointage_jour WHERE id_employe = " + idEmp);
                st.execute("DELETE FROM employe_affectation_carriere WHERE id_employe = " + idEmp);
                st.execute("DELETE FROM contrat_travail WHERE id_employe = " + idEmp);
                st.execute("DELETE FROM employe WHERE id_employe = " + idEmp);
            }
        }
    }

    // =========================================================================
    // GROUPE E : DÉSACTIVATION / RÉACTIVATION EMPLOYÉ (P0-EMP-001)
    // =========================================================================

    @Test
    @DisplayName("Scénario 19 : Désactivation directe d'un employé avec contrat actif -> FAIL")
    void test19_Employe_DesactivationDirecteAvecContratActif_DoitEchouer() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Employe emp = creerEmployeTest("EMP-TEST-EMP-19", c);
                ContratTravail ct = creerContratTest(emp.getIdEmploye(), "ACTIF", Date.valueOf("2026-01-01"), null, c);
                creerAffectationTest(emp.getIdEmploye(), ct.getIdContrat(), 1, "ACTIF", Date.valueOf("2026-01-01"), null, c);

                IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
                    emp.desactiver("Démission", c);
                });
                assertTrue(ex.getMessage().contains("ACTIF") || ex.getMessage().contains("workflow"),
                        "Le message doit refuser la désactivation directe avec contrat actif : " + ex.getMessage());
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("Scénario 20 : Réactivation directe d'un employé sans contrat actif -> FAIL")
    void test20_Employe_ReactivationDirecteSansContratActif_DoitEchouer() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Employe emp = creerEmployeTest("EMP-TEST-EMP-20", c);
                emp.setEstActif(false);
                emp.update(c);

                IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
                    emp.reactiver(c);
                });
                assertTrue(ex.getMessage().contains("sans contrat") || ex.getMessage().contains("workflow") || ex.getMessage().contains("ACTIF"),
                        "Le message doit refuser la réactivation directe sans contrat actif : " + ex.getMessage());
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("Scénario 20b : Réactivation directe avec contrat ACTIF mais affectation FERMÉE -> FAIL")
    void test20b_Employe_ReactivationDirecteContratActifAffectationFermee_DoitEchouer() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Employe emp = creerEmployeTest("EMP-TEST-EMP-20B", c);
                ContratTravail ct = creerContratTest(emp.getIdEmploye(), "ACTIF", Date.valueOf("2026-01-01"), null, c);
                // Affectation clôturée / fermée
                creerAffectationTest(emp.getIdEmploye(), ct.getIdContrat(), 1, "CLOTURE", Date.valueOf("2026-01-01"), Date.valueOf("2026-06-30"), c);
                emp.setEstActif(false);
                emp.update(c);

                IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
                    emp.reactiver(c);
                });
                assertTrue(ex.getMessage().toLowerCase().contains("affectation"),
                        "La réactivation doit être refusée si l'affectation est inactive : " + ex.getMessage());
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("Scénario 20c : Réactivation directe avec contrat ACTIF ET affectation ACTIVE -> SUCCESS")
    void test20c_Employe_ReactivationDirecteContratEtAffectationActifs_DoitReussir() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Employe emp = creerEmployeTest("EMP-TEST-EMP-20C", c);
                ContratTravail ct = creerContratTest(emp.getIdEmploye(), "ACTIF", Date.valueOf("2026-01-01"), null, c);
                creerAffectationTest(emp.getIdEmploye(), ct.getIdContrat(), 1, "ACTIF", Date.valueOf("2026-01-01"), null, c);
                emp.setEstActif(false);
                emp.update(c);

                emp.reactiver(c);
                assertTrue(emp.isEstActif(), "L'employé avec contrat et affectation valides doit être réactivé avec succès");
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("Scénario 21 : Sortie régulière via le Workflow RH unifié -> SUCCESS")
    void test21_Employe_SortieViaWorkflowRh_DoitReussir() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Employe emp = creerEmployeTest("EMP-TEST-EMP-21", c);
                ContratTravail ct = creerContratTest(emp.getIdEmploye(), "ACTIF", Date.valueOf("2026-01-01"), null, c);
                EmployeAffectationCarriere aff = creerAffectationTest(emp.getIdEmploye(), ct.getIdContrat(), 1, "ACTIF", Date.valueOf("2026-01-01"), null, c);

                TransitionCarriereRequest req = new TransitionCarriereRequest();
                req.setCodeTypeEvenement("MUTATION");
                req.setIdContratSource(ct.getIdContrat());
                req.setIdAffectationSource(aff.getIdAffectation());
                req.setIdSite(1);
                req.setIdDepartement(aff.getIdDepartement());
                req.setIdService(aff.getIdService());
                req.setIdFonction(aff.getIdFonction());
                req.setIdCategorieProfessionnelle(aff.getIdCategorieProfessionnelle());
                req.setIdModeCalculPaie(aff.getIdModeCalculPaie());
                req.setSalaireBase(new BigDecimal("1800000.00"));
                req.setDateEffet(Date.valueOf("2026-09-01"));
                req.setMotif("Mutation avec promotion");

                WorkflowRhCPL wf = workflowRhService.creerBrouillonTransition(emp.getIdEmploye(), req, 1, c);
                assertNotNull(wf, "Le workflow brouillon doit être créé");

                ValiderWorkflowRequest vreq = new ValiderWorkflowRequest();
                vreq.setCommentaireValidation("Validé par la direction RH");

                WorkflowRhCPL wfValide = workflowRhService.validerWorkflow(wf.getIdWorkflow(), vreq, 1, c);
                assertNotNull(wfValide, "Le workflow validé ne doit pas être null");
                assertEquals("ACTIF", wfValide.getCodeStatutWorkflow());
            } finally {
                c.rollback();
            }
        }
    }
}
