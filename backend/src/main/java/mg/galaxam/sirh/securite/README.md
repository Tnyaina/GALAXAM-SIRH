# Domaine Sécurité

Ce dossier regroupe l'authentification, la gestion des utilisateurs, les tokens JWT et la journalisation d'audit.

- `api/` : contrôleurs REST (`AuthController`, `UtilisateurController`)
- `api/dto/` : contrats d'entrée/sortie (`LoginRequest`, `RegisterRequest`, `UtilisateurRequest`, `UtilisateurResponse`)
- Composants de sécurité Spring & JWT : `JwtService`, `JwtAuthenticationFilter`, `CustomUserDetailsService`, `CustomUserDetails`
- Persistance & audit : `Utilisateur`, `AuditLog`, `JournalConnexion`, `AdminAccountSeeder`

Les routes publiques restent sous `/auth/*` et `/utilisateurs/*`.

## Bootstrap du premier administrateur

Le bootstrap est désactivé par défaut. Pour provisionner explicitement le premier compte, fournir `BOOTSTRAP_ADMIN_ENABLED=true`, `GALAXAM_DEFAULT_ADMIN_NAME`, `GALAXAM_DEFAULT_ADMIN_EMAIL` et `GALAXAM_DEFAULT_ADMIN_PASSWORD` par l'environnement du serveur ou un gestionnaire de secrets. Ne pas ajouter ces secrets aux fichiers suivis par Git.
