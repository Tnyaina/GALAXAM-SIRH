package mg.galaxam.sirh.integration.paie;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.api.dto.paie.PaieAjustementRequest;
import mg.galaxam.sirh.fixtures.TestEmployeFixtures.CreatedEmploye;
import mg.galaxam.sirh.model.paie.workflow.PayrollAdjustmentCommand;
import mg.galaxam.sirh.model.paie.workflow.PayrollAdjustmentCommandRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Integration Tests - P1-01 — API PaieAjustementController (Workflow commandes)")
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
    @DisplayName("P1-01 CONTRE-EXEMPLE — PUT /{id}/statut retourne 410 Gone (ancienne API détectée)")
    void testOldPutStatutEndpointReturns410() throws Exception {
        // Ce test échouait AVANT la correction (retournait 200 ou 400)
        // Il prouve que l'ancien endpoint est remplacé par POST /commands
        mockMvc.perform(put("/paie/ajustements/9999/statut")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statut\":\"APPROUVE\"}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code", is("ENDPOINT_DEPRECATED")))
                .andExpect(jsonPath("$.newEndpoint", containsString("/commands")));
    }

    @Test
    @DisplayName("P1-01 CONTRE-EXEMPLE — PATCH /{id}/statut retourne 410 Gone (mismatch frontend)")
    void testOldPatchStatutEndpointReturns410() throws Exception {
        // Ce test prouve le bug exact : le frontend appelait PATCH, le backend n'avait que PUT
        // Les deux retournent maintenant 410
        mockMvc.perform(patch("/paie/ajustements/9999/statut")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statut\":\"APPROUVE\"}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code", is("ENDPOINT_DEPRECATED")));
    }

    @Test
    @DisplayName("P1-01 — POST /ajustements crée en EN_ATTENTE (pas APPROUVE)")
    void testCreerAjustementEnAttente() throws Exception {
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
                // P1-01 : créé en EN_ATTENTE, pas APPROUVE
                .andExpect(jsonPath("$.statut", is("EN_ATTENTE")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        int idAvance = objectMapper.readTree(response).get("idAvance").asInt();

        // Vérification DB : statut EN_ATTENTE persiste
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT statut FROM paie_avance_acompte WHERE id_avance = ?")) {
            ps.setInt(1, idAvance);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("EN_ATTENTE", rs.getString("statut"));
            }
        }
    }

    @Test
    @DisplayName("Ownership — un type absent du référentiel actif est refusé")
    void testCreerAjustementRefuseTypeNonConfigure() throws Exception {
        PaieAjustementRequest req = new PaieAjustementRequest();
        req.setIdEmploye(employe.idEmploye);
        req.setIdPeriodePaie(idPeriode);
        req.setTypeMouvement("TYPE_CLIENT_NON_CONFIGURE");
        req.setSens("GAIN");
        req.setMontant(new BigDecimal("10000.00"));
        req.setMotif("Ne doit jamais contourner le référentiel");

        mockMvc.perform(post("/paie/ajustements")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
    }

    @Test
    @DisplayName("Ownership — le sens est toujours celui du type configuré")
    void testCreerAjustementIgnoreSensClientContradictoire() throws Exception {
        PaieAjustementRequest req = new PaieAjustementRequest();
        req.setIdEmploye(employe.idEmploye);
        req.setIdPeriodePaie(idPeriode);
        req.setTypeMouvement("PRIME_EXCEPTIONNELLE");
        req.setSens("RETENUE");
        req.setMontant(new BigDecimal("10000.00"));
        req.setMotif("Sens résolu depuis le référentiel");

        mockMvc.perform(post("/paie/ajustements")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sens", is("GAIN")));
    }

    @Test
    @DisplayName("Ownership — une variable de campagne sans période est rejetée")
    void testVariableSansPeriodeEstRejetee() throws Exception {
        PaieAjustementRequest req = request("PRIME_EXCEPTIONNELLE", "GAIN");
        req.setIdPeriodePaie(null);

        mockMvc.perform(post("/paie/ajustements")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
    }

    @Test
    @DisplayName("Ownership — une rubrique automatique ne peut pas devenir une variable")
    void testHeuresSupplementairesManuellesSontRejetees() throws Exception {
        PaieAjustementRequest req = request("HEURES_SUP_30", "GAIN");

        mockMvc.perform(post("/paie/ajustements")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Ownership — prime assiduité et note de frais ne sont pas exposées en saisie")
    void testCatalogueExclutTypesNonSaisissables() throws Exception {
        mockMvc.perform(get("/paie/ajustements/types-mouvements")
                        .param("idPeriode", String.valueOf(idPeriode))
                        .header("Authorization", "Bearer " + getAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", not(hasItems("PRIME_ASSIDUITE", "NOTE_DE_FRAIS"))));
    }

    @Test
    @DisplayName("Ownership — ajout interdit quand la période est en validation")
    void testVariableInterditeApresVerrouillage() throws Exception {
        setPeriodeState("A_VALIDER");
        assertCreationBlocked(request("PRIME_EXCEPTIONNELLE", "GAIN"), "PAYROLL_PERIOD_PREREQUISITE_BLOCKED");
    }

    @Test
    @DisplayName("Ownership — ajout interdit après validation Paie")
    void testVariableInterditeApresValidationPaie() throws Exception {
        setPeriodeState("PAIE_VALIDEE");
        assertCreationBlocked(request("PRIME_EXCEPTIONNELLE", "GAIN"), "PAYROLL_PERIOD_PREREQUISITE_BLOCKED");
    }

    @Test
    @DisplayName("Ownership — ajout interdit après clôture")
    void testVariableInterditeApresCloture() throws Exception {
        setPeriodeState("CLOTUREE");
        assertCreationBlocked(request("PRIME_EXCEPTIONNELLE", "GAIN"), "STATE_CONFLICT");
    }

    @Test
    @DisplayName("Ownership — approbation d'une variable interdite après verrouillage")
    void testModificationVariableInterditeApresVerrouillage() throws Exception {
        int idAvance = creerAjustement();
        int version = getVersion(idAvance);
        setPeriodeState("A_VALIDER");

        PayrollAdjustmentCommandRequest command = new PayrollAdjustmentCommandRequest();
        command.setCommand(PayrollAdjustmentCommand.APPROVE);
        command.setExpectedVersion(version);

        mockMvc.perform(post("/paie/ajustements/" + idAvance + "/commands")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(command)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("PAYROLL_PERIOD_PREREQUISITE_BLOCKED")));
    }

    @Test
    @DisplayName("P1-01 — POST /{id}/commands APPROVE depuis EN_ATTENTE → APPROUVE")
    void testApproveCommandFromPending() throws Exception {
        // 1. Créer un ajustement en EN_ATTENTE
        int idAvance = creerAjustement();

        // 2. Obtenir la version courante
        int version = getVersion(idAvance);

        // 3. Executer la commande APPROVE
        PayrollAdjustmentCommandRequest cmdReq = new PayrollAdjustmentCommandRequest();
        cmdReq.setCommand(PayrollAdjustmentCommand.APPROVE);
        cmdReq.setExpectedVersion(version);

        mockMvc.perform(post("/paie/ajustements/" + idAvance + "/commands")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(cmdReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("APPROUVE")))
                .andExpect(jsonPath("$.decision", is("ALLOWED")));

        // Vérification DB
        assertEquals("APPROUVE", getStatut(idAvance));
    }

    @Test
    @DisplayName("P1-01 — Double APPROVE → 409 PAYROLL_ADJUSTMENT_ALREADY_APPROVED")
    void testDoubleApproveIsBlocked() throws Exception {
        int idAvance = creerAjustement();
        int version = getVersion(idAvance);

        // Premier APPROVE
        PayrollAdjustmentCommandRequest cmd = new PayrollAdjustmentCommandRequest();
        cmd.setCommand(PayrollAdjustmentCommand.APPROVE);
        cmd.setExpectedVersion(version);
        mockMvc.perform(post("/paie/ajustements/" + idAvance + "/commands")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(cmd)))
                .andExpect(status().isOk());

        // Deuxième APPROVE — doit être bloqué
        int newVersion = getVersion(idAvance);
        PayrollAdjustmentCommandRequest cmd2 = new PayrollAdjustmentCommandRequest();
        cmd2.setCommand(PayrollAdjustmentCommand.APPROVE);
        cmd2.setExpectedVersion(newVersion);
        mockMvc.perform(post("/paie/ajustements/" + idAvance + "/commands")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(cmd2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("PAYROLL_ADJUSTMENT_ALREADY_APPROVED")));
    }

    @Test
    @DisplayName("P1-01 — POST /{id}/commands REJECT avec motif → REJETE")
    void testRejectCommandWithReason() throws Exception {
        int idAvance = creerAjustement();
        int version = getVersion(idAvance);

        PayrollAdjustmentCommandRequest cmd = new PayrollAdjustmentCommandRequest();
        cmd.setCommand(PayrollAdjustmentCommand.REJECT);
        cmd.setReason("Prime non justifiée pour ce mois");
        cmd.setExpectedVersion(version);

        mockMvc.perform(post("/paie/ajustements/" + idAvance + "/commands")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(cmd)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("REJETE")));
    }

    @Test
    @DisplayName("P1-01 — REJECT sans motif → 400 REASON_REQUIRED")
    void testRejectWithoutReasonIsRejected() throws Exception {
        int idAvance = creerAjustement();
        int version = getVersion(idAvance);

        PayrollAdjustmentCommandRequest cmd = new PayrollAdjustmentCommandRequest();
        cmd.setCommand(PayrollAdjustmentCommand.REJECT);
        cmd.setExpectedVersion(version);
        // Pas de reason

        mockMvc.perform(post("/paie/ajustements/" + idAvance + "/commands")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(cmd)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("P1-01 — Utilisateur sans PAIE_WRITE → 403")
    void testCommandRequiresPaieWrite() throws Exception {
        mockMvc.perform(post("/paie/ajustements/1/commands")
                        .header("Authorization", "Bearer " + userFixtures.getPointageToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"command\":\"APPROVE\",\"expectedVersion\":1}"))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private int creerAjustement() throws Exception {
        PaieAjustementRequest req = new PaieAjustementRequest();
        req.setIdEmploye(employe.idEmploye);
        req.setIdPeriodePaie(idPeriode);
        req.setTypeMouvement("PRIME_PERFORMANCE");
        req.setSens("GAIN");
        req.setMontant(new BigDecimal("50000.00"));
        req.setMotif("Prime test workflow P1-01");

        String response = mockMvc.perform(post("/paie/ajustements")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("idAvance").asInt();
    }

    private PaieAjustementRequest request(String type, String sens) {
        PaieAjustementRequest req = new PaieAjustementRequest();
        req.setIdEmploye(employe.idEmploye);
        req.setIdPeriodePaie(idPeriode);
        req.setTypeMouvement(type);
        req.setSens(sens);
        req.setMontant(new BigDecimal("10000.00"));
        req.setMotif("Justification test ownership");
        return req;
    }

    private void assertCreationBlocked(PaieAjustementRequest req, String expectedCode) throws Exception {
        mockMvc.perform(post("/paie/ajustements")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is(expectedCode)));
    }

    private void setPeriodeState(String state) throws Exception {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE periode_paie SET id_statut_periode = (SELECT id_statut_periode FROM ref_statut_periode_paie WHERE code = ?) WHERE id_periode_paie = ?")) {
            ps.setString(1, state);
            ps.setInt(2, idPeriode);
            ps.executeUpdate();
        }
    }

    private int getVersion(int idAvance) throws Exception {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT updated_at FROM paie_avance_acompte WHERE id_avance = ?")) {
            ps.setInt(1, idAvance);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Timestamp ts = rs.getTimestamp("updated_at");
                    return ts != null ? (int) (ts.getTime() % Integer.MAX_VALUE) : 1;
                }
                return 1;
            }
        }
    }

    private String getStatut(int idAvance) throws Exception {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT statut FROM paie_avance_acompte WHERE id_avance = ?")) {
            ps.setInt(1, idAvance);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("statut") : null;
            }
        }
    }
}
