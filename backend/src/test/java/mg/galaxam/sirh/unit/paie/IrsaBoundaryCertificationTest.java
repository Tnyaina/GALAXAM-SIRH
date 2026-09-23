package mg.galaxam.sirh.unit.paie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import mg.galaxam.sirh.paie.persistence.BulletinPaie;
import mg.galaxam.sirh.paie.persistence.PayrollCanonicalConcept;
import mg.galaxam.sirh.paie.domain.engine.IrsaCalculator;
import mg.galaxam.sirh.paie.domain.engine.MoneyRoundingPolicy;

class IrsaBoundaryCertificationTest {

    private static final List<IrsaCalculator.Tranche> BAREME_2026 = List.of(
            tranche("0", "350000", "0"),
            tranche("350000", "400000", "5"),
            tranche("400000", "500000", "10"),
            tranche("500000", "600000", "15"),
            tranche("600000", "4000000", "20"),
            tranche("4000000", null, "25")
    );

    static Stream<Arguments> frontieres() {
        return Stream.of(
                Arguments.of("349999", "349900", "3000.00"),
                Arguments.of("350000", "350000", "3000.00"),
                Arguments.of("350001", "350000", "3000.00"),
                Arguments.of("399999", "399900", "3000.00"),
                Arguments.of("400000", "400000", "3000.00"),
                Arguments.of("400001", "400000", "3000.00"),
                Arguments.of("500000", "500000", "12500.00"),
                Arguments.of("600000", "600000", "27500.00"),
                Arguments.of("1000000", "1000000", "107500.00"),
                Arguments.of("4000000", "4000000", "707500.00"),
                Arguments.of("5000000", "5000000", "957500.00")
        );
    }

    @ParameterizedTest
    @MethodSource("frontieres")
    void certifieToutesLesFrontieres2026(String revenu, String assiette, String irsaDu) {
        IrsaCalculator.Result result = calculer(revenu, 0);
        assertEquals(new BigDecimal(assiette).setScale(2), result.assietteIrsa());
        assertEquals(new BigDecimal(irsaDu), result.irsaDu());
    }

    @Test
    void certifieCentaineInferieure() {
        assertEquals(new BigDecimal("620000.00"), MoneyRoundingPolicy.irsaTaxBaseLowerHundred(new BigDecimal("620054.29")));
        assertEquals(new BigDecimal("620000.00"), MoneyRoundingPolicy.irsaTaxBaseLowerHundred(new BigDecimal("620000.00")));
        assertEquals(new BigDecimal("620000.00"), MoneyRoundingPolicy.irsaTaxBaseLowerHundred(new BigDecimal("620000.01")));
        assertEquals(new BigDecimal("620000.00"), MoneyRoundingPolicy.irsaTaxBaseLowerHundred(new BigDecimal("620099.99")));
        assertEquals(new BigDecimal("620100.00"), MoneyRoundingPolicy.irsaTaxBaseLowerHundred(new BigDecimal("620100.00")));
    }

    @Test
    void reductionEstAppliqueeAvantMinimumDePerception() {
        assertEquals(new BigDecimal("7500.00"), calculer("450000", 0).irsaDu());
        assertEquals(new BigDecimal("5500.00"), calculer("450000", 1).irsaDu());
        assertEquals(new BigDecimal("3500.00"), calculer("450000", 2).irsaDu());
        assertEquals(new BigDecimal("3000.00"), calculer("450000", 3).irsaDu());
        assertEquals(new BigDecimal("3000.00"), calculer("450000", 10).irsaDu());
    }

    @Test
    void distingueLesConceptsCanoniquesEtLeursAliasLegacy() {
        assertEquals(PayrollCanonicalConcept.NET_INCOME_BEFORE_INCOME_TAX,
                PayrollCanonicalConcept.fromLegacyExcelLabel("NETI"));
        assertEquals(PayrollCanonicalConcept.IRSA_TAX_BASE,
                PayrollCanonicalConcept.fromLegacyExcelLabel("Imposable"));
        assertNotEquals(PayrollCanonicalConcept.TAXABLE_NET_INCOME, PayrollCanonicalConcept.NET_PAYABLE);

        BulletinPaie bulletin = new BulletinPaie();
        bulletin.setTotalGainsBruts(new BigDecimal("632708.46"));
        bulletin.setTotalCotisationsSalariales(new BigDecimal("12654.17"));
        bulletin.setSalaireNetImposable(new BigDecimal("620054.29"));
        bulletin.setRevenuNetImposable(new BigDecimal("600000.00"));
        bulletin.setNetAPayer(new BigDecimal("567080.00"));
        assertEquals(new BigDecimal("620054.29"), bulletin.getRevenuNetAvantImpot());
        assertEquals(new BigDecimal("600000.00"), bulletin.getRevenuNetImposable());
        assertNotEquals(bulletin.getRevenuNetImposable(), bulletin.getNetAPayer());
    }

    @Test
    void arrondisMonetairesDemiCentimeRestentCentralises() {
        assertEquals(new BigDecimal("10.00"), MoneyRoundingPolicy.amount(new BigDecimal("10.004")));
        assertEquals(new BigDecimal("10.01"), MoneyRoundingPolicy.amount(new BigDecimal("10.005")));
        assertEquals(new BigDecimal("10.01"), MoneyRoundingPolicy.amount(new BigDecimal("10.006")));
    }

    private static IrsaCalculator.Result calculer(String revenu, int enfants) {
        return new IrsaCalculator().calculate(new BigDecimal(revenu), BAREME_2026, enfants,
                new BigDecimal("2000"), new BigDecimal("3000"));
    }

    private static IrsaCalculator.Tranche tranche(String min, String max, String taux) {
        return new IrsaCalculator.Tranche(new BigDecimal(min), max == null ? null : new BigDecimal(max), new BigDecimal(taux));
    }
}
