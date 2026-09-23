# Module Congés & Absences — Spécification Complète & Rapport de Référence
## GALAXAM SIRH — Documentation Canonique & Métier

> **Auteur** : Équipe d'Ingénierie GALAXAM SIRH  
> **Module** : Congés, Absences, Droits Acquis & Valorisation Financière  
> **Version** : 3.0.0 (Spécification Canonique & Référentiel Source de Vérité)  
> **Date de référence** : Septembre 2026  
> **Sources de référence** :
> - Loi n° 2003-044 du 28 juillet 2004 portant Code du Travail de Madagascar (Articles 13, 85, 86, 87, 88, 89, 90, 95, 97, 98)
> - Décret n° 2007-563 relatif à la durée légale du travail (40 heures hebdomadaires = 173,33 heures mensuelles)
> - Modèle mathématique officiel d'acquisition et valorisation : `OHATRA KAJY DROITS CONGES-given on sept26.xls`
> - Base de données canonique : `04_schema_conges.sql`

---

## Sommaire Exécutif

Le présent document constitue la **source unique de vérité documentaire et technique** du module **Congés & Absences** du SIRH GALAXAM.  
Il unifie :
1. Les **fondements juridiques malagasy** stricts régissant les congés payés, les absences et les suspensions de contrat.
2. Le **modèle mathématique d'acquisition et de valorisation financière** conforme à la pratique comptable et au modèle d'entreprise (`OHATRA KAJY DROITS CONGES`).
3. Le **modèle de données canonique** épuré et optimisé (suppression des tables de lookup artificielles et des vues redondantes).
4. Le **grand-livre comptable des congés (Ledger)** avec traçabilité intégrale (FIFO) et purge légale triennale.
5. Les **règles de gestion (RG)** détaillées de la saisie à la liquidation au Solde de Tout Compte (STC).
6. L'**architecture technique unifiée** (Backend Java Spring Boot, APIs REST, Frontend React).

## Table des Matières

1. [Cadre Légal et Réglementaire Malagasy](#1-cadre-légal-et-réglementaire-malagasy)
2. [Modèle Mathématique Canonique (OHATRA KAJY DROITS CONGES)](#2-modèle-mathématique-canonique-ohatra-kajy-droits-conges)
3. [Architecture du Modèle de Données BDD](#3-architecture-du-modèle-de-données-bdd)
4. [Règles de Gestion Métier (RG) Détaillées](#4-règles-de-gestion-métier-rg-détaillées)
5. [Workflow & Circuit de Validation](#5-workflow--circuit-de-validation)
6. [Automatisations & Schedulers Nocturnes](#6-automatisations--schedulers-nocturnes)
7. [Valorisation Financière & Articulation avec les Autres Modules](#7-valorisation-financière--articulation-avec-les-autres-modules)
8. [Synthèse des Éléments Supprimés & Simplifiés (Refactorisation)](#8-synthèse-des-éléments-supprimés--simplifiés-refactorisation)
9. [Recueil Textuel Intégral des Articles Légaux (Loi n° 2003-044)](#9-recueil-textuel-intégral-des-articles-légaux-loi-n-2003-044)
10. [Données d'Audit Intégrales du Modèle Excel Réel](#10-données-daudit-intégrales-du-modèle-excel-réel)
11. [Cartographie Complète des Composants Logiciels](#11-cartographie-complète-des-composants-logiciels)
12. [Cas Limites et Gestion des Exceptions Métier (Edge Cases)](#12-cas-limites-et-gestion-des-exceptions-métier-edge-cases)

---

## 1. Cadre Légal et Réglementaire Malagasy

### 1.1 Fondements Juridiques (Loi n° 2003-044 du 28 juillet 2004)

Les congés payés constituent une règle d'**ordre public absolu** : toute clause conventionnelle ou contractuelle prévoyant une indemnité compensatrice en lieu et place du congé annuel (hors rupture du contrat) ou une durée inférieure aux minima légaux est **nulle et de plein droit sans effet**.

| Article du Code | Objet & Portée Juridique | Traduction Fonctionnelle dans le SIRH |
| :--- | :--- | :--- |
| **Article 85** | **Durée du congé & Période de référence**<br>Le salarié acquiert droit au congé payé à la charge de l'employeur à raison de **2,5 jours calendaires par mois de service effectif** (soit 30 jours calendaires par an).<br>Le droit de jouissance est ouvert après **12 mois de service effectif**.<br>Prescription des droits à congé par **trois (3) ans**. | • Base légale d'acquisition : `2,50 jours/mois`.<br>• Verrouillage à la saisie avant 12 mois d'ancienneté (sauf dérogation RH expresse).<br>• Job de purge FIFO triennale (+36 mois) : plafond maximal théorique de 90 jours. |
| **Article 86** | **Bonification pour ancienneté & Salariées mères**<br>La durée du congé est augmentée en considération de l'ancienneté du travailleur dans l'entreprise, selon les conventions collectives ou règlements intérieurs. | • Bonification d'ancienneté automatique au mois anniversaire (seuil 3 ans, palier 5 ans, paramétrable dans `parametre_global`). |
| **Article 87** | **Périodes assimilées à du temps de travail effectif**<br>Sont considérées comme périodes de travail effectif pour l'acquisition des congés :<br>- Les périodes de congés payés antérieurs.<br>- Les congés de maternité (Art. 97).<br>- Les absences pour accidents du travail et maladies professionnelles.<br>- Les arrêts maladie dans la limite de 6 mois (Art. 13).<br>- Les permissions exceptionnelles d'événements familiaux (Art. 88). | • La formule d'acquisition mensuelle ne déduit **jamais** les jours de congés payés légaux, ni les permissions autorisées payées.<br>• Seules les **absences injustifiées** ou les congés sans solde suspendent l'acquisition au trentième. |
| **Article 88** | **Permissions exceptionnelles pour événements familiaux**<br>Autorisées jusqu'à concurrence de **10 jours par an**, non déductibles du congé annuel si payées, sous justificatif dans les 6 mois. | • Pool d'événements familiaux de **10 jours par année civile** partagé entre les motifs (mariage, naissance, décès). |
| **Article 89** | **Fractionnement du congé annuel**<br>Le congé peut être fractionné d'accord parties. Toutefois, une des fractions doit être d'une durée continue d'au moins **douze (12) jours consécutifs**. | • Détection et alertes RH de fin d'année (`FRACTIONNEMENT_NON_RESPECTE`) si aucune tranche $\ge 12$ jours n'a été prise dans l'année. |
| **Article 90** | **Indemnité de congé payé (Valorisation financière)**<br>L'employeur doit verser au travailleur, pendant toute la durée du congé, une allocation au moins égale à la moyenne des salaires, indemnités et primes des 12 derniers mois précédant le congé.<br>L'indemnité ne peut être inférieure au salaire que le travailleur aurait perçu s'il avait continué à travailler. | • Calcul et maintien de l'allocation congé via le taux journalier historique (`TCONGE`) issu de la moyenne des 12 mois de référence (`Cumul Valorisation / Cumul Droits`). |
| **Article 13** | **Suspension du contrat pour maladie non professionnelle**<br>Suspension du contrat dans la limite de 6 mois (180 jours). L'employeur est tenu de verser une indemnité égale au montant de sa rémunération dans la limite du délai de préavis (30 jours). | • Plafond de 30 jours de maintien de salaire pour congé `MALADIE`.<br>• Bascule au-delà vers `MALADIE_SANS_SOLDE` (prise en charge CNaPS). |
| **Article 97 & 98** | **Congé de Maternité**<br>Droit à un repos de **14 semaines consécutives (98 jours)** dont 6 semaines après l'accouchement. Possibilité de prolongation de **3 semaines (21 jours)** en cas de maladie résultant de la grossesse (soit 119 jours). | • Durée fixe stricte de 98 jours calendaires.<br>• Extension pathologique à 119 jours sous justificatif médical. |

---

## 2. Modèle Mathématique Canonique (`OHATRA KAJY DROITS CONGES`)

Le système implémente fidèlement le modèle comptable et financier consigné dans le document de référence d'entreprise `OHATRA KAJY DROITS CONGES-given on sept26.xls`.

### 2.1 Les Variables et Colonnes Fondamentales

Pour chaque mois $m \in [1, 12]$ de la période de référence d'un salarié :

| Symbole | Colonne Excel | Libellé Métier | Règle de Calcul & Description |
| :--- | :--- | :--- | :--- |
| $S_{base}$ | `BASE` | Salaire de Base Contractuel | Salaire mensuel de base du collaborateur pour le mois $m$. |
| $J_{abs}$ | `NBABS` | Total Heures/Jours d'Absence | Volume brut des absences constatées durant le mois. |
| $M_{abs}$ | `ABSENCES` | Retenue pour Absence | Montant financier déduit du salaire brut au titre des absences. |
| $J_{conge}$ | `NBCONGE` | Jours de Congé Pris | Nombre de jours calendaires de congés payés pris durant le mois. |
| $T_{conge}$ | `TCONGE` | Taux Journalier de Congé Actif | Taux journalier applicable issu du cycle précédent (ou initialisation). |
| $M_{conge}$ | `CONGE` | Indemnité de Congé Payé | $M_{conge} = J_{conge} \times T_{conge}$ |
| $S_{net\_base}$ | `NET BASE` | Net de Base | $S_{net\_base} = S_{base} - M_{abs} + M_{conge}$ |
| $M_{hs}$ | `HS` | Heures Supplémentaires | Montant des heures supplémentaires rémunérées durant le mois. |
| $M_{primes}$ | `AUTIMP` | Autres Indemnités Imposables | Primes d'assiduité, d'ancienneté, etc. |
| $S_{brut}$ | `BRUTE` | Salaire Brut Total | Total brut soumis aux charges. |
| $B_{totale}$ | `base totale` | Assiette de Droit à Congé | $B_{totale} = S_{net\_base} + M_{hs}$ |
| $F$ | `formule` | Base Trentième d'Activité | $F = 30 - \max(0, J_{abs} - J_{conge})$ *(Les congés pris ne réduisent pas $F$)* |
| $D_m$ | `nbre droit congés` | Droit à Congé Acquis du Mois | $D_m = 2{,}5 \times \frac{F}{30}$ *(Acquisition mensuelle proratisée)* |
| $V_m$ | `R*S/30` | Valorisation Mensuelle du Droit | $V_m = \frac{B_{totale} \times D_m}{30}$ |
| $CD_m$ | `cumul NB JR DROIT` | Cumul Annuel des Droits | $CD_m = \sum_{i=1}^{m} D_i$ |
| $CV_m$ | `Cumul Valorisation Droit` | Cumul Annuel de la Valorisation | $CV_m = \sum_{i=1}^{m} V_i$ |

---

### 2.2 Démonstration Pas à Pas du Cycle de 12 Mois (Cas Réel du Modèle)

Soit le collaborateur matricule `61` (*RASOANARIVO Havana Hideric*) sur un cycle annuel complet :

```
Mois 01 : Base=285 000 | Abs=7.56j | Congé=7.56j (Taux=8 602.88) | HS=4 339.20
          -> formule = 30.00 (l'absence est couverte à 100% par le congé payé)
          -> Droit acquis = 2.5000 j
          -> Base totale = (285 000 - 72 039.24 + 65 037.77) + 4 339.20 = 282 337.73 Ar
          -> R*S/30 = (282 337.73 * 2.5) / 30 = 23 528.14 Ar
          -> Cumul Droits = 2.5000 j | Cumul Valorisation = 23 528.14 Ar

Mois 02 : Base=300 000 | Abs=0.01j non couverte | Congé=0j | HS=12 060.21
          -> formule = 30 - 0.01 = 29.99
          -> Droit acquis = 2.5 * (29.99 / 30) = 2.499167 j
          -> Base totale = (300 000 - 121.16 + 0) + 12 060.21 = 311 939.05 Ar
          -> R*S/30 = (311 939.05 * 2.499167) / 30 = 25 986.26 Ar
          -> Cumul Droits = 4.9992 j | Cumul Valorisation = 49 514.40 Ar

... (Évolution mensuelle des augmentations de salaire à 350 000 Ar au mois 6, prises de congés en M8, M10, M11) ...

Mois 12 : Base=350 000 | Abs=0.41j | Congé=0.40j | Reste absence injustifiée = 0.01j
          -> formule = 30 - 0.01 = 29.99
          -> Droit acquis = 2.499167 j
          -> Cumul Annuel Droits (CD_12) = 29.9567 jours
          -> Cumul Annuel Valorisation (CV_12) = 324 385.38 Ar
```

### 2.3 Détermination du Taux Journalier de Congé (`TCONGE`) à Clôture

À l'issue des 12 mois de référence, le taux journalier officiel de congé payé pour le cycle suivant est déterminé par le ratio strict :

$$\mathbf{TCONGE} = \frac{\text{Cumul Annuel Valorisation Droit}}{\text{Cumul Annuel Nombre Jours Droit}} = \frac{324\,385{,}3763\text{ Ar}}{29{,}956667\text{ jours}} = \mathbf{10\,828{,}49\text{ Ar / jour}}$$

Ce taux journalier de **$10\,828{,}49\text{ Ar}$** devient la valeur unitaire légale de valorisation des congés posés par le salarié au cours du cycle suivant.

---

## 3. Architecture du Modèle de Données BDD

Le schéma de base de données canonique (`04_schema_conges.sql`) repose sur une conception sobre, sans tables artificielles ni surcharge de jointures.

```
                    ┌──────────────────────────┐
                    │         EMPLOYE          │
                    └─────────────┬────────────┘
                                  │ 1
                                  │
         ┌────────────────────────┼────────────────────────┐
         │ N                      │ N                      │ N
┌────────▼──────────────┐ ┌───────▼──────────────┐ ┌───────▼─────────────────────┐
│     DEMANDE_CONGE     │ │MOUVEMENT_SOLDE_CONGE │ │    CONGE_TAUX_APPLICABLE    │
│  (Cycle de vie & flux)│ │ (Grand-Livre Ledger) │ │ (Historique Taux & Cutover) │
└────────┬──────────────┘ └──────────────────────┘ └─────────────────────────────┘
         │ N
         ├────────────────────────┐
         │ 1                      │ 1 (optionnel)
┌────────▼──────────────┐ ┌───────▼──────────────────────────┐
│    REF_TYPE_CONGE     │ │REF_MOTIF_EVENEMENT_FAMILIAL      │
│  (Règles métier &     │ │(4 motifs légaux Art. 88)         │
│   conditions)         │ └──────────────────────────────────┘
└───────────────────────┘
```

### 3.1 Table `ref_type_conge` (Référentiel Typologique)

Définit le comportement fonctionnel et financier de chaque type de congé :

```sql
CREATE TABLE ref_type_conge (
    id_type_conge SERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,       -- ANNUEL, MALADIE, EXCEPTIONNEL, MATERNITE, etc.
    libelle VARCHAR(100) NOT NULL,
    est_paye BOOLEAN DEFAULT TRUE,          -- Maintien de salaire à la paie ?
    decompte_solde_acquis BOOLEAN DEFAULT TRUE, -- Débite le solde de congé annuel (Ledger) ?
    exige_anciennete_min_mois INT DEFAULT 0,    -- 12 mois pour ANNUEL (Art. 85)
    duree_fixe_jours NUMERIC(5,1),              -- 98j (Maternité), 3j (Paternité)
    plafond_jours_episode NUMERIC(5,1),         -- 30j (Maladie avec maintien)
    id_type_conge_bascule INT REFERENCES ref_type_conge(id_type_conge), -- Bascule après plafond
    soumis_delai_preavis BOOLEAN DEFAULT FALSE, -- Soumis aux délais de 2j / 15j ?
    suspend_acquisition_solde BOOLEAN DEFAULT FALSE, -- Absence suspensive de droits ?
    exige_justificatif BOOLEAN DEFAULT FALSE,   -- Certificat ou pièce requise ?
    est_actif BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 3.2 Table `ref_motif_evenement_familial` (Événements Familiaux Art. 88)

Conserve les 4 motifs légaux stricts et leurs quotas :

```sql
CREATE TABLE ref_motif_evenement_familial (
    id_motif SERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,       -- MARIAGE, NAISSANCE, DECES_CONJOINT_ENFANT, DECES_PARENT
    libelle VARCHAR(100) NOT NULL,
    nb_jours_autorises NUMERIC(4,1) NOT NULL,
    justificatif_requis BOOLEAN DEFAULT TRUE,
    est_actif BOOLEAN DEFAULT TRUE
);
```

### 3.3 Table `demande_conge` (Flux Opérationnel des Demandes)

Le statut est géré directement par un code textuel avec contrainte `CHECK`, éliminant la table `ref_statut_demande_conge` :

```sql
CREATE TABLE demande_conge (
    id_demande_conge SERIAL PRIMARY KEY,
    id_employe INT NOT NULL REFERENCES employe(id_employe),
    id_type_conge INT NOT NULL REFERENCES ref_type_conge(id_type_conge),
    id_motif_evenement_familial INT REFERENCES ref_motif_evenement_familial(id_motif),
    
    -- Statut direct sans table intermédiaire
    statut VARCHAR(20) NOT NULL DEFAULT 'EN_ATTENTE' 
        CHECK (statut IN ('BROUILLON', 'EN_ATTENTE', 'VALIDE', 'REFUSE', 'ANNULE')),

    date_debut DATE NOT NULL,
    date_fin DATE NOT NULL,
    date_demande DATE NOT NULL DEFAULT CURRENT_DATE,
    nb_jours_decomptes NUMERIC(5,1) NOT NULL,

    -- Dérogations accordées par la DAF/RH
    derogation_solde_negatif BOOLEAN DEFAULT FALSE,
    derogation_delai_preavis BOOLEAN DEFAULT FALSE,
    derogation_continuite_service BOOLEAN DEFAULT FALSE,
    extension_pathologique_maternite BOOLEAN DEFAULT FALSE,

    -- Demi-journée
    est_demi_journee BOOLEAN DEFAULT FALSE,
    moment_demi_journee VARCHAR(10) CHECK (moment_demi_journee IN ('MATIN', 'APRES_MIDI')),

    est_remunere BOOLEAN DEFAULT NULL,      -- Surcharge facultative du statut payé
    justificatif_fourni BOOLEAN DEFAULT FALSE,
    commentaire TEXT,

    saisi_par INT REFERENCES utilisateur(id_utilisateur),
    valide_par INT REFERENCES utilisateur(id_utilisateur),
    date_validation TIMESTAMP,
    motif_refus TEXT,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_demande_conge_dates CHECK (date_fin >= date_debut),
    CONSTRAINT chk_demande_conge_demi_journee CHECK (
        (est_demi_journee IS FALSE AND moment_demi_journee IS NULL) OR
        (est_demi_journee IS TRUE AND date_debut = date_fin AND moment_demi_journee IN ('MATIN', 'APRES_MIDI'))
    )
);
```

### 3.4 Table `mouvement_solde_conge` (Grand-Livre Comptable / Ledger)

Enregistre tous les flux de débit et de crédit. Le solde n'est jamais stocké en dur, il est calculé par agrégation.

```sql
CREATE TABLE mouvement_solde_conge (
    id_mouvement SERIAL PRIMARY KEY,
    id_employe INT NOT NULL REFERENCES employe(id_employe),
    type_mouvement VARCHAR(20) NOT NULL 
        CHECK (type_mouvement IN ('ACQUISITION','BONIFICATION','PRISE','AJUSTEMENT','ANNULATION','EXPIRATION')),
    nb_jours NUMERIC(5,2) NOT NULL,          -- Positif (crédit) ou Négatif (débit)
    id_demande_conge INT REFERENCES demande_conge(id_demande_conge) ON DELETE SET NULL,
    date_mouvement DATE NOT NULL DEFAULT CURRENT_DATE,
    commentaire TEXT,
    created_by INT REFERENCES utilisateur(id_utilisateur),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 3.5 Table `conge_taux_applicable` (Historique Financier du TCONGE)

Porte le taux journalier de congé pour la valorisation en paie et au STC :

```sql
CREATE TABLE conge_taux_applicable (
    id_taux_applicable SERIAL PRIMARY KEY,
    id_employe INT NOT NULL REFERENCES employe(id_employe),
    taux_journalier NUMERIC(12,2) NOT NULL,
    date_effet DATE NOT NULL,
    date_fin DATE,                           -- Convention d'intervalle semi-ouvert [date_effet, date_fin[
    source_provenance VARCHAR(50) NOT NULL 
        CHECK (source_provenance IN ('KAJY_MIGRATION', 'CALCUL_PERIODE_REFERENCE', 'INITIALISATION_BASE_30', 'REPRISE_CUTOVER')),
    commentaire TEXT,
    cree_par INT REFERENCES utilisateur(id_utilisateur),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_taux_positif CHECK (taux_journalier > 0),
    CONSTRAINT chk_dates_taux CHECK (date_fin IS NULL OR date_fin > date_effet)
);
```

### 3.6 Les Vues Utiles (Lecture Seule)

- **`v_solde_conge_employe`** : Agrège en temps réel le solde disponible (`solde_actuel`), le total acquis, pris, bonifié et expiré par collaborateur.
- **`v_demande_conge_cpl`** : Vue consolidée pour l'affichage des tableaux de bord RH (jointure avec `employe`, `ref_type_conge`, `ref_motif_evenement_familial` et `utilisateur`).

---

## 4. Règles de Gestion Métier (RG) Détaillées

### RG-CON-01 : Décompte Calendaire Strict
Tous les jours calendaires inclus entre `date_debut` et `date_fin` sont décomptés :
$$\text{nb\_jours\_decomptes} = (\text{date\_fin} - \text{date\_debut}) + 1$$
*Samedi, Dimanche et jours fériés intercalés sont comptabilisés*.  
*Exception demi-journée* : si `est_demi_journee = true`, alors $\text{date\_debut} = \text{date\_fin}$ et $\text{nb\_jours\_decomptes} = 0{,}5$.

### RG-CON-02 : Acquisition Mensuelle Légale & Idempotence
- Taux légal par défaut : **2,50 jours/mois** (Art. 85).
- Proratisation : en cas d'absences injustifiées ou non payées, l'acquisition est calculée selon la formule officielle :
  $$D_m = 2{,}5 \times \frac{30 - \text{absences}}{30}$$
- L'acquisition mensuelle est exécutée de façon **idempotente** : si le job tourne plusieurs fois pour le même mois, il met à jour le mouvement existant au lieu de créer un doublon.

### RG-CON-03 : Bonification d'Ancienneté
Au mois anniversaire d'embauche du salarié :
- Si Ancienneté $\ge 3$ ans (paramètre `SEUIL_ANCIENNETE_BONIFICATION_ANS`) :
  $$\text{Bonus} = \left\lfloor \frac{\text{Années d'ancienneté}}{5} \right\rfloor \times 1\text{ jour}$$
  *(5 ans : 1j ; 10 ans : 2j ; 15 ans : 3j ; etc.)*.
- Si le paramètre est désactivé dans `parametre_global`, aucun bonus n'est accordé.

### RG-CON-04 : Prescription Triennale & Purge FIFO (RG-CONGE-06)
- Les droits acquis se prescrivent après **36 mois (3 ans)** conformément à l'Art. 85.
- **Algorithme d'imputation FIFO** :
  1. *Priorité 1* : Les consommations s'imputent sur le stock historique repris le plus ancien.
  2. *Priorité 2* : Les consommations s'imputent chronologiquement sur les droits datés (`ACQUISITION`, `BONIFICATION`).
  3. À l'échéance des 36 mois, le reliquat non consommé d'un droit acquis génère un débit `EXPIRATION`.
  4. Plafond légal maximal théorique d'un solde : **90 jours**.

### RG-CON-05 : Anticipation de Solde & Dérogation RH
- Si le solde disponible est insuffisant ($S_{dispo} < J_{demandes}$), la demande est bloquée.
- La DAF/DRH peut autoriser une **prise par anticipation** en accordant une dérogation (`derogation_solde_negatif = true`).
- Plafond d'anticipation : fixé par défaut à **-10 jours** (`PLAFOND_SOLDE_CONGE_NEGATIF`).
- Si désactivé dans `parametre_global`, toute anticipation est interdite (plafond ramené à 0j).

### RG-CON-06 : Délais de Préavis Réglementaires
Pour les congés soumis à préavis (`soumis_delai_preavis = true`) :
- Demande courte ($\le 3$ jours) : **2 jours ouvrables** de préavis minimum.
- Demande longue ($> 3$ jours) : **15 jours calendaires** de préavis minimum.
- Les congés d'urgence (`MALADIE`, `MATERNITE`, `PATERNITE`, `EXCEPTIONNEL`) ne sont jamais soumis à préavis.

### RG-CON-07 : Pool Annuel des Événements Familiaux (10j/an)
- Conformément à l'Art. 88, l'ensemble des congés exceptionnels et paternité est limité à un cumul strict de **10 jours ouvrables par année civile**.
- Au-delà de 10 jours, toute journée supplémentaire est imputée sur le solde de congé annuel ou bascule en congé sans solde.

### RG-CON-08 : Maternité & Extension Pathologique
- Durée légale standard : **98 jours calendaires consécutifs** (14 semaines — Art. 97).
- Extension pour état pathologique : **+21 jours (3 semaines)**, portant le total à **119 jours**.
- Le congé maternité ne suspend pas l'acquisition du congé annuel payé (Art. 87).

### RG-CON-09 : Maladie Ordinaire & Maintien de Salaire
- Maintien intégral de la rémunération jusqu'à **30 jours continus** (Art. 13).
- Au-delà de 30 jours, la prise en charge bascule vers `MALADIE_SANS_SOLDE` (relais CNaPS).
- Suspension du contrat autorisée jusqu'à 6 mois (180 jours).

### RG-CON-10 : Fractionnement Annuel Obligatoire
- Au moins une tranche continue de **12 jours consécutifs** doit être posée dans l'exercice (Art. 89).
- Le système alerte automatiquement la DRH au 4e trimestre si l'obligation n'est pas remplie.

### RG-CON-11 : Annulation Post-Validation & Traçabilité
- Toute annulation d'un congé déjà validé exige un motif textuel obligatoire.
- Si le congé consommait le solde (`ANNUEL`), le système génère immédiatement un mouvement créditeur inverse `ANNULATION` qui restitue les jours au solde disponible.

### RG-CON-12 : Matrice des 12 Paramètres Configurables (`parametre_global`)

Le comportement de toutes les règles ci-dessus est dynamiquement piloté par la table `parametre_global` sous la catégorie `CONGE` :

| Clé du Paramètre | Type | Valeur Défaut | Comportement si Inactif (`est_actif = false`) |
| :--- | :---: | :---: | :--- |
| `PLAFOND_SOLDE_CONGE_NEGATIF` | Entier | `-10` | Plafond forcé à `0 jour` : **toute anticipation de congé devient strictement impossible**. |
| `SEUIL_ANCIENNETE_BONIFICATION_ANS` | Entier | `3` | Neutralise la bonification : bonus forcé à `0 jour` pour tous les collaborateurs. |
| `PALIER_ANNEES_BONIFICATION` | Entier | `5` | Fallback sur la valeur légale standard de 5 ans. |
| `JOURS_BONIFICATION_PAR_PALIER` | Entier | `1` | Aucun jour accordé par palier d'ancienneté. |
| `DIVISEUR_JOURS_CALENDAIRE_CONGE` | Décimal | `30.0` | Fallback sur 30,0 jours (base trentième légale malagasy). |
| `DELAI_PREAVIS_COURT_JOURS` | Entier | `2` | Aucun préavis minimum exigé pour les demandes courtes. |
| `DELAI_PREAVIS_LONG_JOURS` | Entier | `15` | Aucun préavis minimum exigé pour les demandes longues. |
| `SEUIL_JOURS_CONGE_COURT` | Entier | `3` | Seuil délimitant une demande courte d'une demande longue. |
| `SEUIL_SOLDE_CONGE_FAIBLE` | Entier | `5` | Désactive la génération de l'alerte préventive de solde faible. |
| `SEUIL_PRESCRIPTION_CONGE_MOIS` | Entier | `36` | Désactive la purge automatique triennale. |
| `PLAFOND_POOL_EVENEMENT_FAMILIAL_JOURS` | Décimal | `10.0` | Plafond annuel partagé pour les permissions familiales (Art. 88). |
| `DUREE_TRANCHE_MIN_FRACTIONNEMENT_JOURS` | Entier | `12` | Seuil de contrôle de la tranche continue annuelle obligatoire (Art. 89). |

---

## 5. Workflow & Circuit de Validation

```
     [Employé / RH]
           │
           ▼
     Saisie Demande ───(Vérification Préavis, Conflits de Poste & Solde)
           │
           ├────────────────────────────┐
           ▼                            ▼
      [EN_ATTENTE]                [BROUILLON]
           │
     [Validation DAF/DRH]
           │
     ┌─────┴──────────────────────┐
     ▼                            ▼
[VALIDE]                       [REFUSE]
     │ (Génération PRISE            │ (Motif obligatoire)
     │  au Ledger si ANNUEL)        ▼
     │                         [Notification RH]
     │
     ▼ (Si annulation ultérieure)
[ANNULE]
     │ (Génération ANNULATION = Recrédit au Ledger)
     ▼
[Notification RH & Clôture]
```

### 5.3 Sécurité, Concurrence & Verrous Pessimistes (`FOR UPDATE`)

Pour éviter les anomalies de concurrence (deux managers validant au même instant deux demandes distinctes pour le même collaborateur, provoquant un dépassement de solde non autorisé) :
1. **Verrouillage pessimiste de la demande** :
   ```sql
   SELECT dc.id_statut_demande_conge, dc.statut FROM demande_conge dc 
   WHERE dc.id_demande_conge = ? FOR UPDATE;
   ```
2. **Verrouillage pessimiste de l'employé** :
   ```sql
   SELECT id_employe FROM employe WHERE id_employe = ? FOR UPDATE;
   ```
3. **Idempotence stricte** : Tout mouvement généré est vérifié avant insertion pour interdire les doublons de décompte.

### 5.4 Matrice des Habilitations RBAC (Sécurité Spring Security)

| Rôle / Permission | Consultation Soldes & Planning | Saisie Demande Personnelle | Saisie pour Tiers (RH) | Validation / Refus / Dérogation | Purge & Configuration |
| :--- | :---: | :---: | :---: | :---: | :---: |
| `ROLE_USER` / Collaborateur | ✅ (Propre solde) | ✅ | ❌ | ❌ | ❌ |
| `CONGE_READ` | ✅ (Tous collaborateurs) | ✅ | ❌ | ❌ | ❌ |
| `CONGE_WRITE` | ✅ | ✅ | ✅ | ❌ | ❌ |
| `CONGE_VALIDATE` (DRH, DAF, Manager) | ✅ | ✅ | ✅ | ✅ | ❌ |
| `ROLE_ADMIN` | ✅ | ✅ | ✅ | ✅ | ✅ (Types, Purge, Ledger) |

---

## 6. Automatisations & Schedulers Nocturnes

L'ordonnancement nocturne garantit l'intégrité des droits sans intervention humaine :

| Heure | Scheduler | Description & Rôle |
| :--- | :--- | :--- |
| **00:05** | `NotificationRhScheduler` | Détection et émission automatique des alertes RH de supervision. |
| **01:00** | `AcquisitionCongeScheduler` | Exécution de l'acquisition mensuelle (+2,5j proratisé) pour les salariés dont c'est le jour anniversaire d'embauche, et attribution de la bonification d'ancienneté. |
| **02:00** | `PurgeCongeScheduler` | Application de la prescription légale triennale (purge FIFO des droits vieux de plus de 36 mois). |

### 6.1 Les 9 Alertes Émises par le Système (`NotificationRhScheduler`)

| Code Alerte | Priorité | Déclencheur & Règle d'Activation | Destinataire & Impact |
| :--- | :---: | :--- | :--- |
| `CONGE_EN_ATTENTE` | 🟠 HAUTE | Demande restée au statut `EN_ATTENTE` depuis plus de 48 heures. | Notifie le valideur pour éviter les goulots d'étranglement. |
| `SOLDE_CONGE_FAIBLE` | 🔵 BASSE | Solde disponible $< 5$ jours (`SEUIL_SOLDE_CONGE_FAIBLE`). | Information préventive pour le collaborateur et le manager. |
| `SOLDE_CONGE_NEGATIF` | 🟠 HAUTE | Dérogation pour solde négatif accordée lors de la validation. | Alerte d'audit financier adressée à la DAF. |
| `RETOUR_CONGE_J` | 🔵 BASSE | `date_fin = CURRENT_DATE` pour une demande validée. | Rappel automatique au manager signalant le retour du collaborateur. |
| `FRACTIONNEMENT_NON_RESPECTE` | 🟠 HAUTE | En novembre-décembre : aucune tranche continue $\ge 12$ jours posée dans l'année (Art. 89). | Alerte légale annuelle pour régularisation avant fin d'exercice. |
| `MALADIE_PLAFOND_EPISODE_ATTEINT` | 🔴 CRITIQUE| Arrêt maladie continu atteignant 30 jours (Art. 13). | Déclenche la bascule vers `MALADIE_SANS_SOLDE` et le relais CNaPS. |
| `CONGE_EXPIRATION_PROCHE` | 🟠 HAUTE | Jours acquis il y a 35 mois (à J-30 de la prescription triennale de 36 mois). | Invite le salarié à solder ses droits avant perte définitive. |
| `POOL_EXCEPTIONNEL_EPUISE` | 🟠 HAUTE | Cumul annuel d'événements familiaux $= 10$ jours (Art. 88). | Bloque toute nouvelle permission familiale payée sans dérogation. |
| `BONIFICATION_ANCIENNETE_APPLIQUEE`| 🔵 BASSE | Mois anniversaire d'embauche avec ancienneté $\ge 3$ ans. | Notification d'information du bonus de congés crédité (+1j par palier de 5 ans). |

---

## 7. Valorisation Financière & Articulation avec les Autres Modules

### 7.1 Articulation avec la Paie
1. **Valorisation des congés pris** :
   $$\text{Montant Congé} = \text{Jours Congé Pris} \times \text{TCONGE}$$
2. **Retenue pour absence injustifiée** :
   $$\text{Taux Horaire Absence} = \frac{\text{Salaire de Base}}{173{,}33\text{ h}}$$
   $$\text{Retenue Absence} = \text{Heures Absence} \times \text{Taux Horaire}$$
3. **Maintien de salaire** : Pour les congés payés, le salaire net de base intègre le maintien sans pénalisation des droits acquis futurs.

### 7.2 Articulation avec le Solde de Tout Compte (STC)
Lors du départ du salarié :
- **Si Solde $> 0$** : Indemnité compensatrice de congés payés versée au salarié :
  $$\text{Indemnité STC} = \text{Solde Disponible} \times \text{TCONGE}$$
- **Si Solde $< 0$ (Anticipation non compensée)** : Retenue sur le STC :
  $$\text{Retenue STC} = |\text{Solde Négatif}| \times \text{TCONGE}$$

### 7.3 Passerelle avec le Module Pointage & Gestion du Temps (Présence)
Dès qu'une demande de congé passe au statut `VALIDE` :
- Les journées calendaires correspondantes sont automatiquement renseignées dans la grille de pointage avec le statut d'activité `CONGE`.
- Lors de la clôture des pointages mensuels, le salarié est identifié en absence autorisée et rémunérée, **neutralisant toute qualification erronée en absence injustifiée**.
- Les plannings d'équipe et feuilles de temps reflètent en temps réel la disponibilité opérationnelle du personnel.

---

## 8. Synthèse des Éléments Supprimés & Simplifiés (Refactorisation)

Dans le cadre de l'alignement sur la **Source Unique de Vérité** :

1. **Suppression de `ref_statut_demande_conge`** :  
   Remplacé par la colonne native `statut VARCHAR(20) CHECK (statut IN ('BROUILLON', 'EN_ATTENTE', 'VALIDE', 'REFUSE', 'ANNULE'))` sur `demande_conge`. Élimine une table de 5 lignes et des dizaines de jointures inutiles.

2. **Suppression de `ref_pool_conge_annuel` & `v_pool_conge_annuel_consommation`** :  
   Le quota de 10 jours de permissions exceptionnelles (Art. 88) est géré comme règle globale d'entreprise (`PLAFOND_PERMISSIONS_FAMILIALES = 10`), supprimant 1 table, 1 vue SQL et 2 classes Java orphelines.

3. **Unification Backend (De 4 contrôleurs à 1 contrôleur pilote)** :  
   Regroupement des routes sous `CongeController.java` et centralisation de la logique métier sous `CongeService.java`.

4. **Clarification Frontend** :  
   Séparation nette entre le module opérationnel quotidien (`features/leave`) et les écrans de paramétrage DRH déplacés dans `features/administration`.

---

## 9. Recueil Textuel Intégral des Articles Légaux (Loi n° 2003-044)

Pour garantir une conformité juridique inattaquable et servir de référence légale directe sans consultation externe, voici la transcription des textes applicables :

### Article 85 — Droit au congé annuel, acquisition et prescription
> *« Sauf dispositions plus favorables des conventions collectives ou du contrat individuel de travail, le travailleur acquiert droit au congé payé à la charge de l'employeur, à raison de **deux jours et demi calendaires par mois de service effectif**, soit trente jours calendaires par an.*  
> *Le droit de jouissance au congé est acquis après une période minimale de **service effectif égale à douze mois**.*  
> *Les droits à congé se prescrivent par **trois ans** à compter du jour où la période de référence a pris fin. »*

### Article 86 — Bonification pour ancienneté & Salariées mères
> *« La durée du congé fixé à l'article 85 ci-dessus est augmentée à raison de jours de congé supplémentaires par période entière, continue ou non, de service chez le même employeur, selon les stipulations de la convention collective ou du règlement intérieur.*  
> *Les femmes salariées ou apprenties bénéficient d'un congé supplémentaire par enfant à charge dans les conditions prévues par décret. »*

### Article 87 — Assimilation des périodes d'absence à du travail effectif
> *« Sont assimilées à un mois de service effectif pour le calcul de la durée du congé :*  
> *1° Les périodes de congés payés antérieurs ;*  
> *2° Les périodes de repos de la femme en couches prévues aux articles 97 et 98 ci-dessous ;*  
> *3° Les périodes de suspension du contrat de travail pour cause d'accident du travail ou de maladie professionnelle pendant une durée ininterrompue maximale d'un an ;*  
> *4° Dans la limite de six mois, les absences pour maladie dûment constatée par un médecin agréé ;*  
> *5° Les absences pour permissions exceptionnelles accordées à l'occasion d'événements familiaux prévues à l'article 88. »*

### Article 88 — Permissions exceptionnelles pour événements de famille
> *« Des permissions exceptionnelles pour événements de famille touchant directement le foyer du travailleur sont accordées par l'employeur dans la limite de **dix jours par an**.*  
> *Ces permissions ne peuvent être déduites de la durée du congé annuel si elles ont été payées.*  
> *Le travailleur doit produire les pièces justificatives dans un délai de six mois suivant l'événement. À défaut, le montant correspondant aux jours d'absence peut faire l'objet d'une retenue sur salaire. »*

### Article 89 — Fractionnement et tranche minimale
> *« Le congé peut être fractionné d'accord parties. Toutefois, l'une des fractions doit être d'une durée continue d'au moins **douze jours calendaires consécutifs**.*  
> *Pour les travailleurs engagés hors du lieu d'emploi, la durée du congé est augmentée des délais de route légaux. »*

### Article 90 — Allocation et indemnité compensatrice de congé
> *« L'employeur doit verser au travailleur, pendant toute la durée du congé, une allocation au moins égale à la moyenne des salaires, indemnités et primes diverses perçus par le travailleur au cours des douze mois ayant précédé la date de départ en congé.*  
> *Cette allocation ne peut en aucun cas être inférieure au montant de la rémunération que le travailleur aurait perçue s'il avait continué à travailler pendant la période de congé.*  
> *En cas de rupture ou d'expiration du contrat avant que le travailleur ait acquis droit au congé, une indemnité compensatrice calculée sur la base des droits acquis lui est accordée. »*

### Article 13 — Suspension du contrat pour maladie non professionnelle
> *« Le contrat est suspendu pendant la durée de l'absence du travailleur en cas de maladie dûment constatée par un médecin agréé, durée limitée à **six mois**.*  
> *Pendant cette période de suspension, l'employeur est tenu de verser au travailleur une indemnité égale au montant de sa rémunération, dans la limite du **délai de préavis légal** [30 jours pour les employés mensuels]. Au-delà de cette durée, le contrat peut être résilié à l'initiative de l'une des parties. »*

### Articles 97 et 98 — Congé de Maternité
> *« Toute femme salariée a le droit de suspendre son travail pendant **quatorze semaines consécutives**, dont au moins six semaines postérieures à la délivrance.*  
> *Cette période peut être prolongée de **trois semaines** en cas de maladie dûment constatée résultant de la grossesse ou des couches.*  
> *Pendant cette période, l'employeur ne peut lui donner congé. Elle a droit aux soins médicaux gratuits et au maintien de sa rémunération dans les conditions prévues par la législation de prévoyance sociale (CNaPS). »*

---

## 10. Données d'Audit Intégrales du Modèle Excel Réel

Pour garantir une traçabilité et une réconciliation comptable parfaite avec les services financiers de l'entreprise, voici la reproduction intégrale, cellule par cellule, du tableau issu de `OHATRA KAJY DROITS CONGES-given on sept26.xls` :

### Collaborateur : Matricule 61 — RASOANARIVO Havana Hideric

| M | formule | BASE | NBABS | ABSENCES | NBCONGE | TCONGE | CONGE | NET BASE | HS | AUTIMP | BRUTE | base totale | nbre droit | R*S/30 | Cumul Droits | Cumul Valorisation |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **1** | 30.00 | 285 000 | 7.56 | 72 039.24 | 7.56 | 8 602.88 | 65 037.77 | 277 998.53 | 4 339.20 | 217 790 | 500 127.73 | 282 337.73 | **2.5000** | 23 528.14 | 2.5000 | 23 528.14 |
| **2** | 29.99 | 300 000 | 0.01 | 121.16 | — | — | 0.00 | 299 878.84 | 12 060.21 | 151 488 | 463 427.05 | 311 939.05 | **2.4992** | 25 986.26 | 4.9992 | 49 514.40 |
| **3** | 29.92 | 300 000 | 2.08 | 21 159.64 | 2.00 | 8 328.95 | 16 657.90 | 295 498.26 | 0.00 | 123 060 | 418 558.26 | 295 498.26 | **2.4933** | 24 559.19 | 7.4925 | 74 073.59 |
| **4** | 30.00 | 300 000 | 0.00 | 10 000.00 | 0.00 | 8 328.95 | 0.00 | 290 000.00 | 562.51 | 191 872 | 482 434.51 | 290 562.51 | **2.5000** | 24 213.54 | 9.9925 | 98 287.13 |
| **5** | 30.00 | 300 000 | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 300 000.00 | 10 760.38 | 173 600 | 484 360.38 | 310 760.38 | **2.5000** | 25 896.70 | 12.4925 | 124 183.83 |
| **6** | 29.62 | 350 000 | 0.38 | 6 057.81 | — | — | 0.00 | 343 942.19 | 2 336.30 | 205 105 | 551 383.49 | 346 278.49 | **2.4683** | 28 491.02 | 14.9608 | 152 674.85 |
| **7** | 30.00 | 350 000 | 0.00 | 0.00 | — | — | 0.00 | 350 000.00 | 1 627.53 | 176 042 | 527 669.53 | 351 627.53 | **2.5000** | 29 302.29 | 17.4608 | 181 977.15 |
| **8** | 30.00 | 350 000 | 2.00 | 23 333.34 | 2.00 | 8 669.52 | 17 339.04 | 344 005.70 | 4 410.09 | 169 415 | 517 830.79 | 348 415.79 | **2.5000** | 29 034.65 | 19.9608 | 211 011.80 |
| **9** | 29.96 | 350 000 | 0.04 | 565.40 | — | — | 0.00 | 349 434.60 | 0.00 | 178 165 | 527 599.60 | 349 434.60 | **2.4967** | 29 080.72 | 22.4575 | 240 092.52 |
| **10** | 30.00 | 350 000 | 0.50 | 5 853.53 | 0.50 | 8 669.52 | 4 334.76 | 348 481.23 | 4 715.00 | 170 415 | 523 611.23 | 353 196.23 | **2.5000** | 29 433.02 | 24.9575 | 269 525.54 |
| **11** | 30.00 | 350 000 | 15.00 | 175 101.01 | 15.00 | 8 669.52 | 130 042.80 | 304 941.79 | 26.25 | 96 375 | 401 343.04 | 304 968.04 | **2.5000** | 25 414.00 | 27.4575 | 294 939.54 |
| **12** | 29.99 | 350 000 | 0.41 | — | 0.40 | 8 669.52 | 3 467.81 | 353 467.81 | 0.00 | 136 403 | 489 870.67 | 353 467.81 | **2.4992** | 29 445.83 | **29.9567** | **324 385.38** |

### Résultat Final à la Clôture de l'Exercice
- **Cumul Annuel des Droits Acquis** : **$29{,}9567\text{ jours}$** (proche de 30 jours, avec déduction exacte des micro-absences injustifiées en M2, M3, M6, M9, M12).
- **Cumul Annuel de Valorisation** : **$324\,385{,}38\text{ Ar}$**.
- **Taux Journalier Officiel (`TCONGE`) pour l'exercice suivant** :
  $$\mathbf{TCONGE} = \frac{324\,385{,}3763}{29{,}956667} = \mathbf{10\,828{,}49\text{ Ar / jour}}$$

---

## 11. Cartographie Complète des Composants Logiciels

### 11.1 Cartographie Frontend (`frontend/src/features/leave/`)

| Composant / Fichier | Emplacement | Responsabilité Métier | Action de Refactorisation |
| :--- | :--- | :--- | :--- |
| `GestionCongesPage.jsx` | `features/leave/pages/` | Page conteneur principale avec onglets Soldes et Demandes. | Conservé comme page d'accueil unique du module. |
| `SoldesOverviewTab.jsx` | `features/leave/components/` | Vue tabulaire et planning prévisionnel des collaborateurs. | Simplifié, inclusion de la table et du widget. |
| `SoldesTable.jsx` | `features/leave/components/soldes/` | Tableau paginé des soldes et compteurs de congés. | Déplacé dans `components/tabs/SoldesTab.jsx`. |
| `SoldesGantt.jsx` | `features/leave/components/soldes/` | Vue timeline graphique du planning d'absence. | Allégé pour éviter le surcoût de 700 lignes SVG. |
| `DemandesValidationTab.jsx` | `features/leave/components/` | Vue de supervision et validation des demandes en attente. | Conservé comme onglet d'approbation. |
| `ValidationTable.jsx` | `features/leave/components/validation/` | Tableau des demandes avec badges de statut. | Intégré dans l'onglet de validation. |
| `ValidationModals.jsx` | `features/leave/components/validation/` | Regroupe 4 modals (Validation, Rejet, Annulation, Détail). | Découpé en 1 modal d'action unique paramétrable. |
| `DemandeCongeModal.jsx` | `features/leave/components/` | Modale de saisie de demande avec détection de conflits. | Conservé comme formulaire unique. |
| `ConflitsPostePreview.jsx` | `features/leave/components/formulaire/` | Détection des chevauchements d'absence d'une même équipe. | Conservé et optimisé. |
| `DatesCongeSection.jsx` | `features/leave/components/formulaire/` | Sélecteur de dates, demi-journées et simulation de décompte. | Conservé dans le formulaire. |
| `InitialisationSoldeModal.jsx` | `features/leave/components/` | Reprise d'historique et saisie du solde initial d'embauche. | Simplifié pour ne gérer que le solde et taux initial. |
| `SoldeCongeWidget.jsx` | `features/leave/components/` | Carte résumé du solde (acquis, pris, disponible, valeur). | Rendu réutilisable (pour `CongesTab.jsx` salarié). |
| `MouvementsSoldeTable.jsx` | `features/leave/components/` | Grand-livre comptable des mouvements du salarié. | Conservé pour la transparence d'audit. |
| `ParametresCongesTab.jsx` | `features/leave/components/` | Écran de gestion des types et motifs familiaux. | **Déplacé dans `features/administration/`**. |
| `congeService.js` | `features/leave/api/` | Client Axios appelant les endpoints `/api/conges/*`. | Nettoyé (suppression des alias redondants). |
| `congeBusinessRules.js` | `features/leave/utils/` | Calculs de préavis et durée fixe côté navigateur. | Conservé pour les vérifications UX temps réel. |

### 11.2 Cartographie Backend (`mg.galaxam.sirh.conge.*`)

| Classe Java | Package | Responsabilité Actuelle | Cible après Refactorisation |
| :--- | :--- | :--- | :--- |
| `DemandeCongeController.java` | `api` | Endpoints demandes, simulation, validation, rejet, annulation. | Fusionné dans `CongeController.java`. |
| `CongeSoldeController.java` | `api` | Endpoints soldes, mouvements, planning, purge. | Fusionné dans `CongeController.java`. |
| `CongeRefController.java` | `api` | Endpoints types de congés, motifs, statuts. | Fusionné dans `CongeController.java`. |
| `CongePeriodeReferenceController.java` | `api` | Endpoints cutover et clôture de période. | Fusionné dans `CongeController.java`. |
| `CongeService.java` | `application` | Service métier (décompte, planning, ledger, validation). | **Pilote Unique** orchestrant toutes les règles. |
| `CongeTauxApplicableService.java` | `application` | Calcul et résolution du TCONGE. | Simplifié et rattaché à `CongeService`. |
| `DemandeConge.java` | `persistence` | Entité ActiveRecord (1 082 lignes de SQL et logique). | Allégé : redevient une entité de données pure. |
| `MouvementSoldeConge.java` | `persistence` | Entité du grand-livre (mouvements comptables). | Conservé (pivot du ledger). |
| `RefTypeConge.java` | `persistence` | Référentiel des types de congés. | Conservé. |
| `RefMotifEvenementFamilial.java` | `persistence` | Motifs légaux d'événements familiaux. | Conservé. |
| `RefStatutDemandeConge.java` | `persistence` | Référentiel des 5 statuts. | **Supprimé** (statut en VARCHAR direct). |
| `RefPoolCongeAnnuel.java` | `persistence` | Table du pool 10j. | **Supprimé** (règle globale). |
| `VPoolCongeAnnuelConsommation.java`| `persistence` | Vue SQL de consommation pool. | **Supprimé**. |
| `VSoldeCongeEmploye.java` | `persistence` | Vue agrégée du solde employé. | Conservé. |
| `VDemandeCongeCpl.java` | `persistence` | Vue enrichie pour les tableaux de bord RH. | Conservé (jointures allégées). |
| `PurgeFifoEngine.java` | `domain` | Moteur mathématique d'imputation FIFO. | Conservé comme composant domaine pur. |
| `CongeCalculateurEngine.java` | `domain` | Calculateur calendaire pur (jours décomptés, trentième). | Conservé comme composant domaine pur. |
| `ReferencePeriodPolicy.java` | `domain` | Calcul d'une date de fin sur 12 mois. | **Supprimé** (intégré en méthode utilitaire). |
| Micro-Enums / DTOs (10 fichiers) | `domain` & `api/dto` | `TcongeApplicableRateStatus`, `TcongeReferenceStatus`, etc. | Regroupés dans un fichier de contrats `CongeDTOs.java`. |

---

## 12. Cas Limites et Gestion des Exceptions Métier (Edge Cases)

| Cas Limite / Situation Particulière | Traitement Métier Garanti | Règle Technique & Implémentation |
| :--- | :--- | :--- |
| **Embauche en cours de mois** | Le collaborateur acquiert un droit proratisé selon le nombre de jours calendaires restant dans le mois d'embauche divisé par 30. | Calcul automatique par `calculerAcquisitionMensuelleProratisee`. |
| **Départ du salarié avant 12 mois d'ancienneté** | Le salarié n'a pas encore le droit de jouissance, mais ses droits acquis sont payés sous forme d'**indemnité compensatrice de congé** au STC. | $\text{Indemnité} = \text{Solde Acquis} \times \text{TCONGE}$. Aucune perte de droit pour le collaborateur. |
| **Départ avec solde négatif (Anticipation non compensée)** | Les jours pris en avance et non compensés par des acquisitions sont **déduits du salaire net au STC**. | $\text{Retenue STC} = \|\text{Solde Négatif}\| \times \text{TCONGE}$. |
| **Arrêt maladie dépassant 30 jours continus** | Les 30 premiers jours sont maintenus à 100% par l'employeur. Dès le 31e jour, la demande bascule en `MALADIE_SANS_SOLDE` sans rémunération employeur (relais indemnités CNaPS). | Détection automatique par `DemandeConge.controler` et alerte critique `MALADIE_PLAFOND_EPISODE_ATTEINT`. |
| **Congé Maternité avec complications médicales** | Prolongation de 3 semaines (21 jours) accordée sur production d'un certificat médical, portant la durée légale de 98 à 119 jours. | Case à cocher `extensionPathologiqueMaternite` déverrouillant le plafond à 119 jours. |
| **Chevauchement d'absence d'une même équipe** | Le système n'interdit pas techniquement la pose mais signale en rouge les collègues du même département ou poste absents sur la même période pour préserver la continuité de service. | Endpoint `/demandes/verifier-conflits` et composant `ConflitsPostePreview.jsx`. |
| **Annulation tardive d'un congé déjà consommé** | Si un congé validé dans le passé est annulé, le solde est recrédité immédiatement, mais la paie du mois concerné fera l'objet d'une régularisation au mois $M+1$. | Mouvement `ANNULATION` tracé dans le ledger avec horodatage et identifiant du valideur. |

---

*Document officiel GALAXAM SIRH — Conforme Code du Travail de Madagascar 2026.*

