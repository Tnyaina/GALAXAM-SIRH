package mg.galaxam.sirh.contrat.api.dto;

import java.math.BigDecimal;
import java.sql.Date;

/** Modification d'un contrat brouillon ; seuls les champs fournis sont appliqués. */
public class ContratModificationRequest {
    private Integer idFiliale;
    private Integer idTypeContrat;
    private Integer idTypeIntervenant;
    private Date dateDebut;
    private Date dateFin;
    private Date dateSignature;
    private boolean comptabiliseEffectif = true;
    private String description;
    private String commentaire;

    // Affectation (optionnel)
    private Integer idSite;
    private Integer idDepartement;
    private Integer idService;
    private Integer idFonction;
    private Integer idResponsableHierarchique;
    private Integer idCategorieProfessionnelle;
    private Integer idModeCalculPaie;
    private BigDecimal salaireBase;
    private String descriptionAffectation;
    private String commentaireAffectation;

    /** Retourne true si au moins un champ d'affectation est fourni */
    public boolean hasAffectationData() {
        return idSite != null || idDepartement != null || idService != null
                || idFonction != null || idCategorieProfessionnelle != null
                || idModeCalculPaie != null || salaireBase != null;
    }

    public Integer getIdFiliale() {
        return idFiliale;
    }

    public void setIdFiliale(Integer idFiliale) {
        this.idFiliale = idFiliale;
    }

    public Integer getIdTypeContrat() {
        return idTypeContrat;
    }

    public void setIdTypeContrat(Integer idTypeContrat) {
        this.idTypeContrat = idTypeContrat;
    }

    public Integer getIdTypeIntervenant() {
        return idTypeIntervenant;
    }

    public void setIdTypeIntervenant(Integer idTypeIntervenant) {
        this.idTypeIntervenant = idTypeIntervenant;
    }

    public Date getDateDebut() {
        return dateDebut;
    }

    public void setDateDebut(Date dateDebut) {
        this.dateDebut = dateDebut;
    }

    public Date getDateFin() {
        return dateFin;
    }

    public void setDateFin(Date dateFin) {
        this.dateFin = dateFin;
    }

    public Date getDateSignature() {
        return dateSignature;
    }

    public void setDateSignature(Date dateSignature) {
        this.dateSignature = dateSignature;
    }

    public boolean isComptabiliseEffectif() {
        return comptabiliseEffectif;
    }

    public void setComptabiliseEffectif(boolean comptabiliseEffectif) {
        this.comptabiliseEffectif = comptabiliseEffectif;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCommentaire() {
        return commentaire;
    }

    public void setCommentaire(String commentaire) {
        this.commentaire = commentaire;
    }

    public Integer getIdSite() {
        return idSite;
    }

    public void setIdSite(Integer idSite) {
        this.idSite = idSite;
    }

    public Integer getIdDepartement() {
        return idDepartement;
    }

    public void setIdDepartement(Integer idDepartement) {
        this.idDepartement = idDepartement;
    }

    public Integer getIdService() {
        return idService;
    }

    public void setIdService(Integer idService) {
        this.idService = idService;
    }

    public Integer getIdFonction() {
        return idFonction;
    }

    public void setIdFonction(Integer idFonction) {
        this.idFonction = idFonction;
    }

    public Integer getIdResponsableHierarchique() {
        return idResponsableHierarchique;
    }

    public void setIdResponsableHierarchique(Integer idResponsableHierarchique) {
        this.idResponsableHierarchique = idResponsableHierarchique;
    }

    public Integer getIdCategorieProfessionnelle() {
        return idCategorieProfessionnelle;
    }

    public void setIdCategorieProfessionnelle(Integer idCategorieProfessionnelle) {
        this.idCategorieProfessionnelle = idCategorieProfessionnelle;
    }

    public Integer getIdModeCalculPaie() {
        return idModeCalculPaie;
    }

    public void setIdModeCalculPaie(Integer idModeCalculPaie) {
        this.idModeCalculPaie = idModeCalculPaie;
    }

    public BigDecimal getSalaireBase() {
        return salaireBase;
    }

    public void setSalaireBase(BigDecimal salaireBase) {
        this.salaireBase = salaireBase;
    }

    public String getDescriptionAffectation() {
        return descriptionAffectation;
    }

    public void setDescriptionAffectation(String descriptionAffectation) {
        this.descriptionAffectation = descriptionAffectation;
    }

    public String getCommentaireAffectation() {
        return commentaireAffectation;
    }

    public void setCommentaireAffectation(String commentaireAffectation) {
        this.commentaireAffectation = commentaireAffectation;
    }
}
