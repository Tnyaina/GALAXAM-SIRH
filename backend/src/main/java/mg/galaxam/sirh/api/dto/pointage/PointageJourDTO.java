package mg.galaxam.sirh.api.dto.pointage;

import java.math.BigDecimal;

public class PointageJourDTO {
    private Integer idPointage;
    private String date;        // Format "dd/MM" ex "24/08"
    private String dateComplete;// Format "YYYY-MM-DD"
    private String jour;        // ex "Lun."
    private String entree;      // ex "08:00"
    private String sortiePause; // ex "12:00"
    private String retourPause; // ex "13:00"
    private String sortie;      // ex "17:00"
    private String heures;      // ex "08h00"
    private String ecart;       // ex "+05 min" ou "-10 min" ou "00 min"
    private boolean ecartPositif;
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
    public Integer getIdPointage() { return idPointage; }
    public void setIdPointage(Integer idPointage) { this.idPointage = idPointage; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getDateComplete() { return dateComplete; }
    public void setDateComplete(String dateComplete) { this.dateComplete = dateComplete; }

    public String getJour() { return jour; }
    public void setJour(String jour) { this.jour = jour; }

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

    // ─── Helpers Métier & Sérialisation JSON ──────────────────────────────────
    public boolean isPresent()  { return "PRESENT".equalsIgnoreCase(statut); }
    public boolean isRetard()   { return "RETARD".equalsIgnoreCase(statut) || (isPresent() && !ecartPositif && ecart != null && !ecart.startsWith("00")); }
    public boolean isAbsent()   { return "ABSENT".equalsIgnoreCase(statut) || "ABSENCE_NON_JUSTIFIEE".equalsIgnoreCase(statut); }
    public boolean isConge()    { return "CONGE".equalsIgnoreCase(statut); }
    public boolean isRepos()    { return "REPOS".equalsIgnoreCase(statut); }
    public boolean isEnCours()  { return "EN_COURS".equalsIgnoreCase(statut); }
    public boolean isAnomalie() { return "ANOMALIE".equalsIgnoreCase(statut) || "ANOMALIE_SAISIE".equalsIgnoreCase(statut) || isRetard() || isAbsent(); }
}
