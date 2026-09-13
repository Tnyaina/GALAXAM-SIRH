package mg.galaxam.sirh.model.paie.engine;

import java.math.BigDecimal;

/** Propriétaire unique de la valorisation sociale; paramètres déjà résolus. */
public final class SocialContributionCalculator {
    public record Rules(BigDecimal cnapsEmployee, BigDecimal cnapsEmployer,
                        BigDecimal healthEmployee, BigDecimal healthEmployer, BigDecimal fmfpRate,
                        BigDecimal smig, boolean capHealth) {}
    public record Contribution(BigDecimal assiette, BigDecimal partSalariale, BigDecimal partPatronale) {}
    public record Result(Contribution cnaps,
                         Contribution health,
                         BigDecimal fmfp) {}
    public Result calculate(BigDecimal base, Rules rules) {
        BigDecimal cnapsBase = base.min(rules.smig().multiply(new BigDecimal("8")));
        var cnaps = new Contribution(cnapsBase,
                MoneyRoundingPolicy.contribution(cnapsBase.multiply(rules.cnapsEmployee())),
                MoneyRoundingPolicy.contribution(cnapsBase.multiply(rules.cnapsEmployer())));
        BigDecimal healthBase = rules.capHealth() ? base.min(rules.smig().multiply(new BigDecimal("8"))) : base;
        var health = new Contribution(healthBase,
                MoneyRoundingPolicy.contribution(healthBase.multiply(rules.healthEmployee())),
                MoneyRoundingPolicy.contribution(healthBase.multiply(rules.healthEmployer())));
        BigDecimal fmfp = MoneyRoundingPolicy.contribution(base.multiply(rules.fmfpRate()));
        return new Result(cnaps, health, fmfp);
    }
}
