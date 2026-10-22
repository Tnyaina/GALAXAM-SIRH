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
compte et vérifie `isEnabled()` à chaque requête : un access token existant est
refusé pendant la désactivation. En revanche, faute de version de session ou de
date de révocation, un ancien access token non expiré peut redevenir accepté
après réactivation. La suppression de l'ancien refresh token, elle, reste
effective.

Le test de référence est `IdleSessionServicePostgresTest`.
