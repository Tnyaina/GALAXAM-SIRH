# CARTOGRAPHIE DÉTAILLÉE DES WORKFLOWS RUNTIME (WF01 → WF10)

**Date :** 17 septembre 2026  
**Référence :** Phase 0 → Phase 1C Gate d'Intégration Réel  
**Statut :** Cartographie exhaustive contradictoire Front → API → Controller → Service → Owner → DB

---

## WF01 — Saisie de Pointage Quotidien
- **Front Component :** `frontend/src/pages/pointage/SaisieQuotidiennePage.jsx` (`handleSave`)
- **API Client :** `frontend/src/api/pointage/pointageSaisieService.js` (`enregistrerBatch`)
- **API Endpoint :** `POST /pointages/saisie/batch`
- **Controller :** `PointageSaisieController.java` (`enregistrerBatch`)
- **Service :** `PointageSaisieService.java` (`enregistrerPointagesBatch`)
- **Owner Canonique :** `DailyAttendanceCalculator.java`
  - `calculateDay(DailyAttendanceContext)` : calcul des durées matin/après-midi, total, retards, nuits, statut journalier.
  - `calculateMealAmount(...)` : indemnités repas.
  - `calculateTravelAmount(...)` : indemnités déplacement.
- **DB Persistence :** `pointage_jour` (colonnes `total_heures`, `heures_matin`, `heures_am`, `heures_nuit`, `minutes_retard`, `version`), `pointage_indemnite_jour`.

---

## WF02 — Détection et Correction d'Anomalie
- **Front Component :** `frontend/src/pages/pointage/SuiviAnomaliesPage.jsx` & `frontend/src/components/pointage/AnomalieRegularisationModal.jsx` / `DafAjustementPointageModal.jsx`
- **API Client :** `frontend/src/api/pointage/pointageAnomalieService.js` (`regulariser`)
- **API Endpoint :** `PUT /pointages/anomalies/{idPointage}/regulariser`
- **Controller :** `PointageAnomalieController.java` (`regulariser`)
- **Service :** `PointageAnomalieService.java` (`regulariserPointage`)
- **Owner Canonique :**
  - Étape 1 : `DailyAttendanceCalculator.calculateDay` (recalcul immédiat des heures du jour corrigé)
  - Étape 2 : `PointageSyntheseService.recalculerSemainePourDate` $\to$ `WeeklyAttendanceCalculator.calculateWeek` (recalcul immédiat du tronçon hebdomadaire)
- **DB Persistence :** `pointage_jour` (UPDATE optimiste `version = version + 1`), `pointage_semaine_calcule` (recalcul sans reliquat d'ancienne valeur).

---

## WF03 — Synthèse Hebdomadaire & Compensation des Heures
- **Front Component :** `frontend/src/pages/pointage/ArbitrageDafPage.jsx` (`handleRecalculerTout`)
- **API Client :** `frontend/src/api/pointage/pointageSyntheseService.js` (`calculerTout`)
- **API Endpoint :** `POST /pointages/synthese/calculer-tout`
- **Controller :** `PointageSyntheseController.java` (`calculerTout`)
- **Service :** `PointageSyntheseService.java` (`calculerHebdomadairePourPeriode`)
- **Owner Canonique :**
  - `PeriodWeekSegmenter.java` (`segment(dateDebut, dateFin)`) : découpage rigoureux aux bornes de la semaine civile et de la période (frontière 25/26).
  - `WeeklyAttendanceCalculator.java` (`calculateWeek`) : calcul du surplus journalier compensable, déficit hebdomadaire, heures supplémentaires nettes (40h), et tranches `HS30` / `HS50`.
- **DB Persistence :** `pointage_semaine_calcule` (`debut_semaine`, `nb_jours_troncon`, `total_heures_travaillees`, `heures_hs_brutes`, `heures_hs_nettes`, `heures_hs_30`, `heures_hs_50`, `heures_deficit`).

---

## WF04 — Gestion du Cycle de Vie de la Période de Paie
- **Front Component :** `frontend/src/pages/pointage/ArbitrageDafPage.jsx` & `frontend/src/components/paie/CockpitPaieTab.jsx`
- **API Client :** `frontend/src/api/paie/periodePaieService.js` (`executeCommand`)
- **API Endpoint :** `POST /periodes/{id}/actions/{command}` (`SUBMIT`, `APPROVE`, `REJECT`, `CLOSE`, `REOPEN`)
- **Controller :** `PeriodePaieController.java` (`executeCommand`)
- **Service :** `PayrollPeriodWorkflowService.java` (`execute`)
- **Owner Canonique :** `PayrollPeriodWorkflowService.java` (machine d'état transitionnelle stricte, vérification des prérequis et des rôles).
- **DB Persistence :** `periode_paie` (`id_statut_periode`, `date_validation`, `valide_par`, `motif_rejet`, `version`).

---

## WF05 — Gel des Pointages (Approbation DAF)
- **Front Component :** `frontend/src/pages/pointage/ArbitrageDafPage.jsx` (Action `APPROVE`)
- **API Client :** `frontend/src/api/paie/periodePaieService.js` (`executeCommand(id, 'APPROVE')`)
- **API Endpoint :** `POST /periodes/{id}/actions/APPROVE`
- **Controller :** `PeriodePaieController.java` (`executeCommand`)
- **Service :** `PayrollPeriodWorkflowService.java` $\to$ `PointageSyntheseService.java` (`gelerPeriode`)
- **Owner Canonique :**
  - `WorkIntervalFactory.createIntervals` : reconstruction des vacations continues y compris traversant minuit (J $\to$ J+1).
  - `AttendanceTemporalPartitioner.partition` : partitionnement temporel (22:00, 00:00, 05:00, samedi, dimanche, jour férié).
  - `OvertimeProjectionEngine.project` : projection chronologique des heures `REGULAR`, `OVERTIME_30`, `OVERTIME_50` décidées par Phase 1B sur les segments temporels.
- **DB Persistence :**
  - `pointage_cumul_periode` (`statut_snapshot = 'GELE'`, `version_snapshot`)
  - `pointage_snapshot_jour`
  - `pointage_snapshot_semaine`
  - `pointage_snapshot_bucket` (`QualifiedTimeBucket` : `overtime_tier`, `day_special_type`, `night_work_type`, `minutes`).

---

## WF06 — Réouverture Exceptionnelle de Période
- **Front Component :** `frontend/src/pages/pointage/ArbitrageDafPage.jsx` / `frontend/src/components/paie/CockpitPaieTab.jsx` (Action `REOPEN` avec motif obligatoire $\ge$ 5 car.)
- **API Client :** `frontend/src/api/paie/periodePaieService.js` (`executeCommand(id, 'REOPEN', { motif })`)
- **API Endpoint :** `POST /periodes/{id}/actions/REOPEN`
- **Controller :** `PeriodePaieController.java` (`executeCommand`)
- **Service :** `PayrollPeriodWorkflowService.java` $\to$ `PointageSyntheseService.java` (`degelerPeriode`) & `PayrollService.java` (`rouvrirDonneesPaie`)
- **Owner Canonique :** `PointageSyntheseService.degelerPeriode`
- **DB Persistence :**
  - `pointage_cumul_periode` passe à `statut_snapshot = 'REMPLACE'`
  - `pointage_semaine_calcule.gele_par = NULL`
  - L'intégrité historique est préservée : l'ancien snapshot n'est pas écrasé silencieusement, un nouveau snapshot avec `version_snapshot + 1` sera créé au prochain gel.

---

## WF07 — Scellement Snapshot des QualifiedTimeBuckets
- **Front Component :** Déclenché automatiquement lors de l'approbation (WF05).
- **API Endpoint :** `POST /periodes/{id}/actions/APPROVE`
- **Controller / Service :** `PointageSyntheseService.enregistrerSnapshotBuckets`
- **Owner Canonique :** `OvertimeProjectionEngine.project`
  - Invariant strict : `somme(Phase 1B) == somme(Timeline)`
  - Construction des `QualifiedTimeBucket`s orthogonaux à deux dimensions :
    1. Dimension HS : `REGULAR`, `OVERTIME_30`, `OVERTIME_50`.
    2. Dimension Calendaire/Circadienne : `DAY`/`NIGHT`, `NONE`/`SATURDAY`/`SUNDAY`/`PUBLIC_HOLIDAY`/`SUNDAY_AND_PUBLIC_HOLIDAY`.
- **DB Persistence :** `pointage_snapshot_bucket` (clé étrangère `id_cumul`, `id_pointage`, dates, durées en minutes, qualifications).

---

## WF08 — Préparation des Inputs de Paie
- **Front Component :** Déclenché au lancement de la simulation ou du calcul du bulletin.
- **API Endpoint :** `POST /paie/calculer-periode` ou `POST /paie/calculer-employe`
- **Controller :** `PaieController.java`
- **Service :** `PayrollService.java` (`preparerInput`)
- **Owner Canonique :** `PayrollLegalContextResolver.java` (`resolve`)
  - Charge le snapshot scellé `pointage_cumul_periode` (`statut_snapshot = 'GELE'`).
  - Charge la liste exhaustive des `QualifiedTimeBucket` depuis `pointage_snapshot_bucket`.
  - Construit `PayrollAttendanceInput`.
  - Résout les barèmes légaux historiques (`TemporalRateResolver` partitionné par date d'effet depuis `paie_config_legale`).
  - Résout la politique de collision de sujétions spéciales (`SpecialDayCollisionPolicy`).
- **DB Persistence :** Lecture exclusive DB sous verrou partagé.

---

## WF09 — Simulation de Paie
- **Front Component :** `frontend/src/pages/paie/CalculStepPage.jsx` (`simulation = true`)
- **API Client :** `frontend/src/api/paie/paieService.js` (`calculerPaiePeriode(id, true)`)
- **API Endpoint :** `POST /paie/calculer-periode` (`{ idPeriodePaie, simulation: true }`)
- **Controller :** `PaieController.java` (`calculerPaiePeriode`)
- **Service :** `PayrollService.java` (`calculerPaiePeriode`)
- **Owner Canonique :** `PayrollExecutionEngine.java` & `OvertimePayCalculator.java`
  - Exécute le calcul complet en mémoire sans persistance finale si mode simulation.
  - Retourne les KPI et totaux projetés au frontend.
- **DB Persistence :** Rollback transactionnel ou marquage `est_simulation = true`.

---

## WF10 — Calcul Définitif de Paie & Valorisation Monétaire
- **Front Component :** `frontend/src/pages/paie/CalculStepPage.jsx` & `frontend/src/components/paie/CockpitPaieTab.jsx` (`simulation = false`)
- **API Client :** `frontend/src/api/paie/paieService.js` (`calculerPaiePeriode(id, false)`)
- **API Endpoint :** `POST /paie/calculer-periode`
- **Controller :** `PaieController.java` (`calculerPaiePeriode`)
- **Service :** `PayrollService.java` (`calculerBulletinEmploye`)
- **Owner Canonique :**
  - `PayrollExecutionEngine.calculerBulletin(input)`
  - `OvertimePayCalculator.calculate(input, tauxHoraire)` :
    - Consomme `PayrollAttendanceInput.buckets()`.
    - Valorisation bucket par bucket à la date civile exacte (`rateResolver.resolveRatesAt(bucketDate)`).
    - Verrou anti-double paiement de la base horaire (`payrollAdditionalAmount = 0` pour heures REGULAR).
    - Articulation stricte des majorations spéciales (Art. 3 Décret 72-226).
    - Production de `BucketValuationResult`.
  - Intégration dans les lignes du bulletin :
    - `HS_BASE_LINE` (base horaire des heures supplémentaires)
    - `HS_MAJ_30_LINE` (majoration 30%)
    - `HS_MAJ_50_LINE` (majoration 50%)
    - `MAJ_NUIT_LINE` (majoration nuit)
    - `MAJ_DIMANCHE_LINE` (majoration dimanche)
    - `MAJ_FERIE_LINE` (majoration jour férié)
- **DB Persistence :** `paie_bulletin` & `paie_bulletin_ligne`.
