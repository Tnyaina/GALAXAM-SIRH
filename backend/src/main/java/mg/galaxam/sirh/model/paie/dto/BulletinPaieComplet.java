package mg.galaxam.sirh.model.paie.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import mg.galaxam.sirh.model.paie.BulletinPaie;
import mg.galaxam.sirh.model.paie.BulletinPaieLigne;

/**
 * Conteneur complet d'un bulletin de paie généré avec ses lignes ventilées
 * et les impacts tracés sur les prêts et avances.
 */
public class BulletinPaieComplet {

    private BulletinPaie bulletin;
    private List<BulletinPaieLigne> lignes = new ArrayList<>();
    private ImpactPret impactPret;
    private List<Integer> idsAvancesImputees = new ArrayList<>();

    public static record ImpactPret(
            Integer idEcheance,
            Integer idPret,
            BigDecimal montantEcheanceInitiale,
            BigDecimal montantPreleve,
            BigDecimal montantRestantEcheance,
            String nouveauStatut,
            BigDecimal capitalRestantPretApres
    ) {}

    public BulletinPaieComplet() {}

    public BulletinPaieComplet(BulletinPaie bulletin, List<BulletinPaieLigne> lignes) {
        this.bulletin = bulletin;
        this.lignes = lignes != null ? lignes : new ArrayList<>();
    }

    /**
     * Recherche une ligne de bulletin par son code rubrique.
     */
    public BulletinPaieLigne trouverLigne(String codeRubrique) {
        if (codeRubrique == null || lignes == null) return null;
        for (BulletinPaieLigne l : lignes) {
            if (codeRubrique.equalsIgnoreCase(l.getCodeRubrique())) {
                return l;
            }
        }
        return null;
    }

    /**
     * Retourne le montant salarial d'une ligne ou ZERO si absente.
     */
    public BigDecimal getMontantLigneSalariale(String codeRubrique) {
        BulletinPaieLigne l = trouverLigne(codeRubrique);
        return (l != null && l.getMontantSalarial() != null) ? l.getMontantSalarial() : BigDecimal.ZERO;
    }

    /**
     * Retourne le montant patronal d'une ligne ou ZERO si absente.
     */
    public BigDecimal getMontantLignePatronale(String codeRubrique) {
        BulletinPaieLigne l = trouverLigne(codeRubrique);
        return (l != null && l.getMontantPatronal() != null) ? l.getMontantPatronal() : BigDecimal.ZERO;
    }

    // Getters & Setters
    public BulletinPaie getBulletin() { return bulletin; }
    public void setBulletin(BulletinPaie bulletin) { this.bulletin = bulletin; }

    public List<BulletinPaieLigne> getLignes() { return lignes; }
    public void setLignes(List<BulletinPaieLigne> lignes) { this.lignes = lignes; }

    public ImpactPret getImpactPret() { return impactPret; }
    public void setImpactPret(ImpactPret impactPret) { this.impactPret = impactPret; }

    public List<Integer> getIdsAvancesImputees() { return idsAvancesImputees; }
    public void setIdsAvancesImputees(List<Integer> idsAvancesImputees) { this.idsAvancesImputees = idsAvancesImputees; }
}
