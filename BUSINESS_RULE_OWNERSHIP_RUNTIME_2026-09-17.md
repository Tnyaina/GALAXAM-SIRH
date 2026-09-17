# MATRICE D'OWNERSHIP DES RÈGLES MÉTIER RUNTIME (PHASE 0 → 1C)

**Date d'audit :** 17 septembre 2026  
**Périmètre :** Galaxam SIRH — Modules Pointage, Synthèse, Snapshot & Payroll Valuation  
**Objectif :** Cartographie contradictoire de l'exécution réelle, identification des owners canoniques et élimination des bypasses/duplications.

---

## 1. MATRICE SYNTHÉTIQUE DES SOURCES DE VÉRITÉ

| Règle / Domaine | Owner canonique | Caller runtime | API / Controller | Frontend | DB source / Stockage | Legacy alternatif | Statut |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Temps journalier brut** (heures matin/am, total, retard) | `DailyAttendanceCalculator.calculateDay` | `PointageSaisieService.enregistrerPointagesBatch` & `PointageAnomalieService.regulariserPointage` | `POST /pointages/saisie/batch`<br>`PUT /pointages/anomalies/{id}/regulariser` | `SaisieQuotidiennePage.jsx`<br>`AnomalieRegularisationModal.jsx` | `pointage_jour` | Aucun | `ACTIVE_CANONICAL` |
| **Indemnités repas journalières** | `DailyAttendanceCalculator.calculateMealAmount` | `PointageSaisieService.enregistrerPointagesBatch` | `POST /pointages/saisie/batch` | `SaisieQuotidiennePage.jsx` | `pointage_indemnite_jour` | Aucun | `ACTIVE_CANONICAL` |
| **Indemnités déplacement journalières** | `DailyAttendanceCalculator.calculateTravelAmount` | `PointageSaisieService.enregistrerPointagesBatch` | `POST /pointages/saisie/batch` | `SaisieQuotidiennePage.jsx` | `pointage_indemnite_jour` | Aucun | `ACTIVE_CANONICAL` |
| **Tronçonnage des semaines** (frontière 25/26) | `PeriodWeekSegmenter.segment` | `PointageSyntheseService.calculerHebdomadairePourPeriode` | `POST /pointages/synthese/calculer-tout` | `ArbitrageDafPage.jsx` | Mémoire / bornage | Aucun | `ACTIVE_CANONICAL` |
| **Temps hebdo, compensation & HS30/HS50** | `WeeklyAttendanceCalculator.calculateWeek` | `PointageSyntheseService.calculerHebdomadairePourPeriode` & `recalculerSemainePourDate` | `POST /pointages/synthese/calculer-tout`<br>`PUT /pointages/anomalies/{id}/regulariser` | `ArbitrageDafPage.jsx`<br>`SuiviAnomaliesPage.jsx` | `pointage_semaine_calcule` | `sqlHsEst` (supprimé dans gate) | `ACTIVE_CANONICAL` |
| **Reconstruction vacations & Cross-Midnight** | `WorkIntervalFactory.createIntervals` | `PointageSyntheseService.enregistrerSnapshotBuckets` | `POST /periodes/{id}/actions/APPROVE` (Gel) | `ArbitrageDafPage.jsx` | Mémoire (`List<WorkInterval>`) | Aucun | `ACTIVE_CANONICAL` |
| **Partitionnement temporel** (22h, 00h, 05h, dimanche, férié) | `AttendanceTemporalPartitioner.partition` | `PointageSyntheseService.enregistrerSnapshotBuckets` | `POST /periodes/{id}/actions/APPROVE` (Gel) | `ArbitrageDafPage.jsx` | Mémoire (`List<TemporalBucket>`) | Aucun | `ACTIVE_CANONICAL` |
| **Projection chronologique HS** (REGULAR / HS30 / HS50) | `OvertimeProjectionEngine.project` | `PointageSyntheseService.enregistrerSnapshotBuckets` | `POST /periodes/{id}/actions/APPROVE` (Gel) | `ArbitrageDafPage.jsx` | `pointage_snapshot_bucket` (`QualifiedTimeBucket`) | Aucun | `ACTIVE_CANONICAL` |
| **Scellement & Versioning Snapshot** | `PointageSyntheseService.gelerPeriode` | `PayrollPeriodWorkflowService.execute` (APPROVE) | `POST /periodes/{id}/actions/APPROVE` | `ArbitrageDafPage.jsx` | `pointage_cumul_periode`<br>`pointage_snapshot_bucket` | Aucun | `ACTIVE_CANONICAL` |
| **Dégel lors de réouverture** | `PointageSyntheseService.degelerPeriode` | `PayrollPeriodWorkflowService.execute` (REOPEN) | `POST /periodes/{id}/actions/REOPEN` | `ArbitrageDafPage.jsx`<br>`CockpitPaieTab.jsx` | `pointage_cumul_periode` (`statut='REMPLACE'`) | Aucun | `ACTIVE_CANONICAL` |
| **Résolution historique des taux légaux** | `PayrollLegalContextResolver.resolve` | `PayrollService.preparerInput` | `POST /paie/calculer-periode`<br>`POST /paie/calculer-employe` | `CalculStepPage.jsx`<br>`CockpitPaieTab.jsx` | `paie_config_legale`<br>`ref_taux_majoration` | Aucun | `ACTIVE_CANONICAL` |
| **Valorisation monétaire HS, Nuit, Dimanche, Férié** | `OvertimePayCalculator.calculate` | `PayrollExecutionEngine.calculerBulletin` | `POST /paie/calculer-periode`<br>`POST /paie/calculer-employe` | `CalculStepPage.jsx`<br>`CockpitPaieTab.jsx` | `paie_bulletin_ligne` | Aucun | `ACTIVE_CANONICAL` |
| **Orchestration finale de la paie** | `PayrollExecutionEngine.calculerBulletin` | `PayrollService.calculerBulletinEmploye` & `calculerPaiePeriode` | `POST /paie/calculer-periode`<br>`POST /paie/calculer-employe` | `CalculStepPage.jsx`<br>`CockpitPaieTab.jsx` | `paie_bulletin`<br>`paie_bulletin_ligne` | `CalculateurPaieEngine` (purgé) | `ACTIVE_CANONICAL` |
| **CalculateurPaieEngine** (Legacy) | *Inexistant* (purité confirmée) | Aucun (0 appelant dans tout le codebase) | Aucun | Aucun | Aucune | Inexistant | `DEAD_CODE` / `SUPERSEDED` |

---

## 2. PREUVES DE CALL GRAPH RUNTIME

### 2.1 Saisie de pointage (`WF01`)
```text
SaisieQuotidiennePage.jsx (handleSave)
  ↓
pointageSaisieService.enregistrerBatch(rows)
  ↓
POST /pointages/saisie/batch
  ↓
PointageSaisieController.enregistrerBatch(@RequestBody List<SaisiePointageJourDTO>)
  ↓
PointageSaisieService.enregistrerPointagesBatch(list, connection)
  ↓
DailyAttendanceCalculator.calculateDay(DailyAttendanceContext)
DailyAttendanceCalculator.calculateMealAmount(...)
DailyAttendanceCalculator.calculateTravelAmount(...)
  ↓
INSERT / UPDATE pointage_jour & pointage_indemnite_jour
```

### 2.2 Régularisation d'anomalie (`WF02`)
```text
SuiviAnomaliesPage.jsx / AnomalieRegularisationModal.jsx (handleSaveRegularisation)
  ↓
pointageAnomalieService.regulariser(idPointage, payload)
  ↓
PUT /pointages/anomalies/{idPointage}/regulariser
  ↓
PointageAnomalieController.regulariser(idPointage, payload)
  ↓
PointageAnomalieService.regulariserPointage(idPointage, payload)
  ↓
DailyAttendanceCalculator.calculateDay(DailyAttendanceContext)
  ↓
UPDATE pointage_jour (version optimistic locking)
  ↓
PointageSyntheseService.recalculerSemainePourDate(idPeriode, datePointage, idSite, connection)
  ↓
WeeklyAttendanceCalculator.calculateWeek(...)
  ↓
UPDATE pointage_semaine_calcule
```

### 2.3 Synthèse hebdomadaire (`WF03`)
```text
ArbitrageDafPage.jsx (handleRecalculerTout)
  ↓
pointageSyntheseService.calculerTout(idPeriode, idSite)
  ↓
POST /pointages/synthese/calculer-tout
  ↓
PointageSyntheseController.calculerTout(payload)
  ↓
PointageSyntheseService.calculerHebdomadairePourPeriode(idPeriode, idSite, connection)
  ↓
PeriodWeekSegmenter.segment(dateDebut, dateFin) [Découpage en tronçons ISO ∩ période]
  ↓
Pour chaque tronçon de chaque employé :
  WeeklyAttendanceCalculator.calculateWeek(context, days)
  ↓
INSERT / UPDATE pointage_semaine_calcule
```

### 2.4 Approbation & Gel du Pointage (`WF05` & `WF07`)
```text
ArbitrageDafPage.jsx (handleApprove)
  ↓
periodePaieService.executeCommand(idPeriode, 'APPROVE', payload)
  ↓
POST /periodes/{idPeriode}/actions/APPROVE
  ↓
PeriodePaieController.executeCommand(id, command, payload)
  ↓
PayrollPeriodWorkflowService.execute(APPROVE)
  ↓
PointageSyntheseService.gelerPeriode(idPeriode, connection)
  ↓
PointageSyntheseService.enregistrerSnapshotBuckets(idCumul, idEmploye, idPeriode, connection)
  ↓
WorkIntervalFactory.createIntervals(...)  [Reconstruction vacations cross-midnight]
  ↓
AttendanceTemporalPartitioner.partition(...)  [Découpage 22h, 00h, 05h, férié, dimanche]
  ↓
OvertimeProjectionEngine.project(...)  [Projection stricte des quotas Phase 1B]
  ↓
INSERT INTO pointage_snapshot_bucket (QualifiedTimeBucket)
INSERT INTO pointage_cumul_periode (statut_snapshot = 'GELE')
```

### 2.5 Calcul & Simulation de la Paie (`WF09` & `WF10`)
```text
CalculStepPage.jsx / CockpitPaieTab.jsx (lancerCalcul)
  ↓
paieService.calculerPaiePeriode(idPeriode, simulation)
  ↓
POST /paie/calculer-periode
  ↓
PaieController.calculerPaiePeriode(payload)
  ↓
PayrollService.calculerPaiePeriode(idPeriode, simulation, userId)
  ↓
PayrollService.preparerInput(idEmploye, idPeriode, ...)
  - Charge pointage_cumul_periode (GELE)
  - Charge pointage_snapshot_bucket (List<QualifiedTimeBucket>)
  - Construit PayrollAttendanceInput
  - PayrollLegalContextResolver.resolve(...) -> TemporalRateResolver + SpecialDayCollisionPolicy
  ↓
PayrollExecutionEngine.calculerBulletin(PayrollCalculationInput)
  ↓
OvertimePayCalculator.calculate(input, tauxHoraire)
  - Consomme QualifiedTimeBucket[]
  - Résout le taux historique par date civile de chaque bucket
  - Applique l'anti-double base (base horaire 1.00 payée 1 fois max)
  - Articule les sujétions spéciales (Art. 3 Décret 72-226)
  ↓
BucketValuationResult
  ↓
PayrollExecutionEngine génère les lignes de bulletin :
  - HS_BASE_LINE (si HS)
  - HS_MAJ_30_LINE
  - HS_MAJ_50_LINE
  - MAJ_NUIT_LINE
  - MAJ_DIMANCHE_LINE
  - MAJ_FERIE_LINE
  ↓
INSERT / UPDATE paie_bulletin & paie_bulletin_ligne
```
