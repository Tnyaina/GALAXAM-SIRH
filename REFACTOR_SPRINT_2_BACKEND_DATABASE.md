# Sprint 2 — Backend et base de données

## Résultat

L'audit ne justifie aucune fusion backend ou migration de données dans ce sprint. Les domaines sont déjà séparés selon leur responsabilité, et les objets paie, pointage et congés qui semblent nombreux correspondent à des invariants autonomes : règles fiscales, arrondis, résolution temporelle, snapshot, workflow et historique.

## Source de vérité finale

| Sujet | Source canonique |
| --- | --- |
| Exécution de la paie | `model/paie/engine/PayrollExecutionEngine` |
| IRSA / ISI / cotisations | calculateurs dédiés et résolveurs temporels du domaine `model/paie` |
| Cycle de la période | `model/paie/workflow/PayrollPeriodWorkflowService` |
| Solde de congé | mouvements et services du domaine `model/conge` |
| Pointage et valorisation | pipeline de calcul `model/pointage/calculation` et snapshots |
| Authentification | paquet `securite` et tables sécurité |

## Base de données

Les six fichiers `01_schema_*.sql` restent les schémas de bootstrap canoniques. Les dossiers `migrations/` et `archive_migrations_historiques/` sont conservés pour la traçabilité : les supprimer ou les condenser modifierait le processus de déploiement sans preuve qu'aucun environnement ne les consomme.

Il n'y a donc pas de nouvelle migration dans ce sprint. Une migration destructive ne sera acceptable qu'avec les preuves listées dans `DATABASE_STRUCTURE_AUDIT.md` (dépendances, conservation des lignes, contraintes et test PostgreSQL).

## Vérification

La compilation backend est couverte par l'exécution de `mvn test -q` : elle a atteint l'exécution des tests mais s'arrête sur un seul écart de test de caractérisation préexistant, documenté dans le rapport final. Aucune règle métier ou donnée historique n'a été modifiée.
