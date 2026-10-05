package mg.galaxam.sirh.paie.domain.engine;

import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;
import mg.galaxam.sirh.paie.domain.dto.PayrollCalculationInput.GainVariable;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ManualGainContributionsTest {
    static Stream<Arguments> combinaisonsAssiettesBrut() {
        return IntStream.range(0, 16).mapToObj(flags -> Arguments.of(
                (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0, (flags & 8) != 0));
    }

    @ParameterizedTest
    @MethodSource("combinaisonsAssiettesBrut")
    void toutesLesAssiettesManuellesDuBrutRestentIndependantes(
            boolean irsa, boolean cnaps, boolean sante, boolean fmfp) {
        GainVariable gain = new GainVariable();
        gain.setMontant(new BigDecimal("10000"));
        gain.setEtapeCalcul("BRUT");
        gain.setImposable(irsa);
        gain.setSoumisCotisation(cnaps);
        gain.setSoumisSante(sante);
        gain.setSoumisFmfp(fmfp);

        var result = ManualPayrollRules.Contributions.resolve(gain);
        equal("10000", result.brut());
        equal(irsa ? "10000" : "0", result.irsa());
        equal(cnaps ? "10000" : "0", result.social());
        equal(sante ? "10000" : "0", result.sante());
        equal(fmfp ? "10000" : "0", result.fmfp());
        equal("0", result.ajoutNet());
    }

    @ParameterizedTest
    @CsvSource({"BRUT,10000,0", "GAIN_NET,0,10000"})
    void etapeDeGainDeterminesBrutEtAjoutNetSansDoubleCompte(String etape, String brut, String ajoutNet) {
        GainVariable gain = new GainVariable();
        gain.setMontant(new BigDecimal("10000"));
        gain.setEtapeCalcul(etape);
        var result = ManualPayrollRules.Contributions.resolve(gain);
        equal(brut, result.brut());
        equal(ajoutNet, result.ajoutNet());
    }

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
        var result = ManualPayrollRules.Contributions.resolve(gain);
        equal(brut, result.brut()); equal(baseSociale, result.social());
        equal(irsa, result.irsa()); equal(net, result.ajoutNet());
        assertEquals(0, result.brut().add(result.correctionSociale()).compareTo(result.social()));
        assertEquals(0, result.brut().subtract(result.exclusionIrsa()).compareTo(result.irsa()));
    }
    private static void equal(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
