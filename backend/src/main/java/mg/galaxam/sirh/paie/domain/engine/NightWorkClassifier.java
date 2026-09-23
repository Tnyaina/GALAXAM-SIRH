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

import mg.galaxam.sirh.pointage.domain.calculation.NightWorkType;
import mg.galaxam.sirh.pointage.domain.calculation.QualifiedTimeBucket;

/**
 * Résolveur optionnel de classification du travail de nuit (HABITUEL vs OCCASIONNEL).
 * Permet d'injecter la classification contractuelle ou d'affectation RH
 * pour résoudre les buckets encore à l'état NIGHT_UNCLASSIFIED.
 */
@FunctionalInterface
public interface NightWorkClassifier {
    NightWorkType classify(QualifiedTimeBucket bucket, Integer employeeId);
}
