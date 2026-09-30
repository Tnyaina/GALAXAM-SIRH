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

    // Palette Sauge Galaxam
    private static final Color BRAND_HEADER = new Color(42, 54, 38);       // #2A3626
    private static final Color BRAND_TITLE = new Color(56, 75, 50);        // #384B32
    private static final Color BRAND_PALE = new Color(237, 242, 232);      // #EDF2E8
    private static final Color ROW_ALT = new Color(248, 250, 247);         // Lignes paires
    private static final Color BORDER_SUBTLE = new Color(226, 230, 223);   // Bordures légères
    private static final Color TEXT_MAIN = new Color(20, 21, 15);          // #14150F
    private static final Color TEXT_MUTED = new Color(110, 118, 108);      // #6E766C
    private static final Color DANGER_BG = new Color(254, 242, 242);
    private static final Color DANGER_BORDER = new Color(252, 165, 165);
    private static final Color DANGER_TEXT = new Color(185, 28, 28);
    private static final Color SUCCESS_BG = new Color(240, 253, 244);
    private static final Color SUCCESS_BORDER = new Color(187, 247, 208);
    private static final Color SUCCESS_TEXT = new Color(22, 101, 52);

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
                        if ((i - first) % 2 == 0) fill(stream, 30, y - 3, 782, 11.6f, ROW_ALT);

                        String statut = PointageExcelExportService.status(d.getStatut());
                        boolean isOff = "REPOS".equalsIgnoreCase(d.getStatut())
                                || "FERIE_CHOME".equalsIgnoreCase(d.getStatut())
                                || "NON_ECHU".equalsIgnoreCase(d.getStatut())
                                || ("CONGE".equalsIgnoreCase(d.getStatut()) && (d.getTotalHeuresDecimal() == null || d.getTotalHeuresDecimal() <= 0.0001));

                        String hoursStr = (d.getTotalHeuresDecimal() == null || (isOff && d.getTotalHeuresDecimal() <= 0.0001))
                                ? "-"
                                : PointageDocumentData.dualHours(d.getTotalHeuresDecimal());

                        String retardStr = (d.getMinutesRetard() == null || d.getMinutesRetard() == 0)
                                ? "-"
                                : d.getMinutesRetard() + " min";

                        String obsStr = cleanValue(d.getObservation());

                        String[] fields = {
                                PointageDocumentData.date(d.getDateComplete()),
                                weekday(d.getJour()),
                                statut,
                                isOff && isBlankOrDash(d.getEntree()) ? "-" : cleanValue(d.getEntree()),
                                isOff && isBlankOrDash(d.getSortiePause()) ? "-" : cleanValue(d.getSortiePause()),
                                isOff && isBlankOrDash(d.getRetourPause()) ? "-" : cleanValue(d.getRetourPause()),
                                isOff && isBlankOrDash(d.getSortie()) ? "-" : cleanValue(d.getSortie()),
                                hoursStr,
                                retardStr,
                                obsStr
                        };
                        for (int c = 0; c < fields.length; c++) {
                            Color col = ("-".equals(fields[c]) && (c >= 3 && c <= 9)) ? TEXT_MUTED : TEXT_MAIN;
                            draw(stream, X[c] + 3, y, fields[c], REGULAR, 7.2f, col, X[c + 1] - X[c] - 6);
                        }
                    }
                    draw(stream, 30, 22, "Fiche de présence · " + (data.versionSnapshot() == null ? "Données provisoires" : "Période validée")
                            + " · Édité le " + data.edition(), REGULAR, 7.5f, TEXT_MUTED, 700);
                }
            }
            detailPages(pdf, data);
            pdf.save(output);
            return output.toByteArray();
        }
    }

    private static void header(PDPageContentStream stream, PointageDocumentData data, int page, int pages) throws IOException {
        var f = data.fiche();
        fill(stream, 30, 535, 782, 35, BRAND_HEADER);
        draw(stream, 42, 548, "FICHE DE PRÉSENCE", BOLD, 14, Color.WHITE, 500);
        draw(stream, 742, 548, page + " / " + pages, REGULAR, 9, Color.WHITE, 55);
        draw(stream, 30, 519, cleanValue(f.getNomComplet()) + "  -  Matricule " + cleanValue(f.getMatricule()), BOLD, 10.5f, BRAND_HEADER, 600);
        draw(stream, 30, 503, "Période : " + PointageDocumentData.date(f.getDateDebut()) + " au " + PointageDocumentData.date(f.getDateFin()), REGULAR, 8.5f, TEXT_MUTED, 320);
        draw(stream, 350, 503, "Fonction : " + cleanValue(f.getFonction()) + "   -   Catégorie : " + cleanValue(f.getCategorie()) + "   -   Site : " + cleanValue(f.getSiteNom()), REGULAR, 8.5f, TEXT_MUTED, 460);
    }

    private static void tableHeader(PDPageContentStream stream) throws IOException {
        fill(stream, 30, 470, 782, 18, BRAND_PALE);
        for (int c = 0; c < HEADERS.length; c++) draw(stream, X[c] + 3, 476, HEADERS[c], BOLD, 7.4f, BRAND_TITLE, X[c + 1] - X[c] - 6);
    }

    private static void detailPages(PDDocument pdf, PointageDocumentData data) throws IOException {
        PDPage page = new PDPage(new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()));
        pdf.addPage(page);
        PDPageContentStream stream = new PDPageContentStream(pdf, page);
        detailHeader(stream, data, false);
        float y = 478;
        fill(stream, 30, y, 782, 18, BRAND_PALE);
        draw(stream, 39, y + 6, "SYNTHÈSE HEBDOMADAIRE", BOLD, 9, BRAND_TITLE, 300);
        y -= 17;
        String[] headings = {"Semaine", "Jours", "Heures faites", "Objectif", "Déficit", "HS 30 %", "HS 50 %", "Nuit", "Dimanche", "Férié"};
        float[] positions = {39, 201, 240, 320, 402, 477, 553, 628, 690, 755};
        for (int i = 0; i < headings.length; i++) draw(stream, positions[i], y, headings[i], BOLD, 7.2f, BRAND_TITLE, i == 0 ? 155 : 75);
        var weeks = data.fiche().getBilansHebdo();
        if (weeks != null) for (var week : weeks) {
            y -= 25;
            draw(stream, 39, y, "S" + week.getNumeroSemaine() + " · " + PointageDocumentData.date(week.getDebutSemaine())
                    + " - " + PointageDocumentData.date(week.getFinSemaine()), REGULAR, 7.1f, TEXT_MAIN, 155);
            draw(stream, 201, y, week.getNbJoursTroncon() + " j", REGULAR, 7.1f, TEXT_MUTED, 34);

            // Heures faites
            String[] pairFaite = PointageDocumentData.dualHours(week.getTotalHeuresTravaillees()).split(" ", 2);
            draw(stream, positions[2], y, pairFaite[0], BOLD, 7.2f, TEXT_MAIN, 68);
            draw(stream, positions[2], y - 10, pairFaite.length > 1 ? pairFaite[1] : "", REGULAR, 6.5f, TEXT_MUTED, 68);

            // Objectif
            String[] pairObj = PointageDocumentData.dualHours(week.getSeuilHeuresTroncon()).split(" ", 2);
            draw(stream, positions[3], y, pairObj[0], REGULAR, 7.2f, TEXT_MUTED, 68);
            draw(stream, positions[3], y - 10, pairObj.length > 1 ? pairObj[1] : "", REGULAR, 6.5f, TEXT_MUTED, 68);

            // Déficit : si 0, tiret discret
            double def = week.getHeuresDeficit();
            if (def <= 0.0001) {
                draw(stream, positions[4], y, "-", REGULAR, 7.2f, TEXT_MUTED, 68);
            } else {
                String[] pair = PointageDocumentData.dualHours(def).split(" ", 2);
                draw(stream, positions[4], y, "−" + pair[0], BOLD, 7.2f, DANGER_TEXT, 68);
                draw(stream, positions[4], y - 10, pair.length > 1 ? "(−" + pair[1].replace("(", "") : "", REGULAR, 6.5f, DANGER_TEXT, 68);
            }

            // HS 30 % : si 0, tiret discret
            double hs30 = week.getHeuresHs30();
            if (hs30 <= 0.0001) {
                draw(stream, positions[5], y, "-", REGULAR, 7.2f, TEXT_MUTED, 68);
            } else {
                String[] pair = PointageDocumentData.dualHours(hs30).split(" ", 2);
                draw(stream, positions[5], y, "+" + pair[0], BOLD, 7.2f, SUCCESS_TEXT, 68);
                draw(stream, positions[5], y - 10, pair.length > 1 ? "(+" + pair[1].replace("(", "") : "", REGULAR, 6.5f, SUCCESS_TEXT, 68);
            }

            // HS 50 % : si 0, tiret discret
            double hs50 = week.getHeuresHs50();
            if (hs50 <= 0.0001) {
                draw(stream, positions[6], y, "-", REGULAR, 7.2f, TEXT_MUTED, 68);
            } else {
                String[] pair = PointageDocumentData.dualHours(hs50).split(" ", 2);
                draw(stream, positions[6], y, "+" + pair[0], BOLD, 7.2f, SUCCESS_TEXT, 68);
                draw(stream, positions[6], y - 10, pair.length > 1 ? "(+" + pair[1].replace("(", "") : "", REGULAR, 6.5f, SUCCESS_TEXT, 68);
            }

            // Nuit, Dimanche, Férié : si 0, tiret discret
            double[] qualifs = {week.getHeuresNuit(), week.getHeuresDimanche(), week.getHeuresFerie()};
            for (int q = 0; q < qualifs.length; q++) {
                float pos = positions[7 + q];
                if (qualifs[q] <= 0.0001) {
                    draw(stream, pos, y, "-", REGULAR, 7.2f, TEXT_MUTED, 60);
                } else {
                    String[] pair = PointageDocumentData.dualHours(qualifs[q]).split(" ", 2);
                    draw(stream, pos, y, pair[0], BOLD, 7.2f, TEXT_MAIN, 60);
                    draw(stream, pos, y - 10, pair.length > 1 ? pair[1] : "", REGULAR, 6.5f, TEXT_MUTED, 60);
                }
            }
        }
        if (weeks == null || weeks.isEmpty()) { y -= 16; draw(stream, 39, y, "Aucune synthèse hebdomadaire disponible", REGULAR, 8, TEXT_MUTED, 450); }

        // Espace suffisant pour ne jamais écraser la dernière semaine
        y -= 26;
        draw(stream, 39, y, "Déficit et HS après compensation. Nuit, dimanche et férié qualifient des heures déjà travaillées.", REGULAR, 7.2f, TEXT_MUTED, 760);
        y -= 31;

        if (!data.appliedAdjustments().isEmpty()) {
            if (y - 17 < 200) {
                stream.close();
                page = new PDPage(new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()));
                pdf.addPage(page);
                stream = new PDPageContentStream(pdf, page);
                detailHeader(stream, data, true);
                y = 483;
            }
            fill(stream, 30, y, 782, 18, BRAND_PALE);
            draw(stream, 39, y + 6, "AJUSTEMENTS APPLIQUÉS AU SOLDE", BOLD, 9, BRAND_TITLE, 400);
            y -= 17;
            for (var adjustment : data.appliedAdjustments()) {
                String date = PointageDocumentData.date(adjustment.date());
                String duration = PointageDocumentData.signedDualMinutes(adjustment.minutes());
                String motif = cleanValue(adjustment.motif());
                int max = 120;
                for (int offset = 0; offset < motif.length(); offset += max) {
                    if (y < 200) {
                        stream.close();
                        page = new PDPage(new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()));
                        pdf.addPage(page);
                        stream = new PDPageContentStream(pdf, page);
                        detailHeader(stream, data, true);
                        y = 483;
                    }
                    draw(stream, 39, y, offset == 0 ? date : "", REGULAR, 8, TEXT_MAIN, 100);
                    Color durColor = adjustment.minutes() != null && adjustment.minutes() < 0 ? DANGER_TEXT : SUCCESS_TEXT;
                    draw(stream, 145, y, offset == 0 ? duration : "", BOLD, 8, durColor, 130);
                    draw(stream, 285, y, motif.substring(offset, Math.min(offset + max, motif.length())), REGULAR, 8, TEXT_MAIN, 515);
                    y -= 15;
                }
            }
        }
        stream.close();
    }

    private static void detailHeader(PDPageContentStream stream, PointageDocumentData data, boolean continuation) throws IOException {
        fill(stream, 30, 535, 782, 35, BRAND_HEADER);
        draw(stream, 42, 548, continuation ? "FICHE DE PRÉSENCE - AJUSTEMENTS (SUITE)" : "FICHE DE PRÉSENCE - DÉTAIL & BILAN", BOLD, 13, Color.WHITE, 700);
        draw(stream, 30, 519, cleanValue(data.fiche().getNomComplet()) + "   -   Catégorie : " + cleanValue(data.fiche().getCategorie())
                + "   -   Période : " + PointageDocumentData.date(data.fiche().getDateDebut()) + " au " + PointageDocumentData.date(data.fiche().getDateFin()), REGULAR, 9, TEXT_MUTED, 720);
        if (!continuation) summary(stream, data);
        draw(stream, 30, 14, "Fiche de présence - " + (data.versionSnapshot() == null ? "Données provisoires" : "Période validée")
                + " - Édité le " + data.edition(), REGULAR, 7f, TEXT_MUTED, 700);
    }

    private static void summary(PDPageContentStream stream, PointageDocumentData data) throws IOException {
        float y = 196;
        fill(stream, 30, y, 782, 19, BRAND_PALE);
        draw(stream, 40, y + 6, "RAPPROCHEMENT DU SOLDE", BOLD, 9, BRAND_TITLE, 300);

        List<BigDecimal> b = data.bilan();
        BigDecimal weeklyHs = add(at(b, 1), at(b, 2));
        BigDecimal deficit = negative(at(b, 0));
        BigDecimal ajustements = at(b, 3);
        BigDecimal soldeFinal = at(b, 4);

        // 4 cartes horizontales généreuses et aérées (hauteur 44pt)
        String[] labels = {"HS hebdo", "Déficit restant", "Ajustements", "Solde final"};
        BigDecimal[] values = {weeklyHs, deficit, ajustements, soldeFinal};

        float cardWidth = 191;
        float cardHeight = 44;
        float cardTop = y - 8;
        float cardBottom = cardTop - cardHeight;
        for (int i = 0; i < 4; i++) {
            float x = 31 + i * 197;
            boolean isSoldeCard = (i == 3);
            boolean isNeg = values[i] != null && values[i].signum() < 0;
            boolean isPos = values[i] != null && values[i].signum() > 0;

            Color bgCard = isSoldeCard && isNeg ? DANGER_BG : isSoldeCard && isPos ? SUCCESS_BG : new Color(248, 250, 247);
            Color borderCard = isSoldeCard && isNeg ? DANGER_BORDER : isSoldeCard && isPos ? SUCCESS_BORDER : BORDER_SUBTLE;
            fill(stream, x, cardBottom, cardWidth, cardHeight, bgCard);
            strokeRect(stream, x, cardBottom, cardWidth, cardHeight, borderCard);

            String label = isSoldeCard ? (isNeg ? "Solde final · Déficit" : isPos ? "Solde final · Excédent" : "Solde final") : labels[i];
            Color labelColor = isSoldeCard && isNeg ? DANGER_TEXT : isSoldeCard && isPos ? SUCCESS_TEXT : TEXT_MUTED;
            draw(stream, x + 12, cardTop - 15, label, BOLD, 7.8f, labelColor, cardWidth - 24);

            String valStr = PointageDocumentData.signedDualHours(values[i]);
            Color valColor = isNeg ? DANGER_TEXT : isPos ? SUCCESS_TEXT : TEXT_MAIN;
            draw(stream, x + 12, cardTop - 33, valStr, BOLD, 10f, valColor, cardWidth - 24);
        }

        // Ligne Incidence Paie - alignée en colonnes espacées
        float paieY = cardBottom - 26;
        draw(stream, 40, paieY, "Incidence paie", BOLD, 8.5f, BRAND_TITLE, 110);

        String absenceStr = at(b, 5) != null && at(b, 5).signum() > 0 ? PointageDocumentData.dualHours(at(b, 5)) : "-";
        draw(stream, 160, paieY, "Retenue absence :", REGULAR, 8.2f, TEXT_MUTED, 100);
        Color absColor = at(b, 5) != null && at(b, 5).signum() > 0 ? DANGER_TEXT : TEXT_MUTED;
        draw(stream, 255, paieY, absenceStr, BOLD, 8.5f, absColor, 130);

        String hs30Str = at(b, 6) != null && at(b, 6).signum() > 0 ? PointageDocumentData.dualHours(at(b, 6)) : "-";
        draw(stream, 410, paieY, "HS 30 % :", REGULAR, 8.2f, TEXT_MUTED, 70);
        Color hs30Color = at(b, 6) != null && at(b, 6).signum() > 0 ? SUCCESS_TEXT : TEXT_MUTED;
        draw(stream, 475, paieY, hs30Str, BOLD, 8.5f, hs30Color, 120);

        String hs50Str = at(b, 7) != null && at(b, 7).signum() > 0 ? PointageDocumentData.dualHours(at(b, 7)) : "-";
        draw(stream, 620, paieY, "HS 50 % :", REGULAR, 8.2f, TEXT_MUTED, 70);
        Color hs50Color = at(b, 7) != null && at(b, 7).signum() > 0 ? SUCCESS_TEXT : TEXT_MUTED;
        draw(stream, 685, paieY, hs50Str, BOLD, 8.5f, hs50Color, 120);

        // Ligne Heures qualifiées - alignée en colonnes avec sous-titre
        float qualifY = paieY - 26;
        draw(stream, 40, qualifY + 2, "Heures qualifiées", BOLD, 8.5f, BRAND_TITLE, 110);
        draw(stream, 40, qualifY - 8, "(déjà dans tps travaillé)", REGULAR, 6.6f, TEXT_MUTED, 110);

        String nuitStr = at(b, 8) != null && at(b, 8).signum() > 0 ? PointageDocumentData.dualHours(at(b, 8)) : "-";
        draw(stream, 160, qualifY, "Nuit :", REGULAR, 8.2f, TEXT_MUTED, 50);
        draw(stream, 205, qualifY, nuitStr, BOLD, 8.5f, at(b, 8) != null && at(b, 8).signum() > 0 ? TEXT_MAIN : TEXT_MUTED, 130);

        String dimStr = at(b, 9) != null && at(b, 9).signum() > 0 ? PointageDocumentData.dualHours(at(b, 9)) : "-";
        draw(stream, 410, qualifY, "Dimanche :", REGULAR, 8.2f, TEXT_MUTED, 65);
        draw(stream, 475, qualifY, dimStr, BOLD, 8.5f, at(b, 9) != null && at(b, 9).signum() > 0 ? TEXT_MAIN : TEXT_MUTED, 120);

        String ferieStr = at(b, 10) != null && at(b, 10).signum() > 0 ? PointageDocumentData.dualHours(at(b, 10)) : "-";
        draw(stream, 620, qualifY, "Jour férié :", REGULAR, 8.2f, TEXT_MUTED, 65);
        draw(stream, 685, qualifY, ferieStr, BOLD, 8.5f, at(b, 10) != null && at(b, 10).signum() > 0 ? TEXT_MAIN : TEXT_MUTED, 120);

        // Ligne Indemnités de pointage - cartouches soignés
        float allowanceY = qualifY - 30;
        draw(stream, 40, allowanceY + 3, "Indemnités", BOLD, 8.5f, BRAND_TITLE, 110);
        draw(stream, 40, allowanceY - 7, "(arrondi à l'ariary)", REGULAR, 6.6f, TEXT_MUTED, 110);

        int count = 0;
        for (var allowance : data.indemnites()) {
            if (allowance.montant() == null || allowance.montant().signum() == 0) continue;
            float bx = 160 + count * 225;
            float by = allowanceY - 9;
            fill(stream, bx, by, 215, 23, new Color(248, 250, 247));
            strokeRect(stream, bx, by, 215, 23, BORDER_SUBTLE);
            draw(stream, bx + 10, by + 7, cleanValue(allowance.libelle()) + " : " + PointageDocumentData.money(allowance.montant()), BOLD, 8f, BRAND_TITLE, 195);
            count++;
        }
        if (count == 0) draw(stream, 160, allowanceY - 1, "Aucune indemnité sur la période", REGULAR, 8.2f, TEXT_MUTED, 300);

        draw(stream, 40, 28, "Le bulletin de paie reste le document contractuel de référence pour le versement.", REGULAR, 7.2f, TEXT_MUTED, 750);
    }

    private static BigDecimal at(List<BigDecimal> values, int index) { return values == null || values.size() <= index ? null : values.get(index); }
    private static BigDecimal add(BigDecimal a, BigDecimal b) { return a == null && b == null ? null : (a == null ? BigDecimal.ZERO : a).add(b == null ? BigDecimal.ZERO : b); }
    private static BigDecimal negative(BigDecimal value) { return value == null ? null : value.negate(); }
    private static String weekday(String value) { return value == null ? "-" : value.replace("..", "."); }
    private static boolean isBlankOrDash(String value) { return value == null || value.isBlank() || value.equals("-") || value.equals("—") || value.equals("?"); }
    private static String cleanValue(String value) {
        if (isBlankOrDash(value)) return "-";
        return value.trim();
    }
    private static void fill(PDPageContentStream stream, float x, float y, float width, float height, Color color) throws IOException {
        stream.setNonStrokingColor(color); stream.addRect(x, y, width, height); stream.fill();
    }
    private static void strokeRect(PDPageContentStream stream, float x, float y, float width, float height, Color color) throws IOException {
        stream.setStrokingColor(color); stream.setLineWidth(0.75f); stream.addRect(x, y, width, height); stream.stroke();
    }
    private static void draw(PDPageContentStream stream, float x, float y, String raw, PDType1Font font,
                             float size, Color color, float maxWidth) throws IOException {
        if (raw == null || raw.isEmpty()) return;
        String value = raw.replace('→', '-')
                .replace('−', '-')
                .replace('—', '-')
                .replace('–', '-')
                .replace('’', '\'')
                .replace('·', '-')
                .replace('\u00A0', ' ')
                .replace('\u202F', ' ')
                .replace('\n', ' ')
                .replaceAll("[^\\x20-\\x7E\\xA0-\\xFF]", "");
        while (!value.isEmpty() && font.getStringWidth(value) * size / 1000 > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.isEmpty()) return;
        stream.beginText(); stream.setFont(font, size); stream.setNonStrokingColor(color);
        stream.newLineAtOffset(x, y); stream.showText(value); stream.endText();
    }
}
