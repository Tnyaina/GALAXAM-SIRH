# Migration post-audit du SIRH GALAXAM — 14 septembre 2026

Ce journal suit les corrections réellement stabilisées. Il ne transforme pas les anciens documents en source de vérité : le code courant, les tests et le schéma actif restent prioritaires.

## Phase 0 — Sécurité P0 — TERMINÉE

### Avant

- clé JWT, clé ImgBB et identifiants DAF dotés de valeurs sensibles par défaut ;
- bootstrap administrateur actif implicitement ;
- photos RH publiques sous `/uploads/photos/**` avec cache public ;
- documents RH contournables par un handler de fichiers statiques ;
- deux politiques CORS divergentes et wildcard possible en production ;
- durée JWT signée différente de `expiresIn` annoncé au client.

### Modifications

- secrets JWT et origines CORS rendus obligatoires hors profils local/test ;
- suppression de la clé ImgBB et du stockage RH tiers ;
- bootstrap administrateur désactivé par défaut, activable explicitement et refusé sans identifiants externes ;
- suppression du handler MVC statique et de la seconde politique CORS ;
- validation au démarrage interdisant toute origine vide ou `*` avec credentials ;
- stockage local des photos et lecture par endpoint authentifié `GET /api/employes/photos/{filename}` ;
- accès photo soumis à `EMPLOYE_READ`, écriture soumise à `EMPLOYE_WRITE`, chemins normalisés, types et taille limités, cache privé ;
- téléchargement documentaire conservé derrière son endpoint autorisé et passé en cache privé sans stockage ;
- composant frontend unique `AuthenticatedImage` pour charger toute photo via le client JWT ;
- expiration communiquée par l'API dérivée de la même propriété que la signature JWT.

### Fichiers principaux

- `backend/src/main/resources/application*.properties`
- `backend/src/main/java/mg/galaxam/sirh/config/SecurityConfig.java`
- `backend/src/main/java/mg/galaxam/sirh/config/WebConfig.java` (supprimé)
- `backend/src/main/java/mg/galaxam/sirh/securite/AdminAccountSeeder.java`
- `backend/src/main/java/mg/galaxam/sirh/securite/JwtService.java`
- `backend/src/main/java/mg/galaxam/sirh/api/controller/securite/AuthController.java`
- `backend/src/main/java/mg/galaxam/sirh/api/controller/employe/EmployePhotoController.java`
- `backend/src/main/java/mg/galaxam/sirh/api/controller/employe/DocumentRhController.java`
- `frontend/src/components/ui/data-display/AuthenticatedImage.jsx`
- `frontend/src/components/ui/data-display/Avatar.jsx`

### Tests ajoutés

`Phase0SecurityIntegrationTest` couvre : bootstrap absent, refus photo anonyme, refus cross-role Pointage, upload/lecture autorisés, cache privé, chemin documentaire statique inaccessible, CORS autorisée/refusée et cohérence JWT.

### Tests exécutés et résultat

- tests ciblés sécurité : 8/8 ;
- backend complet, run isolé final : 149/149 ;
- frontend lint : OK ;
- frontend build : OK ;
- `git diff --check` : OK ;
- démarrage `prod` sans `JWT_SECRET` : refusé pendant la création du contexte ;
- recherche des anciennes valeurs sensibles dans les sources actives : 0 résultat.

Un run backend antérieur a aussi été vert. Un chevauchement accidentel entre deux processus Maven a produit un échec de fixture intermédiaire ; le run isolé suivant confirme que le code et la suite sont déterministes lorsqu'une seule suite manipule la base de test partagée.

### Dette restante

- rotation des secrets déjà exposés à réaliser dans l'environnement d'exploitation ;
- les anciennes documentations de sécurité décrivent encore les routes publiques supprimées : mise à jour différée à la phase 11 ;
- l'autorisation objet plus fine que `EMPLOYE_READ` dépend d'une politique de visibilité salarié non encore formalisée. Aucune supposition n'est codée.

### Impact estimé

- Sécurité secrets/CORS/JWT : de critique à environ 8/10 ;
- PII fichiers RH : de critique à environ 8/10 ;
- score global : gain estimé de 6 à 9 points, à confirmer au contre-audit.

### Classification documentaire

| Document | Statut | Raison | Action |
|---|---|---|---|
| `docs/AUDIT_GLOBAL_CONTRADICTOIRE_2026-09-14.md` | KEEP | photographie correcte de l'état avant correction | conserver comme baseline datée |
| `docs/cartographie_architecturale_sirh.md` | UPDATE | décrit encore les photos comme publiques | mettre à jour en phase 11 |
| `backend/docs/securite.md` | UPDATE | contient l'ancienne clé par défaut et l'ancien matcher public | mettre à jour en phase 11 |
| `backend/docs/optimisation.md` | OBSOLETE | recommande le cache public retiré | corriger ou archiver en phase 11 |

## Phase 1 — Workflow métier backend — TERMINÉE

### Avant

- `PUT /periodes/{id}/statut` acceptait un état cible choisi par le client ;
- les transitions Pointage DAF, clôture et réouverture Paie étaient réparties entre plusieurs contrôleurs et services ;
- aucun numéro de version ne protégeait une période contre un double clic ou deux validations concurrentes ;
- la réouverture pouvait remettre en circulation des données de pointage déjà consommées ;
- le frontend disposait de plusieurs méthodes spécialisées qui contournaient un owner métier unique.

### Modifications

- création d'un unique `PayrollPeriodWorkflowService.execute(...)`, seul propriétaire des règles et effets de transition ;
- commandes explicites `OPEN`, `SUBMIT`, `APPROVE`, `REJECT`, `CLOSE` et `REOPEN` ;
- matrice état courant + commande vers état cible, permissions contrôlées côté backend et refus explicite des transitions impossibles ;
- endpoint canonique `POST /api/periodes/{id}/actions/{command}` ; l'ancienne mutation générique répond désormais `410 Gone` sans modifier la période ;
- ajout d'une version optimiste et mise à jour conditionnelle sur l'identifiant, la version et l'état initial ;
- journal append-only `periode_paie_transition` contenant commande, états, versions, auteur, motif et date ;
- gel des snapshots lors de l'approbation, clôture des données de paie uniquement après validation des bulletins et réouverture sans altérer les snapshots `CONSOMME` ;
- suppression des anciens endpoints et méthodes parallèles Pointage/Paie ;
- migration des appels React vers une seule méthode `executeCommand` ;
- retrait de la clôture du cockpit Pointage : cette action reste dans le cockpit Paie où ses prérequis peuvent être expliqués.

### Fichiers principaux

- `backend/conception/bdd/05_schema_pointage.sql`
- `backend/conception/bdd/2026-09-14_workflow_periode_paie.sql`
- `backend/src/main/java/mg/galaxam/sirh/model/paie/workflow/PayrollPeriodWorkflowService.java`
- `backend/src/main/java/mg/galaxam/sirh/model/paie/workflow/PayrollPeriodCommand.java`
- `backend/src/main/java/mg/galaxam/sirh/model/paie/workflow/PayrollPeriodDecision.java`
- `backend/src/main/java/mg/galaxam/sirh/api/controller/pointage/PeriodePaieController.java`
- `backend/src/main/java/mg/galaxam/sirh/model/paie/service/PayrollService.java`
- `frontend/src/api/pointage/periodePaieService.js`
- `frontend/src/hooks/pointage/usePeriodePaie.js`
- `frontend/src/components/paie/CockpitPaieTab.jsx`
- `frontend/src/pages/pointage/ArbitrageDafPage.jsx`

### Tests ajoutés

`PayrollPeriodWorkflowIntegrationTest` couvre : disparition effective de la mutation générique, transition et historique, double commande concurrente, refus de retour `CLOTUREE -> BROUILLON` et conservation d'un snapshot déjà consommé lors d'une réouverture.

Les tests existants Paie, Pointage, sécurité et E2E ont été migrés vers les commandes canoniques, avec version attendue explicite.

### Tests exécutés et résultat

- nouveau workflow : 5/5 ;
- sélection d'intégration migrée : 27/27 ;
- backend complet, premier run isolé : 154/154 ;
- backend complet, second run isolé : 154/154 ;
- frontend lint : OK ;
- frontend build : OK ;
- `git diff --check` : OK ;
- recherche des anciennes routes/méthodes de transition actives : 0 résultat ;
- recherche des écritures d'état de période : une seule, dans le workflow canonique.

### Dette restante

- `A_VALIDER` et l'ancien alias `TRANSMIS_DAF` coexistent encore dans le référentiel ; le workflow les traite de façon identique en attendant une migration de données justifiée ;
- les listes de périodes exposent la version, mais les capacités calculées seront généralisées avec le contrat commun de la phase 2 et le contexte backend de la phase 6 ;
- les documents décrivant les anciennes routes restent volontairement inchangés jusqu'à la phase 11.

### Impact estimé

- Workflow backend : environ 8/10 ;
- concurrence et auditabilité des périodes : environ 8/10 ;
- backend-driven sur le cycle Paie/Pointage : environ 7/10 à ce stade, avant généralisation des capacités ;
- score global : gain estimé de 5 à 7 points, à confirmer au contre-audit.

### Classification documentaire

| Document | Statut | Raison | Action |
|---|---|---|---|
| `docs/AUDIT_GLOBAL_CONTRADICTOIRE_2026-09-14.md` | KEEP | constate correctement la mutation arbitraire avant correction | conserver comme baseline datée |
| documentation API Pointage/Paie existante | UPDATE | référence les anciens endpoints spécialisés | mettre à jour en phase 11 |

## Phase 2 — BusinessDecision et erreurs API — TERMINÉE

### Avant

- DTO de décision propre au seul workflow Paie ;
- exceptions métier spécialisées mais inutilisées et sans `details` ni `issues` ;
- réponses d'erreur construites dans les contrôleurs sous forme de chaînes, maps ou structures locales ;
- messages SQL/Java interprétés par recherche textuelle dans React ;
- erreurs techniques parfois renvoyées directement au client.

### Modifications

- vocabulaire commun non omniscient composé de `BusinessDecision`, `DecisionStatus`, `RuleResult`, `BusinessIssue` et `ActionCapability` ;
- suppression du DTO redondant `PayrollPeriodDecision` et migration du workflow vers le contrat commun ;
- `SirhBusinessException` devient l'unique exception métier structurée avec code stable, statut HTTP, détails et issues ;
- suppression des anciennes classes parallèles `SirhConflictException` et `SirhNotFoundException` inutilisées ;
- catalogue de codes stables complété pour validation, conflit, authentification, ressource absente, intégrité et workflow Paie ;
- `ApiErrorResponse` expose uniformément `code`, `message`, `details` et `issues` ;
- `GlobalExceptionHandler` est l'unique producteur des réponses 4xx/5xx des contrôleurs ;
- `SirhExceptionTranslator` conserve les exceptions déjà typées et masque les détails des exceptions techniques checked ;
- suppression globale des `error maps`, des `createErrorResponse`, des réponses textuelles 4xx/5xx et des fuites `e.getMessage()` depuis les contrôleurs ;
- React consomme le code, les détails et les issues renvoyés et ne recherche plus de fragments SQL/Java dans un message.

### Fichiers principaux

- `backend/src/main/java/mg/galaxam/sirh/api/dto/common/BusinessDecision.java`
- `backend/src/main/java/mg/galaxam/sirh/api/dto/common/BusinessIssue.java`
- `backend/src/main/java/mg/galaxam/sirh/api/dto/common/RuleResult.java`
- `backend/src/main/java/mg/galaxam/sirh/api/dto/common/ActionCapability.java`
- `backend/src/main/java/mg/galaxam/sirh/api/dto/common/ApiErrorResponse.java`
- `backend/src/main/java/mg/galaxam/sirh/common/exception/SirhBusinessException.java`
- `backend/src/main/java/mg/galaxam/sirh/common/exception/SirhExceptionTranslator.java`
- `backend/src/main/java/mg/galaxam/sirh/config/GlobalExceptionHandler.java`
- contrôleurs sous `backend/src/main/java/mg/galaxam/sirh/api/controller/`
- `frontend/src/api/apiClient.js`

### Tests ajoutés et adaptés

- `ErrorContractArchitectureTest` interdit les réponses d'erreur fabriquées dans les contrôleurs et le parsing de messages techniques dans React ;
- `PayrollPeriodWorkflowIntegrationTest` vérifie le code, les détails et l'issue d'une transition bloquée ;
- `PaieAdversarialTest` vérifie désormais le contrat stable et non les anciennes clés locales.

### Tests exécutés et résultat

- contrat/Paie/workflow ciblés : 14/14 ;
- Paie adversarial après migration : 7/7 ;
- Congés adversarial : 5/5 ;
- backend complet, deux runs isolés : 157/157 puis 157/157 ;
- frontend lint : OK ;
- frontend build : OK ;
- recherche `error map`, réponse 4xx/5xx de contrôleur et parsing technique React : 0 résultat ;
- `git diff --check` : OK.

### Dette restante

- plusieurs contrôleurs historiques conservent encore des blocs `catch` pour rollback/logging ; ils propagent désormais vers le contrat unique mais leurs frontières transactionnelles seront simplifiées en phase 5 ;
- les exceptions `IllegalArgumentException` et `IllegalStateException` historiques reçoivent des codes génériques stables ; leur migration progressive vers des codes métier plus précis se fera règle par règle, sans réécriture globale ;
- les pages React qui lisent encore directement `response.data.message` restent compatibles, mais seront alignées sur `getErrorMessage` pendant les migrations de pages des phases 6 à 8.

### Impact estimé

- cohérence API et explicabilité : environ 8/10 ;
- sécurité des erreurs techniques : environ 8/10 ;
- maintenabilité frontend/backend : gain estimé de 3 à 5 points globaux, à confirmer au contre-audit.

## Phase 3 — SQL métier — TERMINÉE

### Avant

- `fn_jours_ouvrables` recalculait dans PostgreSQL une règle déjà détenue par `CalendrierOuvrableEngine` ;
- `v_config_legale_active` choisissait une configuration selon `CURRENT_DATE` et calculait le plafond social ;
- la vue salarié et `PayrollService` remplaçaient silencieusement des assujettissements absents par `true` ou `false` ;
- les colonnes d'assujettissement acceptaient des valeurs absentes ou possédaient des valeurs fiscales par défaut.

### Modifications

- inventaire des consommateurs Java et des dépendances PostgreSQL avant suppression ;
- suppression avec `RESTRICT` de `fn_jours_ouvrables` et `v_config_legale_active` ;
- conservation des owners Java canoniques `CalendrierOuvrableEngine` et `PaieConfigLegale.findActiveAt(targetDate)` ;
- projection brute des assujettissements dans `v_employe_actif_courant`, sans décision de secours ;
- suppression des `COALESCE(...soumis..., TRUE/FALSE)` dans la préparation de paie ;
- un unique garde de configuration dans `PayrollService` bloque un régime ou une rubrique incomplet avec `BLOCKING_CONFIGURATION_ERROR`, détails et issue stable ;
- création et persistance des rubriques et régimes uniquement avec tous les indicateurs explicitement renseignés ;
- colonnes d'assujettissement `NOT NULL` et sans défaut SQL ; la migration refuse de s'appliquer si des données existantes sont incomplètes ;
- migration appliquée sur `sirh_galaxam` et `sirh_galaxam_test`.

### Fichiers principaux

- `backend/conception/bdd/02_schema_referentiels_rh.sql`
- `backend/conception/bdd/03_schema_employes_contrats.sql`
- `backend/conception/bdd/05_schema_pointage.sql`
- `backend/conception/bdd/06_schema_paie.sql`
- `backend/conception/bdd/2026-09-14_remove_business_sql.sql`
- `backend/src/main/java/mg/galaxam/sirh/model/paie/service/PayrollService.java`
- `backend/src/main/java/mg/galaxam/sirh/model/referentiel/RefTypeIntervenant.java`
- `backend/src/main/java/mg/galaxam/sirh/api/controller/paie/PaieRubriqueController.java`

### Tests ajoutés

- `PassiveSqlArchitectureTest` interdit le retour de la fonction, de la vue datée et des fallbacks fiscaux dans le schéma actif ou le service Paie ;
- `PaieAdversarialTest` couvre une variable sans rubrique configurée et la création d'une rubrique sans assujettissements explicites.

### Tests exécutés et résultat

- tests SQL/Paie ciblés : 11/11 ;
- backend complet, deux runs isolés : 161/161 puis 161/161 ;
- frontend lint : OK ;
- frontend build : OK ;
- `git diff --check` : OK ;
- audit des données avant contrainte : 0 régime incomplet, 0 rubrique incomplète ;
- dépendances SQL aval sur les deux artefacts supprimés : 0 ;
- artefacts actifs identifiés par G-004 : `BUSINESS_CALCULATION = 0`, `BUSINESS_DECISION = 0`.

### Dette restante

- les migrations historiques et anciens documents conservent volontairement les définitions passées ; ils ne sont pas rejoués comme schéma actif et seront classifiés en phase 11 ;
- certaines vues réalisent des agrégations techniques autorisées (comptages et sommes de mouvements) ; leur temporalité et leurs filtres seront contrôlés en phase 4 ;
- les triggers d'immutabilité restent des contraintes de défense en profondeur, le workflow Java demeurant l'owner de la décision.

### Impact estimé

- SQL : environ 8/10 sur le périmètre métier audité ;
- configuration Paie explicite : environ 8/10 ;
- cohérence historique : gain estimé de 2 à 3 points globaux, à confirmer après la phase 4.

### Classification documentaire

| Document | Statut | Raison | Action |
|---|---|---|---|
| `docs/AUDIT_GLOBAL_CONTRADICTOIRE_2026-09-14.md` | KEEP | les trois preuves G-004 étaient exactes avant correction | conserver comme baseline datée |
| `docs/pointage.md` et `docs/pointage-cpl.md` | OBSOLETE | présentent encore `fn_jours_ouvrables` comme owner validé | corriger en phase 11 après stabilisation |
| migrations archivées | KEEP | historique immuable, non source du schéma courant | ne pas réécrire |

## Phase 4 — Temporalité — TERMINÉE

### Avant

- le calendrier employé prenait la dernière affectation par identifiant, indépendamment de la période calculée ;
- les anomalies choisissaient une période ouverte puis la dernière période en fallback ;
- le STC substituait la date du jour, la dernière affectation et la dernière période lorsqu'un contexte historique manquait ;
- plusieurs chargements Pointage utilisaient les paramètres actuels pour recalculer une période passée.

### Modifications

- `CalendrierOuvrableEngine.resolveSiteAt` est l'unique résolution du site à une date métier ; absence, site manquant et chevauchement bloquent explicitement ;
- une plage traversant une mutation applique le calendrier de chaque site jour par jour ;
- l'API anomalies exige `periodId` ou `datePointage`, détecte les périodes chevauchantes et n'invente plus de période ;
- le hook React attend ce contexte explicite avant l'appel ;
- STC exige la date de départ, résout affectation, période, ancienneté, IRSA/ISI et configuration légale à cette date, sans fallback `now/latest` ;
- la résolution ISI et les paramètres globaux datés détectent les configurations concurrentes ;
- Paie et synthèses Pointage utilisent les dates de période pour les configurations ;
- l'attente TMM figée à 22 jours a été remplacée par une assertion issue du calendrier canonique réel.

### Fichiers principaux

- `backend/src/main/java/mg/galaxam/sirh/model/pointage/engine/CalendrierOuvrableEngine.java`
- `backend/src/main/java/mg/galaxam/sirh/model/pointage/service/PointageAnomalieService.java`
- `backend/src/main/java/mg/galaxam/sirh/model/pointage/service/PointageSyntheseService.java`
- `backend/src/main/java/mg/galaxam/sirh/model/paie/service/PayrollService.java`
- `backend/src/main/java/mg/galaxam/sirh/model/paie/service/StcService.java`
- `backend/src/main/java/mg/galaxam/sirh/model/paieconfig/ParametreGlobal.java`
- `frontend/src/hooks/pointage/usePointageAnomalies.js`

### Tests ajoutés et exécutés

- `CalendrierOuvrableTemporalIntegrationTest` : avant changement, jour du changement, après, trou et chevauchement ;
- `PointagePeriodContextIntegrationTest` : contexte absent et périodes ambiguës ;
- `HistoricalContextArchitectureTest` interdit le retour des fallbacks critiques ;
- tests ciblés Paie/STC/Pointage : verts ;
- backend complet, après correction de l'attente obsolète : 167/167 deux fois ;
- frontend lint/build : OK ; `git diff --check` : OK.

### Dette restante

- les usages de l'horloge correspondant réellement au « maintenant » (validation d'une saisie future, schedulers, dashboards courants) sont conservés ;
- les écrans de contexte courant seront centralisés par l'API de capacités en phase 6 ;
- les résolutions datées secondaires de Carrière seront consolidées pendant l'extraction de phase 5, sans big bang.

### Impact estimé

- temporalité des chemins Paie/Pointage/STC : environ 8/10 ;
- explicabilité des données historiques : environ 8/10 ;
- score global : gain estimé de 3 à 5 points, à confirmer au contre-audit.

## Phase 5 — Granularité backend — TERMINÉE

### Avant

- les contrôleurs Congés et Contrat ouvraient, validaient et annulaient eux-mêmes leurs transactions JDBC ;
- les mêmes séquences `setAutoCommit/commit/rollback` étaient recopiées dans les entrées autonomes de Paie ;
- `PayrollService` chargeait directement configuration légale, taux, tranches, santé, indemnités et paramètres dynamiques dans une méthode d'orchestration déjà volumineuse ;
- les absences de configuration étaient traduites par plusieurs helpers locaux possibles.

### Modifications

- création d'un unique `TransactionRunner.required` comme frontière transactionnelle JDBC réutilisable ;
- migration des cinq mutations de demande de congé, de la modification du brouillon de contrat et des trois entrées transactionnelles de Paie ;
- les contrôleurs migrés ne possèdent plus de `commit`, `rollback` ou `setAutoCommit` ;
- suppression du masquage des erreurs de contrat en erreur générique de validation ;
- extraction d'un unique `PayrollLegalContextResolver` daté, appelé une seule fois avant le moteur déterministe ;
- création d'un unique `PayrollConfigurationGuard` pour les indicateurs, configurations absentes et configurations ambiguës ;
- aucun moteur certifié de Pointage ou de Paie n'a été modifié.

### Fichiers principaux

- `backend/src/main/java/mg/galaxam/sirh/common/transaction/TransactionRunner.java`
- `backend/src/main/java/mg/galaxam/sirh/model/paie/service/PayrollLegalContextResolver.java`
- `backend/src/main/java/mg/galaxam/sirh/model/paie/service/PayrollConfigurationGuard.java`
- `backend/src/main/java/mg/galaxam/sirh/model/paie/service/PayrollService.java`
- `backend/src/main/java/mg/galaxam/sirh/api/controller/conge/DemandeCongeController.java`
- `backend/src/main/java/mg/galaxam/sirh/api/controller/contrat/ContratTravailController.java`

### Tests ajoutés et exécutés

- `TransactionRunnerIntegrationTest` vérifie commit et rollback intégral ;
- `BackendGranularityArchitectureTest` interdit le retour de transactions manuelles dans les trois composants migrés et protège l'owner unique du contexte légal ;
- tests ciblés Paie, STC, Congés et architecture : verts ;
- backend complet : 171/171 ;
- frontend lint : OK ; frontend build : OK ;
- `git diff --check` : OK.

### Dette restante

- les contrôleurs référentiels historiques conservent encore leurs transactions JDBC ; le runner fournit désormais la voie de migration, sans conversion mécanique risquée ;
- `PointageSyntheseService`, `PointageSaisieService` et `WorkflowRhService` restent volumineux, mais leurs moteurs canoniques sont isolés et couverts ; les prochaines extractions devront rester guidées par un cas et un test ;
- les lectures enrichies de certains contrôleurs historiques conservent du mapping SQL, à sortir progressivement lorsqu'une page est migrée.

### Impact estimé

- cohérence des transactions sur les parcours critiques : environ 7,5/10 ;
- granularité des responsabilités critiques : environ 7/10 ;
- maintenabilité Paie : gain estimé de 1 à 2 points globaux.

## Phase 6 — Frontend backend-driven — TERMINÉE

### Avant

- React choisissait localement la première période ou la période ouverte et recomposait les actions avec le statut, le pointage et le nombre de bulletins ;
- les endpoints de calcul et de validation n'imposaient pas la même décision que l'interface ;
- un calcul réel produisait directement des bulletins `VALIDE`, ce qui rendait l'étape de validation essentiellement décorative ;
- la liste des périodes et le cockpit possédaient deux logiques parallèles pour autoriser les transitions.

### Modifications

- création d'une vue de contexte datée fournissant période courante, période sélectionnée, état, version, issues et `availableActions` ;
- `PayrollPeriodWorkflowService` reste l'unique owner des transitions et fournit aussi les capacités `SIMULATE`, `CALCULATE`, `VALIDATE` et `ADD_VARIABLE` ;
- les endpoints de calcul, simulation et validation contrôlent côté serveur la même capacité que celle exposée au client ;
- le calcul produit désormais l'état individuel `CALCULE` et seule la commande de validation le passe à `VALIDE` ;
- le cockpit et la liste des périodes consomment les capacités backend au lieu de reconstruire les décisions métier ;
- la période sélectionnée est fournie par le contexte backend et restaurable avec le paramètre URL `periodId` ;
- les erreurs frontend utilisent exclusivement le code et le message structurés du contrat API.

### Fichiers principaux

- `backend/src/main/java/mg/galaxam/sirh/model/paie/workflow/PayrollPeriodWorkflowService.java`
- `backend/src/main/java/mg/galaxam/sirh/model/paie/workflow/PayrollAction.java`
- `backend/src/main/java/mg/galaxam/sirh/api/dto/paie/PayrollPeriodContextView.java`
- `backend/src/main/java/mg/galaxam/sirh/api/controller/paie/PeriodePaieController.java`
- `backend/src/main/java/mg/galaxam/sirh/api/controller/paie/PaieController.java`
- `frontend/src/pages/paie/GestionPaiePage.jsx`
- `frontend/src/components/paie/CockpitPaieTab.jsx`
- `frontend/src/components/paie/PeriodePaiePage.jsx`

### Tests ajoutés et exécutés

- contexte courant/sélectionné et capacités testés dans `PayrollPeriodWorkflowIntegrationTest` ;
- double validation et refus serveur testés dans `PaieControllerIntegrationTest` ;
- `BackendDrivenFrontendArchitectureTest` interdit le retour des décisions Paie critiques dans React ;
- backend complet exécuté deux fois après la correction du cycle `CALCULE → VALIDE`, second passage : 173/173 ;
- frontend lint : OK ; frontend build : OK ;
- `git diff --check` : OK.

### Dette restante

- les capacités fines d'une variable ou d'un bulletin restent à porter par les réponses de ressource pendant la refonte Paie ;
- Pointage, Congés et Carrière seront migrés vers le même vocabulaire, sans créer de second moteur de décision, en phase 8 ;
- le chargement des capacités d'une liste de périodes effectue encore plusieurs lectures par période ; il sera agrégé avec la pagination en phase 9.

### Impact estimé

- backend-driven du workflow Paie critique : environ 8/10 ;
- frontend assistant sur les actions de période : environ 8/10 ;
- cohérence calcul/validation : gain structurel estimé de 2 à 3 points globaux.

## Phase 7 — Refonte du workflow Paie — TERMINÉE

### Avant

- `/paie?tab=bulletins` pouvait sélectionner un onglet inexistant et rendre une zone vide ;
- la Paie restait présentée comme quatre CRUD sans parcours partageable par période ;
- la population du batch et l'effectif du cockpit provenaient de requêtes différentes ;
- les exports réglementaires étaient accessibles dès le calcul, avant validation ;
- readiness, résultats, validation et sorties ne formaient pas un message de campagne traçable unique.

### Modifications

- ajout d'un parcours linéaire partageable par URL : préparation, prérequis, population, variables, calcul/contrôle, validation et sorties ;
- redirection sûre de l'ancien onglet `bulletins` et fallback systématique des onglets inconnus ;
- route individuelle partageable `/paie/periods/:periodId/results/:employeeId` ouvrant le détail explicable du bulletin ;
- création de l'unique `PayrollPopulationResolver`, utilisé à la fois par le batch et le cockpit ;
- population datée exposée avec contrat, régime, décision `INCLUDED/EXCLUDED/BLOCKED` et reason code ;
- une affectation ambiguë bloque désormais réellement la capacité `CALCULATE` au lieu d'être choisie implicitement ;
- le cockpit renvoie dans une seule réponse données, readiness `PASS/WARNING/BLOCK`, population, synthèse de validation et contexte de décision canonique ;
- la validation affiche population, net, charges, impôts, warnings, version, auteur et date de calcul ;
- création de la capacité backend `GENERATE_OUTPUT` : toutes les sorties et prévisualisations sont refusées avant validation ;
- sorties exposées avec statut, version, campagne source et checksum SHA-256 de leur source de campagne ;
- suppression du faux indicateur statique « Congés synchronisés ».

### Fichiers principaux

- `backend/src/main/java/mg/galaxam/sirh/model/paie/service/PayrollPopulationResolver.java`
- `backend/src/main/java/mg/galaxam/sirh/model/paie/workflow/PayrollPeriodWorkflowService.java`
- `backend/src/main/java/mg/galaxam/sirh/api/dto/paie/PayrollPopulationMember.java`
- `backend/src/main/java/mg/galaxam/sirh/api/dto/paie/PayrollOutputView.java`
- `backend/src/main/java/mg/galaxam/sirh/api/controller/paie/PaieController.java`
- `frontend/src/pages/paie/GestionPaiePage.jsx`
- `frontend/src/components/paie/CockpitPaieTab.jsx`
- `frontend/src/components/paie/ExportsPaieTab.jsx`
- `frontend/src/routes/Router.jsx`

### Tests ajoutés et exécutés

- test d'intégration du message cockpit unifié, de la population et des métadonnées de sortie ;
- test d'affectation ambiguë et blocage backend du calcul ;
- test de refus des exports avant validation puis succès après validation ;
- test d'architecture des routes Paie, du lien historique et de la décision unifiée ;
- backend complet : 181/181 ;
- frontend lint : OK ; frontend build : OK ;
- `git diff --check` : OK.

### Dette restante

- les résultats restent chargés sans pagination serveur ; la phase 9 remplace ce chargement collectif ;
- l'historique d'import des variables reste moins riche que le parcours principal ;
- le checksum porte la source canonique de campagne ; la persistance d'un registre de fichiers effectivement générés serait une amélioration ultérieure, non nécessaire pour autoriser correctement le workflow.

### Impact estimé

- workflow Paie : environ 8/10 ;
- frontend Paie : environ 7,5/10 ;
- explicabilité et traçabilité des sorties : environ 8/10.

## Phase 8 — Pointage, Congés, Carrière et Employés — TERMINÉE

### Avant

- Pointage recomposait dans plusieurs pages les droits de saisie, transmission et arbitrage à partir du statut, de la date, des anomalies et des permissions ;
- Congés exposait de nombreux booléens redondants (`canValidate`, `isModifiable`, etc.) au lieu d'un vocabulaire d'actions commun ;
- les pages choisissaient encore localement une période avec le premier élément ou le dernier statut connu ;
- les onglets Employé n'étaient pas restaurables par URL.

### Modifications

- extension de l'unique `PayrollPeriodWorkflowService` avec les actions Pointage `EDIT_POINTAGE` et `REGULARIZE_ANOMALY`, sans créer de workflow parallèle ;
- la capacité `SUBMIT` vérifie et explique côté backend : période achevée, pointage présent et absence d'anomalie bloquante ; l'exécution impose exactement la même décision ;
- Saisie, Anomalies et Arbitrage utilisent le contexte de période et les `availableActions` backend ;
- Congés publie un unique message `availableActions` (`APPROVE`, `REJECT`, `EDIT`, `CANCEL`, `MODIFY_DATES`) et les anciens booléens sérialisés ont été supprimés ;
- les vues Congés consomment ces actions sans recomposer le workflow ;
- les sections Employé sont partageables via `/employes/:id/:section` ;
- les conditions de statut restantes sont limitées à la présentation et aux données affichées.

### Fichiers principaux

- `backend/src/main/java/mg/galaxam/sirh/model/paie/workflow/PayrollPeriodWorkflowService.java`
- `backend/src/main/java/mg/galaxam/sirh/model/paie/workflow/PointagePeriodAction.java`
- `backend/src/main/java/mg/galaxam/sirh/model/conge/VDemandeCongeCpl.java`
- `frontend/src/pages/pointage/SaisieQuotidiennePage.jsx`
- `frontend/src/pages/pointage/SuiviAnomaliesPage.jsx`
- `frontend/src/pages/pointage/ArbitrageDafPage.jsx`
- `frontend/src/components/conges/validation/ValidationTable.jsx`
- `frontend/src/pages/employe/EmployeFicheDetailPage.jsx`

### Tests ajoutés et exécutés

- `WorkflowCapabilitiesTest` couvre les capacités Pointage et Congés ;
- `WorkflowAssistantArchitectureTest` interdit les recompositions critiques dans React ;
- E2E Pointage, Congés et workflow d'intégration ciblés : verts ;
- les anciens tests RBAC ont été caractérisés avec une période achevée et un pointage réel ;
- backend complet : 186/186 ;
- frontend lint : OK ; frontend build : OK ;
- `git diff --check` : OK.

### Dette restante

- le workflow Carrière historique conserve des helpers de présentation ; ses mutations restent toutefois gouvernées par le backend et ses tests E2E ;
- la modal multi-jours d'anomalie reste volumineuse : la remplacer par un drawer est une amélioration UX, pas un second moteur métier ;
- les recherches de population sont traitées en phase 9.

### Impact estimé

- workflow Pointage : environ 7,5/10 ;
- workflow Congés : environ 7,5/10 ;
- navigation Employé : environ 7/10.

## Phase 9 — Pagination, performance et accessibilité — TERMINÉE

### Avant

- plusieurs sélecteurs chargeaient arbitrairement 1 000 ou 10 000 salariés ;
- la liste Employés refiltrait et retriait en mémoire une page déjà tronquée ;
- le cockpit Paie renvoyait tous les bulletins et appliquait recherche et filtres dans React ;
- ses filtres n'étaient pas restaurables via URL.

### Modifications

- recherche, statut, site, département, service et tri de la liste Employés sont exécutés côté serveur avec une taille maximale de page de 100 ;
- suppression du second filtrage/tri/paging React ;
- remplacement des chargements massifs par l'unique `EmployeSearchSelect`, débouncé et paginé serveur ;
- ajout de pagination, recherche et filtres serveur au message cockpit Paie ;
- le parcours JDBC calcule chaque contrôle individuel en Java une seule fois, compte les correspondances et ne conserve que la page demandée ;
- les métadonnées `bulletinsPage` et `populationPage` sont transportées dans la même réponse ;
- recherche, filtres et pagination Paie sont restaurables dans l'URL ;
- utilisation du composant commun `Pagination`, avec libellés et contrôles accessibles.

### Fichiers principaux

- `backend/src/main/java/mg/galaxam/sirh/model/employe/service/EmployeService.java`
- `backend/src/main/java/mg/galaxam/sirh/model/employe/EmployeCPL.java`
- `backend/src/main/java/mg/galaxam/sirh/model/paie/service/PayrollService.java`
- `backend/src/main/java/mg/galaxam/sirh/api/controller/paie/PaieController.java`
- `frontend/src/pages/employe/EmployeList.jsx`
- `frontend/src/components/paie/CockpitPaieTab.jsx`
- `frontend/src/components/ui/forms/EmployeSearchSelect.jsx`

### Tests ajoutés et exécutés

- `EmployePaginationIntegrationTest` injecte 10 001 salariés et vérifie les 101 pages ainsi que le dernier enregistrement ;
- test API du cockpit : page 2, taille 1, total et recherche serveur ;
- test d'architecture interdisant `size=1000`, `size=10000` et le retour des filtres Paie locaux ;
- backend complet : 189/189 ;
- frontend lint : OK ; frontend build : OK ;
- `git diff --check` : OK.

### Dette restante

- la résolution de population parcourt encore les dossiers actifs pour produire les décisions individuelles canoniques ; seule la page demandée est sérialisée, et le batch réutilise exactement ce resolver ;
- une optimisation par curseur pourra être ajoutée au-delà de plusieurs dizaines de milliers de salariés, sans déplacer la décision vers SQL ;
- l'accessibilité visuelle et les statuts sémantiques sont consolidés en phase 10.

### Impact estimé

- pagination et exhaustivité : environ 8/10 ;
- performance des parcours de liste : environ 7,5/10 ;
- navigation filtrée : environ 8/10.

## Phase 10 — Design system — TERMINÉE

### Avant

- les écrans métier les plus exposés mélangeaient encore des couleurs nominatives et des badges locaux ;
- quelques contrôles de Pointage ne suivaient pas la convention existante `rounded-none` ;
- le système de composants était présent, mais aucune règle automatisée ne protégeait son usage dans le cockpit Paie.

### Modifications

- lecture de `frontend/src/index.css` avant migration : les tokens et composants existants ont été réutilisés, sans créer un second design system ;
- les statuts du cockpit Paie utilisent désormais les composants communs `Badge`, `Alert` et `Pagination` ainsi que des tokens sémantiques ;
- l'en-tête du tableau de bord et les contrôles Pointage migrés n'emploient plus de couleurs de statut nominatives ni de rayon non conforme ;
- ajout d'un test d'architecture qui vérifie les composants socles, les usages du cockpit et la convention de rayon.

### Fichiers principaux

- `frontend/src/index.css` (contexte vérifié, non dupliqué)
- `frontend/src/components/paie/CockpitPaieTab.jsx`
- `frontend/src/components/dashboard/DashboardHeader.jsx`
- `frontend/src/components/pointage/PointageToolbar.jsx`
- `frontend/src/components/pointage/DafAjustementPointageModal.jsx`
- `backend/src/test/java/mg/galaxam/sirh/unit/architecture/DesignSystemArchitectureTest.java`

### Tests ajoutés et exécutés

- `DesignSystemArchitectureTest` : vert ;
- backend complet : 189/189 à la clôture de la phase ;
- frontend lint : OK ; frontend build : OK ;
- `git diff --check` : OK.

### Dette restante

- les écrans non critiques conservent encore quelques couleurs historiques : leur migration doit rester progressive et sémantique, sans remplacement mécanique global ;
- l'extension de la bibliothèque de composants se fera seulement lorsqu'un besoin récurrent le justifiera.

### Impact estimé

- cohérence visuelle : environ 7/10 ;
- design system : environ 7/10.

## Phase 11 — Documentation et CI — TERMINÉE

### Avant

- le wrapper Maven n'était pas reproductible ;
- le projet ne possédait pas de pipeline CI couvrant les gates backend, frontend et qualité de diff ;
- README, guide de déploiement et documents d'architecture contenaient des noms de moteurs, versions ou compteurs de tests périmés ;
- certains documents métier anciens pouvaient être lus à tort comme des règles légales actuelles.

### Modifications

- génération du wrapper Maven officiel 3.9.9 (`./mvnw`) et ajout de sa configuration versionnée ;
- ajout de la CI GitHub : PostgreSQL prêt par healthcheck, tests Maven par wrapper, résumé Surefire généré, lint/build frontend et contrôle `git diff --check` ;
- ajout du script `surefire-summary.py` : le nombre de tests est désormais lu depuis les rapports Surefire, jamais maintenu à la main ;
- mise à jour du README, du déploiement et de l'architecture pour refléter les moteurs canoniques et les exigences de production ;
- ajout des migrations actives nécessaires aux bases Docker et d'un healthcheck PostgreSQL ;
- marquage explicite des documents anciens comme `OBSOLETE`, `UPDATE` ou `LEGAL_REVIEW` lorsque le code et les tests constituent la source de vérité.

### Fichiers principaux

- `backend/mvnw`, `backend/mvnw.cmd`, `backend/.mvn/wrapper/maven-wrapper.properties`
- `.github/workflows/ci.yml`
- `backend/scripts/surefire-summary.py`
- `docker-compose.yml`, `docker/init-databases.sql`
- `README.md`, `Deployement.md`, `backend/docs/ARCHITECTURE_BACKEND.md`
- `docs/cartographie_architecturale_sirh.md`, `docs/GUIDE_COMPLET_MODULE_PAIE_GALAXAM.md`, `backend/docs/boky/module-paie.md`

### Tests exécutés

- `backend/./mvnw -q test` : 192/192, 0 échec, 0 erreur ;
- exécution ciblée des scénarios adversariaux Paie/Pointage : verte ;
- frontend lint : OK ; frontend build : OK ;
- `docker compose config -q` : OK ;
- `git diff --check` : OK.

### Dette restante

- la CI n'a pas encore eu sa première exécution sur GitHub : sa syntaxe et ses commandes ont été validées localement ;
- les contenus légaux marqués `LEGAL_REVIEW` exigent une validation humaine compétente avant toute évolution de règle ;
- le contre-audit de phase 12 doit encore vérifier l'ensemble du périmètre, sans recycler les anciens scores.

### Impact estimé

- reproductibilité CI : environ 8/10 ;
- documentation opérationnelle : environ 7/10.

## Phase 12 — Contre-audit final — TERMINÉE

### Méthode et résultat

- contre-audit mené sur le code, les migrations Docker actives, les tests d'intégration et les tests d'architecture actuels ;
- les fichiers documentaires historiques ont été traités comme contexte et classifiés séparément ;
- aucun P0/P1 structurel de l'audit initial n'est conservé dans son périmètre ;
- score global conservateur : **76/100**.

### Preuves de gate

- `backend/./mvnw -q test` : **192/192**, 0 échec, 0 erreur ;
- frontend lint et build : OK ;
- `docker compose config -q` : OK ;
- `git diff --check` : OK ;
- test 10 001 employés, tests de sécurité, concurrence, temporalité, routes et workflow : verts.

### Rapport détaillé

- `docs/CONTRE_AUDIT_GLOBAL_2026-09-15.md`

### Dette restante

- maintenance progressive des services historiques les plus longs ;
- UX mobile/accessibilité approfondie et première exécution GitHub de CI ;
- validation juridique externe des règles marquées `LEGAL_REVIEW`.
