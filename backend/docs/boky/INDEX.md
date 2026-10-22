# GALAXAM SIRH — Index de la Documentation Technique
## Mémoire de Fin d'Études

> **Projet** : GALAXAM SIRH — Système d'Information des Ressources Humaines  
> **Entreprise** : GALAXAM Logistics Madagascar  
> **Stack** : Java (Spring Boot 4.1.0) + PostgreSQL 15 + React.js / Vite  
> **Date de référence** : Octobre 2026  
> **Cadre Juridique** : [Loi n° 2024-014 du 14 août 2024 portant Code du Travail](https://cnlegis.gov.mg/uploads/L2024-014-VF.pdf) (adoptée le 01/07/2024, validée par la HCC Décision n° 11-HCC/D3 le 08/08/2024, promulguée le 14/08/2024) & Loi n° 2003-044, CGI LF 2026/2025, Décrets CNaPS/Santé, Décret n° 72-226, Décret n° 64-161, Loi 2017-025 (FMFP).  
> **Modèle & Norme Académique** : Template Officiel IT University (`MémoireITU-v3r4.docx`).

---

## Cadre du Projet, Glossaire & Références Académiques

- **Glossaire & Acronymes Légaux** : Acronymes légaux malgaches (CGI, CNaPS, FMFP, OSTIE/AMIT, SME, IRSA, STC, ICCP) et concepts techniques détaillés dans les six documents maîtres ci-dessous.
- **Cadre Académique** : Modèle de conception et de soutenance IT University (norme IMRAD, pagination cible, grille d'évaluation).
- **Contexte Terrain GALAXAM** : Conduite du projet chez GALAXAM Logistics Madagascar (projets MID/CSB, audit critique des anciens classeurs Excel et transition vers le système d'information intégré).

---

## 1. Documents Maîtres des Modules Métier

Les six documents ci-dessous sont les documents maîtres des six modules. Ils sont à lire avec les sources SQL, Java et les migrations indiquées. Les évolutions SP74–SP84 présentes dans les sources sont décrites dans les modules concernés.

| Document Maître | Module Métier | Principaux Composants Techniques & Périmètre | Fichier |
|---|---|---|---|
| **Module Employé, Contrat & Carrière** | Employé & Carrière | Dossier individuel (Art. 18-20), matricule séquentiel garanti, `ContratTravailController`, transitions de carrière (`CarriereTransitionValidator`), historique sans chevauchement, génération DOCX d'embauche et fiches. | [`module-employe-carriere.md`](./module-employe-carriere.md) |
| **Module Structure, Organisation & Administration** | Organisation & Référentiel | `OrganisationController` (`/referentiel`), 32 tables `ref_*`, 37 clés `parametre_global` versionnées à date d'effet, plancher d'arrivée par site (`ref_site`), gestion des filiales et devises. | [`module-structure-organisation.md`](./module-structure-organisation.md) |
| **Module Congés & Absences** | Congés & Absences | Moteur FIFO par lots de valorisation (`CongeLotValorisationService`), décompte calendaire dynamique (Art. 127), pool familial 10j (Art. 133), paternité 3j (Art. 134), examen DAF de prescription triennale (Art. 129), blocage du solde négatif (RG-CON-05), export Excel DAF 12 mois. | [`module-conges-absences.md`](./module-conges-absences.md) |
| **Module Pointage & Présence** | Pointage & Présence | Cycle paie décalé (26 au 25), plancher d'arrivée par site (`plancher_arrivee_actif`, `heure_entree_minimale_valorisee`), `TemporalAttendanceEngine` (qualification orthogonale nuit 22h-05h, dimanche, férié), ventilation hebdo (Art. 109-110), ajustements DAF de période (`PointageAjustementPeriodeService`), 4 tables de snapshots immuables. | [`module-pointage-presence.md`](./module-pointage-presence.md) |
| **Module Paie, Rémunération, Cotisations, IRSA & STC** | Paie & Rémunération | Campagne et bulletins scellés (`type_bulletin` unifié : `MENSUEL`, `STC`, `COMPLEMENTAIRE`, `RAPPEL`, `GRATIFICATION`), déclaratifs fiscaux/sociaux, cotisations, variables DAF et acomptes/avances en paie mensuelle (gestion des prêts purgée en oct. 2026). Le STC s'articule autour de `paie_dossier_sortie` (checklist, offboarding, certificat Art. 50, clôture dérogatoire SP81), `paie_stc` (décompte scellé, ICCP en FIFO congés, snapshot employeur immuable) et `paie_stc_reglement` (journal échelonné idempotent SP75/SP79). Prestataires indépendants et ISI général désactivé en 2026. | [`module-paie.md`](./module-paie.md) |
| **Module Transversal Sécurité, IAM & Audit** | Sécurité & Traçabilité | Authentification JWT HS512, cookie refresh HttpOnly, un rôle principal par utilisateur et permissions granulaires, familles de refresh tokens par session, expiration après 900 s d'inactivité reconnue côté client et serveur, révocation sur expiration/déconnexion/désactivation, audit JSONB. | [`module-securite-audit.md`](./module-securite-audit.md) |

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
│   ├── mg.galaxam.sirh.contrat      → Dossier collaborateur, contrats, carrières, DOCX   │
│   ├── mg.galaxam.sirh.organisation → OrganisationController (/referentiel), ref_*      │
│   ├── mg.galaxam.sirh.conge        → DemandeCongeController, CongeLotValorisation...   │
│   ├── mg.galaxam.sirh.pointage     → PointageSaisieController, TemporalAttendance...   │
│   ├── mg.galaxam.sirh.paie         → PayrollController, PayrollExecutionEngine, STC... │
│   ├── mg.galaxam.sirh.paieconfig   → Barèmes légaux, tranches IRSA, cotisations datées  │
│   ├── mg.galaxam.sirh.admin        → Gestion profils, rôles RBAC et menus              │
│   ├── mg.galaxam.sirh.securite     → Authentification JWT, RBAC rôle + permissions    │
│   └── Schedulers Nocturnes (@Scheduled Spring Boot)                                   │
│       ├── MouvementRhScheduler        00:10:00 quotidien (Application mutations/promos)│
│       ├── AcquisitionCongeScheduler   01:00:00 mensuel   (Bonification ancienneté)     │
│       ├── NotificationRhScheduler     02:30:00 quotidien (Alertes contrats/essais)    │
│       ├── SessionSecurityScheduler    03:30:00 quotidien (Purge refresh tokens expirés)│
│       ├── AuditLogPurgeScheduler      04:00:00 mensuel   (Purge traces > 36 mois)      │
│       ├── RattrapageAutomatiqueService 15 min récurrent  (Rattrapage autonome baux)    │
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
worker déjà engagé ; les traitements métier gardent leurs protections
d'idempotence propres.

---

## 3. Planification des Schedulers (Tâches Automatisées)

| Heure | Scheduler Java | Fréquence | Action Réalisée | Base Légale / Justification |
|---|---|---|---|---|
| `00:10:00` | `MouvementRhScheduler` | Quotidien | Applique les mouvements RH dont la date d'effet est atteinte (mutations, promotions, titularisations, sorties). | Continuité du dossier salarié (Art. 18 Loi 2024-014). |
| `01:00:00` | `AcquisitionCongeScheduler` | Mensuel (1er) | Calcule et impute les bonifications d'ancienneté (+1 j/5 ans) du mois clos. | Art. 126 Loi 2024-014. |
| `02:30:00` | `NotificationRhScheduler` | Quotidien | Génère les alertes de fin d'essai (J-30, J-15, J-7, J) et d'échéance de CDD. | Respect des préavis légaux d'essai (Art. 28 Loi 2024-014). |
| `03:30:00` | `SessionSecurityScheduler` | Quotidien | Révocation en base des refresh tokens de session expirés. | NIST SP 800-107 & Loi n° 2014-038. |
| `04:00:00` | `AuditLogPurgeScheduler` | Mensuel (1er) | Purge légale des journaux d'audit de plus de 36 mois. | Loi 2024-014 Art. 129 & Traçabilité légale. |
| `15 min` | `RattrapageAutomatiqueService` | Récurrent (`fixedDelay`) | Surveillance et reprise autonome des baux et exécutions asynchrones échues. | Résilience et haute disponibilité du cluster. |
| — | *Examen Prescription* | Aucun cron | Les droits à congés de plus de 3 ans ne sont **jamais purgés unilatéralement** par script ; ils sont soumis à audit contradictoire DAF/Salarié. | Art. 129 Loi 2024-014 (Prescription triennale). |

---

## 4. Synthèse de l'État des Réalisations Techniques

| Module du Système | État de Livraison | Validation Automatisée & Assurance Qualité | Conformité Légale Malgache |
|---|---|---|---|
| **1. Structure & Organisation** | 100% Opérationnel | Validé via `ContratTravailControllerPostgresTest` & schémas SQL canoniques | Décrets fiscaux & territoriaux, 32 ref_*, 37 paramètres |
| **2. Employé & Carrière** | 100% Opérationnel | Validé via `ContratTravailControllerPostgresTest`, `EmployeSatelliteLifecycleTest` | Loi 2024-014 Art. 18-42, Loi 2003-044, Contrats DOCX |
| **3. Congés & Absences** | 100% Opérationnel | Moteur FIFO par lots et grand-livre comptable certifiés | Loi 2024-014 Art. 124-136, Décret 64-161, Moteur FIFO certifié |
| **4. Pointage & Présence** | 100% Opérationnel | Validé via `PayrollPeriodTransitionTargetedTest`, `PayrollPeriodMigrationTargetedTest`, `CalendrierFeriePostgresTest` | Loi 2024-014 Art. 109-123, Décret 72-226, 4 Snapshots immuables |
| **5. Paie & Déclarations** | 100% Opérationnel | Validé via `PayrollCalculationTargetedTest` (8/8 tests ciblés), moteur STC & exports bancaires/SAFI | Paramètres datés, Snapshot employeur scellé, Export Excel POI direct, Dossiers de sortie & STC gouverné SP81 |
| **6. Sécurité, IAM & Audit** | 100% Opérationnel | Validé via `IdleSessionServicePostgresTest` + suite frontend test:api (14/14 tests) | Authentification HS512, Refresh HttpOnly, Inactivité 900s, Audit JSONB |

---

*GALAXAM SIRH — Documentation de référence pour Mémoire de Fin d'Études d'Ingénierie Logicielle (2026)*
