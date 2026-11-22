package mg.galaxam.sirh.conge.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CongeAcquisitionCalculTest {
    private final CongeCalculateurEngine calculateur = new CongeCalculateurEngine();

    @Test
    void entreeEnCoursDePeriodeEtAbsenceReduisentLeDroit() {
        BigDecimal joursService = calculateur.calculerJoursServiceEquivalent(20, 30, new BigDecimal("30"));
        BigDecimal joursEligibles = calculateur.calculerJoursEligibles(
                joursService, new BigDecimal("2"), new BigDecimal("30"));
        assertEquals(new BigDecimal("18.000000"), joursEligibles);
        assertEquals(new BigDecimal("1.5000"), calculateur.calculerAcquisitionMensuelleProratisee(
                joursEligibles, new BigDecimal("2.50"), new BigDecimal("30")));
    }

    @Test
    void vingtHuitJoursDeContratValentUnMoisEtLesAbsencesNeRendentPasLeDroitNegatif() {
        BigDecimal joursService = calculateur.calculerJoursServiceEquivalent(28, 31, new BigDecimal("30"));
        assertEquals(new BigDecimal("30"), joursService);
        assertEquals(BigDecimal.ZERO, calculateur.calculerJoursEligibles(
                joursService, new BigDecimal("35"), new BigDecimal("30")));
    }
}
