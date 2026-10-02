package mg.galaxam.sirh.conge.domain;

import java.time.LocalDate;

public final class DemandeCongeRequests {

    private DemandeCongeRequests() {}

    public record CreerDemandeRequest(
            Integer idEmploye,
            Integer idTypeConge,
            Integer idMotifEvenementFamilial,
            LocalDate dateDebut,
            LocalDate dateFin,
            Boolean estDemiJournee,
            String momentDemiJournee,
            Boolean derogationSoldeNegatif,
            Boolean derogationDelaiPreavis,
            Boolean derogationContinuiteService,
            Boolean extensionPathologiqueMaternite,
            Boolean estRemunere,
            Boolean justificatifFourni,
            String commentaire
    ) {}

    public record ModifierDemandeRequest(
            Integer idTypeConge,
            Integer idMotifEvenementFamilial,
            LocalDate dateDebut,
            LocalDate dateFin,
            Boolean estDemiJournee,
            String momentDemiJournee,
            Boolean derogationSoldeNegatif,
            Boolean derogationDelaiPreavis,
            Boolean derogationContinuiteService,
            Boolean extensionPathologiqueMaternite,
            Boolean justificatifFourni,
            String commentaire
    ) {}

    public record SimulerDemandeRequest(
            Integer idEmploye,
            Integer idTypeConge,
            Integer idMotifEvenementFamilial,
            LocalDate dateDebut,
            LocalDate dateFin,
            Boolean estDemiJournee,
            Boolean derogationSoldeNegatif,
            Boolean derogationDelaiPreavis,
            Boolean extensionPathologiqueMaternite
    ) {}

    public record VerifierConflitsRequest(
            Integer idEmploye,
            Integer idDemandeConge,
            LocalDate dateDebut,
            LocalDate dateFin,
            Boolean estDemiJournee,
            String momentDemiJournee
    ) {}

    public record DecisionDemandeRequest(
            String motifRefus,
            String motif
    ) {
        public String motifEffectif() {
            if (motifRefus != null && !motifRefus.isBlank()) return motifRefus.trim();
            if (motif != null && !motif.isBlank()) return motif.trim();
            return null;
        }
    }
}
