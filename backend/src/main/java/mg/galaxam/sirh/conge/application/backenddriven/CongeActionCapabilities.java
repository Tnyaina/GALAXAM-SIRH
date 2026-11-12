package mg.galaxam.sirh.conge.application.backenddriven;

import java.util.LinkedHashMap;
import java.util.Map;
import mg.galaxam.sirh.common.dto.ActionCapability;

/** Capacités exposées au front à partir des droits et verrous déjà évalués. */
public final class CongeActionCapabilities {
    private CongeActionCapabilities() {}

    public static Map<String, ActionCapability> actions(String statut, boolean canWrite,
            boolean canValidate, boolean isOwner, boolean future, boolean periodeFermee,
            boolean pointageVerrouille) {
        boolean brouillon = "BROUILLON".equalsIgnoreCase(statut);
        boolean attente = "EN_ATTENTE".equalsIgnoreCase(statut);
        boolean valide = "VALIDE".equalsIgnoreCase(statut);
        Map<String, ActionCapability> actions = new LinkedHashMap<>();
        actions.put("EDIT", editCapability(statut, canWrite, isOwner, periodeFermee,
                periodeFermee ? "PAYROLL_PERIOD_CLOSED" : "INVALID_STATE"));
        actions.put("SUBMIT", capability(canWrite, brouillon && !periodeFermee,
                periodeFermee ? "PAYROLL_PERIOD_CLOSED" : "INVALID_STATE"));
        ActionCapability validation = capability(canValidate, attente && !periodeFermee && !pointageVerrouille,
                periodeFermee ? "PAYROLL_PERIOD_CLOSED"
                        : pointageVerrouille && attente ? "CONGE_POINTAGE_VERROUILLE" : "INVALID_STATE");
        actions.put("VALIDATE", validation);
        actions.put("APPROVE", validation); // compatibilité du frontend existant
        actions.put("REJECT", capability(canValidate, attente && !periodeFermee,
                periodeFermee ? "PAYROLL_PERIOD_CLOSED" : "INVALID_STATE"));
        boolean cancelAllowed = (brouillon || attente || valide) && !periodeFermee
                && !(valide && pointageVerrouille);
        actions.put("CANCEL", capability(canWrite || canValidate, cancelAllowed,
                periodeFermee ? "PAYROLL_PERIOD_CLOSED"
                        : valide && pointageVerrouille ? "CONGE_POINTAGE_VERROUILLE" : "INVALID_STATE"));
        actions.put("MODIFY_DATES", capability(canValidate, valide && future && !periodeFermee && !pointageVerrouille,
                periodeFermee ? "PAYROLL_PERIOD_CLOSED"
                        : valide && pointageVerrouille ? "CONGE_POINTAGE_VERROUILLE"
                        : (valide ? "LEAVE_ALREADY_STARTED" : "INVALID_STATE")));
        return actions;
    }

    private static ActionCapability capability(boolean authorized, boolean stateAllowed, String reason) {
        if (!authorized) return ActionCapability.denied("FORBIDDEN");
        return stateAllowed ? ActionCapability.permit() : ActionCapability.denied(reason);
    }

    public static ActionCapability editCapability(String statut, boolean canWrite, boolean isOwner,
            boolean periodeFermee, String reason) {
        boolean editableState = "BROUILLON".equalsIgnoreCase(statut) || "EN_ATTENTE".equalsIgnoreCase(statut);
        return capability(canWrite || isOwner, editableState && !periodeFermee, reason);
    }
}
