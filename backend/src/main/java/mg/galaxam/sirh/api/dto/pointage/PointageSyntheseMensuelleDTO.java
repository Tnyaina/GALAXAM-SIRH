package mg.galaxam.sirh.api.dto.pointage;

import java.io.Serializable;
import java.math.BigDecimal;

public class PointageSyntheseMensuelleDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer idEmploye;
    private String matricule;
    private String nom;
    private String prenom;
    private String photoUrl;
    private Integer idSite;
    private String siteNom;
    private String codeCategorie;
    private String libelleCategorie;
    private boolean estCadre;
    private boolean dispensePointage;

    private Integer idPeriodePaie;
    private String codePeriode;
    private String libellePeriode;
    private String statutPeriode;
    private boolean estGele;

    // Assiduité & jours
    private int nbJoursOuvrables;
    private int nbJoursPresents;
    private int nbJoursAbsence;
    private int nbJoursConge;
    private int nbJoursFerieChome;
    private int nbJoursFerieTravaille;
    private int nbJoursDimancheTravaille;

    // Volumes horaires
    private double totalHeuresTravaillees;
    private double heuresNormales;
    private double heuresHs30;
    private double heuresHs50;
    private double heuresDimancheTotal;
    private double heuresFerieTotal;
    private double heuresNuitTotal;
    private double heuresAbsenceRetenues; // Retards nets non compensés
    private int totalMinutesRetard;
    private double retardCompense; // Retard compensé 1:1 en heures
    private double surplusBrut; // Total travaillé - Heures normales

    public double getSurplusBrut() { return surplusBrut; }
    public void setSurplusBrut(double surplusBrut) { this.surplusBrut = surplusBrut; }

    public int getTotalMinutesRetard() { return totalMinutesRetard; }
    public void setTotalMinutesRetard(int totalMinutesRetard) { this.totalMinutesRetard = totalMinutesRetard; }
    public double getRetardCompense() { return retardCompense; }
    public void setRetardCompense(double retardCompense) { this.retardCompense = retardCompense; }

    // Indemnités
    private int nbJoursDeplacement;
    private double montantDeplacement;
    private int nbJoursRepas;
    private double montantRepas;

    // Contrôle & intégrité
    private int nbAnomaliesNonResolues;

    // Décomposition par semaine (tronçons 40h ISO et compensation 1:1)
    private java.util.List<PointageTronconSemaineDTO> tronconsSemaine = new java.util.ArrayList<>();

    public java.util.List<PointageTronconSemaineDTO> getTronconsSemaine() {
        return tronconsSemaine;
    }

    public void setTronconsSemaine(java.util.List<PointageTronconSemaineDTO> tronconsSemaine) {
        this.tronconsSemaine = tronconsSemaine != null ? tronconsSemaine : new java.util.ArrayList<>();
    }

    public PointageSyntheseMensuelleDTO() {}

    public Integer getIdEmploye() {
        return idEmploye;
    }

    public void setIdEmploye(Integer idEmploye) {
        this.idEmploye = idEmploye;
    }

    public String getMatricule() {
        return matricule;
    }

    public void setMatricule(String matricule) {
        this.matricule = matricule;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getPrenom() {
        return prenom;
    }

    public void setPrenom(String prenom) {
        this.prenom = prenom;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public Integer getIdSite() {
        return idSite;
    }

    public void setIdSite(Integer idSite) {
        this.idSite = idSite;
    }

    public String getSiteNom() {
        return siteNom;
    }

    public void setSiteNom(String siteNom) {
        this.siteNom = siteNom;
    }

    public String getCodeCategorie() {
        return codeCategorie;
    }

    public void setCodeCategorie(String codeCategorie) {
        this.codeCategorie = codeCategorie;
    }

    public String getLibelleCategorie() {
        return libelleCategorie;
    }

    public void setLibelleCategorie(String libelleCategorie) {
        this.libelleCategorie = libelleCategorie;
    }

    public boolean isEstCadre() {
        return estCadre;
    }

    public void setEstCadre(boolean estCadre) {
        this.estCadre = estCadre;
    }

    public boolean isDispensePointage() {
        return dispensePointage;
    }

    public void setDispensePointage(boolean dispensePointage) {
        this.dispensePointage = dispensePointage;
    }

    public String getBadgeProfilLabel() {
        if (dispensePointage && estCadre) return "CADRE AUTO";
        if (dispensePointage) return "AUTONOME";
        if (estCadre) return "CADRE";
        return null;
    }

    public String getBadgeProfilVariant() {
        if (dispensePointage && estCadre) return "purple";
        if (dispensePointage) return "brand";
        if (estCadre) return "indigo";
        return null;
    }

    public Integer getIdPeriodePaie() {
        return idPeriodePaie;
    }

    public void setIdPeriodePaie(Integer idPeriodePaie) {
        this.idPeriodePaie = idPeriodePaie;
    }

    public String getCodePeriode() {
        return codePeriode;
    }

    public void setCodePeriode(String codePeriode) {
        this.codePeriode = codePeriode;
    }

    public String getLibellePeriode() {
        return libellePeriode;
    }

    public void setLibellePeriode(String libellePeriode) {
        this.libellePeriode = libellePeriode;
    }

    public String getStatutPeriode() {
        return statutPeriode;
    }

    public void setStatutPeriode(String statutPeriode) {
        this.statutPeriode = statutPeriode;
    }

    public boolean isEstGele() {
        return estGele;
    }

    public void setEstGele(boolean estGele) {
        this.estGele = estGele;
    }

    public int getNbJoursOuvrables() {
        return nbJoursOuvrables;
    }

    public void setNbJoursOuvrables(int nbJoursOuvrables) {
        this.nbJoursOuvrables = nbJoursOuvrables;
    }

    public int getNbJoursPresents() {
        return nbJoursPresents;
    }

    public void setNbJoursPresents(int nbJoursPresents) {
        this.nbJoursPresents = nbJoursPresents;
    }

    public int getNbJoursAbsence() {
        return nbJoursAbsence;
    }

    public void setNbJoursAbsence(int nbJoursAbsence) {
        this.nbJoursAbsence = nbJoursAbsence;
    }

    public int getNbJoursConge() {
        return nbJoursConge;
    }

    public void setNbJoursConge(int nbJoursConge) {
        this.nbJoursConge = nbJoursConge;
    }

    public int getNbJoursFerieChome() {
        return nbJoursFerieChome;
    }

    public void setNbJoursFerieChome(int nbJoursFerieChome) {
        this.nbJoursFerieChome = nbJoursFerieChome;
    }

    public int getNbJoursFerieTravaille() {
        return nbJoursFerieTravaille;
    }

    public void setNbJoursFerieTravaille(int nbJoursFerieTravaille) {
        this.nbJoursFerieTravaille = nbJoursFerieTravaille;
    }

    public int getNbJoursDimancheTravaille() {
        return nbJoursDimancheTravaille;
    }

    public void setNbJoursDimancheTravaille(int nbJoursDimancheTravaille) {
        this.nbJoursDimancheTravaille = nbJoursDimancheTravaille;
    }

    public double getTotalHeuresTravaillees() {
        return totalHeuresTravaillees;
    }

    public void setTotalHeuresTravaillees(double totalHeuresTravaillees) {
        this.totalHeuresTravaillees = totalHeuresTravaillees;
    }

    public double getHeuresNormales() {
        return heuresNormales;
    }

    public void setHeuresNormales(double heuresNormales) {
        this.heuresNormales = heuresNormales;
    }

    public double getHeuresHs30() {
        return heuresHs30;
    }

    public void setHeuresHs30(double heuresHs30) {
        this.heuresHs30 = heuresHs30;
    }

    public double getHeuresHs50() {
        return heuresHs50;
    }

    public void setHeuresHs50(double heuresHs50) {
        this.heuresHs50 = heuresHs50;
    }

    public double getHeuresDimancheTotal() {
        return heuresDimancheTotal;
    }

    public void setHeuresDimancheTotal(double heuresDimancheTotal) {
        this.heuresDimancheTotal = heuresDimancheTotal;
    }

    public double getHeuresFerieTotal() {
        return heuresFerieTotal;
    }

    public void setHeuresFerieTotal(double heuresFerieTotal) {
        this.heuresFerieTotal = heuresFerieTotal;
    }

    public double getHeuresNuitTotal() {
        return heuresNuitTotal;
    }

    public void setHeuresNuitTotal(double heuresNuitTotal) {
        this.heuresNuitTotal = heuresNuitTotal;
    }

    public double getHeuresAbsenceRetenues() {
        return heuresAbsenceRetenues;
    }

    public void setHeuresAbsenceRetenues(double heuresAbsenceRetenues) {
        this.heuresAbsenceRetenues = heuresAbsenceRetenues;
    }

    public int getNbJoursDeplacement() {
        return nbJoursDeplacement;
    }

    public void setNbJoursDeplacement(int nbJoursDeplacement) {
        this.nbJoursDeplacement = nbJoursDeplacement;
    }

    public double getMontantDeplacement() {
        return montantDeplacement;
    }

    public void setMontantDeplacement(double montantDeplacement) {
        this.montantDeplacement = montantDeplacement;
    }

    public int getNbJoursRepas() {
        return nbJoursRepas;
    }

    public void setNbJoursRepas(int nbJoursRepas) {
        this.nbJoursRepas = nbJoursRepas;
    }

    public double getMontantRepas() {
        return montantRepas;
    }

    public void setMontantRepas(double montantRepas) {
        this.montantRepas = montantRepas;
    }

    public int getNbAnomaliesNonResolues() {
        return nbAnomaliesNonResolues;
    }

    public void setNbAnomaliesNonResolues(int nbAnomaliesNonResolues) {
        this.nbAnomaliesNonResolues = nbAnomaliesNonResolues;
    }

    // ─── Helpers Métier & Sérialisation JSON ──────────────────────────────────
    public boolean isPeriodeOuverte()  { return "OUVERTE".equalsIgnoreCase(statutPeriode); }
    public boolean isPeriodeCloturee() { return "CLOTUREE".equalsIgnoreCase(statutPeriode); }
    public boolean hasAnomalies()      { return nbAnomaliesNonResolues > 0; }
}
