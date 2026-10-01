package mg.galaxam.sirh.paie.domain.engine;

import java.time.LocalDate;

/** Une règle couvre une période complète ; aucun prorata implicite n'est appliqué. */
public final class ManualRuleApplicability {
    private ManualRuleApplicability() {}
    public static boolean covers(LocalDate start, LocalDate end, LocalDate periodStart, LocalDate periodEnd) {
        return (start == null || !start.isAfter(periodStart)) && (end == null || !end.isBefore(periodEnd));
    }
    public static boolean applies(String condition, boolean irsa, boolean cnaps, boolean health) {
        return switch (condition) {
            case "TOUS" -> true;
            case "ASSUJETTI_IRSA" -> irsa;
            case "ASSUJETTI_CNAPS" -> cnaps;
            case "ASSUJETTI_SANTE" -> health;
            default -> throw new IllegalArgumentException("Condition d’application inconnue : " + condition);
        };
    }
}
