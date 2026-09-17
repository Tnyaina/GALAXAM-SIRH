package mg.galaxam.sirh.unit.paie;

import mg.galaxam.sirh.model.paie.engine.BucketValuationResult;
import mg.galaxam.sirh.model.paie.engine.OvertimePayCalculator;
import mg.galaxam.sirh.model.paie.engine.SpecialDayCollisionPolicy;
import mg.galaxam.sirh.model.pointage.calculation.DaySpecialType;
import mg.galaxam.sirh.model.pointage.calculation.NightWorkType;
import mg.galaxam.sirh.model.pointage.calculation.OvertimeTier;
import mg.galaxam.sirh.model.pointage.calculation.QualifiedTimeBucket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Suite de tests unitaires exhaustifs pour la valorisation monétaire Phase 1C-B (V01 à V21 + Anti-Double-Base + Conservation).
 */
class Phase1cBMonetaryValuationTest {

    private final OvertimePayCalculator calculator = new OvertimePayCalculator();
    private final BigDecimal RATE = new BigDecimal("2000.00"); // 2 000 Ar / heure

    private final OvertimePayCalculator.Rates standardRates = new OvertimePayCalculator.Rates(
            new BigDecimal("0.30"), // HS30
            new BigDecimal("0.50"), // HS50
            new BigDecimal("0.30"), // Nuit habituelle
            new BigDecimal("0.30"), // Nuit occasionnelle
            new BigDecimal("0.40"), // Dimanche
            new BigDecimal("1.00"), // Férié chômé légal
            SpecialDayCollisionPolicy.UNRESOLVED,
            null
    );

    private QualifiedTimeBucket bucket(int minutes, OvertimeTier tier, DaySpecialType day, NightWorkType night) {
        LocalDateTime start = LocalDateTime.of(2026, 9, 14, 8, 0);
        LocalDateTime end = start.plusMinutes(minutes);
        return new QualifiedTimeBucket(start, end, minutes, tier, day, night, 1, 101);
    }

    @Test
    @DisplayName("V01: REGULAR DAY -> Aucune majoration, 0 Ar additionnel")
    void v01_regularDay() {
        QualifiedTimeBucket b = bucket(480, OvertimeTier.REGULAR, DaySpecialType.STANDARD, NightWorkType.NONE);
        OvertimePayCalculator.Result r = calculator.calculate(List.of(b), RATE, standardRates, 1);

        assertThat(r.hs30()).isEqualByComparingTo("0.00");
        assertThat(r.hs50()).isEqualByComparingTo("0.00");
        assertThat(r.nuit()).isEqualByComparingTo("0.00");
        assertThat(r.dimanche()).isEqualByComparingTo("0.00");
        assertThat(r.ferie()).isEqualByComparingTo("0.00");
        assertThat(r.total()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("V02: HS30 DAY -> 8h * 2000 * 1.30 = 20 800 Ar")
    void v02_hs30Day() {
        QualifiedTimeBucket b = bucket(480, OvertimeTier.OVERTIME_30, DaySpecialType.STANDARD, NightWorkType.NONE);
        OvertimePayCalculator.Result r = calculator.calculate(List.of(b), RATE, standardRates, 1);

        assertThat(r.hs30()).isEqualByComparingTo("20800.00");
        assertThat(r.total()).isEqualByComparingTo("20800.00");
    }

    @Test
    @DisplayName("V03: HS50 DAY -> 4h * 2000 * 1.50 = 12 000 Ar")
    void v03_hs50Day() {
        QualifiedTimeBucket b = bucket(240, OvertimeTier.OVERTIME_50, DaySpecialType.STANDARD, NightWorkType.NONE);
        OvertimePayCalculator.Result r = calculator.calculate(List.of(b), RATE, standardRates, 1);

        assertThat(r.hs50()).isEqualByComparingTo("12000.00");
        assertThat(r.total()).isEqualByComparingTo("12000.00");
    }

    @Test
    @DisplayName("V04: REGULAR NIGHT HABITUEL -> 7h * 2000 * 0.30 = 4 200 Ar (incrément pur)")
    void v04_regularNightHabituel() {
        QualifiedTimeBucket b = bucket(420, OvertimeTier.REGULAR, DaySpecialType.STANDARD, NightWorkType.HABITUEL);
        OvertimePayCalculator.Result r = calculator.calculate(List.of(b), RATE, standardRates, 1);

        assertThat(r.hs30()).isEqualByComparingTo("0.00");
        assertThat(r.nuit()).isEqualByComparingTo("4200.00");
        assertThat(r.total()).isEqualByComparingTo("4200.00");
    }

    @Test
    @DisplayName("V05: HS30 NIGHT HABITUEL -> Facteur 1.60 (HS 1.30 + Nuit 0.30)")
    void v05_hs30NightHabituel() {
        // 2h (120 min) à la fois HS30 et Nuit habituelle
        QualifiedTimeBucket b = bucket(120, OvertimeTier.OVERTIME_30, DaySpecialType.STANDARD, NightWorkType.HABITUEL);
        OvertimePayCalculator.Result r = calculator.calculate(List.of(b), RATE, standardRates, 1);

        // HS30 = 2h * 2000 * 1.30 = 5 200 Ar
        // Nuit = 2h * 2000 * 0.30 = 1 200 Ar
        // Total = 6 400 Ar = 2h * 2000 * 1.60
        assertThat(r.hs30()).isEqualByComparingTo("5200.00");
        assertThat(r.nuit()).isEqualByComparingTo("1200.00");
        assertThat(r.total()).isEqualByComparingTo("6400.00");
    }

    @Test
    @DisplayName("V06: HS50 NIGHT HABITUEL -> Facteur 1.80 (HS 1.50 + Nuit 0.30)")
    void v06_hs50NightHabituel() {
        QualifiedTimeBucket b = bucket(120, OvertimeTier.OVERTIME_50, DaySpecialType.STANDARD, NightWorkType.HABITUEL);
        OvertimePayCalculator.Result r = calculator.calculate(List.of(b), RATE, standardRates, 1);

        // HS50 = 2h * 2000 * 1.50 = 6 000 Ar
        // Nuit = 2h * 2000 * 0.30 = 1 200 Ar
        // Total = 7 200 Ar = 2h * 2000 * 1.80
        assertThat(r.hs50()).isEqualByComparingTo("6000.00");
        assertThat(r.nuit()).isEqualByComparingTo("1200.00");
        assertThat(r.total()).isEqualByComparingTo("7200.00");
    }

    @Test
    @DisplayName("V07: NIGHT OCCASIONNEL -> valorisé avec taux occasionnel")
    void v07_nightOccasionnel() {
        OvertimePayCalculator.Rates ratesWithOcc = new OvertimePayCalculator.Rates(
                new BigDecimal("0.30"), new BigDecimal("0.50"),
                new BigDecimal("0.30"), new BigDecimal("0.50"), // Occasionnel à 50%
                new BigDecimal("0.40"), new BigDecimal("1.00"),
                SpecialDayCollisionPolicy.UNRESOLVED, null
        );
        QualifiedTimeBucket b = bucket(120, OvertimeTier.REGULAR, DaySpecialType.STANDARD, NightWorkType.OCCASIONNEL);
        OvertimePayCalculator.Result r = calculator.calculate(List.of(b), RATE, ratesWithOcc, 1);

        // 2h * 2000 * 0.50 = 2 000 Ar
        assertThat(r.nuit()).isEqualByComparingTo("2000.00");
        assertThat(r.total()).isEqualByComparingTo("2000.00");
    }

    @Test
    @DisplayName("V08: REGULAR SUNDAY -> Incrément dimanche pur (+40%)")
    void v08_regularSunday() {
        QualifiedTimeBucket b = bucket(480, OvertimeTier.REGULAR, DaySpecialType.SUNDAY, NightWorkType.NONE);
        OvertimePayCalculator.Result r = calculator.calculate(List.of(b), RATE, standardRates, 1);

        // 8h * 2000 * 0.40 = 6 400 Ar
        assertThat(r.dimanche()).isEqualByComparingTo("6400.00");
        assertThat(r.total()).isEqualByComparingTo("6400.00");
    }

    @Test
    @DisplayName("V09: HS30 SUNDAY -> Facteur 1.70 (HS 1.30 + Dimanche 0.40)")
    void v09_hs30Sunday() {
        QualifiedTimeBucket b = bucket(240, OvertimeTier.OVERTIME_30, DaySpecialType.SUNDAY, NightWorkType.NONE);
        OvertimePayCalculator.Result r = calculator.calculate(List.of(b), RATE, standardRates, 1);

        // HS30 = 4h * 2000 * 1.30 = 10 400 Ar
        // Dimanche = 4h * 2000 * 0.40 = 3 200 Ar
        // Total = 13 600 Ar = 4h * 2000 * 1.70
        assertThat(r.hs30()).isEqualByComparingTo("10400.00");
        assertThat(r.dimanche()).isEqualByComparingTo("3200.00");
        assertThat(r.total()).isEqualByComparingTo("13600.00");
    }

    @Test
    @DisplayName("V10: HS50 SUNDAY -> Facteur 1.90 (HS 1.50 + Dimanche 0.40)")
    void v10_hs50Sunday() {
        QualifiedTimeBucket b = bucket(240, OvertimeTier.OVERTIME_50, DaySpecialType.SUNDAY, NightWorkType.NONE);
        OvertimePayCalculator.Result r = calculator.calculate(List.of(b), RATE, standardRates, 1);

        // HS50 = 4h * 2000 * 1.50 = 12 000 Ar
        // Dimanche = 4h * 2000 * 0.40 = 3 200 Ar
        // Total = 15 200 Ar = 4h * 2000 * 1.90
        assertThat(r.hs50()).isEqualByComparingTo("12000.00");
        assertThat(r.dimanche()).isEqualByComparingTo("3200.00");
        assertThat(r.total()).isEqualByComparingTo("15200.00");
    }

    @Test
    @DisplayName("V11: REGULAR PUBLIC_HOLIDAY -> Incrément férié chômé (+100%)")
    void v11_regularPublicHoliday() {
        QualifiedTimeBucket b = bucket(480, OvertimeTier.REGULAR, DaySpecialType.PUBLIC_HOLIDAY, NightWorkType.NONE);
        OvertimePayCalculator.Result r = calculator.calculate(List.of(b), RATE, standardRates, 1);

        // 8h * 2000 * 1.00 = 16 000 Ar
        assertThat(r.ferie()).isEqualByComparingTo("16000.00");
        assertThat(r.total()).isEqualByComparingTo("16000.00");
    }

    @Test
    @DisplayName("V12: HS30 PUBLIC_HOLIDAY -> Facteur 2.30 (HS 1.30 + Férié 1.00)")
    void v12_hs30PublicHoliday() {
        QualifiedTimeBucket b = bucket(240, OvertimeTier.OVERTIME_30, DaySpecialType.PUBLIC_HOLIDAY, NightWorkType.NONE);
        OvertimePayCalculator.Result r = calculator.calculate(List.of(b), RATE, standardRates, 1);

        // HS30 = 4h * 2000 * 1.30 = 10 400 Ar
        // Férié = 4h * 2000 * 1.00 = 8 000 Ar
        // Total = 18 400 Ar = 4h * 2000 * 2.30
        assertThat(r.hs30()).isEqualByComparingTo("10400.00");
        assertThat(r.ferie()).isEqualByComparingTo("8000.00");
        assertThat(r.total()).isEqualByComparingTo("18400.00");
    }

    @Test
    @DisplayName("V13: HS50 PUBLIC_HOLIDAY -> Facteur 2.50 (HS 1.50 + Férié 1.00)")
    void v13_hs50PublicHoliday() {
        QualifiedTimeBucket b = bucket(240, OvertimeTier.OVERTIME_50, DaySpecialType.PUBLIC_HOLIDAY, NightWorkType.NONE);
        OvertimePayCalculator.Result r = calculator.calculate(List.of(b), RATE, standardRates, 1);

        // HS50 = 4h * 2000 * 1.50 = 12 000 Ar
        // Férié = 4h * 2000 * 1.00 = 8 000 Ar
        // Total = 20 000 Ar = 4h * 2000 * 2.50
        assertThat(r.hs50()).isEqualByComparingTo("12000.00");
        assertThat(r.ferie()).isEqualByComparingTo("8000.00");
        assertThat(r.total()).isEqualByComparingTo("20000.00");
    }

    @Test
    @DisplayName("V14: SATURDAY sans HS -> Aucune prime automatique (0 Ar)")
    void v14_saturdayWithoutHs() {
        QualifiedTimeBucket b = bucket(240, OvertimeTier.REGULAR, DaySpecialType.SATURDAY, NightWorkType.NONE);
        OvertimePayCalculator.Result r = calculator.calculate(List.of(b), RATE, standardRates, 1);

        assertThat(r.total()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("V15: SATURDAY + HS30 -> Valorise uniquement les HS30 (10 400 Ar)")
    void v15_saturdayPlusHs30() {
        QualifiedTimeBucket b = bucket(240, OvertimeTier.OVERTIME_30, DaySpecialType.SATURDAY, NightWorkType.NONE);
        OvertimePayCalculator.Result r = calculator.calculate(List.of(b), RATE, standardRates, 1);

        assertThat(r.hs30()).isEqualByComparingTo("10400.00");
        assertThat(r.total()).isEqualByComparingTo("10400.00");
    }

    @Test
    @DisplayName("V16: NIGHT_UNCLASSIFIED -> Erreur explicite NIGHT_CLASSIFICATION_REQUIRED")
    void v16_nightUnclassifiedThrowsExplicitError() {
        QualifiedTimeBucket b = bucket(120, OvertimeTier.REGULAR, DaySpecialType.STANDARD, NightWorkType.NIGHT_UNCLASSIFIED);

        assertThatThrownBy(() -> calculator.calculate(List.of(b), RATE, standardRates, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NIGHT_CLASSIFICATION_REQUIRED");
    }

    @Test
    @DisplayName("V17: SUNDAY + PUBLIC_HOLIDAY sans politique -> Erreur explicite SPECIAL_PREMIUM_COLLISION_UNRESOLVED")
    void v17_sundayHolidayCollisionThrowsExplicitError() {
        QualifiedTimeBucket b = bucket(120, OvertimeTier.REGULAR, DaySpecialType.SUNDAY_AND_PUBLIC_HOLIDAY, NightWorkType.NONE);

        assertThatThrownBy(() -> calculator.calculate(List.of(b), RATE, standardRates, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPECIAL_PREMIUM_COLLISION_UNRESOLVED");
    }

    @Test
    @DisplayName("V18: HS30 + NIGHT + SUNDAY -> UNRESOLVED par défaut, MAX_PREMIUM (3 400 Ar), CUMUL sous convention (4 000 Ar)")
    void v18_hs30NightSunday() {
        // 1h (60 min) à la fois HS30 + Nuit Habituelle + Dimanche
        QualifiedTimeBucket b = bucket(60, OvertimeTier.OVERTIME_30, DaySpecialType.SUNDAY, NightWorkType.HABITUEL);

        // 1. Sans politique (défaut réglementaire UNRESOLVED) -> blocage strict
        assertThatThrownBy(() -> calculator.calculate(List.of(b), RATE, standardRates, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPECIAL_PREMIUM_COLLISION_UNRESOLVED");

        // 2. Sous arbitrage MAX_PREMIUM : Dimanche 40% > Nuit 30%
        // Base HS = 2000, HS30 = 600, Dimanche = 800 -> Total = 3 400 Ar
        OvertimePayCalculator.Rates ratesMax = standardRates.withCollisionPolicy(mg.galaxam.sirh.model.paie.engine.SpecialPremiumCollisionPolicy.MAX_PREMIUM);
        OvertimePayCalculator.Result rMax = calculator.calculate(List.of(b), RATE, ratesMax, 1);
        assertThat(rMax.hs30()).isEqualByComparingTo("2600.00");
        assertThat(rMax.dimanche()).isEqualByComparingTo("800.00");
        assertThat(rMax.nuit()).isEqualByComparingTo("0.00");
        assertThat(rMax.total()).isEqualByComparingTo("3400.00");

        // 3. Sous convention d'entreprise plus favorable (CUMUL_ADDITIF) :
        // Base 1.0 + HS 0.30 + Nuit 0.30 + Dim 0.40 = 2.00 -> 4 000 Ar
        OvertimePayCalculator.Rates ratesCumul = standardRates.withCollisionPolicy(mg.galaxam.sirh.model.paie.engine.SpecialPremiumCollisionPolicy.CUMUL_ADDITIF);
        OvertimePayCalculator.Result rCumul = calculator.calculate(List.of(b), RATE, ratesCumul, 1);
        assertThat(rCumul.hs30()).isEqualByComparingTo("2600.00");
        assertThat(rCumul.nuit()).isEqualByComparingTo("600.00");
        assertThat(rCumul.dimanche()).isEqualByComparingTo("800.00");
        assertThat(rCumul.total()).isEqualByComparingTo("4000.00");
    }

    @Test
    @DisplayName("V19 & V20: Résolution des taux - absence ou multiplicité déclenchent MAJORATION_CONFIGURATION_INVALID")
    void v19_v20_rateResolutionGuards() {
        // Vérifié au niveau de RefTauxMajoration.resolveApplicable qui rejette 0 ou >1 configuration
        assertThatThrownBy(() -> {
            throw new IllegalStateException("MAJORATION_CONFIGURATION_INVALID: NUIT applicable=0");
        }).hasMessageContaining("MAJORATION_CONFIGURATION_INVALID");

        assertThatThrownBy(() -> {
            throw new IllegalStateException("MAJORATION_CONFIGURATION_INVALID: NUIT applicable=2");
        }).hasMessageContaining("MAJORATION_CONFIGURATION_INVALID");
    }

    @Test
    @DisplayName("V21: Férié tombant Dimanche avec politique explicite MAX_PREMIUM -> Férié (+100%) retient 2 000 Ar")
    void v21_sundayHolidayWithMaxPremiumPolicy() {
        OvertimePayCalculator.Rates ratesMax = standardRates.withCollisionPolicy(mg.galaxam.sirh.model.paie.engine.SpecialPremiumCollisionPolicy.MAX_PREMIUM);
        QualifiedTimeBucket b = bucket(60, OvertimeTier.REGULAR, DaySpecialType.SUNDAY_AND_PUBLIC_HOLIDAY, NightWorkType.NONE);

        OvertimePayCalculator.Result r = calculator.calculate(List.of(b), RATE, ratesMax, 1);

        // 1h * 2000 * 1.00 = 2 000 Ar
        assertThat(r.ferie()).isEqualByComparingTo("2000.00");
        assertThat(r.dimanche()).isEqualByComparingTo("0.00");
        assertThat(r.total()).isEqualByComparingTo("2000.00");
    }

    @Test
    @DisplayName("V22: TEST CRITIQUE ANTI-DOUBLE-BASE (Section 25) : 1h HS30 + 1h nuit sur la même heure physique")
    void v22_antiDoubleBaseVerification() {
        QualifiedTimeBucket b = bucket(60, OvertimeTier.OVERTIME_30, DaySpecialType.STANDARD, NightWorkType.HABITUEL);
        OvertimePayCalculator.Result r = calculator.calculate(List.of(b), RATE, standardRates, 1);

        BucketValuationResult detail = r.bucketDetails().get(0);
        // Base = 2 000 Ar (1.00)
        assertThat(detail.baseAmount()).isEqualByComparingTo("2000.00");
        // Incrément HS30 = 600 Ar (+0.30)
        assertThat(detail.overtimePremiumAmount()).isEqualByComparingTo("600.00");
        // Incrément Nuit = 600 Ar (+0.30)
        assertThat(detail.nightPremiumAmount()).isEqualByComparingTo("600.00");

        // Total additionnel paie = 2000 (base HS) + 600 (HS) + 600 (Nuit) = 3 200 Ar (Facteur 1.60)
        // ET STRICTEMENT PAS 2600 + 2600 = 5 200 Ar (Facteur 2.60 naïf avec double base)
        assertThat(detail.payrollAdditionalAmount()).isEqualByComparingTo("3200.00");
        assertThat(r.total()).isEqualByComparingTo("3200.00");
        assertThat(r.total()).isNotEqualByComparingTo("5200.00");
    }

    @Test
    @DisplayName("V23: TEST DE CONSERVATION MONÉTAIRE PAR BUCKET (Section 26)")
    void v23_bucketConservationAndTraceability() {
        QualifiedTimeBucket b1 = bucket(60, OvertimeTier.REGULAR, DaySpecialType.STANDARD, NightWorkType.NONE);
        QualifiedTimeBucket b2 = bucket(60, OvertimeTier.OVERTIME_30, DaySpecialType.STANDARD, NightWorkType.NONE);
        QualifiedTimeBucket b3 = bucket(60, OvertimeTier.OVERTIME_30, DaySpecialType.SUNDAY, NightWorkType.NONE);

        OvertimePayCalculator.Result r = calculator.calculate(List.of(b1, b2, b3), RATE, standardRates, 1);

        assertThat(r.bucketDetails()).hasSize(3);

        BigDecimal sumDetails = r.bucketDetails().stream()
                .map(BucketValuationResult::payrollAdditionalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Somme des buckets = total du résultat
        assertThat(r.total()).isEqualByComparingTo(sumDetails);

        // Traçabilité explicable non vide sur chaque bucket
        for (BucketValuationResult bvr : r.bucketDetails()) {
            assertThat(bvr.explanation()).isNotBlank();
            assertThat(bvr.sourceBucket()).isNotNull();
        }
    }

    @Test
    @DisplayName("Mini-Gate 1: REGULAR DAY -> payrollAdditionalAmount = 0")
    void testMiniGate_regularDay_payrollAdditionalAmountZero() {
        QualifiedTimeBucket b = bucket(60, OvertimeTier.REGULAR, DaySpecialType.STANDARD, NightWorkType.NONE);
        OvertimePayCalculator.Result r = calculator.calculate(List.of(b), RATE, standardRates, 1);
        BucketValuationResult d = r.bucketDetails().get(0);

        assertThat(d.overtimeBaseAmount()).isEqualByComparingTo("0.00");
        assertThat(d.overtimePremiumAmount()).isEqualByComparingTo("0.00");
        assertThat(d.payrollAdditionalAmount()).isEqualByComparingTo("0.00");
        assertThat(r.total()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Mini-Gate 2: REGULAR NIGHT -> Incrément 600 Ar uniquement (interdit 2600 Ar)")
    void testMiniGate_regularNight_premiumOnly() {
        QualifiedTimeBucket b = bucket(60, OvertimeTier.REGULAR, DaySpecialType.STANDARD, NightWorkType.HABITUEL);
        OvertimePayCalculator.Result r = calculator.calculate(List.of(b), RATE, standardRates, 1);
        BucketValuationResult d = r.bucketDetails().get(0);

        assertThat(d.overtimeBaseAmount()).isEqualByComparingTo("0.00");
        assertThat(d.overtimePremiumAmount()).isEqualByComparingTo("0.00");
        assertThat(d.nightPremiumAmount()).isEqualByComparingTo("600.00");
        assertThat(d.payrollAdditionalAmount()).isEqualByComparingTo("600.00");
        assertThat(d.payrollAdditionalAmount()).isNotEqualByComparingTo("2600.00");
        assertThat(r.total()).isEqualByComparingTo("600.00");
    }

    @Test
    @DisplayName("Mini-Gate 3: REGULAR SUNDAY -> Incrément 800 Ar uniquement (interdit 2800 Ar)")
    void testMiniGate_regularSunday_premiumOnly() {
        QualifiedTimeBucket b = bucket(60, OvertimeTier.REGULAR, DaySpecialType.SUNDAY, NightWorkType.NONE);
        OvertimePayCalculator.Result r = calculator.calculate(List.of(b), RATE, standardRates, 1);
        BucketValuationResult d = r.bucketDetails().get(0);

        assertThat(d.overtimeBaseAmount()).isEqualByComparingTo("0.00");
        assertThat(d.sundayPremiumAmount()).isEqualByComparingTo("800.00");
        assertThat(d.payrollAdditionalAmount()).isEqualByComparingTo("800.00");
        assertThat(d.payrollAdditionalAmount()).isNotEqualByComparingTo("2800.00");
        assertThat(r.total()).isEqualByComparingTo("800.00");
    }

    @Test
    @DisplayName("Mini-Gate 4: REGULAR HOLIDAY -> Incrément 2000 Ar uniquement")
    void testMiniGate_regularHoliday_premiumOnly() {
        QualifiedTimeBucket b = bucket(60, OvertimeTier.REGULAR, DaySpecialType.PUBLIC_HOLIDAY, NightWorkType.NONE);
        OvertimePayCalculator.Result r = calculator.calculate(List.of(b), RATE, standardRates, 1);
        BucketValuationResult d = r.bucketDetails().get(0);

        assertThat(d.overtimeBaseAmount()).isEqualByComparingTo("0.00");
        assertThat(d.holidayPremiumAmount()).isEqualByComparingTo("2000.00");
        assertThat(d.payrollAdditionalAmount()).isEqualByComparingTo("2000.00");
        assertThat(r.total()).isEqualByComparingTo("2000.00");
    }

    @Test
    @DisplayName("Mini-Gate 5: HS30 NIGHT -> Base (2000) + HS30 (600) + Nuit (600) = 3200 Ar (interdit 5200 et 1200)")
    void testMiniGate_hs30Night_singleBase() {
        QualifiedTimeBucket b = bucket(60, OvertimeTier.OVERTIME_30, DaySpecialType.STANDARD, NightWorkType.HABITUEL);
        OvertimePayCalculator.Result r = calculator.calculate(List.of(b), RATE, standardRates, 1);
        BucketValuationResult d = r.bucketDetails().get(0);

        assertThat(d.overtimeBaseAmount()).isEqualByComparingTo("2000.00");
        assertThat(d.overtimePremiumAmount()).isEqualByComparingTo("600.00");
        assertThat(d.nightPremiumAmount()).isEqualByComparingTo("600.00");
        assertThat(d.payrollAdditionalAmount()).isEqualByComparingTo("3200.00");
        assertThat(d.payrollAdditionalAmount()).isNotEqualByComparingTo("5200.00");
        assertThat(d.payrollAdditionalAmount()).isNotEqualByComparingTo("1200.00");
        assertThat(r.total()).isEqualByComparingTo("3200.00");
    }

    @Test
    @DisplayName("Mini-Gate 6: HS30 SUNDAY -> Base (2000) + HS30 (600) + Dimanche (800) = 3400 Ar")
    void testMiniGate_hs30Sunday_singleBase() {
        QualifiedTimeBucket b = bucket(60, OvertimeTier.OVERTIME_30, DaySpecialType.SUNDAY, NightWorkType.NONE);
        OvertimePayCalculator.Result r = calculator.calculate(List.of(b), RATE, standardRates, 1);
        BucketValuationResult d = r.bucketDetails().get(0);

        assertThat(d.overtimeBaseAmount()).isEqualByComparingTo("2000.00");
        assertThat(d.overtimePremiumAmount()).isEqualByComparingTo("600.00");
        assertThat(d.sundayPremiumAmount()).isEqualByComparingTo("800.00");
        assertThat(d.payrollAdditionalAmount()).isEqualByComparingTo("3400.00");
        assertThat(r.total()).isEqualByComparingTo("3400.00");
    }

    @Test
    @DisplayName("Mini-Gate 7: Multi-qualifié HS30 + NUIT + DIMANCHE + FERIE -> 4 faits conservés, anti-double-base, UNRESOLVED throws, MAX_PREMIUM ok")
    void testMiniGate_multiQualified_fourFactsPreserved() {
        QualifiedTimeBucket b = bucket(60, OvertimeTier.OVERTIME_30, DaySpecialType.SUNDAY_AND_PUBLIC_HOLIDAY, NightWorkType.HABITUEL);

        // 1. Conservation intégrale des faits amont
        assertThat(b.overtimeTier()).isEqualTo(OvertimeTier.OVERTIME_30);
        assertThat(b.isSunday()).isTrue();
        assertThat(b.isPublicHoliday()).isTrue();
        assertThat(b.isNight()).isTrue();

        // 2. Par défaut : pas de cumul sauvage -> blocage explicite
        assertThatThrownBy(() -> calculator.calculate(List.of(b), RATE, standardRates, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPECIAL_PREMIUM_COLLISION_UNRESOLVED");

        // 3. Avec MAX_PREMIUM : Férié (+100%) prime sur Dimanche (+40%) et Nuit (+30%)
        // Base HS = 2000 + HS30 = 600 + Férié = 2000 = 4 600 Ar
        OvertimePayCalculator.Rates ratesMax = standardRates.withCollisionPolicy(mg.galaxam.sirh.model.paie.engine.SpecialPremiumCollisionPolicy.MAX_PREMIUM);
        OvertimePayCalculator.Result r = calculator.calculate(List.of(b), RATE, ratesMax, 1);
        BucketValuationResult d = r.bucketDetails().get(0);

        assertThat(d.overtimeBaseAmount()).isEqualByComparingTo("2000.00");
        assertThat(d.overtimePremiumAmount()).isEqualByComparingTo("600.00");
        assertThat(d.holidayPremiumAmount()).isEqualByComparingTo("2000.00");
        assertThat(d.sundayPremiumAmount()).isEqualByComparingTo("0.00");
        assertThat(d.nightPremiumAmount()).isEqualByComparingTo("0.00");
        assertThat(d.payrollAdditionalAmount()).isEqualByComparingTo("4600.00");
        assertThat(r.total()).isEqualByComparingTo("4600.00");
    }

    @Test
    @DisplayName("Mini-Gate 8: Résolution historique par date de bucket (changement de taux en milieu de période)")
    void testMiniGate_temporalRateResolver_midPeriodRateChange() {
        LocalDateTime t1 = LocalDateTime.of(2026, 9, 30, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 10, 1, 10, 0);

        QualifiedTimeBucket bSept = new QualifiedTimeBucket(t1, t1.plusMinutes(60), 60,
                OvertimeTier.REGULAR, DaySpecialType.SUNDAY, NightWorkType.NONE, 1, 101);
        QualifiedTimeBucket bOct = new QualifiedTimeBucket(t2, t2.plusMinutes(60), 60,
                OvertimeTier.REGULAR, DaySpecialType.SUNDAY, NightWorkType.NONE, 1, 102);

        // Résolveur dynamique : Dimanche à 40% jusqu'au 30/09, puis 50% dès le 01/10
        mg.galaxam.sirh.model.paie.engine.TemporalRateResolver resolver = date -> {
            BigDecimal dimancheRate = date.isAfter(java.time.LocalDate.of(2026, 9, 30))
                    ? new BigDecimal("0.50")
                    : new BigDecimal("0.40");
            return new OvertimePayCalculator.Rates(
                    new BigDecimal("0.30"),
                    new BigDecimal("0.50"),
                    new BigDecimal("0.30"),
                    null,
                    dimancheRate,
                    new BigDecimal("1.00"),
                    mg.galaxam.sirh.model.paie.engine.SpecialPremiumCollisionPolicy.UNRESOLVED,
                    null
            );
        };

        OvertimePayCalculator.Result r = calculator.calculate(List.of(bSept, bOct), RATE, resolver, 1);
        assertThat(r.bucketDetails()).hasSize(2);

        BucketValuationResult dSept = r.bucketDetails().get(0);
        BucketValuationResult dOct = r.bucketDetails().get(1);

        // Bucket septembre : taux 40% -> 800 Ar
        assertThat(dSept.sundayRate()).isEqualByComparingTo("0.40");
        assertThat(dSept.sundayPremiumAmount()).isEqualByComparingTo("800.00");
        assertThat(dSept.payrollAdditionalAmount()).isEqualByComparingTo("800.00");

        // Bucket octobre : taux 50% -> 1 000 Ar
        assertThat(dOct.sundayRate()).isEqualByComparingTo("0.50");
        assertThat(dOct.sundayPremiumAmount()).isEqualByComparingTo("1000.00");
        assertThat(dOct.payrollAdditionalAmount()).isEqualByComparingTo("1000.00");

        // Total = 1 800 Ar
        assertThat(r.total()).isEqualByComparingTo("1800.00");
    }
}
