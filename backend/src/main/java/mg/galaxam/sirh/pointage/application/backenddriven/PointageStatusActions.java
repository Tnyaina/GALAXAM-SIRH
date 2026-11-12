package mg.galaxam.sirh.pointage.application.backenddriven;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import mg.galaxam.sirh.pointage.api.dto.SaisiePointageJourDTO;
import mg.galaxam.sirh.pointage.domain.engine.CalendrierOuvrableEngine;

/** Actions proposées à l'interface pour qualifier une journée. */
public final class PointageStatusActions {
    private PointageStatusActions() {}

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
        actions.add(new SaisiePointageJourDTO.StatusAction("ABSENCE_JUSTIFIEE", "Absence justifiée", "Motif obligatoire · qualification enregistrée directement", true));
        actions.add(new SaisiePointageJourDTO.StatusAction("ABSENCE_NON_JUSTIFIEE", "Absence non justifiée", "Qualification enregistrée directement", false));
        actions.add(new SaisiePointageJourDTO.StatusAction("CONGE", "Congé payé", "Imputer sur solde congé payé", false));
        return actions;
    }

    private static boolean hasTime(String value) {
        return value != null && !value.isBlank() && !"—".equals(value) && !"--:--".equals(value);
    }
}
