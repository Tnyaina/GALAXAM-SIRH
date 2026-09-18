# PHASE 2A-4C.1 — HARDENING OPÉRATIONNEL TCONGE (RAPPORT CANONIQUE)

**Date :** 18 Septembre 2026  
**Auteur :** Antigravity Agentic Pair Programmer  
**Statut :** VALIDÉ — CONFORME AUX RÈGLES MÉTIER KAJY & GALAXAM  
**Verdict :** `PHASE_2A_4C1_TCONGE_OPERATIONAL_HARDENING_PASS`

---

## 1. P1 — CORRECTION DE LA SIGNIFICATION DE 2000-01-01

Dans la Phase 2A-4C précédente, la date `2000-01-01` avait été introduite dans `parametre_global` pour la clé `DUREE_REFERENCE_TCONGE_MOIS`. Cette date a été formellement reclassifiée :
- **Nature :** `TECHNICAL_VALIDITY_LOWER_BOUND` (borne basse technique de validité de ligne).
- **Interdiction formelle :** Elle ne constitue ni le début de la règle métier dans l'entreprise, ni le début d'une campagne TCONGE quelconque, ni une date historique réelle GALAXAM.
- **Mise à jour en base (`sirh_galaxam` et `sirh_galaxam_test`) :**
  ```sql
  UPDATE parametre_global
  SET description = 'TECHNICAL_VALIDITY_LOWER_BOUND: Borne technique de validité du paramètre. Ne représente ni le début de la règle métier, ni le début d une campagne TCONGE, ni une date historique GALAXAM.'
  WHERE code = 'DUREE_REFERENCE_TCONGE_MOIS';
  ```
- **Vérification automatique :** Le test unitaire [TcongeP1TechnicalValidityLowerBoundUnitTest.java](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/test/java/mg/galaxam/sirh/unit/conge/TcongeP1TechnicalValidityLowerBoundUnitTest.java) certifie cette mention et s'assure qu'aucune méthode ne se sert de 2000-01-01 comme date de référence métier.

---

## 2. P2 — ÉLIMINATION DU FALLBACK SILENCIEUX PERMANENT

### Constat d'audit
L'ancienne logique risquait de faire du statut `TCONGE_REFERENCE_PERIOD_MISSING` une issue silencieuse où le SIRH continuait indéfiniment sur un taux de migration sans alerter le gestionnaire RH sur le fait qu'aucune campagne de référence n'était en cours de valorisation pour la période suivante.

### Modélisation multidimensionnelle non exclusive
Pour éviter de forcer un enum exclusif incapable de représenter les situations réelles simultanées (par exemple : une période de référence OUVERTE coexistant avec un taux applicable ACTIF issu de la période précédente), le modèle a été implémenté via [TcongeConfigurationDTO.java](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/conge/engine/TcongeConfigurationDTO.java) :

```text
TcongeConfigurationDTO
├── referenceStatus (MISSING | ACTIVE | CLOSED_AWAITING_ACTIVATION)
├── applicableRateStatus (NONE | ACTIVE)
├── tauxApplicable (montant en Ar/j)
├── sourceProvenance (MIGRATION_INITIALE | CALCUL_PERIODE_REFERENCE | SAISIE_RH)
├── referenceActive (données de la période en cours, le cas échéant)
├── referenceClosedAwaitingActivation (données de la période clôturée en attente)
└── warnings[] (ex: REFERENCE_SETUP_REQUIRED, INITIALISATION_BASE_30)
```

- **Statuts 100% dérivés :** Aucun de ces statuts n'est stocké de façon redondante en base ; ils sont calculés dynamiquement à partir des tables sources `conge_periode_reference` et `conge_taux_applicable`.
- **Exposition claire des diagnostics :** Si un salarié a un taux actif (ex: issu de migration KAJY) mais aucune période de référence ouverte, `referenceStatus = MISSING`, `applicableRateStatus = ACTIVE` et la liste des `warnings` contient explicitement `REFERENCE_SETUP_REQUIRED`.

---

## 3. P3 — PARAMÈTRE DE DURÉE DE RÉFÉRENCE (12 MOIS STRICT)

- **Source de vérité :** Table `parametre_global` sous la clé `DUREE_REFERENCE_TCONGE_MOIS`.
- **Valeur nominale :** `12`.
- **Classification :** `GALAXAM_CURRENT_BUSINESS_RULE / PROVEN_BY_KAJY`.
- **Interdiction de fallback Java :** Si le paramètre est absent ou invalide, `CongeTauxApplicableService` lève immédiatement une `SirhBusinessException(SirhErrorCode.PARAMETRE_MANQUANT)` ou `IllegalStateException`.
- **Validation calendaire exacte :** La méthode `validerDureeReference(debut, fin, c)` s'assure que :
  $$\text{fin} = \text{debut.plusMonths}(12).\text{minusDays}(1)$$
  Toute tentative de créer une période avec une durée de 6, 11 ou 13 mois est immédiatement rejetée avec le message explicite :
  `"La période de référence doit durer exactement 12 mois (attendu du %s au %s)"`.

---

## 4. ABSENCE D'INFÉRENCE JAVA & CRÉATION EXPLICITE

- **Aucune création automatique silencieuse :** Ni la clôture de paie mensuelle ni l'acquisition de congés ne créent de période de référence en arrière-plan sans action RH.
- **Création backend-driven :** Endpoint `POST /conges/periodes-reference` recevant `idEmploye`, `dateDebut`, `dateFin`.
- **Rôle du Frontend :** Pure interface d'affichage et de déclenchement. Le frontend ne calcule aucun cumul, ne dérive aucune date et n'envoie jamais le montant du taux à activer.

---

## 5. RÈGLES DE COHÉRENCE DES DATES

Lors de la création d'une période de référence :
1. **Ordre chronologique :** `dateDebut <= dateFin`.
2. **Anti-chevauchement (`TCONGE_REFERENCE_PERIOD_OVERLAP`) :**
   ```sql
   SELECT COUNT(*) FROM conge_periode_reference
   WHERE id_employe = ? AND date_debut <= ? AND date_fin >= ?
   ```
   Tout chevauchement même partiel déclenche une exception de conflit `TCONGE_REFERENCE_PERIOD_OVERLAP`.
3. **Détection des trous / discontinuités (`TCONGE_REFERENCE_PERIOD_GAP`) :**
   Si une période précédente existe et se termine à `prevFin`, et que la nouvelle commence après `prevFin + 1`, un avertissement diagnostic `TCONGE_REFERENCE_PERIOD_GAP` est journalisé et notifié.

---

## 6. BORNAGE SEAMLESS DES INTERVALLES APPLICABLES

Lors de l'activation d'un nouveau taux calculé au jour $D$ (`dateEffet`) pour un salarié ayant un taux actif ouvert $A = [\text{dateA}, \text{NULL}[$ :
- L'ancien taux est borné exactement à :
  $$A = [\text{dateA}, D[$$
  (mise à jour de `date_fin = dateEffet`).
- Le nouveau taux est inséré avec :
  $$B = [D, \text{NULL}[$$
  (insertion avec `date_effet = dateEffet`, `date_fin = NULL`).
- **Aucun trou artificiel :** L'ancien intervalle semi-ouvert à droite et le nouvel intervalle semi-ouvert à droite sont strictement contigus au jour $D$.

---

## 7. TRANSACTION D'ACTIVATION STRICTE & ANTI-CONCURRENCE

L'activation du taux calculé via `activerTauxCalcule(...)` est exécutée dans **une seule transaction atomique** avec verrouillage pessimiste PostgreSQL :
1. `SELECT ... FROM conge_periode_reference WHERE id_periode_reference = ? FOR UPDATE`
2. Vérification statut == `CLOTUREE` (sinon rejet `TCONGE_REFERENCE_NOT_CLOSED`).
3. Vérification `taux_calcule_cloture > 0`.
4. Vérification d'idempotence (si déjà activé pour la même période et la même date d'effet, retour de l'enregistrement existant).
5. En cas de date d'effet différente pour la même période déjà activée : rejet explicite `TCONGE_ACTIVATION_CONFLICT`.
6. Verrouillage des taux applicables de l'employé :
   `SELECT ... FROM conge_taux_applicable WHERE id_employe = ? FOR UPDATE`.
7. Validation de la chronologie par rapport à l'historique (`dateEffet > date_effet` du taux en cours, interdisant $date\_fin \le date\_effet$).
8. Bornage atomique du taux précédent (`date_fin = dateEffet`).
9. Insertion atomique du nouveau taux (`source_provenance = CALCUL_PERIODE_REFERENCE`).
10. Commit de la transaction.

Le test d'intégration [TcongeP3OperationalWorkflowIntegrationTest.java](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/test/java/mg/galaxam/sirh/integration/conge/TcongeP3OperationalWorkflowIntegrationTest.java) a vérifié avec succès deux transactions concurrentes simultanées : une seule transaction effectue l'activation, aucun doublon n'est généré et l'état final est parfaitement déterministe.

---

## 8. IDEMPOTENCE DE LA CLÔTURE ET DE L'ACTIVATION

- **Clôture :** Si une période est déjà `CLOTUREE`, ré-appeler `cloturerPeriodeReference(...)` ne recalcule pas les cumuls et retourne immédiatement le `taux_calcule_cloture` figé.
- **Activation :**
  - Même référence + même `dateEffet` + même taux $\rightarrow$ opération idempotente retournant l'ID du taux actif existant.
  - Même référence + `dateEffet` divergente $\rightarrow$ levée immédiate de `TCONGE_ACTIVATION_CONFLICT`.
  - Tentative de forcer un montant par le client $\rightarrow$ impossible, le montant inséré provient à 100% de la colonne `taux_calcule_cloture` de la base de données.

---

## 9. DIAGNOSTIC OPÉRATIONNEL & DTO DE CONFIGURATION

L'évaluation de la configuration par l'endpoint `GET /conges/periodes-reference/employe/{idEmploye}/statut-configuration` produit le snapshot temps réel pour le cockpit RH :
- `referenceStatus` : `MISSING`, `ACTIVE`, `CLOSED_AWAITING_ACTIVATION`.
- `applicableRateStatus` : `NONE`, `ACTIVE`.
- `warnings` :
  - `REFERENCE_SETUP_REQUIRED` : Taux applicable existant mais aucune période en construction pour l'exercice suivant.
  - `INITIALISATION_BASE_30` : Salarié sous taux forfaitaire ou initialisé au 1/30ème du salaire de base en l'absence d'historique 12 mois.

---

## 10. DROIT DU TRAVAIL MALAGASY & TRAÇABILITÉ LÉGALE

Conformément à la Loi n° 2003-044 portant Code du Travail de Madagascar (Art. 87 et suivants) :
- L'indemnité compensatrice ou de congé payé ne peut être inférieure au salaire normal qu'aurait perçu le travailleur pendant la période de congé.
- Le modèle 12 mois borné (KAJY) valorise l'exacte contre-valeur des droits acquis durant l'exercice de référence.
- Chaque taux applicable est horodaté, audité (`cree_par`, `created_at`) et relié par clé étrangère à sa période de référence source.

---

## 11. RÈGLES MÉTIER EN SUSPENS (BUSINESS_RULE_REQUIRED)

Conformément aux directives de rigueur, aucune règle métier non formellement tranchée par GALAXAM n'a été inventée dans le code :
1. `REFERENCE_START_RULE` : La date de départ d'une période de référence (anniversaire d'embauche vs année civile vs campagne d'entreprise) reste `BUSINESS_RULE_REQUIRED` et est pilotée explicitement par l'utilisateur RH lors de la création de la période.
2. `NEW_TCONGE_EFFECTIVE_DATE_RULE` : La date d'effet du nouveau taux calculé (ex: lendemain de la fin de période vs 1er jour du mois civil suivant) reste `BUSINESS_RULE_REQUIRED` et est fournie explicitement dans `ActiverTauxCalculeRequest.dateEffet`.

---

## 12. MODIFICATIONS DU SCHÉMA BDD & SCRIPTS EXÉCUTÉS

Le script canonique [2026-09-18_phase_2a4c_tconge_canonical.sql](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/conception/bdd/2026-09-18_phase_2a4c_tconge_canonical.sql) a été aligné et appliqué sur `sirh_galaxam` et `sirh_galaxam_test` :
- Description du paramètre `DUREE_REFERENCE_TCONGE_MOIS` mise à jour avec la mention `TECHNICAL_VALIDITY_LOWER_BOUND`.
- Les tables `conge_periode_reference` et `conge_taux_applicable` possèdent les index d'intégrité temporelle et de filtrage nécessaires.

---

## 13. RÉSULTATS DES TESTS ET VALIDATIONS

### Backend Maven
- **Total Tests exécutés :** 499 (baseline initiale 473 $\rightarrow$ Phase 2A-4C 488 $\rightarrow$ Phase 2A-4C.1 499)
- **Échecs :** 0
- **Erreurs :** 0
- **Ignorés :** 0
- **Statut :** `BUILD SUCCESS` (temps total ~3m10s)

```text
[INFO] Tests run: 499, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### Frontend Vitest
- **Fichiers de tests :** 5 passés
- **Total Tests exécutés :** 28 passés (100%)
- **Statut :** PASS

### Frontend Vite Build
- **Compilation / Bundling :** Succès en 428ms sans warning bloquant.

### Intégrité du Code
- **`git diff --check` :** 0 erreur (propre, aucun espace de fin, aucun conflit).

---

## 14. PASS GATES & VERDICT FINAL

| Gate | Description | Résultat |
| :--- | :--- | :---: |
| **PG-1** | Les états TCONGE ne perdent pas les situations simultanées (période ACTIVE + taux ACTIVE) | **PASS** |
| **PG-2** | `REFERENCE_SETUP_REQUIRED` est exposable comme diagnostic/warning sans bloquer | **PASS** |
| **PG-3** | Période ACTIVE + taux ACTIVE est représentable via DTO dérivé | **PASS** |
| **PG-4** | Activation entièrement transactionnelle avec verrouillage pessimiste `FOR UPDATE` | **PASS** |
| **PG-5** | Taux précédent borné et nouveau taux inséré atomiquement sans trou temporel | **PASS** |
| **PG-6** | Double activation concurrente ne produit aucun doublon (idempotente/protégée) | **PASS** |
| **PG-7** | Le frontend ne fournit jamais le montant du taux à activer | **PASS** |
| **PG-8** | Golden Master KAJY (10 828,49 Ar/j) 100% stable et testé | **PASS** |
| **PG-9** | Délégation d'architecture d'erreurs respectée (GlobalExceptionHandler) | **PASS** |

# PHASE_2A_4C1_TCONGE_OPERATIONAL_HARDENING_PASS
