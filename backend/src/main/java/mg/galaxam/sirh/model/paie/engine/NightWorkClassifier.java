package mg.galaxam.sirh.model.paie.engine;

import mg.galaxam.sirh.model.pointage.calculation.NightWorkType;
import mg.galaxam.sirh.model.pointage.calculation.QualifiedTimeBucket;

/**
 * Résolveur optionnel de classification du travail de nuit (HABITUEL vs OCCASIONNEL).
 * Permet d'injecter la classification contractuelle ou d'affectation RH
 * pour résoudre les buckets encore à l'état NIGHT_UNCLASSIFIED.
 */
@FunctionalInterface
public interface NightWorkClassifier {
    NightWorkType classify(QualifiedTimeBucket bucket, Integer employeeId);
}
