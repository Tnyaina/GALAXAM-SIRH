package mg.galaxam.sirh.api.dto.contrat;

import java.math.BigDecimal;
import java.sql.Date;

/**
 * DTO pour initier une EMBAUCHE_INITIALE.
 * Contient les informations du contrat + de l'affectation initiale.
 * Le workflow est créé automatiquement côté backend.
 */
public class EmbaucheInitialeRequest {

    // ─── Informations Contrat ──────────────────────────────────────────────────

    /** ID de la filiale employeuse */
    private Integer idFiliale;

    /** Type de contrat : CDD, CDI, STAGE, CONSULTANT */
    private Integer idTypeContrat;

    /** Type d'intervenant : PERMANENT, STAGIAIRE, TEMPORAIRE, CONSULTANT */
    private Integer idTypeIntervenant;

    /** Date de début du contrat (obligatoire) */
    private Date dateDebut;

    /** Date de fin (obligatoire pour CDD, STAGE, CONSULTANT — null pour CDI) */
    private Date dateFin;

    /** Date de signature du contrat (optionnel) */
    private Date dateSignature;

    /** Durée de la période d'essai en mois */
    private Integer dureeEssaiMois;

    /** Date de fin de la période d'essai */
    private Date dateFinEssai;

    /**
     * Si false, l'employé n'est pas comptabilisé dans les effectifs
     * (ex : stagiaires, consultants retraités).
     * Défaut : true
     */
    private boolean comptabiliseEffectif = true;

    /** Numéro de contrat personnalisé (si null, généré automatiquement : CTR-{ANNEE}-{SEQ}) */
    private String numeroContrat;

    /** Description libre du contrat (contexte, conditions particulières) */
    private String description;

    /** Commentaire administratif interne DAF */
    private String commentaire;

    // ─── Informations Affectation ──────────────────────────────────────────────

    /** Site d'affectation */
    private Integer idSite;

    /** Département d'affectation */
    private Integer idDepartement;

    /** Service d'affectation */
    private Integer idService;

    /** Fonction / poste occupé */
    private Integer idFonction;

    /** Responsable hiérarchique direct (optionnel) */
    private Integer idResponsableHierarchique;

    /** Catégorie professionnelle (CADRE, A, etc.) */
    private Integer idCategorieProfessionnelle;

    /** Mode de calcul de la paie (MENSUEL, HORAIRE, JOURNALIER) */
    private Integer idModeCalculPaie;

    /** Salaire de base (montant mensuel, horaire ou journalier selon le mode) */
    private BigDecimal salaireBase;

    /** Description de l'affectation */
    private String descriptionAffectation;

    /** Commentaire sur l'affectation */
    private String commentaireAffectation;

    // ─── Informations Workflow ─────────────────────────────────────────────────

    /** Date d'effet de l'embauche (souvent = dateDebut du contrat) */
    private Date dateEffet;

    /** Motif de l'embauche (libre) */
    private String motif;

    // ─── Getters & Setters ─────────────────────────────────────────────────────

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

    public boolean isComptabiliseEffectif() { return comptabiliseEffectif; }
    public void setComptabiliseEffectif(boolean comptabiliseEffectif) { this.comptabiliseEffectif = comptabiliseEffectif; }

    public String getNumeroContrat() { return numeroContrat; }
    public void setNumeroContrat(String numeroContrat) { this.numeroContrat = numeroContrat; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }

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

    public BigDecimal getSalaireBase() { return salaireBase; }
    public void setSalaireBase(BigDecimal salaireBase) { this.salaireBase = salaireBase; }

    public String getDescriptionAffectation() { return descriptionAffectation; }
    public void setDescriptionAffectation(String descriptionAffectation) { this.descriptionAffectation = descriptionAffectation; }

    public String getCommentaireAffectation() { return commentaireAffectation; }
    public void setCommentaireAffectation(String commentaireAffectation) { this.commentaireAffectation = commentaireAffectation; }

    public Date getDateEffet() { return dateEffet; }
    public void setDateEffet(Date dateEffet) { this.dateEffet = dateEffet; }

    public String getMotif() { return motif; }
    public void setMotif(String motif) { this.motif = motif; }

    /** Code type événement (EMBAUCHE_INITIALE, REPRISE_ANCIENNETE, REPRISE_CONSULTANT). Si null, EMBAUCHE_INITIALE */
    private String codeTypeEvenement;

    /** ID du contrat précédent pour rattachement / reprise d'ancienneté */
    private Integer idContratPrecedent;

    public String getCodeTypeEvenement() { return codeTypeEvenement; }
    public void setCodeTypeEvenement(String codeTypeEvenement) { this.codeTypeEvenement = codeTypeEvenement; }

    public Integer getIdContratPrecedent() { return idContratPrecedent; }
    public void setIdContratPrecedent(Integer idContratPrecedent) { this.idContratPrecedent = idContratPrecedent; }
}
