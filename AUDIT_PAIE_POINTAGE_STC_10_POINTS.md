# Audit ciblé Paie, Pointage et STC

> Audit préliminaire. Les constats et tests après alignement sont consignés dans [RAPPORT_ALIGNEMENT_PAIE_POINTAGE_STC.md](RAPPORT_ALIGNEMENT_PAIE_POINTAGE_STC.md) ; ce rapport prévaut pour l’état actuel.

Date : 29 septembre 2026. Audit du code et des tests du dépôt. Les tests d'intégration ciblés utilisent le profil `test`, configuré sur `sirh_galaxam_test` dans `backend/src/test/resources/application-test.properties`.

## Constats

| # | Sujet | État constaté |
|---|---|---|
| 1 | HS des semaines partielles | Le pointage découpe les semaines à l'intersection de la période et calcule l'objectif depuis les jours ouvrables du tronçon, congés déduits. La première tranche HS reste toutefois le seuil complet paramétré, même pour un tronçon partiel. Les tests couvrent les frontières de période et l'absence de compensation inter-périodes, mais pas embauche/sortie au milieu d'un tronçon ni la tranche HS proratisée. À faire confirmer par DAF avant de modifier cette règle. |
| 2 | Dimanche / cumul additif | Le moteur sait calculer `CUMUL_ADDITIF`, mais le résolveur paie ne fournit actuellement aucune politique. Il retombe sur `UNRESOLVED` et bloque lorsqu'un même intervalle cumule plusieurs sujétions (par exemple dimanche et férié, ou nuit et dimanche). Aucun paramètre de configuration ni test de cette collision n'a été trouvé. Ne pas activer le cumul sans confirmation DAF/RH et règle applicable. |
| 3 | Déplacement | Le calcul journalier est centralisé dans `AttendanceEngine.calculateTravelAmount`, avec tarif et réduction configurés. La synthèse applique aussi un traitement distinct pour le forfait mensuel lorsque tous les jours ouvrables sont pointés. Aucun test direct de rapprochement de ces deux cas n'a été trouvé : le code est unifié au niveau journalier, mais la conformité métier du traitement mensuel reste à valider sur les exemples DAF. |
| 4 | Référentiel santé daté | Le référentiel de cotisation possède `date_debut` / `date_fin`, mais ces dates n'étaient pas utilisées pour résoudre la cotisation Santé de la paie ni du STC. **Corrigé dans cet audit** : les deux recherches filtrent désormais le taux à la date de fin de campagne ou à la date de départ STC et signalent une configuration STC ambiguë. Aucun schéma ni donnée n'a été modifié. Il manque un test d'intégration dédié aux bascules historiques de taux Santé. |
| 5 | Quotité cessible | La paie contient un plafond codé à `1/3` pour les remboursements de prêts et un plafond `10 %` pour les avances spéciales. Ce plafond n'est pas résolu depuis le référentiel légal ni versionné par date. Le Code du travail 2024, article 100, renvoie les cessions/saisies au Code de procédure civile et distingue les avances spéciales ; il ne justifie pas à lui seul la constante générique `1/3` utilisée ici. L'implémentation doit rester à confirmer par DAF/juridique avant paramétrage ou changement de valeur. |
| 6 | Simulation sans écriture | La simulation n'enregistre pas de bulletin : ses changements d'imputation de prêt/avance sont effectués dans la transaction puis annulés au savepoint. Elle ne satisfait donc pas une définition stricte « aucune instruction SQL d'écriture » ; les effets ne sont pas persistés après rollback. Rendre le chemin strictement sans écriture exige de reconstruire en mémoire les imputations d'un bulletin existant ; ne pas supprimer la restauration sans couvrir ce cas, car les résultats de simulation pourraient diverger. |
| 7 | Snapshots et historique daté | Le bulletin fige les données d'identité, la référence de configuration légale et les lignes de calcul (base, taux, quantité, montants) ; le pointage est associé à un snapshot/version. Les dates d'effet de plusieurs barèmes sont résolues à la date métier. Les lignes calculées d'un bulletin validé sont donc conservées, mais la modification d'un référentiel en place peut altérer la capacité à reconstituer un ancien calcul non figé. |
| 8 | Explicabilité des lignes | Les lignes de bulletin gardent base, taux, quantité, montants et libellé. Elles permettent de vérifier l'arithmétique principale ; l'explication textuelle normalisée des règles (source du taux, tranche, plafond appliqué, motif d'écrêtement) n'est pas systématique. |
| 9 | STC et calculateurs | Le STC réutilise les calculateurs partagés IRSA, cotisations sociales, ISI et valorisation congés, mais son orchestration et la préparation de ses assiettes restent distinctes du bulletin mensuel. Le correctif Santé aligne la résolution temporelle du taux ; aucun test comparatif complet STC / bulletin avec une entrée commune n'a été trouvé. |
| 10 | Shadow run et rapprochement | Aucun shadow run persistant n'existe. `PayrollExcelReferenceReconciliationTest` rapproche deux cas de référence Excel, et l'intégration vérifie simulation puis calcul/recalcul sur la base de test ; cela ne remplace pas la comparaison parallèle d'une campagne complète avec les résultats validés DAF. |

## Changements et vérifications

- Fichiers corrigés : `PayrollLegalContextResolver.java`, `StcService.java` (filtrage des cotisations Santé à la date métier).
- Aucun changement de migration ou de données.
- `mvn -q -DskipTests package` : réussi.
- Tests ciblés : `AttendanceEngineTest`, `PayrollOvertimeSettlementTest`, `PayrollMealTravelGrossTest`, `DafOvertimePayTest`, `PayrollExcelReferenceReconciliationTest`, `PayrollPointageToBulletinIntegrationTest` : réussis.
- `git diff --check` : réussi.

## Référence juridique consultée

Le Code du travail malgache 2024, loi n° 2024-014, article 100, renvoie les cessions de rémunération au Code de procédure civile ; son article 101 prévoit la retenue d'office des acomptes. Cette référence ne permet pas de confirmer seule les ratios codés actuellement. [Texte officiel de la loi n° 2024-014](https://cnlegis.gov.mg/uploads/L2024-014-VF.pdf).

## Suite recommandée

1. Faire confirmer par DAF/juridique le traitement des collisions dimanche/férié/nuit et le barème exact de saisie/cession applicable.
2. Décider si la première tranche HS d'un tronçon de semaine partiel est entière ou proratisée, y compris en cas d'entrée/sortie en cours de tronçon.
3. Ajouter des scénarios de test dédiés aux dates Santé, au forfait déplacement mensuel, aux snapshots d'imputation en simulation et à la comparaison STC / paie.
4. Organiser un shadow run d'une campagne choisie sur la base de test avec export et résultats DAF, puis documenter les écarts avant adoption.
