package mg.galaxam.sirh.paie.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Confirmation d'un décaissement réel, distincte du calcul du bulletin. */
public record ConfirmationVersementRequest(
        @NotNull LocalDate dateVersement,
        @NotBlank String referenceVersement,
        @NotBlank String modeReglement,
        @NotNull Integer expectedVersion) {}
