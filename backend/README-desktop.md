# Backend natif macOS — préparation de phase 3

Le profil `desktop` exécute le JAR Spring Boot avec Java 17 et PostgreSQL 15.
Il ne contient aucun identifiant. Il n'installe ni service système ni base locale.

## Configuration

Variables obligatoires fournies par le lanceur ou le terminal :

| Variable | Rôle |
| --- | --- |
| `GALAXAM_DESKTOP_DB_URL` | URL JDBC PostgreSQL explicite |
| `GALAXAM_DESKTOP_DB_USER` | Utilisateur PostgreSQL |
| `GALAXAM_DESKTOP_DB_PASSWORD` | Mot de passe PostgreSQL |
| `GALAXAM_DESKTOP_JWT_SECRET` | Secret JWT, au moins 64 octets |

Variables facultatives : `GALAXAM_DESKTOP_HOME`, `GALAXAM_BACKEND_PORT`
(défaut `18080`), `GALAXAM_DESKTOP_CORS_ORIGIN` (défaut
`http://127.0.0.1:18888`), `GALAXAM_DESKTOP_LOG_FILE` et `GALAXAM_JAVA_BIN`.
Pour conserver les sessions existantes, utiliser le même secret JWT que le
backend remplacé. Ne pas l'écrire dans le dépôt, dans les arguments de commande
ou dans des journaux.

La racine par défaut sur macOS est
`~/Library/Application Support/GALAXAM SIRH/`. Le lanceur crée avec un masque
privé `config/`, `data/`, `logs/`, `documents/photo/`,
`documents/documents/` et `documents/prestataires/`. Les fichiers restent en
dehors du JAR et du bundle Tauri. Pour Windows, définir
`GALAXAM_DESKTOP_HOME` vers un chemin absolu adapté, par exemple sous
`%APPDATA%`; un lanceur Windows devra créer ces sous-répertoires et appliquer
les ACL appropriées. Les répertoires `config/` et `data/` sont réservés pour la
configuration et les données nécessaires aux étapes suivantes.

## Construire et démarrer

```bash
cd backend
mvn -DskipTests package
# Fournir les quatre variables obligatoires dans l'environnement.
./run-desktop-macos.sh
```

Le backend écoute uniquement sur `127.0.0.1`. Son endpoint public de contrôle
est `http://127.0.0.1:18080/api/auth/ping` avec le port par défaut. Le
processus s'arrête avec `Ctrl+C` ou `SIGTERM`; Spring ferme ensuite le pool JDBC.
Le journal applicatif va dans `logs/backend.log` sous la racine choisie.

Le serveur Rust de Tauri relaie `/api/` vers `127.0.0.1:18080`. Pour utiliser
le backend natif à ce port, arrêter d'abord le service Docker `desktop-api`.
Avant d'utiliser une base métier existante, arrêter aussi le backend Docker :
les deux processus ne doivent jamais exécuter leurs tâches planifiées sur la
même base. Le conteneur PostgreSQL peut rester actif et expose actuellement
`127.0.0.1:5433` sur l'hôte. Aucun volume n'a besoin d'être supprimé.

## Limites de cette phase

- Le port fixe évite deux instances natives sur le même poste. Il ne protège
  pas contre un autre backend sur un autre poste ou un autre port connecté à la
  même base. Une coordination entre processus reste à concevoir.
- Les tâches planifiées et le rattrapage démarrent avec le backend. Lorsque le
  processus s'arrête, ils s'arrêtent aussi. Un service indépendant de la fenêtre
  desktop sera nécessaire pour assurer leur continuité.
- Les modèles de documents intégrés sont chargés depuis le classpath du JAR.
  `DocumentRhService` contient encore un chemin absolu de secours propre au
  poste de développement ; ce secours n'est pas portable et doit être retiré
  ou rendu configurable avant une distribution autonome.
- Les chemins relatifs de compatibilité `uploads/photos` ne servent qu'à lire
  d'anciens fichiers. Une migration documentée sera nécessaire si ces fichiers
  doivent être repris dans le stockage desktop.
