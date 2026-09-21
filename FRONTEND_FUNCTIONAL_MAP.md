# CARTOGRAPHIE FONCTIONNELLE COMPLÈTE DU FRONTEND — SIRH GALAXAM

> **Document de référence :** Audit exhaustif des routes, composants, APIs consommées, rôles, actions utilisateur, états et diagnostics d'intégrité du frontend SIRH GALAXAM.  
> **Date :** 20 Septembre 2026  
> **Statut :** Cartographie exhaustive & Matrice d'adéquation Backend ↔ UI  

---

## 1. Vue d'Ensemble des Modules & Rôles

Le frontend SIRH GALAXAM s'articule autour de deux rôles principaux sécurisés par RBAC :
1. **`ROLE_ADMIN` (DAF / DRH / Administrateur Système)** : Accès intégral à tous les modules : Tableaux de bord, Personnel & Carrières, Congés & Absences, Pointage & Clôture DAF, Campagnes de Paie complètes (Étapes 1 à 7), Déclarations & Sorties réglementaires, Paramètres organisationnels et Sécurité.
2. **`ROLE_AGENT_POINTAGE` (Agent d'Exploitation & Pointage)** : Accès strictement restreint à la gestion opérationnelle du pointage : Saisie quotidienne des présences, Suivi des anomalies de badgeage/retards, Contrôle de complétude et Transmission à la DAF. Tout accès à la paie, aux rémunérations, aux taux d'impôts et aux configurations sensibles est hermétiquement verrouillé à la fois côté UI (masquage/navigation) et côté API (rejet HTTP 403 Forbidden).

---

## 2. Inventaire Détaillé Page par Page

### Module A : Authentification & Système

#### PAGE A.1 : Connexion
- **ROUTE :** `/`
- **MODULE :** Authentification
- **PAGE :** `Login.jsx`
- **ROLE :** Public (Hors session)
- **PURPOSE :** Authentifier les utilisateurs et générer les tokens JWT (Access Token en mémoire / Cookie HTTP-Only Refresh).
- **APIS_USED :** `POST /auth/login`
- **INPUTS :** Identifiant (matricule ou email), Mot de passe.
- **OUTPUTS :** Redirection vers `/dashboard` (pour `ROLE_ADMIN`) ou `/pointages` (pour `ROLE_AGENT_POINTAGE`).
- **AVAILABLE_ACTIONS :** Soumettre le formulaire, réinitialiser.
- **EXPECTED_STATES :** Saisie, Envoi (loading), Erreur identifiants, Succès.
- **CURRENT_PROBLEMS :** Aucun problème majeur. Gestion des erreurs HTTP 401/403 déjà traduite.

#### PAGE A.2 : Non Autorisé (403) & Erreurs Système
- **ROUTE :** `/non-autorise`, `/maintenance`, `/hors-ligne`, `/erreur-serveur`, `/*`
- **MODULE :** Système
- **PAGE :** `Unauthorized.jsx`, `Maintenance.jsx`, `NoConnection.jsx`, `ServerError.jsx`, `NotFound.jsx`
- **ROLE :** Tous
- **PURPOSE :** Information claire de l'utilisateur avec conservation du Layout (sidebar et profil) si connecté.
- **APIS_USED :** N/A
- **INPUTS :** N/A
- **OUTPUTS :** Message explicatif et bouton de retour.
- **AVAILABLE_ACTIONS :** Retour au tableau de bord, reconnexion.
- **EXPECTED_STATES :** Statique.
- **CURRENT_PROBLEMS :** Conforme au Design System Sauge.

---

### Module B : Tableau de Bord & Alertes

#### PAGE B.1 : Dashboard Principal
- **ROUTE :** `/dashboard`
- **MODULE :** Dashboard
- **PAGE :** `Dashboard.jsx`
- **ROLE :** `ROLE_ADMIN` (Permission `EMPLOYE_READ`)
- **PURPOSE :** Supervision globale de l'entreprise : effectifs, alertes RH, congés en cours, statut du pointage et échéances de paie.
- **APIS_USED :** `GET /dashboard/kpis`, `GET /dashboard/alertes`, `GET /dashboard/recents`, `GET /notifications`
- **INPUTS :** Filtre de période ou date d'observation.
- **OUTPUTS :** Cartes KPIs (Effectif actif, Absents du jour, Période active, Masse salariale M-1), Graphiques d'absentéisme.
- **AVAILABLE_ACTIONS :** Clic sur widget pour naviguer vers le module correspondant, marquer alertes comme lues.
- **EXPECTED_STATES :** Chargement (Skeletons), Données affichées, État vide.
- **CURRENT_PROBLEMS :** Vérifier que les montants financiers agrégés proviennent directement du backend et non d'une estimation frontend.

---

### Module C : Pointage & Présences (Cockpit Unifié)

#### PAGE C.1 : Étape 1 — Saisie Quotidienne
- **ROUTE :** `/pointages?tab=saisie`
- **MODULE :** Pointage
- **PAGE :** `SaisieQuotidiennePage.jsx`
- **ROLE :** `ROLE_AGENT_POINTAGE`, `ROLE_ADMIN`
- **PURPOSE :** Saisie et contrôle des heures travaillées, arrivées, départs, pauses déduites et indemnités de repas/déplacement au jour le jour.
- **APIS_USED :** `GET /pointages/saisie/grille`, `POST /pointages/saisie/save`, `GET /referentiel/sites`, `GET /periodes/current`
- **INPUTS :** Sélecteur de date (calendrier), Sélecteur de site, Tableau des salariés avec champs `Heure Arrivée`, `Pause Début/Fin`, `Heure Départ`.
- **OUTPUTS :** Heures travaillées calculées par le backend, Statut journalier (Présent, Retard, Demi-journée, Absent), Détection férié.
- **AVAILABLE_ACTIONS :** 
  - Pré-remplir heures standard (8h-17h)
  - Déduire pauses automatiques
  - Modifier les statuts manuellement avec motif
  - Sauvegarder en masse
  - Raccourcis clavier (flèches de direction)
- **EXPECTED_STATES :** 
  - Normal (Saisie active)
  - Date future (Verrouillée avec alerte d'intégrité temporelle)
  - Période fermée/clôturée (Lecture seule)
  - Erreurs de validation de format d'heure
- **CURRENT_PROBLEMS :** 
  - L'intégrité temporelle bloque correctement les dates futures.
  - S'assurer que le format d'heure affiché utilise rigoureusement `X h YY min` et non un nombre décimal ambigu.

#### PAGE C.2 : Étape 2 — Suivi des Semaines & Anomalies
- **ROUTE :** `/pointages?tab=suivi` (alias `/pointages/anomalies`)
- **MODULE :** Pointage
- **PAGE :** `SuiviAnomaliesPage.jsx`
- **ROLE :** `ROLE_AGENT_POINTAGE`, `ROLE_ADMIN`
- **PURPOSE :** Suivre la progression de saisie semaine par semaine (S1 à S5), lister les anomalies détectées (retards non justifiés, pointages incomplets, absences) et les régulariser avant transmission à la DAF.
- **APIS_USED :** `GET /pointages/anomalies`, `POST /pointages/anomalies/{id}/regulariser`, `GET /pointages/synthese/semaines`, `POST /periodes/{id}/commands` (SUBMIT)
- **INPUTS :** Filtres par semaine (S1..S5), sévérité (Bloquant / Avertissement), type d'anomalie, recherche par collaborateur.
- **OUTPUTS :** Cartes d'avancement hebdomadaire avec pourcentages et statuts, Tableau paginé des anomalies.
- **AVAILABLE_ACTIONS :** 
  - Régulariser une anomalie (modal avec motif et nouveau statut)
  - Transmettre la période à la DAF (action `SUBMIT`)
- **EXPECTED_STATES :** 
  - Anomalies résolues (Prêt pour transmission)
  - Anomalies bloquantes restantes (Bouton transmission désactivé avec explication du blocage)
  - Période transmise (En attente d'arbitrage DAF)
- **CURRENT_PROBLEMS :** 
  - Expliciter clairement dans le message de blocage de transmission le nombre exact d'anomalies non arbitrées ou semaines incomplètes.

#### PAGE C.3 : Étape 3 — Synthèse & Arbitrage DAF
- **ROUTE :** `/pointages?tab=validation` (alias `/pointages/arbitrage`, `/pointages/synthese`)
- **MODULE :** Pointage
- **PAGE :** `ArbitrageDafPage.jsx`
- **ROLE :** `ROLE_ADMIN` (Validation/Rejet/Ajustement) + `ROLE_AGENT_POINTAGE` (Consultation & Contrôle)
- **PURPOSE :** Examiner les cumuls mensuels consolidés par collaborateur (Total heures, HS30, HS50, Nuit, Dimanche, Férié, Indemnités repas et déplacement) et arbitrer officiellement la période (Valider pour figer le snapshot, ou Rejeter avec motif de correction).
- **APIS_USED :** `GET /pointages/synthese`, `GET /pointages/synthese/semaines`, `POST /pointages/synthese/recalculer-tout`, `POST /periodes/{id}/commands` (APPROVE, REJECT)
- **INPUTS :** Sélecteur de période, filtre par site, recherche collaborateur, filtres d'alertes (HS, Nuit, Retards, Absences).
- **OUTPUTS :** Totaux généraux de période (KPIs), Tableau consolidé salarié par salarié, Vue Gantt détaillée par semaine.
- **AVAILABLE_ACTIONS :** 
  - Valider la période (Définitif $\to$ Statut `VALIDEE`, fige le snapshot de pointage)
  - Rejeter la période vers l'Agent avec motif obligatoire
  - Ajuster une ligne de pointage (DAF uniquement)
  - Recalculer les cumuls
  - Basculer vue Synthèse / vue Gantt
- **EXPECTED_STATES :** 
  - En attente de validation
  - Validée / Scellée (Lecture seule)
  - Rejetée (Réouverte pour l'agent)
- **CURRENT_PROBLEMS DÉTECTÉS & CORRIGÉS :** 
  - **Bloqueur RBAC levé :** La page affichait un écran verrouillé ("Accès restreint") pour l'Agent de pointage. L'Agent doit pouvoir consulter la synthèse et contrôler les totaux sans pouvoir valider ou rejeter. La page doit donc supporter un mode "Consultation Agent" sécurisé.

---

### Module D : Congés & Absences

#### PAGE D.1 : Soldes & Planning Prévisionnel
- **ROUTE :** `/conges` (alias `/conges?tab=soldes`)
- **MODULE :** Congés
- **PAGE :** `GestionCongesPage.jsx` (sous-composant `SoldesOverviewTab.jsx`)
- **ROLE :** `ROLE_ADMIN` (Permission `CONGE_READ`)
- **PURPOSE :** Supervision des soldes acquis, pris et restants de l'ensemble des collaborateurs, consultation du planning prévisionnel d'équipe (vue Gantt mensuelle / hebdomadaire).
- **APIS_USED :** `GET /conges/soldes`, `GET /conges/planning`, `GET /referentiel/conges/types`
- **INPUTS :** Navigation par mois/semaine, filtre par collaborateur, filtre par statut.
- **OUTPUTS :** Cartes KPIs de présence, Grille Gantt interactive, Tableau des absences du mois.
- **AVAILABLE_ACTIONS :** Nouvelle demande de congé, ajustement de solde, export.
- **EXPECTED_STATES :** Chargé, En attente, Erreur.
- **CURRENT_PROBLEMS :** 
  - Distinction visuelle stricte entre jours de congés (`j`) et heures d'absence (`h min`).

#### PAGE D.2 : Demandes & Approbations
- **ROUTE :** `/conges?tab=demandes`
- **MODULE :** Congés
- **PAGE :** `DemandesValidationTab.jsx`
- **ROLE :** `ROLE_ADMIN` (Permissions `CONGE_WRITE`, `CONGE_VALIDATE`)
- **PURPOSE :** Traitement du flux des demandes de congés et autorisations d'absence : vérification de préavis, calcul du décompte en jours ouvrables/ouvrés, détection des conflits de poste et validation avec mise à jour immédiate du ledger de solde.
- **APIS_USED :** `GET /conges/demandes`, `POST /conges/demandes/{id}/valider`, `POST /conges/demandes/{id}/refuser`, `POST /conges/demandes/{id}/annuler`
- **INPUTS :** Filtre par statut (En attente, Validé, Refusé, Annulé), recherche texte, formulaire de décision (motif refus, option rémunéré / sans solde, dérogation solde négatif).
- **OUTPUTS :** Liste paginée des demandes avec statuts badges et détail des jours décomptés.
- **AVAILABLE_ACTIONS :** Valider, Refuser, Annuler, Modifier les dates.
- **EXPECTED_STATES :** En attente, Validé, Refusé, Annulé.
- **CURRENT_PROBLEMS DÉTECTÉS & CORRIGÉS :** 
  - La modale de validation ne montrait pas le récapitulatif comptable : `Solde avant`, `Jours demandés`, `Jours débités`, `Solde après`, `Impact paie`. Cet encart d'audit doit être systématiquement affiché.

---

### Module E : Campagne de Paie & Rémunération (Workflow Linéaire 7 Étapes)

#### PAGE E.1 : Gestion Globale & Historique des Périodes
- **ROUTE :** `/paie` (alias `/paie?tab=periodes`)
- **MODULE :** Paie
- **PAGE :** `GestionPaiePage.jsx` / `PeriodePaiePage.jsx`
- **ROLE :** `ROLE_ADMIN`
- **PURPOSE :** Vue d'ensemble des exercices de paie passés et actuels, ouverture d'une nouvelle période mensuelle, accès direct au cockpit de campagne.
- **APIS_USED :** `GET /periodes`, `POST /periodes`, `GET /periodes/context`
- **INPUTS :** Code période (`YYYY-MM`), Dates de début et fin, Libellé.
- **OUTPUTS :** Liste des périodes avec états (`EN_COURS`, `VALIDEE`, `PAIE_VALIDEE`, `CLOTUREE`), nombre de bulletins, volume net.
- **AVAILABLE_ACTIONS :** Créer période, Ouvrir campagne active, Consulter historique.
- **EXPECTED_STATES :** Actif, Clôturé.
- **CURRENT_PROBLEMS :** Conforme.

#### PAGE E.2 : Étape 1 — Préparation de la Campagne
- **ROUTE :** `/paie/periodes/:id/preparation`
- **MODULE :** Paie
- **PAGE :** `PreparationStepPage.jsx`
- **ROLE :** `ROLE_ADMIN`
- **PURPOSE :** Contrôle des prérequis fondamentaux avant de lancer la campagne : statut du pointage (scellé / figé), validité de la configuration fiscale IRSA active, référentiels de cotisations CNaPS et santé, contrats actifs.
- **APIS_USED :** `GET /periodes/context?periodId={id}`, `GET /paie/config-legale/active-at`
- **INPUTS :** Période sélectionnée.
- **OUTPUTS :** Check-list des prérequis avec badges de conformité, Résumé des paramètres fiscaux et sociaux applicables à la période.
- **AVAILABLE_ACTIONS :** Rafraîchir les contrôles, Passer à l'étape suivante si prérequis satisfaits.
- **EXPECTED_STATES :** 
  - Conforme (Prêt pour population)
  - Bloqué (Pointage non figé par la DAF)
- **CURRENT_PROBLEMS DÉTECTÉS & CORRIGÉS :** 
  - **Données réglementaires codées en dur :** Remplacement des libellés textuels statiques par l'appel dynamique à `paieConfigService.getConfigLegaleAt(...)` afin d'afficher les valeurs réelles (Plafond CNaPS, SME, seuil exonération IRSA, taux légaux).

#### PAGE E.3 : Étape 2 — Population Éligible
- **ROUTE :** `/paie/periodes/:id/population`
- **MODULE :** Paie
- **PAGE :** `PopulationStepPage.jsx`
- **ROLE :** `ROLE_ADMIN`
- **PURPOSE :** Revue de l'ensemble des collaborateurs inclus dans la campagne de paie, vérification des contrats actifs, dates d'entrée/sortie, temps de travail contractuel (173.33h) et salaires de base.
- **APIS_USED :** `GET /employes`, `GET /periodes/context`
- **INPUTS :** Filtre par site, département, recherche collaborateur.
- **OUTPUTS :** Tableau exhaustif des salariés avec contrat, salaire contractuel, poste et statut d'éligibilité.
- **AVAILABLE_ACTIONS :** Exclure/inclure un collaborateur le cas échéant, naviguer vers la fiche employé.
- **EXPECTED_STATES :** Effectif validé.
- **CURRENT_PROBLEMS :** Conforme.

#### PAGE E.4 : Étape 3 — Variables & Primes de Paie
- **ROUTE :** `/paie/periodes/:id/variables`
- **MODULE :** Paie
- **PAGE :** `VariablesStepPage.jsx` (intégrant `VariablesPaieTab.jsx`)
- **ROLE :** `ROLE_ADMIN`
- **PURPOSE :** Saisie et contrôle de toutes les rubriques variables mensuelles : primes exceptionnelles, primes d'assiduité/performance, acomptes de quinzaine, avances spéciales 1/10ème, retenues diverses (call perso) et régularisations de congés.
- **APIS_USED :** `GET /paie/ajustements`, `POST /paie/ajustements`, `DELETE /paie/ajustements/{id}`, `GET /paie/ajustements/types-mouvements`
- **INPUTS :** Collaborateur, Type de variable (issu de `ref_type_mouvement_paie`), Montant en Ariary, Motif, Mode de règlement.
- **OUTPUTS :** Synthèse globale (Total Gains variables, Total Retenues nettes), Tableau filtrable des variables.
- **AVAILABLE_ACTIONS :** Ajouter variable, Approuver, Rejeter, Supprimer.
- **EXPECTED_STATES :** Modifiable (si période non clôturée), Lecture seule / Verrouillé (si validée ou clôturée).
- **CURRENT_PROBLEMS DÉTECTÉS & CORRIGÉS :** 
  - Code par défaut obsolète `PRIM_EXC` corrigé vers les codes réels de BDD (`PRIME_EXCEPTIONNELLE`, `ACOMPTE_15`, etc.).
  - Blocage explicite des actions d'ajout/suppression si la période est clôturée.

#### PAGE E.5 : Étape 4 — Calcul & Simulation de Paie
- **ROUTE :** `/paie/periodes/:id/calcul`
- **MODULE :** Paie
- **PAGE :** `CalculStepPage.jsx`
- **ROLE :** `ROLE_ADMIN`
- **PURPOSE :** Exécution du moteur de paie en mode simulation (prévisionnel sans impact BDD) ou en mode calcul réel officiel (génération et persistance des bulletins de paie et lignes associées).
- **APIS_USED :** `POST /paie/calculer` (`simulation=true` ou `false`), `GET /paie/cockpit/{id}`
- **INPUTS :** Période active.
- **OUTPUTS :** 
  - KPIs globaux : Masse brute, Cotisations salariales, Retenue IRSA, Net à payer total, Charges patronales.
  - **Tableau de simulation individuelle avec cascade financière détaillée** pour chaque collaborateur.
- **AVAILABLE_ACTIONS :** Simuler (sans persistance), Lancer le calcul réel, Déplier le détail d'un collaborateur.
- **EXPECTED_STATES :** 
  - Prêt pour calcul (Pointage validé)
  - Bloqué (Pointage non figé $\to$ bouton désactivé avec message d'explication)
  - Recalcul recommandé (si variables modifiées après calcul)
- **CURRENT_PROBLEMS DÉTECTÉS & CORRIGÉS :** 
  - **Ajout de la cascade financière individuelle :** La simulation affichait uniquement les StatCards globales. L'UI intègre désormais un tableau dépliable permettant de vérifier pour chaque collaborateur la cascade exacte :
    $$\text{Base} - \text{Absence} - \text{Déduction Congé} + \text{Indemnité Congé} + \text{HS} + \text{Primes} = \text{Brut}$$
    $$\text{Brut} - \text{CNaPS} - \text{Santé} = \text{Net Imposable}$$
    $$\text{Net Imposable} - \text{IRSA} - \text{Avances/Acomptes} = \text{Net à Payer}$$

#### PAGE E.6 : Étape 5 — Contrôle & Revue des Bulletins
- **ROUTE :** `/paie/periodes/:id/controle`
- **MODULE :** Paie
- **PAGE :** `ControleStepPage.jsx`
- **ROLE :** `ROLE_ADMIN`
- **PURPOSE :** Revue détaillée des bulletins calculés, détection des anomalies de calcul, comparaison d'écarts avec M-1, ouverture du bulletin complet dans le drawer latéral pour analyse fine.
- **APIS_USED :** `GET /paie/cockpit/{id}`, `GET /paie/bulletins/{id}`
- **INPUTS :** Filtre par statut d'alerte, recherche salarié, sélection d'un bulletin.
- **OUTPUTS :** Tableau de contrôle avec colonnes financières canoniques, Drawer latéral affichant l'intégralité des rubriques du bulletin scellé.
- **AVAILABLE_ACTIONS :** Consulter bulletin, Recalculer individuellement un bulletin, Exporter l'état de contrôle CSV.
- **EXPECTED_STATES :** Bulletins calculés conformes, Alertes d'écart M-1 signalées.
- **CURRENT_PROBLEMS :** Conforme.

#### PAGE E.7 : Étape 6 — Validation & Clôture
- **ROUTE :** `/paie/periodes/:id/validation`
- **MODULE :** Paie
- **PAGE :** `ValidationStepPage.jsx`
- **ROLE :** `ROLE_ADMIN`
- **PURPOSE :** Décision formelle de la DAF : approbation de l'ensemble des bulletins (`CALCULE` $\to$ `VALIDE`), passage de la période à `PAIE_VALIDEE`, puis clôture définitive et irréversible (`CLOTUREE`).
- **APIS_USED :** `POST /paie/valider`, `POST /periodes/{id}/commands` (`CLOSE`, `REOPEN`)
- **INPUTS :** Période active, confirmation de clôture, motif obligatoire en cas de réouverture exceptionnelle.
- **OUTPUTS :** Statut officiel de la période, Horodatage de validation, Immuabilité des données.
- **AVAILABLE_ACTIONS :** Approuver tous les bulletins, Clôturer définitivement la période, Rouvrir avec motif.
- **EXPECTED_STATES :** 
  - Prêt pour approbation (Tous bulletins calculés)
  - Prêt pour clôture (Bulletins approuvés)
  - Clôturée définitivement (Lecture seule absolue)
- **CURRENT_PROBLEMS :** Conforme.

#### PAGE E.8 : Étape 7 — Déclarations & Sorties Réglementaires
- **ROUTE :** `/paie/periodes/:id/sorties`
- **MODULE :** Paie
- **PAGE :** `SortiesStepPage.jsx` (intégrant `ExportsPaieTab.jsx`)
- **ROLE :** `ROLE_ADMIN`
- **PURPOSE :** Génération, prévisualisation et téléchargement de l'ensemble des livrables légaux et bancaires de la période.
- **APIS_USED :** 
  - `GET /paie/exports/{id}/safi-csv` & `safi-ods` (État fiscal 301 - 25 colonnes DGI)
  - `GET /paie/exports/{id}/dnts-csv` (Bordereau CNaPS / FMFP)
  - `GET /paie/exports/{id}/virement-csv` (Ordres de virement bancaires)
  - `GET /paie/exports/{id}/edi-isi` (XML DGI télé-déclaration prestataires)
- **INPUTS :** Période active clôturée ou validée.
- **OUTPUTS :** Fichiers téléchargés conformes aux normes DGI / CNaPS / Banques, Checksum de certification.
- **AVAILABLE_ACTIONS :** Télécharger CSV/ODS/XML, Prévisualiser les données tabulaires.
- **EXPECTED_STATES :** Disponible (Période validée/clôturée), Verrouillé avec explication si non validée.
- **CURRENT_PROBLEMS :** Conforme.

---

### Module F : Employés, Dossiers RH & Carrières

#### PAGE F.1 : Liste des Salariés
- **ROUTE :** `/employes`
- **MODULE :** Personnel
- **PAGE :** `EmployeList.jsx`
- **ROLE :** `ROLE_ADMIN` (Permission `EMPLOYE_READ`)
- **PURPOSE :** Annuaire complet des collaborateurs de l'entreprise avec filtres par site, département, type de contrat et statut d'activité.
- **APIS_USED :** `GET /employes`, `POST /employes`
- **INPUTS :** Recherche matricule/nom, filtres multicritères, pagination.
- **OUTPUTS :** Cartes effectifs, Tableau des salariés avec badge de statut et accès fiche détaillée.
- **AVAILABLE_ACTIONS :** Créer un employé (modal multi-étapes), Exporter liste, Consulter fiche.
- **EXPECTED_STATES :** Normal, Chargement, Erreur.
- **CURRENT_PROBLEMS :** Conforme.

#### PAGE F.2 : Fiche Détail Salarié
- **ROUTE :** `/employes/:id` (avec onglets `dossier`, `carriere`, `conges`, `pointage`, `paie`)
- **MODULE :** Personnel
- **PAGE :** `EmployeFicheDetailPage.jsx`
- **ROLE :** `ROLE_ADMIN`
- **PURPOSE :** Consultation exhaustive du dossier RH individuel : état civil, situation de famille, historique des contrats et avenants, solde et historique des congés, relevé des pointages, historique des bulletins de paie et STC.
- **APIS_USED :** `GET /employes/{id}`, `GET /carrieres/employe/{id}`, `GET /conges/soldes/{id}`, `GET /paie/bulletins/employe/{id}`
- **INPUTS :** ID employé.
- **OUTPUTS :** Fiche complète multi-onglets avec timeline de carrière et documents RH.
- **AVAILABLE_ACTIONS :** Modifier données, Ajouter contrat, Calculer STC en cas de rupture, Imprimer bulletin.
- **EXPECTED_STATES :** Salarié actif, En période d'essai, Contrat terminé.
- **CURRENT_PROBLEMS :** Conforme.

#### PAGE F.3 : Supervision des Carrières & Contrats
- **ROUTE :** `/carrieres`
- **MODULE :** Personnel
- **PAGE :** `CarrieresSupervisionPage.jsx`
- **ROLE :** `ROLE_ADMIN`
- **PURPOSE :** Pilotage des échéances contractuelles : fins de période d'essai, fins de CDD, alertes de requalification légale en CDI (Code du Travail Malgache Art. 19 & 20).
- **APIS_USED :** `GET /carrieres/echeances`, `POST /carrieres/transitions`
- **INPUTS :** Filtre par type d'alerte (Essai à confirmer, CDD à renouveler, Requalification).
- **OUTPUTS :** Tableau des alertes d'échéances avec compte à rebours en jours.
- **AVAILABLE_ACTIONS :** Confirmer essai, Renouveler contrat, Établir avenant, Notifier rupture.
- **EXPECTED_STATES :** À jour, Échéances imminentes, En retard.
- **CURRENT_PROBLEMS :** Conforme.

---

### Module G : Administration & Paramétrage

#### PAGE G.1 : Sécurité & Droits d'Accès
- **ROUTE :** `/admin/securite`
- **MODULE :** Administration
- **PAGE :** `SecuriteAdminPanel.jsx`
- **ROLE :** `ROLE_ADMIN`
- **PURPOSE :** Gestion des comptes utilisateurs, attributions des rôles (`ROLE_ADMIN`, `ROLE_AGENT_POINTAGE`), gestion de l'arbre des menus et permissions granulaires.
- **APIS_USED :** `GET /admin/utilisateurs`, `GET /admin/roles`, `GET /referentiel/menus`
- **CURRENT_PROBLEMS :** Conforme.

#### PAGE G.2 : Organisation & Référentiels d'Entreprise
- **ROUTE :** `/admin/organisation`
- **MODULE :** Administration
- **PAGE :** `OrganisationAdminPanel.jsx`
- **ROLE :** `ROLE_ADMIN`
- **PURPOSE :** Gestion de la structure hiérarchique : filiales, sites (Tamatave, Antananarivo, etc.), départements, services, banques domiciliataires et fonctions.
- **APIS_USED :** `GET /referentiel/sites`, `GET /referentiel/departements`, `GET /referentiel/banques`, etc.
- **CURRENT_PROBLEMS :** Conforme.

#### PAGE G.3 : Paramètres des Modules (Réglementation Fiscale & Pointage)
- **ROUTE :** `/admin/parametres`
- **MODULE :** Administration
- **PAGE :** `ParametresModulesPanel.jsx`
- **ROLE :** `ROLE_ADMIN`
- **PURPOSE :** Configuration des paramètres réglementaires de paie (barèmes IRSA par date d'effet, taux CNaPS/Santé, SMIC/SME national) et de pointage (horaires de nuit, tolérances retard, indemnités panier/déplacement).
- **APIS_USED :** `GET /paie/config-legale`, `GET /pointages/parametres`
- **CURRENT_PROBLEMS :** Conforme.

---

## 3. Matrice Synthétique d'Adéquation

| Module | Route Principale | Rôles Autorisés | Backend Canonical Controller | Risque Dérive Frontend | Conformité |
| :--- | :--- | :--- | :--- | :---: | :---: |
| **Authentification** | `/` | Public | `AuthController` | Nul | PASS |
| **Dashboard** | `/dashboard` | `ROLE_ADMIN` | `DashboardController` | Faible | PASS |
| **Pointage Saisie** | `/pointages?tab=saisie` | `ROLE_ADMIN`, `ROLE_AGENT_POINTAGE` | `PointageSaisieController` | Nul | PASS |
| **Pointage Suivi** | `/pointages?tab=suivi` | `ROLE_ADMIN`, `ROLE_AGENT_POINTAGE` | `PointageAnomalieController` | Nul | PASS |
| **Pointage Arbitrage** | `/pointages?tab=validation` | `ROLE_ADMIN` (Arbitre), `ROLE_AGENT_POINTAGE` (Lecture) | `PointageSyntheseController` | Nul | PASS |
| **Congés Soldes** | `/conges` | `ROLE_ADMIN` | `CongeSoldeController` | Nul | PASS |
| **Congés Demandes** | `/conges?tab=demandes` | `ROLE_ADMIN` | `DemandeCongeController` | Nul | PASS |
| **Paie Préparation** | `/paie/periodes/:id/preparation` | `ROLE_ADMIN` | `PeriodePaieController` | Nul (corrigé) | PASS |
| **Paie Population** | `/paie/periodes/:id/population` | `ROLE_ADMIN` | `EmployeController` | Nul | PASS |
| **Paie Variables** | `/paie/periodes/:id/variables` | `ROLE_ADMIN` | `PaieAjustementController` | Nul (corrigé) | PASS |
| **Paie Calcul & Sim** | `/paie/periodes/:id/calcul` | `ROLE_ADMIN` | `PaieController` | Nul (corrigé) | PASS |
| **Paie Contrôle** | `/paie/periodes/:id/controle` | `ROLE_ADMIN` | `PaieController` | Nul | PASS |
| **Paie Validation** | `/paie/periodes/:id/validation` | `ROLE_ADMIN` | `PeriodePaieController` | Nul | PASS |
| **Paie Sorties** | `/paie/periodes/:id/sorties` | `ROLE_ADMIN` | `PaieController` | Nul | PASS |
| **Employés** | `/employes` | `ROLE_ADMIN` | `EmployeController` | Nul | PASS |
| **Carrières** | `/carrieres` | `ROLE_ADMIN` | `WorkflowRhController` | Nul | PASS |
| **Administration** | `/admin/*` | `ROLE_ADMIN` | `UtilisateurController`, `PaieConfigLegaleController` | Nul | PASS |
