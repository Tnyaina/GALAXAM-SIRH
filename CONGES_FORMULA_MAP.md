# CARTOGRAPHIE DES FORMULES DU SYSTÈME DE CONGÉS — GALAXAM SIRH

> **Document de Référence** : Traçabilité intégrale des formules mathématiques et métier.  
> **Objectif** : Permettre à tout développeur ou auditeur de comprendre chaque formule du système de congés sans ambiguïté.

---

## 1. Base d'Activité Mensuelle Proratisée pour Acquisition de Droits

```text
FORMULE
↓
D = min(30, 30 - NBABS + NBCONGE)

SOURCE
↓
Excel "OHATRA KAJY DROITS CONGES-given on sept26.xls", Feuille1, Colonne D
Exemple Cellule D3 : =IF(30-F3+H3>30, 30, 30-F3+H3)

IMPLÉMENTATION ACTUELLE
↓
Dans le SIRH, le Pointage classe séparément les absences injustifiées et les congés.
Formule : 30.0 - AbsencesInjustifiees
Fichier : CongeService.java:57-85 & PayrollService.java:1014-1016

IMPLÉMENTATION CIBLE
↓
LeaveEntitlementCalculator.calculerBaseActiviteProratisee(BigDecimal absencesInjustifiees, BigDecimal diviseur)
Règle : Assure que les congés payés approuvés restent assimilés à du temps effectif (Code du Travail Art. 86).

TEST
↓
KajyLeaveCalculationGoldenMasterUnitTest.java:78-80
Phase2a4aTcongeCharacterizationTest.java:52-55
```

---

## 2. Droits Mensuels à Congés Acquis

```text
FORMULE
↓
S = D × 2,5 / 30

SOURCE
↓
Excel "OHATRA KAJY DROITS CONGES-given on sept26.xls", Feuille1, Colonne S
Exemple Cellule S3 : =D3*2.5/30

IMPLÉMENTATION ACTUELLE
↓
Fichier : CongeCalculateurEngine.java:71-88
Méthode : calculerAcquisitionMensuelleProratisee(joursTrentieme, tauxMensuel, diviseur)
Code : taux.multiply(joursTrentieme.divide(diviseur, 6, HALF_UP)).setScale(4, HALF_UP)

IMPLÉMENTATION CIBLE
↓
LeaveEntitlementCalculator.calculerDroitAcquis(BigDecimal baseActivite)
Maintien du type BigDecimal(6,4) avec HALF_UP.

TEST
↓
KajyLeaveCalculationGoldenMasterUnitTest.java:83-86
Phase2a4aTcongeCharacterizationTest.java:54-60
```

---

## 3. Indemnité Mensuelle de Congé Payé Allouée en Paie (CONGE)

```text
FORMULE
↓
CONGE = NBCONGE × TCONGE

SOURCE
↓
Excel "OHATRA KAJY DROITS CONGES-given on sept26.xls", Feuille1, Colonne J
Exemple Cellule J3 : =I3*H3
Excel "ohatra gita fichiers-23jan26.xlsx", Feuille Explication, Ligne 9 : =H3*I3

IMPLÉMENTATION ACTUELLE
↓
Fichier : PayrollExecutionEngine.java:277-282
Méthode : calculerConges(PayrollCalculationInput in, BulletinPaie bp, List<BulletinPaieLigne> lignes)
Code : MoneyRoundingPolicy.amount(nbJoursConges.multiply(tauxJournalierConge))
Génère la rubrique de gain : INDEMNITE_CONGE_PAYE

IMPLÉMENTATION CIBLE
↓
LeaveConsumptionCalculator.calculerIndemniteConge(BigDecimal nbJours, BigDecimal tauxApplicable)

TEST
↓
KajyAppliedTcongeCharacterizationTest.java:85-92
Phase2a4aTcongeCharacterizationTest.java:172-175
```

---

## 4. Retenue sur Salaire de Base pour Congés Pris (1/30ème Contractuel)

```text
FORMULE
↓
DEDUCTION_CONGE_BASE = NBCONGE × (SalaireBase / 30)

SOURCE
↓
Excel "ohatra gita fichiers-23jan26.xlsx", Feuille Fanazavana, Ligne 27 :
"Taux de l’absence : calcul automatique / base actuelle / 30"
Dans KAJY, cette déduction est incluse dans la colonne ABSENCES (G).

IMPLÉMENTATION ACTUELLE
↓
Fichier : PayrollExecutionEngine.java:268-273
Méthode : calculerConges(...)
Code : MoneyRoundingPolicy.amount(nbJoursConges.multiply(tauxJournalierTrentieme))
Génère la rubrique de retenue : DEDUCTION_CONGE_BASE

IMPLÉMENTATION CIBLE
↓
LeaveConsumptionCalculator.calculerDeductionBase(BigDecimal nbJours, BigDecimal salaireBaseContractuel)

TEST
↓
Phase2a4aTcongeCharacterizationTest.java:165-170
PayrollUnitsAndRatesSeparationUnitTest.java:110-125
```

---

## 5. Salaire de Base Net (NET BASE)

```text
FORMULE
↓
NET_BASE = BASE - ABSENCES + CONGE

SOURCE
↓
Excel "OHATRA KAJY DROITS CONGES-given on sept26.xls", Feuille1, Colonne K
Exemple Cellule K3 : =E3-G3+J3

IMPLÉMENTATION ACTUELLE
↓
Fichier : PayrollService.java:1023-1034
Reconstitution du solde de base net à partir des rubriques du bulletin de paie :
SALAIRE_BASE - RETENUE_ABSENCE - DEDUCTION_CONGE_BASE + INDEMNITE_CONGE_PAYE

IMPLÉMENTATION CIBLE
↓
LeaveEntitlementValuationCalculator.calculerNetBase(BigDecimal salaireBase, BigDecimal retenueAbsence, BigDecimal deductionConge, BigDecimal indemniteConge)

TEST
↓
KajyLeaveCalculationGoldenMasterUnitTest.java:94-97
Phase2a4aTcongeCharacterizationTest.java:120-137
```

---

## 6. Assiette Totale de Valorisation Mensuelle des Droits (Base Totale)

```text
FORMULE
↓
R = NET_BASE + HS  (avec AUTIMP formellement exclu)

SOURCE
↓
Excel "OHATRA KAJY DROITS CONGES-given on sept26.xls", Feuille1, Colonne R
Formule : =P3+Q3 (où P3 = K3 = NET_BASE et Q3 = L3 = HS)
Preuve : La colonne M (AUTIMP) n'est jamais additionnée dans R.

IMPLÉMENTATION ACTUELLE
↓
Fichier : PayrollService.java:1036
Code : BigDecimal baseTotale = netBase.add(mntHs).max(BigDecimal.ZERO);

IMPLÉMENTATION CIBLE
↓
LeaveEntitlementValuationCalculator.calculerAssietteValorisation(BigDecimal netBase, BigDecimal heuresSup)

TEST
↓
KajyLeaveCalculationGoldenMasterUnitTest.java:101-105
Phase2a4aTcongeCharacterizationTest.java:104-144
```

---

## 7. Valorisation Mensuelle des Droits Acquis

```text
FORMULE
↓
T = R × S / 30 = BaseTotale × DroitsAcquis / 30

SOURCE
↓
Excel "OHATRA KAJY DROITS CONGES-given on sept26.xls", Feuille1, Colonne T
Exemple Cellule T3 : =S3*R3/30
Pour un mois complet (S = 2,5) : T = BaseTotale / 12 (exactement le 1/12ème légal)

IMPLÉMENTATION ACTUELLE
↓
Fichier : CongeCalculateurEngine.java:107-109
Méthode : calculerValorisationDroitMensuel(baseTotale, joursDroit)
Fichier : PayrollService.java:1037
Code : baseTotale.multiply(droitMois).divide(new BigDecimal("30.0"), 2, RoundingMode.HALF_UP)

IMPLÉMENTATION CIBLE
↓
LeaveEntitlementValuationCalculator.calculerValorisationMensuelle(BigDecimal baseTotale, BigDecimal joursDroit)

TEST
↓
KajyLeaveCalculationGoldenMasterUnitTest.java:108-114
Phase2a4aTcongeCharacterizationTest.java:42-47
```

---

## 8. Cumul Progressif des Droits Acquis et Valorisations

```text
FORMULE
↓
U_m = U_{m-1} + S_m
V_m = V_{m-1} + T_m

SOURCE
↓
Excel "OHATRA KAJY DROITS CONGES-given on sept26.xls", Feuille1, Colonnes U et V
Exemples : U4 = U3 + S4, V4 = V3 + T4

IMPLÉMENTATION ACTUELLE
↓
Fichier : PayrollService.java:1064-1084
Calcul du cumul borné à la période de référence active (conge_periode_reference)
via requête SQL d'agrégation sur conge_journal_droit.

IMPLÉMENTATION CIBLE
↓
LeaveBalanceLedgerService.mettreAJourCumulsPeriode(int idPeriodeReference, int idEmploye)

TEST
↓
KajyLeaveCalculationGoldenMasterUnitTest.java:121-133
TcongeBoundedReferenceIntegrationTest.java:100-130
```

---

## 9. Taux Journalier Moyen de Clôture Annuelle (TCONGE de Clôture)

```text
FORMULE
↓
W14 = V14 / U14 = CumulValorisation12Mois / CumulDroits12Mois

SOURCE
↓
Excel "OHATRA KAJY DROITS CONGES-given on sept26.xls", Feuille1, Cellule W14
Valeur vérifiée : 324 385,3763 / 29,9566667 = 10 828,4870 Ar/jour (Cellule X14 : "taux journalier")

IMPLÉMENTATION ACTUELLE
↓
Fichier : CongeCalculateurEngine.java:115-120
Méthode : calculerTauxJournalierMoyen(cumulValorisation, cumulDroits)
Code : cumulValorisation.divide(cumulDroits, 2, RoundingMode.HALF_UP)
Fichier : CongeTauxApplicableService.java:489-535
Méthode : cloturerPeriodeReference(...)

IMPLÉMENTATION CIBLE
↓
LeaveEntitlementValuationCalculator.calculerTauxMoyenCloture(BigDecimal cumulValo, BigDecimal cumulDroits)

TEST
↓
KajyLeaveCalculationGoldenMasterUnitTest.java:136-148
KajyAppliedTcongeCharacterizationTest.java:76-83
```

---

## 10. Résolution As-Of du Taux Journalier Applicable (TCONGE)

```text
FORMULE
↓
TCONGE_applicable(t) = SELECT taux_journalier FROM conge_taux_applicable 
                      WHERE id_employe = ? AND date_effet <= t AND (date_fin IS NULL OR date_fin > t)
Fallback sans historique : SalaireBase / 30

SOURCE
↓
Excel "ohatra gita fichiers-23jan26.xlsx", Feuille Fanazavana, Ligne 26 :
"Taux Congés : taux journalier droit congés - saisie manuelle suivant tableau de suivi des congés"

IMPLÉMENTATION ACTUELLE
↓
Fichier : CongeTauxApplicableService.java:33-135
Méthode : resoudreTauxCongeApplicable(idEmploye, dateReference, salaireBaseFallback, c)
Priorité :
1. Taux valide dans conge_taux_applicable
2. Dernier taux dans conge_journal_droit (données antérieures)
3. Initialisation contractuelle SalaireBase / 30

IMPLÉMENTATION CIBLE
↓
LeaveTcongeResolverService.resoudreTauxApplicable(...)

TEST
↓
CongeTauxApplicableResolverIntegrationTest.java:50-140
TcongeP2AntiSilentFallbackIntegrationTest.java:40-90
```

---

## 11. Bonification d'Ancienneté Légale

```text
FORMULE
↓
Jours_bonifies = floor(AncienneteAnnees / 5) × 1 jour

SOURCE
↓
Code du Travail Malagasy, Loi n° 2003-044, Art. 85 alinéa 2 :
"Cette durée est augmentée d'un jour ouvrable par période de cinq années de services continus ou non chez le même employeur."

IMPLÉMENTATION ACTUELLE
↓
Fichier : CongeCalculateurEngine.java:125-135
Méthode : calculerBonificationAnciennete(anneesAnciennete, seuilMin, palierAnnees, joursParPalier)
Code : (anneesAnciennete / palierAnnees) * joursParPalier

IMPLÉMENTATION CIBLE
↓
LeaveEntitlementCalculator.calculerBonificationAnciennete(int annees)

TEST
↓
CongeServiceIntegrationTest.java:450-480
```

---

## 12. Solde de Tout Compte (Liquidation des Congés)

```text
FORMULE
↓
IndemniteCompensatrice = SoldeJoursRestants × TCONGE_applicable(DateDepart)

SOURCE
↓
Code du Travail Malagasy, Loi n° 2003-044, Art. 91 :
"En cas de rupture ou d'expiration du contrat (...) le travailleur a droit à une indemnité compensatrice de congé."

IMPLÉMENTATION ACTUELLE
↓
Fichier : StcService.java:206-220
Code :
TauxCongeResolution tc = congeTauxApplicableService.resoudreTauxCongeApplicable(idEmploye, dateDepart, salaireBase, c);
BigDecimal indemnite = soldeCongesRestant.multiply(tc.getTauxJournalier()).setScale(2, HALF_UP);

IMPLÉMENTATION CIBLE
↓
StcService avec injection directe du LeaveBalanceLedgerService.

TEST
↓
Phase2a1PayrollCoreAlignmentIntegrationTest.java:150-185
```
