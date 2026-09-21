package mg.galaxam.sirh.certification.excel;

/**
 * Modèle de provenance immuable traçant l'origine exacte, la classification et le contenu
 * de chaque cellule extraite des classeurs historiques GALAXAM.
 */
public record HistoricalExcelRecord(
        String sourceFile,
        String sheet,
        int row,
        int column,
        String cellAddress,
        String matricule,
        String employeeName,
        String period,
        ExcelCellClassification classification,
        String rawValue,
        String formula,
        String cachedValue,
        String normalizedValue,
        String unit,
        String comment
) {
    public static HistoricalExcelRecord of(String sourceFile, String sheet, int row, int column, String cellAddress,
                                          String matricule, String employeeName, String period,
                                          ExcelCellClassification classification, String rawValue,
                                          String formula, String cachedValue) {
        return new HistoricalExcelRecord(sourceFile, sheet, row, column, cellAddress,
                matricule, employeeName, period, classification, rawValue, formula, cachedValue, null, null, null);
    }

    public HistoricalExcelRecord withNormalized(String normalizedValue, String unit) {
        return new HistoricalExcelRecord(sourceFile, sheet, row, column, cellAddress,
                matricule, employeeName, period, classification, rawValue, formula, cachedValue, normalizedValue, unit, comment);
    }
}
