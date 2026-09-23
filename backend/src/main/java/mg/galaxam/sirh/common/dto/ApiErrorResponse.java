package mg.galaxam.sirh.common.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class ApiErrorResponse {
    private LocalDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
    private String code;
    private Map<String, Object> details;
    private List<BusinessIssue> issues;

    public ApiErrorResponse() {
        this.timestamp = LocalDateTime.now();
    }

    public ApiErrorResponse(int status, String error, String message, String path) {
        this(status, error, message, path, null);
    }

    public ApiErrorResponse(int status, String error, String message, String path, String code) {
        this(status, error, message, path, code, Map.of(), List.of());
    }

    public ApiErrorResponse(int status, String error, String message, String path, String code,
            Map<String, Object> details, List<BusinessIssue> issues) {
        this.timestamp = LocalDateTime.now();
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
        this.code = code;
        this.details = details == null ? Map.of() : Map.copyOf(details);
        this.issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public List<BusinessIssue> getIssues() {
        return issues;
    }

    public void setIssues(List<BusinessIssue> issues) {
        this.issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
