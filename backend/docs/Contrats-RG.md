# Documentation Technique & Métier : Gestion des Contrats et des Parcours de Carrière (Galaxam SIRH)

> **Document de Référence & Mémoire Académique**  
> **Auteur** : Équipe de Développement Galaxam SIRH  
> **Module** : Gestion des RH, Contrats de Travail & Mouvements de Carrière  
> **Version** : 1.0.0 (Spécification Officielle)

---

## Table des Matières

1. [Présentation Générale & Vision Produit](#1-présentation-générale--vision-produit)
2. [Architecture du Système & Modèle de Données](#2-architecture-du-système--modèle-de-données)
3. [Spécification des Règles de Gestion (RG)](#3-spécification-des-règles-de-gestion-rg)
   - [RG-01 : Embauche Initiale & Création de Contrat](#rg-01--embauche-initiale--création-de-contrat)
   - [RG-02 : Période d'Essai (Confirmation & Renouvellement)](#rg-02--période-dessai-confirmation--renouvellement)
   - [RG-03 : Mouvements & Transitions RH (Promotions, Mutations, CDD → CDI)](#rg-03--mouvements--transitions-rh-promotions-mutations-cdd--cdi)
   - [RG-04 : Rupture, Fin de Contrat & Cascadage du Statut Employé](#rg-04--rupture-fin-de-contrat--cascadage-du-statut-employé)
   - [RG-05 : Certificat de Travail Officiel & Génération Documentaire](#rg-05--certificat-de-travail-officiel--génération-documentaire)
4. [Workflow RH & Circuit d'Approbation DAF / DRH](#4-workflow-rh--circuit-dapprobation-daf--drh)
5. [Traçabilité, Sécurité & Audit](#5-traçabilité-sécurité--audit)
6. [Design & Expérience Utilisateur (IHM Split-Screen)](#6-design--expérience-utilisateur-ihm-split-screen)

---

## 1. Présentation Générale & Vision Produit

Le module de **Gestion des Contrats & Parcours de Carrière** de **Galaxam SIRH** constitue le cœur opérationnel de la gestion du capital humain. Il permet de piloter l'intégralité du cycle de vie d'un salarié au sein de l'entreprise, depuis son recrutement initial jusqu'à son départ définitif (Offboarding).

### Objectifs Majeurs :
1. **Conformité au Droit du Travail** : Respect strict des règles légales concernant la durée des contrats, les périodes d'essai et la délivrance des documents officiels.
2. **Traçabilité Intégrale** : Conservation de l'historique immuable de tous les contrats, avenants, mutations, promotions et rémunérations.
3. **Workflow de Validation à Double Rôle** : Séparation claire entre la préparation des dossiers en brouillon (Assistants RH / Managers) et la validation définitive (Direction RH / DAF).
4. **Automatisation du Cascadage d'État** : Synchronisation en temps réel de l'état actif/inactif du profil salarié en fonction de ses contrats en cours.

---

## 2. Architecture du Système & Modèle de Données

Le système repose sur un schéma relationnel PostgreSQL robuste et optimisé, structuré autour de 4 tables principales et d'une vue unifiée :

```
                  ┌──────────────────────┐
                  │       EMPLOYE        │
                  │ ──────────────────── │
                  │ id_employe (PK)      │
                  │ matricule            │
                  │ est_actif (BOOLEAN)  │
                  └──────────┬───────────┘
                             │ 1
                             │
                             │ N
                  ┌──────────┴───────────┐
                  │   CONTRAT_TRAVAIL    │
                  │ ──────────────────── │
                  │ id_contrat (PK)      │
                  │ numero_contrat       │
                  │ id_statut_contrat    │ ───► ref_statut_contrat (ACTIF, ROMPU, CLOTURE, BROUILLON)
                  │ date_debut           │
                  │ date_fin             │
                  └──────────┬───────────┘
                             │ 1
                             │
                             │ N
                  ┌──────────┴───────────┐
                  │ EMPLOYE_AFFECTATION  │
                  │ ──────────────────── │
                  │ id_affectation (PK)  │
                  │ id_statut_affectation│ ───► ref_statut_affectation (ACTIVE, HISTORISEE, PLANIFIEE)
                  │ salaire_base         │
                  │ date_debut / date_fin│
                  └──────────┬───────────┘
                             │
                             ▲ (Rattachement historique)
                             │
                  ┌──────────┴───────────┐
                  │     WORKFLOW_RH      │
                  │ ──────────────────── │
                  │ id_workflow (PK)     │
                  │ id_type_evenement_rh │ ───► ref_type_evenement_rh (EMBAUCHE, PROMOTION, DEMISSION...)
                  │ id_statut_workflow   │ ───► ref_statut_workflow (BROUILLON, VALIDE, ANNULE, PLANIFIE)
                  │ date_effet           │
                  └──────────────────────┘
```

### Vue Unifiée `v_workflow_rh_cpl`
Une vue SQL complète agrège en temps réel les libellés juridiques, organisationnels (Filiale, Site, Département, Service, Fonction, Catégorie Pro) et financiers afin d'alimenter les tableaux de bord et la récapitulation sans requêtes multiples.

---

## 3. Spécification des Règles de Gestion (RG)

### RG-01 : Embauche Initiale & Création de Contrat

* **Description** : Création du premier contrat de travail lors du recrutement d'un salarié.
* **Typologies de Contrats gérés** : CDI (Contrat à Durée Indéterminée), CDD (Contrat à Durée Déterminée), Stage, Prestation/Consultant.
* **Champs Obligatoires** : Filiale Employeuse, Type de Contrat, Prise d'effet (Date de début), Site/Implantation, Département, Service, Fonction/Poste, Salaire de Base.
* **Effet de la Validation** :
  1. Le contrat passe au statut `ACTIF`.
  2. L'affectation créée passe au statut `ACTIVE`.
  3. Le profil de l'employé bascule au statut **`est_actif = TRUE`**.

---

### RG-02 : Période d'Essai (Confirmation & Renouvellement)

* **Définition & Durée** : Tout contrat CDI ou CDD peut comporter une période d'essai (ex: 2 mois pour un employé, 3 à 4 mois pour un cadre).
* **Évaluation Dynamique & Isolation par Contrat** : 
  - L'état de confirmation ou de renouvellement de l'essai est **strictement évalué par identifiant de contrat actif** (`activeContratId`). La confirmation d'un ancien contrat clôturé n'interfère jamais avec un nouveau contrat en cours.
  - Le système calcule automatiquement l'état de l'essai : `EN_COURS`, `ECHEANCE_PROCHE` (< 15j), ou `EXPIRE`.
* **Indépendance des Brouillons** :
  - La présence d'un brouillon non lié à la période d'essai (ex: brouillon de clôture ou de revalorisation salariale) **ne masque pas** les boutons d'action de confirmation ou de renouvellement de l'essai.
* **Règle du Renouvellement Unique (Code du Travail)** :
  - La période d'essai ne peut être renouvelée **qu'une seule fois**.
  - Si un renouvellement a déjà été effectué (`renouvele = true`), toute nouvelle tentative de renouvellement est **bloquée par l'interface** (Badge d'avertissement : *"2ème Période atteinte — Max légal d'essai atteint"*).
* **Confirmation d'Essai** : Passe formellement la période d'essai au statut confirmé et archive la décision dans l'historique du collaborateur.

---

### RG-03 : Mouvements & Transitions RH (Promotions, Mutations, CDD → CDI)

* **Principe de Continuité Contractuelle & Chaînage** : Un changement de contrat (ex: Stage ➔ CDD, CDD ➔ CDI) génère un nouveau contrat qui enregistre la filiation vers le contrat d'origine via `id_contrat_precedent`.
* **Automatisme de Transition Temporelle** :
  1. Lorsqu'un mouvement prend effet à la date $D_{effet}$ :
     $$\text{Date de fin de l'ancienne affectation} = D_{effet} - 1 \text{ jour}$$
  2. L'ancienne affectation passe au statut **`HISTORISEE`**.
  3. La nouvelle affectation devient **`ACTIVE`**.
* **Événements à Date d'Effet Future** :
  - Si $D_{effet} > D_{actuelle}$, le workflow est enregistré avec le statut **`PLANIFIE`**.
  - Le contrat et l'affectation restent **100% actifs** jusqu'à la date d'effet.
  - Le planificateur nocturne de tâches (Scheduler `00:00:00`) applique automatiquement les modifications le matin de la date d'effet.

---

### RG-04 : Rupture, Fin de Contrat & Cascadage du Statut Employé

* **Typologies de Rupture** : `DEMISSION` (Salarié), `LICENCIEMENT` (Employeur), `RUPTURE_CONVENTIONNELLE`, `FIN_CONTRAT` (Terme CDD), `RUPTURE_ESSAI`, `DEPART_RETRAITE`.
* **Gestion du Préavis** :
  - Saisie de la durée du préavis (ex: 1 Mois, 3 Mois) et de la date de notification.
  - Case à cocher : *Préavis effectué par le salarié* ou *Préavis dispensé/compensé*.
* **Rupture à Date Future** :
  - Si la date d'effet de la rupture est future ($D_{effet} > D_{actuelle}$), l'employé et son contrat demeurent au statut `ACTIF` / `est_actif = TRUE` jusqu'au jour $D_{effet}$.
* **Règle de Cascadage Automatique du Statut Employé (Offboarding)** :
  Lors de l'application effective d'une rupture :
  1. Le contrat source est mis à jour : `id_statut_contrat = ROMPU` (ou `CLOTURE`), avec la `date_fin` égale à la date de rupture.
  2. L'affectation courante bascule à `HISTORISEE`.
  3. **Vérification de l'existence d'autres contrats actifs** :

$$\text{Reste-t-il au moins un autre contrat ACTIF pour cet employé ?} \quad \Longrightarrow \quad \begin{cases} \mathbf{OUI} & \longrightarrow \text{Employé reste } \texttt{est\_actif = TRUE} \\ \mathbf{NON} & \longrightarrow \text{Employé passe automatiquement } \texttt{est\_actif = FALSE} \end{cases}$$

---

### RG-05 : Certificat de Travail Officiel & Génération Documentaire

* **Exigence Légale** : À la clôture ou rupture d'un contrat (`CLOTURE` ou `ROMPU`), l'employeur est tenu de délivrer immédiatement un **Certificat de Travail**. Le bouton de génération est réservé aux contrats effectivement clôturés ou rompus.
* **Calcul de l'Ancienneté Globale (Code du Travail)** :
  - En cas de contrats successifs sans interruption (ex: Stage ➔ CDD ➔ CDI), l'ancienneté attestée sur le certificat est calculée depuis la **Date d'Entrée initiale** (`dateDebutInitiale` du premier contrat de la chaîne).
* **Mentions Obligatoires Générées** :
  - Raison sociale, adresse, NIF et STAT de la filiale employeuse.
  - Nom, prénom, domicile, N° CIN, N° CNaPS et Matricule interne du salarié.
  - Date d'entrée initiale et Date de sortie effective.
  - **Calcul Automatique d'Ancienneté Globale** (Format lisible : *"X an(s) Y mois"*).
  - Intitulé exact de la fonction et de la catégorie professionnelle occupées.
  - Clause d'exonération : *"Salarié libre de tout engagement contractuel"*.
  - Effet non libératoire (Ne vaut pas solde de tout compte).
* **Technologie d'Export & Impression** :
  - Génération via composant dédié `CertificatTravailModal.jsx` monté par **React Portal** directement sur le `document.body`.
  - Feuille de style dédiée `@media print` avec format **A4 Portrait**, masquant totalement l'application web `#root` pour offrir un rendu PDF/Impression professionnel prêt à signer.

---

## 4. Workflow RH & Circuit d'Approbation DAF / DRH

Tout mouvement contractuel ou rupture suit un cycle de vie contrôlé :

```
    ┌────────────────┐
    │   BROUILLON    │  ───► Créé par l'Assistant RH / Manager
    └───────┬────────┘
            │
            ├──────────────────────────┐
            ▼ (Validation DAF / DRH)   ▼ (Annulation)
    ┌────────────────┐         ┌────────────────┐
    │     VALIDE     │         │    ANNULE      │
    └───────┬────────┘         └────────────────┘
            │
            ├──────────────────────────┐
            ▼ (Si date effet <= Auj.)  ▼ (Si date effet > Auj.)
    ┌────────────────┐         ┌────────────────┐
    │    APPLIQUE    │         │    PLANIFIE    │
    └────────────────┘         └────────────────┘
```

1. **Création du Brouillon** : Le mouvement est saisi sans impact immédiat sur le contrat ou l'employé.
2. **Relecture & Correction** : Le bouton `[ CORRIGER ]` rouvre la modale spécifique (`EmbaucheModal`, `TransitionModal` ou `RuptureContratModal`) pré-remplie avec les données saisies.
3. **Validation & Application** : Le clic sur `[ VALIDER LE MOUVEMENT RH ]` exécute la transaction SQL en une seule opération atomique (Commit / Rollback).

---

## 5. Traçabilité, Sécurité & Audit

* **Audit Log Immuable** : Chaque changement d'état de workflow ou de contrat enregistre une entrée dans la table `audit_log` contenant l'utilisateur auteur, l'action, l'adresse IP et la sérialisation JSON `anciennes_valeurs` / `nouvelles_valeurs`.
* **Système de Notifications RH** : Des alertes métier (`BROUILLON_CREE`, `MOUVEMENT_APPLIQUE`, `ESSAI_EXPIRE_7J`) sont automatiquement insérées dans la table `notification_rh` et notifiées dans l'en-tête du SIRH.

---

## 6. Design & Expérience Utilisateur (IHM Split-Screen)

L'interface utilisateur adopte les standards ergonomiques les plus modernes :
* **Modale Split-Screen 50/50** :
  - *Côté gauche* : Formulaire de saisie dynamique avec validation en temps réel.
  - *Côté droit* : Prévisualisation fidèle du document papier (Contrat ou Lettre de rupture) avec filigrane et mise en page juridique.
* **Mise à jour Réactive Sans Rafraîchissement (F5)** : L'en-tête du profil collaborateur (Badge `ACTIF` / `INACTIF`) et les tableaux d'historique se mettent à jour instantanément grâce aux callbacks d'événements React.

---

*Fin du document de spécification — Galaxam SIRH 2026*
