# Architecture Pointage

## Flux cible et propriétaires

Le pointage brut ou corrigé est résolu avec le contexte applicable à la date (affectation, site, horaire, congé, férié et paramètres), puis confié à `DailyAttendanceCalculator`. Le domaine travaille exclusivement en minutes entières et retourne un `DailyAttendanceResult` immutable. La conversion en heures décimales n'est autorisée qu'à la frontière de la base historique ou d'un export.

```text
badges/correction + contexte daté
  → DailyAttendanceCalculator
  → DailyAttendanceResult
  → WeeklyAttendanceCalculator
  → WeeklyAttendanceResult
  → AttendancePeriodCalculator
  → AttendancePeriodResult
  → snapshot Pointage
```

Le calcul journalier possède les durées, le retard, la nuit, le statut et les anomalies de cohérence. Le calcul hebdomadaire possède le seuil et la ventilation des heures supplémentaires. Le calcul période additionne uniquement des résultats hebdomadaires : il ne relit pas les badges.

## Temporalité et blocage

Les services d'orchestration chargent une configuration couvrant la date calculée. Zéro configuration applicable ou plusieurs configurations concurrentes produisent une erreur bloquante; aucun « dernier paramètre connu » n'est utilisé. Les algorithmes restent en Java et les valeurs évolutives, avec leurs intervalles d'application, appartiennent à la base.

## Snapshot et provenance

`pointage_cumul_periode.id_cumul` est l'identifiant technique actuellement consommé comme snapshot. `gele_at`, l'employé et la période constituent sa provenance minimale. La Paie reçoit ces informations dans `PayrollAttendanceInput`; elle ne lit pas les badges. La version explicite du snapshot est portée par le contrat et doit être matérialisée en base avant d'autoriser une réouverture/version ultérieure.

Une correction après consommation par une Paie validée ne doit jamais écraser la version consommée : elle requiert réouverture explicite, nouvelle version ou régularisation.

## Arrondis

Les calculs de durée ne sont pas arrondis : ils produisent des minutes. `PayrollAttendanceInput.minutesToHours` est la conversion de compatibilité vers les champs Paie historiques, avec deux décimales et `HALF_UP`. Les montants utilisent `BigDecimal`; l'affichage ne définit aucune règle métier.

## État de migration

La saisie quotidienne délègue désormais ses durées, retard, nuit et statut au calculateur journalier. `PointageAnomalieService`, `PointageSyntheseService` et certaines valorisations d'indemnités utilisent encore des chemins historiques; ils sont donc explicitement classés comme dette active dans `POINTAGE_MIGRATION_MAP_2026-09-13.md` et empêchent encore de déclarer A04/A06 corrigées.
