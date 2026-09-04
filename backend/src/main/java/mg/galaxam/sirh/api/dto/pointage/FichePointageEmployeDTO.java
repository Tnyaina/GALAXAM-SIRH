package mg.galaxam.sirh.api.dto.pointage;

import java.util.List;
import java.util.Map;

public class FichePointageEmployeDTO {
    private Integer idPeriode;
    private String libellePeriode;
    private String dateDebut;
    private String dateFin;
    private String statutPeriode;
    private Map<String, String> kpis;
    private Map<String, String> recap;
    private Map<String, String> reglesAppliquees;
    private List<PointageJourDTO> pointagesData;

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
}
