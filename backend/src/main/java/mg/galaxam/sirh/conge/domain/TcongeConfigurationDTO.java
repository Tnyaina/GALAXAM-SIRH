package mg.galaxam.sirh.conge.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import mg.galaxam.sirh.conge.application.CongeTauxApplicableService.PeriodeReferenceData;
import mg.galaxam.sirh.conge.application.CongeTauxApplicableService.TcongeApplicableRateStatus;
import mg.galaxam.sirh.conge.application.CongeTauxApplicableService.TcongeReferenceStatus;
import mg.galaxam.sirh.conge.application.CongeTauxApplicableService.TcongeSourceProvenance;

/**
 * Phase 2A-4C.1 — DTO d'évaluation multidimensionnelle de la situation TCONGE d'un collaborateur.
 * Totalement dérivé à l'exécution, non persisté en BDD pour éviter toute duplication d'état.
 */
public class TcongeConfigurationDTO {

    private int idEmploye;
    private LocalDate dateReference;
    private TcongeReferenceStatus referenceStatus;
    private TcongeApplicableRateStatus applicableRateStatus;
    private BigDecimal tauxApplicable;
    private TcongeSourceProvenance sourceProvenance;
    private Integer idTauxApplicable;
    private PeriodeReferenceData referenceActive;
    private PeriodeReferenceData referenceClosedAwaitingActivation;
    private List<String> warnings = new ArrayList<>();

    public TcongeConfigurationDTO() {
    }

    public TcongeConfigurationDTO(int idEmploye,
                                  LocalDate dateReference,
                                  TcongeReferenceStatus referenceStatus,
                                  TcongeApplicableRateStatus applicableRateStatus,
                                  BigDecimal tauxApplicable,
                                  TcongeSourceProvenance sourceProvenance,
                                  Integer idTauxApplicable,
                                  PeriodeReferenceData referenceActive,
                                  PeriodeReferenceData referenceClosedAwaitingActivation,
                                  List<String> warnings) {
        this.idEmploye = idEmploye;
        this.dateReference = dateReference;
        this.referenceStatus = referenceStatus;
        this.applicableRateStatus = applicableRateStatus;
        this.tauxApplicable = tauxApplicable;
        this.sourceProvenance = sourceProvenance;
        this.idTauxApplicable = idTauxApplicable;
        this.referenceActive = referenceActive;
        this.referenceClosedAwaitingActivation = referenceClosedAwaitingActivation;
        if (warnings != null) {
            this.warnings = warnings;
        }
    }

    public int getIdEmploye() {
        return idEmploye;
    }

    public void setIdEmploye(int idEmploye) {
        this.idEmploye = idEmploye;
    }

    public LocalDate getDateReference() {
        return dateReference;
    }

    public void setDateReference(LocalDate dateReference) {
        this.dateReference = dateReference;
    }

    public TcongeReferenceStatus getReferenceStatus() {
        return referenceStatus;
    }

    public void setReferenceStatus(TcongeReferenceStatus referenceStatus) {
        this.referenceStatus = referenceStatus;
    }

    public TcongeApplicableRateStatus getApplicableRateStatus() {
        return applicableRateStatus;
    }

    public void setApplicableRateStatus(TcongeApplicableRateStatus applicableRateStatus) {
        this.applicableRateStatus = applicableRateStatus;
    }

    public BigDecimal getTauxApplicable() {
        return tauxApplicable;
    }

    public void setTauxApplicable(BigDecimal tauxApplicable) {
        this.tauxApplicable = tauxApplicable;
    }

    public TcongeSourceProvenance getSourceProvenance() {
        return sourceProvenance;
    }

    public void setSourceProvenance(TcongeSourceProvenance sourceProvenance) {
        this.sourceProvenance = sourceProvenance;
    }

    public Integer getIdTauxApplicable() {
        return idTauxApplicable;
    }

    public void setIdTauxApplicable(Integer idTauxApplicable) {
        this.idTauxApplicable = idTauxApplicable;
    }

    public PeriodeReferenceData getReferenceActive() {
        return referenceActive;
    }

    public void setReferenceActive(PeriodeReferenceData referenceActive) {
        this.referenceActive = referenceActive;
    }

    public PeriodeReferenceData getReferenceClosedAwaitingActivation() {
        return referenceClosedAwaitingActivation;
    }

    public void setReferenceClosedAwaitingActivation(PeriodeReferenceData referenceClosedAwaitingActivation) {
        this.referenceClosedAwaitingActivation = referenceClosedAwaitingActivation;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings != null ? warnings : new ArrayList<>();
    }

    public void addWarning(String warning) {
        if (this.warnings == null) {
            this.warnings = new ArrayList<>();
        }
        this.warnings.add(warning);
    }
}
