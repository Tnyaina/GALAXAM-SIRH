package mg.galaxam.sirh.paie.domain.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import mg.galaxam.sirh.pointage.domain.calculation.QualifiedTimeBucket;
import mg.galaxam.sirh.pointage.domain.calculation.QualifiedTimeBucket.DaySpecialType;
import mg.galaxam.sirh.pointage.domain.calculation.QualifiedTimeBucket.NightWorkType;
import mg.galaxam.sirh.pointage.domain.calculation.QualifiedTimeBucket.OvertimeTier;
import org.junit.jupiter.api.Test;

class OvertimePayCalculatorTest {
    private static final BigDecimal HOURLY_RATE = new BigDecimal("1000");
    private static final OvertimePayCalculator CALCULATOR = new OvertimePayCalculator();
    private static final OvertimePayCalculator.Rates RATES = new OvertimePayCalculator.Rates(
            new BigDecimal("0.30"), new BigDecimal("0.50"), new BigDecimal("0.25"),
            new BigDecimal("0.40"), new BigDecimal("1.00"), SpecialPremiumCollisionPolicy.CUMUL_ADDITIF);

    @Test
    void lesHeuresNormalesNeRajoutentPasUneSecondeBaseAuSalaireMensuel() {
        var result = calculate(bucket(60, OvertimeTier.REGULAR, DaySpecialType.STANDARD, NightWorkType.NONE), RATES);

        assertMoney("0.00", result.total());
        assertMoney("0.00", result.bucketDetails().get(0).payrollAdditionalAmount());
    }

    @Test
    void hs30EtHs50IncluentLaBaseEtLeurMajorationUneSeuleFois() {
        var result = calculate(
                bucket(60, OvertimeTier.OVERTIME_30, DaySpecialType.STANDARD, NightWorkType.NONE),
                bucket(30, OvertimeTier.OVERTIME_50, DaySpecialType.STANDARD, NightWorkType.NONE));

        assertMoney("1300.00", result.hs30());
        assertMoney("750.00", result.hs50());
        assertMoney("2050.00", result.total());
    }

    @Test
    void lesSujétionsSimplesSontDesIncrementsEtPeuventSeCombinerAvecUneHs() {
        var result = calculate(
                bucket(60, OvertimeTier.REGULAR, DaySpecialType.STANDARD, NightWorkType.OCCASIONNEL),
                bucket(60, OvertimeTier.REGULAR, DaySpecialType.SUNDAY, NightWorkType.NONE),
                bucket(60, OvertimeTier.REGULAR, DaySpecialType.PUBLIC_HOLIDAY, NightWorkType.NONE),
                bucket(60, OvertimeTier.OVERTIME_30, DaySpecialType.STANDARD, NightWorkType.OCCASIONNEL));

        assertMoney("500.00", result.nuit());
        assertMoney("400.00", result.dimanche());
        assertMoney("1000.00", result.ferie());
        assertMoney("1300.00", result.hs30());
        // L'heure de nuit en HS30 ajoute base 1000 + HS 300 + nuit 250.
        assertMoney("3200.00", result.total());
    }

    @Test
    void collisionSansPolitiqueEstRefuseeEtLaPolitiqueExpliciteEstAppliquee() {
        var collision = bucket(60, OvertimeTier.REGULAR, DaySpecialType.SUNDAY_AND_PUBLIC_HOLIDAY,
                NightWorkType.OCCASIONNEL);

        assertThrows(IllegalStateException.class,
                () -> calculate(collision, new OvertimePayCalculator.Rates(
                        RATES.rateHs30(), RATES.rateHs50(), RATES.rateNuit(), RATES.rateDimanche(), RATES.rateFerie())));

        var max = calculate(collision, new OvertimePayCalculator.Rates(
                RATES.rateHs30(), RATES.rateHs50(), RATES.rateNuit(), RATES.rateDimanche(), RATES.rateFerie(),
                SpecialPremiumCollisionPolicy.MAX_PREMIUM));
        assertMoney("1000.00", max.ferie());
        assertMoney("0.00", max.dimanche());
        assertMoney("0.00", max.nuit());

        var holidayOnly = calculate(collision, new OvertimePayCalculator.Rates(
                RATES.rateHs30(), RATES.rateHs50(), RATES.rateNuit(), RATES.rateDimanche(), RATES.rateFerie(),
                SpecialPremiumCollisionPolicy.HOLIDAY_ONLY));
        assertMoney("1000.00", holidayOnly.ferie());
        assertMoney("0.00", holidayOnly.dimanche());
        assertMoney("0.00", holidayOnly.nuit());

        var cumulative = calculate(collision, RATES);
        assertMoney("250.00", cumulative.nuit());
        assertMoney("400.00", cumulative.dimanche());
        assertMoney("1000.00", cumulative.ferie());
    }

    @Test
    void lesTauxSontResolusSurLaDateDeChaqueBucket() {
        var first = bucketAt(LocalDate.of(2026, 8, 31), 60, OvertimeTier.OVERTIME_30,
                DaySpecialType.STANDARD, NightWorkType.NONE);
        var next = bucketAt(LocalDate.of(2026, 9, 1), 60, OvertimeTier.OVERTIME_30,
                DaySpecialType.STANDARD, NightWorkType.NONE);

        var result = CALCULATOR.calculate(List.of(first, next), HOURLY_RATE,
                date -> date.getMonthValue() == 8 ? RATES : new OvertimePayCalculator.Rates(
                        new BigDecimal("0.50"), RATES.rateHs50(), RATES.rateNuit(), RATES.rateDimanche(),
                        RATES.rateFerie(), SpecialPremiumCollisionPolicy.CUMUL_ADDITIF), 77);

        assertMoney("2800.00", result.hs30());
        assertMoney("2800.00", result.total());
    }

    private static OvertimePayCalculator.Result calculate(QualifiedTimeBucket... buckets) {
        return calculate(buckets, RATES);
    }

    private static OvertimePayCalculator.Result calculate(QualifiedTimeBucket bucket,
                                                           OvertimePayCalculator.Rates rates) {
        return calculate(new QualifiedTimeBucket[] {bucket}, rates);
    }

    private static OvertimePayCalculator.Result calculate(QualifiedTimeBucket[] buckets,
                                                           OvertimePayCalculator.Rates rates) {
        return CALCULATOR.calculate(List.of(buckets), HOURLY_RATE, ignored -> rates, 77);
    }

    private static QualifiedTimeBucket bucket(int minutes, OvertimeTier overtime,
                                               DaySpecialType day, NightWorkType night) {
        return bucketAt(LocalDate.of(2026, 8, 3), minutes, overtime, day, night);
    }

    private static QualifiedTimeBucket bucketAt(LocalDate date, int minutes, OvertimeTier overtime,
                                                  DaySpecialType day, NightWorkType night) {
        LocalDateTime start = date.atTime(9, 0);
        return new QualifiedTimeBucket(start, start.plusMinutes(minutes), minutes, overtime, day, night, 1, 1);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "Montant inattendu");
    }
}
