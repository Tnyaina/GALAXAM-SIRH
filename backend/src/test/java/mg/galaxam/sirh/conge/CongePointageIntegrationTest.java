package mg.galaxam.sirh.conge;

import mg.galaxam.sirh.conge.application.CongeService;
import mg.galaxam.sirh.conge.persistence.DemandeConge;
import mg.galaxam.sirh.conge.testutils.CongeTestFixtureHelper;
import mg.galaxam.sirh.pointage.api.dto.SaisiePointageJourDTO;
import mg.galaxam.sirh.pointage.application.PointageService;
import mg.galaxam.sirh.pointage.application.PointageAnomalieService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class CongePointageIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private CongeService congeService;

    @Autowired
    private PointageService pointageSaisieService;

    @Autowired
    private PointageAnomalieService pointageAnomalieService;

    private int adminUserId;
    private int employeId;
    private int typeAnnuelId;
    private int typeExceptionnelId;
    private int typeSansSoldeId;
    private int motifMariageId;
    private Integer testDynamicAllowanceId;
    private Integer testDynamicPointageId;

    @AfterEach
    void cleanDynamicAllowanceFixture() throws Exception {
        if (testDynamicAllowanceId == null) return;
        try (Connection c = dataSource.getConnection()) {
            if (testDynamicPointageId != null) {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM pointage_jour WHERE id_pointage = ?")) {
                    ps.setInt(1, testDynamicPointageId);
                    ps.executeUpdate();
                }
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM site_indemnite_config WHERE id_indemnite = ?")) {
                ps.setInt(1, testDynamicAllowanceId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM ref_indemnite WHERE id_indemnite = ?")) {
                ps.setInt(1, testDynamicAllowanceId);
                ps.executeUpdate();
            }
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            CongeTestFixtureHelper.cleanDatabase(c);
            adminUserId = CongeTestFixtureHelper.createAdminUser(c, "admin.pointage@galaxam.mg");
            employeId = CongeTestFixtureHelper.createEmploye(c, "EMP-PTG-001", "Rasoa", "Bakoly");
            int contratId = CongeTestFixtureHelper.createContratActif(c, employeId, LocalDate.of(2025, 1, 1));
            CongeTestFixtureHelper.createAffectation(c, employeId, contratId, LocalDate.of(2025, 1, 1), new BigDecimal("1200000.00"));

            typeAnnuelId = CongeTestFixtureHelper.getTypeCongeId(c, "ANNUEL");
            typeExceptionnelId = CongeTestFixtureHelper.getTypeCongeId(c, "EXCEPTIONNEL");
            typeSansSoldeId = CongeTestFixtureHelper.getTypeCongeId(c, "SANS_SOLDE");
            motifMariageId = CongeTestFixtureHelper.getMotifEvenementFamilialId(c, "MARIAGE");

            // Créer une période de paie ouverte pour le mois de septembre 2026
            CongeTestFixtureHelper.createPeriodePaie(c, "2026-09", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), "EN_COURS");
        }
    }

    @Test
    @DisplayName("Absence justifiée : le motif seul laisse à vérifier, la décision explicite clôt le suivi")
    void absenceJustifieeAttendDecisionExplicite() throws Exception {
        int periodId;
        int pointageId;
        Date day = Date.valueOf("2026-09-15");
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT id_periode_paie FROM periode_paie WHERE code = '2026-09'")) {
                try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); periodId = rs.getInt(1); }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO pointage_jour (id_employe, id_periode_paie, id_site, date_pointage, id_statut_jour, motif_ajustement) " +
                    "VALUES (?, ?, 1, ?, (SELECT id_statut_jour FROM ref_statut_pointage_jour " +
                    "WHERE code = 'ABSENCE_JUSTIFIEE'), ?) RETURNING id_pointage")) {
                ps.setInt(1, employeId); ps.setInt(2, periodId); ps.setDate(3, day);
                ps.setString(4, "Justificatif remis");
                try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); pointageId = rs.getInt(1); }
            }
        }
        @SuppressWarnings("unchecked")
        List<mg.galaxam.sirh.pointage.api.dto.PointageAnomalieDTO> before =
                (List<mg.galaxam.sirh.pointage.api.dto.PointageAnomalieDTO>)
                        pointageAnomalieService.getAnomalies(1, periodId, null, null).get("anomalies");
        var pending = before.stream().filter(a -> a.getIdPointage() == pointageId).findFirst().orElseThrow();
        assertEquals("todo", pending.getStatutAnomalie());
        assertTrue(pending.isBloquantTransmission());

        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("version", 1);
        payload.put("idEmploye", employeId);
        payload.put("idPeriodePaie", periodId);
        payload.put("idSite", 1);
        payload.put("datePointage", day.toString());
        payload.put("motifAjustement", "Absence justifiée confirmée sans congé");
        payload.put("typeRegularisation", "ABSENCE");
        payload.put("optionAbsence", "ABSENCE_DEDUITE");
        payload.put("statutCode", "ABSENCE_JUSTIFIEE");
        assertTrue(pointageAnomalieService.regulariserPointage(pointageId, payload));
        @SuppressWarnings("unchecked")
        List<mg.galaxam.sirh.pointage.api.dto.PointageAnomalieDTO> after =
                (List<mg.galaxam.sirh.pointage.api.dto.PointageAnomalieDTO>)
                        pointageAnomalieService.getAnomalies(1, periodId, null, null).get("anomalies");
        var confirmed = after.stream().filter(a -> a.getIdPointage() == pointageId).findFirst().orElseThrow();
        assertEquals("done", confirmed.getStatutAnomalie());
        assertFalse(confirmed.isBloquantTransmission());

        try (Connection c = dataSource.getConnection()) {
            CongeTestFixtureHelper.crediterSolde(c, employeId, 2.0, "Droit pour convertir l'absence justifiée");
        }
        payload.put("version", 2);
        payload.put("motifAjustement", "Congé annuel confirmé après vérification");
        payload.put("optionAbsence", "CONGE_PAYE");
        payload.put("statutCode", "CONGE");
        payload.put("dateReprise", "2026-09-16");
        assertTrue(pointageAnomalieService.regulariserPointage(pointageId, payload));
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT s.code, p.id_demande_conge FROM pointage_jour p " +
                "JOIN ref_statut_pointage_jour s ON s.id_statut_jour = p.id_statut_jour WHERE p.id_pointage = ?")) {
            ps.setInt(1, pointageId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("CONGE", rs.getString(1));
                assertNotNull(rs.getObject(2));
            }
        }
    }

    @Test
    @DisplayName("Régularisation vendredi avec reprise lundi : trois jours débités et aucun pointage de repos créé")
    void regularisationVendrediRepriseLundi() throws Exception {
        int periodId;
        int pointageId;
        Date friday = Date.valueOf("2026-09-18");
        try (Connection c = dataSource.getConnection()) {
            CongeTestFixtureHelper.crediterSolde(c, employeId, 5.0, "Droit pour congé calendaire");
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id_periode_paie FROM periode_paie WHERE code = '2026-09'")) {
                try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); periodId = rs.getInt(1); }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO pointage_jour (id_employe, id_periode_paie, id_site, date_pointage, id_statut_jour) " +
                    "VALUES (?, ?, 1, ?, (SELECT id_statut_jour FROM ref_statut_pointage_jour " +
                    "WHERE code = 'ABSENCE_NON_JUSTIFIEE')) RETURNING id_pointage")) {
                ps.setInt(1, employeId);
                ps.setInt(2, periodId);
                ps.setDate(3, friday);
                try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); pointageId = rs.getInt(1); }
            }
        }
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("version", 1);
        payload.put("idEmploye", employeId);
        payload.put("idPeriodePaie", periodId);
        payload.put("idSite", 1);
        payload.put("datePointage", friday.toString());
        payload.put("dateReprise", "2026-09-21");
        payload.put("motifAjustement", "Congé du vendredi au dimanche");
        payload.put("typeRegularisation", "ABSENCE");
        payload.put("optionAbsence", "CONGE_PAYE");
        payload.put("statutCode", "CONGE");
        assertTrue(pointageAnomalieService.regulariserPointage(pointageId, payload));
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT d.date_debut, d.date_fin, d.nb_jours_decomptes, m.nb_jours " +
                "FROM pointage_jour p JOIN demande_conge d ON d.id_demande_conge = p.id_demande_conge " +
                "JOIN mouvement_solde_conge m ON m.id_demande_conge = d.id_demande_conge AND m.type_mouvement = 'PRISE' " +
                "WHERE p.id_pointage = ?")) {
            ps.setInt(1, pointageId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(friday, rs.getDate(1));
                assertEquals(Date.valueOf("2026-09-20"), rs.getDate(2));
                assertEquals(0, new BigDecimal("3.00").compareTo(rs.getBigDecimal(3)));
                assertEquals(0, new BigDecimal("-3.00").compareTo(rs.getBigDecimal(4)));
            }
        }
    }

    @Test
    @DisplayName("Régularisation calendaire refusée intégralement si le solde ne couvre pas le week-end")
    void regularisationVendrediSoldeInsuffisant() throws Exception {
        int periodId;
        int pointageId;
        Date friday = Date.valueOf("2026-09-18");
        try (Connection c = dataSource.getConnection()) {
            CongeTestFixtureHelper.crediterSolde(c, employeId, 2.0, "Solde insuffisant pour vendredi-dimanche");
            try (PreparedStatement ps = c.prepareStatement("SELECT id_periode_paie FROM periode_paie WHERE code = '2026-09'")) {
                try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); periodId = rs.getInt(1); }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO pointage_jour (id_employe, id_periode_paie, id_site, date_pointage, id_statut_jour) " +
                    "VALUES (?, ?, 1, ?, (SELECT id_statut_jour FROM ref_statut_pointage_jour " +
                    "WHERE code = 'ABSENCE_NON_JUSTIFIEE')) RETURNING id_pointage")) {
                ps.setInt(1, employeId); ps.setInt(2, periodId); ps.setDate(3, friday);
                try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); pointageId = rs.getInt(1); }
            }
        }
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("version", 1);
        payload.put("idEmploye", employeId);
        payload.put("idPeriodePaie", periodId);
        payload.put("idSite", 1);
        payload.put("datePointage", friday.toString());
        payload.put("dateReprise", "2026-09-21");
        payload.put("typeRegularisation", "ABSENCE");
        payload.put("optionAbsence", "CONGE_PAYE");
        payload.put("statutCode", "CONGE");
        assertThrows(IllegalArgumentException.class, () -> pointageAnomalieService.regulariserPointage(pointageId, payload));
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT s.code, p.id_demande_conge FROM pointage_jour p " +
                "JOIN ref_statut_pointage_jour s ON s.id_statut_jour = p.id_statut_jour WHERE p.id_pointage = ?")) {
            ps.setInt(1, pointageId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("ABSENCE_NON_JUSTIFIEE", rs.getString(1));
                assertNull(rs.getObject(2));
            }
        }
    }

    @Test
    @DisplayName("Régularisation sur trois jours ouvrables : une demande et trois pointages liés")
    void regularisationPlusieursJoursOuvrables() throws Exception {
        int periodId;
        int pointageId;
        Date monday = Date.valueOf("2026-09-14");
        try (Connection c = dataSource.getConnection()) {
            CongeTestFixtureHelper.crediterSolde(c, employeId, 5.0, "Droit pour trois jours");
            try (PreparedStatement ps = c.prepareStatement("SELECT id_periode_paie FROM periode_paie WHERE code = '2026-09'")) {
                try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); periodId = rs.getInt(1); }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO pointage_jour (id_employe, id_periode_paie, id_site, date_pointage, id_statut_jour) " +
                    "VALUES (?, ?, 1, ?, (SELECT id_statut_jour FROM ref_statut_pointage_jour " +
                    "WHERE code = 'ABSENCE_NON_JUSTIFIEE')) RETURNING id_pointage")) {
                ps.setInt(1, employeId); ps.setInt(2, periodId); ps.setDate(3, monday);
                try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); pointageId = rs.getInt(1); }
            }
        }
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("version", 1);
        payload.put("idEmploye", employeId);
        payload.put("idPeriodePaie", periodId);
        payload.put("idSite", 1);
        payload.put("datePointage", monday.toString());
        payload.put("dateReprise", "2026-09-17");
        payload.put("motifAjustement", "Congé régularisé sur trois jours");
        payload.put("typeRegularisation", "ABSENCE");
        payload.put("optionAbsence", "CONGE_PAYE");
        payload.put("statutCode", "CONGE");
        assertTrue(pointageAnomalieService.regulariserPointage(pointageId, payload));
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*), COUNT(DISTINCT p.id_demande_conge) FROM pointage_jour p " +
                "JOIN ref_statut_pointage_jour s ON s.id_statut_jour = p.id_statut_jour " +
                "WHERE p.id_employe = ? AND p.date_pointage BETWEEN ? AND ? AND s.code = 'CONGE'")) {
            ps.setInt(1, employeId);
            ps.setDate(2, monday);
            ps.setDate(3, Date.valueOf("2026-09-16"));
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(3, rs.getInt(1));
                assertEquals(1, rs.getInt(2));
            }
        }
        int demandeId;
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT id_demande_conge FROM pointage_jour WHERE id_employe = ? AND date_pointage = ?")) {
            ps.setInt(1, employeId); ps.setDate(2, monday);
            try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); demandeId = rs.getInt(1); }
        }
        try (Connection c = dataSource.getConnection()) {
            congeService.annulerDemande(demandeId, adminUserId, "Annulation de la régularisation", c);
        }
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM pointage_jour p JOIN ref_statut_pointage_jour s ON s.id_statut_jour = p.id_statut_jour " +
                "WHERE p.id_employe = ? AND p.date_pointage BETWEEN ? AND ? " +
                "AND p.id_demande_conge IS NULL AND s.code = 'ABSENCE_NON_JUSTIFIEE'")) {
            ps.setInt(1, employeId); ps.setDate(2, monday); ps.setDate(3, Date.valueOf("2026-09-16"));
            try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); assertEquals(3, rs.getInt(1)); }
        }
    }

    @Test
    @DisplayName("21.A Bridge Réel Congé Annuel (decompte_solde_acquis = true) -> pointage_jour statut CONGE")
    void testBridgePointageCongeAnnuel() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            CongeTestFixtureHelper.crediterSolde(c, employeId, 10.0, "Solde 10j");

            LocalDate dateConge = LocalDate.of(2026, 9, 15);

            DemandeConge d = new DemandeConge();
            d.setIdEmploye(employeId);
            d.setIdTypeConge(typeAnnuelId);
            d.setDateDebut(Date.valueOf(dateConge));
            d.setDateFin(Date.valueOf(dateConge));
            d.setDerogationDelaiPreavis(true);
            DemandeConge cree = congeService.creerDemande(d, adminUserId, c);
            congeService.validerDemande(cree.getIdDemandeConge(), adminUserId, "Validation congé annuel", c);

            // Appel du service Pointage réel pour générer la grille de saisie
            Map<String, Object> grille = pointageSaisieService.getGrilleSaisie(1, dateConge.toString(), c);
            assertNotNull(grille);
            @SuppressWarnings("unchecked")
            List<SaisiePointageJourDTO> lignes = (List<SaisiePointageJourDTO>) grille.get("grilleData");
            assertNotNull(lignes);

            SaisiePointageJourDTO pointageEmp = lignes.stream()
                    .filter(p -> p.getIdEmploye() == employeId)
                    .findFirst()
                    .orElse(null);
            assertNotNull(pointageEmp, "Le collaborateur doit être présent dans la grille de saisie");
            assertEquals("CONGE", pointageEmp.getCodeStatut());
            assertTrue(pointageEmp.isCongeDecompteSolde());
            assertTrue(pointageEmp.isCongePaye());

            // Enregistrement effectif du pointage en base
            pointageSaisieService.enregistrerPointagesBatch(List.of(pointageEmp), c);

            // Vérification dans la table réelle pointage_jour
            String sqlCheck = "SELECT pj.total_heures, spj.code FROM pointage_jour pj " +
                    "JOIN ref_statut_pointage_jour spj ON pj.id_statut_jour = spj.id_statut_jour " +
                    "WHERE pj.id_employe = ? AND pj.date_pointage = ?";
            try (PreparedStatement ps = c.prepareStatement(sqlCheck)) {
                ps.setInt(1, employeId);
                ps.setDate(2, Date.valueOf(dateConge));
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("CONGE", rs.getString("code"));
                    assertEquals(0, new BigDecimal("0.0").compareTo(rs.getBigDecimal("total_heures")));
                }
            }
        }
    }

    @Test
    @DisplayName("21.B Bridge Réel Événement Familial (decompte_solde_acquis = false + payé) -> pointage_jour statut DISPENSE_POINTAGE")
    void testBridgePointageEvenementFamilial() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            LocalDate dateConge = LocalDate.of(2026, 9, 16);

            DemandeConge d = new DemandeConge();
            d.setIdEmploye(employeId);
            d.setIdTypeConge(typeExceptionnelId);
            d.setIdMotifEvenementFamilial(motifMariageId);
            d.setDateDebut(Date.valueOf(dateConge));
            d.setDateFin(Date.valueOf(dateConge));
            d.setDerogationDelaiPreavis(true);
            DemandeConge cree = congeService.creerDemande(d, adminUserId, c);
            congeService.validerDemande(cree.getIdDemandeConge(), adminUserId, "Validation mariage", c);

            Map<String, Object> grille = pointageSaisieService.getGrilleSaisie(1, dateConge.toString(), c);
            @SuppressWarnings("unchecked")
            List<SaisiePointageJourDTO> lignes = (List<SaisiePointageJourDTO>) grille.get("grilleData");
            SaisiePointageJourDTO pointageEmp = lignes.stream()
                    .filter(p -> p.getIdEmploye() == employeId)
                    .findFirst()
                    .orElse(null);

            assertNotNull(pointageEmp);
            assertEquals("DISPENSE_POINTAGE", pointageEmp.getCodeStatut());
            assertFalse(pointageEmp.isCongeDecompteSolde());
            assertTrue(pointageEmp.isCongePaye());

            assertEquals(8.0, pointageEmp.getTotalHeures()); // Dans la grille de saisie DTO, 8h forfaitaires payées
            pointageSaisieService.enregistrerPointagesBatch(List.of(pointageEmp), c);

            String sqlCheck = "SELECT pj.total_heures, spj.code FROM pointage_jour pj " +
                    "JOIN ref_statut_pointage_jour spj ON pj.id_statut_jour = spj.id_statut_jour " +
                    "WHERE pj.id_employe = ? AND pj.date_pointage = ?";
            try (PreparedStatement ps = c.prepareStatement(sqlCheck)) {
                ps.setInt(1, employeId);
                ps.setDate(2, Date.valueOf(dateConge));
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("DISPENSE_POINTAGE", rs.getString("code"));
                    // En BDD, total_heures pointe à 0.0 (car aucune heure travaillée physiquement sur site)
                    assertEquals(0, new BigDecimal("0.0").compareTo(rs.getBigDecimal("total_heures")));
                }
            }
        }
    }

    @Test
    @DisplayName("21.C Bridge Réel Sans Solde (est_paye = false) -> pointage_jour statut ABSENCE_JUSTIFIEE")
    void testBridgePointageSansSolde() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            LocalDate dateConge = LocalDate.of(2026, 9, 17);

            DemandeConge d = new DemandeConge();
            d.setIdEmploye(employeId);
            d.setIdTypeConge(typeSansSoldeId);
            d.setDateDebut(Date.valueOf(dateConge));
            d.setDateFin(Date.valueOf(dateConge));
            d.setDerogationDelaiPreavis(true);
            DemandeConge cree = congeService.creerDemande(d, adminUserId, c);
            congeService.validerDemande(cree.getIdDemandeConge(), adminUserId, "Validation sans solde", c);

            Map<String, Object> grille = pointageSaisieService.getGrilleSaisie(1, dateConge.toString(), c);
            @SuppressWarnings("unchecked")
            List<SaisiePointageJourDTO> lignes = (List<SaisiePointageJourDTO>) grille.get("grilleData");
            SaisiePointageJourDTO pointageEmp = lignes.stream()
                    .filter(p -> p.getIdEmploye() == employeId)
                    .findFirst()
                    .orElse(null);

            assertNotNull(pointageEmp);
            assertEquals("ABSENCE_JUSTIFIEE", pointageEmp.getCodeStatut());
            assertFalse(pointageEmp.isCongePaye());

            pointageSaisieService.enregistrerPointagesBatch(List.of(pointageEmp), c);

            String sqlCheck = "SELECT pj.total_heures, spj.code FROM pointage_jour pj " +
                    "JOIN ref_statut_pointage_jour spj ON pj.id_statut_jour = spj.id_statut_jour " +
                    "WHERE pj.id_employe = ? AND pj.date_pointage = ?";
            try (PreparedStatement ps = c.prepareStatement(sqlCheck)) {
                ps.setInt(1, employeId);
                ps.setDate(2, Date.valueOf(dateConge));
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("ABSENCE_JUSTIFIEE", rs.getString("code"));
                    assertEquals(0, new BigDecimal("0.0").compareTo(rs.getBigDecimal("total_heures")));
                }
            }
        }
        @SuppressWarnings("unchecked")
        List<mg.galaxam.sirh.pointage.api.dto.PointageAnomalieDTO> anomalies =
                (List<mg.galaxam.sirh.pointage.api.dto.PointageAnomalieDTO>)
                        pointageAnomalieService.getAnomalies(1, null, "2026-09-17", null).get("anomalies");
        var absenceValidee = anomalies.stream()
                .filter(a -> a.getIdEmploye() == employeId && "ABSENCE_JUSTIFIEE".equals(a.getStatutCode()))
                .findFirst().orElseThrow();
        assertEquals("done", absenceValidee.getStatutAnomalie());
        assertFalse(absenceValidee.isBloquantTransmission());
    }

    @Test
    @DisplayName("Régularisation Pointage par congé annuel : validation Congés et mouvement PRISE atomiques")
    void regularisationPointageParCongeValideAvecDroitDisponible() throws Exception {
        LocalDate dateAbsence = LocalDate.of(2026, 9, 21);
        try (Connection c = dataSource.getConnection()) {
            CongeTestFixtureHelper.crediterSolde(c, employeId, 1.0, "Droit pour régularisation Pointage");
            c.setAutoCommit(false);
            congeService.regulariserCongeAnnuelDepuisPointage(employeId, Date.valueOf(dateAbsence),
                    0.5, "APRES_MIDI", "Absence autorisée", adminUserId, c);
            c.commit();

            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT d.est_demi_journee, d.moment_demi_journee, s.code " +
                    "FROM demande_conge d JOIN ref_statut_demande_conge s " +
                    "ON s.id_statut_demande_conge = d.id_statut_demande_conge " +
                    "WHERE d.id_employe = ? AND d.date_debut = ?")) {
                ps.setInt(1, employeId);
                ps.setDate(2, Date.valueOf(dateAbsence));
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertTrue(rs.getBoolean("est_demi_journee"));
                    assertEquals("APRES_MIDI", rs.getString("moment_demi_journee"));
                    assertEquals("VALIDE", rs.getString("code"));
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM mouvement_solde_conge m JOIN demande_conge d " +
                    "ON d.id_demande_conge = m.id_demande_conge " +
                    "WHERE d.id_employe = ? AND d.date_debut = ? AND m.type_mouvement = 'PRISE' AND m.nb_jours = -0.5")) {
                ps.setInt(1, employeId);
                ps.setDate(2, Date.valueOf(dateAbsence));
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1));
                }
            }
        }
    }

    @Test
    @DisplayName("Régularisation Pointage par congé annuel : solde insuffisant refuse sans écriture")
    void regularisationPointageParCongeRefuseSiSoldeInsuffisant() throws Exception {
        LocalDate dateAbsence = LocalDate.of(2026, 9, 22);
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            assertThrows(IllegalArgumentException.class, () ->
                    congeService.regulariserCongeAnnuelDepuisPointage(employeId, Date.valueOf(dateAbsence),
                            1.0, null, "Absence autorisée", adminUserId, c));
            c.rollback();

            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM demande_conge WHERE id_employe = ? AND date_debut = ?")) {
                ps.setInt(1, employeId);
                ps.setDate(2, Date.valueOf(dateAbsence));
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(0, rs.getInt(1));
                }
            }
        }
    }

    @Test
    @DisplayName("Régularisation d'une permission oubliée via la validation Congés")
    void regularisationPermissionOublieeValideeSansInsertDirect() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            Date date = Date.valueOf(LocalDate.of(2026, 9, 18));
            congeService.regulariserPermissionDepuisPointage(employeId, date,
                    "Permission déjà accordée", adminUserId, c);
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT s.code FROM demande_conge d "
                            + "JOIN ref_statut_demande_conge s ON s.id_statut_demande_conge = d.id_statut_demande_conge "
                            + "WHERE d.id_employe = ? AND d.date_debut = ?")) {
                ps.setInt(1, employeId);
                ps.setDate(2, date);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("VALIDE", rs.getString(1));
                }
            }
        }
    }

    @Test
    @DisplayName("Correction d'horaires : nouvelle indemnité résolue par catalogue et tarif site")
    void regularisationHorairesAvecIndemniteDynamique() throws Exception {
        int pointageId;
        int allowanceId;
        int periodId;
        Date date = Date.valueOf(LocalDate.of(2026, 9, 17));
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id_periode_paie FROM periode_paie WHERE code = '2026-09'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    periodId = rs.getInt(1);
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO ref_indemnite (code, libelle, imposable_irsa, soumis_cotisation) "
                            + "VALUES (?, 'Indemnité dynamique', FALSE, FALSE) RETURNING id_indemnite")) {
                ps.setString(1, "TD" + Long.toString(System.nanoTime(), 36));
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    allowanceId = rs.getInt(1);
                    testDynamicAllowanceId = allowanceId;
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO site_indemnite_config (id_site, id_indemnite, periodicite, montant_reference, date_debut) "
                            + "VALUES (1, ?, 'JOURNALIER', 5000, '2026-01-01')")) {
                ps.setInt(1, allowanceId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO pointage_jour (id_employe, id_periode_paie, id_site, date_pointage, id_statut_jour) "
                            + "VALUES (?, ?, 1, ?, (SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = 'PRESENT')) "
                            + "RETURNING id_pointage")) {
                ps.setInt(1, employeId);
                ps.setInt(2, periodId);
                ps.setDate(3, date);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    pointageId = rs.getInt(1);
                    testDynamicPointageId = pointageId;
                }
            }
        }
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("version", 1);
        payload.put("idEmploye", employeId);
        payload.put("idPeriodePaie", periodId);
        payload.put("idSite", 1);
        payload.put("datePointage", date.toString());
        payload.put("motifAjustement", "Horaire oublié");
        payload.put("typeRegularisation", "HEURES");
        payload.put("statutCode", "PRESENT");
        payload.put("heureEntreeMatin", "08:00");
        payload.put("heureSortieMatin", "12:00");
        payload.put("heureEntreeAm", "13:00");
        payload.put("heureSortieAm", "17:00");
        payload.put("dynamicIndemnites", Map.of(String.valueOf(allowanceId), Map.of("accordee", true)));
        assertTrue(pointageAnomalieService.regulariserPointage(pointageId, payload));
        assertThrows(IllegalStateException.class,
                () -> pointageAnomalieService.regulariserPointage(pointageId, payload),
                "Une seconde correction avec la version périmée doit être refusée.");
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT montant_calcule FROM pointage_indemnite_jour WHERE id_pointage = ? AND id_indemnite = ? AND accorde = TRUE")) {
            ps.setInt(1, pointageId);
            ps.setInt(2, allowanceId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(0, new BigDecimal("5000.00").compareTo(rs.getBigDecimal(1)));
            }
        }
    }

    @Test
    @DisplayName("Deuxième repas automatique indépendant du premier et retiré après correction horaire")
    void secondMealIsDerivedFromExitWithoutFirstMeal() throws Exception {
        int pointageId;
        int periodId;
        Date date = Date.valueOf(LocalDate.of(2026, 9, 17));
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement period = c.prepareStatement(
                    "SELECT id_periode_paie FROM periode_paie WHERE code = '2026-09'");
                 ResultSet result = period.executeQuery()) {
                assertTrue(result.next());
                periodId = result.getInt(1);
            }
            try (PreparedStatement insert = c.prepareStatement(
                    "INSERT INTO pointage_jour (id_employe, id_periode_paie, id_site, date_pointage, id_statut_jour) "
                            + "VALUES (?, ?, 1, ?, (SELECT id_statut_jour FROM ref_statut_pointage_jour "
                            + "WHERE code = 'PRESENT')) RETURNING id_pointage")) {
                insert.setInt(1, employeId);
                insert.setInt(2, periodId);
                insert.setDate(3, date);
                try (ResultSet result = insert.executeQuery()) {
                    assertTrue(result.next());
                    pointageId = result.getInt(1);
                }
            }
        }
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("version", 1);
        payload.put("idEmploye", employeId);
        payload.put("idPeriodePaie", periodId);
        payload.put("idSite", 1);
        payload.put("datePointage", date.toString());
        payload.put("motifAjustement", "Correction horaire");
        payload.put("typeRegularisation", "HEURES");
        payload.put("statutCode", "PRESENT");
        payload.put("heureEntreeMatin", "08:00");
        payload.put("heureSortieMatin", "12:00");
        payload.put("heureEntreeAm", "13:00");
        payload.put("heureSortieAm", "21:00");
        payload.put("dynamicIndemnites", Map.of());
        assertTrue(pointageAnomalieService.regulariserPointage(pointageId, payload));
        try (Connection c = dataSource.getConnection(); PreparedStatement check = c.prepareStatement(
                "SELECT p.code_second_repas, p.montant_second_repas, pij.quantite, pij.montant_calcule "
                        + "FROM pointage_jour p JOIN pointage_indemnite_jour pij ON pij.id_pointage = p.id_pointage "
                        + "JOIN ref_indemnite i ON i.id_indemnite = pij.id_indemnite AND i.code = 'REPAS' "
                        + "WHERE p.id_pointage = ?")) {
            check.setInt(1, pointageId);
            try (ResultSet result = check.executeQuery()) {
                assertTrue(result.next());
                assertEquals("REPAS_2_MAJORE", result.getString(1));
                assertEquals(0, new BigDecimal("4000").compareTo(result.getBigDecimal(2)));
                assertEquals(0, BigDecimal.ZERO.compareTo(result.getBigDecimal(3)));
                assertEquals(0, new BigDecimal("4000").compareTo(result.getBigDecimal(4)));
            }
        }
        payload.put("version", 2);
        payload.put("heureSortieAm", "18:00");
        assertTrue(pointageAnomalieService.regulariserPointage(pointageId, payload));
        try (Connection c = dataSource.getConnection(); PreparedStatement check = c.prepareStatement(
                "SELECT code_second_repas, montant_second_repas FROM pointage_jour WHERE id_pointage = ?")) {
            check.setInt(1, pointageId);
            try (ResultSet result = check.executeQuery()) {
                assertTrue(result.next());
                assertNull(result.getString(1));
                assertEquals(0, BigDecimal.ZERO.compareTo(result.getBigDecimal(2)));
            }
        }
    }

    @Test
    @DisplayName("Congé oublié : demande validée liée à la journée et mouvement PRISE dans la même correction")
    void regularisationAbsenceLieCongesEtPointage() throws Exception {
        int pointageId;
        int periodId;
        Date date = Date.valueOf(LocalDate.of(2026, 9, 16));
        try (Connection c = dataSource.getConnection()) {
            CongeTestFixtureHelper.crediterSolde(c, employeId, 2.0, "Droit pour congé oublié");
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id_periode_paie FROM periode_paie WHERE code = '2026-09'");
                 ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                periodId = rs.getInt(1);
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO pointage_jour (id_employe, id_periode_paie, id_site, date_pointage, id_statut_jour) "
                            + "VALUES (?, ?, 1, ?, (SELECT id_statut_jour FROM ref_statut_pointage_jour "
                            + "WHERE code = 'ABSENCE_NON_JUSTIFIEE')) RETURNING id_pointage")) {
                ps.setInt(1, employeId);
                ps.setInt(2, periodId);
                ps.setDate(3, date);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    pointageId = rs.getInt(1);
                }
            }
        }
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("version", 1);
        payload.put("idEmploye", employeId);
        payload.put("idPeriodePaie", periodId);
        payload.put("idSite", 1);
        payload.put("datePointage", date.toString());
        payload.put("motifAjustement", "Congé déjà approuvé, oublié lors de la saisie");
        payload.put("typeRegularisation", "ABSENCE");
        payload.put("optionAbsence", "CONGE_PAYE");
        payload.put("statutCode", "CONGE");
        payload.put("nbJoursCongeDecompte", 1.0);
        assertTrue(pointageAnomalieService.regulariserPointage(pointageId, payload));
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT s.code, d.id_demande_conge, sd.code AS statut_conge, "
                        + "(SELECT COUNT(*) FROM mouvement_solde_conge m WHERE m.id_demande_conge = d.id_demande_conge "
                        + "AND m.type_mouvement = 'PRISE') AS prises "
                        + "FROM pointage_jour p JOIN ref_statut_pointage_jour s ON s.id_statut_jour = p.id_statut_jour "
                        + "JOIN demande_conge d ON d.id_demande_conge = p.id_demande_conge "
                        + "JOIN ref_statut_demande_conge sd ON sd.id_statut_demande_conge = d.id_statut_demande_conge "
                        + "WHERE p.id_pointage = ?")) {
            ps.setInt(1, pointageId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("CONGE", rs.getString("code"));
                assertEquals("VALIDE", rs.getString("statut_conge"));
                assertEquals(1, rs.getInt("prises"));
            }
        }
    }

    @Test
    @DisplayName("Une période Pointage validée interdit la correction directe avant réouverture")
    void correctionDirecteRefuseeApresGel() throws Exception {
        int periodId;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE periode_paie SET id_statut_periode = "
                             + "(SELECT id_statut_periode FROM ref_statut_periode_paie WHERE code = 'VALIDEE') "
                             + "WHERE code = '2026-09' RETURNING id_periode_paie")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                periodId = rs.getInt(1);
            }
        }
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("idEmploye", employeId);
        payload.put("idPeriodePaie", periodId);
        payload.put("idSite", 1);
        payload.put("datePointage", "2026-09-17");
        payload.put("motifAjustement", "Correction tardive");
        payload.put("typeRegularisation", "HEURES");
        payload.put("heureEntreeMatin", "08:00");
        payload.put("heureSortieMatin", "12:00");
        payload.put("heureEntreeAm", "13:00");
        payload.put("heureSortieAm", "17:00");
        payload.put("dynamicIndemnites", Map.of());
        assertThrows(IllegalStateException.class,
                () -> pointageAnomalieService.regulariserPointage(0, payload));
    }
}
