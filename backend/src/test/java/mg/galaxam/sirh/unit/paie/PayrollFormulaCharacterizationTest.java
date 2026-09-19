package mg.galaxam.sirh.unit.paie;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import mg.galaxam.sirh.model.paie.BulletinPaie;
import mg.galaxam.sirh.model.paie.BulletinPaieLigne;
import mg.galaxam.sirh.model.paie.dto.BulletinPaieComplet;
import mg.galaxam.sirh.model.paie.dto.PayrollAttendanceInput;
import mg.galaxam.sirh.model.paie.dto.PayrollCalculationInput;
import mg.galaxam.sirh.model.paie.engine.IrsaCalculator;
import mg.galaxam.sirh.model.paie.engine.IrsaCalculator.Tranche;
import mg.galaxam.sirh.model.paie.engine.IrsaCalculator.Result;
import mg.galaxam.sirh.model.paie.engine.MoneyRoundingPolicy;
import mg.galaxam.sirh.model.paie.engine.PayrollExecutionEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Vérification des Formules de Paie Métier & Tests Frontières")
public class PayrollFormulaCharacterizationTest {

    private PayrollExecutionEngine engine;
    private IrsaCalculator irsaCalculator;
    private List<Tranche> tranchesLf2024;

    @BeforeEach
    void setUp() {
        engine = new PayrollExecutionEngine();
        irsaCalculator = new IrsaCalculator();
        tranchesLf2024 = List.of(
                new Tranche(new BigDecimal("0.00"), new BigDecimal("350000.00"), BigDecimal.ZERO),
                new Tranche(new BigDecimal("350001.00"), new BigDecimal("400000.00"), new BigDecimal("0.05")),
                new Tranche(new BigDecimal("400001.00"), new BigDecimal("500000.00"), new BigDecimal("0.10")),
                new Tranche(new BigDecimal("500001.00"), new BigDecimal("600000.00"), new BigDecimal("0.15")),
                new Tranche(new BigDecimal("600001.00"), null, new BigDecimal("0.20"))
        );
    }

    private PayrollCalculationInput buildBaseInput(BigDecimal baseSalary) {
        PayrollCalculationInput in = new PayrollCalculationInput();
        in.setIdEmploye(1);
        in.setIdPeriodePaie(10);
        in.setMatricule("EMP-001");
        in.setNomPrenom("Test Employe");
        in.setSalaireBaseContractuel(baseSalary);
        in.setDiviseurMensuelHeures(new BigDecimal("173.3300"));
        in.setModeCalculPaie("MENSUEL");
        in.setRegimeIntervenant("PERMANENT");
        in.setCategorieProfessionnelle("OP1A");
        in.setDateEmbauche(java.sql.Date.valueOf(LocalDate.of(2023, 1, 1)));
        in.setNbEnfantsEligibles(0);
        in.setNbJoursOuvrables(new BigDecimal("21"));
        in.setNbJoursPresents(new BigDecimal("21"));

        in.setSmeNational(new BigDecimal("262680.00"));
        in.setMinimumPerceptionIrsa(new BigDecimal("2000.00"));
        in.setDeductionParEnfant(new BigDecimal("2000.00"));
        in.setTranchesIrsa(tranchesLf2024);
        in.setTauxCnapsSalarial(new BigDecimal("0.01"));
        in.setTauxCnapsPatronal(new BigDecimal("0.13"));
        in.setTauxSanteSalarial(new BigDecimal("0.01"));
        in.setTauxSantePatronal(new BigDecimal("0.05"));
        in.setTauxFmfpPatronal(new BigDecimal("0.01"));
        in.setSoumisCnaps(true);
        in.setSoumisSante(true);
        in.setSoumisIrsa(true);
        return in;
    }

    @Nested
    @DisplayName("1. Formule 1 — Taux horaire (Base / 173.33)")
    class TauxHoraireTests {
        @Test
        void testTauxHoraireExact() {
            BigDecimal base = new BigDecimal("625000.00");
            PayrollCalculationInput in = buildBaseInput(base);
            // 625 000 / 173.33 = 3605.8385738...
            BigDecimal expected = base.divide(new BigDecimal("173.3300"), 4, RoundingMode.HALF_UP);
            assertEquals(expected, in.getTauxHoraireEffectif());
            assertEquals(new BigDecimal("3605.8386"), in.getTauxHoraireEffectif());
        }
    }

    @Nested
    @DisplayName("2. Formule 2 — Absence (Heures vs Minutes sans arrondi prématuré)")
    class AbsenceFormulaTests {
        @Test
        void testAbsenceEnHeuresDirectes() {
            BigDecimal base = new BigDecimal("625000.00");
            PayrollCalculationInput in = buildBaseInput(base);
            in.setNbHeuresAbsence(new BigDecimal("11.52"));

            BulletinPaie bp = engine.initialiserEnTete(in);
            List<BulletinPaieLigne> lignes = new ArrayList<>();
            BigDecimal retenue = engine.calculerRetenueAbsence(in, base, bp.getTauxHoraire(), bp, lignes);

            // 11.52 * 625000 / 173.33 = 7 200 000 / 173.33 = 41 539.26037... -> 41 539.26 Ar
            assertEquals(new BigDecimal("41539.26"), retenue);
        }

        @Test
        void testAbsenceEnMinutesFrontieres() {
            BigDecimal base = new BigDecimal("520000.00");
            PayrollCalculationInput in = buildBaseInput(base);

            // 0 min
            PayrollAttendanceInput att0 = new PayrollAttendanceInput(
                    1, 1, 1, 10, Instant.now(), 10000L, 0L, 0L, 0L, 0L, 0L, 0L,
                    0L, 0, new BigDecimal("21"), new BigDecimal("21"), BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of()
            );
            in.setAttendanceInput(att0);
            BulletinPaie bp0 = engine.initialiserEnTete(in);
            List<BulletinPaieLigne> lignes0 = new ArrayList<>();
            BigDecimal r0 = engine.calculerRetenueAbsence(in, base, bp0.getTauxHoraire(), bp0, lignes0);
            assertEquals(BigDecimal.ZERO, r0);

            // 1 min: 520 000 * 1 / (173.33 * 60) = 520 000 / 10399.80 = 50.00096... -> 50.00 Ar
            PayrollAttendanceInput att1 = new PayrollAttendanceInput(
                    1, 1, 1, 10, Instant.now(), 10000L, 0L, 0L, 0L, 0L, 0L, 0L,
                    1L, 1, new BigDecimal("21"), new BigDecimal("21"), BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of()
            );
            in.setAttendanceInput(att1);
            BulletinPaie bp1 = engine.initialiserEnTete(in);
            List<BulletinPaieLigne> lignes1 = new ArrayList<>();
            BigDecimal r1 = engine.calculerRetenueAbsence(in, base, bp1.getTauxHoraire(), bp1, lignes1);
            assertEquals(new BigDecimal("50.00"), r1);

            // 60 min (1 heure): 520 000 * 60 / (173.33 * 60) = 520 000 / 173.33 = 3000.0577... -> 3000.06 Ar
            PayrollAttendanceInput att60 = new PayrollAttendanceInput(
                    1, 1, 1, 10, Instant.now(), 10000L, 0L, 0L, 0L, 0L, 0L, 0L,
                    60L, 60, new BigDecimal("21"), new BigDecimal("21"), BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of()
            );
            in.setAttendanceInput(att60);
            BulletinPaie bp60 = engine.initialiserEnTete(in);
            List<BulletinPaieLigne> lignes60 = new ArrayList<>();
            BigDecimal r60 = engine.calculerRetenueAbsence(in, base, bp60.getTauxHoraire(), bp60, lignes60);
            assertEquals(new BigDecimal("3000.06"), r60);
        }
    }

    @Nested
    @DisplayName("3. Formule 3 — Congé (Strictement Jours * TCONGE)")
    class CongeFormulaTests {
        @Test
        void testCongeFrontieres() {
            BigDecimal base = new BigDecimal("600000.00");
            PayrollCalculationInput in = buildBaseInput(base);
            BigDecimal tconge = new BigDecimal("20000.00");
            in.setTauxJournalierConge(tconge);

            // 0.5 jour
            in.setNbJoursConges(new BigDecimal("0.5"));
            BulletinPaie bp = engine.initialiserEnTete(in);
            List<BulletinPaieLigne> lignes = new ArrayList<>();
            engine.calculerConges(in, bp, lignes);
            BulletinPaieLigne ind05 = lignes.stream().filter(l -> "INDEMNITE_CONGE_PAYE".equals(l.getCodeRubrique())).findFirst().orElseThrow();
            assertEquals(new BigDecimal("10000.00"), ind05.getMontantSalarial());

            // 11.5 jours avec TCONGE = 19 348.12
            in.setNbJoursConges(new BigDecimal("11.5"));
            in.setTauxJournalierConge(new BigDecimal("19348.12"));
            lignes.clear();
            engine.calculerConges(in, bp, lignes);
            BulletinPaieLigne ind115 = lignes.stream().filter(l -> "INDEMNITE_CONGE_PAYE".equals(l.getCodeRubrique())).findFirst().orElseThrow();
            // 11.5 * 19 348.12 = 222 503.38 Ar
            assertEquals(new BigDecimal("222503.38"), ind115.getMontantSalarial());
        }
    }

    @Nested
    @DisplayName("4. Formule 5 & 6 — Cotisations CNaPS & Santé")
    class CotisationsTests {
        @Test
        void testCnapsPlafond() {
            // Plafond CNaPS = 2 101 440 Ar, Taux = 1%
            // Sous plafond
            BigDecimal baseSous = new BigDecimal("1500000.00");
            PayrollCalculationInput inSous = buildBaseInput(baseSous);
            BulletinPaieComplet resSous = engine.calculer(inSous);
            assertEquals(new BigDecimal("15000.00"), resSous.trouverLigne("COTISATION_CNAPS_SAL").getMontantSalarial());

            // Au plafond exact
            BigDecimal basePlafond = new BigDecimal("2101440.00");
            PayrollCalculationInput inPlafond = buildBaseInput(basePlafond);
            BulletinPaieComplet resPlafond = engine.calculer(inPlafond);
            assertEquals(new BigDecimal("21014.40"), resPlafond.trouverLigne("COTISATION_CNAPS_SAL").getMontantSalarial());

            // Au-dessus du plafond (ex: 3 000 000 Ar)
            BigDecimal baseHaut = new BigDecimal("3000000.00");
            PayrollCalculationInput inHaut = buildBaseInput(baseHaut);
            // Cas 1: Santé plafonnée (par défaut dans config = true)
            BulletinPaieComplet resHautPlafonne = engine.calculer(inHaut);
            assertEquals(new BigDecimal("21014.40"), resHautPlafonne.trouverLigne("COTISATION_CNAPS_SAL").getMontantSalarial());
            assertEquals(new BigDecimal("21014.40"), resHautPlafonne.trouverLigne("COTISATION_OSTIE_SAL").getMontantSalarial());

            // Cas 2: Santé non plafonnée (estPlafonneSante = false)
            inHaut.setEstPlafonneSante(false);
            BulletinPaieComplet resHautNonPlafonne = engine.calculer(inHaut);
            assertEquals(new BigDecimal("21014.40"), resHautNonPlafonne.trouverLigne("COTISATION_CNAPS_SAL").getMontantSalarial());
            assertEquals(new BigDecimal("30000.00"), resHautNonPlafonne.trouverLigne("COTISATION_OSTIE_SAL").getMontantSalarial());
        }
    }

    @Nested
    @DisplayName("5. Formule 8 — Assiette IRSA (Arrondi Inférieur à 100 Ar)")
    class AssietteIrsaBoundaryTests {
        @Test
        void testFrontieresAssietteCentaineInferieure() {
            // 620000.00 -> 620000.00
            Result r1 = irsaCalculator.calculate(new BigDecimal("620000.00"), tranchesLf2024, 0, BigDecimal.ZERO, BigDecimal.ZERO, null);
            assertEquals(new BigDecimal("620000.00"), r1.baseIrsaArrondie());

            // 620000.01 -> 620000.00
            Result r2 = irsaCalculator.calculate(new BigDecimal("620000.01"), tranchesLf2024, 0, BigDecimal.ZERO, BigDecimal.ZERO, null);
            assertEquals(new BigDecimal("620000.00"), r2.baseIrsaArrondie());

            // 620049.99 -> 620000.00
            Result r3 = irsaCalculator.calculate(new BigDecimal("620049.99"), tranchesLf2024, 0, BigDecimal.ZERO, BigDecimal.ZERO, null);
            assertEquals(new BigDecimal("620000.00"), r3.baseIrsaArrondie());

            // 620050.00 -> 620000.00 (prouve que ce n'est PAS HALF_UP)
            Result r4 = irsaCalculator.calculate(new BigDecimal("620050.00"), tranchesLf2024, 0, BigDecimal.ZERO, BigDecimal.ZERO, null);
            assertEquals(new BigDecimal("620000.00"), r4.baseIrsaArrondie());

            // 620099.99 -> 620000.00
            Result r5 = irsaCalculator.calculate(new BigDecimal("620099.99"), tranchesLf2024, 0, BigDecimal.ZERO, BigDecimal.ZERO, null);
            assertEquals(new BigDecimal("620000.00"), r5.baseIrsaArrondie());

            // 620100.00 -> 620100.00
            Result r6 = irsaCalculator.calculate(new BigDecimal("620100.00"), tranchesLf2024, 0, BigDecimal.ZERO, BigDecimal.ZERO, null);
            assertEquals(new BigDecimal("620100.00"), r6.baseIrsaArrondie());
        }
    }

    @Nested
    @DisplayName("6. Formule 9 & 10 — Barème IRSA & Arrondi Dizaine Supérieure")
    class BaremeIrsaBoundaryTests {
        @Test
        void testFrontieresTranchesIrsa() {
            BigDecimal minPerception = new BigDecimal("2000.00");

            // <= 350 000 : minimum 2000 Ar
            Result r349 = irsaCalculator.calculate(new BigDecimal("349900.00"), tranchesLf2024, 0, BigDecimal.ZERO, minPerception, null);
            assertEquals(new BigDecimal("2000.00"), r349.irsaNet());

            Result r350 = irsaCalculator.calculate(new BigDecimal("350000.00"), tranchesLf2024, 0, BigDecimal.ZERO, minPerception, null);
            assertEquals(new BigDecimal("2000.00"), r350.irsaNet());

            // 350 100 : brut = 99 * 5% = 4.95 Ar -> net = max(10, 2000) = 2000 Ar
            Result r350100 = irsaCalculator.calculate(new BigDecimal("350100.00"), tranchesLf2024, 0, BigDecimal.ZERO, minPerception, null);
            assertEquals(new BigDecimal("2000.00"), r350100.irsaNet());

            // 400 000 : 49999 * 5% = 2499.95 Ar -> arrondi dizaine = 2500.00 Ar
            Result r400 = irsaCalculator.calculate(new BigDecimal("400000.00"), tranchesLf2024, 0, BigDecimal.ZERO, minPerception, null);
            assertEquals(new BigDecimal("2499.95"), r400.irsaBrut());
            assertEquals(new BigDecimal("2500.00"), r400.irsaArrondiDizaine());

            // 500 000 : 2499.95 + 99999 * 10% (9999.90) = 12499.85 -> arrondi dizaine = 12500.00 Ar
            Result r500 = irsaCalculator.calculate(new BigDecimal("500000.00"), tranchesLf2024, 0, BigDecimal.ZERO, minPerception, null);
            assertEquals(new BigDecimal("12499.85"), r500.irsaBrut());
            assertEquals(new BigDecimal("12500.00"), r500.irsaArrondiDizaine());

            // 600 000 : 12499.85 + 99999 * 15% (14999.85) = 27499.70 -> arrondi dizaine = 27500.00 Ar
            Result r600 = irsaCalculator.calculate(new BigDecimal("600000.00"), tranchesLf2024, 0, BigDecimal.ZERO, minPerception, null);
            assertEquals(new BigDecimal("27499.70"), r600.irsaBrut());
            assertEquals(new BigDecimal("27500.00"), r600.irsaArrondiDizaine());

            // 620 000 : 27499.70 + 19999 * 20% (3999.80) = 31499.50 -> arrondi dizaine = 31500.00 Ar
            Result r620 = irsaCalculator.calculate(new BigDecimal("620000.00"), tranchesLf2024, 0, BigDecimal.ZERO, minPerception, null);
            assertEquals(new BigDecimal("31499.50"), r620.irsaBrut());
            assertEquals(new BigDecimal("31500.00"), r620.irsaArrondiDizaine());
        }

        @Test
        void testArrondiSuperieurDizaine() {
            // Test spécifique de l'arrondi supérieur à la dizaine
            BigDecimal min = BigDecimal.ZERO;
            // 31 499.80 (comme dans Excel) -> 31 500.00
            BigDecimal dizaineExcel = new BigDecimal("31499.80").divide(BigDecimal.TEN, 0, RoundingMode.CEILING).multiply(BigDecimal.TEN);
            assertEquals(new BigDecimal("31500"), dizaineExcel);

            // 31 499.50 (comme dans Java) -> 31 500.00
            BigDecimal dizaineJava = new BigDecimal("31499.50").divide(BigDecimal.TEN, 0, RoundingMode.CEILING).multiply(BigDecimal.TEN);
            assertEquals(new BigDecimal("31500"), dizaineJava);
        }
    }

    @Nested
    @DisplayName("7. Formule 14 — Net à Payer & Arrondi à la dizaine la plus proche (MROUND 10)")
    class NetAPayerRoundingTests {
        @Test
        void testArrondiDizaineAriary() {
            // 567 084.29 -> 567 080.00
            assertEquals(new BigDecimal("567080.00"), BulletinPaie.arrondirDizaineAriary(new BigDecimal("567084.29")));
            // 567 085.00 -> 567 090.00 (HALF_UP)
            assertEquals(new BigDecimal("567090.00"), BulletinPaie.arrondirDizaineAriary(new BigDecimal("567085.00")));
            // 567 086.50 -> 567 090.00
            assertEquals(new BigDecimal("567090.00"), BulletinPaie.arrondirDizaineAriary(new BigDecimal("567086.50")));
        }
    }

    @Nested
    @DisplayName("8. Réconciliation RAKETAKA — Preuve Mathématique & Détection du Bug Excel")
    class RaketakaReconciliationTests {
        @Test
        void testRaketakaCalculMetierCorrect() {
            // Entrées confirmées :
            // BASE = 625 000 Ar
            // NBABS = 11.52 HEURES
            // NBCONGE = 11.5 JOURS
            // TCONGE = 19 348.12 Ar/jour
            // HS = 0 Ar
            // AUTIMP = 25 257.14 Ar (Prime imposable)

            BigDecimal base = new BigDecimal("625000.00");
            PayrollCalculationInput in = buildBaseInput(base);
            in.setNbHeuresAbsence(new BigDecimal("11.52"));
            in.setNbJoursConges(new BigDecimal("11.5"));
            in.setTauxJournalierConge(new BigDecimal("19348.12"));
            in.setAutimp(new BigDecimal("25257.14"));

            // Simulation du calcul
            BulletinPaie bp = engine.initialiserEnTete(in);
            List<BulletinPaieLigne> lignes = new ArrayList<>();

            // 1. Taux horaire = 625000 / 173.33 = 3605.8386 Ar/h
            assertEquals(new BigDecimal("3605.8386"), bp.getTauxHoraire());

            // 2. Absence = 11.52 * 625000 / 173.33 = 41 539.26 Ar
            BigDecimal abs = engine.calculerRetenueAbsence(in, base, bp.getTauxHoraire(), bp, lignes);
            assertEquals(new BigDecimal("41539.26"), abs);
        }

        @Test
        @DisplayName("8.2 Exécution complète du Golden Master RAKETAKA de bout en bout via le runtime réel")
        void testRaketakaCompleteRuntimeExecution() {
            BigDecimal base = new BigDecimal("625000.00");
            PayrollCalculationInput in = buildBaseInput(base);
            in.setNbHeuresAbsence(new BigDecimal("11.52"));
            in.setNbJoursConges(new BigDecimal("11.5"));
            in.setTauxJournalierConge(new BigDecimal("19348.12"));
            in.setAutimp(new BigDecimal("25257.14"));

            BulletinPaieComplet complet = engine.calculer(in);
            BulletinPaie bp = complet.getBulletin();

            // 1. Lignes du bulletin
            assertEquals(new BigDecimal("625000.00"), bp.getSalaireBase());
            assertEquals(new BigDecimal("3605.8386"), bp.getTauxHoraire());

            // Retenue absence = 11.52 * 625000 / 173.33 = 41 539.26 Ar
            assertEquals(new BigDecimal("41539.26"), complet.trouverLigne("RETENUE_ABSENCE").getMontantSalarial());

            // Déduction congé base = 11.5 * 625000 / 30 = 239 583.33 Ar
            assertEquals(new BigDecimal("239583.33"), complet.trouverLigne("DEDUCTION_CONGE_BASE").getMontantSalarial());

            // Indemnité congé = 11.5 * 19 348.12 = 222 503.38 Ar
            assertEquals(new BigDecimal("222503.38"), complet.trouverLigne("INDEMNITE_CONGE_PAYE").getMontantSalarial());

            // Prime AUTIMP = 25 257.14 Ar
            assertEquals(new BigDecimal("25257.14"), complet.trouverLigne("AUTIMP").getMontantSalarial());

            // Brut = 625000 - 41539.26 - 239583.33 + 222503.38 + 25257.14 = 591 637.93 Ar
            assertEquals(new BigDecimal("591637.93"), bp.getTotalGainsBruts());

            // CNaPS salariale = 591 637.93 * 1% = 5 916.38 Ar
            assertEquals(new BigDecimal("5916.38"), complet.trouverLigne("COTISATION_CNAPS_SAL").getMontantSalarial());

            // Santé (OSTIE 1%) = 5 916.38 Ar
            assertEquals(new BigDecimal("5916.38"), complet.trouverLigne("COTISATION_OSTIE_SAL").getMontantSalarial());

            // Net imposable (NETI) = 591 637.93 - 2 * 5 916.38 = 579 805.17 Ar
            assertEquals(new BigDecimal("579805.17"), bp.getSalaireNetImposable());

            // Assiette IRSA = 100 * floor(579 805.17 / 100) = 579 800.00 Ar
            assertEquals(new BigDecimal("579800.00"), bp.getBaseIrsaArrondie());

            // IRSA net = 24 470.00 Ar
            assertEquals(new BigDecimal("24470.00"), bp.getIrsaNet());

            // Net avant arrondi = 579 805.17 - 24 470.00 = 555 335.17 Ar
            assertEquals(new BigDecimal("555335.17"), bp.getNetAvantArrondi());

            // Net à payer final = MROUND(555 335.17, 10) = 555 340.00 Ar
            assertEquals(new BigDecimal("555340.00"), bp.getNetAPayer());
        }
    }

    @Nested
    @DisplayName("9. Paliers de Congé Payé (0j, 1j, 5j, 15j, 30j) — Maintien par Substitution Strict")
    class CongeGraduationTests {
        private final BigDecimal base = new BigDecimal("600000.00");
        private final BigDecimal tconge = new BigDecimal("18000.00"); // Base/30 = 20 000 Ar, TCONGE = 18 000 Ar

        @Test
        @DisplayName("9.1 Scénario 0 jour congé : plein salaire sans ligne congé")
        void testConge0Jours() {
            PayrollCalculationInput in = buildBaseInput(base);
            in.setNbJoursConges(BigDecimal.ZERO);
            in.setTauxJournalierConge(tconge);

            BulletinPaieComplet res = engine.calculer(in);
            assertNull(res.trouverLigne("DEDUCTION_CONGE_BASE"));
            assertNull(res.trouverLigne("INDEMNITE_CONGE_PAYE"));
            assertEquals(new BigDecimal("600000.00"), res.getBulletin().getTotalGainsBruts());
        }

        @Test
        @DisplayName("9.2 Scénario 1 jour congé : -20 000 + 18 000 = -2 000 Ar")
        void testConge1Jour() {
            PayrollCalculationInput in = buildBaseInput(base);
            in.setNbJoursConges(new BigDecimal("1.0"));
            in.setTauxJournalierConge(tconge);

            BulletinPaieComplet res = engine.calculer(in);
            assertEquals(new BigDecimal("20000.00"), res.trouverLigne("DEDUCTION_CONGE_BASE").getMontantSalarial());
            assertEquals(new BigDecimal("18000.00"), res.trouverLigne("INDEMNITE_CONGE_PAYE").getMontantSalarial());
            // Brut = 600 000 - 20 000 + 18 000 = 598 000.00 Ar
            assertEquals(new BigDecimal("598000.00"), res.getBulletin().getTotalGainsBruts());
        }

        @Test
        @DisplayName("9.3 Scénario 5 jours congé : -100 000 + 90 000 = -10 000 Ar")
        void testConge5Jours() {
            PayrollCalculationInput in = buildBaseInput(base);
            in.setNbJoursConges(new BigDecimal("5.0"));
            in.setTauxJournalierConge(tconge);

            BulletinPaieComplet res = engine.calculer(in);
            assertEquals(new BigDecimal("100000.00"), res.trouverLigne("DEDUCTION_CONGE_BASE").getMontantSalarial());
            assertEquals(new BigDecimal("90000.00"), res.trouverLigne("INDEMNITE_CONGE_PAYE").getMontantSalarial());
            // Brut = 600 000 - 100 000 + 90 000 = 590 000.00 Ar
            assertEquals(new BigDecimal("590000.00"), res.getBulletin().getTotalGainsBruts());
        }

        @Test
        @DisplayName("9.4 Scénario 15 jours congé : -300 000 + 270 000 = -30 000 Ar")
        void testConge15Jours() {
            PayrollCalculationInput in = buildBaseInput(base);
            in.setNbJoursConges(new BigDecimal("15.0"));
            in.setTauxJournalierConge(tconge);

            BulletinPaieComplet res = engine.calculer(in);
            assertEquals(new BigDecimal("300000.00"), res.trouverLigne("DEDUCTION_CONGE_BASE").getMontantSalarial());
            assertEquals(new BigDecimal("270000.00"), res.trouverLigne("INDEMNITE_CONGE_PAYE").getMontantSalarial());
            // Brut = 600 000 - 300 000 + 270 000 = 570 000.00 Ar
            assertEquals(new BigDecimal("570000.00"), res.getBulletin().getTotalGainsBruts());
        }

        @Test
        @DisplayName("9.5 Scénario 30 jours congé (mois plein de vacances) : -600 000 + 540 000 = 540 000 Ar")
        void testConge30Jours() {
            PayrollCalculationInput in = buildBaseInput(base);
            in.setNbJoursConges(new BigDecimal("30.0"));
            in.setTauxJournalierConge(tconge);

            BulletinPaieComplet res = engine.calculer(in);
            assertEquals(new BigDecimal("600000.00"), res.trouverLigne("DEDUCTION_CONGE_BASE").getMontantSalarial());
            assertEquals(new BigDecimal("540000.00"), res.trouverLigne("INDEMNITE_CONGE_PAYE").getMontantSalarial());
            // Brut = 600 000 - 600 000 + 540 000 = 540 000.00 Ar (intégralité perçue sous forme d'ICP)
            assertEquals(new BigDecimal("540000.00"), res.getBulletin().getTotalGainsBruts());
        }
    }

    @Nested
    @DisplayName("10. Combinaisons d'Éléments de Brut (Congé, Absence, HS, Primes)")
    class CombinationScenariosTests {
        private final BigDecimal base = new BigDecimal("500000.00");
        private final BigDecimal tconge = new BigDecimal("17000.00");

        @Test
        @DisplayName("10.1 Congé seul")
        void testCongeSeul() {
            PayrollCalculationInput in = buildBaseInput(base);
            in.setNbJoursConges(new BigDecimal("3.0"));
            in.setTauxJournalierConge(tconge);

            BulletinPaieComplet res = engine.calculer(in);
            // Ded = 3 * 500 000 / 30 = 50 000.00 Ar
            // Ind = 3 * 17 000 = 51 000.00 Ar
            // Brut = 500 000 - 50 000 + 51 000 = 501 000.00 Ar
            assertEquals(new BigDecimal("501000.00"), res.getBulletin().getTotalGainsBruts());
            assertNull(res.trouverLigne("RETENUE_ABSENCE"));
        }

        @Test
        @DisplayName("10.2 Absence seule")
        void testAbsenceSeule() {
            PayrollCalculationInput in = buildBaseInput(base);
            in.setNbHeuresAbsence(new BigDecimal("8.00")); // 8h d'absence

            BulletinPaieComplet res = engine.calculer(in);
            // 8 * 500 000 / 173.33 = 23 077.367... -> 23 077.37 Ar
            assertEquals(new BigDecimal("23077.37"), res.trouverLigne("RETENUE_ABSENCE").getMontantSalarial());
            assertNull(res.trouverLigne("DEDUCTION_CONGE_BASE"));
            assertEquals(new BigDecimal("476922.63"), res.getBulletin().getTotalGainsBruts());
        }

        @Test
        @DisplayName("10.3 Congé + Absence")
        void testCongeEtAbsence() {
            PayrollCalculationInput in = buildBaseInput(base);
            in.setNbJoursConges(new BigDecimal("2.0"));
            in.setTauxJournalierConge(tconge);
            in.setNbHeuresAbsence(new BigDecimal("4.00"));

            BulletinPaieComplet res = engine.calculer(in);
            // Ded conge = 2 * 500 000 / 30 = 33 333.33 Ar
            // Ind conge = 2 * 17 000 = 34 000.00 Ar
            // Ret absence = 4 * 500 000 / 173.33 = 11 538.68 Ar
            // Brut = 500 000 - 11 538.68 - 33 333.33 + 34 000.00 = 489 127.99 Ar
            assertEquals(new BigDecimal("33333.33"), res.trouverLigne("DEDUCTION_CONGE_BASE").getMontantSalarial());
            assertEquals(new BigDecimal("34000.00"), res.trouverLigne("INDEMNITE_CONGE_PAYE").getMontantSalarial());
            assertEquals(new BigDecimal("11538.68"), res.trouverLigne("RETENUE_ABSENCE").getMontantSalarial());
            assertEquals(new BigDecimal("489127.99"), res.getBulletin().getTotalGainsBruts());
        }

        @Test
        @DisplayName("10.4 Congé + HS")
        void testCongeEtHs() {
            PayrollCalculationInput in = buildBaseInput(base);
            in.setNbJoursConges(new BigDecimal("2.0"));
            in.setTauxJournalierConge(tconge);
            in.setHeuresHs30(new BigDecimal("5.00")); // 5h à 30%

            BulletinPaieComplet res = engine.calculer(in);
            // Taux horaire = 500 000 / 173.33 = 2884.6708... Ar/h
            // HS 30% = 5 * 2884.6708 * 1.30 = 18 750.36 Ar
            // Net conge = +666.67 Ar
            // Brut = 500 000 + 666.67 + 18 750.36 = 519 417.03 Ar
            assertEquals(new BigDecimal("519417.03"), res.getBulletin().getTotalGainsBruts());
        }

        @Test
        @DisplayName("10.5 Congé + AUTIMP")
        void testCongeEtAutimp() {
            PayrollCalculationInput in = buildBaseInput(base);
            in.setNbJoursConges(new BigDecimal("2.0"));
            in.setTauxJournalierConge(tconge);
            in.setAutimp(new BigDecimal("50000.00"));

            BulletinPaieComplet res = engine.calculer(in);
            // Brut = 500 000 + 666.67 + 50 000 = 550 666.67 Ar
            assertEquals(new BigDecimal("550666.67"), res.getBulletin().getTotalGainsBruts());
        }

        @Test
        @DisplayName("10.6 Congé + Absence + HS + AUTIMP (Matrice Complète)")
        void testCongeAbsenceHsAutimp() {
            PayrollCalculationInput in = buildBaseInput(base);
            in.setNbJoursConges(new BigDecimal("2.0"));
            in.setTauxJournalierConge(tconge);
            in.setNbHeuresAbsence(new BigDecimal("4.00"));
            in.setHeuresHs30(new BigDecimal("5.00"));
            in.setAutimp(new BigDecimal("50000.00"));

            BulletinPaieComplet res = engine.calculer(in);
            // Net conge = +666.67 Ar
            // Ret absence = 11 538.68 Ar
            // HS 30 = 18 750.36 Ar
            // AUTIMP = 50 000.00 Ar
            // Brut = 500 000 - 11 538.68 + 666.67 + 18 750.36 + 50 000.00 = 557 878.35 Ar
            assertEquals(new BigDecimal("557878.35"), res.getBulletin().getTotalGainsBruts());
            // CNaPS = 5 578.78 Ar, OSTIE = 5 578.78 Ar
            assertEquals(new BigDecimal("5578.78"), res.trouverLigne("COTISATION_CNAPS_SAL").getMontantSalarial());
            assertEquals(new BigDecimal("5578.78"), res.trouverLigne("COTISATION_OSTIE_SAL").getMontantSalarial());
            // NETI = 557 878.35 - 11 157.56 = 546 720.79 Ar
            assertEquals(new BigDecimal("546720.79"), res.getBulletin().getSalaireNetImposable());
            // Assiette IRSA = 546 700.00 Ar
            assertEquals(new BigDecimal("546700.00"), res.getBulletin().getBaseIrsaArrondie());
        }
    }

    @Nested
    @DisplayName("11. Anti-Double-Déduction (Pointage CONGE vs RETENUE_ABSENCE)")
    class AntiDoubleDeductionAttendanceCongeTests {
        @Test
        @DisplayName("11.1 Un jour marqué CONGE dans le pointage n'engendre AUCUNE retenue absence injustifiée")
        void testPointageCongeSansRetenueAbsence() {
            BigDecimal base = new BigDecimal("600000.00");
            PayrollCalculationInput in = buildBaseInput(base);
            in.setNbJoursConges(new BigDecimal("1.0"));
            in.setTauxJournalierConge(new BigDecimal("20000.00"));

            // Snapshot de pointage scellé : 0 minute d'absence injustifiée
            PayrollAttendanceInput att = new PayrollAttendanceInput(
                    1, 1, 1, 10, Instant.now(),
                    9600L, 0L, 0L, 0L, 0L, 0L, 0L,
                    0L, 0, // 0 min déficit retenu
                    new BigDecimal("21"), new BigDecimal("20"), BigDecimal.ZERO, new BigDecimal("1.0"), // 1 jour congé
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of()
            );
            in.setAttendanceInput(att);

            BulletinPaieComplet res = engine.calculer(in);

            // 1. RETENUE_ABSENCE est absente (zéro déduction d'absence)
            assertNull(res.trouverLigne("RETENUE_ABSENCE"));

            // 2. DEDUCTION_CONGE_BASE est présente
            assertNotNull(res.trouverLigne("DEDUCTION_CONGE_BASE"));
            assertEquals(new BigDecimal("20000.00"), res.trouverLigne("DEDUCTION_CONGE_BASE").getMontantSalarial());

            // 3. INDEMNITE_CONGE_PAYE est présente
            assertNotNull(res.trouverLigne("INDEMNITE_CONGE_PAYE"));
            assertEquals(new BigDecimal("20000.00"), res.trouverLigne("INDEMNITE_CONGE_PAYE").getMontantSalarial());

            // Brut reste 600 000.00 Ar (maintien parfait sans pénalité)
            assertEquals(new BigDecimal("600000.00"), res.getBulletin().getTotalGainsBruts());
        }
    }

    @Nested
    @DisplayName("12. Test Dimensionnel & Anti-Mélange d'Unités (Heures vs Jours)")
    class DimensionalIntegrityTests {
        @Test
        @DisplayName("12.1 Étanchéité absolue : NBABS en heures sur 173.33 vs NBCONGE en jours sur /30 et TCONGE")
        void testStrictDimensionalSeparation() {
            BigDecimal base = new BigDecimal("625000.00");
            PayrollCalculationInput in = buildBaseInput(base);

            // Cas de 11.52 heures d'absence et 11.5 jours de congé
            in.setNbHeuresAbsence(new BigDecimal("11.52")); // HEURES
            in.setNbJoursConges(new BigDecimal("11.5"));     // JOURS
            in.setTauxJournalierConge(new BigDecimal("19348.12")); // Ar / jour

            BulletinPaieComplet res = engine.calculer(in);

            // 1. L'absence est valorisée en heures sur 173.33 (41 539.26 Ar) et JAMAIS sur /30 (qui aurait donné 240 052.06 Ar)
            BigDecimal absMnt = res.trouverLigne("RETENUE_ABSENCE").getMontantSalarial();
            assertEquals(new BigDecimal("41539.26"), absMnt);
            assertNotEquals(new BigDecimal("240052.06"), absMnt, "L'absence en heures ne doit JAMAIS être divisée par 30 !");

            // 2. Le congé est déduit en jours sur /30 (239 583.33 Ar) et valorisé en TCONGE (222 503.38 Ar), JAMAIS sur 173.33 ou /8
            BigDecimal dedConge = res.trouverLigne("DEDUCTION_CONGE_BASE").getMontantSalarial();
            BigDecimal indConge = res.trouverLigne("INDEMNITE_CONGE_PAYE").getMontantSalarial();
            assertEquals(new BigDecimal("239583.33"), dedConge);
            assertEquals(new BigDecimal("222503.38"), indConge);

            // 3. Aucune soustraction dimensionnelle absurde NBABS - NBCONGE (11.52h - 11.5j = 0.02)
            // L'ancien tableur trouvait 0.02, alors qu'ici les deux grandeurs vivent indépendamment
            assertNotEquals(new BigDecimal("0.02"), res.trouverLigne("RETENUE_ABSENCE").getQuantite());
            assertEquals(new BigDecimal("11.52"), res.trouverLigne("RETENUE_ABSENCE").getQuantite()); // 11.52 heures
            assertEquals(new BigDecimal("11.5"), res.trouverLigne("DEDUCTION_CONGE_BASE").getQuantite()); // 11.5 jours
        }
    }
}

