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
        BigDecimal cnapsEmployeeRate = rules.cnapsEmployee() != null ? rules.cnapsEmployee() : BigDecimal.ZERO;
        BigDecimal cnapsEmployerRate = rules.cnapsEmployer() != null ? rules.cnapsEmployer() : BigDecimal.ZERO;
        BigDecimal healthEmployeeRate = rules.healthEmployee() != null ? rules.healthEmployee() : BigDecimal.ZERO;
        BigDecimal healthEmployerRate = rules.healthEmployer() != null ? rules.healthEmployer() : BigDecimal.ZERO;
        BigDecimal fmfpRate = rules.fmfpRate() != null ? rules.fmfpRate() : BigDecimal.ZERO;

        boolean hasSocial = cnapsEmployeeRate.signum() > 0 || cnapsEmployerRate.signum() > 0
                || healthEmployeeRate.signum() > 0 || healthEmployerRate.signum() > 0 || fmfpRate.signum() > 0;
        if (hasSocial && (rules.plafondMensuel() == null || rules.plafondMensuel().signum() <= 0))
            throw new IllegalStateException("SOCIAL_CONFIGURATION_MISSING: plafond de cotisation mensuel");

        BigDecimal plafond = rules.plafondMensuel() != null ? rules.plafondMensuel() : BigDecimal.ZERO;
        BigDecimal cnapsBase = rules.plafondMensuel() != null ? cnapsAssiette.max(BigDecimal.ZERO).min(plafond) : cnapsAssiette.max(BigDecimal.ZERO);
        var cnaps = new Contribution(cnapsBase,
                MoneyRoundingPolicy.contribution(cnapsBase.multiply(cnapsEmployeeRate)),
                MoneyRoundingPolicy.contribution(cnapsBase.multiply(cnapsEmployerRate)));
        BigDecimal healthBase = (rules.capHealth() && rules.plafondMensuel() != null)
                ? healthAssiette.max(BigDecimal.ZERO).min(plafond)
                : healthAssiette.max(BigDecimal.ZERO);
        var health = new Contribution(healthBase,
                MoneyRoundingPolicy.contribution(healthBase.multiply(healthEmployeeRate)),
                MoneyRoundingPolicy.contribution(healthBase.multiply(healthEmployerRate)));
        BigDecimal fmfpBase = rules.plafondMensuel() != null ? fmfpAssiette.max(BigDecimal.ZERO).min(plafond) : fmfpAssiette.max(BigDecimal.ZERO);
        BigDecimal fmfp = MoneyRoundingPolicy.contribution(fmfpBase.multiply(fmfpRate));
        return new Result(cnaps, health, fmfp);
    }
}
