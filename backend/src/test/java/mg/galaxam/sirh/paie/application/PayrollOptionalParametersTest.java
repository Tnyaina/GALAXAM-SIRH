package mg.galaxam.sirh.paie.application;

import mg.galaxam.sirh.paie.domain.dto.PayrollCalculationInput;
import mg.galaxam.sirh.paie.domain.engine.PayrollExecutionEngine;
import mg.galaxam.sirh.paie.persistence.BulletinPaie;
import mg.galaxam.sirh.paie.persistence.BulletinPaieLigne;
import mg.galaxam.sirh.paieconfig.persistence.ParametreGlobal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PayrollOptionalParametersTest {
    private static Map<String, ParametreGlobal.ParametreEtat> parameters() {
        Map<String, ParametreGlobal.ParametreEtat> result = new HashMap<>();
        for (var entry : Map.of(
                "SEUIL_RETARD_ASSIDUITE_MIN", "45",
                "MONTANT_PRIME_ASSIDUITE", "500",
                "SEUIL_RETARD_PENALITE_PP_MIN", "70",
                "SEUIL_CONGES_PENALITE_PP_JOURS", "1.0",
                "TAUX_PENALITE_PP", "0.25",
                "EXONERATION_HS_IRSA_ACTIVE", "TRUE",
                "PLAFOND_MENSUEL_HS_EXONEREES", "20.00").entrySet()) {
            result.put(entry.getKey(), new ParametreGlobal.ParametreEtat(entry.getKey(), entry.getValue(), true, true));
        }
        return result;
    }

    private static void disable(Map<String, ParametreGlobal.ParametreEtat> params, String code) {
        params.put(code, new ParametreGlobal.ParametreEtat(code, null, false, true));
    }

    @Test
    void disablingAttendanceThresholdRemovesPrimeFromBulletin() {
        var params = parameters();
        var input = primeInput();
        PayrollLegalContextResolver.applyDynamicParameters(input, params);
        assertEquals(0, new BigDecimal("1500").compareTo(calculatePrimes(input)));

        disable(params, "SEUIL_RETARD_ASSIDUITE_MIN");
        input = primeInput();
        PayrollLegalContextResolver.applyDynamicParameters(input, params);
        assertFalse(input.isReglePrimeAssiduiteActive());
        assertEquals(0, new BigDecimal("1000").compareTo(calculatePrimes(input)));
    }

    @Test
    void disablingMontantAssiduiteRemovesPrimeFromBulletin() {
        var params = parameters();
        var input = primeInput();
        PayrollLegalContextResolver.applyDynamicParameters(input, params);
        assertEquals(0, new BigDecimal("1500").compareTo(calculatePrimes(input)));

        disable(params, "MONTANT_PRIME_ASSIDUITE");
        input = primeInput();
        PayrollLegalContextResolver.applyDynamicParameters(input, params);
        assertEquals(0, new BigDecimal("1000").compareTo(calculatePrimes(input)));
    }

    @Test
    void disablingAnyMalusParameterRemovesPerformancePenalty() {
        var activeInput = primeInput();
        activeInput.setMinutesRetard(80);
        PayrollLegalContextResolver.applyDynamicParameters(activeInput, parameters());
        assertEquals(0, new BigDecimal("750").compareTo(calculatePrimes(activeInput)));
        for (String code : List.of("SEUIL_RETARD_PENALITE_PP_MIN", "SEUIL_CONGES_PENALITE_PP_JOURS", "TAUX_PENALITE_PP")) {
            var params = parameters();
            disable(params, code);
            var input = primeInput();
            input.setMinutesRetard(80);
            PayrollLegalContextResolver.applyDynamicParameters(input, params);
            assertFalse(input.isRegleMalusPerformanceActive());
            assertEquals(0, new BigDecimal("1000").compareTo(calculatePrimes(input)), code);
        }
    }

    @Test
    void disablingEitherExemptionParameterCancelsLegalExemption() {
        for (String code : List.of("EXONERATION_HS_IRSA_ACTIVE", "PLAFOND_MENSUEL_HS_EXONEREES")) {
            var params = parameters();
            disable(params, code);
            var input = primeInput();
            input.setExonerationHsIrsaActive(true); // configuration légale déjà chargée
            input.setPlafondMensuelHsExonerees(new BigDecimal("20"));
            PayrollLegalContextResolver.applyDynamicParameters(input, params);
            assertFalse(input.isAppliquerExonerationHs(), code);
            assertEquals(0, input.getPlafondMensuelHsExonerees().compareTo(BigDecimal.ZERO), code);
        }
    }

    @Test
    void missingOrInvalidActiveParameterDoesNotBecomeDisabled() {
        var missingThreshold = parameters();
        missingThreshold.remove("SEUIL_RETARD_ASSIDUITE_MIN");
        assertThrows(IllegalStateException.class,
                () -> PayrollLegalContextResolver.applyDynamicParameters(primeInput(), missingThreshold));
        var missingAmount = parameters();
        missingAmount.remove("MONTANT_PRIME_ASSIDUITE");
        assertThrows(IllegalStateException.class,
                () -> PayrollLegalContextResolver.applyDynamicParameters(primeInput(), missingAmount));
        var invalidThreshold = parameters();
        invalidThreshold.put("SEUIL_RETARD_ASSIDUITE_MIN", new ParametreGlobal.ParametreEtat(
                "SEUIL_RETARD_ASSIDUITE_MIN", "oops", true, true));
        assertThrows(IllegalStateException.class,
                () -> PayrollLegalContextResolver.applyDynamicParameters(primeInput(), invalidThreshold));
    }

    private static PayrollCalculationInput primeInput() {
        var input = new PayrollCalculationInput();
        input.setPrimeAssiduiteForfait(new BigDecimal("500"));
        input.setPrimePerformanceBase(new BigDecimal("1000"));
        input.setMinutesRetard(30);
        input.setNbJoursConges(new BigDecimal("2"));
        return input;
    }

    private static BigDecimal calculatePrimes(PayrollCalculationInput input) {
        List<BulletinPaieLigne> lines = new ArrayList<>();
        return new PayrollExecutionEngine().calculerPrimes(input, new BigDecimal("100000"), new BulletinPaie(), lines);
    }
}
