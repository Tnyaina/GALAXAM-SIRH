package mg.galaxam.sirh.paie.domain.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import mg.galaxam.sirh.paie.persistence.BulletinPaie;
import mg.galaxam.sirh.paie.persistence.BulletinPaieLigne;
import mg.galaxam.sirh.paie.domain.engine.IrsaCalculator;

/**
 * Conteneur complet d'un bulletin de paie généré avec ses lignes ventilées
 * et les impacts tracés sur les prêts et avances.
 */
public class BulletinPaieComplet {

    private BulletinPaie bulletin;
    private List<BulletinPaieLigne> lignes = new ArrayList<>();
    private ImpactPret impactPret;
    private List<Integer> idsAvancesImputees = new ArrayList<>();
    private List<MouvementImputation> imputationsMouvements = new ArrayList<>();
    private ExplicationPaieDTO explication;
    private ExplicationFiscaliteDTO explicationFiscalite;

    public static record ImpactPret(
            Integer idEcheance,
            Integer idPret,
            BigDecimal montantEcheanceInitiale,
            BigDecimal montantPreleve,
            BigDecimal montantRestantEcheance,
            String nouveauStatut,
            BigDecimal capitalRestantPretApres
    ) {}

    public static record MouvementImputation(Integer idMouvement, BigDecimal montantImpute) {}

    /**
     * DTO d'explication canonique backend-driven pour l'affichage transparent et le contrôle
     * des éléments de calcul sur le bulletin sans recalcul côté React (Phase Paie Finalization).
     */
    public static record ExplicationPaieDTO(
            long minutesAbsenceRetenues,
            BigDecimal heuresAbsenceRetenues,
            int minutesRetard,
            BigDecimal diviseurMensuelHeures,
            BigDecimal tauxHoraire,
            BigDecimal montantRetenueAbsence,
            BigDecimal nbJoursConge,
            BigDecimal salaireBaseContractuel,
            BigDecimal tauxJournalierTrentieme,
            BigDecimal deductionCongeTrentieme,
            BigDecimal tauxJournalierConge,
            BigDecimal indemniteConge,
            BigDecimal impactNetConge,
            BigDecimal heuresHs30,
            BigDecimal montantHs30,
            BigDecimal heuresHs50,
            BigDecimal montantHs50,
            BigDecimal heuresNuit,
            BigDecimal montantNuit,
            BigDecimal heuresDimanche,
            BigDecimal montantDimanche,
            BigDecimal heuresFerie,
            BigDecimal montantFerie
    ) {}

    public static record ExplicationFiscaliteDTO(
            BigDecimal revenuNetAvantImpot,
            BigDecimal remunerationHsExonereeIrsa,
            BigDecimal revenuNetImposable,
            BigDecimal assietteIrsa,
            BigDecimal irsaCalcule,
            BigDecimal irsaAvantReduction,
            BigDecimal reductionChargeFamille,
            BigDecimal minimumPerceptionApplicable,
            BigDecimal irsaDu,
            List<IrsaCalculator.TrancheDetail> detailsTranches
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
    public List<MouvementImputation> getImputationsMouvements() { return imputationsMouvements; }
    public void setImputationsMouvements(List<MouvementImputation> imputationsMouvements) { this.imputationsMouvements = imputationsMouvements; }

    public ExplicationPaieDTO getExplication() { return explication; }
    public void setExplication(ExplicationPaieDTO explication) { this.explication = explication; }

    public ExplicationFiscaliteDTO getExplicationFiscalite() { return explicationFiscalite; }
    public void setExplicationFiscalite(ExplicationFiscaliteDTO explicationFiscalite) { this.explicationFiscalite = explicationFiscalite; }
}
