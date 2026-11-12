# Pointage — guide technique

Ce module couvre la saisie journalière, les anomalies, les calculs de temps et d'indemnités, la synthèse de période, le gel et les fiches exportées. Les routes `/pointages/*` et `/periodes/*` exigent une session ; les méthodes contrôlent les autorités avec `@PreAuthorize`.

## Structure

```text
pointage/
├── api/                 Contrôleurs REST
│   └── dto/             Entrées et vues HTTP
├── application/         Règle commune de qualification des statuts
│   ├── saisie/          Saisie, correction et import
│   ├── anomalie/        Détection et régularisation
│   ├── periode/         Synthèse, ajustements et gel
│   ├── document/        Fiche individuelle et exports Excel/PDF
│   └── backenddriven/   Actions de statut proposées à l’interface
├── domain/
│   ├── calculation/     Calculs de temps et d'indemnités
│   └── engine/          Calendrier ouvrable daté
└── persistence/         Dérogations d'indemnité
```

## Trouver une méthode par action

| Action | Entrée HTTP | Traitement principal | Calcul ou donnée |
|---|---|---|---|
| Prévisualiser ou enregistrer des jours | `PointageController.previsualiserJour`, `previsualiserJours`, `enregistrerBatch` | `PointageService.previsualiserPointage`, `previsualiserPointagesBatch`, `enregistrerPointagesBatch` | `AttendanceEngine.calculateDay`, `TemporalAttendanceEngine` ; écritures SQL dans `PointageService` |
| Corriger les heures d'un jour ou d'un lot | `PointageController.corrigerTempsJour`, `corrigerTempsLot` | `PointageService.corrigerTempsJour`, `corrigerTempsJours` | `CorrectedPointageTime.apply` |
| Voir ou régulariser une anomalie | `PointageController.getAnomalies`, `regulariser` | `PointageAnomalieService.getAnomalies`, `regulariserPointage` | `PointageService.saveRegularizedDay` ; services Congés si la décision porte sur un congé |
| Lire les semaines et la synthèse | `PointageController.getSemaines`, `getSemaineDetail`, `getSynthese`, `calculerTout` | `PointageSyntheseService.getSynthesePeriode`, `getSyntheseSemaineDetail`, `calculerToutesSemainesPeriode` | `AttendanceEngine.calculateWeek`, `segmentWeeks` |
| Vérifier puis geler une période | `PointageController.preflightPeriode` ; `PointagePeriodeController.executeCommand` | `PointageSyntheseService.preflightPeriode`, `gelerPeriode`, `gelerEmploye` | `PointageTemporalConfigurationGuard.verify` ; workflow dans `paie/application/backenddriven/workflow/PayrollPeriodWorkflowService` |
| Ajuster un solde de période | `PointageController.getAjustementsPeriode`, `creerAjustementPeriode`, `annulerAjustementPeriode` | `PointageAjustementPeriodeService.lister`, `creer`, `annuler`, `figerProvenance` | Ajustement motivé, distinct d'une correction de badge |
| Créer ou commander une période | `PointagePeriodeController.suggererPeriode`, `simulerPeriode`, `createPeriode`, `executeCommand` | `paie/application/PayrollPeriodCalendarService` et `paie/application/backenddriven/workflow/PayrollPeriodWorkflowService` | `PayrollPeriodStatus` définit les codes de statut |
| Afficher une fiche | `PointageController.getFichePointageEmploye` | `PointageEmployeService.getFichePointageEmploye` | `FichePointageEmployeDTO` et ses jours/semaines |
| Exporter une fiche | `PointageController.exportExcelFicheEmploye`, `exportPdfFicheEmploye` | `document/PointageDocumentService.preparer`, puis `PointageExcelExportService.genererExcelFicheEmploye` ou `PointagePdfExportService.generer` | `PointageDocumentData` choisit la provenance et formate les données |
| Préparer un import | `PointageController.modeleImport`, `apercuImport` | `PointageImportService.template`, `preview` | Le résultat est un aperçu ; la saisie passe par `PointageService` |
| Modifier tolérances et dérogations | `PointageController.getTolerances`, `saveTolerance`, `getDerogations`, `createDerogation`, `updateDerogation`, `deleteDerogation` | Contrôleur et `EmployeIndemniteDerogation` | Paramétrage du plancher d'arrivée et exceptions d'indemnité |

## Rôle de chaque fichier

### Application

| Fichier | Méthodes et responsabilité |
|---|---|
| `saisie/PointageService.java` | Saisie et aperçu des journées, validation contrat/site (`verifierEligibilitePointage`), correction de temps et proposition d'indemnités. Point de départ pour modifier l'écriture journalière. |
| `periode/PointageSyntheseService.java` | Calcul hebdomadaire et de période, lectures de synthèse, précontrôle, gel et dégel. Les versions avec `Connection` participent à la transaction appelante. |
| `anomalie/PointageAnomalieService.java` | Recherche des anomalies et régularisation transactionnelle, y compris l'intégration Congés. |
| `document/PointageEmployeService.java` | Construit la fiche d'un salarié, son contexte d'affectation daté et ses lignes. |
| `periode/PointageAjustementPeriodeService.java` | Ajoute ou annule les ajustements motivés du solde ; `totalMinutesValidees` et `figerProvenance` sont utilisés au gel. |
| `saisie/PointageImportService.java` | Produit le modèle Excel et analyse un fichier pour un aperçu d'import. |
| `AttendanceStatusPolicy.java` | Résout le code de présence selon jour, calcul et régularisation : `resolve`, `resolveRegularizedAbsence`, `resolveRegularizedWork`, `resolveRegularizedLeaveCoverage`. |
| `periode/PointageTemporalConfigurationGuard.java` | Vérifie les configurations datées avant calcul ou gel : `verify`. |
| `backenddriven/PointageStatusActions.java` | `actions` construit la liste des qualifications proposées à l’interface selon la journée ; aucune écriture métier. |
| `document/PointageDocumentService.java` | `preparer` choisit la fiche courante ou le snapshot figé et ses ajustements. |
| `document/PointageDocumentData.java` | Contrat de document et formats communs Excel/PDF (`from`, `days`, `dualHours`, `money`). |
| `document/PointageExcelExportService.java` | Génère la fiche Excel et son nom (`genererExcelFicheEmploye`, `buildExportFilename`). |
| `document/PointagePdfExportService.java` | Génère la fiche PDF (`generer`). |

### Domaine et persistance

| Fichier | Méthodes et responsabilité |
|---|---|
| `domain/calculation/AttendanceEngine.java` | Règles de calcul jour/semaine : `calculateDay`, `calculateWeek`, `segmentWeeks`, `PeriodBalance.calculate`. |
| `domain/calculation/TemporalAttendanceEngine.java` | Construit et découpe les intervalles réels, y compris la nuit et le franchissement de minuit : `createIntervals`, `partition`, `project`. |
| `domain/calculation/CorrectedPointageTime.java` | Applique une correction explicite aux sorties : `apply`. |
| `domain/calculation/PointageAllowancePolicy.java` | Éligibilité, décision par défaut et montant journalier d'une indemnité : `eligibleForAllowance`, `resolve`, `dailyAmount`. |
| `domain/calculation/QualifiedTimeBucket.java` | Minute qualifiée HS, nuit, dimanche ou férié ; objet de résultat du calcul temporel. |
| `domain/engine/CalendrierOuvrableEngine.java` | Résout jours fériés/chômés et site daté, puis compte jours ouvrables ; ce moteur lit la base via `Connection`. |
| `domain/PayrollPeriodStatus.java` | Statuts canoniques de période et libellés (`getLibelle`, `isValid`). |
| `persistence/EmployeIndemniteDerogation.java` | Dérogation d'indemnité datée : `controler`, `listAllActive`, `cloturerParIndemnite`. |

### Contrats HTTP (`api/dto`)

| Fichier | Contenu |
|---|---|
| `SaisiePointageJourDTO.java` | Saisie et aperçu d'une journée. |
| `PointageJourDTO.java` | Journée lue dans une fiche. |
| `PointageTronconSemaineDTO.java` | Segment hebdomadaire et ses résultats. |
| `PointageSyntheseMensuelleDTO.java` | Résultat de synthèse de période. |
| `PointageAnomalieDTO.java` | Anomalie affichée et traitée. |
| `FichePointageEmployeDTO.java` | Fiche individuelle assemblée par `PointageEmployeService`. |
| `PeriodePaieDTO.java` | Période exposée par `PointagePeriodeController`. |

## Index des méthodes principales

| Méthode | Effet concret | À utiliser quand |
|---|---|---|
| `PointageController.calculerTout` → `PointageSyntheseService.calculerToutesSemainesPeriode` | Retourne le nombre de semaines calculées à la demande. **N’enregistre aucun résultat** : le service appelle `getSemainesPeriode` et construit seulement une réponse. | L'interface demande un aperçu du calcul des semaines. |
| `PointageSyntheseService.getSynthesePeriode` | Assemble les résultats de période pour les salariés à partir des données courantes. | Afficher la synthèse ou alimenter le gel. |
| `PointageSyntheseService.preflightPeriode` | Recense les blocages et journées manquantes avant gel. | Avant une transition de période ; consulter `pret()` sur le résultat. |
| `PointageSyntheseService.gelerPeriode` / `gelerEmploye` | Écrit le cumul, les journées sources et les qualifications temporelles en snapshot pour la Paie ; la variante salarié ne fige que son dossier. | Transition autorisée par `PayrollPeriodWorkflowService`, avec la même transaction SQL. |
| `PointageSyntheseService.degelerPeriode` | Retire le gel de la période selon le workflow appelant. | Réouverture autorisée ; ne pas l'appeler depuis une simple lecture. |
| `PointageService.previsualiserPointage` | Valide les données et horaires du site, puis renvoie la journée calculée. **N'écrit pas** le pointage. | Aperçu avant saisie. |
| `PointageService.enregistrerPointagesBatch` | Enregistre plusieurs journées dans la connexion fournie ; retourne le bilan de l'écriture. | Validation de la saisie quotidienne. |
| `PointageService.corrigerTempsJour` / `corrigerTempsJours` | Remplace la correction de minutes, avec motif et contrôle de version ; les heures de badge restent intactes. Le lot est atomique. | Corriger un temps reconnu sans modifier le badge brut. |
| `PointageAnomalieService.regulariserPointage` | Traite une anomalie selon la décision reçue ; peut appeler Congés, écrire la journée régularisée et recalculer la semaine. | Décision sur absence ou présence anormale. |
| `PointageService.saveRegularizedDay` | Écrit la journée corrigée et synchronise ses indemnités dans la transaction **détenue par l'appelant**. | Depuis une régularisation, pas comme opération HTTP autonome. |
| `PointageAjustementPeriodeService.figerProvenance` | Rattache au snapshot les ajustements validés qui ont influencé le solde. | Pendant le gel, pour garder une trace vérifiable. |
| `PointageDocumentService.preparer` | Choisit les données courantes ou le dernier snapshot figé, puis ajoute ajustements et montants au document. | Avant tout export Excel ou PDF. |
| `PointagePeriodeController.executeCommand` | Délègue une transition (`SUBMIT`, `APPROVE`, `CLOSE`, etc.) au workflow Paie, qui vérifie droits et état. | Changer le statut de la campagne ; `retiredGenericStatusMutation` refuse l'ancien changement direct. |
| `PointageStatusActions.actions` | Prépare les choix de qualification affichés dans `SaisiePointageJourDTO.statusActions` ; n’enregistre aucune décision. | Pour modifier les actions proposées par le front. |
| `AttendanceStatusPolicy.resolve` | Déduit le statut affiché/enregistré d'une journée à partir de la saisie, du calcul et de la date. | Lors du calcul ou de la régularisation d'une journée. |
| `TemporalAttendanceEngine.partition` / `project` | Découpe les intervalles travaillés aux frontières temporelles, puis produit les minutes qualifiées (nuit, dimanche, férié). | Modifier la qualification temporelle sans toucher au workflow HTTP. |

## Règles à préserver

- `EmploymentContextResolver` fournit contrat, site et dispense aux dates du pointage. Les chevauchements d'affectation sont signalés ; le précontrôle réutilise ce contexte avant gel.
- Les paliers HS 30 % et 50 % sont calculés par tronçon hebdomadaire. Le solde de période additionne ces tranches, retire les déficits et applique les ajustements validés. Un retrait consomme les HS 30 % avant les HS 50 %.
- Une nuit est calculée sur les intervalles réellement travaillés, aux bornes de chaque date civile. Une vacation qui déborde sur la campagne suivante bloque le gel tant que sa suite n'est pas saisie.
- Un changement d'affectation qui modifie l'éligibilité HS au milieu d'une semaine bloque transmission et gel. Les snapshots déjà consommés par la Paie restent historiques.
- La décision de rémunération d'un congé appartient au domaine Congés. La décision de période appartient au workflow Paie. Le détail chiffré des règles est dans `backend/docs/boky/module-pointage-presence.md`.

## Vérification

Après une restructuration : `mvn -f backend/pom.xml clean test`, `npm --prefix frontend run test:api` et `npm --prefix frontend run build`. Les tests PostgreSQL nécessitent une base accessible ; vérifier leur statut dans les rapports Surefire.
