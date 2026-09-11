package mg.galaxam.sirh.unit.paie;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import mg.galaxam.sirh.model.paie.BulletinPaie;
import mg.galaxam.sirh.model.paie.PaieAvanceAcompte;
import mg.galaxam.sirh.model.paie.PaiePretEcheance;
import mg.galaxam.sirh.model.paie.dto.BulletinPaieComplet;
import mg.galaxam.sirh.model.paie.dto.PayrollCalculationInput;
import mg.galaxam.sirh.model.paie.engine.PayrollExecutionEngine;
import mg.galaxam.sirh.model.paieconfig.engine.CalculateurPaieEngine;
import mg.galaxam.sirh.model.paieconfig.engine.CalculateurPaieEngine.TrancheIrsa;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Unit Tests - Moteur d'Exécution Global de la Paie (PayrollExecutionEngine)")
class PayrollExecutionEngineTest {

    private PayrollExecutionEngine engine;
    private List<TrancheIrsa> tranchesLf2026;

    @BeforeEach
    void setUp() {
        engine = new PayrollExecutionEngine(new CalculateurPaieEngine());
        tranchesLf2026 = List.of(
                new TrancheIrsa(new BigDecimal("0.00"), new BigDecimal("350000.00"), 0.0),
                new TrancheIrsa(new BigDecimal("350001.00"), new BigDecimal("400000.00"), 5.0),
                new TrancheIrsa(new BigDecimal("400001.00"), new BigDecimal("500000.00"), 10.0),
                new TrancheIrsa(new BigDecimal("500001.00"), new BigDecimal("600000.00"), 15.0),
                new TrancheIrsa(new BigDecimal("600001.00"), null, 20.0)
        );
    }

    private PayrollCalculationInput createStandardInput(BigDecimal salaireBase, String regime) {
        PayrollCalculationInput in = new PayrollCalculationInput();
        in.setIdEmploye(1);
        in.setIdPeriodePaie(1);
        in.setCodePeriode("PER-2026-01");
        in.setDateDebutPeriode(Date.valueOf("2025-12-26"));
        in.setDateFinPeriode(Date.valueOf("2026-01-25"));
        in.setMatricule("EMP-002");
        in.setNomPrenom("RAKETAKA Soafara");
        in.setRegimeIntervenant(regime);
        in.setSalaireBaseContractuel(salaireBase);
        in.setCategorieProfessionnelle("AGENT");
        in.setModeCalculPaie("MENSUEL");
        in.setNbJoursOuvrables(new BigDecimal("22.0"));
        in.setNbJoursPresents(new BigDecimal("22.0"));
        in.setTotalHeuresTravaillees(new BigDecimal("173.33"));
        in.setHeuresNormales(new BigDecimal("173.33"));
        in.setModeCalculAbsence("HEURES_REELLES");

        // Paramètres légaux
        in.setSmeNational(new BigDecimal("262680.00"));
        in.setMinimumPerceptionIrsa(new BigDecimal("3000.00"));
        in.setDeductionParEnfant(new BigDecimal("2000.00"));
        in.setTranchesIrsa(tranchesLf2026);
        in.setTauxCnapsSalarial(0.01);
        in.setTauxCnapsPatronal(0.13);
        in.setTauxSanteSalarial(0.01);
        in.setTauxSantePatronal(0.05);

        // Désactiver les forfaits de repas et transport par défaut pour tester les salaires purs
        in.setForfaitRepasSite(BigDecimal.ZERO);
        in.setForfaitTransportSite(BigDecimal.ZERO);
        in.setIndemniteRepas(BigDecimal.ZERO);
        in.setIndemniteTransport(BigDecimal.ZERO);

        if ("PERMANENT".equalsIgnoreCase(regime)) {
            in.setSoumisCnaps(true);
            in.setSoumisSante(true);
            in.setSoumisIrsa(true);
            in.setSoumisIsi5(false);
        } else {
            in.setSoumisCnaps(false);
            in.setSoumisSante(false);
            in.setSoumisIrsa(false);
            in.setSoumisIsi5(true);
        }
        return in;
    }

    @Test
    @DisplayName("Calcul Salarié Permanent - Salaire Base 625 000 Ar, avec CNaPS, OSTIE et IRSA")
    void testCalculSalariePermanentStandard() {
        PayrollCalculationInput in = createStandardInput(new BigDecimal("625000.00"), "PERMANENT");

        BulletinPaieComplet complet = engine.calculer(in);
        BulletinPaie bp = complet.getBulletin();

        assertNotNull(bp);
        assertEquals(new BigDecimal("625000.00"), bp.getSalaireBase());
        assertEquals(new BigDecimal("625000.00"), bp.getTotalGainsBruts());

        // Cotisations CNaPS 1% = 6 250 Ar, OSTIE 1% = 6 250 Ar
        assertEquals(new BigDecimal("6250.00"), complet.getMontantLigneSalariale("COTISATION_CNAPS_SAL"));
        assertEquals(new BigDecimal("6250.00"), complet.getMontantLigneSalariale("COTISATION_OSTIE_SAL"));

        // Net imposable = 625 000 - 12 500 = 612 500 Ar -> arrondi centaine 612 500 Ar
        // IRSA = 2500 + 10000 + 15000 + (12500 * 20% = 2500) = 30 000 Ar
        assertEquals(new BigDecimal("612500.00"), bp.getSalaireNetImposable());
        assertEquals(new BigDecimal("30000.00"), bp.getIrsaNet());

        // Net à payer = 612 500 - 30 000 = 582 500 Ar
        assertEquals(new BigDecimal("582500.00"), bp.getNetAPayer());
    }

    @Test
    @DisplayName("Calcul Salarié avec Heures Supplémentaires (+30% et +50%) et Primes")
    void testCalculSalarieAvecHsEtPrimes() {
        PayrollCalculationInput in = createStandardInput(new BigDecimal("520000.00"), "PERMANENT");
        in.setHeuresHs30(new BigDecimal("10.0")); // 10h à 130%
        in.setPrimePerformanceBase(new BigDecimal("50000.00"));

        BulletinPaieComplet complet = engine.calculer(in);
        BulletinPaie bp = complet.getBulletin();

        assertTrue(bp.getTotalGainsBruts().compareTo(new BigDecimal("570000.00")) > 0);
        assertTrue(complet.getMontantLigneSalariale("PRIME_PERFORMANCE").compareTo(new BigDecimal("50000.00")) >= 0);
        assertTrue(bp.getNetAPayer().compareTo(BigDecimal.ZERO) > 0);
        // Le net est arrondi aux 10 Ariary près
        assertEquals(0, bp.getNetAPayer().remainder(BigDecimal.TEN).compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("Calcul Régime Prestataire / Consultant - Retenue 5% ISI, aucune cotisation CNaPS/OSTIE")
    void testCalculConsultantIsi5() {
        PayrollCalculationInput in = createStandardInput(new BigDecimal("1500000.00"), "CONSULTANT");

        BulletinPaieComplet complet = engine.calculer(in);
        BulletinPaie bp = complet.getBulletin();

        assertEquals(new BigDecimal("1500000.00"), bp.getTotalGainsBruts());
        // 0 cotisations sociales
        assertEquals(BigDecimal.ZERO, complet.getMontantLigneSalariale("COTISATION_CNAPS_SAL"));
        assertEquals(BigDecimal.ZERO, complet.getMontantLigneSalariale("COTISATION_OSTIE_SAL"));
        assertEquals(BigDecimal.ZERO.setScale(2), bp.getIrsaNet());

        // ISI 5% = 75 000 Ar
        BigDecimal isiAttendu = new BigDecimal("75000.00");
        assertEquals(isiAttendu, bp.getRetenueIsi5());

        // Net = 1 500 000 - 75 000 = 1 425 000 Ar
        assertEquals(new BigDecimal("1425000.00"), bp.getNetAPayer());
    }

    @Test
    @DisplayName("Plafond Légal de Quotité Cessible (Art. 72 - Max 1/3 du Net disponible pour prêts)")
    void testPlafondQuotiteCessiblePret() {
        PayrollCalculationInput in = createStandardInput(new BigDecimal("625000.00"), "PERMANENT");

        // Simulation d'une mensualité de prêt excessive (ex: 300 000 Ar alors que net avant prêt ~ 582 500 Ar -> 1/3 = ~194 166 Ar)
        PaiePretEcheance ech = new PaiePretEcheance();
        ech.setIdEcheance(10);
        ech.setIdPret(1);
        ech.setNumeroEcheance(1);
        ech.setMontantEcheance(new BigDecimal("300000.00"));
        ech.setCapitalRestantApres(new BigDecimal("1000000.00"));
        ech.setMontantRestantEcheance(new BigDecimal("300000.00"));
        ech.setStatut("A_ECHOIR");
        in.setEcheancePretActive(ech);

        BulletinPaieComplet complet = engine.calculer(in);
        BulletinPaie bp = complet.getBulletin();

        // Le prélèvement du prêt doit être borné par la quotité cessible (1/3)
        assertTrue(bp.getTotalRetenuesAcomptePret().compareTo(new BigDecimal("300000.00")) < 0);
        assertTrue(bp.getTotalRetenuesAcomptePret().compareTo(new BigDecimal("190000.00")) > 0);
        // Le net final reste supérieur aux 2/3 légaux insaisissables
        assertTrue(bp.getNetAPayer().compareTo(new BigDecimal("380000.00")) > 0);
    }
}
