# Module Congés & Absences — Spécification Complète & Rapport de Référence
## GALAXAM SIRH — Documentation Canonique & Métier

> **Auteur** : Équipe d'Ingénierie & Architecture GALAXAM SIRH  
> **Module** : Congés, Absences, Droits Acquis, Grand-Livre (Ledger), Continuité & Valorisation Financière  
> **Version documentaire** : 3.4.0 — état du code relu le 24 octobre 2026
> **Date de référence** : Octobre 2026  
> **Principes directeurs** : Le CDC détermine le périmètre initial ; le code Java/SQL décrit l'implémentation actuelle ; les tests et rapports datés établissent les comportements vérifiés. La présence d'une règle dans le code ne constitue pas une validation juridique indépendante.
> **Sources juridiques et normatives de référence** :
> - [**Loi n° 2024-014 du 14 août 2024 portant Code du Travail**](https://cnlegis.gov.mg/uploads/L2024-014-VF.pdf) (adoptée le 01/07/2024, validée par la HCC le 08/08/2024 [Décision n° 11-HCC/D3](https://www.hcc.gov.mg/?p=9311), promulguée le 14/08/2024 ; Articles 125 à 135, 142 à 144)
> - [**Loi n° 2003-044 du 28 juillet 2004**](https://www.assemblee-nationale.mg/media/archives_textes/pl/2003-044/Loi-n-2003-044-finale-Portant-Code-du-Travail-1.pdf) portant ancien Code du Travail de Madagascar (Articles 13, 86, 87, 88, 89, 90, 91, 95, 97, 98, 126, 134 — conservés pour comparatif et traçabilité historique)
> - [**Décret n° 72-226 du 6 juillet 1972**](https://fr.scribd.com/document/887714691/Droit-Malgache-Du-Travail) et Loi n° 2024-014 (Art. 109) relatifs à la durée légale du travail (40 heures hebdomadaires = 173,33 heures mensuelles dans les entreprises non agricoles)
> - [**Décret n° 64-161 du 22 avril 1964**](https://cabinetkim.mg/blog/code-du-travail-madagascar-nouvelle-loi-2024-014/) fixant les conditions et modalités d'application du régime des congés payés
> - **Modèle mathématique d'acquisition et valorisation financière d'entreprise** : `OHATRA KAJY DROITS CONGES-given on sept26.xls` (feuille non vérifiée ; sa provenance et son contenu restent à confirmer avant d'utiliser ses exemples comme preuve)
> - **Schémas de base de données canoniques** : `backend/conception/bdd/04_schema_conges.sql` & `backend/conception/bdd/06_schema_paie.sql`

---

## Sommaire Exécutif

Ce document décrit le module **Congés & Absences** de GALAXAM SIRH. Le CDC fixe les exigences initiales, le code décrit les comportements implémentés et les résultats de tests/recettes datés précisent ce qui a été vérifié :

1. **Implémentation et preuves de test** : services Java, schémas SQL et vues analytiques décrivent les mécanismes présents. Des tests ciblés existent, notamment `CongeLotValorisationEngineTest`, `CongePostgresIntegrationTest`, `PayrollLeaveAcquisitionPostgresIntegrationTest`, `PointageLeaveStatusTest` et `RemunerationCongePolicyTest`. Lors de DOC-04A, le test unitaire backend `RemunerationCongePolicyTest` et le test frontend `remunerationOverride.test.js` ont été exécutés avec succès ; ils couvrent la politique d'autorisation et le payload, pas le calcul de paie ni tous les appels HTTP. Aucun décompte global ni succès de l'ensemble du module n'est revendiqué ; les tests PostgreSQL cités ne sont pas déclarés exécutés dans cette correction documentaire.
2. **La gestion unifiée des Deux Flux Fondamentaux** : Traitement rigoureux et étanche du **Flux 1 (Nouvel Employé / Embauche Native)** avec carence légale de 12 mois (Art. 130) et du **Flux 2 (Reprise Historique / Cutover à date d'arrêté)** sans import d'historique lourd, avec reprise du stock arrêté, reprise des cumuls intermédiaires d'exercice et audit DAF via `conge_cutover_correction`.
3. **Le décompte des jours fériés** : `CongeDecompteCalendaireResolver` applique la version datée de `CONGE_DECOMPTE_FERIES`. La configuration actuelle est `INCLUS` : les jours fériés sont compris dans les dates et débités. Le paramètre peut être configuré sur `EXCLUS` si l'entreprise choisit cette politique.
4. **Le verrouillage strict du solde débiteur** : Interdiction absolue de la prise par anticipation en solde négatif au niveau métier (`DemandeConge.java`, `CongeService.java`, `DemandeCongeController.java`).
5. **La protection de la continuité opérationnelle** : Détection algorithmique des collisions de présence d'équipe par poste et site géographique (`CongeContinuiteService.java`) avec dérogation bloquante.
6. **Les mécanismes présents dans le code** : valorisation mensuelle, constitution de lots FIFO et calcul du taux journalier de cycle `TCONGE`. La justification métier et les exemples chiffrés citant `OHATRA KAJY DROITS CONGES` restent à rapprocher de leur source originale.
7. **Le Grand-Livre des congés (`mouvement_solde_conge`) & audit DAF** : journalisation des mouvements (`PRISE`, `ACQUISITION`, `BONIFICATION`, `AJUSTEMENT`, `ANNULATION`, `EXPIRATION`), avec contraintes SQL ciblées contre certains doublons, audit des corrections de cutover (`conge_cutover_correction`), rectifications de valorisation (`conge_valorisation_mensuelle_correction`) et examen motivé des droits candidats à prescription (`conge_expiration_examen`). La table n'est pas protégée par SQL contre toute modification ou suppression ; voir le dictionnaire.
8. **Les intégrations présentes dans le code** avec **Pointage** et **Paie** ; les niveaux de recette diffèrent selon le parcours. Le STC est une extension postérieure au CDC initial, décrite dans le document Paie.

**État des preuves de recette communiquées séparément :** UX-17 rapporte l'observation d'un débit FIFO simple ; le FIFO multi-lots n'est pas démontré en recette navigateur par les éléments disponibles. UX-18 rapporte une acquisition mensuelle observée, tandis que l'activation annuelle TCONGE n'a pas été vérifiée de bout en bout. Ces indications proviennent des comptes rendus UX communiqués dans la conversation ; aucun rapport UX-17/18 distinct n'a été retrouvé dans le dépôt. Les tests automatisés ciblés ne remplacent pas ces recettes métier.

---

## Table des Matières

1. [Cadre Légal et Réglementaire Malagasy](#1-cadre-légal-et-réglementaire-malagasy)
2. [Modèle de calcul et référence d'entreprise non vérifiée](#2-modèle-de-calcul-et-référence-dentreprise-non-vérifiée)
   - 2.1 [Définition des Variables Mensuelles & Trentième](#21-définition-des-variables-mensuelles)
   - 2.2 [Résolution du Taux Journalier Applicable (TCONGE)](#22-résolution-du-taux-journalier-applicable-tconge)
   - 2.3 [Les Deux Flux Fondamentaux : Nouvel Employé vs Reprise Cutover](#23-les-deux-flux-fondamentaux-de-gestion-des-congés)
3. [Architecture du Modèle de Données PostgreSQL (12 Tables Congés, 3 Vues)](#3-architecture-du-modèle-de-données-postgresql)
   - 3.1 [Dictionnaire des 12 Tables Relationnelles Dédiées](#31-dictionnaire-des-12-tables-relationnelles-dédiées)
   - 3.2 [Vues Analytiques SQL Dédiées](#32-vues-analytiques-sql-dédiées)
4. [Règles de Gestion Métier (RG) Détaillées](#4-règles-de-gestion-métier-rg-détaillées)
5. [Workflow & Circuit de Validation Transactionnel](#5-workflow--circuit-de-validation-transactionnel)
6. [Intégrations présentes dans le code : Pointage, Paie & STC](#6-intégrations-présentes-dans-le-code--pointage-paie--stc)
7. [Automatisations, FIFO & Schedulers Nocturnes](#7-automatisations-fifo--schedulers-nocturnes)
8. [Cartographie des Composants Logiciels (Backend, Frontend & REST)](#8-cartographie-des-composants-logiciels-backend--frontend)
   - 8.1 [Backend Java (31 Fichiers de Production Confirmés)](#81-backend-java-31-fichiers-de-production-confirmés)
   - 8.2 [Frontend React Backend-Driven](#82-frontend-react-backend-driven-frontendsrcfeaturesleave)
   - 8.3 [Catalogue Complet des Endpoints REST](#83-catalogue-complet-des-endpoints-rest)
9. [Matrice des Codes d'Erreur & Réponses API Normalisées](#9-matrice-des-codes-derreur--réponses-api-normalisées)
10. [Éditions et exports Excel](#10-éditions-et-exports-excel)
11. [Données d'Audit Intégrales du Modèle Réel (Matricule 61)](#11-données-daudit-intégrales-du-modèle-réel-matricule-61)
12. [Recueil Textuel Intégral des Lois Malagasy (Loi 2024-014 & Loi 2003-044)](#12-recueil-textuel-intégral-des-lois-malagasy-loi-2024-014--loi-2003-044)

---

## 1. Cadre Légal et Réglementaire Malagasy

### 1.1 Tableau Synoptique des Fondements Juridiques

Les congés payés sont régis par des dispositions d'**ordre public absolu** : toute convention individuelle ou collective prévoyant l'abandon du droit à congé contre indemnité pécuniaire en cours d'exécution du contrat est **nulle de plein droit**.

| Article Code du Travail (Loi 2024-014 / Loi 2003-044) | Libellé & Portée Juridique | Traduction Technique & Classe Java Responsable |
| :--- | :--- | :--- |
| **Loi 2024-014, Art. 125**<br>*(Loi 2003-044, Art. 86)* | **Acquisition du droit à congé payé**<br>• 2,5 jours calendaires par mois de service effectif (30 jours par an).<br>• Acquisition à la charge exclusive de l'employeur. | • [`CongeService.java`](../../src/main/java/mg/galaxam/sirh/conge/application/CongeService.java) (`enregistrerAcquisitionMensuelle`).<br>• Stockage mensuel dans `conge_journal_droit` et crédit dans `mouvement_solde_conge` (`ACQUISITION`). |
| **Loi 2024-014, Art. 127** | **Exclusion des jours fériés du congé payé** | • [`CongeDecompteCalendaireResolver.java`](../../src/main/java/mg/galaxam/sirh/conge/application/CongeDecompteCalendaireResolver.java).<br>• Résolution par jour de `CONGE_DECOMPTE_FERIES` (`INCLUS` ou `EXCLUS`) ; les fériés nationaux et locaux (`site_calendrier_ferie`) sont exclus uniquement sous `EXCLUS`. La configuration actuelle `INCLUS` constitue un écart à examiner au regard de cet article. |
| **Loi 2024-014, Art. 130**<br>*(Loi 2003-044, Art. 88 al. 1)* | **Ouverture du droit de jouissance**<br>• La jouissance du congé annuel est acquise après une période minimale de **12 mois de service effectif**. | • [`DemandeConge.java`](../../src/main/java/mg/galaxam/sirh/conge/persistence/DemandeConge.java#L223-L242) : contrôle de l'ancienneté contractuelle (`moisService >= 12`) sur les congés de type `ANNUEL`. Blocage immédiat sinon. |
| **Loi 2024-014, Art. 129**<br>*(Loi 2003-044, Art. 87)* | **Prescription triennale & Protection du salarié**<br>• Prescription de **3 ans** à compter de la fin de la période de référence.<br>• Le salarié peut combattre la prescription s'il établit que l'employeur l'a empêché de jouir de ses droits. | • [`CongeExpirationExamenService.java`](../../src/main/java/mg/galaxam/sirh/conge/application/CongeExpirationExamenService.java) & table `conge_expiration_examen`.<br>• **Zéro purge automatique**. Revue DAF formelle : `REPORTE` ou `EXPIRE` avec confirmation obligatoire `planification_verifiee = true`. |
| **Loi 2024-014, Art. 130**<br>*(Loi 2003-044, Art. 89 & 95)* | **Fractionnement, Tranche continue & Continuité**<br>• Première tranche continue d'au moins **15 jours** dans les trois mois suivant l'ouverture du droit, sous réserve des nécessités de service.<br>• Départ fixé par l'employeur selon nécessités de service. | • `DUREE_TRANCHE_MIN_FRACTIONNEMENT_JOURS = 15` existe dans `CongeCalculateurEngine`, mais aucun contrôle de la première tranche dans les trois mois n'est implémenté.<br>• [`CongeContinuiteService.java`](../../src/main/java/mg/galaxam/sirh/conge/application/CongeContinuiteService.java) : détection des conflits sur le même poste/site. |
| **Loi 2024-014, Art. 126(4)** | **Permissions exceptionnelles pour événements familiaux (10 jours/an)**<br>• Plafond global de **10 jours par an**, non déductibles du congé annuel.<br>• Le texte consulté ne fixe pas de durée par motif. | • Pool `PERMISSIONS_FAMILIALES` partagé par `EXCEPTIONNEL` et `PERMISSION`.<br>• Le motif décrit l'événement ; les dates déterminent la durée demandée. |
| **Loi 2024-014, Art. 134**<br>*(Loi 2003-044, Art. 126 & 134)* | **Congé de paternité autonome**<br>• **3 jours ouvrables rémunérés** à la naissance d'un enfant.<br>• Droit autonome, expressément **disjoint du pool de 10 jours**. | • [`RefTypeConge.java`](../../src/main/java/mg/galaxam/sirh/conge/persistence/RefTypeConge.java) : `id_pool_conge_annuel IS NULL` sur le code `PATERNITE`. `duree_fixe_jours = 3.0`. |
| **Loi 2024-014, Art. 143** | **Congé de maternité & prolongation médicale**<br>• **14 semaines consécutives (98 jours)**, dont au moins 8 semaines après l'accouchement.<br>• Prolongation médicale jusqu'à **4 semaines (28 jours)**, soit un maximum total de **126 jours**.<br>• Les droits à congé annuel continuent d'être acquis pendant cette période. | • Paramètres `DUREE_MAX_MATERNITE_STANDARD_JOURS = 98` et `DUREE_MAX_MATERNITE_PATHOLOGIQUE_JOURS = 126`.<br>• Flag `extension_pathologique_maternite` dans `demande_conge`. |
| **Loi 2003-044, Art. 13** | **Maladie ordinaire & Relais de prévoyance CNaPS**<br>• Suspension jusqu'à 6 mois.<br>• Maintien légal de salaire limité au préavis (**30 jours** pour mensuels). | • `RefTypeConge` : type `MALADIE` borné à 30 jours continus payés.<br>• Bascule automatique dès le 31e jour vers `MALADIE_SANS_SOLDE` (`id_type_conge_bascule = 1`) pour prise en charge CNaPS. |
| **Loi 2024-014, Art. 126**<br>*(Loi 2003-044, Art. 86)* | **Bonifications pour ancienneté**<br>• Majoration de la durée des congés payés selon convention ou règlement d'entreprise. | • Moteur pur [`CongeCalculateurEngine.java`](../../src/main/java/mg/galaxam/sirh/conge/domain/CongeCalculateurEngine.java) : seuil d'activation $\ge 5$ ans révolus, bonus $+1$ jour par tranche complète de 5 ans d'ancienneté (formule : $\lfloor \text{Ancienneté} / 5 \rfloor \times 1\text{ jour}$). Écriture `BONIFICATION` dans le ledger. |
| **Loi 2024-014, Art. 131**<br>*(Loi 2003-044, Art. 90)* | **Indemnité de congé payé & Règle du salaire continué**<br>• Maintien au moins égal au salaire moyen des 12 derniers mois et non inférieur au salaire continué.<br>• *Point à arbitrer* : La double ligne en paie (`DEDUCTION_CONGE_BASE` au trentième actuel vs `INDEMNITE_CONGE_PAYE` au coût historique déstocké par lots FIFO) est décrite comme convention interne dans la documentation antérieure ; sa justification métier et sa conformité doivent être confirmées indépendamment. La feuille `OHATRA KAJY DROITS CONGES-given on sept26.xls` n'étant pas vérifiée, elle n'en constitue pas une preuve. | • Service [`CongeTauxApplicableService.java`](../../src/main/java/mg/galaxam/sirh/conge/application/CongeTauxApplicableService.java) : valorisation mensuelle $V_m$ et résolution temporelle de `TCONGE`.<br>• [`CongeLotValorisationEngine.java`](../../src/main/java/mg/galaxam/sirh/conge/domain/CongeLotValorisationEngine.java) : allocation FIFO des lots historiques. |

---

## 2. Modèle de calcul décrit dans le code et référence d'entreprise non vérifiée

Le code implémente des règles d'acquisition, de valorisation et de constitution de lots FIFO décrites dans cette section. La feuille d'entreprise `OHATRA KAJY DROITS CONGES-given on sept26.xls` n'a pas été vérifiée et ne peut pas être présentée comme fondement certifié ni comme référence indépendante de calcul. Les écarts entre les règles implémentées et cette feuille restent à examiner lorsque la source sera disponible et validée.

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
│ • Prise de congé annuel bloquée (< 12 m)  │ • Photographie arrêtée et traçable à T₀    │
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
   - Seules les absences rémunérées non assujetties à cette carence (permissions exceptionnelles pour événements familiaux de l'Article 126(4) plafonnées à 10 j/an, congé de maternité de l'Article 143, congé de paternité de l'Article 134) sont immédiatement autorisées.
3. **Acquisition et Valorisation Mensuelle en Paie** :
   - À chaque clôture mensuelle de la paie, le moteur enregistre dans `conge_journal_droit` le droit acquis $D_m$ et la valorisation provisionnée $V_m = \frac{B_{totale} \times D_m}{30}$.
   - Une écriture de crédit `ACQUISITION` est enregistrée dans le grand-livre `mouvement_solde_conge`.
4. **Clôture annuelle au 31 décembre & Naissance du premier TCONGE** :
   - À l'issue des 12 mois (ou du prorata de la première année), la période de référence passe au statut `CLOTUREE`.
   - Le système calcule le premier taux journalier officiel du salarié : $\mathbf{TCONGE} = \frac{\sum V_m}{\sum D_m}$.
   - Ce taux est inséré dans `conge_taux_applicable` avec `source_provenance = 'CALCUL_PERIODE_REFERENCE'` pour régir les prises du cycle $N+1$.

#### B. Flux 2 : Reprise Historique à Date d'Arrêté (Cutover DAF)
1. **Principe directeur d'ingénierie** : Éviter l'import lourd, fragile et chronophage de plusieurs années de bulletins de paie et de badgeages quotidiens. Le système enregistre une **photographie de reprise à la date d'arrêté $T_0$**, avec ses données de traçabilité.
2. **Saisie sécurisée par la DAF (`InitialisationSoldeModal.jsx` / `POST /conges/solde/cutover-opening`)** :
   - **Date d'arrêté ($T_0$)** : Date pivot de bascule (`date_cutover`, ex: `2026-07-25`). Le backend rejette formellement toute date système implicite.
   - **Solde de congés restant arrêté** : Volume de jours acquis non pris arrêté à $T_0$ (`solde_jours`, ex: `14.50` jours).
   - **Valorisation du solde d'ouverture** : Montant financier total de la réserve arrêtée (`valeur_solde_ouverture`, ex: `157 013,11 Ar`) et calcul du taux journalier correspondant ($TCONGE = \frac{\text{Valeur}}{\text{Jours}}$).
   - **Cumuls intermédiaires d'exercice (Crucial pour la pérennité du calcul)** :
     - `cumul_droits_ouverture` : Total des droits acquis du 1er janvier de l'année en cours jusqu'à $T_0$.
     - `cumul_valorisation_ouverture` : Total de la valorisation acquise du 1er janvier jusqu'à $T_0$.
     - *Justification d'architecture* : Ces deux cumuls garantissent que lors de la clôture annuelle au 31 décembre, le moteur calculera le nouveau $\mathbf{TCONGE}_{N+1}$ sur l'assiette complète des 12 mois de l'année civile (période pré-cutover issue de l'arrêté + mois post-cutover calculés dans GALAXAM SIRH), évitant toute sous-évaluation ou rupture de charge financière.
   - **Exemple de reprise pour la paie d'août 2026** : arrêter les soldes au **25/07/2026**, puis traiter la paie du **26/07 au 25/08/2026**. La période de référence TCONGE reste **01/01 au 31/12/2026** ; le cycle 26–25 est la période de paie, pas la période annuelle de référence. Les cumuls d'ouverture couvrent les droits et valorisations du 01/01 au 25/07 inclus ; la clôture de la paie d'août enregistre ensuite l'acquisition postérieure au cutover.
   - **Référence formelle de l'arrêté** : Référence administrative obligatoire de l'acte RH/DAF (`reference_arrete`, ex: `ARR-2026-07-025`).
3. **Inscription dans le Grand-Livre (`mouvement_solde_conge`)** :
   - Le stock initial est comptabilisé sous `type_mouvement = 'AJUSTEMENT'` avec le commentaire normalisé `[SOLDE_INITIAL] Reprise historique arrêté au YYYY-MM-DD...`.
4. **Priorité FIFO Absolue de Consommation & Liquidation Anti-Arrondi** :
   - Le moteur pur [`PurgeFifoEngine.java`](../../src/main/java/mg/galaxam/sirh/conge/domain/PurgeFifoEngine.java) et le service [`CongeLotValorisationEngine.java`](../../src/main/java/mg/galaxam/sirh/conge/domain/CongeLotValorisationEngine.java) affectent la **Priorité 1** au stock initial `[SOLDE_INITIAL]`. Toute prise de congé ultérieure déstocke en priorité ce reliquat historique avant d'imputer les acquisitions mensuelles récentes.
   - **Clause de liquidation FIFO anti-arrondi** : Lorsqu'une imputation de congé épuise intégralement les jours restants d'un lot (`quantite == lot.joursRestants`), la valorisation imputée est égale à la `valeur_restante` exacte en Ariary du lot (et non recalculée par multiplication unitaire), garantissant l'absence de centime orphelin ou d'écart résiduel de clôture.
5. **Audit des Rectifications DAF (`conge_cutover_correction`)** :
   - Si une contestation de salarié ou une erreur de report de l'ancien SIRH est constatée ultérieurement, la DAF effectue une correction via `POST /conges/solde/cutover/{idEmploye}/corriger`.
   - La table `conge_cutover_correction` trace exhaustivement l'ancien reliquat, le nouveau reliquat, l'ancien taux, le nouveau taux, la régularisation financière à examiner, le motif obligatoire et l'auteur de la correction.
   - **Zéro falsification rétroactive** : Les bulletins de paie passés et clôturés restent immuables ; seul un mouvement d'ajustement comptable compensatoire est injecté dans le Grand-Livre.

---

## 3. Architecture du Modèle de Données PostgreSQL

Le schéma canonique `04_schema_conges.sql` définit **12 tables Congés et 3 vues analytiques**. Des tables d'autres domaines, notamment `employe`, `utilisateur` et `periode_paie`, sont référencées mais ne font pas partie de ce décompte. Le diagramme ci-dessous présente les relations principales ; le dictionnaire donne les 12 tables et leurs contraintes canoniques. Les contrôles applicatifs et SQL ne constituent pas une certification indépendante :

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
 │ (Plafond 10j/an)     ││ (motifs descriptifs)         │
 └──────────────────────┘└──────────────────────────────┘
```

### 3.1 Dictionnaire des 12 Tables Relationnelles Dédiées

Le schéma canonique [`04_schema_conges.sql`](../../conception/bdd/04_schema_conges.sql) définit **12 tables** pour ce module. Les tables `employe`, `utilisateur` et `periode_paie` auxquelles certaines font référence appartiennent à d'autres schémas et ne sont pas comptées ici.

#### 1. `ref_pool_conge_annuel`
Référentiel des quotas partagés : `id_pool` (PK), `code` (unique), `libelle`, `plafond_jours_an`.

#### 2. `ref_type_conge`
Règles de type : `id_type_conge` (PK), `code` (unique), `libelle`, `est_paye`, `decompte_solde_acquis`, ancienneté minimale, durée fixe, plafond par épisode, type de bascule, pool, indicateurs de préavis, suspension d'acquisition, justificatif et activation. La rémunération par demande peut surcharger `est_paye` uniquement par l'intermédiaire applicatif autorisé ; voir RG-CON-06A.

#### 3. `ref_motif_evenement_familial`
Référentiel des motifs : `id_motif` (PK), `code` (unique), `libelle`, `nb_jours_autorises` nullable (colonne historique), `justificatif_requis` et `est_actif`.

#### 4. `ref_statut_demande_conge`
Référentiel des statuts : `id_statut_demande_conge` (PK), `code` (unique), `libelle`. Le schéma ne fixe pas une liste fermée par contrainte SQL ; les codes actuellement utilisés comprennent `BROUILLON`, `EN_ATTENTE`, `VALIDE`, `REFUSE` et `ANNULE`.

#### 5. `demande_conge`
Demande et décision : identifiants de demande, salarié, type, statut et motif ; dates, `nb_jours_decomptes`, dérogations, extension maternité, demi-journée et créneau, `est_remunere` nullable, justificatif, commentaire, auteurs et horodatages.
Contraintes canoniques : dates ordonnées, cohérence demi-journée/créneau et unicité `(id_demande_conge,id_employe)` pour les références composites. `est_remunere = NULL` signifie qu'aucune surcharge de la valeur du type n'est enregistrée ; l'API résout alors le défaut `ref_type_conge.est_paye`.

#### 6. `mouvement_solde_conge`
Grand-livre des acquisitions, bonifications, prises, ajustements, annulations et expirations : `id_mouvement` (PK), salarié, type, jours signés, demande nullable, date, commentaire, valeur source, auteur et date de création. Le schéma impose les types et les signes de certains mouvements, les références demande/salarié et des index uniques partiels pour `PRISE` et `ANNULATION` par demande, ainsi qu'une unicité salarié/année pour `BONIFICATION`.
Ces contraintes assurent l'intégrité définie par SQL, **pas l'immutabilité** : le schéma ne comporte pas de déclencheur interdisant UPDATE ou DELETE. Les parcours applicatifs usuels ajoutent des écritures de correction ; le modèle JDBC expose également des opérations de modification/suppression et certaines corrections applicatives mettent à jour `valeur_source`. Il faut donc parler de journalisation et de traçabilité, non d'un registre techniquement immuable.

#### 7. `conge_periode_reference`
Périodes et cumuls de référence : salarié, bornes, statut, mode de reprise, date de cutover, cumuls d'ouverture/clôture, taux calculé et métadonnées. SQL impose des bornes ordonnées, les valeurs autorisées des statuts/modes, l'unicité composite salarié/période et l'absence de chevauchement de périodes pour un salarié par exclusion GiST.

#### 8. `conge_journal_droit`
Journal mensuel des droits et de leur valorisation : salarié, période de paie nullable, période de référence nullable, année/mois, salaire de base, jours de formule et acquis, base et montant de valorisation, cumuls et taux journalier. SQL impose l'unicité `(id_employe, annee, mois)` et des clés de référence composites.

#### 9. `conge_taux_applicable`
Historique daté des taux journaliers : salarié, taux, dates d'effet et de fin, provenance, période de référence nullable, commentaire, auteur et création. SQL impose un taux positif, des bornes cohérentes, des provenances autorisées, les références composites et l'absence de chevauchement des intervalles `[date_effet,date_fin[` par salarié.

#### 10. `conge_cutover_correction`
Trace les corrections DAF du stock initial : ancien/nouveau reliquat en jours et valeur, anciens/nouveaux taux, régularisation à examiner, motif, auteur, date, et références aux mouvements de reprise et d'ajustement. Le schéma impose des reliquats corrigés non négatifs et les clés étrangères associées.

#### 11. `conge_valorisation_mensuelle_correction`
Trace une rectification de valorisation : références au journal mensuel et au mouvement d'acquisition du salarié, ancienne/nouvelle valeur, motif, auteur et date. SQL exige les références composites, un motif non vide et une nouvelle valeur non négative.

#### 12. `conge_expiration_examen`
Trace les décisions `REPORTE` ou `EXPIRE`, jours examinés, motif, vérification de planification, prochaine revue, mouvement d'expiration éventuel, décideur et date. La contrainte `chk_conge_expiration_decision` lie les champs requis à chaque décision et la référence composite lie le mouvement au salarié.

Les tables Paie et les vues présentées dans les sections suivantes ne sont pas incluses dans ce décompte des 12 tables Congés.

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
  Le décompte n'est pas un calcul figé. Il est orchestré par [`CongeDecompteCalendaireResolver.java`](../../src/main/java/mg/galaxam/sirh/conge/application/CongeDecompteCalendaireResolver.java). Pour chaque jour $j \in [\text{date\_debut}, \text{date\_fin}]$ :
  1. Résolution temporelle à la date $j$ du paramètre global `CONGE_DECOMPTE_FERIES` (`INCLUS` ou `EXCLUS`).
  2. Sous `EXCLUS`, un férié national ou local au site de l'employé (`site_calendrier_ferie`) est ignoré du décompte. Sous `INCLUS`, il est débité ; c'est le réglage actuel.
  *Source légale* : Loi n° 2024-014, Article 127 (*« Sont exclus du décompte des congés payés les jours fériés chômés et payés... »*).
- **Demi-journée** : Si `est_demi_journee = true`, alors $\text{date\_debut} = \text{date\_fin}$, $\text{nb\_jours\_decomptes} = 0{,}5$, et le créneau (`MATIN` ou `APRES_MIDI`) est obligatoire.

### RG-CON-02 : Acquisition Mensuelle Légale & Idempotence
- **Droit normal** : **2,50 jours calendaires** par mois de service effectif (Article 125 du Code du Travail 2024).
- **Proratisation légale au trentième** : En cas d'absences injustifiées ou non rémunérées dans le mois :
  $$F = 30 - \max(0, J_{abs} - J_{conge}), \quad D_m = 2{,}5 \times \frac{F}{30}$$
- **Déclenchement** : `PayrollService.cloturerDonneesPaie` alimente le journal via `PayrollLeaveJournalService.mettreAJourJournalConge` dans la transaction de clôture. Les bulletins STC sont ignorés. Le service réutilise les jours d'absence et jours travaillés du bulletin, calcule le droit et la valorisation, puis inscrit le journal et le crédit `ACQUISITION` ; une valeur mensuelle déjà figée ne peut pas être recalculée silencieusement. Aucun scheduler Congés n'accorde mensuellement un droit anticipé hors de ce parcours de clôture.

### RG-CON-03 : Bonification pour Ancienneté
- Calculée à la date anniversaire par palier de 5 ans d'ancienneté révolus (+1 jour ouvrable par tranche complète de 5 ans).
  - Seuil d'activation dans le moteur : $\ge 5\text{ ans}$ (formule : $\lfloor \text{Ancienneté} / 5 \rfloor \times 1\text{ jour}$).
  - Tracé dans le ledger sous le type de mouvement `BONIFICATION`.
  *Source légale* : Loi n° 2024-014, Article 126 / Loi n° 2003-044, Article 86.

### RG-CON-04 : Examen DAF des Droits Candidats à Prescription
- Conforme à l'Article 129 de la Loi n° 2024-014 : le délai de prescription de 3 ans (paramétré par `DUREE_PRESCRIPTION_SOLDE_CONGE_MOIS = 36`) est un seuil de revue.
- Le moteur pur [`PurgeFifoEngine.java`](../../src/main/java/mg/galaxam/sirh/conge/domain/PurgeFifoEngine.java) impute les prises historiques en FIFO et isole les droits acquis antérieurs à la date limite.
- **Aucune purge automatique** : La DAF pilote l'examen via [`CongeExpirationExamenService.java`](../../src/main/java/mg/galaxam/sirh/conge/application/CongeExpirationExamenService.java) :
  - **`REPORTE`** : maintien au solde avec date de prochaine revue obligatoire.
  - **`EXPIRE`** : validation obligatoire de la coche `planification_verifiee = true`, motif textuel obligatoire et génération d'un débit `EXPIRATION` dans le grand-livre.

### RG-CON-05 : Interdiction Absolue de l'Anticipation en Solde Négatif
- **Le code bloque systématiquement tout solde négatif** :
  Dans [`DemandeConge.java`](../../src/main/java/mg/galaxam/sirh/conge/persistence/DemandeConge.java#L153-L157) et [`CongeService.java`](../../src/main/java/mg/galaxam/sirh/conge/application/CongeService.java#L938-L940) :
  ```java
  if (nouveauSolde.compareTo(BigDecimal.ZERO) < 0) {
      throw new IllegalArgumentException("Solde de congé insuffisant... La prise par anticipation est désactivée.");
  }
  ```
- Dans [`DemandeCongeController.java`](../../src/main/java/mg/galaxam/sirh/conge/api/DemandeCongeController.java#L257-L259) :
  ```java
  if (derogationSoldeNegatif) {
      throw new IllegalArgumentException("La prise par anticipation avec solde négatif est désactivée.");
  }
  ```
- En base de données, le paramètre `PLAFOND_SOLDE_CONGE_NEGATIF` est configuré avec `est_actif = FALSE`. Le solde débiteur est formellement prohibé.
- Les ajustements manuels verrouillent l'employé et refusent un débit supérieur au solde disponible.

### RG-CON-06 : Délais de Préavis
Pour les types dont `soumis_delai_preavis = true`, le code mesure le préavis par `ChronoUnit.DAYS.between(dateDemande, dateDebut)` : les **jours écoulés sont calendaires**, sans exclusion des week-ends ni jours fériés. Le délai requis est choisi selon le nombre de jours décomptés : si celui-ci dépasse `SEUIL_JOURS_PREAVIS_LONG`, le code applique `DELAI_PREAVIS_LONG_JOURS`, sinon `DELAI_PREAVIS_COURT_JOURS`. Les valeurs initiales du schéma canonique sont : seuil 3 jours, délai court 2 jours, délai long 15 jours. Les paramètres actifs à la date de référence peuvent évoluer. Une `derogationDelaiPreavis` vraie désactive ce contrôle. Ne pas décrire le délai court comme un nombre de jours ouvrables.

### RG-CON-06A : Rémunération d'une demande
Le type de congé fournit la valeur par défaut via `ref_type_conge.est_paye`. `demande_conge.est_remunere` est nullable : sans surcharge, la vue de demande résout la valeur effective avec `COALESCE(d.est_remunere, tc.est_paye)`.

- Le salarié et l'Agent Pointage ne peuvent pas choisir ou modifier explicitement `est_remunere`. Leurs créations et régularisations sans surcharge conservent le défaut du type.
- Un validateur qui n'a pas `ROLE_ADMIN` peut valider une demande, mais ne peut pas joindre une valeur explicite pour en changer la rémunération.
- Seul `ROLE_ADMIN` peut choisir explicitement `true` ou `false`, ou rétablir le défaut du type lors d'une modification ou validation.
- À la création, une surcharge explicite non administrateur est refusée par le backend. Lors d'une modification, l'omission du champ conserve la valeur enregistrée ; l'autorisation ne dépend pas de l'interface React.

Le code de calcul de paie examiné sélectionne les congés validés en fonction de `decompte_solde_acquis` et ne lit pas `est_remunere` dans le parcours `PayrollCalculationContextAssembler` / `PayrollExecutionEngine`. Le champ et sa valeur effective sont stockés et exposés par la vue, mais l'effet de la surcharge sur le calcul du bulletin n'est pas démontré par ce chemin : ne pas en déduire une retenue ou indemnité automatique.

### RG-CON-07 : Pool Annuel des Événements Familiaux (10 jours/an)
- Quota légal partagé de **10,0 jours par année civile** pour `PERMISSION` et `EXCEPTIONNEL` (Article 126(4), Loi 2024-014).
- La vue `v_pool_conge_annuel_consommation` agrège `jours_consommes` (`VALIDE`) et `jours_en_attente` (`EN_ATTENTE`).
- **Prévention du double comptage** : Lors de la validation d'une demande existante en statut `EN_ATTENTE`, le volume de la demande courante est déduit du total engagé avant de ré-appliquer le contrôle, empêchant tout faux rejet.
- **Paternité autonome** : Le congé de paternité (3 jours, Article 134) est expressément **disjoint du pool des 10 jours** (`id_pool_conge_annuel IS NULL`).

### RG-CON-08 : Maternité & Extension Pathologique
- Durée standard : **14 semaines consécutives (98 jours)** (Article 143, Loi 2024-014).
- Prolongation médicale : jusqu'à **4 semaines (28 jours)** sur constat médical (`extension_pathologique_maternite = true`), portant le total maximal à **126 jours calendaires**.
- Non suspensif : la salariée acquiert l'intégralité de ses congés payés (2,5 j/mois) pendant toute la période de maternité.

### RG-CON-09 : Maladie Ordinaire & Relais CNaPS
- Maintien du salaire à la charge de l'employeur pendant les **30 premiers jours continus** (Article 13 Loi 2003-044, limité au préavis mensuel).
- Dès le 31e jour, la demande bascule automatiquement vers `MALADIE_SANS_SOLDE` (`id_type_conge_bascule = 1`) pour prise en charge directe par les indemnités journalières de la CNaPS.

### RG-CON-10 : Fractionnement Annuel Obligatoire
- L'article 130 prévoit une première tranche continue d'au moins **15 jours** dans les trois mois suivant l'ouverture du droit, sous réserve des nécessités de service. Le reliquat peut être fractionné et cumulé dans la limite légale.
- **Écart actuel** : `DUREE_TRANCHE_MIN_FRACTIONNEMENT_JOURS` est une constante, mais le parcours de demande et `NotificationRhScheduler` ne contrôlent ni cette première tranche ni son échéance. La règle requiert une conception dédiée de planification et de justification des nécessités de service.

### RG-CON-11 : Périodes de Paie Clôturées & Intangibilité
- La création et le déplacement d'une demande sont refusés si la période de pointage est validée, la paie validée ou clôturée, ou si le cumul de pointage de l'employé est gelé/consommé. Le contrôle utilise `CONGE_POINTAGE_VERROUILLE`. Une demande déjà validée contrôle aussi son intervalle initial ; l'annulation d'une demande contrôle également la paie clôturée et les bulletins validés.

### RG-CON-12 : Continuité de Service & Collisions de Poste/Site
- Orchestré par [`CongeContinuiteService.java`](../../src/main/java/mg/galaxam/sirh/conge/application/CongeContinuiteService.java) :
  1. Résout l'affectation active de l'employé pour chaque jour de l'intervalle demandé via `EmploymentContextResolver`.
  2. Vérifie que l'employé est sous contrat actif et en poste (`actifSurPeriode() = true`).
  3. Détecte les collisions avec d'autres collaborateurs occupant le même poste (`id_fonction`) sur le même site géographique (`id_site`).
  4. Si au moins une collision est détectée et que `derogationContinuiteService = false`, la demande est immédiatement bloquée avec le nom du collègue et l'intervalle de conflit.
  *Source légale* : Loi n° 2024-014, Article 128 (pouvoir d'organisation de l'employeur pour la continuité de service).

---

## 5. Workflow & Circuit de Validation Transactionnel

### 5.1 États et opérations réellement exposées

Toute création via `POST /conges/demandes` attribue directement le statut `EN_ATTENTE`. `BROUILLON` reste un code du référentiel et certains services savent traiter une demande déjà dans cet état, mais ce n'est pas une étape obligatoire du parcours courant. Le contrôleur n'expose aucune route de soumission ni de suppression d'une demande.

```
        POST /demandes
              │
              ▼
        EN_ATTENTE ── POST /{id}/valider ──► VALIDE
             │                                  │
             ├── POST /{id}/refuser ───────► REFUSE
             │                                  │
             └── POST /{id}/annuler ────────► ANNULE
                                                ▲
                         POST /{id}/annuler ────┘
```

L'annulation est une opération séparée autorisée par le contrôleur aux détenteurs de `CONGE_WRITE`, `CONGE_VALIDATE` ou `ROLE_ADMIN`. Pour une demande `VALIDE`, le service applique les contrôles existants et peut enregistrer le mouvement compensatoire `ANNULATION`.

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

Le backend calcule `availableActions` selon le statut, les permissions globales `CONGE_WRITE`/`CONGE_VALIDATE`, le verrouillage Pointage et l'état de la période de paie. Ces capacités d'interface ne constituent pas la liste exhaustive des routes HTTP et ne remplacent pas leurs contrôles d'accès :

| Statut | Capacités calculées (`availableActions`) | Opérations HTTP actuellement exposées |
| :--- | :--- | :--- |
| **`BROUILLON`** | `EDIT` selon `CONGE_WRITE`; `SUBMIT` peut être calculée pour `CONGE_WRITE`; validation n'est pas proposée par cette matrice. | Pas de route de soumission ou suppression. Le service de validation accepte néanmoins `BROUILLON` si cet état existe déjà. |
| **`EN_ATTENTE`** | `EDIT` selon `CONGE_WRITE`; `VALIDATE`/`APPROVE` et `REJECT` selon `CONGE_VALIDATE`; `CANCEL` selon `CONGE_WRITE` ou `CONGE_VALIDATE`, sous réserve des blocages. | `PUT /{id}`, `POST /{id}/valider`, `POST /{id}/refuser`, `POST /{id}/annuler`, avec contrôles d'autorisation et d'état côté serveur. |
| **`VALIDE`** | `MODIFY_DATES` pour un validateur si la demande est future et déverrouillée; `CANCEL` selon permission et contrôles. | `PUT /{id}` ne permet la modification d'une demande validée que pour RH/administrateur et sous conditions métier; l'annulation exige `CONGE_WRITE`, `CONGE_VALIDATE` ou `ROLE_ADMIN`. |
| **`REFUSE`**, **`ANNULE`** | Aucune action métier n'est indiquée par cette matrice. | Le service refuse leur annulation et la modification directe selon ses règles d'état. |

**Limite connue pour le propriétaire :** `PUT /{id}` autorise l'accès au propriétaire sans `CONGE_WRITE`, mais `enrichirActions` ne tient compte que des permissions globales pour calculer `EDIT`. Il peut donc renvoyer `EDIT = FORBIDDEN` alors que l'API permet au propriétaire de modifier sa demande. À l'inverse, une capacité `CANCEL` calculée ne dispense pas de la permission exigée par `@PreAuthorize`. Cette différence de présentation et de contrat reste un point technique à harmoniser.

---

## 6. Intégrations présentes dans le code : Pointage, Paie & STC

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
La commande Pointage `PUT /pointages/anomalies/{idPointage}/regulariser` accepte `POINTAGE_WRITE`, `POINTAGE_VALIDATE` ou `ROLE_ADMIN`. Le rôle `ROLE_AGENT_POINTAGE` reçoit `POINTAGE_WRITE` dans le schéma canonique ; aucun `CONGE_WRITE` n'est requis pour cet appel interne à `CongeService`. L'opération reste soumise aux contrôles de période, de chevauchement et de solde.

Pour l'option `CONGE_PAYE`, `CongeService.regulariserCongeAnnuelDepuisPointageStructure` tente de couvrir l'absence avec le solde annuel acquis, puis `PointageAnomalieService` rattache la demande créée au pointage. Les résultats diffèrent selon le solde :

1. **Couverture complète :** la demande annuelle est créée et validée pour toute la durée ; un mouvement `PRISE` est enregistré et le pointage reçoit le statut `CONGE` avec l'identifiant de demande.
2. **Couverture partielle d'une journée :** si le solde est supérieur à zéro mais inférieur à la durée demandée, la demande est créée et validée pour la fraction couverte. Le pointage reçoit `DEMI_JOURNEE` et conserve le lien vers cette demande ; le reste n'est pas débité comme congé. La fraction non couverte n'est pas transformée par ce code en ligne de paie dédiée ; son effet salarial n'est pas établi ici.
3. **Solde nul :** aucune demande ni mouvement `PRISE` n'est créé. Le pointage reste traité comme une absence : `ABSENCE_JUSTIFIEE` si un motif est renseigné, sinon `ABSENCE_NON_JUSTIFIEE`.

La couverture partielle est limitée à une seule journée. Pour une période de plusieurs jours, le service exige un solde couvrant toute la durée et rejette la régularisation sinon. Les contrôles des journées supplémentaires (période ouverte, absence de temps ou congé déjà lié) s'appliquent avant l'écriture. Les écritures Congés et Pointage sont effectuées dans la transaction JDBC de régularisation.

#### C. Permission exceptionnelle oubliée dans le pointage
L'option `PERMISSION_EXCEPTIONNELLE` appelle `CongeService.regulariserPermissionDepuisPointage` : le service recherche un type actif `PERMISSION`, ou à défaut `EXCEPTIONNEL`, refuse l'opération si aucun de ces types n'est actif ou si une demande couvre déjà la date, puis crée et valide une demande d'un jour avec le préfixe `[REGULARISATION_POINTAGE]`. La commande est exécutée sous les mêmes permissions Pointage (`POINTAGE_WRITE`, `POINTAGE_VALIDATE` ou `ROLE_ADMIN`) et passe par les contrôles Congés de création et validation, dont les contrôles de pool lorsqu'ils s'appliquent. Elle ne permet pas à l'Agent Pointage de définir explicitement `est_remunere` ; la valeur par défaut du type s'applique.

Une annulation ultérieure suit le workflow d'annulation de Congés et ses contrôles de période/pointage ; ne pas décrire le résultat comme un retour systématique à `ABSENCE_NON_JUSTIFIEE` sans distinguer le statut initial et le motif.

La matérialisation directe d'une journée de congé par la synthèse Pointage est également soumise à la protection SQL du schéma canonique (ancien identifiant historique **SP80**) : `date_pointage` doit appartenir à la période référencée, bornes incluses. Une demande de congé validée en amont ne crée pas à elle seule une ligne `pointage_jour`. Les anciennes migrations SP ont été volontairement supprimées ; le schéma canonique sert à initialiser une base neuve et ne décrit pas la mise à niveau d'une base existante.

### 6.2 Pont Réel Congés $\rightarrow$ Moteur de Paie

Lors du calcul du bulletin mensuel :
1. **Déduction de la base pour les jours de congé** : si `salaireParJour` contient les salaires datés de la campagne, le moteur exige aussi une ventilation des jours de congé par date, vérifie que les jours correspondent et calcule la somme `salaire applicable à la date × jours / 30`. Si cette ventilation salariale n'est pas fournie, il utilise le salaire de base contractuel et le taux journalier au trentième (`jours × salaire de base / 30`).
2. **Valorisation par lots** : dans le parcours de préparation du bulletin, `PayrollCalculationContextAssembler` demande à `CongeLotValorisationService` la valeur des prises sur la période. L'indemnité transmise au moteur correspond à cette valorisation historique des lots FIFO ; le calcul générique de repli du moteur utilise le TCONGE journalier s'il n'a pas reçu de montant par lots :
   $$\text{INDEMNITE\_CONGE\_PAYE} = \sum_{lot} (\text{Jours Pris}_{lot} \times \mathbf{TCONGE}_{lot})$$
3. **Limite liée à `est_remunere`** : le chemin de chargement des jours de congé retient les demandes `VALIDE` dont le type `decompte_solde_acquis` est vrai ; les méthodes de calcul examinées ne filtrent pas cette sélection par `demande_conge.est_remunere` ou `ref_type_conge.est_paye`. La portée de la surcharge `est_remunere` sur les montants du bulletin n'est donc pas démontrée par ces méthodes et reste à vérifier séparément. Les formules ci-dessus décrivent les branches techniques observées, pas une conclusion juridique sur leur traitement.
4. **Deux lignes distinctes** : le moteur produit `DEDUCTION_CONGE_BASE` et `INDEMNITE_CONGE_PAYE` ; les taux historiques FIFO peuvent différer du salaire courant au trentième.

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
| **02:30 chaque jour** | `NotificationRhScheduler` | Détection et dispatching des alertes RH de supervision effectivement câblées ; aucune alerte de première tranche de 15 jours n'est implémentée. |
| **1er du mois, 01:00** | `AcquisitionCongeScheduler` | Vérification des bonifications d'ancienneté du mois échu. Après une panne, les mois manqués sont repris depuis la première période inscrite dans `scheduler_execution`, au plus six par cycle de rattrapage. L'acquisition mensuelle standard est enregistrée par le service Paie dans la transaction de clôture de paie. |
| — | — | **Aucun cron nocturne ne purge automatiquement les congés prescrits.** L'examen est obligatoirement instruit par la DAF. |

Les exécutions planifiées sont inscrites par `SchedulerExecutionRegistry` avec
un jeton de possession et un bail renouvelable (30 minutes par défaut,
heartbeat toutes les 10 minutes). Après expiration, un autre worker peut
reprendre la tentative ; le jeton empêche l'ancien worker de prolonger le bail
ou de finaliser son statut. Il n'arrête pas le traitement métier de cet ancien
worker : les écritures doivent donc être atomiques/idempotentes séparément.
Pour les bonifications, l'insertion est protégée par une unicité PostgreSQL
salarié/année (`idx_uniq_mouvement_bonification_annee`) définie dans le schéma
canonique. La migration historique SP77 n'est pas présente dans le dépôt et son
exécution sur une base existante ne peut pas être confirmée ici. Les doublons
historiques, s'il en existe, nécessiteraient un arbitrage avant d'appliquer une
migration équivalente.

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
    ├── RefMotifEvenementFamilial.java        # Modèle JDBC des motifs descriptifs (pool Art. 126(4))
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
| **`DatesCongeSection`** | `components/formulaire/DatesCongeSection.jsx` | Déclencheur temps réel de `/conges/demandes/simuler` calculant le décompte selon la version datée de `CONGE_DECOMPTE_FERIES`. |
| **`ConflitsPostePreview`** | `components/formulaire/ConflitsPostePreview.jsx` | Alerte dynamique préventive affichant les collisions d'absence d'équipe par poste et site (`/verifier-conflits`). |
| **`DemandesValidationTab`** | `components/DemandesValidationTab.jsx` | Tableau de bord de validation N+1/RH piloté par `availableActions` (`VALIDER`, `REFUSER`, `ANNULER`). |
| **`ValidationModals`** | `components/validation/ValidationModals.jsx` | Modales de confirmation, de refus motivé et d'annulation avec motif textuel obligatoire. |
| **`DemandeDetailsModal`** | `components/DemandeDetailsModal.jsx` | Consultation détaillée d'une demande, audit trail des approbations et pièces justificatives. |
| **`SoldesOverviewTab`** | `components/SoldesOverviewTab.jsx` | Vue globale RH/Manager : synthèse des collaborateurs, filtres et accès direct aux actions individuelles. |
| **`SoldesTable`** | `components/soldes/SoldesTable.jsx` | Grille tabulaire des compteurs temps réel (`v_solde_conge_employe`) avec recherche et pagination. |
| **`SoldesGantt`** | `components/soldes/SoldesGantt.jsx` | Visualisation planning Gantt interactif des présences et absences de l'équipe. |
| **`SoldeCongeWidget`** | `components/SoldeCongeWidget.jsx` | Widget synthétique du collaborateur affichant les trois dimensions distinctes : solde disponible (jours), valeur financière restante du stock par lots FIFO (Ariary) et taux unitaire TCONGE applicable. |
| **`DroitsLotsPanel`** | `components/DroitsLotsPanel.jsx` | Panneau d'inspection détaillée des lots de droits acquis FIFO : origine (Cutover, Acquisition, Bonification, Ajustement), date d'effet, jours initiaux, jours consommés, reliquat et valorisation financière restante. |
| **`SimulationValorisationConge`** | `components/formulaire/SimulationValorisationConge.jsx` | Composant d'estimation prévisionnelle dans le formulaire de demande, affichant en temps réel la projection des imputations par lot FIFO sans écriture préalable dans le ledger. |
| **`PeriodesReferenceTab`** | `components/PeriodesReferenceTab.jsx` | Supervision DAF : consultation des périodes de 12 mois, journal mensuel des droits, clôture et activation de TCONGE. |
| **`CongeExpirationReviewCard`**| `components/CongeExpirationReviewCard.jsx` | Instruction DAF de l'examen triennal Art. 129 : formulaire de report (`REPORTE`) ou de confirmation d'expiration (`EXPIRE`). |
| **`MouvementsSoldeTable`** | `components/MouvementsSoldeTable.jsx` | Grand-livre comptable des mouvements du salarié (`mouvement_solde_conge`) avec traçabilité des imputations. |
| **`ParametresCongesTab`** | `components/ParametresCongesTab.jsx` | Administration des types de congés, motifs descriptifs, pools annuels (10 j/an) et statuts. |
| **`ParametresCongesModals`** | `components/ParametresCongesModals.jsx` | Modales de création et modification des référentiels d'absence avec plafonds et règles de bascule. |
| **`ExportEtatCongesModal`** | `components/ExportEtatCongesModal.jsx` | Téléchargement du bilan individuel sur 12 mois et du grand récapitulatif collectif Excel. |

### 8.3 Catalogue Complet des Endpoints REST

Les endpoints du module ne sont pas accessibles anonymement. `@PreAuthorize` exige une authentification ou une permission spécifique ; certaines opérations de demande complètent ce contrôle par la vérification côté serveur du dossier salarié associé au compte. La capacité React `availableActions` n'accorde aucun droit API.

#### 1. Cycle de Vie des Demandes (`/conges/demandes`)
| Méthode | URI | Contrôle d'Accès / Rôle | Description Métier & Contrat |
| :---: | :--- | :--- | :--- |
| `POST` | `/simuler` | `CONGE_READ`/`ROLE_ADMIN` ou dossier personnel associé | Simulation temps réel du décompte des jours et du quota annuel. |
| `POST` | `/simuler-valorisation` | `CONGE_READ`/`ROLE_ADMIN` ou dossier personnel associé | Estimation prévisionnelle de la valorisation de la prise par déstockage virtuel des lots FIFO (`CongeLotValorisationService.simulerPrise`), projection des soldes avant/après, valeur financière estimée en Ariary et inventaire des demandes concurrentes en attente. |
| `POST` | `/verifier-conflits` | `CONGE_READ`/`ROLE_ADMIN` ou dossier personnel associé | Détection des collisions d'absence sur le même poste/site (`CongeContinuiteService`). |
| `POST` | `/` | `CONGE_WRITE`/`ROLE_ADMIN` ou dossier personnel associé | Création d'une demande. L'identité salarié correspondante est résolue côté serveur. |
| `PUT` | `/{id}` | Authentifié ; `CONGE_WRITE` ou propriétaire pour demandes modifiables ; `CONGE_VALIDATE`/`ROLE_ADMIN` pour les cas RH autorisés | Mise à jour sous contrôles d'état et de période. Les demandes validées ne sont modifiables que par RH/administrateur, sous conditions métier. |
| `GET` | `/employe/{idEmploye}` | `CONGE_READ`/`ROLE_ADMIN` ou propriétaire | Historique d'un salarié, limité au dossier personnel sans permission globale. |
| `GET` | `/{id}` | `CONGE_READ`/`ROLE_ADMIN` ou propriétaire | Consultation unitaire d'une demande avec `availableActions`. |
| `GET` | `/me` | Authentifié avec e-mail salarié unique | Liste des demandes du compte courant. |
| `GET` | `/me/dossier` | Authentifié avec e-mail salarié unique | Résolution du dossier salarié utilisé pour créer sa propre demande. |
| `GET` | `/validation` | `CONGE_VALIDATE` ou `ROLE_ADMIN` | Liste des demandes en attente de validation pour le manager/RH connecté. |
| `GET` | `/all` | `CONGE_READ`/`ROLE_ADMIN` | Consultation globale de toutes les demandes avec filtres multicritères. |
| `POST` | `/{id}/valider` | `CONGE_VALIDATE` ou `ROLE_ADMIN` | Validation transactionnelle : génération du débit `PRISE` au Grand-Livre. |
| `POST` | `/{id}/refuser` | `CONGE_VALIDATE` ou `ROLE_ADMIN` | Refus motivé d'une demande avec notification. |
| `POST` | `/{id}/annuler` | `CONGE_WRITE`, `CONGE_VALIDATE` ou `ROLE_ADMIN` | Annulation sous contrôles métier ; restitution éventuelle du solde au Grand-Livre (`ANNULATION`) et mise à jour des pointages liés. |

#### 2. Soldes, Grand-Livre, Cutover & Expiration (`/conges`)
| Méthode | URI | Contrôle d'Accès / Rôle | Description Métier & Contrat |
| :---: | :--- | :--- | :--- |
| `GET` | `/solde/employe/{idEmploye}` | `CONGE_READ` ou `ROLE_ADMIN` | Compteurs temps réel issus de `v_solde_conge_employe`. |
| `GET` | `/solde/employe/{idEmploye}/situation` | `CONGE_READ` ou `ROLE_ADMIN` | Synthèse complète : solde, période active, TCONGE, expiration et pools. |
| `GET` | `/solde/valorisation-lots/{idEmploye}` | `CONGE_READ` ou `ROLE_ADMIN` | Détail des lots FIFO d'acquisition et valeurs restantes en Ariary. |
| `GET` | `/solde/imputation/{idEmploye}` | `CONGE_READ` ou `ROLE_ADMIN` | Diagnostic d'imputation financière des congés. |
| `GET` | `/mouvements/employe/{idEmploye}` | `CONGE_READ` ou `ROLE_ADMIN` | Grand-Livre des écritures comptables (`mouvement_solde_conge`). |
| `POST` | `/solde/ajustement` | `CONGE_VALIDATE` / `ROLE_ADMIN` | Ajustement manuel de solde motivé par la DAF. |
| `POST` | `/solde/cutover-opening` | `CONGE_VALIDATE` / `ROLE_ADMIN` | Reprise à date d'arrêté $T_0$ : injection stock initial et cumuls Janvier $\rightarrow T_0$. |
| `POST` | `/solde/cutover/{idEmploye}/corriger` | `CONGE_VALIDATE` / `ROLE_ADMIN` | Correction DAF auditée du stock initial de reprise (`conge_cutover_correction`). |
| `GET` | `/planning` | `CONGE_READ` ou `ROLE_ADMIN` | Planning global consolidé des congés pour affichage Gantt. |
| `GET` | `/expiration/employe/{idEmploye}` | `CONGE_READ` ou `ROLE_ADMIN` | Examen des droits candidats à prescription triennale (Loi 2024-014, Art. 129). |
| `POST` | `/expiration/employe/{idEmploye}/reporter` | `CONGE_VALIDATE` / `ROLE_ADMIN` | Décision DAF de report d'expiration avec date de prochaine revue. |
| `POST` | `/expiration/employe/{idEmploye}/confirmer`| `CONGE_VALIDATE` / `ROLE_ADMIN` | Confirmation d'expiration avec coche de planification et débit `EXPIRATION`. |
| `GET` | `/pool-consommation/employe/{idEmploye}`| `CONGE_READ` ou `ROLE_ADMIN` | Consommation courante sur les pools partagés (ex: événements familiaux 10j). |

#### 3. Périodes de Référence, Clôtures & Exports (`/conges/periodes-reference`)
| Méthode | URI | Contrôle d'Accès / Rôle | Description Métier & Contrat |
| :---: | :--- | :--- | :--- |
| `POST` | `/` | `CONGE_WRITE` ou `ROLE_ADMIN` | Création d'une nouvelle période de référence ; contrôle métier des bornes et chevauchements. |
| `GET` | `/employe/{idEmploye}` | `CONGE_READ` | Historique des périodes de référence d'un collaborateur. |
| `GET` | `/employe/{idEmploye}/statut-configuration` | `CONGE_READ` | Évaluation de l'état de configuration et éligibilité à l'activation du TCONGE. |
| `GET` | `/en-attente-activation` | `CONGE_READ` ou `ROLE_ADMIN` | Périodes clôturées dont le taux TCONGE est calculé mais pas encore activé. |
| `GET` | `/{idPeriodeReference}/mois` | `CONGE_READ` | Journal mensuel des 12 mois de référence issu de `conge_journal_droit`. |
| `POST` | `/{idPeriodeReference}/cloturer` | `CONGE_WRITE` ou `ROLE_ADMIN` | Clôture formelle de la période et calcul du ratio officiel $\mathbf{TCONGE} = \frac{CV_{12}}{CD_{12}}$. |
| `POST` | `/{idPeriodeReference}/activer-taux` | `CONGE_VALIDATE` / `ROLE_ADMIN` | Activation opérationnelle du taux journalier dans `conge_taux_applicable`. |
| `GET` | `/{idPeriodeReference}/corrections-valorisation` | `CONGE_READ` | Audit des rectifications de valorisation de la période. |
| `POST` | `/{idPeriodeReference}/mois/{idJournal}/corriger-valorisation` | `CONGE_VALIDATE` / `ROLE_ADMIN` | Rectification DAF auditée de la valorisation d'un mois clos (`conge_valorisation_mensuelle_correction`). |
| `GET` | `/employe/{idEmploye}/export-excel` | `CONGE_READ` | Téléchargement du bilan annuel individuel 4 feuilles (`ETAT_CONGES_PAR_PERSONNE.xlsx`). |
| `GET` | `/{idPeriodeReference}/export-excel` | `CONGE_READ` | Bilan annuel individuel ciblé sur une période de référence spécifique. |
| `GET` | `/grand-recap/export-excel` | `CONGE_READ` | Grand récapitulatif collectif DAF (`GRAND_RECAP_CONGES_VALORISES.xlsx`). |

#### 4. Référentiels & Paramétrage Légal (`/conges/referentiels`)
| Méthode | URI | Contrôle d'Accès / Rôle | Description Métier & Contrat |
| :---: | :--- | :--- | :--- |
| `GET` | `/all` | Authentifié | Référentiel global complet (types, motifs, pools, statuts). |
| `GET` | `/types`, `/motifs`, `/pools`, `/statuts` | Authentifié | Listes unitaires des référentiels de congés. |
| `POST` | `/types`, `/motifs` | `ROLE_ADMIN` | Création d'un nouveau type de congé ou motif légal. |
| `PUT` | `/types/{id}` | `ROLE_ADMIN` | Modification du type. Tant qu'aucune demande ne le référence, ses règles sont modifiables. Dès qu'une demande le référence, code et règles de calcul sont verrouillés ; seul le libellé reste modifiable par cette route. Désactiver le type et en créer un nouveau pour changer ses règles. |
| `PUT` | `/motifs/{id}` | `ROLE_ADMIN` | Modification du code, du libellé et de l'exigence de justificatif d'un motif familial. |
| `PUT` | `/types/{id}/activer`, `/desactiver` | `ROLE_ADMIN` | Activation ou désactivation logique d'un type de congé. |
| `PUT` | `/motifs/{id}/activer`, `/desactiver` | `ROLE_ADMIN` | Activation ou désactivation logique d'un motif familial. |

---

## 9. Matrice des Codes d'Erreur & Réponses API Normalisées

| Code d'Erreur Backend | Cause Métier Déclenchée | Réaction Frontend |
| :--- | :--- | :--- |
| `LEAVE_BALANCE_INSUFFICIENT` | Solde disponible insuffisant pour couvrir la demande. Solde négatif strictement interdit. | Bloque la soumission avec affichage du solde disponible. |
| `LEAVE_NOTICE_NOT_RESPECTED` | Délai de préavis réglementaire non respecté (2j ou 15j) sans dérogation. | Affiche l'avertissement bloquant ou invite à demander dérogation préavis. |
| `LEAVE_POOL_EXCEEDED` | Plafond annuel partagé de 10 jours dépassé pour les événements familiaux. | Bloque la saisie et signale le dépassement du quota Art. 126(4). |
| `LEAVE_OVERLAP` | Chevauchement de dates avec une autre demande active du même salarié. | Affiche le conflit exact de dates avec la demande concurrente. |
| `LEAVE_SERVICE_CONTINUITY_CONFLICT`| Conflit de continuité de service : un collègue du même poste et site est déjà absent. | Bloque la validation sauf si `derogationContinuiteService = true`. |
| `LEAVE_PAYROLL_PERIOD_CLOSED`| La période de paie couvrant la date de congé est déjà clôturée. | Interdit formellement toute modification ou annulation rétroactive. |
| `CYCLE_ACCUMULATORS_UNKNOWN`| Cumuls d'ouverture (droits/valo) manquants pour la clôture cutover. | Bloque la clôture automatique et requiert la régularisation DAF des cumuls. |
| `LEAVE_DEMI_JOURNEE_INVALID` | Incohérence sur la demi-journée (`dateDebut != dateFin` ou moment absent). | Force la sélection du créneau `MATIN` ou `APRES_MIDI`. |
| `LEAVE_STATUT_INVALID` | Transition d'état illégale (ex: ré-annuler une demande déjà annulée). | Bloque l'action avec message explicatif d'idempotence. |

---

## 10. Éditions et exports Excel

### 10.1 Bilan Annuel Individuel (`ETAT_CONGES_PAR_PERSONNE.xlsx`)

Généré par [`CongeAnnuelExcelExportService.java`](../../src/main/java/mg/galaxam/sirh/conge/application/CongeAnnuelExcelExportService.java) :
- Structuré sur 4 feuilles :
  1. **Feuille 1 — « État congés salarié »** : En-tête collaborateur, grille des 12 mois de référence issue de `conge_journal_droit`, retenues et indemnités de paie, solde progressif et préservation intégrale des formules Excel (`=SUM(...)`) avec `workbook.setForceFormulaRecalculation(true)`.
  2. **Feuille 2 — « Mouvements »** : Audit trail chronologique exhaustif du grand-livre filtré sur la période de référence.
  3. **Feuille 3 — « README »** : Rappel du cadre réglementaire officiel.
  4. **Feuille 4 — « Origine des droits »** : Traçabilité des lots FIFO restants et des imputations passées.
- Endpoints REST :
  - `GET /conges/periodes-reference/employe/{idEmploye}/export-excel`
  - `GET /conges/periodes-reference/{idPeriodeReference}/export-excel`

### 10.2 Grand Récapitulatif Collectif DAF (`GRAND_RECAP_CONGES_VALORISES.xlsx`)

Généré par [`CongeGrandRecapExcelExportService.java`](../../src/main/java/mg/galaxam/sirh/conge/application/CongeGrandRecapExcelExportService.java) :
- Accessible via le bouton **« Grand récapitulatif Excel »** sous `/conges`.
- Endpoint : `GET /conges/periodes-reference/grand-recap/export-excel?dateDebut=YYYY-MM-DD&dateFin=YYYY-MM-DD`.
- Aucune limite artificielle de 100 lignes.
- Présente pour chaque collaborateur de l'entreprise : l'ouverture avant `dateDebut`, les mouvements de l'intervalle ventilés par type, le solde arrêté à `dateFin` et la valorisation financière exacte issue de la somme des valeurs restantes des lots historiques FIFO.

---

## 11. Exemple chiffré historique — provenance à confirmer (Matricule 61)

Les valeurs de cette section sont conservées sans réécriture à la demande de DOC-2. Le matricule, la feuille source `OHATRA KAJY DROITS CONGES-given on sept26.xls`, les données d'entrée et l'autorisation de réutilisation ne sont pas vérifiables à partir du dépôt. Ne pas présenter cet exemple comme donnée réelle confirmée, résultat de test reproductible ou validation de conformité avant arbitrage sur sa provenance et son anonymisation.

Exemple historique conservé tel quel, sans validation de concordance mathématique avec la feuille `OHATRA KAJY DROITS CONGES-given on sept26.xls`. La feuille n'a pas été vérifiée ; les chiffres ci-dessous ne constituent ni une référence certifiée ni une preuve de test reproductible. Leur provenance et leur validation restent à arbitrer.

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

#### Article 126(4) — Permissions exceptionnelles pour événements familiaux
Le Code prévoit un plafond global de **dix jours par an** pour ces permissions, sans fixer ici de durée individuelle par motif. Le système demande donc un motif descriptif et contrôle le cumul sur le pool annuel partagé.

#### Article 134 — Congé de paternité
Le conjoint légitime d'une femme qui accouche a droit à **trois jours de congé de paternité**, payés par l'employeur comme des jours de travail effectif. Le Code le distingue du plafond annuel des permissions familiales de l'article 126(4).

#### Article 143 — Congé de maternité
Le Code prévoit **quatorze semaines consécutives**, dont **huit semaines postérieures** à l'accouchement, avec une prolongation jusqu'à **quatre semaines** dans les cas médicaux prévus par le texte.

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

### 12.3 Revue de conception du 24 octobre 2026

**Corrections appliquées** : la création et le déplacement d'une demande contrôlent le verrouillage du pointage et de la paie ; un ajustement manuel ne peut plus rendre le solde négatif. Le paramètre `CONGE_DECOMPTE_FERIES` conserve le réglage actuel `INCLUS` et l'option datée `EXCLUS`.

**Durcissements complémentaires implémentés** : contraintes SQL de cohérence salarié entre demandes, mouvements et corrections ; exclusion des chevauchements de taux TCONGE par salarié ; quota annuel réparti sur les années civiles couvertes par la demande ; verrouillage du salarié lors des contrôles concurrents ; accès personnel limité aux demandes appartenant au compte associé. Le rattachement personnel s'appuie actuellement sur l'e-mail partagé entre le compte et le dossier salarié; l'absence ou l'ambiguïté bloque l'accès personnel. L'installation canonique passe sur PostgreSQL vierge et les tests PostgreSQL isolés ciblés sont passés le 24 octobre 2026.

**Points encore ouverts avant préproduction** :

1. **Référentiel des types de congé** : dès qu'une ligne de `demande_conge` référence un type, l'API verrouille son code et ses règles qui affectent le calcul (`est_paye`, `decompte_solde_acquis`, ancienneté, durées, plafonds, bascule, pool, préavis, suspension d'acquisition et justificatif). Le contrôle couvre également les demandes qui ne sont plus actives, afin de préserver leur interprétation historique. Le libellé reste modifiable ; activation et désactivation passent par leurs routes dédiées. Pour appliquer de nouvelles règles, désactiver l'ancien type et en créer un nouveau. Ce contrôle est appliqué dans une transaction côté API ; aucune table ni colonne supplémentaire n'est requise.
2. **Première tranche annuelle** : aucune planification/alerte ne suit les 15 jours continus à prendre dans les trois mois après l'ouverture du droit. La justification des nécessités de service et les exceptions doivent être définies avec la RH avant automatisation.
3. **Rattachement des comptes salariés** : l'accès personnel utilise l'e-mail partagé entre le compte et le dossier employé et échoue si le lien est absent ou ambigu. En base locale, un compte sur deux est associé sans ambiguïté. Il faut rattacher les comptes de salariés concernés avant leur ouverture en préproduction, puis faire la recette navigateur avec un compte associé.

*Documentation technique du module Congés & Absences — décrit l'implémentation et les preuves explicitement citées ; ne vaut pas certification juridique ni recette exhaustive.*
