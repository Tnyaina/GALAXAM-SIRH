package mg.galaxam.sirh.api.dto.common;

import java.util.Map;

public record BusinessIssue(String code, String message, Map<String, Object> details) {
    public BusinessIssue {
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
