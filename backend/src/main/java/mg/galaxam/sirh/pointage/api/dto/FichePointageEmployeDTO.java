package mg.galaxam.sirh.pointage.api.dto;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public class FichePointageEmployeDTO {
    private List<Map<String, String>> statutsCorrection = List.of();
    public List<Map<String, String>> getStatutsCorrection() { return statutsCorrection; }
    public void setStatutsCorrection(List<Map<String, String>> value) { statutsCorrection = value; }
    private PointageSyntheseMensuelleDTO soldePeriode;
    private Map<String, Boolean> availableActions = new LinkedHashMap<>();
    private Map<String, java.math.BigDecimal> totauxIndemnites = new LinkedHashMap<>();
    public PointageSyntheseMensuelleDTO getSoldePeriode() { return soldePeriode; }
    public void setSoldePeriode(PointageSyntheseMensuelleDTO value) { soldePeriode = value; }
    public Map<String, Boolean> getAvailableActions() { return availableActions; }
    public void setAvailableActions(Map<String, Boolean> value) { availableActions = value; }
    public Map<String, java.math.BigDecimal> getTotauxIndemnites() { return totauxIndemnites; }
    public void setTotauxIndemnites(Map<String, java.math.BigDecimal> value) { totauxIndemnites = value; }
    private Integer idPeriode;
    private Integer idEmploye;
    private String codePeriode;
    private String libellePeriode;
    private String dateDebut;
    private String dateFin;
    private String statutPeriode;
    private Map<String, String> kpis;
    private Map<String, String> recap;
    private Map<String, String> reglesAppliquees;
    private String matricule;
    private String nom;
    private String prenom;
    private String nomComplet;
    private String siteNom;
    private String fonction;
    private String categorie;
    private List<PointageJourDTO> pointagesData;
    private Map<String, IndemniteFicheDTO> indemnitesDisponibles = new LinkedHashMap<>();
    private List<PointageTronconSemaineDTO> bilansHebdo;

    public record IndemniteFicheDTO(String code, String periodicite, String libelle) {
        public IndemniteFicheDTO(String code, String periodicite) { this(code, periodicite, code); }
    }

    public Integer getIdEmploye() { return idEmploye; }
    public void setIdEmploye(Integer idEmploye) { this.idEmploye = idEmploye; }

    public String getCodePeriode() { return codePeriode; }
    public void setCodePeriode(String codePeriode) { this.codePeriode = codePeriode; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getPrenom() { return prenom; }
    public void setPrenom(String prenom) { this.prenom = prenom; }

    public String getMatricule() { return matricule; }
    public void setMatricule(String matricule) { this.matricule = matricule; }

    public String getNomComplet() { return nomComplet; }
    public void setNomComplet(String nomComplet) { this.nomComplet = nomComplet; }

    public String getSiteNom() { return siteNom; }
    public void setSiteNom(String siteNom) { this.siteNom = siteNom; }

    public String getFonction() { return fonction; }
    public void setFonction(String fonction) { this.fonction = fonction; }

    public String getCategorie() { return categorie; }
    public void setCategorie(String categorie) { this.categorie = categorie; }

    public List<PointageTronconSemaineDTO> getBilansHebdo() { return bilansHebdo; }
    public void setBilansHebdo(List<PointageTronconSemaineDTO> bilansHebdo) { this.bilansHebdo = bilansHebdo; }

    public Integer getIdPeriode() { return idPeriode; }
    public void setIdPeriode(Integer idPeriode) { this.idPeriode = idPeriode; }

    public String getLibellePeriode() { return libellePeriode; }
    public void setLibellePeriode(String libellePeriode) { this.libellePeriode = libellePeriode; }

    public String getDateDebut() { return dateDebut; }
    public void setDateDebut(String dateDebut) { this.dateDebut = dateDebut; }

    public String getDateFin() { return dateFin; }
    public void setDateFin(String dateFin) { this.dateFin = dateFin; }

    public String getStatutPeriode() { return statutPeriode; }
    public void setStatutPeriode(String statutPeriode) { this.statutPeriode = statutPeriode; }

    public Map<String, String> getKpis() { return kpis; }
    public void setKpis(Map<String, String> kpis) { this.kpis = kpis; }

    public Map<String, String> getRecap() { return recap; }
    public void setRecap(Map<String, String> recap) { this.recap = recap; }

    public Map<String, String> getReglesAppliquees() { return reglesAppliquees; }
    public void setReglesAppliquees(Map<String, String> reglesAppliquees) { this.reglesAppliquees = reglesAppliquees; }

    public List<PointageJourDTO> getPointagesData() { return pointagesData; }
    public void setPointagesData(List<PointageJourDTO> pointagesData) { this.pointagesData = pointagesData; }

    public Map<String, IndemniteFicheDTO> getIndemnitesDisponibles() { return indemnitesDisponibles; }
    public void setIndemnitesDisponibles(Map<String, IndemniteFicheDTO> indemnitesDisponibles) {
        this.indemnitesDisponibles = indemnitesDisponibles == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(indemnitesDisponibles);
    }
}
