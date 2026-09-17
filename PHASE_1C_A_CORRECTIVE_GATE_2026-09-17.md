# RAPPORT DE CONTRE-AUDIT ET GATE CORRECTIF FINAL — PHASE 1C-A
**Date :** 17 Septembre 2026  
**Système :** GALAXAM-SIRH (Socle Pointage → Qualification Temporelle → Snapshot Paie)  
**Auteur :** Antigravity AI — Pair Programming Audit & Architecture  
**Verdict du Gate :** `PHASE_1C_A_CORRECTED_PASS`

---

## 1. BASELINE ET RÉSULTATS D'EXÉCUTION

Avant ce gate correctif, la Phase 1C-A présentait 358 tests backend et 16 tests frontend.  
Suite aux corrections ciblées, la suite complète a été exécutée :

| Composant | Résultat | Détail |
|---|---|---|
| **Backend Spring Boot** | **367 / 367 PASS** (+9 tests) | 0 échec, 0 erreur, 0 sauté (durée : 2 min 26 s) |
| **Frontend React (Vitest)** | **16 / 16 PASS** | 3 fichiers de tests, 0 régression |
| **Build Frontend (Vite)** | **PASS** | Bundle dist généré sans erreur |
| **PostgreSQL Integration** | **PASS** | Tests transactionnels réels sur snapshots versionnés V1/V2 |
| **Git Diff Check** | **PASS** | Aucun whitespace error, code conforme |

---

## 2. SUPPRESSION DE LA LIMITE ARTIFICIELLE DE VACATION À 16H

### Constat d'audit
L'implémentation initiale de `WorkIntervalFactory` rejetait arbitrairement toute vacation excédant 16 heures (960 minutes) avec l'exception `DUREE_VACATION_ABERRANTE`. Cette règle n'avait jamais été édictée par la réglementation malgache ni par les spécifications GALAXAM.

### Correction appliquée
- **Suppression intégrale** de :
  - `MAX_WORK_INTERVAL_DURATION = 960`
  - Le contrôle `durationMinutes > MAX_WORK_INTERVAL_DURATION`
  - Le message d'erreur `DUREE_VACATION_ABERRANTE`
- **Aucun remplacement arbitraire** par 18h, 20h ou 24h.
- `WorkIntervalFactory` opère désormais en pur reconstructeur temporel.
- Une saisie `08:00 -> 05:00` produit fidèlement `J 08:00 -> J+1 05:00` (1260 minutes).

---

## 3. COMPORTEMENT FINAL DE `WorkIntervalFactory`

### Responsabilité
`WorkIntervalFactory` convertit une saisie de pointage brut journalier en couples d'instants réels `LocalDateTime start` / `LocalDateTime end` :

$$\text{Pointage brut} \xrightarrow{\text{WorkIntervalFactory}} [\text{WorkInterval}_1, \dots, \text{WorkInterval}_n]$$

### Règle Cross-Midnight
Pour chaque paire d'heures $(E, S)$ :
- Si $S \ge E$ : $start = date \mathbin{\Vert} E$, $end = date \mathbin{\Vert} S$ ($end > start$, même jour calendaire).
- Si $S < E$ : $start = date \mathbin{\Vert} E$, $end = (date + 1) \mathbin{\Vert} S$ ($end > start$, transition cross-midnight sur $J+1$).

### Invariants structurels conservés
1. `start != null` et `end != null`.
2. `end.isAfter(start)` obligatoire.
3. `durationMinutes > 0` obligatoire (une entrée égale à la sortie est rejetée comme durée nulle).

---

## 4. WORKFLOW DES ANOMALIES ET RESPECT DES RESPONSABILITÉS

GALAXAM dispose d'une chaîne de responsabilités claire :
1. **`WorkIntervalFactory`** : reconstruit fidèlement les intervalles physiques d'après les heures pointées, sans porter de jugement de plausibilité RH.
2. **Moteur d'anomalies Pointage (`DailyAttendanceCalculator`)** : détecte et consigne les anomalies de saisie (pointages incomplets, retards excessifs, incohérences).
3. **Agent de pointage / Gestionnaire RH** : contrôle, arbitre et régularise les pointages anormaux via les interfaces et modales dédiées (`AnomalieRegularisationModal`, `DafAjustementPointageModal`).
4. **Gel de période (`pointageSyntheseService.gelerPeriode`)** : scelle les données validées en snapshot immuable versionné.
5. **Moteur de Paie** : consomme le snapshot scellé pour la valorisation financière.

### Pointage incomplet : aucune invention d'heures
- Entrée présente, sortie absente $\implies$ Aucun `WorkInterval` inventé ; renvoie `null` / liste vide.
- Sortie présente, entrée absente $\implies$ Aucun `WorkInterval` inventé ; renvoie `null` / liste vide.
- Les pointages incomplets suivent exclusivement le workflow d'anomalies.

---

## 5. AUDIT DE LA PROVENANCE DES HEURES SUPPLÉMENTAIRES (HS)

### Fondement juridique malgache (Code du Travail & Décret n° 72-226)
L'article 75 du Code du Travail malgache définit les heures supplémentaires comme :
> *« toutes les heures effectuées au-delà de la durée légale du travail hebdomadaire (40 heures par semaine ou durée équivalente). »*

En conséquence mathématique et juridique :
1. **Priorité au quota normal hebdomadaire :** Jusqu'à concurrence de l'objectif légal de la semaine (2400 minutes pour 40h, ou l'objectif proratisé du tronçon), chaque minute travaillée remplit l'obligation de base (`REGULAR`).
2. **Compensation intrajournalière :** Un surplus le lundi (+2h) compense un déficit le mardi (-2h). Le travailleur n'a alors effectué que 16h normales sur les deux jours : aucun surplus n'est qualifié en HS.
3. **Position chronologique des HS :** Dans le modèle canonique GALAXAM actuel,
après détermination de l'objectif et compensation Phase 1B,
les volumes REGULAR / HS30 / HS50 sont projetés
chronologiquement sur les minutes travaillées éligibles.

Cette règle constitue la convention d'allocation du SIRH
et est couverte par les tests de provenance Phase 1B/1C-A.

---

## 6. RÉSULTATS DES SCÉNARIOS CONTRADICTOIRES DE PROVENANCE HS

Les 5 scénarios contradictoires exigés par le gate ont été implémentés dans [`Phase1cProvenanceHsAuditTest.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/test/java/mg/galaxam/sirh/unit/pointage/Phase1cProvenanceHsAuditTest.java) :

| Scénario | Configuration | Résultat Phase 1B | Projection Phase 1C-A | Conformité |
|---|---|---|---|---|
| **Cas A** | Lundi +120, Mardi -120, Mercredi +60, Jeudi 0, Vendredi 0 | Travaillé: 2460 min<br>Compensé: 120 min<br>HS30: 60 min, HS50: 0 | 2400 min REGULAR<br>60 min HS30 projetées Vendredi 15:00-16:00 | **PASS** |
| **Cas B** | Lundi +60, Mardi -60, Mercredi 0, Jeudi 0, Vendredi +120 | Travaillé: 2520 min<br>Compensé: 60 min<br>HS30: 120 min, HS50: 0 | 2400 min REGULAR<br>120 min HS30 projetées Vendredi 16:00-18:00 | **PASS** |
| **Cas C** | Nuit Lun 21h-05h (480 min dt 420 nuit), Mardi 0 min | Travaillé: 480 min<br>Objectif 2j: 960 min $\implies$ HS: 0 | 480 min REGULAR<br>Nuit UNCLASSIFIED (420 min) préservée | **PASS** |
| **Cas D** | Mardi déficit -120 min, Dimanche travaillé 480 min | Dimanche non compensable<br>Déficit mardi = 120 min | Heures dimanche qualifiées SUNDAY sans absorption du déficit | **PASS** |
| **Cas E** | Semaine 40h + Samedi 4h + Dimanche 6h | 2400 min REGULAR + 240 min HS30 + 360 min Dimanche | Samedi = OVERTIME_30 + SATURDAY<br>Dimanche = OVERTIME_50 + SUNDAY | **PASS** |

### Démonstration d'adéquation
Ces tests prouvent de façon contractuelle que la projection chronologique appliquée par `OvertimeProjectionEngine` est **strictement conforme** à la qualification des volumes issue de la compensation Phase 1B. L'architecture est donc sanctuarisée sans nécessiter de modification intrusive de Phase 1B.

---

## 7. ÉVOLUTION DU MODÈLE CALENDAIRE : DIMANCHE + FÉRIÉ

### Problématique initiale
L'enum `DaySpecialType` initial utilisait une hiérarchie exclusive :
`PUBLIC_HOLIDAY > SUNDAY > SATURDAY > STANDARD`
Un jour férié tombant un dimanche (ex: Toussaint le 1er Novembre 2026) écrasait la qualification `SUNDAY`.

### Solution adoptée
- Enrichissement de `DaySpecialType` avec la constante :
  ```java
  public enum DaySpecialType {
      STANDARD,
      SATURDAY,
      SUNDAY,
      PUBLIC_HOLIDAY,
      SUNDAY_AND_PUBLIC_HOLIDAY
  }
  ```
- Ajout de prédicats orthogonaux sur `DaySpecialType`, `TemporalBucket` et `QualifiedTimeBucket` :
  - `isSunday()` $\to$ `true` pour `SUNDAY` et `SUNDAY_AND_PUBLIC_HOLIDAY`
  - `isPublicHoliday()` $\to$ `true` pour `PUBLIC_HOLIDAY` et `SUNDAY_AND_PUBLIC_HOLIDAY`
  - `isSaturday()` $\to$ `true` pour `SATURDAY`
  - `isStandard()` $\to$ `true` pour `STANDARD`
- Adaptation de la colonne SQL `pointage_snapshot_bucket.day_special_type` à `VARCHAR(40)` via migration dédiée.

### Test unitaire de coexistence (Section 17)
Dans [`SundayAndHolidayCoexistenceTest.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/test/java/mg/galaxam/sirh/unit/pointage/SundayAndHolidayCoexistenceTest.java) :
- Date : Dimanche 1er Novembre 2026 (Toussaint)
- Vacation : 22:00 -> 23:00 (60 minutes) avec tier `OVERTIME_30`
- Résultat vérifié :
  - `overtimeTier() == OVERTIME_30`
  - `isSunday() == true`
  - `isPublicHoliday() == true`
  - `nightType() == NIGHT_UNCLASSIFIED`
  - Aucun taux monétaire appliqué, tous les faits temporels conservés.

---

## 8. TRAVAIL DE NUIT : SÉPARATION FAIT TEMPOREL / CLASSIFICATION RH

L'enum `NightWorkType` conserve la distinction stricte :
- `NONE` : travail diurne (05:00 -> 22:00).
- `NIGHT_UNCLASSIFIED` : fait temporel brut de nuit (22:00 -> 05:00) reconstruit par partitionnement, en attente de classification.
- `HABITUEL` : classification contractuelle ou structurelle (travailleur de nuit posté).
- `OCCASIONNEL` : travailleur de jour effectuant une vacation de nuit exceptionnelle.

L'attribution `HABITUEL` n'est jamais faite arbitrairement par le partitionneur temporel : les heures sont par défaut `NIGHT_UNCLASSIFIED` jusqu'à qualification contractuelle ou d'affectation.

---

## 9. VERSIONNEMENT RÉEL DES SNAPSHOTS POSTGRESQL (TEST V1 / V2)

Le test d'intégration transactionnel réel [`testSnapshotVersioningV1V2PostgreSql`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/test/java/mg/galaxam/sirh/integration/pointage/Phase1cATemporalPostgreSqlIntegrationTest.java#L147-L260) valide le cycle complet :
1. **Création pointage initial :** 21:00 -> 05:00 (8h / 480 min).
2. **Gel initial (V1) :** Snapshot V1 créé (`version_snapshot = 1`, `statut_snapshot = 'GELE'`), 3 buckets persistés dans `pointage_snapshot_bucket`.
3. **Réouverture exceptionnelle :** Appel `degelerPeriode(...)` $\implies$ Snapshot V1 bascule en `statut_snapshot = 'REMPLACE'`.
4. **Modification pointage :** 21:00 -> 06:00 (9h / 540 min).
5. **Regel (V2) :** Snapshot V2 créé (`version_snapshot = 2`, `statut_snapshot = 'GELE'`, `remplace_id_cumul = idCumulV1`), 4 buckets persistés.
6. **Vérification d'immutabilité stricte :**
   - V1 et V2 coexistent en base avec des IDs distincts (`idCumulV1 != idCumulV2`).
   - Les buckets de V1 sont strictement inchangés (3 buckets, somme = 480 min).
   - Les buckets de V2 reflètent le nouveau pointage (4 buckets, somme = 540 min).
   - Aucune mutation rétroactive.

---

## 10. AUDIT D'ENCAPSULATION : `PayrollService.preparerInput`

### Diagnostic
La méthode `preparerInput(Integer idEmploye, Integer idPeriodePaie, boolean simulation, Connection c)` a été rendue `public` sur `PayrollService`.

### Justification architecturale
Cette méthode constitue une **API publique d'inspection pré-vol (pre-flight checks)** :
1. Elle permet d'assembler et d'auditer de façon idempotente la totalité du contexte de paie d'un collaborateur (pointage snapshoté qualifié, affectation carriere, fiscalité, déductions) sans déclencher d'exécution monétaire ni d'effets de bord transactionnels.
2. Elle est documentée avec une Javadoc explicite clarifiant son statut d'API de lecture/assemblage pré-vol.
3. Sa visibilité `public` est donc pleinement légitime et conservée.

---

## 11. INVARIANTS DE CONSERVATION DU TEMPS

Pour l'ensemble des scénarios et tests validés :

$$\sum \text{WorkInterval.minutes} = \sum \text{TemporalBucket.minutes} = \sum \text{QualifiedTimeBucket.minutes}$$

Et pour chaque tronçon hebdomadaire :
$$\sum \text{Qualified REGULAR} = \text{Phase1B.regularMinutes}$$
$$\sum \text{Qualified HS30} = \text{Phase1B.overtime30Minutes}$$
$$\sum \text{Qualified HS50} = \text{Phase1B.overtime50Minutes}$$

- **Aucune minute perdue.**
- **Aucune minute créée.**
- **Aucun double comptage.**

---

## 12. CHECKLIST DE CONTRE-AUDIT DU GATE

- [x] Aucune limite 16h restante sans règle métier
- [x] Aucune autre durée maximale inventée (18h, 24h, etc.)
- [x] Sortie < entrée est fidèlement reconstruite en J+1 (cross-midnight)
- [x] Pointage incomplet n'invente jamais une heure
- [x] `WorkIntervalFactory` reste un reconstructeur temporel pur
- [x] Anomalies restent sous la responsabilité du workflow Pointage
- [x] Provenance réelle des HS démontrée par 5 scénarios contradictoires
- [x] Dimanche et jour férié coexistent sans écrasement (`SUNDAY_AND_PUBLIC_HOLIDAY`)
- [x] Dimanche + férié + nuit peuvent coexister
- [x] HS + dimanche + férié + nuit peuvent coexister
- [x] Aucune politique financière ni valorisation monétaire n'est introduite en 1C-A
- [x] `NIGHT` n'est jamais arbitrairement étiqueté `HABITUEL`
- [x] Snapshot V1 survit intact et immuable lors du regel en V2
- [x] `PayrollAttendanceInput` conserve toutes les qualifications
- [x] Aucun recalcul 40h en Phase 1C-A
- [x] Aucun fallback silencieux
- [x] Tests backend PASS (367/367)
- [x] Tests frontend PASS (16/16)
- [x] Build frontend PASS
- [x] `git diff --check` PASS

---

## 13. VERDICT ET TRANSITION VERS PHASE 1C-B

```text
========================================================================
VERDICT : PHASE_1C_A_CORRECTED_PASS
========================================================================
```

Le socle temporel et calendaire est parfaitement assaini, conforme à la réalité du terrain et prêt pour la **Phase 1C-B** (valorisation monétaire, règles d'articulation des majorations et bulletin de paie).
