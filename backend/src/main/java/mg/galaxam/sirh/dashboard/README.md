# Dashboard et notifications — guide technique

Ce module sert la synthèse RH et les notifications persistées. Le dashboard agrège des données détenues par Contrat, Congés, Pointage et Paie ; il ne doit pas recalculer leurs résultats officiels.

```text
dashboard/
├── api/           Routes HTTP de synthèse et de notifications
├── application/   Assemblage de la synthèse RH
└── persistence/   Écriture JDBC commune des notifications
```

```mermaid
flowchart LR
    UI[Client React] --> DASH[DashboardController]
    DASH --> SUMMARY[DashboardService]
    SUMMARY --> DB[(Vues et tables métier)]
    UI --> NOTIF[NotificationController]
    NOTIF --> NDB[(notification_rh)]
    JOB[NotificationRhScheduler] --> WRITE[NotificationRhService]
    DOMAIN[Autres domaines] --> WRITE
    WRITE --> NDB
```

## Fichiers et méthodes

| Fichier | Méthodes et usage | Importance |
| --- | --- | --- |
| [`DashboardController`](api/DashboardController.java) | `getDashboardSummary` expose `GET /dashboard/summary` et délègue au service. | **Structurant** — contrat de la page d'accueil. |
| [`DashboardService`](application/DashboardService.java) | `getDashboardSummary` assemble la réponse ; `buildKpis`, `buildActionsATraiter`, `buildDemandesCongeAValider`, `buildAbsentsAujourdhui`, `buildCongesQuinzaine`, `buildAlertesCritiques`, `buildAlertes7j`, `buildAlertes30j`, `buildBrouillons`, `buildActiviteRecente` construisent les blocs. `ActiviteStatut.from` choisit le libellé/badge de présentation. | **Structurant** — agrégation en lecture des données métier. |
| [`NotificationController`](api/NotificationController.java) | `getNotifications` liste 50 non lues ; `marquerToutesLues`, `marquerUne`, `purgerToutes` changent l'état visible. `visibilityCondition` et `bindVisibility` imposent le filtre utilisateur/rôle ; `getCurrentUserId` et `getCurrentUserRoleCode` résolvent le destinataire. | **Critique** — confidentialité et modifications des notifications. |
| [`NotificationRhService`](persistence/NotificationRhService.java) | Surcharges `creer` et `creerPourRole` insèrent une notification avec utilisateur ou rôle cible sur la connexion fournie par l'appelant. | **Structurant** — écriture partagée par Cron et les domaines RH. |

## Index des méthodes principales

| Méthode | Effet concret | Quand la consulter |
|---|---|---|
| `DashboardService.getDashboardSummary` | Assemble une réponse de synthèse à partir de plusieurs sources métier ; lecture seule. | Une carte du tableau de bord est absente ou incohérente. |
| `buildKpis` / `buildActionsATraiter` | Préparent les chiffres clés / actions affichés dans la synthèse. | Corriger un bloc précis de la page d'accueil. |
| `NotificationController.getNotifications` | Liste les notifications visibles pour l'utilisateur ou son rôle, limitées aux non lues. | Une notification manque ou est visible au mauvais destinataire. |
| `NotificationController.visibilityCondition` / `bindVisibility` | Construisent et paramètrent le filtre SQL par destinataire. | Vérifier la confidentialité des lectures et mutations. |
| `marquerUne` / `marquerToutesLues` / `purgerToutes` | Changent l'état des notifications visibles à l'utilisateur. | Une action de notification n'a pas l'effet attendu. |
| `NotificationRhService.creerPourRole` | Insère une notification ciblée sur un rôle dans la connexion appelante ; l'échec est isolé par savepoint. | Une tâche Cron ou un domaine émet une alerte. |

## Contrats et limites actuelles

`GET /dashboard/summary` exige `EMPLOYE_READ` ou `ROLE_ADMIN`. Les routes `/notifications` exigent une authentification ; le contrôleur filtre les lignes par destinataire ou rôle. Le préfixe `/api` vient du context-path. Le frontend appelle ces routes dans `frontend/src/features/dashboard/api/dashboardService.js`.

`NotificationController` exécute encore du SQL directement dans `api/` ; `DashboardService` associe assemblage et requêtes de lecture. Cette séparation n'est donc pas complète. La restructuration actuelle ne change pas leurs requêtes ni leurs réponses. `NotificationRhService` est un adaptateur d'écriture, malgré son nom : il utilise un savepoint et journalise un échec d'insertion sans annuler la transaction principale. Une modification de cette politique doit être traitée comme un changement de comportement.

Test ciblé : `NotificationControllerVisibilityTest` vérifie le filtrage des notifications. Pour changer la synthèse, vérifier aussi le contrat JSON consommé par `DashboardPage.jsx` et les tests frontend du dashboard.
