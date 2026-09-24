# Pointage — analyse backend/BDD phase 3 (avant modifications)

Date : 2026-09-24. État observé : arbre de travail déjà modifié par les phases précédentes; ce document décrit le comportement avant les changements propres à la phase 3. Aucun code applicatif ni migration de phase 3 n'a été modifié avant la création de cette analyse.

## Flux backend actuel

| Étape | Classe / méthode | Tables lues | Tables écrites | Source métier / rôle |
|---|---|---|---|---|
| Charger la grille quotidienne | `PointageSaisieService.getSaisieQuotidienne` | période, employés/affectations, site, calendrier, congés, `pointage_jour`, `pointage_indemnite_jour` et référentiels | — | prépare la saisie du jour et les valeurs préremplies |
| Créer/modifier journée | `PointageSaisieService.enregistrerBatch` et `saveRegularizedDay` | horaires/config, période, version/journée, indemnités | `pointage_jour`, `pointage_indemnite_jour` | propriétaire courant des writes journée; calculate `DailyAttendanceCalculator` |
| Corriger anomalie | `PointageAnomalieService.regulariserPointage` | journée/anomalie, période, employé, Congés/config | journée + indemnités via `PointageSaisieService`; Congé via `CongeService` | orchestration; transaction JDBC partagée; recalcul hebdo conditionnel historique |
| Calcul jour | `DailyAttendanceCalculator.calculateDay` | aucune (moteur pur) | — | heures/retard/déficit/statut et indemnités calculés à partir du contexte fourni |
| Calcul hebdo | `PointageSyntheseService.calculerSyntheseEmploye` → `WeeklyAttendanceCalculator.calculateWeek` | jours, calendrier ouvrable/fériés, congés et paramètres | résultat dans DTO puis actuellement `pointage_semaine_calcule` | surplus quotidien compensé d'abord par les déficits; HS30/50 sur reste éligible |
| Lire les semaines opérationnelles | `getSemainesPeriode`, `getSyntheseSemaineDetail`, synthèse paginée | journées + affectation/calendrier; plusieurs lectures du cache `pointage_semaine_calcule` | — | grille DAF, détails, compteurs, filtres HS/compensé |
| Consolider période | `getSynthesePeriodePaginee` / `calculerSyntheseEmploye` | jours, indemnités, employés/affectations, paramètres | — | synthèse période et KPIs; certaines HS viennent encore des lignes hebdo persistées |
| Préparer / geler | `PayrollPeriodWorkflowService` → `calculerToutesSemainesPeriode`, puis orchestration synthèse/gèle | période, journées, configuration légale, semaine persistée (par recalcul) | `pointage_semaine_calcule`, `pointage_cumul_periode`, `pointage_snapshot_jour`, `pointage_snapshot_bucket` | approbation produit cumul versionné, preuves par jour et buckets Paie |
| Consommer Paie | `PayrollService` et `OvertimePayCalculator` | cumul gelé + snapshots jour/bucket + taux/config légale | tables Paie/consommation selon workflow | ne lit pas `pointage_semaine_calcule`; valorise les qualifications figées |
| Régularisation Congés | `PointageAnomalieService` → `CongeService` (connexion partagée) | règles/paramètres requis par le chemin historique | demande Congé et ledger via CongeService; journée via PointageSaisieService | propriété SQL Congés déjà transférée; le flux spécial ne prouve pas l'exécution du moteur normal de demande |

## Persistance hebdomadaire et lecteurs runtime

`pointage_semaine_calcule` est encore écrite par `PointageSyntheseService.upsertSemaineCalculee`; l'écriture est précédée d'un recalcul synthèse/détail. Ses lecteurs identifiés sont :

- compteurs d'employés compensés et avec HS dans les alertes des KPIs paginés;
- sommes HS30/HS50 des KPIs;
- filtres `HS` et `COMPENSE`;
- `getSyntheseEmploye`, qui recharge les lignes par semaine pour construire les détails et ajuster les cumuls;
- indicateur de présence d'un cache pour annoncer une semaine calculée/validée dans `getSemainesPeriode`;
- `recalculerSemainePourDate`, appelé après correction afin de ne recalculer que les lignes déjà matérialisées;
- `calculerToutesSemainesPeriode`, appelé notamment dans le workflow d'approbation avant gel.

Paie n'en lit pas directement. Cette table est un cache métier-projection qui contamine toutefois les lectures et la sémantique UI (le code ancien la présente parfois comme « validée »). Suppression sûre : remplacer ces usages par une projection calculée une fois en mémoire par employé/période, partager ses résultats pour les pages/filtres/gels, puis supprimer vérification/recalcul/upsert et créer une migration forward. Le schéma source `05_schema_pointage.sql` est initialiseur courant et contient un `DROP TABLE IF EXISTS` historique en tête; il ne remplace pas une migration pour bases déjà installées.

## HS et éligibilité cadre/non-cadre

| Concept | Table | Colonne | Effective-dated ? | Utilisé par |
|---|---|---|---|---|
| Catégorie professionnelle assignée | `employe_affectation_carriere` | `id_categorie_professionnelle`, avec `date_debut`, `date_fin` | Oui : l'affectation est datée | requêtes de synthèse par date; jointure vers catégorie |
| Éligibilité HS | `ref_categorie_professionnelle` | `est_eligible_hs` | Non seule, mais résolue via l'affectation effective | Paie charge `categorie_est_eligible_hs`; le calcul hebdo Pointage ne l'utilise pas actuellement |
| Classification cadre | même table | `est_cadre`, plus `code` | valeur référentiel courante (pas d'historique propre) | présentation et décisions de paie; Pointage utilise actuellement un heuristique sur `code` |
| Contrat | `contrat_travail` | dates de validité, pas de colonne d'éligibilité trouvée | Oui pour contrat, non pour éligibilité | contexte contrat/paie, pas source directe trouvée pour le booléen du moteur |

Constat critique : `PointageSyntheseService.isCadreCategorie(code)` infère cadre par texte (`CADRE`, `C`, `HC`, `DIRIGEANT`), puis appelle `WeeklyAttendanceCalculator` avec `!isCadre`. Il ignore le booléen de référentiel `est_eligible_hs`. Il existe donc déjà une divergence possible entre Pointage et `PayrollService`, qui lit `cat.est_eligible_hs`. L'affectation carrière datée fournit le mécanisme historique de résolution, mais `ref_categorie_professionnelle.est_eligible_hs` est mutable; si la définition de la catégorie est éditée, la valeur passée n'est pas historiquement conservée. Classement : `HISTORICAL_CONFIGURATION_RISK` pour changements de définition du référentiel; historique d'affectation seul suffit seulement si l'éligibilité de chaque catégorie demeure immuable.

La cible mécanique est de résoudre `a.id_categorie_professionnelle → cp.est_eligible_hs` pour l'affectation active sur la date/segment, puis fournir `overtimeEligible` au calculateur pur. Ne pas faire déduire CADRE au moteur. Configuration/affectation manquante doit produire le comportement historique observé ou une erreur explicite documentée; aucune nouvelle valeur par défaut ne doit être introduite silencieusement.

## Workflow de période, snapshot et Paie

Le gel garde `pointage_cumul_periode` (contrat consommé par Paie), `pointage_snapshot_jour` (id/version/date/minutes/statut pour provenance), et `pointage_snapshot_bucket` (intervalles qualifiés permettant taux datés, majorations et collisions). La table semaine n'est pas nécessaire à la lecture Paie, mais ses résultats doivent être produits à nouveau par le même calcul mémoire au moment de la synthèse et du gel afin que le cumul et les buckets restent identiques.

Les buckets ne sont pas dans le périmètre de suppression. Aucun replay historique/monétaire complet multi-cas n'est actuellement établi dans les tests Pointage, donc toute réduction de leur contrat est hors de cette phase.

## Préconditions de suppression de table

Avant migration : aucun `SELECT`, `INSERT`, `UPDATE`, DTO/colonne ou endpoint/runtime ne doit dépendre de `pointage_semaine_calcule`; parité de calcul période/semaine contrôlée pour segments début/fin/inter-périodes, congé/demi-journée, férié chômé/travaillé, surplus/déficit, cadre/éligible; approbation doit produire les mêmes cumul/jour/buckets; compilation/tests complets réussis. Cette analyse identifie les lecteurs, elle ne constitue pas encore la preuve de parité ni l'autorisation de DROP.
