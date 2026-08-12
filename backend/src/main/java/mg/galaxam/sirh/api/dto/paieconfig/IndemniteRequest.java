package mg.galaxam.sirh.api.dto.paieconfig;

public class IndemniteRequest {
    private String code;
    private String libelle;
    private boolean imposableIrsa;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }
    public boolean isImposableIrsa() { return imposableIrsa; }
    public void setImposableIrsa(boolean imposableIrsa) { this.imposableIrsa = imposableIrsa; }
}
