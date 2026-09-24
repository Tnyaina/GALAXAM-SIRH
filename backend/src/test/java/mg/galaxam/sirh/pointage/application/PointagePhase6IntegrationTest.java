package mg.galaxam.sirh.pointage.application;

import mg.galaxam.sirh.conge.application.CongeService;
import mg.galaxam.sirh.conge.application.RegularisationCongeResult;
import mg.galaxam.sirh.conge.testutils.CongeTestFixtureHelper;
import mg.galaxam.sirh.organisation.persistence.RefSite;
import mg.galaxam.sirh.pointage.api.dto.SaisiePointageJourDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class PointagePhase6IntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PointageService pointageService;

    @Autowired
    private PointageSyntheseService pointageSyntheseService;

    @Autowired
    private PointageAnomalieService pointageAnomalieService;

    @Autowired
    private CongeService congeService;

    private int employeId;
    private int adminUserId;
    private int activeSiteId;
    private int inactiveSiteId;
    private int testPeriodId;

    @BeforeEach
    void setUp() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            String suffix = Long.toString(System.nanoTime(), 36).toUpperCase();
            adminUserId = CongeTestFixtureHelper.createAdminUser(c, "adm_" + suffix.substring(0, Math.min(8, suffix.length())));
            String matricule = "E_" + suffix.substring(0, Math.min(6, suffix.length()));
            String cinUnique = String.format("%012d", Math.abs(System.nanoTime() % 1_000_000_000_000L));
            try (PreparedStatement psEmp = c.prepareStatement(
                    "INSERT INTO employe (matricule, nom, prenom, sexe, date_naissance, lieu_naissance, " +
                    "cin_numero, cin_date_delivrance, cin_lieu_delivrance, adresse, telephone) " +
                    "VALUES (?, 'PHASE6', 'Collab', 'M', '1990-01-15', 'Antananarivo', ?, '2010-01-15', 'TNR', 'Rue Test', '0340000000') " +
                    "RETURNING id_employe")) {
                psEmp.setString(1, matricule);
                psEmp.setString(2, cinUnique);
                try (ResultSet rsEmp = psEmp.executeQuery()) {
                    rsEmp.next();
                    employeId = rsEmp.getInt(1);
                }
            }
            int contratId = CongeTestFixtureHelper.createContratActif(c, employeId, LocalDate.of(2025, 1, 1));
            CongeTestFixtureHelper.createAffectation(c, employeId, contratId, LocalDate.of(2025, 1, 1), new BigDecimal("1000000"));

            // Créer deux sites de test : un avec plancher actif, un avec plancher inactif
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO ref_site (code_site, libelle, horaire_continu, heure_debut_matin, heure_fin_matin, " +
                    "heure_debut_apres_midi, heure_fin_apres_midi, plancher_arrivee_actif, heure_entree_minimale_valorisee, est_actif) " +
                    "VALUES (?, 'Site Actif Test', TRUE, '08:00', '12:00', '13:00', '17:00', TRUE, '07:30', TRUE) RETURNING id_site")) {
                ps.setString(1, "ACT_" + suffix.substring(0, Math.min(5, suffix.length())));
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    activeSiteId = rs.getInt(1);
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO ref_site (code_site, libelle, horaire_continu, heure_debut_matin, heure_fin_matin, " +
                    "heure_debut_apres_midi, heure_fin_apres_midi, plancher_arrivee_actif, heure_entree_minimale_valorisee, est_actif) " +
                    "VALUES (?, 'Site Inactif Test', TRUE, '08:00', '12:00', '13:00', '17:00', FALSE, '07:30', TRUE) RETURNING id_site")) {
                ps.setString(1, "INA_" + suffix.substring(0, Math.min(5, suffix.length())));
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    inactiveSiteId = rs.getInt(1);
                }
            }

            // Récupérer une période ouverte
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id_periode_paie FROM periode_paie WHERE date_debut = '2026-09-01' LIMIT 1")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        testPeriodId = rs.getInt(1);
                    } else {
                        try (PreparedStatement psIns = c.prepareStatement(
                                "INSERT INTO periode_paie (code, libelle, date_debut, date_fin, id_statut_periode) " +
                                "VALUES ('2026-09-T', 'Période Test', '2026-09-01', '2026-09-30', 2) RETURNING id_periode_paie")) {
                            try (ResultSet rsIns = psIns.executeQuery()) {
                                rsIns.next();
                                testPeriodId = rsIns.getInt(1);
                            }
                        }
                    }
                }
            }
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM pointage_snapshot_jour WHERE id_pointage IN (SELECT id_pointage FROM pointage_jour WHERE id_employe = ?)")) {
                ps.setInt(1, employeId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM pointage_snapshot_bucket WHERE id_cumul IN (SELECT id_cumul FROM pointage_cumul_periode WHERE id_employe = ?)")) {
                ps.setInt(1, employeId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM pointage_snapshot_indemnite WHERE id_cumul IN (SELECT id_cumul FROM pointage_cumul_periode WHERE id_employe = ?)")) {
                ps.setInt(1, employeId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM pointage_cumul_periode WHERE id_employe = ?")) {
                ps.setInt(1, employeId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM pointage_indemnite_jour WHERE id_pointage IN (SELECT id_pointage FROM pointage_jour WHERE id_employe = ?)")) {
                ps.setInt(1, employeId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM pointage_jour WHERE id_employe = ?")) {
                ps.setInt(1, employeId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM mouvement_solde_conge WHERE id_employe = ?")) {
                ps.setInt(1, employeId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM demande_conge WHERE id_employe = ?")) {
                ps.setInt(1, employeId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM employe_affectation_carriere WHERE id_employe = ?")) {
                ps.setInt(1, employeId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM contrat_travail WHERE id_employe = ?")) {
                ps.setInt(1, employeId);
                ps.executeUpdate();
            }
            if (activeSiteId > 0) {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM ref_site WHERE id_site = ?")) {
                    ps.setInt(1, activeSiteId);
                    ps.executeUpdate();
                }
            }
            if (inactiveSiteId > 0) {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM ref_site WHERE id_site = ?")) {
                    ps.setInt(1, inactiveSiteId);
                    ps.executeUpdate();
                }
            }
        }
    }

    // =========================================================================
    // SECTION 8 : TEST D'INTÉGRATION BDD DU PLANCHER D'ARRIVÉE
    // =========================================================================

    @Test
    @DisplayName("Plancher d'arrivée BDD : activeArrivalFloor lit ref_site et respecte strictement plancher_arrivee_actif sans fallback")
    void plancherArriveeBddLectureRefSite() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            // Site actif -> retourne 07:30
            LocalTime floorActive = PointageService.activeArrivalFloor(c, activeSiteId);
            assertNotNull(floorActive);
            assertEquals(LocalTime.of(7, 30), floorActive);

            // Site inactif -> retourne null (aucun fallback implicite vers 07:30)
            LocalTime floorInactive = PointageService.activeArrivalFloor(c, inactiveSiteId);
            assertNull(floorInactive, "Pour un site dont plancher_arrivee_actif = FALSE, le plancher retourné DOIT être null.");

            // Calcul effectif pour badge à 07:00
            LocalTime punched = LocalTime.of(7, 0);
            LocalTime effectiveActive = PointageService.effectiveMorningEntry(punched, floorActive);
            assertEquals(LocalTime.of(7, 30), effectiveActive, "Site actif avec plancher 07:30 : badge 07:00 doit donner 07:30.");

            LocalTime effectiveInactive = PointageService.effectiveMorningEntry(punched, floorInactive);
            assertEquals(LocalTime.of(7, 0), effectiveInactive, "Site inactif : badge 07:00 doit donner 07:00 (badge brut conservé).");
        }
    }

    // =========================================================================
    // SECTION 19 : TESTS CONGÉ PARTIEL LORS D'UNE RÉGULARISATION
    // =========================================================================

    @Test
    @DisplayName("Cas A : absence 1j, solde 2j -> 1j congé, 0 absence, solde résiduel 1j")
    void casA_absence1j_solde2j_couvertureTotale() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            CongeTestFixtureHelper.crediterSolde(c, employeId, 2.0, "Solde 2j initial");
            Date dateAbsence = Date.valueOf(LocalDate.of(2026, 9, 7));

            c.setAutoCommit(false);
            RegularisationCongeResult res = congeService.regulariserCongeAnnuelDepuisPointageStructure(
                    employeId, dateAbsence, 1.0, null, "Régularisation 1j plein", adminUserId, c);
            c.commit();

            assertNotNull(res.demandeId());
            assertEquals(1.0, res.joursDemandes());
            assertEquals(1.0, res.joursCouverts());
            assertEquals(0.0, res.joursRestants());
            assertEquals(2.0, res.soldeAvant());
            assertEquals(1.0, res.soldeApres());

            // Vérifier le ledger PRISE = -1.0
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT nb_jours FROM mouvement_solde_conge WHERE id_demande_conge = ? AND type_mouvement = 'PRISE'")) {
                ps.setInt(1, res.demandeId());
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(0, new BigDecimal("-1.00").compareTo(rs.getBigDecimal(1)));
                }
            }
        }
    }

    @Test
    @DisplayName("Cas B : absence 1j, solde 0.5j -> 0.5 congé, 0.5 absence restante, solde résiduel 0j")
    void casB_absence1j_soldeDemiJour_couverturePartielle() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            CongeTestFixtureHelper.crediterSolde(c, employeId, 0.5, "Solde 0.5j initial");
            Date dateAbsence = Date.valueOf(LocalDate.of(2026, 9, 8));

            c.setAutoCommit(false);
            RegularisationCongeResult res = congeService.regulariserCongeAnnuelDepuisPointageStructure(
                    employeId, dateAbsence, 1.0, null, "Régularisation partielle", adminUserId, c);
            c.commit();

            assertNotNull(res.demandeId());
            assertEquals(1.0, res.joursDemandes());
            assertEquals(0.5, res.joursCouverts(), "Doit consommer tout le solde disponible (0.5j)");
            assertEquals(0.5, res.joursRestants(), "Le reliquat de 0.5j reste une absence");
            assertEquals(0.5, res.soldeAvant());
            assertEquals(0.0, res.soldeApres(), "Le solde final ne doit jamais être négatif");

            // Vérifier le ledger PRISE = -0.5
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT nb_jours FROM mouvement_solde_conge WHERE id_demande_conge = ? AND type_mouvement = 'PRISE'")) {
                ps.setInt(1, res.demandeId());
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(0, new BigDecimal("-0.50").compareTo(rs.getBigDecimal(1)));
                }
            }
        }
    }

    @Test
    @DisplayName("Cas C : absence 1j, solde 0 -> 0 congé, 1j absence complète, aucune écriture ledger")
    void casC_absence1j_soldeNul_aucuneCouverture() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            Date dateAbsence = Date.valueOf(LocalDate.of(2026, 9, 9));

            c.setAutoCommit(false);
            RegularisationCongeResult res = congeService.regulariserCongeAnnuelDepuisPointageStructure(
                    employeId, dateAbsence, 1.0, null, "Régularisation sans solde", adminUserId, c);
            c.commit();

            assertNull(res.demandeId(), "Aucune demande ne doit être créée si solde nul");
            assertEquals(1.0, res.joursDemandes());
            assertEquals(0.0, res.joursCouverts());
            assertEquals(1.0, res.joursRestants());
            assertEquals(0.0, res.soldeAvant());
            assertEquals(0.0, res.soldeApres());

            // Aucun mouvement PRISE
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM mouvement_solde_conge WHERE id_employe = ? AND type_mouvement = 'PRISE'")) {
                ps.setInt(1, employeId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(0, rs.getInt(1));
                }
            }
        }
    }

    @Test
    @DisplayName("Cas D : absence 0.5j, solde 0.25j -> 0.25 congé, 0.25 absence, précision fine NUMERIC(5,2)")
    void casD_absenceDemiJour_soldeQuartJour_fractionFine() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            CongeTestFixtureHelper.crediterSolde(c, employeId, 0.25, "Solde 0.25j initial");
            Date dateAbsence = Date.valueOf(LocalDate.of(2026, 9, 10));

            c.setAutoCommit(false);
            RegularisationCongeResult res = congeService.regulariserCongeAnnuelDepuisPointageStructure(
                    employeId, dateAbsence, 0.5, "MATIN", "Régularisation 0.25j", adminUserId, c);
            c.commit();

            assertNotNull(res.demandeId());
            assertEquals(0.5, res.joursDemandes());
            assertEquals(0.25, res.joursCouverts());
            assertEquals(0.25, res.joursRestants());
            assertEquals(0.25, res.soldeAvant());
            assertEquals(0.0, res.soldeApres());

            // Vérifier le ledger PRISE = -0.25
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT nb_jours FROM mouvement_solde_conge WHERE id_demande_conge = ? AND type_mouvement = 'PRISE'")) {
                ps.setInt(1, res.demandeId());
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(0, new BigDecimal("-0.25").compareTo(rs.getBigDecimal(1)));
                }
            }
        }
    }

    @Test
    @DisplayName("Cas E : Retry de la même régularisation -> idempotence absolue (aucun double débit ni double demande)")
    void casE_retryMemeRegularisation_idempotence() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            CongeTestFixtureHelper.crediterSolde(c, employeId, 2.0, "Solde 2j initial");
            Date dateAbsence = Date.valueOf(LocalDate.of(2026, 9, 11));

            c.setAutoCommit(false);
            RegularisationCongeResult res1 = congeService.regulariserCongeAnnuelDepuisPointageStructure(
                    employeId, dateAbsence, 1.0, null, "Premier passage", adminUserId, c);
            c.commit();

            assertNotNull(res1.demandeId());
            assertEquals(1.0, res1.joursCouverts());

            // Second passage (retry) pour la même date
            c.setAutoCommit(false);
            RegularisationCongeResult res2 = congeService.regulariserCongeAnnuelDepuisPointageStructure(
                    employeId, dateAbsence, 1.0, null, "Second passage (retry)", adminUserId, c);
            c.commit();

            assertEquals(res1.demandeId(), res2.demandeId(), "Le retry doit renvoyer la même demande");
            assertEquals(1.0, res2.joursCouverts());
            assertEquals(0.0, res2.joursRestants());

            // Vérifier qu'il n'y a qu'un SEUL mouvement PRISE pour cet employé et cette demande
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM mouvement_solde_conge WHERE id_employe = ? AND type_mouvement = 'PRISE'")) {
                ps.setInt(1, employeId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1), "Aucun double mouvement PRISE ne doit être créé lors d'un retry.");
                }
            }
        }
    }

    // =========================================================================
    // SECTION 21, 22, 23 : TEST GEL / REOPEN / REGEL & SNAPSHOTS & PAIE
    // =========================================================================

    @Test
    @DisplayName("Gel / Reopen / Regel : le regel archive l'ancien cumul en REMPLACE, crée V2 GELE, conserve les anciens snapshots intacts")
    void testGelReopenRegelComplet() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            // 1. Créer un pointage version 1
            LocalDate datePtg = LocalDate.of(2026, 9, 14);
            int ptgId;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO pointage_jour (id_employe, id_periode_paie, id_site, date_pointage, id_statut_jour, " +
                    "heure_entree_matin, heure_sortie_matin, heure_entree_matin_effective, total_heures, version) " +
                    "VALUES (?, ?, ?, ?, 1, '08:00', '16:00', '08:00', 8.0, 1) RETURNING id_pointage")) {
                ps.setInt(1, employeId);
                ps.setInt(2, testPeriodId);
                ps.setInt(3, activeSiteId);
                ps.setDate(4, Date.valueOf(datePtg));
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    ptgId = rs.getInt(1);
                }
            }

            // 2. Premier gel (APPROVE V1)
            c.setAutoCommit(false);
            pointageSyntheseService.gelerPeriode(testPeriodId, adminUserId, c);
            c.commit();

            int cumulV1Id;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id_cumul, version_snapshot, statut_snapshot FROM pointage_cumul_periode " +
                    "WHERE id_employe = ? AND id_periode_paie = ? AND statut_snapshot = 'GELE'")) {
                ps.setInt(1, employeId);
                ps.setInt(2, testPeriodId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    cumulV1Id = rs.getInt("id_cumul");
                    assertEquals(1, rs.getInt("version_snapshot"));
                    assertEquals("GELE", rs.getString("statut_snapshot"));
                }
            }

            // Vérifier snapshot_jour V1
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT version_pointage, minutes_travaillees, heure_entree_effective FROM pointage_snapshot_jour " +
                    "WHERE id_cumul = ? AND id_pointage = ?")) {
                ps.setInt(1, cumulV1Id);
                ps.setInt(2, ptgId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt("version_pointage"));
                    assertEquals(480, rs.getInt("minutes_travaillees"));
                    assertEquals(Time.valueOf("08:00:00"), rs.getTime("heure_entree_effective"));
                }
            }

            // 3. REOPEN (dégeler la période)
            c.setAutoCommit(false);
            pointageSyntheseService.degelerPeriode(testPeriodId, c);
            c.commit();

            // Vérifier que le cumul V1 est passé à 'REMPLACE'
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT statut_snapshot FROM pointage_cumul_periode WHERE id_cumul = ?")) {
                ps.setInt(1, cumulV1Id);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("REMPLACE", rs.getString("statut_snapshot"));
                }
            }

            // 4. Modifier le pointage (incrémenter version et changer heures : 9h au lieu de 8h)
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE pointage_jour SET total_heures = 9.0, heure_sortie_matin = '17:00', version = 2 " +
                    "WHERE id_pointage = ?")) {
                ps.setInt(1, ptgId);
                ps.executeUpdate();
            }

            // 5. Deuxième gel (APPROVE V2)
            c.setAutoCommit(false);
            pointageSyntheseService.gelerPeriode(testPeriodId, adminUserId, c);
            c.commit();

            int cumulV2Id;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id_cumul, version_snapshot, statut_snapshot, remplace_id_cumul FROM pointage_cumul_periode " +
                    "WHERE id_employe = ? AND id_periode_paie = ? AND statut_snapshot = 'GELE'")) {
                ps.setInt(1, employeId);
                ps.setInt(2, testPeriodId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    cumulV2Id = rs.getInt("id_cumul");
                    assertEquals(2, rs.getInt("version_snapshot"), "Nouveau cumul doit avoir version_snapshot = 2");
                    assertEquals("GELE", rs.getString("statut_snapshot"));
                    assertEquals(cumulV1Id, rs.getInt("remplace_id_cumul"));
                }
            }

            // 6. Vérifier que l'ancien snapshot V1 est TOUJOURS intact
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT version_pointage, minutes_travaillees FROM pointage_snapshot_jour " +
                    "WHERE id_cumul = ? AND id_pointage = ?")) {
                ps.setInt(1, cumulV1Id);
                ps.setInt(2, ptgId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt("version_pointage"), "L'ancien snapshot V1 doit rester inchangé avec version 1");
                    assertEquals(480, rs.getInt("minutes_travaillees"), "L'ancien snapshot V1 doit conserver 480 minutes");
                }
            }

            // 7. Vérifier que le nouveau snapshot V2 contient les données mises à jour (version 2, 540 min)
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT version_pointage, minutes_travaillees FROM pointage_snapshot_jour " +
                    "WHERE id_cumul = ? AND id_pointage = ?")) {
                ps.setInt(1, cumulV2Id);
                ps.setInt(2, ptgId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(2, rs.getInt("version_pointage"), "Le nouveau snapshot V2 doit avoir version 2");
                    assertEquals(540, rs.getInt("minutes_travaillees"), "Le nouveau snapshot V2 doit avoir 540 minutes");
                }
            }

            // 8. Vérifier la lecture Paie : ne sélectionne strictement QUE le cumul 'GELE'
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM pointage_cumul_periode WHERE id_employe = ? AND id_periode_paie = ? AND statut_snapshot = 'GELE'")) {
                ps.setInt(1, employeId);
                ps.setInt(2, testPeriodId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1), "Il ne doit y avoir qu'un seul cumul actif au statut 'GELE'");
                }
            }
        }
    }
}
