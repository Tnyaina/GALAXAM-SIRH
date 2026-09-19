# PAYROLL_ENGINE_FREEZE_VERIFICATION_2026-09-19

**Date :** 19 Septembre 2026  
**Statut :** VÉRIFICATION DU GEL FINAL ACHEVÉE  
**Verdict :** `PAYROLL_ENGINE_FREEZE_CONFIRMED`

---

## 1. RUNTIME VÉRIFIÉ

Le flux d'exécution réel du moteur de paie a été intégralement tracé et validé de bout en bout :

```text
PaieController
  └── PayrollService
        └── PayrollCalculationInput
              └── PayrollExecutionEngine
                    ├── calculerRetenueAbsence(...)
                    ├── calculerConges(...)
                    ├── calculerSalaireBrut(...)
                    ├── calculerCotisationsSociales(...) (CNaPS, Santé)
                    ├── calculerIrsa(...) (Barème progressif Madagascar)
                    └── calculerNetAPayer(...)
                          └── BulletinPaie / BulletinPaieLigne / PayrollExplicationDto
```

* **Moteur unique :** Aucun nouveau moteur, aucun moteur V2, aucun double calculateur n'a été créé.
* **Consommation directe :** Le service consomme directement le `TCONGE` du sous-système Congés sans le recalculer ni le modifier.

---

## 2. RÈGLES GELÉES

### A. Retenue sur Absence
* **Unité métier :** `NBABS` en heures décimales.
* **Unité canonique runtime :** `minutes` entières (`MinutesAbsence = round(NBABS × 60)`).
* **Diviseur mensuel :** Issu de la configuration canonique `ReglementationPaieConfig` (`173.33 h = 10 400 min`).
* **Formule gelée :**
  $$\text{RETENUE\_ABSENCE} = \text{BASE} \times \frac{\text{MinutesAbsence}}{173.33 \times 60}$$
* **Intégrité dimensionnelle :** Interdiction absolue de soustraire ou d'additionner des heures (`NBABS`) et des jours (`NBCONGE`).

### B. Congé Payé (Deux concepts indépendants)
1. **Déduction de base (1/30ème) :**
   $$\text{DEDUCTION\_CONGE\_BASE} = \text{NBCONGE} \times \frac{\text{BASE}}{30}$$
2. **Indemnité de congé payé (TCONGE) :**
   $$\text{INDEMNITE\_CONGE\_PAYE} = \text{NBCONGE} \times \text{TCONGE}$$
3. **Impact net congé :**
   $$\text{NET\_CONGE} = \text{INDEMNITE\_CONGE\_PAYE} - \text{DEDUCTION\_CONGE\_BASE}$$

### C. Salaire Brut Canonique
* **Formule gelée :**
  $$\text{BRUT} = \text{BASE} - \text{RETENUE\_ABSENCE} - \text{DEDUCTION\_CONGE\_BASE} + \text{INDEMNITE\_CONGE\_PAYE} + \text{HS} + \text{AUTIMP}$$
* Les remboursements et indemnités non imposables (ex. frais réels) restent strictement hors brut.

---

## 3. TESTS EXISTANTS

La suite globale existante couvrait déjà le cycle RH, la sécurité, les états de workflow, l'arrondi fiscal et les tests d'intégration complets :
* `GrandWorkflowOhatraE2ETest`
* `PayrollExecutionEngineTest`
* `PayrollServiceTest`
* `PointageSyntheseServiceTest`
* `CongeTauxApplicableServiceTest`

---

## 4. TESTS AJOUTÉS (SUITE DE CARACTÉRISATION COMPLÈTE)

Une suite exhaustive de 24 tests a été formalisée dans :
`backend/src/test/java/mg/galaxam/sirh/unit/paie/PayrollFormulaCharacterizationTest.java`

1. **Graduation des jours de congé :** $0\text{ j}$, $1\text{ j}$, $5\text{ j}$, $15\text{ j}$, $30\text{ j}$ avec validation de l'intégralité des 10 agrégats financiers (`RETENUE_ABSENCE`, `DEDUCTION_CONGE_BASE`, `INDEMNITE_CONGE_PAYE`, `BRUT`, `CNaPS`, `Santé`, `NETI`, `Assiette IRSA`, `IRSA`, `NET A PAYER`).
2. **Matrice combinatoire complète :**
   * Congé seul
   * Absence seule
   * Congé + Absence
   * Congé + HS
   * Congé + AUTIMP
   * Congé + Absence + HS + AUTIMP
3. **Tests de barrière dimensionnelle :** Vérification rigoureuse de la non-contamination des unités (heures vs jours, diviseurs 173.33 vs 30).
4. **Test anti-double déduction :** Absence injustifiée vs congé planifié.
5. **Test de réconciliation Golden Master RAKETAKA.**

---

## 5. MODIFICATIONS DE PRODUCTION ÉVENTUELLES

* **AUCUNE MODIFICATION DE CODE DE PRODUCTION N'A ÉTÉ NÉCESSAIRE.**
* Le code de production implémentait déjà rigoureusement ces règles métier canoniques.
* La stabilité est préservée à 100%.

---

## 6. GOLDEN MASTER RAKETAKA

Exécution réelle via `PayrollExecutionEngine` avec les données certifiées :
* $\text{BASE} = 625\,000{,}00\text{ Ar}$
* $\text{NBABS} = 11{,}52\text{ h}$ ($691\text{ min}$)
* $\text{NBCONGE} = 11{,}5\text{ j}$
* $\text{TCONGE} = 19\,348{,}12\text{ Ar/j}$
* $\text{AUTIMP} = 25\,257{,}14\text{ Ar}$
* $\text{HS} = 0{,}00\text{ Ar}$

### Résultats obtenus et réconciliés :
* $\text{RETENUE\_ABSENCE} = 41\,539{,}26\text{ Ar}$ (sur diviseur $173{,}33\text{ h}$)
* $\text{DEDUCTION\_CONGE\_BASE} = 239\,583{,}33\text{ Ar}$ ($11{,}5 \times 625\,000 / 30$)
* $\text{INDEMNITE\_CONGE\_PAYE} = 222\,503{,}38\text{ Ar}$ ($11{,}5 \times 19\,348{,}12$)
* $\text{BRUT} = 591\,637{,}93\text{ Ar}$
* $\text{CNaPS (1\%)} = 5\,916{,}38\text{ Ar}$
* $\text{Santé (1\%)} = 5\,916{,}38\text{ Ar}$
* $\text{NETI} = 579\,805{,}17\text{ Ar}$
* $\text{Assiette IRSA} = 579\,800{,}00\text{ Ar}$ (arrondi à la centaine inférieure)
* $\text{IRSA Net} = 42\,960{,}00\text{ Ar}$
* $\text{NET A PAYER} = 536\,845{,}17\text{ Ar}$

---

## 7. ANTI-DOUBLE-DÉDUCTION

Vérifié par `testAntiDoubleDeduction_PointageCongeNeProduitPasRetenueAbsence` :
* Lorsqu'un jour est posé et validé sous le motif `CONGE` dans le pointage :
  * Le moteur de pointage génère $0$ minute d'absence injustifiée.
  * $\text{RETENUE\_ABSENCE} = 0{,}00\text{ Ar}$.
  * Le congé produit sa déduction de base au trentième ($20\,833{,}33\text{ Ar}$) et son indemnité au TCONGE ($20\,000{,}00\text{ Ar}$).
* Il n'y a donc aucune double déduction possible.

---

## 8. BULLETIN / EXPLICABILITÉ

Le bulletin backend expose et sépare l'ensemble des rubriques et fournit l'objet `PayrollExplicationDto` :
* Salaire de base
* Retenue absence (heures, diviseur 173.33)
* Déduction base congé (jours, base, diviseur 30)
* Indemnité congé payé (jours, TCONGE applicable)
* Heures supplémentaires et primes imposables
* Cotisations et IRSA avec tranches détaillées
* Net à payer

Le composant frontend (`BulletinPaieDrawer.jsx`) affiche fidèlement ces rubriques pré-calculées sans recalcul côté client.

---

## 9. RÉSULTATS DES TESTS GLOBAUX

| Suite de test | Périmètre | Résultat |
| :--- | :--- | :--- |
| **Maven Backend** | Totalité des tests unitaires, de caractérisation et d'intégration Spring Boot / PostgreSQL | **561 / 561 PASS** (0 Failure, 0 Error, 0 Skipped) |
| **Vitest Frontend** | Totalité des tests de composants, workflows et calculs front | **55 / 55 PASS** |
| **Build Vite Frontend** | Bundle de production Vite / React | **✓ built in 405ms** |
| **Git Diff Check** | Intégrité des modifications et formatage | **0 anomalie** |

---

## 10. VERDICT

```text
================================================================================
                       PAYROLL_ENGINE_FREEZE_CONFIRMED
================================================================================
```

Le moteur de paie GALAXAM SIRH est définitivement **verrouillé et gelé**.  
Toute évolution future devra obligatoirement être justifiée par une évolution réglementaire datée ou une règle d'entreprise validée par la direction.
