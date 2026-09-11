package mg.galaxam.sirh.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.fixtures.TestEmployeFixtures;
import mg.galaxam.sirh.model.conge.DemandeConge;
import mg.galaxam.sirh.model.conge.service.CongeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class CongeSecurityAndApiTest extends BaseIntegrationTest {

    @Autowired
    private CongeService congeService;

    private int idDemande;

    @BeforeEach
    public void setUp() throws Exception {
        databaseCleaner.cleanTransactionalData();
        int adminId = userFixtures.getAdminUserId();

        // Créer employé avec contrat couvrant la période
        TestEmployeFixtures.CreatedEmploye emp = employeFixtures.createActiveEmploye(
                "EMP040", "RAKOTOMALALA", "Eric", 1, LocalDate.now().minusYears(2));

        // Créditer congés
        employeFixtures.crediterSoldeConge(emp.idEmploye, 15.0);

        // Créer une demande en attente
        DemandeConge d = new DemandeConge();
        d.setIdEmploye(emp.idEmploye);
        d.setIdTypeConge(5); // ANNUEL
        d.setDateDebut(Date.valueOf(LocalDate.now().plusDays(20)));
        d.setDateFin(Date.valueOf(LocalDate.now().plusDays(24)));
        d.setCommentaire("Congé test sécurité");

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            DemandeConge created = congeService.creerDemande(d, adminId, c);
            idDemande = created.getIdDemandeConge();
            c.commit();
        }
    }

    @Test
    @DisplayName("RBAC Congés : Agent de Pointage interdit de valider un congé (403 Forbidden)")
    public void testAgentPointage_InterditDeValiderConge() throws Exception {
        String tokenAgent = userFixtures.getPointageToken();

        mockMvc.perform(post("/conges/demandes/" + idDemande + "/valider")
                .header("Authorization", "Bearer " + tokenAgent))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RBAC Congés : Agent de Pointage interdit d'accéder aux demandes à valider (403 Forbidden)")
    public void testAgentPointage_InterditAccesDemandesAValider() throws Exception {
        String tokenAgent = userFixtures.getPointageToken();

        mockMvc.perform(get("/conges/demandes/validation")
                .header("Authorization", "Bearer " + tokenAgent))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RBAC Congés : Administrateur autorisé à valider un congé (200 OK)")
    public void testAdmin_AutoriseValiderConge() throws Exception {
        String tokenAdmin = userFixtures.getAdminToken();

        mockMvc.perform(post("/conges/demandes/" + idDemande + "/valider")
                .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk());
    }
}
