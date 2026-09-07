package mg.galaxam.sirh.model.paie.dto.export;

import java.math.BigDecimal;

/**
 * DTO représentant une ligne de l'ordre de virement bancaire interbancaire.
 */
public class VirementRowDto {
    private int numeroOrdre;
    private String matricule;
    private String nomBeneficiaire;
    private String banque;
    private String codeBanque;
    private String codeGuichet;
    private String numeroCompte;
    private String cleRib;
    private String ribComplet;
    private BigDecimal netAPayer;
    private String devise;                  // MGA
    private String referencePaiement;
    private String motifVirement;

    public VirementRowDto() {
        this.devise = "MGA";
    }

    // Getters and Setters
    public int getNumeroOrdre() { return numeroOrdre; }
    public void setNumeroOrdre(int numeroOrdre) { this.numeroOrdre = numeroOrdre; }

    public String getMatricule() { return matricule; }
    public void setMatricule(String matricule) { this.matricule = matricule; }

    public String getNomBeneficiaire() { return nomBeneficiaire; }
    public void setNomBeneficiaire(String nomBeneficiaire) { this.nomBeneficiaire = nomBeneficiaire; }

    public String getBanque() { return banque; }
    public void setBanque(String banque) { this.banque = banque; }

    public String getCodeBanque() { return codeBanque; }
    public void setCodeBanque(String codeBanque) { this.codeBanque = codeBanque; }

    public String getCodeGuichet() { return codeGuichet; }
    public void setCodeGuichet(String codeGuichet) { this.codeGuichet = codeGuichet; }

    public String getNumeroCompte() { return numeroCompte; }
    public void setNumeroCompte(String numeroCompte) { this.numeroCompte = numeroCompte; }

    public String getCleRib() { return cleRib; }
    public void setCleRib(String cleRib) { this.cleRib = cleRib; }

    public String getRibComplet() { return ribComplet; }
    public void setRibComplet(String ribComplet) { this.ribComplet = ribComplet; }

    public BigDecimal getNetAPayer() { return netAPayer; }
    public void setNetAPayer(BigDecimal netAPayer) { this.netAPayer = netAPayer; }

    public String getDevise() { return devise; }
    public void setDevise(String devise) { this.devise = devise; }

    public String getReferencePaiement() { return referencePaiement; }
    public void setReferencePaiement(String referencePaiement) { this.referencePaiement = referencePaiement; }

    public String getMotifVirement() { return motifVirement; }
    public void setMotifVirement(String motifVirement) { this.motifVirement = motifVirement; }
}
