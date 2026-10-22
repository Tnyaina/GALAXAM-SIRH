# Version Windows 10/11 — liste de travail

État : planification. Le paquet actuel est un `.app`/DMG macOS ; la présence de `nsis` dans `tauri.conf.json` ne signifie pas qu'un installateur Windows fonctionne.

## Décisions proposées pour la première version

- **Cible :** Windows 10/11 x64, avec construction et validation sur Windows. Étudier ARM64 ensuite.
- **Installation :** `setup.exe` NSIS installé pour toute la machine, avec élévation administrateur. Afficher le dossier du programme et permettre de le modifier ; `C:\Program Files\GALAXAM SIRH` est la valeur proposée. Refuser un dossier réseau, amovible ou sans droits adaptés : le service doit retrouver ses exécutables après redémarrage.
- **Données locales :** `C:\ProgramData\GALAXAM SIRH\` par défaut, distinct du dossier du programme. Les changements de dossier de données doivent être une opération de migration guidée, pas une simple modification de chemin. Le choix initial d'un autre disque local fixe peut venir après validation du service et de ses droits.
- **Sauvegardes automatiques :** `C:\ProgramData\GALAXAM SIRH\backups\` par défaut, avec rotation. L'export manuel utilise « Enregistrer sous… » vers le dossier choisi par l'utilisateur, y compris un autre disque.
- **Service :** un service Windows supervisé par le Service Control Manager (SCM) pour PostgreSQL et Spring, actif après fermeture de la fenêtre et de la session. Démarrage automatique au démarrage du PC, arrêt propre à l'extinction. Le service et la base utilisent des droits limités, à définir ensemble.
- **Dépendances :** Java 17, PostgreSQL et leurs composants nécessaires sont embarqués dans l'installateur. Pas de téléchargement Maven ou PostgreSQL au premier lancement. WebView2 est vérifié par l'installateur ; choisir son mode hors ligne si l'installation sans Internet est une exigence.

Ces choix forment une proposition de produit. Le mode d'installation, le compte du service et le dossier de données doivent être validés ensemble avant de figer l'installateur.

## P0 — Architecture et paquet minimal

- [ ] Ajouter les implémentations Windows de `platform` : redémarrage, dossier de données, dialogue « Enregistrer sous… », ouverture du dossier et superviseur. Retirer les implémentations `unsupported` pour la cible Windows.
- [ ] Extraire les opérations encore propres à Unix/macOS des modules communs : binaires `.exe`, PATH, archives `tar`, permissions/ACL, détection du propriétaire, chemins et nom des ressources.
- [ ] Préparer un runtime Java 17 Windows avec `jlink`, le JAR Spring et les licences associées.
- [ ] Préparer PostgreSQL Windows x64 et vérifier ses DLL, `initdb`, `postgres`, `psql`, `pg_dump`, `pg_restore` et l'extension `btree_gist`. Épingler versions, provenance et empreintes des téléchargements utilisés pendant le build.
- [ ] Séparer les scripts de build macOS et Windows ; générer le frontend une seule fois par build. Construire le `setup.exe` sur une machine ou un runner Windows.
- [ ] Configurer NSIS : nom affiché, icône, langue française, raccourcis, désinstallation, version, détection de version précédente et mode d'installation pour toute la machine.
- [ ] Vérifier sur Windows la page NSIS intégrée « Choisir le dossier d'installation », préremplie avec `Program Files`. Refuser un emplacement réseau, amovible ou modifiable par un utilisateur ordinaire avant d'y installer les exécutables du futur service ; contrôler l'espace disponible.
- [ ] Définir la politique WebView2 : téléchargement si disponible, ou installateur hors ligne intégré pour un paquet entièrement autonome.
- [ ] Générer un installateur signable ; prévoir signature du programme et du setup avant diffusion publique.

## P0 — Service local et premier lancement

- [ ] Définir le compte d'exécution du service, ses ACL sur `ProgramData` et l'accès de l'interface utilisateur à l'API locale. Aucun secret lisible par tous les comptes Windows.
- [ ] Installer/désinstaller le service via SCM, avec démarrage automatique, reprise après panne, arrêt gracieux et journalisation. Afficher dans l'interface l'état réel du service.
- [ ] Garder Spring et PostgreSQL actifs après fermeture de la fenêtre et déconnexion Windows ; les arrêter correctement à l'extinction du PC ou via « Arrêter les services ».
- [ ] Initialiser PostgreSQL en tâche de fond avec étapes visibles, sans geler la fenêtre. Gérer annulation, reprise après interruption, manque d'espace, antivirus et conflits de ports.
- [ ] Restreindre PostgreSQL et l'API à `127.0.0.1` ; vérifier les règles du pare-feu et les collisions avec une autre installation ou un PostgreSQL existant.
- [ ] Vérifier création du premier administrateur, connexion locale, connexion distante et changement de mode ; les données locales ne doivent ni être écrasées ni migrées silencieusement.
- [ ] Définir le comportement de plusieurs comptes Windows sur un même PC : instance de service et base partagées ou installations séparées. Le choix proposé ci-dessus implique une seule base locale par machine.

## P0 — Données, sauvegardes et mises à jour

- [ ] Garder programme, base, documents, journaux et sauvegardes dans des dossiers distincts ; publier leurs emplacements dans l'interface.
- [ ] Reproduire l'export PostgreSQL et des documents sur Windows, avec fichier temporaire, vérification, publication sûre et sélection native de la destination.
- [ ] Planifier les sauvegardes automatiques dans le service ou le Planificateur de tâches ; empêcher les doublons et reprendre après un PC éteint. Conserver la rotation et afficher la dernière réussite ou erreur.
- [ ] Implémenter **la restauration** d'une archive, avec validation du format, arrêt du service, contrôle de version, copie de secours, reprise et retour arrière en cas d'échec.
- [ ] Mettre à jour le programme et les ressources du service sans supprimer la base ; prévoir une migration explicite et un retour arrière si Spring ne redémarre pas.
- [ ] À la désinstallation, proposer clairement de conserver ou de supprimer les données ; conserver par défaut. Ne jamais effacer les exports manuels placés ailleurs.

## P1 — Qualité et diffusion

- [ ] Vérifier Windows 10 et 11 sur des installations vierges, avec et sans Internet, sur un compte standard et un compte administrateur.
- [ ] Vérifier dossiers avec accents, espaces et chemins longs ; disque plein, redémarrage pendant `initdb`, veille, fermeture de session, second lancement et mise à jour depuis une ancienne version.
- [ ] Mesurer durée du premier lancement, mémoire et réactivité de la fenêtre sur un PC modeste ; corriger les blocages observés.
- [ ] Tester un cycle réel sauvegarde → restauration → connexion et intégrité des documents ; tester le résultat après mise à jour et désinstallation.
- [ ] Vérifier le paquet final : fichiers embarqués, signature, installation, réparation, désinstallation, journaux de diagnostic et absence de téléchargement inattendu.
- [ ] Documenter pour l'utilisateur : choix du dossier du programme, emplacement des données, service en arrière-plan, export, restauration et procédure de dépannage.

## Références de conception

- [Installateurs Windows Tauri](https://v2.tauri.app/distribute/windows-installer/) et [configuration NSIS](https://v2.tauri.app/reference/config/).
- [Dossiers connus Windows](https://learn.microsoft.com/en-us/windows/win32/shell/knownfolderid) et [choix des mécanismes d'arrière-plan](https://learn.microsoft.com/en-us/windows/win32/services/about-services).
- [Binaires PostgreSQL pour Windows](https://www.postgresql.org/download/windows/).
