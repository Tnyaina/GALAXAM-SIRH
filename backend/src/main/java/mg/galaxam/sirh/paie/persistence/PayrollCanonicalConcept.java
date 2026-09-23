package mg.galaxam.sirh.paie.persistence;

import java.util.Locale;
import java.util.Map;

/** Vocabulaire métier unique du bulletin; les libellés Excel restent des alias d'import. */
public enum PayrollCanonicalConcept {
    BASE_SALARY("Salaire de base"),
    ABSENCE_DEDUCTION("Retenue pour absence non rémunérée"),
    LEAVE_BASE_DEDUCTION("Déduction de salaire au titre du congé payé"),
    PAID_LEAVE_INDEMNITY("Indemnité de congé payé"),
    OVERTIME_PAY("Rémunération des heures supplémentaires"),
    OTHER_TAXABLE_EARNINGS("Autres gains imposables"),
    GROSS_SALARY("Salaire brut"),
    EMPLOYEE_SOCIAL_CONTRIBUTIONS("Cotisations sociales salariales"),
    NET_INCOME_BEFORE_INCOME_TAX("Revenu net avant impôt"),
    TAXABLE_NET_INCOME("Revenu net imposable"),
    IRSA_TAX_BASE("Base imposable IRSA"),
    IRSA_CALCULATED("IRSA calculé"),
    IRSA_FAMILY_REDUCTION("Réduction IRSA pour charges de famille"),
    IRSA_DUE("IRSA dû et retenu à la source"),
    NET_ITEMS("Éléments hors brut"),
    PERSONAL_CALL_DEDUCTION("Retenue de téléphonie personnelle"),
    NET_PAYABLE_BEFORE_ROUNDING("Net à payer avant arrondi"),
    NET_PAYABLE("Net à payer");

    private static final Map<String, PayrollCanonicalConcept> LEGACY_ALIASES = Map.ofEntries(
            Map.entry("BASE", BASE_SALARY),
            Map.entry("NBABS", ABSENCE_DEDUCTION),
            Map.entry("ABSENCES", ABSENCE_DEDUCTION),
            Map.entry("NBCONGE", LEAVE_BASE_DEDUCTION),
            Map.entry("TCONGE", PAID_LEAVE_INDEMNITY),
            Map.entry("CONGE", PAID_LEAVE_INDEMNITY),
            Map.entry("HS", OVERTIME_PAY),
            Map.entry("AUTIMP", OTHER_TAXABLE_EARNINGS),
            Map.entry("BRUTE", GROSS_SALARY),
            Map.entry("CNAPS", EMPLOYEE_SOCIAL_CONTRIBUTIONS),
            Map.entry("OSTIE", EMPLOYEE_SOCIAL_CONTRIBUTIONS),
            Map.entry("OMSI", EMPLOYEE_SOCIAL_CONTRIBUTIONS),
            Map.entry("NETI", NET_INCOME_BEFORE_INCOME_TAX),
            Map.entry("IMPOSABLE", IRSA_TAX_BASE),
            Map.entry("IRSA", IRSA_CALCULATED),
            Map.entry("IRSA ARRONDI", IRSA_DUE),
            Map.entry("RED ENFT", IRSA_FAMILY_REDUCTION),
            Map.entry("NET À PAYER AVANT CALL", NET_PAYABLE_BEFORE_ROUNDING),
            Map.entry("CALL PERSO", PERSONAL_CALL_DEDUCTION),
            Map.entry("NET À PAYER", NET_PAYABLE),
            Map.entry("ARRONDI NET À PAYER", NET_PAYABLE)
    );

    private final String bulletinLabel;

    PayrollCanonicalConcept(String bulletinLabel) {
        this.bulletinLabel = bulletinLabel;
    }

    public String bulletinLabel() {
        return bulletinLabel;
    }

    public static PayrollCanonicalConcept fromLegacyExcelLabel(String label) {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("LEGACY_PAYROLL_LABEL_REQUIRED");
        }
        PayrollCanonicalConcept concept = LEGACY_ALIASES.get(label.trim().toUpperCase(Locale.ROOT));
        if (concept == null) {
            throw new IllegalArgumentException("UNKNOWN_LEGACY_PAYROLL_LABEL: " + label);
        }
        return concept;
    }
}
