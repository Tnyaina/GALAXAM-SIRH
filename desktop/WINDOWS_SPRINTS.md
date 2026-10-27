# Portage Windows — trois sprints avec validation

Règle de passage : le sprint suivant ne commence qu'après réussite de tous les contrôles de sortie du sprint courant sur un environnement Windows x64. Une compilation macOS seule ne valide pas Windows.

## Sprint 1 — Chaîne de build et installateur de base

**Livrables**

- Configuration Tauri commune et fichiers de configuration propres à macOS et Windows.
- Préparation contrôlée du JAR, du runtime Java 17 et des binaires PostgreSQL Windows x64 avant le build. Aucune dépendance téléchargée au premier lancement.
- Installateur NSIS par machine, en français, avec WebView2 utilisable hors ligne.
- Étape de choix du dossier du programme ; dossier des données séparé dans `ProgramData`.

**Contrôles de sortie**

- Build macOS inchangé et DMG produit après la séparation des configurations.
- Build NSIS sur Windows x64 à partir d'un environnement propre ; inspection des fichiers Java/PostgreSQL dans le setup.
- Installation et désinstallation du programme sur Windows 10 et 11, avec dossier par défaut puis dossier personnalisé. Les données restent intactes à la désinstallation.
- Vérification du lancement de l'interface et du mode distant. Le mode local n'est pas déclaré prêt à ce stade.

État : **validé sur runner Windows Server 2022** (CI `39902854098`, commit `24a4b78a`). Le setup NSIS a été construit, installé dans son dossier par défaut puis dans `C:\GalaxamCI`, ses ressources Java et PostgreSQL ont été contrôlées, l'interface HTTP locale a répondu, les deux désinstallations ont réussi et le dossier des données a été conservé. Le build DMG macOS et les neuf tests Rust ont également réussi. L'essai visuel sur Windows 10/11 reste à faire avant diffusion publique ; le runner ne reproduit pas ces environnements à l'identique.

## Sprint 2 — Base locale et service Windows

**Livrables**

- Service Windows pour PostgreSQL et Spring, avec droits et ACL adaptés à `ProgramData`.
- Initialisation locale asynchrone avec progression, reprise et erreurs compréhensibles.
- Démarrage après boot, fonctionnement sans fenêtre ni session, arrêt propre et mise à jour sûre.

**Contrôles de sortie**

- Premier lancement réel sur Windows 10/11 ; base, schémas, administrateur et API fonctionnels.
- Fermeture de la fenêtre, déconnexion, redémarrage et extinction : état du service et données conformes au choix utilisateur.
- Conflit de ports, manque d'espace, interruption d'`initdb`, installation sur autre disque local et compte non administrateur vérifiés.
- Mesure de la réactivité de l'interface et de la mémoire pendant le premier lancement.

État : **validé par CI sur Windows Server 2022** (run `39942489397`, commit `d704d0ef`). Le setup enregistre le service de production sous `LocalService`. Celui-ci initialise PostgreSQL, démarre Spring, attend le contrôle de santé de l'API, puis arrête proprement les deux processus. Le contrôle élevé initial passe par UAC ; les secrets restent dans `ProgramData` avec des ACL dédiées. Le contrôle utilisateur du service utilise un résultat temporaire limité au profil local. Le gestionnaire de services Windows est configuré pour redémarrer le service après un arrêt inattendu.

Le workflow Windows construit le setup, lance les tests backend/Rust, installe le service, attend l'API et PostgreSQL, vérifie le contrôle élevé simulé et les ACL, arrête/redémarre le service, provoque un arrêt inattendu pour contrôler la récupération du SCM, puis vérifie les deux chemins d'installation et la conservation des données à la désinstallation. Ce parcours a réussi lors du run `39983334818`.

Le runner Windows Server 2022 permet de simuler l'installation avec élévation et d'exécuter ces contrôles de service, mais il ne valide pas les transitions de session, le démarrage après redémarrage/extinction, ni l'interface UAC interactive sur Windows 10/11. Ces essais restent requis avant diffusion publique. Les scénarios de conflits de ports, manque d'espace, interruption d'`initdb`, disque secondaire et mesure mémoire/réactivité ne sont pas encore couverts par le workflow.

Architecture retenue pour la suite : une base par machine sous `ProgramData`, exécutée par `LocalService`. La première configuration et la création du premier compte SIRH se feront avec élévation UAC ; ensuite, l'application fonctionnera en compte standard. Seuls le service et l'étape administrative élevée pourront lire les secrets PostgreSQL et JWT. Les comptes standard liront l'état du service et utiliseront l'API locale, sans accès direct aux secrets. Les ressources exécutables du service devront rester dans un emplacement protégé, même si l'utilisateur choisit un autre dossier pour l'interface. Le mode local Windows reste fermé jusqu'à validation de ces droits et du parcours du premier administrateur.

## Sprint 3 — Sauvegardes, restauration et livraison

**Livrables**

- Export manuel natif, sauvegarde automatique et rotation sous Windows.
- Restauration contrôlée avec copie de secours et retour arrière.
- Mise à jour et désinstallation conservant les données ; paquet signé si le certificat est disponible.

**Contrôles de sortie**

- Cycle réel sauvegarde → restauration → connexion, documents inclus, sur Windows 10/11.
- Essais hors ligne, chemins avec accents et espaces, disque plein, plusieurs sessions, mise à jour depuis une version précédente.
- Vérification du setup final, des journaux, des droits, des fichiers embarqués et des performances.

État : **cycle automatisé validé sur Windows Server 2022** lors du run `39983334818`. L'export manuel Windows utilise la boîte de dialogue native et un helper élevé ; le service Windows crée une sauvegarde locale quotidienne et conserve les sept dernières. L'interface propose la sélection d'une archive, une confirmation destructive explicite, la validation de l'archive, une sauvegarde de sécurité, la restauration de la base et des documents ainsi que le redémarrage du service. Le runner contrôle le retour d'un marqueur SQL et d'un document accentué. Le changement distant → local et l'arrêt pendant une sauvegarde font l'objet d'un correctif supplémentaire en attente de validation CI. Les essais interactifs sur Windows 10/11 restent nécessaires avant diffusion.
