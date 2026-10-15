# Domaine Pointage

Ce dossier regroupe toute la chaîne de gestion du temps et des présences : saisie quotidienne, calculs hebdomadaires (heures supplémentaires, nuit, dimanche, jours fériés), détection d'anomalies, clôture de période et export Excel.

- `api/` : deux contrôleurs REST (`PointageController` pour la saisie, les anomalies, les fiches, la synthèse et le paramétrage ; `PointagePeriodeController` pour la période)
- `api/dto/` : contrats DTO pour la saisie, les fiches individuelles et les synthèses
- `application/` : `PointageService` possède les écritures journalières, `PointageSyntheseService` la synthèse et le gel ; les services d'anomalie, fiche et export restent distincts en raison de leur volume ou de leur transaction inter-domaines
- `domain/` : `AttendanceEngine` calcule jour/semaine/tronçons, `TemporalAttendanceEngine` construit les intervalles et qualifie les minutes réelles, `QualifiedTimeBucket` regroupe les trois qualifications persistées (HS, jour spécial, nuit), `CalendrierOuvrableEngine` résout le calendrier avec ses lectures JDBC datées
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

Le précontrôle de gel lit la dispense et le site de chaque journée via le même
contexte daté que la synthèse. Une date de paie appartient à une seule période :
la base interdit les chevauchements de campagnes, avec bornes inclusives. La
compensation reste hebdomadaire selon l'objectif de la semaine ; l'ajustement
motivé du solde final est distinct d'une correction de badge. Les snapshots
déjà consommés par la Paie restent historiques.

Les paliers HS à 30 % et 50 % sont attribués dans chaque tronçon hebdomadaire.
Le solde de période additionne ces tranches, retire les déficits et applique les
ajustements validés sans nouveau palier global. Un retrait consomme les HS à
30 % avant celles à 50 % ; un ajout non typé alimente les HS à 30 %.
Voir `docs/pointage-source-de-verite.md` pour les exemples chiffrés.

Si une affectation change l'éligibilité aux heures supplémentaires au milieu
d'un tronçon hebdomadaire de paie, le précontrôle signale l'employé, les bornes
du tronçon et la date de changement. La transmission et le gel sont bloqués
jusqu'à décision explicite de la DAF sur la date d'effet. Une nouvelle
affectation qui conserve la même éligibilité ne déclenche pas ce blocage.

La nuit occasionnelle est comptée sur les intervalles travaillés, avec les
bornes applicables à la date civile de chaque segment, y compris à l'aperçu et lors du gel. Une vacation
du dernier jour qui dépasse minuit est signalée au précontrôle : les minutes
du lendemain doivent être saisies sur la campagne suivante avant validation.
