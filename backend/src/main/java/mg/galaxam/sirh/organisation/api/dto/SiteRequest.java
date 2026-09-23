package mg.galaxam.sirh.organisation.api.dto;

import java.sql.Time;

public class SiteRequest {
    private String codeSite;
    private String libelle;
    private Integer idFilialeRattachement;
    private boolean horaireContinu;
    private Time heureDebutMatin;
    private Time heureFinMatin;
    private Time heureDebutApresMidi;
    private Time heureFinApresMidi;

    public String getCodeSite() { return codeSite; }
    public void setCodeSite(String codeSite) { this.codeSite = codeSite; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public Integer getIdFilialeRattachement() { return idFilialeRattachement; }
    public void setIdFilialeRattachement(Integer idFilialeRattachement) { this.idFilialeRattachement = idFilialeRattachement; }

    public boolean isHoraireContinu() { return horaireContinu; }
    public void setHoraireContinu(boolean horaireContinu) { this.horaireContinu = horaireContinu; }

    public Time getHeureDebutMatin() { return heureDebutMatin; }
    public void setHeureDebutMatin(Time heureDebutMatin) { this.heureDebutMatin = heureDebutMatin; }

    public Time getHeureFinMatin() { return heureFinMatin; }
    public void setHeureFinMatin(Time heureFinMatin) { this.heureFinMatin = heureFinMatin; }

    public Time getHeureDebutApresMidi() { return heureDebutApresMidi; }
    public void setHeureDebutApresMidi(Time heureDebutApresMidi) { this.heureDebutApresMidi = heureDebutApresMidi; }

    public Time getHeureFinApresMidi() { return heureFinApresMidi; }
    public void setHeureFinApresMidi(Time heureFinApresMidi) { this.heureFinApresMidi = heureFinApresMidi; }
}
