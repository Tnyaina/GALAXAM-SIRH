# GALAXAM SIRH — Index de la Documentation Technique
## Mémoire de Fin d'Études d'Ingénierie Logicielle

> **Projet** : GALAXAM SIRH — Système d'Information des Ressources Humaines  
> **Entreprise** : GALAXAM Logistics Madagascar  
> **Stack** : Java 17 (Spring Boot 4.1.0) + PostgreSQL 15 + React.js 19 / Vite + Tauri Desktop
> **Date de référence** : Octobre 2026  
> **Cadre Juridique** : [Loi n° 2024-014 du 14 août 2024 portant Code du Travail](https://cnlegis.gov.mg/uploads/L2024-014-VF.pdf) (adoptée le 01/07/2024, validée par la HCC Décision n° 11-HCC/D3 le 08/08/2024, promulguée le 14/08/2024) & Loi n° 2003-044, CGI LF 2026/2025, Décrets CNaPS/Santé, Décret n° 72-226, Décret n° 64-161, Loi 2017-025 (FMFP), Loi n° 2014-038 (Données personnelles).
> **Modèle & Norme Académique** : Template Officiel IT University (`MémoireITU-v3r4.docx`).

**Référence de périmètre :** le CDC initial du projet mémoire est disponible dans le projet de mémoire voisin, `GALAXAM/boky/context/CAHIER_DES_CHARGES_GALAXAM.md` ; il n'est pas inclus dans ce dépôt logiciel. Il définit le périmètre fonctionnel initial ; les extensions ultérieures sont décrites comme telles. Le CDC indique une installation locale Docker Compose envisagée, dont l'installation et la recette sur le poste de l'entreprise restaient à confirmer ; la présence de fichiers de déploiement ne prouve pas cette qualification.

---

## Cadre du Projet, Glossaire & Références Académiques

- **Glossaire & Acronymes Légaux** : Acronymes légaux malgaches (CGI, CNaPS, FMFP, OSTIE/AMIT, SME, IRSA, STC, ICCP) et concepts techniques détaillés dans les six documents maîtres ci-dessous.
- **Cadre Académique** : Modèle de conception et de soutenance IT University (norme IMRAD, rigueur de modélisation relationnelle, traçabilité des règles de gestion, grille d'évaluation d'ingénierie logicielle).
- **Contexte Terrain GALAXAM** : Conduite du projet chez GALAXAM Logistics Madagascar (projets MID/CSB, audit critique des anciens classeurs Excel monolithiques et transition vers le système d'information intégré unifié).

---

## 1. Documents Maîtres des Modules Métier

Les six documents ci-dessous décrivent les modules du système. Pour créer une base neuve, les six schémas SQL canoniques (`01_schema_core_securite.sql` à `06_schema_paie.sql`) sont disponibles dans `backend/conception/bdd`. Les scripts de migration SP01 à SP84 ne sont pas présents dans ce dépôt ; les identifiants SP encore cités dans les documents ne constituent donc pas, à eux seuls, une procédure d'installation ou de mise à niveau reproductible. UX-15 (`scripts/recette/UX15.md`) décrit une initialisation de recette jetable à partir des six schémas.

| Document Maître | Module Métier | Principaux Composants Techniques & Périmètre | Fichier |
|---|---|---|---|
| **Module Employé, Contrat & Carrière** | Employé & Carrière | Dossier individuel pérenne (Art. 18-20), matricule séquentiel garanti, `ContratTravailController`, transitions de carrière (`CarriereTransitionValidator`), historique sans chevauchement, génération DOCX d'embauche et certificats de travail POI, GED documentaire, supervision de carrière `/carrieres`. | [`module-employe-carriere.md`](./module-employe-carriere.md) |
| **Module Structure, Organisation & Administration** | Organisation & Référentiel | `OrganisationController` (`/referentiel`), 32 tables `ref_*`, 37 clés `parametre_global` versionnées à date d'effet (triétat `ResolutionStatus`), plancher d'arrivée par site (`ref_site`), gestion des filiales, devises, banques et catégories professionnelles. | [`module-structure-organisation.md`](./module-structure-organisation.md) |
| **Module Congés & Absences** | Congés & Absences | Moteur FIFO par lots de valorisation (`CongeLotValorisationService`, simulation de prise `/simuler-valorisation`), décompte calendaire dynamique (Art. 127), pool familial 10j (Art. 133), paternité 3j (Art. 134), examen DAF de prescription triennale (Art. 129), blocage absolu du solde négatif (RG-CON-05), export Excel DAF 12 mois. | [`module-conges-absences.md`](./module-conges-absences.md) |
| **Module Pointage & Présence** | Pointage & Présence | Cycle paie décalé (26 au 25), étanchéité de période SP80 (`ex_periode_paie_dates`), plancher d'arrivée par site (`plancher_arrivee_actif`, `heure_entree_minimale_valorisee`), `TemporalAttendanceEngine` (qualification orthogonale nuit 22h-05h, dimanche, férié), ventilation hebdo (Art. 109-110), ajustements DAF signés, 4 snapshots immuables. | [`module-pointage-presence.md`](./module-pointage-presence.md) |
| **Module Paie, Rémunération & Cotisations** | Paie & Rémunération | Campagnes et bulletins, déclaratifs fiscaux/sociaux, variables, acomptes/avances, règlements et prestataires. **Extension postérieure au CDC :** le STC et les dossiers de sortie sont documentés dans `module-paie.md` ; ils ne figurent pas dans le périmètre initial du CDC. | [`module-paie.md`](./module-paie.md) |
| **Module Transversal Sécurité, IAM & Audit** | Sécurité, IAM & Traçabilité | Authentification JWT HS512, cookie refresh HttpOnly, 2 rôles seedés (`ROLE_ADMIN` et `ROLE_AGENT_POINTAGE`), 25 menus seedés (6 racines + 19 sous-menus), familles de refresh tokens par session, expiration après 900 s d'inactivité reconnue client et serveur (`utilisateur_session`), révocation en cascade, audit JSONB, support desktop SCM / supervisor. | [`module-securite-audit.md`](./module-securite-audit.md) |

---

## 2. Architecture Globale du Système

```
┌────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                              GALAXAM SIRH                                              │
├────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                                        │
│   FRONTEND (React.js 19 / Vite / TailwindCSS / Lucide Icons / React Router v6)                         │
│   ├── /dashboard                  → Tableau de bord exécutif, KPIs consolidés, alertes RH              │
│   ├── /employes, /carrieres       → Dossiers individuels, contrats, avenants, carrières, GED           │
│   ├── /conges                     → Demandes, validation, solde FIFO, simulation lots, prescription    │
│   ├── /pointages                  → Saisie quotidienne/période, cycle 26-25, anomalies, arbitrage DAF  │
│   ├── /paie                       → Cockpit mensuel et 7 étapes Paie (préparation à sorties)           │
│   │   ├── /paie/dossiers-sortie   → Orchestration des départs, décompte STC scellé, journal règlements │
│   │   ├── /paie/acomptes-avances  → Validation des acomptes/avances et suivi des imputations           │
│   │   ├── /paie/prestataires      → Contrats et facturation des intervenants indépendants              │
│   │   └── /paie/configuration     → Matrice des rubriques, cotisations et barèmes fiscaux              │
│   ├── /admin                      → Sécurité (/securite), Organisation (/organisation),                │
│   │                                 Référentiels RH (/referentiels-rh), Paramètres (/parametres)       │
│   └── /settings/connection        → Configuration réseau et amorçage du client desktop local           │
│                                                                                                        │
│   BACKEND (Spring Boot 4.1.0 / Java 17 / Maven / HikariCP / Apache POI)                                │
│   ├── mg.galaxam.sirh.dashboard    → Synthèse transverse KPIs (/dashboard/summary), notifications      │
│   ├── mg.galaxam.sirh.contrat      → Dossier salarié, contrats, carrières, transition, DOCX POI        │
│   ├── mg.galaxam.sirh.organisation → Contrôleur unifié (/referentiel), 32 tables ref_*                 │
│   ├── mg.galaxam.sirh.conge        → Moteur FIFO (CongeLotValorisationEngine), grand-livre comptable   │
│   ├── mg.galaxam.sirh.pointage     → Qualification temporelle (TemporalAttendanceEngine), 4 snapshots  │
│   ├── mg.galaxam.sirh.paie         → Calcul des bulletins, règlements et parcours prestataires         │
│   ├── mg.galaxam.sirh.paieconfig   → Barèmes fiscaux, IRSA, cotisations datées, paramètres globaux     │
│   ├── mg.galaxam.sirh.admin        → Rôles, permissions RBAC, menus, hooks superviseur desktop         │
│   ├── mg.galaxam.sirh.securite     → Authentification JWT HS512, sessions inactives (900s), audit JSONB│
│   └── Schedulers Nocturnes (@Scheduled Spring Boot coordonnés par SchedulerExecutionRegistry)          │
│       ├── MouvementRhScheduler        00:10:00 quotidien (Application mutations, promotions, fins)     │
│       ├── AcquisitionCongeScheduler   01:00:00 mensuel (Bonification d'ancienneté)                  │
│       ├── NotificationRhScheduler     02:30:00 quotidien (Alertes contrats, essais, congés, pointages) │
│       ├── SessionSecurityScheduler    03:30:00 quotidien (Purge refresh tokens et sessions expirées)   │
│       ├── AuditLogPurgeScheduler      04:00:00 mensuel   (Purge légale des traces > 36 mois)           │
│       ├── RattrapageAutomatiqueService 15 min récurrent  (Surveillance et reprise des baux de jobs)    │
│       └── (NB : Aucun cron de purge automatique des congés : examen humain DAF contradictoire strict)  │
│                                                                                                        │
│   BASE DE DONNÉES (PostgreSQL 15 / btree_gist)                                                         │
│   ├── 01_schema_core_securite.sql     → ref_role, utilisateur, session, audit_log, parametre_global   │
│   ├── 02_schema_referentiels_rh.sql   → ref_filiale, ref_site, ref_departement, ref_fonction, statuts   │
│   ├── 03_schema_employes_contrats.sql → employe, contrat_travail, affectation_carriere, workflow_rh    │
│   ├── 04_schema_conges.sql            → demande_conge, mouvement_solde_conge, conge_journal_droit     │
│   ├── 05_schema_pointage.sql          → pointage_jour, snapshots immuables, pointage_ajustement_per   │
│   └── 06_schema_paie.sql              → periode_paie, bulletin_paie et tables Paie, dont l'extension STC│
│                                                                                                        │
└────────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

Les jobs planifiés s'appuient sur `SchedulerExecutionRegistry` : acquisition d'une tentative par clé composite `(job_code, execution_key)` avec jeton UUID et bail renouvelable (`lease_until`), reprise automatique après expiration et traçabilité dans `scheduler_execution`.

---

## 3. Planification des Schedulers (Tâches Automatisées)

| Heure | Scheduler Java | Fréquence | Action Réalisée | Base Légale / Justification |
|---|---|---|---|---|
| `00:10:00` | `MouvementRhScheduler` | Quotidien | Applique les mouvements RH dont la date d'effet est atteinte (mutations de site/service, promotions, titularisations, sorties d'effectif). | Continuité du dossier salarié (Art. 18 Loi 2024-014). |
| `01:00:00` | `AcquisitionCongeScheduler` | Mensuel (1er) | Vérifie les anniversaires d'ancienneté du mois échu et crédite les bonifications admissibles. L'acquisition mensuelle standard est enregistrée à la clôture de paie dans sa transaction. | Art. 126 Loi 2024-014. |
| `02:30:00` | `NotificationRhScheduler` | Quotidien | Génère les alertes persistées en base : fins d'essai (J-30, J-15, J-7, J), échéances CDD, contrats échus non régularisés, brouillons 48h, demandes congés 48h, pièces manquantes GED, solde congé faible, retours du jour, maladies prolongées et prescription triennale imminente. | Anticipation légale des délais de préavis (Art. 28 Loi 2024-014). |
| `03:30:00` | `SessionSecurityScheduler` | Quotidien | Révocation physique des refresh tokens expirés et nettoyage des sessions inactives. | NIST SP 800-107 & Loi n° 2014-038. |
| `04:00:00` | `AuditLogPurgeScheduler` | Mensuel (1er) | Purge les journaux d'audit antérieurs au seuil calendaire de 36 mois. | Politique de conservation & traçabilité. |
| `15 min` | `RattrapageAutomatiqueService` | Récurrent (`fixedDelay`) | Au démarrage puis périodiquement, reprend les balayages quotidiens non terminés, les mois de bonification manqués depuis la première période enregistrée (six par cycle), et la purge d'audit du mois courant. Le registre SQL déduplique les exécutions terminées. | Résilience et reprise après incident. |

Le premier lancement de la bonification traite uniquement le dernier mois échu : aucun historique antérieur n'est déduit sans trace dans `scheduler_execution`. Après ce premier lancement, les mois absents entre la première période enregistrée et le dernier mois échu sont détectés. Les alertes RH et le nettoyage de sécurité balayent l'état courant après une indisponibilité ; ils ne reconstruisent pas chaque jour historique. Le rattrapage attend l'heure prévue du job le jour de son échéance et vérifie à nouveau les jobs toutes les 15 minutes, même si un cycle précédent a réussi.

**Examen de prescription (sans cron)** : les droits à congés de plus de 3 ans ne sont **jamais purgés unilatéralement** par script ; ils sont soumis à audit contradictoire DAF/Salarié (`conge_expiration_examen`), au titre de l'article 129 de la Loi 2024-014.

---

## 4. Synthèse de l'État des Réalisations Techniques

Les éléments ci-dessous décrivent des implémentations et des preuves repérées dans le dépôt ; ils ne constituent ni une mesure de complétude, ni une certification réglementaire, ni une recette exhaustive. Les résultats UX communiqués séparément sont signalés comme tels et ne sont pas assimilés à des rapports archivés dans le dépôt.

| Module | Implémentation repérée | Tests automatisés repérés | État de recette / limite |
|---|---|---|---|
| **Structure & Organisation** | Contrôleur d'organisation et six schémas canoniques | Aucun test spécifique de recette Organisation identifié dans les références citées précédemment | Contrôles de permission rapportés dans les phases UX-20/21 communiquées par l'utilisateur ; pas de recette générale archivée ici |
| **Employé & Carrière** | Employés, contrats, carrière et documents RH | `ContratTravailControllerPostgresTest`, `EmployeSatelliteLifecycleTest` | Ces tests ciblent des comportements ; ils ne démontrent pas la recette de toutes les interfaces |
| **Congés & Absences** | Demandes, acquisitions, mouvements et moteur de valorisation FIFO | `CongeLotValorisationEngineTest`, `CongePostgresIntegrationTest`, `PayrollLeaveAcquisitionPostgresIntegrationTest` | FIFO simple observé selon UX-17 communiqué séparément ; FIFO multi-lots non démontré ici ; acquisition mensuelle observée selon UX-18 communiqué séparément ; TCONGE annuel non vérifié de bout en bout |
| **Pointage & Présence** | Workflow de période et génération de snapshots | `PayrollPeriodTransitionTargetedTest`, `PayrollPeriodMigrationTargetedTest` | UX-15 (`scripts/recette/UX15.md`) démontre le scénario jusqu'au snapshot ; le document précise qu'il s'arrête avant le calcul de paie |
| **Paie & Rémunération** | Calcul, validation, clôture, bulletins et exports | `PayrollCalculationTargetedTest` et tests de workflow présents dans le dépôt | UX-16 communiqué séparément rapporte validation, clôture, bulletin et vérification technique des exports ; aucun résultat ne certifie la conformité réglementaire des montants |
| **Sécurité, IAM & Audit** | JWT, permissions granulaires, session et audit | `IdleSessionServicePostgresTest`, tests frontend de navigation/permissions présents dans `frontend/test` | Les tests et profils UX rapportés ne couvrent pas toutes les combinaisons de rôles ; seuls les profils et actions explicitement décrits dans les rapports peuvent être considérés comme vérifiés |
| **Dashboard & Notifications** | Endpoint d'agrégation et routes de notification | Couverture générale non évaluée dans DOC-2 | Présence dans le code ne vaut pas recette fonctionnelle complète |

---

*GALAXAM SIRH — Documentation de référence pour Mémoire de Fin d'Études d'Ingénierie Logicielle (2026)*
