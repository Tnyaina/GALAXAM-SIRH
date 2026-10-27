package mg.galaxam.sirh.common.dto;

import java.util.List;
import java.util.Map;

/** Vocabulaire de décision partagé ; les règles restent la propriété de chaque domaine. */
public record BusinessDecision(
        Integer resourceId,
        String state,
        DecisionStatus decision,
        String message,
        List<RuleResult> ruleResults,
        List<BusinessIssue> blockingIssues,
        List<BusinessIssue> warnings,
        Map<String, ActionCapability> availableActions,
        Integer version) {

    public BusinessDecision {
        ruleResults = ruleResults == null ? List.of() : List.copyOf(ruleResults);
        blockingIssues = blockingIssues == null ? List.of() : List.copyOf(blockingIssues);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        availableActions = availableActions == null ? Map.of() : Map.copyOf(availableActions);
    }
}
