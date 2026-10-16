# Domaine Paie

Les campagnes délèguent la couverture temporelle contrat/affectation à
`contrat.application.EmploymentContextResolver`. La paie conserve ses résolutions
distinctes pour fiscalité, cotisations et paramètres légaux, dont les dates
métier ne sont pas interchangeables avec celles du contrat.

Ce dossier regroupe le moteur de paie, la préparation et le calcul des bulletins, les campagnes de paie, le solde de tout compte (STC) et les exports fiscaux/sociaux.

## Contexte d'un bulletin

`PayrollCalculationContextAssembler` construit l'unique `PayrollCalculationInput` transmis à
`PayrollExecutionEngine`. Sa méthode `assemble` appelle, dans l'ordre, les lectures de période,
emploi, salaire et ancienneté datés, banque, famille fiscale, snapshot Pointage, congés et lots historiques,
configuration légale, mouvements approuvés et prêt actif. Les méthodes unitaires partagent la
même connexion et la même transaction. `PayrollService.preparerInput` reste la façade publique.
Avant le retour, l'assembleur contrôle la couverture salariale, le snapshot, la configuration
légale, le site, la catégorie, le mode de calcul et les taux obligatoires selon l'assujettissement.
Une configuration absente bloque le calcul ; elle ne devient pas un site, une catégorie ou un
paramètre inventé.

La date de fin de campagne résout les paramètres mensuels ; les heures majorées utilisent la
date de chaque tranche de pointage, le salaire suit ses jours d'effet, et les congés consommés
conservent la valeur de leurs lots. L'assembleur prépare les valeurs ; le moteur calcule sans SQL.
En calcul réel, la préparation verrouille les mouvements et peut actualiser le montant d'une
rubrique dérivée dans la transaction. La simulation annule ces effets à son point de sauvegarde.
Ce contexte appartient au bulletin mensuel. Le STC, le journal des acquisitions congé et les
déclarations ont leurs propres dates métier et consomment des résultats ou résolveurs du domaine.
L'entrée reste mutable pendant l'assemblage ; son immutabilité stricte n'est pas encore imposée.

### Inventaire des contextes et limite de l'unification

Trois objets portent des données de contexte utiles au bulletin : `EmploymentContext`
est une vue contrat/affectation **à une date**, `PayrollLegalContextResolver` résout
les référentiels légaux **à la fin de campagne** (et les majorations au jour de la
tranche), et `PayrollCalculationInput` réunit leurs résultats avec pointage figé,
congés, mouvements et prêt pour **un bulletin mensuel**. Le premier et le deuxième
sont des sources de résolution spécialisées ; seul le troisième est remis au moteur.
L'assembleur utilise désormais `EmploymentContextResolver.resolveContext` à la date du
dernier jour couvert par l'emploi : identité, contrat, affectation, site, catégorie,
régime et salaire de clôture viennent de cette projection unique. La couverture et le
salaire journalier appliquent les mêmes statuts admissibles. Les indicateurs sociaux
manquants sont bloquants et ne deviennent plus des valeurs implicites.

Ce regroupement ne constitue pas une garantie d'exhaustivité globale : les exports et
le STC ont des dates métier distinctes, et `PayrollCalculationInput` reste mutable avec
quelques valeurs par défaut historiques. Ces parcours doivent être audités avant de
prétendre à une source d'exécution unique pour tout le domaine Paie.
La préparation STC réutilise maintenant cette projection Emploi à la date de départ ;
la santé du STC résout le code exact du site et son diviseur horaire doit être configuré
à cette date. Les autres traitements de calcul et les exports STC restent distincts
du contexte du bulletin mensuel.
Les cotisations CNaPS, FMFP et Santé sont résolues par
`paieconfig.application.SocialContributionContextResolver`, appelé par le bulletin à
la fin de campagne et par le STC à la date de départ. L'assiette et le calcul restent
dans leurs moteurs respectifs ; aucune valeur de taux n'est recopiée entre contextes.
L'export DNTS utilise le même résolveur pour ses libellés de taux et son contrôle
trimestriel. Sa régularisation trimestrielle existante reste une règle de déclaration
distincte du calcul mensuel figé.
Les exports obtiennent l'identité de la filiale employeur auprès de
`contrat.application.EmployerIdentityContextResolver`, sur la plage de campagne ;
un contrat futur ne peut plus changer l'employeur affiché sur une ancienne campagne.
DNTS et SAFI partagent `PayrollExportContextResolver` pour ne déclarer un départ
que dans la campagne concernée. Une exportation sans bulletin ne devine plus la
filiale à partir de la première fiche active.
L'identité légale de l'employeur n'est toutefois pas figée dans `bulletin_paie` : une
modification rétroactive d'un contrat ou de `ref_filiale` peut encore modifier une
réimpression historique. Résoudre ce dernier cas demande une décision de conservation
des données d'émission avant toute migration de schéma.

- `api/` : contrôleurs REST (`PaieController`, `PaieAjustementController`, `PaieRubriqueController`, `StcController`)
- `api/dto/` : contrats DTO (`PayrollCampaignStep`, `CalculPeriodeRequest`, `stc/*`, etc.)
- `application/` : services applicatifs (`PayrollService`, `StcService`, `workflow/`, `export/`)
- `domain/` : moteurs de calculs purs et invariants fiscaux (`engine/`, `dto/`)
- `persistence/` : entités de paie et tables historiques (`BulletinPaie`, `BulletinPaieLigne`, `PaiePret`, `PaieAvanceAcompte`, `PaieStc`, etc.)

Toutes les routes d'origine restent préservées sous `/paie/*`, `/stc/*`, `/api/paie/*`.

## Où modifier la paie

La proposition des bornes d'une nouvelle campagne (cycle 26–25 ou mois civil)
appartient à `application/PayrollPeriodCalendarService.java` et est exposée par
`GET /periodes/suggestion`. La page de création ne recalcule plus ces dates.
La couverture des pointages figés de la population incluse est vérifiée par
`application/PayrollPointageCoverage.java`, utilisée par le workflow de calcul et
le cockpit. La page Préparation affiche les contrôles transmis par le cockpit ;
Calendriers, Congés, Prêts et Indemnités restent explicitement « à vérifier »
tant qu'aucun contrôle autonome de campagne ne les prouve.

Le chemin du bulletin est `PayrollService` (orchestration) →
`PayrollCalculationContextAssembler` (lectures datées et contrôles) →
`PayrollCalculationInput` (données du calcul) → `PayrollExecutionEngine`
(calcul sans SQL). Une nouvelle donnée d'entrée se résout dans l'assembleur ou
dans le résolveur du domaine propriétaire ; le moteur reçoit sa valeur déjà
déterminée à la bonne date métier. Le contexte du bulletin n'est pas réutilisé
tel quel pour le STC, dont la date métier est celle du départ.

Dans `PayrollService`, chercher `calculerBulletinEmploye` pour le bulletin et
ses imputations, `calculerPaiePeriode` pour le calcul collectif,
`validerPaiePeriode` / `cloturerDonneesPaie` pour la fin de campagne et
`mettreAJourJournalConge` pour déclencher l'acquisition dans la transaction de
clôture. `PayrollLeaveJournalService` porte son calcul, la référence congé et
la valorisation mensuelle, en appelant les règles du domaine Congés.
`PayrollCockpitService` porte
les projections de période, les indicateurs et la pagination du cockpit DAF.
`PayrollEmployeeReadService` porte l'historique et la synthèse de l'onglet Paie
de l'employé. Les méthodes publiques historiques de `PayrollService` délèguent
à ces services de lecture pour préserver les appelants existants.

`PayrollPeriodContextView` contient les vues de résumé, de sortie et d'étape
de campagne ; `PayrollWorkflowStatuses` contient le statut de chaque phase.
Les noms des champs JSON restent ceux des records, sans enveloppe nouvelle.

| Responsabilité | Fichier à ouvrir |
| --- | --- |
| IRSA des salariés | `domain/engine/PayrollTaxCalculations.java` (`calculateIrsa`) |
| Versions datées du barème IRSA | `application/PayrollFiscalRulesResolver.java` |
| Routes HTTP des prestataires | `api/PrestataireController.java` ; voir `PRESTATAIRES_2026.md` |
| Commandes et transitions des factures prestataires | `application/PrestataireFactureService.java` et `domain/PrestataireFacturePolicy.java` |
| Montant, validité et assiettes des rubriques manuelles | `domain/engine/ManualPayrollRules.java` |
| Catalogue, validation et gestion/versionnement des rubriques manuelles | `domain/ManualRubriqueCatalog.java`, `domain/ManualRubriquePolicy.java`, `application/ManualRubriqueManagementService.java` |
| Actions projetées et exécution des ajustements | `application/workflow/PayrollAdjustmentWorkflowService.java` |
| Composition du brut et écarts de bulletin affichés | `domain/dto/BulletinPaieComplet.java` (`compositionBrut`) |
| Lecture de la version de rubrique en base | `application/ManualRubriqueResolver.java` |
| Statuts de la campagne Pointage/Paie | `application/workflow/PayrollWorkflowStatusResolver.java` |
| Commandes typées de la période, de la paie, du pointage et des ajustements | `application/workflow/PayrollWorkflowCommands.java` |
| Données SAFI et CSV | `application/export/SafiExportService.java` |
| Présentation SAFI XLS | `application/export/SafiXlsExportService.java` |
| Données sociales trimestrielles et classeurs | `application/export/QuarterlySocialDataService.java` |
| Employeur et départ déclarés dans les exports | `application/export/PayrollExportContextResolver.java` |
| Éligibilité commune des exports (période/bulletins finalisés) | `application/export/PayrollExportEligibility.java` |
| Alias d'annulation des ajustements et statuts finaux reconnus | `domain/PayrollStatuses.java` |
| Date métier et fuseau de référence | `domain/PayrollBusinessTime.java` |

Cette séparation garde les calculs unitaires regroupés et les accès SQL à
leurs dates d'effet hors du moteur. Le résolveur des statuts conserve un fichier
distinct, car son tableau de transitions est testé indépendamment du service
de workflow. Les commandes partagent un catalogue de types, tandis que les
requêtes d'API restent des objets de transport distincts. Chaque export conserve
des méthodes nommées pour charger ses données et produire ses formats ; les
entités persistées restent séparées selon leurs tables et cycles de vie.

Les exports mensuels, annuels et trimestriels consultent désormais la même
politique d'éligibilité : une période doit être en `PAIE_VALIDEE` ou `CLOTUREE`,
et chaque bulletin en `VALIDE` ou `CLOTURE`. Les contrôles communs sont centralisés
dans `PayrollExportEligibility`; les colonnes, règles de déclaration et modèles
XLS/XLSX restent propres à chaque document réglementaire. Les dates courantes
des workflows paie passent par `PayrollBusinessTime`; un calendrier peut recevoir
un `Clock` explicite pour rester déterministe en test.

### Statuts et capacités de campagne

`PayrollPeriodWorkflowService` décide si `CLOSE` et `GENERATE_OUTPUT` sont
autorisés. La commande de clôture et la projection `availableActions.CLOSE`
utilisent le même contrôle de fin de période, de validation de paie, de calcul
à jour et de couverture des bulletins. Le statut affiché provient de
`describeWorkflowStatuses`, qui délègue sa traduction à
`PayrollWorkflowStatusResolver` ; le code persistant `VALIDEE` désigne seulement
le pointage figé. Le sélecteur Paie demande `GET /periodes?lightweight=true&includePayrollStatus=true`
pour recevoir ces statuts sans recalculer leur sens dans le navigateur. Le
sélecteur Pointage peut conserver la réponse légère sans cet enrichissement.

## Configuration contrôlée des rubriques (sp44 à sp47)

Le moteur conserve un ordre de calcul explicite : rémunération brute, cotisations,
revenu et impôt, gains ajoutés au net, retenues personnelles, arrondi de règlement.
Une rubrique manuelle choisit son intervention parmi `BRUT`, `GAIN_NET`,
`RETENUE_NET`. L'ordre d'affichage ne déplace pas une règle entre ces étapes.

### Méthodes de valeur

- `MONTANT` : montant positif saisi, deux décimales à l'interface.
- `QUANTITE_TAUX` : quantité × prix unitaire ; six décimales conservées pour chaque paramètre.
- `POURCENTAGE_SALAIRE_BASE` : salaire contractuel de la période × pourcentage / 100.
  Cette base est indépendante des variables ; aucune dépendance circulaire implicite.

Le serveur calcule les méthodes dérivées, même si le client envoie un autre montant.
L'arrondi de rubrique est `CENTIME`, `ARIARY` ou `DIZAINE` (au plus proche,
moitié supérieure). Il est distinct de l'arrondi final de règlement du bulletin.
Les paramètres sources restent sur le mouvement. Le montant, les paramètres et
l'explication de la version exécutée restent sur la ligne du bulletin persisté.
Une simulation ne consomme pas le mouvement et ne persiste pas de bulletin.

### Assiettes et validité

Dans le brut, IRSA, CNaPS, santé et FMFP sont des contributions indépendantes.
Le FMFP historique hérite de CNaPS si son réglage est absent. Ce réglage ne remplace
pas les profils d'assujettissement du salarié ni les plafonds des calculateurs.
Un gain net ou une retenue nette ne peut pas alimenter ces assiettes.

Les versions couvrent une période entière ; les chevauchements sont interdits.
Une période à cheval sur deux versions est bloquée, sans prorata inventé.
Les conditions disponibles sont `TOUS`, `ASSUJETTI_IRSA`, `ASSUJETTI_CNAPS`,
`ASSUJETTI_SANTE`. Une condition non satisfaite bloque le mouvement approuvé,
plutôt que de l'ignorer silencieusement.
Une règle déjà utilisée ne change pas de méthode, d'assiette, de phase ou d'arrondi :
créer une version ultérieure. Une référence de traitement et une date d'effet
sont requises ; cette référence ne vaut pas validation juridique automatique.

### Calculateurs métier protégés

`PayrollCalculatorRegistry` expose la capacité de configuration dans le catalogue.
Il décrit les calculateurs existants ; il ne remplace pas leur exécution métier.
Les primes dédiées, cotisations, fiscalité et heures majorées utilisent leurs
paramètres de domaine. Congés, rupture, prêts et acomptes gardent leur circuit.
La base des congés reste valorisée par le taux historique du domaine congés.
Aucune case « base congés » non exécutée, formule libre, ordre arbitraire,
exonération partielle générique ou imputation comptable n'est proposée.

### Mise à niveau et recette

`26-10-01-sp47-methodes-rubriques.sql` ajoute uniquement des colonnes aux tables
existantes. Les taux de lignes sont élargis pour accepter des prix unitaires,
et les codes de mouvements suivent les 40 caractères du référentiel.
Les réglages par défaut conservent les calculs précédents.

La recette opt-in `PayrollVariablesCopiedDataTest` utilise exclusivement
`sirh_galaxam_recette_variables_20261001`, copie des données réelles. Elle vérifie
les trois interventions, les assiettes séparées, les versions et conditions,
les méthodes dérivées, la simulation, la persistance annulée transactionnellement,
les exports et les retenues partiellement imputées. Le bulletin de référence
Agent Y conserve brut 411 361,01 Ar, cotisations 8 227,22 Ar, IRSA 3 000 Ar,
net final 399 120 Ar. Ces tests ne constituent pas une certification réglementaire.

### Propriété des règles de saisie (sprint 2)

`ManualVariableAmountService` résout la version de rubrique à la date de la
période et applique `ManualPayrollRules` pour l'aperçu comme pour l'enregistrement.
L'interface saisit les paramètres et affiche le montant renvoyé par le serveur.
`PayrollAdjustmentPaymentService` décide si un acompte ou une avance peut être
rapproché d'un versement et valide date, mode et version dans la transaction.
La liste réutilise sa capacité `canConfirm`.

La validation STC réutilise une seule préparation dans sa transaction. Pour les
anciens STC sans bulletin d'identité figée, le reçu signale l'absence d'identité
historique et l'interface interdit son impression ; les données RH actuelles ne
doivent jamais être présentées comme celles du jour de validation.

### Aperçu des cycles de validité des rubriques

`PayrollRubriqueCycleService` calcule côté serveur les cycles complets couverts
par les dates d'effet d'une rubrique. `RubriqueCyclePreview` ne fait qu'afficher
cette réponse ; le navigateur n'embarque plus sa propre interprétation du cycle
26–25.

### Documents et exports (sprint 3)

`PayrollDocumentCatalogService` expose les documents mensuels, trimestriels et
annuels avec disponibilité et motif. Pour le mensuel, il lit la capacité
`GENERATE_OUTPUT` du workflow ; pour le trimestre, il utilise le chargement de
`QuarterlySocialDataService` ; pour l'année, il utilise la sélection et le
contrôle des bulletins d'`AnnualPayrollExportService`. L'écran consomme ce
catalogue pour afficher les actions. Les générateurs restent propriétaires des
formats et du contenu réglementaire.

À partir de la migration `26-10-16-sp71-paie-identite-employeur-bulletin.sql`,
`PayrollEmployerSnapshotService` fige la filiale, la raison sociale, le NIF et
les coordonnées dans la transaction de validation du bulletin. Les bulletins,
grands livres et modèles trimestriels réutilisent cet instantané. Les bulletins
validés avant cette migration n'ont pas d'instantané fiable : ils conservent la
résolution par contrat daté, qui peut refléter une correction ultérieure de la
fiche filiale. Aucun ancien instantané n'est inventé ou rempli automatiquement.
Le bulletin STC capture aussi cette identité lors de sa validation ; son reçu
ancien sans identité employeur figée reste consultable, mais non imprimable.
