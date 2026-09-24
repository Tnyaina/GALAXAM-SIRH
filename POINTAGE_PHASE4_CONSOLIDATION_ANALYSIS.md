# Pointage — phase 4, inventaire préalable

État observé avant modification Phase 4 : 35 fichiers Java dans `mg.galaxam.sirh.pointage` (6 contrôleurs, 5 services, 12 fichiers de calcul, 9 DTO, 1 moteur calendrier, 2 modèles de paramétrage). Les modifications non commitées des phases précédentes sont préservées.

La décision ci-dessous repose sur la responsabilité réelle et les imports, pas seulement sur le fait qu'un fichier soit référencé. Les DTO d'API volumineux et les contrats consommés par Paie restent autonomes ; fusionner les services de 900 à 1 800 lignes créerait une classe de plus de 3 000 lignes, contraire à la limite de lisibilité demandée.

| Fichier | Responsabilité réelle | Utilisateurs | Décision | Cible |
|---|---|---|---|---|
| `api/PeriodePaieController.java` | Transitions et contexte période | Frontend, workflow Paie | MERGE/RENAME | `PointagePeriodeController` |
| `api/PointageAnomalieController.java` | Deux routes anomalies | Frontend | MERGE | `PointageController` |
| `api/PointageEmployeController.java` | Fiche et export employé | Frontend | MERGE | `PointageController` |
| `api/PointageParametreController.java` | Tolérances et dérogations administratives | Frontend | MERGE | `PointageController` |
| `api/PointageSaisieController.java` | Deux routes de saisie | Frontend | MERGE | `PointageController` |
| `api/PointageSyntheseController.java` | Synthèse, semaines et détail | Frontend | MERGE | `PointageController` |
| `api/dto/DerogationRequest.java` | Requête d'un seul contrôleur | Paramétrage | INLINE | `PointageController` |
| `api/dto/FichePointageEmployeDTO.java` | Contrat de fiche complexe | Service employé, export | KEEP | Inchangé |
| `api/dto/PeriodePaieDTO.java` | Contrat public période | Contrôleur période | KEEP | Inchangé |
| `api/dto/PointageAnomalieDTO.java` | Contrat public anomalie | Contrôleur, service | KEEP | Inchangé |
| `api/dto/PointageJourDTO.java` | Contrat public journée | Services, grille | KEEP | Inchangé |
| `api/dto/PointageSyntheseMensuelleDTO.java` | Contrat public synthèse complexe | Synthèse, export | KEEP | Inchangé |
| `api/dto/PointageTronconSemaineDTO.java` | Contrat public hebdomadaire | Synthèse, export | KEEP | Inchangé |
| `api/dto/SaisiePointageJourDTO.java` | Contrat public de saisie complexe | Grille, batch | KEEP | Inchangé |
| `api/dto/ToleranceRequest.java` | Requête d'un seul contrôleur | Paramétrage | INLINE | `PointageController` |
| `application/PointageAnomalieService.java` | Recherche et orchestration des régularisations, Congés | Contrôleur, service de saisie | KEEP provisoire | Service dédié : 914 lignes, transaction inter-domaines |
| `application/PointageEmployeService.java` | Lecture fiche et statistiques employé | Contrôleur, export | KEEP provisoire | Lecture de 929 lignes distincte des writes |
| `application/PointageExcelExportService.java` | Mise en forme Excel non triviale | Contrôleur | KEEP | Export isolé du métier |
| `application/PointageSaisieService.java` | Grille, batch et propriétaire des writes jour/indemnités | Contrôleurs, anomalies | MERGE/RENAME | `PointageService` |
| `application/PointageSyntheseService.java` | Semaines en mémoire, KPI, synthèse et gel | Contrôleur, Paie, export | KEEP | Propriétaire synthèse/gel |
| `domain/calculation/AttendanceTemporalPartitioner.java` | Découpage temporel du pipeline | Synthèse | MERGE | `TemporalAttendanceEngine` |
| `domain/calculation/DailyAttendanceCalculator.java` | Calcul pur journalier | Services, tests | MERGE | `AttendanceEngine` |
| `domain/calculation/DaySpecialType.java` | Type consommé aussi par Paie | Pointage, Paie | KEEP | Contrat inter-domaines |
| `domain/calculation/NightWorkType.java` | Type consommé aussi par Paie | Pointage, Paie | KEEP | Contrat inter-domaines |
| `domain/calculation/OvertimeProjectionEngine.java` | Projection du pipeline temporel | Synthèse | MERGE | `TemporalAttendanceEngine` |
| `domain/calculation/OvertimeTier.java` | Type consommé aussi par Paie | Pointage, Paie | KEEP | Contrat inter-domaines |
| `domain/calculation/PeriodWeekSegmenter.java` | Découpe semaine/période | Synthèse, tests | MERGE | `AttendanceEngine` |
| `domain/calculation/QualifiedTimeBucket.java` | Contrat de valorisation consommé par Paie | Pointage, Paie | KEEP | Contrat inter-domaines |
| `domain/calculation/TemporalBucket.java` | Résultat interne temporel | Synthèse, projection | INLINE | `TemporalAttendanceEngine` |
| `domain/calculation/WeeklyAttendanceCalculator.java` | Calcul pur semaine | Synthèse, tests | MERGE | `AttendanceEngine` |
| `domain/calculation/WorkInterval.java` | Intervalle interne | Synthèse, partition | INLINE | `TemporalAttendanceEngine` |
| `domain/calculation/WorkIntervalFactory.java` | Construction d'intervalles interne | Synthèse | MERGE | `TemporalAttendanceEngine` |
| `domain/engine/CalendrierOuvrableEngine.java` | Calendrier site, ouvrable et férié | Plusieurs services | KEEP | Moteur autonome |
| `persistence/EmployeIndemniteDerogation.java` | Dérogations, modèle aussi référencé hors Pointage | Paramétrage, Paie | KEEP | Contrat de persistance partagé |
| `persistence/RefToleranceArriveePointage.java` | Persistance paramétrage tolérances | Paramétrage | KEEP provisoire | Cycle de vie BDD autonome |

## Garde-fous de consolidation

- Les routes et signatures JSON restent inchangées. Le frontend n'est pas modifié.
- `pointage_jour` et `pointage_indemnite_jour` restent écrits par le service de saisie renommé ; aucune nouvelle couche Repository.
- La semaine reste calculée en mémoire. Aucun retour de `pointage_semaine_calcule`.
- Les snapshots période/jour/bucket et la délégation à `CongeService` restent intacts.
- L'éligibilité HS vient de l'affectation carrière datée et de `ref_categorie_professionnelle.est_eligible_hs`; la valeur du référentiel n'étant pas historisée, `HISTORICAL_CONFIGURATION_RISK` demeure.
- Les fallbacks existants ne sont ni supprimés ni modifiés sans preuve métier ; leurs emplacements seront signalés dans le rapport final.
