# Matrice des rubriques de paie — état du moteur au 1er octobre 2026

Ce document décrit **ce que le code calcule aujourd'hui**. Il ne valide pas à lui seul le régime légal d'une rubrique. Les sources réglementaires et les dates d'effet doivent être vérifiées avant de rendre un régime modifiable.

| Famille | Entrée | Moment et effet actuel | Source technique |
| --- | --- | --- | --- |
| Salaire, absence, congés, heures majorées | Contrat, pointage gelé, droits à congés | Brut avant cotisations ; retenues et indemnités de congés distinctes | `PayrollExecutionEngine.calculerSalaireBrut` |
| Repas et déplacement ordinaires | Snapshot de pointage | Gains ajoutés au brut imposable et à l'assiette sociale commune | `PayrollExecutionEngine.calculerPrimes` |
| Gain manuel à l’étape `BRUT` | Type approuvé et rubrique liée | Gain ajouté au brut ; les drapeaux IRSA et sociaux déterminent séparément les assiettes | `PayrollService.preparerInput`, `PayrollExecutionEngine.calculerPrimes`, `calculerCotisationsSociales` |
| Gain manuel à l’étape `GAIN_NET` | Type approuvé et rubrique liée | Ajout après impôt, hors assiettes fiscales et sociales | `PayrollExecutionEngine.calculerIndemnitesNonImposables`, `calculerCotisationsSociales` |
| Cotisations | Assiette sociale, profil salarié, paramètres applicables | CNaPS, santé et FMFP selon les taux et plafonds du moteur | `PayrollExecutionEngine.calculerCotisationsSociales` |
| IRSA ou ISI | Profil, barème et assiette calculée | Calcul après cotisations ; exonération des HS seulement selon ses conditions dédiées | `PayrollExecutionEngine.calculerFiscalite` |
| Acompte, avance, prêt, retenue personnelle | Mouvements et échéances | Déduction du net disponible après impôt, avec plafonds selon le type | `PayrollExecutionEngine.calculerRetenuesEtAvances` |
| Net à payer | Résultat des paliers précédents | Arrondi final à la dizaine d'ariary | `PayrollExecutionEngine.calculerNetAPayer` |

## Frontières de configuration constatées

- `ref_type_mouvement_paie` relie un type à `paie_rubrique`. Le moteur lit `etape_calcul`, `soumis_irsa` et `soumis_cnaps` pour les **gains manuels ordinaires**. Certains codes de gain et de retenue ont un circuit dédié dans `PayrollService` et `PayrollExecutionEngine` ; changer leur rubrique ne suffit pas à changer ce circuit.
- Pour les gains manuels, `soumis_cnaps` et `soumis_sante` alimentent des assiettes distinctes. Le profil salarié détermine toujours si chaque cotisation est applicable. Le FMFP conserve son assiette historique liée à CNaPS ; il n’est pas configurable séparément.
- `ordre_affichage` règle l'affichage du catalogue. Il ne déplace pas une rubrique dans les paliers du calcul.
- Les nouvelles rubriques manuelles portent une référence de traitement et une date de début d'effet. Les rubriques historiques conservent des valeurs nulles jusqu'à leur examen. Le moteur refuse une rubrique dont la date d'effet est postérieure au début de la période calculée ; il choisit désormais la version manuelle couvrant toute la période. Pour un changement de régime après utilisation, créer une nouvelle version manuelle.
- Une retenue manuelle est toujours prélevée sur le net. La référence saisie est une trace à vérifier, pas une certification juridique automatique.
- Le taux historique de congé vient d'un circuit distinct. L'effet d'une prime sur les droits futurs ne se déduit pas actuellement des seuls drapeaux de sa rubrique.

## Référence de non-régression

Le test `PayrollExcelReferenceReconciliationTest.tnrFigeLesPaliersDuBulletinObserveAvecRetenueTelephone` fixe, pour le scénario reconstitué de l'agent Y TNR, le brut à **411 361,01 Ar**, les cotisations salariales à **8 227,22 Ar**, le net imposable à **403 133,79 Ar**, l'IRSA à **3 000 Ar**, le net avant arrondi à **399 123,79 Ar** et le net à payer à **399 120 Ar** après la retenue téléphonique de **1 010 Ar**. Les autres tests couvrent séparément le placement repas/déplacement et l'indépendance des drapeaux IRSA et CNaPS. Ces tests protègent la mécanique observée ; ils ne certifient pas la conformité des taux ou des exonérations.

Les gains manuels au brut exonérés d’IRSA sont exclus de la base IRSA sans être ajoutés une seconde fois au net. Les lignes nouvelles conservent l’étape exécutée ; les lignes historiques restent inchangées. Le paramétrage porte sur des montants saisis, pas sur des formules libres, et ne modifie pas le circuit ISI.

## Centralisation des contributions manuelles

`ManualGainContributions` résout, pour chaque gain saisi, ses contributions au
brut, aux assiettes CNaPS et santé, à l’IRSA et à l’ajout net. Les paliers du
moteur utilisent cette même résolution pour le placement, l’exclusion fiscale
et la correction de l’assiette sociale. La catégorie d’affichage ne détermine
pas ces contributions. La résolution conserve la précision du montant ; les
arrondis restent appliqués par les calculateurs de chaque palier.

Les garde-fous de configuration et de collecte restent actifs. Les calculateurs
automatiques, la base congés et le circuit ISI ne sont pas intégrés à cette
résolution. Aucun changement de schéma n’est nécessaire pour cette étape.
