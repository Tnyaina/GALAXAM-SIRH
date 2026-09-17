# MICRO-CONTRE-AUDIT FINAL PHASE 1 : CONTINUITÉ HEBDOMADAIRE 26→25 & PREUVE FINANCIÈRE POINTAGE → PAIE

**Date** : 16 septembre 2026  
**Auteur** : Agent Antigravity (Google DeepMind)  
**Périmètre** : Réconciliation monétaire inter-périodes (26 M-1 → 25 M), continuité financière Pointage → Snapshot → Paie, audit de `WeeklyPriorState` et analyse d'impact sur le salaire net/brut.  
**Statut** : LIVRABLE D'ARBITRAGE — AUCUN CODE PRODUCTION NI MIGRATION MODIFIÉ.

---

## 1. EXECUTIVE SUMMARY

| Critère | Constat du Micro-Audit |
| :--- | :--- |
| **Cohérence Mathématique Hebdomadaire** | **Parfaite (41h faites / 40h attendues = 0 déficit, 1h HS30).** `WeeklyAttendanceCalculator` calcule rigoureusement la semaine unifiée. |
| **Continuité Financière Réelle (Scénario 1 : Déficit M-1 -> Surplus M)** | **DÉFAILLANTE (PERTE SALARIÉ).** M-1 a déduit 2h de salaire de base (`6 923.21 Ar`). En M, ces 2h ont été absorbées en temps pour compenser le déficit, mais la retenue monétaire de M-1 n'est **JAMAIS restituée**. L'employé a travaillé 41h et subit une perte nette de 2h de salaire (`6 923.21 Ar`). |
| **Continuité Financière Réelle (Scénario 2 : Surplus M-1 -> Déficit M)** | **DÉFAILLANTE (PERTE EMPLOYEUR).** M-1 a payé 3h d'HS (+30% = `13 500.26 Ar`). En M, le déficit de 2h ramène la semaine à 1h d'HS nette. M paie 0 HS et 0 retenue. Le trop-perçu de 2h d'HS (`9 000.17 Ar`) payé en M-1 n'est **JAMAIS récupéré**. |
| **Continuité Financière Réelle (Scénario 3 : Tranches HS 6h + 6h)** | **PARFAITE.** 6h HS30 en M-1 (`27 000.52 Ar`) + 2h HS30 et 4h HS50 en M (`29 769.80 Ar`) = `56 770.32 Ar`, exactement égal aux 8h HS30 + 4h HS50 attendues. |
| **Clôture de M-1 avec Semaine Incomplète** | **AUTORISÉE (`OUI`).** Le workflow permet formellement de clôturer M-1 dès le 25 sans attendre l'achèvement de la semaine civile. |
| **Mécanisme de Régularisation Automatique** | **INEXISTANT.** Aucune rubrique, colonne ou passerelle automatique ne transmet de delta financier inter-périodes entre Pointage et Paie. |
| **VERDICT FINAL** | **`PARTIAL_FIX`** (`WeeklyPriorState` corrige mathématiquement la semaine mais pas les montants monétaires déjà consommés par la Paie). |

---

## 2. QUESTION AUDITÉE

> **« Où les 120 minutes retenues en M-1 sont-elles financièrement régularisées en M ? »**

Le moteur `WeeklyAttendanceCalculator` calcule que pour une semaine débutée le lundi 24 (7h) et mardi 25 (7h) en M-1, puis poursuivie du mercredi 26 au vendredi 28 (3 × 9h = 27h) en M :
* Total travaillé = 14h + 27h = 41h (pour 40h attendues).
* Déficit final de la semaine = 0 min.
* Heures supplémentaires nettes de la semaine = 60 min.

En période M, la quote-part incrémentale retournée est :
* `incrementalAbsence = max(0, totalAbsence - priorDeficit) = max(0, 0 - 120) = 0 min`.
* `incrementalGrossOvertime = max(0, totalGrossOvertime - priorOvertime) = max(0, 60 - 0) = 60 min`.

**Problème financier fondamental** :  
En M-1, le bulletin a été calculé et clôturé avec une retenue sur salaire de 2 heures (`RETENUE_ABSENCE`).  
En M, le bulletin calcule 1 heure d'heures supplémentaires (`HEURES_SUP_30`) et 0 retenue d'absence.  
**À aucun moment les 2 heures de salaire prélevées en M-1 ne sont réinjectées ou remboursées sur le bulletin de paie M.**

---

## 3. WORKFLOW RÉEL DE CLÔTURE DE PÉRIODE

### Peut-on clôturer la Paie M-1 le 25 alors que la semaine civile n'est pas terminée ?
**VERDICT : `OUI`.**

Dans [PayrollPeriodWorkflowService.java](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/paie/workflow/PayrollPeriodWorkflowService.java#L468-L475) :
```java
case CLOSE -> {
    if (period.endDate().isAfter(LocalDate.now(BUSINESS_ZONE))) {
        throw failure(SirhErrorCode.PAYROLL_PERIOD_PREREQUISITE_BLOCKED,
                "Clôture impossible avant la fin de la période (" + period.endDate() + ").",
                Map.of("periodEndDate", period.endDate()));
    }
    requireValidatedPayroll(period.id(), connection);
    payrollService.cloturerDonneesPaie(period.id(), user.userId(), connection);
}
```
* Le contrôle vérifie uniquement que `period.endDate()` (le 25 du mois) est atteinte ou passée (`!endDate.isAfter(now)`).
* **Aucun contrôle** ne bloque la clôture au motif qu'une semaine civile ISO serait en cours ou incomplète.
* Une fois clôturée (`CLOTUREE`), le snapshot dans `pointage_cumul_periode` passe au statut `'GELE'`/`'CONSOMME'`, et les bulletins de paie deviennent strictement immuables.

---

## 4. ANALYSE DU SCÉNARIO 1 : DÉFICIT M-1 → SURPLUS M

### Paramètres de Test
* Employé standard non-cadre (taux légal 40h/semaine).
* Salaire de base contractuel = `600 000.00 Ar`.
* Diviseur légal = `173.3333 h` → Taux horaire = `3 461.605 Ar`.
* Taux majoration HS30 = `130%` → `4 500.0865 Ar/h`.

### Déroulement Pas à Pas
1. **Période M-1 (24 et 25 nov)** :
   * Attendu = 16h, Fait = 14h → Déficit = 120 min (2h).
   * Snapshot M-1 : `heures_absence_retenues = 2.0`.
   * Paie M-1 : Ligne `RETENUE_ABSENCE` = `2h × 3 461.605 Ar` = **`6 923.21 Ar`**.
   * Salaire Brut M-1 = `600 000.00 - 6 923.21` = **`593 076.79 Ar`**.
2. **Période M (26 au 28 nov)** :
   * Attendu = 24h, Fait = 27h (Surplus brut = 3h = 180 min).
   * `WeeklyPriorState` injecte le déficit antérieur de 120 min.
   * `WeeklyAttendanceCalculator` :
     * `totalExpected = 40h`, `totalWorked = 41h`.
     * `totalCompensated = min(180, 120) = 120 min` (les 2h de déficit sont compensées par 2h de surplus).
     * `totalGrossOvertime = 180 - 120 = 60 min` (1h de HS).
     * `totalAbsence = 0 min`.
   * **Quote-part retournée pour la période M** :
     * `incrementalGrossOvertime = max(0, 60 - 0) = 60 min (1h HS30)`.
     * `incrementalAbsence = max(0, 0 - 120) = 0 min (0h absence)`.
   * Snapshot M : `heures_absence_retenues = 0.0`, `heures_hs_30 = 1.0`.
   * Paie M : Ligne `HEURES_SUP_30` = `1h × 3 461.605 × 1.30` = **`4 500.09 Ar`**.
   * Salaire Brut M = `600 000.00 + 4 500.09` = **`604 500.09 Ar`**.
3. **Bilan Monétaire Consolidé (M-1 + M)** :
   * Brut Total Réellement Versé = `593 076.79 + 604 500.09` = **`1 197 576.88 Ar`**.
   * Brut Économiquement Attendu (2 mois pleins + 1h HS30) = `1 200 000.00 + 4 500.09` = **`1 204 500.09 Ar`**.
   * **Écart / Perte pour le Salarié** = **`-6 923.21 Ar`** (tranche exacte de 2h de salaire).

> **Conclusion Scénario 1** : L'employé a travaillé 41h sur la semaine. Il a subi une retenue de 2h en M-1, et en M ses 2h de travail supplémentaires ont servi à « effacer » ce déficit au niveau horaire sans que les 6 923.21 Ar ne lui soient jamais recrédités. **L'employé est sanctionné deux fois pour le même déficit : une fois en argent (-2h en M-1), une fois en travail (-2h d'HS non payées en M).**

---

## 5. ANALYSE DU SCÉNARIO 2 : SURPLUS M-1 → DÉFICIT M

### Déroulement Pas à Pas
1. **Période M-1 (24 et 25 nov)** :
   * Attendu = 16h, Fait = 19h (Surplus brut = 3h = 180 min).
   * Snapshot M-1 : `heures_hs_30 = 3.0`.
   * Paie M-1 : Ligne `HEURES_SUP_30` = `3h × 3 461.605 × 1.30` = **`13 500.26 Ar`**.
   * Salaire Brut M-1 = `600 000.00 + 13 500.26` = **`613 500.26 Ar`**.
2. **Période M (26 au 28 nov)** :
   * Attendu = 24h, Fait = 22h (Déficit de 2h = 120 min).
   * `WeeklyPriorState` injecte le surplus antérieur de 180 min.
   * `WeeklyAttendanceCalculator` :
     * `totalExpected = 40h`, `totalWorked = 41h`.
     * `totalGrossOvertime = 60 min` (1h de HS sur la semaine).
     * `totalAbsence = 0 min`.
   * **Quote-part retournée pour la période M** :
     * `incrementalGrossOvertime = max(0, 60 - 180) = 0 min`.
     * `incrementalAbsence = max(0, 0 - 0) = 0 min`.
   * Snapshot M : `heures_hs_30 = 0.0`, `heures_absence_retenues = 0.0`.
   * Paie M : Brut = **`600 000.00 Ar`**.
3. **Bilan Monétaire Consolidé (M-1 + M)** :
   * Brut Total Réellement Versé = `613 500.26 + 600 000.00` = **`1 213 500.26 Ar`**.
   * Brut Économiquement Attendu (2 mois pleins + 1h HS30) = **`1 204 500.09 Ar`**.
   * **Trop-perçu Salarié / Perte Employeur** = **`+9 000.17 Ar`** (exactement 2h d'HS30 payées en trop en M-1).

> **Conclusion Scénario 2** : L'employeur a payé 3h d'HS en M-1. Le déficit en M a réduit la performance hebdomadaire à 1h d'HS. M ne verse pas d'HS, mais ne déduit rien non plus. L'employeur ne récupère jamais les 2h d'HS payées par anticipation.

---

## 6. ANALYSE DU SCÉNARIO 3 : CONTINUITÉ DES TRANCHES HS (6H + 6H)

* M-1 : 6h HS30 payées (`27 000.52 Ar`).
* M : 6h HS supplémentaires faites (total semaine = 12h HS).
  * Tranche 1 (HS30) : 8h max → Reste 2h pour M (`9 000.17 Ar`).
  * Tranche 2 (HS50) : 4h pour M (`20 769.63 Ar`).
* Total payé M-1 + M = `27 000.52 + 9 000.17 + 20 769.63` = **`56 770.32 Ar`**.
* Total attendu (8h HS30 + 4h HS50) = **`56 770.32 Ar`**.
* **Écart = `0.00 Ar`**.

> **Conclusion Scénario 3** : Pour la progression des tranches d'heures supplémentaires pures, la formule incrémentale `max(0, total - prior)` fonctionne parfaitement car il n'y a pas de compensation croisée entre deux catégories de rubriques opposées (gain vs retenue).

---

## 7. TRACE DÉTAILLÉE DES COMPOSANTS POINTAGE & PAIE

### 7.1. Pointage : `WeeklyAttendanceCalculator.java`
Lignes 43-47 :
```java
long incrementalCompensated = Math.max(0, totalCompensated - prior.compensatedMinutes());
long incrementalGrossOvertime = Math.max(0, totalGrossOvertime - prior.grossOvertimeMinutes());
long incrementalOvertime30 = Math.max(0, totalOvertime30 - prior.overtime30Minutes());
long incrementalOvertime50 = Math.max(0, totalOvertime50 - prior.overtime50Minutes());
long incrementalAbsence = Math.max(0, totalAbsence - prior.deficitMinutes());
```
* Si `totalAbsence = 0` et `prior.deficitMinutes = 120`, `incrementalAbsence = max(0, -120) = 0`.
* Le delta négatif d'absence (-120 min) est **tronqué à 0** et n'est stocké nulle part.

### 7.2. Snapshot : `pointage_cumul_periode`
Dans [PointageSyntheseService.java](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/pointage/service/PointageSyntheseService.java#L1126-L1129) :
* Les colonnes de la table sont : `heures_hs_30`, `heures_hs_50`, `heures_absence_retenues`.
* **Il n'existe aucune colonne** `heures_absence_restituees` ou `heures_regularisation`.

### 7.3. Contrat d'Interface : `PayrollAttendanceInput.java`
Lignes 36-39 :
```java
if (workedMinutes < 0 || overtime30Minutes < 0 || overtime50Minutes < 0 || nightMinutes < 0
        || unpaidAbsenceMinutes < 0 || lateMinutes < 0) {
    throw new IllegalArgumentException("Une quantité Pointage ne peut pas être négative.");
}
```
* `unpaidAbsenceMinutes` **rejette explicitement toute valeur négative**.
* L'interface interdit par contrat le passage d'une absence négative (restitution).

### 7.4. Moteur de Paie : `PayrollExecutionEngine.java`
Lignes 172-206 :
* Si `unpaidAbsenceMinutes > 0`, calcule `RETENUE_ABSENCE`.
* Si `unpaidAbsenceMinutes == 0`, aucune retenue n'est créée, mais **aucun gain compensateur** n'est injecté.

---

## 8. RECHERCHE D'UN MÉCANISME EXISTANT DE RÉGULARISATION

| Mécanisme Recherché | Présence dans le Code / BDD | Emplacement & Comportement |
| :--- | :---: | :--- |
| `REGULARISATION_PAIE` | **Oui (Manuel uniquement)** | Table `paie_rubrique` (code 34). Alimenté uniquement via saisie manuelle dans `paie_avance_acompte` (`PayrollService.java:651`). Aucun lien avec le pointage. |
| `REGULARISATION_CONGE` | **Oui (Manuel RH)** | Réservé au retour tardif de titre de congé annuel justifié après la clôture. Manuel. |
| Restitution d'absence automatique | **Non** | Inexistant dans tout le repository. |
| Report de solde monétaire inter-périodes | **Non** | Inexistant. |
| Déduction sur période suivante (Trop-perçu HS) | **Non** | Inexistant. |

---

## 9. TABLEAU DE PREUVE MONÉTAIRE CONSOLIDÉ

### Scénario 1 : Déficit M-1 (2h) / Surplus M (3h)
| Élément | M-1 | M | Cumul Réel | Attendu Économique | Écart |
| :--- | --: | --: | --: | --: | --: |
| Heures travaillées | 14.0 h | 27.0 h | 41.0 h | 41.0 h | 0.0 h |
| Heures attendues | 16.0 h | 24.0 h | 40.0 h | 40.0 h | 0.0 h |
| Déficit snapshot | 2.0 h | 0.0 h | 2.0 h | 0.0 h | +2.0 h |
| HS30 snapshot | 0.0 h | 1.0 h | 1.0 h | 1.0 h | 0.0 h |
| HS50 snapshot | 0.0 h | 0.0 h | 0.0 h | 0.0 h | 0.0 h |
| **Retenue absence (Ar)** | **-6 923.21 Ar** | **0.00 Ar** | **-6 923.21 Ar** | **0.00 Ar** | **-6 923.21 Ar** |
| Paiement HS30 (Ar) | 0.00 Ar | +4 500.09 Ar | +4 500.09 Ar | +4 500.09 Ar | 0.00 Ar |
| Paiement HS50 (Ar) | 0.00 Ar | 0.00 Ar | 0.00 Ar | 0.00 Ar | 0.00 Ar |
| Régularisation (Ar) | 0.00 Ar | 0.00 Ar | 0.00 Ar | +6 923.21 Ar | -6 923.21 Ar |
| **Impact Brut Total (Ar)** | **593 076.79 Ar** | **604 500.09 Ar** | **1 197 576.88 Ar** | **1 204 500.09 Ar** | **-6 923.21 Ar** |

### Scénario 2 : Surplus M-1 (3h) / Déficit M (2h)
| Élément | M-1 | M | Cumul Réel | Attendu Économique | Écart |
| :--- | --: | --: | --: | --: | --: |
| Heures travaillées | 19.0 h | 22.0 h | 41.0 h | 41.0 h | 0.0 h |
| Heures attendues | 16.0 h | 24.0 h | 40.0 h | 40.0 h | 0.0 h |
| Déficit snapshot | 0.0 h | 0.0 h | 0.0 h | 0.0 h | 0.0 h |
| HS30 snapshot | 3.0 h | 0.0 h | 3.0 h | 1.0 h | +2.0 h |
| HS50 snapshot | 0.0 h | 0.0 h | 0.0 h | 0.0 h | 0.0 h |
| Retenue absence (Ar) | 0.00 Ar | 0.00 Ar | 0.00 Ar | 0.00 Ar | 0.00 Ar |
| **Paiement HS30 (Ar)** | **+13 500.26 Ar** | **0.00 Ar** | **+13 500.26 Ar** | **+4 500.09 Ar** | **+9 000.17 Ar** |
| Paiement HS50 (Ar) | 0.00 Ar | 0.00 Ar | 0.00 Ar | 0.00 Ar | 0.00 Ar |
| Régularisation (Ar) | 0.00 Ar | 0.00 Ar | 0.00 Ar | -9 000.17 Ar | +9 000.17 Ar |
| **Impact Brut Total (Ar)** | **613 500.26 Ar** | **600 000.00 Ar** | **1 213 500.26 Ar** | **1 204 500.09 Ar** | **+9 000.17 Ar** |

### Scénario 3 : 6h HS M-1 + 6h HS M
| Élément | M-1 | M | Cumul Réel | Attendu Économique | Écart |
| :--- | --: | --: | --: | --: | --: |
| HS30 snapshot | 6.0 h | 2.0 h | 8.0 h | 8.0 h | 0.0 h |
| HS50 snapshot | 0.0 h | 4.0 h | 4.0 h | 4.0 h | 0.0 h |
| Paiement HS30 (Ar) | +27 000.52 Ar | +9 000.17 Ar | +36 000.69 Ar | +36 000.69 Ar | 0.00 Ar |
| Paiement HS50 (Ar) | 0.00 Ar | +20 769.63 Ar | +20 769.63 Ar | +20 769.63 Ar | 0.00 Ar |
| **Impact Brut HS (Ar)** | **+27 000.52 Ar** | **+29 769.80 Ar** | **+56 770.32 Ar** | **+56 770.32 Ar** | **0.00 Ar** |

---

## 10. VÉRIFICATION SECONDAIRE : SOURCE 8H

* **Constat** :
  * `SEUIL_HEURES_JOUR` existe en base dans `parametre_global` ('8').
  * Dans `PointageSyntheseService.java`, l'appel `ParametreGlobal.getBigDecimal(params, "SEUIL_HEURES_JOUR", dureeHebdoLegale.divide(5))` utilise le fallback si le paramètre est absent ou désactivé (**B est VRAI**).
  * Dans `getSemainesPeriode`, `nbOuvrables * 8.0` n'est transmis qu'à l'IHM via le contrôleur REST et n'est pas utilisé lors de la persistance des semaines calculées (**C est VRAI**).
* **Classification** : **B et C sont vraies**.

---

## 11. VÉRIFICATION SECONDAIRE : DIVERGENCE WEEK-END DU 26

Lorsque le 26 tombe un samedi :
1. `getSemainesPeriode` (IHM) saute le 26 et 27 et démarre la Semaine 1 au lundi 28 (affiche 5 semaines).
2. `calculerSyntheseEmploye` génère 6 tronçons, dont un premier tronçon (26 au 27) avec `nbOuvrables = 0`.
3. **Impact monétaire réel** :
   * Si aucun travail n'est presté le week-end : `workedMinutes = 0`, `expectedMinutes = 0` → Impact monétaire = 0.
   * Si du travail est presté le dimanche 27 : il est capturé en `DIMANCHE_TRAVAILLE` et majoré à 40% sans incidence sur les 40h de base.
   * La divergence est donc purement structurelle (nombre de tronçons affichés vs calculés), sans distorsion financière.

---

## 12. COUVERTURE DES TESTS EXISTANTS

* `Phase1PointageHebdomadaireTest` : Vérifie uniquement les minutes retournées par `WeeklyAttendanceCalculator`.
* `GrandWorkflowOhatraE2ETest` : Teste une période où le premier jour (26/12/2025) est un vendredi sans déficit antérieur.
* **Constat formel** :
  ```
  AUCUN TEST EXISTANT NE PROUVE LA RÉCONCILIATION FINANCIÈRE INTER-PÉRIODES ENTRE DEUX BULLETINS DE PAIE CONSÉCUTIFS.
  ```

---

## 13. CAUSE RACINE DE LA FAILLE FINANCIÈRE

1. **Cause Racine** :  
   L'architecture de Phase 1 a traité la continuité inter-périodes comme un problème **purement horaire**, en supposant que la compensation d'heures au niveau de `WeeklyAttendanceCalculator` suffisait.  
   Or, la Paie fonctionne par **clôtures mensuelles étanches** avec impact monétaire immédiat :
   * Un déficit en M-1 est **monétisé en retenue d'argent** (sortie de caisse salariée).
   * Un surplus en M-1 est **monétisé en paiement d'heures supplémentaires** (sortie de caisse employeur).
2. **Invariant Violé** :  
   *« Pour une même prestation hebdomadaire de travail (ex. 41h), la somme monétaire perçue par le salarié sur deux périodes consécutives doit être égale à la somme monétaire d'une semaine non coupée. »*
3. **Classes Concernées** :  
   `WeeklyAttendanceResult.java`, `WeeklyAttendanceCalculator.java`, `PointageSyntheseService.java`, `pointage_cumul_periode`, `PayrollAttendanceInput.java`, `PayrollExecutionEngine.java`.

---

## 14. ARCHITECTURE MINIMALE REQUISE POUR UNE PHASE 1B

Pour résoudre cette anomalie sans violer l'immuabilité de la période M-1 clôturée, le système devra introduire un **mécanisme de régularisation automatique de compensation inter-périodes** :
1. Détecter dans `WeeklyAttendanceCalculator` les heures de déficit antérieures qui ont été compensées en M (`priorCompensatedAbsenceMinutes`).
2. Transmettre cette quantité dans `WeeklyAttendanceResult` et dans `pointage_cumul_periode` (colonne ou métadonnée dédiée).
3. Transmettre dans `PayrollAttendanceInput` un champ `absenceRestitutionMinutes`.
4. Dans `PayrollExecutionEngine`, générer automatiquement sur le bulletin M une ligne de gain :  
   `RESTITUTION_ABSENCE_COMPENSEE` = `absenceRestitutionMinutes / 60 × tauxHoraire`.
5. Symétriquement pour le scénario 2, si un surplus d'HS a été surpayé en M-1, imputer la régularisation en retenue sur M.

---

## 15. VERDICT FINAL DU MICRO-AUDIT

```
================================================================================
                    VERDICT OFFICIEL : PARTIAL_FIX
================================================================================
- WeeklyPriorState corrige mathématiquement la semaine à 40h dans le Pointage.
- MAIS les montants financiers déjà consommés et clôturés par la Paie en M-1
  ne sont pas réconciliés en M, créant une distorsion monétaire réelle
  (perte salariale en cas de déficit compensé, perte patronale en cas de surplus
  résorbé).
- Une Phase 1B (Régularisation Financière Inter-Périodes Pointage -> Paie)
  est indispensable avant de considérer la continuité hebdomadaire comme soldée.
================================================================================
```

**ARRÊT STRICT : En attente des instructions et de l'arbitrage de l'utilisateur.**
