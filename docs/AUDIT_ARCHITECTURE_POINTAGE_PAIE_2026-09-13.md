# Audit de l'architecture métier Pointage + Paie

Date de l'audit : 13 septembre 2026  
Périmètre : état courant du dépôt, y compris les modifications locales non commités  
Nature : audit statique et dynamique, sans refactoring du code métier

## 1. Résumé exécutif

L'architecture a déjà engagé une bonne direction côté Paie : `PayrollExecutionEngine` calcule un bulletin individuel à partir d'un `PayrollCalculationInput`, et `PayrollService.calculerPaiePeriode` réutilise effectivement ce point d'entrée pour chaque salarié. Côté Pointage, `PointageCalculateurEngine` isole quelques règles (retard, nuit, repas, déplacement, tronçon hebdomadaire), et la Paie consomme le cumul gelé `pointage_cumul_periode` plutôt que les badges bruts.

Ces acquis ne suffisent pas à garantir une paie irréprochable. Le principal défaut structurel est le Pointage : il ne possède pas de résultat journalier autonome. `PointageSaisieService.enregistrerPointagesBatch` charge les configurations, interprète les badges, calcule les durées, retards, heures de nuit, statuts et indemnités, puis persiste le tout dans la même opération. `PointageSyntheseService.calculerSyntheseEmploye` relit ensuite les colonnes calculées et recompose semaine et période dans une méthode qui mélange SQL, règles, DTO, configuration et agrégation.

La Paie individuelle existe, mais le moteur reste insuffisamment pur et trop agrégé : il construit directement des entités persistables, horodate avec l'heure système, assigne un statut de workflow, et concentre presque toutes les rubriques dans `calculerSalaireBrut`. Le contexte est riche mais mutable, comporte de nombreux défauts silencieux, et le resolver sélectionne l'affectation marquée `ACTIF` au moment du recalcul au lieu de l'affectation applicable à la période.

Conclusion : le traitement collectif de Paie est déjà un orchestrateur, mais son moteur unitaire n'est pas encore historiquement fiable. Le traitement collectif Pointage appelle un calcul individuel, mais ce calcul individuel n'est pas une composition explicite `jour -> semaine -> période`. La priorité est de figer des tests de caractérisation, corriger la temporalité contractuelle et créer un vrai résultat journalier en minutes avant toute extension fonctionnelle de la Paie.

## 2. Architecture Pointage actuelle

Flux observé :

```text
Saisie/API batch
  -> PointageSaisieService.enregistrerPointagesBatch
       -> charge horaires, paramètres et indemnités
       -> calcule statut, durées, retard, nuit, repas, déplacement
       -> écrit pointage_jour + pointage_indemnite_jour
  -> PointageSyntheseService.calculerSyntheseEmploye
       -> relit les résultats journaliers en SQL
       -> regroupe en Map par semaine ISO
       -> PointageCalculateurEngine.calculerTroncon
       -> construit PointageSyntheseMensuelleDTO
  -> gelerPeriode / pointage_cumul_periode
  -> PayrollService.preparerInput
```

`pointage_jour` fusionne actuellement les quatre heures saisies, le statut, les résultats calculés, le motif de correction et les auteurs. Il n'existe ni objet `DailyAttendanceResult`, ni trace explicite séparant badge brut, correction retenue et résultat.

## 3. Architecture Paie actuelle

Flux observé :

```text
PayrollService.preparerInput (JDBC, résolution du contexte)
  -> PayrollCalculationInput mutable
  -> PayrollExecutionEngine.calculer
       -> salaire brut et gains
       -> cotisations
       -> net imposable
       -> IRSA ou ISI
       -> indemnités nettes
       -> avances, prêts et retenues
       -> net et lignes de bulletin
  -> PayrollService.sauvegarderBulletinEtLignes
  -> PayrollService.imputerAvancesEtPrets
```

La campagne (`calculerPaiePeriode`) liste les employés puis appelle `calculerBulletinEmploye` : aucune formule spécifique n'est ajoutée au niveau collectif. C'est le meilleur point de l'architecture actuelle.

## 4. Granularités actuellement utilisées

| Domaine | Granularité actuelle | Évaluation |
|---|---|---|
| Pointage | lot de saisies -> ligne persistée -> tronçon hebdo -> DTO période | journée non autonome ; composition implicite |
| Paie | règle partielle -> bulletin employé -> campagne | bonne hiérarchie générale, règles encore trop regroupées |
| Workflow | période + bulletins | mélangé par endroits au calcul |
| Exports | bulletin/lignes persistés | globalement consommateur, pas moteur principal |

## 5. Méthodes trop grosses

| Fichier | Classe | Méthode | Niveau | Responsabilités mélangées |
|---|---|---|---|---|
| `PointageSaisieService.java` | PointageSaisieService | `enregistrerPointagesBatch` (1194+) | ORCHESTRATION/RULE/PERSISTENCE | configuration, validation, calcul jour, concurrence, SQL, indemnités |
| `PointageSyntheseService.java` | PointageSyntheseService | `calculerSyntheseEmploye` (183+) | UNIT/COMPOSITION/PERSISTENCE | chargement, calendrier, agrégats, semaine, indemnités, DTO |
| `PointageSyntheseService.java` | PointageSyntheseService | `getSynthesePeriode` (600+) | ORCHESTRATION/QUERY | sélection salariés, KPI et reconstruction parallèle |
| `PayrollService.java` | PayrollService | `preparerInput` (379+) | CONTEXT RESOLUTION | période, contrat, enfants, pointage, variables, prêts, config |
| `PayrollExecutionEngine.java` | PayrollExecutionEngine | `calculerSalaireBrut` (170+) | COMPOSITION/RULE | absence, congé, HS, nuit, férié, primes, ancienneté, variables |
| `PayrollExecutionEngine.java` | PayrollExecutionEngine | `calculerRetenuesEtAvances` (589+) | RULE/COMPOSITION | acomptes, avance 1/10, prêt 1/3, autres retenues |
| `StcService.java` | StcService | calcul STC | RULE/ORCHESTRATION/PERSISTENCE | second pipeline de paie partiellement parallèle |

## 6. Calculs dupliqués

- La durée est calculée dans `PointageSaisieService.calcHours`, tandis que la synthèse reconvertit `total_heures` en minutes puis en heures.
- Le calcul retard/nuit est correctement délégué au moteur depuis la saisie, mais `PointageAnomalieService` rappelle les wrappers statiques de `PointageSaisieService` : la règle reste couplée au service d'écriture.
- Les indemnités sont calculées à la saisie (`montant_calcule`), puis recalculées dans `PointageSyntheseService` au lieu d'agréger systématiquement le montant journalier figé.
- Le prorata transport existe côté Pointage et un fallback de prorata existe côté Paie.
- Les constantes 173,33 h, 350 000 Ar, 3 000 Ar et les taux de majoration existent dans le backend, les seeds SQL et les valeurs par défaut du frontend.
- STC applique un pipeline fiscal et un arrondi final parallèle au moteur mensuel.
- `BulletinPaieDrawer` divise arbitrairement les cotisations salariales par deux si les détails CNaPS/OSTIE manquent.

## 7. Règles métier mal placées

- Les formules journalières vivent dans une méthode de persistance batch.
- `CalendrierOuvrableEngine` fait des requêtes JDBC et contient des calculs monétaires en `double` : ce n'est pas un moteur pur.
- `PayrollExecutionEngine` attribue `CALCULE`/`VALIDE` et une date système : le workflow n'appartient pas au calcul.
- `BulletinPaie.arrondirDizaineAriary` place une politique monétaire dans une entité de persistance.
- Les contrôleurs/configurations frontend embarquent des valeurs fiscales par défaut pouvant masquer un backend incomplet.
- La vue d'affichage Paie invente des ventilations sociales ; elle doit afficher « détail indisponible » ou les lignes backend.

## 8. Frontière Pointage -> Paie

La frontière actuelle est `pointage_cumul_periode`, consommée dans `PayrollService.preparerInput`. C'est une bonne direction, mais le contrat reste implicite et incomplet : aucune version du cumul, aucune référence aux semaines/jours sources, aucun état de validation inclus dans un objet typé, et les montants repas/déplacement y sont déjà valorisés.

Contrat cible :

```java
public record PayrollAttendanceInput(
    Integer employeeId,
    Integer periodId,
    UUID attendanceSnapshotId,
    long normalMinutes,
    long overtime30Minutes,
    long overtime50Minutes,
    long nightMinutes,
    long sundayMinutes,
    long publicHolidayMinutes,
    long unpaidAbsenceMinutes,
    int mealDays,
    BigDecimal mealAmount,
    BigDecimal travelUnits,
    BigDecimal travelAmount,
    int lateMinutes,
    List<AttendanceSourceRef> sources
) {}
```

Les heures doivent être transmises comme minutes entières ou `Duration`, jamais comme `double`. La quantité d'HS appartient au Pointage ; sa valorisation appartient à la Paie. Pour repas/déplacement, GALAXAM valorise actuellement au jour selon le site : le contrat peut transporter à la fois quantité et montant figé, avec la règle/configuration source.

## 9. Architecture cible Pointage

```text
pointage/
  domain/
    RawPunches, AttendanceCorrection, DailyAttendanceContext
    DailyAttendanceResult, WeeklyAttendanceResult, AttendancePeriodResult
  calculation/
    DailyAttendanceCalculator
    WeeklyAttendanceCalculator
    AttendancePeriodCalculator
  application/
    AttendanceContextResolver
    AttendancePeriodService
    AttendanceCampaignService
  workflow/
    AttendanceWorkflowService
  persistence/
    AttendanceReadRepository, AttendanceSnapshotRepository
```

Trois structures distinctes sont nécessaires : badge brut immuable ; correction versionnée avec motif/auteur/validation ; résultat calculé versionné.

## 10. Architecture cible Paie

```text
paie/
  domain/
    PayrollCalculationContext, PayrollResult, PayrollLineResult
    MoneyRoundingPolicy, PayrollInvariants
  calculation/
    PayrollCalculator
    SalaryCalculator, AttendanceImpactCalculator, AllowanceCalculator
    SocialContributionCalculator, IrsaCalculator, IsiCalculator
    TreasuryDeductionCalculator, NetPayCalculator
  application/
    PayrollContextResolver, PayrollExecutionService, PayrollCampaignService
  workflow/
    PayrollWorkflowService
  persistence/
    PayrollSnapshotRepository, PayrollResultRepository
```

Il n'est pas nécessaire de créer une interface par classe. Extraire une classe seulement lorsqu'une règle a ses propres entrées, frontières et tests.

## 11. Graphe de calcul Pointage

```text
Badges bruts + corrections + horaire/config applicables à la date
                         |
                         v
              DailyAttendanceCalculator
                         |
                         v
              DailyAttendanceResult[]
                         |
                         v
             WeeklyAttendanceCalculator
                         |
                         v
              WeeklyAttendanceResult[]
                         |
                         v
             AttendancePeriodCalculator
                         |
                         v
              AttendancePeriodResult
                         |
             snapshot validé/versionné
                         |
                         v
              PayrollAttendanceInput
```

## 12. Graphe de calcul Paie

```text
Contrat applicable ----+
Pointage snapshot ------+
Variables validées -----+--> PayrollCalculationContext
Config légale datée ----+              |
Prêts/avances ----------+              v
                                  PayrollCalculator
                   +----------+---------+---------+
                   v          v                   v
              gains/brut  cotisations         fiscalité
                   +----------+---------+---------+
                                      v
                              retenues + net
                                      v
                                PayrollResult
                         +------------+-------------+
                         v            v             v
                    persistance      UI          exports
```

## 13. Proposition de classes

| Classe | Responsabilité unique |
|---|---|
| `DailyAttendanceCalculator` | transformer contexte journalier en résultat explicable |
| `WeeklyAttendanceCalculator` | qualifier compensation et HS à partir des jours |
| `AttendancePeriodCalculator` | agréger semaines et catégories sans relire les badges |
| `AttendanceSnapshot` | figer résultat et références sources |
| `PayrollContextResolver` | résoudre toutes les données à la date de période |
| `PayrollCalculator` | ordonner les règles pour un employé |
| `AttendanceImpactCalculator` | taux horaire, absences, HS, nuit, dimanche, férié |
| `SocialContributionCalculator` | CNaPS/santé/FMFP avec résultats détaillés |
| `IrsaCalculator` | calcul par tranches et trace par tranche |
| `TreasuryDeductionCalculator` | acomptes, avances, prêts, quotité |
| `PayrollInvariants` | valider équations et valeurs impossibles |
| `PayrollSnapshot` | figer contexte, versions et résultat validé |

## 14. Proposition de signatures

```java
DailyAttendanceResult calculateDay(DailyAttendanceContext context);
WeeklyAttendanceResult calculateWeek(WeeklyAttendanceContext context,
                                     List<DailyAttendanceResult> days);
AttendancePeriodResult calculateEmployeePeriod(AttendancePeriodContext context,
                                               List<WeeklyAttendanceResult> weeks);
PayrollCalculationContext resolve(EmployeeId employee, PayrollPeriod period);
PayrollResult calculateEmployeePayroll(PayrollCalculationContext context);
PayrollCampaignResult calculateCampaign(PayrollCampaignContext context);
```

Les calculateurs ne reçoivent ni `Connection`, ni repository, ni horloge implicite, et ne sauvegardent rien.

## 15. Décomposition des méthodes existantes

```text
enregistrerPointagesBatch
  -> validateBatchCommand
  -> loadDailyContexts
  -> calculateDay (pour chaque commande)
  -> persistRawPunches
  -> persistCorrections
  -> persistDailyResults
```

```text
calculerSyntheseEmploye
  -> loadDailyResults
  -> partitionByIsoWeek
  -> calculateWeek
  -> calculatePeriod
  -> mapToDto
```

```text
calculerSalaireBrut
  -> calculateBaseSalary
  -> calculateAbsenceDeduction
  -> calculatePaidLeaveImpact
  -> calculateOvertimePay
  -> calculateNightSundayHolidayPremiums
  -> calculateSeniorityPremium
  -> calculatePerformanceAndAttendancePremiums
  -> applyGrossPayrollVariables
  -> calculateGross
```

```text
preparerInput
  -> resolvePeriod
  -> resolveEmploymentAt(period.end)
  -> resolveTaxHouseholdAt(period.end)
  -> resolveValidatedAttendanceSnapshot
  -> resolveLegalConfigurationAt(period.end)
  -> resolvePayrollVariables
  -> resolveTreasuryDeductions
  -> validateContext
```

## 16. Tests manquants

- `DailyAttendanceCalculatorTest` : journée continue/discontinue, pauses, nuit traversant minuit, correction justifiée, anomalies.
- `WeeklyAttendanceCalculatorTest` : semaines partielles, jour férié, dimanche, cadre, seuils exacts.
- `AttendancePeriodCalculatorTest` : agrégation pure de semaines.
- `AttendanceSnapshotTraceabilityTest` : chaque agrégat remonte aux jours.
- Tests séparés absence, HS, ancienneté, primes, indemnités, ISI, retenues.
- `PayrollContextResolverTest` avec deux contrats/configurations successifs.
- `PayrollInvariantsTest` et property tests.
- Tests d'immuabilité après validation et de régularisation.
- Tests d'isolation DB empêchant les schedulers de modifier les données pendant `mvn test`.

## 17. Tests de frontières

| Règle | Jeux minimaux |
|---|---|
| semaine | 39h59, 40h00, 40h01 ; 48h00, 48h01 |
| nuit | 21h59, 22h00, 22h01 ; 04h59, 05h00, 05h01 |
| retard | heure prévue -1 min, exacte, +1 min |
| CNaPS | plafond -1 Ar, plafond, plafond +1 Ar |
| IRSA | chaque borne -1 Ar, borne, borne +1 Ar |
| arrondis | x,004 ; x,005 ; x,006 et 4/5/6 Ar pour dizaine |
| ancienneté | 35, 36, 37 mois ; plafond 20 ans |
| prime assiduité | 44, 45, 46 minutes |
| quotité | capacité -1 Ar, exacte, +1 Ar |

## 18. Tests d'équivalence unitaire/collectif

Les tests existants ne prouvent pas l'équivalence demandée. Ajouter :

```java
assertThat(campaign.resultFor(employeeId))
    .usingRecursiveComparison()
    .ignoringFields("technicalExecutionTimestamp")
    .isEqualTo(singleResult);
```

Puis calculer RAKOTO seul, avec un autre salarié, et avec 99 contextes synthétiques ; le résultat métier et les lignes doivent être identiques. Le timestamp doit sortir du résultat métier ou être injecté.

## 19. Tests E2E Pointage -> Paie

Le `GrandWorkflowOhatraE2ETest` couvre déjà un flux large, mais doit vérifier chaque frontière avec des attentes indépendantes : badges -> résultat jour -> semaine -> période -> contrat Paie -> lignes -> net. Aujourd'hui il saute l'objet résultat journalier puisqu'il n'existe pas.

Scénario de référence à figer : salaire de base + HS + prime + indemnités soumises - absence = brut ; brut - CNaPS - santé = net imposable ; net imposable - IRSA - acompte - prêt + éléments nets = net avant arrondi ; puis écart d'arrondi explicite.

## 20. Problèmes de précision et d'arrondi

- Pointage manipule heures et Ariary en `double` dans moteurs, services et DTO.
- `PointageSyntheseService` limite une partie de la dérive en cumulant les minutes, mais les jours ont déjà été arrondis au centième d'heure à l'écriture.
- Le tarif déplacement est parfois divisé par `22.0`, indépendamment des jours ouvrables de la période.
- Les taux CNaPS/santé et tranches IRSA restent des `double` dans le moteur fiscal.
- Les majorations de Paie sont codées en dur (`1.30`, `1.50`, `0.30`, `1.00`) alors que des taux datés existent en base.
- La politique `setScale(2, HALF_UP)` est répétée à de nombreux niveaux ; le moment légal d'arrondi n'est pas centralisé.
- Le net est arrondi à la dizaine dans l'entité `BulletinPaie`, distinct de l'arrondi fiscal à la dizaine supérieure.

Décision cible : minutes entières pour le temps ; `BigDecimal` construit depuis chaînes/colonnes décimales pour montants et taux ; une `MoneyRoundingPolicy` explicite par type d'arrondi ; aucun arrondi de durée avant l'agrégation légale.

## 21. Problèmes de temporalité

- Le resolver Paie sélectionne une affectation au statut `ACTIF`, sans filtre par `date_debut/date_fin` de la période. Un recalcul historique peut donc prendre le salaire et le régime actuels.
- La campagne liste les employés actifs aujourd'hui, non ceux éligibles pendant la période.
- Le dernier taux de congé est chargé via `getTauxJournalierDernier`, sans preuve qu'il est applicable à la période.
- Une absence de configuration légale datée déclenche un fallback vers la configuration la plus récente : comportement explicitement dangereux pour l'historique.
- Plusieurs valeurs de Pointage viennent de `parametre_global` « actif », sans version applicable à la date.
- L'heure de calcul système dans le moteur rend deux résultats non strictement égaux.

## 22. Problèmes de persistance

- `pointage_jour` contient simultanément source, correction et résultat.
- Les cumuls Pointage n'enregistrent pas leur version, hash d'inputs ou liens de provenance.
- `PayrollExecutionEngine` retourne des entités de persistence plutôt qu'un résultat métier indépendant.
- Le calcul individuel public persiste même en simulation ; la simulation n'est donc pas une opération pure.
- Recalculer remplace le bulletin et ses lignes : la comparaison entre versions est difficile.
- Les lignes de bulletin prévoient `quantite`, `imposable`, `soumis_cotisation`, mais `creerLigne` ne renseigne pas tout le détail de règle/provenance.

## 23. Problèmes de workflow et concurrence

- Le calcul détermine lui-même `CALCULE` ou `VALIDE` selon le booléen `simulation`, alors que calcul et validation doivent être deux commandes distinctes.
- `validerPaiePeriode` recalcule les bulletins au lieu de valider le snapshot contrôlé ; les inputs peuvent avoir changé entre contrôle et validation.
- La campagne entière utilise une transaction unique : une erreur salarié annule tout et aucun statut 98 OK / 1 warning / 1 blocked n'est produit.
- L'optimistic locking existe sur `pointage_jour.version`, ce qui est positif.
- Le verrou partagé de période à la saisie protège partiellement la concurrence, mais il faut verrouiller/versionner aussi le snapshot consommé par la Paie.
- La réouverture restaure des états d'avances/prêts en masse ; une journalisation compensatoire est préférable à la mutation silencieuse.

## 24. Risques de régression

1. Modifier la représentation des heures peut changer les HS aux frontières de semaine.
2. Supprimer les fallbacks révélera des données historiques incomplètes.
3. Dater la sélection contractuelle peut modifier de nombreux bulletins passés qui utilisaient l'affectation courante.
4. Centraliser les arrondis peut déplacer quelques Ariary entre lignes et net.
5. Séparer badges/corrections/résultats nécessite une migration sans perdre auteurs et motifs.
6. Extraire le STC vers le moteur commun peut exposer des divergences fiscales existantes.

Chaque étape doit donc suivre : caractérisation -> extraction sans changement -> comparaison -> correction métier explicitement approuvée.

## 25. Roadmap de refactoring

1. Stabiliser l'environnement de test : DB dédiée, schedulers désactivés, données déterministes.
2. Ajouter les tests d'équivalence seul/campagne et les scénarios de référence actuels.
3. Ajouter des tests de temporalité avec deux affectations et deux configurations.
4. Introduire `DailyAttendanceContext/Result` en minutes sans changer la persistance.
5. Faire déléguer `enregistrerPointagesBatch` au calculateur journalier.
6. Introduire `WeeklyAttendanceResult`, puis faire agréger les résultats journaliers.
7. Introduire `AttendancePeriodResult` et un snapshot de provenance.
8. Faire du contrat Pointage -> Paie un objet typé et versionné.
9. Séparer `PayrollResult` des entités `BulletinPaie*` et injecter l'horloge technique hors moteur.
10. Corriger le resolver historique et supprimer les fallbacks légaux silencieux.
11. Extraire progressivement absence, HS, cotisations, fiscalité et retenues avec tests frontières.
12. Unifier STC et paie mensuelle sur les mêmes calculateurs de rubriques.
13. Séparer calcul, sauvegarde, contrôle, validation et clôture.
14. Passer la campagne à des contextes préchargés et transactions par employé avec résultat détaillé.
15. Supprimer les anciens calculs et fallbacks frontend après comparaison complète.

## 26. Matrice des responsabilités

| Règle | Pointage | Paie | Congés | Contrat | Frontend |
|---|---:|---:|---:|---:|---:|
| badges entrée/sortie | décide | | | fournit horaire | collecte/affiche |
| correction justifiée | décide/trace | consomme snapshot | peut justifier | | collecte/affiche |
| durée journalière | décide | consomme | | fournit horaire | affiche |
| qualification HS | décide semaine | consomme quantité | | fournit catégorie | affiche |
| montant HS | | décide | | fournit salaire | affiche |
| absence non payée | qualifie/quantifie | valorise | justifie/type | fournit régime | affiche |
| congé payé | matérialise présence | valorise | décide droits | fournit base | affiche |
| repas/déplacement | qualifie et valorise selon règle site | consomme snapshot | | fournit site | affiche |
| salaire/taux horaire | | décide | | source | affiche |
| CNaPS/santé | | décide | | fournit régime | affiche détail backend |
| IRSA/ISI | | décide | | fournit régime | affiche détail backend |
| net | | décide | | | affiche |

## 27. Tableau des problèmes

| ID | Gravité | Module | Fichier/méthode | Problème | Impact | Correction |
|---|---|---|---|---|---|---|
| A01 | P0 | Paie | `PayrollService.preparerInput` | affectation `ACTIF` non datée | salaire/régime historique faux | résoudre à la fin de période |
| A02 | P0 | Paie | `chargerParametresLegaux` | fallback dernière config | fiscalité historique potentiellement fausse | échec explicite si aucune config applicable |
| A03 | P0 | Pointage | `pointage_jour` | brut, correction, résultat fusionnés | audit impossible et recalcul ambigu | trois modèles/versionnement |
| A04 | P0 | Pointage | `enregistrerPointagesBatch` | calcul jour dans persistance batch | journée non testable isolément | `DailyAttendanceCalculator` pur |
| A05 | P1 | Pointage | moteurs/DTO/services | temps et argent en `double` | dérive et arrondis précoces | minutes + BigDecimal |
| A06 | P1 | Pointage | indemnités saisie/synthèse | montant journalier recalculé | sources de vérité concurrentes | agréger snapshot journalier |
| A07 | P1 | Paie | `PayrollExecutionEngine` | taux HS/nuit codés en dur | config datée ignorée | injecter coefficients applicables |
| A08 | P1 | Paie | `validerPaiePeriode` | validation recalcule | résultat contrôlé peut changer | valider snapshot/hash |
| A09 | P1 | Paie | campagne | transaction globale, fail-fast | pas de statut partiel explicable | transaction employé + batch result |
| A10 | P1 | Paie | moteur | timestamp/statut dans calcul | non-déterminisme et workflow couplé | sortir metadata/workflow |
| A11 | P1 | Paie | simulation | calcul individuel persiste | simulation à effets de bord | mapper/persister seulement sur commande |
| A12 | P1 | Paie | STC | pipeline parallèle | divergences de rubriques/fiscalité | réutiliser calculateurs communs |
| A13 | P1 | Frontend | `BulletinPaieDrawer` | CNaPS/OSTIE inventées à 50/50 | information fausse à l'écran | consommer lignes détaillées |
| A14 | P2 | Pointage | `PointageSyntheseService` | Map/String/DTO dans moteur | erreurs de clés, faible testabilité | résultats typés immuables |
| A15 | P2 | Paie | `PayrollCalculationInput` | mutable + défauts silencieux | contexte incomplet accepté | record validé sans fallback métier |
| A16 | P2 | Tests | suite Spring | schedulers actifs et DB partagée | tests non hermétiques | profil test sans jobs, DB isolée |
| A17 | P2 | Paie | arrondis dispersés | politique incohérente | écarts de centimes/dizaines | politique centrale documentée |
| A18 | P2 | Pointage | `fn_jours_ouvrables` IMMUTABLE | fonction lit des tables | qualification PostgreSQL incorrecte | `STABLE` |
| A19 | P2 | Paie | lignes bulletin | provenance/règle incomplète | explicabilité limitée | quantité, taux, config, source refs |
| A20 | P3 | Architecture | services > 1 000 lignes | navigation/maintenance difficiles | régression élevée | extractions verticales progressives |

## 28. Notation

| Dimension | Note |
|---|---:|
| Architecture Pointage | 4/10 |
| Architecture Paie | 6/10 |
| Granularité métier | 5/10 |
| Séparation responsabilités | 4/10 |
| Fiabilité des calculs | 5/10 |
| Testabilité | 6/10 |
| Traçabilité | 5/10 |
| Temporalité | 3/10 |
| Maintenabilité | 4/10 |
| Couplage Pointage <-> Paie | 6/10 |
| Qualité des tests | 6/10 |

**Note globale normalisée : 49/100.**

Cette note valorise le moteur individuel de Paie, l'orchestration collective correcte, les tests unitaires existants, les cumuls gelés et l'optimistic locking. Elle est fortement pénalisée par la temporalité contractuelle, l'absence de calcul journalier autonome, les `double`, les fallbacks silencieux et le mélange calcul/workflow/persistance.

## 29. Top 10 des refactorings

### P0 — 1. Résolution historique du contrat et du salaire

Problème : sélection de l'affectation courante. Danger : un bulletin ancien change après une évolution de carrière. Actuel : jointure sur statut `ACTIF`. Cible : `EmploymentResolver.resolveAt(employee, periodEnd)` avec unicité contrôlée. Classes : `PayrollService`, affectations/contrats. Tests : deux affectations successives, recalcul avant/après changement. Ordre : caractériser, introduire resolver, comparer, supprimer requête courante.

### P0 — 2. Configuration légale strictement applicable à la date

Problème : fallback sur la dernière configuration. Danger : application de règles futures au passé. Actuel : `findActiveAt`, puis `ORDER BY date_debut DESC LIMIT 1`. Cible : absence/chevauchement = erreur bloquante. Classes : `PayrollService`, `PaieConfigLegale`. Tests : veille, date de bascule, lendemain, trou et chevauchement. Ordre : auditer données, ajouter contraintes/tests, retirer fallback.

### P0 — 3. Calcul autonome d'une journée

Problème : la journée est calculée dans l'écriture batch. Danger : impossible d'expliquer/tester une journée sans SQL. Actuel : `enregistrerPointagesBatch`. Cible : contexte et résultat immuables en minutes. Classes : `PointageSaisieService`, `PointageCalculateurEngine`. Tests : matrice complète d'une journée. Ordre : extraire à comportement constant, faire déléguer, comparer colonnes.

### P0 — 4. Séparer brut, correction et résultat Pointage

Problème : une ligne mélange tous les états. Danger : modification silencieuse de l'historique. Actuel : `pointage_jour`. Cible : badge brut immuable, correction versionnée, résultat snapshot. Classes/tables : pointage journalier et audit. Tests : correction, approbation, recalcul, provenance. Ordre : tables additives, double lecture contrôlée, migration, bascule.

### P1 — 5. Temps exact et argent décimal

Problème : `double` partout dans Pointage. Danger : minute/centime perdu aux agrégations. Actuel : heures décimales arrondies par jour. Cible : minutes entières, `BigDecimal` pour Ariary/taux. Tests : longues périodes et valeurs x,005. Ordre : nouveaux types, adaptateurs DB/API, comparaison, suppression doubles.

### P1 — 6. Snapshot Pointage -> Paie versionné

Problème : cumul sans provenance. Danger : bulletin inexpliquable après correction. Actuel : `pointage_cumul_periode`. Cible : id/version/hash et liens semaine/jour/config. Tests : trace montant HS jusqu'aux jours. Ordre : enrichir schéma, écrire snapshot, consommer id figé.

### P1 — 7. PayrollResult pur

Problème : moteur produit entités, timestamp et statut. Danger : déterminisme incomplet, simulation persistée. Actuel : `BulletinPaieComplet`. Cible : `PayrollResult` pur, mapping/persistance externe. Tests : égalité récursive stricte et absence d'écriture. Ordre : introduire résultat, mapper à l'identique, déplacer metadata.

### P1 — 8. Extraire les règles de rubriques et les taux configurés

Problème : `calculerSalaireBrut` concentre les règles et code les coefficients. Danger : seuil/taux modifié à plusieurs endroits. Cible : calculateurs absence, HS, primes, ancienneté alimentés par config datée. Tests : une règle et ses frontières. Ordre : absence, HS, ancienneté, primes, variables.

### P1 — 9. Validation sans recalcul et campagne résiliente

Problème : valider recalcule ; une transaction englobe tous. Danger : résultat contrôlé différent et échec opaque. Cible : snapshot contrôlé validé par hash ; transaction salarié ; statuts OK/WARNING/BLOCKED. Tests : modification concurrente et un salarié invalide parmi 100. Ordre : hash/version, commande valider, transactions isolées.

### P2 — 10. Explicabilité backend unique jusqu'aux exports/UI

Problème : UI invente des détails et les lignes manquent de provenance. Danger : affichage incohérent avec le net. Cible : résultats détaillés CNaPS/IRSA/HS/absence transmis tels quels. Classes : moteurs, DTO bulletin, exports, drawer React. Tests : même ligne et même montant API/PDF/export. Ordre : enrichir backend, adapter consommateurs, supprimer fallbacks.

## 30. Validation effectuée et limites

- Inspection du code backend, frontend, schémas SQL, migrations actives, tests et documentation principale.
- `mvn test` exécuté avec Maven système, le wrapper du dépôt étant incomplet (`.mvn/wrapper/maven-wrapper.properties` absent).
- Les tests observés passent sur l'environnement PostgreSQL disponible ; le rapport de fin Maven doit être conservé comme preuve d'exécution.
- La suite démarre des schedulers et modifie la base de test : son herméticité n'est pas garantie.
- Aucun calcul n'a été certifié juridiquement contre un texte officiel à jour. Les commentaires « CGI/Code du travail » et fichiers Excel sont des hypothèses métier à faire valider par la DAF/conseil compétent.
- Aucun gros refactoring n'a été réalisé conformément à la mission.
