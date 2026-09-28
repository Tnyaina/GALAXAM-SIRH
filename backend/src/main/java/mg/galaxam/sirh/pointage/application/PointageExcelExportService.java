package mg.galaxam.sirh.pointage.application;

import mg.galaxam.sirh.pointage.api.dto.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.io.*;
import java.math.BigDecimal;
import java.util.*;

/** Remplit le modèle GALAXAM ; seules les sommes de présentation restent des formules. */
@Service
public class PointageExcelExportService {
    public byte[] genererExcelFicheEmploye(FichePointageEmployeDTO fiche) throws IOException {
        return genererExcelFicheEmploye(PointageDocumentData.from(fiche));
    }
    public byte[] genererExcelFicheEmploye(PointageDocumentData data) throws IOException {
        try (InputStream input = new ClassPathResource("templates/mensuel/MODELE_POINTAGE_EXCEL.xlsx").getInputStream();
             Workbook workbook = new XSSFWorkbook(input); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.getSheetAt(0);
            var fiche = data.fiche();
            replace(sheet, "{{NOM}}", PointageDocumentData.value(fiche.getNomComplet()));
            replace(sheet, "{{MATRICULE}}", PointageDocumentData.value(fiche.getMatricule()));
            replace(sheet, "{{FONCTION}}", PointageDocumentData.value(fiche.getFonction()));
            replace(sheet, "{{SITE}}", PointageDocumentData.value(fiche.getSiteNom()));
            replace(sheet, "{{CATEGORIE}}", PointageDocumentData.value(fiche.getCategorie()));
            replace(sheet, "{{PERIODE}}", PointageDocumentData.value(fiche.getDateDebut()) + " → " + PointageDocumentData.value(fiche.getDateFin()));
            replace(sheet, "{{PROVENANCE}}", data.note());
            List<String> codes = journalCodes(data);
            int n = codes.size(), columns = Math.max(12, 10 + n);
            int start = expand(sheet, "{{JOURNAL}}", 32, PointageDocumentData.days(fiche).size());
            Row header = sheet.getRow(start - 1);
            for (int col = 9; col < columns; col++) set(header, col, null);
            for (int i = 0; i < n; i++) set(header,9+i,codes.get(i) + " (unité)");
            set(header,9+n,"Observation");
            int index = 0;
            for (var day : PointageDocumentData.days(fiche)) {
                Row row = sheet.getRow(start + index++);
                clear(row);
                set(row,0,day.getDateComplete()); set(row,1,day.getJour()); set(row,2,status(day.getStatut()));
                set(row,3,day.getEntree()); set(row,4,day.getSortiePause()); set(row,5,day.getRetourPause()); set(row,6,day.getSortie());
                set(row,7,day.getTotalHeuresDecimal()); set(row,8,day.getMinutesRetard());
                for (int i=0;i<n;i++) { var item=day.getIndemnites().get(codes.get(i)); set(row,9+i,item==null || !item.accordee() ? BigDecimal.ZERO : item.quantite()); }
                set(row,9+n,day.getObservation());
                for (int col=7;col<9+n;col++) row.getCell(col).setCellStyle(numericStyle(workbook,row.getCell(col).getCellStyle(),"0.00"));
            }
            int total = locate(sheet,"TOTAL JOURNAL");
            Row totalRow=sheet.getRow(total);
            for(int col=7;col<columns;col++)set(totalRow,col,null);
            for(int col=7;col<9+n;col++){
                Cell cell=cell(totalRow,col); String letter=org.apache.poi.ss.util.CellReference.convertNumToColString(col);
                cell.setCellFormula("SUM("+letter+(start+1)+":"+letter+total+")");
                cell.setCellStyle(numericStyle(workbook,header.getCell(7).getCellStyle(),"0.00"));
            }
            List<PointageTronconSemaineDTO> weeks=fiche.getBilansHebdo()==null ? List.of() : fiche.getBilansHebdo();
            int weekStart=expand(sheet,"{{SEMAINES}}",6,weeks.size());
            int wi=0;
            for(var w:weeks){Row row=sheet.getRow(weekStart+wi++);clear(row);Object[] vals={"S"+w.getNumeroSemaine(),w.getDebutSemaine(),w.getFinSemaine(),w.getSeuilHeuresTroncon(),w.getTotalHeuresTravaillees(),Math.round(w.getHeuresRetard()*60),w.getHeuresCompensees(),w.getHeuresDeficit(),w.getHeuresHs30(),w.getHeuresHs50()};for(int i=0;i<vals.length;i++)set(row,i,vals[i]);}
            if(data.versionSnapshot()!=null) replace(sheet,"BILAN HEBDOMADAIRE","BILAN HEBDOMADAIRE (RECONSTITUÉ)");
            Row balance=sheet.getRow(locate(sheet,"{{BILAN}}"));clear(balance);
            for(int i=0;i<data.bilan().size();i++)set(balance,i,data.bilan().get(i));
            int allowanceStart=expand(sheet,"{{INDEMNITES}}",8,data.indemnites().size());
            int ai=0;
            for(var a:data.indemnites()){Row row=sheet.getRow(allowanceStart+ai++);clear(row);set(row,0,a.code());set(row,1,a.libelle());set(row,5,a.quantite());set(row,6,a.montant());set(row,7,a.periodicite());}
            int adjustmentStart=expand(sheet,"{{AJUSTEMENTS}}",6,data.ajustements().size());
            int ji=0;
            for(var a:data.ajustements()){Row row=sheet.getRow(adjustmentStart+ji++);clear(row);set(row,0,PointageDocumentData.hours(a.minutes()));set(row,1,a.motif());set(row,8,a.date());set(row,9,a.auteur());set(row,10,a.statut());row.setHeightInPoints(Math.max(38,Math.min(400,16+12*(float)Math.ceil(PointageDocumentData.value(a.motif()).length()/85.0))));}
            if(data.ajustements().isEmpty())set(sheet.getRow(adjustmentStart),1,"Aucune régularisation DAF");
            // Les placeholders de lignes non utilisées sont effacés ; les autres sont une erreur de modèle.
            for(Row row:sheet)for(Cell cell:row)if(cell.getCellType()==CellType.STRING){
                String text=cell.getStringCellValue();
                if(Set.of("{{JOURNAL}}","{{SEMAINES}}","{{INDEMNITES}}","{{AJUSTEMENTS}}").contains(text))cell.setBlank();
                else if(text.contains("{{"))throw new IOException("POINTAGE_TEMPLATE_INVALID : variable non remplacée " + text);
            }
            for(int col=0;col<columns;col++)if(sheet.getColumnWidth(col)<2700)sheet.setColumnWidth(col,2700);
            sheet.createFreezePane(3,7); sheet.setRepeatingRows(new CellRangeAddress(0,6,-1,-1));
            sheet.getPrintSetup().setLandscape(true);sheet.getPrintSetup().setPaperSize(PrintSetup.A4_PAPERSIZE);sheet.getPrintSetup().setFitWidth((short)1);sheet.getPrintSetup().setFitHeight((short)0);sheet.setFitToPage(true);
            workbook.setPrintArea(0,0,columns-1,0,sheet.getLastRowNum());
            workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
            workbook.write(output);return output.toByteArray();
        }
    }
    static List<String> journalCodes(PointageDocumentData data) {
        Set<String> codes=new TreeSet<>(data.fiche().getIndemnitesDisponibles().keySet());
        for(var day:PointageDocumentData.days(data.fiche()))codes.addAll(day.getIndemnites().keySet());
        return List.copyOf(codes);
    }
    static String status(String code){return formatStatutLabel(code);}
    private static Cell cell(Row row,int col){Cell c=row.getCell(col);return c==null?row.createCell(col):c;}
    private static void set(Row row,int col,Object value){Cell c=cell(row,col);if(value==null)c.setCellValue("—");else if(value instanceof Number n)c.setCellValue(n.doubleValue());else c.setCellValue(value.toString());}
    private static void clear(Row row){for(Cell cell:row)cell.setBlank();}
    private static CellStyle numericStyle(Workbook w,CellStyle base,String format){CellStyle style=w.createCellStyle();style.cloneStyleFrom(base);style.setDataFormat(w.createDataFormat().getFormat(format));return style;}
    private static int locate(Sheet s,String text){for(Row row:s)for(Cell c:row)if(c.getCellType()==CellType.STRING&&text.equals(c.getStringCellValue()))return row.getRowNum();throw new IllegalStateException("POINTAGE_TEMPLATE_INVALID : repère absent " + text);}
    private static void replace(Sheet s,String marker,String value){boolean found=false;for(Row row:s)for(Cell c:row)if(c.getCellType()==CellType.STRING&&marker.equals(c.getStringCellValue())){c.setCellValue(value);found=true;}if(!found)throw new IllegalStateException("POINTAGE_TEMPLATE_INVALID : " + marker);}
    private static int expand(Sheet s,String marker,int capacity,int count){
        int start=locate(s,marker),extra=Math.max(0,count-capacity);
        if(extra>0){
            Row model=s.getRow(start);Map<Integer,CellStyle> styles=new HashMap<>();for(Cell c:model)styles.put(c.getColumnIndex(),c.getCellStyle());float height=model.getHeightInPoints();
            List<CellRangeAddress> merges=new ArrayList<>();for(var merge:s.getMergedRegions())if(merge.getFirstRow()==start&&merge.getLastRow()==start)merges.add(merge.copy());
            s.shiftRows(start+capacity,s.getLastRowNum(),extra,true,false);
            for(int i=0;i<extra;i++){Row row=s.createRow(start+capacity+i);row.setHeightInPoints(height);styles.forEach((col,style)->row.createCell(col).setCellStyle(style));for(var m:merges)s.addMergedRegion(new CellRangeAddress(row.getRowNum(),row.getRowNum(),m.getFirstColumn(),m.getLastColumn()));}
        }
        return start;
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

    private static String formatStatutLabel(String statut) {
        if (statut == null || statut.trim().isEmpty()) return "Présent";
        switch (statut.toUpperCase()) {
            case "CONGE":
            case "CONGE_PAYE":
                return "Congé payé";
            case "FERIE_CHOME":
                return "Férié chômé";
            case "REPOS":
                return "Repos";
            case "ABSENT":
            case "ABSENCE_NON_JUSTIFIEE":
            case "ABSENCE_JUSTIFIEE":
                return "Absent";
            case "DISPENSE_POINTAGE":
                return "Dispensé";
            case "DEMI_JOURNEE":
                return "Demi-journée";
            case "SAMEDI_TRAVAILLE":
                return "Samedi travaillé";
            case "DIMANCHE_TRAVAILLE":
                return "Dimanche (+40%)";
            case "FERIE_TRAVAILLE":
                return "Férié (+100%)";
            case "RETARD":
                return "Retard";
            case "NON_CONCERNE":
                return "Non concerné";
            case "PLANIFIE":
                return "Planifié";
            case "NON_ECHU":
                return "—";
            case "PRESENT":
            default:
                return "Présent";
        }
    }

}
