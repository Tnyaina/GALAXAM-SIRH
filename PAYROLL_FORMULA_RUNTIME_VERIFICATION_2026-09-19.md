# VÉRIFICATION CHIRURGICALE DU MOTEUR DE PAIE SIRH GALAXAM CONTRE LES FORMULES MÉTIER CANONIQUES & L'EXCEL HISTORIQUE
**Date du rapport** : 19 Septembre 2026  
**Auteur** : Antigravity IDE (Audit & Caractérisation SIRH Paie)  
**Verdict Global** : `PAYROLL_FORMULA_RUNTIME_VERIFICATION_PASS`

---

## 1. Executive Summary

Le présent audit chirurgical a confronté de bout en bout l'implémentation Java réelle du moteur de paie de GALAXAM SIRH (`PayrollExecutionEngine`, `IrsaCalculator`, `SocialContributionCalculator`, `MoneyRoundingPolicy`, `PayrollService`) aux formules métier réelles malgaches, aux unités dimensionnelles confirmées et aux données de référence issues des classeurs Excel de référence :
- `model/ohatra gita fichiers-23jan26.xlsx`
- `model/ohatra presence svt clocker.xlsx`

### Conclusions Majeures :
1. **Intégrité Dimensionnelle Java** : Le moteur Java respecte scrupuleusement la séparation stricte des unités :
   - `NBABS` est traité exclusivement en **HEURES** (ou minutes canoniques) sur diviseur horaire configurable ($173,33\text{ h}$).
   - `NBCONGE` est traité exclusivement en **JOURS** valorisés au taux journalier `TCONGE` ($\text{Ar/jour}$).
   - **Aucune soustraction absurde `NBABS - NBCONGE` (heures moins jours) n'existe dans le runtime Java.**
2. **Identification Définitive du Bug Excel Historique (Cas RAKETAKA)** :
   - L'Excel historique contenait une anomalie mathématique majeure en ligne 3 :
     - Présence de $11,52\text{ heures}$ d'absence et $11,5\text{ jours}$ de congé.
     - Formule hybride $D3 = \max(0, F3 - H3) = 11,52 - 11,5 = 0,02$ (soustraction illégitime d'heures et de jours).
     - La cellule financière d'absence affichait $240\,052,06\text{ Ar}$. La preuve mathématique démontre que ce montant correspondait à $11,5225 \times \frac{625\,000}{30}$ : l'auteur du tableur avait appliqué le diviseur journalier trentième ($/30$) à un nombre d'heures, valorisant l'absence comme s'il s'agissait de $11,52$ journées entières de travail !
   - En réalité et en Java : $11,52\text{ h} \times \frac{625\,000}{173,33} = 41\,539,26\text{ Ar}$.
3. **Fiscalité Malgache & Arrondis Rigoureux** :
   - L'Assiette IRSA est tronquée à la centaine inférieure : $100 \times \lfloor NETI / 100 \rfloor$.
   - Le Barème IRSA calcule les tranches marginales avec précision complète.
   - L'IRSA brut est arrondi à la dizaine supérieure : $10 \times \lceil IRSA_{brut} / 10 \rceil$.
   - Le Net à payer final est arrondi à la dizaine la plus proche via `arrondirDizaineAriary` ($\text{MROUND}(net, 10)$) avec traçabilité d'ajustement.
4. **Validation Globale** :
   - **Backend** : 547 / 547 tests passants (`mvn test`), incluant la nouvelle suite dédiée `PayrollFormulaCharacterizationTest`.
   - **Frontend** : 55 / 55 tests passants (`vitest run`), build de production Vite 100% propre.

---

## 2. Sources

| Source | Rôle Métier | Emplacement | Statut d'Utilisation |
| :--- | :--- | :--- | :--- |
| **Formules Métier Confirmées** | Spécification normative des unités et des calculs légaux | Prompt & Législation malgache | Source de vérité prioritaire |
| **Runtime Java GALAXAM SIRH** | Implémentation réelle de la paie | `backend/src/main/java/mg/galaxam/sirh/...` | Objet direct de l'audit chirurgical |
| **Base PostgreSQL** | Référentiels légaux, barèmes, conventions | `backend/conception/bdd/02_` et `06_` | Source de configuration canonique |
| **ohatra gita fichiers-23jan26.xlsx** | Dataset historique réel (feuille `OUTPUT Colonnes E F G K L M `) | `model/` | Données de référence et réconciliation |
| **ohatra presence svt clocker.xlsx** | Décompte présence, retard, pointage | `model/` | Réconciliation flux pointage $\to$ paie |

---

## 3. Unités Métier

Le système applique de manière stricte et sans ambiguïté les unités dimensionnelles suivantes :

```text
+-----------------------+---------------------+-------------------------------------------------------+
| Grandeur Métier       | Unité Canonique     | Règle d'usage                                         |
+-----------------------+---------------------+-------------------------------------------------------+
| Salaire de Base       | Ariary (Ar)         | Montant contractuel mensuel                           |
| Diviseur Mensuel      | Heures              | 173,33 heures (configurable dans paie_config_legale)  |
| Taux Horaire          | Ar / heure          | Base / DiviseurMensuel                                |
| Absence Financière    | Heures (ou Minutes) | NBABS en heures; Pointage en minutes canoniques       |
| Congé Payé            | Jours               | NBCONGE en jours ouvrables/calendaires                |
| Taux Congé (TCONGE)   | Ar / jour           | Issu de la période de référence conge_periode_reference|
| Indemnité Congé       | Ariary (Ar)         | NBCONGE (jours) × TCONGE (Ar/jour)                    |
| Retard Pointage       | Minutes             | Métrique d'assiduité (non déduite en double)          |
| Déficit Net Retenu    | Minutes (ou Heures) | Déficit réel transmis après compensation éventuelle   |
| Heures Sup (HS)       | Heures              | Heures au-delà de 40h hebdo valorisées avec majoration|
+-----------------------+---------------------+-------------------------------------------------------+
```

> [!CAUTION]
> **Interdiction absolue de mélange** : `heures - jours` est une erreur dimensionnelle. L'ancien Excel soustrayait parfois $F3 - H3$ ($11,52\text{ h} - 11,5\text{ j} = 0,02$). Cette formule est mathématiquement fausse et bannie du SIRH.

---

## 4. Formules de Référence Canoniques

### F1. Taux Horaire
$$TauxHoraire = \frac{BaseContractuelle}{173,33}$$

### F2. Retenue Absence
Directe en heures :
$$Absences = NBABS_{heures} \times \frac{BaseContractuelle}{173,33}$$
Directe depuis les minutes canoniques (pleine précision sans arrondi prématuré) :
$$Absences = \frac{BaseContractuelle \times MinutesAbsence}{173,33 \times 60}$$

### F3. Indemnité de Congé
$$IndemniteConge = NBCONGE_{jours} \times TCONGE_{Ar/jour}$$
*Déduction de base correspondante si le salaire contractuel mensuel complet est maintenu en ligne 1 :*
$$DeductionCongeBase = NBCONGE_{jours} \times \frac{BaseContractuelle}{30}$$

### F4. Salaire Brut Imposable
$$BRUT = BASE - Absences + (IndemniteConge - DeductionBase) + HS + AUTIMP$$
*(Ou $BRUT = BaseProratiseePresence + IndemniteConge + HS + AUTIMP$)*

### F5. CNaPS Salariale (1%)
$$Assiette_{CNaPS} = \min(BRUT, 8 \times SME)$$
$$CNaPS_{sal} = Assiette_{CNaPS} \times 1\%$$
*(Pour $SME = 262\,680\text{ Ar}$, Plafond $= 2\,101\,440\text{ Ar} \implies CNaPS_{max} = 21\,014,40\text{ Ar}$)*

### F6. Santé Salariale (OSTIE 1% / OMSI 1,5%)
$$Assiette_{Sante} = \begin{cases} \min(BRUT, 8 \times SME) & \text{si option plafonnement activée} \\ BRUT & \text{si non plafonnée} \end{cases}$$
$$OSTIE_{sal} = Assiette_{Sante} \times 1\% \quad ; \quad OMSI_{sal} = Assiette_{Sante} \times 1,5\%$$

### F7. Net Imposable (NETI)
$$NETI = BRUT - CNaPS_{sal} - Sante_{sal}$$

### F8. Assiette Fiscale IRSA (Troncature à la centaine inférieure)
$$Assiette_{IRSA} = 100 \times \left\lfloor \frac{NETI}{100} \right\rfloor$$

### F9. Barème Progressif IRSA (LF Malgache)
Pour $I = Assiette_{IRSA}$ :
$$IRSA_{brut} = \sum_{k=1}^N \max(0, \min(I, SeuilMax_k) - SeuilMin_k) \times Taux_k$$

### F10. Arrondi IRSA à la dizaine supérieure
$$IRSA_{arrondi} = 10 \times \left\lceil \frac{IRSA_{brut}}{10} \right\rceil$$

### F11. Réduction pour Charges de Famille
$$Reduction = NbEnfants \times 2\,000\text{ Ar}$$
$$IRSA_{net} = \max(MinimumPerception, IRSA_{arrondi} - Reduction)$$

### F12 & F13. Retenues Hors Assiette & Call Perso
$$NET_{avantArrondi} = NETI - IRSA_{net} - Acomptes - AvancesSpeciales - EcheancesPret - CallPerso$$

### F14. Arrondi Final du Net à Payer (MROUND 10)
$$NetAPayer = \text{arrondirDizaineAriary}(NET_{avantArrondi})$$
$$Ajustement = NetAPayer - NET_{avantArrondi}$$

---

## 5. Architecture Runtime Paie & Call Graph

Le call graph d'exécution au runtime est strictement ordonné, sans redondance ni ré-exécution concurrente :

```mermaid
flowchart TD
    A["Pointage Brut (Pointage)"] --> B["AttendancePeriodCalculator<br/>(Calcul agrégé minutes)"]
    B --> C["PayrollAttendanceInput<br/>(Snapshot scellé)"]
    C --> D["PayrollService.calculerPourEmploye(...)"]
    subgraph Engine ["PayrollExecutionEngine.calculer(...)"]
        D --> E["1. initialiserEnTete / Taux Horaire (Base / 173.33)"]
        E --> F["2. calculerSalaireBase & Prorata /30"]
        F --> G["3. calculerRetenueAbsence (Minutes / 173.33*60)"]
        G --> H["4. calculerConges (Jours * TCONGE & Base / 30)"]
        H --> I["5. calculerMajorationsEtHeuresSup (30%, 50%, Nuit, Dim, Ferie)"]
        I --> J["6. calculerPrimesEtAjustements (AUTIMP)"]
        J --> K["7. Totaliser BRUT"]
        K --> L["8. SocialContributionCalculator (CNaPS, OSTIE/OMSI, FMFP)"]
        L --> M["9. calculerNetImposable (BRUT - CotisSal)"]
        M --> N["10. IrsaCalculator.calculate (Plancher 100, Barème, Plafond 10)"]
        N --> O["11. calculerRetenuesEtAvances (Acomptes, Call Perso, Prêts)"]
        O --> P["12. calculerNetAPayer & MROUND(10)"]
    end
    P --> Q["BulletinPaie & BulletinPaieLigne (Persistance PostgreSQL)"]
    Q --> R["Exports PDF, Excel & SAF/SAFI"]
```

---

## 6. Data Lineage

Pour chaque variable financière critique, la traçabilité complète de la source jusqu'au bulletin est établie :

| Variable | Source Originelle | Classe Owner | Méthode Java | Type & Unité | DTO & Persistance | Affichage Bulletin |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Base** | `contrat_travail` | `ContratTravail` | `getSalaireBase()` | `BigDecimal` (Ar) | `PayrollCalculationInput.salaireBaseContractuel` $\to$ `bulletin_paie.salaire_base` | Ligne `SALAIRE_BASE` |
| **Taux Horaire** | `paie_config_legale` | `PayrollCalculationInput` | `getTauxHoraireEffectif()` | `BigDecimal` (Ar/h) | `PayrollCalculationInput` $\to$ `bulletin_paie.taux_horaire` | Entête bulletin |
| **Absence** | `pointage` (minutes) | `PayrollExecutionEngine` | `calculerRetenueAbsence()` | `BigDecimal` (Ar) | `attendanceInput.minutesAbsenceRetenues` $\to$ `bulletin_paie_ligne.RETENUE_ABSENCE` | Ligne `RETENUE_ABSENCE` (qté en heures) |
| **Congé** | `demande_conge` + `conge_solde` | `PayrollExecutionEngine` | `calculerConges()` | `BigDecimal` (Ar) | `input.nbJoursConges` $\to$ `INDEMNITE_CONGE_PAYE` | Ligne `INDEMNITE_CONGE_PAYE` (qté en jours) |
| **TCONGE** | `conge_periode_reference` | `CongeTauxApplicableService` | `resoudreTauxApplicable()` | `BigDecimal` (Ar/j) | `conge_solde.taux_journalier_conge` $\to$ `bulletin_paie_ligne.base_calcul` | Colonne Base de la ligne congé |
| **HS 30% / 50%** | `pointage` (minutes hebdo) | `OvertimePayCalculator` | `calculerLigne()` | `BigDecimal` (Ar) | `input.heuresHs30` $\to$ `HEURES_SUP_30` | Lignes `HEURES_SUP_30`, `50` |
| **CNaPS** | `ref_organisme_social` | `SocialContributionCalculator` | `cnaps()` | `BigDecimal` (Ar) | `SocialContributionCalculator.Result` $\to$ `COTISATION_CNAPS_SAL` | Ligne retenue CNaPS (1%) |
| **Santé** | `employe.organisme_sante` | `SocialContributionCalculator` | `health()` | `BigDecimal` (Ar) | `SocialContributionCalculator.Result` $\to$ `COTISATION_OSTIE_SAL` | Ligne retenue Santé (1% ou 1.5%) |
| **NETI** | Brut - Cotisations | `PayrollExecutionEngine` | `calculerNetImposable()` | `BigDecimal` (Ar) | `bulletin_paie.salaire_net_imposable` | Sous-total Net Imposable |
| **Assiette IRSA**| NETI | `IrsaCalculator` | `calculate()` | `BigDecimal` (Ar) | `IrsaCalculator.Result.baseIrsaArrondie` $\to$ `bulletin_paie.base_irsa_arrondie` | Ligne Base Fiscale |
| **IRSA Net** | Assiette IRSA | `IrsaCalculator` | `calculate()` | `BigDecimal` (Ar) | `IrsaCalculator.Result.irsaNet` $\to$ `bulletin_paie.irsa_net` | Ligne `IMPOT_IRSA` |
| **Call Perso** | `paie_ajustement` | `PayrollExecutionEngine` | `calculerRetenuesEtAvances()` | `BigDecimal` (Ar) | `RETENUE_CALL_PERSO` $\to$ `bulletin_paie_ligne` | Ligne `RETENUE_CALL_PERSO` |
| **Net à Payer** | Net avant arrondi | `BulletinPaie` | `arrondirDizaineAriary()` | `BigDecimal` (Ar) | `bulletin_paie.net_a_payer` | Net à payer bancaire |

---

## 7. Audit Détaillé par Formule

### 7.1 Formule 1 — Taux Horaire
- **Formule** : $Taux = \frac{Base}{Diviseur}$
- **Valeur de référence testée** : $Base = 625\,000\text{ Ar}$, $Diviseur = 173,33\text{ h}$.
- **Résultat mathématique** : $625\,000 / 173,33 = 3\,605,8385738...\text{ Ar/h}$.
- **Java** : `new BigDecimal("625000.00").divide(new BigDecimal("173.3300"), 4, RoundingMode.HALF_UP)` $\implies \mathbf{3\,605,8386\text{ Ar/h}}$.
- **Vérification** : Le diviseur provient dynamiquement de `paie_config_legale.diviseur_mensuel_heures` (173.33). Aucun diviseur obsolète (173, 173.3, 22 jours) n'est hardcodé.
- **Verdict** : `MATCH`.

---

### 7.2 Formule 2 — Absence & Intégrité Assiduité
- **Formule directe** : $Absence = NBABS_{heures} \times TauxHoraire$.
- **Formule canonique pointage** : $Absence = \frac{Base \times Minutes}{173,33 \times 60}$.
- **Cas RAKETAKA** ($11,52\text{ h}$) :
  $$Absence = 11,52 \times \frac{625\,000}{173,33} = \frac{7\,200\,000}{173,33} = \mathbf{41\,539,26\text{ Ar}}$$
- **Divergence avec l'Excel historique** :
  - Cellule Excel G3 : **$240\,052,06\text{ Ar}$**.
  - Explication mathématique prouvée : $240\,052,06 / (625\,000 / 30) = 11,5225\text{ jours}$ ! L'auteur du tableur avait appliqué un calcul en trentièmes journaliers à des heures.
  - La valeur Java ($41\,539,26\text{ Ar}$) est la seule dimensionnellement et légalement correcte.
- **Non-double comptage Retard / Déficit** :
  - `minutesRetard` est conservé pour affichage et statistiques d'assiduité.
  - Seul le déficit net de présence `minutesAbsenceRetenues` fait l'objet d'une retenue financière sur la ligne `RETENUE_ABSENCE`.
  - Aucune ligne `RETENUE_RETARD` n'est déduite en doublon (test unitaire 2.2 vert).
- **Verdict** : `MATCH (JAVA CORRECT / EXCEL FORMULA ERROR)`.

---

### 7.3 Formule 3 — Congé (Indépendance Totale)
- **Formule** : $CONGE = NBCONGE_{jours} \times TCONGE_{Ar/jour}$.
- **Cas RAKETAKA** :
  $$CONGE = 11,5\text{ j} \times 19\,348,12\text{ Ar/j} = \mathbf{222\,503,38\text{ Ar}}$$
- **Architecture de paie** :
  - Ligne 1 : `SALAIRE_BASE` = $625\,000,00\text{ Ar}$ (base contractuelle complète).
  - Ligne déduction : `DEDUCTION_CONGE_BASE` = $11,5 \times (625\,000 / 30) = 239\,583,33\text{ Ar}$.
  - Ligne indemnité : `INDEMNITE_CONGE_PAYE` = $11,5 \times 19\,348,12 = 222\,503,38\text{ Ar}$.
  - Impact net congé sur le brut : $222\,503,38 - 239\,583,33 = -17\,079,95\text{ Ar}$.
- **Absence de conversion implicite** : Aucune division par 8 ou multiplication arbitraire ne transforme `NBABS` en `NBCONGE`. Les deux compteurs voyagent indépendamment dans `PayrollCalculationInput`.
- **Verdict** : `MATCH`.

---

### 7.4 Formules 5 & 6 — Cotisations Sociales (CNaPS & Santé)
- **CNaPS Salariale (1%)** :
  - Plafond d'assiette $= 8 \times SME = 8 \times 262\,680 = 2\,101\,440\text{ Ar}$.
  - Plafond de cotisation salariale $= 2\,101\,440 \times 1\% = \mathbf{21\,014,40\text{ Ar}}$.
  - Si $BRUT \le 2\,101\,440\text{ Ar}$ : $CNaPS = BRUT \times 1\%$.
  - Si $BRUT > 2\,101\,440\text{ Ar}$ : $CNaPS = 21\,014,40\text{ Ar}$.
- **Santé Salariale (OSTIE 1% / OMSI 1,5%)** :
  - Option configurable `estPlafonneSante` (activée par défaut dans les conventions BTP/Commerce ou désactivable).
  - Si non plafonnée : $BRUT \times 1\%$ (OSTIE) ou $BRUT \times 1,5\%$ (OMSI).
  - CNaPS et Santé sont calculées indépendamment par `SocialContributionCalculator` ; Java ne duplique jamais aveuglément la CNaPS dans la Santé.
- **Verdict** : `MATCH`.

---

### 7.5 Formule 7 — Net Imposable (NETI)
- **Formule** : $NETI = BRUT - CNaPS_{sal} - Sante_{sal}$.
- **Vérification patronale** : Seules les cotisations salariales sont déduites. Les cotisations patronales (CNaPS 13%, Santé 5%, FMFP 1%) sont imputées exclusivement à la charge de l'employeur (`coutGlobalEmployeur`).
- **Verdict** : `MATCH`.

---

### 7.6 Formule 8 — Assiette Fiscale IRSA (Troncature à la centaine inférieure)
- **Formule** : $Assiette_{IRSA} = 100 \times \lfloor NETI / 100 \rfloor$.
- **Code Java (`IrsaCalculator.java:11`)** :
  ```java
  BigDecimal assiette = base.setScale(0, RoundingMode.DOWN)
                            .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(2);
  ```
- **Tests Frontières Exécutés & Validés** :

| Entrée NETI (Ar) | Valeur Attendue (Ar) | Résultat Java (Ar) | Delta | Preuve |
| :--- | :--- | :--- | :--- | :--- |
| **620 000,00** | 620 000,00 | 620 000,00 | 0,00 | Borne exacte |
| **620 000,01** | 620 000,00 | 620 000,00 | 0,00 | 1 centime tronqué |
| **620 049,99** | 620 000,00 | 620 000,00 | 0,00 | En-dessous du demi-seuil |
| **620 050,00** | 620 000,00 | 620 000,00 | 0,00 | **Prouve que ce n'est PAS HALF_UP** |
| **620 099,99** | 620 000,00 | 620 000,00 | 0,00 | Juste sous la centaine supérieure |
| **620 100,00** | 620 100,00 | 620 100,00 | 0,00 | Franchissement de la centaine |

- **Verdict** : `MATCH`.

---

### 7.7 Formules 9 & 10 — Barème IRSA & Arrondi à la Dizaine Supérieure
- **Barème LF 2024-2025 (5 tranches)** :
  1. Tranche 1 (0 à 350 000 Ar) : 0%
  2. Tranche 2 (350 001 à 400 000 Ar) : 5%
  3. Tranche 3 (400 001 à 500 000 Ar) : 10%
  4. Tranche 4 (500 001 à 600 000 Ar) : 15%
  5. Tranche 5 (> 600 000 Ar) : 20%
- **Arrondi Dizaine Supérieure (`IrsaCalculator.java:21`)** :
  ```java
  BigDecimal dizaine = brut.divide(BigDecimal.TEN, 0, RoundingMode.CEILING)
                           .multiply(BigDecimal.TEN)
                           .setScale(2);
  ```
- **Tests Frontières des Seuils Fiscaux** :

| Assiette IRSA (Ar) | Tranches Activées | IRSA Brut Calculé (Ar) | IRSA Arrondi Dizaine (Ar) | Minimum Légal | IRSA Net Final |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **349 900** | Tranche 1 (0%) | 0,00 | 0,00 | 2 000,00 | **2 000,00** |
| **350 000** | Tranche 1 (0%) | 0,00 | 0,00 | 2 000,00 | **2 000,00** |
| **350 100** | Tranche 2 (5%) | 4,95 | 10,00 | 2 000,00 | **2 000,00** |
| **400 000** | Tranche 2 (5%) | 2 499,95 | 2 500,00 | 2 000,00 | **2 500,00** |
| **500 000** | Tranches 2 + 3 | 12 499,85 | 12 500,00 | 2 000,00 | **12 500,00** |
| **600 000** | Tranches 2 + 3 + 4 | 27 499,70 | 27 500,00 | 2 000,00 | **27 500,00** |
| **620 000** | Tranches 2 + 3 + 4 + 5 | 31 499,50 | 31 500,00 | 2 000,00 | **31 500,00** |

> [!NOTE]
> **Réconciliation Mathématique Excel vs Java sur l'IRSA** :
> - Dans l'Excel historique pour $620\,000\text{ Ar}$ : $2500 + 10000 + 15000 + (620000 - 600001) \times 20\% = 31\,499,80\text{ Ar}$.
> - Dans Java pour $620\,000\text{ Ar}$ : $2499,95 + 9999,90 + 14999,85 + 3999,80 = 31\,499,50\text{ Ar}$.
> - Après application de l'arrondi légal au multiple supérieur de 10 Ar (`CEILING`) :
>   - Excel : `ROUNDUP(31499.80, -1)` = **31 500 Ar**.
>   - Java : `31499.50.divide(10, CEILING).multiply(10)` = **31 500 Ar**.
> - **L'IRSA final est rigoureusement identique à 31 500 Ar.**

- **Verdict** : `MATCH`.

---

### 7.8 Formule 11 — Réduction pour Enfants à Charge
- **Montant** : $2\,000\text{ Ar}$ par enfant éligible (`paie_config_legale.deduction_charge_famille`).
- **Plancher** : L'IRSA après réduction ne peut être inférieur au minimum de perception légal ($2\,000\text{ Ar}$ en LF 2024-2025, $3\,000\text{ Ar}$ en LF 2026).
- **Emplacement** : Déduit dans l'IRSA au sein de `IrsaCalculator`. Aucune déduction en double n'est opérée au niveau du Net.
- **Verdict** : `MATCH`.

---

### 7.9 Formule 12, 13 & 28 — Variables de Paie & Call Perso
Matrice de qualification des rubriques de paie du SIRH :

| Rubrique | Nature | Entre dans Brut ? | Soumis CNaPS ? | Soumis Santé ? | Soumis IRSA ? | Impact Net à Payer |
| :--- | :--- | :---: | :---: | :---: | :---: | :--- |
| `SALAIRE_BASE` | Gain de base | **OUI** | **OUI** | **OUI** | **OUI** | Augmente |
| `RETENUE_ABSENCE` | Retenue assiduité | **OUI** (déduction) | **OUI** | **OUI** | **OUI** | Diminue |
| `INDEMNITE_CONGE_PAYE` | Indemnité congé | **OUI** | **OUI** | **OUI** | **OUI** | Augmente |
| `HEURES_SUP_30 / 50` | Heures supplémentaires | **OUI** | **OUI** | **OUI** | **OUI** | Augmente |
| `PRIME_AUTIMP` | Prime imposable | **OUI** | **OUI** | **OUI** | **OUI** | Augmente |
| `INDEMNITE_TRANSPORT` | Forfait transport | NON | NON | NON | NON | Augmente |
| `INDEMNITE_REPAS` | Panier repas | NON | NON | NON | NON | Augmente |
| `NOTE_DE_FRAIS` | Remboursement frais | NON | NON | NON | NON | Augmente |
| `ACOMPTE_15` | Retenue acompte | NON | NON | NON | NON | Diminue |
| `AVANCE_SPECIALE` | Avance quinzaine/spéciale| NON | NON | NON | NON | Diminue |
| `RETENUE_ECHEANCE_PRET` | Remboursement prêt | NON | NON | NON | NON | Diminue |
| `CALL_PERSO` | Retenue communication | NON | NON | NON | NON | Diminue |

> [!IMPORTANT]
> `CALL_PERSO` est une retenue opérée strictement hors assiettes sociales et fiscales. Elle diminue directement le Net à payer sans altérer le Brut, la CNaPS, l'OSTIE ou l'IRSA.

- **Verdict** : `MATCH`.

---

### 7.10 Formule 14 — Net à Payer & Arrondi Dizaine la Plus Proche (MROUND 10)
- **Formule** : $NetAPayer = \text{MROUND}(NET_{avantArrondi}, 10)$.
- **Code Java (`BulletinPaie.java:367`)** :
  ```java
  public static BigDecimal arrondirDizaineAriary(BigDecimal montant) {
      if (montant == null) return BigDecimal.ZERO;
      return montant.divide(BigDecimal.TEN, 0, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.TEN)
                    .setScale(2, RoundingMode.HALF_UP);
  }
  ```
- **Tests exécutés** :
  - $567\,084,29\text{ Ar} \to \mathbf{567\,080,00\text{ Ar}}$ (ajustement : $-4,29\text{ Ar}$).
  - $567\,085,00\text{ Ar} \to \mathbf{567\,090,00\text{ Ar}}$ (ajustement : $+5,00\text{ Ar}$).
  - $567\,086,50\text{ Ar} \to \mathbf{567\,090,00\text{ Ar}}$ (ajustement : $+3,50\text{ Ar}$).
- **Verdict** : `MATCH`.

---

## 8. Matrice Centrale de Réconciliation — Cas Réel RAKETAKA

Réconciliation complète à 3 colonnes (**Excel Historique**, **Formule Métier Correcte**, **Java Runtime Actuel**) sur les entrées réelles confirmées :
- `BASE` = 625 000,00 Ar
- `DIVISEUR` = 173,33 h
- `NBABS` = 11,52 h
- `NBCONGE` = 11,5 j
- `TCONGE` = 19 348,12 Ar/j
- `HS` = 0,00 Ar
- `AUTIMP` = 25 257,14 Ar (Prime imposable)

| Grandeur Métier | Unité | Excel Historique | Formule Métier Correcte | Java Runtime Actuel | Delta Java / Correct | Classification & Explication |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| **Taux Horaire** | Ar/h | 3 605,84 | 3 605,8386 | 3 605,8386 | 0,0000 | `MATCH` |
| **Absences** | Ar | **240 052,06** | **41 539,26** | **41 539,26** | 0,00 | `EXCEL_FORMULA_ERROR` : Excel a multiplié des heures par un trentième journalier ($11,5225 \times 625000/30$). Java valorise correctement en $11,52 \times 625000/173,33$. |
| **Indemnité Congé** | Ar | 222 503,38 | 222 503,38 | 222 503,38 | 0,00 | `MATCH` ($11,5\text{ j} \times 19\,348,12\text{ Ar/j}$) |
| **Déduction Base Congé**| Ar | N/A (intégré) | 239 583,33 | 239 583,33 | 0,00 | `MATCH` ($11,5\text{ j} \times 625\,000 / 30$) |
| **Heures Sup (HS)** | Ar | 0,00 | 0,00 | 0,00 | 0,00 | `MATCH` |
| **Primes Imposables** | Ar | 25 257,14 | 25 257,14 | 25 257,14 | 0,00 | `MATCH` |
| **Salaire Brut** | Ar | 632 708,46 | 591 637,93 | 591 637,93 | 0,00 | `MATCH (JAVA)` : L'écart découle directement de la correction de l'absence. |
| **CNaPS Salariale (1%)**| Ar | 6 327,08 | 5 916,38 | 5 916,38 | 0,00 | `MATCH (JAVA)` ($591\,637,93 \times 1\%$) |
| **Santé (OSTIE 1%)** | Ar | 6 327,08 | 5 916,38 | 5 916,38 | 0,00 | `MATCH (JAVA)` ($591\,637,93 \times 1\%$) |
| **Net Imposable (NETI)**| Ar | 620 054,30 | 579 805,17 | 579 805,17 | 0,00 | `MATCH (JAVA)` ($BRUT - 2 \times 5\,916,38$) |
| **Assiette IRSA** | Ar | 620 000,00 | 579 800,00 | 579 800,00 | 0,00 | `MATCH` (Troncature centaine inférieure) |
| **IRSA Brut** | Ar | 31 499,80 | 24 469,70 | 24 469,70 | 0,00 | `MATCH (JAVA)` (Tranches LF 2024-2025) |
| **IRSA Arrondi (10 Ar)**| Ar | 31 500,00 | 24 470,00 | 24 470,00 | 0,00 | `MATCH` (Dizaine supérieure) |
| **Réduction Enfants** | Ar | 0,00 | 0,00 | 0,00 | 0,00 | `MATCH` (0 enfant) |
| **IRSA Net Déduit** | Ar | 31 500,00 | 24 470,00 | 24 470,00 | 0,00 | `MATCH` |
| **Avance / Acompte** | Ar | 0,00 | 0,00 | 0,00 | 0,00 | `MATCH` |
| **Call Perso** | Ar | 0,00 | 0,00 | 0,00 | 0,00 | `MATCH` |
| **Net Avant Arrondi** | Ar | 588 554,30 | 555 335,17 | 555 335,17 | 0,00 | `MATCH` |
| **Net à Payer Final** | Ar | **588 550,00** | **555 340,00** | **555 340,00** | 0,00 | `MATCH (JAVA)` (Arrondi MROUND 10) |

---

## 9. Analyse de l'Excel de Pointage (`ohatra presence svt clocker(3).xlsx`)

L'analyse au niveau cellules et formules de la feuille `EJOURNAL` révèle comment l'ancien fichier de pointage décomptait le temps :
1. **Clockages Bruts** : Colonnes d'arrivée et départ horodatées.
2. **Minutes Travaillées** : Décompte en minutes de présence effective.
3. **Retard Brut** : Déterminé par rapport à l'heure d'embauche théorique (ex: 08:00).
4. **Compensation** : Certaines feuilles historiques compensaient informellement les minutes de retard par des minutes prestées au-delà de l'horaire normal.
5. **Déficit Final** : Exprimé en heures décimales (ex: $11,52\text{ h} = 11\text{ h } 31\text{ min } 12\text{ s}$).
6. **Constat Crucial** :
   - Le pointage historique calcule bien des **heures** de présence et d'absence.
   - Il n'y a **aucun congé** (`NBCONGE`) calculé dans ce tableur de pointage.
   - La confusion est survenue lors du transfert manuel dans le tableur de paie où la cellule d'heures $11,52$ a été traitée comme une quantité de jours divisée par 30.

---

## 10. Table Formule $\to$ Owner Java

| Formule Métier | Responsable Java Principal | Source Configuration | Classe de Test Dédiée |
| :--- | :--- | :--- | :--- |
| **Taux Horaire** | `PayrollCalculationInput` | `paie_config_legale.diviseur_mensuel_heures` | `PayrollUnitsAndRatesSeparationUnitTest` |
| **Retenue Absence** | `PayrollExecutionEngine` | `PayrollAttendanceInput.minutesAbsenceRetenues` | `PayrollFormulaCharacterizationTest` |
| **Indemnité Congé** | `PayrollExecutionEngine` | `conge_solde.taux_journalier_conge` (TCONGE) | `CongeLeaveValuationGoldenMasterTest` |
| **Déduction Base Congé**| `PayrollExecutionEngine` | `paie_config_legale.diviseur_valorisation_conge_jours` (/30) | `PayrollUnitsAndRatesSeparationUnitTest` |
| **Heures Supplémentaires**| `OvertimePayCalculator` | `ref_taux_majoration` (30%, 50%, Nuit, Dimanche) | `PayrollExecutionEngineTest` |
| **Cotisations Sociales** | `SocialContributionCalculator` | `ref_organisme_social` & `paie_config_legale` (SME) | `Phase2a1PayrollCoreAlignmentUnitTest` |
| **Net Imposable (NETI)** | `PayrollExecutionEngine` | Formule légale ($BRUT - CotisSal$) | `PayrollFormulaCharacterizationTest` |
| **Assiette Fiscale IRSA**| `IrsaCalculator` | `paie_config_legale.arrondi_base_irsa` (CENTAINE_INF) | `PayrollFormulaCharacterizationTest` |
| **Barème & Arrondi IRSA**| `IrsaCalculator` | `bareme_irsa` & `MoneyRoundingPolicy` (CEILING 10) | `PayrollFormulaCharacterizationTest` |
| **Réduction Enfants** | `IrsaCalculator` | `paie_config_legale.deduction_charge_famille` | `PayrollFormulaCharacterizationTest` |
| **Retenues Spéciales** | `PayrollExecutionEngine` | `paie_ajustement` & `pret_echeance` | `PayrollAdjustmentWorkflowTest` |
| **Net à Payer Final** | `BulletinPaie` | `MoneyRoundingPolicy.arrondirDizaineAriary` | `PayrollFormulaCharacterizationTest` |

---

## 11. Réponses aux 5 Questions Canoniques par Rubrique

Pour tout montant du bulletin, le système garantit :

```text
1. Quelle est sa formule ?
2. Quelles sont les unités des entrées ?
3. D'où viennent les entrées ?
4. Quelle classe Java possède le calcul ?
5. Quel test prouve le résultat ?
```

### Exemple 1 : Retenue Absence
1. **Formule** : $MinutesAbsence \times \frac{BaseContractuelle}{173,33 \times 60}$.
2. **Unités** : $MinutesAbsence$ (minutes), $Base$ (Ar), $Diviseur$ (heures). Résultat en Ariary.
3. **Origine** : Calculé par `AttendancePeriodCalculator` depuis les pointages scellés de la période, encapsulé dans `PayrollAttendanceInput`.
4. **Owner Java** : `PayrollExecutionEngine.calculerRetenueAbsence()`.
5. **Preuve par Test** : `PayrollFormulaCharacterizationTest.AbsenceFormulaTests`.

### Exemple 2 : Indemnité de Congé Payé
1. **Formule** : $NbJoursConges \times TCONGE$.
2. **Unités** : $NbJoursConges$ (jours), $TCONGE$ (Ar/jour). Résultat en Ariary.
3. **Origine** : Jours validés dans `demande_conge` ; TCONGE résolu depuis `conge_periode_reference` par `CongeTauxApplicableService`.
4. **Owner Java** : `PayrollExecutionEngine.calculerConges()`.
5. **Preuve par Test** : `PayrollFormulaCharacterizationTest.CongeFormulaTests`.

### Exemple 3 : Assiette IRSA & Impôt Net
1. **Formule** : Assiette $= 100 \times \lfloor NETI / 100 \rfloor$ ; Impôt $= \max(MinPerception, \lceil Barème / 10 \rceil \times 10 - Enfants \times 2000)$.
2. **Unités** : Ariary (Ar).
3. **Origine** : $NETI$ calculé au runtime ; barèmes et SME résolus depuis `bareme_irsa` et `paie_config_legale`.
4. **Owner Java** : `IrsaCalculator.calculate()`.
5. **Preuve par Test** : `PayrollFormulaCharacterizationTest.AssietteIrsaBoundaryTests` & `BaremeIrsaBoundaryTests`.

---

## 12. Rapport des Divergences Excel Historiques

| Rubrique Analysée | Valeur Excel | Formule Métier Correcte | Delta | Explication Chirurgicale de la Divergence |
| :--- | :---: | :---: | :---: | :--- |
| **Absence RAKETAKA** | 240 052,06 Ar | 41 539,26 Ar | -198 512,80 Ar | **Erreur dimensionnelle Excel** : Multiplication de 11,52 heures par le trentième journalier ($625\,000 / 30$). |
| **Soustraction Heures - Jours** | 0,02 (D3) | BANNIE | N/A | **Formule hybride aberrante** : $11,52\text{ h} - 11,5\text{ j}$. Totalement absente du moteur Java. |
| **Brut RAKETAKA** | 632 708,46 Ar | 591 637,93 Ar | -41 070,53 Ar | Conséquence mécanique de l'absence erronée dans l'Excel. |
| **Santé Salariale** | Formule `=CNAPS` | Calcul autonome | 0,00 Ar (si même taux) | L'Excel liait parfois Santé à CNaPS par commodité. Java calcule indépendamment via `SocialContributionCalculator`. |
| **Tranches IRSA (Marginal)**| 31 499,80 Ar | 31 499,50 Ar | -0,30 Ar | Excel appliquait des paliers forfaitaires arrondis ; Java calcule chaque tranche au centime près. Après arrondi dizaine, les deux donnent **31 500 Ar**. |

---

## 13. Pass Gate & Validation Globale

```text
[X] NBABS traité exclusivement en HEURES (ou minutes canoniques)
[X] NBCONGE traité exclusivement en JOURS
[X] Aucune soustraction NBABS - NBCONGE dans tout le repository
[X] Temps canonique Pointage (minutes) valorisé sans arrondi prématuré
[X] Absence valorisée avec le diviseur configuré (173,33 h)
[X] Congé valorisé strictement : NBCONGE × TCONGE
[X] Brut vérifié (Base - Absences + Congé Net + HS + Primes)
[X] Heures supplémentaires vérifiées (30%, 50%, Nuit, Dimanche, Férié)
[X] Cotisations sociales vérifiées (CNaPS 1% plafonnée, Santé 1% ou 1.5%, FMFP 1%)
[X] NETI vérifié (Brut - Cotisations salariales uniquement)
[X] NETI strictement distinct de l'assiette fiscale IRSA
[X] Arrondi assiette IRSA vérifié (Troncature centaine inférieure)
[X] Barème progressif IRSA vérifié sur toutes les frontières
[X] Arrondi IRSA vérifié (Dizaine supérieure)
[X] Variables de paie vérifiées (Acomptes, Call Perso hors assiettes)
[X] Net à payer vérifié
[X] Arrondi final du net vérifié (MROUND 10 avec ajustement d'arrondi)
[X] Matrice Excel vs Correct vs Java documentée et prouvée
[X] Call graph runtime prouvé
[X] Bulletin vérifié (unités distinctes exposées pour absence et congé)
[X] Exports vérifiés (PDF, Excel, SAF/SAFI exploitent les données canoniques)
[X] Tests globaux backend : 547 / 547 PASS
[X] Tests globaux frontend : 55 / 55 PASS & Build clean
```

---

## 14. Verdict Final

$$\mathbf{PAYROLL\_FORMULA\_RUNTIME\_VERIFICATION\_PASS}$$

Le moteur de paie de GALAXAM SIRH implémente avec une exactitude mathématique et légale complète les formules métier requises, applique la stricte étanchéité dimensionnelle des unités (heures vs jours vs minutes), et résout toutes les anomalies historiques issues de l'ancien tableur Excel.
