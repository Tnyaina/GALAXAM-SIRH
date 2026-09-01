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

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SirhFullApiEndpointsIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static String adminToken;
    private static String agentToken;

    @BeforeEach
    void setUp() throws Exception {
        if (this.mockMvc == null) {
            this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                    .addFilters(springSecurityFilterChain)
                    .build();
        }

        if (adminToken == null) {
            String loginPayload = "{\"identifiant\":\"admin\",\"motDePasse\":\"Pass1234\"}";
            MvcResult result = mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginPayload))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
            adminToken = node.get("accessToken").asText();
            assertNotNull(adminToken);
        }

        if (agentToken == null) {
            String loginPayload = "{\"identifiant\":\"pointage_agent\",\"motDePasse\":\"Pass1234\"}";
            MvcResult result = mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginPayload))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
            agentToken = node.get("accessToken").asText();
            assertNotNull(agentToken);
        }
    }

    // =========================================================================
    // 1. AUTHENTIFICATION & GESTION DES UTILISATEURS (RBAC)
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("API-AUTH-01 : Login nominal Admin & Obtention JWT")
    void testAuth_LoginAdmin_Nominal() {
        assertNotNull(adminToken);
        assertTrue(adminToken.startsWith("eyJ"));
    }

    @Test
    @Order(2)
    @DisplayName("API-AUTH-02 : Login échoue avec mot de passe erroné (Cas négatif)")
    void testAuth_Login_EchecMotDePasse() throws Exception {
        String badPayload = "{\"identifiant\":\"admin\",\"motDePasse\":\"MauvaisMdp999\"}";
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badPayload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(3)
    @DisplayName("API-AUTH-03 : Inscription Utilisateur /auth/register - Nominal et Rejet Doublon")
    void testAuth_Register_NominalEtDoublon() throws Exception {
        String uniqueId = "test_user_" + System.currentTimeMillis();
        String registerPayload = "{\"identifiant\":\"" + uniqueId + "\",\"motDePasse\":\"Pass1234\",\"idRole\":2}";

        // Création nominale
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload))
                .andExpect(status().isCreated());

        // Rejet du doublon
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload))
                .andExpect(status().isConflict());
    }

    // =========================================================================
    // 2. GESTION DU PERSONNEL (EMPLOYÉ)
    // =========================================================================

    @Test
    @Order(10)
    @DisplayName("API-EMP-01 : Consultation paginée de la liste des collaborateurs (1-indexed)")
    void testEmploye_Liste_Paginee() throws Exception {
        mockMvc.perform(get("/employes")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @Order(11)
    @DisplayName("API-EMP-02 : Recherche collaborateur par ID (EMP-00001)")
    void testEmploye_Detail_EMP00001() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            PreparedStatement ps = c.prepareStatement("SELECT id_employe FROM employe WHERE matricule = 'EMP-00001'");
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next(), "L'employé A (EMP-00001) doit exister en base");
            int idEmp = rs.getInt(1);

            mockMvc.perform(get("/employes/" + idEmp)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.employe.matricule").value("EMP-00001"))
                    .andExpect(jsonPath("$.employe.nom").value("ANDRIANINA"))
                    .andExpect(jsonPath("$.employe.prenom").value("Mamy"));
        }
    }

    @Test
    @Order(12)
    @DisplayName("API-EMP-03 : Rejet de consultation pour ID inexistant (Cas négatif 404)")
    void testEmploye_Detail_Inexistant() throws Exception {
        mockMvc.perform(get("/employes/999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // 3. ORGANISATION & RÉFÉRENTIELS (/referentiel/...)
    // =========================================================================

    @Test
    @Order(20)
    @DisplayName("API-ORG-01 : Consultation des filiales (GLX, VA)")
    void testOrganisation_Filiales() throws Exception {
        mockMvc.perform(get("/referentiel/filiales")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @Order(21)
    @DisplayName("API-ORG-02 : Consultation des sites (TNR, TMM)")
    void testOrganisation_Sites() throws Exception {
        mockMvc.perform(get("/referentiel/sites")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @Order(22)
    @DisplayName("API-ORG-03 : Consultation des départements et fonctions")
    void testOrganisation_DeptsEtFonctions() throws Exception {
        mockMvc.perform(get("/referentiel/departements")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/referentiel/fonctions")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // 4. CONTRATS ET WORKFLOWS RH
    // =========================================================================

    @Test
    @Order(30)
    @DisplayName("API-CTR-01 : Consultation contrat actif pour EMP-00001")
    void testContrats_ContratActif_EMP00001() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            PreparedStatement ps = c.prepareStatement("SELECT id_employe FROM employe WHERE matricule = 'EMP-00001'");
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next());
            int idEmp = rs.getInt(1);

            mockMvc.perform(get("/employes/" + idEmp + "/contrats/actif")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.numeroContrat").value("CT-2026-CDD-0001"))
                    .andExpect(jsonPath("$.codeStatutContrat").value("ACTIF"));
        }
    }

    // =========================================================================
    // 5. CONGÉS ET ABSENCES (/conges/solde/employe/...)
    // =========================================================================

    @Test
    @Order(40)
    @DisplayName("API-CNG-01 : Consultation du solde pour EMP-00006 (Soa)")
    void testConges_Solde_EMP00006() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            PreparedStatement ps = c.prepareStatement("SELECT id_employe FROM employe WHERE matricule = 'EMP-00006'");
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next());
            int idEmp = rs.getInt(1);

            mockMvc.perform(get("/conges/solde/employe/" + idEmp)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk());
        }
    }

    // =========================================================================
    // 6. POINTAGE & SÉCURITÉ DES PÉRIODES FERMÉES
    // =========================================================================

    @Test
    @Order(50)
    @DisplayName("API-PTG-01 : Consultation de la grille de saisie pour le Site Siège TNR")
    void testPointage_GrilleSaisie_TNR() throws Exception {
        mockMvc.perform(get("/pointages/saisie")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("idSite", "1")
                        .param("date", "2026-08-17"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(51)
    @DisplayName("API-PTG-02 : Consultation de la synthèse de pointage d'Août 2026")
    void testPointage_Synthese_Aout2026() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            PreparedStatement ps = c.prepareStatement("SELECT id_periode_paie FROM periode_paie WHERE code = '2026-08'");
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next());
            int idPeriode = rs.getInt(1);

            mockMvc.perform(get("/pointages/synthese")
                            .header("Authorization", "Bearer " + adminToken)
                            .param("idPeriodePaie", String.valueOf(idPeriode)))
                    .andExpect(status().isOk());
        }
    }

    // =========================================================================
    // 7. CONTRÔLE RBAC ET ÉTANCHÉITÉ DES RÔLES
    // =========================================================================

    @Test
    @Order(60)
    @DisplayName("API-RBAC-01 : Accès sans token rejeté systématiquement (401/403)")
    void testRbac_NonAuthentifie_Rejete() throws Exception {
        mockMvc.perform(get("/employes")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(61)
    @DisplayName("API-RBAC-02 : Agent de pointage accède à la saisie de pointage mais est rejeté sur les congés (403)")
    void testRbac_AgentPointage_Etancheite() throws Exception {
        // Agent de pointage a POINTAGE_READ -> doit avoir 200 OK
        mockMvc.perform(get("/pointages/saisie")
                        .header("Authorization", "Bearer " + agentToken)
                        .param("idSite", "1")
                        .param("date", "2026-08-17"))
                .andExpect(status().isOk());

        // Agent de pointage n'a pas CONGE_READ -> doit recevoir 403 Forbidden
        mockMvc.perform(get("/conges/solde/employe/1")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 8. ASSERTIONS DIRECTES SUR LA BASE DE DONNÉES POSTGRESQL
    // =========================================================================

    @Test
    @Order(70)
    @DisplayName("API-DB-01 : Vérification physique de l'état intègre de la base PostgreSQL")
    void testDatabaseDirect_AssertionsPhysiques() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            // 1. Vérifier le nombre d'employés créés
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM employe WHERE matricule LIKE 'EMP-0000%'")) {
                ResultSet rs = ps.executeQuery();
                assertTrue(rs.next());
                assertEquals(8, rs.getInt(1), "Les 8 employés A à H doivent être présents en base");
            }

            // 2. Vérifier les contrats actifs
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM contrat_travail WHERE id_statut_rh = 4")) {
                ResultSet rs = ps.executeQuery();
                assertTrue(rs.next());
                assertTrue(rs.getInt(1) >= 6, "Au moins 6 contrats doivent être actifs");
            }

            // 3. Vérifier le grand livre de congés
            try (PreparedStatement ps = c.prepareStatement("SELECT SUM(nb_jours) FROM mouvement_solde_conge WHERE id_employe = (SELECT id_employe FROM employe WHERE matricule = 'EMP-00006')")) {
                ResultSet rs = ps.executeQuery();
                assertTrue(rs.next());
                assertEquals(42.50, rs.getDouble(1), 0.01, "Le solde net de l'employé F doit être de 42.50 jours");
            }

            // 4. Vérifier l'existence du rôle ADMIN et AGENT_POINTAGE
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM utilisateur")) {
                ResultSet rs = ps.executeQuery();
                assertTrue(rs.next());
                assertTrue(rs.getInt(1) >= 2, "Au moins 2 utilisateurs (admin et agent) doivent exister");
            }
        }
    }
}
