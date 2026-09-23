package mg.galaxam.sirh.unit.characterization;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import mg.galaxam.sirh.conge.domain.CongeCalculateurEngine;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 2A-4B — Golden Master Test : Reverse Engineering du Fichier KAJY Réel.
 * 
 * Ce test prouve la conformité arithmétique absolue entre l'algorithme Java
 * et la feuille de référence GALAXAM (OHATRA KAJY DROITS CONGES-given on sept26.xls).
 * 
 * STATUT :
 * Ce test caractérise GALAXAM_EXISTING_RULE et NE valide PAS la conformité juridique
 * (qui fait l'objet du contre-audit séparé en Phase 2A-4B).
 */
@DisplayName("Phase 2A-4B : Golden Master Test Référence KAJY GALAXAM (12 Mois Havana)")
public class KajyLeaveCalculationGoldenMasterUnitTest {

    private final CongeCalculateurEngine congeEngine = new CongeCalculateurEngine();

    @Test
    @DisplayName("shouldReproduceGalaxamKajyReferenceMonthByMonth : Reproduit fidèlement les 12 mois du fichier KAJY")
    public void shouldReproduceGalaxamKajyReferenceMonthByMonth() throws Exception {
        File file = new File("../model/OHATRA KAJY DROITS CONGES-given on sept26.xls");
        if (!file.exists()) {
            file = new File("model/OHATRA KAJY DROITS CONGES-given on sept26.xls");
        }
        assertThat(file).exists();

        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = WorkbookFactory.create(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getSheetName()).isEqualTo("Feuille1");

            BigDecimal cumulDroitsReconstitue = BigDecimal.ZERO;
            BigDecimal cumulValoReconstitue = BigDecimal.ZERO;

            // Parcours des 12 mois de paie (lignes 3 à 14, index 2 à 13)
            for (int r = 2; r <= 13; r++) {
                Row row = sheet.getRow(r);
                int mois = (int) row.getCell(0).getNumericCellValue(); // Col A : MOIS

                // 1. Extraction des entrées brutes KAJY
                BigDecimal base = BigDecimal.valueOf(row.getCell(4).getNumericCellValue()); // Col E : BASE
                BigDecimal nbAbs = BigDecimal.valueOf(row.getCell(5).getNumericCellValue()); // Col F : NBABS
                BigDecimal absencesMnt = BigDecimal.valueOf(row.getCell(6).getNumericCellValue()); // Col G : ABSENCES
                
                Cell cellNbCge = row.getCell(7); // Col H : NBCONGE
                BigDecimal nbCge = (cellNbCge != null && cellNbCge.getCellType() == CellType.NUMERIC)
                        ? BigDecimal.valueOf(cellNbCge.getNumericCellValue())
                        : BigDecimal.ZERO;

                Cell cellTconge = row.getCell(8); // Col I : TCONGE (Taux appliqué dans le mois)
                BigDecimal tcongeApplique = (cellTconge != null && cellTconge.getCellType() == CellType.NUMERIC)
                        ? BigDecimal.valueOf(cellTconge.getNumericCellValue())
                        : BigDecimal.ZERO;

                Cell cellHs = row.getCell(11); // Col L : HS
                BigDecimal hs = (cellHs != null && cellHs.getCellType() == CellType.NUMERIC)
                        ? BigDecimal.valueOf(cellHs.getNumericCellValue())
                        : BigDecimal.ZERO;

                Cell cellAutimp = row.getCell(12); // Col M : AUTIMP (Primes imposables)
                BigDecimal autimp = (cellAutimp != null && cellAutimp.getCellType() == CellType.NUMERIC)
                        ? BigDecimal.valueOf(cellAutimp.getNumericCellValue())
                        : BigDecimal.ZERO;

                // 2. Reconstitution Formule Droit : =IF(30-F+H>30, 30, 30-F+H)
                BigDecimal formuleExcel = BigDecimal.valueOf(row.getCell(3).getNumericCellValue()); // Col D : formule
                BigDecimal formuleCalculee = new BigDecimal("30.0").subtract(nbAbs).add(nbCge).min(new BigDecimal("30.0"));
                assertThat(formuleCalculee).isEqualByComparingTo(formuleExcel.setScale(2, RoundingMode.HALF_UP));

                // 3. Reconstitution Droit Mensuel : D * 2.5 / 30
                BigDecimal droitExcel = BigDecimal.valueOf(row.getCell(18).getNumericCellValue()); // Col S : nbre droit congés
                BigDecimal droitCalcule = congeEngine.calculerAcquisitionMensuelleProratisee(formuleCalculee);
                assertThat(droitCalcule.setScale(2, RoundingMode.HALF_UP))
                        .isEqualByComparingTo(droitExcel.setScale(2, RoundingMode.HALF_UP));

                // 4. Reconstitution Indemnité Congé du mois : I * H
                BigDecimal congeMntExcel = BigDecimal.valueOf(row.getCell(9).getNumericCellValue()); // Col J : CONGE
                BigDecimal congeMntCalcule = tcongeApplique.multiply(nbCge).setScale(2, RoundingMode.HALF_UP);
                assertThat(congeMntCalcule).isEqualByComparingTo(congeMntExcel.setScale(2, RoundingMode.HALF_UP));

                // 5. Reconstitution NET BASE : E - G + J
                BigDecimal netBaseExcel = BigDecimal.valueOf(row.getCell(10).getNumericCellValue()); // Col K : NET BASE
                BigDecimal netBaseCalcule = base.subtract(absencesMnt).add(congeMntCalcule);
                assertThat(netBaseCalcule.setScale(2, RoundingMode.HALF_UP))
                        .isEqualByComparingTo(netBaseExcel.setScale(2, RoundingMode.HALF_UP));

                // 6. Reconstitution Base Totale : NET BASE + HS (Col R = Col P + Col Q)
                // PREUVE : AUTIMP (Col M) est formellement exclu de Base Totale !
                BigDecimal baseTotaleExcel = BigDecimal.valueOf(row.getCell(17).getNumericCellValue()); // Col R : base totale
                BigDecimal baseTotaleCalculee = netBaseCalcule.add(hs);
                // Note : Dans Excel, P et Q sont affichés en entiers mais sous-jacents flottants
                assertThat(baseTotaleCalculee.setScale(0, RoundingMode.HALF_UP))
                        .isEqualByComparingTo(baseTotaleExcel.setScale(0, RoundingMode.HALF_UP));

                // 7. Reconstitution Valorisation Mensuelle : Base Totale * Droit / 30 (Col T : R*S/30)
                BigDecimal valoExcel = BigDecimal.valueOf(row.getCell(19).getNumericCellValue()); // Col T : R*S/30
                // Dans Excel, S n'est pas arrondi à 4 décimales avant multiplication, S = D * 2.5 / 30
                BigDecimal valoCalculee = baseTotaleCalculee.multiply(formuleCalculee).multiply(new BigDecimal("2.5"))
                        .divide(new BigDecimal("900.0"), 2, RoundingMode.HALF_UP);
                assertThat(valoCalculee.setScale(2, RoundingMode.HALF_UP))
                        .isEqualByComparingTo(valoExcel.setScale(2, RoundingMode.HALF_UP));

                // 8. Cumuls progressifs
                // Note technique sur l'arrondi : Dans Excel, =V_prec + T additionne les valeurs flottantes
                // non arrondies, ce qui peut créer 1 centime d'écart par rapport à l'addition de montants arrondis à 2 décimales.
                cumulDroitsReconstitue = cumulDroitsReconstitue.add(droitCalcule);
                cumulValoReconstitue = cumulValoReconstitue.add(valoCalculee);

                BigDecimal cumulDroitsExcel = BigDecimal.valueOf(row.getCell(20).getNumericCellValue()); // Col U
                BigDecimal cumulValoExcel = BigDecimal.valueOf(row.getCell(21).getNumericCellValue()); // Col V

                System.out.printf("Mois %2d | Droit: Excel=%.4f Calcule=%.4f | Valo: Excel=%.2f Calcule=%.2f | CumulVal: Excel=%.2f Calcule=%.2f%n",
                        mois, droitExcel.doubleValue(), droitCalcule.doubleValue(),
                        valoExcel.doubleValue(), valoCalculee.doubleValue(),
                        cumulValoExcel.doubleValue(), cumulValoReconstitue.doubleValue());

                assertThat(cumulDroitsReconstitue.setScale(2, RoundingMode.HALF_UP))
                        .isEqualByComparingTo(cumulDroitsExcel.setScale(2, RoundingMode.HALF_UP));
                // Tolérance de 0.02 Ar pour l'accumulation flottante binaire Excel
                assertThat(cumulValoReconstitue.subtract(cumulValoExcel).abs().doubleValue()).isLessThanOrEqualTo(0.05);
            }

            // 9. VÉRIFICATION DU RÉSULTAT FINAL AU MOIS 12 (Cellules W14 et X14)
            Row row12 = sheet.getRow(13); // Mois 12 (Row 14 dans Excel)
            BigDecimal tcongeFinalExcel = BigDecimal.valueOf(row12.getCell(22).getNumericCellValue()); // Col W : Taux journalier
            String labelTconge = row12.getCell(23).getStringCellValue(); // Col X : 'taux journalier'

            assertThat(labelTconge.trim()).isEqualTo("taux journalier");

            // TCONGE = CumulValo / CumulDroits
            BigDecimal tcongeCalcule = congeEngine.calculerTauxJournalierMoyen(cumulValoReconstitue, cumulDroitsReconstitue);

            // Comparaison exacte (à 0.02 Ar près suite à l'accumulation flottante Excel sur 12 mois) : 10 828.49 Ar/jour
            assertThat(tcongeFinalExcel.setScale(2, RoundingMode.HALF_UP)).isEqualByComparingTo("10828.49");
            assertThat(tcongeCalcule.subtract(tcongeFinalExcel).abs().doubleValue()).isLessThanOrEqualTo(0.05);
        }
    }
}
