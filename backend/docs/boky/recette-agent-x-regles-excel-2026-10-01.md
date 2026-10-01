# Rapprochement Agent X TMM — août 2026

## Périmètre

Lecture des valeurs mémorisées et des formules des deux classeurs originaux, sans modification ni recalcul des fichiers :

- `ohatra presence svt clocker TMM sy TNR AGENT 17.11.11.xlt`, feuille `AGENT TMM`.
- `ohatra kajy Droits congés-sept26 23.08.52.xls`, feuilles `agent TMM` et `recap salaire`.

Reconstitution sur `sirh_galaxam_recette_variables_20261001`, dans une transaction intégralement annulée. Aucun Agent X n'a été créé dans la base métier. Les informations personnelles du salarié de recette sont fictives.

## Règles rapprochées

| Élément | Source Excel / règle | Reconstitution moteur |
|---|---|---|
| Base | `recap salaire!D5` : 370 000 Ar | 370 000 Ar |
| Présence | 15 jours pointés, horaires réels du 27 juillet au 14 août | 128,95 h, soit 128 h 57 |
| HS hebdomadaires | 4,40 + 2,55 + 2,00 h | 8,95 h avant ajustement |
| Ajustement horaire | `AGENT TMM!T45`, `Y51` : −10 min × 15 déplacements | −150 min ; HS finales 6,45 h, soit 6 h 27 |
| Valorisation HS | Base / 173,33 × 130 % × 6,45 | 17 899,09 Ar |
| Congés | 17–25 août : 9 jours calendaires ; déduction base / 30 | −111 000 Ar |
| Indemnité congés | Deux lots `agent TMM!V19:W20` ; moyenne arrondie `recap salaire!H5` | Reprise des lots : 2,91 j / 26 285,26 Ar et 6,09 j / 60 682,53 Ar ; paiement 86 967,79 Ar |
| Déplacement | `recap salaire!K5` : 53 200 / 22 × 15 − 1 000 | 35 272,70 Ar après arrondis journaliers |
| Cotisations salariales | CNaPS 1 %, OMSI 1,5 % sur le brut de ce cas | 3 991,40 + 5 987,09 Ar |
| IRSA | Excel arrondit l'assiette à la centaine inférieure | Même arrondi ; différence d'assiette détaillée ci-dessous |
| Téléphonie | `recap salaire!X5` : 6 080 Ar | Retenue sur le net après impôt |
| Net final | `recap salaire!Z5` : arrondi au multiple de 10 | 380 080 Ar |

Les −1 000 Ar réduisent le déplacement, ils ne constituent pas une seconde retenue sur le net. Pour reproduire le montant sans date précisée, un repas fourni est positionné le **10 août 2026**, conformément à l'autorisation de choisir un jour. Le repas est à zéro Ar à TMM ; le service de pointage applique alors la réduction de déplacement. Cette date est une hypothèse de recette.

Les −150 minutes sont un autre ajustement, explicitement présent dans la feuille de pointage. Ils ne remplacent pas les −1 000 Ar.

## Résultats

| Montant | Excel | Moteur |
|---|---:|---:|
| Indemnité de congés | 86 967,81 | 86 967,79 |
| HS 30 % | 17 899,0942 | 17 899,09 |
| Déplacement | 35 272,7273 | 35 272,70 |
| Brut | 399 139,6315 | 399 139,58 |
| Cotisations salariales | 9 978,4908 | 9 978,49 |
| Assiette IRSA arrondie | 389 100 | 371 200 |
| IRSA retenu | 3 000 | 3 000 |
| Téléphonie personnelle | 6 080 | 6 080 |
| Net avant arrondi | 380 081,1407 | 380 081,09 |
| **Net à payer** | **380 080** | **380 080** |

Les cinq centimes de différence avant arrondi s'expliquent principalement par :

- le déplacement arrondi à deux décimales par jour, puis cumulé ;
- les congés valorisés à partir des lots, plutôt que neuf fois leur taux moyen préalablement arrondi ;
- les montants de HS et cotisations arrondis au centime.

**Le net identique ne signifie pas que toutes les règles fiscales sont identiques.** Le moteur exclut ici les 17 899,09 Ar de HS de l'assiette IRSA selon sa configuration actuelle ; le récapitulatif Excel ne le fait pas. Le minimum de 3 000 Ar masque cette différence pour Agent X. La présente recette ne constitue pas une validation juridique de cette exonération ni du barème.

## Congés : portée de la comparaison

Le journal historique TMM acquiert 2,5 × jours éligibles / 30 ; la valorisation mensuelle utilise la base diminuée des absences et augmentée de l'indemnité de congés et des HS. Les indemnités de déplacement ne sont pas ajoutées à cette base dans les formules inspectées. Le moteur possède cette séparation.

La recette reprend les lots nécessaires au congé d'Agent X. Elle ne rejoue pas tout le journal historique : le classeur présente des valeurs reprises manuellement et ne fournit pas l'intégralité des événements de 2026. L'acquisition mensuelle et tous les taux historiques ne sont donc pas certifiés par ce seul bulletin.

## Anomalies du classeur à ne pas recopier

- `recap salaire!N4` compare la CNaPS à `$O$3`, qui contient un intitulé, pas un plafond numérique.
- `agent TMM!X23` comporte une division par zéro lors d'un lot épuisé.
- Les branches de la formule IRSA ne garantissent pas uniformément le minimum : Agent Y présente 2 810 Ar dans ce fichier, contre 3 000 dans la configuration actuelle.
- Dans la feuille de présence TNR, le calcul d'absence finale ajoute des jours de congés au solde horaire ; le récapitulatif salaire retient néanmoins le déficit. Le moteur sépare les congés du déficit horaire.

## Correction apportée et vérification

Les lecteurs de pointage reconnaissaient `DEPLACEMENT` et `TRANSPORT`, mais pas le code réel du catalogue `DPLCT`. Le montant était déjà calculé, mais le compteur du snapshot restait à zéro. Ajout de cet alias aux lecteurs existants : **15 jours de déplacement** sont maintenant restitués. Aucune nouvelle table ni règle métier n'a été ajoutée.

Test : `PayrollAgentXCopiedDataTest`, données horaires dans `src/test/resources/paie/agent-x-tmm-aout-2026.csv`.

Le test exécute la saisie journalière, la réduction de déplacement, l'ajustement horaire, le gel, la simulation, le calcul enregistré puis un recalcul. Il vérifie le maintien du même bulletin et du même net. Tous les changements sont annulés à la fin.

Commande, depuis `backend` :

```sh
mvn -q -Dtest='PayrollAgentXCopiedDataTest,PayrollVariablesCopiedDataTest,mg.galaxam.sirh.paie.domain.engine.*Test' -Dpayroll.copied-data-test=true test
```

Cette recette couvre Agent X et les références automatisées existantes. Elle ne couvre pas à elle seule les HS 50 %, les dimanches/jours fériés non nuls, toutes les exonérations ou tous les profils sociaux.
