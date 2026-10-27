package mg.galaxam.sirh.paie.api.dto;

import jakarta.validation.constraints.NotNull;

public class CalculPeriodeRequest {
    @NotNull(message = "L'identifiant de la période de paie est obligatoire.")
    private Integer idPeriodePaie;

    private boolean simulation = false;

    public CalculPeriodeRequest() {}

    public Integer getIdPeriodePaie() { return idPeriodePaie; }
    public void setIdPeriodePaie(Integer idPeriodePaie) { this.idPeriodePaie = idPeriodePaie; }

    public boolean isSimulation() { return simulation; }
    public void setSimulation(boolean simulation) { this.simulation = simulation; }
}
