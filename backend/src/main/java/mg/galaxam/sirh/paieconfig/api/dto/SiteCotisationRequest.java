package mg.galaxam.sirh.paieconfig.api.dto;

public class SiteCotisationRequest {
    private Integer idSite;
    private Integer idTypeCotisation;
    private Integer idOrganisme;

    public Integer getIdSite() { return idSite; }
    public void setIdSite(Integer idSite) { this.idSite = idSite; }
    public Integer getIdTypeCotisation() { return idTypeCotisation; }
    public void setIdTypeCotisation(Integer idTypeCotisation) { this.idTypeCotisation = idTypeCotisation; }
    public Integer getIdOrganisme() { return idOrganisme; }
    public void setIdOrganisme(Integer idOrganisme) { this.idOrganisme = idOrganisme; }
}
