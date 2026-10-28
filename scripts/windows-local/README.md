# Guide complet — application WEB locale, Windows, production et maintenance

Ce guide concerne GALAXAM SIRH dans un navigateur. Docker Desktop héberge PostgreSQL, Java et Nginx ; le client Tauri/desktop ne fait pas partie de cette installation.

## 1. Organisation retenue

| Usage | Configuration | Données |
|---|---|---|
| Production web | `docker-compose.web.yml`, profil Spring `prod` | `galaxam_prod_pg_data`, `galaxam_prod_business_files` |
| Développement | `docker-compose.dev.yml`, profil Spring `local` | volumes `galaxam_local_*` |

Les nouveaux scripts utilisent **uniquement** le Compose web autonome. Il n'y a pas de serveur Vite, de rechargement Maven ni de sources Java montées dans l'application de production. Les images backend et frontend sont compilées ; PostgreSQL reste dans son volume.

`scripts/windows-local/compose.ps1 -Mode prod` ou `-Mode dev` démarre le mode demandé avec les variables utilisateur Windows. Les scripts ci-dessous prennent aussi en charge l'installation initiale et les mises à jour. Celles-ci ne copient jamais la base de développement vers la production.

## 2. Informations à préparer

Avant de remettre le PC, renseigner cette fiche et la conserver avec les documents d'exploitation :

| Élément | Valeur proposée / décision |
|---|---|
| Compte Windows qui héberge Docker | compte choisi par l'entreprise, utilisé à chaque ouverture de session |
| Dossier stable du dépôt | `C:\GALAXAM\SIRH` |
| URL des utilisateurs sur ce PC | `http://localhost:8088` |
| Premier administrateur de l'application | `daf@galaxam.com` ; lors de l'installation, saisir le mot de passe retenu pour ce compte |
| Support de sauvegarde | chemin de `SIRH_BACKUP_DIRECTORY` ; idéalement support externe ou partage UNC accessible |
| Heure Windows des sauvegardes | 19 h ; régler le fuseau Windows sur Madagascar pour obtenir 19 h locale |
| Responsable maintenance | nom et contact à transmettre aux utilisateurs |
| Dépôt Git distant | URL réelle et accès en lecture du compte de maintenance |
| Tag de première livraison | par exemple `v1.0.0`, après validation et commit des fichiers |
| Date de recette | date, personne, résultat de la restauration et du redémarrage Windows |

Aucun mot de passe applicatif par défaut partagé n'est prévu. Le compte PostgreSQL technique `galaxam_admin` et la base `sirh_galaxam` ont des noms fixes ; le mot de passe est propre à chaque PC.

## 3. Préparer Windows une seule fois

1. Vérifier l'édition/version de Windows, la virtualisation et les prérequis Docker. La documentation Docker indique notamment WSL 2.1.5 minimum et 8 Go de RAM pour son parcours WSL 2 ; elle limite son support aux versions Windows encore maintenues. Vérifier donc le cas exact de Windows 10 avant de promettre sa compatibilité. Source : [Docker Desktop pour Windows](https://docs.docker.com/desktop/setup/install/windows-install/).
2. Prévoir davantage d'espace que la seule application : images Docker, compilation, base, documents, archive temporaire et sauvegardes. L'espace nécessaire dépend du volume des documents ; une restauration demande aussi de la place pour la copie de sécurité.
3. Installer/mettre à jour WSL si nécessaire depuis PowerShell administrateur : `wsl --install`, puis `wsl --update`, et redémarrer si demandé.
4. Installer Docker Desktop, choisir les conteneurs Linux et le moteur WSL 2. Lancer Docker dans le compte Windows retenu. Activer son démarrage à l'ouverture de session dans les paramètres Docker.
5. Installer Git for Windows pour les mises à jour par tags. Java, Maven, Node et PostgreSQL ne sont pas à installer séparément.
6. Mettre Windows à l'heure ; éviter la veille pendant le travail et les sauvegardes. Vérifier le fuseau horaire. Une session verrouillée peut rester ouverte ; une session fermée ne fait pas fonctionner ce parcours interactif.
7. Choisir le support externe. Créer le dossier, vérifier qu'on peut écrire et relire un fichier. Pour un réseau, utiliser un chemin UNC (`\\serveur\partage\GALAXAM`) plutôt qu'un lecteur mappé disponible seulement dans une autre session.
8. Vérifier la connectivité pour la première compilation : dépôt Git, Docker Hub, Maven Central, npm et dépôts système des images. Un proxy d'entreprise doit aussi être configuré dans Docker. Après installation, l'usage quotidien local n'exige pas Internet ; la compilation d'une nouvelle version peut l'exiger.

Vérification depuis une nouvelle fenêtre PowerShell :

```powershell
wsl --version
docker version
docker compose version
docker info --format '{{.OSType}}'
git --version
tar --version
```

Le type Docker doit être `linux`. Les commandes de ce guide supposent Docker Compose v2 récent et PowerShell 5.1 ou supérieur. Les scripts n'installent pas WSL/Docker automatiquement.

## 4. Récupérer la première livraison

Cloner le vrai dépôt dans `C:\GALAXAM\SIRH`, puis sélectionner le tag validé. Remplacer les deux valeurs d'exemple :

```powershell
git clone URL_DU_DEPOT C:\GALAXAM\SIRH
Set-Location C:\GALAXAM\SIRH
git switch --detach v1.0.0
```

Ne pas envoyer les variables utilisateur du développeur, ses documents téléversés ou sa base. Les variables de production doivent être configurées sur chaque PC installé. `.maintenance/` reste ignoré par Git.

Les scripts modifient des conteneurs et des données ; lancer uniquement une livraison de confiance. Ne pas déplacer le dépôt après installation : les tâches planifiées et montages Docker utilisent ses chemins.

## 5. Variables utilisateur Windows

L'installateur crée les variables `SIRH_PROD_*` à la première installation et les conserve ensuite. Elles sont propres au compte Windows utilisé pour Docker.

| Variable | Défaut / règle | Quand la changer |
|---|---|---|
| `SIRH_PROD_DB_PASSWORD` (variable Windows utilisateur) | obligatoire, généré aléatoirement ; validation au moins 16 caractères | installation initiale ; ensuite rotation SQL coordonnée, jamais simple édition |
| `SIRH_PROD_JWT_SECRET` (variable Windows utilisateur) | obligatoire, généré avec 64 octets aléatoires encodés en hexadécimal | conserver ; rotation volontaire invalide des jetons existants |
| `SIRH_PROD_WEB_PORT` | `8088` | seulement si occupé ; adapter CORS et raccourci |
| `SIRH_PROD_CORS_ORIGIN` | `http://localhost:8088,http://127.0.0.1:8088` | ajouter exactement l'origine HTTPS utilisée, si nécessaire |
| `SIRH_PROD_BOOTSTRAP_ADMIN_ENABLED` | `true` ; le seeder crée le compte si aucun administrateur actif n'existe | conserver actif ; n'altère pas les comptes existants |
| `SIRH_PROD_ADMIN_EMAIL` | `daf@galaxam.com` proposé par l'installateur ; modifiable à l'invite | adresse du premier administrateur |
| `SIRH_PROD_DEFAULT_ADMIN_PASSWORD` (variable Windows utilisateur) | saisi à l'installation | requis pour recréer le compte après une réinitialisation complète des volumes ; changer ensuite le mot de passe depuis l'application puis mettre à jour cette variable si l'on veut qu'une réinstallation recrée le même accès |
| Identité Compose et volumes | `galaxam-sirh-prod`, `galaxam_prod_pg_data`, `galaxam_prod_business_files` | conserver ces noms sur une installation existante |

L'installateur demande un mot de passe administrateur d'au moins 12 caractères, avec majuscule, minuscule et chiffre ; les signes `! _ + . - #` sont acceptés. Si tu veux utiliser les identifiants du Mac, indique `daf@galaxam.com` (valeur proposée par défaut) et saisis le même mot de passe à l'invite sécurisée. Il conserve les trois secrets dans les variables utilisateur Windows du compte qui exécute Docker. Ouvrir une nouvelle fenêtre PowerShell après leur création pour les commandes Compose manuelles. Vérifier avec `config --quiet`, sans afficher les secrets dans un journal.

Après installation, `.maintenance/installation.json` mémorise les noms du projet et des volumes ; les scripts refusent un changement accidentel de cette identité. Conserver ce fichier avec les variables utilisateur.

Les variables `SIRH_PROD_*` sont lues depuis le compte Windows par les scripts PowerShell. Les noms du projet et des volumes sont contrôlés par `.maintenance/installation.json`.

Les variables utilisateur Windows contenant les secrets sont lisibles par ce compte et conservées dans son profil ; elles ne constituent pas un coffre chiffré. Conserver une copie protégée dans le coffre de l'entreprise, séparée des archives métier. Ne pas exporter ces valeurs dans un journal ni les committer.

## 6. Installer

Depuis `C:\GALAXAM\SIRH`, avec le compte Windows retenu :

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\windows-local\install.ps1
```

Le script vérifie Docker, génère les secrets dans les variables utilisateur Windows, demande le premier administrateur, valide la configuration, compile les images, initialise une base vierge et applique les migrations. Il vérifie qu'un administrateur est actif, puis conserve le bootstrap idempotent actif. Ainsi, après une réinitialisation des volumes, le premier administrateur pourra être recréé. Sur une base déjà remplie, le seeder ne change aucun compte. Le script sauvegarde base et documents, puis redémarre le web. Il crée un raccourci et deux tâches Windows sous le compte courant : démarrage à l'ouverture de session et sauvegarde à 19 h.

Les six schémas actuels incluent déjà des référentiels GALAXAM et deux dossiers salariés historiques avec données associées. Il ne s’agit pas d’une base métier vide : faire valider ces données par le responsable avant usage réel. Ne pas les supprimer automatiquement et ne pas importer une autre base par-dessus sans procédure de reprise.

La première compilation peut être longue. Une interruption réseau ne justifie jamais de supprimer les volumes. Corriger la cause et consulter les journaux avant de reprendre.

Les tâches utilisent une session interactive. Elles ne promettent pas un serveur disponible avant toute connexion Windows. Trois nouvelles tentatives espacées de cinq minutes sont prévues en cas d’échec. Le rattrapage d'une heure manquée est activé avec `StartWhenAvailable`, mais Docker, la session et le support doivent être disponibles ; vérifier le résultat de la tâche. [Documentation Microsoft](https://learn.microsoft.com/en-us/windows/win32/taskschd/taskschedulerschema-startwhenavailable-settingstype-element).

### Réinitialisation complète volontaire

Cette opération supprime **la base de production et tous les documents téléversés**. Les variables utilisateur Windows restent configurées : le prochain démarrage recréera la base et le compte administrateur. Ne pas faire cela pour une mise à jour. Dans PowerShell, depuis le dépôt :

```powershell
docker compose --project-directory $PWD -f .\docker-compose.web.yml down -v
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\windows-local\compose.ps1 -Mode prod
```

Après le démarrage, le compte `daf@galaxam.com` sera recréé si c'est l'adresse configurée pendant l'installation. Le mot de passe est celui de `SIRH_PROD_DEFAULT_ADMIN_PASSWORD`. La sauvegarde préalable est indispensable si des données doivent être gardées.

Si l'installation échoue après création de `.maintenance/maintenance.json`, lire ce fichier et le journal. Ne pas effacer ce marqueur simplement pour contourner l'erreur : il protège une opération potentiellement inachevée.

## 7. HTTP, HTTPS, CORS et connexion

### Sur le PC lui-même

Ouvrir `http://localhost:8088`. Le port est lié à `127.0.0.1`, donc non publié sur le réseau. Le frontend appelle `/api/` sur la même origine ; Nginx transmet à Spring. Aucune adresse `localhost:8080` n'est à saisir dans le navigateur et aucun `VITE_API_BASE_URL` local n'est à recopier.

HTTP sur la boucle locale fonctionne avec le cookie de renouvellement `HttpOnly`, `SameSite=Lax`, sans attribut `Secure`. L'origine doit correspondre exactement à CORS, y compris schéma et port. `localhost` et `127.0.0.1` sont deux origines distinctes. Garder la même URL au quotidien.

Une réponse `UP` à `/api/auth/ping` vérifie également l'accès à la base. Une page visible seule ne prouve pas que la connexion et le renouvellement fonctionnent : les tester.

### Si l'application doit ensuite être accessible depuis d'autres PC

Cette installation prépare le cas local sur un seul PC. Pour un réseau, prévoir une adresse stable, un nom DNS et un proxy HTTPS avec certificat reconnu par tous les postes. Ne pas remplacer simplement `127.0.0.1` par `0.0.0.0` pour exposer les données RH en HTTP.

Le proxy HTTPS, exécuté sur l'hôte, doit transmettre vers `http://127.0.0.1:8088` et **écraser** `X-Forwarded-Proto` avec `https`, ainsi que `X-Forwarded-For` avec une adresse fiable. Ajouter l'origine exacte, par exemple `https://sirh.entreprise.example`, à `SIRH_PROD_CORS_ORIGIN`, en conservant les origines locales nécessaires au diagnostic. Aucune étoile, aucun `/api`, aucun chemin, aucune barre oblique finale dans CORS.

Nginx relaie le protocole à Spring ; Spring marque alors le cookie `Secure`. Un proxy qui oublie cet en-tête ou un certificat non reconnu peut bloquer la session. Ne jamais accepter un avertissement certificat comme solution permanente. Le certificat, le DNS, le pare-feu et le renouvellement du certificat doivent être vérifiés dans l'environnement réseau réel ; ce guide ne fournit pas un domaine ni une autorité de certification de l'entreprise.

Ne pas mélanger une page HTTPS et une API HTTP : garder `/api/` via le même proxy. L'en-tête HSTS est émis par Nginx seulement lorsque le protocole transmis est HTTPS.

## 8. Recette avant remise

- Ouvrir la page et se connecter avec le premier administrateur.
- Créer les comptes et droits nécessaires dans l'application. Vérifier aussi une connexion avec un compte métier non administrateur.
- Renseigner et valider les paramètres métier propres à l'entreprise (organisation, établissements, calendrier, règles et paramètres de paie, etc.) ; l'installation technique ne remplace pas ce travail.
- Enregistrer un dossier de test et un document, le retrouver après redémarrage des conteneurs.
- Exécuter une sauvegarde puis restaurer sur une **installation de test isolée**. Vérifier base, document et connexion.
- Redémarrer Windows, ouvrir la session prévue, attendre Docker, ouvrir le raccourci sans commande manuelle.
- Tester une heure de sauvegarde avec le PC dans son état quotidien. Vérifier `LastTaskResult = 0` et une nouvelle archive lisible sur le support externe.
- Conserver le tag Git livré, la date, les résultats et le contact de maintenance.

## 9. Sauvegarder

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\windows-local\backup.ps1
```

Le script prend un verrou pour empêcher deux maintenances simultanées. Il arrête le web, exporte PostgreSQL au format `pg_dump -Fc`, archive tous les documents (y compris fichiers cachés) et calcule leurs empreintes SHA-256. L'arrêt protège la cohérence entre les références de base et les fichiers. Prévenir les utilisateurs : l'application est indisponible pendant cette opération.

Chaque `galaxam-*.tar` contient `database.dump`, `uploads.tar`, `manifest.json`. L'archive n'est publiée sous son nom définitif qu'après création ; un `.partial` n'est pas une sauvegarde terminée. Le web redémarre après la sauvegarde ; si la reprise échoue, le marqueur de maintenance est conservé. Les 14 dernières archives automatiques/manuelles du dossier sont conservées ; les copies précédant une restauration restent dans un sous-dossier séparé.

Les archives ne sont pas chiffrées par le script. Utiliser un support protégé par l'entreprise (par exemple un disque chiffré) et limiter l'accès. Garder au moins une copie hors du PC. Surveiller l'espace libre et tester régulièrement une restauration. Une empreinte détecte une corruption accidentelle ; elle ne remplace pas le contrôle des accès.

Les journaux se trouvent dans `.maintenance/operation-*.log`. `last-backup.json` indique la dernière sauvegarde manuelle/planifiée réussie. Le Planificateur de tâches expose aussi les erreurs de prérequis. Il n'y a pas encore de notification dans l'interface métier : le responsable doit surveiller ces résultats.

## 10. Développer puis livrer une mise à jour

Sur le PC développeur :

1. Développer avec le profil local et sa base locale.
2. Pour un changement de base, ajouter une migration SQL versionnée dans `docker/migrations/`. Lire le guide de ce dossier. Ne pas éditer une migration déjà livrée ni réinitialiser les six schémas sur la production.
3. Tester les fonctions touchées et la compilation de production. Tester les migrations sur une **copie** restaurée des données de production, avec les droits nécessaires et dans un environnement protégé.
4. Vérifier la conservation de données représentatives et de documents, puis une deuxième exécution des migrations. Le workflow `Web production maintenance` réalise une recette jetable sur les PR concernées et les tags `v*` ; exiger un résultat vert avant la livraison. Il ne se connecte pas au PC client et ne déploie pas automatiquement.
5. Committer les modifications, créer un tag de livraison et pousser vers le dépôt distant. Par exemple, après les tests et le commit :

```bash
git tag -a v1.0.1 -m "Livraison web v1.0.1"
git push origin HEAD
git push origin v1.0.1
```

Ces commandes publient votre travail ; remplacer le numéro et la branche selon votre dépôt. Ne jamais déplacer un tag déjà livré.

Sur le PC de production, dans une plage de maintenance :

```powershell
Set-Location C:\GALAXAM\SIRH
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\windows-local\update.ps1 -Ref v1.0.1
```

Le script refuse un dépôt modifié ou une maintenance déjà ouverte. Il récupère les tags, identifie la révision cible, arrête le web, sauvegarde l'état actuel et écrit `last-update.json` avec les références et le chemin de l'archive. Il sélectionne le tag, compile les images, applique les migrations, recrée les services web et vérifie l'API. **Il ne supprime aucun volume et ne modifie pas les variables utilisateur.**

Le téléchargement/compilation a lieu pendant l'arrêt : prévoir une durée adaptée à la connexion du PC. Ce parcours nécessite Git et Internet sur le PC cible. Une livraison d'images précompilées hors ligne serait une extension distincte ; ne pas supposer que le script la gère déjà.

Les évolutions de schéma de cette filière doivent passer par une migration explicitement ajoutée. Le backend conserve également un ancien ajustement idempotent du stockage des sessions au démarrage (`IdleSessionService`) ; ce mécanisme historique ne remplace pas les migrations versionnées. Une erreur SQL annule le lot de migrations. Les migrations appliquées sont vérifiées par empreinte ; une incohérence bloque le déploiement au lieu de poursuivre.

Après succès, vérifier connexion, dossier, document, fonction modifiée et nouvelle sauvegarde. Conserver l'archive avant mise à jour pendant la période de validation.

## 11. Restaurer / revenir en arrière

La restauration remplace l'état actuel par celui d'une archive et perd donc les saisies postérieures. Elle est réservée à la maintenance. L'archive indique sa révision Git dans le manifeste : si elle est connue, le script exige cette même révision avant de restaurer. Sélectionner la révision et reconstruire les images correspondantes avant l’opération.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\windows-local\restore.ps1 -Archive 'E:\Sauvegardes-GALAXAM\galaxam-20261027-190000-000.tar'
```

Le script valide les empreintes et le catalogue PostgreSQL, demande de taper `RESTAURER`, arrête le web et sauvegarde l'état courant dans `avant-restauration`. Il recrée la base, restaure le dump dans une transaction, remplace les documents et reprend le web. Une erreur garde le web arrêté et le marqueur de maintenance présent. Aucune nouvelle migration n'est appliquée automatiquement pendant la restauration.

Si la base actuelle est si endommagée que sa copie de sécurité échoue, la procédure s'arrête volontairement avant remplacement. Faire alors diagnostiquer la base par le responsable technique ; ne pas contourner ce contrôle à l'aveugle.

Après échec de mise à jour : lire `.maintenance/last-update.json`, garder les logs et l'archive. Pour revenir au code précédent et à sa base correspondante, le technicien peut utiliser :

```powershell
$update = Get-Content .\.maintenance\last-update.json -Raw | ConvertFrom-Json
git switch --detach $update.previous
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\windows-local\compose.ps1 -Mode prod -Action build
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\windows-local\restore.ps1 -Archive $update.backup
```

Ne pas démarrer l'ancien backend sur une base migrée sans avoir vérifié sa compatibilité. Les images locales utilisent le tag `prod` : le retour arrière reconstruit donc le code précédent. Une restauration suppose les variables et secrets du serveur courant.

En cas d'échec de compilation avant toute migration, le technicien peut corriger la cause, relancer compilation et migrations, puis reprendre via les fonctions du fichier `common.ps1`. Il doit vérifier le journal avant de supprimer un marqueur. Ne pas effacer le marqueur et lancer `start.ps1` comme méthode de réparation générale.

## 12. Diagnostic et erreurs fréquentes

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\windows-local\diagnose.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\windows-local\compose.ps1 -Mode prod -Action logs
```

Ne pas publier des journaux contenant des données RH sans les relire. Ne pas partager la sortie complète de `docker compose config` : elle contient les secrets résolus ; utiliser `config --quiet` pour la validation.

| Symptôme | Vérifications / correction |
|---|---|
| Docker introuvable / moteur arrêté | nouvelle fenêtre PowerShell, Docker lancé, WSL fonctionnel, conteneurs Linux |
| Port 8088 occupé | arrêter l'autre application ou changer `SIRH_PROD_WEB_PORT`, `SIRH_PROD_CORS_ORIGIN` et raccourci ; ne pas tuer un processus inconnu |
| Application semble vide après changement de config | noms des volumes/projet ; ne pas recréer les données ni supprimer l'ancien volume |
| PostgreSQL refuse le mot de passe | utiliser le secret de création du volume ; `POSTGRES_PASSWORD` ne change pas le mot de passe d'une base existante |
| CORS 403 | origine exacte, schéma, port, absence de slash final et de joker ; appliquer le fichier avec recréation du backend |
| Connexion expire immédiatement | horloge, cookie, URL stable, origine du refresh, en-tête HTTPS et certificat si proxy |
| Nginx 502 | état backend et base ; recréer frontend après changement d'adresse du backend ; scripts de reprise le font |
| Migration en échec | journal `migrate`, migration modifiée/absente/SQL erroné ; produire une correction validée, jamais effacer l'historique pour forcer |
| Sauvegarde absente | session ouverte, Docker actif, heure/fuseau, support branché, droits d'écriture, espace, résultat tâche et logs |
| Maintenance inachevée | lire `maintenance.json`, `last-update.json`, logs ; reprendre ou restaurer selon l'opération |
| Images/migrations manquantes hors ligne | préparer la connectivité nécessaire ; le démarrage quotidien ne compile pas, la mise à jour oui |

Commandes interdites dans une maintenance ordinaire : `docker compose down -v`, suppression de volumes, réinitialisation SQL, remplacement de la base par celle du développeur. Les scripts fournis n'exécutent aucune de ces actions lors d'une mise à jour.

## 13. Portée de la validation

La recette automatisable est dans `scripts/maintenance-tests/web-production.py` : instance jetable imposée, connexion HTTP/refresh, CORS autorisé/refusé, indicateur de cookie HTTPS, migrations répétées, empreinte modifiée, erreur transactionnelle et restauration PostgreSQL. Les contrôles réalisés dans l'environnement de développement sont consignés dans `VALIDATION.md`.

La recette finale sur le vrai Windows reste nécessaire : installation WSL/Docker, Planificateur de tâches, ACL, disque externe, redémarrage et usage du navigateur. Un test local du protocole transmis n'est pas un test d'un certificat HTTPS réel. Aucun guide ne peut garantir l'absence de panne matérielle ou réseau ; il fournit ici les contrôles et procédures pour détecter et récupérer les incidents.
