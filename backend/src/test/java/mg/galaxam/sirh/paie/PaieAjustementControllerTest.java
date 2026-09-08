package mg.galaxam.sirh.paie;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("local")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("PaieAjustementControllerTest — Validation des Variables de Paie, Primes, Acomptes et Immutabilité")
public class PaieAjustementControllerTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static String adminToken;
    private static Integer testEmployeId;
    private static Integer testAvanceId;

    @BeforeEach
    void setUp() throws Exception {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(springSecurityFilterChain)
                .build();

        if (adminToken == null) {
            try (Connection c = dataSource.getConnection()) {
                String hash = passwordEncoder.encode("Pass1234");
                try (PreparedStatement ps = c.prepareStatement("UPDATE utilisateur SET mot_de_passe_hache = ? WHERE identifiant = 'admin'")) {
                    ps.setString(1, hash);
                    ps.executeUpdate();
                }
            }

            String loginPayload = """
                    {
                        "identifiant": "admin",
                        "motDePasse": "Pass1234"
                    }
                    """;

            MvcResult result = mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginPayload))
                    .andExpect(status().isOk())
                    .andReturn();

            String responseBody = result.getResponse().getContentAsString();
            JsonNode jsonNode = objectMapper.readTree(responseBody);
            adminToken = jsonNode.get("accessToken").asText();
        }

        if (testEmployeId == null) {
            try (Connection c = dataSource.getConnection()) {
                String sql = "SELECT id_employe FROM employe WHERE est_actif = true LIMIT 1";
                try (PreparedStatement ps = c.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        testEmployeId = rs.getInt("id_employe");
                    } else {
                        testEmployeId = 1;
                    }
                }
            }
        }
    }

    @Test
    @Order(1)
    @DisplayName("AJUST-01 : GET /api/v1/paie/ajustements/types-mouvements — Catalogue des types de mouvement")
    void testListerTypesMouvements() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/paie/ajustements/types-mouvements")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        JsonNode arr = objectMapper.readTree(body);
        assertTrue(arr.isArray());
        assertTrue(arr.size() > 0, "Le catalogue des mouvements doit contenir des types");
    }

    @Test
    @Order(2)
    @DisplayName("AJUST-02 : POST /api/v1/paie/ajustements — Création d'une Prime Exceptionnelle")
    void testCreerPrimeExceptionnelle() throws Exception {
        String payload = String.format("""
                {
                    "idEmploye": %d,
                    "idPeriodePaie": 1,
                    "typeMouvement": "PRIME_EXCEPTIONNELLE",
                    "sens": "GAIN",
                    "montant": 125000.00,
                    "motif": "Bonus de clôture projet logistique TMM",
                    "modeReglement": "VIREMENT",
                    "statut": "APPROUVE"
                }
                """, testEmployeId);

        MvcResult result = mockMvc.perform(post("/api/v1/paie/ajustements")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        assertTrue(json.get("success").asBoolean());
        assertNotNull(json.get("idAvance"));
        assertEquals("GAIN", json.get("sens").asText());
        testAvanceId = json.get("idAvance").asInt();
    }

    @Test
    @Order(3)
    @DisplayName("AJUST-03 : PUT /api/v1/paie/ajustements/{id}/statut — Transition vers REJETE")
    void testChangerStatutAjustement() throws Exception {
        Assumptions.assumeTrue(testAvanceId != null);

        String payload = """
                {
                    "statut": "REJETE",
                    "motif": "Rejet temporaire pour vérification DAF"
                }
                """;

        MvcResult result = mockMvc.perform(put("/api/v1/paie/ajustements/" + testAvanceId + "/statut")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        assertTrue(json.get("success").asBoolean());
        assertEquals("REJETE", json.get("statut").asText());
    }

    @Test
    @Order(4)
    @DisplayName("AJUST-04 : GET /api/v1/paie/ajustements — Filtrage par employé et période")
    void testListerAjustementsFiltres() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/paie/ajustements")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("idEmploye", String.valueOf(testEmployeId))
                        .param("idPeriode", "1"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        JsonNode arr = objectMapper.readTree(body);
        assertTrue(arr.isArray());
        assertTrue(arr.size() > 0, "Doit contenir au moins l'ajustement créé");
    }

    @Test
    @Order(5)
    @DisplayName("AJUST-05 : Règle d'Immutabilité — Rejet 400 si statut IMPUTE_PAIE")
    void testImmutabiliteAjustementImpute() throws Exception {
        Assumptions.assumeTrue(testAvanceId != null);

        // Passer manuellement en IMPUTE_PAIE en base pour tester le verrou
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("UPDATE paie_avance_acompte SET statut = 'IMPUTE_PAIE' WHERE id_avance = ?")) {
                ps.setInt(1, testAvanceId);
                ps.executeUpdate();
            }
        }

        String payload = """
                {
                    "statut": "APPROUVE",
                    "motif": "Tentative de modification illégale"
                }
                """;

        mockMvc.perform(put("/api/v1/paie/ajustements/" + testAvanceId + "/statut")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }
}
