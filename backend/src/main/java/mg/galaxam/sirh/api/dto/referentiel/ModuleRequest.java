package mg.galaxam.sirh.api.dto.referentiel;

public class ModuleRequest {
    private String codeModule;
    private String libelle;

    // Getters
    public String getCodeModule() { return codeModule; }
    public String getLibelle() { return libelle; }

    // Setters
    public void setCodeModule(String codeModule) { this.codeModule = codeModule; }
    public void setLibelle(String libelle) { this.libelle = libelle; }
}
