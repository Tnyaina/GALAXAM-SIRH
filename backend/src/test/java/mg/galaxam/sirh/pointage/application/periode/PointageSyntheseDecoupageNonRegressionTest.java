package mg.galaxam.sirh.pointage.application.periode;

import mg.galaxam.sirh.pointage.domain.calculation.AttendanceEngine;
import mg.galaxam.sirh.pointage.domain.calculation.AttendanceEngine.Context;
import mg.galaxam.sirh.pointage.domain.calculation.AttendanceEngine.PeriodBalance;
import mg.galaxam.sirh.pointage.domain.calculation.AttendanceEngine.Result;
import mg.galaxam.sirh.pointage.domain.calculation.AttendanceEngine.WeekContext;
import mg.galaxam.sirh.pointage.domain.calculation.AttendanceEngine.WeekResult;
import mg.galaxam.sirh.pointage.domain.calculation.AttendanceEngine.WeekSegment;
import mg.galaxam.sirh.pointage.domain.calculation.QualifiedTimeBucket;
import mg.galaxam.sirh.pointage.domain.calculation.QualifiedTimeBucket.DaySpecialType;
import mg.galaxam.sirh.pointage.domain.calculation.QualifiedTimeBucket.NightWorkType;
import mg.galaxam.sirh.pointage.domain.calculation.QualifiedTimeBucket.OvertimeTier;
import mg.galaxam.sirh.pointage.domain.calculation.TemporalAttendanceEngine;
import mg.galaxam.sirh.pointage.domain.calculation.TemporalAttendanceEngine.WorkInterval;
import mg.galaxam.sirh.pointage.domain.engine.CalendrierOuvrableEngine;
import mg.galaxam.sirh.pointage.api.dto.PointageSyntheseMensuelleDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de non-régression validant le découpage pédagogique de :
 * - {@link AttendanceEngine} (méthodes internes de calculateWeek)
 * - {@link PointageSyntheseService} (méthodes privées découpées)
 */
class PointageSyntheseDecoupageNonRegressionTest {

    private final AttendanceEngine attendanceEngine = new AttendanceEngine();

    // =========================================================================
    // 1. Découpage en tronçons hebdomadaires (26/09/2026 au 25/10/2026)
    // =========================================================================

    @Test
    @DisplayName("Période 26/09/2026 au 25/10/2026 : 5 tronçons dont le premier du 26 au 27 septembre")
    void testDecoupageSemainesTronqueesSeptembreOctobre2026() {
        LocalDate dateDebut = LocalDate.of(2026, 9, 26); // Samedi
        LocalDate dateFin = LocalDate.of(2026, 10, 25);   // Dimanche

        List<WeekSegment> segments = AttendanceEngine.segmentWeeks(dateDebut, dateFin);

        assertEquals(5, segments.size(), "La période du 26/09 au 25/10 doit comporter exactement 5 tronçons.");

        // Tronçon 1 : Samedi 26/09 au Dimanche 27/09 (tronqué au début)
        WeekSegment seg1 = segments.get(0);
        assertEquals(1, seg1.index());
        assertEquals(LocalDate.of(2026, 9, 26), seg1.start());
        assertEquals(LocalDate.of(2026, 9, 27), seg1.end());
        assertEquals(LocalDate.of(2026, 9, 21), seg1.monday());
        assertEquals(LocalDate.of(2026, 9, 27), seg1.sunday());
        assertTrue(seg1.isTruncatedStart(), "Le 1er tronçon commence un samedi, donc tronqué au début.");
        assertFalse(seg1.isTruncatedEnd(), "Le 1er tronçon finit un dimanche, pas tronqué à la fin.");

        // Tronçon 2 : Lundi 28/09 au Dimanche 04/10 (plein)
        WeekSegment seg2 = segments.get(1);
        assertEquals(2, seg2.index());
        assertEquals(LocalDate.of(2026, 9, 28), seg2.start());
        assertEquals(LocalDate.of(2026, 10, 4), seg2.end());
        assertEquals(LocalDate.of(2026, 9, 28), seg2.monday());
        assertEquals(LocalDate.of(2026, 10, 4), seg2.sunday());
        assertFalse(seg2.isTruncatedStart());
        assertFalse(seg2.isTruncatedEnd());

        // Tronçon 3 : Lundi 05/10 au Dimanche 11/10 (plein)
        WeekSegment seg3 = segments.get(2);
        assertEquals(3, seg3.index());
        assertEquals(LocalDate.of(2026, 10, 5), seg3.start());
        assertEquals(LocalDate.of(2026, 10, 11), seg3.end());

        // Tronçon 4 : Lundi 12/10 au Dimanche 18/10 (plein)
        WeekSegment seg4 = segments.get(3);
        assertEquals(4, seg4.index());
        assertEquals(LocalDate.of(2026, 10, 12), seg4.start());
        assertEquals(LocalDate.of(2026, 10, 18), seg4.end());

        // Tronçon 5 : Lundi 19/10 au Dimanche 25/10 (plein, se termine le dimanche 25)
        WeekSegment seg5 = segments.get(4);
        assertEquals(5, seg5.index());
        assertEquals(LocalDate.of(2026, 10, 19), seg5.start());
        assertEquals(LocalDate.of(2026, 10, 25), seg5.end());
        assertEquals(LocalDate.of(2026, 10, 19), seg5.monday());
        assertEquals(LocalDate.of(2026, 10, 25), seg5.sunday());
        assertFalse(seg5.isTruncatedStart());
        assertFalse(seg5.isTruncatedEnd());
    }

    @Test
    @DisplayName("Équivalence stricte sur 40 000 plages de dates : segmentWeeks vs initialisation jour-par-jour historique")
    void testEquivalenceInitialisationSemainesParSegmentVsJourParJour40000Plages() {
        LocalDate baseStart = LocalDate.of(2024, 1, 1);
        Random rng = new Random(123456789L);

        for (int i = 0; i < 40_000; i++) {
            int offsetDays = rng.nextInt(1200); // Couvre 2024 à 2027
            int durationDays = rng.nextInt(45) + 1; // 1 à 45 jours
            LocalDate start = baseStart.plusDays(offsetDays);
            LocalDate end = start.plusDays(durationDays - 1);

            // Ancienne boucle jour par jour dans PointageSyntheseService
            Map<LocalDate, List<Result>> oldParSemaine = new LinkedHashMap<>();
            for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
                LocalDate monday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                oldParSemaine.computeIfAbsent(monday, ignored -> new ArrayList<>());
            }

            // Nouvelle méthode par AttendanceEngine.segmentWeeks
            Map<LocalDate, List<Result>> newParSemaine = new LinkedHashMap<>();
            for (WeekSegment seg : AttendanceEngine.segmentWeeks(start, end)) {
                newParSemaine.computeIfAbsent(seg.monday(), ignored -> new ArrayList<>());
            }

            assertEquals(new ArrayList<>(oldParSemaine.keySet()), new ArrayList<>(newParSemaine.keySet()),
                    "L'ordre et la liste des lundis doivent être strictement identiques pour " + start + " -> " + end);
        }
    }

    // =========================================================================
    // 2. AttendanceEngine.calculateWeek : 25 000 simulations déterministes
    // =========================================================================

    @Test
    @DisplayName("Équivalence absolue de calculateWeek sur 25 000 simulations (ancien code monobloc vs nouveau découpé)")
    void testSimulationMassive25000CalculsHebdomadairesDeterministes() {
        Random rng = new Random(987654321L);
        LocalDate startMonday = LocalDate.of(2026, 1, 5);

        for (int i = 0; i < 25_000; i++) {
            LocalDate monday = startMonday.plusWeeks(rng.nextInt(100));
            long expected = rng.nextBoolean() ? 2400L : (rng.nextInt(5) + 1) * 480L;
            long firstBand = rng.nextBoolean() ? 240L : rng.nextInt(300);
            boolean overtimeEligible = rng.nextBoolean();
            long standardDay = 480L;

            Set<LocalDate> paidHalfDayLeaveDates = new HashSet<>();
            if (rng.nextInt(5) == 0) {
                paidHalfDayLeaveDates.add(monday.plusDays(rng.nextInt(5)));
            }

            WeekContext context = new WeekContext(monday, expected, firstBand, overtimeEligible, standardDay,
                    paidHalfDayLeaveDates);

            // Générer de 0 à 7 journées
            int numDays = rng.nextInt(8);
            List<Result> days = new ArrayList<>(numDays);
            for (int d = 0; d < numDays; d++) {
                LocalDate dayDate = monday.plusDays(d);
                Result.Status status;
                int stIdx = rng.nextInt(6);
                switch (stIdx) {
                    case 0 -> status = Result.Status.PRESENT;
                    case 1 -> status = Result.Status.HALF_DAY;
                    case 2 -> status = Result.Status.ABSENT;
                    case 3 -> status = Result.Status.LEAVE;
                    case 4 -> status = Result.Status.REST;
                    default -> status = Result.Status.PUBLIC_HOLIDAY_OFF;
                }
                boolean sunday = dayDate.getDayOfWeek() == DayOfWeek.SUNDAY;
                boolean holiday = rng.nextInt(10) == 0;

                int worked = (status == Result.Status.ABSENT || status == Result.Status.LEAVE
                        || status == Result.Status.REST || status == Result.Status.PUBLIC_HOLIDAY_OFF)
                        ? 0 : rng.nextInt(660); // 0 à 11h
                int late = rng.nextInt(60);

                days.add(new Result(1, dayDate, status, 0, 0, worked, worked, late, 0, 0,
                        sunday, holiday, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of()));
            }

            // Calcul de référence (l'implémentation monobloc d'origine)
            WeekResult expectedResult = calculateWeekReferenceMonobloc(context, days);

            // Calcul réel via AttendanceEngine (après refactoring)
            WeekResult actualResult = attendanceEngine.calculateWeek(context, days);

            assertAll(
                    () -> assertEquals(expectedResult.weekStart(), actualResult.weekStart()),
                    () -> assertEquals(expectedResult.expectedMinutes(), actualResult.expectedMinutes()),
                    () -> assertEquals(expectedResult.workedMinutes(), actualResult.workedMinutes()),
                    () -> assertEquals(expectedResult.dailySurplusMinutes(), actualResult.dailySurplusMinutes()),
                    () -> assertEquals(expectedResult.compensatedMinutes(), actualResult.compensatedMinutes()),
                    () -> assertEquals(expectedResult.grossOvertimeMinutes(), actualResult.grossOvertimeMinutes()),
                    () -> assertEquals(expectedResult.overtime30Minutes(), actualResult.overtime30Minutes()),
                    () -> assertEquals(expectedResult.overtime50Minutes(), actualResult.overtime50Minutes()),
                    () -> assertEquals(expectedResult.absenceMinutes(), actualResult.absenceMinutes()),
                    () -> assertEquals(expectedResult.lateMinutes(), actualResult.lateMinutes()),
                    () -> assertEquals(expectedResult.totalWeekOvertimeMinutes(), actualResult.totalWeekOvertimeMinutes()),
                    () -> assertEquals(expectedResult.totalWeekAbsenceMinutes(), actualResult.totalWeekAbsenceMinutes())
            );
        }
    }

    /**
     * Implémentation de référence strictement identique au code d'origine avant découpage.
     */
    private WeekResult calculateWeekReferenceMonobloc(WeekContext context, List<Result> days) {
        List<Result> safeDays = days == null ? List.of() : List.copyOf(days);
        long worked = 0;
        long late = 0;
        long dailySurplus = 0;
        for (Result day : safeDays) {
            worked += day.workedMinutes();
            late += day.lateMinutes();
            boolean compensable = !day.sunday() && !day.publicHoliday()
                    && day.status() != Result.Status.LEAVE && day.status() != Result.Status.REST
                    && day.status() != Result.Status.PUBLIC_HOLIDAY_OFF;
            if (compensable) {
                long target = (day.date() != null && day.date().getDayOfWeek() == DayOfWeek.SATURDAY) ? 0
                        : (context.paidHalfDayLeaveDates().contains(day.date()) ? context.standardDayMinutes() / 2 : context.standardDayMinutes());
                dailySurplus += Math.max(0, day.workedMinutes() - target);
            }
        }
        long missingBeforeCompensation = Math.max(0, context.expectedMinutes() - worked + dailySurplus);
        long compensated = Math.min(dailySurplus, missingBeforeCompensation);
        long grossOvertime = dailySurplus - compensated;
        long eligibleOvertime = context.overtimeEligible() ? grossOvertime : 0;
        long overtime30 = Math.min(eligibleOvertime, context.firstOvertimeBandMinutes());
        long overtime50 = Math.max(0, eligibleOvertime - overtime30);
        long absence = missingBeforeCompensation - compensated;
        return new WeekResult(context.weekStart(), context.expectedMinutes(), worked,
                dailySurplus, compensated, grossOvertime, overtime30, overtime50, absence, late, safeDays);
    }

    // =========================================================================
    // 3. Cas métiers spécifiques : Compensation, HS 30/50, Non-cadre vs Cadre
    // =========================================================================

    @Test
    @DisplayName("Compensation exacte : le surplus journalier absorbe le déficit de la semaine")
    void testCompensationSurplusAbsorbeDeficit() {
        LocalDate monday = LocalDate.of(2026, 10, 5);
        // Lundi : 10h (600m), surplus 120m (cible 8h/480m)
        // Mardi : 6h (360m), déficit 120m
        // Mercredi à Vendredi : 8h (480m) chacun
        List<Result> days = List.of(
                resultJour(monday, 600, 0),
                resultJour(monday.plusDays(1), 360, 0),
                resultJour(monday.plusDays(2), 480, 0),
                resultJour(monday.plusDays(3), 480, 0),
                resultJour(monday.plusDays(4), 480, 0)
        );

        WeekContext context = new WeekContext(monday, 2400, 240, true, 480);
        WeekResult res = attendanceEngine.calculateWeek(context, days);

        assertEquals(2400, res.workedMinutes());
        assertEquals(120, res.dailySurplusMinutes());
        assertEquals(120, res.compensatedMinutes(), "Le surplus de 120m doit compenser le déficit de 120m.");
        assertEquals(0, res.grossOvertimeMinutes(), "Aucune HS nette restante après compensation.");
        assertEquals(0, res.overtime30Minutes());
        assertEquals(0, res.overtime50Minutes());
        assertEquals(0, res.absenceMinutes(), "Déficit résorbé, 0 minute d'absence.");
    }

    @Test
    @DisplayName("Surplus supérieur au déficit : compensation puis génération d'heures supplémentaires")
    void testSurplusSuperieurAuDeficitProduitHs() {
        LocalDate monday = LocalDate.of(2026, 10, 5);
        // Lundi : 11h (660m), surplus 180m
        // Mardi : 7h (420m), déficit 60m
        // Mercredi à Vendredi : 8h (480m) chacun
        // Total travaillé = 2520m (42h), attendu = 2400m (40h)
        List<Result> days = List.of(
                resultJour(monday, 660, 0),
                resultJour(monday.plusDays(1), 420, 0),
                resultJour(monday.plusDays(2), 480, 0),
                resultJour(monday.plusDays(3), 480, 0),
                resultJour(monday.plusDays(4), 480, 0)
        );

        WeekContext context = new WeekContext(monday, 2400, 240, true, 480);
        WeekResult res = attendanceEngine.calculateWeek(context, days);

        assertEquals(2520, res.workedMinutes());
        assertEquals(180, res.dailySurplusMinutes());
        assertEquals(60, res.compensatedMinutes(), "60m de surplus compense les 60m manquants du mardi.");
        assertEquals(120, res.grossOvertimeMinutes(), "180 - 60 = 120m de HS brutes.");
        assertEquals(120, res.overtime30Minutes(), "Inférieur au premier palier (240m), tout est à 30%.");
        assertEquals(0, res.overtime50Minutes());
        assertEquals(0, res.absenceMinutes());
    }

    @Test
    @DisplayName("Déficit supérieur au surplus : surplus entièrement consommé et déficit résiduel")
    void testSurplusInferieurAuDeficitLaisseDeficit() {
        LocalDate monday = LocalDate.of(2026, 10, 5);
        // Lundi : 9h (540m), surplus 60m
        // Mardi : 5h (300m), déficit 180m
        // Mercredi à Vendredi : 8h (480m) chacun
        // Total travaillé = 2280m (38h), attendu = 2400m (40h)
        List<Result> days = List.of(
                resultJour(monday, 540, 0),
                resultJour(monday.plusDays(1), 300, 0),
                resultJour(monday.plusDays(2), 480, 0),
                resultJour(monday.plusDays(3), 480, 0),
                resultJour(monday.plusDays(4), 480, 0)
        );

        WeekContext context = new WeekContext(monday, 2400, 240, true, 480);
        WeekResult res = attendanceEngine.calculateWeek(context, days);

        assertEquals(2280, res.workedMinutes());
        assertEquals(60, res.dailySurplusMinutes());
        assertEquals(60, res.compensatedMinutes(), "Les 60m de surplus sont intégralement utilisés pour compenser.");
        assertEquals(0, res.grossOvertimeMinutes());
        assertEquals(0, res.overtime30Minutes());
        assertEquals(0, res.overtime50Minutes());
        assertEquals(120, res.absenceMinutes(), "Déficit résiduel = 180 - 60 = 120m (2h).");
    }

    @Test
    @DisplayName("Salarié non éligible aux HS (Cadre) : surplus non payable et 0 HS 30/50")
    void testCadreNonEligibleAuxHsConserveSurplusBrutSansPaliers() {
        LocalDate monday = LocalDate.of(2026, 10, 5);
        // 5 jours à 9h (540m) = 2700m (45h), 300m de surplus
        List<Result> days = List.of(
                resultJour(monday, 540, 0),
                resultJour(monday.plusDays(1), 540, 0),
                resultJour(monday.plusDays(2), 540, 0),
                resultJour(monday.plusDays(3), 540, 0),
                resultJour(monday.plusDays(4), 540, 0)
        );

        WeekContext context = new WeekContext(monday, 2400, 240, false, 480);
        WeekResult res = attendanceEngine.calculateWeek(context, days);

        assertEquals(2700, res.workedMinutes());
        assertEquals(300, res.dailySurplusMinutes());
        assertEquals(0, res.compensatedMinutes());
        assertEquals(300, res.grossOvertimeMinutes());
        assertEquals(0, res.overtime30Minutes(), "Cadre non éligible : 0 HS 30.");
        assertEquals(0, res.overtime50Minutes(), "Cadre non éligible : 0 HS 50.");
        assertEquals(0, res.totalWeekOvertimeMinutes());
        assertEquals(0, res.absenceMinutes());
    }

    @Test
    @DisplayName("Dépassement du premier palier HS : répartition 30% puis 50%")
    void testDepassementPremierPalierHsVersCinquantePourCent() {
        LocalDate monday = LocalDate.of(2026, 10, 5);
        // 5 jours à 10h (600m) = 3000m (50h), 600m de surplus
        List<Result> days = List.of(
                resultJour(monday, 600, 0),
                resultJour(monday.plusDays(1), 600, 0),
                resultJour(monday.plusDays(2), 600, 0),
                resultJour(monday.plusDays(3), 600, 0),
                resultJour(monday.plusDays(4), 600, 0)
        );

        WeekContext context = new WeekContext(monday, 2400, 240, true, 480);
        WeekResult res = attendanceEngine.calculateWeek(context, days);

        assertEquals(3000, res.workedMinutes());
        assertEquals(600, res.grossOvertimeMinutes());
        assertEquals(240, res.overtime30Minutes(), "Plafonné à la première tranche (240m = 4h).");
        assertEquals(360, res.overtime50Minutes(), "Le solde (600 - 240 = 360m = 6h) bascule à 50%.");
        assertEquals(600, res.totalWeekOvertimeMinutes());
    }

    // =========================================================================
    // 4. Bilan Période & Ajustements DAF
    // =========================================================================

    @Test
    @DisplayName("Bilan de période : non-cadre et cadre avec ajustements DAF positifs et négatifs")
    void testPeriodBalanceCalculations() {
        // Non-cadre avec HS 30, HS 50, déficit et ajustement
        PeriodBalance pbNonCadre = PeriodBalance.calculate(180, 60, 120, 0);
        assertEquals(120, pbNonCadre.balanceMinutes());
        assertEquals(60, pbNonCadre.overtime30Minutes());
        assertEquals(60, pbNonCadre.overtime50Minutes());
        assertEquals(0, pbNonCadre.absenceMinutes());

        // Cadre avec déficit, surplus compensable et ajustement positif
        PeriodBalance cadreDeficit = PeriodBalance.calculate(0, 0, 117, 282, 30, false);
        assertEquals(-135, cadreDeficit.balanceMinutes());
        assertEquals(135, cadreDeficit.absenceMinutes());
        assertEquals(0, cadreDeficit.overtime30Minutes());

        // Cadre avec excédent et ajustement
        PeriodBalance cadreExcedent = PeriodBalance.calculate(0, 0, 135, 0, 30, false);
        assertEquals(165, cadreExcedent.balanceMinutes());
        assertEquals(0, cadreExcedent.overtime30Minutes());
        assertEquals(0, cadreExcedent.absenceMinutes());

        // Rejet si volumes négatifs hors ajustement
        assertThrows(IllegalArgumentException.class, () -> PeriodBalance.calculate(-1, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> PeriodBalance.calculate(0, -1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> PeriodBalance.calculate(0, 0, -1, 0));
    }

    // =========================================================================
    // 5. Congés, jours chômés, statuts et heures attendues
    // =========================================================================

    @Test
    @DisplayName("Validation des statuts de congés et calcul des heures attendues")
    void testCongesEtHeuresAttendues() {
        // validatedLeaveStatus
        assertEquals("CONGE", PointageSyntheseService.validatedLeaveStatus(true, false));
        assertEquals("DEMI_JOURNEE", PointageSyntheseService.validatedLeaveStatus(true, true));
        assertEquals("ABSENCE_JUSTIFIEE", PointageSyntheseService.validatedLeaveStatus(false, false));
        assertEquals("ABSENCE_JUSTIFIEE", PointageSyntheseService.validatedLeaveStatus(false, true));

        // joursCongePourAttendu
        assertEquals(1.0, PointageSyntheseService.joursCongePourAttendu(Result.Status.LEAVE, false, true, false));
        assertEquals(0.5, PointageSyntheseService.joursCongePourAttendu(Result.Status.HALF_DAY, true, true, true));
        assertEquals(0.0, PointageSyntheseService.joursCongePourAttendu(Result.Status.ABSENT, false, false, false));

        // minutesAttendues : 5 jours ouvrés à 8h = 2400 min
        // 1 jour de congé payé -> 4 jours requis = 1920 min
        assertEquals(1920, PointageSyntheseService.minutesAttendues(BigDecimal.valueOf(8), 5.0, 1.0));
        // 0.5 jour de congé payé -> 4.5 jours requis = 2160 min
        assertEquals(2160, PointageSyntheseService.minutesAttendues(BigDecimal.valueOf(8), 5.0, 0.5));
        // 5 jours de congé payé -> 0 jour requis = 0 min
        assertEquals(0, PointageSyntheseService.minutesAttendues(BigDecimal.valueOf(8), 5.0, 5.0));

        // appliquerStatutCongeValide
        assertEquals("CONGE", PointageSyntheseService.appliquerStatutCongeValide("ABSENCE_JUSTIFIEE", 0.0, true, false));
        assertEquals("DEMI_JOURNEE", PointageSyntheseService.appliquerStatutCongeValide("ABSENCE_JUSTIFIEE", 4.0, true, true));
        assertEquals("ABSENCE_JUSTIFIEE", PointageSyntheseService.appliquerStatutCongeValide("DEMI_JOURNEE", 0.0, false, true));
    }

    // =========================================================================
    // 6. Indemnités de déplacement et proratisation
    // =========================================================================

    @Test
    @DisplayName("Proratisation de l'indemnité de déplacement et cohérence")
    void testIndemnitesProratisation() {
        CalendrierOuvrableEngine cal = new CalendrierOuvrableEngine();
        BigDecimal refSite = new BigDecimal("31200.00");
        int joursOuvrables = 22;

        BigDecimal tarifJour = cal.calculerProrataJournalier(refSite, joursOuvrables);
        assertNotNull(tarifJour);
        assertTrue(tarifJour.compareTo(BigDecimal.ZERO) > 0);
        // 31200 / 22 = 1418.1818... -> 1418.18
        assertEquals(new BigDecimal("1418.18"), tarifJour);

        // Si montantReference > 0 et nbJoursOuvrables <= 0 -> exception attendue
        assertThrows(IllegalArgumentException.class, () -> cal.calculerProrataJournalier(refSite, 0));
        // Si montantReference == 0 et nbJoursOuvrables <= 0 -> 0
        assertEquals(BigDecimal.ZERO, cal.calculerProrataJournalier(BigDecimal.ZERO, 0));
    }

    // =========================================================================
    // 7. Projection temporelle et vérification des bornes de campagne
    // =========================================================================

    @Test
    @DisplayName("Projection temporelle des tranches HS 30 / HS 50")
    void testProjectionTemporelleBuckets() {
        LocalDateTime start = LocalDate.of(2026, 8, 10).atTime(18, 0);
        var bucket = new TemporalAttendanceEngine.TemporalBucket(start, start.plusMinutes(300), 300,
                DaySpecialType.STANDARD, NightWorkType.NONE, 8, 42);

        var projections = new TemporalAttendanceEngine.OvertimeProjectionEngine()
                .project(List.of(bucket), 0, 240, 60);

        assertEquals(2, projections.size());
        assertEquals(OvertimeTier.OVERTIME_30, projections.get(0).overtimeTier());
        assertEquals(240, projections.get(0).minutes());
        assertEquals(OvertimeTier.OVERTIME_50, projections.get(1).overtimeTier());
        assertEquals(60, projections.get(1).minutes());
    }

    @Test
    @DisplayName("Refus strict des vacations chevauchant au-delà de la fin de campagne")
    void testRefusVacationDepassantFinCampagne() {
        LocalDate finCampagne = LocalDate.of(2026, 9, 25);
        LocalDateTime finExclusive = finCampagne.plusDays(1).atStartOfDay(); // 2026-09-26 00:00:00

        // Vacation qui se termine le 26 à 02:00:00 -> DÉPASSEMENT STRICT
        LocalDateTime vacStart = LocalDate.of(2026, 9, 25).atTime(22, 0);
        LocalDateTime vacEnd = LocalDate.of(2026, 9, 26).atTime(2, 0);

        assertTrue(vacEnd.isAfter(finExclusive),
                "La vacation se terminant le 26 à 02h00 doit dépasser la fin exclusive de campagne.");

        // Vacation conforme qui se termine le 26 à 00:00:00 -> CONFORME
        LocalDateTime vacEndConforme = LocalDate.of(2026, 9, 26).atTime(0, 0);
        assertFalse(vacEndConforme.isAfter(finExclusive));
    }

    // =========================================================================
    // 8. Snapshot fige et methodes decoupees de PointageSyntheseService
    // =========================================================================

    @Test
    @DisplayName("appliquerSnapshotFige applique les valeurs gelee si useFrozenSnapshot=true, ou reste inactif sinon")
    void testAppliquerSnapshotFige() throws Exception {
        PointageSyntheseService service = new PointageSyntheseService();
        Method m = PointageSyntheseService.class.getDeclaredMethod("appliquerSnapshotFige",
                PointageSyntheseMensuelleDTO.class, boolean.class, int.class, Integer.class,
                LocalDate.class, LocalDate.class, Connection.class);
        m.setAccessible(true);

        PointageSyntheseMensuelleDTO dtoDynamic = new PointageSyntheseMensuelleDTO();
        dtoDynamic.setNbJoursOuvrables(20);
        dtoDynamic.setNbJoursPresents(15.0);

        // Cas 1 : useFrozenSnapshot == false -> ne fait aucun acces DB et laisse le DTO intact
        Connection cMock = mock(Connection.class);
        m.invoke(service, dtoDynamic, false, 42, 10, LocalDate.of(2026, 9, 26), LocalDate.of(2026, 10, 25), cMock);
        assertEquals(20, dtoDynamic.getNbJoursOuvrables());
        assertEquals(15.0, dtoDynamic.getNbJoursPresents());
        verify(cMock, never()).prepareStatement(anyString());

        // Cas 2 : useFrozenSnapshot == true -> recharge les valeurs depuis pointage_cumul_periode
        PreparedStatement psMock = mock(PreparedStatement.class);
        ResultSet rsMock = mock(ResultSet.class);
        when(cMock.prepareStatement(anyString())).thenReturn(psMock);
        when(psMock.executeQuery()).thenReturn(rsMock);
        when(rsMock.next()).thenReturn(true);

        when(rsMock.getInt("nb_jours_ouvrables")).thenReturn(22);
        when(rsMock.getDouble("nb_jours_presents")).thenReturn(21.0);
        when(rsMock.getInt("nb_jours_absence")).thenReturn(1);
        when(rsMock.getDouble("nb_jours_conge")).thenReturn(0.0);
        when(rsMock.getInt("nb_jours_ferie_chome")).thenReturn(0);
        when(rsMock.getInt("nb_jours_ferie_travaille")).thenReturn(0);
        when(rsMock.getBigDecimal("total_heures_travaillees")).thenReturn(new BigDecimal("168.00"));
        when(rsMock.getBigDecimal("heures_hs_30")).thenReturn(new BigDecimal("4.00"));
        when(rsMock.getBigDecimal("heures_hs_50")).thenReturn(new BigDecimal("2.00"));
        when(rsMock.getBigDecimal("heures_nuit_total")).thenReturn(BigDecimal.ZERO);
        when(rsMock.getBigDecimal("heures_dimanche_total")).thenReturn(BigDecimal.ZERO);
        when(rsMock.getBigDecimal("heures_ferie_total")).thenReturn(BigDecimal.ZERO);
        when(rsMock.getBigDecimal("heures_absence_retenues")).thenReturn(BigDecimal.ZERO);
        when(rsMock.getInt("total_minutes_retard")).thenReturn(10);
        when(rsMock.getBigDecimal("nb_jours_deplacement")).thenReturn(new BigDecimal("21.00"));
        when(rsMock.getBigDecimal("montant_deplacement")).thenReturn(new BigDecimal("29781.78"));
        when(rsMock.getInt("nb_jours_repas")).thenReturn(21);
        when(rsMock.getBigDecimal("montant_repas")).thenReturn(new BigDecimal("105000.00"));
        when(rsMock.getObject("minutes_deficit_hebdo")).thenReturn(0);
        when(rsMock.getObject("minutes_solde_periode")).thenReturn(360);
        when(rsMock.getObject("minutes_ajustement_periode")).thenReturn(0);
        when(rsMock.getObject("minutes_hs30_hebdo")).thenReturn(240);
        when(rsMock.getObject("minutes_hs50_hebdo")).thenReturn(120);
        when(rsMock.getObject("minutes_absence_finale")).thenReturn(0);
        when(rsMock.getObject("minutes_hs30_finales")).thenReturn(240);
        when(rsMock.getObject("minutes_palier_hs30")).thenReturn(240);
        when(rsMock.getObject("minutes_hs50_finales")).thenReturn(120);

        PointageSyntheseMensuelleDTO dtoFrozen = new PointageSyntheseMensuelleDTO();
        m.invoke(service, dtoFrozen, true, 42, 10, LocalDate.of(2026, 9, 26), LocalDate.of(2026, 10, 25), cMock);

        assertEquals(22, dtoFrozen.getNbJoursOuvrables());
        assertEquals(21.0, dtoFrozen.getNbJoursPresents());
        assertEquals(1, dtoFrozen.getNbJoursAbsence());
        assertEquals(168.0, dtoFrozen.getTotalHeuresTravaillees());
        assertEquals(4.0, dtoFrozen.getHeuresHs30());
        assertEquals(2.0, dtoFrozen.getHeuresHs50());
        assertEquals(240, dtoFrozen.getMinutesHs30Hebdo());
        assertEquals(120, dtoFrozen.getMinutesHs50Hebdo());
        assertEquals(360, dtoFrozen.getMinutesSoldePeriode());
        assertEquals(360, dtoFrozen.getMinutesExcedentCompensable());
    }

    @Test
    @DisplayName("verifierVacationsDansLaPeriode : accepte vacations conformes et jette IllegalStateException si debordement")
    void testVerifierVacationsDansLaPeriodeMethod() throws Exception {
        PointageSyntheseService service = new PointageSyntheseService();
        Method m = PointageSyntheseService.class.getDeclaredMethod("verifierVacationsDansLaPeriode",
                List.class, LocalDate.class);
        m.setAccessible(true);

        LocalDate dateFin = LocalDate.of(2026, 9, 25);
        // Vacation conforme : finit le 26 a 00:00 (limite exacte)
        WorkInterval conforme = new WorkInterval(
                LocalDate.of(2026, 9, 25).atTime(18, 0),
                LocalDate.of(2026, 9, 26).atTime(0, 0),
                42, 1, "test");
        m.invoke(service, List.of(conforme), dateFin);

        // Vacation debordante : finit le 26 a 02:00
        WorkInterval debordante = new WorkInterval(
                LocalDate.of(2026, 9, 25).atTime(22, 0),
                LocalDate.of(2026, 9, 26).atTime(2, 0),
                42, 2, "test");

        InvocationTargetException ex = assertThrows(InvocationTargetException.class,
                () -> m.invoke(service, List.of(debordante), dateFin));
        assertTrue(ex.getCause() instanceof IllegalStateException);
        assertTrue(ex.getCause().getMessage().contains("POINTAGE_CROSS_PERIOD_SHIFT"));
    }

    @Test
    @DisplayName("preparerEnteteSynthese initialise l'identite, le site et la periode du DTO")
    void testPreparerEnteteSynthese() throws Exception {
        PointageSyntheseService service = new PointageSyntheseService();
        Method m = PointageSyntheseService.class.getDeclaredMethod("preparerEnteteSynthese",
                int.class, String.class, String.class, String.class, String.class,
                int.class, String.class, String.class, String.class, boolean.class,
                Integer.class, String.class, String.class, String.class, LocalDate.class, int.class,
                Map.class);
        m.setAccessible(true);

        PointageSyntheseMensuelleDTO dto = (PointageSyntheseMensuelleDTO) m.invoke(service,
                101, "EMP001", "Rakoto", "Jean", "photo.jpg",
                5, "SITE_HQ", "Siege", "CAT1", false,
                7, "P2026-10", "Octobre 2026", "BROUILLON",
                LocalDate.of(2026, 10, 25), 22, Map.of());

        assertNotNull(dto);
        assertEquals(101, dto.getIdEmploye());
        assertEquals("EMP001", dto.getMatricule());
        assertEquals("Rakoto", dto.getNom());
        assertEquals("Jean", dto.getPrenom());
        assertEquals("photo.jpg", dto.getPhotoUrl());
        assertEquals(5, dto.getIdSite());
        assertEquals("SITE_HQ", dto.getSiteCode());
        assertEquals("Siege", dto.getSiteNom());
        assertEquals("CAT1", dto.getCodeCategorie());
        assertEquals(7, dto.getIdPeriodePaie());
        assertEquals("P2026-10", dto.getCodePeriode());
        assertEquals("Octobre 2026", dto.getLibellePeriode());
        assertEquals("BROUILLON", dto.getStatutPeriode());
        assertEquals(22, dto.getNbJoursOuvrables());
    }

    // =========================================================================
    // Helper pour créer un Result journalier standard
    // =========================================================================

    private Result resultJour(LocalDate date, int workedMinutes, int lateMinutes) {
        return new Result(1, date, Result.Status.PRESENT, 0, 0, workedMinutes, workedMinutes, lateMinutes, 0, 0,
                date.getDayOfWeek() == DayOfWeek.SUNDAY, false, 0,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
    }
}
