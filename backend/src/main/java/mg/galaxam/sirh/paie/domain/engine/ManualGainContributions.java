package mg.galaxam.sirh.paie.domain.engine;

import java.math.BigDecimal;
import mg.galaxam.sirh.paie.domain.dto.PayrollCalculationInput.GainVariable;

/** Contributions indépendantes de la catégorie d'affichage, sans arrondi intermédiaire.
 */
public record ManualGainContributions(BigDecimal brut, BigDecimal social, BigDecimal sante,
                                      BigDecimal irsa, BigDecimal ajoutNet) {
    public static ManualGainContributions resolve(GainVariable gain) {
        BigDecimal montant = gain.getMontant() == null ? BigDecimal.ZERO
                : gain.getMontant().max(BigDecimal.ZERO);
        boolean brut = gain.isDansBrut();
        return new ManualGainContributions(brut ? montant : BigDecimal.ZERO,
                gain.isSoumisCotisation() ? montant : BigDecimal.ZERO,
                gain.isSoumisSante() ? montant : BigDecimal.ZERO,
                brut && gain.isImposable() ? montant : BigDecimal.ZERO,
                brut ? BigDecimal.ZERO : montant);
    }
    /** Le brut est déjà inclus dans l'assiette sociale initiale. */
    public BigDecimal correctionSociale() { return social.subtract(brut); }
    public BigDecimal correctionSante() { return sante.subtract(brut); }
    public BigDecimal exclusionIrsa() { return brut.subtract(irsa); }
}
