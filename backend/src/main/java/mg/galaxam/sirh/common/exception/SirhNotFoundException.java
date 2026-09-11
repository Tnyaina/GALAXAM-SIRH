package mg.galaxam.sirh.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception levée lorsqu'une ressource métier est introuvable (HTTP 404 Not Found).
 */
public class SirhNotFoundException extends RuntimeException {

    private final SirhErrorCode errorCode;
    private final HttpStatus httpStatus;

    public SirhNotFoundException(SirhErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.httpStatus = errorCode.getHttpStatus();
    }

    public SirhNotFoundException(SirhErrorCode errorCode, String customMessage) {
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
