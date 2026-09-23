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
