# Domaine PaieConfig (Configuration légale et barèmes RH)

Ce dossier regroupe l'ensemble des règles de configuration légale, des barèmes fiscaux et sociaux, et des paramètres RH de paie.

- `api/` : contrôleurs REST pour la configuration :
  - `PaieConfigLegaleController` (`/paie/config-legale`)
  - `IrsaController` (`/referentiel/bareme-irsa`)
  - `CotisationController` (`/referentiel/cotisations`)
  - `IndemniteController` (`/referentiel/indemnites`)
  - `CalendrierFerieController` (`/referentiel/calendrier-ferie`)
  - `TauxMajorationController` (`/referentiel/taux-majoration`)
  - `ParametreGlobalController` (`/referentiel/parametres-globaux`)
  - `RefTypeMouvementPaieController` (`/referentiel/types-mouvement-paie`)
- `api/dto/` : contrats DTO pour chaque écran de configuration
- `persistence/` : modèles Active Record et tables de configuration (`BaremeIrsa`, `PaieConfigLegale`, `ParametreGlobal`, `RefTypeCotisation`, `SiteCotisation`, `RefIndemnite`, `SiteIndemniteConfig`, `RefCalendrierFerie`, `SiteCalendrierFerie`, `RefPeriodeChomee`, `RefTauxMajoration`, `RefTypeMouvementPaie`)
- `application/` : cas d'usage transactionnels
- `domain/` : invariants métier

Toutes les routes d'origine restent préservées sans régression côté frontend.
# Résolution datée des cotisations

`application.SocialContributionContextResolver` est la source de résolution pour les
taux CNaPS, FMFP et Santé par site. Il prend une date métier explicite : fin de campagne
pour le bulletin mensuel, date de départ pour le STC. Une version manquante ou
chevauchante bloque le calcul. Le résolveur ne calcule ni assiette ni montant ; ces
opérations appartiennent aux moteurs des domaines consommateurs.

# Propriété des règles de paie

`paieconfig` possède les barèmes, versions et dates d'effet. Les montants et
assiettes sont calculés dans `paie/domain/engine`, et les opérations de campagne
dans `paie/application`. L'interface lit les capacités du workflow et le
catalogue documentaire ; elle ne décide pas qu'une configuration ou une période
autorise un calcul ou un export. Pour une nouvelle règle, modifier son propriétaire
backend puis étendre le contrat API et son test, sans copier la condition dans
un autre contrôleur ou dans le frontend.
