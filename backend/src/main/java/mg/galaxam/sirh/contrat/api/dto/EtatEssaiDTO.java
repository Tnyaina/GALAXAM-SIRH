package mg.galaxam.sirh.contrat.api.dto;

/** État et actions de période d'essai calculés par le serveur pour l'interface. */
public class EtatEssaiDTO {
    // Champs historiques (compatibilité)
    private boolean isEssaiActif;
    private boolean isDernierJour;
    private boolean isEssaiEchu;
    private boolean estConfirme;
    private boolean estRenouvele;
    private String dateFinEssaiStr;
    private Integer dureeEssaiMois;
    private boolean canConfirm;
    private boolean canRenew;

    // Nouveaux champs enrichis
    private int periodeActuelle; // Numéro de la période EN_COURS (0 si aucune)
    private int nombreRenouvellements; // Nb de renouvellements déjà effectués
    private int maxRenouvellements; // Valeur de MAX_RENOUVELLEMENTS_ESSAI
    private boolean aPeriodeEssai; // true = une PE est EN_COURS sur ce contrat
    private String dateFinEssai; // Alias de dateFinEssaiStr pour cohérence frontend
    private Integer joursRestants; // >0 = reste X jours, <0 = dépassé de X jours, null = inconnu

    public EtatEssaiDTO() {
    }

    /** Constructeur complet (all-args) */
    public EtatEssaiDTO(boolean isEssaiActif, boolean isDernierJour, boolean isEssaiEchu,
            boolean estConfirme, boolean estRenouvele, String dateFinEssaiStr,
            Integer dureeEssaiMois, boolean canConfirm, boolean canRenew,
            int periodeActuelle, int nombreRenouvellements, int maxRenouvellements,
            boolean aPeriodeEssai, String dateFinEssai) {
        this.isEssaiActif = isEssaiActif;
        this.isDernierJour = isDernierJour;
        this.isEssaiEchu = isEssaiEchu;
        this.estConfirme = estConfirme;
        this.estRenouvele = estRenouvele;
        this.dateFinEssaiStr = dateFinEssaiStr;
        this.dureeEssaiMois = dureeEssaiMois;
        this.canConfirm = canConfirm;
        this.canRenew = canRenew;
        this.periodeActuelle = periodeActuelle;
        this.nombreRenouvellements = nombreRenouvellements;
        this.maxRenouvellements = maxRenouvellements;
        this.aPeriodeEssai = aPeriodeEssai;
        this.dateFinEssai = dateFinEssai;
        // Calculer joursRestants si la date est disponible
        if (dateFinEssai != null) {
            try {
                long diff = java.time.temporal.ChronoUnit.DAYS.between(
                        java.time.LocalDate.now(),
                        java.time.LocalDate.parse(dateFinEssai.substring(0, 10)));
                this.joursRestants = (int) diff; // >0 reste, <0 dépassé, 0 dernier jour
            } catch (Exception ignored) {
                this.joursRestants = null;
            }
        }
    }

    /** Constructeur léger — retro-compat (9 paramètres) */
    public EtatEssaiDTO(boolean isEssaiActif, boolean isDernierJour, boolean isEssaiEchu,
            boolean estConfirme, boolean estRenouvele, String dateFinEssaiStr,
            Integer dureeEssaiMois, boolean canConfirm, boolean canRenew) {
        this(isEssaiActif, isDernierJour, isEssaiEchu, estConfirme, estRenouvele,
                dateFinEssaiStr, dureeEssaiMois, canConfirm, canRenew,
                0, 0, 1, isEssaiActif, dateFinEssaiStr);
    }

    /** Factory : état vide (aucune période d'essai) */
    public static EtatEssaiDTO vide() {
        return new EtatEssaiDTO(false, false, false, false, false, null, null, false, false);
    }

    public boolean isIsEssaiActif() {
        return isEssaiActif;
    }

    public void setIsEssaiActif(boolean isEssaiActif) {
        this.isEssaiActif = isEssaiActif;
    }

    public boolean isIsDernierJour() {
        return isDernierJour;
    }

    public void setIsDernierJour(boolean isDernierJour) {
        this.isDernierJour = isDernierJour;
    }

    public boolean isIsEssaiEchu() {
        return isEssaiEchu;
    }

    public void setIsEssaiEchu(boolean isEssaiEchu) {
        this.isEssaiEchu = isEssaiEchu;
    }

    public boolean isEstConfirme() {
        return estConfirme;
    }

    public void setEstConfirme(boolean estConfirme) {
        this.estConfirme = estConfirme;
    }

    public boolean isEstRenouvele() {
        return estRenouvele;
    }

    public void setEstRenouvele(boolean estRenouvele) {
        this.estRenouvele = estRenouvele;
    }

    public String getDateFinEssaiStr() {
        return dateFinEssaiStr;
    }

    public void setDateFinEssaiStr(String dateFinEssaiStr) {
        this.dateFinEssaiStr = dateFinEssaiStr;
    }

    public Integer getDureeEssaiMois() {
        return dureeEssaiMois;
    }

    public void setDureeEssaiMois(Integer dureeEssaiMois) {
        this.dureeEssaiMois = dureeEssaiMois;
    }

    public boolean isCanConfirm() {
        return canConfirm;
    }

    public void setCanConfirm(boolean canConfirm) {
        this.canConfirm = canConfirm;
    }

    public boolean isCanRenew() {
        return canRenew;
    }

    public void setCanRenew(boolean canRenew) {
        this.canRenew = canRenew;
    }

    // Nouveaux getters/setters
    public int getPeriodeActuelle() {
        return periodeActuelle;
    }

    public void setPeriodeActuelle(int periodeActuelle) {
        this.periodeActuelle = periodeActuelle;
    }

    public int getNombreRenouvellements() {
        return nombreRenouvellements;
    }

    public void setNombreRenouvellements(int nombreRenouvellements) {
        this.nombreRenouvellements = nombreRenouvellements;
    }

    public int getMaxRenouvellements() {
        return maxRenouvellements;
    }

    public void setMaxRenouvellements(int maxRenouvellements) {
        this.maxRenouvellements = maxRenouvellements;
    }

    public boolean isAPeriodeEssai() {
        return aPeriodeEssai;
    }

    public void setAPeriodeEssai(boolean aPeriodeEssai) {
        this.aPeriodeEssai = aPeriodeEssai;
    }

    public String getDateFinEssai() {
        return dateFinEssai;
    }

    public void setDateFinEssai(String dateFinEssai) {
        this.dateFinEssai = dateFinEssai;
    }

    public Integer getJoursRestants() {
        return joursRestants;
    }

    public void setJoursRestants(Integer joursRestants) {
        this.joursRestants = joursRestants;
    }
}
