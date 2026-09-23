package mg.galaxam.sirh.paie.domain.engine;

import mg.galaxam.sirh.paie.persistence.*;
import mg.galaxam.sirh.paie.domain.engine.*;
import mg.galaxam.sirh.paie.domain.dto.*;
import mg.galaxam.sirh.paie.domain.dto.export.*;
import mg.galaxam.sirh.paie.application.*;
import mg.galaxam.sirh.paie.application.workflow.*;
import mg.galaxam.sirh.paie.application.export.*;
import mg.galaxam.sirh.paie.api.dto.*;
import mg.galaxam.sirh.paie.api.dto.stc.*;

import java.time.LocalDate;

/**
 * Résolveur fonctionnel des taux de majoration applicables à la date civile unitaire d'un bucket (Phase 1C-B.1).
 * Permet de supporter les changements de taux réglementaires au milieu d'une période de paie.
 */
@FunctionalInterface
public interface TemporalRateResolver {
    OvertimePayCalculator.Rates resolveRates(LocalDate date);
}
