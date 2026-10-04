# Domaine Paie

Les campagnes délèguent la couverture temporelle contrat/affectation à
`contrat.application.EmploymentContextResolver`. La paie conserve ses résolutions
distinctes pour fiscalité, cotisations et paramètres légaux, dont les dates
métier ne sont pas interchangeables avec celles du contrat.

Ce dossier regroupe le moteur de paie, la préparation et le calcul des bulletins, les campagnes de paie, le solde de tout compte (STC) et les exports fiscaux/sociaux.

- `api/` : contrôleurs REST (`PaieController`, `PaieAjustementController`, `PaieRubriqueController`, `StcController`)
- `api/dto/` : contrats DTO (`PayrollCampaignStep`, `CalculPeriodeRequest`, `stc/*`, etc.)
- `application/` : services applicatifs (`PayrollService`, `StcService`, `workflow/`, `export/`)
- `domain/` : moteurs de calculs purs et invariants fiscaux (`engine/`, `dto/`)
- `persistence/` : entités de paie et tables historiques (`BulletinPaie`, `BulletinPaieLigne`, `PaiePret`, `PaieAvanceAcompte`, `PaieStc`, etc.)

Toutes les routes d'origine restent préservées sous `/paie/*`, `/stc/*`, `/api/paie/*`.

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
