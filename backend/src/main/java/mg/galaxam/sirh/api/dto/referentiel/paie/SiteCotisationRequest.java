package mg.galaxam.sirh.api.dto.referentiel.paie;

public class SiteCotisationRequest {
    private Integer idSite;
    private Integer idTypeCotisation;

    public Integer getIdSite() { return idSite; }
    public void setIdSite(Integer idSite) { this.idSite = idSite; }
    public Integer getIdTypeCotisation() { return idTypeCotisation; }
    public void setIdTypeCotisation(Integer idTypeCotisation) { this.idTypeCotisation = idTypeCotisation; }
}
