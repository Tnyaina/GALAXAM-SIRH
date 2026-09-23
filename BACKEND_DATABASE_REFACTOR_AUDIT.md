# Audit de refactorisation — backend et base

Date : 2026-09-23

## Conclusion

Le trop-plein backend ne vient pas d'abord des 80 tables. Il vient du mélange des couches : la frontière HTTP, les transactions, le SQL et les règles métier sont souvent réunis dans les mêmes classes. La priorité n'est donc pas de fusionner des tables métier, mais d'établir un chemin unique `controller -> use case/service -> repository JDBC -> PostgreSQL` par domaine.

Mesures sur le code actuel :

| Indicateur | Valeur |
| --- | ---: |
| Fichiers Java de production | 291 après nettoyage |
| Lignes Java de production | environ 60 000 |
| Contrôleurs | 46 |
| Contrôleurs dépendant directement de `DataSource` | 41 |
| Requêtes SQL écrites dans les contrôleurs | 67 |
| Classes héritant de `ClassMere` | 64 |
| Fichiers manipulant manuellement `commit` / `rollback` | 44 |
| Classes de plus de 500 lignes | 21 |
| Classes de plus de 1 000 lignes | 9 |
| Tables dans les six schémas canoniques | 80 |
| Vues dans les six schémas canoniques | 9 |

## Nettoyage certain appliqué

Ces éléments n'avaient aucun consommateur dans le code de production, les tests ou les scripts :

- `StatutAjustementRequest.java` : DTO HTTP abandonné ;
- `PointageGanttDTO.java` : ancien contrat de présentation sans endpoint ;
- `UtilDB.java` : accès statique à `DataSource`, inutilisé et concurrent de l'injection Spring ;
- propriétés `spring.jpa.*` : aucun JPA/Hibernate dans le projet ;
- niveaux de logs `JdbcTemplate` / `NamedParameterJdbcTemplate` : aucune utilisation de ces APIs.

## Problèmes structurants

### P0 — Transactions dispersées

`TransactionRunner` est annoncé comme frontière transactionnelle unique, mais il n'est utilisé que par quelques cas d'usage. Quarante-quatre fichiers pilotent encore directement les transactions. Cette coexistence est une double méthode et rend les rollback difficiles à vérifier.

Décision cible : un service applicatif possède la transaction ; un contrôleur ne reçoit jamais de `Connection` et n'appelle jamais `commit`, `rollback` ou `setAutoCommit`.

### P0 — SQL dans la couche HTTP

Quarante-et-un contrôleurs injectent `DataSource`. Les cas les plus chargés sont `ContratGlobalController`, `ContratTravailController`, `NotificationController`, `DemandeCongeController` et `PeriodePaieController`.

Décision cible : déplacer chaque requête vers un repository du domaine. Un contrôleur valide le contrat HTTP, appelle un cas d'usage et transforme la réponse. Aucun SQL ne doit rester sous `api/controller`.

### P1 — Active Record trop large

Soixante-quatre classes héritent de `ClassMere`. Une même classe représente les données, valide parfois le métier, construit le SQL, hydrate les résultats et expose des méthodes de recherche. Les classes `Employe`, `EmployeCPL`, `DemandeConge`, `BulletinPaie` et plusieurs référentiels cumulent ainsi plusieurs raisons de changer.

Décision cible : ne pas réécrire les 64 classes d'un coup. Par domaine migré, introduire des objets métier simples et un repository JDBC explicite, puis retirer l'héritage `ClassMere` quand le dernier consommateur est migré. `ClassMere` doit disparaître à la fin, pas devenir une abstraction encore plus générique.

### P1 — Services et contrôleurs géants

Neuf classes dépassent 1 000 lignes : `PointageSyntheseService`, `PointageSaisieService`, `PayrollService`, `WorkflowRhService`, `CongeTauxApplicableService`, `PointageAnomalieService`, `DemandeConge`, `ContratTravailController` et `StcService`.

Découpage recommandé par cas d'usage, pas par helper technique :

- pointage : saisie, calcul, transmission, validation, export ;
- contrat : création, période d'essai, carrière, sortie ;
- paie : préparation, calcul, validation/clôture, export ;
- congé : demande/workflow, acquisition, taux, solde/purge.

### P1 — Emplacements incohérents

Les DTO HTTP sont majoritairement dans `api/dto`, mais deux requêtes congé HTTP sont dans `model/conge/dto`. Les schedulers sont dans le paquet racine `cron` alors que le guide les présente parfois dans les domaines. La sécurité est partagée entre `model/securite` et `securite`.

Décision cible : organisation verticale par domaine :

```text
<domaine>/
  api/          contrôleur + DTO HTTP
  application/  cas d'usage + transaction
  domain/       règles et objets métier sans JDBC/HTTP
  persistence/  SQL, mapping et repositories
```

Les éléments réellement transverses seulement restent dans `common`, `config` et `security`.

### P1 — Configuration de base concurrente

`DatabaseConfig` construit manuellement un `HikariDataSource`, alors que les profils déclarent aussi `spring.datasource.hikari.*`. Cette construction personnalisée risque de contourner la configuration standard du pool et maintient deux chemins de configuration (`SPRING_DATASOURCE_URL` et `DATABASE_URL`).

Décision cible : normaliser l'URL en amont du déploiement ou via une petite propriété dédiée, puis laisser Spring Boot créer et configurer le pool. Ajouter un test de contexte qui vérifie URL, taille maximale et profil actif.

## Base de données : garder, fusionner ou supprimer

### À garder séparé

Les groupes suivants ne doivent pas être fusionnés :

- `bulletin_paie` / `bulletin_paie_ligne` : entête et ventilation 1-N ;
- `paie_pret` / `paie_pret_echeance` : contrat financier et échéancier ;
- `demande_conge` / `mouvement_solde_conge` : workflow et grand livre de solde ;
- `pointage_jour` / `pointage_semaine_calcule` / `pointage_cumul_periode` : saisie, résultat de calcul et clôture ;
- snapshots pointage : preuve historique immuable ;
- `periode_paie` / `periode_paie_transition` : état courant et journal d'audit ;
- tables de taux, barèmes et configurations datées : la temporalité est une règle métier.

Les fusionner réduirait le nombre de tables mais détruirait l'historique, l'audit ou les cardinalités.

### Candidats de suppression à confirmer sur une base réelle

Trois vues n'ont aucune référence dans le Java, les tests ou les scripts actifs :

- `v_pointage_indemnites` ;
- `v_historique_carriere_global` ;
- `v_employe_actif_courant`.

Elles sont `DROP_CANDIDATE`, pas encore supprimées : un client BI, une requête manuelle ou une intégration externe peut les utiliser. La décision exige les statistiques PostgreSQL (`pg_stat_user_tables`/audit de requêtes), la recherche dans les outils externes et une fenêtre de dépréciation.

### Candidats à simplifier, pas à fusionner immédiatement

- `ref_type_bulletin` et `ref_mode_paiement` ont très peu de consommateurs Java. Vérifier s'ils sont administrables et historisés. S'ils sont fixes mais référencés par des lignes historiques, les garder en table ; remplacer par un enum casserait l'intégrité référentielle.
- `ref_pool_conge_annuel` et `ref_motif_evenement_familial` ont peu de consommateurs, mais portent une configuration métier. Leur faible usage ne prouve pas leur inutilité.
- Les tables de liaison `site_cotisation`, `site_calendrier_ferie` et `site_indemnite_config` expriment du N-N ou de la configuration par site ; les fusionner dans les référentiels créerait des colonnes répétées ou du JSON difficile à contraindre.

Conclusion BDD : aucune table ne peut être supprimée ou fusionnée de façon sûre à partir du dépôt seul. Les trois vues ci-dessus sont les seuls candidats morts prouvés côté application.

## Ordre d'exécution recommandé

1. Ajouter des tests d'architecture qui interdisent tout nouveau `DataSource`, SQL ou transaction manuelle dans les contrôleurs.
2. Migrer le domaine `organisation` en premier : CRUD simple, huit contrôleurs très répétitifs, faible risque métier. Créer services applicatifs et repositories JDBC, avec une convention unique.
3. Migrer `paieconfig`, puis `admin`, avec le même patron ; mutualiser seulement l'infrastructure JDBC, pas les règles métier.
4. Migrer ensuite `employe/contrat`, `conge`, `pointage`, puis `paie`, en commençant chaque fois par les cas d'usage les mieux couverts par des tests.
5. Supprimer `ClassMere` lorsque son dernier descendant est migré.
6. Mesurer l'usage des trois vues candidates, publier une migration de dépréciation, puis les supprimer dans une migration séparée si aucun consommateur n'est observé.

## Définition de terminé par domaine

- zéro SQL et zéro `DataSource` dans le contrôleur ;
- une seule frontière transactionnelle dans le service applicatif ;
- repository couvert par test PostgreSQL ;
- règle métier testée sans HTTP ni base quand elle est pure ;
- DTO HTTP localisé dans le domaine et distinct de l'objet persistant ;
- aucune route ou réponse publique modifiée sans test de contrat ;
- documentation du domaine indiquant les points d'entrée et la table propriétaire.

## Vérification du lot initial

- `mvn -q -DskipTests compile` : succès ;
- `git diff --check` : succès ;
- `mvn test -q` : 715 tests exécutés, 2 échecs et 9 erreurs indépendants du nettoyage ;
- les 9 erreurs sont des tests d'architecture qui ciblent d'anciens chemins frontend ;
- les 2 échecs concernent une fixture de période `101` absente et un référentiel de majorations 2026 incomplet ;
- les logs révèlent aussi un défaut de contrat SQL préexistant : `CalendrierOuvrableEngine` sélectionne `libelle` dans `ref_periode_chomee`, alors que le schéma canonique expose `motif`.
