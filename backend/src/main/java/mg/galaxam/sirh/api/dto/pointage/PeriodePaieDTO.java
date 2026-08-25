package mg.galaxam.sirh.api.dto.pointage;

public class PeriodePaieDTO {
    private Integer idPeriodePaie;
    private String code;
    private String libelle;
    private String dateDebut;
    private String dateFin;
    private Integer idStatutPeriode;
    private String statutCode;
    private String statutLibelle;

    public PeriodePaieDTO() {
    }

    public Integer getIdPeriodePaie() {
        return idPeriodePaie;
    }

    public void setIdPeriodePaie(Integer idPeriodePaie) {
        this.idPeriodePaie = idPeriodePaie;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getLibelle() {
        return libelle;
    }

    public void setLibelle(String libelle) {
        this.libelle = libelle;
    }

    public String getDateDebut() {
        return dateDebut;
    }

    public void setDateDebut(String dateDebut) {
        this.dateDebut = dateDebut;
    }

    public String getDateFin() {
        return dateFin;
    }

    public void setDateFin(String dateFin) {
        this.dateFin = dateFin;
    }

    public Integer getIdStatutPeriode() {
        return idStatutPeriode;
    }

    public void setIdStatutPeriode(Integer idStatutPeriode) {
        this.idStatutPeriode = idStatutPeriode;
    }

    public String getStatutCode() {
        return statutCode;
    }

    public void setStatutCode(String statutCode) {
        this.statutCode = statutCode;
    }

    public String getStatutLibelle() {
        return statutLibelle;
    }

    public void setStatutLibelle(String statutLibelle) {
        this.statutLibelle = statutLibelle;
    }
}
