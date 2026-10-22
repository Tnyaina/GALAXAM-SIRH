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

État : **en cours**. La configuration Tauri par plateforme, les scripts de préparation Windows et le dossier de données `ProgramData` sont préparés. Le build et les essais Windows restent obligatoires avant le sprint 2. Le contrôle Windows exige un PC/une VM ou un runner Windows ; il n'est pas remplaçable par un build croisé non exécuté.

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

État : **non commencé**.

## Sprint 3 — Sauvegardes, restauration et livraison

**Livrables**

- Export manuel natif, sauvegarde automatique et rotation sous Windows.
- Restauration contrôlée avec copie de secours et retour arrière.
- Mise à jour et désinstallation conservant les données ; paquet signé si le certificat est disponible.

**Contrôles de sortie**

- Cycle réel sauvegarde → restauration → connexion, documents inclus, sur Windows 10/11.
- Essais hors ligne, chemins avec accents et espaces, disque plein, plusieurs sessions, mise à jour depuis une version précédente.
- Vérification du setup final, des journaux, des droits, des fichiers embarqués et des performances.

État : **non commencé**.
