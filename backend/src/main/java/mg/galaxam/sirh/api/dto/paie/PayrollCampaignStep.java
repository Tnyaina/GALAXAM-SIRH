package mg.galaxam.sirh.api.dto.paie;

import java.util.List;

/**
 * Représente l'état d'une étape du workflow de campagne de paie,
 * tel que calculé côté backend et consommé directement par le frontend.
 *
 * <p>Le frontend NE doit PAS recalculer les états — il affiche ce que le backend fournit.
 *
 * @param code        Identifiant sémantique de l'étape (PREPARATION, POPULATION, VARIABLES,
 *                    CALCULATION, CONTROL, VALIDATION, OUTPUTS)
 * @param label       Libellé affiché à la DAF
 * @param state       État de l'étape : COMPLETED | CURRENT | AVAILABLE | WARNING | BLOCKED | LOCKED
 * @param destination URL de navigation canonique pour cette étape (ex: /paie/periodes/42/variables)
 * @param issues      Problèmes bloquants pour cette étape (vides si état OK)
 * @param warnings    Avertissements non bloquants pour cette étape
 */
public record PayrollCampaignStep(
        String code,
        String label,
        String state,
        String destination,
        List<String> issues,
        List<String> warnings) {

    public PayrollCampaignStep {
        issues = issues == null ? List.of() : List.copyOf(issues);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    /** Retourne vrai si cette étape est accessible (cliquable) dans le stepper. */
    public boolean navigable() {
        return "COMPLETED".equals(state) || "CURRENT".equals(state)
                || "AVAILABLE".equals(state) || "WARNING".equals(state);
    }
}
