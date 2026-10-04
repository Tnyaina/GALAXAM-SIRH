# Domaine Pointage

Ce dossier regroupe toute la chaîne de gestion du temps et des présences : saisie quotidienne, calculs hebdomadaires (heures supplémentaires, nuit, dimanche, jours fériés), détection d'anomalies, clôture de période et export Excel.

- `api/` : deux contrôleurs REST (`PointageController` pour la saisie, les anomalies, les fiches, la synthèse et le paramétrage ; `PointagePeriodeController` pour la période)
- `api/dto/` : contrats DTO pour la saisie, les fiches individuelles et les synthèses
- `application/` : `PointageService` possède les écritures journalières, `PointageSyntheseService` la synthèse et le gel ; les services d'anomalie, fiche et export restent distincts en raison de leur volume ou de leur transaction inter-domaines
- `domain/` : `AttendanceEngine` calcule jour/semaine/tronçons, `TemporalAttendanceEngine` construit et qualifie la chronologie, `CalendrierOuvrableEngine` résout le calendrier avec ses lectures JDBC datées
- `persistence/` : entités de persistance et dérogations d'indemnité (`EmployeIndemniteDerogation`, configuration plancher d'arrivée intégrée dans `ref_site`)

Les routes publiques restent sous `/pointages/*` et `/periodes/*`.

La validation d'une saisie vérifie contrat et site via `EmploymentContextResolver`
au jour du pointage. L'en-tête de la fiche prend le contexte de sa date de fin
et n'invente plus un site de remplacement. La fiche lit le site et la dispense de chaque jour ; les
journées autonomes et la synthèse lisent le site et la dispense de la période
en une seule requête groupée par salarié, avec détection des chevauchements. Les
tarifs et jours chômés continuent d'être résolus par le domaine Pointage aux
dates réelles. Les requêtes de synthèse en masse restent SQL pour éviter une
résolution complète salarié par salarié et jour par jour.
