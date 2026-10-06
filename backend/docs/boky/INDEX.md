# GALAXAM SIRH — Index de la Documentation Technique
## Mémoire de Fin d'Études

> **Projet** : GALAXAM SIRH — Système d'Information des Ressources Humaines  
> **Entreprise** : GALAXAM Logistics Madagascar  
> **Stack** : Java (Spring Boot 3.x) + PostgreSQL 16 + React.js / Vite  
> **Date de référence** : Octobre 2026  
> **Cadre Juridique** : Loi n° 2024-014 du 1er juillet 2024 & Loi n° 2003-044 (Code du Travail malgache), CGI LF 2026/2025, Décrets CNaPS/Santé, Loi 2017-025 (FMFP).  
> **Modèle & Norme Académique** : Template Officiel IT University (`MémoireITU-v3r4.docx`).

---

## 🎯 Plan Officiel & Notes de Terrain du Mémoire

- **Plan Détaillé & Canevas ITU** : 👉 **[`plan-detaille-memoire-itu.md`](./plan-detaille-memoire-itu.md)** (Structure académique IMRAD, pagination cible, grille d'évaluation).
- **Notes de Terrain & Contexte GALAXAM** : 👉 **[`notes-contexte-stage-galaxam.md`](./notes-contexte-stage-galaxam.md)** (Période 06/07/2026 - 06/10/2026, encadrants, projet MID/CSB, audit critique des classeurs Excel).

---

## 1. Documents Maîtres des Modules Métier

Chaque document de module a été intégralement audité, enrichi et strictement aligné avec le code source Java de production, les scripts de migration Flyway/PostgreSQL et les textes de lois applicables en République de Madagascar :

| Document Maître | Module Métier | Principaux Composants Techniques & Périmètre | Fichier |
|---|---|---|---|
| 📋 **Module Employé, Contrat & Carrière** | Employé & Carrière | Dossier individuel (Art. 18-20), matricule séquentiel, `ContratTravailController`, transitions de carrière (`CarriereTransitionValidator`), historique sans chevauchement, génération DOCX d'embauche et fiches. | [`module-employe-carriere.md`](./module-employe-carriere.md) |
| 🏢 **Module Structure, Organisation & Administration** | Organisation & Référentiel | `OrganisationController` (`/referentiel`), 32 tables `ref_*`, 37 clés `parametre_global` versionnées à date d'effet, plancher d'arrivée par site (`ref_site`), gestion des filiales et devises. | [`module-structure-organisation.md`](./module-structure-organisation.md) |
| 🏖️ **Module Congés & Absences** | Congés & Absences | Moteur FIFO par lots de valorisation (`CongeLotValorisationService`), décompte calendaire dynamique (Art. 127), pool familial 10j (Art. 133), paternité 3j (Art. 134), examen DAF de prescription triennale (Art. 129), blocage du solde négatif (RG-CON-05), export Excel DAF 12 mois. | [`module-conges-absences.md`](./module-conges-absences.md) |
| ⏱️ **Module Pointage & Présence** | Pointage & Présence | Cycle paie décalé (26 au 25), plancher d'arrivée par site (`plancher_arrivee_actif`, `heure_entree_minimale_valorisee`), `TemporalAttendanceEngine` (qualification orthogonale nuit 22h-05h, dimanche, férié), ventilation hebdo (Art. 109-110), ajustements DAF de période (`PointageAjustementPeriodeService`), 4 tables de snapshots immuables. | [`module-pointage-presence.md`](./module-pointage-presence.md) |
| 💰 **Module Paie, Rémunération, Cotisations, IRSA & STC** | Paie & Rémunération | Pipeline d'exécution en 6 paliers (`PayrollExecutionEngine`), barème progressif IRSA 6 tranches (CGI Art. 01.03.14) & minimum 3 000 Ar, cotisations CNaPS 1%/13% plafonnées à 8 SME, OSTIE 1%/5%, FMFP 1% patronal (Loi 2017-025), STC complet (`StcService`), circuits d'avances et prêts avec restitution transactionnelle, exports SAFI (DGI), DNTS (CNaPS) et virement bancaire. | [`module-paie.md`](./module-paie.md) |
| 🔐 **Module Transversal Sécurité, IAM & Audit** | Sécurité & Traçabilité | Authentification JWT HS512 (NIST SP 800-107), cookies refresh HttpOnly, RBAC dynamique multi-rôles (`CustomUserDetailsService`), table immuable `audit_log` (différentiels JSONB), non-répudiation (Loi n° 2014-038). | [`module-securite-audit.md`](./module-securite-audit.md) |

---

## 2. Architecture Globale du Système

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                     GALAXAM SIRH                                       │
├────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                        │
│   FRONTEND (React.js / Vite / TailwindCSS / Lucide Icons)                             │
│   ├── /employes         → Dossiers individuels, contrats, avenants, carrières          │
│   ├── /conges           → Demandes, validation N+1/RH, solde FIFO, examen prescription │
│   ├── /pointage         → Saisie quotidienne, cycle décalé 26-25, ajustements DAF     │
│   ├── /paie             → Cockpit mensuel, calcul 6 paliers, bulletins PDF, STC, SAFI  │
│   ├── /administration   → Référentiels (32 ref_*), 37 parametres_globaux, devises, sites│
│   └── /profil           → Espace collaborateur, suivi des demandes et soldes           │
│                                                                                        │
│   BACKEND (Spring Boot 3.x / Java 21)                                                  │
│   ├── mg.galaxam.sirh.employe      → CRUD employé, documents RH, matricule séquentiel  │
│   ├── mg.galaxam.sirh.contrat      → ContratTravailController, CarriereTransition...   │
│   ├── mg.galaxam.sirh.organisation → OrganisationController (/referentiel), ref_*      │
│   ├── mg.galaxam.sirh.conge        → DemandeCongeController, CongeLotValorisation...   │
│   ├── mg.galaxam.sirh.pointage     → PointageSaisieController, TemporalAttendance...   │
│   ├── mg.galaxam.sirh.paie         → PayrollController, PayrollExecutionEngine, STC... │
│   ├── mg.galaxam.sirh.securite     → Authentification JWT, RBAC multi-profils          │
│   └── Schedulers Nocturnes (@Scheduled Spring Boot)                                   │
│       ├── MouvementRhScheduler        00:00:00 quotidien (Application mutations/promos)│
│       ├── NotificationRhScheduler     00:05:00 quotidien (Alertes contrats/essais)    │
│       ├── PointageRappelScheduler     00:10:00 quotidien (Détection pointages absents) │
│       ├── PeriodePaieScheduler        00:15:00 quotidien (Transition auto EN_COURS)    │
│       ├── FerieChomeScheduler         00:20:00 quotidien (Pré-création jours fériés)  │
│       ├── AcquisitionCongeScheduler   01:00:00 mensuel   (Bonification ancienneté)     │
│       ├── SessionSecurityScheduler    03:30:00 quotidien (Purge refresh tokens expirés)│
│       └── (NB : Aucun cron de purge automatique des congés : examen humain DAF strict) │
│                                                                                        │
│   BASE DE DONNÉES (PostgreSQL 16)                                                      │
│   ├── Référentiels d'entreprise : ref_filiale, ref_site, ref_departement, ref_poste... │
│   ├── Configuration globale     : parametre_global (37 clés versionnées à date d'effet)│
│   ├── Dossier du personnel      : employe, contrat_travail, affectation_carriere       │
│   ├── Moteur de congés          : demande_conge, conge_lot_valorisation, mouvement...  │
│   ├── Moteur de présence        : pointage_jour, pointage_cumul_periode, snapshots...  │
│   ├── Moteur de rémunération    : periode_paie, bulletin_paie, paie_avance_acompte...  │
│   └── Audit et traçabilité      : audit_log, conge_expiration_examen, pointage_ajust... │
│                                                                                        │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Planification des Schedulers (Tâches Nocturnes)

| Heure | Scheduler Java | Fréquence | Action Réalisée | Base Légale / Justification |
|---|---|---|---|---|
| `00:00:00` | `MouvementRhScheduler` | Quotidien | Applique les mouvements RH dont la date d'effet est atteinte (mutations, promotions, titularisations, sorties). | Continuité du dossier salarié (Art. 18 Loi 2024-014). |
| `00:05:00` | `NotificationRhScheduler` | Quotidien | Génère les alertes de fin d'essai (J-30, J-15, J-7, J) et d'échéance de CDD. | Respect des préavis légaux d'essai (Art. 28 Loi 2024-014). |
| `00:10:00` | `PointageRappelScheduler` | Quotidien | Détecte les anomalies de pointage de la veille (hors dispenses, congés validés et fériés chômés). | Contrôle de la durée effective du travail (Art. 109). |
| `00:15:00` | `PeriodePaieScheduler` | Quotidien | Transfère automatiquement la période de pointage de `EN_COURS` vers `A_VALIDER` au lendemain de la date de fin (le 26 du mois). | Respect de l'échéance légale de paie mensuelle (Art. 138). |
| `00:20:00` | `FerieChomeScheduler` | Quotidien | Pré-crée les lignes `pointage_jour` en statut `FERIE_CHOME` pour les jours fériés légaux déclarés. | Décret n° 72-226 & Art. 122 Loi 2024-014. |
| `01:00:00` | `AcquisitionCongeScheduler` | Mensuel (1er) | Met à jour les bonifications d'ancienneté (+0,5 j/5 ans) du mois clos. | Art. 126 al. 2 Loi 2024-014. |
| `03:30:00` | `SessionSecurityScheduler` | Quotidien | Révocation en base des refresh tokens de session expirés. | NIST SP 800-107 & Loi n° 2014-038. |
| — | *Examen Prescription* | Aucun cron | Les droits à congés de plus de 3 ans ne sont **jamais purgés unilatéralement** par script ; ils sont signalés pour audit contradictoire DAF/Salarié. | Art. 129 Loi 2024-014 (Prescription triennale). |

---

## 4. Synthèse de l'État des Réalisations Techniques

| Module du Système | État de Livraison | Couverture de Tests Automatisés | Conformité Légale Malgache |
|---|---|---|---|
| **1. Structure & Organisation** | ✅ 100% Opérationnel | Tests référentiels, multi-filiales, multi-sites | Décrets fiscaux & territoriaux, conventions collectives |
| **2. Employé & Carrière** | ✅ 100% Opérationnel | Tests de transition d'état, validation contrats DOCX | Loi 2024-014 Art. 18-38, Loi 2003-044 |
| **3. Congés & Absences** | ✅ 100% Opérationnel | 90 tests unitaires & intégration (`mg.galaxam.sirh.conge`) | Loi 2024-014 Art. 124-136, Décret 2007-563 |
| **4. Pointage & Présence** | ✅ 100% Opérationnel | 83 tests unitaires & intégration (`mg.galaxam.sirh.pointage`) | Loi 2024-014 Art. 109-123, Arrêté 198-IGT |
| **5. Paie & Déclarations** | ✅ 100% Opérationnel | 125 tests unitaires & intégration (`mg.galaxam.sirh.paie`) | Loi 2024-014 Art. 137-147, CGI 2026/2025, CNaPS, FMFP |
| **6. Sécurité, IAM & Audit** | ✅ 100% Opérationnel | Tests d'authentification JWT, RBAC & audit immuable | Loi 2014-038 (Données personnelles), NIST SP 800-107 |

---

*GALAXAM SIRH — Documentation de référence pour Mémoire de Fin d'Études d'Ingénierie Logicielle (2026)*

