package mg.galaxam.sirh.model.paieconfig.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Moteur de calcul des cotisations sociales et de l'impôt sur les revenus salariaux (IRSA).
 * Réf. Code Général des Impôts (CGI) & Code de Prévoyance Sociale (CNaPS / OSTIE).
 */
@Component
public class CalculateurPaieEngine {

    public static final BigDecimal SMIG_DEFAUT = new BigDecimal("262680.00");
    public static final BigDecimal MINIMUM_PERCEPTION_IRSA = new BigDecimal("3000.00");
    public static final BigDecimal DEDUCTION_PAR_ENFANT = new BigDecimal("2000.00");

    public record CotisationResult(BigDecimal assiette, BigDecimal partSalariale, BigDecimal partPatronale) {}

    public record TrancheIrsa(BigDecimal seuilMin, BigDecimal seuilMax, double taux) {}

    /**
     * Calcule les cotisations CNaPS avec application du plafond légal (8 x SMIG).
     */
    public CotisationResult calculerCnaps(BigDecimal salaireBrut, BigDecimal smig, double tauxSalarial, double tauxPatronal) {
        if (salaireBrut == null || salaireBrut.compareTo(BigDecimal.ZERO) <= 0) {
            return new CotisationResult(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        BigDecimal smigRef = smig != null ? smig : SMIG_DEFAUT;
        BigDecimal plafond = smigRef.multiply(BigDecimal.valueOf(8));
        BigDecimal assiette = salaireBrut.min(plafond);

        BigDecimal partSalariale = assiette.multiply(BigDecimal.valueOf(tauxSalarial)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal partPatronale = assiette.multiply(BigDecimal.valueOf(tauxPatronal)).setScale(2, RoundingMode.HALF_UP);

        return new CotisationResult(assiette, partSalariale, partPatronale);
    }

    /**
     * Calcule les cotisations d'organisme de santé (OSTIE / AMIT / FUNHREUMA).
     */
    public CotisationResult calculerSante(BigDecimal salaireBrut, double tauxSalarial, double tauxPatronal) {
        if (salaireBrut == null || salaireBrut.compareTo(BigDecimal.ZERO) <= 0) {
            return new CotisationResult(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        BigDecimal partSalariale = salaireBrut.multiply(BigDecimal.valueOf(tauxSalarial)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal partPatronale = salaireBrut.multiply(BigDecimal.valueOf(tauxPatronal)).setScale(2, RoundingMode.HALF_UP);

        return new CotisationResult(salaireBrut, partSalariale, partPatronale);
    }

    /**
     * Calcule l'impôt brut progressif par tranches (LF 2026).
     * Les tranches doivent obligatoirement provenir de la configuration légale (BDD).
     * Aucun fallback silencieux n'est autorisé.
     */
    public BigDecimal calculerIrsaBrut(BigDecimal baseImposable, List<TrancheIrsa> tranches) {
        if (baseImposable == null || baseImposable.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (tranches == null || tranches.isEmpty()) {
            throw new IllegalStateException("Aucune tranche IRSA définie. Les barèmes IRSA doivent être configurés en base de données.");
        }

        BigDecimal totalImpot = BigDecimal.ZERO;
        for (TrancheIrsa t : tranches) {
            BigDecimal seuilMin = t.seuilMin();
            // Normalisation de la borne inférieure (ex: 350 001 Ar -> 350 000 Ar ou 350 000.01 Ar -> 350 000 Ar)
            if (seuilMin != null && seuilMin.compareTo(BigDecimal.ZERO) > 0) {
                if (seuilMin.remainder(BigDecimal.ONE).compareTo(new BigDecimal("0.01")) == 0) {
                    seuilMin = seuilMin.subtract(new BigDecimal("0.01"));
                } else if (seuilMin.remainder(BigDecimal.TEN).compareTo(BigDecimal.ONE) == 0) {
                    seuilMin = seuilMin.subtract(BigDecimal.ONE);
                }
            }
            if (seuilMin != null && baseImposable.compareTo(seuilMin) > 0) {
                BigDecimal maxTranche = t.seuilMax() != null ? t.seuilMax() : baseImposable;
                BigDecimal taxable = baseImposable.min(maxTranche).subtract(seuilMin);
                if (taxable.compareTo(BigDecimal.ZERO) > 0) {
                    double tauxEffectif = t.taux() >= 1.0 ? t.taux() / 100.0 : t.taux();
                    totalImpot = totalImpot.add(taxable.multiply(BigDecimal.valueOf(tauxEffectif)));
                }
            }
        }
        return totalImpot.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calcule l'IRSA progressif par tranches avec déduction pour charges familiales et minimum de perception.
     */
    public BigDecimal calculerIrsa(BigDecimal baseImposable, List<TrancheIrsa> tranches, int nbEnfantsACharge) {
        if (baseImposable == null || baseImposable.compareTo(BigDecimal.ZERO) <= 0) {
            return MINIMUM_PERCEPTION_IRSA;
        }

        BigDecimal impotBrut = calculerIrsaBrut(baseImposable, tranches);

        // Déduction par enfant à charge (2 000 Ar par enfant)
        if (nbEnfantsACharge > 0) {
            BigDecimal reduction = DEDUCTION_PAR_ENFANT.multiply(BigDecimal.valueOf(nbEnfantsACharge));
            impotBrut = impotBrut.subtract(reduction).max(BigDecimal.ZERO);
        }

        // Application du minimum de perception légal (3 000 Ar)
        return impotBrut.max(MINIMUM_PERCEPTION_IRSA).setScale(2, RoundingMode.HALF_UP);
    }
}
