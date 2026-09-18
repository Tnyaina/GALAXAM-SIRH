# PLAN D'IMPLÉMENTATION — AMÉLIORATIONS UI/UX DU MODULE PAIE

**Date** : 2026-09-18  
**Référence** : Mission Phase Paie — Axe D (Plan d'Implémentation et Restructuration Ergonomique)  
**Règle d'Or** : Zéro nouvelle architecture. Réutilisation stricte des composants du Design System Sauge existant et des tokens de `frontend/src/index.css`.  

---

## 1. Classification Stratégique des Actions

```text
┌─────────────────┬──────────────────────────────────────────────────────────┐
│ FIX_NOW         │ Nettoyage des styles résiduels (dark mode dans Drawer)   │
├─────────────────┼──────────────────────────────────────────────────────────┤
│ IMPLEMENT_NOW   │ • Service Frontend STC (stcService.js)                   │
│                 │ • Modal de Préparation & Simulation STC                  │
│                 │ • Déclenchement STC depuis la Rupture de Contrat         │
│                 │ • Nouvel Onglet "Paie" dans la Fiche Employé             │
│                 │ • Tests Vitest associés                                  │
├─────────────────┼──────────────────────────────────────────────────────────┤
│ NEXT_ITERATION  │ • Table dédiée STC globale dans Cockpit Paie             │
│                 │ • Échéancier visuel des prêts dans l'onglet Paie         │
│                 │ • Proposition semi-automatique de malus PP (25%)         │
├─────────────────┼──────────────────────────────────────────────────────────┤
│ OPTIONAL        │ • Prévisualisation PDF directe dans le navigateur        │
│                 │ • Assistant pas-à-pas de fin de contrat multi-étapes     │
├─────────────────┼──────────────────────────────────────────────────────────┤
│ DO_NOT_IMPLEMENT│ • Refonte globale du routeur ou du stepper existant      │
│                 │ • Recalcul côté client dans React                        │
│                 │ • Modification du moteur de paie backend                 │
└─────────────────┴──────────────────────────────────────────────────────────┘
```

---

## 2. Détail des Lots `FIX_NOW` & `IMPLEMENT_NOW`

### LOT 1 — Nettoyage Design System Sauge dans `BulletinPaieDrawer.jsx` (`FIX_NOW`)
- **Fichier** : [BulletinPaieDrawer.jsx](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/components/paie/BulletinPaieDrawer.jsx)
- **Objectif** : Éliminer les classes rigides Tailwind `text-slate-800`, `text-slate-900`, `bg-slate-900`, `border-slate-800` pour restaurer un rendu parfait en mode sombre.
- **Modifications** :
  - Lignes 511-512 : Remplacer par `text-[var(--color-fg)]` et `text-[var(--color-fg-muted)]`.
  - Lignes 526-527 : Remplacer par `border-[var(--color-border)]` et `text-[var(--color-fg)]`.
  - Ligne 543 : Remplacer l'aplat noir `bg-slate-900` par `bg-[var(--color-brand-800)]` ou `bg-[var(--color-surface-sunken)]` avec bordure `border-[var(--color-brand-500)]`.
  - Lignes 583-585 : Remplacer par `text-[var(--color-fg-muted)]` et `text-[var(--color-fg)]`.

---

### LOT 2 — Service Frontend STC (`IMPLEMENT_NOW`)
- **Nouveau Fichier** : `frontend/src/api/paie/stcService.js`
- **Objectif** : Exposer les endpoints déjà existants et opérationnels de `StcController.java`.
- **Méthodes** :
  ```javascript
  import apiClient from '@/api/apiClient';

  export const stcService = {
      preparerStc: async (idEmploye, dateDepart = null, motif = 'DEMISSION') => {
          const params = {};
          if (dateDepart) params.dateDepart = dateDepart;
          if (motif) params.motif = motif;
          const res = await apiClient.get(`paie/stc/preparer/${idEmploye}`, { params });
          return res.data;
      },
      simulerStc: async (payload) => {
          const res = await apiClient.post('paie/stc/calculer', payload);
          return res.data;
      },
      validerStc: async (payload) => {
          const res = await apiClient.post('paie/stc/valider', payload);
          return res.data;
      },
      listerStc: async () => {
          const res = await apiClient.get('paie/stc/liste');
          return res.data;
      },
      obtenirRecuStc: async (idStc) => {
          const res = await apiClient.get(`paie/stc/${idStc}/recu`);
          return res.data;
      }
  };
  export default stcService;
  ```

---

### LOT 3 — Modal de Préparation & Simulation STC (`IMPLEMENT_NOW`)
- **Nouveau Fichier** : `frontend/src/components/paie/stc/StcPreparationModal.jsx`
- **Composants réutilisés** :
  - `Modal` de `@/components/ui/overlays/Modal`
  - `Button`, `Input`, `Select` de `@/components/ui/forms`
  - `Badge`, `Card`, `Table` de `@/components/ui/data-display`
  - `Alert` de `@/components/ui/feedback`
- **Fonctionnalités** :
  1. Chargement préparatoire via `stcService.preparerStc(idEmploye)` :
     - Affiche l'ancienneté décomptée (années, mois, date embauche origine).
     - Affiche le solde congés et le taux journalier $TCONGE$ valorisé.
     - Affiche le salaire de base et le taux horaire de référence.
     - Affiche le solde des prêts d'entreprise en cours d'apurement.
  2. Formulaire de paramétrage du départ :
     - Date effective de départ.
     - Motif de rupture (Démission, Fin CDD, Licenciement...).
     - Jours travaillés sur le dernier mois (prorata /30).
     - Mois de préavis (payé ou dispensé).
     - Indemnité de licenciement (si applicable).
     - Mode de règlement (Virement, Chèque, Espèces).
  3. Action "Simuler" :
     - Appel `POST /paie/stc/calculer` et affichage instantané du brut, des indemnités, des déductions de prêt et du net à payer.
  4. Action "Valider et Émettre le Reçu" :
     - Appel `POST /paie/stc/valider` avec génération du numéro officiel (`STC-2026-XXXX`).

---

### LOT 4 — Liaison Contrat ↔ STC (`IMPLEMENT_NOW`)
- **Fichiers modifiés** :
  - [RuptureContratModal.jsx](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/pages/employe/carriere/b/RuptureContratModal.jsx)
  - [ContratTravailModal.jsx](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/pages/employe/carriere/b/ContratTravailModal.jsx)
- **Modifications** :
  - Dès qu'un contrat est rompu ou arrive à terme, afficher une bannière ou action secondaire :
    `[ Établir le Solde de Tout Compte ]`.
  - Le clic ouvre directement `StcPreparationModal` pré-rempli avec l'ID employé, la date de fin et le motif.

---

### LOT 5 — Onglet "Paie" dans la Fiche Employé (`IMPLEMENT_NOW`)
- **Nouveau Fichier** : `frontend/src/components/employe/tabs/EmployePaieTab.jsx`
- **Fichier modifié** : [EmployeFicheDetailPage.jsx](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/pages/employe/EmployeFicheDetailPage.jsx)
- **Contenu de l'onglet** :
  1. **En-tête Rémunération** :
     - Salaire contractuel actuel.
     - Taux horaire de référence (Base / 173.33).
     - Taux journalier congé ($TCONGE$ gelé issu de la période de référence active).
     - Site de rattachement et forfaits associés (Transport, Repas).
  2. **Dernier Bulletin & Historique** :
     - Table des bulletins de paie émis pour le collaborateur (Période, Brut, Net imposable, IRSA, Net à payer, Statut).
     - Bouton "Voir le bulletin" ouvrant le `BulletinPaieDrawer` existant.
  3. **Mouvements & Déductions en cours** :
     - Acomptes du mois en cours.
     - Déductions téléphoniques (`CALL PERSO`).
     - Prêts d'entreprise en cours et mensualité retenue.
  4. **Solde de Tout Compte (si applicable)** :
     - Si le contrat est terminé : statut du STC (Validé, Quittance, Reçu numéroté).
     - Bouton "Préparer / Consulter le STC".

---

## 3. Plan de Vérification et Tests Automatisés

### A. Tests Frontend (Vitest)
1. **`frontend/src/tests/BulletinPaieDrawer.test.js`** :
   - Vérifie le formatage des montants, l'absence de valeurs `undefined`, et l'affichage des explications backend-driven sans régression.
2. **`frontend/src/tests/EmployePaieTab.test.js`** :
   - Vérifie le montage de l'onglet Paie dans la fiche employé, le rendu de l'historique des bulletins et des indicateurs de rémunération.
3. **`frontend/src/tests/StcPreparationModal.test.js`** :
   - Vérifie le chargement des données préparatoires, le calcul simulé et la validation avec reçu officiel.

### B. Validation Globale des Suites
- `npm test` dans `frontend/` : Vérification $\ge 30$ tests PASS.
- `npm run build` dans `frontend/` : Compilation Vite propre en $< 500$ ms.
- `mvn test` dans `backend/` : 506+ tests backend PASS.
- `git diff --check` : 0 anomalie de whitespace.
