package mg.galaxam.sirh.api.dto.securite;

public class RegisterRequest {
    private String identifiant;
    private String motDePasse;
    private Integer idRole; // Rôle assigné à la création

    // Getters
    public String getIdentifiant() { return identifiant; }
    public String getMotDePasse() { return motDePasse; }
    public Integer getIdRole() { return idRole; }

    // Setters
    public void setIdentifiant(String identifiant) { this.identifiant = identifiant; }
    public void setMotDePasse(String motDePasse) { this.motDePasse = motDePasse; }
    public void setIdRole(Integer idRole) { this.idRole = idRole; }
}
