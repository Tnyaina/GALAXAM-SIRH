package mg.galaxam.sirh.pointage.api.dto;


public class PointageAnomalieDTO {
    private Integer idPointage;
    private Integer idEmploye;
    private String matricule;
    private String nom;
    private String prenom;
    private String photoUrl;
    private Integer idSite;
    private String siteNom;
    private Boolean siteHoraireContinu = true;
    private String heureDebutMatinSite;
    private String heureFinMatinSite;
    private String heureDebutAmSite;
    private String heureFinAmSite;
    private String datePointage;
    
    private String heureEntreeMatin;
    private String heureSortieMatin;
    private String heureEntreeAm;
    private String heureSortieAm;
    
    private Double totalHeures;
    private Integer minutesRetard;
    
    private Integer idStatutJour;
    private String statutCode;
    private String statutLibelle;
    
    private String typeAnomalie; // 'INCOMPLET', 'RETARD', 'ABSENCE'
    private String descriptionAnomalie;
    private String motifAjustement;
    private String demandeEnAttenteLibelle;
    
    private Boolean indemniteDeplacementRecue;
    private Boolean deplacementMatin = true;
    private Boolean deplacementAm = true;
    private Boolean indemniteRepasRecue;
    
    private Integer idPeriodePaie;
    private String periodeCode;
    private String periodeLibelle;
    private String statutPeriodeCode;
    private Integer version = 1;

    // Nouveaux champs d'alignement maquette & gestion congés
    private String severite = "MOYENNE"; // 'CRITIQUE', 'HAUTE', 'MOYENNE', 'FAIBLE'
    private String semaineCode; // 'S1', 'S2', etc.
    private String statutAnomalie = "todo"; // 'todo', 'done'
    private boolean bloquantTransmission = false;
    private Double soldeCongesRestant = 0.0;
    private Double soldeCongesPris = 0.0;
    private Double soldeCongesAcquis = 0.0;

    // Getters and Setters
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public String getSeverite() { return severite; }
    public void setSeverite(String severite) { this.severite = severite; }

    public String getSemaineCode() { return semaineCode; }
    public void setSemaineCode(String semaineCode) { this.semaineCode = semaineCode; }

    public String getStatutAnomalie() { return statutAnomalie; }
    public void setStatutAnomalie(String statutAnomalie) { this.statutAnomalie = statutAnomalie; }

    public boolean isBloquantTransmission() { return bloquantTransmission; }
    public void setBloquantTransmission(boolean bloquantTransmission) { this.bloquantTransmission = bloquantTransmission; }

    public Double getSoldeCongesRestant() { return soldeCongesRestant; }
    public void setSoldeCongesRestant(Double soldeCongesRestant) { this.soldeCongesRestant = soldeCongesRestant; }
    public Double getSoldeCongesPris() { return soldeCongesPris; }
    public void setSoldeCongesPris(Double soldeCongesPris) { this.soldeCongesPris = soldeCongesPris; }

    public Double getSoldeCongesAcquis() { return soldeCongesAcquis; }
    public void setSoldeCongesAcquis(Double soldeCongesAcquis) { this.soldeCongesAcquis = soldeCongesAcquis; }
    public Boolean getDeplacementMatin() { return deplacementMatin; }
    public void setDeplacementMatin(Boolean deplacementMatin) { this.deplacementMatin = deplacementMatin; }

    public Boolean getDeplacementAm() { return deplacementAm; }
    public void setDeplacementAm(Boolean deplacementAm) { this.deplacementAm = deplacementAm; }

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

    public String getSiteNom() { return siteNom; }
    public void setSiteNom(String siteNom) { this.siteNom = siteNom; }

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

    public Integer getIdStatutJour() { return idStatutJour; }
    public void setIdStatutJour(Integer idStatutJour) { this.idStatutJour = idStatutJour; }

    public String getStatutCode() { return statutCode; }
    public void setStatutCode(String statutCode) { this.statutCode = statutCode; }

    public String getStatutLibelle() { return statutLibelle; }
    public void setStatutLibelle(String statutLibelle) { this.statutLibelle = statutLibelle; }

    public String getTypeAnomalie() { return typeAnomalie; }
    public void setTypeAnomalie(String typeAnomalie) { this.typeAnomalie = typeAnomalie; }

    public String getDescriptionAnomalie() { return descriptionAnomalie; }
    public void setDescriptionAnomalie(String descriptionAnomalie) { this.descriptionAnomalie = descriptionAnomalie; }

    public String getMotifAjustement() { return motifAjustement; }
    public void setMotifAjustement(String motifAjustement) { this.motifAjustement = motifAjustement; }

    public Boolean getIndemniteDeplacementRecue() { return indemniteDeplacementRecue; }
    public void setIndemniteDeplacementRecue(Boolean indemniteDeplacementRecue) { this.indemniteDeplacementRecue = indemniteDeplacementRecue; }

    public Boolean getIndemniteRepasRecue() { return indemniteRepasRecue; }
    public void setIndemniteRepasRecue(Boolean indemniteRepasRecue) { this.indemniteRepasRecue = indemniteRepasRecue; }

    public Integer getIdPeriodePaie() { return idPeriodePaie; }
    public void setIdPeriodePaie(Integer idPeriodePaie) { this.idPeriodePaie = idPeriodePaie; }

    public String getPeriodeCode() { return periodeCode; }
    public void setPeriodeCode(String periodeCode) { this.periodeCode = periodeCode; }

    public String getPeriodeLibelle() { return periodeLibelle; }
    public void setPeriodeLibelle(String periodeLibelle) { this.periodeLibelle = periodeLibelle; }

    public String getStatutPeriodeCode() { return statutPeriodeCode; }
    public void setStatutPeriodeCode(String statutPeriodeCode) { this.statutPeriodeCode = statutPeriodeCode; }

    public String getDemandeEnAttenteLibelle() { return demandeEnAttenteLibelle; }
    public void setDemandeEnAttenteLibelle(String demandeEnAttenteLibelle) { this.demandeEnAttenteLibelle = demandeEnAttenteLibelle; }

    public Boolean getSiteHoraireContinu() { return siteHoraireContinu; }
    public void setSiteHoraireContinu(Boolean siteHoraireContinu) { this.siteHoraireContinu = siteHoraireContinu; }
    public String getHeureDebutMatinSite() { return heureDebutMatinSite; }
    public void setHeureDebutMatinSite(String value) { this.heureDebutMatinSite = value; }
    public String getHeureFinMatinSite() { return heureFinMatinSite; }
    public void setHeureFinMatinSite(String value) { this.heureFinMatinSite = value; }
    public String getHeureDebutAmSite() { return heureDebutAmSite; }
    public void setHeureDebutAmSite(String value) { this.heureDebutAmSite = value; }
    public String getHeureFinAmSite() { return heureFinAmSite; }
    public void setHeureFinAmSite(String value) { this.heureFinAmSite = value; }

    // ─── Helpers Métier & Sérialisation JSON ──────────────────────────────────
    public boolean isIncomplet()       { return "INCOMPLET".equalsIgnoreCase(typeAnomalie) || "ANOMALIE_SAISIE".equalsIgnoreCase(statutCode); }
    public boolean isRetard()          { return "RETARD".equalsIgnoreCase(typeAnomalie) || (minutesRetard != null && minutesRetard > 0); }
    public boolean isAbsence()         { return "ABSENCE".equalsIgnoreCase(typeAnomalie) || "ABSENT".equalsIgnoreCase(statutCode) || "ABSENCE_NON_JUSTIFIEE".equalsIgnoreCase(statutCode); }
    public boolean isPeriodeCloturee() { return "CLOTUREE".equalsIgnoreCase(statutPeriodeCode); }
}
