package mg.galaxam.sirh.pointage.domain.calculation;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Moteur de projection des quotas d'heures supplémentaires de la Phase 1B sur les segments temporels.
 *
 * RÈGLE D'OR (Non négociable) :
 * - Phase 1B décide COMBIEN de minutes sont REGULAR, OVERTIME_30, OVERTIME_50 (selon l'objectif réel du tronçon).
 * - Phase 1C-A décide OÙ elles se situent chronologiquement et QUELS faits temporels elles portent.
 * - INTERDICTION ABSOLUE de recalculer les seuils 40h / 2400m ici.
 */
public class OvertimeProjectionEngine {

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
