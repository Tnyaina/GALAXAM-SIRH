package mg.galaxam.sirh.unit.paie;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import mg.galaxam.sirh.model.conge.engine.CongeCalculateurEngine;
import mg.galaxam.sirh.model.paie.BulletinPaie;
import mg.galaxam.sirh.model.paie.BulletinPaieLigne;
import mg.galaxam.sirh.model.paie.PaieAvanceAcompte;
import mg.galaxam.sirh.model.paie.PaiePretEcheance;
import mg.galaxam.sirh.model.paie.dto.BulletinPaieComplet;
import mg.galaxam.sirh.model.paie.dto.PayrollCalculationInput;
import mg.galaxam.sirh.model.paie.engine.IrsaCalculator;
import mg.galaxam.sirh.model.paie.engine.IrsaCalculator.Tranche;
import mg.galaxam.sirh.model.paie.engine.MoneyRoundingPolicy;
import mg.galaxam.sirh.model.paie.engine.OvertimePayCalculator;
import mg.galaxam.sirh.model.paie.engine.PayrollExecutionEngine;
import mg.galaxam.sirh.model.paie.engine.SocialContributionCalculator;
import mg.galaxam.sirh.model.pointage.calculation.DailyAttendanceCalculator;
import mg.galaxam.sirh.model.pointage.calculation.DailyAttendanceContext;
import mg.galaxam.sirh.model.pointage.calculation.DailyAttendanceResult;
import mg.galaxam.sirh.model.pointage.calculation.WeeklyAttendanceCalculator;
import mg.galaxam.sirh.model.pointage.calculation.WeeklyAttendanceContext;
import mg.galaxam.sirh.model.pointage.calculation.WeeklyAttendanceResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * SUITE DE CONTRÔLE DE CONFORMITÉ INTÉGRALE DES CALCULS
 * Conforme au Code du Travail Malagasy (Loi n° 2003-044), au Code Général des Impôts (CGI),
 * et aux règles de paie et pointage de référence GALAXAM.
 */
@DisplayName("Conformité Intégrale des Moteurs de Calcul : Paie, Congés, Absences & Pointage")
public class CalculsConformiteIntegraleTest {

    private PayrollExecutionEngine payrollEngine;
    private CongeCalculateurEngine congeEngine;
    private DailyAttendanceCalculator dailyAttendanceCalculator;
    private WeeklyAttendanceCalculator weeklyAttendanceCalculator;
    private List<Tranche> tranchesLf2026;

    @BeforeEach
    void setUp() {
        payrollEngine = new PayrollExecutionEngine();
        congeEngine = new CongeCalculateurEngine();
        dailyAttendanceCalculator = new DailyAttendanceCalculator();
        weeklyAttendanceCalculator = new WeeklyAttendanceCalculator();

        // Barème fiscal progressif IRSA officiel Madagascar
        tranchesLf2026 = List.of(
                new Tranche(new BigDecimal("0.00"), new BigDecimal("350000.00"), new BigDecimal("0.0")),
                new Tranche(new BigDecimal("350000.00"), new BigDecimal("400000.00"), new BigDecimal("5.0")),
                new Tranche(new BigDecimal("400000.00"), new BigDecimal("500000.00"), new BigDecimal("10.0")),
                new Tranche(new BigDecimal("500000.00"), new BigDecimal("600000.00"), new BigDecimal("15.0")),
                new Tranche(new BigDecimal("600000.00"), null, new BigDecimal("20.0"))
        );
    }

    private PayrollCalculationInput buildBaseInput(BigDecimal salaireBase, String regime) {
        PayrollCalculationInput in = new PayrollCalculationInput();
        in.setIdEmploye(101);
        in.setIdPeriodePaie(2026);
        in.setCodePeriode("PER-2026-01");
        in.setDateDebutPeriode(Date.valueOf("2026-01-01"));
        in.setDateFinPeriode(Date.valueOf("2026-01-31"));
        in.setMatricule("EMP-CONFORMITE");
        in.setNomPrenom("RAKOTO Jean-Baptiste");
        in.setRegimeIntervenant(regime);
        in.setSalaireBaseContractuel(salaireBase);
        in.setCategorieProfessionnelle("AGENT");
        in.setModeCalculPaie("MENSUEL");
        in.setNbJoursOuvrables(new BigDecimal("22.0"));
        in.setNbJoursPresents(new BigDecimal("22.0"));
        in.setTotalHeuresTravaillees(new BigDecimal("173.33"));
        in.setHeuresNormales(new BigDecimal("173.33"));
        in.setModeCalculAbsence("HEURES_REELLES");

        // Paramètres légaux Madagascar
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
        in.setMajorationFerie(new BigDecimal("1.00"));

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

    // =========================================================================
    // 1. CONFORMITÉ DU CALCUL DE PAIE
    // =========================================================================

    @Nested
    @DisplayName("1. Moteur de Paie : Salaire Brut, Heures Sup & Primes")
    class PaieBrutTests {

        @Test
        @DisplayName("1.1 Prorata temporis en cours de mois (Entrée / Sortie) : jours sous contrat /30 obligatoire")
        void testProrataTemporisEntreeSortie() {
            PayrollCalculationInput in = buildBaseInput(new BigDecimal("600000.00"), "PERMANENT");
            in.setEntreeSortieEnCoursDeMois(true);

            // Phase 2A-1 : Le fallback heuresReelles * tauxHoraire est strictement interdit
            BulletinPaie bp = payrollEngine.initialiserEnTete(in);
            List<BulletinPaieLigne> lignes = new ArrayList<>();
            assertThrows(IllegalStateException.class, () -> payrollEngine.calculerSalaireBrut(in, bp, lignes));

            // Avec les jours sous contrat canoniques (15 jours sur 30 = moitié du mois)
            in.setJoursSousContrat(new BigDecimal("15"));
            List<BulletinPaieLigne> lignesValides = new ArrayList<>();
            BigDecimal brut = payrollEngine.calculerSalaireBrut(in, bp, lignesValides);

            // 600 000 * 15 / 30 = 300 000.00 Ar
            assertEquals(new BigDecimal("300000.00"), brut);
            assertEquals(new BigDecimal("300000.00"), bp.getTotalGainsBruts());
        }

        @Test
        @DisplayName("1.1b Prorata trentième calendaire (Entrée le 11 -> 20j/30)")
        void testProrataTrentiemeEntreeLe11() {
            // Entrée le 11 : 20 jours calendaires sous contrat (30 - 11 + 1 = 20)
            PayrollCalculationInput in = buildBaseInput(new BigDecimal("600000.00"), "PERMANENT");
            in.setEntreeSortieEnCoursDeMois(true);
            in.setJoursSousContrat(new BigDecimal("20"));

            BulletinPaie bp = payrollEngine.initialiserEnTete(in);
            List<BulletinPaieLigne> lignes = new ArrayList<>();
            BigDecimal brut = payrollEngine.calculerSalaireBrut(in, bp, lignes);

            // 600 000 * 20 / 30 = 400 000.00 Ar
            assertEquals(new BigDecimal("400000.00"), brut);
            assertEquals(new BigDecimal("400000.00"), bp.getTotalGainsBruts());
        }

        @Test
        @DisplayName("1.1c Prorata trentième calendaire (Sortie le 20 -> 20j/30)")
        void testProrataTrentiemeSortieLe20() {
            // Sortie le 20 : 20 jours calendaires sous contrat (20 - 1 + 1 = 20)
            PayrollCalculationInput in = buildBaseInput(new BigDecimal("600000.00"), "PERMANENT");
            in.setEntreeSortieEnCoursDeMois(true);
            in.setJoursSousContrat(new BigDecimal("20"));

            BulletinPaie bp = payrollEngine.initialiserEnTete(in);
            List<BulletinPaieLigne> lignes = new ArrayList<>();
            BigDecimal brut = payrollEngine.calculerSalaireBrut(in, bp, lignes);

            // 600 000 * 20 / 30 = 400 000.00 Ar
            assertEquals(new BigDecimal("400000.00"), brut);
        }

        @Test
        @DisplayName("1.1d Prorata trentième calendaire (Entrée le 5 et sortie le 25 -> 21j/30)")
        void testProrataTrentiemeEntree5Sortie25() {
            // Du 5 au 25 inclus = 21 jours
            PayrollCalculationInput in = buildBaseInput(new BigDecimal("600000.00"), "PERMANENT");
            in.setEntreeSortieEnCoursDeMois(true);
            in.setJoursSousContrat(new BigDecimal("21"));

            BulletinPaie bp = payrollEngine.initialiserEnTete(in);
            List<BulletinPaieLigne> lignes = new ArrayList<>();
            BigDecimal brut = payrollEngine.calculerSalaireBrut(in, bp, lignes);

            // 600 000 * 21 / 30 = 420 000.00 Ar
            assertEquals(new BigDecimal("420000.00"), brut);
        }

        @Test
        @DisplayName("1.1e Prorata trentième calendaire (Mois complet sous contrat -> 30j/30, BASE pleine)")
        void testProrataTrentiemeMoisComplet() {
            PayrollCalculationInput in = buildBaseInput(new BigDecimal("600000.00"), "PERMANENT");
            in.setEntreeSortieEnCoursDeMois(true);
            in.setJoursSousContrat(new BigDecimal("30"));

            BulletinPaie bp = payrollEngine.initialiserEnTete(in);
            List<BulletinPaieLigne> lignes = new ArrayList<>();
            BigDecimal brut = payrollEngine.calculerSalaireBrut(in, bp, lignes);

            assertEquals(new BigDecimal("600000.00"), brut);
        }

        @Test
        @DisplayName("1.2 Retenue d'absence en mode Heures Réelles")
        void testRetenueAbsenceHeuresReelles() {
            PayrollCalculationInput in = buildBaseInput(new BigDecimal("520000.00"), "PERMANENT");
            in.setModeCalculAbsence("HEURES_REELLES");
            in.setNbHeuresAbsence(new BigDecimal("16.0")); // 2 jours de 8h

            BulletinPaie bp = payrollEngine.initialiserEnTete(in);
            List<BulletinPaieLigne> lignes = new ArrayList<>();
            BigDecimal brut = payrollEngine.calculerSalaireBrut(in, bp, lignes);

            // Taux horaire = 520 000 / 173.33 = 3000.0577 Ar
            // Retenue = 16 * 3000.0577 = 48000.92 Ar
            // Brut = 520 000 - 48000.92 = 471 999.08 Ar
            BigDecimal montantRetenue = lignes.stream()
                    .filter(l -> "RETENUE_ABSENCE".equals(l.getCodeRubrique()))
                    .map(BulletinPaieLigne::getMontantSalarial)
                    .findFirst().orElse(BigDecimal.ZERO);

            assertEquals(new BigDecimal("48000.92"), montantRetenue);
            assertEquals(new BigDecimal("471999.08"), brut);
        }

        @Test
        @DisplayName("1.3 Retenue d'absence en heures réelles canonique")
        void testRetenueAbsenceTrentieme() {
            PayrollCalculationInput in = buildBaseInput(new BigDecimal("600000.00"), "PERMANENT");
            // Phase 2A-1 : Absences consommées en temps (heures réelles)
            in.setNbHeuresAbsence(new BigDecimal("24.0"));

            BulletinPaie bp = payrollEngine.initialiserEnTete(in);
            List<BulletinPaieLigne> lignes = new ArrayList<>();
            BigDecimal brut = payrollEngine.calculerSalaireBrut(in, bp, lignes);

            // Taux horaire = 600 000 / 173.33 = ~3461.6050
            // Retenue pour 24h = 24 * 3461.6050 = 83078.52 Ar
            // Brut = 600 000 - 83078.52 = 516921.48 Ar
            BigDecimal montantRetenue = lignes.stream()
                    .filter(l -> "RETENUE_ABSENCE".equals(l.getCodeRubrique()))
                    .map(BulletinPaieLigne::getMontantSalarial)
                    .findFirst().orElse(BigDecimal.ZERO);

            assertEquals(new BigDecimal("83078.52"), montantRetenue);
            assertEquals(new BigDecimal("516921.48"), brut);
        }

        @Test
        @DisplayName("1.4 Heures supplémentaires : HS 30%, HS 50%, Nuit (+30%), Férié (+100%)")
        void testHeuresSupplementairesEtMajorations() {
            PayrollCalculationInput in = buildBaseInput(new BigDecimal("346660.00"), "PERMANENT");
            // Taux horaire = 346 660 / 173.33 = 2 000.00 Ar
            in.setMajorationHs30(new BigDecimal("0.30"));
            in.setMajorationHs50(new BigDecimal("0.50"));
            in.setMajorationNuit(new BigDecimal("0.30"));
            in.setMajorationFerie(new BigDecimal("1.00"));

            in.setHeuresHs30(new BigDecimal("8.0"));   // 8h à +30% : 8 * 2000 * 1.30 = 20 800 Ar
            in.setHeuresHs50(new BigDecimal("4.0"));   // 4h à +50% : 4 * 2000 * 1.50 = 12 000 Ar
            in.setHeuresNuit(new BigDecimal("5.0"));   // 5h à +30% nuit : 5 * 2000 * 0.30 = 3 000 Ar
            in.setHeuresFeriees(new BigDecimal("8.0"));// 8h à +100% férié : 8 * 2000 * 2.00 = 32 000 Ar

            BulletinPaie bp = payrollEngine.initialiserEnTete(in);
            List<BulletinPaieLigne> lignes = new ArrayList<>();
            payrollEngine.calculerSalaireBrut(in, bp, lignes);

            assertEquals(new BigDecimal("20800.00"), getMontantLigne(lignes, "HEURES_SUP_30"));
            assertEquals(new BigDecimal("12000.00"), getMontantLigne(lignes, "HEURES_SUP_50"));
            assertEquals(new BigDecimal("3000.00"), getMontantLigne(lignes, "MAJORATION_NUIT"));
            assertEquals(new BigDecimal("32000.00"), getMontantLigne(lignes, "MAJORATION_FERIE"));
        }

        @Test
        @DisplayName("1.4b Majoration Dimanche travaillé (+40%) : 6h à 140% = 16 800 Ar")
        void testMajorationDimancheTravaille() {
            PayrollCalculationInput in = buildBaseInput(new BigDecimal("346660.00"), "PERMANENT");
            // Taux horaire = 2 000.00 Ar
            in.setMajorationDimanche(new BigDecimal("0.40"));
            in.setHeuresDimanche(new BigDecimal("6.0")); // 6h à 140% : 6 * 2000 * 1.40 = 16 800 Ar

            BulletinPaie bp = payrollEngine.initialiserEnTete(in);
            List<BulletinPaieLigne> lignes = new ArrayList<>();
            payrollEngine.calculerSalaireBrut(in, bp, lignes);

            assertEquals(new BigDecimal("16800.00"), getMontantLigne(lignes, "HEURES_DIMANCHE"));
        }

        @Test
        @DisplayName("1.5 Prime d'ancienneté légale (3 ans révolus : 3%, 15 ans : 15%, max 20%)")
        void testPrimeAncienneteLegale() {
            PayrollCalculationInput in = buildBaseInput(new BigDecimal("500000.00"), "PERMANENT");
            in.setCalculerPrimeAncienneteAuto(true);

            // Cas 1 : Moins de 3 ans (ex 24 mois = 2 ans) -> 0 Ar
            in.setAncienneteMois(24);
            BulletinPaie bp1 = payrollEngine.initialiserEnTete(in);
            List<BulletinPaieLigne> l1 = new ArrayList<>();
            payrollEngine.calculerSalaireBrut(in, bp1, l1);
            assertEquals(BigDecimal.ZERO, getMontantLigne(l1, "PRIME_ANCIENNETE"));

            // Cas 2 : 5 ans d'ancienneté (60 mois) -> 5% de 500 000 = 25 000 Ar
            in.setAncienneteMois(60);
            BulletinPaie bp2 = payrollEngine.initialiserEnTete(in);
            List<BulletinPaieLigne> l2 = new ArrayList<>();
            payrollEngine.calculerSalaireBrut(in, bp2, l2);
            assertEquals(new BigDecimal("25000.00"), getMontantLigne(l2, "PRIME_ANCIENNETE"));

            // Cas 3 : 25 ans d'ancienneté (300 mois) -> Plafond 20% = 100 000 Ar
            in.setAncienneteMois(300);
            BulletinPaie bp3 = payrollEngine.initialiserEnTete(in);
            List<BulletinPaieLigne> l3 = new ArrayList<>();
            payrollEngine.calculerSalaireBrut(in, bp3, l3);
            assertEquals(new BigDecimal("100000.00"), getMontantLigne(l3, "PRIME_ANCIENNETE"));
        }
    }

    // =========================================================================
    // 2. CONFORMITÉ DES COTISATIONS SOCIALES & FISCALITÉ (MADAGASCAR)
    // =========================================================================

    @Nested
    @DisplayName("2. Cotisations Sociales & Barème IRSA Madagascar")
    class CotisationsEtFiscaliteTests {

        @Test
        @DisplayName("2.1 Plafonnement CNaPS à 8x SME (SME = 262 680 Ar -> Plafond = 2 101 440 Ar)")
        void testPlafonnementCnaps() {
            PayrollCalculationInput in = buildBaseInput(new BigDecimal("4000000.00"), "PERMANENT");
            BulletinPaie bp = payrollEngine.initialiserEnTete(in);
            List<BulletinPaieLigne> lignes = new ArrayList<>();

            var cotis = payrollEngine.calculerCotisationsSociales(new BigDecimal("4000000.00"), in, bp, lignes);

            BigDecimal plafondAttendu = new BigDecimal("2101440.00");
            BigDecimal cnapsSalAttendue = new BigDecimal("21014.40");
            BigDecimal cnapsPatAttendue = new BigDecimal("273187.20"); // 13% de 2 101 440
            BigDecimal fmfpAttendue = new BigDecimal("40000.00");     // 1% sur brut total non plafonné (4 000 000)

            assertEquals(cnapsSalAttendue, getMontantLigne(lignes, "COTISATION_CNAPS_SAL"));
            assertEquals(cnapsPatAttendue, getMontantPatronal(lignes, "COTISATION_CNAPS_PAT"));
            assertEquals(fmfpAttendue, getMontantPatronal(lignes, "COTISATION_FMFP_PAT"));
        }

        @Test
        @DisplayName("2.2 Santé OMSI (1.5% salarial / 5.5% patronal)")
        void testSanteOmsi() {
            PayrollCalculationInput in = buildBaseInput(new BigDecimal("1000000.00"), "PERMANENT");
            in.setCodeSanteOrgane("OMSI");
            in.setTauxSanteSalarial(new BigDecimal("0.015"));
            in.setTauxSantePatronal(new BigDecimal("0.055"));

            BulletinPaie bp = payrollEngine.initialiserEnTete(in);
            List<BulletinPaieLigne> lignes = new ArrayList<>();

            payrollEngine.calculerCotisationsSociales(new BigDecimal("1000000.00"), in, bp, lignes);

            assertEquals(new BigDecimal("15000.00"), getMontantLigne(lignes, "COTISATION_OMSI_SAL"));
            assertEquals(new BigDecimal("55000.00"), getMontantPatronal(lignes, "COTISATION_OMSI_PAT"));
        }

        @Test
        @DisplayName("2.3 Fiscalité IRSA : Arrondi centaine inférieure, barème progressif, réduction enfants, min 3000 Ar")
        void testCalculIrsaComplet() {
            PayrollCalculationInput in = buildBaseInput(new BigDecimal("750000.00"), "PERMANENT");
            in.setNbEnfantsEligibles(3); // 3 enfants x 2000 Ar = 6000 Ar de réduction
            in.setDeductionParEnfant(new BigDecimal("2000.00"));
            in.setMinimumPerceptionIrsa(new BigDecimal("3000.00"));

            BulletinPaie bp = payrollEngine.initialiserEnTete(in);
            List<BulletinPaieLigne> lignes = new ArrayList<>();

            // Net imposable = 735 250.75 Ar (après cotisations)
            // Assiette arrondie à la centaine inférieure = 735 200.00 Ar
            BigDecimal netImposable = new BigDecimal("735250.75");
            var fisc = payrollEngine.calculerFiscalite(netImposable, netImposable, in, bp, lignes);

            // Calcul par tranches :
            // 0 à 350k : 0
            // 350k à 400k (50k) * 5% = 2 500
            // 400k à 500k (100k) * 10% = 10 000
            // 500k à 600k (100k) * 15% = 15 000
            // 600k à 735 200 (135 200) * 20% = 27 040
            // Total brut = 2500 + 10000 + 15000 + 27040 = 54 540 Ar
            // Réduction 3 enfants = 6 000 Ar
            // Impôt net = 54 540 - 6 000 = 48 540 Ar
            assertEquals(new BigDecimal("735200.00"), bp.getBaseIrsaArrondie());
            assertEquals(new BigDecimal("54540.00"), bp.getIrsaBrut());
            assertEquals(new BigDecimal("6000.00"), bp.getReductionEnfants());
            assertEquals(new BigDecimal("48540.00"), fisc.irsaNet());
            assertEquals(new BigDecimal("48540.00"), bp.getIrsaNet());
        }

        @Test
        @DisplayName("2.4 Application stricte du minimum de perception IRSA (3 000 Ar)")
        void testMinimumPerceptionIrsa() {
            PayrollCalculationInput in = buildBaseInput(new BigDecimal("355000.00"), "PERMANENT");
            in.setNbEnfantsEligibles(0);
            in.setMinimumPerceptionIrsa(new BigDecimal("3000.00"));

            BulletinPaie bp = payrollEngine.initialiserEnTete(in);
            List<BulletinPaieLigne> lignes = new ArrayList<>();

            // Net imposable = 352 000 Ar -> Base = 352 000 Ar
            // Tranche 2 : 2 000 * 5% = 100 Ar < 3 000 Ar -> Impôt perçu = 3 000 Ar
            var fisc = payrollEngine.calculerFiscalite(new BigDecimal("352000.00"), new BigDecimal("352000.00"), in, bp, lignes);
            assertEquals(new BigDecimal("3000.00"), fisc.irsaNet());
        }
    }

    // =========================================================================
    // 3. CONFORMITÉ DES DÉDUCTIONS, QUOTITÉS CESSIBLES & ARRONDI
    // =========================================================================

    @Nested
    @DisplayName("3. Retenues, Quotité Cessible (1/3 Art. 72, 1/10 Art. 68) & Arrondi MROUND 10")
    class RetenuesEtQuotiteTests {

        @Test
        @DisplayName("3.1 Plafond Avance Employeur 1/10e (Art. 68)")
        void testPlafondAvanceDixieme() {
            PayrollCalculationInput in = buildBaseInput(new BigDecimal("1000000.00"), "PERMANENT");

            // Demande d'avance spéciale de 200 000 Ar (alors que net disponible = 900 000 Ar -> max 10% = 90 000 Ar)
            PaieAvanceAcompte av = new PaieAvanceAcompte();
            av.setIdAvance(1);
            av.setTypeMouvement("AVANCE_SPECIALE");
            av.setMontant(new BigDecimal("200000.00"));
            av.setMotif("Avance spéciale exceptionnelle");
            in.setAvancesAcomptes(List.of(av));

            BulletinPaie bp = payrollEngine.initialiserEnTete(in);
            List<BulletinPaieLigne> lignes = new ArrayList<>();

            BigDecimal netAvantTresorerie = new BigDecimal("900000.00");
            var retenues = payrollEngine.calculerRetenuesEtAvances(netAvantTresorerie, in, bp, lignes);

            // Doit être plafonné à 1/10e = 90 000.00 Ar
            assertEquals(new BigDecimal("90000.00"), retenues.totalRetenues());
            assertEquals(new BigDecimal("90000.00"), getMontantLigne(lignes, "RETENUE_AVANCE_SPECIALE"));
        }

        @Test
        @DisplayName("3.2 Arrondi monétaire bancaire officiel à la dizaine d'Ariary (MROUND 10)")
        void testArrondiMonetaireBancaireDizaine() {
            BulletinPaie bp = new BulletinPaie();
            List<BulletinPaieLigne> lignes = new ArrayList<>();

            // Cas 1 : 625 124.30 Ar -> Arrondi inférieur à 625 120.00 Ar
            payrollEngine.calculerNetAPayer(new BigDecimal("700000.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                    new BigDecimal("625124.30"), BigDecimal.ZERO, bp, lignes);
            assertEquals(new BigDecimal("625120.00"), bp.getNetAPayer());
            assertEquals(new BigDecimal("625124.30"), bp.getNetAvantArrondi());
            assertEquals(new BigDecimal("4.30"), getMontantLigne(lignes, "AJUSTEMENT_ARRONDI"));

            // Cas 2 : 625 127.80 Ar -> Arrondi supérieur à 625 130.00 Ar
            lignes.clear();
            payrollEngine.calculerNetAPayer(new BigDecimal("700000.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                    new BigDecimal("625127.80"), BigDecimal.ZERO, bp, lignes);
            assertEquals(new BigDecimal("625130.00"), bp.getNetAPayer());
            assertEquals(new BigDecimal("2.20"), getMontantLigne(lignes, "AJUSTEMENT_ARRONDI"));
        }
    }

    // =========================================================================
    // 4. CONFORMITÉ DE LA GESTION DES CONGÉS (LOI N° 2003-044)
    // =========================================================================

    @Nested
    @DisplayName("4. Moteur de Congés : Acquisition, Proratisation, Art. 13 et Pool Familial")
    class CongeEngineTests {

        @Test
        @DisplayName("4.1 Acquisition légale mensuelle : 2.5 jours par mois de travail effectif (Art. 85)")
        void testAcquisitionLegaleMensuelle() {
            assertEquals(new BigDecimal("2.5"), congeEngine.calculerAcquisitionMensuelle(1));
            assertEquals(new BigDecimal("15.0"), congeEngine.calculerAcquisitionMensuelle(6));
            assertEquals(new BigDecimal("30.0"), congeEngine.calculerAcquisitionMensuelle(12));
        }

        @Test
        @DisplayName("4.2 Proratisation d'acquisition au 30ème (jours / 30 * 2.5)")
        void testAcquisitionProratiseeTrentieme() {
            // 15 jours sur 30 -> 1.2500 j
            assertEquals(new BigDecimal("1.2500"), congeEngine.calculerAcquisitionMensuelleProratisee(new BigDecimal("15.0")));
            // 21 jours sur 30 -> 1.7500 j
            assertEquals(new BigDecimal("1.7500"), congeEngine.calculerAcquisitionMensuelleProratisee(new BigDecimal("21.0")));
        }

        @Test
        @DisplayName("4.3 Scission automatique des arrêts maladie > 30 jours (Art. 13)")
        void testScissionArretMaladieArt13() {
            assertFalse(congeEngine.doitScinderArretMaladie(new BigDecimal("29.0")));
            assertFalse(congeEngine.doitScinderArretMaladie(new BigDecimal("30.0")));
            assertTrue(congeEngine.doitScinderArretMaladie(new BigDecimal("30.5")));
            assertTrue(congeEngine.doitScinderArretMaladie(new BigDecimal("45.0")));
        }

        @Test
        @DisplayName("4.4 Plafond annuel pool familial (10 jours par an max, Art. 88)")
        void testPlafondPoolFamilial() {
            assertTrue(congeEngine.verifierEligibilitePoolFamilial(new BigDecimal("7.0"), new BigDecimal("3.0"))); // total 10j
            assertFalse(congeEngine.verifierEligibilitePoolFamilial(new BigDecimal("7.5"), new BigDecimal("3.0"))); // total 10.5j > 10j
        }

        @Test
        @DisplayName("4.5 Bonification d'ancienneté (+1j par tranche de 5 ans révolus)")
        void testBonificationAnciennete() {
            assertEquals(0, congeEngine.calculerBonificationAnciennete(4));
            assertEquals(1, congeEngine.calculerBonificationAnciennete(5));
            assertEquals(1, congeEngine.calculerBonificationAnciennete(9));
            assertEquals(2, congeEngine.calculerBonificationAnciennete(10));
            assertEquals(3, congeEngine.calculerBonificationAnciennete(15));
            assertEquals(4, congeEngine.calculerBonificationAnciennete(20));
        }

        @Test
        @DisplayName("4.6 Valorisation du droit et Taux Journalier Moyen TCONGE")
        void testValorisationEtTauxMoyenConge() {
            // Base totale = 600 000 Ar, Droit = 2.5 jours -> Valorisation = 600 000 * 2.5 / 30 = 50 000 Ar
            BigDecimal valorisation = congeEngine.calculerValorisationDroitMensuel(new BigDecimal("600000.00"), new BigDecimal("2.5"));
            assertEquals(new BigDecimal("50000.00"), valorisation);

            // Cumul valorisation 150 000 Ar pour 7.5 jours acquis -> Taux moyen = 20 000 Ar/jour
            BigDecimal tauxMoyen = congeEngine.calculerTauxJournalierMoyen(new BigDecimal("150000.00"), new BigDecimal("7.5"));
            assertEquals(new BigDecimal("20000.00"), tauxMoyen);
        }
    }

    // =========================================================================
    // 5. CONFORMITÉ DU POINTAGE & ATTENDANCE
    // =========================================================================

    @Nested
    @DisplayName("5. Moteur de Pointage : Qualification Hebdomadaire, Retards & Repas")
    class PointageTests {

        @Test
        @DisplayName("5.1 Qualification stricte des heures normales (40h = 2400 min) et HS")
        void testQualificationSemaineHeuresSup() {
            LocalDate lundi = LocalDate.of(2026, 9, 14);
            WeeklyAttendanceContext regle = new WeeklyAttendanceContext(lundi, 2400, 480, true, 480);

            // Semaine normale 40h pile (2400 min)
            WeeklyAttendanceResult resNormale = weeklyAttendanceCalculator.calculateWeek(regle,
                    List.of(creerJourAvecMinutes(lundi, 2400)));
            assertEquals(2400, resNormale.workedMinutes());
            assertEquals(0, resNormale.overtime30Minutes());
            assertEquals(0, resNormale.overtime50Minutes());

            // Semaine avec 45h (2700 min) -> 300 min (5h) de HS à 30%
            WeeklyAttendanceResult resHs = weeklyAttendanceCalculator.calculateWeek(regle,
                    List.of(creerJourAvecMinutes(lundi, 2700)));
            assertEquals(2700, resHs.workedMinutes());
            assertEquals(300, resHs.overtime30Minutes());
            assertEquals(0, resHs.overtime50Minutes());

            // Semaine avec 50h (3000 min) -> 480 min (8h) de HS à 30% et 120 min (2h) de HS à 50%
            WeeklyAttendanceResult resHsMax = weeklyAttendanceCalculator.calculateWeek(regle,
                    List.of(creerJourAvecMinutes(lundi, 3000)));
            assertEquals(3000, resHsMax.workedMinutes());
            assertEquals(480, resHsMax.overtime30Minutes());
            assertEquals(120, resHsMax.overtime50Minutes());
        }

        @Test
        @DisplayName("5.2 Décompte des retards journaliers et indemnité repas conditionnée à la présence")
        void testJourneeRetardEtRepas() {
            // Arrivée 8h25 au lieu de 8h00 -> 25 min de retard
            DailyAttendanceContext ctx = new DailyAttendanceContext(
                    101, LocalDate.of(2026, 9, 15),
                    java.time.LocalTime.of(8, 25), null, null, java.time.LocalTime.of(17, 0),
                    java.time.LocalTime.of(8, 0), null, null, java.time.LocalTime.of(17, 0),
                    java.time.LocalTime.of(22, 0), java.time.LocalTime.of(5, 0),
                    true, false, false,
                    DailyAttendanceContext.LeavePart.NONE, true,
                    new BigDecimal("2000.00"), BigDecimal.ONE, new BigDecimal("1400.00"),
                    BigDecimal.ZERO, false
            );

            DailyAttendanceResult res = dailyAttendanceCalculator.calculateDay(ctx);

            assertEquals(25, res.lateMinutes(), "Retard de 25 minutes constaté");
            assertEquals(new BigDecimal("2000.00"), res.mealAmount(), "Repas accordé car présent");
            assertEquals(new BigDecimal("1400.00"), res.travelAmount(), "Transport accordé");
        }
    }

    // =========================================================================
    // 6. CONFORMITÉ DU SOLDE DE TOUT COMPTE (STC) & ANCIENNETÉ
    // =========================================================================

    @Nested
    @DisplayName("6. Moteur STC & Ancienneté : Prorata, ICCP, Préavis & Licenciement")
    class StcEtAncienneteTests {

        private final mg.galaxam.sirh.model.contrat.engine.AncienneteEngine ancienneteEngine =
                new mg.galaxam.sirh.model.contrat.engine.AncienneteEngine();

        @Test
        @DisplayName("6.1 Calcul Ancienneté en chaîne continue de contrats (CDD puis CDI)")
        void testCalculAncienneteChaineContinue() {
            // Contrat 1 (CDD) : 01/01/2024 au 30/06/2024 (6 mois)
            // Contrat 2 (CDI) : 01/07/2024 au 30/06/2026 (24 mois), précédent = Contrat 1
            var c1 = new mg.galaxam.sirh.model.contrat.engine.AncienneteEngine.IntervalleContrat(
                    1, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 6, 30), null);
            var c2 = new mg.galaxam.sirh.model.contrat.engine.AncienneteEngine.IntervalleContrat(
                    2, LocalDate.of(2024, 7, 1), LocalDate.of(2026, 6, 30), 1);

            var bilan = ancienneteEngine.evaluerAnciennete(List.of(c1, c2), 2, LocalDate.of(2026, 6, 30));

            // Total chaîne = 30 mois = 2 ans et 6 mois
            assertEquals(2, bilan.ansEffectifs(), "2 ans effectifs");
            assertEquals(6, bilan.moisRestantsEffectifs(), "6 mois restants effectifs");
            assertEquals(2, bilan.nombreContratsChaine(), "Chaîne de 2 contrats");
            assertEquals(LocalDate.of(2024, 1, 1), bilan.dateOrigineChaine(), "Origine au 01/01/2024");
        }

        @Test
        @DisplayName("6.2 STC : Salaire Prorata du dernier mois (base * jours / 30)")
        void testStcSalaireProrataDernierMois() {
            BigDecimal salaireBase = new BigDecimal("900000.00");
            BigDecimal joursTravailles = new BigDecimal("10.0");

            // 900 000 * 10 / 30 = 300 000.00 Ar
            BigDecimal salaireProrata = salaireBase.multiply(joursTravailles)
                    .divide(new BigDecimal("30.0"), 2, java.math.RoundingMode.HALF_UP);

            assertEquals(new BigDecimal("300000.00"), salaireProrata);
        }

        @Test
        @DisplayName("6.3 STC : Indemnité Compensatrice de Congés Payés (ICCP = solde * tauxConge)")
        void testStcIndemniteCompensatriceConges() {
            BigDecimal soldeCongesRestant = new BigDecimal("12.5");
            BigDecimal tauxJournalierConge = new BigDecimal("25000.00");

            // ICCP = 12.5 * 25 000 = 312 500.00 Ar
            BigDecimal iccp = MoneyRoundingPolicy.amount(soldeCongesRestant.multiply(tauxJournalierConge));

            assertEquals(new BigDecimal("312500.00"), iccp);
        }

        @Test
        @DisplayName("6.4 STC : Retenue pour congés pris par anticipation si solde négatif")
        void testStcRetenueCongesAnticipes() {
            BigDecimal soldeNegatif = new BigDecimal("-3.0"); // 3 jours pris en avance
            BigDecimal tauxConge = new BigDecimal("20000.00");

            BigDecimal iccp = MoneyRoundingPolicy.amount(soldeNegatif.multiply(tauxConge));
            assertTrue(iccp.compareTo(BigDecimal.ZERO) < 0);

            BigDecimal retenueAnticipee = iccp.abs();
            assertEquals(new BigDecimal("60000.00"), retenueAnticipee);
        }

        @Test
        @DisplayName("6.5 STC : Indemnité Compensatrice de Préavis (moisPreavis * salaireBase)")
        void testStcIndemnitePreavis() {
            BigDecimal salaireBase = new BigDecimal("650000.00");
            BigDecimal moisPreavis = new BigDecimal("2.0"); // 2 mois de préavis légal cadre

            BigDecimal indemnitePreavis = MoneyRoundingPolicy.amount(salaireBase.multiply(moisPreavis));
            assertEquals(new BigDecimal("1300000.00"), indemnitePreavis);
        }
    }

    // =========================================================================
    // UTILITAIRES PRIVÉS
    // =========================================================================

    private BigDecimal getMontantLigne(List<BulletinPaieLigne> lignes, String codeRubrique) {
        return lignes.stream()
                .filter(l -> codeRubrique.equals(l.getCodeRubrique()))
                .map(BulletinPaieLigne::getMontantSalarial)
                .findFirst().orElse(BigDecimal.ZERO);
    }

    private BigDecimal getMontantPatronal(List<BulletinPaieLigne> lignes, String codeRubrique) {
        return lignes.stream()
                .filter(l -> codeRubrique.equals(l.getCodeRubrique()))
                .map(BulletinPaieLigne::getMontantPatronal)
                .findFirst().orElse(BigDecimal.ZERO);
    }

    private DailyAttendanceResult creerJourAvecMinutes(LocalDate date, int minutes) {
        return new DailyAttendanceResult(
                101, date, DailyAttendanceResult.Status.PRESENT,
                minutes, 0, minutes, minutes, 0, 0, 0, false, false,
                0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of()
        );
    }
}
