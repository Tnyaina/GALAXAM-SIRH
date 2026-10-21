package mg.galaxam.sirh.paie.application.export;

import mg.galaxam.sirh.paie.domain.dto.BulletinPaieComplet;
import mg.galaxam.sirh.paie.persistence.BulletinPaie;
import mg.galaxam.sirh.paie.persistence.BulletinPaieLigne;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.text.Normalizer;

@Service
public class BulletinDocumentService {
    private static final float LEFT = 42;
    private static final float RIGHT = 553;
    private static final org.apache.pdfbox.pdmodel.font.PDFont REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final org.apache.pdfbox.pdmodel.font.PDFont BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    // Design tokens Galaxam Navy & Neutres Gris Bleutés (index.css)
    private static final int BRAND_NAVY = 0x07324A;   // --color-brand-900 (#07324A)
    private static final int BRAND_ACCENT = 0x247B9F; // --color-brand-500 (#247B9F)
    private static final int BRAND_PALE = 0xF2F7F9;   // --color-brand-50 (#F2F7F9)
    private static final int INK_TEXT = 0x18242D;     // --color-ink-900 (#18242D)
    private static final int INK_MUTED = 0x62717A;    // --color-ink-500 (#62717A)
    private static final int INK_BORDER = 0xD5DDE1;   // --color-ink-200 (#D5DDE1)
    private static final int ROW_ALT = 0xF7F9FA;      // --color-ink-50 (#F7F9FA)

    public record Context(String periode, String dates, String entreprise, String adresse, String nif, String stat) {}
    private record Rubrique(String libelle, BigDecimal base, BigDecimal taux, BigDecimal gain, BigDecimal retenue) {}

    public Context context(BulletinPaieComplet complet, Connection connection) throws Exception {
        String sql = "SELECT code, date_debut, date_fin FROM periode_paie WHERE id_periode_paie=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, complet.getBulletin().getIdPeriodePaie());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Période de paie introuvable");
                var start = rs.getDate("date_debut").toLocalDate();
                var end = rs.getDate("date_fin").toLocalDate();
                Integer bulletinId = complet.getBulletin().getIdBulletin();
                var snapshot = bulletinId != null
                        ? new PayrollEmployerSnapshotService().find(bulletinId, connection)
                        : null;
                var employer = snapshot == null
                        ? new mg.galaxam.sirh.contrat.application.EmployerIdentityContextResolver()
                                .resolve(complet.getBulletin().getIdEmploye(), start, end, connection)
                        : null;
                DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                String dates = start.format(dateFormat) + " au " + end.format(dateFormat);
                return new Context(n(rs.getString("code")), dates,
                        n(snapshot != null ? snapshot.name() : employer.name()),
                        n(snapshot != null ? snapshot.address() : employer.address()),
                        n(snapshot != null ? snapshot.nif() : employer.nif()),
                        n(snapshot != null ? snapshot.stat() : employer.stat()));
            }
        }
    }

    private static List<Rubrique> rubriques(BulletinPaieComplet complet) {
        List<Rubrique> result = new ArrayList<>();
        if (complet.getLignes() == null) return result;
        for (BulletinPaieLigne l : complet.getLignes()) {
            if ("PATRONAL".equals(l.getSens())) continue;
            BigDecimal taux = l.getTauxSalarial();
            // Les rubriques non proportionnelles utilisent parfois ce champ pour une valeur monétaire.
            if (taux != null && taux.abs().compareTo(BigDecimal.valueOf(100)) > 0) taux = null;
            String libelle = n(l.getLibelleRubrique());
            if ("GAIN_NET".equals(l.getEtapeCalcul())) libelle = "Ajout au net : " + libelle;
            result.add(new Rubrique(libelle,
                    l.getBaseCalcul() != null ? l.getBaseCalcul() : l.getQuantite(), taux,
                    "GAIN".equals(l.getSens()) ? l.getMontantSalarial() : null,
                    "RETENUE".equals(l.getSens()) ? l.getMontantSalarial() : null));
        }
        return result;
    }

    public byte[] excel(BulletinPaieComplet complet, Context context) throws Exception {
        BulletinPaie b = complet.getBulletin();
        List<Rubrique> rubriques = rubriques(complet);
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = book.createSheet("Bulletin de paie");
            sheet.setDisplayGridlines(false);
            int[] widths = {10500, 4800, 3600, 5400, 5400};
            for (int i = 0; i < widths.length; i++) sheet.setColumnWidth(i, widths[i]);
            CellStyle title = style(book, true, (short) 16, 0xFFFFFF, true, false);
            CellStyle label = style(book, true, (short) 10, INK_TEXT, false, false);
            CellStyle header = style(book, true, (short) 10, 0xFFFFFF, true, false);
            CellStyle body = style(book, false, (short) 10, INK_TEXT, false, false);
            CellStyle tableBody = style(book, false, (short) 10, INK_TEXT, false, false);
            CellStyle tableNumber = style(book, false, (short) 10, INK_TEXT, false, true);
            borders(tableBody);
            borders(tableNumber);
            borders(header);
            CellStyle total = style(book, true, (short) 11, INK_TEXT, true, true);
            CellStyle net = style(book, true, (short) 14, 0xFFFFFF, true, true);
            CellStyle netLabel = style(book, true, (short) 14, 0xFFFFFF, true, false);
            int r = 0;
            cell(sheet, r++, 0, "BULLETIN DE PAIE", title);
            for (int col = 1; col < 5; col++) cell(sheet, 0, col, "", title);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 4));
            sheet.getRow(0).setHeightInPoints(36);
            cell(sheet, r++, 0, context.entreprise(), label);
            cell(sheet, r++, 0, context.adresse(), body);
            cell(sheet, r++, 0, "NIF : " + context.nif() + "   STAT : " + context.stat(), body);
            r++;
            cell(sheet, r, 0, "Période : " + context.periode(), label);
            cell(sheet, r++, 3, context.dates(), body);
            cell(sheet, r, 0, "Référence : " + n(b.getReferenceBulletin()), body);
            cell(sheet, r++, 3, "Statut : " + n(b.getStatut()), body);
            r++;
            cell(sheet, r, 0, "SALARIÉ", label);
            cell(sheet, r++, 3, "Matricule : " + n(b.getMatricule()), body);
            cell(sheet, r, 0, n(b.getNomPrenom()), label);
            cell(sheet, r++, 3, n(b.getIntitulePoste()), body);
            cell(sheet, r, 0, "Site : " + n(b.getNomSite()), body);
            cell(sheet, r++, 3, "CNaPS : " + n(b.getNumeroCnaps()), body);
            r++;
            String[] columns = {"RUBRIQUE", "BASE / QTÉ", "TAUX %", "GAIN (Ar)", "RETENUE (Ar)"};
            int headerRow = r;
            for (int i = 0; i < columns.length; i++) cell(sheet, r, i, columns[i], header);
            r++;
            for (Rubrique l : rubriques) {
                cell(sheet, r, 0, l.libelle(), tableBody);
                numeric(sheet, r, 1, l.base(), tableNumber);
                numeric(sheet, r, 2, l.taux(), tableNumber);
                numeric(sheet, r, 3, l.gain(), tableNumber);
                numeric(sheet, r++, 4, l.retenue(), tableNumber);
            }
            int lastDetailRow = r - 1;
            r++;
            cell(sheet, r, 0, "Gains bruts", label); numeric(sheet, r++, 3, b.getTotalGainsBruts(), total);
            cell(sheet, r, 0, "Cotisations salariales", label); numeric(sheet, r++, 4, b.getTotalCotisationsSalariales(), total);
            cell(sheet, r, 0, "Net imposable", label); numeric(sheet, r++, 3, b.getRevenuNetImposable(), total);
            cell(sheet, r, 0, "IRSA", label); numeric(sheet, r++, 4, impot(b), total);
            cell(sheet, r, 0, "Ajouts au net", label); numeric(sheet, r++, 3, b.getTotalIndemnitesNonImposables(), total);
            cell(sheet, r, 0, "Avances et acomptes", label); numeric(sheet, r++, 4, b.getTotalRetenuesAvancesAcomptes(), total);
            r++;
            cell(sheet, r, 0, "NET À PAYER", netLabel);
            for (int col = 1; col < 5; col++) cell(sheet, r, col, "", net);
            numeric(sheet, r++, 3, b.getNetAPayer(), net);
            sheet.getRow(r - 1).setHeightInPoints(34);
            sheet.createFreezePane(1, headerRow + 1);
            if (lastDetailRow > headerRow) sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(headerRow, lastDetailRow, 0, 4));
            sheet.setRepeatingRows(new org.apache.poi.ss.util.CellRangeAddress(headerRow, headerRow, 0, columns.length - 1));
            sheet.getPrintSetup().setPaperSize(PrintSetup.A4_PAPERSIZE);
            sheet.setFitToPage(true);
            sheet.getPrintSetup().setFitWidth((short) 1);
            sheet.getPrintSetup().setFitHeight((short) 0);
            sheet.setAutobreaks(true);
            book.write(output);
            return output.toByteArray();
        }
    }

    public byte[] pdf(BulletinPaieComplet complet, Context context) throws Exception {
        BulletinPaie b = complet.getBulletin();
        List<Rubrique> rubriques = rubriques(complet);
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PageWriter page = new PageWriter(doc);
            page.heading(context, b);
            page.tableHeader();
            for (Rubrique l : rubriques) {
                if (page.y - page.rubriqueHeight(l) < 145) { page.close(); page = new PageWriter(doc); page.continuation(context, b); page.tableHeader(); }
                page.rubrique(l);
            }
            if (page.y < 205) { page.close(); page = new PageWriter(doc); page.continuation(context, b); }
            page.totals(b);
            page.close();
            doc.save(output);
            return output.toByteArray();
        }
    }

    private static class PageWriter {
        final PDPageContentStream stream;
        float y = 805;
        PageWriter(PDDocument doc) throws Exception {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            stream = new PDPageContentStream(doc, page);
        }
        void heading(Context c, BulletinPaie b) throws Exception {
            fill(LEFT, 817, RIGHT - LEFT, 52, BRAND_NAVY);
            text(LEFT + 14, 789, c.entreprise().isBlank() ? "BULLETIN DE PAIE" : c.entreprise(), 15, true, 0xFFFFFF);
            text(RIGHT - 145, 789, "BULLETIN DE PAIE", 10, true, 0xFFFFFF);
            text(LEFT + 14, 774, c.adresse(), 8, false, 0xFFFFFF);
            y = 748;
            text(LEFT, y, "PÉRIODE  " + c.periode() + "  ·  " + c.dates(), 9, true, BRAND_NAVY);
            right(RIGHT, y, "Réf. " + n(b.getReferenceBulletin()), 9, false, INK_MUTED);
            y -= 28;
            fill(LEFT, y + 12, RIGHT - LEFT, 73, BRAND_PALE);
            text(LEFT + 10, y - 5, "SALARIÉ", 8, true, BRAND_ACCENT);
            text(LEFT + 10, y - 23, n(b.getNomPrenom()), 12, true, BRAND_NAVY);
            text(LEFT + 10, y - 43, "Matricule " + n(b.getMatricule()) + "  ·  " + n(b.getIntitulePoste()), 9, false, INK_TEXT);
            text(LEFT + 10, y - 57, "Site : " + n(b.getNomSite()) + "   |   CNaPS : " + n(b.getNumeroCnaps()), 8, false, INK_MUTED);
            y -= 88;
        }
        void continuation(Context c, BulletinPaie b) throws Exception {
            fill(LEFT, 813, RIGHT - LEFT, 38, BRAND_NAVY);
            text(LEFT + 12, 789, "BULLETIN DE PAIE · SUITE", 12, true, 0xFFFFFF);
            text(LEFT, 758, n(b.getNomPrenom()) + "   |   " + c.periode() + "   |   " + n(b.getReferenceBulletin()), 9, false, INK_TEXT);
            y = 737;
        }
        void tableHeader() throws Exception {
            fill(LEFT, y + 13, RIGHT - LEFT, 22, BRAND_NAVY);
            text(LEFT + 8, y + 3, "RUBRIQUE", 8, true, 0xFFFFFF);
            right(342, y + 3, "BASE / QTÉ", 8, true, 0xFFFFFF);
            right(397, y + 3, "TAUX", 8, true, 0xFFFFFF);
            right(475, y + 3, "GAIN", 8, true, 0xFFFFFF);
            right(RIGHT - 8, y + 3, "RETENUE", 8, true, 0xFFFFFF);
            y -= 26;
        }
        void rubrique(Rubrique l) throws Exception {
            float rowY = y;
            List<String> labelLines = wrapLabel(l.libelle());
            float height = 21 + (labelLines.size() - 1) * 10;
            if (((int) (rowY / 21)) % 2 == 0) fill(LEFT, rowY + 15, RIGHT - LEFT, height, ROW_ALT);
            for (int i = 0; i < labelLines.size(); i++) text(LEFT + 8, rowY + 1 - i * 10, labelLines.get(i), 8, false, INK_TEXT);
            right(342, rowY + 1, fmt(l.base()), 8, false, INK_TEXT);
            right(397, rowY + 1, l.taux() == null ? "" : fmt(l.taux()) + " %", 8, false, INK_TEXT);
            right(475, rowY + 1, fmt(l.gain()), 8, false, INK_TEXT);
            right(RIGHT - 8, rowY + 1, fmt(l.retenue()), 8, false, INK_TEXT);
            y -= height;
        }
        float rubriqueHeight(Rubrique l) throws Exception { return 21 + (wrapLabel(l.libelle()).size() - 1) * 10; }
        List<String> wrapLabel(String value) throws Exception {
            List<String> lines = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (String word : safe(value).split("\\s+")) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (!current.isEmpty() && REGULAR.getStringWidth(candidate) / 1000 * 8 > 250) {
                    lines.add(current.toString());
                    current.setLength(0);
                    current.append(word);
                } else {
                    current.setLength(0);
                    current.append(candidate);
                }
            }
            if (!current.isEmpty()) lines.add(current.toString());
            return lines.isEmpty() ? List.of("") : lines;
        }
        void totals(BulletinPaie b) throws Exception {
            y -= 14;
            rule(y + 16);
            y -= 9;
            summary("Gains bruts", b.getTotalGainsBruts());
            summary("Cotisations salariales", b.getTotalCotisationsSalariales());
            summary("Net imposable", b.getRevenuNetImposable());
            summary("IRSA", impot(b));
            summary("Ajouts au net", b.getTotalIndemnitesNonImposables());
            summary("Avances et acomptes", b.getTotalRetenuesAvancesAcomptes());
            y -= 7;
            fill(LEFT, y + 12, RIGHT - LEFT, 36, BRAND_NAVY);
            text(LEFT + 12, y - 10, "NET À PAYER", 12, true, 0xFFFFFF);
            right(RIGHT - 12, y - 10, fmt(b.getNetAPayer()) + " Ar", 14, true, 0xFFFFFF);
            y -= 54;
        }
        void summary(String label, BigDecimal value) throws Exception {
            text(LEFT + 8, y, label, 9, false, INK_TEXT);
            right(RIGHT - 8, y, fmt(value) + " Ar", 9, true, BRAND_NAVY);
            y -= 18;
        }
        void fill(float x, float top, float width, float height, int color) throws Exception {
            color(color); stream.addRect(x, top - height, width, height); stream.fill();
        }
        void rule(float at) throws Exception {
            color(INK_BORDER); stream.moveTo(LEFT, at); stream.lineTo(RIGHT, at); stream.stroke();
        }
        void color(int rgb) throws Exception { stream.setNonStrokingColor(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f); }
        void text(float x, float baseline, String value, int size, boolean bold, int rgb) throws Exception {
            String safe = safe(value);
            color(rgb); stream.beginText(); stream.setFont(bold ? BOLD : REGULAR, size);
            stream.newLineAtOffset(x, baseline); stream.showText(safe); stream.endText();
        }
        void right(float x, float baseline, String value, int size, boolean bold, int rgb) throws Exception {
            String safe = safe(value);
            float width = (bold ? BOLD : REGULAR).getStringWidth(safe) / 1000 * size;
            text(x - width, baseline, safe, size, bold, rgb);
        }
        void close() throws Exception { stream.close(); }
    }

    private static BigDecimal impot(BulletinPaie b) {
        return b.getRetenueIsi5() != null && b.getRetenueIsi5().signum() > 0 ? b.getRetenueIsi5() : b.getIrsaNet();
    }
    private static String fmt(BigDecimal value) {
        if (value == null) return "";
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.FRANCE);
        symbols.setGroupingSeparator(' ');
        return new DecimalFormat("#,##0.##", symbols).format(value);
    }
    private static String safe(String value) {
        return n(value).replace('\u202f', ' ').replace('\u00a0', ' ').replace('–', '-').replace('—', '-').replace('…', '.')
                .replace('·', '|').replaceAll("[^\\x20-\\x7E\\xA0-\\xFF]", "?");
    }
    private static String n(String value) { return value == null ? "" : value; }
    public static String buildExportFilename(BulletinPaie b, Context context, String extension) {
        String[] parts = n(b.getNomPrenom()).trim().split("\\s+");
        String nom = parts.length == 0 || parts[0].isBlank() ? "Employe" : parts[0];
        String initiale = parts.length > 1 ? parts[1].substring(0, 1) : "P";
        String matricule = n(b.getMatricule()).isBlank() ? "0000" : b.getMatricule();
        return "Bulletin_" + filenamePart(nom) + "_" + filenamePart(initiale) + "_"
                + filenamePart(matricule) + "_" + filenamePart(context.periode()) + "." + extension;
    }
    private static String filenamePart(String value) {
        String ascii = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("[^\\p{ASCII}]", "");
        return ascii.replaceAll("[^a-zA-Z0-9_-]", "_").replaceAll("_+", "_");
    }
    private static CellStyle style(XSSFWorkbook book, boolean bold, short fontSize, int color, boolean filled, boolean numeric) {
        XSSFCellStyle style = book.createCellStyle();
        Font font = book.createFont(); font.setBold(bold); font.setFontHeightInPoints(fontSize);
        font.setColor(color == 0xFFFFFF ? IndexedColors.WHITE.getIndex() : IndexedColors.BLACK.getIndex()); style.setFont(font);
        if (filled) {
            byte[] rgb = color == 0xFFFFFF ? new byte[]{7, 50, 74} : new byte[]{(byte)242, (byte)247, (byte)249};
            style.setFillForegroundColor(new XSSFColor(rgb, null));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        if (numeric) { style.setAlignment(HorizontalAlignment.RIGHT); style.setDataFormat(book.createDataFormat().getFormat("#,##0.00")); }
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }
    private static void borders(CellStyle style) {
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
    }
    private static void cell(Sheet sheet, int row, int col, String value, CellStyle style) {
        Row r = sheet.getRow(row); if (r == null) r = sheet.createRow(row);
        Cell c = r.createCell(col); c.setCellValue(value); c.setCellStyle(style);
        r.setHeightInPoints(Math.max(r.getHeightInPoints(), 22));
    }
    private static void numeric(Sheet sheet, int row, int col, BigDecimal value, CellStyle style) {
        Row r = sheet.getRow(row); if (r == null) r = sheet.createRow(row);
        Cell c = r.createCell(col); if (value != null) c.setCellValue(value.doubleValue()); c.setCellStyle(style);
        r.setHeightInPoints(Math.max(r.getHeightInPoints(), 22));
    }
}
