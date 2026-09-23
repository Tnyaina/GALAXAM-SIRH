package mg.galaxam.sirh.paie.api.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import mg.galaxam.sirh.common.dto.ActionCapability;
import mg.galaxam.sirh.common.dto.BusinessIssue;

/**
 * Contexte complet d'une période de paie retourné au frontend.
 *
 * <p>Sprint 2B : Enrichi avec les étapes de campagne résolues côté backend
 * ({@code campaignSteps}, {@code currentStep}, {@code recalculationRequired})
 * pour alimenter le stepper wizard du frontend sans logique de décision côté client.
 */
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
        NextStep nextStep,

        /**
         * Sprint 2B : Code de l'étape courante dans le workflow de campagne.
         * Exemples : "PREPARATION", "POPULATION", "VARIABLES", "CALCULATION",
         *            "CONTROL", "VALIDATION", "OUTPUTS".
         * Peut être {@code null} si la période n'est pas encore active (pointage non gelé).
         */
        String currentStep,

        /**
         * Sprint 2B : Liste ordonnée des étapes du workflow de campagne avec leur état calculé.
         * Chaque élément contient : code, label, state, destination, issues, warnings.
         * Le frontend affiche ces étapes dans le PayrollPeriodStepper sans recalculer.
         */
        List<PayrollCampaignStep> campaignSteps,

        /**
         * Vrai si une source dont la version est traçable a été modifiée depuis le dernier
         * calcul. La couverture actuelle porte sur les variables de campagne datées et
         * les versions de snapshots Pointage rattachées aux bulletins.
         */
        boolean recalculationRequired,

        /**
         * Sprint 2B : Message explicatif en français pour la DAF si {@code recalculationRequired == true}.
         * Ex : "3 variables ont été modifiées depuis le dernier calcul."
         */
        String recalculationReason) {

    public PayrollPeriodContextView {
        availableActions = availableActions == null ? Map.of() : Map.copyOf(availableActions);
        issues = issues == null ? List.of() : List.copyOf(issues);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        outputs = outputs == null ? List.of() : List.copyOf(outputs);
        campaignSteps = campaignSteps == null ? List.of() : List.copyOf(campaignSteps);
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
