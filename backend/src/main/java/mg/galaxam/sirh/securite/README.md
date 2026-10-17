# Domaine Sécurité

Ce dossier regroupe l'authentification, la gestion des utilisateurs, les tokens JWT et la journalisation d'audit.

- `api/` : contrôleurs REST (`AuthController`, `UtilisateurController`)
- `api/dto/` : contrats d'entrée/sortie (`LoginRequest`, `RegisterRequest`, `UtilisateurRequest`, `UtilisateurResponse`)
- Composants de sécurité Spring & JWT : `JwtService`, `JwtAuthenticationFilter`, `CustomUserDetailsService`, `CustomUserDetails`
- Persistance & audit : `Utilisateur`, `AuditLog`, `JournalConnexion`, `AdminAccountSeeder`

Les routes publiques restent sous `/auth/*` et `/utilisateurs/*`.

## Bootstrap du premier administrateur

Le bootstrap est désactivé par défaut. Pour provisionner explicitement le premier compte, fournir `BOOTSTRAP_ADMIN_ENABLED=true`, `GALAXAM_DEFAULT_ADMIN_NAME`, `GALAXAM_DEFAULT_ADMIN_EMAIL` et `GALAXAM_DEFAULT_ADMIN_PASSWORD` par l'environnement du serveur ou un gestionnaire de secrets. Ne pas ajouter ces secrets aux fichiers suivis par Git.

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

Le test de référence est `UtilisateurSessionLifecycleIntegrationTest`.
