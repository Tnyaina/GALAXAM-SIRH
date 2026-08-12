package mg.galaxam.sirh.api.dto.organisation;

public class FilialeRequest {
    private String codeFiliale;
    private String raisonSociale;
    private String nif;
    private String stat;
    private Integer seuilEffectifAlerte;

    public String getCodeFiliale() { return codeFiliale; }
    public void setCodeFiliale(String codeFiliale) { this.codeFiliale = codeFiliale; }

    public String getRaisonSociale() { return raisonSociale; }
    public void setRaisonSociale(String raisonSociale) { this.raisonSociale = raisonSociale; }

    public String getNif() { return nif; }
    public void setNif(String nif) { this.nif = nif; }

    public String getStat() { return stat; }
    public void setStat(String stat) { this.stat = stat; }

    public Integer getSeuilEffectifAlerte() { return seuilEffectifAlerte; }
    public void setSeuilEffectifAlerte(Integer seuilEffectifAlerte) { this.seuilEffectifAlerte = seuilEffectifAlerte; }
}
