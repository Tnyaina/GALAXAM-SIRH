# PHASE 2A-3 — UNIFORMISATION NBCONGE CALENDAIRE & PRÉPARATION DU TCONGE

**Date :** 17 Septembre 2026  
**Auteur :** Antigravity Engine (Pair-Programming avec l'équipe GALAXAM)  
**Document Unique de Référence :** `PHASE_2A_3_TCONGE_PREPARATION_2026-09-17.md`  
**Statut :** VALIDÉ (Verdict : `PHASE_2A_3_TCONGE_PREPARATION_PASS`)

---

## 1. BASELINE & CONTEXTE

Cette phase prolonge les acquis validés de :
- `PHASE_2A_2_CORRECTIONS_2026-09-17.md` (`PHASE_2A_2_CORRECTIONS_PASS`).
- Gardes d'immutabilité post-clôture sur congés validés (`DemandeConge`, `DemandeCongeController`).
- Ajustement non-destructif du solde (`mouvement_solde_conge`).
- Acquisition mensuelle unifiée et idempotente dans le Domaine Congés (`CongeService.enregistrerAcquisitionMensuelle`).
- Résolution dynamique BDD du diviseur (`DIVISEUR_JOURS_CALENDAIRE_CONGE`) et du taux d'acquisition (`acquisition_conge_mensuel_jours`).

### Métriques d'entrée (Phase 2A-2C)
- Tests Backend : 435 tests PASS
- Tests Frontend : 16 tests PASS
- PostgreSQL Réel : PASS
- Vite Build : PASS
- `git diff --check` : PASS

### Métriques de sortie (Phase 2A-3)
- **Tests Backend : 440 tests PASS (+5 tests)**
- **Tests Frontend : 16 tests PASS**
- **PostgreSQL Réel : PASS (Intégration & Scénarios critiques validés)**
- **Vite Build : PASS (392 ms)**
- **`git diff --check` : PASS (Aucune erreur de formatage)**

---

## 2. RG-CONGE-05 : VALORISATION CALENDAIRE ACTUELLE

En vertu des règles métier confirmées par GALAXAM :
- **RG-CONGE-01 :** Le congé annuel est décompté en jours calendaires stricts ($nbJours = dateFin - dateDebut + 1$ pour un congé continu plein).
- **RG-CONGE-02 :** Les week-ends compris dans la période de congé sont débités du solde de congé.
- **RG-CONGE-03 :** Les jours fériés compris dans la période restent débités (aucune restitution automatique).
- **RG-CONGE-04 :** La validation de la demande entraîne immédiatement l'écriture dans le grand-livre de droits : $\text{PRISE} = -nbJoursDecomptes$. Il n'y a pas de statut intermédiaire de "réservation".
- **RG-CONGE-05 (Nouvelle Règle Canonique) :** Pour l'implémentation actuelle Galaxam, le nombre de jours valorisé en paie (`NBCONGE`) est strictement égal au nombre de jours calendaires débités du solde de congé appartenant à la période de paie :
  $$\text{NBCONGE} = \text{Nombre de jours calendaires débités du congé (période de paie)}$$
  $$\text{INDEMNITE\_CONGE\_PAYE} = \text{NBCONGE} \times \text{TCONGE}$$
  $$\text{DEDUCTION\_CONGE\_BASE} = \text{NBCONGE} \times \frac{\text{Salaire de Base}}{30}$$

### Exemples d'application canonique :
1. **Vendredi → Dimanche :**
   - Débit Grand-Livre : $-3$ jours
   - $\text{NBCONGE}$ valorisé en Paie : $3$ jours (Samedi et Dimanche inclus)
   - Si $\text{TCONGE} = 20\,000\text{ Ar/j}$ : $\text{Indemnité} = 60\,000\text{ Ar}$
2. **Lundi → Dimanche :**
   - Débit Grand-Livre : $-7$ jours
   - $\text{NBCONGE}$ : $7$ jours
3. **Lundi → Vendredi (Mercredi férié) :**
   - Débit Grand-Livre : $-5$ jours
   - $\text{NBCONGE}$ : $5$ jours (aucun décompte réduit)
4. **Demi-journée :**
   - Débit Grand-Livre : $-0.5$ jour
   - $\text{NBCONGE}$ : $0.5$ jour

---

## 3. RUNTIME NBCONGE AVANT CORRECTION

Avant cette phase, l'analyse contradictoire du runtime a mis en lumière une anomalie de couplage :
1. Dans `PayrollService.preparerInput(...)` (ligne 643), `in.setAttendanceInput(...)` recevait `rs.getBigDecimal("nb_jours_conge")` extrait de la table `pointage_cumul_periode`.
2. Dans `PointageSyntheseService.java` (lignes 501-510), ce compteur était calculé en filtrant les jours ouvrés non fériés :
   ```java
   if (!CalendrierOuvrableEngine.isWeekend(j.date()) && !feriesChomesTroncon.contains(j.date())) {
       if (j.status() == DailyAttendanceResult.Status.LEAVE) {
           joursCongeTroncon += 1.0;
       }
   }
   ```
3. **Conséquence :** Pour un congé posé du Vendredi au Dimanche, le solde de l'employé était débité de **3 jours calendaires**, mais le bulletin de paie recevait `nbJoursConges = 1` depuis le snapshot Pointage. L'employé perdait 3 jours de droits mais n'était indemnisé que pour 1 jour !

---

## 4. CORRECTION & CENTRALISATION DE NBCONGE

### Propriétaire Métier Canonique
La décision de valorisation est désormais centralisée dans le Domaine Congés, sans aucune prolifération de classes :
- **Classe Propriétaire :** `mg.galaxam.sirh.model.conge.service.CongeService`
- **Méthode Canonique :**
  ```java
  public BigDecimal calculerNbJoursCongeValorises(Integer idEmploye, LocalDate dateDebutPeriode, LocalDate dateFinPeriode, Connection c) throws Exception
  ```
- **Moteur Unitaire délégué :**
  `mg.galaxam.sirh.model.conge.engine.CongeCalculateurEngine.calculerNbJoursCongeValorises(LocalDate dateDebut, LocalDate dateFin, boolean estDemiJournee)`

### Commentaire Obligatoire pour Évolution Future
La méthode est munie du commentaire contractuel explicite :
```java
/**
 * Détermine le nombre de jours de congé valorisés en paie.
 *
 * Règle GALAXAM actuelle :
 * - valorisation en jours calendaires ;
 * - cohérente avec le débit du solde congé ;
 * - week-ends inclus ;
 * - jours fériés inclus ;
 * - NBCONGE = nombre de jours calendaires débités.
 *
 * Exemple :
 * congé vendredi -> dimanche = 3 jours valorisés.
 *
 * IMPORTANT :
 * Si la règle métier évolue ultérieurement vers une valorisation
 * limitée aux jours normalement travaillés (ex. vendredi uniquement),
 * modifier cette méthode / stratégie de détermination de NBCONGE,
 * et NON les règles de décompte du grand-livre des congés.
 *
 * Le solde Congés reste calendaire tant que les règles
 * RG-CONGE-01 à RG-CONGE-03 ne sont pas explicitement modifiées.
 */
```

### Segmentation Étanche des Périodes de Paie (Multi-Périodes)
Pour une demande traversant deux périodes de paie (ex. 28 Octobre au 04 Novembre, 8 jours) :
- Le Grand-Livre enregistre une seule prise complète : $\text{PRISE} = -8.0$.
- Période P1 (Octobre) valorise l'intersection : $[28/10, 31/10] = 4.0$ jours.
- Période P2 (Novembre) valorise l'intersection : $[01/11, 04/11] = 4.0$ jours (férié du 01/11 inclus).
- $\text{Total} = \text{NBCONGE}(P_1) + \text{NBCONGE}(P_2) = 8.0$ jours.
- Aucun chevauchement ni double comptage.

---

## 5. SÉPARATION CONGÉS / POINTAGE

Pointage et Congés mesurent deux grandeurs indépendantes :
- **Domaine Congés & Paie :**
  Mesure des droits légaux et de l'indemnisation financière en jours calendaires ($NBCONGE = 3$ pour Vendredi–Dimanche).
- **Domaine Pointage :**
  Mesure le temps attendu et travaillé par rapport au planning contractuel.
  - Vendredi (planning 8h) : 8h attendues neutralisées par le motif congé.
  - Samedi (planning 0h) : 0h attendues.
  - Dimanche (planning 0h) : 0h attendues.
  - Total heures attendues neutralisées en Pointage : 8 heures.
- **Principe garanti :** Le Pointage n'est jamais utilisé pour réduire la quantité financière `NBCONGE`.

---

## 6. CALL GRAPH RÉEL DE NBCONGE

```text
               DEMANDE CONGÉ (demande_conge)
                             │
                             ▼ [Statut: VALIDE, tc.decompte_solde_acquis = TRUE]
               nbJoursDecomptes (CALENDAIRE)
                             │
            ┌────────────────┴────────────────┐
            ▼                                 ▼
   mouvement_solde_conge            CongeService.calculerNbJoursCongeValorises
    (PRISE = -nbJours)              (Intersection calendaire avec la période)
                                              │
                                              ▼
                                    PayrollService.preparerInput
                                              │
                                              ▼
                                    PayrollCalculationInput
                                    .setNbJoursConges(nbJoursValorises)
                                              │
                                              ▼
                                    PayrollExecutionEngine.calculerConges
                                              │
                      ┌───────────────────────┴───────────────────────┐
                      ▼                                               ▼
             DEDUCTION_CONGE_BASE                            INDEMNITE_CONGE_PAYE
             NBCONGE × (Base / 30)                           NBCONGE × TCONGE
```

---

## 7. AUDIT APPROFONDI DU TCONGE ACTUEL

### 7.1 Formule Runtime Réellement Exécutée
Le taux journalier de congé `TCONGE` est calculé lors de chaque clôture de paie dans `PayrollService.mettreAJourJournalConge` :
1. **Acquisition mensuelle canonique :**
   $$droitMois = \text{CongeService.enregistrerAcquisitionMensuelle}(idEmp, annee, mois, absInjustifiees)$$
2. **Assiette mensuelle prise en compte :**
   $$netBase = \text{SalaireBase} - \text{RetenuesAbsencesEtConges} + \text{IndemnitesConges}$$
   $$mntHs = \sum \text{HeuresSupplementaires}$$
   $$baseTotale = \max(0, netBase + mntHs)$$
3. **Valorisation mensuelle :**
   $$valMois = \frac{baseTotale \times droitMois}{30.0}$$
4. **Cumuls historiques :**
   $$cumulJours = cumulJoursPrec + droitMois$$
   $$cumulVal = cumulValPrec + valMois$$
5. **Taux journalier résultant :**
   $$TCONGE = \frac{cumulVal}{cumulJours} \quad (\text{arrondi à 2 décimales, HALF\_UP})$$

### 7.2 Caractérisation des Variables
| Variable | Source | Propriétaire | Nature / Type | Unité | Provenance |
|---|---|---|---|---|---|
| `salaire_base` | `contrat_travail` / `employe` | Carrière | Numeric(12,2) | MGA | Donnée contractuelle |
| `droitMois` | `mouvement_solde_conge` | Congés | Numeric(6,4) | Jours | Formule légale (2.5j proratisé) |
| `mntHs` | `bulletin_paie_ligne` (HEURES_SUP) | Paie | Numeric(12,2) | MGA | Lignes effectives du bulletin |
| `cumul_jours_droit` | `conge_journal_droit` | Paie/Congés | Numeric(8,4) | Jours | Somme cumulée historique |
| `cumul_valorisation`| `conge_journal_droit` | Paie/Congés | Numeric(12,2) | MGA | Somme cumulée historique |
| `taux_journalier_conge`| `conge_journal_droit` | Paie/Congés | Numeric(12,2) | MGA/j | $cumulVal / cumulJours$ |

---

## 8. AUDIT DE CONGE_JOURNAL_DROIT

### Structure & Sémantique
- **Table :** `conge_journal_droit`
- **Clé d'unicité :** `uk_conge_journal_emp_annee_mois (id_employe, annee, mois)`
- **Nature :**
  Cette table est un **journal hybride d'historisation de valorisation des droits acquis**.
  Elle stocke pour chaque mois :
  - le droit acquis ($droitMois$) ;
  - la base de valorisation ($baseTotale$) ;
  - la valeur monétaire du droit du mois ($valMois$) ;
  - les cumuls tous temps ($cumulJours$, $cumulVal$) ;
  - le taux journalier de congé moyen pondéré résultant ($tauxJournalierConge$).
- **Moment d'écriture :** Lors du calcul/génération du bulletin dans `PayrollService.mettreAJourJournalConge`.
- **Consommateurs :**
  1. `PayrollService.preparerInput` : pour valoriser `INDEMNITE_CONGE_PAYE` du mois suivant via `StcService.getTauxJournalierApplicable`.
  2. `StcService.preparer` : pour valoriser l'indemnité compensatrice de congés payés au départ du salarié.

---

## 9. ALIGNEMENT PAIE & STC

Paie et STC consomment **la même et unique source de vérité** :
`StcService.getTauxJournalierApplicable(idEmploye, dateReference, salaireBaseFallback, connection)`
- Recherche dans `conge_journal_droit` le dernier enregistrement avec condition temporelle étanche :
  `WHERE id_employe = ? AND (annee < ? OR (annee = ? AND mois < ?)) ORDER BY annee DESC, mois DESC LIMIT 1`.
- **Garantie anti-fuite future (Future Leakage) :** Une paie ou un STC d'Octobre 2026 ne lira jamais un taux issu de Novembre 2026.
- **Règle d'initialisation (Premier mois / absence d'historique) :**
  Classifié comme `CURRENT_INITIALIZATION_RULE` :
  $$\text{TCONGE} = \frac{\text{Salaire de Base}}{30.0}$$
  Aucune division par zéro, aucune valeur nulle silencieuse (lève une exception explicite si le salaire est introuvable).

---

## 10. AUDIT DE LA RETENUE / DÉDUCTION CONGÉ

Dans `PayrollExecutionEngine.calculerConges` :
- `DEDUCTION_CONGE_BASE` applique le diviseur légal calendaire $1/30$ au salaire contractuel :
  $$\text{DEDUCTION\_CONGE\_BASE} = \text{NBCONGE} \times \frac{\text{Salaire de Base}}{30}$$
- `INDEMNITE_CONGE_PAYE` valorise le congé au taux $TCONGE$ :
  $$\text{INDEMNITE\_CONGE\_PAYE} = \text{NBCONGE} \times \text{TCONGE}$$
- **Cohérence calendaire :**
  Lorsque $\text{TCONGE} = \text{Base} / 30$, l'indemnité compense exactement la retenue, garantissant le maintien intégral du salaire. Lorsque des heures supplémentaires antérieures ont valorisé $TCONGE > \text{Base} / 30$, le salarié perçoit un gain net conforme à la valorisation légale des droits acquis.

---

## 11. MATRICE DE CLASSIFICATION DES OCCURRENCES

| Terme / Occurrence | Emplacement | Classification | Rôle / Justification |
|---|---|---|---|
| `calculerNbJoursCongeValorises` | `CongeService.java` | `ACTIVE_CANONICAL` | **Propriétaire canonique unique de NBCONGE** |
| `calculerNbJoursCongeValorises` | `CongeCalculateurEngine.java` | `DELEGATE` | Calcul unitaire calendaire |
| `setNbJoursConges` | `PayrollService.java:663` | `ACTIVE_CANONICAL` | Injection dans le DTO de paie depuis CongeService |
| `setAttendanceInput` | `PayrollCalculationInput.java:230` | `LEGACY_ACTIVE` | Alimente `leaveDays` depuis Pointage (écrasé par CongeService) |
| `calculerConges` | `PayrollExecutionEngine.java:207` | `ACTIVE_CANONICAL` | Calcul des rubriques DEDUCTION et INDEMNITE |
| `getTauxJournalierApplicable` | `StcService.java:936` | `ACTIVE_CANONICAL` | Résolution temporelle du taux TCONGE |
| `mettreAJourJournalConge` | `PayrollService.java:935` | `ACTIVE_CANONICAL` | Mise à jour du journal de valorisation TCONGE |
| `nbJoursConges` (KPI) | `PointageEmployeService.java:812` | `DISPLAY_ONLY` | Affichage UX tableau de bord |

---

## 12. TESTS RÉALISÉS & RÉSULTATS POSTGRESQL

### Tests Unitaires (`CongeCalculateurEngineTest`)
- **Test 1 :** Vendredi → Dimanche $\rightarrow NBCONGE = 3.0$ (**PASS**)
- **Test 2 :** Lundi → Dimanche $\rightarrow NBCONGE = 7.0$ (**PASS**)
- **Test 3 :** Lundi → Vendredi avec jour férié inclus $\rightarrow NBCONGE = 5.0$ (**PASS**)
- **Test 4 :** Vendredi → Lundi inclus $\rightarrow NBCONGE = 4.0$ (**PASS**)
- **Test 5 :** Demi-journée $\rightarrow NBCONGE = 0.5$ (**PASS**)

### Tests d'Intégration PostgreSQL Réels (`CongeServiceIntegrationTest`)
- `testNbJoursCongeValorises_MultiPeriodesEtFerie` :
  Congé 28/10/2026 → 04/11/2026 (8 jours). Débit solde : $-8.0$j.
  $NBCONGE(P_1) = 4.0$j, $NBCONGE(P_2) = 4.0$j. Somme $= 8.0$j sans double comptage. (**PASS**)
- `testPointageVsNbConge_IndependanceDesMetriques` :
  Congé Ven–Dim. Grand-Livre débite $3.0$j, $NBCONGE = 3.0$j. Pointage mesure 8h attendues. (**PASS**)
- `testDemiJournee_Valorisation` :
  Demi-journée validée $\rightarrow$ Débit Grand-Livre $-0.5$j $\rightarrow NBCONGE = 0.5$j. (**PASS**)
- `testTcongeNonRegressionEtResolutionHistorique` :
  Sans historique $\rightarrow$ Fallback $\text{Base}/30$.
  Historique Septembre $\rightarrow 25\,000$ Ar. Insertion future Novembre $\rightarrow$ Reste $25\,000$ Ar au 01/10 (anti-leakage strict). (**PASS**)

---

## 13. PROPOSITION ARCHITECTURALE FUTURE POUR TCONGE (HORS PHASE 2A-3)

Pour la phase ultérieure de refonte du TCONGE, les constatations suivantes sont établies sans préjuger des décisions futures :
1. `CURRENT_RUNTIME` : Moyenne pondérée cumulative tous temps ($cumulVal / cumulJours$).
2. `PROPOSED_DESIGN` (à arbitrer) :
   - Évolution éventuelle vers une moyenne glissante sur 12 mois.
   - Clarification formelle de l'intégration des primes variables vs maintien pur.
3. `BUSINESS_RULE_REQUIRED` :
   - Aucune modification de formule TCONGE n'a été introduite dans cette phase, garantissant une stricte non-régression.

---

## 14. VERDICT GLOBAL

```text
================================================================================
PHASE 2A-3 — UNIFORMISATION NBCONGE CALENDAIRE & PRÉPARATION DU TCONGE
VERDICT : PHASE_2A_3_TCONGE_PREPARATION_PASS
================================================================================
[x] NBCONGE possède une source authoritative unique (CongeService)
[x] NBCONGE est actuellement calendaire (RG-CONGE-05)
[x] Week-ends inclus dans NBCONGE
[x] Fériés inclus dans NBCONGE
[x] Frontière de période de paie correctement segmentée
[x] Demi-journées conservées (0.5j)
[x] Pointage n'est pas utilisé pour réduire NBCONGE
[x] Pointage continue à posséder le temps attendu
[x] Grand-livre Congés reste calendaire strict
[x] Commentaire d'évolution future présent
[x] Aucune prolifération de classes (méthode unitaire dans CongeService)
[x] TCONGE actuel complètement cartographié
[x] Aucune nouvelle formule TCONGE inventée prématurément
[x] Aucune future leakage (résolution temporelle stricte)
[x] Bulletins scellés immuables
[x] Tests PostgreSQL critiques PASS
[x] Backend PASS (440 tests)
[x] Frontend PASS (16 tests)
[x] Vite Build PASS
[x] git diff --check PASS
================================================================================
```
