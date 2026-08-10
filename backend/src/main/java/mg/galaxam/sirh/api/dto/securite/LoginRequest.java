package mg.galaxam.sirh.api.dto.securite;

public class LoginRequest {
    private String identifiant;
    private String motDePasse;

    // Getters et Setters
    public String getIdentifiant() { return identifiant; }
    public void setIdentifiant(String identifiant) { this.identifiant = identifiant; }
    public String getMotDePasse() { return motDePasse; }
    public void setMotDePasse(String motDePasse) { this.motDePasse = motDePasse; }
}
