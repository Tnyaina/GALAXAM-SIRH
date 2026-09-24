# Domaine Pointage

Ce dossier regroupe toute la chaîne de gestion du temps et des présences : saisie quotidienne, calculs hebdomadaires (heures supplémentaires, nuit, dimanche, jours fériés), détection d'anomalies, clôture de période et export Excel.

- `api/` : deux contrôleurs REST (`PointageController` pour la saisie, les anomalies, les fiches, la synthèse et le paramétrage ; `PointagePeriodeController` pour la période)
- `api/dto/` : contrats DTO pour la saisie, les fiches individuelles et les synthèses
- `application/` : `PointageService` possède les écritures journalières, `PointageSyntheseService` la synthèse et le gel ; les services d'anomalie, fiche et export restent distincts en raison de leur volume ou de leur transaction inter-domaines
- `domain/` : `AttendanceEngine` calcule jour/semaine/tronçons, `TemporalAttendanceEngine` construit et qualifie la chronologie, `CalendrierOuvrableEngine` résout le calendrier ; aucun JDBC dans ces moteurs
- `persistence/` : entités de tolérance et dérogations d'indemnité (`RefToleranceArriveePointage`, `EmployeIndemniteDerogation`)

Les routes publiques restent sous `/pointages/*` et `/periodes/*`.
