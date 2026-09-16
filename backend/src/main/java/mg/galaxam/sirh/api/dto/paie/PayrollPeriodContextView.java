package mg.galaxam.sirh.api.dto.paie;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import mg.galaxam.sirh.api.dto.common.ActionCapability;
import mg.galaxam.sirh.api.dto.common.BusinessIssue;

public record PayrollPeriodContextView(
        LocalDate businessDate,
        PayrollPeriodSummary currentPeriod,
        PayrollPeriodSummary selectedPeriod,
        Map<String, ActionCapability> availableActions,
        List<BusinessIssue> issues,
        List<BusinessIssue> warnings,
        List<PayrollOutputView> outputs,
        /**
         * P1-02 : Étape suivante calculée côté backend selon le statut de la période et le rôle de l'utilisateur.
         * Le frontend utilise {@code nextStep.destination} pour la navigation et ne reconstruit pas cette logique.
         * Peut être {@code null} si aucune action n'est disponible.
         */
        NextStep nextStep) {

    public PayrollPeriodContextView {
        availableActions = availableActions == null ? Map.of() : Map.copyOf(availableActions);
        issues = issues == null ? List.of() : List.copyOf(issues);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        outputs = outputs == null ? List.of() : List.copyOf(outputs);
        // nextStep peut être null (aucune action disponible)
    }

    /**
     * Étape de navigation suivante calculée par le backend.
     *
     * @param code        identifiant sémantique de l'étape (ex : "SAISIE", "VALIDATION", "COCKPIT")
     * @param destination URL absolue de destination (ex : "/pointages?tab=saisie")
     */
    public record NextStep(String code, String destination) {}
}
