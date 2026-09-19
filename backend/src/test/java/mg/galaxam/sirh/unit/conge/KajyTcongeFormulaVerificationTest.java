package mg.galaxam.sirh.unit.conge;

import mg.galaxam.sirh.model.conge.engine.CongeCalculateurEngine;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Vérification Finale Ciblée : Formule Générale KAJY vs Simplification /12 vs Runtime Java")
public class KajyTcongeFormulaVerificationTest {

    private final CongeCalculateurEngine engine = new CongeCalculateurEngine();

    @Test
    @DisplayName("1. Preuve formelle des formules des cellules Excel (Feuille1)")
    void verifierFormulesCellulesExcel() throws Exception {
        File file = new File("../model/OHATRA KAJY DROITS CONGES-given on sept26.xls");
        if (!file.exists()) file = new File("model/OHATRA KAJY DROITS CONGES-given on sept26.xls");
        assertThat(file).exists();

        try (FileInputStream fis = new FileInputStream(file);
             HSSFWorkbook wb = new HSSFWorkbook(fis)) {
            HSSFSheet sheet = wb.getSheetAt(0);

            // Vérification de l'en-tête de la colonne T (index 19)
            String enteteT = sheet.getRow(1).getCell(19).getStringCellValue();
            assertThat(enteteT).isEqualTo("R*S/30");

            // Vérification sur les 12 lignes (lignes 3 à 14, index 2 à 13)
            for (int r = 2; r <= 13; r++) {
                HSSFRow row = sheet.getRow(r);
                int numRow = r + 1;

                // Col D : formule de présence plafonnée à 30
                String formuleD = row.getCell(3).getCellFormula();
                assertThat(formuleD).isEqualTo("IF(30-F" + numRow + "+H" + numRow + ">30,30,30-F" + numRow + "+H" + numRow + ")");

                // Col K : NET BASE = BASE - ABSENCES + CONGE
                String formuleK = row.getCell(10).getCellFormula();
                assertThat(formuleK).isEqualTo("E" + numRow + "-G" + numRow + "+J" + numRow);

                // Col R : base totale = NET BASE + HS
                String formuleR = row.getCell(17).getCellFormula();
                assertThat(formuleR).isEqualTo("P" + numRow + "+Q" + numRow);

                // Col S : nbre droit = D * 2.5 / 30
                String formuleS = row.getCell(18).getCellFormula();
                assertThat(formuleS).isEqualTo("D" + numRow + "*2.5/30");

                // Col T : Valorisation = S * R / 30
                String formuleT = row.getCell(19).getCellFormula();
                assertThat(formuleT).isEqualTo("S" + numRow + "*R" + numRow + "/30");
            }

            // Cellule W14 (index 13, col 22) : TCONGE = Cumul Valo / Cumul Droits
            String formuleW14 = sheet.getRow(13).getCell(22).getCellFormula();
            assertThat(formuleW14).isEqualTo("V14/U14");
            double tcongeVal = sheet.getRow(13).getCell(22).getNumericCellValue();
            assertEquals(10828.487026, tcongeVal, 0.0001);
        }
    }

    @ParameterizedTest(name = "Cas D={0} : Droit={1}, Base={2} -> Général={3}, /12={4}, Java={5}")
    @CsvSource({
            "30.0, 2.5000, 600000.00, 50000.00, 50000.00, 50000.00",
            "29.0, 2.4167, 600000.00, 48334.00, 50000.00, 48334.00",
            "15.0, 1.2500, 600000.00, 25000.00, 50000.00, 25000.00",
            "1.0,  0.0833, 600000.00, 1666.00,  50000.00, 1666.00",
            "0.0,  0.0000, 600000.00, 0.00,     50000.00, 0.00"
    })
    @DisplayName("2. Vérification des 5 cas obligatoires : Formule générale vs /12 vs Java Runtime")
    void verifierCasObligatoires(double dVal, String droitStr, String baseStr,
                                 String valoGenStr, String valoSur12Str, String valoJavaExpectedStr) {
        BigDecimal baseTotale = new BigDecimal(baseStr);
        BigDecimal joursDroit = new BigDecimal(droitStr);
        BigDecimal expectedGeneral = new BigDecimal(valoGenStr);
        BigDecimal expectedSur12 = new BigDecimal(valoSur12Str);
        BigDecimal expectedJava = new BigDecimal(valoJavaExpectedStr);

        // 1. Calcul selon la formule générale : BaseTotale * DroitMois / 30
        BigDecimal valoGenerale = baseTotale.multiply(joursDroit).divide(new BigDecimal("30.0"), 2, RoundingMode.HALF_UP);
        assertThat(valoGenerale).isEqualByComparingTo(expectedGeneral);

        // 2. Calcul selon la simplification /12 : BaseTotale / 12
        BigDecimal valoSur12 = baseTotale.divide(new BigDecimal("12.0"), 2, RoundingMode.HALF_UP);
        assertThat(valoSur12).isEqualByComparingTo(expectedSur12);

        // 3. Calcul exécuté par le moteur Java runtime (CongeCalculateurEngine)
        BigDecimal valoJava = engine.calculerValorisationDroitMensuel(baseTotale, joursDroit, 30);
        assertThat(valoJava).isEqualByComparingTo(expectedJava);

        // Preuve : Java produit TOUJOURS le résultat de la formule générale et JAMAIS le résultat erroné /12
        assertThat(valoJava).isEqualByComparingTo(valoGenerale);
        if (dVal < 30.0) {
            assertThat(valoJava).isNotEqualByComparingTo(valoSur12);
        }
    }

    @Test
    @DisplayName("3. Reproduit fidèlement les 5 mois proratisés réels du fichier KAJY (Mois 2, 3, 6, 9, 12)")
    void verifierMoisProratisesKajyReels() {
        // En Excel, Col T = Col S * Col R / 30. Comme Col S = D * 2.5 / 30 n'est pas tronqué avant le produit,
        // la valeur exacte sous-jacente est BaseTotale * D * 2.5 / 900.
        // Si l'on applique le droit stocké à 4 décimales, l'écart d'arrondi est inférieur à 0.40 Ar par mois
        // et produit le même TCONGE terminal de 10 828.49 Ar/j.

        // Mois 2 : D=29.99, BaseTotale=311939.05
        // Formule exacte Excel : 311 939.05 * 29.99 * 2.5 / 900 = 25 986.26 Ar
        // Simplification /12   : 311 939.05 / 12 = 25 994.92 Ar (écart de +8.66 Ar)
        BigDecimal baseMois2 = new BigDecimal("311939.05");
        BigDecimal dMois2 = new BigDecimal("29.99");
        BigDecimal valoExacteMois2 = baseMois2.multiply(dMois2).multiply(new BigDecimal("2.5"))
                .divide(new BigDecimal("900.0"), 2, RoundingMode.HALF_UP);
        assertThat(valoExacteMois2).isEqualByComparingTo("25986.26");

        BigDecimal droit4decMois2 = engine.calculerAcquisitionMensuelleProratisee(dMois2);
        BigDecimal valoJavaMois2 = engine.calculerValorisationDroitMensuel(baseMois2, droit4decMois2, 30);
        assertThat(valoJavaMois2).isEqualByComparingTo("25986.60");
        assertThat(valoJavaMois2.subtract(valoExacteMois2).abs()).isLessThan(new BigDecimal("0.50"));

        // Mois 3 : D=29.92, BaseTotale=295498.26
        // Formule exacte Excel : 24 559.19 Ar. Simplification /12 = 24 624.86 Ar (écart de +65.67 Ar).
        BigDecimal baseMois3 = new BigDecimal("295498.26");
        BigDecimal dMois3 = new BigDecimal("29.92");
        BigDecimal valoExacteMois3 = baseMois3.multiply(dMois3).multiply(new BigDecimal("2.5"))
                .divide(new BigDecimal("900.0"), 2, RoundingMode.HALF_UP);
        assertThat(valoExacteMois3).isEqualByComparingTo("24559.19");

        // Mois 6 : D=29.62, BaseTotale=346278.49
        // Formule exacte Excel : 28 491.02 Ar. Simplification /12 = 28 856.54 Ar (écart de +365.52 Ar).
        BigDecimal baseMois6 = new BigDecimal("346278.49");
        BigDecimal dMois6 = new BigDecimal("29.62");
        BigDecimal valoExacteMois6 = baseMois6.multiply(dMois6).multiply(new BigDecimal("2.5"))
                .divide(new BigDecimal("900.0"), 2, RoundingMode.HALF_UP);
        assertThat(valoExacteMois6).isEqualByComparingTo("28491.02");

        // Mois 9 : D=29.96, BaseTotale=349434.60
        // Formule exacte Excel : 29 080.72 Ar. Simplification /12 = 29 119.55 Ar (écart de +38.83 Ar).
        BigDecimal baseMois9 = new BigDecimal("349434.60");
        BigDecimal dMois9 = new BigDecimal("29.96");
        BigDecimal valoExacteMois9 = baseMois9.multiply(dMois9).multiply(new BigDecimal("2.5"))
                .divide(new BigDecimal("900.0"), 2, RoundingMode.HALF_UP);
        assertThat(valoExacteMois9).isEqualByComparingTo("29080.72");

        // Mois 12 : D=29.99, BaseTotale=353467.81
        // Formule exacte Excel : 29 445.83 Ar. Simplification /12 = 29 455.65 Ar (écart de +9.82 Ar).
        BigDecimal baseMois12 = new BigDecimal("353467.81");
        BigDecimal dMois12 = new BigDecimal("29.99");
        BigDecimal valoExacteMois12 = baseMois12.multiply(dMois12).multiply(new BigDecimal("2.5"))
                .divide(new BigDecimal("900.0"), 2, RoundingMode.HALF_UP);
        assertThat(valoExacteMois12).isEqualByComparingTo("29445.83");
    }
}
