package mg.galaxam.sirh.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SirhApiE2EIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Autowired
    private javax.sql.DataSource dataSource;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static String adminToken;

    @BeforeEach
    void setup() throws Exception {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(springSecurityFilterChain)
                .build();

        try (java.sql.Connection c = dataSource.getConnection()) {
            String hash = passwordEncoder.encode("Pass1234");
            try (java.sql.PreparedStatement ps = c.prepareStatement("UPDATE utilisateur SET mot_de_passe_hache = ? WHERE identifiant = 'admin'")) {
                ps.setString(1, hash);
                ps.executeUpdate();
            }
        }
    }

    @Test
    @Order(1)
    @DisplayName("API-01 : Authentification via POST /auth/login avec admin/Pass1234")
    void testLoginAdminApi() throws Exception {
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

        assertTrue(jsonNode.has("accessToken"), "La réponse doit contenir un accessToken JWT");
        adminToken = jsonNode.get("accessToken").asText();
        assertNotNull(adminToken);
        assertTrue(adminToken.startsWith("eyJ"));
        assertEquals("admin", jsonNode.get("identifiant").asText());
    }

    @Test
    @Order(2)
    @DisplayName("API-02 : Sécurité — Rejet 403/401 sur GET /employes sans jeton JWT")
    void testSecuriteRejetSansToken() throws Exception {
        mockMvc.perform(get("/employes"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(3)
    @DisplayName("API-03 : Consultation paginée des 5 collaborateurs via GET /employes avec jeton JWT")
    void testConsultationEmployesAvecToken() throws Exception {
        assertNotNull(adminToken, "Le jeton administrateur doit être disponible");

        MvcResult result = mockMvc.perform(get("/employes")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(responseBody);

        assertTrue(jsonNode.has("content"), "La réponse doit contenir le tableau 'content'");
        assertTrue(jsonNode.get("content").isArray());
        assertEquals(5, jsonNode.get("totalElements").asInt(), "La liste doit contenir les 5 employés du dataset réaliste");
    }

    @Test
    @Order(4)
    @DisplayName("API-04 : Consultation du solde de congés via GET /conges/solde/employe/{id}")
    void testConsultationSoldesConges() throws Exception {
        // Emp 1 : DAF (solde 35j)
        MvcResult result = mockMvc.perform(get("/conges/solde/employe/1")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(responseBody);

        assertTrue(jsonNode.has("soldeActuel"));
        assertTrue(jsonNode.get("soldeActuel").asDouble() >= 35.0, "Le solde net doit être d'au moins 35 jours");
    }

    @Test
    @Order(5)
    @DisplayName("API-05 : Récupération de la synthèse de pointage via GET /pointages/synthese?idPeriodePaie=1")
    void testSynthesePointagePeriodeApi() throws Exception {
        MvcResult result = mockMvc.perform(get("/pointages/synthese")
                        .param("idPeriodePaie", "1")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(responseBody);

        assertTrue(jsonNode.has("synthese"), "La réponse doit contenir la clé 'synthese'");
        JsonNode list = jsonNode.get("synthese");
        assertTrue(list.isArray());
        assertEquals(5, list.size(), "La synthèse mensuelle doit retourner les 5 employés");
    }
}
