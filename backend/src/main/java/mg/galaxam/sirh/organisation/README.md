# Domaine organisation

Ce dossier est le point d'entrée unique du backend pour la structure de l'entreprise.

- `api/` : contrôleur REST unique [`OrganisationController.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/organisation/api/OrganisationController.java) et DTOs d'entrée ;
- `application/` : service métier [`OrganisationService.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/organisation/application/OrganisationService.java) centralisant la logique transactionnelle, les garde-fous d'intégrité (désactivation contrôlée des filiales) et la journalisation d'audit ;
- `persistence/` : modèles Active Record et SQL JDBC explicite ([`RefFiliale`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/organisation/persistence/RefFiliale.java), [`RefSite`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/organisation/persistence/RefSite.java), [`RefDepartement`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/organisation/persistence/RefDepartement.java), [`RefService`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/organisation/persistence/RefService.java), [`RefFonction`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/organisation/persistence/RefFonction.java), [`RefBanque`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/organisation/persistence/RefBanque.java), [`RefTypeContrat`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/organisation/persistence/RefTypeContrat.java), [`RefTypeIntervenant`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/organisation/persistence/RefTypeIntervenant.java)).

Les routes REST restent sous le préfixe `/referentiel/*` (exposées sous `/api/referentiel/*` via le context-path). Le contrat d'intégration avec le frontend React est intégralement préservé.

L'écran `/admin/organisation` est accessible à `ROLE_ADMIN` et aux comptes dotés de `ORGANISATION_WRITE`. Côté API, les lectures de `OrganisationController` exigent une authentification et les écritures exigent `ORGANISATION_WRITE` ou `ROLE_ADMIN` ; le contrôle serveur reste la source d'autorité. Les rôles délégués doivent recevoir le menu `ADMIN_ORGANISATION` pour voir son lien dans la barre latérale. Une écriture d'audit en échec interrompt les opérations transactionnelles du service.

Les classes de `persistence/` conservent la rigueur Active Record : `Connection`, `PreparedStatement` et `ResultSet` sans aucun framework ORM lourd.

## Cohérence des affectations de carrière

Le lien d'une affectation vers son contrat est contrôlé avec le couple
`(id_contrat, id_employe)`, et le lien vers le service avec
`(id_service, id_departement)`. Les clés étrangères composées figurent dans
`backend/conception/bdd/03_schema_employes_contrats.sql`, avec les contraintes
uniques correspondantes dans les schémas canoniques 02 et 03. Elles complètent
les validations applicatives sans changer le modèle des départements ni celui
des sites partagés.

Ces schémas servent à initialiser une base neuve ; ils ne constituent pas une
procédure de mise à niveau d'une base existante. Les tests
`ContratTravailControllerPostgresTest` et `EmployeSatelliteLifecycleTest`
vérifient des comportements ciblés, sans valider une migration de schéma.
