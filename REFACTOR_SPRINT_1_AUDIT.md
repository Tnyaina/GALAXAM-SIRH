# Audit de refactorisation — Sprint 1

Date : 2026-09-22. Cet audit porte sur le code et les schémas versionnés ; il ne prétend pas déduire les volumes ou les consommateurs externes d'une base de production.

## Cartographie d'exécution

| Domaine | Backend propriétaire | API | Frontend | Schéma canonique |
| --- | --- | --- | --- | --- |
| Organisation | `model/organisation`, `model/referentiel` | `api/controller/organisation` | `pages/parametres/organisation`, `components/parametres/structure` | `02_schema_referentiels_rh.sql` |
| Employés et carrière | `model/employe`, `model/contrat` | `controller/employe`, `controller/contrat` | `pages/employe`, `components/employe`, `components/carriere` | `03_schema_employes_contrats.sql` |
| Congés et absences | `model/conge` | `controller/conge` | `pages/conges`, `components/conges`, `hooks/conges` | `04_schema_conges.sql` |
| Pointage et présence | `model/pointage` | `controller/pointage` | `pages/pointage`, `components/pointage`, `hooks/pointage`, `api/pointage` | `05_schema_pointage.sql` |
| Paie et rémunération | `model/paie`, `model/paieconfig` | `controller/paie`, `controller/paieconfig` | `pages/paie`, `components/paie`, `hooks/paie`, `api/paie` | `06_schema_paie.sql` |
| Administration | `model/admin`, `model/dashboard`, `cron` | `controller/admin`, `controller/dashboard` | `pages/parametres`, `components/parametres`, dashboard | `01_schema_core_securite.sql` |
| Authentification | `securite` | `controller/securite` | `pages/auth`, `context/AuthContext` | `01_schema_core_securite.sql` |

La chaîne de navigation principale est explicite dans `frontend/src/routes/Router.jsx` : route → page → composant/hook → module `frontend/src/api` → contrôleur Spring → service/engine de domaine → SQL JDBC.

## Dépendances et sources de vérité

Les controllers sont des frontières HTTP ; les services orchestrent transaction, autorisation ou chargements ; les `engine` portent les calculs autonomes. Les calculateurs paie, pointage et congés ne sont pas des pass-through : leurs tests de bord, de temporalité et d'arrondi démontrent une responsabilité distincte.

Sources canoniques identifiées :

- calcul de paie : `model/paie/engine/PayrollExecutionEngine` et ses calculateurs spécialisés ; les résultats validés sont figés dans `bulletin_paie` et ses lignes ;
- taux légaux datés : `bareme_irsa`, `site_cotisation`, `paie_config_legale`, avec les résolveurs temporels paie ;
- workflow de période de paie : `model/paie/workflow/PayrollPeriodWorkflowService` ;
- pointage : services et pipeline `model/pointage`, puis snapshots de période ;
- solde congé et reprise historique : `model/conge/service`, `mouvement_solde_conge`, `conge_periode_reference`, `conge_taux_applicable` ;
- présentation de montants et dates : `frontend/src/utils/moneyUtils.js` et `frontend/src/utils/dateUtils.js` ;
- client HTTP : `frontend/src/api/apiClient.js`.

## Nettoyage sûr effectué

- Suppression de `frontend/src/services/periodePaieService.js`, alias interne sans logique. Ses sept consommateurs importent directement `api/pointage/periodePaieService.js`.
- Suppression de `frontend/src/utils/excelExportUtils.js` et `frontend/src/utils/pointageSyntheseUtils.js`. Recherche dans code, tests, routes, configuration et documentation : aucun import, appel, export indirect ou référence textuelle.

Les alias d'URL pointage, paie et paramètres sont conservés : `Router.jsx` les documente comme compatibilité de bookmarks. Leur trafic externe n'est pas observable depuis le dépôt.

## Éléments volontairement non supprimés

- Les classes de calcul paie et pointage : elles protègent des invariants de temporalité, de droit ou d'arrondi et sont couvertes séparément.
- Les tables de snapshots, transitions et journaux : elles séparent des cycles de vie historiques ; une fusion casserait l'auditabilité.
- Les migrations historiques : leur README indique que les six schémas sont le bootstrap canonique. Elles restent une archive de déploiement, pas du code actif.

## Tests et constat

- Frontend : `npm test -- --run` — 12 fichiers, 91 tests, tous passants.
- Backend : `mvn test -q` échoue sur `TcongeP1TechnicalValidityLowerBoundUnitTest`. Le test attend la chaîne `TECHNICAL_VALIDITY_LOWER_BOUND` dans une description qui contient seulement son libellé humain. Aucun changement de règle métier n'a été appliqué : voir `BUSINESS_RULE_SUSPECT` dans le rapport final.
