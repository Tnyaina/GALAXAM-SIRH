package mg.galaxam.sirh.pointage.domain.calculation;

/** Rapprochement DAF global, après calcul des semaines. Toutes les durées sont en minutes. */
public record PeriodBalance(int balanceMinutes, int absenceMinutes, int overtime30Minutes, int overtime50Minutes) {
    public static PeriodBalance calculate(int hs30, int hs50, int deficit, int adjustment, int firstBand) {
        if (hs30 < 0 || hs50 < 0 || deficit < 0 || firstBand < 0) {
            throw new IllegalArgumentException("Volumes de rapprochement invalides.");
        }
        int balance = Math.addExact(Math.subtractExact(Math.addExact(hs30, hs50), deficit), adjustment);
        int overtime = Math.max(0, balance);
        int tier30 = Math.min(overtime, firstBand);
        return new PeriodBalance(balance, balance < 0 ? Math.negateExact(balance) : 0,
                tier30, overtime - tier30);
    }
}
