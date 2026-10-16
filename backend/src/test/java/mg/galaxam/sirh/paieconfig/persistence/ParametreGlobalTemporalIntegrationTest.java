package mg.galaxam.sirh.paieconfig.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class ParametreGlobalTemporalIntegrationTest {

    @Test
    void diagnosticPrealableSurBaseDeTestEstEnLectureSeule() throws Exception {
        String sql = "SELECT "
                + "(SELECT COUNT(*) FROM parametre_global WHERE date_fin IS NOT NULL AND date_fin < date_debut) AS invalid_dates, "
                + "(SELECT COUNT(*) FROM parametre_global a JOIN parametre_global b "
                + " ON UPPER(a.code_parametre)=UPPER(b.code_parametre) AND a.id_parametre<b.id_parametre "
                + " AND daterange(a.date_debut,a.date_fin,'[]') && daterange(b.date_debut,b.date_fin,'[]')) AS overlaps";
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            System.out.println("parametre_global preflight (base test, lecture seule): invalid_dates="
                    + rs.getLong("invalid_dates") + ", overlaps=" + rs.getLong("overlaps"));
        }
    }

    private Connection openConnection() throws Exception {
        Properties properties = new Properties();
        try (var input = getClass().getResourceAsStream("/application-test.properties")) {
            properties.load(input);
        }
        return DriverManager.getConnection(properties.getProperty("spring.datasource.url"),
                properties.getProperty("spring.datasource.username"),
                properties.getProperty("spring.datasource.password"));
    }

    @Test
    void resoutVersionsHistoriquesCourantesFuturesAdjacentesEtOuvertes() throws Exception {
        int year = LocalDate.now().getYear();
        String code = "TEST_TEMPORAL_" + Long.toUnsignedString(System.nanoTime());
        try (Connection c = openConnection()) {
            c.setAutoCommit(false);
            insert(c, code, LocalDate.of(year - 2, 1, 1), LocalDate.of(year - 1, 12, 31), "HISTORIQUE");
            insert(c, code, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), "COURANT");
            insert(c, code, LocalDate.of(year + 1, 1, 1), null, "FUTUR_OUVERT");

            assertEquals("HISTORIQUE", ParametreGlobal.resolveAtDate(code,
                    Date.valueOf(LocalDate.of(year - 1, 6, 1)), c).valeur());
            assertEquals("COURANT", ParametreGlobal.resolveAtDate(code,
                    Date.valueOf(LocalDate.of(year, 1, 1)), c).valeur());
            assertEquals(ParametreGlobal.ResolutionStatus.MISSING,
                    ParametreGlobal.resolveAtDate(code, Date.valueOf(LocalDate.of(year - 3, 1, 1)), c).status());
            var future = ParametreGlobal.resolveAtDate(code, Date.valueOf(LocalDate.of(year + 1, 1, 1)), c);
            assertEquals(ParametreGlobal.ResolutionStatus.ACTIVE, future.status());
            assertEquals("FUTUR_OUVERT", future.valeur());
            c.rollback();
        }
    }

    @Test
    void refuseLesChevauchementsEnJavaOuParLaContrainteDejaDeployee() throws Exception {
        String code = "TEST_OVERLAP_" + Long.toUnsignedString(System.nanoTime());
        try (Connection c = openConnection()) {
            c.setAutoCommit(false);
            LocalDate start = LocalDate.now().minusDays(20);
            insert(c, code, start, start.plusDays(20), "A");
            try {
                insert(c, code.toLowerCase(java.util.Locale.ROOT), start.plusDays(10), null, "B");
                IllegalStateException ambiguity = assertThrows(IllegalStateException.class,
                        () -> ParametreGlobal.resolveAtDate(code,
                                Date.valueOf(start.plusDays(15)), c));
                assertTrue(ambiguity.getMessage().startsWith("AMBIGUOUS_CONFIGURATION:"));
            } catch (SQLException sqlException) {
                assertEquals("23P01", sqlException.getSQLState(), "La contrainte d'exclusion doit rejeter le chevauchement");
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    void leModeleRejetteUneDateFinAvantLeDebut() {
        ParametreGlobal parameter = new ParametreGlobal();
        parameter.setCodeParametre("TEST");
        parameter.setValeur("1");
        parameter.setDateDebut(Date.valueOf("2026-02-01"));
        parameter.setDateFin(Date.valueOf("2026-01-31"));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> parameter.controler(null));
        assertTrue(error.getMessage().contains("date de fin"));
    }

    @Test
    void laContraintePostgresqlRejetteAussiLesChevauchementsConcurrents() throws Exception {
        String table = "test_pg_param_ranges_" + Long.toUnsignedString(System.nanoTime());
        try (Connection setup = openConnection()) {
            setup.setAutoCommit(true);
            setup.createStatement().execute("CREATE EXTENSION IF NOT EXISTS btree_gist");
            setup.createStatement().execute("CREATE TABLE " + table + " (code text NOT NULL, debut date NOT NULL, fin date, "
                    + "CHECK (fin IS NULL OR fin >= debut), "
                    + "EXCLUDE USING gist (upper(code) WITH =, daterange(debut, fin, '[]') WITH &&))");
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection owner = openConnection()) {
            owner.setAutoCommit(false);
            SQLException invalidRange = assertThrows(SQLException.class,
                    () -> insertRange(owner, table, "INVALID", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 31)));
            assertEquals("23514", invalidRange.getSQLState());
            owner.rollback();
            owner.setAutoCommit(false);
            insertRange(owner, table, "CONCURRENT", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));
            CountDownLatch insertStarted = new CountDownLatch(1);
            Future<String> competingInsert = executor.submit(() -> {
                try (Connection contender = openConnection()) {
                    contender.setAutoCommit(false);
                    insertStarted.countDown();
                    try {
                        insertRange(contender, table, "concurrent", LocalDate.of(2026, 6, 30), null);
                        contender.commit();
                        return "INSERTED";
                    } catch (SQLException e) {
                        contender.rollback();
                        return e.getSQLState();
                    }
                }
            });
            assertTrue(insertStarted.await(5, java.util.concurrent.TimeUnit.SECONDS));
            owner.commit();
            assertEquals("23P01", competingInsert.get().toString());
        } catch (ExecutionException e) {
            throw new AssertionError("L'insertion concurrente n'a pas renvoyé son diagnostic SQL", e.getCause());
        } finally {
            executor.shutdownNow();
            try (Connection cleanup = openConnection()) {
                cleanup.setAutoCommit(true);
                cleanup.createStatement().execute("DROP TABLE IF EXISTS " + table);
            }
        }
    }

    private void insert(Connection c, String code, LocalDate start, LocalDate end, String value) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO parametre_global "
                + "(code_parametre, categorie, valeur, date_debut, date_fin, est_actif) VALUES (?, 'TEST', ?, ?, ?, TRUE)")) {
            ps.setString(1, code);
            ps.setString(2, value);
            ps.setDate(3, Date.valueOf(start));
            if (end == null) ps.setNull(4, java.sql.Types.DATE); else ps.setDate(4, Date.valueOf(end));
            ps.executeUpdate();
        }
    }

    private void insertRange(Connection c, String table, String code, LocalDate start, LocalDate end) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + table + " (code, debut, fin) VALUES (?, ?, ?)")) {
            ps.setString(1, code);
            ps.setDate(2, Date.valueOf(start));
            if (end == null) ps.setNull(3, java.sql.Types.DATE); else ps.setDate(3, Date.valueOf(end));
            ps.executeUpdate();
        }
    }
}
