# RAPPORT D'EXÉCUTION — SPRINT PHASE 1C-A

## Moteur de Qualification Temporelle & Cross-Midnight
**GALAXAM-SIRH — Pointage → Timeline Qualifiée**  
**Date :** 17 Septembre 2026  
**Auteur :** Antigravity AI Engine (Pair Programming avec GALAXAM)  
**Verdict :** `PHASE_1C_A_PASS`

---

## 1. Baseline Initiale

* **Backend Spring Boot** : 338/338 tests passés avec succès
* **Frontend React** : 16/16 tests passés avec succès
* **Build Frontend** : Production build Vite 100% PASS
* **Base de données PostgreSQL** : Opérationnelle sur port 5432 (`sirh_galaxam` et `sirh_galaxam_test`)
* **Dettes bloquantes pré-1C** : 0
* **Vérification Git** : `git diff --check` PASS (zéro espace superflu, zéro conflit)

---

## 2. Cartographie Avant Modification

L'architecture avant le sprint Phase 1C-A :
```text
UI Saisie (PointageGridTable)
       ↓
PointageSaisieController / Service
       ↓
pointage_jour (PostgreSQL)
       ↓
DailyAttendanceCalculator (minutes travaillées, retards, indemnités)
       ↓
PeriodWeekSegmenter / WeeklyAttendanceCalculator (objectif réel, surplus, compensation, HS30/HS50 par tronçon Phase 1B)
       ↓
PointageSyntheseService.gelerPeriode
       ↓
pointage_cumul_periode (snapshot scalaire versionné)
       ↓
PayrollAttendanceInput (contrat immuable consommé par PayrollService / PayrollExecutionEngine)
```

### Constats d'audit sur l'existant :
1. **Propriétaire des volumes HS** : Phase 1B (`WeeklyAttendanceCalculator`) reste le propriétaire unique et incontestable du calcul des volumes `REGULAR`, `OVERTIME_30`, `OVERTIME_50` par tronçon hebdomadaire.
2. **Lacune temporelle comblée par 1C-A** : Les heures de nuit, dimanche, férié et les vacations traversant minuit n'étaient calculées qu'au niveau journalier ou par sommation scalaire brute sans traçabilité des intersections multidimensionnelles (`HS30 + NUIT`, `HS50 + DIMANCHE + NUIT`).
3. **Absence d'intervalles canoniques continus** : Les heures de sortie inférieures aux heures d'entrée utilisaient ponctuellement `minutes < 0 ? minutes + 1440 : minutes` sans objet `LocalDateTime` continu ni validation stricte d'inversion aberrante.

---

## 3. Fichiers Modifiés et Créés

### Fichiers Créés :
1. [`backend/src/main/java/mg/galaxam/sirh/model/pointage/calculation/OvertimeTier.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/pointage/calculation/OvertimeTier.java) : Enum `REGULAR`, `OVERTIME_30`, `OVERTIME_50`.
2. [`backend/src/main/java/mg/galaxam/sirh/model/pointage/calculation/DaySpecialType.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/pointage/calculation/DaySpecialType.java) : Enum `STANDARD`, `SATURDAY`, `SUNDAY`, `PUBLIC_HOLIDAY`.
3. [`backend/src/main/java/mg/galaxam/sirh/model/pointage/calculation/NightWorkType.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/pointage/calculation/NightWorkType.java) : Enum `NONE`, `NIGHT_UNCLASSIFIED`, `HABITUEL`, `OCCASIONNEL`.
4. [`backend/src/main/java/mg/galaxam/sirh/model/pointage/calculation/WorkInterval.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/pointage/calculation/WorkInterval.java) : Value object immuable (`start`, `end`, `employeeId`, `pointageId`, `sourceRef`) avec invariant `end > start`.
5. [`backend/src/main/java/mg/galaxam/sirh/model/pointage/calculation/WorkIntervalFactory.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/pointage/calculation/WorkIntervalFactory.java) : Usine de reconstruction d'intervalles continus avec détection cross-midnight et garde anti-aberration (plafond 16h).
6. [`backend/src/main/java/mg/galaxam/sirh/model/pointage/calculation/TemporalBucket.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/pointage/calculation/TemporalBucket.java) : Segment temporel homogène intermédiaire avec contrôle d'exactitude à la minute près.
7. [`backend/src/main/java/mg/galaxam/sirh/model/pointage/calculation/QualifiedTimeBucket.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/pointage/calculation/QualifiedTimeBucket.java) : Entité canonique portant à la fois la qualification HS (Phase 1B) et les faits temporels (Phase 1C-A).
8. [`backend/src/main/java/mg/galaxam/sirh/model/pointage/calculation/AttendanceTemporalPartitioner.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/pointage/calculation/AttendanceTemporalPartitioner.java) : Découpeur d'intervalles aux frontières 22h, 00h et 05h, calendrier et frontières de période 25/26.
9. [`backend/src/main/java/mg/galaxam/sirh/model/pointage/calculation/OvertimeProjectionEngine.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/pointage/calculation/OvertimeProjectionEngine.java) : Moteur de projection chronologique sans recalcul des 40h avec scission exacte aux frontières de palier.
10. [`backend/conception/bdd/2026-09-17_phase_1c_a_snapshot_bucket.sql`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/conception/bdd/2026-09-17_phase_1c_a_snapshot_bucket.sql) : Migration SQL pour la table `pointage_snapshot_bucket`.
11. [`backend/src/test/java/mg/galaxam/sirh/unit/pointage/WorkIntervalFactoryTest.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/test/java/mg/galaxam/sirh/unit/pointage/WorkIntervalFactoryTest.java) : Tests W01 à W04.
12. [`backend/src/test/java/mg/galaxam/sirh/unit/pointage/AttendanceTemporalPartitionerTest.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/test/java/mg/galaxam/sirh/unit/pointage/AttendanceTemporalPartitionerTest.java) : Tests T01 à T10.
13. [`backend/src/test/java/mg/galaxam/sirh/unit/pointage/OvertimeProjectionEngineTest.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/test/java/mg/galaxam/sirh/unit/pointage/OvertimeProjectionEngineTest.java) : Tests anti-régression 40h, transitions et intersections.
14. [`backend/src/test/java/mg/galaxam/sirh/integration/pointage/Phase1cATemporalPostgreSqlIntegrationTest.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/test/java/mg/galaxam/sirh/integration/pointage/Phase1cATemporalPostgreSqlIntegrationTest.java) : Test d'intégration bout en bout PostgreSQL.

### Fichiers Modifiés :
1. [`backend/conception/bdd/05_schema_pointage.sql`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/conception/bdd/05_schema_pointage.sql) : Intégration de la table `pointage_snapshot_bucket` dans le schéma de référence.
2. [`backend/src/main/java/mg/galaxam/sirh/model/paie/dto/PayrollAttendanceInput.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/paie/dto/PayrollAttendanceInput.java) : Enrichissement avec `List<QualifiedTimeBucket> qualifiedTimeBuckets` immuable avec compatibilité 100% ascendante.
3. [`backend/src/main/java/mg/galaxam/sirh/model/pointage/service/PointageSyntheseService.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/pointage/service/PointageSyntheseService.java) : Persistance des buckets lors du gel et fourniture de `getSnapshotBuckets`.
4. [`backend/src/main/java/mg/galaxam/sirh/model/paie/service/PayrollService.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/paie/service/PayrollService.java) : Chargement des buckets lors de la préparation des inputs de paie et passage de `preparerInput` en visibilité publique.
5. [`frontend/src/components/pointage/PointageGridTable.jsx`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/components/pointage/PointageGridTable.jsx) : Détection visuelle et affichage du badge `+1j` avec tooltip contextuelle sur les sorties du lendemain.

---

## 4. Problèmes Découverts et Résolus

1. **Nom des colonnes dans `pointage_jour`** : Les colonnes réelles en BDD sont `heure_entree_am`, `heure_sortie_am`, et `horaire_continu_jour` (et non `heure_entree_apres_midi`, etc.). Corrigé immédiatement dans `PointageSyntheseService`.
2. **Application de la migration sur les bases de données** : La table `pointage_snapshot_bucket` a été appliquée à la fois sur la base opérationnelle `sirh_galaxam` et sur la base de test `sirh_galaxam_test`, ainsi qu'ajoutée au schéma maître `05_schema_pointage.sql`.
3. **Visibilité de `preparerInput` dans `PayrollService`** : Méthode rendue publique afin de permettre l'audit unitaire et l'inspection de l'input de paie sans déclencher d'effets de bord de facturation.

---

## 5. Description Détaillée des Composants

### 5.1 `WorkInterval` et `WorkIntervalFactory`
* Invariants : `start != null`, `end != null`, `end.isAfter(start)`.
* Pas de `payrollPeriodId` arbitraire rattaché à l'intervalle brut car un intervalle continu peut franchir minuit et chevaucher deux périodes (25/26).
* Respect de la pause déjeuner : une journée standard 08:00-12:00 / 13:00-17:00 produit deux `WorkInterval` indépendants (240 min chacun, total 480 min). La pause n'est jamais comptabilisée comme du travail.
* Détection cross-midnight si `heureSortie < heureEntree` : `endDate = startDate.plusDays(1)`.
* Garde anti-erreur : toute durée supérieure à 16h (960 min) lève une exception explicite `DUREE_VACATION_ABERRANTE` (ex: 08:00 à 05:00 = 21h rejeté).

### 5.2 `AttendanceTemporalPartitioner`
Découpe tout `WorkInterval` aux frontières strictes :
* `22:00` : Début de nuit légale.
* `00:00` : Minuit (changement calendaire, frontière samedi/dimanche, férié, période 25/26).
* `05:00` : Fin de nuit légale.
* Attribution horaire : `NightWorkType.NIGHT_UNCLASSIFIED` pour toute minute dans `[22:00, 05:00[`, `NightWorkType.NONE` en journée.
* Attribution calendaire : `PUBLIC_HOLIDAY` > `SUNDAY` > `SATURDAY` > `STANDARD`.
* Résolution de période : chaque sous-segment résout sa période de paie via `periodResolver(segmentDate)`.

### 5.3 `OvertimeProjectionEngine`
* **Contrat de responsabilité** :
  * Phase 1B décide **COMBIEN** de minutes sont `REGULAR`, `OVERTIME_30`, `OVERTIME_50` sur l'objectif réel du tronçon.
  * Phase 1C-A décide **OÙ** elles se situent chronologiquement sur la timeline.
* **Interdiction absolue du seuil 40h en 1C-A** : aucun recalcul `workedMinutes > 2400`. Si l'objectif réel du tronçon est 1920 min (32h) et que le salarié a fait 2100 min, Phase 1B donne R=1920 et HS30=180. 1C-A projette exactement 1920 min REGULAR et 180 min HS30, même si 2100 < 2400.
* **Scission lors des transitions** : si un bucket de 120 min de nuit franchit le reliquat du quota régulier (90 min), le bucket est scindé en deux : 90 min `REGULAR + NIGHT` et 30 min `OVERTIME_30 + NIGHT`.
* Invariant de conservation : $\sum \text{QualifiedTimeBucket.minutes} == \text{regularMinutes} + \text{overtime30Minutes} + \text{overtime50Minutes}$. Toute disparité lève une `IllegalStateException` explicite.

### 5.4 Persistance Immuable et Versionnée (`pointage_snapshot_bucket`)
* Clé étrangère stricte vers `pointage_cumul_periode(id_cumul)`.
* Colonnes persistées : `id_snapshot_bucket`, `id_cumul`, `id_pointage`, `date_debut`, `date_fin`, `minutes`, `overtime_tier`, `day_special_type`, `night_work_type`, `id_periode_paie`.
* Nature append-only : une réouverture de période marque le cumul précédent comme `REMPLACE`, garantissant qu'aucun snapshot scellé n'est altéré rétroactivement.
* Préservation intégrale des intersections : le calcul de paie en Phase 1C-B pourra valoriser directement chaque combinaison sans perte d'information.

### 5.5 Adaptation Frontend (`PointageGridTable.jsx`)
* L'utilisateur saisit une seule vacation continue (ex: 21:00 et 05:00).
* Le frontend détecte automatiquement `heureSortie < heureEntree` et affiche un badge compact `+1j` à côté de l'heure de sortie.
* Tooltip contextuelle : `Sortie le lendemain à HH:mm`.
* Zéro logique financière ou légale dans React : l'affichage reste piloté par le backend.

---

## 6. Synthèse des Tests Exécutés

### Tests Unitaires & Intégration Phase 1C-A :
* **`WorkIntervalFactoryTest`** (4 tests) :
  * W01 : Journée avec pause $\to$ 2 intervalles distincts (480 min).
  * W02 : Vacation de nuit 21h-05h $\to$ cross-midnight valide (480 min).
  * W03 : Inversion aberrante 08h-05h $\to$ rejet avec exception.
  * W04 : Début identique à la fin 08h-08h $\to$ rejet d'intervalle nul.
* **`AttendanceTemporalPartitionerTest`** (10 tests) :
  * T01 : 09h-17h $\to$ 1 bucket diurne standard (480 min).
  * T02 : 21h-23h $\to$ bascule 22h : 1h DAY + 1h NIGHT.
  * T03 : 23h-02h $\to$ minuit en pleine nuit : 1h NIGHT (J) + 2h NIGHT (J+1).
  * T04 : 04h-07h $\to$ bascule 05h : 1h NIGHT + 2h DAY.
  * T05 : 21h-05h $\to$ 1h DAY + 2h NIGHT + 5h NIGHT (480 min).
  * T06 : Samedi 21h $\to$ Dimanche 05h : Samedi 21h-22h SAT/DAY, Samedi 22h-00h SAT/NIGHT, Dimanche 00h-05h SUN/NIGHT.
  * T07 : Dimanche 21h $\to$ Lundi 05h : Dimanche 21h-22h SUN/DAY, Dimanche 22h-00h SUN/NIGHT, Lundi 00h-05h STD/NIGHT (aucune contamination).
  * T08 : Veille férié 21h $\to$ Férié 05h : bascule stricte à minuit vers PUBLIC_HOLIDAY.
  * T09 : Férié 21h $\to$ Lendemain 05h : fin férié stricte à minuit.
  * T10 : 25/MM 21h $\to$ 26/MM 05h : bascule période M $\to$ période M+1 à minuit.
* **`OvertimeProjectionEngineTest`** (5 tests) :
  * Section 43 : Test anti-régression seuil 40h (R=1920, HS30=180 sur 2100 min travaillées $\to$ REGULAR=1920, HS30=180).
  * Section 44 : Scission 22h-00h de nuit (90 min REGULAR + NIGHT, 30 min HS30 + NIGHT).
  * Section 45 : Scission exacte palier HS30 vers HS50.
  * Section 47 : Préservation des intersections (SUNDAY + NIGHT + REGULAR / OVERTIME_30).
  * Section 48 : Échecs explicites sur discordance de volume ou quotas négatifs.
* **`Phase1cATemporalPostgreSqlIntegrationTest`** (1 test) :
  * Pointage réel PostgreSQL 21:00 $\to$ 05:00.
  * Gel du snapshot et vérification des 3 lignes dans `pointage_snapshot_bucket`.
  * Rechargement des buckets via `PayrollAttendanceInput`.
  * Exactitude des champs, durées et qualifications confirmée.

### Validation Globale de Régression :
* **Backend Maven Surefire** : **358 / 358 tests PASS** (augmentation de 338 à 358 tests, zéro régression).
* **Frontend Vitest** : **16 / 16 tests PASS**.
* **Frontend Build Vite** : **PASS** (zéro erreur de build ou de bundle).
* **Vérification Git** : `git diff --check` **PASS**.

---

## 7. Statuts des Points Normatifs & Arbitrages

1. **Seuil 40h** : Recalcul formellement banni du moteur Phase 1C-A. Les volumes HS proviennent exclusivement de Phase 1B.
2. **Classification Nuit** : Toute heure de nuit est strictement qualifiée en `NIGHT_UNCLASSIFIED`. Aucune affectation arbitraire en `HABITUEL` par défaut (réservé à l'arbitrage RH GALAXAM).
3. **Prorata et Frontière 25/26** : Minuit découpe systématiquement les vacations chevauchantes. Les minutes de chaque côté sont rattachées à leur période respective sans transport de compteurs.
4. **Valorisation Monétaire (Phase 1C-B)** : Aucune formule financière (1.30, 1.50, MAX_PREMIUM, montants en Ariary) n'a été introduite dans ce sprint. Le socle est prêt pour l'exécution financière en Phase 1C-B.

---

## 8. Verdict Final

```text
========================================================================
             VERDICT SPRINT PHASE 1C-A : PHASE_1C_A_PASS
========================================================================
- WorkInterval & Cross-Midnight Réel          : VALIDÉ (tests unitaires W01-W04)
- Partitionnement Temporel (22h, 00h, 05h)    : VALIDÉ (tests unitaires T01-T10)
- Samedi, Dimanche, Férié à Minuit            : VALIDÉ (zéro contamination)
- Frontière 25/26 Découpée à Minuit           : VALIDÉ (aucun transport de compteur)
- Projection Chronologique Phase 1B           : VALIDÉ (zéro recalcul 40h)
- Scission aux Frontières de Palier           : VALIDÉ (invariants stricts)
- Persistance pointage_snapshot_bucket        : VALIDÉ (PostgreSQL réel testé)
- Enrichissement PayrollAttendanceInput       : VALIDÉ (immuable, compatibilité 100%)
- Adaptation UI Frontend (+1j)                : VALIDÉ (PointageGridTable.jsx)
- Tests Backend (358/358 PASS)                : VALIDÉ
- Tests Frontend (16/16 PASS)                 : VALIDÉ
- Build Frontend (Vite)                       : VALIDÉ
- Git Check                                   : VALIDÉ
========================================================================
```
