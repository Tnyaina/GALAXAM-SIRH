package mg.galaxam.sirh.model.pointage.service;

import mg.galaxam.sirh.api.dto.pointage.FichePointageEmployeDTO;
import mg.galaxam.sirh.api.dto.pointage.PointageJourDTO;
import mg.galaxam.sirh.api.dto.pointage.PointageTronconSemaineDTO;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service d'exportation Excel (.xlsx) natif pour la fiche individuelle de pointage.
 * Conception sobre et professionnelle (KISS), enrichie de formules Excel dynamiques
 * (SUM, MAX, MIN, IF, ROUND, ISNUMBER) pour garantir une totale exploitabilité et
 * recalcul automatique par la DAF et le service Paie.
 */
@Service
public class PointageExcelExportService {

    private static final Logger log = LoggerFactory.getLogger(PointageExcelExportService.class);

    private final DataSource dataSource;
    private final PointageEmployeService pointageEmployeService;

    public PointageExcelExportService(DataSource dataSource, PointageEmployeService pointageEmployeService) {
        this.dataSource = dataSource;
        this.pointageEmployeService = pointageEmployeService;
    }

    public byte[] genererExcelFicheEmploye(Integer idEmploye, String date, String period, Integer idPeriode) throws IOException {
        FichePointageEmployeDTO fiche = pointageEmployeService.getFichePointageEmploye(idEmploye, date, period, idPeriode);
        if (fiche != null && fiche.getIdEmploye() == null) {
            fiche.setIdEmploye(idEmploye);
        }
        return genererExcelFicheEmploye(fiche);
    }

    public byte[] genererExcelFicheEmploye(FichePointageEmployeDTO fiche) throws IOException {
        if (fiche == null) {
            throw new IllegalArgumentException("La fiche de pointage ne peut être nulle.");
        }

        // Récupérer les informations signalétiques de l'employé
        String matricule = fiche.getMatricule() != null && !fiche.getMatricule().equals("—") ? fiche.getMatricule() : "—";
        String nomComplet = fiche.getNomComplet() != null && !fiche.getNomComplet().equals("Collaborateur") ? fiche.getNomComplet() : "—";
        String siteNom = fiche.getSiteNom() != null ? fiche.getSiteNom() : "Siège";
        String fonction = fiche.getFonction() != null ? fiche.getFonction() : "Collaborateur";
        String categorie = fiche.getCategorie() != null ? fiche.getCategorie() : "—";

        Integer idEmploye = fiche.getIdEmploye();
        if ((matricule.equals("—") || nomComplet.equals("—")) && idEmploye != null) {
            String sqlEmp = "SELECT e.matricule, e.nom, e.prenom, " +
                            "COALESCE(s.libelle, 'Siège') AS site_nom, " +
                            "COALESCE(f.libelle, 'Collaborateur') AS fonction_nom, " +
                            "COALESCE(cp.code, '—') AS code_categorie " +
                            "FROM employe e " +
                            "LEFT JOIN employe_affectation_carriere a ON e.id_employe = a.id_employe " +
                            "    AND (a.date_fin IS NULL OR a.date_fin >= CURRENT_DATE) " +
                            "LEFT JOIN ref_fonction f ON a.id_fonction = f.id_fonction " +
                            "LEFT JOIN ref_site s ON a.id_site = s.id_site " +
                            "LEFT JOIN ref_categorie_professionnelle cp ON a.id_categorie_professionnelle = cp.id_categorie_professionnelle " +
                            "WHERE e.id_employe = ? " +
                            "ORDER BY a.date_debut DESC LIMIT 1";
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(sqlEmp)) {
                ps.setInt(1, idEmploye);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        matricule = rs.getString("matricule");
                        nomComplet = (rs.getString("nom") + " " + (rs.getString("prenom") != null ? rs.getString("prenom") : "")).trim();
                        siteNom = rs.getString("site_nom");
                        fonction = rs.getString("fonction_nom");
                        categorie = rs.getString("code_categorie");
                    }
                }
            } catch (Exception e) {
                log.warn("Erreur lors de la récupération des métadonnées employé pour export Excel: {}", e.getMessage());
            }
        }

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Pointage Individuel");
            sheet.setDisplayGridlines(true);

            // ==================== PALETTE STYLES (KISS & SOBRE) ====================
            Font fontRegular = workbook.createFont();
            fontRegular.setFontName("Calibri");
            fontRegular.setFontHeightInPoints((short) 10);

            Font fontBold = workbook.createFont();
            fontBold.setFontName("Calibri");
            fontBold.setFontHeightInPoints((short) 10);
            fontBold.setBold(true);

            Font fontTitle = workbook.createFont();
            fontTitle.setFontName("Calibri");
            fontTitle.setFontHeightInPoints((short) 13);
            fontTitle.setBold(true);

            DataFormat df = workbook.createDataFormat();

            // Style Titre Document
            CellStyle titleStyle = workbook.createCellStyle();
            titleStyle.setFont(fontTitle);

            // Style Métadonnées
            CellStyle metaLabelStyle = workbook.createCellStyle();
            metaLabelStyle.setFont(fontBold);

            CellStyle metaValueStyle = workbook.createCellStyle();
            metaValueStyle.setFont(fontRegular);

            // Style En-têtes de tableau (Gris très clair sobre, sans couleur criarde)
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(fontBold);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            CellStyle headerLeftStyle = workbook.createCellStyle();
            headerLeftStyle.cloneStyleFrom(headerStyle);
            headerLeftStyle.setAlignment(HorizontalAlignment.LEFT);

            // Style Cellules standard
            CellStyle textStyle = workbook.createCellStyle();
            textStyle.setFont(fontRegular);
            textStyle.setBorderTop(BorderStyle.HAIR);
            textStyle.setBorderBottom(BorderStyle.HAIR);
            textStyle.setBorderLeft(BorderStyle.HAIR);
            textStyle.setBorderRight(BorderStyle.HAIR);

            CellStyle textCenterStyle = workbook.createCellStyle();
            textCenterStyle.cloneStyleFrom(textStyle);
            textCenterStyle.setAlignment(HorizontalAlignment.CENTER);

            CellStyle timeStyle = workbook.createCellStyle();
            timeStyle.cloneStyleFrom(textCenterStyle);
            timeStyle.setDataFormat(df.getFormat("hh:mm"));

            CellStyle numberStyle = workbook.createCellStyle();
            numberStyle.cloneStyleFrom(textStyle);
            numberStyle.setAlignment(HorizontalAlignment.RIGHT);
            numberStyle.setDataFormat(df.getFormat("#,##0.00"));

            CellStyle integerStyle = workbook.createCellStyle();
            integerStyle.cloneStyleFrom(textStyle);
            integerStyle.setAlignment(HorizontalAlignment.RIGHT);
            integerStyle.setDataFormat(df.getFormat("#,##0"));

            CellStyle currencyStyle = workbook.createCellStyle();
            currencyStyle.cloneStyleFrom(textStyle);
            currencyStyle.setAlignment(HorizontalAlignment.RIGHT);
            currencyStyle.setDataFormat(df.getFormat("#,##0 \"Ar\""));

            // Style Lignes de Totaux (Gras + double bordure inférieure)
            CellStyle totalLabelStyle = workbook.createCellStyle();
            totalLabelStyle.setFont(fontBold);
            totalLabelStyle.setBorderTop(BorderStyle.THIN);
            totalLabelStyle.setBorderBottom(BorderStyle.DOUBLE);
            totalLabelStyle.setAlignment(HorizontalAlignment.LEFT);

            CellStyle totalNumberStyle = workbook.createCellStyle();
            totalNumberStyle.setFont(fontBold);
            totalNumberStyle.setBorderTop(BorderStyle.THIN);
            totalNumberStyle.setBorderBottom(BorderStyle.DOUBLE);
            totalNumberStyle.setAlignment(HorizontalAlignment.RIGHT);
            totalNumberStyle.setDataFormat(df.getFormat("#,##0.00"));

            CellStyle totalCurrencyStyle = workbook.createCellStyle();
            totalCurrencyStyle.setFont(fontBold);
            totalCurrencyStyle.setBorderTop(BorderStyle.THIN);
            totalCurrencyStyle.setBorderBottom(BorderStyle.DOUBLE);
            totalCurrencyStyle.setAlignment(HorizontalAlignment.RIGHT);
            totalCurrencyStyle.setDataFormat(df.getFormat("#,##0 \"Ar\""));

            // Styles pour cumuls horaires (format [h]:mm évitant le débordement des 24h)
            CellStyle durationTimeStyle = workbook.createCellStyle();
            durationTimeStyle.cloneStyleFrom(textStyle);
            durationTimeStyle.setAlignment(HorizontalAlignment.RIGHT);
            durationTimeStyle.setDataFormat(df.getFormat("[h]:mm"));

            CellStyle totalDurationTimeStyle = workbook.createCellStyle();
            totalDurationTimeStyle.setFont(fontBold);
            totalDurationTimeStyle.setBorderTop(BorderStyle.THIN);
            totalDurationTimeStyle.setBorderBottom(BorderStyle.DOUBLE);
            totalDurationTimeStyle.setAlignment(HorizontalAlignment.RIGHT);
            totalDurationTimeStyle.setDataFormat(df.getFormat("[h]:mm"));

            int rowIdx = 0;

            // ==================== 1. EN-TÊTE CORPORATE GALAXAM ====================
            Row rTitle = sheet.createRow(rowIdx++);
            Cell cTitle = rTitle.createCell(0);
            cTitle.setCellValue("GALAXAM LOGISTICS — FICHE INDIVIDUELLE DE POINTAGE");
            cTitle.setCellStyle(titleStyle);

            Row rSub = sheet.createRow(rowIdx++);
            Cell cSub = rSub.createCell(0);
            String periodeStr = (fiche.getLibellePeriode() != null ? fiche.getLibellePeriode() : "Période active") +
                    (fiche.getDateDebut() != null && fiche.getDateFin() != null ? " (du " + fiche.getDateDebut() + " au " + fiche.getDateFin() + ")" : "");
            cSub.setCellValue("Période : " + periodeStr + "  |  Statut : " + (fiche.getStatutPeriode() != null ? fiche.getStatutPeriode() : "EN COURS"));
            cSub.setCellStyle(metaValueStyle);

            rowIdx++; // Espace

            // Bloc Salarié (2 colonnes d'infos)
            Row rInfo1 = sheet.createRow(rowIdx++);
            createMetaCell(rInfo1, 0, "Collaborateur :", metaLabelStyle);
            createMetaCell(rInfo1, 1, nomComplet, metaValueStyle);
            createMetaCell(rInfo1, 3, "Matricule :", metaLabelStyle);
            createMetaCell(rInfo1, 4, matricule, metaValueStyle);

            Row rInfo2 = sheet.createRow(rowIdx++);
            createMetaCell(rInfo2, 0, "Poste / Fonction :", metaLabelStyle);
            createMetaCell(rInfo2, 1, fonction, metaValueStyle);
            createMetaCell(rInfo2, 3, "Site :", metaLabelStyle);
            createMetaCell(rInfo2, 4, siteNom + " (" + categorie + ")", metaValueStyle);

            rowIdx++; // Espace

            List<PointageTronconSemaineDTO> bilans = fiche.getBilansHebdo();
            List<PointageJourDTO> pointages = fiche.getPointagesData();

            // ==================== 2. DÉCOMPOSITION HEBDOMADAIRE  ====================
            Row rSec1 = sheet.createRow(rowIdx++);
            Cell cSec1 = rSec1.createCell(0);
            cSec1.setCellValue("I. DÉCOMPOSITION HEBDOMADAIRE & COMPENSATION");
            cSec1.setCellStyle(metaLabelStyle);

            Row rHead1 = sheet.createRow(rowIdx++);
            String[] headersHebdo = {
                    "Tronçon / Semaine", "Jours Ovr.", "Seuil (h)",
                    "Réalisé (hh:mm)", "Réalisé (déc.)",
                    "Retards (hh:mm)", "Retards (déc.)",
                    "Surplus Brut (déc.)", "Compensé  (déc.)", "Solde Net (déc.)"
            };
            for (int i = 0; i < headersHebdo.length; i++) {
                Cell ch = rHead1.createCell(i);
                ch.setCellValue(headersHebdo[i]);
                ch.setCellStyle(i == 0 ? headerLeftStyle : headerStyle);
            }

            int hebdoStartRow = rowIdx + 1; // 1-indexed pour formules Excel
            List<HebdoRowHolder> hebdoHolders = new ArrayList<>();
            if (bilans != null && !bilans.isEmpty()) {
                for (int i = 0; i < bilans.size(); i++) {
                    PointageTronconSemaineDTO b = bilans.get(i);
                    Row rH = sheet.createRow(rowIdx++);
                    int excelR = rowIdx; // 1-indexed (rowIdx déjà incrémenté)

                    // A: Semaine
                    Cell cSem = rH.createCell(0);
                    cSem.setCellValue("Semaine " + (b.getNumeroSemaine() > 0 ? b.getNumeroSemaine() : (i + 1)) +
                            (b.getDebutSemaine() != null ? " (" + b.getDebutSemaine() + " au " + b.getFinSemaine() + ")" : ""));
                    cSem.setCellStyle(textStyle);

                    // B: Jours ouvrés
                    Cell cJ = rH.createCell(1);
                    cJ.setCellValue(b.getNbJoursTroncon());
                    cJ.setCellStyle(integerStyle);

                    // C: Seuil (Formule native Excel: =B{R} * 8, prend en compte les jours fériés chômés)
                    Cell cSeuil = rH.createCell(2);
                    cSeuil.setCellFormula("B" + excelR + " * 8");
                    cSeuil.setCellStyle(numberStyle);

                    // D: Réalisé (hh:mm)
                    Cell cRealTime = rH.createCell(3);
                    cRealTime.setCellValue(b.getTotalHeuresTravaillees() / 24.0);
                    cRealTime.setCellStyle(durationTimeStyle);

                    // E: Réalisé (déc.)
                    Cell cRealDec = rH.createCell(4);
                    cRealDec.setCellValue(b.getTotalHeuresTravaillees());
                    cRealDec.setCellStyle(numberStyle);

                    // F: Retards (hh:mm)
                    Cell cRetTime = rH.createCell(5);
                    cRetTime.setCellValue(b.getHeuresRetard() / 24.0);
                    cRetTime.setCellStyle(durationTimeStyle);

                    // G: Retards (déc.)
                    Cell cRetDec = rH.createCell(6);
                    cRetDec.setCellValue(b.getHeuresRetard());
                    cRetDec.setCellStyle(numberStyle);

                    // H: Surplus Brut (Formule Excel native: =MAX(0; Réalisé déc. - Seuil))
                    Cell cSurplus = rH.createCell(7);
                    cSurplus.setCellFormula("MAX(0, E" + excelR + " - C" + excelR + ")");
                    cSurplus.setCellStyle(numberStyle);

                    // I: Compensé  (Formule Excel native: =MIN(Retards déc.; Surplus déc.))
                    Cell cComp = rH.createCell(8);
                    cComp.setCellFormula("MIN(G" + excelR + ", H" + excelR + ")");
                    cComp.setCellStyle(numberStyle);

                    // J: Solde Net (Formule Excel native: =IF(Surplus >= Retards; Surplus - Compensé; -(Retards - Compensé)))
                    Cell cSolde = rH.createCell(9);
                    cSolde.setCellFormula("IF(H" + excelR + " >= G" + excelR + ", H" + excelR + " - I" + excelR + ", -(G" + excelR + " - I" + excelR + "))");
                    cSolde.setCellStyle(numberStyle);

                    HebdoRowHolder holder = new HebdoRowHolder();
                    holder.row = rH;
                    holder.excelR = excelR;
                    holder.dto = b;
                    hebdoHolders.add(holder);
                }
            }
            int hebdoEndRow = rowIdx; // Dernier tronçon 1-indexed

            // Ligne Total Hebdo
            Row rTotHebdo = sheet.createRow(rowIdx++);
            Cell cTotLbl = rTotHebdo.createCell(0);
            cTotLbl.setCellValue("TOTAL MENSUEL");
            cTotLbl.setCellStyle(totalLabelStyle);

            Cell cTotJ = rTotHebdo.createCell(1);
            cTotJ.setCellFormula("SUM(B" + hebdoStartRow + ":B" + hebdoEndRow + ")");
            cTotJ.setCellStyle(totalNumberStyle);

            Cell cTotSeuil = rTotHebdo.createCell(2);
            cTotSeuil.setCellFormula("SUM(C" + hebdoStartRow + ":C" + hebdoEndRow + ")");
            cTotSeuil.setCellStyle(totalNumberStyle);

            Cell cTotRealTime = rTotHebdo.createCell(3);
            cTotRealTime.setCellFormula("SUM(D" + hebdoStartRow + ":D" + hebdoEndRow + ")");
            cTotRealTime.setCellStyle(totalDurationTimeStyle);

            Cell cTotRealDec = rTotHebdo.createCell(4);
            cTotRealDec.setCellFormula("SUM(E" + hebdoStartRow + ":E" + hebdoEndRow + ")");
            cTotRealDec.setCellStyle(totalNumberStyle);

            Cell cTotRetTime = rTotHebdo.createCell(5);
            cTotRetTime.setCellFormula("SUM(F" + hebdoStartRow + ":F" + hebdoEndRow + ")");
            cTotRetTime.setCellStyle(totalDurationTimeStyle);

            Cell cTotRetDec = rTotHebdo.createCell(6);
            cTotRetDec.setCellFormula("SUM(G" + hebdoStartRow + ":G" + hebdoEndRow + ")");
            cTotRetDec.setCellStyle(totalNumberStyle);

            Cell cTotSurplus = rTotHebdo.createCell(7);
            cTotSurplus.setCellFormula("SUM(H" + hebdoStartRow + ":H" + hebdoEndRow + ")");
            cTotSurplus.setCellStyle(totalNumberStyle);

            Cell cTotComp = rTotHebdo.createCell(8);
            cTotComp.setCellFormula("SUM(I" + hebdoStartRow + ":I" + hebdoEndRow + ")");
            cTotComp.setCellStyle(totalNumberStyle);

            Cell cTotSolde = rTotHebdo.createCell(9);
            cTotSolde.setCellFormula("SUM(J" + hebdoStartRow + ":J" + hebdoEndRow + ")");
            cTotSolde.setCellStyle(totalNumberStyle);

            rowIdx++; // Espace
            rowIdx++; // Espace

            // ==================== 3. JOURNAL QUOTIDIEN DÉTAILLÉ ====================
            Row rSec2 = sheet.createRow(rowIdx++);
            Cell cSec2 = rSec2.createCell(0);
            cSec2.setCellValue("II. JOURNAL QUOTIDIEN DES POINTAGES & INDEMNITÉS");
            cSec2.setCellStyle(metaLabelStyle);

            Row rHead2 = sheet.createRow(rowIdx++);
            String[] headersJour = {
                    "Date", "Jour", "Statut", "Entrée 1", "Sortie 1", "Entrée 2", "Sortie 2",
                    "Total (hh:mm)", "Total (déc.)", "Retard (min)", "Repas (Ar)", "Déplacement (Ar)", "Observation"
            };
            for (int i = 0; i < headersJour.length; i++) {
                Cell ch = rHead2.createCell(i);
                ch.setCellValue(headersJour[i]);
                ch.setCellStyle(i < 3 ? headerLeftStyle : headerStyle);
            }

            int dailyStartRow = rowIdx + 1; // 1-indexed
            Map<String, Integer> dateToExcelRowMap = new HashMap<>();
            if (pointages != null && !pointages.isEmpty()) {
                for (PointageJourDTO pj : pointages) {
                    Row rD = sheet.createRow(rowIdx++);
                    int excelR = rowIdx; // 1-indexed
                    if (pj.getDateComplete() != null) {
                        dateToExcelRowMap.put(pj.getDateComplete(), excelR);
                    }

                    // A: Date
                    Cell cDate = rD.createCell(0);
                    cDate.setCellValue(pj.getDateComplete() != null ? pj.getDateComplete() : (pj.getDate() != null ? pj.getDate() : ""));
                    cDate.setCellStyle(textCenterStyle);

                    // B: Jour
                    Cell cJour = rD.createCell(1);
                    cJour.setCellValue(pj.getJour() != null ? pj.getJour() : "");
                    cJour.setCellStyle(textStyle);

                    // C: Statut
                    Cell cStat = rD.createCell(2);
                    cStat.setCellValue(pj.getStatut() != null ? pj.getStatut() : "Présent");
                    cStat.setCellStyle(textStyle);

                    // D, E, F, G: Horaires au format Heure Excel native (hh:mm)
                    createTimeCell(rD, 3, pj.getEntree(), timeStyle);
                    createTimeCell(rD, 4, pj.getSortiePause(), timeStyle);
                    createTimeCell(rD, 5, pj.getRetourPause(), timeStyle);
                    createTimeCell(rD, 6, pj.getSortie(), timeStyle);

                    // H: Total Heures (hh:mm)
                    // DISPENSE_POINTAGE → valeur fixe directe (pas de formule, plus clair pour l'auditeur)
                    // Horaire continu (E vide, F vide) → Sortie - Entrée directement
                    // Horaire scindé → (Sortie1 - Entrée1) + (Sortie2 - Entrée2)
                    //
                    // Formule finale (2 termes lisibles) :
                    //   Terme matin : si Sortie1 existe  → Sortie1 - Entrée1
                    //                 sinon (continu)    → Sortie2 - Entrée1
                    //   Terme AM    : si Entrée2 ET Sortie2 existent → Sortie2 - Entrée2
                    Cell cTotTime = rD.createCell(7);
                    boolean isDispense = "DISPENSE_POINTAGE".equals(pj.getStatut());
                    if (isDispense) {
                        // Valeur directe : 8 h = 8/24 en fraction de jour Excel
                        cTotTime.setCellValue(8.0 / 24.0);
                    } else {
                        String r = String.valueOf(excelR);
                        // Terme matin  : si E présent → E-D ; sinon si continu → G-D ; sinon 0
                        String matin = "IF(ISNUMBER(E" + r + "), E" + r + "-D" + r +
                                ", IF(AND(ISNUMBER(D" + r + "), ISNUMBER(G" + r + ")), G" + r + "-D" + r + ", 0))";
                        // Terme AM : si F ET G présents → G-F ; sinon 0
                        // (on soustrait l'intégralité G-D déjà prise par "matin continu")
                        String am = "IF(AND(ISNUMBER(F" + r + "), ISNUMBER(G" + r + ")), G" + r + "-F" + r + ", 0)";
                        cTotTime.setCellFormula(matin + "+" + am);
                    }
                    cTotTime.setCellStyle(timeStyle);

                    // I: Total Heures (déc.) = conversion centésimale depuis H
                    Cell cTotDec = rD.createCell(8);
                    if (isDispense) {
                        cTotDec.setCellValue(8.0);
                    } else {
                        cTotDec.setCellFormula("ROUND(H" + excelR + " * 24, 2)");
                    }
                    cTotDec.setCellStyle(numberStyle);

                    // J: Retard en minutes
                    Cell cRetM = rD.createCell(9);
                    cRetM.setCellValue(parseRetardMinutes(pj.getEcart(), pj.isEcartPositif()));
                    cRetM.setCellStyle(integerStyle);

                    // K: Panier Repas
                    Cell cMntRep = rD.createCell(10);
                    cMntRep.setCellValue(pj.getMontantRepas() != null ? pj.getMontantRepas().doubleValue() : 0.0);
                    cMntRep.setCellStyle(currencyStyle);

                    // L: Déplacement
                    Cell cMntDepl = rD.createCell(11);
                    cMntDepl.setCellValue(pj.getMontantDeplacement() != null ? pj.getMontantDeplacement().doubleValue() : 0.0);
                    cMntDepl.setCellStyle(currencyStyle);

                    // M: Observation
                    Cell cObs = rD.createCell(12);
                    String obs = pj.isConge() ? "Congé" : (pj.isRepos() ? "Repos" : (pj.isAbsent() ? "Absent" : (pj.isRetard() ? "Retard" : "")));
                    cObs.setCellValue(obs);
                    cObs.setCellStyle(textStyle);
                }
            }
            int dailyEndRow = rowIdx; // 1-indexed

            // Liaison dynamique 100% exacte entre la synthèse Hebdo et le journal Quotidien
            for (HebdoRowHolder h : hebdoHolders) {
                PointageTronconSemaineDTO b = h.dto;
                int sDayRow = -1;
                int eDayRow = -1;
                if (pointages != null && b.getDebutSemaine() != null && b.getFinSemaine() != null) {
                    for (PointageJourDTO pj : pointages) {
                        String dStr = pj.getDateComplete();
                        if (dStr != null && dStr.compareTo(b.getDebutSemaine()) >= 0 && dStr.compareTo(b.getFinSemaine()) <= 0) {
                            Integer r = dateToExcelRowMap.get(dStr);
                            if (r != null) {
                                if (sDayRow == -1) sDayRow = r;
                                eDayRow = r;
                            }
                        }
                    }
                }
                if (sDayRow != -1 && eDayRow != -1) {
                    // Correction : hh:mm = somme exacte des horaires du journal (sans double arrondi)
                    Cell cRealTime = h.row.getCell(3);
                    cRealTime.setCellFormula("SUM(H" + sDayRow + ":H" + eDayRow + ")");

                    // Correction : déc. = conversion depuis le total hh:mm exact, arrondi UNE SEULE FOIS
                    Cell cRealDec = h.row.getCell(4);
                    cRealDec.setCellFormula("ROUND(SUM(H" + sDayRow + ":H" + eDayRow + ") * 24, 2)");

                    // Retards (hh:mm) : inchangé (stocké depuis le backend, pas calculé en Excel)
                    Cell cRetTime = h.row.getCell(5);
                    cRetTime.setCellFormula("G" + h.excelR + " / 24");

                    // Retards (déc.) : somme des minutes du journal ÷ 60
                    Cell cRetDec = h.row.getCell(6);
                    cRetDec.setCellFormula("ROUND(SUM(J" + sDayRow + ":J" + eDayRow + ") / 60, 2)");
                }
            }

            // Ligne Totaux Journaliers
            Row rTotDaily = sheet.createRow(rowIdx++);
            Cell cTotD1 = rTotDaily.createCell(0);
            cTotD1.setCellValue("CUMULS JOURNALIERS");
            cTotD1.setCellStyle(totalLabelStyle);

            for (int i = 1; i <= 6; i++) {
                Cell cEmpty = rTotDaily.createCell(i);
                cEmpty.setCellStyle(totalLabelStyle);
            }

            // H: Somme heures (hh:mm) (Formule dynamique)
            Cell cTotHTime = rTotDaily.createCell(7);
            cTotHTime.setCellFormula("SUM(H" + dailyStartRow + ":H" + dailyEndRow + ")");
            cTotHTime.setCellStyle(totalDurationTimeStyle);

            // I: Somme heures (déc.) (Formule dynamique)
            Cell cTotHDec = rTotDaily.createCell(8);
            cTotHDec.setCellFormula("SUM(I" + dailyStartRow + ":I" + dailyEndRow + ")");
            cTotHDec.setCellStyle(totalNumberStyle);

            // J: Somme retards (minutes)
            Cell cTotRetAll = rTotDaily.createCell(9);
            cTotRetAll.setCellFormula("SUM(J" + dailyStartRow + ":J" + dailyEndRow + ")");
            cTotRetAll.setCellStyle(totalNumberStyle);

            // K: Somme repas
            Cell cTotRepAll = rTotDaily.createCell(10);
            cTotRepAll.setCellFormula("SUM(K" + dailyStartRow + ":K" + dailyEndRow + ")");
            cTotRepAll.setCellStyle(totalCurrencyStyle);

            // L: Somme déplacements
            Cell cTotDeplAll = rTotDaily.createCell(11);
            cTotDeplAll.setCellFormula("SUM(L" + dailyStartRow + ":L" + dailyEndRow + ")");
            cTotDeplAll.setCellStyle(totalCurrencyStyle);

            Cell cTotEnd = rTotDaily.createCell(12);
            cTotEnd.setCellStyle(totalLabelStyle);

            sheet.setForceFormulaRecalculation(true);

            // Correction finale : TOTAL MENSUEL déc. et hh:mm
            // référencent la somme exacte du journal quotidien
            // (pas la ré-somme des lignes hebdo déjà arrondies)
            cTotRealTime.setCellFormula("SUM(H" + dailyStartRow + ":H" + dailyEndRow + ")");
            cTotRealDec.setCellFormula("ROUND(SUM(H" + dailyStartRow + ":H" + dailyEndRow + ") * 24, 2)");

            // Ajustement automatique des largeurs de colonnes
            for (int col = 0; col < 13; col++) {
                sheet.autoSizeColumn(col);
                if (sheet.getColumnWidth(col) < 3200) {
                    sheet.setColumnWidth(col, 3200);
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    public static String buildExportFilename(FichePointageEmployeDTO fiche, String extension) {
        String nom = "Employe";
        String p = "P";
        if (fiche.getNom() != null && !fiche.getNom().trim().isEmpty()) {
            nom = fiche.getNom().trim();
        } else if (fiche.getNomComplet() != null && !fiche.getNomComplet().trim().isEmpty()) {
            String[] parts = fiche.getNomComplet().trim().split("\\s+");
            nom = parts[0];
            if (parts.length > 1 && !parts[1].isEmpty()) {
                p = parts[1].substring(0, 1).toUpperCase();
            }
        }
        if (fiche.getPrenom() != null && !fiche.getPrenom().trim().isEmpty()) {
            p = fiche.getPrenom().trim().substring(0, 1).toUpperCase();
        }
        String matricule = fiche.getMatricule() != null && !fiche.getMatricule().trim().isEmpty()
                && !fiche.getMatricule().equals("—")
                ? fiche.getMatricule().trim() : "0000";

        String periode = resolveLibellePeriode(fiche.getLibellePeriode(), fiche.getCodePeriode(), fiche.getDateDebut(), fiche.getDateFin());

        nom = sanitizeForFilename(nom);
        p = sanitizeForFilename(p);
        matricule = sanitizeForFilename(matricule);
        periode = sanitizeForFilename(periode);

        return String.format("Pointage_%s_%s_%s_%s.%s", nom, p, matricule, periode, extension);
    }

    private static String resolveLibellePeriode(String libelle, String code, String dateDebut, String dateFin) {
        if (libelle != null && !libelle.trim().isEmpty() && !libelle.equalsIgnoreCase("Periode")) {
            return libelle.trim();
        }
        if (code != null && code.trim().matches("\\d{4}[-_]\\d{2}")) {
            String[] parts = code.trim().split("[-_]");
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            String[] mois = {"Janvier", "Fevrier", "Mars", "Avril", "Mai", "Juin", "Juillet", "Aout", "Septembre", "Octobre", "Novembre", "Decembre"};
            if (month >= 1 && month <= 12) {
                return mois[month - 1] + "_" + year;
            }
        }
        if (code != null && !code.trim().isEmpty()) {
            return code.trim();
        }
        if (dateDebut != null && dateFin != null) {
            return dateDebut + "_" + dateFin;
        }
        return "Periode";
    }

    private static String sanitizeForFilename(String input) {
        if (input == null || input.isEmpty()) return "X";
        String normalized = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD);
        String ascii = normalized.replaceAll("[^\\p{ASCII}]", "");
        String sanitized = ascii.replaceAll("[^a-zA-Z0-9_-]", "_").replaceAll("_+", "_");
        return sanitized.isEmpty() ? "X" : sanitized;
    }

    private static class HebdoRowHolder {
        Row row;
        int excelR;
        PointageTronconSemaineDTO dto;
    }

    private int parseRetardMinutes(String ecartStr, boolean ecartPositif) {
        if (ecartPositif || ecartStr == null || !ecartStr.contains("-")) return 0;
        try {
            String digits = ecartStr.replaceAll("[^0-9]", "");
            return digits.isEmpty() ? 0 : Integer.parseInt(digits);
        } catch (Exception e) {
            return 0;
        }
    }

    private void createMetaCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "—");
        cell.setCellStyle(style);
    }

    private void createTimeCell(Row row, int col, String timeStr, CellStyle style) {
        Cell cell = row.createCell(col);
        if (timeStr != null && !timeStr.trim().isEmpty() && !timeStr.equals("—") && !timeStr.equals("-") && timeStr.contains(":")) {
            try {
                String[] parts = timeStr.trim().split(":");
                int h = Integer.parseInt(parts[0].trim());
                int m = Integer.parseInt(parts[1].trim());
                double timeValue = (h * 60.0 + m) / 1440.0;
                cell.setCellValue(timeValue);
                cell.setCellStyle(style);
                return;
            } catch (Exception ignored) {}
        }
        cell.setBlank();
        cell.setCellStyle(style);
    }
}
