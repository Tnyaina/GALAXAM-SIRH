# Domaine organisation

Ce dossier est le point d'entrée unique du backend pour la structure de l'entreprise.

- `api/` : contrôleur REST unique [`OrganisationController.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/organisation/api/OrganisationController.java) et DTOs d'entrée ;
- `application/` : service métier [`OrganisationService.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/organisation/application/OrganisationService.java) centralisant la logique transactionnelle, les garde-fous d'intégrité (désactivation contrôlée des filiales) et la journalisation d'audit ;
- `persistence/` : modèles Active Record et SQL JDBC explicite ([`RefFiliale`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/organisation/persistence/RefFiliale.java), [`RefSite`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/organisation/persistence/RefSite.java), [`RefDepartement`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/organisation/persistence/RefDepartement.java), [`RefService`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/organisation/persistence/RefService.java), [`RefFonction`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/organisation/persistence/RefFonction.java), [`RefBanque`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/organisation/persistence/RefBanque.java), [`RefTypeContrat`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/organisation/persistence/RefTypeContrat.java), [`RefTypeIntervenant`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/organisation/persistence/RefTypeIntervenant.java)).

Les routes REST restent sous le préfixe `/referentiel/*` (exposées sous `/api/referentiel/*` via le context-path). Le contrat d'intégration avec le frontend React est intégralement préservé.

Les classes de `persistence/` conservent la rigueur Active Record : `Connection`, `PreparedStatement` et `ResultSet` sans aucun framework ORM lourd.

## Cohérence des affectations de carrière

Le lien d'une affectation vers son contrat est contrôlé avec le couple
`(id_contrat, id_employe)`, et le lien vers le service avec
`(id_service, id_departement)`. Les contraintes composées sont définies dans
`backend/conception/bdd/migrations/26-10-16-sp74-affectation-integrity.sql`
et intégrées aux schémas canoniques 02 et 03. Elles complètent la validation
applicative et préservent les règles de mobilité et d'historisation existantes ;
elles ne changent pas le rattachement des départements aux filiales ni le
modèle des sites partagés.

Avant SP74 sur une base existante, exécuter les diagnostics décrits dans le
script. Toute incohérence bloque la migration et doit être arbitrée/corrigée
explicitement. Les tests de référence sont validés via `ContratTravailControllerPostgresTest` et `EmployeSatelliteLifecycleTest`.
