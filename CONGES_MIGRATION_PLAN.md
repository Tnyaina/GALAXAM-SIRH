# PLAN DE MIGRATION DU SYSTÈME DE CONGÉS — GALAXAM SIRH

> **Document de Référence** : Plan d'exécution par étapes atomiques et ordonnées.  
> **Principe Cardinal** : Zéro régression sur Pointage, Paie et STC. Aucune invention de règle non démontrée. Approche pilotée par les tests (TDD & Caractérisation).

---

## Vue d'Ensemble des Phases

| Phase | Intitulé | Objectif Majeur | Niveau de Risque |
| :---: | :--- | :--- | :---: |
| **P0** | **Tests de Caractérisation** | Figer mathématiquement l'Excel et le comportement actuel | Faible |
| **P1** | **Modèle Canonique d'Acquisition** | Unifier l'acquisition mensuelle et la proratisation | Moyen |
| **P2** | **Valorisation Historique Mensuelle** | Figer l'assiette ($NetBase + HS$) et le 1/12ème dans le domaine Congés | Moyen |
| **P3** | **Grand-Livre & Ledger Append-Only** | Créer le double stock (quantité en jours et valeur en Ariary) | Élevé |
| **P4** | **Consommation & Indemnisation** | Séparer déduction contractuelle ($1/30$) et indemnité légale ($TCONGE$) | Élevé |
| **P5** | **Intégration Pointage** | Sanctuariser l'interface univoque d'absence et de présence assimilée | Moyen |
| **P6** | **Intégration Paie & STC** | Clôture de paie alimentant automatiquement le nouveau ledger | Élevé |
| **P7** | **API & DTO Backend** | Exposer le solde quantitatif et financier unifié | Moyen |
| **P8** | **Composants Frontend** | Restitution claire du solde, de la valeur du stock et de TCONGE | Faible |
| **P9** | **Migration des Données Historiques** | Reconstitution du ledger pour les salariés existants (cutover) | Élevé |
| **P10**| **Suppression du Code Legacy** | Nettoyage des anciennes méthodes dépréciées | Moyen |
| **P11**| **Tests E2E & Validation Complète** | Exécution de la suite de tests complète (Backend + Frontend) | Faible |
| **P12**| **Contre-Audit Final** | Vérification formelle d'intégrité avant livraison en production | Faible |

---

## Phase P0 — Tests de Caractérisation

- **Fichiers touchés** :
  - `backend/src/test/java/mg/galaxam/sirh/unit/characterization/KajyLeaveCalculationGoldenMasterUnitTest.java`
  - `backend/src/test/java/mg/galaxam/sirh/unit/characterization/KajyAppliedTcongeCharacterizationTest.java`
  - `backend/src/test/java/mg/galaxam/sirh/unit/characterization/Phase2a4aTcongeCharacterizationTest.java`
- **Objectif** :
  - Disposer d'une couverture de tests de référence reproduisant à $100\%$ les 12 mois du fichier KAJY (`Havana`), le découplage de `TCONGE` (colonne I), les rubriques de paie et la formule d'arrondi.
- **Risque** :
  - Nul (tests seuls, aucune modification de code applicatif).
- **Dépendances** :
  - Aucune.
- **Tests associés** :
  - Exécution de la suite `mg.galaxam.sirh.unit.characterization.*`.
- **Critères d'acceptation** :
  - $100\%$ des tests de caractérisation au statut VERT.
  - Tolérance d'écart arithmétique $\le 0,05\text{ Ar}$ sur le cumul 12 mois.

---

## Phase P1 — Modèle Canonique d'Acquisition

- **Fichiers touchés** :
  - `backend/src/main/java/mg/galaxam/sirh/model/conge/engine/CongeCalculateurEngine.java`
  - `backend/src/main/java/mg/galaxam/sirh/model/conge/service/CongeService.java`
- **Objectif** :
  - Extraire et encapsuler le calcul d'acquisition mensuelle proratisée :
    $$Droit = TauxMensuel \times \frac{\max(0, Diviseur - AbsencesInjustifiees)}{Diviseur}$$
  - S'assurer que `DIVISEUR_JOURS_CALENDAIRE_CONGE` (30) et `TAUX_ACQUISITION_MENSUEL` (2.5) proviennent de la configuration sans valeur magique en dur.
- **Risque** :
  - Effet de bord sur le calcul d'acquisition lors de la clôture mensuelle.
- **Dépendances** :
  - P0 validée.
- **Tests associés** :
  - `CongeCalculateurEngineTest`
  - `CongeServiceIntegrationTest.testCalculerDroitAcquis`
- **Critères d'acceptation** :
  - Pour un mois complet sans absence : acquisition exacte de 2.5000 jours.
  - Pour 6 jours d'absence : acquisition exacte de 2.0000 jours.

---

## Phase P2 — Valorisation Historique Mensuelle

- **Fichiers touchés** :
  - `backend/src/main/java/mg/galaxam/sirh/model/conge/engine/CongeCalculateurEngine.java`
  - `backend/src/main/java/mg/galaxam/sirh/model/paie/service/PayrollService.java`
- **Objectif** :
  - Sanctuariser l'assiette de valorisation mensuelle :
    $$Assiette = NetBase + HS = (SalaireBase - RetenuesAbsence - DeductionConge + IndemniteConge) + HS$$
  - Garantir l'exclusion stricte de toute prime ou indemnité `AUTIMP`.
  - Calculer la valorisation mensuelle des droits : $Assiette \times DroitAcquis / 30$.
- **Risque** :
  - Mauvaise classification de rubriques de paie entraînant l'inclusion indue d'une prime dans l'assiette.
- **Dépendances** :
  - P1 validée.
- **Tests associés** :
  - `Phase2a4aTcongeCharacterizationTest.AssietteBaseTotaleRuntime`
  - `KajyLeaveCalculationGoldenMasterUnitTest`
- **Critères d'acceptation** :
  - L'assiette $R$ correspond exactement à $NET\_BASE + HS$.
  - Si $AUTIMP > 0$, la valorisation $T$ reste insensible à ce montant.

---

## Phase P3 — Grand-Livre du Solde & Accumulateurs de Référence Découplés

- **Fichiers touchés** :
  - `backend/conception/bdd/04_schema_conges.sql`
  - `backend/src/main/java/mg/galaxam/sirh/model/conge/MouvementSoldeConge.java`
  - `backend/src/main/java/mg/galaxam/sirh/model/conge/service/CongeService.java`
- **Objectif** :
  - Séparer formellement les deux dynamiques démontrées par l'audit :
    1. **Le Stock Consommable (`soldeJoursDisponibles`)** : Évolue par `+ ACQUISITION`, `- PRISE` et `+/- AJUSTEMENT`.
    2. **Les Accumulateurs du Cycle de Référence (`cumulDroitsCycle`, `cumulValoCycle`)** : Servent exclusivement à calculer à la clôture annuelle le futur `TCONGE` ($CumulValo / CumulDroits$).
  - **Règle Cardinale** : Une prise de congé décrémente `soldeJoursDisponibles` mais ne décrémente PAS les accumulateurs du cycle en cours (pas de réduction bilatérale indue).
- **Risque** :
  - Confusion conceptuelle entre valorisation résiduelle d'un stock et assiette de calcul de la moyenne annuelle.
- **Dépendances** :
  - P2 validée.
- **Tests associés** :
  - `CongeCutoverContinuityUnitTest.test3_shouldHandleAggregatedOpeningProperly`
- **Critères d'acceptation** :
  - La prise de congés diminue le solde consommable mais laisse intacts les cumuls de valorisation du cycle.
  - Immutabilité et traçabilité absolue des mouvements de solde.

---

## Phase P4 — Consommation & Indemnisation

- **Fichiers touchés** :
  - `backend/src/main/java/mg/galaxam/sirh/model/paie/engine/PayrollExecutionEngine.java`
  - `backend/src/main/java/mg/galaxam/sirh/model/conge/service/CongeTauxApplicableService.java`
- **Objectif** :
  - Découpler clairement la déduction du salaire de base ($NBCONGE \times Base / 30$) et l'allocation de l'indemnité ($NBCONGE \times TCONGE$).
  - Garantir que `TCONGE` provient du service unifié `CongeTauxApplicableService` résolu à la date de début de la période de paie.
- **Risque** :
  - Impact direct sur le montant net à payer sur le bulletin.
- **Dépendances** :
  - P3 validée.
- **Tests associés** :
  - `PayrollExecutionEngineTest.testCalculerConges`
  - `CompensationPaieRetenueVsAllocationTest`
- **Critères d'acceptation** :
  - Si $TCONGE = Base / 30$, impact net du congé = 0 Ar.
  - Si $TCONGE > Base / 30$, impact net $= NBCONGE \times (TCONGE - Base / 30)$.

---

## Phase P5 — Intégration Pointage

- **Fichiers touchés** :
  - `backend/src/main/java/mg/galaxam/sirh/model/pointage/service/PointageSyntheseService.java`
  - `backend/src/main/java/mg/galaxam/sirh/model/conge/service/CongeService.java`
- **Objectif** :
  - S'assurer que le calcul de `NBCONGE` consommé pour la paie (`calculerNbJoursCongeValorises`) s'appuie exclusivement sur les demandes validées et gelées dans le snapshot de pointage.
  - Éliminer tout recalcul redondant ou divergence de comptage.
- **Risque** :
  - Désynchronisation entre le calendrier affiché et les jours payés.
- **Dépendances** :
  - P4 validée.
- **Tests associés** :
  - `PointageSyntheseServiceTest`
  - `CongeServiceIntegrationTest`
- **Critères d'acceptation** :
  - Le nombre de jours de congé valorisés en paie correspond exactement aux congés approuvés sur la période.

---

## Phase P6 — Intégration Paie & STC

- **Fichiers touchés** :
  - `backend/src/main/java/mg/galaxam/sirh/model/paie/service/PayrollService.java`
  - `backend/src/main/java/mg/galaxam/sirh/model/paie/service/StcService.java`
- **Objectif** :
  - À la clôture de la paie, alimenter automatiquement et de manière idempotente le grand-livre de congés.
  - Lors du calcul d'un STC, liquider les jours restants au taux `TCONGE` légitime sans altérer le grand-livre des salariés actifs.
- **Risque** :
  - Double clôture ou écrasement d'historique lors d'un recalcul.
- **Dépendances** :
  - P5 validée.
- **Tests associés** :
  - `PayrollGoldenMasterFinalizationIntegrationTest`
  - `StcServiceIntegrationTest`
- **Critères d'acceptation** :
  - Clôture de paie 100% idempotente.
  - STC génère l'indemnité compensatrice exacte au centime près.

---

## Phase P7 — API & DTO Backend

- **Fichiers touchés** :
  - `backend/src/main/java/mg/galaxam/sirh/api/controller/conge/CongeSoldeController.java`
  - `backend/src/main/java/mg/galaxam/sirh/model/conge/VSoldeCongeEmploye.java`
  - `backend/src/main/java/mg/galaxam/sirh/model/conge/engine/CongeSituationEmployeDTO.java` (NOUVEAU)
- **Objectif** :
  - Exposer la situation complète et décorrélée du salarié sans confondre solde consommable et valorisation du cycle :
    - `soldeJours` : Stock disponible pour prise immédiate.
    - `tauxCongeApplicable` : TCONGE résolu as-of pour indemnisation.
    - `sourceTauxConge` : Provenance auditée (`REPRISE_CUTOVER`, `CALCUL_PERIODE_REFERENCE`, etc.).
    - `periodeReference` : Détail du cycle en cours (dates, cumuls, mode d'ouverture `FULL_HISTORY`, `AGGREGATED_OPENING`, `MINIMAL_OPENING`, indicateur de complétude).
  - **Suppression explicite** : Aucun champ `valeurSoldeEstimee` confus ne doit être exposé comme "valeur financière du solde".
- **Risque** :
  - Rupture de contrat d'interface pour le frontend existant (rétro-compatibilité assurée sur `VSoldeCongeEmploye`).
- **Dépendances** :
  - P6 validée.
- **Tests associés** :
  - `CongeSecurityAndApiTest`
- **Critères d'acceptation** :
  - Contrats JSON rétro-compatibles et enrichis de la situation réelle.

---

## Phase P8 — Composants Frontend

- **Fichiers touchés** :
  - `frontend/src/components/conges/SoldeCongeWidget.jsx`
  - `frontend/src/components/conges/InitialisationSoldeModal.jsx`
  - `frontend/src/components/employe/tabs/CongesTab.jsx`
- **Objectif** :
  - Afficher de manière transparente et découplée :
    - Le solde consommable en jours.
    - Le taux applicable (`TCONGE`) avec son badge de provenance.
    - L'état de la période de référence (complète ou partielle issue du cutover).
  - Fournir un formulaire d'initialisation guidé et ergonomique (choix du niveau de reprise A, B ou C avec date de fin calculée automatiquement).
  - Aucun calcul monétaire côté client (affichage strict des données fournies par le backend).
- **Risque** :
  - Régression d'affichage ou d'ergonomie.
- **Dépendances** :
  - P7 validée.
- **Tests associés** :
  - `npm test -- soldeInitialAndConge.test.js`
- **Critères d'acceptation** :
  - Build Vite sans erreur (`npm run build`).
  - Tests unitaires frontend au vert.

---

## Phase P9 — Stratégie de Cutover sans Fabrication de Faux Passé

- **Fichiers touchés** :
  - `backend/conception/bdd/04_schema_conges.sql` (Extension de `conge_periode_reference` avec colonnes `mode_reprise`, `date_cutover`, `cumul_droits_ouverture`, `cumul_valorisation_ouverture`)
  - `backend/src/main/java/mg/galaxam/sirh/model/conge/service/CongeService.java`
  - `backend/src/main/java/mg/galaxam/sirh/api/controller/conge/CongeSoldeController.java`
  - Document de référence : `CONGES_CUTOVER_STRATEGY.md`
- **Objectif** :
  - Définir et implémenter le **minimum d'état suffisant** à la date de cutover $T$ sans inventer de faux bulletins, de faux mois ou de faux mouvements passés.
  - Supporter formellement les modes de reprise :
    1. **Mode A (`FULL_HISTORY`)** : Reconstitution continue si tous les mois passés existent.
    2. **Mode B (`AGGREGATED_OPENING`)** : Enregistrement de l'état arrêté (Solde + TCONGE + Cumuls du cycle en cours). Continuité mathématique absolue prouvée par test.
    3. **Mode C (`MINIMAL_OPENING`)** : Enregistrement du solde et du TCONGE uniquement. Cumuls antérieurs du cycle marqués `NULL` (`UNKNOWN`). Blocage explicite du calcul silencieux de clôture de cycle sans arbitrage RH.
    4. **Mode D (`NEW_HIRE`)** : Démarrage propre à zéro pour les nouveaux salariés post-cutover.
  - Interdire le fallback silencieux $Base/30$ au profit d'une exception explicite `LEAVE_RATE_MISSING` si aucun TCONGE n'a été configuré.
- **Risque** :
  - Risque d'erreur de saisie de l'état initial par les utilisateurs RH (couvert par validation d'intégrité API et modale frontend guidée).
- **Dépendances** :
  - P8 validée.
- **Tests associés** :
  - `CongeCutoverContinuityUnitTest` (5 tests de caractérisation : Test 1 à Test 5).
- **Critères d'acceptation** :
  - $Passé\;Connu\;(Agrégats) + Événements\;Post\text{-}Cutover = Résultat\;Identique\;au\;Centime\;Près$ (Démontré sur Havana : $10\,828,49\text{ Ar/j}$).
  - 0 fausse ligne de bulletin ou de pointage créée en BDD.

---

## Phase P10 — Suppression du Code Legacy

- **Fichiers touchés** :
  - Suppression des méthodes dépréciées et constantes mortes dans le backend.
- **Objectif** :
  - Éliminer toute dette technique, code redondant ou fallback obsolète.
- **Risque** :
  - Appel résiduel d'une méthode supprimée.
- **Dépendances** :
  - P9 validée.
- **Tests associés** :
  - Compilation complète Maven (`mvn clean compile test-compile`).
- **Critères d'acceptation** :
  - 0 warning de compilation critique, 0 référence orpheline.

---

## Phase P11 — Tests End-to-End & Intégration Globale

- **Fichiers touchés** :
  - Ensemble des suites de tests intégrées.
- **Objectif** :
  - Exécuter la chaîne complète : Saisie Pointage $\rightarrow$ Validation Congé $\rightarrow$ Calcul Paie $\rightarrow$ Clôture Paie $\rightarrow$ Contrôle Journalier.
- **Risque** :
  - Détection tardive d'une régression croisée.
- **Dépendances** :
  - P10 validée.
- **Tests associés** :
  - `mvn clean test`
  - `npm test`
- **Critères d'acceptation** :
  - $100\%$ des tests backend et frontend au statut VERT.

---

## Phase P12 — Contre-Audit & Validation Métier

- **Fichiers touchés** :
  - Rapport final de validation : `CONTRE_AUDIT_FINAL_CONGES.md`
- **Objectif** :
  - Valider formellement la conformité avec la Direction Administrative et Financière de Galaxam Logistics.
- **Risque** :
  - Aucun.
- **Dépendances** :
  - P11 validée.
- **Critères d'acceptation** :
  - Approbation formelle du rapport d'audit et passage en production.
