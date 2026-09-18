package mg.galaxam.sirh.unit.characterization;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.PrintWriter;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.Test;

public class AllGalaxamExcelDumpTest {

    @Test
    void dumpAllThreeFiles() throws Exception {
        dumpFile("../model/ohatra gita fichiers-23jan26.xlsx", "target/ohatra_gita_dump.txt");
        dumpFile("../model/OHATRA KAJY DROITS CONGES-given on sept26.xls", "target/kajy_dump.txt");
        dumpFile("../model/ohatra presence svt clocker.xlsx", "target/ohatra_presence_dump.txt");
    }

    private void dumpFile(String relPath, String outTxt) throws Exception {
        File file = new File(relPath);
        if (!file.exists()) {
            file = new File(relPath.replace("../", ""));
        }
        System.out.println("=== DUMPING: " + file.getAbsolutePath() + " -> " + outTxt + " (exists: " + file.exists() + ") ===");
        if (!file.exists()) {
            System.out.println("File not found: " + relPath);
            return;
        }

        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = WorkbookFactory.create(fis);
             PrintWriter pw = new PrintWriter(new FileWriter(outTxt))) {

            int numSheets = workbook.getNumberOfSheets();
            pw.println("FILE: " + file.getName());
            pw.println("NUMBER OF SHEETS: " + numSheets);

            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            for (int s = 0; s < numSheets; s++) {
                Sheet sheet = workbook.getSheetAt(s);
                pw.println("\n============================================================");
                pw.println("SHEET " + s + ": '" + sheet.getSheetName() + "' (Rows: " + (sheet.getLastRowNum() + 1) + ")");
                pw.println("============================================================");

                for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;

                    StringBuilder sb = new StringBuilder();
                    sb.append(String.format("R%03d: ", r + 1));
                    boolean hasContent = false;

                    for (int c = 0; c < row.getLastCellNum(); c++) {
                        Cell cell = row.getCell(c);
                        if (cell == null) continue;

                        String colLetter = org.apache.poi.ss.util.CellReference.convertNumToColString(c);
                        String coord = colLetter + (r + 1);

                        switch (cell.getCellType()) {
                            case FORMULA:
                                hasContent = true;
                                String formula = cell.getCellFormula();
                                String val = "";
                                try {
                                    val = formatter.formatCellValue(cell, evaluator);
                                } catch (Exception e) {
                                    val = "EVAL_ERR(" + e.getMessage() + ")";
                                }
                                sb.append(String.format("[%s FORMULA: =%s | VAL: %s] ", coord, formula, val));
                                break;
                            case NUMERIC:
                                hasContent = true;
                                if (DateUtil.isCellDateFormatted(cell)) {
                                    sb.append(String.format("[%s DATE: %s] ", coord, cell.getDateCellValue()));
                                } else {
                                    sb.append(String.format("[%s NUM: %s] ", coord, cell.getNumericCellValue()));
                                }
                                break;
                            case STRING:
                                String str = cell.getStringCellValue().trim();
                                if (!str.isEmpty()) {
                                    hasContent = true;
                                    sb.append(String.format("[%s STR: \"%s\"] ", coord, str));
                                }
                                break;
                            case BOOLEAN:
                                hasContent = true;
                                sb.append(String.format("[%s BOOL: %s] ", coord, cell.getBooleanCellValue()));
                                break;
                            case ERROR:
                                hasContent = true;
                                sb.append(String.format("[%s ERR: %s] ", coord, cell.getErrorCellValue()));
                                break;
                            default:
                                break;
                        }
                    }
                    if (hasContent) {
                        pw.println(sb.toString());
                    }
                }
            }
        }
    }
}
