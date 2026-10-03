package mg.galaxam.sirh.paie.domain.engine;


import java.math.BigDecimal;

/** Propriétaire unique de la valorisation sociale; paramètres déjà résolus. */
public final class SocialContributionCalculator {
    public record Rules(BigDecimal cnapsEmployee, BigDecimal cnapsEmployer,
                        BigDecimal healthEmployee, BigDecimal healthEmployer, BigDecimal fmfpRate,
                        BigDecimal plafondMensuel, boolean capHealth) {}
    public record Contribution(BigDecimal assiette, BigDecimal partSalariale, BigDecimal partPatronale) {}
    public record Result(Contribution cnaps,
                         Contribution health,
                         BigDecimal fmfp) {}
    public Result calculate(BigDecimal base, Rules rules) {
        return calculate(base, base, base, rules);
    }
    public Result calculate(BigDecimal cnapsAssiette, BigDecimal healthAssiette,
                            BigDecimal fmfpAssiette, Rules rules) {
        if (rules.plafondMensuel() == null || rules.plafondMensuel().signum() <= 0)
            throw new IllegalStateException("SOCIAL_CONFIGURATION_MISSING: plafond de cotisation mensuel");
        BigDecimal cnapsBase = cnapsAssiette.max(BigDecimal.ZERO).min(rules.plafondMensuel());
        var cnaps = new Contribution(cnapsBase,
                MoneyRoundingPolicy.contribution(cnapsBase.multiply(rules.cnapsEmployee())),
                MoneyRoundingPolicy.contribution(cnapsBase.multiply(rules.cnapsEmployer())));
        BigDecimal healthBase = rules.capHealth() ? healthAssiette.max(BigDecimal.ZERO).min(rules.plafondMensuel()) : healthAssiette.max(BigDecimal.ZERO);
        var health = new Contribution(healthBase,
                MoneyRoundingPolicy.contribution(healthBase.multiply(rules.healthEmployee())),
                MoneyRoundingPolicy.contribution(healthBase.multiply(rules.healthEmployer())));
        BigDecimal fmfpBase = fmfpAssiette.max(BigDecimal.ZERO).min(rules.plafondMensuel());
        BigDecimal fmfp = MoneyRoundingPolicy.contribution(fmfpBase.multiply(rules.fmfpRate()));
        return new Result(cnaps, health, fmfp);
    }
}
