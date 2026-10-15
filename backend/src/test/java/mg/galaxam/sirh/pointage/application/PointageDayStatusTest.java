package mg.galaxam.sirh.pointage.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import mg.galaxam.sirh.pointage.api.dto.SaisiePointageJourDTO;
import mg.galaxam.sirh.pointage.domain.calculation.AttendanceEngine;
import org.junit.jupiter.api.Test;

class PointageDayStatusTest {
    private final AttendanceEngine engine = new AttendanceEngine();

    @Test
    void jourNonTravailleEstQualifiePareilPourSaisieEtRegularisation() {
        assertEquals(true, AttendanceStatusPolicy.isNonWorkedDay("CONGE", false, false, false, 0));
        assertEquals(true, AttendanceStatusPolicy.isNonWorkedDay("PRESENT", false, true, false, 0));
        assertEquals(true, AttendanceStatusPolicy.isNonWorkedDay("REPOS", false, false, true, 0));
        assertEquals(false, AttendanceStatusPolicy.isNonWorkedDay("FERIE_TRAVAILLE", false, true, false, 8));
        assertEquals(false, AttendanceStatusPolicy.isNonWorkedDay("PRESENT", false, false, false, 8));
    }

    @Test
    void regularisationGardeLaDecisionExpliciteEtQualifieLaCouvertureDuConge() {
        assertEquals("DEMI_JOURNEE", AttendanceStatusPolicy.resolveRegularizedWork(
                "ANOMALIE_SAISIE", true, false, 4.5));
        assertEquals("PRESENT", AttendanceStatusPolicy.resolveRegularizedWork(
                "NON_SAISI", false, false, 8.0));
        assertEquals("ABSENCE_JUSTIFIEE", AttendanceStatusPolicy.resolveRegularizedWork(
                "ABSENCE_JUSTIFIEE", false, false, 8.0));
        assertEquals("CONGE", AttendanceStatusPolicy.resolveRegularizedLeaveCoverage(1.0, 1.0, "Congé"));
        assertEquals("DEMI_JOURNEE", AttendanceStatusPolicy.resolveRegularizedLeaveCoverage(0.5, 1.0, "Congé"));
        assertEquals("ABSENCE_JUSTIFIEE", AttendanceStatusPolicy.resolveRegularizedLeaveCoverage(0, 1.0, "Justifié"));
    }

    @Test
    void jourOuvrableVideResteNonSaisi() {
        assertEquals("NON_SAISI", status(LocalDate.of(2026, 9, 28), null, null, null, null,
                "NON_SAISI", false));
    }

    @Test
    void retardAvecQuatreBornesRestePresent() {
        assertEquals("PRESENT", status(LocalDate.of(2026, 9, 28), "08:24", "12:30", "13:30", "17:00",
                "NON_SAISI", false));
    }

    @Test
    void uneSeulePlageEstUneAnomalieParDefaut() {
        assertEquals("ANOMALIE_SAISIE", status(LocalDate.of(2026, 9, 28), "08:00", "12:30", null, null,
                "PRESENT", false));
        assertEquals("ANOMALIE_SAISIE", status(LocalDate.of(2026, 9, 28), "08:00", "12:30", null, null,
                "DEMI_JOURNEE", false));
    }

    @Test
    void choixDemiJourneeDisponibleSeulementPourUnePlageComplete() {
        var dto = new SaisiePointageJourDTO();
        dto.setDatePointage("2026-08-07");
        assertEquals(false, AttendanceStatusPolicy.actions(dto).stream()
                .anyMatch(action -> "DEMI_JOURNEE".equals(action.code())));
        dto.setHeureEntreeMatin("08:00");
        dto.setHeureSortieMatin("12:30");
        assertEquals(true, AttendanceStatusPolicy.actions(dto).stream()
                .anyMatch(action -> "DEMI_JOURNEE".equals(action.code()) && action.requiresMotif()));
    }

    @Test
    void anomalieCorrigeeNeRestePasFigee() {
        assertEquals("ANOMALIE_SAISIE", status(LocalDate.of(2026, 9, 28), "08:00", null, null, null,
                "PRESENT", false));
        assertEquals("PRESENT", status(LocalDate.of(2026, 9, 28), "08:00", "12:30", "13:30", "17:00",
                "ANOMALIE_SAISIE", false));
    }

    @Test
    void demiJourneeQualifieeManuellementAvecMotif() {
        var dto = new SaisiePointageJourDTO();
        dto.setCodeStatut("DEMI_JOURNEE");
        dto.setMotifAjustement("Après-midi autorisé sans présence");
        dto.setHeureEntreeMatin("08:00");
        dto.setHeureSortieMatin("12:30");
        var result = engine.calculateDay(new AttendanceEngine.Context(1, LocalDate.of(2026, 8, 7),
                time("08:00"), time("12:30"), null, null,
                time("08:00"), time("12:30"), time("13:30"), time("17:00"),
                null, null, false, false, false, AttendanceEngine.Context.LeavePart.NONE,
                false, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false));
        assertEquals("DEMI_JOURNEE", PointageService.resolveDayStatus(dto, result, LocalDate.of(2026, 8, 7)));
    }

    @Test
    void reposDevientJourTravailleQuandDesBornesSontSaisies() {
        assertEquals("REPOS", status(LocalDate.of(2026, 9, 26), null, null, null, null, "REPOS", false));
        assertEquals("SAMEDI_TRAVAILLE", status(LocalDate.of(2026, 9, 26), "08:00", "12:30", null, null,
                "REPOS", false));
        assertEquals("REPOS", status(LocalDate.of(2026, 9, 27), null, null, null, null, "REPOS", false));
        assertEquals("DIMANCHE_TRAVAILLE", status(LocalDate.of(2026, 9, 27), "08:00", "12:30", null, null,
                "REPOS", false));
    }

    @Test
    void autonomeGardeSaDispenseAvecHorairesPreRemplis() {
        assertEquals("DISPENSE_POINTAGE", status(LocalDate.of(2026, 9, 28), "08:00", "12:30", "13:30", "17:00",
                "DISPENSE_POINTAGE", true));
    }

    @Test
    void retardResteUnIndicateurDistinctDuStatut() {
        var dto = new SaisiePointageJourDTO();
        dto.setCodeStatut("PRESENT");
        dto.setMinutesRetard(24);
        assertEquals(true, dto.isPresent());
        assertEquals(true, dto.isRetard());
        assertEquals("success", dto.getStatutBadgeVariant());
        dto.setCodeStatut("NON_SAISI");
        assertEquals(true, dto.isNonSaisi());
    }

    private String status(LocalDate date, String em, String sm, String ea, String sa,
            String priorStatus, boolean autonomous) {
        var dto = new SaisiePointageJourDTO();
        dto.setCodeStatut(priorStatus);
        dto.setHeureEntreeMatin(em);
        dto.setHeureSortieMatin(sm);
        dto.setHeureEntreeAm(ea);
        dto.setHeureSortieAm(sa);
        dto.setEstAutonome(autonomous);
        var result = engine.calculateDay(new AttendanceEngine.Context(1, date,
                time(em), time(sm), time(ea), time(sa),
                time("08:00"), time("12:30"), time("13:30"), time("17:00"),
                null, null, false, autonomous, false, AttendanceEngine.Context.LeavePart.NONE,
                false, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false));
        return PointageService.resolveDayStatus(dto, result, date);
    }

    private static LocalTime time(String value) {
        return value == null ? null : LocalTime.parse(value);
    }
}
