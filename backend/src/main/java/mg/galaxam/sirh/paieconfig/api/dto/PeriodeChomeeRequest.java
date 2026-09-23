package mg.galaxam.sirh.paieconfig.api.dto;

import java.math.BigDecimal;
import java.util.Date;

public class PeriodeChomeeRequest {
    private String libelle;
    private Integer idSite;
    private Date dateDebut;
    private Date dateFin;
    private Boolean estPaye;
    private BigDecimal tauxPaiement;

    public String getLibelle() {
        return libelle;
    }

    public void setLibelle(String libelle) {
        this.libelle = libelle;
    }

    public Integer getIdSite() {
        return idSite;
    }

    public void setIdSite(Integer idSite) {
        this.idSite = idSite;
    }

    public Date getDateDebut() {
        return dateDebut;
    }

    public void setDateDebut(Date dateDebut) {
        this.dateDebut = dateDebut;
    }

    public Date getDateFin() {
        return dateFin;
    }

    public void setDateFin(Date dateFin) {
        this.dateFin = dateFin;
    }

    public Boolean getEstPaye() {
        return estPaye;
    }

    public void setEstPaye(Boolean estPaye) {
        this.estPaye = estPaye;
    }

    public BigDecimal getTauxPaiement() {
        return tauxPaiement;
    }

    public void setTauxPaiement(BigDecimal tauxPaiement) {
        this.tauxPaiement = tauxPaiement;
    }
}
