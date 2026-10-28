# GALAXAM web local sans Docker (Windows x64)

Ce parcours est en cours de construction. Il vise un PC Windows vierge avec Java 17 et PostgreSQL 15 installés globalement, un frontend React compilé et une interface dans le navigateur local. Il est distinct de l'installation web Docker et du client Tauri.

## Prérequis pour un PC qui sert aussi au développement

- Windows x64 et PowerShell ; droits d'écriture dans le dossier de travail.
- JDK 17 avec `jlink` et Maven 3.9 pour compiler Spring Boot.
- Node.js 22 avec npm pour compiler et lancer React/Vite.
- PostgreSQL 15, avec les outils `initdb`, `pg_ctl`, `psql`, `pg_dump` et `pg_restore`.
- Espace disque pour les outils, les dépendances Maven/npm, la base, les documents et les sauvegardes.

Les archives téléchargées sont conservées dans `scripts/windows-native/downloads/` (ignoré par Git). Les copies décompressées provisoires ont été supprimées après l'installation globale.

Pour installer ces quatre outils **pour toute la machine**, lancer PowerShell en administrateur puis exécuter `scripts/windows-native/install-global.ps1`. Le script vérifie les archives et installe les versions ci-dessus dans `C:\Program Files\GALAXAM\Toolchain`, puis configure `JAVA_HOME` et le `PATH` système. Fermer et rouvrir la session Windows si un nouveau terminal ne voit pas encore les variables. Le cluster et le service PostgreSQL sont décrits ci-dessous.

## PostgreSQL local sur ce PC

Un cluster PostgreSQL 15.19 est initialisé dans `C:\ProgramData\GALAXAM\PostgreSQL15\data`. Le service Windows `galaxam-postgresql-15` démarre automatiquement, écoute uniquement sur `127.0.0.1:5432` et utilise l'authentification SCRAM.

pgAdmin contient l'entrée `GALAXAM local - PostgreSQL 15` (hôte `127.0.0.1`, port `5432`, base de maintenance `postgres`, utilisateur `postgres`). Son mot de passe a été défini localement par l'utilisateur et n'est pas conservé dans le dépôt. L'entrée pgAdmin n'enregistre pas ce secret.

## Variables communes Docker et hors Docker

Les mêmes variables utilisateur Windows `SIRH_DEV_*`, `SIRH_PROD_*` et `SIRH_ADMIN_*` servent dans les deux parcours. Docker Compose les transmet aux conteneurs ; Spring les lit aussi directement en mode natif. Les secrets DEV et PROD restent distincts.

| Mode | Variables requises | Base native par défaut |
|---|---|---|
| DEV | `SIRH_DEV_DB_PASSWORD`, `SIRH_DEV_JWT_SECRET`, `SIRH_DEV_WEB_PORT`, `SIRH_DEV_BOOTSTRAP_ADMIN_ENABLED`, `SIRH_ADMIN_*` | `sirh_galaxam_dev`, rôle `galaxam_dev_admin` |
| PROD | `SIRH_PROD_DB_PASSWORD`, `SIRH_PROD_JWT_SECRET`, `SIRH_PROD_WEB_PORT`, `SIRH_PROD_CORS_ORIGIN`, `SIRH_PROD_BOOTSTRAP_ADMIN_ENABLED`, `SIRH_ADMIN_*` | `sirh_galaxam`, rôle `galaxam_prod_admin` |

Sur ce PC, la base native DEV `sirh_galaxam_dev` a été créée dans PostgreSQL 15 avec le rôle `galaxam_dev_admin`. Les six schémas canoniques y ont été appliqués. La base native PROD `sirh_galaxam` a été créée dans pgAdmin et initialisée avec `initialize-prod.ps1`, qui refuse toute base contenant déjà des tables.

## Réinitialiser une base et redémarrer le backend

Depuis CMD, à la racine du projet :

```cmd
scripts\windows-native\reset-dev.bat
scripts\windows-native\reset-prod.bat
```

Exécuter **une seule** de ces commandes selon la base voulue. Le script demande de saisir exactement le nom de la base (`sirh_galaxam_dev` ou `sirh_galaxam`) puis le mot de passe du superutilisateur PostgreSQL `postgres`. Il arrête le backend GALAXAM sur le port 8080, supprime le schéma `public`, puis réexécute les six fichiers `backend/conception/bdd/01_*.sql` à `06_*.sql`, dans le même ordre que `docker/init-local.sql`. Les six fichiers sont appliqués dans une transaction : en cas d'erreur, la remise à zéro est annulée. Le backend redémarre ensuite avec le profil `local` (DEV) ou `prod` (PROD).

Le script PROD crée auparavant une sauvegarde `pg_dump` dans `scripts/windows-native/backups/`. Les journaux du backend sont dans `scripts/windows-native/logs/`. Ces deux dossiers sont ignorés par Git. La sauvegarde couvre PostgreSQL ; les éventuels fichiers joints stockés hors de la base demandent une sauvegarde séparée. Cette commande efface toutes les données métier de la base ciblée. Docker conserve sa propre base `sirh_galaxam` dans un volume séparé.

## Lancement en un clic pour l'utilisateur

Sur le PC actuellement configuré, le paquet installé est dans `D:\GALAXAM-GRH`. Les deux raccourcis Bureau pointent vers ce dossier ; ils n'ont plus besoin du dépôt `D:\GALAXAM-SIRH` pour lancer l'application. Le paquet de distribution est préparé par `package-grh.ps1` dans `scripts/windows-native/dist/GRH-package/` et `GRH-package.zip`. L'administrateur lance `Installer-GRH.bat` depuis le paquet extrait. Les scripts techniques restent dans `D:\GALAXAM-GRH\scripts`, sans raccourci Bureau.

L'administrateur prépare les fichiers compilés avec `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\windows-native\prepare-app.ps1`, construit le paquet avec `package-grh.ps1`, puis lance `Installer-GRH.bat` depuis le paquet extrait. Sur une base PROD absente ou vide, l'installateur demande le mot de passe PostgreSQL `postgres`, crée la base si nécessaire, charge les six DDL et applique les migrations versionnées. Il refuse une base contenant déjà des tables si le rôle applicatif ne peut pas y accéder. Les variables utilisateur Windows PROD et le premier compte administrateur doivent être configurés avant cette opération. Ne lancez pas le script de réinitialisation pour ouvrir l'application : il efface les données.

L'utilisateur double-clique ensuite sur **GRH** sur le Bureau, ou sur `scripts\windows-native\GALAXAM.bat`. Le lanceur vérifie le service PostgreSQL, démarre le JAR Spring et le serveur web local s'ils sont absents, attend leur disponibilité puis ouvre le navigateur sur `http://127.0.0.1:18089/`. Plusieurs doubles clics réutilisent les processus déjà actifs. Le port `18089` évite le port `8088` de Docker PROD. Les journaux sont dans `scripts/windows-native/logs/`. L'installation et la préparation initiale restent des tâches de l'administrateur ; le raccourci ne les remplace pas.

Pour éteindre GRH, utiliser le raccourci Bureau **Arrêter GRH** (ou `scripts\windows-native\Arreter-GRH.bat`). Il ferme uniquement le backend et le petit serveur web ; le service PostgreSQL reste actif. Fermer l'onglet du navigateur ne suffit pas à arrêter ces processus. Plusieurs clics sur **GRH** ne créent pas plusieurs serveurs : les lancements sont synchronisés et chaque clic ouvre simplement l'application dans le navigateur quand elle est prête.

## Sauvegarde GRH sans Docker

Le raccourci Bureau s'appelle **GRH**. Sa base native `sirh_galaxam` et le dossier `uploads/` sont sauvegardés dans `D:\GALAXAM-Sauvegardes\GRH-native\`, séparément des archives Docker du dossier parent. Une tâche Windows **GALAXAM GRH - sauvegarde quotidienne** est planifiée à 19 h. Elle fonctionne sous la session de l'utilisateur Windows ; le PC doit être allumé et la session ouverte (elle peut être verrouillée). Si le PC est éteint à 19 h, le Planificateur tente de rattraper la tâche à la prochaine ouverture de session.

Pour une sauvegarde manuelle :

```cmd
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\windows-native\backup-grh.ps1
```

L'archive `grh-YYYYMMDD-HHMMSS-fff.tar` contient `database.dump`, `uploads.tar` et `manifest.json` avec les empreintes SHA-256. Le backend PROD est arrêté pendant l'export pour garder base et fichiers cohérents, puis redémarré s'il était actif. Le script garde les 14 dernières archives de ce dossier. Un échec de redémarrage est signalé ; dans ce cas, relancer **GRH** sur le Bureau.

Si GRH est déjà éteint, la sauvegarde utilise quand même le service PostgreSQL et le dossier `uploads/` ; elle ne démarre pas l'application après l'export. Si le PC est éteint, aucune sauvegarde ne peut s'exécuter avant son prochain démarrage et l'ouverture de session Windows.

Pour vérifier qu'une archive restaure réellement la base dans une base temporaire isolée :

```cmd
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\windows-native\verify-backup-grh.ps1 -Archive "D:\GALAXAM-Sauvegardes\GRH-native\grh-AAAA....tar"
```

Pour restaurer les données de production, un administrateur utilise `restore-grh.ps1 -Archive "CHEMIN_COMPLET"`. Le script vérifie les empreintes, demande de taper `RESTAURER`, crée une sauvegarde de sécurité de l'état courant, restaure la base et les fichiers, puis relance le backend. Toute saisie effectuée après l'archive restaurée sera perdue.

Ces archives ne sont pas chiffrées. Comme la base et les sauvegardes sont toutes deux sur le disque D:, une panne de ce disque peut les perdre ensemble. Prévoir ensuite une copie régulière sur un disque externe ou un autre emplacement.

## Mise à jour locale depuis le code DEV, sans GitHub

Le script `Mettre-a-jour-GRH.bat` compile le backend et le frontend du dossier `D:\GALAXAM-SIRH`, crée une sauvegarde native de la base et des fichiers, arrête GRH, installe les nouveaux fichiers dans `D:\GALAXAM-GRH\app`, puis redémarre et vérifie l'application. Si le nouveau démarrage échoue, il remet les anciens fichiers de l'application ; la sauvegarde des données reste disponible dans `D:\GALAXAM-Sauvegardes\GRH-native`. Il conserve également l'ancienne version des fichiers dans `D:\GALAXAM-GRH\updates`. Il n'utilise pas GitHub.

Avant une mise à jour, exécuter `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\windows-native\update-grh.ps1 -CheckOnly` pour voir les nouvelles migrations. Le script complet refuse toute modification d'un DDL canonique ou d'une migration déjà livrée. Il accepte les nouveaux fichiers `docker/migrations/0003_*.sql`, `0004_*.sql`, etc., dans l'ordre, et les applique avec `migrate-grh.ps1` **après la sauvegarde et l'arrêt de GRH, avant le redémarrage du nouveau code**. Le runner utilise la même table `public.galaxam_schema_history` que Docker, avec empreintes SHA-256, verrou et transaction. Une seconde exécution n'applique rien de plus. La base n'est jamais réinitialisée lors d'une mise à jour.

Une nouvelle migration doit d'abord être testée sur une copie de la base. Si elle échoue, sa transaction est annulée et les anciens fichiers restent en place. Si la migration réussit mais que le nouveau code ne démarre pas, GRH reste arrêté et `maintenance-required.txt` bloque son lancement : restaurer la base depuis l'archive affichée par le script avant de relancer l'ancien code. `restore-grh.ps1` efface ce marqueur après restauration des données. Ne pas effectuer les évolutions courantes par des `ALTER` manuels dans pgAdmin, car elles échapperaient à l'historique versionné.

Dans un **nouveau terminal CMD**, le développement natif utilise les commandes habituelles :

```cmd
cd D:\GALAXAM-SIRH
mvn -f backend\pom.xml spring-boot:run
```

Dans un second terminal :

```cmd
cd D:\GALAXAM-SIRH\frontend
npm run dev
```

Vite lit `SIRH_DEV_WEB_PORT` et relaie `/api` vers `127.0.0.1:8080`. Pour le profil Spring `prod` hors Docker, définir `SPRING_PROFILES_ACTIVE=prod` dans le processus ou le service qui démarre le JAR. Les URL JDBC natives sont séparées par défaut ; `SIRH_DEV_DB_URL` et `SIRH_PROD_DB_URL` permettent de les surcharger si nécessaire. Le parcours web de production native n'a pas encore son serveur de fichiers statiques ni sa recette complète.

Archives préparées sur ce poste : [Temurin 17.0.20.1+1](https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse), [Maven 3.9.16](https://maven.apache.org/download.cgi), [Node.js 22.23.0](https://nodejs.org/dist/v22.23.0/) et [PostgreSQL 15.19](https://www.enterprisedb.com/download-postgresql-binaries). Le SHA-256 de l'archive PostgreSQL téléchargée est `49e0690194d9fe2bb4d7d6587fdddaa85a3eaaafe2fd5480e0916fcb25d0143e` ; le fournisseur ne publie pas d'empreinte sur la page de téléchargement liée, donc conserver cette valeur comme référence du fichier reçu, pas comme validation indépendante du fournisseur.

## Étape disponible : construire le paquet

Sur le poste de construction, installer un JDK 17 Windows x64, Maven et Node/npm. Définir `JAVA_HOME` vers le JDK. Fournir une archive binaire PostgreSQL **15** Windows x64 et son empreinte SHA-256 vérifiée. Depuis la racine du dépôt :

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\windows-native\build.ps1 -PostgresArchive C:\chemin\postgresql-15.zip -PostgresSha256 EMPREINTE_SHA256
```

Le résultat est dans `scripts/windows-native/dist/` : `backend.jar`, `web/`, `runtime/` et `postgresql/`. Node et Maven servent uniquement à construire ce paquet ; ils ne seront pas nécessaires sur le PC utilisateur.

Le paquet `GRH-package` et son installateur concernent le PC Windows déjà configuré avec Java, Node et PostgreSQL. `build.ps1` est un chantier distinct de paquet autonome pour un PC réellement vierge ; ce parcours nécessite encore l'installation automatisée du service PostgreSQL, l'initialisation de la base et la gestion du premier compte administrateur.
