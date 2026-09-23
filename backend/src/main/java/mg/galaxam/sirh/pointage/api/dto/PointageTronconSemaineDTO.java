package mg.galaxam.sirh.pointage.api.dto;

import java.io.Serializable;
import java.sql.Date;

public class PointageTronconSemaineDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer idEmploye;
    private String matricule;
    private String nom;
    private String prenom;
    private String photoUrl;
    private Integer idSite;
    private String siteNom;
    private String codeCategorie;
    private boolean estCadre;

    private Integer idPeriodePaie;
    private String debutSemaine;
    private String finSemaine;
    private int numeroSemaine;
    private int nbJoursTroncon;
    private double seuilHeuresTroncon;
    private double nbJoursConge;
    private int nbJoursFerie;

    private double totalHeuresTravaillees;
    private double heuresRetard;
    private double heuresHsBrutes;
    private double heuresCompensees;
    private double heuresHsNettes;
    private double heuresDeficit;

    private double heuresHs30;
    private double heuresHs50;
    private double heuresDimanche;
    private double heuresFerie;
    private double heuresNuit;

    public PointageTronconSemaineDTO() {}

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

    public boolean isEstCadre() {
        return estCadre;
    }

    public void setEstCadre(boolean estCadre) {
        this.estCadre = estCadre;
    }

    public Integer getIdPeriodePaie() {
        return idPeriodePaie;
    }

    public void setIdPeriodePaie(Integer idPeriodePaie) {
        this.idPeriodePaie = idPeriodePaie;
    }

    public String getDebutSemaine() {
        return debutSemaine;
    }

    public void setDebutSemaine(String debutSemaine) {
        this.debutSemaine = debutSemaine;
    }

    public void setDebutSemaine(Date debutSemaine) {
        this.debutSemaine = debutSemaine != null ? debutSemaine.toString() : null;
    }

    public String getFinSemaine() {
        return finSemaine;
    }

    public void setFinSemaine(String finSemaine) {
        this.finSemaine = finSemaine;
    }

    public void setFinSemaine(Date finSemaine) {
        this.finSemaine = finSemaine != null ? finSemaine.toString() : null;
    }

    public int getNumeroSemaine() {
        return numeroSemaine;
    }

    public void setNumeroSemaine(int numeroSemaine) {
        this.numeroSemaine = numeroSemaine;
    }

    public int getNbJoursTroncon() {
        return nbJoursTroncon;
    }

    public void setNbJoursTroncon(int nbJoursTroncon) {
        this.nbJoursTroncon = nbJoursTroncon;
    }

    public double getSeuilHeuresTroncon() {
        return seuilHeuresTroncon;
    }

    public void setSeuilHeuresTroncon(double seuilHeuresTroncon) {
        this.seuilHeuresTroncon = seuilHeuresTroncon;
    }

    public double getTotalHeuresTravaillees() {
        return totalHeuresTravaillees;
    }

    public void setTotalHeuresTravaillees(double totalHeuresTravaillees) {
        this.totalHeuresTravaillees = totalHeuresTravaillees;
    }

    public double getHeuresRetard() {
        return heuresRetard;
    }

    public void setHeuresRetard(double heuresRetard) {
        this.heuresRetard = heuresRetard;
    }

    public double getHeuresHsBrutes() {
        return heuresHsBrutes;
    }

    public void setHeuresHsBrutes(double heuresHsBrutes) {
        this.heuresHsBrutes = heuresHsBrutes;
    }

    public double getHeuresCompensees() {
        return heuresCompensees;
    }

    public void setHeuresCompensees(double heuresCompensees) {
        this.heuresCompensees = heuresCompensees;
    }

    public double getHeuresHsNettes() {
        return heuresHsNettes;
    }

    public void setHeuresHsNettes(double heuresHsNettes) {
        this.heuresHsNettes = heuresHsNettes;
    }

    public double getHeuresDeficit() {
        return heuresDeficit;
    }

    public void setHeuresDeficit(double heuresDeficit) {
        this.heuresDeficit = heuresDeficit;
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

    public double getHeuresDimanche() {
        return heuresDimanche;
    }

    public void setHeuresDimanche(double heuresDimanche) {
        this.heuresDimanche = heuresDimanche;
    }

    public double getHeuresFerie() {
        return heuresFerie;
    }

    public void setHeuresFerie(double heuresFerie) {
        this.heuresFerie = heuresFerie;
    }

    public double getHeuresNuit() {
        return heuresNuit;
    }

    public void setHeuresNuit(double heuresNuit) {
        this.heuresNuit = heuresNuit;
    }

    public double getNbJoursConge() {
        return nbJoursConge;
    }

    public void setNbJoursConge(double nbJoursConge) {
        this.nbJoursConge = nbJoursConge;
    }

    public int getNbJoursFerie() {
        return nbJoursFerie;
    }

    public void setNbJoursFerie(int nbJoursFerie) {
        this.nbJoursFerie = nbJoursFerie;
    }
}
