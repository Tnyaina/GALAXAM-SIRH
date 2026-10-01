package mg.galaxam.sirh.paie.domain.engine;

import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import mg.galaxam.sirh.paie.domain.dto.PayrollCalculationInput.GainVariable;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ManualGainContributionsTest {
    @ParameterizedTest
    @CsvSource({"BRUT,true,true,100.125,100.125,100.125,0",
                "BRUT,false,true,100.125,100.125,0,0",
                "BRUT,true,false,100.125,0,100.125,0",
                "BRUT,false,false,100.125,0,0,0",
                "GAIN_NET,false,false,0,0,0,100.125"})
    void contributionsConserventLaPrecisionEtNeDoublentPasLeNet(String phase, boolean fiscal,
            boolean social, String brut, String baseSociale, String irsa, String net) {
        GainVariable gain = new GainVariable();
        gain.setEtapeCalcul(phase); gain.setImposable(fiscal);
        gain.setSoumisCotisation(social); gain.setMontant(new BigDecimal("100.125"));
        var result = ManualGainContributions.resolve(gain);
        equal(brut, result.brut()); equal(baseSociale, result.social());
        equal(irsa, result.irsa()); equal(net, result.ajoutNet());
        assertEquals(0, result.brut().add(result.correctionSociale()).compareTo(result.social()));
        assertEquals(0, result.brut().subtract(result.exclusionIrsa()).compareTo(result.irsa()));
    }
    private static void equal(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
