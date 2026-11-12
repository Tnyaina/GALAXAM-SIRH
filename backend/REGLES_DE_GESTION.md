# Index des règles de gestion modifiables

Chercher ici le **résultat à faire varier**, ouvrir la méthode liée, puis remonter à son appelant dans le README du domaine. Un « aléa » est un changement de comportement à démontrer : noter l'entrée, le résultat avant/après et le test qui le prouve. Les méthodes `backenddriven` qui ne font que présenter des choix ne remplacent pas la règle exécutée lors de l'écriture.

| Domaine | Guide détaillé |
|---|---|
| Admin | [README](src/main/java/mg/galaxam/sirh/admin/README.md) |
| Common | [README](src/main/java/mg/galaxam/sirh/common/README.md) |
| Config | [README](src/main/java/mg/galaxam/sirh/config/README.md) |
| Contrat | [README](src/main/java/mg/galaxam/sirh/contrat/README.md) |
| Congé | [README](src/main/java/mg/galaxam/sirh/conge/README.md) |
| Cron | [README](src/main/java/mg/galaxam/sirh/cron/README.md) |
| Dashboard | [README](src/main/java/mg/galaxam/sirh/dashboard/README.md) |
| Organisation | [README](src/main/java/mg/galaxam/sirh/organisation/README.md) |
| Paie | [README](src/main/java/mg/galaxam/sirh/paie/README.md) |
| Paieconfig | [README](src/main/java/mg/galaxam/sirh/paieconfig/README.md) |
| Pointage | [README](src/main/java/mg/galaxam/sirh/pointage/README.md) |
| Sécurité | [README](src/main/java/mg/galaxam/sirh/securite/README.md) |

## Contrat

| Résultat / règle | Méthode à ouvrir | Entrées et effet observable | Vérification |
|---|---|---|---|
| Admissibilité d'une transition, durée et motif d'un CDD | [`CarriereTransitionValidator.validerTransition`, `validerDureeContratCDD`, `validerMotifCDD`](src/main/java/mg/galaxam/sirh/contrat/domain/CarriereTransitionValidator.java) | Type, dates, motif, poste → transition acceptée ou refusée. | `CarriereTransitionValidatorCddTest` |
| Dates du contrat et essai | [`ContratValidationEngine.validerDatesContrat`](src/main/java/mg/galaxam/sirh/contrat/domain/ContratValidationEngine.java) | Début, fin, durée d'essai → contrat admissible ou erreur. | Test ciblé à ajouter selon le cas. |
| Âge légal, CIN et compte bancaire | [`EmployeValidationEngine.validerEmploye`](src/main/java/mg/galaxam/sirh/contrat/domain/EmployeValidationEngine.java) et ses validateurs spécialisés | Dossier salarié → création ou modification acceptée/refusée. | `EmployeValidationEngineSatelliteTest` |
| Ancienneté | [`AncienneteEngine.evaluerAnciennete`](src/main/java/mg/galaxam/sirh/contrat/domain/AncienneteEngine.java) | Intervalles contractuels et date → années, mois et jours retenus. | Test ciblé à ajouter selon le cas. |
| Contrat, site et salaire applicables à une date | [`EmploymentContextResolver.resolveContext`, `resolveSalaryByDay`](src/main/java/mg/galaxam/sirh/contrat/application/EmploymentContextResolver.java) | Salarié, date, affectations → données consommées par Pointage et Paie. | Tester aussi le domaine consommateur. |
| Effet d'un mouvement validé | [`WorkflowRhService.validerWorkflow`, `autoAppliquerMouvementsEchus`](src/main/java/mg/galaxam/sirh/contrat/application/WorkflowRhService.java) | Statut et date d'effet → application immédiate ou planifiée. | Test d'intégration selon le mouvement. |

## Congé

| Résultat / règle | Méthode à ouvrir | Entrées et effet observable | Vérification |
|---|---|---|---|
| Jours demandés, acquisition, bonification | [`CongeCalculateurEngine.calculerNombreJours`, `calculerAcquisitionMensuelle`, `calculerBonificationAnciennete`](src/main/java/mg/galaxam/sirh/conge/domain/CongeCalculateurEngine.java) | Dates, service, ancienneté → jours calculés ou crédités. | `CongeMaternitePolicyTest` et cas ciblé à ajouter. |
| Jours fériés inclus ou exclus du décompte | [`CongeDecompteCalendaireResolver.calculer`](src/main/java/mg/galaxam/sirh/conge/application/solde/CongeDecompteCalendaireResolver.java) | Dates, site et politique datée → jours imputés. | Test d'intégration avec calendrier. |
| Ordre FIFO d'imputation et expiration | [`PurgeFifoEngine.calculerImputation`, `calculerJoursAExpierFifo`](src/main/java/mg/galaxam/sirh/conge/domain/PurgeFifoEngine.java) | Lots disponibles, dates → lots débités ou expirables. | Test ciblé à ajouter selon le scénario. |
| Valorisation des lots pris | [`CongeLotValorisationEngine.calculer`, `simulerPrise`](src/main/java/mg/galaxam/sirh/conge/domain/CongeLotValorisationEngine.java) | Lots, jours pris, valeurs → montant de congé. | `CongeLotValorisationEngineTest` |
| Rémunération d'un congé | [`CongePayrollPolicy.estRemunere`](src/main/java/mg/galaxam/sirh/conge/application/valorisation/CongePayrollPolicy.java), [`RemunerationCongePolicy.resolve`](src/main/java/mg/galaxam/sirh/conge/application/valorisation/RemunerationCongePolicy.java) | Type, surcharge explicite → payé ou non payé dans Paie. | `CongePayrollRemunerationTest`, `RemunerationCongePolicyTest` |
| Validation et débit d'une demande | [`CongeService.validerDemande`](src/main/java/mg/galaxam/sirh/conge/application/demande/CongeService.java) | Demande, solde, statut → validation et mouvement de droits. | `CongePostgresIntegrationTest` si PostgreSQL disponible. |
| Actions proposées à l'interface | [`CongeActionCapabilities.actions`](src/main/java/mg/galaxam/sirh/conge/application/backenddriven/CongeActionCapabilities.java) | Autorités et verrous déjà évalués → boutons et motifs de refus ; aucune validation de demande. | `CongeActionCapabilitiesTest` |

## Pointage

| Résultat / règle | Méthode à ouvrir | Entrées et effet observable | Vérification |
|---|---|---|---|
| Minutes travaillées, retard, absence et statut calculé | [`AttendanceEngine.calculateDay`](src/main/java/mg/galaxam/sirh/pointage/domain/calculation/AttendanceEngine.java) | Badges, horaire, congé, jour → résultat journalier. | `PointageTimeValidationTest` et cas ciblé à ajouter. |
| Statut retenu pour une journée | [`AttendanceStatusPolicy.resolve`](src/main/java/mg/galaxam/sirh/pointage/application/AttendanceStatusPolicy.java) | Saisie, résultat, date → `PRESENT`, `ANOMALIE_SAISIE`, absence, etc. | `PointageLeaveStatusTest` |
| Qualification nuit, dimanche et férié | [`TemporalAttendanceEngine.AttendanceTemporalPartitioner.partition`, `project`](src/main/java/mg/galaxam/sirh/pointage/domain/calculation/TemporalAttendanceEngine.java) | Intervalles réels et fenêtres horaires → minutes qualifiées. | `AttendanceOvertimeWorkflowTest` et cas ciblé à ajouter. |
| Compensation et tranches HS 30 % / 50 % | [`AttendanceEngine.calculateWeek`, `prorateFirstOvertimeBand`](src/main/java/mg/galaxam/sirh/pointage/domain/calculation/AttendanceEngine.java) | Journées, attendu, seuil → déficit et HS de la semaine. | `AttendanceOvertimeWorkflowTest` |
| Solde de période et ordre de consommation des HS | [`AttendanceEngine.PeriodBalance.calculate`](src/main/java/mg/galaxam/sirh/pointage/domain/calculation/AttendanceEngine.java) | HS, déficit, ajustement → solde, absence et HS payables. | `AttendanceOvertimeWorkflowTest` |
| Heures attendues avec congé | [`PointageSyntheseService.minutesAttendues`](src/main/java/mg/galaxam/sirh/pointage/application/periode/PointageSyntheseService.java) | Jours ouvrés, congés, seuil journalier → minutes dues. | `PointageLeaveStatusTest` |
| Attribution et tarif des indemnités | [`PointageAllowancePolicy.eligibleForAllowance`, `resolve`, `dailyAmount`](src/main/java/mg/galaxam/sirh/pointage/domain/calculation/PointageAllowancePolicy.java) | Présence, congé, décision, périodicité → droit et montant. | `PointageAllowancePolicyTest` |
| Actions de statut proposées au front | [`PointageStatusActions.actions`](src/main/java/mg/galaxam/sirh/pointage/application/backenddriven/PointageStatusActions.java) | Journée affichée → choix possibles ; ne change pas le statut enregistré. | Test ciblé à ajouter si modifié. |

## Paie

Parcours de calcul : `PayrollService.calculerBulletinEmploye` → `PayrollCalculationContextAssembler.assemble` → `PayrollExecutionEngine.calculerBulletin` → méthodes ci-dessous. Pour un montant faux, vérifier d'abord si l'entrée du moteur est correcte.

| Résultat / règle | Méthode à ouvrir | Entrées et effet observable | Vérification |
|---|---|---|---|
| Salaire de base et prorata | [`PayrollExecutionEngine.calculerSalaireBase`](src/main/java/mg/galaxam/sirh/paie/domain/engine/PayrollExecutionEngine.java) | Mode de paie, salaire, présence → ligne salaire de base. | `NonMonthlyPayrollBaseTest`, `PayrollCalculationTargetedTest` |
| Absence et congé | [`PayrollExecutionEngine.calculerRetenueAbsence`, `calculerConges`](src/main/java/mg/galaxam/sirh/paie/domain/engine/PayrollExecutionEngine.java) | Minutes d'absence, jours de congé, taux → déduction et indemnité. | `PayrollCalculationTargetedTest`, `PayrollLeaveJournalValuationTest` |
| HS et majorations | [`PayrollExecutionEngine.calculerMajorationsEtHeuresSup`](src/main/java/mg/galaxam/sirh/paie/domain/engine/PayrollExecutionEngine.java) → [`OvertimePayCalculator.calculate`](src/main/java/mg/galaxam/sirh/paie/domain/engine/OvertimePayCalculator.java) | Minutes qualifiées, taux → lignes HS, nuit, dimanche, férié. | `OvertimePayCalculatorTest` |
| Primes | [`PayrollExecutionEngine.calculerPrimes`](src/main/java/mg/galaxam/sirh/paie/domain/engine/PayrollExecutionEngine.java) | Salaire, présence, paramètres → primes et brut. | `PayrollCalculationTargetedTest` |
| Cotisations | [`PayrollExecutionEngine.calculerCotisationsSociales`](src/main/java/mg/galaxam/sirh/paie/domain/engine/PayrollExecutionEngine.java) → [`SocialContributionCalculator.calculate`](src/main/java/mg/galaxam/sirh/paie/domain/engine/SocialContributionCalculator.java) | Assiettes, plafonds, taux → parts salariales et patronales. | `PayrollCalculationTargetedTest` ; ajouter le cas de taux visé. |
| Net imposable et exonération HS | [`PayrollExecutionEngine.calculerNetImposable`, `calculerExonerationFiscaleHs`](src/main/java/mg/galaxam/sirh/paie/domain/engine/PayrollExecutionEngine.java) | Brut, cotisations, HS → revenu et assiette IRSA. | `PayrollCalculationTargetedTest` |
| IRSA et ISI | [`PayrollExecutionEngine.calculerFiscalite`](src/main/java/mg/galaxam/sirh/paie/domain/engine/PayrollExecutionEngine.java) → [`PayrollTaxCalculations.calculateIrsa`, `calculateIsi`](src/main/java/mg/galaxam/sirh/paie/domain/engine/PayrollTaxCalculations.java) | Assiette, tranches, enfants, taux → impôt retenu. | `PayrollCalculationTargetedTest` |
| Retenues personnelles | [`PayrollExecutionEngine.calculerRetenuesEtAvances`](src/main/java/mg/galaxam/sirh/paie/domain/engine/PayrollExecutionEngine.java) | Acomptes, avances, prêts → retenues et imputations. | `AdvanceRepaymentPolicyTest` et cas ciblé. |
| Net à payer et arrondi de règlement | [`PayrollExecutionEngine.calculerNetAPayer`](src/main/java/mg/galaxam/sirh/paie/domain/engine/PayrollExecutionEngine.java) → [`MoneyRoundingPolicy.netSettlementToNearestTenAriary`](src/main/java/mg/galaxam/sirh/paie/domain/engine/MoneyRoundingPolicy.java) | Net avant trésorerie et retenues → `netAPayer`, `AJUSTEMENT_ARRONDI`. Le STC appelle le même arrondi. | `MoneyRoundingPolicyTest`, `PayrollCalculationTargetedTest` |
| Rubrique manuelle | [`ManualPayrollRules.calculateAmount`](src/main/java/mg/galaxam/sirh/paie/domain/engine/ManualPayrollRules.java) | Méthode, assiette, taux, arrondi → ligne de rubrique ; distincte de l'arrondi final. | `MoneyRoundingPolicyTest` et cas de rubrique. |

## Paieconfig

| Résultat / règle | Méthode à ouvrir | Entrées et effet observable | Vérification |
|---|---|---|---|
| Millésime légal applicable | [`PaieConfigLegale.findActiveAt`](src/main/java/mg/galaxam/sirh/paieconfig/persistence/PaieConfigLegale.java) | Date métier → SME, plafonds et paramètres de la version retenue. | `PayrollLegalContextResolverTest` côté Paie. |
| Version et état d'un paramètre | [`ParametreGlobal.resolveAtDate`](src/main/java/mg/galaxam/sirh/paieconfig/persistence/ParametreGlobal.java) | Code, date → actif, désactivé ou absent ; peut bloquer un calcul. | Test du consommateur concerné. |
| Barème IRSA | [`BaremeIrsa.revaloriser`, `validerContinuite`](src/main/java/mg/galaxam/sirh/paieconfig/persistence/BaremeIrsa.java) | Tranches et date d'effet → barème utilisé ensuite par Paie. | `PayrollCalculationTargetedTest` avec millésime ciblé. |
| Cotisations selon entreprise/site | [`SocialContributionContextResolver.companyRates`, `healthRate`](src/main/java/mg/galaxam/sirh/paieconfig/application/SocialContributionContextResolver.java) | Date et site → taux CNAPS, FMFP et santé fournis au calcul. | `PayrollLegalContextResolverTest` et cas ciblé. |
| Taux de majoration applicable | [`RefTauxMajoration.resolveApplicable`](src/main/java/mg/galaxam/sirh/paieconfig/persistence/RefTauxMajoration.java) | Type, millésime, date → taux appliqué aux HS ou heures spéciales. | `OvertimePayCalculatorTest` avec taux datés. |

## Organisation

| Résultat / règle | Méthode à ouvrir | Entrées et effet observable | Vérification |
|---|---|---|---|
| Désactivation d'une filiale | [`OrganisationService.desactiverFiliale`](src/main/java/mg/galaxam/sirh/organisation/application/OrganisationService.java), `validerDesactivationFiliale` | Sites actifs, contrats en cours → désactivation acceptée ou refusée. | Test ciblé à ajouter. |
| Plancher d'arrivée d'un site | [`OrganisationService.configurerPlancherSite`](src/main/java/mg/galaxam/sirh/organisation/application/OrganisationService.java) | Site et heure → entrée prise en compte par Pointage. | Vérifier dans `PointageTimeValidationTest`. |
| Disponibilité d'une fonction, d'un site ou d'un type | [`OrganisationService.activerSite`, `desactiverSite`, `activerFonction`, `desactiverFonction`](src/main/java/mg/galaxam/sirh/organisation/application/OrganisationService.java) | État du référentiel → choix proposés et contexte des nouveaux dossiers. | Test du parcours consommateur. |

## Sécurité

| Résultat / règle | Méthode à ouvrir | Entrées et effet observable | Vérification |
|---|---|---|---|
| Connexion et limitation des essais | [`AuthController.login`](src/main/java/mg/galaxam/sirh/securite/api/AuthController.java), [`LoginAttemptRateLimiter`](src/main/java/mg/galaxam/sirh/securite/application/LoginAttemptRateLimiter.java) | Identifiants et tentatives → session créée ou refusée. | Tests du package `securite`. |
| Expiration pour inactivité | [`IdleSessionService.touch`, `rotate`](src/main/java/mg/galaxam/sirh/securite/application/IdleSessionService.java) | Dernière activité, délai, jeton → maintien ou expiration de session. | Tests du package `securite`. |
| Accès aux routes | [`SecurityConfig.securityFilterChain`, `roleHierarchy`](src/main/java/mg/galaxam/sirh/config/SecurityConfig.java), [`CustomUserDetailsService.loadUserByUsername`](src/main/java/mg/galaxam/sirh/securite/auth/CustomUserDetailsService.java) | Rôle, permissions, route → 401, 403 ou accès. | Test d'autorisation de la route visée. |

## Admin

| Résultat / règle | Méthode à ouvrir | Entrées et effet observable | Vérification |
|---|---|---|---|
| Permissions d'un rôle | [`RoleController.updateRolePermissions`](src/main/java/mg/galaxam/sirh/admin/api/RoleController.java) | Rôle et permissions → accès effectif aux modules. | Test d'autorisation de la route visée. |
| Menus visibles | [`RoleController.updateRoleMenus`](src/main/java/mg/galaxam/sirh/admin/api/RoleController.java), [`MenuController.buildMenuTree`](src/main/java/mg/galaxam/sirh/admin/api/MenuController.java) | Associations et parents → arborescence affichée ; n'accorde pas une permission HTTP. | Test API frontend ou cas de menu ciblé. |

## Cron

| Résultat / règle | Méthode à ouvrir | Entrées et effet observable | Vérification |
|---|---|---|---|
| Exécution unique et reprise d'une tâche | [`SchedulerExecutionRegistry.runDaily`, `runMonthly`, `tryStart`, `renewLease`](src/main/java/mg/galaxam/sirh/cron/execution/SchedulerExecutionRegistry.java) | Code, date, bail → tâche exécutée une fois ou rejouée après échec. | Test d'intégration multi-exécution à ajouter. |
| Rattrapage après arrêt | [`RattrapageAutomatiqueService.verifierEtExecuterRattrapage`](src/main/java/mg/galaxam/sirh/cron/execution/RattrapageAutomatiqueService.java) | Dernière période traitée → périodes manquées relancées. | Test de reprise à ajouter. |
| Bonification ou mouvement RH planifié | [`AcquisitionCongeScheduler.bonifierCongesAnciennete`](src/main/java/mg/galaxam/sirh/cron/jobs/AcquisitionCongeScheduler.java), [`MouvementRhScheduler.effectuerBasculementMouvementsPlanifies`](src/main/java/mg/galaxam/sirh/cron/jobs/MouvementRhScheduler.java) | Horaire → appel des règles de Congé ou Contrat ; le calcul reste dans ces domaines. | Tester la tâche et le service appelé. |

## Dashboard

| Résultat / règle | Méthode à ouvrir | Entrées et effet observable | Vérification |
|---|---|---|---|
| Chiffres et actions de l'accueil | [`DashboardService.getDashboardSummary`, `buildKpis`, `buildActionsATraiter`](src/main/java/mg/galaxam/sirh/dashboard/application/DashboardService.java) | Données des domaines → cartes et actions affichées ; lecture seule. | Test du package `dashboard`. |
| Visibilité et lecture des notifications | [`NotificationController.visibilityCondition`, `bindVisibility`, `marquerUne`](src/main/java/mg/galaxam/sirh/dashboard/api/NotificationController.java) | Utilisateur, rôle, état → notifications visibles et marquées lues. | Test du package `dashboard`. |

## Common et Config

Ces deux domaines contiennent des règles **transversales**, pas les formules de Pointage ou Paie.

| Domaine | Résultat / règle | Méthode à ouvrir | Effet observable | Vérification |
|---|---|---|---|---|
| Common | Atomicité JDBC | [`TransactionRunner.required`](src/main/java/mg/galaxam/sirh/common/transaction/TransactionRunner.java) | Toutes les écritures réussissent ou sont annulées. | Test de rollback du parcours concerné. |
| Common | Verrou des sauvegardes | [`BackupConsistencyGate.withWriteAccess`](src/main/java/mg/galaxam/sirh/common/backup/BackupConsistencyGate.java) | Écriture serveur attend ou passe selon le verrou. | Test du package `common`. |
| Config | Accès HTTP global | [`SecurityConfig.securityFilterChain`, `corsConfigurationSource`](src/main/java/mg/galaxam/sirh/config/SecurityConfig.java) | Accès ou refus avant le contrôleur, CORS navigateur. | Test de route ciblé. |
| Config | Traduction d'erreur HTTP | [`GlobalExceptionHandler.handleSirhBusiness`, `handleSqlException`](src/main/java/mg/galaxam/sirh/config/GlobalExceptionHandler.java) | Code et corps JSON de l'erreur. | Test de réponse ciblé. |

## Vérifier un aléa

1. Choisir une seule règle et un cas d'entrée qui distingue clairement l'ancien et le nouveau comportement.
2. Ajouter ou adapter un test de calcul pur pour la méthode concernée ; si elle lit ou écrit SQL, compléter par un test d'intégration avec PostgreSQL.
3. Suivre le résultat jusqu'au service ou à la route qui l'expose ; vérifier aussi les consommateurs (Paie après Pointage ou Congé, par exemple).
4. Lancer `mvn -f backend/pom.xml clean test`, `npm --prefix frontend run test:api` et `npm --prefix frontend run build` si le contrat exposé au front change. Les tests PostgreSQL ignorés ne prouvent pas le comportement en base.
