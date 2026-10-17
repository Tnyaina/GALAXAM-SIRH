# Phase 3.4 — Parcours RH des dossiers de sortie

## Écrans et états

La page `DossiersSortiePage` fournit recherche, filtres et consultation. Une courte fenêtre choisit le salarié, la date et le motif ; elle mène à la page dédiée `/paie/dossiers-sortie/nouveau`, puis à `/paie/dossiers-sortie/{id}` après sauvegarde. `DossierSortieModal` fournit la fiche métier dans `DossierSortieWorkspaceShell` : préparation, checklist, prêts, simulation, scellement et formalités. `DossierSortieSuiviPanel` présente séparément le contrat, le STC financier et le dossier administratif. Le décompte détaillé d'un STC scellé s'ouvre dans `StcRecuModal` ; cette action ne relance pas la préparation.

La recherche du salarié à l'ouverture utilise `GET /api/paie/dossier-sortie/employes?q=...`. Elle renvoie au plus 20 identités minimales (identifiant, matricule, nom et prénom) aux profils dotés de `PAIE_READ` ; la fiche complète `/api/employes` reste soumise à `EMPLOYE_READ`. Une recherche refusée ou indisponible s'affiche comme erreur, sans être confondue avec une liste vide.

La fiche affiche la prochaine étape selon l'état réel du dossier. Elle explicite les points bloquants de la checklist et indique, lorsqu'une simulation échoue faute d'`AFFECTATION_CONTRAT`, les données contractuelles à vérifier. Un lecteur voit un parcours de consultation ; la liste emploie « Consulter » à la place de « Traiter » pour ce profil.

La création explique que l'ouverture du dossier ne rompt pas le contrat. La fiche distingue visuellement le gel du pointage, l'état du bulletin mensuel et la simulation STC. Si aucun bulletin n'existe, la simulation peut utiliser le pointage et le prorata ; elle reste provisoire avant scellement. Si un bulletin mensuel existe sans validation, le service refuse son rapprochement : il faut le valider, actualiser le dossier et relancer la simulation. Le scellement doit toujours être précédé d'une vérification du décompte et de la checklist. L'écran ne présente plus un STC scellé comme « régularisé » tant que les règlements ne le prouvent pas.

L'aperçu lancé depuis la fiche salarié (`StcPreparationModal`, `simulationOnly`) est un calcul immédiat par `POST /api/paie/stc/calculer`. Il utilise les données RH disponibles et les hypothèses saisies, sans persister de dossier, bulletin, paiement ou référence de scellement. Le formulaire met les ajustements exceptionnels dans une section facultative et n'affiche plus de mode de règlement dans cet aperçu. La simulation du dossier (`POST /api/paie/dossier-sortie/{id}/simuler`) sauvegarde au contraire une référence et un snapshot contrôlés à l'étape du scellement. Ces deux résultats ne doivent pas être confondus ; l'aperçu isolé peut diverger si pointage, bulletin ou décisions RH changent ensuite.

Les actions « Établir le STC » de la fiche Paie et de l'historique Carrière ouvrent désormais la page du dossier de sortie. Seule l'action de simulation prévisionnelle conserve l'ancienne fenêtre de calcul. L'ancien endpoint `POST /api/paie/stc/valider` reste présent pour compatibilité technique, mais le parcours RH guidé passe par la référence de simulation et le scellement du dossier.

Le calcul de l'aperçu reprend la période de paie couvrant la date de départ. Si elle est absente, `StcService.preparerStc` propose un prorata depuis le premier jour du mois civil, au maximum 30 jours ; l'écran signale désormais explicitement ce repli et demande de vérifier le cycle 26–25. Un montant d'aperçu obtenu sans pointage ni bulletin validé n'est donc pas une validation des données finales.

La fiche présente six sections courtes et une action contextuelle « prochaine étape ». Les changements de date, motif ou décision de prêt doivent être sauvegardés avant de simuler. Une modification enregistrée après simulation impose un nouveau calcul. Après rechargement, le DTO du dossier fournit la référence et le résumé de la simulation, mais pas toutes ses lignes détaillées : l'interface demande donc une nouvelle simulation avant de permettre le scellement. Le décompte potentiellement périmé n'est plus affiché comme résultat courant. La fermeture demande confirmation si des changements de préparation restent non enregistrés.

La liste concentre les informations en six colonnes et évite les cartes de compteurs vides lors de la première utilisation. Le suivi sépare les états du contrat, du STC et du dossier, puis présente les montants issus du backend, le journal des règlements, les documents et la clôture dans cet ordre. Les formulaires de paiement et de dérogation affichent leurs libellés à côté des champs ; la date de sortie ISO est ramenée à une date compatible avec le champ de paiement.

Les accès de la checklist vers Carrière, Congés et Pointage utilisent les routes React réelles et ne s'affichent que si le profil peut ouvrir ces modules. L'accès aux documents renvoie à l'onglet de formalités du dossier. Les autres codes de module sans destination précise restent accompagnés de leur consigne textuelle, sans lien trompeur.

## Recette guidée avec une base isolée

1. Créer un salarié de test avec contrat et affectation couvrant la date de sortie ; préparer une période de paie 26–25 avec pointage réel. Le scénario de rupture de contrat doit être effectué par le workflow Carrière, pas par l'ouverture du dossier STC.
2. Geler le pointage. Ouvrir un dossier de sortie et vérifier trois états distincts : pointage gelé, bulletin non calculé, STC à simuler. Simuler et relever le prorata et les rubriques ; vérifier que le dossier indique explicitement le caractère provisoire du résultat.
3. Dans la campagne de paie isolée, calculer puis valider le bulletin mensuel. Revenir au dossier, l'actualiser et relancer la simulation. Comparer le décompte avec le bulletin validé, puis résoudre les éventuels points bloquants de la checklist avant de tenter le scellement.
4. Sceller une seule fois, vérifier le montant figé, enregistrer un paiement partiel puis le solde, contrôler le journal et les documents. Refaire le parcours avec une clôture dérogatoire et un règlement après clôture.
5. Rejouer avec un profil `PAIE_READ` et un profil `PAIE_WRITE` ; contrôler les boutons et les réponses du serveur. Simuler une erreur réseau après un paiement, recharger la page et répéter avec la même clé d'idempotence pour confirmer qu'aucun deuxième règlement n'est créé.

Cette recette exige une base PostgreSQL isolée et des données de test cohérentes. Ne pas la jouer sur une base RH partagée ; ne pas fabriquer un bulletin validé ou une rupture par modification SQL directe. Le comportement sans bulletin doit être accepté explicitement par RH/Paie avant de sceller un STC réel.

Un statut administratif `CLOTURE` peut coexister avec un reste à payer lorsque `derogationReglement` est vrai. L'interface affiche alors la décision et conserve le formulaire de versement. Les montants de net, cumul et reste sont ceux des réponses du backend. Les dates ou échéances détaillées d'un plan de paiement ne sont pas structurées dans le DTO actuel ; l'écran n'en invente aucune.

## API et droits

Le client Axios a pour base `/api/`. Le service appelle le mapping canonique `paie/dossier-sortie`, soit `/api/paie/dossier-sortie` au niveau HTTP. Le backend exige `PAIE_READ` pour la consultation et les documents, `PAIE_WRITE` pour les mutations. Les boutons de mutation sont masqués aux utilisateurs sans `PAIE_WRITE` et les statuts métier limitent les actions. Le serveur revalide chaque opération.

La route React `/paie/dossiers-sortie` et l'entrée de menu `PAIE_DOSSIERS_SORTIE` acceptent les profils dotés de `PAIE_READ`. La migration SP82 crée cette entrée dans le menu Paie et l'attribue, avec son parent, aux rôles disposant déjà de la lecture Paie. Elle doit être appliquée sur les bases existantes ; modifier le schéma canonique ne met pas leur menu à jour.

- `GET /liste`, `GET /{id}` : lecture du dossier.
- `POST /`, `POST /{id}/simuler`, `POST /{id}/sceller` : préparation et scellement.
- `POST /{id}/certificat`, `PUT /{id}/offboarding` : formalités réellement effectuées.
- `GET /{id}/reglements`, `POST /{id}/reglement`, `POST /{id}/reglement/{idReglement}/annuler` : journal et paiement. Une clé d'idempotence est conservée lors d'une nouvelle tentative identique.
- `POST /{id}/cloturer` : clôture normale ou avec décision dérogatoire documentée.
- `GET /{id}/documents/decompte/pdf`, `/recu-stc/pdf`, `/certificat/pdf`, `/certificat/docx`, `/bordereau-remise/pdf`, `/quittance/{idReglement}/pdf` : téléchargements sans mutation.

Le reçu STC téléchargé est un **projet non signé**. Son téléchargement n'enregistre ni paiement, ni remise, ni signature.

## Validation et limites

Avant les derniers ajustements d'interface et de menu, la suite backend complète sur PostgreSQL isolé comptait 538 tests : 533 réussis, 5 ignorés, aucun échec. La suite frontend complète comptait 220 tests réussis après correction de six échecs initiaux liés aux tests d'accessibilité, aux couleurs de thème et aux composants partagés. Les derniers ajustements d'interface et la migration SP82 n'ont pas été retestés, conformément à la demande de poursuivre sans tests.

Une recette navigateur a confirmé la sélection du salarié, la création et la sauvegarde d'un dossier, la lecture seule pour `PAIE_READ` et l'accès en écriture pour `PAIE_WRITE`. Le jeu de données isolé ne possédait pas l'affectation contractuelle nécessaire à la simulation du STC : le parcours complet de scellement et de règlement n'a pas été confirmé dans cette recette.

L'API de remise du certificat et l'interface exigent désormais un contrat `ROMPU`, `CLOTURE` ou `TERMINE` avec une date de fin échue. La validité juridique d'un reçu STC final et signé reste à confirmer par RH/juridique ; aucun reçu final n'est produit par cette interface.
