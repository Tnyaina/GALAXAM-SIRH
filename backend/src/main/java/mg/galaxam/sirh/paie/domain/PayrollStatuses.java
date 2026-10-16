package mg.galaxam.sirh.paie.domain;

import java.util.Locale;
import java.util.Set;

/** Statuts canoniques et alias historiques propres aux entités de paie. */
public final class PayrollStatuses {
    private PayrollStatuses() {}

    private static final Set<String> CANCELLED_ADJUSTMENT_ALIASES = Set.of("ANNULE", "ANNULEE", "CANCELLED");
    private static final Set<String> FINAL_PERIOD_STATUSES = Set.of("PAIE_VALIDEE", "CLOTUREE");
    private static final Set<String> FINAL_BULLETIN_STATUSES = Set.of("VALIDE", "CLOTURE");

    public static String canonicalAdjustment(String raw) {
        String value = normalize(raw);
        return CANCELLED_ADJUSTMENT_ALIASES.contains(value) ? "ANNULE" : value;
    }

    public static boolean isCancelledAdjustment(String raw) {
        return "ANNULE".equals(canonicalAdjustment(raw));
    }

    public static boolean isFinalPeriod(String raw) { return FINAL_PERIOD_STATUSES.contains(normalize(raw)); }
    public static boolean isFinalBulletin(String raw) { return FINAL_BULLETIN_STATUSES.contains(normalize(raw)); }

    /** SQL predicate includes legacy spellings while new writes keep the canonical ANNULE value. */
    public static String cancelledAdjustmentSql(String statusExpression) {
        return "UPPER(COALESCE(" + statusExpression + ",'')) IN ('ANNULE','ANNULEE','CANCELLED')";
    }

    private static String normalize(String raw) { return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT); }
}
