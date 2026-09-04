# DICTIONNAIRE DES RÈGLES DE GESTION & GUIDE DE MODIFICATION DU CODE MÉTIER

# GALAXAM-SIRH — SYSTÈME D'INFORMATION RH & PRÉ-PAIE

> **Version du document :** 2.0  
> **Conformité :** Code du Travail Malagasy (Loi n° 2003-044), Code Général des Impôts (CGI), Régime CNaPS / OSTIE, Politiques internes GALAXAM LOGISTICS.  
> **Destinataires :** Développeurs Fullstack, Architectes Logiciels, Chefs de Projet RH & Mainteneurs.

---

## SOMMAIRE GÉNÉRAL

- [1. Architecture de la Logique Métier & Philosophie](#1-architecture-de-la-logique-métier--philosophie)
- [2. Index Général des Règles de Gestion (RG)](#2-index-général-des-règles-de-gestion-rg)
- [3. Cartographie Détaillée des Règles par Domaine](#3-cartographie-détaillée-des-règles-par-domaine)
  - [3.1 Domaine Salariés & Dossier RH (RG-EMP)](#31-domaine-salariés--dossier-rh-rg-emp)
  - [3.2 Domaine Contrats & Supervision des Carrières (RG-CTR)](#32-domaine-contrats--supervision-des-carrières-rg-ctr)
  - [3.3 Domaine Pointage, Présences & Indemnités (RG-PTG)](#33-domaine-pointage-présences--indemnités-rg-ptg)
  - [3.4 Domaine Congés, Absences & Ledger FIFO (RG-CNG)](#34-domaine-congés-absences--ledger-fifo-rg-cng)
  - [3.5 Domaine Configuration Paie, Cotisations & IRSA (RG-PAIE)](#35-domaine-configuration-paie-cotisations--irsa-rg-paie)
  - [3.6 Domaine Sécurité, IAM, Permissions & GED (RG-SEC)](#36-domaine-sécurité-iam-permissions--ged-rg-sec)
- [4. Matrice Opérationnelle : "Je veux modifier une règle, que changer, où et comment ?"](#4-matrice-opérationnelle--je-veux-modifier-une-règle-que-changer-où-et-comment-)
  - [Scénario 1 : Modifier l'âge minimum légal d'accès à l'emploi](#scénario-1--modifier-lâge-minimum-légal-daccès-à-lemploi)
  - [Scénario 2 : Modifier la structure du matricule collaborateur](#scénario-2--modifier-la-structure-du-matricule-collaborateur)
  - [Scénario 3 : Modifier le plafond de durée des CDD ou des périodes d'essai](#scénario-3--modifier-le-plafond-de-durée-des-cdd-ou-des-périodes-dessai)
  - [Scénario 4 : Modifier la formule de calcul de l'ancienneté](#scénario-4--modifier-la-formule-de-calcul-de-lancienneté)
  - [Scénario 5 : Modifier la tolérance de retard au pointage](#scénario-5--modifier-la-tolérance-de-retard-au-pointage)
  - [Scénario 6 : Modifier le seuil de déclenchement des Heures Supplémentaires (Base 40h)](#scénario-6--modifier-le-seuil-de-déclenchement-des-heures-supplémentaires-base-40h)
  - [Scénario 7 : Modifier la plage horaire du travail de nuit](#scénario-7--modifier-la-plage-horaire-du-travail-de-nuit)
  - [Scénario 8 : Modifier les montants et seuils d'indemnité repas et déplacement](#scénario-8--modifier-les-montants-et-seuils-dindemnité-repas-et-déplacement)
  - [Scénario 9 : Modifier le pré-remplissage et l'éligibilité de la grille de pointage](#scénario-9--modifier-le-pré-remplissage-et-léligibilité-de-la-grille-de-pointage)
  - [Scénario 10 : Modifier le taux d'acquisition mensuel des congés payés (2,5 j/mois)](#scénario-10--modifier-le-taux-dacquisition-mensuel-des-congés-payés-25-jmois)
  - [Scénario 11 : Modifier la règle de scission arrêt maladie > 30 jours (Article 13)](#scénario-11--modifier-la-règle-de-scission-arrêt-maladie--30-jours-article-13)
  - [Scénario 12 : Modifier la prescription triennale des congés (Purge FIFO 36 mois)](#scénario-12--modifier-la-prescription-triennale-des-congés-purge-fifo-36-mois)
  - [Scénario 13 : Mettre à jour le SMIG et le plafond des cotisations CNaPS](#scénario-13--mettre-à-jour-le-smig-et-le-plafond-des-cotisations-cnaps)
  - [Scénario 14 : Mettre à jour les tranches de l'IRSA ou le minimum de perception](#scénario-14--mettre-à-jour-les-tranches-de-lirsa-ou-le-minimum-de-perception)
  - [Scénario 15 : Modifier la déduction fiscale par enfant à charge](#scénario-15--modifier-la-déduction-fiscale-par-enfant-à-charge)
  - [Scénario 16 : Modifier les types de pièces jointes autorisées en GED](#scénario-16--modifier-les-types-de-pièces-jointes-autorisées-en-ged)
  - [Scénario 17 : Ajouter un nouveau rôle ou permission applicative](#scénario-17--ajouter-un-nouveau-rôle-ou-permission-applicative)
- [5. Checklist Qualité lors d'une Modification Métier](#5-checklist-qualité-lors-dune-modification-métier)

---

# 1. Architecture de la Logique Métier & Philosophie

Dans GALAXAM-SIRH, une **séparation absolue des responsabilités** régit le code source :

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│ 1. PRÉSENTATION FRONTEND (React / Vite)                                    │
│    Formulaires, masques de saisie, contrôles visuels de complétude, UX.    │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ HTTP / REST (JSON)
┌──────────────────────────────────────▼──────────────────────────────────────┐
│ 2. CONTRÔLEUR BACKEND (Spring Boot REST)                                    │
│    Validation syntaxique (@Valid, @NotNull), Sécurité RBAC (@PreAuthorize). │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ DTO
┌──────────────────────────────────────▼──────────────────────────────────────┐
│ 3. SERVICE BACKEND (Spring Service)                                         │
│    Orchestration transactionnelle (c.commit()/c.rollback()), Journalisation.│
└──────────────────┬───────────────────────────────────────┬──────────────────┘
                   │ Données brutes                        │ Entités
┌──────────────────▼──────────────────┐ ┌──────────────────▼──────────────────┐
│ 4. MOTEURS DE DOMAINE (*Engine)     │ │ 5. PERSISTANCE (ClassMere ActiveRec) │
│    100% logique métier Java PURE.   │ │    Requêtes SQL, Transactions JDBC,  │
│    Stateless, déterministe, testé.  │ │    Vues relationnelles PostgreSQL.   │
└─────────────────────────────────────┘ └──────────────────────────────────────┘
```

### Règle d'or pour tout développeur :
1. **Le SQL ne prend AUCUNE décision métier.** Le SQL sert uniquement à filtrer, joindre et stocker les données.
2. **Tous les calculs, seuils, barèmes et validations RH résident dans le package `engine/` (ou les Services métiers dédiés).**
3. **Chaque moteur de calcul possède un test unitaire dédié (`*EngineTest.java`) garantissant la non-régression.**

---

# 2. Index Général des Règles de Gestion (RG)

| Code RG | Intitulé Court | Moteur Java / Classe Pivot |
| :--- | :--- | :--- |
| **RG-EMP-01** | Âge minimum légal d'accès à l'emploi (16 ans) | `EmployeValidationEngine.validerAgeLegal` |
| **RG-EMP-02** | Format et unicité du numéro CIN (12 chiffres) | `EmployeValidationEngine.validerCinFormatEtDate` |
| **RG-EMP-03** | Format de numéro de téléphone international/local | `EmployeValidationEngine.validerEmploye` |
| **RG-EMP-04** | Génération de matricule séquentiel vs préfixé | `MatriculeGeneratorEngine.genererMatriculeSequentiel` |
| **RG-EMP-05** | Complétude obligatoire compte bancaire et RIB | `EmployeValidationEngine.validerCompteBancaire` |
| **RG-EMP-06** | Motif obligatoire lors de la sortie/désactivation | `EmployeValidationEngine.validerDesactivation` |
| **RG-CTR-01** | Date de fin obligatoire pour les contrats précaires | `ContratValidationEngine.validerDatesContrat` |
| **RG-CTR-02** | Plafond de période d'essai initiale (max 6 mois) | `ContratValidationEngine.validerDatesContrat` |
| **RG-CTR-03** | Calcul prévisionnel d'échéance de période d'essai | `ContratValidationEngine.calculerFinPeriodeEssai` |
| **RG-CTR-04** | Événements de carrière nécessitant un contrat actif | `ContratValidationEngine.validerTransitionCarriere` |
| **RG-CTR-05** | Motif obligatoire pour toute rupture contractuelle | `ContratValidationEngine.validerTransitionCarriere` |
| **RG-CTR-06** | Machine à états finis des statuts de contrat | `ContratValidationEngine.estTransitionStatutAutorisee` |
| **RG-CTR-07** | Chaîne d'ancienneté continue et reconstitution | `AncienneteEngine.evaluerAnciennete` |
| **RG-PTG-01** | Détection et calcul strict des retards (tolérance) | `PointageCalculateurEngine.calculerMinutesRetard` |
| **RG-PTG-02** | Détection et calcul du travail de nuit (22h-05h) | `PointageCalculateurEngine.calculerHeuresNuit` |
| **RG-PTG-03** | Indemnité repas de midi et sorties tardives | `PointageCalculateurEngine.calculerTotalRepasJour` |
| **RG-PTG-04** | Indemnité transport/déplacement et déduction repas | `PointageCalculateurEngine.calculerDeplacementJour` |
| **RG-PTG-05** | Seuil hebdomadaire 40h et compensation 1:1 retard/HS | `PointageCalculateurEngine.calculerTroncon` |
| **RG-PTG-06** | Ventilation des tranches HS (+30% / +50%) | `PointageCalculateurEngine.calculerTroncon` |
| **RG-PTG-07** | Forfait Cadres : non-paiement des HS, compensation | `PointageCalculateurEngine.calculerTroncon` |
| **RG-PTG-08** | Détection des jours fériés et chômés par site | `CalendrierOuvrableEngine.getDatesFeriesEtChomees` |
| **RG-PTG-09** | Pré-remplissage autonome (hors weekend/férié/congé) | `PointageSaisieService.chargerGrilleHebdo` |
| **RG-PTG-10** | Gel et transmission DAF de la période de paie | `PointageSyntheseService.transmettreDaf` |
| **RG-CNG-01** | Taux d'acquisition mensuel légal (2,5 j/mois) | `CongeCalculateurEngine.calculerAcquisitionMensuelle` |
| **RG-CNG-02** | Décompte calendaire strict (début, fin, demi-jour) | `CongeCalculateurEngine.calculerNombreJours` |
| **RG-CNG-03** | Bonification d'ancienneté (+1j par tranche de 5 ans) | `CongeCalculateurEngine.calculerBonificationAnciennete` |
| **RG-CNG-04** | Scission arrêt maladie > 30 jours (Code Travail Art.13) | `CongeCalculateurEngine.doitScinderArretMaladie` |
| **RG-CNG-05** | Plafond annuel partagé pool familial (10 jours/an) | `CongeCalculateurEngine.verifierEligibilitePoolFamilial` |
| **RG-CNG-06** | Prescription triennale et purge FIFO (36 mois) | `PurgeFifoEngine.calculerJoursAExpier` |
| **RG-PAIE-01** | Assiette CNaPS et plafonnement légal à 8 × SMIG | `CalculateurPaieEngine.calculerCnaps` |
| **RG-PAIE-02** | Cotisation santé (OSTIE/AMIT) part salariale/patronale | `CalculateurPaieEngine.calculerSante` |
| **RG-PAIE-03** | Barème progressif par tranches IRSA | `CalculateurPaieEngine.calculerIrsa` |
| **RG-PAIE-04** | Déduction fiscale par enfant à charge (2 000 Ar) | `CalculateurPaieEngine.calculerIrsa` |
| **RG-PAIE-05** | Minimum légal de perception IRSA (3 000 Ar) | `CalculateurPaieEngine.calculerIrsa` |
| **RG-SEC-01** | Restriction de création de compte (/auth/register) | `SecurityConfig` / `AuthController.register` |
| **RG-SEC-02** | Filtrage strict des extensions de fichiers RH en GED | `DocumentRhController.televerserDocument` |
| **RG-SEC-03** | Interdiction d'auto-octroi de dérogations d'indemnité | `DemandeCongeController.creerDemande` |

---

# 3. Cartographie Détaillée des Règles par Domaine

---

## 3.1 Domaine Salariés & Dossier RH (RG-EMP)

### RG-EMP-01 : Âge minimum légal d'accès à l'emploi
- **Énoncé Métier :** Nul ne peut être embauché s'il n'a pas atteint l'âge minimum de **16 ans révolus** à la date de référence (Loi n° 2003-044, Art. 100).
- **Classe & Méthode :** `EmployeValidationEngine.validerAgeLegal(LocalDate dateNaissance, LocalDate referenceDate, int ageMin, List<String> erreurs)`
- **Classe Appelante :** `EmployeValidationEngine.validerEmploye(EmployeRequest req)` appelée par `EmployeService.creerEmploye(...)` et `EmployeService.modifierEmploye(...)`.
- **Contrôleur Backend :** `EmployeController.creerEmploye` (`POST /api/employes`) et `modifierEmploye` (`PUT /api/employes/{id}`).
- **Composants Frontend :** `EmployeFormModal.jsx` (Section `CiviliteSection.jsx`), validation préalable dans `employeFormValidation.js`.
- **Test de Validation :** `EmployeValidationEngineTest.testValiderAgeLegal_SuccesEtEchec()`.

### RG-EMP-02 : Format et cohérence du numéro de CIN
- **Énoncé Métier :** Le numéro de Carte d'Identité Nationale (CIN) à Madagascar doit comporter **exactement 12 chiffres**. La date de délivrance ne peut pas être dans le futur, ni antérieure au 16ème anniversaire du titulaire.
- **Classe & Méthode :** `EmployeValidationEngine.validerCinFormatEtDate(String cinNumero, LocalDate cinDateDelivrance, LocalDate dateNaissance, List<String> erreurs)`
- **Classe Appelante :** `EmployeValidationEngine.validerEmploye`.
- **Contrôleur Backend :** `EmployeController.java`.
- **Composants Frontend :** `IdentiteSection.jsx`, masque regex dans `employeFormValidation.js`.
- **Test de Validation :** `EmployeValidationEngineTest.testValiderCinFormatEtDate()`.

### RG-EMP-04 : Génération et unicité du matricule collaborateur
- **Énoncé Métier :** Par défaut, les matricules historiques de GALAXAM suivent un format numérique séquentiel sur 4 chiffres (`0001`, `0042`) pour compatibilité CNaPS/Santé, avec support d'extension préfixée (`EMP-YYYY-XXXX`).
- **Classe & Méthode :** `MatriculeGeneratorEngine.genererMatriculeSequentiel(long sequenceCount)` et `genererMatriculePrefixe(int annee, long sequenceCount)`
- **Classe Appelante :** `EmployeService.genererNouveauMatricule(Connection c)`.
- **Contrôleur Backend :** `EmployeController.prochainMatricule` (`GET /api/employes/prochain-matricule`).
- **Composants Frontend :** `EmployeFormModal.jsx` (champ matricule pré-rempli automatiquement).
- **Test de Validation :** `MatriculeGeneratorEngineTest.java`.

### RG-EMP-05 : Cohérence compte bancaire et RIB
- **Énoncé Métier :** Si une banque est sélectionnée, le numéro de compte RIB devient obligatoire. Inversement, la saisie d'un RIB impose le choix d'un établissement bancaire référencé.
- **Classe & Méthode :** `EmployeValidationEngine.validerCompteBancaire(CompteBanqueRequest cb, List<String> erreurs)`
- **Classe Appelante :** `EmployeValidationEngine.validerEmploye`.
- **Contrôleur Backend :** `EmployeController.java`.
- **Composants Frontend :** `CoordonneesBancairesSection.jsx`.
- **Test de Validation :** `EmployeValidationEngineTest.testValiderCompteBancaire()`.

### RG-EMP-06 : Désactivation et fin de collaboration
- **Énoncé Métier :** La désactivation d'un employé requiert impérativement un motif de départ valide (`DEMISSION`, `LICENCIEMENT`, `RETRAITE`, `FIN_CONTRAT`) et archive la date de désactivation sans supprimer l'historique de paie ou de pointage.
- **Classe & Méthode :** `EmployeValidationEngine.validerDesactivation(DesactivationRequest req)`
- **Classe Appelante :** `EmployeService.desactiverEmploye(Integer idEmploye, DesactivationRequest req, Connection c)`.
- **Contrôleur Backend :** `EmployeController.desactiverEmploye` (`PATCH /api/employes/{id}/desactiver`).
- **Composants Frontend :** `DesactiverEmployeModal.jsx` sur la fiche employé `EmployeFicheDetail.jsx`.
- **Test de Validation :** `EmployeValidationEngineTest.testValiderDesactivation()`.

---

## 3.2 Domaine Contrats & Supervision des Carrières (RG-CTR)

### RG-CTR-01 & RG-CTR-02 : Dates obligatoires et durée maximale de période d'essai
- **Énoncé Métier :** Tout contrat à terme (`CDD`, `STAGE`, `PRESTATION`, `CONSULTANT`) exige une date de fin explicite. La période d'essai initiale ne peut pas excéder **6 mois**.
- **Classe & Méthode :** `ContratValidationEngine.validerDatesContrat(String codeTypeContrat, Date dateDebut, Date dateFin, Integer dureeEssaiMois)`
- **Classe Appelante :** `WorkflowRhService.enregistrerMouvement(WorkflowRhRequest req, Connection c)` et `ContratTravailController`.
- **Contrôleur Backend :** `WorkflowRhController.java` (`POST /api/carrieres/workflows`).
- **Composants Frontend :** `EmbaucheModal.jsx`, `ContratFormModal.jsx`.
- **Test de Validation :** `ContratCarriereWorkflowIntegrationTest.java`.

### RG-CTR-04 & RG-CTR-05 : Transitions de carrière et motifs de sortie
- **Énoncé Métier :** Une promotion, mutation ou revalorisation exige un contrat actif préalable. Une rupture (`RUPTURE_ESSAI`, `DEMISSION`, `LICENCIEMENT`) exige obligatoirement un motif textuel.
- **Classe & Méthode :** `ContratValidationEngine.validerTransitionCarriere(String codeEvenement, boolean hasContratActif, String motifSortie)`
- **Classe Appelante :** `WorkflowRhService.validerWorkflow(Integer idWorkflow, Connection c)`.
- **Contrôleur Backend :** `WorkflowRhController.validerWorkflow` (`POST /api/carrieres/workflows/{id}/valider`).
- **Composants Frontend :** `CarrieresSupervisionPage.jsx`, `WorkflowActionModal.jsx`.

### RG-CTR-07 : Calcul d'ancienneté effective totale et contractuelle
- **Énoncé Métier :** L'ancienneté légale se calcule en mémoire en reconstituant la chaîne continue des contrats successifs (`id_contrat_precedent`) sans interruption, exprimée en années décimales et au format officiel `"X ans Y mois"`.
- **Classe & Méthode :** `AncienneteEngine.evaluerAnciennete(List<IntervalleContrat> contrats, Integer idContratCible, LocalDate dateReference)`
- **Classe Appelante :** `WorkflowRhService.getAncienneteDetaillee(...)` et `ContratTravail.chargerAnciennete(...)`.
- **Contrôleur Backend :** `ContratTravailController.java` et `WorkflowRhController.java`.
- **Composants Frontend :** Badges d'ancienneté dans `CarrieresSupervisionPage.jsx` et `ContratsAlertsWidget.jsx`.
- **Test de Validation :** `AncienneteEngineTest.java`.

---

## 3.3 Domaine Pointage, Présences & Indemnités (RG-PTG)

### RG-PTG-01 : Retards et seuil de tolérance
- **Énoncé Métier :** Tout pointage d'arrivée après l'heure de début théorique (ex: 08h00) constitue un retard calculé en minutes entières. Une arrivée avant la tolérance d'avance ne crée pas d'heures supplémentaires d'avance.
- **Classe & Méthode :** `PointageCalculateurEngine.calculerMinutesRetard(LocalTime arrivee, LocalTime debutTheorique, LocalTime plafondTolerance)`
- **Classe Appelante :** `PointageSaisieService.sauvegarderLignePointage(...)`, `PointageAnomalieService.detecterAnomalies(...)`.
- **Contrôleur Backend :** `PointageSaisieController.sauvegarderSemaine` (`POST /api/pointages/saisie/semaine`).
- **Composants Frontend :** `PointageGrilleSaisieHebdo.jsx`, `PointageCellEditor.jsx`.
- **Test de Validation :** `PointageCalculateurEngineTest.testCalculerMinutesRetard()`.

### RG-PTG-02 : Heures de travail de nuit
- **Énoncé Métier :** Le travail accompli entre **22h00 et 05h00** ouvre droit à une majoration d'heures de nuit (+30%). Le calcul gère automatiquement les sorties tardives et les vacations traversant minuit.
- **Classe & Méthode :** `PointageCalculateurEngine.calculerHeuresNuit(LocalTime sortie, LocalTime debutNuit, LocalTime finNuit)`
- **Classe Appelante :** `PointageSaisieService.calcHeuresNuit(...)` et `PointageSyntheseService`.
- **Contrôleur Backend :** `PointageSaisieController.java`.
- **Composants Frontend :** Colonne "Heures Nuit" dans `PointageGrilleSaisieHebdo.jsx`.
- **Test de Validation :** `PointageCalculateurEngineTest.testCalculerHeuresNuit()`.

### RG-PTG-03 & RG-PTG-04 : Primes de repas et indemnités de déplacement
- **Énoncé Métier :** 
  - Repas de midi : montant de base (ex: 2 000 Ar). Sortie >= 19h30 : prime 2e repas 1 (ex: +2 500 Ar). Sortie >= 21h00 : prime 2e repas 2 (ex: +4 000 Ar).
  - Déplacement : indemnité aller/retour. Si le salarié prend son repas sur site, une déduction de transport (ex: 1 000 Ar à Tamatave) s'applique.
- **Classe & Méthodes :** 
  - `PointageCalculateurEngine.calculerTotalRepasJour(...)`
  - `PointageCalculateurEngine.calculerDeplacementJour(...)`
- **Classe Appelante :** `PointageSaisieService.calculerEtSauvegarderIndemnitesJour(...)`.
- **Contrôleur Backend :** `PointageSaisieController.java`.
- **Composants Frontend :** Modale de détail des indemnités journalières `IndemniteJourModal.jsx`.
- **Test de Validation :** `PointageCalculateurEngineTest.testCalculerTotalRepasJour()` et `testCalculerDeplacementJour()`.

### RG-PTG-05, 06, 07 : Seuil 40h hebdomadaire, ventilation HS et compensation Cadres
- **Énoncé Métier :** 
  - La base légale est de **40 heures par semaine ouvrée** (8h/jour × nombre de jours ouvrables).
  - **Compensation 1:1 :** les retards de la semaine sont compensés en priorité par les heures travaillées au-delà du seuil.
  - **Ventilation HS :** Les 8 premières heures sup nettes sont majorées à **130%**, les heures au-delà à **150%**.
  - **Cadres :** Aucun paiement d'heures supplémentaires (forfait conventionnel), mais la compensation des retards reste active.
- **Classe & Méthode :** `PointageCalculateurEngine.calculerTroncon(LocalDate debutSemaine, int nbJoursOuvres, double totalHeures, double minutesRetard, boolean isCadre, double seuilHeuresJour, double seuilHsTranche1)`
- **Classe Appelante :** `PointageSyntheseService.calculerSyntheseSemaine(...)` et `PointageSyntheseService.calculerSyntheseMensuelle(...)`.
- **Contrôleur Backend :** `PointageSyntheseController.calculerSynthese` (`POST /api/pointages/synthese/calculer`).
- **Composants Frontend :** `SynthesePointagePage.jsx`, `PointageHebdoTable.jsx`.
- **Test de Validation :** `PointageCalculateurEngineTest.testCalculerTronconHebdo()`.

### RG-PTG-08 & RG-PTG-09 : Calendrier ouvrable, jours chômés et pré-remplissage
- **Énoncé Métier :** Les jours fériés chômés (nationaux ou spécifiques au site) ainsi que les périodes de fermeture administrative sont déduits des jours ouvrables. La saisie quotidienne pré-remplit les salariés autonomes uniquement sur les jours ouvrés (excluant weekends, fériés et congés approuvés).
- **Classe & Méthodes :** 
  - `CalendrierOuvrableEngine.getDatesFeriesEtChomees(debut, fin, idSite, c)`
  - `PointageSaisieService.chargerGrilleHebdo(LocalDate debutSemaine, Integer idSite, Connection c)`
- **Classe Appelante :** `PointageSaisieController.getGrilleHebdo(...)`.
- **Contrôleur Backend :** `PointageSaisieController.java`.
- **Composants Frontend :** `SaisieQuotidiennePage.jsx`, `PointageGrilleSaisieHebdo.jsx`.
- **Test de Validation :** `CalendrierOuvrableEngineTest.java`.

---

## 3.4 Domaine Congés, Absences & Ledger FIFO (RG-CNG)

### RG-CNG-01 & RG-CNG-02 : Acquisition et décompte des congés
- **Énoncé Métier :** L'acquisition légale est de **2,5 jours calendaires par mois** de travail effectif. Le décompte lors d'une absence s'effectue en jours calendaires stricts (`ChronoUnit.DAYS + 1`), avec prise en charge des demi-journées (0,5 j).
- **Classe & Méthodes :** 
  - `CongeCalculateurEngine.calculerAcquisitionMensuelle(int moisTravailles)`
  - `CongeCalculateurEngine.calculerNombreJours(LocalDate debut, LocalDate fin, boolean estDemiJournee)`
- **Classe Appelante :** `CongeService.creerDemande(...)` et le cron `RattrapageAutomatiqueService.acquerirCongesMensuels()`.
- **Contrôleur Backend :** `DemandeCongeController.creerDemande` (`POST /api/conges/demandes`).
- **Composants Frontend :** `DemandeCongeFormModal.jsx`, `GestionCongesPage.jsx`.
- **Test de Validation :** `CongeCalculateurEngineTest.testCalculerNombreJours()`.

### RG-CNG-04 : Scission d'arrêt maladie > 30 jours (Code du Travail Art. 13)
- **Énoncé Métier :** Un arrêt maladie ne peut faire l'objet d'un maintien de salaire employeur que dans la limite de **30 jours continus**. Tout arrêt maladie supérieur à 30 jours doit être scindé en une tranche avec maintien (1 à 30 jours) et une tranche sans solde / prise en charge CNaPS (jour 31 et suivants).
- **Classe & Méthode :** `CongeCalculateurEngine.doitScinderArretMaladie(BigDecimal totalJours)`
- **Classe Appelante :** `CongeService.creerDemande(DemandeCongeRequest req, Connection c)`.
- **Contrôleur Backend :** `DemandeCongeController.java`.
- **Composants Frontend :** `DemandeCongeFormModal.jsx` (Alerte d'avertissement automatique Article 13).
- **Test de Validation :** `CongeCalculateurEngineTest.testDoitScinderArretMaladie()`.

### RG-CNG-06 : Règle FIFO et purge triennale des droits (36 mois)
- **Énoncé Métier :** Selon l'Art. 85 du Code du Travail, les congés non pris se prescrivent après 3 ans (36 mois). Le système applique une consommation strictement **FIFO** (les prises de congés apurent en premier les droits les plus anciens). Les reliquats vieux de plus de 36 mois non consommés sont expirés.
- **Classe & Méthode :** `PurgeFifoEngine.calculerJoursAExpier(double totalAcquisAnciens, double totalPris, double totalDejaExpire)`
- **Classe Appelante :** Cron `RattrapageAutomatiqueService.purgerDroitsEchus()` et `CongeService.getBilanDroits(...)`.
- **Contrôleur Backend :** `CongeSoldeController.java`.
- **Composants Frontend :** `SoldesOverviewTab.jsx` (indicateur de droits en voie d'expiration).
- **Test de Validation :** `PurgeFifoEngineTest.java`.

---

## 3.5 Domaine Configuration Paie, Cotisations & IRSA (RG-PAIE)

### RG-PAIE-01 : Cotisation CNaPS et plafonnement 8 × SMIG
- **Énoncé Métier :** L'assiette de cotisation CNaPS est plafonnée à **8 fois le SMIG légal** (fixé par décret). Au-delà, la cotisation n'augmente plus. Les taux salariaux (1%) et patronaux (13%) sont appliqués sur cette assiette plafonnée.
- **Classe & Méthode :** `CalculateurPaieEngine.calculerCnaps(BigDecimal salaireBrut, BigDecimal smig, double tauxSalarial, double tauxPatronal)`
- **Classe Appelante :** `PointageSyntheseService.genererPrepaieBulletin(...)` et `PaieConfigLegaleService`.
- **Contrôleur Backend :** `PaieConfigLegaleController.java`.
- **Composants Frontend :** `PaieConfigLegaleTab.jsx`.
- **Test de Validation :** `CalculateurPaieEngineTest.testCalculerCnaps()`.

### RG-PAIE-03, 04, 05 : Barème progressif IRSA, déductions familiales et minimum légal
- **Énoncé Métier :** 
  - L'IRSA est calculé par tranches progressives (Code Général des Impôts).
  - Une **déduction fiscale de 2 000 Ar par enfant à charge** est appliquée sur le montant d'impôt brut calculé.
  - Dans tous les cas, l'impôt final ne peut pas être inférieur au **minimum de perception légal de 3 000 Ar**.
- **Classe & Méthode :** `CalculateurPaieEngine.calculerIrsa(BigDecimal baseImposable, List<TrancheIrsa> tranches, int nbEnfantsACharge)`
- **Classe Appelante :** Moteur de pré-paie et contrôleur IRSA.
- **Contrôleur Backend :** `IrsaController.calculerSimulation` (`POST /api/paie-config/irsa/simuler`).
- **Composants Frontend :** `ParametresModulesPanel.jsx` (Onglet Barème IRSA).
- **Test de Validation :** `CalculateurPaieEngineTest.testCalculerIrsa()`.

---

## 3.6 Domaine Sécurité, IAM, Permissions & GED (RG-SEC)

### RG-SEC-01 : Protection de l'auto-enregistrement
- **Énoncé Métier :** L'enregistrement d'un utilisateur (`/auth/register`) ne peut en aucun cas être public. Il requiert impérativement le privilège `ROLE_ADMIN` authentifié.
- **Classes & Méthodes :** 
  - `SecurityConfig.java` : `.requestMatchers("/auth/register").hasAuthority("ROLE_ADMIN")`
  - `AuthController.java` : `@PreAuthorize("hasAuthority('ROLE_ADMIN')")`
- **Composants Frontend :** `SecuriteAdminPanel.jsx` (Modale de création de compte utilisateur).

### RG-SEC-02 : Whitelist stricte d'extension des documents RH
- **Énoncé Métier :** Les pièces justificatives téléversées en GED (CIN, contrats scannés, certificats médicaux) doivent impérativement posséder une extension whitelistée par le type de document (`pdf, png, jpg, jpeg`). Tout fichier exécutable ou non autorisé est rejeté avec code HTTP 400.
- **Classe & Méthode :** `DocumentRhController.televerserDocument(Integer idEmploye, Integer idTypeDoc, MultipartFile fichier)`
- **Contrôleur Backend :** `DocumentRhController.java`.
- **Composants Frontend :** `EmployeDocumentsTab.jsx`, `UploadDocModal.jsx`.

### RG-SEC-03 : Interdiction d'auto-octroi de dérogations d'indemnité
- **Énoncé Métier :** Un demandeur de congé ne peut pas s'auto-attribuer un maintien exceptionnel d'indemnité de déplacement ou de repas lors de sa soumission. L'octroi ou la modification de dérogations est un privilège d'arbitrage réservé à `ROLE_ADMIN` ou aux validateurs ayant la permission `CONGE_VALIDATE`.
- **Classe & Méthodes :** `DemandeCongeController.creerDemande(...)` et `DemandeCongeController.modifierDemande(...)`.
- **Contrôleur Backend :** `DemandeCongeController.java`.
- **Composants Frontend :** `DemandeCongeFormModal.jsx` (champs de dérogation masqués pour les utilisateurs standards).

---

# 4. Matrice Opérationnelle : "Je veux modifier une règle, que changer, où et comment ?"

Cette section est le **manuel de maintenance pratique**. Lorsqu'une règle d'entreprise évolue ou qu'une loi change, suivez scrupuleusement la fiche correspondante :

---

### Scénario 1 : Modifier l'âge minimum légal d'accès à l'emploi
*Exemple : La loi ou l'accord d'entreprise relève l'âge d'embauche de 16 à 18 ans.*
1. **Backend Moteur :** Ouvrir [`EmployeValidationEngine.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/employe/engine/EmployeValidationEngine.java).
   - Modifier la constante : `public static final int AGE_MINIMUM_LEGAL = 18;`
2. **Frontend Validation :** Ouvrir `frontend/src/utils/employeFormValidation.js`.
   - Ajuster la constante `AGE_MINIMUM_LEGAL = 18`.
3. **Tests Unitaires :** Ouvrir [`EmployeValidationEngineTest.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/test/java/mg/galaxam/sirh/employe/engine/EmployeValidationEngineTest.java).
   - Ajuster les jeux d'essais pour 18 ans et exécuter `mvn test -Dtest=EmployeValidationEngineTest`.

---

### Scénario 2 : Modifier la structure du matricule collaborateur
*Exemple : Remplacer la nomenclature historique `0001` par le format préfixé officiel `EMP-2026-0001`.*
1. **Backend Moteur :** Ouvrir [`MatriculeGeneratorEngine.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/employe/engine/MatriculeGeneratorEngine.java).
   - Dans `genererMatriculeParDefaut(long sequenceCount)`, basculer vers `genererMatriculePrefixe(LocalDate.now().getYear(), sequenceCount)`.
2. **Backend Service :** Vérifier dans `EmployeService.genererNouveauMatricule` que la méthode appelée correspond au format souhaité.
3. **Tests Unitaires :** Mettre à jour [`MatriculeGeneratorEngineTest.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/test/java/mg/galaxam/sirh/employe/engine/MatriculeGeneratorEngineTest.java).

---

### Scénario 3 : Modifier le plafond de durée des CDD ou des périodes d'essai
*Exemple : L'accord de branche réduit la période d'essai maximale de 6 mois à 3 mois.*
1. **Backend Moteur :** Ouvrir [`ContratValidationEngine.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/contrat/engine/ContratValidationEngine.java).
   - Modifier `public static final int DUREE_MAX_ESSAI_MOIS = 3;`
2. **Base de Données (Dynamique) :** Modifier la valeur de `MAX_RENOUVELLEMENTS_ESSAI` dans la table `parametre_global` via l'interface `/admin/parametres`.
3. **Frontend IHM :** Modifier le `max` de l'input dans `EmbaucheModal.jsx`.

---

### Scénario 4 : Modifier la formule de calcul de l'ancienneté
*Exemple : Ne plus réinitialiser l'ancienneté en cas de changement de filiale au sein du même groupe GALAXAM.*
1. **Backend Moteur :** Ouvrir [`AncienneteEngine.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/contrat/engine/AncienneteEngine.java).
   - Modifier la méthode `evaluerAnciennete(...)` pour accepter la chaîne de contrats même si `id_filiale` diffère.
2. **Tests Unitaires :** Ajouter un cas de mutation multi-filiales dans [`AncienneteEngineTest.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/test/java/mg/galaxam/sirh/contrat/engine/AncienneteEngineTest.java).

---

### Scénario 5 : Modifier la tolérance de retard au pointage
*Exemple : Accorder 10 minutes de tolérance le matin sans pénalité de retard.*
1. **Backend Moteur :** Ouvrir [`PointageCalculateurEngine.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/pointage/engine/PointageCalculateurEngine.java).
   - Dans `calculerMinutesRetard(arrivee, debutTheorique, plafondTolerance)`, adapter le calcul avec une franchise : `if (Duration.between(debutTheorique, arrivee).toMinutes() > toleranceMinutes) ...`
2. **Base de Données :** Si la franchise est paramétrable, ajouter la clé `TOLERANCE_RETARD_MINUTES` dans `parametre_global`.
3. **Tests Unitaires :** Exécuter [`PointageCalculateurEngineTest.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/test/java/mg/galaxam/sirh/pointage/engine/PointageCalculateurEngineTest.java).

---

### Scénario 6 : Modifier le seuil de déclenchement des Heures Supplémentaires (Base 40h)
*Exemple : Passage d'un régime 40h à 44h ou abaissement à 35h.*
1. **Base de Données :** Mettre à jour `SEUIL_HEURES_SEMAINE` et `SEUIL_HEURES_JOUR` dans la table `parametre_global` (`UPDATE parametre_global SET valeur = '35' WHERE code_parametre = 'SEUIL_HEURES_SEMAINE';`).
2. **Backend Moteur :** Vérifier que [`PointageCalculateurEngine.calculerTroncon`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/pointage/engine/PointageCalculateurEngine.java) reçoit bien la nouvelle valeur passée par `PointageSyntheseService`.
3. **Tests d'Intégration :** Réexécuter la synthèse complète dans `PointageSyntheseWorkflowIntegrationTest.java`.

---

### Scénario 7 : Modifier la plage horaire du travail de nuit
*Exemple : Le travail de nuit démarre désormais à 21h00 au lieu de 22h00.*
1. **Base de Données :** Mettre à jour `SEUIL_NUIT_DEBUT` dans `parametre_global` :
   `UPDATE parametre_global SET valeur = '21:00' WHERE code_parametre = 'SEUIL_NUIT_DEBUT';`
2. **Backend Service :** [`PointageSaisieService.calcHeuresNuit`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/pointage/service/PointageSaisieService.java) prend en charge le paramètre ou sa surcharge par défaut.
3. **Tests Unitaires :** Tester le calcul dans `PointageCalculateurEngineTest.java`.

---

### Scénario 8 : Modifier les montants et seuils d'indemnité repas et déplacement
*Exemple : L'indemnité repas de midi passe de 2 000 Ar à 3 000 Ar.*
1. **Base de Données :** Modifier directement dans `parametre_global` :
   - `MONTANT_REPAS_MIDI` = 3000
   - `MONTANT_2E_REPAS_1` = 3500
   - `MONTANT_2E_REPAS_2` = 5000
2. **IHM Administrateur :** Il n'est pas nécessaire de toucher au code Java ! Rendez-vous sur la page web `/admin/parametres` (Onglet Paramètres Généraux) et enregistrez la nouvelle valeur. Le moteur Java prendra en compte le changement immédiatement lors de la prochaine sauvegarde de pointage.

---

### Scénario 9 : Modifier le pré-remplissage et l'éligibilité de la grille de pointage
*Exemple : Masquer de la grille de saisie les stagiaires ou intégrer une nouvelle catégorie d'employés.*
1. **Backend Service :** Ouvrir [`PointageSaisieService.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/pointage/service/PointageSaisieService.java).
   - Méthode `chargerGrilleHebdo` : ajuster le filtre SQL sélectionnant les salariés actifs avec contrat en cours.
2. **Frontend Composant :** `PointageGrilleSaisieHebdo.jsx` : adapter les colonnes ou les badges de statut si nécessaire.

---

### Scénario 10 : Modifier le taux d'acquisition mensuel des congés payés (2,5 j/mois)
*Exemple : Accorder 3,0 jours de congés par mois pour des postes pénibles ou expatriés.*
1. **Backend Moteur :** Ouvrir [`CongeCalculateurEngine.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/conge/engine/CongeCalculateurEngine.java).
   - Modifier `TAUX_ACQUISITION_MENSUEL = new BigDecimal("3.0");`
2. **Backend Cron :** [`RattrapageAutomatiqueService.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/cron/RattrapageAutomatiqueService.java) appliquera ce taux lors de la génération mensuelle des soldes.
3. **Tests Unitaires :** Adapter `CongeCalculateurEngineTest.java`.

---

### Scénario 11 : Modifier la règle de scission arrêt maladie > 30 jours (Article 13)
*Exemple : L'entreprise décide de maintenir le salaire pendant 60 jours au lieu de 30.*
1. **Backend Moteur :** Ouvrir [`CongeCalculateurEngine.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/conge/engine/CongeCalculateurEngine.java).
   - Modifier la constante : `public static final int PLAFOND_MALADIE_MAINTIEN_JOURS = 60;`
2. **Backend Service :** [`CongeService.creerDemande`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/conge/service/CongeService.java) scindera désormais automatiquement au-delà de 60 jours.
3. **Frontend Message :** Mettre à jour le texte explicatif dans `DemandeCongeFormModal.jsx`.

---

### Scénario 12 : Modifier la prescription triennale des congés (Purge FIFO 36 mois)
*Exemple : Rallonger le délai de prescription de 3 ans (36 mois) à 4 ans (48 mois).*
1. **Backend Cron :** Ouvrir [`RattrapageAutomatiqueService.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/cron/RattrapageAutomatiqueService.java).
   - Modifier la requête SQL de sélection des droits échus en remplaçant `NOW() - INTERVAL '36 months'` par `'48 months'`.
2. **Backend Moteur :** Le calcul mathématique dans [`PurgeFifoEngine.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/conge/engine/PurgeFifoEngine.java) reste inchangé car il est basé sur la somme des anciens droits transmis.

---

### Scénario 13 : Mettre à jour le SMIG et le plafond des cotisations CNaPS
*Exemple : Le SMIG national passe de 262 680 Ar à 300 000 Ar.*
1. **Base de Données / IHM :** Rendez-vous dans l'interface `/admin/parametres` (Onglet Configuration Légale Paie).
   - Modifier la valeur du SMIG pour la nouvelle année.
   - La table `paie_config_legale` est mise à jour.
2. **Backend Moteur :** [`CalculateurPaieEngine.calculerCnaps`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/paieconfig/engine/CalculateurPaieEngine.java) calcule automatiquement le nouveau plafond : $8 \times 300\,000 = 2\,400\,000\text{ Ar}$.

---

### Scénario 14 : Mettre à jour les tranches de l'IRSA ou le minimum de perception
*Exemple : La loi de finances modifie les tranches d'imposition ou porte le minimum de perception à 5 000 Ar.*
1. **Base de Données :**
   - Mettre à jour le minimum de perception dans `paie_config_legale` (`minimum_perception_irsa = 5000.00`).
   - Mettre à jour les tranches dans la table `bareme_irsa` (seuil_min, seuil_max, taux).
2. **Backend Moteur :** [`CalculateurPaieEngine.calculerIrsa`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/paieconfig/engine/CalculateurPaieEngine.java) utilise dynamiquement la liste des tranches chargées en base de données.
3. **Tests Unitaires :** Vérifier dans `CalculateurPaieEngineTest.java`.

---

### Scénario 15 : Modifier la déduction fiscale par enfant à charge
*Exemple : La déduction IRSA passe de 2 000 Ar à 3 000 Ar par enfant.*
1. **Base de Données :** Modifier `deduction_charge_famille` dans la table `paie_config_legale`.
2. **Backend Moteur :** Si fallback par défaut, ajuster `CalculateurPaieEngine.DEDUCTION_PAR_ENFANT`.
3. **Frontend IHM :** Page `/admin/parametres` (Onglet Paie Légale).

---

### Scénario 16 : Modifier les types de pièces jointes autorisées en GED
*Exemple : Autoriser le format `.docx` pour les contrats.*
1. **Base de Données :** Mettre à jour la colonne `extension_autorisee` de la table `ref_type_document` pour le document concerné :
   `UPDATE ref_type_document SET extension_autorisee = 'pdf, png, jpg, jpeg, docx' WHERE code = 'CONTRAT_SIGNE';`
2. **Backend Contrôleur :** [`DocumentRhController.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/api/controller/employe/DocumentRhController.java) lit dynamiquement cette colonne pour valider l'upload. Aucun redémarrage serveur requis !
3. **Frontend Formulaire :** Ajouter `.docx` dans la balise `accept` du champ file dans `UploadDocModal.jsx`.

---

### Scénario 17 : Ajouter un nouveau rôle ou permission applicative
*Exemple : Créer un rôle `ROLE_CONTROLEUR_GESTION` avec lecture seule sur les paies et synthèses.*
1. **Base de Données :** Insérer dans `ref_role` et lier les permissions dans `role_permission` :
   `INSERT INTO ref_role (code, libelle) VALUES ('ROLE_CONTROLEUR_GESTION', 'Contrôleur de Gestion');`
2. **Backend Contrôleurs :** Ajouter l'autorité dans les annotations `@PreAuthorize` souhaitées (ex: `@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_CONTROLEUR_GESTION')")`).
3. **Frontend Routage :** Ajouter le rôle dans `roleRequired` des routes protégées dans `App.jsx` ou le système RBAC `useAuth.js`.

---

# 5. Checklist Qualité lors d'une Modification Métier

Avant de déployer toute modification de règle métier en préproduction ou production, valider la checklist impérative suivante :

```text
[ ] 1. MOTEUR JAVA PUR
    La règle est-elle isolée dans un package engine/ sans couplage SQL ou HTTP ?
    
[ ] 2. TESTS UNITAIRES
    Le fichier *EngineTest.java correspondant a-t-il été mis à jour avec les cas nominaux et cas limites ?
    La commande 'mvn test -Dtest=*EngineTest' retourne-t-elle 100% de succès ?

[ ] 3. GESTION DES NULL & LIMITES
    Avez-vous testé les valeurs nulles, les dates inverses, les divisions par zéro et les cas limites ?

[ ] 4. ALIGNEMENT FRONTEND
    Les validateurs JavaScript côté client (employeFormValidation.js, masques regex) reflètent-ils fidèlement la nouvelle règle ?

[ ] 5. CONCURRENCE & TRANSACTIONS
    Si la règle modifie un solde ou un statut critique, le verrouillage pessimiste (FOR UPDATE) et le rollback transactionnel sont-ils garantis ?

[ ] 6. DOCUMENTATION
    Le présent document (GUIDE_REGLES_METIER_ET_MODIFICATIONS.md) a-t-il été mis à jour avec le nouveau comportement ?
```

---
*Document certifié conforme à la structure logicielle GALAXAM-SIRH.*
