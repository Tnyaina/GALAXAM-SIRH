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

    @Test void genereFicheAgentYPourVisualisation() throws Exception {
        var f = new FichePointageEmployeDTO();
        f.setNomComplet("AGENT Y TNR"); f.setMatricule("199"); f.setFonction("Assistante Admin");
        f.setSiteNom("Antananarivo - Siège"); f.setCategorie("Employé");
        f.setDateDebut("2026-07-26"); f.setDateFin("2026-08-25");

        Object[][] rawDays = {
            {"2026-07-26", "Dim.", "REPOS", null, null, null, null, null, null, null},
            {"2026-07-27", "Lun.", "PRESENT", "08:24", "12:30", "13:30", "18:20", 8.93, 24, "Retard réel confirmé"},
            {"2026-07-28", "Mar.", "PRESENT", "08:25", "12:30", "13:30", "17:50", 8.42, 25, "Retard réel confirmé"},
            {"2026-07-29", "Mer.", "PRESENT", "08:13", "12:38", "13:43", "17:18", 8.00, 18, "Retard réel confirmé"},
            {"2026-07-30", "Jeu.", "PRESENT", "08:52", "12:30", "13:30", "17:15", 7.38, 52, "Retard réel confirmé"},
            {"2026-07-31", "Ven.", "PRESENT", "08:25", "12:30", "13:30", "17:00", 7.58, 25, "Retard réel confirmé"},
            {"2026-08-01", "Sam.", "REPOS", null, null, null, null, null, null, null},
            {"2026-08-02", "Dim.", "REPOS", null, null, null, null, null, null, null},
            {"2026-08-03", "Lun.", "PRESENT", "08:28", "12:41", "13:48", "17:00", 7.42, 35, "Retard réel confirmé"},
            {"2026-08-04", "Mar.", "PRESENT", "08:37", "12:30", "13:30", "17:15", 7.63, 37, "Retard réel confirmé"},
            {"2026-08-05", "Mer.", "PRESENT", "08:27", "12:30", "13:30", "17:10", 7.72, 27, "Retard réel confirmé"},
            {"2026-08-06", "Jeu.", "PRESENT", "08:24", "12:30", "13:30", "17:00", 7.60, 24, "Retard réel confirmé"},
            {"2026-08-07", "Ven.", "CONGE", null, null, null, null, 0.0, null, "Congé lery"},
            {"2026-08-08", "Sam.", "CONGE", null, null, null, null, null, null, null},
            {"2026-08-09", "Dim.", "CONGE", null, null, null, null, null, null, null},
            {"2026-08-10", "Lun.", "PRESENT", "08:28", "12:30", "13:30", "18:14", 8.77, 28, "Retard réel confirmé"},
            {"2026-08-11", "Mar.", "PRESENT", "08:28", "12:30", "13:30", "19:00", 9.53, 28, "io mhts ka"},
            {"2026-08-12", "Mer.", "PRESENT", "08:26", "12:30", "13:30", "17:00", 7.57, 26, "Retard réel confirmé"},
            {"2026-08-13", "Jeu.", "PRESENT", "08:30", "12:30", "13:30", "17:00", 7.50, 30, "Retard réel confirmé"},
            {"2026-08-14", "Ven.", "PRESENT", "08:30", "12:30", "13:30", "17:29", 7.98, 30, "Retard réel confirmé"},
            {"2026-08-15", "Sam.", "REPOS", null, null, null, null, null, null, null},
            {"2026-08-16", "Dim.", "REPOS", null, null, null, null, null, null, null},
            {"2026-08-17", "Lun.", "PRESENT", "07:45", "12:30", "13:30", "17:31", 8.77, null, null},
            {"2026-08-18", "Mar.", "PRESENT", "08:10", "12:30", "13:30", "17:31", 8.35, 10, "Retard réel confirmé"},
            {"2026-08-19", "Mer.", "PRESENT", "08:22", "12:30", "13:30", "17:58", 8.60, 22, "Retard réel confirmé"},
            {"2026-08-20", "Jeu.", "PRESENT", "08:34", "12:30", "13:30", "18:30", 8.93, 34, "Retard réel confirmé"},
            {"2026-08-21", "Ven.", "PRESENT", "08:10", "12:30", "13:30", "17:00", 7.83, 10, "Retard réel confirmé"},
            {"2026-08-22", "Sam.", "REPOS", null, null, null, null, null, null, null},
            {"2026-08-23", "Dim.", "REPOS", null, null, null, null, null, null, null},
            {"2026-08-24", "Lun.", "PRESENT", "08:00", "12:30", "13:30", "17:00", 8.00, null, null},
            {"2026-08-25", "Mar.", "PRESENT", "08:11", "12:30", "13:30", "17:00", 7.82, 11, "Diso"}
        };
        var rows = new ArrayList<PointageJourDTO>();
        for (var d : rawDays) {
            var row = new PointageJourDTO();
            row.setDateComplete((String) d[0]);
            row.setJour((String) d[1]);
            row.setStatut((String) d[2]);
            row.setEntree((String) d[3]);
            row.setSortiePause((String) d[4]);
            row.setRetourPause((String) d[5]);
            row.setSortie((String) d[6]);
            row.setTotalHeuresDecimal(d[7] == null ? null : ((Number) d[7]).doubleValue());
            row.setMinutesRetard(d[8] == null ? null : ((Number) d[8]).intValue());
            row.setObservation((String) d[9]);
            rows.add(row);
        }
        f.setPointagesData(rows);

        var w1 = new PointageTronconSemaineDTO(); w1.setNumeroSemaine(1); w1.setDebutSemaine("2026-07-26"); w1.setFinSemaine("2026-07-26"); w1.setNbJoursTroncon(0); w1.setTotalHeuresTravaillees(0); w1.setSeuilHeuresTroncon(0);
        var w2 = new PointageTronconSemaineDTO(); w2.setNumeroSemaine(2); w2.setDebutSemaine("2026-07-27"); w2.setFinSemaine("2026-08-02"); w2.setNbJoursTroncon(5); w2.setTotalHeuresTravaillees(40.32); w2.setSeuilHeuresTroncon(40); w2.setHeuresHs30(0.32);
        var w3 = new PointageTronconSemaineDTO(); w3.setNumeroSemaine(3); w3.setDebutSemaine("2026-08-03"); w3.setFinSemaine("2026-08-09"); w3.setNbJoursTroncon(5); w3.setTotalHeuresTravaillees(30.37); w3.setSeuilHeuresTroncon(32); w3.setHeuresDeficit(1.63);
        var w4 = new PointageTronconSemaineDTO(); w4.setNumeroSemaine(4); w4.setDebutSemaine("2026-08-10"); w4.setFinSemaine("2026-08-16"); w4.setNbJoursTroncon(5); w4.setTotalHeuresTravaillees(41.35); w4.setSeuilHeuresTroncon(40); w4.setHeuresHs30(1.35);
        var w5 = new PointageTronconSemaineDTO(); w5.setNumeroSemaine(5); w5.setDebutSemaine("2026-08-17"); w5.setFinSemaine("2026-08-23"); w5.setNbJoursTroncon(5); w5.setTotalHeuresTravaillees(42.48); w5.setSeuilHeuresTroncon(40); w5.setHeuresHs30(2.48);
        var w6 = new PointageTronconSemaineDTO(); w6.setNumeroSemaine(6); w6.setDebutSemaine("2026-08-24"); w6.setFinSemaine("2026-08-25"); w6.setNbJoursTroncon(2); w6.setTotalHeuresTravaillees(15.82); w6.setSeuilHeuresTroncon(16); w6.setHeuresDeficit(0.18);
        f.setBilansHebdo(List.of(w1, w2, w3, w4, w5, w6));

        var solde = new PointageSyntheseMensuelleDTO();
        solde.setMinutesHs30Hebdo(249); // 4.15h
        solde.setMinutesDeficitHebdo(109); // 1.82h
        solde.setMinutesAjustementPeriode(-160); // -2.67h
        solde.setMinutesSoldePeriode(-20); // -0.33h
        solde.setMinutesAbsenceFinale(20);
        solde.setMinutesHs30Finales(0);
        solde.setMinutesHs50Finales(0);
        f.setSoldePeriode(solde);

        var indemnites = List.of(
                new PointageDocumentData.Indemnite("IND_DEP", "Indemnité de Déplacement", "JOURNALIER", BigDecimal.ONE, new BigDecimal("28364")),
                new PointageDocumentData.Indemnite("IND_REP", "Indemnité de Repas", "JOURNALIER", BigDecimal.ONE, new BigDecimal("44000"))
        );
        var ajustements = List.of(
                new PointageDocumentData.Ajustement(-160, "Régularisation validée", "2026-09-29", "Admin", "VALIDE")
        );
        var bilan = List.of(
                new BigDecimal("1.82"), new BigDecimal("4.15"), BigDecimal.ZERO,
                new BigDecimal("-2.67"), new BigDecimal("-0.33"), new BigDecimal("0.33"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        );

        var data = new PointageDocumentData(f, "30/09/2026 15:32", "Validé", 1, indemnites, ajustements, bilan);
        byte[] pdfBytes = new PointagePdfExportService().generer(data);
        java.nio.file.Files.write(java.nio.file.Path.of("/Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/Pointage_AGENT_Y_199_Aout_2026.pdf"), pdfBytes);

        byte[] excelBytes = new PointageExcelExportService().genererExcelFicheEmploye(data);
        java.nio.file.Files.write(java.nio.file.Path.of("/Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/Pointage_AGENT_Y_199_Aout_2026.xlsx"), excelBytes);
    }
}
