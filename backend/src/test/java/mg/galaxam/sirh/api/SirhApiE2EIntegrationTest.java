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
        assertTrue(jsonNode.get("totalElements").asInt() >= 5, "La liste doit contenir au moins les 5 employés du dataset");
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
        assertTrue(list.size() >= 5, "La synthèse mensuelle doit retourner au moins les 5 employés");
    }

    @Test
    @Order(6)
    @DisplayName("API-06 : Création et consultation d'un employé avec enfants (étudiant, handicapé, déduction IRSA)")
    void testGestionEnfantsEmployeApi() throws Exception {
        assertNotNull(adminToken);

        String employePayload = """
                {
                    "matricule": "EMP-TEST-ENF-001",
                    "nom": "TESTFAMILLE",
                    "prenom": "Jean",
                    "sexe": "M",
                    "dateNaissance": "1985-01-01",
                    "lieuNaissance": "Antananarivo",
                    "nationalite": "Malgache",
                    "cinNumero": "101985000001",
                    "cinDateDelivrance": "2003-01-01",
                    "cinLieuDelivrance": "Antananarivo",
                    "adresse": "Lot Test",
                    "ville": "Antananarivo",
                    "telephone": "+261340000000",
                    "email": "test.famille@galaxam.mg",
                    "enfants": [
                        {
                            "nom": "TESTFAMILLE",
                            "prenom": "Mineur",
                            "sexe": "M",
                            "dateNaissance": "2015-06-01",
                            "aCharge": true,
                            "estEtudiant": false,
                            "estHandicape": false,
                            "certificatFourni": false
                        },
                        {
                            "nom": "TESTFAMILLE",
                            "prenom": "Etudiant",
                            "sexe": "F",
                            "dateNaissance": "2003-09-15",
                            "aCharge": true,
                            "estEtudiant": true,
                            "dateFinScolarite": "2027-06-30",
                            "estHandicape": false,
                            "certificatFourni": true
                        },
                        {
                            "nom": "TESTFAMILLE",
                            "prenom": "Handicape",
                            "sexe": "M",
                            "dateNaissance": "1998-04-10",
                            "aCharge": true,
                            "estEtudiant": false,
                            "estHandicape": true,
                            "certificatFourni": false
                        }
                    ]
                }
                """;

        // 1. Création employé
        MvcResult createResult = mockMvc.perform(post("/employes")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employePayload))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createdNode = objectMapper.readTree(createResult.getResponse().getContentAsString());
        int empId = createdNode.get("idEmploye").asInt();
        assertTrue(empId > 0);

        try {
            // 2. Consultation fiche détail
            MvcResult detailResult = mockMvc.perform(get("/employes/" + empId)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode detailNode = objectMapper.readTree(detailResult.getResponse().getContentAsString());
            assertTrue(detailNode.has("enfants"));
            JsonNode enfantsNode = detailNode.get("enfants");
            assertEquals(3, enfantsNode.size(), "L'employé doit avoir 3 enfants enregistrés");

            // Vérifier l'étudiant
            JsonNode etudiantNode = null;
            for (JsonNode enf : enfantsNode) {
                if ("Etudiant".equals(enf.get("prenom").asText())) {
                    etudiantNode = enf;
                    break;
                }
            }
            assertNotNull(etudiantNode);
            assertTrue(etudiantNode.get("estEtudiant").asBoolean());
            assertTrue(etudiantNode.get("certificatFourni").asBoolean());
            assertTrue(etudiantNode.get("dateFinScolarite").asText().startsWith("2027-06-30"));

            // Vérifier l'enfant handicapé
            JsonNode handiNode = null;
            for (JsonNode enf : enfantsNode) {
                if ("Handicape".equals(enf.get("prenom").asText())) {
                    handiNode = enf;
                    break;
                }
            }
            assertNotNull(handiNode);
            assertTrue(handiNode.get("estHandicape").asBoolean());

        } finally {
            // Nettoyage
            try (java.sql.Connection c = dataSource.getConnection()) {
                try (java.sql.PreparedStatement ps = c.prepareStatement("DELETE FROM employe WHERE id_employe = ?")) {
                    ps.setInt(1, empId);
                    ps.executeUpdate();
                }
            }
        }
    }
}
