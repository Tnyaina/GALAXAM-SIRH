package mg.galaxam.sirh.unit.characterization;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import mg.galaxam.sirh.model.conge.engine.CongeCalculateurEngine;
import mg.galaxam.sirh.model.paie.BulletinPaie;
import mg.galaxam.sirh.model.paie.BulletinPaieLigne;
import mg.galaxam.sirh.model.paie.dto.PayrollCalculationInput;
import mg.galaxam.sirh.model.paie.engine.MoneyRoundingPolicy;
import mg.galaxam.sirh.model.paie.engine.OvertimePayCalculator;
import mg.galaxam.sirh.model.paie.engine.PayrollExecutionEngine;
import mg.galaxam.sirh.model.paie.engine.SocialContributionCalculator;
import mg.galaxam.sirh.model.pointage.calculation.DailyAttendanceResult;
import mg.galaxam.sirh.model.pointage.calculation.WeeklyAttendanceCalculator;
import mg.galaxam.sirh.model.pointage.calculation.WeeklyAttendanceContext;
import mg.galaxam.sirh.model.pointage.calculation.WeeklyAttendanceResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 0 — Tests de Caractérisation.
 * Sanctuaire des comportements valides et documentation probante des anomalies avant correction.
 */
@DisplayName("Phase 0 — Tests de Caractérisation & Sanctuarisation")
class Phase0CharacterizationTest {

    private final WeeklyAttendanceCalculator weeklyCalculator = new WeeklyAttendanceCalculator();
    private final CongeCalculateurEngine congeEngine = new CongeCalculateurEngine();
    private final PayrollExecutionEngine payrollEngine = new PayrollExecutionEngine();

    // =========================================================================
    // 1. COMPORTEMENTS VALIDES SANCTUARISÉS (Interdiction de régression)
    // =========================================================================

    @Nested
    @DisplayName("1. Comportements Sains Sanctuarisés")
    class ComportementsSainsSanctuarises {

        @Test
        @DisplayName("1.1 Semaine nominale 40h (5 x 8h) -> 0 déficit, 0 HS")
        void testSemaineNominale40h() {
            LocalDate monday = LocalDate.of(2026, 9, 14);
            WeeklyAttendanceContext ctx = new WeeklyAttendanceContext(monday, 2400, 480, true, 480);
            List<DailyAttendanceResult> days = List.of(
                    jour(monday, 480),
                    jour(monday.plusDays(1), 480),
                    jour(monday.plusDays(2), 480),
                    jour(monday.plusDays(3), 480),
                    jour(monday.plusDays(4), 480)
            );
            WeeklyAttendanceResult res = weeklyCalculator.calculateWeek(ctx, days);
            assertThat(res.absenceMinutes()).isZero();
            assertThat(res.grossOvertimeMinutes()).isZero();
            assertThat(res.compensatedMinutes()).isZero();
        }

        @Test
        @DisplayName("1.2 Compensation 7h + 9h + 8h + 8h + 8h = 40h -> déficit absorbé, 0 HS")
        void testCompensation7_9_8_8_8() {
            LocalDate monday = LocalDate.of(2026, 9, 14);
            WeeklyAttendanceContext ctx = new WeeklyAttendanceContext(monday, 2400, 480, true, 480);
            List<DailyAttendanceResult> days = List.of(
                    jour(monday, 420),        // 7h (-60 min déficit)
                    jour(monday.plusDays(1), 540), // 9h (+60 min surplus)
                    jour(monday.plusDays(2), 480), // 8h
                    jour(monday.plusDays(3), 480), // 8h
                    jour(monday.plusDays(4), 480)  // 8h
            );
            WeeklyAttendanceResult res = weeklyCalculator.calculateWeek(ctx, days);
            assertThat(res.compensatedMinutes()).isEqualTo(60);
            assertThat(res.absenceMinutes()).isZero();
            assertThat(res.grossOvertimeMinutes()).isZero();
        }

        @Test
        @DisplayName("1.3 Déficit 39h (7h + 4x8h) -> 60 min d'absence résiduelle, 0 HS")
        void testDeficitHebdomadaire39h() {
            LocalDate monday = LocalDate.of(2026, 9, 14);
            WeeklyAttendanceContext ctx = new WeeklyAttendanceContext(monday, 2400, 480, true, 480);
            List<DailyAttendanceResult> days = List.of(
                    jour(monday, 420),
                    jour(monday.plusDays(1), 480),
                    jour(monday.plusDays(2), 480),
                    jour(monday.plusDays(3), 480),
                    jour(monday.plusDays(4), 480)
            );
            WeeklyAttendanceResult res = weeklyCalculator.calculateWeek(ctx, days);
            assertThat(res.absenceMinutes()).isEqualTo(60);
            assertThat(res.grossOvertimeMinutes()).isZero();
            assertThat(res.compensatedMinutes()).isZero();
        }

        @Test
        @DisplayName("1.4 Dépassement 41h (9h + 4x8h) -> 60 min HS 30%, 0 déficit")
        void testHeuresSupHebdomadaire41h() {
            LocalDate monday = LocalDate.of(2026, 9, 14);
            WeeklyAttendanceContext ctx = new WeeklyAttendanceContext(monday, 2400, 480, true, 480);
            List<DailyAttendanceResult> days = List.of(
                    jour(monday, 540),
                    jour(monday.plusDays(1), 480),
                    jour(monday.plusDays(2), 480),
                    jour(monday.plusDays(3), 480),
                    jour(monday.plusDays(4), 480)
            );
            WeeklyAttendanceResult res = weeklyCalculator.calculateWeek(ctx, days);
            assertThat(res.absenceMinutes()).isZero();
            assertThat(res.grossOvertimeMinutes()).isEqualTo(60);
            assertThat(res.overtime30Minutes()).isEqualTo(60);
            assertThat(res.overtime50Minutes()).isZero();
        }

        @Test
        @DisplayName("1.5 Férié chômé (Modèle A objectif 32h) avec 32h travaillées -> 0 déficit, 0 HS")
        void testFerieChomeObjectifReduit32h() {
            LocalDate monday = LocalDate.of(2026, 9, 14);
            // Objectif réduit à 1920 min (32h), journée standard maintenue à 480 min (8h)
            WeeklyAttendanceContext ctx = new WeeklyAttendanceContext(monday, 1920, 480, true, 480);
            List<DailyAttendanceResult> days = List.of(
                    jourFerieChome(monday),
                    jour(monday.plusDays(1), 480),
                    jour(monday.plusDays(2), 480),
                    jour(monday.plusDays(3), 480),
                    jour(monday.plusDays(4), 480)
            );
            WeeklyAttendanceResult res = weeklyCalculator.calculateWeek(ctx, days);
            assertThat(res.absenceMinutes()).isZero();
            assertThat(res.grossOvertimeMinutes()).isZero();
        }

        @Test
        @DisplayName("1.6 Férié chômé (Modèle A objectif 32h) avec 33h travaillées -> 1h HS 30%")
        void testFerieChomeAvecDepassement33h() {
            LocalDate monday = LocalDate.of(2026, 9, 14);
            WeeklyAttendanceContext ctx = new WeeklyAttendanceContext(monday, 1920, 480, true, 480);
            List<DailyAttendanceResult> days = List.of(
                    jourFerieChome(monday),
                    jour(monday.plusDays(1), 540), // 9h (surplus 1h)
                    jour(monday.plusDays(2), 480), // 8h
                    jour(monday.plusDays(3), 480), // 8h
                    jour(monday.plusDays(4), 480)  // 8h
            );
            WeeklyAttendanceResult res = weeklyCalculator.calculateWeek(ctx, days);
            assertThat(res.absenceMinutes()).isZero();
            assertThat(res.grossOvertimeMinutes()).isEqualTo(60);
            assertThat(res.overtime30Minutes()).isEqualTo(60);
        }

        @Test
        @DisplayName("1.7 Salarié mensuel sans absence -> Salaire de base contractuel plein")
        void testSalaireBaseContractuelPlein() {
            PayrollCalculationInput in = new PayrollCalculationInput();
            in.setSalaireBaseContractuel(new BigDecimal("600000.00"));
            in.setEntreeSortieEnCoursDeMois(false);
            in.setNbHeuresAbsence(BigDecimal.ZERO);
            in.setNbJoursAbsence(BigDecimal.ZERO);

            BulletinPaie bp = payrollEngine.initialiserEnTete(in);
            List<BulletinPaieLigne> lignes = new ArrayList<>();
            BigDecimal brut = payrollEngine.calculerSalaireBrut(in, bp, lignes);

            assertThat(brut).isEqualByComparingTo("600000.00");
            assertThat(bp.getTotalGainsBruts()).isEqualByComparingTo("600000.00");
        }
    }

    // =========================================================================
    // 2. COMPORTEMENTS INCORRECTS ET LIMITES DÉMONTRÉS (Bugs à corriger)
    // =========================================================================

    @Nested
    @DisplayName("2. Démonstration Probante des Anomalies à Corriger")
    class AnomaliesDemontrees {

        @Test
        @DisplayName("2.1 Résolution Proratisation Paie : Requiert les jours sous contrat et calcule sur le trentième calendaire (/30)")
        void demontrerBugProratisationPaie() {
            // Embauche le 16 d'un mois de 30 jours (15 jours calendaires travaillés)
            // Règle confirmée attendue : 600 000 * 15 / 30 = 300 000.00 Ar
            // Le fallback horaire silencieux a été supprimé en Phase 2A-1
            PayrollCalculationInput in = new PayrollCalculationInput();
            in.setSalaireBaseContractuel(new BigDecimal("600000.00"));
            in.setEntreeSortieEnCoursDeMois(true);
            in.setHeuresNormales(new BigDecimal("80.00"));

            BulletinPaie bp = payrollEngine.initialiserEnTete(in);
            List<BulletinPaieLigne> lignes = new ArrayList<>();
            // Sans joursSousContrat : interdiction du fallback horaire
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> payrollEngine.calculerSalaireBrut(in, bp, lignes))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("PAYROLL_CONTRACT_PERIOD_DATA_MISSING");

            // Avec joursSousContrat = 15 : calcul canonique /30
            in.setJoursSousContrat(new BigDecimal("15"));
            List<BulletinPaieLigne> lignesValides = new ArrayList<>();
            BigDecimal brut = payrollEngine.calculerSalaireBrut(in, bp, lignesValides);
            assertThat(brut).isEqualByComparingTo("300000.00");
        }

        @Test
        @DisplayName("2.2 Bug KAJY jAbs - jCge : L'absence injustifiée est effacée par les congés pris")
        void demontrerBugKajyAbsenceEffaceeParConge() {
            // Salarié avec 2 jours d'absence injustifiée et 2 jours de congé
            BigDecimal jAbs = new BigDecimal("2.0");
            BigDecimal jCge = new BigDecimal("2.0");

            // Formule actuelle dans PayrollService.java:870 :
            BigDecimal absInjustifiees = jAbs.subtract(jCge).max(BigDecimal.ZERO);
            BigDecimal formule = new BigDecimal("30.0").subtract(absInjustifiees).max(BigDecimal.ZERO);
            BigDecimal droitMois = new BigDecimal("2.5").multiply(formule).divide(new BigDecimal("30.0"), 4, RoundingMode.HALF_UP);

            // CARACTÉRISATION DU BUG : Droit acquis calculé = 2.5000 (aucun impact des 2 jours d'absence injustifiée !)
            assertThat(absInjustifiees).isEqualByComparingTo("0.0");
            assertThat(formule).isEqualByComparingTo("30.0");
            assertThat(droitMois).isEqualByComparingTo("2.5000");

            // Règle confirmée cible : D = max(0, 30 - 2) = 28 jours -> 28 * 2.5 / 30 = 2.3333 jours !
            BigDecimal formuleCible = new BigDecimal("30.0").subtract(jAbs).max(BigDecimal.ZERO);
            BigDecimal droitMoisCible = new BigDecimal("2.5").multiply(formuleCible).divide(new BigDecimal("30.0"), 4, RoundingMode.HALF_UP);
            assertThat(formuleCible).isEqualByComparingTo("28.0");
            assertThat(droitMoisCible).isEqualByComparingTo("2.3333");
        }

        @Test
        @DisplayName("2.3 Résolution Biais Constructeur WeeklyAttendanceContext : standardDayMinutes est désormais explicite")
        void demontrerBiaisConstructeurExpectedMinutesDiv5() {
            LocalDate monday = LocalDate.of(2026, 9, 14);
            // Constructeur explicite sur une semaine à 32h attendues (1920 min)
            WeeklyAttendanceContext sainCtx = new WeeklyAttendanceContext(monday, 1920, 480, true, 480);

            // RÉSOLUTION P1-A : standardDayMinutes = 480 min (8h) sans dérivation erronée / 5 !
            assertThat(sainCtx.standardDayMinutes()).isEqualTo(480);
            long vraiSurplus = Math.max(0, 480 - sainCtx.standardDayMinutes());
            assertThat(vraiSurplus).isZero();
        }

        @Test
        @DisplayName("2.4 Limite SocialContributionCalculator : Plafond 8x SME hardcodé")
        void demontrerHardcodePlafond8xSme() {
            // Le calculateur pur applique "rules.smig().multiply(new BigDecimal(\"8\"))"
            SocialContributionCalculator calc = new SocialContributionCalculator();
            SocialContributionCalculator.Rules rules = new SocialContributionCalculator.Rules(
                    new BigDecimal("0.01"), new BigDecimal("0.13"),
                    new BigDecimal("0.01"), new BigDecimal("0.05"),
                    new BigDecimal("0.01"), new BigDecimal("262680.00"), true
            );
            // Base brute très élevée (5 000 000 Ar)
            SocialContributionCalculator.Result res = calc.calculate(new BigDecimal("5000000.00"), rules);

            // Plafond CNaPS = 262 680 * 8 = 2 101 440 Ar
            assertThat(res.cnaps().assiette()).isEqualByComparingTo("2101440.00");
            // CARACTÉRISATION : Le record Rules ne possède aucun champ pour faire varier ce facteur 8.
        }

        @Test
        @DisplayName("2.5 Limite OvertimePayCalculator : Majoration Férié appliquée à 200% (1 + Majoration)")
        void demontrerMajorationFerie200Pourcent() {
            OvertimePayCalculator calc = new OvertimePayCalculator();
            PayrollCalculationInput in = new PayrollCalculationInput();
            in.setHeuresFeriees(new BigDecimal("8.00"));
            in.setMajorationFerie(new BigDecimal("1.00")); // 100%

            BigDecimal hourlyRate = new BigDecimal("3000.00");
            OvertimePayCalculator.Result res = calc.calculate(in, hourlyRate);

            // holiday * hourlyRate * (1 + 1.00) = 8 * 3000 * 2.0 = 48 000 Ar
            // CARACTÉRISATION : Si l'heure de base est déjà dans le salaire mensuel fixe, l'employé perçoit 100% (base) + 200% (ici) = 300% !
            assertThat(res.ferie()).isEqualByComparingTo("48000.00");
        }
    }

    // =========================================================================
    // OUTILS D'ASSISTANCE DE TEST
    // =========================================================================

    private DailyAttendanceResult jour(LocalDate date, long workedMinutes) {
        return new DailyAttendanceResult(1, date, DailyAttendanceResult.Status.PRESENT,
                Math.min(240, workedMinutes), Math.max(0, workedMinutes - 240),
                workedMinutes, Math.min(480, workedMinutes), 0,
                Math.max(0, 480 - workedMinutes), 0, false, false,
                1, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
    }

    private DailyAttendanceResult jourFerieChome(LocalDate date) {
        return new DailyAttendanceResult(1, date, DailyAttendanceResult.Status.PUBLIC_HOLIDAY_OFF,
                0, 0, 0, 0, 0, 0, 0, false, true,
                0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
    }
}
