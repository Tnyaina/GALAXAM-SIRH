package mg.galaxam.sirh.paie.application.export;

import mg.galaxam.sirh.paie.persistence.BulletinPaie;
import mg.galaxam.sirh.paie.persistence.BulletinPaieLigne;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Grand livre mensuel : un état nominatif, chaque ligne de bulletin, puis des contrôles de lecture. */
@Service
public class GrandLivrePaieExportService {
    private static final String[] SUMMARY_COLUMNS = {
            "Matricule", "Nom & prénom", "Filiale", "Site", "Poste", "Type contrat",
            "Salaire de base", "Absence (h)", "Retenue absence", "Congés (j)", "Indemnité congé",
            "Heures sup.", "Indemnités / primes", "Salaire brut", "CNaPS salarié", "Santé salarié",
            "Net imposable", "IRSA", "Avances et acomptes", "Autres retenues",
            "Net à payer", "Cotisations patronales", "Coût employeur"
    };
    private static final String[] DETAIL_COLUMNS = {
            "Référence bulletin", "Matricule", "Nom & prénom", "Code rubrique", "Libellé rubrique",
            "Sens", "Catégorie", "Base calcul", "Quantité", "Taux salarié %", "Taux patronal %",
            "Montant salarié", "Montant patronal"
    };
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE);

    public record Result(byte[] bytes, String periodCode, int bulletinCount, int detailCount) {}
    record Period(String code, LocalDate start, LocalDate end) {}

    public Result export(Integer periodId, Connection connection) throws Exception {
        Period period = loadPeriod(periodId, connection);
        PayrollExportEligibility.requireFinalPeriod(periodId, connection);
        List<BulletinPaie> bulletins = BulletinPaie.listerParPeriode(periodId, connection);
        if (bulletins.isEmpty()) throw new IllegalStateException("Aucun bulletin pour cette période.");
        PayrollExportEligibility.requireFinalBulletins(bulletins, period.code());
        Map<Integer, String> filialByBulletin = loadFiliales(periodId, connection);
        Map<Integer, List<BulletinPaieLigne>> lignesParBulletin = BulletinPaieLigne.listerParBulletins(
                bulletins.stream().map(BulletinPaie::getIdBulletin).toList(), connection);
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);
            Sheet summary = workbook.createSheet("Grand livre");
            Sheet detail = workbook.createSheet("Détail rubriques");
            Sheet checks = workbook.createSheet("Contrôle");
            for (Sheet sheet : List.of(summary, detail, checks)) sheet.setDisplayGridlines(false);
            banner(summary, "GRAND LIVRE DE PAIE", 5, styles.title);
            title(summary, 1, "État mensuel nominatif · période " + period.code(), styles.subtitle);
            title(summary, 3, "Du " + period.start().format(DATE) + " au " + period.end().format(DATE)
                    + "   |   " + bulletins.size() + " bulletins validés ou clôturés", styles.subtitle);
            header(summary, 7, SUMMARY_COLUMNS, styles.header);
            summary.createFreezePane(6, 8);
            for (int col = 0; col < SUMMARY_COLUMNS.length; col++) summary.setColumnWidth(col, col == 1 ? 9500 : col < 6 ? 4800 : 5100);
            summary.setColumnWidth(0, 3600);
            summary.setRepeatingRows(new CellRangeAddress(7, 7, 0, SUMMARY_COLUMNS.length - 1));

            banner(detail, "DÉTAIL DES RUBRIQUES DE PAIE", 5, styles.title);
            title(detail, 1, "Toutes les lignes enregistrées des bulletins de la période " + period.code(), styles.subtitle);
            header(detail, 2, DETAIL_COLUMNS, styles.header);
            detail.createFreezePane(3, 3);
            for (int col = 0; col < DETAIL_COLUMNS.length; col++) detail.setColumnWidth(col, col == 4 ? 11500 : col == 2 ? 9500 : 5000);
            detail.setRepeatingRows(new CellRangeAddress(2, 2, 0, DETAIL_COLUMNS.length - 1));
            int detailRow = 3;
            int summaryRow = 8;
            for (BulletinPaie b : bulletins) {
                List<BulletinPaieLigne> lines = lignesParBulletin.getOrDefault(b.getIdBulletin(), List.of());
                writeSummary(summary, summaryRow++, b, lines, filialByBulletin.get(b.getIdBulletin()), styles);
                for (BulletinPaieLigne line : lines) writeDetail(detail, detailRow++, b, line, styles);
            }
            int totalRow = summaryRow + 1;
            Cell totalLabel = cell(summary, totalRow, 1, styles.totalLabel); totalLabel.setCellValue("TOTAL PÉRIODE");
            for (int col = 6; col < SUMMARY_COLUMNS.length; col++) {
                Cell c = cell(summary, totalRow, col, styles.totalMoney);
                String letter = org.apache.poi.ss.util.CellReference.convertNumToColString(col);
                c.setCellFormula("SUM(" + letter + "9:" + letter + summaryRow + ")");
            }
            summary.setAutoFilter(new CellRangeAddress(7, summaryRow - 1, 0, SUMMARY_COLUMNS.length - 1));
            detail.setAutoFilter(new CellRangeAddress(2, Math.max(3, detailRow - 1), 0, DETAIL_COLUMNS.length - 1));
            writeChecks(checks, period, bulletins.size(), detailRow - 3, totalRow, styles);
            for (Sheet sheet : List.of(summary, detail, checks)) {
                sheet.getPrintSetup().setLandscape(sheet != checks);
                sheet.getPrintSetup().setPaperSize(PrintSetup.A4_PAPERSIZE);
                sheet.setFitToPage(true);
                sheet.getPrintSetup().setFitWidth((short) 1);
                sheet.getPrintSetup().setFitHeight((short) 0);
            }
            workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
            workbook.setForceFormulaRecalculation(true);
            workbook.write(output);
            return new Result(output.toByteArray(), period.code(), bulletins.size(), detailRow - 3);
        }
    }

    private static Period loadPeriod(Integer id, Connection connection) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("SELECT code, date_debut, date_fin FROM periode_paie WHERE id_periode_paie = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Période de paie introuvable.");
                return new Period(rs.getString(1), rs.getDate(2).toLocalDate(), rs.getDate(3).toLocalDate());
            }
        }
    }
    static Map<Integer, String> loadFiliales(Integer periodId, Connection connection) throws Exception {
        return PayrollExportContextResolver.namesByBulletin(periodId, connection);
    }
    private static void writeSummary(Sheet sheet, int rowIndex, BulletinPaie b, List<BulletinPaieLigne> lines,
                                     String filiale, Styles s) {
        String[] identity = summaryIdentity(b, filiale);
        for (int col = 0; col < identity.length; col++) cell(sheet, rowIndex, col, s.text).setCellValue(n(identity[col]));
        BigDecimal[] values = summaryValues(b, lines);
        for (int i = 0; i < values.length; i++) {
            Cell c = cell(sheet, rowIndex, 6 + i, s.money);
            if (values[i] != null) c.setCellValue(values[i].doubleValue());
        }
    }
    static String[] summaryIdentity(BulletinPaie b, String filiale) {
        return new String[]{b.getMatricule(), b.getNomPrenom(), filiale, b.getNomSite(), b.getIntitulePoste(), b.getRegimeIntervenant()};
    }
    static BigDecimal[] summaryValues(BulletinPaie b, List<BulletinPaieLigne> lines) {
        BigDecimal absenceHours = quantity(lines, "RETENUE_ABSENCE");
        BigDecimal congéGain = sum(lines, l -> "GAIN".equals(l.getSens()) && isLeaveGain(l));
        BigDecimal hsGain = sum(lines, l -> "GAIN".equals(l.getSens()) && "HEURES_SUP".equals(l.getCategorie()));
        BigDecimal otherGains = sum(lines, l -> "GAIN".equals(l.getSens()) && !"SALAIRE_BASE".equals(l.getCategorie())
                && !isLeaveGain(l) && !"HEURES_SUP".equals(l.getCategorie()) && !isRounding(l));
        BigDecimal cnaps = sum(lines, l -> "RETENUE".equals(l.getSens()) && isCnaps(l));
        BigDecimal health = sum(lines, l -> "RETENUE".equals(l.getSens()) && isHealth(l));
        BigDecimal advances = sum(lines, l -> "RETENUE".equals(l.getSens()) && "ACOMPTE_AVANCE".equals(l.getCategorie()));
        BigDecimal others = sum(lines, l -> "RETENUE".equals(l.getSens()) && !"SALAIRE_BASE".equals(l.getCategorie())
                && !isCnaps(l) && !isHealth(l) && !"ACOMPTE_AVANCE".equals(l.getCategorie())
                && !"IMPOT".equals(l.getCategorie()) && !"RETENUE_ABSENCE".equals(code(l)) && !isRounding(l));
        BigDecimal[] values = {b.getSalaireBase(), absenceHours, amount(lines, "RETENUE_ABSENCE"), b.getJoursConges(), congéGain,
                zero(b.getHeuresHs30()).add(zero(b.getHeuresHs50())), otherGains.add(hsGain), b.getTotalGainsBruts(), cnaps, health,
                b.getRevenuNetImposable(), zero(b.getIrsaNet()).add(zero(b.getRetenueIsi5())), advances, others,
                b.getNetAPayer(), b.getTotalCotisationsPatronales(), b.getCoutGlobalEmployeur()};
        return values;
    }
    private static boolean isRounding(BulletinPaieLigne line) {
        String c = code(line);
        return c.contains("ARRONDI") || c.contains("MONETAIRE") || "NET".equalsIgnoreCase(line.getCategorie());
    }
    private static boolean isLeaveGain(BulletinPaieLigne line) {
        return java.util.Set.of("INDEMNITE_CONGE", "INDEMNITE_CONGE_PAYE", "INDEMNITE_COMPENSATRICE_CONGE")
                .contains(code(line));
    }
    private static boolean isCnaps(BulletinPaieLigne line) { return code(line).startsWith("COTISATION_CNAPS"); }
    private static boolean isHealth(BulletinPaieLigne line) {
        String c = code(line);
        if (c.contains("SANTE") || c.contains("OSTIE") || c.contains("OMSI") || c.contains("AMIT") || c.contains("SMIE")) {
            return true;
        }
        String org = line.getCodeOrganisme();
        if (org != null) {
            String o = org.toUpperCase(Locale.ROOT);
            return o.equals("OSTIE") || o.equals("OMSI") || o.equals("AMIT") || o.equals("SMIE");
        }
        return false;
    }

    private static void writeDetail(Sheet sheet, int rowIndex, BulletinPaie b, BulletinPaieLigne l, Styles s) {
        String[] fields = {b.getReferenceBulletin(), b.getMatricule(), b.getNomPrenom(), l.getCodeRubrique(),
                l.getLibelleRubrique(), l.getSens(), l.getCategorie()};
        for (int col = 0; col < fields.length; col++) cell(sheet, rowIndex, col, s.text).setCellValue(n(fields[col]));
        BigDecimal legacyPatronalRate = "PATRONAL".equals(l.getSens()) && l.getTauxPatronal() == null
                ? l.getTauxSalarial() : null;
        BigDecimal[] amounts = {l.getBaseCalcul(), l.getQuantite(),
                "PATRONAL".equals(l.getSens()) ? null : l.getTauxSalarial(),
                l.getTauxPatronal() != null ? l.getTauxPatronal() : legacyPatronalRate,
                l.getMontantSalarial(), l.getMontantPatronal()};
        for (int i = 0; i < amounts.length; i++) {
            Cell c = cell(sheet, rowIndex, 7 + i, s.money);
            if (amounts[i] != null) c.setCellValue(amounts[i].doubleValue());
        }
    }
    static void writeChecks(Sheet sheet, Period period, int count, int lines, int totalRow, Styles s) {
        banner(sheet, "CONTRÔLE DU GRAND LIVRE", 1, s.title);
        title(sheet, 1, "Période " + period.code() + " · bulletins validés et clôturés uniquement", s.subtitle);
        sheet.setColumnWidth(0, 10000); sheet.setColumnWidth(1, 6500); sheet.setColumnWidth(2, 6500);
        cell(sheet, 3, 0, s.header).setCellValue("Indicateur");
        cell(sheet, 3, 1, s.header).setCellValue("Valeur");
        String[] labels = {"Nombre de bulletins", "Nombre de lignes de rubrique", "Salaire brut total", "Net imposable total", "IRSA total", "Net à payer total", "Cotisations patronales totales"};
        int[] cols = {-1, -1, 13, 16, 17, 20, 21};
        for (int i = 0; i < labels.length; i++) {
            cell(sheet, 4 + i, 0, s.text).setCellValue(labels[i]);
            Cell value = cell(sheet, 4 + i, 1, s.money);
            if (i == 0) value.setCellValue(count);
            else if (i == 1) value.setCellValue(lines);
            else value.setCellFormula("'Grand livre'!" + org.apache.poi.ss.util.CellReference.convertNumToColString(cols[i]) + (totalRow + 1));
        }
        cell(sheet, 12, 0, s.subtitle).setCellValue("Source : bulletins enregistrés et lignes de rubriques de la période.");
        cell(sheet, 13, 0, s.subtitle).setCellValue("Les lignes patronales et informatives figurent dans le détail ; elles ne sont pas ajoutées au net salarié.");
    }
    private static BigDecimal amount(List<BulletinPaieLigne> lines, String code) { return sum(lines, l -> code.equals(code(l))); }
    private static BigDecimal quantity(List<BulletinPaieLigne> lines, String code) {
        return lines.stream().filter(l -> code.equals(code(l))).map(l -> zero(l.getQuantite())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private static BigDecimal sum(List<BulletinPaieLigne> lines, java.util.function.Predicate<BulletinPaieLigne> match) {
        return lines.stream().filter(match).map(l -> zero(l.getMontantSalarial())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private static String code(BulletinPaieLigne l) { return n(l.getCodeRubrique()).toUpperCase(Locale.ROOT); }
    private static BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private static String n(String value) { return value == null ? "" : value; }
    private static Cell cell(Sheet sheet, int rowIndex, int column, CellStyle style) {
        Row row = sheet.getRow(rowIndex); if (row == null) row = sheet.createRow(rowIndex);
        Cell cell = row.createCell(column); cell.setCellStyle(style); return cell;
    }
    private static void title(Sheet sheet, int row, String value, CellStyle style) { cell(sheet, row, 0, style).setCellValue(value); }
    private static void banner(Sheet sheet, String value, int lastColumn, CellStyle style) {
        for (int col = 0; col <= lastColumn; col++) cell(sheet, 0, col, style);
        sheet.getRow(0).getCell(0).setCellValue(value);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, lastColumn));
        sheet.getRow(0).setHeightInPoints(36);
    }
    private static void header(Sheet sheet, int row, String[] columns, CellStyle style) {
        for (int i = 0; i < columns.length; i++) cell(sheet, row, i, style).setCellValue(columns[i]);
        sheet.getRow(row).setHeightInPoints(32);
    }
    static class Styles {
        final CellStyle title, subtitle, header, text, money, totalLabel, totalMoney;
        Styles(XSSFWorkbook book) {
            byte[] navy = new byte[]{7, 50, 74};               // #07324A --color-brand-900
            byte[] totalBg = new byte[]{(byte)225, (byte)237, (byte)242}; // #E1EDF2 --color-brand-100
            title = make(book, 16, true, navy, false);
            subtitle = make(book, 10, false, null, false);
            header = make(book, 10, true, navy, false);
            text = make(book, 10, false, null, true);
            money = make(book, 10, false, null, true);
            money.setDataFormat(book.createDataFormat().getFormat("#,##0.00"));
            money.setAlignment(HorizontalAlignment.RIGHT);
            totalLabel = make(book, 11, true, totalBg, true);
            totalMoney = make(book, 11, true, totalBg, true);
            totalMoney.setDataFormat(book.createDataFormat().getFormat("#,##0.00"));
            totalMoney.setAlignment(HorizontalAlignment.RIGHT);
        }
        private static CellStyle make(XSSFWorkbook book, int size, boolean bold, byte[] background, boolean border) {
            XSSFCellStyle style = book.createCellStyle();
            Font font = book.createFont(); font.setFontHeightInPoints((short) size); font.setBold(bold);
            font.setColor(background != null && background[0] == 7 ? IndexedColors.WHITE.getIndex() : IndexedColors.BLACK.getIndex());
            style.setFont(font); style.setVerticalAlignment(VerticalAlignment.CENTER);
            if (background != null) { style.setFillForegroundColor(new XSSFColor(background, null)); style.setFillPattern(FillPatternType.SOLID_FOREGROUND); }
            if (border) {
                style.setBorderBottom(BorderStyle.THIN); style.setBorderTop(BorderStyle.THIN);
                style.setBorderLeft(BorderStyle.THIN); style.setBorderRight(BorderStyle.THIN);
                style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
                style.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
                style.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
                style.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            }
            return style;
        }
    }
}
