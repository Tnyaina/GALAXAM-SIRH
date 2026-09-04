package mg.galaxam.sirh.api.dto.pointage;

public class ToleranceRequest {

    private Integer idSite;
    private String heurePlafond; // Format "HH:mm" ou "HH:mm:ss"

    public Integer getIdSite() { return idSite; }
    public void setIdSite(Integer idSite) { this.idSite = idSite; }

    public String getHeurePlafond() { return heurePlafond; }
    public void setHeurePlafond(String heurePlafond) { this.heurePlafond = heurePlafond; }
}
