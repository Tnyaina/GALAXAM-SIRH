package mg.galaxam.sirh.paie.domain.engine;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import mg.galaxam.sirh.paie.domain.dto.PayrollCalculationInput;
import mg.galaxam.sirh.pointage.domain.calculation.QualifiedTimeBucket.NightWorkType;
import mg.galaxam.sirh.pointage.domain.calculation.QualifiedTimeBucket.OvertimeTier;
import mg.galaxam.sirh.pointage.domain.calculation.QualifiedTimeBucket;

/**
 * Moteur canonique de valorisation monétaire des temps qualifiés (Phase 1C-B.1).
 * Consomme exclusivement les QualifiedTimeBucket[] issus du snapshot scellé de pointage.
 *
 * Principes stricts de conformité :
 * 1. ZERO recalcul temporel (durées, 40h, seuils, partitions déjà scellés).
 * 2. ZERO double paiement de la base horaire :
 *    - Salarié mensuel normal : les heures REGULAR ont leur base comprise dans SALAIRE_BASE.
 *      payrollAdditionalAmount = 0 (ou uniquement l'incrément de sujétion).
 *    - Heures supplémentaires : la base horaire (1.00) n'est comptabilisée qu'une seule fois.
 * 3. Les collisions de sujétions (Nuit, Dimanche, Férié) suivent la politique explicitement
 *    transmise par le contexte de paie ; le résolveur de production applique le cumul additif validé DAF.
 * 4. Résolution historique par date réelle de bucket :
 *    - Un bucket ne traversant jamais minuit, il est valorisé au barème légal en vigueur à sa date civile exacte.
 * 5. Arrondis : exclusivement via MoneyRoundingPolicy.
 */
public final class OvertimePayCalculator {

    private static final BigDecimal SIXTY = new BigDecimal("60");

    /**
     * Taux applicables résolus depuis ref_taux_majoration pour la valorisation.
     */
    public record Rates(
            BigDecimal rateHs30,
            BigDecimal rateHs50,
            BigDecimal rateNuit,
            BigDecimal rateDimanche,
            BigDecimal rateFerie,
            SpecialPremiumCollisionPolicy collisionPolicy
    ) {
        public Rates(BigDecimal rateHs30, BigDecimal rateHs50, BigDecimal rateNuit, BigDecimal rateDimanche, BigDecimal rateFerie) {
            this(rateHs30, rateHs50, rateNuit, rateDimanche, rateFerie, SpecialPremiumCollisionPolicy.UNRESOLVED);
        }

        public Rates withCollisionPolicy(SpecialPremiumCollisionPolicy policy) {
            return new Rates(rateHs30, rateHs50, rateNuit, rateDimanche, rateFerie, policy);
        }
    }

    /**
     * Résultat consolidé de valorisation avec traçabilité unitaire des buckets.
     */
    public record Result(
            BigDecimal hs30,
            BigDecimal hs50,
            BigDecimal nuit,
            BigDecimal dimanche,
            BigDecimal ferie,
            List<BucketValuationResult> bucketDetails,
            BigDecimal totalHours
    ) {
        public Result(BigDecimal hs30, BigDecimal hs50, BigDecimal nuit, BigDecimal dimanche, BigDecimal ferie) {
            this(hs30, hs50, nuit, dimanche, ferie, List.of(), BigDecimal.ZERO);
        }

        public Result(BigDecimal hs30, BigDecimal hs50, BigDecimal nuit, BigDecimal ferie) {
            this(hs30, hs50, nuit, BigDecimal.ZERO, ferie, List.of(), BigDecimal.ZERO);
        }

        public BigDecimal total() {
            return hs30.add(hs50).add(nuit).add(dimanche).add(ferie);
        }
    }

    /**
     * Valorisation unitaire canonique avec résolveur de taux par date de bucket.
     */
    public Result calculate(List<QualifiedTimeBucket> buckets, BigDecimal hourlyRate, TemporalRateResolver rateResolver, Integer employeeId) {
        if (hourlyRate == null || hourlyRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Le taux horaire doit être positif ou nul.");
        }
        if (rateResolver == null) {
            throw new IllegalArgumentException("Le résolveur temporel de taux est obligatoire.");
        }

        if (buckets == null || buckets.isEmpty()) {
            return new Result(BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2),
                    BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2),
                    BigDecimal.ZERO.setScale(2), List.of(), BigDecimal.ZERO);
        }

        BigDecimal totalHs30 = BigDecimal.ZERO;
        BigDecimal totalHs50 = BigDecimal.ZERO;
        BigDecimal totalNuit = BigDecimal.ZERO;
        BigDecimal totalDimanche = BigDecimal.ZERO;
        BigDecimal totalFerie = BigDecimal.ZERO;
        BigDecimal totalMinutes = BigDecimal.ZERO;

        List<BucketValuationResult> details = new ArrayList<>();

        for (QualifiedTimeBucket bucket : buckets) {
            totalMinutes = totalMinutes.add(BigDecimal.valueOf(bucket.minutes()));
            BigDecimal hours = BigDecimal.valueOf(bucket.minutes()).divide(SIXTY, 8, RoundingMode.HALF_UP);

            // Résolution des taux à la date civile exacte du bucket (invariant : ne traverse jamais minuit)
            LocalDate bucketDate = bucket.start().toLocalDate();
            Rates rates = rateResolver.resolveRates(bucketDate);
            if (rates == null) {
                throw new IllegalStateException("Aucun taux résolu pour la date " + bucketDate);
            }

            // 1. Détermination de la composante Heures Supplémentaires
            BigDecimal otRate = BigDecimal.ZERO;
            BigDecimal overtimeBaseAmount = BigDecimal.ZERO;
            BigDecimal overtimePremiumAmount = BigDecimal.ZERO;

            if (bucket.overtimeTier() == OvertimeTier.OVERTIME_30) {
                otRate = nz(rates.rateHs30());
                overtimeBaseAmount = hours.multiply(hourlyRate);
                overtimePremiumAmount = hours.multiply(hourlyRate).multiply(otRate);
                totalHs30 = totalHs30.add(overtimeBaseAmount).add(overtimePremiumAmount);
            } else if (bucket.overtimeTier() == OvertimeTier.OVERTIME_50) {
                otRate = nz(rates.rateHs50());
                overtimeBaseAmount = hours.multiply(hourlyRate);
                overtimePremiumAmount = hours.multiply(hourlyRate).multiply(otRate);
                totalHs50 = totalHs50.add(overtimeBaseAmount).add(overtimePremiumAmount);
            }

            // 2. Traitement du travail de nuit
            // Les snapshots antérieurs peuvent porter NIGHT_UNCLASSIFIED ou HABITUEL.
            // La configuration active ne possède qu'un taux NUIT : toutes les plages nocturnes
            // sont valorisées comme travail occasionnel, sans arbitrage de sous-type.
            boolean isNight = bucket.nightType() != NightWorkType.NONE;
            boolean isSunday = bucket.isSunday();
            boolean isHoliday = bucket.isPublicHoliday();

            // 3. Articulation et collision des sujétions spéciales (Art. 3 Décret 72-226)
            int specialConditionsCount = (isNight ? 1 : 0) + (isSunday ? 1 : 0) + (isHoliday ? 1 : 0);

            BigDecimal nightRate = BigDecimal.ZERO;
            BigDecimal sundayRate = BigDecimal.ZERO;
            BigDecimal holidayRate = BigDecimal.ZERO;

            if (specialConditionsCount == 1) {
                if (isNight) {
                    nightRate = nz(rates.rateNuit());
                } else if (isSunday) {
                    sundayRate = nz(rates.rateDimanche());
                } else if (isHoliday) {
                    holidayRate = nz(rates.rateFerie());
                }
            } else if (specialConditionsCount > 1) {
                SpecialPremiumCollisionPolicy policy = rates.collisionPolicy() != null
                        ? rates.collisionPolicy()
                        : SpecialPremiumCollisionPolicy.UNRESOLVED;

                switch (policy) {
                    case UNRESOLVED -> throw new IllegalStateException(
                            "SPECIAL_PREMIUM_COLLISION_UNRESOLVED: Le bucket temporel [" + bucket.start() + " -> " +
                            bucket.end() + "] présente " + specialConditionsCount +
                            " sujétions spéciales simultanées sans politique d'arbitrage configurée.");
                    case MAX_PREMIUM -> {
                        BigDecimal rN = isNight ? nz(rates.rateNuit()) : BigDecimal.ZERO;
                        BigDecimal rS = isSunday ? nz(rates.rateDimanche()) : BigDecimal.ZERO;
                        BigDecimal rH = isHoliday ? nz(rates.rateFerie()) : BigDecimal.ZERO;

                        BigDecimal maxRate = rN.max(rS).max(rH);
                        if (rH.compareTo(maxRate) == 0 && rH.compareTo(BigDecimal.ZERO) > 0) {
                            holidayRate = rH;
                        } else if (rS.compareTo(maxRate) == 0 && rS.compareTo(BigDecimal.ZERO) > 0) {
                            sundayRate = rS;
                        } else {
                            nightRate = rN;
                        }
                    }
                    case CUMUL_ADDITIF -> {
                        if (isNight) {
                            nightRate = nz(rates.rateNuit());
                        }
                        if (isSunday) sundayRate = nz(rates.rateDimanche());
                        if (isHoliday) holidayRate = nz(rates.rateFerie());
                    }
                    case HOLIDAY_ONLY -> {
                        if (isHoliday) {
                            holidayRate = nz(rates.rateFerie());
                        } else if (isSunday) {
                            sundayRate = nz(rates.rateDimanche());
                        } else if (isNight) {
                            nightRate = nz(rates.rateNuit());
                        }
                    }
                }
            }

            // Calcul des montants d'incréments de sujétion
            BigDecimal nightPremiumAmount = hours.multiply(hourlyRate).multiply(nightRate);
            BigDecimal sundayPremiumAmount = hours.multiply(hourlyRate).multiply(sundayRate);
            BigDecimal holidayPremiumAmount = hours.multiply(hourlyRate).multiply(holidayRate);

            totalNuit = totalNuit.add(nightPremiumAmount);
            totalDimanche = totalDimanche.add(sundayPremiumAmount);
            totalFerie = totalFerie.add(holidayPremiumAmount);

            // Montant additionnel total injecté par la Paie
            BigDecimal payrollAdditionalAmount = overtimeBaseAmount
                    .add(overtimePremiumAmount)
                    .add(nightPremiumAmount)
                    .add(sundayPremiumAmount)
                    .add(holidayPremiumAmount);

            String explanation = String.format(
                    "Bucket %s->%s (%d min): Base=%s Ar, HS=+%s%% (%s Ar), Nuit=+%s%% (%s Ar), Dim=+%s%% (%s Ar), Ferie=+%s%% (%s Ar) -> PaieAjout=%s Ar",
                    bucket.start(), bucket.end(), bucket.minutes(),
                    overtimeBaseAmount,
                    otRate.multiply(new BigDecimal("100")), overtimePremiumAmount,
                    nightRate.multiply(new BigDecimal("100")), nightPremiumAmount,
                    sundayRate.multiply(new BigDecimal("100")), sundayPremiumAmount,
                    holidayRate.multiply(new BigDecimal("100")), holidayPremiumAmount,
                    payrollAdditionalAmount
            );

            details.add(new BucketValuationResult(
                    bucket,
                    MoneyRoundingPolicy.intermediate(hours),
                    hourlyRate,
                    overtimeBaseAmount,
                    overtimePremiumAmount,
                    nightPremiumAmount,
                    sundayPremiumAmount,
                    holidayPremiumAmount,
                    payrollAdditionalAmount,
                    otRate,
                    nightRate,
                    sundayRate,
                    holidayRate,
                    explanation
            ));
        }

        BigDecimal totalHoursWorked = totalMinutes.divide(SIXTY, 8, RoundingMode.HALF_UP);

        return new Result(
                MoneyRoundingPolicy.amount(totalHs30),
                MoneyRoundingPolicy.amount(totalHs50),
                MoneyRoundingPolicy.amount(totalNuit),
                MoneyRoundingPolicy.amount(totalDimanche),
                MoneyRoundingPolicy.amount(totalFerie),
                Collections.unmodifiableList(details),
                totalHoursWorked
        );
    }

    /**
     * Surcharge canonique pour barème statique sur l'ensemble de la liste.
     */
    public Result calculate(List<QualifiedTimeBucket> buckets, BigDecimal hourlyRate, Rates rates, Integer employeeId) {
        return calculate(buckets, hourlyRate, date -> rates, employeeId);
    }

    /**
     * Point d'entrée orchestré depuis PayrollCalculationInput.
     */
    public Result calculate(PayrollCalculationInput input, BigDecimal hourlyRate) {
        if (input != null && input.getAttendanceInput() != null
                && !input.getAttendanceInput().qualifiedTimeBuckets().isEmpty()) {

            TemporalRateResolver resolver = input.getTemporalRateResolver();
            if (resolver == null) {
                SpecialPremiumCollisionPolicy policy = input.getSpecialPremiumCollisionPolicy() != null
                        ? input.getSpecialPremiumCollisionPolicy()
                        : SpecialPremiumCollisionPolicy.UNRESOLVED;
                Rates staticRates = new Rates(
                        nz(input.getMajorationHs30()),
                        nz(input.getMajorationHs50()),
                        nz(input.getMajorationNuit()),
                        nz(input.getMajorationDimanche()),
                        nz(input.getMajorationFerie()),
                        policy
                );
                resolver = date -> staticRates;
            }

            var result = calculate(input.getAttendanceInput().qualifiedTimeBuckets(), hourlyRate, resolver, input.getIdEmploye());
            var attendance = input.getAttendanceInput();
            if (attendance.dafOvertime30Minutes() == 0 && attendance.dafOvertime50Minutes() == 0) return result;
            // Décision monétaire de fin de période, sans horaire ni sujétion inventés.
            if (input.getDateFinPeriode() == null) throw new IllegalStateException("Date de valorisation DAF absente.");
            var rates = resolver.resolveRates(input.getDateFinPeriode().toLocalDate());
            BigDecimal extra30 = BigDecimal.valueOf(attendance.dafOvertime30Minutes()).divide(SIXTY, 8, RoundingMode.HALF_UP)
                    .multiply(hourlyRate).multiply(BigDecimal.ONE.add(nz(rates.rateHs30())));
            BigDecimal extra50 = BigDecimal.valueOf(attendance.dafOvertime50Minutes()).divide(SIXTY, 8, RoundingMode.HALF_UP)
                    .multiply(hourlyRate).multiply(BigDecimal.ONE.add(nz(rates.rateHs50())));
            return new Result(MoneyRoundingPolicy.amount(result.hs30().add(extra30)),
                    MoneyRoundingPolicy.amount(result.hs50().add(extra50)), result.nuit(), result.dimanche(),
                    result.ferie(), result.bucketDetails(), result.totalHours());
        }

        // Rétrocompatibilité : calcul agrégé lorsque aucun bucket détaillé n'est fourni
        BigDecimal rate = nz(hourlyRate);
        BigDecimal h30 = nz(input != null ? input.getHeuresHs30() : null);
        BigDecimal h50 = nz(input != null ? input.getHeuresHs50() : null);
        BigDecimal night = nz(input != null ? input.getHeuresNuit() : null);
        BigDecimal sunday = nz(input != null ? input.getHeuresDimanche() : null);
        BigDecimal holiday = nz(input != null ? input.getHeuresFeriees() : null);

        BigDecimal rateHs30 = BigDecimal.ONE.add(nz(input != null ? input.getMajorationHs30() : null));
        BigDecimal rateHs50 = BigDecimal.ONE.add(nz(input != null ? input.getMajorationHs50() : null));
        BigDecimal rateNuit = nz(input != null ? input.getMajorationNuit() : null);
        BigDecimal rateDimanche = BigDecimal.ONE.add(nz(input != null ? input.getMajorationDimanche() : null));
        BigDecimal rateFerie = BigDecimal.ONE.add(nz(input != null ? input.getMajorationFerie() : null));

        return new Result(
                MoneyRoundingPolicy.amount(h30.multiply(rate).multiply(rateHs30)),
                MoneyRoundingPolicy.amount(h50.multiply(rate).multiply(rateHs50)),
                MoneyRoundingPolicy.amount(night.multiply(rate).multiply(rateNuit)),
                MoneyRoundingPolicy.amount(sunday.multiply(rate).multiply(rateDimanche)),
                MoneyRoundingPolicy.amount(holiday.multiply(rate).multiply(rateFerie))
        );
    }

    /**
     * Formule unifiée combinée avec politique de collision des sujétions.
     */
    public BigDecimal calculateCombinedHourRate(
            BigDecimal hourlyRate,
            BigDecimal majorationHs,
            boolean isSunday,
            BigDecimal majorationDimanche,
            boolean isHoliday,
            BigDecimal majorationFerie,
            boolean isNight,
            BigDecimal majorationNuit,
            SpecialPremiumCollisionPolicy collisionPolicy
    ) {
        BigDecimal factor = BigDecimal.ONE;

        if (majorationHs != null && majorationHs.compareTo(BigDecimal.ZERO) > 0) {
            factor = factor.add(majorationHs);
        }

        int specialCount = (isNight ? 1 : 0) + (isSunday ? 1 : 0) + (isHoliday ? 1 : 0);
        BigDecimal specialAddition = BigDecimal.ZERO;

        if (specialCount == 1) {
            if (isNight && majorationNuit != null) specialAddition = majorationNuit;
            if (isSunday && majorationDimanche != null) specialAddition = majorationDimanche;
            if (isHoliday && majorationFerie != null) specialAddition = majorationFerie;
        } else if (specialCount > 1) {
            SpecialPremiumCollisionPolicy policy = collisionPolicy != null ? collisionPolicy : SpecialPremiumCollisionPolicy.UNRESOLVED;
            switch (policy) {
                case UNRESOLVED -> throw new IllegalStateException(
                        "SPECIAL_PREMIUM_COLLISION_UNRESOLVED: Conflit de majorations spéciales sans politique d'arbitrage.");
                case MAX_PREMIUM -> {
                    BigDecimal n = isNight ? nz(majorationNuit) : BigDecimal.ZERO;
                    BigDecimal s = isSunday ? nz(majorationDimanche) : BigDecimal.ZERO;
                    BigDecimal h = isHoliday ? nz(majorationFerie) : BigDecimal.ZERO;
                    specialAddition = n.max(s).max(h);
                }
                case CUMUL_ADDITIF -> {
                    if (isNight) specialAddition = specialAddition.add(nz(majorationNuit));
                    if (isSunday) specialAddition = specialAddition.add(nz(majorationDimanche));
                    if (isHoliday) specialAddition = specialAddition.add(nz(majorationFerie));
                }
                case HOLIDAY_ONLY -> specialAddition = isHoliday ? nz(majorationFerie) : nz(majorationDimanche);
            }
        }

        factor = factor.add(specialAddition);
        return MoneyRoundingPolicy.amount(nz(hourlyRate).multiply(factor));
    }

    public BigDecimal calculateCombinedHourRate(
            BigDecimal hourlyRate,
            BigDecimal majorationHs,
            boolean isSunday,
            BigDecimal majorationDimanche,
            boolean isHoliday,
            BigDecimal majorationFerie,
            boolean isNight,
            BigDecimal majorationNuit
    ) {
        return calculateCombinedHourRate(hourlyRate, majorationHs, isSunday, majorationDimanche,
                isHoliday, majorationFerie, isNight, majorationNuit, SpecialPremiumCollisionPolicy.UNRESOLVED);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
