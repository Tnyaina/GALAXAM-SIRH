package mg.galaxam.sirh.unit.characterization;

import java.io.File;
import java.io.FileInputStream;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.Test;

public class KajyExcelDumpTest {

    @Test
    void dumpKajyExcel() throws Exception {
        File file = new File("../model/OHATRA KAJY DROITS CONGES-given on sept26.xls");
        if (!file.exists()) {
            file = new File("model/OHATRA KAJY DROITS CONGES-given on sept26.xls");
        }
        System.out.println("=== DUMPING EXCEL FILE: " + file.getAbsolutePath() + " (exists: " + file.exists() + ") ===");

        java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter("target/kajy_dump.txt"));
        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = WorkbookFactory.create(fis)) {

            int numSheets = workbook.getNumberOfSheets();
            pw.println("Number of sheets: " + numSheets);

            for (int s = 0; s < numSheets; s++) {
                Sheet sheet = workbook.getSheetAt(s);
                pw.println("\n------------------------------------------------------------");
                pw.println("SHEET " + s + ": " + sheet.getSheetName() + " (Rows: " + (sheet.getLastRowNum() + 1) + ")");
                pw.println("------------------------------------------------------------");

                DataFormatter formatter = new DataFormatter();
                FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

                for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;

                    StringBuilder sb = new StringBuilder();
                    sb.append(String.format("Row %2d: ", r + 1));

                    for (int c = 0; c < row.getLastCellNum(); c++) {
                        Cell cell = row.getCell(c);
                        if (cell == null) {
                            sb.append("[EMPTY] | ");
                            continue;
                        }

                        String colLetter = org.apache.poi.ss.util.CellReference.convertNumToColString(c);
                        String cellCoord = colLetter + (r + 1);

                        switch (cell.getCellType()) {
                            case FORMULA:
                                String formula = cell.getCellFormula();
                                String val = formatter.formatCellValue(cell, evaluator);
                                sb.append(String.format("%s={FORMULA: =%s -> VAL: %s} | ", cellCoord, formula, val));
                                break;
                            case NUMERIC:
                                if (DateUtil.isCellDateFormatted(cell)) {
                                    sb.append(String.format("%s={DATE: %s} | ", cellCoord, cell.getDateCellValue()));
                                } else {
                                    sb.append(String.format("%s={NUM: %s} | ", cellCoord, cell.getNumericCellValue()));
                                }
                                break;
                            case STRING:
                                sb.append(String.format("%s={STR: '%s'} | ", cellCoord, cell.getStringCellValue()));
                                break;
                            case BOOLEAN:
                                sb.append(String.format("%s={BOOL: %s} | ", cellCoord, cell.getBooleanCellValue()));
                                break;
                            case BLANK:
                                sb.append(String.format("%s={BLANK} | ", cellCoord));
                                break;
                            case ERROR:
                                sb.append(String.format("%s={ERR: %s} | ", cellCoord, cell.getErrorCellValue()));
                                break;
                            default:
                                sb.append(String.format("%s={UNKNOWN} | ", cellCoord));
                        }
                    }
                    pw.println(sb.toString());
                }
            }
        }
        pw.close();
        System.out.println("=== DUMP WRITTEN TO target/kajy_dump.txt ===");
    }
}
