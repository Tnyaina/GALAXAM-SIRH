# GALAXAM SIRH — Index de la Documentation Technique
## Mémoire de Fin d'Études

> **Projet** : GALAXAM SIRH — Système d'Information des Ressources Humaines  
> **Entreprise** : GALAXAM Logistics Madagascar  
> **Stack** : Java (Spring Boot) + PostgreSQL + React.js  
> **Date** : 2026

---

## Documents Disponibles

| Document | Module | Description | Fichier |
|---|---|---|---|
| 📋 **Module Employé, Contrat & Carrière** | Employé & Carrière | Dossier individuel, workflows RH, contrats, affectations, génération de contrats Word (.docx) et documents RH | [`module-employe-carriere.md`](./module-employe-carriere.md) |
| 🏢 **Module Structure** | Organisation | Filiales, Sites, Référentiels, Fiscalité | [`module-structure-organisation.md`](./module-structure-organisation.md) |
| 💰 **Module Paie & Rémunération** | Paie | **Document Maître Mémoire** : Cadre légal Malagasy, IRSA 6 tranches, CNaPS 8×SME, STC, BDD, API, Bilan des réalisations | [`module-paie.md`](./module-paie.md) |
| 📜 **Réglementation Fiscale** | Paie & Fiscalité | Versions légales datées, SME, cotisations et barème IRSA | [Chapitre du module Paie](./module-paie.md#4-référentiel-légal-et-sélection-à-date-deffet) |
| 📝 **Module Contrat & Carrière** | Contrat & Carrière | Parcours d'embauche, mouvements, périodes d'essai et sorties | [Chapitre du module Employé & Carrière](./module-employe-carriere.md#5-contrats-et-période-dessai) |
| 🏖️ **Module Congés** | Congés & Absences | Acquisition, Décompte, Purge FIFO, Bilan annuel officiel Excel (12 mois), Alertes | [`module-conges-absences.md`](./module-conges-absences.md) |
| 💳 **Variables, avances et prêts** | Paie | Saisie, décision et imputation des mouvements financiers | [Chapitre du module Paie](./module-paie.md#6-variables-avances-et-prêts) |
| 🔐 **Sécurité & RBAC** | Transversal | Authentification JWT, RBAC dynamique | [`securite.md`](./securite.md) |
| 🔧 **Initialisation** | Infrastructure | Setup, déploiement, base de données | [`init.md`](./init.md) |
| ⚡ **Optimisation** | Transversal | Performances, indexation, requêtes | [`optimisation.md`](./optimisation.md) |

---

## Architecture Globale du Système

```
┌─────────────────────────────────────────────────────────────────────┐
│                         GALAXAM SIRH                                │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│   FRONTEND (React.js / Vite)                                        │
│   ├── /employes      → Module Employé                               │
│   ├── /conges        → Module Congés & Absences                     │
│   ├── /pointages     → Module Pointage                              │
│   ├── /paie          → Module Paie & Rémunération                   │
│   ├── /parametres    → Module Structure & Réglementation Fiscale    │
│   └── /systeme       → Module Admin (RBAC, utilisateurs)            │
│                                                                     │
│   BACKEND (Spring Boot / Java)                                      │
│   ├── api/controller/employe    → CRUD employés & documents         │
│   ├── api/controller/contrat    → Workflow RH & contrats            │
│   ├── api/controller/conge      → Demandes & soldes de congé        │
│   ├── api/controller/pointage   → Pointage, périodes, synthèses     │
│   ├── api/controller/paie       → Moteur de paie, STC & exports     │
│   ├── api/controller/organisation → Référentiels structure          │
│   ├── api/controller/securite   → Auth JWT + RBAC                   │
│   └── cron/                     → 6 schedulers nocturnes             │
│       ├── MouvementRhScheduler     00:00:00 quotidien               │
│       ├── NotificationRhScheduler  00:05:00 quotidien               │
│       ├── PointageRappelScheduler  00:10:00 quotidien               │
│       ├── PeriodePaieScheduler     00:15:00 quotidien               │
│       ├── FerieChomeScheduler      00:20:00 quotidien               │
│       ├── AcquisitionCongeScheduler 01:00:00 mensuel               │
│       └── (aucun débit automatique des droits à congé prescrits)   │
│                                                                     │
│   BASE DE DONNÉES (PostgreSQL)                                      │
│   ├── Référentiels : ref_filiale, ref_site, ref_departement...      │
│   ├── RH Core : employe, contrat_travail, affectation_carriere      │
│   ├── Workflow : workflow_rh, audit_log, notification_rh            │
│   ├── Congés : demande_conge, mouvement_solde_conge, vues           │
│   └── Pointage : pointage_jour, periode_paie, semaine_calcule...    │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Planification des Schedulers (Tâches Nocturnes)

| Heure | Scheduler | Fréquence | Action |
|---|---|---|---|
| `00:00:00` | `MouvementRhScheduler` | Quotidien | Applique les mouvements RH planifiés (mutations, promotions, ruptures) |
| `00:05:00` | `NotificationRhScheduler` | Quotidien | Génère toutes les alertes RH (contrats, essais, congés, documents) |
| `00:10:00` | `PointageRappelScheduler` | Quotidien | Détecte les employés sans pointage la veille (hors DISPENSE/CONGE/FERIE_CHOME) |
| `00:15:00` | `PeriodePaieScheduler` | Quotidien | Fait passer `periode_paie` de `EN_COURS` à `A_VALIDER` quand `date_fin` dépassée |
| `00:20:00` | `FerieChomeScheduler` | Quotidien | Pré-crée les lignes `pointage_jour` en statut `FERIE_CHOME` pour les jours à venir |
| `01:00:00` | `AcquisitionCongeScheduler` | Mensuel (1er) | Vérifie la bonification d'ancienneté du mois clos ; l'acquisition passe par la clôture de paie |
| — | — | Manuel DAF | Les droits candidats à prescription sont examinés dans la fiche salarié ; aucun débit automatique |

---

## Types de Notifications RH Générées

| Type d'Alerte | Module | Priorité |
|---|---|---|
| `CONTRAT_EXPIRE_J` | Contrat | 🔴 CRITIQUE |
| `CONTRAT_EXPIRE_7J` | Contrat | 🟠 HAUTE |
| `CONTRAT_EXPIRE_30J` | Contrat | 🟡 NORMALE |
| `ESSAI_EXPIRE_J` | Contrat | 🔴 CRITIQUE |
| `ESSAI_EXPIRE_7J` | Contrat | 🟠 HAUTE |
| `ESSAI_EXPIRE_15J` | Contrat | 🟡 NORMALE |
| `ESSAI_EXPIRE_30J` | Contrat | 🟡 NORMALE |
| `BROUILLON_EN_ATTENTE` | Contrat | 🟠 HAUTE |
| `DOCUMENTS_MANQUANTS` | Employé | 🟠 HAUTE |
| `CONGE_EN_ATTENTE` | Congés | 🟠 HAUTE |
| `SOLDE_CONGE_FAIBLE` | Congés | 🔵 BASSE |
| `SOLDE_CONGE_NEGATIF` | Congés | 🟠 HAUTE |
| `RETOUR_CONGE_J` | Congés | 🔵 BASSE |
| `FRACTIONNEMENT_NON_RESPECTE` | Congés | 🟠 HAUTE |
| `MALADIE_PLAFOND_EPISODE_ATTEINT` | Congés | 🔴 CRITIQUE |
| `CONGE_EXPIRATION_PROCHE` | Congés | 🟠 HAUTE |
| `POOL_EXCEPTIONNEL_EPUISE` | Congés | 🟠 HAUTE |
| `BONIFICATION_ANCIENNETE_APPLIQUEE` | Congés | 🔵 BASSE |
| `POINTAGE_MANQUANT` | Pointage | 🟠 HAUTE |
| `PERIODE_POINTAGE_A_VALIDER` | Pointage | 🟠 HAUTE |
| `PERIODE_POINTAGE_VALIDEE` | Pointage | 🔵 INFO |
| `RETARD_RECURRENT` | Pointage | 🟡 NORMALE |

---

## Modules en Cours de Développement (Roadmap)

| Module | État | Description |
|---|---|---|
| **Pointage** | ✅ Implémenté & Validé | Saisie décalée (26 au 25), calculs d'heures, régularisations, exports Excel, UI complète |
| **Paie** | ⚡ Moteur & BDD 100% Opérationnels | Calcul IRSA 6 tranches, CNaPS 8×SME, Santé, STC, déclarations SAFI DGI (Workflow & UI en cours) |
| **Dashboard analytique** | 📅 Planifié | KPIs RH, tableaux de bord dirigeants |

---

*GALAXAM SIRH — Documentation générée pour mémoire de fin d'études 2026*
