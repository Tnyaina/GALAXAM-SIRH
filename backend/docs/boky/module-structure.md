# Module Structure Organisationnelle — Documentation Technique & Fonctionnelle
## GALAXAM SIRH — Mémoire de Fin d'Études

> **Auteur** : Équipe de Développement GALAXAM SIRH  
> **Module** : Gestion de la Structure d'Entreprise & Configuration Organisationnelle  
> **Version** : 1.0.0  
> **Date** : 2026

---

## Table des Matières

1. [Présentation Générale](#1-présentation-générale)
2. [Modèle de Données](#2-modèle-de-données)
3. [Règles de Gestion (RG)](#3-règles-de-gestion)
4. [Configuration des Sites & Calendriers](#4-configuration-des-sites--calendriers)
5. [Référentiels Fiscaux & Sociaux](#5-référentiels-fiscaux--sociaux)
6. [Architecture Technique](#6-architecture-technique)
7. [API REST — Endpoints](#7-api-rest--endpoints)
8. [Interdépendances avec les Autres Modules](#8-interdépendances-avec-les-autres-modules)

---

## 1. Présentation Générale

### 1.1 Rôle du Module

Le **module Structure** est le **socle de configuration** du SIRH GALAXAM. Il représente l'architecture organisationnelle de l'entreprise GALAXAM Logistics et de ses filiales. Tous les autres modules (Employé, Contrat, Congé, Paie) s'appuient sur les référentiels définis ici.

Ce module permet de :
- Modéliser la **hiérarchie organisationnelle** : Filiales → Sites → Départements → Services → Fonctions.
- Configurer les **paramètres opérationnels** de chaque site (horaires, indemnités, cotisations sociales).
- Gérer les **calendriers de jours fériés** par site ou à l'échelle nationale.
- Maintenir les **paramètres globaux** du système (SME, seuils IRSA, plafonds de congés...).
- Gérer les **référentiels fiscaux** (barème IRSA, cotisations sociales).

### 1.2 Périmètre Fonctionnel

| Référentiel | Description | Exemples |
|---|---|---|
| **Filiale** | Entités juridiques du groupe | GALAXAM Logistics, Vina sy Asa |
| **Site** | Implantations géographiques | Antananarivo (TNR), Toamasina (TMM) |
| **Département** | Grandes directions | LOG (Logistique), LMD (Projet) |
| **Service** | Subdivisions fonctionnelles | ADM (Administration), OPE (Opérations) |
| **Fonction** | Postes du catalogue global | DG, DAF, Chef Déclarant, OPE Manager |
| **Type de Contrat** | Nature des engagements | CDI, CDD, PRESTATION, STAGE |
| **Type d'Intervenant** | Catégories RH | PERMANENT, CONSULTANT, TEMPORAIRE |
| **Banque** | Institutions bancaires partenaires | BOA, BNI, BFV-Société Générale |

---

## 2. Modèle de Données

### 2.1 Hiérarchie Organisationnelle

```
GROUPE GALAXAM
    │
    ├── ref_filiale (Entité Juridique)
    │       code_filiale, raison_sociale, NIF, STAT
    │       seuil_effectif_alerte (seuil de déclaration légale)
    │
    └── ref_site (Implantation Géographique)
            code_site, libelle
            id_filiale_rattachement → ref_filiale
            horaire_continu (true=TNR, false=TMM)
            heure_debut_matin / heure_fin_matin
            heure_debut_apres_midi / heure_fin_apres_midi
    
CATALOGUE DE FONCTIONS (transversal)
    ├── ref_departement  — Directions (peuvent être temporaires/projet)
    ├── ref_service      — Sous-divisions
    └── ref_fonction     — Catalogue global de postes
```

### 2.2 Table `ref_filiale`

```sql
Table ref_filiale {
  id_filiale          INTEGER   PK
  code_filiale        VARCHAR(10) UNIQUE -- ex: 'LOG', 'VA'
  raison_sociale      VARCHAR(100) NOT NULL
  nif                 VARCHAR(30)  -- Numéro d'Identification Fiscale
  stat                VARCHAR(30)  -- Numéro STAT (Madagascar)
  seuil_effectif_alerte INTEGER DEFAULT 19
  est_actif           BOOLEAN DEFAULT true
  date_desactivation  TIMESTAMP
  created_at          TIMESTAMP
}
```

> **Note clé** : Le `seuil_effectif_alerte` à 19 correspond au **seuil légal de l'inspection du travail malgache**. Au-delà de 19 salariés sur un même site/filiale, certaines obligations légales supplémentaires s'appliquent. Le SIRH génère une alerte automatique lorsque ce seuil est atteint.

### 2.3 Table `ref_departement`

```sql
Table ref_departement {
  id_departement      INTEGER   PK
  code_departement    VARCHAR(10) UNIQUE -- ex: 'LOG', 'LMD'
  libelle             VARCHAR(100) NOT NULL
  date_fin_prevue     DATE  -- NULL = permanent ; renseigné si département projet
  est_actif           BOOLEAN DEFAULT true
}
```

> **Distinction conceptuelle** : Un département peut être **permanent** (ex: LOG — Logistique, qui existera tant que la société opère) ou **temporaire/projet** (ex: LMD — créé pour la durée d'un projet spécifique, avec une `date_fin_prevue`).

### 2.4 Table `ref_site`

Le site est l'unité géographique opérationnelle. Il porte la configuration des **horaires de travail** et sert de base pour le calcul des indemnités :

```sql
Table ref_site {
  id_site               INTEGER PK
  code_site             VARCHAR(10) UNIQUE
  libelle               VARCHAR(100)
  id_filiale_rattachement INTEGER → ref_filiale
  horaire_continu       BOOLEAN DEFAULT true
  -- Horaires (configuration de la pointeuse)
  heure_debut_matin     TIME
  heure_fin_matin       TIME
  heure_debut_apres_midi TIME
  heure_fin_apres_midi  TIME
  est_actif             BOOLEAN DEFAULT true
}
```

**Types d'horaires configurés** :
- `horaire_continu = TRUE` (TNR — Antananarivo) : plage horaire continue avec pause repas, indemnité repas accordée par défaut.
- `horaire_continu = FALSE` (TMM — Toamasina) : journée de travail sans pause repas officielle, pas d'indemnité repas par défaut.

---

## 3. Règles de Gestion

### RG-STR-01 : Unicité des Codes de Référentiel

Tous les référentiels organisationnels (filiale, site, département, service, fonction) imposent une **contrainte d'unicité** sur leur code :
- Les codes sont normalisés en **MAJUSCULES** automatiquement lors de la création/modification.
- Toute tentative de création d'un doublon de code est rejetée par une exception métier explicite.
- Les codes ne peuvent pas être modifiés une fois attribués (ils servent de clé de liaison dans les systèmes intégrés).

### RG-STR-02 : Désactivation Logique des Référentiels

Comme pour les employés, les entités organisationnelles ne sont **jamais supprimées physiquement** de la base de données. Elles sont désactivées via `est_actif = FALSE` et `date_desactivation`.

**Règle de sécurité** : Une filiale ou un site ne peut être désactivé que si **aucun employé actif** n'y est rattaché. Une vérification préalable est effectuée avant tout soft-delete.

### RG-STR-03 : Seuil d'Effectif de la Filiale

Le champ `seuil_effectif_alerte` sur `ref_filiale` détermine le **plafond d'effectif** à surveiller :
- Valeur par défaut : **19 employés** (seuil légal malgache des obligations employeur renforcées).
- Le tableau de bord affiche l'effectif actuel vs ce seuil.
- Le SIRH peut générer une notification lorsque l'effectif comptabilisé (`comptabilise_effectif = TRUE` sur le contrat) approche ou dépasse ce seuil.

### RG-STR-04 : Comptabilisation de l'Effectif

Pas tous les contrats ne sont comptabilisés dans l'effectif légal :
- `contrat_travail.comptabilise_effectif = TRUE` (défaut) : le salarié compte dans l'effectif de la filiale.
- `contrat_travail.comptabilise_effectif = FALSE` : le salarié ne compte **pas** dans l'effectif (cas des prestations externes, consultants ponctuels).

---

## 4. Configuration des Sites & Calendriers

### 4.1 Cascade des Indemnités (3 niveaux)

Le système applique une **cascade à 3 niveaux** pour déterminer si un employé bénéficie d'une indemnité (ex: repas, déplacement) un jour donné :

```
NIVEAU 1 — site_indemnite_config
├── Configuration par défaut du site
├── Ex: TNR → indemnité repas = 3 500 Ar/jour
└── Ex: TMM → aucune ligne = pas d'indemnité repas par défaut

NIVEAU 2 — employe_indemnite_derogation
├── Dérogation individuelle (peut accorder ou retirer une indemnité)
├── Ex: Le DAF basé à TMM bénéficie quand même du repas (beneficie=TRUE)
└── Ex: Double emploi → indemnité repas retirée (beneficie=FALSE)

NIVEAU 3 — pointage_journalier
├── Ajustement manuel jour par jour
├── Case à cocher pré-remplie selon niveaux 1 & 2
└── Modifiable manuellement avec motif optionnel
```

### 4.2 Table `site_indemnite_config`

```sql
Table site_indemnite_config {
  id_site_indemnite   INTEGER PK
  id_site             INTEGER FK → ref_site
  id_indemnite        INTEGER FK → ref_indemnite  -- ex: REPAS, DEPLACEMENT
  montant_jour_reference DECIMAL(12,2)  -- Montant par jour ouvrable
  date_debut          DATE NOT NULL  -- Historisation du montant
  date_fin            DATE           -- NULL = valeur actuelle
  est_actif           BOOLEAN DEFAULT true
}
```

> L'historisation (`date_debut` / `date_fin`) permet de **tracer les revalorisations** des montants d'indemnités dans le temps, sans perdre l'historique de calcul des paies passées.

### 4.3 Calendrier des Jours Fériés

```sql
Table ref_calendrier_ferie {
  id_ferie     INTEGER PK
  date_ferie   DATE NOT NULL
  libelle      VARCHAR(100)  -- ex: 'Fête Nationale (26 juin)'
  est_chome    BOOLEAN DEFAULT true
  est_national BOOLEAN DEFAULT true  -- true = s'applique à tous les sites
}

Table site_calendrier_ferie {
  -- Dérogation locale : permet d'exclure un jour férié national pour un site
  id_site_ferie INTEGER PK
  id_site       INTEGER FK → ref_site
  id_ferie      INTEGER FK → ref_calendrier_ferie
}
```

Les jours fériés sont utilisés par :
- Le module **Pointage** : pour calculer les majorations fériées.
- Le module **Congés** : pour le décompte calendaire strict.
- Le scheduler de **notifications** : pour anticiper les alertes de fin de contrat.

### 4.4 Paramètres Globaux (`parametre_global`) & Exercices Fiscaux (`paie_config_legale`)

Afin de garantir une stricte séparation entre les **paramètres opérationnels RH/Pointage** et les **constantes légales/fiscales évolutives**, le système s'appuie sur deux mécanismes distincts :

#### 1. Paramètres Opérationnels (`parametre_global`)
Centralise les paramètres de calcul opérationnels non soumis à la Loi de Finances annuelle :
* **`RH_GENERAL`** : Durée normale quotidienne (`SEUIL_HEURES_JOUR` = 8h), durée hebdomadaire normale (`SEUIL_HEURES_SEMAINE` = 40h), seuil HS tranche 1 (`SEUIL_HS_TRANCHE_1` = 8h), plafond HS hebdo (`PLAFOND_HEURES_SUPP_SEMAINE` = 20h).
* **`POINTAGE`** : Début nuit (`SEUIL_NUIT_DEBUT` = 22:00), fin nuit (`SEUIL_NUIT_FIN` = 05:00), indemnité repas (`MONTANT_REPAS_MIDI` = 2 000 Ar), tranches 2nd repas (19:30 à 2 000 Ar, 21:00 à 4 000 Ar), détection pause minimale (60 min).
* **`CONGE`** : Durées légales maternité (98j / 119j), plafonds solde négatif (-10j), préavis de dépôt (2j / 15j) et seuils d'alertes.

#### 2. Millésimes Fiscaux & Sociaux (`paie_config_legale`)
Gère l'historique immuable de la législation malgache par version (ex: `LF_2024`, `LF_2026`) :
* **SME National & Agricole** (262 680 Ar / 266 500 Ar en 2026).
* **Plafond Social CNaPS/Santé** : $8 \times \text{SME} = 2\,101\,440\text{ Ar}$.
* **Règles IRSA** : Seuil d'exonération (350 000 Ar), minimum de perception (3 000 Ar), déduction par charge de famille (2 000 Ar/personne).
* **Temps de travail & Congés** : Diviseur moyen mensuel ($173.3333\text{ h}$), acquisition mensuelle ($2.5\text{ j}$), diviseur de valorisation des congés ($\div 30$).

> 📖 Pour la spécification complète de ce système, consulter : [`module-paie-reglementation-fiscale.md`](./module-paie-reglementation-fiscale.md).

## 5. Référentiels Fiscaux & Sociaux

### 5.1 Types de Cotisations Sociales

```sql
Table ref_type_cotisation {
  id_type_cotisation  INTEGER PK
  code                VARCHAR(20) UNIQUE  -- ex: 'CNAPS', 'OSTIE', 'OMSI', 'ISI'
  libelle             VARCHAR(100)
  taux_salarial       DECIMAL(5,2)  -- ex: 1.00 pour OSTIE (part employé)
  taux_patronal       DECIMAL(5,2)  -- ex: 5.00 pour OSTIE (part employeur)
  est_plafonnee       BOOLEAN       -- TRUE = plafonnée à 8x SME
  date_debut          DATE NOT NULL -- Historisation des taux
  date_fin            DATE          -- NULL = taux en vigueur
}
```

**Cotisations obligatoires à Madagascar** :

| Code | Organisme | Taux Salarial | Taux Patronal | Plafond |
|---|---|---|---|---|
| `CNAPS` | Caisse Nationale de Prévoyance Sociale | — | Voir CNAPS | 8x SME |
| `OSTIE` | Organisation Sanitaire Tananarivienne | 1,00% | 5,00% | Oui |
| `OMSI` | Organisme de Médecine du Service | 1,50% | 5,50% | Oui |
| `ISI_5` | ISI (Consultants uniquement) | 5,00% | — | Non |

### 5.2 Barème IRSA (Progressif par Tranches)

L'IRSA (Impôt sur le Revenu Salarial) est calculé selon un **barème progressif à tranches** :

```sql
Table bareme_irsa {
  id_tranche    INTEGER PK
  numero_tranche INTEGER  -- Ordre de la tranche (1 à 6)
  tranche_min   DECIMAL(12,2) NOT NULL
  tranche_max   DECIMAL(12,2)           -- NULL = dernière tranche, sans plafond
  taux          DECIMAL(5,2) NOT NULL   -- ex: 5.00, 10.00, 15.00...
  date_debut    DATE NOT NULL           -- Historisation
  date_fin      DATE                    -- NULL = barème actuellement en vigueur
}
```

> **Pourquoi une table séparée de `ref_type_cotisation` ?** L'IRSA est un barème progressif (le taux varie selon la tranche de revenu), alors que CNAPS/OSTIE sont des taux fixes. Une table spécifique `bareme_irsa` est nécessaire pour modéliser cette structure en tranches.

### 5.3 Référentiel des Indemnités

```sql
Table ref_indemnite {
  id_indemnite  INTEGER PK
  code          VARCHAR(20) UNIQUE  -- ex: 'REPAS', 'DEPLACEMENT'
  libelle       VARCHAR(100)
  imposable_irsa BOOLEAN DEFAULT false  -- TRUE = s'intègre dans l'assiette IRSA
}
```

### 5.4 Types d'Intervenants & Régime Social

```sql
Table ref_type_intervenant {
  id_type_intervenant INTEGER PK
  code                VARCHAR(20) UNIQUE  -- PERMANENT, CONSULTANT, TEMPORAIRE
  libelle             VARCHAR(100)
  soumis_irsa         BOOLEAN DEFAULT true
  soumis_isi_5        BOOLEAN DEFAULT false  -- ISI 5% pour consultants
  soumis_cnaps        BOOLEAN DEFAULT true
  soumis_sante        BOOLEAN DEFAULT true
}
```

Le type d'intervenant détermine automatiquement le régime de cotisations sociales applicable, évitant toute saisie manuelle des taux par dossier.

---

## 6. Architecture Technique

### 6.1 Contrôleurs REST

| Contrôleur | URL de Base | Gestion |
|---|---|---|
| `OrganisationController` | `/api/organisation` | Filiales, Sites, Départements, Services, Fonctions |
| `PaieConfigController` | `/api/paie-config` | Indemnités, Cotisations, Barème IRSA, Paramètres |

### 6.2 Modèles Entité

| Classe Java | Table PostgreSQL | Description |
|---|---|---|
| `RefFiliale` | `ref_filiale` | Entités juridiques |
| `RefSite` | `ref_site` | Sites géographiques |
| `RefDepartement` | `ref_departement` | Départements |
| `RefService` | `ref_service` | Services |
| `RefFonction` | `ref_fonction` | Catalogue de fonctions |

### 6.3 Validation & Contrôles

Chaque entité de référentiel implémente `controler(Connection c)` qui :
1. Vérifie la **présence des champs obligatoires**.
2. Normalise le code en **MAJUSCULES**.
3. Vérifie l'**absence de doublon** sur le code unique avant insertion/modification.

---

## 7. API REST — Endpoints

### Référentiels Organisationnels
Base URL : `/api/organisation`

| Méthode | URL | Description |
|---|---|---|
| `GET` | `/filiales` | Liste toutes les filiales |
| `POST` | `/filiales` | Créer une filiale |
| `PUT` | `/filiales/{id}` | Modifier une filiale |
| `DELETE` | `/filiales/{id}` | Désactiver une filiale |
| `GET` | `/sites` | Liste tous les sites |
| `POST` | `/sites` | Créer un site |
| `GET` | `/departements` | Liste tous les départements |
| `GET` | `/services` | Liste tous les services |
| `GET` | `/fonctions` | Liste toutes les fonctions |

### Référentiels de Configuration Paie
Base URL : `/api/paie-config`

| Méthode | URL | Description |
|---|---|---|
| `GET` | `/indemnites` | Liste des types d'indemnités |
| `GET` | `/cotisations` | Liste des types de cotisations |
| `GET` | `/bareme-irsa` | Barème IRSA actuel |
| `GET` | `/parametres` | Paramètres globaux actuels |
| `PUT` | `/parametres/{code}` | Mettre à jour un paramètre |

---

## 8. Interdépendances avec les Autres Modules

Le module Structure est le **référentiel central** consommé par tous les autres modules. Voici les dépendances :

```
MODULE STRUCTURE fournit à :
    │
    ├── MODULE EMPLOYÉ
    │   └── ref_banque → employe_compte_banque
    │
    ├── MODULE CONTRAT
    │   ├── ref_filiale     → contrat_travail.id_filiale
    │   ├── ref_type_contrat → contrat_travail.id_type_contrat
    │   ├── ref_type_intervenant → contrat_travail.id_type_intervenant
    │   ├── ref_site        → employe_affectation_carriere.id_site
    │   ├── ref_departement → employe_affectation_carriere.id_departement
    │   ├── ref_service     → employe_affectation_carriere.id_service
    │   └── ref_fonction    → employe_affectation_carriere.id_fonction
    │
    ├── MODULE CONGÉ
    │   ├── ref_calendrier_ferie → calcul décompte calendaire
    │   └── parametre_global    → seuils et plafonds de gestion
    │
    └── MODULE PAIE (futur)
        ├── ref_type_cotisation → calcul des charges sociales
        ├── bareme_irsa        → calcul IRSA progressif
        ├── ref_indemnite      → valorisation des indemnités
        └── site_cotisation    → cotisations applicables par site
```

> **Bonne pratique d'architecture** : Ce pattern de référentiel centralisé (appelé aussi *Master Data Management* ou MDM) garantit la **cohérence des données** à travers tous les modules. Une modification du libellé d'un département se répercute automatiquement partout sans manipulation de données transactionnelles.

---

*Document généré pour mémoire de fin d'études — GALAXAM SIRH 2026*
