# CONTRE-AUDIT PRÉ-PHASE 1B : POINTAGE, SEMAINE INCOMPLÈTE & FRONTIÈRE 25/26

**Date** : 17 septembre 2026  
**Auteur** : Agent Antigravity (Google DeepMind)  
**Périmètre** : Workflow Pointage → Snapshot → Paie, frontière de période 25/26, qualification des semaines incomplètes, arbitrage Architecture A (Régularisation) vs Architecture B (Qualification Différée).  
**Statut** : LIVRABLE D'ARBITRAGE — AUCUN CODE PRODUCTION NI MIGRATION MODIFIÉ.

---

## 1. VERDICT EXÉCUTIF

```
================================================================================
                    VERDICT DU CONTRE-AUDIT : PARTIAL_FIX
================================================================================
- La continuité temporelle hebdomadaire existe et fonctionne (WeeklyPriorState).
- MAIS le système traite actuellement les semaines incomplètes (coupées au 25)
  comme des unités de calcul closes et prématurément définitives.
- Des surplus candidats et des déficits candidats sont transformés en heures
  supplémentaires payables et en retenues sur salaire fermées avant même
  que la semaine civile de 40h ne soit achevée.
- Le bug financier révélé au micro-audit précédent ne provient PAS d'un manque
  de régularisation, mais d'une QUALIFICATION FINANCIÈRE TROP PRÉCOCE.
- Recommandation ferme : Adopter l'ARCHITECTURE B (Qualification Différée).
================================================================================
```

---

## 2. RÈGLE MÉTIER DE RÉFÉRENCE

1. **Seuil journalier normal** : Une journée de travail standard comptabilise 8 heures normales (`SEUIL_HEURES_JOUR`) avant de produire un surplus journalier candidat.
2. **Compensation hebdomadaire** : Les heures effectuées au-delà de l'objectif journalier constituent un crédit candidat servant prioritairement à compenser les déficits d'autres journées de la même semaine civile.
3. **Non-payable prématurément** : Un surplus journalier n'est **JAMAIS** une heure supplémentaire payable en tant que telle.
4. **Semaine complète obligatoire** : La semaine civile (lundi au dimanche, 40h légales) doit être **complète** avant de déterminer les véritables heures supplémentaires et le déficit résiduel.
5. **Qualification définitive unique** : La qualification définitive (HS30, HS50, retenue d'absence) ne s'opère qu'après consolidation globale de la semaine.

---

## 3. CARTOGRAPHIE DU PIPELINE RÉEL

```
[Pointages Bruts (pointage_jour)]
       │
       ▼
[Calcul Journalier (PointageCalculService / DailyAttendanceResult)]
       │ -> Heures travaillées, retards, statut journalier, indemnités
       ▼
[Partitionnement par Lundi ISO (PointageSyntheseService.calculerSyntheseEmploye)]
       │ -> Découpage en tronçons [debutTroncon, finTroncon]
       ▼
[Calcul Hebdomadaire (WeeklyAttendanceCalculator.calculateWeek)]
       │ -> Reçoit expectedMinutes du tronçon (ex: 16h pour 2 jours ouvrés)
       │ -> Reçoit WeeklyPriorState (NONE si premier tronçon de la semaine)
       │ -> Calcule : worked, surplus, compensated, overtime30, overtime50, absence
       ▼
[Persistance Tronçon (PointageSyntheseService.upsertSemaineCalculee)]
       │ -> Table: pointage_semaine_calcule
       │ -> Stocke: seuil_heures_troncon, heures_hs_30, heures_hs_50, heures_deficit
       │ -> PERTE DU STATUT : Ne distingue pas tronçon complet vs incomplet !
       ▼
[Agrégation Période (AttendancePeriodCalculator / PointageSyntheseService.gelerPeriode)]
       │ -> Somme aveugle : sum(overtime30), sum(overtime50), sum(absence)
       │ -> Table: pointage_cumul_periode (statut_snapshot = 'GELE')
       ▼
[Contrat d'Entrée Paie (PayrollAttendanceInput)]
       │ -> overtime30Minutes, overtime50Minutes, unpaidAbsenceMinutes
       ▼
[Moteur de Paie (PayrollExecutionEngine)]
       │ -> RETENUE_ABSENCE = unpaidAbsenceMinutes × TauxHoraire
       │ -> HEURES_SUP_30  = overtime30Minutes × TauxHoraire × 1.30
       │ -> HEURES_SUP_50  = overtime50Minutes × TauxHoraire × 1.50
       ▼
[Bulletin de Paie Définitif & Clôture (CLOTUREE)]
```

---

## 4. FONCTIONNEMENT RÉEL DE `getSemainesPeriode`

L'audit approfondi de `PointageSyntheseService.getSemainesPeriode(...)` confirme le comportement sur les 5 cas réels :
* **Cas A — 26 Lundi** : Semaine 1 complète (lundi au dimanche, 5 jours ouvrés = 40h).
* **Cas B — 26 Mercredi** : Tronçon 1 partiel (mercredi au dimanche, 3 jours ouvrés = 24h).
* **Cas C — 26 Samedi** : Le code applique `if (isWeekend(cur) && finSemaine.plusDays(7).isBefore(dateFin.plusDays(7))) { cur = cur.plusDays(1); continue; }`. Le samedi et dimanche sont sautés de la liste des semaines, et la Semaine 1 commence le lundi 28.
* **Cas D — 25 Mercredi** : Dernier tronçon partiel (lundi au mercredi, 3 jours ouvrés = 24h). Le jeudi 26 et vendredi 27 appartiendront à la période suivante.
* **Cas E — Semaine coupée 25/26** : Les deux tronçons partagent rigoureusement la même identité de semaine civile via le même lundi de référence (`day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))`).

---

## 5. MODÈLE DE SEMAINE CIVILE & TRONÇONS

Le système manipule deux réalités géométriques :
1. **La Semaine Civile ISO (Unité Légale)** : Invariante, du lundi 00:00 au dimanche 23:59 (40h).
2. **Le Tronçon de Période (Unité Calendaire)** : Intersection entre la semaine civile et la période de paie `[26 M-1, 25 M]`.
* **Constat** : Le système sait découper en tronçons, mais **il évalue chaque tronçon comme s'il s'agissait d'une mini-semaine civile autonome** avec son propre seuil (ex: 16h pour 2 jours).

---

## 6. GESTION DU STATUT COMPLETE / INCOMPLETE

### Existe-t-il un concept de complétude de la semaine ?
**NON.**
* Dans `WeeklyAttendanceResult` : aucun booléen `isComplete` ou `isProvisional`.
* Dans `PointageTronconSemaineDTO` : aucun statut de tronçon.
* Dans `pointage_semaine_calcule` : aucune colonne `est_complete`, `est_provisoire` ou `statut_troncon`.
* Dans `pointage_cumul_periode` : le champ `statut_snapshot` (`GELE`, `CONSOMME`, `REMPLACE`) concerne la version du snapshot mensuel global, pas l'état des semaines.
* Dans `PayrollAttendanceInput` : les minutes sont consommées comme des grandeurs fermées et définitives.

**Conséquence** : Le système traite un tronçon de 2 jours (lundi-mardi) finissant le 25 exactement de la même façon qu'une semaine terminée le dimanche.

---

## 7. ANALYSE DE `WeeklyPriorState`

* `WeeklyPriorState` a été introduit en Phase 1 pour transmettre les cumuls antérieurs d'une semaine tronquée à la période suivante.
* **Ce qu'il fait très bien** : Il conserve la mémoire des heures déjà faites, des surplus et des déficits antérieurs, permettant à la semaine complète de calculer son vrai cumul 40h et la continuité des tranches HS30/HS50.
* **Sa faiblesse actuelle** : Il présuppose que les résultats du tronçon 1 ont été consommés définitivement par la paie M, et calcule une « quote-part incrémentale » en faisant `Math.max(0, total - prior)`. Si le total est inférieur au prior (surplus résorbé ou déficit compensé), le delta négatif est tronqué à zéro, ce qui déconnecte la paie de la réalité économique.

---

## 8. ANALYSE DE `pointage_semaine_calcule`

* Table de persistance intermédiaire.
* Clé composite : `(id_employe, id_periode_paie, debut_semaine)`.
* Enregistre : `seuil_heures_troncon`, `heures_hs_30`, `heures_hs_50`, `heures_deficit`.
* **Défaut identifié** : Elle stocke les surplus et déficits d'un tronçon incomplet sous les mêmes libellés et colonnes que des résultats définitifs.

---

## 9. ANALYSE DE `pointage_cumul_periode`

* Table scellée consommée par la Paie.
* Réalise la somme aveugle de tous les tronçons de la période via `AttendancePeriodCalculator` :
  `total_hs_30 = sum(troncon.hs_30)`
  `heures_absence_retenues = sum(troncon.heures_deficit)`
* Ne sait pas si la dernière semaine intégrée est complète ou incomplète.

---

## 10. ANALYSE DU SNAPSHOT FINAL & CONTRAT D'INTERFACE

* `PayrollAttendanceInput` est un contrat immuable en minutes.
* Ligne 37 de `PayrollAttendanceInput.java` :
  ```java
  if (unpaidAbsenceMinutes < 0 || overtime30Minutes < 0) {
      throw new IllegalArgumentException("Une quantité Pointage ne peut pas être négative.");
  }
  ```
* Interdit structurellement toute quantité négative : une restitution d'absence ou une reprise d'HS ne peut pas passer par ce canal sans violer l'invariant de l'interface.

---

## 11. ANALYSE DU MOTEUR DE PAIE (`PayrollExecutionEngine`)

* [PayrollExecutionEngine.java](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/paie/engine/PayrollExecutionEngine.java#L172-L245) :
  * Si `unpaidAbsenceMinutes > 0`, déduit immédiatement `RETENUE_ABSENCE = heures × TauxHoraire`.
  * Si `overtime30Minutes > 0`, verse immédiatement `HEURES_SUP_30 = heures × TauxHoraire × 1.30`.
* Le moteur de paie n'a aucun moyen de savoir si ces heures proviennent d'une semaine achevée ou d'un tronçon de 2 jours coupé par le 25.

---

## 12. ANALYSE DE LA CLÔTURE DE PÉRIODE

* [PayrollPeriodWorkflowService.java](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/paie/workflow/PayrollPeriodWorkflowService.java#L468) :
  La commande `CLOSE` exige uniquement que la date courante ait atteint le 25 du mois (`!period.endDate().isAfter(now)`).
* **Verdict formel** : Le système **autorise la clôture définitive** de la période M au 25 même si la semaine civile en cours ne compte que 1 ou 2 jours de pointage.
* Une fois clôturée, la période M, son snapshot et ses bulletins sont **scellés et immuables**.

---

## 13. RÉSULTATS DES TESTS DE CARACTÉRISATION (T1 À T8)

La suite de caractérisation [PrePhase1bCharacterizationTest.java](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/test/java/mg/galaxam/sirh/unit/pointage/PrePhase1bCharacterizationTest.java) a été exécutée avec succès (`8/8 PASS`) :

| Test | Configuration | Attendu Métier Global | Comportement Actuel au 25 (Tronçon 1) | Diagnostic |
| :--- | :--- | :---: | :---: | :--- |
| **T1** | 7 + 9 + 8 + 8 + 8 = 40h | 0 déficit, 0 HS | N/A (Semaine intra-période) | **CONFORME** (Compensation interne parfaite). |
| **T2** | 7 + 9 + 8 + 8 + 9 = 41h | 0 déficit, 1h HS | N/A (Semaine intra-période) | **CONFORME** (1h HS30 qualifiée). |
| **T3** | 9 + 9 \| 7 + 7 + 8 = 40h | **0 HS finale** | **Émet 2h HS30 au 25** | **ANOMALIE** : Paiement prématuré de 2h HS en M, non dues sur la semaine. |
| **T4** | 7 + 7 \| 8 + 10 + 8 = 40h | **0 déficit final** | **Émet 2h d'absence au 25** | **ANOMALIE** : Retenue prématurée de 2h en M, non due sur la semaine. |
| **T5** | 8 + 9 \| 8 + 8 + 8 = 41h | **1h HS finale** | Émet 1h HS30 au 25 | **ANOMALIE DE QUALIFICATION** : Qualifiée au 25 avant fin de semaine. |
| **T6** | 11 + 11 \| 10 + 10 + 10 = 52h | **8h HS30 + 4h HS50** | Géré par WeeklyPriorState | **CONFORME** : Continuité des tranches préservée. |
| **T7** | Congé + 4x8h = 32h | **0 déficit, 0 HS** | Objectif réduit à 32h | **CONFORME** : Réduction d'objectif saine. |
| **T8** | Congé + 3x8h + 1x9h = 33h | **1h HS finale** | Objectif 32h, fait 33h | **CONFORME** : 1h HS qualifiée au-delà des 32h. |

---

## 14. BUGS CONFIRMÉS

### Bug 1 : Qualification Prématurée des Semaines Incomplètes (Priorité HAUTE)
* **Comportement observé** : Le tronçon final finissant le 25 émet des heures supplémentaires payables et des retenues pour absence dès le 25 du mois, alors que la semaine civile se poursuit les jours suivants.
* **Cause racine** : Absence de distinction entre `surplus candidat` et `heure supplémentaire payable`, et absence de statut `INCOMPLETE` sur les tronçons hebdomadaires.

### Bug 2 : Double Sanction du Déficit Compensé (Priorité CRITIQUE)
* **Comportement observé** : Un déficit de 2h en M est retenu sur salaire en M. En M+1, ces 2h sont compensées par du temps supplémentaire sans que la retenue ne soit remboursée. L'employé perd 2h de rémunération.
* **Cause racine** : L'état d'absence a été monétisé en M alors qu'il n'était que provisoire.

### Bug 3 : Trop-Perçu Patronal sur Surplus Résorbé (Priorité HAUTE)
* **Comportement observé** : Un surplus de 2h en M est payé en HS au 25. En M+1, un déficit absorbe ce surplus. L'employeur a payé des HS qui n'existaient pas sur la semaine complète et ne peut les récupérer.
* **Cause racine** : L'état de surplus a été monétisé en M alors qu'il n'était que candidat.

---

## 15. FAUX POSITIFS DES AUDITS PRÉCÉDENTS

1. **Faux Positif « Besoin d'une Régularisation Rétroactive Automatique »** :  
   L'audit précédent proposait de créer des rubriques de régularisation automatique sur M+1 (restitution d'absence, reprise de trop-perçu).  
   *Rectification* : C'est soigner le symptôme plutôt que la cause. Si le système ne qualifie pas prématurément le tronçon incomplet en M, **il n'y a aucun delta à régulariser en M+1**.
2. **Faux Positif « WeeklyPriorState est Invalide »** :  
   `WeeklyPriorState` est sain dans son principe : il assure le lien d'antériorité nécessaire pour évaluer la semaine complète et préserver la continuité des tranches HS30/HS50 (prouvé par le test T6). Il doit être complété, non supprimé.

---

## 16. DETTES SECONDAIRES IDENTIFIÉES

1. **Confusion conceptuelle dans les DTO** : Le champ `heuresHsNettes` est utilisé à la fois pour désigner le surplus au-delà de 8h/jour, le surplus hebdomadaire résiduel et les heures supplémentaires payables.
2. **Hardcode cosmétique `8.0`** : Dans `getSemainesPeriode`, `nbOuvrables * 8.0` pour l'IHM, au lieu de lire `seuilHeuresJour`.
3. **Divergence de comptage tronçon week-end** : `getSemainesPeriode` saute le week-end du 26 alors que `calculerSyntheseEmploye` crée un groupe à 0 jour ouvré.

---

## 17. COMPARAISON ARCHITECTURALE : A VS B

| Critère | Architecture A (Régularisation Financière) | Architecture B (Qualification Différée) |
| :--- | :--- | :--- |
| **Principe** | M paie/déduit sur l'incomplet. M+1 crée un delta financier correctif. | M laisse l'incomplet provisoire (0 HS, 0 retenue). M+1 qualifie la semaine complète. |
| **Conformité Code du Travail** | **Moyenne** (paie des HS avant 40h de travail effectif). | **Excellente** (HS qualifiées uniquement après semaine complète). |
| **Immutabilité de la Clôture** | Respectée (correction sur M+1). | **Parfaitement respectée** (aucun ajustement ultérieur nécessaire). |
| **Complexité Code** | **Très élevée** (nouvelles rubriques, gestion fiscale/sociale décalée, reprises de négatifs). | **Faible à moyenne** (conservation des candidats en carry-forward, qualification unique en M+1). |
| **Compréhension Salarié (Bulletin)** | **Médiocre** (retenues suivies de restitutions le mois suivant). | **Parfaite** (bulletins stables, sans à-coups ni lignes de correction). |
| **Gestion STC / Départ le 25** | Complexe (régularisation orpheline après départ). | Naturelle (liquidation du solde d'heures réelles au STC). |
| **Compatibilité `WeeklyPriorState`** | Exige de soustraire les montants déjà payés. | **Complète harmonie** (transporte les heures candidates vers la clôture de la semaine). |

---

## 18. RECOMMANDATION ARCHITECTURALE : L'ARCHITECTURE B

L'**Architecture B (Qualification Différée)** est la seule architecture saine, robuste et conforme aux principes fondamentaux du droit du travail et de la comptabilité de paie :

1. **Sur le tronçon final de la période M (finissant le 25)** :
   * Si la semaine est **INCOMPLÈTE** (le 25 est un lundi, mardi, mercredi ou jeudi) :
     * Les heures travaillées réelles sont enregistrées dans `total_heures_travaillees` (garantit le salaire de base et le pointage).
     * Les surplus journaliers sont conservés comme `candidate_surplus_minutes`.
     * Les déficits journaliers sont conservés comme `candidate_deficit_minutes`.
     * **`heures_hs_30 = 0`**, **`heures_hs_50 = 0`**, **`heures_deficit = 0`**.
     * Le tronçon est marqué `est_complete = false`.
     * Le bulletin M est clôturé sans aucune extrapolation spéculative.
2. **Sur le tronçon initial de la période M+1 (débutant le 26)** :
   * `WeeklyPriorState` charge l'état candidat du tronçon 1 (heures travaillées, candidats surplus/déficit).
   * La semaine atteint sa fin normale (vendredi 28 / dimanche 30) : elle devient **COMPLETE** (`est_complete = true`).
   * La compensation globale opère sur les 40h complètes.
   * La qualification définitive émet les véritables HS (ex: 1h nette) ou le véritable déficit net : ils sont **imputés sur la période M+1**, période au cours de laquelle la prestation hebdomadaire s'est achevée.

---

## 19. PLAN MINIMAL DE MISE EN ŒUVRE (PHASE 1B)

1. **Détection de la complétude du tronçon** :
   Dans `PointageSyntheseService` : Un tronçon est incomplet si `finTroncon == dateFinPeriode` et `finTroncon.getDayOfWeek() != DayOfWeek.SUNDAY` (ou vendredi pour un régime 5j).
2. **Adaptation de `WeeklyAttendanceCalculator`** :
   Ajouter dans `WeeklyAttendanceContext` un indicateur `isWeekComplete`.
   Si `!isWeekComplete` :
   * `overtime30Minutes = 0`, `overtime50Minutes = 0`, `absenceMinutes = 0`.
   * Les surplus et déficits sont exposés en tant que `candidateSurplusMinutes` et `candidateDeficitMinutes`.
3. **Carry-forward via `WeeklyPriorState`** :
   `WeeklyPriorState` transporte ces candidats vers le tronçon suivant.
   Lorsque le tronçon suivant est complet, il résout la semaine sur les 40h globales et émet la qualification définitive.
4. **Validation par tests de non-régression** :
   Rejouer T1 à T8 et vérifier que les scénarios de frontière 25/26 produisent une équité financière totale sans aucune régularisation rétroactive.

---

## 20. FICHIERS IMPACTÉS

### Fichiers à modifier en Phase 1B
1. `WeeklyAttendanceContext.java` : Ajout de la propriété `boolean isWeekComplete`.
2. `WeeklyAttendanceResult.java` : Exposition claire des candidats vs définitifs.
3. `WeeklyAttendanceCalculator.java` : Gel des qualifications payables si `!isWeekComplete`.
4. `PointageSyntheseService.java` : Détection de la complétude du tronçon et alimentation du context.
5. `WeeklyPriorState.java` : Transport des candidats provisoires.

### Fichiers qui ne doivent PAS être modifiés
* `PayrollExecutionEngine.java` (le moteur de paie fait déjà ce qu'il doit faire à partir des entrées scellées).
* `PayrollAttendanceInput.java` (le contrat immuable reste intact).
* Les migrations SQL complexes (aucune modification de schéma lourd n'est requise, les colonnes existantes suffisent ou un flag booléen léger en migration propre).
* Les règles de majoration Dimanche, Nuit, et Férié.

---

## 21. RÉPONSE EXPLICITE AUX CINQ QUESTIONS FONDAMENTALES

### Q1. Une semaine coupée au 25/26 est-elle actuellement connue comme incomplète par le système ?
**NON.** Le système évalue le tronçon comme une semaine autonome avec un objectif réduit et ignore totalement qu'il s'agit d'une semaine tronquée non achevée.

### Q2. Des surplus/déficits d'une semaine incomplète peuvent-ils actuellement atteindre Paie comme éléments définitifs ?
**OUI.** Les surplus d'un lundi-mardi sont immédiatement transformés en HS30 payées en M, et les déficits sont immédiatement déduits en retenue sur salaire en M.

### Q3. Le bug financier observé précédemment vient-il réellement d'un manque de régularisation, ou d'une qualification financière trop précoce ?
**D'UNE QUALIFICATION FINANCIÈRE TROP PRÉCOCE.** La monétisation prématurée d'un état provisoire au 25 est l'unique cause des distorsions financières constatées lors de la clôture hebdomadaire en M+1.

### Q4. Faut-il conserver, modifier ou compléter `WeeklyPriorState` ?
**LE CONSERVER ET LE COMPLÉTER.** Il est indispensable pour transporter les heures et les candidats provisoires vers le tronçon de complétude.

### Q5. Phase 1B doit-elle implémenter une régularisation financière ou une qualification différée ?
**UNE QUALIFICATION DIFFÉRÉE (Architecture B).** C'est la seule approche qui respecte la définition légale des 40h, garantit l'immutabilité stricte des clôtures et assure des bulletins de paie justes sans aucun artifice de régularisation a posteriori.

---

## 22. VERDICT FINAL DU CONTRE-AUDIT

```
================================================================================
                       VERDICT FINAL : PARTIAL_FIX
================================================================================
Le diagnostic est posé et prouvé par les tests : la cause racine n'est pas
comptable (manque de régularisation), mais chronologique (qualification trop
précoce des semaines incomplètes).
La Phase 1B doit implémenter la QUALIFICATION DIFFÉRÉE (Architecture B).
================================================================================
```

**ARRÊT STRICT : En attente de l'arbitrage et des instructions de l'utilisateur avant toute implémentation de la Phase 1B.**
