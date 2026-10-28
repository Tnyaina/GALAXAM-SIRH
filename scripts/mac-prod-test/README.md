# Recette de production WEB sur le Mac

L'instance utilise exactement le Compose de production, le profil Spring `prod`, Nginx et PostgreSQL 15. Son URL est `http://localhost:18089`. Elle a sa propre base `galaxam_mac_prodtest_pg_data` et son propre volume de documents `galaxam_mac_prodtest_business_files`. Le développement reste sur `http://localhost:8088` avec ses propres volumes.

Cette recette isolée utilise quatre variables propres au Mac : `SIRH_MAC_TEST_DB_PASSWORD`, `SIRH_MAC_TEST_JWT_SECRET`, `SIRH_MAC_TEST_ADMIN_EMAIL` et `SIRH_MAC_TEST_ADMIN_PASSWORD`. Définissez-les dans l'environnement utilisateur macOS avant de lancer les scripts. Reprenez le mot de passe PostgreSQL de la recette existante si son volume contient déjà des données ; ne le régénérez pas. Pour afficher les identifiants de la recette dans le terminal :

```sh
./scripts/mac-prod-test/show-login.sh
```

Pour lancer ou reprendre la recette :

```sh
. ./scripts/mac-prod-test/common.sh
compose up -d --build --wait
```

Pour vérifier l'état :

```sh
. ./scripts/mac-prod-test/common.sh
compose ps
curl http://localhost:18089/api/auth/ping
```

La sauvegarde et la restauration **de cette recette seulement** :

```sh
./scripts/mac-prod-test/backup.sh
./scripts/mac-prod-test/restore.sh /CHEMIN/ABSOLU/DU/PROJET/local-prod-test-backups/galaxam-mac-prodtest-AAAAMMJJ-HHMMSS.tar
```

Les archives locales sont dans `local-prod-test-backups/`, ignoré par Git. Le script de restauration crée une autre sauvegarde avant remplacement. Faire ces opérations lorsque personne ne saisit de données : le web est arrêté pendant l'export et la restauration. Si une restauration échoue après l'arrêt, elle laisse le web arrêté pour permettre un diagnostic.

Le bootstrap administrateur est idempotent et n'altère pas un administrateur actif existant. Conservez les valeurs de la recette dans un coffre pour pouvoir retrouver ses données.

Ce test confirme les images et la configuration web de production sur macOS. Les tâches Windows, WSL, les droits des fichiers Windows et le disque de sauvegarde du PC cible devront toujours être validés sur ce PC avant la remise.
