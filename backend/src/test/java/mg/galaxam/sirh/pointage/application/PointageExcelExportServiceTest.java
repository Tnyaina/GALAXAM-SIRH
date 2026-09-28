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
                "COMMUNICATION", true, new BigDecimal("1304"),new BigDecimal("0.5")));
        indemnites.put("REPAS", new PointageJourDTO.IndemniteJourDTO(
                "REPAS", true, new BigDecimal("2000"),BigDecimal.ONE));
        jour.setIndemnites(indemnites);
        fiche.setPointagesData(List.of(jour));

        fiche.setTotauxIndemnites(Map.of("COMMUNICATION",new BigDecimal("1304"),"REPAS",new BigDecimal("2000")));
        byte[] contenu = new PointageExcelExportService().genererExcelFicheEmploye(fiche);
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
            assertEquals(12, entetes.getLastCellNum());
            assertEquals("COMMUNICATION (unité)", entetes.getCell(9).getStringCellValue());
            assertEquals("REPAS (unité)", entetes.getCell(10).getStringCellValue());
            assertEquals(0.5, feuille.getRow(entetes.getRowNum() + 1).getCell(9).getNumericCellValue());
            assertEquals(1.0, feuille.getRow(entetes.getRowNum() + 1).getCell(10).getNumericCellValue());
            Row recap = null;
            for (Row row : feuille) if (row.getCell(0) != null && row.getCell(0).getCellType() == org.apache.poi.ss.usermodel.CellType.STRING
                && "RÉCAPITULATIF DES INDEMNITÉS".equals(row.getCell(0).getStringCellValue())) recap = feuille.getRow(row.getRowNum() + 2);
            assertNotNull(recap);
            assertEquals(1304.0, recap.getCell(6).getNumericCellValue());
            for(Row row:feuille)for(var cell:row)if(cell.getCellType()==org.apache.poi.ss.usermodel.CellType.STRING)
                org.junit.jupiter.api.Assertions.assertFalse(cell.getStringCellValue().contains("{{"));
        }
    }
    @Test
    void conserveLesLignesEtColonnesSupplementairesSansRecalculerLeBilan() throws Exception {
        var data=PointagePdfExportServiceTest.example(35,7);
        byte[] bytes=new PointageExcelExportService().genererExcelFicheEmploye(data);
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(bytes))){
            var sheet=workbook.getSheetAt(0);
            assertEquals("PRIME_6 (unité)",sheet.getRow(6).getCell(15).getStringCellValue());
            assertEquals("2026-10-05",sheet.getRow(41).getCell(0).getStringCellValue());
            Row balance=null;
            for(Row row:sheet)if(row.getCell(0)!=null && row.getCell(0).getCellType()==org.apache.poi.ss.usermodel.CellType.STRING
                    && "BILAN FINAL DE PÉRIODE".equals(row.getCell(0).getStringCellValue()))balance=sheet.getRow(row.getRowNum()+2);
            assertNotNull(balance);assertEquals(2.0,balance.getCell(4).getNumericCellValue());
            for(Row row:sheet)for(var cell:row)if(cell.getCellType()==org.apache.poi.ss.usermodel.CellType.FORMULA)
                org.junit.jupiter.api.Assertions.assertTrue(cell.getCellFormula().startsWith("SUM("));
        }
    }

}
