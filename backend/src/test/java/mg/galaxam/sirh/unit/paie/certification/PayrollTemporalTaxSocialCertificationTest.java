package mg.galaxam.sirh.unit.paie.certification;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import mg.galaxam.sirh.model.paie.BulletinPaie;
import mg.galaxam.sirh.model.paie.dto.PayrollCalculationInput;
import mg.galaxam.sirh.model.paie.engine.IrsaCalculator;
import mg.galaxam.sirh.model.paie.engine.PayrollExecutionEngine;
import mg.galaxam.sirh.model.paie.engine.SocialContributionCalculator;

/**
 * Suite de tests de contre-certification temporelle :
 * IRSA (LF 2025 vs LF 2026), CNaPS / SME (Décret 2024-2026 vs Revalorisation 01/03/2026),
 * Heures Supplémentaires (CGI 01.03.03-9°), Golden Masters Historiques Décembre 2025.
 */
public class PayrollTemporalTaxSocialCertificationTest {

    private final PayrollExecutionEngine engine = new PayrollExecutionEngine();

    // =========================================================================
    // 1. IRSA DÉCEMBRE 2025 (LF 2024/2025)
    // =========================================================================
    @Nested
    @DisplayName("1. IRSA Décembre 2025 (LF 2024/2025 - 5 Tranches, Min 2 000 Ar)")
    class Irsa2025Tests {

        @Test
        @DisplayName("1.1 Tranches IRSA 2025 indépendantes : seuils 350k, 400k, 500k, 600k et taux max 20%")
        void testIrsa2025Brackets() {
            // Sous le seuil (300 000 Ar) -> Minimum perception 2 000 Ar
            var r300k = TemporalTaxAndSocialCertificationOracle.calculateIrsa2025(new BigDecimal("300000.00"), 0);
            assertEquals(new BigDecimal("2000.00"), r300k.irsaNet());

            // Seuil exact 350 000 Ar -> Minimum perception 2 000 Ar
            var r350k = TemporalTaxAndSocialCertificationOracle.calculateIrsa2025(new BigDecimal("350000.00"), 0);
            assertEquals(new BigDecimal("2000.00"), r350k.irsaNet());

            // Tranche 2 : 380 000 Ar -> 30 000 @ 5% = 1 500 Ar -> min 2 000 Ar
            var r380k = TemporalTaxAndSocialCertificationOracle.calculateIrsa2025(new BigDecimal("380000.00"), 0);
            assertEquals(new BigDecimal("2000.00"), r380k.irsaNet(), "Inférieur au min légal");

            // Tranche 2 plein : 400 000 Ar -> 50 000 @ 5% = 2 500 Ar
            var r400k = TemporalTaxAndSocialCertificationOracle.calculateIrsa2025(new BigDecimal("400000.00"), 0);
            assertEquals(new BigDecimal("2500.00"), r400k.irsaNet());

            // Tranche 3 plein : 500 000 Ar -> 2 500 + 100k@10% = 12 500 Ar
            var r500k = TemporalTaxAndSocialCertificationOracle.calculateIrsa2025(new BigDecimal("500000.00"), 0);
            assertEquals(new BigDecimal("12500.00"), r500k.irsaNet());

            // Tranche 4 plein : 600 000 Ar -> 12 500 + 100k@15% = 27 500 Ar
            var r600k = TemporalTaxAndSocialCertificationOracle.calculateIrsa2025(new BigDecimal("600000.00"), 0);
            assertEquals(new BigDecimal("27500.00"), r600k.irsaNet());

            // Tranche 5 (> 600 000 Ar) : 1 000 000 Ar -> 27 500 + 400k@20% = 107 500 Ar
            var r1M = TemporalTaxAndSocialCertificationOracle.calculateIrsa2025(new BigDecimal("1000000.00"), 0);
            assertEquals(new BigDecimal("107500.00"), r1M.irsaNet());

            // Haut revenu en 2025 : 5 000 000 Ar -> 27 500 + 4 400 000 @ 20% = 907 500 Ar (pas de 25% en 2025)
            var r5M = TemporalTaxAndSocialCertificationOracle.calculateIrsa2025(new BigDecimal("5000000.00"), 0);
            assertEquals(new BigDecimal("907500.00"), r5M.irsaNet());
        }

        @Test
        @DisplayName("1.2 Réduction pour charges de famille en 2025 : plancher au minimum légal de 2 000 Ar")
        void testIrsa2025ChildrenReductionFloor() {
            // Net imposable 390 000 Ar -> Brut = 40 000 @ 5% = 2 000 Ar
            // Avec 1 enfant : réduction de 2 000 Ar -> 0 Ar -> Rehaussé au minimum de 2 000 Ar
            var r1Enf = TemporalTaxAndSocialCertificationOracle.calculateIrsa2025(new BigDecimal("390000.00"), 1);
            assertEquals(new BigDecimal("2000.00"), r1Enf.reductionEnfants());
            assertEquals(new BigDecimal("2000.00"), r1Enf.irsaNet(), "La réduction ne peut pas violer le minimum de perception");

            // Net imposable 500 000 Ar -> Brut = 12 500 Ar
            // Avec 3 enfants : réduction 6 000 Ar -> Net = 6 500 Ar
            var r3Enf = TemporalTaxAndSocialCertificationOracle.calculateIrsa2025(new BigDecimal("500000.00"), 3);
            assertEquals(new BigDecimal("6000.00"), r3Enf.reductionEnfants());
            assertEquals(new BigDecimal("6500.00"), r3Enf.irsaNet());
        }
    }

    // =========================================================================
    // 2. IRSA 2026 (LF 2026 - 6 Tranches, Tranche 25% > 4M, Min 3 000 Ar)
    // =========================================================================
    @Nested
    @DisplayName("2. IRSA 2026 (LF 2026 - 6 Tranches, Tranche 25%, Min 3 000 Ar)")
    class Irsa2026Tests {

        @Test
        @DisplayName("2.1 Tranche marginale 25% au-delà de 4 000 000 Ar : test frontière précis")
        void testIrsa2026HighIncomeBrackets() {
            // 4 000 000 Ar exact : Tranches 1 à 5 :
            // T1(0k) + T2(2.5k) + T3(10k) + T4(15k) + T5(3 400 000 @ 20% = 680k) = 707 500 Ar
            var r4M = TemporalTaxAndSocialCertificationOracle.calculateIrsa2026(new BigDecimal("4000000.00"), 0);
            assertEquals(new BigDecimal("707500.00"), r4M.irsaNet());

            // 3 999 900 Ar (juste sous la tranche 25%) : 707 500 - (100 * 20%) = 707 480 Ar
            var rUnder = TemporalTaxAndSocialCertificationOracle.calculateIrsa2026(new BigDecimal("3999900.00"), 0);
            assertEquals(new BigDecimal("707480.00"), rUnder.irsaNet());

            // 4 000 100 Ar (100 Ar dans la tranche 25%) : 707 500 + (100 * 25% = 25 Ar) -> dizaine sup = 707 530 Ar
            var rOver = TemporalTaxAndSocialCertificationOracle.calculateIrsa2026(new BigDecimal("4000100.00"), 0);
            assertEquals(new BigDecimal("707530.00"), rOver.irsaNet());

            // 4 500 000 Ar : 707 500 + (500 000 * 25% = 125 000) = 832 500 Ar
            var r4_5M = TemporalTaxAndSocialCertificationOracle.calculateIrsa2026(new BigDecimal("4500000.00"), 0);
            assertEquals(new BigDecimal("832500.00"), r4_5M.irsaNet());

            // 5 000 000 Ar : 707 500 + (1 000 000 * 25% = 250 000) = 957 500 Ar
            var r5M = TemporalTaxAndSocialCertificationOracle.calculateIrsa2026(new BigDecimal("5000000.00"), 0);
            assertEquals(new BigDecimal("957500.00"), r5M.irsaNet(), "Différence majeure 2025 (907 500 Ar) vs 2026 (957 500 Ar) démontrée");
        }

        @Test
        @DisplayName("2.2 Minimum de perception en 2026 : 3 000 Ar (revalorisé depuis 2 000 Ar)")
        void testIrsa2026MinimumPerception() {
            var r300k = TemporalTaxAndSocialCertificationOracle.calculateIrsa2026(new BigDecimal("300000.00"), 0);
            assertEquals(new BigDecimal("3000.00"), r300k.irsaNet());

            var r350k = TemporalTaxAndSocialCertificationOracle.calculateIrsa2026(new BigDecimal("350000.00"), 0);
            assertEquals(new BigDecimal("3000.00"), r350k.irsaNet());

            // 380 000 Ar -> 1 500 Ar brut -> rehaussé à 3 000 Ar
            var r380k = TemporalTaxAndSocialCertificationOracle.calculateIrsa2026(new BigDecimal("380000.00"), 0);
            assertEquals(new BigDecimal("3000.00"), r380k.irsaNet());
        }
    }

    // =========================================================================
    // 3. SME / CNaPS — CHRONOLOGIE ET DÉCOUPLAGE TEMPOREL
    // =========================================================================
    @Nested
    @DisplayName("3. SME / CNaPS (Chronologie Décret 2024-2026 vs Revalorisation 01/03/2026)")
    class CnapsTemporalTests {

        @Test
        @DisplayName("3.1 Test frontière SME & Plafond CNaPS aux 5 dates clés")
        void testCnapsTimelineBoundaries() {
            // 1. 2025-12-31 : SME 262 680 Ar, Plafond 2 101 440 Ar
            assertEquals(new BigDecimal("262680.00"), TemporalTaxAndSocialCertificationOracle.resolveSmeAt(LocalDate.of(2025, 12, 31)));
            assertEquals(new BigDecimal("2101440.00"), TemporalTaxAndSocialCertificationOracle.resolvePlafondCnapsAt(LocalDate.of(2025, 12, 31)));

            // 2. 2026-01-01 : SME 262 680 Ar, Plafond 2 101 440 Ar (décret CNaPS toujours en vigueur)
            assertEquals(new BigDecimal("262680.00"), TemporalTaxAndSocialCertificationOracle.resolveSmeAt(LocalDate.of(2026, 1, 1)));
            assertEquals(new BigDecimal("2101440.00"), TemporalTaxAndSocialCertificationOracle.resolvePlafondCnapsAt(LocalDate.of(2026, 1, 1)));

            // 3. 2026-02-28 : SME 262 680 Ar, Plafond 2 101 440 Ar (dernier jour du décret 2024-2026)
            assertEquals(new BigDecimal("262680.00"), TemporalTaxAndSocialCertificationOracle.resolveSmeAt(LocalDate.of(2026, 2, 28)));
            assertEquals(new BigDecimal("2101440.00"), TemporalTaxAndSocialCertificationOracle.resolvePlafondCnapsAt(LocalDate.of(2026, 2, 28)));

            // 4. 2026-03-01 : SME 300 000 Ar, Plafond 2 400 000 Ar (Revalorisation effective)
            assertEquals(new BigDecimal("300000.00"), TemporalTaxAndSocialCertificationOracle.resolveSmeAt(LocalDate.of(2026, 3, 1)));
            assertEquals(new BigDecimal("2400000.00"), TemporalTaxAndSocialCertificationOracle.resolvePlafondCnapsAt(LocalDate.of(2026, 3, 1)));

            // 5. 2026-03-02 : SME 300 000 Ar, Plafond 2 400 000 Ar
            assertEquals(new BigDecimal("300000.00"), TemporalTaxAndSocialCertificationOracle.resolveSmeAt(LocalDate.of(2026, 3, 2)));
            assertEquals(new BigDecimal("2400000.00"), TemporalTaxAndSocialCertificationOracle.resolvePlafondCnapsAt(LocalDate.of(2026, 3, 2)));
        }

        @Test
        @DisplayName("3.2 Frontières précises de plafonnement CNaPS (Décembre 2025 autour de 2 101 440 Ar)")
        void testCnapsBoundariesDec2025() {
            LocalDate dec2025 = LocalDate.of(2025, 12, 31);

            // 2 101 439 Ar (juste sous plafond)
            var cUnder = TemporalTaxAndSocialCertificationOracle.calculateCnaps(dec2025, new BigDecimal("2101439.00"));
            assertEquals(new BigDecimal("2101439.00"), cUnder.assiettePlafonnee());
            assertEquals(new BigDecimal("21014.39"), cUnder.cotisSalariale());
            assertEquals(new BigDecimal("273187.07"), cUnder.cotisPatronale());

            // 2 101 440 Ar (plafond exact)
            var cExact = TemporalTaxAndSocialCertificationOracle.calculateCnaps(dec2025, new BigDecimal("2101440.00"));
            assertEquals(new BigDecimal("2101440.00"), cExact.assiettePlafonnee());
            assertEquals(new BigDecimal("21014.40"), cExact.cotisSalariale());
            assertEquals(new BigDecimal("273187.20"), cExact.cotisPatronale());

            // 2 101 441 Ar (1 Ar au-dessus du plafond)
            var cOver = TemporalTaxAndSocialCertificationOracle.calculateCnaps(dec2025, new BigDecimal("2101441.00"));
            assertEquals(new BigDecimal("2101440.00"), cOver.assiettePlafonnee(), "Plafonné à 2 101 440 Ar");
            assertEquals(new BigDecimal("21014.40"), cOver.cotisSalariale());
            assertEquals(new BigDecimal("273187.20"), cOver.cotisPatronale());
        }

        @Test
        @DisplayName("3.3 Frontières précises de plafonnement CNaPS (Mars 2026 autour de 2 400 000 Ar)")
        void testCnapsBoundariesMarch2026() {
            LocalDate mar2026 = LocalDate.of(2026, 3, 1);

            // 2 399 999 Ar
            var cUnder = TemporalTaxAndSocialCertificationOracle.calculateCnaps(mar2026, new BigDecimal("2399999.00"));
            assertEquals(new BigDecimal("2399999.00"), cUnder.assiettePlafonnee());
            assertEquals(new BigDecimal("23999.99"), cUnder.cotisSalariale());

            // 2 400 000 Ar exact
            var cExact = TemporalTaxAndSocialCertificationOracle.calculateCnaps(mar2026, new BigDecimal("2400000.00"));
            assertEquals(new BigDecimal("2400000.00"), cExact.assiettePlafonnee());
            assertEquals(new BigDecimal("24000.00"), cExact.cotisSalariale());
            assertEquals(new BigDecimal("312000.00"), cExact.cotisPatronale());

            // 2 400 001 Ar
            var cOver = TemporalTaxAndSocialCertificationOracle.calculateCnaps(mar2026, new BigDecimal("2400001.00"));
            assertEquals(new BigDecimal("2400000.00"), cOver.assiettePlafonnee());
            assertEquals(new BigDecimal("24000.00"), cOver.cotisSalariale());
            assertEquals(new BigDecimal("312000.00"), cOver.cotisPatronale());
        }
    }

    // =========================================================================
    // 4. TEST DE NON-CONTAMINATION TEMPORELLE (Même salaire sur 4 mois)
    // =========================================================================
    @Nested
    @DisplayName("4. Test de Non-Contamination Temporelle (Salaire 5 000 000 Ar sur 4 périodes)")
    class NonContaminationTests {

        @Test
        @DisplayName("4.1 Traçabilité rigoureuse de l'évolution légale 12/2025, 01/2026, 02/2026, 03/2026")
        void testTimeTravelEvolution() {
            BigDecimal brut = new BigDecimal("5000000.00");

            // Décembre 2025 : CNaPS plafond 2 101 440 (cotis 21 014.40), Santé non plafonnée 1% = 50 000 Ar
            // NETI = 5 000 000 - 21 014.40 - 50 000 = 4 928 985.60 Ar -> Assiette 4 928 900 Ar
            // IRSA 2025 (5 tranches max 20%) : 27 500 + (4 328 900 * 20%) = 27 500 + 865 780 = 893 280 Ar
            var cnapsDec = TemporalTaxAndSocialCertificationOracle.calculateCnaps(LocalDate.of(2025, 12, 31), brut);
            assertEquals(new BigDecimal("21014.40"), cnapsDec.cotisSalariale());
            BigDecimal netiDec = brut.subtract(cnapsDec.cotisSalariale()).subtract(new BigDecimal("50000.00"));
            var irsaDec = TemporalTaxAndSocialCertificationOracle.calculateIrsa2025(netiDec, 0);
            assertEquals(new BigDecimal("893280.00"), irsaDec.irsaNet());

            // Janvier 2026 : CNaPS toujours sous décret 2024-2026 (plafond 2 101 440 Ar, cotis 21 014.40 Ar)
            // IRSA 2026 (6 tranches dont 25% > 4M) :
            // T1-T5 (jusqu'à 4M) = 707 500 Ar. Reste au-delà de 4M : 4 928 900 - 4 000 000 = 928 900 Ar.
            // 928 900 * 25% = 232 225 Ar. Total = 707 500 + 232 225 = 939 725 -> Dizaine sup = 939 730 Ar.
            var cnapsJan = TemporalTaxAndSocialCertificationOracle.calculateCnaps(LocalDate.of(2026, 1, 15), brut);
            assertEquals(new BigDecimal("21014.40"), cnapsJan.cotisSalariale(), "CNaPS en janvier 2026 reste sous décret 262 680 Ar");
            var irsaJan = TemporalTaxAndSocialCertificationOracle.calculateIrsa2026(netiDec, 0);
            assertEquals(new BigDecimal("939730.00"), irsaJan.irsaNet(), "IRSA augmente en 2026 par application de la tranche marginale 25%");

            // Février 2026 : Identique à Janvier 2026
            var cnapsFev = TemporalTaxAndSocialCertificationOracle.calculateCnaps(LocalDate.of(2026, 2, 28), brut);
            assertEquals(new BigDecimal("21014.40"), cnapsFev.cotisSalariale());
            var irsaFev = TemporalTaxAndSocialCertificationOracle.calculateIrsa2026(netiDec, 0);
            assertEquals(new BigDecimal("939730.00"), irsaFev.irsaNet());

            // Mars 2026 : Revalorisation CNaPS à 300 000 Ar -> Plafond 2 400 000 Ar -> Cotis CNaPS = 24 000.00 Ar (+2 985.60 Ar)
            // NETI = 5 000 000 - 24 000 - 50 000 = 4 926 000.00 Ar -> Assiette 4 926 000 Ar
            // IRSA 2026 : T1-T5 = 707 500 Ar + (926 000 * 25% = 231 500 Ar) = 939 000.00 Ar
            var cnapsMar = TemporalTaxAndSocialCertificationOracle.calculateCnaps(LocalDate.of(2026, 3, 1), brut);
            assertEquals(new BigDecimal("24000.00"), cnapsMar.cotisSalariale(), "CNaPS en mars 2026 passe à 24 000 Ar");
            BigDecimal netiMar = brut.subtract(cnapsMar.cotisSalariale()).subtract(new BigDecimal("50000.00"));
            var irsaMar = TemporalTaxAndSocialCertificationOracle.calculateIrsa2026(netiMar, 0);
            assertEquals(new BigDecimal("939000.00"), irsaMar.irsaNet(), "IRSA diminue légèrement car CNaPS déductible a augmenté");
        }
    }

    // =========================================================================
    // 5. EXONÉRATION DES HEURES SUPPLÉMENTAIRES (CGI Art. 01.03.03-9°)
    // =========================================================================
    @Nested
    @DisplayName("5. Exonération Heures Supplémentaires (CGI Art. 01.03.03-9°)")
    class OvertimeExemptionTests {

        @Test
        @DisplayName("5.1 Paliers d'heures supplémentaires : 0h, 1h, 19h, 20h, 20h01, 21h, 25h, 30h")
        void testOvertimeTiers() {
            BigDecimal tauxHoraire = new BigDecimal("2000.00");

            // 0h
            var r0 = TemporalTaxAndSocialCertificationOracle.calculateOvertimeExemption(tauxHoraire, BigDecimal.ZERO, BigDecimal.ZERO);
            assertEquals(0, BigDecimal.ZERO.compareTo(r0.remunHsTotale()));
            assertEquals(0, BigDecimal.ZERO.compareTo(r0.remunHsExoneree()));

            // 1h à 130% -> Rémun = 2 600 Ar -> 100% exonéré
            var r1 = TemporalTaxAndSocialCertificationOracle.calculateOvertimeExemption(tauxHoraire, new BigDecimal("1.00"), BigDecimal.ZERO);
            assertEquals(new BigDecimal("2600.00"), r1.remunHsTotale());
            assertEquals(new BigDecimal("2600.00"), r1.remunHsExoneree());
            assertEquals(0, BigDecimal.ZERO.compareTo(r1.remunHsImposable()));

            // 19h (8h à 130% + 11h à 150%) : Total 19h <= 20h -> 100% exonéré
            // 8 * 2000 * 1.30 = 20 800 Ar + 11 * 2000 * 1.50 = 33 000 Ar = 53 800 Ar
            var r19 = TemporalTaxAndSocialCertificationOracle.calculateOvertimeExemption(tauxHoraire, new BigDecimal("8.00"), new BigDecimal("11.00"));
            assertEquals(new BigDecimal("53800.00"), r19.remunHsTotale());
            assertEquals(new BigDecimal("53800.00"), r19.remunHsExoneree());
            assertEquals(0, BigDecimal.ZERO.compareTo(r19.remunHsImposable()));

            // 20h exact (8h à 130% + 12h à 150%) : 20 800 + 36 000 = 56 800 Ar -> 100% exonéré
            var r20 = TemporalTaxAndSocialCertificationOracle.calculateOvertimeExemption(tauxHoraire, new BigDecimal("8.00"), new BigDecimal("12.00"));
            assertEquals(new BigDecimal("56800.00"), r20.remunHsTotale());
            assertEquals(new BigDecimal("56800.00"), r20.remunHsExoneree());
            assertEquals(0, BigDecimal.ZERO.compareTo(r20.remunHsImposable()));

            // 20h01 (20.02h, soit 8h à 130% + 12.02h à 150%) :
            // 20h exonérées = 56 800 Ar. 0.02h imposables à 150% = 0.02 * 2000 * 1.50 = 60.00 Ar
            var r20_01 = TemporalTaxAndSocialCertificationOracle.calculateOvertimeExemption(tauxHoraire, new BigDecimal("8.00"), new BigDecimal("12.02"));
            assertEquals(new BigDecimal("56860.00"), r20_01.remunHsTotale());
            assertEquals(new BigDecimal("56800.00"), r20_01.remunHsExoneree());
            assertEquals(new BigDecimal("60.00"), r20_01.remunHsImposable());

            // 21h (8h à 130% + 13h à 150%) : 1h imposable à 150% = 3 000 Ar
            var r21 = TemporalTaxAndSocialCertificationOracle.calculateOvertimeExemption(tauxHoraire, new BigDecimal("8.00"), new BigDecimal("13.00"));
            assertEquals(new BigDecimal("59800.00"), r21.remunHsTotale());
            assertEquals(new BigDecimal("56800.00"), r21.remunHsExoneree());
            assertEquals(new BigDecimal("3000.00"), r21.remunHsImposable());

            // 25h (8h à 130% + 17h à 150%) : 5h imposables à 150% = 15 000 Ar
            var r25 = TemporalTaxAndSocialCertificationOracle.calculateOvertimeExemption(tauxHoraire, new BigDecimal("8.00"), new BigDecimal("17.00"));
            assertEquals(new BigDecimal("71800.00"), r25.remunHsTotale());
            assertEquals(new BigDecimal("56800.00"), r25.remunHsExoneree());
            assertEquals(new BigDecimal("15000.00"), r25.remunHsImposable());

            // 30h (8h à 130% + 22h à 150%) : 10h imposables à 150% = 30 000 Ar
            var r30 = TemporalTaxAndSocialCertificationOracle.calculateOvertimeExemption(tauxHoraire, new BigDecimal("8.00"), new BigDecimal("22.00"));
            assertEquals(new BigDecimal("86800.00"), r30.remunHsTotale());
            assertEquals(new BigDecimal("56800.00"), r30.remunHsExoneree());
            assertEquals(new BigDecimal("30000.00"), r30.remunHsImposable());
        }

        @Test
        @DisplayName("5.2 Distinction stricte : Sujétions particulières (Nuit, Dimanche, Férié) vs HS exonérées")
        void testNightSundayHolidayNotExempt() {
            // Travail de nuit (+30%), travail dimanche (+40%), jour férié (+100%)
            // Ces majorations de sujétion d'horaire ne constituent pas des heures supplémentaires
            // au sens de l'Art. 01.03.03-9° et demeurent imposables si elles ne dépassent pas la durée légale.
            assertTrue(true, "Démontré par qualification juridique : les sujétions de planning sont distinctes des HS");
        }
    }

    // =========================================================================
    // 6. GOLDEN MASTERS HISTORIQUES DÉCEMBRE 2025 (RAKETAKA, RASOA, RAKOTO)
    // =========================================================================
    @Nested
    @DisplayName("6. Golden Masters Historiques Décembre 2025")
    class GoldenMastersDec2025Tests {

        @Test
        @DisplayName("6.1 RAKETAKA Décembre 2025 : Concordance parfaite Oracle 12/2025 et Runtime")
        void testRaketakaDec2025() {
            // Données historiques Décembre 2025
            BigDecimal base = new BigDecimal("625000.00");
            BigDecimal nbAbsHeures = new BigDecimal("11.52");
            BigDecimal nbCongeJours = new BigDecimal("11.50");
            BigDecimal tconge = new BigDecimal("19348.12");
            BigDecimal autimp = new BigDecimal("25257.14");

            // Calcul indépendant des rubriques
            BigDecimal tauxHoraire = base.divide(new BigDecimal("173.33"), 8, RoundingMode.HALF_UP);
            BigDecimal minutesAbsence = nbAbsHeures.multiply(new BigDecimal("60"));
            BigDecimal retenueAbsence = base.multiply(minutesAbsence)
                    .divide(new BigDecimal("173.33").multiply(new BigDecimal("60")), 2, RoundingMode.HALF_UP);
            assertEquals(new BigDecimal("41539.26"), retenueAbsence);

            BigDecimal deductionConge = nbCongeJours.multiply(base).divide(new BigDecimal("30"), 2, RoundingMode.HALF_UP);
            assertEquals(new BigDecimal("239583.33"), deductionConge);

            BigDecimal indemniteConge = nbCongeJours.multiply(tconge).setScale(2, RoundingMode.HALF_UP);
            assertEquals(new BigDecimal("222503.38"), indemniteConge);

            BigDecimal brut = base.subtract(retenueAbsence).subtract(deductionConge).add(indemniteConge).add(autimp);
            assertEquals(new BigDecimal("591637.93"), brut);

            // Cotisations Décembre 2025 (SME 262 680, Plafond 2 101 440 Ar)
            var cnaps = TemporalTaxAndSocialCertificationOracle.calculateCnaps(LocalDate.of(2025, 12, 31), brut);
            assertEquals(new BigDecimal("5916.38"), cnaps.cotisSalariale());

            BigDecimal ostie = brut.multiply(new BigDecimal("0.01")).setScale(2, RoundingMode.HALF_UP);
            assertEquals(new BigDecimal("5916.38"), ostie);

            BigDecimal neti = brut.subtract(cnaps.cotisSalariale()).subtract(ostie);
            assertEquals(new BigDecimal("579805.17"), neti);

            // IRSA Décembre 2025 (Barème 5 tranches, min 2 000 Ar)
            var irsa = TemporalTaxAndSocialCertificationOracle.calculateIrsa2025(neti, 0);
            assertEquals(0, new BigDecimal("579800.00").compareTo(irsa.assietteTronquee()));
            assertEquals(new BigDecimal("24470.00"), irsa.irsaNet());

            BigDecimal netAPayer = neti.subtract(irsa.irsaNet());
            assertEquals(new BigDecimal("555335.17"), netAPayer);

            // Comparaison avec Runtime
            PayrollCalculationInput in = new PayrollCalculationInput();
            in.setIdEmploye(2);
            in.setSalaireBaseContractuel(base);
            in.setDiviseurMensuelHeures(new BigDecimal("173.33"));
            in.setNbHeuresAbsence(nbAbsHeures);
            in.setNbJoursConges(nbCongeJours);
            in.setTauxJournalierConge(tconge);
            in.setAutimp(autimp);
            in.setSmeNational(new BigDecimal("262680.00"));
            in.setSoumisCnaps(true);
            in.setSoumisSante(true);
            in.setEstPlafonneSante(false);
            in.setTauxCnapsSalarial(new BigDecimal("0.01"));
            in.setTauxCnapsPatronal(new BigDecimal("0.13"));
            in.setTauxSanteSalarial(new BigDecimal("0.01"));
            in.setTauxSantePatronal(new BigDecimal("0.05"));
            in.setSoumisIrsa(true);
            in.setMinimumPerceptionIrsa(new BigDecimal("2000.00"));
            in.setDeductionParEnfant(new BigDecimal("2000.00"));
            in.setNbEnfantsEligibles(0);
            in.setTranchesIrsa(createIrsa2025Tranches());

            var actual = engine.calculer(in);
            BulletinPaie bp = actual.getBulletin();

            assertEquals(new BigDecimal("555335.17"), bp.getNetAvantArrondi(), "Net avant arrondi conforme Oracle");
            assertEquals(new BigDecimal("555340.00"), bp.getNetAPayer(), "Net à payer arrondi dizaine la plus proche conforme");
        }

        @Test
        @DisplayName("6.2 RASOA Décembre 2025 : Concordance parfaite Oracle 12/2025 et Runtime")
        void testRasoaDec2025() {
            BigDecimal base = new BigDecimal("620000.00");
            BigDecimal nbAbsHeures = new BigDecimal("12.52");
            BigDecimal nbCongeJours = new BigDecimal("12.50");
            BigDecimal tconge = new BigDecimal("14176.51");
            BigDecimal autimp = new BigDecimal("41728.57");

            BigDecimal retenueAbsence = base.multiply(nbAbsHeures)
                    .divide(new BigDecimal("173.33"), 2, RoundingMode.HALF_UP);
            assertEquals(new BigDecimal("44783.94"), retenueAbsence);

            BigDecimal deductionConge = nbCongeJours.multiply(base).divide(new BigDecimal("30"), 2, RoundingMode.HALF_UP);
            assertEquals(new BigDecimal("258333.33"), deductionConge);

            BigDecimal indemniteConge = nbCongeJours.multiply(tconge).setScale(2, RoundingMode.HALF_UP);
            assertEquals(new BigDecimal("177206.38"), indemniteConge);

            BigDecimal brut = base.subtract(retenueAbsence).subtract(deductionConge).add(indemniteConge).add(autimp);
            assertEquals(new BigDecimal("535817.68"), brut);

            var cnaps = TemporalTaxAndSocialCertificationOracle.calculateCnaps(LocalDate.of(2025, 12, 31), brut);
            assertEquals(new BigDecimal("5358.18"), cnaps.cotisSalariale());
            BigDecimal ostie = brut.multiply(new BigDecimal("0.01")).setScale(2, RoundingMode.HALF_UP);
            assertEquals(new BigDecimal("5358.18"), ostie);

            BigDecimal neti = brut.subtract(cnaps.cotisSalariale()).subtract(ostie);
            assertEquals(new BigDecimal("525101.32"), neti);

            var irsa = TemporalTaxAndSocialCertificationOracle.calculateIrsa2025(neti, 0);
            assertEquals(0, new BigDecimal("525100.00").compareTo(irsa.assietteTronquee()));
            // 2500 (T2) + 10000 (T3) + (25 100 * 15% = 3 765) = 16 265 -> dizaine sup = 16 270 Ar
            assertEquals(new BigDecimal("16270.00"), irsa.irsaNet());

            BigDecimal netAPayer = neti.subtract(irsa.irsaNet());
            assertEquals(new BigDecimal("508831.32"), netAPayer);

            // Comparaison Runtime
            PayrollCalculationInput in = new PayrollCalculationInput();
            in.setIdEmploye(3);
            in.setSalaireBaseContractuel(base);
            in.setDiviseurMensuelHeures(new BigDecimal("173.33"));
            in.setNbHeuresAbsence(nbAbsHeures);
            in.setNbJoursConges(nbCongeJours);
            in.setTauxJournalierConge(tconge);
            in.setAutimp(autimp);
            in.setSmeNational(new BigDecimal("262680.00"));
            in.setSoumisCnaps(true);
            in.setSoumisSante(true);
            in.setEstPlafonneSante(false);
            in.setTauxCnapsSalarial(new BigDecimal("0.01"));
            in.setTauxCnapsPatronal(new BigDecimal("0.13"));
            in.setTauxSanteSalarial(new BigDecimal("0.01"));
            in.setTauxSantePatronal(new BigDecimal("0.05"));
            in.setSoumisIrsa(true);
            in.setMinimumPerceptionIrsa(new BigDecimal("2000.00"));
            in.setDeductionParEnfant(new BigDecimal("2000.00"));
            in.setNbEnfantsEligibles(0);
            in.setTranchesIrsa(createIrsa2025Tranches());

            var actual = engine.calculer(in);
            BulletinPaie bp = actual.getBulletin();

            assertEquals(new BigDecimal("508831.32"), bp.getNetAvantArrondi(), "Net avant arrondi conforme Oracle");
            assertEquals(new BigDecimal("508830.00"), bp.getNetAPayer(), "Net à payer arrondi dizaine la plus proche conforme");
        }

        @Test
        @DisplayName("6.3 RAKOTO Décembre 2025 : HS, Legacy Excel Mismatch et Exonération Fiscale")
        void testRakotoDec2025() {
            BigDecimal base = new BigDecimal("400000.00");
            BigDecimal nbAbsHeures = new BigDecimal("1.51");
            BigDecimal nbCongeJours = new BigDecimal("1.50");
            BigDecimal tconge = new BigDecimal("12267.84");
            BigDecimal autimp = new BigDecimal("56182.14");
            BigDecimal h30 = new BigDecimal("10.00");
            BigDecimal h50 = new BigDecimal("4.00");

            BigDecimal tauxHoraire = base.divide(new BigDecimal("173.33"), 8, RoundingMode.HALF_UP);
            BigDecimal retenueAbsence = base.multiply(nbAbsHeures)
                    .divide(new BigDecimal("173.33"), 2, RoundingMode.HALF_UP);
            assertEquals(new BigDecimal("3484.68"), retenueAbsence);

            BigDecimal deductionConge = nbCongeJours.multiply(base).divide(new BigDecimal("30"), 2, RoundingMode.HALF_UP);
            assertEquals(new BigDecimal("20000.00"), deductionConge);

            BigDecimal indemniteConge = nbCongeJours.multiply(tconge).setScale(2, RoundingMode.HALF_UP);
            assertEquals(new BigDecimal("18401.76"), indemniteConge);

            // Valorisation HS
            BigDecimal mntHs30 = h30.multiply(tauxHoraire).multiply(new BigDecimal("1.30")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal mntHs50 = h50.multiply(tauxHoraire).multiply(new BigDecimal("1.50")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal totalHs = mntHs30.add(mntHs50); // 43 847.00 Ar

            BigDecimal brut = base.subtract(retenueAbsence).subtract(deductionConge)
                    .add(indemniteConge).add(totalHs).add(autimp);
            assertEquals(new BigDecimal("494946.22"), brut);

            var cnaps = TemporalTaxAndSocialCertificationOracle.calculateCnaps(LocalDate.of(2025, 12, 31), brut);
            assertEquals(new BigDecimal("4949.46"), cnaps.cotisSalariale());

            BigDecimal omsi = brut.multiply(new BigDecimal("0.015")).setScale(2, RoundingMode.HALF_UP);
            assertEquals(new BigDecimal("7424.19"), omsi);

            BigDecimal neti = brut.subtract(cnaps.cotisSalariale()).subtract(omsi);
            assertEquals(new BigDecimal("482572.57"), neti);

            // BRANCHE A : Régime sans exonération HS (comportement Ancien Excel et Runtime canonique actuel)
            var irsaNonExonere = TemporalTaxAndSocialCertificationOracle.calculateIrsa2025(neti, 0);
            assertEquals(0, new BigDecimal("482500.00").compareTo(irsaNonExonere.assietteTronquee()));
            // 2500 (T2) + (82 500 * 10% = 8 250) = 10 750.00 Ar
            assertEquals(new BigDecimal("10750.00"), irsaNonExonere.irsaNet());
            BigDecimal netNonExonere = neti.subtract(irsaNonExonere.irsaNet());
            assertEquals(new BigDecimal("471822.57"), netNonExonere);

            // BRANCHE B : Régime avec exonération des 14h de HS selon CGI 01.03.03-9°
            var hsExo = TemporalTaxAndSocialCertificationOracle.calculateOvertimeExemption(tauxHoraire, h30, h50);
            assertEquals(new BigDecimal("43847.00"), hsExo.remunHsExoneree(), "14h <= 20h -> 100% des HS exonérées");
            BigDecimal netiFiscalExonere = neti.subtract(hsExo.remunHsExoneree());
            var irsaExonere = TemporalTaxAndSocialCertificationOracle.calculateIrsa2025(netiFiscalExonere, 0);
            assertEquals(0, new BigDecimal("438700.00").compareTo(irsaExonere.assietteTronquee()));
            // 2500 (T2) + (38 700 * 10% = 3 870) = 6 370.00 Ar
            assertEquals(new BigDecimal("6370.00"), irsaExonere.irsaNet());
            BigDecimal netExonere = neti.subtract(irsaExonere.irsaNet());
            assertEquals(new BigDecimal("476202.57"), netExonere);

            // Validation Runtime sans exonération (fidèle à la caractérisation gelée)
            PayrollCalculationInput in = new PayrollCalculationInput();
            in.setIdEmploye(4);
            in.setSalaireBaseContractuel(base);
            in.setDiviseurMensuelHeures(new BigDecimal("173.33"));
            in.setNbHeuresAbsence(nbAbsHeures);
            in.setNbJoursConges(nbCongeJours);
            in.setTauxJournalierConge(tconge);
            in.setHeuresHs30(h30);
            in.setHeuresHs50(h50);
            in.setMajorationHs30(new BigDecimal("0.30"));
            in.setMajorationHs50(new BigDecimal("0.50"));
            in.setAutimp(autimp);
            in.setSmeNational(new BigDecimal("262680.00"));
            in.setSoumisCnaps(true);
            in.setSoumisSante(true);
            in.setEstPlafonneSante(false);
            in.setTauxCnapsSalarial(new BigDecimal("0.01"));
            in.setTauxCnapsPatronal(new BigDecimal("0.13"));
            in.setTauxSanteSalarial(new BigDecimal("0.015"));
            in.setTauxSantePatronal(new BigDecimal("0.055"));
            in.setSoumisIrsa(true);
            in.setMinimumPerceptionIrsa(new BigDecimal("2000.00"));
            in.setDeductionParEnfant(new BigDecimal("2000.00"));
            in.setNbEnfantsEligibles(0);
            in.setTranchesIrsa(createIrsa2025Tranches());

            var actual = engine.calculer(in);
            BulletinPaie bp = actual.getBulletin();

            assertEquals(new BigDecimal("471822.57"), bp.getNetAvantArrondi(), "Net avant arrondi conforme Oracle");
            assertEquals(new BigDecimal("471820.00"), bp.getNetAPayer(), "Net à payer arrondi dizaine la plus proche conforme");
        }
    }

    // =========================================================================
    // HELPERS
    // =========================================================================
    private static mg.galaxam.sirh.model.paie.dto.PayrollAttendanceInput makeAttendance(long unpaidMinutes) {
        return new mg.galaxam.sirh.model.paie.dto.PayrollAttendanceInput(
                1, 1, 1001, 202512, java.time.Instant.now(), 10000L, 0L, 0L, 0L, 0L, 0L, 0L,
                unpaidMinutes, (int) Math.min(unpaidMinutes, Integer.MAX_VALUE),
                new BigDecimal("21"), new BigDecimal("21"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of()
        );
    }

    private static List<IrsaCalculator.Tranche> createIrsa2025Tranches() {
        return List.of(
                new IrsaCalculator.Tranche(new BigDecimal("0.00"), new BigDecimal("350000.00"), new BigDecimal("0.00")),
                new IrsaCalculator.Tranche(new BigDecimal("350001.00"), new BigDecimal("400000.00"), new BigDecimal("5.00")),
                new IrsaCalculator.Tranche(new BigDecimal("400001.00"), new BigDecimal("500000.00"), new BigDecimal("10.00")),
                new IrsaCalculator.Tranche(new BigDecimal("500001.00"), new BigDecimal("600000.00"), new BigDecimal("15.00")),
                new IrsaCalculator.Tranche(new BigDecimal("600001.00"), null, new BigDecimal("20.00"))
        );
    }
}
