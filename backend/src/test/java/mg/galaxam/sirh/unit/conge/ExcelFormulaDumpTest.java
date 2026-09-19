package mg.galaxam.sirh.unit.conge;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.CellType;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;

public class ExcelFormulaDumpTest {

    @Test
    public void dumpAllFormulas() throws Exception {
        File file = new File("../model/OHATRA KAJY DROITS CONGES-given on sept26.xls");
        if (!file.exists()) {
            file = new File("model/OHATRA KAJY DROITS CONGES-given on sept26.xls");
        }
        System.out.println("Reading file: " + file.getAbsolutePath());
        try (FileInputStream fis = new FileInputStream(file);
             HSSFWorkbook wb = new HSSFWorkbook(fis)) {
            HSSFSheet sheet = wb.getSheetAt(0);
            System.out.println("Sheet name: " + sheet.getSheetName());
            System.out.println("Rows: " + sheet.getPhysicalNumberOfRows());

            for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                HSSFRow row = sheet.getRow(r);
                if (row == null) continue;
                System.out.println("--- ROW " + (r + 1) + " ---");
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    HSSFCell cell = row.getCell(c);
                    if (cell == null) continue;
                    String colLetter = getColLetter(c);
                    String coord = colLetter + (r + 1);
                    if (cell.getCellType() == CellType.FORMULA) {
                        System.out.printf("  [%s] FORMULA: =%s  | EVAL: %s%n", coord, cell.getCellFormula(), formatCellValue(cell));
                    } else if (cell.getCellType() == CellType.NUMERIC) {
                        System.out.printf("  [%s] NUMERIC: %s%n", coord, cell.getNumericCellValue());
                    } else if (cell.getCellType() == CellType.STRING) {
                        System.out.printf("  [%s] STRING: \"%s\"%n", coord, cell.getStringCellValue());
                    } else if (cell.getCellType() == CellType.BLANK) {
                        // ignore
                    } else {
                        System.out.printf("  [%s] OTHER (%s): %s%n", coord, cell.getCellType(), cell.toString());
                    }
                }
            }
        }
    }

    private static String getColLetter(int col) {
        StringBuilder sb = new StringBuilder();
        int c = col;
        while (c >= 0) {
            sb.insert(0, (char) ('A' + (c % 26)));
            c = (c / 26) - 1;
        }
        return sb.toString();
    }

    private static String formatCellValue(HSSFCell cell) {
        try {
            switch (cell.getCachedFormulaResultType()) {
                case NUMERIC: return String.valueOf(cell.getNumericCellValue());
                case STRING: return "\"" + cell.getStringCellValue() + "\"";
                case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
                case ERROR: return "ERROR " + cell.getErrorCellValue();
                default: return cell.toString();
            }
        } catch (Exception e) {
            return "?";
        }
    }
}
