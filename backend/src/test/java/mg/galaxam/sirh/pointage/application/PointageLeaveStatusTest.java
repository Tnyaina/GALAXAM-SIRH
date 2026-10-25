package mg.galaxam.sirh.pointage.application;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import mg.galaxam.sirh.pointage.domain.calculation.AttendanceEngine;

import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PointageLeaveStatusTest {
    @Test
    void distinguishesUnpaidFullDayFromPaidLeave() {
        assertEquals("ABSENCE_JUSTIFIEE", PointageSyntheseService.validatedLeaveStatus(false, false));
        assertEquals("CONGE", PointageSyntheseService.validatedLeaveStatus(true, false));
        assertEquals("ABSENCE_JUSTIFIEE", PointageSyntheseService.validatedLeaveStatus(false, true));
        assertEquals("DEMI_JOURNEE", PointageSyntheseService.validatedLeaveStatus(true, true));
    }

    @Test
    void unpaidHalfDayRemainsInExpectedWorkAndProducesTheUncoveredAbsenceMinutes() {
        AttendanceEngine engine = new AttendanceEngine();
        var unpaid = engine.calculateWeek(new AttendanceEngine.WeekContext(
                        java.time.LocalDate.of(2026, 10, 5), 480, 0, false, 480),
                java.util.List.of(new AttendanceEngine.Result(1, java.time.LocalDate.of(2026, 10, 5),
                        AttendanceEngine.Result.Status.ABSENT, 0, 0, 240, 240, 0, 240, 0,
                        false, false, 0, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO,
                        java.math.BigDecimal.ZERO, java.util.List.of())));
        var paid = engine.calculateWeek(new AttendanceEngine.WeekContext(
                        java.time.LocalDate.of(2026, 10, 5), 240, 0, false, 480),
                java.util.List.of(new AttendanceEngine.Result(1, java.time.LocalDate.of(2026, 10, 5),
                        AttendanceEngine.Result.Status.HALF_DAY, 0, 0, 240, 240, 0, 0, 0,
                        false, false, 0, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO,
                        java.math.BigDecimal.ZERO, java.util.List.of())));
        assertEquals(240, unpaid.absenceMinutes());
        assertEquals(0, paid.absenceMinutes());
    }

    @Test
    void unpaidValidatedHalfDayDoesNotEraseTheUncoveredHalfWhenAWorkRowExists() {
        assertEquals("ABSENCE_JUSTIFIEE",
                PointageSyntheseService.appliquerStatutCongeValide("DEMI_JOURNEE", 4.0, false, true));
        assertEquals("DEMI_JOURNEE",
                PointageSyntheseService.appliquerStatutCongeValide("DEMI_JOURNEE", 4.0, true, true));
        assertEquals("DEMI_JOURNEE",
                AttendanceStatusPolicy.resolveRegularizedLeaveCoverage(0.5, 1.0, "Solde partiel"));
    }

    @Test
    void unpaidLeaveContributesToAbsenceOnDisposableCampaign() throws Exception {
        String url = System.getenv("GALAXAM_FIX_TEST_DB_URL");
        assumeTrue(url != null && url.contains("127.0.0.1:15441/galaxam_recette"));
        String user = System.getenv("GALAXAM_FIX_TEST_DB_USER");
        String password = System.getenv("GALAXAM_FIX_TEST_DB_PASSWORD");
        var source = new DriverManagerDataSource(url, user, password);
        try (var connection = DriverManager.getConnection(url, user, password)) {
            connection.setReadOnly(true);
            int employeeId;
            try (var statement = connection.prepareStatement(
                    "SELECT id_employe FROM demande_conge WHERE date_debut = DATE '2026-09-07' AND est_remunere = FALSE LIMIT 1");
                 var rows = statement.executeQuery()) {
                assumeTrue(rows.next());
                employeeId = rows.getInt(1);
            }
            var summary = new PointageSyntheseService(source).calculerSyntheseEmploye(
                    employeeId, "TEST", "", "", null, 1, "", "", "", false, false,
                    java.time.LocalDate.of(2026, 8, 26), java.time.LocalDate.of(2026, 9, 25),
                    null, "2026-09", "", "CLOTUREE", 0, java.util.Map.of(), java.util.Map.of(), connection);
            assertTrue(summary.getNbJoursAbsence() >= 1);
            assertTrue(summary.getMinutesAbsenceFinale() > 0);
        }
    }
}
