package mg.galaxam.sirh.api.dto.referentiel.rh;

public class BanqueRequest {
    private String codeBanque;
    private String nomBanque;
    private String codeSwift;

    public String getCodeBanque() { return codeBanque; }
    public void setCodeBanque(String codeBanque) { this.codeBanque = codeBanque; }

    public String getNomBanque() { return nomBanque; }
    public void setNomBanque(String nomBanque) { this.nomBanque = nomBanque; }

    public String getCodeSwift() { return codeSwift; }
    public void setCodeSwift(String codeSwift) { this.codeSwift = codeSwift; }
}
