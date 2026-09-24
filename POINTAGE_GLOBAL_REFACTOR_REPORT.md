# Rapport — refactor global Pointage & Présence

Date : 2026-09-24  
Périmètre : refactor structurel réalisé sans redéfinir les règles métier.

## 1. Résultat

Cette passe supprime deux agrégats confirmés sans lecteur runtime, enlève la fausse validation hebdomadaire, regroupe les clients API frontend et transfère à `CongeService` les écritures de congé effectuées par la régularisation Pointage. Les calculateurs jour/semaine canoniques et le contrat de snapshot Paie sont conservés.

Le refactor n'est pas une réécriture totale du domaine : la persistance hebdomadaire, les deux modales, les pages dédiées, les calculs de prévisualisation React et les writes Pointage dispersés restent des dettes explicites (voir § 8). Les retirer maintenant sans remplacer leurs consommateurs risquerait une régression.

## 2. Changements effectués

### Backend

- Supprimé `AttendancePeriodCalculator`, `AttendancePeriodContext` et `AttendancePeriodResult`. L'agrégation des résultats hebdomadaires est maintenant locale à `PointageSyntheseService` sous forme de `PeriodTotals`.
- Supprimé `PointageTimeBoundaryMapper`. Les conversions triviales sont inline dans leurs propriétaires (`PointageSyntheseService` et `PointageEmployeService`).
- Les routes `POST /pointages/synthese/semaines/valider` et `/devalider`, ainsi que leurs méthodes publiques, ont été retirées. Le recalcul est interne et nommé `recalculerSemaine`; la semaine reste une unité de calcul matérialisée, pas une étape de validation utilisateur.
- Les insertions `demande_conge` et `mouvement_solde_conge` propres à la régularisation sont maintenant dans `CongeService`. `PointageAnomalieService` lui délègue via la même `Connection` JDBC déjà ouverte : les écritures congé + pointage restent dans la transaction existante.
- Le comportement déplacé reste volontairement historique : statut VALIDE, type ANNUEL / permission, mouvement PRISE si applicable, aucune nouvelle règle de validation ou de solde introduite.

### Frontend

- Fusion des trois fichiers API `attendanceService.js`, `pointageSyntheseService.js` et `periodePaieService.js` dans `features/attendance/api/attendanceApi.js`, organisé par groupes `daily`, `anomalies`, `employees`, `summary` et `period`.
- Migration des consommateurs Pointage, Paie, fiche employé et dashboard vers ce module unique. Les noms d'objets historiques sont temporairement exportés depuis le point d'entrée partagé pour limiter le risque de changement d'interface interne.

### Base de données

- `pointage_cumul_indemnite` et `pointage_snapshot_semaine` n'avaient pas de lecteur runtime. Leurs insertions au gel et leurs définitions du schéma courant sont supprimées.
- Migration forward ajoutée : [26-09-23-sp22-pointage-remove-unused-summaries.sql](backend/conception/bdd/migrations/26-09-23-sp22-pointage-remove-unused-summaries.sql).
- `pointage_cumul_periode` demeure l'unique contrat de consommation Paie. `pointage_snapshot_jour` conserve la provenance détaillée et `pointage_snapshot_bucket` est conservé car Paie le lit encore pour qualifier les heures.
- Les migrations historiques archivées ne sont pas réécrites.

## 3. Tables et contrats conservés

| Structure | Décision | Motif actuel |
|---|---|---|
| `pointage_jour` | KEEP | Source des saisies journalières |
| `pointage_indemnite_jour` | KEEP | Indemnités accordées/calculées par journée |
| `pointage_semaine_calcule` | KEEP temporaire | Sert encore aux synthèses, filtres HS, détails de semaine et recalcul préalable à la paie |
| `pointage_cumul_periode` | KEEP | Snapshot/version consommé par Paie |
| `pointage_snapshot_jour` | KEEP | Provenance jour au gel |
| `pointage_snapshot_bucket` | KEEP | Consommé par `PayrollService` pour les qualifications temporelles |
| `pointage_cumul_indemnite` | DROP | Aucun lecteur runtime; agrégat redondant avec les snapshots et indemnités jour |
| `pointage_snapshot_semaine` | DROP | Aucun lecteur runtime; redondant avec la provenance jour et le calcul hebdomadaire |

La suppression physique par migration est irréversible sans restauration de sauvegarde. La procédure de retour consiste à restaurer les tables et leurs données depuis un backup pré-migration; les données supprimées ne peuvent pas être reconstruites exactement à partir des seuls cumuls.

## 4. Frontières et sources de vérité après cette passe

- Calcul quotidien : `DailyAttendanceCalculator` côté backend.
- Calcul hebdomadaire / seuils HS : `WeeklyAttendanceCalculator`; ses résultats sont encore stockés dans `pointage_semaine_calcule` pour les lecteurs listés plus haut.
- Calendrier ouvrable : `CalendrierOuvrableEngine`.
- Agrégation période et gel : `PointageSyntheseService`.
- Workflow de période : `PayrollPeriodWorkflowService`.
- Contrat Pointage → Paie : `pointage_cumul_periode`; les buckets détaillés restent un support technique interne.
- Écritures Congés initiées par cette régularisation Pointage : `CongeService`, même connexion transactionnelle.
- Indemnités de journée : `pointage_indemnite_jour`.

Le transfert SQL vers `CongeService` est une relocalisation de responsabilité, pas encore une utilisation du moteur métier normal de demande de congé. La régularisation continue donc à contourner les validations classiques comme avant : c'est un point de refactor ultérieur, pas un changement de règle réalisé ici.

## 5. Mesures avant / après

Comptage des fichiers suivis sous les répertoires ciblés, comparaison HEAD → arbre de travail; le comptage exclut les artefacts d'audit non suivis déjà présents.

| Mesure | Avant | Après | Écart |
|---|---:|---:|---:|
| Fichiers Java sous `pointage/` | 45 | 41 | -4 |
| Fichiers frontend sous `features/attendance/` | 23 | 21 | -2 |
| Tables `CREATE TABLE` dans le schéma Pointage courant | 16 | 14 | -2 |
| Endpoints de validation hebdomadaire | 2 | 0 | -2 |
| Clients API Pointage/période dédiés | 3 | 1 | -2 |

Ces chiffres mesurent le delta de cette passe, pas la taille totale du backend, ni les fichiers d'autres domaines modifiés indirectement pour importer le client API unifié.

## 6. Vérification

- `mvn test -q` — réussi.
- `npm test -- --run` — réussi, 1 fichier / 4 tests.
- `npm run lint` — réussi.
- `npm run build` — réussi.
- `git diff --check` — réussi.

Les tests frontend existants restent très réduits; aucune nouvelle suite d'intégration dédiée au workflow Pointage n'a été ajoutée dans cette passe.

## 7. Workflow préservé

La chaîne quotidienne reste : saisie par batch → calcul backend au moment du traitement → correction/régularisation → recalcul hebdomadaire → contrôle/transmission et arbitrage via le workflow période → gel versionné dans `pointage_cumul_periode` → consommation Paie.

Le recalcul hebdomadaire reste un traitement technique déclenché par les chemins existants; il ne crée plus d'état de validation semaine. Le gel conserve snapshots jour et bucket utilisés pour l'historique et Paie.

## 8. Dettes et prochaines étapes

1. **Persistance hebdomadaire.** Ne pas supprimer `pointage_semaine_calcule` avant de remplacer les filtres et agrégats de `PointageSyntheseService`, les lectures de détail, le calcul HS de période et les besoins de `PayrollPeriodWorkflowService`. Prévoir une comparaison/replay des périodes avant DROP.
2. **Calculs React.** `pointageCalculations.js` recalcule encore heures, pause automatique, retard, statut et erreurs chronologiques pour le preview immédiat de la grille. Le backend recalcule à l'enregistrement. Garder ce preview UX pour l'instant; une vraie source unique exige un endpoint preview et des tests d'équivalence, ou l'acceptation d'un preview moins riche.
3. **Écritures journée.** `PointageSaisieService` et `PointageAnomalieService` modifient tous deux `pointage_jour`. Un propriétaire de persistance partagé reste à concevoir pour éviter la duplication SQL, sans créer une couche DAO abstraite.
4. **Modales.** `AnomalieRegularisationModal` et `DafAjustementPointageModal` restent séparées. La fusion est plausible, mais nécessite de clarifier le contrat commun et les différences d'autorisation avant de déplacer leurs formulaires et effets.
5. **Navigation.** `GestionPointagesPage` compose encore les vues saisie, anomalies et arbitrage. Ce sont des étapes d'un même parcours mais gardent des états et permissions distincts; leur fusion visuelle doit être faite avec tests d'accès et parcours.
6. **Congés.** Introduire plus tard une API applicative Congés qui conserve exactement le comportement par défaut tout en portant la transaction reçue; ne pas activer validations/solde/préavis sans décision métier.
7. **Buckets et tolérance.** Les buckets ne peuvent pas être retirés avant de comprendre/rejouer `OvertimePayCalculator`. La table de tolérance est conservée; la fusion avec le site demanderait une migration et un inventaire de ses consommateurs.
8. **Disponible actions / tests workflow.** La réponse Pointage ne fournit pas encore un contrat cohérent `availableActions`; ajouter les scénarios intégrés saisie → anomalies → transmission → gel → paie avant d'élargir la suppression des endpoints ou tables.

## 9. BUSINESS_RULE_SUSPECT

- La régularisation Congés crée toujours une demande `VALIDE` directement et débite le ledger selon l'ancien chemin, sans vérifier explicitement solde, pool, préavis ou validations usuelles. Comportement préservé; aucune règle nouvelle n'est affirmée.
- Le frontend présente un calcul de journée qui peut diverger du backend et reste un aperçu non officiel.
- Le nom historique « validation semaine » décrivait une matérialisation/recalcul, pas une approbation utilisateur réelle; les routes correspondantes ont été retirées.

Toute décision modifiant ces comportements doit être traitée comme une évolution métier séparée.
