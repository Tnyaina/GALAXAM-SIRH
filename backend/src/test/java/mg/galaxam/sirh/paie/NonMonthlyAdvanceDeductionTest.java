package mg.galaxam.sirh.paie;

import mg.galaxam.sirh.paie.domain.dto.PayrollCalculationInput;
import mg.galaxam.sirh.paie.domain.engine.PayrollExecutionEngine;
import mg.galaxam.sirh.paie.persistence.BulletinPaie;
import mg.galaxam.sirh.paie.persistence.BulletinPaieLigne;
import mg.galaxam.sirh.paie.persistence.PaieAvanceAcompte;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NonMonthlyAdvanceDeductionTest {
    private final PayrollExecutionEngine engine = new PayrollExecutionEngine();

    @Test
    void hourlyAdvanceAndAcompteUseAvailableNetRatherThanHourlyRate() {
        var input = input("HORAIRE", "1000", avance("ACOMPTE_15", "10000"), avance("AVANCE_SPECIALE", "10000"));
        var lines = new ArrayList<BulletinPaieLigne>();
        var result = engine.calculerRetenuesEtAvances(new BigDecimal("7000"), input, new BulletinPaie(), lines);
        assertEquals(0, result.totalRetenues().compareTo(new BigDecimal("7000.00")));
        assertEquals(1, lines.size());
        assertEquals(0, lines.get(0).getMontantSalarial().compareTo(new BigDecimal("7000.00")));
    }

    @Test
    void dailyAdvanceIsCappedAfterAcompteEvenWhenDailyRateIsSmall() {
        var input = input("JOURNALIER", "20000", avance("ACOMPTE_15", "5000"), avance("AVANCE_SPECIALE", "10000"));
        var lines = new ArrayList<BulletinPaieLigne>();
        var result = engine.calculerRetenuesEtAvances(new BigDecimal("18000"), input, new BulletinPaie(), lines);
        assertEquals(0, result.totalRetenues().compareTo(new BigDecimal("6300.00")));
        assertEquals(2, lines.size());
        assertEquals(0, lines.get(1).getMontantSalarial().compareTo(new BigDecimal("1300.00")));
    }

    @Test
    void zeroNetDefersBothDeductions() {
        var input = input("HORAIRE", "1000", avance("ACOMPTE_15", "5000"), avance("AVANCE_SPECIALE", "10000"));
        var result = engine.calculerRetenuesEtAvances(BigDecimal.ZERO, input, new BulletinPaie(), new ArrayList<>());
        assertEquals(0, result.totalRetenues().compareTo(BigDecimal.ZERO));
    }

    private static PayrollCalculationInput input(String mode, String rate, PaieAvanceAcompte... movements) {
        var input = new PayrollCalculationInput();
        input.setModeCalculPaie(mode);
        input.setSalaireBaseContractuel(new BigDecimal(rate));
        input.setPlafondAvanceEmployeur(new BigDecimal("0.10"));
        input.setAvancesAcomptes(List.of(movements));
        return input;
    }

    private static PaieAvanceAcompte avance(String type, String amount) {
        var advance = new PaieAvanceAcompte();
        advance.setTypeMouvement(type);
        advance.setMontant(new BigDecimal(amount));
        return advance;
    }
}
