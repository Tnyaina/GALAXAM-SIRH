# GALAXAM SIRH — prototype desktop macOS ARM64

Le bundle Tauri contient le build React, le JAR Spring Boot et un runtime Java 17
minimal construit avec `jlink`. Il ne contient ni PostgreSQL, ni documents, ni
identifiants. Le serveur de la WebView écoute sur `127.0.0.1:18888` et relaie
`/api/` vers le backend sélectionné. Le mode par défaut reste **distant** : il
utilise le proxy Docker de développement sur `127.0.0.1:18080` et ne lance pas
Java. Le mode local doit être sélectionné explicitement.

## Construction

Prérequis de construction : macOS ARM64, JDK Temurin 17 avec `jlink`, Maven,
Node.js, Rust et les dépendances de compilation Tauri 2. Depuis `desktop/` :

```sh
npm ci
npm run build
open 'src-tauri/target/release/bundle/macos/GALAXAM SIRH.app'
```

`npm run build` compile le JAR, construit le runtime Java, compile React et
produit le `.app`. `npm run dev` exécute les mêmes préparations avant le lanceur
Tauri de développement. Le dossier généré `desktop/bundle/` est ignoré par Git.
Le runtime provient du JDK de `/usr/libexec/java_home -v 17`, ou du JDK désigné
par `GALAXAM_BUILD_JDK_HOME`. Les notices de licence Java sont conservées dans
`Contents/Resources/backend/runtime-licenses.zip`.

## Mode distant : backend Docker ou serveur client

Sans `desktop.conf`, l'application utilise `http://127.0.0.1:18080`. Pour le
prototype Docker, démarrer la pile habituelle et le relais local :

```sh
docker compose -f docker-compose.yml -f docker-compose.desktop.yml --profile desktop up -d --no-deps desktop-api
curl http://127.0.0.1:18080/api/auth/ping
```

Le relais `desktop-api` écoute uniquement sur loopback. Pour une API sur un
serveur client, créer le fichier
`~/Library/Application Support/GALAXAM SIRH/config/desktop.conf` :

```ini
mode=remote
remote-api=https://serveur.example
```

L'URL distante doit être HTTPS, sans chemin ni identifiant dans l'URL. Une URL
HTTP n'est acceptée que pour `127.0.0.1` ou `localhost`. Le backend distant doit
accepter le protocole d'authentification et les cookies attendus par le proxy.
Cette connexion à un vrai serveur distant reste à valider de bout en bout.

## Mode local : Java embarqué et PostgreSQL externe

Créer `~/Library/Application Support/GALAXAM SIRH/config/desktop.conf` :

```ini
mode=local
local-port=18080
```

Créer dans le même dossier `backend.env`, avec ces quatre noms de variables :

```ini
GALAXAM_DESKTOP_DB_URL=jdbc:postgresql://127.0.0.1:PORT/BASE_ISOLEE
GALAXAM_DESKTOP_DB_USER=UTILISATEUR
GALAXAM_DESKTOP_DB_PASSWORD=VALEUR_A_FOURNIR_HORS_DEPOT
GALAXAM_DESKTOP_JWT_SECRET=VALEUR_A_FOURNIR_HORS_DEPOT
```

Le fichier `backend.env` doit appartenir à l'utilisateur et être limité par
`chmod 600`. Ne jamais le placer dans le dépôt ou le bundle. Le secret JWT doit
respecter la longueur exigée par le backend. Le lanceur transmet ces valeurs
par l'environnement du processus enfant, sans les placer dans les arguments.
Avant le démarrage local, libérer le port choisi, notamment en arrêtant le
relais `desktop-api` s'il utilise `18080`. **Ne jamais démarrer le backend
embarqué sur la même base métier qu'un autre backend actif** : les tâches
planifiées seraient concurrentes.

Le lanceur refuse un port déjà occupé, démarre le JAR avec le Java du bundle,
attend `/api/auth/ping`, puis affiche l'application. En cas d'échec, une page
indique de consulter `logs/backend-console.log` sous la racine des données.
Les journaux applicatifs sont dans `logs/backend.log`. La fermeture de la fenêtre
arrête le backend enfant ; ses tâches planifiées s'arrêtent donc aussi. Leur
continuité exigera un service indépendant dans une phase ultérieure.

Les données et documents sont stockés hors du bundle, sous
`~/Library/Application Support/GALAXAM SIRH/` : `config/`, `data/`, `logs/`
et `documents/`. Le chemin peut être remplacé par la variable de test
`GALAXAM_DESKTOP_HOME` (chemin absolu). Aucun document existant n'est déplacé.

## Limites connues

- Ce build local est signé ad hoc et n'est pas notarisé. La distribution à des
  clients macOS exige une identité Developer ID, la signature des exécutables
  embarqués et la notarisation.
- Le serveur HTTP loopback reste accessible aux autres processus du poste. Les
  contrôles Host et Origin ne protègent pas contre un processus local hostile.
- La réservation du port est libérée juste avant le lancement de Java ; une
  course très courte reste possible si un autre processus s'y attache.
- Le mode local ne garantit pas les tâches planifiées quand la fenêtre est
  fermée. Aucun service système permanent n'est installé.
- Le script `jlink` et le paquet validé ciblent macOS ARM64. Windows nécessitera
  un JDK 17 Windows, `java.exe`, un package NSIS, des ACL et un arrêt propre
  adaptés. La structure `backend/runtime` et `backend/backend.jar` est prévue
  pour ce portage.
- `DocumentRhService` contient encore un chemin absolu de secours historique ;
  les modèles du classpath sont prioritaires. Sa migration demande une stratégie
  pour les anciens documents et des tests spécifiques.

Les parcours image privée, téléchargement, PDF, impression, fenêtres secondaires,
expiration naturelle de session et authentification sur une base de test isolée
n'ont pas été validés dans ce build. Le prototype précédent a validé connexion,
navigation protégée, reprise de session et déconnexion avec le backend Docker.
