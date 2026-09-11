package mg.galaxam.sirh.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;
import mg.galaxam.sirh.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

public class ContratAndEmployeSecurityApiTest extends BaseIntegrationTest {

    @BeforeEach
    public void setUp() {
        databaseCleaner.cleanTransactionalData();
    }

    @Test
    @DisplayName("RBAC Employé : Agent de Pointage interdit d'accès à la liste des employés (403 Forbidden)")
    public void testAgentPointage_InterditDeLireEmployes() throws Exception {
        String tokenAgent = userFixtures.getPointageToken();

        mockMvc.perform(get("/employes")
                .header("Authorization", "Bearer " + tokenAgent))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RBAC Employé : Agent de Pointage interdit de créer un employé (403 Forbidden)")
    public void testAgentPointage_InterditDeCreerEmploye() throws Exception {
        String tokenAgent = userFixtures.getPointageToken();

        Map<String, Object> body = Map.of(
                "nom", "TEST",
                "prenom", "Agent",
                "sexe", "M",
                "cinNumero", "101991000888",
                "adresse", "Tana",
                "telephone", "0340000000"
        );

        mockMvc.perform(post("/employes")
                .header("Authorization", "Bearer " + tokenAgent)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RBAC Carrière : Agent de Pointage interdit d'initier un workflow d'embauche (403 Forbidden)")
    public void testAgentPointage_InterditDeCreerWorkflowEmbauche() throws Exception {
        String tokenAgent = userFixtures.getPointageToken();

        Map<String, Object> body = Map.of(
                "idEmploye", 1,
                "idFiliale", 1
        );

        mockMvc.perform(post("/employes/1/workflows/embauche-initiale")
                .header("Authorization", "Bearer " + tokenAgent)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RBAC IAM : Agent de Pointage interdit de créer des comptes utilisateurs (403 Forbidden)")
    public void testAgentPointage_InterditDeCreerCompteUtilisateur() throws Exception {
        String tokenAgent = userFixtures.getPointageToken();

        Map<String, Object> body = Map.of(
                "identifiant", "hacker@galaxam.com",
                "motDePasse", "HackedPassword123"
        );

        mockMvc.perform(post("/auth/register")
                .header("Authorization", "Bearer " + tokenAgent)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RBAC Administrateur : Autorisé à consulter la liste des employés (200 OK)")
    public void testAdmin_AutoriseLireEmployes() throws Exception {
        String tokenAdmin = userFixtures.getAdminToken();

        mockMvc.perform(get("/employes")
                .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk());
    }
}
