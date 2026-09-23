package mg.galaxam.sirh.unit.pointage;

import static org.assertj.core.api.Assertions.assertThat;

import mg.galaxam.sirh.pointage.api.dto.FichePointageEmployeDTO;
import mg.galaxam.sirh.pointage.api.dto.PointageJourDTO;
import mg.galaxam.sirh.pointage.api.dto.PointageTronconSemaineDTO;
import mg.galaxam.sirh.pointage.application.PointageExcelExportService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

class PointageExcelExportServiceTest {

    private final PointageExcelExportService exportService = new PointageExcelExportService(null, null);

    @Test
    void genererExcelFicheEmploye_creeUnClasseurCompletAvecColonnesClaires() throws IOException {
        FichePointageEmployeDTO fiche = new FichePointageEmployeDTO();
        fiche.setMatricule("EMP-001");
        fiche.setNomComplet("Rakoto Jean");
        fiche.setFonction("Opérateur");
        fiche.setSiteNom("Antananarivo");
        fiche.setCategorie("HC");
        fiche.setLibellePeriode("Décembre 2025");
        fiche.setDateDebut("2025-12-01");
        fiche.setDateFin("2025-12-31");

        // Bilans hebdo
        List<PointageTronconSemaineDTO> bilans = new ArrayList<>();
        PointageTronconSemaineDTO b1 = new PointageTronconSemaineDTO();
        b1.setNumeroSemaine(1);
        b1.setDebutSemaine("2025-12-01");
        b1.setFinSemaine("2025-12-07");
        b1.setNbJoursTroncon(5);
        b1.setSeuilHeuresTroncon(40.0);
        b1.setTotalHeuresTravaillees(41.5);
        b1.setHeuresRetard(0.27);
        b1.setHeuresCompensees(0.5);
        b1.setHeuresHsBrutes(1.5);
        b1.setHeuresHsNettes(1.5);
        b1.setHeuresDeficit(0.0);
        bilans.add(b1);
        fiche.setBilansHebdo(bilans);

        // Pointages journaliers
        List<PointageJourDTO> pointages = new ArrayList<>();
        PointageJourDTO pj1 = new PointageJourDTO();
        pj1.setDate("01/12/2025");
        pj1.setDateComplete("2025-12-01");
        pj1.setJour("Lun");
        pj1.setEntree("08:16");
        pj1.setSortiePause("12:00");
        pj1.setRetourPause("12:30");
        pj1.setSortie("17:05");
        pj1.setMinutesRetard(16);
        pj1.setHeures("08h19");
        pj1.setTotalHeuresDecimal(8.32);
        pj1.setEcart("+00h19");
        pj1.setStatut("PRESENT");
        pj1.setMontantRepas(new BigDecimal("2000"));
        pj1.setMontantDeplacement(new BigDecimal("1400"));
        pointages.add(pj1);
        fiche.setPointagesData(pointages);

        byte[] excelBytes = exportService.genererExcelFicheEmploye(fiche);
        assertThat(excelBytes).isNotEmpty();

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            Sheet sheet = wb.getSheet("Pointage Individuel");
            assertThat(sheet).isNotNull();

            // Trouver la ligne d'en-tête Hebdo
            Row headerHebdo = null;
            Row headerJour = null;
            for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || row.getCell(0) == null) continue;
                String val = row.getCell(0).getStringCellValue();
                if ("Semaine".equals(val)) {
                    headerHebdo = row;
                } else if ("Date".equals(val)) {
                    headerJour = row;
                }
            }

            // Vérifier en-tête Hebdo logique et non technique
            assertThat(headerHebdo).isNotNull();
            assertThat(headerHebdo.getCell(0).getStringCellValue()).isEqualTo("Semaine");
            assertThat(headerHebdo.getCell(1).getStringCellValue()).isEqualTo("Jours");
            assertThat(headerHebdo.getCell(2).getStringCellValue()).isEqualTo("Objectif semaine (h)");
            assertThat(headerHebdo.getCell(3).getStringCellValue()).isEqualTo("Heures faites (hh:mm)");
            assertThat(headerHebdo.getCell(4).getStringCellValue()).isEqualTo("Heures faites (déc.)");
            assertThat(headerHebdo.getCell(5).getStringCellValue()).isEqualTo("Retards (min)");
            assertThat(headerHebdo.getCell(6).getStringCellValue()).isEqualTo("Compensé (h)");
            assertThat(headerHebdo.getCell(7).getStringCellValue()).isEqualTo("Impact Paie (h)");
            assertThat(headerHebdo.getCell(8).getStringCellValue()).isEqualTo("Heures Sup (h)");
            assertThat(headerHebdo.getCell(9).getStringCellValue()).isEqualTo("Retenue (h)");

            // Vérifier en-tête Journal Quotidien logique et ordonné
            assertThat(headerJour).isNotNull();
            assertThat(headerJour.getCell(0).getStringCellValue()).isEqualTo("Date");
            assertThat(headerJour.getCell(1).getStringCellValue()).isEqualTo("Jour");
            assertThat(headerJour.getCell(2).getStringCellValue()).isEqualTo("Entrée");
            assertThat(headerJour.getCell(3).getStringCellValue()).isEqualTo("Sortie Pause");
            assertThat(headerJour.getCell(4).getStringCellValue()).isEqualTo("Retour Pause");
            assertThat(headerJour.getCell(5).getStringCellValue()).isEqualTo("Sortie");
            assertThat(headerJour.getCell(6).getStringCellValue()).isEqualTo("Retard (min)");
            assertThat(headerJour.getCell(7).getStringCellValue()).isEqualTo("Heures faites (hh:mm)");
            assertThat(headerJour.getCell(8).getStringCellValue()).isEqualTo("Heures faites (déc.)");
            assertThat(headerJour.getCell(9).getStringCellValue()).isEqualTo("Écart (vs 8h)");
            assertThat(headerJour.getCell(10).getStringCellValue()).isEqualTo("Statut");
            assertThat(headerJour.getCell(11).getStringCellValue()).isEqualTo("Transport (Ar)");
            assertThat(headerJour.getCell(12).getStringCellValue()).isEqualTo("Repas (Ar)");
        }
    }
}
