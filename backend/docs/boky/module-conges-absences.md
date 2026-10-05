# Module Congés & Absences — Spécification Complète & Rapport de Référence
## GALAXAM SIRH — Documentation Canonique & Métier

> **Auteur** : Équipe d'Ingénierie & Architecture GALAXAM SIRH  
> **Module** : Congés, Absences, Droits Acquis, Grand-Livre (Ledger), Continuité & Valorisation Financière  
> **Version** : 3.3.0 (État réel du code source & certification transactionnelle — 90 tests JUnit 5 verts)  
> **Date de référence** : Octobre 2026  
> **Principes directeurs** : Le code source Java/SQL est le moteur et la source principale de vérité du système. Toute règle métier implémentée est adossée à une source légale ou réglementaire vérifiable.  
> **Sources juridiques et normatives de référence** :
> - **Loi n° 2024-014 du 1er juillet 2024** portant Code du Travail de Madagascar (Articles 125 à 135, 142 à 144)
> - **Loi n° 2003-044 du 28 juillet 2004** portant ancien Code du Travail de Madagascar (Articles 13, 85, 86, 87, 88, 89, 90, 91, 95, 97, 98, 126, 134 — conservés pour comparatif et traçabilité historique)
> - **Décret n° 2007-563 du 03 juillet 2007** relatif à la durée légale du travail (40 heures hebdomadaires = 173,33 heures mensuelles, diviseur horaire officiel)
> - **Décret n° 64-162 du 22 avril 1964** fixant les conditions et modalités d'application du régime des congés payés
> - **Modèle mathématique d'acquisition et valorisation financière d'entreprise** : `OHATRA KAJY DROITS CONGES-given on sept26.xls`
> - **Schémas de base de données canoniques** : `backend/conception/bdd/04_schema_conges.sql` & `backend/conception/bdd/06_schema_paie.sql`

---

## Sommaire Exécutif

Le présent document constitue la **source unique de vérité documentaire, juridique et technique** du module **Congés & Absences** du SIRH GALAXAM.  
Il est rédigé selon le principe fondamental où **le code source opérationnel conduit et reflète fidèlement la réalité**, soutenu par des justifications légales et normatives documentées :

1. **L'alignement direct sur le code Java et PostgreSQL** : 31 fichiers de production (`mg.galaxam.sirh.conge`), 8 tables relationnelles dédiées, 3 vues analytiques, 90 tests automatisés d'intégration et unitaires 100% passants (`BUILD SUCCESS`).
2. **Le respect des règles légales de décompte** : Résolution temporelle dynamique des jours fériés via `CongeDecompteCalendaireResolver` et le paramètre global `CONGE_DECOMPTE_FERIES` (`INCLUS` ou `EXCLUS`), permettant l'application conforme de l'Article 127 du Code du Travail 2024.
3. **Le verrouillage strict du solde débiteur** : Interdiction absolue de la prise par anticipation en solde négatif au niveau métier (`DemandeConge.java`, `CongeService.java`, `DemandeCongeController.java`).
4. **La protection de la continuité opérationnelle** : Détection algorithmique des collisions de présence d'équipe par poste et site géographique (`CongeContinuiteService.java`) avec dérogation bloquante.
5. **Le modèle mathématique canonique (`OHATRA KAJY DROITS CONGES`)** : Proratisation au trentième, valorisation mensuelle $V_m = \frac{B_{totale} \times D_m}{30}$, constitution de lots FIFO immuables et calcul du taux journalier unitaire de cycle `TCONGE`.
6. **Le Grand-Livre des congés (`mouvement_solde_conge`) & Audit DAF** : Traçabilité absolue des mouvements (débits `PRISE`, crédits `ACQUISITION`, `BONIFICATION`, `AJUSTEMENT`, `ANNULATION`, `EXPIRATION`), audit des corrections de cutover (`conge_cutover_correction`), rectifications de valorisation (`conge_valorisation_mensuelle_correction`) et examen motivé des droits candidats à prescription (`conge_expiration_examen`).
7. **L'interconnexion certifiée** avec les modules **Pointage** (qualification des présences et régularisations automatiques), **Paie** (retenue au 1/30e et indemnité de congé valorisée) et **STC** (liquidation des reliquats de lots).

---

## Table des Matières

1. [Cadre Légal et Réglementaire Malagasy](#1-cadre-légal-et-réglementaire-malagasy)
2. [Modèle Mathématique Canonique (OHATRA KAJY DROITS CONGES)](#2-modèle-mathématique-canonique-ohatra-kajy-droits-conges)
3. [Architecture du Modèle de Données PostgreSQL](#3-architecture-du-modèle-de-données-postgresql)
4. [Règles de Gestion Métier (RG) Détaillées](#4-règles-de-gestion-métier-rg-détaillées)
5. [Workflow & Circuit de Validation Transactionnel](#5-workflow--circuit-de-validation-transactionnel)
6. [Intégrations Certifiées : Pointage, Paie & STC](#6-intégrations-certifiées--pointage-paie--stc)
7. [Automatisations, FIFO & Schedulers Nocturnes](#7-automatisations-fifo--schedulers-nocturnes)
8. [Cartographie des Composants Logiciels (Backend & Frontend)](#8-cartographie-des-composants-logiciels-backend--frontend)
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
| **Loi 2024-014, Art. 125**<br>*(Loi 2003-044, Art. 85)* | **Acquisition du droit à congé payé**<br>• 2,5 jours calendaires par mois de service effectif (30 jours par an).<br>• Acquisition à la charge exclusive de l'employeur. | • [`CongeService.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/conge/application/CongeService.java) (`enregistrerAcquisitionMensuelle`).<br>• Stockage mensuel dans `conge_journal_droit` et crédit dans `mouvement_solde_conge` (`ACQUISITION`). |
| **Loi 2024-014, Art. 127** | **Exclusion des jours fériés du congé payé**<br>• *« Sont exclus du décompte des congés payés les jours fériés chômés et payés... »* | • [`CongeDecompteCalendaireResolver.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/conge/application/CongeDecompteCalendaireResolver.java).<br>• Résolution par jour du paramètre `CONGE_DECOMPTE_FERIES` (`INCLUS`/`EXCLUS`). Si `EXCLUS`, déduction automatique des fériés nationaux et locaux (`site_calendrier_ferie`). |
| **Loi 2024-014, Art. 130**<br>*(Loi 2003-044, Art. 85)* | **Ouverture du droit de jouissance**<br>• La jouissance du congé annuel est acquise après une période minimale de **12 mois de service effectif**. | • [`DemandeConge.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/conge/persistence/DemandeConge.java#L223-L242) : contrôle de l'ancienneté contractuelle (`moisService >= 12`) sur les congés de type `ANNUEL`. Blocage immédiat sinon. |
| **Loi 2024-014, Art. 129**<br>*(Loi 2003-044, Art. 85)* | **Prescription triennale & Protection du salarié**<br>• Prescription de **3 ans** à compter de la fin de la période de référence.<br>• Le salarié peut combattre la prescription s'il établit que l'employeur l'a empêché de jouir de ses droits. | • [`CongeExpirationExamenService.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/conge/application/CongeExpirationExamenService.java) & table `conge_expiration_examen`.<br>• **Zéro purge automatique**. Revue DAF formelle : `REPORTE` ou `EXPIRE` avec confirmation obligatoire `planification_verifiee = true`. |
| **Loi 2024-014, Art. 128**<br>*(Loi 2003-044, Art. 89 & 95)* | **Fractionnement, Tranche continue & Continuité**<br>• Fractionnement possible d'accord parties avec au moins **une tranche continue d'au moins 12 jours consécutifs**.<br>• Départ fixé par l'employeur selon nécessités de service. | • [`CongeCalculateurEngine.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/conge/domain/CongeCalculateurEngine.java) & paramètre `DUREE_TRANCHE_MIN_FRACTIONNEMENT_JOURS = 12`.<br>• [`CongeContinuiteService.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/conge/application/CongeContinuiteService.java) : détection des conflits sur le même poste/site. |
| **Loi 2024-014, Art. 133**<br>*(Loi 2003-044, Art. 88)* | **Permissions pour événements de famille (10 jours/an)**<br>• Quota légal de **10 jours par an** non déductibles du congé annuel si payées.<br>• Justificatif dans les 6 mois. | • [`RefPoolCongeAnnuel.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/conge/persistence/RefPoolCongeAnnuel.java) : pool `PERMISSIONS_FAMILIALES` plafonné à 10,0 j/an.<br>• Quotas individuels : Mariage (3j), Décès conjoint/enfant (3j), Naissance (2j), Décès parent (2j). |
| **Loi 2024-014, Art. 134**<br>*(Loi 2003-044, Art. 126 & 134)* | **Congé de paternité autonome**<br>• **3 jours ouvrables rémunérés** à la naissance d'un enfant.<br>• Droit autonome, expressément **disjoint du pool de 10 jours**. | • [`RefTypeConge.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/conge/persistence/RefTypeConge.java) : `id_pool_conge_annuel IS NULL` sur le code `PATERNITE`. `duree_fixe_jours = 3.0`. |
| **Loi 2024-014, Art. 142 & 143**<br>*(Loi 2003-044, Art. 97 & 98)* | **Congé de maternité & Extension pathologique**<br>• **14 semaines consécutives (98 jours)** dont 6 post-accouchement.<br>• Prolongation de **3 semaines (21 jours)** en cas d'état pathologique attesté (portant à 119 jours).<br>• Maintien intégral des droits à congés payés. | • Paramètres `DUREE_MAX_MATERNITE_STANDARD_JOURS = 98` et `DUREE_MAX_MATERNITE_PATHOLOGIQUE_JOURS = 119`.<br>• Flag `extension_pathologique_maternite` dans `demande_conge`.<br>• Assimilation à du travail effectif à 100%. |
| **Loi 2003-044, Art. 13** | **Maladie ordinaire & Relais de prévoyance CNaPS**<br>• Suspension jusqu'à 6 mois.<br>• Maintien légal de salaire limité au préavis (**30 jours** pour mensuels). | • `RefTypeConge` : type `MALADIE` borné à 30 jours continus payés.<br>• Bascule automatique dès le 31e jour vers `MALADIE_SANS_SOLDE` (`id_type_conge_bascule = 1`) pour prise en charge CNaPS. |
| **Loi 2024-014, Art. 126**<br>*(Loi 2003-044, Art. 86)* | **Bonifications pour ancienneté**<br>• Majoration de la durée des congés payés selon convention ou règlement d'entreprise. | • Moteur pur [`CongeCalculateurEngine.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/conge/domain/CongeCalculateurEngine.java) : éligibilité $\ge 3$ ans, bonus $+1$ jour par tranche complète de 5 ans d'ancienneté. Écriture `BONIFICATION` dans le ledger. |
| **Loi 2024-014, Art. 131**<br>*(Loi 2003-044, Art. 90)* | **Indemnité de congé payé & Règle du salaire continué**<br>• Maintien au moins égal au salaire moyen des 12 derniers mois et non inférieur au salaire continué. | • Service [`CongeTauxApplicableService.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/conge/application/CongeTauxApplicableService.java) : valorisation mensuelle $V_m$ et résolution temporelle de `TCONGE`. |

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

---

## 3. Architecture du Modèle de Données PostgreSQL

Le schéma de données certifié (`04_schema_conges.sql` et `06_schema_paie.sql`) garantit l'intégrité référentielle complète :

```
                        ┌──────────────────────────────┐
                        │           EMPLOYE            │
                        └──────────────┬───────────────┘
                                       │ 1
        ┌──────────────────────────────┼──────────────────────────────┬──────────────────────────────┐
        │ N                            │ N                            │ N                            │ N
 ┌──────▼───────────────────────┐┌─────▼────────────────────────┐┌─────▼───────────────────────┐┌──────▼───────────────────────┐
 │        DEMANDE_CONGE         ││    MOUVEMENT_SOLDE_CONGE     ││   CONGE_EXPIRATION_EXAMEN   ││   CONGE_PERIODE_REFERENCE   │
 │  (Cycle de vie & Contrôles)  ││   (Grand-Livre Comptable)    ││   (Workflow Décisions DAF)  ││   (Périodes Annuelles 12M)  │
 └──────┬───────────────────────┘└─────┬────────────────────────┘└─────────────────────────────┘└──────┬───────────────────────┘
        │ N                            │ 1                                                             │ 1
        │                              ├──────────────────────────────┐                                │ N
        ├──────────────────────┐       │ 1                            │ 1                         ┌────▼────────────────────────┐
        │ 1                    │ 1┌────▼────────────────────────┐┌────▼────────────────────────┐  │    CONGE_TAUX_APPLICABLE    │
 ┌──────▼───────────────┐┌─────▼──┴─────────────────────┐┌──────┴───────────────────────┐ │  │ (TCONGE Historique & Cutover)│
 │    REF_TYPE_CONGE    ││    CONGE_CUTOVER_CORRECTION  ││CONGE_VALORISATION_CORRECTION │ │  └─────────────────────────────┘
 │(Conditions & Règles) ││ (Corrections Reliquat DAF)   ││(Rectifications Valeur DAF)   │ │
 └──────┬───────────────┘└──────────────────────────────┘└──────────────────────────────┘ │
        │ N                                                                             │ 1 (optionnel)
        ├──────────────────────┐                                                  ┌─────▼────────────────────────┐
        │ 1                    │ 1                                                │     CONGE_JOURNAL_DROIT      │
 ┌──────▼───────────────┐┌─────▼────────────────────────┐                         │  (Grand-Livre Paie Droits)   │
 │REF_POOL_CONGE_ANNUEL ││REF_MOTIF_EVENEMENT_FAMILIAL  │                         └──────────────────────────────┘
 │ (Plafond 10j/an)     ││ (4 motifs légaux Art. 88/133)│
 └──────────────────────┘└──────────────────────────────┘
```

### 3.1 Dictionnaire des Tables Principales

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
Quotas individuels des motifs légaux prévus par l'Article 88 / Article 133 :
- `MARIAGE` : 3 jours | `NAISSANCE` : 2 jours | `DECES_CONJOINT_ENFANT` : 3 jours | `DECES_PARENT` : 2 jours.

#### 4. `demande_conge`
Enregistre chaque demande et son statut opérationnel :
- `id_demande_conge` (PK SERIAL), `id_employe` (FK), `id_type_conge` (FK), `id_statut_demande_conge` (FK), `id_motif_evenement_familial` (FK nullable)
- `date_debut`, `date_fin`, `date_demande`, `nb_jours_decomptes` (NUMERIC(5,2))
- `derogation_solde_negatif` (booléen), `derogation_delai_preavis` (booléen), `derogation_continuite_service` (booléen), `extension_pathologique_maternite` (booléen)
- `est_demi_journee` (booléen), `moment_demi_journee` (`MATIN`, `APRES_MIDI`)
- `saisi_par`, `valide_par`, `date_validation`, `motif_refus`, `commentaire`
- Contraintes : `chk_demande_conge_dates` (`date_fin >= date_debut`), `chk_demande_conge_demi_journee`.

#### 5. `mouvement_solde_conge` (Grand-Livre Comptable / Ledger)
Registre immuable de chaque débit et crédit de congés :
- `id_mouvement` (PK SERIAL), `id_employe` (FK), `date_mouvement`
- `type_mouvement` (`ACQUISITION`, `BONIFICATION`, `PRISE`, `AJUSTEMENT`, `ANNULATION`, `EXPIRATION`)
- `nb_jours` (NUMERIC(5,2) signé : positif pour crédit, négatif pour débit)
- `id_demande_conge` (FK nullable vers la demande d'origine)
- `valeur_source` (NUMERIC(14,2) pour mémorisation de valorisation financière historique)
- **Index uniques partiels anti-doublon certifiés** :
  - `idx_uniq_mouvement_prise_demande` : empêche toute double écriture de `PRISE` pour une même demande.
  - `idx_uniq_mouvement_annulation_demande` : garantit l'idempotence stricte de l'opération d'annulation.

#### 6. `conge_cutover_correction` (Audit des corrections DAF de stock initial)
Permet à la DAF de corriger les anomalies de reprise de solde sans altérer rétroactivement les bulletins passés :
- `id_correction` (PK SERIAL), `id_employe` (FK), `id_mouvement_reprise` (FK), `id_mouvement_ajustement` (FK unique)
- `ancien_reliquat_jours`, `nouveau_reliquat_jours`, `ancien_reliquat_valeur`, `nouveau_reliquat_valeur`
- `ancien_taux`, `nouveau_taux`, `regularisation_a_examiner`, `motif`, `corrige_par`, `date_correction`.

#### 7. `conge_valorisation_mensuelle_correction` (Rectification DAF de valorisation mensuelle)
Permet à la DAF de rectifier la valorisation en ariary d'un mois clos sans modifier les jours acquis ni réouvrir la paie :
- `id_correction` (PK SERIAL), `id_journal` (FK vers `conge_journal_droit`), `id_mouvement_acquisition` (FK)
- `ancienne_valeur`, `nouvelle_valeur`, `motif`, `corrige_par`, `date_correction`.

#### 8. `conge_expiration_examen` (Audit des décisions DAF de prescription)
Historise les examens des droits candidats à prescription (Loi 2024-014 Art. 129) :
- `id_examen` (PK SERIAL), `id_employe` (FK), `decision` (`REPORTE` ou `EXPIRE`), `jours_examines`, `motif`
- `planification_verifiee` (booléen obligatoire pour `EXPIRE`), `date_prochaine_revue` (obligatoire pour `REPORTE`)
- `id_mouvement_expiration` (FK vers `mouvement_solde_conge`), `decide_par`.

#### 9. `conge_periode_reference` & `conge_taux_applicable`
Périodes de 12 mois bornées du 01/01 au 31/12 avec contrainte d'exclusion temporelle GIST (`daterange` sans chevauchement) et historisation du taux journalier `TCONGE` avec convention $[date\_effet, date\_fin[$.

#### 10. `conge_journal_droit` (Grand-Livre Mensuel de Paie — `06_schema_paie.sql`)
Trace mois par mois la constitution des droits et de la réserve financière :
- `id_journal` (PK SERIAL), `id_employe` (FK), `id_periode_paie` (FK), `id_periode_reference` (FK)
- `salaire_base`, `jours_travailles_formule`, `jours_droit_acquis`, `base_valorisation`, `valorisation_mensuelle`, `cumul_jours_droit`, `cumul_valorisation`, `taux_journalier_conge`.

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
- Calculée à la date anniversaire d'embauche de l'employé (ou rattrapée par `AcquisitionCongeScheduler`) :
  - Éligibilité : Ancienneté $\ge 3$ ans chez l'employeur.
  - Majoration : $+1$ jour supplémentaire par tranche complète de 5 ans d'ancienneté :
    $$\text{Bonus} = \left\lfloor \frac{\text{Années d'ancienneté}}{5} \right\rfloor \times 1\text{ jour}$$
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
- Au moins une tranche continue de congé annuel payé doit atteindre **12 jours consécutifs** au cours de l'exercice (Article 128 Loi 2024-014 / Article 89 Loi 2003-044).
- Alerte RH automatique émise par `NotificationRhScheduler` au 4e trimestre si l'employé n'a posé aucune tranche $\ge 12$ jours.

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

L'approbation d'une demande de congé met à jour automatiquement la grille de pointage mensuelle via `PointageSaisieService` :

| Propriétés du Type de Congé | Statut dans Pointage | Heures Badgées | Heures Rémunérées DTO |
| :--- | :--- | :---: | :---: |
| **`decompte_solde_acquis = true`** (ex: `ANNUEL`) | Statut `CONGE` (id 5) | `0.0 h` | Forfait horaire contractuel |
| **`decompte_solde_acquis = false` + payé** (ex: `EXCEPTIONNEL`) | Statut `DISPENSE_POINTAGE` (id 10) | `0.0 h` | Forfait horaire contractuel |
| **Non rémunéré** (ex: `SANS_SOLDE`, `MALADIE_SANS_SOLDE`) | Statut `ABSENCE_JUSTIFIEE` (id 4) | `0.0 h` | `0.0 h` (Non rémunéré) |

**Régularisation d'absence en congé depuis le pointage** :
- Réalisée par `CongeService.regulariserCongeAnnuelDepuisPointageStructure` : saisit la date de reprise, calcule la date de fin inclusive et le volume calendaire réel.
- Le solde de congé doit couvrir l'intégralité de la période à régulariser sous peine de rejet immédiat.
- En cas d'annulation ultérieure de la demande validée issue d'une régularisation, le solde de congé est restitué et les pointages redeviennent automatiquement des absences non justifiées dans la même transaction.

### 6.2 Pont Réel Congés $\rightarrow$ Moteur de Paie

Lors du calcul du bulletin mensuel :
1. **Retenue sur salaire de base pour congé pris** (au trentième légal) :
   $$\text{DEDUCTION\_CONGE\_BASE} = \text{Jours Congé Pris} \times \frac{\text{Salaire de Base Contractuel}}{30}$$
2. **Indemnité de congé payé** : chaque fraction de prise consomme la valeur historique de son lot FIFO. Le bulletin additionne les montants imputés :
   $$\text{INDEMNITE\_CONGE\_PAYE} = \sum_{lot} (\text{Jours Pris}_{lot} \times \mathbf{TCONGE}_{lot})$$
3. **Écart normal entre retenue et indemnité** : Le taux historique du lot peut différer du salaire courant / 30. Les deux lignes de paie figurent distinctement sur le bulletin de paie.

### 6.3 Pont Réel Congés $\rightarrow$ Solde de Tout Compte (STC)

Lors de la rupture du contrat de travail (licenciement, démission, départ à la retraite, terme de CDD) :
- En vertu de l'Article 131 de la Loi n° 2024-014 (Article 90 Loi 2003-044), une indemnité compensatrice de congé payé est obligatoirement versée.
- Le service `StcService` interroge `CongeLotValorisationService` pour liquider exactement la valeur financière restante des lots non consommés :
  $$\text{Indemnité Compensatrice STC} = \sum_{lot} \text{Valeur restante du lot}$$

---

## 7. Automatisations, FIFO & Schedulers Nocturnes

| Horaire | Composant Scheduler | Rôle & Traitement Effectué |
| :---: | :--- | :--- |
| **00:05** | `NotificationRhScheduler` | Détection et dispatching des alertes RH de supervision (solde faible, expiration candidate proche, tranche de fractionnement non respectée). |
| **1er jour du mois, 01:00** | `AcquisitionCongeScheduler` | Exécution des bonifications d'ancienneté pour le mois échu avec contrôle d'antériorité. L'acquisition mensuelle standard est quant à elle enregistrée lors de la clôture de la paie. |
| — | — | **Aucun cron nocturne ne purge automatiquement les congés prescrits.** L'examen est obligatoirement instruit par la DAF. |

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

Le frontend n'implémente aucune règle de calcul métier : il consomme les endpoints REST et réagit aux permissions et à `availableActions` :
- `DemandeCongeModal.jsx` : Modale de saisie dynamique avec dates de début et reprise.
- `DatesCongeSection.jsx` : Déclencheur temps réel de l'endpoint `/simuler`.
- `SoldeCongeWidget.jsx` & `SoldesOverviewTab.jsx` : Affichage temps réel des compteurs issus de `v_solde_conge_employe`.
- `DemandesValidationTab.jsx` : Tableau de bord des approbations piloté par `availableActions`.
- `ExamenExpirationModal.jsx` : Interface DAF pour le report ou la confirmation d'expiration.
- `ValorisationLotsTab.jsx` : Visualisation de l'origine FIFO des droits et consommations.
- `ExportEtatCongesModal.jsx` : Téléchargement du bilan individuel et du grand récapitulatif collectif Excel.

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

#### Article 128 — Fractionnement et ordre des départs
> *« Le congé peut être fractionné d'accord parties. Toutefois, l'une des fractions doit être d'une durée continue d'au moins **douze jours calendaires consécutifs**.*  
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
