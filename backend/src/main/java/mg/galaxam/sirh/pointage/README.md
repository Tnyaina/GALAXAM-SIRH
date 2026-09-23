# Domaine Pointage

Ce dossier regroupe toute la chaîne de gestion du temps et des présences : saisie quotidienne, calculs hebdomadaires (heures supplémentaires, nuit, dimanche, jours fériés), détection d'anomalies, clôture de période et export Excel.

- `api/` : contrôleurs REST (`PointageSaisieController`, `PointageSyntheseController`, `PointageAnomalieController`, `PointageEmployeController`, `PointageParametreController`, `PeriodePaieController`)
- `api/dto/` : contrats DTO pour la saisie, les fiches individuelles et les synthèses
- `application/` : services applicatifs (`PointageSaisieService`, `PointageSyntheseService`, `PointageAnomalieService`, `PointageEmployeService`, `PointageExcelExportService`)
- `domain/` : pipeline pur de calcul de temps sans accès direct JDBC (`calculation/`, `engine/`, `mapper/`)
- `persistence/` : entités de tolérance et dérogations d'indemnité (`RefToleranceArriveePointage`, `EmployeIndemniteDerogation`)

Les routes publiques restent sous `/pointages/*` et `/periodes/*`.
