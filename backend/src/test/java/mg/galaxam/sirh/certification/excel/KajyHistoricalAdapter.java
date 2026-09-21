package mg.galaxam.sirh.certification.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Adaptateur spécialisé pour le classeur d'acquisition et valorisation de congés KAJY :
 * `model/OHATRA KAJY DROITS CONGES-given on sept26.xls`.
 */
public class KajyHistoricalAdapter {

    public static HistoricalLeaveCase loadHavanaCase(ExcelWorkbookReader reader) {
        Sheet sheet = reader.getSheet("Feuille1");
        HistoricalLeaveCase leaveCase = new HistoricalLeaveCase("LEAVE-61", "61", "RASOANARIVO Havana Hideric");

        BigDecimal runningRights = BigDecimal.ZERO;
        BigDecimal runningValorisation = BigDecimal.ZERO;

        // Lignes 3 à 14 dans Excel (index 2 à 13 dans POI) : Mois 1 à 12
        for (int r = 2; r <= 13; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            int mois = (int) reader.getNumericValue(row.getCell(0)).doubleValue();
            BigDecimal presenceDays = reader.getNumericValue(row.getCell(3));
            BigDecimal baseSalary = reader.getNumericValue(row.getCell(4));
            BigDecimal absenceDays = reader.getNumericValue(row.getCell(5));
            BigDecimal leaveDaysTaken = reader.getNumericValue(row.getCell(7));
            BigDecimal tcongeApplied = reader.getNumericValue(row.getCell(8));
            BigDecimal leaveIndemnityExcel = reader.getNumericValue(row.getCell(9));
            BigDecimal hsAmount = reader.getNumericValue(row.getCell(11));
            BigDecimal autimpAmount = reader.getNumericValue(row.getCell(12));
            BigDecimal grossSalaryExcel = reader.getNumericValue(row.getCell(13));

            BigDecimal droitAcquisExcel = reader.getNumericValue(row.getCell(18));
            BigDecimal valorisationExcel = reader.getNumericValue(row.getCell(19));
            BigDecimal cumulDroitsExcel = reader.getNumericValue(row.getCell(20));
            BigDecimal cumulValorisationExcel = reader.getNumericValue(row.getCell(21));

            // Calcul canonique Oracle :
            // Règle 1 : nbre droit = 2.5 * formule / 30.0 (arrondi exact ou précision 4 décimales)
            BigDecimal canonPresence = presenceDays != null ? presenceDays : new BigDecimal("30.0");
            BigDecimal canonDroitAcquis = new BigDecimal("2.5").multiply(canonPresence)
                    .divide(new BigDecimal("30.0"), 6, RoundingMode.HALF_UP);

            // Base de valorisation : base totale = base_colonne_K + Pdir/HS
            // Dans le fichier : colonne 17 'base totale'
            BigDecimal baseTotale = reader.getNumericValue(row.getCell(17));
            if (baseTotale == null) {
                baseTotale = baseSalary != null ? baseSalary : BigDecimal.ZERO;
            }

            // Règle 2 : valorisation = base_totale * nbre_droit / 30.0
            BigDecimal canonValorisation = baseTotale.multiply(canonDroitAcquis)
                    .divide(new BigDecimal("30.0"), 6, RoundingMode.HALF_UP);

            runningRights = runningRights.add(canonDroitAcquis);
            runningValorisation = runningValorisation.add(canonValorisation);

            HistoricalLeaveCase.MonthlyLeaveEntry entry = new HistoricalLeaveCase.MonthlyLeaveEntry(
                    mois,
                    baseSalary,
                    presenceDays,
                    absenceDays != null ? absenceDays : BigDecimal.ZERO,
                    leaveDaysTaken != null ? leaveDaysTaken : BigDecimal.ZERO,
                    tcongeApplied != null ? tcongeApplied : BigDecimal.ZERO,
                    leaveIndemnityExcel != null ? leaveIndemnityExcel : BigDecimal.ZERO,
                    hsAmount != null ? hsAmount : BigDecimal.ZERO,
                    autimpAmount != null ? autimpAmount : BigDecimal.ZERO,
                    grossSalaryExcel != null ? grossSalaryExcel : BigDecimal.ZERO,
                    droitAcquisExcel,
                    valorisationExcel,
                    cumulDroitsExcel,
                    cumulValorisationExcel,
                    canonDroitAcquis.setScale(4, RoundingMode.HALF_UP),
                    canonValorisation.setScale(2, RoundingMode.HALF_UP),
                    runningRights.setScale(4, RoundingMode.HALF_UP),
                    runningValorisation.setScale(2, RoundingMode.HALF_UP)
            );
            leaveCase.addMonth(entry);

            // Traçabilité provenance
            Cell cellBase = row.getCell(4);
            if (cellBase != null) {
                leaveCase.addProvenance(HistoricalExcelRecord.of(reader.getFilePath(), sheet.getSheetName(),
                        r + 1, 5, reader.getCellAddress(cellBase), "61", "Havana Hideric",
                        "Mois-" + mois, ExcelCellClassification.REFERENCE_INPUT, reader.getStringValue(cellBase),
                        reader.getRawFormula(cellBase), reader.getCachedValueAsString(cellBase)));
            }
        }

        // TCONGE terminal affiché en W14 (colonne 22, ligne 14 dans Excel)
        Row r14 = sheet.getRow(13);
        if (r14 != null) {
            Cell cellTconge = r14.getCell(22);
            if (cellTconge != null) {
                leaveCase.setHistoricalFinalTconge(reader.getNumericValue(cellTconge).setScale(2, RoundingMode.HALF_UP));
            }
        }

        if (runningRights.compareTo(BigDecimal.ZERO) > 0) {
            leaveCase.setCanonicalFinalTconge(runningValorisation.divide(runningRights, 2, RoundingMode.HALF_UP));
        }

        return leaveCase;
    }
}
