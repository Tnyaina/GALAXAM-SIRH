# MATRICE D'OWNERSHIP DES RÈGLES MÉTIER DU DOMAINE CONGÉS & TCONGE
**Date :** 17 Septembre 2026  
**Référence :** Phase 2A-2 Audit Contradictoire Complet  
**Système :** GALAXAM SIRH  

---

## 1. PRINCIPE DIRECTEUR D'OWNERSHIP CIBLE

Pour éliminer les collisions de sources de vérité et les responsabilités croisées incohérentes, l'architecture cible applique les principes suivants :

1. **Le Domaine Congés possède l'intégralité du cycle de vie des droits et de leur valeur :**
   - Acquisition en jours et en valeur monétaire.
   - Solde disponible et réservations.
   - Demandes, validations et annulations.
   - Calcul de TCONGE et valorisation scellée.
2. **Le Domaine Pointage constate la réalité temporelle :**
   - Vérifie la présence/absence effective.
   - Constaté sur les jours ouvrés non chômés.
3. **Le Domaine Paie est un pur consommateur :**
   - Il ne calcule **jamais** de droits de congé.
   - Il ne calcule **jamais** de TCONGE.
   - Il consomme des montants et des jours scellés issus d'un `LeaveValuationSnapshot`.
4. **Le Domaine STC réutilise le moteur de valorisation des congés :**
   - Il valorise le stock de droits restants non pris via le moteur canonique du Domaine Congés.

---

## 2. MATRICE D'OWNERSHIP COMPARATIVE

| Règle Métier | Owner Actuel | Caller Production | Table DB | Owner Cible Recommandé | Statut Audit & Diagnostic |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Acquisition mensuelle en jours (2.5j)** | `AcquisitionCongeScheduler` | Scheduler Spring (01:00) | `mouvement_solde_conge` | `CongeAccrualService` | `DUPLICATE_SOURCE_OF_TRUTH` (Ne proratise pas selon les absences injustifiées) |
| **Proratisation acquisition selon absences** | `PayrollService.mettreAJourJournalConge` | `PayrollService.cloturerDonneesPaie` | `conge_journal_droit` | `CongeAccrualService` | `WRONG_DOMAIN_OWNER` (La Paie ne doit pas posséder l'acquisition des droits à congé) |
| **Bonification d'ancienneté (+1j / 5 ans)** | `AcquisitionCongeScheduler` + `CongeCalculateurEngine` | Scheduler Spring (01:00) | `mouvement_solde_conge` | `CongeAccrualService` | `ACTIVE_CANONICAL` |
| **Plafonnement cumul solde (90j max)** | `AcquisitionCongeScheduler` | Scheduler Spring (01:00) | `mouvement_solde_conge` | `CongeAccrualService` | `ACTIVE_CANONICAL` |
| **Purge triennale FIFO (36 mois)** | `PurgeCongeScheduler` + `PurgeFifoEngine` | Scheduler Spring (02:00) + `CongeSoldeController` | `mouvement_solde_conge` | `CongePurgeService` | `CLEAN` |
| **Décompte jours de congé demandés** | `CongeCalculateurEngine.calculerNombreJours` | `DemandeCongeController.simulerDemande` | `demande_conge` | `CongeDurationEngine` | `DATA_MODEL_REDESIGN_RECOMMENDED` (Calendaires bruts en conflit avec le Pointage ouvré) |
| **Contrôle éligibilité 12 mois (Art. 83)** | React `SoldeCongeWidget.jsx` (lignes 20-80) | Composant React UI | Aucune (calcul front) | `CongeEligibilityEngine` | `BUSINESS_CALCULATION_DRIFT` (Doit être certifié par le backend) |
| **Contrôle dépassement solde (Anticipation)** | `DemandeConge.valider` | `CongeService.validerDemande` | `parametre_global` | `CongeWorkflowService` | `CLEAN` |
| **Contrôle continuité de service (Poste)** | `CongeService.verifierConflitsDemande` | `DemandeCongeController.verifierConflits` | `employe_affectation_carriere` | `CongeWorkflowService` | `CLEAN` |
| **Débit du solde (Consommation)** | `DemandeConge.valider` (Immédiat à validation) | `CongeService.validerDemande` | `mouvement_solde_conge` | `CongeWorkflowService` (Réservation) + `CongeSettlementService` (Consommation définitive) | `TEMPORAL_INTEGRITY_RISK` (Débit prématuré avant la prise effective du congé) |
| **Restitution sur annulation** | `DemandeConge.annuler` | `DemandeCongeController.annulerDemande` | `mouvement_solde_conge` | `CongeWorkflowService` | `TEMPORAL_INTEGRITY_RISK` (Permet d'annuler un congé sur une période déjà payée/scellée) |
| **Détection des congés dans le Pointage** | `PointageSaisieService.getSaisieJournaliere` | Pointage Saisie UI | `demande_conge` $\to$ `pointage_jour` | `PointageAttendanceBridge` | `ACTIVE_CANONICAL` |
| **Agrégation des congés payables en paie** | `PointageSyntheseService.calculerSyntheseMensuelle` | Validation période pointage | `pointage_synthese_employe` | `PointageSyntheseService` | `CLEAN` |
| **Calcul du TCONGE historique** | `PayrollService.mettreAJourJournalConge` | Clôture de Paie | `conge_journal_droit` | `CongeValuationEngine` | `WRONG_DOMAIN_OWNER` + `LEAVE_VALUATION_PROVENANCE_GAP` |
| **Résolution temporelle du TCONGE applicable** | `StcService.getTauxJournalierApplicable` | `PayrollService.calculerEmploye` + `StcService.preparerStc` | `conge_journal_droit` | `CongeValuationEngine` | `TEMPORAL_INTEGRITY_RISK` (Calcul dynamique au lieu d'une valeur figée) |
| **Amorçage TCONGE sans historique (BASE/30)** | `StcService.getTauxJournalierApplicable` (lignes 962-965) | Fallback dynamique | Code Java | `CongeValuationEngine` | `CURRENT_INITIALIZATION_RULE` (Validée comme règle transitoire explicite) |
| **Figeage de la valorisation de congé** | Aucun (Inexistant) | Aucun | Aucune table dédiée | `CongeValuationSnapshotService` | `BUSINESS_RULE_REQUIRED` (Un snapshot doit être créé au gel du pointage) |
| **Retenue sur salaire congé (1/30ème)** | `PayrollExecutionEngine.calculerConges` | Moteur de Paie | `bulletin_paie_ligne` (`RETENUE_CONGE`) | `PayrollExecutionEngine` | `CLEAN` |
| **Indemnisation congé (TCONGE)** | `PayrollExecutionEngine.calculerConges` | Moteur de Paie | `bulletin_paie_ligne` (`INDEMNITE_CONGE`) | `PayrollExecutionEngine` | `ACTIVE_CANONICAL` (Mais doit consommer le snapshot valorisé) |
| **Liquidation ICCP au STC** | `StcService.preparerStc` | STC Wizard | `paie_stc` | `StcService` via `CongeValuationEngine` | `ACTIVE_CANONICAL` |

---

## 3. IDENTIFICATION DES COMPOSANTS CIBLES

Pour respecter la règle d'or d'architecture : **"Pas une classe par méthode, un minimum de composants cohérents"**, le domaine cible est structuré autour de 4 composants majeurs :

```text
mg.galaxam.sirh.model.conge
│
├── service
│   ├── CongeService (Façade REST, coordination workflow demandes/validations/annulations)
│   ├── CongeAccrualService (Acquisition unifiée jours + droits financiers au rythme mensuel)
│   └── CongeValuationService (Moteur de calcul TCONGE 12 mois et production des snapshots scellés)
│
└── engine
    ├── CongeCalculateurEngine (Calculs mathématiques purs de décompte et bonification)
    └── PurgeFifoEngine (Algorithme FIFO de prescription triennale Art. 85)
```
