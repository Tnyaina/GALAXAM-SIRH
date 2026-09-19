package mg.galaxam.sirh.model.conge.engine;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO pour la saisie et l'enregistrement d'un Cutover Snapshot initial (Section 25 & 26).
 */
public class CongeCutoverOpeningRequestDTO {

    private Integer idEmploye;
    private LocalDate dateCutover;
    private CongeModeReprise modeReprise = CongeModeReprise.AGGREGATED_OPENING;
    private BigDecimal soldeJours;
    private BigDecimal tauxJournalierApplicable;
    private LocalDate dateDebutReference;
    private BigDecimal cumulDroitsOuverture;
    private BigDecimal cumulValorisationOuverture;
    private String sourceReprise = "REPRISE_CUTOVER";
    private String commentaire;

    public Integer getIdEmploye() { return idEmploye; }
    public void setIdEmploye(Integer idEmploye) { this.idEmploye = idEmploye; }
    public LocalDate getDateCutover() { return dateCutover; }
    public void setDateCutover(LocalDate dateCutover) { this.dateCutover = dateCutover; }
    public CongeModeReprise getModeReprise() { return modeReprise; }
    public void setModeReprise(CongeModeReprise modeReprise) { this.modeReprise = modeReprise; }
    public BigDecimal getSoldeJours() { return soldeJours; }
    public void setSoldeJours(BigDecimal soldeJours) { this.soldeJours = soldeJours; }
    public BigDecimal getTauxJournalierApplicable() { return tauxJournalierApplicable; }
    public void setTauxJournalierApplicable(BigDecimal tauxJournalierApplicable) { this.tauxJournalierApplicable = tauxJournalierApplicable; }
    public LocalDate getDateDebutReference() { return dateDebutReference; }
    public void setDateDebutReference(LocalDate dateDebutReference) { this.dateDebutReference = dateDebutReference; }
    public BigDecimal getCumulDroitsOuverture() { return cumulDroitsOuverture; }
    public void setCumulDroitsOuverture(BigDecimal cumulDroitsOuverture) { this.cumulDroitsOuverture = cumulDroitsOuverture; }
    public BigDecimal getCumulValorisationOuverture() { return cumulValorisationOuverture; }
    public void setCumulValorisationOuverture(BigDecimal cumulValorisationOuverture) { this.cumulValorisationOuverture = cumulValorisationOuverture; }
    public String getSourceReprise() { return sourceReprise; }
    public void setSourceReprise(String sourceReprise) { this.sourceReprise = sourceReprise; }
    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }
}
