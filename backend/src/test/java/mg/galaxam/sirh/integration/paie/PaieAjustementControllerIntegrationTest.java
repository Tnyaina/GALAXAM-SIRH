package mg.galaxam.sirh.integration.paie;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.api.dto.paie.PaieAjustementRequest;
import mg.galaxam.sirh.api.dto.paie.StatutAjustementRequest;
import mg.galaxam.sirh.fixtures.TestEmployeFixtures.CreatedEmploye;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Integration Tests - API PaieAjustementController (Variables, Primes, Acomptes)")
class PaieAjustementControllerIntegrationTest extends BaseIntegrationTest {

    private CreatedEmploye employe;
    private int idPeriode;

    @BeforeEach
    void setUp() {
        databaseCleaner.cleanTransactionalData();

        idPeriode = employeFixtures.createPeriodePaie(
                "PER-2026-01",
                LocalDate.of(2025, 12, 26),
                LocalDate.of(2026, 1, 25),
                "EN_COURS"
        );

        employe = employeFixtures.createEmployeWithSalary(
                "EMP-AJUST-01",
                "RAKOTOARISOA",
                "Faly",
                1,
                new BigDecimal("700000.00"),
                "PERMANENT",
                0
        );
    }

    @Test
    @DisplayName("GET /paie/ajustements/types-mouvements - Liste des types de mouvements de paie actifs")
    void testListerTypesMouvements() throws Exception {
        mockMvc.perform(get("/paie/ajustements/types-mouvements")
                        .header("Authorization", "Bearer " + getAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())))
                .andExpect(jsonPath("$[*].code", hasItem("PRIME_PERFORMANCE")));
    }

    @Test
    @DisplayName("POST /paie/ajustements & PUT /{id}/statut - Création et modification de statut d'un ajustement")
    void testCreerEtModifierAjustement() throws Exception {
        // 1. Création d'une prime de performance
        PaieAjustementRequest req = new PaieAjustementRequest();
        req.setIdEmploye(employe.idEmploye);
        req.setIdPeriodePaie(idPeriode);
        req.setTypeMouvement("PRIME_PERFORMANCE");
        req.setSens("GAIN");
        req.setMontant(new BigDecimal("75000.00"));
        req.setMotif("Objectif mensuel logistique atteint");

        String response = mockMvc.perform(post("/paie/ajustements")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.idAvance", notNullValue()))
                .andExpect(jsonPath("$.typeMouvement", is("PRIME_PERFORMANCE")))
                .andExpect(jsonPath("$.montant", is(75000.0)))
                .andExpect(jsonPath("$.statut", is("APPROUVE")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        int idAvance = objectMapper.readTree(response).get("idAvance").asInt();

        // 2. Vérification de la persistance en base
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT montant, statut, motif FROM paie_avance_acompte WHERE id_avance = ?")) {
            ps.setInt(1, idAvance);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(new BigDecimal("75000.00"), rs.getBigDecimal("montant"));
                assertEquals("APPROUVE", rs.getString("statut"));
                assertEquals("Objectif mensuel logistique atteint", rs.getString("motif"));
            }
        }

        // 3. Consultation filtrée par employé
        mockMvc.perform(get("/paie/ajustements")
                        .param("idEmploye", String.valueOf(employe.idEmploye))
                        .header("Authorization", "Bearer " + getAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].idAvance", is(idAvance)))
                .andExpect(jsonPath("$[0].matricule", is("EMP-AJUST-01")));

        // 4. Modification de statut (Annulation)
        StatutAjustementRequest reqStatut = new StatutAjustementRequest();
        reqStatut.setStatut("ANNULE");
        reqStatut.setMotif("Erreur de saisie DAF");

        mockMvc.perform(put("/paie/ajustements/" + idAvance + "/statut")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(reqStatut)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.statut", is("ANNULE")));

        // 5. Vérification du statut mis à jour en base
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT statut FROM paie_avance_acompte WHERE id_avance = ?")) {
            ps.setInt(1, idAvance);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("ANNULE", rs.getString("statut"));
            }
        }
    }
}
