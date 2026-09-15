package mg.galaxam.sirh.api.dto.paie;

/** Métadonnées de traçabilité d'une sortie produite depuis une campagne de paie. */
public record PayrollOutputView(
        String code,
        String status,
        int version,
        String checksum,
        String sourceCampaign) {
}
