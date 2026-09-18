# CYCLE DE VIE ET MODÈLE DE VALORISATION TCONGE
**Date :** 17 Septembre 2026  
**Référence :** Phase 2A-2 Audit Contradictoire Complet  
**Système :** GALAXAM SIRH  

---

## 1. ÉTAT DES LIEUX : LE TCONGE ACTUEL

### 1.1. Comment est-il calculé aujourd'hui ?
- **Calculateur :** `PayrollService.mettreAJourJournalConge` (méthode privée dans le module Paie).
- **Moment :** Lors de la **clôture définitive** d'une période de paie (`PayrollService.cloturerDonneesPaie`).
- **Source des Données :**
  - Le bulletin de paie qui vient d'être clôturé (`bp.getSalaireBase()`, `bp.getJoursAbsence()`).
  - Les lignes du bulletin (`BulletinPaieLigne`) :
    - Déduction des retenues d'absence et retenues congé.
    - Ajout des indemnités de congé.
    - Ajout des rubriques de la catégorie `HEURES_SUP`.
  - La ligne immédiatement antérieure dans `conge_journal_droit` :
    ```sql
    SELECT cumul_jours_droit, cumul_valorisation FROM conge_journal_droit 
    WHERE id_employe = ? AND (annee < ? OR (annee = ? AND mois < ?)) 
    ORDER BY annee DESC, mois DESC LIMIT 1
    ```
- **Formule :**
  $$\text{Droit Mois} = 2.5 \times \frac{\max(0, 30.0 - \text{Absences Injustifiées})}{30.0}$$
  $$\text{Base Totale} = \text{Net Base} + \text{Heures Sup}$$
  $$\text{Valo Mois} = \frac{\text{Base Totale} \times \text{Droit Mois}}{30.0}$$
  $$\text{Cumul Droits} = \text{Cumul Droits Précédent} + \text{Droit Mois}$$
  $$\text{Cumul Valo} = \text{Cumul Valo Précédent} + \text{Valo Mois}$$
  $$\text{TCONGE} = \frac{\text{Cumul Valo}}{\text{Cumul Droits}}$$
- **Stockage :** Table `conge_journal_droit` (située dans `06_schema_paie.sql`).
- **Amorçage (Premier Mois) :** Si aucune ligne antérieure n'existe dans `conge_journal_droit`, `StcService.getTauxJournalierApplicable` applique :
  $$\text{TCONGE}_{\text{fallback}} = \frac{\text{Salaire de Base}}{30.0}$$

---

### 1.2. Problèmes et Faiblesses du Modèle Actuel

1. **Calcul après Consommation (Décalage Temporel Systématique) :**
   - Lorsqu'un bulletin de paie du mois $M$ est calculé, la ligne du mois $M$ n'existe pas encore dans `conge_journal_droit` (elle n'est créée qu'à la clôture).
   - Le moteur de paie utilise donc obligatoirement le TCONGE du mois $M-1$ (ou antérieur).
   - Ce décalage d'un mois est structurel, mais s'il n'y a aucun historique, le fallback $Base / 30$ est appliqué même si le salarié a travaillé 15 jours le mois en cours.
2. **Cumul Infini sans Fenêtre Glissante (Biais d'Ancienneté) :**
   - L'accumulation `cumul_valorisation` et `cumul_jours_droit` ne s'arrête jamais.
   - Les salaires et heures supplémentaires d'il y a 5 ou 10 ans continuent de peser dans la moyenne du taux journalier, réduisant artificiellement le TCONGE face à un salaire qui a augmenté.
   - Or, en droit malgache du travail (Art. 89), l'indemnité de congé doit refléter le salaire moyen de la **période de référence des 12 derniers mois** (ou depuis l'embauche si $< 12$ mois).
3. **Exclusion des Majorations Légales :**
   - La formule filtre uniquement `l.getCategorie().equalsIgnoreCase("HEURES_SUP")`.
   - Les majorations de nuit (`MAJORATION_NUIT`), de travail du dimanche (`DIMANCHE_TRAVAILLE`) et de jour férié (`FERIE_TRAVAILLE`) sont arbitrairement ignorées, faussant la base légale de rémunération.
4. **Instabilité en Cas de Réouverture :**
   - Si une période de paie antérieure est rouverte puis re-clôturée, `conge_journal_droit` est écrasé via `ON CONFLICT DO UPDATE`, mais les cumuls des mois suivants ne sont pas recalculés en cascade.

---

## 2. MODÈLE CIBLE DE VALORISATION TCONGE

### 2.1. Principes Fondamentaux de la Conception Cible

1. **Règle des 12 Mois Glissants (Période de Référence Légale) :**
   - Le TCONGE est calculé sur la base de la rémunération brute soumise des **12 mois civils précédant** le mois de prise du congé (ou la totalité des mois disponibles si l'ancienneté est inférieure à 1 an).
2. **Comparaison Légale du Maintien de Salaire :**
   - L'indemnité journalière de congé ne peut être inférieure à la rémunération que le salarié aurait perçue s'il avait continué à travailler (règle du trentième de maintien) :
     $$\text{TCONGE} = \max\left(\frac{\text{Cumul Rémunérations Réf.}}{\text{Cumul Droits Réf.}}, \frac{\text{Salaire Contractuel Actuel}}{30.0}\right)$$
3. **Périmètre Exhaustif des Rémunérations Incluses :**
   - Salaire de base contractuel.
   - Heures supplémentaires (HS 30%, HS 50%).
   - Majorations temporelles (Nuit, Dimanche, Jours Fériés).
   - Primes régulières inhérentes au travail (hors remboursements de frais réels).
   - Proratisé rigoureusement selon les absences effectives.

---

### 2.2. Les Deux Moments Clés : Calcul vs Gel

```text
  DATE DEMANDE / SIMULATION
  │
  ▼
  [1. CALCUL ESTIMATIF] (TCONGE Indicatif)
  - Donnée dynamique fournie par CongeValuationService.
  - Permet d'afficher au RH et à l'employé le coût prévisionnel.
  - Statut : ESTIMATED.
  
  CLÔTURE DU POINTAGE (Validation Mensuelle)
  │
  ▼
  [2. CALCUL & GEL DE VALORISATION] (TCONGE Scellé)
  - Déclenché à la fin de la période de présence (ex: au 25 ou 30 du mois).
  - Constaté sur les jours de congés réels non travaillés.
  - Enregistré dans conge_valuation_snapshot.
  - Statut : FROZEN (Immutable).
  
  EXÉCUTION DE LA PAIE
  │
  ▼
  [3. CONSOMMATION PASSIVE PAR PAYROLL]
  - La Paie lit conge_valuation_snapshot.
  - ZÉRO calcul de TCONGE dans PayrollExecutionEngine.
  - Lignes de bulletin créées directement à partir du snapshot.
  - Statut : SCELLED_PAID.
```

---

## 3. STRUCTURE DU SNAPSHOT DE VALORISATION CIBLE

Au lieu de disperser la valorisation dans des champs éparpillés, une structure scellée dédiée est prévue :

```text
Table : conge_valuation_snapshot
─────────────────────────────────────────────────────────────────────────────
id_snapshot                SERIAL PRIMARY KEY
id_employe                 INT NOT NULL REFERENCES employe(id_employe)
id_periode_paie            INT NOT NULL REFERENCES periode_paie(id_periode_paie)
jours_conges_payables      NUMERIC(5,2) NOT NULL -- Jours effectifs validés
taux_journalier_conge      NUMERIC(12,2) NOT NULL -- TCONGE figé
montant_retenue_base       NUMERIC(12,2) NOT NULL -- jours * (salaireBase / 30)
montant_indemnite_conge    NUMERIC(12,2) NOT NULL -- jours * TCONGE
methode_calcul             VARCHAR(30) NOT NULL -- 'DOUZIEME_GLISSANT', 'BASE_30_MAINTIEN'
base_reference_cumulee     NUMERIC(14,2) NOT NULL -- Somme des 12 mois
droits_reference_cumules   NUMERIC(8,4) NOT NULL -- Somme des droits des 12 mois
nb_mois_reference          INT NOT NULL -- 12 (ou moins si embauche récente)
details_json               JSONB -- Historique mois par mois pour explicabilité UI
statut                     VARCHAR(20) NOT NULL -- 'FROZEN', 'PROCESSED_IN_PAYROLL'
scelle_par                 INT REFERENCES utilisateur(id_utilisateur)
scelle_le                  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
version                    INT DEFAULT 1
CONSTRAINT uk_conge_snapshot_emp_periode UNIQUE (id_employe, id_periode_paie)
```

---

## 4. CONSOMMATION DIFFÉRENCIÉE : PAIE vs STC

### 4.1. Ce que la Paie Consomme
- **Entrée :** `conge_valuation_snapshot` rattaché à l'employé et à la période de paie.
- **Actions du Moteur de Paie :**
  1. Génère la rubrique `RETENUE_CONGE` avec le montant `montant_retenue_base`.
  2. Génère la rubrique `INDEMNITE_CONGE` avec le montant `montant_indemnite_conge`.
  3. Porte le `taux_journalier_conge` sur la ligne de bulletin pour impression sur la fiche de paie.
- **Règle d'or :** Si aucun jour de congé n'a été constaté sur la période, aucun snapshot n'est généré et aucune ligne de congé n'apparaît sur le bulletin.

### 4.2. Ce que le STC Consomme
- **Problématique distincte :** Le STC ne valorise pas un congé pris, mais liquide un **stock de droits acquis restants non pris** lors du départ.
- **Entrée :**
  - Le solde net en jours depuis le Grand-Livre (`v_solde_conge_employe.solde_actuel`).
  - Le TCONGE à date de départ résolu par `CongeValuationService.calculerTauxDate(idEmploye, dateDepartEffective)`.
- **Indemnité Compensatrice :**
  $$\text{ICCP} = \text{Solde Net Jours} \times \text{TCONGE}$$
- **Isolement :** Le STC ne dépend pas d'un snapshot mensuel d'absence, mais utilise le **même moteur mathématique de valorisation** que le domaine Congés, garantissant une parfaite équité de traitement.
