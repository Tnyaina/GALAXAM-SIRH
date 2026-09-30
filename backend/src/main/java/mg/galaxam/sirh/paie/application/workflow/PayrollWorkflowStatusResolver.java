package mg.galaxam.sirh.paie.application.workflow;

import mg.galaxam.sirh.paie.api.dto.PayrollPhaseStatus;
import mg.galaxam.sirh.paie.api.dto.PayrollWorkflowStatuses;

/** Traduction unique des états persistés et des bulletins en statuts de Pointage et de Paie. */
public final class PayrollWorkflowStatusResolver {
    private PayrollWorkflowStatusResolver() {
    }

    public static PayrollWorkflowStatuses resolve(String state, int totalBulletins,
            int calculatedBulletins, int validatedBulletins, boolean recalculationRequired,
            boolean populationComplete) {
        PayrollPhaseStatus pointage = switch (state) {
            case "BROUILLON" -> status("A_OUVRIR", "À ouvrir", "neutral");
            case "EN_COURS" -> status("EN_SAISIE", "Saisie en cours", "info");
            case "A_VALIDER", "TRANSMIS_DAF" -> status("A_VALIDER", "À valider", "warning");
            case "VALIDEE", "PAIE_VALIDEE", "CLOTUREE" -> status("FIGE", "Pointage figé", "success");
            default -> status("INCONNU", "Statut à vérifier", "warning");
        };

        PayrollPhaseStatus paie = switch (state) {
            case "BROUILLON", "EN_COURS", "A_VALIDER", "TRANSMIS_DAF" ->
                    status("EN_ATTENTE_POINTAGE", "En attente du pointage", "neutral");
            case "PAIE_VALIDEE" -> recalculationRequired
                    ? status("A_RECALCULER", "À recalculer", "warning")
                    : status("VALIDEE", "Paie validée", "success");
            case "CLOTUREE" -> status("CLOTUREE", "Paie clôturée", "success");
            case "VALIDEE" -> {
                if (recalculationRequired) yield status("A_RECALCULER", "À recalculer", "warning");
                if (totalBulletins == 0) yield status("A_PREPARER", "À préparer", "warning");
                if (!populationComplete) yield status("A_COMPLETER", "Calcul incomplet", "warning");
                if (validatedBulletins == totalBulletins) yield status("A_FINALISER", "Validation à finaliser", "warning");
                if (calculatedBulletins > 0 || validatedBulletins > 0) yield status("A_CONTROLER", "À contrôler", "info");
                yield status("A_CALCULER", "À calculer", "warning");
            }
            default -> status("INCONNU", "Statut à vérifier", "warning");
        };
        return new PayrollWorkflowStatuses(pointage, paie);
    }

    private static PayrollPhaseStatus status(String code, String label, String tone) {
        return new PayrollPhaseStatus(code, label, tone);
    }
}
