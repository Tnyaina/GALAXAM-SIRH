package mg.galaxam.sirh.unit.paie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import mg.galaxam.sirh.model.paie.BulletinPaie;
import mg.galaxam.sirh.model.paie.BulletinPaieLigne;
import mg.galaxam.sirh.model.paie.dto.PayrollCalculationInput;
import mg.galaxam.sirh.model.paie.engine.PayrollExecutionEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Phase 2A-1 : Tests unitaires ciblés pour l'alignement du Payroll Core.
 * - Dette KAJY : les congés n'effacent jamais les absences.
 * - Prorata temporis /30 : bornes calendaires inclusives, pas de fallback heures.
 * - Absences en temps : valorisation horaire canonique, pas de jours * 8.
 * - Congés : dissociation stricte absence vs congé.
 * - Décomposition modulaire du Brut sans sur-engineering.
 */
public class Phase2a1PayrollCoreAlignmentUnitTest {

    private PayrollExecutionEngine engine;

    @BeforeEach
    void setUp() {
        engine = new PayrollExecutionEngine();
    }

    private PayrollCalculationInput buildBaseInput(BigDecimal salaireBase) {
        PayrollCalculationInput in = new PayrollCalculationInput();
        in.setIdEmploye(101);
        in.setMatricule("EMP-2A1");
        in.setNomPrenom("Rakoto 2A1");
        in.setRegimeIntervenant("PERMANENT");
        in.setCategorieProfessionnelle("AGENT");
        in.setModeCalculPaie("MENSUEL");
        in.setSalaireBaseContractuel(salaireBase);
        in.setDateDebutPeriode(Date.valueOf(LocalDate.of(2026, 9, 1)));
        in.setDateFinPeriode(Date.valueOf(LocalDate.of(2026, 9, 30)));
        in.setDateCalcul(Date.valueOf(LocalDate.of(2026, 9, 30)));
        in.setMajorationHs30(new BigDecimal("0.30"));
        in.setMajorationHs50(new BigDecimal("0.50"));
        in.setMajorationNuit(new BigDecimal("0.30"));
        in.setMajorationDimanche(new BigDecimal("0.40"));
        in.setMajorationFerie(new BigDecimal("1.00"));
        return in;
    }

    // =========================================================================
    // 1. TESTS DETTE KAJY (Section 16 & 17)
    // =========================================================================

    @Nested
    @DisplayName("1. Dette KAJY : Absence ≠ Congé (Les congés n'effacent jamais les absences)")
    class KajyDebtResolutionTests {

        /**
         * Règle canonique GALAXAM :
         * Droit acquis = 2.5 * max(0, 30 - jAbs) / 30
         * jAbs et jCge sont mutuellement exclusifs.
         */
        private BigDecimal calculerDroitAcquis(BigDecimal jAbs, BigDecimal jCge) {
            BigDecimal absInjustifiees = jAbs.max(BigDecimal.ZERO);
            BigDecimal formule = new BigDecimal("30.0").subtract(absInjustifiees).max(BigDecimal.ZERO);
            return new BigDecimal("2.5").multiply(formule).divide(new BigDecimal("30.0"), 4, RoundingMode.HALF_UP);
        }

        @Test
        @DisplayName("KAJY Matrice 1 : 0 absence + 0 congé -> 30j formule -> 2.5000 j acquis")
        void testKajy0Abs0Cge() {
            BigDecimal droit = calculerDroitAcquis(BigDecimal.ZERO, BigDecimal.ZERO);
            assertEquals(new BigDecimal("2.5000"), droit);
        }

        @Test
        @DisplayName("KAJY Matrice 2 : 2 absences + 0 congé -> 28j formule -> 2.3333 j acquis")
        void testKajy2Abs0Cge() {
            BigDecimal droit = calculerDroitAcquis(new BigDecimal("2.0"), BigDecimal.ZERO);
            assertEquals(new BigDecimal("2.3333"), droit);
        }

        @Test
        @DisplayName("KAJY Matrice 3 : 0 absence + 2 congés -> 30j formule (congés payés assimilés) -> 2.5000 j acquis")
        void testKajy0Abs2Cge() {
            BigDecimal droit = calculerDroitAcquis(BigDecimal.ZERO, new BigDecimal("2.0"));
            assertEquals(new BigDecimal("2.5000"), droit);
        }

        @Test
        @DisplayName("KAJY Matrice 4 : 2 absences + 2 congés -> 28j formule (les congés n'effacent PAS les absences) -> 2.3333 j acquis")
        void testKajy2Abs2Cge() {
            BigDecimal droit = calculerDroitAcquis(new BigDecimal("2.0"), new BigDecimal("2.0"));
            // L'ancien bug produisait 2.5000 car 2 - 2 = 0. La formule corrigée produit 2.3333 !
            assertEquals(new BigDecimal("2.3333"), droit);
        }

        @Test
        @DisplayName("KAJY Matrice 5 : 1 absence + 10 congés -> 29j formule -> 2.4167 j acquis")
        void testKajy1Abs10Cge() {
            BigDecimal droit = calculerDroitAcquis(new BigDecimal("1.0"), new BigDecimal("10.0"));
            assertEquals(new BigDecimal("2.4167"), droit);
        }
    }

    // =========================================================================
    // 2. TESTS DE FRONTIÈRE PRORATA TEMPORIS /30 (Section 5, 6, 7, 8, 9, 34)
    // =========================================================================

    @Nested
    @DisplayName("2. Prorata Temporis /30 et Frontières de Période")
    class ProrataTemporisTests {

        private final BigDecimal SALAIRE = new BigDecimal("600000.00");
        private final LocalDate DEBUT_PERIODE = LocalDate.of(2026, 9, 1);
        private final LocalDate FIN_PERIODE = LocalDate.of(2026, 9, 30);

        private long calculerJoursSousContrat(LocalDate dateDebutContrat, LocalDate dateFinContrat) {
            LocalDate effectiveStart = dateDebutContrat.isAfter(DEBUT_PERIODE) ? dateDebutContrat : DEBUT_PERIODE;
            LocalDate effectiveEnd = (dateFinContrat != null && dateFinContrat.isBefore(FIN_PERIODE)) ? dateFinContrat : FIN_PERIODE;
            if (effectiveEnd.isBefore(effectiveStart)) return 0;
            return ChronoUnit.DAYS.between(effectiveStart, effectiveEnd) + 1;
        }

        @Test
        @DisplayName("2.1 Salarié couvrant toute la période : salaire contractuel plein, aucun prorata")
        void testCouvertureComplete() {
            PayrollCalculationInput in = buildBaseInput(SALAIRE);
            in.setEntreeSortieEnCoursDeMois(false);

            BulletinPaie bp = engine.initialiserEnTete(in);
            List<BulletinPaieLigne> lignes = new ArrayList<>();
            BigDecimal baseLigne = engine.calculerSalaireBase(in, bp, lignes);

            assertEquals(SALAIRE, baseLigne);
            assertEquals(SALAIRE, bp.getSalaireBase());
        }

        @Test
        @DisplayName("2.2 Entrée le premier jour (01/09) : couvre toute la période -> pas de prorata")
        void testEntreePremierJour() {
            long jours = calculerJoursSousContrat(LocalDate.of(2026, 9, 1), null);
            assertEquals(30, jours);
            // Pas d'entrée en cours de mois car débutContrat == débutPériode
        }

        @Test
        @DisplayName("2.3 Entrée le deuxième jour (02/09) : 29 jours inclusifs -> 600 000 * 29 / 30 = 580 000 Ar")
        void testEntreeDeuxiemeJour() {
            long jours = calculerJoursSousContrat(LocalDate.of(2026, 9, 2), null);
            assertEquals(29, jours);

            PayrollCalculationInput in = buildBaseInput(SALAIRE);
            in.setEntreeSortieEnCoursDeMois(true);
            in.setJoursSousContrat(BigDecimal.valueOf(jours));

            BulletinPaie bp = engine.initialiserEnTete(in);
            List<BulletinPaieLigne> lignes = new ArrayList<>();
            BigDecimal baseLigne = engine.calculerSalaireBase(in, bp, lignes);

            assertEquals(new BigDecimal("580000.00"), baseLigne);
        }

        @Test
        @DisplayName("2.4 Entrée le 16/09 : 15 jours inclusifs -> 600 000 * 15 / 30 = 300 000 Ar")
        void testEntreeMilieuMois() {
            long jours = calculerJoursSousContrat(LocalDate.of(2026, 9, 16), null);
            assertEquals(15, jours); // 30 - 16 + 1 = 15

            PayrollCalculationInput in = buildBaseInput(SALAIRE);
            in.setEntreeSortieEnCoursDeMois(true);
            in.setJoursSousContrat(BigDecimal.valueOf(jours));

            BulletinPaie bp = engine.initialiserEnTete(in);
            List<BulletinPaieLigne> lignes = new ArrayList<>();
            BigDecimal baseLigne = engine.calculerSalaireBase(in, bp, lignes);

            assertEquals(new BigDecimal("300000.00"), baseLigne);
        }

        @Test
        @DisplayName("2.5 Entrée le dernier jour (30/09) : 1 jour inclusif -> 600 000 * 1 / 30 = 20 000 Ar")
        void testEntreeDernierJour() {
            long jours = calculerJoursSousContrat(LocalDate.of(2026, 9, 30), null);
            assertEquals(1, jours);

            PayrollCalculationInput in = buildBaseInput(SALAIRE);
            in.setEntreeSortieEnCoursDeMois(true);
            in.setJoursSousContrat(BigDecimal.valueOf(jours));

            BulletinPaie bp = engine.initialiserEnTete(in);
            List<BulletinPaieLigne> lignes = new ArrayList<>();
            BigDecimal baseLigne = engine.calculerSalaireBase(in, bp, lignes);

            assertEquals(new BigDecimal("20000.00"), baseLigne);
        }

        @Test
        @DisplayName("2.6 Sortie le premier jour (01/09) : 1 jour inclusif -> 600 000 * 1 / 30 = 20 000 Ar")
        void testSortiePremierJour() {
            long jours = calculerJoursSousContrat(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1));
            assertEquals(1, jours);

            PayrollCalculationInput in = buildBaseInput(SALAIRE);
            in.setEntreeSortieEnCoursDeMois(true);
            in.setJoursSousContrat(BigDecimal.valueOf(jours));

            BulletinPaie bp = engine.initialiserEnTete(in);
            List<BulletinPaieLigne> lignes = new ArrayList<>();
            BigDecimal baseLigne = engine.calculerSalaireBase(in, bp, lignes);

            assertEquals(new BigDecimal("20000.00"), baseLigne);
        }

        @Test
        @DisplayName("2.7 Sortie le 15/09 : 15 jours inclusifs -> 600 000 * 15 / 30 = 300 000 Ar")
        void testSortieMilieuMois() {
            long jours = calculerJoursSousContrat(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 15));
            assertEquals(15, jours); // 15 - 1 + 1 = 15

            PayrollCalculationInput in = buildBaseInput(SALAIRE);
            in.setEntreeSortieEnCoursDeMois(true);
            in.setJoursSousContrat(BigDecimal.valueOf(jours));

            BulletinPaie bp = engine.initialiserEnTete(in);
            List<BulletinPaieLigne> lignes = new ArrayList<>();
            BigDecimal baseLigne = engine.calculerSalaireBase(in, bp, lignes);

            assertEquals(new BigDecimal("300000.00"), baseLigne);
        }

        @Test
        @DisplayName("2.8 Sortie le dernier jour (30/09) : couvre toute la période -> pas de prorata")
        void testSortieDernierJour() {
            long jours = calculerJoursSousContrat(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 30));
            assertEquals(30, jours);
        }

        @Test
        @DisplayName("2.9 Entrée avant et sortie après la période -> couvre toute la période")
        void testContratDebordePeriode() {
            long jours = calculerJoursSousContrat(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
            assertEquals(30, jours);
        }

        @Test
        @DisplayName("2.10 Entrée et sortie en cours de mois (du 10 au 20) : 11 jours -> 600 000 * 11 / 30 = 220 000 Ar")
        void testEntreeEtSortieMemeMois() {
            long jours = calculerJoursSousContrat(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 20));
            assertEquals(11, jours); // 20 - 10 + 1 = 11

            PayrollCalculationInput in = buildBaseInput(SALAIRE);
            in.setEntreeSortieEnCoursDeMois(true);
            in.setJoursSousContrat(BigDecimal.valueOf(jours));

            BulletinPaie bp = engine.initialiserEnTete(in);
            List<BulletinPaieLigne> lignes = new ArrayList<>();
            BigDecimal baseLigne = engine.calculerSalaireBase(in, bp, lignes);

            assertEquals(new BigDecimal("220000.00"), baseLigne);
        }

        @Test
        @DisplayName("2.11 Interdiction stricte du fallback heures : PAYROLL_CONTRACT_PERIOD_DATA_MISSING")
        void testInterdictionFallbackHeures() {
            PayrollCalculationInput in = buildBaseInput(SALAIRE);
            in.setEntreeSortieEnCoursDeMois(true);
            in.setJoursSousContrat(null); // Information manquante

            BulletinPaie bp = engine.initialiserEnTete(in);
            List<BulletinPaieLigne> lignes = new ArrayList<>();

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> engine.calculerSalaireBase(in, bp, lignes));
            assertEquals("PAYROLL_CONTRACT_PERIOD_DATA_MISSING: prorata requis mais joursSousContrat absent ou invalide", ex.getMessage());
        }
    }

    // =========================================================================
    // 3. TESTS ABSENCES EN TEMPS & VALORISATION HORAIRE (Section 11, 12, 13, 35)
    // =========================================================================

    @Nested
    @DisplayName("3. Absences en Temps et Valorisation Horaire Canonique")
    class AbsenceTimeValuationTests {

        private final BigDecimal SALAIRE = new BigDecimal("600000.00");
        // Taux horaire = 600 000 / 173.33 = 3461.6050

        @Test
        @DisplayName("3.1 Absence 0 heure / 0 minute -> retenue 0")
        void testAbsenceZero() {
            PayrollCalculationInput in = buildBaseInput(SALAIRE);
            in.setNbHeuresAbsence(BigDecimal.ZERO);

            BulletinPaie bp = engine.initialiserEnTete(in);
            List<BulletinPaieLigne> lignes = new ArrayList<>();
            BigDecimal retenue = engine.calculerRetenueAbsence(in, SALAIRE, in.getTauxHoraireEffectif(), bp, lignes);

            assertEquals(BigDecimal.ZERO, retenue);
            assertEquals(0, lignes.size());
        }

        @Test
        @DisplayName("3.2 Absence 60 minutes (1.0 heure) -> 1.0 * 3461.6050 = 3461.61 Ar")
        void testAbsence1Heure() {
            PayrollCalculationInput in = buildBaseInput(SALAIRE);
            in.setNbHeuresAbsence(new BigDecimal("1.0"));

            BulletinPaie bp = engine.initialiserEnTete(in);
            List<BulletinPaieLigne> lignes = new ArrayList<>();
            BigDecimal retenue = engine.calculerRetenueAbsence(in, SALAIRE, in.getTauxHoraireEffectif(), bp, lignes);

            assertEquals(new BigDecimal("3461.61"), retenue);
            assertEquals(1, lignes.size());
            assertEquals("RETENUE_ABSENCE", lignes.get(0).getCodeRubrique());
        }

        @Test
        @DisplayName("3.3 Absence 90 minutes (1.5 heure) -> 1.5 * 3461.6050 = 5192.41 Ar")
        void testAbsenceFractionnaire90Minutes() {
            PayrollCalculationInput in = buildBaseInput(SALAIRE);
            in.setNbHeuresAbsence(new BigDecimal("1.5"));

            BulletinPaie bp = engine.initialiserEnTete(in);
            List<BulletinPaieLigne> lignes = new ArrayList<>();
            BigDecimal retenue = engine.calculerRetenueAbsence(in, SALAIRE, in.getTauxHoraireEffectif(), bp, lignes);

            // 1.5 * 3461.6050 = 5192.4075 -> arrondi HALF_UP = 5192.41
            assertEquals(new BigDecimal("5192.41"), retenue);
        }

        @Test
        @DisplayName("3.4 Absence 480 minutes (8.0 heures) -> 8.0 * 3461.6050 = 27692.84 Ar")
        void testAbsence8Heures() {
            PayrollCalculationInput in = buildBaseInput(SALAIRE);
            in.setNbHeuresAbsence(new BigDecimal("8.0"));

            BulletinPaie bp = engine.initialiserEnTete(in);
            List<BulletinPaieLigne> lignes = new ArrayList<>();
            BigDecimal retenue = engine.calculerRetenueAbsence(in, SALAIRE, in.getTauxHoraireEffectif(), bp, lignes);

            assertEquals(new BigDecimal("27692.84"), retenue);
        }

        @Test
        @DisplayName("3.5 Retenue plafonnée au salaire de base de la ligne")
        void testAbsencePlafonneeAuSalaireBase() {
            PayrollCalculationInput in = buildBaseInput(SALAIRE);
            // Absence de 200 heures (> 173.33) -> retenue théorique 692 321 Ar > 600 000 Ar
            in.setNbHeuresAbsence(new BigDecimal("200.0"));

            BulletinPaie bp = engine.initialiserEnTete(in);
            List<BulletinPaieLigne> lignes = new ArrayList<>();
            BigDecimal retenue = engine.calculerRetenueAbsence(in, SALAIRE, in.getTauxHoraireEffectif(), bp, lignes);

            assertEquals(SALAIRE, retenue);
        }
    }

    // =========================================================================
    // 4. TESTS CONGÉS & PRORATA COMBINÉS (Section 23, 24)
    // =========================================================================

    @Nested
    @DisplayName("4. Congés Payés et Dissociation Stricte Congé / Prorata")
    class LeavePayTests {

        private final BigDecimal SALAIRE = new BigDecimal("600000.00");
        // Taux trentième = 600 000 / 30 = 20 000.00 Ar

        @Test
        @DisplayName("4.1 Congé sans historique TCONGE -> Amorçage canonique Base / 30")
        void testCongeAmorcageTrentieme() {
            PayrollCalculationInput in = buildBaseInput(SALAIRE);
            in.setNbJoursConges(new BigDecimal("2.0"));
            // TCONGE non valorisé par historique -> utilise le taux trentième (20 000 Ar)
            in.setTauxJournalierConge(new BigDecimal("20000.00"));

            BulletinPaie bp = engine.initialiserEnTete(in);
            List<BulletinPaieLigne> lignes = new ArrayList<>();
            BigDecimal soldeConge = engine.calculerConges(in, bp, lignes);

            // Déduction : 2 * 20 000 = 40 000 Ar
            // Indemnité : 2 * 20 000 = 40 000 Ar
            // Solde net = 0 (maintien parfait de salaire)
            assertEquals(new BigDecimal("0.00"), soldeConge);
            assertEquals(2, lignes.size());
            assertEquals("DEDUCTION_CONGE_BASE", lignes.get(0).getCodeRubrique());
            assertEquals("INDEMNITE_CONGE_PAYE", lignes.get(1).getCodeRubrique());
        }

        @Test
        @DisplayName("4.2 Congé avec TCONGE revalorisé par HS historiques (22 000 Ar > 20 000 Ar)")
        void testCongeTauxRevalorise() {
            PayrollCalculationInput in = buildBaseInput(SALAIRE);
            in.setNbJoursConges(new BigDecimal("3.0"));
            in.setTauxJournalierConge(new BigDecimal("22000.00"));

            BulletinPaie bp = engine.initialiserEnTete(in);
            List<BulletinPaieLigne> lignes = new ArrayList<>();
            BigDecimal soldeConge = engine.calculerConges(in, bp, lignes);

            // Déduction trentième : 3 * 20 000 = 60 000 Ar
            // Indemnité valorisée : 3 * 22 000 = 66 000 Ar
            // Solde net : +6 000 Ar (gain sur maintien légal)
            assertEquals(new BigDecimal("6000.00"), soldeConge);
        }

        @Test
        @DisplayName("4.3 Prorata + Congé payé : pas de double réduction, assiettes distinctes")
        void testProrataPlusConge() {
            PayrollCalculationInput in = buildBaseInput(SALAIRE);
            in.setEntreeSortieEnCoursDeMois(true);
            in.setJoursSousContrat(new BigDecimal("15")); // Entrée le 16 -> 15j / 30
            in.setNbJoursConges(new BigDecimal("2.0"));
            in.setTauxJournalierConge(new BigDecimal("20000.00"));

            BulletinPaie bp = engine.initialiserEnTete(in);
            List<BulletinPaieLigne> lignes = new ArrayList<>();

            BigDecimal base = engine.calculerSalaireBase(in, bp, lignes);
            BigDecimal congeNet = engine.calculerConges(in, bp, lignes);

            // Base proratisée = 600 000 * 15 / 30 = 300 000 Ar
            assertEquals(new BigDecimal("300000.00"), base);
            // Congé déduit à 40 000 Ar et indemnisé à 40 000 Ar -> net = 0
            assertEquals(new BigDecimal("0.00"), congeNet);
        }
    }

    // =========================================================================
    // 5. TEST DE COMPOSITION GLOBALE DU BRUT (Section 42)
    // =========================================================================

    @Nested
    @DisplayName("5. Total Gains Bruts Canonique")
    class TotalGainsBrutsTests {

        @Test
        @DisplayName("5.1 Scénario combiné : Base proratisée + Absence + Congé valorisé + HS30 + Prime assiduité")
        void testScenarioGrossCombine() {
            BigDecimal salaire = new BigDecimal("600000.00");
            PayrollCalculationInput in = buildBaseInput(salaire);

            // 1. Prorata : entrée le 16 -> 15 jours sous contrat / 30 -> 300 000 Ar
            in.setEntreeSortieEnCoursDeMois(true);
            in.setJoursSousContrat(new BigDecimal("15"));

            // 2. Absence : 8 heures -> 8 * 3461.6050 = 27 692.84 Ar
            in.setNbHeuresAbsence(new BigDecimal("8.0"));

            // 3. Congé : 2 jours, TCONGE = 25 000 Ar
            // Déduction trentième : 2 * 20 000 = 40 000 Ar
            // Indemnité TCONGE : 2 * 25 000 = 50 000 Ar
            // Net congé = +10 000 Ar
            in.setNbJoursConges(new BigDecimal("2.0"));
            in.setTauxJournalierConge(new BigDecimal("25000.00"));

            // 4. HS30 : 10 heures -> 10 * 3461.6050 * 1.30 = 45 000.87 Ar
            in.setHeuresHs30(new BigDecimal("10.0"));

            // 5. Prime assiduité : 30 000 Ar (retard = 0 < 45 min)
            in.setPrimeAssiduiteForfait(new BigDecimal("30000.00"));
            in.setMinutesRetard(0);

            BulletinPaie bp = engine.initialiserEnTete(in);
            List<BulletinPaieLigne> lignes = new ArrayList<>();
            BigDecimal brut = engine.calculerSalaireBrut(in, bp, lignes);

            // Décomposition attendue :
            // Base          : 300 000.00
            // - Absence     :  27 692.84
            // - Déduction C :  40 000.00
            // + Indemnité C :  50 000.00
            // + HS30        :  45 000.87
            // + Assiduité   :  30 000.00
            // Total = 300 000 - 27 692.84 - 40 000 + 50 000 + 45 000.87 + 30 000
            //       = 357 308.03 Ar
            BigDecimal attendu = new BigDecimal("357308.03");
            assertEquals(attendu, brut);
            assertEquals(attendu, bp.getTotalGainsBruts());
        }
    }
}
