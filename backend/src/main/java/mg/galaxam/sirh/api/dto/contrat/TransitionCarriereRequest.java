package mg.galaxam.sirh.api.dto.contrat;

import java.sql.Date;

/**
 * DTO représentant la demande de création d'un workflow de transition de carrière / mutation.
 *
 * Types d'événements supportés :
 *  - RENOUVELLEMENT_CDD
 *  - TRANSFORMATION_CDI
 *  - EMBAUCHE_APRES_STAGE
 *  - MUTATION
 *  - PROMOTION
 *  - REVALORISATION
 */
public class TransitionCarriereRequest {

    private String codeTypeEvenement; // Requis (ex: TRANSFORMATION_CDI, MUTATION...)
    private Integer idContratSource;  // Id du contrat actif actuel (si concerné)
    private Integer idAffectationSource; // Id de l'affectation active actuelle

    // ─── NOUVEAU CONTRAT CIBLE (Optionnel si simple mutation/promotion sans nouveau contrat) ───
    private Integer idFiliale;
    private Integer idTypeContrat;
    private Integer idTypeIntervenant;
    private Date dateDebut;
    private Date dateFin;
    private Date dateSignature;
    private Integer dureeEssaiMois;
    private Date dateFinEssai;
    private Boolean comptabiliseEffectif;
    private String numeroContrat;
    private String descriptionContrat;
    private String commentaireContrat;

    // ─── NOUVELLE AFFECTATION CIBLE ───
    private Integer idSite;
    private Integer idDepartement;
    private Integer idService;
    private Integer idFonction;
    private Integer idResponsableHierarchique;
    private Integer idCategorieProfessionnelle;
    private Integer idModeCalculPaie;
    private Double salaireBase;
    private String descriptionAffectation;
    private String commentaireAffectation;

    // ─── INFORMATIONS WORKFLOW ───
    private Date dateEffet; // Date de prise d'effet de la transition (obligatoire)
    private String motif;   // Motif/justification du mouvement

    public TransitionCarriereRequest() {}

    // Getters & Setters
    public String getCodeTypeEvenement() { return codeTypeEvenement; }
    public void setCodeTypeEvenement(String codeTypeEvenement) { this.codeTypeEvenement = codeTypeEvenement; }

    public Integer getIdContratSource() { return idContratSource; }
    public void setIdContratSource(Integer idContratSource) { this.idContratSource = idContratSource; }

    public Integer getIdAffectationSource() { return idAffectationSource; }
    public void setIdAffectationSource(Integer idAffectationSource) { this.idAffectationSource = idAffectationSource; }

    public Integer getIdFiliale() { return idFiliale; }
    public void setIdFiliale(Integer idFiliale) { this.idFiliale = idFiliale; }

    public Integer getIdTypeContrat() { return idTypeContrat; }
    public void setIdTypeContrat(Integer idTypeContrat) { this.idTypeContrat = idTypeContrat; }

    public Integer getIdTypeIntervenant() { return idTypeIntervenant; }
    public void setIdTypeIntervenant(Integer idTypeIntervenant) { this.idTypeIntervenant = idTypeIntervenant; }

    public Date getDateDebut() { return dateDebut; }
    public void setDateDebut(Date dateDebut) { this.dateDebut = dateDebut; }

    public Date getDateFin() { return dateFin; }
    public void setDateFin(Date dateFin) { this.dateFin = dateFin; }

    public Date getDateSignature() { return dateSignature; }
    public void setDateSignature(Date dateSignature) { this.dateSignature = dateSignature; }

    public Integer getDureeEssaiMois() { return dureeEssaiMois; }
    public void setDureeEssaiMois(Integer dureeEssaiMois) { this.dureeEssaiMois = dureeEssaiMois; }

    public Date getDateFinEssai() { return dateFinEssai; }
    public void setDateFinEssai(Date dateFinEssai) { this.dateFinEssai = dateFinEssai; }

    public Boolean getComptabiliseEffectif() { return comptabiliseEffectif; }
    public void setComptabiliseEffectif(Boolean comptabiliseEffectif) { this.comptabiliseEffectif = comptabiliseEffectif; }

    public String getNumeroContrat() { return numeroContrat; }
    public void setNumeroContrat(String numeroContrat) { this.numeroContrat = numeroContrat; }

    public String getDescriptionContrat() { return descriptionContrat; }
    public void setDescriptionContrat(String descriptionContrat) { this.descriptionContrat = descriptionContrat; }

    public String getCommentaireContrat() { return commentaireContrat; }
    public void setCommentaireContrat(String commentaireContrat) { this.commentaireContrat = commentaireContrat; }

    public Integer getIdSite() { return idSite; }
    public void setIdSite(Integer idSite) { this.idSite = idSite; }

    public Integer getIdDepartement() { return idDepartement; }
    public void setIdDepartement(Integer idDepartement) { this.idDepartement = idDepartement; }

    public Integer getIdService() { return idService; }
    public void setIdService(Integer idService) { this.idService = idService; }

    public Integer getIdFonction() { return idFonction; }
    public void setIdFonction(Integer idFonction) { this.idFonction = idFonction; }

    public Integer getIdResponsableHierarchique() { return idResponsableHierarchique; }
    public void setIdResponsableHierarchique(Integer idResponsableHierarchique) { this.idResponsableHierarchique = idResponsableHierarchique; }

    public Integer getIdCategorieProfessionnelle() { return idCategorieProfessionnelle; }
    public void setIdCategorieProfessionnelle(Integer idCategorieProfessionnelle) { this.idCategorieProfessionnelle = idCategorieProfessionnelle; }

    public Integer getIdModeCalculPaie() { return idModeCalculPaie; }
    public void setIdModeCalculPaie(Integer idModeCalculPaie) { this.idModeCalculPaie = idModeCalculPaie; }

    public Double getSalaireBase() { return salaireBase; }
    public void setSalaireBase(Double salaireBase) { this.salaireBase = salaireBase; }

    public String getDescriptionAffectation() { return descriptionAffectation; }
    public void setDescriptionAffectation(String descriptionAffectation) { this.descriptionAffectation = descriptionAffectation; }

    public String getCommentaireAffectation() { return commentaireAffectation; }
    public void setCommentaireAffectation(String commentaireAffectation) { this.commentaireAffectation = commentaireAffectation; }

    public Date getDateEffet() { return dateEffet; }
    public void setDateEffet(Date dateEffet) { this.dateEffet = dateEffet; }

    public String getMotif() { return motif; }
    public void setMotif(String motif) { this.motif = motif; }
}
