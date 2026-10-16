package mg.galaxam.sirh.paie.api.dto.stc;

import jakarta.validation.constraints.NotBlank;

/**
 * Requête d'annulation motivée d'une ligne de règlement STC.
 */
public record AnnulationReglementRequest(
        @NotBlank(message = "Le motif de l'annulation est obligatoire pour traçabilité comptable.")
        String motifAnnulation
) {}
