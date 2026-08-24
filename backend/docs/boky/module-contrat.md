# Module Contrat & Parcours de Carrière — Documentation Technique & Fonctionnelle
## GALAXAM SIRH — Mémoire de Fin d'Études

> **Auteur** : Équipe de Développement GALAXAM SIRH  
> **Module** : Gestion des Contrats de Travail & Mouvements de Carrière  
> **Version** : 2.0.0 (Spécification Officielle Étendue)  
> **Date** : 2026

---

## Table des Matières

1. [Présentation Générale](#1-présentation-générale)
2. [Modèle de Données](#2-modèle-de-données)
3. [Règles de Gestion (RG)](#3-règles-de-gestion)
4. [Workflow & Circuit d'Approbation](#4-workflow--circuit-dapprobation)
5. [Automatisations & Schedulers (Tâches Planifiées)](#5-automatisations--schedulers)
6. [Système de Notifications RH](#6-système-de-notifications-rh)
7. [Génération Documentaire](#7-génération-documentaire)
8. [Architecture Technique](#8-architecture-technique)
9. [API REST — Endpoints](#9-api-rest--endpoints)
10. [Traçabilité & Audit](#10-traçabilité--audit)

---

## 1. Présentation Générale

### 1.1 Rôle du Module

Le **module Contrat & Parcours de Carrière** est le **cœur opérationnel RH** du SIRH GALAXAM. Il orchestre l'intégralité du cycle de vie d'un salarié au sein de l'entreprise, depuis l'embauche initiale (onboarding) jusqu'au départ définitif (offboarding).

Ce module couvre :
- La **création et gestion des contrats de travail** (CDI, CDD, Stage, Prestation).
- La **gestion des périodes d'essai** (confirmation, renouvellement unique).
- Les **mouvements de carrière** (mutations, promotions, changements de type de contrat).
- La **rupture de contrat** (démission, licenciement, rupture conventionnelle...).
- La **génération automatique de documents officiels** (certificats de travail, lettres de rupture).
- Le **cascadage automatique du statut employé** en fonction de ses contrats.

### 1.2 Principes Fondamentaux

| Principe | Description |
|---|---|
| **Immutabilité de l'historique** | Aucun contrat ou affectation validé n'est jamais supprimé — tout est historisé |
| **Workflow obligatoire** | Tout mouvement RH passe par un état BROUILLON avant d'être appliqué |
| **Atomicité transactionnelle** | Chaque validation est une transaction ACID unique (Commit / Rollback) |
| **Cascadage automatique** | La rupture du dernier contrat bascule automatiquement l'employé en inactif |
| **Chaînage contractuel** | Les contrats successifs sont liés par `id_contrat_precedent` pour tracer la continuité |

---

## 2. Modèle de Données

### 2.1 Vue d'Ensemble des Tables

```
EMPLOYE (1)
    │
    ├── (N) CONTRAT_TRAVAIL
    │           │ id_contrat_precedent → CONTRAT_TRAVAIL (self-join pour chaînage)
    │           │
    │           └── (N) EMPLOYE_AFFECTATION_CARRIERE
    │                       (Poste, Salaire, Site, Département, Service, Fonction)
    │
    └── (N) WORKFLOW_RH
                (Événement RH en cours : EMBAUCHE, TRANSITION, RUPTURE)
                id_contrat_cible → CONTRAT_TRAVAIL
                id_affectation_cible → EMPLOYE_AFFECTATION_CARRIERE
```

### 2.2 Table `contrat_travail`

```sql
Table contrat_travail {
  id_contrat              INTEGER PK AUTO_INCREMENT
  id_employe              INTEGER FK → employe
  id_filiale              INTEGER FK → ref_filiale
  id_type_contrat         INTEGER FK → ref_type_contrat  -- CDI, CDD, STAGE...
  id_type_intervenant     INTEGER FK → ref_type_intervenant
  id_statut_contrat       INTEGER FK → ref_statut_contrat
  id_type_evenement_initial INTEGER FK → ref_type_evenement_rh
  id_contrat_precedent    INTEGER FK → contrat_travail  -- Chaînage historique
  
  numero_contrat          VARCHAR(50)  -- Auto-généré si non fourni
  date_signature          DATE
  date_debut              DATE NOT NULL
  date_fin                DATE          -- NULL pour CDI
  duree_essai_mois        INTEGER
  date_fin_essai          DATE
  
  comptabilise_effectif   BOOLEAN DEFAULT true
  id_motif_sortie         INTEGER FK → ref_motif_sortie
  description             TEXT
  commentaire             TEXT
  
  created_at              TIMESTAMP
  updated_at              TIMESTAMP
}
```

**Cycle de vie du statut contrat** :
```
BROUILLON → A_SIGNER → ACTIF → SUSPENDU → CLOTURE
                                         → ROMPU
```

### 2.3 Table `employe_affectation_carriere`

L'affectation porte les **données opérationnelles et financières** d'un poste à un instant T :

```sql
Table employe_affectation_carriere {
  id_affectation          INTEGER PK
  id_employe              INTEGER FK → employe
  id_contrat              INTEGER FK → contrat_travail
  id_statut_affectation   INTEGER FK → ref_statut_affectation
  id_motif_mouvement      INTEGER FK → ref_motif_mouvement
  
  -- Localisation
  id_site                 INTEGER FK → ref_site
  id_departement          INTEGER FK → ref_departement
  id_service              INTEGER FK → ref_service
  id_fonction             INTEGER FK → ref_fonction
  id_responsable_hierarchique INTEGER FK → employe
  
  -- Classification professionnelle & Paie
  id_categorie_professionnelle INTEGER FK → ref_categorie_professionnelle
    -- 'A' (Agent = droit aux heures supplémentaires)
    -- 'C' (Cadre = pas d'heures supplémentaires)
  id_mode_calcul_paie     INTEGER FK → ref_mode_calcul_paie
    -- MENSUEL, JOURNALIER, HORAIRE
  salaire_base            DECIMAL(12,2) NOT NULL
  
  date_debut              DATE NOT NULL
  date_fin                DATE  -- NULL = affectation courante
  description             TEXT
  commentaire             TEXT
  
  created_at              TIMESTAMP
}
```

**Cycle de vie du statut affectation** :
```
PLANIFIEE → ACTIVE → HISTORISEE
```

### 2.4 Table `workflow_rh`

Le workflow RH est le **mécanisme de validation** avant toute application d'un mouvement :

```sql
Table workflow_rh {
  id_workflow             INTEGER PK
  id_employe              INTEGER FK → employe
  id_type_evenement_rh    INTEGER FK → ref_type_evenement_rh
  id_statut_workflow      INTEGER FK → ref_statut_workflow
  
  id_contrat_cible        INTEGER FK → contrat_travail  -- Contrat à activer
  id_affectation_cible    INTEGER FK → employe_affectation_carriere
  id_contrat_source       INTEGER FK → contrat_travail  -- Contrat à fermer (transitions)
  
  date_effet              DATE NOT NULL
  motif                   TEXT
  commentaire             TEXT
  
  created_at              TIMESTAMP
  created_by              INTEGER FK → utilisateur
  updated_at              TIMESTAMP
}
```

**Types d'événements RH (`ref_type_evenement_rh`)** :
- `EMBAUCHE_INITIALE` — Premier contrat
- `MUTATION` — Changement de site ou département
- `PROMOTION` — Changement de fonction ou catégorie
- `REVALORISATION_SALARIALE` — Augmentation de salaire
- `CHANGEMENT_TYPE_CONTRAT` — ex: CDD → CDI
- `RUPTURE_CONTRAT` — Fin de la relation de travail

**Statuts de workflow (`ref_statut_workflow`)** :
- `BROUILLON` — Saisi, non validé
- `VALIDE` — Approuvé par DAF/DRH, pas encore appliqué
- `PLANIFIE` — Validé mais date d'effet future
- `APPLIQUE` — Effectivement appliqué
- `ANNULE` — Annulé sans suite

---

## 3. Règles de Gestion

### RG-CON-01 : Embauche Initiale — Unicité du Contrat Actif

Avant toute création de brouillon d'embauche :
1. Le système vérifie qu'**aucun contrat ACTIF** n'existe déjà pour cet employé. Si oui → exception métier : *"Utilisez un workflow de transition."*
2. Le système vérifie qu'**aucun workflow BROUILLON** n'est en cours. Si oui → exception métier : *"Validez ou annulez le brouillon existant."*

```
CRÉATION BROUILLON EMBAUCHE
    │
    ├── [VÉRIF] Contrat actif existant ? → ERREUR si OUI
    ├── [VÉRIF] Workflow brouillon existant ? → ERREUR si OUI
    ├── [CREATE] ContratTravail (statut: BROUILLON)
    ├── [CREATE] EmployeAffectationCarriere (statut: PLANIFIEE)
    └── [CREATE] WorkflowRh (statut: BROUILLON)
```

### RG-CON-02 : Période d'Essai — Renouvellement Unique

- La période d'essai est soumise au **Code du Travail malgache** qui stipule qu'elle ne peut être renouvelée **qu'une seule fois**.
- Le système calcule automatiquement l'état de l'essai : `EN_COURS`, `ECHEANCE_PROCHE` (< 15 jours), ou `EXPIRE`.
- Si `essai_renouvele = TRUE` sur le contrat, toute tentative de renouvellement est **bloquée** par l'interface avec le message : *"2ème Période atteinte — Max légal d'essai atteint."*
- **Isolation par contrat** : L'état d'un essai est évalué **par identifiant de contrat actif**. La confirmation d'un ancien contrat clôturé ne peut pas interférer avec un nouveau contrat en cours.

### RG-CON-03 : Transitions de Carrière — Continuité & Automatisme Temporel

Lors de tout mouvement (Mutation, Promotion, Changement de type de contrat) :

**1. Chaînage contractuel :**
```
Nouveau Contrat
  └── id_contrat_precedent = Ancien Contrat (ID)
```

**2. Clôture temporelle automatique de l'ancienne affectation :**
```
Date de fin ancienne affectation = Date d'effet du mouvement — 1 jour
```

**3. Transitions de statut :**
```
Ancienne Affectation : ACTIVE → HISTORISEE
Nouvelle Affectation : PLANIFIEE → ACTIVE
```

**4. Gestion des événements futurs (PLANIFIE) :**
Si la date d'effet est dans le futur, le workflow passe au statut `PLANIFIE`. Le contrat et l'affectation en cours restent `ACTIF` jusqu'à la date d'effet. Le scheduler nocturne applique automatiquement les changements au matin J.

### RG-CON-04 : Rupture & Cascadage Automatique du Statut Employé

La rupture de contrat est le point critique où le SIRH doit synchroniser le statut de l'employé :

**Typologies de rupture (`ref_motif_sortie`)** :
- `DEMISSION` — À l'initiative du salarié
- `LICENCIEMENT` — À l'initiative de l'employeur
- `RUPTURE_CONVENTIONNELLE` — Par accord mutuel
- `FIN_CONTRAT` — Terme naturel d'un CDD
- `RUPTURE_ESSAI` — Pendant la période d'essai
- `DEPART_RETRAITE` — Départ à la retraite

**Algorithme de cascadage :**
```java
// Lors de l'application effective d'une rupture :
1. Contrat source → statut = ROMPU (ou CLOTURE)
                    date_fin = date_effet
2. Affectation source → statut = HISTORISEE
3. Vérifier s'il reste un autre contrat ACTIF pour cet employé :
   SELECT COUNT(*) FROM contrat_travail 
   WHERE id_employe = ? AND id_statut_contrat = ACTIF
   
   SI COUNT > 0 → employe.est_actif reste TRUE  (multi-contrats)
   SI COUNT = 0 → employe.est_actif = FALSE     (offboarding complet)
                  employe.date_desactivation = NOW()
```

**Règle du préavis :**
Si la date d'effet de rupture est future, l'employé reste `est_actif = TRUE` jusqu'au jour J. Le scheduler nocturne applique le cascadage à minuit de la date d'effet.

### RG-CON-05 : Génération du Numéro de Contrat

Si le RH ne fournit pas de numéro de contrat lors de la création, le système en génère automatiquement un via la méthode `genererNumeroContrat()` :

```
Format : [ANNEE]-CT-[SEQUENCE_5_CHIFFRES]
Exemple : 2026-CT-00042
```

La séquence est calculée par `MAX(id_contrat) + 1` pour garantir l'unicité.

### RG-CON-06 : Catégorie Professionnelle & Heures Supplémentaires

La catégorie professionnelle (`A` ou `C`) a un impact direct sur la **gestion des heures supplémentaires** :

| Catégorie | Code | Droit aux Heures Sup | Description |
|---|---|---|---|
| **Agent** | `A` | ✅ OUI | Ouvriers, employés, techniciens. Les HS sont calculées et payées. |
| **Cadre** | `C` | ❌ NON | Cadres dirigeants. Les HS sont forcées à 0 dans la synthèse mensuelle, quelle que soit la réalité du pointage. |

---

## 4. Workflow & Circuit d'Approbation

### 4.1 Diagramme d'État & Gestion des Événements Planifiés

```
┌────────────────┐
│   BROUILLON    │  ◄── Créé par l'Assistant RH / Manager
└───────┬────────┘      (aucun impact sur le contrat ou l'employé)
        │
        ├──────────────────────────┐
        ▼ (Action: VALIDER)        ▼ (Action: ANNULER)
┌────────────────┐         ┌────────────────┐
│     VALIDE     │         │    ANNULE      │
└───────┬────────┘         └────────────────┘
        │
        ├──────────────────────────────────────┐
        ▼ (date_effet <= Aujourd'hui)          ▼ (date_effet > Aujourd'hui)
┌────────────────┐                    ┌────────────────┐
│    APPLIQUE    │                    │    PLANIFIE    │
│ (Application   │                    │ (En attente du │
│  immédiate)    │                    │  scheduler)    │
└────────────────┘                    └────────┬───────┘
                                               │
                                    ┌──────────▼──────────┐
                                    │  MouvementRhScheduler│
                                    │  (00:00:00 chaque    │
                                    │   nuit & au startup) │
                                    └──────────────────────┘
```

> **Règle d'Or (Événements Planifiés à Date Postérieure)** :
> Tout événement RH (confirmation d'essai, renouvellement d'essai, mutation, promotion, rupture) validé avec une **date d'effet future (`date_effet > aujourd'hui`)** est enregistré comme événement planifié sans modifier immédiatement le statut du contrat ou de la période d'essai (`EN_COURS`). Les modifications prennent effet automatiquement lorsque la date d'effet est atteinte, via le Cron `MouvementRhScheduler`.

### 4.2 Actions par Rôle

| Action | Rôle autorisé |
|---|---|
| Créer un brouillon | Assistant RH, Manager |
| Corriger un brouillon | Assistant RH, Manager (créateur) |
| Valider / Annuler | DAF, Directeur RH |
| Consulter l'historique | Tout utilisateur avec droit LECTURE |

### 4.3 Correction d'un Brouillon

Un brouillon peut être corrigé avant validation. Le bouton `[ CORRIGER ]` rouvre la modale de saisie (EmbaucheModal, TransitionModal ou RuptureContratModal) pré-remplie avec les données existantes, permettant une modification sans perte de saisie.

---

## 5. Automatisations & Schedulers

### 5.1 `MouvementRhScheduler` — Exécution Nocturne & Rattrapage au Démarrage

```java
@EventListener(ApplicationReadyEvent.class) // Rattrapage au démarrage
@Scheduled(cron = "0 0 0 * * ?")            // Chaque nuit à 00:00:00
public void effectuerBasculementMouvementsPlanifies() {
    // Appelle WorkflowRhController.autoAppliquerMouvementsEchus(c)
    // Applique tous les workflows validés dont la date_effet <= CURRENT_DATE
}
```

**Logique d'application automatique :**
- Détection des workflows au statut `VALIDE` dont `date_effet <= CURRENT_DATE`.
- Application des effets réels :
  - Confirmation d'essai (`contrat_periode_essai` -> `CONFIRMEE`, `date_fin_essai` -> `NULL`).
  - Renouvellement d'essai (P1 -> `RENOUVELEE`, P2 -> `EN_COURS`).
  - Ruptures de contrat (contrat -> `ROMPU`, clôture affectation, cascadage statut employé).
  - Transitions (clôture source, activation cible).
- Envoi de la notification RH `MOUVEMENT_APPLIQUE`.

**Garanties :**
- Exécuté en premier (avant le scheduler de notifications à 00h05).
- Traitement atomique par workflow (chaque workflow est une transaction indépendante).
- Journalisation des erreurs en cas d'échec (log système).

---

## 6. Système de Notifications RH

### 6.1 `NotificationRhScheduler` — Exécution à 00h05

Le scheduler de notifications tourne **5 minutes après** le scheduler de mouvements, pour garantir que les données sont à jour avant la génération des alertes.

```java
@Scheduled(cron = "0 5 0 * * ?")  // Chaque nuit à 00:05:00
public void genererNotificationsRh()
```

### 6.2 Notifications liées au Module Contrat

| Type d'Alerte | Priorité | Déclencheur | Description |
|---|---|---|---|
| `CONTRAT_EXPIRE_J` | 🔴 CRITIQUE | `date_fin = CURRENT_DATE` | Contrat CDD/Stage expirant le jour même |
| `CONTRAT_EXPIRE_7J` | 🟠 HAUTE | `date_fin BETWEEN +1 AND +7 jours` | Contrat expirant dans la semaine |
| `CONTRAT_EXPIRE_30J` | 🟡 NORMALE | `date_fin BETWEEN +8 AND +30 jours` | Contrat expirant dans le mois |
| `ESSAI_EXPIRE_J` | 🔴 CRITIQUE | `date_fin_essai = CURRENT_DATE` | Période d'essai se terminant aujourd'hui |
| `ESSAI_EXPIRE_7J` | 🟠 HAUTE | `date_fin_essai BETWEEN +1 AND +7 jours` | Essai expirant dans la semaine |
| `ESSAI_EXPIRE_15J` | 🟡 NORMALE | `date_fin_essai BETWEEN +8 AND +15 jours` | Essai expirant dans les 15 jours |
| `ESSAI_EXPIRE_30J` | 🟡 NORMALE | `date_fin_essai BETWEEN +16 AND +30 jours` | Essai expirant dans le mois |
| `BROUILLON_EN_ATTENTE` | 🟠 HAUTE | `created_at < NOW() - 48h` AND statut=BROUILLON | Workflow non traité depuis +48h |
| `DOCUMENTS_MANQUANTS` | 🟠 HAUTE | Documents obligatoires absents | Dossier RH incomplet |

### 6.3 Mécanisme de Purge & Anti-Doublon

Avant de générer les nouvelles alertes, le scheduler :
1. **Supprime les notifications de +60 jours** non lues (purge globale).
2. **Supprime toutes les alertes récurrentes non lues** du jour J-1 pour les recréer avec les données du jour J — évite les doublons.

```java
// Purge des alertes récurrentes J-1 non lues
DELETE FROM notification_rh 
WHERE type_alerte IN ('CONTRAT_EXPIRE_J', 'CONTRAT_EXPIRE_7J', 'CONTRAT_EXPIRE_30J', 
                      'ESSAI_EXPIRE_J', 'ESSAI_EXPIRE_7J', 'ESSAI_EXPIRE_15J', 'ESSAI_EXPIRE_30J',
                      'BROUILLON_EN_ATTENTE', 'DOCUMENTS_MANQUANTS', ...)
AND est_lue = FALSE;
```

### 6.4 Structure de la Table `notification_rh`

```sql
Table notification_rh {
  id_notification      INTEGER PK
  id_utilisateur       INTEGER FK → utilisateur  -- NULL = broadcast
  type_alerte          VARCHAR(50)
  priorite             VARCHAR(20)  -- CRITIQUE, HAUTE, NORMALE, BASSE
  titre                VARCHAR(255)
  message              TEXT
  lien                 VARCHAR(255)  -- URL de navigation directe
  id_employe_concerne  INTEGER FK → employe
  est_lue              BOOLEAN DEFAULT false
  date_creation        TIMESTAMP
}
```

---

## 7. Génération Documentaire

### 7.1 Certificat de Travail

**Exigence légale** : Obligatoire à tout employé dont le contrat est `CLOTURE` ou `ROMPU`.

**Mentions obligatoires générées automatiquement :**
- Raison sociale, adresse, NIF, STAT de la filiale employeuse
- Nom, prénom, domicile, N° CIN, N° CNaPS, Matricule interne du salarié
- Date d'entrée initiale & Date de sortie effective
- **Ancienneté globale calculée** : depuis la date du premier contrat de la chaîne (`id_contrat_precedent` suivi jusqu'à l'origine)
- Format : *"X an(s) Y mois"*
- Intitulé exact de la fonction et de la catégorie professionnelle
- Clause d'exonération : *"Salarié libre de tout engagement contractuel"*
- Mention de non-libération (ne vaut pas solde de tout compte)

**Calcul d'ancienneté en cas de contrats successifs :**
```
CDD (2024-01-01) → CDI (2025-06-01) → Démission (2026-08-19)
                                                         ↑
Ancienneté = depuis 2024-01-01 (date du PREMIER contrat de la chaîne)
           = 2 ans 7 mois (non pas 1 an 2 mois depuis le CDI)
```

**Technologie d'impression :**
- Généré via `CertificatTravailModal.jsx` — monté par **React Portal** sur `document.body`
- Feuille de style `@media print` dédiée au format **A4 Portrait**
- Masque l'interface SIRH (`#root`) lors de l'impression pour un rendu PDF professionnel

### 7.2 Autres Documents (Roadmap)

| Document | Statut | Notes |
|---|---|---|
| Lettre de rupture / licenciement | 🔄 En cours | Basée sur le motif de rupture sélectionné |
| Contrat de travail (modèle) | 🔄 Planifié | Génération du contrat signable en PDF |
| Avenant au contrat | 🔄 Planifié | Pour les modifications de salaire ou de poste |

---

## 8. Architecture Technique

### 8.1 Service Métier Central : `WorkflowRhService.java`

C'est le fichier de logique métier le plus critique du module (~1 000 lignes). Il orchestre :

```java
@Service
public class WorkflowRhService {
    // Étape 1 : Création du brouillon
    creerBrouillonEmbauche(idEmploye, req, userId, conn)
    creerBrouillonTransition(idEmploye, req, userId, conn)
    creerBrouillonRupture(idEmploye, req, userId, conn)
    
    // Étape 2 : Validation & Application
    validerEtAppliquerWorkflow(idWorkflow, userId, conn)
    
    // Automatique (Scheduler)
    autoAppliquerMouvementsEchus(conn)
    
    // Rupture de période d'essai
    rompre/confirmerEssai(idContrat, userId, conn)
}
```

### 8.2 Pattern de Transaction

Chaque opération critique utilise le pattern :
```java
c.setAutoCommit(false);
try {
    // Opérations métier
    operationA(c);
    operationB(c);
    c.commit();
} catch (Exception e) {
    c.rollback();
    throw e;
}
```

### 8.3 Vue SQL Unifiée `v_workflow_rh_cpl`

Une vue SQL agrège en temps réel toutes les données du workflow avec les libellés enrichis :

```sql
CREATE VIEW v_workflow_rh_cpl AS
SELECT
    wf.*,
    e.nom, e.prenom, e.matricule,
    tev.libelle AS type_evenement,
    rsw.code AS statut_workflow_code,
    ct.numero_contrat, ct.date_debut, ct.date_fin,
    rtc.code AS type_contrat_code, rtc.libelle AS type_contrat_libelle,
    aff.salaire_base,
    rs.code_site, rs.libelle AS site_libelle,
    rd.code_departement, rd.libelle AS departement_libelle,
    rf.libelle AS fonction_libelle,
    rcp.code AS categorie_pro_code
FROM workflow_rh wf
JOIN employe e ON wf.id_employe = e.id_employe
-- ... (jointures sur tous les référentiels)
```

Cette vue est utilisée par les API de liste et de détail pour éviter les requêtes multiples côté backend.

---

## 9. API REST — Endpoints

Base URL : `/api/contrats` et `/api/workflow`

| Méthode | URL | Description |
|---|---|---|
| `GET` | `/api/employes/{id}/contrats` | Historique complet des contrats d'un employé |
| `GET` | `/api/workflow/{id}` | Détail d'un workflow RH |
| `POST` | `/api/employes/{id}/workflow/embauche` | Créer brouillon d'embauche |
| `POST` | `/api/employes/{id}/workflow/transition` | Créer brouillon de transition |
| `POST` | `/api/employes/{id}/workflow/rupture` | Créer brouillon de rupture |
| `PUT` | `/api/workflow/{id}/valider` | Valider et appliquer un workflow |
| `PUT` | `/api/workflow/{id}/annuler` | Annuler un workflow |
| `PUT` | `/api/contrats/{id}/essai/confirmer` | Confirmer la période d'essai |
| `PUT` | `/api/contrats/{id}/essai/renouveler` | Renouveler la période d'essai |
| `GET` | `/api/contrats/{id}/certificat` | Générer le certificat de travail |

---

## 10. Traçabilité & Audit

### 10.1 Audit Log Immuable

Chaque opération sur un contrat ou workflow génère une entrée `audit_log` :

```sql
Table audit_log {
  id_audit         INTEGER PK
  id_utilisateur   INTEGER FK → utilisateur
  action           VARCHAR(100)  -- ex: 'VALIDER_WORKFLOW', 'ROMPRE_CONTRAT'
  entite           VARCHAR(50)   -- 'workflow_rh', 'contrat_travail'
  id_entite        INTEGER
  anciennes_valeurs JSONB        -- État avant
  nouvelles_valeurs JSONB        -- État après
  adresse_ip       VARCHAR(45)
  date_action      TIMESTAMP DEFAULT NOW()
}
```

**Actions auditées** :
- `CREER_BROUILLON_EMBAUCHE`
- `VALIDER_WORKFLOW`
- `ANNULER_WORKFLOW`
- `APPLIQUER_WORKFLOW_AUTO` (scheduler)
- `CONFIRMER_ESSAI`
- `RENOUVELER_ESSAI`
- `ROMPRE_CONTRAT`

### 10.2 Traçabilité des Mouvements

La table `employe_affectation_carriere` constitue en elle-même un **registre historique complet** de tous les postes occupés par un employé. La vue de la timeline de carrière (`TimelineBanners.jsx` côté frontend) exploite cette table pour afficher un parcours chronologique visuel.

---

*Document généré pour mémoire de fin d'études — GALAXAM SIRH 2026*



---

## Perspectives d'Évolution (V2)

### Gestion du Temps Partiel (Taux d'Activité)
Actuellement, le système suppose que tous les employés travaillent à 100% (40h/semaine). 
**Évolution prévue** :
- Ajouter une colonne `taux_activite DECIMAL(5,2) DEFAULT 100.00` dans `employe_affectation_carriere`.
- Proratiser dynamiquement le salaire de base et les seuils de calcul du module Pointage (ex: `8.0 * (taux_activite / 100)`) pour éviter qu'un employé à temps partiel ne soit comptabilisé comme étant en "déficit d'heures" chaque semaine.
