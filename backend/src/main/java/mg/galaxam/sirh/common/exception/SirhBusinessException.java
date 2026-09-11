package mg.galaxam.sirh.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception pour violation de règle métier ou validation fonctionnelle (HTTP 400 Bad Request par défaut).
 * Hérite d'IllegalArgumentException pour préserver la compatibilité ascendante avec l'écosystème Spring et les tests existants.
 */
public class SirhBusinessException extends IllegalArgumentException {

    private final SirhErrorCode errorCode;
    private final HttpStatus httpStatus;

    public SirhBusinessException(SirhErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.httpStatus = errorCode.getHttpStatus();
    }

    public SirhBusinessException(SirhErrorCode errorCode, String customMessage) {
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
