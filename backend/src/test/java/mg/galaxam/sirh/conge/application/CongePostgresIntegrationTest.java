package mg.galaxam.sirh.conge.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** PostgreSQL integration checks. Point only at an isolated disposable database. */
class CongePostgresIntegrationTest {
    private static final String URL = System.getenv("GALAXAM_CONGE_TEST_DB_URL");
    private static final String USER = System.getenv("GALAXAM_CONGE_TEST_DB_USER");
    private static final String PASSWORD = System.getenv("GALAXAM_CONGE_TEST_DB_PASSWORD");

    private static BigDecimal d(String value) { return new BigDecimal(value); }
    private static void decimal(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, d(expected).compareTo(actual));
    }

    @Test
    void reprisePersisteSnapshotAgregeEtConstruitUnLotSansConfondreTaux() throws Exception {
        assumeDatabaseConfigured();
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try {
                int employee = insertEmployee(c);
                var request = opening(employee, LocalDate.of(2026, 6, 15), "5", "70000", "12000", "15", "180000");
                int reference = new CongeTauxApplicableService().enregistrerCutoverSnapshot(request, null, c);

                var situation = new CongeLotValorisationService().situation(employee,
                        LocalDate.of(2026, 6, 15), c);
                decimal("5", situation.soldeJours());
                decimal("70000.00", situation.soldeValorisation());
                assertEquals(1, situation.lots().size());
                assertEquals("REPRISE_CUTOVER", situation.lots().get(0).origine());
                decimal("14000", situation.lots().get(0).tauxHistorique());

                try (PreparedStatement ps = c.prepareStatement("SELECT date_cutover,cumul_droits_ouverture,"
                        + "cumul_valorisation_ouverture FROM conge_periode_reference WHERE id_periode_reference=?")) {
                    ps.setInt(1, reference);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(LocalDate.of(2026, 6, 15), rs.getDate(1).toLocalDate());
                        decimal("15", rs.getBigDecimal(2));
                        decimal("180000", rs.getBigDecimal(3));
                    }
                }
                assertTrue(new CongeTauxApplicableService().acquisitionDejaReprise(employee,
                        LocalDate.of(2026, 6, 15), c));
                assertFalse(new CongeTauxApplicableService().acquisitionDejaReprise(employee,
                        LocalDate.of(2026, 6, 16), c));
                assertThrows(IllegalStateException.class,
                        () -> new CongeTauxApplicableService().enregistrerCutoverSnapshot(request, null, c));
            } finally { c.rollback(); }
        }
    }

    @Test
    void repriseAu25JuilletGardeLaReferenceCivileEtLaisseLaClotureDePaieAoutAcquerir() throws Exception {
        assumeDatabaseConfigured();
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try {
                int employee = insertEmployee(c);
                LocalDate cutoff = LocalDate.of(2026, 7, 25);
                var request = opening(employee, cutoff, "12", "180000", "15000", "17.5", "262500");
                int reference = new CongeTauxApplicableService().enregistrerCutoverSnapshot(request, null, c);

                try (PreparedStatement ps = c.prepareStatement("SELECT date_debut,date_fin,date_cutover "
                        + "FROM conge_periode_reference WHERE id_periode_reference=?")) {
                    ps.setInt(1, reference);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(LocalDate.of(2026, 1, 1), rs.getDate(1).toLocalDate());
                        assertEquals(LocalDate.of(2026, 12, 31), rs.getDate(2).toLocalDate());
                        assertEquals(cutoff, rs.getDate(3).toLocalDate());
                    }
                }

                CongeTauxApplicableService service = new CongeTauxApplicableService();
                assertTrue(service.acquisitionDejaReprise(employee, cutoff, c));
                assertFalse(service.acquisitionDejaReprise(employee, LocalDate.of(2026, 8, 25), c));
                assertEquals(LocalDate.of(2026, 7, 26), cutoff.plusDays(1));
            } finally { c.rollback(); }
        }
    }

    @Test
    void quotaPoolEstRepartiEntreLesAnneesSelonLesJoursCouverts() throws Exception {
        assumeDatabaseConfigured();
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try {
                int employee = insertEmployee(c);
                int type = scalarInt(c, "SELECT id_type_conge FROM ref_type_conge WHERE code='EXCEPTIONNEL'");
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO demande_conge "
                        + "(id_employe,id_type_conge,id_statut_demande_conge,date_debut,date_fin,nb_jours_decomptes) "
                        + "VALUES (?, ?, (SELECT id_statut_demande_conge FROM ref_statut_demande_conge WHERE code='VALIDE'), "
                        + "DATE '2025-12-30',DATE '2026-01-02',4)")) {
                    ps.setInt(1, employee); ps.setInt(2, type); ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement("SELECT annee,jours_consommes FROM v_pool_conge_annuel_consommation WHERE id_employe=? ORDER BY annee")) {
                    ps.setInt(1, employee);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next()); assertEquals(2025, rs.getInt(1)); decimal("2", rs.getBigDecimal(2));
                        assertTrue(rs.next()); assertEquals(2026, rs.getInt(1)); decimal("2", rs.getBigDecimal(2));
                        assertFalse(rs.next());
                    }
                }
            } finally { c.rollback(); }
        }
    }

    @Test
    void simulationEtValorisationPersisteeSuiventLeFifoEtLaPaieLitLeMemeMontant() throws Exception {
        assumeDatabaseConfigured();
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try {
                int employee = insertEmployee(c);
                int oldLot = insertMovement(c, employee, "ACQUISITION", "5", "2024-12-31", "50000", null, "Lot 2024");
                int newLot = insertMovement(c, employee, "ACQUISITION", "10", "2025-12-31", "120000", null, "Lot 2025");
                CongeLotValorisationService service = new CongeLotValorisationService();
                var before = service.situation(employee, LocalDate.of(2026, 2, 8), c);
                var simulation = service.simulerPrise(employee, LocalDate.of(2026, 2, 8), d("8"), c);
                decimal("15", before.soldeJours());
                decimal("86000.00", simulation.valeurEstimee());
                assertEquals(oldLot, simulation.lots().get(0).idSource());
                decimal("5", simulation.lots().get(0).jours());
                decimal("50000.00", simulation.lots().get(0).valeur());
                assertEquals(newLot, simulation.lots().get(1).idSource());
                decimal("3", simulation.lots().get(1).jours());
                decimal("36000.00", simulation.lots().get(1).valeur());

                int demand = insertValidatedDemand(c, employee, "2026-02-01", "2026-02-08", "8");
                insertMovement(c, employee, "PRISE", "-8", "2026-02-01", null, demand, "Prise validée");
                var after = service.situation(employee, LocalDate.of(2026, 2, 8), c);
                decimal("7", after.soldeJours());
                decimal("84000.00", after.soldeValorisation());
                assertEquals(2, after.imputations().stream().filter(i -> demand == i.idDemande()).count());
                decimal("86000.00", service.valorisationPrisesSurPeriode(employee,
                        LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 8), c));
                try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM mouvement_solde_conge WHERE id_employe=?")) {
                    ps.setInt(1, employee);
                    try (ResultSet rs = ps.executeQuery()) { rs.next(); assertEquals(3, rs.getInt(1)); }
                }
            } finally { c.rollback(); }
        }
    }

    @Test
    void clotureEtActivationTcongeRespectentLesCumulsEtNeRevalorisentPasLesLots() throws Exception {
        assumeDatabaseConfigured();
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try {
                int employee = insertEmployee(c);
                var request = opening(employee, LocalDate.of(2026, 1, 1), "0", "0", "10000", "10", "100000");
                int reference = new CongeTauxApplicableService().enregistrerCutoverSnapshot(request, null, c);
                int oldLot = insertMovement(c, employee, "ACQUISITION", "5", "2024-12-31", "50000", null, "Lot ancien");
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO conge_journal_droit "
                        + "(id_employe,id_periode_reference,annee,mois,salaire_base,jours_travailles_formule,"
                        + "jours_droit_acquis,base_valorisation,valorisation_mensuelle,cumul_jours_droit,"
                        + "cumul_valorisation,taux_journalier_conge) VALUES (?, ?, 2026, 12, 120000, 30, 2.5, 120000, 30000, 2.5, 30000, 12000)")) {
                    ps.setInt(1, employee); ps.setInt(2, reference); ps.executeUpdate();
                }
                CongeTauxApplicableService service = new CongeTauxApplicableService();
                decimal("10400.00", service.cloturerPeriodeReference(reference, null, c));
                int active = service.activerTauxCalcule(reference, LocalDate.of(2027, 1, 1), null, c);
                assertTrue(active > 0);
                var result = new CongeLotValorisationService().situation(employee,
                        LocalDate.of(2027, 1, 1), c);
                assertEquals(oldLot, result.lots().get(0).idSource());
                decimal("50000.00", result.lots().get(0).valeur());
                try (PreparedStatement ps = c.prepareStatement("SELECT taux_journalier FROM conge_taux_applicable WHERE id_taux_applicable=?")) {
                    ps.setInt(1, active);
                    try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); decimal("10400", rs.getBigDecimal(1)); }
                }
            } finally { c.rollback(); }
        }
    }

    private static void assumeDatabaseConfigured() {
        assumeTrue(URL != null && !URL.isBlank(), "Use GALAXAM_CONGE_TEST_DB_URL pointing to disposable PostgreSQL");
    }

    private static Connection connection() throws Exception {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    private static int scalarInt(Connection c, String sql) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next()); return rs.getInt(1);
        }
    }

    private static int insertEmployee(Connection c) throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO employe "
                + "(matricule,nom,prenom,sexe,date_naissance,lieu_naissance,cin_numero,cin_date_delivrance,"
                + "cin_lieu_delivrance,adresse,telephone) VALUES (?, 'TEST', 'Conges', 'M', '1990-01-01', 'Test', ?, "
                + "'2010-01-01', 'Test', 'Adresse test', '000000') RETURNING id_employe")) {
            ps.setString(1, "IT-" + suffix); ps.setString(2, "IT" + suffix);
            try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); return rs.getInt(1); }
        }
    }

    private static CongeTauxApplicableService.CutoverOpeningRequest opening(int employee, LocalDate date,
            String days, String value, String rate, String cycleDays, String cycleValue) {
        var request = new CongeTauxApplicableService.CutoverOpeningRequest();
        request.setIdEmploye(employee); request.setDateCutover(date);
        request.setModeReprise(CongeTauxApplicableService.CongeModeReprise.AGGREGATED_OPENING);
        request.setSoldeJours(d(days)); request.setValeurSoldeOuverture(d(value));
        request.setTauxJournalierApplicable(d(rate)); request.setDateDebutReference(date.withDayOfYear(1));
        request.setCumulDroitsOuverture(d(cycleDays)); request.setCumulValorisationOuverture(d(cycleValue));
        request.setReferenceArrete("IT-DAF-" + date);
        return request;
    }

    private static int insertMovement(Connection c, int employee, String type, String days, String date,
            String value, Integer demand, String comment) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO mouvement_solde_conge "
                + "(id_employe,type_mouvement,nb_jours,date_mouvement,valeur_source,id_demande_conge,commentaire) "
                + "VALUES (?,?,?,?,?,?,?) RETURNING id_mouvement")) {
            ps.setInt(1, employee); ps.setString(2, type); ps.setBigDecimal(3, d(days));
            ps.setObject(4, LocalDate.parse(date));
            if (value == null) ps.setNull(5, java.sql.Types.NUMERIC); else ps.setBigDecimal(5, d(value));
            if (demand == null) ps.setNull(6, java.sql.Types.INTEGER); else ps.setInt(6, demand);
            ps.setString(7, comment);
            try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); return rs.getInt(1); }
        }
    }

    private static int insertValidatedDemand(Connection c, int employee, String start, String end, String days) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO demande_conge "
                + "(id_employe,id_type_conge,id_statut_demande_conge,date_debut,date_fin,date_demande,nb_jours_decomptes,est_remunere) "
                + "VALUES (?,5,3,?,?,?, ?, TRUE) RETURNING id_demande_conge")) {
            ps.setInt(1, employee); ps.setObject(2, LocalDate.parse(start)); ps.setObject(3, LocalDate.parse(end));
            ps.setObject(4, LocalDate.parse(start)); ps.setBigDecimal(5, d(days));
            try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); return rs.getInt(1); }
        }
    }
}
