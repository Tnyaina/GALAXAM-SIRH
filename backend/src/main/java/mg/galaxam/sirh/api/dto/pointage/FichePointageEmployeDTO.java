package mg.galaxam.sirh.api.dto.pointage;

import java.util.List;
import java.util.Map;

public class FichePointageEmployeDTO {
    private Map<String, String> kpis;
    private Map<String, String> recap;
    private Map<String, String> reglesAppliquees;
    private List<PointageJourDTO> pointagesData;

    public Map<String, String> getKpis() { return kpis; }
    public void setKpis(Map<String, String> kpis) { this.kpis = kpis; }

    public Map<String, String> getRecap() { return recap; }
    public void setRecap(Map<String, String> recap) { this.recap = recap; }

    public Map<String, String> getReglesAppliquees() { return reglesAppliquees; }
    public void setReglesAppliquees(Map<String, String> reglesAppliquees) { this.reglesAppliquees = reglesAppliquees; }

    public List<PointageJourDTO> getPointagesData() { return pointagesData; }
    public void setPointagesData(List<PointageJourDTO> pointagesData) { this.pointagesData = pointagesData; }
}
