package mg.galaxam.sirh.paieconfig.api.dto;

public class IndemniteRequest {
    private String code;
    private String libelle;
    private String codeRubriquePaie;
    private Boolean imposableIrsa;
    private Boolean soumisCotisation;
    private boolean fractionnable;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }
    public String getCodeRubriquePaie() { return codeRubriquePaie; }
    public void setCodeRubriquePaie(String codeRubriquePaie) { this.codeRubriquePaie = codeRubriquePaie; }
    public Boolean getImposableIrsa() { return imposableIrsa; }
    public void setImposableIrsa(Boolean imposableIrsa) { this.imposableIrsa = imposableIrsa; }
    public Boolean getSoumisCotisation() { return soumisCotisation; }
    public void setSoumisCotisation(Boolean soumisCotisation) { this.soumisCotisation = soumisCotisation; }
    public boolean isFractionnable() { return fractionnable; }
    public void setFractionnable(boolean fractionnable) { this.fractionnable = fractionnable; }
}
