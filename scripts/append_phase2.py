content = """
# 2. CLASSIFICATION EXHAUSTIVE DE CHAQUE COLONNE (PHASE 2)

Conformément à la directive de certification, aucune donnée HISTORICAL_CALCULATED ou HISTORICAL_FORMULA ne peut être utilisée directement comme entrée (input) de l'oracle ou du runtime.
Seules les données RAW_INPUT, REFERENCE_INPUT et MANUAL_ADJUSTMENT constituent les véritables entrées du problème.

---

## 2.1. Classeur Paie : `model/ohatra presence svt clocker.xlsx` (Onglet `EJOURNAL`)

| Col | Nom En-tête | Rôle Métier | Classification | Statut pour l'Oracle / Runtime |
| :---: | :--- | :--- | :---: | :--- |
| **B (2)** | `MATR` | Matricule salarié | **`REFERENCE_INPUT`** | Entrée d'identification |
| **C (3)** | `NOM` | Nom du salarié | **`REFERENCE_INPUT`** | Entrée d'identification |
| **D (4)** | `formule` | Différence brute `=IF(F-H>0,F-H,0)` | **`DERIVED_LEGACY`** | Non utilisé (erreur conceptuelle legacy) |
| **E (5)** | `BASE` | Salaire de base contractuel | **`REFERENCE_INPUT`** | Entrée canonique de paie |
| **F (6)** | `NBABS` | Absence brute déclarée | **`DERIVED_LEGACY`** | Non utilisé directement (mélange heures/jours) |
| **G (7)** | `ABSENCES` | Retenue financière d'absence | **`HISTORICAL_CALCULATED`** | Sortie à vérifier contre l'oracle |
| **H (8)** | `NBCONGE` | Jours de congés payés pris | **`RAW_INPUT`** | Entrée d'imputation congés |
| **I (9)** | `TCONGE` | Taux journalier applicable | **`REFERENCE_INPUT`** | Entrée de valorisation de congé |
| **J (10)**| `CONGE` | Indemnité congé payé | **`HISTORICAL_FORMULA`** | Sortie à vérifier contre l'oracle |
| **K (11)**| `DEPLACEMENT` | Indemnité transport | **`RAW_INPUT`** | Entrée variable |
| **L (12)**| `HS` | Rémunération des heures sup | **`HISTORICAL_CALCULATED`** | Sortie recalculée via pointage brut |
| **M (13)**| `AUTIMP` | Primes imposables diverses | **`RAW_INPUT`** | Entrée variable soumise |
| **N (14)**| `BRUTE` | Salaire brut total | **`HISTORICAL_FORMULA`** | Sortie à vérifier contre l'oracle |
| **O (15)**| `CNAPS/formule`| CNaPS salariale (1% plafonné) | **`HISTORICAL_FORMULA`** | Sortie sociale à recalculer |
| **P (16)**| `OSTIE/formule`| Cotisation médicale OSTIE | **`HISTORICAL_FORMULA`** | Sortie santé (assimilation legacy O=CNaPS) |
| **Q (17)**| `OMSI/formule` | Cotisation médicale OMSI (1.5%)| **`HISTORICAL_CALCULATED`** | Sortie santé propre à TMM |
| **R (18)**| `NETI` | Salaire net avant impôt | **`HISTORICAL_FORMULA`** | Sortie intermédiaire |
| **S (19)**| `Imposable` | Base IRSA (arrondi 100 Ar) | **`HISTORICAL_FORMULA`** | Sortie fiscale |
| **T (20)**| `IRSA/formule` | Impôt progressif | **`HISTORICAL_FORMULA`** | Sortie fiscale (ancien barème) |
| **U (21)**| `IRSA Arrondi` | Arrondi supérieur à 10 | **`HISTORICAL_FORMULA`** | Sortie fiscale |
| **V (22)**| `RED ENFT` | Déduction enfants à charge | **`REFERENCE_INPUT`** | Entrée fiscale (nb enfants) |
| **W (23)**| `AVANCE` | Avance sur salaire | **`MANUAL_ADJUSTMENT`** | Entrée de retenue nette |
| **X (24)**| `avce spec` | Avance spéciale | **`MANUAL_ADJUSTMENT`** | Entrée de retenue nette |
| **Y (25)**| `NET` | Net fiscal avant panier/call | **`HISTORICAL_FORMULA`** | Sortie intermédiaire |
| **Z (26)**| `repas` | Indemnité panier repas | **`RAW_INPUT`** | Entrée non soumise |
| **AA (27)**| `net avant call` | Net avec panier avant retenues | **`HISTORICAL_FORMULA`** | Sortie intermédiaire |
| **AB (28)**| `CALL PERSO` | Retenue comm. privées | **`MANUAL_ADJUSTMENT`** | Entrée de retenue nette |
| **AC (29)**| `NET À PAYER` | Net financier final | **`HISTORICAL_FORMULA`** | Sortie à vérifier |
| **AD (30)**| `ARRONDI NET` | Arrondi dizaine Ariary | **`HISTORICAL_FORMULA`** | Sortie finale payée |

---

## 2.2. Classeur Pointage : `model/ohatra presence svt clocker.xlsx` (Onglet `pointage extrait machine`)

| Plage Colonnes | Nom En-tête | Rôle Métier | Classification |
| :---: | :--- | :--- | :---: |
| **A / T / AM** | `Date / Jour` | Date calendaire du jour | **`REFERENCE_INPUT`** |
| **B / U / AN** | `Entrée Matin` | Horodatage brut machine (ex. 07:49:59) | **`RAW_INPUT`** |
| **D / W / AP** | `Sortie Matin` | Horodatage brut machine (ex. 12:00:00) | **`RAW_INPUT`** |
| **G / Z / AS** | `Entrée Après-midi` | Horodatage brut machine (ex. 13:00:00) | **`RAW_INPUT`** |
| **I / AB / AU** | `Sortie Après-midi` | Horodatage brut machine (ex. 16:36:59) | **`RAW_INPUT`** |
| **K / AD / AW** | `Heures Matin` | Durée travaillée matin | **`HISTORICAL_FORMULA`** |
| **M / AF / AY** | `Heures Après-midi` | Durée travaillée après-midi | **`HISTORICAL_FORMULA`** |
| **O / AH / BA** | `Total Heures` | Durée journalière brute | **`HISTORICAL_FORMULA`** |
| **P / AI / BB** | `Heures Décimal` | Conversion en heures décimales | **`HISTORICAL_FORMULA`** |

---

## 2.3. Classeur Congés KAJY : `model/OHATRA KAJY DROITS CONGES-given on sept26.xls` (Feuille 1)

| Col | En-tête | Rôle Métier | Classification |
| :---: | :--- | :--- | :---: |
| **0** | `MOIS` | Numéro d'ordre du mois (1 à 12) | **`REFERENCE_INPUT`** |
| **1** | `MATR` | Matricule salarié (61.0) | **`REFERENCE_INPUT`** |
| **2** | `NOM` | Nom salarié (Havana Hideric) | **`REFERENCE_INPUT`** |
| **3** | `formule` | Présence/jours ouvrés du mois | **`RAW_INPUT`** |
| **4** | `BASE` | Salaire de base contractuel du mois | **`REFERENCE_INPUT`** |
| **5** | `NBABS` | Jours d'absence du mois | **`RAW_INPUT`** |
| **6** | `ABSENCES` | Retenue pour absence du mois | **`HISTORICAL_CALCULATED`** |
| **7** | `NBCONGE` | Jours de congés payés pris | **`RAW_INPUT`** |
| **8** | `TCONGE` | Taux journalier appliqué aux congés | **`REFERENCE_INPUT`** |
| **9** | `CONGE` | Montant de l'indemnité de congé | **`HISTORICAL_FORMULA`** |
| **11** | `HS` | Heures sup rémunérées du mois | **`RAW_INPUT`** |
| **12** | `AUTIMP` | Primes imposables du mois | **`RAW_INPUT`** |
| **13** | `BRUTE` | Salaire brut du mois | **`HISTORICAL_CALCULATED`** |
| **17** | `base totale` | Base de valorisation du droit à congé | **`HISTORICAL_CALCULATED`** |
| **18** | `nbre droit congés` | Droit acquis du mois (`2.5 * formule / 30`) | **`HISTORICAL_CALCULATED`** |
| **19** | `R*S/30` | Valorisation financière du mois | **`HISTORICAL_CALCULATED`** |
| **20** | `cumul NB JR DROIT` | Cumul progressif des droits acquis | **`HISTORICAL_CALCULATED`** |
| **21** | `Cumul Valorisation` | Cumul progressif de la valorisation | **`HISTORICAL_CALCULATED`** |
| **22** | `taux journalier (W14)` | TCONGE de clôture (`CumulVal / CumulDroits`) | **`HISTORICAL_CALCULATED`** |
"""

with open('GALAXAM_EXCEL_SOURCE_MAP.md', 'a', encoding='utf-8') as f:
    f.write(content)
print("Section 2 ajoutée avec succès.")
