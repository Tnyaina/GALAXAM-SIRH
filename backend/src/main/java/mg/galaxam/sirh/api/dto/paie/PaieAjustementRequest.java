package mg.galaxam.sirh.api.dto.paie;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.sql.Date;

public class PaieAjustementRequest {

    @NotNull(message = "L'identifiant de l'employé est obligatoire.")
    private Integer idEmploye;

    private Integer idPeriodePaie;
    private Integer idTypeMouvement;
    private String typeMouvement; // Code optionnel si idTypeMouvement non renseigné
    private String sens; // 'GAIN' ou 'RETENUE'

    @NotNull(message = "Le montant est obligatoire.")
    private BigDecimal montant;

    private Date dateDemande;
    private String motif;
    private String modeReglement = "VIREMENT";
    // Note P1-01 : le statut initial n'est plus fourni par le client.
    // L'ajustement est toujours créé en EN_ATTENTE ; l'approbation passe par POST /{id}/commands.

    public Integer getIdEmploye() { return idEmploye; }
    public void setIdEmploye(Integer idEmploye) { this.idEmploye = idEmploye; }

    public Integer getIdPeriodePaie() { return idPeriodePaie; }
    public void setIdPeriodePaie(Integer idPeriodePaie) { this.idPeriodePaie = idPeriodePaie; }

    public Integer getIdTypeMouvement() { return idTypeMouvement; }
    public void setIdTypeMouvement(Integer idTypeMouvement) { this.idTypeMouvement = idTypeMouvement; }

    public String getTypeMouvement() { return typeMouvement; }
    public void setTypeMouvement(String typeMouvement) { this.typeMouvement = typeMouvement; }

    public String getSens() { return sens; }
    public void setSens(String sens) { this.sens = sens; }

    public BigDecimal getMontant() { return montant; }
    public void setMontant(BigDecimal montant) { this.montant = montant; }

    public Date getDateDemande() { return dateDemande; }
    public void setDateDemande(Date dateDemande) { this.dateDemande = dateDemande; }

    public String getMotif() { return motif; }
    public void setMotif(String motif) { this.motif = motif; }

    public String getModeReglement() { return modeReglement; }
    public void setModeReglement(String modeReglement) { this.modeReglement = modeReglement; }
}
