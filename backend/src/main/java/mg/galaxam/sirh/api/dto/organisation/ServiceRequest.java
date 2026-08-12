package mg.galaxam.sirh.api.dto.organisation;

public class ServiceRequest {
    private String codeService;
    private String libelle;
    private Integer idDepartement;

    public String getCodeService() { return codeService; }
    public void setCodeService(String codeService) { this.codeService = codeService; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public Integer getIdDepartement() { return idDepartement; }
    public void setIdDepartement(Integer idDepartement) { this.idDepartement = idDepartement; }
}
