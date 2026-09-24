# Pointage — phase 4 : consolidation backend

## Résultat et périmètre

La consolidation réduit le domaine de **35 à 23 fichiers Java** sans changer les routes HTTP, le frontend, les tables ou les règles de calcul. L'inventaire exhaustif et les décisions préalables figurent dans `POINTAGE_PHASE4_CONSOLIDATION_ANALYSIS.md`. La cible de deux services n'est **pas atteinte** : les services existants ont des transactions et des volumes qui rendraient leur fusion immédiate en une classe de plus de 3 000 lignes peu navigable. La duplication de SQL de write journalier reste également ouverte ; cette phase ne doit pas être présentée comme une unification complète des writes.

| Mesure | Avant | Après | Commentaire |
|---|---:|---:|---|
| Fichiers Java Pointage | 35 | 23 | −12 |
| Services applicatifs | 5 | 5 | Saisie renommé `PointageService`, autres conservés |
| Contrôleurs | 6 | 2 | 23 routes conservées |
| Fichiers `domain/calculation` | 12 | 6 | Deux moteurs purs + quatre contrats Paie |
| DTO autonomes | 9 | 7 | Deux requêtes de paramétrage inlinées |
| LOC Java Pointage | ~10 027 | 9 744 | Le chiffre avant provient de l'inventaire préalable ; le chiffre après de `wc -l` |
| Imports inter-domaines | Contrats Paie/Congés existants | Inchangés sur le fond | Aucune dépendance Paie cachée dans un type interne |

## Fusions, inline et suppressions

- Les cinq contrôleurs `PointageSaisieController`, `PointageAnomalieController`, `PointageEmployeController`, `PointageSyntheseController` et `PointageParametreController` sont regroupés dans `PointageController`. `PeriodePaieController` devient `PointagePeriodeController`. Préfixes de routes, permissions et signatures JSON restent identiques.
- `PointageSaisieService` devient `PointageService`, propriétaire explicite des writes de `pointage_jour` et `pointage_indemnite_jour` déjà centralisés par la phase 3. Les noms d'injection métier utilisés localement restent parfois `pointageSaisieService` : dette de lisibilité mineure.
- `DailyAttendanceCalculator`, `WeeklyAttendanceCalculator` et `PeriodWeekSegmenter` sont réunis dans `AttendanceEngine` : `calculateDay`, `calculateWeek`, `segmentWeeks`, et records de contexte/résultat internes. Le test de compensation et de périodes coupées devient `AttendanceEngineTest`.
- `WorkInterval`, `TemporalBucket`, `WorkIntervalFactory`, `AttendanceTemporalPartitioner` et `OvertimeProjectionEngine` sont réunis dans `TemporalAttendanceEngine` en types internes. Les algorithmes et invariants de conservation ont été déplacés sans nouvelle formule de durée ou de paie.
- `ToleranceRequest` et `DerogationRequest` deviennent des classes statiques internes au contrôleur unique ; les propriétés JSON JavaBean restent inchangées.
- Les anciens fichiers ainsi fusionnés sont supprimés ; aucune migration BDD ni modification frontend n'a été ajoutée en phase 4.

## Architecture finale et responsabilité des fichiers conservés

| Fichier/groupe | Rôle et raison de conservation |
|---|---|
| `api/PointageController.java` | Saisie, anomalies, fiche, synthèse, export et paramètres ; navigation HTTP unique |
| `api/PointagePeriodeController.java` | Contexte et transitions de période ; workflow propre |
| `application/PointageService.java` | Grille, batch, écriture jour et indemnités, correction journalière |
| `application/PointageSyntheseService.java` | Calcul des semaines en mémoire, KPI, cumul et snapshots du gel |
| `application/PointageAnomalieService.java` | Régularisation, contrôle et transaction JDBC avec `CongeService` ; 914 lignes, fusion dangereuse avec `PointageService` (~1 829 lignes) |
| `application/PointageEmployeService.java` | Fiche individuelle et statistiques ; 929 lignes de lecture spécialisée, distinctes du write journalier |
| `application/PointageExcelExportService.java` | 735 lignes de présentation Excel ; ne pollue pas le métier |
| `domain/calculation/AttendanceEngine.java` | Calcul pur jour, semaine et frontières de tronçons |
| `domain/calculation/TemporalAttendanceEngine.java` | Construction des intervalles, partition minute et projection HS ; algorithme autonome justifiant le deuxième moteur temporel |
| `domain/engine/CalendrierOuvrableEngine.java` | Calendrier ouvrable/férié/site autonome |
| `domain/calculation/{QualifiedTimeBucket,OvertimeTier,DaySpecialType,NightWorkType}.java` | Contrats explicites importés par Paie ; ne doivent pas devenir internes aux moteurs |
| `api/dto/` (7 fichiers) | Contrats publics de grille, anomalie, fiche, semaine, synthèse et période, complexes ou réutilisés |
| `persistence/{RefToleranceArriveePointage,EmployeIndemniteDerogation}.java` | Paramétrage persistant ; dérogation également utilisée hors Pointage |

## Pipeline de calcul et contrat Paie

`PointageService` → `AttendanceEngine.calculateDay` → write `pointage_jour`/`pointage_indemnite_jour`.

`PointageSyntheseService` → `AttendanceEngine.segmentWeeks`/`calculateWeek` → `TemporalAttendanceEngine` (intervalles, partition, projection) → agrégation/gel → `pointage_cumul_periode`, `pointage_snapshot_jour`, `pointage_snapshot_bucket` → Paie.

La semaine ne possède aucune table runtime : aucune référence Java à `pointage_semaine_calcule` après consolidation. `pointage_cumul_periode` reste le contrat de synthèse vers Paie et les buckets demeurent figés pour le replay monétaire. Les quatre types temporels partagés avec Paie restent des fichiers publics distincts.

L'éligibilité HS continue d'être résolue à la date du tronçon via `employe_affectation_carriere.id_categorie_professionnelle` puis `ref_categorie_professionnelle.est_eligible_hs`. `AttendanceEngine.calculateWeek` ne reçoit que `overtimeEligible`. **HISTORICAL_CONFIGURATION_RISK** : la valeur du référentiel catégorie n'est pas historisée ; un replay après modification de cette valeur peut diverger sans que ce refactor puisse trancher la règle métier.

La régularisation Congés continue d'appeler `CongeService` avec la même connexion JDBC. Les lectures SQL de `demande_conge` dans Pointage restent des lectures ; aucun `INSERT` direct Pointage dans `demande_conge` ou `mouvement_solde_conge` n'a été introduit.

## SQL jour : avant/après et dette restante

Avant comme après, `PointageService` est le seul propriétaire applicatif des writes dans `pointage_jour` et `pointage_indemnite_jour`, mais **deux chemins SQL internes subsistent** :

1. Le batch prépare ses propres `INSERT`/`UPDATE` de journée (version optimiste, fallback du statut `PRESENT`) et un upsert dynamique d'indemnité par identifiant, code/fraction et configuration.
2. `saveRegularizedDay` prépare un second `INSERT`/`UPDATE` (statut strict, motif de correction, version) et synchronise seulement `REPAS`/`DEPLACEMENT` sur la fraction `JOUR_ENTIER`. Ce chemin absorbe historiquement les exceptions de synchronisation d'indemnité.

Une fusion mécanique de ces deux requêtes changerait les fallbacks de statut, la granularité des indemnités ou la gestion d'échec. **DETTE NON RÉSOLUE** : définir et tester un `persistDay` canonique et une synchronisation d'indemnités commune avec parité transactionnelle explicite. Le maintien de l'absorption des erreurs de régularisation est `BUSINESS_RULE_SUSPECT` ; il peut laisser le pointage corrigé sans indemnités synchronisées.

## Fallbacks et règles inchangés

Les valeurs historiques 08:00/12:00/13:00/17:00, 07:30, objectif journalier implicite, seuil de première tranche et frontières 22:00/05:00 n'ont pas été modifiées. Plusieurs résolutions sont encore dispersées entre grille/saisie/synthèse et partition temporelle : **LEGACY_FALLBACK / CONFIGURATION_MISSING** tant qu'une source canonique datée n'a pas été établie. Aucun hard-code supplémentaire n'a été ajouté aux formules HS. La compensation des déficits avant HS et la séparation des tronçons de période restent celles des tests préexistants.

## Validation

- `mvn clean test -q` : succès, 54 tests, 0 échec/erreur (y compris intégration Congés/Pointage, 11 tests du moteur semaine et 2 nouveaux tests de conservation temporelle).
- `npm test -- --run` : succès, 4 tests.
- `npm run lint` : succès.
- `npm run build` : succès.
- `git diff --check` : succès.
- Frontend : aucun fichier modifié par cette phase.

## Dettes restantes et condition de fin

La navigation principale est désormais courte : saisie et écritures → `PointageService` ; synthèse et gel → `PointageSyntheseService` ; calcul pur → `AttendanceEngine` / `TemporalAttendanceEngine` ; calendrier → `CalendrierOuvrableEngine` ; actions période → `PointagePeriodeController`. Les services Anomalie/Employé demeurent des exceptions justifiées par leur taille et leur rôle. La condition de fin idéale de **deux services et un write SQL canonique** n'est donc que partiellement satisfaite. La prochaine étape sûre serait d'ajouter un test de parité batch/régularisation couvrant version, statuts, fractions d'indemnité et rollback, puis de fusionner les deux writes internes sans changer silencieusement leur comportement.
