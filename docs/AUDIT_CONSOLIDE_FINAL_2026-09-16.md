# AUDIT CONSOLIDÉ FINAL AVANT IMPLÉMENTATION — GALAXAM SIRH
## Synthèse Décisionnelle, Preuves sur Pièces, Confrontation Critique & Architecture Cible

**Date :** 16 septembre 2026  
**Auteurs :** Synthèse d'Audit Contradictoire (Sonnet 4.6 ↔ Gemini 3.8 Flash)  
**Périmètre :** Moteurs Pointage → Congés → Paie → STC  
**Statut :** 🛑 PLAN CONSOLIDÉ FIGÉ — AUCUNE MODIFICATION DE CODE RÉALISÉE

---

## 1. RÉSUMÉ EXÉCUTIF

Le présent document constitue la **consolidation technique finale** des deux audits indépendants menés sur le référentiel du SIRH GALAXAM. Il étaye formellement chaque décision sur le code actif, les tables PostgreSQL, les flux d'exécution et les règles métier confirmées.

### Conclusions Majeures de la Consolidation

1. **Règle KAJY et Sémantique `NBABS` Réconciliée :**
   Le bug identifié dans `PayrollService.java:870` (`absInjustifiees = jAbs - jCge`) est formellement prouvé. Dans le tableau historique Excel KAJY, `NBABS` désignait le total des journées d'absence tous motifs confondus (y compris congés). L'addition `+ NBCONGE` dans la formule `30 - NBABS + NBCONGE` servait à neutraliser la déduction des congés. Dans le SIRH moderne, `pointage_cumul_periode.nb_jours_absence` ne contient **strictement que les absences non rémunérées**. Soustraire `jCge` de `jAbs` efface indûment les absences injustifiées. La formule canonique cible pour GALAXAM est $D = \max(0, 30 - jAbs)$.
2. **Modèle du Férié Chômé Définitivement Établi (Modèle A — Objectif Réduit) :**
   L'analyse du workflow prouve que GALAXAM utilise un **objectif hebdomadaire réduit** et non un crédit d'heures fictives. Un lundi férié chômé n'ajoute aucune minute travaillée (`workedMinutes = 0`), mais abaisse l'objectif hebdomadaire attendu de 40h à 32h via `CalendrierOuvrableEngine.compterJoursOuvrables`. Si un salarié effectue 33h du mardi au vendredi, il réalise 1h d'HS (+30%) sur son objectif de 32h.
3. **Risque Majeur de Double Comptage des Temps Spéciaux Circonscrit :**
   Dans le moteur actuel (`WeeklyAttendanceCalculator.java:12`), les minutes du dimanche et des fériés travaillés sont incluses dans `workedMinutes`. Les brancher directement sur `OvertimePayCalculator` (qui applique `1 + Majoration`) en maintenant le salaire mensuel fixe non réduit aboutirait à rémunérer l'heure travaillée jusqu'à **300%**. La matrice d'imputation unifiée (Section 11) neutralise ce risque.
4. **Anomalie Frontière de Mois (Semaines Tronquées) Identifiée :**
   Lorsqu'une semaine civile chevauche deux périodes de paie, chaque tronçon applique indépendamment le plafond de 8h d'HS à 30% (`seuilHsTranche1`). Le mécanisme de mémoire de tronçon basé sur `debut_semaine` (déjà présent dans `pointage_semaine_calcule`) permet d'assurer la continuité hebdomadaire sans casser les périodes mensuelles.
5. **Dédoublement Sémantique du « 30 » Sanctuarisé :**
   La convention de proratisation de salaire au trentième calendaire ($Base \times Jours / 30$) et la valorisation légale des congés payés ($Base \times JoursDroit / 30$) sont deux concepts juridiques distincts devant posséder des paramètres indépendants en base de données.

---

## 2. REVALIDATION DES FINDINGS SONNET & GEMINI

| Ref Finding | Description | Statut Initial | Verdict Consolidé | Preuve Code & BDD |
|---|---|:---:|:---:|---|
| **P0-01** | Proratisation entrée/sortie utilise `tauxHoraire × heures` au lieu de `BASE × jours / 30` | Sonnet P0 | **CONFIRMÉ** | `PayrollExecutionEngine.java:163-166` fait `tauxHoraire.multiply(heuresReelles)`. Test `CalculsConformiteIntegraleTest.java:124-137` encode l'erreur. |
| **P0-02** | Heures dimanche et férié travaillé perdues entre Pointage et Paie | Sonnet P0 | **CONFIRMÉ (SOLUTION RÉVISÉE)** | Manque colonnes dans `pointage_cumul_periode`. Solution Sonnet révisée pour empêcher le double comptage (Section 11). |
| **P0-03** | Soustraction `jAbs - jCge` efface les absences injustifiées dans le journal des droits | Gemini P0 | **CONFIRMÉ** | `PayrollService.java:870`. `jAbs` étant déjà net d'absences non payées, `jAbs - jCge` annule les pénalités si des congés sont pris. |
| **P1-01** | Fallback `8.0h/jour` dans conversion absence (`PayrollService`) | Sonnet P1 | **CONFIRMÉ** | `PayrollService.java:567` hardcode `new BigDecimal("8.0")`. |
| **P1-02** | Fallback `8.00` dans `PayrollExecutionEngine` | Sonnet P1 | **CONFIRMÉ** | `PayrollExecutionEngine.java:191` fallback sur `new BigDecimal("8.00")`. |
| **P1-03** | Cotisation santé STC sans filtre de date (`LIMIT 1`) | Sonnet P1 | **CONFIRMÉ (ÉLARGI)** | `StcService.java:410` fait `LIMIT 1`. De plus, STC duplique tout le résolveur social (CNaPS, FMFP, Santé). |
| **P1-04** | TCONGE fallback silencieux `BASE/30` au premier mois | Sonnet P1 | **CONFIRMÉ** | `StcService.java:953`, `PayrollCalculationInput.java:268`. Classé `BUSINESS_RULE_REQUIRED` (BR-02). |
| **P1-05** | Taux horaire STC utilise constante non datée | Sonnet P1 | **CONFIRMÉ** | `StcService.java:150` appelle `DIVISEUR_HEURES_MENSUELLES` statique. |
| **P1-06** | Tranche 1 HS (8h à 30%) dédoublée lors des semaines tronçonnées | Gemini P1 | **CONFIRMÉ** | `PointageSyntheseService.java:506-510`. Seuil de 480 min réappliqué sur chaque morceau de semaine. |
| **P1-07** | Omission de la majoration `DIMANCHE` dans le résolveur légal | Gemini P0 | **CONFIRMÉ** | `PayrollLegalContextResolver.java:54-60` résout HS30, HS50, NUIT, FERIE, mais pas DIMANCHE. |
| **P2-01** | Littéraux `"2.5"` et `"30.0"` hardcodés dans `mettreAJourJournalConge()` | Sonnet P2 | **CONFIRMÉ** | `PayrollService.java:874, 893`. |
| **P2-02** | Multiplicateur CNaPS `8.0` hardcodé | Sonnet P2 | **CONFIRMÉ** | `PayrollExecutionEngine.java:36` et `SocialContributionCalculator.java:15, 19`. |
| **P2-03** | Fuite de `DIVISEUR_HEURES_MENSUELLES` en `public static final` | Sonnet P2 | **CONFIRMÉ** | `PayrollExecutionEngine.java:34`. |
| **P2-04** | `getTauxJournalierTrentieme()` hardcode `"30.0"` | Sonnet P2 | **CONFIRMÉ** | `PayrollCalculationInput.java:256`. |
| **P2-05** | Constructeur `WeeklyAttendanceContext` à 4 arguments dérive faux objectif | Gemini C3 | **CONFIRMÉ** | `WeeklyAttendanceContext.java:21-22` calcule `expectedMinutes / 5`. Faux sur semaines réduites. |

---

## 3. CORRECTIONS APPORTÉES AU RAISONNEMENT INITIAL GEMINI

Le contre-audit précédent a fait l'objet de deux rectifications majeures lors de cette phase de consolidation :
1. **Précision sémantique sur la semaine de 33h avec férié :**
   Le rapport précédent mentionnait de manière informelle « 41h sur base 32h ». Cette formulation était conceptuellement trompeuse. La consolidation établit strictement :
   - Temps réellement travaillé : 33 heures ;
   - Temps théorique attendu (objectif réduit) : 32 heures ;
   - Excédent ouvrant droit aux heures supplémentaires : 1 heure (à +30%).
2. **Clarification de l'assimilation du férié chômé :**
   Le férié chômé n'a pas besoin d'injecter 8 heures fictives dans `workedMinutes`. Le fait que l'objectif soit abaissé à 32 heures produit mathématiquement le maintien intégral du salaire sans générer de déficit ni d'heures supplémentaires indus.

---

## 4. DÉMONSTRATION DU FINDING KAJY (`jAbs` ↔ `jCge`)

### Chaîne de Traçabilité Réelle

```
1. Saisie Pointage :
   - Jour 1 & 2 : Absence injustifiée -> statut pointage_jour = 'ABSENCE_NON_JUSTIFIEE'
   - Jour 3 & 4 : Congé payé validé  -> statut pointage_jour = 'CONGE'
   - Jours 5 à 22 : Présence normale -> statut pointage_jour = 'PRESENT'

2. Agrégation PointageSyntheseService.java:357-376 :
   - 'CONGE' incrémente uniquement conges++
   - 'ABSENCE_NON_JUSTIFIEE' incrémente uniquement absences++
   - Résultat injecté dans pointage_cumul_periode :
     nb_jours_absence = 2
     nb_jours_conge   = 2

3. Transmission Paie :
   - PayrollService.java:565 : absenceDays = 2, leaveDays = 2
   - BulletinPaie : joursAbsence = 2.0, joursConges = 2.0

4. Exécution mettreAJourJournalConge() (PayrollService.java:870) :
   - absInjustifiees = jAbs.subtract(jCge).max(ZERO) = (2.0 - 2.0) = 0.0
   - formule = 30.0 - 0.0 = 30.0
   - droitMois = 2.5 * 30.0 / 30.0 = 2.5 jours acquis !
```

### Conclusion sur le Finding KAJY
* **Classification :** **BUG CONFIRMÉ (Divergence de modèle de données)**.
* **Explication :** La formule KAJY $D = 30 - NBABS + NBCONGE$ s'appliquait à une colonne unique $NBABS$ qui agrégeait l'ensemble des journées non travaillées. Comme le schéma relationnel GALAXAM sépare nativement `nb_jours_absence` (strictement non payées) et `nb_jours_conge` (congés payés), réappliquer la soustraction revient à annuler la pénalité d'absence.
* **Formule Corrigée :**
  $$D = \max\left(0, 30.0 - jAbs\right)$$
  $$JoursDroit = D \times \frac{acquisition\_conge\_mensuel\_jours}{diviseur\_valorisation\_conge\_jours}$$

---

## 5. MODÈLE DE TEMPS ET ANTI-DOUBLE COMPTAGE

Pour garantir qu'aucune heure ne soit rémunérée deux fois ni oubliée, chaque heure effectuée est qualifiée selon la matrice d'imputation suivante :

| Situation de l'heure travaillée | Dans salaire mensuel fixe ? | Inclus dans `workedMinutes` ? | Qualifié HS ? | Majoration Nuit ? | Majoration Dimanche ? | Majoration Férié ? | Rémunération Additionnelle Paie |
|---|:---:|:---:|:---:|:---:|:---:|:---:|---|
| **Heure normale** (Semaine, 8h-17h, $\le 40$h) | ✅ OUI | ✅ OUI | ❌ Non | ❌ Non | ❌ Non | ❌ Non | **0 Ar** (couvert par le salaire fixe) |
| **Heure Sup jour ouvré** ($> 40$h, 1ère tranche) | ❌ NON | ✅ OUI | ✅ HS 30% | ❌ Non | ❌ Non | ❌ Non | **$TH \times 1{,}30$** (Heure + 30%) |
| **Heure Sup jour ouvré** ($> 48$h, 2ème tranche) | ❌ NON | ✅ OUI | ✅ HS 50% | ❌ Non | ❌ Non | ❌ Non | **$TH \times 1{,}50$** (Heure + 50%) |
| **Heure de Nuit normale** ($\le 40$h, 22h-5h) | ✅ OUI | ✅ OUI | ❌ Non | ✅ +30% | ❌ Non | ❌ Non | **$TH \times 0{,}30$** (Majoration seule) |
| **Heure Sup de Nuit** ($> 40$h, 22h-5h) | ❌ NON | ✅ OUI | ✅ HS 30% | ✅ +30% | ❌ Non | ❌ Non | **$TH \times (1 + 0{,}30 + 0{,}30) = TH \times 1{,}60$** |
| **Dimanche normal** (Shift repos décalé, $\le 40$h) | ✅ OUI | ✅ OUI | ❌ Non | ❌ Non | ✅ +40% | ❌ Non | **$TH \times 0{,}40$** (Majoration seule) |
| **Dimanche en dépassement** ($> 40$h de la semaine) | ❌ NON | ✅ OUI | Arbitrage | ❌ Non | ✅ +40% | ❌ Non | **$TH \times 1{,}40$** (ou $TH \times 1{,}70$ si cumul HS) |
| **Férié chômé non travaillé** | ✅ OUI | ❌ NON | ❌ Non | ❌ Non | ❌ Non | ❌ Non | **0 Ar** (maintien intégral de salaire) |
| **Férié travaillé** | ✅ OUI | ⚠️ Arbitré | ❌ Non | ❌ Non | ❌ Non | ✅ +100% | **$TH \times 1{,}00$** (Majoration légale 100%) |

---

## 6. SEMAINE CHEVAUCHANT DEUX PÉRIODES DE PAIE (TRONÇONS HEBDOMADAIRES)

### Problématique
Une semaine civile (du lundi au dimanche) peut être coupée par la clôture mensuelle (ex : lundi 29 au mercredi 30 septembre, puis jeudi 1er au dimanche 4 octobre).
Actuellement, `pointage_semaine_calcule` est partitionné par `id_periode_paie`. Chaque période traite son morceau de semaine avec `firstBandMinutes = 480` (8 heures), réinitialisant indûment le compteur de la tranche 1.

### Solution Architecturale Conforme (Mémoire de Tronçon)
1. **Identité de la Semaine :** La semaine est formellement identifiée par son lundi (`debut_semaine`).
2. **Continuité de la Tranche HS :**
   Lors du calcul du tronçon de la période 2 (octobre), le service recherche s'il existe un tronçon antérieur pour le même `debut_semaine` :
   ```sql
   SELECT heures_hs_30, heures_hs_50 
   FROM pointage_semaine_calcule 
   WHERE id_employe = ? AND debut_semaine = ? AND id_periode_paie != ?
   ```
3. **Plafond résiduel de tranche 1 :**
   $$\text{firstBandMinutes}_{\text{Tronçon 2}} = \max\left(0, 480 - \text{heures\_hs\_30}_{\text{Tronçon 1}} \times 60\right)$$
4. Ainsi, si 6 heures d'HS 30% ont été liquidées sur le tronçon de fin septembre, le tronçon d'octobre n'accordera que 2 heures d'HS 30% avant de basculer automatiquement les heures suivantes en HS 50%.

---

## 7. MATRICE COMPLÈTE DES RÈGLES MÉTIER

| Règle Métier | Source Documentaire | Implémentation Réelle | Modèle BDD | Tests Actuels | Verdict | Action Requise |
|---|---|---|---|---|:---:|---|
| **Base Mensuelle Fixe** | Code du Travail Art. 74 | Respectée si pas d'entrée/sortie | `contrat_travail.salaire_base` | Conforme | **CONFORME** | Maintenir |
| **Prorata Entrée/Sortie** | Règle confirmée §18 | `tauxHoraire × heuresReelles` | Manque colonne convention | Test 1.1 erroné | **NON CONFORME** | Remplacer par $Base \times Jours / 30$ |
| **Convention Prorata** | 30 jours calendaires | Hardcode `"30.0"` dans STC | Non paramétré | STC partiel | **DETTE** | Créer `diviseur_prorata_salaire_jours` |
| **Objectif Journalier** | 8h standard | `seuilHeuresJour` (8h) | `ref_site` (heures) | Biais constructeur DTO | **CORRIGÉ** | Passer 480 min obligatoires |
| **Objectif Semaine Réduite** | 40h - 8h par férié | `joursRequis × 8h` | `ref_calendrier_ferie` | Non testé en pipeline | **CONFORME** | Ajouter test caractérisation |
| **Compensation Hebdo** | 7+9+8+8+8 = 40h | `WeeklyAttendanceCalculator` | `pointage_semaine_calcule` | Validé | **CONFORME** | Maintenir |
| **Heures Dimanche** | Majoration 40% | Calculé, non transmis en Paie | Manque colonnes | Aucun | **NON CONFORME** | Ajouter colonnes + câbler résolveur |
| **Heures Férié Travaillé** | Majoration 100% | Compté en jours, pas en heures | Manque colonne heures | Aucun | **NON CONFORME** | Ajouter colonne `heures_ferie_travaille` |
| **TCONGE Historique** | CumulValo / CumulJours | Formule KAJY avec bug `jAbs-jCge` | `conge_journal_droit` | Aucun | **NON CONFORME** | Corriger soustraction absence |
| **TCONGE Mois 1** | Arbitrage BR-02 | Fallback silencieux `BASE/30` | — | Aucun | **ARBITRAGE** | Rendre explicite via BR-02 |
| **Cotisations STC** | Alignées Paie | Requêtes brutes SQL dupliquées | `site_cotisation` | Partiel | **DETTE MAJEURE** | Déléguer à `PayrollLegalContextResolver` |
| **Plafond CNaPS** | 8× SME dynamique | Hardcode `"8.0"` dans moteur pur | `plafond_cotisation_nb_sme` | Conforme à 8x | **NON CONFORME** | Injecter multiplicateur dans Rules |

---

## 8. ARBITRAGES MÉTIER BLOQUANTS

Avant d'exécuter les phases 2 et 4, les deux arbitrages suivants doivent être tranchés :

### BR-01 : Statut et Rémunération du Dimanche et Férié Travaillé
* **Option A (Majoration d'incommodité seule) :** L'heure travaillée est intégrée dans le contingent hebdomadaire (ouvre droit au repos compensateur légal). Le bulletin de paie ne verse que la prime d'incommodité : $+40\%$ (dimanche) et $+100\%$ (férié).
* **Option B (Rémunération complète de l'heure + Majoration) :** L'heure travaillée est exclue du temps normal hebdomadaire et rémunérée intégralement en sus du salaire mensuel à $140\%$ (dimanche) et $200\%$ (férié).

### BR-02 : Règle du TCONGE au Premier Mois d'Embauche
* **Option A (Trentième forfaitaire légal) :** En l'absence d'historique de droits acquis dans `conge_journal_droit`, le taux journalier de congé est réputé égal à $Base / 30$.
* **Option B (Valorisation sur les gains réels du mois M) :** Le taux est calculé sur le bulletin du mois 1 en cours : $(NETBASE_1 + HS_1) / 30$.
* **Option C (Blocage strict) :** Tout décompte de congé payé au mois 1 est bloqué avec levée d'une exception `TCONGE_INITIAL_MISSING`.

---

## 9. CATALOGUE DE TESTS MINIMUM OBLIGATOIRE (38 TESTS)

### Domaine Pointage & Qualification Hebdomadaire
1. `testSemaineNominale40h` : 5 jours de 8h $\rightarrow$ 0 déficit, 0 HS.
2. `testCompensation7_9_8_8_8` : 7h + 9h + 8h + 8h + 8h = 40h $\rightarrow$ 60 min compensées, 0 déficit, 0 HS.
3. `testDeficitHebdomadaire39h` : 7h + 8h + 8h + 8h + 8h = 39h $\rightarrow$ déficit résiduel 60 min, 0 HS.
4. `testHeuresSupHebdomadaire41h` : 9h + 8h + 8h + 8h + 8h = 41h $\rightarrow$ 0 déficit, 60 min HS 30%.
5. `testFerieChomeObjectifReduit32h` : Lundi férié chômé (0h) + Mar-Ven (8h) $\rightarrow$ total 32h, 0 déficit, 0 HS.
6. `testFerieChomeAvecDepassement33h` : Lundi férié chômé (0h) + Mardi (9h) + Mer-Ven (8h) $\rightarrow$ 33h travaillées, 1h HS 30%.
7. `testCongePleinJourPasDabsence` : 1 jour congé plein + 4 jours 8h $\rightarrow$ objectif 32h, 0 déficit.
8. `testSemainePartielleContrat` : Salarié entré un mercredi $\rightarrow$ objectif tronçon 24h respecté.
9. `testSemaineTronqueeContinuiteTranches` : 6h HS en fin de mois 1 + 6h HS en début de mois 2 $\rightarrow$ total semaine 12h réparti en 8h à 30% et 4h à 50%.
10. `testCompensationInterTroncons` : Déficit sur tronçon 1 compensé par surplus sur tronçon 2 de la même semaine civile.
11. `testFrontiereTrancheHs` : Test aux bornes exactes 479 min, 480 min, 481 min d'HS hebdomadaires.
12. `testCumulHsEtNuit` : Heures de nuit effectuées au-delà de la 40e heure $\rightarrow$ cumul régulier sans double heure de base.
13. `testDimancheTravailleQualification` : Présence dimanche $\rightarrow$ minutes isolées dans `sundayMinutes`.
14. `testFerieTravailleQualification` : Présence jour férié $\rightarrow$ minutes isolées dans `publicHolidayMinutes`.

### Domaine Paie & Rémunération
15. `testAbsenceRetenueExacteHeures` : Retenue absence = $Minutes / 60 \times (Base / 173{,}33)$.
16. `testMoisPleinSalaireBaseIntact` : 0 absence résiduelle $\rightarrow$ Salaire de base contractuel non altéré.
17. `testProrataEntreeCalendaire` : Embauche le 16 d'un mois de 30 jours $\rightarrow$ 15 jours calendaires $\rightarrow 50\%$ du salaire de base.
18. `testProrataSortieCalendaire` : Sortie le 10 d'un mois $\rightarrow 10/30$ du salaire de base.
19. `testChangementSalaireFrontiereMois` : Augmentation au 1er du mois prise en compte sur la période complète.
20. `testReproductibiliteAnciennePaie` : Recalcul d'un bulletin historique avec paramètres datés sans dérive.

### Domaine Congés & TCONGE (KAJY)
21. `testBenchmarkKajyConge` : Validation intégrale du tableau KAJY : $CumulJours \approx 29{,}96$, $CumulValo \approx 324\,385{,}38$, $TCONGE \approx 10\,828{,}49$.
22. `testAbsenceInjustifieeNonAnnuleeParConge` : 2 jours d'absence injustifiée + 2 jours de congés $\rightarrow D = 28$ jours (et non 30).
23. `testApplicationTcongeMoisPrecedent` : Congé pris en mois M valorisé au taux scellé en M-1.
24. `testCongeDansAssietteBrute` : Indemnité de congés payés incluse dans le brut soumis à cotisations et IRSA.
25. `testPremierMoisCongeSansHistorique` : Application stricte de la règle arbitrée BR-02 sans fallback silencieux.
26. `testImmutabiliteJournalCongeCloture` : La clôture de la paie scelle définitivement la ligne dans `conge_journal_droit`.

### Domaine Cotisations Sociales & Fiscalité
27. `testPlafondCnapsDynamique` : Vérification du plafonnement à $SME \times plafond\_cotisation\_nb\_sme$ (dynamique).
28. `testPlafonnementSanteParSite` : Santé plafonnée ou déplafonnée selon `site_cotisation.est_plafonne_sante`.
29. `testResolutionCotisationsHistoriques` : Application stricte des taux valides à la date de référence.
30. `testConfigLegaleAbsenteBloquante` : Absence de tranche IRSA ou SME $\rightarrow$ exception explicite levée.
31. `testCotisationSanteAmbiguëBloquante` : Deux organismes actifs pour un même site $\rightarrow$ rejet strict.

### Domaine Solde de Tout Compte (STC)
32. `testStcProrataConformePaie` : Proratisation de salaire de départ rigoureusement égale à la formule Paie.
33. `testStcCotisationsConformesPaie` : Bulletin STC appliquant les mêmes bases et plafonds que le bulletin mensuel.
34. `testStcIccpAvecTcongeDynamique` : Solde restant de congés valorisé au dernier TCONGE scellé.
35. `testStcApurementAvances` : Avances en attente imputées et soldées sur le reçu STC.
36. `testStcApurementPrets` : Déduction du capital restant dû des prêts dans la limite légale de quotité cessible.
37. `testStcIndemniteLicenciementExoneree` : Indemnité de licenciement non soumise aux cotisations ni à l'IRSA.
38. `testStcRecalculIdempotent` : Réouverture et recalcul STC sans duplication de lignes ni d'imputations.

---

## 10. CRITÈRES D'ACCEPTATION FINALE DU PROJET

1. **Zéro constante métier hardcodée :** Aucun diviseur horaire, plafond, taux ou seuil d'absence ne figure en dur dans le code Java.
2. **100% de tests au vert :** L'intégralité des 38 tests obligatoires ainsi que les tests de régression existants réussissent.
3. **Traçabilité totale :** Chaque bulletin et snapshot est immuable et permet de reproduire les montants au centime d'Ariary près.
4. **Validation de non-régression :** Aucun écart non justifié sur les bulletins de paie de référence.
