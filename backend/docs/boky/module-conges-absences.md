# Module Congés & Absences — Spécification Complète & Rapport de Référence
## GALAXAM SIRH — Documentation Canonique & Métier

> **Auteur** : Équipe d'Ingénierie & Architecture GALAXAM SIRH
> **Module** : Congés, Absences, Droits Acquis, Grand-Livre (Ledger) & Valorisation Financière
> **Version** : 3.2.0 (Post-Sprint 3 — Certification Fonctionnelle & Transactionnelle Réussie — Verdict `CERTIFIED`)
> **Date de référence** : Septembre 2026
> **Sources juridiques et normatives de référence** :
> - **Loi n° 2003-044 du 28 juillet 2004** portant Code du Travail de Madagascar (Articles 13, 33, 85, 86, 87, 88, 89, 90, 91, 95, 97, 98, 126, 134)
> - **Décret n° 2007-563 du 03 juillet 2007** relatif à la durée légale du travail (40 heures hebdomadaires = 173,33 heures mensuelles, diviseur horaire officiel)
> - **Décret n° 64-162 du 22 avril 1964** fixant les conditions et modalités d'application du régime des congés payés
> - **Modèle mathématique officiel d'acquisition et valorisation financière** : `OHATRA KAJY DROITS CONGES-given on sept26.xls`
> - **Schéma de base de données canonique** : `backend/conception/bdd/04_schema_conges.sql`
> - **Rapport de certification d'intégration réelle** : `CONGE_CERTIFICATION_REPORT.md` (32 tests backend JUnit 5 PostgreSQL réel, 3 tests frontend Vitest, 100% verts)

---

## Sommaire Exécutif

Le présent document constitue la **source unique de vérité documentaire, juridique et technique** du module **Congés & Absences** du SIRH GALAXAM.
Il unifie et verrouille définitivement :
1. Les **fondements juridiques malagasy stricts** (Code du Travail 2003-044 et décrets d'application) régissant l'acquisition des droits, les permissions exceptionnelles, les suspensions pour maladie et les congés de maternité/paternité.
2. Le **modèle mathématique d'acquisition proratisée et de valorisation financière** conforme aux feuilles de calcul d'entreprise et à la pratique de paie malagasy (`OHATRA KAJY DROITS CONGES`).
3. L'**architecture de persistance consolidée** (18 fichiers Java de production, zéro couche ORM JPA/Hibernate, exécution JDBC directe optimisée avec transactions pilotées par `TransactionRunner`).
4. Le **grand-livre comptable des congés (`mouvement_solde_conge`)**, garantissant la traçabilité intégrale de chaque centième de jour (imputation FIFO chronologique, purge triennale automatique, débits `PRISE`, crédits `ANNULATION` idempotents).
5. Les **règles de gestion métier (RG)** exhaustives couvrant l'intégralité du cycle de vie opérationnel, de la simulation préventive jusqu'à la liquidation au Solde de Tout Compte (STC).
6. Les **intégrations interconnectées réelles** certifiées avec les modules **Pointage**, **Paie** et **STC**.

---

## Table des Matières

1. [Cadre Légal et Réglementaire Malagasy](#1-cadre-légal-et-réglementaire-malagasy)
2. [Modèle Mathématique Canonique (OHATRA KAJY DROITS CONGES)](#2-modèle-mathématique-canonique-ohatra-kajy-droits-conges)
3. [Architecture du Modèle de Données PostgreSQL](#3-architecture-du-modèle-de-données-postgresql)
4. [Règles de Gestion Métier (RG) Détaillées](#4-règles-de-gestion-métier-rg-détaillées)
5. [Workflow & Circuit de Validation Transactionnel](#5-workflow--circuit-de-validation-transactionnel)
6. [Intégrations Certifiées : Pointage, Paie & STC](#6-intégrations-certifiées--pointage-paie--stc)
7. [Automatisations, Purge FIFO & Schedulers Nocturnes](#7-automatisations-purge-fifo--schedulers-nocturnes)
8. [Cartographie des Composants Logiciels (Backend & Frontend)](#8-cartographie-des-composants-logiciels-backend--frontend)
9. [Matrice des Codes d'Erreur & Réponses API Normalisées](#9-matrice-des-codes-derreur--réponses-api-normalisées)
10. [Corrections Critiques & Points d'Audit du Sprint 3](#10-corrections-critiques--points-daudit-du-sprint-3)
11. [Recueil Textuel Intégral des Articles Légaux (Loi n° 2003-044)](#11-recueil-textuel-intégral-des-articles-légaux-loi-n-2003-044)
12. [Données d'Audit Intégrales du Modèle Excel Réel (Matricule 61)](#12-données-daudit-intégrales-du-modèle-excel-réel-matricule-61)

---

## 1. Cadre Légal et Réglementaire Malagasy

### 1.1 Tableau Synoptique des Fondements Juridiques

Les congés payés sont régis par des dispositions d'**ordre public absolu** : toute clause contractuelle prévoyant l'abandon du congé contre indemnité (en cours de contrat) ou une durée inférieure aux seuils légaux est **nulle de plein droit**.

| Article de Loi / Décret | Libellé & Portée Juridique | Traduction Fonctionnelle & Technique dans GALAXAM SIRH |
| :--- | :--- | :--- |
| **Article 85** | **Durée du congé, jouissance et prescription**<br>• Acquisition : **2,5 jours calendaires par mois de service effectif** (soit 30 jours par an).<br>• Droit de jouissance ouvert après **12 mois de service effectif**.<br>• Prescription triennale : extinction des droits par **trois (3) ans**. | • Formule d'acquisition mensuelle canonique : `2.50 j/mois`.<br>• Contrôle d'ancienneté minimale : seuil de 12 mois dans `ref_type_conge` (`ANNUEL`).<br>• Job de purge FIFO triennale : expiration automatique des lots acquis datant de plus de 36 mois. |
| **Article 86** | **Bonifications d'ancienneté & Mères salariées**<br>• Augmentation de la durée du congé pour ancienneté dans l'entreprise selon convention ou règlement intérieur.<br>• Congés supplémentaires pour enfants à charge. | • Moteur `CongeCalculateurEngine` : calcul du bonus annuel d'ancienneté (+1 jour par palier de 5 ans à partir de 3 ans d'ancienneté).<br>• Mouvement créditeur `BONIFICATION` dans le grand-livre. |
| **Article 87** | **Périodes assimilées à du travail effectif**<br>Sont assimilées à un mois de travail effectif :<br>1° Congés payés antérieurs.<br>2° Repos de maternité (Art. 97).<br>3° Accidents du travail / maladies pro (limite 1 an).<br>4° Maladie ordinaire (limite 6 mois — Art. 13).<br>5° Permissions d'événements familiaux (Art. 88). | • La formule du trentième ($F = 30 - \max(0, J_{abs} - J_{conge})$) neutralise l'impact des congés pris.<br>• Le congé maternité et les permissions payées maintiennent à 100% l'acquisition du droit mensuel de 2,5 jours. |
| **Article 88** | **Permissions exceptionnelles pour événements familiaux**<br>• Droit à des permissions payées dans la limite de **10 jours par an**.<br>• Non déductibles du congé annuel si payées.<br>• Justificatif obligatoire dans les 6 mois. | • Table `ref_pool_conge_annuel` avec quota de 10,0 jours/an.<br>• Décompte partagé entre les motifs légaux (mariage, naissance, décès).<br>• Contrôle anti-dépassement cumulatif (`VALIDE` + `EN_ATTENTE`). |
| **Article 89** | **Fractionnement & Tranche continue minimale**<br>• Fractionnement possible d'accord parties.<br>• Obligation d'au moins **une tranche continue d'au moins 12 jours calendaires consécutifs**. | • Paramètre `DUREE_TRANCHE_MIN_FRACTIONNEMENT_JOURS = 12`.<br>• Surveillance annuelle et alertes RH de fin d'exercice si aucune tranche continue $\ge 12\text{j}$ n'est posée. |
| **Article 90** | **Indemnité de congé payé & Maintien de salaire**<br>• Allocation au moins égale à la moyenne des salaires, indemnités et primes des 12 derniers mois.<br>• L'indemnité ne peut être inférieure au salaire continué.<br>• Indemnité compensatrice au STC en cas de rupture. | • Calcul et historique du taux unitaire journalier `TCONGE` via `CongeTauxApplicableService`.<br>• En paie : déduction de base au 1/30e et versement de l'indemnité congé au TCONGE.<br>• Au STC : liquidation des droits acquis non pris ($Solde \times TCONGE$). |
| **Article 91** | **Date de paiement de l'indemnité de congé**<br>• L'indemnité doit être versée au salarié avant son départ en congé. | • Prise en compte dans l'acompte ou sur le bulletin de la période couvrant la date de début de congé. |
| **Article 95** | **Ordre et dates de départ en congé**<br>• Fixés par l'employeur après consultation des délégués, selon situation de famille et ancienneté. | • Circuit de validation managériale et RH avec analyse des conflits de présence d'équipe (`ConflitsPostePreview`). |
| **Article 13** | **Suspension pour maladie ordinaire non professionnelle**<br>• Suspension du contrat jusqu'à 6 mois (180 jours).<br>• Maintien légal de la rémunération par l'employeur limité au **délai de préavis (30 jours pour les mensuels)**. | • Type `MALADIE` borné à un plafond de 30 jours continus avec maintien de salaire.<br>• Bascule automatique dès le 31e jour vers `MALADIE_SANS_SOLDE` (prise en charge CNaPS). |
| **Articles 97 & 98** | **Congé de maternité**<br>• **14 semaines consécutives (98 jours)** dont 6 semaines postérieures à la délivrance.<br>• Prolongation de **3 semaines (21 jours)** en cas d'état pathologique attesté (portant le total à 119 jours). | • Type `MATERNITE` : durée fixe de 98 jours.<br>• Dérogation `extension_pathologique_maternite = true` étendant la durée à 119 jours.<br>• Non suspensif des droits à congés payés. |
| **Articles 126 & 134** | **Congé de paternité**<br>• Droit à **3 jours ouvrables payés** à l'occasion de la naissance de chaque enfant. | • Type `PATERNITE` : durée fixe de 3 jours.<br>• **Exclu du pool des 10 jours de l'Art. 88** (droit autonome prévu par le Code). |
| **Décret n° 2007-563** | **Durée légale du travail (40 heures hebdomadaires)**<br>• Base mensuelle de référence : $\frac{40 \times 52}{12} = \mathbf{173{,}33\text{ heures}}$. | • Diviseur horaire légal pour le calcul du taux horaire d'absence : $\text{Taux Horaire} = \frac{\text{Salaire de Base}}{173{,}33}$. |

---

## 2. Modèle Mathématique Canonique (`OHATRA KAJY DROITS CONGES`)

Le système applique de manière stricte le modèle financier validé en entreprise (`OHATRA KAJY DROITS CONGES-given on sept26.xls`), réconciliant l'acquisition des droits et leur valorisation comptable.

### 2.1 Définition des Variables Mensuelles

Pour chaque mois $m \in [1, 12]$ de la période de référence d'un salarié :

| Variable | Symbole | Règle de Calcul & Description Métier |
| :--- | :---: | :--- |
| **Salaire de Base Contractuel** | $S_{base}$ | Salaire mensuel de base du collaborateur pour le mois $m$. |
| **Jours d'Absence Constatés** | $J_{abs}$ | Volume d'absence du mois (en jours ou équivalent trentième). |
| **Retenue pour Absence** | $M_{abs}$ | Déduction financière au trentième ou taux horaire légal : $M_{abs} = J_{abs} \times \frac{S_{base}}{30}$. |
| **Jours de Congé Pris** | $J_{conge}$ | Jours calendaires de congés payés validés pris durant le mois. |
| **Taux Journalier TCONGE Actif** | $T_{conge}$ | Taux journalier applicable en vigueur issu du cycle précédent. |
| **Indemnité de Congé Payé** | $M_{conge}$ | $M_{conge} = J_{conge} \times T_{conge}$. |
| **Net de Base du Mois** | $S_{net\_base}$ | $S_{net\_base} = S_{base} - M_{abs} + M_{conge}$. |
| **Heures Supplémentaires Rémunérées** | $M_{hs}$ | Majoration pour heures supplémentaires effectuées dans le mois. |
| **Assiette de Droit à Congé** | $B_{totale}$ | Assiette brute globale : $B_{totale} = S_{net\_base} + M_{hs}$. |
| **Base Trentième d'Activité** | $F$ | $F = 30 - \max(0, J_{abs} - J_{conge})$ *(Les congés pris ne réduisent pas la formule)*. |
| **Droit Acquis du Mois** | $D_m$ | $D_m = 2{,}5 \times \frac{F}{30}$ *(Proratisation légale au trentième)*. |
| **Valorisation Mensuelle du Droit** | $V_m$ | $V_m = \frac{B_{totale} \times D_m}{30}$ *(Formule canonique $R \times S / 30$)*. |
| **Cumul Annuel des Droits** | $CD_{12}$ | $CD_{12} = \sum_{m=1}^{12} D_m$ *(Plafond théorique de 30 jours/an hors micro-absences)*. |
| **Cumul Annuel de Valorisation** | $CV_{12}$ | $CV_{12} = \sum_{m=1}^{12} V_m$ *(Masse financière totale provisionnée)*. |

### 2.2 Résolution du Taux Journalier Applicable (`TCONGE`)

À la clôture de la période annuelle de référence (12 mois), le taux journalier officiel `TCONGE` qui régira la valorisation du cycle suivant est calculé par le ratio canonique :

$$\mathbf{TCONGE} = \frac{CV_{12}}{CD_{12}} = \frac{\sum_{m=1}^{12} V_m}{\sum_{m=1}^{12} D_m}$$

Ce taux unitaire est persisté dans `conge_taux_applicable` avec la convention temporelle d'intervalle semi-ouvert $[date\_effet, date\_fin[$.

---

## 3. Architecture du Modèle de Données PostgreSQL

Le schéma de données certifié (`04_schema_conges.sql`) est fondé sur l'absence de tables artificielles et garantit l'intégrité référentielle complète :

```
                        ┌──────────────────────────────┐
                        │           EMPLOYE            │
                        └──────────────┬───────────────┘
                                       │ 1
       ┌───────────────────────────────┼───────────────────────────────┐
       │ N                             │ N                             │ N
┌──────▼───────────────────────┐ ┌─────▼────────────────────────┐ ┌────▼────────────────────────┐
│        DEMANDE_CONGE         │ │    MOUVEMENT_SOLDE_CONGE     │ │   CONGE_PERIODE_REFERENCE   │
│  (Cycle de vie & Contrôles)  │ │   (Grand-Livre Comptable)    │ │   (Périodes Annuelles 12M)  │
└──────┬───────────────────────┘ └──────────────────────────────┘ └────┬────────────────────────┘
       │ N                                                             │ 1
       │                                                               │ N
       ├───────────────────────────────┐                          ┌────▼────────────────────────┐
       │ 1                             │ 1                        │    CONGE_TAUX_APPLICABLE    │
┌──────▼───────────────────────┐ ┌─────▼────────────────────────┐ │ (TCONGE Historique & Cutover)│
│        REF_TYPE_CONGE        │ │ REF_MOTIF_EVENEMENT_FAMILIAL │ └─────────────────────────────┘
│ (Conditions & Comportements) │ │   (4 motifs légaux Art. 88)  │
└──────┬───────────────────────┘ └──────────────────────────────┘
       │ N
       ├───────────────────────────────┐
       │ 1                             │ 1
┌──────▼───────────────────────┐ ┌─────▼────────────────────────┐
│     REF_POOL_CONGE_ANNUEL    │ │   REF_STATUT_DEMANDE_CONGE   │
│ (Plafond partagé 10j/an)     │ │(BROUILLON, EN_ATTENTE, etc.) │
└──────────────────────────────┘ └──────────────────────────────┘
```

### 3.1 Dictionnaire des Tables Principales

#### 1. `ref_pool_conge_annuel`
Gère les quotas annuels partagés entre plusieurs types ou motifs :
- `id_pool` (PK)
- `code` (`PERMISSIONS_FAMILIALES`)
- `plafond_jours_an` (`10.0`)

#### 2. `ref_type_conge`
Porte les règles de gestion intrinsèques à chaque typologie d'absence :
- `id_type_conge` (PK), `code` (`ANNUEL`, `MALADIE`, `EXCEPTIONNEL`, `MATERNITE`, `PATERNITE`, `SANS_SOLDE`...)
- `est_paye` (booléen de maintien de salaire)
- `decompte_solde_acquis` (débit du solde annuel dans le ledger)
- `exige_anciennete_min_mois` (12 mois pour l'annuel selon Art. 85)
- `duree_fixe_jours` (98j pour Maternité, 3j pour Paternité)
- `plafond_jours_episode` (30j pour Maladie ordinaire selon Art. 13)
- `id_type_conge_bascule` (FK vers `MALADIE_SANS_SOLDE` après 30 jours)
- `id_pool_conge_annuel` (FK vers `ref_pool_conge_annuel`)
- `soumis_delai_preavis` (booléen)
- `suspend_acquisition_solde` (booléen)

#### 3. `ref_motif_evenement_familial`
Quotas individuels des 4 motifs légaux prévus par l'Article 88 :
- `MARIAGE` : 3 jours
- `NAISSANCE` : 2 jours
- `DECES_CONJOINT_ENFANT` : 3 jours
- `DECES_PARENT` : 2 jours

#### 4. `demande_conge`
Enregistre chaque demande et son statut opérationnel :
- `id_demande_conge` (PK), `id_employe` (FK), `id_type_conge` (FK), `id_statut_demande_conge` (FK)
- `date_debut`, `date_fin`, `nb_jours_decomptes`
- `est_demi_journee`, `moment_demi_journee` (`MATIN`, `APRES_MIDI`)
- `derogation_solde_negatif`, `derogation_delai_preavis`, `extension_pathologique_maternite`
- `saisi_par`, `valide_par`, `date_validation`, `motif_refus`

#### 5. `mouvement_solde_conge` (Grand-Livre Comptable / Ledger)
Registre immuable de chaque débit et crédit de congés :
- `id_mouvement` (PK), `id_employe` (FK), `date_mouvement`
- `type_mouvement` (`ACQUISITION`, `BONIFICATION`, `PRISE`, `AJUSTEMENT`, `ANNULATION`, `EXPIRATION`)
- `nb_jours` (valeur signée : positif pour crédit, négatif pour débit)
- `id_demande_conge` (FK nullable vers la demande d'origine)
- **Index uniques partiels anti-doublon** :
  - `idx_uniq_mouvement_prise_demande` : empêche toute double écriture de `PRISE` pour une même demande.
  - `idx_uniq_mouvement_annulation_demande` : garantit l'idempotence stricte de l'opération d'annulation.

#### 6. `conge_periode_reference` & `conge_taux_applicable`
Périodes de 12 mois bornées et historisation du taux journalier `TCONGE` avec convention $[date\_effet, date\_fin[$.

### 3.2 Vues SQL Certifiées

1. **`v_solde_conge_employe`** :
   Synthétise en temps réel le solde disponible (`solde_actuel`), les cumuls acquis, pris, bonifiés et expirés.
2. **`v_pool_conge_annuel_consommation`** :
   Agrège par employé et par an les jours engagés (`jours_consommes` issus de `VALIDE` + `jours_en_attente` issus de `EN_ATTENTE`).
3. **`v_demande_conge_cpl`** :
   Vue enrichie projetant l'ensemble des jointures pour l'affichage des tableaux de bord RH.

---

## 4. Règles de Gestion Métier (RG) Détaillées

### RG-CON-01 : Décompte Calendaire Strict & Demi-Journées
- Décompte standard : tous les jours calendaires inclus entre `date_debut` et `date_fin` sont décomptés :
  $$\text{nb\_jours\_decomptes} = (\text{date\_fin} - \text{date\_debut}) + 1$$
  *(Les samedis, dimanches et jours fériés intercalés sont obligatoirement comptabilisés conformément à la base calendaire de l'Art. 85)*.
- **Demi-journée** : Si `est_demi_journee = true`, alors $\text{date\_debut} = \text{date\_fin}$, $\text{nb\_jours\_decomptes} = 0{,}5$, et le moment doit être explicitement renseigné (`MATIN` ou `APRES_MIDI`).

### RG-CON-02 : Acquisition Mensuelle Légale & Idempotence
- Base normale : **2,50 jours calendaires** par mois de service effectif (Art. 85).
- Proratisation : en présence d'absences injustifiées ou non rémunérées, le calcul s'applique sur la formule d'activité :
  $$D_m = 2{,}5 \times \frac{30 - J_{abs}}{30}$$
- **Idempotence certifiée** : L'exécution du cycle d'acquisition mensuelle vérifie la présence d'une écriture existante sur la période pour le collaborateur. Tout rappel ultérieur préserve le solde sans générer de crédit double.

### RG-CON-03 : Bonification pour Ancienneté
- Calculée au mois anniversaire d'embauche du salarié :
  - Seuil d'éligibilité : Ancienneté $\ge 3$ ans.
  - Formule : $+1$ jour de congé supplémentaire par tranche complète de 5 ans d'ancienneté :
    $$\text{Bonus} = \left\lfloor \frac{\text{Années d'ancienneté}}{5} \right\rfloor \times 1\text{ jour}$$
  - Tracé dans le ledger sous le libellé `BONIFICATION`.

### RG-CON-04 : Prescription Triennale & Purge FIFO (RG-CONGE-06)
- Conformément à l'Article 85 in fine, les droits à congé se prescrivent par **trois (3) ans (36 mois)**.
- **Algorithme d'imputation FIFO autonome (`PurgeFifoEngine`)** :
  1. Les consommations (`PRISE`) s'imputent chronologiquement sur les lots acquis les plus anciens.
  2. À la date de purge, tout reliquat non consommé d'un lot acquis datant de plus de 36 mois génère une écriture débitrice `EXPIRATION`.
  3. Les droits récents non échus sont rigoureusement préservés.

### RG-CON-05 : Anticipation de Solde & Dérogation RH
- En règle générale, toute demande dont le volume dépasse le solde disponible est bloquée (`LEAVE_BALANCE_INSUFFICIENT`).
- La DRH/DAF peut octroyer une dérogation (`derogation_solde_negatif = true`).
- Le solde débiteur ne peut en aucun cas excéder le plafond légal/conventionnel paramétré (`PLAFOND_SOLDE_CONGE_NEGATIF = -10 jours`). Tout dépassement au-delà de -10j est rejeté même avec dérogation.

### RG-CON-06 : Délais de Préavis Réglementaires
Pour les congés soumis à préavis (`soumis_delai_preavis = true` sur `ref_type_conge`) :
- Demande courte ($\le 3$ jours) : **2 jours ouvrables** de délai minimum entre la date de demande et la date de départ.
- Demande longue ($> 3$ jours) : **15 jours calendaires** de délai minimum.
- Les congés d'urgence (`MALADIE`, `MATERNITE`, `PATERNITE`, `EXCEPTIONNEL`) sont exemptés de préavis.

### RG-CON-07 : Pool Annuel des Événements Familiaux (10 jours/an)
- Quota légal partagé de **10,0 jours par année civile** (Article 88).
- Le total engagé correspond à la somme des jours `VALIDE` et `EN_ATTENTE`.
- **Règle d'invariance et correction certifiée** : Lors du contrôle de validation d'une demande déjà enregistrée en base au statut `EN_ATTENTE`, le système déduit le volume déjà comptabilisé pour cette demande avant de sommer le volume validé, empêchant tout rejet prématuré par double comptage.
- Le congé de paternité (3 jours) est expressément **exclu** du pool partagé des 10 jours (Articles 126 & 134).

### RG-CON-08 : Maternité & Extension Pathologique
- Durée légale standard : **14 semaines consécutives (98 jours)** (Art. 97).
- Prolongation pathologique : **+21 jours (3 semaines)** sur production d'un certificat médical (`extension_pathologique_maternite = true`), portant le total autorisé à **119 jours calendaires**.
- Suspension sans perte de droits : la salariée continue d'acquérir ses 2,5 jours de congés payés par mois pendant tout le congé maternité (Art. 87).

### RG-CON-09 : Maladie Ordinaire & Relais CNaPS
- Maintien intégral du salaire à la charge de l'employeur pendant les **30 premiers jours continus** (Article 13, limité au délai de préavis mensuel).
- Dès le 31e jour, la demande bascule automatiquement vers `MALADIE_SANS_SOLDE` pour prise en charge directe par les indemnités journalières de la CNaPS.

### RG-CON-10 : Fractionnement Annuel Obligatoire
- Dans l'année civile, au moins une période continue de congé payé annuel doit atteindre **12 jours consécutifs** (Art. 89).
- Une alerte légale préventive est émise par le système au 4e trimestre si l'employé n'a posé aucune tranche $\ge 12$ jours.

### RG-CON-11 : Périodes de Paie Clôturées & Invariance Historique
- Toute tentative de validation, de modification ou d'annulation d'un congé tombant dans une période de paie au statut `CLOTUREE` est immédiatement rejetée avec le code `LEAVE_PAYROLL_PERIOD_CLOSED`.
- L'intégrité comptable et financière des exercices passés est inviolable.

---

## 5. Workflow & Circuit de Validation Transactionnel

### 5.1 Diagramme d'États Certifié

```
                  ┌──────────────┐
                  │  BROUILLON   │
                  └──────┬───────┘
                         │ soumettre()
                         ▼
                  ┌──────────────┐
                  │  EN_ATTENTE  │◄────────────────┐
                  └──┬────────┬──┘                 │
     validerDemande()│        │refuserDemande()    │
                     │        ▼                    │
                     │  ┌──────────────┐           │
                     │  │    REFUSE    │           │
                     │  └──────────────┘           │
                     ▼                             │
              ┌──────────────┐                     │
              │    VALIDE    │                     │
              └──┬───────────┘                     │
annulerDemande() │ (Génération PRISE au Ledger)    │
                 ▼                                 │
          ┌──────────────┐                         │
          │    ANNULE    │                         │
          └──────────────┘                         │
         (Génération ANNULATION au Ledger)         │
         (Seconde annulation interdite/idempotente)┘
```

### 5.2 Isolation Transactionnelle & Verrous Pessimistes (`FOR UPDATE`)

Pour neutraliser tout risque d'accès concurrentiel (ex: deux validateurs approuvant simultanément deux demandes distinctes pour le même salarié alors que son solde n'en permet qu'une) :
1. **Verrouillage de la demande** :
   ```sql
   SELECT id_statut_demande_conge, nb_jours_decomptes
   FROM demande_conge
   WHERE id_demande_conge = ? FOR UPDATE;
   ```
2. **Verrouillage de l'employé** :
   ```sql
   SELECT id_employe FROM employe WHERE id_employe = ? FOR UPDATE;
   ```
3. **Atomicité via `TransactionRunner`** :
   L'ensemble des opérations (mise à jour du statut, horodatage, insertion de la ligne `PRISE` dans `mouvement_solde_conge`) est exécuté au sein d'une transaction unique. En cas de défaillance, un rollback complet est immédiatement déclenché.

### 5.3 Matrice Dynamique des Actions Disponibles (`availableActions`)

Le backend calcule dynamiquement pour chaque demande la liste des actions autorisées en fonction du statut, du rôle de l'utilisateur et de l'état de clôture de la paie :

| Statut Demande | Rôle Salarié (`ROLE_USER`) | Rôle Approbateur / RH (`CONGE_VALIDATE` / `ADMIN`) | Si Période de Paie Clôturée |
| :--- | :--- | :--- | :--- |
| **`BROUILLON`** | `EDITER`, `SOUMETTRE`, `SUPPRIMER` | `EDITER`, `SOUMETTRE`, `SUPPRIMER` | Aucune action (`[]`) |
| **`EN_ATTENTE`** | `ANNULER` | `VALIDER`, `REFUSER`, `MODIFIER` | Aucune action (`[]`) |
| **`VALIDE`** | `DEMANDER_ANNULATION` | `ANNULER` (avec motif) | Aucune action (`[]`) |
| **`REFUSE`** | Aucune action | Aucune action | Aucune action (`[]`) |
| **`ANNULE`** | Aucune action | Aucune action | Aucune action (`[]`) |

---

## 6. Intégrations Certifiées : Pointage, Paie & STC

### 6.1 Pont Réel Congés $\rightarrow$ Pointage (Gestion du Temps)
L'approbation d'une demande de congé met à jour automatiquement la grille de pointage mensuelle via `PointageSaisieService` :

| Propriétés du Type de Congé | Qualification dans Pointage | Heures Badgées | Heures Rémunérées DTO |
| :--- | :--- | :---: | :---: |
| **`decompte_solde_acquis = true`** (ex: `ANNUEL`) | Statut `CONGE` (id 5) | `0.0 h` | Forfait horaire contractuel |
| **`decompte_solde_acquis = false` + payé** (ex: `EXCEPTIONNEL`) | Statut `DISPENSE_POINTAGE` (id 10) | `0.0 h` | Forfait horaire contractuel (8.0h) |
| **Non rémunéré** (ex: `SANS_SOLDE`, `MALADIE_SANS_SOLDE`) | Statut `ABSENCE_JUSTIFIEE` (id 4) | `0.0 h` | `0.0 h` (Non rémunéré) |

*Règle d'intégrité temporelle* : Les pointages ne peuvent être saisis pour une date future (`datePointage > LocalDate.now()`).

### 6.2 Pont Réel Congés $\rightarrow$ Moteur de Paie
Lors du calcul du bulletin mensuel :
1. **Retenue sur salaire de base pour congé pris** (au trentième légal) :
   $$\text{DEDUCTION\_CONGE\_BASE} = \text{Jours Congé Pris} \times \frac{\text{Salaire de Base Contractuel}}{30}$$
2. **Indemnité de congé payé** (au taux `TCONGE` canonique résolu par `CongeTauxApplicableService`) :
   $$\text{INDEMNITE\_CONGE\_PAYE} = \text{Jours Congé Pris} \times \mathbf{TCONGE}$$
3. **Gain net pour le salarié** : Si le salarié a perçu des heures supplémentaires ou des primes durant l'année de référence, $\mathbf{TCONGE} > \frac{S_{base}}{30}$, ce qui génère une régularisation positive sur son bulletin.

### 6.3 Pont Réel Congés $\rightarrow$ Solde de Tout Compte (STC)
Lors du départ du collaborateur (démission, licenciement, terme du contrat) :
- L'Article 90 alinéa 3 impose le versement d'une indemnité compensatrice pour les congés non pris.
- Le service STC interroge `v_solde_conge_employe` et le taux `TCONGE` du jour de départ :
  $$\text{Indemnité Compensatrice STC} = \text{Solde Restant} \times \mathbf{TCONGE}$$
- En cas de solde négatif (anticipation non amortie), une déduction de même valeur est appliquée sur le net STC.

---

## 7. Automatisations, Purge FIFO & Schedulers Nocturnes

Trois processus automatisés tournent en arrière-plan selon un calendrier strict :

| Horaire | Composant Scheduler | Rôle & Traitement Effectué |
| :---: | :--- | :--- |
| **00:05** | `NotificationRhScheduler` | Détection et dispatching des alertes RH de supervision (solde faible, expiration proche, tranche de fractionnement non respectée). |
| **01:00** | `AcquisitionCongeScheduler` | Exécution de l'acquisition mensuelle (+2,5j proratisé) pour les salariés actifs et calcul de la bonification d'ancienneté au mois anniversaire. |
| **02:00** | `PurgeCongeScheduler` | Exécution de la purge FIFO triennale : calcul des droits acquis datant de plus de 36 mois et génération des mouvements débit de type `EXPIRATION`. |

---

## 8. Cartographie des Composants Logiciels (Backend & Frontend)

### 8.1 Backend Java (18 Fichiers de Production Confirmés)

Le backend est entièrement consolidé sous le package `mg.galaxam.sirh.conge`, sans aucun framework ORM (JPA/Hibernate) :

```
mg.galaxam.sirh.conge/
├── api/
│   ├── DemandeCongeController.java          # Endpoints cycle de vie des demandes, simulation & actions
│   ├── CongeSoldeController.java            # Endpoints consultation soldes, mouvements & audit
│   ├── CongeRefController.java              # Endpoints référentiels (types, motifs légaux, pools)
│   └── CongePeriodeReferenceController.java # Endpoints clôture de période de référence & TCONGE
├── application/
│   ├── CongeService.java                    # Orchestrateur central de gestion et validation transactionnelle
│   └── CongeTauxApplicableService.java      # Source unique de résolution temporelle du taux TCONGE
├── domain/
│   ├── CongeCalculateurEngine.java          # Moteur pur de calcul (jours calendaires, préavis, prorata)
│   ├── PurgeFifoEngine.java                 # Moteur autonome d'imputation FIFO et purge triennale
│   ├── CongeSituationEmployeDTO.java        # DTO canonique de synthèse collaborateur
│   └── TcongeConfigurationDTO.java          # DTO de configuration et historique TCONGE
└── persistence/
    ├── DemandeConge.java                    # Modèle JDBC et contrôles d'intégrité de la table demande_conge
    ├── MouvementSoldeConge.java             # Modèle JDBC du grand-livre (mouvement_solde_conge)
    ├── RefTypeConge.java                    # Modèle JDBC du référentiel des types de congés
    ├── RefMotifEvenementFamilial.java       # Modèle JDBC des 4 motifs légaux (Art. 88)
    ├── RefStatutDemandeConge.java           # Modèle JDBC des statuts (BROUILLON, EN_ATTENTE...)
    ├── RefPoolCongeAnnuel.java              # Modèle JDBC du pool partagé (10j/an)
    ├── VSoldeCongeEmploye.java              # Vue JDBC d'agrégation temps réel du solde collaborateur
    └── VDemandeCongeCpl.java                # Vue JDBC consolidée pour les tableaux de bord RH
```

### 8.2 Frontend React Backend-Driven (`features/leave/`)

Le frontend n'implémente **aucune logique de calcul métier**. Il est strictement asservi aux contrats du backend :
- `DemandeCongeModal.jsx` : Modale de saisie dynamique (récupère types, motifs, dérogations).
- `DatesCongeSection.jsx` : Sélecteur de dates déclenchant l'endpoint `/simuler`.
- `SoldeCongeWidget.jsx` & `SoldesOverviewTab.jsx` : Affichage temps réel des compteurs issus de `v_solde_conge_employe`.
- `DemandesValidationTab.jsx` : Tableau de bord de validation affichant les boutons d'action conditionnés par `availableActions`.
- `congeService.js` : Client Axios communiquant avec les APIs `/api/conges/*`.

---

## 9. Matrice des Codes d'Erreur & Réponses API Normalisées

Toutes les violations de règles métier renvoient des codes d'erreur stables et exploitables :

| Code d'Erreur Backend | Signification & Déclencheur Métier | Comportement UX Frontend |
| :--- | :--- | :--- |
| `LEAVE_BALANCE_INSUFFICIENT` | Solde disponible insuffisant pour couvrir la demande (sans dérogation). | Bloque le bouton de soumission et propose la case dérogation RH. |
| `LEAVE_NOTICE_NOT_RESPECTED` | Délai de préavis réglementaire non respecté (2j ou 15j). | Affiche un avertissement bloquant sauf dérogation préavis. |
| `LEAVE_POOL_EXCEEDED` | Plafond annuel partagé de 10 jours dépassé pour les événements familiaux. | Bloque la saisie et signale le dépassement de quota d'entreprise. |
| `LEAVE_OVERLAP` | Chevauchement de dates avec une autre demande déjà existante. | Signale le conflit de dates exact avec la demande concurrente. |
| `LEAVE_PAYROLL_PERIOD_CLOSED`| La période de paie couvrant la date de congé est déjà clôturée. | Interdit toute modification ou annulation rétroactive. |
| `LEAVE_CONFIGURATION_MISSING`| Type de congé ou motif légal introuvable ou désactivé. | Message d'erreur invitant à contacter l'administrateur RH. |
| `LEAVE_DEMI_JOURNEE_INVALID` | Incohérence sur la demi-journée (`date_debut != date_fin` ou moment absent). | Force la sélection du créneau MATIN ou APRES_MIDI. |
| `LEAVE_STATUT_INVALID` | Transition d'état illégale (ex: tentative de ré-annuler une demande déjà annulée). | Bloque l'action avec message explicatif d'idempotence. |

---

## 10. Corrections Critiques & Points d'Audit du Sprint 3

### 10.1 Bug Corrigé : Double Comptage du Quota de Pool Partagé à la Validation
- **Constat** : Lors de la validation administrative (`validerDemande`), l'appel à `controler(c)` dans `DemandeConge.java` provoquait un rejet immédiat (`LEAVE_POOL_EXCEEDED`) dès que le total atteignait 10 jours.
- **Cause racine** : La vue `v_pool_conge_annuel_consommation` agrège les demandes en statut `VALIDE` **et** `EN_ATTENTE`. La demande courante étant déjà enregistrée en base au statut `EN_ATTENTE`, `controler(c)` rajoutait à nouveau `this.nbJoursDecomptes` à la somme renvoyée par la vue, comptabilisant la même demande deux fois.
- **Correction apportée** : Déduction systématique du volume déjà comptabilisé en base pour `this.idDemandeConge` avant de sommer le volume validé, garantissant l'exactitude mathématique du contrôle.

### 10.2 Dette Métier Identifiée (`BUSINESS_RULE_REQUIRED`)
- La dérogation pour dépassement du pool d'événements familiaux réutilise actuellement le flag booléen `derogation_solde_negatif` sur `demande_conge`.
- *Recommandation future* : Création d'une colonne dédiée `derogation_pool_annuel` lors d'une prochaine migration de schéma.

---

## 11. Recueil Textuel Intégral des Articles Légaux (Loi n° 2003-044)

Pour servir de référence textuelle inattaquable lors des audits sociaux et des contrôles de l'Inspection du Travail :

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

### Article 134 — Congé de Paternité
> *« Tout travailleur masculin a droit, à l'occasion de la naissance de chaque enfant au sein de son foyer, à un congé de paternité rémunéré de **trois jours ouvrables**. Ce congé est indépendant des permissions exceptionnelles pour événements de famille prévues à l'article 88. »*

---

## 12. Données d'Audit Intégrales du Modèle Excel Réel (Matricule 61)

Reproduction intégrale du cycle annuel de référence validant le calcul mathématique de `OHATRA KAJY DROITS CONGES-given on sept26.xls` :

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

### Synthèse Comptable à la Clôture
- **Cumul Annuel des Droits Acquis** : **$29{,}9567\text{ jours}$** (avec proratisation exacte sur 30e au regard des micro-absences injustifiées en M2, M3, M6, M9, M12).
- **Cumul Annuel de Valorisation Financière** : **$324\,385{,}38\text{ Ar}$**.
- **Taux Journalier TCONGE Applicable pour l'Exercice Suivant** :
  $$\mathbf{TCONGE} = \frac{324\,385{,}3763\text{ Ar}}{29{,}956667\text{ jours}} = \mathbf{10\,828{,}49\text{ Ar / jour}}$$

---

*Document officiel d'ingénierie et de certification GALAXAM SIRH — Conforme Code du Travail malgache 2026.*
