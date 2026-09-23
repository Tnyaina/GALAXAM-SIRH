package mg.galaxam.sirh.securite.api.dto;

public class UtilisateurRequest {
    private String identifiant;
    private String motDePasse;
    private Integer idRole;
    private String motifDesactivation;

    // Getters
    public String getIdentifiant() { return identifiant; }
    public String getMotDePasse() { return motDePasse; }
    public Integer getIdRole() { return idRole; }
    public String getMotifDesactivation() { return motifDesactivation; }

    // Setters
    public void setIdentifiant(String identifiant) { this.identifiant = identifiant; }
    public void setMotDePasse(String motDePasse) { this.motDePasse = motDePasse; }
    public void setIdRole(Integer idRole) { this.idRole = idRole; }
    public void setMotifDesactivation(String motifDesactivation) { this.motifDesactivation = motifDesactivation; }
}
