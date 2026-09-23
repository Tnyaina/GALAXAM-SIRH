package mg.galaxam.sirh.paie.api.dto;

/** Métadonnées de traçabilité d'une sortie produite depuis une campagne de paie. */
public record PayrollOutputView(
        String code,
        String status,
        int version,
        String checksum,
        String sourceCampaign) {
}
