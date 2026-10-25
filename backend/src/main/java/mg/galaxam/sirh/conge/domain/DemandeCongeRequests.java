package mg.galaxam.sirh.conge.domain;

import java.time.LocalDate;
import mg.galaxam.sirh.common.json.FlexibleLocalDateDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public final class DemandeCongeRequests {

    private DemandeCongeRequests() {}

    public record CreerDemandeRequest(
            Integer idEmploye,
            Integer idTypeConge,
            Integer idMotifEvenementFamilial,
            @JsonDeserialize(using = FlexibleLocalDateDeserializer.class)
            LocalDate dateDebut,
            @JsonDeserialize(using = FlexibleLocalDateDeserializer.class)
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
            @JsonDeserialize(using = FlexibleLocalDateDeserializer.class)
            LocalDate dateDebut,
            @JsonDeserialize(using = FlexibleLocalDateDeserializer.class)
            LocalDate dateFin,
            Boolean estDemiJournee,
            String momentDemiJournee,
            Boolean derogationSoldeNegatif,
            Boolean derogationDelaiPreavis,
            Boolean derogationContinuiteService,
            Boolean extensionPathologiqueMaternite,
            Boolean justificatifFourni,
            String commentaire,
            Boolean estRemunere,
            Boolean utiliserRemunerationParDefaut
    ) {
        public ModifierDemandeRequest(Integer idTypeConge, Integer idMotifEvenementFamilial,
                LocalDate dateDebut, LocalDate dateFin, Boolean estDemiJournee,
                String momentDemiJournee, Boolean derogationSoldeNegatif,
                Boolean derogationDelaiPreavis, Boolean derogationContinuiteService,
                Boolean extensionPathologiqueMaternite, Boolean justificatifFourni,
                String commentaire) {
            this(idTypeConge, idMotifEvenementFamilial, dateDebut, dateFin, estDemiJournee,
                    momentDemiJournee, derogationSoldeNegatif, derogationDelaiPreavis,
                    derogationContinuiteService, extensionPathologiqueMaternite,
                    justificatifFourni, commentaire, null, null);
        }
    }

    public record SimulerDemandeRequest(
            Integer idEmploye,
            Integer idTypeConge,
            Integer idMotifEvenementFamilial,
            @JsonDeserialize(using = FlexibleLocalDateDeserializer.class)
            LocalDate dateDebut,
            @JsonDeserialize(using = FlexibleLocalDateDeserializer.class)
            LocalDate dateFin,
            Boolean estDemiJournee,
            Boolean derogationSoldeNegatif,
            Boolean derogationDelaiPreavis,
            Boolean extensionPathologiqueMaternite,
            Integer idDemandeConge
    ) {}

    public record VerifierConflitsRequest(
            Integer idEmploye,
            Integer idTypeConge,
            Integer idDemandeConge,
            @JsonDeserialize(using = FlexibleLocalDateDeserializer.class)
            LocalDate dateDebut,
            @JsonDeserialize(using = FlexibleLocalDateDeserializer.class)
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
