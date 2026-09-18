# PHASE 2A-4B — REVERSE ENGINEERING KAJY & CONFORMITÉ DU RUNTIME TCONGE
**Date :** 17 Septembre 2026  
**Type de phase :** AUDIT CONTRADICTOIRE & REVERSE ENGINEERING KAJY  
**Fichier source analysé :** `model/OHATRA KAJY DROITS CONGES-given on sept26.xls`  
**Statut de clôture :** VALIDÉ  
**Verdict :** `PHASE_2A_4B_KAJY_REVERSE_ENGINEERING_PASS`

---

## 1. BASELINE DE DÉPART & VÉRIFICATIONS TECHNIQUES

| Composant | Statut Initial (Phase 2A-4A) | Statut Final (Phase 2A-4B) |
|---|---|---|
| **Backend Test Suite** | 469 PASS | **471 PASS** (+2 tests POI / Golden Master KAJY) |
| **Frontend Test Suite (Vitest)** | 28 PASS | **28 PASS** (100% vert) |
| **PostgreSQL Réel (Intégration)** | PASS | **PASS** |
| **Build Vite** | PASS | **PASS** (375 ms) |
| **`git diff --check`** | PASS | **PASS** (0 whitespace/newline error) |

---

## 2. DESCRIPTION DU FICHIER KAJY RÉEL

Le fichier analysé est le classeur Excel officiel de référence GALAXAM :
- **Chemin local :** `model/OHATRA KAJY DROITS CONGES-given on sept26.xls`
- **Format :** Microsoft Excel 97-2004 (BIFF8 binary `.xls`).
- **Taille :** 15 360 octets.
- **Rôle métier :** Fiche de référence servant de modèle de calcul RH chez GALAXAM pour le suivi des droits à congés, de l'assiette de valorisation et du calcul du taux journalier moyen `TCONGE`.

---

## 3. FEUILLES ET STRUCTURE DU CLASSEUR

L'inspection directe via Apache POI (`HSSFWorkbook`) révèle que le classeur contient **une seule feuille** :
- **Nom de la feuille :** `Feuille1`
- **Dimensions utilisées :** 14 lignes (lignes 1 à 14), 24 colonnes (colonnes A à X).
- **Organisation :**
  - **Ligne 1 :** Titre de section en cellule `P1` : `'CALCUL DROIT CONGES'` (au-dessus des colonnes de valorisation).
  - **Ligne 2 :** En-têtes de colonnes (colonnes A à V).
  - **Lignes 3 à 14 :** Données mensuelles d'un salarié réel sur **exactement 12 mois** (Mois 1 à Mois 12) :
    - **Matricule :** 61
    - **Nom :** `RASOANARIVO Havana Hideric`
  - **Ligne 14 (Cellules W14 & X14) :** Résultat final du taux journalier (`W14 = V14/U14`, `X14 = 'taux journalier'`).

---

## 4. CARTOGRAPHIE COLONNE PAR COLONNE

| Col. | En-tête KAJY | Type Cellule | Unité | Rôle Métier dans KAJY | Équivalent Java / DB |
|---|---|---|---|---|---|
| **A** | `MOIS` | NUM | Mois (1..12) | Numéro séquentiel de mois dans l'exercice | `annee`, `mois` |
| **B** | `MATR` | NUM | Matricule | Identifiant RH du salarié (61) | `id_employe` |
| **C** | `NOM` | STR | Texte | Nom complet du collaborateur | `employe.nom` |
| **D** | `formule` | FORMULE | Jours | Base de présence corrigée (max 30) | `jours_travailles_formule` |
| **E** | `BASE` | NUM | Ar | Salaire de base contractuel du mois | `salaire_base` |
| **F** | `NBABS` | NUM / FORMULE | Jours | Total des absences décomptées en paie (y compris congés) | Pointage brut |
| **G** | `ABSENCES` | NUM | Ar | Montant de la retenue pour absence décomptée | Rubrique `RETENUE_ABSENCE` |
| **H** | `NBCONGE` | NUM / FORMULE | Jours | Jours de congés payés indemnisés dans le mois | `bulletin_paie.jours_conges` |
| **I** | `TCONGE` | NUM | Ar/j | Taux journalier appliqué pour indemniser le congé du mois | `taux_journalier_conge` (M-1) |
| **J** | `CONGE` | FORMULE | Ar | Montant de l'indemnité de congé payé versée | Rubrique `INDEMNITE_CONGE_PAYE` |
| **K** | `NET BASE` | FORMULE | Ar | Base contractuelle nette d'absences et de congés | Base recalculée bulletin |
| **L** | `HS` | NUM | Ar | Montant total des heures supplémentaires du mois | Catégorie `HEURES_SUP` |
| **M** | `AUTIMP` | NUM | Ar | Autres primes imposables du mois (primes de rendement, etc.) | Catégorie `PRIME` |
| **N** | `BRUTE` | FORMULE | Ar | Rémunération brute totale du bulletin | `bulletin_paie.total_gains_bruts` |
| **O** | *(vide)* | BLANK | — | Séparateur visuel entre bulletin et calcul congés | — |
| **P** | `BASE colonne K` | FORMULE | Ar | Report de la colonne K (`NET BASE`) | `netBase` |
| **Q** | `Pdir/HS` | FORMULE | Ar | Report de la colonne L (`HS`) | `mntHs` |
| **R** | `base totale` | FORMULE | Ar | Assiette brute soumise aux congés (`P + Q`) | `base_valorisation` |
| **S** | `nbre droit congés` | FORMULE | Jours | Droits à congés acquis dans le mois | `jours_droit_acquis` |
| **T** | `R*S/30` | FORMULE | Ar | Quote-part mensuelle de valorisation du congé | `valorisation_mensuelle` |
| **U** | `cumul NB JR DROIT` | FORMULE | Jours | Cumul annuel des droits acquis (1..12 mois) | `cumul_jours_droit` |
| **V** | `Cumul Valorisation Droit` | FORMULE | Ar | Cumul annuel de la valorisation (1..12 mois) | `cumul_valorisation` |
| **W** | *(en ligne 14)* | FORMULE | Ar/j | Taux journalier moyen annuel final (`V14 / U14`) | `taux_journalier_conge` annuel |
| **X** | *(en ligne 14)* | STR | Label | Libellé : `'taux journalier'` | — |

---

## 5. CARTOGRAPHIE FORMULE PAR FORMULE (PREUVE EXCEL EXTRAITE)

Pour chaque ligne $i \in [3, 14]$ (Mois 1 à 12) :

1. **Colonne D (`formule`) :**
   ```text
   =IF(30-F{i}+H{i}>30, 30, 30-F{i}+H{i})
   ```
2. **Colonne J (`CONGE`) :**
   ```text
   =I{i}*H{i}
   ```
3. **Colonne K (`NET BASE`) :**
   ```text
   =E{i}-G{i}+J{i}
   ```
4. **Colonne N (`BRUTE`) :**
   ```text
   =E{i}-G{i}+J{i}+L{i}+M{i}
   ```
5. **Colonne P (`BASE colonne K`) :**
   ```text
   =K{i}
   ```
6. **Colonne Q (`Pdir/HS`) :**
   ```text
   =L{i}
   ```
7. **Colonne R (`base totale`) :**
   ```text
   =P{i}+Q{i}
   ```
8. **Colonne S (`nbre droit congés`) :**
   ```text
   =D{i}*2.5/30
   ```
9. **Colonne T (`R*S/30`) :**
   ```text
   =S{i}*R{i}/30
   ```
10. **Colonne U (`cumul NB JR DROIT`) :**
    - En ligne 3 (Mois 1) : `=S3`
    - En lignes 4 à 14 (Mois 2 à 12) : `=U{i-1}+S{i}`
11. **Colonne V (`Cumul Valorisation Droit`) :**
    - En ligne 3 (Mois 1) : `=T3`
    - En lignes 4 à 14 (Mois 2 à 12) : `=V{i-1}+T{i}`
12. **Cellule W14 (`TCONGE final`) :**
    ```text
    =V14/U14
    ```

---

## 6. INVENTAIRE DES CONSTANTES CACHÉES

| Constante | Formules Concernées | Fonction Mathématique | Origine / Signification | Classification |
|---|---|---|---|---|
| **`30`** | `D = IF(30-F+H>30, 30, ...)` | Plafond mensuel de jours | Trentième forfaitaire mensuel malgache | `GALAXAM_EXISTING_RULE` |
| **`2.5`** | `S = D * 2.5 / 30` | Taux mensuel d'acquisition | 2,5 jours calendaires / mois (Art. 125 Code Travail) | `LEGAL_REQUIREMENT_VERIFIED` |
| **`30`** | `S = D * 2.5 / 30` | Base de proratisation acquisition | Nombre forfaitaire de jours du mois | `GALAXAM_EXISTING_RULE` |
| **`30`** | `T = S * R / 30` | Diviseur de valorisation mensuelle | Ramène l'allocation mensuelle au taux journalier | `GALAXAM_EXISTING_RULE` |

> [!NOTE]
> **Le facteur 1/12 implicite :**  
> L'association de `* 2.5 / 30` (en colonne S) et de `/ 30` (en colonne T) produit pour un mois plein ($S = 2.5$) :  
> $$T = \frac{2.5 \times R}{30} = \frac{R}{12}$$  
> Le chiffre 12 n'est pas écrit en dur, mais découle rigoureusement de la simplification $\frac{2.5}{30} = \frac{1}{12}$.

---

## 7. REVERSE ENGINEERING : FORMULE DU DROIT MENSUEL

### 7.1. Comportement de la Colonne D (`formule`)
Formule : `=IF(30 - NBABS + NBCONGE > 30, 30, 30 - NBABS + NBCONGE)`

- **Pourquoi `+ NBCONGE` ?**  
  Dans le système amont de paie de GALAXAM d'où proviennent ces exports Excel, la colonne `NBABS` représentait **le total des jours d'absence décomptés du salaire fixe, congés inclus**.  
  Ainsi, lorsque Havana a pris 7,56 jours de congés au Mois 1 :
  - `NBABS` = 7,56 jours déduits de la base.
  - `NBCONGE` = 7,56 jours indemnisés par l'indemnité de congé.
  - L'auteur de la formule Excel a donc fait : $30 - (\text{NBABS} - \text{NBCONGE}) = 30 - 7.56 + 7.56 = 30$.  
    Les congés pris ne devaient pas réduire le droit à congés !
- **Évolution dans le SIRH (Dette résolue en Phase 2A-0) :**  
  Dans le moteur moderne de Pointage SIRH (`PointageSyntheseService`), `jAbs` et `jCge` sont déjà distincts et mutuellement exclusifs. `jAbs` ne contient que les absences injustifiées. Par conséquent, la formule Java :
  ```java
  BigDecimal formule = new BigDecimal("30.0").subtract(absInjustifiees).max(BigDecimal.ZERO);
  ```
  produit exactement le résultat net attendu par KAJY sans la double manipulation `+ NBCONGE`.

---

## 8. REVERSE ENGINEERING : BASE DE VALORISATION (`BaseTotale`)

### 8.1. Définition Exacte dans KAJY
- Colonne P (`BASE colonne K`) = `=K` (`NET BASE`).
- Colonne Q (`Pdir/HS`) = `=L` (`HS`).
- Colonne R (`base totale`) = `=P + Q` = `NET BASE + HS`.

### 8.2. Découverte Majeure : Exclusion Complète des Primes (`AUTIMP`)
- Dans la feuille Excel, la colonne M (`AUTIMP`) contient des montants très significatifs pour Havana :
  - Mois 1 : 217 790,00 Ar
  - Mois 2 : 151 488,00 Ar
  - Mois 6 : 205 105,00 Ar
  - Mois 8 : 169 415,00 Ar
  - Total annuel `AUTIMP` : **1 988 884,86 Ar** (près de 2 millions d'Ariary !).
- **Preuve arithmétique absolue :**  
  La colonne N (`BRUTE`) intègre bien `AUTIMP` (`=E-G+J+L+M`), mais les colonnes de valorisation du congé (P, Q, R) **IGNRENT TOTALEMENT LA COLONNE M** !
  - En ligne 3 (Mois 1) :
    - `NET BASE` = 277 998,53 Ar
    - `HS` = 4 339,20 Ar
    - `base totale` = 277 998,53 + 4 339,20 = **282 337,73 Ar**
    - Si `AUTIMP` (217 790 Ar) avait été inclus, la base aurait été de 500 127,73 Ar.
- **Classification :** `GALAXAM_EXISTING_RULE = EXCLUSION DES PRIMES`.  
  Le runtime Java `PayrollService.java` (qui n'inclut que `HEURES_SUP` et ignore `PRIME`) n'est donc **PAS un bug de transcription**, mais la **reproduction exacte à l'euro/ariary près de la pratique historique GALAXAM**.

---

## 9. REVERSE ENGINEERING : ABSENCES, CONGÉS ET HS

### 9.1. Absences
- Le montant déduit en colonne G (`ABSENCES`) réduit `NET BASE` (`=E - G + J`).
- Si un salarié a des absences injustifiées non rémunérées, elles diminuent la base de valorisation du mois.

### 9.2. Congés Déjà Pris
- Le montant versé en colonne J (`CONGE = I * H`) réintègre la valeur du congé pris dans `NET BASE`.
- Cela assure la neutralité financière du maintien de salaire sur la base de valorisation.

### 9.3. Heures Supplémentaires
- La colonne L (`HS`) est intitulée `Pdir/HS` en colonne Q.
- Elle est intégralement ajoutée à la base de valorisation : `BaseTotale = NetBase + HS`.

---

## 10. REVERSE ENGINEERING : PRIMES ET FRAIS DANS KAJY

- **Primes (`AUTIMP`) :** Présentes dans le brut payé, **exclues de la valorisation congés**.
- **Frais professionnels / Remboursements de transport / Repas :** Ils ne figurent même pas sur ce tableau de bord (ils sont traités hors salaire brut).

---

## 11. REVERSE ENGINEERING : VALORISATION MENSUELLE & CUMULS

### 11.1. Formule Mensuelle (`R*S/30`)
$$\text{ValMois} = \frac{\text{BaseTotale} \times \text{DroitMois}}{30}$$

### 11.2. Cumul des Droits (`U`) et Cumul de Valorisation (`V`)
$$\text{CumulDroits}_M = \sum_{k=1}^{M} \text{DroitMois}_k$$
$$\text{CumulVal}_M = \sum_{k=1}^{M} \text{ValMois}_k$$

---

## 12. RÉVÉLATION FONDAMENTALE : CUMUL TOUS TEMPS vs 12 MOIS

### 12.1. Ce que fait KAJY Réellement
- Le fichier KAJY est structuré en **un tableau annuel clos de 12 lignes** (Mois 1 à Mois 12).
- En fin de tableau (Mois 12, ligne 14), le classeur calcule :
  $$\text{TCONGE} = \frac{\text{CumulVal}_{12}}{\text{CumulDroits}_{12}} = \frac{324\,385.38}{29.96} = \mathbf{10\,828.49\text{ Ar/jour}}$$
- Ce taux de 10 828,49 Ar/j calculé en fin de 12ème mois a vocation à être **recopié comme constante dans la colonne I pour l'année suivante** !
- **Preuve par le fichier :**  
  Dans les lignes 10 à 14 du fichier (Mois 8 à 12), la colonne I porte la valeur fixe `8669.52`. Cette valeur était le taux calculé à la fin de l'exercice $N-1$ !

### 12.2. Origine de la Divergence "Cumul Tous Temps" dans le SIRH Java
- Dans KAJY, la feuille est limitée à 12 mois. Le cumul s'arrête naturellement au mois 12.
- Lors de l'implémentation de `conge_journal_droit` en SQL/Java, le développeur a codé :
  ```sql
  SELECT cumul_jours_droit, cumul_valorisation FROM conge_journal_droit ... ORDER BY annee DESC, mois DESC LIMIT 1
  ```
  sans introduire de réinitialisation annuelle ni de fenêtre glissante.
- **Verdict :**  
  Le « cumul tous temps » infini n'a jamais été voulu par KAJY ! KAJY est un calcul annuel sur 12 mois. Le cumul tous temps du SIRH est un **effet de bord technique de la persistance SQL non bornée** (`RUNTIME_DIVERGENCE`).

---

## 13. ANALYSE DES ARRONDIS ET PRÉCISION INTERMÉDIAIRE

L'implémentation du Golden Master test révèle une nuance technique cruciale :
1. **Dans Excel :**
   - La cellule `S` (`DroitMois = D * 2.5 / 30`) est stockée en nombre flottant double précision (ex: `2.49916666666667` pour $D = 29.99$).
   - La cellule `T` (`R * S / 30`) multiplie directement ces valeurs non arrondies.
   - Les cumuls progressifs `=V_prec + T` additionnent les flottants non arrondis.
2. **Dans Java :**
   - Si `DroitMois` est arrondi à 4 décimales (`2.4992`) avant calcul de `ValoMois`, un écart de 34 centimes apparaît au mois 2 ($25\,986.60$ vs $25\,986.26$).
   - Si la multiplication utilise la précision étendue, les 12 mois de `ValoMois` correspondent au centime près à Excel.
   - Sur la somme cumulée annuelle (324 385,38 Ar), l'accumulation de flottants binaires Excel produit une différence résiduelle de seulement **0,02 Ar** (2 centimes d'Ariary, soit $0,000006\%$) :
     - Cumul Excel : `324 385.38 Ar`
     - Cumul Java (somme de montants arrondis à 2 décimales) : `324 385.36 Ar`
     - TCONGE résultant : `10 828.49 Ar/j` (Excel) vs `10 828.47 Ar/j` (Java), soit **0,02 Ar d'écart**.

---

## 14. COMPARAISON EXHAUSTIVE KAJY ↔ RUNTIME JAVA

| Règle / Grandeur | Formule KAJY Excel | Formule Runtime Java SIRH | Statut de Conformité | Explication de l'Écart Éventuel |
|---|---|---|---|---|
| **DroitMois** | `D * 2.5 / 30` où `D = 30 - Abs + Cge` | `2.5 * (30 - AbsInjustifiees) / 30` | `EXACT_MATCH` | Même résultat net, dette Pointage résolue |
| **Base Totale** | `NET BASE + HS` | `netBase + mntHs` | `EXACT_MATCH` | Identité parfaite |
| **Périmètre Primes** | `AUTIMP` exclu | `PRIME` exclu | `EXACT_MATCH` | Conformité absolue à la pratique KAJY |
| **ValoMois** | `BaseTotale * Droit / 30` | `baseTotale * droitMois / 30` | `EXACT_MATCH` | Identité arithmétique |
| **Profondeur Historique**| Table annuelle de 12 mois | Cumul infini SQL "Tous temps" | `RUNTIME_DIVERGENCE` | KAJY est annuel, SQL n'avait pas de borne |
| **Application TCONGE** | Taux constant de l'année précédente | Taux résolu au mois M-1 | `SEMANTIC_MATCH` | Décalage M-1 vs taux annuel fixe |
| **Maintien de salaire** | Absent (moyenne stricte) | Absent (moyenne stricte) | `EXACT_MATCH` | KAJY ne pratique pas le maintien |

---

## 15. MAPPING KAJY ↔ POSTGRESQL (`conge_journal_droit`)

| Cellule KAJY | Colonne `conge_journal_droit` | Type BDD | Correspondance |
|---|---|---|---|
| Col E (`BASE`) | `salaire_base` | NUMERIC(12,2) | Directe |
| Col D (`formule`) | `jours_travailles_formule` | NUMERIC(5,2) | Directe |
| Col S (`nbre droit congés`) | `jours_droit_acquis` | NUMERIC(6,4) | Directe |
| Col R (`base totale`) | `base_valorisation` | NUMERIC(12,2) | Directe |
| Col T (`R*S/30`) | `valorisation_mensuelle` | NUMERIC(12,2) | Directe |
| Col U (`cumul NB JR DROIT`)| `cumul_jours_droit` | NUMERIC(8,4) | Directe |
| Col V (`Cumul Valorisation`)| `cumul_valorisation` | NUMERIC(12,2) | Directe |
| Col W (`taux journalier`) | `taux_journalier_conge` | NUMERIC(12,2) | Directe |

---

## 16. PREUVE PAR LE TEST GOLDEN MASTER KAJY

Un test de caractérisation officiel a été créé :
[KajyLeaveCalculationGoldenMasterUnitTest.java](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/test/java/mg/galaxam/sirh/unit/characterization/KajyLeaveCalculationGoldenMasterUnitTest.java)

### Résultat de l'Exécution Maven
```text
[INFO] Running Phase 2A-4B : Golden Master Test Référence KAJY GALAXAM (12 Mois Havana)
Mois  1 | Droit: Excel=2,5000 Calcule=2,5000 | Valo: Excel=23528,14 Calcule=23528,14 | CumulVal: Excel=23528,14 Calcule=23528,14
Mois  2 | Droit: Excel=2,4992 Calcule=2,4992 | Valo: Excel=25986,26 Calcule=25986,26 | CumulVal: Excel=49514,40 Calcule=49514,40
Mois  3 | Droit: Excel=2,4933 Calcule=2,4933 | Valo: Excel=24559,19 Calcule=24559,19 | CumulVal: Excel=74073,59 Calcule=74073,59
Mois  4 | Droit: Excel=2,5000 Calcule=2,5000 | Valo: Excel=24213,54 Calcule=24213,54 | CumulVal: Excel=98287,13 Calcule=98287,13
Mois  5 | Droit: Excel=2,5000 Calcule=2,5000 | Valo: Excel=25896,70 Calcule=25896,70 | CumulVal: Excel=124183,83 Calcule=124183,83
Mois  6 | Droit: Excel=2,4683 Calcule=2,4683 | Valo: Excel=28491,02 Calcule=28491,02 | CumulVal: Excel=152674,85 Calcule=152674,85
Mois  7 | Droit: Excel=2,5000 Calcule=2,5000 | Valo: Excel=29302,29 Calcule=29302,29 | CumulVal: Excel=181977,15 Calcule=181977,14
Mois  8 | Droit: Excel=2,5000 Calcule=2,5000 | Valo: Excel=29034,65 Calcule=29034,65 | CumulVal: Excel=211011,80 Calcule=211011,79
Mois  9 | Droit: Excel=2,4967 Calcule=2,4967 | Valo: Excel=29080,72 Calcule=29080,72 | CumulVal: Excel=240092,52 Calcule=240092,51
Mois 10 | Droit: Excel=2,5000 Calcule=2,5000 | Valo: Excel=29433,02 Calcule=29433,02 | CumulVal: Excel=269525,54 Calcule=269525,53
Mois 11 | Droit: Excel=2,5000 Calcule=2,5000 | Valo: Excel=25414,00 Calcule=25414,00 | CumulVal: Excel=294939,54 Calcule=294939,53
Mois 12 | Droit: Excel=2,4992 Calcule=2,4992 | Valo: Excel=29445,83 Calcule=29445,83 | CumulVal: Excel=324385,38 Calcule=324385,36
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.383 s
[INFO] BUILD SUCCESS
```

---

## 17. CONTRE-AUDIT DES CONCLUSIONS DE LA PHASE 2A-4A

| Point d'Audit Phase 2A-4A | Conclusion Phase 2A-4A | Réalité Démontrée par KAJY | Statut Contradictoire |
|---|---|---|---|
| **Cumul Tous Temps** | "Le runtime souffre d'un bug de cumul infini" | KAJY est une feuille annuelle stricte de 12 mois. Le cumul tous temps provient de l'absence de clause de réinitialisation dans `conge_journal_droit`. | `CONFIRMED_BY_KAJY` (Divergence technique confirmée) |
| **Intégration des Primes** | "Il faut intégrer les primes régulières (Art. 88)" | KAJY exclut catégoriquement `AUTIMP` de la base de valorisation congés. La pratique GALAXAM est de ne retenir que `NetBase + HS`. | `CONTRADICTED_BY_KAJY` (Pratique GALAXAM confirmée) |
| **Maintien de Salaire** | "Il faut appliquer $\max(1/12, \text{Base}/30)$" | KAJY applique la stricte moyenne annuelle pondérée (10 828,49 Ar/j), même après une augmentation à 350 000 Ar (trentième = 11 666,67 Ar/j). | `CONTRADICTED_BY_KAJY` (Pas de maintien dans KAJY) |
| **Diviseur 30** | "Démonstration du trentième requise" | KAJY utilise rigoureusement `2.5 / 30` et `/ 30`, validant la relation $1/12\text{ème}$ d'année calendaire. | `CONFIRMED_BY_KAJY` |
| **TCONGE au Mois le Mois**| "TCONGE calculé dynamiquement chaque mois" | Dans KAJY, le TCONGE appliqué pendant l'année est un taux fixe annuel issu de l'année précédente (`8669.52`). Le nouveau taux (`10828.49`) n'est calculé qu'au 12ème mois pour l'année suivante. | `CONTRADICTED_BY_KAJY` (TCONGE KAJY = Taux annuel fixe) |

---

## 18. CONTRE-AUDIT JURIDIQUE SÉPARÉ : KAJY vs CODE DU TRAVAIL MALGACHE

| Sujet | Méthode KAJY (Pratique GALAXAM) | Loi Malgache 2024-014 | Écart Constaté | Qualification Juridique | Décision Recommandée |
|---|---|---|---|---|---|
| **Période de référence** | 12 mois de l'année de référence | 12 mois précédant le congé (Art. 131) | Aucun écart conceptuel sur la durée (12 mois) | `NO_GAP` | Conserver la fenêtre de 12 mois (corriger le cumul tous temps SQL) |
| **Assiette Primes** | Exclut `AUTIMP` | Art. 88 inclut primes, commissions, prestations | Primes exclues chez GALAXAM | `POTENTIAL_COMPLIANCE_GAP` | Arbitrage RH requis : maintenir KAJY ou conformité stricte Art. 88 |
| **Maintien de salaire** | Non appliqué (moyenne stricte inférieure au salaire final) | Plancher légal 1/12ème respecté | KAJY respecte le texte littéral du 1/12ème mais pas le principe de faveur | `LEGAL_INTERPRETATION_REQUIRED` | Optionnel : maintien de salaire si GALAXAM le souhaite |
| **Taux fixe vs glissant** | Taux fixe annuel de l'exercice N-1 | Fenêtre des 12 mois précédant le départ | Taux annuel fixe en décalage de quelques mois | `BUSINESS_RULE_REQUIRED` | Choisir entre taux annuel fixe de campagne vs taux glissant 12 mois |

---

## 19. QUESTIONS MÉTIER REFORMULÉES POUR L'ARBITRAGE FINAL

Grâce au reverse engineering de KAJY, les questions d'arbitrage sont désormais parfaitement ciblées :

1. **Assiette des Primes (`AUTIMP`) :**  
   Le fichier KAJY exclut formellement les primes imposables (`AUTIMP`) de l'assiette des congés.  
   *Question :* GALAXAM souhaite-t-elle **conserver la méthode KAJY historique** (assiette = Salaire de base net d'absences + Heures Sup), ou intégrer certaines primes contractuelles fixes conformément à l'article 88 ?
2. **Cycle de Révision du TCONGE (Annuel Fixe vs Glissant 12 Mois) :**  
   Dans KAJY, le TCONGE est calculé **une fois par an** à la clôture du 12ème mois, puis appliqué fixement toute l'année suivante.  
   *Question :* Le SIRH doit-il fonctionner avec un **TCONGE annuel de campagne** (calqué sur KAJY), ou avec une **fenêtre glissante continue des 12 derniers mois** recalculée à chaque clôture de paie ?
3. **Traitement des Augmentations de Salaire (Maintien de Salaire Optionnel) :**  
   KAJY n'applique pas de maintien de salaire contractuel : un salarié augmenté prend ses congés à son taux moyen pondéré historique.  
   *Question :* GALAXAM souhaite-t-elle maintenir cette règle KAJY (stricte moyenne), ou instaurer la règle du plus favorable $\max(\text{TCONGE}_{1/12}, \text{SalaireBase} / 30)$ ?

---

## 20. RECOMMANDATIONS POUR LA PHASE 2A-4C (PLAN D'IMPLÉMENTATION)

1. **Sanctuariser la règle KAJY comme baseline certifiée :** Le Golden Master test assure qu'aucune modification intempestive ne détruira la formule GALAXAM.
2. **Corriger la divergence SQL de `conge_journal_droit` :** Borner le cumul à 12 mois (ou introduire le snapshot annuel de campagne) pour éliminer le cumul tous temps non souhaité.
3. **Attendre les arbitrages utilisateur :** Ne coder l'intégration des primes ou le maintien de salaire que si et seulement si l'arbitrage RH le valide expressément.

---

## 21. VERDICT DE LA PHASE 2A-4B

```text
================================================================================
           PHASE 2A-4B : REVERSE ENGINEERING KAJY TERMINÉ ET VALIDÉ
================================================================================
[X] Fichier XLS officiel (OHATRA KAJY DROITS CONGES) ouvert et extrait par POI
[X] Formules et dépendances inter-cellules extraites et documentées intégralement
[X] DroitMois (= D * 2.5 / 30) et NetBase (= E - G + J) rigoureusement expliqués
[X] BaseTotale démontrée : contient NetBase + HS, EXCLUT formellement AUTIMP
[X] Origine du cumul tous temps démontrée (drift SQL vs tableau annuel KAJY de 12 mois)
[X] TCONGE annuel KAJY reconstitué (10 828.49 Ar/j) à 0.02 Ar près sur 12 mois
[X] Golden Master test unitaire créé et vert (KajyLeaveCalculationGoldenMasterUnitTest)
[X] Comparaison KAJY ↔ Java ↔ PostgreSQL formalisée
[X] Contre-audit juridique séparé réalisé sans confusion des ordres de vérité
[X] Aucune modification de formule en production durant cette phase
================================================================================
VERDICT : PHASE_2A_4B_KAJY_REVERSE_ENGINEERING_PASS
================================================================================
```
