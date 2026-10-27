package mg.galaxam.sirh.common.exception;

import org.springframework.http.HttpStatus;
import java.util.List;
import java.util.Map;
import mg.galaxam.sirh.common.dto.BusinessIssue;

/**
 * Exception pour violation de règle métier ou validation fonctionnelle (HTTP 400 Bad Request par défaut).
 * Porte un code stable, des détails structurés et les issues exposées par l'API.
 */
public class SirhBusinessException extends RuntimeException {

    private final SirhErrorCode errorCode;
    private final HttpStatus httpStatus;
    private final Map<String, Object> details;
    private final List<BusinessIssue> issues;

    public SirhBusinessException(SirhErrorCode errorCode) {
        this(errorCode, null, Map.of(), List.of());
    }

    public SirhBusinessException(SirhErrorCode errorCode, String customMessage) {
        this(errorCode, customMessage, Map.of(), List.of());
    }

    public SirhBusinessException(
            SirhErrorCode errorCode,
            String customMessage,
            Map<String, Object> details,
            List<BusinessIssue> issues) {
        super(customMessage != null && !customMessage.isBlank() ? customMessage : errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.httpStatus = errorCode.getHttpStatus();
        this.details = details == null ? Map.of() : Map.copyOf(details);
        this.issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public SirhErrorCode getErrorCode() {
        return errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public List<BusinessIssue> getIssues() {
        return issues;
    }
}
