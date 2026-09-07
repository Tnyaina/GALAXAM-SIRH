package mg.galaxam.sirh.api.dto.paie;

import jakarta.validation.constraints.NotNull;

public class ActionPaiePeriodeRequest {
    @NotNull(message = "L'identifiant de la période de paie est obligatoire.")
    private Integer idPeriodePaie;

    private String motif;

    public ActionPaiePeriodeRequest() {}

    public Integer getIdPeriodePaie() { return idPeriodePaie; }
    public void setIdPeriodePaie(Integer idPeriodePaie) { this.idPeriodePaie = idPeriodePaie; }

    public String getMotif() { return motif; }
    public void setMotif(String motif) { this.motif = motif; }
}
