package mg.galaxam.sirh.model.paie.engine;

/**
 * @deprecated Utiliser {@link SpecialPremiumCollisionPolicy} qui couvre l'ensemble des sujétions (Nuit, Dimanche, Jour Férié).
 */
@Deprecated
public enum SpecialDayCollisionPolicy {
    UNRESOLVED,
    MAX_PREMIUM,
    CUMUL_ADDITIF,
    HOLIDAY_ONLY;

    public SpecialPremiumCollisionPolicy toPremiumPolicy() {
        return SpecialPremiumCollisionPolicy.valueOf(this.name());
    }
}
