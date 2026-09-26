package mg.galaxam.sirh.paie.domain.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import mg.galaxam.sirh.pointage.domain.calculation.OvertimeTier;
import mg.galaxam.sirh.pointage.domain.calculation.QualifiedTimeBucket;

/**
 * Applique le solde de période aux seules HS 30 % à payer. Les minutes de HS
 * utilisées pour compenser un déficit restent travaillées : leurs sujétions
 * (nuit, dimanche, férié) restent qualifiées, mais leur base HS n'est plus payée.
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

    private static QualifiedTimeBucket withTier(QualifiedTimeBucket source,
            java.time.LocalDateTime start, java.time.LocalDateTime end, long minutes, OvertimeTier tier) {
        return new QualifiedTimeBucket(start, end, minutes, tier, source.dayType(), source.nightType(),
                source.payrollPeriodId(), source.pointageId());
    }
}
