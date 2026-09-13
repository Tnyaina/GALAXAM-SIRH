# Cartographie de migration Pointage — baseline du 13 septembre 2026

Cette cartographie décrit l'état observé avant les modifications de la seconde tranche. Elle sert de garde-fou : une ancienne formule n'est supprimée qu'après caractérisation et comparaison avec le nouveau pipeline.

| Règle | Ancien emplacement | Nouveau propriétaire | Ancien chemin actif ? | Action |
|---|---|---|---:|---|
| Durée journalière | `PointageSaisieService`, `PointageAnomalieService` | `DailyAttendanceCalculator` → `DailyAttendanceResult.workedMinutes` | Non (chemin d'écriture/correction) | Équivalence couverte; wrappers historiques supprimés. **CORRIGÉ** |
| Retard | anciennes méthodes de saisie/correction | `DailyAttendanceCalculator` → `lateMinutes` | Non | Cas matin/reprise couverts. **CORRIGÉ** |
| Heures de nuit | ancien moteur Pointage | `DailyAttendanceCalculator` → `nightMinutes` | Non dans les services | Ancien moteur reste uniquement en caractérisation de tests. **PARTIELLEMENT CORRIGÉ** |
| Statut journée | anciennes branches de saisie | `DailyAttendanceCalculator` → `status` | Non par défaut | Mapping congé/férié conservé à l'orchestration. **CORRIGÉ** |
| Anomalies journée | `PointageAnomalieService` | `DailyAttendanceCalculator` → `anomalies` | Non pour durée/retard/nuit | Certaines règles de workflow restent spécifiques. **PARTIELLEMENT CORRIGÉ** |
| Repas | services Pointage et frontend | `DailyAttendanceCalculator` + montant persisté | Non dans synthèse/fiche | Formule unique; configuration tardive de saisie encore à auditer. **PARTIELLEMENT CORRIGÉ** |
| Déplacement | services Pointage et frontend | `DailyAttendanceCalculator` + montant persisté | Non dans synthèse/fiche | Fallback fiche supprimé; moteur legacy conservé pour compatibilité de tests. **PARTIELLEMENT CORRIGÉ** |
| Agrégation hebdomadaire | ancienne synthèse | `WeeklyAttendanceCalculator` → `WeeklyAttendanceResult` | Non | Résultats journaliers persistés, composition typée. **CORRIGÉ** |
| Heures supplémentaires | ancienne synthèse | `WeeklyAttendanceCalculator` | Non dans synthèse | Frontières minute testées; autres consommateurs Paie/exports à auditer. **PARTIELLEMENT CORRIGÉ** |
| Agrégation période | ancienne synthèse | `AttendancePeriodCalculator` → `AttendancePeriodResult` | Non | La synthèse délègue à la période typée. **CORRIGÉ** |
| Entrée Paie | cumul période | `PayrollAttendanceInput` issu du snapshot | Non pour le moteur | Contrat immutable avec provenance. **CORRIGÉ** |
| Montants repas/déplacement | Pointage, Paie et affichage | Pointage (montant gelé) | Non dans Paie/synthèse | Frontière documentée, audit complet des exports restant. **PARTIELLEMENT CORRIGÉ** |

Le snapshot est désormais append-only (`GELE`, `CONSOMME`, `REMPLACE`) avec version, liens jours/semaines et migration SQL additive. Le contrôle d'obsolescence compare snapshot et configuration légale lors de la validation; les anciens bulletins sans référence restent compatibles et donc non contrôlés.

## Tests ajoutés lors de la tranche précédente

- `AttendanceCalculationPipelineTest` : 3 tests couvrant calcul journalier en minutes, composition hebdomadaire et composition période.
- `SingleEmployeePayrollVerificationTest` : vérification d'un calcul Paie individuel sur la pile réelle de services.
- `PayrollExecutionEngineTest` : cas enrichis autour du résultat pur et de l'absence de métadonnées de workflow dans le moteur.
- `PaieAdversarialTest` et `PaieControllerIntegrationTest` : simulation, validation, clôture et intégrité du workflow.
- `PointageCalculateurEngineTest` : caractérisation renforcée du moteur historique pendant la migration.

Ces tests sont conservés. Les tests du moteur historique servent temporairement de caractérisation et ne seront retirés qu'en même temps que le code devenu inaccessible, après équivalence prouvée.
