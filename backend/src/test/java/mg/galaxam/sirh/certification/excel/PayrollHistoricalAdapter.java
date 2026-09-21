package mg.galaxam.sirh.certification.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Adaptateur spécialisé pour les données de paie historiques réelles :
 * - `model/ohatra presence svt clocker.xlsx` (Feuille `EJOURNAL`)
 * - `model/ohatra gita fichiers-23jan26.xlsx` (Feuille `OUTPUT Colonnes E F G K L M `)
 */
public class PayrollHistoricalAdapter {

    public static List<HistoricalPayrollCase> loadPayrollCases(ExcelWorkbookReader reader) {
        List<HistoricalPayrollCase> cases = new ArrayList<>();
        Sheet sheet = reader.getSheet("EJOURNAL");

        // RAKETAKA (Row 3, index 2)
        // RASOA (Row 4, index 3)
        // RAKOTO (Row 5, index 4)
        cases.add(extractCase(reader, sheet, 2, "2", "RAKETAKA", "TNR", "OSTIE"));
        cases.add(extractCase(reader, sheet, 3, "3", "RASOA", "TNR", "OSTIE"));
        cases.add(extractCase(reader, sheet, 4, "4", "RAKOTO", "TMM", "OMSI"));

        return cases;
    }

    private static HistoricalPayrollCase extractCase(
            ExcelWorkbookReader reader, Sheet sheet, int rowIndex,
            String matricule, String name, String siteCode, String healthRegime) {

        Row row = sheet.getRow(rowIndex);
        LocalDate payrollDate = LocalDate.of(2025, 12, 31);
        HistoricalPayrollCase pCase = new HistoricalPayrollCase(
                "PAY-" + matricule, reader.getFilePath(), sheet.getSheetName(), rowIndex + 1,
                matricule, name, "2025-12", payrollDate, siteCode);
        pCase.setHealthRegime(healthRegime);

        // Colonnes EJOURNAL :
        // B (1) : MATR
        // C (2) : NOM
        // E (4) : BASE
        // F (5) : NBABS
        // G (6) : ABSENCES
        // H (7) : NBCONGE
        // I (8) : TCONGE
        // J (9) : CONGE
        // K (10): DEPLACEMENT
        // L (11): HS
        // M (12): AUTIMP
        // N (13): BRUTE
        // O (14): CNAPS
        // P (15): OSTIE
        // Q (16): OMSI
        // R (17): NETI
        // S (18): Imposable
        // T (19): IRSA
        // U (20): IRSA Arrondi
        // V (21): RED ENFT
        // W (22): AVANCE
        // X (23): avce spec
        // Y (24): NET
        // Z (25): repas
        // AB (27): CALL PERSO
        // AC (28): NET À PAYER
        // AD (29): ARRONDI NET

        BigDecimal base = reader.getNumericValue(row.getCell(4));
        BigDecimal nbAbs = reader.getNumericValue(row.getCell(5));
        BigDecimal nbConge = reader.getNumericValue(row.getCell(7));
        BigDecimal tConge = reader.getNumericValue(row.getCell(8));
        BigDecimal deplacement = reader.getNumericValue(row.getCell(10));
        BigDecimal hsAmount = reader.getNumericValue(row.getCell(11));
        BigDecimal autimp = reader.getNumericValue(row.getCell(12));
        BigDecimal repas = reader.getNumericValue(row.getCell(25));
        BigDecimal avance = reader.getNumericValue(row.getCell(22));
        BigDecimal avceSpec = reader.getNumericValue(row.getCell(23));
        BigDecimal callPerso = reader.getNumericValue(row.getCell(27));

        pCase.setBaseSalary(base != null ? base.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        pCase.setLeaveDaysTaken(nbConge != null ? nbConge.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        pCase.setTcongeRate(tConge != null ? tConge.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        pCase.setHsAmountDirect(hsAmount != null ? hsAmount.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        pCase.setAutimpAmount(autimp != null ? autimp.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        pCase.setDeplacementAmount(deplacement != null ? deplacement.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        pCase.setRepasAmount(repas != null ? repas.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);

        BigDecimal totalAvances = BigDecimal.ZERO;
        if (avance != null) totalAvances = totalAvances.add(avance);
        if (avceSpec != null) totalAvances = totalAvances.add(avceSpec);
        pCase.setAvanceAmount(totalAvances.setScale(2, RoundingMode.HALF_UP));
        pCase.setCallPersoAmount(callPerso != null ? callPerso.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);

        // Traçabilité de provenance pour les entrées clés
        Cell cellBase = row.getCell(4);
        if (cellBase != null) {
            pCase.addProvenance(HistoricalExcelRecord.of(reader.getFilePath(), sheet.getSheetName(),
                    rowIndex + 1, 5, reader.getCellAddress(cellBase), matricule, name, "2025-12",
                    ExcelCellClassification.REFERENCE_INPUT, reader.getStringValue(cellBase),
                    reader.getRawFormula(cellBase), reader.getCachedValueAsString(cellBase)));
        }

        return pCase;
    }
}
