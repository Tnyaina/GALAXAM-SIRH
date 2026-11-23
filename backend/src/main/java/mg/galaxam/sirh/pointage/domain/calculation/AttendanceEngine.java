package mg.galaxam.sirh.pointage.domain.calculation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Calcul pur d'une journée : aucune requête, transaction, horloge ou écriture. */
@Component
public class AttendanceEngine {

    /** Rapprochement DAF des totaux hebdomadaires, en minutes entières. */
    public record PeriodBalance(
            int balanceMinutes,
            int absenceMinutes,
            int overtime30Minutes,
            int overtime50Minutes
    ) {
        /**
         * Rapprochement hebdomadaire des heures avec imputation en cascade des déficits.
         * <p>
         * <b>Règles & Formules :</b><br>
         * 1. <b>Solde net :</b> {@code Balance = (HS30 + HS50 - Deficit) + AjustementDAF}<br>
         * 2. <b>Absence retenue :</b> {@code AbsenceFinale = Balance < 0 ? -Balance : 0}<br>
         * 3. <b>Cascade d'absorption du déficit :</b><br>
         *    - Le déficit éponge d'abord les heures supplémentaires à 30 % :<br>
         *      {@code Reduction30 = min(HS30, Deficit)} ; {@code FinalHS30 = HS30 - Reduction30}<br>
         *    - Le reliquat de déficit non absorbé entame les heures à 50 % :<br>
         *      {@code Reste = Deficit - Reduction30} ; {@code FinalHS50 = max(0, HS50 - Reste)}.
         * </p>
         */
        public static PeriodBalance calculate(int hs30, int hs50, int deficit, int adjustment) {
            validerVolumesPositifs(hs30, hs50, deficit, 0);

            // 1. Solde global net (HS - Déficit + Ajustement DAF)
            int totalPositif = Math.addExact(hs30, hs50);
            int balance = Math.addExact(Math.subtractExact(totalPositif, deficit), adjustment);
            int absenceFinale = balance < 0 ? Math.negateExact(balance) : 0;

            // 2. Impact net sur les heures supplémentaires payables
            // Une réduction (déficit ou ajustement DAF négatif) absorbe d'abord la tranche à 30 %,
            // puis entame la tranche à 50 % si la première ne suffit pas.
            int netAdjustment = Math.subtractExact(adjustment, deficit);

            int finalHs30;
            int finalHs50;

            if (netAdjustment >= 0) {
                // Ajustement favorable : bonifie directement le premier palier
                finalHs30 = Math.addExact(hs30, netAdjustment);
                finalHs50 = hs50;
            } else {
                // Ajustement défavorable (netAdjustment < 0) : on puise d'abord dans les 30 %
                int reductionTotale = Math.negateExact(netAdjustment); // valeur positive à imputer
                int reductionSur30 = Math.min(hs30, reductionTotale);
                finalHs30 = hs30 - reductionSur30;

                // Le reste non couvert par les 30 % vient réduire les 50 %
                int resteAReduire = reductionTotale - reductionSur30;
                finalHs50 = Math.max(0, hs50 - resteAReduire);
            }

            return new PeriodBalance(balance, absenceFinale, finalHs30, finalHs50);
        }

        /**
         * Rapprochement complet prenant en compte le statut Cadre (non éligible aux HS)
         * et les excédents non payables (qui compensent le déficit sans générer de paiement HS).
         */
        public static PeriodBalance calculate(int hs30, int hs50, int nonPayableSurplus,
                int deficit, int adjustment, boolean overtimeEligible) {
            validerVolumesPositifs(hs30, hs50, deficit, nonPayableSurplus);

            // 1. Le surplus non payable (cadre) commence par éponger le déficit
            int deficitRestant = Math.max(0, Math.subtractExact(deficit, nonPayableSurplus));

            // 2. Calcul du solde global net
            int totalHeuresPositives = Math.addExact(Math.addExact(hs30, hs50), nonPayableSurplus);
            int balance = Math.addExact(Math.subtractExact(totalHeuresPositives, deficit), adjustment);
            int absenceFinale = balance < 0 ? Math.negateExact(balance) : 0;

            // 3. Calcul des HS payables selon l'éligibilité
            if (!overtimeEligible) {
                // Cadre non éligible : aucune HS n'est payable, mais le solde global est préservé
                return new PeriodBalance(balance, absenceFinale, 0, 0);
            }

            // Salarié éligible : on calcule les paliers sur le déficit restant après absorption du surplus
            PeriodBalance payable = calculate(hs30, hs50, deficitRestant, adjustment);
            return new PeriodBalance(balance, absenceFinale, payable.overtime30Minutes(), payable.overtime50Minutes());
        }

        private static void validerVolumesPositifs(int hs30, int hs50, int deficit, int nonPayableSurplus) {
            if (hs30 < 0 || hs50 < 0 || deficit < 0 || nonPayableSurplus < 0) {
                throw new IllegalArgumentException("Volumes de rapprochement invalides.");
            }
        }
    }

    public record Context(
            Integer employeeId,
            LocalDate date,
            LocalTime morningEntry,
            LocalTime morningExit,
            LocalTime afternoonEntry,
            LocalTime afternoonExit,
            LocalTime scheduledMorningEntry,
            LocalTime scheduledMorningExit,
            LocalTime scheduledAfternoonEntry,
            LocalTime scheduledAfternoonExit,
            LocalTime nightStartsAt,
            LocalTime nightEndsAt,
            boolean continuousSchedule,
            boolean autonomous,
            boolean publicHoliday,
            LeavePart leavePart,
            boolean mealGranted,
            BigDecimal mealAmount,
            BigDecimal travelUnits,
            BigDecimal travelUnitAmount,
            BigDecimal mealTravelDeduction,
            boolean applyMealTravelDeduction
    ) {
        public enum LeavePart { NONE, MORNING, AFTERNOON, FULL_DAY }

        public Context {
            leavePart = leavePart == null ? LeavePart.NONE : leavePart;
            mealAmount = mealAmount == null ? BigDecimal.ZERO : mealAmount;
            travelUnits = travelUnits == null ? BigDecimal.ZERO : travelUnits;
            travelUnitAmount = travelUnitAmount == null ? BigDecimal.ZERO : travelUnitAmount;
            mealTravelDeduction = mealTravelDeduction == null ? BigDecimal.ZERO : mealTravelDeduction;
            if (date == null) throw new IllegalArgumentException("La date de pointage est obligatoire.");
            if (mealAmount.signum() < 0 || travelUnits.signum() < 0
                    || travelUnitAmount.signum() < 0 || mealTravelDeduction.signum() < 0) {
                throw new IllegalArgumentException("Les quantités et montants journaliers ne peuvent pas être négatifs.");
            }
        }
    }

    public record Result(
            Integer employeeId,
            LocalDate date,
            Status status,
            long morningMinutes,
            long afternoonMinutes,
            long workedMinutes,
            long normalMinutes,
            long lateMinutes,
            long absenceMinutes,
            long nightMinutes,
            boolean sunday,
            boolean publicHoliday,
            int mealDays,
            BigDecimal mealAmount,
            BigDecimal travelUnits,
            BigDecimal travelAmount,
            List<String> anomalies
    ) {
        public enum Status {
            PRESENT, HALF_DAY, ABSENT, LEAVE, REST, PUBLIC_HOLIDAY_OFF,
            PUBLIC_HOLIDAY_WORKED, SUNDAY_WORKED, AUTONOMOUS, ANOMALY
        }

        public Result {
            if (morningMinutes < 0 || afternoonMinutes < 0 || workedMinutes < 0 || normalMinutes < 0
                    || lateMinutes < 0 || absenceMinutes < 0 || nightMinutes < 0) {
                throw new IllegalArgumentException("Une durée journalière ne peut pas être négative.");
            }
            mealAmount = mealAmount == null ? BigDecimal.ZERO : mealAmount;
            travelUnits = travelUnits == null ? BigDecimal.ZERO : travelUnits;
            travelAmount = travelAmount == null ? BigDecimal.ZERO : travelAmount;
            anomalies = anomalies == null ? List.of() : List.copyOf(anomalies);
        }
    }

    /** Seule formule d'indemnité déplacement; le résultat est en BigDecimal. */
    public BigDecimal calculateTravelAmount(BigDecimal fraction, BigDecimal dailyRate,
                                            boolean reduce, BigDecimal reduction) {
        BigDecimal amount = nonNegative(fraction).multiply(nonNegative(dailyRate));
        if (reduce) amount = amount.subtract(nonNegative(fraction).multiply(nonNegative(reduction))).max(BigDecimal.ZERO);
        // La politique d'arrondi est appliquée une seule fois à la frontière de persistance/export.
        return amount;
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        BigDecimal result = value == null ? BigDecimal.ZERO : value;
        if (result.signum() < 0) throw new IllegalArgumentException("Un montant d'indemnité ne peut pas être négatif.");
        return result;
    }

    public Result calculateDay(Context context) {
        TemporalAttendanceEngine.NightWindow today = context.nightStartsAt() == null || context.nightEndsAt() == null
                ? null : new TemporalAttendanceEngine.NightWindow(context.nightStartsAt(), context.nightEndsAt());
        return calculateDay(context, today);
    }

    public Result calculateDay(Context context, TemporalAttendanceEngine.NightWindow nextDayNightWindow) {
        List<String> anomalies = verifierPointages(context);
        boolean sunday = context.date().getDayOfWeek() == DayOfWeek.SUNDAY;
        boolean weekend = sunday || context.date().getDayOfWeek() == DayOfWeek.SATURDAY;

        long scheduledMinutes = calculerMinutesPrevues(context);
        boolean hasPunch = contientUnPointage(context);
        DureesTravaillees durees = calculerDureesTravaillees(context, scheduledMinutes, hasPunch, weekend);
        long workedMinutes = durees.total();
        long lateMinutes = context.autonomous() ? 0 : calculerMinutesRetard(context);
        long normalMinutes = Math.min(workedMinutes, scheduledMinutes);
        long absenceMinutes = calculerMinutesAbsence(context, scheduledMinutes, workedMinutes, hasPunch);
        long nightMinutes = calculerMinutesNuit(context, nextDayNightWindow);
        Result.Status status = determinerStatutTechnique(
                context, anomalies, sunday, weekend, hasPunch, workedMinutes, scheduledMinutes);
        IndemnitesJour indemnites = calculerIndemnitesJour(context, workedMinutes);

        return new Result(context.employeeId(), context.date(), status,
                durees.matin(), durees.apresMidi(), workedMinutes, normalMinutes, lateMinutes, absenceMinutes, nightMinutes,
                sunday, context.publicHoliday(), indemnites.joursRepas(), indemnites.montantRepas(),
                indemnites.unitesDeplacement(), indemnites.montantDeplacement(), anomalies);
    }

    private record DureesTravaillees(long matin, long apresMidi) {
        long total() { return matin + apresMidi; }
    }

    /** Calcule les heures pointées puis applique le congé entier, le férié et l'autonomie. */
    private DureesTravaillees calculerDureesTravaillees(Context c, long prevues,
            boolean pointe, boolean weekend) {
        long matin = calculerMinutesMatin(c);
        long apresMidi = calculerMinutesApresMidi(c);
        if (c.leavePart() == Context.LeavePart.FULL_DAY || (c.publicHoliday() && !pointe))
            return new DureesTravaillees(0, 0);
        if (c.autonomous() && !pointe && !weekend) {
            matin = calculerMinutesPrevuesMatin(c);
            return new DureesTravaillees(matin, prevues - matin);
        }
        return new DureesTravaillees(matin, apresMidi);
    }

    /** Retire du déficit les minutes couvertes par un congé ou un férié chômé. */
    private long calculerMinutesAbsence(Context c, long prevues, long travaillees, boolean pointe) {
        long absence = Math.max(0, prevues - travaillees);
        if (c.leavePart() == Context.LeavePart.FULL_DAY || (c.publicHoliday() && !pointe)) return 0;
        if (c.leavePart() == Context.LeavePart.MORNING)
            return Math.max(0, absence - calculerMinutesPrevuesMatin(c));
        if (c.leavePart() == Context.LeavePart.AFTERNOON)
            return Math.max(0, absence - (prevues - calculerMinutesPrevuesMatin(c)));
        return absence;
    }

    private record IndemnitesJour(int joursRepas, BigDecimal montantRepas,
            BigDecimal unitesDeplacement, BigDecimal montantDeplacement) {}

    /** Applique les conditions de présence et la réduction de déplacement liée au repas. */
    private IndemnitesJour calculerIndemnitesJour(Context c, long travaillees) {
        boolean eligible = travaillees > 0 && c.leavePart() != Context.LeavePart.FULL_DAY && !c.publicHoliday();
        int joursRepas = eligible && c.mealGranted() ? 1 : 0;
        BigDecimal montantRepas = joursRepas == 1 ? c.mealAmount() : BigDecimal.ZERO;
        BigDecimal unites = eligible ? c.travelUnits() : BigDecimal.ZERO;
        BigDecimal montant = unites.multiply(c.travelUnitAmount());
        if (c.applyMealTravelDeduction() && joursRepas == 1)
            montant = montant.subtract(unites.multiply(c.mealTravelDeduction())).max(BigDecimal.ZERO);
        return new IndemnitesJour(joursRepas, montantRepas, unites, montant);
    }

    /** Repère les heures manquantes ou incohérentes avant de calculer la journée. */
    private List<String> verifierPointages(Context c) {
        List<String> anomalies = new ArrayList<>();
        if (c.continuousSchedule()) {
            if ((c.morningEntry() == null) != (c.afternoonExit() == null)) {
                anomalies.add("Pointage de journée continue incomplet.");
            }
            return anomalies;
        }
        if (aPauseAutomatique(c)) return anomalies;
        verifierPaireHoraire(c.morningEntry(), c.morningExit(), "matin", anomalies);
        verifierPaireHoraire(c.afternoonEntry(), c.afternoonExit(), "après-midi", anomalies);
        if (!c.continuousSchedule() && c.morningExit() != null && c.afternoonEntry() != null
                && c.afternoonEntry().isBefore(c.morningExit())) {
            anomalies.add("L'entrée d'après-midi précède la sortie du matin.");
        }
        return anomalies;
    }

    private void verifierPaireHoraire(LocalTime start, LocalTime end, String segment, List<String> anomalies) {
        if ((start == null) != (end == null)) anomalies.add("Pointage " + segment + " incomplet.");
    }

    private long calculerMinutesMatin(Context c) {
        if (c.continuousSchedule()) return minutesEntre(c.morningEntry(), c.afternoonExit());
        if (aPauseAutomatique(c)) {
            return minutesEntre(c.morningEntry(), c.scheduledMorningExit());
        }
        return minutesEntre(c.morningEntry(), c.morningExit());
    }

    private long calculerMinutesApresMidi(Context c) {
        if (c.continuousSchedule()) return 0;
        if (aPauseAutomatique(c)) return minutesEntre(c.scheduledAfternoonEntry(), c.afternoonExit());
        return minutesEntre(c.afternoonEntry(), c.afternoonExit());
    }

    /** Durée de référence du site, hors pause pour un horaire fractionné. */
    private long calculerMinutesPrevues(Context c) {
        if (c.continuousSchedule()) {
            return minutesEntre(c.scheduledMorningEntry(), c.scheduledAfternoonExit());
        }
        return minutesEntre(c.scheduledMorningEntry(), c.scheduledMorningExit()) +
                minutesEntre(c.scheduledAfternoonEntry(), c.scheduledAfternoonExit());
    }

    private long calculerMinutesPrevuesMatin(Context c) {
        if (c.continuousSchedule()) return calculerMinutesPrevues(c);
        return minutesEntre(c.scheduledMorningEntry(), c.scheduledMorningExit());
    }

    /** Retard à l’arrivée et, si applicable, au retour de la pause. */
    private long calculerMinutesRetard(Context c) {
        long morning = minutesDeRetardPositives(c.scheduledMorningEntry(), c.morningEntry());
        if (c.continuousSchedule() || aPauseAutomatique(c)) return morning;
        long afternoon = calculerRetardApresMidi(c);
        return morning + afternoon;
    }

    /** La reprise tardive est mesurée sur la pause réellement prise lorsqu'elle débute tard. */
    private long calculerRetardApresMidi(Context c) {
        if (c.afternoonEntry() == null) return 0;
        if (c.morningExit() != null && c.scheduledMorningExit() != null && c.scheduledAfternoonEntry() != null
                && c.scheduledAfternoonEntry().isAfter(c.scheduledMorningExit())
                && c.morningExit().isAfter(c.scheduledMorningExit())
                && c.afternoonEntry().isAfter(c.morningExit())) {
            long scheduledBreak = minutesEntre(c.scheduledMorningExit(), c.scheduledAfternoonEntry());
            long actualBreak = minutesEntre(c.morningExit(), c.afternoonEntry());
            return Math.max(0, actualBreak - scheduledBreak);
        }
        return minutesDeRetardPositives(c.scheduledAfternoonEntry(), c.afternoonEntry());
    }

    private boolean aPauseAutomatique(Context c) {
        return !c.continuousSchedule() && c.morningEntry() != null && c.afternoonExit() != null
                && c.morningExit() == null && c.afternoonEntry() == null;
    }

    /** Mesure la part des intervalles travaillés comprise dans la plage de nuit. */
    private long calculerMinutesNuit(Context c, TemporalAttendanceEngine.NightWindow nextDayNightWindow) {
        if (c.leavePart() == Context.LeavePart.FULL_DAY) return 0;
        var intervals = TemporalAttendanceEngine.WorkIntervalFactory.createIntervals(
                c.employeeId(), null, c.date(), c.morningEntry(), c.morningExit(),
                c.afternoonEntry(), c.afternoonExit(), c.continuousSchedule(), "POINTAGE_JOUR");
        return TemporalAttendanceEngine.nightMinutes(intervals, c.nightStartsAt(), c.nightEndsAt(),
                nextDayNightWindow);
    }

    private Result.Status determinerStatutTechnique(Context c, List<String> anomalies,
            boolean sunday, boolean weekend, boolean hasPunch, long worked, long scheduled) {
        if (!anomalies.isEmpty()) return Result.Status.ANOMALY;
        if (c.leavePart() == Context.LeavePart.FULL_DAY) return Result.Status.LEAVE;
        if (c.publicHoliday()) return hasPunch ? Result.Status.PUBLIC_HOLIDAY_WORKED
                : Result.Status.PUBLIC_HOLIDAY_OFF;
        if (sunday && worked > 0) return Result.Status.SUNDAY_WORKED;
        if (weekend && worked == 0) return Result.Status.REST;
        if (c.autonomous() && !weekend) return Result.Status.AUTONOMOUS;
        if (worked == 0) return Result.Status.ABSENT;
        boolean oneSegmentOnly = !c.continuousSchedule()
                && ((c.morningEntry() != null && c.morningExit() != null)
                    != (c.afternoonEntry() != null && c.afternoonExit() != null));
        if (c.leavePart() != Context.LeavePart.NONE || oneSegmentOnly) {
            return Result.Status.HALF_DAY;
        }
        return Result.Status.PRESENT;
    }

    private boolean contientUnPointage(Context c) {
        return c.morningEntry() != null || c.morningExit() != null ||
                c.afternoonEntry() != null || c.afternoonExit() != null;
    }

    private long minutesDeRetardPositives(LocalTime expected, LocalTime actual) {
        if (expected == null || actual == null || !actual.isAfter(expected)) return 0;
        return Duration.between(expected, actual).toMinutes();
    }

    private long minutesEntre(LocalTime start, LocalTime end) {
        if (start == null || end == null) return 0;
        long minutes = Duration.between(start, end).toMinutes();
        return minutes >= 0 ? minutes : minutes + 24L * 60L;
    }

    // Calcul hebdomadaire : les surplus journaliers compensent les déficits
    // du même tronçon avant de devenir des heures supplémentaires.
    public record WeekContext(LocalDate weekStart, long expectedMinutes,
            long firstOvertimeBandMinutes, boolean overtimeEligible, long standardDayMinutes,
            java.util.Set<LocalDate> paidHalfDayLeaveDates) {
        public WeekContext(LocalDate weekStart, long expectedMinutes,
                long firstOvertimeBandMinutes, boolean overtimeEligible, long standardDayMinutes) {
            this(weekStart, expectedMinutes, firstOvertimeBandMinutes, overtimeEligible,
                    standardDayMinutes, java.util.Set.of());
        }
        public WeekContext {
            if (weekStart == null || expectedMinutes < 0 || firstOvertimeBandMinutes < 0 || standardDayMinutes < 0) {
                throw new IllegalArgumentException("Contexte hebdomadaire invalide.");
            }
            paidHalfDayLeaveDates = paidHalfDayLeaveDates == null ? java.util.Set.of() : java.util.Set.copyOf(paidHalfDayLeaveDates);
        }
    }

    /**
     * Ajuste le premier palier HS à la durée normale réellement attendue dans le tronçon.
     * Le palier paramétré reste inchangé pour une semaine normale ; un tronçon écourté par
     * les bornes de période, d'emploi, un jour férié chômé ou un congé en reçoit la part
     * proportionnelle. Les absences n'abaissent pas l'objectif attendu.
     */
    public static long prorateFirstOvertimeBand(long configuredBandMinutes,
            long expectedMinutes, long nominalWeekMinutes) {
        if (configuredBandMinutes < 0 || expectedMinutes < 0 || nominalWeekMinutes < 0) {
            throw new IllegalArgumentException("Seuil hebdomadaire HS invalide.");
        }
        if (nominalWeekMinutes == 0) return 0;
        return BigDecimal.valueOf(configuredBandMinutes)
                .multiply(BigDecimal.valueOf(Math.min(expectedMinutes, nominalWeekMinutes)))
                .divide(BigDecimal.valueOf(nominalWeekMinutes), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    public record WeekResult(LocalDate weekStart, long expectedMinutes, long workedMinutes,
            long dailySurplusMinutes, long compensatedMinutes, long grossOvertimeMinutes,
            long overtime30Minutes, long overtime50Minutes, long absenceMinutes,
            long lateMinutes, List<Result> days) {
        public WeekResult {
            days = days == null ? List.of() : List.copyOf(days);
        }
        public long totalWeekOvertimeMinutes() { return overtime30Minutes + overtime50Minutes; }
        public long totalWeekAbsenceMinutes() { return absenceMinutes; }
    }

    public WeekResult calculateWeek(WeekContext context, List<Result> days) {
        List<Result> safeDays = days == null ? List.of() : List.copyOf(days);
        TotauxJourneesSemaine totaux = cumulerTravailRetardEtSurplus(context, safeDays);
        CompensationHebdomadaire compensation = compenserSurplusEtDeficit(context, totaux);
        PaliersHeuresSupplementaires paliers = repartirHeuresSupplementaires(
                context, compensation.heuresSupplementairesBrutes());
        return new WeekResult(context.weekStart(), context.expectedMinutes(), totaux.travaillees(),
                totaux.surplusJournaliers(), compensation.minutesCompensees(),
                compensation.heuresSupplementairesBrutes(),
                paliers.trentePourCent(), paliers.cinquantePourCent(), compensation.deficitRestant(),
                totaux.retard(), safeDays);
    }

    private record TotauxJourneesSemaine(long travaillees, long retard, long surplusJournaliers) {}

    /** Cumule les minutes réalisées et les excédents de chaque journée compensable. */
    private TotauxJourneesSemaine cumulerTravailRetardEtSurplus(WeekContext context, List<Result> jours) {
        long worked = 0;
        long late = 0;
        long dailySurplus = 0;
        for (Result day : jours) {
            worked += day.workedMinutes();
            late += day.lateMinutes();
            if (isCompensableWorkingDay(day)) {
                dailySurplus += Math.max(0,
                        day.workedMinutes() - dailyTarget(day, context.standardDayMinutes(),
                                context.paidHalfDayLeaveDates().contains(day.date())));
            }
        }
        return new TotauxJourneesSemaine(worked, late, dailySurplus);
    }

    private record CompensationHebdomadaire(long minutesCompensees,
            long heuresSupplementairesBrutes, long deficitRestant) {}

    /** Compense d'abord le déficit avec les surplus, puis conserve l'excédent net. */
    private CompensationHebdomadaire compenserSurplusEtDeficit(WeekContext contexte,
            TotauxJourneesSemaine totaux) {
        long deficitAvantCompensation = Math.max(0,
                contexte.expectedMinutes() - totaux.travaillees() + totaux.surplusJournaliers());
        long minutesCompensees = Math.min(totaux.surplusJournaliers(), deficitAvantCompensation);
        long heuresSupplementairesBrutes = totaux.surplusJournaliers() - minutesCompensees;
        long deficitRestant = deficitAvantCompensation - minutesCompensees;
        return new CompensationHebdomadaire(minutesCompensees, heuresSupplementairesBrutes, deficitRestant);
    }

    private record PaliersHeuresSupplementaires(long trentePourCent, long cinquantePourCent) {}

    /** Répartit seulement les HS brutes éligibles sur les paliers 30 % et 50 %. */
    private PaliersHeuresSupplementaires repartirHeuresSupplementaires(WeekContext context,
            long heuresSupplementairesBrutes) {
        long eligibleOvertime = context.overtimeEligible() ? heuresSupplementairesBrutes : 0;
        long overtime30 = Math.min(eligibleOvertime, context.firstOvertimeBandMinutes());
        long overtime50 = Math.max(0, eligibleOvertime - overtime30);
        return new PaliersHeuresSupplementaires(overtime30, overtime50);
    }

    private boolean isCompensableWorkingDay(Result day) {
        return !day.sunday() && !day.publicHoliday()
                && day.status() != Result.Status.LEAVE && day.status() != Result.Status.REST
                && day.status() != Result.Status.PUBLIC_HOLIDAY_OFF;
    }

    private long dailyTarget(Result day, long standardDayMinutes, boolean paidHalfDayLeave) {
        if (day.date() != null && day.date().getDayOfWeek() == DayOfWeek.SATURDAY) return 0;
        return paidHalfDayLeave ? standardDayMinutes / 2 : standardDayMinutes;
    }

    /** SEMAINE ∩ PÉRIODE : chaque tronçon est autonome pour la compensation. */
    public record WeekSegment(int index, LocalDate start, LocalDate end, LocalDate monday, LocalDate sunday) {
        public WeekSegment {
            if (start == null || end == null || monday == null || sunday == null || start.isAfter(end)) {
                throw new IllegalArgumentException("Bornes du tronçon hebdomadaire invalides.");
            }
        }
        public boolean isTruncatedStart() { return start.isAfter(monday); }
        public boolean isTruncatedEnd() { return end.isBefore(sunday); }
    }

    public static List<WeekSegment> segmentWeeks(LocalDate dateDebut, LocalDate dateFin) {
        if (dateDebut == null || dateFin == null || dateDebut.isAfter(dateFin)) return List.of();
        List<WeekSegment> segments = new ArrayList<>();
        LocalDate cur = dateDebut;
        int idx = 1;
        while (!cur.isAfter(dateFin)) {
            LocalDate monday = cur.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate sunday = cur.with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
            LocalDate end = sunday.isAfter(dateFin) ? dateFin : sunday;
            segments.add(new WeekSegment(idx++, cur, end, monday, sunday));
            cur = end.plusDays(1);
        }
        return segments;
    }
}
