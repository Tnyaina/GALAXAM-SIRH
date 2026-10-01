# Interface de configuration de la paie

## Accès et organisation

Administration → Paramétrage des modules → Paie : « Rubriques et calcul » et « Types de variables ». Les liens existants conservent leurs identifiants d’onglet. Les deux catalogues partagent un composant chargé à la demande ; ils remplacent les anciens écrans séparés.

Le catalogue comporte recherche temporisée, filtres, tri et pagination exécutés par le backend. Le contexte et la sélection sont conservés dans l’URL. La fiche est consultable avant d’ouvrir un formulaire.

## Responsabilités

`GET /referentiel/types-mouvement-paie/configuration` fournit les listes paginées, options, valeurs initiales, descriptions de traitement, versions et capacités de modification. Le front ne calcule aucun montant de paie. Son aperçu décrit uniquement le traitement choisi.

Les rubriques protégées restent consultables et proposent, lorsque disponible, un lien vers leur paramétrage spécialisé. L’historique et les mouvements existants verrouillent les associations ou traitements concernés. La mutation backend conserve le contrôle final des droits et de la validité. Une nouvelle version est proposée uniquement lorsque le backend l’autorise.

Les rubriques requièrent le rôle administrateur pour être modifiées ; les types requièrent PAIE_WRITE ou le rôle administrateur. Les formulaires conservent la saisie en cas d’échec, séparent les erreurs de champ des erreurs de soumission et bloquent les doubles enregistrements.

## Vérifications

Tests du catalogue sur la base de recette copiée : pagination, recherche, maintien de la sélection, verrous et paramètres invalides. Tests frontend : pagination et URL, erreurs inline, conservation de saisie, double soumission et annulation des requêtes. Vérification visuelle sur bureau et mobile.

Ce chantier ne crée aucune table et ne modifie pas les formules du moteur. Le paramétrage fiscal et social spécialisé existant reste accessible par ses onglets. Il n’introduit ni éditeur de formules libres ni simulation monétaire de configuration.

## Organisation des paramètres

Les quatre espaces Organisation, Référentiels RH, Règles métier et Accès & système partagent une navigation. Les sections sont verticales sur bureau et proposées par un sélecteur sur mobile. La paie regroupe ses écrans sous Rubriques & variables, Fiscalité & cotisations, Primes & indemnités, et Paiement. Le calendrier dispose d’un accès partagé ; l’ancien lien paie/calendrier demeure compatible. Les listes de référence de carrière sont consultées et chargées séparément. Les liens historiques et identifiants d’onglet sont conservés.

Pour cette réorganisation, conformément à la demande utilisateur, la vérification se limite au lint, à la compilation et à la revue du diff, sans tests de pages ni parcours navigateur.

### Simplification de la navigation

Les accès aux quatre espaces restent dans la barre latérale principale et ne sont plus répétés dans les pages. Organisation, Référentiels et Accès utilisent les onglets partagés ; la paie conserve une navigation verticale par sujet. Les filtres et tableaux des utilisateurs, menus, fonctions, filiales et sites partagent une surface, sans cadres imbriqués. Les sections courtes utilisent un sélecteur sur mobile.

### Paramètres globaux

Vue unique regroupée par thème avec cinq colonnes : paramètre, valeur, application, validité et actions. Les filtres secondaires sont dépliables, la description et les autres versions sont consultables dans une fiche. Le service transmet la catégorie à l’API, qui filtre le domaine ; les compteurs portent sur les versions de ce domaine. Le backend expose le statut temporel et les possibilités de bascule/versionnement. Aucun changement de schéma. Les textes génériques sur les cascades et les garanties temporelles ont été retirés.
