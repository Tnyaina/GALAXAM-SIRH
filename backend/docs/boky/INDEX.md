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
| 📋 **Module Employé** | Employé | Dossier individuel, identité civile, documents RH | [`module-employe.md`](./module-employe.md) |
| 🏢 **Module Structure** | Organisation | Filiales, Sites, Référentiels, Fiscalité | [`module-structure.md`](./module-structure.md) |
| 📝 **Module Contrat** | Contrat & Carrière | Contrats, Workflow RH, Notifications, Certificats | [`Contrats-RG.md`](./Contrats-RG.md) |
| 🏖️ **Module Congés** | Congés & Absences | Acquisition, Décompte, Purge FIFO, Alertes | [`module-conges-absences.md`](./module-conges-absences.md) |
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
│   ├── /pointage      → Module Pointage (en cours)                   │
│   ├── /parametres    → Module Structure (référentiels)              │
│   └── /systeme       → Module Admin (RBAC, utilisateurs)            │
│                                                                     │
│   BACKEND (Spring Boot / Java)                                      │
│   ├── api/controller/employe    → CRUD employés & documents         │
│   ├── api/controller/contrat    → Workflow RH & contrats            │
│   ├── api/controller/conge      → Demandes & soldes de congé        │
│   ├── api/controller/organisation → Référentiels structure          │
│   ├── api/controller/securite   → Auth JWT + RBAC                   │
│   └── cron/                     → 4 schedulers nocturnes            │
│       ├── MouvementRhScheduler     00:00:00 quotidien               │
│       ├── NotificationRhScheduler  00:05:00 quotidien               │
│       ├── AcquisitionCongeScheduler 01:00:00 quotidien              │
│       └── PurgeCongeScheduler      02:00:00 le 1er du mois          │
│                                                                     │
│   BASE DE DONNÉES (PostgreSQL)                                      │
│   ├── Référentiels : ref_filiale, ref_site, ref_departement...      │
│   ├── RH Core : employe, contrat_travail, affectation_carriere      │
│   ├── Workflow : workflow_rh, audit_log, notification_rh            │
│   └── Congés : demande_conge, mouvement_solde_conge, vues           │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Planification des Schedulers (Tâches Nocturnes)

| Heure | Scheduler | Fréquence | Action |
|---|---|---|---|
| `00:00:00` | `MouvementRhScheduler` | Quotidien | Applique les mouvements RH planifiés (mutations, promotions, ruptures) |
| `00:05:00` | `NotificationRhScheduler` | Quotidien | Génère toutes les alertes RH (contrats, essais, congés, documents) |
| `01:00:00` | `AcquisitionCongeScheduler` | Quotidien | Crédite 2,5j aux employés dont c'est l'anniversaire d'embauche |
| `02:00:00` | `PurgeCongeScheduler` | Mensuel (1er) | Purge les droits à congés prescrits (+36 mois, FIFO) |

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

---

## Modules en Cours de Développement (Roadmap)

| Module | État | Description |
|---|---|---|
| **Pointage** | 🔄 En cours | Saisie quotidienne, heures supplémentaires, synthèse mensuelle |
| **Paie** | 📅 Planifié | Calcul IRSA, CNAPS/OSTIE, bulletins de paie |
| **Dashboard analytique** | 📅 Planifié | KPIs RH, tableaux de bord dirigeants |

---

*GALAXAM SIRH — Documentation générée pour mémoire de fin d'études 2026*
