# Module Pointage & Présence — Spécification Complète & Rapport de Référence
## GALAXAM SIRH — Documentation Canonique & Métier

> **Auteur** : Équipe d'Ingénierie, Architecture Logicielle & Direction Financière GALAXAM SIRH  
> **Module** : Pointage, Présence, Suivi des Temps, Compensation Hebdomadaire, Sujétions Temporelles, Indemnités, Snapshots & Gel de Paie  
> **Version** : 4.1.0 (Alignement canonique sur le code source réel — Suite de 83 tests JUnit 5 verts, 100% certifiés)  
> **Date de référence** : Octobre 2026  
> **Principe directeur** : Le code source Java et PostgreSQL est le moteur et la source principale de vérité du système. Les règles de gestion (RG) sont étayées et justifiées par les dispositions du Code du Travail malgache.  
> **Sources juridiques et normatives de référence** :  
> - **Loi n° 2024-014 du 1er juillet 2024** portant Code du Travail de Madagascar (Articles 109 à 116, 120 à 124, 127, 128)  
> - **Loi n° 2003-044 du 28 juillet 2004** portant ancien Code du Travail (Articles 75, 76, 77, 78, 80, 81, 85, 87, 88 — conservés pour comparatif et audit historique)  
> - **Décret n° 2007-563 du 03 juillet 2007** relatif à la durée légale du travail (40 heures hebdomadaires = 173,33 heures mensuelles, diviseur horaire légal)  
> - **Décret n° 72-226 du 06 juillet 1972** fixant le régime des heures supplémentaires (30% pour les 8 premières heures, 50% au-delà) et des majorations pour travail de nuit, du dimanche et des jours fériés  
> - **Arrêté n° 198-IGT du 12 janvier 1954** relatif aux modalités de contrôle du temps de travail et tenue du registre d'émargement / badgeage  
> - **Schémas de base de données canoniques** : `backend/conception/bdd/05_schema_pointage.sql`, `02_schema_referentiels_rh.sql` et `04_schema_conges.sql`

---

## Sommaire Exécutif

Le module **Pointage & Présence** du SIRH GALAXAM constitue le moteur opérationnel et comptable chargé de capturer, qualifier, régulariser et sceller l'activité temporelle des collaborateurs. Loin d'être un simple registre déclaratif ou une feuille d'émargement bureautique, ce module est le **pivot financier en amont de la Paie** : chaque badgeage enregistré conditionne directement le calcul des salaires de base, les retenues pour absence ou retard, les majorations pour heures supplémentaires et sujétions (nuit, dimanche, jours fériés), ainsi que l'attribution des indemnités de déplacement et de restauration.

Le présent document consacre l'alignement rigoureux entre **le code source réel** et les règles de gestion :

1. **La philosophie centrale « Le backend calcule, le frontend affiche »** : Aucune formule arithmétique de temps, aucun arbitrage de statut journalier, aucun calcul de retard ou d'heures supplémentaires n'est délégué au navigateur. Le moteur pur Java [`AttendanceEngine.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/pointage/domain/calculation/AttendanceEngine.java) constitue l'unique juge du calcul journalier et hebdomadaire, invoqué de façon identique en prévisualisation temps réel (*preview*) et lors de la persistance batch.
2. **Le plancher d'arrivée valorisé (*Earliest Paid Start*)** : Règle intégrée directement dans `ref_site` (`plancher_arrivee_actif`, `heure_entree_minimale_valorisee`). L'heure d'entrée effective valorisée est arrêtée à $\max(\text{badge}, \text{plancher})$ sans aucun repli implicite en dur.
3. **Le principe fondamental de compensation hebdomadaire des heures** : Conformément à la législation malagasy (Loi n° 2024-014 Art. 109 & 110), les dépassements journaliers ne génèrent pas d'heures supplémentaires au jour le jour. Ils viennent prioritairement combler les déficits constatés sur d'autres journées ouvrables de la même semaine civile avant de prétendre à majoration.
4. **La qualification spatio-temporelle en buckets orthogonaux ([`TemporalAttendanceEngine.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/pointage/domain/calculation/TemporalAttendanceEngine.java))** : Pour valoriser avec exactitude le travail en horaires atypiques (nuits, dimanches, fériés) sans double comptage, le temps de travail est découpé en tranches élémentaires (`QualifiedTimeBucket`) portant simultanément une qualification calendaire et un palier de majoration (Normal, HS 30%, HS 50%).
5. **Le solde horaire global de période & Décisions DAF ([`PointageAjustementPeriodeService.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/pointage/application/PointageAjustementPeriodeService.java))** : Arbitrage signé et motivé ($\ge 5$ caractères) appliqué au bilan net de fin de période ($Solde = HS30 + HS50 - \text{Déficit} + \text{Ajustements}$), avec reventilation des quotas finaux figés dans `pointage_cumul_periode` (`minutes_absence_finale`, `minutes_hs30_finales`, `minutes_hs50_finales`).
6. **Le garde-fou d'intégrité temporelle ([`PointageTemporalConfigurationGuard.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/pointage/application/PointageTemporalConfigurationGuard.java))** : Préflight vérifiant avant toute agrégation ou gel l'unicité stricte de l'affectation active et des configurations d'indemnités sur chaque jour ouvré, bloquant tout traitement ambigu.
7. **La régularisation des anomalies avec consommation du solde de congé** : Une journée isolée applique la couverture partielle historique dans la limite du solde disponible (statut `DEMI_JOURNEE`). Une régularisation multi-jours exige un solde suffisant pour tout l'intervalle avec date de reprise. Interdiction stricte de tout solde négatif.
8. **L'immutabilité absolue du gel de période (`APPROVE`) et l'auditabilité des snapshots** : Dès l'approbation DAF, la synthèse mensuelle est figée dans `pointage_cumul_periode` (statut `GELE`), tandis que la provenance exacte des journées, des indemnités et des tranches temporelles est scellée dans les 4 tables immuables de snapshots. Le moteur de Paie ([`PayrollService.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/paie/application/PayrollService.java)) consomme exclusivement ces structures gelées.

---

## Table des Matières

1. [Cadre Légal, Réglementaire & Normatif Malagasy](#1-cadre-légal-réglementaire--normatif-malagasy)
2. [Acteurs, Rôles & Matrice des Responsabilités (RBAC)](#2-acteurs-rôles--matrice-des-responsabilités-rbac)
3. [Cycle de Vie d'une Période de Paie & Machine à États](#3-cycle-de-vie-dune-période-de-paie--machine-à-états)
4. [Saisie Quotidienne, Horaires de Site & Plancher d'Arrivée](#4-saisie-quotidienne-horaires-de-site--plancher-darrivée)
5. [Moteur de Calcul Journalier (`AttendanceEngine`)](#5-moteur-de-calcul-journalier-attendanceengine)
6. [Moteur de Compensation Hebdomadaire & Heures Supplémentaires](#6-moteur-de-compensation-hebdomadaire--heures-supplémentaires)
7. [Moteur Temporel & Qualification des Buckets (`TemporalAttendanceEngine`)](#7-moteur-temporel--qualification-des-buckets-temporalattendanceengine)
8. [Rapprochement Horaire & Solde Global DAF](#8-rapprochement-horaire--solde-global-daf)
9. [Régime des Absences, Retards & Pont Transactionnel Congés](#9-régime-des-absences-retards--pont-transactionnel-congés)
10. [Architecture des Indemnités Journalières, Forfaits & Deuxième Repas](#10-architecture-des-indemnités-journalières-forfaits--deuxième-repas)
11. [Le Protocole de Clôture : Gel, Snapshots & Immutabilité](#11-le-protocole-de-clôture--gel-snapshots--immutabilité)
12. [Consommation des Données Scellées par le Moteur de Paie](#12-consommation-des-données-scellées-par-le-moteur-de-paie)
13. [Architecture Logicielle Backend & Catalogue d'Endpoints](#13-architecture-logicielle-backend--catalogue-dendpoints)
14. [Architecture Frontend, IHM & Composant Gantt](#14-architecture-frontend-ihm--composant-gantt)
15. [Dictionnaire de Données PostgreSQL & Modèle Relationnel](#15-dictionnaire-de-données-postgresql--modèle-relationnel)
16. [Catalogue Exhaustif des Règles de Gestion (RG)](#16-catalogue-exhaustif-des-règles-de-gestion-rg)
17. [Cas d'Usage Chiffrés & Scénarios de Référence](#17-cas-dusage-chiffrés--scénarios-de-référence)

---

## 1. Cadre Légal, Réglementaire & Normatif Malagasy

Le module Pointage & Présence traduit fidèlement en logique logicielle les prescriptions de l'ordre public social malagasy :

### 1.1 Tableau Synoptique des Fondements Juridiques

| Texte Légal (Loi 2024-014 / Loi 2003-044) | Article / Décret | Portée Juridique & Règle Métier | Traduction Technique dans GALAXAM SIRH |
| :--- | :--- | :--- | :--- |
| **Loi 2024-014, Art. 109**<br>*(Loi 2003-044, Art. 75)* | **Durée légale du travail** | Fixée à **40 heures par semaine** dans tous les établissements non agricoles. Base mensuelle légale à **173,33 heures**. | Seuil hebdomadaire paramétré à $40\text{ h} = 2\,400\text{ minutes}$. Diviseur légal officiel de 173,33h (`HEURES_MENSUELLES_LEGALES`). |
| **Loi 2024-014, Art. 110-112**<br>*(Loi 2003-044, Art. 76)* | **Heures supplémentaires** | Heures effectuées au-delà de la durée légale hebdomadaire donnant lieu à majoration. | Moteur `AttendanceEngine.calculateWeek()` : déclenchement des HS au-delà de l'objectif hebdomadaire net de la semaine. |
| **Décret n° 72-226, Art. 2 & 3** | **Taux de majoration des heures supplémentaires** | • 8 premières heures sup (41e à 48e heure incluse) : **+30%**.<br>• Au-delà de la 48e heure hebdomadaire : **+50%**. | Tranche 1 (`seuil_hs_tranche1` = 8h, soit 480 min) imputée à `OVERTIME_30`. Tout excédent imputé à `OVERTIME_50`. |
| **Loi 2024-014, Art. 115**<br>*(Loi 2003-044, Art. 80)* | **Travail de nuit** | Tout travail exécuté entre **22 heures et 5 heures** est considéré comme travail de nuit. | Constantes `NIGHT_START = 22:00` et `NIGHT_END = 05:00`. Découpage systématique aux frontières de nuit par `AttendanceTemporalPartitioner`. |
| **Loi 2024-014, Art. 120 & 121**<br>*(Loi 2003-044, Art. 81)* | **Repos hebdomadaire obligatoire** | Repos hebdomadaire au minimum de 24 heures consécutives, ayant lieu en principe le **dimanche**. | Qualification `DaySpecialType.SUNDAY` des vacations dominicales. Majoration dominicale appliquée aux buckets sans double paiement de base. Nouveau statut `REPOS` (id 12). |
| **Loi 2024-014, Art. 122 & 124** | **Jours fériés chômés et payés** | Les jours de fêtes légales sont chômés et rémunérés. S'ils sont travaillés, ils ouvrent droit à majoration légale. | Qualification `DaySpecialType.PUBLIC_HOLIDAY`. Résolution des fériés nationaux et locaux (`site_calendrier_ferie`) par `CalendrierOuvrableEngine`. |
| **Arrêté n° 198-IGT** | **Obligation de contrôle & Registre d'émargement** | L'employeur doit enregistrer les heures exactes d'entrée et de sortie et tenir un registre de pointage infalsifiable. | `pointage_jour` stocke l'heure brute physique (`heure_entree_matin`) et l'entrée valorisée (`heure_entree_matin_effective`). |

---

## 2. Acteurs, Rôles & Matrice des Responsabilités (RBAC)

La sécurité du module repose sur Spring Security et des permissions déclaratives fines :

| Action Opérationnelle | Endpoint / Service | Rôle AGENT_POINTAGE | Rôle DAF / VALIDATE | Rôle ADMIN |
| :--- | :--- | :---: | :---: | :---: |
| **Consulter les pointages et synthèses** | `GET /pointages/saisie`, `GET /pointages/synthese` | **OUI** (`POINTAGE_READ`) | **OUI** (`POINTAGE_READ`) | **OUI** |
| **Prévisualiser une saisie (Preview)** | `POST /pointages/saisie/preview` | **OUI** (`POINTAGE_READ`) | **OUI** (`POINTAGE_READ`) | **OUI** |
| **Enregistrer des pointages quotidiens (Batch)** | `POST /pointages/saisie/batch` | **OUI** (`POINTAGE_WRITE`) | **OUI** (`POINTAGE_WRITE`) | **OUI** |
| **Régulariser une anomalie (Horaires/Absences)**| `PUT /pointages/anomalies/{id}/regulariser` | **OUI** (`POINTAGE_WRITE`) | **OUI** (`POINTAGE_WRITE`) | **OUI** |
| **Régulariser par Congé Payé Annuel** | `PUT /pointages/anomalies/{id}/regulariser` | **OUI** (Délégation CongeService) | **OUI** | **OUI** |
| **Arbitrage horaire de fin de période** | `POST /pointages/synthese/ajustements` | **NON** (Interdit) | **OUI** (`POINTAGE_VALIDATE`) | **OUI** |
| **Transmettre la période pour arbitrage (SUBMIT)** | `POST /periodes/{id}/actions/SUBMIT` | **OUI** (`POINTAGE_WRITE`) | Non pertinent | **OUI** |
| **Rejeter une période vers la saisie (REJECT)** | `POST /periodes/{id}/actions/REJECT` | **NON** (Interdit) | **OUI** (`POINTAGE_VALIDATE`) | **OUI** |
| **Approuver et sceller le gel (APPROVE)** | `POST /periodes/{id}/actions/APPROVE` | **NON** (Interdit) | **OUI** (`POINTAGE_VALIDATE`) | **OUI** |
| **Rouvrir une période scellée (REOPEN)** | `POST /periodes/{id}/actions/REOPEN` | **NON** (Interdit) | **OUI** (`PAIE_VALIDATE` / DAF) | **OUI** |
| **Mettre à jour le plancher d'arrivée site** | `PUT /referentiel/sites/{id}/plancher` | **NON** | **NON** | **OUI** (`ROLE_ADMIN`) |

---

## 3. Cycle de Vie d'une Période de Paie & Machine à États

Le déroulement du pointage s'inscrit rigoureusement dans le cycle de vie de la période de paie (`periode_paie`), gouverné par `PayrollPeriodWorkflowService` :

```
   ┌──────────────┐
   │  BROUILLON   │
   └──────┬───────┘
          │ Commande : OPEN
          ▼
   ┌──────────────┐      Commande : REJECT (Motif obligatoire)
   │   EN_COURS   │ ◄─────────────────────────────────────────────┐
   └──────┬───────┘                                               │
          │ Commande : SUBMIT                                     │
          │ (Vérification : zéro anomalie bloquante)              │
          ▼                                                       │
   ┌──────────────┐                                               │
   │ TRANSMIS_DAF │ ──────────────────────────────────────────────┘
   │ (A_VALIDER)  │
   └──────┬───────┘
          │ Commande : APPROVE (DAF)
          │ -> Exécution : PointageSyntheseService.gelerPeriode()
          ▼
   ┌──────────────┐
   │   VALIDEE    │ ───► Moteur de Paie : Calcul des bulletins
   └──────┬───────┘      (Consommation exclusive du cumul et des snapshots)
          │ Commande : CLOSE
          ▼
   ┌──────────────┐
   │   CLOTUREE   │ ───► Période archivée et définitivement scellée
   └──────┬───────┘
          │ Commande : REOPEN (Motif >= 5 caractères, rôle DAF/Admin)
          │ -> Exécution : degelerPeriode() -> Cumul précédent marqué 'REMPLACE'
          └─────────────────────────────► (Retour vers EN_COURS)
```

**Étanchéité temporelle sans chevauchement** :
La table `periode_paie` comporte la contrainte d'exclusion GIST suivante :
```sql
CONSTRAINT ex_periode_paie_dates EXCLUDE USING gist (daterange(date_debut, date_fin, '[]') WITH &&)
```
Aucun chevauchement temporel de campagnes de pointage/paie n'est permis au niveau de la base de données.

---

## 4. Saisie Quotidienne, Horaires de Site & Plancher d'Arrivée

### 4.1 Horaires de Référence par Site (`ref_site`)

Chaque collaborateur est rattaché à un site (`ref_site`) qui définit l'organisation du travail :
- **Horaire Continu (`horaire_continu = TRUE`)** : Vacation unique continue (ex: 07:00 – 15:00 ou 08:00 – 16:00). Les colonnes d'amplitude sont `heure_entree_matin` et `heure_sortie_am`.
- **Horaire Discontinu / Standard (`horaire_continu = FALSE`)** : Deux vacations séparées par une pause méridienne :
  1. Matin : `heure_debut_matin` à `heure_fin_matin` (ex : 08:00 – 12:00).
  2. Après-midi : `heure_debut_apres_midi` à `heure_fin_apres_midi` (ex : 13:00 – 17:00).

### 4.2 Le Plancher d'Arrivée Valorisé (*Earliest Paid Start*)

Le plancher d'arrivée n'est **PAS** une tolérance de retard. Il s'agit d'une heure minimale valorisée configurée au niveau de l'établissement (`ref_site.plancher_arrivee_actif` et `ref_site.heure_entree_minimale_valorisee`).  
L'heure d'entrée effective valorisée $H_{effective}$ retenue pour tous les décomptes est calculée par `PointageService.effectiveMorningEntry()` :

$$H_{effective} = \begin{cases} 
\max(H_{badge}, H_{plancher}) & \text{si } \text{ref\_site.plancher\_arrivee\_actif} = \mathbf{TRUE} \\
H_{badge} & \text{si } \text{ref\_site.plancher\_arrivee\_actif} = \mathbf{FALSE} \text{ ou } H_{plancher} \text{ non configuré}
\end{cases}$$

- `pointage_jour.heure_entree_matin` conserve l'heure brute réelle badgée (valeur probante pour l'inspection du travail).
- `pointage_jour.heure_entree_matin_effective` conserve l'heure valorisée après application du plancher.
- Lors du gel mensuel, cette valeur est scellée dans `pointage_snapshot_jour.heure_entree_effective`.
- Aucun repli implicite en dur (`07:30`) n'est toléré dans le code.

---

## 5. Moteur de Calcul Journalier (`AttendanceEngine`)

Le composant pur [`AttendanceEngine.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/pointage/domain/calculation/AttendanceEngine.java) est déterministe et sans état (`stateless`). Il ne réalise aucune requête SQL et manipule son objet d'entrée `AttendanceEngine.Context` :

### 5.1 Algorithme de Calcul des Durées Journalières

Soit la fonction $\Delta(t_1, t_2)$ calculant la durée en minutes entre deux horaires :
1. **Temps Travaillé le Matin ($M_{matin}$)** :
   - Horaire continu : $M_{matin} = \Delta(H_{entree\_matin}, H_{sortie\_am})$.
   - Horaire discontinu : $M_{matin} = \Delta(H_{entree\_matin}, H_{sortie\_matin})$.
   - Pause automatique (*Auto-Break*) : $M_{matin} = \Delta(H_{entree\_matin}, H_{prevue\_sortie\_matin})$.
2. **Temps Travaillé l'Après-Midi ($M_{am}$)** :
   - Horaire continu : $M_{am} = 0$.
   - Horaire discontinu : $M_{am} = \Delta(H_{entree\_am}, H_{sortie\_am})$.
   - Pause automatique : $M_{am} = \Delta(H_{prevue\_entree\_am}, H_{sortie\_am})$.
3. **Temps Travaillé Total ($M_{travaille}$)** :
   $$M_{travaille} = M_{matin} + M_{am}$$
4. **Retard Journalier ($M_{retard}$)** :
   - Collaborateur autonome (`autonomous = TRUE`) : $M_{retard} = 0$.
   - Collaborateur standard :
     $$Retard_{matin} = \max(0, \Delta(H_{prevue\_entree\_matin}, H_{entree\_matin}))$$
     $$Retard_{am} = \max(0, \Delta(H_{prevue\_entree\_am}, H_{entree\_am}))$$
     $$M_{retard} = Retard_{matin} + Retard_{am}$$
5. **Déficit / Absence Journalière ($M_{absence}$)** :
   Pour un jour ouvré standard de 8 heures (480 minutes) :
   $$M_{absence} = \max(0, M_{planifie} - M_{travaille})$$
   - Si `CONGE` ou jour férié chômé légal : $M_{absence} = 0$.
   - Si demi-journée de congé : déduction ciblée de la tranche couverte (240 minutes).

---

## 6. Moteur de Compensation Hebdomadaire & Heures Supplémentaires

### 6.1 Principe Fondamental de Compensation Hebdomadaire

En droit du travail malagasy (Loi n° 2024-014, Art. 109 & 110), la semaine civile (lundi 00:00 au dimanche 24:00) constitue l'unité légale d'appréciation de la durée du travail.  
**Les surplus journaliers d'une semaine compensent prioritairement les déficits des autres jours ouvrables de la même semaine avant d'ouvrir droit aux heures supplémentaires.**

### 6.2 Découpage en Tronçons Hebdomadaires Autonomes (`segmentWeeks`)

Lorsque la période de paie débute en cours de semaine (ex : le 26 du mois), `AttendanceEngine.segmentWeeks()` partitionne la période en tronçons hebdomadaires autonomes.  
**Règle d'étanchéité** : La compensation s'exécute strictement au sein de chaque tronçon. Il n'existe **aucune compensation rétroactive ou anticipée entre deux périodes de paie distinctes**.

### 6.3 Algorithme Mathématique du Calcul Hebdomadaire

Pour chaque tronçon hebdomadaire de $N$ journées :
1. **Temps Travaillé Total Hebdomadaire** : $W_{travaille} = \sum_{j=1}^N M_{travaille}(j)$.
2. **Surplus Journalier Cumulé ($S_{jour}$)** sur les jours ouvrables (hors dimanches et fériés) :
   $$S_{jour} = \sum_{j \in \text{Ouvrables}} \max\Big(0, M_{travaille}(j) - Cible(j)\Big)$$
3. **Manque à Gagner Initial ($Manque_{initial}$)** :
   $$Manque_{initial} = \max\Big(0, W_{attendu} - W_{travaille} + S_{jour}\Big)$$
4. **Minutes Compensées ($M_{compensees}$)** :
   $$M_{compensees} = \min\Big(S_{jour}, Manque_{initial}\Big)$$
5. **Heures Supplémentaires Brutes ($HS_{brutes}$)** :
   $$HS_{brutes} = S_{jour} - M_{compensees}$$
6. **Éligibilité Légale aux Heures Supplémentaires** :
   $$HS_{eligibles} = \begin{cases} 
   HS_{brutes} & \text{si } \text{overtimeEligible} = \mathbf{TRUE} \text{ (Non-cadres, Ouvriers, ETAM)} \\
   0 & \text{si } \text{overtimeEligible} = \mathbf{FALSE} \text{ (Cadres dirigeants, Forfait journalier)}
   \end{cases}$$
7. **Ventilation dans les Paliers de Majoration (Décret n° 72-226)** :
   Soit $P_1 = 8\text{ heures} = 480\text{ minutes}$ (lu dans `paie_config_legale.seuil_hs_tranche1 × 60`) :
   $$HS_{30} = \min(HS_{eligibles}, P_1)$$
   $$HS_{50} = \max(0, HS_{eligibles} - HS_{30})$$
8. **Déficit Restant sur la Semaine** :
   $$Absence_{semaine} = Manque_{initial} - M_{compensees}$$

---

## 7. Moteur Temporel & Qualification des Buckets (`TemporalAttendanceEngine`)

### 7.1 Le Concept de « Bucket Qualifié » (`QualifiedTimeBucket`)

Pour éliminer tout risque de double comptage lors de vacations atypiques (ex: travail un dimanche soir de 20:00 à 23:00, avec créneau de nuit après 22:00 et dépassement d'heures supplémentaires), le temps de travail est découpé en tranches élémentaires qui ne traversent jamais de frontière calendaire (minuit).  
Chaque minute appartient à un **unique bucket** portant de manière orthogonale :
- **Qualification Calendaire (`DaySpecialType`)** : `STANDARD`, `SUNDAY`, `PUBLIC_HOLIDAY`.
- **Qualification Nocturne (`NightWorkType`)** : `NONE`, `NIGHT_UNCLASSIFIED`.
- **Palier de Majoration (`OvertimeTier`)** : `REGULAR`, `OVERTIME_30`, `OVERTIME_50`.

### 7.2 Invariant d'Intégrité Absolue

La somme des durées de tous les buckets qualifiés est garantie strictement égale à la durée totale travaillée constatée par le moteur journalier :
$$\sum \text{Durée}(Bucket) = W_{travaille}$$
Toute divergence déclenche une exception bloquante immédiate : `CONSERVATION_VIOLATION_PROJECTION`.

---

## 8. Rapprochement Horaire & Solde Global DAF

En fin de période, les soldes hebdomadaires sont consolidés au niveau de la période. La DAF peut enregistrer un ajustement signé et motivé via [`PointageAjustementPeriodeService.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/pointage/application/PointageAjustementPeriodeService.java) et la table `pointage_ajustement_periode` :

$$Solde_{periode} = HS30_{hebdo} + HS50_{hebdo} - \text{Déficit}_{hebdo} + \text{Ajustements}_{valides}$$

- **Si $Solde_{periode} < 0$** : le solde net devient une **absence horaire finale** déduite sur le bulletin :
  $$\text{Minutes Absence Finale} = |Solde_{periode}|, \quad HS_{finales} = 0$$
- **Si $Solde_{periode} \ge 0$** : le solde net est reventilé en HS finales selon le palier légal :
  $$HS30_{finales} = \min(Solde_{periode}, P_1), \quad HS50_{finales} = \max(0, Solde_{periode} - P_1)$$
- Ces grandeurs sont scellées dans `pointage_cumul_periode` (`minutes_absence_finale`, `minutes_hs30_finales`, `minutes_hs50_finales`, `minutes_palier_hs30`).
- Les ajustements DAF exigent un motif textuel d'au moins 5 caractères et sont strictement tracés (`saisi_par`, `valide_par`, `date_saisie`).

---

## 9. Régime des Absences, Retards & Pont Transactionnel Congés

### 9.1 Typologie des Absences

| Statut Jour (`ref_statut_pointage_jour`) | Décompte Absence Paie ? | Conséquence sur le Bulletin de Paie |
| :--- | :---: | :--- |
| **`PRESENT`** | NON | Salaire de base maintenu. |
| **`ABSENCE_NON_JUSTIFIEE`** | **OUI** | Retenue sur salaire au taux horaire strict ($S_{base} / 173{,}33$). |
| **`ABSENCE_JUSTIFIEE`** | **OUI** | Retenue sur salaire maintenue, pas de sanction disciplinaire. |
| **`CONGE`** | NON | Aucun décompte d'absence. Maintien légal ou indemnité congé valorisée. |
| **`DEMI_JOURNEE`** | **OUI (prorata)** | Retenue horaire ciblée sur les 4 heures non couvertes. |
| **`DISPENSE_POINTAGE`** | NON | Journée réputée effectuée à 100% (480 minutes). |
| **`REPOS`** | NON | Repos hebdomadaire (samedi ou dimanche non travaillé). |

### 9.2 Pont Transactionnel Pointage $\leftrightarrow$ Congés

Lorsqu'une absence est régularisée en congé payé annuel via [`PointageAnomalieService.regulariserPointage()`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/pointage/application/PointageAnomalieService.java) :
1. **Régularisation d'une journée isolée avec solde partiel** :
   - Si le collaborateur a 0,5 jour de solde disponible : consommation de 0,5 jour en congé payé, statut journalier basculé en **`DEMI_JOURNEE`**, retenue en paie limitée aux 4 heures d'absence restantes.
   - Si le solde est nul : rejet de la demande de congé, retenue intégrale de 8h. **Jamais de solde négatif**.
2. **Régularisation sur un intervalle multi-jours** :
   - Saisie obligatoire de la date de reprise. Calcul de `date_fin = date_reprise - 1`.
   - Le solde de congés doit couvrir l'intégralité de la période demandée. À défaut, l'opération est refusée sans débit ni pointage partiel.
3. **Annulation d'une régularisation** :
   - En cas d'annulation de la demande validée, le solde de congé est restitué et les pointages associés redeviennent automatiquement des absences non justifiées dans la même transaction ACID.

---

## 10. Architecture des Indemnités Journalières, Forfaits & Deuxième Repas

### 10.1 Catalogue des Indemnités (`ref_indemnite`)

- **`REPAS`** : Indemnité de restauration / panier journalier.
- **`DEPLACEMENT`** : Forfait transport mensuel ou journalier.
- **Indemnité repas à montant zéro** : Si le site offre la cantine en nature, `site_indemnite_config.montant_reference = 0.00`. L'indemnité monétaire est nulle.

### 10.2 Deuxième Repas pour Sortie Tardive

Attribué automatiquement selon l'heure de départ de l'après-midi (`heure_sortie_am`) et les 4 paramètres temporels de `parametre_global` :
- `SEUIL_HEURE_DEUXIEME_REPAS_1` (ex: 19:30) $\rightarrow$ attribution du montant `MONTANT_DEUXIEME_REPAS_1` (code `REPAS_2`).
- `SEUIL_HEURE_DEUXIEME_REPAS_2` (ex: 21:00) $\rightarrow$ attribution du montant majoré `MONTANT_DEUXIEME_REPAS_2` (code `REPAS_2_MAJORE`).
- Ces montants sont tracés directement dans `pointage_jour` (`code_second_repas`, `montant_second_repas`) et additionnés à l'unique ligne d'indemnité repas sur le bulletin de paie.

---

## 11. Le Protocole de Clôture : Gel, Snapshots & Immutabilité

L'approbation DAF (`POST /periodes/{id}/actions/APPROVE`) scelle les données temporelles et crée les 4 tables de snapshots immuables :
1. **`pointage_cumul_periode`** : Totaux certifiés par salarié pour la période (statut `GELE`).
2. **`pointage_snapshot_jour`** : Fige l'état de chaque jour ouvré, son statut, ses minutes et son heure d'entrée valorisée (`heure_entree_effective`).
3. **`pointage_snapshot_indemnite`** : Fige les montants et le régime fiscal/social (`imposable_irsa`, `soumis_cotisation`) au moment du gel.
4. **`pointage_snapshot_bucket`** : Fige le découpage élémentaire des tranches horaires qualifiées (`QualifiedTimeBucket`).

En cas de réouverture motivée (`REOPEN`), le cumul précédent passe au statut `REMPLACE` et les snapshots historiques sont conservés pour auditabilité.

---

## 12. Consommation des Données Scellées par le Moteur de Paie

> **Règle architecturale absolue** : Le composant `PayrollService` ne réalise **JAMAIS** de requête directe sur la table mutable `pointage_jour`.

Le calculateur de paie lit exclusivement `pointage_cumul_periode` (statut `GELE`) et ses tables de snapshots associées :
- Retenue pour absence : $\text{Heures Absence Finale} \times \frac{S_{base}}{173{,}33}$.
- Heures supplémentaires 30% : $\text{Heures HS30 Finales} \times \frac{S_{base}}{173{,}33} \times 1{,}30$.
- Heures supplémentaires 50% : $\text{Heures HS50 Finales} \times \frac{S_{base}}{173{,}33} \times 1{,}50$.
- Majoration de nuit, dimanche et férié : application des barèmes sur les buckets qualifiés correspondants.
- Indemnités repas et déplacement : injection dans les rubriques exonérées du bulletin.

---

## 13. Architecture Logicielle Backend & Catalogue d'Endpoints

### 13.1 Composants Backend (`mg.galaxam.sirh.pointage`)

```
mg.galaxam.sirh.pointage/
├── README.md
├── api/
│   ├── PointageController.java              # Saisie, preview, batch, synthèse, anomalies, exports
│   ├── PointagePeriodeController.java       # Machine à états des périodes de paie
│   └── dto/                                 # Contrats d'échange normalisés (DTO)
├── application/
│   ├── PointageAjustementPeriodeService.java# Décisions DAF sur le solde horaire global
│   ├── PointageAnomalieService.java         # Détection des anomalies & régularisations
│   ├── PointageDocumentData.java            # Données structurées des fiches de présence
│   ├── PointageDocumentService.java         # Génération des documents de présence
│   ├── PointageEmployeService.java          # Fiches individuelles de présence employé
│   ├── PointageExcelExportService.java      # Reporting Excel Apache POI certifié
│   ├── PointagePdfExportService.java        # Édition des fiches individuelles au format PDF
│   ├── PointageService.java                 # Orchestrateur central de saisie et de plancher site
│   ├── PointageSyntheseService.java         # Synthèse mensuelle, calcul des semaines et gel
│   └── PointageTemporalConfigurationGuard.java # Contrôle préflight d'intégrité temporelle
├── domain/
│   ├── calculation/
│   │   ├── AttendanceEngine.java            # Moteur pur de calcul journalier et hebdomadaire
│   │   ├── QualifiedTimeBucket.java         # Bucket élémentaire orthogonal
│   │   └── TemporalAttendanceEngine.java    # Partitionneur temporel et projection HS
│   └── engine/
│       └── CalendrierOuvrableEngine.java    # Calendrier ouvré et jours fériés
└── persistence/
    └── EmployeIndemniteDerogation.java      # Dérogations individuelles d'indemnités
```

---

## 14. Architecture Frontend, IHM & Composant Gantt

Le frontend sous `frontend/src/features/attendance/` s'appuie sur :
- `GestionPointagesPage.jsx` : Grille de saisie matricielle quotidienne avec récapitulatif automatique sur validation.
- `ArbitrageDafPage.jsx` : Console d'arbitrage DAF affichant les anomalies filtrables, les ajustements de solde et l'action de gel `APPROVE`.
- `PointageGanttView.jsx` : Visualisation temporelle graphique horizontale des 7 jours de la semaine avec code couleur de présence.
- `attendanceApi.js` : Client Axios communiquant avec les APIs `/pointages/*` et `/periodes/*`.

---

## 15. Dictionnaire de Données PostgreSQL & Modèle Relationnel

### Schéma des Tables Clés (`05_schema_pointage.sql`)

```sql
-- 1. Table principale de pointage journalier
CREATE TABLE pointage_jour (
    id_pointage SERIAL PRIMARY KEY,
    id_employe INTEGER NOT NULL REFERENCES employe(id_employe) ON DELETE CASCADE,
    id_periode_paie INTEGER NOT NULL REFERENCES periode_paie(id_periode_paie),
    id_site INTEGER NOT NULL REFERENCES ref_site(id_site),
    date_pointage DATE NOT NULL,
    heure_entree_matin TIME,
    heure_entree_matin_effective TIME,
    heure_sortie_matin TIME,
    heure_entree_am TIME,
    heure_sortie_am TIME,
    id_statut_jour INTEGER NOT NULL REFERENCES ref_statut_pointage_jour(id_statut_jour),
    heures_matin DECIMAL(10,8) DEFAULT 0,
    heures_am DECIMAL(10,8) DEFAULT 0,
    total_heures DECIMAL(10,8) DEFAULT 0,
    heures_nuit DECIMAL(10,8) NOT NULL DEFAULT 0,
    minutes_retard INTEGER DEFAULT 0,
    minutes_depart_anticipe INTEGER DEFAULT 0,
    utilise_vehicule_fonction_jour BOOLEAN DEFAULT FALSE,
    horaire_continu_jour BOOLEAN,
    montant_total_indemnites NUMERIC(20,2) DEFAULT 0,
    code_second_repas VARCHAR(20),
    montant_second_repas NUMERIC(20,2) NOT NULL DEFAULT 0,
    motif_ajustement TEXT,
    saisi_par INTEGER REFERENCES utilisateur(id_utilisateur),
    ajuste_par_daf BOOLEAN DEFAULT FALSE,
    ajuste_par INTEGER REFERENCES utilisateur(id_utilisateur),
    date_ajustement TIMESTAMP,
    id_demande_conge INTEGER REFERENCES demande_conge(id_demande_conge),
    genere_automatiquement BOOLEAN NOT NULL DEFAULT FALSE,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_pointage_employe_jour UNIQUE (id_employe, date_pointage),
    CONSTRAINT ck_pointage_second_repas_code CHECK (code_second_repas IS NULL OR code_second_repas IN ('REPAS_2', 'REPAS_2_MAJORE'))
);

-- 2. Ajustements de solde de fin de période
CREATE TABLE pointage_ajustement_periode (
    id_ajustement BIGSERIAL PRIMARY KEY,
    id_employe INTEGER NOT NULL REFERENCES employe(id_employe),
    id_periode_paie INTEGER NOT NULL REFERENCES periode_paie(id_periode_paie),
    minutes_signees INTEGER NOT NULL CHECK (minutes_signees <> 0),
    motif TEXT NOT NULL CHECK (length(btrim(motif)) >= 5),
    statut VARCHAR(12) NOT NULL DEFAULT 'VALIDE' CHECK (statut IN ('VALIDE', 'ANNULE')),
    saisi_par INTEGER REFERENCES utilisateur(id_utilisateur),
    valide_par INTEGER REFERENCES utilisateur(id_utilisateur),
    annule_par INTEGER REFERENCES utilisateur(id_utilisateur),
    date_saisie TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_annulation TIMESTAMP,
    motif_annulation TEXT
);

-- 3. Cumul gelé scellé pour la Paie
CREATE TABLE pointage_cumul_periode (
    id_cumul SERIAL PRIMARY KEY,
    id_employe INTEGER NOT NULL REFERENCES employe(id_employe),
    id_periode_paie INTEGER NOT NULL REFERENCES periode_paie(id_periode_paie),
    version_snapshot INTEGER NOT NULL DEFAULT 1 CHECK (version_snapshot > 0),
    statut_snapshot VARCHAR(20) NOT NULL DEFAULT 'GELE' CHECK (statut_snapshot IN ('GELE', 'CONSOMME', 'REMPLACE')),
    remplace_id_cumul INTEGER REFERENCES pointage_cumul_periode(id_cumul),
    nb_jours_ouvrables INTEGER NOT NULL,
    nb_jours_presents INTEGER NOT NULL DEFAULT 0,
    nb_jours_absence INTEGER NOT NULL DEFAULT 0,
    nb_jours_conge INTEGER NOT NULL DEFAULT 0,
    nb_jours_ferie_chome INTEGER NOT NULL DEFAULT 0,
    nb_jours_ferie_travaille INTEGER NOT NULL DEFAULT 0,
    total_heures_travaillees DECIMAL(12,8) NOT NULL DEFAULT 0,
    heures_hs_30 DECIMAL(12,8) NOT NULL DEFAULT 0,
    heures_hs_50 DECIMAL(12,8) NOT NULL DEFAULT 0,
    heures_nuit_total DECIMAL(12,8) NOT NULL DEFAULT 0,
    heures_dimanche_total DECIMAL(12,8) NOT NULL DEFAULT 0,
    heures_ferie_total DECIMAL(12,8) NOT NULL DEFAULT 0,
    heures_absence_retenues DECIMAL(12,8) NOT NULL DEFAULT 0,
    minutes_deficit_hebdo INTEGER,
    minutes_hs30_hebdo INTEGER,
    minutes_hs50_hebdo INTEGER,
    minutes_ajustement_periode INTEGER,
    minutes_solde_periode INTEGER,
    minutes_absence_finale INTEGER,
    minutes_hs30_finales INTEGER,
    minutes_hs50_finales INTEGER,
    minutes_palier_hs30 INTEGER,
    total_minutes_retard INTEGER NOT NULL DEFAULT 0,
    nb_jours_deplacement DECIMAL(5,2) NOT NULL DEFAULT 0,
    montant_total_indemnites NUMERIC(20,2) NOT NULL DEFAULT 0,
    cloture_par INTEGER REFERENCES utilisateur(id_utilisateur),
    date_cloture TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_pointage_cumul_periode_version UNIQUE (id_periode_paie, id_employe, version_snapshot)
);
```

---

## 16. Catalogue Exhaustif des Règles de Gestion (RG)

| Identifiant RG | Intitulé de la Règle | Énoncé Formel & Formule de Calcul | Composant d'Exécution |
| :--- | :--- | :--- | :--- |
| **RG-PTG-01** | **Période de saisie active** | La saisie quotidienne n'est autorisée que si la période de paie associée est au statut `EN_COURS`. Si la période est `VALIDEE`, `PAIE_VALIDEE` ou `CLOTUREE`, tout rejet par verrouillage strict est levé. | `PointageService`, `PointageAnomalieService` |
| **RG-PTG-02** | **Interdiction des dates futures** | Il est formellement interdit de saisir un pointage sur une date postérieure à la date civile courante ($Date_{pointage} \le Date_{systeme}$). | `PointageService.verifierDateNonFuture()` |
| **RG-PTG-03** | **Plancher d'arrivée valorisé** | Si `ref_site.plancher_arrivee_actif = TRUE`, l'entrée valorisée vaut $H_{eff} = \max(H_{brut}, H_{min})$. Si inactif ou non configuré, $H_{eff} = H_{brut}$. Zéro fallback en dur. | `PointageService.effectiveMorningEntry()` |
| **RG-PTG-04** | **Unicité journalière** | Un collaborateur ne peut posséder qu'un seul enregistrement de pointage par date civile (contrainte d'unicité `uk_pointage_employe_jour`). | Base PostgreSQL (`pointage_jour`) |
| **RG-PTG-05** | **Compensation hebdomadaire** | Les dépassements journaliers d'une semaine compensent en priorité les manques des autres jours ouvrables avant toute éligibilité aux heures supplémentaires (Loi 2024-014 Art. 110). | `AttendanceEngine.calculateWeek()` |
| **RG-PTG-06** | **Éligibilité aux heures sup** | Les heures supplémentaires ne sont calculées que pour les catégories professionnelles non cadres (`overtimeEligible = TRUE`). | `PointageSyntheseService.calculerSyntheseEmploye()` |
| **RG-PTG-07** | **Ventilation HS 30% et 50%** | Les heures supplémentaires jusqu’au palier de 8h (`paie_config_legale.seuil_hs_tranche1`) sont imputées à 30 %, puis à 50 % (Décret 72-226). | `AttendanceEngine.calculateWeek()` |
| **RG-PTG-08** | **Orthogonalité des sujétions** | Une heure travaillée la nuit ou un dimanche ne donne pas lieu à un double paiement de sa base horaire. Les sujétions sont isolées dans des `QualifiedTimeBucket`. | `TemporalAttendanceEngine`, `OvertimePayCalculator` |
| **RG-PTG-09** | **Créneau légal de nuit** | Tout travail exécuté entre 22:00 et 05:00 du matin est qualifié de travail de nuit et valorisé avec la majoration horaire correspondante (Loi 2024-014 Art. 115). | `AttendanceTemporalPartitioner` |
| **RG-PTG-10** | **Prérequis de transmission** | La transmission d'une période par l'agent (`SUBMIT`) est interdite tant qu'il subsiste au moins une anomalie bloquante (`ANOMALIE_SAISIE` ou absence injustifiée sans motif). | `PayrollPeriodWorkflowService` |
| **RG-PTG-11** | **Régularisation congé et solde** | Pour une journée isolée, couverture partielle historique par $\min(\text{absence}, \text{solde})$. Pour un intervalle multi-jours, couverture intégrale exigée. Aucun solde négatif. | `CongeService.regulariserCongeAnnuelDepuisPointageStructure()` |
| **RG-PTG-12** | **Scellement au gel (APPROVE)** | L'approbation DAF génère un cumul figé (`pointage_cumul_periode`) au statut `GELE` et archive les snapshots d'audit. Tout cumul antérieur passe à l'état `REMPLACE`. | `PointageSyntheseService.gelerPeriode()` |
| **RG-PTG-13** | **Réouverture auditée (REOPEN)** | La réouverture d'une période gelée exige un motif d'au moins 5 caractères et archive les cumuls précédents sous `REMPLACE`. | `PayrollPeriodWorkflowService.execute(REOPEN)` |
| **RG-PTG-14** | **Étanchéité de Paie** | Le moteur de Paie calcule les bulletins de paie exclusivement à partir des données figées dans `pointage_cumul_periode` et de ses snapshots associés. Aucune lecture directe sur `pointage_jour`. | `PayrollService.calculerBulletinPaie()` |
| **RG-PTG-15** | **Deuxième repas sortie tardive** | Attribué automatiquement selon l'heure de sortie réelle et les seuils configurés dans `parametre_global` (`code_second_repas`, `montant_second_repas`). | `PointageService` |

---

## 17. Cas d'Usage Chiffrés & Scénarios de Référence

### 17.1 Scénario 1 : La Semaine avec Compensation et Dépassement Net

Un collaborateur non cadre d'un site à horaire standard discontinu (objectif journalier = 8h, objectif hebdo = 40h) effectue les pointages suivants sur une semaine civile complète :
- **Lundi** : 08:00 – 12:00 et 13:00 – 19:00 $\rightarrow 10\text{ heures}$ travaillées (Surplus journalier = $+2\text{ heures}$).
- **Mardi** : 08:00 – 12:00 et 13:00 – 15:00 $\rightarrow 6\text{ heures}$ travaillées (Déficit journalier = $-2\text{ heures}$).
- **Mercredi** : 08:00 – 12:00 et 13:00 – 17:00 $\rightarrow 8\text{ heures}$ travaillées (Conforme).
- **Jeudi** : 08:00 – 12:00 et 13:00 – 17:00 $\rightarrow 8\text{ heures}$ travaillées (Conforme).
- **Vendredi** : 08:00 – 12:00 et 13:00 – 19:00 $\rightarrow 10\text{ heures}$ travaillées (Surplus journalier = $+2\text{ heures}$).

#### Résolution par le Moteur :
1. Total temps travaillé : $10 + 6 + 8 + 8 + 10 = \mathbf{42\text{ heures}}$.
2. Surplus journalier cumulé : $S_{jour} = 2\text{ h (Lundi)} + 2\text{ h (Vendredi)} = 4\text{ heures}$ ($240\text{ minutes}$).
3. Manque avant compensation : $40\text{ h} - 42\text{ h} + 4\text{ h} = 2\text{ heures}$ ($120\text{ minutes}$ correspondant au déficit du Mardi).
4. Minutes compensées : $\min(240, 120) = \mathbf{120\text{ minutes}}$ ($2\text{ heures}$). Le surplus du Lundi compense intégralement le déficit du Mardi.
5. Absence nette de la semaine : $2\text{ h} - 2\text{ h} = \mathbf{0\text{ heure}}$. Aucune retenue pour absence.
6. Heures supplémentaires brutes : $4\text{ h} - 2\text{ h} = \mathbf{2\text{ heures}}$ ($120\text{ minutes}$).
7. Répartition : $120\text{ minutes} \le 480\text{ minutes} \rightarrow \mathbf{HS 30\% = 2\text{ heures}}$, $\mathbf{HS 50\% = 0\text{ heure}}$.

---

### 17.2 Scénario 2 : Absence Régularisée avec Solde de Congé Partiel

Un collaborateur est absent le 15 Septembre (journée de 8 heures). Son solde de congés disponible au grand-livre est de **0,50 jour** (4 heures) :
1. L'agent lance la régularisation par congé annuel pour 1,0 jour.
2. `CongeService` alloue $\min(1.0, 0.5) = \mathbf{0{,}5\text{ jour}}$ de congé payé.
3. Une demande de congé `VALIDE` est créée pour 0,5 jour, et un débit `PRISE` de 0,5 jour est enregistré dans `mouvement_solde_conge`.
4. Le statut journalier dans `pointage_jour` devient **`DEMI_JOURNEE`**.
5. Les minutes d'absence retenues sont recalculées : $480\text{ min} - 240\text{ min} = \mathbf{240\text{ minutes}}$ (4 heures d'absence retenue).
6. Lors du calcul du bulletin de paie :
   - Maintien de salaire pour la demi-journée de congé.
   - Retenue sur salaire strictement limitée aux 4 heures d'absence non couverte :
     $$\text{Retenue} = 4 \times \left(\frac{S_{base}}{173{,}33}\right)$$

---

*Document officiel d'ingénierie, d'architecture logicielle et de conformité légale GALAXAM SIRH — Conforme Code du Travail malgache 2026.*
