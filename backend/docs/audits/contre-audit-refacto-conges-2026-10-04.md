# Contre-audit du refactoring Congés — 4 octobre 2026

## Résultat contradictoire

Le parcours annuel, les acquisitions à la clôture, les lots FIFO, le cutover
agrégé et la correction DAF des mois non consommés sont présents. Le changement
d'année du cycle `26 → 25` résout la période et les règles sur la date réelle de
clôture. Le contrôle précédent était toutefois trop optimiste sur deux points :
`CONGE_DECOMPTE_FERIES` était imposé actif/non désactivable en BDD, et le contexte
contrat/affectation de paie existait dans un resolver distinct du contexte RH.
Ces deux écarts sont corrigés dans ce lot.

## Responsabilités retenues

| Classe / fichier | Responsabilité unique retenue | État contre-audité |
|---|---|---|
| `CongeService` | Workflow des demandes et intégrations congés-pointage | Restait un alias de simulation calendaire inutilisé ; supprimé. Les responsabilités demande et régularisation pointage restent ensemble car elles partagent le workflow de validation et ses transactions. |
| `CongeCalculateurEngine` | Calculs purs de durée, seuils et simulations sans accès SQL | La méthode alias de valorisation non datée a été supprimée ; les résolutions métier réelles restent dans le service d'application. |
| `CongeDecompteCalendaireResolver` | Décompte daté par jour, site réel et calendrier férié | Une désactivation explicite renvoie maintenant `CONFIGURATION_DISABLED`, distinct de l'absence. |
| `CongeTauxApplicableService` | Cycle civil de référence, TCONGE, acquisitions de référence et snapshot de cutover | Classe encore longue (~1 350 lignes), mais les méthodes forment le cycle de vie cohérent de référence/TCONGE. Nouvelle extraction seulement si un prochain changement permet de séparer réellement un sous-domaine, sans façade en doublon. |
| `CongeValorisationMensuelleService` | Rectification auditable d'un mois de lot intact | Service dédié, réutilise le journal et le grand-livre existants. |
| `CongeLotValorisationService` | FIFO, reliquat et valorisation historique par lot | Service dédié, source de valeur historique consommée par la paie. |
| Services d'export Congés | Format et export Excel | Séparés des calculs et des écritures métier. |
| `EmploymentContextResolver` | Contrat, affectation et couverture temporelle commune | Désormais utilisé directement par `PayrollService` et `PayrollPopulationResolver`; les chevauchements sont bloqués explicitement. L'ancien resolver paie redondant a été supprimé. |

## Politique des jours fériés

La règle est maintenant débrayable, mais uniquement par version datée future.
Créer une version `INCLUS` ou `EXCLUS` inactive désactive le calcul à sa date
d'effet et bloque les demandes/valorisations concernées avec une erreur explicite.
Une valeur précédente n'est jamais reprise. La bascule immédiate est interdite
pour préserver la reproductibilité historique ; une version future active peut
changer la règle sans modifier les congés déjà validés.

Migration incrémentale : `26-10-04-sp56-conge-decompte-feries-desactivable.sql`.
Elle met d'abord à jour le marqueur des lignes historiques avant de recréer la
contrainte, point détecté pendant l'essai sur `sirh_galaxam_test`.

## Limite fonctionnelle du cutover à ne pas masquer

Le formulaire de reprise importe un stock global de jours et sa valeur, le TCONGE
historique, puis les cumuls de l'année civile. Il ne reconstitue pas une ligne de
lot par mois pour tout l'historique Excel antérieur. Le FIFO post-cutover est
conservé pour les lots enregistrés par le système, mais l'ancien stock DAF reste
un lot agrégé. Le guide le dit maintenant clairement. Cette option est cohérente
si l'arrêté DAF fournit seulement le reliquat global ; si la DAF doit conserver
la valeur et la consommation FIFO de chaque ancien mois, l'import détaillé reste
un besoin distinct à spécifier avant de déclarer la reprise « complète ».

## Limites métier / contrôles restants

- Le contrôle de continuité de service des demandes Congés utilise désormais
  `CongeContinuiteService` pour segmenter le contrat et l'affectation via
  `EmploymentContextResolver` sur les dates réelles. Simulation et validation
  interrogent la même analyse ; elles ne choisissent plus la première
  affectation qui chevauche la demande. Le frontend présente les segments et
  les seules dates où collègue, poste et site coïncident. Aucun nouveau stockage
  n'est nécessaire. La synthèse de la fiche Paie garde sa requête locale : ce
  parcours d'affichage Paie est hors du présent nettoyage du module Congés.
- L'endpoint de solde Congés lisait le dernier salaire sans date et masquait
  toute erreur. Il réutilise désormais la situation canonique, elle-même
  raccordée à `EmploymentContextResolver` à la date demandée. Un test vérifie
  le changement de salaire au 01/01 dans cette situation.

- L'inclusion des fériés est la règle Galaxam actuelle, mais son écart avec
  l'article 127 du Code du travail malgache doit rester soumis à validation
  juridique. Cette implémentation n'en déduit pas la conformité légale.
- Une régularisation de lot déjà consommé est traçable via le type paie existant,
  mais le contrôle automatique du minimum légal et le chemin STC ne sont pas
  couverts par cette correction ciblée.
- Le cutover exige que le DAF choisisse explicitement le taux historique et les
  cumuls ; le système ne les déduit pas des feuilles Excel ni du salaire actuel.
- Les cotisations, la fiscalité et les paramètres légaux gardent chacun leur
  resolver de domaine : leurs dates métier diffèrent du contexte contrat/site.
  L'unification porte seulement sur contrat/affectation/salaire/site à date.

## Vérifications

La couverture paie est désormais résolue par le même service contrat qui expose
le contexte salarié à une date. Tests d'intégration ciblés : cycle civil,
`26/12/2025 → 25/01/2026`, cutover idempotent/refus de doublon, politique férié
active/inactive/absente/chevauchante, et couverture contrat sur la campagne.
Tous les tests BDD doivent d'abord vérifier `current_database()` et n'utiliser
que `sirh_galaxam_test`.

## Vérification contradictoire du parcours DAF

| Étape | Constat | État |
|---|---|---|
| Nouvelle embauche | La première clôture de paie crée la référence de l'année de la date d'acquisition ; pas de saisie DAF de cutover. | Conforme |
| Reprise d'historique | La DAF saisit un stock restant valorisé, le dernier taux applicable, les cumuls acquis/valorisés de l'année civile et la référence identifiable de l'arrêté source. Le stock antérieur reste un lot agrégé. | Conforme au choix métier exprimé, sous réserve de disposer de l'arrêté DAF fiable |
| Passage 2025 → 2026 | L'acquisition du 25/01/2026 appartient à la référence 2026 ; la clôture de décembre 2025 fige le taux moyen 2025, sans l'activer rétroactivement. | Conforme sur les tests ciblés |
| Ancien cycle encore ouvert | Le contrôle DAF affiche désormais une alerte datée et demande de vérifier la clôture de décembre et les acquisitions. La création de 2026 ne masque plus ce problème dans l'écran. | Alerte ajoutée ; correction des données toujours explicite |
| Export grand récapitulatif | Deux références chevauchantes à la date du rapport étaient départagées par `LIMIT 1`. L'export bloque maintenant avec `AMBIGUOUS_CONFIGURATION`. | Corrigé |
| Sécurité des tests BDD | Le nettoyage commun vérifiait le catalogue JDBC. Il exécute maintenant `SELECT current_database()` avant toute suppression. | Corrigé |

Le complément de nettoyage impose désormais une référence DAF distincte du
commentaire libre avant la reprise. Le backend la valide et l'inscrit dans les
traces existantes du taux initial et du stock repris, sans nouvelle table.
L'export grand récapitulatif utilise `EmploymentContextResolver` à la date de
fin du rapport pour le contrat, la filiale, le site et le poste ; il ne choisit
plus localement la dernière affectation par `LIMIT 1`.

Le parcours applicatif ciblé est nettoyé. Les tests ciblés ne valent toutefois
pas validation des arrêtés DAF réels ni de toute la suite Congés. La référence
identifie le document source mais ne vérifie pas son contenu et ne stocke pas
le fichier lui-même.
