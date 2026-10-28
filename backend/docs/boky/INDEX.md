# GALAXAM SIRH — Index de la Documentation Technique
## Mémoire de Fin d'Études d'Ingénierie Logicielle

> **Projet** : GALAXAM SIRH — Système d'Information des Ressources Humaines  
> **Entreprise** : GALAXAM Logistics Madagascar  
> **Stack déclarée** : Java 17 / Spring Boot 4.1.0, PostgreSQL 15, React 19.2.7 / Vite 8.1.1 / React Router 7.18.2, Tauri 2. Les versions frontend exactes proviennent de `frontend/package.json` et de son fichier lock ; la version PostgreSQL 15-alpine de Compose ne fixe pas un numéro de correctif.
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

Les six documents ci-dessous décrivent les modules du système. Les six fichiers SQL canoniques de `backend/conception/bdd/` — `01_schema_core_securite.sql`, `02_schema_referentiels_rh.sql`, `03_schema_employes_contrats.sql`, `04_schema_conges.sql`, `05_schema_pointage.sql` et `06_schema_paie.sql` — sont la référence pour initialiser une base neuve. Les anciennes migrations SP01–SP84 ont été volontairement supprimées ; les identifiants SP éventuellement conservés dans les documents ne servent qu'à la traçabilité historique. Les schémas canoniques ne décrivent pas une mise à niveau d'une base existante et aucun mécanisme automatique d'installation ou de migration n'est documenté ici. UX-15 (`scripts/recette/UX15.md`) décrit une initialisation de recette jetable à partir des six schémas.

Pour activer la reprise des congés par lots sur une base déjà initialisée, exécuter une fois `backend/conception/bdd/upgrade_conge_lots_reprise.sql` avant de déployer le backend correspondant.

| Document Maître | Module Métier | Principaux Composants Techniques & Périmètre | Fichier |
|---|---|---|---|
| **Module Employé, Contrat & Carrière** | Employé & Carrière | Dossier individuel pérenne (Art. 18-20), matricule séquentiel garanti, `ContratTravailController`, transitions de carrière (`CarriereTransitionValidator`), historique sans chevauchement, génération DOCX d'embauche et certificats de travail POI, GED documentaire, supervision de carrière `/carrieres`. | [`module-employe-carriere.md`](./module-employe-carriere.md) |
| **Module Structure, Organisation & Administration** | Organisation & Référentiel | `OrganisationController` (`/referentiel`), 15 tables `ref_*` définies dans le schéma 02 (30 déclarations `ref_*` sur les six schémas canoniques), 37 clés `parametre_global` versionnées à date d'effet (triétat `ResolutionStatus`), plancher d'arrivée par site (`ref_site`), gestion des filiales, devises, banques et catégories professionnelles. | [`module-structure-organisation.md`](./module-structure-organisation.md) |
| **Module Congés & Absences** | Congés & Absences | Moteur FIFO par lots de valorisation (`CongeLotValorisationService`, simulation de prise `/simuler-valorisation`), décompte calendaire dynamique (Art. 127), pool familial 10j (Art. 133), paternité 3j (Art. 134), examen DAF de prescription triennale (Art. 129), blocage absolu du solde négatif (RG-CON-05), export Excel DAF 12 mois. | [`module-conges-absences.md`](./module-conges-absences.md) |
| **Module Pointage & Présence** | Pointage & Présence | Convention de période généralement du 26 au 25 (bornes saisies, non imposées universellement), étanchéité SQL des journées/périodes, plancher d'arrivée valorisée par site (distinct du retard), `TemporalAttendanceEngine` (qualification nuit, dimanche, férié), ventilation hebdomadaire, ajustements DAF signés et snapshots versionnés avec états `GELE`/`REMPLACE`/`CONSOMME`. | [`module-pointage-presence.md`](./module-pointage-presence.md) |
| **Module Paie, Rémunération & Cotisations** | Paie & Rémunération | Campagnes et bulletins, déclaratifs fiscaux/sociaux, variables, acomptes/avances, règlements et prestataires. **Extension postérieure au CDC :** le STC et les dossiers de sortie sont documentés dans `module-paie.md` ; ils ne figurent pas dans le périmètre initial du CDC. | [`module-paie.md`](./module-paie.md) |
| **Module Transversal Sécurité, IAM & Audit** | Sécurité, IAM & Traçabilité | Connexion par email, access token JWT HS512, refresh token en cookie HttpOnly et familles stockées dans `utilisateur_session` ; 2 rôles seedés (`ROLE_ADMIN` et `ROLE_AGENT_POINTAGE`), 25 menus seedés (6 racines + 19 sous-menus), inactivité par défaut de 900 s, révocation des sessions à la désactivation, audit JSONB et support desktop SCM / supervisor. | [`module-securite-audit.md`](./module-securite-audit.md) |

---

## 2. Architecture Globale du Système

```
┌────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                              GALAXAM SIRH                                              │
├────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                                        │
│   FRONTEND (React 19 / Vite / Tailwind CSS v4 / Lucide / React Router v7)                              │
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
│   ├── mg.galaxam.sirh.organisation → Contrôleur unifié (/referentiel), référentiels définis au schéma 02│
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
│   ├── 05_schema_pointage.sql          → periode_paie, transitions, pointage et snapshots versionnés    │
│   └── 06_schema_paie.sql              → bulletin_paie et tables Paie, dont l'extension STC             │
│                                                                                                        │
└────────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

Le navigateur utilise l'API REST Spring Boot sous `/api/`. En développement Web, Docker Compose lance Vite, dont le proxy relaie `/api` vers le backend sur le réseau Compose. L'image de production sert le bundle React avec Nginx ; sa configuration relaie `/api/` vers `backend:8080` sans retirer ce préfixe. L'authentification utilise JWT signé HS512 et un cookie de renouvellement HttpOnly ; les règles de session et de permissions sont décrites dans [`module-securite-audit.md`](./module-securite-audit.md). Le stockage des pièces jointes est un répertoire local monté ou configuré, et non un service objet externe.

Les versions déclarées dans les manifestes sont : backend Java 17 et Spring Boot 4.1.0 (`backend/pom.xml`) ; React 19.2.7, React Router 7.18.2, Vite 8.1.1 et Tailwind CSS 4.3.3 (`frontend/package.json`) ; images Node 22 et Nginx 1.27 dans les Dockerfiles frontend ; Tauri 2 et Rust/Cargo pour le desktop. Le Compose Web utilise `postgres:15-alpine`, tandis que le guide du bundle macOS documente PostgreSQL 15.19. Ces fichiers décrivent les versions de construction, pas une matrice de compatibilité certifiée.

Les jobs planifiés s'appuient sur `SchedulerExecutionRegistry` : acquisition d'une tentative par clé composite `(job_code, execution_key)` avec jeton UUID, bail initial de 30 minutes renouvelé toutes les 10 minutes et limite d'exécution de 23 heures. Une erreur de renouvellement ne prolonge pas le bail. La reprise après expiration et la traçabilité sont enregistrées dans `scheduler_execution`. Le bail réduit le risque de double exécution, mais ne l'exclut pas absolument : si un worker perd son bail, son traitement déjà démarré n'est pas interrompu automatiquement et peut chevaucher une tentative reprise.

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
| **Congés & Absences** | Demandes, acquisitions, mouvements et moteur de valorisation FIFO | `CongeLotValorisationEngineTest`, `CongePostgresIntegrationTest`, `PayrollLeaveAcquisitionPostgresIntegrationTest` | FIFO simple observé selon UX-17 communiqué séparément ; FIFO multi-lots non démontré en recette navigateur ; acquisition mensuelle observée selon UX-18 communiqué séparément ; TCONGE annuel non vérifié de bout en bout |
| **Pointage & Présence** | Workflow de période et génération de snapshots | `PayrollPeriodTransitionTargetedTest`, `PayrollPeriodMigrationTargetedTest` | UX-15 (`scripts/recette/UX15.md`) démontre le scénario jusqu'au snapshot ; le document précise qu'il s'arrête avant le calcul de paie |
| **Paie & Rémunération** | Calcul, validation, clôture, bulletins et exports | `PayrollCalculationTargetedTest` et tests de workflow présents dans le dépôt | UX-16 communiqué séparément rapporte validation, clôture, bulletin et vérification technique des exports ; aucun résultat ne certifie la conformité réglementaire des montants |
| **Sécurité, IAM & Audit** | JWT, permissions granulaires, familles de session et audit | `UtilisateurPasswordChangeTest`, `IdleSessionServicePostgresTest`, `LoginAttemptRateLimiterTest`, `frontend/test/idleSession.test.js` et `frontend/test/roleGatedNavigation.test.js` | Le test du contrôleur vérifie la révocation conditionnelle et le rollback en cas d'échec. Les autres ciblent l'inactivité, la rotation, le limiteur de connexion et la navigation ; ils ne valident pas le parcours HTTP/navigateur complet ni toutes les combinaisons de rôles et permissions |
| **Dashboard & Notifications** | Endpoint d'agrégation et routes de notification | Couverture générale non évaluée dans DOC-2 | Présence dans le code ne vaut pas recette fonctionnelle complète |

---

## 5. Exécution, initialisation et déploiement

### Développement Web avec Docker Compose

Le mode local est défini par `docker-compose.dev.yml` et les Dockerfiles : PostgreSQL 15, Spring Boot lancé par Maven en profil `local`, et Vite en mode développement. Sur le PC Windows configuré, le frontend est accessible sur `127.0.0.1:18088`, PostgreSQL sur `127.0.0.1:5433` ; le backend n'a pas de port hôte publié. La base, les fichiers métier, le dépôt Maven et les modules Node utilisent des volumes nommés. Le lancement passe par `scripts/compose dev` sur macOS/Linux ou `scripts/windows-local/compose.ps1 -Mode dev` sur Windows, après définition des variables utilisateur `SIRH_DEV_*`.

Au premier démarrage seulement, l'entrypoint de l'image PostgreSQL exécute `docker/init-local.sql` si le répertoire de données est vide. Ce script inclut les six fichiers SQL dans cet ordre : `01_schema_core_securite.sql`, `02_schema_referentiels_rh.sql`, `03_schema_employes_contrats.sql`, `04_schema_conges.sql`, `05_schema_pointage.sql`, `06_schema_paie.sql`. Ils créent les structures, contraintes, vues, référentiels et données initiales ; le schéma 03 contient aussi deux dossiers salariés historiques d'exemple. Une base ou un volume déjà initialisé n'est pas mis à niveau par ce mécanisme. Vérifier les données d'exemple avant d'utiliser une base opérationnelle.

`docker/init-databases.sql` est un script `psql` distinct qui crée une base de test puis applique les mêmes six fichiers sur la base applicative et la base de test. Il n'est pas monté par le Compose Web principal et ne constitue pas une migration progressive. Aucun Flyway, Liquibase ou mécanisme général de mise à niveau de schéma n'est déclaré dans le backend. Les schémas canoniques sont une référence de création de base neuve, pas une procédure de conversion d'une base existante. Le bootstrap conditionnel du compte administrateur est une opération séparée au démarrage Spring et ne remplace pas l'initialisation PostgreSQL.

### Exécution native de développement

Le README racine décrit `mvn -f backend/pom.xml spring-boot:run` et `npm run dev` depuis `frontend/`. Le backend et le frontend nécessitent chacun une base/configuration API accessibles ; lancer ces commandes ne crée pas les six schémas. Ce parcours est documenté pour le développement et n'est pas une procédure de déploiement.

### Application desktop Tauri

| Cible / mode | Implémentation et preuve documentaire | Limites et qualification |
|---|---|---|
| macOS ARM64, services locaux | Tauri 2, React, Spring Boot Java 17 et PostgreSQL 15.19 ; `cd desktop && npm ci && npm run build` produit un `.app` et un `.dmg` selon `desktop/README.md`. Le code initialise un cluster PostgreSQL absent avec les six schémas dans l'ordre 01–06 et conserve base, documents, configuration et journaux sous `~/Library/Application Support/GALAXAM SIRH/`. Le guide décrit le lancement via LaunchAgent, les sauvegardes locales et la restauration. | Le guide indique que le DMG n'est ni signé Developer ID ni notarisé. Les mesures de charge et l'installation sur différentes versions de macOS ne sont pas établies ici ; DOC-3 n'a pas exécuté le build ni l'installation. |
| Desktop connecté à un backend Web | L'application peut pointer vers une API distante ; le profil `desktop-api` dans `docker-compose.desktop.yml` fournit aussi un relais local vers le backend Compose. Ce mode ne crée pas de PostgreSQL local. | La disponibilité distante dépend du déploiement Web et de sa configuration réseau. |
| Windows x64 | Les sources comprennent `desktop/scripts/build-windows.ps1`, les implémentations de plateforme Windows et `.github/workflows/windows-desktop.yml`. `desktop/WINDOWS_SPRINTS.md` rapporte des validations automatisées sur Windows Server 2022 pour le packaging, le service et le cycle de sauvegarde/restauration. | Ces résultats CI ne valident pas l'usage interactif sur Windows 10/11. Le document de portage liste encore des essais OS, session, premier administrateur et diffusion à réaliser ; ne pas présenter Windows comme entièrement qualifié ou prêt à diffuser. |

### Compose de production et exploitation

`docker-compose.web.yml` construit un backend empaqueté et sert le frontend statique par Nginx. Le frontal est lié à `127.0.0.1` sur le port `SIRH_PROD_WEB_PORT`, le backend reste sur le réseau Compose et PostgreSQL n'a pas de port hôte. Le profil exige une origine CORS et un secret JWT propres à l'environnement. Les volumes séparent les données PostgreSQL (`galaxam_prod_pg_data`) et les fichiers métier (`galaxam_prod_business_files`).

Les commandes `scripts/compose prod config` et `scripts/compose prod` valident et démarrent ce mode sur macOS/Linux ; le script PowerShell joue le même rôle sur Windows. Elles ne configurent pas un proxy TLS externe ni un déploiement public. Les politiques d'accès au stockage, de sauvegarde, de restauration, de rotation des secrets et de reprise restent propres à l'installation cible.

*GALAXAM SIRH — Documentation de référence pour Mémoire de Fin d'Études d'Ingénierie Logicielle (2026)*
