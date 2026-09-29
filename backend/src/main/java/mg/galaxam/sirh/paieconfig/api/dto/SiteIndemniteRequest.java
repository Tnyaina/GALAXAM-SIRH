package mg.galaxam.sirh.paieconfig.api.dto;

import java.math.BigDecimal;
import java.sql.Date;

public class SiteIndemniteRequest {
    private Integer idSite;
    private Integer idIndemnite;
    private String periodicite;
    private BigDecimal montantReference;
    private Date dateDebut;
    private Date dateFin;

    public Integer getIdSite() { return idSite; }
    public void setIdSite(Integer idSite) { this.idSite = idSite; }
    public Integer getIdIndemnite() { return idIndemnite; }
    public void setIdIndemnite(Integer idIndemnite) { this.idIndemnite = idIndemnite; }
    public String getPeriodicite() { return periodicite; }
    public void setPeriodicite(String periodicite) { this.periodicite = periodicite; }
    public BigDecimal getMontantReference() { return montantReference; }
    public void setMontantReference(BigDecimal montantReference) { this.montantReference = montantReference; }
    public Date getDateDebut() { return dateDebut; }
    public void setDateDebut(Date dateDebut) { this.dateDebut = dateDebut; }
    public Date getDateFin() { return dateFin; }
    public void setDateFin(Date dateFin) { this.dateFin = dateFin; }
}
