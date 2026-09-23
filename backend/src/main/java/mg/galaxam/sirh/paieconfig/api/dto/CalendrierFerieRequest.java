package mg.galaxam.sirh.paieconfig.api.dto;

import java.sql.Date;

public class CalendrierFerieRequest {
    private Date dateFerie;
    private String libelle;
    private boolean estChome = true;
    private boolean estNational = true;

    public Date getDateFerie() { return dateFerie; }
    public void setDateFerie(Date dateFerie) { this.dateFerie = dateFerie; }
    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }
    public boolean isEstChome() { return estChome; }
    public void setEstChome(boolean estChome) { this.estChome = estChome; }
    public boolean isEstNational() { return estNational; }
    public void setEstNational(boolean estNational) { this.estNational = estNational; }
}
