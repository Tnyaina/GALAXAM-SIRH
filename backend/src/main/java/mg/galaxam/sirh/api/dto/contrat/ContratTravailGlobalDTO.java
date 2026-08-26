package mg.galaxam.sirh.api.dto.contrat;

import java.sql.Date;

public class ContratTravailGlobalDTO {
    private Integer idContrat;
    private Integer idEmploye;
    private String matricule;
    private String nomPrenom;
    private String photoUrl;
    private String numeroContrat;
    private String codeTypeContrat;
    private String libelleTypeContrat;
    private String codeStatutContrat;
    private String libelleStatutContrat;
    private String nomSite;
    private String nomDepartement;
    private String nomFonction;
    private Date dateDebut;
    private Date dateFin;
    private Date dateFinEssai;
    private Integer joursRestantsFin;
    private Integer joursRestantsEssai;

    public ContratTravailGlobalDTO() {}

    // Getters and Setters
    public Integer getIdContrat() { return idContrat; }
    public void setIdContrat(Integer idContrat) { this.idContrat = idContrat; }

    public Integer getIdEmploye() { return idEmploye; }
    public void setIdEmploye(Integer idEmploye) { this.idEmploye = idEmploye; }

    public String getMatricule() { return matricule; }
    public void setMatricule(String matricule) { this.matricule = matricule; }

    public String getNomPrenom() { return nomPrenom; }
    public void setNomPrenom(String nomPrenom) { this.nomPrenom = nomPrenom; }

    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }

    public String getNumeroContrat() { return numeroContrat; }
    public void setNumeroContrat(String numeroContrat) { this.numeroContrat = numeroContrat; }

    public String getCodeTypeContrat() { return codeTypeContrat; }
    public void setCodeTypeContrat(String codeTypeContrat) { this.codeTypeContrat = codeTypeContrat; }

    public String getLibelleTypeContrat() { return libelleTypeContrat; }
    public void setLibelleTypeContrat(String libelleTypeContrat) { this.libelleTypeContrat = libelleTypeContrat; }

    public String getCodeStatutContrat() { return codeStatutContrat; }
    public void setCodeStatutContrat(String codeStatutContrat) { this.codeStatutContrat = codeStatutContrat; }

    public String getLibelleStatutContrat() { return libelleStatutContrat; }
    public void setLibelleStatutContrat(String libelleStatutContrat) { this.libelleStatutContrat = libelleStatutContrat; }

    public String getNomSite() { return nomSite; }
    public void setNomSite(String nomSite) { this.nomSite = nomSite; }

    public String getNomDepartement() { return nomDepartement; }
    public void setNomDepartement(String nomDepartement) { this.nomDepartement = nomDepartement; }

    public String getNomFonction() { return nomFonction; }
    public void setNomFonction(String nomFonction) { this.nomFonction = nomFonction; }

    public Date getDateDebut() { return dateDebut; }
    public void setDateDebut(Date dateDebut) { this.dateDebut = dateDebut; }

    public Date getDateFin() { return dateFin; }
    public void setDateFin(Date dateFin) { this.dateFin = dateFin; }

    public Date getDateFinEssai() { return dateFinEssai; }
    public void setDateFinEssai(Date dateFinEssai) { this.dateFinEssai = dateFinEssai; }

    public Integer getJoursRestantsFin() { return joursRestantsFin; }
    public void setJoursRestantsFin(Integer joursRestantsFin) { this.joursRestantsFin = joursRestantsFin; }

    public Integer getJoursRestantsEssai() { return joursRestantsEssai; }
    public void setJoursRestantsEssai(Integer joursRestantsEssai) { this.joursRestantsEssai = joursRestantsEssai; }

    private Boolean isDuplicate = false;
    public Boolean getIsDuplicate() { return isDuplicate; }
    public void setIsDuplicate(Boolean isDuplicate) { this.isDuplicate = isDuplicate; }
}
