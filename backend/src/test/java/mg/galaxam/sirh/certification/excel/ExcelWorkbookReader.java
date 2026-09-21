package mg.galaxam.sirh.certification.excel;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * Lecteur universel Apache POI pour les classeurs historiques GALAXAM (.xlsx et .xls).
 * Gère de manière homogène les types de cellules, formules, valeurs en cache et dates.
 */
public class ExcelWorkbookReader implements AutoCloseable {

    private final Workbook workbook;
    private final FormulaEvaluator evaluator;
    private final String filePath;

    public ExcelWorkbookReader(File file) throws IOException {
        this.filePath = file.getPath();
        try (FileInputStream fis = new FileInputStream(file)) {
            if (file.getName().endsWith(".xlsx")) {
                this.workbook = new XSSFWorkbook(fis);
            } else if (file.getName().endsWith(".xls")) {
                this.workbook = new HSSFWorkbook(fis);
            } else {
                throw new IllegalArgumentException("Format non supporté: " + file.getName());
            }
        }
        this.evaluator = workbook.getCreationHelper().createFormulaEvaluator();
    }

    public static ExcelWorkbookReader open(String relativeOrAbsolutePath) throws IOException {
        File f = new File(relativeOrAbsolutePath);
        if (!f.exists()) {
            f = new File("../" + relativeOrAbsolutePath);
        }
        if (!f.exists()) {
            throw new IllegalArgumentException("Fichier introuvable : " + relativeOrAbsolutePath);
        }
        return new ExcelWorkbookReader(f);
    }

    public Sheet getSheet(String name) {
        Sheet s = workbook.getSheet(name);
        if (s == null) {
            // Tentative insensitive
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                if (workbook.getSheetName(i).trim().equalsIgnoreCase(name.trim())) {
                    return workbook.getSheetAt(i);
                }
            }
            throw new IllegalArgumentException("Onglet '" + name + "' introuvable dans " + filePath);
        }
        return s;
    }

    public Sheet getSheetAt(int index) {
        return workbook.getSheetAt(index);
    }

    public String getRawFormula(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.FORMULA) {
            return "=" + cell.getCellFormula();
        }
        return null;
    }

    public String getCachedValueAsString(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.FORMULA) {
            return switch (cell.getCachedFormulaResultType()) {
                case NUMERIC -> {
                    if (DateUtil.isCellDateFormatted(cell)) {
                        yield cell.getDateCellValue().toString();
                    }
                    yield BigDecimal.valueOf(cell.getNumericCellValue()).toPlainString();
                }
                case STRING -> cell.getStringCellValue();
                case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
                case ERROR -> "ERROR_" + cell.getErrorCellValue();
                default -> null;
            };
        }
        return getStringValue(cell);
    }

    public String getStringValue(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getDateCellValue().toString();
                }
                double d = cell.getNumericCellValue();
                if (d == Math.floor(d) && !Double.isInfinite(d)) {
                    yield String.valueOf((long) d);
                }
                yield BigDecimal.valueOf(d).toPlainString();
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> getCachedValueAsString(cell);
            case BLANK -> null;
            default -> null;
        };
    }

    public BigDecimal getNumericValue(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        }
        if (cell.getCellType() == CellType.FORMULA) {
            if (cell.getCachedFormulaResultType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(cell.getNumericCellValue());
            }
            try {
                CellValue cv = evaluator.evaluate(cell);
                if (cv != null && cv.getCellType() == CellType.NUMERIC) {
                    return BigDecimal.valueOf(cv.getNumberValue());
                }
            } catch (Exception ignored) { }
        }
        if (cell.getCellType() == CellType.STRING) {
            String str = cell.getStringCellValue().trim().replace(" ", "").replace(",", ".");
            if (str.isEmpty()) return null;
            try {
                return new BigDecimal(str);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public LocalTime getLocalTimeValue(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) {
            double num = cell.getNumericCellValue();
            // Fraction de jour Excel (ex. 0.33333 = 08:00)
            double totalSeconds = (num - Math.floor(num)) * 86400.0;
            long roundedSec = Math.round(totalSeconds);
            if (roundedSec >= 86400) roundedSec = 86399;
            int hours = (int) (roundedSec / 3600);
            int minutes = (int) ((roundedSec % 3600) / 60);
            int seconds = (int) (roundedSec % 60);
            return LocalTime.of(hours, minutes, seconds);
        }
        if (cell.getCellType() == CellType.STRING) {
            String s = cell.getStringCellValue().trim();
            if (s.isEmpty() || s.equalsIgnoreCase("CONGES") || s.equalsIgnoreCase("FERIE")) return null;
            try {
                if (s.length() == 5) s = s + ":00";
                return LocalTime.parse(s.substring(0, 8));
            } catch (Exception ignored) { }
        }
        return null;
    }

    public String getCellAddress(Cell cell) {
        if (cell == null) return "";
        return cell.getAddress().formatAsString();
    }

    public String getFilePath() {
        return filePath;
    }

    @Override
    public void close() throws Exception {
        workbook.close();
    }
}
