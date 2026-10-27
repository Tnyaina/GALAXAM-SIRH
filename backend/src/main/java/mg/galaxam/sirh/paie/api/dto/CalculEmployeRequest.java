package mg.galaxam.sirh.paie.api.dto;

import jakarta.validation.constraints.NotNull;

public class CalculEmployeRequest {
    @NotNull(message = "L'identifiant de l'employé est obligatoire.")
    private Integer idEmploye;

    @NotNull(message = "L'identifiant de la période de paie est obligatoire.")
    private Integer idPeriodePaie;

    private boolean simulation = false;

    public CalculEmployeRequest() {}

    public Integer getIdEmploye() { return idEmploye; }
    public void setIdEmploye(Integer idEmploye) { this.idEmploye = idEmploye; }

    public Integer getIdPeriodePaie() { return idPeriodePaie; }
    public void setIdPeriodePaie(Integer idPeriodePaie) { this.idPeriodePaie = idPeriodePaie; }

    public boolean isSimulation() { return simulation; }
    public void setSimulation(boolean simulation) { this.simulation = simulation; }
}
