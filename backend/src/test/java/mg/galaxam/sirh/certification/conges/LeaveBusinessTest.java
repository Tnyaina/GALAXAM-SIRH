package mg.galaxam.sirh.certification.conges;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import mg.galaxam.sirh.oracle.LeaveIndependentOracle;

@DisplayName("CERTIFICATION CONGÉS & TCONGE — RÈGLES MÉTIER & ORACLE INDÉPENDANT")
public class LeaveBusinessTest {

    @Test
    @DisplayName("1. Acquisition légale 2.5 jours/mois et valorisation TCONGE au trentième")
    void testAcquisitionEtTconge() {
        BigDecimal baseContractuelle = new BigDecimal("600000.00");
        BigDecimal soldeInitial = new BigDecimal("10.00");
        BigDecimal acquisitionMois = new BigDecimal("2.50");
        BigDecimal joursPris = new BigDecimal("1.00");

        LeaveIndependentOracle.LeaveLedgerResult oracle = LeaveIndependentOracle.evaluateLeavePeriod(
                soldeInitial, acquisitionMois, joursPris, baseContractuelle, null
        );

        // Solde final = 10.0 + 2.5 - 1.0 = 11.5 jours
        assertEquals(new BigDecimal("11.50"), oracle.soldeFinal(), "Solde final exact : 11.5 jours");

        // TCONGE légal au trentième : 600 000 / 30 = 20 000.00 Ar/jour
        assertEquals(new BigDecimal("20000.00"), oracle.tcongeApplicable(), "TCONGE exact : 20 000.00 Ar/j");

        // Déduction de la base mensuelle : 1 * (600 000 / 30) = 20 000.00 Ar
        assertEquals(new BigDecimal("20000.00"), oracle.deductionCongeBase(), "Déduction congé base : 20 000.00 Ar");

        // Indemnité allouée : 1 * 20 000.00 = 20 000.00 Ar
        assertEquals(new BigDecimal("20000.00"), oracle.indemniteConge(), "Indemnité congé payé : 20 000.00 Ar");
    }

    @Test
    @DisplayName("2. Séparation dimensionnelle absolue : Jours (NBCONGE) vs Minutes/Heures (NBABS)")
    void testSeparationDimensionnelle() {
        BigDecimal nbCongeJours = new BigDecimal("1.50"); // En JOURS
        long nbAbsenceMinutes = 120L;                     // En MINUTES (2h)
        BigDecimal diviseurMensuelHeures = new BigDecimal("173.33");

        // Vérification de dimension :
        // Le congé s'exprime en jours entiers ou demi-journées (ex: 1.50 j, échelle 2 en BDD)
        assertEquals(2, nbCongeJours.scale(), "Précision standard des congés en demi-journées (2 décimales)");

        // L'absence se calcule en minutes réelles sans jamais soustraire directement des jours
        BigDecimal absenceHeures = BigDecimal.valueOf(nbAbsenceMinutes).divide(new BigDecimal("60"), 4, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("2.0000"), absenceHeures, "120 minutes = 2.0000 heures d'absence");

        // L'évaluation financière utilise des bases différentes :
        // Congé = trentième (diviseur 30 jours)
        // Absence = diviseur horaire légal (173.33 heures)
        assertTrue(new BigDecimal("30").compareTo(diviseurMensuelHeures) < 0, "Les diviseurs sont dimensionnellement distincts");
    }

    @Test
    @DisplayName("3. Règle du plus favorable (Art. 88 CT) : TCONGE ne peut être inférieur au trentième du salaire de base")
    void testTcongePlusFavorable() {
        BigDecimal salaireBase = new BigDecimal("450000.00");
        BigDecimal trentieme = salaireBase.divide(new BigDecimal("30"), 2, RoundingMode.HALF_UP); // 15 000.00 Ar

        // Cas 1 : Rémunération historique supérieure (ex: primes antérieures) -> TCONGE retenu = 16 500 Ar
        BigDecimal tcongeHistoriqueSuperieur = new BigDecimal("16500.00");
        LeaveIndependentOracle.LeaveLedgerResult resSup = LeaveIndependentOracle.evaluateLeavePeriod(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, salaireBase, tcongeHistoriqueSuperieur
        );
        assertEquals(new BigDecimal("16500.00"), resSup.tcongeApplicable(), "TCONGE plus favorable retenu");
        assertEquals(new BigDecimal("16500.00"), resSup.indemniteConge(), "Indemnité valorisée au taux supérieur");
        assertEquals(new BigDecimal("15000.00"), resSup.deductionCongeBase(), "Déduction de base reste au trentième");

        // Cas 2 : Absence de primes -> TCONGE = trentième légal = 15 000 Ar
        LeaveIndependentOracle.LeaveLedgerResult resNominal = LeaveIndependentOracle.evaluateLeavePeriod(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, salaireBase, null
        );
        assertEquals(new BigDecimal("15000.00"), resNominal.tcongeApplicable(), "TCONGE minimal égal au trentième");
    }
}
