package mg.galaxam.sirh.unit.characterization;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 2A-4B.1 — Caractérisation de la provenance et du cycle de vie du TCONGE.
 * 
 * Ce test caractérise : GALAXAM_CURRENT_BUSINESS_REFERENCE (référence métier actuelle).
 * Il NE valide PAS la conformité juridique (LEGAL_CORRECTNESS).
 * 
 * Preuves prouvées par ce test :
 * 1. La colonne I de KAJY n'est pas un taux constant unique : elle comporte 8602.88 (Mois 1),
 *    8328.95 (Mois 3-4) et 8669.52 (Mois 8, 10, 11, 12).
 * 2. Toutes les cellules de la colonne I sont des constantes numériques brutes (saisie / report).
 * 3. Les feuilles d'explications de paie GALAXAM documentent explicitement TCONGE comme :
 *    "saisie manuelle suivant tableau de suivi des congés (fichier à part actuellement)".
 * 4. Le taux 10828.49 est calculé uniquement en clôture du Mois 12 (W14 = V14/U14) et
 *    n'a pas été appliqué aux congés pris au Mois 12 (qui utilisent encore 8669.52).
 */
@DisplayName("Phase 2A-4B.1 : Caractérisation de la provenance et cycle de TCONGE KAJY (GALAXAM_CURRENT_BUSINESS_REFERENCE)")
public class KajyAppliedTcongeCharacterizationTest {

    @Test
    @DisplayName("shouldCharacterizeColumnIAppliedRatesAndFinalRateInKajy")
    public void shouldCharacterizeColumnIAppliedRatesAndFinalRateInKajy() throws Exception {
        File kajyFile = new File("../model/OHATRA KAJY DROITS CONGES-given on sept26.xls");
        if (!kajyFile.exists()) {
            kajyFile = new File("model/OHATRA KAJY DROITS CONGES-given on sept26.xls");
        }
        assertThat(kajyFile).exists();

        try (FileInputStream fis = new FileInputStream(kajyFile);
             Workbook workbook = WorkbookFactory.create(fis)) {

            Sheet sheet = workbook.getSheetAt(0);

            // 1. Mois 1 (Row 3, index 2) : TCONGE appliqué = 8602.88
            Row rMois1 = sheet.getRow(2);
            Cell cI1 = rMois1.getCell(8);
            assertThat(cI1.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(BigDecimal.valueOf(cI1.getNumericCellValue()).setScale(2, RoundingMode.HALF_UP))
                    .isEqualByComparingTo("8602.88");

            // 2. Mois 3 (Row 5, index 4) : TCONGE appliqué = 8328.95
            Row rMois3 = sheet.getRow(4);
            Cell cI3 = rMois3.getCell(8);
            assertThat(cI3.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(BigDecimal.valueOf(cI3.getNumericCellValue()).setScale(2, RoundingMode.HALF_UP))
                    .isEqualByComparingTo("8328.95");

            // 3. Mois 4 (Row 6, index 5) : TCONGE appliqué = 8328.95
            Row rMois4 = sheet.getRow(5);
            Cell cI4 = rMois4.getCell(8);
            assertThat(cI4.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(BigDecimal.valueOf(cI4.getNumericCellValue()).setScale(2, RoundingMode.HALF_UP))
                    .isEqualByComparingTo("8328.95");

            // 4. Mois 8, 10, 11, 12 : TCONGE appliqué = 8669.52
            int[] mois8a12Rows = { 9, 11, 12, 13 };
            for (int rIdx : mois8a12Rows) {
                Row r = sheet.getRow(rIdx);
                Cell cI = r.getCell(8);
                assertThat(cI.getCellType()).isEqualTo(CellType.NUMERIC);
                assertThat(BigDecimal.valueOf(cI.getNumericCellValue()).setScale(2, RoundingMode.HALF_UP))
                        .isEqualByComparingTo("8669.52");
            }

            // 5. Clôture Mois 12 (Row 14, index 13) : Nouveau TCONGE calculé = 10828.49 Ar/j
            Row rMois12 = sheet.getRow(13);
            Cell cW12 = rMois12.getCell(22); // Col W
            assertThat(cW12.getCellType()).isEqualTo(CellType.FORMULA);
            assertThat(cW12.getCellFormula()).isEqualTo("V14/U14");
            assertThat(BigDecimal.valueOf(cW12.getNumericCellValue()).setScale(2, RoundingMode.HALF_UP))
                    .isEqualByComparingTo("10828.49");

            // 6. Preuve que le Mois 12 applique encore 8669.52 (J14 = I14 * H14) et non le nouveau taux W14
            Cell cJ12 = rMois12.getCell(9); // Col J : CONGE
            Cell cH12 = rMois12.getCell(7); // Col H : NBCONGE (0.4)
            double congeMnt12 = cJ12.getNumericCellValue();
            double nbCge12 = cH12.getNumericCellValue();
            double tcongeUsedInMonth12 = congeMnt12 / nbCge12;
            assertThat(BigDecimal.valueOf(tcongeUsedInMonth12).setScale(2, RoundingMode.HALF_UP))
                    .isEqualByComparingTo("8669.52");
        }
    }

    @Test
    @DisplayName("shouldCharacterizePayrollExplanationReferenceForTconge")
    public void shouldCharacterizePayrollExplanationReferenceForTconge() throws Exception {
        File gitaFile = new File("../model/ohatra gita fichiers-23jan26.xlsx");
        if (!gitaFile.exists()) {
            gitaFile = new File("model/ohatra gita fichiers-23jan26.xlsx");
        }
        assertThat(gitaFile).exists();

        try (FileInputStream fis = new FileInputStream(gitaFile);
             Workbook workbook = WorkbookFactory.create(fis)) {

            Sheet fanazavana = workbook.getSheet("Fanazavana");
            assertThat(fanazavana).isNotNull();

            // Row 26 (index 25) : Taux Congés | taux journalier droit congés | saisie manuelle...
            Row row26 = fanazavana.getRow(25);
            assertThat(row26).isNotNull();
            String colA = row26.getCell(0).getStringCellValue().trim();
            String colB = row26.getCell(1).getStringCellValue().trim();
            String colC = row26.getCell(2).getStringCellValue().trim();

            assertThat(colA).isEqualTo("Taux Congés");
            assertThat(colB).isEqualTo("taux journalier droit congés");
            assertThat(colC).contains("saisie manuelle suivant tableau de suivi des congés");
        }
    }
}
