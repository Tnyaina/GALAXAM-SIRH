# GALAXAM SIRH Desktop

Prototype macOS ARM64 construit avec Tauri 2, React, Spring Boot, Java 17 et PostgreSQL 15.19. Le Web continue d'utiliser le même frontend React et le backend Spring Boot Docker.

## Construire

Prérequis : macOS ARM64, Node.js/npm, Rust/Cargo, Maven et un JDK Temurin 17 avec `jlink`.

```sh
cd desktop
npm ci
npm run build
```

Le build prépare le JAR et le runtime Java, compile ou valide PostgreSQL, compile React puis produit le `.app` et le `.dmg` dans `src-tauri/target/release/bundle/`. Ce build local n'est pas signé Developer ID ni notarisé.

## Connexion au Web Docker

La Web Docker reste disponible sur `http://localhost:8088`. Le backend n'est pas publié directement sur le réseau. Pour que le desktop utilise la même API, démarrer le relais local dédié :

```sh
docker compose -f docker-compose.yml -f docker-compose.desktop.yml --profile desktop up -d --no-deps desktop-api
```

Dans le choix de connexion desktop, utiliser `http://127.0.0.1:18080`. L'API est appelée sous `/api/`. L'adresse `http://127.0.0.1:8088` fonctionne aussi tant que le Nginx Web Docker est actif. Les deux adresses ne doivent pas être remplacées par une URL de site ou une page d'accueil.

## Données et services locaux

En mode local, PostgreSQL et Spring Boot utilisent les ressources du bundle et un LaunchAgent utilisateur macOS. Les bases, documents, configurations et journaux restent dans `~/Library/Application Support/GALAXAM SIRH/`, hors du `.app`. En mode distant, GALAXAM n'initialise ni cluster local ni backend Java local. Changer de mode ne migre ni ne supprime les données.

Fermer la fenêtre ne désenregistre pas le LaunchAgent ni n'arrête les services locaux. L'arrêt explicite des services se fait dans **Paramètres → Connexion et données**. Les tâches Spring ne tournent pas pendant que le Mac est en veille ou après fermeture de la session utilisateur.

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
