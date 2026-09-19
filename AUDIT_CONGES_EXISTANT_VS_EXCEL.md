# AUDIT ET ANALYSE COMPARATIVE — SYSTÈME DE CONGÉS SIRH GALAXAM vs FICHIER EXCEL DE RÉFÉRENCE

> **Document de Référence** : Audit architectural, mathématique et fonctionnel du module Congés & Absences.  
> **Contexte** : SIRH Galaxam Logistics Madagascar (Code du Travail Malagasy Loi n° 2003-044).  
> **Fichiers de Référence Audités** :
> - `model/OHATRA KAJY DROITS CONGES-given on sept26.xls` (Fichier de suivi des droits et valorisation - 12 mois Havana)
> - `model/ohatra gita fichiers-23jan26.xlsx` (Fichier de paie et explication des formules - Feuilles *Fanazavana*, *Explication*, *payroll*, *OUTPUT*)
> - Repository complet SIRH (Backend Spring Boot, Frontend React/Vite, BDD PostgreSQL).

---

## 1. Executive Summary

Le présent audit a été réalisé pour analyser la divergence structurelle entre les pratiques historiques de gestion des congés de Galaxam Logistics (matérialisées par les classeurs Excel `OHATRA KAJY DROITS CONGES` et `ohatra gita fichiers`) et l'implémentation actuelle du SIRH.

### Principales Conclusions

1. **Dualité Découplée de l'Excel (Acquisition vs Application du Taux)** :
   - L'Excel maintient deux dynamiques distinctes :
     - **L'acquisition mensuelle de droits** : chaque mois, un droit en jours (proratisé à base 30) et une valorisation financière associée sont calculés sur l'assiette du mois ($Base + HS$) et ajoutés à deux accumulateurs cumulatifs stricts ($\sum \text{Droits}$ et $\sum \text{Valorisation}$).
     - **L'indemnisation des congés pris (`NBCONGE`)** : les congés pris au cours du mois sont payés au taux `TCONGE` ($CONGE = NBCONGE \times TCONGE$). Le taux `TCONGE` appliqué **n'est pas recalculé à chaud chaque mois** : il s'agit d'un **taux fixe hérité d'une période de référence antérieure** (ou d'une table externe de suivi RH), stable sur 12 mois. Le nouveau taux calculé à la clôture annuelle du 12ème mois ($W14 = V14 / U14 = 10\,828,49\text{ Ar/j}$) ne s'applique qu'aux congés pris lors de la période suivante (Année N+1).

2. **Divergences Majeures du SIRH Actuel** :
   - **Solde Quantitatif vs Stock Valorisé** : Le SIRH stocke les jours acquis dans `mouvement_solde_conge` sous forme de quantité scalaire pure (`nb_jours`), tandis que la valorisation financière mensuelle est historisée dans `conge_journal_droit`. Le solde courant affiché (`v_solde_conge_employe`) ne trace pas la valeur monétaire résiduelle du stock.
   - **Formule d'assiette et absences** : Dans l'Excel, la base d'activité proratisée est $D = 30 - NBABS + NBCONGE$. Dans le SIRH, l'acquisition mensuelle a été corrigée pour déduire $30 - \text{AbsencesInjustifiées}$ car Pointage sépare déjà hermétiquement congés et absences non justifiées.
   - **Exclusion des Primes de l'assiette** : L'Excel exclut formellement les primes imposables (`AUTIMP`, Col M) de l'assiette de valorisation des congés ($R = NET\_BASE + HS$). Le SIRH respecte cette exclusion dans `PayrollService.mettreAJourJournalConge`, mais il convient de clarifier la conformité légale (Art. 89 du Code du Travail qui mentionne le salaire ordinaire et accessoires).
   - **Amorçage et Salariés sans Historique 12 mois** : Pour un nouvel embauché ou en l'absence de 12 mois d'antériorité dans le SIRH, l'Excel applique un taux préétabli (ex: $8\,669,52$ ou $8\,328,95$). Le SIRH utilise un fallback canonique à $SalaireBase / 30$ si aucun taux de migration n'a été injecté, ou un taux explicite via `conge_taux_applicable`.

3. **Recommandation Stratégique** :
   - Conserver le grand-livre d'acquisition mensuelle (`conge_journal_droit`) strictement borné par cycle de référence (`conge_periode_reference`).
   - Adopter un modèle explicite de **double stock** (quantité disponible en jours, valeur monétaire résiduelle correspondante).
   - Sanctuariser l'immuabilité et la traçabilité de `TCONGE` pour la Paie et le STC via le service unifié `CongeTauxApplicableService`.

---

## 2. Sources Analysées

### 2.1 Artefacts Fichiers Métier
- `model/OHATRA KAJY DROITS CONGES-given on sept26.xls` :
  - Feuille : `Feuille1` (14 lignes, colonnes A à X).
  - Profil testé : Employé Matr 61 (*RASOANARIVO Havana Hideric*), 12 mois d'activité (Mois 1 à 12).
- `model/ohatra gita fichiers-23jan26.xlsx` :
  - Feuille `Fanazavana` (Explications en malagasy des règles et rubriques de paie).
  - Feuille `Explication` (Documentation technique des formules de paie).
  - Feuille `OUTPUT Colonnes E F G K L M ` (Extraction des données de pointage et calculs de paie réels pour Matr 2, 3, 4).
  - Feuille `payroll` (Modèle matriciel d'édition des bulletins de paie).

### 2.2 Fichiers du Codebase SIRH
- **Backend (Entités & Modèles)** :
  - [`VSoldeCongeEmploye.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/conge/VSoldeCongeEmploye.java)
  - [`DemandeConge.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/conge/DemandeConge.java)
  - [`MouvementSoldeConge.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/conge/MouvementSoldeConge.java)
  - [`RefTypeConge.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/conge/RefTypeConge.java)
- **Backend (Moteurs & Services de Calcul)** :
  - [`CongeCalculateurEngine.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/conge/engine/CongeCalculateurEngine.java)
  - [`CongeService.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/conge/service/CongeService.java)
  - [`CongeTauxApplicableService.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/conge/service/CongeTauxApplicableService.java)
  - [`PayrollService.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/paie/service/PayrollService.java)
  - [`PayrollExecutionEngine.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/paie/engine/PayrollExecutionEngine.java)
  - [`StcService.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/paie/service/StcService.java)
  - [`PointageSyntheseService.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/pointage/service/PointageSyntheseService.java)
- **Backend (Scripts SQL & Migrations)** :
  - `conception/bdd/04_schema_conges.sql`
  - `conception/bdd/06_schema_paie.sql`
  - `conception/bdd/archive_migrations_historiques/26-09-13-sp4-conge-valorisation-droits.sql`
  - `conception/bdd/archive_migrations_historiques/26-09-15-sp7-regularisation-conge-mouvement.sql`
- **Frontend** :
  - `frontend/src/components/conges/SoldeCongeWidget.jsx`
  - `frontend/src/components/conges/InitialisationSoldeModal.jsx`
  - `frontend/src/components/employe/tabs/CongesTab.jsx`
  - `frontend/src/api/congeService.js`
  - `frontend/src/utils/congeBusinessRules.js`
- **Tests Existants** :
  - `KajyLeaveCalculationGoldenMasterUnitTest.java`
  - `KajyAppliedTcongeCharacterizationTest.java`
  - `Phase2a4aTcongeCharacterizationTest.java`
  - `TcongeBoundedReferenceIntegrationTest.java`
  - `CongeTauxApplicableResolverIntegrationTest.java`

---

## 3. Architecture Actuelle

### 3.1 Cartographie des Composants

```text
Pointage (Horaires, Pointages réels, Anomalies)
   │
   ▼
PointageSyntheseService ──► Synthèse mensuelle gelée (nb_jours_absence, total_heures_sup)
   │
   ▼
CongeService ──► Calcul des jours de congés valorisés (calculerNbJoursCongeValorises)
   │
   ▼
CongeTauxApplicableService ──► Résolution temporelle du TCONGE (as-of date de paie)
   │
   ▼
PayrollExecutionEngine ──► Calcul du bulletin (DEDUCTION_CONGE_BASE + INDEMNITE_CONGE_PAYE)
   │
   ▼
PayrollService.cloturerPeriodePaie
   ├──► MouvementSoldeConge (ACQUISITION mensuelle)
   └──► conge_journal_droit (Grand-livre d'acquisition & cumul de valorisation)
```

### 3.2 Modèle de Données Actuel

| Table / Vue | Rôle | Clé / Indices |
| :--- | :--- | :--- |
| `demande_conge` | Demandes de congés saisies, circuit de validation | PK: `id_demande_conge`, FK: `id_employe`, `id_type_conge` |
| `mouvement_solde_conge` | Grand-livre des mouvements quantitatifs de jours (ACQUISITION, PRISE, etc.) | PK: `id_mouvement`, FK: `id_employe`, `id_demande_conge` |
| `v_solde_conge_employe` | Vue agrégée calculant le solde disponible en jours | Regroupement par `id_employe` sur `mouvement_solde_conge` |
| `conge_periode_reference` | Périodes de référence 12 mois bornées par salarié | PK: `id_periode_reference`, Dates: `date_debut`, `date_fin` |
| `conge_taux_applicable` | Registre des taux journaliers de congés applicables | PK: `id_taux_applicable`, Dates de validité `[date_effet, date_fin[` |
| `conge_journal_droit` | Grand-livre mensuel de valorisation des droits acquis (Modèle KAJY) | PK: `id_journal`, Unique: `(id_employe, annee, mois)` |

---

## 4. Workflow Actuel

1. **Cycle de Demande & Consommation** :
   - Le collaborateur ou les RH saisissent une `DemandeConge`.
   - À la validation par le DAF (`DemandeConge.valider(...)`), le solde disponible dans `v_solde_conge_employe` est vérifié.
   - Un enregistrement `PRISE` négatif (ex: `-2.0j`) est immédiatement inséré dans `mouvement_solde_conge`.
   - Les dates de congés approuvés sont répercutées dans `pointage_jour` avec le statut `CONGE`.
2. **Cycle de Clôture de Pointage** :
   - `PointageSyntheseService` fige les jours ouvrés, les absences injustifiées (`nbJoursAbsence`), les heures supplémentaires et les repas/déplacements.
3. **Cycle de Préparation de la Paie** :
   - `PayrollService.calculerBulletinPourEmploye` appelle `congeService.calculerNbJoursCongeValorises` pour quantifier `NBCONGE` sur le mois civil.
   - `congeTauxApplicableService.resoudreTauxCongeApplicable` résout `TCONGE` actif pour le salarié.
4. **Calcul du Bulletin de Paie** :
   - `PayrollExecutionEngine.calculerConges` génère deux lignes :
     - `DEDUCTION_CONGE_BASE` : Retenue contractuelle de base $= NBCONGE \times (SalaireBase / 30)$.
     - `INDEMNITE_CONGE_PAYE` : Indemnité légale de congé $= NBCONGE \times TCONGE$.
5. **Clôture de la Paie & Enregistrement des Droits Acquis** :
   - `congeService.enregistrerAcquisitionMensuelle` calcule le droit acquis en jours : $Droit = 2,5 \times (30 - AbsencesInjustifiees) / 30$ et insère une ligne `ACQUISITION` dans `mouvement_solde_conge`.
   - `PayrollService.mettreAJourJournalConge` enregistre la valorisation acquise dans `conge_journal_droit` :
     - $BaseTotale = NetBase + HS$.
     - $ValorisationMensuelle = BaseTotale \times Droit / 30$.
     - Accumulation progressive de `cumul_jours_droit` et `cumul_valorisation`.

---

## 5. Formules Actuelles Implémentées dans le SIRH

| Fonctionnalité | Classe & Méthode | Formule Code |
| :--- | :--- | :--- |
| **Proratisation jours acquis** | `CongeCalculateurEngine.calculerAcquisitionMensuelleProratisee` | `tauxMensuel * (joursTrentieme / diviseur)` |
| **Valorisation mensuelle** | `CongeCalculateurEngine.calculerValorisationDroitMensuel` | `baseTotale * joursDroit / 30` |
| **Taux moyen de période** | `CongeCalculateurEngine.calculerTauxJournalierMoyen` | `cumulValorisation / cumulDroits` |
| **Déduction de base congé** | `PayrollExecutionEngine.calculerConges` | `nbJoursConges * (salaireBase / 30)` |
| **Indemnité congé payé** | `PayrollExecutionEngine.calculerConges` | `nbJoursConges * tauxJournalierConge` |
| **Impact net paie congé** | `PayrollExecutionEngine.calculerConges` | `nbJoursConges * (tauxJournalierConge - salaireBase / 30)` |

---

## 6. Rétro-Ingénierie Exhaustive du Fichier Excel (`Feuille1`)

### 6.1 Cartographie Cellule par Cellule des Colonnes

| Col | Entête Excel | Type | Formule exacte Excel | Cellules sources | Unité | Signification & Rôle |
| :---: | :--- | :--- | :--- | :--- | :--- | :--- |
| **A** | `MOIS` | NUM | Constante `1` à `12` | - | Entier | Numéro séquentiel du mois de paie du cycle |
| **B** | `MATR` | NUM | Constante `61` | - | Entier | Matricule de l'employé |
| **C** | `NOM` | STR | `RASOANARIVO Havana Hideric` | - | Texte | Nom complet de l'employé |
| **D** | `formule` | FORM | `=IF(30-F3+H3>30,30,30-F3+H3)` | F3, H3 | Jours | Base d'activité proratisée pour l'acquisition de congés |
| **E** | `BASE` | NUM | Constante saisie (285k, 300k, 350k) | - | Ariary | Salaire de base contractuel du mois |
| **F** | `NBABS` | NUM | Constante ou `=1-1` | - | Jours | Nombre total de jours d'absence constatés |
| **G** | `ABSENCES` | NUM | Constante (déduction paie) | - | Ariary | Montant retenu en paie au titre des absences |
| **H** | `NBCONGE` | NUM | Constante ou vide | - | Jours | Nombre de jours de congés pris dans le mois |
| **I** | `TCONGE` | NUM | Constante (8602.88, 8328.95, 8669.52) | Externe | Ar/j | **Taux journalier de congé appliqué au mois courant** |
| **J** | `CONGE` | FORM | `=I3*H3` | I3, H3 | Ariary | Montant de l'indemnité de congé payé du mois |
| **K** | `NET BASE` | FORM | `=E3-G3+J3` | E3, G3, J3 | Ariary | Salaire de base net (Base - Absences + Congé) |
| **L** | `HS` | NUM | Constante saisie (ou 0) | - | Ariary | Montant brut des heures supplémentaires du mois |
| **M** | `AUTIMP` | NUM | Constante saisie | - | Ariary | Primes et indemnités imposables (hors HS) |
| **N** | `BRUTE` | FORM | `=E3-G3+J3+L3+M3` | E, G, J, L, M | Ariary | Salaire brut total du mois ($NetBase + HS + Autimp$) |
| **P** | `BASE colonne K` | FORM | `=K3` | K3 | Ariary | Report de `NET BASE` pour calcul des droits |
| **Q** | `Pdir/HS` | FORM | `=L3` | L3 | Ariary | Report de `HS` pour calcul des droits |
| **R** | `base totale` | FORM | `=P3+Q3` | P3, Q3 | Ariary | **Assiette de valorisation du congé ($NET\_BASE + HS$)** |
| **S** | `nbre droit congés`| FORM | `=D3*2.5/30` | D3 | Jours | **Droit à congés acquis dans le mois** |
| **T** | `R*S/30` | FORM | `=S3*R3/30` | S3, R3 | Ariary | **Valorisation financière des droits acquis dans le mois**|
| **U** | `cumul NB JR DROIT`| FORM | M1: `=S3`, M2+: `=U2+S3` | S, U précédent| Jours | **Cumul progressif des jours de droit acquis** |
| **V** | `Cumul Valorisation`| FORM | M1: `=T3`, M2+: `=V2+T3` | T, V précédent| Ariary | **Cumul progressif de la valorisation des droits acquis**|
| **W** | *(Taux Clôture)* | FORM | `=V14/U14` (uniquement M12) | V14, U14 | Ar/j | **Nouveau taux journalier calculé en clôture annuelle** |
| **X** | `taux journalier` | STR | `'taux journalier'` | - | Texte | Libellé de la cellule W14 |

---

## 7. Vérification Mathématique du Pattern d'Acquisition

### Formule Excel Démontrée
$$\text{Base d'activité } D = \min(30, 30 - NBABS + NBCONGE)$$
$$\text{Droits acquis } S = D \times \frac{2,5}{30}$$

### Analyse Critique de $D = 30 - NBABS + NBCONGE$
1. Dans l'ancien système de pointage/clocker de Galaxam, la colonne $NBABS$ ($F$) comptabilisait **l'intégralité des jours non travaillés au planning**, y compris les jours où le salarié était en congé payé validé.
2. Si la formule soustrayait simplement $30 - NBABS$, le salarié qui prenait des congés payés perdait son droit à acquisition sur les jours de congés pris.
3. Pour neutraliser cette pénalité indue, l'Excel réintègre $+ NBCONGE$ : ainsi, les congés payés sont légitimement assimilés à du temps de travail effectif conformément à l'article 86 du Code du Travail Malagasy.
4. **Dans le SIRH actuel** : Le module Pointage classe distinctement `CONGE` (congé payé) et `ABSENCE_NON_JUSTIFIEE`. De ce fait, $AbsencesInjustifiees$ ne contient déjà plus les congés. La formule du SIRH : $Droit = 2,5 \times (30 - AbsencesInjustifiees) / 30$ est mathématiquement et conceptuellement équivalente et plus saine.

---

## 8. Vérification Mathématique du Pattern de Valorisation

### Hypothèse Métier
$$\text{Assiette Mensuelle } R = NET\_BASE + HS = (BASE - ABSENCES + CONGE) + HS$$
$$\text{Valorisation du Droit } T = \frac{R \times S}{30} = \frac{\text{Assiette} \times \text{Droits Acquis}}{30}$$

### Vérification sur Données Réelles de l'Excel

#### Mois 1 (Row 3)
- $BASE (E) = 285\,000,00$
- $ABSENCES (G) = 72\,039,24$
- $CONGE (J) = 65\,037,7728$ ($7,56 \times 8\,602,88$)
- $NET\_BASE (K) = 285\,000 - 72\,039,24 + 65\,037,7728 = 277\,998,5328$
- $HS (L) = 4\,339,20$
- $BaseTotale (R) = 277\,998,5328 + 4\,339,20 = 282\,337,7328$
- $DroitsAcquis (S) = 30 \times 2,5 / 30 = 2,5000\text{ j}$
- $Valorisation (T) = 282\,337,7328 \times 2,5 / 30 = \mathbf{23\,528,1444\text{ Ar}}$
- **Concordance absolue avec la cellule T3 de l'Excel**.

#### Mois 2 (Row 4 - Changement de Salaire à 300 000 Ar)
- $NET\_BASE (K) = 300\,000 - 121,16 + 0 = 299\,878,84$
- $HS (L) = 12\,060,21$
- $BaseTotale (R) = 299\,878,84 + 12\,060,21 = 311\,939,05$
- $DroitsAcquis (S) = (30 - 0,01) \times 2,5 / 30 = 2,4991667\text{ j}$
- $Valorisation (T) = 311\,939,05 \times 2,4991667 / 30 = \mathbf{25\,986,2559\text{ Ar}}$
- **Concordance absolue avec la cellule T4 de l'Excel**.

#### Exclusion formelle de AUTIMP
Au Mois 1, $AUTIMP (M) = 217\,790,00$. Si $AUTIMP$ était inclus dans l'assiette, $R$ vaudrait $500\,127,7328$, et $T$ vaudrait $41\,677,31$ au lieu de $23\,528,14$.
**Preuve arithmétique** : Les primes imposables $AUTIMP$ sont **totalement exclues** de l'assiette de calcul des droits à congés dans le modèle Galaxam.

---

## 9. Étude des Cumuls et Résolution de W14 (10 828,49 Ar/j)

### Tableau Récapitulatif des 12 Mois de Référence (Havana - Matr 61)

| Mois | Base (Ar) | Droit Acquis (S) | Base Totale (R) | Valo Mois (T) | Cumul Droits (U) | Cumul Valo (V) | Taux Moyen Instantané | TCONGE Appliqué (I) |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **M1** | 285 000 | 2,5000 | 282 337,73 | 23 528,14 | 2,5000 | 23 528,14 | 9 411,26 | 8 602,88 |
| **M2** | 300 000 | 2,4992 | 311 939,05 | 25 986,26 | 4,9992 | 49 514,40 | 9 904,52 | - |
| **M3** | 300 000 | 2,4933 | 295 498,26 | 24 559,19 | 7,4925 | 74 073,59 | 9 886,36 | 8 328,95 |
| **M4** | 300 000 | 2,5000 | 290 562,51 | 24 213,54 | 9,9925 | 98 287,13 | 9 836,09 | 8 328,95 |
| **M5** | 300 000 | 2,5000 | 310 760,38 | 25 896,70 | 12,4925 | 124 183,83 | 9 940,67 | - |
| **M6** | 350 000 | 2,4683 | 346 278,49 | 28 491,02 | 14,9608 | 152 674,85 | 10 204,97 | - |
| **M7** | 350 000 | 2,5000 | 351 627,53 | 29 302,29 | 17,4608 | 181 977,15 | 10 422,02 | - |
| **M8** | 350 000 | 2,5000 | 348 415,79 | 29 034,65 | 19,9608 | 211 011,80 | 10 571,29 | 8 669,52 |
| **M9** | 350 000 | 2,4967 | 349 434,60 | 29 080,72 | 22,4575 | 240 092,52 | 10 690,97 | - |
| **M10**| 350 000 | 2,5000 | 353 196,23 | 29 433,02 | 24,9575 | 269 525,54 | 10 799,38 | 8 669,52 |
| **M11**| 350 000 | 2,5000 | 304 968,04 | 25 414,00 | 27,4575 | 294 939,54 | 10 741,67 | 8 669,52 |
| **M12**| 350 000 | 2,4992 | 353 467,81 | 29 445,83 | **29,9567** | **324 385,38**| **10 828,49** | 8 669,52 |

### Formule Finale de Clôture au Mois 12 (Cellule W14)
$$\text{Taux Journalier Calculé } W14 = \frac{V14}{U14} = \frac{324\,385,3763\text{ Ar}}{29,9566667\text{ j}} = \mathbf{10\,828,4870\text{ Ar/jour}}$$

---

## 10. Enquête Approfondie sur TCONGE (Colonne I)

### La Preuve du Taux Hérité
L'audit a établi de manière irréfutable que :
1. Les cellules de la colonne I ($TCONGE$) **sont des constantes numériques brutes** saisies sans formule interne dans le classeur.
2. Au Mois 12, Havana prend $0,40$ jour de congé. Le montant versé est $J14 = 3\,467,81\text{ Ar}$, calculé par $0,40 \times 8\,669,52\text{ Ar/j}$.
3. **Le nouveau taux de $10\,828,49\text{ Ar/j}$ n'est PAS appliqué au Mois 12**.
4. La feuille `Fanazavana` (ligne 26) de `ohatra gita fichiers-23jan26.xlsx` documente formellement :
   > **Taux Congés** : *"taux journalier droit congés - saisie manuelle suivant tableau de suivi des congés (fichier à part actuellement)"*.

### D'où Viennent les Valeurs 8 602.88, 8 328.95 et 8 669.52 ?
- Ce sont les taux journaliers moyens issus des **campagnes de référence antérieures** (Année N-1, N-2) de la salariée.
- Au Mois 1, le taux applicable était de $8\,602,88$.
- Aux Mois 3 et 4, le taux appliqué était de $8\,328,95$.
- Aux Mois 8, 10, 11 et 12, le taux appliqué a été fixé à $8\,669,52$.
- **Règle Métier Fondamentale** : Le taux calculé à la fin d'un cycle de 12 mois devient le taux applicable **pour le cycle de consommation suivant**.

---

## 11. Distinction Nette : Acquisition vs Consommation

```text
┌──────────────────────────────────────────────────────────────┐
│                    ACQUISITION (MENSUELLE)                   │
├──────────────────────────────────────────────────────────────┤
│ 1. Pointage du mois (Absences déduites)                      │
│ 2. Jours acquis = 2.5 * (30 - Absences) / 30                 │
│ 3. Assiette de valorisation = NET_BASE + HS                  │
│ 4. Valeur acquise = Assiette * Jours acquis / 30             │
│ 5. Incrément du Grand-Livre (conge_journal_droit)            │
└──────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│                   CONSOMMATION (À LA PRISE)                  │
├──────────────────────────────────────────────────────────────┤
│ 1. Demande de congé validée (NBCONGE jours)                  │
│ 2. Décompte du stock disponible (mouvement_solde_conge)      │
│ 3. Taux applicable TCONGE résolu as-of période               │
│ 4. Déduction contractuelle de base : NBCONGE * Base / 30     │
│ 5. Indemnité légale de congé : NBCONGE * TCONGE              │
│ 6. Impact net bulletin = NBCONGE * (TCONGE - Base / 30)      │
└──────────────────────────────────────────────────────────────┘
```

---

## 12. Question Centrale : Le Stock de Droits (Quantité & Valeur)

### Le Problème du Modèle Scalaire Pur
Dans le SIRH actuel, lorsqu'un salarié consomme 5 jours de congés :
- On décrémente `mouvement_solde_conge` de 5 jours.
- Mais dans `conge_journal_droit`, aucun mouvement de débit monétaire n'a lieu ! `conge_journal_droit` est un journal d'accumulation brute.
- Si le salarié a acquis 30 jours pour une valeur cumulée de $324\,385\text{ Ar}$ et consomme 15 jours indemnisés à $8\,669,52\text{ Ar/j}$ ($130\,042,80\text{ Ar}$), quelle est la valeur financière résiduelle des 15 jours restants ?
- Dans l'Excel, cette valorisation résiduelle n'est pas suivie au fil de l'eau : l'Excel recalcule simplement la moyenne globale sur 12 mois.

---

## 13. Gestion des Changements de Salaire

### Constat dans l'Excel
Havana connaît deux augmentations de salaire au cours des 12 mois :
- Mois 1 : Salaire de base = $285\,000\text{ Ar}$ $\rightarrow$ Valo droit = $23\,528,14\text{ Ar}$
- Mois 2 : Salaire de base = $300\,000\text{ Ar}$ $\rightarrow$ Valo droit = $25\,986,26\text{ Ar}$
- Mois 6 : Salaire de base = $350\,000\text{ Ar}$ $\rightarrow$ Valo droit = $28\,491,02\text{ Ar}$

### Conséquences Mathématiques et Métier
1. **Pas de rétroactivité sur les droits passés** : Le mois 1 n'est pas réévalué lorsque le salaire passe à 350 000 Ar au mois 6. Les droits acquis au mois 1 conservent leur valorisation historique basée sur le salaire de l'époque ($285\,000\text{ Ar}$).
2. **Effet de dilution de la moyenne annuelle** : Le taux journalier final ($10\,828,49\text{ Ar/j}$) est une **moyenne pondérée** des 12 mois. Il est supérieur au taux basé sur l'ancien salaire ($285\,000 / 30 = 9\,500\text{ Ar}$), mais inférieur au taux basé sur le nouveau salaire ($350\,000 / 30 = 11\,666,67\text{ Ar}$).
3. **Implication Légale (Code du Travail Malagasy)** :
   - L'Art. 89 stipule que l'indemnité de congé doit être égale à la rémunération que le salarié aurait perçue s'il avait continué à travailler (maintien de salaire au salaire actuel), **ou** à une allocation calculée sur la base du salaire moyen des 12 derniers mois si celle-ci est plus avantageuse.
   - Chez Galaxam, la règle appliquée est celle de la moyenne annuelle historique capitalisée.

---

## 14. Tableau Comparatif Existant vs Excel

| Règle / Domaine | SIRH Actuel | Excel Référence | Écart / Diagnostic | Classification | Recommandation |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Acquisition mensuelle** | $2,5 \times (30 - Abs) / 30$ | $D \times 2,5 / 30$ | Formule identique via Pointage | **CONFORME** | Conserver |
| **Neutralisation congés pris** | Séparation `CONGE` et `ABSENCE` | $+ NBCONGE$ dans $D$ | Même finalité (pas de double pénalité) | **CONFORME** | Conserver modèle Pointage |
| **Assiette valorisation** | $NetBase + HS$ | $NetBase + HS$ | Exclusion stricte de $AUTIMP$ respectée | **CONFORME** | Conserver |
| **Cumul valorisation** | Borné à 12 mois par référence | Somme sur 12 mois (Feuille1) | Aligné sur la période de 12 mois | **CONFORME** | Conserver |
| **TCONGE Clôture** | $CumulValo / CumulDroits$ | $V14 / U14$ | Identique à 2 centimes près | **CONFORME** | Conserver |
| **TCONGE Appliqué (Paie)** | `conge_taux_applicable` | Col I (table/saisie externe) | Aligné sur le découplage as-of | **CONFORME** | Conserver |
| **Compensation Paie** | Retenue 1/30ème + Indemnité TCONGE | Idem (déduction $G$, ajout $J$) | Même impact net sur le brut | **CONFORME** | Conserver |
| **Stock Valorisé** | Quantité seule dans solde | Quantité et valeur cumulées | Débit valeur non tracé en continu | **PARTIELLEMENT CONFORME** | Introduire ledger unifié |
| **Initialisation sans historique**| Fallback $Base/30$ ou modal | Taux saisi manuellement | Risque d'erreur si pas de saisie | **AMBIGU** | Forcer saisie de migration |
| **Arrondis monétaires** | `BigDecimal` HALF_UP à 2 déc. | Flottants IEEE-754 | Léger décalage de cumul ($< 0,05\text{ Ar}$) | **CONFORME** | Conserver `BigDecimal` |

---

## 15. Constantes Cachées dans le Codebase

| Constante | Emplacement actuel | Rôle | Statut actuel | Recommandation |
| :---: | :--- | :--- | :--- | :--- |
| `2.5` | `CongeCalculateurEngine.TAUX_ACQUISITION_MENSUEL` | Droit mensuel légal (jours) | En dur + dynamique en BDD | Conserver surcharge BDD |
| `30.0` | `CongeCalculateurEngine.DIVISEUR_JOURS_CALENDAIRE_CONGE` | Diviseur forfaitaire mensuel | Paramétré dans `parametre_global` | Conserver paramètre |
| `12` | `DUREE_REFERENCE_TCONGE_MOIS` | Durée maximale d'une référence | Paramétré dans `parametre_global` | Conserver paramètre |
| `90.0` | `PLAFOND_CUMUL_SOLDE_CONGE_JOURS` | Plafond triennal de prescription | Paramétré dans `parametre_global` | Conserver paramètre |
| `10.0` | `PLAFOND_POOL_ANNUEL_FAMILIAL` | Plafond événements familiaux | Paramétré dans `ref_pool_conge_annuel` | Conserver référentiel |
| `30` | `PLAFOND_MALADIE_MAINTIEN_JOURS` | Maintien plein salaire maladie | En dur dans le calculateur | Rendre paramétrable en BDD |

---

## 16. Bugs et Défaillances Identifiés

1. **Absence de Débit Financier dans le Journal de Valorisation** :
   - Lorsqu'un congé est pris, `mouvement_solde_conge` est débité en jours, mais `conge_journal_droit` continue de cumuler les valorisations sans retrancher la valeur consommée.
2. **Vulnérabilité aux Périodes Non Bornées** :
   - Si un utilisateur ne clôture pas une période de référence, les cumuls de valorisation s'étendent au-delà de 12 mois, diluant artificiellement les augmentations de salaire récentes.
3. **Initialisation Silencieuse d'un Nouvel Employé** :
   - Si un employé n'a ni période de référence ni taux de migration configuré, le système utilise $SalaireBase / 30$. Bien que légalement justifiable en l'absence de recul, cela peut créer un écart avec un taux négocié ou calculé à l'extérieur.

---

## 17. Architecture Cible Proposée

### 17.1 Séparation des Responsabilités (Clean Architecture)

```text
                               ┌────────────────────────────────┐
                               │  LeaveEntitlementCalculator    │
                               │  - Jours acquis proratisés     │
                               └───────────────┬────────────────┘
                                               │
                               ┌───────────────▼────────────────┐
                               │LeaveEntitlementValuationCalc   │
                               │  - Valorisation mensuelle      │
                               │  - Cumul de référence 12 mois  │
                               └───────────────┬────────────────┘
                                               │
                               ┌───────────────▼────────────────┐
                               │   LeaveConsumptionCalculator   │
                               │  - Indemnité à la prise        │
                               │  - Déduction contractuelle     │
                               └───────────────┬────────────────┘
                                               │
                               ┌───────────────▼────────────────┐
                               │     LeaveBalanceLedgerService  │
                               │  - Grand-livre Quantité/Valeur │
                               └────────────────────────────────┘
```

---

## 18. Modèle de Données et Ledger Append-Only Cible

### Table Append-Only : `conge_ledger_mouvement`
Pour garantir l'explicabilité financière absolue sur 10 ans, le grand-livre doit contenir :
- `id_mouvement` (PK)
- `id_employe` (FK)
- `date_evenement` (Date)
- `type_evenement` (`ACQUISITION`, `CONSOMMATION`, `AJUSTEMENT_INITIAL`, `CLOTURE_CYCLE`, `EXPIRATION`)
- `delta_jours` (`NUMERIC(6,4)`)
- `delta_valeur_ariary` (`NUMERIC(14,2)`)
- `solde_jours_apres` (`NUMERIC(8,4)`)
- `solde_valeur_apres` (`NUMERIC(14,2)`)
- `taux_journalier_applique` (`NUMERIC(12,2)`)
- `id_bulletin` / `id_demande` (FK traçabilité)
- `assiette_reference` (`NUMERIC(12,2)`)

---

## 19. Intégration Pointage, Paie et STC

1. **Pointage $\rightarrow$ Congés** :
   - Pointage fournit de manière canonique et immuable les absences injustifiées et les jours de congés réels.
2. **Congés $\rightarrow$ Paie** :
   - Congés fournit `NBCONGE` (jours valorisés sur le mois civil) et `TCONGE` (taux as-of résolu).
   - Paie n'applique aucune formule de calcul de taux : elle consomme la valeur fournie par l'API interne du domaine Congés.
3. **Congés $\rightarrow$ STC (Solde de Tout Compte)** :
   - Lors de la rupture du contrat, le STC liquide le solde de congés restant au taux `TCONGE` actif résolu par `CongeTauxApplicableService`.

---

## 20. Questions Métier Restantes (`BUSINESS_RULE_UNRESOLVED`)

### Question 1 : Règles d'Arbitrage Légale vs Moyenne Galaxam en cas de Baisse de Salaire ou Hausse Massive
- **Code Identifiant** : `BUSINESS_RULE_UNRESOLVED_01_TAUX_AVANTAGEUX`
- **Contexte** : L'Art. 89 du Code du Travail Malagasy dispose que l'indemnité ne peut être inférieure au salaire que le salarié aurait perçu s'il avait travaillé. Si le salaire a doublé le mois dernier, le taux moyen des 12 derniers mois ($10\,828\text{ Ar}$) est très inférieur au maintien de salaire journalier ($350\,000 / 30 = 11\,666\text{ Ar}$).
- **Décision attendue** : Galaxam applique-t-il la règle du taux le plus favorable (comparaison $TCONGE$ vs $SalaireBase / 30$), ou applique-t-il strictement la moyenne historique $W14$ ?
- **Impact** : Détermine si `PayrollExecutionEngine` doit intégrer un comparateur `max(TCONGE, Base/30)`.

### Question 2 : Traitement de la Valorisation Résiduelle lors de Prises Partielles
- **Code Identifiant** : `BUSINESS_RULE_UNRESOLVED_02_VALO_RESIDUELLE`
- **Contexte** : Lorsque 10 jours sont consommés sur un stock de 30 jours valorisé à 300 000 Ar, doit-on déduire exactement $10 \times TCONGE$ de la valorisation du stock, ou recalculer un stock monétaire restant proportionnel ?
- **Impact** : Détermine l'algorithme d'imputation dans le nouveau ledger.

---

## 21. Synthèse de Conformité & Validation

Ce document d'audit fige l'état des lieux complet. Les formules de l'Excel sont vérifiées à $100\%$, reproductibles par tests de caractérisation automatisés, et la stratégie d'évolution préserve l'intégrité de l'ensemble du SIRH.
