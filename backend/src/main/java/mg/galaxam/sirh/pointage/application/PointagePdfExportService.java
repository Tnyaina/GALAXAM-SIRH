package mg.galaxam.sirh.pointage.application;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.io.*;
import java.util.*;
import java.math.BigDecimal;

/** Remplit les deux pages du modèle ; les débordements répètent les mêmes pages. */
@Service
public class PointagePdfExportService {
    public byte[] generer(PointageDocumentData data) throws IOException {
        byte[] template;
        try(var in=new ClassPathResource("templates/pdf/MODELE_FICHE_POINTAGE.pdf").getInputStream()){template=in.readAllBytes();}
        try(var result=new PDDocument();var out=new ByteArrayOutputStream()){
            var days=PointageDocumentData.days(data.fiche());
            var codes=PointageExcelExportService.journalCodes(data);
            int groups=Math.max(1,(codes.size()+2)/3);
            for(int offset=0;offset<Math.max(1,days.size());offset+=32)for(int group=0;group<groups;group++){
                Map<String,String> values=common(data,"j_");
                for(int q=0;q<3;q++)if(group*3+q<codes.size())values.put("j_allowance_"+q,codes.get(group*3+q)+" (unité)");
                for(int r=0;r<32&&offset+r<days.size();r++){
                    var d=days.get(offset+r);String prefix="j_day_"+r+"_";
                    Object[] v={d.getDateComplete(),PointageExcelExportService.status(d.getStatut()),d.getEntree(),d.getSortiePause(),d.getRetourPause(),d.getSortie(),d.getTotalHeuresDecimal(),d.getMinutesRetard()};
                    String[] keys={"date","statut","em","sm","ea","sa","heures","retard"};
                    for(int k=0;k<keys.length;k++)values.put(prefix+keys[k],show(v[k]));
                    for(int q=0;q<3;q++)if(group*3+q<codes.size()){
                        var a=d.getIndemnites().get(codes.get(group*3+q));
                        values.put(prefix+"q"+q,show(a==null||!a.accordee()?BigDecimal.ZERO:a.quantite()));
                    }
                    values.put(prefix+"observation",d.getObservation()==null||d.getObservation().isBlank()?"":"Note "+(offset+r+1));
                }
                values.put("j_note",data.note()+"\nQuantités journalières stockées ; les montants figurent au bilan. Groupe d’indemnités "+(group+1)+"/"+groups+".");
                append(result,template,0,values);
            }
            var weeks=data.fiche().getBilansHebdo()==null?List.<mg.galaxam.sirh.pointage.api.dto.PointageTronconSemaineDTO>of():data.fiche().getBilansHebdo();
            // Découpe de présentation : chaque fragment conserve la totalité du motif.
            List<PointageDocumentData.Ajustement> adjustments=new ArrayList<>();
            var notes=new ArrayList<>(data.ajustements());
            for(int i=0;i<days.size();i++){var day=days.get(i);
                if(day.getObservation()!=null&&!day.getObservation().isBlank())
                    notes.add(new PointageDocumentData.Ajustement(null,"Note "+(i+1)+" : "+day.getObservation(),day.getDateComplete(),"Journal","OBSERVATION"));
            }
            for(var a:notes){
                String motif=PointageDocumentData.value(a.motif());
                for(int offset=0;offset<motif.length();offset+=180)adjustments.add(new PointageDocumentData.Ajustement(
                    offset==0?a.minutes():null,motif.substring(offset,Math.min(offset+180,motif.length())),a.date(),a.auteur(),a.statut()));
            }
            int pages=Math.max(1,Math.max((weeks.size()+5)/6,Math.max((data.indemnites().size()+4)/5,(adjustments.size()+1)/2)));
            for(int page=0;page<pages;page++){
                var values=common(data,"r_");
                for(int r=0;r<6&&page*6+r<weeks.size();r++){
                    var w=weeks.get(page*6+r);String prefix="r_week_"+r+"_";
                    Object[] v={w.getDebutSemaine()+" / "+w.getFinSemaine(),w.getSeuilHeuresTroncon(),w.getTotalHeuresTravaillees(),Math.round(w.getHeuresRetard()*60),w.getHeuresCompensees(),w.getHeuresDeficit(),w.getHeuresHs30(),w.getHeuresHs50()};
                    String[] keys={"dates","objectif","travaille","retard","compense","deficit","hs30","hs50"};
                    for(int k=0;k<keys.length;k++)values.put(prefix+keys[k],show(v[k]));
                }
                int[] indices={0,1,2,3,4,5,6,7};
                for(int k=0;k<indices.length;k++)values.put("r_final_"+k,show(data.bilan().get(indices[k]))+" h");
                for(int r=0;r<5&&page*5+r<data.indemnites().size();r++){
                    var a=data.indemnites().get(page*5+r);String p="r_allow_"+r+"_";
                    values.put(p+"label",a.libelle()+" / "+PointageDocumentData.value(a.periodicite()));
                    values.put(p+"quantite",show(a.quantite()));values.put(p+"montant",show(a.montant()));
                }
                for(int r=0;r<2&&page*2+r<adjustments.size();r++){
                    var a=adjustments.get(page*2+r);String p="r_adj_"+r+"_";
                    values.put(p+"duree",a.minutes()==null?"suite":show(PointageDocumentData.hours(a.minutes())));
                    values.put(p+"motif",a.motif());values.put(p+"auteur",show(a.date())+"\n"+show(a.auteur())+" / "+a.statut());
                }
                values.put("r_note",data.note()+"\nHS50 avant : "+show(data.bilan().get(2))+" h · Nuit : "+show(data.bilan().get(8))+" h · Dimanche : "+show(data.bilan().get(9))+" h · Férié : "+show(data.bilan().get(10))+" h");
                append(result,template,1,values);
            }
            result.save(out);return out.toByteArray();
        }
    }
    private static Map<String,String> common(PointageDocumentData data,String prefix){
        var f=data.fiche();Map<String,String> v=new HashMap<>();
        v.put(prefix+"reference",show(f.getMatricule())+" · "+show(f.getCodePeriode()));
        v.put(prefix+"edition","Édité le "+data.edition());v.put(prefix+"nom",show(f.getNomComplet()));
        v.put(prefix+"matricule",show(f.getMatricule()));v.put(prefix+"affectation",show(f.getFonction())+" / "+show(f.getSiteNom())+" / "+show(f.getCategorie()));
        v.put(prefix+"periode",show(f.getLibellePeriode())+" · "+show(f.getDateDebut())+" -> "+show(f.getDateFin()));
        v.put(prefix+"source",data.provenance());return v;
    }
    private static String show(Object v){return v instanceof Number n?PointageDocumentData.number(new BigDecimal(n.toString())):PointageDocumentData.value(v==null?null:v.toString());}
    /** Mise en page selon le nombre réel de colonnes et de lignes, sans calcul RH. */
    private static void adaptJournal(PDDocument document,Map<String,String> values)throws IOException {
        var page=document.getPage(0);var form=document.getDocumentCatalog().getAcroForm();
        int count=0, rows=0;
        while(values.containsKey("j_allowance_"+count))count++;
        while(values.containsKey("j_day_"+rows+"_date"))rows++;
        float width=page.getMediaBox().getWidth()-60, height=page.getMediaBox().getHeight();
        float[] fixed={48,115,43,43,43,43,53,47};
        float available=width;for(float w:fixed)available-=w;
        float allowance=count==0?0:Math.min(90,(available-110)/count);
        var widths=new ArrayList<Float>();for(float w:fixed)widths.add(w);
        for(int i=0;i<count;i++)widths.add(allowance);widths.add(available-count*allowance);
        var keys=new ArrayList<>(List.of("date","statut","em","sm","ea","sa","heures","retard"));
        for(int i=0;i<count;i++)keys.add("q"+i);keys.add("observation");
        var labels=new ArrayList<>(List.of("Date","Statut","Entrée M","Sortie M","Entrée AM","Sortie AM","Heures","Retard"));
        for(int i=0;i<count;i++)labels.add("");labels.add("Observation");
        Set<String> removed=new HashSet<>();
        for(int q=count;q<3;q++)removed.add("j_allowance_"+q);
        for(int r=0;r<32;r++)for(String key:List.of("date","statut","em","sm","ea","sa","heures","retard","q0","q1","q2","observation"))
            if(r>=rows || key.startsWith("q")&&Integer.parseInt(key.substring(1))>=count)removed.add("j_day_"+r+"_"+key);
        for(String name:removed){var field=form.getField(name);if(field!=null)page.getAnnotations().removeAll(field.getWidgets());}
        form.setFields(new ArrayList<>(form.getFields().stream().filter(f->!removed.contains(f.getPartialName())).toList()));
        float y=height-178;
        try(var stream=new org.apache.pdfbox.pdmodel.PDPageContentStream(document,page,
                org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND,true,true)){
            stream.setNonStrokingColor(java.awt.Color.WHITE);stream.addRect(30,60,width,360);stream.fill();
            stream.setNonStrokingColor(new java.awt.Color(234,240,235));stream.addRect(30,y-15,width,17);stream.fill();
            float x=30;
            for(int col=0;col<keys.size();col++){
                stream.beginText();stream.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(
                        org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA_BOLD),7);
                stream.setNonStrokingColor(new java.awt.Color(48,77,63));stream.newLineAtOffset(x+3,y-9);
                stream.showText(labels.get(col));stream.endText();
                for(int r=0;r<rows;r++){
                    float cy=y-17-(r+1)*10.6f;
                    stream.setStrokingColor(new java.awt.Color(203,214,206));stream.addRect(x,cy,widths.get(col),10.6f);stream.stroke();
                    var field=form.getField("j_day_"+r+"_"+keys.get(col));
                    if(field==null)throw new IOException("POINTAGE_TEMPLATE_INVALID : cellule du journal absente");
                    field.getWidgets().get(0).setRectangle(new org.apache.pdfbox.pdmodel.common.PDRectangle(x+2,cy+1,widths.get(col)-4,8.6f));
                }
                if(col>=8&&col<8+count)form.getField("j_allowance_"+(col-8)).getWidgets().get(0)
                        .setRectangle(new org.apache.pdfbox.pdmodel.common.PDRectangle(x+2,height-187,widths.get(col)-4,12));
                x+=widths.get(col);
            }
        }
    }

    /** Retire les cases inutilisées tout en conservant les rubriques du modèle. */
    private static void trimRecap(PDDocument document,Map<String,String> values)throws IOException {
        var form=document.getDocumentCatalog().getAcroForm();var page=document.getPage(1);
        String[] prefixes={"r_week_","r_allow_","r_adj_"};
        String[] firstKeys={"dates","label","duree"};int[] capacities={6,5,2};
        float[] ys={page.getMediaBox().getHeight()-177,224,103};float[] heights={13,13,24};
        var removed=new HashSet<String>();
        try(var stream=new org.apache.pdfbox.pdmodel.PDPageContentStream(document,page,
                org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND,true,true)) {
            for(int section=0;section<prefixes.length;section++) {
                int used=0;while(values.containsKey(prefixes[section]+used+"_"+firstKeys[section]))used++;
                if(used==capacities[section])continue;
                float bottom=ys[section]-17-capacities[section]*heights[section];
                stream.setNonStrokingColor(java.awt.Color.WHITE);
                stream.addRect(29.5f,bottom-0.5f,page.getMediaBox().getWidth()-59,(capacities[section]-used)*heights[section]+1);stream.fill();
                for(var field:form.getFields())for(int row=used;row<capacities[section];row++)
                    if(field.getPartialName().startsWith(prefixes[section]+row+"_")) {
                        removed.add(field.getPartialName());page.getAnnotations().removeAll(field.getWidgets());
                    }
            }
        }
        form.setFields(new ArrayList<>(form.getFields().stream().filter(f->!removed.contains(f.getPartialName())).toList()));
    }

    private static void append(PDDocument target,byte[] template,int page,Map<String,String> values)throws IOException{
        try(var source=Loader.loadPDF(template)){
            var form=source.getDocumentCatalog().getAcroForm();
            if(form==null)throw new IOException("POINTAGE_TEMPLATE_INVALID : formulaire PDF absent");
            for(PDField f:form.getFieldTree()){
                f.setValue("");
                if(f instanceof org.apache.pdfbox.pdmodel.interactive.form.PDTextField text && !text.isMultiline())
                    text.setDefaultAppearance("/Helv 0 Tf 0.188 0.302 0.247 rg");
            }
            if(page==0)adaptJournal(source,values);
            else trimRecap(source,values);
            for(var entry:values.entrySet()){
                var field=form.getField(entry.getKey());
                if(field==null)throw new IOException("POINTAGE_TEMPLATE_INVALID : champ absent "+entry.getKey());
                field.setValue(entry.getValue().replace('−','-').replace('’','\'').replace("→","->"));
            }
            // Conserve le formulaire et ses apparences ; valeurs calculées en lecture seule.
            String prefix=page==0?"j_":"r_";
            var fields=new ArrayList<>(form.getFields().stream().filter(f->f.getPartialName().startsWith(prefix)).toList());
            form.setFields(fields);
            for(var field:fields){field.setReadOnly(true);field.setPartialName("p"+target.getNumberOfPages()+"_"+field.getPartialName());}
            source.removePage(1-page);
            new PDFMergerUtility().appendDocument(target,source);
        }
    }
}
