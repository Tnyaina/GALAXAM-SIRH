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
  - [Scénario 6 : Modifier le seuil de déclenchement des Heures Supplémentaires (Base 40h) ou la durée journalière](#scénario-6--modifier-le-seuil-de-déclenchement-des-heures-supplémentaires-base-40h-ou-la-durée-journalière)
  - [Scénario 7 : Modifier la plage horaire du travail de nuit](#scénario-7--modifier-la-plage-horaire-du-travail-de-nuit)
  - [Scénario 8 : Modifier les montants et seuils d'indemnité repas et déplacement](#scénario-8--modifier-les-montants-et-seuils-dindemnité-repas-et-déplacement)
  - [Scénario 8 bis : Activer ou désactiver une règle opérationnelle débrayable (Repas, Transport, Congés)](#scénario-8-bis--activer-ou-désactiver-une-règle-opérationnelle-débrayable-repas-transport-congés)
  - [Scénario 9 : Modifier le pré-remplissage et l'éligibilité de la grille de pointage](#scénario-9--modifier-le-pré-remplissage-et-léligibilité-de-la-grille-de-pointage)
  - [Scénario 10 : Modifier le taux d'acquisition mensuel des congés payés (2,5 j/mois)](#scénario-10--modifier-le-taux-dacquisition-mensuel-des-congés-payés-25-jmois)
  - [Scénario 11 : Modifier la règle de scission arrêt maladie > 30 jours (Article 13)](#scénario-11--modifier-la-règle-de-scission-arrêt-maladie--30-jours-article-13)
  - [Scénario 12 : Modifier la prescription triennale des congés (Purge FIFO 36 mois)](#scénario-12--modifier-la-prescription-triennale-des-congés-purge-fifo-36-mois)
  - [Scénario 13 : Mettre à jour le SMIG et le plafond des cotisations CNaPS](#scénario-13--mettre-à-jour-le-smig-et-le-plafond-des-cotisations-cnaps)
  - [Scénario 14 : Mettre à jour les tranches de l'IRSA ou le minimum de perception](#scénario-14--mettre-à-jour-les-tranches-de-lirsa-ou-le-minimum-de-perception)
  - [Scénario 15 : Modifier la déduction fiscale par enfant à charge](#scénario-15--modifier-la-déduction-fiscale-par-enfant-à-charge)
  - [Scénario 17 : Ajouter un nouveau rôle ou module applicatif (Backend + Base)](#scénario-17--ajouter-un-nouveau-rôle-ou-module-applicatif-backend--base)
  - [Scénario 18 : Sécuriser ou masquer un élément sensible dans l'interface Frontend (Bouton, Colonne, Donnée)](#scénario-18--sécuriser-ou-masquer-un-élément-sensible-dans-linterface-frontend-bouton-colonne-donnée)
  - [Scénario 19 : Configurer ou modifier le compte administrateur initial (Bootstrap Seeder)](#scénario-19--configurer-ou-modifier-le-compte-administrateur-initial-bootstrap-seeder)
- [5. Guide Opérationnel du Frontend (React / Vite) & Système de Permissions UI](#5-guide-opérationnel-du-frontend-react--vite--système-de-permissions-ui)
  - [5.1 Structure et Cartographie des Dossiers Frontend (`frontend/src/`)](#51-structure-et-cartographie-des-dossiers-frontend-frontendsrc)
  - [5.2 Système de Contrôle d'Accès Frontend (`useAuth` vs `<HasPermission />`)](#52-système-de-contrôle-daccès-frontend-useauth-vs-haspermission-)
  - [5.3 Matrice Complète des Permissions et Impacts sur l'Interface](#53-matrice-complète-des-permissions-et-impacts-sur-linterface)
  - [5.4 Comment Sécuriser / Masquer un Nouveau Champ ou Bouton Sensible](#54-comment-sécuriser--masquer-un-nouveau-champ-ou-bouton-sensible)
  - [5.5 Comment Créer une Nouvelle Page ou un Nouvel Onglet Protégé](#55-comment-créer-une-nouvelle-page-ou-un-nouvel-onglet-protégé)
- [6. Checklist Qualité lors d'une Modification Métier](#6-checklist-qualité-lors-dune-modification-métier)

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
| **RG-PAIE-06** | Décompte des absences injustifiées au taux horaire strict | `PayrollExecutionEngine.calculer` |
| **RG-PAIE-07** | Régularisation congés DAF au retour du salarié | `PayrollExecutionEngine` & `PayrollService` |
| **RG-PAIE-08** | Plafonnements légaux d'endettement (Art. 72 : 1/3 max & Art. 68 : 1/10 max) | `PayrollExecutionEngine.calculer` |
| **RG-PAIE-09** | Solde de Tout Compte (STC) & Quittance libératoire financière réconciliée | `StcService.calculerStc` |
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

### RG-PAIE-03, 04, 05 : Barème progressif IRSA, déductions familiales et minimum légal (CGI Art. 01.03.18)
- **Énoncé Métier :** 
  - L'IRSA est calculé par tranches progressives selon le Code Général des Impôts (Art. 01.03.18).
  - L'assiette imposable est obligatoirement **arrondie à la centaine d'Ariary inférieure** ($\lfloor \text{Base} / 100 \rfloor \times 100$).
  - L'impôt brut obtenu par tranche est **arrondi à la dizaine d'Ariary supérieure** ($\lceil \text{ImpotBrut} / 10 \rceil \times 10$).
  - Une **déduction fiscale de 2 000 Ar par enfant à charge** est appliquée sur le montant d'impôt brut calculé.
  - Dans tous les cas, l'impôt net final ne peut pas être inférieur au **minimum de perception légal de 3 000 Ar**.
  - **Parité stricte :** Le même moteur canonique [`CalculateurPaieEngine.calculerIrsaDetaille`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/paieconfig/engine/CalculateurPaieEngine.java) est partagé entre l'orchestration mensuelle (`PayrollExecutionEngine`) et la quittance de départ (`StcService`), garantissant une conformité mathématique au centime près.
- **Classe & Méthodes :** 
  - `CalculateurPaieEngine.calculerIrsaDetaille(BigDecimal baseImposable, List<TrancheIrsa> tranches, int nbEnfants, BigDecimal minPerception, BigDecimal deductionParEnfant)`
  - `CalculateurPaieEngine.calculerIrsa(BigDecimal baseImposable, List<TrancheIrsa> tranches, int nbEnfantsACharge)`
- **Classes Appelantes :** `PayrollExecutionEngine.java`, `StcService.java`, `IrsaController.java`.
- **Contrôleur Backend :** `IrsaController.calculerSimulation` (`POST /api/paie-config/irsa/simuler`), `PaieController`, `StcController`.
- **Composants Frontend :** `ParametresModulesPanel.jsx` (Onglet Barème IRSA), `BulletinPaieDetailModal.jsx`, `RecuStcModal.jsx`.
- **Test de Validation :** `CalculateurPaieEngineTest.java` et `CalculateurPaieEngineAdvancedTest.java`.

### RG-PAIE-06 : Décompte des absences au taux horaire strict
- **Énoncé Métier :** Pour les salariés sans solde de congé ou en cas d'absence non rémunérée, la retenue est décomptée au taux horaire légal : $\text{Heures d'absence} \times (\text{Salaire de Base} / 173{,}3333)$. La retenue ne peut en aucun cas excéder le salaire de base de la période.
- **Classe & Méthode :** `PayrollExecutionEngine.calculer(PayrollCalculationInput input)`
- **Classe Appelante :** `PayrollService.calculerBulletinsPeriode(...)`.
- **Contrôleur Backend :** `PaieController.calculer` (`POST /api/v1/paie/calculer`).
- **Composants Frontend :** `GestionPaiePage.jsx`, `PayrollTable.jsx`.
- **Test de Validation :** `AuditDeepInspectionExhaustifTest.testWorkflowDafAbsenceHoraireEtRegularisationConge()`.

### RG-PAIE-07 : Régularisation des congés DAF au retour du salarié
- **Énoncé Métier :** Lorsqu'un salarié justifie son absence a posteriori, la DAF enregistre un mouvement de type `REGULARISATION_CONGE` (sens `GAIN`, rubrique `REGULARISATION_PAIE`). Le montant est réintégré dans les gains bruts imposables (catégorie `SALAIRE_BASE`, ordre 112) au mois $M+1$, soumis aux cotisations et à l'IRSA.
- **Classe & Méthodes :** `PayrollService.preparerInput(...)` et `PayrollExecutionEngine.calculer(...)`.
- **Contrôleur Backend :** `PaieAjustementController.creerMouvement` (`POST /api/v1/paie/ajustements/mouvements`).
- **Composants Frontend :** Modale de saisie des variables et ajustements `VariablesPaieModal.jsx`.
- **Test de Validation :** `AuditDeepInspectionExhaustifTest.testWorkflowDafAbsenceHoraireEtRegularisationConge()`.

### RG-PAIE-08 : Plafonnements légaux d'endettement salarié (Art. 72 & 68)
- **Énoncé Métier :**
  - **Prêts d'entreprise (Art. 72 Code du Travail)** : La quotité cessible pour le remboursement des prêts est strictement plafonnée au **tiers (33,33%)** du salaire net disponible. L'échéance excédentaire bascule en `PRELEVEE_PARTIELLEMENT` et le reliquat reste dû, sans jamais rendre le net négatif.
  - **Avances spéciales (Art. 68 Code du Travail)** : Le remboursement d'avances en espèces consenties par l'employeur est plafonné au **dixième (10%)** des salaires échus.
- **Classe & Méthode :** `PayrollExecutionEngine.calculer(...)` (Constantes `PLAFOND_QUOTITE_CESSIBLE` et `PLAFOND_AVANCE_EMPLOYEUR_DIXIEME`).
- **Classe Appelante :** `PayrollService.java`.
- **Contrôleur Backend :** `PaieController.java`.
- **Composants Frontend :** `PaieAuditModal.jsx`.
- **Test de Validation :** `AuditDeepInspectionExhaustifTest.testPlafonnementQuotiteCessiblePret()` et `testPlafonnementAvanceSpecialeDixieme()`.

### RG-PAIE-09 : Solde de Tout Compte (STC) & Balance Quittance Libératoire
- **Énoncé Métier :** Lors de la rupture du contrat, le STC liquide le dernier salaire proratisé, l'indemnité compensatrice de congés payés (ICCP), le préavis éventuel et l'indemnité légale de licenciement, tout en apurant l'intégralité du capital restant dû sur les prêts. La quittance libératoire est réconciliée à la dizaine d'Ariary la plus proche avec balance parfaite $\text{Total Brut} - \text{Total Retenues} \equiv \text{Net à Payer}$ et mentions Art. 53.
- **Classe & Méthodes :** `StcService.calculerStc(...)` et `StcService.validerStc(...)`.
- **Contrôleur Backend :** `StcController` (`/api/v1/paie/stc/preparer`, `/api/v1/paie/stc/creer`).
- **Composants Frontend :** `SoldeToutCompteTable.jsx`, `RecuStcModal.jsx`.
- **Test de Validation :** `StcIntegrationTest.java`.

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
   - La surcharge canonique `calculerAncienneteEmploye(Integer idEmploye, LocalDate dateReference)` centralise l'accès pour la supervision de carrière (`WorkflowRhService`) et pour le solde de tout compte (`StcService`), éliminant toute duplication SQL.
2. **Tests Unitaires :** Ajouter un cas de mutation multi-filiales dans [`AncienneteEngineTest.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/test/java/mg/galaxam/sirh/contrat/engine/AncienneteEngineTest.java).

---

### Scénario 5 : Modifier la tolérance de retard au pointage
*Exemple : Accorder 10 minutes de tolérance le matin sans pénalité de retard.*
1. **Backend Moteur :** Ouvrir [`PointageCalculateurEngine.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/pointage/engine/PointageCalculateurEngine.java).
   - Dans `calculerMinutesRetard(arrivee, debutTheorique, plafondTolerance)`, adapter le calcul avec une franchise : `if (Duration.between(debutTheorique, arrivee).toMinutes() > toleranceMinutes) ...`
2. **Base de Données :** Si la franchise est paramétrable, ajouter la clé `TOLERANCE_RETARD_MINUTES` dans `parametre_global`.
3. **Tests Unitaires :** Exécuter [`PointageCalculateurEngineTest.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/test/java/mg/galaxam/sirh/pointage/engine/PointageCalculateurEngineTest.java).

---

### Scénario 6 : Modifier le seuil de déclenchement des Heures Supplémentaires (Base 40h) ou la durée journalière
*Exemple : Passage d'un régime hebdomadaire 40h à 44h ou abaissement à 35h, ou passage d'une journée de 8h à 7h.*
1. **Base de Données & Configuration Légale :**
   - La durée hebdomadaire légale et les seuils HS sont gérés dans **`paie_config_legale`** :
     `UPDATE paie_config_legale SET duree_hebdomadaire_legale_heures = 35 WHERE version_loi = 'LF_2026';`
   - La durée normale quotidienne de base reste dans **`parametre_global`** (`SEUIL_HEURES_JOUR`) :
     `UPDATE parametre_global SET valeur = '7' WHERE code_parametre = 'SEUIL_HEURES_JOUR';`
2. **IHM Administrateur :**
   - Rendez-vous sur `/admin/parametres` :
     - **Onglet Configuration Légale** : pour modifier la durée hebdomadaire légale (40h) et les seuils de tranches HS.
     - **Onglet Paramètres Généraux** : pour modifier la durée normale quotidienne (`SEUIL_HEURES_JOUR`).
3. **Backend Moteur :** Vérifier que [`PointageCalculateurEngine.calculerTroncon`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/pointage/engine/PointageCalculateurEngine.java) reçoit bien la nouvelle valeur passée par `PointageSyntheseService`.
4. **Tests d'Intégration :** Réexécuter la synthèse complète dans `PointageSyntheseWorkflowIntegrationTest.java` et `StructureEtAdministrationIntegrationTest.java`.

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
   - `MONTANT_2ND_REPAS_1` = 3500
   - `MONTANT_2ND_REPAS_2` = 5000
   - `REDUCTION_DEPLACT_REPAS_MIDI` = 1500
2. **IHM Administrateur :** Rendez-vous sur `/admin/parametres` (Onglet Paramètres Généraux). Les modifications sont prises en compte immédiatement pour toute nouvelle synthèse ou saisie.
3. **Temporalité des Calculs :** `PointageSyntheseService` charge les paramètres avec `loadEtatMapAtDate(dateDebutPeriode, connection)`, garantissant que recalculer un mois passé n'appliquera pas les nouveaux montants de manière anachronique.

---

### Scénario 8 bis : Activer ou désactiver une règle opérationnelle débrayable (Repas, Transport, Congés)
*Exemple : L'entreprise décide de suspendre temporairement la déduction déplacement sur site (`REDUCTION_DEPLACT_REPAS_MIDI`) ou de supprimer le second repas du soir.*
1. **IHM Administrateur :**
   - Aller sur `/admin/parametres` (Onglet Paramètres Généraux).
   - Localiser le paramètre souhaité (ex: `REDUCTION_DEPLACT_REPAS_MIDI` ou `SEUIL_2ND_REPAS_1`).
   - Cliquer sur le commutateur rectiligne Galaxam (`Actif` / `Inactif`).
   - L'appel API `PUT /api/parametres-globaux/{id}/toggle-actif?estActif=false` journalise l'action dans `audit_log` (`DESACTIVATION`).
2. **Garde-fous Système (`est_desactivable = FALSE`) :**
   - Si un utilisateur tente de désactiver une constante indispensable (ex. `SEUIL_HEURES_JOUR`, `MONTANT_REPAS_MIDI`, `SEUIL_NUIT_DEBUT`), le backend rejette la requête avec un code HTTP 400 : *"Ce paramètre système est indispensable et ne peut pas être désactivé."*
3. **Distinction sémantique dans le code Java :**
   - **`Inactif` (`est_actif = false`)** : Décision explicite. Le calcul applique `0.0 Ar` (ou `0 jour`), sans déclencher de fallback.
   - **`Introuvable` (Clé supprimée)** : Erreur de configuration. Le calcul utilise la valeur de repli par défaut (ex. 1 000 Ar pour la déduction transport).
4. **Base de Données (Script d'urgence) :**
   `UPDATE parametre_global SET est_actif = FALSE WHERE code_parametre = 'REDUCTION_DEPLACT_REPAS_MIDI';`

---

### Scénario 9 : Modifier le pré-remplissage et l'éligibilité de la grille de pointage
*Exemple : Masquer de la grille de saisie les stagiaires ou intégrer une nouvelle catégorie d'employés.*
1. **Backend Service :** Ouvrir [`PointageSaisieService.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/pointage/service/PointageSaisieService.java).
   - Méthode `chargerGrilleHebdo` : utilise `CalendrierOuvrableEngine.getDatesFeriesEtChomeesEmploye` et filtre les salariés actifs (`est_actif = TRUE` et statut `ACTIF`).
   - Pour la consultation individuelle mensuelle, la logique est isolée dans [`PointageEmployeService.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/pointage/service/PointageEmployeService.java).
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
1. **Backend Cron :** Ouvrir [`PurgeCongeScheduler.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/cron/PurgeCongeScheduler.java).
   - Modifier le seuil de prescription calculé en Java pur :
     `LocalDate seuilPrescription = LocalDate.now().minusMonths(48);` (aucun `INTERVAL` SQL).
2. **Backend Moteur :** Le calcul mathématique dans [`PurgeFifoEngine.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/conge/engine/PurgeFifoEngine.java) reste inchangé car il est basé sur la somme des anciens droits transmis.
3. **Tests Unitaires :** Exécuter `PurgeFifoEngineTest.java`.

---

### Scénario 13 : Mettre à jour le SMIG et le plafond des cotisations CNaPS
*Exemple : Le SMIG national passe de 262 680 Ar à 300 000 Ar.*
1. **Base de Données / IHM :** Rendez-vous dans l'interface `/admin/parametres` (Onglet Configuration Légale Paie).
   - Modifier la valeur du SMIG pour la nouvelle année.
   - La table `paie_config_legale` est mise à jour.
2. **Backend Moteur :** [`CalculateurPaieEngine.calculerCnaps`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/paieconfig/engine/CalculateurPaieEngine.java) calcule automatiquement le nouveau plafond : $8 \times 300\,000 = 2\,400\,000\text{ Ar}$.

---

### Scénario 14 : Mettre à jour les tranches de l'IRSA ou le minimum de perception (CGI Art. 01.03.18)
*Exemple : La loi de finances modifie les tranches d'imposition ou porte le minimum de perception à 5 000 Ar.*
1. **Base de Données :**
   - Mettre à jour le minimum de perception dans `paie_config_legale` (`minimum_perception_irsa = 5000.00`).
   - Mettre à jour les tranches dans la table `bareme_irsa` (seuil_min, seuil_max, taux).
2. **Backend Moteur :** [`CalculateurPaieEngine.calculerIrsaDetaille`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/paieconfig/engine/CalculateurPaieEngine.java) applique uniformément le barème pour la paie mensuelle (`PayrollExecutionEngine`) et le départ salarié (`StcService`).
3. **Tests Unitaires :** Vérifier dans `CalculateurPaieEngineTest.java` et `CalculateurPaieEngineAdvancedTest.java`.

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

### Scénario 17 : Ajouter un nouveau rôle ou module applicatif (Backend + Base)
*Exemple : Créer un rôle `ROLE_DAF` ayant les droits de validation sur la paie et la consultation des autres modules.*
1. **Base de Données :**
   - Si un nouveau module est créé :
     `INSERT INTO ref_module (code_module, libelle, est_actif) VALUES ('PAIE', 'Module Paie et Salaires', true);`
   - Insérer le nouveau rôle :
     `INSERT INTO ref_role (code_role, libelle, est_actif) VALUES ('ROLE_DAF', 'Directeur Administratif et Financier', true);`
   - Configurer les permissions du rôle dans `role_permission` :
     `INSERT INTO role_permission (id_role, id_module, droit_lecture, droit_ecriture, droit_validation) VALUES (idRole, idModule, true, false, true);`
2. **Backend Sécurité :**
   - Dans [`SecurityConfig.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/config/SecurityConfig.java) : Si `ROLE_ADMIN` doit hériter de ce rôle, l'ajouter au builder `.role("ADMIN").implies(...)`.
   - Dans les Contrôleurs ciblés : Annoter les méthodes sensibles avec `@PreAuthorize("hasAuthority('PAIE_VALIDATE') or hasAuthority('ROLE_ADMIN')")`.
3. **Backend UserDetailsService :**
   - [`CustomUserDetailsService.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/securite/CustomUserDetailsService.java) génère automatiquement les `SimpleGrantedAuthority` à partir de `role_permission` (`MODULE_READ`, `MODULE_WRITE`, `MODULE_VALIDATE`).

---

### Scénario 18 : Sécuriser ou masquer un élément sensible dans l'interface Frontend (Bouton, Colonne, Donnée)
*Exemple : Masquer un montant de prime ou un bouton de validation pour les utilisateurs n'ayant pas `PAIE_WRITE`.*
1. **Option 1 : Approche Déclarative avec `<HasPermission>` (Recommandé pour les blocs JSX)**
   - Importer le composant :
     ```jsx
     import HasPermission from '@/components/auth/HasPermission';
     ```
   - Envelopper le bloc avec ou sans fallback :
     ```jsx
     {/* Masquage simple */}
     <HasPermission authority="PAIE_WRITE">
         <Button onClick={handleCalculerPaie}>Générer les Bulletins</Button>
     </HasPermission>

     {/* Masquage avec valeur de substitution */}
     <HasPermission authority="PAIE_READ" fallback={<span className="italic text-gray-400">Confidentiel</span>}>
         <span className="font-bold">{fmtMoney(salaire)} Ar</span>
     </HasPermission>
     ```
2. **Option 2 : Approche Programmatique avec `useAuth()` (Pour logique JS, requêtes, ou attributs disabled)**
   - Utiliser le hook :
     ```jsx
     const { hasPermission, hasAnyPermission, hasRole } = useAuth();
     const canWrite = hasPermission('PAIE_WRITE');
     ```
   - Conditionner les actions :
     ```jsx
     <Button disabled={!canWrite} onClick={canWrite ? handleSave : undefined}>Enregistrer</Button>
     ```

---

### Scénario 19 : Configurer ou modifier le compte administrateur initial (Bootstrap Seeder)
*Exemple : Définir un mot de passe administrateur par défaut sécurisé lors du premier déploiement en production.*
1. **Composant d'initialisation :** [`AdminAccountSeeder.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/securite/AdminAccountSeeder.java).
2. **Configuration sans modifier le code :**
   - Définir les variables d'environnement dans le conteneur ou serveur :
     - `GALAXAM_DEFAULT_ADMIN_USERNAME=superadmin`
     - `GALAXAM_DEFAULT_ADMIN_PASSWORD=MonMotDePasseComplexe2026!`
   - Ou modifier dans [`application.properties`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/resources/application.properties) :
     ```properties
     app.security.default-admin.username=superadmin
     app.security.default-admin.password=MonMotDePasseComplexe2026!
     ```
3. **Comportement au démarrage :**
   - Si un administrateur actif existe déjà en BDD, le seeder ignore l'opération.
   - Si la BDD est neuve ou sans admin, il crée le compte avec le mot de passe hashé par BCrypt.

---

# 5. Guide Opérationnel du Frontend (React / Vite) & Système de Permissions UI

Pour permettre à tout développeur ou mainteneur de naviguer aisément dans le Frontend et de le modifier sans risque de régression :

## 5.1 Structure et Cartographie des Dossiers Frontend (`frontend/src/`)

```text
frontend/src/
├── api/                   # Clients HTTP Axios et appels d'API REST
│   ├── admin/             # organisationService.js, referentielService.js
│   ├── employe/           # employeService.js, contratService.js, documentRhService.js
│   ├── conges/            # congeService.js
│   ├── pointage/          # pointageService.js, periodePaieService.js
│   ├── paie/              # paieConfigService.js (et futur paieCalculService.js)
│   └── apiClient.js       # Client Axios configuré avec intercepteurs JWT (auto-refresh)
│
├── components/            # Composants réutilisables
│   ├── auth/              # HasPermission.jsx (Composant déclaratif RBAC)
│   ├── layout/            # Layout.jsx, Sidebar.jsx, Topbar.jsx, Footer.jsx
│   ├── carriere/          # Dossier carrière : timeline, formulaires d'avenant, documents officiels
│   │   ├── timeline/      # ContratActifSection.jsx, ParcoursChronologique.jsx, HistoriqueContratsTable.jsx
│   │   ├── form/          # AffectationFieldsSection.jsx, ContratFieldsSection.jsx
│   │   └── supervision/   # SupervisionMouvementsTab.jsx
│   ├── employe/           # EmployeHeaderCard.jsx, EmployeTable.jsx, EmployeStatsCards.jsx
│   ├── conges/            # DemandesValidationTab.jsx, DemandeCongeModal.jsx, soldes/
│   ├── pointage/          # PointageTable.jsx, PeriodNavigator.jsx
│   ├── dashboard/         # Widgets et grilles KPI du tableau de bord
│   └── ui/                # Bibliothèque UI Atomique (Button, Modal, Table, Badge, Alert, Input, etc.)
│
├── context/               # Contextes React globaux
│   └── AuthContext.jsx    # État utilisateur, JWT, rôles, hasPermission, hasAnyPermission, hasRole
│
├── hooks/                 # Custom Hooks React métier
│   ├── useAuth.js         # Raccourci vers useAuth()
│   ├── useMenu.js         # Chargement dynamique des menus autorisés depuis l'API
│   ├── useDashboard.js    # Récupération et polling des indicateurs RH
│   ├── useContractReferentiels.js # Cache des listes déroulantes (filiales, sites, fonctions, etc.)
│   └── useDebounce.js     # Débounce pour barres de recherche
│
├── pages/                 # Écrans principaux associés aux routes
│   ├── auth/              # Login.jsx, Logout.jsx
│   ├── dashboard/         # Dashboard.jsx
│   ├── employe/           # EmployeList.jsx, EmployeFicheDetailPage.jsx, CarrieresSupervisionPage.jsx
│   ├── conges/            # GestionCongesPage.jsx
│   ├── pointage/          # SaisieQuotidiennePage.jsx, AnomaliesPointagePage.jsx, PeriodePaiePage.jsx, SynthesePointagePage.jsx
│   └── parametres/        # Panels d'administration (Organisation, Sécurité, Paramètres modules)
│
├── routes/                # Routage de l'application
│   ├── AppRoutes.jsx      # Définition des routes et URLs
│   └── ProtectedRoute.jsx # Garde de route avec contrôle de permissions
│
└── utils/                 # Utilitaires purs
    ├── carriereUtils.js   # Formatage dates, salaires, devises Ariary
    ├── fileUtils.js       # Traitement fichiers, formats acceptés, prévisualisations
    └── dateUtils.js       # Manipulation de calendriers et jours ouvrables
```

## 5.2 Système de Contrôle d'Accès Frontend (`useAuth` vs `<HasPermission />`)

Le frontend dispose de deux mécanismes complémentaires pour gérer les autorisations :

### 1. Le Hook `useAuth()` (Logique JS)
Utilisé pour les conditions impératives dans les fonctions, les redirections, ou l'attribut `disabled` des boutons :
```jsx
import { useAuth } from '@/context/AuthContext';

function MonComposant() {
    const { user, hasPermission, hasAnyPermission, hasAllPermissions, hasRole } = useAuth();

    const canEdit = hasPermission('EMPLOYE_WRITE');
    const isDAF = hasRole('ROLE_DAF');

    const handleAction = () => {
        if (!canEdit) {
            alert("Action non autorisée");
            return;
        }
        // ...
    };

    return <Button disabled={!canEdit} onClick={handleAction}>Modifier</Button>;
}
```

### 2. Le Composant Déclaratif `<HasPermission />` (Structure JSX)
Utilisé pour rendre ou masquer des blocs entiers, des cartes ou des colonnes sans polluer le JSX avec des conditions ternaires :
```jsx
import HasPermission from '@/components/auth/HasPermission';

// Affiche le bouton uniquement si l'utilisateur possède la permission
<HasPermission authority="EMPLOYE_WRITE">
    <Button onClick={openModal}>Ajouter un collaborateur</Button>
</HasPermission>

// Masque une donnée confidentielle avec un texte de remplacement
<HasPermission authority="PAIE_READ" fallback={<span className="italic">Confidentiel</span>}>
    <span>{fmtMoney(salaire)} Ar</span>
</HasPermission>

// Vérifie la présence d'au moins une permission parmi une liste
<HasPermission any={['PAIE_READ', 'PAIE_WRITE']}>
    <OngletPaie />
</HasPermission>

// Contrôle par rôle spécifique
<HasPermission role="ROLE_ADMIN">
    <PanelAdministration />
</HasPermission>
```

> **Règle d'or :** L'utilisateur `ROLE_ADMIN` est automatiquement évalué à `true` sur toutes les vérifications de permissions (principe de super-administrateur).

---

## 5.3 Matrice Complète des Permissions et Impacts sur l'Interface

| Module | Code Permission | Pages / Vues impactées | Actions / Données contrôlées dans l'UI |
| :--- | :--- | :--- | :--- |
| **EMPLOYÉ** | `EMPLOYE_READ` | `/employes`, `/employes/:id`, `/carrieres/supervision` | Consultation du répertoire, fiches salariés, documents et historique de carrière. |
| **EMPLOYÉ** | `EMPLOYE_WRITE` | `/employes` (Bouton Nouvel Employé), Fiche détail, Modales de mouvements | Création d'employé, modification des coordonnées, téléversement de documents RH, saisie d'avenants/embauches (brouillons). |
| **EMPLOYÉ** | `EMPLOYE_VALIDATE` | `/dashboard` (Widget Brouillons), Fiche détail, Modale de validation | Approbation définitive des mouvements de carrière, désactivation / réactivation d'un employé. |
| **CONGÉ** | `CONGE_READ` | `/conges`, Fiche détail (Onglet Congés) | Consultation des plannings d'absences, soldes et historiques de demandes. |
| **CONGÉ** | `CONGE_WRITE` | `/conges` (Bouton Nouvelle Demande) | Soumission d'une demande de congé, modification/annulation d'une demande en attente. |
| **CONGÉ** | `CONGE_VALIDATE` | `/dashboard` (Widget Congés à valider), `/conges` (Onglet Validation) | Boutons d'approbation et de refus des demandes de congés. |
| **POINTAGE** | `POINTAGE_READ` | `/pointages/saisie`, `/pointages/anomalies`, `/pointages/synthese` | Consultation de la grille journalière, des anomalies et des cumuls d'heures. |
| **POINTAGE** | `POINTAGE_WRITE` | `/pointages/saisie`, `/pointages/anomalies` | Saisie des heures d'arrivée/départ, déclaration des pauses et des motifs de retard. |
| **POINTAGE** | `POINTAGE_VALIDATE` | `/pointages/periodes`, `/pointages/synthese` | Verrouillage/clôture de période de pointage, signature/validation DAF pour export paie. |
| **ORGANISATION** | `ORGANISATION_READ` | `/parametres/organisation` | Consultation des filiales, sites, départements, services et fonctions. |
| **ORGANISATION** | `ORGANISATION_WRITE` | `/parametres/organisation` | Création, modification, activation et désactivation des structures organisationnelles. |
| **PAIE** | `PAIE_READ` | Carrière ([CarriereHeaderCard](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/components/carriere/CarriereHeaderCard.jsx), [ContratActifSection](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/components/carriere/timeline/ContratActifSection.jsx)), [Supervision](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/components/carriere/supervision/SupervisionMouvementsTab.jsx), Contrats | Affichage en clair du salaire de base, des révisions salariales et des paramètres fiscaux (IRSA, CNaPS). |
| **PAIE** | `PAIE_WRITE` | `/parametres/paie`, Futur module de paie | Modification des barèmes légaux, calcul des salaires bruts/nets, génération des bulletins. |

---

## 5.4 Comment Sécuriser / Masquer un Nouveau Champ ou Bouton Sensible

### Cas pratique 1 : Masquer une colonne dans un tableau
```jsx
// 1. Importer useAuth ou HasPermission
import { useAuth } from '@/context/AuthContext';
import HasPermission from '@/components/auth/HasPermission';

function MonTableau({ items }) {
    const { hasPermission } = useAuth();
    const canSeePaie = hasPermission('PAIE_READ');

    return (
        <Table>
            <Thead>
                <Tr>
                    <Th>Employé</Th>
                    {/* N'afficher l'en-tête que si autorisé */}
                    {canSeePaie && <Th>Salaire Brut</Th>}
                </Tr>
            </Thead>
            <Tbody>
                {items.map(item => (
                    <Tr key={item.id}>
                        <Td>{item.nom}</Td>
                        {canSeePaie && (
                            <Td className="font-data">{fmtMoney(item.salaire)} Ar</Td>
                        )}
                    </Tr>
                ))}
            </Tbody>
        </Table>
    );
}
```

### Cas pratique 2 : Protéger un bouton d'action avec un fallback informatif
```jsx
<HasPermission 
    authority="EMPLOYE_WRITE" 
    fallback={<span className="text-xs text-gray-400 italic">Droits insuffisants pour modifier</span>}
>
    <Button onClick={handleEdit} variant="outline" size="sm">
        Modifier la fiche
    </Button>
</HasPermission>
```

---

## 5.5 Comment Créer une Nouvelle Page ou un Nouvel Onglet Protégé

1. **Créer la vue** dans `src/pages/mon-module/MaNouvellePage.jsx`.
2. **Déclarer la route protégée** dans `src/routes/AppRoutes.jsx` :
   ```jsx
   <Route 
       path="/mon-module/page" 
       element={
           <ProtectedRoute requiredPermission="MON_MODULE_READ">
               <MaNouvellePage />
           </ProtectedRoute>
       } 
   />
   ```
3. **Configurer le menu** dans l'Administration (`/parametres/menus` ou table `ref_menu`) : lier le chemin `/mon-module/page` au module correspondant pour qu'il apparaisse automatiquement dans la Sidebar des profils autorisés.

---

# 6. Checklist Qualité lors d'une Modification Métier

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
    Les permissions nécessaires sont-elles appliquées via <HasPermission> ou useAuth() ?

[ ] 5. CONCURRENCE & TRANSACTIONS
    Si la règle modifie un solde ou un statut critique, le verrouillage pessimiste (FOR UPDATE) et le rollback transactionnel sont-ils garantis ?

[ ] 6. DOCUMENTATION
    Le présent document (GUIDE_REGLES_METIER_ET_MODIFICATIONS.md) a-t-il été mis à jour avec le nouveau comportement ?
```

---
*Document certifié conforme à la structure logicielle GALAXAM-SIRH.*
