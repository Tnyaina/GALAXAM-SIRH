package mg.galaxam.sirh.paie.domain.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Échéance contractuelle, choix DAF et limite du solde dû. */
public final class AdvanceRepaymentPolicy {
    private AdvanceRepaymentPolicy() {}

    public static BigDecimal echeance(BigDecimal montantInitial, int nombreMensualites, BigDecimal soldeRestant) {
        if (montantInitial == null || soldeRestant == null || nombreMensualites < 1 || soldeRestant.signum() <= 0)
            throw new IllegalArgumentException("Échéancier d'avance invalide.");
        return montantInitial.divide(BigDecimal.valueOf(nombreMensualites), 2, RoundingMode.UP).min(soldeRestant);
    }

    public static BigDecimal retenueDemandee(BigDecimal montantInitial, int nombreMensualites,
            BigDecimal soldeRestant, BigDecimal choix) {
        BigDecimal prevue = echeance(montantInitial, nombreMensualites, soldeRestant);
        return (choix == null ? prevue : choix).min(soldeRestant);
    }
}
