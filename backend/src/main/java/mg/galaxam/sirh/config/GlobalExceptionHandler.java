package mg.galaxam.sirh.config;

import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;
import mg.galaxam.sirh.api.dto.common.ApiErrorResponse;
import mg.galaxam.sirh.api.dto.common.BusinessIssue;
import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.common.exception.SirhErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Gestionnaire global des exceptions pour GALAXAM SIRH.
 * Traduit toutes les erreurs techniques en messages clairs, pédagogiques et orientés métier.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(SirhBusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleSirhBusiness(SirhBusinessException ex, HttpServletRequest request) {
        log.warn("Règle métier non satisfaite [{}] sur [{}] : {}", ex.getErrorCode(), request.getRequestURI(), ex.getMessage());
        return problem(ex.getHttpStatus(), ex.getErrorCode().name(), ex.getMessage(),
                request, ex.getDetails(), ex.getIssues());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Validation métier échouée sur [{}] : {}", request.getRequestURI(), ex.getMessage());
        return problem(HttpStatus.BAD_REQUEST, SirhErrorCode.VALIDATION_ERROR.name(),
                ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(IllegalStateException ex, HttpServletRequest request) {
        log.warn("Conflit d'état métier sur [{}] : {}", request.getRequestURI(), ex.getMessage());
        return problem(HttpStatus.CONFLICT, "STATE_CONFLICT", ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> (err.getField() != null ? err.getField() + " : " : "") + err.getDefaultMessage())
                .collect(Collectors.joining(" | "));

        log.warn("Champs de formulaire invalides sur [{}] : {}", request.getRequestURI(), details);
        var issues = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> new BusinessIssue("INVALID_FIELD", err.getDefaultMessage(),
                        java.util.Map.of("field", err.getField())))
                .toList();
        return problem(HttpStatus.BAD_REQUEST, SirhErrorCode.VALIDATION_ERROR.name(),
                "Veuillez vérifier les informations saisies : " + details,
                request, java.util.Map.of(), issues);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Violation d'intégrité BDD sur [{}] : {}", request.getRequestURI(), ex.getMessage());
        
        SirhErrorCode code = SirhErrorCode.DATA_INTEGRITY_VIOLATION;
        String userFriendlyMessage = code.getDefaultMessage();
        String exMsg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";

        if (exMsg.contains("foreign key") || exMsg.contains("violates foreign key constraint") || exMsg.contains("fk_")) {
            code = SirhErrorCode.DATA_REFERENCED;
            userFriendlyMessage = "Impossible de supprimer ou modifier cet élément car il est actuellement lié à d'autres dossiers actifs (employés, contrats, congés ou pointages).";
        } else if (exMsg.contains("duplicate key") || exMsg.contains("unique constraint") || exMsg.contains("unique")) {
            code = SirhErrorCode.DATA_DUPLICATE;
            userFriendlyMessage = "Un enregistrement comportant cette valeur unique (ex: matricule, CIN, numéro de contrat ou code) existe déjà dans l'application.";
        }
        return problem(HttpStatus.CONFLICT, code.name(), userFriendlyMessage, request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        log.warn("Fichier trop volumineux sur [{}]", request.getRequestURI());
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, SirhErrorCode.FILE_TOO_LARGE.name(),
                "Le fichier sélectionné est trop volumineux. La taille maximale autorisée est de 10 Mo.", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        log.warn("Paramètre mal formaté sur [{}] : {}", request.getRequestURI(), ex.getMessage());
        return problem(HttpStatus.BAD_REQUEST, SirhErrorCode.PARAMETER_FORMAT_INVALID.name(),
                "Le paramètre fourni dans l'adresse ou le formulaire est invalide.", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Accès refusé sur [{}] : {}", request.getRequestURI(), ex.getMessage());
        return problem(HttpStatus.FORBIDDEN, SirhErrorCode.ACCESS_DENIED.name(),
                "Vous ne disposez pas des autorisations nécessaires pour effectuer cette action.", request);
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleMessageNotReadable(org.springframework.http.converter.HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("Format de données reçu illisible sur [{}] : {}", request.getRequestURI(), ex.getMessage());
        return problem(HttpStatus.BAD_REQUEST, SirhErrorCode.PAYLOAD_INVALID.name(),
                "Le format des données transmises est incorrect (ex: date ou nombre mal formé). Veuillez vérifier votre saisie.", request);
    }

    @ExceptionHandler(java.sql.SQLException.class)
    public ResponseEntity<ApiErrorResponse> handleSqlException(java.sql.SQLException ex, HttpServletRequest request) {
        log.error("Erreur SQL lors du traitement sur [{}] : {}", request.getRequestURI(), ex.getMessage(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, SirhErrorCode.PERSISTENCE_ERROR.name(),
                "Une erreur est survenue lors de l'enregistrement des données. Veuillez réessayer ou contacter l'administrateur si le problème persiste.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Erreur serveur inattendue sur [{}]", request.getRequestURI(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, SirhErrorCode.INTERNAL_ERROR.name(),
                "Une anomalie temporaire est survenue lors du traitement de votre demande. Veuillez réessayer dans quelques instants.", request);
    }

    private ResponseEntity<ApiErrorResponse> problem(
            HttpStatus status, String code, String message, HttpServletRequest request) {
        return problem(status, code, message, request, java.util.Map.of(), java.util.List.of());
    }

    private ResponseEntity<ApiErrorResponse> problem(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            java.util.Map<String, Object> details,
            java.util.List<BusinessIssue> issues) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                status.value(), status.getReasonPhrase(), message, request.getRequestURI(),
                code, details, issues));
    }
}
