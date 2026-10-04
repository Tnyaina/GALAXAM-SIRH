package mg.galaxam.sirh.paie.domain.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import mg.galaxam.sirh.pointage.domain.calculation.QualifiedTimeBucket.OvertimeTier;
import mg.galaxam.sirh.pointage.domain.calculation.QualifiedTimeBucket;

/**
 * Reventile les heures supplémentaires sans modifier les faits de pointage.
 * apply conserve la règle des snapshots historiques ; applyPeriod rapproche HS30 et HS50.
 * Les sujétions restent qualifiées, y compris sur les minutes compensant un déficit.
 */
public final class PayrollOvertimeSettlement {
    private PayrollOvertimeSettlement() {}

    public static List<QualifiedTimeBucket> apply(List<QualifiedTimeBucket> source,
            long rawHs30Minutes, long payableHs30Minutes) {
        if (source == null || rawHs30Minutes < 0 || payableHs30Minutes < 0
                || payableHs30Minutes > rawHs30Minutes) {
            throw new IllegalArgumentException("Solde HS 30 % du snapshot invalide.");
        }
        List<QualifiedTimeBucket> ordered = source.stream()
                .sorted(Comparator.comparing(QualifiedTimeBucket::start)
                        .thenComparing(QualifiedTimeBucket::end))
                .toList();
        long bucketHs30 = ordered.stream()
                .filter(bucket -> bucket.overtimeTier() == OvertimeTier.OVERTIME_30)
                .mapToLong(QualifiedTimeBucket::minutes).sum();
        if (bucketHs30 != rawHs30Minutes) {
            throw new IllegalStateException("SNAPSHOT_HS30_INCOHERENT: les buckets HS 30 % ne correspondent pas au cumul hebdomadaire.");
        }

        List<QualifiedTimeBucket> result = new ArrayList<>();
        long remaining = payableHs30Minutes;
        for (QualifiedTimeBucket bucket : ordered) {
            if (bucket.overtimeTier() != OvertimeTier.OVERTIME_30) {
                result.add(bucket);
                continue;
            }
            long payable = Math.min(remaining, bucket.minutes());
            if (payable > 0) {
                result.add(withTier(bucket, bucket.start(), bucket.start().plusMinutes(payable),
                        payable, OvertimeTier.OVERTIME_30));
            }
            if (payable < bucket.minutes()) {
                result.add(withTier(bucket, bucket.start().plusMinutes(payable), bucket.end(),
                        bucket.minutes() - payable, OvertimeTier.REGULAR));
            }
            remaining -= payable;
        }
        if (remaining != 0) throw new IllegalStateException("SNAPSHOT_HS30_INCOHERENT: HS finales non attribuées.");
        return List.copyOf(result);
    }

    /** Reventile les HS de la période ; aucune minute travaillée ni sujétion n'est créée. */
    public static List<QualifiedTimeBucket> applyPeriod(List<QualifiedTimeBucket> source,
            long raw30, long raw50, long final30, long final50) {
        if (source == null || raw30 < 0 || raw50 < 0 || final30 < 0 || final50 < 0)
            throw new IllegalArgumentException("Rapprochement de période invalide.");
        long actual30 = source.stream().filter(b -> b.overtimeTier() == OvertimeTier.OVERTIME_30)
                .mapToLong(QualifiedTimeBucket::minutes).sum();
        long actual50 = source.stream().filter(b -> b.overtimeTier() == OvertimeTier.OVERTIME_50)
                .mapToLong(QualifiedTimeBucket::minutes).sum();
        if (actual30 != raw30 || actual50 != raw50)
            throw new IllegalStateException("SNAPSHOT_HS_INCOHERENT: ventilation hebdomadaire divergente.");
        long remaining30 = final30, remaining50 = final50;
        List<QualifiedTimeBucket> result = new ArrayList<>();
        for (var bucket : source.stream().sorted(Comparator.comparing(QualifiedTimeBucket::start)
                .thenComparing(QualifiedTimeBucket::end)).toList()) {
            if (bucket.overtimeTier() == OvertimeTier.REGULAR) { result.add(bucket); continue; }
            long at30 = Math.min(remaining30, bucket.minutes());
            long at50 = Math.min(remaining50, bucket.minutes() - at30);
            var cursor = bucket.start();
            if (at30 > 0) { result.add(withTier(bucket, cursor, cursor.plusMinutes(at30), at30, OvertimeTier.OVERTIME_30)); cursor = cursor.plusMinutes(at30); }
            if (at50 > 0) { result.add(withTier(bucket, cursor, cursor.plusMinutes(at50), at50, OvertimeTier.OVERTIME_50)); cursor = cursor.plusMinutes(at50); }
            long regular = bucket.minutes() - at30 - at50;
            if (regular > 0) result.add(withTier(bucket, cursor, bucket.end(), regular, OvertimeTier.REGULAR));
            remaining30 -= at30; remaining50 -= at50;
        }
        // Le reliquat positif DAF est valorisé séparément, sans bucket temporel fictif.
        return List.copyOf(result);
    }

    private static QualifiedTimeBucket withTier(QualifiedTimeBucket source,
            java.time.LocalDateTime start, java.time.LocalDateTime end, long minutes, OvertimeTier tier) {
        return new QualifiedTimeBucket(start, end, minutes, tier, source.dayType(), source.nightType(),
                source.payrollPeriodId(), source.pointageId());
    }
}
