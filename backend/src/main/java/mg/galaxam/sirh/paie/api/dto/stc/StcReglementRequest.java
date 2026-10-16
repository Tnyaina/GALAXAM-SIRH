package mg.galaxam.sirh.paie.api.dto.stc;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.sql.Date;

/**
 * Requête d'enregistrement d'un versement de règlement de STC.
 * Supporte le règlement total en une fois, les règlements échelonnés/partiels,
 * et l'attestation explicite des STC à solde nul.
 */
public record StcReglementRequest(
        @NotNull Date dateReglement,
        @NotBlank String referenceReglement,
        BigDecimal montantRegle,
        String modeReglement,
        String commentaire
) {
    /**
     * Constructeur rétro-compatible pour les appels n'indiquant que la date et la référence.
     * Dans ce cas, le montant réglé par défaut sera le solde restant à payer.
     */
    public StcReglementRequest(Date dateReglement, String referenceReglement) {
        this(dateReglement, referenceReglement, null, "VIREMENT", null);
    }

    public StcReglementRequest(Date dateReglement, String referenceReglement, BigDecimal montantRegle) {
        this(dateReglement, referenceReglement, montantRegle, "VIREMENT", null);
    }
}
