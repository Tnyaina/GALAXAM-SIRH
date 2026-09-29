package mg.galaxam.sirh.pointage.application;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

/** Fiche salarié paginée : une ligne par jour et un bilan lisible en fin de document. */
@Service
public class PointagePdfExportService {
    private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final Color GREEN = new Color(36, 67, 46);
    private static final Color PALE = new Color(233, 239, 234);
    private static final float[] X = {30, 88, 123, 220, 266, 312, 358, 404, 506, 566, 812};
    private static final String[] HEADERS = {"Date", "Jour", "Statut", "Entrée", "Pause", "Retour", "Sortie", "Heures", "Retard", "Observation"};

    public byte[] generer(PointageDocumentData data) throws IOException {
        try (var pdf = new PDDocument(); var output = new ByteArrayOutputStream()) {
            var days = PointageDocumentData.days(data.fiche());
            int perPage = 34;
            int pages = Math.max(1, (days.size() + perPage - 1) / perPage);
            for (int pageIndex = 0; pageIndex < pages; pageIndex++) {
                PDPage page = new PDPage(new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()));
                pdf.addPage(page);
                try (var stream = new PDPageContentStream(pdf, page)) {
                    header(stream, data, pageIndex + 1, pages);
                    tableHeader(stream);
                    int first = pageIndex * perPage;
                    int end = Math.min(first + perPage, days.size());
                    for (int i = first; i < end; i++) {
                        var d = days.get(i);
                        float y = 459 - (i - first) * 11.6f;
                        if ((i - first) % 2 == 0) fill(stream, 30, y - 3, 782, 11.6f, new Color(247, 248, 246));
                        String[] fields = {PointageDocumentData.date(d.getDateComplete()), weekday(d.getJour()),
                                PointageExcelExportService.status(d.getStatut()), value(d.getEntree()), value(d.getSortiePause()),
                                value(d.getRetourPause()), value(d.getSortie()), PointageDocumentData.dualHours(d.getTotalHeuresDecimal()),
                                d.getMinutesRetard() == null || d.getMinutesRetard() == 0 ? "-" : d.getMinutesRetard() + " min",
                                value(d.getObservation())};
                        for (int c = 0; c < fields.length; c++) draw(stream, X[c] + 3, y, fields[c], REGULAR, 7.2f, Color.DARK_GRAY, X[c + 1] - X[c] - 6);
                    }
                    draw(stream, 30, 22, "Fiche de présence · " + (data.versionSnapshot() == null ? "Données provisoires" : "Période validée")
                            + " · Édité le " + data.edition(), REGULAR, 8, Color.GRAY, 700);
                }
            }
            detailPages(pdf, data);
            pdf.save(output);
            return output.toByteArray();
        }
    }

    private static void header(PDPageContentStream stream, PointageDocumentData data, int page, int pages) throws IOException {
        var f = data.fiche();
        fill(stream, 30, 535, 782, 35, GREEN);
        draw(stream, 42, 548, "FICHE DE PRÉSENCE", BOLD, 15, Color.WHITE, 500);
        draw(stream, 742, 548, page + " / " + pages, REGULAR, 9, Color.WHITE, 55);
        draw(stream, 30, 519, value(f.getNomComplet()) + "  ·  Matricule " + value(f.getMatricule()), BOLD, 11, GREEN, 600);
        draw(stream, 30, 503, "Période : " + PointageDocumentData.date(f.getDateDebut()) + " au " + PointageDocumentData.date(f.getDateFin()), REGULAR, 9, Color.DARK_GRAY, 380);
        draw(stream, 426, 503, "Fonction : " + value(f.getFonction()) + " · Site : " + value(f.getSiteNom()), REGULAR, 9, Color.DARK_GRAY, 380);
    }

    private static void tableHeader(PDPageContentStream stream) throws IOException {
        fill(stream, 30, 470, 782, 18, PALE);
        for (int c = 0; c < HEADERS.length; c++) draw(stream, X[c] + 3, 476, HEADERS[c], BOLD, 7.4f, GREEN, X[c + 1] - X[c] - 6);
    }

    private static void detailPages(PDDocument pdf, PointageDocumentData data) throws IOException {
        PDPage page = new PDPage(new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()));
        pdf.addPage(page);
        PDPageContentStream stream = new PDPageContentStream(pdf, page);
        detailHeader(stream, data, false);
        float y = 478;
        fill(stream, 30, y, 782, 18, PALE);
        draw(stream, 39, y + 6, "SYNTHÈSE HEBDOMADAIRE", BOLD, 9, GREEN, 300);
        y -= 17;
        String[] headings = {"Semaine", "Jours", "Heures faites", "Objectif", "Déficit", "HS 30 %", "HS 50 %", "Nuit", "Dimanche", "Férié"};
        float[] positions = {39, 201, 240, 320, 402, 477, 553, 628, 690, 755};
        for (int i = 0; i < headings.length; i++) draw(stream, positions[i], y, headings[i], BOLD, 7.2f, GREEN, i == 0 ? 155 : 75);
        var weeks = data.fiche().getBilansHebdo();
        if (weeks != null) for (var week : weeks) {
            y -= 25;
            draw(stream, 39, y, "S" + week.getNumeroSemaine() + " · " + PointageDocumentData.date(week.getDebutSemaine())
                    + " - " + PointageDocumentData.date(week.getFinSemaine()), REGULAR, 7.1f, Color.DARK_GRAY, 155);
            draw(stream, 201, y, week.getNbJoursTroncon() + " j", REGULAR, 7.1f, Color.DARK_GRAY, 34);
            double[] values = {week.getTotalHeuresTravaillees(), week.getSeuilHeuresTroncon(), week.getHeuresDeficit(),
                    week.getHeuresHs30(), week.getHeuresHs50(), week.getHeuresNuit(), week.getHeuresDimanche(), week.getHeuresFerie()};
            for (int i = 0; i < values.length; i++) {
                String[] pair = PointageDocumentData.dualHours(values[i]).split(" ", 2);
                float x = positions[i + 2];
                draw(stream, x, y, pair[0], BOLD, 7.2f, Color.DARK_GRAY, 68);
                draw(stream, x, y - 10, pair.length > 1 ? pair[1] : "", REGULAR, 6.5f, Color.GRAY, 68);
            }
        }
        if (weeks == null || weeks.isEmpty()) { y -= 16; draw(stream, 39, y, "Aucune synthèse hebdomadaire disponible", REGULAR, 8, Color.GRAY, 450); }
        y -= 14;
        draw(stream, 39, y, "Déficit et HS après compensation. Nuit, dimanche et férié sont déjà inclus dans les heures faites.", REGULAR, 7.2f, Color.GRAY, 760);
        y -= 31;
        if (!data.appliedAdjustments().isEmpty()) {
            if (y - 17 < 167) {
                stream.close();
                page = new PDPage(new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()));
                pdf.addPage(page);
                stream = new PDPageContentStream(pdf, page);
                detailHeader(stream, data, true);
                y = 483;
            }
            fill(stream, 30, y, 782, 18, PALE);
            draw(stream, 39, y + 6, "AJUSTEMENTS APPLIQUÉS AU SOLDE", BOLD, 9, GREEN, 400);
            y -= 17;
            for (var adjustment : data.appliedAdjustments()) {
                String date = PointageDocumentData.date(adjustment.date());
                String duration = PointageDocumentData.signedDualMinutes(adjustment.minutes());
                String motif = value(adjustment.motif());
                int max = 120;
                for (int offset = 0; offset < motif.length(); offset += max) {
                    if (y < 167) {
                        stream.close();
                        page = new PDPage(new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()));
                        pdf.addPage(page);
                        stream = new PDPageContentStream(pdf, page);
                        detailHeader(stream, data, true);
                        y = 483;
                    }
                    draw(stream, 39, y, offset == 0 ? date : "", REGULAR, 8, Color.DARK_GRAY, 100);
                    draw(stream, 145, y, offset == 0 ? duration : "", BOLD, 8, GREEN, 130);
                    draw(stream, 285, y, motif.substring(offset, Math.min(offset + max, motif.length())), REGULAR, 8, Color.DARK_GRAY, 515);
                    y -= 15;
                }
            }
        }
        stream.close();
    }

    private static void detailHeader(PDPageContentStream stream, PointageDocumentData data, boolean continuation) throws IOException {
        fill(stream, 30, 535, 782, 35, GREEN);
        draw(stream, 42, 548, continuation ? "FICHE DE PRÉSENCE - AJUSTEMENTS (SUITE)" : "FICHE DE PRÉSENCE - DÉTAIL", BOLD, 13, Color.WHITE, 700);
        draw(stream, 30, 519, value(data.fiche().getNomComplet()) + " · " + PointageDocumentData.date(data.fiche().getDateDebut())
                + " au " + PointageDocumentData.date(data.fiche().getDateFin()), REGULAR, 9, Color.DARK_GRAY, 720);
        if (!continuation) summary(stream, data);
        draw(stream, 30, 22, "Fiche de présence · " + (data.versionSnapshot() == null ? "Données provisoires" : "Période validée")
                + " · Édité le " + data.edition(), REGULAR, 8, Color.GRAY, 700);
    }

    private static void summary(PDPageContentStream stream, PointageDocumentData data) throws IOException {
        float y = 91;
        fill(stream, 30, y + 61, 782, 18, PALE);
        draw(stream, 39, y + 67, "RAPPROCHEMENT DU SOLDE", BOLD, 9, GREEN, 220);
        List<BigDecimal> b = data.bilan();
        BigDecimal weeklyHs = add(at(b, 1), at(b, 2));
        String[] labels = {"HS hebdo", "Déficit restant", "Ajustements", "Solde final"};
        BigDecimal[] values = {weeklyHs, negative(at(b, 0)), at(b, 3), at(b, 4)};
        for (int i = 0; i < labels.length; i++) {
            float x = 39 + i * 192;
            draw(stream, x, y + 49, labels[i], BOLD, 7.8f, GREEN, 180);
            draw(stream, x, y + 34, PointageDocumentData.signedDualHours(values[i]), REGULAR, 8.5f, Color.DARK_GRAY, 180);
        }
        draw(stream, 39, y + 17, "Incidence paie : absence " + PointageDocumentData.dualHours(at(b, 5))
                + "   ·   HS 30 % " + PointageDocumentData.dualHours(at(b, 6))
                + "   ·   HS 50 % " + PointageDocumentData.dualHours(at(b, 7)), REGULAR, 8, Color.DARK_GRAY, 760);
        draw(stream, 39, y + 3, "Heures qualifiées : nuit " + PointageDocumentData.dualHours(at(b, 8))
                + "   ·   dimanche " + PointageDocumentData.dualHours(at(b, 9))
                + "   ·   férié " + PointageDocumentData.dualHours(at(b, 10)), REGULAR, 8, Color.DARK_GRAY, 760);
        float allowanceY = y - 18;
        draw(stream, 39, allowanceY, "Indemnités de pointage (montants arrondis à l'ariary)", BOLD, 8.5f, GREEN, 400);
        int count = 0;
        for (var allowance : data.indemnites()) {
            if (allowance.montant() == null || allowance.montant().signum() == 0) continue;
            float x = 39 + (count % 4) * 192;
            float line = allowanceY - 15 - (count / 4) * 13;
            draw(stream, x, line, value(allowance.libelle()) + " : " + PointageDocumentData.money(allowance.montant()), REGULAR, 8.2f, Color.DARK_GRAY, 177);
            count++;
        }
        if (count == 0) draw(stream, 39, allowanceY - 15, "Aucune indemnité", REGULAR, 8.2f, Color.GRAY, 250);
        draw(stream, 39, 34, "Le bulletin de paie reste la référence pour le versement.", REGULAR, 7.6f, Color.GRAY, 680);
    }

    private static BigDecimal at(List<BigDecimal> values, int index) { return values == null || values.size() <= index ? null : values.get(index); }
    private static BigDecimal add(BigDecimal a, BigDecimal b) { return a == null && b == null ? null : (a == null ? BigDecimal.ZERO : a).add(b == null ? BigDecimal.ZERO : b); }
    private static BigDecimal negative(BigDecimal value) { return value == null ? null : value.negate(); }
    private static String number(BigDecimal value) { return value == null ? "-" : PointageDocumentData.number(value); }
    private static String hours(Double value) { return value == null ? "-" : PointageDocumentData.number(BigDecimal.valueOf(value)); }
    private static String hours(double value) { return PointageDocumentData.number(BigDecimal.valueOf(value)); }
    private static String weekday(String value) { return value == null ? "-" : value.replace("..", "."); }
    private static String value(String value) { return value == null || value.isBlank() ? "-" : value; }
    private static void fill(PDPageContentStream stream, float x, float y, float width, float height, Color color) throws IOException {
        stream.setNonStrokingColor(color); stream.addRect(x, y, width, height); stream.fill();
    }
    private static void draw(PDPageContentStream stream, float x, float y, String raw, PDType1Font font,
                             float size, Color color, float maxWidth) throws IOException {
        String value = raw.replace('→', '-').replace('−', '-').replace('’', '\'').replace('·', '-').replace('\n', ' ')
                .replaceAll("[^\\x20-\\x7E\\xA0-\\xFF]", "?");
        while (!value.isEmpty() && font.getStringWidth(value) * size / 1000 > maxWidth) value = value.substring(0, value.length() - 1);
        stream.beginText(); stream.setFont(font, size); stream.setNonStrokingColor(color);
        stream.newLineAtOffset(x, y); stream.showText(value); stream.endText();
    }
}
