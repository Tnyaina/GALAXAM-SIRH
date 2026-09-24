# Pointage — rapport phase 3 backend + BDD

Date : 2026-09-24. Frontend applicatif non modifié pendant cette phase. L'analyse préalable, créée avant les changements de phase 3, est dans [POINTAGE_PHASE3_BACKEND_ANALYSIS.md](POINTAGE_PHASE3_BACKEND_ANALYSIS.md).

## Résultat

La semaine n'est plus persistée : tous les lecteurs et writers runtime de `pointage_semaine_calcule` ont été retirés, les résultats HS/compensation sont recalculés depuis `pointage_jour`, et les buckets sont construits au gel à partir des mêmes tronçons calculés en mémoire. Une migration forward supprime la table. Le schéma canonique courant ne la recrée plus.

Pointage utilise maintenant `ref_categorie_professionnelle.est_eligible_hs`, résolu à la date de début de chaque segment via l'affectation carrière datée. Le moteur hebdomadaire reçoit ce booléen et ne déduit plus l'éligibilité de `CADRE`. Si aucune affectation/catégorie applicable n'est trouvée, le calcul échoue avec une erreur explicite.

## Flux avant / après

Avant :

```text
pointage_jour → calculateDay / calculateWeek
  → synthèse hebdomadaire → INSERT/UPSERT pointage_semaine_calcule
  → SELECT cache pour KPIs, filtres, détails et buckets du gel
  → pointage_cumul_periode + snapshot_jour + snapshot_bucket → Paie
```

Après :

```text
pointage_jour → calcul jour et calcul semaine en mémoire
  → synthèse/filtres/détails en mémoire
  → pointage_cumul_periode + snapshot_jour + snapshot_bucket → Paie
```

Le workflow de période n'appelle plus le recalcul persistant avant transmission. Les corrections n'invalident plus de cache hebdomadaire. L'endpoint historique « calculer toutes les semaines » reste compatible mais ne persiste rien; il annonce que les segments sont calculés à la demande. La liste des tronçons reste une vue de consultation, sans statut de validation hebdomadaire.

## Migration des consommateurs

| Usage antérieur | Nouvelle source |
|---|---|
| Sommes HS30/HS50 des KPIs | `getSynthesePeriode` → résultats de `WeeklyAttendanceCalculator` |
| Alertes HS / compensé | ensemble d'employés construit depuis les tronçons de synthèse calculés |
| Filtres HS / COMPENSE | mêmes ensembles mémoire injectés dans la requête de pagination |
| Détail salarié / semaine | calcul à la demande déjà produit par `calculerSyntheseEmploye` |
| Buckets de gel | `PointageTronconSemaineDTO` du calcul mémoire de la synthèse, pas une lecture SQL semaine |
| Approbation / correction | aucune écriture ni invalidation hebdomadaire; recalcul à la prochaine lecture ou au gel |

La pagination synthèse calcule désormais la synthèse de la période complète avant d'appliquer les ensembles HS/compensé. C'est nécessaire pour filtrer sans table semaine; le coût mémoire/temps est une dette de performance à mesurer sur des périodes de production volumineuses.

## HS et historique de l'éligibilité

| Concept | Table / colonne | Historique | Usage après phase 3 |
|---|---|---|---|
| Affectation d'un employé à une catégorie | `employe_affectation_carriere.id_categorie_professionnelle`, `date_debut`, `date_fin` | effective-dated | résolution à la date du début de chaque tronçon |
| Éligibilité HS | `ref_categorie_professionnelle.est_eligible_hs` | référentiel mutable, pas d'historique propre | entrée de `WeeklyAttendanceCalculator.Context.overtimeEligible` |
| Cadre | `ref_categorie_professionnelle.est_cadre` | association datée par affectation, définition référentiel modifiable | présentation seulement; ne pilote plus les HS |
| Contrat | `contrat_travail` + dates | effectif-daté; aucune colonne d'éligibilité HS identifiée | pas la source retenue pour cette règle |

Tests calculateur : éligible avec surplus → HS; non éligible avec surplus → aucune HS; non éligible avec déficit → compensation conservée; bascule du booléen entre deux périodes → résultats séparés; segments de période coupés testés. Il n'existe pas de fixture d'intégration employé dont l'affectation change dans une vraie base historique. Une modification rétroactive de `ref_categorie_professionnelle.est_eligible_hs` changerait donc un replay : `HISTORICAL_CONFIGURATION_RISK`. La règle choisit l'éligibilité au début du segment; une modification de catégorie en cours de semaine exige une règle métier explicite.

## Structure et mesures

| Mesure | Avant phase 3 | Après phase 3 | Note |
|---|---:|---:|---|
| Fichiers Java sous `pointage/` | 36 | 35 | le delta de fichiers résulte principalement des phases antérieures; cette phase retire surtout SQL/cache |
| Services applicatifs Pointage | 5 | 5 | `Saisie`, `Anomalie`, `Employe`, `Synthese`, export Excel |
| Controllers Pointage/Période | 6 | 6 | contrats et périmètres HTTP séparés conservés |
| Fichiers calcul (`domain/calculation`) | 12 | 12 | 2 calculateurs centraux + types temporels partagés avec Paie |
| DTO dans `api/dto` | 9 | 9 | les DTO existants restent contrats API ou données inter-domaines |
| Tables créées par le schéma Pointage courant | 14 | 13 | `pointage_semaine_calcule` supprimée |

La cible idéale de deux services n'est pas atteinte. Fusionner maintenant correction, lecture salarié, export et synthèse créerait des classes encore plus vastes : les deux services de lecture et les contrôleurs spécialisés restent séparés faute de preuves/tests suffisants pour une fusion sûre. Le domaine conserve 12 types de calcul; plusieurs (`QualifiedTimeBucket`, `DaySpecialType`, `NightWorkType`, `OvertimeTier`) sont des contrats consommés par Paie, et les autres portent des frontières temporelles distinctes. Ce n'est pas une réduction maximale de fichiers : c'est une réduction concentrée sur la persistance hebdomadaire sans transformer `AttendanceEngine` en monolithe.

## Writes Pointage, Congés, gel et Paie

- `PointageSaisieService` reste l'unique classe runtime qui écrit `pointage_jour` et `pointage_indemnite_jour` dans le code Java Pointage. `PointageAnomalieService` délègue les corrections à ce propriétaire.
- Les chemins batch et régularisation ont toujours des instructions SQL distinctes à l'intérieur de ce service, car le batch porte des indemnités dynamiques/versionnement différents. La propriété est unique, mais l'objectif « une seule instruction SQL canonique » n'est pas entièrement réalisé.
- `CongeService` reste propriétaire des writes Congés; la transaction de régularisation préexistante n'a pas été redéfinie.
- `pointage_cumul_periode` reste le contrat Paie; `pointage_snapshot_jour` garde id/version/date/minutes/statut; `pointage_snapshot_bucket` est conservé et continue de porter la qualification temporelle nécessaire à `OvertimePayCalculator`.

## Fallbacks et règles à confirmer

Occurrences conservées dans le backend/schéma : paramètres de 40h et 8h par défaut dans `PointageSyntheseService` et `PointageEmployeService`; horaires 08:00/12:00/13:00/17:00 et plafond 07:30 dans `PointageSaisieService`; exemples horaires dans les commentaires DTO; seuil HS première tranche à 8h si la configuration légale n'est pas disponible. Le schéma comporte des horaires/plafonds site et la configuration légale/paramètres sont les sources à privilégier, mais certains chemins ont encore un fallback applicatif. Classements : `BUSINESS_RULE_SUSPECT` et `CONFIGURATION_MISSING`; conservés pour éviter de changer silencieusement le comportement historique.

## Tables après

- Sources et opérations : `pointage_jour`, `pointage_indemnite_jour`.
- Référentiels/configuration et périodes : statuts/jours, période de paie, indemnités, configuration site, tolérance, dérogations.
- Contrat de gel Paie : `pointage_cumul_periode`, `pointage_snapshot_jour`, `pointage_snapshot_bucket`.
- `pointage_semaine_calcule` : absente du schéma courant et supprimée par `26-09-24-sp23-pointage-drop-weekly-cache.sql`.

Les anciennes migrations archivées conservent leur historique; elles n'ont pas été réécrites. La migration forward n'a pas été appliquée à une base partagée pendant cette tâche.

## Tests et limites de preuve

- `mvn clean test -q` — réussi (52 tests, dont 11 tests de calcul hebdomadaire).
- `npm test -- --run` — réussi (1 fichier, 4 tests).
- `npm run lint` — réussi.
- `npm run build` — réussi; aucun fichier frontend applicatif n'a été modifié pendant la phase 3.
- `git diff --check` — réussi.
- Recherche runtime : aucun lecteur/writer `pointage_semaine_calcule` sous `backend/src/main/java` ou `backend/src/test/java`.

La parité démontrée est celle du moteur et des scénarios unitaires; aucun replay avant/après sur un échantillon de périodes de production ni comparaison monétaire Paie complète (HS30/50, nuit, dimanche, férié, collisions) n'a été possible dans cette tâche. La projection du gel réutilise les tronçons du calcul courant, mais la migration doit être vérifiée sur une copie de base avant déploiement. Les tests de demi-journée/férié travaillé et de changement d'éligibilité réel entre deux affectations restent à compléter.

## Dettes restantes

1. Mesurer la nouvelle synthèse complète avant pagination; si nécessaire, calculer chaque employé une seule fois dans un objet période mémoire partagé au sein de la requête (sans table/cache BDD).
2. Unifier le SQL batch/régularisation de `pointage_jour` sans perdre les indemnités dynamiques, l'optimistic locking ou l'idempotence.
3. Décider si `ref_categorie_professionnelle.est_eligible_hs` doit être historisé/versionné lorsque la définition d'une catégorie change.
4. Ajouter un replay représentatif de gel et Paie avant tout déploiement de la migration destructive.
5. Résoudre les fallbacks et les comportements fériés/horaires manquants après décision métier, pas par refactor implicite.
