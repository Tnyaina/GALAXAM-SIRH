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
    }
}
