package mg.galaxam.sirh.pointage.domain.calculation;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * Découpe canonique d'une période de paie en tronçons de semaines civiles ISO.
 * <p>
 * Règle métier GALAXAM :
 * {@code TRONÇON = SEMAINE ∩ PÉRIODE ∩ CALENDRIER APPLICABLE}.
 * <p>
 * Unifie le partitionnement entre {@code getSemainesPeriode} (affichage IHM, calcul global)
 * et {@code calculerSyntheseEmploye} (synthèse mensuelle par employé).
 * La frontière de période constitue une frontière dure : chaque tronçon est une unité
 * autonome de calcul et de compensation pour sa période de paie.
 */
public final class PeriodWeekSegmenter {

    /**
     * Représente un tronçon de semaine à l'intérieur d'une période de paie.
     *
     * @param index Numéro d'ordre du tronçon dans la période (1-indexé)
     * @param start Date de début du tronçon (max entre début de période et lundi de la semaine)
     * @param end Date de fin du tronçon (min entre fin de période et dimanche de la semaine)
     * @param monday Date du lundi civil ISO de la semaine de référence
     * @param sunday Date du dimanche civil ISO de la semaine de référence
     */
    public record Segment(int index, LocalDate start, LocalDate end, LocalDate monday, LocalDate sunday) {
        public Segment {
            if (start == null || end == null || monday == null || sunday == null) {
                throw new IllegalArgumentException("Les bornes du segment de semaine ne peuvent pas être nulles.");
            }
            if (start.isAfter(end)) {
                throw new IllegalArgumentException("start (" + start + ") ne peut pas être postérieur à end (" + end + ").");
            }
        }

        /**
         * Indique si ce tronçon commence après le lundi (période démarrée en milieu de semaine).
         */
        public boolean isTruncatedStart() {
            return start.isAfter(monday);
        }

        /**
         * Indique si ce tronçon se termine avant le dimanche (période coupée en cours de semaine).
         */
        public boolean isTruncatedEnd() {
            return end.isBefore(sunday);
        }
    }

    private PeriodWeekSegmenter() {}

    /**
     * Partitionne une période [dateDebut, dateFin] en tronçons de semaines civiles ISO.
     *
     * @param dateDebut Date de début de la période
     * @param dateFin Date de fin de la période
     * @return Liste ordonnée des segments de semaine
     */
    public static List<Segment> segment(LocalDate dateDebut, LocalDate dateFin) {
        if (dateDebut == null || dateFin == null || dateDebut.isAfter(dateFin)) {
            return List.of();
        }
        List<Segment> segments = new ArrayList<>();
        LocalDate cur = dateDebut;
        int idx = 1;
        while (!cur.isAfter(dateFin)) {
            LocalDate monday = cur.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate sunday = cur.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
            LocalDate start = cur;
            LocalDate end = sunday.isAfter(dateFin) ? dateFin : sunday;
            segments.add(new Segment(idx++, start, end, monday, sunday));
            cur = end.plusDays(1);
        }
        return segments;
    }
}
