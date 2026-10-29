package mg.galaxam.sirh.pointage.domain.calculation;

import java.math.BigDecimal;
import java.util.Locale;
import mg.galaxam.sirh.pointage.domain.engine.CalendrierOuvrableEngine;

/** Règles communes d'attribution des indemnités journalières dans la grille et le contrôle. */
public final class PointageAllowancePolicy {
    private PointageAllowancePolicy() {}

    /** Le défaut s'applique uniquement à une journée éligible, configurée et sans exclusion individuelle. */
    public static boolean defaultGranted(boolean eligibleDay, boolean configuredByDefault, boolean excluded) {
        return eligibleDay && configuredByDefault && !excluded;
    }

    /** Une décision enregistrée, y compris un refus explicite, prime toujours sur le défaut. */
    public static boolean resolve(Boolean recordedDecision, boolean defaultDecision) {
        return recordedDecision != null ? recordedDecision : defaultDecision;
    }

    /** Une indemnité explicitement accordée reste due pendant un congé payé. */
    public static boolean eligibleForAllowance(boolean workedOrPresent, boolean paidLeave,
            Boolean recordedDecision) {
        return workedOrPresent || (paidLeave && Boolean.TRUE.equals(recordedDecision));
    }

    /** Même tarif journalier pour la grille, les propositions datées et la persistance. */
    public static BigDecimal dailyAmount(String periodicity, BigDecimal reference, int workingDays,
            CalendrierOuvrableEngine calendar) {
        if (reference == null || reference.signum() < 0) {
            throw new IllegalArgumentException("Montant d'indemnité invalide.");
        }
        return switch (periodicity.toUpperCase(Locale.ROOT)) {
            case "JOURNALIER", "FORFAIT" -> reference;
            case "MENSUEL", "MENSUEL_PRORATISE" -> {
                if (workingDays <= 0) throw new IllegalArgumentException("Jours ouvrables manquants.");
                yield calendar.calculerProrataJournalier(reference, workingDays);
            }
            default -> throw new IllegalArgumentException("Périodicité non applicable : " + periodicity);
        };
    }
}
