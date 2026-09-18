# RAPPORT OFFICIEL DE CLÔTURE — PHASE 2A-1 : ALIGNEMENT ET CORRECTION DU PAYROLL CORE

**Date :** 17 septembre 2026  
**Auteur :** Antigravity AI Pair Programmer  
**Statut du sprint :** `PHASE_2A_1_ALIGNMENT_PASS`  
**Conformité légale & conventionnelle :** Code du Travail Malgache, Accord d'établissement GALAXAM, Méthode Ohatra Kajy canonique.

---

## 1. BASELINE ET RÉSULTATS DE VALIDATION

| Suite de vérification | Baseline Avant (2A-0) | Résultat Après (2A-1) | Statut |
| :--- | :--- | :--- | :--- |
| **Backend Unit & Integration Tests** | 402 PASS | **429 PASS** (0 Failure, 0 Error) | **PASS** |
| **Frontend Tests (Vitest)** | 16 PASS | **16 PASS** (3 test files, 16 tests) | **PASS** |
| **PostgreSQL Integration Tests** | PASS | **PASS** (Spring Boot + PostgreSQL réel) | **PASS** |
| **Frontend Build (Vite production)** | PASS | **PASS** (dist/ généré sans warning bloquant) | **PASS** |
| **Contrôle Whitespace & Git Diff** | PASS | **PASS** (`git diff --check` = 0) | **PASS** |

---

## 2. SYNTHÈSE DES CORRECTIONS ET ALIGNEMENTS APPLIQUÉS

L'objectif de Phase 2A-1 a été de corriger les incohérences et dettes identifiées lors de l'audit contradictoire Phase 2A-0 pour faire du chemin d'exécution existant la **source de vérité canonique unique** sans multiplication artificielle de classes :

```text
Contrat de travail & Carrière
   ↓
Salaire contractuel de base
   ↓
Prorata calendaire /30 (tronçon CONTRAT ∩ PÉRIODE)
   ↓
Absences en temps (snapshot Pointage canonique en minutes/heures)
   ↓
Congés payés (Déduction 1/30ème + Maintien TCONGE historique)
   ↓
HS & Majorations spéciales (OvertimePayCalculator — Phase 1C)
   ↓
Primes & ajustements opérationnels
   ↓
TOTAL GAINS BRUTS (Explicable, Déterministe, Immuable)
```

---

## 3. DÉTAIL TECHNIQUE DES ALIGNEMENTS RÉALISÉS

### 3.1 Prorata Temporis Entrée / Sortie en cours de période
* **Règle canonique :** Prorata par jour calendaire avec diviseur fixe **30**.
  $$\text{Salaire de base payé} = \text{Salaire contractuel} \times \frac{\text{Jours calendaires sous contrat dans la période}}{30}$$
* **Calcul des jours sous contrat :** Câblage dynamique dans `PayrollService.preparerInput` :
  $$\text{effectiveStart} = \max(\text{contrat.dateDebut}, \text{periode.dateDebut})$$
  $$\text{effectiveEnd} = \min(\text{contrat.dateFin}, \text{affectation.dateFin}, \text{periode.dateFin})$$
  $$\text{joursSousContrat} = \text{ChronoUnit.DAYS.between}(\text{effectiveStart}, \text{effectiveEnd}) + 1$$
* **Suppression définitive du fallback horaire :** La branche morte qui reconstruisait silencieusement le salaire par `heuresReelles * tauxHoraire` a été supprimée de `PayrollExecutionEngine.calculerSalaireBase`. Si un prorata est requis (`entreeSortieEnCoursDeMois == true`) sans `joursSousContrat` valide, le moteur lève explicitement `IllegalStateException("PAYROLL_CONTRACT_PERIOD_DATA_MISSING: prorata requis mais joursSousContrat absent ou invalide")`.
* **Couverture complète :** Si le salarié couvre tout le mois, son salaire contractuel plein est conservé sans aucun prorata.

### 3.2 Absences Décomptées en Temps & Valorisation Horaire
* **Unité et source canonique :** Minutes et heures réelles consommées depuis le snapshot scellé (`pointage_cumul_periode.heures_absence_retenues`).
* **Suppression du fallback `joursAbsence * 8` :** Dans `PayrollService.preparerInput`, le bloc qui multipliait artificiellement les jours d'absence par 8.0 a été supprimé. Le snapshot Pointage fournit les heures exactes d'absence.
* **Suppression du mode legacy `TRENTIEME` :** La retenue pour absence est désormais calculée exclusivement selon la formule canonique :
  $$\text{Retenue Absence} = \text{nbHeuresAbsence} \times \text{tauxHoraire}$$
  avec plafonnement au salaire de base de la ligne. Les branches mortes de `TRENTIEME` ont été retirées du moteur.

### 3.3 Résolution de la Dette KAJY (Absence vs Congé)
* **Preuve contradictoire :** Dans le modèle Pointage canonique, un jour de congé validé (`nb_jours_conge`) et un jour d'absence injustifiée (`nb_jours_absence`) sont déjà mutuellement exclusifs.
* **Correction appliquée :** Dans `PayrollService.mettreAJourJournalConge`, la formule legacy issue d'Excel `jAbs.subtract(jCge).max(BigDecimal.ZERO)` a été remplacée par `jAbs.max(BigDecimal.ZERO)`.
* **Matrice de test validée :**
  * $0\text{ abs} + 0\text{ cge} \implies 30\text{j formule} \implies 2.5000\text{ jours acquis}$
  * $2\text{ abs} + 0\text{ cge} \implies 28\text{j formule} \implies 2.3333\text{ jours acquis}$
  * $0\text{ abs} + 2\text{ cge} \implies 30\text{j formule} \implies 2.5000\text{ jours acquis}$
  * $2\text{ abs} + 2\text{ cge} \implies 28\text{j formule} \implies 2.3333\text{ jours acquis}$ (l'absence n'est plus effacée par le congé)
  * $1\text{ abs} + 10\text{ cge} \implies 29\text{j formule} \implies 2.4167\text{ jours acquis}$

### 3.4 Étanchéité Temporelle TCONGE & Grand-Livre des Congés
* **Faille temporelle corrigée :** La méthode `StcService.getTauxJournalierDernier` exécutait un `ORDER BY annee DESC, mois DESC LIMIT 1` sans borne temporelle, risquant de lire des valeurs futures lors du recalcul d'une paie passée.
* **Nouvelle méthode canonique :**
  `StcService.getTauxJournalierApplicable(int idEmploye, LocalDate dateReference, BigDecimal salaireBaseFallback, Connection c)`
  Filtrage SQL strict :
  ```sql
  SELECT taux_journalier_conge FROM conge_journal_droit
  WHERE id_employe = ? AND (annee < ? OR (annee = ? AND mois < ?))
  ORDER BY annee DESC, mois DESC LIMIT 1
  ```
* **Amorçage défini et explicite :** Si aucun enregistrement antérieur n'existe (nouveau salarié / premier mois), la méthode applique la règle d'amorçage canonique : $\text{salaireBase} / 30.0$. Si le salaire de base est nul ou indisponible, elle lève `LEAVE_RATE_INITIALIZATION_REQUIRED`.
* **Preuve d'étanchéité :** Validée par test d'intégration PostgreSQL avec insertion d'une valeur future (ex: Octobre à 40 000 Ar). La paie de Septembre lit strictement Août (24 000 Ar) et ne peut jamais être polluée par le futur.

### 3.5 Décomposition Modulaire de `PayrollExecutionEngine`
Conformément au principe architectural de refus du sur-engineering, `PayrollExecutionEngine` reste l'orchestrateur avec 5 méthodes unitaires spécialisées :
1. `calculerSalaireBase` : Prorata temporis /30, contrôle des dates de période et d'affectation.
2. `calculerRetenueAbsence` : Valorisation horaire canonique décomptée en temps.
3. `calculerConges` : Déduction 1/30ème + Indemnisation TCONGE historique.
4. `calculerMajorationsEtHeuresSup` : Délégation canonique à `OvertimePayCalculator` (Phase 1C).
5. `calculerPrimes` : Primes de performance (avec seuils et malus), assiduité, ancienneté, exceptionnelle, régularisations et gains dynamiques.

Le brut global est calculé sans aucune duplication :
$$\text{TOTAL\_GAINS\_BRUTS} = \text{Base} - \text{RetenueAbsence} + \text{CongesNet} + \text{HSetMajorations} + \text{Primes}$$

### 3.6 Indemnités Pointage (Panier Repas & Transport)
* Les fallbacks de recalcul dans Paie (`forfaitRepas * joursPresents` et `forfaitSite * ratio`) ont été supprimés.
* Les indemnités de panier et de transport sont consommées directement depuis le snapshot scellé (`pointage_cumul_periode.montant_repas` et `montant_deplacement`), garantissant qu'aucun double propriétaire n'existe.

---

## 4. MATRICE AVANT / APRÈS (SECTION 46)

| Domaine | État Avant (Phase 2A-0) | État Après (Phase 2A-1) | Owner Runtime | Source de Données | Statut |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Salaire de base** | Prorata non câblé, fallback heures silencieux | Prorata calendaire `/30` câblé, exception explicite si dates manquantes | `PayrollExecutionEngine.calculerSalaireBase` | `contrat_travail` $\cap$ `periode_paie` | **CLEAN** |
| **Prorata temporis** | `NOT_WIRED` au runtime | `USED_CANONICAL` (bornes inclusives, diviseur fixe 30) | `PayrollService.preparerInput` $\to$ Engine | Intersection d'intervalles SQL | **CLEAN** |
| **Absence** | Fallback `jours * 8`, mode `TRENTIEME` actif | Décompte en heures/minutes, valorisation horaire canonique | `PayrollExecutionEngine.calculerRetenueAbsence` | `pointage_cumul_periode` (Snapshot) | **CLEAN** |
| **Dette KAJY** | `jAbs.subtract(jCge)` effaçait les absences | `jAbs.max(0)` : absences et congés strictement dissociés | `PayrollService.mettreAJourJournalConge` | Bulletin de paie / Pointage | **CLEAN** |
| **TCONGE** | Faille temporelle (`ORDER BY DESC LIMIT 1`) | Résolution temporelle stricte `(annee < ? OR (annee = ? AND mois < ?))` | `StcService.getTauxJournalierApplicable` | `conge_journal_droit` (Antérieur) | **CLEAN** |
| **Amorçage TCONGE** | Fallback silencieux non typé | Amorçage canonique explicite `Base / 30` ou `INITIALIZATION_REQUIRED` | `StcService.getTauxJournalierApplicable` | Salaire de base contractuel | **CLEAN** |
| **Heures Sup / Nuit / Dimanche / Férié** | Canonique (Phase 1C) | Inchangé, consommé sans duplication | `OvertimePayCalculator` | `QualifiedTimeBucket` (Snapshot) | **CLEAN** |
| **Indemnités repas / transport** | Fallback de recalcul concurrent dans Paie | Consommation directe du snapshot sans recalcul | `PayrollExecutionEngine` | `pointage_cumul_periode` | **CLEAN** |
| **Taux horaire** | Duplication `tauxHoraire` vs `getTauxHoraireEffectif` | Source canonique unique : `input.getTauxHoraireEffectif()` (`Base / 173.33`) | `PayrollCalculationInput` | Configuration légale / Convention | **CLEAN** |

---

## 5. COUVERTURE DES TESTS DE CONTRÔLE

1. **`Phase2a1PayrollCoreAlignmentUnitTest` (25 tests unitaires) :**
   * Matrice KAJY complète (5 cas de collision absence/congé).
   * 11 tests de frontière prorata (entrée 1er jour, 2e jour, milieu, dernier jour, sortie 1er jour, milieu, dernier jour, débordement, contrat hors période, interdiction du fallback).
   * 5 tests de valorisation des absences en temps (0h, 1h, 1.5h/90min, 8h/480min, plafonnement au salaire de base).
   * 3 tests congés & prorata combinés (amorçage trentième, TCONGE revalorisé par HS, prorata + congé sans double réduction).
   * Test de composition intégrale du brut avec toutes rubriques combinées.

2. **`Phase2a1PayrollCoreAlignmentIntegrationTest` (2 tests intégration Spring + PostgreSQL) :**
   * **Test 1 :** Étanchéité temporelle TCONGE avec grand-livre réel (Juin, Juillet, Août, Septembre, Octobre futur). Preuve que Septembre lit Août et ignore la valeur future d'Octobre.
   * **Test 2 :** Cycle production complet de bout en bout :
     `Employé` $\to$ `Contrat au 16 août` $\to$ `Affectation` $\to$ `Pointage avec HS30` $\to$ `Synthèse & Approbation (gel snapshot)` $\to$ `Calcul de paie REST` $\to$ `Bulletin en BDD`.
     Vérification en base : `SALAIRE_BASE = 320 000.00 Ar` (16j / 30), `HEURES_SUP_30 = 18 000.35 Ar`, `TOTAL_GAINS_BRUTS = 338 000.35 Ar`.

3. **`CalculsConformiteIntegraleTest` (29 tests unitaires) :**
   * Alignement des tests de prorata et d'absence sur les règles canoniques /30 et heures réelles.

4. **`Phase0CharacterizationTest` (12 tests unitaires) :**
   * Mise à jour du test de caractérisation démontrant la résolution de l'anomalie de proratisation.

---

## 6. CRITÈRES DE PASSAGE (CHECKLIST OFFICIELLE)

```text
[x] prorata calendaire /30 réellement câblé au runtime
[x] frontière de période respectée
[x] aucun fallback heuresReelles×taux pour reconstruire la base mensuelle
[x] absences consommées en temps depuis Pointage
[x] aucune reconstruction jours×8
[x] absence valorisée via taux horaire canonique
[x] mode TRENTIEME absent du runtime s'il est legacy
[x] KAJY résolu (les congés n'effacent jamais les absences)
[x] TCONGE ne lit jamais le futur
[x] TCONGE n'utilise aucun fallback silencieux non validé
[x] congé et absence restent deux domaines distincts
[x] Phase 1C non recalculée
[x] OvertimePayCalculator reste owner canonique
[x] Payroll ne recalcule pas les indemnités Pointage
[x] une seule source active du taux horaire
[x] aucune duplication SQL/Frontend
[x] aucun nouveau sur-engineering de classes
[x] tests unitaires PASS (429/429 Backend)
[x] test runtime Spring + PostgreSQL PASS
[x] frontend tests PASS (16/16)
[x] frontend build PASS
[x] git diff --check PASS
```

---

## 7. VERDICT DU SPRINT

```text
=================================================================
  VERDICT : PHASE_2A_1_ALIGNMENT_PASS
=================================================================
```

Le Payroll Core existant est désormais assaini, rigoureusement aligné sur les règles canoniques, historiquement étanche et validé sur PostgreSQL réel. Le système est prêt pour les phases ultérieures de cotisations sociales (Phase 2B) et de fiscalité IRSA/ISI (Phase 2C).
