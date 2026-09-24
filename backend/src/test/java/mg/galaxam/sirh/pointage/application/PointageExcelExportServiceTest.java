package mg.galaxam.sirh.pointage.application;

import mg.galaxam.sirh.pointage.api.dto.FichePointageEmployeDTO;
import mg.galaxam.sirh.pointage.api.dto.PointageJourDTO;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PointageExcelExportServiceTest {

    @Test
    void exporteToutesLesIndemnitesParCodeSansColonneReservee() throws Exception {
        FichePointageEmployeDTO fiche = new FichePointageEmployeDTO();
        fiche.setMatricule("0001");
        fiche.setNomComplet("Employe Test");
        fiche.setSiteNom("Site Test");
        fiche.setFonction("Agent");
        fiche.setCategorie("TEST");
        fiche.setReglesAppliquees(Map.of("dureeHebdoLegale", "40"));
        fiche.setIndemnitesDisponibles(Map.of(
                "COMMUNICATION", new FichePointageEmployeDTO.IndemniteFicheDTO("COMMUNICATION", "MENSUEL_PRORATISE"),
                "REPAS", new FichePointageEmployeDTO.IndemniteFicheDTO("REPAS", "JOURNALIER")));

        PointageJourDTO jour = new PointageJourDTO();
        jour.setDateComplete("2026-09-24");
        jour.setJour("Jeu.");
        jour.setStatut("PRESENT");
        jour.setTotalHeuresDecimal(8.0);
        Map<String, PointageJourDTO.IndemniteJourDTO> indemnites = new LinkedHashMap<>();
        indemnites.put("COMMUNICATION", new PointageJourDTO.IndemniteJourDTO(
                "COMMUNICATION", true, new BigDecimal("1304")));
        indemnites.put("REPAS", new PointageJourDTO.IndemniteJourDTO(
                "REPAS", true, new BigDecimal("2000")));
        jour.setIndemnites(indemnites);
        fiche.setPointagesData(List.of(jour));

        byte[] contenu = new PointageExcelExportService(null, null).genererExcelFicheEmploye(fiche);
        try (XSSFWorkbook classeur = new XSSFWorkbook(new ByteArrayInputStream(contenu))) {
            Sheet feuille = classeur.getSheetAt(0);
            Row entetes = null;
            for (Row ligne : feuille) {
                if (ligne.getCell(0) != null && "Date".equals(ligne.getCell(0).getStringCellValue())) {
                    entetes = ligne;
                    break;
                }
            }
            assertNotNull(entetes);
            assertEquals(13, entetes.getLastCellNum());
            assertEquals("COMMUNICATION (mensuel proratise) (Ar)", entetes.getCell(11).getStringCellValue());
            assertEquals("REPAS (journalier) (Ar)", entetes.getCell(12).getStringCellValue());
            assertEquals(1304.0, feuille.getRow(entetes.getRowNum() + 1).getCell(11).getNumericCellValue());
            assertEquals(2000.0, feuille.getRow(entetes.getRowNum() + 1).getCell(12).getNumericCellValue());
        }
    }
}
