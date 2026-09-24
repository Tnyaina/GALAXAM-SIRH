# Pointage & Présence — rapport phase 2

Date : 2026-09-24  
Périmètre : simplification structurelle après analyse documentée, sans changement intentionnel des règles métier.

## Résumé

Pointage était effectivement l'un des domaines les plus fragmentés : avant cette phase, le package backend contenait 41 fichiers Java et le domaine frontend 21 fichiers. La phase 2 ramène le backend à 36 fichiers Java (-5). Le nombre frontend reste 21 : les pages, hooks et modales portent des parcours distincts, et leur fusion sans tests de parcours ni contrat d'autorisation commun aurait accru le risque pour un gain essentiellement cosmétique.

L'analyse préalable est consignée dans [POINTAGE_PHASE2_ANALYSIS.md](POINTAGE_PHASE2_ANALYSIS.md). Elle conserve le modèle métier observé : **jour = source, semaine = calcul matérialisé, période = consolidation, gel = snapshot historique, Paie = consommateur**.

## Travail effectué

- Rassemblement de `Context` et `Result` à l'intérieur des calculateurs journaliers et hebdomadaires, puis suppression des cinq fichiers autonomes correspondants (`DailyAttendanceContext/Result`, `WeeklyAttendanceContext/Result`, `WeeklyPriorState`). Le support générique `WeeklyPriorState` était toujours `NONE` à l'appel; le calcul reste indépendant par segment semaine/période.
- Ajout de tests unitaires du calcul hebdomadaire couvrant compensation, déficit, congés, férié chômé, segmentation de période, tranches HS30/HS50 et inéligibilité cadre.
- Centralisation dans `PointageSaisieService.saveRegularizedDay(...)` des écritures `pointage_jour` et de la synchronisation des indemnités effectuées pour une régularisation. `PointageAnomalieService` garde l'orchestration de l'anomalie, appelle ce propriétaire de persistance sur la même connexion, recalcule la semaine et conserve la transaction existante.
- Aucun schéma, migration ou contrat utilisateur n'a été ajouté/modifié dans cette phase. Les changements de schéma déjà présents dans l'arbre de travail appartiennent à la première passe décrite dans `POINTAGE_GLOBAL_REFACTOR_REPORT.md`.

## Analyse workflow

Le parcours de référence est :

```text
Période de paie ouverte
  → GestionPointagesPage (?tab=saisie)
  → saisie du jour / calcul canonique DailyAttendanceCalculator
  → correction ou régularisation via le suivi des anomalies
  → recalcul hebdomadaire (calcul indépendant par segment de période)
  → synthèse consolidée de période
  → SUBMIT vers DAF
  → APPROVE ou REJECT via PayrollPeriodWorkflowService
  → gel versionné : cumul période + snapshot journalier + buckets qualifiés
  → consommation par Paie
```

Les semaines ne sont plus une étape d'approbation. `pointage_semaine_calcule` demeure cependant utilisée par la synthèse, les filtres et les détails : elle reste donc une matérialisation technique à remplacer/rejouer avant une suppression éventuelle. `pointage_snapshot_bucket` demeure nécessaire au calcul Paie qui applique taux datés, tranches et qualifications d'heures spéciales. Ni cette table ni le calcul hebdomadaire matérialisé ne sont supprimés.

## Réduction mesurée

| Zone ciblée | Avant phase 2 | Après phase 2 | Réduction |
|---|---:|---:|---:|
| Fichiers Java `pointage/` | 41 | 36 | 5 (-12,2 %) |
| Fichiers frontend `features/attendance/` | 21 | 21 | 0 |
| Fichiers du sous-package `domain/calculation/` | 17 | 12 | 5 |

La réduction frontend n'est pas forcée : `SaisieQuotidiennePage`, suivi des anomalies, arbitrage et modales de correction ont des états/permissions et responsabilités distincts. Les calculateurs React gardent le preview immédiat; le serveur demeure propriétaire du calcul persisté. Un remplacement propre demande un endpoint de preview ou une validation explicite de la perte de preview, puis des tests d'équivalence.

## Frontière Pointage / Congés

La régularisation Pointage délègue les écritures de demande et de ledger au `CongeService` sur la connexion JDBC courante, et conserve le commit atomique couvrant le congé, la journée Pointage et son recalcul. C'est une meilleure frontière de propriété, mais **pas encore l'utilisation du workflow normal des demandes de congé** : le comportement historique direct (demande validée et débit selon le chemin existant) demeure. Ne pas en déduire que contrôles de solde, pool, préavis ou approbations usuelles sont appliqués. Une API applicative Congés doit être conçue séparément; toute règle de dérogation/auto-validation requiert une décision métier.

## Règles et dettes préservées

- Ne pas modifier les horaires de fallback, les seuils configurables, l'éligibilité HS, les fériés, la tolérance d'arrivée désactivée, les statuts soumis par l'UI ou la frontière étanche semaine/période sans replay et décision métier (`BUSINESS_RULE_SUSPECT`).
- Ne pas supprimer `pointage_semaine_calcule` avant de remplacer chacun de ses lecteurs par un calcul mémoire et de comparer la synthèse/détail sur des périodes historiques.
- Ne pas supprimer ou réduire les buckets sans replay monétaire Paie couvrant nuit, dimanche, férié, taux datés, collisions et qualification habituelle/occasionnelle.
- Les deux modales de régularisation restent distinctes : leur endpoint est partagé, mais les sélections, données chargées et autorisations ne sont pas équivalentes.
- La transaction inter-domaines actuelle reste une transaction JDBC unique; les scénarios d'idempotence, double clic, retry et concurrence n'ont pas reçu de nouveaux tests spécifiques dans cette phase.

## Vérifications

- `mvn test -q` — réussi.
- `npm test -- --run` — réussi (1 fichier, 4 tests).
- `npm run lint` — réussi.
- `npm run build` — réussi.
- `git diff --check` — réussi avant création de ce rapport; à revérifier avec le document final.

Les modifications antérieures et artefacts non suivis déjà présents dans l'arbre de travail n'ont pas été nettoyés. Ce rapport ne prétend donc pas que l'intégralité des changements visibles dans `git status` a été créée par la seule phase 2.

## Parcours minimal visé

```text
Agent : saisir jour → traiter anomalies → transmettre période
Admin/DAF : contrôler synthèse/anomalies → arbitrer → valider ou rejeter
Backend : calculer jour → recalculer semaine → consolider période → geler snapshot
Congés : décider et persister toute demande de régularisation selon ses règles
Paie : lire exclusivement la version gelée et valoriser les qualifications conservées
```

Avant le refactor suivant, les arbitrages métier prioritaires sont : la politique de régularisation congé (validation/dérogation et contrôle solde), la conduite à tenir sur horaires/configuration absente et tolérance, et la règle exacte de calcul des jours fériés travaillés.
