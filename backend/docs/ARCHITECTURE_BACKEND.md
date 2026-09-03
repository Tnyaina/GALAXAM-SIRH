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
│   │   └── ContratValidationEngine.java    # Contrôles de fin de contrat, dates d'essai, statut
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
│   │   └── CalendrierOuvrableEngine.java   # Jours ouvrés, ouvrables et fériés par site
│   └── service
│       ├── PointageSaisieService.java      # Batch save, synchronisation indemnités, verrous statut
│       ├── PointageAnomalieService.java    # Détection et régularisation avec sync pointage_indemnite_jour
│       └── PointageSyntheseService.java    # Calcul global 1-clic, verrous FOR UPDATE, transmission/arbitrage DAF
├── dashboard
│   ├── service
│   │   ├── DashboardService.java
│   │   └── NotificationRhService.java      # Source d'écriture avec routage RBAC cible_role
│   └── cron
│       └── NotificationRhScheduler.java    # Alertes automatiques
└── paieconfig
    ├── engine
    │   └── CalculateurPaieEngine.java      # Plafonds CNaPS, OSTIE, IRSA progressif, perception min.
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

## 4. Tests Automatisés & Couverture (147 Tests)

Le projet compte **147 tests automatisés réussis** (unitaires purs et intégration transactionnelle) :
- `EmployeValidationEngineTest`
- `MatriculeGeneratorEngineTest`
- `ContratValidationEngineTest`
- `CongeCalculateurEngineTest`
- `PurgeFifoEngineTest`
- `PointageCalculateurEngineTest`
- `CalendrierOuvrableEngineTest`
- `CalculateurPaieEngineTest`
- `SimulationWorkflowLongTermeSirhTest` (Simulation complète de 6 ans de carrière)
- `PointageSyntheseWorkflowIntegrationTest` (10 tests de workflow pointage, verrous A_VALIDER et synchronisation indemnités)
- `CongeLedgerWorkflowIntegrationTest` (7 tests ledger et solde congés)
- `SirhApiE2EIntegrationTest` (5 tests bout-en-bout API)
- `StructureEtAdministrationIntegrationTest` (5 tests structure organisationnelle)

---

## 5. Workflow Transactionnel de Pointage & Sérialisation PostgreSQL

1. **Calcul global 1-clic** : L'agent consolide toutes les semaines du mois en une transaction atomique via `calculerToutesSemainesPeriode`. La période reste en `EN_COURS`, autorisant la consultation et les ajustements.
2. **Transmission DAF avec verrouillage pessimiste** : `transmettreDaf` bloque si des anomalies non résolues existent sur l'entreprise entière, recalcule les tronçons, pose un verrou `FOR UPDATE` sur `periode_paie` et bascule en `A_VALIDER`.
3. **Verrouillage strict en écriture** : Dès le statut `A_VALIDER`, `VALIDEE` ou `CLOTUREE`, `PointageSaisieService` et `PointageAnomalieService` rejettent toute insertion ou modification avec une `IllegalStateException`.
4. **Arbitrage DAF bidirectionnel** :
   - Validation (`validerParDaf`) : fige les cumuls dans `pointage_cumul_periode`, passe en `VALIDEE`, notifie `ROLE_AGENT_POINTAGE`.
   - Rejet (`rejeterParDaf`) : exige un `motif_rejet`, réouvre en `EN_COURS`, notifie `ROLE_AGENT_POINTAGE` avec alerte prioritaire.
5. **Ségrégation RBAC des notifications** : Colonne `cible_role` dans `notification_rh` permettant d'adresser les alertes exclusivement au profil concerné (`ROLE_ADMIN` vs `ROLE_AGENT_POINTAGE`).
