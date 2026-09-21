# Nomenclature canonique du bulletin de paie

## Règle de gouvernance

Les codes ci-dessous décrivent une signification métier, jamais une position de colonne Excel. Les libellés historiques ne sont acceptés que par `PayrollCanonicalConcept.fromLegacyExcelLabel`; ils ne sont pas des concepts du domaine moderne.

| Code canonique | Libellé bulletin | Définition | Unité | Formule / propriétaire | Visibilité |
|---|---|---|---|---|---|
| `BASE_SALARY` | Salaire de base | Rémunération contractuelle mensuelle, proratisée seulement si le contrat ne couvre pas toute la période | Ar | Contrat / `PayrollExecutionEngine` | Standard |
| `ABSENCE_DEDUCTION` | Retenue pour absence non rémunérée | Heures d'absence réellement décomptées × taux horaire, plafonnée à la base | h, Ar/h, Ar | Pointage / moteur | Standard |
| `LEAVE_BASE_DEDUCTION` | Déduction de salaire au titre du congé payé | Jours de congé × salaire contractuel / 30 | j, Ar | Congé / moteur | Détail |
| `PAID_LEAVE_INDEMNITY` | Indemnité de congé payé | Jours de congé × TCONGE applicable | j, Ar/j, Ar | Congé / TCONGE | Standard |
| `OVERTIME_PAY` | Heures supplémentaires | Rémunération des HS par tranche et majoration | h, %, Ar | `OvertimePayCalculator` | Standard |
| `OTHER_TAXABLE_EARNINGS` | Autres gains imposables | Primes et gains inclus dans le brut social et fiscal | Ar | Mouvements validés | Standard |
| `GROSS_SALARY` | Salaire brut | Gains bruts moins retenues qui affectent le brut | Ar | `PayrollExecutionEngine` | Standard |
| `EMPLOYEE_SOCIAL_CONTRIBUTIONS` | Cotisations salariales | Somme des parts salariales CNaPS et santé | Ar | `SocialContributionCalculator` | Standard |
| `NET_INCOME_BEFORE_INCOME_TAX` | Revenu net avant impôt | Salaire brut moins cotisations salariales | Ar | `BulletinPaie.revenuNetAvantImpot` | Détail |
| `TAXABLE_NET_INCOME` | Revenu net imposable | Revenu net avant impôt moins rémunérations fiscalement exonérées, notamment les HS éligibles | Ar | `PayrollExecutionEngine.calculerFiscalite` | Standard |
| `IRSA_TAX_BASE` | Base imposable IRSA | Revenu net imposable tronqué à la centaine inférieure | Ar | `MoneyRoundingPolicy.irsaTaxBaseLowerHundred` | Standard |
| `IRSA_CALCULATED` | IRSA calculé | Somme des montants par tranches avant réduction familiale et minimum | Ar | `IrsaCalculator` | Détail |
| `IRSA_FAMILY_REDUCTION` | Réduction IRSA pour charges de famille | Nombre de personnes éligibles × réduction datée | Ar | Configuration fiscale / `IrsaCalculator` | Détail |
| `IRSA_DUE` | IRSA dû | IRSA arrondi à la dizaine supérieure, moins réduction, puis minimum applicable | Ar | `IrsaCalculator` | Standard |
| `NET_ITEMS` | Éléments hors brut | Indemnités/remboursements nets et retenues personnelles post-fiscalité | Ar | Mouvements validés | Standard |
| `NET_PAYABLE_BEFORE_ROUNDING` | Net à payer avant arrondi | Revenu net avant impôt − impôts + gains nets − retenues nettes | Ar | `PayrollExecutionEngine` | Détail |
| `NET_PAYABLE` | Net à payer | Net à payer avant arrondi, arrondi à la dizaine la plus proche | Ar | `BulletinPaie.arrondirDizaineAriary` | Standard |

## Alias Excel historiques

| Libellé historique | Concept canonique | Observation |
|---|---|---|
| `BASE` | `BASE_SALARY` | Correspondance directe |
| `ABSENCES` | `ABSENCE_DEDUCTION` | L'Excel pouvait aussi y incorporer un effet congé; cet usage n'est pas importé sans ventilation |
| `TCONGE`, `CONGE` | `PAID_LEAVE_INDEMNITY` | `TCONGE` est un taux, `CONGE` un montant; la déduction de base reste distincte |
| `BRUTE` | `GROSS_SALARY` | Faute historique conservée uniquement comme alias |
| `NETI` | `NET_INCOME_BEFORE_INCOME_TAX` | Brut moins cotisations; ce n'est ni le net à payer ni, en présence d'exonérations, le revenu net imposable |
| `Imposable` | `IRSA_TAX_BASE` | La formule Excel `100*INT(NETI/100)` démontre qu'il s'agit de l'assiette arrondie |
| `IRSA Arrondi` | `IRSA_DUE` | Ne doit pas masquer IRSA calculé, réduction et minimum |
| `CALL PERSO` | `PERSONAL_CALL_DEDUCTION` | Retenue nette post-fiscalité |
| `NET À PAYER` | `NET_PAYABLE_BEFORE_ROUNDING` | Dans le classeur source, le champ précédait encore l'arrondi final |
| `ARRONDI NET À PAYER` | `NET_PAYABLE` | Montant réglé |

## Sources et temporalité

- Excel historique : `model/ohatra gita fichiers-23jan26.xlsx`, documenté dans `GALAXAM_EXCEL_SOURCE_MAP.md`.
- Paramètres fiscaux datés : `backend/conception/bdd/02_schema_referentiels_rh.sql` et barèmes de `backend/conception/bdd/06_schema_paie.sql`.
- Règles déjà retenues par le projet : CGI Madagascar, articles cités dans `PAYROLL_FINANCIAL_CERTIFICATION_2026-09-19.md`; périodes `LF_2024_2025`, `LF_2026_JAN_FEB`, `LF_2026`.
- Propriétaires : moteur pour la cascade, `SocialContributionCalculator` pour les cotisations, `IrsaCalculator` pour la liquidation IRSA, `MoneyRoundingPolicy` pour les arrondis.

