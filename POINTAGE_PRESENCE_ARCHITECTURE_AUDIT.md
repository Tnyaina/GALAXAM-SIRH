# AUDIT STRUCTUREL ET ARCHITECTURAL — MODULE POINTAGE & PRÉSENCE
**GALAXAM SIRH — Version d'Audit : 2026-09-23**  
**Statut : RAPPORT D'AUDIT PRÉALABLE (Zéro modification de code / BDD exécutée)**

> Périmètre vérifié : schéma canonique `05_schema_pointage.sql`, migrations actives,
> package Java `mg.galaxam.sirh.pointage`, consommateurs Paie/Congés, routes et
> composants React. Les migrations archivées ont été utilisées uniquement pour
> comprendre l'intention historique, jamais comme preuve du comportement courant.

---

## 1. EXECUTIVE SUMMARY

Le présent audit a été réalisé sur le référentiel complet de GALAXAM SIRH afin de répondre à la question fondamentale :
> *"Si nous devions reconstruire aujourd'hui le module Pointage avec les besoins métier réels de GALAXAM, quels concepts conserverions-nous réellement ?"*

### Constat Global
Le module Pointage & Présence actuel souffre d'une **sur-ingénierie manifeste** héritée d'un empilement de phases de conception successives (Phase 1A, Phase 1B, Phase 1C-A, Phase 1C-B) ayant introduit des abstractions complexes d'inspiration industrielle ou hospitalière (partitionnement temporel infra-journalier, tranches d'heures dites "buckets", multi-instanciation de snapshots jour/semaine/bucket, pré-agrégation hebdomadaire persistée) qui **ne correspondent ni au besoin opérationnel de GALAXAM (société de logistique/services à Madagascar) ni à la réalité de son moteur de paie**.

### Chiffres Clés de l'Audit

| Indicateur | État Actuel | Cible Estimée | Écart / Réduction |
| :--- | :---: | :---: | :---: |
| **Tables BDD du domaine** | **16 tables** (+ 1 vue) | **8 à 10 tables** (+ 1–2 vues) | cible conditionnelle |
| **Lignes de code Backend** | **10 605 LOC** (44 fichiers) | ~**6 000–7 000 LOC** (25–30 fichiers) | estimation, pas quota |
| **Lignes de code Frontend** | **8 955 LOC** (23 fichiers) | ~**5 500 LOC** (14 fichiers) | **-38% LOC** (-9 fichiers) |
| **Tables non lues au runtime applicatif** | **3 tables** (`snapshot_jour`, `snapshot_semaine`, `cumul_indemnite`) | **0 table** | retrait après validation audit/replay |
| **Concepts techniques exposés** | Buckets, TemporalBuckets, QualifiedTimeBuckets, Segments tronçons, Cumuls intermédiaires | Minutes travaillées, Heures sup (30%/50%), Indemnités effectives, Snapshot mensuel Paie | **Simplification radicale** |

### Les 5 Enseignements Majeurs
1. **Les snapshots jour et semaine sont des tables mortes** : `pointage_snapshot_jour` et `pointage_snapshot_semaine` sont alimentées lors du scellement d'une période (`gelerPeriode`) mais ne sont **absolument jamais lues ni requêtées par aucun composant de l'application** (ni paie, ni écran, ni export).
2. **Le cumul d'indemnité est une duplication morte** : La table `pointage_cumul_indemnite` est calculée et insérée lors du gel, mais `PayrollService` lit exclusivement ses indemnités dans `pointage_cumul_periode` (colonnes dédiées repas et déplacement).
3. **Les buckets sont un format technique, mais aujourd'hui un format historique de paie** : `PayrollService` relit `pointage_snapshot_bucket` et le transmet à `OvertimePayCalculator`. Ils sont recalculables depuis les pointages et les règles *courants*, mais pas nécessairement reproductibles après modification des horaires, seuils, calendriers ou règles. Leur table peut disparaître seulement si un snapshot final équivalent conserve les classifications et la provenance nécessaires.
4. **La table `pointage_semaine_calcule` est un stockage intermédiaire inutile** : Les heures supplémentaires hebdomadaires sont régies par un seuil légal de 40 heures. Ce calcul relève du **calculateur en mémoire (`WeeklyAttendanceCalculator`)** et non d'une table relationnelle modifiée, invalidée et recalculée à chaque clic.
5. **Duplication sévère du calcul de journée Frontend / Backend** : Le fichier frontend `pointageCalculations.js` (275 LOC) réimplémente à l'identique la logique de pause automatique, de calcul de retard, d'amplitude horaire et de qualification de statut déjà présente dans `DailyAttendanceCalculator.java` (209 LOC).

---

## 2. WORKFLOW RÉEL DU DOMAINE

L'analyse minutieuse du code révèle le cycle de vie réel des données de présence à travers l'application.

```
       [ Configuration Globale & Site ]
        (ref_site, horaires, tolérances, site_indemnite_config)
                         │
                         ▼
        ┌──────────────────────────────────┐
        │       PÉRIODE DE PAIE            │
        │   Statut: BROUILLON → EN_COURS   │
        └─────────────────┬────────────────┘
                          │
                          ▼
        ┌──────────────────────────────────┐
        │  SAISIE QUOTIDIENNE (Agent)      │
        │  Table: pointage_jour (brut)     │◄──── Congés validés (demande_conge)
        │  Table: pointage_indemnite_jour  │◄──── Fériés (ref_calendrier_ferie)
        └─────────────────┬────────────────┘
                          │
                          ▼
        ┌──────────────────────────────────┐
        │  DÉTECTION & RÉGULARISATION      │
        │  Anomalies, Justifications, DAF  │
        └─────────────────┬────────────────┘
                          │
                 (Action: SUBMIT)
                          │
                          ▼
        ┌──────────────────────────────────┐
        │  TRANSMISSION DAF                │
        │  Statut: TRANSMIS_DAF            │
        │  (Vérification blocages = 0)     │
        └─────────────────┬────────────────┘
                          │
                 (Action: APPROVE)
                          │
                          ▼
        ┌──────────────────────────────────┐
        │  SCELLEMENT DAF (gelerPeriode)   │
        │  Statut période: VALIDEE         │
        │  Écriture: pointage_cumul_periode│
        │  [+ écritures mortes snapshots]  │
        └─────────────────┬────────────────┘
                          │
                          ▼
        ┌──────────────────────────────────┐
        │  MOTEUR DE PAIE (PayrollService) │
        │  Consomme pointage_cumul_periode │
        │  Consomme buckets en mémoire     │
        │  Fige: bulletin_paie             │
        └──────────────────────────────────┘
```

### Détail des Étapes Réelles
1. **Ouverture de Période** : L'Administrateur ou DAF crée la période (`code`, `date_debut`, `date_fin`) via `PeriodePaieController`. Elle passe de `BROUILLON` à `EN_COURS`.
2. **Saisie Journalière** : L'Agent de Pointage sélectionne un site et une date. Le service `PointageSaisieService.getGrilleSaisie` agrège les salariés affectés, les congés approuvés (`demande_conge`) et les jours fériés chômés (`CalendrierOuvrableEngine`). L'Agent saisit les 4 horaires (`heure_entree_matin`, `heure_sortie_matin`, `heure_entree_am`, `heure_sortie_am`) et coche les indemnités.
3. **Enregistrement en Lot** : L'API `POST /pointages/saisie/batch` enregistre dans `pointage_jour` et ventile les indemnités dans `pointage_indemnite_jour`. Un verrou optimiste basé sur la colonne `version` protège les lignes contre les écritures concurrentes.
4. **Contrôle & Régularisation des Anomalies** : L'Agent régularise les pointages incomplets (`ANOMALIE_SAISIE`) ou absences non justifiées via l'écran Suivi.
5. **Transmission à la DAF** : L'Agent clique sur "Transmettre à la DAF" (`PayrollPeriodCommand.SUBMIT`). Le système vérifie qu'il n'existe plus aucune anomalie bloquante. La période passe en `TRANSMIS_DAF`.
6. **Validation & Scellement (Gel)** : La DAF valide (`PayrollPeriodCommand.APPROVE`). La méthode `PointageSyntheseService.gelerPeriode` :
   - Calcule la synthèse de chaque employé via `getSynthesePeriode` ;
   - Écrit une ligne dans `pointage_cumul_periode` avec `statut_snapshot = 'GELE'` et un numéro de version incrémenté ;
   - Écrit des lignes dans `pointage_snapshot_jour`, `pointage_snapshot_semaine`, `pointage_snapshot_bucket` et `pointage_cumul_indemnite` ;
   - Passe la période en `VALIDEE`.
7. **Consommation par la Paie** : `PayrollService` lit `pointage_cumul_periode` pour valoriser les jours ouvrables, travaillés, absences, retards, indemnités, et lit `pointage_snapshot_bucket` pour ventiler les majorations HS/Nuit/Dimanche/Férié via `OvertimePayCalculator`.
8. **Clôture** : Lorsque les bulletins de paie sont approuvés, la période est clôturée (`CLOTUREE`). Le snapshot devient `CONSOMME`.

---

## 3. PERSONAS ET PERMISSIONS

Le système implémente une ségrégation rigoureuse des rôles à travers Spring Security (`PreAuthorize`) et le filtre JWT.

### Personas Identifiés dans le Code

| Rôle Métier | Rôle Spring Security | Authorities Effectives | Responsabilité Métier |
| :--- | :--- | :--- | :--- |
| **Agent de Pointage** | `ROLE_AGENT_POINTAGE` | `POINTAGE_READ`, `POINTAGE_WRITE` | Saisie quotidienne des présences, contrôle des retards, déclaration des motifs d'absence, transmission de la période à la DAF. **Interdiction formelle d'accéder aux salaires, taux et bulletins.** |
| **Directeur Administratif & Financier (DAF) / Admin** | `ROLE_ADMIN` | `ROLE_ADMIN`, `POINTAGE_READ`, `POINTAGE_WRITE`, `POINTAGE_VALIDATE`, `PAIE_READ`, `PAIE_WRITE`, `PAIE_VALIDATE` | Arbitrage des litiges, validation/rejet de la période de pointage, scellement des heures, déclenchement et clôture de la paie. |

### Vérification des Permissions Réelles

- **Lecture Pointage** : `@PreAuthorize("hasAuthority('POINTAGE_READ') or hasAuthority('ROLE_ADMIN')")`
- **Écriture / Saisie** : `@PreAuthorize("hasAuthority('POINTAGE_WRITE') or hasAuthority('ROLE_ADMIN')")`
- **Validation / Scellement** : `@PreAuthorize("hasAuthority('POINTAGE_VALIDATE') or hasAuthority('ROLE_ADMIN')")`
- **Dévalidation / Réouverture** : `@PreAuthorize("hasAuthority('ROLE_ADMIN')")`

### Analyse des Anticipations Inutiles
Le code ne contient **aucune logique résiduelle de Manager ou d'Employé en libre-service** dans le module Pointage. L'architecture est saine sur ce point : elle se concentre strictement sur l'Agent de Saisie et l'Administrateur DAF.

---

## 4. INVENTAIRE BACKEND COMPLET

Le package `mg.galaxam.sirh.pointage` totalise **44 fichiers Java** et **10 605 lignes physiques** (`wc -l`, commentaires et blancs inclus) : 6 contrôleurs, 9 DTO, 5 services applicatifs, 18 types de calcul, 1 moteur calendrier, 1 mapper et 2 beans de persistance, plus les fichiers de package/documentation hors comptage Java.

| Fichier | LOC | Responsabilité Principale | SQL Direct | Calcul Métier | Workflow | Réutilisé ? | Classification Provisoire |
| :--- | :---: | :--- | :---: | :---: | :---: | :---: | :--- |
| `PointageSaisieController.java` | 60 | Endpoints REST saisie grille & batch | Non | Non | Non | Non | **KEEP** (Simplifier) |
| `PointageSyntheseController.java` | 183 | Endpoints synthèse, semaines, calcul global | Non | Non | Oui | Non | **PROBABLY_MERGE** |
| `PointageAnomalieController.java` | 47 | Endpoints anomalies & régularisation | Non | Non | Non | Non | **PROBABLY_MERGE** |
| `PointageEmployeController.java` | 58 | Fiche individuelle & export Excel | Non | Non | Non | Non | **KEEP** |
| `PointageParametreController.java` | 167 | CRUD tolérances et dérogations | Oui | Non | Non | Non | **PROBABLY_MERGE** |
| `PeriodePaieController.java` | 247 | Endpoints cycle de vie période | Oui | Non | Oui | Oui | **KEEP** |
| `PointageSaisieService.java` | 1706 | Chargement grille, batch upsert, verrous | Oui | Oui | Oui | Oui | **ESSENTIAL** (À alléger) |
| `PointageSyntheseService.java` | 1914 | Calcul mensuel, gel, tronçons, snapshots | Oui | Oui | Oui | Oui | **ESSENTIAL** (À assainir) |
| `PointageAnomalieService.java` | 1113 | Détection & régularisation anomalies | Oui | Oui | Oui | Non | **PROBABLY_MERGE** |
| `PointageEmployeService.java` | 921 | Reconstitution fiche salarié | Oui | Oui | Non | Non | **PROBABLY_MERGE** |
| `PointageExcelExportService.java` | 735 | Génération POI Excel des fiches | Non | Non | Non | Non | **KEEP** |
| `CalendrierOuvrableEngine.java` | 318 | Source canonique jours ouvrables/fériés | Oui | Oui | Non | Oui | **ESSENTIAL** |
| `DailyAttendanceCalculator.java` | 209 | Calcul pur journée (retard, amplitudes) | Non | Oui | Non | Oui | **ESSENTIAL** |
| `DailyAttendanceContext.java` | 44 | Contexte immuable calcul jour | Non | Non | Non | Oui | **ESSENTIAL** |
| `DailyAttendanceResult.java` | 37 | Résultat calcul jour | Non | Non | Non | Oui | **ESSENTIAL** |
| `WeeklyAttendanceCalculator.java` | 74 | Seuils 40h, quotas HS30/50, tronçons | Non | Oui | Non | Oui | **ESSENTIAL** |
| `WeeklyAttendanceContext.java` | 18 | Contexte calcul hebdomadaire | Non | Non | Non | Oui | **ESSENTIAL** |
| `WeeklyAttendanceResult.java` | 37 | Résultat hebdomadaire | Non | Non | Non | Oui | **ESSENTIAL** |
| `WeeklyPriorState.java` | 17 | État hérité semaine à cheval | Non | Non | Non | Oui | **PROBABLY_KEEP** |
| `PeriodWeekSegmenter.java` | 83 | Découpage ISO en tronçons | Non | Oui | Non | Oui | **PROBABLY_KEEP** |
| `AttendancePeriodCalculator.java`| 32 | Agrégation période des semaines | Non | Oui | Non | Oui | **PROBABLY_INLINE** |
| `AttendancePeriodContext.java` | 11 | Contexte période | Non | Non | Non | Oui | **PROBABLY_INLINE** |
| `AttendancePeriodResult.java` | 27 | Résultat période | Non | Non | Non | Oui | **PROBABLY_INLINE** |
| `AttendanceTemporalPartitioner.java`| 171 | Découpage 22h/00h/05h en buckets | Non | Oui | Non | Non | **QUESTIONABLE** (Détail Paie) |
| `OvertimeProjectionEngine.java` | 131 | Projection quotas HS sur buckets | Non | Oui | Non | Non | **QUESTIONABLE** (Détail Paie) |
| `TemporalBucket.java` | 57 | Record segment temporel brut | Non | Non | Non | Non | **QUESTIONABLE** |
| `QualifiedTimeBucket.java` | 63 | Record segment temporel qualifié | Non | Non | Non | Oui | **PROBABLY_KEEP** (In Paie) |
| `WorkInterval.java` | 33 | Intervalle temporel continu | Non | Non | Non | Non | **QUESTIONABLE** |
| `WorkIntervalFactory.java` | 86 | Fabrique d'intervalles depuis pointage | Non | Oui | Non | Non | **QUESTIONABLE** |
| `DaySpecialType.java` | 29 | Enum type calendaire (dimanche, férié) | Non | Non | Non | Oui | **PROBABLY_KEEP** |
| `NightWorkType.java` | 10 | Enum type nuit | Non | Non | Non | Oui | **PROBABLY_KEEP** |
| `OvertimeTier.java` | 8 | Enum tranches HS (REGULAR, 30, 50) | Non | Non | Non | Oui | **PROBABLY_KEEP** |
| `PointageTimeBoundaryMapper.java` | 21 | Formattage "08h30" et conversions | Non | Non | Non | Non | **PROBABLY_INLINE** |
| `RefToleranceArriveePointage.java`| 149 | Entité bean tolérance site | Oui | Non | Non | Non | **PROBABLY_DELETE** (Merge ref_site) |
| `EmployeIndemniteDerogation.java`| 227 | Entité bean dérogation employé | Oui | Non | Non | Non | **PROBABLY_KEEP** |
| **DTOs (9 fichiers)** | ~1 200 | Structures JSON API | Non | Non | Non | Oui | **PROBABLY_MERGE** (Élaguer) |

---

## 5. INVENTAIRE FRONTEND

L'interface utilisateur React/Vite/Tailwind comprend **23 fichiers** pour **8 955 lignes de code**.

| Fichier / Composant | LOC | Rôle Réel | Problème Détecté / Diagnostic | Verdict Provisoire |
| :--- | :---: | :--- | :--- | :--- |
| `GestionPointagesPage.jsx` | 179 | Cockpit et routeur par onglets | Gère l'orchestration des 3 sous-pages | **KEEP** |
| `SaisieQuotidiennePage.jsx` | 521 | Écran de saisie journalière par site | Bien structuré, utilise la grille dynamique | **KEEP** |
| `SuiviAnomaliesPage.jsx` | 933 | Écran de suivi des anomalies | Très lourd ; duplique des filtres et formulaires | **PROBABLY_MERGE** |
| `ArbitrageDafPage.jsx` | 1029 | Synthèse mensuelle et validation DAF | Trop massif ; intègre la validation hebdo | **PROBABLY_MERGE** |
| `PointageGridTable.jsx` | 691 | Tableau matriciel de saisie | Micro-calculs de ligne en continu | **KEEP** (Optimiser) |
| `PointageToolbar.jsx` | 145 | Barre d'actions et sélecteurs de date/site| Utile, générique | **KEEP** |
| `StatusPickerPopover.jsx` | 354 | Sélecteur contextuel de statut de badgeage| Riche, fonctionnel | **KEEP** |
| `PointageGanttView.jsx` | 597 | Visualisation graphique horaire | Optionnelle ; gadget peu utilisé par les agents| **QUESTIONABLE** |
| `AnomalieRegularisationModal.jsx`| 983 | Modal de justification/régularisation | Énorme ; intègre la création de congés inline | **PROBABLY_SIMPLIFY** |
| `DafAjustementPointageModal.jsx` | 589 | Modal de rectification d'heures par la DAF| Duplication avec la régularisation anomalie | **PROBABLY_MERGE** |
| `PointageDetailHebdoModal.jsx` | 554 | Modal de détail des tronçons de semaine | Présente les données de `pointage_semaine_calcule`| **PROBABLY_SIMPLIFY** |
| `PointageFichePrintModal.jsx` | 579 | Aperçu avant impression fiche employé | Indispensable pour l'émargement papier | **KEEP** |
| `PointageRecapModal.jsx` | 263 | Modal de récapitulatif avant transmission | Simple et utile | **KEEP** |
| `usePointageSaisie.js` | 465 | Hook de gestion d'état de saisie | Très bien conçu mais complexe | **KEEP** |
| `usePointageAnomalies.js` | 97 | Hook fetch anomalies | Hook simple | **PROBABLY_MERGE** |
| `usePeriodePaie.js` | 91 | Hook fetch période | Hook réutilisable | **KEEP** |
| `useToleranceArrivee.js` | 87 | Hook CRUD tolérances | Utilisé uniquement dans l'administration | **KEEP** |
| `useDerogationIndemnites.js` | 170 | Hook CRUD dérogations | Utilisé uniquement dans l'administration | **KEEP** |
| `attendanceService.js` | 36 | Client API Axios pointage | Service standard | **KEEP** |
| `pointageSyntheseService.js` | 57 | Client API Axios synthèses | Duplication avec attendanceService | **PROBABLY_MERGE** |
| `periodePaieService.js` | 48 | Client API Axios périodes | Service standard | **KEEP** |
| `attendanceDisplayUtils.jsx` | 212 | Helpers d'affichage, badges, couleurs | Bon découplage UI | **KEEP** |
| `pointageCalculations.js` | 275 | Moteur de calcul de ligne temps réel | **DUPLICATION COMPLÈTE DU BACKEND** | **REWRITE / LIGHTEN** |

---

## 6. INVENTAIRE BDD — PRIORITÉ ABSOLUE

Le schéma `05_schema_pointage.sql` et ses migrations créent actuellement **16 tables** et **1 vue**.

| Table BDD | Rôle Réel | Écrit par | Lu par | Historique ? | Recalculable ? | Paie Dépend ? | Verdict Provisoire |
| :--- | :--- | :--- | :--- | :---: | :---: | :---: | :--- |
| `ref_statut_periode_paie` | Référentiel des statuts de période (7 statuts fixes) | Migration SQL | `PeriodePaieController`, `PayrollPeriodWorkflowService` | Non | Non | Indirectement | **KEEP** (Ou Enum/CHECK) |
| `periode_paie` | Entité centrale Période de paie | `PeriodePaieController`, `PayrollPeriodWorkflowService` | Tout le système | Oui | Non | **OUI** | **KEEP** |
| `periode_paie_transition` | Journal d'audit des transitions d'états | `PayrollPeriodWorkflowService.appendHistory` | Audit RH, Support | Oui | Non | Non | **KEEP (Audit)** |
| `ref_statut_pointage_jour` | Référentiel des 11 statuts de pointage jour | Migration SQL | `PointageSaisieService`, `PointageAnomalieService` | Non | Non | Non | **KEEP** (Ou Enum/CHECK) |
| `ref_indemnite` | Référentiel des indemnités (Repas, Déplacement) | Migration SQL | Saisie, Paie, Config | Non | Non | **OUI** | **KEEP** |
| `site_indemnite_config` | Montants d'indemnité par site et périodicité | Admin RH | `PointageSaisieService` | Oui (date_fin) | Non | Indirectement | **KEEP** |
| `ref_tolerance_arrivee_pointage`| Heure plafond d'arrivée par site | Admin RH | `PointageSaisieService` (Désactivé) | Non | Non | Non | **DROP (Merge ref_site)** |
| `employe_indemnite_derogation`| Exceptions d'indemnités par employé | Admin RH | `PointageSaisieService` | Oui (date_fin) | Non | Indirectement | **KEEP** |
| `pointage_jour` | **Donnée source primaire : badgeages bruts** | `PointageSaisieService`, `PointageAnomalieService` | Moteurs de calcul, IHM, Exports | Oui (updated_at) | Non (Source) | **OUI (Source)** | **KEEP (Inviolable)** |
| `pointage_indemnite_jour` | **Donnée source indemnités accordées par jour** | `PointageSaisieService`, `PointageAnomalieService` | Synthèses, IHM, Exports | Oui (updated_at) | Partiellement | **OUI** | **KEEP** |
| `pointage_semaine_calcule` | Agrégation hebdomadaire persistée | `PointageSyntheseService.validerSemaine` | IHM détail hebdo, `gelerPeriode` | Non | **100% OUI** | Non | **DROP / REPLACE_BY_CALC** |
| `pointage_cumul_periode` | **Snapshot scellé consommé par la Paie** | `PointageSyntheseService.gelerPeriode` | `PayrollService.java`, Bulletins | **OUI (Scellé)** | **Non (Figé)** | **OUI (DIRECT)** | **KEEP (Indispensable)** |
| `pointage_snapshot_jour` | Copie conforme figée des jours du snapshot | `PointageSyntheseService:1169` | **PERSONNE (Dead Code)** | Oui | 100% OUI | **NON** | **DROP (Inutile)** |
| `pointage_snapshot_semaine` | Copie conforme figée des semaines du snapshot | `PointageSyntheseService:1178` | **PERSONNE (Dead Code)** | Oui | 100% OUI | **NON** | **DROP (Inutile)** |
| `pointage_snapshot_bucket` | Découpage infra-journalier des heures | `PointageSyntheseService:1304` | `PayrollService:1499` | Oui, au gel | Non sans règles figées | **OUI** | **KEEP puis MERGE/REWRITE** |
| `pointage_cumul_indemnite` | Pré-cumul mensuel des indemnités | `PointageSyntheseService:1136` | **PERSONNE (Dead Code)** | Non | 100% OUI | **NON** | **DROP (Inutile)** |
| `v_pointage_indemnites` (Vue) | Vue de jointure pij + ref_indemnite | DDL | IHM | N/A | 100% OUI | Non | **KEEP** |

---

## 7. SOURCES DE VÉRITÉ — MATRICE DES RESPONSABILITÉS

| Concept Métier | Source Canonique Actuelle | Sources Concurrentes Détectées | Risque Identifié | Verdict Architectural |
| :--- | :--- | :--- | :--- | :--- |
| **Horaires de travail** | `ref_site` (colonnes `heure_debut_matin`, etc.) | `pointage_jour` (horaire_continu_jour) | Désynchronisation si le site change en cours de mois | **Source : `ref_site`** |
| **Jours ouvrables / fériés**| `CalendrierOuvrableEngine` | Calcul inline frontend (`pointageCalculations.js`) | Jours fériés manqués côté UI si calendrier non fetché | **Source : Backend `CalendrierOuvrableEngine`** |
| **Congés approuvés** | `demande_conge` (statut `VALIDE`) | Création inline dans `PointageAnomalieService` | Corruption du workflow RH Congés | **Source : Module Congés exclusivement** |
| **Minutes travaillées** | `DailyAttendanceCalculator` (calcul) | `pointageCalculations.js` (frontend) | Écart d'arrondi ou de pause auto entre UI et DB | **Source : `DailyAttendanceCalculator`** |
| **Retard** | `DailyAttendanceCalculator` | `pointageCalculations.js` (frontend) | Divergence sur pause théorique vs réelle | **Source : `DailyAttendanceCalculator`** |
| **Heures supplémentaires**| `WeeklyAttendanceCalculator` | `pointage_semaine_calcule` (persistance) | `pointage_semaine_calcule` désynchronisé si jour édité | **Source : `WeeklyAttendanceCalculator`** |
| **Indemnités accordées** | `pointage_indemnite_jour` | `pointage_jour.montant_total_indemnites` (dénorm) | Colonne dénormalisée pouvant diverger | **Source : `pointage_indemnite_jour`** |
| **Snapshot pour Paie** | `pointage_cumul_periode` | `pointage_snapshot_bucket`, `pointage_semaine_calcule`| Multiplicité des tables de gel | **Source : `pointage_cumul_periode` unique** |

---

## 8. BUCKETS — SONT-ILS RÉELLEMENT NÉCESSAIRES ?

### 1. Qu'est-ce qu'un "Bucket" dans GALAXAM ?
Un "Bucket" (incarné par les classes `TemporalBucket`, `QualifiedTimeBucket` et la table `pointage_snapshot_bucket`) est un segment de temps continu, d'une durée exprimée en minutes, appartenant strictement à une même journée civile et ne traversant aucune des frontières temporelles suivantes :
- `00:00` (minuit, frontière calendaire)
- `05:00` (fin légale du travail de nuit selon le Code du travail malagasy)
- `22:00` (début légal du travail de nuit)

### 2. Exemple Concret
Un employé de GALAXAM effectue un quart de nuit le samedi soir, entrant à 20h00 et sortant le dimanche matin à 06h00 (durée : 10 heures = 600 minutes).
Le moteur `AttendanceTemporalPartitioner` découpe cette vacation continue en 4 "Buckets" :
1. `Samedi 20h00 → 22h00` (120 min) : Jour standard, jour samedi.
2. `Samedi 22h00 → Dimanche 00h00` (120 min) : Travail de nuit, jour samedi.
3. `Dimanche 00h00 → 05h00` (300 min) : Travail de nuit ET dimanche (collision de majorations).
4. `Dimanche 05h00 → 06h00` (60 min) : Travail de jour, dimanche.

Puis, `OvertimeProjectionEngine` projette sur chacun de ces buckets le statut d'heures supplémentaires (Normal, HS 30%, HS 50%) déterminé au niveau de la semaine.

### 3. Pourquoi ce Concept a-t-il été Introduit ?
Le droit du travail malagasy (Décret n° 72-226 régissant le travail de nuit, le travail du dimanche et des jours fériés) pose une règle complexe de **non-cumul ou cumul plafonné des majorations** (collision policy : `MAX_PREMIUM`, `CUMUL_ADDITIF`).
Pour savoir si les heures supplémentaires d'une semaine doivent être payées à +30%, +50%, ou majorées de +40% pour le dimanche ou +30% pour la nuit, l'ancien architecte a estimé qu'il fallait découper chaque journée en segments homogènes et stocker cette décomposition en base.

### 4. Ce qu'il Coûte en Complexité
- **6 classes Java dédiées** (`TemporalBucket`, `QualifiedTimeBucket`, `AttendanceTemporalPartitioner`, `OvertimeProjectionEngine`, `WorkInterval`, `WorkIntervalFactory`) représentant **665 lignes de code**.
- **1 table PostgreSQL dédiée** (`pointage_snapshot_bucket`). Son volume réel doit être mesuré en production ; le dépôt seul ne permet ni d'affirmer 25 000 lignes/mois ni une saturation de PostgreSQL.
- Deux méthodes JDBC complexes de sérialisation (`enregistrerSnapshotBuckets`, 138 LOC) et désérialisation (`loadSnapshotBuckets`, 25 LOC).

### 5. Est-il indispensable de le persister en base ?
**Dans le contrat courant : oui, jusqu'à remplacement par une preuve historique équivalente.**
- Le moteur de paie (`OvertimePayCalculator`) a besoin des objets Java `QualifiedTimeBucket` en mémoire au moment du calcul du bulletin.
- Ils sont reconstructibles à la volée à partir de `pointage_jour` seulement avec les règles et configurations ayant servi au gel. Or ces versions ne sont pas toutes historisées dans le snapshot période.
- Dès que le bulletin de paie est calculé, la table `bulletin_paie_ligne` enregistre le résultat financier final :
  - Rubrique HS 30% : Montant
  - Rubrique HS 50% : Montant
  - Majoration Nuit : Montant
  - Majoration Dimanche : Montant
- Aucun écran ne l'expose, mais la Paie la consomme directement : ce n'est donc pas du dead code.
- **Verdict : KEEP temporaire / MERGE à terme.** Le concept « segment qualifié » peut devenir interne au moteur, mais sa sortie figée doit rester reproductible. Une cible acceptable est soit (a) un snapshot final période enrichi de toutes les minutes qualifiées et de la version des règles, soit (b) des lignes immuables de valorisation de paie. Ne supprimer la table qu'après test de replay historique bit-à-bit.

---

## 9. PIPELINE JOUR → SEMAINE → PÉRIODE

### Diagnostic du Choix Architectural Actuel

Le module implémente actuellement :
```
pointage_jour (DB)
       ↓
pointage_semaine_calcule (DB)
       ↓
pointage_cumul_periode (DB)
       ↓
pointage_snapshot_semaine (DB)
```

### La Table `pointage_semaine_calcule` est-elle Justifiée ?
**NON.**
1. **La semaine est une unité de calcul, pas une unité de stockage historique.** Le Code du travail impose le seuil de 40 heures par semaine civile pour déclencher les heures supplémentaires (les 8 premières à 30%, les suivantes à 50%).
2. Cette règle exige que le moteur `WeeklyAttendanceCalculator` calcule la semaine. Mais une fois calculée, **la paie est mensuelle** (du 26 au 25 du mois suivant). La paie consomme le total mensuel des HS 30 et HS 50 scellé dans `pointage_cumul_periode`.
3. Actuellement, chaque modification journalière oblige à recalculer et réécrire dans `pointage_semaine_calcule` via `recalculerSemainePourDate`. C'est une source permanente de désynchronisation.
4. Si un utilisateur souhaite consulter le détail hebdomadaire sur l'écran (modal hebdo), ce détail peut être produit **à la volée par le calculateur** ou exposé par une simple fonction/service en mémoire.

### Comparaison des Deux Modèles

| Critère | Modèle Actuel (Semaine Persistée) | Modèle Cible (Semaine en Mémoire) |
| :--- | :--- | :--- |
| **Complexité BDD** | Table `pointage_semaine_calcule` + triggers/upserts | Zéro table hebdomadaire |
| **Cohérence des données** | Risque de semaine périmée par rapport aux jours | Impossibilité physique d'incohérence |
| **Consommation Paie** | Paie lit le cumul période | Paie lit le cumul période |
| **Auditabilité** | Semaine écrasée à chaque recalcul (non auditable) | Traçabilité conservée dans les jours sources |

---

## 10. CUMULS — TABLE OU CALCUL ?

### Audit de `pointage_cumul_indemnite`
- **Définition** : `pointage_cumul_indemnite` stocke `(id_periode_paie, id_employe, id_indemnite, quantite_totale, montant_total)`.
- **Source** : `SUM(pij.quantite), SUM(pij.montant_calcule) FROM pointage_indemnite_jour`.
- **Consommateur** : **AUCUN**. Aucun service de paie, aucun contrôleur ne lit cette table.
- **Verdict : DROP.**

### Audit de `pointage_cumul_periode`
- **Définition** : Représente la synthèse mensuelle complète d'un salarié pour la période (jours ouvrables, travaillés, absences, retards, HS 30, HS 50, nuit, dimanche, férié, repas, déplacement).
- **Rôle Métier** : **C'est le contrat officiel d'interface entre le Pointage et la Paie.**
- **Peut-on la remplacer par une simple vue SQL `SUM()` ?**  
  **NON.** Pourquoi ? Parce qu'à l'instant $T$ où la DAF clique sur "Valider le Pointage", cette photo **doit devenir immuable**. Si un agent modifiait rétroactivement un badgeage du 12 du mois alors que la paie est en cours de virement, une vue dynamique modifierait instantanément les bases de calcul de la paie, ce qui violerait le principe de reproductibilité comptable.
- **Verdict : KEEP.** `pointage_cumul_periode` est la **seule et unique table de snapshot** qui mérite d'exister.

---

## 11. SNAPSHOTS — QUEL EST LE MINIMUM NÉCESSAIRE ?

L'audit révèle 4 tables de snapshots actuellement créées lors de l'appel à `gelerPeriode` :

```
                        ┌── pointage_cumul_periode (LUE par PayrollService)
                        ├── pointage_snapshot_jour (JAMAIS LUE)
gelerPeriode() ─────────┼── pointage_snapshot_semaine (JAMAIS LUE)
                        └── pointage_snapshot_bucket (LUE pour ré-instancier des objets)
```

### Analyse Détaillée des 4 Niveaux

1. **`pointage_snapshot_jour`** :
   - Contient : `(id_cumul, id_pointage, version_pointage, minutes_travaillees, minutes_retard, minutes_nuit, statut_jour)`.
   - Utilisation : Aucune ligne de code ne lit cette table. Elle a été créée par mimétisme d'audit.
   - Perte si suppression : **Aucune.** Les données sources restent dans `pointage_jour` avec leur horodatage et leur version.
   - **Décision : DROP.**

2. **`pointage_snapshot_semaine`** :
   - Contient : `(id_cumul, id_semaine_calcule, debut_semaine, minutes_travaillees, minutes_hs_30, minutes_hs_50)`.
   - Utilisation : Aucune lecture.
   - Perte si suppression : **Aucune.**
   - **Décision : DROP.**

3. **`pointage_snapshot_bucket`** :
   - Contient : `(id_snapshot_bucket, id_cumul, date_debut, date_fin, minutes, overtime_tier, day_special_type, night_work_type)`.
   - Utilisation : Lue par `PayrollService.loadSnapshotBuckets` pour alimenter `OvertimePayCalculator`.
   - Perte si suppression immédiate : la Paie perd son entrée figée de ventilation des collisions HS/nuit/dimanche/férié. Recalculer depuis des jours encore modifiables et une configuration courante ne reproduit pas nécessairement le gel.
   - **Décision : KEEP FOR NOW ; MERGE/REPLACE seulement par un snapshot final équivalent et versionné.**

4. **`pointage_cumul_periode`** :
   - Contient : Les totaux consolidés scellés, avec `version_snapshot`, `statut_snapshot` (`GELE`, `CONSOMME`, `REMPLACE`), `gele_par`, `gele_at`.
   - Utilisation : Référencée par `bulletin_paie.id_snapshot_pointage`. C'est le fondement de la reproductibilité de la paie.
   - **Décision : CONSERVER comme snapshot UNIQUE.**

---

## 12. PÉRIODES DE PAIE ET STATUTS

### 1. Cycle de Vie Réel de la Période

Le cycle de vie effectif est géré par `PayrollPeriodWorkflowService` via l'énumération `PayrollPeriodCommand` :

```
 [BROUILLON]
      │
      │ (OPEN)
      ▼
  [EN_COURS] ◄──────────────┐
      │                     │ (REJECT)
      │ (SUBMIT)            │
      ▼                     │
[TRANSMIS_DAF] ─────────────┘
      │
      │ (APPROVE) -> Déclenche gelerPeriode()
      ▼
  [VALIDEE]
      │
      │ (Calcul Paie & Approbation)
      ▼
[PAIE_VALIDEE]
      │
      │ (CLOSE)
      ▼
 [CLOTUREE] ───────────────► (REOPEN) ───► [EN_COURS]
```

### 2. Justification des Tables de Référence
- **`ref_statut_periode_paie`** : Contient 7 lignes fixes (`BROUILLON`, `EN_COURS`, `A_VALIDER`, `TRANSMIS_DAF`, `VALIDEE`, `CLOTUREE`, `PAIE_VALIDEE`). Les transitions sont déjà strictement codées en Java dans `PayrollPeriodWorkflowService`.
  - *Option moderne recommandée* : Remplacer par un type `VARCHAR(20)` avec contrainte `CHECK (id_statut IN (...))` ou conserver la table sans couche ORM complexe.
- **`periode_paie_transition`** : Trace chaque commande, l'utilisateur auteur, l'ancien état, le nouvel état, l'horodatage et le motif (ex: motif de rejet obligatoire).
  - *Verdict* : **CONSERVER.** Cette table remplit un vrai rôle d'audit légal et de résolution des litiges DAF/Agents.

---

## 13. STATUTS DE POINTAGE JOURNALIER

### Analyse de `ref_statut_pointage_jour`
La table contient 11 statuts :
1. `PRESENT`
2. `ABSENCE_NON_JUSTIFIEE`
3. `ANOMALIE_SAISIE`
4. `ABSENCE_JUSTIFIEE`
5. `CONGE`
6. `FERIE_TRAVAILLE`
7. `FERIE_CHOME`
8. `SAMEDI_TRAVAILLE`
9. `DIMANCHE_TRAVAILLE`
10. `DISPENSE_POINTAGE`
11. `DEMI_JOURNEE`

Ces statuts ne sont **jamais administrés ni configurés par l'utilisateur final** : ils correspondent au vocabulaire immuable du moteur de calcul.
- *Verdict* : Peut être conservé pour l'intégrité référentielle SQL des clés étrangères (`FOREIGN KEY`), mais ne nécessite aucune API de gestion dynamique.

---

## 14. INDEMNITÉS — CHAÎNE DE VALORISATION

### Architecture Actuelle des Indemnités
1. **`ref_indemnite`** : Référentiel des indemnités (ex: `REPAS`, `DEPLACEMENT`).
2. **`site_indemnite_config`** : Montant de référence par site (`DEPLACEMENT` = 31 200 Ar mensuel proratisé sur les jours ouvrables réels, `REPAS` = 2 000 Ar par jour).
3. **`employe_indemnite_derogation`** : Permet d'exclure un cadre ou d'octroyer une indemnité dérogatoire à un salarié spécifique.
4. **`pointage_indemnite_jour`** : Source de vérité quotidienne (`fraction`, `quantite`, `montant_calcule`, `accorde`).
5. **`pointage_cumul_indemnite`** : Table de cumul redondante.

### Verdict sur les Indemnités
- La chaîne `ref_indemnite` → `site_indemnite_config` → `employe_indemnite_derogation` → `pointage_indemnite_jour` est **métier, saine et parfaitement fonctionnelle**.
- La seule anomalie est `pointage_cumul_indemnite` qui doit être **supprimée**.

---

## 15. TOLÉRANCES D'ARRIVÉE — AUDIT SPÉCIFIQUE

### Analyse de `ref_tolerance_arrivee_pointage`
- **Table** : `ref_tolerance_arrivee_pointage (id_tolerance, id_site, heure_plafond)`
- **Observation stupéfiante dans le code** :
  Dans `PointageSaisieService.java` (lignes 1319 à 1327) :
  ```java
  // POUR ACTIVER L'ÉCRÊTAGE : Décommenter le bloc ci-dessous le jour où l'entreprise
  // souhaite appliquer le plafond :
  /*
  LocalTime ltPlafondSite = sH.length > 4 ? sH[4] : null;
  LocalTime ltEmEcrete = (em != null && ltPlafondSite != null) ...
  */
  Time emEffectif = em; // Actuellement désactivé : calcul sur l'heure brute saisie
  ```
  La règle d'écrêtage est **en commentaire dans le code** ! L'heure brute est conservée telle quelle.
- **Verdict Architectural** :
  Créer une table dédiée, une classe bean `RefToleranceArriveePointage.java` de 149 lignes héritant de `ClassMere`, un contrôleur et un hook React pour un seul champ horaire rattaché au site est un non-sens.
  - **Recommandation** : Ajouter une colonne `heure_tolerance_arrivee TIME` directement dans la table `ref_site` et supprimer la table `ref_tolerance_arrivee_pointage`.

---

## 16. CALENDRIER ET JOURS OUVRABLES

### Source Canonique Identifiée : `CalendrierOuvrableEngine.java`
Le moteur résout les jours ouvrables réels selon la règle stricte :
$$\text{Jours Ouvrables Réels} = \text{Jours du Lundi au Vendredi} - (\text{Jours Fériés Chômés} + \text{Périodes Chômées})$$

- Sources vérifiées : `ref_calendrier_ferie`, `site_calendrier_ferie`, `ref_periode_chomee`.
- **Point de conformité majeur** : Aucun fallback arbitraire du type "22 jours par défaut" n'a été détecté dans le backend. Si le calendrier est manquant ou incohérent, le moteur lève une exception explicite `MISSING_WORKING_CALENDAR` ou `SirhBusinessException`.
- **Dette restante** : Dans `PointageSaisieService.java` (ligne 1290), un fallback conventionnel résiduel subsiste si les jours calculés sont $\le 0$ :
  ```java
  if (jours <= 0) {
      jours = CalendrierOuvrableEngine.calculerJoursOuvrables(perDebut, perFin);
  }
  ```
  Ce fallback doit être éliminé lors de la future refactorisation.

---

## 17. CONGÉS → POINTAGE : RECONSTITUTION DU FLUX

### Le Pont Inter-Domaines

```
    [ Module CONGÉS ]
    Table: demande_conge (statut = 'VALIDE' / 'APPROUVEE')
            │
            ▼ (Lecture seule lors du chargement de la grille de saisie)
    [ Module POINTAGE ]
    PointageSaisieService:548
            │
            ▼
    Détection automatique :
    - typeCongeJour = 'FULL_DAY', 'MATIN', ou 'APRES_MIDI'
    - congePaye (boolean)
    - congeDecompteSolde (boolean)
            │
            ▼
    Impact sur la journée pointée :
    - Si FULL_DAY : Heures travaillées forcées à 0h
    - Statut forcé à CONGE (ou ABSENCE_JUSTIFIEE si non payé)
    - Indemnités repas et déplacement neutralisées (0 Ar)
    - Retards neutralisés (0 min)
```

### Anomalie Architecturale Détectée dans `PointageAnomalieService`
Dans `PointageAnomalieService.java` (lignes 1041-1075), lors de la régularisation d'une absence en congé payé, **le service Pointage effectue des `INSERT` directs dans `demande_conge` et `mouvement_solde_conge`** !
- **Violation de domaine caractérisée** : Pointage ne doit jamais manipuler les tables internes de solde de congés. Il doit obligatoirement déléguer cette action à l'API ou au service canonique `CongeService`.

---

## 18. POINTAGE → PAIE : CE QUE LE MOTEUR CONSOMME RÉELLEMENT

L'inspection de `PayrollService.java` (lignes 680 à 725) et de `OvertimePayCalculator.java` révèle exactement ce que la Paie consomme :

| Champ Requis par la Paie | Source Actuelle Pointage | Présent dans `pointage_cumul_periode` ? | Statut Réel |
| :--- | :--- | :---: | :--- |
| `id_cumul` / `version_snapshot` | `pointage_cumul_periode.id_cumul` | **OUI** | Identifiant de preuve |
| `heures_normales` | `total_heures_travaillees` | **OUI** | Heures effectives de base |
| `heures_hs_30` | `heures_hs_30` | **OUI** | Majoration 30% |
| `heures_hs_50` | `heures_hs_50` | **OUI** | Majoration 50% |
| `heures_nuit` | `heures_nuit_total` | **OUI** | Heures de nuit scellées |
| `heures_dimanche` | `heures_dimanche_total` | **OUI** | Heures dimanche scellées |
| `heures_ferie` | `heures_ferie_total` | **OUI** | Heures férié scellées |
| `heures_absence` | `heures_absence_retenues` | **OUI** | Retenue pour absence |
| `minutes_retard` | `total_minutes_retard` | **OUI** | Suivi / décompte |
| `jours_ouvrables` | `nb_jours_ouvrables` | **OUI** | Calendrier de référence |
| `jours_travailles` | `nb_jours_presents` | **OUI** | Présence réelle |
| `jours_absence` | `nb_jours_absence` | **OUI** | Jours d'absence |
| `jours_conges` | Résolu dynamiquement par `CongeService` | Non (Ignoré du pointage) | Canonique Congés |
| `indemnite_repas` | `nb_jours_repas` & `montant_repas` | **OUI** | Indemnité scellée |
| `indemnite_deplacement` | `nb_jours_deplacement` & `montant_deplacement`| **OUI** | Indemnité scellée |
| `buckets` | `pointage_snapshot_bucket` | **NON** (Table séparée) | Reconstructible à la volée |

### Constat Fondamental
`pointage_cumul_periode` contient les totaux principaux, mais **pas 100 % de l'entrée effectivement consommée** : `PayrollService.loadSnapshotBuckets` charge aussi les segments qualifiés pour `OvertimePayCalculator`. La cible peut supprimer ce détail relationnel, mais seulement en figeant un résultat de valorisation équivalent ou toutes les données/provenances permettant un replay historique exact.

---

## 19. ANALYSE DES TABLES SUSPECTES (CANDIDATES À L'ÉLIMINATION)

### 1. `pointage_snapshot_jour`
- **Décision : DROP**
- **Démonstration** : Table écrite par une boucle `INSERT` dans `PointageSyntheseService:1169`, mais strictement **aucune requête `SELECT` n'existe dans tout le code Java ni dans le frontend**.
- **Impact** : Zéro régression fonctionnelle. Économie de milliers de lignes SQL par mois.

### 2. `pointage_snapshot_semaine`
- **Décision : DROP**
- **Démonstration** : Écrite dans `PointageSyntheseService:1178`, jamais lue nulle part.
- **Impact** : Zéro régression fonctionnelle.

### 3. `pointage_cumul_indemnite`
- **Décision : DROP**
- **Démonstration** : Insérée par `PointageSyntheseService:1136`. `PayrollService` ne la lit pas car il utilise les montants pré-agrégés de `pointage_cumul_periode`.
- **Impact** : Zéro régression fonctionnelle.

### 4. `pointage_snapshot_bucket`
- **Décision : KEEP FOR NOW ; candidat MERGE/REWRITE du contrat Pointage→Paie**
- **Démonstration** : donnée dérivée, mais lue directement par `PayrollService` et figée avec le cumul. Elle garantit aujourd'hui la qualification temporelle utilisée par la paie.
- **Impact d'une suppression prématurée** : perte de reproductibilité si les jours ou règles changent. Condition de retrait : snapshot final enrichi ou lignes de valorisation immuables, avec tests de replay.

### 5. `pointage_semaine_calcule`
- **Décision : DROP (Remplacer par calcul en mémoire)**
- **Démonstration** : Résultat intermédiaire recalculable à la volée par `WeeklyAttendanceCalculator`.
- **Impact** : Suppression du code complexe de recalcul incrémental et des risques de désynchronisation.

### 6. `ref_tolerance_arrivee_pointage`
- **Décision : MERGE dans `ref_site`**
- **Démonstration** : Une table 1-to-1 pour une seule colonne horaire dont l'application est actuellement désactivée dans le code.

---

## 20. ANALYSE DES FICHIERS SUSPECTS (BACKEND & FRONTEND)

### Backend
1. `AttendancePeriodCalculator.java`, `AttendancePeriodContext.java`, `AttendancePeriodResult.java` :
   - Total : 70 LOC.
   - Diagnostic : Abstraction vide qui se contente de faire `weeks.stream().mapToLong(...).sum()`.
   - Action future : Inliner dans le service de synthèse.
2. `PointageTimeBoundaryMapper.java` :
   - 21 LOC pour une simple division par 60 et un `String.format`.
   - Action future : Inliner.
3. `RefToleranceArriveePointage.java` :
   - 149 LOC pour encapsuler une table inutile.
   - Action future : Supprimer lors de la fusion avec `ref_site`.
4. `PointageAnomalieService.java` (1113 LOC) et `PointageEmployeService.java` (921 LOC) :
   - Diagnostic : Dispersion des responsabilités de lecture et mise à jour de `pointage_jour`.
   - Action future : Regrouper dans un service unifié `PointageService`.

### Frontend
1. `pointageCalculations.js` (275 LOC) :
   - Diagnostic : Duplique intégralement la logique métier du backend. Tout changement de règle dans Java exige une réécriture manuelle dans ce fichier JS.
   - Action future : Rendre le frontend plus passif en exploitant les réponses calculées par l'API backend lors des saisies.
2. `DafAjustementPointageModal.jsx` (589 LOC) :
   - Diagnostic : Quasi-clone de `AnomalieRegularisationModal.jsx`.
   - Action future : Fusionner en un seul modal générique d'arbitrage de ligne.
3. `PointageGanttView.jsx` (597 LOC) :
   - Diagnostic : Composant lourd, très peu adapté aux volumes de production d'un agent de saisie qui préfère la vue matricielle rapide (`PointageGridTable.jsx`).

---

## 21. RISQUES TRANSACTIONNELS ET CONCURRENCE

1. **Double Validation / Double Gel** :
   - *Actuel* : Protégé par une contrainte d'unicité conditionnelle PostgreSQL :
     `CREATE UNIQUE INDEX uk_cumul_snapshot_courant ON pointage_cumul_periode(id_employe, id_periode_paie) WHERE statut_snapshot = 'GELE';`
   - *Appréciation* : **Excellente pratique architecturale à préserver impérativement.**
2. **Conflit de Saisie Concurrente sur les Badgeages** :
   - *Actuel* : Contrôlé par la colonne `version` dans `pointage_jour` avec clause `WHERE id_pointage = ? AND version = ?`.
   - *Appréciation* : Verrouillage optimiste robuste.
3. **Modification en Période Fermée** :
   - *Actuel* : Contrôle Java `psCheckPer` vérifiant le statut de la période.
   - *Risque* : Absence de trigger BDD interdisant l'écriture dans `pointage_jour` si `periode_paie.id_statut_periode` est `VALIDEE` ou `CLOTUREE`.
   - *Recommandation cible* : Ajouter une règle ou contrainte d'intégrité formelle en base.

---

## 22. ARCHITECTURE CIBLE MINIMALE THÉORIQUE

Si nous devions reconstruire le module Pointage aujourd'hui avec les stricts besoins de GALAXAM :

### A. Modèle BDD cible (8 à 10 tables au lieu de 16)

```
[ Référentiels & Configuration ]
├── ref_statut_periode_paie     (ou contrainte CHECK sur periode_paie)
├── periode_paie                (Entité période, dates, état, version)
├── periode_paie_transition     (Audit trail des actions et rejets)
├── ref_site                    (Contient désormais heure_tolerance_arrivee)
├── ref_indemnite               (Référentiel Repas, Déplacement)
├── site_indemnite_config       (Montants par site et périodicité)
└── employe_indemnite_derogation(Dérogations individuelles)

[ Données Opérationnelles Quotidiennes ]
├── pointage_jour               (Badgeages bruts, statuts, détection retards)
└── pointage_indemnite_jour     (Indemnités quotidiennes accordées)

[ Scellement / Interface Paie ]
├── pointage_cumul_periode      (snapshot final scellé consommé par la Paie)
└── [preuve_valorisation_temps] (option : table séparée, ou colonnes/provenance intégrées au snapshot final)
```

Le dessin ci-dessus compte 10 tables propres au domaine dans sa variante conservatrice. Il descend à 8 si les deux référentiels de statuts fixes deviennent des `CHECK`/enums et si la preuve de valorisation est intégrée au snapshot final. Le nombre 8 n'est donc pas compatible avec la conservation inchangée de tous les blocs dessinés.

### B. Modèle Applicatif Backend Cible (1 Moteur, 2 Services, 2 Contrôleurs)

1. **Calculateur Pur (`AttendanceCalculatorEngine`)** :
   - Responsabilité : Calcul pur en mémoire sans JDBC.
   - Méthodes : `calculateDay(...)`, `calculateWeek(...)`, `calculateMonthlySummary(...)`.
2. **Service Métier (`PointageService`)** :
   - Responsabilité : Grille quotidienne, enregistrement batch, régularisation anomalies, fiche employé.
3. **Service de Scellement & Cycle de Vie (`PointagePeriodeService` ou intégré à `PayrollPeriodWorkflowService`)** :
   - Responsabilité : Validation, scellement (`gelerPeriode`), réouverture.
4. **Contrôleurs REST épurés** :
   - `PointageSaisieController` : Saisie quotidienne et anomalies.
   - `PointageSyntheseController` : Synthèse mensuelle, export Excel et fiche employé.

---

## 23. TABLEAU SYNTHÉTIQUE ACTUEL → CIBLE

### BDD (Tables)

| Table Actuelle | Catégorie | Verdict Cible | Justification Métier |
| :--- | :--- | :--- | :--- |
| `ref_statut_periode_paie` | CONFIG | **KEEP** | Standardise les 7 statuts du workflow |
| `periode_paie` | SOURCE | **KEEP** | Entité racine indispensable |
| `periode_paie_transition` | AUDIT | **KEEP** | Traçabilité légale des rejets et approbations |
| `ref_statut_pointage_jour` | CONFIG | **KEEP** | Intégrité relationnelle des statuts de journée |
| `ref_indemnite` | CONFIG | **KEEP** | Référentiel des indemnités |
| `site_indemnite_config` | CONFIG | **KEEP** | Tarification par site |
| `ref_tolerance_arrivee_pointage`| CONFIG | **DROP (MERGE)** | Fusionner dans une colonne de `ref_site` |
| `employe_indemnite_derogation`| CONFIG | **KEEP** | Dérogations individuelles indispensables |
| `pointage_jour` | SOURCE | **KEEP** | **Cœur battant du système (Donnée source)** |
| `pointage_indemnite_jour` | SOURCE/CALCUL| **KEEP** | **Source unique des indemnités effectives** |
| `pointage_semaine_calcule` | CALCUL | **DROP** | 100% recalculable en mémoire par le moteur hebdo |
| `pointage_cumul_periode` | SNAPSHOT | **KEEP** | **Snapshot scellé UNIQUE consommé par la Paie** |
| `pointage_snapshot_jour` | SNAPSHOT | **DROP** | Dead code : jamais lu |
| `pointage_snapshot_semaine` | SNAPSHOT | **DROP** | Dead code : jamais lu |
| `pointage_snapshot_bucket` | SNAPSHOT | **KEEP puis MERGE/REWRITE** | Lu par la Paie ; remplaçable seulement avec preuve historique équivalente |
| `pointage_cumul_indemnite` | CALCUL | **DROP** | Dead code : jamais lu par la Paie |

### Backend Java (Fichiers Majeurs)

| Fichier Actuel | LOC | Verdict Cible | Rôle dans l'Architecture Cible |
| :--- | :---: | :--- | :--- |
| `PointageSaisieController` | 60 | **KEEP** | Point d'entrée saisie |
| `PointageAnomalieController` | 47 | **MERGE** | Fusionner dans `PointageSaisieController` |
| `PointageParametreController` | 167 | **MERGE** | Fusionner dans l'administration générale |
| `PointageSyntheseController` | 183 | **KEEP** | Point d'entrée synthèses & clôture |
| `PointageEmployeController` | 58 | **MERGE** | Fusionner dans `PointageSyntheseController` |
| `PointageSaisieService` | 1706 | **MERGE** | Cœur du futur `PointageService` |
| `PointageAnomalieService` | 1113 | **MERGE** | Fusionner dans le futur `PointageService` |
| `PointageEmployeService` | 921 | **MERGE** | Fusionner dans le futur `PointageService` |
| `PointageSyntheseService` | 1914 | **SIMPLIFY**| Alléger de toute la gestion des buckets/snapshots morts |
| `DailyAttendanceCalculator` | 209 | **KEEP** | Moteur de calcul pur jour |
| `WeeklyAttendanceCalculator` | 74 | **KEEP** | Moteur de calcul pur semaine (quotas HS 40h) |
| `AttendancePeriodCalculator` | 32 | **INLINE** | Simple somme à intégrer dans la synthèse |
| `AttendanceTemporalPartitioner`| 171 | **MOVE TO PAIE**| Déplacer dans le domaine Paie (détail interne de valorisation) |
| `OvertimeProjectionEngine` | 131 | **MOVE TO PAIE**| Déplacer dans le domaine Paie |
| `TemporalBucket` / `QualifiedTimeBucket` | 120 | **MOVE TO PAIE**| Records internes au calcul monétaire des majorations |
| `RefToleranceArriveePointage` | 149 | **DELETE** | Supprimer (propriété de `RefSite`) |

---

## 24. PLAN DE MIGRATION POTENTIEL (POUR LA PHASE ULTÉRIEURE)

> [!IMPORTANT]
> Conformément aux consignes strictes de la mission d'audit, **aucune modification n'est réalisée lors de cette phase**. Le plan ci-dessous constitue la feuille de route technique recommandée pour la mission de refactorisation ultérieure.

### Étape 1 : Nettoyage BDD Zéro Risque (Tables Mortes Démontrées)
- Supprimer les tables `pointage_snapshot_jour`, `pointage_snapshot_semaine`, et `pointage_cumul_indemnite`.
- Nettoyer les requêtes d'insertion correspondantes dans `PointageSyntheseService.java`.
- *Impact fonctionnel : Strictement nul (code mort).*

### Étape 2 : Réduire l'exposition des buckets sans perdre la reproductibilité
- Définir le contrat historique minimal : totaux par combinaison de majoration, version des paramètres/règles et provenance des jours.
- Faire un replay de périodes clôturées et comparer chaque rubrique monétaire avant/après.
- Alors seulement, intégrer cette preuve au snapshot final ou aux lignes de bulletin et retirer `pointage_snapshot_bucket`.

### Étape 3 : Suppression de `pointage_semaine_calcule`
- Remplacer les lectures de `pointage_semaine_calcule` par des appels dynamiques à `WeeklyAttendanceCalculator.calculateWeek`.
- Supprimer la table `pointage_semaine_calcule`.

### Étape 4 : Consolidation des Services Backend
- Regrouper `PointageSaisieService`, `PointageAnomalieService` et `PointageEmployeService` dans une classe unifiée et cohérente `PointageService.java`.
- Déléguer la création de congés lors d'une régularisation à `CongeService`.

### Étape 5 : Allègement Frontend
- Supprimer les calculs métier redondants de `pointageCalculations.js`.
- Fusionner `DafAjustementPointageModal` dans `AnomalieRegularisationModal`.

---

## 25. QUESTIONS MÉTIER RESTANTES

Avant toute décision de refactorisation, les arbitrages suivants doivent être validés avec le métier GALAXAM :

1. **Application réelle de la tolérance d'arrivée** :
   - *Question* : L'entreprise souhaite-t-elle réellement écrêter les arrivées avant 07h30 pour le décompte des heures travaillées, ou le calcul sur l'heure brute badgée doit-il rester la règle définitive ?
2. **Validation formelle intermédiaire des semaines** :
   - *Question* : L'Agent de Pointage doit-il impérativement "valider et verrouiller" chaque semaine individuellement au cours du mois, ou seule la validation mensuelle globale avant paie est-elle requise ? (Si seule la validation mensuelle compte, `pointage_semaine_calcule` disparaît immédiatement).
3. **Cas d'usage réels du travail de nuit et du dimanche** :
   - *Question* : Quel est le pourcentage réel de salariés GALAXAM effectuant du travail de nuit (22h-05h) ou de dimanche ? Si ces cas ne concernent que des gardiens ou postes spécifiques, la tuyauterie des collisions de majorations peut être drastiquement simplifiée.

---

## 26. ANNEXES DE CONTRÔLE

### 26.1 Test d'existence des 16 tables

Lecture compacte des dix questions demandées : `S/C/D/H/A` = source, configuration,
dérivé, historique, audit ; « exact » signifie reproductible avec les informations
figées, pas seulement recalculable avec la configuration du jour.

| Table | Nature | Recalcul exact ? | Pourquoi PostgreSQL ? / perte si retrait | Même information / cycle fusionnable | Verdict |
| :--- | :--- | :---: | :--- | :--- | :--- |
| `ref_statut_periode_paie` | C fixe | N/A | FK lisible ; perte faible avec enum+CHECK | doublé par commandes/états Java | INLINE possible |
| `periode_paie` | S/H | non | borne, état, version, acteurs | aucun équivalent | KEEP |
| `periode_paie_transition` | A append-only | non | preuve qui/quand/pourquoi | cycle distinct de la période courante | KEEP |
| `ref_statut_pointage_jour` | C fixe | N/A | attributs `decompte_absence`, `necessite_saisie` | vocabulaire aussi interprété en Java/UI | KEEP ou enum+CHECK après centralisation |
| `ref_indemnite` | C | non | types, fiscalité, activation | aucun | KEEP |
| `site_indemnite_config` | C datée | non | montant applicable par site/date | ne pas fusionner au résultat | KEEP |
| `ref_tolerance_arrivee_pointage` | C courante | non | réglage 1:1 du site | même cycle que site si non historisé | MERGE candidat |
| `employe_indemnite_derogation` | C datée | non | exception individuelle et motif | cycle autonome | KEEP |
| `pointage_jour` | S + résultats jour | non | saisie, provenance congé, version | mélange déjà brut et dérivé | KEEP, REWRITE ciblé éventuel |
| `pointage_indemnite_jour` | S/résultat accordé | non | décision effective et montant du jour | le total existe aussi dans `pointage_jour` | KEEP ; retirer seulement la dénormalisation |
| `pointage_semaine_calcule` | D | oui avant gel ; historique incertain | consultation/validation hebdo courante | calculateur hebdo fait autorité | REPLACE_BY_CALCULATION/VIEW si validation semaine non métier |
| `pointage_cumul_periode` | H | non | contrat figé Pointage→Paie | aucun équivalent complet | KEEP |
| `pointage_snapshot_jour` | H technique | partiellement | lie versions de jours au cumul | non lu au runtime, mais preuve de composition | DROP seulement après politique d'audit validée |
| `pointage_snapshot_semaine` | H technique | oui depuis snapshot jour+règles figées | preuve intermédiaire | même gel que cumul | PROBABLY_DROP |
| `pointage_snapshot_bucket` | H/D | non sans règles figées | entrée réelle de valorisation Paie | peut être compacté dans preuve finale | KEEP puis MERGE/REWRITE |
| `pointage_cumul_indemnite` | D | oui depuis indemnités jour | optimisation non consommée | montants déjà dans cumul période | DROP candidat fort |

### 26.2 Inventaire complet des fichiers backend et futur propriétaire

Les 44 fichiers sont couverts ci-dessous. `KEEP*` signifie conserver la responsabilité,
pas nécessairement le fichier sous sa forme actuelle.

| Famille / fichiers | Verdict | Futur propriétaire / justification |
| :--- | :--- | :--- |
| `PeriodePaieController` | KEEP* | workflow période partagé Pointage/Paie |
| `PointageSaisieController` | KEEP | API saisie |
| `PointageSyntheseController` | KEEP* | API lecture/synthèse |
| `PointageAnomalieController`, `PointageEmployeController` | MERGE possible | contrôleur Pointage si les routes restent cohérentes |
| `PointageParametreController` | MOVE/MERGE | administration de configuration |
| `DerogationRequest`, `ToleranceRequest` | KEEP/INLINE selon API | contrats d'écriture admin |
| `FichePointageEmployeDTO`, `PointageAnomalieDTO`, `PointageJourDTO`, `SaisiePointageJourDTO` | KEEP* | contrats API utiles, à élaguer champ par champ |
| `PeriodePaieDTO` | KEEP | contrat de workflow |
| `PointageSyntheseMensuelleDTO`, `PointageTronconSemaineDTO` | REWRITE | ne pas exposer la persistance technique hebdomadaire |
| `PointageSaisieService` | KEEP/REWRITE | orchestration transactionnelle de saisie |
| `PointageSyntheseService` | REWRITE | gel + lectures, responsabilités aujourd'hui trop nombreuses |
| `PointageAnomalieService` | KEEP/REWRITE | régularisation ; doit appeler Congés au lieu d'écrire son ledger |
| `PointageEmployeService` | MERGE possible | projection de lecture employé |
| `PointageExcelExportService` | KEEP | export, responsabilité autonome |
| `DailyAttendanceCalculator`, `DailyAttendanceContext`, `DailyAttendanceResult` | KEEP | vérité calcul jour pure |
| `WeeklyAttendanceCalculator`, `WeeklyAttendanceContext`, `WeeklyAttendanceResult`, `WeeklyPriorState` | KEEP | vraie règle hebdomadaire 40 h et semaines tronquées |
| `PeriodWeekSegmenter` | KEEP | découpage nécessaire au calcul, pas forcément à la BDD |
| `AttendancePeriodCalculator`, `AttendancePeriodContext`, `AttendancePeriodResult` | INLINE/MERGE | agrégation simple de période |
| `WorkInterval`, `WorkIntervalFactory`, `TemporalBucket`, `AttendanceTemporalPartitioner` | KEEP interne / MOVE | segmentation nécessaire aux règles nuit/jour spécial |
| `QualifiedTimeBucket`, `OvertimeProjectionEngine`, `OvertimeTier`, `DaySpecialType`, `NightWorkType` | KEEP interne / MOVE | contrat de valorisation Paie, à cacher aux API/UI |
| `CalendrierOuvrableEngine` | KEEP | source canonique calendrier |
| `PointageTimeBoundaryMapper` | INLINE | helper mono-usage |
| `EmployeIndemniteDerogation` | KEEP* | configuration datée ; JDBC peut être rapproché du service admin |
| `RefToleranceArriveePointage` | DELETE après MERGE | disparaît si réglage absorbé par configuration site |

### 26.3 Frontend, vues, contraintes et mesures

- Frontend `features/attendance` : **23 fichiers, 8 955 lignes**. En ajoutant
  `PointageTab.jsx` et les trois fichiers d'administration directement liés
  (`ToleranceArriveeSiteTab`, `DerogationIndemnitesTab`, `pointageParametreService`),
  le périmètre élargi compte **27 fichiers, 10 685 lignes**.
- Le schéma canonique recrée **une vue**, `v_pointage_indemnites`; les quatre autres
  noms au début du script sont des vues historiques explicitement supprimées.
- Invariants BDD forts : unicité employé/date, indemnité/type/fraction,
  semaine/employé/période, snapshot/version et un seul snapshot `GELE` courant.
  Risques restant surtout en Java : interdiction d'écrire une période fermée,
  légalité des transitions, non-chevauchement des configurations datées et
  cohérence entre totaux dénormalisés et lignes d'indemnité.
- Temps : les calculateurs utilisent correctement des minutes `long`, mais les
  services/DTO/SQL conservent des heures décimales et repassent par `double`
  (`minutesToHours`, `getDouble`, statistiques). Dette : choisir les minutes comme
  unité interne et convertir seulement aux frontières.
- Argent : le backend de calcul utilise majoritairement `BigDecimal` et
  `MoneyRoundingPolicy`. Le frontend recalcule cependant des montants avec
  `Number`, `Math.round` et `/2`; ces valeurs doivent rester prévisionnelles et
  le montant serveur doit faire autorité.
- Sauts de compréhension observés : saisie jour 4–5 (page→hook→API→controller→service→calculator),
  semaine 5–6, gel 6–8, buckets/snapshot 7–9, consommation Paie 5–6, indemnité
  7–9. La cible doit ramener chaque scénario à un contrôleur, un service
  transactionnel et un calculateur pur, sans imposer de classe « Engine » unique.

### 26.4 Limites et critères de décision avant migration

Cet audit est statique : il démontre les lecteurs/écrivains présents dans le dépôt,
pas les requêtes externes ni les obligations d'archivage décidées hors code. Avant
tout `DROP`, vérifier les volumes et usages en production, exporter les périodes
clôturées, faire un replay de paie, et obtenir une décision métier sur la validation
hebdomadaire et le niveau de détail exigé en cas de contestation.

## 27. RÉPONSE FINALE OBLIGATOIRE

> *"Si je devais réduire Pointage aujourd'hui sans perdre aucune capacité métier importante :"*

### Estimations Chiffrées d'Audit

- **Tables actuelles** : **16 tables**
- **Tables cible estimées** : **8 à 10 tables** *(8 si les statuts fixes deviennent des contraintes et si la preuve bucket est intégrée ; 10 dans la variante conservatrice)*
- **Fichiers backend actuels** : **44 fichiers Java**
- **Fichiers cible estimés** : **25 à 30 fichiers Java** *(la borne dépend du maintien de DTO publics séparés)*

### Concepts Techniques Actuels (À Éliminer ou Réduire en Détails Internes)
- `pointage_snapshot_jour` (table fantôme)
- `pointage_snapshot_semaine` (table fantôme)
- `pointage_cumul_indemnite` (table fantôme)
- `pointage_snapshot_bucket` (segmentation infra-journalière persistée)
- `pointage_semaine_calcule` (agrégation hebdomadaire persistée)
- `ref_tolerance_arrivee_pointage` (table isolée pour un seul champ horaire)
- `AttendancePeriodCalculator` / `AttendancePeriodResult` (surcouche vide)
- `PointageTimeBoundaryMapper` (micro-classe utilitaire)
- Calcul miroir des badgeages et statuts dans React (`pointageCalculations.js`)

### Concepts Métier Réellement Nécessaires (À Conserver Absolument)
1. **La Période de Paie & son Audit Trail** (`periode_paie`, `periode_paie_transition`) : pour borner la saisie et tracer les rejets/validations DAF.
2. **Le Pointage Journalier Brut** (`pointage_jour`) : source de vérité inviolable portant les 4 heures badgées, les retards et la justification d'absence.
3. **Les Indemnités Journalières Réelles** (`pointage_indemnite_jour`) : source de vérité des repas et déplacements octroyés.
4. **Le Snapshot Mensuel Scellé** (`pointage_cumul_periode`) : unique photo figée transmise au moteur de paie garantissant l'immuabilité financière des bulletins.
5. **Le Moteur de Calendrier Canonique** (`CalendrierOuvrableEngine`) : garantissant le calcul exact des jours ouvrables, fériés et chômés par site.
6. **Le Moteur d'Heures Supplémentaires Hebdomadaire** (`WeeklyAttendanceCalculator`) : appliquant les seuils légaux des 40 heures et des tranches 30%/50% en mémoire.

### Les 5 Simplifications Ayant le Plus de Valeur Immédiate

1. **Suppression immédiate des 3 tables mortes (`pointage_snapshot_jour`, `pointage_snapshot_semaine`, `pointage_cumul_indemnite`)** :
   - *Gain* : Zéro risque de régression, élimination de centaines de lignes de code SQL mort dans `PointageSyntheseService`.
2. **Réduire les buckets à un détail interne avec preuve finale compacte** :
   - *Gain* : simplification de l'architecture sans sacrifier le replay historique ; la suppression physique vient après migration et comparaison des rubriques de paie.
3. **Suppression de la persistance hebdomadaire (`pointage_semaine_calcule`)** :
   - *Gain* : Suppression des cascades de recalculs et d'invalidation à chaque badgeage journalier. La semaine redevient un calcul dynamique à la demande.
4. **Fusion de `ref_tolerance_arrivee_pointage` dans `ref_site`** :
   - *Gain* : Élimination d'une table, d'une classe Java `ClassMere` de 149 lignes, d'un contrôleur et d'un hook frontend pour un simple attribut de configuration du site.
5. **Unification des services de pointage en un service canonique `PointageService`** :
   - *Gain* : Fin de la dispersion du code métier entre `PointageSaisieService` (1 706 LOC), `PointageSyntheseService` (1 914 LOC), `PointageAnomalieService` (1 113 LOC) et `PointageEmployeService` (921 LOC), ramenant la base de code à un ensemble compact, lisible et auditable.
