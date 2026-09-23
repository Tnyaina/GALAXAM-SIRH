package mg.galaxam.sirh.unit.paie.certification;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import mg.galaxam.sirh.paie.persistence.BulletinPaie;
import mg.galaxam.sirh.paie.domain.dto.BulletinPaieComplet;
import mg.galaxam.sirh.paie.domain.dto.PayrollAttendanceInput;
import mg.galaxam.sirh.paie.domain.dto.PayrollCalculationInput;
import mg.galaxam.sirh.paie.domain.engine.IrsaCalculator;
import mg.galaxam.sirh.paie.domain.engine.PayrollExecutionEngine;
import mg.galaxam.sirh.unit.paie.certification.FinancialCertificationIndependentOracle.IrsaTrancheDef;
import mg.galaxam.sirh.unit.paie.certification.FinancialCertificationIndependentOracle.OracleInput;
import mg.galaxam.sirh.unit.paie.certification.FinancialCertificationIndependentOracle.OracleResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * MISSION — CERTIFICATION FINANCIÈRE PRÉ-PRODUCTION DU MOTEUR DE PAIE GALAXAM
 *
 * PROUVE INDÉPENDAMMENT QUE LES MONTANTS FINANCIERS PRODUITS PAR LE MOTEUR SONT EXACTS.
 * Les attendus (EXPECTED) proviennent EXCLUSIVEMENT de l'Oracle Financier Indépendant
 * {@link FinancialCertificationIndependentOracle} et de formules mathématiques pures.
 */
@DisplayName("Certification Financière Pré-Production — Moteur de Paie GALAXAM")
public class PayrollFinancialPreProductionCertificationTest {

    private PayrollExecutionEngine engine;

    // Barème LF 2026 officiel (6 tranches)
    private static final List<IrsaTrancheDef> TRANCHES_LF2026_ORACLE = List.of(
            new IrsaTrancheDef(BigDecimal.ZERO, new BigDecimal("350000.00"), BigDecimal.ZERO),
            new IrsaTrancheDef(new BigDecimal("350000.00"), new BigDecimal("400000.00"), new BigDecimal("5.00")),
            new IrsaTrancheDef(new BigDecimal("400000.00"), new BigDecimal("500000.00"), new BigDecimal("10.00")),
            new IrsaTrancheDef(new BigDecimal("500000.00"), new BigDecimal("600000.00"), new BigDecimal("15.00")),
            new IrsaTrancheDef(new BigDecimal("600000.00"), new BigDecimal("4000000.00"), new BigDecimal("20.00")),
            new IrsaTrancheDef(new BigDecimal("4000000.00"), null, new BigDecimal("25.00"))
    );

    // Barème LF 2024-2025 officiel (5 tranches)
    private static final List<IrsaTrancheDef> TRANCHES_LF2025_ORACLE = List.of(
            new IrsaTrancheDef(BigDecimal.ZERO, new BigDecimal("350000.00"), BigDecimal.ZERO),
            new IrsaTrancheDef(new BigDecimal("350000.00"), new BigDecimal("400000.00"), new BigDecimal("5.00")),
            new IrsaTrancheDef(new BigDecimal("400000.00"), new BigDecimal("500000.00"), new BigDecimal("10.00")),
            new IrsaTrancheDef(new BigDecimal("500000.00"), new BigDecimal("600000.00"), new BigDecimal("15.00")),
            new IrsaTrancheDef(new BigDecimal("600000.00"), null, new BigDecimal("20.00"))
    );

    private static final List<IrsaCalculator.Tranche> TRANCHES_LF2026_ENGINE = List.of(
            new IrsaCalculator.Tranche(BigDecimal.ZERO, new BigDecimal("350000.00"), BigDecimal.ZERO),
            new IrsaCalculator.Tranche(new BigDecimal("350000.00"), new BigDecimal("400000.00"), new BigDecimal("0.05")),
            new IrsaCalculator.Tranche(new BigDecimal("400000.00"), new BigDecimal("500000.00"), new BigDecimal("0.10")),
            new IrsaCalculator.Tranche(new BigDecimal("500000.00"), new BigDecimal("600000.00"), new BigDecimal("0.15")),
            new IrsaCalculator.Tranche(new BigDecimal("600000.00"), new BigDecimal("4000000.00"), new BigDecimal("0.20")),
            new IrsaCalculator.Tranche(new BigDecimal("4000000.00"), null, new BigDecimal("0.25"))
    );

    @BeforeEach
    void setUp() {
        engine = new PayrollExecutionEngine();
    }

    private PayrollCalculationInput buildBaseInput(BigDecimal base) {
        PayrollCalculationInput in = new PayrollCalculationInput();
        in.setIdEmploye(1001);
        in.setIdPeriodePaie(202601);
        in.setCodePeriode("PER-2026-01");
        in.setMatricule("EMP-CERT-01");
        in.setNomPrenom("Employé Certification");
        in.setNomSite("Antananarivo");
        in.setCodeSanteOrgane("OSTIE");
        in.setSalaireBaseContractuel(base);
        in.setDiviseurMensuelHeures(new BigDecimal("173.33"));
        in.setSmeNational(new BigDecimal("300000.00"));
        in.setSoumisCnaps(true);
        in.setSoumisSante(true);
        in.setEstPlafonneSante(true);
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
        in.setSoumisIrsa(true);
        in.setSoumisIsi5(false);
        in.setMinimumPerceptionIrsa(new BigDecimal("3000.00"));
        in.setDeductionParEnfant(new BigDecimal("2000.00"));
        in.setNbEnfantsEligibles(0);
        in.setTranchesIrsa(TRANCHES_LF2026_ENGINE);
        return in;
    }

    private static PayrollAttendanceInput makeAttendance(long unpaidMinutes) {
        return new PayrollAttendanceInput(
                1, 1, 1001, 202601, java.time.Instant.now(), 10000L, 0L, 0L, 0L, 0L, 0L, 0L,
                unpaidMinutes, (int) Math.min(unpaidMinutes, Integer.MAX_VALUE),
                new BigDecimal("21"), new BigDecimal("21"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of()
        );
    }

    private static void assertMoneyEquals(BigDecimal expected, BigDecimal actual) {
        if (expected == null && actual == null) return;
        assertNotNull(expected, "Expected should not be null");
        assertNotNull(actual, "Actual should not be null");
        assertEquals(0, expected.compareTo(actual), "Expected: " + expected + " but was: " + actual);
    }

    private static mg.galaxam.sirh.paie.persistence.PaieAvanceAcompte makeAcompte(String type, BigDecimal montant) {
        mg.galaxam.sirh.paie.persistence.PaieAvanceAcompte a = new mg.galaxam.sirh.paie.persistence.PaieAvanceAcompte();
        a.setTypeMouvement(type);
        a.setMontant(montant);
        a.setStatut("APPROUVE");
        return a;
    }

    // =========================================================================
    // SECTION 2 : CONTRADICTION IRSA 42 960 vs 24 470 RÉSOUDRE
    // =========================================================================
    @Nested
    @DisplayName("2. Résolution Contradiction IRSA 42 960 vs 24 470")
    class IrsaContradictionResolutionTests {

        @Test
        @DisplayName("Preuve mathématique indépendante : Assiette 579 800 Ar -> IRSA = 24 470 Ar (REPORT_ERROR)")
        void testIrsaAssiette579800Proof() {
            BigDecimal assiette = new BigDecimal("579800.00");

            // Recalcul indépendant tranche par tranche :
            // T1 : [0 - 350 000] @ 0% = 0 Ar
            BigDecimal t1 = BigDecimal.ZERO;
            // T2 : [350 001 - 400 000] @ 5% = (400 000 - 350 000) * 0.05 = 2 500 Ar
            BigDecimal t2 = new BigDecimal("50000.00").multiply(new BigDecimal("0.05"));
            // T3 : [400 001 - 500 000] @ 10% = (500 000 - 400 000) * 0.10 = 10 000 Ar
            BigDecimal t3 = new BigDecimal("100000.00").multiply(new BigDecimal("0.10"));
            // T4 : [500 001 - 579 800] @ 15% = (579 800 - 500 000) * 0.15 = 11 970 Ar
            BigDecimal t4 = new BigDecimal("79800.00").multiply(new BigDecimal("0.15"));

            BigDecimal totalBrut = t1.add(t2).add(t3).add(t4).setScale(2, RoundingMode.HALF_UP);
            assertEquals(new BigDecimal("24470.00"), totalBrut);

            // Arrondi dizaine supérieure
            BigDecimal dizaine = totalBrut.divide(BigDecimal.TEN, 0, RoundingMode.CEILING).multiply(BigDecimal.TEN).setScale(2);
            assertEquals(new BigDecimal("24470.00"), dizaine);

            // Comparaison avec l'Oracle Indépendant
            OracleInput inOracle = new OracleInput(
                    new BigDecimal("625000.00"), false, null, 691, null, new BigDecimal("173.33"),
                    new BigDecimal("11.5"), new BigDecimal("19348.12"),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    new BigDecimal("25257.14"), new BigDecimal("300000.00"),
                    new BigDecimal("0.01"), new BigDecimal("0.13"),
                    new BigDecimal("0.01"), new BigDecimal("0.05"),
                    true, new BigDecimal("0.01"),
                    TRANCHES_LF2026_ORACLE, 0, new BigDecimal("2000.00"), new BigDecimal("3000.00"),
                    false, null, BigDecimal.ZERO, BigDecimal.ZERO
            );
            OracleResult resOracle = FinancialCertificationIndependentOracle.calculate(inOracle);
            assertEquals(new BigDecimal("579800.00"), resOracle.baseIrsaArrondie());
            assertEquals(new BigDecimal("24470.00"), resOracle.irsaNet());

            // Comparaison avec le moteur de production
            PayrollCalculationInput inEngine = buildBaseInput(new BigDecimal("625000.00"));
            inEngine.setAttendanceInput(makeAttendance(691));
            inEngine.setNbJoursConges(new BigDecimal("11.5"));
            inEngine.setTauxJournalierConge(new BigDecimal("19348.12"));
            inEngine.setAutimp(new BigDecimal("25257.14"));

            BulletinPaieComplet resEngine = engine.calculer(inEngine);
            assertEquals(new BigDecimal("579800.00"), resEngine.getBulletin().getBaseIrsaArrondie());
            assertEquals(new BigDecimal("24470.00"), resEngine.getBulletin().getIrsaNet());

            // DELTA STRICTEMENT NUL
            BigDecimal delta = resEngine.getBulletin().getIrsaNet().subtract(resOracle.irsaNet());
            assertEquals(0, delta.compareTo(BigDecimal.ZERO),
                    "Le delta entre l'oracle financier indépendant et le moteur est strictement 0.00 Ar. Le 42 960 était un REPORT_ERROR.");
        }
    }

    // =========================================================================
    // SECTION 6 : SALAIRE DE BASE & PRORATISATION
    // =========================================================================
    @Nested
    @DisplayName("6. Certification du Salaire de Base & Proratisation")
    class SalaireBaseCertificationTests {

        @Test
        @DisplayName("Mois complet : 30/30 sans prorata")
        void testMoisComplet() {
            BigDecimal base = new BigDecimal("1200000.00");
            PayrollCalculationInput in = buildBaseInput(base);
            BulletinPaieComplet complet = engine.calculer(in);

            assertEquals(new BigDecimal("1200000.00"), complet.getBulletin().getSalaireBase());
            assertEquals(new BigDecimal("1200000.00"), complet.getBulletin().getTotalGainsBruts());
        }

        @Test
        @DisplayName("Entrée en cours de mois : 15 jours sous contrat -> BASE * 15 / 30")
        void testEntreeEnCoursDeMois() {
            BigDecimal base = new BigDecimal("1200000.00");
            PayrollCalculationInput in = buildBaseInput(base);
            in.setEntreeSortieEnCoursDeMois(true);
            in.setJoursSousContrat(new BigDecimal("15"));

            OracleInput orIn = new OracleInput(
                    base, true, new BigDecimal("15"), 0, null, new BigDecimal("173.33"),
                    BigDecimal.ZERO, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, new BigDecimal("300000.00"),
                    new BigDecimal("0.01"), new BigDecimal("0.13"), new BigDecimal("0.01"), new BigDecimal("0.05"),
                    true, new BigDecimal("0.01"), TRANCHES_LF2026_ORACLE, 0, new BigDecimal("2000.00"), new BigDecimal("3000.00"),
                    false, null, BigDecimal.ZERO, BigDecimal.ZERO
            );
            OracleResult expected = FinancialCertificationIndependentOracle.calculate(orIn);
            assertEquals(new BigDecimal("600000.00"), expected.salaireBaseLigne());

            BulletinPaieComplet actual = engine.calculer(in);
            assertEquals(expected.salaireBaseLigne(), actual.getBulletin().getTotalGainsBruts());
        }

        @Test
        @DisplayName("Sortie en cours de mois : 10 jours sous contrat -> BASE * 10 / 30")
        void testSortieEnCoursDeMois() {
            BigDecimal base = new BigDecimal("900000.00");
            PayrollCalculationInput in = buildBaseInput(base);
            in.setEntreeSortieEnCoursDeMois(true);
            in.setJoursSousContrat(new BigDecimal("10"));

            BulletinPaieComplet actual = engine.calculer(in);
            assertEquals(new BigDecimal("300000.00"), actual.getBulletin().getTotalGainsBruts());
        }

        @Test
        @DisplayName("Entrée + sortie en cours de mois : 5 jours sous contrat -> BASE * 5 / 30")
        void testEntreeEtSortieMemeMois() {
            BigDecimal base = new BigDecimal("600000.00");
            PayrollCalculationInput in = buildBaseInput(base);
            in.setEntreeSortieEnCoursDeMois(true);
            in.setJoursSousContrat(new BigDecimal("5"));

            BulletinPaieComplet actual = engine.calculer(in);
            assertEquals(new BigDecimal("100000.00"), actual.getBulletin().getTotalGainsBruts());
        }
    }

    // =========================================================================
    // SECTION 7 : ABSENCES & VALORISATION HORAIRE
    // =========================================================================
    @Nested
    @DisplayName("7. Certification des Absences (Minutes canoniques & Diviseur 173.33)")
    class AbsenceCertificationTests {
        private final BigDecimal base = new BigDecimal("625000.00");
        private final BigDecimal diviseur = new BigDecimal("173.33");

        @Test
        @DisplayName("Paliers d'absence : 0m, 1m, 30m, 60m, 7h36 (456m), 11.52h (691m), 1j (480m), 21j (10080m)")
        void testPaliersAbsence() {
            long[] testMinutes = {0, 1, 30, 60, 456, 691, 480, 10080};

            for (long min : testMinutes) {
                OracleInput orIn = new OracleInput(
                        base, false, null, min, null, diviseur,
                        BigDecimal.ZERO, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, new BigDecimal("300000.00"),
                        new BigDecimal("0.01"), new BigDecimal("0.13"), new BigDecimal("0.01"), new BigDecimal("0.05"),
                        true, new BigDecimal("0.01"), TRANCHES_LF2026_ORACLE, 0, new BigDecimal("2000.00"), new BigDecimal("3000.00"),
                        false, null, BigDecimal.ZERO, BigDecimal.ZERO
                );
                OracleResult expected = FinancialCertificationIndependentOracle.calculate(orIn);

                PayrollCalculationInput in = buildBaseInput(base);
                in.setAttendanceInput(makeAttendance(min));

                BulletinPaieComplet actual = engine.calculer(in);
                BigDecimal actualAbs = actual.getMontantLigneSalariale("RETENUE_ABSENCE");

                assertMoneyEquals(expected.retenueAbsence(), actualAbs);
                assertMoneyEquals(expected.totalGainsBruts(), actual.getBulletin().getTotalGainsBruts());
            }
        }
    }

    // =========================================================================
    // SECTION 8 : CONGÉ PAYÉ & INTÉGRITÉ DES PALIERS
    // =========================================================================
    @Nested
    @DisplayName("8. Certification du Congé Payé (Déduction Trentième & TCONGE)")
    class CongePayeCertificationTests {
        private final BigDecimal base = new BigDecimal("600000.00");
        private final BigDecimal tconge = new BigDecimal("18500.00");

        @Test
        @DisplayName("Graduation des jours : 0j, 0.5j, 1j, 5j, 11.5j, 15j, 30j")
        void testGraduationConges() {
            BigDecimal[] paliers = {
                    BigDecimal.ZERO, new BigDecimal("0.5"), new BigDecimal("1.0"),
                    new BigDecimal("5.0"), new BigDecimal("11.5"), new BigDecimal("15.0"), new BigDecimal("30.0")
            };

            for (BigDecimal j : paliers) {
                OracleInput orIn = new OracleInput(
                        base, false, null, 0, null, new BigDecimal("173.33"),
                        j, tconge, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, new BigDecimal("300000.00"),
                        new BigDecimal("0.01"), new BigDecimal("0.13"), new BigDecimal("0.01"), new BigDecimal("0.05"),
                        true, new BigDecimal("0.01"), TRANCHES_LF2026_ORACLE, 0, new BigDecimal("2000.00"), new BigDecimal("3000.00"),
                        false, null, BigDecimal.ZERO, BigDecimal.ZERO
                );
                OracleResult expected = FinancialCertificationIndependentOracle.calculate(orIn);

                PayrollCalculationInput in = buildBaseInput(base);
                in.setNbJoursConges(j);
                in.setTauxJournalierConge(tconge);

                BulletinPaieComplet actual = engine.calculer(in);

                assertMoneyEquals(expected.deductionCongeTrentieme(), actual.getMontantLigneSalariale("DEDUCTION_CONGE_BASE"));
                assertMoneyEquals(expected.indemniteConge(), actual.getMontantLigneSalariale("INDEMNITE_CONGE_PAYE"));
                assertMoneyEquals(expected.totalGainsBruts(), actual.getBulletin().getTotalGainsBruts());
            }
        }
    }

    // =========================================================================
    // SECTION 9 : HEURES SUPPLÉMENTAIRES & MAJORATIONS
    // =========================================================================
    @Nested
    @DisplayName("9. Certification des Heures Supplémentaires & Majorations Spéciales")
    class OvertimeCertificationTests {
        private final BigDecimal base = new BigDecimal("400000.00");

        @Test
        @DisplayName("Branches indépendantes : HS 30%, HS 50%, Nuit, Dimanche, Férié")
        void testBranchesHeuresSup() {
            // Taux horaire = 400 000 / 173.33 = 2 307.7367 Ar/h
            PayrollCalculationInput in = buildBaseInput(base);
            in.setHeuresHs30(new BigDecimal("10.0"));
            in.setHeuresHs50(new BigDecimal("4.0"));
            in.setHeuresNuit(new BigDecimal("8.0"));
            in.setHeuresDimanche(new BigDecimal("5.0"));
            in.setHeuresFeriees(new BigDecimal("8.0"));

            OracleInput orIn = new OracleInput(
                    base, false, null, 0, null, new BigDecimal("173.33"),
                    BigDecimal.ZERO, null,
                    new BigDecimal("10.0"), new BigDecimal("4.0"), new BigDecimal("8.0"), new BigDecimal("5.0"), new BigDecimal("8.0"),
                    BigDecimal.ZERO, new BigDecimal("300000.00"),
                    new BigDecimal("0.01"), new BigDecimal("0.13"), new BigDecimal("0.01"), new BigDecimal("0.05"),
                    true, new BigDecimal("0.01"), TRANCHES_LF2026_ORACLE, 0, new BigDecimal("2000.00"), new BigDecimal("3000.00"),
                    false, null, BigDecimal.ZERO, BigDecimal.ZERO
            );
            OracleResult expected = FinancialCertificationIndependentOracle.calculate(orIn);

            BulletinPaieComplet actual = engine.calculer(in);

            assertEquals(expected.montantHs30(), actual.getMontantLigneSalariale("HEURES_SUP_30"));
            assertEquals(expected.montantHs50(), actual.getMontantLigneSalariale("HEURES_SUP_50"));
            assertEquals(expected.montantNuit(), actual.getMontantLigneSalariale("MAJORATION_NUIT"));
            assertEquals(expected.montantDimanche(), actual.getMontantLigneSalariale("HEURES_DIMANCHE"));
            assertEquals(expected.montantFerie(), actual.getMontantLigneSalariale("MAJORATION_FERIE"));
            assertEquals(expected.totalGainsBruts(), actual.getBulletin().getTotalGainsBruts());
        }
    }

    // =========================================================================
    // SECTION 11 & 12 : COTISATIONS SOCIALES (CNaPS, SANTÉ OSTIE/OMSI, FMFP)
    // =========================================================================
    @Nested
    @DisplayName("11 & 12. Certification des Cotisations Sociales")
    class SocialContributionsCertificationTests {

        @Test
        @DisplayName("Frontières Plafond CNaPS (8x SME 300 000 = 2 400 000 Ar)")
        void testFrontieresPlafondCnaps() {
            BigDecimal[] bases = {
                    BigDecimal.ZERO,
                    new BigDecimal("500000.00"),
                    new BigDecimal("2399999.00"),
                    new BigDecimal("2400000.00"),
                    new BigDecimal("2400000.00"),
                    new BigDecimal("5000000.00")
            };

            for (BigDecimal b : bases) {
                OracleInput orIn = new OracleInput(
                        b, false, null, 0, null, new BigDecimal("173.33"),
                        BigDecimal.ZERO, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, new BigDecimal("300000.00"),
                        new BigDecimal("0.01"), new BigDecimal("0.13"), new BigDecimal("0.01"), new BigDecimal("0.05"),
                        true, new BigDecimal("0.01"), TRANCHES_LF2026_ORACLE, 0, new BigDecimal("2000.00"), new BigDecimal("3000.00"),
                        false, null, BigDecimal.ZERO, BigDecimal.ZERO
                );
                OracleResult expected = FinancialCertificationIndependentOracle.calculate(orIn);

                PayrollCalculationInput in = buildBaseInput(b);
                BulletinPaieComplet actual = engine.calculer(in);

                assertEquals(expected.cnapsSalariale(), actual.getMontantLigneSalariale("COTISATION_CNAPS_SAL"));
                assertEquals(expected.cnapsPatronale(), actual.getMontantLignePatronale("COTISATION_CNAPS_PAT"));
                assertEquals(expected.fmfpPatronale(), actual.getMontantLignePatronale("COTISATION_FMFP_PAT"));
            }
        }

        @Test
        @DisplayName("Santé : OSTIE TNR (1% sal / 5% pat) vs OMSI TMM (1.5% sal / 5.5% pat)")
        void testSanteOstieVsOmsi() {
            BigDecimal base = new BigDecimal("1000000.00");

            // Cas 1 : OSTIE
            PayrollCalculationInput inOstie = buildBaseInput(base);
            inOstie.setCodeSanteOrgane("OSTIE");
            inOstie.setTauxSanteSalarial(new BigDecimal("0.01"));
            inOstie.setTauxSantePatronal(new BigDecimal("0.05"));
            BulletinPaieComplet actOstie = engine.calculer(inOstie);
            assertMoneyEquals(new BigDecimal("10000.00"), actOstie.getMontantLigneSalariale("COTISATION_OSTIE_SAL"));
            assertMoneyEquals(new BigDecimal("50000.00"), actOstie.getMontantLignePatronale("COTISATION_OSTIE_PAT"));

            // Cas 2 : OMSI
            PayrollCalculationInput inOmsi = buildBaseInput(base);
            inOmsi.setCodeSanteOrgane("OMSI");
            inOmsi.setTauxSanteSalarial(new BigDecimal("0.015"));
            inOmsi.setTauxSantePatronal(new BigDecimal("0.055"));
            BulletinPaieComplet actOmsi = engine.calculer(inOmsi);
            assertMoneyEquals(new BigDecimal("15000.00"), actOmsi.getMontantLigneSalariale("COTISATION_OMSI_SAL"));
            assertMoneyEquals(new BigDecimal("55000.00"), actOmsi.getMontantLignePatronale("COTISATION_OMSI_PAT"));
        }
    }

    // =========================================================================
    // SECTION 14 : IRSA — CERTIFICATION DU BARÈME PROGRESSIF ET RÉDUCTIONS
    // =========================================================================
    @Nested
    @DisplayName("14. Certification Prioritaire IRSA (Seuils, Troncature, Réductions, Minimum)")
    class IrsaCertificationTests {

        @Test
        @DisplayName("Frontières de tranches et seuil minimum 3 000 Ar (LF 2026)")
        void testFrontieresTranchesIrsa() {
            BigDecimal[] assiettes = {
                    new BigDecimal("300000.00"), // < 350k -> min 3 000 Ar
                    new BigDecimal("350000.00"), // Seuil exact T1 -> min 3 000 Ar
                    new BigDecimal("350100.00"), // 100 Ar en T2 -> brut 5 Ar -> min 3 000 Ar
                    new BigDecimal("400000.00"), // Max T2 = 2 500 Ar -> min 3 000 Ar
                    new BigDecimal("500000.00"), // Max T3 = 2 500 + 10 000 = 12 500 Ar
                    new BigDecimal("600000.00"), // Max T4 = 12 500 + 15 000 = 27 500 Ar
                    new BigDecimal("1000000.00") // 27 500 + 400 000 * 20% = 107 500 Ar
            };

            for (BigDecimal ass : assiettes) {
                PayrollCalculationInput in = buildBaseInput(ass.divide(new BigDecimal("0.98"), 2, RoundingMode.HALF_UP)); // Brut calculé pour donner ass neti
                BulletinPaieComplet actual = engine.calculer(in);

                OracleInput orIn = new OracleInput(
                        in.getSalaireBaseContractuel(), false, null, 0, null, new BigDecimal("173.33"),
                        BigDecimal.ZERO, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, new BigDecimal("300000.00"),
                        new BigDecimal("0.01"), new BigDecimal("0.13"), new BigDecimal("0.01"), new BigDecimal("0.05"),
                        true, new BigDecimal("0.01"), TRANCHES_LF2026_ORACLE, 0, new BigDecimal("2000.00"), new BigDecimal("3000.00"),
                        false, null, BigDecimal.ZERO, BigDecimal.ZERO
                );
                OracleResult expected = FinancialCertificationIndependentOracle.calculate(orIn);

                assertEquals(expected.irsaNet(), actual.getBulletin().getIrsaNet());
            }
        }

        @Test
        @DisplayName("Réduction pour charges de famille (2 000 Ar / enfant)")
        void testReductionEnfants() {
            BigDecimal base = new BigDecimal("1000000.00");

            for (int nbEnf = 0; nbEnf <= 5; nbEnf++) {
                PayrollCalculationInput in = buildBaseInput(base);
                in.setNbEnfantsEligibles(nbEnf);
                BulletinPaieComplet actual = engine.calculer(in);

                OracleInput orIn = new OracleInput(
                        base, false, null, 0, null, new BigDecimal("173.33"),
                        BigDecimal.ZERO, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, new BigDecimal("300000.00"),
                        new BigDecimal("0.01"), new BigDecimal("0.13"), new BigDecimal("0.01"), new BigDecimal("0.05"),
                        true, new BigDecimal("0.01"), TRANCHES_LF2026_ORACLE, nbEnf, new BigDecimal("2000.00"), new BigDecimal("3000.00"),
                        false, null, BigDecimal.ZERO, BigDecimal.ZERO
                );
                OracleResult expected = FinancialCertificationIndependentOracle.calculate(orIn);

                assertMoneyEquals(expected.reductionEnfants(), actual.getBulletin().getReductionEnfants());
                assertMoneyEquals(expected.irsaNet(), actual.getBulletin().getIrsaNet());
            }
        }
    }

    // =========================================================================
    // SECTION 15 : ISI (RÉGIME PRESTATAIRE / CONSULTANT)
    // =========================================================================
    @Nested
    @DisplayName("15. Certification ISI (5% Libératoire & Exclusion Mutuelle IRSA)")
    class IsiCertificationTests {

        @Test
        @DisplayName("Consultant ISI : 5% sur brut, 0 IRSA, 0 Cotisations salariales")
        void testIsiRegime() {
            BigDecimal brut = new BigDecimal("2000000.00");
            PayrollCalculationInput in = buildBaseInput(brut);
            in.setSoumisIrsa(false);
            in.setSoumisIsi5(true);
            in.setTauxIsi(new BigDecimal("5.00"));
            in.setSoumisCnaps(false);
            in.setSoumisSante(false);

            BulletinPaieComplet actual = engine.calculer(in);

            assertEquals(new BigDecimal("100000.00"), actual.getBulletin().getRetenueIsi5());
            assertEquals(BigDecimal.ZERO.setScale(2), actual.getBulletin().getIrsaNet());
            assertEquals(new BigDecimal("1900000.00"), actual.getBulletin().getNetAPayer());
        }
    }

    // =========================================================================
    // SECTION 22, 23 : GOLDEN MASTERS RAKETAKA, RASOA, RAKOTO
    // =========================================================================
    @Nested
    @DisplayName("22 & 23. Golden Masters Réels GALAXAM (Réconciliation Complète)")
    class GoldenMastersCertificationTests {

        @Test
        @DisplayName("GM-1 : RAKETAKA (TNR) — Réconciliation complète de zéro avec l'Oracle")
        void testGoldenMasterRaketakaDeZero() {
            BigDecimal base = new BigDecimal("625000.00");
            BigDecimal autimp = new BigDecimal("25257.14");
            BigDecimal tconge = new BigDecimal("19348.12");
            BigDecimal nbConge = new BigDecimal("11.5");
            long minutesAbs = 691; // 11.52 h

            OracleInput orIn = new OracleInput(
                    base, false, null, minutesAbs, null, new BigDecimal("173.33"),
                    nbConge, tconge,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    autimp, new BigDecimal("300000.00"),
                    new BigDecimal("0.01"), new BigDecimal("0.13"),
                    new BigDecimal("0.01"), new BigDecimal("0.05"),
                    true, new BigDecimal("0.01"),
                    TRANCHES_LF2026_ORACLE, 0, new BigDecimal("2000.00"), new BigDecimal("3000.00"),
                    false, null, BigDecimal.ZERO, BigDecimal.ZERO
            );
            OracleResult expected = FinancialCertificationIndependentOracle.calculate(orIn);

            PayrollCalculationInput in = buildBaseInput(base);
            in.setAttendanceInput(makeAttendance(minutesAbs));
            in.setNbJoursConges(nbConge);
            in.setTauxJournalierConge(tconge);
            in.setAutimp(autimp);

            BulletinPaieComplet actual = engine.calculer(in);
            BulletinPaie bp = actual.getBulletin();

            // Vérification ligne à ligne contre l'Oracle
            assertEquals(expected.salaireBaseLigne(), bp.getSalaireBase());
            assertEquals(expected.retenueAbsence(), actual.getMontantLigneSalariale("RETENUE_ABSENCE"));
            assertEquals(expected.deductionCongeTrentieme(), actual.getMontantLigneSalariale("DEDUCTION_CONGE_BASE"));
            assertEquals(expected.indemniteConge(), actual.getMontantLigneSalariale("INDEMNITE_CONGE_PAYE"));
            assertEquals(expected.totalGainsBruts(), bp.getTotalGainsBruts());
            assertEquals(expected.cnapsSalariale(), actual.getMontantLigneSalariale("COTISATION_CNAPS_SAL"));
            assertEquals(expected.santeSalariale(), actual.getMontantLigneSalariale("COTISATION_OSTIE_SAL"));
            assertEquals(expected.salaireNetImposable(), bp.getSalaireNetImposable());
            assertEquals(expected.baseIrsaArrondie(), bp.getBaseIrsaArrondie());
            assertEquals(expected.irsaNet(), bp.getIrsaNet());
            assertEquals(expected.netAvantArrondi(), bp.getNetAvantArrondi());
            assertEquals(expected.netAPayer(), bp.getNetAPayer());

            // DELTA FINANCIER GLOBAL = 0
            assertEquals(0, bp.getNetAPayer().subtract(expected.netAPayer()).compareTo(BigDecimal.ZERO));
        }

        @Test
        @DisplayName("GM-2 : RASOA (EMP 3 TNR) — Admin RH, 1 Enfant, OSTIE")
        void testGoldenMasterRasoa() {
            BigDecimal base = new BigDecimal("1350000.00");
            int nbEnfants = 1;

            OracleInput orIn = new OracleInput(
                    base, false, null, 0, null, new BigDecimal("173.33"),
                    BigDecimal.ZERO, null,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, new BigDecimal("300000.00"),
                    new BigDecimal("0.01"), new BigDecimal("0.13"),
                    new BigDecimal("0.01"), new BigDecimal("0.05"),
                    true, new BigDecimal("0.01"),
                    TRANCHES_LF2026_ORACLE, nbEnfants, new BigDecimal("2000.00"), new BigDecimal("3000.00"),
                    false, null, BigDecimal.ZERO, BigDecimal.ZERO
            );
            OracleResult expected = FinancialCertificationIndependentOracle.calculate(orIn);

            PayrollCalculationInput in = buildBaseInput(base);
            in.setNbEnfantsEligibles(nbEnfants);

            BulletinPaieComplet actual = engine.calculer(in);
            BulletinPaie bp = actual.getBulletin();

            assertMoneyEquals(expected.totalGainsBruts(), bp.getTotalGainsBruts());
            assertMoneyEquals(expected.totalCotisationsSalariales(), bp.getTotalCotisationsSalariales());
            assertMoneyEquals(expected.salaireNetImposable(), bp.getSalaireNetImposable());
            assertMoneyEquals(expected.baseIrsaArrondie(), bp.getBaseIrsaArrondie());
            assertMoneyEquals(expected.reductionEnfants(), bp.getReductionEnfants());
            assertMoneyEquals(expected.irsaNet(), bp.getIrsaNet());
            assertMoneyEquals(expected.netAPayer(), bp.getNetAPayer());
            assertEquals(0, bp.getNetAPayer().subtract(expected.netAPayer()).compareTo(BigDecimal.ZERO));
        }

        @Test
        @DisplayName("GM-3 : RAKOTO (EMP 2 TMM) — Logistique, 1 Enfant, OMSI 1.5%, Heures Sup")
        void testGoldenMasterRakoto() {
            BigDecimal base = new BigDecimal("1850000.00");
            int nbEnfants = 1;
            BigDecimal h30 = new BigDecimal("10.0");
            BigDecimal h50 = new BigDecimal("4.0");

            OracleInput orIn = new OracleInput(
                    base, false, null, 0, null, new BigDecimal("173.33"),
                    BigDecimal.ZERO, null,
                    h30, h50, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, new BigDecimal("300000.00"),
                    new BigDecimal("0.01"), new BigDecimal("0.13"),
                    new BigDecimal("0.015"), new BigDecimal("0.055"),
                    true, new BigDecimal("0.01"),
                    TRANCHES_LF2026_ORACLE, nbEnfants, new BigDecimal("2000.00"), new BigDecimal("3000.00"),
                    false, null, new BigDecimal("53200.00"), BigDecimal.ZERO
            );
            OracleResult expected = FinancialCertificationIndependentOracle.calculate(orIn);

            PayrollCalculationInput in = buildBaseInput(base);
            in.setCodeSanteOrgane("OMSI");
            in.setTauxSanteSalarial(new BigDecimal("0.015"));
            in.setTauxSantePatronal(new BigDecimal("0.055"));
            in.setNbEnfantsEligibles(nbEnfants);
            in.setHeuresHs30(h30);
            in.setHeuresHs50(h50);
            in.setIndemniteTransport(new BigDecimal("53200.00"));

            BulletinPaieComplet actual = engine.calculer(in);
            BulletinPaie bp = actual.getBulletin();

            assertMoneyEquals(expected.totalGainsBruts(), bp.getTotalGainsBruts());
            assertMoneyEquals(expected.totalCotisationsSalariales(), bp.getTotalCotisationsSalariales());
            assertMoneyEquals(expected.salaireNetImposable(), bp.getSalaireNetImposable());
            assertMoneyEquals(expected.irsaNet(), bp.getIrsaNet());
            assertMoneyEquals(expected.netAPayer(), bp.getNetAPayer());
            assertEquals(0, bp.getNetAPayer().subtract(expected.netAPayer()).compareTo(BigDecimal.ZERO));
        }
    }

    // =========================================================================
    // SECTION 24 : CAS SYNTHÉTIQUES PERSONAS A À L
    // =========================================================================
    @Nested
    @DisplayName("24. Certification des Personas Synthétiques A à L")
    class PersonasSynthetiquesCertificationTests {

        @Test
        @DisplayName("Persona A : Parfaitement présent")
        void testPersonaA() {
            BigDecimal base = new BigDecimal("800000.00");
            OracleResult exp = FinancialCertificationIndependentOracle.calculate(new OracleInput(
                    base, false, null, 0, null, new BigDecimal("173.33"),
                    BigDecimal.ZERO, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, new BigDecimal("300000.00"),
                    new BigDecimal("0.01"), new BigDecimal("0.13"), new BigDecimal("0.01"), new BigDecimal("0.05"),
                    true, new BigDecimal("0.01"), TRANCHES_LF2026_ORACLE, 0, new BigDecimal("2000.00"), new BigDecimal("3000.00"),
                    false, null, BigDecimal.ZERO, BigDecimal.ZERO
            ));
            BulletinPaieComplet act = engine.calculer(buildBaseInput(base));
            assertEquals(exp.netAPayer(), act.getBulletin().getNetAPayer());
        }

        @Test
        @DisplayName("Persona B : Avec retard / absence 3h (180 min)")
        void testPersonaB() {
            BigDecimal base = new BigDecimal("800000.00");
            OracleResult exp = FinancialCertificationIndependentOracle.calculate(new OracleInput(
                    base, false, null, 180, null, new BigDecimal("173.33"),
                    BigDecimal.ZERO, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, new BigDecimal("300000.00"),
                    new BigDecimal("0.01"), new BigDecimal("0.13"), new BigDecimal("0.01"), new BigDecimal("0.05"),
                    true, new BigDecimal("0.01"), TRANCHES_LF2026_ORACLE, 0, new BigDecimal("2000.00"), new BigDecimal("3000.00"),
                    false, null, BigDecimal.ZERO, BigDecimal.ZERO
            ));
            PayrollCalculationInput in = buildBaseInput(base);
            in.setAttendanceInput(makeAttendance(180));
            BulletinPaieComplet act = engine.calculer(in);
            assertEquals(exp.netAPayer(), act.getBulletin().getNetAPayer());
        }

        @Test
        @DisplayName("Persona C : Avec congé 3j")
        void testPersonaC() {
            BigDecimal base = new BigDecimal("800000.00");
            BigDecimal tconge = new BigDecimal("25000.00");
            OracleResult exp = FinancialCertificationIndependentOracle.calculate(new OracleInput(
                    base, false, null, 0, null, new BigDecimal("173.33"),
                    new BigDecimal("3.0"), tconge, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, new BigDecimal("300000.00"),
                    new BigDecimal("0.01"), new BigDecimal("0.13"), new BigDecimal("0.01"), new BigDecimal("0.05"),
                    true, new BigDecimal("0.01"), TRANCHES_LF2026_ORACLE, 0, new BigDecimal("2000.00"), new BigDecimal("3000.00"),
                    false, null, BigDecimal.ZERO, BigDecimal.ZERO
            ));
            PayrollCalculationInput in = buildBaseInput(base);
            in.setNbJoursConges(new BigDecimal("3.0"));
            in.setTauxJournalierConge(tconge);
            BulletinPaieComplet act = engine.calculer(in);
            assertEquals(exp.netAPayer(), act.getBulletin().getNetAPayer());
        }

        @Test
        @DisplayName("Persona D : Congé + absence indépendante")
        void testPersonaD() {
            BigDecimal base = new BigDecimal("800000.00");
            BigDecimal tconge = new BigDecimal("25000.00");
            OracleResult exp = FinancialCertificationIndependentOracle.calculate(new OracleInput(
                    base, false, null, 120, null, new BigDecimal("173.33"),
                    new BigDecimal("2.0"), tconge, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, new BigDecimal("300000.00"),
                    new BigDecimal("0.01"), new BigDecimal("0.13"), new BigDecimal("0.01"), new BigDecimal("0.05"),
                    true, new BigDecimal("0.01"), TRANCHES_LF2026_ORACLE, 0, new BigDecimal("2000.00"), new BigDecimal("3000.00"),
                    false, null, BigDecimal.ZERO, BigDecimal.ZERO
            ));
            PayrollCalculationInput in = buildBaseInput(base);
            in.setAttendanceInput(makeAttendance(120));
            in.setNbJoursConges(new BigDecimal("2.0"));
            in.setTauxJournalierConge(tconge);
            BulletinPaieComplet act = engine.calculer(in);
            assertEquals(exp.netAPayer(), act.getBulletin().getNetAPayer());
        }

        @Test
        @DisplayName("Persona E : Avec HS 30% (5h)")
        void testPersonaE() {
            BigDecimal base = new BigDecimal("800000.00");
            OracleResult exp = FinancialCertificationIndependentOracle.calculate(new OracleInput(
                    base, false, null, 0, null, new BigDecimal("173.33"),
                    BigDecimal.ZERO, null, new BigDecimal("5.0"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, new BigDecimal("300000.00"),
                    new BigDecimal("0.01"), new BigDecimal("0.13"), new BigDecimal("0.01"), new BigDecimal("0.05"),
                    true, new BigDecimal("0.01"), TRANCHES_LF2026_ORACLE, 0, new BigDecimal("2000.00"), new BigDecimal("3000.00"),
                    false, null, BigDecimal.ZERO, BigDecimal.ZERO
            ));
            PayrollCalculationInput in = buildBaseInput(base);
            in.setHeuresHs30(new BigDecimal("5.0"));
            BulletinPaieComplet act = engine.calculer(in);
            assertEquals(exp.netAPayer(), act.getBulletin().getNetAPayer());
        }

        @Test
        @DisplayName("Persona F : Nuit (4h) + Dimanche (7h) + Férié (8h)")
        void testPersonaF() {
            BigDecimal base = new BigDecimal("800000.00");
            OracleResult exp = FinancialCertificationIndependentOracle.calculate(new OracleInput(
                    base, false, null, 0, null, new BigDecimal("173.33"),
                    BigDecimal.ZERO, null, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("4.0"), new BigDecimal("7.0"), new BigDecimal("8.0"),
                    BigDecimal.ZERO, new BigDecimal("300000.00"),
                    new BigDecimal("0.01"), new BigDecimal("0.13"), new BigDecimal("0.01"), new BigDecimal("0.05"),
                    true, new BigDecimal("0.01"), TRANCHES_LF2026_ORACLE, 0, new BigDecimal("2000.00"), new BigDecimal("3000.00"),
                    false, null, BigDecimal.ZERO, BigDecimal.ZERO
            ));
            PayrollCalculationInput in = buildBaseInput(base);
            in.setHeuresNuit(new BigDecimal("4.0"));
            in.setHeuresDimanche(new BigDecimal("7.0"));
            in.setHeuresFeriees(new BigDecimal("8.0"));
            BulletinPaieComplet act = engine.calculer(in);
            assertEquals(exp.netAPayer(), act.getBulletin().getNetAPayer());
        }

        @Test
        @DisplayName("Persona G : Primes imposables AUTIMP 50 000 Ar")
        void testPersonaG() {
            BigDecimal base = new BigDecimal("800000.00");
            OracleResult exp = FinancialCertificationIndependentOracle.calculate(new OracleInput(
                    base, false, null, 0, null, new BigDecimal("173.33"),
                    BigDecimal.ZERO, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    new BigDecimal("50000.00"), new BigDecimal("300000.00"),
                    new BigDecimal("0.01"), new BigDecimal("0.13"), new BigDecimal("0.01"), new BigDecimal("0.05"),
                    true, new BigDecimal("0.01"), TRANCHES_LF2026_ORACLE, 0, new BigDecimal("2000.00"), new BigDecimal("3000.00"),
                    false, null, BigDecimal.ZERO, BigDecimal.ZERO
            ));
            PayrollCalculationInput in = buildBaseInput(base);
            in.setAutimp(new BigDecimal("50000.00"));
            BulletinPaieComplet act = engine.calculer(in);
            assertEquals(exp.netAPayer(), act.getBulletin().getNetAPayer());
        }

        @Test
        @DisplayName("Persona H : Avance sur salaire 100 000 Ar")
        void testPersonaH() {
            BigDecimal base = new BigDecimal("800000.00");
            OracleResult exp = FinancialCertificationIndependentOracle.calculate(new OracleInput(
                    base, false, null, 0, null, new BigDecimal("173.33"),
                    BigDecimal.ZERO, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, new BigDecimal("300000.00"),
                    new BigDecimal("0.01"), new BigDecimal("0.13"), new BigDecimal("0.01"), new BigDecimal("0.05"),
                    true, new BigDecimal("0.01"), TRANCHES_LF2026_ORACLE, 0, new BigDecimal("2000.00"), new BigDecimal("3000.00"),
                    false, null, BigDecimal.ZERO, new BigDecimal("100000.00")
            ));
            PayrollCalculationInput in = buildBaseInput(base);
            in.setAvancesAcomptes(List.of(makeAcompte("ACOMPTE_15", new BigDecimal("100000.00"))));
            BulletinPaieComplet act = engine.calculer(in);
            assertEquals(exp.netAPayer(), act.getBulletin().getNetAPayer());
        }

        @Test
        @DisplayName("Persona I : Au-dessus du plafond CNaPS (Salaire 3 500 000 Ar)")
        void testPersonaI() {
            BigDecimal base = new BigDecimal("3500000.00");
            OracleResult exp = FinancialCertificationIndependentOracle.calculate(new OracleInput(
                    base, false, null, 0, null, new BigDecimal("173.33"),
                    BigDecimal.ZERO, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, new BigDecimal("300000.00"),
                    new BigDecimal("0.01"), new BigDecimal("0.13"), new BigDecimal("0.01"), new BigDecimal("0.05"),
                    true, new BigDecimal("0.01"), TRANCHES_LF2026_ORACLE, 0, new BigDecimal("2000.00"), new BigDecimal("3000.00"),
                    false, null, BigDecimal.ZERO, BigDecimal.ZERO
            ));
            PayrollCalculationInput in = buildBaseInput(base);
            BulletinPaieComplet act = engine.calculer(in);
            assertEquals(exp.netAPayer(), act.getBulletin().getNetAPayer());
            assertEquals(new BigDecimal("24000.00"), act.getMontantLigneSalariale("COTISATION_CNAPS_SAL"));
        }

        @Test
        @DisplayName("Persona J : Avec 3 enfants à charge")
        void testPersonaJ() {
            BigDecimal base = new BigDecimal("800000.00");
            OracleResult exp = FinancialCertificationIndependentOracle.calculate(new OracleInput(
                    base, false, null, 0, null, new BigDecimal("173.33"),
                    BigDecimal.ZERO, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, new BigDecimal("300000.00"),
                    new BigDecimal("0.01"), new BigDecimal("0.13"), new BigDecimal("0.01"), new BigDecimal("0.05"),
                    true, new BigDecimal("0.01"), TRANCHES_LF2026_ORACLE, 3, new BigDecimal("2000.00"), new BigDecimal("3000.00"),
                    false, null, BigDecimal.ZERO, BigDecimal.ZERO
            ));
            PayrollCalculationInput in = buildBaseInput(base);
            in.setNbEnfantsEligibles(3);
            BulletinPaieComplet act = engine.calculer(in);
            assertEquals(exp.reductionEnfants(), act.getBulletin().getReductionEnfants());
            assertEquals(exp.netAPayer(), act.getBulletin().getNetAPayer());
        }

        @Test
        @DisplayName("Persona K : Consultant sous régime ISI 5%")
        void testPersonaK() {
            BigDecimal honoraires = new BigDecimal("1500000.00");
            OracleResult exp = FinancialCertificationIndependentOracle.calculate(new OracleInput(
                    honoraires, false, null, 0, null, new BigDecimal("173.33"),
                    BigDecimal.ZERO, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, new BigDecimal("300000.00"),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    false, BigDecimal.ZERO, TRANCHES_LF2026_ORACLE, 0, BigDecimal.ZERO, BigDecimal.ZERO,
                    true, new BigDecimal("5.00"), BigDecimal.ZERO, BigDecimal.ZERO
            ));
            PayrollCalculationInput in = buildBaseInput(honoraires);
            in.setSoumisIrsa(false);
            in.setSoumisIsi5(true);
            in.setTauxIsi(new BigDecimal("5.00"));
            in.setSoumisCnaps(false);
            in.setSoumisSante(false);
            BulletinPaieComplet act = engine.calculer(in);
            assertEquals(exp.netAPayer(), act.getBulletin().getNetAPayer());
            assertEquals(new BigDecimal("75000.00"), act.getBulletin().getRetenueIsi5());
        }

        @Test
        @DisplayName("Persona L : Entrée en cours de mois (18 jours sous contrat)")
        void testPersonaL() {
            BigDecimal base = new BigDecimal("1000000.00");
            OracleResult exp = FinancialCertificationIndependentOracle.calculate(new OracleInput(
                    base, true, new BigDecimal("18"), 0, null, new BigDecimal("173.33"),
                    BigDecimal.ZERO, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, new BigDecimal("300000.00"),
                    new BigDecimal("0.01"), new BigDecimal("0.13"), new BigDecimal("0.01"), new BigDecimal("0.05"),
                    true, new BigDecimal("0.01"), TRANCHES_LF2026_ORACLE, 0, new BigDecimal("2000.00"), new BigDecimal("3000.00"),
                    false, null, BigDecimal.ZERO, BigDecimal.ZERO
            ));
            PayrollCalculationInput in = buildBaseInput(base);
            in.setEntreeSortieEnCoursDeMois(true);
            in.setJoursSousContrat(new BigDecimal("18"));
            BulletinPaieComplet act = engine.calculer(in);
            assertEquals(exp.netAPayer(), act.getBulletin().getNetAPayer());
        }
    }

    // =========================================================================
    // SECTION 25 & 30 : INVARIANTS & IDEMPOTENCE
    // =========================================================================
    @Nested
    @DisplayName("25 & 30. Invariants Financiers & Idempotence Stricte")
    class InvariantsAndIdempotenceCertificationTests {

        @Test
        @DisplayName("Idempotence : RUN 1 == RUN 2 au centime")
        void testIdempotence() {
            PayrollCalculationInput in = buildBaseInput(new BigDecimal("750000.00"));
            in.setAttendanceInput(makeAttendance(120));
            in.setNbJoursConges(new BigDecimal("2.5"));
            in.setTauxJournalierConge(new BigDecimal("24000.00"));
            in.setHeuresHs30(new BigDecimal("4.0"));

            BulletinPaieComplet run1 = engine.calculer(in);
            BulletinPaieComplet run2 = engine.calculer(in);

            assertEquals(run1.getBulletin().getTotalGainsBruts(), run2.getBulletin().getTotalGainsBruts());
            assertEquals(run1.getBulletin().getTotalCotisationsSalariales(), run2.getBulletin().getTotalCotisationsSalariales());
            assertEquals(run1.getBulletin().getSalaireNetImposable(), run2.getBulletin().getSalaireNetImposable());
            assertEquals(run1.getBulletin().getIrsaNet(), run2.getBulletin().getIrsaNet());
            assertEquals(run1.getBulletin().getNetAPayer(), run2.getBulletin().getNetAPayer());
        }

        @Test
        @DisplayName("Invariant : Augmentation de salaire de base ne réduit jamais le brut")
        void testInvariantMonotonieBaseBrut() {
            PayrollCalculationInput in1 = buildBaseInput(new BigDecimal("600000.00"));
            PayrollCalculationInput in2 = buildBaseInput(new BigDecimal("700000.00"));

            BigDecimal brut1 = engine.calculer(in1).getBulletin().getTotalGainsBruts();
            BigDecimal brut2 = engine.calculer(in2).getBulletin().getTotalGainsBruts();

            assertTrue(brut2.compareTo(brut1) > 0);
        }

        @Test
        @DisplayName("Invariant : Augmentation d'absence ne peut jamais augmenter le net à payer")
        void testInvariantAbsenceNet() {
            PayrollCalculationInput in1 = buildBaseInput(new BigDecimal("600000.00"));
            in1.setAttendanceInput(makeAttendance(60));

            PayrollCalculationInput in2 = buildBaseInput(new BigDecimal("600000.00"));
            in2.setAttendanceInput(makeAttendance(180));

            BigDecimal net1 = engine.calculer(in1).getBulletin().getNetAPayer();
            BigDecimal net2 = engine.calculer(in2).getBulletin().getNetAPayer();

            assertTrue(net2.compareTo(net1) <= 0);
        }
    }

    // =========================================================================
    // SECTION 17 : RETENUES DE TRÉSORERIE HORS ASSIETTE
    // =========================================================================
    @Nested
    @DisplayName("17. Certification Retenues Hors Assiette (Invariance Fiscale & Sociale)")
    class RetenuesHorsAssietteCertificationTests {

        @Test
        @DisplayName("Acompte, Avance et Call Perso ne modifient NI Brut, NI Cotisations, NI IRSA")
        void testRetenuesHorsAssietteInvariance() {
            BigDecimal base = new BigDecimal("800000.00");
            PayrollCalculationInput inTemoin = buildBaseInput(base);
            BulletinPaieComplet temoin = engine.calculer(inTemoin);

            PayrollCalculationInput inRetenues = buildBaseInput(base);
            inRetenues.setAvancesAcomptes(List.of(
                    makeAcompte("ACOMPTE_15", new BigDecimal("50000.00")),
                    makeAcompte("AVANCE_SPECIALE", new BigDecimal("30000.00")),
                    makeAcompte("CALL_PERSO", new BigDecimal("12000.00"))
            ));
            BulletinPaieComplet actual = engine.calculer(inRetenues);

            // Invariance absolue du palier fiscal et social
            assertMoneyEquals(temoin.getBulletin().getTotalGainsBruts(), actual.getBulletin().getTotalGainsBruts());
            assertMoneyEquals(temoin.getBulletin().getTotalCotisationsSalariales(), actual.getBulletin().getTotalCotisationsSalariales());
            assertMoneyEquals(temoin.getBulletin().getSalaireNetImposable(), actual.getBulletin().getSalaireNetImposable());
            assertMoneyEquals(temoin.getBulletin().getIrsaNet(), actual.getBulletin().getIrsaNet());

            // Seul le net disponible est déduit des 92 000 Ar
            BigDecimal deltaNet = temoin.getBulletin().getNetAvantArrondi().subtract(actual.getBulletin().getNetAvantArrondi());
            assertMoneyEquals(new BigDecimal("92000.00"), deltaNet);
        }
    }

    // =========================================================================
    // SECTION 18 : INDEMNITÉS NON IMPOSABLES
    // =========================================================================
    @Nested
    @DisplayName("18. Certification des Indemnités Non Imposables")
    class IndemnitesNonImposablesCertificationTests {

        @Test
        @DisplayName("Repas et Déplacement s'ajoutent au net sans altérer le Brut, CNaPS, Santé, IRSA")
        void testIndemnitesNonImposables() {
            BigDecimal base = new BigDecimal("800000.00");
            PayrollCalculationInput inTemoin = buildBaseInput(base);
            BulletinPaieComplet temoin = engine.calculer(inTemoin);

            PayrollCalculationInput inIndem = buildBaseInput(base);
            inIndem.setIndemniteRepas(new BigDecimal("40000.00"));
            inIndem.setIndemniteTransport(new BigDecimal("50000.00"));
            BulletinPaieComplet actual = engine.calculer(inIndem);

            assertMoneyEquals(temoin.getBulletin().getTotalGainsBruts(), actual.getBulletin().getTotalGainsBruts());
            assertMoneyEquals(temoin.getBulletin().getTotalCotisationsSalariales(), actual.getBulletin().getTotalCotisationsSalariales());
            assertMoneyEquals(temoin.getBulletin().getSalaireNetImposable(), actual.getBulletin().getSalaireNetImposable());
            assertMoneyEquals(temoin.getBulletin().getIrsaNet(), actual.getBulletin().getIrsaNet());

            BigDecimal deltaNet = actual.getBulletin().getNetAvantArrondi().subtract(temoin.getBulletin().getNetAvantArrondi());
            assertMoneyEquals(new BigDecimal("90000.00"), deltaNet);
        }
    }

    // =========================================================================
    // SECTION 19 : ARRONDIS MONÉTAIRES
    // =========================================================================
    @Nested
    @DisplayName("19. Certification des Frontières d'Arrondi")
    class RoundingPolicyCertificationTests {

        @Test
        @DisplayName("Arrondis bancaires : HALF_UP sur centimes et MROUND 10 sur Net final")
        void testArrondisBancaires() {
            // MROUND 10
            assertEquals(new BigDecimal("555340.00"), BulletinPaie.arrondirDizaineAriary(new BigDecimal("555335.00")));
            assertEquals(new BigDecimal("555340.00"), BulletinPaie.arrondirDizaineAriary(new BigDecimal("555335.17")));
            assertEquals(new BigDecimal("555330.00"), BulletinPaie.arrondirDizaineAriary(new BigDecimal("555334.99")));
            assertEquals(new BigDecimal("555330.00"), BulletinPaie.arrondirDizaineAriary(new BigDecimal("555330.00")));
            assertEquals(new BigDecimal("555340.00"), BulletinPaie.arrondirDizaineAriary(new BigDecimal("555339.99")));
        }
    }

    // =========================================================================
    // SECTION 26 : SHADOW PAYROLL COMPLET (5 SALARIÉS RÉELS)
    // =========================================================================
    @Nested
    @DisplayName("26. Shadow Payroll Multi-Salariés (Delta Non Expliqué = 0)")
    class ShadowPayrollCertificationTests {

        record EmployeeSim(
                String nom,
                BigDecimal base,
                long minAbs,
                BigDecimal nbConge,
                BigDecimal tconge,
                BigDecimal autimp,
                BigDecimal h30,
                BigDecimal h50,
                String site,
                String organe,
                BigDecimal txSanteSal,
                BigDecimal txSantePat,
                int enfants,
                BigDecimal nonImposable,
                BigDecimal retenues
        ) {}

        @Test
        @DisplayName("Shadow Payroll sur 5 profils représentatifs : Oracle vs Runtime -> Delta = 0.00 Ar")
        void testShadowPayrollCompletePeriod() {
            List<EmployeeSim> roster = List.of(
                    new EmployeeSim("RAKETAKA", new BigDecimal("625000.00"), 691, new BigDecimal("11.5"), new BigDecimal("19348.12"),
                            new BigDecimal("25257.14"), BigDecimal.ZERO, BigDecimal.ZERO, "TNR", "OSTIE", new BigDecimal("0.01"), new BigDecimal("0.05"), 0, BigDecimal.ZERO, new BigDecimal("21470.00")),
                    new EmployeeSim("RASOA", new BigDecimal("1350000.00"), 0, BigDecimal.ZERO, null,
                            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "TNR", "OSTIE", new BigDecimal("0.01"), new BigDecimal("0.05"), 1, BigDecimal.ZERO, BigDecimal.ZERO),
                    new EmployeeSim("RAKOTO", new BigDecimal("1850000.00"), 0, BigDecimal.ZERO, null,
                            BigDecimal.ZERO, new BigDecimal("10.0"), new BigDecimal("4.0"), "TMM", "OMSI", new BigDecimal("0.015"), new BigDecimal("0.055"), 1, new BigDecimal("53200.00"), BigDecimal.ZERO),
                    new EmployeeSim("TOKY", new BigDecimal("3800000.00"), 0, BigDecimal.ZERO, null,
                            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "TNR", "OSTIE", new BigDecimal("0.01"), new BigDecimal("0.05"), 2, BigDecimal.ZERO, BigDecimal.ZERO),
                    new EmployeeSim("FALY", new BigDecimal("1050000.00"), 0, BigDecimal.ZERO, null,
                            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "TNR", "OSTIE", new BigDecimal("0.01"), new BigDecimal("0.05"), 0, BigDecimal.ZERO, BigDecimal.ZERO)
            );

            for (EmployeeSim emp : roster) {
                // 1. Calcul Oracle Indépendant
                OracleInput orIn = new OracleInput(
                        emp.base(), false, null, emp.minAbs(), null, new BigDecimal("173.33"),
                        emp.nbConge(), emp.tconge(),
                        emp.h30(), emp.h50(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        emp.autimp(), new BigDecimal("300000.00"),
                        new BigDecimal("0.01"), new BigDecimal("0.13"),
                        emp.txSanteSal(), emp.txSantePat(),
                        true, new BigDecimal("0.01"),
                        TRANCHES_LF2026_ORACLE, emp.enfants(), new BigDecimal("2000.00"), new BigDecimal("3000.00"),
                        false, null, emp.nonImposable(), emp.retenues()
                );
                OracleResult oracle = FinancialCertificationIndependentOracle.calculate(orIn);

                // 2. Calcul Runtime Moteur
                PayrollCalculationInput in = buildBaseInput(emp.base());
                in.setNomPrenom(emp.nom());
                in.setNomSite(emp.site());
                in.setCodeSanteOrgane(emp.organe());
                in.setTauxSanteSalarial(emp.txSanteSal());
                in.setTauxSantePatronal(emp.txSantePat());
                in.setNbEnfantsEligibles(emp.enfants());

                if (emp.minAbs() > 0) {
                    in.setAttendanceInput(makeAttendance(emp.minAbs()));
                }
                if (emp.nbConge().compareTo(BigDecimal.ZERO) > 0) {
                    in.setNbJoursConges(emp.nbConge());
                    in.setTauxJournalierConge(emp.tconge());
                }
                if (emp.autimp().compareTo(BigDecimal.ZERO) > 0) {
                    in.setAutimp(emp.autimp());
                }
                if (emp.h30().compareTo(BigDecimal.ZERO) > 0) {
                    in.setHeuresHs30(emp.h30());
                }
                if (emp.h50().compareTo(BigDecimal.ZERO) > 0) {
                    in.setHeuresHs50(emp.h50());
                }
                if (emp.nonImposable().compareTo(BigDecimal.ZERO) > 0) {
                    in.setIndemniteTransport(emp.nonImposable());
                }
                if (emp.retenues().compareTo(BigDecimal.ZERO) > 0) {
                    in.setAvancesAcomptes(List.of(makeAcompte("CALL_PERSO", emp.retenues())));
                }

                BulletinPaieComplet actual = engine.calculer(in);
                BulletinPaie bp = actual.getBulletin();

                // 3. Réconciliation complète ligne à ligne
                assertMoneyEquals(oracle.salaireBaseLigne(), bp.getSalaireBase());
                assertMoneyEquals(oracle.totalGainsBruts(), bp.getTotalGainsBruts());
                assertMoneyEquals(oracle.totalCotisationsSalariales(), bp.getTotalCotisationsSalariales());
                assertMoneyEquals(oracle.salaireNetImposable(), bp.getSalaireNetImposable());
                assertMoneyEquals(oracle.baseIrsaArrondie(), bp.getBaseIrsaArrondie());
                assertMoneyEquals(oracle.irsaNet(), bp.getIrsaNet());
                assertMoneyEquals(oracle.netAPayer(), bp.getNetAPayer());

                BigDecimal delta = bp.getNetAPayer().subtract(oracle.netAPayer());
                assertEquals(0, delta.compareTo(BigDecimal.ZERO),
                        "Delta non nul sur " + emp.nom() + " : delta=" + delta);
            }
        }
    }
}
