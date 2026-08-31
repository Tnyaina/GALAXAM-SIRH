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
│   │   └── PointageCalculateurEngine.java  # Retards 0 tolérance, nuits, compensation 40h, indemnités
│   └── service
│       └── PointageSaisieService.java
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

## 4. Tests Unitaires & Couverture

Le projet compte **126 tests unitaires automatisés** sans dépendance externe à la base de données pour les moteurs de calcul :
- `EmployeValidationEngineTest` (4 tests)
- `MatriculeGeneratorEngineTest` (4 tests)
- `ContratValidationEngineTest` (4 tests)
- `CongeCalculateurEngineTest` (4 tests)
- `PurgeFifoEngineTest` (3 tests)
- `PointageCalculateurEngineTest` (5 tests)
- `CalculateurPaieEngineTest` (4 tests)
- `SimulationWorkflowLongTermeSirhTest` (Simulation complète de 6 ans de carrière)
