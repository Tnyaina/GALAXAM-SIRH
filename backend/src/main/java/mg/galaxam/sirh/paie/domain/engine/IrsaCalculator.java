package mg.galaxam.sirh.paie.domain.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
/** Calcul IRSA pur; le barème est résolu avant invocation. */
public final class IrsaCalculator {
  public record Tranche(BigDecimal seuilMin, BigDecimal seuilMax, BigDecimal taux) {}
  public record TrancheDetail(BigDecimal borneInferieure, BigDecimal borneSuperieure,
                              BigDecimal taux, BigDecimal montantTaxable, BigDecimal impotProduit) {}
  public record Result(BigDecimal assietteIrsa, BigDecimal irsaCalcule, BigDecimal irsaAvantReduction,
                       BigDecimal reductionChargeFamille, BigDecimal minimumPerceptionApplicable,
                       BigDecimal irsaDu, List<TrancheDetail> detailsTranches) {
    public BigDecimal baseIrsaArrondie() { return assietteIrsa; }
    public BigDecimal irsaBrut() { return irsaCalcule; }
    public BigDecimal irsaArrondiDizaine() { return irsaAvantReduction; }
    public BigDecimal reductionEnfants() { return reductionChargeFamille; }
    public BigDecimal irsaNet() { return irsaDu; }
  }
  public Result calculate(BigDecimal base, List<Tranche> tranches, int enfants, BigDecimal deduction, BigDecimal minimum) {
    if (tranches == null || tranches.isEmpty()) throw new IllegalStateException("IRSA_CONFIGURATION_MISSING");
    if (deduction == null || minimum == null) throw new IllegalStateException("IRSA_CONFIGURATION_MISSING");
    if (base == null || base.signum() <= 0) return new Result(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, minimum, BigDecimal.ZERO, List.of());
    BigDecimal assiette = MoneyRoundingPolicy.irsaTaxBaseLowerHundred(base);
    BigDecimal brut = BigDecimal.ZERO;
    BigDecimal borneInferieure = BigDecimal.ZERO;
    List<TrancheDetail> details = new ArrayList<>();
    for (Tranche t : tranches) {
      verifierTranche(t, borneInferieure);
      BigDecimal max = t.seuilMax() == null ? assiette : t.seuilMax();
      BigDecimal taxable = assiette.min(max).subtract(borneInferieure).max(BigDecimal.ZERO);
      BigDecimal rate = t.taux() == null ? BigDecimal.ZERO : (t.taux().compareTo(BigDecimal.ONE) >= 0 ? t.taux().movePointLeft(2) : t.taux());
      BigDecimal impotProduit = taxable.multiply(rate).setScale(2, RoundingMode.HALF_UP);
      brut = brut.add(impotProduit);
      if (taxable.signum() > 0) details.add(new TrancheDetail(borneInferieure, t.seuilMax(), rate, taxable, impotProduit));
      if (t.seuilMax() == null || assiette.compareTo(t.seuilMax()) <= 0) break;
      borneInferieure = t.seuilMax();
    }
    brut = brut.setScale(2, RoundingMode.HALF_UP);
    BigDecimal dizaine = MoneyRoundingPolicy.irsaCalculatedUpperTen(brut);
    BigDecimal reduction = deduction.multiply(BigDecimal.valueOf(Math.max(0,enfants)));
    BigDecimal net = dizaine.subtract(reduction).max(minimum);
    return new Result(assiette, brut, dizaine, reduction, minimum,
            net.setScale(2, RoundingMode.HALF_UP), List.copyOf(details));
  }

  private static void verifierTranche(Tranche tranche, BigDecimal borneInferieureAttendue) {
    if (tranche == null || tranche.seuilMin() == null || tranche.taux() == null) {
      throw new IllegalStateException("IRSA_BRACKET_CONFIGURATION_INVALID");
    }
    BigDecimal minimumConfigure = tranche.seuilMin();
    boolean canonique = minimumConfigure.compareTo(borneInferieureAttendue) == 0;
    boolean legacyInclusif = minimumConfigure.compareTo(borneInferieureAttendue.add(BigDecimal.ONE)) == 0;
    if (!canonique && !legacyInclusif) throw new IllegalStateException("IRSA_BRACKET_CONFIGURATION_NOT_CONTIGUOUS");
    if (tranche.seuilMax() != null && tranche.seuilMax().compareTo(borneInferieureAttendue) <= 0) {
      throw new IllegalStateException("IRSA_BRACKET_CONFIGURATION_INVALID");
    }
  }
}
