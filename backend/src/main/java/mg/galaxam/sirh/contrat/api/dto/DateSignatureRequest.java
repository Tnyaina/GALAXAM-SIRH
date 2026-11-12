package mg.galaxam.sirh.contrat.api.dto;

import java.sql.Date;

/** Mise à jour de la date de signature d'un contrat. */
public class DateSignatureRequest {
    private Date dateSignature;

    public Date getDateSignature() {
        return dateSignature;
    }

    public void setDateSignature(Date dateSignature) {
        this.dateSignature = dateSignature;
    }
}
