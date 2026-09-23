package mg.galaxam.sirh.paieconfig.api.dto;

import java.math.BigDecimal;
import java.sql.Date;

public class TauxMajorationRequest {
    private Integer idConfigLegale;
    private String typeMajoration;
    private BigDecimal taux;
    private String description;
    private Date dateDebut;
    private Date dateFin;

    public Integer getIdConfigLegale() { return idConfigLegale; }
    public void setIdConfigLegale(Integer idConfigLegale) { this.idConfigLegale = idConfigLegale; }
    public String getTypeMajoration() { return typeMajoration; }
    public void setTypeMajoration(String typeMajoration) { this.typeMajoration = typeMajoration; }
    public BigDecimal getTaux() { return taux; }
    public void setTaux(BigDecimal taux) { this.taux = taux; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Date getDateDebut() { return dateDebut; }
    public void setDateDebut(Date dateDebut) { this.dateDebut = dateDebut; }
    public Date getDateFin() { return dateFin; }
    public void setDateFin(Date dateFin) { this.dateFin = dateFin; }
}
