# Validation du parcours web — 27 octobre 2026

> Mise à jour Windows du 28 octobre 2026 : le lancement courant utilise les variables utilisateur `SIRH_PROD_*` et `SIRH_DEV_*`, sans fichiers `.env`. Les deux piles ont démarré et répondu sur 8088 et 18088 ; connexion administrateur validée dans chaque mode. Les tâches Windows de démarrage et de sauvegarde ont fini avec `LastTaskResult = 0`. La sauvegarde a été extraite et ses empreintes vérifiées. Les notes ci-dessous décrivent les essais historiques du parcours précédent ; leurs références à `.env.production` ne décrivent plus la configuration courante.

## Exécuté dans l'environnement de développement

- Construction effective des images de production Java 17 / Nginx / frontend : réussie.
- Démarrage d'une stack isolée `galaxam-web-audit`, port 18089, volumes de test distincts : réussi. La stack de développement sur 8088 est restée disponible.
- Initialisation des six schémas, vérification SHA-256 des sept fichiers canoniques montés, migration de départ et création du premier administrateur : réussies.
- HTTP `/api/auth/ping`, connexion, cookie HttpOnly/SameSite=Lax et renouvellement : réussis.
- Prérequête CORS autorisée acceptée, origine inconnue refusée : réussies.
- Simulation du protocole transmis HTTPS : cookie Secure/SameSite=None obtenu. Ceci ne teste ni un vrai certificat ni le DNS de l'entreprise.
- Défaut détecté et corrigé : deux connexions dans la même seconde pouvaient créer un refresh token identique et provoquer une violation d'unicité. Ajout d'un identifiant JWT aléatoire.
- Test Java `RefreshTokenUniquenessTest` : 1 test, 0 échec, 0 erreur ; 30 jetons indépendants vérifiés.
- Migration d'ajout de colonne sur une donnée témoin : valeur conservée. Deuxième passage sans réapplication.
- Modification d'une migration déjà appliquée : refusée.
- Migration contenant un SQL invalide après un ALTER : transaction annulée, colonne absente.
- Export PostgreSQL puis restauration : valeur d'origine retrouvée ; table ajoutée après l'export absente après restauration.
- Analyse syntaxique de tous les scripts PowerShell sous PowerShell 7.4 : réussie.
- Prévalidation PowerShell effective contre Docker : réussie.
- Exécution réelle de `backup.ps1` puis `restore.ps1` sous PowerShell 7.4 dans un conteneur de test, avec Docker CLI : réussie. Le seul ajustement du banc d'essai est l'URL du contrôle de santé (`host.docker.internal` au lieu de `localhost`, car le client de test est lui-même dans un conteneur).
- Après modification volontaire de la donnée témoin et d'un document caché, la restauration PowerShell a rétabli les deux valeurs originales. Une copie de sécurité préalable a bien été produite et le web a redémarré.
- `docker compose up -d --wait` pour la pile complète : réussi.
- `git diff --check` : réussi.

## Vérifications encore nécessaires sur le PC cible

- Windows 10/11 exact, WSL, Docker Desktop, droits et montage des fichiers depuis Windows.
- Exécution PowerShell 5.1 native, création/ACL de `.env.production`, raccourci et tâches Windows. La syntaxe native est prévue dans le workflow GitHub, qui n'a pas été exécuté à distance dans cette session.
- Disponibilité du support de sauvegarde, droits du partage éventuel, espace disque et reprise après redémarrage Windows.
- Mise à jour complète par un tag Git distant réellement publié. Le script a été préparé ; aucun commit/tag n'a été publié et aucun dépôt de production n'a été changé pendant ce travail.
- Recette métier sur les données réellement prévues à la livraison.
- HTTPS réel si l'accès réseau est demandé : certificat reconnu, DNS, proxy et renouvellement.

Le workflow `.github/workflows/web-production.yml` ajoute les contrôles de production sur les PR concernées et les tags `v*`. Attendre son résultat avant d'utiliser un tag en production. Il ne déploie pas automatiquement sur le PC client.

## Recette web de production sur le Mac — 27 octobre 2026

- Stack de production lancée sur `http://localhost:18089`, profil Spring `prod`, images frontend/backend reconstruites : réussie.
- Volumes dédiés `galaxam_mac_prodtest_pg_data` et `galaxam_mac_prodtest_business_files` : confirmés ; ports de développement 8088/5433 toujours actifs séparément.
- Bootstrap administrateur utilisé une fois puis désactivé ; reconnexion après redémarrage backend : réussie. Identifiants de recette conservés dans `.local-prod-test-credentials` (privé et ignoré Git).
- Connexion répétée dans la même seconde, cookie local, refresh, CORS autorisé/refusé : réussis.
- `backup.sh` a réellement exporté la base et l'ensemble des documents. Après modification volontaire de lignes SQL et d'un fichier caché, `restore.sh` a rétabli les deux témoins. Les témoins ont ensuite été supprimés.
- Reconstruction et recréation de la pile complète avec les volumes conservés : API saine ; une valeur SQL et un document vérifiés avant le redéploiement étaient encore présents ensuite. Témoins supprimés.
- La recette Mac demeure active sur le port 18089. Elle permet des essais d'usage sur le profil de production. Elle ne couvre pas encore les tâches Windows, le disque externe Windows, un certificat HTTPS réel ou une migration métier de livraison distincte.
