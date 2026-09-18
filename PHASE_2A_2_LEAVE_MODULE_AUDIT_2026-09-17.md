# PHASE 2A-2 — AUDIT CONTRADICTOIRE COMPLET DU MODULE CONGÉS & TCONGE
**Date :** 17 Septembre 2026  
**Auteur :** Antigravity (Auditeur Pair-Programming)  
**Baseline Runtime :** Backend 429 PASS (0 échec, 0 erreur) | Frontend 16 PASS | Build Vite PASS | git diff --check PASS  
**Verdict :** `PHASE_2A_2_AUDIT_COMPLETE`

---

## 1. RÉSUMÉ EXÉCUTIF & CONSTATS CRITIQUES

L'audit contradictoire exhaustif du module **Congés, Droits, TCONGE et Valorisation** a mis en évidence des fractures architecturales fondamentales, dissimulées derrière une suite de tests au vert.

### Les 5 Fractures Majeures Découvertes au Runtime :

1. **Dualité Incohérente des Mécanismes d'Acquisition (Collision de Grand-Livre) :**
   - **Compteur de Solde en Jours :** `AcquisitionCongeScheduler` s'exécute chaque nuit à 01h00, parcourt les contrats actifs et insère dans `mouvement_solde_conge` (+2.5j ou plafonné) à chaque date anniversaire d'embauche, sans AUCUN prorata pour les absences injustifiées du mois !
   - **Grand-Livre Financier :** `PayrollService.mettreAJourJournalConge` s'exécute à la clôture de la paie et insère dans `conge_journal_droit` un droit proratisé selon les absences injustifiées (`2.5 * (30 - abs) / 30`).
   - **Conséquence :** Le solde en jours affiché à l'employé et la base de droits valorisée en paie divergent silencieusement dès qu'il y a une absence injustifiée.

2. **Divergence Calendaire Majeure (Collision Congé ↔ Pointage) :**
   - **Module Congé :** Décompte en jours calendaires stricts (`ChronoUnit.DAYS.between(debut, fin) + 1`). Une semaine du lundi au dimanche débite **7,0 jours** du solde dans `mouvement_solde_conge`.
   - **Module Pointage :** `PointageSyntheseService` filtre explicitement les week-ends et les jours fériés (`!isWeekend && !isFerie`). Pour cette même semaine, il ne compte que **5,0 jours**.
   - **Module Paie :** `bp.setJoursConges(pointageSnapshot.nb_jours_conge)` consomme le snapshot de pointage (5,0 jours) pour déduire `RETENUE_CONGE` et indemniser `INDEMNITE_CONGE`.
   - **Conséquence :** 2 jours de solde sont amputés au salarié dans son compte de congés sans jamais être indemnisés ni décomptés en paie !

3. **Temporalité de Consommation Prématurée :**
   - Le solde de congé est débité (`MouvementSoldeConge(type='PRISE')`) dès la validation RH/DAF de la demande, des semaines ou des mois avant que le congé ne soit pris.
   - Si un congé approuvé n'est finalement pas pris (salarié présent et pointé), aucun retour arrière n'est automatisé. Le pointage enregistre la présence, la paie paie les heures travaillées, mais le solde de congés reste amputé !

4. **Absence de Garde-Fou sur l'Annulation de Congés Passés :**
   - `DemandeConge.annuler` permet d'annuler une demande validée même si la période de paie et le pointage sont clôturés/archivés depuis plusieurs mois, réinjectant des jours dans le solde actuel alors qu'une indemnité de congé a déjà été versée sur un bulletin scellé.

5. **Invisibilité Totale du TCONGE dans l'Expérience Utilisateur :**
   - Aucune page, onglet ou composant de l'interface Congés n'affiche le TCONGE, la valorisation monétaire du solde ou la provenance du taux journalier. L'utilisateur RH navigue à l'aveugle sur la dimension financière des congés.

---

## 2. BASELINE TECHNIQUE VÉRIFIÉE

```text
===============================================================================
BACKEND (Maven / JUnit 5 + Spring Boot) :
  Tests run: 429, Failures: 0, Errors: 0, Skipped: 0
  Temps total : 02:51 min
  Statut : BUILD SUCCESS

FRONTEND (Vitest) :
  Test Files: 3 passed (3)
  Tests: 16 passed (16)
  Temps : 329 ms
  Statut : PASS

FRONTEND BUILD (Vite) :
  Built in 749 ms
  dist/ assets générés sans aucune erreur

GIT TREE INTEGRITY :
  git diff --check : 0 erreur
===============================================================================
```

---

## 3. INVENTAIRE ET AUDIT DU FRONTEND

### 3.1. Matrice des Écrans et Composants

| Page / Composant | Route / Emplacement | Rôle Autorisé | Données Affichées | Actions Métier | API Appelée | Statut Audit |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **GestionCongesPage** | `/conges` | `CONGE_READ`, `CONGE_WRITE`, `CONGE_VALIDATE` | Onglets Soldes et Demandes, badge demandes en attente | Navigation entre onglets | `getAllDemandes`, `getAllReferentiels` | **ACTIVE_CANONICAL** |
| **SoldesOverviewTab** | `/conges?tab=soldes` | `CONGE_READ` | Gantt mensuel des absences, KPIs (en attente, absents), planning | Changement mois/semaine, recherche, filtre | `getPlanningCollaborateurs`, `getAllDemandes` | **UX_REDESIGN_RECOMMENDED** |
| **SoldesTable** | `/conges?tab=soldes` (vue table) | `CONGE_READ` | Liste des absences du mois, salarié, dates, nb jours décomptés, statut | Lien vers fiche employé, tri | Données du planning | **CLEAN** |
| **DemandesValidationTab** | `/conges?tab=demandes` | `CONGE_VALIDATE` | Tableau des demandes, filtres statut et type | Valider, Refuser, Annuler, Modifier | `validerDemande`, `refuserDemande`, `annulerDemande` | **ACTIVE_CANONICAL** |
| **CongesTab (Fiche Employé)** | `/employes/:id?tab=conges` | `EMPLOYE_READ`, `CONGE_READ` | Solde actuel, total acquis, pris, bonifié, expiré, conso pool 10j, historique demandes, registre mouvements | Poser un congé, annuler demande | `getSoldeEmploye`, `getMouvementsEmploye`, `getDemandesEmploye`, `getConsommationPool` | **UX_REDESIGN_RECOMMENDED** |
| **DemandeCongeModal** | Modale partagée | `CONGE_WRITE` | Formulaire dates, type, justificatif, conflit de poste, solde dispo | Soumettre demande, simulation décompte | `simulerDemande`, `verifierConflitsDemande`, `creerDemande`, `updateDemande` | **ACTIVE_CANONICAL** |
| **SoldeCongeWidget** | Widget fiche employé | `CONGE_READ` | 4 compteurs (solde, acquis, pris, bonifié), bandeau ancienneté | Aucune (affichage seul) | Données transmises par `CongesTab` | **BUSINESS_CALCULATION_DRIFT** |
| **MouvementsSoldeTable** | Fiche employé | `CONGE_READ` | Historique des mouvements de solde (date, type, nb_jours, commentaire) | Aucune | Données de `getMouvementsEmploye` | **ACTIVE_CANONICAL** |

### 3.2. Analyse Ergonomique et UX

1. **Absence du Taux et de la Valorisation :**
   - Le RH ne voit nulle part à combien est valorisé 1 jour de congé pour un collaborateur (`TCONGE`).
   - L'indemnité compensatrice de congés payés (ICCP) en cas de départ ou le coût d'une absence est impossible à anticiper depuis le module Congés.
2. **Logique Métier Déplacée dans React (`BUSINESS_CALCULATION_DRIFT`) :**
   - `SoldeCongeWidget.jsx` (lignes 20-80) calcule côté frontend l'ancienneté en mois, l'éligibilité légale aux congés (règle des 12 mois Art. 83) et la date d'ouverture des droits.
   - Ce calcul devrait provenir directement du backend (`AncienneteEngine` / API solde).
3. **Erreur d'Exploitation lors de la Modification d'un Congé Validé :**
   - `DemandeCongeController.java` (lignes 277-288) tente de réinsérer un mouvement `PRISE` lors d'un `PUT` sans annuler le précédent, risquant d'enfreindre la contrainte unique `idx_uniq_mouvement_prise_demande`.

---

## 4. AUDIT BIDIRECTIONNEL DU RUNTIME

### 4.1. Cycle Réel Constaté

```text
1. ACQUISITION EN JOURS (Scheduler)
   AcquisitionCongeScheduler (01:00)
   └── SELECT contrat ACTIF
   └── if (today >= date_debut.dayOfMonth)
   └── INSERT INTO mouvement_solde_conge (type='ACQUISITION', nb_jours=2.5)
   [NOTE : Ignore totalement les absences injustifiées du mois en cours]

2. DEMANDE DE CONGÉ (Frontend / REST)
   DemandeCongeModal.jsx
   └── POST /conges/demandes/simuler -> CongeCalculateurEngine.calculerNombreJours (dateFin - dateDebut + 1)
   └── POST /conges/demandes/verifier-conflits -> CongeService.verifierConflitsDemande
   └── POST /conges/demandes -> CongeService.creerDemande
   └── INSERT INTO demande_conge (statut='EN_ATTENTE', nb_jours_decomptes=X.X)

3. VALIDATION RH / DAF
   DemandesValidationTab.jsx
   └── POST /conges/demandes/{id}/valider -> CongeService.validerDemande
   └── UPDATE demande_conge SET id_statut = 'VALIDE'
   └── INSERT INTO mouvement_solde_conge (type='PRISE', nb_jours = -nb_jours_decomptes)
   [ATTENTION : Le solde v_solde_conge_employe est diminué ici, avant la date de prise effective !]

4. SAISIE & POINTAGE QUOTIDIEN
   PointageSaisieService.getSaisieJournaliere
   └── SELECT FROM demande_conge WHERE date BETWEEN date_debut AND date_fin AND statut IN ('VALIDE', 'APPROUVEE')
   └── if (conge_trouve) -> pointage_jour.codeStatut = 'CONGE' (ou DISPENSE_POINTAGE si non décompté)

5. SYNTHÈSE MENSUELLE POINTAGE & GEL
   PointageSyntheseService.calculerSyntheseMensuelle
   └── SELECT FROM pointage_jour WHERE codeStatut = 'CONGE'
   └── if (!isWeekend && !isFerie) -> conges++ (jours ouvrés uniquement)
   └── PointageSyntheseService.figerPointage
   └── INSERT INTO pointage_synthese_employe (nb_jours_conge = conges)
   └── INSERT INTO pointage_snapshot_mensuel

6. VALORISATION TCONGE & CALCUL DE PAIE
   PayrollService.preparerInput
   └── input.setNbJoursConges(pointage_synthese_employe.nb_jours_conge)
   PayrollService.calculerEmploye
   └── TCONGE = StcService.getTauxJournalierApplicable(idEmploye, annee, mois, base)
       └── SELECT taux_journalier_conge FROM conge_journal_droit WHERE id_employe=? AND (annee < ? OR (annee=? AND mois < ?)) ORDER BY annee DESC, mois DESC LIMIT 1
       └── Fallback si absent : salaireBase / 30.0
   PayrollExecutionEngine.calculerConges
   └── RETENUE_CONGE   = input.nbJoursConges * (salaireBase / 30.0)
   └── INDEMNITE_CONGE = input.nbJoursConges * TCONGE
   └── Sauvegarde BulletinPaie et BulletinPaieLigne

7. CLÔTURE DE PAIE & GRAND-LIVRE DROITS
   PayrollService.cloturerDonneesPaie
   └── PayrollService.mettreAJourJournalConge
       └── Jours travaillés formule = 30 - Absences Injustifiées
       └── Droit mois = 2.5 * formule / 30
       └── Base valorisation = (Base - Retenues + Indemnités) + Heures Sup
       └── Valo mois = Base valorisation * Droit mois / 30
       └── Cumul jours = Cumul prec + Droit mois
       └── Cumul valo = Cumul valo prec + Valo mois
       └── Taux journalier = Cumul valo / Cumul jours
       └── UPSERT INTO conge_journal_droit (annee, mois, taux_journalier_conge, ...)
```

---

## 5. AUDIT DU MODÈLE DE DONNÉES

### 5.1. Tables Existantes

| Table | Responsabilité Réelle | PK / Unicité | Statut & Historisation | Risque Identifié |
| :--- | :--- | :--- | :--- | :--- |
| `ref_type_conge` | Types de congés et règles de décompte | `id_type_conge` (PK), `code` (UQ) | Référentiel paramétrable | `CLEAN` |
| `ref_statut_demande_conge` | Statuts du workflow de demande | `id_statut_demande_conge` (PK), `code` (UQ) | Référentiel fixe | `CLEAN` |
| `ref_motif_evenement_familial` | Motifs d'événements familiaux et plafonds | `id_motif` (PK), `code` (UQ) | Référentiel paramétrable | `CLEAN` |
| `ref_pool_conge_annuel` | Quotas partagés (10j permissions) | `id_pool` (PK), `code` (UQ) | Référentiel paramétrable | `CLEAN` |
| `demande_conge` | Demandes de congés posées par les salariés | `id_demande_conge` (PK) | Dates début/fin, nb_jours, statut, validateur | Décompte calendaire brut en collision avec Pointage |
| `mouvement_solde_conge` | Ledger transactionnel des jours de congé | `id_mouvement` (PK), UQ(demande, PRISE) | Append-only (ACQUISITION, PRISE, ANNULATION, EXPIRATION, AJUSTEMENT) | Double source d'acquisition avec `conge_journal_droit` |
| `conge_journal_droit` | Grand-livre mensuel de valorisation financière | `id_journal` (PK), UQ(employe, annee, mois) | État mensuel écrasable (`ON CONFLICT DO UPDATE`) | Situé dans `06_schema_paie.sql` au lieu du domaine Congés |

### 5.2. Distinction des Concepts du Domaine Congés

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│                       DISTINCTION CONCEPTUELLE STRICTE                       │
├──────────────────────────┬──────────────────────────────────────────────────┤
│ 1. DROIT ACQUIS          │ Jours crédités au titre du travail effectif      │
│                          │ (2.5j / mois proratisé selon absences).          │
├──────────────────────────┼──────────────────────────────────────────────────┤
│ 2. DROIT CONSOMMÉ        │ Jours de congé effectivement pris sur une        │
│                          │ période de travail constatée par le Pointage.    │
├──────────────────────────┼──────────────────────────────────────────────────┤
│ 3. SOLDE DISPONIBLE      │ Cumul droits acquis - Cumul droits consommés     │
│                          │ + Bonifications - Expirations.                   │
├──────────────────────────┼──────────────────────────────────────────────────┤
│ 4. DEMANDE DE CONGÉ      │ Intention de congé posée pour le futur.          │
├──────────────────────────┼──────────────────────────────────────────────────┤
│ 5. CONGÉ APPROUVÉ        │ Autorisation RH formelle (réserve le planning).  │
├──────────────────────────┼──────────────────────────────────────────────────┤
│ 6. CONGÉ EFFECTIF        │ Journée d'absence autorisée constatée dans le    │
│                          │ Pointage gelé.                                   │
├──────────────────────────┼──────────────────────────────────────────────────┤
│ 7. VALORISATION MONÉTAIRE│ Valeur financière du jour de congé (TCONGE) et   │
│                          │ montant total de l'indemnité compensatrice.      │
└──────────────────────────┴──────────────────────────────────────────────────┘
```

---

## 6. AUDIT APPROFONDI DE TCONGE

### 6.1. Formule de Calcul Actuelle

Dans `PayrollService.mettreAJourJournalConge` :
$$\text{Formule Absences} = \max(0, 30.0 - \text{Absences Injustifiées})$$
$$\text{Droit Mois} = \frac{2.5 \times \text{Formule Absences}}{30.0}$$
$$\text{Base Valorisation} = (\text{Salaire Base} - \text{Retenues} + \text{Indemnités}) + \text{Heures Supplémentaires}$$
$$\text{Valorisation Mensuelle} = \frac{\text{Base Valorisation} \times \text{Droit Mois}}{30.0}$$
$$\text{Cumul Droits}_N = \text{Cumul Droits}_{N-1} + \text{Droit Mois}$$
$$\text{Cumul Valo}_N = \text{Cumul Valo}_{N-1} + \text{Valorisation Mensuelle}$$
$$\text{TCONGE}_N = \frac{\text{Cumul Valo}_N}{\text{Cumul Droits}_N}$$

### 6.2. Incohérences et Lacunes Identifiées :

1. **Fenêtre Glissante Inexistante (Cumul à Vie) :**
   - Le calcul additionne indéfiniment `cumul_valorisation` et `cumul_jours_droit` depuis la date d'embauche de l'employé !
   - Pour un salarié ayant 8 ans d'ancienneté, le TCONGE est dilué par les salaires perçus 8 ans auparavant, en contradiction avec la pratique du Code du Travail qui impose la période de référence des 12 derniers mois précédant le congé.
2. **Exclusion Injustifiée des Majorations :**
   - Seules les rubriques de catégorie `HEURES_SUP` sont incluses dans la base de valorisation.
   - Les majorations de nuit (`MAJORATION_NUIT`), de dimanche et de jours fériés ne sont pas agrégées dans `baseTotale`, minorant indûment le TCONGE.
3. **Moment Inapproprié de Calcul :**
   - TCONGE est calculé lors de la clôture de la paie du mois $M$.
   - Le bulletin du mois $M$ doit donc consommer le TCONGE du mois $M-1$ (`WHERE annee < ? OR (annee = ? AND mois < ?)`).
   - En l'absence d'historique antérieur (ex: premier mois), le système retombe sur le fallback `salaireBase / 30.0`.

---

## 7. AUDIT DU STC (SOLDE DE TOUT COMPTE)

### 7.1. Fonctionnement Actuel
- `StcService.preparerStc` résout le solde de congés restants via `VSoldeCongeEmploye.rechercherParEmploye(idEmploye, c)`.
- Il résout le TCONGE applicable via `getTauxJournalierApplicable(idEmploye, dateDepart, salaireBase, c)`.
- Il calcule l'Indemnité Compensatrice de Congés Payés :
  $$\text{ICCP} = \text{Solde Jours Restants} \times \text{TCONGE}$$

### 7.2. Points de Vigilance pour le STC :
- Le STC valorise un **stock de droits restants non pris**, et non une consommation passée.
- Si le salarié a un solde négatif (prise par anticipation autorisée), l'ICCP devient négative et se transforme en retenue sur salaire dans le reçu pour solde de tout compte.
- Le moteur de valorisation TCONGE doit impérativement être partagé entre le Congé Mensuel et le STC, sans imposer au STC de consommer un snapshot de congé pris.

---

## 8. CONCEPTION CIBLE RECOMMANDÉE

```text
               ARCHITECTURE CIBLE SÉPARÉE ET SÉCURISÉE
               
   DOMAINE CONGÉS (Owner Métier Unique)
   ├── CongeAccrualService (Acquisition mensuelle unifiée avec prorata absences)
   ├── CongeWorkflowService (Demandes, validations, annulations sécurisées)
   ├── CongeValuationEngine (Moteur de calcul TCONGE sur 12 mois glissants)
   └── Table : conge_valuation_snapshot (Créé au gel du pointage)
            │
            ├── Jours réellement pris constatés
            ├── TCONGE applicable scellé
            ├── Montant valorisé scellé
            └── Provenance explicable (détail des 12 mois)
            │
            ▼
   DOMAINE POINTAGE (Temps & Présence)
   └── Détecte les congés approuvés, constate les absences/présences réelles,
       génère le pointage_snapshot_mensuel.
            │
            ▼
   DOMAINE PAIE (Moteur d'Exécution)
   └── NE CALCULE PAS TCONGE !
   └── Consomme passivement le conge_valuation_snapshot (comme le pointage snapshot).
   └── Inscrit INDEMNITE_CONGE et RETENUE_CONGE sur le bulletin.
            │
            ▼
   DOMAINE STC (Rupture de Contrat)
   └── Consomme le solde restant issu du Grand-Livre Congés.
   └── Appelle CongeValuationEngine pour valoriser les droits acquis non pris.
```

---

## 9. VERDICT GLOBAL

```text
===============================================================================
VERDICT OFFICIEL : PHASE_2A_2_AUDIT_COMPLETE
===============================================================================
L'audit contradictoire est achevé. Tous les flux, tables, contrôleurs, 
écrans UI, formules mathématiques et divergences de sources de vérité 
sont cartographiés avec précision et appuyés par des preuves de code réelles.
Aucune modification de code de production n'a été effectuée pendant cet audit.
===============================================================================
```
