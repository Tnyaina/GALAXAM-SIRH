package mg.galaxam.sirh.organisation.api.dto;

public class FilialeRequest {
    private String codeFiliale;
    private String raisonSociale;
    private String nif;
    private String stat;
    private String adresseSiege;
    private String rcs;
    private String telephone;
    private String email;
    private Integer seuilEffectifAlerte;

    public String getCodeFiliale() { return codeFiliale; }
    public void setCodeFiliale(String codeFiliale) { this.codeFiliale = codeFiliale; }

    public String getRaisonSociale() { return raisonSociale; }
    public void setRaisonSociale(String raisonSociale) { this.raisonSociale = raisonSociale; }

    public String getNif() { return nif; }
    public void setNif(String nif) { this.nif = nif; }

    public String getStat() { return stat; }
    public void setStat(String stat) { this.stat = stat; }

    public String getAdresseSiege() { return adresseSiege; }
    public void setAdresseSiege(String adresseSiege) { this.adresseSiege = adresseSiege; }
    public String getRcs() { return rcs; }
    public void setRcs(String rcs) { this.rcs = rcs; }
    public String getTelephone() { return telephone; }
    public void setTelephone(String telephone) { this.telephone = telephone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Integer getSeuilEffectifAlerte() { return seuilEffectifAlerte; }
    public void setSeuilEffectifAlerte(Integer seuilEffectifAlerte) { this.seuilEffectifAlerte = seuilEffectifAlerte; }
}
