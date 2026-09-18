# LIGNÉE COMPLÈTE DES RUBRIQUES DU BULLETIN DE PAIE (PHASE 2A-0)

**Date :** 17 septembre 2026  
**Référence :** Audit Contradictoire du Payroll Core Existant  
**Objectif :** Répondre avec une traçabilité mathématique et technique absolue à la question :  
*"Pourquoi ce montant apparaît-il sur le bulletin de cet employé ?"*

---

## 1. PALIER 1 : GAINS ET ÉLÉMENTS DU SALAIRE BRUT

```text
================================================================================
RUBRIQUE : SALAIRE_BASE
================================================================================
Libellé     : Salaire de Base
Sens / Cat  : GAIN / SALAIRE_BASE
Formule     : Si entrée/sortie en cours de mois ET joursSousContrat > 0 :
                baseContractuelle * joursSousContrat / 30
              Sinon si entrée/sortie en cours de mois ET joursSousContrat == null :
                min(baseContractuelle, tauxHoraire * heuresReelles)
              Sinon :
                baseContractuelle
Quantité    : Jours sous contrat (si proratisé) ou 1.0 (mois complet)
Taux        : Taux journalier trentième (si proratisé) ou base mensuelle
Source      : employe_affectation_carriere.salaire_base
Owner Actuel: PayrollExecutionEngine.calculerSalaireBrut (lignes 161-178)
Snapshot    : Fiché dans bulletin_paie.salaire_base et bulletin_paie_ligne

================================================================================
RUBRIQUE : RETENUE_ABSENCE
================================================================================
Libellé     : Retenue pour Absences Décomptées (Heures réelles / 1/30ème)
Sens / Cat  : RETENUE / SALAIRE_BASE
Formule     : Si modeCalculAbsence == "TRENTIEME" :
                min(salaireBaseLigne, nbJoursAbsence * (baseContractuelle / 30))
              Sinon (mode HEURES_REELLES par défaut) :
                min(salaireBaseLigne, nbHeuresAbsence * tauxHoraire)
              où tauxHoraire = baseContractuelle / 173.33
Quantité    : nbHeuresAbsence (heures décimales) ou nbJoursAbsence (jours)
Taux        : Taux horaire (base / 173.33) ou Taux journalier (base / 30)
Source      : pointage_cumul_periode.heures_absence_retenues (déficit hebdo 40h)
Owner Actuel: PayrollExecutionEngine.calculerSalaireBrut (lignes 180-214)
Snapshot    : pointage_cumul_periode (gelé) -> bulletin_paie_ligne

================================================================================
RUBRIQUE : DEDUCTION_CONGE_BASE
================================================================================
Libellé     : Déduction Salaire Jours de Congé (1/30ème)
Sens / Cat  : RETENUE / SALAIRE_BASE
Formule     : nbJoursConges * (baseContractuelle / 30.0)
Quantité    : nbJoursConges
Taux        : baseContractuelle / 30.0 (Taux journalier trentième légal)
Source      : pointage_cumul_periode.nb_jours_conge
Owner Actuel: PayrollExecutionEngine.calculerSalaireBrut (lignes 216-224)
Snapshot    : pointage_cumul_periode (gelé) -> bulletin_paie_ligne

================================================================================
RUBRIQUE : INDEMNITE_CONGE_PAYE
================================================================================
Libellé     : Indemnité de Congés Payés (Maintien de salaire)
Sens / Cat  : GAIN / SALAIRE_BASE
Formule     : nbJoursConges * tauxJournalierConge
              où tauxJournalierConge est résolu depuis conge_journal_droit
              avec repli automatique sur (baseContractuelle / 30.0)
Quantité    : nbJoursConges
Taux        : tauxJournalierConge (conge_journal_droit ou base/30)
Source      : conge_journal_droit.taux_journalier_conge
Owner Actuel: PayrollExecutionEngine.calculerSalaireBrut (lignes 226-234)
Snapshot    : conge_journal_droit -> bulletin_paie_ligne

================================================================================
RUBRIQUE : HEURES_SUP_30
================================================================================
Libellé     : Heures Supplémentaires (+30%)
Sens / Cat  : GAIN / HEURES_SUP
Formule     : heuresHs30 * tauxHoraire * 1.30
              où heuresHs30 provient de la sommation des QualifiedTimeBuckets
Quantité    : heuresHs30 (heures décimales converties depuis minutes du snapshot)
Taux        : 130.00 % (1.00 base + 0.30 majoration légale résolue)
Source      : pointage_snapshot_bucket (tier OVERTIME_30)
Owner Actuel: OvertimePayCalculator.calculate -> PayrollExecutionEngine (l. 239-245)
Snapshot    : pointage_snapshot_bucket (gelé) -> bulletin_paie_ligne

================================================================================
RUBRIQUE : HEURES_SUP_50
================================================================================
Libellé     : Heures Supplémentaires (+50%)
Sens / Cat  : GAIN / HEURES_SUP
Formule     : heuresHs50 * tauxHoraire * 1.50
Quantité    : heuresHs50 (heures décimales converties depuis minutes du snapshot)
Taux        : 150.00 % (1.00 base + 0.50 majoration légale résolue)
Source      : pointage_snapshot_bucket (tier OVERTIME_50)
Owner Actuel: OvertimePayCalculator.calculate -> PayrollExecutionEngine (l. 247-253)
Snapshot    : pointage_snapshot_bucket (gelé) -> bulletin_paie_ligne

================================================================================
RUBRIQUE : MAJORATION_NUIT
================================================================================
Libellé     : Majoration Travail de Nuit (+30%)
Sens / Cat  : GAIN / HEURES_SUP
Formule     : heuresNuit * tauxHoraire * tauxNuit
              (tauxNuit = 0.30 si HABITUEL / OCCASIONNEL selon ref_taux_majoration)
Quantité    : heuresNuit (converties depuis minutes du snapshot, intervalle 22h-05h)
Taux        : 30.00 % (majoration seule de sujétion)
Source      : pointage_snapshot_bucket (night_work_type qualifié RH)
Owner Actuel: OvertimePayCalculator.calculate -> PayrollExecutionEngine (l. 255-261)
Snapshot    : pointage_snapshot_bucket (gelé) -> bulletin_paie_ligne

================================================================================
RUBRIQUE : HEURES_DIMANCHE
================================================================================
Libellé     : Majoration Travail Dimanche (+40%)
Sens / Cat  : GAIN / HEURES_SUP
Formule     : heuresDimanche * tauxHoraire * tauxDimanche (taux = 0.40)
Quantité    : heuresDimanche (heures travaillées le dimanche civil)
Taux        : 40.00 % (ou taux combiné selon politique de collision légale Art. 3)
Source      : pointage_snapshot_bucket (day_special_type SUNDAY)
Owner Actuel: OvertimePayCalculator.calculate -> PayrollExecutionEngine (l. 263-271)
Snapshot    : pointage_snapshot_bucket (gelé) -> bulletin_paie_ligne

================================================================================
RUBRIQUE : MAJORATION_FERIE
================================================================================
Libellé     : Majoration Travail Jour Férié (+100%)
Sens / Cat  : GAIN / HEURES_SUP
Formule     : heuresFerie * tauxHoraire * 1.00
Quantité    : heuresFerie (heures travaillées un jour férié chômé légal)
Taux        : 100.00 %
Source      : pointage_snapshot_bucket (day_special_type PUBLIC_HOLIDAY)
Owner Actuel: OvertimePayCalculator.calculate -> PayrollExecutionEngine (l. 273-279)
Snapshot    : pointage_snapshot_bucket (gelé) -> bulletin_paie_ligne

================================================================================
RUBRIQUE : PRIME_PERFORMANCE
================================================================================
Libellé     : Prime de Performance (Proratisée [- Pénalité Retard/Congé])
Sens / Cat  : GAIN / PRIME
Formule     : primeBase * (nbJoursPresents / nbJoursOuvrables) * (1 - malusApplicable)
              malusApplicable = 25% si retards > 70mn ET congés > 1j
Quantité    : Ratio de présence (%)
Taux        : Taux effectif après malus
Source      : paie_avance_acompte.montant (type PRIME_PERFORMANCE) + pointage
Owner Actuel: PayrollExecutionEngine.calculerSalaireBrut (lignes 282-311)
Snapshot    : paie_avance_acompte (statut IMPUTE_PAIE) -> bulletin_paie_ligne

================================================================================
RUBRIQUE : PRIME_ASSIDUITE
================================================================================
Libellé     : Prime d'Assiduité
Sens / Cat  : GAIN / PRIME
Formule     : primeForfait si totalMinutesRetard < 45 mn, sinon 0.00
Quantité    : 1.0
Taux        : Forfait
Source      : paie_avance_acompte.montant (type PRIME_ASSIDUITE)
Owner Actuel: PayrollExecutionEngine.calculerSalaireBrut (lignes 313-319)
Snapshot    : paie_avance_acompte (statut IMPUTE_PAIE) -> bulletin_paie_ligne

================================================================================
RUBRIQUE : PRIME_ANCIENNETE
================================================================================
Libellé     : Prime d'Ancienneté (X ans)
Sens / Cat  : GAIN / PRIME
Formule     : baseContractuelle * min(anneesAnciennete, 20) * 0.01
              (applicable uniquement si ancienneteMois >= 36)
Quantité    : Années révolues d'ancienneté
Taux        : 1% par an (max 20%)
Source      : contrat_travail.date_debut / employe.date_declaration
Owner Actuel: PayrollExecutionEngine.calculerSalaireBrut (lignes 321-330)
Snapshot    : bulletin_paie.anciennete_mois -> bulletin_paie_ligne

================================================================================
RUBRIQUE : PRIME_EXCEPTIONNELLE
================================================================================
Libellé     : Prime Exceptionnelle [: motif]
Sens / Cat  : GAIN / PRIME
Formule     : Montant fixe approuvé
Quantité    : 1.0
Taux        : Montant fixe
Source      : paie_avance_acompte.montant (type PRIME_EXCEPTIONNELLE)
Owner Actuel: PayrollExecutionEngine.calculerSalaireBrut (lignes 332-340)
Snapshot    : paie_avance_acompte (statut IMPUTE_PAIE) -> bulletin_paie_ligne

================================================================================
RUBRIQUE : REGULARISATION_PAIE
================================================================================
Libellé     : Régularisation Congés (Restitution Absence) [: motif]
Sens / Cat  : GAIN / SALAIRE_BASE
Formule     : Montant fixe régularisé
Quantité    : 1.0
Taux        : Montant fixe
Source      : paie_avance_acompte.montant (type REGULARISATION_PAIE / CONGE)
Owner Actuel: PayrollExecutionEngine.calculerSalaireBrut (lignes 342-350)
Snapshot    : paie_avance_acompte (statut IMPUTE_PAIE) -> bulletin_paie_ligne

================================================================================
RUBRIQUE : AUTIMP
================================================================================
Libellé     : Autres Gains Imposables (AUTIMP)
Sens / Cat  : GAIN / PRIME
Formule     : Montant fixe saisi
Quantité    : 1.0
Taux        : Montant fixe
Source      : paie_avance_acompte.montant (type AUTIMP)
Owner Actuel: PayrollExecutionEngine.calculerSalaireBrut (lignes 352-357)
Snapshot    : paie_avance_acompte (statut IMPUTE_PAIE) -> bulletin_paie_ligne
```

---

## 2. PALIER 2 : COTISATIONS SOCIALES ET CHARGES

```text
================================================================================
RUBRIQUE : COTISATION_CNAPS_SAL
================================================================================
Libellé     : Cotisation CNaPS Salariale (1%)
Sens / Cat  : RETENUE / COTISATION_SALARIALE
Formule     : min(totalGainsBruts, plafondCnaps) * tauxCnapsSalarial (1%)
              plafondCnaps = 8 * SME National (262 680 * 8 = 2 101 440 Ar)
Base Calcul : Assiette plafonnée CNaPS
Taux        : 1.00 %
Source      : paie_config_legale.sme_national / ref_type_cotisation
Owner Actuel: PayrollExecutionEngine.calculerCotisationsSociales (lignes 396-405)
Snapshot    : bulletin_paie.total_cotisations_salariales -> bulletin_paie_ligne

================================================================================
RUBRIQUE : COTISATION_CNAPS_PAT
================================================================================
Libellé     : Cotisation CNaPS Patronale (13%)
Sens / Cat  : PATRONAL / COTISATION_PATRONALE
Formule     : min(totalGainsBruts, plafondCnaps) * 13%
Base Calcul : Assiette plafonnée CNaPS
Taux        : 13.00 %
Source      : paie_config_legale / ref_type_cotisation
Owner Actuel: PayrollExecutionEngine.calculerCotisationsSociales (lignes 402)
Snapshot    : bulletin_paie.total_cotisations_patronales -> bulletin_paie_ligne

================================================================================
RUBRIQUE : COTISATION_FMFP_PAT
================================================================================
Libellé     : Cotisation FMFP Patronale (1%)
Sens / Cat  : PATRONAL / COTISATION_PATRONALE
Formule     : totalGainsBruts * 1% (non plafonnée)
Base Calcul : totalGainsBruts
Taux        : 1.00 %
Source      : paie_config_legale / ref_type_cotisation
Owner Actuel: PayrollExecutionEngine.calculerCotisationsSociales (lignes 410)
Snapshot    : bulletin_paie.total_cotisations_patronales -> bulletin_paie_ligne

================================================================================
RUBRIQUE : OSTIE_SAL / AMIT_SAL (Organisme Sanitaire Médical)
================================================================================
Libellé     : Cotisation Médicale Salariale (OSTIE / Organe de rattachement)
Sens / Cat  : RETENUE / COTISATION_SALARIALE
Formule     : assietteSante * tauxSanteSalarial (1%)
              assietteSante = plafonnée si est_plafonne_sante == true, sinon totalGainsBruts
Base Calcul : Assiette santé
Taux        : 1.00 % (résolu par site_cotisation)
Source      : site_cotisation / ref_type_cotisation
Owner Actuel: PayrollExecutionEngine.calculerCotisationsSociales (lignes 433)
Snapshot    : bulletin_paie.total_cotisations_salariales -> bulletin_paie_ligne
```

---

## 3. PALIER 3 : FISCALITÉ ET IMPÔTS

```text
================================================================================
RUBRIQUE : IMPOT_IRSA_BRUT
================================================================================
Libellé     : IRSA Brut (Barème Progressif 6 Tranches)
Sens / Cat  : INFO / IMPOT
Formule     : Barème progressif LF 2026 appliqué sur baseImposable
              baseImposable = totalGainsBruts - cotisationsSalariales (arrondi aux 100 Ar inf.)
              Tranche 1 (0 -> 350 000) : 0%
              Tranche 2 (350 001 -> 400 000) : 5%
              Tranche 3 (400 001 -> 500 000) : 10%
              Tranche 4 (500 001 -> 600 000) : 15%
              Tranche 5 (600 001 -> ... ) : 20%
              Minimum de perception légal : 3 000 Ar
Base Calcul : Salaire Net Imposable
Owner Actuel: IrsaCalculator.calculerIrsa -> PayrollExecutionEngine (l. 490-496)
Snapshot    : bulletin_paie.irsa_brut -> bulletin_paie_ligne

================================================================================
RUBRIQUE : REDUCTION_CHARGE_FAMILLE
================================================================================
Libellé     : Réduction IRSA pour Charges de Famille (X enf.)
Sens / Cat  : INFO / IMPOT
Formule     : nbEnfantsEligibles * 2 000 Ar
Owner Actuel: PayrollExecutionEngine.calculerIrsa (lignes 498-502)
Snapshot    : bulletin_paie.reduction_enfants -> bulletin_paie_ligne

================================================================================
RUBRIQUE : IMPOT_IRSA_NET
================================================================================
Libellé     : IRSA Net à Payer (Retenu à la Source)
Sens / Cat  : RETENUE / IMPOT
Formule     : max(minimumPerception, irsaBrut - reductionEnfants)
Owner Actuel: PayrollExecutionEngine.calculerIrsa (lignes 504-510)
Snapshot    : bulletin_paie.irsa_net -> bulletin_paie_ligne
```

---

## 4. PALIER 4 : INDEMNITÉS NON IMPOSABLES

```text
================================================================================
RUBRIQUE : INDEMNITE_REPAS
================================================================================
Libellé     : Indemnité de Panier (Accord Établissement GALAXAM)
Sens / Cat  : GAIN / INDEMNITE_NON_IMPOSABLE
Formule     : Somme des indemnités journalières validées dans pointage_indemnite_jour
              Repli si absent : nbJoursPresents * forfaitRepasSite (2 000 Ar)
Quantité    : nbJoursRepas
Taux        : 2 000 Ar / jour
Source      : pointage_cumul_periode.montant_repas
Owner Actuel: PayrollExecutionEngine.calculerIndemnitesNonImposables (l. 526-541)
Snapshot    : pointage_cumul_periode (gelé) -> bulletin_paie_ligne

================================================================================
RUBRIQUE : INDEMNITE_TRANSPORT
================================================================================
Libellé     : Indemnité Forfaitaire de Déplacement
Sens / Cat  : GAIN / INDEMNITE_NON_IMPOSABLE
Formule     : pointage_cumul_periode.montant_deplacement
              (Forfait site proratisé sur nbJoursPresents / nbJoursOuvrables)
Quantité    : nbJoursDeplacement
Taux        : Tarif journalier proratisé de référence (ex: 31 200 Ar ou 53 200 Ar / mois)
Source      : pointage_cumul_periode.montant_deplacement
Owner Actuel: PayrollExecutionEngine.calculerIndemnitesNonImposables (l. 543-557)
Snapshot    : pointage_cumul_periode (gelé) -> bulletin_paie_ligne

================================================================================
RUBRIQUE : EXTRA_DEPLACEMENT
================================================================================
Libellé     : Indemnité Extra Déplacement
Sens / Cat  : GAIN / INDEMNITE_NON_IMPOSABLE
Formule     : Montant validé dans paie_avance_acompte
Source      : paie_avance_acompte (type EXTRA_DEPLACEMENT)
Owner Actuel: PayrollExecutionEngine.calculerIndemnitesNonImposables (l. 559-565)
Snapshot    : paie_avance_acompte -> bulletin_paie_ligne

================================================================================
RUBRIQUE : REMBOURSEMENT_NOTE_FRAIS
================================================================================
Libellé     : Remboursement Notes de Frais Validées
Sens / Cat  : GAIN / INDEMNITE_NON_IMPOSABLE
Formule     : Montant total validé
Source      : paie_avance_acompte (type NOTE_DE_FRAIS)
Owner Actuel: PayrollExecutionEngine.calculerIndemnitesNonImposables (l. 567-573)
Snapshot    : paie_avance_acompte -> bulletin_paie_ligne
```

---

## 5. PALIER 5 : RETENUES SUR LE NET ET ACOMPTES

```text
================================================================================
RUBRIQUE : RETENUE_ACOMPTE_15
================================================================================
Libellé     : Acompte de Quinzaine (du 15)
Sens / Cat  : RETENUE / ACOMPTE_AVANCE
Formule     : Montant approuvé dans paie_avance_acompte (plafonné par quotité cessible)
Source      : paie_avance_acompte (type ACOMPTE_15)
Owner Actuel: PayrollExecutionEngine.calculerRetenuesEtAvances (lignes 615-625)
Snapshot    : paie_avance_acompte (statut IMPUTE_PAIE) -> bulletin_paie_ligne

================================================================================
RUBRIQUE : RETENUE_AVANCE_SPECIALE
================================================================================
Libellé     : Avance Spéciale sur Salaire
Sens / Cat  : RETENUE / ACOMPTE_AVANCE
Formule     : Montant approuvé dans paie_avance_acompte (plafonné par quotité cessible)
Source      : paie_avance_acompte (type AVANCE_SPECIALE)
Owner Actuel: PayrollExecutionEngine.calculerRetenuesEtAvances (lignes 645-655)
Snapshot    : paie_avance_acompte (statut IMPUTE_PAIE) -> bulletin_paie_ligne

================================================================================
RUBRIQUE : RETENUE_ECHEANCE_PRET
================================================================================
Libellé     : Échéance Prêt N° X
Sens / Cat  : RETENUE / ACOMPTE_AVANCE
Formule     : Montant d'échéance active prélevé dans la limite de la quotité cessible
Source      : paie_pret_echeance
Owner Actuel: PayrollExecutionEngine.calculerRetenuesEtAvances (lignes 675-688)
Snapshot    : paie_pret_echeance -> bulletin_paie_ligne

================================================================================
RUBRIQUE : AJUSTEMENT_ARRONDI
================================================================================
Libellé     : Ajustement Arrondi Monétaire (Écart de Règlement)
Sens / Cat  : GAIN / AJUSTEMENT_ARRONDI
Formule     : netAPayerArrondi - netAvantArrondi (arrondi à la dizaine d'Ariary supérieure)
Source      : BulletinPaie.arrondirDizaineAriary
Owner Actuel: PayrollExecutionEngine.calculerRetenuesEtAvances (lignes 760-768)
Snapshot    : bulletin_paie.net_avant_arrondi / net_a_payer -> bulletin_paie_ligne
```
