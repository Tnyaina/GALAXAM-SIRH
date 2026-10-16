package mg.galaxam.sirh.paie.api.dto.stc;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;

/**
 * DTO représentant une ligne de versement du journal paie_stc_reglement.
 */
public class StcReglementDTO {
    private Integer idReglement;
    private Integer idStc;
    private Date dateReglement;
    private BigDecimal montantRegle;
    private String modeReglement;
    private String referenceReglement;
    private String commentaire;
    private Integer enregistrePar;
    private String nomEnregistrePar;
    private Timestamp createdAt;
    private boolean estAnnule;
    private Integer annulePar;
    private String nomAnnulePar;
    private Timestamp dateAnnulation;
    private String motifAnnulation;

    public StcReglementDTO() {}

    public Integer getIdReglement() { return idReglement; }
    public void setIdReglement(Integer idReglement) { this.idReglement = idReglement; }

    public Integer getIdStc() { return idStc; }
    public void setIdStc(Integer idStc) { this.idStc = idStc; }

    public Date getDateReglement() { return dateReglement; }
    public void setDateReglement(Date dateReglement) { this.dateReglement = dateReglement; }

    public BigDecimal getMontantRegle() { return montantRegle; }
    public void setMontantRegle(BigDecimal montantRegle) { this.montantRegle = montantRegle; }

    public String getModeReglement() { return modeReglement; }
    public void setModeReglement(String modeReglement) { this.modeReglement = modeReglement; }

    public String getReferenceReglement() { return referenceReglement; }
    public void setReferenceReglement(String referenceReglement) { this.referenceReglement = referenceReglement; }

    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }

    public Integer getEnregistrePar() { return enregistrePar; }
    public void setEnregistrePar(Integer enregistrePar) { this.enregistrePar = enregistrePar; }

    public String getNomEnregistrePar() { return nomEnregistrePar; }
    public void setNomEnregistrePar(String nomEnregistrePar) { this.nomEnregistrePar = nomEnregistrePar; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public boolean isEstAnnule() { return estAnnule; }
    public void setEstAnnule(boolean estAnnule) { this.estAnnule = estAnnule; }

    public Integer getAnnulePar() { return annulePar; }
    public void setAnnulePar(Integer annulePar) { this.annulePar = annulePar; }

    public String getNomAnnulePar() { return nomAnnulePar; }
    public void setNomAnnulePar(String nomAnnulePar) { this.nomAnnulePar = nomAnnulePar; }

    public Timestamp getDateAnnulation() { return dateAnnulation; }
    public void setDateAnnulation(Timestamp dateAnnulation) { this.dateAnnulation = dateAnnulation; }

    public String getMotifAnnulation() { return motifAnnulation; }
    public void setMotifAnnulation(String motifAnnulation) { this.motifAnnulation = motifAnnulation; }
}
