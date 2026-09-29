# Vérification Congés — reprise, FIFO et valorisation historique

## Déjà correct

- Les jours acquis sont calculés et proratisés dans le domaine Congés à partir du paramétrage en base. Les HS alimentent la base monétaire du journal mensuel, sans créer de jours supplémentaires.
- Le grand-livre des jours, l'annulation des prises et l'imputation FIFO existaient. Le cutover conservait sa date, ses jours et son TCONGE ; les cumuls d'ouverture de la période de référence sont des compteurs de calcul, non la valeur du solde restant.
- Les exports individuels et collectifs utilisaient les canevas Excel du dépôt.

## Modifications

- Les anciennes et nouvelles mentions du stock initial sont reconnues et ce stock est servi en premier. Les nouveaux cutovers figent leurs jours et leur valeur dans `mouvement_solde_conge`.
- Un moteur rejoue les lots de droits, leurs valeurs d'origine, les prises, les ajustements, les annulations et les expirations. Chaque imputation porte l'identifiant du droit source, les jours, la valeur et le taux historique. Une prise sur plusieurs lots est ventilée ; les jours anticipés sans lot gardent le TCONGE applicable à la prise. Une acquisition ultérieure couvre l'anticipation dans le stock sans générer une seconde indemnité.
- La paie somme les valeurs imputées pour l'indemnité congé ; le solde de tout compte utilise la valeur des lots restants lorsque son nombre de jours n'est pas modifié manuellement. Les bulletins déjà validés ne sont pas réécrits.
- Les exports présentent la valeur historique restante et une feuille d'origine/consommations. Le détail est aussi disponible par `GET /conges/solde/valorisation-lots/{idEmploye}`.
- La DAF peut corriger le reliquat de reprise par `POST /conges/solde/cutover/{idEmploye}/corriger` avec `nouveauReliquatJours`, `nouveauReliquatValeur` et `motif`. La correction conserve ancienne/nouvelle valeur, ancien/nouveau taux, auteur, date et écart antérieur **à examiner**. Elle ne modifie que les jours et la valeur futurs du lot de reprise. L'écart déjà payé doit être traité par la régularisation de congé existante après examen DAF ; il n'est pas versé automatiquement car une prise peut ne pas encore avoir été payée.
- Dans le registre Congés de la fiche salarié, l'action « Corriger la reprise » est réservée au droit de validation. Elle préremplit le reliquat depuis l'API, recueille le motif DAF, puis montre l'écart potentiel et conduit à la liste des campagnes de paie. La DAF choisit explicitement la campagne et ouvre ses variables ; le backend contrôle si la campagne peut encore recevoir des variables.
- Les nouveaux crédits de bonification et d'ajustement positif figent le taux à la date du crédit. Une valorisation spécifique DAF peut être transmise à l'ajustement manuel. Si la source d'un lot historique manque, le calcul renvoie une erreur explicite plutôt que d'utiliser le salaire actuel.

## Fichiers et base

Le schéma et la migration concernent `mouvement_solde_conge.valeur_source` et `conge_cutover_correction`. Les principaux services concernés sont `CongeService`, `CongeTauxApplicableService`, `CongeLotValorisationService`, `CongeLotValorisationEngine`, `PayrollService`, `PayrollExecutionEngine`, `StcService` et les deux services d'export Excel. La documentation métier `backend/docs/boky/module-conges-absences.md` est mise à jour.

La migration `backend/conception/bdd/migrations/26-09-29-sp38-conge-cutover-correction.sql` a été exécutée **uniquement** sur `sirh_galaxam_test`, après vérification explicite de `current_database()`. Elle a été rejouée pour valider son caractère idempotent. Aucune donnée de production n'a été touchée.

## Vérification

- Tests ciblés : moteur FIFO/valorisation, cutover, annulation, anticipation, intégration PostgreSQL sur la base de test, exports et ligne de paie.
- Frontend : test de l'action du registre, lint ciblé et build Vite exécutés.
- Suite backend complète : 197 tests lors du dernier passage ; deux échecs préexistants liés au paramétrage de test (`ContractDocumentTest.missingEffectiveTimeConfigurationFailsExplicitly` et `CongeWorkflowIntegrationTest.testSoldeNegatifEtDerogation`, dont l'anticipation est désactivée dans les paramètres de la base de test). Aucun autre échec dans ce passage.
- Build backend (`mvn -q -DskipTests package`) réussi ; `git diff --check` sans erreur. Les tests ciblés ont été rejoués après la correction de précision entre le journal (quatre décimales) et le grand-livre (deux décimales).

## Points restant à arbitrer ou intégrer

- La correction calcule un écart potentiel sur les jours de reprise déjà consommés. La DAF doit rapprocher cet écart des bulletins effectivement payés puis enregistrer la régularisation de congé sur une période ouverte. Une automatisation ne doit pas créer un mouvement de paie tant que ce rapprochement n'est pas défini.
- Un solde de congés modifié manuellement dans la simulation de STC conserve le calcul unitaire existant ; la DAF doit corriger le grand-livre avant le STC pour obtenir la valeur exacte des lots.
- Les anciennes acquisitions sans journal mensuel ni valeur source et les crédits sans TCONGE historique nécessitent une régularisation des données ; le service les signale explicitement.
