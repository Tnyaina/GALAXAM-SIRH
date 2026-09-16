# CARTOGRAPHIE UX INITIALE DU MODULE PAIE (AVANT SPRINT 2B)
**Date :** 16 Septembre 2026  
**Auteur :** Antigravity Pair-Programming Assistant  
**Projet :** GALAXAM SIRH — Module Paie & Rémunération  

---

## 1. Vue d'Ensemble & Architecture Initiale de Navigation

Avant les modifications du Sprint 2B, l'accès et le cheminement utilisateur au sein du module Paie étaient fragmentés et exigeaient de la DAF une connaissance technique préalable des routes et des statuts de base de données.

### 1.1 Parcours Utilisateur Observé (Avant)

```text
SIDEBAR
   ↓ (Clic "Paie & Rémunération" ou sous-menus)
GESTION_PAIE_PAGE (/paie?tab=...)
   │
   ├── Onglet 1 : "Cockpit Paie" (/paie?tab=cockpit)
   │     - Monolithe UI de 949 lignes (CockpitPaieTab.jsx)
   │     - Mélange : KPIs globaux, simulation, calcul réel, table des 25 bulletins, 
   │       recherche textuelle, filtres alertes, drawer bulletin, exports rapides.
   │     - La DAF ne sait pas par quoi commencer (pas de séquence linéaire).
   │
   ├── Onglet 2 : "Variables & Primes" (/paie?tab=variables)
   │     - Liste brute des variables d'acomptes/avances sans indicateur d'impact sur le calcul.
   │     - Si une variable est modifiée, aucune bannière n'indiquait que la paie devait être recalculée.
   │
   ├── Onglet 3 : "Périodes de Paie" (/paie?tab=periodes)
   │     - Gestion technique CRUD des périodes (ouverture, transmission, clôture).
   │     - Coupé du flux de travail quotidien de calcul.
   │
   └── Onglet 4 : "Déclarations & Exports" (/paie?tab=exports)
         - Accessible même si la période n'était pas calculée ni validée, générant des erreurs 403/400.
```

---

## 2. Dysfonctionnements et Ruptures UX Identifiés

| ID | Composant / Page | Sévérité | Problème Constaté | Impact DAF |
|---|---|---|---|---|
| **UX-01** | `Sidebar.jsx` | CRITIQUE | Algorithme de correspondance d'URL ambigu : plusieurs menus apparaissaient actifs simultanément (`PAIE_MODULE` + `PAIE_PERIODES` + section parent). De plus, toute section simplement dépliée prenait la couleur active `--color-sidebar-fg-active`. | Confusion majeure sur la localisation actuelle dans le SIRH. |
| **UX-02** | `ref_menu` (BDD) | MAJEURE | Présence de sous-menus techniques (`PAIE_PERIODES`, `PAIE_VARIABLES`, `PAIE_BULLETINS`) dans la Sidebar principale. | La Sidebar reflétait les étapes internes d'une campagne plutôt que les grands modules SIRH. |
| **UX-03** | `GestionPaiePage.jsx` | MAJEURE | Absence d'en-tête de campagne unifié et d'action principale évidente. L'utilisateur arrivait sur un tableau sans savoir s'il devait préparer, calculer ou valider. | Risque d'omission d'étapes critiques (ex: calcul sans contrôle préalable du gel de pointage). |
| **UX-04** | Stepper de Campagne | CRITIQUE | Pas de stepper backend-driven informant des étapes complétées, en cours, bloquées ou verrouillées. Le stepper frontend était purement cosmétique. | La DAF ne savait pas si des blocages existaient (ex: pointage non gelé, employé sans contrat). |
| **UX-05** | Recalcul après modification de variables | HAUTE | Aucune détection ni avertissement lorsqu'une prime ou un acompte était ajouté après un premier calcul de paie. | Risque de valider et payer des bulletins avec des montants obsolètes. |
| **UX-06** | Navigation locale | MOYENNE | Pas de barre de boutons de bas de page standardisée (`PayrollNavigationFooter`). La DAF devait scroller vers le haut et cliquer manuellement sur des onglets pour avancer. | Rupture de la sensation de progression fluide (wizard). |

---

## 3. Cible Fonctionnelle Sprint 2B

Transformer le module Paie en un **workflow de campagne guidé, unifié et backend-driven** en 7 étapes séquentielles :

```text
1. PRÉPARATION   → Vérification du gel du pointage et des prérequis
2. POPULATION    → Salariés inclus, exclus et cas bloqués
3. VARIABLES     → Heures supp., primes, acomptes et retenues
4. CALCUL        → Simulation ou calcul réel persisté
5. CONTRÔLE      → Alertes, écarts M-1 et revue individuelle (Drawer)
6. VALIDATION    → Validation des bulletins et clôture définitive
7. SORTIES       → Bulletins PDF, SAFI, DNTS CNaPS, Virements
```

Chaque étape reste une **page autonome** (avec sa propre URL deep-link), mais les transitions entre étapes se comportent avec la fluidité d'un **wizard guidé**.
