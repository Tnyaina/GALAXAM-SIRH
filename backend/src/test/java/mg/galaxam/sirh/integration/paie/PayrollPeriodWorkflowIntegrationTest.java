package mg.galaxam.sirh.integration.paie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.paie.application.workflow.PayrollPeriodCommand;
import mg.galaxam.sirh.paie.application.workflow.PayrollPeriodCommandRequest;
import mg.galaxam.sirh.paie.application.workflow.PayrollPeriodUserContext;
import mg.galaxam.sirh.paie.application.workflow.PayrollPeriodWorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

@DisplayName("Workflow canonique des périodes de paie")
class PayrollPeriodWorkflowIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private PayrollPeriodWorkflowService workflowService;

    @BeforeEach
    void cleanDatabase() {
        databaseCleaner.cleanTransactionalData();
    }

    @Test
    @DisplayName("L'affectation générique d'un statut n'existe plus")
    void genericStatusMutationIsGone() throws Exception {
        int periodId = createPeriod("WF-GONE", "BROUILLON");

        mockMvc.perform(put("/periodes/" + periodId + "/statut")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statutCode\":\"CLOTUREE\"}"))
                .andExpect(status().isGone());
    }

    @Test
    @DisplayName("OPEN applique la matrice, incrémente la version et historise la commande")
    void openUsesMatrixVersionAndHistory() throws Exception {
        int periodId = createPeriod("WF-OPEN", "BROUILLON");

        mockMvc.perform(post("/periodes/" + periodId + "/actions/OPEN")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("EN_COURS"))
                .andExpect(jsonPath("$.decision").value("ALLOWED"))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.availableActions.SUBMIT.allowed").value(false))
                .andExpect(jsonPath("$.availableActions.SUBMIT.reasonCodes[0]").value("POINTAGE_EMPTY"));

        assertThat(singleText(
                "SELECT commande || ':' || statut_source || ':' || statut_cible "
                        + "FROM periode_paie_transition WHERE id_periode_paie = ?", periodId))
                .isEqualTo("OPEN:BROUILLON:EN_COURS");
    }

    @Test
    @DisplayName("Un double clic avec la même version produit un seul succès et un conflit")
    void concurrentDoubleClickHasOneSuccessAndOneConflict() throws Exception {
        int periodId = createPeriod("WF-OCC", "BROUILLON");
        PayrollPeriodUserContext admin = new PayrollPeriodUserContext(1, Set.of("ROLE_ADMIN"));
        CountDownLatch start = new CountDownLatch(1);

        var executor = Executors.newFixedThreadPool(2);
        try {
            var action = (java.util.concurrent.Callable<Boolean>) () -> {
                start.await();
                PayrollPeriodCommandRequest request = new PayrollPeriodCommandRequest();
                request.setVersion(1);
                try {
                    workflowService.execute(periodId, PayrollPeriodCommand.OPEN, request, admin);
                    return true;
                } catch (SirhBusinessException conflict) {
                    assertThat(conflict.getErrorCode().name()).isEqualTo("PAYROLL_PERIOD_VERSION_CONFLICT");
                    return false;
                }
            };
            var first = executor.submit(action);
            var second = executor.submit(action);
            start.countDown();

            assertThat(java.util.List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }

        assertThat(singleInt(
                "SELECT COUNT(*) FROM periode_paie_transition WHERE id_periode_paie = ?", periodId))
                .isEqualTo(1);
        assertThat(getPayrollPeriodVersion(periodId)).isEqualTo(2);
    }

    @Test
    @DisplayName("Une période clôturée ne peut jamais être ramenée implicitement au brouillon")
    void closedToDraftIsBlocked() throws Exception {
        int periodId = createPeriod("WF-BLOCK", "CLOTUREE");

        mockMvc.perform(post("/periodes/" + periodId + "/actions/OPEN")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYROLL_PERIOD_TRANSITION_BLOCKED"))
                .andExpect(jsonPath("$.details.command").value("OPEN"))
                .andExpect(jsonPath("$.issues[0].code").value("PAYROLL_PERIOD_TRANSITION_BLOCKED"));

        assertThat(singleText(
                "SELECT s.code FROM periode_paie p JOIN ref_statut_periode_paie s "
                        + "ON s.id_statut_periode=p.id_statut_periode WHERE p.id_periode_paie=?", periodId))
                .isEqualTo("CLOTUREE");
    }

    @Test
    @DisplayName("REOPEN est explicite et ne dégèle jamais un snapshot déjà consommé")
    void reopenPreservesConsumedSnapshot() throws Exception {
        int periodId = createPeriod("WF-REOPEN", "CLOTUREE");
        int employeeId = employeFixtures.createActiveEmploye(
                "WF-EMP", "Workflow", "Snapshot", 1, LocalDate.now().minusYears(1)).idEmploye;
        insertConsumedSnapshot(employeeId, periodId);

        mockMvc.perform(post("/periodes/" + periodId + "/actions/REOPEN")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1,\"motif\":\"Correction contrôlée du pointage\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("EN_COURS"))
                .andExpect(jsonPath("$.version").value(2));

        assertThat(singleText(
                "SELECT statut_snapshot FROM pointage_cumul_periode "
                        + "WHERE id_employe=? AND id_periode_paie=?", employeeId, periodId))
                .isEqualTo("CONSOMME");
    }

    @Test
    @DisplayName("Le contexte explicite fournit la période sélectionnée et les capacités backend")
    void explicitContextProvidesBackendCapabilities() throws Exception {
        int periodId = createPeriod("WF-CONTEXT", "VALIDEE");

        mockMvc.perform(get("/periodes/context")
                        .param("periodId", String.valueOf(periodId))
                        .header("Authorization", "Bearer " + getAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedPeriod.id").value(periodId))
                .andExpect(jsonPath("$.selectedPeriod.state").value("VALIDEE"))
                .andExpect(jsonPath("$.availableActions.CALCULATE.allowed").value(false))
                .andExpect(jsonPath("$.availableActions.CALCULATE.reasonCodes[0]").value("POINTAGE_NOT_FROZEN"));

        int employeeId = employeFixtures.createActiveEmploye(
                "WF-CONTEXT-EMP", "Workflow", "Context", 1, LocalDate.now().minusYears(1)).idEmploye;
        insertConsumedSnapshot(employeeId, periodId);

        mockMvc.perform(get("/periodes/context")
                        .param("periodId", String.valueOf(periodId))
                        .header("Authorization", "Bearer " + getAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableActions.CALCULATE.allowed").value(true));
    }

    private int createPeriod(String code, String state) {
        return employeFixtures.createPeriodePaie(
                code, LocalDate.now().minusMonths(2), LocalDate.now().minusMonths(1), state);
    }

    private void insertConsumedSnapshot(int employeeId, int periodId) throws Exception {
        String sql = "INSERT INTO pointage_cumul_periode "
                + "(id_employe,id_periode_paie,version_snapshot,statut_snapshot,nb_jours_ouvrables,"
                + "nb_jours_presents,nb_jours_absence,nb_jours_conge,nb_jours_ferie_chome,"
                + "nb_jours_ferie_travaille,total_heures_travaillees,heures_hs_30,heures_hs_50,"
                + "heures_nuit_total,heures_absence_retenues,total_minutes_retard,nb_jours_deplacement,"
                + "montant_deplacement,nb_jours_repas,montant_repas,gele_at) "
                + "VALUES (?,?,1,'CONSOMME',22,22,0,0,0,0,176,0,0,0,0,0,0,0,0,0,CURRENT_TIMESTAMP)";
        try (var connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, employeeId);
            statement.setInt(2, periodId);
            statement.executeUpdate();
        }
    }

    private int singleInt(String sql, int... parameters) throws Exception {
        try (var connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setInt(index + 1, parameters[index]);
            }
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private String singleText(String sql, int... parameters) throws Exception {
        try (var connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setInt(index + 1, parameters[index]);
            }
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getString(1);
            }
        }
    }
}
