package mg.galaxam.sirh.pointage.domain.calculation;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Usine responsable de la reconstruction temporelle stricte des WorkInterval à partir des pointages journaliers bruts.
 * Ne contient aucune règle RH de durée maximale (responsabilité du workflow d'anomalies).
 * Reconstruit fidèlement la réalité continue des pointages réels.
 */
public final class WorkIntervalFactory {

    private WorkIntervalFactory() {}

    /**
     * Reconstruit les intervalles de travail à partir d'une journée de pointage.
     * Respecte rigoureusement la pause déjeuner (2 intervalles distincts pour horaire standard).
     * Gère les vacations de nuit traversant minuit si heureSortie < heureEntree (J -> J+1).
     * Si un pointage est incomplet (entrée ou sortie manquante), aucun intervalle n'est inventé.
     */
    public static List<WorkInterval> createIntervals(
            Integer employeeId,
            Integer pointageId,
            LocalDate datePointage,
            LocalTime entreeMatin,
            LocalTime sortieMatin,
            LocalTime entreeAm,
            LocalTime sortieAm,
            boolean isHoraireContinu,
            String sourceRef
    ) {
        if (datePointage == null) {
            throw new IllegalArgumentException("La date de pointage est obligatoire.");
        }

        List<WorkInterval> intervals = new ArrayList<>();

        if (isHoraireContinu) {
            // Journée continue : un seul intervalle continu de l'entrée matin à la sortie après-midi
            if (entreeMatin != null && sortieAm != null) {
                WorkInterval interval = resolveSingleInterval(
                        employeeId, pointageId, datePointage, entreeMatin, sortieAm, sourceRef + "_CONTINU");
                if (interval != null) intervals.add(interval);
            }
        } else {
            // Journée standard fractionnée : Matin et Après-midi séparés (préservation de la pause)
            if (entreeMatin != null && sortieMatin != null) {
                WorkInterval mat = resolveSingleInterval(
                        employeeId, pointageId, datePointage, entreeMatin, sortieMatin, sourceRef + "_MATIN");
                if (mat != null) intervals.add(mat);
            }
            if (entreeAm != null && sortieAm != null) {
                // Si le matin a traversé minuit, l'après-midi part de la date suivante
                LocalDate amStartDate = datePointage;
                if (!intervals.isEmpty() && intervals.get(0).end().toLocalDate().isAfter(datePointage)) {
                    amStartDate = intervals.get(0).end().toLocalDate();
                }
                WorkInterval am = resolveSingleInterval(
                        employeeId, pointageId, amStartDate, entreeAm, sortieAm, sourceRef + "_AM");
                if (am != null) intervals.add(am);
            }
        }

        return List.copyOf(intervals);
    }

    /**
     * Résout un couple (heureEntree, heureSortie) sur une date de référence donnée.
     * Si sortie < entree, candidat cross-midnight -> endDate = startDate + 1 jour.
     * Si sortie == entree -> rejet d'intervalle nul.
     * Si entree == null ou sortie == null -> retourne null (aucune invention d'heure).
     */
    public static WorkInterval resolveSingleInterval(
            Integer employeeId,
            Integer pointageId,
            LocalDate startDate,
            LocalTime entree,
            LocalTime sortie,
            String sourceRef
    ) {
        if (entree == null || sortie == null) return null;
        if (startDate == null) {
            throw new IllegalArgumentException("La date de début est obligatoire.");
        }

        LocalDateTime start = LocalDateTime.of(startDate, entree);
        LocalDateTime end;

        if (sortie.isBefore(entree)) {
            // Cross-midnight factuel : sortie le lendemain
            end = LocalDateTime.of(startDate.plusDays(1), sortie);
        } else if (sortie.equals(entree)) {
            throw new IllegalArgumentException("Intervalle nul : l'heure de sortie est identique à l'heure d'entrée (" + entree + ") le " + startDate);
        } else {
            end = LocalDateTime.of(startDate, sortie);
        }

        return new WorkInterval(start, end, employeeId, pointageId, sourceRef);
    }
}
