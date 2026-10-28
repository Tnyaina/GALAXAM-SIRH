# GALAXAM web local sans Docker (Windows x64)

Ce parcours est en cours de construction. Il vise un PC Windows vierge avec Java 17 et PostgreSQL 15 embarqués, un frontend React compilé et une interface dans le navigateur local. Il est distinct de l'installation web Docker et du client Tauri.

## Prérequis pour un PC qui sert aussi au développement

- Windows x64 et PowerShell ; droits d'écriture dans le dossier de travail.
- JDK 17 avec `jlink` et Maven 3.9 pour compiler Spring Boot.
- Node.js 22 avec npm pour compiler et lancer React/Vite.
- PostgreSQL 15, avec les outils `initdb`, `pg_ctl`, `psql`, `pg_dump` et `pg_restore`.
- Espace disque pour les outils, les dépendances Maven/npm, la base, les documents et les sauvegardes.

Les archives téléchargées sont conservées dans `scripts/windows-native/downloads/` (ignoré par Git). Les copies décompressées provisoires ont été supprimées après l'installation globale.

Pour installer ces quatre outils **pour toute la machine**, lancer PowerShell en administrateur puis exécuter `scripts/windows-native/install-global.ps1`. Le script vérifie les archives et installe les versions ci-dessus dans `C:\Program Files\GALAXAM\Toolchain`, puis configure `JAVA_HOME` et le `PATH` système. Fermer et rouvrir la session Windows si un nouveau terminal ne voit pas encore les variables. PostgreSQL reste un outil disponible globalement ; son cluster de données et son service Windows seront configurés dans l'étape d'installation de GALAXAM.

Archives préparées sur ce poste : [Temurin 17.0.20.1+1](https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse), [Maven 3.9.16](https://maven.apache.org/download.cgi), [Node.js 22.23.0](https://nodejs.org/dist/v22.23.0/) et [PostgreSQL 15.19](https://www.enterprisedb.com/download-postgresql-binaries). Le SHA-256 de l'archive PostgreSQL téléchargée est `49e0690194d9fe2bb4d7d6587fdddaa85a3eaaafe2fd5480e0916fcb25d0143e` ; le fournisseur ne publie pas d'empreinte sur la page de téléchargement liée, donc conserver cette valeur comme référence du fichier reçu, pas comme validation indépendante du fournisseur.

## Étape disponible : construire le paquet

Sur le poste de construction, installer un JDK 17 Windows x64, Maven et Node/npm. Définir `JAVA_HOME` vers le JDK. Fournir une archive binaire PostgreSQL **15** Windows x64 et son empreinte SHA-256 vérifiée. Depuis la racine du dépôt :

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\windows-native\build.ps1 -PostgresArchive C:\chemin\postgresql-15.zip -PostgresSha256 EMPREINTE_SHA256
```

Le résultat est dans `scripts/windows-native/dist/` : `backend.jar`, `web/`, `runtime/` et `postgresql/`. Node et Maven servent uniquement à construire ce paquet ; ils ne seront pas nécessaires sur le PC utilisateur.

**Ce paquet n'est pas encore un installateur.** Il manque l'initialisation transactionnelle de la base et des migrations sur Windows, le serveur web local qui distribue `web/` et relaie `/api/`, le démarrage automatique de PostgreSQL et Spring, les sauvegardes/restaurations, les mises à jour et la recette sur le PC cible. Ne pas le déployer avec des données métier avant ces étapes.
