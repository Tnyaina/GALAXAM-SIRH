package mg.galaxam.sirh.paie.domain.engine;

import mg.galaxam.sirh.paie.domain.dto.PayrollCalculationInput;
import mg.galaxam.sirh.paie.persistence.BulletinPaie;
import mg.galaxam.sirh.paie.persistence.BulletinPaieLigne;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PayrollAllowanceSocialBaseTest {
    private static PayrollCalculationInput input(boolean taxable, boolean social) {
        PayrollCalculationInput input = new PayrollCalculationInput();
        input.setSmeNational(new BigDecimal("100000"));
        input.setPlafondCotisationMensuel(new BigDecimal("800000"));
        input.setTauxCnapsSalarial(new BigDecimal("0.01"));
        input.setTauxCnapsPatronal(new BigDecimal("0.13"));
        input.setTauxSanteSalarial(BigDecimal.ZERO);
        input.setTauxSantePatronal(BigDecimal.ZERO);
        input.setTauxFmfpPatronal(BigDecimal.ZERO);
        input.ajouterGainVariable(new PayrollCalculationInput.GainVariable(
                null, "INDEMNITE_TEST", "Indemnité de test", new BigDecimal("10000"),
                "INDEMNITE", taxable, social));
        return input;
    }

    @Test
    void indemnityTaxabilityDoesNotAutomaticallyTriggerSocialContributions() {
        PayrollExecutionEngine engine = new PayrollExecutionEngine();
        var lines = new ArrayList<BulletinPaieLigne>();
        engine.calculerCotisationsSociales(new BigDecimal("110000"), input(true, false), new BulletinPaie(), lines);
        assertEquals(0, lines.stream().filter(line -> "COTISATION_CNAPS_SAL".equals(line.getCodeRubrique()))
                .findFirst().orElseThrow().getBaseCalcul().compareTo(new BigDecimal("100000")));
    }

    @Test
    void nonTaxableIndemnityMayBeIncludedInSocialBaseWhenConfigured() {
        PayrollExecutionEngine engine = new PayrollExecutionEngine();
        var lines = new ArrayList<BulletinPaieLigne>();
        engine.calculerCotisationsSociales(new BigDecimal("100000"), input(false, true), new BulletinPaie(), lines);
        assertEquals(0, lines.stream().filter(line -> "COTISATION_CNAPS_SAL".equals(line.getCodeRubrique()))
                .findFirst().orElseThrow().getBaseCalcul().compareTo(new BigDecimal("110000")));
    }
}
