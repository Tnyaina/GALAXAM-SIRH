# RAPPORT D'IMPLÉMENTATION SPRINT PHASE 1C-B — VALORISATION MONÉTAIRE DES TEMPS QUALIFIÉS
**Date :** 17 Septembre 2026  
**Système :** GALAXAM-SIRH (Socle Paie & Valorisation Monétaire des Temps Qualifiés)  
**Auteur :** Antigravity AI — Pair Programming Audit & Architecture  
**Verdict :** `PHASE_1C_B_PASS`

---

## 1. BASELINE ET RÉSULTATS D'EXÉCUTION

| Composant | Résultat | Détail |
|---|---|---|
| **Backend Spring Boot** | **390 / 390 PASS** (+23 tests) | 0 échec, 0 erreur, 0 sauté (durée : 2 min 18 s) |
| **Frontend React (Vitest)** | **16 / 16 PASS** | 3 suites de tests, 0 régression |
| **Build Frontend (Vite)** | **PASS** | Bundle de production généré en 366 ms |
| **PostgreSQL Integration** | **PASS** | Test transactionnel complet de reproductibilité historique |
| **`git diff --check`** | **PASS** | Aucun whitespace error, intégrité absolue |

---

## 2. CARTOGRAPHIE AVANT MODIFICATION

| Élément | Propriétaire Initial | Statut / Décision | Justification |
|---|---|---|---|
| Calcul HS forfaitaire | `OvertimePayCalculator` (sur totaux d'heures) | **À FAIRE ÉVOLUER** | Ne valorisait que des agrégats grossiers (`heuresHs30`, etc.) sans traçabilité des buckets. |
| Multi-qualification | `calculateCombinedHourRate` | **À CONSERVER & GÉNÉRALISER** | Portait déjà le principe anti-double-base, étendu à la valorisation bucket par bucket. |
| Résolution des taux | `RefTauxMajoration.resolveApplicable` | **À CONSERVER** | Déjà strictement conforme (0 config $\to$ erreur, >1 config $\to$ erreur). |
| Collision Dimanche/Férié | `max(dimanche, ferie)` implicite | **À REMPLACER** | Décision arbitraire sans mandat RH. Remplacée par `SpecialDayCollisionPolicy.UNRESOLVED` par défaut. |
| Nuit non classifiée | Non traitée au niveau monétaire | **À ENCADRER** | Risque de valorisation silencieuse. Remplacé par `NIGHT_CLASSIFICATION_REQUIRED`. |
| Chargement des buckets | `PayrollService.loadSnapshotBuckets` | **CORRIGÉ** | L'exception SQL était avalée silencieusement ; désormais propagée avec rigueur. |

---

## 3. OWNER CANONIQUE RETENU : `OvertimePayCalculator`

Aucun deuxième calculateur redondant (`TemporalPremiumCalculator`, etc.) n'a été créé.  
[`OvertimePayCalculator`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/paie/engine/OvertimePayCalculator.java) demeure **l'unique propriétaire canonique** de la valorisation monétaire de l'ensemble des sujétions temporelles :
- Heures supplémentaires (HS30 / HS50)
- Travail de nuit (habituel / occasionnel)
- Dimanche travaillé
- Jour férié chômé travaillé
- Combinaisons et collisions multi-qualifiées

---

## 4. MODÈLE DE VALORISATION UNITAIRE EXPLICABLE

Chaque segment unitaire `QualifiedTimeBucket` produit un objet explicable [`BucketValuationResult`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/paie/engine/BucketValuationResult.java) :

```java
public record BucketValuationResult(
    QualifiedTimeBucket sourceBucket,
    BigDecimal hours,
    BigDecimal hourlyRate,
    BigDecimal baseAmount,
    BigDecimal overtimeRate,
    BigDecimal overtimeAmount,
    BigDecimal nightRate,
    BigDecimal nightAmount,
    BigDecimal sundayRate,
    BigDecimal sundayAmount,
    BigDecimal holidayRate,
    BigDecimal holidayAmount,
    BigDecimal totalAmount,
    String explanation
)
```

Chaque montant est mathématiquement justifiable par sa formule, ses taux applicables, son bucket d'origine et son snapshot source.

---

## 5. RÉSOLUTION STRICTE DES TAUX DANS `ref_taux_majoration`

La résolution s'appuie sur `RefTauxMajoration.resolveApplicable(idConfigLegale, type, date, connection)` :
- **0 configuration trouvée** $\implies$ Exception explicite : `MAJORATION_CONFIGURATION_INVALID: <type> applicable=0`
- **>1 configurations concurrentes** $\implies$ Exception explicite : `MAJORATION_CONFIGURATION_INVALID: <type> applicable=N`
- **Aucun taux codé en dur** : aucun `orElse(0.30)` ni fallback silencieux dans le chemin canonique de valorisation.

---

## 6. GESTION HISTORIQUE ET REPRODUCTIBILITÉ ABSOLUE

- Les taux sont résolus à la date d'effet de la période de paie calculée (`dateFinPeriode`).
- Une modification future d'un taux dans `ref_taux_majoration` (ex. réforme au 01/01/2027) n'affecte en rien le calcul d'une période antérieure (ex. Septembre 2026).
- Prouvé par le test PostgreSQL réel [`Phase1cBPostgreSqlValuationIntegrationTest`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/test/java/mg/galaxam/sirh/integration/paie/Phase1cBPostgreSqlValuationIntegrationTest.java).

---

## 7. CALCUL DES HEURES SUPPLÉMENTAIRES (HS)

- **Source canonique :** le tier reçu du bucket (`OvertimeTier.OVERTIME_30` ou `OvertimeTier.OVERTIME_50`). Phase 1C-B ne recalcule ni les 40h ni les dépassements.
- **Formule HS :**
  $$\text{Montant HS30} = \text{Heures} \times \text{TauxHoraire} \times (1.00 + r_{\text{hs30}})$$
  $$\text{Montant HS50} = \text{Heures} \times \text{TauxHoraire} \times (1.00 + r_{\text{hs50}})$$
  avec $r_{\text{hs30}} = 0.30$ et $r_{\text{hs50}} = 0.50$ issus de `ref_taux_majoration`.
- Pour les heures `REGULAR`, l'incrément HS est de 0.00.

---

## 8. CALCUL DU TRAVAIL DE NUIT

- **Règle d'or anti-double base :** Le travail de nuit est une majoration pour sujétion. La base horaire (1.00) est déjà rémunérée soit dans le `SALAIRE_BASE` mensuel (pour les heures `REGULAR`), soit dans la ligne `HEURES_SUP` (pour les heures supplémentaires).
- **Formule Nuit :**
  $$\text{Montant Nuit} = \text{Heures} \times \text{TauxHoraire} \times r_{\text{nuit}}$$
  avec $r_{\text{nuit}} = 0.30$ (`HABITUEL`).
  Aucune base 1.00 supplémentaire n'est ajoutée.

---

## 9. CALCUL DU DIMANCHE TRAVAILLÉ

- **Prédicat :** `bucket.isSunday() == true`.
- **Formule Dimanche :**
  $$\text{Montant Dimanche} = \text{Heures} \times \text{TauxHoraire} \times r_{\text{dimanche}}$$
  avec $r_{\text{dimanche}} = 0.40$ résolu depuis `ref_taux_majoration`.
- Lorsque les heures du dimanche sont qualifiées en heures supplémentaires par Phase 1B, la base horaire (1.00) est portée par la ligne HS, et la ligne Dimanche ne porte que l'incrément (+0.40).

---

## 10. CALCUL DU JOUR FÉRIÉ CHÔMÉ TRAVAILLÉ

- **Prédicat :** `bucket.isPublicHoliday() == true`.
- **Formule Férié :**
  $$\text{Montant Férié} = \text{Heures} \times \text{TauxHoraire} \times r_{\text{ferie}}$$
  avec $r_{\text{ferie}} = 1.00$ (+100 % selon la configuration GALAXAM en vigueur).

---

## 11. PRINCIPE CRITIQUE ANTI-DOUBLE-BASE

Chaque heure physique travaillée est rémunérée selon la décomposition stricte :

$$\text{Rémunération} = \text{TauxHoraire} \times \left( \text{Base (1.00 si hors salaire mensuel)} + \Delta_{\text{HS}} + \Delta_{\text{Nuit}} + \Delta_{\text{Dimanche/Férié}} \right)$$

### Exemple vérifié par le test unitaire V22 :
Pour 1 heure physique qui est simultanément `HS30` et `Nuit Habituelle` :
- Base horaire : $2\,000 \times 1.00 = 2\,000\text{ Ar}$
- Incrément HS30 : $2\,000 \times 0.30 = 600\text{ Ar}$
- Incrément Nuit : $2\,000 \times 0.30 = 600\text{ Ar}$
- **Total exact :** $3\,200\text{ Ar}$ (Facteur 1.60)
- **Sanction :** Une implémentation naïve additionnant deux taux complets ($1.30 + 1.30 = 2.60 \to 5\,200\text{ Ar}$) échoue strictement au test.

---

## 12. GESTION DES COLLISIONS CALENDAIRES (DIMANCHE + FÉRIÉ)

La gestion des collisions est régie par [`SpecialDayCollisionPolicy`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/paie/engine/SpecialDayCollisionPolicy.java) :
- `UNRESOLVED` **(par défaut)** : Le moteur refuse de trancher silencieusement et lève immédiatement une exception explicite :
  `SPECIAL_PREMIUM_COLLISION_UNRESOLVED: Le bucket [...] est simultanément DIMANCHE et JOUR FERIE sans politique de cumul/non-cumul configurée.`
- `MAX_PREMIUM` : Retient le taux le plus élevé ($\max(0.40, 1.00) = 1.00$).
- `CUMUL_ADDITIF` : Cumul favorable conventionnel ($0.40 + 1.00 = 1.40$).
- `HOLIDAY_ONLY` : Priorité stricte au jour férié chômé légal ($1.00$).

---

## 13. TRAITEMENT DE `NIGHT_UNCLASSIFIED`

- Tout bucket portant `nightType == NightWorkType.NIGHT_UNCLASSIFIED` qui parvient au moteur de valorisation déclenche une exception bloquante :
  `NIGHT_CLASSIFICATION_REQUIRED: Le bucket temporel [...] porte une qualification NIGHT_UNCLASSIFIED qui ne peut pas être monétisée sans arbitrage RH.`
- La classification peut être injectée via l'interface fonctionnelle [`NightWorkClassifier`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/paie/engine/NightWorkClassifier.java).
- En l'absence de classification contractuelle ou RH, le moteur refuse catégoriquement d'inventer une qualification `HABITUEL` par défaut.

---

## 14. INTÉGRATION AVEC `PayrollAttendanceInput`

- `PayrollAttendanceInput` transmet de façon immuable la collection de `QualifiedTimeBucket` scellée lors du gel de pointage.
- `OvertimePayCalculator.calculate(PayrollCalculationInput, BigDecimal)` inspecte la présence de ces buckets. S'ils sont présents, il applique la valorisation unitaire canonique.

---

## 15. INTÉGRATION AVEC `PayrollExecutionEngine`

- `PayrollExecutionEngine` conserve son rôle strict d'**orchestrateur de haut niveau**.
- Il ne contient aucune formule analytique `if sunday`, `if night`.
- Il consomme directement les composantes monétaires consolidées de `OvertimePayCalculator.Result` (`hs30()`, `hs50()`, `nuit()`, `dimanche()`, `ferie()`) pour alimenter les lignes de bulletin correspondantes.

---

## 16. PERSISTANCE, SNAPSHOT ET PROVENANCE

- Le snapshot Pointage (`pointage_cumul_periode`, `pointage_snapshot_bucket`) est scellé avant le calcul de paie.
- Le snapshot Paie conserve les totaux et détails nécessaires à la reproduction exacte du bulletin.
- Chaque montant élémentaire est rattachable à son bucket temporel, à son pointage d'origine et à son snapshot.

---

## 17. MIGRATIONS SQL

Aucune nouvelle table n'a été requise :
- La table `ref_taux_majoration` existait déjà avec les plages de validité `[date_debut, date_fin]`.
- La colonne `pointage_snapshot_bucket.day_special_type` a été adaptée en `VARCHAR(40)` lors du patch précédent pour accueillir `SUNDAY_AND_PUBLIC_HOLIDAY`.

---

## 18. TESTS UNITAIRES (SUITE `Phase1cBMonetaryValuationTest`)

22 tests unitaires exhaustifs ont été ajoutés et validés :
- **V01 :** REGULAR DAY $\to$ 0 Ar additionnel
- **V02 :** HS30 DAY $\to$ 8h à 130 % = 20 800 Ar
- **V03 :** HS50 DAY $\to$ 4h à 150 % = 12 000 Ar
- **V04 :** REGULAR NIGHT HABITUEL $\to$ 7h à +30 % = 4 200 Ar
- **V05 :** HS30 NIGHT HABITUEL $\to$ 2h à Facteur 1.60 = 6 400 Ar
- **V06 :** HS50 NIGHT HABITUEL $\to$ 2h à Facteur 1.80 = 7 200 Ar
- **V07 :** NIGHT OCCASIONNEL $\to$ valorisé au taux occasionnel (2h à 50% = 2 000 Ar)
- **V08 :** REGULAR SUNDAY $\to$ 8h à +40 % = 6 400 Ar
- **V09 :** HS30 SUNDAY $\to$ 4h à Facteur 1.70 = 13 600 Ar
- **V10 :** HS50 SUNDAY $\to$ 4h à Facteur 1.90 = 15 200 Ar
- **V11 :** REGULAR PUBLIC_HOLIDAY $\to$ 8h à +100 % = 16 000 Ar
- **V12 :** HS30 PUBLIC_HOLIDAY $\to$ 4h à Facteur 2.30 = 18 400 Ar
- **V13 :** HS50 PUBLIC_HOLIDAY $\to$ 4h à Facteur 2.50 = 20 000 Ar
- **V14 :** SATURDAY sans HS $\to$ 0 Ar
- **V15 :** SATURDAY + HS30 $\to$ 4h à 130 % = 10 400 Ar
- **V16 :** NIGHT_UNCLASSIFIED $\to$ lève `NIGHT_CLASSIFICATION_REQUIRED`
- **V17 :** SUNDAY + PUBLIC_HOLIDAY sans politique $\to$ lève `SPECIAL_PREMIUM_COLLISION_UNRESOLVED`
- **V18 :** HS30 + NIGHT + SUNDAY $\to$ Facteur 2.00 = 4 000 Ar
- **V19 & V20 :** Résolution des taux $\to$ rejet strict des configurations absentes ou multiples
- **V21 :** Férié tombant Dimanche avec politique `MAX_PREMIUM` $\to$ retient 100 %
- **V22 :** Test anti-double-base $\to$ 1.60 $\times$ Base validé contre 2.60 sanctionné
- **V23 :** Conservation monétaire et traçabilité par bucket

---

## 19. TESTS D'INTÉGRATION POSTGRESQL

Test [`Phase1cBPostgreSqlValuationIntegrationTest`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/test/java/mg/galaxam/sirh/integration/paie/Phase1cBPostgreSqlValuationIntegrationTest.java) :
- Exécution du cycle réel en base de données.
- Insertion d'une modification future de taux (`DIMANCHE` passant à 50% en 2027).
- Preuve que le calcul de la période de Septembre 2026 demeure **strictement inchangé à 40 %** : reproductibilité historique parfaite.

---

## 20. QUESTIONS MÉTIER OUVERTES POUR ARBITRAGE RH

1. **Classification Nuit :** Quelle source contractuelle ou structurelle GALAXAM doit porter l'attribut `HABITUEL` vs `OCCASIONNEL` (table `contrat_travail`, affectation de poste, ou statut d'équipe de nuit) ?
2. **Collision Dimanche + Férié :** Quelle est la politique conventionnelle retenue par GALAXAM pour un jour férié chômé tombant un dimanche (`MAX_PREMIUM` à +100 % ou `CUMUL_ADDITIF` à +140 %) ?
3. **Spécificités par Site / Convention d'Entreprise :** Existe-t-il des majorations locales dérogeant au barème national ?

---

## 21. DETTES HISTORIQUES CONSERVÉES POUR LA PHASE 2 (PAYROLL CORE)

Les dettes fonctionnelles suivantes sont documentées et sanctuarisées pour la Phase 2 :
- **TCONGE :** Élimination / arbitrage du fallback historique `BASE / 30`.
- **KAJY :** Clarification du calcul `jAbs - jCge`.
- **Plafond CNaPS :** Paramétrage dynamique de la constante historique.
- **Unification Paie / STC :** Harmonisation des calculateurs d'indemnités et de retenues.

---

## 22. VERDICT FINAL DU SPRINT

```text
========================================================================
VERDICT FINAL : PHASE_1C_B_PASS
========================================================================
```

Le moteur de valorisation monétaire est opérationnel, rigoureux, sans double base, explicable bucket par bucket et entièrement couvert par 390 tests backend, 16 tests frontend et l'intégration PostgreSQL réelle. Le socle est prêt pour la **Phase 2 : Payroll Core**.
