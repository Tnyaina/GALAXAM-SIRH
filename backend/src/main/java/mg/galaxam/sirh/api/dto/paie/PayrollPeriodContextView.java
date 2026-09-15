package mg.galaxam.sirh.api.dto.paie;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import mg.galaxam.sirh.api.dto.common.ActionCapability;
import mg.galaxam.sirh.api.dto.common.BusinessIssue;

public record PayrollPeriodContextView(
        LocalDate businessDate,
        PayrollPeriodSummary currentPeriod,
        PayrollPeriodSummary selectedPeriod,
        Map<String, ActionCapability> availableActions,
        List<BusinessIssue> issues,
        List<BusinessIssue> warnings) {

    public PayrollPeriodContextView {
        availableActions = availableActions == null ? Map.of() : Map.copyOf(availableActions);
        issues = issues == null ? List.of() : List.copyOf(issues);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
