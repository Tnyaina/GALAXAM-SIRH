# Pointage — état de la mise en œuvre (24 septembre 2026)

## Réalisé dans ce lot

- `FIXE_MENSUEL` : le gel calcule, par date et affectation de site, la quote-part du tarif mensuel sur les jours calendaires exacts de la période de paie. Il agrège par indemnité et fige le résultat dans `pointage_snapshot_indemnite`. La Paie lit ce snapshot (contrat v2), pas le tarif courant. Test d'intégration : 10 jours à 30 000 Ar et 20 jours à 60 000 Ar sur 30 jours donnent 50 000 Ar ; une modification ultérieure des tarifs ne change pas la lecture de la période gelée.
- Correction d'une journée : indemnités sélectionnées par identifiant de catalogue, tarif daté du site et fractionnement configuré. Les modales Agent/DAF chargent la configuration de la grille et n'exigent plus de branche UI propre à REPAS ou DÉPLACEMENT. Une nouvelle indemnité de test est persistée par ce chemin.
- Régularisation d'un congé oublié : Pointage appelle `CongeService` pour créer puis valider la demande dans la même transaction que la correction de journée ; la journée conserve `id_demande_conge`. Le moteur Congés gère le mouvement de solde. Les doublons, versions périmées et périodes déjà validées sont rejetés. Tests d'intégration : congé validé lié à la journée, mouvement PRISE unique, permission validée, solde insuffisant et période gelée.
- Heure minimale comptabilisée : activation explicite, sans fallback quand la règle est désactivée ; heure brute et heure effective séparées. Migration forward `sp25` nécessaire avant déploiement du backend.
- Prorata journalier : un calendrier ouvrable vide avec montant positif provoque `MISSING_WORKING_CALENDAR` au lieu d'attribuer le montant mensuel entier.
- Les synthèses de période et de semaine résolvent désormais l'objectif journalier par la même méthode que la saisie. Les trois replis locaux à `8 h` ont été supprimés : `DISABLED` reste inactif et `MISSING` est une erreur explicite. Le seuil de première tranche HS vient de la configuration légale datée ; une configuration absente ne déclenche plus un `8 h` codé en dur.
- L'arbitrage DAF affiche les montants de toutes les indemnités par code, au lieu d'additionner uniquement repas et déplacement. Avant gel, la lecture part des journées ; après gel de la période complète, elle part des snapshots consommés par Paie. Le détail d'une semaine reste limité à ses journées.
- Le survol Gantt présente les indemnités de la grille et n'affiche plus de pourcentage de majoration codé dans React.
- L'action collective de la grille n'est plus « Transport AR » codé en dur : une action est proposée pour chaque indemnité configurée sur le site. Le préremplissage des horaires ne force plus une indemnité de déplacement ni un statut calculé côté React.
- Le détail hebdomadaire affiche le total d'indemnités fourni par le backend et les indemnités réellement accordées dans chaque journée, sans colonnes fixes « Repas » et « Déplac. ».
- La sauvegarde batch exige désormais `dynamicIndemnites` (une map vide est valide). Elle ne calcule plus et n'insère plus REPAS/DÉPLACEMENT via le vieux chemin parallèle ; elle remet à zéro les anciennes lignes et recalcule toutes les indemnités à partir des sélections et tarifs datés.
- La détection des modifications locales ne compare plus les anciens drapeaux Repas/Déplacement : elle compare la sélection générique d'indemnités.
- Test d'intégration du `MENSUEL_PRORATISE` : deux tarifs de site différents, valeur de site zéro avec paramètre d'exception, règle d'exception désactivée et paramètre manquant. Le changement de site ne reprend pas rétroactivement le tarif du dernier site ; une règle désactivée donne zéro sans fallback.
- La fiche employé lit la durée hebdomadaire légale datée sans repli à `40 h` ; une configuration légale manquante est signalée explicitement.

## Vérifications exécutées

- `mvn -q clean test` : succès.
- `npm test -- --run` : succès, 4 tests.
- `npm run lint` : succès.
- `npm run build` : succès.
- `git diff --check` : succès.

## Non terminé — ne pas assimiler à une parité complète

1. La sauvegarde batch n'a plus de chemin monétaire par code. Des champs de compatibilité de la grille, de certaines lectures et des synthèses utilisent encore `REPAS`, `DEPLACEMENT` ou `TRANSPORT`. La vue DAF et les fiches/exports lisent aussi la projection générique, mais il faut retirer ces anciens champs après des tests de parité complets.
2. Les résolutions de tarif `MENSUEL_PRORATISE` par site, valeur zéro/exception et désactivation sont testées, mais il manque encore un replay monétaire de bout en bout avec demi-journée, cumul multi-sites effectivement persisté, et comparaison des montants dans tous les exports.
3. Les tests frontend ne couvrent pas le parcours navigateur Agent → Anomalies → DAF → Paie ; seules compilation, lint et quatre tests unitaires ont été exécutés. L'application locale a été ouverte, mais elle présente l'écran de connexion et aucune session de test n'est disponible dans le navigateur intégré. Ne pas considérer l'UX validée en situation réelle.
4. La table de seuil d'arrivée est activable, mais pas encore historisée par date. Un recalcul d'une période passée non gelée peut utiliser le réglage courant (`HISTORICAL_CONFIGURATION_RISK`).
5. Les fallbacks d'objectif journalier, de première tranche HS et de durée hebdomadaire de la fiche ont été supprimés. Il faut encore vérifier les autres familles de configuration avec un replay historique et leur source datée.
6. La migration `sp25` a été appliquée dans la base de test pendant la validation, pas dans une base de production. Déployer la migration avant le code dépendant de ses colonnes.

## Séquence restante recommandée

1. Ajouter un jeu de replay monétaire pour chaque périodicité (`JOURNALIER`, `MENSUEL_PRORATISE`, `FIXE_MENSUEL`) et chaque fraction, avec tarif zéro, exception et changement de site.
2. Faire lire la même projection générique par la grille, les fiches, les exports et le gel ; retirer ensuite les champs et branches de compatibilité codés par libellé ou code spécifique.
3. Tester dans le navigateur, sur données de test, la saisie, la correction, le congé oublié, le rejet/gel DAF et la préparation Paie ; vérifier les montants avant/après validation et l'immutabilité de la paie validée.
4. Historiser la règle d'heure minimale avant de permettre un replay historique automatique.
