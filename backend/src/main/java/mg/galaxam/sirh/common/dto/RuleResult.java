package mg.galaxam.sirh.common.dto;

import java.util.Map;

public record RuleResult(
        String code,
        String result,
        String message,
        Map<String, Object> details) {

    public RuleResult {
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public static RuleResult pass(String code) {
        return new RuleResult(code, "PASS", null, Map.of());
    }
}
