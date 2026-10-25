# Domaine Pointage

Ce dossier regroupe toute la chaîne de gestion du temps et des présences : saisie quotidienne, calculs hebdomadaires (heures supplémentaires, nuit, dimanche, jours fériés), détection d'anomalies, clôture de période et export Excel.

- `api/` : deux contrôleurs REST (`PointageController` pour la saisie, les anomalies, les fiches, la synthèse et le paramétrage ; `PointagePeriodeController` pour la période)
- `api/dto/` : contrats DTO pour la saisie, les fiches individuelles et les synthèses
- `application/` : `PointageService` possède les écritures journalières, `PointageSyntheseService` la synthèse et le gel ; les services d'anomalie, fiche et export restent distincts en raison de leur volume ou de leur transaction inter-domaines
- `domain/` : `AttendanceEngine` calcule jour/semaine/tronçons, `TemporalAttendanceEngine` construit les intervalles et qualifie les minutes réelles, `QualifiedTimeBucket` regroupe les trois qualifications persistées (HS, jour spécial, nuit), `CalendrierOuvrableEngine` résout le calendrier avec ses lectures JDBC datées
- `persistence/` : entités de persistance et dérogations d'indemnité (`EmployeIndemniteDerogation`, configuration plancher d'arrivée intégrée dans `ref_site`)

Les contrôleurs REST du domaine sont montés sous `/pointages/*` et `/periodes/*` ; ces routes ne sont pas publiques au sens d'un accès anonyme. La configuration Spring Security exige une authentification et les méthodes appliquent des autorités avec `@PreAuthorize`. Les règles métier des commandes de période ajoutent un contrôle d'autorisation dans `PayrollPeriodWorkflowService`.

Repères d'accès vérifiés dans `PointageController`, `PointagePeriodeController` et `PayrollPeriodWorkflowService` :

- **Consultation** : la plupart des lectures Pointage exigent `POINTAGE_READ` (certaines lectures de contrôle acceptent aussi `POINTAGE_VALIDATE` ou `ROLE_ADMIN`). La consultation des périodes accepte `POINTAGE_READ`, `PAIE_READ` ou `ROLE_ADMIN`.
- **Saisie** : l'enregistrement des pointages et l'aperçu d'import exigent `POINTAGE_WRITE` ou `ROLE_ADMIN`. Les aperçus de saisie acceptent aussi `POINTAGE_READ`.
- **Régularisation** : `PUT /pointages/anomalies/{idPointage}/regulariser` accepte `POINTAGE_WRITE`, `POINTAGE_VALIDATE` ou `ROLE_ADMIN`. Les options de congé appellent ensuite les services Congés et appliquent leurs contrôles métier dans la transaction de régularisation.
- **Validation et périodes** : `APPROVE` et `REJECT` exigent `POINTAGE_VALIDATE` (ou l'administrateur via la hiérarchie de rôles) ; `SUBMIT` accepte `POINTAGE_WRITE` ou `POINTAGE_VALIDATE`. `OPEN` exige effectivement `PAIE_WRITE`, `PAIE_VALIDATE` ou `POINTAGE_VALIDATE` dans le service métier : `POINTAGE_WRITE` seul ne suffit pas. `CLOSE` exige `PAIE_VALIDATE` et `REOPEN` `ROLE_ADMIN`.
- **Paramètres** : la lecture de `/pointages/parametres/tolerances` exige une session authentifiée ; l'écriture de ce plancher et l'administration des dérogations requièrent `ROLE_ADMIN`.

Ces autorités décrivent les vérifications backend observées. La visibilité d'un écran React ne remplace pas ces contrôles.

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
Voir `backend/docs/boky/module-pointage-presence.md` pour les règles détaillées et exemples chiffrés.

Si une affectation change l'éligibilité aux heures supplémentaires au milieu
d'un tronçon hebdomadaire de paie, le précontrôle signale l'employé, les bornes
du tronçon et la date de changement. La transmission et le gel sont bloqués
jusqu'à décision explicite de la DAF sur la date d'effet. Une nouvelle
affectation qui conserve la même éligibilité ne déclenche pas ce blocage.

La nuit occasionnelle est comptée sur les intervalles travaillés, avec les
bornes applicables à la date civile de chaque segment, y compris à l'aperçu et lors du gel. Une vacation
du dernier jour qui dépasse minuit est signalée au précontrôle : les minutes
du lendemain doivent être saisies sur la campagne suivante avant validation.
