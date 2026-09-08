package mg.galaxam.sirh.api;

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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("local")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("PaieApiControllerIntegrationTest — Endpoints REST du Module Paie & Exports Déclaratifs")
public class PaieApiControllerIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DataSource dataSource;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static String adminToken;

    @BeforeEach
    void setup() throws Exception {
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
    }

    @Test
    @Order(1)
    @DisplayName("API-PAIE-01 : Sécurité — Rejet 403 sans jeton d'authentification")
    void testSecuriteSansToken() throws Exception {
        mockMvc.perform(get("/api/v1/paie/periode/1/bulletins"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(2)
    @DisplayName("API-PAIE-02 : POST /api/v1/paie/calculer-periode — Calcul collectif de la période 1")
    void testCalculerPeriode() throws Exception {
        String payload = """
                {
                    "idPeriodePaie": 1,
                    "simulation": false
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/v1/paie/calculer-periode")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(body);

        assertTrue(node.has("nbBulletinsCalcules"));
        assertTrue(node.get("nbBulletinsCalcules").asInt() >= 5);
        assertTrue(node.get("totalMasseSalarialeBrute").asDouble() > 0);
        assertTrue(node.get("totalNetAPayer").asDouble() > 0);
    }

    @Test
    @Order(3)
    @DisplayName("API-PAIE-03 : GET /api/v1/paie/periode/1/bulletins — Liste des bulletins calculés")
    void testListerBulletins() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/paie/periode/1/bulletins")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        JsonNode arr = objectMapper.readTree(body);
        assertTrue(arr.isArray());
        assertTrue(arr.size() >= 5);
    }

    @Test
    @Order(4)
    @DisplayName("API-PAIE-04 : GET /api/v1/paie/periode/1/export/safi — Téléchargement État 301 SAFI CSV")
    void testExportSafiCsv() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/paie/periode/1/export/safi")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals("text/csv;charset=UTF-8", result.getResponse().getContentType());
        String content = result.getResponse().getContentAsString();
        assertTrue(content.startsWith("\ufeff"));
        assertTrue(content.contains("ETAT 301 - ANNEXE DE DECLARATION DE L'IRSA (SAFI)"));
        assertTrue(content.contains("TOTAL;"));
    }

    @Test
    @Order(5)
    @DisplayName("API-PAIE-05 : GET /api/v1/paie/periode/1/export/edi-isi — Téléchargement XML e-Hetra ISI 5%")
    void testExportEdiIsiXml() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/paie/periode/1/export/edi-isi")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals("application/xml", result.getResponse().getContentType());
        String content = result.getResponse().getContentAsString();
        assertTrue(content.contains("<idEdiType>ISI</idEdiType>"));
        assertTrue(content.contains("<rContribuable>3000019434</rContribuable>"));
    }

    @Test
    @Order(6)
    @DisplayName("API-PAIE-06 : GET /api/v1/paie/periode/1/export/dnts — Téléchargement DNTS CNaPS CSV")
    void testExportDntsCsv() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/paie/periode/1/export/dnts")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals("text/csv;charset=UTF-8", result.getResponse().getContentType());
        String content = result.getResponse().getContentAsString();
        assertTrue(content.contains("CNAPS - CAISSE NATIONALE DE PREVOYANCE SOCIALE & FMFP"));
        assertTrue(content.contains("TOTAL;"));
    }

    @Test
    @Order(7)
    @DisplayName("API-PAIE-07 : GET /api/v1/paie/periode/1/export/virement — Téléchargement Ordre de Virement CSV")
    void testExportVirementCsv() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/paie/periode/1/export/virement")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals("text/csv;charset=UTF-8", result.getResponse().getContentType());
        String content = result.getResponse().getContentAsString();
        assertTrue(content.contains("ORDRE DE VIREMENT DE SALAIRES INTERBANCAIRE"));
        assertTrue(content.contains("TOTAL;"));
    }

    @Test
    @Order(8)
    @DisplayName("API-PAIE-08 : POST /api/v1/paie/valider-periode puis /cloturer-periode")
    void testValidationEtCloturePeriode() throws Exception {
        String payload = """
                {
                    "idPeriodePaie": 1
                }
                """;

        // Validation
        mockMvc.perform(post("/api/v1/paie/valider-periode")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        // Clôture
        mockMvc.perform(post("/api/v1/paie/cloturer-periode")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
    }

    @Test
    @Order(9)
    @DisplayName("API-PAIE-09 : POST /api/v1/paie/rouvrir-periode — Réouverture tracée avec motif")
    void testReouverturePeriode() throws Exception {
        // Sans motif -> rejet bad request
        String invalidPayload = """
                {
                    "idPeriodePaie": 1,
                    "motif": "Non"
                }
                """;
        mockMvc.perform(post("/api/v1/paie/rouvrir-periode")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest());

        // Avec motif valide -> succès
        String validPayload = """
                {
                    "idPeriodePaie": 1,
                    "motif": "Régularisation exceptionnelle des heures supplémentaires suite audit DAF"
                }
                """;
        MvcResult result = mockMvc.perform(post("/api/v1/paie/rouvrir-periode")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(body);
        assertTrue(node.get("success").asBoolean());
    }
}
