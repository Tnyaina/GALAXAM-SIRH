# Certification sémantique du bulletin de paie

## Verdict

`PAYROLL_BULLETIN_SEMANTIC_CERTIFICATION_PASS` — les fixtures fiscales et sociales utilisent désormais le contexte légal daté ou des valeurs de fixture explicitement rattachées à la période testée. Les anciennes bornes IRSA `+1` et leurs résultats dérivés ont été supprimés des tests actifs. Toutes les suites exigées sont vertes.

## Cascade canonique

| Étape | Concept | Code | Source | Exemple RAKETAKA historique | Arrondi |
|---:|---|---|---|---:|---|
| 1 | Salaire de base | `BASE_SALARY` | Contrat | 625 000,00 | Centime |
| 2 | Retenue absence | `ABSENCE_DEDUCTION` | Pointage | à reconstruire séparément | Centime `HALF_UP` |
| 3 | Déduction congé | `LEAVE_BASE_DEDUCTION` | Jours × base / 30 | à reconstruire séparément | Centime `HALF_UP` |
| 4 | Indemnité congé | `PAID_LEAVE_INDEMNITY` | Jours × TCONGE | 222 503,38 | Centime `HALF_UP` |
| 5 | Salaire brut | `GROSS_SALARY` | Moteur | 632 708,46 | Centime |
| 6 | CNaPS salarié | ligne `COTISATION_CNAPS_SAL` | Calculateur social | 6 327,08 | Centime |
| 7 | Santé salarié | ligne santé + organisme | Calculateur social | 6 327,08 | Centime |
| 8 | Revenu net avant impôt | `NET_INCOME_BEFORE_INCOME_TAX` | Brut − cotisations | 620 054,29 | Centime |
| 9 | Revenu net imposable | `TAXABLE_NET_INCOME` | Étape 8 − exonérations fiscales | 620 054,29 si aucune exonération | Centime |
| 10 | Base imposable IRSA | `IRSA_TAX_BASE` | `IrsaCalculator` | 620 000,00 | `CENTAINE_INF` |
| 11 | IRSA calculé | `IRSA_CALCULATED` | Barème daté | 31 500,00 moderne | Centime |
| 12 | IRSA dû | `IRSA_DUE` | Arrondi, réduction, minimum | 31 500,00 | `DIZAINE_SUP` puis centime |
| 13 | Éléments nets | `NET_ITEMS` | Mouvements | CALL perso −21 470,00 | Centime |
| 14 | Net avant arrondi | `NET_PAYABLE_BEFORE_ROUNDING` | Cascade moteur | 567 084,29 | Centime |
| 15 | Net à payer | `NET_PAYABLE` | Moteur | 567 080,00 | Dizaine la plus proche |

## Définitions fiscales

- **Revenu net avant impôt** : brut moins parts salariales. C'est la signification démontrée de `NETI`.
- **Revenu net imposable** : revenu net avant impôt diminué des rémunérations fiscalement exonérées. Il reste distinct même lorsqu'il a la même valeur.
- **Assiette IRSA** : revenu net imposable tronqué à la centaine inférieure par `MoneyRoundingPolicy.irsaTaxBaseLowerHundred`.
- **IRSA calculé** : somme progressive par tranches, avant arrondi fiscal, réduction et minimum.
- **IRSA dû** : IRSA calculé arrondi à la dizaine supérieure, réduction familiale soustraite, puis minimum appliqué.
- **Net à payer** : revenu net avant impôt moins IRSA/ISI, plus éléments nets positifs, moins retenues nettes, puis arrondi final.

## Audit IRSA

| Étape | Propriétaire | Entrée | Sortie | Échelle / mode |
|---|---|---|---|---|
| Configuration datée | `PayrollLegalContextResolver` + `IrsaBracketResolver` | date de fin de période | barème, minimum, réduction, HS | Valeurs BDD datées |
| Revenu imposable | `PayrollExecutionEngine` | revenu avant impôt, HS exonérées | revenu net imposable | 2 décimales métier |
| Assiette | `IrsaCalculator` | revenu net imposable | assiette IRSA | centaine inférieure (`DOWN`) |
| Barème | `IrsaCalculator` | assiette, tranches | IRSA calculé | 2 décimales `HALF_UP` |
| Arrondi fiscal | `MoneyRoundingPolicy` | IRSA calculé | IRSA avant réduction | dizaine supérieure (`CEILING`) |
| Réduction/minimum | `IrsaCalculator` | IRSA arrondi, personnes à charge, minimum daté | IRSA dû | réduction puis minimum |

`StcService` appelle le même `IrsaCalculator`. SAFI consomme les champs persistés `base_irsa_arrondie`, `irsa_brut`, `reduction_enfants`, `irsa_net`; il ne recalcule pas l'impôt. Le frontend n'implémente aucun barème fiscal.

## Cotisations sociales et congés

- CNaPS et santé portent assiette, taux et parts salariale/patronale dans des lignes distinctes. OSTIE et OMSI sont des organismes de la cotisation santé, pas des concepts concurrents.
- Le congé est ventilé en `DEDUCTION_CONGE_BASE` et `INDEMNITE_CONGE_PAYE`; l'impact net est fourni par l'explication backend.
- L'absence est `RETENUE_ABSENCE` avec quantité horaire, taux et montant. Elle ne reçoit jamais la déduction congé moderne.

## DTO et explicabilité

- `BulletinPaie` expose `revenuNetAvantImpot`, `revenuNetImposable`, `assietteIrsa`, `irsaCalcule`, `irsaDu`, `netAvantArrondi`, `netAPayer`.
- Les getters historiques (`salaireNetImposable`, `baseIrsaArrondie`, `irsaBrut`, `irsaNet`) restent disponibles pour compatibilité; `salaireNetImposable` conserve provisoirement la valeur historique `NETI` et ne doit plus être utilisé dans les nouveaux consommateurs.
- `BulletinPaieComplet.explicationFiscalite` transporte tous les paliers fiscaux et interdit un recalcul frontend.
- `explicationFiscalite.detailsTranches` expose borne basse/haute, taux, montant taxable et impôt produit sans recalcul frontend.

## RAKETAKA

Le classeur visible fourni par la mission donne base 625 000, brut 632 708,46, `NETI` 620 054,29, IRSA 31 500, CALL perso 21 470 et net final 567 080. Cette chaîne se réconcilie arithmétiquement. En revanche, `ABSENCES = 240 052,06` est une colonne surchargée : elle ne permet pas d'isoler avec certitude absence réelle et déduction congé. Le SIRH moderne exige les deux événements sources séparés; reproduire cette colonne comme retenue d'absence serait une erreur.

Le Golden Master moderne déjà présent utilise un autre snapshot RAKETAKA (base 600 000, déduction congé 10 000, indemnité 8 985,45, brut 633 177,20, revenu avant impôt 620 513,66, IRSA 32 100, net 582 080). Les deux scénarios ne doivent pas être fusionnés.

## Export mapping

| Sortie | Source canonique |
|---|---|
| Bulletin / cockpit | `BulletinPaie` et lignes |
| SAFI montant imposable | `base_irsa_arrondie` / `assietteIrsa` |
| SAFI impôt correspondant | `irsa_brut` / `irsaCalcule` |
| SAFI impôt dû | `irsa_net` / `irsaDu` |
| DNTS | lignes de cotisations sociales |
| Virement | `net_a_payer` |

## Tests

- `IrsaBoundaryCertificationTest` couvre seuil −1, seuil, seuil +1, chaque frontière, tranche intermédiaire, multi-tranches, haut revenu, centaine inférieure, réduction 0/1/2/N, minimum et demi-centimes.
- Les suites existantes couvrent décembre 2025, janvier/février 2026, mars 2026+ et septembre 2026, ainsi que HS 0/10/20/20h01/21/30 et populations éligible/non éligible.
- Tests ciblés paie : PASS — 247 tests, 0 échec, 0 erreur.
- Tests backend complets : PASS — 696 tests, 0 échec, 0 erreur, 0 ignoré.
- Frontend Vitest : PASS — 10 fichiers, 74 tests.
- Build frontend : PASS — 2 133 modules transformés.
- Playwright isolé, cycle pointage : PASS — 3 tests.
- Playwright complet, première exécution : PASS — 27 tests.
- Playwright complet après correction d'isolation et nouvelle initialisation : PASS — 27 tests.

## Migration des expected IRSA historiques

| Ancien expected | Ancienne formule | Formule correcte | Nouvel expected | Source / règle | Test de remplacement |
|---:|---|---|---:|---|---|
| 2 499,95 | `(400000 - 350001) × 5 %` | `(400000 - 350000) × 5 %` | 2 500,00 | Bornes continues du barème daté | `IrsaBoundaryCertificationTest` + `PayrollFormulaCharacterizationTest` |
| 12 499,85 | perte de 1 Ar à chaque changement de tranche | somme progressive sur intervalles continus | 12 500,00 | `bareme_irsa` normalisé | `IrsaBoundaryCertificationTest` + `PayrollFormulaCharacterizationTest` |
| 27 499,70 | bornes `350001/400001/500001` | bornes `350000/400000/500000` | 27 500,00 | CGI art. 01.03.16, configuration datée | `IrsaBoundaryCertificationTest` + `PayrollFormulaCharacterizationTest` |
| 31 499,50 | bornes `+1` cumulées jusqu'à 620 000 | tranches continues jusqu'à l'assiette 620 000 | 31 500,00 | CGI art. 01.03.16, `CENTAINE_INF` | `IrsaBoundaryCertificationTest` + `PayrollFormulaCharacterizationTest` |
| 54 539,50 | calcul progressif avec pertes aux frontières | calcul progressif continu | 54 540,00 | barème daté configuré | `CalculsConformiteIntegraleTest` |

## Décisions fiscales closes

- La zone jusqu'à 350 000 Ar est la première tranche progressive à taux zéro; elle n'est pas un seuil d'exonération court-circuitant le minimum.
- Les minima techniques sont les bornes inférieures exclusives continues (`350000`, `400000`, etc.). La migration normalise les anciennes écritures `+1`.
- `seuil_exoneration_irsa` n'est plus consommé par le moteur; `bareme_irsa` est l'unique source des tranches.
- Source primaire : CGI Madagascar, article 01.03.16; traçabilité détaillée dans `IRSA_REGULATORY_TRACEABILITY.md`.

## Blockers restants

Aucun blocker de certification fiscale ou sémantique identifié.
