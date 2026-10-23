# Domaine Sécurité

Ce dossier regroupe l'authentification, la gestion des utilisateurs, les tokens JWT et la journalisation d'audit.

- `api/` : contrôleurs REST (`AuthController`, `UtilisateurController`)
- `api/dto/` : contrats d'entrée/sortie (`LoginRequest`, `RegisterRequest`, `UtilisateurRequest`, `UtilisateurResponse`)
- Composants de sécurité Spring & JWT : `JwtService`, `JwtAuthenticationFilter`, `CustomUserDetailsService`, `CustomUserDetails`
- Persistance & audit : `Utilisateur`, `AuditLog`, `JournalConnexion`, `AdminAccountSeeder`

Les routes publiques restent sous `/auth/*` et `/utilisateurs/*`.

## Bootstrap du premier administrateur

Le bootstrap Spring est désactivé par défaut dans `application.properties`. En mode Docker intégré, `docker-compose.yml` transmet les valeurs du `.env` racine : `GALAXAM_BOOTSTRAP_ADMIN_ENABLED`, `GALAXAM_DEFAULT_ADMIN_NAME`, `GALAXAM_DEFAULT_ADMIN_EMAIL` et `GALAXAM_DEFAULT_ADMIN_PASSWORD`. Ce fichier local est ignoré par Git ; ne pas ajouter les secrets aux documents ou fichiers suivis.

Lorsque le bootstrap est activé, `AdminAccountSeeder` crée le compte seulement si aucun administrateur actif n'existe. Un administrateur déjà actif conserve son identité et son mot de passe. Après l'initialisation, désactiver le bootstrap dans `.env` et recréer le backend pour appliquer la variable. Le mode de développement hors Docker possède ses propres paramètres dans `application-local.properties`.

## Cycle de vie du compte et sessions

La procédure normale pour retirer un accès est la désactivation via
`UtilisateurController`, qui efface le refresh token et son expiration. La
réactivation ne restaure pas l'ancienne session : l'utilisateur doit se
connecter à nouveau. Le refresh est accepté uniquement si le compte est actif
et si le jeton correspond encore à celui enregistré en base. Le logout efface
également le jeton persisté.

La suppression physique via `Utilisateur.delete` est interdite afin d'éviter la
perte silencieuse des historiques liés au compte. Les journaux de connexion et
d'audit restent donc associés à l'identité d'origine. Le filtre JWT recharge le
compte et vérifie `isEnabled()` ainsi que la session `sid` à chaque requête.
La désactivation révoque les sessions en base : un ancien access token reste
refusé après réactivation. Une nouvelle connexion crée une nouvelle session.

Les requêtes de renouvellement et de déconnexion par cookie exigent une origine
exactement autorisée par `app.cors.allowed-origins` (en-tête `Origin`, ou
`Referer` en repli). Une origine absente ou différente est refusée. Les écritures
d'audit échouées remontent désormais une erreur ; les opérations qui utilisent
la même transaction JDBC doivent alors être annulées.

La connexion limite à dix les échecs par adresse e-mail normalisée sur une
fenêtre de quinze minutes. Le compteur est en mémoire et borné ; il protège
l'instance Compose unique. En cas de déploiement multi-instance, appliquer aussi
une limite partagée au proxy d'entrée ou déplacer ce compteur vers un stockage
partagé. Le proxy TLS public doit remplacer `X-Forwarded-Proto` et transmettre
sa valeur à Nginx pour que le cookie de renouvellement conserve l'attribut
`Secure`.

Le test de référence est `IdleSessionServicePostgresTest`.

Les écritures métier qui exigent un auteur échouent si aucun `CustomUserDetails` n'est présent dans le contexte de sécurité. L'identifiant du compte administrateur n'est jamais utilisé comme valeur de remplacement. Les permissions complètes implicites ne sont accordées qu'au code de rôle exact `ROLE_ADMIN`.
