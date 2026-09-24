# Pointage & Présence — Analyse phase 2 (avant refactor)

Date d'analyse : 2026-09-24. Cette analyse décrit l'arbre de travail après la première passe déjà présente. Aucun code applicatif n'a été modifié pour cette phase avant la création de ce document.

## 1. Workflow actuellement exécuté

```text
GestionPointagesPage (?periode=…&tab=saisie|suivi|arbitrage)
    ├── SaisieQuotidiennePage → GET /pointages/saisie, POST /pointages/saisie/batch
    │       └── PointageSaisieService → DailyAttendanceCalculator → pointage_jour + pointage_indemnite_jour
    ├── SuiviAnomaliesPage → GET /pointages/anomalies, PUT /pointages/anomalies/{id}/regulariser
    │       └── PointageAnomalieService → DailyAttendanceCalculator / CongeService → writes jour + indemnités
    └── ArbitrageDafPage → synthèse / semaines / détail, actions de période
            └── PointageSyntheseService → Daily + WeeklyAttendanceCalculator
                    → pointage_semaine_calcule (matérialisation recalculable)
                    → PayrollPeriodWorkflowService (SUBMIT / APPROVE / REJECT)
                    → gel versionné : pointage_cumul_periode + snapshot_jour + snapshot_bucket
                            └── PayrollService → pointage_cumul_periode + pointage_snapshot_bucket
                                    └── OvertimePayCalculator → valorisation des temps qualifiés
```

La transmission/validation est pilotée par le workflow des périodes, pas par un workflow semaine. Les routes de validation semaine avaient déjà été supprimées dans la première passe. Le code conserve néanmoins une matérialisation hebdomadaire pour les lectures et certains contrôles.

## 2. Parcours journalier et sources de calcul

| Étape | Frontend | Endpoint | Service | Calculateur | Tables lues | Tables écrites |
|---|---|---|---|---|---|---|
| Charger grille | `SaisieQuotidiennePage`, `usePointageSaisie` | `GET /pointages/saisie` | `PointageSaisieService` | — | période, employés/affectations, sites, congés, fériés, `pointage_jour`, `pointage_indemnite_jour`, configs d'indemnités, tolérance | — |
| Enregistrer lot | même écran | `POST /pointages/saisie/batch` | `PointageSaisieService` | `DailyAttendanceCalculator` | site/horaires, paramètres globaux, calendrier, période, indemnités | `pointage_jour`, `pointage_indemnite_jour` |
| Lister anomalies | `SuiviAnomaliesPage`, `usePointageAnomalies` | `GET /pointages/anomalies` | `PointageAnomalieService` | calcul/lecture d'anomalies | `pointage_jour`, statuts, site, congés, période | — |
| Corriger/régulariser | modale anomalie ou modale DAF | `PUT /pointages/anomalies/{id}/regulariser` | `PointageAnomalieService` | `DailyAttendanceCalculator` pour correction horaire | pointage verrouillé, horaire site, paramètres indemnité, congés | `pointage_jour`, `pointage_indemnite_jour`; via `CongeService`, `demande_conge` et ledger |
| Calculer semaine/synthèse | `ArbitrageDafPage`, `PointageDetailHebdoModal`, Gantt | `GET /pointages/synthese`, `/semaines`, `/semaine-detail`, `POST /calculer-tout` | `PointageSyntheseService` | `DailyAttendanceCalculator`, `WeeklyAttendanceCalculator`, `PeriodWeekSegmenter` | `pointage_jour`, affectations, calendrier/fériés, paramètres, semaine calculée | `pointage_semaine_calcule` pour le recalcul |
| Transmission et décision | Suivi / Arbitrage, période | `/periodes/{id}/actions/{SUBMIT,APPROVE,REJECT}` | `PayrollPeriodWorkflowService` + synthèse au gel | calcul semaine avant gel | périodes/statuts, `pointage_jour`, snapshots existants | transitions de période, snapshots période/jour/bucket |
| Consommation Paie | pages/campagne Paie | services Paie | `PayrollService`, `OvertimePayCalculator` | aucune requalification depuis jours courants | `pointage_cumul_periode`, `pointage_snapshot_bucket` | rattachement/consommation snapshot au bulletin |

## 3. Calcul quotidien : propriétaire et divergences

- Les horaires théoriques viennent des heures de `ref_site` (entrée/sortie matin/après-midi) avec des fallbacks codés dans les services. La continuité vient de `ref_site.horaire_continu`.
- `DailyAttendanceCalculator.calculateDay` calcule durées de segments, total travaillé, objectif journalier à partir de l'horaire planifié, retard, déficit/absence, nuit et statut pur (`DailyAttendanceResult.Status`). `calculateMealAmount` et `calculateTravelAmount` centralisent des formules d'indemnités.
- Le service résout les paramètres et données (site, statut congé, férié, régime autonome, montant indemnités), puis persiste le résultat. C'est la bonne séparation de responsabilité.
- Toutefois `PointageSaisieService` et `PointageAnomalieService` construisent chacun `DailyAttendanceContext` et mappent le statut persistant avec des décisions supplémentaires. L'UI recalcule aussi le statut/heures/retard/anomalie dans `pointageCalculations.js` et envoie `codeStatut`. Dans le batch, un `codeStatut` fourni n'est pas systématiquement remplacé par le calcul serveur. Il existe donc plusieurs chemins décisionnels, et pas seulement un affichage preview.
- `ref_tolerance_arrivee_pointage` est lu dans les requêtes de site, mais l'écrêtage d'arrivée est explicitement désactivé dans `PointageSaisieService`; la table/config n'a actuellement pas d'effet démontré sur le calcul. Classement : `CONFIG_UNUSED`/suppression candidate après preuve et décision sur l'UI admin, pas de suppression métier dans cette passe.
- Des horaires de fallback 08:00/12:00/13:00/17:00 et 07:30 existent. L'objectif jour a un fallback global généralement 8h; certaines requêtes de synthèse utilisent aussi `new BigDecimal("8")`. Les jours autonomes synthétisés injectent directement 08:00–17:00. Ces occurrences sont des `BUSINESS_RULE_SUSPECT` vis-à-vis de la cible « aucun 8h codé en dur »; ne pas les changer sans déterminer le comportement historique et la config manquante.

## 4. Calcul hebdomadaire exact

`PointageSyntheseService.calculerSyntheseEmploye` regroupe les journées par lundi civil, calcule le nombre de jours ouvrables du tronçon via `CalendrierOuvrableEngine`, décompte congés validés (1 jour ou 0,5 jour, hors weekend et férié chômé), puis construit :

```text
joursRequis = max(0, joursOuvrables - joursCongé)
objectifTronçon = seuilHeuresJour × joursRequis
```

Le seuil jour vient de `SEUIL_HEURES_JOUR`, à défaut `dureeHebdoLegale / 5`, avec des fallbacks 8h dans des chemins de synthèse. Les fériés chômés sortent du compte de jours ouvrables via le calendrier. Les congés baissent l'objectif; les demi-journées le baissent de moitié. Les jours fériés travaillés ne sont pas filtrés comme les fériés chômés dans tous les chemins, à vérifier dans replay.

Dans `WeeklyAttendanceCalculator` :

```text
surplusJour = somme(max(0, minutes travaillées - cible jour))
manqueAvantCompensation = max(0, objectif + surplusJour - heures travaillées)
compensé = min(surplusJour, manqueAvantCompensation)
HS brute = surplusJour - compensé
HS éligible = HS brute si overtimeEligible, sinon 0
HS30 = min(HS éligible, seuil première tranche)
HS50 = max(0, HS éligible - HS30)
déficit = manqueAvantCompensation - compensé
```

Le seuil première tranche est résolu depuis `PaieConfigLegale` (8h par défaut dans le code). Les cadres rendent `overtimeEligible=false`. La compensation lundi 10h + mardi 6h avec trois jours à 8h donne 0 HS; la semaine 10h + 4×8h produit 2h brutes avant classement HS30/HS50, sous réserve d'éligibilité et des règles de statut/jour.

**Frontières de période :** `PeriodWeekSegmenter` découpe la période en intersections avec les semaines civiles. Chaque segment est calculé indépendamment; le commentaire métier explicite dit que les périodes de paie sont étanches et qu'aucune compensation déficit/HS ne passe leur frontière. `toPriorState()` n'avait pas d'appelant. `WeeklyPriorState` et les champs cumulatifs de `WeeklyAttendanceResult` étaient donc du support générique non utilisé dans le workflow courant.

### Consommateurs de `pointage_semaine_calcule`

| Lecteur/écriture | Classe | Catégorie | Usage |
|---|---|---|---|
| présence HS compensées et présence HS | `PointageSyntheseService` | UI/synthèse | KPIs et filtres de la grille de synthèse |
| HS30/HS50 par employé | `PointageSyntheseService` | UI/synthèse | Résultats de synthèse et filtres |
| semaines dans un détail salarié | `PointageSyntheseService` | UI | Détail hebdomadaire / fiche salarié |
| existence/calcul du segment | `PointageSyntheseService` | recalcul/cache | vérifie si un recalcul de semaine doit être déclenché |
| insertion / upsert | `PointageSyntheseService` | calcul intermédiaire | stocke le résultat canonique du calculateur |
| nettoyage gel | `PointageSyntheseService` | lifecycle historique | efface `gele_par`, vestige du gel semaine |
| aucune lecture identifiée | `PayrollService`, `OvertimePayCalculator` | Paie | Paie lit les buckets scellés, pas cette table |
| aucun autre lecteur runtime identifié | API/export/debug | — | pas de consommateur hors synthèse détecté par recherche Java |

Conclusion : la table n'est pas une source de vérité Paie et ne porte plus un workflow utilisateur, mais elle alimente encore directement la synthèse et sert de cache/détection de calcul. **On peut la supprimer seulement après migration de ces consommateurs vers des résultats `WeeklyAttendanceCalculator` calculés en lecture et après replay identique.** Aucun DROP de cette table n'est inclus avant ce replay.

## 5. Snapshot et buckets Paie

`pointage_cumul_periode` porte les compteurs, les heures HS/nuit/dimanche/férié/absence, le retard et les quantités/montants d'indemnités. `pointage_snapshot_jour` fige, par version de gel et journée, l'identifiant/version source, date, minutes travaillées/retard/nuit et statut.

À l'exécution de Paie, `PayrollService` charge le dernier cumul `GELE` ou `CONSOMME`, puis reconstruit aussi une liste `QualifiedTimeBucket` depuis `pointage_snapshot_bucket`. `OvertimePayCalculator` consomme chaque segment : minutes, début/fin (taux légal historique à la date), tranche HS (rémunération base + majoration), nature dimanche/férié, qualification habituelle/occasionnelle de nuit et collisions de sujétions. Il résout des taux par date, applique la politique de collision et produit des détails d'explication. Le bucket n'est donc pas qu'un total agrégé.

| Information gelée par bucket | Usage démontré Paie | Cible minimale sans bucket envisageable |
|---|---|---|
| `minutes`, `date_debut`, `date_fin` | valorisation monétaire et taux daté | agrégats période séparés par dimensions de taux/date; perte de détail explicatif à évaluer |
| `overtime_tier` | choisir HS30/HS50 et calculer la base | colonnes minutes par tranche et par qualification, uniquement si taux homogènes |
| `day_special_type` | dimanche/férié + résolution collisions | matrice de minutes de combinaisons spéciales; ne pas additionner si politique MAX |
| `night_work_type` | nuit habituelle vs occasionnelle; peut bloquer `UNCLASSIFIED` | agrégats séparés par qualification de nuit, avec provenance d'arbitrage |
| `id_periode_paie` | rattachement du segment au workflow paie | le cumul porte déjà la période, mais un bucket peut référencer une autre période aux frontières |
| `id_pointage` | traçabilité de détail | snapshot jour/version, à confirmer pour reconstruction exacte |

La table snapshot bucket est lue par Paie; supprimer le type, les DTO et/ou la table avant un replay historique/montétaire et une cible de traçabilité testée n'est pas sûr. `pointage_cumul_periode` seul ne contient pas actuellement la matrice de qualifications nécessaire.

## 6. Tables Pointage : inventaire et décision

| Table | Nature | Décision phase 2 | Justification |
|---|---|---|---|
| `ref_statut_periode_paie` | référentiel de workflow | KEEP | codes consommés par workflow et contrôles |
| `periode_paie` | racine/workflow | KEEP | dates, statut et cycle de vie de la période |
| `periode_paie_transition` | audit | KEEP | historique des transitions/actions de période |
| `ref_statut_pointage_jour` | référentiel | KEEP | codes status et jointures workflow/anomalies |
| `ref_indemnite` | référentiel/config | KEEP | types d'indemnité réels |
| `site_indemnite_config` | configuration | KEEP | tarifs et disponibilité par site |
| `ref_tolerance_arrivee_pointage` | configuration sans effet observé | CONFIG_UNUSED / analyse ultérieure | chargée et administrable, mais écrêtage inactif |
| `employe_indemnite_derogation` | configuration individuelle | KEEP | dérogations de tarifs/éligibilité |
| `pointage_jour` | source | KEEP | donnée quotidienne + provenance de correction/version |
| `pointage_indemnite_jour` | source dérivée/config appliquée | KEEP | plusieurs indemnités/fractions par journée |
| `pointage_semaine_calcule` | cache/calcul intermédiaire | MERGE candidate | lecteurs synthèse actifs; aucune lecture paie directe |
| `pointage_cumul_periode` | snapshot/contrat paie | KEEP ABSOLU | consommé par contrôles et Paie; versionné |
| `pointage_snapshot_jour` | provenance snapshot | KEEP provisoire | permet de relier version gelée aux journées utilisées |
| `pointage_snapshot_bucket` | provenance temporelle valorisée | KEEP jusqu'au replay | consommé par OvertimePayCalculator |

Le schéma courant comporte 14 tables (les deux agrégats non lus avaient déjà été retirés dans la passe 1) et la vue `v_pointage_indemnites`.

### `pointage_jour` : centralisation à faire

Écritures actives : `PointageSaisieService` insère/actualise les journées du batch, synchronise les indemnités dynamiques et met à jour le total dénormalisé; `PointageAnomalieService` insère/actualise après correction et réalise des mises à jour similaires. Les deux écrivent aussi `pointage_indemnite_jour`. Le service de synthèse n'écrit pas les jours, mais lit les colonnes calculées. `PayrollPeriodWorkflowService` lit les jours pour les blocages d'état, sans les modifier.

Cible de phase B : déplacer le bloc SQL journalier/indemnités de correction dans le propriétaire existant des opérations de saisie (`PointageSaisieService`), accessible avec la même `Connection`, plutôt que créer un repository générique. Avant déplacement, comparer les colonnes, triggers, versions, audit, conflits d'unicité et ordre de recalcul des deux chemins.

## 7. Frontend et navigation

| Écran/composant | Persona/objectif | API/état | Diagnostic |
|---|---|---|---|
| `GestionPointagesPage` | Agent et DAF; shell de navigation par étape | query URL `tab` + `periode`; charge période/anomalies; monte une seule étape lazy | shell léger et URL utile, KEEP comme `AttendancePage`/orchestrateur |
| `SaisieQuotidiennePage` | Agent saisit une journée | grille, site/date, congés/fériés, indemnités, save batch; délègue beaucoup à `usePointageSaisie` | vrai parcours distinct, garder écran mais réduire wrapper/hook si sûr |
| `SuiviAnomaliesPage` | Agent résout anomalies/transmet | hook anomalies, filtres, période, confirmation, modale; `availableActions` période | étape avec responsabilité et permission différentes; pas forcément page URL indépendante |
| `ArbitrageDafPage` | DAF contrôle/arbitre/gèle | synthèse, semaines, commandes APPROVE/REJECT/SUBMIT, Gantt, modal d'ajustement | étape de même shell mais workflow DAF spécialisé |
| `AnomalieRegularisationModal` | correction agent/anomalie | heures/statut/indemnités/absence/congé; ~983 lignes | même endpoint anomalie que correction DAF, beaucoup de formulaire/branches |
| `DafAjustementPointageModal` | correction/arbitrage DAF | charge fiche employé et utilise le même endpoint regulariser; ~589 lignes | duplication de champs/chargement et soumission; candidats à fusion avec mode et permissions |
| `PointageGridTable` | saisie/table jour | ~691 lignes, reçoit valeurs déjà prévisualisées | KEEP, vérifier logique d'affichage vs calcul |
| `PointageGanttView` | vue DAF facultative | ~597 lignes, utilisé uniquement par ArbitrageDafPage | KEEP secondaire jusqu'à validation d'usage, pas une source de vérité |
| `PointageDetailHebdoModal` | détail jour/semaine d'employé | API fiche employé | KEEP tant que le workflow l'utilise; semaine persistée non requise en tant que concept UI |
| `PointageRecapModal`, `PointageFichePrintModal` | récapitulatif/impression | analyser imports/runtime avant suppression | aucune suppression sans preuve de non-usage |

Les trois étapes sont déjà montées sous un shell unique via query param; il ne s'agit pas réellement de trois routes indépendantes. Fusionner les trois gros fichiers de page réduirait la navigation code mais mélangerait les états/perms (plus de 2 800 LOC). La cible raisonnable est un orchestrateur unique léger qui conserve trois vues/sections et le lien profond `tab`.

Les deux modales de correction partagent le même endpoint et des horaires/motif/statut/indemnités, mais leurs scénarios sont différents : anomalie avec choix absence, demi-journée et permission; DAF avec sélection de jour depuis une fiche employé. Fusion UI candidate; les contrôles d'accès doivent rester backend-driven et les branches métier préservées.

## 8. Décompte de départ phase 2

| Mesure | État de travail analysé |
|---|---:|
| Fichiers Java Pointage | 41 |
| Fichiers frontend attendance | 21 |
| Fichiers calculateurs sous `domain/calculation` | 15 environ (dont types/records et algorithmes temporels) |
| Services applicatifs Pointage | 5 |
| Controllers Pointage + période | 6 |
| DTO Pointage/période | 9 |
| Tables du schéma courant | 14 |
| Pages du shell Pointage | 4 (shell + 3 étapes) |
| Modales identifiées | 5 |
| Tests Java Pointage dédiés trouvés | 1 intégration Congés↔Pointage; aucun test unitaire calcul `WeeklyAttendanceCalculator` trouvé |
| Tests frontend ciblant attendance | aucun scénario workflow identifié dans la suite actuelle |

La commande de comptage peut inclure quelques types/supports; les totaux sont des mesures de fichiers présents et pas un quota cible.

## 9. Recommandations d'exécution phase 2

1. Ajouter d'abord des tests unitaires à `WeeklyAttendanceCalculator` pour compensation, HS30/HS50, demi-journée/férié et segments de période. Le moteur existant calcule déjà la compensation; ne pas remplacer ses formules avant d'avoir ces invariants.
2. Centraliser les écritures `pointage_jour` + `pointage_indemnite_jour` dans un seul chemin applicatif transactionnel en réutilisant la même connexion.
3. Réduire les micro-types utilisés uniquement par le calcul hebdomadaire (prior state et résultats cumulatifs inutilisés) après test de parité. Les types calendrier/calcul jour sont réellement partagés et ne sont pas automatiquement orphelins.
4. Fusionner les modales au niveau du formulaire commun sans mêler l'orchestration de fiche DAF à la résolution de l'anomalie.
5. Remplacer progressivement les lectures SQL de `pointage_semaine_calcule` par résultats mémoire et comparer la sortie historique. Ne pas supprimer la table si aucune base échantillon/export/replay n'est accessible.
6. Conserver les buckets et snapshots jusqu'à un replay monétaire avant/après couvrant taux datés, collisions nuit/dimanche/férié, nuit habituelle/occasionnelle et segmentation inter-périodes.

## 10. Questions `BUSINESS_RULE_SUSPECT` à ne pas trancher dans ce refactor

- Faut-il maintenir une semaine civile strictement étanche entre périodes de paie ? C'est le commentaire/runtime actuel; le besoin phase 2 cite aussi une semaine traversant deux périodes.
- Comment traiter les journées fériées travaillées dans l'objectif attendu et l'éligibilité au seuil ? Vérifier à partir d'exemples RH/Paie avant de changer les exclusions.
- Que faire lorsque les horaires de site ou le paramètre `SEUIL_HEURES_JOUR` sont absents ? Le code utilise plusieurs fallback 8h/horaire.
- La tolérance d'arrivée est administrable mais désactivée au calcul : supprimer la configuration ou réactiver une règle sont deux décisions métier différentes.
- Le code qualifie nuit 22:00–05:00 à plusieurs endroits avec un horaire fixe; confirmer la source canonique avant de la centraliser.
- Les statuts que React envoie doivent-ils être recalculés/ignorés systématiquement par le serveur ? Le comportement actuel admet certains statuts explicites; toute évolution requiert parité et tests de workflow.
