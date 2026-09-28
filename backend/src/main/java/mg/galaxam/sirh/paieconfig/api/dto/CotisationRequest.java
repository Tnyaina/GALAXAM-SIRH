package mg.galaxam.sirh.paieconfig.api.dto;

import java.math.BigDecimal;
import java.sql.Date;

public class CotisationRequest {
    private String code;
    private String libelle;
    private BigDecimal tauxSalarial;
    private BigDecimal tauxPatronal;
    private Date dateDebut;
    private Date dateFin;
    private String portee;
    private String categorie;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }
    public BigDecimal getTauxSalarial() { return tauxSalarial; }
    public void setTauxSalarial(BigDecimal tauxSalarial) { this.tauxSalarial = tauxSalarial; }
    public BigDecimal getTauxPatronal() { return tauxPatronal; }
    public void setTauxPatronal(BigDecimal tauxPatronal) { this.tauxPatronal = tauxPatronal; }
    public Date getDateDebut() { return dateDebut; }
    public void setDateDebut(Date dateDebut) { this.dateDebut = dateDebut; }
    public Date getDateFin() { return dateFin; }
    public void setDateFin(Date dateFin) { this.dateFin = dateFin; }
    public String getPortee() { return portee; }
    public void setPortee(String portee) { this.portee = portee; }
    public String getCategorie() { return categorie; }
    public void setCategorie(String categorie) { this.categorie = categorie; }
}
