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
     * Résultat complet et traçable du calcul de l'impôt sur le revenu (IRSA).
     */
    public record IrsaResult(
        BigDecimal baseIrsaArrondie,
        BigDecimal irsaBrut,
        BigDecimal irsaArrondiDizaine,
        BigDecimal reductionEnfants,
        BigDecimal irsaNet
    ) {}

    /**
     * Arrondit l'assiette imposable à la centaine d'Ariary inférieure (CGI Madagascar).
     * Ex: 350 120 Ar -> 350 100 Ar
     */
    public static BigDecimal arrondirCentaineInferieure(BigDecimal montant) {
        if (montant == null || montant.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        long valLong = montant.setScale(0, RoundingMode.DOWN).longValue();
        long arrondie = (valLong / 100L) * 100L;
        return BigDecimal.valueOf(arrondie).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Arrondit l'impôt brut à la dizaine d'Ariary supérieure (CGI Madagascar).
     * Ex: 54 321.40 Ar -> 54 330.00 Ar
     */
    public static BigDecimal arrondirDizaineSuperieure(BigDecimal montant) {
        if (montant == null || montant.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return montant.divide(BigDecimal.TEN, 0, RoundingMode.CEILING).multiply(BigDecimal.TEN).setScale(2, RoundingMode.HALF_UP);
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
     * Calcule l'ensemble des composantes de l'IRSA de manière canonique et unifiée :
     * 1. Arrondi de la base à la centaine inférieure
     * 2. Application du barème progressif par tranches
     * 3. Arrondi de l'impôt à la dizaine supérieure
     * 4. Déduction pour charges de famille (nb enfants)
     * 5. Plafond du minimum de perception (3 000 Ar)
     * 6. Règle protectrice de l'Article 01.03.18 du CGI Malagasy (le net imposable après IRSA
     *    ne peut pas être inférieur au seuil d'exonération de 350 000 Ar).
     */
    public IrsaResult calculerIrsaDetaille(
            BigDecimal netImposable,
            List<TrancheIrsa> tranches,
            int nbEnfantsACharge,
            BigDecimal deductionParEnfant,
            BigDecimal minimumPerception,
            BigDecimal seuilExoneration
    ) {
        if (netImposable == null || netImposable.compareTo(BigDecimal.ZERO) <= 0) {
            BigDecimal zero = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            return new IrsaResult(zero, zero, zero, zero, zero);
        }

        BigDecimal baseArrondieCentaine = arrondirCentaineInferieure(netImposable);
        BigDecimal irsaBrut = calculerIrsaBrut(baseArrondieCentaine, tranches);
        BigDecimal irsaArrondiDizaine = arrondirDizaineSuperieure(irsaBrut);

        BigDecimal deducParEnfant = deductionParEnfant != null ? deductionParEnfant : DEDUCTION_PAR_ENFANT;
        BigDecimal reductionEnfants = deducParEnfant.multiply(BigDecimal.valueOf(Math.max(0, nbEnfantsACharge)));

        BigDecimal minPerception = minimumPerception != null ? minimumPerception : MINIMUM_PERCEPTION_IRSA;
        BigDecimal irsaNet = irsaArrondiDizaine.subtract(reductionEnfants).max(BigDecimal.ZERO);
        irsaNet = irsaNet.max(minPerception);

        // Règle fiscale protectrice (CGI Madagascar Art. 01.03.18) :
        if (seuilExoneration != null && seuilExoneration.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal revenuDisponibleAvantImpot = netImposable;
            BigDecimal netApresImpot = revenuDisponibleAvantImpot.subtract(irsaNet);
            if (netApresImpot.compareTo(seuilExoneration) < 0 && revenuDisponibleAvantImpot.compareTo(seuilExoneration) > 0) {
                BigDecimal maxImpotPossible = revenuDisponibleAvantImpot.subtract(seuilExoneration);
                irsaNet = irsaNet.min(maxImpotPossible).max(minPerception);
            }
        }

        return new IrsaResult(baseArrondieCentaine, irsaBrut, irsaArrondiDizaine, reductionEnfants, irsaNet.setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * Calcule l'IRSA progressif par tranches avec déduction pour charges familiales et minimum de perception.
     * Délègue à calculerIrsaDetaille avec les constantes par défaut.
     */
    public BigDecimal calculerIrsa(BigDecimal baseImposable, List<TrancheIrsa> tranches, int nbEnfantsACharge) {
        if (baseImposable == null || baseImposable.compareTo(BigDecimal.ZERO) <= 0) {
            return MINIMUM_PERCEPTION_IRSA;
        }
        return calculerIrsaDetaille(baseImposable, tranches, nbEnfantsACharge, DEDUCTION_PAR_ENFANT, MINIMUM_PERCEPTION_IRSA, new BigDecimal("350000.00")).irsaNet();
    }
}
