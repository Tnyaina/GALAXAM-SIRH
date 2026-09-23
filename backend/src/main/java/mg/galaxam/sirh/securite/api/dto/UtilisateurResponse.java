package mg.galaxam.sirh.securite.api.dto;

import java.sql.Timestamp;

public class UtilisateurResponse {
    private Integer idUtilisateur;
    private String identifiant;
    private Integer idRole;
    private String codeRole;
    private String roleLibelle;
    private boolean estActif;
    private Timestamp dateDesactivation;
    private String motifDesactivation;

    // Getters
    public Integer getIdUtilisateur() { return idUtilisateur; }
    public String getIdentifiant() { return identifiant; }
    public Integer getIdRole() { return idRole; }
    public String getCodeRole() { return codeRole; }
    public String getRoleLibelle() { return roleLibelle; }
    public boolean isEstActif() { return estActif; }
    public Timestamp getDateDesactivation() { return dateDesactivation; }
    public String getMotifDesactivation() { return motifDesactivation; }

    // Setters
    public void setIdUtilisateur(Integer idUtilisateur) { this.idUtilisateur = idUtilisateur; }
    public void setIdentifiant(String identifiant) { this.identifiant = identifiant; }
    public void setIdRole(Integer idRole) { this.idRole = idRole; }
    public void setCodeRole(String codeRole) { this.codeRole = codeRole; }
    public void setRoleLibelle(String roleLibelle) { this.roleLibelle = roleLibelle; }
    public void setEstActif(boolean estActif) { this.estActif = estActif; }
    public void setDateDesactivation(Timestamp dateDesactivation) { this.dateDesactivation = dateDesactivation; }
    public void setMotifDesactivation(String motifDesactivation) { this.motifDesactivation = motifDesactivation; }
}
