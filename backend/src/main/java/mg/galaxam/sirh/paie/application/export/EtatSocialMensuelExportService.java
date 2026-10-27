package mg.galaxam.sirh.paie.application.export;

import mg.galaxam.sirh.paie.persistence.BulletinPaie;
import mg.galaxam.sirh.paie.persistence.BulletinPaieLigne;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** État interne mensuel des cotisations constatées sur les bulletins, avec regroupement dynamique par organisme social. */
@Service
public class EtatSocialMensuelExportService {
    private static final String[] SUMMARY = {"Organisme", "Salariés concernés", "Part salariale (Ar)", "Part patronale (Ar)", "Total (Ar)"};
    private static final String[] DETAIL = {"Filiale", "Site", "Matricule", "Nom et prénom", "N° CNaPS", "Organisme", "Part salariale (Ar)", "Part patronale (Ar)", "Total (Ar)"};
    public static final String UNCONFIGURED_ORGANISME = "Organisme non configuré";

    public record Result(byte[] bytes, String periodCode, int employeeCount, int lineCount) {}
    private record DetailKey(Integer employeeId, String filiale, String site, String organisme) {}
    private static final class DetailRow {
        final BulletinPaie bulletin;
        final DetailKey key;
        BigDecimal employee = BigDecimal.ZERO;
        BigDecimal employer = BigDecimal.ZERO;
        DetailRow(BulletinPaie bulletin, DetailKey key) { this.bulletin = bulletin; this.key = key; }
    }

    public Result export(Integer periodId, Connection connection) throws Exception {
        String periodCode = periodCode(periodId, connection);
        PayrollExportEligibility.requireFinalPeriod(periodId, connection);
        List<BulletinPaie> bulletins = BulletinPaie.listerParPeriode(periodId, connection);
        if (bulletins.isEmpty()) throw new IllegalStateException("Aucun bulletin pour cette période.");
        PayrollExportEligibility.requireFinalBulletins(bulletins, periodCode);
        Map<Integer, String> filiales = loadFiliales(periodId, connection);
        Map<String, String> registeredOrganismes = loadOrganismes(connection);
        Map<Integer, List<BulletinPaieLigne>> lignesParBulletin = BulletinPaieLigne.listerParBulletins(
                bulletins.stream().map(BulletinPaie::getIdBulletin).toList(), connection);

        Map<DetailKey, DetailRow> detailRows = new LinkedHashMap<>();
        Map<String, Set<Integer>> people = new LinkedHashMap<>();
        Map<String, BigDecimal[]> amounts = new LinkedHashMap<>();
        boolean hasUnassociated = false;

        for (BulletinPaie b : bulletins) {
            for (BulletinPaieLigne line : lignesParBulletin.getOrDefault(b.getIdBulletin(), List.of())) {
                if (!isSocial(line)) continue;
                BigDecimal salariale = zero(line.getMontantSalarial());
                BigDecimal patronale = zero(line.getMontantPatronal());
                if (salariale.signum() == 0 && patronale.signum() == 0) continue;

                String organisme = resolveOrganisme(line, registeredOrganismes);
                if (UNCONFIGURED_ORGANISME.equals(organisme)) {
                    hasUnassociated = true;
                }

                DetailKey key = new DetailKey(b.getIdEmploye(), filiales.getOrDefault(b.getIdBulletin(), ""),
                        n(b.getNomSite()), organisme);
                DetailRow detailRow = detailRows.computeIfAbsent(key, ignored -> new DetailRow(b, key));
                detailRow.employee = detailRow.employee.add(salariale);
                detailRow.employer = detailRow.employer.add(patronale);
                people.computeIfAbsent(organisme, ignored -> new TreeSet<>()).add(b.getIdEmploye());
                BigDecimal[] sums = amounts.computeIfAbsent(organisme, ignored -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                sums[0] = sums[0].add(salariale);
                sums[1] = sums[1].add(patronale);
            }
        }

        List<DetailRow> rows = detailRows.values().stream()
                .sorted(Comparator.comparing((DetailRow d) -> d.key.organisme())
                        .thenComparing(d -> n(d.bulletin.getMatricule())))
                .toList();

        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);
            Sheet summary = workbook.createSheet("Synthèse sociale");
            Sheet detail = workbook.createSheet("Détail nominatif");
            summary.setDisplayGridlines(false); detail.setDisplayGridlines(false);

            banner(summary, "ÉTAT SOCIAL MENSUEL", SUMMARY.length - 1, styles.title);
            heading(summary, 1, "Période " + periodCode + " · données des bulletins validés ou clôturés", styles.muted);
            heading(summary, 2, "Document interne de contrôle · regroupement dynamique par organisme social", styles.muted);

            int startRow = 4;
            if (hasUnassociated) {
                heading(summary, 3, "⚠️ AVERTISSEMENT : Des cotisations sans organisme configuré ont été détectées et regroupées sous '" + UNCONFIGURED_ORGANISME + "'.", styles.warning);
                startRow = 5;
            }

            header(summary, startRow, SUMMARY, styles.header);
            summary.setColumnWidth(0, 9000);
            for (int i = 1; i < SUMMARY.length; i++) summary.setColumnWidth(i, 5700);

            int sr = startRow + 1;
            for (String organisme : amounts.keySet().stream().sorted().toList()) {
                BigDecimal[] sums = amounts.get(organisme);
                cell(summary, sr, 0, styles.text).setCellValue(organisme);
                cell(summary, sr, 1, styles.integer).setCellValue(people.get(organisme).size());
                cell(summary, sr, 2, styles.money).setCellValue(sums[0].doubleValue());
                cell(summary, sr, 3, styles.money).setCellValue(sums[1].doubleValue());
                cell(summary, sr, 4, styles.money).setCellValue(sums[0].add(sums[1]).doubleValue());
                sr++;
            }
            cell(summary, sr, 0, styles.totalText).setCellValue("TOTAL DES COTISATIONS");
            for (int col = 2; col <= 4; col++) {
                String letter = org.apache.poi.ss.util.CellReference.convertNumToColString(col);
                Cell total = cell(summary, sr, col, styles.totalMoney);
                if (amounts.isEmpty()) total.setCellValue(0);
                else total.setCellFormula("SUM(" + letter + (startRow + 2) + ":" + letter + sr + ")");
            }

            cell(summary, sr + 2, 0, styles.muted).setCellValue("Source : lignes de cotisation des bulletins de la période.");
            cell(summary, sr + 3, 0, styles.muted).setCellValue("Un salarié présent dans plusieurs organismes est compté dans chacun d'eux.");
            if (hasUnassociated) {
                cell(summary, sr + 4, 0, styles.warning).setCellValue("Lignes non associées détectées : vérifier la configuration des types et sites de cotisation.");
            }
            summary.createFreezePane(0, startRow + 1);

            banner(detail, "COTISATIONS PAR SALARIÉ", DETAIL.length - 1, styles.title);
            heading(detail, 1, "Période " + periodCode + " · une ligne par salarié et par organisme", styles.muted);
            int detailHeaderRow = 3;
            if (hasUnassociated) {
                heading(detail, 2, "⚠️ AVERTISSEMENT : Des cotisations comportent la mention '" + UNCONFIGURED_ORGANISME + "'.", styles.warning);
                detailHeaderRow = 4;
            }
            header(detail, detailHeaderRow, DETAIL, styles.header);
            for (int i = 0; i < DETAIL.length; i++) detail.setColumnWidth(i, i == 0 || i == 3 ? 10500 : i >= 6 ? 5700 : 5200);
            detail.setColumnWidth(2, 3300);

            int dr = detailHeaderRow + 1;
            for (DetailRow entry : rows) {
                BulletinPaie b = entry.bulletin;
                String[] fields = {entry.key.filiale(), entry.key.site(), b.getMatricule(), b.getNomPrenom(),
                        b.getNumeroCnaps(), entry.key.organisme()};
                for (int i = 0; i < fields.length; i++) cell(detail, dr, i, styles.text).setCellValue(n(fields[i]));
                BigDecimal[] values = {entry.employee, entry.employer, entry.employee.add(entry.employer)};
                for (int i = 0; i < values.length; i++) {
                    cell(detail, dr, 6 + i, styles.money).setCellValue(values[i].doubleValue());
                }
                dr++;
            }
            cell(detail, dr, 3, styles.totalText).setCellValue("TOTAL PÉRIODE");
            for (int col = 6; col <= 8; col++) {
                Cell total = cell(detail, dr, col, styles.totalMoney);
                if (rows.isEmpty()) total.setCellValue(0);
                else {
                    String letter = org.apache.poi.ss.util.CellReference.convertNumToColString(col);
                    total.setCellFormula("SUM(" + letter + (detailHeaderRow + 2) + ":" + letter + dr + ")");
                }
            }
            detail.createFreezePane(4, detailHeaderRow + 1);
            if (!rows.isEmpty()) detail.setAutoFilter(new CellRangeAddress(detailHeaderRow, dr - 1, 0, DETAIL.length - 1));

            Map<String, List<DetailRow>> byCompany = new LinkedHashMap<>();
            for (BulletinPaie bulletin : bulletins) {
                String name = filiales.getOrDefault(bulletin.getIdBulletin(), "Filiale");
                byCompany.computeIfAbsent(name == null || name.isBlank() ? "Filiale" : name,
                        ignored -> new java.util.ArrayList<>());
            }
            for (DetailRow row : rows)
                byCompany.computeIfAbsent(row.key.filiale().isBlank() ? "Filiale" : row.key.filiale(),
                        ignored -> new java.util.ArrayList<>()).add(row);
            for (var entry : byCompany.entrySet()) {
                Sheet companySheet = workbook.createSheet(PayrollSheetNames.unique(workbook, entry.getKey()));
                writeCompanySheet(companySheet, periodCode, entry.getKey(), entry.getValue(), styles);
            }

            for (Sheet sheet : workbook) {
                sheet.getPrintSetup().setLandscape(sheet != summary);
                sheet.getPrintSetup().setPaperSize(PrintSetup.A4_PAPERSIZE);
                sheet.setFitToPage(true);
                sheet.getPrintSetup().setFitWidth((short) 1);
                sheet.getPrintSetup().setFitHeight((short) 0);
            }
            workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
            workbook.write(out);
            Set<Integer> uniqueEmployees = new TreeSet<>();
            people.values().forEach(uniqueEmployees::addAll);
            return new Result(PayrollWorkbookAnalysis.monthly(out.toByteArray(), PayrollWorkbookAnalysis.Kind.SOCIAL, periodId, connection), periodCode, uniqueEmployees.size(), rows.size());
        }
    }

    private static void writeCompanySheet(Sheet sheet, String periodCode, String company,
            List<DetailRow> rows, Styles styles) {
        banner(sheet, "ÉTAT SOCIAL · " + company, DETAIL.length - 1, styles.title);
        heading(sheet, 1, "Période " + periodCode, styles.muted);
        header(sheet, 3, DETAIL, styles.header);
        for (int col = 0; col < DETAIL.length; col++)
            sheet.setColumnWidth(col, col == 0 || col == 3 ? 10500 : col >= 6 ? 5700 : 5200);
        int row = 4;
        for (DetailRow entry : rows) {
            BulletinPaie bulletin = entry.bulletin;
            String[] fields = {company, entry.key.site(), bulletin.getMatricule(), bulletin.getNomPrenom(),
                    bulletin.getNumeroCnaps(), entry.key.organisme()};
            for (int col = 0; col < fields.length; col++)
                cell(sheet, row, col, styles.text).setCellValue(n(fields[col]));
            cell(sheet, row, 6, styles.money).setCellValue(entry.employee.doubleValue());
            cell(sheet, row, 7, styles.money).setCellValue(entry.employer.doubleValue());
            cell(sheet, row, 8, styles.money).setCellValue(entry.employee.add(entry.employer).doubleValue());
            row++;
        }
        cell(sheet, row, 3, styles.totalText).setCellValue("TOTAL FILIALE");
        for (int col = 6; col <= 8; col++) {
            String letter = org.apache.poi.ss.util.CellReference.convertNumToColString(col);
            Cell total = cell(sheet, row, col, styles.totalMoney);
            if (rows.isEmpty()) total.setCellValue(0);
            else total.setCellFormula("SUM(" + letter + "5:" + letter + row + ")");
        }
        sheet.createFreezePane(4, 4);
        sheet.setAutoFilter(new CellRangeAddress(3, row - 1, 0, DETAIL.length - 1));
    }

    static boolean isSocial(BulletinPaieLigne line) {
        String code = n(line.getCodeRubrique()).toUpperCase(Locale.ROOT);
        String category = n(line.getCategorie()).toUpperCase(Locale.ROOT);
        return code.startsWith("COTISATION_") || category.startsWith("COTISATION_");
    }

    public static String resolveOrganisme(BulletinPaieLigne line, Map<String, String> registeredOrganismes) {
        if (line.getCodeOrganisme() != null && !line.getCodeOrganisme().isBlank()) {
            String orgCode = line.getCodeOrganisme().trim().toUpperCase(Locale.ROOT);
            return registeredOrganismes.getOrDefault(orgCode, line.getCodeOrganisme().trim());
        }
        // Fallback résilient si anciennes lignes sans code_organisme
        String code = n(line.getCodeRubrique()).toUpperCase(Locale.ROOT);
        if (code.contains("CNAPS")) return "CNaPS";
        if (code.contains("FMFP")) return "FMFP";
        if (code.contains("OSTIE")) return "OSTIE";
        if (code.contains("OMSI")) return "OMSI";
        if (code.contains("AMIT")) return "AMIT";
        if (code.contains("SMIE")) return "SMIE";
        return UNCONFIGURED_ORGANISME;
    }

    static Map<String, String> loadOrganismes(Connection c) throws Exception {
        Map<String, String> map = new LinkedHashMap<>();
        String sql = "SELECT code, libelle FROM ref_organisme_social";
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String code = rs.getString("code");
                String upper = code.toUpperCase(Locale.ROOT);
                String display = "CNAPS".equalsIgnoreCase(code) ? "CNaPS" : code;
                map.put(upper, display);
            }
        }
        return map;
    }

    private static String periodCode(Integer id, Connection c) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("SELECT code FROM periode_paie WHERE id_periode_paie = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Période de paie introuvable.");
                return rs.getString(1);
            }
        }
    }

    private static Map<Integer, String> loadFiliales(Integer id, Connection c) throws Exception {
        return PayrollExportContextResolver.namesByBulletin(id, c);
    }

    private static void heading(Sheet sheet, int row, String value, CellStyle style) {
        Row r = sheet.getRow(row); if (r == null) r = sheet.createRow(row);
        Cell c = r.getCell(0); if (c == null) c = r.createCell(0);
        c.setCellStyle(style);
        c.setCellValue(value);
    }

    private static void banner(Sheet sheet, String value, int lastColumn, CellStyle style) {
        for (int col = 0; col <= lastColumn; col++) cell(sheet, 0, col, style);
        sheet.getRow(0).getCell(0).setCellValue(value);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, lastColumn));
        sheet.getRow(0).setHeightInPoints(36);
    }

    private static void header(Sheet sheet, int row, String[] values, CellStyle style) {
        for (int col = 0; col < values.length; col++) cell(sheet, row, col, style).setCellValue(values[col]);
        sheet.getRow(row).setHeightInPoints(30);
    }

    private static Cell cell(Sheet sheet, int row, int col, CellStyle style) {
        Row r = sheet.getRow(row); if (r == null) r = sheet.createRow(row);
        Cell c = r.getCell(col); if (c == null) c = r.createCell(col);
        c.setCellStyle(style); return c;
    }

    private static BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private static String n(String value) { return value == null ? "" : value; }

    private static final class Styles {
        final CellStyle title, muted, warning, header, text, integer, money, totalText, totalMoney;
        Styles(Workbook workbook) {
            byte[] navy = new byte[]{7, 50, 74};                          // #07324A --color-brand-900
            byte[] totalBg = new byte[]{(byte)225, (byte)237, (byte)242}; // #E1EDF2 --color-brand-100
            Font titleFont = workbook.createFont(); titleFont.setBold(true); titleFont.setFontHeightInPoints((short) 16); titleFont.setColor(IndexedColors.WHITE.getIndex());
            Font headerFont = workbook.createFont(); headerFont.setBold(true); headerFont.setColor(IndexedColors.WHITE.getIndex());
            Font totalFont = workbook.createFont(); totalFont.setBold(true); totalFont.setColor(IndexedColors.BLACK.getIndex());
            Font warnFont = workbook.createFont(); warnFont.setBold(true); warnFont.setColor(IndexedColors.DARK_RED.getIndex());

            title = workbook.createCellStyle(); title.setFont(titleFont);
            ((org.apache.poi.xssf.usermodel.XSSFCellStyle) title).setFillForegroundColor(new org.apache.poi.xssf.usermodel.XSSFColor(navy, null));
            title.setFillPattern(FillPatternType.SOLID_FOREGROUND); title.setVerticalAlignment(VerticalAlignment.CENTER);
            muted = workbook.createCellStyle(); muted.setFont(workbook.createFont());
            warning = workbook.createCellStyle(); warning.setFont(warnFont);
            header = workbook.createCellStyle(); header.setFont(headerFont);
            ((org.apache.poi.xssf.usermodel.XSSFCellStyle) header).setFillForegroundColor(new org.apache.poi.xssf.usermodel.XSSFColor(navy, null));
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND); header.setWrapText(true); border(header);
            text = workbook.createCellStyle(); border(text);
            integer = workbook.createCellStyle(); integer.setDataFormat(workbook.createDataFormat().getFormat("#,##0")); border(integer);
            money = workbook.createCellStyle(); money.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00")); border(money);
            totalText = workbook.createCellStyle(); totalText.setFont(totalFont);
            ((org.apache.poi.xssf.usermodel.XSSFCellStyle) totalText).setFillForegroundColor(new org.apache.poi.xssf.usermodel.XSSFColor(totalBg, null));
            totalText.setFillPattern(FillPatternType.SOLID_FOREGROUND); border(totalText);
            totalMoney = workbook.createCellStyle(); totalMoney.cloneStyleFrom(totalText); totalMoney.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
        }
        private static void border(CellStyle style) {
            style.setBorderBottom(BorderStyle.THIN); style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN); style.setBorderTop(BorderStyle.THIN);
            style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            style.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            style.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            style.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        }
    }
}
