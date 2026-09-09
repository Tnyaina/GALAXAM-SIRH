# AUDIT UX/UI — SIRH GALAXAM — PARTIE 2
## Scores, Roadmap & Backlog

---

# 12. SCORE GLOBAL & RAPPORT FINAL

## 12.1 Scores par Domaine

| Domaine | Score | Commentaire |
|---|---|---|
| **UI** | 62/100 | Palette cohérente, typographie système, manque de micro-animations |
| **UX** | 48/100 | Workflows non guidés, duplications, ruptures de contexte |
| **Ergonomie** | 52/100 | Densité élevée, navigation libre dans workflows séquentiels |
| **Logique métier** | 78/100 | Backend solide, règles légales correctement implémentées |
| **Workflows** | 45/100 | Navigation libre trop permissive pour des tâches séquentielles |
| **Fluidité** | 50/100 | Trop de navigations pour des tâches unitaires |
| **Cohérence** | 55/100 | Duplication PointageTab/CongesTab, validation DAF dans mauvais module |
| **Design System** | 65/100 | Bien structuré mais tokens workflow manquants, styles hardcodés |
| **Accessibilité** | 55/100 | Couleurs sémantiques : bonne base. Aria-labels : non vérifié. |
| **Responsive** | 45/100 | Tables 22 colonnes sur mobile = inutilisable |

**Score global : 55.5 / 100**

> L'architecture technique est excellente. La dette est UX, pas technique.

---

## 12.2 Top 10 Problèmes (par impact)

| # | Problème | Impact | Module |
|---|---|---|---|
| 1 | Validation DAF pointage dans /paie — rupture de contexte | CRITIQUE | Pointage/Paie |
| 2 | PointageTab.jsx (48 Ko) dans la fiche employé — duplication module entier | CRITIQUE | Employé/Pointage |
| 3 | Workflow Pointage non guidé — 4 onglets libres pour processus séquentiel | ÉLEVÉ | Pointage |
| 4 | Workflow Paie non guidé — 3 pages séparées pour processus continu | ÉLEVÉ | Paie |
| 5 | Chargement 1000 employés côté client — scalabilité nulle | ÉLEVÉ | Employé |
| 6 | Absence d'indicateur de progression dans les workflows | ÉLEVÉ | Tous |
| 7 | Police système — incohérence cross-platform | MOYEN | Global |
| 8 | Styles Tailwind hardcodés dans les pages (`bg-emerald-500/10` répété) | MOYEN | Design System |
| 9 | CongesTab.jsx (14 Ko) dans fiche employé — duplication | MOYEN | Employé/Congés |
| 10 | CarrieresSupervision séparée de la liste Employés | FAIBLE | Employé |

---

## 12.3 Top 10 Améliorations (Impact / Effort)

| # | Amélioration | Impact | Effort | Ratio |
|---|---|---|---|---|
| 1 | Workflow Pointage linéaire guidé (cockpit ① → ④) | ÉLEVÉ | MOYEN | ★★★★★ |
| 2 | Supprimer PointageTab fiche employé + lien contextuel | ÉLEVÉ | FAIBLE | ★★★★★ |
| 3 | Déplacer validation DAF dans module Pointage | ÉLEVÉ | FAIBLE | ★★★★★ |
| 4 | Validation congé inline Dashboard | MOYEN | FAIBLE | ★★★★☆ |
| 5 | Widget cycle mensuel sur le Dashboard | MOYEN | FAIBLE | ★★★★☆ |
| 6 | Police Inter | FAIBLE | TRÈS FAIBLE | ★★★★☆ |
| 7 | Tokens statuts workflow dans Design System | MOYEN | FAIBLE | ★★★★☆ |
| 8 | Supprimer CongesTab fiche employé + lien contextuel | MOYEN | FAIBLE | ★★★☆☆ |
| 9 | Fusion CarrièresSupervision dans EmployeList | FAIBLE | MOYEN | ★★★☆☆ |
| 10 | Vue calendrier Gantt dans module Congés | MOYEN | ÉLEVÉ | ★★★☆☆ |

---

## 12.4 Décisions finales par page

| Page | Verdict | Action |
|---|---|---|
| Dashboard.jsx | RESTRUCTURER | Ajouter cockpit mensuel + actions inline |
| EmployeList.jsx | MODIFIER | Simplifier filtres, supprimer stat-cards, ajouter onglet Carrières |
| EmployeFicheDetailPage.jsx | RESTRUCTURER | Supprimer onglets Pointage et Congés |
| PointageTab.jsx | SUPPRIMER | Remplacé par lien contextuel |
| CongesTab.jsx | SUPPRIMER | Remplacé par lien contextuel |
| CarrieresSupervisionPage.jsx | FUSIONNER | Onglet de EmployeList |
| GestionPointagesPage.jsx | REMPLACER | Nouveau cockpit linéaire ① → ④ |
| SynthesePointagePage.jsx | DÉCOMPOSER | 1149L → 2-3 composants ciblés |
| AnomaliesPointagePage.jsx | INTÉGRER | Dans le cockpit Pointage (inline) |
| GestionCongesPage.jsx | RESTRUCTURER | Ajouter onglet Planning/Calendrier |
| PeriodePaiePage.jsx + GestionPaiePage.jsx | FUSIONNER | Un seul cockpit Paie ① → ⑤ |
| PointageValidationDafTab.jsx | DÉPLACER | Étape ④ du cockpit Pointage |

---

# 13. ROADMAP DE REFONTE

## Phase 1 — Corrections Critiques (Sprint 1-2, ~2 semaines)

**Objectif :** Éliminer les duplications et ruptures de contexte les plus graves.

| Ticket | Action | Fichier | Effort |
|---|---|---|---|
| PTG-001 | Supprimer PointageTab de la fiche employé | EmployeFicheDetailPage.jsx | 0.5j |
| PTG-001 | Ajouter lien contextuel pointage filtré | EmployeSideSummaryPanel.jsx | 0.5j |
| EMP-001 | Supprimer CongesTab de la fiche employé | EmployeFicheDetailPage.jsx | 0.5j |
| EMP-001 | Ajouter lien contextuel congés filtré | EmployeSideSummaryPanel.jsx | 0.5j |
| PTG-002 | Déplacer PointageValidationDafTab → cockpit Pointage | GestionPointagesPage.jsx | 1j |
| PTG-002 | Supprimer onglet validation de /paie | PeriodePaiePage.jsx | 0.5j |
| DSH-001 | Ajouter badge statut période sur Dashboard | Dashboard.jsx | 0.5j |

**Total : ~4j de développement**
**Impact immédiat :** Suppression des 2 duplications majeures

---

## Phase 2 — Workflows Guidés (Sprint 3-5, ~3 semaines)

**Objectif :** Guider l'utilisateur dans les workflows séquentiels.

| Ticket | Action | Effort |
|---|---|---|
| PTG-003 | Créer WorkflowStepper.jsx (composant réutilisable) | 1j |
| PTG-003 | Refonte PointageWorkflowNav — indicateur + blocage | 1j |
| PTG-003 | Blocage "Transmettre" si anomalies bloquantes | 0.5j |
| PTG-003 | Bannière motif rejet DAF visible pour l'Agent | 0.5j |
| DSH-002 | Validation congé inline dans CongesAValiderWidget | 1j |
| DSH-001 | Widget CycleRhWidget — progression mensuelle | 0.5j |
| PAI-001 | Cockpit Paie — indicateur étapes ① → ⑤ | 1.5j |

**Total : ~6j de développement**

---

## Phase 3 — Architecture des Pages (Sprint 6-8, ~3 semaines)

| Ticket | Action | Effort |
|---|---|---|
| EMP-002 | Fusion CarrièresSupervision dans EmployeList | 2j |
| CNG-001 | Ajouter onglet Planning/Calendrier dans GestionCongesPage | 3j |
| PTG-004 | Décomposer SynthesePointagePage (1149L) | 2j |
| PAI-002 | Fusion GestionPaiePage + PeriodePaiePage | 2j |

**Total : ~9j de développement**

---

## Phase 4 — Design System (Sprint 9-10, ~1.5 semaine)

| Ticket | Action | Effort |
|---|---|---|
| DS-001 | Tokens statuts workflow dans index.css | 0.5j |
| DS-001 | Créer StatusBadge.jsx | 0.5j |
| DS-002 | Migrer vers Inter (Google Fonts) | 0.5j |
| DS-003 | Éliminer les styles hardcodés (audit + migration) | 2j |
| DS-004 | Variante bouton workflow-cta | 0.5j |

**Total : ~4j de développement**

---

## Phase 5 — Polish UI (Sprint 11-12, ~2 semaines)

| Action | Effort |
|---|---|
| Micro-animations transitions (open/close modales, changements état) | 2j |
| Révision densité et whitespace des tables | 1j |
| Hiérarchie typographique cohérente | 1j |
| EmptyState illustrées et contextuelles | 1j |
| Loading skeletons cohérents sur tous les modules | 1j |

**Total : ~6j de développement**

---

## Phase 6 — Responsive & Accessibilité (Sprint 13-14, ~2 semaines)

| Action | Effort |
|---|---|
| Tables Pointage responsive (freeze colonne Employé) | 2j |
| Tables Paie responsive | 1j |
| Focus management et focus trap sur modales | 1j |
| Audit et ajout aria-labels manquants | 1j |
| Vérification contrastes WCAG AA (outillée) | 1j |

**Total : ~6j de développement**

---

## Résumé Roadmap

| Phase | Durée | Dev | Priorité |
|---|---|---|---|
| Phase 1 — Corrections critiques | 2 semaines | ~4j | P0 |
| Phase 2 — Workflows guidés | 3 semaines | ~6j | P0 |
| Phase 3 — Architecture pages | 3 semaines | ~9j | P1 |
| Phase 4 — Design System | 1.5 semaine | ~4j | P1 |
| Phase 5 — Polish UI | 2 semaines | ~6j | P2 |
| Phase 6 — Responsive/A11y | 2 semaines | ~6j | P2 |
| **TOTAL** | **14 semaines** | **~35j** | |

---

# 14. BACKLOG D'IMPLÉMENTATION

---

## [P0 — CRITIQUE] PTG-001
**Supprimer PointageTab.jsx de la fiche employé**

**Problème :**
PointageTab.jsx (48 Ko) dans EmployeFicheDetailPage est un module complet replié dans un onglet. Maintenir 2 implémentations parallèles crée une dette technique et une incohérence UX majeure.

**Solution :**
1. Supprimer l'onglet "Pointages & Présence" de la fiche employé
2. Dans EmployeSideSummaryPanel, ajouter section "Accès rapides" :
   - "→ Voir pointages de cet employé" → `/pointage?tab=saisie&employe={id}`
   - Afficher solde du dernier mois en résumé dans le panel
3. Le module Pointage doit accepter `?employe=` en query param pour filtrer la grille

**Fichiers impactés :**
- `frontend/src/pages/employe/EmployeFicheDetailPage.jsx` (supprimer onglet)
- `frontend/src/components/employe/EmployeSideSummaryPanel.jsx` (ajouter lien)
- `frontend/src/components/employe/tabs/PointageTab.jsx` (à supprimer)
- `frontend/src/pages/pointage/SaisieQuotidiennePage.jsx` (accepter ?employe=)

**Complexité :** FAIBLE
**Priorité :** P0 — Sprint 1

---

## [P0 — CRITIQUE] PTG-002
**Intégrer la validation DAF pointage dans le module Pointage**

**Problème :**
PointageValidationDafTab.jsx (38 Ko) est placé dans `/paie` alors que c'est la dernière étape du workflow Pointage. La DAF doit changer de module pour valider le pointage.

**Solution :**
1. Créer un onglet/étape ④ "Arbitrage DAF" dans GestionPointagesPage
2. Y déplacer PointageValidationDafTab
3. Rendre cet onglet visible uniquement si :
   - `hasPermission('POINTAGE_VALIDATE')` === true
   - Statut période === 'A_VALIDER'
4. Dans PeriodePaiePage, supprimer le tab 'validation' (ou le rediriger vers /pointage)

**Fichiers impactés :**
- `frontend/src/pages/pointage/GestionPointagesPage.jsx`
- `frontend/src/components/pointage/PointageWorkflowNav.jsx`
- `frontend/src/pages/paie/PeriodePaiePage.jsx`
- `frontend/src/pages/paie/PointageValidationDafTab.jsx` (déplacer vers /pointage)

**Complexité :** MOYENNE
**Priorité :** P0 — Sprint 1

---

## [P0 — CRITIQUE] PTG-003
**Créer un indicateur de progression et un système de blocage dans le workflow Pointage**

**Problème :**
L'utilisateur navigue librement entre 4 onglets pour un processus qui doit être séquentiel. Il n'y a aucun guidage, aucun blocage, aucun indicateur d'étape.

**Solution :**

Créer `WorkflowStepper.jsx` — composant réutilisable :
```jsx
<WorkflowStepper
  steps={[
    { id: 'saisie', label: '① Saisie', progress: '17/22j', status: 'active' },
    { id: 'verification', label: '② Vérification', status: 'pending' },
    { id: 'transmission', label: '③ Transmission', status: 'locked' },
    { id: 'arbitrage', label: '④ Arbitrage DAF', status: 'locked', dafOnly: true },
  ]}
  currentStep="saisie"
/>
```

Règles de déblocage :
- ② accessible dès qu'au moins 1 sauvegarde a été effectuée
- ③ "Transmettre" accessible seulement si `anomaliesBloquantes === 0`
- ④ visible uniquement DAF, accessible si statut === 'A_VALIDER'

Si l'utilisateur clique sur ③ avec des anomalies restantes :
→ Banner d'erreur : "X anomalies bloquantes doivent être corrigées avant transmission"
→ Lien "Corriger les anomalies" qui scrolle vers la liste

**Fichiers à créer :**
- `frontend/src/components/ui/navigation/WorkflowStepper.jsx`

**Fichiers impactés :**
- `frontend/src/pages/pointage/GestionPointagesPage.jsx`
- `frontend/src/components/pointage/PointageWorkflowNav.jsx` (refonte complète)
- `frontend/src/pages/pointage/SuiviPeriodePage.jsx`

**Complexité :** MOYENNE
**Priorité :** P0 — Sprint 2

---

## [P1 — ÉLEVÉ] EMP-001
**Supprimer CongesTab.jsx de la fiche employé**

**Problème :**
CongesTab.jsx (14 Ko) dans EmployeFicheDetailPage duplique le module GestionCongesPage.

**Solution :**
1. Supprimer l'onglet "Congés & Absences" de la fiche employé
2. Dans EmployeSideSummaryPanel, afficher le solde de congés (déjà probablement calculé)
3. Ajouter lien contextuel : "→ Voir les congés de cet employé" → `/conges?employe={id}`
4. Le module Congés doit accepter `?employe=` pour filtrer les demandes

**Fichiers impactés :**
- `frontend/src/pages/employe/EmployeFicheDetailPage.jsx`
- `frontend/src/components/employe/EmployeSideSummaryPanel.jsx`
- `frontend/src/components/employe/tabs/CongesTab.jsx` (à supprimer)

**Complexité :** FAIBLE
**Priorité :** P1 — Sprint 1

---

## [P1 — ÉLEVÉ] DSH-001
**Enrichir le Dashboard avec le cockpit de cycle mensuel**

**Problème :**
Le Dashboard ne montre pas l'état du cycle mensuel (Pointage → Paie). L'utilisateur doit naviguer vers /pointage pour savoir où il en est.

**Solution :**
Créer `CycleRhWidget.jsx` :
```text
[CYCLE MENSUEL — Septembre 2026]
Pointage : ① Saisie en cours ●●●●●●○○ 17/22j
Paie : En attente clôture pointage — Non calculable
[→ Accéder au pointage]
```

API requise :
- `GET /periodes?statut=EN_COURS` → période active + statut
- `GET /pointage/stats?idPeriode=X` → jours saisis / jours totaux

**Fichiers impactés :**
- `frontend/src/pages/dashboard/Dashboard.jsx`
- `frontend/src/components/dashboard/widgets/` (nouveau fichier CycleRhWidget.jsx)
- `frontend/src/hooks/useDashboard.js` (ajouter données cycle)

**Complexité :** FAIBLE
**Priorité :** P1 — Sprint 2

---

## [P1 — ÉLEVÉ] DSH-002
**Validation de congés inline depuis le Dashboard**

**Problème :**
Les congés à valider sont listés dans le widget Dashboard mais la validation nécessite de naviguer vers /conges. 2 clics et 1 changement de page inutiles.

**Solution :**
Dans `CongesAValiderWidget.jsx`, ajouter boutons [Valider ✓] [Refuser ✗] directement sur chaque ligne. Au clic :
- Valider → confirmation simple (1 clic) → action API + mise à jour optimiste
- Refuser → modal légère avec champ motif obligatoire → action API

Réutiliser les appels API existants de `congeService`.

**Fichiers impactés :**
- `frontend/src/components/dashboard/widgets/CongesAValiderWidget.jsx`
- `frontend/src/components/conges/ValidationDemandeModal.jsx` (réutiliser)

**Complexité :** FAIBLE
**Priorité :** P1 — Sprint 2

---

## [P2 — MOYEN] EMP-002
**Fusionner CarrièresSupervision dans EmployeList comme onglet**

**Problème :**
CarrieresSupervisionPage (/employes/carrieres) est une page séparée pour une vue qui est fonctionnellement liée à la liste des employés. L'utilisateur navigue entre 2 pages pour un même objet métier.

**Solution :**
1. Ajouter dans EmployeList.jsx une barre d'onglets :
   `[Répertoire] [Contrats & Carrières]`
2. L'onglet "Contrats & Carrières" charge le contenu de CarrieresSupervisionPage
3. Route /employes/carrieres → redirect vers /employes?tab=carrieres
4. CarrieresSupervisionPage.jsx → composant à intégrer dans EmployeList

**Fichiers impactés :**
- `frontend/src/pages/employe/EmployeList.jsx`
- `frontend/src/pages/employe/CarrieresSupervisionPage.jsx` (transformer en composant)
- Routes (ajouter redirect)

**Complexité :** MOYENNE
**Priorité :** P2 — Sprint 6

---

## [P2 — MOYEN] DS-001
**Créer les tokens de statut et le composant StatusBadge**

**Problème :**
Les statuts métier (EN_COURS, A_VALIDER, VALIDEE, CLOTUREE) sont stylisés avec des classes Tailwind hardcodées et répétées dans chaque page. Aucune cohérence garantie.

**Solution :**
1. Dans `index.css`, ajouter les tokens :
```css
/* Statuts de période */
--color-status-active: theme('colors.sky.600');
--color-status-active-bg: theme('colors.sky.50');
--color-status-pending: theme('colors.amber.700');
--color-status-pending-bg: theme('colors.amber.50');
--color-status-validated: theme('colors.green.700');
--color-status-validated-bg: theme('colors.green.50');
--color-status-closed: theme('colors.ink.600');
--color-status-closed-bg: theme('colors.ink.100');
```

2. Créer `StatusBadge.jsx` :
```jsx
<StatusBadge statutCode="EN_COURS" />
<StatusBadge statutCode="VALIDEE" />
```

3. Remplacer tous les styles hardcodés dans SynthesePointagePage, PeriodePaiePage, CarrieresSupervisionPage, etc.

**Fichiers à créer :**
- `frontend/src/components/ui/data-display/StatusBadge.jsx`

**Fichiers impactés :**
- `frontend/src/index.css`
- Toutes les pages avec statuts hardcodés

**Complexité :** FAIBLE
**Priorité :** P2 — Sprint 9

---

## [P3 — FUTUR] CNG-001
**Ajouter une vue Planning/Calendrier dans le module Congés**

**Problème :**
Aucune vue calendrier. La DAF ne peut pas visualiser rapidement qui est absent sur une période donnée ni détecter les chevauchements.

**Solution :**
Ajouter un onglet "Planning" dans GestionCongesPage avec :
- Vue timeline (type Gantt) des absences par mois
- Filtrable par équipe / site
- Codes couleur par type de congé (Annuel / Maladie / Sans Solde)
- Tooltip avec nom + durée + statut au survol

Implémentation : CSS pur ou librairie légère (react-calendar-timeline ou similaire)

**Fichiers à créer :**
- `frontend/src/components/conges/PlanningGanttTab.jsx`

**Fichiers impactés :**
- `frontend/src/pages/conges/GestionCongesPage.jsx`

**Complexité :** ÉLEVÉE
**Priorité :** P3 — Sprint 6

---

## [P3 — FUTUR] DS-002
**Migrer vers la police Inter**

**Problème :**
`system-ui` produit un rendu différent sur macOS (SF Pro), Windows (Segoe UI), Android (Roboto). Incohérent pour un SaaS B2B professionnel.

**Solution :**
```html
<!-- index.html -->
<link rel="preconnect" href="https://fonts.googleapis.com">
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
```
```css
/* index.css */
--font-sans: 'Inter', system-ui, -apple-system, sans-serif;
```

**Fichiers impactés :**
- `frontend/index.html`
- `frontend/src/index.css`

**Complexité :** TRÈS FAIBLE (1h de travail)
**Priorité :** P3 — peut être fait en parallèle de n'importe quelle phase

---

## Synthèse du Backlog

| Ticket | Titre | Priorité | Effort | Sprint |
|---|---|---|---|---|
| PTG-001 | Supprimer PointageTab de la fiche employé | P0 | 1j | 1 |
| PTG-002 | Validation DAF pointage dans module Pointage | P0 | 1.5j | 1 |
| PTG-003 | WorkflowStepper + blocages workflow Pointage | P0 | 3j | 2 |
| EMP-001 | Supprimer CongesTab de la fiche employé | P1 | 1j | 1 |
| DSH-001 | Widget CycleRhWidget sur Dashboard | P1 | 1j | 2 |
| DSH-002 | Validation congé inline Dashboard | P1 | 1j | 2 |
| EMP-002 | Fusion CarrièresSupervision dans EmployeList | P2 | 2j | 6 |
| DS-001 | Tokens statuts + StatusBadge composant | P2 | 1.5j | 9 |
| CNG-001 | Vue Planning/Calendrier dans module Congés | P3 | 3j | 6 |
| DS-002 | Migration police Inter | P3 | 0.5j | Tout sprint |

---

*Audit UX/UI complet — SIRH GALAXAM — Septembre 2026*
*Senior Product Designer / UX Designer / Business Analyst SIRH*

