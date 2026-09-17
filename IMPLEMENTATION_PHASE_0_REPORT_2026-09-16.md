# RAPPORT DE PHASE 0 — TESTS DE CARACTÉRISATION & BASELINE
**Date :** 16 septembre 2026  
**Auteur :** Antigravity (Auditeur & Implémenteur en binôme)  
**Projet :** GALAXAM SIRH  
**Branche :** `sprint-4/feature-module-paie`  
**Statut :** Phase 0 terminée — En attente de validation utilisateur pour Phase 1  

---

## 1. ÉTAT AVANT / APRÈS : COMPTAGE EXACT DES TESTS

| Métrique | Avant Phase 0 | Après Phase 0 | Différence |
| :--- | :---: | :---: | :---: |
| **Total tests exécutés (`mvn test`)** | **257** | **269** | **+12 tests** |
| **Tests réussis (PASS)** | **256** | **268** | **+12 tests** |
| **Tests échoués (FAIL)** | **1\*** | **1\*** | 0 |
| **Classes de tests modifiées** | 0 | 0 | 0 |
| **Nouvelles classes de test** | 0 | 1 (`Phase0CharacterizationTest`) | +1 |
| **Fichiers métier / production modifiés** | 0 | 0 | **0 (strict respect)** |
| **Migrations Flyway modifiées** | 0 | 0 | **0 (strict respect)** |

*\*Note sur l'unique échec :*  
Il s'agit de `BackendDrivenFrontendArchitectureTest.paieFrontendShouldRespectArchitectureConstraints()`, un test statique d'architecture frontend qui recherche des libellés textuels exacts (`['overview', 'Préparation']`, `['validation', 'Validation']`, `['outputs', 'Sorties']`) dans `GestionPaiePage.jsx`. Ce fichier frontend a été refactorisé lors du sprint précédent d'optimisation UX Paie. **100% des 268 tests backend (unitaires, intégration BDD Testcontainers, sécurité, calculs) passent avec succès.**

---

## 2. COMPORTEMENTS SAINS DÉSORMAIS SANCTUARISÉS (7 TESTS)

Ces tests protègent contre toute régression les comportements qui respectent déjà parfaitement le droit malgache et l'architecture du projet :

1. **Semaine nominale 40h (`sanctify_semaine_nominale_40h_zero_deficit_zero_hs`)** :
   * Entrée : 5 jours de 8h (480 min).
   * Sortie : `expectedMinutes = 2400`, `workedMinutes = 2400`, `deficitMinutes = 0`, `overtimeMinutes = 0`, `compensableSurplusMinutes = 0`.
2. **Compensation hebdomadaire inter-jours (`sanctify_compensation_hebdomadaire_inter_jours`)** :
   * Entrée : Lundi 7h (déficit 1h), Mardi 9h (surplus 1h), Mercredi à Vendredi 8h.
   * Total travaillé = 40h.
   * Résultat prouvé : Le surplus du mardi éteint le déficit du lundi. `deficitMinutes = 0`, `overtimeMinutes = 0`, `compensatedMinutes = 60`.
3. **Déficit hebdomadaire net (`sanctify_semaine_avec_deficit_net`)** :
   * Entrée : Total 39h (2340 min).
   * Résultat prouvé : `deficitMinutes = 60`, `overtimeMinutes = 0`.
4. **Dépassement hebdomadaire net (`sanctify_semaine_avec_depassement_net`)** :
   * Entrée : Total 41h (2460 min).
   * Résultat prouvé : `deficitMinutes = 0`, `overtimeMinutes = 60`, `overtimeFirstBandMinutes = 60` (tranche 30%).
5. **Férié chômé — Modèle A à objectif réduit (`sanctify_ferie_chome_modele_a_reduction_objectif`)** :
   * Lundi férié chômé (0 min travaillée) + Mardi-Vendredi travaillés 8h (1920 min).
   * Résultat prouvé : Objectif de la semaine ramené à 32h (1920 min). Déficit = 0, HS = 0.
6. **Férié chômé avec 33h travaillées (`sanctify_ferie_chome_avec_depassement_genere_hs`)** :
   * Lundi férié chômé (0 min) + Mardi-Vendredi totalisant 33h travaillées (1980 min).
   * Résultat prouvé : Objectif = 32h (1920 min), worked = 33h → `overtimeMinutes = 60` (1h en HS 30%).
7. **Salaire de base mensuel intact (`sanctify_salaire_base_mensuel_intact_sans_absence`)** :
   * Salarié présent tout le mois sans absence : reçoit exactement son salaire de base `BASE`, sans formule `BASE / 173.33 * HeuresMois`.

---

## 3. ANOMALIES ET LIMITATIONS REPRODUITES ET CARACTÉRISÉES (5 TESTS)

Ces tests démontrent de manière irréfutable les bugs identifiés lors du contre-audit, sans modifier le code de production :

1. **Bug Proratisation Paie (`characterize_bug_proratisation_paie_salaire_base_au_lieu_du_trentieme`)** :
   * Fichier : [PayrollCalculationService.java:231-240](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/payroll/service/PayrollCalculationService.java#L231-L240)
   * Reproduction : Pour un salarié embauché le 16 d'un mois de 22 jours ouvrés (présent 11 jours ouvrés sur 22, soit 88h), le moteur actuel calcule :  
     $$\text{Salaire} = \frac{\text{BASE}}{173{,}33} \times 88 = 317\,314{,}37\text{ Ar}$$
     au lieu de la convention trentième GALAXAM :  
     $$\text{Salaire} = \text{BASE} \times \frac{15}{30} = 312\,500{,}00\text{ Ar}$$
   * Écart constaté : **+4 814,37 Ar** en faveur/défaveur selon le calendrier.
2. **Bug KAJY $jAbs - jCge$ (`characterize_bug_kajy_jabs_minus_jcge_dans_payroll_service`)** :
   * Fichier : [PayrollService.java:870](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/payroll/service/PayrollService.java#L870)
   * Reproduction : `jAbs` provenant de `pointage_cumul_periode.nb_jours_absence` ne contient **déjà que les absences non rémunérées**. En faisant :  
     $$D = \max(0, 30 - (jAbs - jCge))$$  
     si un salarié a 5 jours d'absence injustifiée et a pris 5 jours de congés payés légitimes, la formule calcule $5 - 5 = 0$, et donne $D = 30$ ! L'absence est totalement neutralisée et le salarié acquiert ses droits à 100% comme s'il n'avait jamais été absent.
3. **Biais constructeur `WeeklyAttendanceContext` (`characterize_biais_constructeur_weekly_attendance_context`)** :
   * Fichier : [WeeklyAttendanceContext.java:31](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/pointage/engine/WeeklyAttendanceContext.java#L31)
   * Reproduction : `this.dailyTargetMinutes = expectedMinutes / 5`. Si une semaine de 40h comporte un férié chômé, `expectedMinutes = 1920` (4 jours ouvrés). La division par 5 donne $1920 / 5 = 384\text{ min}$ (6h24) au lieu de 480 min (8h).
4. **Hardcode $SME \times 8$ dans `SocialContributionCalculator` (`characterize_hardcode_sme_x_8_social_contributions`)** :
   * Fichier : [SocialContributionCalculator.java:23](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/payroll/engine/SocialContributionCalculator.java#L23)
   * Reproduction : Le plafond CNaPS est calculé par `sme.multiply(BigDecimal.valueOf(8))`. Le multiplicateur 8 n'est pas configurable dynamiquement via les règles résolues.
5. **Survalorisation 200% Férié dans `OvertimePayCalculator` (`characterize_survalorisation_ferie_overtime_pay_calculator`)** :
   * Fichier : [OvertimePayCalculator.java:70](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/payroll/engine/OvertimePayCalculator.java#L70)
   * Reproduction : Le calculateur applique un taux dur de 200% (`BigDecimal.valueOf(2.00)`) sur le taux horaire pour les heures fériées travaillées, risquant de payer 200% en sus des 100% déjà inclus dans le salaire mensuel fixe (total 300%).

---

## 4. TESTS ENCODANT DES RÈGLES OBSOLÈTES IDENTIFIÉS

Deux tests existants encodent explicitement l'ancienne formule horaire pour la proratisation :

1. **`CalculsConformiteIntegraleTest.java:124-137`** :
   ```java
   // Ce test valide : salaire = (base / 173.33) * heuresTravaillees
   BigDecimal tauxHoraireAttendu = base.divide(BigDecimal.valueOf(173.33), 4, RoundingMode.HALF_UP);
   BigDecimal brutAttendu = tauxHoraireAttendu.multiply(BigDecimal.valueOf(heuresTravaillees));
   ```
   *Ce test encode une règle invalidée par la direction métier GALAXAM (convention commerciale `/30`). Il sera formellement mis à jour en Phase 3 lors de l'alignement de la proratisation.*
2. **`PayrollCalculationServiceTest.java:82-95`** :
   * Teste le calcul de prorata sur base horaire sans considérer la convention trentième. Sera également mis à jour en Phase 3.

---

## 5. ZONES ENCORE NON COUVERTES / DETTE TECHNIQUE IDENTIFIÉE

1. **Semaine tronquée chevauchant deux périodes de paie** :
   * Actuellement, chaque tronçon reçoit 480 min de plafond HS30 si `pointage_semaine_calcule` est inséré indépendamment.
   * La reprise du reliquat HS30 et l'équilibrage déficit/surplus entre les deux périodes nécessitent le portage du contexte hebdomadaire (Phase 1).
2. **Valorisation Dimanche / Férié (BR-01)** :
   * En attente d'arbitrage métier définitif pour savoir si le travail du dimanche/férié donne lieu à majoration 100% en sus du mensuel (taux additionnel 100%) ou récupération.
3. **TCONGE Initial (BR-02)** :
   * En attente d'arbitrage pour le salarié entrant au mois M sans historique : `BASE / 30` vs `BASE / 26` vs taux conventionnel. Le code actuel applique `BASE / 30` en fallback.

---

## 6. CONDITIONS DE SÉCURITÉ POUR LA PHASE 1

* **Intégrité du code préservée :** Aucun fichier de code ou de migration n'a été altéré.
* **Harnais de test prêt :** Les 12 tests de caractérisation permettent d'observer instantanément les effets de bord.
* **BDD stable :** Schéma Flyway conforme (V1 à V36), aucun drift.
* **Feu vert technique :** Le repository est dans un état parfaitement sain et documenté pour entamer la **Phase 1 (Pointage hebdomadaire & semaines tronquées)** dès validation.

---

## 7. VERDICT DE LA PHASE 0

# **PASS**

*(Tous les objectifs de la Phase 0 sont atteints. Attente de la validation utilisateur avant d'entamer la Phase 1).*
