package mg.galaxam.sirh.api.dto.pointage;

import java.sql.Date;

public class DerogationRequest {

    private Integer idEmploye;
    private Integer idIndemnite;
    private Boolean beneficie;
    private String motif;
    private Date dateDebut;
    private Date dateFin;

    public Integer getIdEmploye() { return idEmploye; }
    public void setIdEmploye(Integer idEmploye) { this.idEmploye = idEmploye; }

    public Integer getIdIndemnite() { return idIndemnite; }
    public void setIdIndemnite(Integer idIndemnite) { this.idIndemnite = idIndemnite; }

    public Boolean getBeneficie() { return beneficie; }
    public void setBeneficie(Boolean beneficie) { this.beneficie = beneficie; }

    public String getMotif() { return motif; }
    public void setMotif(String motif) { this.motif = motif; }

    public Date getDateDebut() { return dateDebut; }
    public void setDateDebut(Date dateDebut) { this.dateDebut = dateDebut; }

    public Date getDateFin() { return dateFin; }
    public void setDateFin(Date dateFin) { this.dateFin = dateFin; }
}
