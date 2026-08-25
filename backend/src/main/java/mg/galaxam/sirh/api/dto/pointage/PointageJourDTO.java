package mg.galaxam.sirh.api.dto.pointage;

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

    // Getters et Setters
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
}
