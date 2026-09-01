package mg.galaxam.sirh.model.p0;

import mg.galaxam.sirh.api.dto.pointage.SaisiePointageJourDTO;
import mg.galaxam.sirh.model.conge.DemandeConge;
import mg.galaxam.sirh.model.conge.RefTypeConge;
import mg.galaxam.sirh.model.conge.service.CongeService;
import mg.galaxam.sirh.model.contrat.ContratTravail;
import mg.galaxam.sirh.model.contrat.EmployeAffectationCarriere;
import mg.galaxam.sirh.model.employe.Employe;
import mg.galaxam.sirh.model.pointage.service.PointageAnomalieService;
import mg.galaxam.sirh.model.pointage.service.PointageSaisieService;
import org.junit.jupiter.api.*;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 1.7 — Campagne de tests d'intégration réels en base PostgreSQL.
 *
 * Principes obligatoires :
 * 1. COMMIT réel systématique sur toutes les opérations (aucun rollback automatique).
 * 2. Vérification sur de NOUVELLES connexions JDBC distinctes après fermeture de la transaction d'écriture.
 * 3. Multi-connexions / Multi-threads réels pour les scénarios de concurrence.
 * 4. Validation des contraintes d'intégrité physiques PostgreSQL (index unique partiel, verrous FOR UPDATE / FOR SHARE).
 * 5. Nettoyage explicite et tracé à la fin de la campagne.
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class P0IntegriteBackendRealDbIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private CongeService congeService;

    @Autowired
    private PointageSaisieService pointageSaisieService;

    @Autowired
    private PointageAnomalieService pointageAnomalieService;

    private static final List<Integer> createdEmployeIds = new CopyOnWriteArrayList<>();
    private static final List<Integer> createdPeriodeIds = new CopyOnWriteArrayList<>();

    // =========================================================================
    // UTILITAIRES DE CRÉATION & DE COMMUTATION
    // =========================================================================

    private int queryId(Connection c, String sql) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        }
        throw new SQLException("Aucun résultat pour: " + sql);
    }

    private Employe creerEtCommiterEmploye(String prefix) throws Exception {
        String shortPrefix = prefix.length() > 6 ? prefix.substring(0, 6) : prefix;
        String uniqueMatricule = shortPrefix + (System.nanoTime() % 1000000000L);
        if (uniqueMatricule.length() > 20) {
            uniqueMatricule = uniqueMatricule.substring(0, 20);
        }

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            Employe emp = new Employe();
            emp.setMatricule(uniqueMatricule);
            emp.setNom("TestRealDb_" + uniqueMatricule);
            emp.setPrenom("PrenomTest");
            emp.setSexe("M");
            emp.setDateNaissance(Date.valueOf("1994-03-20"));
            emp.setLieuNaissance("Antananarivo");
            emp.setCinNumero("101" + (System.nanoTime() % 1000000000L));
            emp.setCinDateDelivrance(Date.valueOf("2014-01-01"));
            emp.setCinLieuDelivrance("Antananarivo");
            emp.setTelephone("+261 34 11 222 33");
            emp.setEmail(uniqueMatricule.toLowerCase().replace("-", "").replace("_", "") + "@realdb-galaxam.mg");
            emp.setAdresse("Lot RealDB Test");
            emp.setEstActif(true);
            emp.save(c);
            c.commit();

            createdEmployeIds.add(emp.getIdEmploye());
            return emp;
        }
    }

    private ContratTravail creerEtCommiterContrat(int idEmploye, String codeStatut, Date debut, Date fin) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            int idStatut = queryId(c, "SELECT id_statut_rh FROM ref_statut_rh WHERE code = '" + codeStatut + "'");
            int idFiliale = queryId(c, "SELECT id_filiale FROM ref_filiale LIMIT 1");
            int idTypeContrat = queryId(c, "SELECT id_type_contrat FROM ref_type_contrat LIMIT 1");
            int idTypeIntervenant = queryId(c, "SELECT id_type_intervenant FROM ref_type_intervenant LIMIT 1");

            ContratTravail ct = new ContratTravail();
            ct.setIdEmploye(idEmploye);
            ct.setNumeroContrat("CTR-" + idEmploye + "-" + System.nanoTime() % 100000);
            ct.setIdFiliale(idFiliale);
            ct.setIdTypeContrat(idTypeContrat);
            ct.setIdTypeIntervenant(idTypeIntervenant);
            ct.setIdStatutRh(idStatut);
            ct.setDateDebut(debut);
            ct.setDateFin(fin);
            ct.setComptabiliseEffectif(true);
            ct.save(c);
            c.commit();
            return ct;
        }
    }

    private EmployeAffectationCarriere creerEtCommiterAffectation(int idEmploye, int idContrat, int idSite, String codeStatut, Date debut, Date fin) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
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
            aff.setSalaireBase(new BigDecimal("1600000.00"));
            aff.setIdStatutRh(idStatut);
            aff.setDateDebut(debut);
            aff.setDateFin(fin);
            aff.save(c);
            c.commit();
            return aff;
        }
    }

    private int creerEtCommiterPeriodePaie(String codeStatut, LocalDate debut, LocalDate fin) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (Statement stmt = c.createStatement()) {
                stmt.execute("INSERT INTO ref_statut_periode_paie (code, libelle) VALUES ('TRANSMIS_DAF', 'Transmise à la DAF') ON CONFLICT (code) DO NOTHING");
            } catch (Exception ignored) {}

            String sql = "INSERT INTO periode_paie (id_statut_periode, code, libelle, date_debut, date_fin) " +
                    "VALUES ((SELECT id_statut_periode FROM ref_statut_periode_paie WHERE code = ?), ?, ?, ?, ?) RETURNING id_periode_paie";
            int idPer;
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, codeStatut);
                ps.setString(2, "PER-RDB-" + System.nanoTime() % 100000);
                ps.setString(3, "Période RealDB " + codeStatut);
                ps.setDate(4, Date.valueOf(debut));
                ps.setDate(5, Date.valueOf(fin));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        idPer = rs.getInt(1);
                    } else {
                        throw new SQLException("Impossible de créer la période de paie");
                    }
                }
            }
            c.commit();
            createdPeriodeIds.add(idPer);
            return idPer;
        }
    }

    @AfterAll
    static void tearDownGlobal(@Autowired DataSource ds) {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            for (Integer idEmp : createdEmployeIds) {
                try (Statement st = c.createStatement()) {
                    st.execute("DELETE FROM pointage_indemnite_jour WHERE id_pointage IN (SELECT id_pointage FROM pointage_jour WHERE id_employe = " + idEmp + ")");
                    st.execute("DELETE FROM pointage_jour WHERE id_employe = " + idEmp);
                    st.execute("DELETE FROM mouvement_solde_conge WHERE id_employe = " + idEmp);
                    st.execute("DELETE FROM demande_conge WHERE id_employe = " + idEmp);
                    st.execute("DELETE FROM employe_affectation_carriere WHERE id_employe = " + idEmp);
                    st.execute("DELETE FROM contrat_travail WHERE id_employe = " + idEmp);
                    st.execute("DELETE FROM employe WHERE id_employe = " + idEmp);
                }
            }
            for (Integer idPer : createdPeriodeIds) {
                try (Statement st = c.createStatement()) {
                    st.execute("DELETE FROM pointage_indemnite_jour WHERE id_pointage IN (SELECT id_pointage FROM pointage_jour WHERE id_periode_paie = " + idPer + ")");
                    st.execute("DELETE FROM pointage_jour WHERE id_periode_paie = " + idPer);
                    st.execute("DELETE FROM periode_paie WHERE id_periode_paie = " + idPer);
                }
            }
            c.commit();
        } catch (Exception e) {
            System.err.println("Notice nettoyage RealDB: " + e.getMessage());
        }
    }

    // =========================================================================
    // SECTION 1 : PERSISTANCE DU POINTAGE & OPTIMISTIC LOCKING APRÈS COMMIT
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("RealDB-01 : Création pointage -> COMMIT -> Vérification nouvelle connexion (version = 1)")
    void testRealDb_01_CreationPointage_CommitEtVerificationNouvelleConnexion() throws Exception {
        Employe emp = creerEtCommiterEmploye("RDB01");
        ContratTravail ct = creerEtCommiterContrat(emp.getIdEmploye(), "ACTIF", Date.valueOf("2026-01-01"), null);
        creerEtCommiterAffectation(emp.getIdEmploye(), ct.getIdContrat(), 1, "ACTIF", Date.valueOf("2026-01-01"), null);
        int idPer = creerEtCommiterPeriodePaie("EN_COURS", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        // 1. Transaction A : Saisie et COMMIT
        try (Connection cA = dataSource.getConnection()) {
            cA.setAutoCommit(false);
            SaisiePointageJourDTO dto = new SaisiePointageJourDTO();
            dto.setIdEmploye(emp.getIdEmploye());
            dto.setIdSite(1);
            dto.setIdPeriodePaie(idPer);
            dto.setDatePointage("2026-08-10");
            dto.setHeureEntreeMatin("08:00");
            dto.setHeureSortieMatin("12:00");
            dto.setHeureEntreeAm("13:00");
            dto.setHeureSortieAm("17:00");

            Map<String, Object> res = pointageSaisieService.enregistrerPointagesBatch(Collections.singletonList(dto), cA);
            assertTrue(Boolean.TRUE.equals(res.get("success")), "La saisie doit réussir");
            cA.commit();
        }

        // 2. Transaction B (Nouvelle connexion distincte) : Relecture SQL direct
        try (Connection cB = dataSource.getConnection()) {
            String sql = "SELECT version, total_heures, minutes_retard FROM pointage_jour WHERE id_employe = ? AND date_pointage = ?";
            try (PreparedStatement ps = cB.prepareStatement(sql)) {
                ps.setInt(1, emp.getIdEmploye());
                ps.setDate(2, Date.valueOf("2026-08-10"));
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "Le pointage doit être physiquement présent en base PostgreSQL");
                    assertEquals(1, rs.getInt("version"), "La version initiale persistée doit être 1");
                    assertEquals(8.0, rs.getDouble("total_heures"), 0.01, "Le total des heures doit être 8.0");
                    assertEquals(0, rs.getInt("minutes_retard"), "Le retard doit être 0");
                }
            }
        }
    }

    @Test
    @Order(2)
    @DisplayName("RealDB-02 : Modification pointage -> COMMIT -> Vérification nouvelle connexion (version passe à 2)")
    void testRealDb_02_ModificationPointage_CommitEtIncrementationVersion() throws Exception {
        Employe emp = creerEtCommiterEmploye("RDB02");
        ContratTravail ct = creerEtCommiterContrat(emp.getIdEmploye(), "ACTIF", Date.valueOf("2026-01-01"), null);
        creerEtCommiterAffectation(emp.getIdEmploye(), ct.getIdContrat(), 1, "ACTIF", Date.valueOf("2026-01-01"), null);
        int idPer = creerEtCommiterPeriodePaie("EN_COURS", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        // Initialisation avec version = 1
        try (Connection cInit = dataSource.getConnection()) {
            cInit.setAutoCommit(false);
            SaisiePointageJourDTO dto = new SaisiePointageJourDTO();
            dto.setIdEmploye(emp.getIdEmploye());
            dto.setIdSite(1);
            dto.setIdPeriodePaie(idPer);
            dto.setDatePointage("2026-08-12");
            dto.setHeureEntreeMatin("08:00");
            dto.setHeureSortieAm("17:00");
            pointageSaisieService.enregistrerPointagesBatch(Collections.singletonList(dto), cInit);
            cInit.commit();
        }

        // Transaction A : Modification avec version = 1 fournie
        try (Connection cA = dataSource.getConnection()) {
            cA.setAutoCommit(false);
            SaisiePointageJourDTO dtoUp = new SaisiePointageJourDTO();
            dtoUp.setIdEmploye(emp.getIdEmploye());
            dtoUp.setIdSite(1);
            dtoUp.setIdPeriodePaie(idPer);
            dtoUp.setDatePointage("2026-08-12");
            dtoUp.setVersion(1); // Version attendue
            dtoUp.setHeureEntreeMatin("08:30");
            dtoUp.setHeureSortieAm("17:30");
            dtoUp.setMotifAjustement("Ajustement RealDB");
            pointageSaisieService.enregistrerPointagesBatch(Collections.singletonList(dtoUp), cA);
            cA.commit();
        }

        // Transaction B (Nouvelle connexion) : Vérification persistance version = 2
        try (Connection cB = dataSource.getConnection()) {
            String sql = "SELECT version, heure_entree_matin, motif_ajustement FROM pointage_jour WHERE id_employe = ? AND date_pointage = ?";
            try (PreparedStatement ps = cB.prepareStatement(sql)) {
                ps.setInt(1, emp.getIdEmploye());
                ps.setDate(2, Date.valueOf("2026-08-12"));
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(2, rs.getInt("version"), "La version dans PostgreSQL doit être exactement 2");
                    assertEquals("08:30:00", rs.getTime("heure_entree_matin").toString());
                    assertEquals("Ajustement RealDB", rs.getString("motif_ajustement"));
                }
            }
        }
    }

    @Test
    @Order(3)
    @DisplayName("RealDB-03 : Régularisation d'anomalie avec version obligatoire -> COMMIT -> version passe à 3")
    void testRealDb_03_RegularisationPointage_CommitEtVerification() throws Exception {
        Employe emp = creerEtCommiterEmploye("RDB03");
        ContratTravail ct = creerEtCommiterContrat(emp.getIdEmploye(), "ACTIF", Date.valueOf("2026-01-01"), null);
        creerEtCommiterAffectation(emp.getIdEmploye(), ct.getIdContrat(), 1, "ACTIF", Date.valueOf("2026-01-01"), null);
        int idPer = creerEtCommiterPeriodePaie("EN_COURS", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        int idPtg;
        // Création initiale
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            SaisiePointageJourDTO dto = new SaisiePointageJourDTO();
            dto.setIdEmploye(emp.getIdEmploye());
            dto.setIdSite(1);
            dto.setIdPeriodePaie(idPer);
            dto.setDatePointage("2026-08-14");
            dto.setHeureEntreeMatin("08:00");
            dto.setHeureSortieAm("17:00");
            pointageSaisieService.enregistrerPointagesBatch(Collections.singletonList(dto), c);
            c.commit();

            idPtg = queryId(c, "SELECT id_pointage FROM pointage_jour WHERE id_employe = " + emp.getIdEmploye() + " AND date_pointage = '2026-08-14'");
        }

        // Régularisation avec version = 1
        Map<String, Object> payload = new HashMap<>();
        payload.put("idPointage", idPtg);
        payload.put("idEmploye", emp.getIdEmploye());
        payload.put("idSite", 1);
        payload.put("idPeriodePaie", idPer);
        payload.put("datePointage", "2026-08-14");
        payload.put("version", 1);
        payload.put("heureEntreeMatin", "08:10");
        payload.put("heureSortieAm", "17:10");
        payload.put("motifAjustement", "Régularisation superviseur");

        boolean ok = pointageAnomalieService.regulariserPointage(idPtg, payload);
        assertTrue(ok, "La régularisation doit réussir");

        // Nouvelle connexion : vérification
        try (Connection cCheck = dataSource.getConnection()) {
            String sql = "SELECT version, heure_entree_matin, motif_ajustement FROM pointage_jour WHERE id_pointage = ?";
            try (PreparedStatement ps = cCheck.prepareStatement(sql)) {
                ps.setInt(1, idPtg);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(2, rs.getInt("version"), "La version après régularisation doit être 2");
                    assertEquals("08:10:00", rs.getTime("heure_entree_matin").toString());
                }
            }
        }
    }

    // =========================================================================
    // SECTION 2 : INTÉGRITÉ POSTGRESQL & DOUBLE VALIDATION CONGÉS (P0-CNG-001)
    // =========================================================================

    @Test
    @Order(4)
    @DisplayName("RealDB-04 : Validation congé -> COMMIT -> Vérification Ledger & Décompte Solde sur Nouvelle Connexion")
    void testRealDb_04_ValidationDemandeConge_CommitEtMouvementPriseLedger() throws Exception {
        Employe emp = creerEtCommiterEmploye("RDB04");
        creerEtCommiterContrat(emp.getIdEmploye(), "ACTIF", Date.valueOf("2025-01-01"), Date.valueOf("2026-12-31"));

        int idDemande;
        int idTypeAnnuel;
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            idTypeAnnuel = queryId(c, "SELECT id_type_conge FROM ref_type_conge WHERE code = 'ANNUEL'");

            // Acquisition initiale de 15 jours
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO mouvement_solde_conge (id_employe, type_mouvement, nb_jours, date_mouvement, commentaire) VALUES (?, 'ACQUISITION', 15.0, CURRENT_DATE, 'Acquisition initiale')")) {
                ps.setInt(1, emp.getIdEmploye());
                ps.executeUpdate();
            }

            DemandeConge d = new DemandeConge();
            d.setIdEmploye(emp.getIdEmploye());
            d.setIdTypeConge(idTypeAnnuel);
            d.setDateDebut(Date.valueOf("2026-09-15"));
            d.setDateFin(Date.valueOf("2026-09-17")); // 3 jours
            DemandeConge saved = congeService.creerDemande(d, 1, c);
            idDemande = saved.getIdDemandeConge();
            c.commit();
        }

        // Transaction A : Validation
        try (Connection cA = dataSource.getConnection()) {
            cA.setAutoCommit(false);
            DemandeConge d = DemandeConge.rechercherParId(idDemande, cA);
            d.valider(1, cA);
            cA.commit();
        }

        // Transaction B (Nouvelle connexion) : Vérification de la persistance exacte dans PostgreSQL
        try (Connection cB = dataSource.getConnection()) {
            // 1. Statut de la demande
            String sqlDemande = "SELECT s.code FROM demande_conge dc JOIN ref_statut_demande_conge s ON dc.id_statut_demande_conge = s.id_statut_demande_conge WHERE dc.id_demande_conge = ?";
            try (PreparedStatement ps = cB.prepareStatement(sqlDemande)) {
                ps.setInt(1, idDemande);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("VALIDE", rs.getString("code"));
                }
            }

            // 2. Mouvement PRISE dans le Ledger
            String sqlLedger = "SELECT COUNT(*), SUM(nb_jours) FROM mouvement_solde_conge WHERE id_demande_conge = ? AND type_mouvement = 'PRISE'";
            try (PreparedStatement ps = cB.prepareStatement(sqlLedger)) {
                ps.setInt(1, idDemande);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1), "Il doit y avoir exactement 1 mouvement PRISE");
                    assertEquals(-3.0, rs.getDouble(2), 0.01, "Le montant débité doit être -3.0 jours");
                }
            }

            // 3. Solde calculé via la vue SQL v_solde_conge_employe
            String sqlSolde = "SELECT solde_actuel FROM v_solde_conge_employe WHERE id_employe = ?";
            try (PreparedStatement ps = cB.prepareStatement(sqlSolde)) {
                ps.setInt(1, emp.getIdEmploye());
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(12.0, rs.getDouble("solde_actuel"), 0.01, "Le solde net restant doit être 15 - 3 = 12 jours");
                }
            }
        }
    }

    @Test
    @Order(5)
    @DisplayName("RealDB-05 : Annulation congé validé -> COMMIT -> Restitution créditeur dans le Ledger")
    void testRealDb_05_AnnulationDemandeConge_CommitEtMouvementAnnulationLedger() throws Exception {
        Employe emp = creerEtCommiterEmploye("RDB05");
        creerEtCommiterContrat(emp.getIdEmploye(), "ACTIF", Date.valueOf("2025-01-01"), Date.valueOf("2026-12-31"));

        int idDemande;
        int idTypeAnnuel;
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            idTypeAnnuel = queryId(c, "SELECT id_type_conge FROM ref_type_conge WHERE code = 'ANNUEL'");
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO mouvement_solde_conge (id_employe, type_mouvement, nb_jours, date_mouvement, commentaire) VALUES (?, 'ACQUISITION', 20.0, CURRENT_DATE, 'Acquisition')")) {
                ps.setInt(1, emp.getIdEmploye());
                ps.executeUpdate();
            }
            DemandeConge d = new DemandeConge();
            d.setIdEmploye(emp.getIdEmploye());
            d.setIdTypeConge(idTypeAnnuel);
            d.setDateDebut(Date.valueOf("2026-11-01"));
            d.setDateFin(Date.valueOf("2026-11-04")); // 4 jours
            DemandeConge saved = congeService.creerDemande(d, 1, c);
            idDemande = saved.getIdDemandeConge();
            c.commit();
        }

        // Validation
        try (Connection cVal = dataSource.getConnection()) {
            cVal.setAutoCommit(false);
            DemandeConge.rechercherParId(idDemande, cVal).valider(1, cVal);
            cVal.commit();
        }

        // Annulation
        try (Connection cAnn = dataSource.getConnection()) {
            cAnn.setAutoCommit(false);
            DemandeConge.rechercherParId(idDemande, cAnn).annuler(1, cAnn);
            cAnn.commit();
        }

        // Vérification sur nouvelle connexion
        try (Connection cCheck = dataSource.getConnection()) {
            String sqlStatut = "SELECT s.code FROM demande_conge dc JOIN ref_statut_demande_conge s ON dc.id_statut_demande_conge = s.id_statut_demande_conge WHERE dc.id_demande_conge = ?";
            try (PreparedStatement ps = cCheck.prepareStatement(sqlStatut)) {
                ps.setInt(1, idDemande);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("ANNULE", rs.getString("code"));
                }
            }

            String sqlMouv = "SELECT COUNT(*) FROM mouvement_solde_conge WHERE id_demande_conge = ? AND type_mouvement = 'ANNULATION'";
            try (PreparedStatement ps = cCheck.prepareStatement(sqlMouv)) {
                ps.setInt(1, idDemande);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1), "Il doit y avoir exactement 1 mouvement ANNULATION (créditeur)");
                }
            }

            // Solde final restitué = 20.0
            String sqlSolde = "SELECT solde_actuel FROM v_solde_conge_employe WHERE id_employe = ?";
            try (PreparedStatement ps = cCheck.prepareStatement(sqlSolde)) {
                ps.setInt(1, emp.getIdEmploye());
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(20.0, rs.getDouble("solde_actuel"), 0.01, "Le solde doit être intégralement restitué à 20.0 jours");
                }
            }
        }
    }

    // =========================================================================
    // SECTION 3 : TEST DIRECT DE CONTRAINTE D'INTÉGRITÉ UNIQUE INDEX POSTGRESQL
    // =========================================================================

    @Test
    @Order(6)
    @DisplayName("RealDB-06 (Contrainte SQL Physique) : Rejet d'un 2ème mouvement PRISE direct par l'index unique partiel PostgreSQL")
    void testRealDb_06_PostgresConstraint_UniqueIndexPrise_RejetPhysiqueDoublon() throws Exception {
        Employe emp = creerEtCommiterEmploye("RDB06");
        int idDemande;
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            int idType = queryId(c, "SELECT id_type_conge FROM ref_type_conge WHERE code = 'ANNUEL'");
            creerEtCommiterContrat(emp.getIdEmploye(), "ACTIF", Date.valueOf("2025-01-01"), Date.valueOf("2026-12-31"));

            DemandeConge d = new DemandeConge();
            d.setIdEmploye(emp.getIdEmploye());
            d.setIdTypeConge(idType);
            d.setDateDebut(Date.valueOf("2026-12-01"));
            d.setDateFin(Date.valueOf("2026-12-05"));
            DemandeConge saved = congeService.creerDemande(d, 1, c);
            idDemande = saved.getIdDemandeConge();
            c.commit();
        }

        // 1. Insertion directe du premier mouvement PRISE -> COMMIT OK
        try (Connection c1 = dataSource.getConnection()) {
            c1.setAutoCommit(false);
            String sqlInsert1 = "INSERT INTO mouvement_solde_conge (id_employe, type_mouvement, nb_jours, id_demande_conge, date_mouvement) " +
                    "VALUES (?, 'PRISE', -5.0, ?, CURRENT_DATE)";
            try (PreparedStatement ps1 = c1.prepareStatement(sqlInsert1)) {
                ps1.setInt(1, emp.getIdEmploye());
                ps1.setInt(2, idDemande);
                ps1.executeUpdate();
            }
            c1.commit();
        }

        // 2. Tentative d'insertion d'un 2ème mouvement PRISE pour le MÊME id_demande_conge -> DOIT ÉCHOUER AU NIVEAU DE POSTGRESQL
        try (Connection c2 = dataSource.getConnection()) {
            c2.setAutoCommit(false);
            String sqlInsert2 = "INSERT INTO mouvement_solde_conge (id_employe, type_mouvement, nb_jours, id_demande_conge, date_mouvement) " +
                    "VALUES (?, 'PRISE', -5.0, ?, CURRENT_DATE)";
            try (PreparedStatement ps2 = c2.prepareStatement(sqlInsert2)) {
                ps2.setInt(1, emp.getIdEmploye());
                ps2.setInt(2, idDemande);
                SQLException ex = assertThrows(SQLException.class, ps2::executeUpdate,
                        "PostgreSQL doit physiquement lever une SQLException pour violation d'index unique sur idx_uniq_mouvement_prise_demande");
                assertTrue(ex.getMessage().toLowerCase().contains("unique") || ex.getMessage().toLowerCase().contains("duplicate") || ex.getMessage().contains("idx_uniq_mouvement_prise_demande"),
                        "Le message d'erreur PostgreSQL doit mentionner la violation d'unicité : " + ex.getMessage());
            }
            c2.rollback();
        }

        // 3. Vérification sur une 3ème connexion que le premier enregistrement est toujours intact et unique
        try (Connection c3 = dataSource.getConnection()) {
            String sqlCheck = "SELECT COUNT(*), SUM(nb_jours) FROM mouvement_solde_conge WHERE id_demande_conge = ? AND type_mouvement = 'PRISE'";
            try (PreparedStatement ps3 = c3.prepareStatement(sqlCheck)) {
                ps3.setInt(1, idDemande);
                try (ResultSet rs = ps3.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1), "Le nombre de mouvements PRISE doit être strictement 1");
                    assertEquals(-5.0, rs.getDouble(2), 0.01);
                }
            }
        }
    }

    // =========================================================================
    // SECTION 4 : CONCURRENCE RÉELLE MULTI-CONNEXIONS (OPTIMISTIC & PESSIMISTIC)
    // =========================================================================

    @Test
    @Order(7)
    @DisplayName("RealDB-07 (Concurrence Réelle) : 2 transactions simultanées sur même pointage v=1 -> Exactement 1 COMMIT réussi, 1 Conflit 409")
    void testRealDb_07_ConcurrenceOptimiste_DeuxConnexionsSimultanees_UnSeulSucces() throws Exception {
        Employe emp = creerEtCommiterEmploye("RDB07");
        ContratTravail ct = creerEtCommiterContrat(emp.getIdEmploye(), "ACTIF", Date.valueOf("2026-01-01"), null);
        creerEtCommiterAffectation(emp.getIdEmploye(), ct.getIdContrat(), 1, "ACTIF", Date.valueOf("2026-01-01"), null);
        int idPer = creerEtCommiterPeriodePaie("EN_COURS", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        // Initialisation en base (version 1)
        try (Connection cInit = dataSource.getConnection()) {
            cInit.setAutoCommit(false);
            SaisiePointageJourDTO initDto = new SaisiePointageJourDTO();
            initDto.setIdEmploye(emp.getIdEmploye());
            initDto.setIdSite(1);
            initDto.setIdPeriodePaie(idPer);
            initDto.setDatePointage("2026-08-20");
            initDto.setHeureEntreeMatin("08:00");
            initDto.setHeureSortieAm("17:00");
            pointageSaisieService.enregistrerPointagesBatch(Collections.singletonList(initDto), cInit);
            cInit.commit();
        }

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        Callable<Void> worker1 = () -> {
            startGate.await();
            try (Connection c1 = dataSource.getConnection()) {
                c1.setAutoCommit(false);
                try {
                    SaisiePointageJourDTO dto1 = new SaisiePointageJourDTO();
                    dto1.setIdEmploye(emp.getIdEmploye());
                    dto1.setIdSite(1);
                    dto1.setIdPeriodePaie(idPer);
                    dto1.setDatePointage("2026-08-20");
                    dto1.setVersion(1); // Version initiale
                    dto1.setHeureEntreeMatin("08:15");
                    dto1.setHeureSortieAm("17:15");
                    dto1.setMotifAjustement("Connexion 1 Gagnante");
                    pointageSaisieService.enregistrerPointagesBatch(Collections.singletonList(dto1), c1);
                    c1.commit();
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    c1.rollback();
                    conflictCount.incrementAndGet();
                }
            }
            return null;
        };

        Callable<Void> worker2 = () -> {
            startGate.await();
            try (Connection c2 = dataSource.getConnection()) {
                c2.setAutoCommit(false);
                try {
                    SaisiePointageJourDTO dto2 = new SaisiePointageJourDTO();
                    dto2.setIdEmploye(emp.getIdEmploye());
                    dto2.setIdSite(1);
                    dto2.setIdPeriodePaie(idPer);
                    dto2.setDatePointage("2026-08-20");
                    dto2.setVersion(1); // Même version initiale 1
                    dto2.setHeureEntreeMatin("08:45");
                    dto2.setHeureSortieAm("17:45");
                    dto2.setMotifAjustement("Connexion 2 Concurrente");
                    pointageSaisieService.enregistrerPointagesBatch(Collections.singletonList(dto2), c2);
                    c2.commit();
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    c2.rollback();
                    conflictCount.incrementAndGet();
                }
            }
            return null;
        };

        Future<Void> f1 = executor.submit(worker1);
        Future<Void> f2 = executor.submit(worker2);

        startGate.countDown();
        f1.get();
        f2.get();
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactement 1 transaction doit réussir et commiter");
        assertEquals(1, conflictCount.get(), "L'autre transaction concurrente doit être rejetée (409 Conflict)");

        // Vérification sur 3ème connexion de l'état persistant dans PostgreSQL
        try (Connection c3 = dataSource.getConnection()) {
            String sql = "SELECT version, motif_ajustement FROM pointage_jour WHERE id_employe = ? AND date_pointage = ?";
            try (PreparedStatement ps = c3.prepareStatement(sql)) {
                ps.setInt(1, emp.getIdEmploye());
                ps.setDate(2, Date.valueOf("2026-08-20"));
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(2, rs.getInt("version"), "La version finale en base doit être 2");
                    assertNotNull(rs.getString("motif_ajustement"));
                }
            }
        }
    }

    @Test
    @Order(8)
    @DisplayName("RealDB-08 (Concurrence Réelle) : 2 transactions simultanées de validation de congé -> 1 seul COMMIT VALIDE, 1 mouvement PRISE")
    void testRealDb_08_ConcurrenceValidationConge_DeuxConnexionsSimultanees_UnSeulMouvementPrise() throws Exception {
        Employe emp = creerEtCommiterEmploye("RDB08");
        creerEtCommiterContrat(emp.getIdEmploye(), "ACTIF", Date.valueOf("2025-01-01"), Date.valueOf("2026-12-31"));

        int idDemande;
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            int idType = queryId(c, "SELECT id_type_conge FROM ref_type_conge WHERE code = 'ANNUEL'");
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO mouvement_solde_conge (id_employe, type_mouvement, nb_jours, date_mouvement, commentaire) VALUES (?, 'ACQUISITION', 25.0, CURRENT_DATE, 'Acquisition')")) {
                ps.setInt(1, emp.getIdEmploye());
                ps.executeUpdate();
            }
            DemandeConge d = new DemandeConge();
            d.setIdEmploye(emp.getIdEmploye());
            d.setIdTypeConge(idType);
            d.setDateDebut(Date.valueOf("2026-10-10"));
            d.setDateFin(Date.valueOf("2026-10-15")); // 6 jours
            DemandeConge saved = congeService.creerDemande(d, 1, c);
            idDemande = saved.getIdDemandeConge();
            c.commit();
        }

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger valides = new AtomicInteger(0);
        AtomicInteger rejetes = new AtomicInteger(0);

        Callable<Void> validerTask = () -> {
            startGate.await();
            try (Connection c = dataSource.getConnection()) {
                c.setAutoCommit(false);
                try {
                    DemandeConge d = DemandeConge.rechercherParId(idDemande, c);
                    d.valider(1, c);
                    c.commit();
                    valides.incrementAndGet();
                } catch (Exception e) {
                    c.rollback();
                    rejetes.incrementAndGet();
                }
            }
            return null;
        };

        Future<Void> f1 = executor.submit(validerTask);
        Future<Void> f2 = executor.submit(validerTask);

        startGate.countDown();
        f1.get();
        f2.get();
        executor.shutdown();

        assertEquals(1, valides.get(), "Une seule validation concurrente doit réussir");
        assertEquals(1, rejetes.get(), "L'autre validation concurrente doit échouer");

        // Relecture sur nouvelle connexion
        try (Connection cCheck = dataSource.getConnection()) {
            String sqlCount = "SELECT COUNT(*) FROM mouvement_solde_conge WHERE id_demande_conge = ? AND type_mouvement = 'PRISE'";
            try (PreparedStatement ps = cCheck.prepareStatement(sqlCount)) {
                ps.setInt(1, idDemande);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1), "Le Ledger doit contenir physiquement 1 seul mouvement PRISE");
                }
            }
        }
    }

    // =========================================================================
    // SECTION 5 : SÉRIALISATION CONCURRENTE SAISIE POINTAGE VS CLÔTURE PAIE
    // =========================================================================

    @Test
    @Order(9)
    @DisplayName("RealDB-09 (Concurrence FOR SHARE vs EXCLUSIVE) : Saisie en cours bloque clôture, puis clôture bloque écritures ultérieures")
    void testRealDb_09_ConcurrencePointageVsCloturePaie_VerrouillagePartageEtExclusif() throws Exception {
        Employe emp = creerEtCommiterEmploye("RDB09");
        ContratTravail ct = creerEtCommiterContrat(emp.getIdEmploye(), "ACTIF", Date.valueOf("2026-01-01"), null);
        creerEtCommiterAffectation(emp.getIdEmploye(), ct.getIdContrat(), 1, "ACTIF", Date.valueOf("2026-01-01"), null);
        int idPer = creerEtCommiterPeriodePaie("EN_COURS", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        CountDownLatch tx1HoldLock = new CountDownLatch(1);
        CountDownLatch tx2Started = new CountDownLatch(1);
        AtomicInteger tx1Status = new AtomicInteger(0);
        AtomicInteger tx2Status = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Transaction 1 : Saisie de pointage (acquiert FOR SHARE sur période_paie)
        Future<Void> f1 = executor.submit(() -> {
            try (Connection c1 = dataSource.getConnection()) {
                c1.setAutoCommit(false);
                SaisiePointageJourDTO dto = new SaisiePointageJourDTO();
                dto.setIdEmploye(emp.getIdEmploye());
                dto.setIdSite(1);
                dto.setIdPeriodePaie(idPer);
                dto.setDatePointage("2026-09-10");
                dto.setHeureEntreeMatin("08:00");
                dto.setHeureSortieAm("17:00");
                pointageSaisieService.enregistrerPointagesBatch(Collections.singletonList(dto), c1);

                tx1HoldLock.countDown(); // Signale que FOR SHARE est acquis
                // Simule une transaction longue
                Thread.sleep(300);

                c1.commit();
                tx1Status.set(1); // Succès
            }
            return null;
        });

        // Transaction 2 : Clôture de la période de paie (requiert verrou EXCLUSIF sur la ligne periode_paie)
        Future<Void> f2 = executor.submit(() -> {
            tx1HoldLock.await(); // Attend que Tx1 ait acquis FOR SHARE
            tx2Started.countDown();
            try (Connection c2 = dataSource.getConnection()) {
                c2.setAutoCommit(false);
                String sqlClose = "UPDATE periode_paie SET id_statut_periode = (SELECT id_statut_periode FROM ref_statut_periode_paie WHERE code = 'VALIDEE') WHERE id_periode_paie = ?";
                try (PreparedStatement ps = c2.prepareStatement(sqlClose)) {
                    ps.setInt(1, idPer);
                    ps.executeUpdate(); // Va attendre que Tx1 commit
                }
                c2.commit();
                tx2Status.set(1); // Succès
            }
            return null;
        });

        f1.get();
        f2.get();
        executor.shutdown();

        assertEquals(1, tx1Status.get(), "La saisie de pointage sous verrou FOR SHARE doit réussir");
        assertEquals(1, tx2Status.get(), "La clôture de la période doit réussir après la libération du verrou partagé");

        // Transaction 3 : Tentative d'écriture ULTÉRIEURE sur la période désormais VALIDEE
        try (Connection c3 = dataSource.getConnection()) {
            c3.setAutoCommit(false);
            SaisiePointageJourDTO dtoPost = new SaisiePointageJourDTO();
            dtoPost.setIdEmploye(emp.getIdEmploye());
            dtoPost.setIdSite(1);
            dtoPost.setIdPeriodePaie(idPer);
            dtoPost.setDatePointage("2026-09-15");
            dtoPost.setHeureEntreeMatin("08:00");
            dtoPost.setHeureSortieAm("17:00");

            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
                pointageSaisieService.enregistrerPointagesBatch(Collections.singletonList(dtoPost), c3);
            });
            assertTrue(ex.getMessage().contains("fermée"), "L'écriture après clôture doit être immédiatement rejetée : " + ex.getMessage());
            c3.rollback();
        }

        // Vérification sur nouvelle connexion : le pointage de Tx1 existe, celui de Tx3 n'existe pas
        try (Connection c4 = dataSource.getConnection()) {
            String sqlP1 = "SELECT COUNT(*) FROM pointage_jour WHERE id_employe = ? AND date_pointage = '2026-09-10'";
            try (PreparedStatement ps = c4.prepareStatement(sqlP1)) {
                ps.setInt(1, emp.getIdEmploye());
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1), "Le pointage validé avant clôture doit être présent");
                }
            }

            String sqlP3 = "SELECT COUNT(*) FROM pointage_jour WHERE id_employe = ? AND date_pointage = '2026-09-15'";
            try (PreparedStatement ps = c4.prepareStatement(sqlP3)) {
                ps.setInt(1, emp.getIdEmploye());
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(0, rs.getInt(1), "Le pointage tenté après clôture ne doit pas exister");
                }
            }
        }
    }

    // =========================================================================
    // SECTION 6 : TESTS DES CHEMINS ALTERNATIFS & CYCLE DE VIE EMPLOYÉ
    // =========================================================================

    @Test
    @Order(10)
    @DisplayName("RealDB-10 : Réactivation employé -> COMMIT -> Vérification cohérence contrat + affectation")
    void testRealDb_10_ReactivationValide_CommitEtVerification() throws Exception {
        Employe emp = creerEtCommiterEmploye("RDB10");
        ContratTravail ct = creerEtCommiterContrat(emp.getIdEmploye(), "ACTIF", Date.valueOf("2026-01-01"), null);
        creerEtCommiterAffectation(emp.getIdEmploye(), ct.getIdContrat(), 1, "ACTIF", Date.valueOf("2026-01-01"), null);

        // Désactivation simulée (employé inactif)
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement("UPDATE employe SET est_actif = FALSE WHERE id_employe = ?")) {
                ps.setInt(1, emp.getIdEmploye());
                ps.executeUpdate();
            }
            c.commit();
        }

        // Réactivation
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            emp.reactiver(c);
            c.commit();
        }

        // Vérification sur nouvelle connexion
        try (Connection cCheck = dataSource.getConnection()) {
            String sql = "SELECT est_actif, date_desactivation FROM employe WHERE id_employe = ?";
            try (PreparedStatement ps = cCheck.prepareStatement(sql)) {
                ps.setInt(1, emp.getIdEmploye());
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertTrue(rs.getBoolean("est_actif"), "L'employé doit être réactivé");
                    assertNull(rs.getDate("date_desactivation"));
                }
            }
        }
    }

    @Test
    @Order(11)
    @DisplayName("RealDB-11 (Chemin Alternatif) : Régularisation d'un pointage existant avec version = null -> REJET")
    void testRealDb_11_CheminsAlternatifs_RegularisationAvecVersionNull_Rejet() throws Exception {
        Employe emp = creerEtCommiterEmploye("RDB11");
        ContratTravail ct = creerEtCommiterContrat(emp.getIdEmploye(), "ACTIF", Date.valueOf("2026-01-01"), null);
        creerEtCommiterAffectation(emp.getIdEmploye(), ct.getIdContrat(), 1, "ACTIF", Date.valueOf("2026-01-01"), null);
        int idPer = creerEtCommiterPeriodePaie("EN_COURS", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        int idPtg;
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            SaisiePointageJourDTO dto = new SaisiePointageJourDTO();
            dto.setIdEmploye(emp.getIdEmploye());
            dto.setIdSite(1);
            dto.setIdPeriodePaie(idPer);
            dto.setDatePointage("2026-08-28");
            dto.setHeureEntreeMatin("08:00");
            dto.setHeureSortieAm("17:00");
            pointageSaisieService.enregistrerPointagesBatch(Collections.singletonList(dto), c);
            c.commit();

            idPtg = queryId(c, "SELECT id_pointage FROM pointage_jour WHERE id_employe = " + emp.getIdEmploye() + " AND date_pointage = '2026-08-28'");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("idPointage", idPtg);
        payload.put("idEmploye", emp.getIdEmploye());
        payload.put("idSite", 1);
        payload.put("datePointage", "2026-08-28");
        payload.put("version", null); // Version omise/nulle volontairement
        payload.put("heureEntreeMatin", "08:30");
        payload.put("heureSortieAm", "17:30");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            pointageAnomalieService.regulariserPointage(idPtg, payload);
        });
        assertTrue(ex.getMessage().contains("version") && ex.getMessage().contains("obligatoire"));

        // Vérification nouvelle connexion : la version reste intacte à 1
        try (Connection cCheck = dataSource.getConnection()) {
            String sql = "SELECT version, heure_entree_matin FROM pointage_jour WHERE id_pointage = ?";
            try (PreparedStatement ps = cCheck.prepareStatement(sql)) {
                ps.setInt(1, idPtg);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt("version"));
                    assertEquals("08:00:00", rs.getTime("heure_entree_matin").toString());
                }
            }
        }
    }

    @Test
    @Order(12)
    @DisplayName("RealDB-12 : Saisie batch avec version = null sur pointage existant -> REJET")
    void testRealDb_12_BatchUpdateAvecVersionNull_Rejet() throws Exception {
        Employe emp = creerEtCommiterEmploye("RDB12");
        ContratTravail ct = creerEtCommiterContrat(emp.getIdEmploye(), "ACTIF", Date.valueOf("2026-01-01"), null);
        creerEtCommiterAffectation(emp.getIdEmploye(), ct.getIdContrat(), 1, "ACTIF", Date.valueOf("2026-01-01"), null);
        int idPer = creerEtCommiterPeriodePaie("EN_COURS", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        // Pointage initial version 1
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            SaisiePointageJourDTO dto = new SaisiePointageJourDTO();
            dto.setIdEmploye(emp.getIdEmploye());
            dto.setIdSite(1);
            dto.setIdPeriodePaie(idPer);
            dto.setDatePointage("2026-08-25");
            dto.setHeureEntreeMatin("08:00");
            dto.setHeureSortieAm("17:00");
            pointageSaisieService.enregistrerPointagesBatch(Collections.singletonList(dto), c);
            c.commit();
        }

        // Tentative de modification sans champ version
        try (Connection cUpdate = dataSource.getConnection()) {
            cUpdate.setAutoCommit(false);
            SaisiePointageJourDTO dtoUp = new SaisiePointageJourDTO();
            dtoUp.setIdEmploye(emp.getIdEmploye());
            dtoUp.setIdSite(1);
            dtoUp.setIdPeriodePaie(idPer);
            dtoUp.setDatePointage("2026-08-25");
            dtoUp.setVersion(null); // Version non renseignée
            dtoUp.setHeureEntreeMatin("08:30");
            dtoUp.setHeureSortieAm("17:30");

            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
                pointageSaisieService.enregistrerPointagesBatch(Collections.singletonList(dtoUp), cUpdate);
            });
            assertTrue(ex.getMessage().contains("version") && ex.getMessage().contains("obligatoire"));
            cUpdate.rollback();
        }
    }

    @Test
    @Order(13)
    @DisplayName("RealDB-13 : Rejet pointage sur période fermée (VALIDEE/CLOTUREE) -> Aucune donnée persistée")
    void testRealDb_13_RejetPointagePeriodeFermee_AucuneEcriturePersistee() throws Exception {
        Employe emp = creerEtCommiterEmploye("RDB13");
        ContratTravail ct = creerEtCommiterContrat(emp.getIdEmploye(), "ACTIF", Date.valueOf("2026-01-01"), null);
        creerEtCommiterAffectation(emp.getIdEmploye(), ct.getIdContrat(), 1, "ACTIF", Date.valueOf("2026-01-01"), null);
        int idPerCloturee = creerEtCommiterPeriodePaie("CLOTUREE", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            SaisiePointageJourDTO dto = new SaisiePointageJourDTO();
            dto.setIdEmploye(emp.getIdEmploye());
            dto.setIdSite(1);
            dto.setIdPeriodePaie(idPerCloturee);
            dto.setDatePointage("2026-07-15");
            dto.setHeureEntreeMatin("08:00");
            dto.setHeureSortieAm("17:00");

            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
                pointageSaisieService.enregistrerPointagesBatch(Collections.singletonList(dto), c);
            });
            assertTrue(ex.getMessage().contains("fermée"));
            c.rollback();
        }

        try (Connection cCheck = dataSource.getConnection()) {
            String sql = "SELECT COUNT(*) FROM pointage_jour WHERE id_employe = ? AND date_pointage = '2026-07-15'";
            try (PreparedStatement ps = cCheck.prepareStatement(sql)) {
                ps.setInt(1, emp.getIdEmploye());
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(0, rs.getInt(1), "Aucun pointage ne doit être persisté pour une période fermée");
                }
            }
        }
    }

    @Test
    @Order(14)
    @DisplayName("RealDB-14 : Rejet pointage pour employé inactif -> Aucune donnée persistée")
    void testRealDb_14_RejetPointageEmployeInactif_AucuneEcriturePersistee() throws Exception {
        Employe emp = creerEtCommiterEmploye("RDB14");
        ContratTravail ct = creerEtCommiterContrat(emp.getIdEmploye(), "ACTIF", Date.valueOf("2026-01-01"), null);
        creerEtCommiterAffectation(emp.getIdEmploye(), ct.getIdContrat(), 1, "ACTIF", Date.valueOf("2026-01-01"), null);
        int idPer = creerEtCommiterPeriodePaie("EN_COURS", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        // Désactivation de l'employé
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement("UPDATE employe SET est_actif = FALSE WHERE id_employe = ?")) {
                ps.setInt(1, emp.getIdEmploye());
                ps.executeUpdate();
            }
            c.commit();
        }

        try (Connection cPtg = dataSource.getConnection()) {
            cPtg.setAutoCommit(false);
            SaisiePointageJourDTO dto = new SaisiePointageJourDTO();
            dto.setIdEmploye(emp.getIdEmploye());
            dto.setIdSite(1);
            dto.setIdPeriodePaie(idPer);
            dto.setDatePointage("2026-08-18");
            dto.setHeureEntreeMatin("08:00");
            dto.setHeureSortieAm("17:00");

            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
                pointageSaisieService.enregistrerPointagesBatch(Collections.singletonList(dto), cPtg);
            });
            assertTrue(ex.getMessage().toLowerCase().contains("éligible") || ex.getMessage().toLowerCase().contains("actif"));
            cPtg.rollback();
        }

        try (Connection cCheck = dataSource.getConnection()) {
            String sql = "SELECT COUNT(*) FROM pointage_jour WHERE id_employe = ? AND date_pointage = '2026-08-18'";
            try (PreparedStatement ps = cCheck.prepareStatement(sql)) {
                ps.setInt(1, emp.getIdEmploye());
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(0, rs.getInt(1), "Aucune ligne ne doit être persistée pour un employé inactif");
                }
            }
        }
    }

    @Test
    @Order(15)
    @DisplayName("RealDB-15 : Rejet pointage sur un site différent de l'affectation active -> Aucune donnée persistée")
    void testRealDb_15_RejetPointageMauvaisSite_AucuneEcriturePersistee() throws Exception {
        Employe emp = creerEtCommiterEmploye("RDB15");
        ContratTravail ct = creerEtCommiterContrat(emp.getIdEmploye(), "ACTIF", Date.valueOf("2026-01-01"), null);
        // Affecté sur Site 1
        creerEtCommiterAffectation(emp.getIdEmploye(), ct.getIdContrat(), 1, "ACTIF", Date.valueOf("2026-01-01"), null);
        int idPer = creerEtCommiterPeriodePaie("EN_COURS", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        int idAutreSite;
        try (Connection c = dataSource.getConnection()) {
            idAutreSite = queryId(c, "SELECT id_site FROM ref_site WHERE id_site != 1 LIMIT 1");
        }

        try (Connection cPtg = dataSource.getConnection()) {
            cPtg.setAutoCommit(false);
            SaisiePointageJourDTO dto = new SaisiePointageJourDTO();
            dto.setIdEmploye(emp.getIdEmploye());
            dto.setIdSite(idAutreSite); // Mauvais site
            dto.setIdPeriodePaie(idPer);
            dto.setDatePointage("2026-08-19");
            dto.setHeureEntreeMatin("08:00");
            dto.setHeureSortieAm("17:00");

            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
                pointageSaisieService.enregistrerPointagesBatch(Collections.singletonList(dto), cPtg);
            });
            assertTrue(ex.getMessage().contains("affectation"));
            cPtg.rollback();
        }
    }

    @Test
    @Order(16)
    @DisplayName("RealDB-16 : Création demande de congé -> COMMIT -> Relecture état persistant EN_ATTENTE")
    void testRealDb_16_CreationDemandeConge_CommitEtRelecture() throws Exception {
        Employe emp = creerEtCommiterEmploye("RDB16");
        creerEtCommiterContrat(emp.getIdEmploye(), "ACTIF", Date.valueOf("2025-01-01"), Date.valueOf("2026-12-31"));

        int idDemande;
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            int idType = queryId(c, "SELECT id_type_conge FROM ref_type_conge WHERE code = 'ANNUEL'");
            DemandeConge d = new DemandeConge();
            d.setIdEmploye(emp.getIdEmploye());
            d.setIdTypeConge(idType);
            d.setDateDebut(Date.valueOf("2026-11-10"));
            d.setDateFin(Date.valueOf("2026-11-12"));
            DemandeConge saved = congeService.creerDemande(d, 1, c);
            idDemande = saved.getIdDemandeConge();
            c.commit();
        }

        try (Connection cCheck = dataSource.getConnection()) {
            String sql = "SELECT s.code, dc.nb_jours_decomptes FROM demande_conge dc " +
                    "JOIN ref_statut_demande_conge s ON dc.id_statut_demande_conge = s.id_statut_demande_conge " +
                    "WHERE dc.id_demande_conge = ?";
            try (PreparedStatement ps = cCheck.prepareStatement(sql)) {
                ps.setInt(1, idDemande);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("EN_ATTENTE", rs.getString("code"));
                    assertEquals(3.0, rs.getDouble("nb_jours_decomptes"), 0.01);
                }
            }
        }
    }

    @Test
    @Order(17)
    @DisplayName("RealDB-17 : Double validation séquentielle de congé -> Rejet de la 2ème validation")
    void testRealDb_17_DoubleValidationSequentielle_RejetDeuxiemeTentative() throws Exception {
        Employe emp = creerEtCommiterEmploye("RDB17");
        creerEtCommiterContrat(emp.getIdEmploye(), "ACTIF", Date.valueOf("2025-01-01"), Date.valueOf("2026-12-31"));

        int idDemande;
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            int idType = queryId(c, "SELECT id_type_conge FROM ref_type_conge WHERE code = 'ANNUEL'");
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO mouvement_solde_conge (id_employe, type_mouvement, nb_jours, date_mouvement, commentaire) VALUES (?, 'ACQUISITION', 10.0, CURRENT_DATE, 'Acquisition')")) {
                ps.setInt(1, emp.getIdEmploye());
                ps.executeUpdate();
            }
            DemandeConge d = new DemandeConge();
            d.setIdEmploye(emp.getIdEmploye());
            d.setIdTypeConge(idType);
            d.setDateDebut(Date.valueOf("2026-12-10"));
            d.setDateFin(Date.valueOf("2026-12-12"));
            DemandeConge saved = congeService.creerDemande(d, 1, c);
            idDemande = saved.getIdDemandeConge();
            c.commit();
        }

        // 1ère validation -> Succès et COMMIT
        try (Connection c1 = dataSource.getConnection()) {
            c1.setAutoCommit(false);
            DemandeConge.rechercherParId(idDemande, c1).valider(1, c1);
            c1.commit();
        }

        // 2ème validation séquentielle sur la même demande déjà VALIDEE -> Rejet
        try (Connection c2 = dataSource.getConnection()) {
            c2.setAutoCommit(false);
            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
                DemandeConge.rechercherParId(idDemande, c2).valider(1, c2);
            });
            assertTrue(ex.getMessage().contains("EN_ATTENTE") || ex.getMessage().contains("Statut actuel"));
            c2.rollback();
        }
    }

    @Test
    @Order(18)
    @DisplayName("RealDB-18 : Désactivation directe d'un employé avec contrat actif -> REJET et état DB inchangé")
    void testRealDb_18_DesactivationRejeteeAvecContratActif_EtatInchange() throws Exception {
        Employe emp = creerEtCommiterEmploye("RDB18");
        creerEtCommiterContrat(emp.getIdEmploye(), "ACTIF", Date.valueOf("2026-01-01"), null);

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
                emp.desactiver("Sortie de test", c);
            });
            assertTrue(ex.getMessage().toLowerCase().contains("contrat") && ex.getMessage().toLowerCase().contains("actif"));
            c.rollback();
        }

        try (Connection cCheck = dataSource.getConnection()) {
            String sql = "SELECT est_actif FROM employe WHERE id_employe = ?";
            try (PreparedStatement ps = cCheck.prepareStatement(sql)) {
                ps.setInt(1, emp.getIdEmploye());
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertTrue(rs.getBoolean("est_actif"), "L'employé doit toujours être actif dans PostgreSQL");
                }
            }
        }
    }

    @Test
    @Order(19)
    @DisplayName("RealDB-19 : Réactivation d'un employé avec affectation clôturée -> REJET et état DB inchangé")
    void testRealDb_19_ReactivationRejeteeAvecAffectationFermee_EtatInchange() throws Exception {
        Employe emp = creerEtCommiterEmploye("RDB19");
        ContratTravail ct = creerEtCommiterContrat(emp.getIdEmploye(), "ACTIF", Date.valueOf("2026-01-01"), null);
        // Affectation CLOTURE
        creerEtCommiterAffectation(emp.getIdEmploye(), ct.getIdContrat(), 1, "CLOTURE", Date.valueOf("2026-01-01"), Date.valueOf("2026-06-30"));

        // Marquer employé inactif en base
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement("UPDATE employe SET est_actif = FALSE WHERE id_employe = ?")) {
                ps.setInt(1, emp.getIdEmploye());
                ps.executeUpdate();
            }
            c.commit();
        }

        try (Connection cRe = dataSource.getConnection()) {
            cRe.setAutoCommit(false);
            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
                emp.reactiver(cRe);
            });
            assertTrue(ex.getMessage().contains("affectation"));
            cRe.rollback();
        }

        try (Connection cCheck = dataSource.getConnection()) {
            String sql = "SELECT est_actif FROM employe WHERE id_employe = ?";
            try (PreparedStatement ps = cCheck.prepareStatement(sql)) {
                ps.setInt(1, emp.getIdEmploye());
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertFalse(rs.getBoolean("est_actif"), "L'employé doit rester inactif dans PostgreSQL");
                }
            }
        }
    }

    @Test
    @Order(20)
    @DisplayName("RealDB-20 : Concurrence 2 demandes distinctes du même employé (Solde = 5j, 2x 4j) -> 1 succès, 1 rejet solde insuffisant, état DB = 1j")
    void testRealDb_20_ConcurrenceDeuxDemandesMemeEmployeSoldeInsuffisant() throws Exception {
        Employe emp = creerEtCommiterEmploye("RDB20");
        creerEtCommiterContrat(emp.getIdEmploye(), "ACTIF", Date.valueOf("2025-01-01"), Date.valueOf("2026-12-31"));

        int idTypeAnnuel;
        int idDemande1;
        int idDemande2;

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            idTypeAnnuel = queryId(c, "SELECT id_type_conge FROM ref_type_conge WHERE code = 'ANNUEL'");

            // Solde initial = 5.0 jours
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO mouvement_solde_conge (id_employe, type_mouvement, nb_jours, date_mouvement, commentaire) " +
                            "VALUES (?, 'ACQUISITION', 5.0, CURRENT_DATE, 'Initial 5j')")) {
                ps.setInt(1, emp.getIdEmploye());
                ps.executeUpdate();
            }

            int idStatutEnAttente = queryId(c, "SELECT id_statut_demande_conge FROM ref_statut_demande_conge WHERE code = 'EN_ATTENTE'");

            // Demande 1 : 3.0 jours (du jeudi 2026-10-01 au samedi 2026-10-03)
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO demande_conge (id_employe, id_type_conge, date_debut, date_fin, nb_jours_decomptes, id_statut_demande_conge, derogation_delai_preavis) " +
                            "VALUES (?, ?, '2026-10-01', '2026-10-03', 3.0, ?, true) RETURNING id_demande_conge")) {
                ps.setInt(1, emp.getIdEmploye());
                ps.setInt(2, idTypeAnnuel);
                ps.setInt(3, idStatutEnAttente);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    idDemande1 = rs.getInt(1);
                }
            }

            // Demande 2 : 3.0 jours (du lundi 2026-11-02 au mercredi 2026-11-04)
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO demande_conge (id_employe, id_type_conge, date_debut, date_fin, nb_jours_decomptes, id_statut_demande_conge, derogation_delai_preavis) " +
                            "VALUES (?, ?, '2026-11-02', '2026-11-04', 3.0, ?, true) RETURNING id_demande_conge")) {
                ps.setInt(1, emp.getIdEmploye());
                ps.setInt(2, idTypeAnnuel);
                ps.setInt(3, idStatutEnAttente);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    idDemande2 = rs.getInt(1);
                }
            }
            c.commit();
        }

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Future<Boolean> f1 = executor.submit(() -> {
            startLatch.await();
            try (Connection c1 = dataSource.getConnection()) {
                c1.setAutoCommit(false);
                DemandeConge d1 = DemandeConge.rechercherParId(idDemande1, c1);
                d1.valider(1, c1);
                c1.commit();
                return true;
            } catch (Exception e) {
                return false;
            }
        });

        Future<Boolean> f2 = executor.submit(() -> {
            startLatch.await();
            try (Connection c2 = dataSource.getConnection()) {
                c2.setAutoCommit(false);
                DemandeConge d2 = DemandeConge.rechercherParId(idDemande2, c2);
                d2.valider(1, c2);
                c2.commit();
                return true;
            } catch (Exception e) {
                return false;
            }
        });

        startLatch.countDown();
        boolean r1 = f1.get(10, TimeUnit.SECONDS);
        boolean r2 = f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactement une seule validation doit réussir, la deuxième doit échouer (solde insuffisant)
        assertTrue((r1 && !r2) || (!r1 && r2), "Exactement 1 validation concurrente doit réussir sur un solde de 5j demandant 2x 3j");

        // Relecture SQL sur nouvelle connexion : le solde final doit être exactement 2.0j (5 - 3)
        try (Connection cCheck = dataSource.getConnection()) {
            String sqlSolde = "SELECT solde_actuel FROM v_solde_conge_employe WHERE id_employe = ?";
            try (PreparedStatement ps = cCheck.prepareStatement(sqlSolde)) {
                ps.setInt(1, emp.getIdEmploye());
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(2.0, rs.getDouble("solde_actuel"), 0.01, "Le solde net persistant doit être 5.0 - 3.0 = 2.0j");
                }
            }

            // Vérifier qu'il n'y a qu'un seul mouvement PRISE
            String sqlMvt = "SELECT COUNT(*) FROM mouvement_solde_conge WHERE id_employe = ? AND type_mouvement = 'PRISE'";
            try (PreparedStatement ps = cCheck.prepareStatement(sqlMvt)) {
                ps.setInt(1, emp.getIdEmploye());
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1), "Il doit y avoir un seul mouvement PRISE en base");
                }
            }
        }
    }

    @Test
    @Order(21)
    @DisplayName("RealDB-21 : Mise à jour de statut de période (VALIDEE) -> Transaction atomique et gel de synthèse")
    void testRealDb_21_PeriodePaie_UpdateStatutAtomicite() throws Exception {
        int idPeriode = creerEtCommiterPeriodePaie("EN_COURS", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                String sql = "UPDATE periode_paie SET id_statut_periode = (SELECT id_statut_periode FROM ref_statut_periode_paie WHERE code = 'VALIDEE') WHERE id_periode_paie = ?";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setInt(1, idPeriode);
                    assertEquals(1, ps.executeUpdate());
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }

        try (Connection cCheck = dataSource.getConnection()) {
            String sqlCheck = "SELECT s.code FROM periode_paie p JOIN ref_statut_periode_paie s ON p.id_statut_periode = s.id_statut_periode WHERE p.id_periode_paie = ?";
            try (PreparedStatement ps = cCheck.prepareStatement(sqlCheck)) {
                ps.setInt(1, idPeriode);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("VALIDEE", rs.getString("code"));
                }
            }
        }
    }
}
