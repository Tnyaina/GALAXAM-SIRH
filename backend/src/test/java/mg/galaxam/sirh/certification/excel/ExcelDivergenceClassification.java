package mg.galaxam.sirh.certification.excel;

/**
 * Typologie standardisée pour classifier chaque écart entre les anciens classeurs Excel
 * et le calcul canonique de l'Oracle Indépendant / Runtime SIRH.
 */
public enum ExcelDivergenceClassification {
    EXCEL_CONFIRMED,
    EXCEL_FORMULA_ERROR,
    EXCEL_LEGACY_RULE,
    EXCEL_ROUNDING_DIFFERENCE,
    EXCEL_UNIT_MISMATCH,
    EXCEL_OVERLOADED_COLUMN,
    EXCEL_MANUAL_ADJUSTMENT,
    SOURCE_DATA_MISSING,
    BUSINESS_RULE_UNCERTAIN,
    RUNTIME_BUG
}
