package mg.galaxam.sirh.paie.domain.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyRoundingPolicyTest {

    @Test
    void arronditLariaryEntierAuPlusProche() {
        assertEquals(new BigDecimal("12"), MoneyRoundingPolicy.roundToNearestAriary(new BigDecimal("12.49")));
        assertEquals(new BigDecimal("13"), MoneyRoundingPolicy.roundToNearestAriary(new BigDecimal("12.50")));
        assertEquals(new BigDecimal("-13"), MoneyRoundingPolicy.roundToNearestAriary(new BigDecimal("-12.50")));
        assertEquals(BigDecimal.ZERO, MoneyRoundingPolicy.roundToNearestAriary(null));
    }

    @Test
    void distingueEntierInferieurEtSuperieurYComprisPourUnMontantNegatif() {
        assertEquals(new BigDecimal("12"), MoneyRoundingPolicy.roundDownToAriary(new BigDecimal("12.01")));
        assertEquals(new BigDecimal("13"), MoneyRoundingPolicy.roundUpToAriary(new BigDecimal("12.01")));
        assertEquals(new BigDecimal("-13"), MoneyRoundingPolicy.roundDownToAriary(new BigDecimal("-12.01")));
        assertEquals(new BigDecimal("-12"), MoneyRoundingPolicy.roundUpToAriary(new BigDecimal("-12.01")));
        assertEquals(new BigDecimal("12"), MoneyRoundingPolicy.roundDownToAriary(new BigDecimal("12.00")));
        assertEquals(new BigDecimal("12"), MoneyRoundingPolicy.roundUpToAriary(new BigDecimal("12.00")));
    }

    @Test
    void arronditALaCentaineEtAuMillierSansChangerLesMontantsExactementMultiples() {
        assertEquals(new BigDecimal("12300"), MoneyRoundingPolicy.roundDownToHundredAriary(new BigDecimal("12345.67")));
        assertEquals(new BigDecimal("12400"), MoneyRoundingPolicy.roundUpToHundredAriary(new BigDecimal("12345.67")));
        assertEquals(new BigDecimal("12000"), MoneyRoundingPolicy.roundDownToThousandAriary(new BigDecimal("12345.67")));
        assertEquals(new BigDecimal("13000"), MoneyRoundingPolicy.roundUpToThousandAriary(new BigDecimal("12345.67")));
        assertEquals(new BigDecimal("12000"), MoneyRoundingPolicy.roundDownToThousandAriary(new BigDecimal("12000")));
        assertEquals(new BigDecimal("12000"), MoneyRoundingPolicy.roundUpToThousandAriary(new BigDecimal("12000")));
        assertEquals(new BigDecimal("-12400"), MoneyRoundingPolicy.roundDownToHundredAriary(new BigDecimal("-12345.67")));
        assertEquals(new BigDecimal("-12300"), MoneyRoundingPolicy.roundUpToHundredAriary(new BigDecimal("-12345.67")));
        assertEquals(BigDecimal.ZERO, MoneyRoundingPolicy.roundUpToThousandAriary(null));
    }

    @Test
    void rubriqueManuelleAriaryConserveDeuxDecimalesPourLeStockage() {
        var result = ManualPayrollRules.calculateAmount("MONTANT", "ARIARY",
                new BigDecimal("12.50"), null, null, null);
        assertEquals(new BigDecimal("13.00"), result.montant());
    }
}
