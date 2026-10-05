# Cartographie backend Paie — 04/10/2026

Périmètre : les fichiers Java de `backend/src/main/java/mg/galaxam/sirh/paie/` et `paieconfig/`. Les deux `README.md` sont des cartes du domaine, pas du code exécutable. Inventaire statique : « candidat » signifie à caractériser et tester avant déplacement ; aucune fusion n'est décidée sur le seul nombre de fichiers.

## `paie/api/` — transport HTTP

| Fichier | Rôle | Regroupement possible |
|---|---|---|
| `PaieController.java` | Calcul de bulletins/campagnes, validation et consultation Paie. | Garder comme API de campagne. |
| `PaieAjustementController.java` | API des mouvements, avances, acomptes, prêts et décisions DAF. | Candidat à découper **par parcours**, pas à fusionner avec le calcul. |
| `PaieRubriqueController.java` | Catalogue et versionnement des rubriques configurables. | Garder avec son référentiel. |
| `StcController.java` | Préparation, simulation, validation et reçu de STC. | Garder distinct : date métier du départ. |

## `paie/api/dto/` — contrats HTTP

| Fichier | Rôle | Regroupement possible |
|---|---|---|
| `ActionPaiePeriodeRequest.java` | Demande d'action de campagne avec motif. | Candidat à regrouper avec les commandes de période après audit JSON. |
| `CalculEmployeRequest.java` | Requête de calcul d'un salarié. | Garder comme contrat HTTP. |
| `CalculPeriodeRequest.java` | Requête de calcul d'une campagne et simulation. | Garder comme contrat HTTP. |
| `ConfirmationVersementRequest.java` | Confirmation d'un versement. | Garder près du parcours d'avance. |
| `PaieAjustementRequest.java` | Saisie d'un mouvement/ajustement. | Garder près de l'API DAF. |
| `PaieRubriqueRequest.java` | Écriture d'une version de rubrique. | Garder avec le référentiel. |
| `PayrollCampaignStep.java` | Étape et accès du parcours de campagne. | Candidat au regroupement des petites vues de workflow. |
| `PayrollOutputView.java` | Vue de sortie du calcul/campagne. | Garder si contrat consommé ; auditer usages. |
| `PayrollPeriodContextView.java` | Vue UI de la campagne et de ses capacités. | **Pas** un contexte de calcul. Garder séparée de ce dernier. |
| `PayrollPeriodSummary.java` | Résumé de campagne. | Candidat au regroupement des petites vues de workflow. |
| `PayrollPhaseStatus.java` | État d'une phase du workflow. | Candidat au regroupement des petites vues de workflow. |
| `PayrollPopulationMember.java` | Membre de la population de calcul. | Garder avec la résolution de population. |
| `PayrollWorkflowStatuses.java` | États exposés du workflow. | Candidat au regroupement des petites vues de workflow. |
| `stc/StcCalculRequest.java` | Entrée de simulation/validation STC. | Garder dans le contrat STC. |
| `stc/StcCalculResultDTO.java` | Résultat détaillé et lignes STC. | Garder dans le contrat STC. |
| `stc/StcPreparationDTO.java` | Données préparatoires au départ. | Garder dans le contrat STC. |
| `stc/StcRecuDTO.java` | Reçu final STC. | Garder dans le contrat STC. |

## `paie/application/` — assemblage et cas d'usage

| Fichier | Rôle | Regroupement possible |
|---|---|---|
| `PayrollService.java` | Prépare les entrées, calcule/persiste bulletins, orchestre la campagne et les effets post-calcul. | Extraire progressivement les **lectures de contexte** ; ne pas fusionner d'autres services dans ce fichier. |
| `PayrollLegalContextResolver.java` | Résout la configuration légale, cotisations, santé, IRSA, majorations et paramètres au 25 ou par jour pour les majorations. | Noyau du futur contexte de calcul, à renforcer plutôt qu'à dupliquer. |
| `PayrollPopulationResolver.java` | Détermine salariés inclus, exclus et raisons selon la période. | Garder ; alimenter le contexte de campagne. |
| `PayrollConfigurationGuard.java` | Erreurs explicites et contrôles de paramètres obligatoires. | Petit fichier partagé, justifié ; fusion possible avec un résolveur commun seulement si le contrat d'erreur reste unique. |
| `ManualRubriqueResolver.java` | Sélection stricte d'une version de rubrique couvrant la campagne. | Candidat à rejoindre le traitement des variables manuelles si une seule résolution subsiste. |
| `StcService.java` | Prépare, valorise et valide le solde de tout compte. | Garder distinct ; réutiliser les résolveurs datés à la date de départ. |

## `paie/application/workflow/` — décisions et transitions

| Fichier | Rôle | Regroupement possible |
|---|---|---|
| `PayrollPeriodWorkflowService.java` | Autorisations, prérequis et transitions de la campagne/Pointage/Paie. | Garder comme propriétaire de la transaction d'état. |
| `PayrollAdjustmentWorkflowService.java` | Approbation/rejet des mouvements manuels. | Garder distinct des campagnes. |
| `PayrollWorkflowStatusResolver.java` | Convertit états et nombres de bulletins en statuts affichables. | Candidat à fusionner dans le workflow de période si aucun autre consommateur. |
| `PayrollAction.java` | Codes d'actions Paie. | Candidat au regroupement des types du workflow. |
| `PointagePeriodAction.java` | Codes d'actions Pointage dans le même workflow. | Candidat au regroupement des types du workflow, en gardant les noms JSON. |
| `PayrollPeriodCommand.java` | Commandes de campagne. | Candidat au regroupement des types du workflow. |
| `PayrollPeriodCommandRequest.java` | Requête de commande versionnée/motivée. | Garder contrat de transport ; éviter doublon avec `ActionPaiePeriodeRequest`. |
| `PayrollPeriodUserContext.java` | Rôles et permissions de l'acteur. | Garder séparé du contexte de **calcul**. |
| `PayrollAdjustmentCommand.java` | Commandes d'ajustement. | Candidat à types internes du workflow d'ajustement. |
| `PayrollAdjustmentCommandRequest.java` | Requête motivée d'ajustement. | Garder si contrat HTTP ; vérifier doublon avec `PaieAjustementRequest`. |

## `paie/domain/dto/` — données de calcul et sortie

| Fichier | Rôle | Regroupement possible |
|---|---|---|
| `PayrollCalculationInput.java` | Grande entrée mutable du moteur : identité, régime, salaire, pointage, fiscal/social, indemnités, mouvements. | Garder comme projection du moteur, **pas** comme source de vérité des dates/configurations. À rendre validée après assemblage. |
| `PayrollAttendanceInput.java` | Snapshot Pointage qualifié, minutes et buckets consommés par la Paie. | Garder : frontière explicite avec Pointage. |
| `BulletinPaieComplet.java` | Bulletin, lignes et explications calculées. | Garder comme sortie de calcul. |
| `PayrollBatchResult.java` | Résultat de calcul collectif et salariés bloqués. | Garder comme sortie de campagne. |
| `export/DntsRowDto.java` | Ligne de déclaration DNTS. | Garder si format externe distinct. |
| `export/SafiRowDto.java` | Ligne d'export SAFI. | Garder si format externe distinct. |
| `export/VirementRowDto.java` | Ligne de virement bancaire. | Garder si format externe distinct. |

## `paie/domain/engine/` — règles pures et résolutions

| Fichier | Rôle | Regroupement possible |
|---|---|---|
| `PayrollExecutionEngine.java` | Ordonnance les phases du calcul et produit le bulletin. | Garder comme moteur central. |
| `PayrollCalculatorRegistry.java` | Décrit les calculateurs/capacités du catalogue. | Garder avec les rubriques ; vérifier qu'il ne duplique pas l'ordre effectif du moteur. |
| `OvertimePayCalculator.java` | Valorise les buckets HS, nuit, dimanche, férié. | Garder : règle temporelle propre. |
| `PayrollOvertimeSettlement.java` | Répartit la régularisation HS sur les buckets Pointage. | Garder avec la valorisation du temps, fusion seulement si tests de conservation. |
| `BucketValuationResult.java` | Résultat détaillé de la valorisation des buckets. | Candidat à type interne du calculateur HS si un seul consommateur. |
| `SpecialPremiumCollisionPolicy.java` | Politique de cumul des majorations. | Candidat à type interne du calculateur HS si sérialisation inchangée. |
| `TemporalRateResolver.java` | Contrat de taux de majoration dépendant du jour civil. | Garder tant que moteur et résolveur légal le partagent. |
| `SocialContributionCalculator.java` | Calcule cotisations salariales et patronales. | Garder distinct de la résolution SQL. |
| `IrsaCalculator.java` | Calcule l'impôt selon tranches et déductions. | Garder distinct de la résolution SQL. |
| `IrsaBracketResolver.java` | Charge les tranches applicables. | Candidat à rejoindre le contexte légal si accès uniquement au calcul de paie. |
| `IsiCalculator.java` | Calcule l'ISI. | Candidat à intégrer avec la fiscalité si un seul consommateur. |
| `TaxConfigurationResolver.java` | Résout le taux ISI daté. | Candidat à rejoindre le contexte fiscal. |
| `MoneyRoundingPolicy.java` | Arrondis monétaires partagés. | Garder si plusieurs calculateurs l'utilisent. |
| `ManualAmountCalculator.java` | Montant des rubriques saisies : fixe, quantité×taux, pourcentage du salaire. | Garder avec la règle des rubriques manuelles. |
| `ManualGainContributions.java` | Assiettes du gain manuel (brut, santé, FMFP). | Candidat à type interne du traitement manuel. |
| `ManualRuleApplicability.java` | Plage et condition d'application d'une rubrique. | Candidat à regrouper avec `ManualAmountCalculator`/`ManualRubriqueResolver`. |

## `paie/application/export/` — documents et déclarations

| Fichier | Rôle | Regroupement possible |
|---|---|---|
| `PaieExportService.java` | Façade de routes d'export et délégation. | Candidat à suppression après migration des appelants vers des exports typés. |
| `BulletinDocumentService.java` | Contexte et rendus Excel/PDF d'un bulletin. | Déjà regroupé par document ; garder. |
| `AnnualPayrollExportService.java` | Export annuel des bulletins. | Garder format et période distincts. |
| `GrandLivrePaieExportService.java` | Grand livre de paie par campagne. | Garder finalité comptable distincte. |
| `EtatSocialMensuelExportService.java` | État social mensuel. | Garder finalité sociale distincte. |
| `DntsExportService.java` | Données et CSV DNTS mensuel/trimestriel. | Garder ; partager seulement l'agrégat trimestriel. |
| `EdiIsiExportService.java` | Fichier EDI/XML de l'ISI. | Garder format externe distinct. |
| `IrsaExcelExportService.java` | Classeur fiscal IRSA. | Garder format externe distinct. |
| `SafiExportService.java` | Données et CSV SAFI. | Garder source de données. |
| `SafiOdsExportService.java` | Rendu ODS des mêmes données SAFI. | Candidat à regroupement dans un service SAFI avec méthodes unitaires, si API et tests préservés. |
| `VirementBancaireExportService.java` | Données et CSV des virements. | Garder sortie bancaire séparée. |
| `QuarterlySocialDataService.java` | Agrège mois et salariés pour les déclarations trimestrielles. | Garder comme source commune DNTS/classeur. |
| `QuarterlySocialWorkbookService.java` | Rendu des classeurs sociaux trimestriels. | Candidat à regrouper avec les données seulement si cela n'accouple pas agrégation et format. |
| `PayrollExportCompanyResolver.java` | Identité de l'entreprise imprimée dans plusieurs exports. | Petit fichier partagé justifié ; ne pas copier dans chaque export. |

## `paie/persistence/` — enregistrements et historique

| Fichier | Rôle | Regroupement possible |
|---|---|---|
| `BulletinPaie.java` | Bulletin persisté et ses lectures. | Garder entité de table. |
| `BulletinPaieLigne.java` | Lignes de bulletin persistées. | Garder entité de table. |
| `PaieAvanceAcompte.java` | Mouvements d'avance, acompte et autres variables/retentions. | Garder selon table ; candidat à séparation conceptuelle, pas à fusion. |
| `PaiePret.java` | Contrat de prêt et solde. | Garder entité de table. |
| `PaiePretEcheance.java` | Échéance et prélèvement du prêt. | Garder entité de table. |
| `PaieRubrique.java` | Version de rubrique configurable. | Garder entité de table. |
| `PaieHonorairePrestation.java` | Prestations/honoraires hors salariat. | Garder tant que parcours actif confirmé. |
| `PaieStc.java` | STC persisté et historique. | Garder entité de table. |
| `PayrollCanonicalConcept.java` | Codes canoniques et libellés de lignes de paie. | Garder si mapping partagé calcul/export ; vérifier anciennes étiquettes Excel. |

## `paieconfig/api/` — paramétrage HTTP

| Fichier | Rôle | Regroupement possible |
|---|---|---|
| `PaieConfigLegaleController.java` | Version de configuration légale. | Garder avec la configuration datée. |
| `IrsaController.java` | Barème IRSA et revalorisation. | Garder avec fiscalité. |
| `CotisationController.java` | Référentiel social et rattachement au site. | Garder avec social. |
| `IndemniteController.java` | Indemnités et tarifs par site. | Garder avec indemnités. |
| `CalendrierFerieController.java` | Jours fériés, chômés et affectation au site. | Garder avec calendrier partagé Pointage. |
| `TauxMajorationController.java` | Taux HS/nuit/dimanche/férié. | Garder avec rémunération du temps. |
| `ParametreGlobalController.java` | Paramètres métier versionnés. | Garder comme administration transversale. |
| `RefTypeMouvementPaieController.java` | Catalogue des types de mouvement/variable. | Garder avec variables de paie. |

## `paieconfig/api/dto/` — demandes de paramétrage

| Fichier | Rôle | Regroupement possible |
|---|---|---|
| `PaieConfigLegaleRequest.java` | Saisie d'une version légale. | Garder. |
| `IrsaRequest.java` | Tranche IRSA. | Candidat à type interne de la demande de revalorisation si usages indépendants absents. |
| `IrsaRevalorisationRequest.java` | Nouvelle date et ensemble de tranches IRSA. | Garder. |
| `CotisationRequest.java` | Taux et dates d'une cotisation. | Garder. |
| `SiteCotisationRequest.java` | Liaison site/organisme social. | Garder. |
| `IndemniteRequest.java` | Définition d'une indemnité. | Garder. |
| `SiteIndemniteRequest.java` | Tarif/attribution d'indemnité par site. | Garder. |
| `CalendrierFerieRequest.java` | Définition d'un férié. | Garder. |
| `SiteCalendrierFerieRequest.java` | Liaison férié/site. | Garder. |
| `PeriodeChomeeRequest.java` | Intervalle chômé. | Garder. |
| `TauxMajorationRequest.java` | Taux de majoration par version légale. | Garder. |
| `ParametreGlobalRequest.java` | Valeur/statut d'un paramètre. | Garder. |
| `RefTypeMouvementPaieRequest.java` | Type de mouvement. | Garder. |

## `paieconfig/persistence/` — référentiels datés

| Fichier | Rôle | Regroupement possible |
|---|---|---|
| `PaieConfigLegale.java` | Version légale, bornes et résolution à date. | Garder entité de table. |
| `BaremeIrsa.java` | Tranches/version fiscale. | Garder entité de table. |
| `RefTypeCotisation.java` | Taux CNaPS, santé, FMFP et autres types. | Garder entité de table. |
| `SiteCotisation.java` | Organisme/taux rattaché au site. | Garder entité de table. |
| `RefTauxMajoration.java` | Majorations datées. | Garder entité de table. |
| `RefIndemnite.java` | Catalogue d'indemnités. | Garder entité de table. |
| `SiteIndemniteConfig.java` | Tarif/validité d'une indemnité sur site. | Garder entité de table. |
| `RefCalendrierFerie.java` | Calendrier des fériés. | Garder entité de table. |
| `SiteCalendrierFerie.java` | Applicabilité d'un férié sur site. | Garder entité de table. |
| `RefPeriodeChomee.java` | Périodes chômées. | Garder entité de table. |
| `ParametreGlobal.java` | Paramètres métiers activables et datés. | Garder entité de table. |
| `RefTypeMouvementPaie.java` | Catalogue de mouvements/variables. | Garder entité de table. |

## Regroupements réellement prometteurs

1. **Contexte d'un calcul Paie** : ne pas créer un second `PayrollCalculationInput`. Introduire une couche d'assemblage qui construit et valide cet input à partir d'un contexte de campagne immuable (bornes, date de clôture, règles légales à cette date, taux journaliers indexés par date) et d'un contexte salarié (couverture contrat/affectation, régime, site, salaire, snapshot Pointage, lots Congés, mouvements). Une seule transaction et des IDs/versions de provenance. Réutiliser `EmploymentContextResolver` et `PayrollLegalContextResolver` ; ne pas charger aveuglément tous les référentiels ni supposer que le 25 résout aussi les règles journalières.
2. **Variables manuelles** : `ManualRubriqueResolver`, `ManualAmountCalculator`, `ManualRuleApplicability`, `ManualGainContributions` forment un sous-domaine cohérent. Candidat fort à un module/fichier procédural avec petites méthodes et types internes. Vérifier les consommateurs et sérialisations avant de déplacer.
3. **Fiscal/social** : les calculateurs restent purs ; leurs *résolutions* SQL (`IrsaBracketResolver`, `TaxConfigurationResolver` et parties de `PayrollLegalContextResolver`) pourraient être réunies sous le contexte légal. Unifier la résolution, pas le calcul IRSA/ISI/CNaPS.
4. **Exports** : `SafiExportService` + `SafiOdsExportService` et l'éventuelle façade `PaieExportService` sont à évaluer ensemble. Les formats DNTS, EDI, IRSA, virement et grand livre restent séparés pour limiter les dépendances et préserver leurs contrats.
5. **Petits types de workflow** : regrouper les `enum`/records qui n'ont qu'un propriétaire et ne sont pas des contrats JSON autonomes. Vérifier noms sérialisés et API avant fusion.

## Contre-audit du contexte : écarts à tester avant implémentation

- `PayrollService.preparerInput` est décrit comme « idempotent » et « sans effets de bord », mais sa requête de mouvements prend `FOR UPDATE` et peut exécuter un `UPDATE paie_avance_acompte` hors simulation pour une rubrique dérivée. Séparer clairement préparation/validation et écriture transactionnelle.
- Le salaire, le site et le régime du bulletin sont projetés à `employmentCoverage.lastDay()`. Ce choix peut être correct pour une ligne mensuelle, mais il ne décrit pas les éléments journaliers d'une campagne 26→25 qui change au 01/01. Le contexte doit garder la couverture datée, pas seulement la dernière affectation.
- La santé est actuellement résolue par `code_site` **ou** ressemblance sur le nom du site. La clé `id_site` datée devrait être la source ; vérifier les données historiques et bloquer deux organismes actifs au lieu de prendre un résultat approximatif.
- `PayrollCalculationInput` se dit « immuable » dans son commentaire mais expose des setters, des listes modifiables et des valeurs par défaut (`PERMANENT`, `AGENT`, `OSTIE`, etc.). Ces valeurs ne doivent pas masquer une configuration manquante dans le nouveau contexte.
- Le paramètre `MONTANT_PRIME_ASSIDUITE` absent peut aujourd'hui aboutir à zéro dans `PayrollLegalContextResolver`. Vérifier la règle d'activation : absence et désactivation ne sont pas équivalentes.
- Le contexte légal principal est pris à la fin de campagne, alors que les majorations qualifiées prennent déjà leur date civile. Documenter cette matrice de dates et tester le passage du 31/12 au 01/01, changement de site/organisme, salaire et régime.

## Ordre de travail proposé

1. Capturer des bulletins et exports de référence (2025, 26/12→25/01, 26/01→25/02), avec versions de snapshots et absence de recalcul historique ; BDD uniquement `sirh_galaxam_test`, `current_database()` vérifié avant écriture.
2. Corriger les écarts temporels/valeurs par défaut démontrés par tests, en conservant les règles métier valides.
3. Extraire l'assemblage de contexte hors `PayrollService`, avec date métier par domaine et provenance explicite ; laisser le moteur pur et la persistance séparés.
4. Regrouper variables manuelles et petits types de workflow seulement après tests de non-régression et inventaire des consommateurs.
5. Simplifier les exports/facades après preuve des usages ; contrôler compilation, tests Paie/STC/Pointage/Congés et sorties de fichiers.

Question métier préalable à une ventilation du salaire/site : lorsqu'une affectation ou un salaire change le 01/01 dans la paie 26/12→25/01, faut-il proratiser la rémunération de base selon les jours contractuels, ou appliquer le salaire en vigueur au 25 à la ligne mensuelle tout en gardant les éléments journaliers à leur date ? Le code actuel fait surtout le second choix ; ne pas en changer par simple refactoring.
