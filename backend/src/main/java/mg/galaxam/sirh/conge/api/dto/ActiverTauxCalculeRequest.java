package mg.galaxam.sirh.conge.api.dto;

import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Phase 2A-4C.1 — DTO d'activation explicite d'un taux calculé.
 * Le montant du taux n'est JAMAIS fourni par le frontend : l'opération est backend-driven.
 */
public class ActiverTauxCalculeRequest {

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateEffet;

    public ActiverTauxCalculeRequest() {
    }

    public ActiverTauxCalculeRequest(LocalDate dateEffet) {
        this.dateEffet = dateEffet;
    }

    public LocalDate getDateEffet() {
        return dateEffet;
    }

    public void setDateEffet(LocalDate dateEffet) {
        this.dateEffet = dateEffet;
    }
}
