# Recette de production WEB sur le Mac

L'instance utilise exactement le Compose de production, le profil Spring `prod`, Nginx et PostgreSQL 15. Son URL est `http://localhost:18089`. Elle a sa propre base `galaxam_mac_prodtest_pg_data` et son propre volume de documents `galaxam_mac_prodtest_business_files`. Le développement reste sur `http://localhost:8088` avec ses propres volumes.

La configuration privée est `.env.production.test`, ignorée par Git, créée avec des secrets aléatoires. Après la création du premier compte, le bootstrap a été désactivé et le mot de passe de connexion est conservé dans `.local-prod-test-credentials`, également privé et ignoré par Git. Pour consulter **uniquement** l'identifiant et le mot de passe de la recette dans votre terminal local :

```sh
./scripts/mac-prod-test/show-login.sh
```

Pour lancer ou reprendre la recette :

```sh
docker compose --env-file .env.production.test -f docker-compose.web.yml up -d --build --wait
```

Pour vérifier l'état :

```sh
docker compose --env-file .env.production.test -f docker-compose.web.yml ps
curl http://localhost:18089/api/auth/ping
```

La sauvegarde et la restauration **de cette recette seulement** :

```sh
./scripts/mac-prod-test/backup.sh
./scripts/mac-prod-test/restore.sh /CHEMIN/ABSOLU/DU/PROJET/local-prod-test-backups/galaxam-mac-prodtest-AAAAMMJJ-HHMMSS.tar
```

Les archives locales sont dans `local-prod-test-backups/`, ignoré par Git. Le script de restauration crée une autre sauvegarde avant remplacement. Faire ces opérations lorsque personne ne saisit de données : le web est arrêté pendant l'export et la restauration. Si une restauration échoue après l'arrêt, elle laisse le web arrêté pour permettre un diagnostic.

Le bootstrap administrateur est désactivé dans `.env.production.test`. Le fichier `.local-prod-test-credentials` sert seulement aux essais sur le Mac ; garder les identifiants dans un coffre si vous souhaitez les conserver au-delà de cette recette.

Ce test confirme les images et la configuration web de production sur macOS. Les tâches Windows, WSL, les droits des fichiers Windows et le disque de sauvegarde du PC cible devront toujours être validés sur ce PC avant la remise.
