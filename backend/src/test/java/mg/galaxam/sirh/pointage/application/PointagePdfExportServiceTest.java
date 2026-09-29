package mg.galaxam.sirh.pointage.application;

import mg.galaxam.sirh.pointage.api.dto.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PointagePdfExportServiceTest {
    static PointageDocumentData example(int days, int allowances) {
        var f = new FichePointageEmployeDTO();
        f.setNomComplet("Agent Émilie"); f.setMatricule("0001"); f.setFonction("Agent");
        f.setSiteNom("Toamasina"); f.setCategorie("Agent");
        f.setDateDebut("2026-09-01"); f.setDateFin("2026-09-30");
        var rows = new ArrayList<PointageJourDTO>();
        var codes = new LinkedHashMap<String, FichePointageEmployeDTO.IndemniteFicheDTO>();
        var amounts = new LinkedHashMap<String, BigDecimal>();
        for (int i = 0; i < allowances; i++) {
            codes.put("PRIME_" + i, new FichePointageEmployeDTO.IndemniteFicheDTO("PRIME_" + i, "JOURNALIER"));
            amounts.put("PRIME_" + i, new BigDecimal("1304"));
        }
        for (int i = 0; i < days; i++) {
            var day = new PointageJourDTO();
            day.setDateComplete(LocalDate.of(2026, 9, 1).plusDays(i).toString());
            day.setStatut("PRESENT"); day.setTotalHeuresDecimal(8.0);
            day.setObservation("Pointage vérifié");
            var map = new LinkedHashMap<String, PointageJourDTO.IndemniteJourDTO>();
            for (String code : codes.keySet()) map.put(code, new PointageJourDTO.IndemniteJourDTO(code, true, new BigDecimal("1304"), new BigDecimal("0.5")));
            day.setIndemnites(map); rows.add(day);
        }
        f.setPointagesData(rows); f.setIndemnitesDisponibles(codes); f.setTotauxIndemnites(amounts);
        return PointageDocumentData.from(f);
    }

    @Test void trenteEtUnJoursRestentSurDeuxPagesSansBilanRepete() throws Exception {
        var data = example(31, 2);
        data.fiche().setTotauxIndemnites(Map.of("PRIME_0", new BigDecimal("28363.60"), "PRIME_1", new BigDecimal("44000")));
        data = PointageDocumentData.from(data.fiche());
        try (var pdf = Loader.loadPDF(new PointagePdfExportService().generer(data))) {
            String content = new PDFTextStripper().getText(pdf);
            assertEquals(2, pdf.getNumberOfPages());
            assertTrue(content.contains("01/09/2026"));
            assertTrue(content.contains("01/10/2026"));
            assertTrue(content.contains("08h00 (8.00h)"));
            assertTrue(content.contains("28 364 Ar"));
            assertEquals(1, content.split("RAPPROCHEMENT DU SOLDE", -1).length - 1);
            assertFalse(content.contains("validation DAF"));
        }
    }

    @Test void afficheSyntheseEtSeulementAjustementsAppliques() throws Exception {
        var data = example(2, 0);
        var week = new PointageTronconSemaineDTO();
        week.setDebutSemaine("2026-09-01"); week.setFinSemaine("2026-09-07");
        week.setSeuilHeuresTroncon(40); week.setTotalHeuresTravaillees(39.5);
        data.fiche().setBilansHebdo(List.of(week));
        data = new PointageDocumentData(data.fiche(), data.edition(), data.provenance(), null, data.indemnites(),
                List.of(new PointageDocumentData.Ajustement(-90, "Correction validée du solde", "2026-09-08", "DAF", "VALIDE"),
                        new PointageDocumentData.Ajustement(60, "Correction annulée", "2026-09-09", "DAF", "ANNULE")), data.bilan());
        try (var pdf = Loader.loadPDF(new PointagePdfExportService().generer(data))) {
            String content = new PDFTextStripper().getText(pdf);
            assertTrue(content.contains("SYNTHÈSE HEBDOMADAIRE"));
            assertTrue(content.contains("01/09/2026 - 07/09/2026"));
            assertTrue(content.contains("40h00"));
            assertTrue(content.contains("(40.00h)"));
            assertTrue(content.contains("39h30"));
            assertTrue(content.contains("(39.50h)"));
            assertTrue(content.contains("Correction validée du solde"));
            assertFalse(content.contains("Correction annulée"));
        }
    }

    @Test void montantArrondiSeulementPourLaPresentation() {
        assertEquals(new BigDecimal("28364"), PointageDocumentData.ariary(new BigDecimal("28363.60")));
        assertEquals(new BigDecimal("28363.60"), new BigDecimal("28363.60"));
    }
}
