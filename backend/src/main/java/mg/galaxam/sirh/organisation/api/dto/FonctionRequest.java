package mg.galaxam.sirh.organisation.api.dto;

public class FonctionRequest {
    private String codeFonction;
    private String libelle;
    private boolean dispensePointage;

    public String getCodeFonction() { return codeFonction; }
    public void setCodeFonction(String codeFonction) { this.codeFonction = codeFonction; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public boolean isDispensePointage() { return dispensePointage; }
    public void setDispensePointage(boolean dispensePointage) { this.dispensePointage = dispensePointage; }
}
