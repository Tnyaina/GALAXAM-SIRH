package mg.galaxam.sirh.pointage.domain.calculation;

import mg.galaxam.sirh.pointage.domain.calculation.QualifiedTimeBucket.DaySpecialType;
import mg.galaxam.sirh.pointage.domain.calculation.QualifiedTimeBucket.NightWorkType;
import mg.galaxam.sirh.pointage.domain.calculation.QualifiedTimeBucket.OvertimeTier;
import java.time.*;
import java.util.*;
import java.util.function.*;

/** Pipeline temporel pur : intervalles, partition calendaire et projection HS. */
public final class TemporalAttendanceEngine {
    private TemporalAttendanceEngine() {}

    public record NightWindow(LocalTime start, LocalTime end) {
        public NightWindow {
            if (start == null || end == null || !start.isAfter(end))
                throw new IllegalArgumentException("La plage de nuit doit traverser minuit.");
        }
    }

    /** Même partition des minutes réelles pour la saisie, la synthèse et le gel. */
    public static long nightMinutes(List<WorkInterval> intervals, LocalTime start, LocalTime end) {
        return nightMinutes(intervals, start, end,
                start == null && end == null ? null : new NightWindow(start, end));
    }

    public static long nightMinutes(List<WorkInterval> intervals, LocalTime start, LocalTime end,
                                    NightWindow nextDayWindow) {
        if (start == null && end == null && nextDayWindow == null) return 0;
        if ((start == null) != (end == null))
            throw new IllegalArgumentException("Les deux bornes de nuit doivent être configurées ensemble.");
        NightWindow window = start == null ? null : new NightWindow(start, end);
        LocalDate firstDate = intervals == null || intervals.isEmpty() ? null : intervals.get(0).start().toLocalDate();
        return new AttendanceTemporalPartitioner(date -> false, date -> 1,
                date -> firstDate != null && date.isAfter(firstDate) ? nextDayWindow : window)
                .partition(intervals).stream().filter(TemporalBucket::isNight)
                .mapToLong(TemporalBucket::minutes).sum();
    }

    public record WorkInterval(
            LocalDateTime start,
            LocalDateTime end,
            Integer employeeId,
            Integer pointageId,
            String sourceRef
    ) {
        public WorkInterval {
            if (start == null || end == null) {
                throw new IllegalArgumentException("WorkInterval start et end doivent être non nuls.");
            }
            if (!end.isAfter(start)) {
                throw new IllegalArgumentException("WorkInterval end doit être strictement après start: " + start + " -> " + end);
            }
        }
    
        public long durationMinutes() {
            return Duration.between(start, end).toMinutes();
        }
    }

    public record TemporalBucket(
            LocalDateTime start,
            LocalDateTime end,
            long minutes,
            DaySpecialType dayType,
            NightWorkType nightType,
            Integer payrollPeriodId,
            Integer pointageId
    ) {
        public TemporalBucket {
            if (start == null || end == null) {
                throw new IllegalArgumentException("start et end doivent être non nuls.");
            }
            if (!end.isAfter(start)) {
                throw new IllegalArgumentException("end doit être strictement après start: " + start + " -> " + end);
            }
            if (minutes <= 0) {
                throw new IllegalArgumentException("minutes doit être strictement positif: " + minutes);
            }
            if (dayType == null) {
                throw new IllegalArgumentException("dayType ne peut pas être nul.");
            }
            if (nightType == null) {
                throw new IllegalArgumentException("nightType ne peut pas être nul.");
            }
            long expected = Duration.between(start, end).toMinutes();
            if (minutes != expected) {
                throw new IllegalArgumentException("minutes (" + minutes + ") incohérent avec la durée réelle entre " + start + " et " + end + " (" + expected + ")");
            }
        }
    
        public boolean isSunday() {
            return dayType.isSunday();
        }
    
        public boolean isPublicHoliday() {
            return dayType.isPublicHoliday();
        }
    
        public boolean isSaturday() {
            return dayType.isSaturday();
        }
    
        public boolean isNight() {
            return nightType != NightWorkType.NONE;
        }
    }

    public static final class WorkIntervalFactory {
    
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

    public static class AttendanceTemporalPartitioner {
    
        public static final LocalTime NIGHT_START = LocalTime.of(22, 0);
        public static final LocalTime NIGHT_END = LocalTime.of(5, 0);
    
        private final Predicate<LocalDate> isPublicHoliday;
        private final Function<LocalDate, Integer> periodResolver;
        private final Function<LocalDate, NightWindow> nightWindowResolver;
    
        public AttendanceTemporalPartitioner(
                Predicate<LocalDate> isPublicHoliday,
                Function<LocalDate, Integer> periodResolver
        ) {
            this(isPublicHoliday, periodResolver, date -> new NightWindow(NIGHT_START, NIGHT_END));
        }

        public AttendanceTemporalPartitioner(
                Predicate<LocalDate> isPublicHoliday,
                Function<LocalDate, Integer> periodResolver,
                Function<LocalDate, NightWindow> nightWindowResolver
        ) {
            this.isPublicHoliday = isPublicHoliday != null ? isPublicHoliday : d -> false;
            this.periodResolver = periodResolver != null ? periodResolver : d -> 1;
            this.nightWindowResolver = Objects.requireNonNull(nightWindowResolver);
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
    
                NightWindow window = nightWindowResolver.apply(current);
                if (window != null) {
                    LocalDateTime morningEnd = current.atTime(window.end());
                    if (morningEnd.isAfter(start) && morningEnd.isBefore(end)) cutPoints.add(morningEnd);
                    LocalDateTime nightStart = current.atTime(window.start());
                    if (nightStart.isAfter(start) && nightStart.isBefore(end)) cutPoints.add(nightStart);
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
    
                // Qualification horaire selon les bornes valides à la date civile du segment.
                LocalTime startTime = segStart.toLocalTime();
                NightWindow window = nightWindowResolver.apply(segDate);
                boolean isNight = window != null
                        && (!startTime.isBefore(window.start()) || startTime.isBefore(window.end()));
                NightWorkType nightType = isNight ? NightWorkType.OCCASIONNEL : NightWorkType.NONE;
    
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

    public static class OvertimeProjectionEngine {
    
        /**
         * Projette chronologiquement les quotas Phase 1B sur les TemporalBuckets d'un tronçon.
         *
         * @param buckets            segments temporels issus du partitionnement (triés chronologiquement)
         * @param regularMinutes     volume régulier décidé par Phase 1B
         * @param overtime30Minutes  volume HS 30% décidé par Phase 1B
         * @param overtime50Minutes  volume HS 50% décidé par Phase 1B
         * @return liste des QualifiedTimeBucket immutables portant les deux dimensions (HS + temporel)
         */
        public List<QualifiedTimeBucket> project(
                List<TemporalBucket> buckets,
                long regularMinutes,
                long overtime30Minutes,
                long overtime50Minutes
        ) {
            if (regularMinutes < 0 || overtime30Minutes < 0 || overtime50Minutes < 0) {
                throw new IllegalArgumentException("Les quotas Phase 1B ne peuvent pas être négatifs : R=" +
                        regularMinutes + ", HS30=" + overtime30Minutes + ", HS50=" + overtime50Minutes);
            }
    
            long phase1bTotal = regularMinutes + overtime30Minutes + overtime50Minutes;
            long timelineTotal = (buckets == null) ? 0 : buckets.stream().mapToLong(TemporalBucket::minutes).sum();
    
            // Invariant strict : égalité parfaite entre la timeline et les volumes Phase 1B
            if (phase1bTotal != timelineTotal) {
                throw new IllegalStateException(
                        "CONSERVATION_VIOLATION_PROJECTION: Somme des quotas Phase 1B (" + phase1bTotal +
                        " min: R=" + regularMinutes + ", HS30=" + overtime30Minutes + ", HS50=" + overtime50Minutes +
                        ") != Somme des TemporalBuckets (" + timelineTotal + " min). Aucun fallback silencieux autorisé.");
            }
    
            if (timelineTotal == 0) {
                return List.of();
            }
    
            // Assurer le tri chronologique des buckets
            List<TemporalBucket> sortedBuckets = new ArrayList<>(buckets);
            sortedBuckets.sort(Comparator.comparing(TemporalBucket::start));
    
            long remRegular = regularMinutes;
            long remH30 = overtime30Minutes;
            long remH50 = overtime50Minutes;
    
            List<QualifiedTimeBucket> result = new ArrayList<>();
    
            for (TemporalBucket bucket : sortedBuckets) {
                LocalDateTime currentStart = bucket.start();
                long remBucketMins = bucket.minutes();
    
                while (remBucketMins > 0) {
                    OvertimeTier tier;
                    long availTierMins;
    
                    if (remRegular > 0) {
                        tier = OvertimeTier.REGULAR;
                        availTierMins = remRegular;
                    } else if (remH30 > 0) {
                        tier = OvertimeTier.OVERTIME_30;
                        availTierMins = remH30;
                    } else if (remH50 > 0) {
                        tier = OvertimeTier.OVERTIME_50;
                        availTierMins = remH50;
                    } else {
                        throw new IllegalStateException("Anomalie interne: Quotas épuisés mais minutes de bucket restantes (" + remBucketMins + " min)");
                    }
    
                    long sliceMins = Math.min(remBucketMins, availTierMins);
                    LocalDateTime currentEnd = currentStart.plusMinutes(sliceMins);
    
                    QualifiedTimeBucket qb = new QualifiedTimeBucket(
                            currentStart,
                            currentEnd,
                            sliceMins,
                            tier,
                            bucket.dayType(),
                            bucket.nightType(),
                            bucket.payrollPeriodId(),
                            bucket.pointageId()
                    );
                    result.add(qb);
    
                    if (tier == OvertimeTier.REGULAR) {
                        remRegular -= sliceMins;
                    } else if (tier == OvertimeTier.OVERTIME_30) {
                        remH30 -= sliceMins;
                    } else {
                        remH50 -= sliceMins;
                    }
    
                    remBucketMins -= sliceMins;
                    currentStart = currentEnd;
                }
            }
    
            // Post-conditions de validation
            if (remRegular != 0 || remH30 != 0 || remH50 != 0) {
                throw new IllegalStateException("Reliquat non projeté : remR=" + remRegular + ", remH30=" + remH30 + ", remH50=" + remH50);
            }
    
            long actualRegular = result.stream().filter(b -> b.overtimeTier() == OvertimeTier.REGULAR).mapToLong(QualifiedTimeBucket::minutes).sum();
            long actualH30 = result.stream().filter(b -> b.overtimeTier() == OvertimeTier.OVERTIME_30).mapToLong(QualifiedTimeBucket::minutes).sum();
            long actualH50 = result.stream().filter(b -> b.overtimeTier() == OvertimeTier.OVERTIME_50).mapToLong(QualifiedTimeBucket::minutes).sum();
    
            if (actualRegular != regularMinutes || actualH30 != overtime30Minutes || actualH50 != overtime50Minutes) {
                throw new IllegalStateException(
                        "DISCORDANCE_PROJECTION: Attendu [R=" + regularMinutes + ", HS30=" + overtime30Minutes + ", HS50=" + overtime50Minutes +
                        "] mais obtenu [R=" + actualRegular + ", HS30=" + actualH30 + ", HS50=" + actualH50 + "]");
            }
    
            return Collections.unmodifiableList(result);
        }
    }
}
