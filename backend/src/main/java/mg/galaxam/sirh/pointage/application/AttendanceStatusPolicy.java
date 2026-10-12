package mg.galaxam.sirh.pointage.application;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Locale;
import java.util.List;
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

    public static List<SaisiePointageJourDTO.StatusAction> actions(SaisiePointageJourDTO dto) {
        if (dto.isFerieChome() || dto.isCongeApprouve() || dto.isEstAutonome()) return List.of();
        return List.of(
                new SaisiePointageJourDTO.StatusAction("PRESENT", "Présent", "Journée travaillée", false),
                new SaisiePointageJourDTO.StatusAction("ABSENCE_JUSTIFIEE", "Absence justifiée", "Absence documentée ou autorisée", true),
                new SaisiePointageJourDTO.StatusAction("ABSENCE_NON_JUSTIFIEE", "Absence non justifiée", "Absence sans justificatif", false),
                new SaisiePointageJourDTO.StatusAction("CONGE", "Congé payé", "Imputer sur solde congé payé", false)
        );
    }

    private static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean hasTime(String value) {
        return value != null && !value.isBlank() && !"—".equals(value) && !"--:--".equals(value);
    }
}
