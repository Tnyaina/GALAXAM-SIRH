# Plan de refactoring ciblé du module Congés

## Règles retenues

- Référence annuelle civile `01/01 → 31/12`, créée à la première acquisition issue de la clôture de paie ; campagne `26/12/2025 → 25/01/2026` rattachée à 2026.
- Acquisition de 2,5 jours par mois de service, proratisée selon les règles en place et datée à la clôture. Chaque lot garde sa valeur d'origine ; le stock est consommé FIFO.
- La prise se décompte en jours calendaires, week-ends et jours fériés inclus selon la politique Galaxam actuelle. Une règle globale **datée, obligatoire et sans valeur implicite** permet d'inclure ou exclure les fériés à compter d'une date future. Une version inactive bloque les calculs concernés (`CONFIGURATION_DISABLED`) ; aucune valeur implicite ne s'applique. Un changement ne recalcule pas les demandes validées.
- Une erreur de valeur sur un lot intact et une référence ouverte est rectifiée dans le journal mensuel avec avant/après, auteur et motif. Si le lot a déjà été consommé ou la référence clôturée, l'historique reste figé : la DAF valide une régularisation monétaire explicite sur une paie future ou au STC après contrôle des paiements antérieurs. Un trop-perçu ne donne pas lieu à une retenue automatique.
- Le décompte des fériés inclus reste en conflit avec l'article 127 du Code du travail 2024 ; décision Galaxam conservée, avec arbitrage juridique à documenter.

## Parcours cible

| Étape | Déclencheur | Interface nécessaire | Écriture autorisée |
|---|---|---|---|
| Reprise historique | Entrée en gestion SIRH, une seule fois | Fiche salarié, formulaire DAF dédié | Stock, valeur d'ouverture, cumuls et taux de reprise ; rejet des chevauchements |
| Demande et validation | Salarié/RH | Demandes et calendrier existants | Réservation puis consommation FIFO avec valeur du lot |
| Acquisition | Validation/clôture de la paie | Résultat de paie ; pas de bouton de cycle | Référence annuelle idempotente, journal du mois, lot et valeur figée |
| Contrôle | Après clôture | « Contrôle des droits » central | Lecture du stock multiannuel, des mois et des écarts |
| Correction d'un lot intact | Écart justifié | Action DAF dans le mois concerné | Historique de correction + mise à jour cohérente du journal/lot |
| Régularisation d'un lot consommé | Écart justifié et validé | Dossier DAF relié à paie/STC | Écriture monétaire future traçable, jamais de réécriture du bulletin passé |

## Découpage du code retenu

| Fichier/service | Responsabilité à garder |
|---|---|
| `CongeService` | Workflow des demandes et intégrations de pointage ; contient encore quelques lectures et écritures de mouvement à extraire au fil des prochains changements |
| `CongeCalculateurEngine` | Calculs purs : durée, règles de solde et seuils, sans accès BDD |
| `CongeDecompteCalendaireResolver` | Politique de décompte par jour, référentiel férié du site et version de paramètre datée |
| `CongeTauxApplicableService` | Références annuelles, résolution TCONGE et reprise cutover atomique ; les chemins de reprise autonomes supprimés ne sont plus maintenus |
| `CongeValorisationMensuelleService` | Journal mensuel, correction d'un lot intact et historique DAF |
| `CongeLotValorisationService` | Solde multiannuel, FIFO, valorisation des lots et correction du reliquat cutover |
| `CongeAnnuelExcelExportService`, `CongeGrandRecapExcelExportService` | Exports spécialisés, indépendants des règles d'écriture |

`CongeService` et `CongeTauxApplicableService` restent volumineux. Le présent chantier extrait la règle journalière et retire des API obsolètes ; déplacer en masse toutes les méthodes publiques créerait un changement transversal sans bénéfice immédiat. L'export et la valorisation mensuelle ont déjà leurs services propres. La prochaine extraction utile est un service de grand-livre/mouvements, en conservant des méthodes délégataires le temps de migrer les appelants.

## Nettoyage et ordre de réalisation

1. **Navigation et garde-fous** : retirer les commandes de cycle du parcours normal ; réserver la reprise historique au rôle DAF, avec un libellé précis et sans bouton « initialiser le taux » sur la synthèse. Conserver l'export Excel et l'écran mensuel de contrôle. **Fait pour l'entrée de reprise.**
2. **Règle fériés** : introduire une version datée du paramètre global, vérifier son unicité et son existence pour chaque jour concerné ; propager la même règle dans estimation, validation, calcul paie et affichage. **Fait :** `CONGE_DECOMPTE_FERIES=INCLUS` au départ, `EXCLUS` disponible par version future ; absence, désactivation et chevauchement ont des erreurs distinctes. La règle se désactive en créant une version future inactive ; les bascules immédiates sont refusées pour préserver l'historique. Le décompte enregistré sur une demande validée reste figé.
3. **Reprise unique** : comparer les trois API actuelles (initialisation de solde, cutover complet, taux de migration), conserver un seul parcours DAF normal et documenter/retirer les doublons après vérification des clients. Exiger absence de stock/journal incompatible et journaliser la source Excel. **Fait :** les deux API séparées sans client frontend ont été retirées ; le cutover unique bloque les mouvements, journaux et taux préexistants, impose une date d'arrêté explicite et une référence civile. La clôture d'une campagne dont la fin est antérieure ou égale à l'arrêté n'acquiert pas de nouveau droit. Une nouvelle embauche sans historique ne passe jamais par ce cutover.
4. **Correction monétaire après consommation** : contrôler paiement déjà servi, montant légal minimal à la date de prise, solde non consommé et statut de paie. **Chemin existant réutilisé :** la variable `REGULARISATION_CONGE`, déjà dotée de l'état `EN_ATTENTE → APPROUVE → IMPUTE_PAIE`, apparaît désormais dans les types de variables. Son motif doit citer `JOURNAL_CONGE#id`, vérifié côté serveur pour le salarié. La DAF détermine le complément après revue des paiements ; aucune récupération automatique de trop-perçu. Aucun ajout de table. Le contrôle automatique du minimum légal et l'intégration au STC restent à réaliser avant de considérer la régularisation entièrement automatisée.
5. **Tests et suppression** : conserver seulement les vues/API réellement appelées ; tester reprise, cycle 2025→2026, FIFO, fériés, correction et régularisation. Toutes les migrations et tests BDD visent exclusivement `sirh_galaxam_test` après `current_database()`.

Les contrôles de dates et les droits acquis ont priorité sur la suppression de code : une API historique ne sera retirée qu'après inventaire de ses appels. Les changements de politique de décompte et les régularisations monétaires réclament des tests d'intégration paie/congés avant migration.
