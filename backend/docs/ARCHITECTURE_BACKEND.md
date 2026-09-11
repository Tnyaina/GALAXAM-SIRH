# Guide d'Architecture Backend : GALAXAM SIRH

## 1. Principes Fondateurs

Le backend du SIRH GALAXAM applique deux règles architecturales strictes :
1. **100% de l'intelligence et des règles de calcul en code Java pur (Domain Engines).**
2. **La base de données SQL ne sert qu'au stockage persistant et aux jointures relationnelles simples (vues `*CPL`).**

---

## 2. Organisation par Domaine et Sous-packages `engine`

Chaque domaine fonctionnel encapsule ses calculs dans un sous-package `*.engine` :

```
mg.galaxam.sirh.model
├── employe
│   ├── engine
│   │   ├── EmployeValidationEngine.java    # Contrôle de l'âge (>= 16 ans), CIN 12 chiffres, RIB
│   │   └── MatriculeGeneratorEngine.java   # Format séquentiel historique et préfixé
│   ├── service
│   │   └── EmployeService.java
│   └── Employe.java
├── contrat
│   ├── engine
│   │   ├── ContratValidationEngine.java    # Contrôles de fin de contrat, dates d'essai, statut
│   │   ├── CarriereTransitionValidator.java# Validation des transitions d'affectation et de salaire
│   │   └── AncienneteEngine.java           # Calcul unifié de l'ancienneté en mémoire Java (Carrière & STC)
│   ├── WorkflowRhService.java
│   └── ContratTravail.java
├── conge
│   ├── engine
│   │   ├── CongeCalculateurEngine.java     # Droits mensuels (2.5j), bonifications, scission maladie
│   │   └── PurgeFifoEngine.java            # Algorithme FIFO de purge triennale (> 36 mois)
│   ├── service
│   │   └── CongeService.java
│   └── DemandeConge.java
├── pointage
│   ├── engine
│   │   ├── PointageCalculateurEngine.java  # Retards 0 tolérance, nuits, compensation 40h, indemnités
│   │   └── CalendrierOuvrableEngine.java   # Jours ouvrés, ouvrables et fériés par site (injecté dans Pointage & Congés)
│   └── service
│       ├── PointageSaisieService.java      # Batch save, synchronisation indemnités, verrous statut
│       ├── PointageAnomalieService.java    # Détection et régularisation avec sync pointage_indemnite_jour
│       ├── PointageSyntheseService.java    # Calcul global 1-clic, verrous FOR UPDATE, transmission/arbitrage DAF
│       └── PointageEmployeService.java     # Fiche individuelle mensuelle de pointage (découplée du contrôleur)
├── paie
│   ├── engine
│   │   └── PayrollExecutionEngine.java     # Moteur d'orchestration paie, cotisations, IRSA, STC, régularisation
│   ├── service
│   │   ├── PayrollService.java             # Cycle de calcul mensuel, clôture, variables de paie
│   │   ├── StcService.java                 # Solde de tout compte, ICCP, préavis, quittance libératoire
│   │   └── export
│   │       ├── SafiExportService.java      # Export État 301 IRSA SAFI (25 colonnes)
│   │       ├── SafiOdsExportService.java   # Export OpenDocument ODS
│   │       ├── EdiIsiExportService.java    # Export TradXML EDI ISI 5% & feuille SAISIE
│   │       └── PaieExportService.java      # Bordereaux CNaPS DNTS et Santé (OSTIE/OMSI)
│   └── dto
│       ├── PayrollCalculationInput.java    # DTO unifié d'entrée pour le moteur de paie
│       └── BulletinPaieComplet.java        # Agrégat bulletin, lignes de ventilation et impacts trésorerie
├── dashboard
│   ├── service
│   │   ├── DashboardService.java
│   │   └── NotificationRhService.java      # Source d'écriture avec routage RBAC cible_role
│   └── cron
│       └── NotificationRhScheduler.java    # Alertes automatiques
└── paieconfig
    ├── engine
    │   └── CalculateurPaieEngine.java      # Moteur canonique IRSA (CGI Art. 01.03.18), CNaPS, OSTIE
    └── service
        └── PaieConfigService.java
```

---

## 3. Suppression des requêtes SQL complexes

Toutes les logiques procédurales SQL ont été déplacées vers Java :
- **Intervalles de dates (`INTERVAL '...'`)** : Remplacés par des calculs `LocalDate` / `Instant` typés en Java et passés en paramètres préparés `PreparedStatement.setDate(...)` ou `setTimestamp(...)`.
- **Filtres conditionnels d'agrégation (`FILTER (WHERE ...)`)** : Remplacés par l'extraction des données brutes et le traitement en mémoire via les `Domain Engines` et les flux Java (`Stream` / `Map`).
- **Conditions métier (`CASE WHEN ...`)** : Évaluées directement dans les contrôleurs et services Java.

---

## 4. Tests Automatisés & Couverture Complète (328 Tests)

Le projet compte **328 tests automatisés réussis à 100%** (unitaires purs et intégration transactionnelle) :
- `AuditDeepInspectionExhaustifTest` (8 tests approfondis : plafonnements Art. 72 & 68, compensation , régularisation congés DAF, etc.)
- `AuditAdversarialRemediationTest` (13 tests contradictoires et cas limites)
- `AuditPhase5Et6VerificationTest` & `Phase1Et2AuditVerificationTest` (12 tests de conformité par phases)
- `PayrollExecutionEngineTest` (21 tests de calculs de paie duale, cotisations et déductions)
- `CalculateurPaieEngineTest` & `CalculateurPaieEngineAdvancedTest` (24 tests barème canonique IRSA et cotisations sociales)
- `PointageCalculateurEngineTest` & `CalendrierOuvrableEngineTest` (52 tests de temps de travail)
- `CongeCalculateurEngineTest` & `PurgeFifoEngineTest` (35 tests de congés et purge triennale FIFO)
- `CarriereTransitionValidatorTest` & `AncienneteEngineTest` (11 tests de carrière et transitions contractuelles)
- `EmployeValidationEngineTest` & `MatriculeGeneratorEngineTest` (30 tests de conformité légale salarié)
- `PaieExportServiceIntegrationTest` & `PaieBddModelTest` (21 tests d'exports réglementaires SAFI, EDI, CNaPS)
- `PayrollServiceIntegrationTest`, `StcIntegrationTest`, `SirhApiE2EIntegrationTest`, etc. (101 tests d'intégration E2E)

---

## 5. Workflow Transactionnel de Pointage & Sérialisation PostgreSQL

1. **Calcul global 1-clic** : L'agent consolide toutes les semaines du mois en une transaction atomique via `calculerToutesSemainesPeriode`. La période reste en `EN_COURS`, autorisant la consultation et les ajustements.
2. **Transmission DAF avec verrouillage pessimiste** : `transmettreDaf` bloque si des anomalies non résolues existent sur l'entreprise entière, recalcule les tronçons, pose un verrou `FOR UPDATE` sur `periode_paie` et bascule en `A_VALIDER`.
3. **Verrouillage strict en écriture** : Dès le statut `A_VALIDER`, `VALIDEE` ou `CLOTUREE`, `PointageSaisieService` et `PointageAnomalieService` rejettent toute insertion ou modification avec une `IllegalStateException`.
4. **Arbitrage DAF bidirectionnel** :
   - Validation (`validerParDaf`) : fige les cumuls dans `pointage_cumul_periode`, passe en `VALIDEE`, notifie `ROLE_AGENT_POINTAGE`.
   - Rejet (`rejeterParDaf`) : exige un `motif_rejet`, réouvre en `EN_COURS`, notifie `ROLE_AGENT_POINTAGE` avec alerte prioritaire.
5. **Ségrégation RBAC des notifications** : Colonne `cible_role` dans `notification_rh` permettant d'adresser les alertes exclusivement au profil concerné (`ROLE_ADMIN` vs `ROLE_AGENT_POINTAGE`).
