package mg.galaxam.sirh.conge.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class CongeSoldeRequests {

    private CongeSoldeRequests() {}

    public record CorrigerCutoverRequest(
            BigDecimal nouveauReliquatJours,
            BigDecimal nouveauReliquatValeur,
            String motif
    ) {}

    public record ReporterExamenRequest(
            LocalDate prochaineDate,
            String motif
    ) {}

    public record ConfirmerExpirationRequest(
            Boolean planificationVerifiee,
            String motif
    ) {}

    public record AjustementSoldeRequest(
            Integer idEmploye,
            BigDecimal nbJours,
            String commentaire,
            BigDecimal valorisationSpecifique,
            LocalDate dateMouvement
    ) {}

}
