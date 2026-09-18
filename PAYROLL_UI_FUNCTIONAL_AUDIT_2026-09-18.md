# AUDIT FONCTIONNEL ET ERGONOMIQUE DU MODULE PAIE (UI / UX)

**Date** : 2026-09-18  
**Référence** : Mission Phase Paie — Axe C (Audit Fonctionnel Réel) & Axe D (Navigation, Cohérence et Complétude)  
**Périmètre** : Routes React, Pages, Composants, Endpoints, Workflows DAF, Fiche Employé, Contrats et STC  

---

## 1. Sitemap et Cartographie des Routes Paie Actuelles

```text
/paie (GestionPaiePage)
  ├── ?tab=campagne           → Vue active & Stepper horizontal Sprint 2B
  ├── ?tab=variables          → VariablesPaieTab (Ajustements, Primes, Acomptes)
  ├── ?tab=periodes           → PeriodePaiePage (CRUD Périodes, États BROUILLON/VALIDE/CLOTURE)
  ├── ?tab=cockpit            → CockpitPaieTab (Vue DAF dense, table complète, actions globales)
  └── ?tab=exports            → ExportsPaieTab (Exports bancaires, SAFI 301, DNTS CNaPS/FMFP)

/paie/periodes/:periodId/... (Pages d'étapes de campagne - Sprint 2B)
  ├── /preparation            → PreparationStepPage (Vérification prérequis, clôture pointages)
  ├── /population             → PopulationStepPage (Salariés éligibles, entrées/sorties)
  ├── /variables              → VariablesStepPage (Wrapper autour de VariablesPaieTab)
  ├── /calcul                 → CalculStepPage (Lancement calcul global, KPIs masse salariale)
  ├── /controle               → ControleStepPage (Revue des bulletins, filtres, drawer bulletin)
  ├── /validation             → ValidationStepPage (Verrouillage DAF, signature, clôture)
  └── /sorties                → SortiesStepPage (Téléchargement des états légaux et virements)
```

---

## 2. Inventaire Détaillé : Routes, Pages, Endpoints et Statut

| Route React | Composant Page | Fonction Métier | Endpoints Backend Consommés | Statut / Utilisation | Diagnostic UX |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `/paie` | `GestionPaiePage` | Hub central Paie DAF | `/periodes/context`, `/paie/periode/{id}/cockpit` | **ACTIF** | Bon point d'entrée, mais redondant avec les sous-onglets |
| `/paie?tab=campagne` | `GestionPaiePage` | Tableau de bord campagne | `/periodes/context` | **ACTIF** | Stepper fonctionnel vers étapes Sprint 2B |
| `/paie?tab=variables` | `VariablesPaieTab` | Saisie ajustements | `/paie/ajustements`, `/paie/ajustements/types-mouvements` | **ACTIF** | Table des variables opérationnelle |
| `/paie?tab=periodes` | `PeriodePaiePage` | Gestion des périodes | `/periodes`, `/periodes/{id}/actions/*` | **ACTIF** | Permet création et réouverture |
| `/paie?tab=cockpit` | `CockpitPaieTab` | Revue globale DAF | `/paie/periode/{id}/cockpit`, `/paie/bulletin/{id}` | **REDONDANT** | **Doublon fonctionnel direct** avec `ControleStepPage` |
| `/paie?tab=exports` | `ExportsPaieTab` | Déclarations & Virement | `/paie/periode/{id}/export/*` | **ACTIF** | Doublon avec `SortiesStepPage` |
| `/paie/periodes/:id/preparation` | `PreparationStepPage` | Étape 1 : Prérequis | `/periodes/context`, `/pointages/statut-periode` | **ACTIF** | Clair, bloque si pointages non validés |
| `/paie/periodes/:id/population` | `PopulationStepPage` | Étape 2 : Salariés | `/paie/periode/{id}/population` | **ACTIF** | Affiche effectif, nouveaux entrants, sortants |
| `/paie/periodes/:id/variables` | `VariablesStepPage` | Étape 3 : Variables | Consomme `VariablesPaieTab` | **ACTIF** | Cohérent avec stepper |
| `/paie/periodes/:id/calcul` | `CalculStepPage` | Étape 4 : Calcul | `/paie/calculer-periode` | **ACTIF** | Lance simulation ou calcul officiel |
| `/paie/periodes/:id/controle` | `ControleStepPage` | Étape 5 : Contrôle | `/paie/periode/{id}/cockpit`, `/paie/bulletin/{id}` | **ACTIF** | Revue unitaire via `BulletinPaieDrawer` |
| `/paie/periodes/:id/validation` | `ValidationStepPage` | Étape 6 : Validation | `/paie/valider-periode`, `/periodes/{id}/actions/CLOSE` | **ACTIF** | Clôture irréversible |
| `/paie/periodes/:id/sorties` | `SortiesStepPage` | Étape 7 : Sorties | `/paie/periode/{id}/export/*` | **ACTIF** | Fichiers officiels DGI / CNaPS / Banques |

---

## 3. Détection des Redondances Majeures (Section 11)

L'audit révèle une **double architecture parallèle** :
1. **L'ancienne vue monolithique DAF** :
   - `CockpitPaieTab.jsx` (898 lignes) regroupe dans une seule page : sélection de période, statistiques globales, tableau des bulletins, calcul, simulation, validation, clôture, saisie de prime modale, et drawer bulletin.
2. **Le workflow séquentiel Sprint 2B** :
   - Découpé en 7 étapes d'URL distinctes (`PreparationStepPage`, `PopulationStepPage`, `VariablesStepPage`, `CalculStepPage`, `ControleStepPage`, `ValidationStepPage`, `SortiesStepPage`).

### Décision et Recommandation :
- **`ControleStepPage` vs `CockpitPaieTab`** : Les deux écrans appellent exactement le même endpoint `/paie/periode/{id}/cockpit` et affichent le même tableau de contrôle des bulletins avec le même `BulletinPaieDrawer`.
- **Verdict** :
  - **MERGE / HARMONIZE** : `CockpitPaieTab` doit être conservé comme vue de synthèse globale transverse rapide (pour consultation directe hors campagne pas-à-pas), mais doit réutiliser les composants d'étapes sans duplication de logique.
  - La navigation DAF par défaut doit privilégier le **Stepper Sprint 2B**, garant d'un audit de conformité ordonné.

---

## 4. Audit des `undefined`, `null`, `NaN` et Risques de Plantage (Section 10)

1. **Hardcoding de classes Slate dans `BulletinPaieDrawer.jsx`** :
   - Lignes 511-512, 526-527, 543, 583-585 : présence de `text-slate-800`, `text-slate-900`, `bg-slate-900`, `border-slate-800`.
   - **Impact** : En mode sombre (dark mode), ces styles créent des contrastes illisibles ou des aplats noirs cassant le thème Sauge.
   - **Correction** : Remplacer systématiquement par les variables CSS canoniques `var(--color-fg)`, `var(--color-surface)`, `var(--color-brand-*)`.
2. **Robustesse de `formatAr` face aux valeurs nulles** :
   - `formatAr` dans `ControleStepPage` et `GestionPaiePage` gère `v != null ? Number(v).toLocaleString(...) + ' Ar' : '–'`.
   - En revanche, certains sous-totaux dans les fiches intermédiaires utilisent `Number(x).toLocaleString(...)` direct sans vérification de nullité (`l.baseCalcul ? ... : '-'`).
   - Risque maîtrisé mais à uniformiser avec un helper partagé `formatMoneyAr`.

---

## 5. Audit de la Fiche Employé : Absence Majeure de l'Onglet Paie (Sections 13 et 14)

### Constat Critique :
- `EmployeFicheDetailPage.jsx` dispose de 6 onglets :
  - `personnel` (Identité, CIN, Banque, CNaPS)
  - `famille` (Enfants à charge éligibles)
  - `documents` (Contrats, pièces justificatives)
  - `carriere` (Affectations, postes, salaire actuel contractuel)
  - `conges` (Soldes, mouvements, demandes)
  - `pointage` (Heures travaillées, anomalies)
- **IL N'EXISTE AUCUN ONGLET PAIE DANS LA FICHE EMPLOYÉ.**

### Conséquences Opérationnelles :
- Un gestionnaire RH ou DAF voulant vérifier si un salarié a perçu son salaire, consulter son bulletin du mois dernier, vérifier ses déductions de prêt ou son taux $TCONGE$ en vigueur doit :
  1. Quitter le dossier employé.
  2. Aller dans le module Paie.
  3. Sélectionner la période concernée.
  4. Chercher le salarié dans la liste de 50 à 100 salariés.
  5. Ouvrir le drawer.
  6. Recommencer pour le mois précédent !

### Recommandation P1 Immédiate :
- **Ajout de l'onglet `PAIE` dans la Fiche Employé** (`EmployePaieTab.jsx`) affichant :
  - Synthèse Rémunération : Salaire contractuel actuel, Taux horaire, Taux $TCONGE$ gelé applicable.
  - Dernier bulletin validé : Brut, Cotisations, IRSA, Net à payer.
  - Historique des bulletins émis pour l'employé avec ouverture directe du `BulletinPaieDrawer`.
  - Mouvements de paie récents : Acomptes perçus, Prêts en cours d'apurement, Retenues téléphoniques (`CALL PERSO`).
  - Statut STC si contrat rompu/clôturé.

---

## 6. Audit Contrat ↔ STC : Absence d'Interface STC (Sections 15, 16, 17, 18)

### Constat Paradoxal :
- Le backend dispose d'un **moteur STC complet, robuste et testé** (`StcController`, `StcService`, 1005 lignes) supportant :
  - Décompte complet de l'ancienneté multi-contrats (`AncienneteEngine`).
  - Prorata temporis du dernier mois ($Base / 30$).
  - Indemnité compensatrice de congés non pris au taux $TCONGE$.
  - Préavis payé ou dispensé.
  - Indemnité de licenciement économique/légal.
  - Apurement prioritaire des prêts d'entreprise et avances.
  - Émission du reçu pour solde de tout compte numéroté officiel.
- **DANS LE FRONTEND, IL N'Y A AUCUN SERVICE NI ÉCRAN STC.**
  - `contratService.js` ne possède aucune méthode STC.
  - `RuptureContratModal.jsx` clôture le contrat sans jamais proposer de préparer le STC.
  - La DAF ne peut ni simuler ni valider un STC depuis l'interface sans passer par Postman/cURL !

### Recommandations P0 / P1 :
1. **Dans `RuptureContratModal.jsx` / Fiche Contrat** :
   - À la confirmation d'une rupture ou arrivée à terme, proposer un bouton direct :
     **`[ Établir le Solde de Tout Compte (STC) ]`**.
2. **Création d'une modale/tiroir STC (`StcPreparationModal.jsx`)** :
   - Connectée aux endpoints existants :
     - `GET /paie/stc/preparer/{idEmploye}`
     - `POST /paie/stc/calculer` (simulation)
     - `POST /paie/stc/valider` (validation officielle + reçu)
3. **Dans la Fiche Employé (nouvel onglet Paie)** :
   - Afficher l'état du STC (En attente, Calculé, Validé, Quittance émise).
4. **Dans le Module Paie Global** :
   - Onglet ou filtre DAF listant les STC en instance et validés.

---

## 7. Audit des Avances, Acomptes et Variables de Paie (Sections 19 à 23)

### État des Lieux :
- **Entité DB & Backend** : `paie_avance_acompte`, `RefTypeMouvementPaie`, `PaieAjustementController`.
- **Workflow supporté** : Commandes `APPROVE`, `REJECT`, `CANCEL`.
- **Types reconnus** :
  - `ACOMPTE_15` (Acompte de quinzaine)
  - `AVANCE_SPECIALE` (Avance employeur plafonnée au 1/10ème Art. 68)
  - `CALL_PERSO` (Retenue pour communications privées)
  - `PRIME_EXCEPTIONNELLE` (Prime variable ponctuelle)
  - `NOTE_DE_FRAIS` (Remboursement de frais professionnels non imposable)
  - `RETENUE_DIVERSE` (Saisie ou régularisation)
- **Interface actuelle** :
  - `VariablesPaieTab.jsx` permet de saisir et lister ces éléments au niveau d'une période de paie.
  - **Lacune UI** : La saisie se fait uniquement en masse par période. Un gestionnaire ne peut pas saisir une avance ou un acompte directement depuis le dossier du collaborateur au moment de la demande.

---

## 8. Audit de Conformité au Design System Sauge (Sections 25 à 29)

### Respect Global :
- Le module Paie utilise majoritairement les composants du design system :
  - `PageHeader`, `Tabs`, `Button`, `Input`, `Select`, `Badge`, `StatCard`, `Card`, `Table`, `Modal`, `Alert`.
- **Anomalies identifiées à corriger** :
  1. Utilisation résiduelle de classes Tailwind `slate-*` dans `BulletinPaieDrawer.jsx` au lieu de `var(--color-*)`.
  2. Quelques styles inline non nécessaires pour les couleurs de badge dans les vues de comparaison d'anomalies.

---

## 9. Permissions et Contrôle d'Accès RBAC (Section 32)

- Les routes `/paie/*` sont protégées par :
  `requiredPermission="PAIE_READ"` et `allowedRoles={["ROLE_ADMIN"]}`.
- Les actions financières d'écriture (`/paie/calculer-periode`, `/paie/valider-periode`, `/paie/stc/valider`) exigent côté backend `hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')`.
- Les agents de pointage (`ROLE_AGENT_POINTAGE`) sont strictement confinés à `/pointages` et ne peuvent pas accéder aux données salariales.

---

## 10. Synthèse des Recommandations Priorisées

| Recommandation | Cible | Priorité | Complexité |
| :--- | :--- | :--- | :--- |
| **Création du Service & Modal STC** | `stcService.js` + `StcPreparationModal.jsx` | **P0** | Moyenne |
| **Bouton déclencheur STC après Rupture Contrat** | `RuptureContratModal.jsx` & `ContratTravailModal.jsx` | **P0** | Faible |
| **Création de l'Onglet Paie dans la Fiche Employé** | `EmployePaieTab.jsx` dans `EmployeFicheDetailPage.jsx` | **P1** | Moyenne |
| **Nettoyage Slate & Alignement Sauge dans Bulletin Drawer** | `BulletinPaieDrawer.jsx` | **P1** | Faible |
| **Ajout de Tests Vitest Composants Paie & STC** | `frontend/src/tests/` | **P1** | Faible |
| **Harmonisation Cockpit Paie vs Stepper Contrôle** | `CockpitPaieTab.jsx` / `ControleStepPage.jsx` | **P2** | Moyenne |
