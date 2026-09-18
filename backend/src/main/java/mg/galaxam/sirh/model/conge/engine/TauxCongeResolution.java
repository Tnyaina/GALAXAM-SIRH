package mg.galaxam.sirh.model.conge.engine;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO immuable portant le résultat canonique de la résolution du TCONGE applicable à une date donnée.
 */
public class TauxCongeResolution {

    private final BigDecimal tauxJournalier;
    private final TcongeSourceProvenance sourceProvenance;
    private final LocalDate dateEffet;
    private final LocalDate dateFin;
    private final Integer idPeriodeReference;
    private final Integer idTauxApplicable;

    public TauxCongeResolution(BigDecimal tauxJournalier,
                               TcongeSourceProvenance sourceProvenance,
                               LocalDate dateEffet,
                               LocalDate dateFin,
                               Integer idPeriodeReference,
                               Integer idTauxApplicable) {
        if (tauxJournalier == null || tauxJournalier.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Le taux journalier de congé doit être strictement positif.");
        }
        if (sourceProvenance == null) {
            throw new IllegalArgumentException("La source de provenance du TCONGE est obligatoire.");
        }
        this.tauxJournalier = tauxJournalier;
        this.sourceProvenance = sourceProvenance;
        this.dateEffet = dateEffet;
        this.dateFin = dateFin;
        this.idPeriodeReference = idPeriodeReference;
        this.idTauxApplicable = idTauxApplicable;
    }

    public BigDecimal getTauxJournalier() {
        return tauxJournalier;
    }

    public TcongeSourceProvenance getSourceProvenance() {
        return sourceProvenance;
    }

    public LocalDate getDateEffet() {
        return dateEffet;
    }

    public LocalDate getDateFin() {
        return dateFin;
    }

    public Integer getIdPeriodeReference() {
        return idPeriodeReference;
    }

    public Integer getIdTauxApplicable() {
        return idTauxApplicable;
    }

    @Override
    public String toString() {
        return "TauxCongeResolution{" +
                "taux=" + tauxJournalier +
                ", source=" + sourceProvenance +
                ", effet=" + dateEffet +
                ", fin=" + dateFin +
                ", refId=" + idPeriodeReference +
                '}';
    }
}
