package mg.galaxam.sirh.integration.paie;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.api.dto.paie.stc.StcCalculRequest;
import mg.galaxam.sirh.fixtures.TestEmployeFixtures.CreatedEmploye;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Integration Tests - API StcController (Simulation, Validation Reçu, Apurement Prêts)")
class StcControllerIntegrationTest extends BaseIntegrationTest {

    private CreatedEmploye employeDepart;
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

        // Salarié avec ancienneté, solde de congés et prêt en cours
        employeDepart = employeFixtures.createEmployeWithSalary(
                "EMP-DEP-01",
                "RAKOTO",
                "Jean",
                1,
                new BigDecimal("800000.00"),
                "PERMANENT",
                1
        );

        // Créditer 10 jours de congés restants
        employeFixtures.crediterSoldeConge(employeDepart.idEmploye, 10.0);

        // Accorder un prêt d'entreprise avec échéances
        employeFixtures.createPretAvecEcheances(
                employeDepart.idEmploye,
                new BigDecimal("300000.00"),
                new BigDecimal("100000.00"),
                3
        );
    }

    @Test
    @DisplayName("GET /paie/stc/preparer/{idEmploye} - Préparation administrative du départ")
    void testPreparerStc() throws Exception {
        mockMvc.perform(get("/paie/stc/preparer/" + employeDepart.idEmploye)
                        .param("dateDepart", "2026-01-20")
                        .param("motif", "DEMISSION")
                        .header("Authorization", "Bearer " + getAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idEmploye", is(employeDepart.idEmploye)))
                .andExpect(jsonPath("$.matricule", is("EMP-DEP-01")))
                .andExpect(jsonPath("$.salaireBase", is(800000.0)))
                .andExpect(jsonPath("$.soldeCongesRestant", is(10.0)))
                .andExpect(jsonPath("$.tauxJournalierConge", notNullValue()))
                .andExpect(jsonPath("$.capitalRestantPrets", is(300000.0)));
    }

    @Test
    @DisplayName("POST /paie/stc/calculer - Simulation sans persistance ni apurement")
    void testSimulerStc() throws Exception {
        StcCalculRequest req = new StcCalculRequest();
        req.setIdEmploye(employeDepart.idEmploye);
        req.setIdPeriodePaie(idPeriode);
        req.setDateDepart(Date.valueOf("2026-01-20"));
        req.setMotifRupture("DEMISSION");
        req.setSimulation(true);

        mockMvc.perform(post("/paie/stc/calculer")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idEmploye", is(employeDepart.idEmploye)))
                .andExpect(jsonPath("$.totalBrutStc", notNullValue()))
                .andExpect(jsonPath("$.indemniteCongesPayes", notNullValue()))
                .andExpect(jsonPath("$.soldePretsDeduit", is(300000.0)))
                .andExpect(jsonPath("$.netAPayerStc", notNullValue()))
                .andExpect(jsonPath("$.lignes", not(empty())));

        // Vérifier qu'aucun enregistrement dans paie_stc n'a été créé
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM paie_stc WHERE id_employe = ?")) {
            ps.setInt(1, employeDepart.idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "La simulation de STC ne doit rien insérer");
            }
        }
    }

    @Test
    @DisplayName("POST /paie/stc/valider & GET /paie/stc/{id}/recu - Émission du reçu officiel et apurement des prêts")
    void testValiderStcEtRecu() throws Exception {
        StcCalculRequest req = new StcCalculRequest();
        req.setIdEmploye(employeDepart.idEmploye);
        req.setIdPeriodePaie(idPeriode);
        req.setDateDepart(Date.valueOf("2026-01-20"));
        req.setMotifRupture("DEMISSION");
        req.setSimulation(false); // Validation réelle

        // 1. Validation de l'émission STC
        String responseContent = mockMvc.perform(post("/paie/stc/valider")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idStc", notNullValue()))
                .andExpect(jsonPath("$.numeroRecu", notNullValue()))
                .andExpect(jsonPath("$.numeroRecu", containsString("STC-")))
                .andExpect(jsonPath("$.netAPayerStc", notNullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Récupérer l'ID généré
        int idStc = objectMapper.readTree(responseContent).get("idStc").asInt();

        // 2. Vérification de la persistance en base dans paie_stc
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT numero_recu, net_a_payer_stc FROM paie_stc WHERE id_stc = ?")) {
            ps.setInt(1, idStc);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertNotNull(rs.getString("numero_recu"));
                assertTrue(rs.getBigDecimal("net_a_payer_stc").compareTo(BigDecimal.ZERO) > 0);
            }
        }

        // 3. Vérification de l'apurement intégral du prêt d'entreprise
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT statut, capital_restant_du FROM paie_pret WHERE id_employe = ?")) {
            ps.setInt(1, employeDepart.idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("SOLDE", rs.getString("statut"), "Le prêt doit être apuré et soldé lors de la validation du STC");
                assertEquals(new BigDecimal("0.00"), rs.getBigDecimal("capital_restant_du"));
            }
        }

        // 4. Consultation du reçu officiel (quittance libératoire)
        mockMvc.perform(get("/paie/stc/" + idStc + "/recu")
                        .header("Authorization", "Bearer " + getAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroRecu", containsString("STC-")))
                .andExpect(jsonPath("$.nomComplet", containsString("RAKOTO")))
                .andExpect(jsonPath("$.netAPayer", notNullValue()))
                .andExpect(jsonPath("$.mentionLiberatoire", notNullValue()))
                .andExpect(jsonPath("$.formuleManuscriteObligatoire", notNullValue()));

        // 5. Liste des reçus STC
        mockMvc.perform(get("/paie/stc/liste")
                        .header("Authorization", "Bearer " + getAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].idStc", is(idStc)));
    }
}
