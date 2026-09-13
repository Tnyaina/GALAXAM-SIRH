package mg.galaxam.sirh;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.sql.DataSource;
import mg.galaxam.sirh.fixtures.TestDatabaseCleaner;
import mg.galaxam.sirh.fixtures.TestEmployeFixtures;
import mg.galaxam.sirh.fixtures.TestUserFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
public abstract class BaseIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected DataSource dataSource;

    protected ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    protected TestUserFixtures userFixtures;

    @Autowired
    protected TestDatabaseCleaner databaseCleaner;

    @Autowired
    protected TestEmployeFixtures employeFixtures;

    @BeforeEach
    public void setupBase() {
        userFixtures.initTestUsers();
        // Les scénarios d'intégration valides disposent explicitement d'un taux ISI daté.
        // Les tests de configuration absente utilisent les calculateurs purs et ne passent pas par ce setup.
        try (var c = dataSource.getConnection()) {
            try (var normalize = c.prepareStatement(
                    "UPDATE parametre_global SET date_debut=DATE '2000-01-01', date_fin=NULL "
                            + "WHERE code_parametre='TAUX_ISI' AND description='Fixture ISI intégration'")) {
                normalize.executeUpdate();
            }
            try (var ps = c.prepareStatement(
                "INSERT INTO parametre_global (code_parametre, categorie, valeur, description, date_debut, est_actif) "
                        + "SELECT 'TAUX_ISI','FISCAL','5.00','Fixture ISI intégration',DATE '2000-01-01',true "
                        + "WHERE NOT EXISTS (SELECT 1 FROM parametre_global WHERE code_parametre='TAUX_ISI' AND est_actif=true AND date_debut <= DATE '2000-01-01')")) {
                ps.executeUpdate();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Impossible d'initialiser la configuration fiscale de test", e);
        }
    }

    protected String getAdminToken() {
        return userFixtures.getAdminToken();
    }

    protected String getPointageToken() {
        return userFixtures.getPointageToken();
    }

    protected String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
