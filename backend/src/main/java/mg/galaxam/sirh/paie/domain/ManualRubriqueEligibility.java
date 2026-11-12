package mg.galaxam.sirh.paie.domain;

import java.util.Set;

/** Détermine si une rubrique peut utiliser les règles manuelles plutôt qu'un calculateur dédié. */
public final class ManualRubriqueEligibility {
    private static final Set<String> DEDICATED_CODES = Set.of(
            "RETENUE_CONGE", "INDEMNITE_CONGE", "DEDUCTION_CONGE_BASE", "INDEMNITE_CONGE_PAYE",
            "INDEMNITE_COMPENSATRICE_CONGE", "INDEMNITE_PREAVIS", "INDEMNITE_LICENCIEMENT",
            "PRIME_PERFORMANCE", "PRIME_ASSIDUITE", "PRIME_ANCIENNETE",
            "RETENUE_ACOMPTE_15", "RETENUE_AVANCE_SPECIALE", "RETENUE_CALL_PERSO",
            "REMBOURSEMENT_NOTE_FRAIS", "SALAIRE_BASE", "RETENUE_ABSENCE", "AJUSTEMENT_ARRONDI");
    private static final Set<String> MANUAL_CATEGORIES = Set.of(
            "PRIME", "INDEMNITE_IMPOSABLE", "INDEMNITE_NON_IMPOSABLE", "RETENUE_DIVERSE");

    private ManualRubriqueEligibility() {}

    public static boolean isConfigurable(String code, String categorie, boolean fixed) {
        return !fixed && !DEDICATED_CODES.contains(code == null ? "" : code)
                && MANUAL_CATEGORIES.contains(categorie == null ? "" : categorie);
    }
}
