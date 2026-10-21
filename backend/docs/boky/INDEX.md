# GALAXAM SIRH — Index de la Documentation Technique
## Mémoire de Fin d'Études

> **Projet** : GALAXAM SIRH — Système d'Information des Ressources Humaines  
> **Entreprise** : GALAXAM Logistics Madagascar  
> **Stack** : Java (Spring Boot 4.1.0) + PostgreSQL 15 + React.js / Vite  
> **Date de référence** : Octobre 2026  
> **Cadre Juridique** : [Loi n° 2024-014 du 14 août 2024 portant Code du Travail](https://cnlegis.gov.mg/uploads/L2024-014-VF.pdf) (adoptée le 01/07/2024, validée par la HCC Décision n° 11-HCC/D3 le 08/08/2024, promulguée le 14/08/2024) & Loi n° 2003-044, CGI LF 2026/2025, Décrets CNaPS/Santé, Décret n° 72-226, Décret n° 64-161, Loi 2017-025 (FMFP).  
> **Modèle & Norme Académique** : Template Officiel IT University (`MémoireITU-v3r4.docx`).

---

## 🎯 Plan Officiel, Glossaire & Notes de Terrain du Mémoire

- **Glossaire Exhaustif du Projet** : 👉 **[`glossaire.md`](./glossaire.md)** (Ordre alphabétique A à Z de tous les acronymes légaux malgaches, termes RH, paie, STC et concepts techniques).
- **Plan Détaillé & Canevas ITU** : 👉 **[`plan-detaille-memoire-itu.md`](./plan/plan-detaille-memoire-itu.md)** (Structure académique IMRAD, pagination cible, grille d'évaluation).
- **Notes de Terrain & Contexte GALAXAM** : 👉 **[`notes-contexte-stage-galaxam.md`](./plan/notes-contexte-stage-galaxam.md)** (Période 06/07/2026 - 06/11/2026 incluant prolongation d'1 mois, encadrants, projet MID/CSB, audit critique des classeurs Excel).

---

## 1. Documents Maîtres des Modules Métier

Les six documents ci-dessous sont les documents maîtres des six modules. Ils sont à lire avec les sources SQL, Java et les migrations indiquées. Les tests et chiffres de couverture sont des constats datés, non une garantie de réussite de tout état de branche ultérieur. Les évolutions SP74–SP80 présentes dans les sources sont décrites dans les modules concernés ; leur présence dans le dépôt ne prouve pas leur application sur une base donnée. SP80 requiert un diagnostic des journées hors période et une validation de la suite complète avant déploiement.

| Document Maître | Module Métier | Principaux Composants Techniques & Périmètre | Fichier |
|---|---|---|---|
| 📋 **Module Employé, Contrat & Carrière** | Employé & Carrière | Dossier individuel (Art. 18-20), matricule séquentiel, `ContratTravailController`, transitions de carrière (`CarriereTransitionValidator`), historique sans chevauchement, génération DOCX d'embauche et fiches. | [`module-employe-carriere.md`](./module-employe-carriere.md) |
| 🏢 **Module Structure, Organisation & Administration** | Organisation & Référentiel | `OrganisationController` (`/referentiel`), 32 tables `ref_*`, 37 clés `parametre_global` versionnées à date d'effet, plancher d'arrivée par site (`ref_site`), gestion des filiales et devises. | [`module-structure-organisation.md`](./module-structure-organisation.md) |
| 🏖️ **Module Congés & Absences** | Congés & Absences | Moteur FIFO par lots de valorisation (`CongeLotValorisationService`), décompte calendaire dynamique (Art. 127), pool familial 10j (Art. 133), paternité 3j (Art. 134), examen DAF de prescription triennale (Art. 129), blocage du solde négatif (RG-CON-05), export Excel DAF 12 mois. | [`module-conges-absences.md`](./module-conges-absences.md) |
| ⏱️ **Module Pointage & Présence** | Pointage & Présence | Cycle paie décalé (26 au 25), plancher d'arrivée par site (`plancher_arrivee_actif`, `heure_entree_minimale_valorisee`), `TemporalAttendanceEngine` (qualification orthogonale nuit 22h-05h, dimanche, férié), ventilation hebdo (Art. 109-110), ajustements DAF de période (`PointageAjustementPeriodeService`), 4 tables de snapshots immuables. | [`module-pointage-presence.md`](./module-pointage-presence.md) |
| 💰 **Module Paie, Rémunération, Cotisations, IRSA & STC** | Paie & Rémunération | Campagne et bulletins, déclaratifs sociaux, cotisations, variables, acomptes/avances et échéances de prêts en paie mensuelle. Le STC s'articule autour de `paie_dossier_sortie` (checklist, offboarding, arbitrage prêts, certificat Art. 50, clôture dérogatoire SP81), `paie_stc` (décompte scellé, ICCP en FIFO congés, snapshot employeur immuable) et `paie_stc_reglement` (journal échelonné idempotent SP75/SP79). Prestataires indépendants et ISI général désactivé en 2026. | [`module-paie.md`](./module-paie.md) |
| 🔐 **Module Transversal Sécurité, IAM & Audit** | Sécurité & Traçabilité | Authentification JWT HS512, cookie refresh HttpOnly, un rôle principal par utilisateur et permissions granulaires, familles de refresh tokens par session, expiration après 900 s d'inactivité reconnue côté client et serveur, révocation sur expiration/déconnexion/désactivation, audit JSONB. | [`module-securite-audit.md`](./module-securite-audit.md) |

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
│   BACKEND (Spring Boot 4.1.0 / Java 17)                                                  │
│   ├── mg.galaxam.sirh.employe      → CRUD employé, documents RH, matricule séquentiel  │
│   ├── mg.galaxam.sirh.contrat      → ContratTravailController, CarriereTransition...   │
│   ├── mg.galaxam.sirh.organisation → OrganisationController (/referentiel), ref_*      │
│   ├── mg.galaxam.sirh.conge        → DemandeCongeController, CongeLotValorisation...   │
│   ├── mg.galaxam.sirh.pointage     → PointageSaisieController, TemporalAttendance...   │
│   ├── mg.galaxam.sirh.paie         → PayrollController, PayrollExecutionEngine, STC... │
│   ├── mg.galaxam.sirh.securite     → Authentification JWT, RBAC rôle + permissions    │
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
│   BASE DE DONNÉES (PostgreSQL 15)                                                      │
│   ├── Référentiels d'entreprise : ref_filiale, ref_site, ref_departement, ref_poste... │
│   ├── Configuration globale     : parametre_global (37 clés versionnées à date d'effet)│
│   ├── Dossier du personnel      : employe, contrat_travail, affectation_carriere       │
│   ├── Moteur de congés          : demande_conge, mouvement_solde_conge, conge_periode...│
│   ├── Moteur de présence        : pointage_jour, pointage_cumul_periode, snapshots...  │
│   ├── Moteur de rémunération    : periode_paie, bulletin_paie, paie_avance_acompte...  │
│   └── Audit et traçabilité      : audit_log, conge_expiration_examen, pointage_ajust... │
│                                                                                        │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

Les jobs utilisent `SchedulerExecutionRegistry` : possession de tentative par
jeton UUID et bail renouvelable, avec reprise après expiration. Ce verrou
coordonne la ligne d'exécution entre instances mais n'arrête pas un ancien
worker déjà engagé ; les traitements métier doivent garder leurs protections
d'idempotence propres. SP77 ajoute aussi l'unicité salarié/année des mouvements
de bonification.

---

## 3. Planification des Schedulers (Tâches Nocturnes)

| Heure | Scheduler Java | Fréquence | Action Réalisée | Base Légale / Justification |
|---|---|---|---|---|
| `00:00:00` | `MouvementRhScheduler` | Quotidien | Applique les mouvements RH dont la date d'effet est atteinte (mutations, promotions, titularisations, sorties). | Continuité du dossier salarié (Art. 18 Loi 2024-014). |
| `00:05:00` | `NotificationRhScheduler` | Quotidien | Génère les alertes de fin d'essai (J-30, J-15, J-7, J) et d'échéance de CDD. | Respect des préavis légaux d'essai (Art. 28 Loi 2024-014). |
| `00:10:00` | `PointageRappelScheduler` | Quotidien | Détecte les anomalies de pointage de la veille (hors dispenses, congés validés et fériés chômés). | Contrôle de la durée effective du travail (Art. 109). |
| `00:15:00` | `PeriodePaieScheduler` | Quotidien | Transfère automatiquement la période de pointage de `EN_COURS` vers `A_VALIDER` au lendemain de la date de fin (le 26 du mois). | Respect de l'échéance légale de paie mensuelle (Art. 138). |
| `00:20:00` | `FerieChomeScheduler` | Quotidien | Pré-crée les lignes `pointage_jour` en statut `FERIE_CHOME` pour les jours fériés légaux déclarés. | Décret n° 72-226 & Art. 122 Loi 2024-014. |
| `01:00:00` | `AcquisitionCongeScheduler` | Mensuel (1er) | Met à jour les bonifications d'ancienneté (+1 j/5 ans) du mois clos. | Art. 126 Loi 2024-014. |
| `03:30:00` | `SessionSecurityScheduler` | Quotidien | Révocation en base des refresh tokens de session expirés. | NIST SP 800-107 & Loi n° 2014-038. |
| — | *Examen Prescription* | Aucun cron | Les droits à congés de plus de 3 ans ne sont **jamais purgés unilatéralement** par script ; ils sont signalés pour audit contradictoire DAF/Salarié. | Art. 129 Loi 2024-014 (Prescription triennale). |

Les jobs utilisent `SchedulerExecutionRegistry` avec un jeton de possession et
un bail renouvelable depuis SP77. Une reprise protège le registre contre la
finalisation tardive d'un ancien worker, mais ne l'arrête pas ; les effets métier
doivent conserver leurs propres protections transactionnelles ou d'idempotence.

---

## 4. Synthèse de l'État des Réalisations Techniques

| Module du Système | État de Livraison | Couverture de Tests Automatisés | Conformité Légale Malgache |
|---|---|---|---|
| **1. Structure & Organisation** | ✅ 100% Opérationnel (Doc v5.0) | 7 tests backend (`mg.galaxam.sirh.organisation`) + 17 tests React | Décrets fiscaux & territoriaux, 32 ref_*, 37 paramètres |
| **2. Employé & Carrière** | ✅ 100% Opérationnel (Doc v5.0) | 54 tests backend (`mg.galaxam.sirh.contrat`) + 37 tests React | Loi 2024-014 Art. 18-42, Loi 2003-044, Contrats DOCX |
| **3. Congés & Absences** | ✅ 100% Opérationnel | 90 tests unitaires & intégration (`mg.galaxam.sirh.conge`) | Loi 2024-014 Art. 124-136, Décret 64-161, Moteur FIFO certifié |
| **4. Pointage & Présence** | ✅ 100% Opérationnel (Doc v5.0) | 93 tests backend (`mg.galaxam.sirh.pointage`) + 60 tests React | Loi 2024-014 Art. 109-123, Décret 72-226, 4 Snapshots immuables |
| **5. Paie & Déclarations** | ✅ 100% Opérationnel (Doc v5.6) | 189 tests backend passés (194 exécutés, 5 ignorés) + 56 tests React passés | Paramètres datés, Snapshot employeur scellé, Export Excel POI direct, Dossiers de sortie & STC gouverné SP81 |
| **6. Sécurité, IAM & Audit** | Parcours d'authentification et d'inactivité implémentés; validation automatisée ciblée disponible | Tests PostgreSQL isolés requis pour les scénarios de session; couverture et recette web/Tauri à distinguer | La documentation technique ne vaut pas attestation de conformité juridique |

---

*GALAXAM SIRH — Documentation de référence pour Mémoire de Fin d'Études d'Ingénierie Logicielle (2026)*
