package mg.galaxam.sirh.paie.domain.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import mg.galaxam.sirh.paie.persistence.BulletinPaie;
import mg.galaxam.sirh.paie.persistence.BulletinPaieLigne;
import mg.galaxam.sirh.paie.domain.engine.PayrollTaxCalculations;

/**
 * Conteneur complet d'un bulletin de paie généré avec ses lignes ventilées
 * et les impacts tracés sur les prêts et avances.
 */
public class BulletinPaieComplet {

    private BulletinPaie bulletin;
    private List<BulletinPaieLigne> lignes = new ArrayList<>();
    private List<Integer> idsAvancesImputees = new ArrayList<>();
    private List<MouvementImputation> imputationsMouvements = new ArrayList<>();
    private ExplicationPaieDTO explication;
    private ExplicationFiscaliteDTO explicationFiscalite;
    private CompositionBrutDTO compositionBrut;

    public static record CompositionBrutDTO(List<BulletinPaieLigne> lignes, BigDecimal totalLignes,
            BigDecimal gainsExoneresIrsa, BigDecimal ecartAvecTotalEnregistre, BigDecimal ecartArrondiNet) {}


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
            List<PayrollTaxCalculations.TrancheDetail> detailsTranches
    ) {}

    public BulletinPaieComplet() {}

    public BulletinPaieComplet(BulletinPaie bulletin, List<BulletinPaieLigne> lignes) {
        this.bulletin = bulletin;
        this.lignes = lignes != null ? lignes : new ArrayList<>();
        this.compositionBrut = composeBrut(this.bulletin, this.lignes);
    }

    private static CompositionBrutDTO composeBrut(BulletinPaie bulletin, List<BulletinPaieLigne> lignes) {
        List<BulletinPaieLigne> gross = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO, exempt = BigDecimal.ZERO;
        for (BulletinPaieLigne line : lignes) {
            boolean gainBrut = "GAIN".equals(line.getSens()) && (line.getEtapeCalcul() != null
                    ? "BRUT".equals(line.getEtapeCalcul())
                    : line.isImposable() && !"NET".equals(line.getCategorie())
                            && !"INDEMNITE_NON_IMPOSABLE".equals(line.getCategorie()));
            boolean retenueBrut = "RETENUE".equals(line.getSens()) && "SALAIRE_BASE".equals(line.getCategorie());
            if (gainBrut || retenueBrut) {
                gross.add(line);
                BigDecimal amount = line.getMontantSalarial() == null ? BigDecimal.ZERO : line.getMontantSalarial();
                total = total.add(gainBrut ? amount : amount.negate());
            }
            if ("GAIN".equals(line.getSens()) && "BRUT".equals(line.getEtapeCalcul()) && !line.isImposable()
                    && line.getMontantSalarial() != null) exempt = exempt.add(line.getMontantSalarial());
        }
        BigDecimal storedGross = bulletin == null || bulletin.getTotalGainsBruts() == null ? BigDecimal.ZERO : bulletin.getTotalGainsBruts();
        BigDecimal rounding = bulletin == null || bulletin.getNetAvantArrondi() == null || bulletin.getNetAPayer() == null
                ? null : bulletin.getNetAPayer().subtract(bulletin.getNetAvantArrondi()).setScale(2, RoundingMode.HALF_UP);
        return new CompositionBrutDTO(List.copyOf(gross), total, exempt, total.subtract(storedGross), rounding);
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
    public void setBulletin(BulletinPaie bulletin) { this.bulletin = bulletin; this.compositionBrut = composeBrut(this.bulletin, this.lignes); }

    public List<BulletinPaieLigne> getLignes() { return lignes; }
    public void setLignes(List<BulletinPaieLigne> lignes) { this.lignes = lignes == null ? new ArrayList<>() : lignes; this.compositionBrut = composeBrut(this.bulletin, this.lignes); }

    public List<Integer> getIdsAvancesImputees() { return idsAvancesImputees; }
    public void setIdsAvancesImputees(List<Integer> idsAvancesImputees) { this.idsAvancesImputees = idsAvancesImputees; }
    public List<MouvementImputation> getImputationsMouvements() { return imputationsMouvements; }
    public void setImputationsMouvements(List<MouvementImputation> imputationsMouvements) { this.imputationsMouvements = imputationsMouvements; }

    public ExplicationPaieDTO getExplication() { return explication; }
    public void setExplication(ExplicationPaieDTO explication) { this.explication = explication; }

    public ExplicationFiscaliteDTO getExplicationFiscalite() { return explicationFiscalite; }
    public void setExplicationFiscalite(ExplicationFiscaliteDTO explicationFiscalite) { this.explicationFiscalite = explicationFiscalite; }
    public CompositionBrutDTO getCompositionBrut() { return compositionBrut; }
    public void setCompositionBrut(CompositionBrutDTO compositionBrut) { this.compositionBrut = compositionBrut; }
}
