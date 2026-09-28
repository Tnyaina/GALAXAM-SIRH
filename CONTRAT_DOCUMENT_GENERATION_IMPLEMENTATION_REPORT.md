# Génération et archivage des contrats RH — rapport d’implémentation

Date : 27 septembre 2026. Périmètre : CDI, CDD, STAGE, PRESTATION et TEMPORAIRE.

## 1. État avant intervention

L’audit a précédé les modifications. Le dépôt était propre au démarrage.

- `WorkflowRhService.creerBrouillonEmbauche` crée un contrat BROUILLON, son affectation PLANIFIE liée par `id_contrat` et le workflow cible. Les créations de transitions réutilisent ces objets et leurs liens source/cible.
- Les statuts SQL sont BROUILLON, PLANIFIE, A_SIGNER, ACTIF, SUSPENDU, CLOTURE, HISTORISE, ANNULE et ROMPU. Aucun statut n’a été ajouté.
- La validation verrouille le workflow et exige un brouillon. Les transitions futures passent par A_SIGNER / PLANIFIE et le scheduler existant. L’embauche initiale était activée immédiatement même pour une date future.
- L’affectation historique peut évoluer plusieurs fois au sein d’un même contrat. La méthode existante `rechercherParContrat` renvoie la dernière affectation ; elle ne convient donc pas à la restitution du contrat initial après mutation.
- `DocumentOfficielRhController` exposait Attestation et Certificat, avec Apache POI XWPF dans `DocumentRhService`.
- `ContratTravailModal` affichait un contrat HTML, avec des clauses codées dans le composant, des valeurs présumées et des fallbacks vers les données courantes de l’employé. Aucun téléchargement DOCX contractuel ni dépôt directement lié au contrat.
- Les contrats BROUILLON étaient exclus de la liste des contrats affichés dans les tableaux de carrière.
- `document_rh` et `v_document_rh_cpl` possédaient déjà `id_contrat` et `id_workflow`. Ces colonnes n’étaient pas mappées ni transmises par Java ou le frontend.
- Le téléversement générique vérifiait le type, la taille et l’extension, stockait sous UUID puis enregistrait la métadonnée. Un échec SQL pouvait laisser le fichier sur disque.
- Les quatre canevas demandés étaient déjà présents dans les ressources backend.
- Aucun test dédié DocumentRh/contrat/DOCX n’a été trouvé. Les tests existants portent principalement sur Congés, Pointage et Paie.

## 2. Documentation retrouvée

- `backend/docs/boky/module-employe-carriere.md` et `module-structure-organisation.md` : documentation métier et cartographie.
- `backend/src/main/java/mg/galaxam/sirh/contrat/README.md` : architecture du module.
- `CONTRAT_DOCUMENT_GENERATION_AUDIT.md` : audit documentaire antérieur.
- `docs/CAHIER_DES_CHARGES_GALAXAM.md` et `docs/AUDIT_CONFORMITE_CDC_ET_CADRAGE_EXPORTS_GALAXAM.md` : cadrage métier général.
- `backend/conception/bdd/02_schema_referentiels_rh.sql`, `03_schema_employes_contrats.sql`, archives contrat/documents et registre des migrations : schéma et historique.
- `frontend/src/features/CONVENTIONS.md`, documentation frontend et composants existants : organisation du frontend.
- `README_variables_modeles_contrats.docx` et les six modèles Attestation/Certificat/contrats ont été repérés ; les variables des quatre contrats ont été extraites directement du XML DOCX.

Écarts avec l’ancien audit : les modèles sont présents ; un fallback vers l’affectation ACTIVE serait incorrect ; un modèle générique silencieux est exclu ; 40 heures, un représentant RH présumé ou une ville déduite du site ne sont pas des données contractuelles validées. L’audit antérieur constatait déjà l’activation immédiate de l’embauche, mais la cible demandée exige sa planification lorsque la date d’effet est future.

## 3. Architecture réellement utilisée

Backend Spring/JDBC : contrôleurs existants → `DocumentRhService` → entités JDBC et référentiels → ressources DOCX → Apache POI 5.2.5. Aucune dépendance documentaire ajoutée. Un DTO immutable fournit le contrat, l’employé, l’affectation initiale, les variables, les paragraphes d’aperçu et les données manquantes.

La préparation recherche exclusivement une affectation portant l’ID du contrat demandé, non annulée, triée par date de début puis ID croissants. Elle utilise la première affectation historique et ne cherche jamais l’affectation ACTIVE courante. Une absence ou un propriétaire incohérent provoque `CONTRACT_ASSIGNMENT_MISSING`.

L’aperçu est dérivé du canevas DOCX. Les données absentes obligatoires sont signalées dans `donneesManquantes`, `generationPossible=false` et les paragraphes. Le téléchargement retourne `CONTRACT_DOCUMENT_DATA_MISSING` avec la liste des variables. Une variable inconnue ou résiduelle déclenche `CONTRACT_TEMPLATE_INVALID`. Les tables, tables imbriquées, en-têtes, pieds de page et notes sont parcourus ; le DOCX sérialisé fait aussi l’objet d’un contrôle des parties XML Word avant son envoi.

Les ressources contractuelles sont choisies dans un mapping central par le code exact de `ref_type_contrat`. Les autres codes sont refusés par `CONTRACT_TEMPLATE_UNSUPPORTED`, même si un autre fichier existe dans le dossier.

## 4. Fichiers modifiés ou ajoutés

Backend, sous `backend/src/main/java/mg/galaxam/sirh/` :

- `common/exception/SirhErrorCode.java` : erreurs contractuelles documentaires.
- `contrat/api/DocumentOfficielRhController.java` : aperçu et téléchargement.
- `contrat/api/DocumentRhController.java` : liens multipart, validation, nettoyage du fichier sur rollback.
- `contrat/api/dto/ContratTravailDocumentDTO.java` : nouveau DTO documentaire.
- `contrat/application/DocumentRhService.java` : préparation, mapping, sélection du modèle, génération et contrôle des liens.
- `contrat/domain/ContratValidationEngine.java` : ajout de TEMPORAIRE à l’exigence de date de fin, déjà définie dans le référentiel et appliquée par le workflow.
- `contrat/application/WorkflowRhService.java` : planification de l’embauche future via la logique existante.
- `contrat/persistence/DocumentRh.java` : champs, insert, update, ResultSet et contrôle des liens.
- `contrat/persistence/DocumentRhCPL.java` : liens, référence et code type contrat, indicateur de contrat signé.
- `contrat/persistence/RefTypeDocument.java` : identification centralisée du type documentaire contractuel.

Autres fichiers backend :

- `backend/src/main/resources/templates/documents/contrats-configuration.example.properties` : documentation des paramètres externes, sans valeurs inventées.
- `backend/src/test/java/mg/galaxam/sirh/contrat/ContractDocumentTest.java`.
- `backend/src/test/java/mg/galaxam/sirh/contrat/ContractDocumentPermissionsTest.java`.

Frontend :

- `frontend/src/api/documentService.js`.
- `frontend/src/features/employees/api/documentRhService.js`.
- `frontend/src/features/employees/components/carriere/CarriereTimeline.jsx`.
- `frontend/src/features/employees/components/carriere/modals/CarriereModalCoordinator.jsx`.
- `frontend/src/features/employees/components/carriere/modals/workflow/ContratTravailModal.jsx`.
- `frontend/src/features/employees/components/carriere/timeline/HistoriqueContratsTable.jsx`.
- `frontend/src/features/employees/components/tabs/DocumentsRhTab.jsx`.
- `frontend/src/features/employees/components/tabs/UploadDocumentModal.jsx`.
- `frontend/src/features/employees/pages/EmployeFicheDetailPage.jsx`.
- `frontend/src/features/employees/__tests__/ContractDocuments.test.jsx`.
- `frontend/src/features/employees/__tests__/ContractDocumentApi.test.js`.

Le présent rapport est ajouté à la racine. Aucune table, migration SQL, dépendance, menu ou infrastructure PDF nouvelle. Les cinq modèles sont utilisés sans modification de leurs clauses. Le fichier `Contrat_TEMPORAIRE_modele.docx`, fourni dans les ressources au moment de l’extension, est intégré au mapping documentaire.

## 5. Workflow final

Création employé → contrat BROUILLON et affectation PLANIFIE → vérification/aperçu → DOCX si les données requises sont disponibles → validation du canevas par la DAF/RH → impression/signature → validation RH → dépôt du scan → archivage dans Documents RH.

La génération est disponible au BROUILLON et ne dépend pas de l’activation. Le scan peut être déposé avant ou après la validation. Aucune vérification de présence du contrat signé n’a été ajoutée à la validation RH.

L’embauche à effet futur réutilise la planification existante : contrat A_SIGNER, workflow PLANIFIE, affectation maintenue PLANIFIE. Le scheduler applique à échéance le mécanisme existant d’activation et d’historisation. Les dates immédiates/passées conservent l’activation immédiate. Le scheduler n’a pas été réécrit.

## 6. Mapping des variables

Chaque nom ci-dessous correspond à la variable `{{NOM}}` du canevas.

| Variables | Source / traitement |
| --- | --- |
| ENTREPRISE_NOM | `ref_filiale.raison_sociale` de la filiale du contrat |
| ENTREPRISE_NIF, ENTREPRISE_STAT | Référentiel de cette même filiale ; aucune société de secours |
| ENTREPRISE_ADRESSE | `ref_filiale.adresse_siege` de la filiale du contrat ; adresse GLX confirmée, VA à renseigner |
| ENTREPRISE_RCS | `ref_filiale.rcs` ; ligne affichée seulement si un numéro est renseigné |
| SIGNATAIRE_NOM, SIGNATAIRE_FONCTION, SIGNATAIRE_VILLE | Configuration externe propre à la filiale |
| SIGNATAIRE_DATE | Date de signature du contrat ; si absente, mention explicite « À renseigner lors de la signature » |
| CONTRAT_NUMERO, CONTRAT_TYPE, CONTRAT_STATUT | Numéro, code réel du type, code du statut |
| CONTRAT_DATE_DEBUT, CONTRAT_DATE_FIN | Dates du contrat, format français ; CDI sans fin : « Durée indéterminée » |
| CONTRAT_DATE_SIGNATURE, CONTRAT_DATE_FIN_ESSAI | Dates persistées, exposées dans le mapping ; utilisées seulement si présentes dans le modèle |
| CONTRAT_DUREE_ESSAI | Durée persistée en mois ; null sans date de fin d’essai ou zéro : « Aucune période d’essai prévue », conformément au formulaire d’embauche |
| CONTRAT_DESCRIPTION | Description/missions du contrat, obligatoire dans le modèle PRESTATION |
| EMPLOYE_NOM_COMPLET, EMPLOYE_MATRICULE, EMPLOYE_CIVILITE | Identité employé ; civilité exposée dans le mapping |
| EMPLOYE_DATE_NAISSANCE, EMPLOYE_LIEU_NAISSANCE | État civil persisté |
| EMPLOYE_CIN, EMPLOYE_CIN_DELIVRANCE | CIN, date et lieu de délivrance persistés |
| EMPLOYE_ADRESSE | Adresse employé persistée |
| POSTE_FONCTION, POSTE_SITE, POSTE_DEPARTEMENT, POSTE_SERVICE | Référentiels de l’affectation initiale liée au contrat |
| POSTE_CATEGORIE | Catégorie de cette affectation |
| POSTE_SUPERIEUR | Mention supprimée des documents rendus ; lien hiérarchique conservé en BDD |
| REMUNERATION_BASE, REMUNERATION_MODE | Salaire de base et mode de calcul de cette affectation ; montant en Ar |
| TEMPS_TRAVAIL | Configuration spécifique au contrat (y compris TEMPORAIRE) ; aucune présomption d’horaire |
| TEMPORAIRE_MOTIF | `contrat_travail.description` ; absence visible et ajoutée aux avertissements, caractère obligatoire à confirmer DAF/RH |
| TEMPORAIRE_UNITE_REMUNERATION | Code de `ref_mode_calcul_paie` de l’affectation initiale : HORAIRE → par heure, JOURNALIER → par jour, MENSUEL → par mois |
| STAGE_ETABLISSEMENT, STAGE_REFERENT_ETABLISSEMENT, STAGE_GRATIFICATION | Configuration spécifique au contrat de stage |
| PRESTATION_MONTANT, PRESTATION_MODALITES_PAIEMENT | Configuration spécifique au contrat de prestation ; aucune assimilation au salaire de base |

Les variables présentes dans le modèle sélectionné sont obligatoires, sauf service, référent établissement et motif temporaire (les mentions RCS et supérieur sont retirées des paragraphes avant préparation/génération) : leur absence est remplacée explicitement par « Non renseigné — à vérifier par la DAF/RH » et ajoutée aux avertissements. Les autres valeurs absentes bloquent le DOCX. La date de signature peut rester à compléter à la signature : elle ne reprend ni la date du jour ni la date de début.

Les textes entre crochets (clauses et emplacement logo) sont conservés tels quels. Ils ne sont pas des variables techniques et nécessitent une validation/complétion humaine.

## 7. Templates utilisés

Ressources déjà présentes sous `backend/src/main/resources/templates/documents/` :

| Code réel | Ressource |
| --- | --- |
| CDI | `Contrat_CDI_modele.docx` |
| CDD | `Contrat_CDD_modele.docx` |
| STAGE | `Contrat_STAGE_modele.docx` |
| PRESTATION | `Contrat_PRESTATION_modele.docx` |
| TEMPORAIRE | `Contrat_TEMPORAIRE_modele.docx` |

Attestation et Certificat utilisent toujours leurs propres ressources et leur moteur POI existant. Aucun texte juridique ajouté aux canevas contractuels.

## 8. Endpoints ajoutés ou modifiés

- `GET /documents/contrat/{idContrat}` et alias `/api/documents/contrat/{idContrat}` : DTO d’aperçu et disponibilité de génération.
- `GET /documents/contrat/{idContrat}/docx` et alias `/api/documents/contrat/{idContrat}/docx` : DOCX, nom de fichier nettoyé, MIME Word, `Cache-Control: no-store`.
- Génération/aperçu : `EMPLOYE_READ` et `PAIE_READ`, ou `ROLE_ADMIN`, car le document contient la rémunération. La méthode est protégée par la sécurité Spring existante.
- `POST /employes/{idEmploye}/documents` : multipart inchangé, enrichi de `idContrat` et `idWorkflow` facultatifs ; `EMPLOYE_WRITE` ou administrateur.
- `GET /employes/{idEmploye}/documents` : projection enrichie avec liens, numéro, code type contrat et `contratSigne`.
- Routes de lecture/téléchargement/suppression documentaire existantes conservées avec leurs permissions.

Le type documentaire ne possède pas de code métier immuable dans le schéma. L’identification « Contrat de travail » est centralisée en Java et exposée par `contratTravail` au frontend ; aucun ID numérique 6 n’est codé dans les écrans. Un changement de ce libellé nécessite de maintenir cette identification ou de faire évoluer ultérieurement le référentiel avec un code stable.

## 9. Comportement frontend

- Les brouillons sont désormais visibles dans le tableau des contrats et proposent « Générer / signer ».
- `ContratTravailModal` reste le point d’entrée. Il affiche type, référence, statut, employé, poste/site, rémunération et dates préparés par le backend.
- « Modifier » réouvre l’éditeur du workflow brouillon lorsque celui-ci correspond au contrat sélectionné et que l’utilisateur a le droit d’écriture.
- « Aperçu » affiche les paragraphes du canevas ; il ne construit plus de clauses contractuelles dans le composant et ne remplace plus une affectation historique par le poste actuel.
- « Télécharger DOCX » respecte les droits et `generationPossible`. Les erreurs JSON reçues en blob sont décodées avant affichage.
- « Imprimer / PDF » conserve `window.print()` pour l’aperçu. L’aperçu HTML restitue le contenu textuel du canevas, pas la mise en page Word exacte ; le DOCX reste le support prioritaire de signature.
- « Déposer le contrat signé » est disponible indépendamment d’un échec de génération.
- Le formulaire Documents RH générique permet également de choisir le contrat lorsqu’un type « Contrat de travail » est sélectionné.

## 10. Gestion du contrat signé

Le scan est un document RH classique. Le dépôt depuis la fiche contrat préremplit l’employé, le contrat et le type documentaire, verrouille le type et n’exige aucune ressaisie des liens.

Le backend vérifie l’appartenance du contrat à l’employé. S’il est fourni, le workflow doit appartenir à cet employé et concerner le contrat comme source ou cible. Un dépôt « Contrat de travail » sans contrat est refusé. Les dépôts génériques restent compatibles avec l’absence de liens.

Insert, update et lecture conservent les deux clés étrangères déjà disponibles en BDD. La liste enrichie joint le numéro et le code du type sans modifier la vue SQL. Un rollback supprime le fichier nouvellement stocké.

Après dépôt, la fiche indique « Contrat signé ✓ » avec consultation et téléchargement authentifiés. Documents RH affiche référence, type et marque de signature. La fiche employé recharge les Documents RH après un dépôt depuis la carrière. Plusieurs fichiers/versionnements restent possibles ; aucun nouvel onglet n’est créé. « Signé » signifie que la pièce a été classée comme contrat signé par l’utilisateur : aucune vérification électronique de signature n’est prétendue.

## 11. Tests exécutés

Nouveaux tests backend : préparation des cinq types, sélection des cinq ressources, génération/relecture DOCX, contrat inexistant, type non supporté, affectation introuvable, données requises manquantes, inconnues/résidus, variables réparties sur des runs, remplacement dans une table, liens JDBC insert/update/ResultSet, upload et récupération de la projection liée, nettoyage sur échec, sérialisation des indicateurs, permissions Spring réellement interceptées, embauche immédiate/future sans scan, génération Attestation et Certificat. Pour TEMPORAIRE : génération horaire/journalière/mensuelle sans conversion, motif présent/absent, date de fin obligatoire, mode inconnu bloquant et upload signé par le mécanisme commun.

Les nouveaux tests contractuels utilisent JDBC simulé ; ils exécutent les méthodes réelles de préparation, persistance et contrôleur avec un stockage temporaire pour l’upload. Les tests d’intégration PostgreSQL déjà présents sont inclus dans la suite complète. Aucun parcours utilisateur complet contre une instance navigateur/backend déployée n’a été exécuté.

Nouveaux tests frontend : rendu React des actions au brouillon et à l’état actif, téléchargement du contrat choisi, upload prérempli/verrouillé, état signé et filtrage par contrat, consultation/téléchargement du scan, erreur backend, blocage DOCX sans blocage de l’archivage, affichage dans Documents RH, paramètres multipart facultatifs, compatibilité dépôt générique et décodage d’erreur blob. TEMPORAIRE horaire est testé dans la même modale : aperçu, DOCX, dépôt et état signé.

## 12. Résultats des tests et builds

| Commande | Résultat |
| --- | --- |
| `mvn -f backend/pom.xml test` | 129 tests, 0 échec, 0 erreur, 0 ignoré — BUILD SUCCESS |
| `mvn -f backend/pom.xml package` | 129 tests réussis ; JAR assemblé et reconditionné — BUILD SUCCESS |
| `npm --prefix frontend test` | 3 fichiers, 16 tests réussis |
| `npm --prefix frontend run build` | Build Vite réussi |
| `git diff --check` | Aucun défaut d’espacement |

Le remplacement contractuel est isolé de la logique Attestation/Certificat ; leurs tests de génération et les suites préexistantes passent. Les vérifications DOCX sont structurelles/textuelles, sans contrôle de rendu visuel Word/LibreOffice. Aucun convertisseur PDF n’a été installé.

## 13. Dettes restantes

- Renseigner les configurations documentaires approuvées avant la première génération réelle : l’aperçu indique les champs absents et le DOCX les refuse.
- La configuration externe constitue la solution minimale V1 ; il n’existe pas d’écran dédié aux données contractuelles Stage/Prestation et aux représentants de filiales. Une extension métier persistée de `ref_filiale` et des détails contractuels serait préférable si leur gestion devient fréquente.
- L’identité employé et les libellés référentiels ne sont pas des snapshots historiques immuables. Le salaire et les rattachements proviennent de l’affectation initiale, mais les changements ultérieurs d’identité/libellé/configuration peuvent changer une régénération. Le scan signé archivé conserve la version effectivement signée.
- La restitution est celle du contrat initial, pas d’un avenant ultérieur à la même relation contractuelle. L’éditeur d’avenants reste hors périmètre.
- Une validation visuelle des cinq canevas dans Word et un parcours complet en recette restent à réaliser.
- Les anciennes constantes propres aux Attestation/Certificat restent une dette documentée ci-dessous ; elles ne sont jamais utilisées pour les contrats.
- La qualification du document signé est documentaire ; ni reconnaissance de signature ni signature électronique.

## 14. Données métier encore manquantes

Après l'extension de la fiche filiale, `ref_filiale` persiste aussi l'adresse officielle du siège, le RCS, le téléphone et l'email (trois derniers champs facultatifs). La ville de signature et le signataire restent des données de l'acte, distinctes de l'identité permanente de la société. L'initialisation actuelle ne crée que GLX ; la migration SP34 ne modifie pas les autres filiales.

Aujourd’hui, `DocumentRhService.chargerEntrepriseInfo` utilise encore les constantes historiques de téléphone/email (`+261 32 12 131 01`, `daf@galaxam.com`) pour Attestation/Certificat, et `resoudreSignataire` les libellés « Direction des Ressources Humaines » / « Directeur des Ressources Humaines ». La ville de ces anciens documents peut être dérivée du libellé de site. Leur validation métier n’est pas démontrée par le dépôt. Ces valeurs n’ont pas été déplacées vers les nouveaux contrats ; la génération contractuelle ne les appelle pas. Les modifier relève d’un correctif métier séparé, pour préserver le périmètre et la non-régression demandés.

Les canevas contractuels n’utilisent pas téléphone/email. Adresse/représentant/fonction/ville sont obligatoires pour les canevas ; le RCS est facultatif et sa ligne disparaît quand sa valeur est absente. Le supérieur hiérarchique ne figure pas dans les contrats.

Les données spécifiques établissement, référent, gratification, honoraires/modalités et temps de travail contractuel ne sont pas persistées dans les objets audités. Elles utilisent une configuration spécifique au contrat, sans transformer le salaire en gratification/honoraires.

Paramétrage : copier les clés documentées dans `backend/src/main/resources/templates/documents/contrats-configuration.example.properties` vers une configuration Spring externe. Préfixes :

- `app.documents.contrats.filiales.<idFiliale>.<VARIABLE>` pour les coordonnées et le représentant.
- `app.documents.contrats.contrats.<idContrat>.<VARIABLE>` pour les détails contractuels non persistés.

Charger la configuration avec `--spring.config.additional-location=file:/chemin/configuration.properties`, en remplaçant le chemin par le fichier réel approuvé, puis redémarrer le backend. L’exemple ne charge aucune valeur automatiquement. Aucune valeur de production n’a été inventée ni injectée pendant cette intervention.

### Extension TEMPORAIRE : audit des règles réelles

- Code du contrat : `ref_type_contrat.code = TEMPORAIRE` (seed ID 4, `exige_date_fin=TRUE`). Code de l’intervenant : `ref_type_intervenant.code = TEMPORAIRE` (seed ID 3). Aucun usage TEMPORARY n’a été trouvé dans le périmètre métier audité.
- L’API/formulaire d’embauche utilise `exigeDateFin`, et `WorkflowRhService.validerExigenceDateFin` lit le référentiel. `ContratValidationEngine` omettait ce code dans sa liste ; il est désormais aligné sur la règle déjà existante, sans introduire une nouvelle limite de durée ou de renouvellement.
- Les modes de paie existants sont MENSUEL (Mensuel fixe), HORAIRE (Taux horaire), JOURNALIER (Forfait journalier). Le formulaire d’affectation expose ce référentiel sans restriction supplémentaire propre au temporaire.
- `EmbaucheInitialeRequest.salaireBase` est documenté comme montant mensuel, horaire ou journalier selon le mode. Il est enregistré sans conversion dans `employe_affectation_carriere.salaire_base`, NUMERIC(12,2), positif, accompagné de `id_mode_calcul_paie`.
- La génération documentaire conserve strictement ce montant. Pour TEMPORAIRE, `REMUNERATION_BASE` inclut l’unité déterminée par le code, également exposée dans `TEMPORAIRE_UNITE_REMUNERATION`. Un libellé changé ne modifie pas l’unité ; un code inconnu la laisse manquante et bloque le DOCX, sans défaut mensuel silencieux.
- Aucun mécanisme de présentation commun des unités n’a été retrouvé ; le mapping des trois unités est centralisé une seule fois dans le service documentaire. Il ne réimplémente aucun calcul de paie.
- Attention à une incohérence préexistante : `PayrollService` transmet le mode et le montant, mais `PayrollExecutionEngine.calculerSalaireBase` applique le montant source et un prorata mensuel /30 sans branche horaire/journalière. Les référentiels et le DTO autorisent ces modes ; leur liquidation en paie demande un audit distinct. Aucun calcul de paie n’a été changé pour cette extension documentaire.
- Le champ `contrat_travail.description`, alimenté dès l’embauche et lors des transitions, est la source retenue pour `TEMPORAIRE_MOTIF`. Il peut décrire le besoin et les missions ; la DAF/RH doit confirmer sa bonne saisie et le caractère obligatoire du motif. V1 : absence explicite et avertissement, sans contrainte métier nouvelle. Les motifs de sortie ou d’un workflow de mutation ne sont pas utilisés comme substitut au besoin temporaire initial.
- La matrice carrière existante prévoit, pour TEMPORAIRE, transformation CDI, fin de contrat et rupture conventionnelle ; ces règles sont conservées. Les flags fiscaux/sociaux du régime restent gérés par les référentiels et la paie existante.
- Le workflow, le DTO documentaire, les endpoints, les actions frontend et le scan archivé sont exactement ceux des autres types. Aucune table, colonne ni interface spéciale TEMPORAIRE ajoutée.

## 15. Décisions nécessitant validation DAF/RH

- Validation des clauses conservées entre crochets, des modèles de chacun des cinq types et du logo ; les fichiers restent des canevas internes, pas des contrats juridiquement complets validés.
- Fourniture des coordonnées officielles, NIF/STAT, du représentant habilité, de sa fonction et de la ville de signature pour chaque filiale.
- Confirmation de l’applicabilité du RCS et des champs facultatifs explicitement signalés.
- Validation des temps de travail, gratifications, informations d’établissement et conditions de prestation propres à chaque dossier.
- Confirmation du caractère obligatoire du motif temporaire et de la sémantique des montants horaires/journaliers effectivement saisis ; revue séparée de leur traitement dans la paie.
- Décision ultérieure sur la persistance/administration de ces données plutôt qu’une configuration externe.
- Validation du rendu Word et du parcours en recette avant utilisation officielle.

Les statuts ci-dessous distinguent le moteur testé du fonctionnement opérationnel sans données métier supplémentaires : les cinq générations sont partielles tant que la configuration requise n’est pas fournie. L’archivage fonctionne indépendamment de ce paramétrage. Les modèles/configurations juridiques ne peuvent pas être approuvés par cette intervention technique.

### Ajustements suite au retour utilisateur sur Carrière

- L’onglet Carrière conserve un résumé visible et un accès direct « Contrat / documents ». Les actions secondaires sont regroupées dans « Actions RH », et les détails contrat/affectation ainsi que l’historique sont repliés par défaut, accessibles au clavier par des éléments `details/summary` natifs. Les workflows et leurs permissions restent identiques.
- Les données manquantes de la fiche documentaire portent des libellés lisibles, sans noms de variables techniques.
- RCS et supérieur hiérarchique sont retirés du contenu rendu des cinq canevas, sans supprimer la relation hiérarchique du SIRH ni toucher aux clauses. Le retrait conserve les autres lignes du même paragraphe (nom, NIF, STAT et adresse).
- Correction du prérequis essai : le formulaire envoie `dureeEssaiMois=null` et `dateFinEssai=null` lorsque la case essai n’est pas cochée. Ce cas n’est plus considéré comme une donnée manquante ; le canevas indique l’absence d’essai. Une fin d’essai renseignée sans durée demeure une information à compléter.
- Les coordonnées employeur ne sont pas saisies dans le workflow. La filiale persiste nom/NIF/STAT, sans adresse officielle ni identité du représentant. Le compte utilisateur persiste un identifiant de connexion, sans nom/prénom de signataire. Le rôle administrateur ne permet donc pas de remplir automatiquement une identité juridique.
- Évolution recommandée : intégrer adresse, représentant et ville de signature à la fiche filiale, les renseigner une seule fois et les charger automatiquement pour chaque contrat. Cette évolution de persistance et de formulaire n’a pas été réalisée dans cet ajustement ; les paramètres externes V1 restent nécessaires pour ces informations.
- Validation de cet ajustement : 129 tests backend et 16 tests frontend réussis, builds backend/frontend et `git diff --check` réussis. Un test vérifie l’état replié des sections de Carrière et l’accès direct aux documents ; un autre vérifie l’absence d’essai et le retrait RCS/supérieur tout en préservant les informations entreprise.

## Signataire DAF — complément

La génération recherche désormais la DAF par `ref_fonction.code_fonction = DAF`, avec affectation et contrat ACTIF de la filiale concernée. Une seule personne distincte est requise ; aucune sélection arbitraire en cas de doublon. Son nom et sa fonction alimentent le signataire. La configuration explicite reste disponible si aucune personne unique ne peut être résolue. La ville de signature ne vient pas de son adresse personnelle : le référentiel site ne possède pas de ville de signature. Le temps de travail reste une donnée distincte de la durée du contrat ; le CDI sans fin demeure « Durée indéterminée ». L’adresse fournie par l’utilisateur (3e étage, Tour Forello, Tanjombato) reste à enregistrer dans la configuration de la filiale concernée ; aucune modification des données en base n’a été exécutée.

Deux tests supplémentaires couvrent la DAF unique et les candidates multiples. Validation : suite backend complète et package Maven ; diff --check. Aucun changement frontend dans ce complément.

## Présentation professionnelle des cinq modèles

Les modèles CDI, CDD, STAGE, PRESTATION et TEMPORAIRE ont été harmonisés : format A4, marges de 2 cm, police Calibri, titre Word `Title`, sections `Heading 1`, logo GALAXAM existant intégré, identité entreprise en lignes distinctes, deux espaces de signature nominatifs et pied de page avec numéro de contrat et pagination Word. Le RCS figure désormais en ligne facultative dans les cinq canevas ; le supérieur hiérarchique reste retiré. Aucun autre champ documentaire requis n’a été ajouté. Toutes les zones de clauses entre crochets ont été conservées, sans rédaction juridique nouvelle. Le CDI conserve sa durée indéterminée.

## Vérification de la chaîne documentaire GLX — complément

Les cinq modèles contiennent à nouveau `{{ENTREPRISE_RCS}}`. La préparation lit la valeur de `ref_filiale.rcs`. Si elle est absente, l'aperçu et le DOCX suppriment la ligne entière sans texte de remplacement. Les tests couvrent les deux cas, ainsi que le dépôt d'un contrat signé avec `idContrat`, la restitution du lien document/contrat et l'indépendance de la validation RH vis-à-vis de ce dépôt. La migration SP34 a été appliquée à la base locale : les deux CDI GLX récupèrent l'adresse confirmée et un RCS vide, sans toucher à la fiche VA existante. La préparation du CDI réel indique encore nom/fonction du signataire, ville de signature et durée/répartition du travail à compléter avant signature. Ces mentions sont explicites dans le canevas et ne bloquent plus le DOCX de préparation, conformément à la décision utilisateur. Un DOCX du CDI réel a été généré et rendu en deux pages, sans RCS affiché ni variable résiduelle. La base contient un document RH, aucun document lié à un contrat ; le dépôt est donc vérifié par tests automatisés, pas par un dépôt signé réel. La décision sur le temps de travail négociable et son effet pointage/paie est reportée.

Validation de ce complément : 165 tests backend réussis, 12 tests frontend ciblés réussis, builds backend/frontend réussis et `git diff --check` sans erreur. Les cinq canevas ont été rendus et inspectés visuellement après l'ajout du RCS facultatif.

Vérification structurelle : comparaison des ensembles de variables et des clauses avant/après. Les tests de génération vérifient aussi la présence du logo et du style du titre. Vérification visuelle : rendu des cinq modèles et de cinq fichiers réellement générés par le service Java avec les fixtures de test ; les 20 pages ont été inspectées. Les fixtures ne constituent pas des données métier réelles. LibreOffice du runtime a servi uniquement au contrôle local des DOCX ; aucune conversion serveur ou infrastructure PDF n’a été ajoutée au SIRH.

Validation de ce complément : suite backend complète (131 tests, zéro échec/erreur), package Maven réussi, tests de génération ciblés réussis et `git diff --check`. Aucun changement frontend pour cette présentation documentaire.

## Logo fourni par l’utilisateur

Les cinq modèles utilisent désormais `frontend/src/assets/logo/GLX_LOGO.jpg`, intégré à une largeur de 2,1 cm avec proportions conservées. Le logo et son texte GALAXAM LOGISTICS sont conservés sans retouche ni réinterprétation. Les dix pages des cinq modèles ont été rendues et inspectées après remplacement. Tests ContractDocumentTest réussis (30 tests), dont génération des cinq types et non-régression Attestation/Certificat ; diff --check réussi.

## Ergonomie de l’onglet Carrière

La carte de situation présente le type et le numéro du contrat, le statut, le poste/site, les dates et la rémunération (toujours protégée par PAIE_READ). Les détails du contrat et de l’affectation sont ouverts par défaut, conformément à la dernière demande utilisateur. L’action principale est « Faire évoluer la situation ». Le menu Documents en superposition donne accès à « Contrat et signature » (fenêtre existante : aperçu, DOCX, dépôt et consultation du signé) et à l’attestation. Un bouton distinct « Autres actions » contient « Mettre fin au contrat », en rouge. Les menus se ferment au clic extérieur, à la sortie de focus et avec Échap (retour du focus au déclencheur), et leur position est ajustée à la largeur disponible.

Les brouillons et événements planifiés sont regroupés sous « Changements en cours », après la situation actuelle ; les restrictions existantes empêchant une nouvelle transition concurrente sont conservées. Un contrat de brouillon identifié dans la liste ouvre directement « Préparer le contrat ». L’historique reste replié. Aucun workflow ni statut métier n’a été changé.

Validation frontend : 19 tests réussis, build réussi et diff --check propre. Tests complémentaires sur l’accès Documents, le repli des détails, la transition/rupture, Échap, clic extérieur, lecture seule et accès au contrat brouillon. Aucun test visuel en navigateur n’a été exécuté dans ce complément.

## ÉTAT FINAL

| Élément | État |
| --- | --- |
| génération CDI | Partiellement fonctionnel |
| génération CDD | Partiellement fonctionnel |
| génération Stage | Partiellement fonctionnel |
| génération Prestation | Partiellement fonctionnel |
| génération Temporaire | Partiellement fonctionnel |
| upload contrat signé | Fonctionnel |
| liaison document/contrat | Fonctionnel |
| affichage frontend | Fonctionnel |
| Attestation | Fonctionnel |
| Certificat | Fonctionnel |
