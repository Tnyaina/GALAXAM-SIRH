# REGISTRE DES DETTES HISTORIQUES & AUDIT CONTRADICTOIRE
## GALAXAM SIRH — Gate de Clôture Avant Phase 1C
**Date d'établissement :** 17 septembre 2026  
**Auditeur :** Antigravity (Google DeepMind)  
**Socle technique vérifié :** Backend Java/Spring Boot (308 tests PASS), Frontend React (16 tests PASS, build OK), PostgreSQL `sirh_galaxam`  
**Statut Global :** AUDITÉ, CLÔTURÉ & CONTRÔLÉ

---

### Tableau d'Inventaire Exhaustif des Dettes Historiques

| ID | Source | Dette historique | Module | État documentaire | État réel code | Preuve | Action / Résolution |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **D-01** | Phase 1 / Contre-Audit P1 | Continuité hebdomadaire 25/26 via `WeeklyPriorState` (carry-forward inter-périodes) | Pointage | Conclu "PASS" à tort en P1 | `CLOSED_PROVEN` | Arbitrage GALAXAM 17/09/2026 : frontières de paie étanches. `priorState = WeeklyPriorState.NONE` dans `PointageSyntheseService`. 12/12 tests `Phase1bTronconIndependantTest` PASS. | Fermée en Phase 1B. Interdiction stricte de carry-forward. |
| **D-02** | Contre-Audit P1 | Saut arbitraire de week-end dans `getSemainesPeriode` & divergence de partitionnement | Pointage | `OPEN_CONFIRMED` | `CLOSED_PROVEN` | Extraction de `PeriodWeekSegmenter.java`. Suppression du saut `isWeekend(cur)`. Seuil dynamique `seuilHeuresJour`. 10/10 tests d'intégration pointage PASS. | Fermée en Phase 1B. Partitionnement canonique unifié. |
| **D-03** | Gate Pre-1C / Arbitrage Samedi | Samedi travaillé non pris en compte dans la compensation hebdomadaire ordinaire | Pointage | `OPEN_CONFIRMED` | `CLOSED_PROVEN` | `WeeklyAttendanceCalculator.dailyTarget` fixé à 0 min pour le samedi non contractuel. Samedi participe à la compensation ordinaire (T11) et produit des HS au-delà de 40h (T12). | Corrigée dans le Gate. 12/12 tests PASS. |
| **D-04** | Phase 0 (test 2.1) / Audit Consolidé | Proratisation salaire entrée/sortie calculée par formule horaire (`BASE / 173.33 * h`) | Paie | `OPEN_CONFIRMED` | `CLOSED_PROVEN` | `PayrollCalculationInput.joursSousContrat` ajouté. `PayrollExecutionEngine` calcule `BASE * jours / 30`. Tests 1.1b, 1.1c, 1.1d, 1.1e dans `CalculsConformiteIntegraleTest` PASS. | Corrigée dans le Gate. Règle du 30ème calendaire active. |
| **D-05** | Phase 0 (test 2.2) / Audit Consolidé | Formule KAJY `jAbs - jCge` effaçant l'absence injustifiée lors de la prise de congés | Paie / Congés | `OPEN_CONFIRMED` | `OPEN_CONFIRMED` | `PayrollService.java:870` contient encore `jAbs.subtract(jCge).max(0)`. Pointage sépare déjà en amont `nb_jours_absence` et `nb_jours_conge`. | Maintenue ouverte : assignée formellement à la Phase 2 (Congés / KAJY). Non bloquante pour 1C. |
| **D-06** | Audit Consolidé / StcService | Fallback silencieux `salaireBaseFallback / 30.0` pour premier TCONGE sans historique | STC / Paie | `OPEN_CONFIRMED` | `BUSINESS_RULE_REQUIRED` | `StcService.java:952-954` applique `BASE / 30` en cas d'absence d'historique dans `conge_journal_droit`. | Maintenue ouverte : arbitrage métier requis sur la valorisation initiale du TCONGE. Cible Phase 2. |
| **D-07** | Baseline Phase 0 / Sprint 2B | Test architectural frontend échouant sur assertions de chaînes de caractères obsolètes | Architecture / UI | `BASELINE_FAIL` | `CLOSED_PROVEN` | `BackendDrivenFrontendArchitectureTest` adapté : retrait des chaînes d'un tableau inline obsolète, validation des routes `/results` et redirections `bulletins`. | Corrigée dans le Gate. Suite de tests: 308/308 PASS (0 fail). |
| **D-08** | Audit 2026-09-13 / docs | Présence résiduelle suspectée de l'ancien moteur `CalculateurPaieEngine` | Paie | Suspecté | `SUPERSEDED` | Aucun fichier `CalculateurPaieEngine.java` dans `backend/src/main/java`. Moteur unique : `PayrollExecutionEngine` et `StcService`. | Fermée (code mort déjà purgé). |
| **D-09** | Audit Global 2026-09-14 | Duplication des algorithmes IRSA et ISI entre Paie et STC | Paie / STC | Suspecté | `CLOSED_PROVEN` | `PayrollExecutionEngine` et `StcService` partagent les mêmes classes canoniques `IrsaCalculator` et `IsiCalculator`. | Fermée. Source unique de calcul fiscal. |
| **D-10** | Audit Constantes Métier | Plafond CNaPS hardcodé via `MULTIPLICATEUR_PLAFOND_CNAPS = 8.0` | Paie | `OPEN_CONFIRMED` | `OPEN_CONFIRMED` | `PayrollExecutionEngine.java:36`. Conforme à l'art. 13 du Code de Prévoyance Sociale malgache (8× SME), mais codé en constante Java statique. | Maintenue ouverte : classée `OPEN_NON_BLOCKING`. Historisation DB à envisager ultérieurement. |
| **D-11** | Audit Global 2026-09-14 | Divergences possibles d'arrondis monétaires entre modules | Paie / STC | `CLOSED_PROVEN` | `CLOSED_PROVEN` | `MoneyRoundingPolicy` applique `amount` (échelle 2, HALF_UP). Tests frontières `x.004`, `x.005`, `x.006` validés dans `MoneyRoundingPolicyTest`. | Fermée. Politique unifiée. |
| **D-12** | Contre-Audit Phase 1B | Absence de transmission des minutes DIMANCHE, FERIE et SAMEDI dans `PayrollAttendanceInput` | Pointage $\to$ Paie | `OPEN_CONFIRMED` | `INPUTS_READY_FOR_PHASE_1C` | `pointage_jour` et `pointage_semaine_calcule` qualifient et stockent déjà ces minutes. `PayrollAttendanceInput` doit être enrichi lors de la valorisation monétaire. | Mandat explicite de la Phase 1C. Inputs d'ores et déjà prêts. |
| **D-13** | Phase 1 / Phase 1B | Réduction d'objectif hebdomadaire pour férié chômé et congé payé | Pointage | `CLOSED_PROVEN` | `CLOSED_PROVEN` | `PointageSyntheseService` réduit `joursRequis = max(0, joursOuvres - joursConge)` et déduit les fériés chômés via `compterJoursOuvrables`. Tests T7/T8 PASS. | Fermée et prouvée par tests. |
| **D-14** | Audit Code Qualité | Usage technique de `double` pour compteurs statistiques du cockpit | Pointage / UI | `OPEN_CONFIRMED` | `OPEN_NON_BLOCKING` | `PointageSyntheseService.java:1254` (`heuresSaisies`). Donnée d'affichage informatif uniquement, sans impact sur les calculs financiers ni les bulletins. | Maintenue ouverte : classée `DISPLAY_ONLY`, non bloquante. |

---

### Synthèse des Catégories

* **CLOSED_PROVEN (Fermées avec preuve complète) :** 7 dettes (D-01, D-02, D-03, D-04, D-07, D-09, D-11, D-13).
* **SUPERSEDED (Remplacées / Absentes) :** 1 dette (D-08).
* **INPUTS_READY_FOR_PHASE_1C (Prêtes pour la Phase 1C) :** 1 dette (D-12).
* **OPEN_NON_BLOCKING (Ouvertes documentées, non bloquantes pour Phase 1C) :** 3 dettes (D-05, D-10, D-14).
* **BUSINESS_RULE_REQUIRED (Nécessite arbitrage métier futur, Phase 2) :** 1 dette (D-06).
* **OPEN_BLOCKING (Bloquant le passage à Phase 1C) :** **0 DETTE BLOQUANTE.**
