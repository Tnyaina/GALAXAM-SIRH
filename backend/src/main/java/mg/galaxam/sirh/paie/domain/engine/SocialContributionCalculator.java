package mg.galaxam.sirh.paie.domain.engine;


import java.math.BigDecimal;

/**
 * Calculateur pur des cotisations sociales obligatoires à Madagascar (CNaPS, Santé/OSTIE, FMFP).
 * <p>
 * <b>Formules appliquées :</b><br>
 * 1. <b>CNaPS :</b><br>
 *    - {@code AssietteCNaPS = min(assietteSociale, plafondMensuel)} (Plafond légal = 8 × SME)<br>
 *    - {@code PartSalariale = AssietteCNaPS * cnapsEmployeeRate} (1%)<br>
 *    - {@code PartPatronale = AssietteCNaPS * cnapsEmployerRate} (13%)<br>
 * 2. <b>Santé (OSTIE / Organisme Médical) :</b><br>
 *    - {@code AssietteSante = capHealth ? min(assietteSante, plafondMensuel) : assietteSante}<br>
 *    - {@code PartSalariale = AssietteSante * healthEmployeeRate} (1%)<br>
 *    - {@code PartPatronale = AssietteSante * healthEmployerRate} (5%)<br>
 * 3. <b>FMFP (Fonds Malgache de Formation Professionnelle) :</b><br>
 *    - {@code AssietteFmfp = min(assietteFmfp, plafondMensuel)}<br>
 *    - {@code PartPatronale = AssietteFmfp * fmfpRate} (1% patronal exclusivement).
 * </p>
 */
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
