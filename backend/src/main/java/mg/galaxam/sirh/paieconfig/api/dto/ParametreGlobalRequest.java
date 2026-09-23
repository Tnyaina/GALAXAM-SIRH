package mg.galaxam.sirh.paieconfig.api.dto;

import java.sql.Date;

public class ParametreGlobalRequest {
    private String codeParametre;
    private String valeur;
    private String description;
    private String categorie;
    private Date dateDebut;
    private Date dateFin;
    private Boolean estActif;
    private Boolean estDesactivable;

    public String getCodeParametre() { return codeParametre; }
    public void setCodeParametre(String codeParametre) { this.codeParametre = codeParametre; }
    public String getValeur() { return valeur; }
    public void setValeur(String valeur) { this.valeur = valeur; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategorie() { return categorie; }
    public void setCategorie(String categorie) { this.categorie = categorie; }
    public Date getDateDebut() { return dateDebut; }
    public void setDateDebut(Date dateDebut) { this.dateDebut = dateDebut; }
    public Date getDateFin() { return dateFin; }
    public void setDateFin(Date dateFin) { this.dateFin = dateFin; }
    public Boolean getEstActif() { return estActif; }
    public void setEstActif(Boolean estActif) { this.estActif = estActif; }
    public Boolean getEstDesactivable() { return estDesactivable; }
    public void setEstDesactivable(Boolean estDesactivable) { this.estDesactivable = estDesactivable; }
}
