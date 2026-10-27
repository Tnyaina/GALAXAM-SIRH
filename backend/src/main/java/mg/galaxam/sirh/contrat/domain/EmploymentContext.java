package mg.galaxam.sirh.contrat.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Représente l'état canonique complet d'un collaborateur à une date donnée.
 * Vue datée commune du contrat et de l'affectation, utilisée par les parcours
 * qui appellent explicitement EmploymentContextResolver.
 */
public record EmploymentContext(
        // Identité Salarié
        int idEmploye,
        String matricule,
        String nom,
        String prenom,
        String nomComplet,
        String cinNumero,
        LocalDate cinDateDelivrance,
        String cinLieuDelivrance,
        String numAffiliationCnaps,
        LocalDate dateNaissance,
        boolean estActifEmploye,

        // Contrat en vigueur
        Integer idContrat,
        String numeroContrat,
        String typeContratCode,
        String typeContratLibelle,
        String statutContratCode,
        LocalDate dateDebutContrat,
        LocalDate dateFinContrat,
        LocalDate dateEmbaucheInitiale,
        String filialeNom,

        // Affectation de Carrière
        Integer idAffectation,
        LocalDate dateDebutAffectation,
        LocalDate dateFinAffectation,
        BigDecimal salaireBase,

        // Organisation & Structure
        Integer idSite,
        String codeSite,
        String nomSite,
        Integer idDepartement,
        String departementNom,
        Integer idService,
        String serviceNom,
        Integer idFonction,
        String codeFonction,
        String fonctionNom,
        boolean dispensePointage,

        // Catégorie Professionnelle & Mode de calcul
        Integer idCategoriePro,
        String categorieProCode,
        String categorieProLibelle,
        boolean estEligibleHs,
        boolean estCadre,
        boolean estEligibleExonerationHs,
        Integer idModeCalculPaie,
        String modeCalculPaieCode,

        // Régime Fiscal & Social
        String regimeCode,
        boolean soumisCnaps,
        boolean soumisSante,
        boolean soumisIrsa,
        boolean soumisIsi5,

        // Indicateur synthétique d'activité
        boolean estEnPoste
) {
    public boolean estActifAPaie() {
        return estEnPoste && salaireBase != null && salaireBase.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean estSoumisPointage() {
        return estEnPoste && !dispensePointage;
    }
}
