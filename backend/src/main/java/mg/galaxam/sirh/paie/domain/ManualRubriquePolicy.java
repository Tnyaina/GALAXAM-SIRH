package mg.galaxam.sirh.paie.domain;

import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.common.exception.SirhErrorCode;
import mg.galaxam.sirh.paie.persistence.PaieRubrique;
import mg.galaxam.sirh.paie.domain.engine.PayrollCalculatorRegistry;

/** Single owner for rules that allow a manually configured payroll rubrique. */
public final class ManualRubriquePolicy {
    public static final java.util.Set<String> CATEGORIES = ManualRubriqueCatalog.CATEGORIES;
    public static final java.util.Set<String> CONDITIONS = ManualRubriqueCatalog.CONDITIONS;
    public static final java.util.Set<String> METHODS = ManualRubriqueCatalog.METHODS;
    public static final java.util.Set<String> ROUNDING = ManualRubriqueCatalog.ROUNDING;

    private ManualRubriquePolicy() {}

    public static void validate(PaieRubrique rubrique, boolean requireEvidence) {
        if (rubrique.isEstFixe() || !CATEGORIES.contains(rubrique.getCategorie())
                || !"MONTANT".equals(rubrique.getTypeValeur())
                || !("GAIN".equals(rubrique.getSens()) || "RETENUE".equals(rubrique.getSens()))) {
            invalid("Seules les rubriques manuelles de gain ou de retenue en montant peuvent être créées ici.");
        }
        if (!"CONFIGURABLE".equals(PayrollCalculatorRegistry.describe(rubrique.getCode(), rubrique.getCategorie(),
                rubrique.isEstFixe(), rubrique.getEtapeCalcul()).niveau()))
            invalid("Cette rubrique appartient à un calculateur dédié. Utilisez son paramétrage métier.");
        if (rubrique.getDateFinEffet() != null && (rubrique.getDateDebutEffet() == null
                || rubrique.getDateFinEffet().isBefore(rubrique.getDateDebutEffet())))
            invalid("La fin de validité doit suivre le début de validité.");
        if (!CONDITIONS.contains(value(rubrique.getConditionApplication())))
            invalid("Condition d’application non prise en charge.");
        if (!METHODS.contains(value(rubrique.getMethodeCalcul())) || !ROUNDING.contains(value(rubrique.getPolitiqueArrondi())))
            invalid("Méthode ou politique d’arrondi non prise en charge.");
        if (!"BRUT".equals(rubrique.getEtapeCalcul()) && Boolean.TRUE.equals(rubrique.getSoumisFmfp()))
            invalid("Un ajout ou une retenue nette ne peut pas alimenter le FMFP.");
        if (rubrique.getSoumisFmfp() != null && rubrique.getSoumisFmfp() != rubrique.isSoumisCnaps())
            invalid("L'assiette FMFP doit suivre l'assiette sociale CNaPS pour une rubrique manuelle.");
        if (requireEvidence && (rubrique.getReferenceReglementaire() == null || rubrique.getReferenceReglementaire().isBlank()
                || rubrique.getDateDebutEffet() == null))
            invalid("La référence du traitement et sa date de début d'effet sont obligatoires.");
        if ("GAIN".equals(rubrique.getSens()) && !("BRUT".equals(rubrique.getEtapeCalcul()) || "GAIN_NET".equals(rubrique.getEtapeCalcul())))
            invalid("Choisissez l'étape du gain : brut ou ajout au net après impôt.");
        if ("GAIN_NET".equals(rubrique.getEtapeCalcul()) && (rubrique.isSoumisIrsa() || rubrique.isSoumisCnaps() || rubrique.isSoumisSante()))
            invalid("Un gain ajouté après impôt doit être hors assiettes. Pour un gain soumis ou partiellement exonéré, choisissez l'étape brut.");
        if ("RETENUE".equals(rubrique.getSens()) && !"RETENUE_NET".equals(rubrique.getEtapeCalcul()))
            invalid("Une retenue manuelle intervient sur le net après impôt.");
        if ("RETENUE".equals(rubrique.getSens()) && (rubrique.isSoumisCnaps() || rubrique.isSoumisSante() || rubrique.isSoumisIrsa()))
            invalid("Une retenue nette manuelle ne modifie pas les assiettes fiscales et sociales.");
    }

    private static String value(String value) { return value == null ? "" : value; }
    private static void invalid(String message) { throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, message); }
}
