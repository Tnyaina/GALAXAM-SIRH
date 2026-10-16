package mg.galaxam.sirh.paie.domain;

import java.util.List;
import java.util.stream.Stream;

/** Business eligibility for contractor invoice workflow actions. */
public final class PrestataireFacturePolicy {
    public record Eligibility(List<String> actions, List<String> missingRequirements) {}

    private PrestataireFacturePolicy() {}

    public static Eligibility evaluate(String status, boolean invoicePdf, boolean contractPdf, boolean providerNif,
            boolean providerActive) {
        List<String> missing = Stream.of(!invoicePdf ? "PDF_FACTURE" : null,
                !contractPdf ? "PDF_CONTRAT" : null, !providerNif ? "NIF" : null,
                !providerActive ? "PRESTATAIRE_INACTIF" : null)
                .filter(java.util.Objects::nonNull).toList();
        if ("BROUILLON".equals(status)) {
            boolean complete = missing.isEmpty();
            return new Eligibility(complete ? List.of("CANCEL", "VALIDATE") : List.of("CANCEL"), missing);
        }
        if ("VALIDEE".equals(status)) return new Eligibility(List.of("MARK_PAID"), List.of());
        return new Eligibility(List.of(), List.of());
    }
}
