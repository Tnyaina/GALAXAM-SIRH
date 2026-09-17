package mg.galaxam.sirh.model.pointage.calculation;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Moteur de partitionnement temporel (Phase 1C-A).
 * Découpe les WorkInterval continus en segments temporels homogènes (TemporalBucket)
 * aux frontières strictes :
 * - 22:00 (début nuit)
 * - 00:00 (minuit : changement de date, samedi/dimanche, jour férié, frontière période 25/26)
 * - 05:00 (fin nuit)
 *
 * Responsabilité exclusive : OÙ se situent les minutes et QUELS faits temporels objectifs elles portent.
 * Ne décide ni des volumes HS (propriété Phase 1B) ni de leur valorisation monétaire (propriété Phase 1C-B).
 */
public class AttendanceTemporalPartitioner {

    public static final LocalTime NIGHT_START = LocalTime.of(22, 0);
    public static final LocalTime NIGHT_END = LocalTime.of(5, 0);

    private final Predicate<LocalDate> isPublicHoliday;
    private final Function<LocalDate, Integer> periodResolver;

    public AttendanceTemporalPartitioner(
            Predicate<LocalDate> isPublicHoliday,
            Function<LocalDate, Integer> periodResolver
    ) {
        this.isPublicHoliday = isPublicHoliday != null ? isPublicHoliday : d -> false;
        this.periodResolver = periodResolver != null ? periodResolver : d -> 1;
    }

    /**
     * Partitionne une liste d'intervalles de travail en TemporalBuckets homogènes.
     */
    public List<TemporalBucket> partition(List<WorkInterval> intervals) {
        if (intervals == null || intervals.isEmpty()) {
            return List.of();
        }

        List<TemporalBucket> allBuckets = new ArrayList<>();
        for (WorkInterval interval : intervals) {
            allBuckets.addAll(partitionSingleInterval(interval));
        }
        return Collections.unmodifiableList(allBuckets);
    }

    /**
     * Partitionne un unique WorkInterval continu aux frontières 22h, 00h, 05h.
     */
    public List<TemporalBucket> partitionSingleInterval(WorkInterval interval) {
        if (interval == null) {
            return List.of();
        }

        LocalDateTime start = interval.start();
        LocalDateTime end = interval.end();

        // 1. Collecter toutes les frontières temporelles potentielles entre start et end
        TreeSet<LocalDateTime> cutPoints = new TreeSet<>();
        cutPoints.add(start);
        cutPoints.add(end);

        LocalDate startDate = start.toLocalDate();
        LocalDate endDate = end.toLocalDate();

        // Parcourir de startDate à endDate + 1 pour être exhaustif
        LocalDate current = startDate;
        while (!current.isAfter(endDate.plusDays(1))) {
            // Frontière 00:00 (minuit / début de journée)
            LocalDateTime midnight = current.atStartOfDay();
            if (midnight.isAfter(start) && midnight.isBefore(end)) {
                cutPoints.add(midnight);
            }

            // Frontière 05:00 (fin de nuit légale)
            LocalDateTime morning05 = current.atTime(NIGHT_END);
            if (morning05.isAfter(start) && morning05.isBefore(end)) {
                cutPoints.add(morning05);
            }

            // Frontière 22:00 (début de nuit légale)
            LocalDateTime night22 = current.atTime(NIGHT_START);
            if (night22.isAfter(start) && night22.isBefore(end)) {
                cutPoints.add(night22);
            }

            current = current.plusDays(1);
        }

        // 2. Construire les segments temporels élémentaires
        List<LocalDateTime> orderedPoints = new ArrayList<>(cutPoints);
        List<TemporalBucket> buckets = new ArrayList<>();

        long totalSegmentMinutes = 0;

        for (int i = 0; i < orderedPoints.size() - 1; i++) {
            LocalDateTime segStart = orderedPoints.get(i);
            LocalDateTime segEnd = orderedPoints.get(i + 1);

            long segMinutes = Duration.between(segStart, segEnd).toMinutes();
            if (segMinutes <= 0) {
                continue;
            }

            totalSegmentMinutes += segMinutes;

            // Date d'imputation civile du segment (aucun segment ne traversant minuit, segStart.toLocalDate() est canonique)
            LocalDate segDate = segStart.toLocalDate();

            // Qualification calendaire : préserve formellement les coïncidences (dimanche férié)
            boolean isHoliday = isPublicHoliday.test(segDate);
            boolean isSun = segDate.getDayOfWeek() == DayOfWeek.SUNDAY;
            boolean isSat = segDate.getDayOfWeek() == DayOfWeek.SATURDAY;

            DaySpecialType dayType;
            if (isSun && isHoliday) {
                dayType = DaySpecialType.SUNDAY_AND_PUBLIC_HOLIDAY;
            } else if (isHoliday) {
                dayType = DaySpecialType.PUBLIC_HOLIDAY;
            } else if (isSun) {
                dayType = DaySpecialType.SUNDAY;
            } else if (isSat) {
                dayType = DaySpecialType.SATURDAY;
            } else {
                dayType = DaySpecialType.STANDARD;
            }

            // Qualification horaire : nuit [22h00 - 05h00[
            LocalTime startTime = segStart.toLocalTime();
            boolean isNight = !startTime.isBefore(NIGHT_START) || startTime.isBefore(NIGHT_END);
            NightWorkType nightType = isNight ? NightWorkType.NIGHT_UNCLASSIFIED : NightWorkType.NONE;

            // Résolution de la période de paie
            Integer periodId = periodResolver.apply(segDate);
            if (periodId == null) {
                throw new IllegalStateException("Période de paie introuvable pour la date: " + segDate);
            }

            buckets.add(new TemporalBucket(
                    segStart,
                    segEnd,
                    segMinutes,
                    dayType,
                    nightType,
                    periodId,
                    interval.pointageId()
            ));
        }

        // Invariant de conservation absolue des minutes
        long expectedIntervalMinutes = interval.durationMinutes();
        if (totalSegmentMinutes != expectedIntervalMinutes) {
            throw new IllegalStateException(
                    "CONSERVATION_VIOLATION: Somme des buckets (" + totalSegmentMinutes + " min) != durée intervalle (" +
                    expectedIntervalMinutes + " min) pour l'intervalle " + interval);
        }

        return Collections.unmodifiableList(buckets);
    }
}
