package mg.galaxam.sirh.paie.api.dto.stc;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.sql.Date;

public record StcReglementRequest(
        @NotNull Date dateReglement,
        @NotBlank String referenceReglement) {}
