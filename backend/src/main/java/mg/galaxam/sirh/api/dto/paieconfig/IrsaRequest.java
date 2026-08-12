package mg.galaxam.sirh.api.dto.paieconfig;

import java.math.BigDecimal;
import java.sql.Date;

public class IrsaRequest {
    private Integer numeroTranche;
    private BigDecimal trancheMin;
    private BigDecimal trancheMax;
    private BigDecimal taux;
    private Date dateDebut;
    private Date dateFin;

    public Integer getNumeroTranche() { return numeroTranche; }
    public void setNumeroTranche(Integer numeroTranche) { this.numeroTranche = numeroTranche; }
    public BigDecimal getTrancheMin() { return trancheMin; }
    public void setTrancheMin(BigDecimal trancheMin) { this.trancheMin = trancheMin; }
    public BigDecimal getTrancheMax() { return trancheMax; }
    public void setTrancheMax(BigDecimal trancheMax) { this.trancheMax = trancheMax; }
    public BigDecimal getTaux() { return taux; }
    public void setTaux(BigDecimal taux) { this.taux = taux; }
    public Date getDateDebut() { return dateDebut; }
    public void setDateDebut(Date dateDebut) { this.dateDebut = dateDebut; }
    public Date getDateFin() { return dateFin; }
    public void setDateFin(Date dateFin) { this.dateFin = dateFin; }
}
