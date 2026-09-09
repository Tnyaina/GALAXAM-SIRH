package mg.galaxam.sirh.api.dto.pointage;

import java.util.HashMap;
import java.util.Map;

public class SaisiePointageJourDTO {
    private Integer idPointage;
    private Integer idEmploye;
    private String matricule;
    private String nom;
    private String prenom;
    private String photoUrl;
    private Integer idSite;
    private String siteNom;
    private String datePointage; // YYYY-MM-DD

    private String heureEntreeMatin; // HH:mm
    private String heureSortieMatin; // HH:mm
    private String heureEntreeAm;    // HH:mm
    private String heureSortieAm;    // HH:mm

    private Double heuresMatin;
    private Double heuresAm;
    private Double totalHeures;
    private Double heuresNuit;
    private Integer minutesRetard;

    private String codeStatut; // PRESENT, RETARD, ABSENCE_NON_JUSTIFIEE, DISPENSE_POINTAGE, CONGE, etc.
    private String libelleStatut;

    private boolean estAutonome; // Dispense de pointage horaire (poste/fonction)
    private boolean estCadre;    // Categorie cadre (forfait conventionnel)
    private String typeCongeJour; // FULL_DAY, MATIN, APRES_MIDI, null
    private boolean ferieJour;
    private String libelleFerie;

    private String codeCategorie;
    private String libelleCategorie;
    private boolean horaireContinu;

    private boolean siteIndemniteDeplacementActif;
    private boolean siteIndemniteRepasActif;
    private boolean estDerogeDeplacement;
    private String motifDerogationDeplacement;
    private boolean estDerogeRepas;
    private String motifDerogationRepas;

    private boolean indemniteDeplacementRecue;
    private boolean deplacementMatin = true;
    private boolean deplacementAm = true;
    private Double montantDeplacement;
    private boolean indemniteRepasRecue;
    private Double montantRepas;

    private String motifAjustement;
    private Integer idPeriodePaie;
    private String libelleConge;
    private Boolean congePaye = true;
    private Boolean congeDecompteSolde = true;
    private Integer version; // Optimistic locking version
    private Map<String, Object> dynamicIndemnites = new HashMap<>();

    // Proprietes Backend-Driven pour l affichage IHM sans calculs front
    private String badgeProfilCode;
    private String badgeProfilLabel;
    private String badgeProfilVariant;
    private String badgeProfilTooltip;
    private String labelDerogationDeplacement;
    private String labelDerogationRepas;

    // Getters et Setters
    public String getBadgeProfilCode() { return badgeProfilCode; }
    public void setBadgeProfilCode(String badgeProfilCode) { this.badgeProfilCode = badgeProfilCode; }

    public String getBadgeProfilLabel() { return badgeProfilLabel; }
    public void setBadgeProfilLabel(String badgeProfilLabel) { this.badgeProfilLabel = badgeProfilLabel; }

    public String getBadgeProfilVariant() { return badgeProfilVariant; }
    public void setBadgeProfilVariant(String badgeProfilVariant) { this.badgeProfilVariant = badgeProfilVariant; }

    public String getBadgeProfilTooltip() { return badgeProfilTooltip; }
    public void setBadgeProfilTooltip(String badgeProfilTooltip) { this.badgeProfilTooltip = badgeProfilTooltip; }

    public String getLabelDerogationDeplacement() { return labelDerogationDeplacement; }
    public void setLabelDerogationDeplacement(String labelDerogationDeplacement) { this.labelDerogationDeplacement = labelDerogationDeplacement; }

    public String getLabelDerogationRepas() { return labelDerogationRepas; }
    public void setLabelDerogationRepas(String labelDerogationRepas) { this.labelDerogationRepas = labelDerogationRepas; }

    public boolean isCadreAutonome() { return estAutonome && estCadre; }
    public boolean isCadreBadgeant() { return !estAutonome && estCadre; }
    public boolean isAutonomeNonCadre() { return estAutonome && !estCadre; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public Integer getIdPeriodePaie() { return idPeriodePaie; }
    public void setIdPeriodePaie(Integer idPeriodePaie) { this.idPeriodePaie = idPeriodePaie; }

    public String getLibelleConge() { return libelleConge; }
    public void setLibelleConge(String libelleConge) { this.libelleConge = libelleConge; }

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

    public Double getHeuresMatin() { return heuresMatin; }
    public void setHeuresMatin(Double heuresMatin) { this.heuresMatin = heuresMatin; }

    public Double getHeuresAm() { return heuresAm; }
    public void setHeuresAm(Double heuresAm) { this.heuresAm = heuresAm; }

    public Double getTotalHeures() { return totalHeures; }
    public void setTotalHeures(Double totalHeures) { this.totalHeures = totalHeures; }

    public Double getHeuresNuit() { return heuresNuit; }
    public void setHeuresNuit(Double heuresNuit) { this.heuresNuit = heuresNuit; }

    public Integer getMinutesRetard() { return minutesRetard; }
    public void setMinutesRetard(Integer minutesRetard) { this.minutesRetard = minutesRetard; }

    public String getCodeStatut() { return codeStatut; }
    public void setCodeStatut(String codeStatut) { this.codeStatut = codeStatut; }

    public String getLibelleStatut() { return libelleStatut; }
    public void setLibelleStatut(String libelleStatut) { this.libelleStatut = libelleStatut; }

    public boolean isEstAutonome() { return estAutonome; }
    public void setEstAutonome(boolean estAutonome) { this.estAutonome = estAutonome; }

    public String getTypeCongeJour() { return typeCongeJour; }
    public void setTypeCongeJour(String typeCongeJour) { this.typeCongeJour = typeCongeJour; }

    public boolean isFerieJour() { return ferieJour; }
    public void setFerieJour(boolean ferieJour) { this.ferieJour = ferieJour; }

    public String getLibelleFerie() { return libelleFerie; }
    public void setLibelleFerie(String libelleFerie) { this.libelleFerie = libelleFerie; }

    public String getCodeCategorie() { return codeCategorie; }
    public void setCodeCategorie(String codeCategorie) { this.codeCategorie = codeCategorie; }

    public String getLibelleCategorie() { return libelleCategorie; }
    public void setLibelleCategorie(String libelleCategorie) { this.libelleCategorie = libelleCategorie; }

    public boolean isHoraireContinu() { return horaireContinu; }
    public void setHoraireContinu(boolean horaireContinu) { this.horaireContinu = horaireContinu; }
    public boolean isHoraireContinuJour() { return horaireContinu; }
    public void setHoraireContinuJour(boolean horaireContinuJour) { this.horaireContinu = horaireContinuJour; }

    public boolean isSiteIndemniteDeplacementActif() { return siteIndemniteDeplacementActif; }
    public void setSiteIndemniteDeplacementActif(boolean siteIndemniteDeplacementActif) { this.siteIndemniteDeplacementActif = siteIndemniteDeplacementActif; }

    public boolean isSiteIndemniteRepasActif() { return siteIndemniteRepasActif; }
    public void setSiteIndemniteRepasActif(boolean siteIndemniteRepasActif) { this.siteIndemniteRepasActif = siteIndemniteRepasActif; }

    public boolean isEstCadre() { return estCadre; }
    public void setEstCadre(boolean estCadre) { this.estCadre = estCadre; }

    public boolean isEstDerogeDeplacement() { return estDerogeDeplacement; }
    public void setEstDerogeDeplacement(boolean estDerogeDeplacement) { this.estDerogeDeplacement = estDerogeDeplacement; }

    public String getMotifDerogationDeplacement() { return motifDerogationDeplacement; }
    public void setMotifDerogationDeplacement(String motifDerogationDeplacement) { this.motifDerogationDeplacement = motifDerogationDeplacement; }

    public boolean isEstDerogeRepas() { return estDerogeRepas; }
    public void setEstDerogeRepas(boolean estDerogeRepas) { this.estDerogeRepas = estDerogeRepas; }

    public String getMotifDerogationRepas() { return motifDerogationRepas; }
    public void setMotifDerogationRepas(String motifDerogationRepas) { this.motifDerogationRepas = motifDerogationRepas; }

    public boolean isIndemniteDeplacementRecue() { return indemniteDeplacementRecue || deplacementMatin || deplacementAm; }
    public void setIndemniteDeplacementRecue(boolean indemniteDeplacementRecue) { this.indemniteDeplacementRecue = indemniteDeplacementRecue; }

    public boolean isDeplacementMatin() { return deplacementMatin; }
    public void setDeplacementMatin(boolean deplacementMatin) { this.deplacementMatin = deplacementMatin; }

    public boolean isDeplacementAm() { return deplacementAm; }
    public void setDeplacementAm(boolean deplacementAm) { this.deplacementAm = deplacementAm; }

    public Double getMontantDeplacement() { return montantDeplacement; }
    public void setMontantDeplacement(Double montantDeplacement) { this.montantDeplacement = montantDeplacement; }

    public boolean isIndemniteRepasRecue() { return indemniteRepasRecue; }
    public void setIndemniteRepasRecue(boolean indemniteRepasRecue) { this.indemniteRepasRecue = indemniteRepasRecue; }

    public Double getMontantRepas() { return montantRepas; }
    public void setMontantRepas(Double montantRepas) { this.montantRepas = montantRepas; }

    public String getMotifAjustement() { return motifAjustement; }
    public void setMotifAjustement(String motifAjustement) { this.motifAjustement = motifAjustement; }

    public Map<String, Object> getDynamicIndemnites() { return dynamicIndemnites; }
    public void setDynamicIndemnites(Map<String, Object> dynamicIndemnites) { this.dynamicIndemnites = dynamicIndemnites; }

    public Boolean getCongePaye() { return congePaye; }
    public void setCongePaye(Boolean congePaye) { this.congePaye = congePaye; }
    public boolean isCongePaye() { return congePaye == null || congePaye; }

    public Boolean getCongeDecompteSolde() { return congeDecompteSolde; }
    public void setCongeDecompteSolde(Boolean congeDecompteSolde) { this.congeDecompteSolde = congeDecompteSolde; }
    public boolean isCongeDecompteSolde() { return congeDecompteSolde == null || congeDecompteSolde; }

    // Backend-driven booleans pour l'interface UI (Zero comparaison de string en dur sur le front)
    public boolean isRepos() { return "REPOS".equalsIgnoreCase(codeStatut); }
    public boolean isPresent() { return "PRESENT".equalsIgnoreCase(codeStatut); }
    public boolean isRetard() { return "RETARD".equalsIgnoreCase(codeStatut) || (minutesRetard != null && minutesRetard > 0); }
    public boolean isConge() { return "CONGE".equalsIgnoreCase(codeStatut); }
    public boolean isAbsenceJustifiee() { return "ABSENCE_JUSTIFIEE".equalsIgnoreCase(codeStatut); }
    public boolean isFerieTravaille() { return "FERIE_TRAVAILLE".equalsIgnoreCase(codeStatut) || (ferieJour && totalHeures != null && totalHeures > 0); }
    public boolean isFerieChome() { return ("FERIE_CHOME".equalsIgnoreCase(codeStatut) || ferieJour) && !isFerieTravaille(); }
    public boolean isDimancheTravaille() { return "DIMANCHE_TRAVAILLE".equalsIgnoreCase(codeStatut); }
    public boolean isSamediTravaille() { return "SAMEDI_TRAVAILLE".equalsIgnoreCase(codeStatut); }
    public boolean isDispensePointage() {
        if (isFerieChome() || isConge() || isAbsenceJustifiee() || isRepos()) return false;
        return estAutonome || "DISPENSE_POINTAGE".equalsIgnoreCase(codeStatut);
    }
    public boolean isAnomalie() { return "ANOMALIE_SAISIE".equalsIgnoreCase(codeStatut); }
    public boolean isAbsenceNonJustifiee() { return "ABSENCE_NON_JUSTIFIEE".equalsIgnoreCase(codeStatut); }
    public boolean isAbsence() { return isAbsenceJustifiee() || isAbsenceNonJustifiee(); }

    public boolean isNonSaisi() {
        if (isRepos() || isConge() || isAbsenceJustifiee() || isAbsenceNonJustifiee() || isFerieChome() || isFerieTravaille() || isDimancheTravaille() || isSamediTravaille()) {
            return false;
        }
        if (estAutonome || isDispensePointage()) {
            return false;
        }
        return (totalHeures == null || totalHeures.compareTo(0.0) == 0);
    }

    public boolean isCongeFull() { return "FULL_DAY".equalsIgnoreCase(typeCongeJour); }
    public boolean isCongeMatin() { return "MATIN".equalsIgnoreCase(typeCongeJour); }
    public boolean isCongeAm() { return "APRES_MIDI".equalsIgnoreCase(typeCongeJour); }

    public String getStatutBadgeVariant() {
        if (isRepos()) return "neutral";
        if (isFerieTravaille()) return "error";
        if (isFerieChome()) return "neutral";
        if (isDimancheTravaille()) return "purple";
        if (isSamediTravaille() || isDispensePointage()) return "brand";
        if (isRetard() || isAnomalie() || isAbsenceNonJustifiee()) return "error";
        if (isConge() || isAbsenceJustifiee()) return "warning";
        if (isPresent()) return "success";
        return "neutral";
    }

    public String getStatutBadgeLabel() {
        if (isRepos()) return "Repos";
        if (libelleStatut != null && !libelleStatut.trim().isEmpty() && !"Non saisi".equalsIgnoreCase(libelleStatut)) {
            return libelleStatut;
        }
        if (isFerieTravaille()) return "Férié (+100%)";
        if (isFerieChome()) return "Férié chômé";
        if (isDimancheTravaille()) return "Dimanche (+40%)";
        if (isSamediTravaille()) return "Samedi travaillé";
        if (isDispensePointage()) return "Forfait 8h";
        if (isConge()) return "Congé";
        if (isAbsenceJustifiee()) return "Absence justifiée";
        if (isRetard()) return "Retard";
        if (isAnomalie()) return "Incomplet";
        if (isAbsenceNonJustifiee()) return "Absent";
        if (isPresent()) return "Présent";
        return "Non saisi";
    }
}
