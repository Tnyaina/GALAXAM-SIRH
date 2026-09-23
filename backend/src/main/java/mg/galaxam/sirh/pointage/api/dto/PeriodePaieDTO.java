package mg.galaxam.sirh.pointage.api.dto;

import java.util.Map;
import mg.galaxam.sirh.common.dto.ActionCapability;

public class PeriodePaieDTO {
    private Integer idPeriodePaie;
    private String code;
    private String libelle;
    private String dateDebut;
    private String dateFin;
    private Integer idStatutPeriode;
    private String statutCode;
    private String statutLibelle;
    private Integer nbJoursOuvrables;
    private Integer nbJoursCalendaires;
    private Integer nbJoursFeries;
    private Integer version;
    private Map<String, ActionCapability> availableActions = Map.of();

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

    public Integer getNbJoursOuvrables() {
        return nbJoursOuvrables;
    }

    public void setNbJoursOuvrables(Integer nbJoursOuvrables) {
        this.nbJoursOuvrables = nbJoursOuvrables;
    }

    public Integer getNbJoursCalendaires() {
        return nbJoursCalendaires;
    }

    public void setNbJoursCalendaires(Integer nbJoursCalendaires) {
        this.nbJoursCalendaires = nbJoursCalendaires;
    }

    public Integer getNbJoursFeries() {
        return nbJoursFeries;
    }

    public void setNbJoursFeries(Integer nbJoursFeries) {
        this.nbJoursFeries = nbJoursFeries;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Map<String, ActionCapability> getAvailableActions() {
        return availableActions;
    }

    public void setAvailableActions(Map<String, ActionCapability> availableActions) {
        this.availableActions = availableActions == null ? Map.of() : Map.copyOf(availableActions);
    }

    private String motifRejet;

    public String getMotifRejet() {
        return motifRejet;
    }

    public void setMotifRejet(String motifRejet) {
        this.motifRejet = motifRejet;
    }
}
