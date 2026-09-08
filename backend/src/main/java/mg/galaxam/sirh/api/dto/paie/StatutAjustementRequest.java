package mg.galaxam.sirh.api.dto.paie;

import jakarta.validation.constraints.NotBlank;

public class StatutAjustementRequest {

    @NotBlank(message = "Le statut est obligatoire.")
    private String statut;

    private String motif;

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public String getMotif() { return motif; }
    public void setMotif(String motif) { this.motif = motif; }
}
