package mg.galaxam.sirh.paie.api.dto.stc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Vue synthétique de la situation financière des règlements d'un STC.
 * Expose le montant scellé, le total réglé, le solde restant à payer,
 * l'état d'acquittement et l'historique complet des versements.
 */
public class StcSituationReglementDTO {
    private Integer idStc;
    private Integer idDossierSortie;
    private Integer idEmploye;
    private String matricule;
    private String nomComplet;
    private String numeroRecu;
    private String statutStc;
    private String statutDossier;
    private BigDecimal netAPayerStc;
    private BigDecimal totalRegle;
    private BigDecimal resteAPayer;
    private boolean estIntegralementRegle;
    private List<StcReglementDTO> reglements = new ArrayList<>();

    public StcSituationReglementDTO() {}

    public Integer getIdStc() { return idStc; }
    public void setIdStc(Integer idStc) { this.idStc = idStc; }

    public Integer getIdDossierSortie() { return idDossierSortie; }
    public void setIdDossierSortie(Integer idDossierSortie) { this.idDossierSortie = idDossierSortie; }

    public Integer getIdEmploye() { return idEmploye; }
    public void setIdEmploye(Integer idEmploye) { this.idEmploye = idEmploye; }

    public String getMatricule() { return matricule; }
    public void setMatricule(String matricule) { this.matricule = matricule; }

    public String getNomComplet() { return nomComplet; }
    public void setNomComplet(String nomComplet) { this.nomComplet = nomComplet; }

    public String getNumeroRecu() { return numeroRecu; }
    public void setNumeroRecu(String numeroRecu) { this.numeroRecu = numeroRecu; }

    public String getStatutStc() { return statutStc; }
    public void setStatutStc(String statutStc) { this.statutStc = statutStc; }

    public String getStatutDossier() { return statutDossier; }
    public void setStatutDossier(String statutDossier) { this.statutDossier = statutDossier; }

    public BigDecimal getNetAPayerStc() { return netAPayerStc; }
    public void setNetAPayerStc(BigDecimal netAPayerStc) { this.netAPayerStc = netAPayerStc; }

    public BigDecimal getTotalRegle() { return totalRegle; }
    public void setTotalRegle(BigDecimal totalRegle) { this.totalRegle = totalRegle; }

    public BigDecimal getResteAPayer() { return resteAPayer; }
    public void setResteAPayer(BigDecimal resteAPayer) { this.resteAPayer = resteAPayer; }

    public boolean isEstIntegralementRegle() { return estIntegralementRegle; }
    public void setEstIntegralementRegle(boolean estIntegralementRegle) { this.estIntegralementRegle = estIntegralementRegle; }

    public List<StcReglementDTO> getReglements() { return reglements; }
    public void setReglements(List<StcReglementDTO> reglements) { this.reglements = reglements; }
}
