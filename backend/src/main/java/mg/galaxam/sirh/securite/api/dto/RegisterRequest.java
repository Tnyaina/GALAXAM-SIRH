package mg.galaxam.sirh.securite.api.dto;

public class RegisterRequest {
    private String identifiant;
    private String email;
    private String motDePasse;
    private Integer idRole; // Rôle assigné à la création

    // Getters
    public String getIdentifiant() { return identifiant; }
    public String getEmail() { return email; }
    public String getMotDePasse() { return motDePasse; }
    public Integer getIdRole() { return idRole; }

    // Setters
    public void setIdentifiant(String identifiant) { this.identifiant = identifiant; }
    public void setEmail(String email) { this.email = email; }
    public void setMotDePasse(String motDePasse) { this.motDePasse = motDePasse; }
    public void setIdRole(Integer idRole) { this.idRole = idRole; }
}
