package mg.galaxam.sirh.certification.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Adaptateur spécialisé pour le classeur de pointage brut :
 * `model/ohatra presence svt clocker.xlsx` (Onglet `pointage extrait machine`).
 */
public class PresenceHistoricalAdapter {

    public static List<HistoricalAttendanceCase> loadAttendanceCases(ExcelWorkbookReader reader) {
        List<HistoricalAttendanceCase> list = new ArrayList<>();
        Sheet sheet = reader.getSheet("pointage extrait machine");

        // Les 3 salariés sont disposés en colonnes :
        // RAKOTO : col A (1) à S (19) - Matr 4
        // RAKETAKA : col T (20) à AL (38) - Matr 2
        // RASOA : col AM (39) à BA (53) - Matr 3
        list.add(extractEmployeeAttendance(reader, sheet, 0, "4", "RAKOTO", "TMM"));
        list.add(extractEmployeeAttendance(reader, sheet, 19, "2", "RAKETAKA", "TNR"));
        list.add(extractEmployeeAttendance(reader, sheet, 38, "3", "RASOA", "TNR"));

        return list;
    }

    private static HistoricalAttendanceCase extractEmployeeAttendance(
            ExcelWorkbookReader reader, Sheet sheet, int colOffset,
            String matricule, String name, String siteCode) {

        LocalDate pStart = LocalDate.of(2025, 11, 26);
        LocalDate pEnd = LocalDate.of(2025, 12, 25);
        HistoricalAttendanceCase c = new HistoricalAttendanceCase(
                "ATT-" + matricule, matricule, name, siteCode, pStart, pEnd);

        // Ligne 8 : totaux affichés dans l'Excel
        Row r8 = sheet.getRow(7);
        if (r8 != null) {
            Cell cAbs = r8.getCell(colOffset);
            Cell cCong = r8.getCell(colOffset + 1);
            Cell cWork = r8.getCell(colOffset + 4);
            Cell cHs = r8.getCell(colOffset + 5);
            Cell cRet = r8.getCell(colOffset + 8);

            BigDecimal vWork = reader.getNumericValue(cWork);
            if (vWork != null) c.setExcelWorkedDays(vWork.doubleValue());

            BigDecimal vAbs = reader.getNumericValue(cAbs);
            if (vAbs != null) c.setExcelAbsenceDays(vAbs.doubleValue());

            BigDecimal vHs = reader.getNumericValue(cHs);
            if (vHs != null) c.setExcelOvertimeHours(vHs.doubleValue());

            BigDecimal vRet = reader.getNumericValue(cRet);
            if (vRet != null) c.setExcelLateMinutes(vRet.doubleValue());
        }

        // Lignes 13 à 42 : jours calendaires (30 jours du 26/11 au 25/12)
        for (int r = 12; r < 42; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            Cell cDay = row.getCell(colOffset);
            String dayLabel = reader.getStringValue(cDay);

            // Calcul de la date exacte
            int dayOffset = r - 12;
            LocalDate curDate = pStart.plusDays(dayOffset);

            Cell cInM = row.getCell(colOffset + 1);
            Cell cOutM = row.getCell(colOffset + 3);
            Cell cInA = row.getCell(colOffset + 6);
            Cell cOutA = row.getCell(colOffset + 8);

            String inMStr = reader.getStringValue(cInM);
            String inAStr = reader.getStringValue(cInA);

            boolean isConge = "CONGES".equalsIgnoreCase(inMStr) || "CONGES".equalsIgnoreCase(inAStr);
            boolean isFerie = curDate.getMonthValue() == 12 && curDate.getDayOfMonth() == 25; // Noël

            LocalTime inM = isConge ? null : reader.getLocalTimeValue(cInM);
            LocalTime outM = isConge ? null : reader.getLocalTimeValue(cOutM);
            LocalTime inA = isConge ? null : reader.getLocalTimeValue(cInA);
            LocalTime outA = isConge ? null : reader.getLocalTimeValue(cOutA);

            HistoricalAttendanceCase.DailyPunchItem item = new HistoricalAttendanceCase.DailyPunchItem(
                    curDate, inM, outM, inA, outA, isConge, isFerie, dayLabel);
            c.addPunch(item);

            // Enregistrement de la provenance
            if (cInM != null) {
                c.addProvenance(HistoricalExcelRecord.of(reader.getFilePath(), sheet.getSheetName(),
                        r + 1, colOffset + 2, reader.getCellAddress(cInM), matricule, name,
                        "2025-12", ExcelCellClassification.RAW_INPUT, reader.getStringValue(cInM),
                        reader.getRawFormula(cInM), reader.getCachedValueAsString(cInM)));
            }
        }

        return c;
    }
}
