# Module Paie, Rémunération, Cotisations, IRSA & STC — Spécification Complète & Rapport de Référence
## GALAXAM SIRH — Documentation Canonique & Métier

> **Auteur** : Équipe d'Ingénierie, Architecture Logicielle & Direction Financière GALAXAM SIRH  
> **Module** : Paie, Rémunération Contractuelle, Heures Supplémentaires, Cotisations Sociales (CNaPS, OSTIE/Santé, FMFP), Fiscalité (IRSA / ISI), Avances, Prêts, Solde de Tout Compte (STC) & Déclarations Officielles (SAFI, DNTS)  
> **Version** : 4.2.0 (État réel du code source Java/SQL — Suite de 125 tests JUnit 5 verts, 100% certifiés)  
> **Date de référence** : Octobre 2026  
> **Principe directeur** : Le code source Java et PostgreSQL est le moteur et la source principale de vérité. Chaque formule de calcul et règle de gestion est adossée aux textes légaux et fiscaux malagasy applicables.  
> **Sources juridiques et normatives de référence** :  
> - **Loi n° 2024-014 du 1er juillet 2024** portant Code du Travail de Madagascar (Articles 33 à 38, 109 à 116, 127, 131, 136 à 141)  
> - **Loi n° 2003-044 du 28 juillet 2004** portant ancien Code du Travail (Articles 13, 33, 61 à 74, 85 à 91 — conservés pour comparatif et traçabilité historique)  
> - **Code Général des Impôts (CGI) de Madagascar — Dispositions LF 2025 / LF 2026** :  
>   - **Art. 01.03.01 et suivants** : Assiette imposable des traitements, salaires, indemnités, émoluments, primes et avantages en nature.  
>   - **Barème progressif de l'IRSA** : Tranches de 0% à 20%, minimum de perception de **3 000 Ar**.  
>   - **Art. 01.03.14** : Réduction d'impôt pour charges de famille (**2 000 Ar par mois par personne à charge**).  
>   - **Art. 01.03.22** : Impôt Synthétique Intermittent (**ISI à 5%**) prélevé à la source sur les honoraires de prestations externes.  
> - **Décret n° 69-145 et Décret n° 2010-386 fixant le régime de la CNaPS** :  
>   - Plafond CNaPS fixé à **8 fois le Salaire Minimum d'Embauche (SME)**.  
>   - Cotisation salariale : **1%** du brut plafonné.  
>   - Cotisation patronale : **13%** (1% Prestations Familiales, 1% Accidents du Travail, 11% Retraite plafonnée).  
> - **Statuts des Organismes Sanitaires Interentreprises (OSTIE / AMIT / FUNHREUMA)** :  
>   - Cotisation salariale : **1%** du brut.  
>   - Cotisation patronale : **5%** du brut.  
> - **Loi n° 2017-025 relative au FMFP** : Contribution patronale unique de **1%** sur la masse salariale brute non plafonnée.  
> - **Décret n° 2007-563 du 03 juillet 2007** relatif à la durée légale du travail (173,33 heures mensuelles, diviseur horaire officiel).  
> - **Décret n° 72-226 du 06 juillet 1972** fixant le régime des heures supplémentaires (+30% pour les 8 premières heures, +50% au-delà) et des majorations de sujétion (nuit, dimanche, jours fériés).  
> - **Schémas de base de données canoniques** : `backend/conception/bdd/06_schema_paie.sql`, `05_schema_pointage.sql`, `04_schema_conges.sql`, `03_schema_employes_contrats.sql`.

---

## Sommaire Exécutif

Le module **Paie & Rémunération** est le cœur financier et déclaratif du SIRH GALAXAM.  
Il transforme une période de pointage contrôlée et gelée, des droits à congés audités et des variables d'exploitation approuvées en **bulletins de paie légalement certifiés, justifiables et opposables aux tiers** (administration fiscale, organismes sociaux, banques, inspection du travail).

L'architecture repose sur des principes d'ingénierie stricts :

1. **La séparation étanche des responsabilités** :  
   - Le module **Pointage** fige les faits temporels sous forme de snapshots immuables (`pointage_cumul_periode`, `pointage_snapshot_bucket`, etc.).  
   - Le module **Congés** fige les jours calendaires et la valeur financière historique des lots FIFO (`conge_journal_droit`, `conge_taux_applicable`).  
   - Le module **Paie** consomme exclusivement ces données scellées, sans jamais interroger directement la table mutable `pointage_jour` et sans altérer rétroactivement le passé.
2. **Le moteur d'exécution pur en 6 paliers ([`PayrollExecutionEngine.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/paie/domain/engine/PayrollExecutionEngine.java))** :  
   Orchestration déterministe et traçable : Gains bruts $\rightarrow$ Cotisations sociales (salariales & patronales) $\rightarrow$ Net imposable $\rightarrow$ Fiscalité (IRSA progressif avec minimum de perception et déductions familiales, ou retenue ISI 5%) $\rightarrow$ Gains nets après impôt $\rightarrow$ Retenues de trésorerie (acomptes, avances, prêts).
3. **Le traitement rigoureux des congés et absences** :  
   - Retenue pour congé au 1/30e contractuel : $\text{Retenue} = S_{base} / 30 \times J_{conge}$.  
   - Indemnité de congé valorisée au taux historique du lot FIFO : $\text{Gain} = \sum (J_{lot} \times \mathbf{TCONGE}_{lot})$.  
   - Retenue pour absence résiduelle au taux horaire légal : $\text{Retenue} = \text{Heures Absence} \times (S_{base} / 173{,}33)$.  
   - Ces composantes constituent des lignes de bulletin distinctes et ne se compensent jamais aveuglément.
4. **La gestion de la trésorerie et des prêts ([`PaieAvanceAcompte.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/paie/persistence/PaieAvanceAcompte.java), [`PaiePret.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/paie/persistence/PaiePret.java))** :  
   - Cycles de vie stricts avec verrouillage pessimiste et restauration transactionnelle des imputations lors des recalculs de brouillons.  
   - Respect impératif de la quotité saisissable et des limites légales de retenue (**Loi 2024-014, Art. 140**).
5. **Le Solde de Tout Compte certifié ([`StcService.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/paie/application/StcService.java))** :  
   Calcul exhaustif et sécurisé des indemnités de rupture (préavis, licenciement légal Art. 35, liquidation financière des congés payés non pris Art. 131) avec apurement des dettes salariales et édition du reçu pour Solde de Tout Compte.
6. **Le bouquet complet d'exports officiels** :  
   Production native sans dépendance externe des états télédéclaratifs et bancaires :  
   - **SAFI** (Télé-déclaration fiscale CSV et ODS conforme aux normes de la Direction Générale des Impôts)  
   - **DNTS** (Déclaration Nominative Trimestrielle des Salaires CNaPS)  
   - **État Social Mensuel** (Ventilation mensuelle CNaPS, Santé et FMFP)  
   - **Bordereau IRSA & ISI**  
   - **Fichiers de Virement Bancaire** normalisés pour la place financière malagasy (BNI, BMOI, BOA, Société Générale, MCB, etc.)  
   - **Bulletins de paie PDF** individuels et massifs certifiés.

---

## Table des Matières

1. [Cadre Légal, Fiscal & Réglementaire Malagasy](#1-cadre-légal-fiscal--réglementaire-malagasy)
2. [Périmètre, Acteurs & Frontières Inter-Modules](#2-périmètre-acteurs--frontières-inter-modules)
3. [Architecture du Modèle de Données PostgreSQL](#3-architecture-du-modèle-de-données-postgresql)
4. [Sélection Temporelle du Contexte Légal & Barèmes Datés](#4-sélection-temporelle-du-contexte-légal--barèmes-datés)
5. [Moteur d'Exécution de la Paie en 6 Paliers](#5-moteur-dexécution-de-la-paie-en-6-paliers)
6. [Calcul des Cotisations Sociales & Organismes Tiers](#6-calcul-des-cotisations-sociales--organismes-tiers)
7. [Moteur Fiscal : Barème IRSA & Retenue ISI](#7-moteur-fiscal--barème-irsa--retenue-isi)
8. [Variables de Paie, Acomptes, Avances & Prêts](#8-variables-de-paie-acomptes-avances--prêts)
9. [Solde de Tout Compte (STC) & Ruptures de Contrat](#9-solde-de-tout-compte-stc--ruptures-de-contrat)
10. [Machine à États & Clôture Transactionnelle](#10-machine-à-états--clôture-transactionnelle)
11. [Exports Réglementaires & Déclarations Officielles](#11-exports-réglementaires--déclarations-officielles)
12. [Cartographie des Composants Backend & Frontend](#12-cartographie-des-composants-backend--frontend)
13. [Catalogue Exhaustif des Règles de Gestion (RG)](#13-catalogue-exhaustif-des-règles-de-gestion-rg)
14. [Étude de Cas Chiffrée & Rapprochement de Recette](#14-étude-de-cas-chiffrée--rapprochement-de-recette)

---

## 1. Cadre Légal, Fiscal & Réglementaire Malagasy

### 1.1 Tableau Synoptique des Textes Légaux Applicables

| Domaine | Source Juridique Officielle | Portée Métier & Règles de Calcul | Traduction Technique dans le SIRH |
| :--- | :--- | :--- | :--- |
| **Durée Légale du Travail** | **Loi 2024-014, Art. 109**<br>Décret n° 2007-563 | • Durée légale hebdomadaire : 40 heures.<br>• Base mensuelle légale : $\frac{40 \times 52}{12} = \mathbf{173{,}33\text{ heures}}$. | • Constante `HEURES_MENSUELLES_LEGALES = 173.33`.<br>• Taux horaire de base : $T_{horaire} = \frac{S_{base}}{173{,}33}$. |
| **Heures Supplémentaires** | **Loi 2024-014, Art. 110-112**<br>Décret n° 72-226 | • 8 premières heures sup hebdomadaires (41e à 48e heure) : majoration de **30%**.<br>• Au-delà de la 48e heure : majoration de **50%**. | • [`OvertimePayCalculator.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/paie/domain/engine/OvertimePayCalculator.java).<br>• Seuil de tranche 1 : `seuil_hs_tranche1` (8h = 480 min). |
| **Sujétions Temporelles** | **Loi 2024-014, Art. 115, 120, 124**<br>Décret n° 72-226 | • Travail de nuit (22h–05h) : majoration horaire spécifique.<br>• Dimanche & Férié : majorations légales sans double paiement de base. | • Table `ref_taux_majoration`.<br>• Résolution temporelle stricte par `TemporalRateResolver.java`. |
| **Congés Payés** | **Loi 2024-014, Art. 125 & 131** | • Règle du salaire continué : l'indemnité ne peut être inférieure au salaire maintenu.<br>• Retenue au trentième et indemnité valorisée au taux historique du cycle. | • Retenue : $S_{base} / 30 \times J_{conge}$.<br>• Indemnité : $\sum (J_{lot} \times \mathbf{TCONGE}_{lot})$ via `CongeLotValorisationEngine`. |
| **Cotisations CNaPS** | **Décret n° 69-145**<br>Décret n° 2010-386 | • Plafond CNaPS = 8 fois le SME.<br>• Part salariale : **1%** du brut plafonné.<br>• Part patronale : **13%** (1% PF, 1% ATMP, 11% Retraite plafonnée). | • [`SocialContributionCalculator.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/paie/domain/engine/SocialContributionCalculator.java).<br>• Plafond résolu dans `paie_config_legale.plafond_cnaps_ar`. |
| **Organismes de Santé (OSTIE)** | Statuts de l'OSTIE / Arrêté interministériel | • Part salariale : **1%** du salaire brut total.<br>• Part patronale : **5%** du salaire brut total. | • Rubriques `COT_OSTIE_SALARIALE` et `COT_OSTIE_PATRONALE`. |
| **Formation Professionnelle** | **Loi n° 2017-025 (FMFP)** | • Contribution patronale unique de **1%** sur le brut déplafonné. | • Rubrique `COT_FMFP_PATRONALE`. |
| **Fiscalité Salariale (IRSA)** | **CGI Madagascar, Art. 01.03.01+**<br>Dispositions LF 2025/2026 | • Barème progressif par tranches (0% à 20%).<br>• Minimum de perception légal : **3 000 Ar**.<br>• Réduction pour charges familiales : **2 000 Ar / personne / mois**. | • [`PayrollTaxCalculations.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/paie/domain/engine/PayrollTaxCalculations.java).<br>• Tranches stockées dans `bareme_irsa`. |
| **Fiscalité Prestataires (ISI)** | **CGI Madagascar, Art. 01.03.22** | • Retenue à la source de **5%** sur honoraires de consultants et prestataires.<br>• Exonéré de charges sociales salariales et patronales. | • Paramètre `TAUX_ISI = 0.05`.<br>• Table `paie_honoraire_prestation`. |
| **Limites de Retenues** | **Loi 2024-014, Art. 140** | • Protection de la fraction insaisissable du salaire net (quotité cessible). | • Plafonnement des déductions d'acomptes et prêts sur le net disponible. |
| **Solde de Tout Compte** | **Loi 2024-014, Art. 33-38, 131** | • Indemnités légales de licenciement, préavis et reliquats de congés payés non pris. | • [`StcService.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/paie/application/StcService.java) & table `paie_stc`. |

---

## 2. Périmètre, Acteurs & Frontières Inter-Modules

### 2.1 Matrice des Échanges Inter-Modules

```
┌─────────────────────────────────┐
│     CONTRAT & CARRIÈRE          │
│ • Salaire de base, Catégorie    │
│ • Statut cadre / non-cadre      │
│ • Mode de rémunération          │
└────────────────┬────────────────┘
                 │
                 ▼
┌─────────────────────────────────┐      ┌─────────────────────────────────┐
│      POINTAGE SCELLÉ            │      │       CONGÉS AUDITÉS            │
│ • Heures travaillées certifiées │      │ • Jours calendaires validés     │
│ • Snapshots HS30, HS50, Nuit    │      │ • Lots FIFO historiques         │
│ • Indemnités repas/déplacement  │      │ • Taux applicable TCONGE        │
└────────────────┬────────────────┘      └────────────────┬────────────────┘
                 │                                        │
                 └───────────────────┬────────────────────┘
                                     │
                                     ▼
                   ┌───────────────────────────────────┐
                   │           MOTEUR DE PAIE          │
                   │      (PayrollExecutionEngine)     │
                   │ • Calcul déterministe en 6 phases │
                   │ • Déductions fiscales & sociales  │
                   │ • Apurement prêts et acomptes     │
                   └─────────────────┬─────────────────┘
                                     │
                                     ▼
                   ┌───────────────────────────────────┐
                   │    BULLETINS SCELLÉS & EXPORTS    │
                   │ • Bulletin individuel PDF         │
                   │ • Déclarations SAFI (ODS / CSV)   │
                   │ • DNTS CNaPS & Ordres de virement │
                   └───────────────────────────────────┘
```

### 2.2 Rôles & Ségrégation des Tâches (RBAC)

1. **Gestionnaire de Paie (`ROLE_PAIE`, `PAIE_WRITE`)** :  
   Préparation de la campagne, sélection de la population active, saisie et enregistrement des variables d'ajustement individuelles, lancement des simulations provisoires et exécution du calcul batch.
2. **Directeur Administratif & Financier (`ROLE_DAF`, `PAIE_VALIDATE`, `ROLE_ADMIN`)** :  
   Contrôle approfondi des écarts, arbitrage des primes et ajustements, approbation formelle du calcul (`APPROVE`), clôture définitive de la période (`CLOSE`), signature du Livre de Paie et des déclarations télédéclaratives.
3. **Auditeur / Direction Générale (`ROLE_AUDIT`, `PAIE_READ`)** :  
   Consultation en lecture seule des synthèses, consultation des fiches de calcul, contrôle des snapshots et génération des rapports de conformité.

---

## 3. Architecture du Modèle de Données PostgreSQL

Le schéma certifié `06_schema_paie.sql` assure l'isolation stricte entre référentiels, mouvements financiers, pièces de synthèse et lignes de détail :

```
                        ┌──────────────────────────────┐
                        │         PERIODE_PAIE         │
                        └──────────────┬───────────────┘
                                       │ 1
        ┌──────────────────────────────┼──────────────────────────────┐
        │ N                            │ N                            │ N
 ┌──────▼───────────────────────┐┌─────▼────────────────────────┐┌─────▼───────────────────────┐
 │        BULLETIN_PAIE         ││     PAIE_AVANCE_ACOMPTE      ││      PAIE_AJUSTEMENT        │
 │(En-tête de paie collaborateur││ (Mouvements de trésorerie)   ││(Primes, régularisations DAF) │
 └──────┬───────────────────────┘└──────────────────────────────┘└─────────────────────────────┘
        │ 1
        │ N
 ┌──────▼───────────────────────┐┌──────────────────────────────┐┌─────────────────────────────┐
 │     BULLETIN_PAIE_LIGNE      ││           PAIE_STC           ││     CONGE_JOURNAL_DROIT     │
 │(Lignes de détail valorisées) ││(Reçu Solde de Tout Compte)   ││ (Grand-Livre de Valorisation)│
 └──────────────────────────────┘└──────────────────────────────┘└─────────────────────────────┘
```

### 3.1 Dictionnaire des Tables Principales

#### 1. `paie_config_legale` (Référentiel des versions fiscales et sociales)
- `id_config_legale` (PK SERIAL), `code_version` (VARCHAR(50) unique), `libelle`
- `date_debut` (DATE), `date_fin` (DATE nullable, convention $[date\_debut, date\_fin[$)
- `plafond_cnaps_ar` (NUMERIC(20,2) : plafond de 8 SME)
- `taux_cnaps_salarial` (NUMERIC(6,4) : 0.0100)
- `taux_cnaps_patronal` (NUMERIC(6,4) : 0.1300)
- `taux_ostie_salarial` (NUMERIC(6,4) : 0.0100)
- `taux_ostie_patronal` (NUMERIC(6,4) : 0.0500)
- `taux_fmfp_patronal` (NUMERIC(6,4) : 0.0100)
- `seuil_hs_tranche1` (NUMERIC(4,2) : 8.00 heures)
- `heures_mensuelles_legales` (NUMERIC(6,2) : 173.33)
- `diviseur_absence_jours` (NUMERIC(4,2) : 30.00).

#### 2. `bareme_irsa` (Tranches de l'impôt sur le revenu salarial)
- `id_tranche` (PK SERIAL), `id_config_legale` (FK), `numero_tranche` (INT : 1 à 5)
- `seuil_inferieur` (NUMERIC(20,2)), `seuil_superieur` (NUMERIC(20,2) nullable)
- `taux_imposition` (NUMERIC(6,4) : 0.00, 0.05, 0.10, 0.15, 0.20)
- `date_debut` (DATE), `date_fin` (DATE nullable)
- Contrainte d'unicité : `uk_bareme_irsa_version_tranche` (`id_config_legale`, `numero_tranche`, `date_debut`).

#### 3. `paie_rubrique` (Catalogue paramétré des rubriques de paie)
- `id_rubrique` (PK SERIAL), `code_rubrique` (VARCHAR(50) unique), `libelle`
- `sens` (VARCHAR(10) : `GAIN` ou `RETENUE`)
- `etape` (VARCHAR(20) : `BRUT`, `NET_APRES_IMPOT`, `RETENUE_TRESORERIE`)
- `imposable_irsa` (BOOLEAN), `soumis_cnaps` (BOOLEAN), `soumis_sante` (BOOLEAN)
- `ordre_affichage` (INT), `est_actif` (BOOLEAN).

#### 4. `bulletin_paie` (En-tête certifié du bulletin mensuel)
- `id_bulletin` (PK SERIAL), `id_employe` (FK), `id_periode_paie` (FK)
- `numero_bulletin` (VARCHAR(50) unique), `date_calcul` (TIMESTAMP), `date_validation` (TIMESTAMP nullable)
- `salaire_base` (NUMERIC(20,2)), `total_heures_travaillees` (NUMERIC(10,4))
- `total_brut` (NUMERIC(20,2)), `total_cotisations_salariales` (NUMERIC(20,2))
- `total_cotisations_patronales` (NUMERIC(20,2)), `net_imposable` (NUMERIC(20,2))
- `impot_irsa` (NUMERIC(20,2)), `impot_isi` (NUMERIC(20,2)), `net_a_payer` (NUMERIC(20,2))
- `cout_total_employeur` (NUMERIC(20,2) : brut + cotisations patronales)
- `id_snapshot_cumul` (FK vers `pointage_cumul_periode`), `id_config_legale` (FK).
- Contrainte d'unicité : `uk_bulletin_employe_periode` (`id_employe`, `id_periode_paie`).

#### 5. `bulletin_paie_ligne` (Lignes de ventilation comptable)
- `id_ligne` (PK SERIAL), `id_bulletin` (FK CASCADE), `id_rubrique` (FK)
- `code_rubrique` (VARCHAR(50)), `libelle` (VARCHAR(200)), `sens` (`GAIN` ou `RETENUE`)
- `base_calcul` (NUMERIC(20,2)), `taux_applique` (NUMERIC(10,4)), `montant` (NUMERIC(20,2))
- `part_patronale` (NUMERIC(20,2) default 0.00).

#### 6. `paie_avance_acompte` (Gestion des avances sur salaire et acomptes)
- `id_avance` (PK SERIAL), `id_employe` (FK), `type_mouvement` (`ACOMPTE` ou `AVANCE_QUINZAINE`)
- `montant_demande` (NUMERIC(20,2)), `montant_accorde` (NUMERIC(20,2))
- `statut` (`DEMANDE`, `VALIDE`, `IMPUTE`, `ANNULE`, `REJETE`)
- `id_periode_paie_imputation` (FK nullable), `date_versement` (DATE).

#### 7. `paie_pret` & `paie_pret_echeance` (Prêts d'entreprise remboursables)
- Table `paie_pret` : `id_pret` (PK SERIAL), `id_employe` (FK), `montant_capital`, `taux_interet` (0% standard), `duree_mois`, `montant_echeance`, `statut` (`ACTIF`, `SOLDE`, `SUSPENDU`).
- Table `paie_pret_echeance` : `id_echeance` (PK SERIAL), `id_pret` (FK), `numero_echeance`, `id_periode_paie`, `montant_capital`, `statut_echeance` (`A_ECHOIR`, `IMPUTE`, `REPORTE`).

#### 8. `paie_stc` (Solde de Tout Compte lors des départs)
- `id_stc` (PK SERIAL), `id_employe` (FK), `id_contrat` (FK), `date_sortie` (DATE)
- `motif_rupture` (VARCHAR(50) : `DEMISSION`, `LICENCIEMENT_ECONOMIQUE`, `LICENCIEMENT_FAUTE`, `RETRAITE`, `FIN_CDD`)
- `indemnite_preavis` (NUMERIC(20,2)), `indemnite_licenciement` (NUMERIC(20,2))
- `indemnite_conge_paye` (NUMERIC(20,2)), `deduction_prets_avances` (NUMERIC(20,2))
- `montant_net_stc` (NUMERIC(20,2)), `statut` (`BROUILLON`, `VALIDE`, `PAYE`).

---

## 4. Sélection Temporelle du Contexte Légal & Barèmes Datés

Le contexte réglementaire et les paramètres du SIRH GALAXAM ne sont jamais résolus au hasard, par une constante figée en dur, ou par simple extraction de l'année civile. L'ensemble des référentiels obéit au **principe d'immutabilité et de fidélité temporelle**.

### 4.1 Règle Générale de Résolution Temporelle Canonique

Pour toute entité paramétrique versionnée (configuration légale, barème fiscal, majoration de sujétion, paramètre global, indemnité de site), l'applicabilité est évaluée selon la condition stricte :

$$\text{date\_debut\_effet} \le \text{date\_metier} \quad \text{ET} \quad (\text{date\_fin\_effet IS NULL} \;\lor\; \text{date\_fin\_effet} \ge \text{date\_metier})$$

La **date métier** de référence dépend exclusivement de la nature de l'opération :

| Domaine | Donnée / Paramètre | Date Métier de Résolution | Source / Classe Responsable |
|---|---|---|---|
| **Pointage & Horaires** | Calendrier, horaires, seuils, majorations HS/nuit/dimanche/férié | Date réelle du jour travaillé (`pointage_jour.date_pointage`) | `TemporalRateResolver.java`, `TemporalAttendanceEngine.java` |
| **Contrat & Carrière** | Salaire de base, catégorie socio-professionnelle, site d'affectation | Date concernée / date d'effet de la ligne contractuelle active | `ContratTravail`, `AffectationCarriere` |
| **Indemnités de site** | Grille d'indemnités conventionnelles par site et dérogations | Date de fin de période pour la paie ou date réelle pour le jour | `SiteIndemniteConfig`, `EmployeIndemniteDerogation` |
| **Cotisations Sociales** | Plafond CNaPS (8 SME), taux CNaPS, OSTIE/santé, FMFP | Date sociale de fin de la période de paie (`dateFinPeriode`) | `PayrollLegalContextResolver.java`, `SocialContributionCalculator.java` |
| **Fiscalité Salariale** | Barème IRSA par tranches, minimum de perception (3 000 Ar), déduction familiale | Date fiscale de clôture de la paie (`dateFinPeriode`) | `PayrollLegalContextResolver.java`, `PayrollTaxCalculations.java` |
| **Solde de Tout Compte** | Taux et règles d'indemnisation de rupture (préavis, licenciement) | Date effective de sortie du collaborateur (`dateSortie`) | `StcService.java`, `PaieStc` |
| **Congés Payés** | Valorisation financière des lots FIFO et prescription triennale | Date d'acquisition du lot / date de clôture de référence | `CongeLotValorisationService.java`, `CongeService.java` |
| **Paramètres Globaux** | Seuils et règles applicatives (`ParametreGlobal`) | • Calculs passés / simulation : `loadActiveMapAtDate(dateMetier)`<br>• Contrôle temps réel : `loadActiveMap()` (`CURRENT_DATE`) | `ParametreGlobal.java` |

### 4.2 Composantes Clés du Pipeline Temporel de Paie

1. **Résolution de la Configuration Légale** :  
   Recherche dans `paie_config_legale` d'une ligne active vérifiant :
   $$\text{date\_debut} \le \text{dateFinPeriode} < \text{date\_fin}$$
   Si aucune version ou si plusieurs versions simultanées sont détectées, une exception bloquante `AMBIGUOUS_CONFIGURATION` ou `MISSING_CONFIGURATION` est immédiatement levée par [`PayrollConfigurationGuard.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/paie/application/PayrollConfigurationGuard.java).
2. **Résolution du Barème IRSA** :  
   Chargement de la totalité des tranches ordonnées par `numero_tranche` dans `bareme_irsa` associées à cette configuration légale. Contrôle d'exhaustivité vérifiant qu'il n'existe aucun trou ni chevauchement de seuils.
3. **Résolution des Taux de Majoration des Sujétions** :  
   Pour chaque type de majoration (`HS_30`, `HS_50`, `NUIT`, `DIMANCHE`, `FERIE`), sélection de l'unique enregistrement applicable dans `ref_taux_majoration` via `TemporalRateResolver.java` à chaque tranche horaire réelle.
4. **Transition d'Exercice & Changements d'Année (ex: 2025 $\rightarrow$ 2026)** :  
   Le cycle de paie décalé (ex: 26 décembre au 25 janvier) chevauche deux années civiles. Le moteur isole rigoureusement les barèmes de l'exercice précédent et du nouvel exercice, garantissant l'absence de régression ou d'ambiguïté (certifié par `TemporalDurabilityReproducibilityIntegrationTest.java#test29_transitionAnnee2025Vers2026`).

---

## 5. Moteur d'Exécution de la Paie en 6 Paliers

Le calculateur central [`PayrollExecutionEngine.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/paie/domain/engine/PayrollExecutionEngine.java) opère de manière séquentielle et déterministe :

```
┌────────────────────────────────────────────────────────────────────────┐
│ PALIERS D'ÉXÉCUTION DU CALCUL DU BULLETIN (PayrollExecutionEngine)     │
├────────────────────────────────────────────────────────────────────────┤
│ PALIER 1 : DÉTERMINATION DES GAINS BRUTS & DÉDUCTIONS HORAIRES         │
│ • Salaire de base contractuel (S_base)                                 │
│ • Retenue pour congé (S_base / 30 × Jours Congé)                       │
│ • Indemnité de congé payé (somme des valorisations lots FIFO)          │
│ • Retenue pour absence résiduelle (Heures Absence × S_base / 173.33)   │
│ • Heures supplémentaires (HS 30% et HS 50%)                            │
│ • Majoration travail de nuit, dimanches et fériés (buckets qualifiés)  │
│ • Indemnités journalières de site (panier, transport)                  │
│ • Variables manuelles de brut (primes d'ancienneté, assiduité, etc.)   │
│ ────────────────────────────────────────────────────────────────────── │
│ PALIER 2 : ASSIETTES ET COTISATIONS SOCIALES                           │
│ • Calcul assiette CNaPS (plafonné à 8 SME)                             │
│ • Calcul cotisation CNaPS salariale (1%) et patronale (13%)            │
│ • Calcul cotisation OSTIE/Santé salariale (1%) et patronale (5%)       │
│ • Calcul contribution patronale FMFP (1% non plafonné)                 │
│ ────────────────────────────────────────────────────────────────────── │
│ PALIER 3 : DÉTERMINATION DU NET IMPOSABLE                              │
│ • Net Imposable = Brut Imposable - Cotisations Salariales Déductibles  │
│ ────────────────────────────────────────────────────────────────────── │
│ PALIER 4 : FISCALITÉ (IRSA OU RETENUE ISI)                             │
│ • Si profil Salarié : Barème progressif IRSA par tranches              │
│   - Application du minimum de perception (3 000 Ar)                   │
│   - Déduction pour enfants à charge (-2 000 Ar / enfant)               │
│ • Si profil Prestataire : Retenue ISI 5% à la source                   │
│ ────────────────────────────────────────────────────────────────────── │
│ PALIER 5 : GAINS NETS NON IMPOSABLES & AJUSTEMENTS POST-IMPÔT          │
│ • Indemnités exonérées d'impôt et non soumises                         │
│ • Rappels ou régularisations nettes directes                           │
│ ────────────────────────────────────────────────────────────────────── │
│ PALIER 6 : RETENUES DE TRÉSORERIE & NET À PAYER                        │
│ • Imputation des acomptes de quinzaine                                 │
│ • Déduction des échéances de prêts d'entreprise                        │
│ • Vérification de la quotité cessible (protection du salarié)          │
│ • NET À PAYER = Net Imposable - Impôt + Gains Nets - Retenues Trésorerie│
└────────────────────────────────────────────────────────────────────────┘
```

---

## 6. Calcul des Cotisations Sociales & Organismes Tiers

Le calcul des cotisations est confié à [`SocialContributionCalculator.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/paie/domain/engine/SocialContributionCalculator.java) :

### 6.1 Cotisations CNaPS (Caisse Nationale de Prévoyance Sociale)

- **Assiette Salariale Plafonnée** :
  $$Assiette_{cnaps} = \min(Brut_{soumis}, \text{PlafondCNaPS})$$
  Où $\text{PlafondCNaPS} = 8 \times \text{SME}$ (lu dans `paie_config_legale.plafond_cnaps_ar`).
- **Cotisation Salariale Retirée** :
  $$Cotisation_{cnaps\_salariale} = Assiette_{cnaps} \times 1\%$$
- **Cotisation Patronale à la Charge de l'Entreprise (13%)** :
  - Prestations Familiales : $Brut \times 1\%$ (déplafonné ou selon convention)
  - Accidents du Travail & Maladies Pro : $Brut \times 1\%$ (déplafonné)
  - Retraite des Travailleurs : $Assiette_{cnaps} \times 11\%$ (plafonné).
  $$Total_{cnaps\_patronal} = Cotisation_{cnaps\_salariale} + \dots = 13\%$$

### 6.2 Organismes Sanitaires Interentreprises (OSTIE / AMIT / FUNHREUMA)

- **Assiette Santé** : Généralement assise sur le salaire brut total non plafonné :
  $$Cotisation_{sante\_salariale} = Brut_{soumis} \times 1\%$$
  $$Cotisation_{sante\_patronale} = Brut_{soumis} \times 5\%$$

### 6.3 Fonds Malgache de Formation Professionnelle (FMFP)

- Contribution de formation continue à la charge exclusive de l'employeur (**Loi n° 2017-025**) :
  $$Cotisation_{fmfp\_patronale} = Brut_{soumis} \times 1\%$$

---

## 7. Moteur Fiscal : Barème IRSA & Retenue ISI

### 7.1 L'Impôt sur les Revenus Salariaux et Assimilés (IRSA)

Orchestré par [`PayrollTaxCalculations.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/paie/domain/engine/PayrollTaxCalculations.java) :

1. **Base Nette Imposable ($N_I$)** :
   $$N_I = Brut_{imposable} - Cotisation_{cnaps\_salariale} - Cotisation_{sante\_salariale}$$
2. **Barème Progressif par Tranches (CGI Art. 01.03.01)** :
   Pour chaque tranche $k$ définie par $[Seuil_{inf}(k), Seuil_{sup}(k)]$ et son taux $T_k$ :
   $$Assiette_k = \max\Big(0, \min(N_I, Seuil_{sup}(k)) - Seuil_{inf}(k)\Big)$$
   $$Impot_{brut} = \sum_k (Assiette_k \times T_k)$$
   *Barème canonique LF 2026* :
   - 0 à 350 000 Ar : **0%**
   - 350 001 à 400 000 Ar : **5%**
   - 400 001 à 500 000 Ar : **10%**
   - 500 001 à 600 000 Ar : **15%**
   - Au-delà de 600 000 Ar : **20%**
3. **Minimum de Perception Légal** :
   Le Code Général des Impôts impose un impôt minimum de **3 000 Ar** dès lors que le salaire est imposable :
   $$Impot_{plancher} = \max(Impot_{brut}, 3\,000\text{ Ar})$$
4. **Réduction pour Charges de Famille (CGI Art. 01.03.14)** :
   Déduction légale de **2 000 Ar par mois par personne à charge** (enfants légitimes mineurs déclarés dans `employe.nb_enfants_charge`) :
   $$Reduction = \text{nb\_enfants\_charge} \times 2\,000\text{ Ar}$$
   $$IRSA_{final} = \max(3\,000\text{ Ar}, Impot_{plancher} - Reduction)$$
   *(Note d'audit : le minimum de perception de 3 000 Ar s'applique après ou avant déduction selon la version de loi de finances en vigueur persistée dans `paie_config_legale`).*

### 7.2 L'Impôt Synthétique Intermittent (ISI)

Pour les collaborateurs et experts sous contrat de prestation d'honoraires (`paie_honoraire_prestation`) :
- Aucune cotisation CNaPS ni OSTIE n'est prélevée.
- Prélèvement libératoire à la source au taux de **5%** (**CGI Art. 01.03.22**) :
  $$ISI = Montant_{honoraire} \times 5\%$$
  $$Net = Montant_{honoraire} - ISI$$

---

## 8. Variables de Paie, Acomptes, Avances & Prêts

### 8.1 Workflow des Variables d'Ajustement

Chaque prime, rappel ou retenue exceptionnelle fait l'objet d'un mouvement formalisé dans `paie_ajustement` :
- Statuts de cycle : `BROUILLON` $\rightarrow$ `EN_ATTENTE` $\rightarrow$ `VALIDE` ou `REJETE`.
- **Règle d'or** : Le calcul réel de paie **bloque immédiatement** si une variable demeure au statut `EN_ATTENTE`. Toutes les variables doivent être explicitement arbitrées par la DAF.
- Une variable validée est consommée lors du calcul du bulletin (`statut = 'CONSOMME'`). En cas de recalcul d'un brouillon de paie, l'imputation est transactionnellement restituée.

### 8.2 Apurement des Avances et Prêts Remboursables

1. **Acomptes de Quinzaine** : Déduits intégralement du net du mois dès lors que leur date de versement appartient à la période de paie.
2. **Échéancier de Prêt** : L'échéance courante de chaque prêt actif (`paie_pret_echeance.statut_echeance = 'A_ECHOIR'`) est imputée sur le bulletin. Le capital restant dû est immédiatement mis à jour :
   $$Capital_{restant} = Capital_{precedent} - Montant_{echeance}$$
3. **Plafond Légal de Retenue (Quotité Cessible — Loi 2024-014 Art. 140)** :  
   Le cumul des retenues de trésorerie ne peut en aucun cas priver le travailleur de son minimum vital. Toute retenue excessive est plafonnée par le moteur avec report du reliquat d'échéance.

---

## 9. Solde de Tout Compte (STC) & Ruptures de Contrat

Le calcul de rupture est opéré par [`StcService.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/paie/application/StcService.java) et formalisé dans `paie_stc` :

### 9.1 Composantes Indemnitaires du STC

1. **Indemnité Compensatrice de Préavis** (**Loi 2024-014, Art. 33**) :  
   Due en cas de dispense de préavis à l'initiative de l'employeur. Égale à la rémunération intégrale qu'aurait perçue le collaborateur jusqu'au terme du préavis.
2. **Indemnité Légale de Licenciement** (**Loi 2024-014, Art. 35**) :  
   Due pour tout salarié licencié (hors faute lourde) justifiant d'au moins un an d'ancienneté continue. Calculée selon le barème conventionnel ou légal sur la base du salaire moyen des 12 derniers mois.
3. **Indemnité Compensatrice de Congés Payés** (**Loi 2024-014, Art. 131**) :  
   Liquidation intégrale de la valeur des congés acquis non pris. Interrogation de `CongeLotValorisationService` pour solder la totalité des lots FIFO restants :
   $$Indemnite_{conge} = \sum_{lot} \text{Valeur restante du lot}$$
4. **Déduction des Engagements Financiers** :  
   Apurement immédiat du capital restant dû sur les prêts d'entreprise en cours et des avances non encore amorties.
5. **Édition du Reçu pour Solde de Tout Compte** :  
   Document juridique libératoire formalisé (`GET /paie/stc/{id}/recu-pdf`) avec mention manuscrite obligatoire et inventaire détaillé de chaque indemnité versée.

---

## 10. Machine à États & Clôture Transactionnelle

### 10.1 Machine à États d'une Campagne de Paie

```
   ┌──────────────┐
   │  BROUILLON   │
   └──────┬───────┘
          │ Commande : OPEN
          ▼
   ┌──────────────┐
   │   EN_COURS   │ (Saisie des pointages et des variables)
   └──────┬───────┘
          │ Commande : SUBMIT
          ▼
   ┌──────────────┐      Commande : REJECT (Motif obligatoire)
   │  A_VALIDER   │ ─────────────────────────────────────────────┐
   │(TRANSMIS_DAF)│                                              │
   └──────┬───────┘                                              │
          │ Commande : APPROVE (Gel du Pointage)                 │
          ▼                                                      │
   ┌──────────────┐ ◄────────────────────────────────────────────┘
   │   VALIDEE    │ (Pointage scellé sous statut 'GELE')
   └──────┬───────┘
          │ Action : CALCULER LA PAIE (Brouillons de bulletins générés)
          │ Commande : VALIDATE_PAYROLL
          ▼
   ┌──────────────┐
   │ PAIE_VALIDEE │ (Bulletins certifiés, nets à payer verrouillés)
   └──────┬───────┘
          │ Commande : CLOSE
          ▼
   ┌──────────────┐
   │   CLOTUREE   │ (Archivage comptable, génération Droit Congé N+1)
   └──────┬───────┘
          │ Commande exceptionnelle : REOPEN (Motif >= 5 caractères, DAF)
          └─────────────────────────────► (Rétrogradation vers EN_COURS)
```

### 10.2 Matrice de Correspondance des Statuts Pointage et Paie

Source de vérité : [`PayrollPeriodWorkflowService.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/paie/application/workflow/PayrollPeriodWorkflowService.java) pour les transitions, capacités, étapes et prochaine destination ; [`PayrollWorkflowStatusResolver.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/paie/application/workflow/PayrollWorkflowStatusResolver.java) pour les deux statuts affichés. Le frontend consomme `workflowStatuses`, `campaignSteps`, `availableActions` et `nextStep`.

| État de période | Pointage affiché | Paie affichée | Étape suivante possible |
| --- | --- | --- | --- |
| `BROUILLON` | À ouvrir | En attente du pointage | Ouverture / saisie selon les capacités |
| `EN_COURS` | Saisie en cours | En attente du pointage | Saisie ou transmission |
| `A_VALIDER`, `TRANSMIS_DAF` | À valider | En attente du pointage | Validation ou correction du pointage |
| `VALIDEE`, aucun bulletin | Pointage figé | À préparer | Préparation de la paie |
| `VALIDEE`, bulletins incomplets pour la population éligible | Pointage figé | Calcul incomplet | Reprendre le calcul des salariés manquants |
| `VALIDEE`, bulletins calculés | Pointage figé | À contrôler | Contrôle et validation des bulletins |
| `VALIDEE`, calcul obsolète | Pointage figé | À recalculer | Nouveau calcul |
| `VALIDEE`, bulletins tous validés | Pointage figé | Validation à finaliser | Finalisation de la paie |
| `PAIE_VALIDEE` | Pointage figé | Paie validée | Sorties réglementaires |
| `CLOTUREE` | Pointage figé | Paie clôturée | Consultation des sorties |

> [!NOTE]
> `VALIDEE` valide le pointage et fige son snapshot. Ce code ne signifie pas que la paie est validée. Le statut Paie dépend aussi des bulletins et de l'obsolescence du calcul. `PAIE_VALIDEE` est l'état de validation de la paie. La clôture exige des bulletins validés.
> Les transitions autorisées sont `BROUILLON → EN_COURS → TRANSMIS_DAF/A_VALIDER → VALIDEE → PAIE_VALIDEE → CLOTUREE`, avec rejet vers `EN_COURS`, réouverture vers `EN_COURS` et retour de `PAIE_VALIDEE` à `VALIDEE` lors d'un recalcul.

### 10.3 Intangibilité & Verrous Pessimistes

Toutes les écritures de validation de bulletin et de clôture sont protégées par `TransactionRunner` avec des verrous pessimistes :
```sql
SELECT id_bulletin FROM bulletin_paie WHERE id_bulletin = ? FOR UPDATE;
SELECT id_employe FROM employe WHERE id_employe = ? FOR UPDATE;
```
Dès que la période passe au statut `CLOTUREE`, aucun champ de `bulletin_paie` ni de `bulletin_paie_ligne` ne peut être altéré.

---

## 11. Exports Réglementaires & Déclarations Officielles

Le module Paie produit l'intégralité des liasses administratives exigées par les autorités malagasy :

### 11.1 Télé-déclaration Fiscale SAFI (`SafiExportService.java`)
- **Formats produits** : Fichier structuré CSV et classeur tableur ODS conforme à la maquette officielle de la DGI (`modele_declaration_irsa_safi.ods`).
- **Contenu** : NIF, STAT, matricule salarié, nom et prénoms, fonction, salaire brut, déductions sociales autorisées, net imposable, montant de l'IRSA dû.
- **Endpoint** : `GET /paie/exports/safi?idPeriodePaie=...&format=ods`.

### 11.2 Déclaration Nominative Trimestrielle CNaPS (`DntsExportService.java`)
- Génération du document **DNTS** ventilé par trimestre civil (T1, T2, T3, T4).
- Récapitule par salarié : numéro d'affiliation CNaPS, salaires bruts mensuels des 3 mois, assiettes plafonnées, cotisations 1% salariale et 13% patronale.

### 11.3 Fichiers de Virement Bancaire (`VirementBancaireExportService.java`)
- Génération des ordres de virement collectif au format interbancaire malagasy normalisé.
- Regroupement automatique par banque de domiciliation (ex : BNI Madagascar, BMOI, Bank of Africa, Société Générale Madagasikara) avec contrôle des clés RIB (23 caractères).

### 11.4 Bulletins de Paie PDF Haute Fidélité (`BulletinDocumentService.java`)
- Édition unitaire ou groupée de l'ensemble des bulletins de paie d'une période.
- Présentation conforme aux exigences du Code du Travail : identification de l'employeur, numéro d'affiliation CNaPS et OSTIE, matricule, emploi et classification professionnelle, détail chronologique des gains et retenues, cumul annuel, net à payer en chiffres et en lettres.

---

## 12. Cartographie des Composants Backend & Frontend

### 12.1 Backend Java (`mg.galaxam.sirh.paie` & `mg.galaxam.sirh.paieconfig`)

Le sous-système de paie est composé de plus de 90 classes de production :

```
mg.galaxam.sirh.paie/
├── api/
│   ├── PaieAjustementController.java        # Gestion des primes, retenues et acomptes
│   ├── PaieController.java                  # Calcul, validation des bulletins, simulation, exports
│   ├── PayrollCockpitController.java        # Cockpit de pilotage et KPIs de paie
│   └── StcController.java                   # Calcul et édition du Solde de Tout Compte
├── application/
│   ├── ManualRubriqueResolver.java          # Résolution des rubriques manuelles et gouvernance
│   ├── PayrollCockpitService.java           # Indicateurs de pilotage de la campagne
│   ├── PayrollConfigurationGuard.java       # Contrôles préflight des configurations datées
│   ├── PayrollFiscalRulesResolver.java      # Résolution fiscale IRSA et ISI
│   ├── PayrollLegalContextResolver.java     # Sélection à date de la version légale applicable
│   ├── PayrollPopulationResolver.java       # Sélection de la population active rémunérable
│   ├── PayrollService.java                  # Orchestrateur central transactionnel de paie
│   ├── StcService.java                      # Calcul complet et édition du reçu STC
│   ├── export/                              # Services d'export (SAFI, DNTS, Virement, PDF...)
│   └── workflow/                            # Machine à états des périodes et des ajustements
├── domain/
│   ├── engine/
│   │   ├── MoneyRoundingPolicy.java         # Règle d'arrondi monétaire à 2 décimales
│   │   ├── OvertimePayCalculator.java       # Calculateur valorisé des heures supplémentaires
│   │   ├── PayrollExecutionEngine.java      # Moteur central de calcul du bulletin en 6 paliers
│   │   ├── PayrollTaxCalculations.java      # Algorithme du barème IRSA et minimum de perception
│   │   └── SocialContributionCalculator.java# Calculateur des cotisations CNaPS, Santé et FMFP
│   └── dto/                                 # Contrats et DTO de calcul et d'export
└── persistence/
    ├── BulletinPaie.java                    # Modèle JDBC de l'en-tête de bulletin
    ├── BulletinPaieLigne.java               # Modèle JDBC des lignes de ventilation
    ├── PaieAvanceAcompte.java               # Modèle JDBC des avances et acomptes
    ├── PaieHonorairePrestation.java         # Modèle JDBC des honoraires sous ISI
    ├── PaiePret.java                        # Modèle JDBC des prêts d'entreprise
    ├── PaiePretEcheance.java                # Modèle JDBC des échéances de remboursement
    ├── PaieRubrique.java                    # Modèle JDBC du catalogue des rubriques
    └── PaieStc.java                         # Modèle JDBC du Solde de Tout Compte
```

### 12.2 Frontend React (`frontend/src/features/payroll/`)

- `PayrollCampaignShell.jsx` : Stepper de navigation guidé (Population $\rightarrow$ Pointage gelé $\rightarrow$ Variables $\rightarrow$ Calcul $\rightarrow$ Contrôle $\rightarrow$ Validation $\rightarrow$ Sorties).
- `VariablesAjustementsTab.jsx` : Grille de saisie et d'approbation des variables DAF.
- `AcomptesAvancesPage.jsx` : Gestion des acomptes de quinzaine et avances sur salaire.
- `PretsSalariesPage.jsx` : Suivi des contrats de prêt et des tableaux d'amortissement.
- `BulletinPaieDrawer.jsx` : Tiroir de consultation détaillée du bulletin avec ventilation des rubriques.
- `StcPage.jsx` : Console de calcul et d'édition du Solde de Tout Compte.

---

## 13. Catalogue Exhaustif des Règles de Gestion (RG)

| Identifiant RG | Énoncé Formel de la Règle | Fondement Légal / Réglementaire | Composant Responsable |
| :--- | :--- | :--- | :--- |
| **RG-PAI-01** | **Consommation exclusive du pointage gelé** : Le calcul de paie réel est impossible si la période de pointage n'a pas été scellée sous statut `GELE`. Aucune lecture directe sur `pointage_jour`. | Ordre public social & Intégrité comptable | [`PayrollService.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/paie/application/PayrollService.java) |
| **RG-PAI-02** | **Approbation préalable des variables** : Toute variable en statut `EN_ATTENTE` bloque immédiatement le lancement du calcul batch. Seules les variables `VALIDE` sont imputées. | Contrôle interne & Auditabilité | [`PayrollService.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/paie/application/PayrollService.java) |
| **RG-PAI-03** | **Décompte des congés au trentième et valorisation FIFO** : La retenue pour congé s'opère au 1/30e ($S_{base}/30 \times J$) et l'indemnité au taux historique du lot FIFO. Les deux lignes sont indépendantes et ne s'annulent pas. | **Loi 2024-014, Art. 125 & 131** | [`PayrollExecutionEngine.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/paie/domain/engine/PayrollExecutionEngine.java) |
| **RG-PAI-04** | **Retenue pour absence au taux horaire strict** : L'absence résiduelle issue du pointage est déduite selon le diviseur légal mensuel : $\text{Retenue} = \text{Heures} \times (S_{base} / 173{,}33)$ sans conversion préalable en centièmes d'heure. | Décret n° 2007-563 | [`PayrollExecutionEngine.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/paie/domain/engine/PayrollExecutionEngine.java) |
| **RG-PAI-05** | **Ventilation légale des heures supplémentaires** : Les HS validées sont ventilées jusqu'à 8h hebdomadaires à 30%, et l'excédent à 50%. | Décret n° 72-226, Art. 2 | [`OvertimePayCalculator.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/paie/domain/engine/OvertimePayCalculator.java) |
| **RG-PAI-06** | **Sélection à date d'effet de la version légale** : La configuration fiscale et sociale est sélectionnée à la date de fin de période. Toute ambiguïté (versions multiples) bloque le calcul. | Principe de légalité fiscale | [`PayrollLegalContextResolver.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/paie/application/PayrollLegalContextResolver.java) |
| **RG-PAI-07** | **Plafonnement des cotisations CNaPS** : La cotisation salariale CNaPS (1%) est plafonnée à $8 \times \text{SME}$. La cotisation patronale CNaPS est de 13%. | Décret n° 69-145 | [`SocialContributionCalculator.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/paie/domain/engine/SocialContributionCalculator.java) |
| **RG-PAI-08** | **Application stricte du barème IRSA & Minimum de perception** : L'IRSA est calculé par tranches progressives avec application d'un plancher minimum de **3 000 Ar**. | CGI Madagascar, Art. 01.03.01+ | [`PayrollTaxCalculations.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/paie/domain/engine/PayrollTaxCalculations.java) |
| **RG-PAI-09** | **Réduction fiscale pour charges de famille** : Déduction de **2 000 Ar par mois par enfant à charge** sur l'impôt brut calculé. | CGI Madagascar, Art. 01.03.14 | [`PayrollTaxCalculations.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/paie/domain/engine/PayrollTaxCalculations.java) |
| **RG-PAI-10** | **Retenue libératoire ISI pour prestataires** : Les honoraires de prestataires externes font l'objet d'une retenue à la source unique de **5%**, exonérée de cotisations sociales. | CGI Madagascar, Art. 01.03.22 | [`PayrollExecutionEngine.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/paie/domain/engine/PayrollExecutionEngine.java) |
| **RG-PAI-11** | **Restauration transactionnelle des imputations** : La simulation ou le recalcul d'un bulletin brouillon restaure intégralement les imputations d'avances et de prêts avant réapplication. | Intégrité financière & Idempotence | [`PayrollService.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/paie/application/PayrollService.java) |
| **RG-PAI-12** | **Intangibilité des bulletins validés** : Un bulletin au statut `VALIDE` ou `CLOTURE` ne peut être ni modifié ni recalculé. Toute correction exige un parcours de régularisation auditée. | Code du Travail & Traçabilité | [`PayrollService.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/paie/application/PayrollService.java) |
| **RG-PAI-13** | **Liquidation des congés au STC** : Le Solde de Tout Compte liquide obligatoirement la totalité de la valeur financière des lots de congés non pris. | **Loi 2024-014, Art. 131** | [`StcService.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/paie/application/StcService.java) |

---

## 14. Étude de Cas Chiffrée & Rapprochement de Recette

Rapprochement certifié sur la période du 26/07 au 25/08/2026 comparant les deux collaborateurs de référence d'entreprise (TMM et TNR) :

| Rubrique / Composante du Bulletin | Collaborateur TMM (Tamatave) | Collaborateur TNR (Antananarivo) | Justification Métier |
| :--- | ---: | ---: | :--- |
| **Salaire de Base Contractuel** | **370 000,00 Ar** | **350 000,00 Ar** | Contrat de travail en vigueur |
| **Heures Supplémentaires Valorisées** | **17 899,09 Ar** (387 min HS 30%) | **0,00 Ar** (Dépassements absorbés) | Après compensation hebdomadaire et arbitrage DAF |
| **Retenue Congé au Trentième (1/30e)** | **-111 000,00 Ar** (9 jours pris) | **-35 000,00 Ar** (3 jours pris) | $S_{base} / 30 \times \text{Jours}$ |
| **Indemnité de Congé Payé Historique** | **+86 967,81 Ar** | **+24 670,50 Ar** | Consommation des lots FIFO au TCONGE du cycle |
| **Retenue pour Absence Résiduelle** | **0,00 Ar** (0 min) | **-673,09 Ar** (20 min d'absence) | $20 / 60 \times (350\,000 / 173{,}33)$ |
| **Total Brut Comparable SIRH** | **363 866,90 Ar** | **338 997,41 Ar** | Base brute avant variables d'indemnité |
| **Indemnité de Déplacement / Transport** | 35 272,73 Ar | 28 363,64 Ar | Dénommée historiquement dans `AUTIMP` |
| **Indemnité de Restauration / Panier** | 0,00 Ar (Cantine de site gratuite) | 44 000,00 Ar (Repas payés) | Site à montant zéro vs site standard |
| **Cotisation CNaPS Salariale (1%)** | **-3 991,40 Ar** | **-4 113,68 Ar** | 1% du brut soumis plafonné à 8 SME |
| **Cotisation OSTIE Salariale (1%)** | **-3 991,40 Ar** | **-4 113,68 Ar** | 1% du brut total |
| **Net Imposable** | **391 156,83 Ar** | **403 140,42 Ar** | Brut imposable - CNaPS - OSTIE |
| **Impôt IRSA Calculé (Barème Progressif)**| **3 000,00 Ar** (Minimum de perception) | **3 314,04 Ar** (Tranche 5% entamée) | Barème progressif CGI LF 2026 |
| **Réduction par Enfant à Charge** | **0,00 Ar** (0 enfant) | **-2 000,00 Ar** (1 enfant à charge) | Application de l'Art. 01.03.14 du CGI |
| **IRSA Net Déduit** | **3 000,00 Ar** | **3 000,00 Ar** (Plancher légal maintenu) | Règle du minimum de perception de 3 000 Ar |
| **Total Net à Payer** | **388 156,83 Ar** | **400 140,42 Ar** | Net versé sur compte bancaire |

---

*Document officiel d'ingénierie logicielle, d'architecture comptable et de conformité légale GALAXAM SIRH — Conforme Code du Travail et Code Général des Impôts malgache 2026.*
