# Guide d'Architecture Backend : GALAXAM SIRH

> **Statut : UPDATE (15 septembre 2026).** Ce guide d'orientation ne remplace pas le code, les migrations actives et les tests. Les règles légales doivent être validées avant modification.

## 1. Principes Fondateurs

Le backend du SIRH GALAXAM applique deux règles architecturales strictes :
1. **Le code Java calcule et le workflow backend autorise.**
2. **SQL charge, filtre, joint, trie et agrège techniquement ; il ne décide pas une règle métier.**

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
│   │   ├── DailyAttendanceCalculator.java
│   │   ├── WeeklyAttendanceCalculator.java
│   │   ├── AttendancePeriodCalculator.java
│   │   └── CalendrierOuvrableEngine.java
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
    └── service
        └── PaieConfigService.java
```

---

## 3. SQL passif

Les agrégations et conditions SQL purement techniques restent autorisées. Les artefacts actifs de calcul/décision métier identifiés par l'audit ont été supprimés par `2026-09-14_remove_business_sql.sql`.

---

## 4. Tests automatisés

Le nombre de tests n'est pas documenté manuellement. La commande canonique est `./mvnw test`; `scripts/surefire-summary.py` produit ensuite le total depuis les XML Surefire.

---

## 5. Workflow Transactionnel de Pointage & Sérialisation PostgreSQL

1. **Calcul global 1-clic** : L'agent consolide toutes les semaines du mois en une transaction atomique via `calculerToutesSemainesPeriode`. La période reste en `EN_COURS`, autorisant la consultation et les ajustements.
2. **Transmission DAF avec verrouillage pessimiste** : `transmettreDaf` bloque si des anomalies non résolues existent sur l'entreprise entière, recalcule les tronçons, pose un verrou `FOR UPDATE` sur `periode_paie` et bascule en `A_VALIDER`.
3. **Verrouillage strict en écriture** : Dès le statut `A_VALIDER`, `VALIDEE` ou `CLOTUREE`, `PointageSaisieService` et `PointageAnomalieService` rejettent toute insertion ou modification avec une `IllegalStateException`.
4. **Arbitrage DAF bidirectionnel** :
   - Validation (`validerParDaf`) : fige les cumuls dans `pointage_cumul_periode`, passe en `VALIDEE`, notifie `ROLE_AGENT_POINTAGE`.
   - Rejet (`rejeterParDaf`) : exige un `motif_rejet`, réouvre en `EN_COURS`, notifie `ROLE_AGENT_POINTAGE` avec alerte prioritaire.
5. **Ségrégation RBAC des notifications** : Colonne `cible_role` dans `notification_rh` permettant d'adresser les alertes exclusivement au profil concerné (`ROLE_ADMIN` vs `ROLE_AGENT_POINTAGE`).
