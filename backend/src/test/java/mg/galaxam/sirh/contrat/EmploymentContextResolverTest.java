package mg.galaxam.sirh.contrat;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import javax.sql.DataSource;
import mg.galaxam.sirh.conge.testutils.CongeTestFixtureHelper;
import mg.galaxam.sirh.contrat.application.EmploymentContextResolver;
import mg.galaxam.sirh.contrat.domain.EmploymentContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class EmploymentContextResolverTest {

    @Autowired
    private EmploymentContextResolver contextResolver;

    @Autowired
    private DataSource dataSource;

    @Test
    void testResolveContextEmploye() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            assertEquals("sirh_galaxam_test", currentDatabase(c));
            c.setAutoCommit(false);
            try {
                int empId = createEmploye(c, "RAZAFY", "Hery");
                int ctId = CongeTestFixtureHelper.createContratActif(c, empId, LocalDate.of(2025, 1, 1));
                CongeTestFixtureHelper.createAffectation(c, empId, ctId, LocalDate.of(2025, 1, 1), new BigDecimal("1500000.00"));

                EmploymentContext ctx = contextResolver.resolveContext(empId, LocalDate.of(2026, 1, 15), c);
                assertNotNull(ctx, "Le contexte d'emploi de l'employé ne doit pas être nul.");
                assertEquals(empId, ctx.idEmploye());
                assertEquals("RAZAFY", ctx.nom());
                assertEquals("Hery", ctx.prenom());
                assertTrue(ctx.estActifEmploye());
                assertTrue(ctx.estEnPoste());
                assertEquals(new BigDecimal("1500000.00"), ctx.salaireBase());
                assertEquals("TNR", ctx.codeSite());
                assertTrue(ctx.estActifAPaie());
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    void testListActiveEmploymentContexts() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            assertEquals("sirh_galaxam_test", currentDatabase(c));
            c.setAutoCommit(false);
            try {
                int empId = createEmploye(c, "ANDRY", "Tiana");
                int ctId = CongeTestFixtureHelper.createContratActif(c, empId, LocalDate.of(2025, 6, 1));
                CongeTestFixtureHelper.createAffectation(c, empId, ctId, LocalDate.of(2025, 6, 1), new BigDecimal("1200000.00"));

                var list = contextResolver.listActiveEmploymentContexts(
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), c);
                assertNotNull(list);
                assertTrue(list.stream().anyMatch(e -> e.idEmploye() == empId), "Le salarié créé doit être résolu dans la liste active.");
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    void couvertureCampagneUtiliseLeContexteCommunSurLaPlage() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            assertEquals("sirh_galaxam_test", currentDatabase(c));
            c.setAutoCommit(false);
            try {
                int empId = createEmploye(c, "RABE", "Miora");
                int ctId = CongeTestFixtureHelper.createContratActif(c, empId, LocalDate.of(2025, 1, 1));
                CongeTestFixtureHelper.createAffectation(c, empId, ctId, LocalDate.of(2025, 1, 1), new BigDecimal("800000"));
                var coverage = contextResolver.resolveCoverage(empId,
                        LocalDate.of(2025, 12, 26), LocalDate.of(2026, 1, 25), c);
                assertEquals(31, coverage.days());
                assertEquals(LocalDate.of(2025, 12, 26), coverage.firstDay());
                assertEquals(LocalDate.of(2026, 1, 25), coverage.lastDay());
            } finally {
                c.rollback();
            }
        }
    }

    private String currentDatabase(Connection c) throws Exception {
        try (var statement = c.createStatement(); var result = statement.executeQuery("SELECT current_database()")) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }

    private int createEmploye(Connection c, String nom, String prenom) throws Exception {
        String unique = Long.toUnsignedString(System.nanoTime());
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO employe (matricule,nom,prenom,sexe,date_naissance,cin_numero,lieu_naissance,"
                + "cin_date_delivrance,cin_lieu_delivrance,adresse,telephone) "
                + "VALUES (?, ?, ?, 'M', DATE '1990-01-15', ?, 'Antananarivo', DATE '2010-01-15', 'TNR', 'Test', '0340000000') "
                + "RETURNING id_employe")) {
            ps.setString(1, "CTX-" + unique);
            ps.setString(2, nom);
            ps.setString(3, prenom);
            ps.setString(4, "CIN-CTX-" + unique);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }
}
