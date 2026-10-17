package mg.galaxam.sirh.paie.api.dto.stc;

import java.sql.Date;

/**
 * DTO de requête pour la clôture administrative du dossier de sortie.
 * Phase 3.2 : Clôture administrative et cycle de vie RH.
 * Gouvernance stricte : toute dérogation de règlement incomplet requiert une autorité, une référence et une date.
 */
public class ClotureDossierRequest {

    private String motifCloture;
    private boolean derogationReglementIncomplet;
    private String justificationDerogation;
    private String autoriteDecisionDerogation;
    private String referenceDecisionDerogation;
    private Date dateDecisionDerogation;
    private boolean confirmationFormalites;

    public ClotureDossierRequest() {
    }

    public ClotureDossierRequest(String motifCloture) {
        this.motifCloture = motifCloture;
        this.confirmationFormalites = true;
    }

    public ClotureDossierRequest(String motifCloture, boolean derogationReglementIncomplet, String justificationDerogation, boolean confirmationFormalites) {
        this.motifCloture = motifCloture;
        this.derogationReglementIncomplet = derogationReglementIncomplet;
        this.justificationDerogation = justificationDerogation;
        this.confirmationFormalites = confirmationFormalites;
    }

    public ClotureDossierRequest(String motifCloture, boolean derogationReglementIncomplet,
                                 String justificationDerogation, String autoriteDecisionDerogation,
                                 String referenceDecisionDerogation, Date dateDecisionDerogation,
                                 boolean confirmationFormalites) {
        this.motifCloture = motifCloture;
        this.derogationReglementIncomplet = derogationReglementIncomplet;
        this.justificationDerogation = justificationDerogation;
        this.autoriteDecisionDerogation = autoriteDecisionDerogation;
        this.referenceDecisionDerogation = referenceDecisionDerogation;
        this.dateDecisionDerogation = dateDecisionDerogation;
        this.confirmationFormalites = confirmationFormalites;
    }

    public String getMotifCloture() {
        return motifCloture;
    }

    public void setMotifCloture(String motifCloture) {
        this.motifCloture = motifCloture;
    }

    public boolean isDerogationReglementIncomplet() {
        return derogationReglementIncomplet;
    }

    public void setDerogationReglementIncomplet(boolean derogationReglementIncomplet) {
        this.derogationReglementIncomplet = derogationReglementIncomplet;
    }

    public String getJustificationDerogation() {
        return justificationDerogation;
    }

    public void setJustificationDerogation(String justificationDerogation) {
        this.justificationDerogation = justificationDerogation;
    }

    public String getAutoriteDecisionDerogation() {
        return autoriteDecisionDerogation;
    }

    public void setAutoriteDecisionDerogation(String autoriteDecisionDerogation) {
        this.autoriteDecisionDerogation = autoriteDecisionDerogation;
    }

    public String getReferenceDecisionDerogation() {
        return referenceDecisionDerogation;
    }

    public void setReferenceDecisionDerogation(String referenceDecisionDerogation) {
        this.referenceDecisionDerogation = referenceDecisionDerogation;
    }

    public Date getDateDecisionDerogation() {
        return dateDecisionDerogation;
    }

    public void setDateDecisionDerogation(Date dateDecisionDerogation) {
        this.dateDecisionDerogation = dateDecisionDerogation;
    }

    public boolean isConfirmationFormalites() {
        return confirmationFormalites;
    }

    public void setConfirmationFormalites(boolean confirmationFormalites) {
        this.confirmationFormalites = confirmationFormalites;
    }
}
