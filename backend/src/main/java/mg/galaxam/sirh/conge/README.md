# Congés — guide technique

Le module gère les demandes, droits acquis, soldes, lots FIFO, valorisation, périodes de référence et exports. Les routes `/conges/*` exigent une authentification ; chaque contrôleur contrôle ensuite les permissions ou l'accès au dossier personnel.

## Structure et chemin de lecture

```text
conge/
├── api/                 Entrées HTTP, autorisation et réponses
├── application/         Cas d'utilisation, transactions et intégrations
│   └── export/          Lecture et génération Excel
├── domain/              Calculs sans accès aux données et contrats du module
└── persistence/         Tables, vues et requêtes SQL
```

Pour suivre une action, partir du contrôleur, ouvrir la méthode de service appelée, puis le moteur de calcul ou l'objet de persistance référencé. Exemple : `DemandeCongeController.creerDemande` → `CongeService.creerDemande` → `CongeCalculateurEngine` / `PurgeFifoEngine` → `DemandeConge` / `MouvementSoldeConge`.

## API : où entre la demande ?

| Fichier | Responsabilité ; méthodes à lire |
|---|---|
| `api/DemandeCongeController.java` | Demandes et workflow : `simulerDemande`, `verifierConflits`, `creerDemande`, `modifierDemande`, `validerDemande`, `refuserDemande`, `annulerDemande` ; lectures personnelles et de validation. |
| `api/CongeSoldeController.java` | Soldes, mouvements, lots, planning et expiration : `getSoldeEmploye`, `getImputationSolde`, `getValorisationLots`, `getExamenExpiration`, `reporterExamenExpiration`, `confirmerExpiration`, `creerAjustementManuel`, `enregistrerCutoverOpening`. |
| `api/CongePeriodeReferenceController.java` | Périodes et TCONGE : `creerPeriodeReference`, `cloturerPeriodeReference`, `activerTauxCalcule`, `corrigerValorisation` ; trois routes `export*`. |
| `api/CongeRefController.java` | Lecture et administration des types, motifs familiaux, pools et statuts de demande. |

## Application : où est le fonctionnement ?

| Fichier | Responsabilité ; méthodes à lire |
|---|---|
| `application/CongeService.java` | Orchestration principale : `simulerDemande`, `creerDemande`, `modifierDemande`, `validerDemande`, `refuserDemande`, `annulerDemande`, `verifierConflitsDemande` ; acquisition et bonification via `enregistrerAcquisitionMensuelle`, `executerBonificationMensuelle` ; régularisations de pointage. |
| `application/CongeTauxApplicableService.java` | Période de référence, TCONGE et reprise : `resoudreTauxCongeApplicable`, `creerPeriodeReference`, `ensureLeaveReferencePeriod`, `cloturerPeriodeReference`, `activerTauxCalcule`, `enregistrerCutoverSnapshot`, `obtenirSituationComplete`. |
| `application/CongeLotValorisationService.java` | Situation des lots et prises valorisées : `situation`, `simulerPrise`, `valorisationPrisesSurPeriode`, `corrigerReliquatCutover`. |
| `application/CongeValorisationMensuelleService.java` | Lecture, historique et correction auditée des valorisations mensuelles : `lister`, `historique`, `corriger`. |
| `application/CongeExpirationExamenService.java` | Examen des droits expirables : `obtenir`, `reporter`, `confirmerExpiration`. |
| `application/CongeContinuiteService.java` | Vérifie la continuité d'affectation poste/site pendant une demande : `analyser`. |
| `application/CongeDecompteCalendaireResolver.java` | Résout le décompte journalier selon la politique datée : `calculer`, `session`. |
| `application/CongePayrollPolicy.java` | Décide si un type de congé est rémunéré : `estRemunere`. |
| `application/RemunerationCongePolicy.java` | Résout la rémunération applicable : `resolve`. |
| `application/RegularisationCongeResult.java` | Résultat transmis après régularisation ; aucune orchestration. |
| `application/export/CongeAnnuelExcelExportService.java` | État individuel : `genererEtatCongesIndividuel`, `obtenirNomFichierExport`, `resoudreEmployeDePeriode`. |
| `application/export/CongeGrandRecapExcelExportService.java` | Grand récapitulatif DAF : `generer` ; lit les lots et l'affectation à la date du rapport. |

## Domaine : règles et contrats

| Fichier | Responsabilité ; méthodes à lire |
|---|---|
| `domain/CongeCalculateurEngine.java` | Calcul pur des jours, acquisitions, bonification, taux et simulation : `calculerNombreJours`, `calculerAcquisitionMensuelle`, `calculerBonificationAnciennete`, `simuler`. |
| `domain/PurgeFifoEngine.java` | Ordre d'imputation et expiration des droits : `calculerDroitsDisponibles`, `calculerImputation`, `calculerJoursAExpierFifo`. |
| `domain/CongeLotValorisationEngine.java` | Valorisation pure des lots et des prises : `calculer`, `simulerPrise`. |
| `domain/DemandeCongeRequests.java` | Corps des requêtes de création, modification, simulation, conflit et décision. |
| `domain/CongeSoldeRequests.java` | Corps des requêtes d'ajustement, cutover et examen d'expiration. |
| `domain/CongeExpirationExamenDTO.java` | Résultat de l'examen d'expiration. |
| `domain/CongeSituationEmployeDTO.java` | Situation consolidée d'un salarié. |
| `domain/TcongeConfigurationDTO.java` | État de configuration du TCONGE. |

Les `Requests` et `DTO` sont des contrats de données, pas des moteurs de règles. Ils restent dans `domain/` car les services d'application consomment certains de ces types directement ; déplacer ces contrats sous `api/` ferait dépendre l'application de l'interface HTTP.

## Persistance : où sont les données ?

| Fichier | Responsabilité |
|---|---|
| `persistence/DemandeConge.java` | Demande enregistrée, recherche et mise à jour ; `calculerNombreJoursDecomptes` fournit le décompte associé. |
| `persistence/MouvementSoldeConge.java` | Ledger des droits et prises ; `saveBonificationAnnuelleUneFois` protège l'écriture annuelle. |
| `persistence/RefTypeConge.java` | Configuration des types de congé. |
| `persistence/RefMotifEvenementFamilial.java` | Motifs familiaux. |
| `persistence/RefPoolCongeAnnuel.java` | Pool annuel, consommations et quota ; `verrouillerQuota`. |
| `persistence/RefStatutDemandeConge.java` | Statuts de demande. |
| `persistence/VDemandeCongeCpl.java` | Vue enrichie des demandes pour la lecture. |
| `persistence/VSoldeCongeEmploye.java` | Vue de solde par salarié. |

## Index des méthodes principales

| Méthode | Effet concret | Quand la consulter |
|---|---|---|
| `CongeService.simulerDemande` / `creerDemande` | Estiment les droits et conflits / enregistrent une nouvelle demande. La simulation n'écrit pas la demande. | Vérifier le résultat avant soumission ou la création effective. |
| `CongeService.validerDemande` | Applique la décision de validation et les effets sur droits et solde. | Une demande approuvée ne se reflète pas dans les soldes. |
| `CongeService.enregistrerAcquisitionMensuelle` | Inscrit les droits acquis pour un mois. | Une acquisition planifiée manque. |
| `CongeService.executerBonificationMensuelle` | Applique la bonification d'ancienneté du mois concerné. | Une bonification Cron est absente ou doublée. |
| `CongeTauxApplicableService.resoudreTauxCongeApplicable` | Choisit le TCONGE valable à la date et sa provenance. | Une valorisation utilise un taux inattendu. |
| `CongeTauxApplicableService.ensureLeaveReferencePeriod` | Retrouve ou crée la période de référence nécessaire à une acquisition. | Une acquisition échoue faute de période. |
| `CongeTauxApplicableService.enregistrerCutoverSnapshot` | Enregistre l'état d'ouverture historique avec sa référence DAF. | Reprise initiale des droits et valorisations. |
| `CongeLotValorisationService.simulerPrise` / `PurgeFifoEngine.calculerImputation` | Estiment la valorisation / répartissent les jours pris entre les lots selon FIFO. | Un débit semble viser le mauvais lot. |
| `CongeExpirationExamenService.obtenir` / `confirmerExpiration` | Examinent les droits expirables / confirment l'expiration. | Une expiration affichée n'a pas encore produit d'effet. |
| `CongeValorisationMensuelleService.corriger` | Enregistre une correction mensuelle avec historique. | Réparer une valeur de période déjà calculée. |

## Intégrations et invariants

- `cron` déclenche acquisition et bonification ; `pointage` appelle les régularisations ; `paie` consomme la rémunération et la valorisation des congés. Les règles restent dans ce module, les déclencheurs dans leurs domaines respectifs.
- La reprise historique exige une référence d'arrêté DAF, conservée avec le taux et le mouvement d'ouverture. `CongeTauxApplicableService.enregistrerCutoverSnapshot` est le point d'entrée.
- Les bonifications sont idempotentes par unicité salarié/année. Le schéma canonique est `backend/conception/bdd/04_schema_conges.sql` ; il initialise une base vierge et ne constitue pas une migration historique.
- Les exports lisent les données métier ; ils ne recalculent pas les droits. L'état individuel utilise `backend/src/main/resources/templates/annuel/ETAT_CONGES_PAR_PERSONNE.xlsx`.

## Vérification

`mvn -f backend/pom.xml clean test` exécute les tests unitaires ; les tests PostgreSQL du package `conge` demandent une base disponible. Après un changement de route ou de contrat, lancer aussi `npm --prefix frontend run test:api` et `npm --prefix frontend run build`.
