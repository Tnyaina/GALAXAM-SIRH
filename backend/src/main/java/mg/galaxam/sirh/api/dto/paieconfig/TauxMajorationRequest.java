package mg.galaxam.sirh.api.dto.paieconfig;

import java.math.BigDecimal;
import java.sql.Date;

public class TauxMajorationRequest {
    private String typeMajoration;
    private BigDecimal taux;
    private Date dateDebut;
    private Date dateFin;

    public String getTypeMajoration() { return typeMajoration; }
    public void setTypeMajoration(String typeMajoration) { this.typeMajoration = typeMajoration; }
    public BigDecimal getTaux() { return taux; }
    public void setTaux(BigDecimal taux) { this.taux = taux; }
    public Date getDateDebut() { return dateDebut; }
    public void setDateDebut(Date dateDebut) { this.dateDebut = dateDebut; }
    public Date getDateFin() { return dateFin; }
    public void setDateFin(Date dateFin) { this.dateFin = dateFin; }
}
