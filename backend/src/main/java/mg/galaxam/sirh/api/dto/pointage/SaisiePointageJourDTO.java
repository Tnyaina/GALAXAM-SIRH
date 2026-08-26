package mg.galaxam.sirh.api.dto.pointage;

public class SaisiePointageJourDTO {
    private Integer idPointage;
    private Integer idEmploye;
    private String matricule;
    private String nom;
    private String prenom;
    private String photoUrl;
    private Integer idSite;
    private String datePointage; // YYYY-MM-DD

    private String heureEntreeMatin; // HH:mm
    private String heureSortieMatin; // HH:mm
    private String heureEntreeAm;    // HH:mm
    private String heureSortieAm;    // HH:mm

    private Double totalHeures;
    private Integer minutesRetard;
    private String codeStatut; // PRESENT, RETARD, ABSENCE_NON_JUSTIFIEE, DISPENSE_POINTAGE, CONGE_MATIN, CONGE_APRES_MIDI, etc.

    private boolean estAutonome; // Cadre / Dispense de pointage
    private String typeCongeJour; // FULL_DAY, MATIN, APRES_MIDI, null

    private boolean siteIndemniteDeplacementActif;
    private boolean siteIndemniteRepasActif;
    private boolean estDerogeDeplacement;
    private boolean estDerogeRepas;

    private boolean indemniteDeplacementRecue;
    private boolean deplacementMatin = true;
    private boolean deplacementAm = true;
    private boolean indemniteRepasRecue;

    private String motifAjustement;

    // Getters et Setters
    public Integer getIdPointage() { return idPointage; }
    public void setIdPointage(Integer idPointage) { this.idPointage = idPointage; }

    public Integer getIdEmploye() { return idEmploye; }
    public void setIdEmploye(Integer idEmploye) { this.idEmploye = idEmploye; }

    public String getMatricule() { return matricule; }
    public void setMatricule(String matricule) { this.matricule = matricule; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getPrenom() { return prenom; }
    public void setPrenom(String prenom) { this.prenom = prenom; }

    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }

    public Integer getIdSite() { return idSite; }
    public void setIdSite(Integer idSite) { this.idSite = idSite; }

    public String getDatePointage() { return datePointage; }
    public void setDatePointage(String datePointage) { this.datePointage = datePointage; }

    public String getHeureEntreeMatin() { return heureEntreeMatin; }
    public void setHeureEntreeMatin(String heureEntreeMatin) { this.heureEntreeMatin = heureEntreeMatin; }

    public String getHeureSortieMatin() { return heureSortieMatin; }
    public void setHeureSortieMatin(String heureSortieMatin) { this.heureSortieMatin = heureSortieMatin; }

    public String getHeureEntreeAm() { return heureEntreeAm; }
    public void setHeureEntreeAm(String heureEntreeAm) { this.heureEntreeAm = heureEntreeAm; }

    public String getHeureSortieAm() { return heureSortieAm; }
    public void setHeureSortieAm(String heureSortieAm) { this.heureSortieAm = heureSortieAm; }

    public Double getTotalHeures() { return totalHeures; }
    public void setTotalHeures(Double totalHeures) { this.totalHeures = totalHeures; }

    public Integer getMinutesRetard() { return minutesRetard; }
    public void setMinutesRetard(Integer minutesRetard) { this.minutesRetard = minutesRetard; }

    public String getCodeStatut() { return codeStatut; }
    public void setCodeStatut(String codeStatut) { this.codeStatut = codeStatut; }

    public boolean isEstAutonome() { return estAutonome; }
    public void setEstAutonome(boolean estAutonome) { this.estAutonome = estAutonome; }

    public String getTypeCongeJour() { return typeCongeJour; }
    public void setTypeCongeJour(String typeCongeJour) { this.typeCongeJour = typeCongeJour; }

    public boolean isSiteIndemniteDeplacementActif() { return siteIndemniteDeplacementActif; }
    public void setSiteIndemniteDeplacementActif(boolean siteIndemniteDeplacementActif) { this.siteIndemniteDeplacementActif = siteIndemniteDeplacementActif; }

    public boolean isSiteIndemniteRepasActif() { return siteIndemniteRepasActif; }
    public void setSiteIndemniteRepasActif(boolean siteIndemniteRepasActif) { this.siteIndemniteRepasActif = siteIndemniteRepasActif; }

    public boolean isEstDerogeDeplacement() { return estDerogeDeplacement; }
    public void setEstDerogeDeplacement(boolean estDerogeDeplacement) { this.estDerogeDeplacement = estDerogeDeplacement; }

    public boolean isEstDerogeRepas() { return estDerogeRepas; }
    public void setEstDerogeRepas(boolean estDerogeRepas) { this.estDerogeRepas = estDerogeRepas; }

    public boolean isIndemniteDeplacementRecue() { return indemniteDeplacementRecue; }
    public void setIndemniteDeplacementRecue(boolean indemniteDeplacementRecue) { this.indemniteDeplacementRecue = indemniteDeplacementRecue; }

    public boolean isDeplacementMatin() { return deplacementMatin; }
    public void setDeplacementMatin(boolean deplacementMatin) { this.deplacementMatin = deplacementMatin; }

    public boolean isDeplacementAm() { return deplacementAm; }
    public void setDeplacementAm(boolean deplacementAm) { this.deplacementAm = deplacementAm; }

    public boolean isIndemniteRepasRecue() { return indemniteRepasRecue; }
    public void setIndemniteRepasRecue(boolean indemniteRepasRecue) { this.indemniteRepasRecue = indemniteRepasRecue; }

    public String getMotifAjustement() { return motifAjustement; }
    public void setMotifAjustement(String motifAjustement) { this.motifAjustement = motifAjustement; }

    private String siteNom;
    private String codeCategorie;
    private String libelleCategorie;
    private boolean horaireContinu;
    private Double heuresMatin;
    private Double heuresAm;
    private Double heuresNuit;
    private Double montantRepas;
    private String libelleStatut;

    public String getSiteNom() { return siteNom; }
    public void setSiteNom(String siteNom) { this.siteNom = siteNom; }

    public String getCodeCategorie() { return codeCategorie; }
    public void setCodeCategorie(String codeCategorie) { this.codeCategorie = codeCategorie; }

    public String getLibelleCategorie() { return libelleCategorie; }
    public void setLibelleCategorie(String libelleCategorie) { this.libelleCategorie = libelleCategorie; }

    public boolean isHoraireContinu() { return horaireContinu; }
    public void setHoraireContinu(boolean horaireContinu) { this.horaireContinu = horaireContinu; }

    public Double getHeuresMatin() { return heuresMatin; }
    public void setHeuresMatin(Double heuresMatin) { this.heuresMatin = heuresMatin; }

    public Double getHeuresAm() { return heuresAm; }
    public void setHeuresAm(Double heuresAm) { this.heuresAm = heuresAm; }

    public Double getHeuresNuit() { return heuresNuit; }
    public void setHeuresNuit(Double heuresNuit) { this.heuresNuit = heuresNuit; }

    public Double getMontantRepas() { return montantRepas; }
    public void setMontantRepas(Double montantRepas) { this.montantRepas = montantRepas; }

    public String getLibelleStatut() { return libelleStatut; }
    public void setLibelleStatut(String libelleStatut) { this.libelleStatut = libelleStatut; }
}
