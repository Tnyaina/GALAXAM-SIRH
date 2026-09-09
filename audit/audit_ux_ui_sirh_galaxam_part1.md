# AUDIT UX/UI & REFONTE WORKFLOWS — SIRH GALAXAM
**Senior Product Designer / UX Designer / Business Analyst SIRH**
**Date : Septembre 2026**

---

## SOMMAIRE

1. Compréhension du Projet & Contexte Métier
2. Utilisateurs & Profils
3. Architecture d'Information Actuelle
4. Analyse des Modules & Workflows Actuels
5. Critique Page par Page
6. Analyse des Parcours Utilisateur
7. Workflows Cibles Complets
8. Workflows Cross-Module
9. Architecture Cible
10. Design System — Audit & Recommandations
11. Wireframes
12. Score Global & Rapport Final
13. Roadmap de Refonte
14. Backlog d'Implémentation

---

# 1. COMPRÉHENSION DU PROJET & CONTEXTE MÉTIER

## 1.1 Contexte

GALAXAM LOGISTICS est une entreprise malgache multi-filiales (GALAXAM, Vina sy Asa, LMD) qui gère son personnel via un SIRH développé sur mesure pour remplacer une gestion Excel dispersée.

**Contraintes spécifiques :**
- Cadre légal malagasy strict (Code du Travail, CGI, CNaPS, OSTIE, OMSI)
- Multi-sites avec règles différenciées (TMM Tamatave / TNR Antananarivo)
- 3 catégories de personnel : Salariés permanents (IRSA), Consultants 60+ (ISI 5%), Temporaires (ISI 5% horaire)
- 2 profils utilisateurs uniquement : DAF/Admin et Agent de Pointage

## 1.2 Périmètre Fonctionnel

9 modules backend traduits en ~7 zones frontend : Authentification, Dashboard, Employés, Carrières, Congés, Pointage, Paie, Paramètres, Système.

## 1.3 Solidité Technique

Le backend est techniquement excellent : 328 tests à 100%, Domain Engines Java purs, règles légales correctement encapsulées. **La dette est UX, pas technique.**

---

# 2. UTILISATEURS & PROFILS

## 2.1 Profil 1 — DAF / Administrateur

| Dimension | Description |
|---|---|
| **Qui** | Gérante / Responsable RH (1 personne) |
| **Fréquence** | Quotidienne + Mensuelle intensive (clôture paie) |
| **Tâches** | Valider congés, arbitrer pointage, calculer paie, générer bulletins, exporter SAFI/EDI, gérer employés/contrats |
| **Niveau technique** | Intermédiaire — utilise Excel, pas développeuse |
| **Criticité** | HAUTE — toute erreur impacte la paie légale |

## 2.2 Profil 2 — Agent de Pointage

| Dimension | Description |
|---|---|
| **Qui** | Agent de saisie RH (1-2 personnes) |
| **Fréquence** | Quotidienne (saisie horaires) |
| **Tâches** | Saisir horaires, corriger anomalies, transmettre à la DAF |
| **Niveau technique** | Basique — remplace des fichiers Excel |
| **Criticité** | HAUTE — données sources pour la paie |

> **PROBLÈME IDENTIFIÉ :** Les 2 profils ont des workflows fondamentalement différents mais voient la même structure de navigation avec quelques menus masqués. Ce n'est pas suffisant.

---

# 3. ARCHITECTURE D'INFORMATION ACTUELLE

## 3.1 Navigation actuelle (reconstituée)

```text
Sidebar dynamique (menus depuis BDD)
├── Tableau de bord
├── Employés
│   ├── Liste des employés → /employes
│   └── Supervision des carrières → /employes/carrieres
├── Congés & Absences → /conges
│   ├── [Tab] Soldes & Planning prévisionnel
│   └── [Tab] Demandes & Validations
├── Pointage (GestionPointagesPage)
│   ├── [Tab] Saisie Quotidienne
│   ├── [Tab] Suivi de Période
│   ├── [Tab] Anomalies & Retards (DAF uniquement)
│   └── [Tab] Synthèse & Clôture
├── Paie
│   ├── Périodes de Paie → /paie
│   ├── Validation DAF Pointage → /paie?tab=validation   ← MAUVAISE PLACE
│   └── Gestion Paie → /paie/gestion
└── Paramètres
    ├── Organisation / Référentiels / Sécurité / Modules
```

## 3.2 Problèmes structurels identifiés

| # | Problème | Criticité |
|---|---|---|
| 1 | Validation DAF pointage dans /paie au lieu de /pointage | CRITIQUE |
| 2 | PointageTab.jsx (48 Ko) dans la fiche employé — module entier dupliqué | CRITIQUE |
| 3 | Workflow Pointage : 4 onglets libres pour un processus séquentiel | ÉLEVÉ |
| 4 | CongesTab.jsx (14 Ko) dans la fiche employé — doublon | MOYEN |
| 5 | Supervision carrières séparée de la liste employés | FAIBLE |
| 6 | Menu dynamique BDD — si mal configuré, navigation cassée | MOYEN |

---

# 4. ANALYSE DES MODULES & WORKFLOWS ACTUELS

## 4.1 Module Pointage — Workflow actuel reconstruit

```text
Agent de pointage
    ↓
[Tab: Saisie Quotidienne] — 1 site, 1 jour à la fois
    ↓ (navigation manuelle — aucun guidage)
[Tab: Suivi de Période] — vue semaines, calcul 1-clic
    ↓ (navigation manuelle — aucun guidage)
[Tab: Anomalies & Retards] — visible DAF uniquement
    ↓ (navigation manuelle — aucun guidage)
[Tab: Synthèse & Clôture] — transmission DAF
    ↓ (CHANGEMENT DE MODULE — rupture de contexte)
[/paie?tab=validation] — validation DAF
    ↓
[/paie] — calcul paie
```

**Problèmes critiques :**
1. Navigation libre dans un workflow obligatoirement séquentiel
2. L'utilisateur ne sait pas où il en est dans le cycle
3. Changement de module entre Pointage et Paie sans continuité
4. PointageTab.jsx dans la fiche employé = duplication de 48 Ko
5. Anomalies non visibles pour l'Agent (onglet DAF) mais il doit quand même les corriger

## 4.2 Module Paie — Structure actuelle

```text
GestionPaiePage.jsx (4 Ko — coquille vide)
    → délègue à PeriodePaiePage.jsx (40 Ko)
       → contient PointageValidationDafTab (38 Ko) — MAUVAISE PLACE
```

## 4.3 Module Employés — Problèmes

```text
EmployeList → EmployeFicheDetailPage (6-7 onglets)
   ├── Fiche Personnelle     ← pertinent
   ├── Documents RH          ← pertinent
   ├── Famille & Contacts    ← pertinent
   ├── Carrière              ← pertinent
   ├── Congés & Absences     ← DOUBLON (module /conges existe)
   └── Pointages & Présence  ← DOUBLON (module /pointage existe, 48 Ko!)
```

---

# 5. CRITIQUE PAGE PAR PAGE

---

## [TABLEAU DE BORD] Dashboard.jsx

**Objectif :** Vue d'ensemble RH avec KPIs, alertes et accès rapides.

**Problèmes :**
1. Grille 2 colonnes rigide — se déforme si peu de permissions (Agent)
2. Absence d'état du cycle mensuel (Pointage → Paie) — information critique manquante
3. Widgets sans action inline — valider un congé depuis le Dashboard nécessite une navigation vers /conges
4. AccesRapidesWidget redondant avec la sidebar

| Critère | Note |
|---|---|
| UI | 6/10 |
| UX | 5/10 |
| Ergonomie | 6/10 |
| Logique | 5/10 |
| Fluidité | 6/10 |
| Lisibilité | 7/10 |
| Cohérence | 6/10 |
| Accessibilité | 6/10 |

**Verdict : RESTRUCTURER** → Cockpit de cycle mensuel avec actions inline

---

## [LISTE EMPLOYÉS] EmployeList.jsx

**Objectif :** Répertoire des collaborateurs.

**Problèmes :**
1. Chargement de 1000 employés en une requête pour filtrer côté client — scalabilité nulle
2. 4 filtres simultanés surdimensionnés pour 20-30 employés
3. EmployeStatsCards (3 stats) — visuellement encombrant, information inutile à ce volume
4. Vue tableau dense — pour si peu d'employés, une vue cards serait plus lisible

| Critère | Note |
|---|---|
| UI | 6/10 |
| UX | 6/10 |
| Ergonomie | 6/10 |
| Logique | 5/10 |
| Fluidité | 7/10 |
| Lisibilité | 6/10 |
| Cohérence | 6/10 |
| Accessibilité | 6/10 |

**Verdict : MODIFIER** → Supprimer stat-cards, simplifier filtres, ajouter colonne statut contrat

---

## [FICHE EMPLOYÉ] EmployeFicheDetailPage.jsx

**Objectif :** Dossier RH complet d'un collaborateur.

**Problèmes critiques :**
1. **PointageTab.jsx (48 Ko) dans la fiche** — C'est un module entier (hooks, états, filtres, tableaux) replié dans un onglet. Aussi complexe que SaisieQuotidiennePage. Duplication majeure.
   > Pourquoi aller dans la fiche employé pour voir son pointage quand un module Pointage complet existe ?
   > La solution : lien contextuel filtré, pas une duplication.
2. **CongesTab.jsx (14 Ko)** — même problème
3. **6-7 onglets** — trop, la fiche perd son focus (dossier RH)
4. **EmployeSideSummaryPanel** — layout complexe et dense

| Critère | Note |
|---|---|
| UI | 6/10 |
| UX | 4/10 |
| Ergonomie | 4/10 |
| Logique | 4/10 |
| Fluidité | 5/10 |
| Lisibilité | 6/10 |
| Cohérence | 3/10 |
| Accessibilité | 6/10 |

**Verdict : RESTRUCTURER**
```text
FICHE EMPLOYÉ [navigation libre — 4 onglets seulement]
├── Identité & Coordonnées
├── Documents RH
├── Famille & Urgences
└── Carrière & Contrat

SUPPRIMÉS → Remplacés par liens contextuels :
├── → Voir pointages de cet employé [/pointage?employe=42]
└── → Voir congés de cet employé    [/conges?employe=42]
```

---

## [SUPERVISION CARRIÈRES] CarrieresSupervisionPage.jsx

**Objectif :** Vue globale contrats, alertes échéances, mouvements RH.

**Problème principal :**
Page séparée alors qu'elle est fonctionnellement liée à la liste des employés. Navigation inutile entre /employes et /employes/carrieres.

| Critère | Note |
|---|---|
| UI | 6/10 | UX | 6/10 | Ergonomie | 6/10 | Logique | 5/10 |

**Verdict : FUSIONNER** → Onglet "Contrats & Carrières" dans EmployeList

---

## [GESTION POINTAGE] GestionPointagesPage.jsx (5 fichiers)

**Problèmes CRITIQUES :**

1. **Navigation libre dans un workflow séquentiel obligatoire** — 4 onglets égaux pour un processus qui doit être Saisie → Vérification → Transmission → Arbitrage DAF

2. **PointageWorkflowNav.jsx existe mais est non-directive** — Elle montre des onglets, pas une progression. Elle ne bloque pas, ne guide pas, n'explique pas "où vous en êtes".

3. **Validation DAF dans /paie — rupture de contexte majeure** — Après transmission, la DAF doit changer de module. Il n'y a aucune continuité visuelle.

4. **SynthesePointagePage.jsx fait 1149 lignes (80 Ko)** — Trop grande, fait trop de choses.

5. **Anomalies non priorisées** — Liste plate sans hiérarchisation par criticité ni regroupement par employé.

| Critère | Note |
|---|---|
| UI | 6/10 |
| UX | 4/10 |
| Ergonomie | 4/10 |
| Logique | 4/10 |
| Fluidité | 4/10 |
| Lisibilité | 6/10 |
| Cohérence | 5/10 |
| Accessibilité | 5/10 |

**Verdict : REMPLACER — Cockpit avec workflow linéaire guidé ① → ④**

---

## [GESTION CONGÉS] GestionCongesPage.jsx

**Problèmes :**
1. Seulement 2 onglets pour un module complexe (soldes, planning, demandes, validation)
2. L'onglet "Soldes & Planning prévisionnel" mélange 2 vues différentes
3. Absence de vue calendrier / Gantt pour visualiser les absences
4. Paramètres congés dans /parametres — rupture de contexte

| Critère | Note |
|---|---|
| UI | 6/10 | UX | 5/10 | Ergonomie | 5/10 | Logique | 5/10 |

**Verdict : RESTRUCTURER** → 3 onglets : Planning/Calendrier, Soldes, Demandes

---

## [PAIE] PeriodePaiePage.jsx + GestionPaiePage.jsx + PointageValidationDafTab.jsx

**Problèmes :**
1. **PointageValidationDafTab.jsx (38 Ko) dans /paie** — Conceptuellement du pointage, pas de la paie. Mauvaise organisation.
2. **GestionPaiePage.jsx (4 Ko)** — Coquille quasi-vide qui délègue tout. Architecture confuse.
3. **Workflow paie invisible** — Les statuts BROUILLON → CLOTUREE existent mais leur progression n'est pas guidée.
4. **Solde de Tout Compte (STC)** — Accès peu clair dans la navigation.

| Critère | Note |
|---|---|
| UI | 6/10 | UX | 4/10 | Ergonomie | 5/10 | Logique | 4/10 | Fluidité | 4/10 | Cohérence | 4/10 |

**Verdict : RESTRUCTURER — Workflow guidé Paie en 5 étapes**

---

# 6. ANALYSE DES PARCOURS UTILISATEUR

## 6.1 Traitement du pointage de septembre

### Parcours actuel

```text
/pointage?tab=saisie         ← 22 jours de saisie
    ↓ (navigation manuelle)
/pointage?tab=suivi          ← calcul 1-clic
    ↓ (navigation manuelle)
/pointage?tab=anomalies      ← visible DAF uniquement !
    ↓ (navigation manuelle)
/pointage?tab=synthese       ← transmission
    ↓ (CHANGEMENT DE MODULE)
/paie?tab=validation         ← arbitrage DAF
    ↓ (si rejet : retour arrière, motif du rejet non visible pour l'agent)
```

**Fluidité actuelle : 3/10**

### Parcours cible

```text
/pointage [Cockpit]          ← Vue semaine, anomalies visibles inline
    ↓ (bouton contextuel)
[Étape ②] Vérification       ← 0 anomalie = bouton Transmettre activé
    ↓
[DAF] Notification           ← Arbitrage sans changer de module
    ↓ (si rejet)
[Agent] Bannière motif visible directement dans le cockpit
```

**Gain : 5 navigations → 2 max. Contexte conservé. Anomalies inline.**

## 6.2 Validation d'un congé

### Parcours actuel
Dashboard → /conges?tab=demandes → modal validation (3 clics, changement de page)
**Fluidité : 6/10**

### Parcours cible
Dashboard → [Valider] inline dans le widget (1 clic, modal contextuelle)

## 6.3 Calcul de paie de septembre

### Parcours actuel
/paie?tab=validation → /paie (calcul) → variables → recalcul → clôture → exports
(3 URLs différentes pour 1 workflow)
**Fluidité : 4/10**

### Parcours cible
/paie [Cockpit ① → ⑤] — un seul espace guidé de bout en bout

---

# 7. WORKFLOWS CIBLES COMPLETS

## 7.1 Module Pointage — WORKFLOW LINÉAIRE GUIDÉ

> **DÉCISION : Workflow linéaire** car séquentiel avec dépendances strictes. La navigation libre induirait des erreurs.

```text
ACTEUR : Agent de Pointage
DÉCLENCHEUR : Période créée par la DAF

ÉTAPE 0 — PÉRIODE INITIALISÉE
    → Agent voit cockpit : "Septembre 2026 — EN COURS"

ÉTAPE ① SAISIE (quotidienne, pendant tout le mois)
    → Grille employés × semaine (pas jour par jour)
    → Statuts pré-remplis automatiquement (CalendrierOuvrableEngine)
    → Agent corrige les exceptions manuellement
    → Anomalies détectées en temps réel (badge rouge sur la ligne)
    → Sauvegarde

ÉTAPE ② VÉRIFICATION (fin de mois)
    → Résumé : employés traités, jours saisis, heures totales
    → Anomalies restantes avec lien [Corriger]

DÉCISION ①
├── Anomalies bloquantes ?
│   ├── OUI → Retour ① avec anomalie mise en évidence
│   └── NON → Bouton "Transmettre à la DAF" activé

ÉTAPE ③ TRANSMISSION
    → Calcul scellé automatique
    → Notification DAF (cloche + badge)
    → Interface Agent : LECTURE SEULE

══════════════════════════════════════════
ACTEUR : DAF
══════════════════════════════════════════

ÉTAPE ④ ARBITRAGE DAF
    → Vue synthèse : KPIs, tableau employés
    → Détail accessible au clic

DÉCISION ②
├── Valide → Période VALIDEE → Module Paie débloqué → Notification Agent
└── Rejette → Motif obligatoire → Période EN_COURS → Bannière Agent avec motif
              → Retour Étape ①
```

---

## 7.2 Module Paie — WORKFLOW GUIDÉ 5 ÉTAPES

```text
ACTEUR : DAF
DÉCLENCHEUR : Pointage VALIDEE

ÉTAPE ① PRÉPARATION
    → Variables saisies : primes, ajustements, régularisations
    → Prêts/avances vérifiés
    → STC si départs ce mois

ÉTAPE ② CALCUL
    → PayrollExecutionEngine — automatique
    → Tableau résultats par employé
    → Alertes : net négatif, plafonnements

ÉTAPE ③ CONTRÔLE
    → DAF vérifie chaque bulletin (résumé + détail)
    → Modification possible → recalcul

ÉTAPE ④ VALIDATION & CLÔTURE
    → Bulletins PDF (lot ou individuel)
    → Ordre de virement

ÉTAPE ⑤ EXPORTS RÉGLEMENTAIRES
    → État 301 IRSA (SAFI/e-Hetra)
    → EDI ISI 5%
    → DNTS CNaPS (si fin trimestre)
    → Bordereaux OSTIE/OMSI

ÉTAT FINAL : CLOTUREE — Archivée définitivement
```

---

## 7.3 Module Congés — WORKFLOW PAR ÉTAT

```text
ACTEUR : DAF
DÉCLENCHEUR : Demande reçue (statut EN_ATTENTE)

ÉTAPE ① VÉRIFICATION AUTOMATIQUE
    → Solde disponible (CongeCalculateurEngine — FIFO)
    → Anti-collision planning (même fonction/site)
    → Ancienneté min 12 mois (Art. 85)
    → Si maladie > 30j : scission Art. 13 proposée automatiquement

ÉTAPE ② DÉCISION DAF
    ├── APPROUVER
    │   → Solde décompté FIFO
    │   → Pointage marqué congé
    │   → État : APPROUVEE
    └── REFUSER
        → Motif obligatoire
        → État : REFUSEE

IMPACT : APPROUVEE → Impact automatique paie si absence non rémunérée
```

---

## 7.4 Module Employés — WORKFLOW LIBRE

> **DÉCISION : Workflow libre** — La gestion d'un dossier RH n'a pas d'ordre imposé.

```text
ENTRÉE : Liste employés → Fiche employé

NAVIGATION LIBRE (4 onglets) :
├── Identité & Coordonnées
├── Documents RH
├── Famille & Urgences
└── Carrière & Contrat → Mouvement RH → STC si départ

ACCÈS CONTEXTUELS (liens, pas onglets) :
├── → Pointages de cet employé [/pointage?employe=42]
└── → Congés de cet employé   [/conges?employe=42]
```

---

## 7.5 Module Carrières — WORKFLOW PAR ÉTAT

```text
EMBAUCHE
    Employé créé → Contrat saisi → Période essai calculée → Documents OK
    → ÉTAT : ACTIF → Apparaît dans les grilles de pointage

DÉPART
    Clôture contrat (motif obligatoire) → STC calculé automatiquement
    → DAF valide STC → Bulletin final → ÉTAT : INACTIF
```

---

# 8. WORKFLOWS CROSS-MODULE

## 8.1 Chaîne de valeur complète

```text
EMPLOYÉ → données : matricule, catégorie (Agent/Cadre), salaire base
    ↓
CONTRAT → données : type, filiale, site, horaires théoriques
    ↓
CALENDRIER → données : jours ouvrables, fériés par site
    ↓
POINTAGE (saisie) → données : heures réelles, statuts, indemnités
    ↓
POINTAGE (synthèse) → calcul : HS 130%/150%, nuit, dimanche, déductions
    ↓
CONGÉS → données : jours absents, type (payé / SS / maladie)
    ↓
VARIABLES PAIE → données : primes, régularisations, prêts, avances
    ↓
PAIE → calcul : brut, cotisations CNaPS/OSTIE, IRSA/ISI, net
    ↓
EXPORTS → SAFI, EDI, DNTS, Bulletins PDF
```

## 8.2 Sources de vérité

| Donnée | Source Unique | Consommateurs |
|---|---|---|
| Salaire de base | `contrat_travail.salaire_base` | Paie, STC |
| Catégorie Agent/Cadre | `contrat_travail.categorie_pro` | Pointage HS, Paie |
| Site de travail | `contrat_travail.id_site` | Pointage, Indemnités, Calendrier |
| Heures réelles | `pointage_jour` | Synthèse, Paie |
| Jours ouvrables | `CalendrierOuvrableEngine` | Pointage, Congés |
| Solde congés | `conge_solde_pool` FIFO | Congés, Paie, STC |
| Taux IRSA | `tranche_irsa` | Paie, STC |

## 8.3 Duplications identifiées

| Donnée | Endroit 1 | Endroit 2 | Action |
|---|---|---|---|
| Pointage individuel | Module /pointage | PointageTab fiche employé | Supprimer doublon |
| Congés individuels | Module /conges | CongesTab fiche employé | Supprimer doublon |
| Validation DAF pointage | Module Pointage (synthèse) | Module Paie | Fusionner dans Pointage |

---

# 9. ARCHITECTURE CIBLE

```text
SIDEBAR — GALAXAM SIRH
═══════════════════════════════════════════════════

Dashboard → /dashboard
  Widget : Cycle mensuel (Pointage + Paie)
  Widget : Actions rapides (valider congés inline)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
PERSONNEL
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Employés & Dossiers → /employes
  [onglet] Répertoire
  [onglet] Contrats & Carrières  ← FUSION CarrieresSupervisionPage

  Congés & Absences → /conges
  [onglet] Planning & Calendrier  ← NOUVEAU
  [onglet] Soldes
  [onglet] Demandes

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
TEMPS DE TRAVAIL
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Pointage → /pointage
  [Cockpit workflow linéaire ① → ④]

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
PAIE (DAF uniquement)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Paie → /paie
  [Cockpit workflow guidé ① → ⑤]

  Historique → /paie/historique

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
ADMINISTRATION (DAF uniquement)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Organisation, Référentiels Paie, Calendrier Entreprise,
  Utilisateurs & Rôles, Modules & Menus
```

---

# 10. DESIGN SYSTEM — AUDIT & RECOMMANDATIONS

## 10.1 Points forts

- Palette "Sauge Apaisant" cohérente et sémantique (tokens HSL bien définis)
- Composants atomiques existants : Table, Badge, Alert, Button, Modal, Select
- Sidebar propre avec mode rétractable

## 10.2 Problèmes identifiés

### Typographie
**Problème majeur :** Police `system-ui` — rendu différent sur macOS/Windows/Android.
**Recommandation :** Migrer vers **Inter** (Google Fonts — référence SaaS B2B).

### Tokens manquants
```css
/* Statuts métier — actuellement hardcodés dans chaque page */
--color-status-active-bg: var(--color-info-100);
--color-status-active-fg: var(--color-info-700);
--color-status-pending-bg: var(--color-warning-100);
--color-status-pending-fg: var(--color-warning-700);
--color-status-validated-bg: var(--color-success-100);
--color-status-validated-fg: var(--color-success-700);
--color-status-closed-bg: var(--color-ink-100);
--color-status-closed-fg: var(--color-ink-600);
```

### Styles hardcodés à éliminer
Classes Tailwind répétées dans les pages : `bg-emerald-500/10 text-emerald-700 border-emerald-500/30`
→ Remplacer par `<StatusBadge statutCode="VALIDEE" />` composant unifié

### Composants manquants
- `WorkflowStepper` — indicateur d'étapes réutilisable (Pointage + Paie)
- `StatusBadge` — badge de statut unifié (période, contrat, congé)
- `InlineActionWidget` — actions rapides dans les widgets Dashboard

## 10.3 Standardisation des variantes Badge

| Statut | Variante | Couleur |
|---|---|---|
| BROUILLON | secondary | Gris |
| EN_COURS | info | Bleu-indigo |
| A_VALIDER | warning | Ambre |
| VALIDEE | success | Vert |
| CLOTUREE | neutral | Gris foncé |
| EN_ATTENTE | warning | Ambre |
| APPROUVEE | success | Vert |
| REFUSEE | danger | Rouge |

---

# 11. WIREFRAMES

## 11.1 Cockpit Pointage — Vue Agent (Étape ①)

```text
┌─────────────────────────────────────────────────────────────────────────┐
│  POINTAGE                                          ⬤ EN COURS           │
│  Septembre 2026 · Site TMM (Tamatave)                                   │
├─────────────────────────────────────────────────────────────────────────┤
│  ① SAISIE  →  ② VÉRIFICATION  →  ③ TRANSMISSION  [DAF ④ ARBITRAGE]     │
│  [●●●●●○○○]  [             ]     [             ]                        │
│   17j / 22j                                                             │
├─────────────────────────────────────────────────────────────────────────┤
│  Semaine du 01/09 au 07/09     [< Semaine préc.]  [Semaine suiv. >]     │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────────┐│
│  │ Employé     │ Lun 01 │ Mar 02 │ Mer 03 │ Jeu 04 │ Ven 05 │ État   ││
│  ├─────────────┼─────────┼────────┼────────┼────────┼────────┼────────┤│
│  │ Jean R.     │ 08-17  │ 08-17  │  ⚠ !  │ 08-17  │ 08-17  │  ⚠    ││
│  │ Marie T.    │ 08-17  │ 08-17  │ 08-17  │ 08-17  │ 08-17  │  ✓    ││
│  │ Pierre M.   │ CONGÉ  │ CONGÉ  │ 08-17  │ 08-17  │ 08-17  │  ✓    ││
│  │ Sophie L.   │   -    │   -    │   -    │   -    │   -    │  ●    ││
│  └─────────────────────────────────────────────────────────────────────┘│
│                                                                         │
│  ⚠ 1 anomalie bloquante — Mer 03 Sept · Jean R. : Pointage incomplet   │
│  [Corriger maintenant]                                                  │
│                                           [Enregistrer]  [→ Étape ②]   │
└─────────────────────────────────────────────────────────────────────────┘
```

## 11.2 Cockpit Pointage — Étape ② Vérification

```text
┌─────────────────────────────────────────────────────────────────────────┐
│  POINTAGE · Vérification                          ⬤ EN COURS           │
│  Septembre 2026 · Site TMM                                              │
├─────────────────────────────────────────────────────────────────────────┤
│  ① SAISIE ✓  →  ② VÉRIFICATION  →  ③ TRANSMISSION                     │
│                  [●●●●●●●●●●●●]                                         │
├─────────────────────────────────────────────────────────────────────────┤
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │  Employés traités : 18/20  │  Jours saisis : 22  │  HS : 48h     │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  Anomalies restantes : 0  ✓                                             │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │  ✓ Aucune anomalie bloquante — Transmission possible              │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  Employés non traités (2) :                                             │
│  · Sophie L. — Aucune heure saisie ce mois          [← Saisir]        │
│  · Marc B. — Dernier jour : 15/09 (départ ?)        [← Saisir]        │
│                                                                         │
│                            [← Retour Saisie]  [Transmettre à DAF →]   │
└─────────────────────────────────────────────────────────────────────────┘
```

## 11.3 Cockpit Paie — Vue DAF (Étape ① Variables)

```text
┌─────────────────────────────────────────────────────────────────────────┐
│  PAIE                                         ⬤ POINTAGE VALIDÉ        │
│  Septembre 2026                                                         │
├─────────────────────────────────────────────────────────────────────────┤
│  ① VARIABLES  →  ② CALCUL  →  ③ CONTRÔLE  →  ④ BULLETINS  →  ⑤ EXPORTS│
│  [●●●●●●○○]                                                             │
├─────────────────────────────────────────────────────────────────────────┤
│  Primes & Ajustements manuels                                           │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │ Employé     │ Type mouvement  │ Montant  │ Rubrique  │ Actions    │  │
│  │ Jean R.     │ Prime except.   │ 50 000 ₳ │ PRIME_SP  │ [Suppr.]   │  │
│  │ [+ Ajouter un mouvement]                                          │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  Prêts actifs (3 remboursements ce mois)                                │
│  Marie T. · Prêt 120 000 ₳ · Échéance 40 000 ₳/mois · Reste : 2      │
│                                                                         │
│                                    [Sauvegarder]  [→ ② Calculer]       │
└─────────────────────────────────────────────────────────────────────────┘
```

## 11.4 Fiche Employé — Nouvelle Structure

```text
┌─────────────────────────────────────────────────────────────────────────┐
│  ← Employés  /  Jean RAKOTO — Chauffeur · EMP-0042 · TMM               │
├─────────────────────────────────────────────────────────────────────────┤
│  [Photo]  Jean RAKOTO                  ┌────────────────────────────┐   │
│           Chauffeur Grade B            │  Ancienneté : 4 ans 2 mois  │   │
│           Site : Tamatave              │  Contrat : CDI · ACTIF      │   │
│           Filiale : GALAXAM            │  Solde congés : 12.5 j      │   │
│  [Modifier]  [Désactiver]             │  Dernier ptg : Auj. ✓       │   │
│                                        └────────────────────────────┘   │
├─────────────────────────────────────────────────────────────────────────┤
│  [Identité & Coordonnées] [Documents(3)] [Famille] [Carrière & Contrat] │
├─────────────────────────────────────────────────────────────────────────┤
│  Nom complet    : RAKOTO Jean Pierre         CIN : 123456789012         │
│  Date naissance : 15 mars 1985 (41 ans)      Banque : BFV               │
│  Téléphone      : +261 34 123 4567           RIB : ...                  │
│  Adresse        : Lot VK12, Toamasina                                   │
│                                                                         │
│  ─────────────────────────────────────────────────────────────────────  │
│  Accès rapides :                                                        │
│  → Voir pointages de Jean RAKOTO en septembre 2026  [Ouvrir]           │
│  → Voir historique des congés de Jean RAKOTO        [Ouvrir]           │
└─────────────────────────────────────────────────────────────────────────┘
```

## 11.5 Dashboard — Cockpit Mensuel

```text
┌─────────────────────────────────────────────────────────────────────────┐
│  Tableau de bord — Mardi 8 Septembre 2026                               │
├─────────────────────────────────────────────────────────────────────────┤
│  CYCLE MENSUEL SEPTEMBRE 2026                                           │
│  ────────────────────────────────────────────────────────────────────   │
│  POINTAGE  ① Saisie [●●●●●●●○○○] 17/22j  →  ② En attente  →  ③ ...   │
│  [Accéder au pointage →]                                               │
│                                                                         │
│  PAIE      En attente clôture pointage — Non calculable                │
│                                                                         │
├────────────────────────────┬────────────────────────────────────────────┤
│  AUJOURD'HUI               │  ALERTES                                  │
│  ─────────────────────     │  ───────────────────────────────────────  │
│  Absents : 2               │  ⚠ CDD Marie T. expire dans 8 jours       │
│  En congé : 1              │  ⚠ Congé Jean R. en attente depuis 3j     │
│  Présents : 17             │    [Valider ✓]  [Refuser ✗]               │
│                            │                                            │
├────────────────────────────┴────────────────────────────────────────────┤
│  CONGÉS À VALIDER (2)                                                  │
│  ────────────────────────────────────────────────────────────────────   │
│  Jean RAKOTO · Congé annuel · 10-15 sept (5j) · Solde : 15j disponibles│
│  [Valider ✓]  [Refuser ✗]  [Détail →]                                  │
└─────────────────────────────────────────────────────────────────────────┘
```

