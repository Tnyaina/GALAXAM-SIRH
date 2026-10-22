package mg.galaxam.sirh.contrat.application;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import mg.galaxam.sirh.contrat.api.dto.EmployeRequest;
import mg.galaxam.sirh.contrat.persistence.EmployeConjoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

class EmployeConjointLifecycleTest {
    private final EmployeService service = new EmployeService(null, null, null);
    private Connection connection;

    @BeforeEach
    void ouvrirTransaction() throws Exception {
        String url = System.getenv("GALAXAM_CONJOINT_TEST_DB_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank(), "Base PostgreSQL temporaire requise");
        connection = DriverManager.getConnection(url,
                System.getenv("GALAXAM_CONJOINT_TEST_DB_USER"),
                System.getenv("GALAXAM_CONJOINT_TEST_DB_PASSWORD"));
        connection.setAutoCommit(false);
        EmployeConjoint.deleteByEmployeId(1, connection);
    }

    @AfterEach
    void annulerTransaction() throws Exception {
        if (connection != null) {
            connection.rollback();
            connection.close();
        }
    }

    private static EmployeRequest demandeConjoint(String nom) {
        EmployeRequest req = new EmployeRequest();
        EmployeRequest.ConjointRequest conjoint = new EmployeRequest.ConjointRequest();
        conjoint.setNom(nom);
        conjoint.setPrenom("Test");
        req.setConjoint(conjoint);
        return req;
    }

    @Test
    void sansConjointEtChampAbsentNeCreentRien() throws Exception {
        service.appliquerConjoint(1, new EmployeRequest(), connection);
        assertNull(EmployeConjoint.findByEmployeId(1, connection));
    }

    @Test
    void creePuisModifieSansChangerIdentifiant() throws Exception {
        service.appliquerConjoint(1, demandeConjoint("Initial"), connection);
        int id = EmployeConjoint.findByEmployeId(1, connection).getIdConjoint();
        service.appliquerConjoint(1, demandeConjoint("Modifie"), connection);
        EmployeConjoint conjoint = EmployeConjoint.findByEmployeId(1, connection);
        assertEquals(id, conjoint.getIdConjoint());
        assertEquals("Modifie", conjoint.getNom());
    }

    @Test
    void modificationEmployeSansConjointConserveIdentifiantEtContenu() throws Exception {
        service.appliquerConjoint(1, demandeConjoint("Initial"), connection);
        int id = EmployeConjoint.findByEmployeId(1, connection).getIdConjoint();
        service.appliquerConjoint(1, new EmployeRequest(), connection);
        EmployeRequest vide = new EmployeRequest();
        vide.setConjoint(new EmployeRequest.ConjointRequest());
        service.appliquerConjoint(1, vide, connection);
        EmployeConjoint conjoint = EmployeConjoint.findByEmployeId(1, connection);
        assertEquals(id, conjoint.getIdConjoint());
        assertEquals("Initial", conjoint.getNom());
    }

    @Test
    void suppressionExigeUnIndicateurExplicite() throws Exception {
        service.appliquerConjoint(1, demandeConjoint("Initial"), connection);
        EmployeRequest req = new EmployeRequest();
        req.setSupprimerConjoint(true);
        service.appliquerConjoint(1, req, connection);
        assertNull(EmployeConjoint.findByEmployeId(1, connection));
    }

    @Test
    void refuseSuppressionEtSaisieSimultanees() throws Exception {
        EmployeRequest req = demandeConjoint("Initial");
        req.setSupprimerConjoint(true);
        assertThrows(IllegalArgumentException.class, () -> service.appliquerConjoint(1, req, connection));
    }

    @Test
    void baseRefuseUnSecondConjointPourLeMemeEmploye() throws Exception {
        service.appliquerConjoint(1, demandeConjoint("Initial"), connection);
        EmployeConjoint second = new EmployeConjoint();
        second.setIdEmploye(1);
        second.setNom("Second");
        second.setPrenom("Test");
        SQLException error = assertThrows(SQLException.class, () -> second.save(connection));
        assertEquals("23505", error.getSQLState());
    }
}
