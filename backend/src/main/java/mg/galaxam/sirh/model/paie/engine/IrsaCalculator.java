package mg.galaxam.sirh.model.paie.engine;
import java.math.BigDecimal;
import java.util.List;
/** Calcul IRSA pur; le barème est résolu avant invocation. */
public final class IrsaCalculator {
  public record Tranche(BigDecimal seuilMin, BigDecimal seuilMax, BigDecimal taux) {}
  public record Result(BigDecimal baseIrsaArrondie, BigDecimal irsaBrut, BigDecimal irsaArrondiDizaine, BigDecimal reductionEnfants, BigDecimal irsaNet) {}
  public Result calculate(BigDecimal base, List<Tranche> tranches, int enfants, BigDecimal deduction, BigDecimal minimum, BigDecimal seuil) {
    if (tranches == null || tranches.isEmpty()) throw new IllegalStateException("IRSA_CONFIGURATION_MISSING");
    if (base == null || base.signum() <= 0) return new Result(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO);
    BigDecimal assiette = base.setScale(0, java.math.RoundingMode.DOWN).divide(BigDecimal.valueOf(100),0,java.math.RoundingMode.DOWN).multiply(BigDecimal.valueOf(100)).setScale(2);
    BigDecimal brut = BigDecimal.ZERO;
    for (Tranche t : tranches) {
      BigDecimal min = t.seuilMin() == null ? BigDecimal.ZERO : t.seuilMin();
      BigDecimal max = t.seuilMax() == null ? assiette : t.seuilMax();
      BigDecimal taxable = assiette.min(max).subtract(min).max(BigDecimal.ZERO);
      BigDecimal rate = t.taux() == null ? BigDecimal.ZERO : (t.taux().compareTo(BigDecimal.ONE) >= 0 ? t.taux().movePointLeft(2) : t.taux());
      brut = brut.add(taxable.multiply(rate));
    }
    brut = brut.setScale(2, java.math.RoundingMode.HALF_UP);
    BigDecimal dizaine = brut.divide(BigDecimal.TEN,0,java.math.RoundingMode.CEILING).multiply(BigDecimal.TEN).setScale(2);
    BigDecimal reduction = (deduction == null ? BigDecimal.ZERO : deduction).multiply(BigDecimal.valueOf(Math.max(0,enfants)));
    BigDecimal net = dizaine.subtract(reduction).max(minimum == null ? BigDecimal.ZERO : minimum);
    return new Result(assiette,brut,dizaine,reduction,net.setScale(2, java.math.RoundingMode.HALF_UP));
  }
}
