package mg.galaxam.sirh.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception pour conflit d'état métier ou concurrence (HTTP 409 Conflict par défaut).
 * Hérite d'IllegalStateException pour préserver la compatibilité ascendante avec l'écosystème Spring et les tests existants.
 */
public class SirhConflictException extends IllegalStateException {

    private final SirhErrorCode errorCode;
    private final HttpStatus httpStatus;

    public SirhConflictException(SirhErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.httpStatus = errorCode.getHttpStatus();
    }

    public SirhConflictException(SirhErrorCode errorCode, String customMessage) {
        super(customMessage != null && !customMessage.isBlank() ? customMessage : errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.httpStatus = errorCode.getHttpStatus();
    }

    public SirhErrorCode getErrorCode() {
        return errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
