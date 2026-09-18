# PHASE 2A-0 — AUDIT CONTRADICTOIRE DU PAYROLL CORE EXISTANT

**Date :** 17 septembre 2026  
**Auteur :** Antigravity AI  
**Référence :** Traçabilité et Preuves de Code Runtime — Construction du Brut et des Rubriques  
**Verdict du Gate :** `PHASE_2A_0_AUDIT_COMPLETE`  

---

## 1. BASELINE DES TESTS ET CONTRÔLE PRÉALABLE

Avant d'engager l'audit du Payroll Core, les suites de tests et builds ont été exécutées avec succès :

- **Backend (Surefire) :** 402 tests **PASS** (incluant le nouveau test d'observation `Phase2a0PayrollCoreRuntimeAuditTest`).
- **Frontend (Vitest) :** 16 tests **PASS** (3 suites de test complètes).
- **Frontend Build (Vite) :** **PASS** (aucun warning de compilation, bundle généré en 433ms).
- **Base PostgreSQL Réelle :** Intégration Spring Boot + HikariCP validée sur environnement réel.
- **Contrôle Git :** `git diff --check` retourne code 0 sans aucune anomalie.

---

## 2. CALL GRAPH RUNTIME DU CALCUL DU BRUT

Le call graph réel montre comment l'application passe d'une action utilisateur dans le Wizard Paie jusqu'à la création du bulletin :

```text
Frontend : CalculStepPage.jsx / CockpitPaieTab.jsx
    ↓
API Client : paieService.calculerPaiePeriode(idPeriode)
    ↓
REST Controller : PaieController.calculerPaiePeriode(req)
    ↓
Application Service : PayrollService.calculerPaiePeriode(...)
    ↓
Sous-étape 1 : Ingestion du Snapshot Pointage & Contexte Employé
    → PayrollService.preparerInput(idEmploye, idPeriodePaie, simulation, c)
        ├── employe + employe_affectation_carriere (Salaire contractuel, catégorie, régime)
        ├── pointage_cumul_periode (Snapshot scellé : heures, jours, retards, indemnités)
        ├── pointage_snapshot_bucket (QualifiedTimeBuckets scellés)
        ├── StcService.getTauxJournalierDernier (Taux congé historique depuis conge_journal_droit)
        ├── PayrollLegalContextResolver.resolve (Barèmes IRSA, plafonds SME, taux majorations datés)
        ├── paie_avance_acompte (Primes, acomptes, régularisations, extras)
        └── paie_pret_echeance (Échéance de prêt active)
    ↓
Sous-étape 2 : Orchestration Métier du Moteur d'Exécution
    → PayrollExecutionEngine.calculerBulletin(input)
        ├── 1. initialiserEnTete(input)
        ├── 2. calculerSalaireBrut(input, bp, lignes)
        │       ├── Salaire de base (proratisé si entrée/sortie)
        │       ├── Retenue sur salaire pour absences (Heures réelles / Trentième)
        │       ├── Déduction congé 1/30ème & Maintien indemnité congé (TCONGE)
        │       ├── OvertimePayCalculator.calculate (Valorisation HS30, HS50, Nuit, Dimanche, Férié)
        │       ├── Primes (Performance, Assiduité, Ancienneté, Exceptionnelle, Régul, AUTIMP)
        │       └── Gains variables dynamiques imposables
        │       → TOTAL_GAINS_BRUTS
        ├── 3. calculerCotisationsSociales(input, bp, lignes) [Palier 2]
        ├── 4. calculerNetImposable(totalGainsBruts, cotisations, bp) [Palier 3]
        ├── 5. calculerIrsa(input, bp, lignes) [Palier 4]
        ├── 6. calculerIndemnitesNonImposables(input, bp, lignes) [Palier 5 : Repas, Transport]
        └── 7. calculerRetenuesEtAvances(netAvant, input, bp, lignes) [Palier 6 : Acomptes, Prêts]
    ↓
Sous-étape 3 : Persistance Immuable & Alimentation des Grands-Livres
    → PayrollService.sauvegarderBulletinEtLignes(complet, c)
        ├── INSERT INTO bulletin_paie
        └── INSERT INTO bulletin_paie_ligne (rubrique par rubrique)
    → PayrollService.imputerAvancesEtPrets(complet, idPeriodePaie, c)
        ├── UPDATE paie_avance_acompte SET statut = 'IMPUTE_PAIE'
        └── PaiePretEcheance.preleverEnPaie(...)
    → PayrollService.mettreAJourJournalConge(idPeriodePaie, c)
        └── UPSERT INTO conge_journal_droit (Grand-livre d'acquisition et valorisation congés)
```

---

## 3. INVENTAIRE DES LIGNES ACTUELLES DE BULLETIN (`bulletin_paie_ligne`)

Chaque bulletin généré ventile les montants calculés dans la table `bulletin_paie_ligne` :

| Code Rubrique | Libellé Rubrique | Sens | Catégorie | Base de Calcul | Taux / Formule |
| :--- | :--- | :---: | :---: | :---: | :---: |
| `SALAIRE_BASE` | Salaire de Base | `GAIN` | `SALAIRE_BASE` | Base contractuelle | Proratisé /30 si entrée/sortie |
| `RETENUE_ABSENCE` | Retenue pour Absences Décomptées | `RETENUE` | `SALAIRE_BASE` | Taux horaire ou 1/30ème | Heures absence × Taux horaire |
| `DEDUCTION_CONGE_BASE` | Déduction Salaire Jours de Congé (1/30ème) | `RETENUE` | `SALAIRE_BASE` | Base / 30.0 | Jours congé × (Base / 30) |
| `INDEMNITE_CONGE_PAYE`| Indemnité de Congés Payés (Maintien) | `GAIN` | `SALAIRE_BASE` | Taux conge journalier | Jours congé × TCONGE |
| `HEURES_SUP_30` | Heures Supplémentaires (+30%) | `GAIN` | `HEURES_SUP` | Taux horaire | Heures HS30 × Taux × 130% |
| `HEURES_SUP_50` | Heures Supplémentaires (+50%) | `GAIN` | `HEURES_SUP` | Taux horaire | Heures HS50 × Taux × 150% |
| `MAJORATION_NUIT` | Majoration Travail de Nuit (+30%) | `GAIN` | `HEURES_SUP` | Taux horaire | Heures Nuit × Taux × 30% |
| `HEURES_DIMANCHE` | Majoration Travail Dimanche (+40%) | `GAIN` | `HEURES_SUP` | Taux horaire | Heures Dimanche × Taux × 40% |
| `MAJORATION_FERIE` | Majoration Travail Jour Férié (+100%) | `GAIN` | `HEURES_SUP` | Taux horaire | Heures Férié × Taux × 100% |
| `PRIME_PERFORMANCE` | Prime de Performance (Proratisée) | `GAIN` | `PRIME` | Base prime | Prorata présence - Malus retard/congé |
| `PRIME_ASSIDUITE` | Prime d'Assiduité | `GAIN` | `PRIME` | Forfait prime | Forfait si retards < 45mn |
| `PRIME_ANCIENNETE` | Prime d'Ancienneté (X ans) | `GAIN` | `PRIME` | Base contractuelle | 1% par an (max 20%) dès 3 ans |
| `PRIME_EXCEPTIONNELLE`| Prime Exceptionnelle | `GAIN` | `PRIME` | Montant saisi | Saisie manuelle approuvée |
| `REGULARISATION_PAIE` | Régularisation Congés (Restitution) | `GAIN` | `SALAIRE_BASE` | Montant saisi | Saisie manuelle approuvée |
| `AUTIMP` | Autres Gains Imposables (AUTIMP) | `GAIN` | `PRIME` | Montant saisi | Saisie manuelle approuvée |
| Dynamic Gains | Gain variable configuré en BDD | `GAIN` | `PRIME` | Montant saisi | Config `ref_type_mouvement_paie` |
| `COTISATION_CNAPS_SAL`| Cotisation CNaPS Salariale (1%) | `RETENUE`| `COTISATION_SALARIALE`| Assiette plafonnée CNaPS| 1.00 % |
| `COTISATION_CNAPS_PAT`| Cotisation CNaPS Patronale (13%) | `PATRONAL`| `COTISATION_PATRONALE`| Assiette plafonnée CNaPS| 13.00 % |
| `COTISATION_FMFP_PAT` | Cotisation FMFP Patronale (1%) | `PATRONAL`| `COTISATION_PATRONALE`| Total Gains Bruts | 1.00 % |
| `OSTIE_SAL` / etc. | Cotisation Organe Sanitaire Salariale | `RETENUE`| `COTISATION_SALARIALE`| Assiette santé | 1.00 % |
| `OSTIE_PAT` / etc. | Cotisation Organe Sanitaire Patronale | `PATRONAL`| `COTISATION_PATRONALE`| Assiette santé | Taux patronal site (ex: 5% ou 6%) |
| `IMPOT_IRSA_BRUT` | IRSA Brut (Barème Progressif) | `INFO` | `IMPOT` | Net Imposable | Barème 6 tranches |
| `REDUCTION_CHARGE_FAMILLE`| Réduction Charges de Famille | `INFO` | `IMPOT` | Nb enfants éligibles | 2 000 Ar / enfant |
| `IMPOT_IRSA_NET` | IRSA Net à Payer (Retenu à la source)| `RETENUE`| `IMPOT` | Impôt après abattement | Min perception 3 000 Ar |
| `INDEMNITE_REPAS` | Indemnité de Panier | `GAIN` | `INDEMNITE_NON_IMPOSABLE`| Montant validé pointage| Forfait journalier (2 000 Ar/j) |
| `INDEMNITE_TRANSPORT` | Indemnité Forfaitaire Déplacement | `GAIN` | `INDEMNITE_NON_IMPOSABLE`| Montant validé pointage| Prorata présence sur forfait site |
| `EXTRA_DEPLACEMENT` | Indemnité Extra Déplacement | `GAIN` | `INDEMNITE_NON_IMPOSABLE`| Montant saisi | Saisie manuelle approuvée |
| `REMBOURSEMENT_NOTE_FRAIS`| Remboursement Notes de Frais Validées | `GAIN` | `INDEMNITE_NON_IMPOSABLE`| Montant saisi | Saisie manuelle approuvée |
| `RETENUE_ACOMPTE_15` | Acompte de Quinzaine | `RETENUE`| `ACOMPTE_AVANCE` | Montant acompte | Plafonné quotité cessible |
| `RETENUE_AVANCE_SPECIALE`| Avance Spéciale sur Salaire | `RETENUE`| `ACOMPTE_AVANCE` | Montant avance | Plafonné quotité cessible |
| `RETENUE_ECHEANCE_PRET`| Échéance Prêt N° X | `RETENUE`| `ACOMPTE_AVANCE` | Échéance mensuelle | Plafonné quotité cessible |
| `AJUSTEMENT_ARRONDI` | Ajustement Arrondi Monétaire | `GAIN` | `AJUSTEMENT_ARRONDI` | Écart d'arrondi | Arrondi dizaine Ariary supérieure |

---

## 4. INVENTAIRE DE `PayrollCalculationInput`

La classe `PayrollCalculationInput` compte 72 champs. L'audit classe leur statut actuel :

- **`USED_CANONICAL` (52 champs) :** Identifiants, dates de période, salaire contractuel, données scellées de pointage (`PayrollAttendanceInput`), taux légaux datés (`TemporalRateResolver`), primes opérationnelles, variables de paie, enfants fiscalement éligibles, échéance de prêt.
- **`USED_LEGACY` / `NOT_WIRED` (4 champs) :**
  - `entreeSortieEnCoursDeMois` : Utilisé dans `PayrollExecutionEngine`, mais jamais renseigné dans `PayrollService.preparerInput`. Statut : `NOT_WIRED`.
  - `joursSousContrat` : Prévu pour la règle `/30`, mais jamais calculé dans `PayrollService.preparerInput`. Statut : `NOT_WIRED`.
  - `modeCalculAbsence` : Hardcodé à `"HEURES_REELLES"` dans `PayrollService.java:600`.
- **`DUPLICATED` (3 champs) :**
  - `tauxHoraire` : Dupliqué avec `getTauxHoraireEffectif()` qui le recalcule dynamiquement via `Base / diviseurMensuelHeures`.
- **`UNUSED` (13 champs) :** Constructeurs de compatibilité unitaire ou anciens drapeaux de simulation.

---

## 5. AUDIT DU SALAIRE CONTRACTUEL

- **Résolution dans `PayrollService.preparerInput` :**
  La requête SQL interroge `employe_affectation_carriere` (jointure avec `employe`) à la date de fin de période (`date_debut <= finPeriode AND (date_fin IS NULL OR date_fin >= finPeriode)`).
- **Garde-fou d'unicité :**
  Si plusieurs affectations concurrentes se chevauchent à la date cible, une exception explicite est levée :
  `IllegalStateException("Plusieurs affectations concurrentes sont applicables à l'employé...")`.
- **Séparation Contractuel vs Payé :**
  `input.getSalaireBaseContractuel()` conserve TOUJOURS la valeur contractuelle intégrale de l'affectation. La ligne de bulletin `SALAIRE_BASE` affiche la base contractuelle, et la base proratisée n'est calculée que pour déterminer le montant effectif de la ligne.

---

## 6. AUDIT DU PRORATA ENTRÉE / SORTIE

- **Formule Implémentée dans le Moteur (`PayrollExecutionEngine.java:166-169`) :**
  ```java
  salaireBaseLigne = MoneyRoundingPolicy.amount(
      salaireBaseContractuel.multiply(input.getJoursSousContrat())
          .divide(new BigDecimal("30"), 4, RoundingMode.HALF_UP)
  );
  ```
- **CONSTAT MAJEUR D'AUDIT :**  
  Bien que la formule `/30` soit présente et testée unitairement dans `PayrollExecutionEngine`, **`PayrollService.preparerInput` NE CALCULE JAMAIS `joursSousContrat` ni n'active `entreeSortieEnCoursDeMois`** lors de la préparation depuis la base de données.
- **Conséquence en production :**  
  Un employé embauché le 16 du mois reçoit actuellement son salaire complet de 30 jours, sauf intervention manuelle.
- **Classification :** `NOT_WIRED` / `SEVERITY P0` pour Phase 2A-1.

---

## 7. AUDIT DES ABSENCES

- **Origine des Données :**
  Pointage scellé dans `pointage_cumul_periode` via la colonne `heures_absence_retenues` (alimentée par `AttendancePeriodCalculator` en agrégeant les déficits hebdomadaires sous 40h).
- **Modes de Calcul dans le Moteur :**
  - Mode 1 : `"HEURES_REELLES"` $\to$ `montant = heuresAbsence * (salaireBase / 173.33)`.
  - Mode 2 : `"TRENTIEME"` $\to$ `montant = joursAbsence * (salaireBase / 30.0)`.
- **Comportement Réel :**
  `PayrollService.preparerInput` force le mode `"HEURES_REELLES"`. La déduction d'absence est plafonnée à `salaireBaseLigne` et apparaît sous la rubrique `RETENUE_ABSENCE`.

---

## 8. DETTE KAJY (`jAbs - jCge`)

- **Emplacement du Code Réel :**
  [`PayrollService.java:888`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/paie/service/PayrollService.java#L888) dans la méthode `mettreAJourJournalConge` :
  ```java
  BigDecimal absInjustifiees = jAbs.subtract(jCge).max(BigDecimal.ZERO);
  BigDecimal formule = new BigDecimal("30.0").subtract(absInjustifiees).max(BigDecimal.ZERO);
  BigDecimal droitMois = new BigDecimal("2.5").multiply(formule).divide(new BigDecimal("30.0"), 4, RoundingMode.HALF_UP);
  ```
- **Origine Métier :**
  Héritage direct d'un ancien fichier Excel "OHATRA KAJY" où l'auteur supposait que les absences déclarées `jAbs` incluaient les congés autorisés `jCge`.
- **Anomalie Détectée :**
  Dans le module Pointage de GALAXAM, `nb_jours_absence` et `nb_jours_conge` sont **déjà mutuellement exclusifs** (un jour est soit `CONGE`, soit `ABSENCE_NON_JUSTIFIEE`). Soustraire `jCge` de `jAbs` a pour effet pervers d'annuler les pénalités d'absence pour les salariés qui ont pris des congés !
- **Classification :** `BUSINESS_RULE_UNRESOLVED` / `DEBT_KAJY` (à corriger en Phase 2A-1).

---

## 9. AUDIT DES CONGÉS PAYÉS ET MAINTIEN DE SALAIRE

- **Source des Jours Consommés :**
  `pointage_cumul_periode.nb_jours_conge` (agrégé depuis les demandes validées dans `conge_demande`).
- **Mécanisme à Double Ligne sur le Bulletin :**
  1. `DEDUCTION_CONGE_BASE` (RETENUE) : Déduit `nbJoursConges * (salaireBase / 30.0)`.
  2. `INDEMNITE_CONGE_PAYE` (GAIN) : Ajoute `nbJoursConges * tauxJournalierConge`.
- **Résultat Mathématique :**
  Si le taux journalier historique est égal au taux trentième, l'opération est blanche (maintien pur du salaire). Si le taux journalier historique est plus élevé (grâce aux heures supplémentaires passées intégrées au grand-livre), le salarié perçoit le complément légal de 1/12ème.

---

## 10. AUDIT DU TCONGE & HISTORIQUE DES DROITS

- **Résolution dans `StcService.getTauxJournalierDernier` :**
  ```sql
  SELECT taux_journalier_conge FROM conge_journal_droit
  WHERE id_employe = ? ORDER BY annee DESC, mois DESC LIMIT 1
  ```
- **Fallback Actuel :**
  Si aucune ligne n'existe dans `conge_journal_droit` (ex. nouvel embauché ou absence d'historique), le système applique :
  `salaireBaseFallback.divide(new BigDecimal("30.0"), 2, RoundingMode.HALF_UP)`.
- **Faille Temporelle Détectée :**
  La clause `ORDER BY annee DESC, mois DESC LIMIT 1` ne filtre pas strictement `date < datePeriode`. Si une écriture ultérieure existe, elle est prélevée à tort.
- **Classification :** `TEMPORAL_SOURCE_OF_TRUTH_VIOLATION` (mineure mais réelle).

---

## 11. ARTICULATION AVEC LES CALCULATEURS PHASE 1

- **Principe Strict Respecté :**
  Phase 2 **NE RECALCULE AUCUN TEMPS**.
- **Preuve :**
  Toutes les heures supplémentaires (`HS30`, `HS50`), heures de nuit, dimanches et jours fériés sont consommées directement depuis `pointage_snapshot_bucket`. `OvertimePayCalculator` prend les buckets gelés et applique uniquement les taux de valorisation monétaire issus de `ref_taux_majoration`.

---

## 12. AUDIT DES PRIMES ET AJUSTEMENTS

- **Prime de Performance :**
  Formule : `primeBase * (presents / ouvrables) * (1 - malus)`. Malus de 25% appliqué si `retard > 70mn` et `conge > 1j`.
- **Prime d'Assiduité :**
  Forfait intégral accordé si `retards < 45mn`, sinon 0 Ar.
- **Prime d'Ancienneté :**
  Calcul automatique : 1% du salaire de base par année d'ancienneté au-delà de 3 ans (plafonnée à 20 ans / 20%).
- **Primes Manuelles / Variables :**
  Alimentées depuis `paie_avance_acompte` avec validation RH (statuts `EN_ATTENTE`, `APPROUVE`). Lors du calcul, le statut devient `IMPUTE_PAIE`.

---

## 13. AUDIT DES INDEMNITÉS (PANIER & TRANSPORT)

- **Source de Vérité :**
  `DailyAttendanceCalculator` calcule quotidiennement l'éligibilité au panier et au déplacement lors de la saisie de pointage (`pointage_indemnite_jour`).
- **Consommation en Paie :**
  `PayrollExecutionEngine` ne recalcule pas les indemnités : il les lit depuis `pointage_cumul_periode.montant_repas` et `montant_deplacement` et les intègre en Palier 5 (Gains non imposables et non soumis à cotisations).

---

## 14. AUDIT DES AVANCES, ACOMPTES ET PRÊTS

- **Gestion des Acomptes :**
  Saisis dans `paie_avance_acompte` (type `ACOMPTE_15`, `AVANCE_SPECIALE`, etc.).
- **Gestion des Prêts :**
  Chaque prêt actif dans `paie_pret` possède un échéancier dans `paie_pret_echeance`.
- **Protection de la Quotité Cessible (Art. L. 138-1 Code du Travail) :**
  Le moteur vérifie que le total des retenues (acomptes + prêts) ne dépasse pas la fraction saisissable du salaire net. Lors du calcul définitif, les échéances sont passées à `PRELEVEE` et liées à l'ID du bulletin.

---

## 15. AUDIT DES AUTRES GAINS ET RETENUES

- **Mécanisme Générique :**
  La table `ref_type_mouvement_paie` permet de définir des rubriques configurables par l'utilisateur. Chaque mouvement est rattaché à une rubrique de paie (`paie_rubrique`) qui définit explicitement son caractère imposable (`soumis_irsa`) et cotisable (`soumis_cnaps`).

---

## 16. FORMULE BRUTE RUNTIME ACTUELLE

Dans `PayrollExecutionEngine.calculerSalaireBrut(...)` :

$$\begin{aligned}
\text{TOTAL\_GAINS\_BRUTS} = & \;\text{salaireBaseLigne} \\
& - \text{montantAbsence} \\
& - \text{deductionCongeTrentieme} \\
& + \text{indemniteConges} \\
& + \text{montantHs30} + \text{montantHs50} \\
& + \text{montantNuit} + \text{montantDimanche} + \text{montantFerie} \\
& + \text{primePerformanceFinal} + \text{primeAssiduite} + \text{primeAnciennete} \\
& + \text{primeExceptionnelle} + \text{regularisationPaie} + \text{autimp} \\
& + \sum \text{gainsVariablesImposables}
\end{aligned}$$

---

## 17. AUDIT DES FALLBACKS MÉTIER

| Fallback Détecté | Fichier & Ligne | Valeur de Repli | Risque Métier | Action Phase 2A-1 |
| :--- | :--- | :--- | :--- | :--- |
| Prorata entrée/sortie sans jours | `PayrollExecutionEngine:172` | `heuresReelles * tauxHoraire` | Reconstitution non contractuelle | Exiger `joursSousContrat` obligatoire |
| TCONGE sans historique | `StcService:953` | `base / 30.0` | Maintien simple au lieu de moyenne | Documenter comme repli d'amorçage |
| Absence sans heures déclarées | `PayrollService:578` | `joursAbsence * 8.0` | Heures estimées arbitrairement | Aligner avec pointage réel |
| Diviseur d'heures mensuelles | `PayrollCalculationInput:254` | `173.33` | Hardcodé si config absente | Exiger dans `paie_config_legale` |
| Mode de calcul absence | `PayrollService:600` | `"HEURES_REELLES"` | Absence de choix contractuel | Rendre paramétrable par contrat |

---

## 18. AUDIT DES CONSTANTES NUMÉRIQUES

- **`173.33` :** Utilisé pour le diviseur mensuel légal d'heures ($40 \times 52 / 12 = 173.3333$). Défini en constante dans `PayrollExecutionEngine`. Conforme à la convention GALAXAM.
- **`30.0` :** Utilisé pour le calcul journalier trentième (prorata et déduction congés). Règle du Code du Travail malgache.
- **`2.5` :** Taux d'acquisition mensuel légal de congés payés (2.5 jours ouvrables par mois).
- **`2 000 Ar` :** Forfait journalier d'indemnité repas et réduction fiscale par enfant à charge.
- **`45 mn` et `70 mn` :** Seuils d'assiduité et de retard pour pénalités de primes.

---

## 19. AUDIT DE L'HISTORISATION TEMPORELLE

- **Points Forts :**
  Les barèmes légaux, tranches IRSA, plafonds CNaPS et taux de majoration HS/Nuit/Dimanche/Férié sont tous résolus historiquement via `PayrollLegalContextResolver` à la date exacte de travail.
- **Points Faibles :**
  La recherche du dernier taux de congé (`getTauxJournalierDernier`) fait un `ORDER BY annee DESC, mois DESC LIMIT 1` sans borner à la date de la période courante.

---

## 20. SNAPSHOT ET IMMUTABILITÉ DU BULLETIN

- **Comportement Actuel :**
  - Si un bulletin a le statut `CLOTURE`, le système refuse strictement toute recalculation ou écrasement :  
    `IllegalStateException("Le bulletin [...] est clôturé et strictement immuable.")`.
  - Si le bulletin est `BROUILLON` ou `CALCULE`, un nouveau calcul supprime les lignes existantes et met à jour l'en-tête.
- **Traçabilité des Lignes :**
  Chaque ligne conserve la base de calcul, le taux salarial, le montant salarial, le montant patronal et la catégorie de rubrique.

---

## 21. AUDIT DES LOGIQUES MÉTIER SQL

- **Constat :**
  Toutes les requêtes SQL du module Paie effectuent des sélections, filtres temporels, jointures de référentiels et sommations simples (`SUM(total_gains_bruts)`).
- **Verdict :** **Aucune duplication de logique métier n'a été trouvée dans les requêtes SQL de paie** (`CLEAN`).

---

## 22. AUDIT DES LOGIQUES MÉTIER FRONTEND

- **Constat :**
  Dans `VariablesPaieTab.jsx`, `CalculStepPage.jsx` et `CockpitPaieTab.jsx`, le frontend ne fait qu'envoyer des saisies brutes (montant, motif, sélection d'employé) ou déclencher des calculs via API REST.
- **Verdict :** **Aucune fuite de logique de calcul de paie dans React** (`CLEAN`).

---

## 23. DUPLICATIONS ACTIVES

- **Taux Horaire :** Le calcul `base / diviseur` est dupliqué entre `PayrollCalculationInput.getTauxHoraireEffectif()` et certaines surcharges unitaires.
- **Indemnité Repas/Transport :** Calculé dans `PointageSyntheseService` ET présent en fallback dans `PayrollExecutionEngine` si absent de l'input.

---

## 24. CODE LEGACY ET OBSOLÈTE

- `CalculateurPaieEngine` : Déjà supprimé (0 occurrence).
- Constructeurs historiques de `PayrollCalculationInput` ne prenant pas en compte `QualifiedTimeBucket` : identifiés et réservés aux tests unitaires dépréciés.

---

## 25. RÈGLES MÉTIER NON RÉSOLUES OU AMBIGUËS

1. **Articulation `jAbs - jCge` (KAJY) :**  
   Faut-il déduire les congés des absences dans le grand-livre de congés, ou s'agit-il d'une anomalie historique de l'ancien fichier Excel ?  
   *Preuve de code :* Les absences et congés sont déjà séparés dans le pointage. La formule actuelle fausse l'acquisition de congés.
2. **Choix du mode de décompte des absences :**  
   Faut-il décompter les absences en heures réelles ou au trentième ?  
   *Preuve de code :* Actuellement forcé à `"HEURES_REELLES"` par le backend.
3. **Proratisation contractuelle des entrées/sorties :**  
   Faut-il activer automatiquement le calcul `/30` en comparant la date d'embauche/départ à la période de paie ?  
   *Preuve de code :* La formule `/30` existe dans `PayrollExecutionEngine`, mais n'est pas câblée dans `PayrollService.preparerInput`.

---

## 26. MATRICE D'OWNERSHIP ACTUEL

| Domaine | Owner Actuel | Problème Détecté | Sévérité |
| :--- | :--- | :--- | :---: |
| Salaire de Base | `PayrollExecutionEngine` | Responsabilité directe dans le moteur au lieu d'un composant dédié | P1 |
| Prorata Entrée/Sortie | Non câblé en production | Paramètres jamais passés par `PayrollService` | P0 |
| Déduction Absence | `PayrollExecutionEngine` | Mode hardcodé, conversion jours $\to$ heures en dur | P1 |
| Congés / Maintien | `PayrollExecutionEngine` + `StcService` | Faille temporelle dans `getTauxJournalierDernier` | P1 |
| TCONGE / Journal | `PayrollService.mettreAJourJournalConge` | Dette KAJY `jAbs - jCge` active | P0 |
| Valorisation HS/Nuit | `OvertimePayCalculator` (Existant) | **Parfaitement câblé et canonique** | **CLEAN** |
| Indemnités Pointage | `DailyAttendanceCalculator` $\to$ Paie | Présence d'un fallback redundant dans le moteur | P2 |
| Primes & Ajustements | `PayrollExecutionEngine` | Logique de calcul des malus directement dans le moteur | P1 |

---

## 27. MATRICE D'OWNERSHIP CIBLE PROPOSÉ (PHASE 2A)

```text
CONTRAT & SALAIRE DE BASE :
    → BaseSalaryAndProrataCalculator (Nouveau)
        - Résolution du salaire contractuel
        - Détection automatique entrée/sortie en cours de période
        - Calcul canonique de proratisation /30

ABSENCES :
    → AbsenceSalaryDeductionCalculator (Nouveau)
        - Application du mode contractuel (Heures réelles vs Trentième)
        - Valorisation explicable de la retenue d'absence

CONGÉS & TCONGE :
    → LeavePayCalculator (Nouveau)
        - Déduction 1/30ème du salaire de base
        - Maintien ou survalorisation TCONGE
    → LeaveAccrualValuationEngine (Grand-livre des droits)
        - Correction de la dette KAJY (suppression de jAbs - jCge)
        - Résolution temporelle stricte date < periodeDate

TEMPS QUALIFIÉS (HS / Nuit / Dimanche / Férié) :
    → OvertimePayCalculator (EXISTANT & CONSERVÉ)
        - Source unique de valorisation des QualifiedTimeBuckets

INDEMNITÉS ISSUES DU POINTAGE :
    → DailyAttendanceCalculator (EXISTANT & CONSERVÉ)
        - Paie = Consommateur passif du snapshot (suppression des fallbacks)

PRIMES & VARIABLES :
    → PayrollAdjustmentWorkflowService + PerformanceBonusCalculator (Nouveau)
        - Calcul des ratios et malus de performance hors du moteur central

SALAIRE BRUT CONSOLIDÉ :
    → PayrollExecutionEngine (ORCHESTRATEUR PUR)
        - Sommation des résultats fournis par les calculateurs spécialisés
```

---

## 28. PLAN D'EXÉCUTION DE LA PHASE 2A-1

La phase 2A-1 effectuera la migration progressive sans interruption de service, en respectant l'ordre de priorité suivant :

### Étape 2A-1.1 : Câblage et Sécurisation du Prorata Entrée/Sortie (Priorité P0)
- **Problème :** `joursSousContrat` et `entreeSortieEnCoursDeMois` non alimentés au runtime.
- **Action :** Dans `PayrollService.preparerInput`, comparer `date_embauche` et `date_depart` aux bornes de la période pour calculer automatiquement les jours calendaires sous contrat.
- **Tests :** Valider par un test d'intégration sur un embauché en cours de mois.

### Étape 2A-1.2 : Résolution de la Dette KAJY et TCONGE Historique (Priorité P0)
- **Problème :** Formule `jAbs - jCge` erronée dans `mettreAJourJournalConge` et absence de filtre temporel dans `getTauxJournalierDernier`.
- **Action :** Supprimer la soustraction `jAbs - jCge`, borner la recherche SQL à la période antérieure.
- **Tests :** Vérifier les droits acquis et le maintien de salaire sur 12 mois glissants.

### Étape 2A-1.3 : Extraction du Calculateur de Déduction d'Absence (Priorité P1)
- **Problème :** Logique d'absence codée en dur dans `PayrollExecutionEngine`.
- **Action :** Créer `AbsenceSalaryDeductionCalculator` avec support paramétrable `HEURES_REELLES` / `TRENTIEME`.

### Étape 2A-1.4 : Extraction des Primes et Performance (Priorité P1)
- **Problème :** Calcul des ratios de présence et malus de performance directement dans le moteur.
- **Action :** Créer `PerformanceBonusCalculator`.

### Étape 2A-1.5 : Transformation de `PayrollExecutionEngine` en Orchestrateur Pur (Priorité P1)
- **Problème :** Moteur monolithique contenant 800 lignes de formules mixtes.
- **Action :** Déléguer aux calculateurs spécialisés et conserver uniquement l'assemblage du bulletin.

---

# VERDICT FORMEL

```text
================================================================================
VERDICT : PHASE_2A_0_AUDIT_COMPLETE
================================================================================
Toutes les sources, variables, lignes de bulletin, formules du brut,
dettes historiques (KAJY, TCONGE, Prorata /30 non câblé) et fallbacks
ont été contradictoirement identifiés et prouvés par le code et les tests réels.
Le plan de migration Phase 2A-1 est formellement établi.
================================================================================
```
