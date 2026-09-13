# Contre-audit A01–A20 — certification finale

| ID | Problème initial | Statut | Preuve code | Preuve test | Risque résiduel |
|---|---|---|---|---|---|
| A01 | Affectation/contrat non historiques | CORRIGÉ | `PayrollService.preparerInput` résout à la période | tests temporels Paie | aucun connu |
| A02 | Configuration légale ambiguë ou absente | CORRIGÉ | `PaieConfigLegale.findActiveAt` | tests absence/concurrence | aucun connu |
| A03 | Absence de résultat Pointage gelé | CORRIGÉ | snapshots jour/semaine/période append-only | tests snapshot/obsolescence | aucun connu |
| A04 | Deux calculs journaliers Pointage | CORRIGÉ | `DailyAttendanceCalculator`; ancien moteur supprimé | `AttendanceCalculationPipelineTest` | aucun connu |
| A05 | Temps/argent actifs en `double` | CORRIGÉ | temps canonique en minutes ; montants repas/déplacement et agrégats monétaires en `BigDecimal` de JDBC à la persistance ; conversion `double` limitée aux DTO/stockages historiques et exports | `CalendrierOuvrableEngineTest`, tests Pointage et suite complète | aucun connu |
| A06 | Agrégations Pointage concurrentes | CORRIGÉ | `WeeklyAttendanceCalculator` puis `AttendancePeriodCalculator` | tests d’équivalence des niveaux | aucun connu |
| A07 | Coefficients évolutifs codés en dur | CORRIGÉ | `PointageSyntheseService.upsertSemaineCalculee` résout strictement HS30/HS50/nuit/dimanche/férié via `PaieConfigLegale.findActiveAt` et `RefTauxMajoration.resolveApplicable`; aucun taux de secours | tests résolution stricte et anti-hardcode `OvertimePayCalculatorTest` | aucun connu |
| A08 | Validation Paie recalculant | CORRIGÉ | `validerPaiePeriode` valide le bulletin existant | test validation sans recalcul | aucun connu |
| A09 | Campagne non isolée par salarié | CORRIGÉ | savepoints + `PayrollBatchResult` | tests campagne | aucun connu |
| A10 | Workflow dans le moteur pur | CORRIGÉ | `PayrollExecutionEngine` sans persistance/horloge | tests unitaires moteur | aucun connu |
| A11 | Simulation persistante | CORRIGÉ | chemin simulation sans sauvegarde | tests simulation | aucun connu |
| A12 | STC deuxième moteur social/fiscal | CORRIGÉ | STC utilise Social/IRSA/ISI communs | tests STC/Paie | spécifique rupture conservé |
| A13 | Calculs/fallbacks Paie dans React | CORRIGÉ | UI consomme les résultats backend | build Vite | aucun connu |
| A14 | Résultats mutables/non typés | CORRIGÉ | records jour/semaine/période | tests calculateurs | aucun connu |
| A15 | Entrée Pointage→Paie implicite | CORRIGÉ | `PayrollAttendanceInput` immutable | `PayrollAttendanceInputTest` | aucun connu |
| A16 | Jobs perturbant les tests | CORRIGÉ | schedulers désactivés + JUnit séquentiel | deux suites complètes | aucun connu |
| A17 | Arrondis concurrents | CORRIGÉ | HS, cotisations, impôts et montants génériques passent par `MoneyRoundingPolicy`; les arrondis Pointage restants sont affichage/progression et les divisions fiscales restent chez leur propriétaire légal | `MoneyRoundingPolicyTest` couvre x.004/x.005/x.006 et l'ordre arrondir-les-lignes-puis-sommer | aucun connu |
| A18 | Calendrier non déterministe | CORRIGÉ | `CalendrierOuvrableEngine`; absence bloquante | tests calendrier manquant | aucun connu |
| A19 | Provenance Paie insuffisante | CORRIGÉ | bulletin référence snapshot/version; provenance jours/semaines | tests snapshot/obsolescence | aucun connu |
| A20 | Responsabilités Paie concentrées | CORRIGÉ | calculateurs HS/social/IRSA/ISI actifs; moteur orchestre | tests directs + intégration | aucun connu |
