package mg.galaxam.sirh.conge.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CongeExpirationExamenDTO(
        int idEmploye,
        LocalDate dateReference,
        int delaiPrescriptionMois,
        LocalDate dateSeuilPrescription,
        BigDecimal joursAExaminer,
        LocalDate plusAncienDroitConcerne,
        List<DroitCandidat> droitsCandidats,
        String derniereDecision,
        String motifDerniereDecision,
        LocalDate prochaineDateExamen) {
    public record DroitCandidat(LocalDate dateAcquisition, LocalDate dateEcheance, BigDecimal joursDisponibles) {}
}
