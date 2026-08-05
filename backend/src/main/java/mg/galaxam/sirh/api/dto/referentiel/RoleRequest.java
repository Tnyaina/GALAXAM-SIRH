package mg.galaxam.sirh.api.dto.referentiel;

public class RoleRequest {
    private String codeRole;
    private String libelle;

    // Getters
    public String getCodeRole() { return codeRole; }
    public String getLibelle() { return libelle; }

    // Setters
    public void setCodeRole(String codeRole) { this.codeRole = codeRole; }
    public void setLibelle(String libelle) { this.libelle = libelle; }
}
