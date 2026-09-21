package mg.galaxam.sirh.certification.excel;

/**
 * Classification formelle de la nature de chaque cellule Excel auditée.
 * Empêche d'utiliser une cellule calculée ou formule comme input de l'oracle.
 */
public enum ExcelCellClassification {
    RAW_INPUT,
    REFERENCE_INPUT,
    HISTORICAL_CALCULATED,
    HISTORICAL_FORMULA,
    MANUAL_ADJUSTMENT,
    DERIVED_LEGACY,
    UNKNOWN
}
