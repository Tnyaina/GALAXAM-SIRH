# Module Congés & Absences — Spécification Complète & Rapport de Référence
## GALAXAM SIRH — Documentation Canonique & Métier

> **Auteur** : Équipe d'Ingénierie & Architecture GALAXAM SIRH  
> **Module** : Congés, Absences, Droits Acquis, Grand-Livre (Ledger), Continuité & Valorisation Financière  
> **Version** : 3.3.0 (État réel du code source & certification transactionnelle — 90 tests JUnit 5 verts)  
> **Date de référence** : Octobre 2026  
> **Principes directeurs** : Le code source Java/SQL est le moteur et la source principale de vérité du système. Toute règle métier implémentée est adossée à une source légale ou réglementaire vérifiable.  
> **Sources juridiques et normatives de référence** :
> - [**Loi n° 2024-014 du 14 août 2024 portant Code du Travail**](https://cnlegis.gov.mg/uploads/L2024-014-VF.pdf) (adoptée le 01/07/2024, validée par la HCC le 08/08/2024 [Décision n° 11-HCC/D3](https://www.hcc.gov.mg/?p=9311), promulguée le 14/08/2024 ; Articles 125 à 135, 142 à 144)
> - [**Loi n° 2003-044 du 28 juillet 2004**](https://www.assemblee-nationale.mg/media/archives_textes/pl/2003-044/Loi-n-2003-044-finale-Portant-Code-du-Travail-1.pdf) portant ancien Code du Travail de Madagascar (Articles 13, 86, 87, 88, 89, 90, 91, 95, 97, 98, 126, 134 — conservés pour comparatif et traçabilité historique)
> - [**Décret n° 72-226 du 6 juillet 1972**](https://fr.scribd.com/document/887714691/Droit-Malgache-Du-Travail) et Loi n° 2024-014 (Art. 109) relatifs à la durée légale du travail (40 heures hebdomadaires = 173,33 heures mensuelles dans les entreprises non agricoles)
> - [**Décret n° 64-161 du 22 avril 1964**](https://cabinetkim.mg/blog/code-du-travail-madagascar-nouvelle-loi-2024-014/) fixant les conditions et modalités d'application du régime des congés payés
> - **Modèle mathématique d'acquisition et valorisation financière d'entreprise** : `OHATRA KAJY DROITS CONGES-given on sept26.xls` (Convention de gestion interne DAF assumée face au risque prud'homal du salaire continué Art. 131)
> - **Schémas de base de données canoniques** : `backend/conception/bdd/04_schema_conges.sql` & `backend/conception/bdd/06_schema_paie.sql`

---

## Sommaire Exécutif

Le présent document constitue la **source unique de vérité documentaire, juridique et technique** du module **Congés & Absences** du SIRH GALAXAM.  
Il est rédigé selon le principe fondamental où **le code source opérationnel conduit et reflète fidèlement la réalité**, soutenu par des justifications légales et normatives documentées :

1. **L'alignement direct sur le code Java et PostgreSQL** : 31 fichiers de production (`mg.galaxam.sirh.conge`), 11 tables relationnelles dédiées (`04_schema_conges.sql`), 1 table de journal de droits de paie (`06_schema_paie.sql`), 3 vues analytiques, 90 tests automatisés d'intégration et unitaires 100% passants (`BUILD SUCCESS`).
2. **La gestion unifiée des Deux Flux Fondamentaux** : Traitement rigoureux et étanche du **Flux 1 (Nouvel Employé / Embauche Native)** avec carence légale de 12 mois (Art. 130) et du **Flux 2 (Reprise Historique / Cutover à date d'arrêté)** sans import d'historique lourd, avec reprise du stock arrêté, reprise des cumuls intermédiaires d'exercice et audit DAF via `conge_cutover_correction`.
3. **Le respect des règles légales de décompte** : Résolution temporelle dynamique des jours fériés via `CongeDecompteCalendaireResolver` et le paramètre global `CONGE_DECOMPTE_FERIES` (`INCLUS` ou `EXCLUS`), permettant l'application conforme de l'Article 127 du Code du Travail 2024.
4. **Le verrouillage strict du solde débiteur** : Interdiction absolue de la prise par anticipation en solde négatif au niveau métier (`DemandeConge.java`, `CongeService.java`, `DemandeCongeController.java`).
5. **La protection de la continuité opérationnelle** : Détection algorithmique des collisions de présence d'équipe par poste et site géographique (`CongeContinuiteService.java`) avec dérogation bloquante.
6. **Le modèle mathématique canonique (`OHATRA KAJY DROITS CONGES`)** : Proratisation au trentième, valorisation mensuelle $V_m = \frac{B_{totale} \times D_m}{30}$, constitution de lots FIFO immuables et calcul du taux journalier unitaire de cycle `TCONGE`.
7. **Le Grand-Livre des congés (`mouvement_solde_conge`) & Audit DAF** : Traçabilité absolue des mouvements (débits `PRISE`, crédits `ACQUISITION`, `BONIFICATION`, `AJUSTEMENT`, `ANNULATION`, `EXPIRATION`), audit des corrections de cutover (`conge_cutover_correction`), rectifications de valorisation (`conge_valorisation_mensuelle_correction`) et examen motivé des droits candidats à prescription (`conge_expiration_examen`).
8. **L'interconnexion certifiée** avec les modules **Pointage** (qualification des présences et régularisations automatiques), **Paie** (retenue au 1/30e et indemnité de congé valorisée) et **STC** (liquidation des reliquats de lots).

---

## Table des Matières

1. [Cadre Légal et Réglementaire Malagasy](#1-cadre-légal-et-réglementaire-malagasy)
2. [Modèle Mathématique Canonique & Les Deux Flux Fondamentaux](#2-modèle-mathématique-canonique-ohatra-kajy-droits-conges)
   - 2.1 [Définition des Variables Mensuelles & Trentième](#21-définition-des-variables-mensuelles)
   - 2.2 [Résolution du Taux Journalier Applicable (TCONGE)](#22-résolution-du-taux-journalier-applicable-tconge)
   - 2.3 [Les Deux Flux Fondamentaux : Nouvel Employé vs Reprise Cutover](#23-les-deux-flux-fondamentaux-de-gestion-des-congés)
3. [Architecture du Modèle de Données PostgreSQL (11 Tables, 1 Table Paie, 3 Vues)](#3-architecture-du-modèle-de-données-postgresql)
   - 3.1 [Dictionnaire des 11 Tables Relationnelles Dédiées](#31-dictionnaire-des-11-tables-relationnelles-dédiées)
   - 3.2 [Vues Analytiques SQL Dédiées](#32-vues-analytiques-sql-dédiées)
4. [Règles de Gestion Métier (RG) Détaillées](#4-règles-de-gestion-métier-rg-détaillées)
5. [Workflow & Circuit de Validation Transactionnel](#5-workflow--circuit-de-validation-transactionnel)
6. [Intégrations Certifiées : Pointage, Paie & STC](#6-intégrations-certifiées--pointage-paie--stc)
7. [Automatisations, FIFO & Schedulers Nocturnes](#7-automatisations-fifo--schedulers-nocturnes)
8. [Cartographie des Composants Logiciels (Backend, Frontend & REST)](#8-cartographie-des-composants-logiciels-backend--frontend)
   - 8.1 [Backend Java (31 Fichiers de Production Confirmés)](#81-backend-java-31-fichiers-de-production-confirmés)
   - 8.2 [Frontend React Backend-Driven](#82-frontend-react-backend-driven-frontendsrcfeaturesleave)
   - 8.3 [Catalogue Complet des Endpoints REST](#83-catalogue-complet-des-endpoints-rest)
9. [Matrice des Codes d'Erreur & Réponses API Normalisées](#9-matrice-des-codes-derreur--réponses-api-normalisées)
10. [Éditions et Exports Excel Certifiés](#10-éditions-et-exports-excel-certifiés)
11. [Données d'Audit Intégrales du Modèle Réel (Matricule 61)](#11-données-daudit-intégrales-du-modèle-réel-matricule-61)
12. [Recueil Textuel Intégral des Lois Malagasy (Loi 2024-014 & Loi 2003-044)](#12-recueil-textuel-intégral-des-lois-malagasy-loi-2024-014--loi-2003-044)

---

## 1. Cadre Légal et Réglementaire Malagasy

### 1.1 Tableau Synoptique des Fondements Juridiques

Les congés payés sont régis par des dispositions d'**ordre public absolu** : toute convention individuelle ou collective prévoyant l'abandon du droit à congé contre indemnité pécuniaire en cours d'exécution du contrat est **nulle de plein droit**.

| Article Code du Travail (Loi 2024-014 / Loi 2003-044) | Libellé & Portée Juridique | Traduction Technique & Classe Java Responsable |
| :--- | :--- | :--- |
| **Loi 2024-014, Art. 125**<br>*(Loi 2003-044, Art. 86)* | **Acquisition du droit à congé payé**<br>• 2,5 jours calendaires par mois de service effectif (30 jours par an).<br>• Acquisition à la charge exclusive de l'employeur. | • [`CongeService.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/conge/application/CongeService.java) (`enregistrerAcquisitionMensuelle`).<br>• Stockage mensuel dans `conge_journal_droit` et crédit dans `mouvement_solde_conge` (`ACQUISITION`). |
| **Loi 2024-014, Art. 127** | **Exclusion des jours fériés du congé payé**<br>• *« Sont exclus du décompte des congés payés les jours fériés chômés et payés... »* | • [`CongeDecompteCalendaireResolver.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/conge/application/CongeDecompteCalendaireResolver.java).<br>• Résolution par jour du paramètre `CONGE_DECOMPTE_FERIES` (`INCLUS`/`EXCLUS`). Si `EXCLUS`, déduction automatique des fériés nationaux et locaux (`site_calendrier_ferie`). |
| **Loi 2024-014, Art. 130**<br>*(Loi 2003-044, Art. 88 al. 1)* | **Ouverture du droit de jouissance**<br>• La jouissance du congé annuel est acquise après une période minimale de **12 mois de service effectif**. | • [`DemandeConge.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/conge/persistence/DemandeConge.java#L223-L242) : contrôle de l'ancienneté contractuelle (`moisService >= 12`) sur les congés de type `ANNUEL`. Blocage immédiat sinon. |
| **Loi 2024-014, Art. 129**<br>*(Loi 2003-044, Art. 87)* | **Prescription triennale & Protection du salarié**<br>• Prescription de **3 ans** à compter de la fin de la période de référence.<br>• Le salarié peut combattre la prescription s'il établit que l'employeur l'a empêché de jouir de ses droits. | • [`CongeExpirationExamenService.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/conge/application/CongeExpirationExamenService.java) & table `conge_expiration_examen`.<br>• **Zéro purge automatique**. Revue DAF formelle : `REPORTE` ou `EXPIRE` avec confirmation obligatoire `planification_verifiee = true`. |
| **Loi 2024-014, Art. 130**<br>*(Loi 2003-044, Art. 89 & 95)* | **Fractionnement, Tranche continue & Continuité**<br>• Fractionnement possible d'accord parties avec au moins **une première tranche continue d'au moins 15 jours consécutifs** (Loi 2024-014, Art. 130).<br>• Départ fixé par l'employeur selon nécessités de service. | • [`CongeCalculateurEngine.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/conge/domain/CongeCalculateurEngine.java) & paramètre `DUREE_TRANCHE_MIN_FRACTIONNEMENT_JOURS = 15`.<br>• [`CongeContinuiteService.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/conge/application/CongeContinuiteService.java) : détection des conflits sur le même poste/site. |
| **Loi 2024-014, Art. 126 & 133**<br>*(Loi 2003-044, Art. 88)* | **Permissions pour événements de famille (10 jours/an)**<br>• Quota légal de **10 jours par an** non déductibles du congé annuel si payées.<br>• Justificatif dans les 6 mois. La ventilation par motif relève de la Convention Collective d'entreprise. | • [`RefPoolCongeAnnuel.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/conge/persistence/RefPoolCongeAnnuel.java) : pool `PERMISSIONS_FAMILIALES` plafonné à 10,0 j/an.<br>• Quotas individuels : Mariage (3j), Décès conjoint/enfant (3j), Naissance (2j), Décès parent (2j). |
| **Loi 2024-014, Art. 134**<br>*(Loi 2003-044, Art. 126 & 134)* | **Congé de paternité autonome**<br>• **3 jours ouvrables rémunérés** à la naissance d'un enfant.<br>• Droit autonome, expressément **disjoint du pool de 10 jours**. | • [`RefTypeConge.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/conge/persistence/RefTypeConge.java) : `id_pool_conge_annuel IS NULL` sur le code `PATERNITE`. `duree_fixe_jours = 3.0`. |
| **Loi 2024-014, Art. 142 & 143**<br>*(Loi 2003-044, Art. 97 & 98)* | **Congé de maternité & Extension pathologique**<br>• **14 semaines consécutives (98 jours)** dont 6 post-accouchement.<br>• Prolongation de **3 semaines (21 jours)** en cas d'état pathologique attesté (portant à 119 jours).<br>• Maintien intégral des droits à congés payés. | • Paramètres `DUREE_MAX_MATERNITE_STANDARD_JOURS = 98` et `DUREE_MAX_MATERNITE_PATHOLOGIQUE_JOURS = 119`.<br>• Flag `extension_pathologique_maternite` dans `demande_conge`.<br>• Assimilation à du travail effectif à 100%. |
| **Loi 2003-044, Art. 13** | **Maladie ordinaire & Relais de prévoyance CNaPS**<br>• Suspension jusqu'à 6 mois.<br>• Maintien légal de salaire limité au préavis (**30 jours** pour mensuels). | • `RefTypeConge` : type `MALADIE` borné à 30 jours continus payés.<br>• Bascule automatique dès le 31e jour vers `MALADIE_SANS_SOLDE` (`id_type_conge_bascule = 1`) pour prise en charge CNaPS. |
| **Loi 2024-014, Art. 126**<br>*(Loi 2003-044, Art. 86)* | **Bonifications pour ancienneté**<br>• Majoration de la durée des congés payés selon convention ou règlement d'entreprise. | • Moteur pur [`CongeCalculateurEngine.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/conge/domain/CongeCalculateurEngine.java) : seuil d'activation $\ge 5$ ans révolus, bonus $+1$ jour par tranche complète de 5 ans d'ancienneté (formule : $\lfloor \text{Ancienneté} / 5 \rfloor \times 1\text{ jour}$). Écriture `BONIFICATION` dans le ledger. |
| **Loi 2024-014, Art. 131**<br>*(Loi 2003-044, Art. 90)* | **Indemnité de congé payé & Règle du salaire continué**<br>• Maintien au moins égal au salaire moyen des 12 derniers mois et non inférieur au salaire continué.<br>• *Note de gouvernance DAF* : La double ligne en paie (`DEDUCTION_CONGE_BASE` au trentième actuel vs `INDEMNITE_CONGE_PAYE` au coût historique déstocké par lots FIFO) est une convention financière interne (`OHATRA KAJY`) assumée face au risque prud'homal sur le salaire continué. | • Service [`CongeTauxApplicableService.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/conge/application/CongeTauxApplicableService.java) : valorisation mensuelle $V_m$ et résolution temporelle de `TCONGE`.<br>• [`CongeLotValorisationEngine.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/conge/domain/CongeLotValorisationEngine.java) : déstockage FIFO immuable des lots historiques. |

---

## 2. Modèle Mathématique Canonique (`OHATRA KAJY DROITS CONGES`)

Le système applique de manière stricte le modèle financier d'entreprise (`OHATRA KAJY DROITS CONGES-given on sept26.xls`), réconciliant l'acquisition des droits et leur valorisation comptable.

### 2.1 Définition des Variables Mensuelles

Pour chaque mois $m \in [1, 12]$ de la période de référence d'un salarié :

| Variable | Symbole | Règle de Calcul & Description Métier | Implémentation Code |
| :--- | :---: | :--- | :--- |
| **Salaire de Base Contractuel** | $S_{base}$ | Salaire mensuel contractuel de l'employé au mois $m$. | `conge_journal_droit.salaire_base` |
| **Jours d'Absence Constatés** | $J_{abs}$ | Volume d'absence du mois (en équivalent trentième). | Issu des pointages validés |
| **Retenue pour Absence** | $M_{abs}$ | Déduction financière au trentième : $M_{abs} = J_{abs} \times \frac{S_{base}}{30}$. | Rubrique de paie `RETENUE_ABSENCE` |
| **Jours de Congé Pris** | $J_{conge}$ | Jours de congés payés validés pris durant le mois. | `DemandeConge.nbJoursDecomptes` |
| **Taux Journalier TCONGE Actif** | $T_{conge}$ | Taux journalier applicable en vigueur issu du cycle précédent. | `conge_taux_applicable.taux_journalier` |
| **Indemnité de Congé Payé** | $M_{conge}$ | Somme des valeurs imputées sur les lots historiques FIFO : $\sum (j_{lot} \times T_{lot})$. | `CongeLotValorisationEngine.java` |
| **Net de Base du Mois** | $S_{net\_base}$ | $S_{net\_base} = S_{base} - M_{abs} + M_{conge}$. | Moteur de paie |
| **Heures Supplémentaires Rémunérées** | $M_{hs}$ | Majoration pour heures supplémentaires du mois. | `paie_bulletin.total_heures_sup` |
| **Assiette de Droit à Congé** | $B_{totale}$ | Assiette brute globale : $B_{totale} = S_{net\_base} + M_{hs}$. | `conge_journal_droit.base_valorisation` |
| **Base Trentième d'Activité** | $F$ | $F = 30 - \max(0, J_{abs} - J_{conge})$ *(Les congés pris ne réduisent pas la base)*. | `conge_journal_droit.jours_travailles_formule` |
| **Droit Acquis du Mois** | $D_m$ | $D_m = 2{,}5 \times \frac{F}{30}$ *(Proratisation légale au trentième)*. | `conge_journal_droit.jours_droit_acquis` |
| **Valorisation Mensuelle du Droit** | $V_m$ | $V_m = \frac{B_{totale} \times D_m}{30}$ *(Formule canonique $R \times S / 30$)*. | `conge_journal_droit.valorisation_mensuelle` |
| **Cumul Annuel des Droits** | $CD_{12}$ | $CD_{12} = \sum_{m=1}^{12} D_m$ *(Droits totaux de l'exercice)*. | `conge_journal_droit.cumul_jours_droit` |
| **Cumul Annuel de Valorisation** | $CV_{12}$ | $CV_{12} = \sum_{m=1}^{12} V_m$ *(Masse financière totale provisionnée)*. | `conge_journal_droit.cumul_valorisation` |

### 2.2 Résolution du Taux Journalier Applicable (`TCONGE`)

À la clôture de la période annuelle de référence (12 mois), le taux journalier officiel `TCONGE` qui régira la valorisation du cycle suivant est calculé par le ratio canonique :

$$\mathbf{TCONGE} = \frac{CV_{12}}{CD_{12}} = \frac{\sum_{m=1}^{12} V_m}{\sum_{m=1}^{12} D_m}$$

Ce taux unitaire est persisté dans `conge_taux_applicable` avec la convention temporelle d'intervalle semi-ouvert $[date\_effet, date\_fin[$.

### 2.3 Les Deux Flux Fondamentaux de Gestion des Congés

Le système GALAXAM SIRH distingue avec une rigueur absolue deux flux d'entrée et de gestion des collaborateurs dans le moteur de congés :

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                      LES DEUX FLUX FONDAMENTAUX DU MODULE CONGÉS                       │
├───────────────────────────────────────────┬────────────────────────────────────────────┤
│ FLUX 1 : NOUVEL EMPLOYÉ (EMBAUCHE NATIVE) │ FLUX 2 : REPRISE HISTORIQUE (CUTOVER DAF)  │
├───────────────────────────────────────────┼────────────────────────────────────────────┤
│ • Embauche neuve enregistrée dans le SIRH │ • Salarié avec passif (migration système)  │
│ • Période 'NEW_HIRE' [dateEmbauche, 31/12]│ • Période 'AGGREGATED_OPENING' ou 'MINIMAL'│
│ • Carence légale 12 mois (Art. 130)       │ • Pas d'import de paies/pointages passés   │
│ • Prise de congé annuel bloquée (< 12 m)  │ • Photographie certifiée à date d'arrêté T₀│
│ • Permissions familiales autorisées       │ • Injection stock initial restant (jours+Ar)│
│ • Acquisition mensuelle 2,5 j/m en paie   │ • Injection cumuls Janvier → T₀ (droit+val)│
│ • Clôture 31/12 → 1er TCONGE calculé      │ • Consommation FIFO prioritaire (Prio 1)   │
│ • Cycle civil suivant en autonomie pleine │ • Audit DAF via 'conge_cutover_correction' │
└───────────────────────────────────────────┴────────────────────────────────────────────┘
```

#### A. Flux 1 : Nouvel Employé (Embauche Native)
1. **Création de la période initiale** : Lors de l'embauche contractuelle, une période de référence est ouverte dans `conge_periode_reference` avec `mode_reprise = 'NEW_HIRE'`, bornée du jour d'embauche au 31 décembre de l'année en cours.
2. **Carence légale de 12 mois de service effectif (Loi 2024-014, Art. 130)** :
   - Pendant les 12 premiers mois d'ancienneté, le collaborateur acquiert régulièrement ses 2,50 jours par mois de service effectif (proratisés en paie).
   - Toutefois, la pose de congés payés de type `ANNUEL` est **strictement verrouillée** au niveau métier (`DemandeConge.java` contrôle `moisService >= 12`).
   - Seules les absences rémunérées non assujetties à cette carence (permissions exceptionnelles pour événements familiaux de l'Article 133 plafonnées à 10 j/an, congé de maternité de l'Article 142, congé de paternité de l'Article 134) sont immédiatement autorisées.
3. **Acquisition et Valorisation Mensuelle en Paie** :
   - À chaque clôture mensuelle de la paie, le moteur enregistre dans `conge_journal_droit` le droit acquis $D_m$ et la valorisation provisionnée $V_m = \frac{B_{totale} \times D_m}{30}$.
   - Une écriture de crédit `ACQUISITION` est enregistrée dans le grand-livre `mouvement_solde_conge`.
4. **Clôture annuelle au 31 décembre & Naissance du premier TCONGE** :
   - À l'issue des 12 mois (ou du prorata de la première année), la période de référence passe au statut `CLOTUREE`.
   - Le système calcule le premier taux journalier officiel du salarié : $\mathbf{TCONGE} = \frac{\sum V_m}{\sum D_m}$.
   - Ce taux est inséré dans `conge_taux_applicable` avec `source_provenance = 'CALCUL_PERIODE_REFERENCE'` pour régir les prises du cycle $N+1$.

#### B. Flux 2 : Reprise Historique à Date d'Arrêté (Cutover DAF)
1. **Principe directeur d'ingénierie** : Éviter l'import lourd, fragile et chronophage de plusieurs années de bulletins de paie et de badgeages quotidiens. Le système opère une **photographie certifiée à la date d'arrêté $T_0$**.
2. **Saisie sécurisée par la DAF (`InitialisationSoldeModal.jsx` / `POST /conges/solde/cutover-opening`)** :
   - **Date d'arrêté ($T_0$)** : Date pivot de bascule (`date_cutover`, ex: `2026-09-30`). Le backend rejette formellement toute date système implicite.
   - **Solde de congés restant arrêté** : Volume de jours acquis non pris arrêté à $T_0$ (`solde_jours`, ex: `14.50` jours).
   - **Valorisation du solde d'ouverture** : Montant financier total de la réserve arrêtée (`valeur_solde_ouverture`, ex: `157 013,11 Ar`) et calcul du taux journalier correspondant ($TCONGE = \frac{\text{Valeur}}{\text{Jours}}$).
   - **Cumuls intermédiaires d'exercice (Crucial pour la pérennité du calcul)** :
     - `cumul_droits_ouverture` : Total des droits acquis du 1er janvier de l'année en cours jusqu'à $T_0$.
     - `cumul_valorisation_ouverture` : Total de la valorisation acquise du 1er janvier jusqu'à $T_0$.
     - *Justification d'architecture* : Ces deux cumuls garantissent que lors de la clôture annuelle au 31 décembre, le moteur calculera le nouveau $\mathbf{TCONGE}_{N+1}$ sur l'assiette complète des 12 mois de l'année civile (période pré-cutover issue de l'arrêté + mois post-cutover calculés dans GALAXAM SIRH), évitant toute sous-évaluation ou rupture de charge financière.
   - **Référence formelle de l'arrêté** : Référence administrative obligatoire de l'acte RH/DAF (`reference_arrete`, ex: `ARR-2026-09-042`).
3. **Inscription dans le Grand-Livre (`mouvement_solde_conge`)** :
   - Le stock initial est comptabilisé sous `type_mouvement = 'AJUSTEMENT'` avec le commentaire normalisé `[SOLDE_INITIAL] Reprise historique arrêté au YYYY-MM-DD...`.
4. **Priorité FIFO Absolue de Consommation & Liquidation Anti-Arrondi** :
   - Le moteur pur [`PurgeFifoEngine.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/conge/domain/PurgeFifoEngine.java) et le service [`CongeLotValorisationEngine.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/conge/domain/CongeLotValorisationEngine.java) affectent la **Priorité 1** au stock initial `[SOLDE_INITIAL]`. Toute prise de congé ultérieure déstocke en priorité ce reliquat historique avant d'imputer les acquisitions mensuelles récentes.
   - **Clause de liquidation FIFO anti-arrondi** : Lorsqu'une imputation de congé épuise intégralement les jours restants d'un lot (`quantite == lot.joursRestants`), la valorisation imputée est égale à la `valeur_restante` exacte en Ariary du lot (et non recalculée par multiplication unitaire), garantissant l'absence de centime orphelin ou d'écart résiduel de clôture.
5. **Audit des Rectifications DAF (`conge_cutover_correction`)** :
   - Si une contestation de salarié ou une erreur de report de l'ancien SIRH est constatée ultérieurement, la DAF effectue une correction via `POST /conges/solde/cutover/{idEmploye}/corriger`.
   - La table `conge_cutover_correction` trace exhaustivement l'ancien reliquat, le nouveau reliquat, l'ancien taux, le nouveau taux, la régularisation financière à examiner, le motif obligatoire et l'auteur de la correction.
   - **Zéro falsification rétroactive** : Les bulletins de paie passés et clôturés restent immuables ; seul un mouvement d'ajustement comptable compensatoire est injecté dans le Grand-Livre.

---

## 3. Architecture du Modèle de Données PostgreSQL

Le schéma de données certifié (`04_schema_conges.sql` et `06_schema_paie.sql`) s'articule autour de **11 tables relationnelles dédiées**, **1 table liée au domaine de la paie** et **3 vues analytiques temps réel** garantissant l'intégrité transactionnelle et référentielle :

```
                        ┌──────────────────────────────┐
                        │           EMPLOYE            │
                        └──────────────┬───────────────┘
                                       │ 1
        ┌──────────────────────────────┼──────────────────────────────┬──────────────────────────────┐
        │ N                            │ N                            │ N                            │ N
 ┌──────▼───────────────────────┐┌─────▼────────────────────────┐┌─────▼───────────────────────┐┌──────▼───────────────────────┐
 │        DEMANDE_CONGE         ││    MOUVEMENT_SOLDE_CONGE     ││   CONGE_EXPIRATION_EXAMEN   ││   CONGE_PERIODE_REFERENCE   │
 │  (Cycle de vie & Contrôles)  ││   (Grand-Livre Comptable)    ││   (Workflow Décisions DAF)  ││ (Périodes 12M & Modes Reprise│
 └──────┬───────────────┬───────┘└─────┬────────────────────────┘└─────────────┬───────────────┘└──────┬───────────────────────┘
        │ N             │ N            │ 1                                     │ 0..1 (si EXPIRE)      │ 1
        │               │              ├──────────────────────────────┐        │                       │ N
        │               │              │ 1                            │ 1      ▼                  ┌────▼────────────────────────┐
 ┌──────▼───────┐┌──────▼───────┐┌─────▼──┴─────────────────────┐┌────▼────────┴──────────────┐  │    CONGE_TAUX_APPLICABLE    │
 │REF_TYPE_CONGE││REF_STATUT_...││    CONGE_CUTOVER_CORRECTION  ││CONGE_VALORISATION_CORRECTION│  │ (TCONGE Historique & Cutover)│
 │ (Règles L125)││(BROUILLON...)││ (Corrections Reliquat DAF)   ││(Rectifications Valeur DAF)  │  └─────────────────────────────┘
 └──────┬───────┘└──────────────┘└──────────────────────────────┘└─────────────────────────────┘
        │ N                                                                             │ 1 (optionnel)
        ├──────────────────────┐                                                  ┌─────▼────────────────────────┐
        │ 1                    │ 1                                                │     CONGE_JOURNAL_DROIT      │
 ┌──────▼───────────────┐┌─────▼────────────────────────┐                         │  (Grand-Livre Paie Droits)   │
 │REF_POOL_CONGE_ANNUEL ││REF_MOTIF_EVENEMENT_FAMILIAL  │                         └──────────────────────────────┘
 │ (Plafond 10j/an)     ││ (4 motifs légaux Art. 88/133)│
 └──────────────────────┘└──────────────────────────────┘
```

### 3.1 Dictionnaire des 11 Tables Relationnelles Dédiées

#### 1. `ref_pool_conge_annuel`
Gère les quotas annuels partagés entre plusieurs types ou motifs :
- `id_pool` (PK SERIAL), `code` (`PERMISSIONS_FAMILIALES`), `libelle`, `plafond_jours_an` (`10.0`).

#### 2. `ref_type_conge`
Porte les règles de gestion intrinsèques à chaque typologie d'absence :
- `id_type_conge` (PK SERIAL), `code` (`ANNUEL`, `MALADIE`, `EXCEPTIONNEL`, `MATERNITE`, `PATERNITE`, `SANS_SOLDE`, `MALADIE_SANS_SOLDE`...)
- `est_paye` (maintien de salaire), `decompte_solde_acquis` (débit du solde annuel dans le ledger)
- `exige_anciennete_min_mois` (12 mois pour le congé annuel selon Art. 130)
- `duree_fixe_jours` (98j pour Maternité, 3j pour Paternité)
- `plafond_jours_episode` (30j pour Maladie ordinaire selon Art. 13)
- `id_type_conge_bascule` (FK vers `MALADIE_SANS_SOLDE` après 30 jours)
- `id_pool_conge_annuel` (FK vers `ref_pool_conge_annuel`, `NULL` pour Paternité)
- `soumis_delai_preavis` (booléen), `suspend_acquisition_solde` (booléen), `exige_justificatif` (booléen).

#### 3. `ref_motif_evenement_familial`
Quotas individuels des 4 motifs légaux d'ordre public prévus par l'Article 88 (Loi 2003-044) et l'Article 133 (Loi 2024-014) :
- `MARIAGE` : 3 jours | `NAISSANCE` : 2 jours | `DECES_CONJOINT_ENFANT` : 3 jours | `DECES_PARENT` : 2 jours.

#### 4. `ref_statut_demande_conge`
Porte le cycle de vie des demandes avec libellé et couleur d'affichage :
- `id_statut_demande_conge` (PK SERIAL), `code` (`BROUILLON`, `EN_ATTENTE`, `VALIDE`, `REFUSE`, `ANNULE`), `libelle`, `couleur_badge`.

#### 5. `demande_conge`
Enregistre chaque demande et son statut opérationnel :
- `id_demande_conge` (PK SERIAL), `id_employe` (FK), `id_type_conge` (FK), `id_statut_demande_conge` (FK), `id_motif_evenement_familial` (FK nullable)
- `date_debut`, `date_fin`, `date_demande`, `nb_jours_decomptes` (NUMERIC(5,2))
- `derogation_solde_negatif` (booléen), `derogation_delai_preavis` (booléen), `derogation_continuite_service` (booléen), `extension_pathologique_maternite` (booléen)
- `est_demi_journee` (booléen), `moment_demi_journee` (`MATIN`, `APRES_MIDI`)
- `saisi_par`, `valide_par`, `date_validation`, `motif_refus`, `commentaire`
- Contraintes : `chk_demande_conge_dates` (`date_fin >= date_debut`), `chk_demande_conge_demi_journee`.

#### 6. `mouvement_solde_conge` (Grand-Livre Comptable / Ledger)
Registre immuable de chaque débit et crédit de congés :
- `id_mouvement` (PK SERIAL), `id_employe` (FK), `date_mouvement`
- `type_mouvement` (`ACQUISITION`, `BONIFICATION`, `PRISE`, `AJUSTEMENT`, `ANNULATION`, `EXPIRATION`)
- `nb_jours` (NUMERIC(5,2) signé : positif pour crédit, négatif pour débit)
- `id_demande_conge` (FK nullable vers la demande d'origine)
- `valeur_source` (NUMERIC(14,2) pour mémorisation de valorisation financière historique)
- **Index uniques partiels anti-doublon certifiés** :
  - `idx_uniq_mouvement_prise_demande` : empêche toute double écriture de `PRISE` pour une même demande.
  - `idx_uniq_mouvement_annulation_demande` : garantit l'idempotence stricte de l'opération d'annulation.

#### 7. `conge_cutover_correction` (Audit des corrections DAF de stock initial)
Permet à la DAF de corriger les anomalies de reprise de solde sans altérer rétroactivement les bulletins passés :
- `id_correction` (PK SERIAL), `id_employe` (FK), `id_mouvement_reprise` (FK), `id_mouvement_ajustement` (FK unique)
- `ancien_reliquat_jours`, `nouveau_reliquat_jours`, `ancien_reliquat_valeur`, `nouveau_reliquat_valeur`
- `ancien_taux`, `nouveau_taux`, `regularisation_a_examiner`, `motif`, `corrige_par`, `date_correction`.
- Contrainte : `chk_cutover_correction_reliquat` (`nouveau_reliquat_jours >= 0 AND nouveau_reliquat_valeur >= 0`).

#### 8. `conge_valorisation_mensuelle_correction` (Rectification DAF de valorisation mensuelle)
Permet à la DAF de rectifier la valorisation en ariary d'un mois clos sans modifier les jours acquis ni réouvrir la paie :
- `id_correction` (PK SERIAL), `id_journal` (FK vers `conge_journal_droit`), `id_mouvement_acquisition` (FK)
- `ancienne_valeur`, `nouvelle_valeur`, `motif`, `corrige_par`, `date_correction`.

#### 9. `conge_expiration_examen` (Audit des décisions DAF de prescription)
Historise les examens des droits candidats à prescription (Loi 2024-014 Art. 129) :
- `id_examen` (PK SERIAL), `id_employe` (FK), `decision` (`REPORTE` ou `EXPIRE`), `jours_examines`, `motif`
- `planification_verifiee` (booléen obligatoire pour `EXPIRE`), `date_prochaine_revue` (obligatoire pour `REPORTE`)
- `id_mouvement_expiration` (FK vers `mouvement_solde_conge`), `decide_par`.
- **Contrainte CHECK d'intégrité stricte (`chk_conge_expiration_decision`)** :
  ```sql
  (decision = 'REPORTE' AND date_prochaine_revue IS NOT NULL AND id_mouvement_expiration IS NULL)
  OR
  (decision = 'EXPIRE' AND date_prochaine_revue IS NULL AND id_mouvement_expiration IS NOT NULL AND planification_verifiee)
  ```

#### 10. `conge_periode_reference` (Périodes Annuelles Bornées)
Périodes de 12 mois bornées du 01/01 au 31/12 avec contrainte d'exclusion temporelle GIST (`daterange` sans chevauchement) :
- `id_periode_reference` (PK SERIAL), `id_employe` (FK), `date_debut`, `date_fin`, `statut` (`OUVERTE`, `CLOTUREE`, `ARCHIVEE`)
- `mode_reprise` : `FULL_HISTORY`, `AGGREGATED_OPENING`, `MINIMAL_OPENING`, `NEW_HIRE`
- `date_cutover` (DATE pivot d'arrêté)
- `cumul_droits_ouverture`, `cumul_valorisation_ouverture` (cumuls acquis pré-cutover)
- `taux_calcule_cloture`, `cumul_droits_cloture`, `cumul_valorisation_cloture` (indicateurs consolidés à la clôture)
- **Garde-fou d'intégrité du Cutover** : Si un dossier collaborateur repris en mode cutover ne possède pas ses cumuls d'ouverture (`cumul_droits_ouverture` / `cumul_valorisation_ouverture`) lors de la clôture annuelle, le système bloque la génération automatique du taux TCONGE et lève l'exception `CYCLE_ACCUMULATORS_UNKNOWN`, exigeant une régularisation formelle de la DAF.
- Contrainte GiST anti-chevauchement : `uk_conge_reference_temporal_overlap EXCLUDE USING gist (id_employe WITH =, daterange(date_debut, date_fin, '[]') WITH &&)`.

#### 11. `conge_taux_applicable` (Historique du Taux Unitaire Journalier)
Historisation rigoureuse du taux journalier `TCONGE` avec convention $[date\_effet, date\_fin[$ :
- `id_taux_applicable` (PK SERIAL), `id_employe` (FK), `taux_journalier` (NUMERIC(20,2)), `date_effet`, `date_fin`
- `source_provenance` : `KAJY_MIGRATION`, `CALCUL_PERIODE_REFERENCE`, `INITIALISATION_BASE_30`, `REPRISE_CUTOVER`
- `id_periode_reference` (FK), `commentaire`, `cree_par`.

#### 12. `conge_journal_droit` (Grand-Livre Mensuel de Paie — `06_schema_paie.sql`)
Trace mois par mois la constitution des droits et de la réserve financière :
- `id_journal` (PK SERIAL), `id_employe` (FK), `id_periode_paie` (FK), `id_periode_reference` (FK)
- `salaire_base`, `jours_travailles_formule`, `jours_droit_acquis`, `base_valorisation`, `valorisation_mensuelle`, `cumul_jours_droit`, `cumul_valorisation`, `taux_journalier_conge`.

### 3.2 Vues Analytiques SQL Dédiées

1. **`v_solde_conge_employe`** :
   Agrège en temps réel le solde net de chaque collaborateur à partir de la somme de `mouvement_solde_conge` :
   $$\text{Solde Net} = \sum (\text{nb\_jours})$$
   Fournit également `derniere_activite`, `dernier_mouvement` et sert de source de vérité pour tous les contrôles d'éligibilité transactionnels.
2. **`v_pool_conge_annuel_consommation`** :
   Consolide pour chaque employé et chaque pool partagé (`id_pool`) la somme des jours consommés (`VALIDE`) et des jours engagés (`EN_ATTENTE`) sur l'année civile en cours.
3. **`v_demande_conge_cpl`** :
   Vue dénormalisée joignant `demande_conge`, `employe`, `ref_type_conge`, `ref_statut_demande_conge` et `ref_motif_evenement_familial`, optimisée pour l'alimentation des tableaux de bord et des interfaces de validation.

---

## 4. Règles de Gestion Métier (RG) Détaillées

### RG-CON-01 : Décompte des Jours, Fériés Dynamiques & Date de Reprise
- **Saisie par date de reprise** : L'écran de demande saisit la date de départ (`date_debut`) et la **date de reprise du travail**. Le système convertit immédiatement cette saisie en date de fin inclusive :
  $$\text{date\_fin} = \text{date\_reprise} - 1\text{ jour}$$
- **Décompte calendaire dynamique des jours fériés** :
  Le décompte n'est pas un calcul figé. Il est orchestré par [`CongeDecompteCalendaireResolver.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/conge/application/CongeDecompteCalendaireResolver.java). Pour chaque jour $j \in [\text{date\_debut}, \text{date\_fin}]$ :
  1. Résolution temporelle à la date $j$ du paramètre global `CONGE_DECOMPTE_FERIES` (`INCLUS` ou `EXCLUS`).
  2. Si `CONGE_DECOMPTE_FERIES = 'EXCLUS'` : vérification si le jour $j$ est un jour férié chômé légal (férié national ou férié spécifique rattaché au site géographique de l'employé via `site_calendrier_ferie`). Si le jour est férié, **il est ignoré du décompte** (0 jour débité).
  3. Si `CONGE_DECOMPTE_FERIES = 'INCLUS'` : le jour calendaire est débité (1 jour complet, ou 0,5 pour demi-journée).
  *Source légale* : Loi n° 2024-014, Article 127 (*« Sont exclus du décompte des congés payés les jours fériés chômés et payés... »*).
- **Demi-journée** : Si `est_demi_journee = true`, alors $\text{date\_debut} = \text{date\_fin}$, $\text{nb\_jours\_decomptes} = 0{,}5$, et le créneau (`MATIN` ou `APRES_MIDI`) est obligatoire.

### RG-CON-02 : Acquisition Mensuelle Légale & Idempotence
- **Droit normal** : **2,50 jours calendaires** par mois de service effectif (Article 125 du Code du Travail 2024).
- **Proratisation légale au trentième** : En cas d'absences injustifiées ou non rémunérées dans le mois :
  $$F = 30 - \max(0, J_{abs} - J_{conge}), \quad D_m = 2{,}5 \times \frac{F}{30}$$
- **Idempotence de clôture** : L'acquisition est calculée et figée lors de la clôture de la paie mensuelle dans `conge_journal_droit` et créditée dans `mouvement_solde_conge`. Aucun cron n'accorde d'anticipation non travaillée.

### RG-CON-03 : Bonification pour Ancienneté
- Calculée à la date anniversaire par palier de 5 ans d'ancienneté révolus (+1 jour ouvrable par tranche complète de 5 ans).
  - Seuil d'activation dans le moteur : $\ge 5\text{ ans}$ (formule : $\lfloor \text{Ancienneté} / 5 \rfloor \times 1\text{ jour}$).
  - Tracé dans le ledger sous le type de mouvement `BONIFICATION`.
  *Source légale* : Loi n° 2024-014, Article 126 / Loi n° 2003-044, Article 86.

### RG-CON-04 : Examen DAF des Droits Candidats à Prescription
- Conforme à l'Article 129 de la Loi n° 2024-014 : le délai de prescription de 3 ans (paramétré par `DUREE_PRESCRIPTION_SOLDE_CONGE_MOIS = 36`) est un seuil de revue.
- Le moteur pur [`PurgeFifoEngine.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/conge/domain/PurgeFifoEngine.java) impute les prises historiques en FIFO et isole les droits acquis antérieurs à la date limite.
- **Aucune purge automatique** : La DAF pilote l'examen via [`CongeExpirationExamenService.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/conge/application/CongeExpirationExamenService.java) :
  - **`REPORTE`** : maintien au solde avec date de prochaine revue obligatoire.
  - **`EXPIRE`** : validation obligatoire de la coche `planification_verifiee = true`, motif textuel obligatoire et génération d'un débit `EXPIRATION` dans le grand-livre.

### RG-CON-05 : Interdiction Absolue de l'Anticipation en Solde Négatif
- **Le code bloque systématiquement tout solde négatif** :
  Dans [`DemandeConge.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/conge/persistence/DemandeConge.java#L153-L157) et [`CongeService.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/conge/application/CongeService.java#L938-L940) :
  ```java
  if (nouveauSolde.compareTo(BigDecimal.ZERO) < 0) {
      throw new IllegalArgumentException("Solde de congé insuffisant... La prise par anticipation est désactivée.");
  }
  ```
- Dans [`DemandeCongeController.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/conge/api/DemandeCongeController.java#L257-L259) :
  ```java
  if (derogationSoldeNegatif) {
      throw new IllegalArgumentException("La prise par anticipation avec solde négatif est désactivée.");
  }
  ```
- En base de données, le paramètre `PLAFOND_SOLDE_CONGE_NEGATIF` est configuré avec `est_actif = FALSE`. Le solde débiteur est formellement prohibé.

### RG-CON-06 : Délais de Préavis Réglementaires
Pour les congés soumis à préavis (`soumis_delai_preavis = true` sur `ref_type_conge`) :
- Demande courte ($\le 3$ jours) : **2 jours ouvrables** de délai minimum.
- Demande longue ($> 3$ jours) : **15 jours calendaires** de délai minimum.
- Les congés d'urgence (`MALADIE`, `MATERNITE`, `PATERNITE`, `EXCEPTIONNEL`) sont exemptés de préavis. Une dérogation expresse (`derogationDelaiPreavis = true`) permet de passer outre pour les congés annuels.

### RG-CON-07 : Pool Annuel des Événements Familiaux (10 jours/an)
- Quota légal partagé de **10,0 jours par année civile** (Article 88 Loi 2003-044 / Article 133 Loi 2024-014).
- La vue `v_pool_conge_annuel_consommation` agrège `jours_consommes` (`VALIDE`) et `jours_en_attente` (`EN_ATTENTE`).
- **Prévention du double comptage** : Lors de la validation d'une demande existante en statut `EN_ATTENTE`, le volume de la demande courante est déduit du total engagé avant de ré-appliquer le contrôle, empêchant tout faux rejet.
- **Paternité autonome** : Le congé de paternité (3 jours, Article 134) est expressément **disjoint du pool des 10 jours** (`id_pool_conge_annuel IS NULL`).

### RG-CON-08 : Maternité & Extension Pathologique
- Durée standard : **14 semaines consécutives (98 jours)** (Article 142 Loi 2024-014 / Article 97 Loi 2003-044).
- Prolongation pathologique : **+21 jours (3 semaines)** sur certificat médical (`extension_pathologique_maternite = true`), portant le total autorisé à **119 jours calendaires**.
- Non suspensif : la salariée acquiert l'intégralité de ses congés payés (2,5 j/mois) pendant toute la période de maternité.

### RG-CON-09 : Maladie Ordinaire & Relais CNaPS
- Maintien du salaire à la charge de l'employeur pendant les **30 premiers jours continus** (Article 13 Loi 2003-044, limité au préavis mensuel).
- Dès le 31e jour, la demande bascule automatiquement vers `MALADIE_SANS_SOLDE` (`id_type_conge_bascule = 1`) pour prise en charge directe par les indemnités journalières de la CNaPS.

### RG-CON-10 : Fractionnement Annuel Obligatoire
- Au moins une tranche continue de congé annuel payé doit atteindre **15 jours consécutifs** au cours de l'exercice (Article 130 Loi 2024-014 / Article 89 Loi 2003-044).
- Alerte RH automatique émise par `NotificationRhScheduler` au 4e trimestre si l'employé n'a posé aucune tranche continue $\ge 15$ jours.

### RG-CON-11 : Périodes de Paie Clôturées & Intangibilité
- Toute tentative de création, modification ou annulation d'un congé tombant sur une période de paie au statut `CLOTUREE` est immédiatement rejetée avec le code d'erreur `LEAVE_PAYROLL_PERIOD_CLOSED`.

### RG-CON-12 : Continuité de Service & Collisions de Poste/Site
- Orchestré par [`CongeContinuiteService.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/conge/application/CongeContinuiteService.java) :
  1. Résout l'affectation active de l'employé pour chaque jour de l'intervalle demandé via `EmploymentContextResolver`.
  2. Vérifie que l'employé est sous contrat actif et en poste (`actifSurPeriode() = true`).
  3. Détecte les collisions avec d'autres collaborateurs occupant le même poste (`id_fonction`) sur le même site géographique (`id_site`).
  4. Si au moins une collision est détectée et que `derogationContinuiteService = false`, la demande est immédiatement bloquée avec le nom du collègue et l'intervalle de conflit.
  *Source légale* : Loi n° 2024-014, Article 128 (pouvoir d'organisation de l'employeur pour la continuité de service).

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
               ┌──────────────┐                    │
               │    VALIDE    │                    │
               └──┬───────────┘                    │
 annulerDemande() │ (Génération PRISE au Ledger)   │
                  ▼                                │
           ┌──────────────┐                        │
           │    ANNULE    │                        │
           └──────────────┘                        │
         (Génération ANNULATION au Ledger)         │
         (Annulation idempotente / index unique)───┘
```

### 5.2 Isolation Transactionnelle & Verrous Pessimistes (`FOR UPDATE`)

Pour éliminer tout risque d'incohérence sous accès concurrentiel (ex: deux validateurs approuvant simultanément deux demandes pour un salarié dont le solde ne couvre qu'une seule) :
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
   Toutes les opérations (mise à jour du statut, insertion de la ligne `PRISE` dans `mouvement_solde_conge`, mise à jour du planning de pointage) sont exécutées dans une seule transaction ACID.

### 5.3 Matrice Dynamique des Actions Disponibles (`availableActions`)

Le backend calcule dynamiquement pour chaque demande la liste des actions permises selon le statut, le rôle utilisateur et la clôture de la paie :

| Statut Demande | Salarié (`ROLE_USER`) | Approbateur / RH (`CONGE_VALIDATE` / `ADMIN`) | Si Période de Paie Clôturée |
| :--- | :--- | :--- | :--- |
| **`BROUILLON`** | `EDITER`, `SOUMETTRE`, `SUPPRIMER` | `EDITER`, `SOUMETTRE`, `SUPPRIMER` | Aucune action (`[]`) |
| **`EN_ATTENTE`** | `ANNULER` | `VALIDER`, `REFUSER`, `MODIFIER` | Aucune action (`[]`) |
| **`VALIDE`** | `DEMANDER_ANNULATION` | `ANNULER` (avec motif obligatoire) | Aucune action (`[]`) |
| **`REFUSE`** | Aucune action | Aucune action | Aucune action (`[]`) |
| **`ANNULE`** | Aucune action | Aucune action | Aucune action (`[]`) |

---

## 6. Intégrations Certifiées : Pointage, Paie & STC

### 6.1 Pont Réel Congés $\rightarrow$ Pointage (Gestion du Temps)

L'interaction entre Congés et Pointage est bidirectionnelle et repose sur deux flux distincts :

#### A. Cas 1 : Demande posée et validée en amont (Module Congés)
Lorsque le collaborateur pose sa demande de congé à l'avance et que celle-ci est validée par le manager ou les RH :
1. **Source de vérité** : `demande_conge` et `mouvement_solde_conge` scellent l'autorisation et le débit de solde. Aucune insertion physique prématurée ne vient polluer `pointage_jour` (évite les conflits d'édition concurrente et les verrous pessimistes).
2. **Grille de saisie quotidienne (`PointageSaisieService.getGrilleSaisie`)** : La date est dynamiquement résolue et dispensée de badgeage :
   - Congé annuel payé (`decompte_solde_acquis = true`) $\rightarrow$ Statut `CONGE` (id 5), 0h travaillée.
   - Permission / Événement familial payé (`decompte_solde_acquis = false` + payé) $\rightarrow$ Statut `DISPENSE_POINTAGE` (id 10), 8h forfaitaires payées.
   - Absence non rémunérée (ex: sans solde) $\rightarrow$ Statut `ABSENCE_JUSTIFIEE` (id 4), 0h travaillée.
3. **Pré-contrôle DAF (`PointageSyntheseService.journeesOuvrablesSansStatut`)** : Les jours couverts par un congé validé sont automatiquement exclus des anomalies de "journées sans pointage" ; le bandeau bloquant `POINTAGE_JOURNEES_MANQUANTES` ne se déclenche pas.
4. **Calcul de synthèse mensuelle (`PointageSyntheseService.calculerSyntheseEmploye`)** : Les congés validés sont réintégrés dans les tronçons hebdomadaires (`Result.Status.LEAVE`). L'objectif requis de travail est abaissé proportionnellement (8h par jour ouvré de congé), et la fiche collaborateur affiche `X prévus (+Y j congé)` avec l'objectif mensuel réduit (ex: 120h00 au lieu de 176h00).

#### B. Cas 2 : Absence constatée régularisée a posteriori (Module Pointage)
Lorsque l'absence a été constatée en cours de pointage (collaborateur non badgé) :
- Réalisée par `CongeService.regulariserCongeAnnuelDepuisPointageStructure` via le menu *Suivi & Anomalies* :
  1. `CongeService` crée et valide atomiquement la demande avec le préfixe `[REGULARISATION_POINTAGE]` et débite le solde acquis dans `mouvement_solde_conge`.
  2. `PointageAnomalieService` enregistre la journée dans `pointage_jour` avec `id_statut_jour = CONGE` et lie la clé étrangère `id_demande_conge`.
- Le solde de congé doit couvrir l'intégralité de la période à régulariser sous peine de rejet immédiat.
- En cas d'annulation ultérieure de la demande validée issue d'une régularisation, le solde de congé est restitué et les pointages redeviennent automatiquement des absences non justifiées dans la même transaction.

La matérialisation directe d'une journée de congé par la synthèse Pointage est également soumise à la protection SQL **SP80** : `date_pointage` doit appartenir à la période référencée, bornes incluses. Une demande de congé validée en amont ne crée pas à elle seule une ligne `pointage_jour`. SP80 ne modifie ni les mouvements de solde ni les snapshots historiques ; sa migration doit d'abord diagnostiquer les éventuelles journées hors période sur la base cible.

### 6.2 Pont Réel Congés $\rightarrow$ Moteur de Paie

Lors du calcul du bulletin mensuel :
1. **Retenue sur salaire de base pour congé pris** (au trentième légal) :
   $$\text{DEDUCTION\_CONGE\_BASE} = \text{Jours Congé Pris} \times \frac{\text{Salaire de Base Contractuel}}{30}$$
2. **Indemnité de congé payé** : chaque fraction de prise consomme la valeur historique de son lot FIFO. Le bulletin additionne les montants imputés :
   $$\text{INDEMNITE\_CONGE\_PAYE} = \sum_{lot} (\text{Jours Pris}_{lot} \times \mathbf{TCONGE}_{lot})$$
3. **Écart normal entre retenue et indemnité** : Le taux historique du lot peut différer du salaire courant / 30. Les deux lignes de paie figurent distinctement sur le bulletin de paie.

### 6.3 Pont Réel Congés $\rightarrow$ Solde de Tout Compte (STC) & Extinction des Droits

Lors de la rupture du contrat de travail (licenciement, démission, départ à la retraite, terme de CDD) :
1. **Obligation d'ordre public d'indemnisation** : En vertu de l'Article 131 de la Loi n° 2024-014 (Article 90 Loi 2003-044), une Indemnité Compensatrice de Congé Payé (ICCP) est obligatoirement versée pour tout droit acquis non pris.
2. **Liquidation financière exacte par lots FIFO** : Le service `StcService` interroge `CongeLotValorisationService` pour liquider exactement la valeur financière restante des lots non consommés :
   $$\text{Indemnité Compensatrice STC (ICCP)} = \sum_{lot} \text{Valeur restante du lot}$$
3. **Extinction comptable & Remise stricte à zéro (`0.00 j`)** : 
   - L'indemnisation financière éteint juridiquement et matériellement la créance de repos du salarié.
   - Dès la validation officielle du STC (`StcService.validerStc`), le moteur génère de manière atomique une écriture d'apurement dans le Grand-Livre des congés (`mouvement_solde_conge`) :
     - Type de mouvement : `AJUSTEMENT`
     - Volume : `-soldeCongesRestant` (débit intégral)
     - Commentaire : `"Liquidation ICCP - STC STC-AAAA-MM-XXXXX"`
   - Le compteur actif `v_solde_conge_employe` passe immédiatement à **`0.00` jour**.
   - La période de référence annuelle active dans `conge_periode_reference` passe au statut `CLOTUREE`.
4. **Cas particulier des congés pris par anticipation (solde négatif)** :
   - Conformément aux articles 67 et 68 de la Loi 2003-044 prohibant les retenues unilatérales sur salaire sans accord exprès, un solde négatif n'entraîne pas de ponction automatique au STC.
   - Il fait l'objet d'une ligne d'avertissement `CONGE_ANTICIPE_A_EXAMINER` pour instruction amiable ou contentieuse par la DAF.

---

## 7. Automatisations, FIFO & Schedulers Nocturnes

| Horaire | Composant Scheduler | Rôle & Traitement Effectué |
| :---: | :--- | :--- |
| **00:05** | `NotificationRhScheduler` | Détection et dispatching des alertes RH de supervision (solde faible, expiration candidate proche, tranche de fractionnement non respectée). |
| **1er jour du mois, 01:00** | `AcquisitionCongeScheduler` | Exécution des bonifications d'ancienneté pour le mois échu avec contrôle d'antériorité. L'acquisition mensuelle standard est quant à elle enregistrée lors de la clôture de la paie. |
| — | — | **Aucun cron nocturne ne purge automatiquement les congés prescrits.** L'examen est obligatoirement instruit par la DAF. |

Les exécutions planifiées sont inscrites par `SchedulerExecutionRegistry` avec
un jeton de possession et un bail renouvelable (30 minutes par défaut,
heartbeat toutes les 10 minutes). Après expiration, un autre worker peut
reprendre la tentative ; le jeton empêche l'ancien worker de prolonger le bail
ou de finaliser son statut. Il n'arrête pas le traitement métier de cet ancien
worker : les écritures doivent donc être atomiques/idempotentes séparément.
Pour les bonifications, l'insertion est protégée par une unicité PostgreSQL
salarié/année (`idx_uniq_mouvement_bonification_annee`) et la migration SP77
bloque si des doublons historiques doivent être arbitrés.

---

## 8. Cartographie des Composants Logiciels (Backend & Frontend)

### 8.1 Backend Java (31 Fichiers de Production Confirmés)

Le module est intégralement consolidé sous le package `mg.galaxam.sirh.conge` sans aucun framework ORM (exécution JDBC directe) :

```
mg.galaxam.sirh.conge/
├── README.md
├── api/
│   ├── CongePeriodeReferenceController.java  # Périodes de référence, clôtures, activation TCONGE, export Excel
│   ├── CongeRefController.java               # Référentiels (types, motifs légaux, pools partagés, statuts)
│   ├── CongeSoldeController.java             # Soldes, grand-livre, examen expiration, valorisation lots, cutover
│   └── DemandeCongeController.java           # Cycle de vie des demandes, simulation, validation, workflow
├── application/
│   ├── CongeAnnuelExcelExportService.java    # Bilan officiel annuel Excel individuel (ETAT_CONGES_PAR_PERSONNE.xlsx)
│   ├── CongeContinuiteService.java           # Analyse organisationnelle des affectations et collisions de poste/site
│   ├── CongeDecompteCalendaireResolver.java  # Résolution temporelle dynamique du décompte des jours fériés
│   ├── CongeExpirationExamenService.java     # Instruction et décisions DAF des droits candidats à prescription
│   ├── CongeGrandRecapExcelExportService.java# Grand récapitulatif collectif Excel (GRAND_RECAP_CONGES_VALORISES.xlsx)
│   ├── CongeLotValorisationService.java      # Moteur d'imputation FIFO des lots et corrections DAF de cutover
│   ├── CongeService.java                     # Orchestration centrale métier, workflow et intégration pointage
│   ├── CongeTauxApplicableService.java       # Gestion des périodes de référence, cycle civil et activation TCONGE
│   ├── CongeValorisationMensuelleService.java# Gestion des rectifications DAF de valorisation mensuelle
│   └── RegularisationCongeResult.java        # Record résultat de la régularisation de pointage en congé
├── domain/
│   ├── CongeCalculateurEngine.java           # Moteur pur de calcul (jours calendaires, préavis, bonifications)
│   ├── CongeExpirationExamenDTO.java         # DTO des informations d'examen de prescription
│   ├── CongeLotValorisationEngine.java       # Algorithme pur d'imputation FIFO des lots de valorisation
│   ├── CongeSituationEmployeDTO.java         # DTO de situation globale de congé d'un employé
│   ├── CongeSoldeRequests.java               # Records de requêtes (ajustement, cutover, expiration, report)
│   ├── DemandeCongeRequests.java             # Records de requêtes (création, modification, simulation, conflits)
│   ├── PurgeFifoEngine.java                  # Moteur pur de calcul FIFO des droits prescrits
│   └── TcongeConfigurationDTO.java           # DTO d'évaluation et statut d'activation du taux TCONGE
└── persistence/
    ├── DemandeConge.java                     # Modèle JDBC et contrôles d'intégrité de la table demande_conge
    ├── MouvementSoldeConge.java              # Modèle JDBC du grand-livre (mouvement_solde_conge)
    ├── RefMotifEvenementFamilial.java        # Modèle JDBC des 4 motifs légaux (Art. 88 / Art. 133)
    ├── RefPoolCongeAnnuel.java               # Modèle JDBC du pool partagé de 10 jours/an
    ├── RefStatutDemandeConge.java            # Modèle JDBC des statuts (BROUILLON, EN_ATTENTE, VALIDE...)
    ├── RefTypeConge.java                     # Modèle JDBC du référentiel des types de congés
    ├── VDemandeCongeCpl.java                 # Vue JDBC consolidée pour affichage et tableaux de bord
    └── VSoldeCongeEmploye.java               # Vue JDBC d'agrégation temps réel du solde collaborateur
```

### 8.2 Frontend React Backend-Driven (`frontend/src/features/leave/`)

L'architecture frontend est strictement **backend-driven** : elle n'exécute aucune règle de calcul arithmétique ou légale en local, consomme les endpoints REST et réagit aux autorisations et au contrat dynamique `availableActions` :

| Composant React | Fichier Source | Rôle Métier & Interaction Backend |
| :--- | :--- | :--- |
| **`InitialisationSoldeModal`** | `components/InitialisationSoldeModal.jsx` | Modale DAF de cutover : saisie de la date d'arrêté $T_0$, solde restant, valorisation d'ouverture, cumuls intermédiaires Janvier $\rightarrow T_0$ et référence d'arrêté (`POST /conges/solde/cutover-opening`). |
| **`DemandeCongeModal`** | `components/DemandeCongeModal.jsx` | Saisie d'une demande avec date de départ et date de reprise, préavis, dérogations éventuelles et soumission sécurisée. |
| **`DatesCongeSection`** | `components/formulaire/DatesCongeSection.jsx` | Déclencheur temps réel de `/conges/demandes/simuler` calculant le décompte net hors fériés légaux. |
| **`ConflitsPostePreview`** | `components/formulaire/ConflitsPostePreview.jsx` | Alerte dynamique préventive affichant les collisions d'absence d'équipe par poste et site (`/verifier-conflits`). |
| **`DemandesValidationTab`** | `components/DemandesValidationTab.jsx` | Tableau de bord de validation N+1/RH piloté par `availableActions` (`VALIDER`, `REFUSER`, `ANNULER`). |
| **`ValidationModals`** | `components/validation/ValidationModals.jsx` | Modales de confirmation, de refus motivé et d'annulation avec motif textuel obligatoire. |
| **`DemandeDetailsModal`** | `components/DemandeDetailsModal.jsx` | Consultation détaillée d'une demande, audit trail des approbations et pièces justificatives. |
| **`SoldesOverviewTab`** | `components/SoldesOverviewTab.jsx` | Vue globale RH/Manager : synthèse des collaborateurs, filtres et accès direct aux actions individuelles. |
| **`SoldesTable`** | `components/soldes/SoldesTable.jsx` | Grille tabulaire des compteurs temps réel (`v_solde_conge_employe`) avec recherche et pagination. |
| **`SoldesGantt`** | `components/soldes/SoldesGantt.jsx` | Visualisation planning Gantt interactif des présences et absences de l'équipe. |
| **`SoldeCongeWidget`** | `components/SoldeCongeWidget.jsx` | Widget synthétique du collaborateur affichant le solde disponible, les jours pris et les jours en attente. |
| **`PeriodesReferenceTab`** | `components/PeriodesReferenceTab.jsx` | Supervision DAF : consultation des périodes de 12 mois, journal mensuel des droits, clôture et activation de TCONGE. |
| **`CongeExpirationReviewCard`**| `components/CongeExpirationReviewCard.jsx` | Instruction DAF de l'examen triennal Art. 129 : formulaire de report (`REPORTE`) ou de confirmation d'expiration (`EXPIRE`). |
| **`MouvementsSoldeTable`** | `components/MouvementsSoldeTable.jsx` | Grand-livre comptable des mouvements du salarié (`mouvement_solde_conge`) avec traçabilité des imputations. |
| **`ParametresCongesTab`** | `components/ParametresCongesTab.jsx` | Administration des types de congés, motifs légaux (Art. 133), pools annuels (10 j/an) et statuts. |
| **`ParametresCongesModals`** | `components/ParametresCongesModals.jsx` | Modales de création et modification des référentiels d'absence avec plafonds et règles de bascule. |
| **`ExportEtatCongesModal`** | `components/ExportEtatCongesModal.jsx` | Téléchargement du bilan individuel certifié 12 mois et du grand récapitulatif collectif Excel. |

### 8.3 Catalogue Complet des Endpoints REST

#### 1. Cycle de Vie des Demandes (`/conges/demandes`)
| Méthode | URI | Contrôle d'Accès / Rôle | Description Métier & Contrat |
| :---: | :--- | :--- | :--- |
| `POST` | `/simuler` | `CONGE_READ` | Simulation temps réel du décompte de jours et projection de solde. |
| `POST` | `/verifier-conflits` | `CONGE_READ` | Détection des collisions d'absence sur le même poste/site (`CongeContinuiteService`). |
| `POST` | `/` | `CONGE_WRITE` | Création d'une nouvelle demande de congé (brouillon ou soumission directe). |
| `PUT` | `/{id}` | `CONGE_WRITE` | Mise à jour d'une demande existante en statut modifiable. |
| `GET` | `/employe/{idEmploye}` | `CONGE_READ` | Historique exhaustif des demandes d'un employé. |
| `GET` | `/{id}` | `CONGE_READ` | Consultation unitaire d'une demande avec `availableActions`. |
| `GET` | `/validation` | `CONGE_VALIDATE` | Liste des demandes en attente de validation pour le manager/RH connecté. |
| `GET` | `/all` | `CONGE_READ` | Consultation globale de toutes les demandes avec filtres multicritères. |
| `POST` | `/{id}/valider` | `CONGE_VALIDATE` | Validation transactionnelle : génération du débit `PRISE` au Grand-Livre. |
| `POST` | `/{id}/refuser` | `CONGE_VALIDATE` | Refus motivé d'une demande avec notification. |
| `POST` | `/{id}/annuler` | `CONGE_WRITE` / `CONGE_VALIDATE` | Annulation : restitution du solde au Grand-Livre (`ANNULATION`) et mise à jour pointage. |

#### 2. Soldes, Grand-Livre, Cutover & Expiration (`/conges`)
| Méthode | URI | Contrôle d'Accès / Rôle | Description Métier & Contrat |
| :---: | :--- | :--- | :--- |
| `GET` | `/solde/employe/{idEmploye}` | `CONGE_READ` | Compteurs temps réel issus de `v_solde_conge_employe`. |
| `GET` | `/solde/employe/{idEmploye}/situation` | `CONGE_READ` | Synthèse complète : solde, période active, TCONGE, expiration et pools. |
| `GET` | `/solde/valorisation-lots/{idEmploye}` | `CONGE_READ` | Détail des lots FIFO d'acquisition et valeurs restantes en Ariary. |
| `GET` | `/solde/imputation/{idEmploye}` | `CONGE_READ` | Diagnostic d'imputation financière des congés. |
| `GET` | `/mouvements/employe/{idEmploye}` | `CONGE_READ` | Grand-Livre des écritures comptables (`mouvement_solde_conge`). |
| `POST` | `/solde/ajustement` | `CONGE_VALIDATE` / `ROLE_ADMIN` | Ajustement manuel de solde motivé par la DAF. |
| `POST` | `/solde/cutover-opening` | `CONGE_VALIDATE` / `ROLE_ADMIN` | Reprise à date d'arrêté $T_0$ : injection stock initial et cumuls Janvier $\rightarrow T_0$. |
| `POST` | `/solde/cutover/{idEmploye}/corriger` | `CONGE_VALIDATE` / `ROLE_ADMIN` | Correction DAF auditée du stock initial de reprise (`conge_cutover_correction`). |
| `GET` | `/planning` | `CONGE_READ` | Planning global consolidé des congés pour affichage Gantt. |
| `GET` | `/expiration/employe/{idEmploye}` | `CONGE_READ` | Examen des droits candidats à prescription triennale (Loi 2024-014, Art. 129). |
| `POST` | `/expiration/employe/{idEmploye}/reporter` | `CONGE_VALIDATE` / `ROLE_ADMIN` | Décision DAF de report d'expiration avec date de prochaine revue. |
| `POST` | `/expiration/employe/{idEmploye}/confirmer`| `CONGE_VALIDATE` / `ROLE_ADMIN` | Confirmation d'expiration avec coche de planification et débit `EXPIRATION`. |
| `GET` | `/pool-consommation/employe/{idEmploye}`| `CONGE_READ` | Consommation courante sur les pools partagés (ex: événements familiaux 10j). |

#### 3. Périodes de Référence, Clôtures & Exports (`/conges/periodes-reference`)
| Méthode | URI | Contrôle d'Accès / Rôle | Description Métier & Contrat |
| :---: | :--- | :--- | :--- |
| `POST` | `/` | `CONGE_VALIDATE` / `ROLE_ADMIN` | Création d'une nouvelle période de référence annuelle. |
| `GET` | `/employe/{idEmploye}` | `CONGE_READ` | Historique des périodes de référence d'un collaborateur. |
| `GET` | `/employe/{idEmploye}/statut-configuration` | `CONGE_READ` | Évaluation de l'état de configuration et éligibilité à l'activation du TCONGE. |
| `GET` | `/en-attente-activation` | `CONGE_VALIDATE` / `ROLE_ADMIN` | Périodes clôturées dont le taux TCONGE est calculé mais pas encore activé. |
| `GET` | `/{idPeriodeReference}/mois` | `CONGE_READ` | Journal mensuel des 12 mois de référence issu de `conge_journal_droit`. |
| `POST` | `/{idPeriodeReference}/cloturer` | `CONGE_VALIDATE` / `ROLE_ADMIN` | Clôture formelle de la période et calcul du ratio officiel $\mathbf{TCONGE} = \frac{CV_{12}}{CD_{12}}$. |
| `POST` | `/{idPeriodeReference}/activer-taux` | `CONGE_VALIDATE` / `ROLE_ADMIN` | Activation opérationnelle du taux journalier dans `conge_taux_applicable`. |
| `GET` | `/{idPeriodeReference}/corrections-valorisation` | `CONGE_READ` | Audit des rectifications de valorisation de la période. |
| `POST` | `/{idPeriodeReference}/mois/{idJournal}/corriger-valorisation` | `CONGE_VALIDATE` / `ROLE_ADMIN` | Rectification DAF auditée de la valorisation d'un mois clos (`conge_valorisation_mensuelle_correction`). |
| `GET` | `/employe/{idEmploye}/export-excel` | `CONGE_READ` | Téléchargement du bilan annuel individuel 4 feuilles (`ETAT_CONGES_PAR_PERSONNE.xlsx`). |
| `GET` | `/{idPeriodeReference}/export-excel` | `CONGE_READ` | Bilan annuel individuel ciblé sur une période de référence spécifique. |
| `GET` | `/grand-recap/export-excel` | `CONGE_READ` | Grand récapitulatif collectif DAF (`GRAND_RECAP_CONGES_VALORISES.xlsx`). |

#### 4. Référentiels & Paramétrage Légal (`/conges/referentiels`)
| Méthode | URI | Contrôle d'Accès / Rôle | Description Métier & Contrat |
| :---: | :--- | :--- | :--- |
| `GET` | `/all` | `CONGE_READ` | Référentiel global complet (types, motifs, pools, statuts). |
| `GET` | `/types`, `/motifs`, `/pools`, `/statuts` | `CONGE_READ` | Listes unitaires des référentiels de congés. |
| `POST` | `/types`, `/motifs` | `ROLE_ADMIN` | Création d'un nouveau type de congé ou motif légal. |
| `PUT` | `/types/{id}`, `/motifs/{id}` | `ROLE_ADMIN` | Modification des règles et plafonds d'un type ou motif. |
| `PUT` | `/types/{id}/activer`, `/desactiver` | `ROLE_ADMIN` | Activation ou désactivation logique d'un type de congé. |
| `PUT` | `/motifs/{id}/activer`, `/desactiver` | `ROLE_ADMIN` | Activation ou désactivation logique d'un motif familial. |

---

## 9. Matrice des Codes d'Erreur & Réponses API Normalisées

| Code d'Erreur Backend | Cause Métier Déclenchée | Réaction Frontend |
| :--- | :--- | :--- |
| `LEAVE_BALANCE_INSUFFICIENT` | Solde disponible insuffisant pour couvrir la demande. Solde négatif strictement interdit. | Bloque la soumission avec affichage du solde disponible. |
| `LEAVE_NOTICE_NOT_RESPECTED` | Délai de préavis réglementaire non respecté (2j ou 15j) sans dérogation. | Affiche l'avertissement bloquant ou invite à demander dérogation préavis. |
| `LEAVE_POOL_EXCEEDED` | Plafond annuel partagé de 10 jours dépassé pour les événements familiaux. | Bloque la saisie et signale le dépassement du quota Art. 133. |
| `LEAVE_OVERLAP` | Chevauchement de dates avec une autre demande active du même salarié. | Affiche le conflit exact de dates avec la demande concurrente. |
| `LEAVE_SERVICE_CONTINUITY_CONFLICT`| Conflit de continuité de service : un collègue du même poste et site est déjà absent. | Bloque la validation sauf si `derogationContinuiteService = true`. |
| `LEAVE_PAYROLL_PERIOD_CLOSED`| La période de paie couvrant la date de congé est déjà clôturée. | Interdit formellement toute modification ou annulation rétroactive. |
| `CYCLE_ACCUMULATORS_UNKNOWN`| Cumuls d'ouverture (droits/valo) manquants pour la clôture cutover. | Bloque la clôture automatique et requiert la régularisation DAF des cumuls. |
| `LEAVE_DEMI_JOURNEE_INVALID` | Incohérence sur la demi-journée (`dateDebut != dateFin` ou moment absent). | Force la sélection du créneau `MATIN` ou `APRES_MIDI`. |
| `LEAVE_STATUT_INVALID` | Transition d'état illégale (ex: ré-annuler une demande déjà annulée). | Bloque l'action avec message explicatif d'idempotence. |

---

## 10. Éditions et Exports Excel Certifiés

### 10.1 Bilan Annuel Individuel (`ETAT_CONGES_PAR_PERSONNE.xlsx`)

Généré par [`CongeAnnuelExcelExportService.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/conge/application/CongeAnnuelExcelExportService.java) :
- Structuré sur 4 feuilles :
  1. **Feuille 1 — « État congés salarié »** : En-tête collaborateur, grille des 12 mois de référence issue de `conge_journal_droit`, retenues et indemnités de paie, solde progressif et préservation intégrale des formules Excel (`=SUM(...)`) avec `workbook.setForceFormulaRecalculation(true)`.
  2. **Feuille 2 — « Mouvements »** : Audit trail chronologique exhaustif du grand-livre filtré sur la période de référence.
  3. **Feuille 3 — « README »** : Rappel du cadre réglementaire officiel.
  4. **Feuille 4 — « Origine des droits »** : Traçabilité des lots FIFO restants et des imputations passées.
- Endpoints REST :
  - `GET /conges/periodes-reference/employe/{idEmploye}/export-excel`
  - `GET /conges/periodes-reference/{idPeriodeReference}/export-excel`

### 10.2 Grand Récapitulatif Collectif DAF (`GRAND_RECAP_CONGES_VALORISES.xlsx`)

Généré par [`CongeGrandRecapExcelExportService.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/conge/application/CongeGrandRecapExcelExportService.java) :
- Accessible via le bouton **« Grand récapitulatif Excel »** sous `/conges`.
- Endpoint : `GET /conges/periodes-reference/grand-recap/export-excel?dateDebut=YYYY-MM-DD&dateFin=YYYY-MM-DD`.
- Aucune limite artificielle de 100 lignes.
- Présente pour chaque collaborateur de l'entreprise : l'ouverture avant `dateDebut`, les mouvements de l'intervalle ventilés par type, le solde arrêté à `dateFin` et la valorisation financière exacte issue de la somme des valeurs restantes des lots historiques FIFO.

---

## 11. Données d'Audit Intégrales du Modèle Réel (Matricule 61)

Reproduction intégrale du cycle de calcul validant la concordance mathématique avec la feuille d'entreprise `OHATRA KAJY DROITS CONGES-given on sept26.xls` :

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

### Synthèse de Clôture
- **Cumul Annuel des Droits Acquis** : **$29{,}9567\text{ jours}$** (proratisation exacte au 30e).
- **Cumul Annuel de Valorisation Financière** : **$324\,385{,}38\text{ Ar}$**.
- **Taux Journalier TCONGE Calculé pour le Cycle Suivant** :
  $$\mathbf{TCONGE} = \frac{324\,385{,}38\text{ Ar}}{29{,}9567\text{ jours}} = \mathbf{10\,828{,}49\text{ Ar / jour}}$$

---

## 12. Recueil Textuel Intégral des Lois Malagasy (Loi 2024-014 & Loi 2003-044)

### 12.1 Extraits Officiels de la Loi n° 2024-014 (Nouveau Code du Travail)

#### Article 125 — Droit au congé annuel payé
> *« Sauf dispositions plus favorables des conventions collectives ou du contrat individuel de travail, le travailleur acquiert droit au congé payé à la charge de l'employeur, à raison de **deux jours et demi calendaires par mois de service effectif**, soit trente jours calendaires par an. »*

#### Article 127 — Décompte et jours fériés
> *« Sont exclus du décompte des congés payés les **jours fériés chômés et payés**, ainsi que les interruptions de travail dues à la maladie ou à l'accident dûment constatés par un médecin agréé survenus au cours de la période de congé. »*

#### Article 130 — Fractionnement, tranche minimale et ordre des départs
> *« Le congé peut être fractionné d'accord parties. Toutefois, la première fraction doit être d'une durée continue d'au moins **quinze (15) jours consécutifs**, obligatoirement prise dans les trois mois suivant l'ouverture du droit.*  
> *L'ordre et les dates de départ en congé sont fixés par l'employeur compte tenu des nécessités du service et de la situation de famille du travailleur. »*

#### Article 129 — Prescription triennale des droits
> *« Les droits à congé se prescrivent par **trois ans** à compter du jour où la période de référence a pris fin.*  
> *Toutefois, le travailleur peut combattre la prescription s'il établit que l'employeur l'a empêché de jouir de ses droits en raison de refus réitérés ou d'une défaillance dans la fixation de l'ordre des départs. »*

#### Article 130 — Ouverture de la jouissance
> *« Le droit de jouissance au congé est acquis après une période minimale de service effectif égale à **douze mois**. »*

#### Article 131 — Indemnité compensatrice de congé
> *« L'employeur doit verser au travailleur, pendant toute la durée du congé, une allocation au moins égale à la moyenne des salaires, indemnités et primes diverses perçus au cours des douze mois précédant le départ.*  
> *Cette allocation ne peut être inférieure au montant de la rémunération que le travailleur aurait perçue s'il avait continué à travailler.*  
> *En cas de rupture ou d'expiration du contrat, une indemnité compensatrice calculée sur la base des droits acquis lui est accordée. »*

#### Article 133 — Permissions exceptionnelles pour événements familiaux
> *« Des permissions exceptionnelles pour événements familiaux touchant directement le foyer du travailleur sont accordées par l'employeur dans la limite de **dix jours par an**.*  
> *Ces permissions ne peuvent être déduites du congé annuel si elles ont été payées.*  
> *Le travailleur doit produire les pièces justificatives dans un délai de six mois suivant l'événement. »*

#### Article 134 — Congé de paternité
> *« Tout travailleur masculin a droit, à l'occasion de la naissance de chaque enfant au sein de son foyer, à un congé de paternité rémunéré de **trois jours ouvrables**. Ce congé est indépendant des permissions exceptionnelles prévues à l'article 133. »*

#### Articles 142 & 143 — Congé de maternité
> *« Toute femme salariée a le droit de suspendre son travail pendant **quatorze semaines consécutives**, dont au moins six semaines postérieures à la délivrance.*  
> *Cette période peut être prolongée de **trois semaines** en cas de maladie résultant de la grossesse ou des couches. »*

---

### 12.2 Extraits Historiques de la Loi n° 2003-044 (Ancien Code du Travail)

Conservés à titre comparatif pour l'audit des contrats antérieurs et l'analyse rétrospective :

#### Article 13 — Suspension pour maladie ordinaire
> *« Le contrat est suspendu pendant la durée de l'absence du travailleur en cas de maladie dûment constatée par un médecin agréé, durée limitée à **six mois**.*  
> *Pendant cette période, l'employeur est tenu de verser au travailleur une indemnité égale au montant de sa rémunération dans la limite du **délai de préavis** [30 jours pour les employés mensuels]. »*

#### Article 86 — Bonification pour ancienneté
> *« La durée du congé est augmentée à raison de jours de congé supplémentaires par période entière, continue ou non, de service chez le même employeur, selon les stipulations de la convention collective ou du règlement intérieur. »*

#### Article 87 — Périodes assimilées à du travail effectif
> *« Sont assimilées à un mois de service effectif : les périodes de congés payés antérieurs, les repos de maternité, les suspensions pour accident du travail (limite 1 an), les absences maladie (limite 6 mois), les permissions d'événements familiaux. »*

---

*Document officiel d'ingénierie, de conformité légale et d'architecture GALAXAM SIRH — Validé et certifié sur le code source opérationnel.*
