package mg.galaxam.sirh.pointage.api.dto;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public class PointageJourDTO {
    private String observation;
    public String getObservation() { return observation; }
    public void setObservation(String value) { observation = value; }
    private boolean canCorrect;
    public boolean isCanCorrect() { return canCorrect; }
    public void setCanCorrect(boolean value) { canCorrect = value; }
    private boolean congeApprouve;
    public boolean isCongeApprouve() { return congeApprouve; }
    public void setCongeApprouve(boolean value) { congeApprouve = value; }
    private Integer idPointage;
    private Integer idSite;
    private Integer version;
    private String date;        // Format "dd/MM" ex "24/08"
    private String dateComplete;// Format "YYYY-MM-DD"
    private String jour;        // ex "Lun."
    private boolean weekend;    // true si samedi ou dimanche (non ouvré)
    private String entree;      // ex "08:00"
    private String sortiePause; // ex "12:00"
    private String retourPause; // ex "13:00"
    private String sortie;      // ex "17:00"
    private String heures;      // ex "08h00"
    private String ecart;       // ex "+05 min" ou "-10 min" ou "00 min" (différence temps travaillé vs norme)
    private boolean ecartPositif;
    private Integer minutesRetard; // Retard d'embauche le matin en minutes
    private String statut;      // PRESENT, RETARD, ABSENT, CONGE, REPOS, EN_COURS

    // Indemnités journalières
    private boolean deplacementMatin;
    private boolean deplacementAm;
    private boolean indemniteRepas;
    private int nbRepas;                // 1 = Repas standard, 2 = Repas + 2e Repas/Nuit
    private BigDecimal montantRepas;
    private boolean indemniteDeplacement;
    private BigDecimal montantDeplacement;
    private boolean horaireContinuJour; // Indique si la journée a été réalisée en continu (sans pause méridienne)
    private Double totalHeuresDecimal;  // Heures en centésimal (décimal) ex: 8.85
    private Map<String, IndemniteJourDTO> indemnites = new LinkedHashMap<>();

    public record IndemniteJourDTO(String code, boolean accordee, BigDecimal montant, BigDecimal quantite) {
        public IndemniteJourDTO(String code, boolean accordee, BigDecimal montant) { this(code, accordee, montant, null); }
    }

    // Getters et Setters
    public Double getTotalHeuresDecimal() { return totalHeuresDecimal; }
    public void setTotalHeuresDecimal(Double totalHeuresDecimal) { this.totalHeuresDecimal = totalHeuresDecimal; }

    public boolean isHoraireContinuJour() { return horaireContinuJour; }
    public void setHoraireContinuJour(boolean horaireContinuJour) { this.horaireContinuJour = horaireContinuJour; }
    public boolean isDeplacementMatin() { return deplacementMatin; }
    public void setDeplacementMatin(boolean deplacementMatin) { this.deplacementMatin = deplacementMatin; }

    public boolean isDeplacementAm() { return deplacementAm; }
    public void setDeplacementAm(boolean deplacementAm) { this.deplacementAm = deplacementAm; }

    public boolean isIndemniteRepas() { return indemniteRepas; }
    public void setIndemniteRepas(boolean indemniteRepas) { this.indemniteRepas = indemniteRepas; }

    public int getNbRepas() { return nbRepas; }
    public void setNbRepas(int nbRepas) { this.nbRepas = nbRepas; }

    public BigDecimal getMontantRepas() { return montantRepas; }
    public void setMontantRepas(BigDecimal montantRepas) { this.montantRepas = montantRepas; }

    public boolean isIndemniteDeplacement() { return indemniteDeplacement; }
    public void setIndemniteDeplacement(boolean indemniteDeplacement) { this.indemniteDeplacement = indemniteDeplacement; }

    public BigDecimal getMontantDeplacement() { return montantDeplacement; }
    public void setMontantDeplacement(BigDecimal montantDeplacement) { this.montantDeplacement = montantDeplacement; }
    public Map<String, IndemniteJourDTO> getIndemnites() { return indemnites; }
    public void setIndemnites(Map<String, IndemniteJourDTO> indemnites) {
        this.indemnites = indemnites == null ? new LinkedHashMap<>() : new LinkedHashMap<>(indemnites);
    }
    public Integer getIdPointage() { return idPointage; }
    public void setIdPointage(Integer idPointage) { this.idPointage = idPointage; }
    public Integer getIdSite() { return idSite; }
    public void setIdSite(Integer idSite) { this.idSite = idSite; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getDateComplete() { return dateComplete; }
    public void setDateComplete(String dateComplete) { this.dateComplete = dateComplete; }

    public String getJour() { return jour; }
    public void setJour(String jour) { this.jour = jour; }

    public boolean isWeekend() { return weekend; }
    public void setWeekend(boolean weekend) { this.weekend = weekend; }

    public String getEntree() { return entree; }
    public void setEntree(String entree) { this.entree = entree; }

    public String getSortiePause() { return sortiePause; }
    public void setSortiePause(String sortiePause) { this.sortiePause = sortiePause; }

    public String getRetourPause() { return retourPause; }
    public void setRetourPause(String retourPause) { this.retourPause = retourPause; }

    public String getSortie() { return sortie; }
    public void setSortie(String sortie) { this.sortie = sortie; }

    public String getHeures() { return heures; }
    public void setHeures(String heures) { this.heures = heures; }

    public String getEcart() { return ecart; }
    public void setEcart(String ecart) { this.ecart = ecart; }

    public boolean isEcartPositif() { return ecartPositif; }
    public void setEcartPositif(boolean ecartPositif) { this.ecartPositif = ecartPositif; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public Integer getMinutesRetard() { return minutesRetard; }
    public void setMinutesRetard(Integer minutesRetard) { this.minutesRetard = minutesRetard; }

    // ─── Helpers Métier & Sérialisation JSON ──────────────────────────────────
    @com.fasterxml.jackson.annotation.JsonProperty("isPresent")
    public boolean isPresent()  { return "PRESENT".equalsIgnoreCase(statut); }

    @com.fasterxml.jackson.annotation.JsonProperty("isRetard")
    public boolean isRetard()   { return "RETARD".equalsIgnoreCase(statut) || (minutesRetard != null && minutesRetard > 0); }

    @com.fasterxml.jackson.annotation.JsonProperty("isAbsent")
    public boolean isAbsent()   { return "ABSENT".equalsIgnoreCase(statut) || "ABSENCE_NON_JUSTIFIEE".equalsIgnoreCase(statut); }

    @com.fasterxml.jackson.annotation.JsonProperty("isConge")
    public boolean isConge()    { return "CONGE".equalsIgnoreCase(statut) || "CONGE_PAYE".equalsIgnoreCase(statut); }

    @com.fasterxml.jackson.annotation.JsonProperty("isRepos")
    public boolean isRepos()    { return "REPOS".equalsIgnoreCase(statut); }

    @com.fasterxml.jackson.annotation.JsonProperty("isEnCours")
    public boolean isEnCours()  { return "EN_COURS".equalsIgnoreCase(statut); }

    @com.fasterxml.jackson.annotation.JsonProperty("isAnomalie")
    public boolean isAnomalie() { return "ANOMALIE".equalsIgnoreCase(statut) || "ANOMALIE_SAISIE".equalsIgnoreCase(statut) || isRetard() || isAbsent(); }

    @com.fasterxml.jackson.annotation.JsonProperty("isFerie")
    public boolean isFerie() {
        return "FERIE".equalsIgnoreCase(statut) || "FERIE_CHOME".equalsIgnoreCase(statut) || "FERIE_TRAVAILLE".equalsIgnoreCase(statut);
    }

    @com.fasterxml.jackson.annotation.JsonProperty("isFerieChome")
    public boolean isFerieChome() {
        return "FERIE_CHOME".equalsIgnoreCase(statut) || ("FERIE".equalsIgnoreCase(statut) && (heures == null || "00h00".equals(heures) || "—".equals(heures) || "-".equals(heures)));
    }

    @com.fasterxml.jackson.annotation.JsonProperty("isFerieTravaille")
    public boolean isFerieTravaille() {
        return "FERIE_TRAVAILLE".equalsIgnoreCase(statut);
    }

    @com.fasterxml.jackson.annotation.JsonProperty("isDimancheTravaille")
    public boolean isDimancheTravaille() {
        return "DIMANCHE_TRAVAILLE".equalsIgnoreCase(statut);
    }

    @com.fasterxml.jackson.annotation.JsonProperty("isSamediTravaille")
    public boolean isSamediTravaille() {
        return "SAMEDI_TRAVAILLE".equalsIgnoreCase(statut);
    }

    @com.fasterxml.jackson.annotation.JsonProperty("isDemiJournee")
    public boolean isDemiJournee() {
        return "DEMI_JOURNEE".equalsIgnoreCase(statut);
    }

    @com.fasterxml.jackson.annotation.JsonProperty("isDispensePointage")
    public boolean isDispensePointage() {
        return "DISPENSE_POINTAGE".equalsIgnoreCase(statut);
    }

    @com.fasterxml.jackson.annotation.JsonProperty("isPlanifie")
    public boolean isPlanifie() {
        return "PLANIFIE".equalsIgnoreCase(statut);
    }

    @com.fasterxml.jackson.annotation.JsonProperty("isNonEchu")
    public boolean isNonEchu() {
        return "NON_ECHU".equalsIgnoreCase(statut);
    }

    @com.fasterxml.jackson.annotation.JsonProperty("isNonConcerne")
    public boolean isNonConcerne() {
        return "NON_CONCERNE".equalsIgnoreCase(statut);
    }

    @com.fasterxml.jackson.annotation.JsonProperty("isMerged")
    public boolean isMerged() {
        return isConge() || isFerieChome() || isRepos() || isNonEchu();
    }

    @com.fasterxml.jackson.annotation.JsonProperty("isContinu")
    public boolean isContinu() {
        return horaireContinuJour || ("12:00".equals(sortiePause) && "12:00".equals(retourPause));
    }
}
