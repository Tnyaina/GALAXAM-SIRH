package mg.galaxam.sirh.pointage;

import mg.galaxam.sirh.pointage.domain.engine.CalendrierOuvrableEngine;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CalendrierFeriePostgresTest {

    @Test
    void nationalSiteEtReposHebdomadaireSansPeriodeExceptionnelle() throws Exception {
        String url = System.getenv("GALAXAM_TEST_JDBC_URL");
        assumeTrue(url != null && !url.isBlank(), "Base PostgreSQL de test non configurée");
        try (Connection connection = DriverManager.getConnection(url,
                System.getenv("GALAXAM_TEST_DB_USER"), System.getenv("GALAXAM_TEST_DB_PASSWORD"))) {
            connection.setAutoCommit(false);
            try {
                int[] sites = new int[2];
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT id_site FROM ref_site ORDER BY id_site LIMIT 2");
                     ResultSet rows = statement.executeQuery()) {
                    assumeTrue(rows.next(), "Premier site requis");
                    sites[0] = rows.getInt(1);
                    assumeTrue(rows.next(), "Second site requis");
                    sites[1] = rows.getInt(1);
                }
                LocalDate monday = LocalDate.of(2031, 1, 6);
                LocalDate tuesday = monday.plusDays(1);
                int national = insertHoliday(connection, monday, true);
                int siteOnly = insertHoliday(connection, tuesday, false);
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO site_calendrier_ferie (id_site, id_ferie) VALUES (?, ?)")) {
                    statement.setInt(1, sites[0]);
                    statement.setInt(2, siteOnly);
                    statement.executeUpdate();
                }

                CalendrierOuvrableEngine calendar = new CalendrierOuvrableEngine();
                assertTrue(calendar.getDatesFeriesChomees(monday, tuesday, sites[0], connection).containsAll(
                        java.util.Set.of(monday, tuesday)));
                assertEquals(java.util.Set.of(monday),
                        calendar.getDatesFeriesChomees(monday, tuesday, sites[1], connection));
                assertEquals(0, calendar.compterJoursOuvrables(monday, tuesday, sites[0], connection));
                assertEquals(1, calendar.compterJoursOuvrables(monday, tuesday, sites[1], connection));
                assertEquals(0, calendar.compterJoursOuvrables(monday.minusDays(2), monday.minusDays(1),
                        sites[1], connection));
                assertTrue(calendar.getJourFerieChomeInfo(tuesday, sites[0], connection).estChome());
                assertFalse(calendar.getJourFerieChomeInfo(tuesday, sites[1], connection).estChome());
                assertTrue(national > 0);
            } finally {
                connection.rollback();
            }
        }
    }

    private static int insertHoliday(Connection connection, LocalDate day, boolean national) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO ref_calendrier_ferie (date_ferie, libelle, est_chome, est_national, est_actif) " +
                        "VALUES (?, 'Test calendrier', TRUE, ?, TRUE) RETURNING id_ferie")) {
            statement.setObject(1, day);
            statement.setBoolean(2, national);
            try (ResultSet row = statement.executeQuery()) {
                assertTrue(row.next());
                return row.getInt(1);
            }
        }
    }
}
