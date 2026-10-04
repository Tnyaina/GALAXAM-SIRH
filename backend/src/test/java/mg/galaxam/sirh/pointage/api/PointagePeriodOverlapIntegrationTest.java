package mg.galaxam.sirh.pointage.api;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class PointagePeriodOverlapIntegrationTest {
    @Test
    void bornesInclusivesInterdisentLeChevauchementMaisAcceptentLeJourSuivant() throws Exception {
        Properties properties = new Properties();
        try (var input = getClass().getResourceAsStream("/application-test.properties")) {
            assertNotNull(input);
            properties.load(input);
        }
        String url = properties.getProperty("spring.datasource.url");
        assertEquals("jdbc:postgresql://localhost:5432/sirh_galaxam_test", url);
        try (Connection c = DriverManager.getConnection(url, properties.getProperty("spring.datasource.username"),
                properties.getProperty("spring.datasource.password"))) {
            try (var statement = c.createStatement(); var result = statement.executeQuery("SELECT current_database()")) {
                assertTrue(result.next());
                assertEquals("sirh_galaxam_test", result.getString(1));
            }
            c.setAutoCommit(false);
            try {
                String prefix = "T" + Long.toString(System.nanoTime(), 36).toUpperCase();
                insert(c, prefix + "A", LocalDate.of(2099, 1, 1), LocalDate.of(2099, 1, 25));
                insert(c, prefix + "B", LocalDate.of(2099, 1, 26), LocalDate.of(2099, 2, 25));
                SQLException overlap = assertThrows(SQLException.class,
                        () -> insert(c, prefix + "C", LocalDate.of(2099, 1, 25), LocalDate.of(2099, 2, 1)));
                assertEquals("23P01", overlap.getSQLState());
            } finally {
                c.rollback();
            }
        }
    }

    private static void insert(Connection c, String code, LocalDate debut, LocalDate fin) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO periode_paie(code, libelle, date_debut, date_fin, id_statut_periode) "
                        + "VALUES (?, ?, ?, ?, (SELECT id_statut_periode FROM ref_statut_periode_paie WHERE code='BROUILLON'))")) {
            ps.setString(1, code);
            ps.setString(2, code);
            ps.setDate(3, java.sql.Date.valueOf(debut));
            ps.setDate(4, java.sql.Date.valueOf(fin));
            ps.executeUpdate();
        }
    }
}
