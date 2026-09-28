# Module Pointage & Présence — Spécification Complète & Rapport de Référence

## GALAXAM SIRH — Documentation Canonique & Ouvrage de Référence Métier / Technique

---

> **Auteur** : Équipe d'Ingénierie, Architecture Logicielle & Direction Financière GALAXAM SIRH  
> **Module** : Pointage, Présence, Suivi des Temps, Compensation Hebdomadaire, Sujétions Temporelles, Indemnités & Gel de Paie  
> **Version** : 4.0.0 (Post-Phase 6 — Finalisation Backend/BDD, Plancher d'Arrivée Site, Régularisation Congé Partielle & Snapshots Scellés)  
> **Date de référence** : 24 Septembre 2026  
> **Statut documentaire** : **SOURCE UNIQUE DE VÉRITÉ CANONIQUE** (Annule et remplace tous les audits et mémos antérieurs)  
> **Sources juridiques et normatives de référence** :  
> - **Loi n° 2003-044 du 28 juillet 2004** portant Code du Travail de Madagascar (Articles 75, 76, 77, 78, 80, 81, 85, 87, 88)  
> - **Décret n° 2007-563 du 03 juillet 2007** relatif à la durée légale du travail (40 heures hebdomadaires = 173,33 heures mensuelles, diviseur horaire officiel)  
> - **Décret n° 72-226 du 06 juillet 1972** fixant le régime des heures supplémentaires et des majorations pour travail de nuit, du dimanche et des jours fériés  
> - **Arrêté n° 198-IGT du 12 janvier 1954** relatif aux modalités de contrôle du temps de travail et tenue du registre de pointage  
> - **Schéma de base de données canonique** : `backend/conception/bdd/05_schema_pointage.sql` (avec `02_schema_referentiels_rh.sql` et `04_schema_conges.sql`)  
> - **Historique des migrations actives** : `26-09-24-sp22` à `26-09-24-sp27`  
> - **Validation automatisée** : Suite JUnit 5 (87 tests backend 100% verts), Vitest & ESLint frontend (100% conformes)  

---

## Sommaire Exécutif

Le module **Pointage & Présence** du SIRH GALAXAM constitue le moteur opérationnel et comptable chargé de capturer, qualifier, régulariser et sceller l'activité temporelle des collaborateurs. Loin d'être un simple registre de présence déclaratif ou une feuille d'émargement bureautique, ce module est le **pivot financier en amont de la Paie** : chaque badgeage enregistré conditionne directement le calcul des salaires de base, les retenues pour absence ou retard, les majorations pour heures supplémentaires et sujétions (nuit, dimanche, jours fériés), ainsi que l'attribution des indemnités de déplacement et de restauration.

Le présent ouvrage unifie et scelle l'ensemble des règles de gestion, des modèles mathématiques et des choix d'architecture logicielle :

1. **La philosophie centrale « Le backend calcule, le frontend affiche »** : Aucune formule arithmétique de temps, aucun arbitrage de statut journalier, aucun calcul de retard ou d'heures supplémentaires n'est délégué au navigateur. Le moteur pur Java `AttendanceEngine` constitue l'unique juge du calcul journalier et hebdomadaire, invoqué de façon identique en prévisualisation temps réel (*preview*) et lors de la persistance batch.
2. **La gestion du plancher d'arrivée valorisé (*Earliest Paid Start*)** : Règle historiquement nommée « tolérance », il s'agit en réalité d'un plancher strict configuré au niveau de l'établissement (`ref_site`). L'heure d'entrée effective valorisée est arrêtée à $\max(\text{badge}, \text{plancher})$ si le plancher est actif, sans aucun repli implicite en dur.
3. **Le principe fondamental de compensation hebdomadaire des heures** : Conformément à la législation malagasy et à la jurisprudence sociale, les dépassements journaliers ne génèrent pas d'heures supplémentaires au jour le jour. Ils viennent prioritairement combler les déficits constatés sur d'autres journées ouvrables de la même semaine civile avant de prétendre à majoration.
4. **La qualification spatio-temporelle en buckets orthogonaux (`TemporalAttendanceEngine`)** : Pour valoriser avec exactitude le travail en horaires atypiques (nuits, dimanches, fériés) sans double comptage, le temps de travail est découpé en tranches élémentaires (`QualifiedTimeBucket`) portant simultanément une qualification calendaire et un palier de majoration (Normal, HS 30%, HS 50%).
5. **La régularisation des anomalies avec consommation de solde de congé partiel** : Le pont transactionnel avec le module Congés autorise la couverture rétroactive d'absences justifiées par du congé annuel dans la stricte limite du solde disponible ($\min(\text{absence}, \text{solde})$), sans jamais créer de solde négatif. Le reliquat non couvert demeure comptabilisé et déduit en absence.
6. **L'immutabilité absolue du gel de période (`APPROVE`) et l'auditabilité des snapshots** : Dès l'approbation DAF, la synthèse mensuelle est figée dans `pointage_cumul_periode` (statut `GELE`), tandis que la provenance exacte des journées, des indemnités et des tranches temporelles est scellée dans les tables immuables de snapshots. Le moteur de Paie (`PayrollService`) consomme exclusivement ces structures gelées, rendant les calculs de paie strictement indépendants de toute modification ultérieure sur la saisie courante.

---

## Table des Matières

1. [Cadre Légal, Réglementaire & Normatif Malagasy](#1-cadre-légal-réglementaire--normatif-malagasy)
2. [Acteurs, Rôles & Matrice des Responsabilités (RBAC)](#2-acteurs-rôles--matrice-des-responsabilités-rbac)
3. [Cycle de Vie d'une Période de Paie & Machine à États](#3-cycle-de-vie-dune-période-de-paie--machine-à-états)
4. [Saisie Quotidienne, Horaires de Site & Plancher d'Arrivée](#4-saisie-quotidienne-horaires-de-site--plancher-darrivée)
5. [Moteur de Calcul Journalier (`AttendanceEngine`)](#5-moteur-de-calcul-journalier-attendanceengine)
6. [Moteur de Compensation Hebdomadaire & Heures Supplémentaires](#6-moteur-de-compensation-hebdomadaire--heures-supplémentaires)
7. [Moteur Temporel & Qualification des Buckets (`TemporalAttendanceEngine`)](#7-moteur-temporel--qualification-des-buckets-temporalattendanceengine)
8. [Régime des Absences, Retards & Pont Transactionnel Congés](#8-régime-des-absences-retards--pont-transactionnel-congés)
9. [Architecture des Indemnités Journalières & Forfaits](#9-architecture-des-indemnités-journalières--forfaits)
10. [Le Protocole de Clôture : Gel, Snapshots & Immutabilité](#10-le-protocole-de-clôture--gel-snapshots--immutabilité)
11. [Consommation des Données Scellées par le Moteur de Paie](#11-consommation-des-données-scellées-par-le-moteur-de-paie)
12. [Architecture Logicielle Backend & Catalogue d'Endpoints](#12-architecture-logicielle-backend--catalogue-dendpoints)
13. [Architecture Frontend, IHM & Composant Gantt](#13-architecture-frontend-ihm--composant-gantt)
14. [Dictionnaire de Données PostgreSQL & Modèle Relationnel](#14-dictionnaire-de-données-postgresql--modèle-relationnel)
15. [Catalogue Exhaustif des Règles de Gestion (RG)](#15-catalogue-exhaustif-des-règles-de-gestion-rg)
16. [Cas d'Usage Chiffrés & Scénarios de Référence](#16-cas-dusage-chiffrés--scénarios-de-référence)
17. [Dettes Techniques Résolues & Points de Vigilance](#17-dettes-techniques-résolues--points-de-vigilance)
18. [Annexes Techniques & Glossaire Terminologique](#18-annexes-techniques--glossaire-terminologique)

---

## 1. Cadre Légal, Réglementaire & Normatif Malagasy

Le module Pointage & Présence tire sa légitimité de l'ordre public social malagasy. Les règles programmées dans les moteurs d'ingénierie traduisent fidèlement les textes législatifs et réglementaires suivants :

### 1.1 Tableau Synoptique des Fondements Juridiques

| Texte de Référence | Article / Décret | Portée Juridique & Règle Métier | Traduction Technique dans GALAXAM SIRH |
| :--- | :--- | :--- | :--- |
| **Loi n° 2003-044** (Code du Travail) | **Article 75** | **Durée légale du travail** : Fixée à 40 heures par semaine dans tous les établissements non agricoles. | Seuil hebdomadaire de référence fixé à $40\text{ h} = 2\,400\text{ minutes}$. Base mensuelle légale à $173{,}33\text{ heures}$. |
| **Loi n° 2003-044** | **Article 76** | **Heures supplémentaires** : Les heures effectuées au-delà de la durée légale hebdomadaire constituent des heures supplémentaires donnant lieu à majoration. | Moteur `AttendanceEngine.calculateWeek()` : déclenchement des HS au-delà de l'objectif hebdomadaire net de la semaine. |
| **Loi n° 2003-044** | **Article 80** | **Travail de nuit** : Tout travail effectué entre 22 heures et 5 heures est considéré comme travail de nuit. | Constante `AttendanceTemporalPartitioner.NIGHT_START = 22:00` et `NIGHT_END = 05:00`. Découpage systématique aux frontières. |
| **Loi n° 2003-044** | **Article 81** | **Repos hebdomadaire** : Le repos hebdomadaire est obligatoire. Il est au minimum de 24 heures consécutives et doit avoir lieu en principe le dimanche. | Qualification `DaySpecialType.SUNDAY` des vacations dominicales. Attribution de la majoration dimanche sans double paiement de base. |
| **Décret n° 72-226** | **Article 2 & 3** | **Taux de majoration des heures supplémentaires** :<br>• 8 premières heures sup (de la 41e à la 48e heure incluse) : **Majoration de 30%**.<br>• Au-delà de la 48e heure : **Majoration de 50%**. | Tranche 1 (`firstOvertimeBandMinutes`, alimenté par `paie_config_legale.seuil_hs_tranche1 × 60`) imputée à `OVERTIME_30`. Tranche 2 (excédent) imputée à `OVERTIME_50`. |
| **Décret n° 72-226** | **Article 3 (Al. 2)** | **Articulation des sujétions spéciales** : Le travail de nuit, du dimanche et des jours fériés donne lieu à majorations spécifiques non cumulables aveuglément avec la base salaire. | Modèle de collision `SpecialPremiumCollisionPolicy` dans `OvertimePayCalculator`. Séparation stricte de la base horaire et des majorations. |
| **Arrêté n° 198-IGT** | **Article 1 à 4** | **Obligation de contrôle et traçabilité** : L'employeur doit enregistrer les heures exactes d'entrée et de sortie de chaque salarié et tenir un registre d'émargement ou de badgeage. | `pointage_jour` stocke le badge brut physique (`heure_entree_matin`) et l'entrée valorisée (`heure_entree_matin_effective`). |

---

## 2. Acteurs, Rôles & Matrice des Responsabilités (RBAC)

La sécurité du module repose sur le modèle de contrôle d'accès fondé sur les rôles (**RBAC** — *Role-Based Access Control*), adossé à Spring Security au backend et matérialisé par des permissions précises.

### 2.1 Typologie des Acteurs Métier

```
      ┌───────────────────────────────────────────────────────────┐
      │                   ADMINISTRATEUR SYSTÈME                  │
      │   (Supervision globale, paramétrages transverses, DDL)    │
      └─────────────────────────────┬─────────────────────────────┘
                                    │
         ┌──────────────────────────┴──────────────────────────┐
         │                                                     │
┌────────▼─────────────────────────┐        ┌──────────────────▼──────────────────┐
│          AGENT DE POINTAGE       │        │        DIRECTEUR FINANCIER (DAF)    │
│ (Opérateur RH / Pointeur Site)   │        │     (Contrôleur de Gestion / Paie)  │
│ • Saisie quotidienne batch       │        │ • Arbitrage des anomalies           │
│ • Correction des oublis badge    │        │ • Validation & Scellement (APPROVE) │
│ • Transmission période (SUBMIT)  │        │ • Réouverture motivée (REOPEN)      │
└──────────────────────────────────┘        └─────────────────────────────────────┘
```

### 2.2 Matrice des Droits et Permissions

| Action Opérationnelle | Endpoint / Service | Rôle AGENT_POINTAGE | Rôle DAF / VALIDATE | Rôle ADMIN |
| :--- | :--- | :---: | :---: | :---: |
| **Consulter les pointages et synthèses** | `GET /pointages/saisie`, `GET /pointages/synthese` | **OUI** (`POINTAGE_READ`) | **OUI** (`POINTAGE_READ`) | **OUI** |
| **Prévisualiser une saisie (Preview)** | `POST /pointages/saisie/preview` | **OUI** (`POINTAGE_READ`) | **OUI** (`POINTAGE_READ`) | **OUI** |
| **Enregistrer des pointages quotidiens (Batch)** | `POST /pointages/saisie/batch` | **OUI** (`POINTAGE_WRITE`) | **OUI** (`POINTAGE_WRITE`) | **OUI** |
| **Régulariser une anomalie (Horaires)** | `PUT /pointages/anomalies/{id}/regulariser` | **OUI** (`POINTAGE_WRITE`) | **OUI** (`POINTAGE_WRITE`) | **OUI** |
| **Régulariser une absence par Congé Payé** | `PUT /pointages/anomalies/{id}/regulariser` | **OUI** (Délégation CongeService) | **OUI** | **OUI** |
| **Transmettre la période pour arbitrage (SUBMIT)** | `POST /periodes/{id}/actions/SUBMIT` | **OUI** (`POINTAGE_WRITE`) | Non pertinent | **OUI** |
| **Rejeter une période vers la saisie (REJECT)** | `POST /periodes/{id}/actions/REJECT` | **NON** (Interdit) | **OUI** (`POINTAGE_VALIDATE`) | **OUI** |
| **Approuver et sceller le gel (APPROVE)** | `POST /periodes/{id}/actions/APPROVE` | **NON** (Interdit) | **OUI** (`POINTAGE_VALIDATE`) | **OUI** |
| **Rouvrir une période scellée (REOPEN)** | `POST /periodes/{id}/actions/REOPEN` | **NON** (Interdit) | **OUI** (`PAIE_VALIDATE` / DAF) | **OUI** |
| **Configurer le plancher d'arrivée site** | `POST /pointages/parametres/tolerances` | **NON** | **NON** | **OUI** (`ROLE_ADMIN`) |
| **Créer des dérogations individuelles d'indemnité** | `POST /pointages/parametres/derogations` | **NON** | **NON** | **OUI** (`ROLE_ADMIN`) |

---

## 3. Cycle de Vie d'une Période de Paie & Machine à États

Le déroulement du pointage s'inscrit rigoureusement dans le cycle de vie de la période de paie (`periode_paie`), gouverné par le composant `PayrollPeriodWorkflowService`.

### 3.1 Diagramme d'États-Transitions

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

### 3.2 Matrice des Règles d'Accès aux Données selon l'État de la Période

| Statut Période (`ref_statut_periode_paie`) | Saisie Quotidienne (`pointage_jour`) | Régularisation Anomalies | Modification Cumul Gelé | Calcul Paie Réel |
| :--- | :---: | :---: | :---: | :---: |
| **`BROUILLON`** | Bloquée (Ouvrir d'abord) | Bloquée | Impossible | Bloqué |
| **`EN_COURS`** | **AUTORISÉE** | **AUTORISÉE** | Aucun cumul gelé | Bloqué |
| **`A_VALIDER` / `TRANSMIS_DAF`** | Réservée DAF/Admin | Réservée DAF/Admin | Aucun cumul gelé | Bloqué |
| **`VALIDEE` (GELÉ)** | **VERROUILLÉE** (Période gelée) | **VERROUILLÉE** | **IMMUABLE** (`GELE`) | **AUTORISÉ** |
| **`PAIE_VALIDEE`** | **VERROUILLÉE** | **VERROUILLÉE** | **IMMUABLE** | Bulletins figés |
| **`CLOTUREE`** | **VERROUILLÉE** (Archive) | **VERROUILLÉE** | **IMMUABLE** (`CONSOMME`) | Bulletins archivés |

---

## 4. Saisie Quotidienne, Horaires de Site & Plancher d'Arrivée

### 4.1 Horaires de Référence par Site (`ref_site`)

Chaque collaborateur est rattaché, via son affectation carrière active (`employe_affectation_carriere`), à un établissement physique (`ref_site`). L'établissement définit la grille horaire normale et la modalité d'organisation du travail :

- **Horaire Continu (`horaire_continu = TRUE`)** : La journée comporte une vacation unique continue (par exemple 07:00 – 15:00 ou 08:00 – 16:00). Une pause repas interne n'interrompt pas la vacation dans le système. Les colonnes d'amplitude sont `heure_entree_matin` et `heure_sortie_am`.
- **Horaire Discontinu / Standard (`horaire_continu = FALSE`)** : La journée comporte deux vacations séparées par une pause méridienne :
  1. Vacation du matin : `heure_debut_matin` à `heure_fin_matin` (ex : 08:00 – 12:00).
  2. Vacation de l'après-midi : `heure_debut_apres_midi` à `heure_fin_apres_midi` (ex : 13:00 – 17:00).

### 4.2 Le Plancher d'Arrivée Valorisé (*Earliest Paid Start*)

#### Problématique Métier
Dans plusieurs filiales et sites d'exploitation, certains collaborateurs se présentent sur le lieu de travail très tôt le matin (ex : 06:30 ou 07:00) pour des raisons personnelles de transport ou de logistique. Cependant, l'entreprise ne souhaite ni ne doit rémunérer du travail effectif avant l'heure d'ouverture autorisée du service (ex : 07:30 pour une prise de poste normale à 08:00).

> **Règle fondamentale** : Ce mécanisme n'est **PAS** une tolérance de retard. Il s'agit d'un **plancher d'arrivée valorisé**.

#### Formalisation Mathématique & Logique
Soit $H_{badge}$ l'heure brute constatée à la pointeuse, et $H_{plancher}$ l'heure minimale valorisée configurée pour le site (`ref_site.heure_entree_minimale_valorisee`). L'heure d'entrée effective valorisée $H_{effective}$ retenue pour tous les décomptes d'heures est définie par :

$$H_{effective} = \begin{cases} 
\max(H_{badge}, H_{plancher}) & \text{si } \text{ref\_site.plancher\_arrivee\_actif} = \mathbf{TRUE} \\
H_{badge} & \text{si } \text{ref\_site.plancher\_arrivee\_actif} = \mathbf{FALSE} \text{ ou } H_{plancher} \text{ non configuré}
\end{cases}$$

#### Exemples d'Application

```
Configuration du Site : Horaire normal = 08:00 | Plancher actif = TRUE | Heure minimale = 07:30

Cas A : Badge physique à 07:00
        -> Entrée brute = 07:00
        -> Entrée effective = max(07:00, 07:30) = 07:30 (Les 30 min avant 07:30 sont ignorées)

Cas B : Badge physique à 07:20
        -> Entrée brute = 07:20
        -> Entrée effective = max(07:20, 07:30) = 07:30

Cas C : Badge physique à 07:45
        -> Entrée brute = 07:45
        -> Entrée effective = max(07:45, 07:30) = 07:45 (Le salarié est arrivé après le plancher)

Cas D : Badge physique à 08:05 (Retard de 5 minutes)
        -> Entrée brute = 08:05
        -> Entrée effective = max(08:05, 07:30) = 08:05 (Le retard reste intégralement décompté)

Cas E : Règle inactive (plancher_arrivee_actif = FALSE)
        -> Badge physique à 07:00 -> Entrée effective = 07:00
```

#### Persistance et Traçabilité en Base de Données
Le résultat du calcul est scellé dans deux colonnes distinctes de `pointage_jour` :
- `heure_entree_matin` : Conserve à jamais l'heure brute réelle badgée (valeur probante pour l'inspection du travail).
- `heure_entree_matin_effective` : Conserve l'heure valorisée retenue pour le calcul.
- Lors du gel mensuel, cette valeur est archivée dans `pointage_snapshot_jour.heure_entree_effective`.

---

## 5. Moteur de Calcul Journalier (`AttendanceEngine`)

Le composant `AttendanceEngine` est un moteur pur, déterministe et sans état (`stateless`). Il ne réalise aucune requête SQL, ne manipule aucune transaction et dépend exclusivement de son objet d'entrée immuable `AttendanceEngine.Context`.

### 5.1 Entrées et Sorties du Moteur

```
┌────────────────────────────────────────────────────────┐
│              AttendanceEngine.Context                  │
├────────────────────────────────────────────────────────┤
│ • Identifiant employé, Date civile de pointage         │
│ • Heures réelles : morningEntry (effective),           │
│   morningExit, afternoonEntry, afternoonExit           │
│ • Heures planifiées : scheduledMorningEntry,           │
│   scheduledMorningExit, scheduledAfternoonEntry...     │
│ • Horaires de nuit légale : nightStartsAt, nightEndsAt │
│ • Booléens d'état : continuousSchedule, autonomous,    │
│   publicHoliday, leavePart (NONE/MORNING/PM/FULL)      │
│ • Droits indemnités : mealGranted, travelUnits...      │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼ AttendanceEngine.calculateDay()
┌────────────────────────────────────────────────────────┐
│               AttendanceEngine.Result                  │
├────────────────────────────────────────────────────────┤
│ • Durées : morningMinutes, afternoonMinutes,           │
│   workedMinutes, normalMinutes                         │
│ • Dérives : lateMinutes, absenceMinutes, nightMinutes  │
│ • Statut dérivé : Result.Status (PRESENT, HALF_DAY...) │
│ • Indemnités valorisées : mealAmount, travelAmount     │
│ • Anomalies détectées : List<String> anomalies         │
└────────────────────────────────────────────────────────┘
```

### 5.2 Algorithme de Calcul des Durées Journalières

Soient les fonctions d'écart horaire $\Delta(t_1, t_2)$ calculant la durée en minutes entre deux bornes :

1. **Temps Travaillé le Matin ($M_{matin}$)** :
   - Si horaire continu : $M_{matin} = \Delta(H_{entree\_matin}, H_{sortie\_am})$.
   - Si horaire discontinu standard : $M_{matin} = \Delta(H_{entree\_matin}, H_{sortie\_matin})$.
   - Si pause automatique (*Auto-Break* : pas de sortie matin ni d'entrée AM saisis) : $M_{matin} = \Delta(H_{entree\_matin}, H_{prevue\_sortie\_matin})$.

2. **Temps Travaillé l'Après-Midi ($M_{am}$)** :
   - Si horaire continu : $M_{am} = 0$.
   - Si horaire discontinu standard : $M_{am} = \Delta(H_{entree\_am}, H_{sortie\_am})$.
   - Si pause automatique : $M_{am} = \Delta(H_{prevue\_entree\_am}, H_{sortie\_am})$.

3. **Temps Travaillé Global ($M_{travaille}$)** :
   $$M_{travaille} = M_{matin} + M_{am}$$

4. **Retard Journalier ($M_{retard}$)** :
   - Salarié cadre / autonome (`autonomous = TRUE`) : $M_{retard} = 0$.
   - Salarié standard :
     $$Retard_{matin} = \max(0, \Delta(H_{prevue\_entree\_matin}, H_{entree\_matin}))$$
     $$Retard_{am} = \begin{cases} 
     \max(0, \text{PauseReelle} - \text{PausePrevue}) & \text{si pause débutée tardivement} \\
     \max(0, \Delta(H_{prevue\_entree\_am}, H_{entree\_am})) & \text{sinon}
     \end{cases}$$
     $$M_{retard} = Retard_{matin} + Retard_{am}$$

5. **Déficit / Absence Journalière ($M_{absence}$)** :
   Soit $M_{planifie}$ la durée contractuelle théorique de la journée (généralement 8 heures = 480 minutes).
   $$M_{absence} = \max(0, M_{planifie} - M_{travaille})$$
   - Si `leavePart = FULL_DAY` ou jour férié chômé non travaillé : $M_{absence} = 0$.
   - Si `leavePart = MORNING` : $M_{absence} = \max(0, M_{absence} - M_{planifie\_matin})$.
   - Si `leavePart = AFTERNOON` : $M_{absence} = \max(0, M_{absence} - M_{planifie\_am})$.

---

## 6. Moteur de Compensation Hebdomadaire & Heures Supplémentaires

### 6.1 Principe Fondamental de Compensation Hebdomadaire

En droit du travail malagasy, la semaine civile (du lundi 00:00 au dimanche 24:00) forme l'unité indivisible de calcul de la durée du travail. **Un collaborateur qui dépasse son horaire journalier un mardi ne bénéficie pas automatiquement d'heures supplémentaires si ce même collaborateur est parti plus tôt le jeudi.**

> **Règle canonique** : Les surplus d'heures journaliers constatés au cours d'une semaine compensent en premier lieu les déficits ou départs anticipés des autres jours ouvrables de la même semaine, avant que le reliquat éventuel ne devienne éligible aux heures supplémentaires.

### 6.2 Découpage en Tronçons Autonomes (`segmentWeeks`)

Lorsque le mois de paie débute un jour autre qu'un lundi (ex : le 26 du mois), les semaines calendaires se trouvent coupées à cheval sur deux périodes de paie successives.
La fonction `AttendanceEngine.segmentWeeks(dateDebut, dateFin)` partitionne la période en **tronçons hebdomadaires autonomes** :

```
Exemple : Période du 26 Janvier au 25 Février (Année non bissextile)
Lundi 26 Janvier ────────► Dimanche 01 Février : Tronçon 1 (Complet, 7 jours)
Lundi 02 Février ────────► Dimanche 08 Février : Tronçon 2 (Complet, 7 jours)
Lundi 09 Février ────────► Dimanche 15 Février : Tronçon 3 (Complet, 7 jours)
Lundi 16 Février ────────► Dimanche 22 Février : Tronçon 4 (Complet, 7 jours)
Lundi 23 Février ────────► Mercredi 25 Février : Tronçon 5 (Tronqué en fin, 3 jours)
```

> **Règle d'étanchéité** : La compensation hebdomadaire s'exécute strictement au sein de chaque tronçon de période. Il n'existe **aucune compensation rétroactive ou anticipée entre deux périodes de paie distinctes**.

### 6.3 Algorithme et Formules Mathématiques du Calcul Hebdomadaire

Soit un tronçon hebdomadaire comportant $N$ journées analysées par `AttendanceEngine.calculateDay()`.

1. **Temps Travaillé Total Hebdomadaire ($W_{travaille}$)** :
   $$W_{travaille} = \sum_{j=1}^N M_{travaille}(j)$$

2. **Objectif Journalier Standard ($Cible(j)$)** :
   - Pour le Samedi : $Cible(j) = 0$ (tout travail le samedi génère du surplus compensatoire).
   - Pour une Demi-journée : $Cible(j) = \frac{\text{standardDayMinutes}}{2} = 240\text{ min}$.
   - Pour un jour complet ouvré : $Cible(j) = \text{standardDayMinutes} = 480\text{ min}$.

3. **Surplus Journalier Cumulé ($S_{jour}$)** :
   Somme des dépassements constatés sur les jours ouvrables compensables (hors dimanches, fériés et congés payés) :
   $$S_{jour} = \sum_{j \in \text{Ouvrables}} \max\Big(0, M_{travaille}(j) - Cible(j)\Big)$$

4. **Manque à Gagner Avant Compensation ($Manque_{initial}$)** :
   Écart brut entre l'objectif hebdomadaire effectif du tronçon ($W_{attendu}$) et le temps réellement presté, majoré du surplus :
   $$Manque_{initial} = \max\Big(0, W_{attendu} - W_{travaille} + S_{jour}\Big)$$

5. **Minutes Compensées ($M_{compensees}$)** :
   $$M_{compensees} = \min\Big(S_{jour}, Manque_{initial}\Big)$$

6. **Heures Supplémentaires Brutes ($HS_{brutes}$)** :
   Le surplus qui n'a pas été consommé par la compensation d'un déficit devient un dépassement net :
   $$HS_{brutes} = S_{jour} - M_{compensees}$$

7. **Éligibilité Légale aux Heures Supplémentaires** :
   Selon la catégorie professionnelle de l'affectation active :
   $$HS_{eligibles} = \begin{cases} 
   HS_{brutes} & \text{si } \text{overtimeEligible} = \mathbf{TRUE} \text{ (Non-cadres, Ouvriers, ETAM)} \\
   0 & \text{si } \text{overtimeEligible} = \mathbf{FALSE} \text{ (Cadres dirigeants, Forfait journalier)}
   \end{cases}$$

8. **Ventilation dans les Paliers de Majoration (HS 30% et HS 50%)** :
   Soit $P_1$ le palier en minutes, lu dans `paie_config_legale.seuil_hs_tranche1 × 60` à la date applicable (aucune constante de secours) :
   $$HS_{30} = \min(HS_{eligibles}, P_1)$$
   $$HS_{50} = \max(0, HS_{eligibles} - HS_{30})$$

9. **Déficit restant sur la semaine ($Absence_{semaine}$)** :
   Le déficit horaire qui n'a pas pu être comblé par du surplus est conservé pour le rapprochement de période :
   $$Absence_{semaine} = Manque_{initial} - M_{compensees}$$

### 6.4 Rapprochement horaire de fin de période

La semaine reste calculée séparément pour établir les faits avant régularisation.
En fin de période, la DAF applique un ajustement signé et motivé au bilan global :

$$Solde_{periode} = HS30_{hebdo} + HS50_{hebdo} - Deficit_{hebdo} + Ajustements_{valides}$$

- Un ajustement −5 h retire 5 h ; +2 h ajoute 2 h. Dans un Excel qui soustrait Divers, une déduction de 5 h s’écrit +5 dans cette colonne.
- Un solde négatif devient une absence horaire hors congés.
- Un solde positif est ventilé une fois pour la période : jusqu’au palier issu de `paie_config_legale.seuil_hs_tranche1` à 30 %, puis le reste à 50 %.
- Les qualifications dimanche, férié et nuit des heures réellement travaillées sont conservées. Elles ne sont pas additionnées une deuxième fois aux HS.
- Un ajustement positif peut produire un complément de HS payé sans inventer d’horaires. Ce complément est tracé dans le contrat de paie comme décision DAF ; il n’a aucune sujétion calendaire inventée.

Les nouveaux snapshots figent les minutes finales HS30 et HS50 et le palier utilisé (`minutes_palier_hs30`, migration SP33). Un ancien palier NULL est indiqué comme non historisé, sans déduire arbitrairement 8 h. Les anciens snapshots, dont minutes_hs50_finales est NULL, restent consommés selon leur règle historique.
Les semaines et buckets bruts restent des faits de pointage. La reventilation de paie conserve les minutes réellement travaillées ; le complément DAF est valorisé séparément au taux applicable à la date de fin de période.

---

## 7. Moteur Temporel & Qualification des Buckets (`TemporalAttendanceEngine`)

### 7.1 Le Concept Pédagogique de « Bucket Qualifié »

Pour un gestionnaire de paie ou un auditeur financier, le temps de travail ne peut pas être appréhendé comme une somme de scalaires indépendants.

> **Exemple d'impasse** : Un salarié effectue une vacation un **Dimanche soir de 20:00 à 23:00**. Les 60 dernières minutes (22:00 – 23:00) constituent simultanément :
> 1. Du travail un **Dimanche** (sujétion dominicale).
> 2. Du travail de **Nuit** (créneau légal 22:00 – 05:00).
> 3. Éventuellement des **Heures Supplémentaires** si le seuil hebdomadaire de 40 heures a été franchi.
>
> Si le système comptabilisait $1\text{ h Dimanche} + 1\text{ h Nuit} + 1\text{ h HS}$, il créerait artificiellement **3 heures payées pour 1 heure passée sur site** !

Pour éliminer définitivement tout risque de double comptage, le moteur `TemporalAttendanceEngine` découpe le temps en **segments chronologiques homogènes et élémentaires** appelés `QualifiedTimeBucket`. Chaque minute prestée appartient à **un et un seul bucket**, qui porte de manière orthogonale :
- Sa qualification de calendrier (`DaySpecialType` : STANDARD, SUNDAY, PUBLIC_HOLIDAY).
- Sa qualification nocturne (`NightWorkType` : NONE, NIGHT_UNCLASSIFIED).
- Son palier de majoration de temps (`OvertimeTier` : REGULAR, OVERTIME_30, OVERTIME_50).

### 7.2 Diagramme de Partitionnement Temporel

```
Vacation continue : Dimanche 20:00 ────────────────────────────────────────► Lundi 02:00

Ligne du Temps :
20:00                    22:00                 00:00                               02:00
  │                        │                     │                                   │
  ▼                        ▼                     ▼                                   ▼
┌────────────────────────┬─────────────────────┬───────────────────────────────────┐
│        Bucket 1        │      Bucket 2       │             Bucket 3              │
│      (120 minutes)     │    (120 minutes)    │           (120 minutes)           │
├────────────────────────┼─────────────────────┼───────────────────────────────────┤
│ • Dimanche             │ • Dimanche          │ • Lundi (Standard)                │
│ • Jour (avant 22h)     │ • Nuit (22h - 00h)  │ • Nuit (00h - 02h)                │
│ • OvertimeTier:        │ • OvertimeTier:     │ • OvertimeTier:                   │
│   REGULAR              │   REGULAR           │   OVERTIME_30 (selon projection)  │
└────────────────────────┴─────────────────────┴───────────────────────────────────┘
```

### 7.3 Algorithme de Projection Chronologique des Quotas HS

Une fois la timeline partitionnée en buckets temporels, le moteur `OvertimeProjectionEngine.project()` y injecte chronologiquement les volumes issus du moteur hebdomadaire `AttendanceEngine` :
1. Les premières minutes de la semaine remplissent la tranche `REGULAR` (jusqu'à concurrence de l'objectif attendu).
2. Dès que l'objectif régulier est comblé, les minutes suivantes basculent automatiquement en `OVERTIME_30` (dans la limite du palier paramétré en base).
3. Tout dépassement supplémentaire bascule en `OVERTIME_50`.

> **Invariant d'intégrité absolue** : La somme des durées de tous les buckets qualifiés est garantie strictement égale à la durée totale travaillée constatée par le moteur journalier. Toute discordance déclenche une exception bloquante `CONSERVATION_VIOLATION_PROJECTION`.

---

## 8. Régime des Absences, Retards & Pont Transactionnel Congés

### 8.1 Typologie des Absences dans GALAXAM SIRH

| Code Statut Jour | Désignation Métier | Justificatif Fourni ? | Décompte en Absence Paie ? | Conséquence Financière |
| :--- | :--- | :---: | :---: | :--- |
| **`ABSENCE_NON_JUSTIFIEE`** | Absence injustifiée / Abandon de poste | NON | **OUI** | Retenue sur salaire au taux horaire strict ($S_{base} / 173{,}33$). |
| **`ABSENCE_JUSTIFIEE`** | Absence autorisée sans maintien de salaire | OUI | **OUI** | Retenue sur salaire maintenue, mais aucune sanction disciplinaire. |
| **`CONGE`** | Congé payé annuel ou permission exceptionnelle | OUI | **NON** | Aucun décompte d'absence. Maintien légal ou indemnité congé. |
| **`DEMI_JOURNEE`** | Présence partielle (matin ou après-midi seul) | N/A | **OUI (au prorata)** | Retenue horaire ciblée sur les heures manquantes non couvertes. |
| **`DISPENSE_POINTAGE`** | Poste autonome / Cadre dirigeant | N/A | **NON** | Journée réputée intégralement effectuée (480 minutes). |

### 8.2 Pont Transactionnel Pointage ↔ Congés (Régularisation Rétroactive)

Lorsqu'un collaborateur a été absent sur un jour ouvré, l'agent ou la DAF peut régulariser cette journée via l'IHM d'arbitrage. Trois modes opératoires sont supportés par `PointageAnomalieService.regulariserPointage()` :

```
                                  Régularisation d'une Absence
                                                │
         ┌──────────────────────────────────────┼──────────────────────────────────────┐
         │                                      │                                      │
         ▼                                      ▼                                      ▼
[CONGÉ PAYÉ ANNUEL]             [PERMISSION EXCEPTIONNELLE]                  [ABSENCE DÉDUITE]
Imputation sur solde            Quota légal Art. 88                          Retenue financière
de congés acquis                (10 jours par an max)                        sur bulletin Paie
```

#### Traitement de la Régularisation par Congé Annuel avec Solde Partiel
L'instruction de Phase 6 a définitivement clarifié le traitement du solde insuffisant lors d'une régularisation :

1. **Solde Disponible Suffisant** :  
   Absence de 1,0 jour, Solde disponible de 2,5 jours  
   $\rightarrow$ Imputation de 1,0 jour en congé payé (`nb_jours_decomptes = 1.00`).  
   $\rightarrow$ Statut journalier = `CONGE`. Retenue absence = 0 heure.  
   $\rightarrow$ Mouvement `PRISE` de 1,0 jour au grand-livre. Nouveau solde = 1,5 jour.

2. **Solde Disponible Partiel (Décision Phase 6)** :  
   Absence de 1,0 jour, Solde disponible de 0,5 jour  
   $\rightarrow$ Consommation maximale : $\min(1.0, 0.5) = \mathbf{0{,}5\text{ jour}}$.  
   $\rightarrow$ Création d'une demande de congé de 0,5 jour avec mouvement `PRISE` de 0,5 jour. Nouveau solde = 0,00 jour.  
   $\rightarrow$ **Statut de synthèse dans Pointage = `DEMI_JOURNEE`**.  
   $\rightarrow$ **Le reliquat de 0,5 jour (4 heures) demeure comptabilisé en absence retenue**. La Paie déduira exactement 4 heures au taux horaire.

3. **Solde Nul** :  
   Absence de 1,0 jour, Solde disponible de 0,0 jour  
   $\rightarrow$ Aucune demande de congé créée. Aucun mouvement `PRISE`.  
   $\rightarrow$ Statut journalier = `ABSENCE_JUSTIFIEE` ou `ABSENCE_NON_JUSTIFIEE`.  
   $\rightarrow$ Retenue complète de 8 heures en Paie. **Jamais de solde négatif**.

```
Record Java de Retour Structuré :
mg.galaxam.sirh.conge.application.RegularisationCongeResult(
    Integer demandeId,
    double joursDemandes,
    double joursCouverts,
    double joursRestants,
    BigDecimal soldeAvant,
    BigDecimal soldeApres
)
```

---

## 9. Architecture des Indemnités Journalières & Forfaits

Le module assure l'attribution automatique, l'ajustement et le scellement des indemnités liées aux conditions d'exécution du travail.

### 9.1 Catalogue des Indemnités (`ref_indemnite`)

| Code Indemnité | Libellé Officiel | Périodicité Type | Fractionnable ? | Imposable IRSA ? | Soumis CNaPS/OSTIE ? | Rubrique Paie |
| :--- | :--- | :---: | :---: | :---: | :---: | :--- |
| **`REPAS`** | Indemnité de Restauration / Panier | JOURNALIER | NON | NON | NON | `INDEMNITE_REPAS` |
| **`DPLCT` / `DEPLACEMENT`** | Forfait Transport & Déplacement | FIXE_MENSUEL ou JOUR | OUI | NON | NON | `INDEMNITE_TRANSPORT` |

### 9.2 Règle d'Attribution de l'Indemnité de Restauration (`REPAS`)

1. **Éligibilité de Base** : L'indemnité repas est accordée sur toute journée ouvrable travaillée où le collaborateur est physiquement présent sur site, sous réserve de la configuration active du site dans `site_indemnite_config`.
2. **Cas des Sites à Montant Zéro** : Si un site offre la cantine en nature (ex : cantine d'entreprise à Tamatave), `montant_reference = 0.00`. L'indemnité monétaire est nulle.
3. **Repas Supplémentaire pour Sortie Tardive** :
   Le moteur `AttendanceEngine.calculateMealAmount()` supporte l'attribution d'un supplément si le salarié prolonge son activité au-delà de seuils horaires configurés dans `parametre_global` :
   - Dépassement Seuil 1 (ex : sortie $\ge$ 19:30) $\rightarrow$ Majoration de montant 1.
   - Dépassement Seuil 2 (ex : sortie $\ge$ 21:00) $\rightarrow$ Majoration de montant 2.

### 9.3 Règle de Déplacement & Proratisations

- **Forfait Mensuel (`FIXE_MENSUEL`)** : Prévu pour les salariés bénéficiant d'une enveloppe mensuelle de transport. La méthode `enregistrerIndemnitesFixesMensuelles()` calcule la quote-part journalière par jour calendaire de rattachement au site.
- **Indemnité Journalière au Trajet** : Attribuée par demi-journée ou journée entière (`fraction = MATIN / APRES_MIDI / JOUR_ENTIER`). Si le salarié utilise un véhicule de fonction pour sa mission (`utilise_vehicule_fonction_jour = TRUE`), l'indemnité est automatiquement annulée.

---

## 10. Le Protocole de Clôture : Gel, Snapshots & Immutabilité

Le gel mensuel de la période constitue l'opération charnière qui transfère la responsabilité des données temporelles du domaine Pointage vers le domaine Paie.

### 10.1 Séquence d'Exécution de la Clôture (`APPROVE`)

```
      GESTIONNAIRE DAF                       POINTAGE SYNTHÈSE SERVICE                    BASE POSTGRESQL
             │                                          │                                        │
             │ POST /periodes/{id}/actions/APPROVE      │                                        │
             ├─────────────────────────────────────────►│                                        │
             │                                          │ Transaction ouverte                    │
             │                                          │                                        │
             │                                          │ 1. UPDATE cumul existant               │
             │                                          │    statut_snapshot = 'REMPLACE'        │
             │                                          ├───────────────────────────────────────►│
             │                                          │                                        │
             │                                          │ 2. INSERT pointage_cumul_periode       │
             │                                          │    (statut 'GELE', version N+1)        │
             │                                          ├───────────────────────────────────────►│
             │                                          │                                        │
             │                                          │ 3. INSERT pointage_snapshot_jour       │
             │                                          │    (audit provenance journées)         │
             │                                          ├───────────────────────────────────────►│
             │                                          │                                        │
             │                                          │ 4. INSERT pointage_snapshot_indemnite  │
             │                                          │    (indemnités figées avec fiscalité)  │
             │                                          ├───────────────────────────────────────►│
             │                                          │                                        │
             │                                          │ 5. INSERT pointage_snapshot_bucket     │
             │                                          │    (tranches qualifiées HS/Nuit/Dim)   │
             │                                          ├───────────────────────────────────────►│
             │                                          │                                        │
             │                                          │ Commit transaction                     │
             │ 200 OK (Période VALIDEE)                 │                                        │
             │◄─────────────────────────────────────────┤                                        │
```

### 10.2 Inventaire des 4 Tables Immutables de Snapshot

1. **`pointage_cumul_periode`** : Contrat de synthèse principal. Contient les totaux certifiés par salarié pour la période (heures travaillées, HS 30%, HS 50%, heures de nuit, dimanches, fériés, retards en minutes, jours d'absence).
2. **`pointage_snapshot_jour`** : Table d'audit de provenance. Fige l'état exact de chaque journée ayant produit le cumul (version du pointage, minutes prestées, statut du jour, et l'heure effective valorisée `heure_entree_effective`).
3. **`pointage_snapshot_indemnite`** : Fige les montants et le traitement fiscal/social (`imposable_irsa`, `soumis_cotisation`) de chaque indemnité accordée. Si les règles fiscales changent ultérieurement dans les référentiels, le snapshot préserve fidèlement la règle en vigueur au moment du gel.
4. **`pointage_snapshot_bucket`** : Fige le découpage élémentaire des tranches horaires (`QualifiedTimeBucket`) pour la valorisation détaillée par tranche par le calculateur de paie.

---

## 11. Consommation des Données Scellées par le Moteur de Paie

### 11.1 Étanchéité Stricte Paie ↔ Pointage

> **Règle architecturale absolue** : Le composant `PayrollService` ne réalise **JAMAIS** de requête directe sur la table mutable `pointage_jour`.

Toute tentative de calculer un bulletin de paie réel alors que la période n'a pas été scellée déclenche une exception bloquante :
`IllegalStateException: MISSING_ATTENDANCE_SNAPSHOT: aucun snapshot Pointage applicable`.

### 11.2 Matrice de Consommation et Valorisation en Paie

| Rubrique du Bulletin de Paie | Source dans le Snapshot Scellé | Formule de Valorisation dans le Moteur de Paie (`PayrollService`) |
| :--- | :--- | :--- |
| **Heures Normales de Base** | `pointage_cumul_periode.nb_jours_ouvrables` | Salaire de base contractuel maintenu mensuellement ($S_{base}$). |
| **Retenue pour Absence** | `pointage_cumul_periode.heures_absence_retenues` | $\text{Retenue} = \text{HeuresAbsence} \times \left(\frac{S_{base}}{173{,}33}\right)$. |
| **Heures Supplémentaires 30%** | `pointage_cumul_periode.heures_hs_30` ou buckets | $\text{Gain} = \text{HeuresHS30} \times \left(\frac{S_{base}}{173{,}33}\right) \times 1{,}30$. |
| **Heures Supplémentaires 50%** | `pointage_cumul_periode.heures_hs_50` ou buckets | $\text{Gain} = \text{HeuresHS50} \times \left(\frac{S_{base}}{173{,}33}\right) \times 1{,}50$. |
| **Majoration Travail de Nuit** | Buckets qualifiés `night_work_type != NONE` | Application du barème conventionnel (majoration horaire nuit). |
| **Majoration Dimanche & Férié** | Buckets qualifiés `day_special_type` | Application des majorations légales selon le Décret 72-226. |
| **Indemnité de Restauration** | `pointage_snapshot_indemnite` (`REPAS`) | Versement non imposable, non cotisable (rubrique `INDEMNITE_REPAS`). |
| **Indemnité de Déplacement** | `pointage_snapshot_indemnite` (`DEPLACEMENT`) | Versement non imposable, non cotisable (rubrique `INDEMNITE_TRANSPORT`). |

---

## 12. Architecture Logicielle Backend & Catalogue d'Endpoints

### 12.1 Arborescence des Composants Backend

```
backend/src/main/java/mg/galaxam/sirh/pointage/
├── api/
│   ├── PointageController.java              # API REST Saisie, Preview, Synthèse, Anomalies, Config
│   ├── PointagePeriodeController.java       # API REST Cycle de vie des Périodes de Paie
│   └── dto/                                 # Objets de transfert normalisés (DTO)
├── application/
│   ├── PointageService.java                 # Service Orchestrateur Saisie, Plancher, Batch (2041 LOC)
│   ├── PointageSyntheseService.java         # Synthèse mensuelle, Semaines, Gel & Snapshots (1800 LOC)
│   ├── PointageAnomalieService.java         # Détection des anomalies & Régularisations (894 LOC)
│   ├── PointageEmployeService.java          # Fiche individuelle de présence employé (1057 LOC)
│   └── PointageExcelExportService.java      # Exporteur de reporting Excel Apache POI (600 LOC)
├── domain/
│   ├── calculation/
│   │   ├── AttendanceEngine.java            # Moteur pur de calcul journalier et hebdomadaire
│   │   ├── TemporalAttendanceEngine.java    # Partitionneur temporel et projection chronologique HS
│   │   ├── QualifiedTimeBucket.java         # Record immuable d'un bucket temporel qualifié
│   │   ├── DaySpecialType.java              # Enumération des jours spéciaux (Dimanche, Férié)
│   │   ├── NightWorkType.java               # Enumération des types de travail de nuit
│   │   └── OvertimeTier.java                # Enumération des paliers d'heures sup (REGULAR, HS30, HS50)
│   └── engine/
│       └── CalendrierOuvrableEngine.java    # Moteur de calcul des jours ouvrables et fériés
└── persistence/
    └── EmployeIndemniteDerogation.java      # Entité de dérogation individuelle aux indemnités
```

### 12.2 Catalogue Exhaustif des Endpoints REST

| Méthode | Route HTTP | Rôle / Description | Autorisation Requise | Corps Entrant | Format Réponse |
| :---: | :--- | :--- | :--- | :--- | :--- |
| `GET` | `/pointages/saisie` | Charge la grille de pointage d'un site à une date donnée | `POINTAGE_READ` | Aucun | JSON (liste des lignes de grille) |
| `POST` | `/pointages/saisie/preview` | Prévisualise les temps et le statut d'une journée sans écrire | `POINTAGE_READ` | `SaisiePointageJourDTO` | `SaisiePointageJourDTO` enrichi |
| `POST` | `/pointages/saisie/batch` | Enregistre en base un lot de pointages avec détection conflits | `POINTAGE_WRITE` | `List<SaisiePointageJourDTO>` | JSON `{success, count}` |
| `GET` | `/pointages/synthese` | Synthèse mensuelle paginée par employé et période | `POINTAGE_READ` | Aucun | JSON `{synthese, totalPages...}` |
| `GET` | `/pointages/synthese/semaines` | Liste des semaines et tronçons de la période avec métriques | `POINTAGE_READ` | Aucun | JSON `{semaines: [...]}` |
| `GET` | `/pointages/synthese/semaine-detail` | Détail d'une semaine spécifique pour l'ensemble des agents | `POINTAGE_READ` | Aucun | JSON `{detail: [...]}` |
| `POST` | `/pointages/synthese/calculer-tout` | Recalcule en mémoire toutes les semaines d'une période | `POINTAGE_WRITE` | `{idPeriodePaie, idSite}` | JSON `{recalculated: true}` |
| `GET` | `/pointages/anomalies` | Liste filtrable des anomalies de présence avec sévérités | `POINTAGE_READ` | Aucun | JSON `{anomalies, stats, kpis}` |
| `PUT` | `/pointages/anomalies/{id}/regulariser` | Régularise une anomalie (heures, congé payé, permission) | `POINTAGE_WRITE` | Payload de régularisation | JSON `{success, message}` |
| `GET` | `/pointages/employes/{id}` | Fiche de pointage individuelle d'un collaborateur | `POINTAGE_READ` | Aucun | `FichePointageEmployeDTO` |
| `GET` | `/pointages/employes/{id}/export/excel` | Téléchargement du relevé individuel format Excel (.xlsx) | `POINTAGE_READ` | Aucun | Fichier binaire streamé |
| `GET` | `/pointages/parametres/tolerances` | Liste des configurations plancher d'arrivée par site | Authentifié | Aucun | JSON (sites, plancher, actif) |
| `POST` | `/pointages/parametres/tolerances` | Met à jour le plancher d'arrivée et son activation pour un site | `ROLE_ADMIN` | `{idSite, estActif, heurePlafond}` | JSON mis à jour |
| `GET` | `/periodes` | Liste chronologique des périodes de paie avec actions permises | `POINTAGE_READ` | Aucun | `List<PeriodePaieDTO>` |
| `POST` | `/periodes` | Crée une nouvelle période de paie mensuelle | `ROLE_ADMIN` / Paie | `{code, libelle, dateDebut, dateFin}` | JSON `{success: true}` |
| `POST` | `/periodes/{id}/actions/{command}` | Exécute une transition d'état (`OPEN`, `SUBMIT`, `APPROVE`...) | Rôles selon commande | Request avec motif | `BusinessDecision` |

---

## 13. Architecture Frontend, IHM & Composant Gantt

### 13.1 Module Client `features/attendance`

Le frontend est construit sous React avec Vite, Tailwind CSS et des composants d'interface modulaires :

```
frontend/src/features/attendance/
├── api/
│   └── attendanceApi.js             # Client Axios centralisé regroupant les appels par intention
├── components/
│   ├── controls/                    # Composants de formulaire et sélecteurs de grille
│   ├── modals/                      # Modales d'arbitrage (DafAjustement, AnomalieRegularisation)
│   └── views/
│       ├── PointageGridTable.jsx    # Grille de saisie matricielle quotidienne
│       └── PointageGanttView.jsx    # Vue Gantt hebdomadaire multi-salariés (KEEP)
├── hooks/
│   ├── usePointageSaisie.js         # Gestion de l'état de la grille et appels preview en temps réel
│   ├── usePointageAnomalies.js      # Chargement réactif des anomalies et filtres
│   └── usePeriodePaie.js            # Contexte de période active et stepper de navigation
├── pages/
│   ├── GestionPointagesPage.jsx     # Écran de saisie quotidienne (Agent de pointage)
│   ├── ArbitrageDafPage.jsx         # Console d'arbitrage DAF (Synthèse, anomalies, gel APPROVE)
│   └── ParametragePointagePage.jsx  # Configuration des sites et dérogations d'indemnités
└── utils/
    ├── pointageCalculations.js      # UNIQUEMENT : dirty-check UI (isRowDataModified)
    └── attendanceDisplayUtils.js    # Formatage des badges, heures et sévérités
```

### 13.2 Rôle & Maintien de la Vue Gantt (`PointageGanttView`)

Le composant `PointageGanttView.jsx` (580 lignes) a été expressément confirmé et maintenu lors des décisions de Phase 6 (`KEEP`). Il offre à la Direction Financière une visualisation temporelle graphique horizontale des 7 jours de chaque semaine :
- Chaque collaborateur est visualisé sur une ligne temporelle découpée du lundi au dimanche.
- Un code couleur visuel distingue instantanément le type de présence (Vert = Présent complet, Ambre = Demi-journée, Bleu = Congé payé, Rouge = Absence injustifiée, Violet = Jour férié ou dimanche travaillé).
- L'infobulle au survol (*hover tooltip*) détaille les heures d'entrée/sortie, le retard cumulé et les indemnités attachées sans changer de page.

---

## 14. Dictionnaire de Données PostgreSQL & Modèle Relationnel

### 14.1 Dictionnaire Exhaustif de la Table `pointage_jour`

| Nom de la Colonne | Type SQL | Nullable | Valeur par Défaut | Signification Métier | Source & Modalité de Mise à Jour |
| :--- | :--- | :---: | :---: | :--- | :--- |
| `id_pointage` | `SERIAL` | NON | auto-incrément | Clé primaire unique de la journée de pointage. | Système (généré à l'insertion). |
| `id_employe` | `INTEGER` | NON | — | Référence vers le collaborateur (`employe.id_employe`). | Saisie / Sélection de grille. |
| `id_periode_paie` | `INTEGER` | NON | — | Période de paie couvrant la date de pointage. | Résolu automatiquement par les bornes de dates. |
| `id_site` | `INTEGER` | NON | — | Établissement physique d'exécution du travail. | Affectation carrière ou sélection de grille. |
| `date_pointage` | `DATE` | NON | — | Date civile du jour ouvré ou de présence. | Sélecteur de date de pointage. |
| `heure_entree_matin` | `TIME` | OUI | `NULL` | Heure brute réelle de première arrivée constatée. | Badge physique ou saisie de l'agent. |
| `heure_entree_matin_effective` | `TIME` | OUI | `NULL` | Heure valorisée après application du plancher site. | Calculé par `effectiveMorningEntry()`. |
| `heure_sortie_matin` | `TIME` | OUI | `NULL` | Heure de départ pour la pause déjeuner (discontinu). | Badge physique ou saisie de l'agent. |
| `heure_entree_am` | `TIME` | OUI | `NULL` | Heure de reprise de travail l'après-midi. | Badge physique ou saisie de l'agent. |
| `heure_sortie_am` | `TIME` | OUI | `NULL` | Heure de départ définitif en fin de journée. | Badge physique ou saisie de l'agent. |
| `id_statut_jour` | `INTEGER` | NON | — | Statut de présence qualifié (`ref_statut_pointage_jour`). | Dérivé par `AttendanceEngine` ou régularisé. |
| `heures_matin` | `DECIMAL(4,2)` | OUI | `0.00` | Volume horaire accompli durant la matinée. | Calculé : $\text{morningMinutes} / 60$. |
| `heures_am` | `DECIMAL(4,2)` | OUI | `0.00` | Volume horaire accompli durant l'après-midi. | Calculé : $\text{afternoonMinutes} / 60$. |
| `total_heures` | `DECIMAL(4,2)` | OUI | `0.00` | Volume total de travail effectif de la journée. | Calculé : $\text{workedMinutes} / 60$. |
| `heures_nuit` | `DECIMAL(4,2)` | NON | `0.00` | Volume horaire presté sur le créneau légal 22h-05h. | Calculé : $\text{nightMinutes} / 60$. |
| `minutes_retard` | `INTEGER` | OUI | `0` | Retard cumulé à l'embauche du matin et reprise AM. | Calculé : `lateMinutes` par `AttendanceEngine`. |
| `minutes_depart_anticipe` | `INTEGER` | OUI | `0` | Minutes de départ anticipé avant la fin normale. | Calculé selon les heures de sortie de site. |
| `utilise_vehicule_fonction_jour` | `BOOLEAN` | OUI | `FALSE` | Indicateur d'usage d'un véhicule d'entreprise. | Saisie agent (annule l'indemnité transport). |
| `horaire_continu_jour` | `BOOLEAN` | OUI | — | Modalité continue ou fractionnée de la journée. | Hérité de `ref_site.horaire_continu`. |
| `montant_total_indemnites` | `NUMERIC(12,2)` | OUI | `0.00` | Total monétaire des indemnités du jour. | Somme dénormalisée de `pointage_indemnite_jour`. |
| `motif_ajustement` | `TEXT` | OUI | `NULL` | Justification administrative en cas d'anomalie/absence. | Saisie de l'agent ou de la DAF. |
| `saisi_par` | `INTEGER` | OUI | `NULL` | Identifiant de l'utilisateur ayant saisi la ligne. | Utilisateur authentifié (`utilisateur`). |
| `ajuste_par_daf` | `BOOLEAN` | OUI | `FALSE` | Vrai si la ligne a fait l'objet d'un arbitrage DAF. | Positionné lors de l'arbitrage. |
| `ajuste_par` | `INTEGER` | OUI | `NULL` | Utilisateur DAF ayant validé la régularisation. | Utilisateur authentifié. |
| `date_ajustement` | `TIMESTAMP` | OUI | `NULL` | Horodatage de la dernière régularisation. | `CURRENT_TIMESTAMP`. |
| `id_demande_conge` | `INTEGER` | OUI | `NULL` | Lien vers la demande de congé payé régularisée. | Positionné par `CongeService`. |
| `genere_automatiquement` | `BOOLEAN` | NON | `FALSE` | Vrai si créé par un batch de rattrapage automatique. | Marqueur technique de génération. |
| `version` | `INTEGER` | NON | `1` | Numéro de version pour verrouillage optimiste. | Incrémenté à chaque `UPDATE`. |
| `created_at` | `TIMESTAMP` | OUI | `NOW()` | Horodatage d'insertion initiale. | Système. |
| `updated_at` | `TIMESTAMP` | OUI | `NOW()` | Horodatage de dernière modification. | Système. |

---

## 15. Catalogue Exhaustif des Règles de Gestion (RG)

| Identifiant RG | Intitulé de la Règle | Énoncé Formel & Formule de Calcul | Composant d'Exécution |
| :--- | :--- | :--- | :--- |
| **RG-PTG-01** | **Période de saisie active** | La saisie quotidienne n'est autorisée que si la période de paie associée est au statut `EN_COURS`. Si la période est `VALIDEE`, `PAIE_VALIDEE` ou `CLOTUREE`, tout rejet par verrouillage strict est levé. | `PointageService`, `PointageAnomalieService` |
| **RG-PTG-02** | **Interdiction des dates futures** | Il est formellement interdit de saisir un pointage sur une date postérieure à la date civile courante ($Date_{pointage} \le Date_{systeme}$). | `PointageService.verifierDateNonFuture()` |
| **RG-PTG-03** | **Plancher d'arrivée valorisé** | Si `ref_site.plancher_arrivee_actif = TRUE`, l'entrée valorisée vaut $H_{eff} = \max(H_{brut}, H_{min})$. Si inactif ou non configuré, $H_{eff} = H_{brut}$. Aucun fallback 07:30 en dur n'est toléré. | `PointageService.effectiveMorningEntry()` |
| **RG-PTG-04** | **Unicité journalière** | Un collaborateur ne peut posséder qu'un seul enregistrement de pointage par date civile (contrainte d'unicité `uk_pointage_employe_jour`). | Base PostgreSQL (`pointage_jour`) |
| **RG-PTG-05** | **Compensation hebdomadaire** | Les dépassements journaliers d'une semaine compensent en priorité les manques des autres jours ouvrables avant toute éligibilité aux heures supplémentaires. | `AttendanceEngine.calculateWeek()` |
| **RG-PTG-06** | **Éligibilité aux heures sup** | Les heures supplémentaires ne sont calculées que pour les catégories professionnelles non cadres (`overtimeEligible = TRUE`). Pour les cadres dirigeants, les heures sup sont nulles. | `PointageSyntheseService.calculerSyntheseEmploye()` |
| **RG-PTG-07** | **Ventilation HS 30% et 50%** | Les heures supplémentaires jusqu’au palier `paie_config_legale.seuil_hs_tranche1` sont imputées à 30 %, puis à 50 %. La clôture de période applique à nouveau cette ventilation au solde après rapprochement DAF. | `AttendanceEngine.calculateWeek()` |
| **RG-PTG-08** | **Orthogonalité des sujétions** | Une heure de travail effectuée la nuit ou un dimanche ne peut donner lieu à un double paiement de sa base horaire. Les sujétions sont isolées dans des `QualifiedTimeBucket`. | `TemporalAttendanceEngine`, `OvertimePayCalculator` |
| **RG-PTG-09** | **Créneau légal de nuit** | Tout travail exécuté entre 22:00 et 05:00 du matin est obligatoirement qualifié de travail de nuit et valorisé avec la majoration horaire correspondante. | `TemporalAttendanceEngine.AttendanceTemporalPartitioner` |
| **RG-PTG-10** | **Prérequis de transmission** | La transmission d'une période par l'agent (`SUBMIT`) est interdite tant qu'il subsiste au moins une anomalie bloquante (`ANOMALIE_SAISIE` ou absence injustifiée sans motif). | `PayrollPeriodWorkflowService.submissionCapability()` |
| **RG-PTG-11** | **Régularisation congé partiel** | La régularisation d'une absence par congé annuel consomme $\min(\text{absence}, \text{solde})$. Le reliquat non couvert reste comptabilisé en absence retenue. Aucun solde négatif n'est permis. | `CongeService.regulariserCongeAnnuelDepuisPointageStructure()` |
| **RG-PTG-12** | **Idempotence de régularisation** | Toute réexécution d'une régularisation déjà actée portant le tag `[REGULARISATION_POINTAGE]` retourne le résultat d'origine sans créer de second débit dans le grand-livre. | `CongeService` |
| **RG-PTG-13** | **Scellement au gel (APPROVE)** | L'approbation DAF génère un cumul figé (`pointage_cumul_periode`) au statut `GELE` et archive les snapshots d'audit. Tout cumul antérieur de la même période passe à l'état `REMPLACE`. | `PointageSyntheseService.gelerPeriode()` |
| **RG-PTG-14** | **Réouverture auditée (REOPEN)** | La réouverture d'une période clôturée ou gelée exige un motif d'au moins 5 caractères. Elle rétrograde le cumul gelé à `REMPLACE` tout en conservant intacts les anciens snapshots. | `PayrollPeriodWorkflowService.execute(REOPEN)` |
| **RG-PTG-15** | **Étanchéité de Paie** | Le moteur de Paie calcule les bulletins de paie exclusivement à partir des données figées dans `pointage_cumul_periode` et de ses snapshots associés. Aucune lecture directe sur `pointage_jour`. | `PayrollService.calculerBulletinPaie()` |

---

## 16. Cas d'Usage Chiffrés & Scénarios de Référence

### 16.1 Scénario 1 : La Semaine avec Compensation et Dépassement Net

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

### 16.2 Scénario 2 : Vacation Dominicale avec Dépassement Nocturne

Un technicien de maintenance intervient d'urgence un Dimanche de 18:00 à Lundi 02:00 du matin (horaire continu, durée = 8 heures = 480 minutes). Cette vacation intervient alors que le technicien a déjà accompli ses 40 heures normales du lundi au vendredi.

#### Découpage par `TemporalAttendanceEngine` :
- **Bucket 1 : Dimanche 18:00 – 22:00 (4 heures = 240 minutes)**  
  $\rightarrow$ `daySpecialType = SUNDAY`, `nightWorkType = NONE`, `overtimeTier = OVERTIME_30`.
- **Bucket 2 : Dimanche 22:00 – 00:00 (2 heures = 120 minutes)**  
  $\rightarrow$ `daySpecialType = SUNDAY`, `nightWorkType = NIGHT_UNCLASSIFIED`, `overtimeTier = OVERTIME_30`.
- **Bucket 3 : Lundi 00:00 – 02:00 (2 heures = 120 minutes)**  
  $\rightarrow$ `daySpecialType = STANDARD`, `nightWorkType = NIGHT_UNCLASSIFIED`, `overtimeTier = OVERTIME_30`.

#### Conséquence et Valorisation en Paie :
- Total heures sup 30% : 8 heures (majorées à 30%).
- Heures sujétion Dimanche : 6 heures (Buckets 1 et 2, majorées au taux dominical).
- Heures sujétion Nuit : 4 heures (Buckets 2 et 3, majorées au taux légal de nuit).
- **Zéro double comptage** : Chaque tranche temporelle est scellée une seule fois dans `pointage_snapshot_bucket` avec ses attributs croisés exacts.

---

### 16.3 Scénario 3 : Absence Régularisée avec Solde de Congé Partiel

Un collaborateur est absent le 15 Septembre (journée de 8 heures). Son solde de congés disponible au grand-livre est de **0,50 jour** (4 heures).
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

### 16.4 Rapprochement réel de la période juillet–août 2026

La somme des soldes hebdomadaires est suivie d'un ajustement horaire libre,
motivé et saisi par la DAF. Dans l'exemple TMM, 537 minutes de HS à 30 % et
un ajustement de −150 minutes donnent 387 minutes de HS valorisables. Dans
l'exemple TNR, 249 minutes de HS, 109 minutes de déficit hebdomadaire et un
ajustement de −160 minutes donnent un solde de −20 minutes, soit une absence
horaire. Les congés de 9 et 3 jours restent des jours calendaires distincts
de ce solde. Le [rapport de rapprochement](./rapprochement-pointage-paie-juillet-aout-2026.md)
conserve les chiffres et les limites de comparaison avec Excel.

---

## 17. Dettes Techniques Résolues & Points de Vigilance

### 17.1 Dettes Techniques Définitivement Éliminées (Phase 6)

1. **Suppression de la table d'indemnité semaine et du cache semaine** (`sp22`, `sp23`) : Le calcul des semaines est désormais purement dynamique en mémoire à partir des journées sources, éliminant tout risque de désynchronisation de données dérivées.
2. **Migration du plancher d'arrivée dans `ref_site`** (`sp27`) : Suppression de l'ancienne table `ref_tolerance_arrivee_pointage`, élimination de l'entité Java orpheline `RefToleranceArriveePointage.java` et suppression de tous les fallbacks en dur (`07:30`).
3. **Capture de l'heure effective valorisée dans le snapshot** (`sp26`) : La colonne `pointage_snapshot_jour.heure_entree_effective` permet désormais de reconstituer à 100% quelle heure valorisée exacte a servi à chaque gel V1, V2 ou V3.
4. **Correction du type décompte congé en base** (`sp27`) : La colonne `demande_conge.nb_jours_decomptes` a été migrée en `NUMERIC(5,2)` (avec recompilation des vues SQL associées), permettant le décompte exact des fractions de congé lors des régularisations partielles.
5. **Nettoyage strict des calculs frontend** : Le fichier `pointageCalculations.js` est certifié exempt de toute logique arithmétique de temps ou de statut.

### 17.2 Points de Vigilance pour les Développeurs & Administrateurs

- **Modification rétroactive des affectations de carrière** : Si un administrateur RH modifie la catégorie professionnelle d'un salarié dans le passé (ex : passage de non-cadre à cadre), les pointages passés déjà scellés (`GELE`) ne sont pas impactés. En revanche, si la période est rouverte (`REOPEN`), le nouveau gel appliquera la nouvelle catégorie lors de la recalculation des quotas.
- **Ajout d'une nouvelle indemnité au catalogue** : Toute nouvelle indemnité créée dans `ref_indemnite` doit obligatoirement avoir ses indicateurs `imposable_irsa` et `soumis_cotisation` renseignés avant qu'une période ne puisse être scellée (`enregistrerProvenanceSnapshot` bloque le gel si une indemnité attribuée n'a pas de qualification sociale/fiscale explicite).

---

## 18. Annexes Techniques & Glossaire Terminologique

### Passage du solde final vers la paie

Après compensation de chaque semaine, le solde de période est calculé en minutes :
`HS30 hebdomadaires - déficit hebdomadaire + ajustements DAF signés`.
Un solde négatif devient une absence horaire hors congés ; un solde positif
devient des HS 30 % finales. Le précontrôle de la période bloque un solde de
HS supérieur aux HS attribuées à des semaines et affiche les collaborateurs à
corriger avant le gel.

La paie consomme les deux quantités finales du snapshot. Elle conserve les
buckets datés pour appliquer les taux de majoration configurés et requalifie
chronologiquement en heures normales les HS 30 % utilisées pour compenser un
déficit. Les qualifications nuit, dimanche et férié de ces heures restent
intactes. Les congés sont valorisés séparément en jours : déduction de base au
1/30 et indemnité au taux historique. Les indemnités de site sont figées avec
leurs indicateurs d'imposition et de cotisation ; la prime exceptionnelle
reste une variable de paie distincte. Le total historique « AUTIMP » est la
somme du déplacement et de la prime exceptionnelle selon la DAF. Lorsqu'un
repas à 0 Ar est accordé sur un site concerné, la réduction paramétrée de
1 000 Ar porte sur le déplacement accordé ce jour-là, avec un plancher à
zéro. Les deux gains restent séparés sur le bulletin.

---

### 18.1 Glossaire Méthodologique & Métier

- **Auto-Break (Pause Automatique)** : Mécanisme d'inférence d'un horaire discontinu lorsque le collaborateur badge son entrée du matin et sa sortie du soir sans badger la pause déjeuner. Le système impute automatiquement les heures théoriques de pause pour ne pas pénaliser le décompte.
- **Bucket Qualifié (`QualifiedTimeBucket`)** : Segment temporel élémentaire, homogène et continu ne traversant jamais de frontière calendaire (minuit), portant simultanément une qualification de sujétion et un palier d'heure supplémentaire.
- **Compensation Hebdomadaire** : Absorption arithmétique prioritaire des déficits journaliers par les surplus journaliers d'une même semaine civile avant attribution de majorations pour heures supplémentaires.
- **Entrée Effective Valorisée** : Heure d'embauche retenue pour le calcul financier, résultant de l'application du plancher d'arrivée site sur le badge physique brut.
- **Gel de Période (`APPROVE`)** : Action DAF scellant la synthèse mensuelle et ses preuves historiques (snapshots), constituant la seule vérité admise par le moteur de Paie.
- **Plancher d'Arrivée (*Earliest Paid Start*)** : Heure minimale à compter de laquelle l'activité d'un salarié présent sur site commence à être rémunérée.
- **Réouverture (`REOPEN`)** : Transition exceptionnelle permettant de déverrouiller une période scellée pour corrections, marquant le cumul antérieur au statut `REMPLACE` tout en conservant l'audit des snapshots historiques.
- **Tronçon Hebdomadaire (`WeekSegment`)** : Intersection géométrique et calendaire entre une semaine civile (lundi à dimanche) et les dates limites d'une période de paie mensuelle.

---

### 18.2 Matrice Complète des Statuts de Pointage Journalier

```sql
SELECT id_statut_jour, code, libelle, decompte_absence, necessite_saisie 
FROM ref_statut_pointage_jour ORDER BY id_statut_jour;
```

| ID | Code Statut | Libellé | Décompte Absence ? | Nécessite Saisie ? |
| :---: | :--- | :--- | :---: | :---: |
| 1 | `PRESENT` | Présent (journée complète normale) | NON | OUI |
| 2 | `ABSENCE_NON_JUSTIFIEE` | Absence non justifiée (non régularisée) | **OUI** | NON |
| 3 | `ANOMALIE_SAISIE` | Pointage incomplet (entrée ou sortie manquante) | NON | OUI |
| 4 | `ABSENCE_JUSTIFIEE` | Absence justifiée (avec motif administratif) | **OUI** | NON |
| 5 | `CONGE` | En congé payé (matérialisé depuis demande) | NON | NON |
| 6 | `FERIE_TRAVAILLE` | Jour férié travaillé | NON | OUI |
| 7 | `FERIE_CHOME` | Jour férié chômé légal | NON | NON |
| 8 | `SAMEDI_TRAVAILLE` | Samedi travaillé (surplus pur) | NON | OUI |
| 9 | `DIMANCHE_TRAVAILLE` | Dimanche travaillé (sujétion dominicale) | NON | OUI |
| 10 | `DISPENSE_POINTAGE` | Poste autonome / dispense de pointage | NON | NON |
| 11 | `DEMI_JOURNEE` | Demi-journée travaillée (présence partielle) | NON | OUI |

---

> **CERTIFICATION D'EXHAUSTIVITÉ & DE CONFORMITÉ**  
> Le présent document a été confronté avec succès au code source JavaHEAD, aux scripts PostgreSQL canoniques et aux suites d'intégration réelles du dépôt GALAXAM SIRH. Il constitue l'ouvrage officiel de référence pour l'audit, l'exploitation et la soutenance de mémoire universitaire.

### Exports individuels adaptés à la période de paie
Les téléchargements Excel/PDF utilisent les bornes réelles de `periode_paie`, même à cheval sur deux mois. Le paramètre `period` d’une vue exploratoire ne remplace pas ces bornes ; une période inexistante produit `POINTAGE_PERIOD_CONTEXT_REQUIRED`. Les modèles sont des supports de présentation adaptables : les lignes inutilisées sont retirées, les jours supplémentaires conservés, et seules les indemnités présentes sont affichées en colonnes. Le PDF répartit les colonnes d’indemnités sur plusieurs groupes si nécessaire. Une colonne inutilisée n’est pas exportée. Le journal montre les quantités accordées ; le récapitulatif montre les montants backend. Les seules formules Excel sont les sommes de présentation du journal.
