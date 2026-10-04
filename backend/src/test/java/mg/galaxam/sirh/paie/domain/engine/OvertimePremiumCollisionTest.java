package mg.galaxam.sirh.paie.domain.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.stream.Stream;
import mg.galaxam.sirh.pointage.domain.calculation.QualifiedTimeBucket.DaySpecialType;
import mg.galaxam.sirh.pointage.domain.calculation.QualifiedTimeBucket.NightWorkType;
import mg.galaxam.sirh.pointage.domain.calculation.QualifiedTimeBucket.OvertimeTier;
import mg.galaxam.sirh.pointage.domain.calculation.QualifiedTimeBucket;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

class OvertimePremiumCollisionTest {
    private final OvertimePayCalculator calculator = new OvertimePayCalculator();

    @Test
    void ancienBucketNonClasseDeTrenteMinutesUtiliseLeTauxNuitUnique() {
        LocalDateTime start = LocalDate.of(2026, 1, 25).atTime(22, 0);
        var bucket = new QualifiedTimeBucket(start, start.plusMinutes(30), 30,
                OvertimeTier.REGULAR, DaySpecialType.SUNDAY,
                NightWorkType.NIGHT_UNCLASSIFIED, 1, 1);
        var rates = new OvertimePayCalculator.Rates(
                new BigDecimal("0.30"), new BigDecimal("0.50"), new BigDecimal("0.25"),
                new BigDecimal("0.40"), new BigDecimal("0.50"),
                SpecialPremiumCollisionPolicy.CUMUL_ADDITIF);

        var result = calculator.calculate(java.util.List.of(bucket), new BigDecimal("10000"), rates, 1);
        assertEquals(0, new BigDecimal("1250.00").compareTo(result.nuit()));
        assertEquals(0, new BigDecimal("2000.00").compareTo(result.dimanche()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("collisions")
    void lesMajorationsCumuleesSontStrictementAdditives(String label, OvertimeTier tier,
            DaySpecialType dayType, NightWorkType nightType, String expectedHourlyAmount) {
        LocalDateTime start = LocalDate.of(2026, 9, 27).atTime(21, 0);
        var bucket = new QualifiedTimeBucket(start, start.plusHours(1), 60, tier,
                dayType, nightType, 1, 1);
        var rates = new OvertimePayCalculator.Rates(
                new BigDecimal("0.30"), new BigDecimal("0.50"), new BigDecimal("0.25"),
                new BigDecimal("0.40"), new BigDecimal("0.50"),
                SpecialPremiumCollisionPolicy.CUMUL_ADDITIF);

        var result = calculator.calculate(java.util.List.of(bucket), new BigDecimal("10000"), rates, 1);
        BigDecimal total = result.hs30().add(result.hs50()).add(result.nuit())
                .add(result.dimanche()).add(result.ferie());
        if (tier == OvertimeTier.REGULAR) total = total.add(new BigDecimal("10000"));

        assertEquals(0, new BigDecimal(expectedHourlyAmount).compareTo(total));
    }

    private static Stream<Arguments> collisions() {
        return Stream.of(
                Arguments.of("HS 30 + dimanche : 10 000 × 1,70", OvertimeTier.OVERTIME_30,
                        DaySpecialType.SUNDAY, NightWorkType.NONE, "17000.00"),
                Arguments.of("HS 50 + dimanche : 10 000 × 1,90", OvertimeTier.OVERTIME_50,
                        DaySpecialType.SUNDAY, NightWorkType.NONE, "19000.00"),
                Arguments.of("HS 30 + nuit : 10 000 × 1,55", OvertimeTier.OVERTIME_30,
                        DaySpecialType.STANDARD, NightWorkType.OCCASIONNEL, "15500.00"),
                Arguments.of("HS 30 + férié : 10 000 × 1,80", OvertimeTier.OVERTIME_30,
                        DaySpecialType.PUBLIC_HOLIDAY, NightWorkType.NONE, "18000.00"),
                Arguments.of("nuit + dimanche : 10 000 × 1,65", OvertimeTier.REGULAR,
                        DaySpecialType.SUNDAY, NightWorkType.OCCASIONNEL, "16500.00"),
                Arguments.of("nuit + férié : 10 000 × 1,75", OvertimeTier.REGULAR,
                        DaySpecialType.PUBLIC_HOLIDAY, NightWorkType.OCCASIONNEL, "17500.00"),
                Arguments.of("dimanche + férié : 10 000 × 1,90", OvertimeTier.REGULAR,
                        DaySpecialType.SUNDAY_AND_PUBLIC_HOLIDAY, NightWorkType.NONE, "19000.00"),
                Arguments.of("HS 30 + nuit + dimanche + férié : 10 000 × 2,45", OvertimeTier.OVERTIME_30,
                        DaySpecialType.SUNDAY_AND_PUBLIC_HOLIDAY, NightWorkType.OCCASIONNEL, "24500.00"),
                Arguments.of("ancien snapshot non classé : même taux NUIT", OvertimeTier.REGULAR,
                        DaySpecialType.STANDARD, NightWorkType.NIGHT_UNCLASSIFIED, "12500.00"),
                Arguments.of("ancien snapshot habituel : même taux NUIT", OvertimeTier.REGULAR,
                        DaySpecialType.STANDARD, NightWorkType.HABITUEL, "12500.00"));
    }
}
