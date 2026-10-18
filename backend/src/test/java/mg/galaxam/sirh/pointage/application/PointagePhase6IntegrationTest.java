package mg.galaxam.sirh.pointage.application;

import mg.galaxam.sirh.conge.application.CongeService;
import mg.galaxam.sirh.conge.application.RegularisationCongeResult;
import mg.galaxam.sirh.conge.testutils.CongeTestFixtureHelper;
import mg.galaxam.sirh.paie.application.workflow.PayrollPeriodUserContext;
import mg.galaxam.sirh.paie.application.workflow.PayrollPeriodWorkflowService;
import mg.galaxam.sirh.paie.application.PayrollService;
import mg.galaxam.sirh.pointage.api.dto.PointageSyntheseMensuelleDTO;
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
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class PointagePhase6IntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PointageSyntheseService pointageSyntheseService;

    @Autowired
    private PointageService pointageService;

    @Autowired
    private CongeService congeService;

    @Autowired
    private PayrollPeriodWorkflowService payrollPeriodWorkflowService;

    @Autowired
    private PayrollService payrollService;

    @Autowired
    private PointageEmployeService pointageEmployeService;

    private int employeId;
    private int adminUserId;
    private int activeSiteId;
    private int inactiveSiteId;
    private int testPeriodId;
    private int initialAssignmentId;
    private int contractId;
    private int autonomousFunctionId;
    private int autonomousAllowanceId;
    private String autonomousAllowanceCode;

    @Test
    @DisplayName("Un salarié sorti en cours de campagne reste dans la synthèse de pointage")
    void sortieEnCoursDeCampagneResteDansLaSynthese() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE contrat_travail SET date_fin=DATE '2026-09-15' WHERE id_contrat=?")) {
                ps.setInt(1, contractId); ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE employe_affectation_carriere SET date_fin=DATE '2026-09-15' WHERE id_affectation=?")) {
                ps.setInt(1, initialAssignmentId); ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE employe SET est_actif=FALSE WHERE id_employe=?")) {
                ps.setInt(1, employeId); ps.executeUpdate();
            }
            assertTrue(pointageSyntheseService.getSynthesePeriode(testPeriodId, null, c).stream()
                    .anyMatch(row -> row.getIdEmploye() == employeId));
            assertTrue(pointageSyntheseService.preflightPeriode(testPeriodId, c).nombreEmployes() > 0);
            var page = pointageSyntheseService.getSynthesePeriodePaginee(
                    testPeriodId, null, null, null, 1, 100, c);
            @SuppressWarnings("unchecked")
            var rows = (java.util.List<PointageSyntheseMensuelleDTO>) page.get("synthese");
            assertTrue(rows.stream().anyMatch(row -> row.getIdEmploye() == employeId));
        }
    }

    @Test
    @DisplayName("La fiche propose les indemnités de la période en deux lectures groupées")
    void periodAllowanceSuggestionsFollowDayContext() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement update = c.prepareStatement(
                    "UPDATE employe_affectation_carriere SET id_site = ? WHERE id_affectation = ?")) {
                update.setInt(1, activeSiteId);
                update.setInt(2, initialAssignmentId);
                update.executeUpdate();
            }
            autonomousAllowanceCode = "SUG_" + Long.toString(System.nanoTime(), 36).toUpperCase();
            try (PreparedStatement insert = c.prepareStatement(
                    "INSERT INTO ref_indemnite (code, libelle, imposable_irsa, soumis_cotisation, fractionnable, est_actif) "
                            + "VALUES (?, 'Indemnité test période', FALSE, FALSE, TRUE, TRUE) RETURNING id_indemnite")) {
                insert.setString(1, autonomousAllowanceCode);
                try (ResultSet rows = insert.executeQuery()) {
                    assertTrue(rows.next());
                    autonomousAllowanceId = rows.getInt(1);
                }
            }
            try (PreparedStatement insert = c.prepareStatement(
                    "INSERT INTO site_indemnite_config (id_site, id_indemnite, periodicite, montant_reference, "
                            + "date_debut, date_fin, est_actif) VALUES (?, ?, 'JOURNALIER', 10000, "
                            + "DATE '2026-09-02', DATE '2026-09-02', TRUE)")) {
                insert.setInt(1, activeSiteId);
                insert.setInt(2, autonomousAllowanceId);
                insert.executeUpdate();
            }
            try (PreparedStatement insert = c.prepareStatement(
                    "INSERT INTO site_indemnite_config (id_site, id_indemnite, periodicite, montant_reference, "
                            + "date_debut, date_fin, est_actif) VALUES (?, ?, 'JOURNALIER', 20000, "
                            + "DATE '2026-09-03', DATE '2026-09-03', TRUE)")) {
                insert.setInt(1, activeSiteId);
                insert.setInt(2, autonomousAllowanceId);
                insert.executeUpdate();
            }
        }

        var fiche = pointageEmployeService.getFichePointageEmploye(
                employeId, null, "periode", testPeriodId, true);
        var first = fiche.getPointagesData().stream()
                .filter(day -> "2026-09-01".equals(day.getDateComplete())).findFirst().orElseThrow();
        var active = fiche.getPointagesData().stream()
                .filter(day -> "2026-09-02".equals(day.getDateComplete())).findFirst().orElseThrow();
        var changed = fiche.getPointagesData().stream()
                .filter(day -> "2026-09-03".equals(day.getDateComplete())).findFirst().orElseThrow();
        var expired = fiche.getPointagesData().stream()
                .filter(day -> "2026-09-04".equals(day.getDateComplete())).findFirst().orElseThrow();
        String key = Integer.toString(autonomousAllowanceId);
        assertFalse(first.getIndemnitesProposees().containsKey(key));
        assertTrue(active.getIndemnitesProposees().get(key).accordee());
        assertTrue(active.getIndemnitesProposees().get(key).matin());
        assertTrue(active.getIndemnitesProposees().get(key).am());
        assertEquals(0, active.getIndemnitesApplicables().get(key).montantJour()
                .compareTo(new java.math.BigDecimal("10000")));
        assertEquals(0, changed.getIndemnitesApplicables().get(key).montantJour()
                .compareTo(new java.math.BigDecimal("20000")));
        assertFalse(first.getIndemnitesApplicables().containsKey(key));
        assertFalse(expired.getIndemnitesApplicables().containsKey(key));
        assertFalse(expired.getIndemnitesProposees().containsKey(key));
    }

    @BeforeEach
    void setUp() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            try (var verification = c.createStatement(); var database = verification.executeQuery("SELECT current_database()")) {
                assertTrue(database.next());
                assertEquals("sirh_galaxam_test", database.getString(1));
            }
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
            contractId = CongeTestFixtureHelper.createContratActif(c, employeId, LocalDate.of(2025, 1, 1));
            initialAssignmentId = CongeTestFixtureHelper.createAffectation(c, employeId, contractId, LocalDate.of(2025, 1, 1), new BigDecimal("1000000"));

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
                    "SELECT id_periode_paie FROM periode_paie WHERE code = '2026-09-T' LIMIT 1")) {
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
            if (autonomousAllowanceId > 0) {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM site_indemnite_config WHERE id_indemnite = ?")) {
                    ps.setInt(1, autonomousAllowanceId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM ref_indemnite WHERE id_indemnite = ?")) {
                    ps.setInt(1, autonomousAllowanceId);
                    ps.executeUpdate();
                }
            }
            if (autonomousFunctionId > 0) {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM ref_fonction WHERE id_fonction = ?")) {
                    ps.setInt(1, autonomousFunctionId);
                    ps.executeUpdate();
                }
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

    @Test
    @DisplayName("Le contrôle et le gel conservent les indemnités par défaut des autonomes sans ligne de pointage")
    void testIndemnitesAutonomesSansPointageSontFigeesPourLaPaie() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            String suffix = Long.toString(System.nanoTime(), 36).toUpperCase();
            autonomousAllowanceCode = "AU" + suffix.substring(0, Math.min(8, suffix.length()));
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO ref_fonction (code_fonction, libelle, dispense_pointage, est_actif) "
                            + "VALUES (?, 'Fonction autonome test', TRUE, TRUE) RETURNING id_fonction")) {
                ps.setString(1, "FA" + suffix.substring(0, Math.min(8, suffix.length())));
                try (ResultSet rs = ps.executeQuery()) { rs.next(); autonomousFunctionId = rs.getInt(1); }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE employe_affectation_carriere SET id_site = ?, id_fonction = ? WHERE id_affectation = ?")) {
                ps.setInt(1, activeSiteId);
                ps.setInt(2, autonomousFunctionId);
                ps.setInt(3, initialAssignmentId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO ref_indemnite (code, libelle, imposable_irsa, soumis_cotisation, fractionnable, est_actif) "
                            + "VALUES (?, 'Indemnité autonome test', FALSE, FALSE, FALSE, TRUE) RETURNING id_indemnite")) {
                ps.setString(1, autonomousAllowanceCode);
                try (ResultSet rs = ps.executeQuery()) { rs.next(); autonomousAllowanceId = rs.getInt(1); }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO site_indemnite_config (id_site, id_indemnite, periodicite, montant_reference, date_debut, est_actif) "
                            + "VALUES (?, ?, 'JOURNALIER', 10000, DATE '2026-09-01', TRUE)")) {
                ps.setInt(1, activeSiteId);
                ps.setInt(2, autonomousAllowanceId);
                ps.executeUpdate();
            }

            var summary = pointageSyntheseService.getSynthesePeriode(testPeriodId, null, c).stream()
                    .filter(row -> row.getIdEmploye() == employeId).findFirst().orElseThrow();
            BigDecimal amountFromControl = summary.getMontantsIndemnites().get(autonomousAllowanceCode);
            assertNotNull(amountFromControl);
            assertTrue(amountFromControl.signum() > 0);
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM pointage_jour WHERE id_employe = ? AND date_pointage BETWEEN DATE '2026-09-01' AND DATE '2026-09-30'")) {
                ps.setInt(1, employeId);
                try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); assertEquals(0, rs.getInt(1)); }
            }

            // Une journée enregistrée prévaut sur le défaut autonome du seul jour concerné.
            int pointageId;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO pointage_jour (id_employe, id_periode_paie, id_site, date_pointage, id_statut_jour, "
                            + "total_heures, minutes_retard, heures_nuit, version) "
                            + "VALUES (?, ?, ?, DATE '2026-09-01', "
                            + "(SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = 'DISPENSE_POINTAGE'), "
                            + "8, 0, 0, 1) RETURNING id_pointage")) {
                ps.setInt(1, employeId);
                ps.setInt(2, testPeriodId);
                ps.setInt(3, activeSiteId);
                try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); pointageId = rs.getInt(1); }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO pointage_indemnite_jour (id_pointage, id_indemnite, fraction, quantite, montant_calcule, accorde) "
                            + "VALUES (?, ?, 'JOUR_ENTIER', 0, 0, FALSE)")) {
                ps.setInt(1, pointageId);
                ps.setInt(2, autonomousAllowanceId);
                ps.executeUpdate();
            }
            var summaryWithOneRecordedDay = pointageSyntheseService.getSynthesePeriode(testPeriodId, null, c).stream()
                    .filter(row -> row.getIdEmploye() == employeId).findFirst().orElseThrow();
            BigDecimal amountAfterOneDayOverride = summaryWithOneRecordedDay.getMontantsIndemnites().get(autonomousAllowanceCode);
            assertNotNull(amountAfterOneDayOverride);
            assertEquals(0, new BigDecimal("10000").compareTo(amountFromControl.subtract(amountAfterOneDayOverride)));

            c.setAutoCommit(false);
            pointageSyntheseService.gelerPeriode(testPeriodId, adminUserId, c);
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT si.montant FROM pointage_snapshot_indemnite si "
                            + "JOIN pointage_cumul_periode cp ON cp.id_cumul = si.id_cumul "
                            + "WHERE cp.id_employe = ? AND cp.id_periode_paie = ? AND si.code = ? "
                            + "AND cp.statut_snapshot = 'GELE'")) {
                ps.setInt(1, employeId);
                ps.setInt(2, testPeriodId);
                ps.setString(3, autonomousAllowanceCode);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(0, amountAfterOneDayOverride.compareTo(rs.getBigDecimal(1)));
                }
            }
            c.commit();
        }
    }

    @Test
    @DisplayName("Une catégorie qui change l'éligibilité HS en semaine bloque le gel, sans bloquer une affectation équivalente")
    void testChangementEligibiliteHsEnSemaine() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE employe_affectation_carriere SET date_fin=DATE '2026-09-15' WHERE id_affectation=?")) {
                ps.setInt(1, initialAssignmentId);
                ps.executeUpdate();
            }
            int suivante = CongeTestFixtureHelper.createAffectation(c, employeId, contractId,
                    LocalDate.of(2026, 9, 16), new BigDecimal("1000000"));
            assertTrue(pointageSyntheseService.preflightPeriode(testPeriodId, c)
                    .changementsEligibiliteHs().isEmpty());

            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE employe_affectation_carriere SET id_categorie_professionnelle=2 WHERE id_affectation=?")) {
                ps.setInt(1, suivante);
                ps.executeUpdate();
            }
            var preflight = pointageSyntheseService.preflightPeriode(testPeriodId, c);
            assertFalse(preflight.pret());
            assertTrue(preflight.changementsEligibiliteHs().stream().anyMatch(changement ->
                    changement.idEmploye() == employeId
                            && changement.dateChangement().equals(LocalDate.of(2026, 9, 16))
                            && changement.debutTroncon().equals(LocalDate.of(2026, 9, 14))));
            var error = assertThrows(IllegalStateException.class,
                    () -> pointageSyntheseService.gelerPeriode(testPeriodId, adminUserId, c));
            assertTrue(error.getMessage().contains("POINTAGE_HS_ELIGIBILITY_CHANGE"));
        }
    }

    @Test
    @DisplayName("Préflight : une journée ouvrable non saisie bloque le gel avec une date identifiable")
    void testPreflightJourneesManquantes() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            var preflight = pointageSyntheseService.preflightPeriode(testPeriodId, c);
            assertFalse(preflight.pret());
            assertTrue(preflight.journeesManquantes().stream()
                    .anyMatch(jour -> jour.idEmploye() == employeId
                            && jour.date().equals(LocalDate.of(2026, 9, 1))));
        }
    }

    @Test
    @DisplayName("Une vacation dépassant minuit le dernier jour bloque la validation avant le gel")
    void testPreflightRefuseVacationSurDeuxCampagnes() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO pointage_jour (id_employe,id_periode_paie,id_site,date_pointage,id_statut_jour,"
                            + "heure_entree_matin,heure_entree_matin_effective,heure_sortie_am,horaire_continu_jour,version) "
                            + "VALUES (?,?,?,DATE '2026-09-30',1,TIME '23:00',TIME '23:00',TIME '02:00',TRUE,1)")) {
                ps.setInt(1, employeId); ps.setInt(2, testPeriodId); ps.setInt(3, activeSiteId);
                ps.executeUpdate();
            }
            var preflight = pointageSyntheseService.preflightPeriode(testPeriodId, c);
            assertFalse(preflight.pret());
            assertEquals(1, preflight.vacationsChevauchantes().size());
            assertEquals(LocalDate.of(2026, 10, 1), preflight.vacationsChevauchantes().get(0).dateSuivante());
            var error = assertThrows(IllegalStateException.class,
                    () -> pointageSyntheseService.gelerPeriode(testPeriodId, adminUserId, c));
            assertTrue(error.getMessage().contains("POINTAGE_CROSS_PERIOD_SHIFT"));
        }
    }

    @Test
    @DisplayName("Le gel conserve trois heures de nuit occasionnelle pour 23h à 02h")
    void testGelNuitOccasionnelleSurMinutesReelles() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO pointage_jour (id_employe,id_periode_paie,id_site,date_pointage,id_statut_jour,"
                            + "heure_entree_matin,heure_entree_matin_effective,heure_sortie_am,horaire_continu_jour,version) "
                            + "VALUES (?,?,?,DATE '2026-09-29',1,TIME '23:00',TIME '23:00',TIME '02:00',TRUE,1)")) {
                ps.setInt(1, employeId); ps.setInt(2, testPeriodId); ps.setInt(3, activeSiteId);
                ps.executeUpdate();
            }
            c.setAutoCommit(false);
            pointageSyntheseService.gelerPeriode(testPeriodId, adminUserId, c);
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COALESCE(SUM(b.minutes),0) FROM pointage_snapshot_bucket b "
                            + "JOIN pointage_cumul_periode s ON s.id_cumul=b.id_cumul "
                            + "WHERE s.id_employe=? AND s.id_periode_paie=? AND b.night_work_type='OCCASIONNEL'")) {
                ps.setInt(1, employeId); ps.setInt(2, testPeriodId);
                try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); assertEquals(180, rs.getInt(1)); }
            }
            c.commit();
        }
    }

    @Test
    @DisplayName("La prévisualisation accepte la nuit dans la campagne et refuse le débordement du dernier jour")
    void testPrevisualisationNuitEtFrontiereCampagne() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            var dto = new mg.galaxam.sirh.pointage.api.dto.SaisiePointageJourDTO();
            dto.setIdEmploye(employeId);
            dto.setIdSite(activeSiteId);
            dto.setHoraireContinu(true);
            dto.setHeureEntreeMatin("23:00");
            dto.setHeureSortieAm("02:00");
            dto.setDatePointage("2026-09-29");
            var preview = pointageService.previsualiserPointage(dto, c);
            assertEquals(3.0, preview.getHeuresNuit(), 0.0001);
            dto.setHeureEntreeMatin("00:00");
            dto.setHeureSortieAm("02:00");
            var morningAfterSplit = pointageService.previsualiserPointage(dto, c);
            assertEquals(2.0, morningAfterSplit.getTotalHeures(), 0.0001);
            assertEquals(2.0, morningAfterSplit.getHeuresNuit(), 0.0001);
            dto.setHeureEntreeMatin("23:00");
            dto.setDatePointage("2026-09-30");
            var error = assertThrows(RuntimeException.class,
                    () -> pointageService.previsualiserPointage(dto, c));
            assertTrue(error.getMessage().contains("POINTAGE_CROSS_PERIOD_SHIFT"));
        }
    }

    @Test
    @DisplayName("Préflight : la dispense de pointage s'applique uniquement aux jours de sa fonction")
    void testPreflightDispenseChangePendantPeriode() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            int contrat;
            int affectation;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id_contrat, id_affectation FROM employe_affectation_carriere WHERE id_employe = ?")) {
                ps.setInt(1, employeId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    contrat = rs.getInt(1);
                    affectation = rs.getInt(2);
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE employe_affectation_carriere SET date_fin = '2026-09-15' WHERE id_affectation = ?")) {
                ps.setInt(1, affectation);
                ps.executeUpdate();
            }
            int seconde = CongeTestFixtureHelper.createAffectation(c, employeId, contrat,
                    LocalDate.of(2026, 9, 16), new BigDecimal("1000000"));
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE employe_affectation_carriere SET id_fonction = 1, id_site = ? WHERE id_affectation = ?")) {
                ps.setInt(1, activeSiteId);
                ps.setInt(2, seconde);
                ps.executeUpdate();
            }
            var preflight = pointageSyntheseService.preflightPeriode(testPeriodId, c);
            assertTrue(preflight.journeesManquantes().stream().anyMatch(jour -> jour.idEmploye() == employeId
                    && jour.date().equals(LocalDate.of(2026, 9, 1))));
            assertFalse(preflight.journeesManquantes().stream().anyMatch(jour -> jour.idEmploye() == employeId
                    && jour.date().equals(LocalDate.of(2026, 9, 16))));
        }
    }

    @Test
    @DisplayName("Les KPI heures et indemnités reprennent les totaux de la synthèse complète")
    void testKpisSyntheseAlignesSurToutesLesLignes() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            var synthese = pointageSyntheseService.getSynthesePeriode(testPeriodId, null, c);
            var page = pointageSyntheseService.getSynthesePeriodePaginee(testPeriodId, null, null, null, 1, 10000, c);
            @SuppressWarnings("unchecked")
            Map<String, Object> kpis = (Map<String, Object>) page.get("kpis");

            double heuresAttendues = synthese.stream()
                    .mapToDouble(PointageSyntheseMensuelleDTO::getTotalHeuresTravaillees).sum();
            BigDecimal indemnitesAttendues = synthese.stream()
                    .flatMap(dto -> dto.getMontantsIndemnites().values().stream())
                    .filter(amount -> amount != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            assertEquals(Math.round(heuresAttendues * 100.0) / 100.0,
                    ((Number) kpis.get("totalHeures")).doubleValue(), 0.001);
            assertEquals(0, indemnitesAttendues.compareTo((BigDecimal) kpis.get("totalIndemnites")));
        }
    }

    @Test
    @DisplayName("La synthèse conserve un salarié dont l'affectation valide est historisée après la période")
    void testSyntheseInclutAffectationHistoriseeSurLaPeriode() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE employe_affectation_carriere SET id_statut_rh = " +
                    "(SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'HISTORISE') " +
                    "WHERE id_affectation = ?")) {
                ps.setInt(1, initialAssignmentId);
                assertEquals(1, ps.executeUpdate());
            }

            var synthese = pointageSyntheseService.getSynthesePeriode(testPeriodId, null, c);
            var page = pointageSyntheseService.getSynthesePeriodePaginee(testPeriodId, null, null, null, 1, 100, c);
            @SuppressWarnings("unchecked")
            var pagedRows = (java.util.List<PointageSyntheseMensuelleDTO>) page.get("synthese");

            assertTrue(synthese.stream().anyMatch(row -> row.getIdEmploye() == employeId));
            assertTrue(pagedRows.stream().anyMatch(row -> row.getIdEmploye() == employeId));
        }
    }

    @Test
    @DisplayName("Transmission refusée si une période partiellement saisie contient des journées ouvrables manquantes")
    void testTransmissionRefuseJourneesManquantes() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO pointage_jour (id_employe, id_periode_paie, id_site, date_pointage, " +
                    "id_statut_jour, heure_entree_matin, heure_sortie_matin, heure_entree_am, heure_sortie_am, total_heures) " +
                    "VALUES (?, ?, ?, '2026-09-01', " +
                    "(SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = 'PRESENT'), " +
                    "'08:00', '12:00', '13:00', '17:00', 8)")) {
                    ps.setInt(1, employeId);
                    ps.setInt(2, testPeriodId);
                    ps.setInt(3, activeSiteId);
                    ps.executeUpdate();
                }
                var actions = payrollPeriodWorkflowService.describeActions(testPeriodId, "EN_COURS",
                        new PayrollPeriodUserContext(adminUserId, Set.of("ROLE_ADMIN")), c);
                assertFalse(actions.get("SUBMIT").allowed());
                assertTrue(actions.get("SUBMIT").reasonCodes().contains("POINTAGE_JOURNEES_MANQUANTES"));
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("Paie : validation et sorties bloquées si un salarié inclus n'a pas de bulletin")
    void testValidationPaieExigeTousLesBulletins() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            var actions = payrollPeriodWorkflowService.describeActions(testPeriodId, "VALIDEE",
                    new PayrollPeriodUserContext(adminUserId, Set.of("ROLE_ADMIN")), c);
            assertFalse(actions.get("VALIDATE").allowed());
            assertTrue(actions.get("VALIDATE").reasonCodes().contains("PAYROLL_POPULATION_INCOMPLETE"));
            assertFalse(actions.get("GENERATE_OUTPUT").allowed());
            assertThrows(IllegalStateException.class,
                    () -> payrollService.validerPaiePeriode(testPeriodId, adminUserId, c));
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

    @Test
    @DisplayName("P7: Employé inactif sur la période - la fiche de pointage et la vérification congés n'explosent pas et reflètent l'inactivité")
    void testEmployeInactifSurPeriodeNePlantePas() throws Exception {
        int inactifEmpId;
        try (Connection c = dataSource.getConnection()) {
            String suffix = Long.toString(System.nanoTime(), 36).toUpperCase();
            String matricule = "I_" + suffix.substring(0, Math.min(6, suffix.length()));
            String cinUnique = String.format("%012d", Math.abs(System.nanoTime() % 1_000_000_000_000L));
            try (PreparedStatement psEmp = c.prepareStatement(
                    "INSERT INTO employe (matricule, nom, prenom, sexe, date_naissance, lieu_naissance, " +
                    "cin_numero, cin_date_delivrance, cin_lieu_delivrance, adresse, telephone) " +
                    "VALUES (?, 'INACTIF', 'Collab', 'M', '1990-01-15', 'Antananarivo', ?, '2010-01-15', 'TNR', 'Rue Test', '0340000000') " +
                    "RETURNING id_employe")) {
                psEmp.setString(1, matricule);
                psEmp.setString(2, cinUnique);
                try (ResultSet rsEmp = psEmp.executeQuery()) {
                    rsEmp.next();
                    inactifEmpId = rsEmp.getInt(1);
                }
            }

            int inactifContratId = CongeTestFixtureHelper.createContratActif(c, inactifEmpId, LocalDate.of(2099, 1, 1));
            CongeTestFixtureHelper.createAffectation(c, inactifEmpId, inactifContratId, LocalDate.of(2099, 1, 1), new BigDecimal("1000000"));

            // 1. Vérification que getFichePointageEmploye ne plante pas et retourne le statut INACTIF
            var fiche = pointageEmployeService.getFichePointageEmploye(inactifEmpId, null, null, testPeriodId);
            assertNotNull(fiche, "La fiche de pointage doit être retournée même pour un employé non affecté sur la période");
            assertEquals("INACTIF", fiche.getKpis().get("statutEmployePeriode"), "Le KPI doit indiquer que l'employé est inactif sur la période");
            assertTrue(fiche.getKpis().get("joursPrevus").contains("inactif sur la période"), "L'affichage doit indiquer 0 prévu (inactif sur la période)");
            assertEquals("00h00", fiche.getKpis().get("heuresObjectif"), "L'objectif d'heures doit être 00h00");

            // 2. Vérification que verifierConflitsDemande ne plante pas et indique actifSurPeriode = false
            var resultConge = congeService.verifierConflitsDemande(inactifEmpId, Date.valueOf("2026-08-01"), Date.valueOf("2026-08-05"), false, null, null, c);
            assertNotNull(resultConge);
            assertEquals(false, resultConge.get("actifSurPeriode"), "actifSurPeriode doit valoir false pour un employé sans contrat actif");
            assertNotNull(resultConge.get("alerteInactivite"), "Une alerte d'inactivité claire doit être fournie");

            // 3. Vérification que getFichePointageEmploye sur une date historique (2020) ne plante pas grâce à la baseline 2001
            var ficheHistorique = pointageEmployeService.getFichePointageEmploye(inactifEmpId, "2020-01-15", "mois", null);
            assertNotNull(ficheHistorique);
            assertNotNull(ficheHistorique.getReglesAppliquees().get("seuilAssiduiteMinutes"));
        }
    }
}
