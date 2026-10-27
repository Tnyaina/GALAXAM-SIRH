package mg.galaxam.sirh.conge.application;

/** Règle partagée pour déterminer si une demande contribue aux lignes de congé payé en paie. */
public final class CongePayrollPolicy {
    private CongePayrollPolicy() {}

    public static boolean estRemunere(Boolean surchargeDemande, Boolean remunerationParDefautDuType) {
        Boolean valeurEffective = surchargeDemande != null ? surchargeDemande : remunerationParDefautDuType;
        return Boolean.TRUE.equals(valeurEffective);
    }
}
