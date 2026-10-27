package mg.galaxam.sirh.pointage.application;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Locale;
import java.util.List;
import java.util.ArrayList;
import mg.galaxam.sirh.pointage.api.dto.SaisiePointageJourDTO;
import mg.galaxam.sirh.pointage.domain.calculation.AttendanceEngine;
import mg.galaxam.sirh.pointage.domain.engine.CalendrierOuvrableEngine;

/** Source commune de décision et de libellé des statuts de pointage journalier. */
public final class AttendanceStatusPolicy {
    private AttendanceStatusPolicy() {}

    public static String resolve(SaisiePointageJourDTO dto, AttendanceEngine.Result result, LocalDate date) {
        String explicit = normalize(dto.getCodeStatut());
        if (!result.anomalies().isEmpty()) return "ANOMALIE_SAISIE";
        if ("FULL_DAY".equalsIgnoreCase(dto.getTypeCongeJour())) return fromCalculation(result, dto);
        if ("ABSENCE_JUSTIFIEE".equals(explicit) || "ABSENCE_NON_JUSTIFIEE".equals(explicit)) return explicit;
        if ("DEMI_JOURNEE".equals(explicit) && !CalendrierOuvrableEngine.isWeekend(date) && !dto.isFerieJour()
                && dto.getMotifAjustement() != null && !dto.getMotifAjustement().isBlank()
                && result.status() == AttendanceEngine.Result.Status.HALF_DAY) return explicit;
        // Une seule plage complète ne prouve pas une demi-journée autorisée.
        // La qualification se fait lors de la régularisation, sauf congé partiel déjà approuvé.
        if (result.status() == AttendanceEngine.Result.Status.HALF_DAY
                && !CalendrierOuvrableEngine.isWeekend(date) && !dto.isFerieJour()
                && (dto.getTypeCongeJour() == null || dto.getTypeCongeJour().isBlank())) {
            return "ANOMALIE_SAISIE";
        }

        boolean hasPunch = hasTime(dto.getHeureEntreeMatin()) || hasTime(dto.getHeureSortieMatin())
                || hasTime(dto.getHeureEntreeAm()) || hasTime(dto.getHeureSortieAm());
        if (!hasPunch && !dto.isFerieJour() && !CalendrierOuvrableEngine.isWeekend(date)
                && !dto.isEstAutonome() && dto.getTypeCongeJour() == null) return "NON_SAISI";
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY && result.workedMinutes() > 0 && !dto.isFerieJour()) {
            return "SAMEDI_TRAVAILLE";
        }
        return fromCalculation(result, dto);
    }

    public static String fromCalculation(AttendanceEngine.Result result, SaisiePointageJourDTO dto) {
        return switch (result.status()) {
            case PRESENT -> "PRESENT";
            case HALF_DAY -> "DEMI_JOURNEE";
            case ABSENT -> "ABSENCE_NON_JUSTIFIEE";
            case LEAVE -> !dto.isCongePaye() ? "ABSENCE_JUSTIFIEE"
                    : (!dto.isCongeDecompteSolde() ? "DISPENSE_POINTAGE" : "CONGE");
            case REST -> "REPOS";
            case PUBLIC_HOLIDAY_OFF -> "FERIE_CHOME";
            case PUBLIC_HOLIDAY_WORKED -> "FERIE_TRAVAILLE";
            case SUNDAY_WORKED -> "DIMANCHE_TRAVAILLE";
            case AUTONOMOUS -> "DISPENSE_POINTAGE";
            case ANOMALY -> "ANOMALIE_SAISIE";
        };
    }

    public static String label(String code) {
        return switch (normalize(code)) {
            case "PRESENT" -> "Présent";
            case "RETARD" -> "Retard";
            case "DEMI_JOURNEE" -> "Demi-journée travaillée";
            case "ABSENCE_NON_JUSTIFIEE", "ABSENT" -> "Absent";
            case "ABSENCE_JUSTIFIEE" -> "Absence justifiée";
            case "ANOMALIE_SAISIE" -> "Incomplet / Anomalie";
            case "CONGE" -> "Congé";
            case "DISPENSE_POINTAGE", "AUTONOME" -> "Dispense de pointage";
            case "FERIE_TRAVAILLE" -> "Férié travaillé";
            case "FERIE_CHOME" -> "Férié chômé";
            case "SAMEDI_TRAVAILLE" -> "Samedi travaillé";
            case "DIMANCHE_TRAVAILLE" -> "Dimanche travaillé";
            case "REPOS" -> "Repos";
            case "NON_SAISI", "" -> "Non saisi";
            default -> "Non saisi";
        };
    }

    /** Qualification commune utilisée avant la valorisation des indemnités. */
    public static boolean isNonWorkedDay(String status, boolean fullDayLeave,
            boolean holiday, boolean weekend, double workedHours) {
        String code = normalize(status);
        return fullDayLeave || (holiday && workedHours <= 0.0)
                || "CONGE".equals(code) || "FERIE_CHOME".equals(code)
                || "ABSENCE_NON_JUSTIFIEE".equals(code) || "ABSENCE_JUSTIFIEE".equals(code)
                || "REPOS".equals(code) || (weekend && workedHours <= 0.0);
    }

    /** Une régularisation est une décision humaine : son choix explicite prime sur la déduction automatique. */
    public static String resolveRegularizedAbsence(String explicitStatus, String reason) {
        String code = normalize(explicitStatus);
        if (code.isEmpty() || "NON_SAISI".equals(code)) {
            return reason != null && !reason.isBlank() ? "ABSENCE_JUSTIFIEE" : "ABSENCE_NON_JUSTIFIEE";
        }
        return code;
    }

    public static String resolveRegularizedWork(String explicitStatus, boolean morningOnly,
            boolean afternoonOnly, double workedHours) {
        String code = normalize(explicitStatus);
        if (code.isEmpty() || "NON_SAISI".equals(code) || "ANOMALIE_SAISIE".equals(code)) {
            if (morningOnly || afternoonOnly) return "DEMI_JOURNEE";
            return workedHours > 0 ? "PRESENT" : "ABSENCE_NON_JUSTIFIEE";
        }
        return code;
    }

    public static String resolveRegularizedLeaveCoverage(double coveredDays, double requestedDays, String reason) {
        if (coveredDays >= requestedDays) return "CONGE";
        if (coveredDays > 0.0) return "DEMI_JOURNEE";
        return resolveRegularizedAbsence(null, reason);
    }

    public static List<SaisiePointageJourDTO.StatusAction> actions(SaisiePointageJourDTO dto) {
        if (dto.isFerieChome() || dto.isCongeApprouve() || dto.isEstAutonome()) return List.of();
        var actions = new ArrayList<SaisiePointageJourDTO.StatusAction>();
        actions.add(new SaisiePointageJourDTO.StatusAction("PRESENT", "Présent", "Journée travaillée", false));
        boolean morningOnly = hasTime(dto.getHeureEntreeMatin()) && hasTime(dto.getHeureSortieMatin())
                && !hasTime(dto.getHeureEntreeAm()) && !hasTime(dto.getHeureSortieAm());
        boolean afternoonOnly = !hasTime(dto.getHeureEntreeMatin()) && !hasTime(dto.getHeureSortieMatin())
                && hasTime(dto.getHeureEntreeAm()) && hasTime(dto.getHeureSortieAm());
        LocalDate day = dto.getDatePointage() == null ? null : LocalDate.parse(dto.getDatePointage());
        if ((morningOnly || afternoonOnly) && !dto.isFerieJour()
                && day != null && !CalendrierOuvrableEngine.isWeekend(day)) {
            actions.add(new SaisiePointageJourDTO.StatusAction("DEMI_JOURNEE", "Demi-journée",
                    "Valider une seule plage travaillée avec motif", true));
        }
        actions.add(new SaisiePointageJourDTO.StatusAction("ABSENCE_JUSTIFIEE", "Absence justifiée", "Absence documentée ou autorisée", true));
        actions.add(new SaisiePointageJourDTO.StatusAction("ABSENCE_NON_JUSTIFIEE", "Absence non justifiée", "Absence sans justificatif", false));
        actions.add(new SaisiePointageJourDTO.StatusAction("CONGE", "Congé payé", "Imputer sur solde congé payé", false));
        return actions;
    }

    private static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean hasTime(String value) {
        return value != null && !value.isBlank() && !"—".equals(value) && !"--:--".equals(value);
    }
}
