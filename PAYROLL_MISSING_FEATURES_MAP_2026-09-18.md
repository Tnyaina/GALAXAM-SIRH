# CARTOGRAPHIE DES FONCTIONNALITÉS PAIE — ÉTAT & GAPS (MISSING FEATURES MAP)

**Date** : 2026-09-18  
**Référence** : Mission Phase Paie — Axe D (Matrice des Fonctionnalités Manquantes)  
**Critères de Priorisation** :
- **P0** : Empêche l'exécution ou la complétude du workflow réel de paie/RH.
- **P1** : Fonctionnalité métier nécessaire pour la gestion opérationnelle quotidienne.
- **P2** : Amélioration ergonomique, consolidation ou fluidification de navigation.
- **P3** : Confort visuel ou automatisation secondaire.

---

## 1. Matrice Globale de Couverture et d'Analyse des Écarts

| Fonction Métier | Backend (Moteur/DB) | API REST (Controller) | UI Frontend (Écran/Modal) | Workflow Utilisateur | Priorité | Action Recommandée |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| **STC depuis Rupture Contrat** | ✅ `StcService` | ✅ `StcController` | ❌ **ABSENT** | Incomplet | **P0** | **FIX_NOW** : Ajouter bouton "Préparer STC" dans `RuptureContratModal.jsx` et `ContratTravailModal.jsx`. |
| **Interface Simulation & Émission STC** | ✅ `StcService` | ✅ `StcController` | ❌ **ABSENT** | Inexistant | **P0** | **IMPLEMENT_NOW** : Créer `stcService.js` et `StcPreparationModal.jsx` (calcul, reçu officiel, apurement prêts). |
| **Onglet Paie dans Fiche Employé** | ✅ `PayrollService` | ✅ `/paie/bulletin/*` | ❌ **ABSENT** | Absent | **P1** | **IMPLEMENT_NOW** : Créer `EmployePaieTab.jsx` avec salaire contractuel, dernier net, historique bulletins, TCONGE, prêts. |
| **Historique Bulletins par Collaborateur** | ✅ `BulletinPaie` | ✅ `GET /paie/bulletin/{id}` | ⚠️ Partiel (Période uniquement) | Fragmenté | **P1** | **IMPLEMENT_NOW** : Intégrer la liste des bulletins du salarié dans `EmployePaieTab` avec ouverture de `BulletinPaieDrawer`. |
| **Acomptes de Quinzaine (ACOMPTE_15)** | ✅ `PaieAvanceAcompte` | ✅ `PaieAjustementController` | ⚠️ Dans Variables période | Collectif | **P1** | **IMPLEMENT_NOW** : Permettre la saisie unitaire d'acompte depuis la Fiche Employé et la table des variables. |
| **Avances Spéciales (Plafond 1/10 Art. 68)** | ✅ Plafonnement exact | ✅ `PaieAjustementController` | ⚠️ Dans Variables période | Collectif | **P1** | **IMPLEMENT_NOW** : Afficher la capacité résiduelle d'avance légale (1/10ème du net). |
| **Prêts d'Entreprise & Quotité 1/3 (Art. 72)** | ✅ `PaiePret` | ✅ `PaieAjustementController` | ⚠️ Prêts gérés en admin | Partagé | **P1** | **NEXT_ITERATION** : Afficher l'échéancier des prêts dans l'onglet Paie Employé. |
| **Retenues Téléphoniques (CALL PERSO)** | ✅ Supporté en retenue | ✅ `PaieAjustementController` | ✅ Dans `VariablesPaieTab` | Fonctionnel | **P2** | **KEEP** : Type de mouvement déjà disponible et réconcilié. |
| **Remboursements Notes de Frais (Non imposables)** | ✅ `sens='GAIN'`, non imposable | ✅ `PaieAjustementController` | ✅ Dans `VariablesPaieTab` | Fonctionnel | **P2** | **KEEP** : Supporté comme gain non soumis à cotisations ni IRSA. |
| **Déplacement TNR / TMM avec Déduction Repas** | ✅ `PointageSyntheseService` | ✅ Automatique via Pointage | ✅ Visualisé dans bulletin | Automatique | **P1** | **KEEP** : Calculé au prorata des jours de présence avec gestion cantine TMM. |
| **Indemnités Repas Site (TNR 2 000 Ar/j)** | ✅ `DailyAttendanceCalculator` | ✅ Automatique via Pointage | ✅ Visualisé dans bulletin | Automatique | **P1** | **KEEP** : Calculé automatiquement selon présences matin et après-midi. |
| **Prime de Performance (Prorata & Pénalités)** | ✅ Règles seuils 70m / 1j | ✅ `PayrollExecutionEngine` | ⚠️ Saisie manuelle DAF | Semi-auto | **P2** | **NEXT_ITERATION** : Pré-remplir la proposition de malus de 25% à partir des données de retard. |
| **Prime d'Assiduité (Seuil 45 min)** | ✅ `seuilRetardAssiduiteMinutes` | ✅ `PayrollExecutionEngine` | ✅ Automatique dans bulletin | Automatique | **P1** | **KEEP** : Attribuée si retards $< 45\text{ min}$. |
| **Autres Primes Imposables (AUTIMP)** | ✅ Imposable brut standard | ✅ `PaieAjustementController` | ✅ Dans `VariablesPaieTab` | Fonctionnel | **P1** | **KEEP** : Saisie libre par période. |
| **Vue Transverse STC dans Module Paie** | ✅ `stcService.listerStc()` | ✅ `GET /paie/stc/liste` | ❌ **ABSENT** | Absent | **P2** | **NEXT_ITERATION** : Ajouter sous-onglet "Soldes Tout Compte" dans `GestionPaiePage`. |
| **Nettoyage Slate & Sauge dans Bulletin Drawer** | N/A | N/A | ⚠️ Présence de classes slate-* | Visuel | **P1** | **FIX_NOW** : Remplacer par variables CSS canoniques pour compatibilité dark mode. |
| **Harmonisation Cockpit Paie vs ControleStepPage** | N/A | N/A | ⚠️ Doublon d'écrans | Ergonomie | **P2** | **NEXT_ITERATION** : Factoriser le tableau des bulletins partagé. |

---

## 2. Synthèse des Gaps Critiques (P0 & P1)

### Gap Critique 1 : La chaîne de fin de contrat ne déclenche pas le STC (P0)
- **Problème** : Lorsqu'un contrat est terminé ou rompu dans `CarrieresSupervisionPage` ou `RuptureContratModal`, le statut passe à `CLOTURE` ou `RESILIE`, mais aucun lien n'existe pour initialiser le Solde de Tout Compte.
- **Solution** : Ajouter à la clôture du contrat l'action contextuelle immédiate : `"Préparer le Solde de Tout Compte"`, ouvrant `StcPreparationModal`.

### Gap Critique 2 : Absence de l'onglet Paie dans le Dossier Collaborateur (P1)
- **Problème** : La fiche employé est aveugle sur le plan salarial et financier.
- **Solution** : Ajouter l'onglet `PAIE` dans `EmployeFicheDetailPage`, affichant l'historique des bulletins émis, le taux horaire, le taux $TCONGE$ gelé applicable, et les déductions en cours.

### Gap Critique 3 : Styles résiduels non conformes au Design System Sauge (P1)
- **Problème** : `BulletinPaieDrawer.jsx` contient des classes Tailwind rigides (`text-slate-800`, `bg-slate-900`) altérant le dark mode.
- **Solution** : Remplacer par `text-[var(--color-fg)]`, `bg-[var(--color-surface)]`, `border-[var(--color-border)]`.
