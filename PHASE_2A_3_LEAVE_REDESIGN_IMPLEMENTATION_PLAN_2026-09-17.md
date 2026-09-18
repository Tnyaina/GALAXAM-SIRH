# PLAN D'IMPLÉMENTATION DU REDESIGN DU MODULE CONGÉS & TCONGE (PHASE 2A-3)
**Date :** 17 Septembre 2026  
**Statut :** Plan d'action suite aux conclusions de l'Audit Phase 2A-2  
**Système :** GALAXAM SIRH  

---

## 1. OBJECTIFS ET PRINCIPES DE TRANSITION

La Phase 2A-3 concrétisera la séparation claire et robuste entre :
```text
ACQUISITION UNIFIÉE -> GRAND-LIVRE AUDITABLE -> VALIDATION SÉCURISÉE 
  -> POINTAGE CONSTATÉ -> VALORISATION SCELLÉE -> PAIE CONSOMMATRICE -> LIQUIDATION STC
```

### Règles de Transition :
1. **Aucun bris de régression :** La baseline de 429 tests backend et 16 tests frontend doit rester à 100% passante à chaque sprint.
2. **Migration incrémentale par petits sprints ciblés (2A-3A à 2A-3F).**
3. **Immutabilité de l'existant déjà clôturé :** Ne pas écraser ni déstabiliser les anciens bulletins ou les anciens reçus STC.

---

## 2. DÉCOUPAGE EN SPRINTS OPÉRATIONNELS

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│                       PLANNING DU SPRINT PHASE 2A-3                         │
├─────────┬───────────────────────────────────────────────────────────────────┤
│ 2A-3A   │ UNIFICATION DU MODÈLE MÉTIER & GRAND-LIVRE CONGÉS                 │
│         │ - Réconciliation mouvement_solde_conge et acquisition mensuelle   │
│         │ - Prorata d'acquisition automatique tenant compte des absences   │
│         │ - Élimination de la double source de vérité d'acquisition        │
├─────────┼───────────────────────────────────────────────────────────────────┤
│ 2A-3B   │ MOTEUR DE VALORISATION TCONGE (12 MOIS GLISSANTS)                 │
│         │ - CongeValuationService & algorithme de moyenne légale Art. 89    │
│         │ - Agrégation complète : Base + Heures Sup + Nuit + Dimanche/Férié │
│         │ - Règle du trentième de maintien le plus favorable                │
│         │ - Table conge_valuation_snapshot                                 │
├─────────┼───────────────────────────────────────────────────────────────────┤
│ 2A-3C   │ SÉCURISATION DU WORKFLOW DE DEMANDE & TEMPORALITÉ                 │
│         │ - Harmonisation décompte calendaire vs jours ouvrés pointage      │
│         │ - Distinction stricte : Réservation (Validation) vs Consommation │
│         │ - Verrouillage anti-annulation sur périodes de paie clôturées     │
│         │ - Correction du bug d'unicité sur modification d'un congé validé  │
├─────────┼───────────────────────────────────────────────────────────────────┤
│ 2A-3D   │ REDESIGN UI / UX FRONTEND                                         │
│         │ - Hub /conges à 3 onglets (Planning, Demandes, Soldes & TCONGE)   │
│         │ - Tableau consolidé de tous les soldes salariés + valeur en Ar    │
│         │ - Tiroir d'explicabilité de valorisation TCONGE                   │
│         │ - Déport du calcul d'éligibilité légale depuis React vers backend │
├─────────┼───────────────────────────────────────────────────────────────────┤
│ 2A-3E   │ PASSERELLES POINTAGE, PAIE ET STC                                 │
│         │ - Création automatique du conge_valuation_snapshot au gel pointage│
│         │ - Déplacement de l'appel TCONGE : Payroll ne calcule plus rien    │
│         │ - Adaptation de StcService pour consommer CongeValuationService   │
├─────────┼───────────────────────────────────────────────────────────────────┤
│ 2A-3F   │ GATE D'INTÉGRATION RUNTIME & TESTS ADVERSARIAUX                   │
│         │ - Tests PostgreSQL réels multi-scénarios                          │
│         │ - Tests E2E Cypress / Playwright ou MockMvc de bout en bout       │
│         │ - Validation de non-régression sur paie et STC                   │
└─────────┴───────────────────────────────────────────────────────────────────┘
```

---

## 3. DÉTAIL DES SPRINTS

### SPRINT 2A-3A : Unification du Modèle Métier & Grand-Livre
- **Livrables techniques :**
  - Création du service `CongeAccrualService` dans le package `mg.galaxam.sirh.model.conge.service`.
  - Fusion de la logique d'acquisition : l'acquisition mensuelle de 2,5j devient proratisée selon les absences constatées à la clôture de la période, éliminant la divergence entre `mouvement_solde_conge` et `conge_journal_droit`.
  - Migration de la responsabilité hors de `PayrollService.mettreAJourJournalConge` vers un listener d'événement ou un appel explicite orchestré par `CongeAccrualService`.

---

### SPRINT 2A-3B : Moteur de Valorisation TCONGE
- **Livrables techniques :**
  - Création de `CongeValuationService` avec méthode canonique :
    `calculerValorisationPeriode(int idEmploye, LocalDate dateFinPeriode, Connection c)`
  - Algorithme de calcul de la moyenne des 12 derniers mois glissants :
    $$\text{Taux Moyen} = \frac{\sum_{i=1}^{12} \text{Rémunérations Soumises}_i}{\sum_{i=1}^{12} \text{Droits Acquis}_i}$$
  - Règle de sauvegarde du maintien : $\max(\text{Taux Moyen}, \text{Salaire Actuel} / 30.0)$.
  - Création de la table `conge_valuation_snapshot` avec détail JSON des 12 mois pour traçabilité et explicabilité complète.

---

### SPRINT 2A-3C : Workflow de Demande et Temporalité
- **Livrables techniques :**
  - Modification de `DemandeConge.annuler` : ajout d'un contrôle d'intégrité temporelle interdisant l'annulation si un snapshot de paie existe sur la période sans autorisation DAF explicite.
  - Correction de `DemandeCongeController.updateDemande` : gestion propre du remplacement du mouvement `PRISE` sans violation de contrainte unique.
  - Alignement du décompte : le formulaire calcule et affiche explicitement :
    - Les jours calendaires bruts (pour le calendrier d'absence).
    - Les jours ouvrables décomptés du solde.
    - Les jours fériés chômés exclus de l'imputation.

---

### SPRINT 2A-3D : Redesign UI / UX Frontend
- **Livrables techniques :**
  - Refonte de `GestionCongesPage.jsx` pour intégrer les 3 onglets :
    - `Planning & Absences` (`SoldesGantt`)
    - `Demandes & Approbations` (`DemandesValidationTab`)
    - `Soldes & Grand-Livre TCONGE` (`SoldesTable` enrichie avec soldes individuels, TCONGE et valeur monétaire).
  - Création du composant `TcongeExplicabiliteDrawer.jsx` (Détail mois par mois des 12 mois de référence).
  - Nettoyage de `SoldeCongeWidget.jsx` : consommation des attributs d'éligibilité fournis par le backend.

---

### SPRINT 2A-3E : Passerelles Pointage, Paie et STC
- **Livrables techniques :**
  - Déclenchement de la création du `conge_valuation_snapshot` lors de la validation/gel du pointage mensuel dans `PointageSyntheseService.figerPointage`.
  - Refactorisation de `PayrollService.preparerInput` :
    - Au lieu d'appeler `StcService.getTauxJournalierApplicable`, lecture directe du snapshot scellé `conge_valuation_snapshot`.
  - Adaptation de `StcService` : délégation de la valorisation des congés non pris à `CongeValuationService.calculerTauxDate(...)`.

---

### SPRINT 2A-3F : Runtime Gate & Validation d'Intégrité
- **Scénarios de test indispensables :**
  1. Congé traversant deux périodes de paie avec changement de salaire.
  2. Congé posé sur un jour férié chômé (non imputé au solde et non payé en indemnité de congé).
  3. Annulation d'une demande passée déjà clôturée (rejet strict).
  4. Salarié sans aucun historique antérieur (application propre de $Base / 30$).
  5. Salarié avec 12 mois complets d'historique et heures sup (TCONGE moyen supérieur au salaire de base).
  6. STC avec solde positif $\to$ versement ICCP valorisé aux 12 derniers mois.
  7. STC avec solde négatif (anticipation) $\to$ déduction sur le solde final.
