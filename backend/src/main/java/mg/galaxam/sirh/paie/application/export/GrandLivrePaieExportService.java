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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Grand livre mensuel : un état nominatif, chaque ligne de bulletin, puis des contrôles de lecture. */
@Service
public class GrandLivrePaieExportService {
    private static final String[] SUMMARY_COLUMNS = {
            "Matricule", "Nom & prénom", "Filiale", "Site", "Poste", "Régime",
            "Salaire de base", "Absence (h)", "Retenue absence", "Congés (j)", "Indemnité congé",
            "Heures sup.", "Indemnités / primes", "Salaire brut", "CNaPS salarié", "Santé salarié",
            "Net imposable", "IRSA / ISI", "Avance / acompte", "Prêt", "Autres retenues",
            "Net à payer", "Cotisations patronales", "Coût employeur"
    };
    private static final String[] DETAIL_COLUMNS = {
            "Référence bulletin", "Matricule", "Nom & prénom", "Code rubrique", "Libellé rubrique",
            "Sens", "Catégorie", "Base calcul", "Quantité", "Taux salarié %", "Taux patronal %",
            "Montant salarié", "Montant patronal"
    };
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE);

    public record Result(byte[] bytes, String periodCode, int bulletinCount, int detailCount) {}
    private record Period(String code, LocalDate start, LocalDate end) {}

    public Result export(Integer periodId, Connection connection) throws Exception {
        Period period = loadPeriod(periodId, connection);
        List<BulletinPaie> bulletins = BulletinPaie.listerParPeriode(periodId, connection);
        if (bulletins.isEmpty()) throw new IllegalStateException("Aucun bulletin pour cette période.");
        if (bulletins.stream().anyMatch(b -> !"VALIDE".equals(b.getStatut()) && !"CLOTURE".equals(b.getStatut()))) {
            throw new IllegalStateException("Tous les bulletins doivent être validés avant l'export du grand livre.");
        }
        Map<Integer, String> filialByBulletin = loadFiliales(periodId, connection);
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);
            Sheet summary = workbook.createSheet("Grand livre");
            Sheet detail = workbook.createSheet("Détail rubriques");
            Sheet checks = workbook.createSheet("Contrôle");
            for (Sheet sheet : List.of(summary, detail, checks)) sheet.setDisplayGridlines(false);
            title(summary, 0, "GRAND LIVRE DE PAIE", styles.title);
            title(summary, 1, "État mensuel nominatif · période " + period.code(), styles.subtitle);
            title(summary, 3, "Du " + period.start().format(DATE) + " au " + period.end().format(DATE)
                    + "   |   " + bulletins.size() + " bulletins validés ou clôturés", styles.subtitle);
            header(summary, 7, SUMMARY_COLUMNS, styles.header);
            summary.createFreezePane(6, 8);
            for (int col = 0; col < SUMMARY_COLUMNS.length; col++) summary.setColumnWidth(col, col == 1 ? 9500 : col < 6 ? 4800 : 5100);
            summary.setColumnWidth(0, 3600);
            summary.setRepeatingRows(new CellRangeAddress(7, 7, 0, SUMMARY_COLUMNS.length - 1));

            title(detail, 0, "DÉTAIL DES RUBRIQUES DE PAIE", styles.title);
            title(detail, 1, "Toutes les lignes enregistrées des bulletins de la période " + period.code(), styles.subtitle);
            header(detail, 2, DETAIL_COLUMNS, styles.header);
            detail.createFreezePane(3, 3);
            for (int col = 0; col < DETAIL_COLUMNS.length; col++) detail.setColumnWidth(col, col == 4 ? 11500 : col == 2 ? 9500 : 5000);
            detail.setRepeatingRows(new CellRangeAddress(2, 2, 0, DETAIL_COLUMNS.length - 1));
            int detailRow = 3;
            int summaryRow = 8;
            for (BulletinPaie b : bulletins) {
                List<BulletinPaieLigne> lines = BulletinPaieLigne.listerParBulletin(b.getIdBulletin(), connection);
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
        Map<Integer, String> result = new HashMap<>();
        String sql = "SELECT b.id_bulletin, f.raison_sociale FROM bulletin_paie b " +
                "JOIN periode_paie p ON p.id_periode_paie = b.id_periode_paie " +
                "LEFT JOIN LATERAL (SELECT rf.raison_sociale FROM contrat_travail ct " +
                "JOIN ref_filiale rf ON rf.id_filiale = ct.id_filiale " +
                "WHERE ct.id_employe = b.id_employe AND ct.date_debut <= p.date_fin " +
                "ORDER BY ct.date_debut DESC, ct.id_contrat DESC LIMIT 1) f ON TRUE " +
                "WHERE b.id_periode_paie = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, periodId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.put(rs.getInt(1), rs.getString(2));
            }
        }
        return result;
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
        BigDecimal congéGain = sum(lines, l -> "GAIN".equals(l.getSens()) && code(l).contains("CONGE"));
        BigDecimal hsGain = sum(lines, l -> "GAIN".equals(l.getSens()) && code(l).contains("HS"));
        BigDecimal otherGains = sum(lines, l -> "GAIN".equals(l.getSens()) && !"SALAIRE_BASE".equals(code(l)) && !code(l).contains("CONGE") && !code(l).contains("HS"));
        BigDecimal cnaps = sum(lines, l -> "RETENUE".equals(l.getSens()) && code(l).contains("CNAPS"));
        BigDecimal health = sum(lines, l -> "RETENUE".equals(l.getSens()) && (code(l).contains("OSTIE") || code(l).contains("OMSI") || code(l).contains("SANTE")));
        BigDecimal advances = sum(lines, l -> "RETENUE".equals(l.getSens()) && (code(l).contains("AVANCE") || code(l).contains("ACOMPTE")));
        BigDecimal loans = sum(lines, l -> "RETENUE".equals(l.getSens()) && code(l).contains("PRET"));
        BigDecimal others = sum(lines, l -> "RETENUE".equals(l.getSens()) && !code(l).contains("ABSENCE") && !code(l).contains("CONGE")
                && !code(l).contains("CNAPS") && !code(l).contains("OSTIE") && !code(l).contains("OMSI") && !code(l).contains("SANTE")
                && !code(l).contains("IRSA") && !code(l).contains("ISI") && !code(l).contains("AVANCE") && !code(l).contains("ACOMPTE") && !code(l).contains("PRET"));
        BigDecimal[] values = {b.getSalaireBase(), absenceHours, amount(lines, "RETENUE_ABSENCE"), b.getJoursConges(), congéGain,
                zero(b.getHeuresHs30()).add(zero(b.getHeuresHs50())), otherGains.add(hsGain), b.getTotalGainsBruts(), cnaps, health,
                b.getRevenuNetImposable(), zero(b.getIrsaNet()).add(zero(b.getRetenueIsi5())), advances, loans, others,
                b.getNetAPayer(), b.getTotalCotisationsPatronales(), b.getCoutGlobalEmployeur()};
        return values;
    }
    private static void writeDetail(Sheet sheet, int rowIndex, BulletinPaie b, BulletinPaieLigne l, Styles s) {
        String[] fields = {b.getReferenceBulletin(), b.getMatricule(), b.getNomPrenom(), l.getCodeRubrique(),
                l.getLibelleRubrique(), l.getSens(), l.getCategorie()};
        for (int col = 0; col < fields.length; col++) cell(sheet, rowIndex, col, s.text).setCellValue(n(fields[col]));
        BigDecimal[] amounts = {l.getBaseCalcul(), l.getQuantite(), l.getTauxSalarial(), l.getTauxPatronal(), l.getMontantSalarial(), l.getMontantPatronal()};
        for (int i = 0; i < amounts.length; i++) {
            Cell c = cell(sheet, rowIndex, 7 + i, s.money);
            if (amounts[i] != null) c.setCellValue(amounts[i].doubleValue());
        }
    }
    private static void writeChecks(Sheet sheet, Period period, int count, int lines, int totalRow, Styles s) {
        title(sheet, 0, "CONTRÔLE DU GRAND LIVRE", s.title);
        title(sheet, 1, "Période " + period.code() + " · bulletins validés et clôturés uniquement", s.subtitle);
        sheet.setColumnWidth(0, 10000); sheet.setColumnWidth(1, 6500); sheet.setColumnWidth(2, 6500);
        cell(sheet, 3, 0, s.header).setCellValue("Indicateur");
        cell(sheet, 3, 1, s.header).setCellValue("Valeur");
        String[] labels = {"Nombre de bulletins", "Nombre de lignes de rubrique", "Salaire brut total", "Net imposable total", "IRSA / ISI total", "Net à payer total", "Cotisations patronales totales"};
        int[] cols = {-1, -1, 13, 16, 17, 21, 22};
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
    private static void header(Sheet sheet, int row, String[] columns, CellStyle style) {
        for (int i = 0; i < columns.length; i++) cell(sheet, row, i, style).setCellValue(columns[i]);
        sheet.getRow(row).setHeightInPoints(32);
    }
    private static class Styles {
        final CellStyle title, subtitle, header, text, money, totalLabel, totalMoney;
        Styles(XSSFWorkbook book) {
            title = make(book, 16, true, null, false);
            subtitle = make(book, 10, false, null, false);
            header = make(book, 10, true, new byte[]{32, 51, 40}, false);
            text = make(book, 10, false, null, true);
            money = make(book, 10, false, null, true);
            money.setDataFormat(book.createDataFormat().getFormat("#,##0.00"));
            money.setAlignment(HorizontalAlignment.RIGHT);
            totalLabel = make(book, 11, true, new byte[]{(byte)239, (byte)243, (byte)239}, true);
            totalMoney = make(book, 11, true, new byte[]{(byte)239, (byte)243, (byte)239}, true);
            totalMoney.setDataFormat(book.createDataFormat().getFormat("#,##0.00"));
            totalMoney.setAlignment(HorizontalAlignment.RIGHT);
        }
        private static CellStyle make(XSSFWorkbook book, int size, boolean bold, byte[] background, boolean border) {
            XSSFCellStyle style = book.createCellStyle();
            Font font = book.createFont(); font.setFontHeightInPoints((short) size); font.setBold(bold);
            font.setColor(background != null && background[0] == 32 ? IndexedColors.WHITE.getIndex() : IndexedColors.BLACK.getIndex());
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
