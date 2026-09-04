# AUDIT UI/UX COMPLET — SIRH GALAXAM

**Date** : 2026-09-01  
**Version analysée** : Branche courante (commit HEAD)  
**Stack** : React 18 + Vite + Tailwind CSS 4 + Axios + Spring Boot (Java) + MariaDB  
**Modules analysés** : Employés, Contrats & Carrière, Congés & Absences, Pointage & Présence

---

## Executive Summary

### Scores globaux (sur 10)

| Dimension | Score | Commentaire |
|---|---|---|
| **UX Globale** | **6.5/10** | Workflows métier bien pensés mais guidage utilisateur insuffisant |
| **UI Cohérence** | **7/10** | Design system solide, quelques divergences inter-modules |
| **Workflows Métier** | **8/10** | Architecture workflow sophistiquée, brouillon→validation→application |
| **Architecture Information** | **6/10** | Navigation correcte mais hiérarchie parfois confuse |
| **Cohérence Formulaires** | **6.5/10** | Bonne validation backend, faible validation inline frontend |
| **Design System** | **7.5/10** | Tokens bien définis, sous-utilisés dans certains composants |
| **Accessibilité** | **5/10** | Focus visible, skip-link ✓ — Labels, ARIA, clavier ✗ |
| **Responsive** | **5.5/10** | Desktop-first correct, tables larges problématiques mobile |
| **Robustesse Formulaires** | **6/10** | Anti double-submit ✓, validation inline insuffisante |
| **Cohérence Frontend/API** | **7.5/10** | Conventions respectées, quelques cas de désynchronisation |

### Score global : **6.5/10**

> [!WARNING]
> Le SIRH est fonctionnellement riche mais souffre de lacunes en termes de guidage utilisateur, cohérence cross-module et accessibilité. Les fondations architecturales sont solides, ce qui rend les améliorations très réalisables.

---

## Top 10 des problèmes

| # | ID | Priorité | Module | Problème |
|---|---|---|---|---|
| 1 | UX-EMP-001 | 🔴 P0 | Employés | Désactivation manuelle découplée du workflow contrats → incohérence statut |
| 2 | UX-CON-001 | 🔴 P0 | Contrats | Pas de blocage frontend pour embauche si contrat actif existant |
| 3 | UX-PTG-001 | 🔴 P1 | Pointage | `size: 1000` en dur — pas de pagination serveur, crash à grande échelle |
| 4 | UX-CON-002 | 🔴 P1 | Contrats | `initierRupture` utilise le endpoint `/transition` au lieu de `/rupture` |
| 5 | UX-CNG-001 | 🔴 P1 | Congés | Module autonome sans lien contextuel vers la fiche employé |
| 6 | UX-NAV-001 | 🟠 P2 | Navigation | Absence de breadcrumb cohérent dans les modules Pointage et Congés |
| 7 | UX-FRM-001 | 🟠 P2 | Formulaires | Validation inline quasi inexistante — erreurs uniquement après soumission |
| 8 | UX-DSN-001 | 🟠 P2 | Design System | Couleurs hardcodées Tailwind (`slate-*`, `emerald-*`) au lieu des tokens |
| 9 | UX-ACC-001 | 🟡 P3 | Accessibilité | Labels manquants, navigation clavier incomplete, ARIA insuffisant |
| 10 | UX-RSP-001 | 🟡 P3 | Responsive | Tables du pointage non scrollables horizontalement sur mobile |

---

## Top 10 des améliorations

| # | Module | Amélioration |
|---|---|---|
| 1 | Global | Recherche globale (Cmd+K) cross-module (employés, contrats, congés) |
| 2 | Employés | Stepper visuel d'onboarding : Créer → Contrat → Affectation → Documents |
| 3 | Contrats | Sidebar de contexte contrat avec indicateurs d'essai et actions rapides |
| 4 | Congés | Calendrier visuel d'équipe avec vue par semaine/mois |
| 5 | Pointage | Batch save avec indicateur de progression et undo |
| 6 | Formulaires | Validation inline temps réel avec debounce sur les champs critiques |
| 7 | Tables | Virtualisation pour les listes > 100 lignes |
| 8 | Navigation | Persistance des filtres dans l'URL pour toutes les listes |
| 9 | Feedback | Toast contextuel avec lien d'action (ex: "Voir le contrat créé") |
| 10 | Global | Dark mode accessible avec vérification automatique des contrastes WCAG |

---

# 1. Architecture Globale

## 1.1 Cartographie du projet

```
GALAXAM-SIRH/
├── frontend/                     # React 18 + Vite
│   ├── src/
│   │   ├── api/                  # Services API (Axios)
│   │   │   ├── apiClient.js      # Intercepteur JWT, refresh, error handling
│   │   │   ├── congeService.js
│   │   │   ├── employe/          # employeService, contratService, documentRhService
│   │   │   ├── pointage/         # 4 services pointage
│   │   │   └── admin/
│   │   ├── components/
│   │   │   ├── ui/               # Design System (10 forms, 9 data-display, 4 feedback, 5 overlays, 6 nav)
│   │   │   ├── layout/           # Layout, Sidebar, Topbar, Footer, NotificationBell
│   │   │   ├── employe/          # EmployeTable, EmployeFilterBar, EmployeHeaderCard, form/, tabs/
│   │   │   ├── carriere/         # ContratActifSection, Timeline, Modals, Supervision, form/
│   │   │   ├── conges/           # DemandeCongeModal, Validation, Soldes, Paramètres
│   │   │   ├── pointage/         # PointageGridTable, Toolbar, Anomalies, Synthèse
│   │   │   └── parametres/       # Admin: structure, paie, securité, referentiels
│   │   ├── pages/
│   │   │   ├── employe/          # EmployeList, EmployeFicheDetailPage, CarrieresSupervisionPage
│   │   │   ├── conges/           # GestionCongesPage
│   │   │   ├── pointage/         # SaisieQuotidienne, Anomalies, Synthèse, PeriodePaie
│   │   │   ├── dashboard/
│   │   │   └── parametres/
│   │   ├── hooks/                # useCarriere, useContractReferentiels, useDashboard, useDebounce
│   │   │   ├── carriere/         # useCarriereModals, useTransitionForm
│   │   │   ├── conges/           # useDemandeCongeForm, useDemandesValidation, useSoldesOverview
│   │   │   └── pointage/         # usePointageSaisie, usePointageAnomalies, etc.
│   │   ├── context/              # AuthContext.jsx (JWT, permissions, rôles)
│   │   ├── routes/               # Router.jsx, ProtectedRoute.jsx
│   │   ├── utils/                # carriereUtils, pointageUtils, formatters, iconRegistry
│   │   └── services/             # periodePaieService
│   └── docs/                     # runtime.md, rapport_bonnes_pratiques_ui_ux.md, ARCHITECTURE_GUIDE.md
│
└── backend/                      # Spring Boot (Java)
    ├── api/controller/           # 38 contrôleurs REST
    ├── model/                    # employe/, contrat/, pointage/, paieconfig/, referentiel/
    │   ├── contrat/              # WorkflowRhService (orchestration centrale ~1000L)
    │   └── pointage/             # PointageCalculateurEngine, CalendrierOuvrableEngine
    └── docs/                     # Contrats-RG.md, conges.md, pointage.md, conception_pointage.md
```

## 1.2 Architecture en 3 couches (Page → Hook → Components)

```
Page (Conteneur/Orchestrateur)
    ↓ appelle
Hook (Logique métier, état, API)
    ↓ expose données + fonctions
Components UI (Rendu pur, props-driven)
```

> [!NOTE]
> Cette architecture est **correctement appliquée** dans les modules Congés (DemandesValidationTab → useDemandesValidation → ValidationFilters/Table/Modals) et Pointage (SaisieQuotidiennePage → usePointageSaisie → composants).
> En revanche, **EmployeList.jsx** (520 lignes) et **EmployeFicheDetailPage.jsx** (532 lignes) sont encore **monolithiques** — la logique métier, l'état et le rendu cohabitent dans le même fichier.

## 1.3 Flux de données global

```
Utilisateur
    ↓
Sidebar (menu backend-driven via /api/menus)
    ↓
ProtectedRoute (rôle + permission)
    ↓
Layout (Topbar + Sidebar + Main)
    ↓
Page → Hook → Service API → Backend Controller → Service métier → DB
    ↓
Réponse API → Hook met à jour state → Re-render composants UI
```

---

# 2. Module Employés

## 2.1 Cartographie

```
Pages :
- EmployeList.jsx (520L)
- EmployeFicheDetailPage.jsx (532L)
- CarrieresSupervisionPage.jsx (15054B)
- dossier/EmployeFormModal.jsx (24359B)
- dossier/EmployeFicheDetail.jsx (16714B)

Routes :
- /employes → EmployeList
- /employes/:id → EmployeFicheDetailPage
- /carrieres → CarrieresSupervisionPage

Components :
- EmployeTable.jsx, EmployeFilterBar.jsx, EmployeHeaderCard.jsx
- EmployeStatsCards.jsx, EmployeSideSummaryPanel.jsx
- form/ : CiviliteStep, CinPermisStep, CoordonneesPhotoStep, CnapsBanqueStep,
          FamilleFormStep, UrgenceFormStep
- tabs/ : FichePersonnelleTab, DocumentsRhTab, FamilleContactsTab,
          CongesTab, PointageTab, HistoriqueStatutTab, UploadDocumentModal

Hooks :
- useContractReferentiels.js

Services API :
- employeService.js (createEmploye, updateEmploye, getEmployes, etc.)
- documentRhService.js (upload, download, delete)
- contratService.js (30+ méthodes pour workflows, contrats, affectations)

Permissions :
- EMPLOYE_READ (lecture)
- EMPLOYE_WRITE (création, modification)
- EMPLOYE_VALIDATE (validation)
```

## 2.2 Workflow reconstruit

```
Liste Employés (/employes)
    ↓ [+ Nouvel Employé]
EmployeFormModal (multi-step: Civilité → CIN → Coordonnées → CNaPS/Banque)
    ↓ POST /api/employes
    ↓ Succès → Redirection vers /employes/:id?tab=carriere&action=new_contract
Fiche Employé → Onglet Carrière
    ↓ [Initier l'Embauche]
EmbaucheModal (Contrat + Affectation + Période d'essai)
    ↓ POST /api/employes/:id/workflows/embauche-initiale
    ↓ Succès → RecapitulatifBrouillonModal
    ↓ [Valider] (action DAF)
    ↓ PUT /api/workflows/:id/valider
    ↓ Contrat ACTIF + Affectation ACTIVE + Employé estActif=true
```

## 2.3 Problèmes identifiés

### UX-EMP-001 — Désactivation manuelle hors workflow

```
ID : UX-EMP-001
Priorité : P0 🔴
Catégorie : Workflow / Métier
Module : Employés
Fichiers concernés :
- src/pages/employe/EmployeList.jsx (L316-342)
- src/pages/employe/EmployeFicheDetailPage.jsx (L149-181)
- src/api/employe/employeService.js (L40-48)

Problème :
L'interface permet de désactiver/réactiver un employé INDÉPENDAMMENT du workflow contrats.
La liste propose un motif de désactivation (Démission, Licenciement, etc.) mais cette action
ne passe PAS par le workflow RH officiel (WorkflowRhService.creerBrouillonRupture).

Pourquoi c'est problématique :
- Contournement du circuit d'approbation DAF → pas de brouillon, pas de validation
- Le contrat actif n'est pas clôturé → incohérence : employé inactif AVEC contrat actif
- Aucun certificat de travail ne peut être généré automatiquement
- L'historique workflow ne trace pas cette désactivation

Impact utilisateur :
L'utilisateur RH peut croire avoir correctement traité un départ alors que le contrat reste ACTIF.

Impact métier :
- Données RH incohérentes
- Impact potentiel sur paie future (employé "inactif" avec contrat "actif")
- Audit RH compromis

Correction recommandée :
Supprimer les boutons désactiver/réactiver de la liste employés et de la fiche détail.
Les remplacer par un guidage vers le workflow de rupture de contrat dans l'onglet Carrière.
La désactivation d'un employé ne doit se produire QUE comme conséquence du cascadage
automatique après validation d'une rupture de contrat.

Effort estimé : Moyen
```

### UX-EMP-002 — Chargement de 1000 employés côté client

```
ID : UX-EMP-002
Priorité : P1 🔴
Catégorie : Technique / Performance
Module : Employés
Fichiers concernés :
- src/pages/employe/EmployeList.jsx (L68-69)

Problème :
employeService.getEmployes({ size: 1000 }) charge TOUS les employés d'un coup.
Le tri, filtrage et pagination se font ensuite entièrement en mémoire côté client.

Pourquoi c'est problématique :
- À 500+ employés, le temps de chargement initial devient perceptible
- La mémoire navigateur peut souffrir avec des milliers d'objets
- Les filtres ne sont pas dans l'URL → perte de contexte au retour arrière

Impact utilisateur :
Temps de chargement initial long, impossibilité de partager un lien filtré.

Correction recommandée :
Pagination serveur avec filtres dans l'URL. Conserver le tri client comme option secondaire.

Effort estimé : Moyen
```

### UX-EMP-003 — Absence de guidage post-création

```
ID : UX-EMP-003
Priorité : P2 🟠
Catégorie : UX / Workflow
Module : Employés
Fichiers concernés :
- src/pages/employe/EmployeList.jsx (L293-306)
- src/pages/employe/EmployeFicheDetailPage.jsx (L314-342)

Problème :
Après création d'un employé, la redirection vers ?tab=carriere&action=new_contract
est bien implémentée, et un bandeau d'orientation s'affiche. Cependant :
- Le bandeau ne détaille pas les étapes restantes (contrat → documents → pointage)
- Pas de stepper visuel montrant la progression de l'onboarding
- L'utilisateur ne sait pas quels documents obligatoires manquent

Correction recommandée :
Ajouter un ProgressStepper dans l'en-tête de la fiche détail pour les nouveaux collaborateurs :
1. ✅ Fiche créée
2. ⏳ Contrat à configurer
3. ○ Documents obligatoires
4. ○ Intégration pointage

Wireframe :
┌──────────────────────────────────────────────────────────────┐
│ ✅ Fiche créée → ⏳ Contrat → ○ Documents → ○ Pointage     │
└──────────────────────────────────────────────────────────────┘

Effort estimé : Faible
```

### UX-EMP-004 — Formulaire multi-step sans sauvegarde de progrès

```
ID : UX-EMP-004
Priorité : P2 🟠
Catégorie : Formulaire / UX
Module : Employés
Fichiers concernés :
- src/pages/employe/dossier/EmployeFormModal.jsx (24359B)
- src/components/employe/form/ (6 steps)

Problème :
Le formulaire d'employé est un multi-step dans une modale. Si l'utilisateur ferme
la modale accidentellement (clic extérieur, Echap, erreur réseau), TOUTES les données
saisies sont perdues. Aucun brouillon n'est sauvegardé.

Correction recommandée :
- Confirmation de fermeture si le formulaire a été modifié ("Modifications non enregistrées")
- Optionnel : sauvegarde temporaire en localStorage

Effort estimé : Faible
```

---

# 3. Module Contrats & Carrière

## 3.1 Cartographie

```
Pages :
- carriere/CarriereTab.jsx (5690B) — conteneur onglet dans fiche employé
- carriere/RecapitulatifBrouillonModal.jsx (28785B)
- carriere/b/ : EmbaucheModal (28036B), TransitionModal (14692B),
  ConfirmationEssaiModal (13581B), RenouvellementEssaiModal (17566B),
  RuptureContratModal (22641B), CertificatTravailModal (17140B),
  AttestationTravailModal (17767B)

Components :
- CarriereHeaderCard.jsx, CarriereTimeline.jsx, ContractDocument.jsx
- timeline/ : ContratActifSection, HistoriqueContratsTable,
  HistoriqueWorkflowsTable, ParcoursChronologique (33450B), TimelineBanners
- form/ : ContratFieldsSection, AffectationFieldsSection,
  PeriodeEssaiFieldsSection, TransitionTypeSection, FormSectionHeader
- modals/ : CarriereModalsManager.jsx
- supervision/ : 7 composants (KPI, Filtres, Alertes, Comparaison)
- preview/ : AvenantPreviewDocument.jsx

Hooks :
- useCarriere.js, useCarriereModals.js, useTransitionForm.js, useContractReferentiels.js

Services API :
- contratService.js (30+ méthodes)

Workflow états :
  BROUILLON → PLANIFIE → APPLIQUE
                      → ANNULE
Contrat : BROUILLON → A_SIGNER → ACTIF → SUSPENDU → CLOTURE/ROMPU
Affectation : PLANIFIE → ACTIVE → HISTORISE
```

## 3.2 Problèmes identifiés

### UX-CON-001 — Pas de blocage frontend pour embauche si contrat actif

```
ID : UX-CON-001
Priorité : P0 🔴
Catégorie : Workflow / Métier
Module : Contrats
Fichiers concernés :
- src/components/carriere/timeline/ContratActifSection.jsx (L24-62)
- src/components/carriere/modals/CarriereModalsManager.jsx

Problème :
Le backend bloque bien la création d'un brouillon d'embauche si un contrat actif existe
(RG-CON-01). Mais le FRONTEND affiche le bouton "Initier l'Embauche" uniquement quand
contratActif est null. Le problème est que la vérification de brouillonWf (L25) fait un
return null silencieux — l'utilisateur ne voit AUCUN message expliquant pourquoi il ne peut
pas créer un nouveau contrat.

Correction recommandée :
Si un brouillon existe, afficher un bandeau explicatif :
"Un brouillon de workflow est en cours de traitement. Validez-le ou annulez-le avant
d'initier un nouveau mouvement."
Avec un bouton [Voir le brouillon] pointant vers le RecapitulatifBrouillonModal.

Effort estimé : Faible
```

### UX-CON-002 — initierRupture utilise le mauvais endpoint

```
ID : UX-CON-002
Priorité : P1 🔴
Catégorie : Technique / API
Module : Contrats
Fichiers concernés :
- src/api/employe/contratService.js (L96-99)

Problème :
La méthode `initierRupture` envoie la requête vers `/employes/{id}/workflows/transition`
au lieu de `/employes/{id}/workflows/rupture`. C'est le même endpoint que `initierTransition`.
C'est un bug de copier-coller qui pourrait envoyer une rupture comme une transition.

Cause probable :
Copier-coller de la méthode `initierTransition` (L91-94) sans changer l'URL.

Correction recommandée :
```diff
- const response = await apiClient.post(`/employes/${idEmploye}/workflows/transition`, data);
+ const response = await apiClient.post(`/employes/${idEmploye}/workflows/rupture`, data);
```

Effort estimé : Trivial
```

### UX-CON-003 — Modales de contrat très longues sans scroll indicator

```
ID : UX-CON-003
Priorité : P2 🟠
Catégorie : UI / UX
Module : Contrats
Fichiers concernés :
- src/pages/employe/carriere/b/EmbaucheModal.jsx (28036B)
- src/pages/employe/carriere/b/RuptureContratModal.jsx (22641B)
- src/pages/employe/carriere/RecapitulatifBrouillonModal.jsx (28785B)

Problème :
Les modales de contrat sont très denses (embauche : contrat + affectation + essai + référentiels).
Le max-height de 75vh dans Modal.jsx crée un scroll vertical, mais l'utilisateur ne voit
aucun indicateur que du contenu se trouve en dessous (pas de gradient, pas d'ombre,
pas de scroll indicator). Le bouton de soumission dans le footer peut être invisible.

Correction recommandée :
Ajouter un indicateur d'ombre en bas du body de la modale quand le contenu est scrollable.
Alternativement, transformer les modales les plus lourdes (EmbaucheModal, TransitionModal)
en drawers pleine hauteur ou en pages dédiées.

Effort estimé : Moyen
```

### UX-CON-004 — RecapitulatifBrouillonModal : 28785B monolithique

```
ID : UX-CON-004
Priorité : P3 🟡
Catégorie : Architecture
Module : Contrats
Fichiers concernés :
- src/pages/employe/carriere/RecapitulatifBrouillonModal.jsx

Problème :
Ce fichier de ~800 lignes combine : récapitulatif, validation, annulation, correction,
comparaison avant/après. Il n'a pas été refactorisé selon l'architecture 3 couches
recommandée dans ARCHITECTURE_GUIDE.md.

Correction recommandée :
Extraire en sous-composants : RecapHeader, RecapContratSection, RecapAffectationSection,
RecapWorkflowActions, RecapComparison. Extraire la logique dans useRecapitulatifBrouillon.

Effort estimé : Moyen
```

---

# 4. Module Congés & Absences

## 4.1 Cartographie

```
Pages :
- GestionCongesPage.jsx (4321B) — Conteneur simple avec 2 onglets

Routes :
- /conges → GestionCongesPage
- /conges/* → GestionCongesPage (catch-all pour onglets URL)

Components :
- DemandeCongeModal.jsx (8923B), ValidationDemandeModal.jsx (11337B)
- DemandesTab.jsx, DemandesValidationTab.jsx (6165B), ValidationTab.jsx
- SoldesOverviewTab.jsx, SoldeCongeWidget.jsx, MouvementsSoldeTable.jsx
- ParametresCongesTab.jsx (22228B), TypeCongeModal.jsx, MotifFamilialModal.jsx
- formulaire/ : TypeCongeSection, DatesCongeSection, OptionsRHSection
- validation/ : ValidationFilters, ValidationTable, ValidationModals
- soldes/ : sous-composants soldes

Hooks :
- useDemandeCongeForm.js (11506B)
- useDemandesValidation.js (3295B)
- useSoldesOverview.js (8802B)

Services API :
- congeService.js (7757B, 20+ méthodes)

Workflow :
  Demande → EN_ATTENTE → VALIDE → (crédit PRISE dans ledger)
                       → REFUSE
                       → ANNULE (si validée → crédit ANNULATION dans ledger)
```

## 4.2 Problèmes identifiés

### UX-CNG-001 — Module congés déconnecté des fiches employés

```
ID : UX-CNG-001
Priorité : P1 🔴
Catégorie : Architecture Information / Navigation
Module : Congés
Fichiers concernés :
- src/pages/conges/GestionCongesPage.jsx
- src/components/conges/DemandesValidationTab.jsx

Problème :
Le module Congés (/conges) est une page autonome avec ses propres onglets.
Mais il n'existe aucun lien contextuel depuis la fiche employé (onglet Congés)
vers le module de gestion globale des congés, et inversement.

Quand un RH valide une demande dans /conges, il ne peut pas rapidement accéder
à la fiche de l'employé concerné.

Correction recommandée :
- Dans le tableau de validation : ajouter un lien cliquable sur le nom de l'employé
  → /employes/:id?tab=conges
- Dans l'onglet Congés de la fiche employé : ajouter un lien "Voir toutes les demandes"
  → /conges?tab=demandes

Effort estimé : Faible
```

### UX-CNG-002 — DemandeCongeModal n'utilise pas le composant Modal standard

```
ID : UX-CNG-002
Priorité : P2 🟠
Catégorie : Design System / Cohérence
Module : Congés
Fichiers concernés :
- src/components/conges/DemandeCongeModal.jsx (L50-51)
- src/components/ui/overlays/Modal.jsx

Problème :
La DemandeCongeModal implémente sa propre structure de modale (backdrop, conteneur, header)
au lieu d'utiliser le composant <Modal> du design system. Cela crée des divergences visuelles :
- Backdrop : `bg-slate-900/60` au lieu de `bg-black/60`
- Pas de `rounded-none` cohérent
- Header avec padding différent (px-6 py-4 vs px-4 py-3)
- Bouton fermer : caractère `×` au lieu du composant <X> de Lucide

Correction recommandée :
Réécrire DemandeCongeModal pour utiliser <Modal> comme wrapper.

Effort estimé : Faible
```

### UX-CNG-003 — Double extraction des demandes (code dupliqué)

```
ID : UX-CNG-003
Priorité : P3 🟡
Catégorie : Technique
Module : Congés
Fichiers concernés :
- src/pages/conges/GestionCongesPage.jsx (L39-41 et L56-58)

Problème :
Le code d'extraction de la liste de demandes depuis la réponse API est dupliqué :
lignes 39-41 et 56-58 font exactement la même chose. La variable `demandesList`
est recalculée à chaque render hors useMemo.

Correction recommandée :
Utiliser un seul useMemo pour l'extraction, supprimer la duplication.

Effort estimé : Trivial
```

---

# 5. Module Pointage & Présence

## 5.1 Cartographie

```
Pages :
- SaisieQuotidiennePage.jsx (477L / 20477B)
- AnomaliesPointagePage.jsx (26381B)
- SynthesePointagePage.jsx (46807B — PLUS GROS FICHIER DU PROJET)
- PeriodePaiePage.jsx (29379B)

Routes :
- /pointages → redirect /pointages/saisie
- /pointages/saisie, /pointages/anomalies, /pointages/synthese, /pointages/periodes

Components :
- PointageGridTable.jsx (25272B), PointageToolbar.jsx, PointageRecapModal.jsx (15595B)
- PointageGuideStepper.jsx, PointageFerieAlert.jsx, PointageAnomaliesSection.jsx
- PointageProcessBanner.jsx, PointagePeriodeBanner.jsx, PointageStatsCards.jsx
- PointageWeeklySyntheseSection.jsx, PointageTransmissionDafSection.jsx
- PointageWorkflowNav.jsx, SyntheseHebdomadaireTab.jsx (20010B)
- SyntheseMensuelleTab.jsx (18946B)

Hooks :
- usePointageSaisie.js (16702B — logique complexe de grille éditable)
- usePointageAnomalies.js, usePointageSynthese.js, usePeriodePaie.js
- useDerogationIndemnites.js, useToleranceArrivee.js

Services API :
- pointageSaisieService.js, pointageAnomalieService.js
- pointageSyntheseService.js, pointageEmployeService.js

Permissions :
- ROLE_ADMIN + ROLE_AGENT_POINTAGE
```

## 5.2 Problèmes identifiés

### UX-PTG-001 — SynthesePointagePage : fichier monolithique de 46KB

```
ID : UX-PTG-001
Priorité : P1 🔴
Catégorie : Architecture / Performance
Module : Pointage
Fichiers concernés :
- src/pages/pointage/SynthesePointagePage.jsx (46807B)

Problème :
Ce fichier est le plus gros du projet. Il contient les onglets de synthèse
hebdomadaire et mensuelle, la logique de filtrage, l'export, et le rendu
de tableaux denses. Cela viole l'architecture 3 couches recommandée.

100 employés × 5 jours × entrée/sortie = 1000 cellules rendues sans virtualisation.

Correction recommandée :
1. Extraire la logique dans useSynthesePointage
2. Extraire les tabs en composants dédiés (déjà partiellement fait)
3. Implémenter la virtualisation pour les tableaux > 50 lignes

Effort estimé : Élevé
```

### UX-PTG-002 — Pointage sans lien avec statut contrat

```
ID : UX-PTG-002
Priorité : P1 🔴
Catégorie : Workflow / Métier / Cross-module
Module : Pointage ↔ Contrats
Fichiers concernés :
- src/pages/pointage/SaisieQuotidiennePage.jsx
- src/hooks/pointage/usePointageSaisie.js

Problème :
Le module pointage affiche TOUS les employés du site sélectionné. Mais il ne vérifie pas :
- Si l'employé a un contrat ACTIF
- Si la date de pointage est dans la période du contrat
- Si l'employé est en période d'essai (et sa catégorie pour les HS)

Un employé dont le contrat est terminé peut donc être pointé.

Correction recommandée :
Le backend devrait filtrer les employés éligibles au pointage. Côté frontend,
afficher un indicateur visuel clair pour les employés sans contrat actif.

Effort estimé : Moyen
```

### UX-PTG-003 — Workflow de saisie sans stepper

```
ID : UX-PTG-003
Priorité : P2 🟠
Catégorie : UX
Module : Pointage
Fichiers concernés :
- src/components/pointage/PointageGuideStepper.jsx (4709B)

Problème :
Le composant PointageGuideStepper existe mais le workflow de pointage est complexe :
Sélection site → Date → Saisie → Vérification anomalies → Validation → Transmission DAF.
Le stepper actuel n'est pas suffisamment visible/guidant pour qu'un agent de pointage
nouveau comprenne le processus complet.

Correction recommandée :
Rendre le stepper plus proéminent avec des étapes cliquables et un indicateur de progression.

Effort estimé : Faible
```

---

# 6. Préparation future de la Paie

## 6.1 État actuel

Les fondations pour la paie sont **partiellement en place** :

| Élément | Statut | Fichiers |
|---|---|---|
| Catégorie professionnelle (A/C) | ✅ Implémenté | `RefCategorieProfessionnelle.java` |
| Mode calcul paie (MENSUEL/JOURNALIER/HORAIRE) | ✅ Implémenté | `RefModeCalculPaie.java` |
| Salaire base | ✅ Dans affectation | `EmployeAffectationCarriere.java` |
| Barème IRSA | ✅ Configurable | `BaremeIrsa.java`, `IrsaController.java` |
| Cotisations (CNaPS, sanitaire) | ✅ Par site | `SiteCotisation.java` |
| Indemnités | ✅ Configurables par site | `SiteIndemniteConfig.java`, `RefIndemnite.java` |
| Taux majoration HS | ✅ Configurables | `RefTauxMajoration.java` |
| Périodes de paie | ✅ Workflow complet | `PeriodePaieController.java` |
| Jours fériés | ✅ Calendrier configurable | `RefCalendrierFerie.java` |
| Synthèse mensuelle (heures) | ✅ Frontend | `SyntheseMensuelleTab.jsx` |
| Bulletins de paie | ❌ Non implémenté | — |

> [!TIP]
> Le SIRH est **architecturalement prêt** pour intégrer un module de paie. Les données nécessaires
> (salaire, catégorie, heures, cotisations, IRSA, indemnités) sont déjà traçables.
> Le chainon manquant est le moteur de calcul de paie et la génération de bulletins.

## 6.2 Risques d'intégration identifiés

1. **Le `paieUtils.js` frontend** (2428B) existe déjà mais semble contenir des calculs qui devraient être côté backend uniquement
2. **Le `periodePaieService.js`** (1098B) est minimal — le module de paie devra s'appuyer davantage sur les services backend
3. **Le statut de la période de paie** (EN_COURS → A_VALIDER → VALIDEE → CLOTUREE → ARCHIVEE) est bien défini dans `StatusBadge.jsx`

---

# 7. Analyse Cross-Module

## 7.1 Incohérences détectées

### Employé ↔ Contrat

```
Problème : L'employé peut être désactivé manuellement (via employeService.desactiverEmploye)
SANS passer par le workflow de rupture de contrat.

Conséquence :
- estActif = false sur l'employé
- Contrat toujours ACTIF dans contrat_travail
- L'onglet Carrière montre un contrat actif pour un employé "inactif"

Fichiers :
- EmployeList.jsx L316-342 (désactivation directe)
- EmployeFicheDetailPage.jsx L149-181 (désactivation directe)
- Contrats-RG.md RG-CON-04 (stipule le cascadage automatique via workflow)
```

### Employé ↔ Pointage

```
Problème : Le module pointage ne vérifie pas le statut du contrat.
Un employé dont le contrat est ROMPU/CLOTURE peut encore apparaître
dans la grille de saisie quotidienne s'il est dans le site sélectionné.

Fichiers :
- SaisieQuotidiennePage.jsx (utilise la liste des employés par site)
- usePointageSaisie.js (pas de filtre sur statut contrat)
```

### Contrat ↔ Congés

```
Problème : L'onglet Congés dans la fiche employé (CongesTab.jsx) ne vérifie pas
si l'employé a un contrat actif avant de permettre la création d'une demande.
Un employé sans contrat actif peut techniquement soumettre une demande de congé.

Fichiers :
- src/components/employe/tabs/CongesTab.jsx
- src/components/conges/DemandeCongeModal.jsx
```

### Pointage ↔ Paie future

```
État : La synthèse mensuelle produit des données (heures normales, HS, indemnités)
qui sont prêtes à alimenter un futur module de paie. Le workflow de période de paie
(EN_COURS → A_VALIDER → VALIDEE/GELÉE → CLOTUREE) est correctement implémenté
et vérouille les pointages une fois la période validée.
→ C'est un point POSITIF qui facilitera l'intégration de la paie.
```

## 7.2 Matrice de cohérence visuelle

| Aspect | Employés | Contrats | Congés | Pointage |
|---|---|---|---|---|
| **PageHeader** | ✅ Utilisé | ❌ N/A (sous-onglet) | ✅ Utilisé | ❌ Non utilisé |
| **Breadcrumb** | ✅ Liste + Fiche | ✅ Via fiche employé | ✅ Partiel | ❌ Absent |
| **Tabs** | ✅ Underline | ✅ Via fiche | ✅ Underline | ⚠️ WorkflowNav propre |
| **Table** | ✅ \<Table\> UI | ✅ \<Table\> UI | ✅ \<Table\> UI | ⚠️ Table custom (PointageGridTable) |
| **Pagination** | ✅ \<Pagination\> | — | ✅ Via hook | ✅ Custom |
| **Empty State** | ✅ \<EmptyState\> | ✅ Custom dans ContratActifSection | ✅ Partiel | ⚠️ Incohérent |
| **Loading** | ✅ \<SkeletonTable\> | ✅ | ✅ | ✅ |
| **StatusBadge** | ✅ | ✅ | ✅ | ✅ |
| **Confirmation** | ✅ \<ConfirmDialog\> | ✅ RecapitulatifBrouillonModal | ✅ ValidationModals | ⚠️ ConfirmDialog |
| **Toast** | ✅ | ✅ | ✅ | ✅ |
| **Modal** | ✅ \<Modal\> | ⚠️ Modal custom dans certaines | ❌ Modal custom | ✅ \<Modal\> |

---

# 8. Navigation

## 8.1 Structure actuelle

```
Sidebar (Backend-driven via /api/menus)
├── Dashboard           → /dashboard
├── GESTION RH
│   ├── Employés        → /employes
│   ├── Carrières       → /carrieres
├── TEMPS & PRÉSENCE
│   ├── Saisie          → /pointages/saisie
│   ├── Anomalies       → /pointages/anomalies
│   ├── Synthèse        → /pointages/synthese
│   ├── Périodes         → /pointages/periodes
├── CONGÉS
│   ├── Congés          → /conges
├── ADMINISTRATION
│   ├── Sécurité        → /admin/securite
│   ├── Organisation    → /admin/organisation
│   ├── Référentiels RH → /admin/referentiels-rh
│   ├── Paramètres      → /admin/parametres
```

## 8.2 Problèmes de navigation

### UX-NAV-001 — Breadcrumbs incohérents

```
ID : UX-NAV-001
Priorité : P2 🟠
Module : Navigation

Pages AVEC breadcrumb :
- EmployeList ✅ (Tableau de bord > Collaborateurs)
- EmployeFicheDetailPage ✅ (Tableau de bord > Collaborateurs > Nom)
- GestionCongesPage ✅ (Tableau de bord > Congés & Absences)

Pages SANS breadcrumb :
- SaisieQuotidiennePage ❌
- AnomaliesPointagePage ❌
- SynthesePointagePage ❌
- PeriodePaiePage ❌
- CarrieresSupervisionPage ❌ (à vérifier)
- Toutes les pages admin ❌

Correction recommandée :
Standardiser les breadcrumbs via le composant PageHeader (qui intègre déjà <Breadcrumb>)
dans TOUTES les pages.
```

### UX-NAV-002 — Pas de navigation Employé ↔ Contrat rapide

```
ID : UX-NAV-002
Priorité : P2 🟠
Module : Navigation

Problème :
Depuis le module /carrieres (supervision globale), on peut voir la liste des contrats.
Mais cliquer sur un contrat ne redirige pas vers la fiche de l'employé concerné.
La navigation est uni-directionnelle :
  Fiche Employé → Onglet Carrière → Contrats ✅
  Module Carrières → Fiche Employé ❌ (pas de lien)

Correction recommandée :
Rendre le nom de l'employé cliquable dans les tables de supervision
(→ /employes/:id?tab=carriere).
```

### UX-NAV-003 — Topbar affiche un titre dynamique basé sur le menu

```
Observation positive :
Le Topbar.jsx utilise `findMenuTitleByHref(menu, pathname)` pour afficher le titre
de la page courante. C'est un bon pattern de navigation contextuelle.

Problème mineur :
Le breadcrumb dans PageHeader utilise "Tableau de bord" comme premier élément
avec href="/". Or la route "/" est la page de Login, pas le Dashboard (/dashboard).
Cliquer sur "Tableau de bord" dans le breadcrumb d'un utilisateur connecté
le redirigerait vers la page de login.

Correction :
Changer href: '/' en href: '/dashboard' dans tous les breadcrumbs.
```

---

# 9. Design System

## 9.1 Tokens actuels (index.css)

| Catégorie | Tokens définis | État |
|---|---|---|
| **Couleurs brand** | brand-50 à brand-950, brand-hover | ✅ Complet |
| **Couleurs sémantiques** | success, danger, warning, info | ✅ Complet |
| **Neutres** | ink-50 à ink-950 | ✅ Complet |
| **Surfaces** | bg, bg-subtle, surface, surface-hover | ✅ Complet |
| **Sidebar** | 8 tokens dédiés | ✅ Complet |
| **Typographie** | font-sans, font-mono | ✅ |
| **Rayons** | none, sm (2px), md (4px), full | ✅ Angular design |
| **Ombres** | sm, md, lg | ✅ |
| **Transitions** | ease-default, duration-fast/base/slow | ✅ |
| **Dark mode** | Variante complète .dark | ✅ |

## 9.2 Problèmes du Design System

### UX-DSN-001 — Couleurs hardcodées vs tokens

```
ID : UX-DSN-001
Priorité : P2 🟠
Catégorie : Design System
Module : Global

Problème :
Malgré un système de tokens complet, de nombreux composants utilisent des classes
Tailwind hardcodées au lieu des tokens sémantiques :

Badge.jsx :
- `bg-emerald-500/15` au lieu de var(--color-success-*)
- `bg-amber-500/15` au lieu de var(--color-warning-*)
- `bg-red-500/15` au lieu de var(--color-danger-*)
- `bg-slate-100 text-slate-700` au lieu de var(--color-ink-*)

ContratActifSection.jsx :
- `bg-white dark:bg-ink-900` → devrait être var(--color-surface)
- `border-slate-200/90` → devrait être var(--color-border-subtle)
- `text-slate-900 dark:text-white` → devrait être var(--color-fg)
- `text-slate-400` → devrait être var(--color-fg-subtle)

GestionCongesPage.jsx :
- `border-slate-200 dark:border-ink-800` → devrait être var(--color-border-subtle)

Conséquence :
Le dark mode peut être incohérent car les classes `dark:` doivent être ajoutées
manuellement au lieu d'utiliser les variables CSS qui s'adaptent automatiquement.

Correction recommandée :
Remplacer systématiquement les classes Tailwind de couleur par les variables CSS custom.
Créer un lint rule ou un guide de migration.
```

### UX-DSN-002 — Badge variant "brand" utilisé mais non défini

```
ID : UX-DSN-002
Priorité : P3 🟡
Catégorie : Design System
Fichiers :
- src/components/employe/EmployeTable.jsx (L188) utilise variant="brand"
- src/components/ui/data-display/Badge.jsx — pas de variant "brand" défini

Problème :
EmployeTable utilise <Badge variant="brand"> mais le composant Badge ne définit pas
cette variante. Le fallback sera `variants.default`.

Correction : Ajouter la variante "brand" au Badge ou utiliser variant="primary".
```

---

# 10. Formulaires

## 10.1 Analyse globale

| Formulaire | Validation inline | Anti-double submit | Préservation données | Confirmation fermeture |
|---|---|---|---|---|
| EmployeFormModal | ❌ Non | ✅ Oui (submitting) | ❌ Non | ❌ Non |
| EmbaucheModal | ❌ Non | ✅ Oui | ❌ Non | ❌ Non |
| TransitionModal | ❌ Non | ✅ Oui | ❌ Non | ❌ Non |
| RuptureContratModal | ❌ Non | ✅ Oui | ❌ Non | ❌ Non |
| DemandeCongeModal | ⚠️ Partiel (fieldErrors) | ✅ Oui | ❌ Non | ❌ Non |
| PointageGridTable | N/A (édition inline) | ✅ Oui (saving) | ✅ Oui (grille) | ⚠️ Partiel |

### UX-FRM-001 — Validation inline quasi inexistante

```
ID : UX-FRM-001
Priorité : P2 🟠
Catégorie : Formulaire / UX
Module : Global

Problème :
Le rapport_bonnes_pratiques_ui_ux.md stipule (§10.1) : "Éviter d'attendre la soumission
pour révéler toutes les erreurs." Or AUCUN formulaire du SIRH ne fait de validation inline
en temps réel. Les erreurs ne sont affichées qu'après soumission, et souvent comme un
message global plutôt que proche du champ concerné.

L'exception partielle est DemandeCongeModal qui gère fieldErrors par champ, mais la
validation se fait au moment du submit, pas en temps réel.

Correction recommandée :
Implémenter la validation inline avec debounce pour les champs critiques :
- CIN (vérification unicité)
- Dates (cohérence début < fin)
- Matricule (vérification unicité)
- Salaire (format numérique)
- Email (format)
```

---

# 11. Tables

## 11.1 Composant Table du Design System

Le composant `Table.jsx` est **bien conçu** avec :
- ✅ Thead, Tbody, Tr, Td, Th standardisés
- ✅ Tri sur colonnes (sortable, sorted, onSort)
- ✅ Sélection multiple (ThCheckbox, TdCheckbox, TableSelectionBar)
- ✅ Zebra striping optionnel
- ✅ Empty state intégré (TableEmpty)
- ✅ Scroll vertical avec sticky header (maxHeight + sticky)
- ✅ Divided mode pour les colonnes

## 11.2 Problèmes

### UX-TBL-001 — PointageGridTable n'utilise pas le composant Table

```
ID : UX-TBL-001
Priorité : P2 🟠
Module : Pointage
Fichiers : src/components/pointage/PointageGridTable.jsx (25272B)

Problème :
La grille de pointage est un composant entièrement custom (25KB). Elle n'utilise pas
le composant <Table> du design system. Cela crée des divergences visuelles et rend
la maintenance plus difficile.

Raison probable : La grille a des besoins spéciaux (édition inline, colonnes dynamiques
pour les indemnités). Mais la structure de base (thead, tbody, tr, td) pourrait
utiliser les composants du DS.
```

---

# 12. États UI

## 12.1 Couverture par module

| État | Employés | Contrats | Congés | Pointage |
|---|---|---|---|---|
| **Loading** | ✅ SkeletonTable | ✅ Loading | ✅ SkeletonTable | ✅ Loading |
| **Empty** | ✅ EmptyState | ✅ Custom | ⚠️ Partiel | ⚠️ Partiel |
| **Error** | ✅ Alert | ✅ Alert | ✅ Alert | ✅ Alert |
| **Success** | ✅ Toast | ✅ Toast | ✅ Toast | ✅ Toast |
| **Submitting** | ✅ Button loading | ✅ Button loading | ✅ Button loading | ✅ Button loading |
| **No results** | ✅ EmptyState | ⚠️ Partiel | ⚠️ Partiel | ⚠️ Partiel |
| **Unauthorized** | ✅ ProtectedRoute | ✅ | ✅ | ✅ |

> [!NOTE]
> Les états Loading, Error et Success sont **correctement gérés** dans l'ensemble du projet.
> Le principal manque est dans les **empty states contextuels** : certains modules affichent
> simplement "Aucune donnée" sans guider l'utilisateur vers l'action suivante.

---

# 13. Accessibilité

### UX-ACC-001 — Lacunes d'accessibilité

```
ID : UX-ACC-001
Priorité : P3 🟡
Catégorie : Accessibilité
Module : Global

Points POSITIFS :
- ✅ Skip-link implémenté (Layout.jsx L44)
- ✅ Focus visible avec outline personnalisé (index.css :focus-visible)
- ✅ prefers-reduced-motion respecté (index.css @media)
- ✅ aria-label sur le bouton hamburger (Topbar.jsx)
- ✅ aria-disabled sur Button.jsx
- ✅ aria-sort sur les en-têtes de colonne triables (Table.jsx)

Points NÉGATIFS :
- ❌ Les icônes seules n'ont pas de tooltip ni d'aria-label (boutons action dans EmployeTable)
- ❌ Le dropdown utilisateur dans Topbar.jsx n'a pas de gestion focus-trap ni Escape
- ❌ Les modales custom (DemandeCongeModal) n'ont pas de focus-trap
- ❌ Le composant Modal.jsx ne gère pas le focus-trap (seul Escape est géré)
- ❌ Les Select.jsx natifs n'ont pas tous de labels explicites
- ❌ Les badges de statut utilisent la couleur comme seul indicateur (dot coloré)
- ❌ Navigation clavier dans les tableaux : impossible de naviguer entre cellules au clavier
- ❌ Les erreurs de formulaire ne sont pas annoncées via aria-live

Corrections prioritaires :
1. Focus-trap dans toutes les modales
2. aria-label sur les boutons icon-only
3. aria-live="polite" sur les zones d'erreur des formulaires
4. Tooltips sur les actions de tableau
```

---

# 14. Responsive

### UX-RSP-001 — Tables non optimisées pour mobile

```
ID : UX-RSP-001
Priorité : P3 🟡
Module : Global

Problème :
Les tables (EmployeTable, PointageGridTable, SynthèseMensuelleTab) ont des min-width
par colonne (ex: min-w-[220px], min-w-[200px]) qui les rendent très larges.
Sur mobile, le scroll horizontal existe (overflow-x-auto) mais :
- Les colonnes d'action ne sont pas sticky
- Pas de transformation responsive (table → cards)
- La grille de pointage est pratiquement inutilisable sur tablette

Points positifs :
- ✅ La sidebar est responsive (mobile overlay + collapsed desktop)
- ✅ Le layout flex est correctement structuré
- ✅ Les PageHeader sont flex-wrap responsive

Correction recommandée :
Pour les tables critiques (Employés, Pointage) :
- Sticky la première colonne (identifiant) et la dernière (actions)
- Ajouter un mode "card" pour mobile < 768px
```

---

# 15. Bugs et Incohérences Techniques ayant un Impact UX

### UX-BUG-001 — initierRupture utilise le mauvais endpoint (déjà décrit comme UX-CON-002)

### UX-BUG-002 — Badge variant "brand" non défini (déjà décrit comme UX-DSN-002)

### UX-BUG-003 — Breadcrumb href "/" pointe vers Login

```
ID : UX-BUG-003
Priorité : P2 🟠
Fichiers :
- src/pages/employe/EmployeList.jsx (L372) : { label: 'Tableau de bord', href: '/' }
- src/pages/conges/GestionCongesPage.jsx (L85)

Problème : href '/' est la route de Login. Devrait être '/dashboard'.
```

### UX-BUG-004 — Topbar dropdown sans click-outside handler

```
ID : UX-BUG-004
Priorité : P2 🟠
Fichiers : src/components/layout/Topbar.jsx (L42-77)

Problème : Le dropdown profil utilisateur s'ouvre/ferme uniquement par clic sur le bouton.
Il ne se ferme PAS quand on clique ailleurs sur la page.
Pas de focus-trap, pas de fermeture sur Escape.

Correction : Ajouter useEffect avec click-outside listener et keydown Escape.
```

---

# 16. Standardisation UI/UX Proposée

## 16.1 Navigation

| Élément | Standard proposé |
|---|---|
| **Sidebar** | Backend-driven, collapsible, sections catégorisées ✅ (déjà en place) |
| **Breadcrumb** | Obligatoire sur TOUTE page, premier item = "Dashboard" avec href="/dashboard" |
| **Tabs** | Variant "underline" par défaut, variant "pills" pour les sous-navigation denses |
| **Retour** | Breadcrumb priorisé. Bouton "Retour" uniquement si pas de breadcrumb |

## 16.2 Layout

| Élément | Standard |
|---|---|
| **Largeur max** | `max-w-7xl 2xl:max-w-[1600px]` |
| **Padding main** | `p-3.5 sm:p-4.5` |
| **Spacing vertical** | `space-y-3.5` entre sections principales |

## 16.3 Tables

| Élément | Standard |
|---|---|
| **Composant** | Toujours utiliser `<Table>` du DS |
| **Empty state** | `<EmptyState>` ou `<TableEmpty>` avec action suggérée |
| **Loading** | `<SkeletonTable>` |
| **Tri** | Via props `sortable`/`sorted`/`onSort` du `<Th>` |
| **Pagination** | `<Pagination>` avec choix de pageSize |
| **Densité** | `text-xs` pour les cellules, `text-[10px]` pour les en-têtes |

## 16.4 Formulaires

| Élément | Standard |
|---|---|
| **Wrapper** | `<Modal>` du DS pour les formulaires simples, page dédiée pour les complexes |
| **Validation** | Inline en temps réel + backend. Erreurs au plus proche du champ |
| **Anti-double submit** | `submitting` state + `Button loading` ✅ (déjà normé dans runtime.md) |
| **Labels** | Toujours visibles, jamais uniquement placeholder |
| **Groupes** | Sections avec `<FormSectionHeader>` |
| **Confirmation fermeture** | Obligatoire si formulaire modifié |

## 16.5 Statuts — Système uniforme

| Code | Label | Couleur (Badge variant) | Dot | Usage |
|---|---|---|---|---|
| `BROUILLON` | Brouillon | warning | ● | Workflows, contrats en saisie |
| `EN_ATTENTE` | En attente | warning | ● | Demandes de congé |
| `PLANIFIE` | Planifié | info | ● | Workflow validé, date future |
| `A_SIGNER` | À signer | info | ● | Contrat en attente de signature |
| `ACTIF` / `ACTIVE` | Actif | success | ● | Contrat en cours, affectation courante |
| `EN_COURS` | En cours | primary | ● | Période de paie active |
| `SUSPENDU` | Suspendu | warning | ● | Contrat suspendu |
| `VALIDE` | Validé | success | ● | Demande de congé approuvée |
| `REFUSE` | Refusé | danger | ○ | Demande refusée |
| `CLOTURE` | Clôturé | secondary | ○ | Contrat terminé normalement |
| `ROMPU` | Rompu | danger | ○ | Contrat résilié |
| `ANNULE` | Annulé | secondary | ○ | Workflow/demande annulé |
| `HISTORISE` | Historisé | secondary | ○ | Affectation passée |
| `ARCHIVE` | Archivé | default | ○ | Données archivées |

> Le composant `StatusBadge.jsx` implémente **déjà** ce mapping. ✅

## 16.6 Boutons

| Variant | Usage | Exemple |
|---|---|---|
| **primary** | Action principale de la page/modale | "Enregistrer", "Valider" |
| **secondary** | Action secondaire importante | "Exporter", "Imprimer" |
| **outline** | Action neutre | "Retour", "Annuler" |
| **ghost** | Action tertiaire, icônes | "Actualiser", filtres |
| **danger** | Action destructive | "Supprimer", "Désactiver" |
| **success** | Confirmation positive | "Valider et Appliquer" |

## 16.7 Dialogs / Drawers / Pages

| Type | Quand l'utiliser |
|---|---|
| **Dialog (Modal)** | Confirmation, formulaire court (< 6 champs), action rapide |
| **Drawer (SlideOver)** | Détail d'un élément, formulaire moyen, aperçu |
| **Page dédiée** | Formulaire complexe (embauche, transition), multi-step > 3 étapes |
| **Popover** | Info contextuelle, filtre rapide, menu d'actions |

## 16.8 Notifications

| Type | Composant | Durée | Usage |
|---|---|---|---|
| Success | Toast | 3s auto-close | Après create/update/delete réussi |
| Error | Alert (inline) | Persistant | Erreur de soumission, erreur API |
| Warning | Alert (inline) | Persistant | Avertissement métier |
| Info | Alert ou Toast | Variable | Information contextuelle |

---

# 17. Wireframes

## 17.1 Fiche employé — Stepper d'onboarding

```
┌─────────────────────────────────────────────────────────────────────┐
│ ← Collaborateurs                                     [✎ Modifier] │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  [Photo]  Jean DUPONT                            Badge: ● ACTIF    │
│           Matricule: GLX-2026-0042                                  │
│           Agent de Sécurité • Site Tana 1                           │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ ✅ Fiche ─── ✅ Contrat ─── ⏳ Documents ─── ○ Pointage    │   │
│  │   créée        actif       2/5 obligatoires                 │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                     │
│ [Personnel] [Documents(3)] [Famille] [Carrière] [Congés] [Pointage]│
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  Contenu de l'onglet actif...                                       │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

## 17.2 Module Congés — Lien contextuel vers employé

```
┌──────────────────────────────────────────────────────────────────┐
│ Demandes & Validations                           [+ Nouvelle]     │
├────────┬──────────┬──────────┬─────────┬──────────┬──────────────┤
│ Employé│ Type     │ Période  │ Jours   │ Statut   │ Actions      │
├────────┼──────────┼──────────┼─────────┼──────────┼──────────────┤
│ [Jean  │ Annuel   │ 01/09 →  │ 3       │ ● En     │ ✓  ✗  •••   │
│ Dupont]│          │ 03/09    │         │ attente  │              │
│  ↑     │          │          │         │          │              │
│  Lien  │          │          │         │          │              │
│  vers  │          │          │         │          │              │
│  fiche │          │          │         │          │              │
└────────┴──────────┴──────────┴─────────┴──────────┴──────────────┘
```

---

# 18. Workflows Recommandés

## 18.1 Workflow d'Onboarding complet

```
                ┌─────────────────┐
                │  Créer Employé  │
                │ (EmployeFormModal)│
                └────────┬────────┘
                         ↓
                ┌─────────────────┐
                │ Fiche créée     │
                │ estActif=false  │
                └────────┬────────┘
                         ↓
                ┌─────────────────┐
                │ Initier Embauche│
                │ (EmbaucheModal) │
                │ Brouillon créé  │
                └────────┬────────┘
                         ↓
                ┌─────────────────┐
                │ Validation DAF  │
                │ (Récapitulatif) │
                └────────┬────────┘
                    ↓              ↓
            ┌──────────┐   ┌──────────┐
            │ Validé   │   │ Annulé   │
            │ Contrat  │   │ Retour   │
            │ ACTIF    │   │ brouillon│
            └────┬─────┘   └──────────┘
                 ↓
            ┌──────────┐
            │ Documents│
            │ RH       │
            │ (upload) │
            └────┬─────┘
                 ↓
            ┌──────────┐
            │ Employé  │
            │ opéra-   │
            │ tionnel  │
            └──────────┘
```

## 18.2 Workflow de Rupture (correctif UX-EMP-001)

```
                ┌─────────────────┐
                │ Onglet Carrière │
                │ Employé actif   │
                └────────┬────────┘
                         ↓
                ┌─────────────────┐
                │  Initier        │
                │  Rupture        │
                │ (RuptureModal)  │
                └────────┬────────┘
                         ↓
                ┌─────────────────┐
                │ Brouillon       │
                │ (motif, dates)  │
                └────────┬────────┘
                         ↓
                ┌─────────────────┐
                │ Validation DAF  │
                └────────┬────────┘
                    ↓              ↓
            ┌──────────┐   ┌──────────┐
            │ Appliqué │   │ Annulé   │
            │ immédiat │   └──────────┘
            │ OU       │
            │ Planifié │
            └────┬─────┘
                 ↓
            ┌──────────┐
            │ Contrat  │
            │ ROMPU    │
            │ Cascadage│
            │ estActif │
            │ = false  │
            └────┬─────┘
                 ↓
            ┌──────────┐
            │Certificat│
            │ de       │
            │ travail  │
            └──────────┘
```

---

# 19. Plan de Correction P0/P1/P2/P3

## 🔴 P0 — Critique (Bloquer/prioriser immédiatement)

| ID | Module | Modification | Fichiers | Effort |
|---|---|---|---|---|
| UX-EMP-001 | Employés | Supprimer désactivation/réactivation directe, rediriger vers workflow rupture | EmployeList, EmployeFicheDetailPage | Moyen |
| UX-CON-001 | Contrats | Afficher message explicatif si brouillon bloque la création | ContratActifSection | Faible |

## 🔴 P1 — Important (Sprint suivant)

| ID | Module | Modification | Fichiers | Effort |
|---|---|---|---|---|
| UX-CON-002 | Contrats | Corriger endpoint initierRupture | contratService.js L96-99 | Trivial |
| UX-PTG-001 | Pointage | Refactoriser SynthesePointagePage (46KB) | SynthesePointagePage | Élevé |
| UX-EMP-002 | Employés | Pagination serveur pour la liste employés | EmployeList, employeService | Moyen |
| UX-CNG-001 | Congés | Liens contextuels Congés ↔ Fiche employé | DemandesValidationTab, CongesTab | Faible |
| UX-PTG-002 | Pointage | Filtrer employés par statut contrat actif | usePointageSaisie, backend | Moyen |

## 🟠 P2 — Amélioration importante

| ID | Module | Modification | Fichiers | Effort |
|---|---|---|---|---|
| UX-NAV-001 | Navigation | Ajouter breadcrumbs à toutes les pages | Pages pointage, admin | Faible |
| UX-FRM-001 | Formulaires | Validation inline sur champs critiques | Tous les formulaires | Moyen |
| UX-DSN-001 | Design System | Remplacer couleurs hardcodées par tokens | Badge, Contrat, Congés composants | Moyen |
| UX-EMP-003 | Employés | Stepper d'onboarding post-création | EmployeFicheDetailPage | Faible |
| UX-EMP-004 | Employés | Confirmation de fermeture formulaire modifié | EmployeFormModal, toutes modales | Faible |
| UX-CNG-002 | Congés | Utiliser `<Modal>` standard dans DemandeCongeModal | DemandeCongeModal | Faible |
| UX-CON-003 | Contrats | Indicateur de scroll dans les modales longues | Modal.jsx | Faible |
| UX-BUG-003 | Navigation | Corriger breadcrumb href "/" → "/dashboard" | Tous les breadcrumbs | Trivial |
| UX-BUG-004 | Layout | Click-outside handler pour dropdown Topbar | Topbar.jsx | Faible |
| UX-NAV-002 | Navigation | Liens Supervision Carrière → Fiche Employé | CarrieresSupervisionPage | Faible |
| UX-TBL-001 | Pointage | Unifier PointageGridTable avec composant Table DS | PointageGridTable | Élevé |
| UX-PTG-003 | Pointage | Améliorer stepper de workflow pointage | PointageGuideStepper | Faible |

## 🟡 P3 — Mineur / Confort

| ID | Module | Modification | Fichiers | Effort |
|---|---|---|---|---|
| UX-ACC-001 | Accessibilité | Focus-trap modales, aria-labels, aria-live | Modal, Topbar, formulaires | Moyen |
| UX-RSP-001 | Responsive | Sticky colonnes table, mode card mobile | EmployeTable, PointageGridTable | Moyen |
| UX-DSN-002 | Design System | Ajouter variant "brand" au Badge | Badge.jsx | Trivial |
| UX-CNG-003 | Congés | Supprimer duplication extraction demandes | GestionCongesPage | Trivial |
| UX-CON-004 | Contrats | Refactoriser RecapitulatifBrouillonModal | RecapitulatifBrouillonModal | Moyen |

---

# 20. Conclusion

## Forces du SIRH GALAXAM

1. **Architecture workflow solide** : Le système BROUILLON → PLANIFIE → APPLIQUE avec circuit d'approbation DAF est mature et bien implémenté côté backend
2. **Design System cohérent** : Les tokens CSS, le composant Table, Badge, StatusBadge, Modal forment une base solide
3. **Backend-driven decisions** : Les booléens de décision (isActif, isBrouillon, etc.) sont calculés côté serveur, évitant les comparaisons de chaînes côté frontend
4. **Gestion JWT/Auth robuste** : Refresh proactif, lock, retry, error normalization
5. **Code splitting effectif** : Lazy loading systématique des pages et composants lourds
6. **Performances mesurées et optimisées** : De 109 requêtes à 16 pour le dashboard

## Faiblesses principales

1. **Incohérence désactivation employé / workflow contrat** : C'est le problème P0 le plus critique
2. **Fichiers monolithiques** : SynthesePointagePage (46KB), RecapitulatifBrouillonModal (28KB), EmbaucheModal (28KB) violent l'architecture 3 couches documentée
3. **Validation frontend insuffisante** : Les formulaires comptent trop sur le backend pour la validation
4. **Navigation cross-module faible** : Les modules fonctionnent comme des silos avec peu de liens contextuels
5. **Accessibilité** : Score de 5/10, beaucoup de travail reste à faire

## Recommandation stratégique

> Le SIRH GALAXAM est **fonctionnellement avancé** avec une architecture backend solide.
> Les améliorations prioritaires ne sont PAS des refontes visuelles mais des **corrections
> de cohérence métier** (P0) et de **guidage utilisateur** (P1-P2).
>
> Le design system existant est une bonne base. Il faut l'**exploiter plus systématiquement**
> plutôt que de le contourner avec des styles hardcodés.
>
> Avant d'ajouter le module Paie, corriger les P0 et P1 pour garantir l'intégrité
> des données (statut employé ↔ contrat ↔ pointage).
