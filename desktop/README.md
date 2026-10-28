# GALAXAM SIRH Desktop

Le client desktop Tauri 2 partage le frontend React et Spring Boot avec le Web. Le build macOS ARM64 prépare Java 17 et PostgreSQL 15.19 et produit un `.app`/`.dmg`. Le code Windows x64 comprend également un installateur, un service local et des parcours de sauvegarde/restauration ; les validations automatisées documentées portent sur Windows Server 2022, pas encore sur les installations interactives Windows 10/11.

Le travail nécessaire pour Windows 10/11 est suivi dans [WINDOWS_TODO.md](WINDOWS_TODO.md).
Le découpage en trois sprints et ses critères de validation figurent dans [WINDOWS_SPRINTS.md](WINDOWS_SPRINTS.md). Le build Windows utilise `scripts/build-windows.ps1` sur un hôte Windows x64 avec un JDK Temurin 17, Maven, Node/npm et Rust MSVC ; il demande le chemin et l'empreinte SHA-256 de l'archive PostgreSQL Windows. Le parcours automatisé local a réussi sur le runner Windows Server 2022 ; les essais interactifs sur Windows 10/11 restent nécessaires avant diffusion.

## Organisation du code et préparation de Windows

Le code Rust est rangé par responsabilité :

- `app/` : état partagé, démarrage et localisation des ressources embarquées ;
- `connection/` : configuration et commandes de connexion locale ou distante ;
- `database/` : initialisation et pilotage de PostgreSQL ;
- `backup/` : format d'archive, export et commandes de sauvegarde ;
- `security/` : amorçage administrateur et création du premier compte ;
- `web/` : fichiers du frontend et relais HTTP vers Spring ;
- `platform/` : intégrations au système, avec le superviseur et la planification macOS sous `platform/macos/`.

`src-tauri/src/main.rs` ne garde que l'assemblage Tauri et le démarrage de la fenêtre. Le cycle de vie s'appuie sur des implémentations distinctes dans `platform/macos/` et `platform/windows/` ; `platform/unsupported_service.rs` ne concerne que les systèmes qui n'ont pas d'implémentation dédiée.

Le port Windows dispose maintenant de scripts de build et de composants propres à la plateforme. Les suites CI documentées couvrent le packaging et certains parcours de service, permissions et sauvegarde/restauration. Les essais interactifs Windows 10/11, les transitions de session/redémarrage et la validation de bout en bout du premier administrateur restent à compléter avant de qualifier la diffusion. `WINDOWS_SPRINTS.md` consigne les preuves CI et leurs limites ; `WINDOWS_TODO.md` est une liste de travail à rapprocher de ces résultats. Les paquets et scripts de build restent séparés par système ; les exécutables macOS ne fonctionnent pas sur Windows.

## Construire

Prérequis : macOS ARM64, Node.js/npm, Rust/Cargo, Maven et un JDK Temurin 17 avec `jlink`.

```sh
cd desktop
npm ci
npm run build
```

Le build prépare le JAR et le runtime Java, compile ou valide PostgreSQL, compile React puis produit le `.app` et le `.dmg` dans `src-tauri/target/release/bundle/`. Ce build local n'est pas signé Developer ID ni notarisé.

## Empreinte mémoire desktop

Le runtime Java embarqué est construit avec un tas initial de 64 Mio, un plafond de 512 Mio et le ramasse-miettes Serial, de faible empreinte. Le profil Spring desktop garde de 1 à 5 connexions SQL et limite Tomcat à 32 threads (4 au repos). PostgreSQL est configuré avec 64 Mio de tampons partagés, 2 Mio par opération SQL et 30 connexions maximum. Ces réglages s'appliquent aux deux scripts de préparation du runtime et à la configuration PostgreSQL desktop commune.

Le DMG ARM64 produit pendant la dernière vérification pèse 107,14 Mio. Le plafond Java de 512 Mio et ces réglages n'ont pas encore été mesurés sur un PC Windows peu puissant ni lors d'une exportation RH volumineuse ; ils constituent des plafonds de départ à confirmer avec des mesures représentatives avant livraison Windows.

## Connexion au Web Docker

La Web Docker reste disponible sur `http://localhost:8088`. Le backend n'est pas publié directement sur le réseau. Pour que le desktop utilise la même API, démarrer le relais local dédié :

```sh
docker compose -f docker-compose.dev.yml -f docker-compose.desktop.yml --profile desktop up -d --no-deps desktop-api
```

Dans le choix de connexion desktop, utiliser `http://127.0.0.1:18080`. L'API est appelée sous `/api/`. L'adresse `http://127.0.0.1:8088` fonctionne aussi tant que le Nginx Web Docker est actif. Les deux adresses ne doivent pas être remplacées par une URL de site ou une page d'accueil.

## Données et services locaux

En mode local, PostgreSQL et Spring Boot utilisent les ressources du bundle et un LaunchAgent utilisateur macOS. Les bases, documents, configurations et journaux restent dans `~/Library/Application Support/GALAXAM SIRH/`, hors du `.app`. En mode distant, GALAXAM n'initialise ni cluster local ni backend Java local. Changer de mode ne migre ni ne supprime les données.

Fermer la fenêtre ne désenregistre pas le LaunchAgent ni n'arrête les services locaux. L'arrêt explicite des services se fait dans **Paramètres → Connexion et données**. Les tâches Spring ne tournent pas pendant que le Mac est en veille ou après fermeture de la session utilisateur.

Lors du passage du service local `0.1.4` à `0.1.5`, le prochain lancement copie les nouveaux composants, attend l'arrêt de l'ancien service, puis démarre le nouveau. Si ce démarrage échoue, l'application tente de rétablir l'ancien service. Les données PostgreSQL et les documents restent dans le même dossier. Toute future modification du JAR ou du runtime embarqué doit augmenter `SERVICE_VERSION` dans `platform/macos/service.rs` et définir la version précédente pour déclencher ce remplacement.

Un administrateur connecté peut créer une archive manuelle depuis **Paramètres → Connexion et données → Sauvegardes de la base locale**. Elle contient un export PostgreSQL au format personnalisé et le dossier des documents. L'archive est publiée dans `~/Library/Application Support/GALAXAM SIRH/backups/` après vérification de sa structure, puis copiée à l'emplacement choisi dans la fenêtre « Enregistrer sous… ».

Un LaunchAgent utilisateur distinct vérifie chaque heure si une sauvegarde automatique est due. Il crée au plus une archive par période de 24 heures dans `backups/`, après contrôle de l'identité du cluster PostgreSQL, et conserve les sept dernières archives automatiques. Les archives manuelles ne sont pas supprimées par cette rotation. Les erreurs et les réussites sont consignées dans `logs/backup.log`. La tâche est désactivée avec les services locaux. Aucune tâche ne peut s'exécuter pendant que le Mac est éteint ; une tentative a lieu après son prochain démarrage si les services locaux sont actifs.

Un administrateur peut restaurer une archive `.tar` depuis **Paramètres → Connexion et données → Sauvegardes de la base locale**. L'application valide le manifeste et les chemins, rejette les liens et crée une archive de sécurité avant l'opération. La restauration remplace la base et le dossier des documents et redémarre les services. En cas d'échec de la restauration de la base, l'application tente un retour arrière ; si ce retour arrière échoue, les services restent arrêtés et l'archive de sécurité est conservée dans `backups/`. Les sauvegardes capturent la base et les documents en deux étapes, donc les fichiers modifiés pendant l'export ne forment pas une image atomique.

## Nettoyer les fichiers générés

Le staging Java/PostgreSQL, les dépendances npm et les sorties Rust/Tauri sont reproductibles et ignorés par Git. Pour libérer l'espace après avoir conservé l'installateur souhaité :

```sh
cd desktop
rm -rf node_modules bundle src-tauri/target
```

Après ce nettoyage, `npm ci` réinstalle le CLI Tauri. `npm run build` recrée Java et le JAR, reconstruit PostgreSQL 15.19 si son staging est absent, puis rebâtit l'application. La première reconstruction de PostgreSQL est plus longue.

## Scripts conservés

- `scripts/prepare-backend-macos.sh` prépare le JAR et Java 17 avec `jlink`.
- `scripts/ensure-postgresql-macos.sh` valide le staging ou déclenche sa construction.
- `scripts/build-postgresql-macos.sh` construit PostgreSQL depuis l'archive officielle vérifiée.
- `scripts/relocate-postgresql-macos.py` rend les bibliothèques PostgreSQL relocalisables.
- `scripts/verify-postgresql-macos.py` vérifie PostgreSQL dans un cluster temporaire isolé.

Les fichiers de données, les volumes Docker et le dossier utilisateur Application Support ne font pas partie du nettoyage du dépôt.
