package mg.galaxam.sirh.api.dto.organisation;

import java.sql.Date;

public class DepartementRequest {
    private String codeDepartement;
    private String libelle;
    private Date dateFinPrevue;

    public String getCodeDepartement() { return codeDepartement; }
    public void setCodeDepartement(String codeDepartement) { this.codeDepartement = codeDepartement; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public Date getDateFinPrevue() { return dateFinPrevue; }
    public void setDateFinPrevue(Date dateFinPrevue) { this.dateFinPrevue = dateFinPrevue; }
}
