package mg.galaxam.sirh.paie.application.export;

import mg.galaxam.sirh.paie.persistence.BulletinPaie;
import mg.galaxam.sirh.paie.persistence.BulletinPaieLigne;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Récapitulatif annuel issu des mêmes résultats de bulletins que le grand livre mensuel. */
@Service
public class AnnualPayrollExportService {
    public record Result(byte[] bytes, int coveredMonths, int employees) {}
    private record Period(int id, String code) {}
    record PayRow(int employeeId, String period, String[] identity, BigDecimal[] values) {}

    public Result export(int year, Connection connection) throws Exception {
        if (year < 2000 || year > 2100) throw new IllegalArgumentException("Année invalide.");
        List<Period> periods = new ArrayList<>();
        String sql = "SELECT p.id_periode_paie, p.code, s.code AS statut FROM periode_paie p " +
                "JOIN ref_statut_periode_paie s ON s.id_statut_periode=p.id_statut_periode " +
                "WHERE p.code LIKE ? ORDER BY p.code";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, year + "-%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String code = rs.getString("code");
                    if (!code.matches(year + "-(0[1-9]|1[0-2])")) continue;
                    String status = rs.getString("statut");
                    if ("PAIE_VALIDEE".equals(status) || "CLOTUREE".equals(status)) {
                        if (periods.stream().anyMatch(p -> p.code().equals(code)))
                            throw new IllegalStateException("Plusieurs campagnes portent le code " + code + ".");
                        periods.add(new Period(rs.getInt("id_periode_paie"), code));
                    }
                }
            }
        }
        if (periods.isEmpty()) throw new IllegalStateException("Aucune période de paie validée pour " + year + ".");
        Map<String, PayRow> monthly = new LinkedHashMap<>();
        for (Period period : periods) {
            Map<Integer, String> filiales = GrandLivrePaieExportService.loadFiliales(period.id(), connection);
            for (BulletinPaie bulletin : BulletinPaie.listerParPeriode(period.id(), connection)) {
                if (!"VALIDE".equals(bulletin.getStatut()) && !"CLOTURE".equals(bulletin.getStatut()))
                    throw new IllegalStateException("Bulletin non validé dans la période " + period.code() + ".");
                List<BulletinPaieLigne> lines = BulletinPaieLigne.listerParBulletin(bulletin.getIdBulletin(), connection);
                String key = period.code() + ":" + bulletin.getIdEmploye();
                String[] identity = GrandLivrePaieExportService.summaryIdentity(bulletin, filiales.get(bulletin.getIdBulletin()));
                BigDecimal[] values = normalized(GrandLivrePaieExportService.summaryValues(bulletin, lines));
                PayRow previous = monthly.get(key);
                monthly.put(key, previous == null ? new PayRow(bulletin.getIdEmploye(), period.code(), identity, values)
                        : new PayRow(bulletin.getIdEmploye(), period.code(), identity, add(previous.values(), values)));
            }
        }
        if (monthly.isEmpty()) throw new IllegalStateException("Aucun bulletin validé pour " + year + ".");
        return render(year, periods.stream().map(Period::code).toList(), new ArrayList<>(monthly.values()));
    }

    Result render(int year, List<String> periodCodes, List<PayRow> monthlyRows) throws Exception {
        if (monthlyRows.isEmpty()) throw new IllegalStateException("Aucun bulletin à récapituler.");
        monthlyRows.sort(Comparator.comparing(PayRow::period).thenComparing(r -> text(r.identity()[0])));
        Map<Integer, PayRow> annual = new LinkedHashMap<>();
        for (PayRow row : monthlyRows) {
            PayRow previous = annual.get(row.employeeId());
            annual.put(row.employeeId(), previous == null ? new PayRow(row.employeeId(), row.period(), row.identity(), normalized(row.values()))
                    : new PayRow(row.employeeId(), row.period(), row.identity(), add(previous.values(), row.values())));
        }
        List<PayRow> annualRows = annual.values().stream().sorted(Comparator.comparing(r -> text(r.identity()[0]))).toList();
        try (InputStream input = getClass().getResourceAsStream("/templates/annuel/RECAP_ANNUEL_PAIE.xlsx")) {
            if (input == null) throw new IllegalStateException("Modèle annuel introuvable.");
            try (Workbook book = WorkbookFactory.create(input); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                Sheet summary = book.getSheet("Récap annuel");
                Sheet detail = book.getSheet("Détail mensuel");
                if (summary == null || detail == null) throw new IllegalStateException("Structure du modèle annuel invalide.");
                int readme = book.getSheetIndex("README");
                if (readme >= 0) book.removeSheetAt(readme);
                String coverage = year + "  ·  " + periodCodes.size() + "/12 périodes validées  ·  " + String.join(", ", periodCodes);
                summary.getRow(1).getCell(0).setCellValue(coverage);
                detail.getRow(1).getCell(0).setCellValue("Détail par salarié et par période  ·  " + coverage);
                summary.getRow(3).getCell(17).setCellValue("Impôt retenu");
                detail.getRow(3).getCell(19).setCellValue("Impôt retenu");
                detail.getRow(3).getCell(1).setCellValue("Mois de paie");
                // Colonnes de suivi du canevas masquées : le document livré reste lisible sans identifiants techniques.
                detail.setColumnHidden(26, true); detail.setColumnHidden(27, true);
                CellStyle[] summaryStyle = styles(summary.getRow(4), 24);
                CellStyle[] alternateStyle = styles(summary.getRow(5), 24);
                CellStyle[] detailStyle = styles(detail.getRow(4), 26);
                CellStyle[] totalStyle = styles(summary.getRow(104), 24);
                CellStyle titleStyle = summary.getRow(0).getCell(0).getCellStyle();
                CellStyle subtitleStyle = summary.getRow(1).getCell(0).getCellStyle();
                CellStyle headerStyle = summary.getRow(3).getCell(0).getCellStyle();
                for (int index = summary.getNumMergedRegions() - 1; index >= 0; index--)
                    if (summary.getMergedRegion(index).getFirstRow() == 104) summary.removeMergedRegion(index);
                clearFrom(summary, 4); clearFrom(detail, 4);
                for (int i = 0; i < annualRows.size(); i++) {
                    PayRow item = annualRows.get(i);
                    Row row = styledRow(summary, 4 + i, summaryStyle);
                    for (int col = 0; col < 6; col++) row.getCell(col).setCellValue(text(item.identity()[col]));
                    for (int col = 0; col < 18; col++) row.getCell(6 + col).setCellValue(item.values()[col].doubleValue());
                }
                int totalIndex = 4 + annualRows.size();
                Row total = styledRow(summary, totalIndex, totalStyle);
                total.getCell(0).setCellValue("TOTAL DES PÉRIODES INCLUSES");
                summary.addMergedRegion(new CellRangeAddress(totalIndex, totalIndex, 0, 5));
                for (int col = 6; col < 24; col++) {
                    String letter = CellReference.convertNumToColString(col);
                    total.getCell(col).setCellFormula("SUM(" + letter + "5:" + letter + totalIndex + ")");
                }
                for (int i = 0; i < monthlyRows.size(); i++) {
                    PayRow item = monthlyRows.get(i);
                    Row row = styledRow(detail, 4 + i, detailStyle);
                    row.getCell(0).setCellValue(year); row.getCell(1).setCellValue(monthLabel(item.period()));
                    for (int col = 0; col < 6; col++) row.getCell(2 + col).setCellValue(text(item.identity()[col]));
                    for (int col = 0; col < 18; col++) row.getCell(8 + col).setCellValue(item.values()[col].doubleValue());
                }
                summary.createFreezePane(2, 4); detail.createFreezePane(4, 4);
                int[] summaryTextWidths = {14, 32, 25, 28, 24, 17};
                for (int col = 0; col < summaryTextWidths.length; col++) summary.setColumnWidth(col, summaryTextWidths[col] * 256);
                int[] detailTextWidths = {11, 16, 14, 32, 25, 28, 24, 17};
                for (int col = 0; col < detailTextWidths.length; col++) detail.setColumnWidth(col, detailTextWidths[col] * 256);
                summary.setAutoFilter(new CellRangeAddress(3, totalIndex - 1, 0, 23));
                detail.setAutoFilter(new CellRangeAddress(3, 3 + monthlyRows.size(), 0, 25));
                ((XSSFSheet) summary).getTables().get(0).setArea(new AreaReference(
                        "A4:X" + (4 + annualRows.size()), SpreadsheetVersion.EXCEL2007));
                ((XSSFSheet) detail).getTables().get(0).setArea(new AreaReference(
                        "A4:AB" + (4 + monthlyRows.size()), SpreadsheetVersion.EXCEL2007));
                writeMonthlyOverview(book, year, periodCodes, monthlyRows, annualRows.size(),
                        titleStyle, subtitleStyle, headerStyle, summaryStyle, alternateStyle, totalStyle);
                for (Sheet sheet : List.of(summary, detail)) {
                    sheet.setDisplayGridlines(false); sheet.setFitToPage(true);
                    sheet.getPrintSetup().setLandscape(true);
                    sheet.getPrintSetup().setPaperSize(PrintSetup.A3_PAPERSIZE);
                    sheet.getPrintSetup().setFitWidth((short) 1);
                    sheet.getPrintSetup().setFitHeight((short) 0);
                }
                book.getCreationHelper().createFormulaEvaluator().evaluateAll();
                book.write(output);
                return new Result(output.toByteArray(), periodCodes.size(), annualRows.size());
            }
        }
    }

    private static BigDecimal[] normalized(BigDecimal[] values) {
        BigDecimal[] copy = new BigDecimal[values.length];
        for (int i = 0; i < values.length; i++) copy[i] = values[i] == null ? BigDecimal.ZERO : values[i];
        return copy;
    }
    private static void writeMonthlyOverview(Workbook book, int year, List<String> periodCodes,
                                             List<PayRow> monthlyRows, int employees,
                                             CellStyle titleStyle, CellStyle subtitleStyle, CellStyle headerStyle,
                                             CellStyle[] bodyStyle, CellStyle[] alternateStyle, CellStyle[] totalStyle) {
        Sheet sheet = book.createSheet("Synthèse par mois");
        book.setSheetOrder(sheet.getSheetName(), 1);
        sheet.setDisplayGridlines(false);
        Row title = sheet.createRow(0); title.setHeightInPoints(30);
        for (int col = 0; col < 11; col++) title.createCell(col).setCellStyle(titleStyle);
        title.getCell(0).setCellValue("GALAXAM — SYNTHÈSE MENSUELLE DE LA PAIE");
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 10));
        Row subtitle = sheet.createRow(1);
        for (int col = 0; col < 11; col++) subtitle.createCell(col).setCellStyle(subtitleStyle);
        subtitle.getCell(0).setCellValue(year + "  ·  " + periodCodes.size() + "/12 périodes validées  ·  montants en ariary");
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 10));
        String[] headers = {"Mois", "Statut", "Salariés", "Salaire brut", "CNaPS salarié", "Santé salarié",
                "Net imposable", "Impôt retenu", "Net à payer", "Cotisations patronales", "Coût employeur"};
        Row head = sheet.createRow(3); head.setHeightInPoints(30);
        for (int col = 0; col < headers.length; col++) {
            Cell cell = head.createCell(col); cell.setCellStyle(headerStyle); cell.setCellValue(headers[col]);
            sheet.setColumnWidth(col, (col == 0 ? 21 : col == 1 ? 16 : col == 2 ? 13 : 22) * 256);
        }
        int[] metrics = {7, 8, 9, 10, 11, 15, 16, 17};
        Set<String> included = Set.copyOf(periodCodes);
        for (int month = 1; month <= 12; month++) {
            String code = "%04d-%02d".formatted(year, month);
            List<PayRow> monthRows = monthlyRows.stream().filter(row -> code.equals(row.period())).toList();
            Row row = styledRow(sheet, 3 + month, Arrays.copyOf(month % 2 == 0 ? alternateStyle : bodyStyle, 11));
            row.getCell(0).setCellValue(monthLabel(code));
            row.getCell(1).setCellValue(included.contains(code) ? "Inclus" : "Non inclus");
            if (!included.contains(code)) continue;
            row.getCell(2).setCellValue(monthRows.stream().map(PayRow::employeeId).collect(Collectors.toSet()).size());
            for (int col = 0; col < metrics.length; col++) {
                final int metric = metrics[col];
                BigDecimal total = monthRows.stream().map(r -> r.values()[metric])
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                row.getCell(3 + col).setCellValue(total.doubleValue());
                row.getCell(3 + col).setCellStyle(month % 2 == 0 ? alternateStyle[13] : bodyStyle[13]);
            }
        }
        Row total = styledRow(sheet, 16, Arrays.copyOf(totalStyle, 11));
        total.getCell(0).setCellValue("TOTAL DES MOIS INCLUS");
        sheet.addMergedRegion(new CellRangeAddress(16, 16, 0, 1));
        total.getCell(2).setCellValue(employees);
        for (int col = 3; col < 11; col++) {
            String letter = CellReference.convertNumToColString(col);
            total.getCell(col).setCellStyle(totalStyle[13]);
            total.getCell(col).setCellFormula("SUM(" + letter + "5:" + letter + "16)");
        }
        sheet.createFreezePane(1, 4);
        sheet.setFitToPage(true);
        sheet.getPrintSetup().setLandscape(true);
        sheet.getPrintSetup().setPaperSize(PrintSetup.A4_PAPERSIZE);
        sheet.getPrintSetup().setFitWidth((short) 1);
        sheet.getPrintSetup().setFitHeight((short) 1);
    }
    private static String monthLabel(String code) {
        LocalDate date = LocalDate.parse(code + "-01");
        String name = date.getMonth().getDisplayName(TextStyle.FULL, Locale.FRENCH);
        return "%02d — %s %d".formatted(date.getMonthValue(),
                Character.toUpperCase(name.charAt(0)) + name.substring(1), date.getYear());
    }
    private static BigDecimal[] add(BigDecimal[] a, BigDecimal[] b) {
        BigDecimal[] out = new BigDecimal[a.length];
        for (int i = 0; i < a.length; i++) out[i] = a[i].add(b[i] == null ? BigDecimal.ZERO : b[i]);
        return out;
    }
    private static String text(String value) { return value == null ? "" : value; }
    private static CellStyle[] styles(Row row, int count) {
        CellStyle[] styles = new CellStyle[count];
        for (int i = 0; i < count; i++) styles[i] = row.getCell(i) == null ? null : row.getCell(i).getCellStyle();
        return styles;
    }
    private static void clearFrom(Sheet sheet, int first) {
        for (int i = sheet.getLastRowNum(); i >= first; i--) {
            Row row = sheet.getRow(i);
            if (row != null) sheet.removeRow(row);
        }
    }
    private static Row styledRow(Sheet sheet, int index, CellStyle[] styles) {
        Row row = sheet.createRow(index);
        row.setHeightInPoints(24);
        for (int i = 0; i < styles.length; i++) {
            Cell cell = row.createCell(i);
            if (styles[i] != null) cell.setCellStyle(styles[i]);
        }
        return row;
    }
}
