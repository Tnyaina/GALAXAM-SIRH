# Recette des variables sur copie des données — 1er octobre 2026

La base de travail `sirh_galaxam` a été copiée avec `pg_dump`/`pg_restore` vers
`sirh_galaxam_recette_variables_20261001`, sans réinitialiser la source.
La recette démarre un contexte de test Spring, sans serveur HTTP, avec le
processeur des tâches planifiées remplacé par un mock, le rattrapage désactivé
et la création automatique de compte administrateur désactivée.

## Résultats pour le salarié de référence, matricule 199

Chaque variable est essayée seule, pour 10 000 Ar, sur la période 2026-08.
La retenue téléphonique existante de 1 010 Ar reste prise en compte.

| Scénario | Brut | Cotisations salariales | IRSA | Net avant arrondi | Net à payer |
| --- | ---: | ---: | ---: | ---: | ---: |
| Référence | 411 361,01 | 8 227,22 | 3 000,00 | 399 123,79 | 399 120,00 |
| Gain imposable et social +10 000 | 421 361,01 | 8 427,22 | 3 790,00 | 408 133,79 | 408 130,00 |
| Gain hors assiettes +10 000 | 411 361,01 | 8 227,22 | 3 000,00 | 409 123,79 | 409 120,00 |
| Gain au brut exonéré, hors cotisations +10 000 | 421 361,01 | 8 227,22 | 3 000,00 | 409 123,79 | 409 120,00 |
| Gain au brut exonéré, soumis aux cotisations +10 000 | 421 361,01 | 8 427,22 | 3 000,00 | 408 923,79 | 408 920,00 |
| Retenue nette −10 000 | 411 361,01 | 8 227,22 | 3 000,00 | 389 123,79 | 389 120,00 |

Le scénario imposable produit une assiette IRSA arrondie de 412 900 Ar.
Le barème configuré donne 50 000 × 5 % + 12 900 × 10 % = 3 790 Ar.
Il dépasse alors le minimum de perception configuré à 3 000 Ar. Ces résultats
vérifient la mécanique du moteur avec le référentiel copié ; ils ne constituent
pas une validation juridique d'une nouvelle exonération.

## Défaut détecté et corrigé

Le moteur écrivait le code du type de variable sur les lignes de gain et
utilisait une correspondance fixe pour les retenues. Lorsque le code du type
différait de celui de sa rubrique, le montant était juste mais la ligne n'était
pas liée à la rubrique choisie ; une retenue personnalisée pouvait même porter
le code d'un acompte. Le moteur reçoit désormais le code de la rubrique liée,
ainsi que sa catégorie pour les gains, et le conserve jusqu'à la persistance.
Aucune table ou colonne supplémentaire n'est nécessaire pour cette correction.

## Contrôles réussis

- Création de rubriques et de types via les contrôleurs utilisés par l'interface.
- Simulation individuelle avec les cinq combinaisons et assertions sur les paliers.
- Refus d'un changement d’étape, d'assiette ou de date d'effet après création d'un mouvement.
- Refus de calcul d'une variable dont la rubrique commence après le début de période.
- Calcul réel et rattachement à la rubrique configurée, puis annulation de la transaction.
- Retour au net de référence après annulation des variables de recette.
- Empreintes identiques entre source et copie pour `employe`, `pointage_cumul_periode`,
  `bulletin_paie` et `bulletin_paie_ligne` après les essais.

Les rubriques, types et mouvements annulés de recette demeurent uniquement dans
la copie. La copie est conservée pour permettre de reproduire les essais.

## Rejouer

Le test est désactivé par défaut et vérifie le nom exact de la base avant toute
écriture de recette. Depuis `backend` :

```sh
mvn -q -Dtest=PayrollVariablesCopiedDataTest,PayrollVariableRubriqueLinkTest,PayrollExcelReferenceReconciliationTest,PayrollAllowanceSocialBaseTest,PayrollMealTravelGrossTest -Dpayroll.copied-data-test=true test
```

Résultat : **14 tests réussis**, aucune erreur ni échec. Le test sur copie utilise
les données du salarié et du bulletin de référence ; si la copie est remplacée
par un autre jeu de données, les attentes doivent être revues.

La seconde recette vérifie également l’étape persistée, l’absence de double ajout
des gains exonérés au net et leur classement dans l’export SAFI. La migration sp45
est additive et ne recalcule aucun bulletin existant.

## Séparation CNaPS / santé

Deux scénarios supplémentaires sur la copie ajoutent 10 000 Ar au brut exonérés
d’IRSA, soumis uniquement à CNaPS ou uniquement à la santé. Chaque scénario
produit 8 327,22 Ar de cotisations salariales et un net de 409 020 Ar avec les
taux du salarié de référence. Quatre essais unitaires avec des taux différents
vérifient séparément les bases et montants CNaPS/santé. Aucun changement de
schéma ni de données de travail n’est nécessaire : `soumis_sante` existe déjà.

## Versions et applicabilité

La recette crée une famille avec une version initiale, une version applicable
à août et une version future. Le type reste lié à l’origine : août utilise le
code de la deuxième version, même après création de la troisième. Le retour
au net de référence est vérifié après annulation du mouvement de recette.
Une création rétroactive sur une période utilisée est refusée. Un chevauchement
injecté directement en SQL est refusé par le trigger. Un profil non assujetti
à l’IRSA reçoit une erreur explicite pour un mouvement conditionné à l’IRSA ;
la modification du profil est annulée dans la transaction de test.

Les tests ciblés comptent désormais **31 tests réussis**, dont les huit tests
unitaires de `ManualRuleApplicabilityTest`. Les essais de configuration restent
sur la copie ; seule la migration additive est destinée à la base de travail.
