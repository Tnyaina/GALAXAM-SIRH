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
import mg.galaxam.sirh.model.paie.engine.IrsaCalculator.Tranche;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Unit Tests - Moteur d'Exécution Global de la Paie (PayrollExecutionEngine)")
class PayrollExecutionEngineTest {

    private PayrollExecutionEngine engine;
    private List<Tranche> tranchesLf2026;

    @BeforeEach
    void setUp() {
        engine = new PayrollExecutionEngine();
        tranchesLf2026 = List.of(
                new Tranche(new BigDecimal("0.00"), new BigDecimal("350000.00"), new BigDecimal("0.0")),
                new Tranche(new BigDecimal("350000.00"), new BigDecimal("400000.00"), new BigDecimal("5.0")),
                new Tranche(new BigDecimal("400000.00"), new BigDecimal("500000.00"), new BigDecimal("10.0")),
                new Tranche(new BigDecimal("500000.00"), new BigDecimal("600000.00"), new BigDecimal("15.0")),
                new Tranche(new BigDecimal("600000.00"), null, new BigDecimal("20.0"))
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
        in.setTauxCnapsSalarial(new BigDecimal("0.01"));
        in.setTauxCnapsPatronal(new BigDecimal("0.13"));
        in.setTauxSanteSalarial(new BigDecimal("0.01"));
        in.setTauxSantePatronal(new BigDecimal("0.05"));
        in.setTauxFmfpPatronal(new BigDecimal("0.01"));
        in.setMajorationHs30(new BigDecimal("0.30"));
        in.setMajorationHs50(new BigDecimal("0.50"));
        in.setMajorationNuit(new BigDecimal("0.30"));
        in.setMajorationDimanche(new BigDecimal("0.40"));
        in.setMajorationFerie(BigDecimal.ZERO);

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
        // Le taux ISI est une donnée obligatoire du contexte (aucun fallback moteur).
        in.setTauxIsi(new BigDecimal("5.00"));

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

    @Test
    @DisplayName("Couche Unitaire 1 - Calcul du Brut avec Heures Sup et Primes")
    void testCalculerSalaireBrutUnitaire() {
        PayrollCalculationInput in = createStandardInput(new BigDecimal("400000.00"), "PERMANENT");
        in.setHeuresHs30(new BigDecimal("10.0")); // 10h à +30%
        in.setPrimePerformanceBase(new BigDecimal("50000.00"));
        BulletinPaie bp = engine.initialiserEnTete(in);
        List<mg.galaxam.sirh.model.paie.BulletinPaieLigne> lignes = new ArrayList<>();

        BigDecimal brut = engine.calculerSalaireBrut(in, bp, lignes);

        // Taux horaire = 400 000 / 173.33 = ~2307.736 Ar. HS30 = 10 * 2307.736 * 1.3 = ~30 000.57 Ar.
        // Brut = 400 000 + 30 000.57 + 50 000 = ~480 000.57 Ar
        assertTrue(brut.compareTo(new BigDecimal("479000.00")) > 0);
        assertEquals(brut, bp.getTotalGainsBruts());
        assertFalse(lignes.isEmpty());
    }

    @Test
    @DisplayName("Ownership — prime performance conserve le montant attribué et expose le montant payé")
    void testPrimePerformanceMontantAttribueDistinctDuMontantPaye() {
        PayrollCalculationInput input = createStandardInput(new BigDecimal("500000.00"), "PERMANENT");
        input.setPrimePerformanceBase(new BigDecimal("100000.00"));
        input.setNbJoursOuvrables(new BigDecimal("20"));
        input.setNbJoursPresents(new BigDecimal("10"));

        BulletinPaieComplet result = engine.calculer(input);
        var line = result.trouverLigne("PRIME_PERFORMANCE");

        assertNotNull(line);
        assertEquals(new BigDecimal("100000.00"), line.getBaseCalcul());
        assertEquals(new BigDecimal("50000.00"), line.getMontantSalarial());
    }

    @Test
    @DisplayName("Ownership — avance plafonnée produit une imputation partielle traçable")
    void testAvancePlafonneeConserveUneImputationPartielle() {
        PayrollCalculationInput input = createStandardInput(new BigDecimal("625000.00"), "PERMANENT");
        PaieAvanceAcompte avance = new PaieAvanceAcompte();
        avance.setIdAvance(42);
        avance.setTypeMouvement("AVANCE_SPECIALE");
        avance.setMontant(new BigDecimal("200000.00"));
        avance.setMotif("Avance test");
        input.setAvancesAcomptes(List.of(avance));

        BulletinPaieComplet result = engine.calculer(input);
        var imputation = result.getImputationsMouvements().get(0);

        assertEquals(42, imputation.idMouvement());
        assertTrue(imputation.montantImpute().compareTo(avance.getMontant()) < 0);
        assertTrue(avance.getMontant().subtract(imputation.montantImpute()).compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("Couche Unitaire 2 & 3 - Cotisations Sociales Plafonnées à 8x SME et Net Imposable")
    void testCalculerCotisationsEtNetImposableUnitaire() {
        PayrollCalculationInput in = createStandardInput(new BigDecimal("3000000.00"), "PERMANENT");
        // Plafond 8x SME = 8 * 262 680 = 2 101 440 Ar
        in.setEstPlafonneSante(true);
        BulletinPaie bp = engine.initialiserEnTete(in);
        List<mg.galaxam.sirh.model.paie.BulletinPaieLigne> lignes = new ArrayList<>();

        BigDecimal brut = new BigDecimal("3000000.00");
        var cotis = engine.calculerCotisationsSociales(brut, in, bp, lignes);

        // CNaPS salariale plafonnée à 1% de 2 101 440 = 21 014.40 Ar
        // Santé salariale plafonnée à 1% de 2 101 440 = 21 014.40 Ar
        // Total salarial = 42 028.80 Ar
        BigDecimal plafond = new BigDecimal("2101440.00");
        BigDecimal attenduSal = plafond.multiply(new BigDecimal("0.02")).setScale(2, java.math.RoundingMode.HALF_UP);
        assertEquals(attenduSal, cotis.totalCotisationsSalariales());

        BigDecimal netImposable = engine.calculerNetImposable(brut, cotis.totalCotisationsSalariales(), bp);
        assertEquals(brut.subtract(attenduSal), netImposable);
        assertEquals(netImposable, bp.getSalaireNetImposable());
    }

    @Test
    @DisplayName("Couche Unitaire 4 - Fiscalité IRSA avec Minimum 3 000 Ar et 6 Tranches")
    void testCalculerFiscaliteIrsaUnitaire() {
        PayrollCalculationInput in = createStandardInput(new BigDecimal("360000.00"), "PERMANENT");
        BulletinPaie bp = engine.initialiserEnTete(in);
        List<mg.galaxam.sirh.model.paie.BulletinPaieLigne> lignes = new ArrayList<>();

        // Net imposable = 360 000 Ar.
        // Tranche 1 (0 à 350k) = 0 Ar.
        // Tranche 2 (350k à 360k = 10 000 Ar) * 5% = 500 Ar.
        // Impôt brut < minimum légal de 3 000 Ar -> Le minimum de perception 3 000 Ar doit s'appliquer !
        var fisc = engine.calculerFiscalite(new BigDecimal("360000.00"), new BigDecimal("360000.00"), in, bp, lignes);

        assertEquals(new BigDecimal("3000.00"), fisc.irsaNet());
        assertEquals(new BigDecimal("3000.00"), bp.getIrsaNet());
        assertEquals(BigDecimal.ZERO.setScale(2), fisc.retenueIsi());
    }

    @Test
    @DisplayName("Couche Unitaire 6 & 7 - Retenues Plafonnées et Net à Payer Arrondi à la dizaine d'Ariary")
    void testCalculerNetAPayerEtArrondiUnitaire() {
        BulletinPaie bp = new BulletinPaie();
        List<mg.galaxam.sirh.model.paie.BulletinPaieLigne> lignes = new ArrayList<>();

        BigDecimal brut = new BigDecimal("854234.00");
        BigDecimal patronal = new BigDecimal("120000.00");
        BigDecimal indemnites = new BigDecimal("42000.00");
        BigDecimal netAvantTresorerie = new BigDecimal("785423.45"); // Non arrondi
        BigDecimal totalRetenues = new BigDecimal("100000.00");

        engine.calculerNetAPayer(brut, patronal, indemnites, netAvantTresorerie, totalRetenues, bp, lignes);

        // Net avant arrondi = 785 423.45 - 100 000 = 685 423.45 Ar
        assertEquals(new BigDecimal("685423.45"), bp.getNetAvantArrondi());

        // Arrondi bancaire officiel aux 10 Ariary près : 685 420.00 Ar
        assertEquals(new BigDecimal("685420.00"), bp.getNetAPayer());
        assertEquals(0, bp.getNetAPayer().remainder(BigDecimal.TEN).compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("Règles Ohatra - Prime d'Assiduité conditionnée au seuil de retard (< 45 min)")
    void testPrimeAssiduiteConditionSeuil() {
        // Cas 1 : Retard = 30 min (< 45 min) -> Prime accordée
        PayrollCalculationInput in1 = createStandardInput(new BigDecimal("500000.00"), "PERMANENT");
        in1.setPrimeAssiduiteForfait(new BigDecimal("20000.00"));
        in1.setMinutesRetard(30);
        in1.setSeuilRetardAssiduiteMinutes(45);

        BulletinPaieComplet res1 = engine.calculer(in1);
        assertEquals(new BigDecimal("20000.00"), res1.getMontantLigneSalariale("PRIME_ASSIDUITE"));

        // Cas 2 : Retard = 50 min (>= 45 min) -> Prime refusée (0 Ar)
        PayrollCalculationInput in2 = createStandardInput(new BigDecimal("500000.00"), "PERMANENT");
        in2.setPrimeAssiduiteForfait(new BigDecimal("20000.00"));
        in2.setMinutesRetard(50);
        in2.setSeuilRetardAssiduiteMinutes(45);

        BulletinPaieComplet res2 = engine.calculer(in2);
        assertEquals(BigDecimal.ZERO, res2.getMontantLigneSalariale("PRIME_ASSIDUITE"));
    }

    @Test
    @DisplayName("Règles Ohatra - Prime de Performance avec Pénalité -1/4 si retard > 70mn ET congés > 1j")
    void testPrimePerformancePenaliteOhatra() {
        BigDecimal ppBase = new BigDecimal("40000.00");

        // Cas 1 : Retard = 75 min (>70) ET Congés = 2 jours (>1j) -> Pénalité 25% appliquée
        // PP proratisée = 40 000 Ar, Malus 25% = 10 000 Ar -> Net PP = 30 000 Ar
        PayrollCalculationInput in1 = createStandardInput(new BigDecimal("500000.00"), "PERMANENT");
        in1.setPrimePerformanceBase(ppBase);
        in1.setMinutesRetard(75);
        in1.setNbJoursConges(new BigDecimal("2.0"));
        in1.setSeuilRetardMalusPerformanceMinutes(70);
        in1.setSeuilCongesMalusPerformanceJours(new BigDecimal("1.0"));
        in1.setTauxMalusPerformance(new BigDecimal("0.25"));

        BulletinPaieComplet res1 = engine.calculer(in1);
        assertEquals(new BigDecimal("30000.00"), res1.getMontantLigneSalariale("PRIME_PERFORMANCE"));

        // Cas 2 : Retard = 75 min (>70) mais Congés = 0 jour (<= 1j) -> Pas de pénalité !
        PayrollCalculationInput in2 = createStandardInput(new BigDecimal("500000.00"), "PERMANENT");
        in2.setPrimePerformanceBase(ppBase);
        in2.setMinutesRetard(75);
        in2.setNbJoursConges(BigDecimal.ZERO);
        in2.setSeuilRetardMalusPerformanceMinutes(70);
        in2.setSeuilCongesMalusPerformanceJours(new BigDecimal("1.0"));
        in2.setTauxMalusPerformance(new BigDecimal("0.25"));

        BulletinPaieComplet res2 = engine.calculer(in2);
        assertEquals(new BigDecimal("40000.00"), res2.getMontantLigneSalariale("PRIME_PERFORMANCE"));

        // Cas 3 : Congés = 3 jours (>1j) mais Retard = 20 min (<= 70 min) -> Pas de pénalité !
        PayrollCalculationInput in3 = createStandardInput(new BigDecimal("500000.00"), "PERMANENT");
        in3.setPrimePerformanceBase(ppBase);
        in3.setMinutesRetard(20);
        in3.setNbJoursConges(new BigDecimal("3.0"));
        in3.setSeuilRetardMalusPerformanceMinutes(70);
        in3.setSeuilCongesMalusPerformanceJours(new BigDecimal("1.0"));
        in3.setTauxMalusPerformance(new BigDecimal("0.25"));

        BulletinPaieComplet res3 = engine.calculer(in3);
        assertEquals(new BigDecimal("40000.00"), res3.getMontantLigneSalariale("PRIME_PERFORMANCE"));
    }
}
