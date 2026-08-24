# Module Congés & Absences — Documentation Technique & Fonctionnelle
## GALAXAM SIRH — Mémoire de Fin d'Études

> **Auteur** : Équipe de Développement GALAXAM SIRH  
> **Module** : Gestion des Congés, Absences & Soldes  
> **Version** : 2.0.0 (Spécification Complète Officielle)  
> **Date** : 2026
>
> **Périmètre implémenté** : Acquisition, décompte calendaire, typologies de congés, bonification d'ancienneté, prescription triennale FIFO, dérogations, alertes automatiques.  
> **Hors périmètre (reporté)** : Pauses d'allaitement, co-financement CNaPS/IJM maternité (reporté au module Paie).

---

## Table des Matières

1. [Cadre Légal Malgache](#1-cadre-légal-malgache)
2. [Modèle de Données](#2-modèle-de-données)
3. [Règles de Gestion (RG)](#3-règles-de-gestion)
4. [Typologies de Congés](#4-typologies-de-congés)
5. [Workflow & Circuit de Validation](#5-workflow--circuit-de-validation)
6. [Automatisations & Schedulers](#6-automatisations--schedulers)
7. [Système de Notifications — Alertes Congés](#7-système-de-notifications--alertes-congés)
8. [Valorisation Financière](#8-valorisation-financière)
9. [Architecture Technique](#9-architecture-technique)
10. [API REST — Endpoints](#10-api-rest--endpoints)

---

## 1. Cadre Légal Malgache

### 1.1 Textes Fondamentaux

La gestion des congés et absences dans le SIRH GALAXAM est conçue en stricte conformité avec le droit du travail malgache :

| Texte Légal | Articles Clés | Impacts sur le SIRH |
|---|---|---|
| **Loi n° 2003-044 du 28 juillet 2004** (Code du Travail) | Art. 13, 85, 86, 87, 88, 90, 95, 97, 98 | Règles d'acquisition, décompte, plafonds, suspensions |
| **Décret n° 2007-563** | Durée du travail : 40h/semaine = 173,33h/mois | Valorisation horaire des retenues pour absences |
| **Code de Prévoyance Sociale / CNaPS** | Prestations familiales & indemnités journalières | Prise en charge maternité et maladie longue durée |

### 1.2 Principes Fondamentaux

1. **Droit d'ordre public** : Toute disposition inférieure aux minima légaux est nulle.
2. **Condition d'ancienneté** : Le droit de *jouissance* du congé annuel requiert **12 mois continus** de service (Art. 85). L'*acquisition* commence dès le 1er mois.
3. **Décompte calendaire strict** : Tous les jours (ouvrables + week-ends + fériés) sont comptés.
4. **Prescription triennale** : Les droits à congés se prescrivent par **3 ans** (Art. 85). Plafond absolu : 90 jours (3 ans × 30 jours/an).
5. **Fractionnement obligatoire** : Au moins une tranche continue de **12 jours** doit être posée dans l'exercice (Art. 85).

---

## 2. Modèle de Données

### 2.1 Vue d'Ensemble

```
EMPLOYE (1)
    │
    ├── (N) DEMANDE_CONGE
    │           id_employe
    │           id_type_conge → ref_type_conge
    │           id_statut_demande → ref_statut_demande_conge
    │           id_motif_evenement_familial → ref_motif_evenement_familial
    │           date_debut / date_fin / nb_jours_decomptes
    │           derogation_solde_negatif / derogation_delai_preavis
    │
    └── (N) MOUVEMENT_SOLDE_CONGE
                id_employe
                type_mouvement  -- ACQUISITION, PRISE, ANNULATION, EXPIRATION, BONIFICATION
                nb_jours        -- positif (crédit) ou négatif (débit)
                id_demande_conge → demande_conge (pour traçabilité PRISE)
                date_mouvement
                commentaire

VUES (Agrégats en lecture seule)
    ├── v_solde_conge_employe    -- Solde actuel calculé par SUM(nb_jours)
    ├── v_demande_conge_cpl      -- Demandes enrichies avec libellés
    └── v_pool_conge_annuel_consommation  -- Consommation du pool exceptionnel (Art.88)
```

### 2.2 Table `ref_type_conge`

Référentiel des types de congés avec toutes leurs propriétés comportementales :

```sql
Table ref_type_conge {
  id_type_conge                INTEGER PK
  code                         VARCHAR(20) UNIQUE  -- ANNUEL, MALADIE, MATERNITE...
  libelle                      VARCHAR(100)
  
  -- Comportements financiers
  est_paye                     BOOLEAN  -- Maintien de salaire ?
  decompte_solde_acquis        BOOLEAN  -- Entame le solde CP ?
  
  -- Contraintes
  exige_anciennete_min_mois    INTEGER  -- NULL = pas de condition
  exige_justificatif           BOOLEAN  -- Certificat médical requis ?
  soumis_delai_preavis         BOOLEAN  -- Préavis obligatoire ?
  id_pool_conge_annuel         INTEGER FK → ref_pool_conge_annuel  -- NULL = pas de pool
  
  -- Plafonds
  duree_max_jours              INTEGER  -- NULL = pas de plafond
}
```

### 2.3 Table `demande_conge`

```sql
Table demande_conge {
  id_demande_conge             INTEGER PK
  id_employe                   INTEGER FK → employe
  id_type_conge                INTEGER FK → ref_type_conge
  id_statut_demande            INTEGER FK → ref_statut_demande_conge
  id_motif_evenement_familial  INTEGER FK → ref_motif_evenement_familial  -- Pour EXCEPTIONNEL

  date_debut                   DATE NOT NULL
  date_fin                     DATE NOT NULL
  date_demande                 DATE  -- AUTO = date de saisie
  
  nb_jours_decomptes           DECIMAL(6,2)  -- Calculé automatiquement
  
  -- Dérogations RH
  derogation_solde_negatif     BOOLEAN DEFAULT false
  derogation_delai_preavis     BOOLEAN DEFAULT false
  extension_pathologique_maternite BOOLEAN DEFAULT false
  
  -- Demi-journée (0,5 j)
  est_demi_journee             BOOLEAN DEFAULT false
  moment_demi_journee          VARCHAR(10) -- MATIN ou APRES_MIDI
  
  -- Justificatif
  justificatif_fourni          BOOLEAN DEFAULT false
  commentaire                  TEXT
  
  -- Validation
  saisie_par                   INTEGER FK → utilisateur
  valide_par                   INTEGER FK → utilisateur
  date_validation              TIMESTAMP
  motif_refus                  TEXT
  
  created_at                   TIMESTAMP
  updated_at                   TIMESTAMP
}
```

**Cycle de vie du statut de demande** :
```
BROUILLON → EN_ATTENTE → VALIDE
                       → REFUSE
            ANNULE (depuis EN_ATTENTE ou VALIDE)
```

### 2.4 Table `mouvement_solde_conge`

C'est le **journal comptable** des droits à congés. Le solde actuel n'est jamais stocké directement — il est toujours **recalculé** par `SUM(nb_jours)` sur l'ensemble des mouvements de l'employé.

```sql
Table mouvement_solde_conge {
  id_mouvement     INTEGER PK
  id_employe       INTEGER FK → employe
  type_mouvement   VARCHAR(20)  -- ACQUISITION, PRISE, ANNULATION, EXPIRATION, BONIFICATION
  nb_jours         DECIMAL(6,2) -- Positif = crédit ; Négatif = débit
  date_mouvement   DATE
  id_demande_conge INTEGER FK → demande_conge  -- Traçabilité (NULL pour acquisitions)
  commentaire      TEXT
  created_by       INTEGER FK → utilisateur
}
```

**Types de mouvements** :
| Type | Signe | Source | Description |
|---|---|---|---|
| `ACQUISITION` | ➕ +2,5 | Scheduler mensuel | Droit mensuel légal |
| `BONIFICATION` | ➕ Variable | Scheduler mensuel | Jours ancienneté (≥ 3 ans) |
| `PRISE` | ➖ Négatif | Validation demande | Débit lors de la validation d'un congé annuel |
| `ANNULATION` | ➕ Positif | Annulation demande | Recréditement lors de l'annulation d'un congé validé |
| `EXPIRATION` | ➖ Négatif | Scheduler mensuel | Purge triennale FIFO |

### 2.5 Vue `v_solde_conge_employe`

```sql
CREATE VIEW v_solde_conge_employe AS
SELECT 
    id_employe,
    SUM(nb_jours) AS solde_actuel,
    SUM(nb_jours) FILTER (WHERE type_mouvement = 'ACQUISITION') AS total_acquis,
    SUM(-nb_jours) FILTER (WHERE type_mouvement = 'PRISE') AS total_pris
FROM mouvement_solde_conge
GROUP BY id_employe;
```

---

## 3. Règles de Gestion

### RG-CON-01 : Décompte en Jours Calendaires Stricts

```
nb_jours_decomptes = (date_fin - date_debut) + 1
```

**Règle** : Tous les jours de la période sont comptés — Samedi, Dimanche, jours fériés inclus.

```java
// Implémentation Java (DemandeConge.java)
public static BigDecimal calculerNombreJoursDecomptes(Date dateDebut, Date dateFin, boolean estDemiJournee) {
    if (estDemiJournee) return new BigDecimal("0.5");
    long nbJours = ChronoUnit.DAYS.between(dateDebut.toLocalDate(), dateFin.toLocalDate()) + 1;
    return BigDecimal.valueOf(nbJours);
}
```

### RG-CON-01b : Décompte des Demi-Journées de Congé (0,5 j)

**Règle** : Si `est_demi_journee = true`, `date_debut` doit égaler `date_fin` et `moment_demi_journee` doit être soit `'MATIN'` soit `'APRES_MIDI'`. Le décompte est fixé à **0,5 jour** (`nb_jours_decomptes = 0.5`). 

**Valorisation paie** : Maintien de salaire à 100% (pas de retenue pour absence). Seul le solde de congé payé est débité de 0,5 jour.

**Exemples concrets** :
| Situation | Date début | Date fin | Reprise | Jours décomptés |
|---|---|---|---|---|
| 1 semaine complète | Lundi 01/09 | Dimanche 07/09 | Lundi 08/09 | **7 jours** |
| Vendredi posé | Vendredi 05/09 | Vendredi 05/09 | Lundi 08/09 | **3 jours** (V + S + D) |
| Pont semaine | Jeudi 04/09 | Dimanche 07/09 | Lundi 08/09 | **4 jours** |

### RG-CON-02 : Acquisition Mensuelle & Ancienneté Minimale

- **Taux d'acquisition** : 2,5 jours par mois de travail effectif (soit 30 jours/an).
- **Droit de jouissance** : Ouvert après **12 mois continus** de service (Art. 85).
- Durant les 12 premiers mois : l'employé accumule son solde mais **ne peut pas le consommer** sans dérogation RH.
- La vérification se fait sur la date de début du contrat actif :

```java
long moisAnciennete = ChronoUnit.MONTHS.between(dateDebutContrat, dateDebut);
if (moisAnciennete < typeConge.getExigeAncienneteMinMois()) {
    throw new IllegalArgumentException("Ancienneté insuffisante...");
}
```

### RG-CON-03 : Bonification d'Ancienneté

Au-delà de 3 ans d'ancienneté, un **bonus annuel** de jours de congés est accordé lors du mois anniversaire d'embauche :

```
Règle : bonus = floor(années_ancienneté / 5)

Exemples :
  3 ans  → bonus = 0  (3/5 = 0)
  5 ans  → bonus = 1 jour
  10 ans → bonus = 2 jours
  15 ans → bonus = 3 jours
```

Ce bonus est généré par le `AcquisitionCongeScheduler` et inséré comme mouvement de type `BONIFICATION`.

### RG-CON-04 : Prescription Triennale & Algorithme FIFO

Les droits acquis se prescrivent après **36 mois** (Art. 85). Le SIRH applique la logique **FIFO** (First In, First Out) : les jours les plus anciens sont supposés consommés en premier.

**Algorithme de purge (PurgeCongeScheduler — 1er de chaque mois à 02h00)** :

```sql
-- Calculer pour chaque employé :
-- Total acquis il y a +36 mois VS Total consommé sur toute la période
SELECT id_employe,
    SUM(nb_jours) FILTER (WHERE type_mouvement IN ('ACQUISITION', 'BONIFICATION')
                           AND date_mouvement < CURRENT_DATE - INTERVAL '36 months') 
        AS total_acquis_anciens,
    SUM(-nb_jours) FILTER (WHERE nb_jours < 0) 
        AS total_consomme
FROM mouvement_solde_conge
GROUP BY id_employe
HAVING [total_acquis_anciens] > [total_consomme]
-- → Si positif : des jours anciens ne sont pas encore consommés → EXPIRATION
```

Si `total_acquis_anciens - total_consomme > 0`, un mouvement `EXPIRATION` est inséré avec le nombre de jours périmés (débit négatif).

**Plafond absolu du solde** : 30j × 3ans = **90 jours maximum**.

### RG-CON-05 : Solde Négatif & Anticipation

- Un congé peut être accordé **en anticipation** (avant que les jours soient acquis) sous réserve d'une **dérogation RH explicite** (`derogation_solde_negatif = TRUE`).
- **Plafond d'anticipation** : `-10 jours` maximum (paramètre `PLAFOND_SOLDE_CONGE_NEGATIF`).
- Si l'employé quitte l'entreprise avec un solde négatif, la valeur financière des jours pris en trop est **déduite du Solde de Tout Compte** (STC).

```java
BigDecimal nouveauSolde = soldeDisponible.subtract(nbJoursDecomptes);
if (nouveauSolde.compareTo(BigDecimal.ZERO) < 0 && !derogationSoldeNegatif) {
    throw new IllegalArgumentException("Solde insuffisant. Dérogation RH requise.");
}
if (nouveauSolde.compareTo(BigDecimal.valueOf(plafondNegatif)) < 0) {
    throw new IllegalArgumentException("Plafond d'anticipation dépassé.");
}
```

### RG-CON-06 : Délais de Préavis

Pour les congés soumis à préavis (`soumis_delai_preavis = TRUE` sur le type de congé) :

| Durée de la demande | Préavis minimum requis |
|---|---|
| ≤ 3 jours (court) | 2 jours (`DELAI_PREAVIS_COURT_JOURS` en `parametre_global`) |
| > 3 jours (long) | 15 jours (`DELAI_PREAVIS_LONG_JOURS` en `parametre_global`) |

**Exceptions au préavis** : MALADIE, MATERNITE, PATERNITE, EXCEPTIONNEL ne sont pas soumis à préavis (imprévisibles).

**Dérogation** : Un RH peut cocher `derogation_delai_preavis = TRUE` pour bypasser cette règle avec justification.

### RG-CON-07 : Pool Annuel Exceptionnel (Art. 88 — 10 jours/an)

Les congés exceptionnels (événements familiaux) et le congé paternité partagent un **pool annuel commun de 10 jours** par employé par année civile.

**Sous-référentiel des motifs (`ref_motif_evenement_familial`)** :
| Code Motif | Durée accordée |
|---|---|
| `MARIAGE` | 3 jours |
| `NAISSANCE` / `PATERNITE` | 2 à 3 jours |
| `DECES_CONJOINT_ENFANT` | 3 jours |
| `DECES_PARENT` | 2 jours |

**Vérification à la saisie** :
```java
// Calcul de la consommation du pool pour l'année en cours
BigDecimal totalApresDemande = consommeActuel.add(nbJoursDecomptes);
if (totalApresDemande.compareTo(BigDecimal.valueOf(10)) > 0 && !derogationSoldeNegatif) {
    throw new IllegalArgumentException("Quota du pool 10j/an (Art. 88) dépassé...");
}
```

### RG-CON-08 : Congé Maternité — 98 jours fixes

- **Durée standard** : **98 jours consécutifs** (14 semaines légales — Art. 97).
- **Extension pathologique** : Sur justificatif médical, extension possible jusqu'à **119 jours** (14 + 3 semaines supplémentaires).
- Décompte calendaire strict (Samedis, Dimanches, Fériés inclus — non reportables).

```java
if ("MATERNITE".equals(typeConge.getCode())) {
    int maxPlafond = extensionPathologiqueMaternite ? 119 : 98;
    if (nbJoursDecomptes.compareTo(BigDecimal.valueOf(maxPlafond)) > 0) {
        throw new IllegalArgumentException("Durée max maternité dépassée...");
    }
}
```

### RG-CON-09 : Congé Maladie — Maintien & Bascule

- **Maintien de salaire** : Garanti par l'employeur jusqu'à **30 jours par épisode continu** (correspondant à la durée de préavis légale — Art. 13).
- **Bascule automatique** : Au-delà de 30 jours d'arrêt continu, le type bascule vers `MALADIE_SANS_SOLDE` (prise en charge CNaPS).
- **Suspension maximale du contrat** : **6 mois (180 jours)**.

### RG-CON-10 : Fractionnement Obligatoire

Pour le congé annuel, la loi (Art. 85) impose qu'au moins une tranche de **12 jours consécutifs minimum** soit posée au cours de l'exercice.

Le scheduler de notifications détecte en novembre-décembre les employés n'ayant pas respecté cette obligation et génère l'alerte `FRACTIONNEMENT_NON_RESPECTE`.

---

## 4. Typologies de Congés

| Type | Code | Payé | Décompte Solde | Ancienneté Min | Préavis | Pool | Plafond |
|---|---|---|---|---|---|---|---|
| Congé Annuel | `ANNUEL` | ✅ | ✅ Oui | 12 mois | ✅ Oui | — | 90j (FIFO) |
| Congé Maladie | `MALADIE` | ✅ | ❌ Non | — | ❌ Non | — | 30j/épisode |
| Maladie Sans Solde | `MALADIE_SANS_SOLDE` | ❌ | ❌ Non | — | ❌ Non | — | 180j/total |
| Exceptionnel | `EXCEPTIONNEL` | ✅ | ❌ Non | — | ❌ Non | Pool 10j | 10j/an |
| Paternité | `PATERNITE` | ✅ | ❌ Non | — | ❌ Non | Pool 10j | 3j |
| Maternité | `MATERNITE` | ✅ | ❌ Non | — | ❌ Non | — | 98j (119j patho) |
| Sans Solde | `SANS_SOLDE` | ❌ | ❌ Non | — | ✅ Oui | — | — |

---

## 5. Workflow & Circuit de Validation

### 5.1 Diagramme d'État d'une Demande

```
             ┌────────────────────────┐
             │  SAISIE DE LA DEMANDE  │
             │  (BROUILLON)           │
             └────────────┬───────────┘
                          │  Soumettre
             ┌────────────▼───────────┐
             │     EN_ATTENTE         │◄──── Délai préavis vérifié
             │                        │      Règles métier validées
             └──────┬─────────────────┘
                    │
          ┌─────────┼───────────────────┐
          ▼         ▼                   ▼
  ┌─────────────┐ ┌──────────┐ ┌──────────────┐
  │   VALIDE    │ │  REFUSE  │ │   ANNULE     │
  │ (+ mouvement│ │ (motif   │ │ (+ recrédit  │
  │  PRISE si   │ │  requis) │ │  si VALIDE)  │
  │  ANNUEL)    │ └──────────┘ └──────────────┘
  └──────┬──────┘
         │ (si annulation post-validation)
         ▼
  ┌─────────────┐
  │   ANNULE    │ (+ mouvement ANNULATION = recrédit)
  └─────────────┘
```

### 5.2 Rôles & Droits de Validation

| Action | Rôle |
|---|---|
| Soumettre une demande (EN_ATTENTE) | Employé, Assistant RH |
| Valider / Refuser | DRH, DAF, Manager |
| Annuler une demande | DRH, DAF, ou l'employé (si encore EN_ATTENTE) |
| Accorder une dérogation solde/préavis | DRH uniquement |

### 5.3 Contrôles à la Validation

Lors de la transition `EN_ATTENTE → VALIDE`, le système effectue dans l'ordre :

1. ✅ Vérification du justificatif obligatoire (si requis par le type)
2. ✅ Vérification de l'ancienneté minimale
3. ✅ Vérification du délai de préavis
4. ✅ Vérification du plafond maternité
5. ✅ Vérification du pool annuel Art.88 (si EXCEPTIONNEL/PATERNITE)
6. ✅ Vérification du solde disponible (si ANNUEL)
7. ✅ **Génération du mouvement PRISE** (débit du solde si ANNUEL)
8. ✅ Génération des notifications (solde faible, pool épuisé)

---

## 6. Automatisations & Schedulers

### 6.1 `AcquisitionCongeScheduler` — Exécution Quotidienne à 01h00

```java
@Scheduled(cron = "0 0 1 * * ?")  // Chaque nuit à 01:00:00
public void acquerirCongesMensuels()
```

**Logique d'exécution :**
1. Sélectionne tous les employés actifs dont le **jour anniversaire d'embauche** correspond au jour en cours (gestion de fin de mois incluse).
2. Vérification d'idempotence : s'assure qu'aucun mouvement `ACQUISITION` n'a déjà été crédité aujourd'hui pour cet employé.
3. Pour chaque employé éligible :
   - Insère un mouvement `ACQUISITION` de +2,5 jours.
   - Si c'est également le **mois anniversaire** et que l'ancienneté ≥ 3 ans : calcule et insère la `BONIFICATION` (voir RG-CON-03).
   - Génère une notification `BONIFICATION_ANCIENNETE_APPLIQUEE` si bonus > 0.

```sql
-- Éligibilité (gestion des mois courts : ex: embauche le 31, on crédite le 28/29 fév.)
WHERE EXTRACT(DAY FROM ct.date_debut) = EXTRACT(DAY FROM CURRENT_DATE)
   OR (  -- Dernier jour du mois si la date d'embauche n'existe pas ce mois
         EXTRACT(MONTH FROM CURRENT_DATE + INTERVAL '1 day') != EXTRACT(MONTH FROM CURRENT_DATE)
         AND EXTRACT(DAY FROM ct.date_debut) > EXTRACT(DAY FROM CURRENT_DATE)
      )
```

### 6.2 `PurgeCongeScheduler` — Exécution Quotidienne à 02h00

```java
@Scheduled(cron = "0 0 2 * * ?")  // Chaque nuit à 02:00:00
public void purgerCongesExpires()
```

Applique au jour le jour l'algorithme FIFO de prescription triennale décrit dans RG-CON-04 (purge des droits acquis il y a plus de 36 mois).

**Séquence d'exécution nocturne complète :**
```
00:00:00  → MouvementRhScheduler     (Basculement des mouvements RH planifiés)
00:05:00  → NotificationRhScheduler   (Toutes les alertes RH & notifications)
01:00:00  → AcquisitionCongeScheduler (Acquisition mensuelle + bonifications)
02:00:00  → PurgeCongeScheduler       (Purge triennale FIFO quotidienne)
03:30:00  → SessionSecurityScheduler  (Nettoyage notifications lues obsolètes)
04:00:00  → AuditLogPurgeScheduler    (1er du mois — Purge logs d'audit > 1 an)
```

---

## 7. Système de Notifications — Alertes Congés

Le `NotificationRhScheduler` (00h05) génère les alertes congés suivantes :

| Type d'Alerte | Priorité | Déclencheur | Description |
|---|---|---|---|
| `CONGE_EN_ATTENTE` | 🟠 HAUTE | Demande EN_ATTENTE depuis +48h | Demande non traitée depuis 2 jours |
| `SOLDE_CONGE_FAIBLE` | 🔵 BASSE | Solde < 5 jours | Information préventive (seuil paramétrable) |
| `SOLDE_CONGE_NEGATIF` | 🟠 HAUTE | Dérogation solde négatif accordée | Alerte déclenchée à la validation même |
| `RETOUR_CONGE_J` | 🔵 BASSE | `date_fin = CURRENT_DATE` AND statut=VALIDE | Rappel de fin de congé pour le manager |
| `FRACTIONNEMENT_NON_RESPECTE` | 🟠 HAUTE | Nov-Déc : aucune tranche ≥ 12j dans l'année | Alerte légale annuelle |
| `MALADIE_PLAFOND_EPISODE_ATTEINT` | 🔴 CRITIQUE | Maladie en cours ≥ 30 jours | Bascule CNaPS imminente |
| `CONGE_EXPIRATION_PROCHE` | 🟠 HAUTE | Jours acquis à J-30 de prescription | Jours risquant d'expirer sous 30 jours |
| `POOL_EXCEPTIONNEL_EPUISE` | 🟠 HAUTE | Pool 10j Art.88 = 100% | Quota annuel épuisé |
| `BONIFICATION_ANCIENNETE_APPLIQUEE` | 🔵 BASSE | Mois anniversaire + ancienneté ≥ 3 ans | Information de bonus accordé |

### 7.1 Alerte `CONGE_EXPIRATION_PROCHE` — Logique Détaillée

Cette alerte est la plus complexe. Elle anticipe de 30 jours la purge FIFO pour permettre à l'employé de poser ses congés avant qu'ils expirent :

```sql
-- Logique : identifier les employés avec des jours anciens (>= 36 mois - 30 jours)
-- non encore compensés par des consommations FIFO
SELECT e.id_employe, ...
FROM mouvement_solde_conge m
GROUP BY e.id_employe
HAVING 
    SUM(m.nb_jours) FILTER (WHERE type IN ('ACQUISITION','BONIFICATION')
        AND date_mouvement < (CURRENT_DATE + 30 jours) - 36 mois)
    - SUM(-m.nb_jours) FILTER (WHERE nb_jours < 0) 
    > 0
```

---

## 8. Valorisation Financière

Pour éviter les litiges et garantir l'équité de la paie, la valorisation utilise **deux diviseurs distincts** selon la nature de l'absence :

| Domaine | Diviseur | Formule |
|---|---|---|
| **Congés Payés** | 30 jours (calendaire mensuel légal) | `Valeur 1j CP = (Salaire Base + HS habituelles) / 30` |
| **Absences non justifiées** | 173,33 heures (Décret 2007-563) | `Taux horaire absence = Salaire Base / 173,33` |

**Exemple concret :**
```
Salaire de base : 1 000 000 Ar
Congé annuel de 5 jours posés :
  → Valeur = (1 000 000 / 30) × 5 = 166 667 Ar (maintenu à la paie)

Absence non justifiée de 1 journée (8h) :
  → Retenue = (1 000 000 / 173,33) × 8 = 46 170 Ar (déduite à la paie)
```

---

## 9. Architecture Technique

### 9.1 Classes Java Principales

| Classe | Table | Rôle |
|---|---|---|
| `DemandeConge.java` | `demande_conge` | Entité principale + toute la logique de validation métier |
| `MouvementSoldeConge.java` | `mouvement_solde_conge` | Journal comptable des droits |
| `RefTypeConge.java` | `ref_type_conge` | Référentiel comportemental des types |
| `RefStatutDemandeConge.java` | `ref_statut_demande_conge` | Référentiel des statuts |
| `RefMotifEvenementFamilial.java` | `ref_motif_evenement_familial` | Motifs pour EXCEPTIONNEL |
| `RefPoolCongeAnnuel.java` | `ref_pool_conge_annuel` | Pools annuels (Art.88) |
| `VSoldeCongeEmploye.java` | `v_solde_conge_employe` | Vue solde (read-only) |
| `VPoolCongeAnnuelConsommation.java` | `v_pool_conge_annuel_consommation` | Vue consommation pool |
| `AcquisitionCongeScheduler.java` | — | Scheduler d'acquisition mensuelle |
| `PurgeCongeScheduler.java` | — | Scheduler de purge FIFO |

### 9.2 Contrôleurs REST

| Contrôleur | URL de Base | Rôle |
|---|---|---|
| `DemandeCongeController` | `/api/conges/demandes` | CRUD + transitions de statut |
| `CongeSoldeController` | `/api/conges/soldes` | Consultation des soldes |
| `CongeRefController` | `/api/conges/referentiels` | Types, statuts, motifs |

### 9.3 Logique Métier Centralisée dans `DemandeConge.java`

Toutes les règles de gestion sont implémentées directement dans le modèle entité :

```java
public class DemandeConge extends ClassMere {
    
    // Calcul du décompte (statique, appelé à chaque saisie)
    static BigDecimal calculerNombreJoursDecomptes(Date debut, Date fin)
    
    // Validation complète (appelée avant tout save())
    @Override
    void controler(Connection c) throws Exception
        // → RG-CON-01 : Décompte calendaire
        // → RG-CON-02 : Ancienneté minimale
        // → RG-CON-06 : Délai de préavis
        // → RG-CON-08 : Plafond maternité
        // → RG-CON-07 : Pool annuel Art.88
    
    // Transition EN_ATTENTE → VALIDE (avec génération du mouvement PRISE)
    DemandeConge valider(Integer idValideur, Connection c) throws Exception
    
    // Transition EN_ATTENTE → REFUSE
    DemandeConge refuser(Integer idValideur, String motif, Connection c)
    
    // Annulation (avec recrédit si congé annuel validé)
    DemandeConge annuler(Integer idActeur, Connection c)
}
```

---

## 10. API REST — Endpoints

Base URL : `/api/conges`

| Méthode | URL | Description |
|---|---|---|
| `GET` | `/demandes` | Liste toutes les demandes (filtres disponibles) |
| `GET` | `/demandes/{id}` | Détail d'une demande |
| `POST` | `/demandes` | Créer une nouvelle demande |
| `PUT` | `/demandes/{id}` | Modifier une demande (BROUILLON seulement) |
| `PUT` | `/demandes/{id}/soumettre` | Passer EN_ATTENTE |
| `PUT` | `/demandes/{id}/valider` | Valider une demande |
| `PUT` | `/demandes/{id}/refuser` | Refuser une demande (motif requis) |
| `PUT` | `/demandes/{id}/annuler` | Annuler une demande |
| `GET` | `/soldes/{idEmploye}` | Solde actuel d'un employé |
| `GET` | `/soldes/{idEmploye}/mouvements` | Historique complet des mouvements |
| `GET` | `/referentiels/types` | Liste des types de congés |
| `GET` | `/referentiels/statuts` | Liste des statuts de demande |
| `GET` | `/referentiels/motifs-familiaux` | Liste des motifs exceptionnels |

### Paramètres de Filtrage (GET `/demandes`)

| Paramètre | Type | Description |
|---|---|---|
| `idEmploye` | Integer | Filtrer par employé |
| `idTypeConge` | Integer | Filtrer par type de congé |
| `idStatut` | Integer | Filtrer par statut |
| `annee` | Integer | Filtrer par année |
| `dateDebut` / `dateFin` | Date | Fenêtre temporelle |

---

## Annexe — Sources Légales

| Source | Référence |
|---|---|
| Code du Travail de Madagascar | Loi n° 2003-044 du 28 juillet 2004 — Art. 13, 85, 86, 87, 88, 90, 95, 97, 98 |
| Durée légale du travail | Décret n° 2007-563 — 40h/semaine = 173,33h/mois |
| Prestations CNaPS | Code de Prévoyance Sociale |

---

*Document généré pour mémoire de fin d'études — GALAXAM SIRH 2026*



---

## Perspectives d'Évolution (V2)

### Gestion des Soldes par Millésime (Exercice)
Bien que l'acquisition des congés se fasse mensuellement (ex: +2.5j via un job sur le solde global), l'application de la prescription (expiration au bout de 3 ans) est complexe avec un solde unique, car elle requiert un algorithme FIFO (First-In, First-Out) sur tout l'historique.
**Évolution prévue** :
- Créer une table `conge_compteur_annuel` pour tracer les acquisitions et les prises par période de référence (ex: Exercice 2024, Exercice 2025).
- Le job d'expiration ciblera directement le reliquat d'un millésime obsolète, allégeant la logique de calcul du backend.
