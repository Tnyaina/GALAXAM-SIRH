# Module Employé — Documentation Technique & Fonctionnelle
## GALAXAM SIRH — Mémoire de Fin d'Études

> **Auteur** : Équipe de Développement GALAXAM SIRH  
> **Module** : Gestion des Employés & Dossier Individuel  
> **Version** : 1.0.0  
> **Date** : 2026

---

## Table des Matières

1. [Présentation Générale](#1-présentation-générale)
2. [Modèle de Données](#2-modèle-de-données)
3. [Règles de Gestion (RG)](#3-règles-de-gestion)
4. [Workflow & Cycle de Vie](#4-workflow--cycle-de-vie)
5. [Architecture Technique](#5-architecture-technique)
6. [API REST — Endpoints](#6-api-rest--endpoints)
7. [Sécurité & Contrôle d'Accès](#7-sécurité--contrôle-daccès)
8. [Expérience Utilisateur](#8-expérience-utilisateur)

---

## 1. Présentation Générale

### 1.1 Rôle du Module

Le **module Employé** constitue le **référentiel central** du SIRH GALAXAM. Il est le point d'ancrage de toutes les autres fonctionnalités du système : les contrats, les affectations, les congés, la paie et le pointage s'appuient tous sur l'enregistrement d'un employé valide et actif.

Ce module permet de :
- Enregistrer et gérer le **dossier complet** de chaque collaborateur (identité civile, documents officiels, coordonnées, données sociales).
- Maintenir l'**historique des informations** personnelles et professionnelles.
- Piloter le **statut actif/inactif** de l'employé en cohérence avec ses contrats de travail.
- Centraliser les **pièces justificatives** (documents RH numérisés).

### 1.2 Périmètre Fonctionnel

| Sous-fonctionnalité | Description |
|---|---|
| Fiche d'identité | Nom, prénom, CIN, date de naissance, nationalité |
| Informations familiales | Situation maritale, conjoint, enfants à charge |
| Contacts & coordonnées | Téléphone, adresse, contacts d'urgence |
| Informations sociales | N° CNaPS, affiliation organisme sanitaire |
| Documents RH | CV, contrat signé, CIN numérisée, diplômes... |
| Photo de profil | Stockage et affichage de la photo de l'employé |
| Expériences professionnelles | Historique de carrière externe |
| Compte bancaire | RIB/coordonnées bancaires pour le virement de salaire |

---

## 2. Modèle de Données

### 2.1 Entité Principale : `employe`

La table `employe` est la table maître du système. Elle porte les informations d'état civil et les indicateurs de statut.

```
┌────────────────────────────────────────────────────────┐
│                        EMPLOYE                          │
├────────────────────────────────────────────────────────┤
│ id_employe        INTEGER  PK AUTO_INCREMENT            │
│ matricule         VARCHAR(20) UNIQUE NOT NULL           │
│ nom               VARCHAR(100) NOT NULL                 │
│ prenom            VARCHAR(100) NOT NULL                 │
│ sexe              CHAR(1) NOT NULL  -- 'M' ou 'F'       │
│ date_naissance    DATE NOT NULL                         │
│ lieu_naissance    VARCHAR(100) NOT NULL                 │
│ nationalite       VARCHAR(50) DEFAULT 'Malgache'        │
│ pere_nom_prenom   VARCHAR(150)                          │
│ mere_nom_prenom   VARCHAR(150)                          │
│ situation_familiale VARCHAR(20)                         │
│ cin_numero        VARCHAR(30) NOT NULL                  │
│ cin_date_delivrance DATE NOT NULL                       │
│ cin_lieu_delivrance VARCHAR(100) NOT NULL               │
│ cin_duplicata_date DATE                                 │
│ cin_duplicata_lieu VARCHAR(100)                         │
│ permis_conduire   VARCHAR(50)                           │
│ adresse_actuelle  TEXT NOT NULL                         │
│ telephone         VARCHAR(30) NOT NULL                  │
│ num_cnaps_personnel VARCHAR(30)                         │
│ photo_url         VARCHAR(255)                          │
│ date_declaration  DATE                                  │
│ id_responsable_hierarchique INTEGER → employe.id_employe│
│ est_actif         BOOLEAN DEFAULT true                  │
│ date_desactivation TIMESTAMP                            │
│ motif_desactivation VARCHAR(100)                        │
│ created_at        TIMESTAMP                             │
│ updated_at        TIMESTAMP                             │
└────────────────────────────────────────────────────────┘
```

### 2.2 Tables Satellites

Le dossier d'un employé est enrichi par plusieurs tables satellites liées par une relation `1-N` :

```
EMPLOYE (1)
    ├── (N) employe_conjoint           — Informations du conjoint
    ├── (N) employe_enfant             — Enfants (à charge IRSA)
    ├── (N) employe_contact_urgence    — Personnes à prévenir
    ├── (N) employe_exp_pro            — Expériences professionnelles antérieures
    ├── (N) employe_compte_banque      — Comptes pour virement de salaire
    ├── (N) document_rh                — Pièces justificatives numérisées
    ├── (N) contrat_travail            — Historique contractuel
    └── (N) employe_affectation_carriere — Historique des postes & salaires
```

#### `employe_conjoint`
| Champ | Type | Description |
|---|---|---|
| `id_conjoint` | INTEGER PK | |
| `id_employe` | INTEGER FK | |
| `nom_prenoms` | VARCHAR(150) | |
| `date_naissance` | DATE | |
| `societe` | VARCHAR(100) | Employeur du conjoint |
| `fonction` | VARCHAR(100) | Poste du conjoint |

#### `employe_enfant`
| Champ | Type | Description |
|---|---|---|
| `id_enfant` | INTEGER PK | |
| `id_employe` | INTEGER FK | |
| `nom_prenoms` | VARCHAR(150) | |
| `sexe` | CHAR(1) | |
| `date_naissance` | DATE NOT NULL | |
| `a_charge` | BOOLEAN DEFAULT true | **Impacte la réduction IRSA** |

#### `employe_compte_banque`
| Champ | Type | Description |
|---|---|---|
| `id_compte_banque` | INTEGER PK | |
| `id_employe` | INTEGER FK | |
| `id_banque` | INTEGER FK → `ref_banque` | |
| `num_compte_rib` | VARCHAR(50) | RIB complet |
| `titulaire_compte` | VARCHAR(150) | |
| `est_principal` | BOOLEAN | Compte à utiliser pour la paie |

#### `document_rh`
| Champ | Type | Description |
|---|---|---|
| `id_document` | INTEGER PK | |
| `id_employe` | INTEGER FK | |
| `id_type_document` | INTEGER FK → `ref_type_document` | |
| `nom_fichier` | VARCHAR(255) | Nom original |
| `chemin_stockage` | TEXT | Chemin serveur |
| `date_upload` | TIMESTAMP | |
| `id_uploade_par` | INTEGER FK → `utilisateur` | |

---

## 3. Règles de Gestion

### RG-EMP-01 : Unicité et Format du Matricule

- Le matricule est **unique à l'échelle du système** (contrainte `UNIQUE` en base de données).
- Il est **généré automatiquement** par le système lors de la création si non fourni.
- **Format** : Séquentiel numérique formaté (ex: `EMP-0001`, `EMP-0042`).
- Une fois attribué, le matricule est **immuable** — il ne peut jamais être modifié.

### RG-EMP-02 : Champs Obligatoires à la Création

La validation (`controler()`) impose les champs suivants pour toute création ou modification :

| Champ | Justification |
|---|---|
| `matricule` | Identifiant interne obligatoire |
| `nom` & `prenom` | Identification civile |
| `sexe` (M ou F) | Déclaration sociale |
| `date_naissance` | Age légal & IRSA |
| `lieu_naissance` | Document CIN |
| `cin_numero` | Identité légale malgache |
| `cin_date_delivrance` | Validité du CIN |
| `cin_lieu_delivrance` | Document officiel |
| `adresse_actuelle` | Correspondance légale |
| `telephone` | Contact d'urgence |

### RG-EMP-03 : Statut Actif / Inactif (Soft Delete)

Le système n'utilise **pas la suppression physique** des employés. Un employé est désactivé via les champs :
- `est_actif = FALSE`
- `date_desactivation = CURRENT_TIMESTAMP`
- `motif_desactivation = [raison de la désactivation]`

> **Règle critique** : Le champ `est_actif` de l'employé est **contrôlé automatiquement** par le module Contrat. Lors de la rupture du dernier contrat actif, le système bascule automatiquement `est_actif = FALSE` sans intervention manuelle du RH (cf. Module Contrat — RG-CON-04).

### RG-EMP-04 : Enfants à Charge & Impact Fiscal (IRSA)

Le flag `a_charge` sur la table `employe_enfant` détermine si l'enfant est pris en compte dans le calcul de la **réduction d'IRSA** (Impôt sur le Revenu Salarial).

- `a_charge = TRUE` → L'enfant génère un abattement fiscal sur l'IRSA calculé à la paie.
- `a_charge = FALSE` → Enfant enregistré au dossier mais sans impact fiscal.

### RG-EMP-05 : Documents Obligatoires

Un référentiel `ref_type_document` liste les documents avec un flag `obligatoire`. Chaque nuit, le scheduler de notifications (`NotificationRhScheduler`, 00h05) compare les types de documents obligatoires aux documents effectivement uploadés pour chaque employé actif et génère une alerte `DOCUMENTS_MANQUANTS` si des pièces sont absentes.

```sql
-- Logique de détection des pièces manquantes (extrait scheduler)
SELECT e.id_employe, STRING_AGG(rd.libelle, ', ') AS docs_manquants
FROM employe e
CROSS JOIN ref_type_document rd
WHERE rd.obligatoire = TRUE AND rd.actif = TRUE
AND NOT EXISTS (
  SELECT 1 FROM document_rh d
  WHERE d.id_employe = e.id_employe 
    AND d.id_type_document = rd.id_type_document
)
GROUP BY e.id_employe;
```

### RG-EMP-06 : Compte Bancaire Principal

Un employé peut avoir plusieurs comptes bancaires enregistrés (multi-banques autorisé). Le compte marqué `est_principal = TRUE` est celui utilisé par défaut pour les virements salariaux.

**Contrainte** : Un seul compte peut être principal à la fois. La logique applicative assure qu'au moment où un nouveau compte est défini comme principal, l'ancien perd ce statut.

### RG-EMP-07 : Responsable Hiérarchique

Le champ `id_responsable_hierarchique` crée une **auto-référence** sur la table `employe`. Il permet de modéliser l'organigramme hiérarchique directement dans le dossier de l'employé.

- La valeur est synchronisée avec l'affectation de carrière en cours.
- Elle est mise à jour automatiquement lors d'un mouvement RH (mutation de service, changement de N+1).

---

## 4. Workflow & Cycle de Vie

### 4.1 Cycle de Vie d'un Employé dans le SIRH

```
                    ┌─────────────────────────────┐
                    │  CRÉATION DU DOSSIER         │
                    │  (est_actif = FALSE           │
                    │   sans contrat actif)         │
                    └──────────────┬──────────────┘
                                   │
                    ┌──────────────▼──────────────┐
                    │  EMBAUCHE VALIDÉE            │
                    │  (est_actif = TRUE           │
                    │   ← déclenché par Contrat)   │
                    └──────────────┬──────────────┘
                                   │
               ┌───────────────────┼───────────────────┐
               ▼                   ▼                   ▼
    ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐
    │ MUTATION / PROMO │ │  CONGÉ / ABSENCE │ │  MISE À JOUR     │
    │ (via Module      │ │  (via Module     │ │  DOSSIER         │
    │  Contrat)        │ │   Congé)         │ │  (modification   │
    └──────────────────┘ └──────────────────┘ │  fiche)          │
                                              └──────────────────┘
                                   │
                    ┌──────────────▼──────────────┐
                    │  RUPTURE DE CONTRAT          │
                    │  (est_actif = FALSE          │
                    │   si aucun contrat ACTIF     │
                    │   restant — cascade auto)    │
                    └─────────────────────────────┘
```

### 4.2 Processus de Création d'un Dossier Employé

1. **Saisie de la fiche** : Le RH renseigne les informations d'état civil, CIN, coordonnées.
2. **Validation des champs** : Le backend exécute `controler()` — tous les champs obligatoires sont vérifiés, une exception métier est levée si nécessaire.
3. **Enregistrement** : Le dossier est sauvegardé avec `est_actif = FALSE` (l'employé n'est pas encore actif).
4. **Enrichissement du dossier** : Upload des documents (CIN numérisé, contrat signé, etc.), ajout du conjoint, des enfants.
5. **Activation** : L'employé passe à `est_actif = TRUE` uniquement lors de la **validation du premier contrat de travail** via le module Contrat.

---

## 5. Architecture Technique

### 5.1 Stack Technologique

| Couche | Technologie |
|---|---|
| **Backend** | Java (Jakarta EE / Spring Boot), JDBC pur |
| **Base de données** | PostgreSQL |
| **Frontend** | React.js (Vite), Axios |
| **Stockage fichiers** | Serveur local (répertoire `uploads/`) |
| **Authentification** | JWT (JSON Web Token) |

### 5.2 Pattern Architectural : ClassMere

Toutes les entités du projet héritent d'une classe abstraite `ClassMere` qui fournit un **CRUD générique** basé sur JDBC :

```java
public class Employe extends ClassMere {
    // Implémente :
    // - getTuppleID()        → retourne id_employe
    // - getAttributIDName()  → retourne "id_employe"
    // - controler(Connection c) → validation métier
    // - inserer(Connection c)   → INSERT SQL
    // - modifier(Connection c)  → UPDATE SQL
    // Hérite de ClassMere :
    // - save(c)   → appelle controler() puis inserer() ou modifier()
    // - delete(c) → suppression logique
    // - lister()  → SELECT *
}
```

### 5.3 Couches Applicatives

```
React Frontend (SPA)
        │  HTTP/JSON
        ▼
EmployeController.java      ← REST Controller (@GetMapping, @PostMapping...)
        │  Java objects / DTO
        ▼
Employe.java                ← Entité Métier + Validation + SQL (ClassMere)
        │  JDBC
        ▼
PostgreSQL Database
```

### 5.4 Gestion des Photos

La photo de profil est gérée par un contrôleur dédié `EmployePhotoController.java` :
- **Upload** : Multipart/Form-Data, stockage dans `uploads/photos/{id_employe}/`
- **Accès** : URL relative retournée et stockée dans `employe.photo_url`
- **Format** : JPG, PNG — taille recommandée 150x150 px

---

## 6. API REST — Endpoints

### Contrôleur : `EmployeController`
Base URL : `/api/employes`

| Méthode | URL | Description | Auth requise |
|---|---|---|---|
| `GET` | `/` | Liste paginée (`PageDTO`) + filtres (nom, matricule, statut) | ✅ LECTURE |
| `GET` | `/options` | Liste ultra-légère pour Select/Modals (id, matricule, nom) | ✅ LECTURE |
| `GET` | `/{id}` | Fiche complète d'un employé (toutes tables satellites) | ✅ LECTURE |
| `POST` | `/` | Créer un nouvel employé | ✅ ÉCRITURE |
| `PUT` | `/{id}` | Modifier la fiche d'un employé | ✅ ÉCRITURE |
| `DELETE` | `/{id}` | Désactiver (soft delete) un employé | ✅ ÉCRITURE |
| `GET` | `/{id}/documents` | Lister les documents RH | ✅ LECTURE |
| `POST` | `/{id}/documents` | Uploader un document | ✅ ÉCRITURE |
| `DELETE` | `/{id}/documents/{docId}` | Supprimer un document | ✅ ÉCRITURE |
| `GET` | `/{id}/photo` | Récupérer la photo | ✅ LECTURE |
| `POST` | `/{id}/photo` | Uploader la photo de profil | ✅ ÉCRITURE |

### Paramètres de Filtrage (GET `/`)

| Paramètre | Type | Description |
|---|---|---|
| `q` | String | Recherche full-text (nom, prénom, matricule) |
| `estActif` | Boolean | Filtrer actifs / inactifs |
| `idFiliale` | Integer | Filtrer par filiale |
| `idDepartement` | Integer | Filtrer par département |
| `page` | Integer | Pagination (défaut: 0) |
| `size` | Integer | Taille de page (défaut: 20) |

---

## 7. Sécurité & Contrôle d'Accès

### 7.1 RBAC (Role-Based Access Control)

L'accès au module Employé est contrôlé par le système RBAC dynamique (`role_permission`). Pour le module `EMPLOYE` :

| Action | Droit requis |
|---|---|
| Consulter la liste & fiches | `droit_lecture` |
| Créer / Modifier un dossier | `droit_ecriture` |
| Désactiver un employé | `droit_ecriture` |
| Valider des documents | `droit_validation` |

### 7.2 Filtrage par Périmètre

Un utilisateur peut être restreint à un **périmètre organisationnel** (filiale, site, département). Dans ce cas, les requêtes de liste filtrent automatiquement les employés selon le périmètre accordé au rôle.

### 7.3 Audit Log

Chaque modification sur un dossier employé génère une entrée dans la table `audit_log` avec :
- `id_utilisateur` : auteur de l'action
- `action` : `CREATE_EMPLOYE`, `UPDATE_EMPLOYE`, `DEACTIVATE_EMPLOYE`...
- `anciennes_valeurs` : sérialisation JSON de l'état avant modification
- `nouvelles_valeurs` : sérialisation JSON de l'état après modification
- `date_action` : horodatage précis
- `adresse_ip` : traçabilité réseau

---

## 8. Expérience Utilisateur

### 8.1 Interface — Vue Liste

La vue liste des employés présente :
- **Tableau paginé** avec colonnes : Photo, Matricule, Nom & Prénom, Filiale, Poste actuel, Statut (badge Actif/Inactif).
- **Barre de recherche** full-text avec résultats en temps réel (debounce 300ms).
- **Filtres rapides** : par filiale, par statut actif/inactif.

### 8.2 Interface — Fiche Employé (Onglets)

La fiche employé adopte une navigation par **onglets** pour organiser la densité d'information :

| Onglet | Contenu |
|---|---|
| **Identité** | Informations civiles, CIN, coordonnées, photo |
| **Famille** | Conjoint, enfants, contacts d'urgence |
| **Carrière** | Contrats, affectations, historique de poste |
| **Congés** | Solde de congés, historique des demandes |
| **Documents** | Pièces justificatives uploadées |
| **Banque** | Comptes bancaires enregistrés |

### 8.3 Comportements Clés de l'Interface

- **Badge de statut dynamique** : Le badge `ACTIF` / `INACTIF` en en-tête de fiche se met à jour instantanément après une action RH (validation de contrat, rupture), sans rechargement de page.
- **Validation en temps réel** : Les champs obligatoires affichent des erreurs de validation inline sans attendre la soumission du formulaire.
- **Chargement progressif** : La fiche centrale charge d'abord les informations de base, puis les données des onglets à la demande (lazy loading) pour améliorer la réactivité perçue.

---

*Document généré pour mémoire de fin d'études — GALAXAM SIRH 2026*
