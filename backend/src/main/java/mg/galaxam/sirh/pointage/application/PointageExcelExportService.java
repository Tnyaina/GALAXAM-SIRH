package mg.galaxam.sirh.pointage.application;

import mg.galaxam.sirh.pointage.api.dto.FichePointageEmployeDTO;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;

/** Fiche de présence destinée au salarié : journal, soldes utiles et indemnités. */
@Service
public class PointageExcelExportService {
    public byte[] genererExcelFicheEmploye(FichePointageEmployeDTO fiche) throws IOException {
        return genererExcelFicheEmploye(PointageDocumentData.from(fiche));
    }

    public byte[] genererExcelFicheEmploye(PointageDocumentData data) throws IOException {
        try (var book = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            Sheet sheet = book.createSheet("Fiche de présence");
            CellStyle title = style(book, true, (short) 16, "07324A", "FFFFFF");   // #07324A --color-brand-900
            CellStyle section = style(book, true, (short) 11, "E1EDF2", "07324A"); // #E1EDF2 --color-brand-100
            CellStyle header = style(book, true, (short) 10, "E1EDF2", "07324A");  // #E1EDF2 --color-brand-100
            CellStyle text = style(book, false, (short) 10, "FFFFFF", "18242D");   // #18242D --color-ink-900
            CellStyle money = book.createCellStyle(); money.cloneStyleFrom(text);
            money.setDataFormat(book.createDataFormat().getFormat("# ##0 \"Ar\""));
            CellStyle note = style(book, false, (short) 9, "F7F9FA", "62717A");    // #F7F9FA --color-ink-50 & #62717A --color-ink-500
            CellStyle duration = book.createCellStyle(); duration.cloneStyleFrom(text); duration.setWrapText(true);
            int row = 0;
            merged(sheet, row++, 0, 9, "FICHE DE PRÉSENCE", title);
            var f = data.fiche();
            merged(sheet, row++, 0, 9, "Période : " + PointageDocumentData.date(f.getDateDebut()) + " au " + PointageDocumentData.date(f.getDateFin()), section);
            merged(sheet, row++, 0, 9, "Salarié : " + PointageDocumentData.value(f.getNomComplet()) + "  ·  Matricule : " + PointageDocumentData.value(f.getMatricule()), text);
            merged(sheet, row++, 0, 9, "Fonction : " + PointageDocumentData.value(f.getFonction()) + "  ·  Catégorie : " + PointageDocumentData.value(f.getCategorie()) + "  ·  Site : " + PointageDocumentData.value(f.getSiteNom()), text);
            merged(sheet, row++, 0, 9, data.versionSnapshot() == null ? "Données provisoires · Édité le " + data.edition() : "Période validée · Édité le " + data.edition(), note);
            row++;
            int journalTitle = row;
            merged(sheet, row++, 0, 9, "JOURNAL DES PRÉSENCES", section);
            String[] headings = {"Date", "Jour", "Statut", "Entrée", "Pause", "Retour", "Sortie", "Heures", "Retard (min)", "Observation"};
            for (int c = 0; c < headings.length; c++) cell(sheet, row, c, headings[c], header);
            row++;
            int firstDay = row;
            for (var d : PointageDocumentData.days(f)) {
                Object[] values = {PointageDocumentData.date(d.getDateComplete()), cleanWeekday(d.getJour()), status(d.getStatut()),
                        d.getEntree(), d.getSortiePause(), d.getRetourPause(), d.getSortie(), PointageDocumentData.dualHours(d.getTotalHeuresDecimal()),
                        d.getMinutesRetard(), d.getObservation()};
                for (int c = 0; c < values.length; c++) cell(sheet, row, c, values[c], text);
                row++;
            }
            cell(sheet, row, 0, "Total", header);
            if (row > firstDay) {
                for (int c : new int[]{7, 8}) {
                    if (c == 7) {
                        double totalHours = PointageDocumentData.days(f).stream().mapToDouble(d -> d.getTotalHeuresDecimal() == null ? 0 : d.getTotalHeuresDecimal()).sum();
                        cell(sheet, row, c, PointageDocumentData.dualHours(totalHours), text);
                    } else {
                        Cell total = cell(sheet, row, c, null, text);
                        total.setCellFormula("SUM(I" + (firstDay + 1) + ":I" + row + ")");
                    }
                }
            }
            row += 2;
            merged(sheet, row++, 0, 9, "SYNTHÈSE HEBDOMADAIRE", section);
            String[] weekHeaders = {"Semaine", "Jours", "Heures faites", "Objectif ajusté", "Déficit", "HS 30 %", "HS 50 %", "Nuit", "Dimanche", "Férié"};
            for (int c = 0; c < weekHeaders.length; c++) cell(sheet, row, c, weekHeaders[c], header);
            row++;
            var weeks = f.getBilansHebdo() == null ? List.<mg.galaxam.sirh.pointage.api.dto.PointageTronconSemaineDTO>of() : f.getBilansHebdo();
            for (var week : weeks) {
                cell(sheet, row, 0, "S" + week.getNumeroSemaine() + " · " + PointageDocumentData.date(week.getDebutSemaine())
                        + " au " + PointageDocumentData.date(week.getFinSemaine()), duration);
                cell(sheet, row, 1, week.getNbJoursTroncon() + " j", text);
                double[] values = {week.getTotalHeuresTravaillees(), week.getSeuilHeuresTroncon(), week.getHeuresDeficit(),
                        week.getHeuresHs30(), week.getHeuresHs50(), week.getHeuresNuit(), week.getHeuresDimanche(), week.getHeuresFerie()};
                for (int c = 0; c < values.length; c++) {
                    String formatted = (c >= 2 && Math.abs(values[c]) < 0.0001) ? "-" : compactHours(values[c]);
                    cell(sheet, row, c + 2, formatted, duration);
                }
                sheet.getRow(row).setHeightInPoints(29);
                row++;
            }
            if (weeks.isEmpty()) merged(sheet, row++, 0, 9, "Aucune synthèse hebdomadaire disponible.", note);
            merged(sheet, row++, 0, 9, "Déficit et HS après compensation dans chaque semaine. Nuit, dimanche et férié qualifient des heures déjà travaillées.", note);
            row++;
            if (!data.appliedAdjustments().isEmpty()) {
                merged(sheet, row++, 0, 9, "AJUSTEMENTS APPLIQUÉS AU SOLDE", section);
                for (var adjustment : data.appliedAdjustments()) {
                    cell(sheet, row, 0, PointageDocumentData.date(adjustment.date()), text);
                    cell(sheet, row, 1, PointageDocumentData.signedDualMinutes(adjustment.minutes()), text);
                    merged(sheet, row, 2, 9, PointageDocumentData.value(adjustment.motif()), text);
                    sheet.getRow(row).setHeightInPoints(30);
                    row++;
                }
                row++;
            }
            merged(sheet, row++, 0, 9, "RAPPROCHEMENT DU SOLDE", section);
            row = summary(sheet, row, "HS hebdomadaires après compensation (30 % + 50 %)", sum(at(data.bilan(), 1), at(data.bilan(), 2)), text);
            row = summary(sheet, row, "Déficits hebdomadaires restants", negate(at(data.bilan(), 0)), text);
            row = summary(sheet, row, "Ajustements de période", at(data.bilan(), 3), text);
            row = summary(sheet, row, "Solde final", at(data.bilan(), 4), text);
            row++;
            merged(sheet, row++, 0, 9, "INCIDENCE FINALE SUR LA PAIE", section);
            row = summary(sheet, row, "Absence horaire à retenir", at(data.bilan(), 5), text);
            row = summary(sheet, row, "Heures supplémentaires à 30 %", at(data.bilan(), 6), text);
            row = summary(sheet, row, "Heures supplémentaires à 50 %", at(data.bilan(), 7), text);
            row = summary(sheet, row, "Heures de nuit", at(data.bilan(), 8), text);
            row = summary(sheet, row, "Heures du dimanche", at(data.bilan(), 9), text);
            row = summary(sheet, row, "Heures des jours fériés", at(data.bilan(), 10), text);
            row++;
            merged(sheet, row++, 0, 9, "INDEMNITÉS DE POINTAGE", section);
            for (var allowance : data.indemnites()) {
                if (allowance.montant() == null || allowance.montant().signum() == 0) continue;
                merged(sheet, row, 0, 5, PointageDocumentData.value(allowance.libelle()), text);
                cell(sheet, row++, 7, PointageDocumentData.ariary(allowance.montant()), money);
            }
            merged(sheet, row++, 0, 9, "Montants arrondis à l'ariary après calcul. Le bulletin de paie reste la référence pour le versement.", note);
            if (data.versionSnapshot() == null) merged(sheet, row++, 0, 9, "Fiche provisoire : les pointages peuvent encore être corrigés.", note);
            for (int c = 0; c < 10; c++) sheet.setColumnWidth(c, new int[]{6800, 4600, 4200, 4700, 4200, 4200, 4200, 4200, 4200, 10000}[c]);
            sheet.createFreezePane(0, journalTitle + 2);
            sheet.setRepeatingRows(new CellRangeAddress(0, journalTitle + 1, -1, -1));
            sheet.getPrintSetup().setLandscape(true);
            sheet.getPrintSetup().setPaperSize(PrintSetup.A4_PAPERSIZE);
            sheet.getPrintSetup().setFitWidth((short) 1);
            sheet.getPrintSetup().setFitHeight((short) 0);
            sheet.setFitToPage(true);
            book.setPrintArea(0, 0, 9, 0, row);
            book.getCreationHelper().createFormulaEvaluator().evaluateAll();
            book.write(output);
            return output.toByteArray();
        }
    }

    private static BigDecimal at(List<BigDecimal> values, int index) { return values == null || values.size() <= index ? null : values.get(index); }
    private static BigDecimal sum(BigDecimal a, BigDecimal b) { return a == null && b == null ? null : (a == null ? BigDecimal.ZERO : a).add(b == null ? BigDecimal.ZERO : b); }
    private static BigDecimal negate(BigDecimal value) { return value == null ? null : value.negate(); }
    private static String compactHours(double hours) { return PointageDocumentData.dualHours(hours).replace(" (", "\n("); }
    private static int summary(Sheet sheet, int row, String label, BigDecimal value, CellStyle text) {
        merged(sheet, row, 0, 5, label, text);
        boolean isOvertimeOrQualified = label.contains("supplémentaires") || label.contains("nuit") || label.contains("dimanche") || label.contains("fériés");
        String formatted = (isOvertimeOrQualified && (value == null || value.signum() == 0))
                ? "-"
                : PointageDocumentData.dualHours(value);
        cell(sheet, row, 7, formatted, text);
        return row + 1;
    }
    private static Cell cell(Sheet sheet, int rowIndex, int column, Object value, CellStyle style) {
        Row row = sheet.getRow(rowIndex); if (row == null) row = sheet.createRow(rowIndex);
        Cell cell = row.getCell(column); if (cell == null) cell = row.createCell(column);
        cell.setCellStyle(style);
        if (value == null || value.toString().isBlank() || value.toString().equals("—")) cell.setCellValue("-");
        else if (value instanceof Number number) cell.setCellValue(number.doubleValue());
        else cell.setCellValue(value.toString());
        return cell;
    }
    private static void merged(Sheet sheet, int row, int first, int last, String value, CellStyle style) {
        for (int c = first; c <= last; c++) cell(sheet, row, c, c == first ? value : null, style);
        sheet.addMergedRegion(new CellRangeAddress(row, row, first, last));
    }
    private static CellStyle style(Workbook book, boolean bold, short size, String hexBackground, String hexForeground) {
        org.apache.poi.xssf.usermodel.XSSFCellStyle style = (org.apache.poi.xssf.usermodel.XSSFCellStyle) book.createCellStyle();
        Font font = book.createFont(); font.setBold(bold); font.setFontHeightInPoints(size);
        font.setColor("FFFFFF".equalsIgnoreCase(hexForeground) ? IndexedColors.WHITE.getIndex() : IndexedColors.BLACK.getIndex());
        style.setFont(font);
        if (hexBackground != null && !hexBackground.isEmpty()) {
            byte[] rgb = parseHex(hexBackground);
            style.setFillForegroundColor(new org.apache.poi.xssf.usermodel.XSSFColor(rgb, null));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }
    private static byte[] parseHex(String hex) {
        int r = Integer.parseInt(hex.substring(0, 2), 16);
        int g = Integer.parseInt(hex.substring(2, 4), 16);
        int b = Integer.parseInt(hex.substring(4, 6), 16);
        return new byte[]{(byte) r, (byte) g, (byte) b};
    }
    private static String cleanWeekday(String value) { return value == null ? "—" : value.replace("..", "."); }
    static String status(String code) {
        if (code == null) return "Non saisi";
        return switch (code.toUpperCase(Locale.ROOT)) {
            case "CONGE", "CONGE_PAYE" -> "Congé payé";
            case "FERIE_CHOME" -> "Férié chômé";
            case "REPOS" -> "Repos";
            case "ABSENT", "ABSENCE_NON_JUSTIFIEE" -> "Absent";
            case "ABSENCE_JUSTIFIEE" -> "Absence justifiée";
            case "DISPENSE_POINTAGE" -> "Dispensé";
            case "DEMI_JOURNEE" -> "Demi-journée";
            case "SAMEDI_TRAVAILLE" -> "Samedi travaillé";
            case "DIMANCHE_TRAVAILLE" -> "Dimanche travaillé";
            case "FERIE_TRAVAILLE" -> "Férié travaillé";
            case "NON_SAISI", "PLANIFIE", "NON_ECHU" -> "Non saisi";
            default -> "Présent";
        };
    }
    public static String buildExportFilename(FichePointageEmployeDTO fiche, String extension) {
        String nom = fiche.getNom() != null && !fiche.getNom().isBlank() ? fiche.getNom() :
                fiche.getNomComplet() == null ? "Employe" : fiche.getNomComplet().split("\\s+")[0];
        String initiale = fiche.getPrenom() != null && !fiche.getPrenom().isBlank() ? fiche.getPrenom().substring(0, 1) : "P";
        String matricule = fiche.getMatricule() == null || fiche.getMatricule().isBlank() ? "0000" : fiche.getMatricule();
        String periode = fiche.getLibellePeriode() != null && !fiche.getLibellePeriode().isBlank() && !"Periode".equalsIgnoreCase(fiche.getLibellePeriode())
                ? fiche.getLibellePeriode() : fiche.getCodePeriode() == null ? "Periode" : fiche.getCodePeriode();
        return "Pointage_" + safe(nom) + "_" + safe(initiale) + "_" + safe(matricule) + "_" + safe(periode) + "." + extension;
    }
    private static String safe(String value) {
        String ascii = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("[^\\p{ASCII}]", "");
        return ascii.replaceAll("[^a-zA-Z0-9_-]", "_").replaceAll("_+", "_");
    }
}
