package mg.galaxam.sirh.paie.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import mg.galaxam.sirh.paie.api.dto.PayrollPeriodContextView.PayrollCampaignStep;
import mg.galaxam.sirh.paie.api.dto.PayrollPeriodContextView.PayrollOutputView;
import mg.galaxam.sirh.paie.api.dto.PayrollPeriodContextView;
import mg.galaxam.sirh.paie.api.dto.PayrollPeriodContextView.PayrollPeriodSummary;
import mg.galaxam.sirh.paie.api.dto.PayrollWorkflowStatuses.PayrollPhaseStatus;
import mg.galaxam.sirh.paie.api.dto.PayrollWorkflowStatuses;
import org.junit.jupiter.api.Test;

class PayrollPeriodContextJsonContractTest {
    @Test
    void campaignResponseKeepsItsJsonShape() throws Exception {
        var statuses = new PayrollWorkflowStatuses(
                new PayrollPhaseStatus("FIGE", "Figé", "success"),
                new PayrollPhaseStatus("A_CONTROLER", "À contrôler", "warning"));
        var period = new PayrollPeriodSummary(42, "2026-01", "Janvier 2026",
                null, null, "VALIDEE", 3, statuses);
        var view = new PayrollPeriodContextView(null, period, period,
                Map.of(), List.of(), List.of(), List.of(new PayrollOutputView("BULLETIN", "READY", 2, "abc", "2026-01")),
                new PayrollPeriodContextView.NextStep("CALCULATION", "/paie/periodes/42/calcul"),
                "CONTROL", List.of(new PayrollCampaignStep("CONTROL", "Contrôle", "LOCKED", "/controle",
                        List.of(), List.of())), false, null);
        var json = new ObjectMapper().findAndRegisterModules().valueToTree(view);
        assertEquals("2026-01", json.path("selectedPeriod").path("code").asText());
        assertEquals("FIGE", json.path("selectedPeriod").path("workflowStatuses").path("pointage").path("code").asText());
        assertEquals("BULLETIN", json.path("outputs").get(0).path("code").asText());
        assertEquals("CONTROL", json.path("campaignSteps").get(0).path("code").asText());
        assertTrue(json.path("campaignSteps").get(0).path("readOnly").asBoolean());
        assertTrue(json.path("campaignSteps").get(0).path("accessible").asBoolean());
        assertFalse(json.path("recalculationRequired").asBoolean());
    }
}
