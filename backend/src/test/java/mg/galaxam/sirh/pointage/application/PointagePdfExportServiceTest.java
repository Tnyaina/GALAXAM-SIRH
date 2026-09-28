package mg.galaxam.sirh.pointage.application;

import mg.galaxam.sirh.pointage.api.dto.*;
import org.junit.jupiter.api.Test;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PointagePdfExportServiceTest {
    static PointageDocumentData example(int days,int allowances) {
        var f=new FichePointageEmployeDTO();f.setNomComplet("Agent Émilie");f.setMatricule("0001");
        f.setFonction("Agent");f.setSiteNom("Toamasina");f.setCategorie("Agent");f.setDateDebut("2026-09-01");f.setDateFin("2026-09-30");
        var rows=new ArrayList<PointageJourDTO>();var codes=new LinkedHashMap<String,FichePointageEmployeDTO.IndemniteFicheDTO>();
        var amounts=new LinkedHashMap<String,BigDecimal>();
        for(int i=0;i<allowances;i++){codes.put("PRIME_"+i,new FichePointageEmployeDTO.IndemniteFicheDTO("PRIME_"+i,"JOURNALIER"));amounts.put("PRIME_"+i,new BigDecimal("1304"));}
        for(int i=0;i<days;i++){var d=new PointageJourDTO();d.setDateComplete(java.time.LocalDate.of(2026,9,1).plusDays(i).toString());d.setStatut("PRESENT");d.setTotalHeuresDecimal(8.0);
            var a=new LinkedHashMap<String,PointageJourDTO.IndemniteJourDTO>();for(String code:codes.keySet())a.put(code,new PointageJourDTO.IndemniteJourDTO(code,true,new BigDecimal("1304"),new BigDecimal("0.5")));d.setIndemnites(a);rows.add(d);}
        f.setPointagesData(rows);f.setIndemnitesDisponibles(codes);f.setTotauxIndemnites(amounts);
        var data=PointageDocumentData.from(f);
        return new PointageDocumentData(f,data.edition(),data.provenance(),null,data.indemnites(),
            List.of(new PointageDocumentData.Ajustement(120,"Régularisation autorisée par la DAF","2026-09-25","DAF","VALIDE")),
            Collections.nCopies(11,new BigDecimal("2")));
    }
    static String contents(org.apache.pdfbox.pdmodel.PDDocument pdf)throws Exception{
        var text=new StringBuilder(new PDFTextStripper().getText(pdf));
        assertNotNull(pdf.getDocumentCatalog().getAcroForm());
        for(var field:pdf.getDocumentCatalog().getAcroForm().getFieldTree()){assertTrue(field.isReadOnly());text.append("\n").append(field.getValueAsString());}
        return text.toString();
    }
    @Test void utiliseLeModeleAvecQuantitesMontantsEtBilanBackend() throws Exception {
        var bytes=new PointagePdfExportService().generer(example(2,2));
        try(var pdf=Loader.loadPDF(bytes)){
            assertEquals(2,pdf.getNumberOfPages());String text=contents(pdf);
            assertTrue(text.contains("Agent Émilie"));assertTrue(text.contains("0,50"));
            assertTrue(text.contains("1304,00"));assertTrue(text.contains("Régularisation autorisée"));
            assertTrue(text.contains("2,00 h"));assertFalse(text.contains("{{"));
        }
    }
    @Test void pagineSansPerdreLesIndemnitesEtMotifsLongs() throws Exception {
        var d=example(35,7);String motif="Correction détaillée ".repeat(90)+"FIN MOTIF";
        var data=new PointageDocumentData(d.fiche(),d.edition(),d.provenance(),null,d.indemnites(),
            List.of(new PointageDocumentData.Ajustement(-60,motif,"2026-09-25","DAF","VALIDE")),d.bilan());
        try(var pdf=Loader.loadPDF(new PointagePdfExportService().generer(data))){
            assertTrue(pdf.getNumberOfPages()>8);String text=contents(pdf);
            assertTrue(text.contains("PRIME_6"));assertTrue(text.contains("2026-10-05"));assertTrue(text.contains("FIN MOTIF"));
        }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints={0,1,2,3})
    void journalSansColonneNiLigneVideEtPeriodeEntreDeuxMois(int count) throws Exception {
        var data=example(30,count);var fiche=data.fiche();
        fiche.setDateDebut("2026-09-25");fiche.setDateFin("2026-10-24");fiche.setLibellePeriode("Période test");
        for(int i=0;i<30;i++)fiche.getPointagesData().get(i).setDateComplete(java.time.LocalDate.of(2026,9,25).plusDays(i).toString());
        try(var pdf=Loader.loadPDF(new PointagePdfExportService().generer(data))){
            var form=pdf.getDocumentCatalog().getAcroForm();
            assertTrue(form.getField("p0_j_periode").getValueAsString().contains("2026-09-25 -> 2026-10-24"));
            assertEquals("2026-10-24",form.getField("p0_j_day_29_date").getValueAsString());
            assertNull(form.getField("p0_j_day_30_date"));
            for(int q=count;q<3;q++)assertNull(form.getField("p0_j_day_0_q"+q));
            assertNotNull(form.getField("p0_j_day_0_observation"));
        }
    }
    @Test void quantiteAbsenteNeDevientPasUnMontantOuUnForfait() {
        var d=example(1,1);d.fiche().getPointagesData().get(0).setIndemnites(Map.of("PRIME_0",new PointageJourDTO.IndemniteJourDTO("PRIME_0",true,new BigDecimal("1304"))));
        assertNull(PointageDocumentData.from(d.fiche()).indemnites().get(0).quantite());
    }
}
