# Pointage : sources de vérité et chemins de traitement

Ce document accompagne la [carte des fichiers](pointage-file-map.md). Toute évolution d'une règle de pointage doit commencer par le propriétaire indiqué ici, puis vérifier ses consommateurs. Les écrans construisent une intention de saisie et affichent des résultats ; ils ne fixent ni la durée acquise, ni le retard, ni le montant payé.

| Sujet | Propriétaire | Consommateurs |
| --- | --- | --- |
| Heures travaillées, retard, nuit et anomalies de journée | `AttendanceEngine`, appelé par `PointageService.previsualiserPointage` et `enregistrerPointagesBatch` | Saisie collective, saisie par période, import Excel, fiche employé, suivi et contrôle |
| Statut persistant d'une saisie ordinaire | `AttendanceStatusPolicy` et `PointageService` | Tous les écrans via les DTO ; `attendanceStatus.js` donne uniquement les libellés et couleurs, avec repli pour un ancien DTO sans statut |
| Horaire proposé pour une saisie | Configuration datée du site renvoyée par l'API | `pointageStandardHours.js` construit la même intention dans les deux modes de saisie ; aucune heure de remplacement codée en dur |
| Indemnités et fractions valorisées | Configuration datée et `PointageService.persistDynamicAllowances` | La fiche fournit les propositions datées à la saisie par période ; les écrans montrent les choix provisoires et le montant appliqué vient du serveur |
| Congé et solde de congé | Domaine Congés, appelé lors de la régularisation | Pointage affiche l'état et permet de déclencher le workflow ; il ne débite pas le solde localement |
| Totaux hebdomadaires, mensuels, gel et validation | `PointageSyntheseService` et moteur hebdomadaire | Fiche employé, contrôle DAF, paie et exports |
| Import Excel | `PointageImportService.preview` pour lecture et validation initiale ; `PointageService` pour la prévisualisation de chaque jour et l'enregistrement en lot | Aperçu d'import et saisie par période |
| Écriture de `pointage_jour` | `PointageService.writeDay`, appelé par la saisie en lot et la régularisation | Contrôles propres à chaque workflow avant écriture ; verrouillage de version commun |
| Résolution du statut après régularisation | `AttendanceStatusPolicy` | `PointageAnomalieService` transmet le choix explicite, le contexte horaire et la couverture du congé |

## Contrat frontend

- `pointageRow.js` gère les modifications provisoires d'une ligne.
- `pointageBatchPayload.js` convertit une ligne vers le contrat commun de prévisualisation et de sauvegarde.
- `usePointageBatchPreview.js` interroge le serveur après chaque changement et ignore les réponses périmées.
- `usePointageSaisie.js` et `usePointagePeriodeEmploye.js` orchestrent chacun une vue ; ils partagent les fonctions précédentes. Les totaux locaux ne servent qu'à résumer les résultats déjà renvoyés par l'API.
- Les actions de régularisation sont des commandes distinctes de la saisie ordinaire, car elles portent une justification, un historique et parfois une demande de congé.

## Redondances encore à résorber

1. Les différentes présentations reconstruisent parfois des drapeaux d'affichage à partir du statut lorsque les DTO ne fournissent pas ces champs. Stabiliser un DTO de journée commun permettrait de supprimer ces replis progressivement.
2. Les choix d'indemnités historiquement enregistrés hors d'une configuration encore applicable méritent une présentation dédiée en lecture seule : ils doivent rester auditables sans autoriser une nouvelle saisie sur un tarif expiré.

## Coût des lectures

La saisie par période demande désormais une grille limitée au collaborateur sélectionné ; ses requêtes filtrent le salarié. La grille collective conserve son contrat complet. La fiche ne charge les propositions et paramètres datés d'indemnités que lorsque la saisie par période le demande : deux requêtes groupées couvrent toute la période, puis les propositions sont résolues en mémoire. Le nombre de jours ouvrables est calculé une fois par site concerné, sans requête HTTP ou SQL par journée. Les colonnes de la saisie par période sont l'union des indemnités applicables dans la période ; chaque ligne utilise le tarif de sa date. La persistance d'une journée charge chaque tarif une seule fois avant de contrôler les choix et de calculer les montants. Les choix enregistrés et explicites restent prioritaires sur les propositions.

L'écriture SQL de la journée et la résolution des statuts de régularisation ont été rapprochées. Les tests ciblés de calcul, de congé et de pointage passent ; la validation de non-régression globale reste à faire sur l'ensemble des parcours.

## Cache et invalidation

Le frontend partage les fiches d'un même salarié et les options légères de période pendant 15 secondes. Une lecture simultanée identique utilise la même promesse ; une réponse arrivée après une invalidation ne repeuple pas le cache. Le changement de session, les écritures de pointage et les transitions de période vident les caches concernés. Les créations, modifications et décisions de congé invalident aussi la fiche de pointage.

La grille du jour, les anomalies, le précontrôle de transmission et les calculs DAF restent lus directement : leur contenu doit refléter immédiatement les saisies et décisions récentes. Les paramètres du site et les tarifs datés pourraient être mis en cache côté backend avec une clé site/date, mais seulement après avoir centralisé l'invalidation de leurs écrans de configuration et des jours fériés. Un TTL seul risquerait de proposer un ancien tarif après une modification administrative.

Pour chaque refonte, comparer les quatre entrées : saisie collective, saisie par période, import et régularisation ; puis vérifier fiche employé, suivi, contrôle DAF, paie et exports. Ne pas déplacer une règle dans un utilitaire frontend si elle influe sur les droits, les heures, le statut ou un montant.
