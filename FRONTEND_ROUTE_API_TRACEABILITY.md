# Traçabilité Complète Routes Frontend ↔ Endpoints Backend API

Ce document établit la matrice de correspondance exhaustive entre l'arborescence des routes de l'application frontend React (Vite) et les contrats d'API REST du backend Spring Boot (port 8085), avec typage des DTOs, verbes HTTP et contraintes métier.

---

## 1. Module Paie & Campagnes (`/paie/*`)

Toutes les routes de ce module sont strictement protégées par le garde `ProtectedRoute` (`requiredPermission="PAIE_READ"`, `allowedRoles=["ROLE_ADMIN"]`).

| Route Frontend | Composant Page | Verbe | Endpoint Backend | Payload / Paramètres | DTO Réponse | Responsabilité Métier |
|---|---|---|---|---|---|---|
| `/paie` | `GestionPaiePage` | `GET` | `/api/periodes` | Aucun | `List<PayrollPeriodSummary>` | Liste des périodes et sélection de la campagne active |
| `/paie` | `GestionPaiePage` | `GET` | `/api/periodes/context` | `?periodId={id}` | `PayrollPeriodContextView` | Contexte du cycle de paie, étapes, actions autorisées |
| `/paie/periodes/:id/preparation` | `PreparationStepPage` | `GET` | `/api/periodes/context` | `?periodId={id}` | `PayrollPeriodContextView` | Vérification des prérequis de calcul (gel pointage, config légale) |
| `/paie/periodes/:id/preparation` | `PreparationStepPage` | `GET` | `/api/admin/paie-config/active` | Aucun | `PaieConfigLegaleDTO` | Paramètres légaux actifs (barème IRSA, plafonds CNaPS, SMIG) |
| `/paie/periodes/:id/population` | `PopulationStepPage` | `GET` | `/api/paie/periode/{id}/cockpit` | `?light=true` | `PayrollCockpitDTO` (champ `population`) | Résolution déterministe des salariés éligibles et décisions |
| `/paie/periodes/:id/variables` | `VariablesStepPage` | `GET` | `/api/paie/ajustements` | `?periodeId={id}` | `List<PaieAjustementDTO>` | Liste des éléments variables du mois (primes, retenues, acomptes) |
| `/paie/periodes/:id/variables` | `VariablesStepPage` | `GET` | `/api/paie/types-mouvements` | Aucun | `List<RefTypeMouvementPaieDTO>` | Référentiel des types de rubriques variables et sens |
| `/paie/periodes/:id/variables` | `VariablesStepPage` | `POST` | `/api/paie/ajustements` | `{"idEmploye", "idPeriodePaie", "typeMouvement", "sens", "montant", "motif"}` | `PaieAjustementDTO` | Création d'une variable de paie avec impact sur recalcul |
| `/paie/periodes/:id/calcul` | `CalculStepPage` | `POST` | `/api/paie/calculer-periode` | `{"idPeriodePaie": id, "simulation": true}` | `PayrollPeriodCalculationResultDTO` | Simulation collective in-memory (cascade canonique Base → Brut → Net) |
| `/paie/periodes/:id/calcul` | `CalculStepPage` | `POST` | `/api/paie/calculer-periode` | `{"idPeriodePaie": id, "simulation": false}` | `PayrollPeriodCalculationResultDTO` | Exécution officielle du calcul réel et persistance en base |
| `/paie/periodes/:id/calcul` | `CalculStepPage` | `GET` | `/api/paie/periode/{id}/cockpit` | `?size=100` | `PayrollCockpitDTO` | Récupération des KPIs et décompositions après calcul |
| `/paie/periodes/:id/controle` | `ControleStepPage` | `GET` | `/api/paie/periode/{id}/cockpit` | `?size=100&q=&filter=` | `PayrollCockpitDTO` (champ `bulletins`) | Grille de contrôle de conformité des bulletins et alertes d'écarts |
| `/paie/periodes/:id/controle` | `BulletinPaieDrawer` | `GET` | `/api/paie/bulletins/{id}` | Aucun | `BulletinPaieCompletDTO` | Consultation détaillée et explicabilité intégrale des rubriques |
| `/paie/periodes/:id/validation` | `ValidationStepPage` | `POST` | `/api/paie/valider-periode` | `{"idPeriodePaie": id}` | `Map<String, Object>` | Approbation DAF officielle : statut `CALCULE` → `VALIDE` |
| `/paie/periodes/:id/validation` | `ValidationStepPage` | `POST` | `/api/periodes/{id}/actions/CLOSE` | `{"version": N}` | `BusinessDecision` | Clôture définitive et irréversible de la campagne (`CLOTUREE`) |
| `/paie/periodes/:id/validation` | `ValidationStepPage` | `POST` | `/api/periodes/{id}/actions/REOPEN` | `{"version": N, "motif": "..."}` | `BusinessDecision` | Réouverture exceptionnelle post-clôture avec motif audité |
| `/paie/periodes/:id/sorties` | `SortiesStepPage` | `GET` | `/api/paie/exports/{type}/preview` | `?periodeId={id}` | `List<Map<String, Object>>` | Prévisualisation des états légaux (SAFI, DNTS, Virement) |
| `/paie/periodes/:id/sorties` | `SortiesStepPage` | `GET` | `/api/paie/exports/{type}/download` | `?periodeId={id}` | Fichier binaire / octet-stream (ODS, CSV, XML) | Téléchargement sécurisé des déclarations DGI, CNaPS et banques |

---

## 2. Module Pointage & Présences (`/pointages/*`)

Protégé par `ProtectedRoute` (`requiredPermission="POINTAGE_READ"`, `allowedRoles=["ROLE_ADMIN", "ROLE_AGENT_POINTAGE"]`).

| Route Frontend | Composant Page | Verbe | Endpoint Backend | Payload / Paramètres | DTO Réponse | Responsabilité Métier |
|---|---|---|---|---|---|---|
| `/pointages?tab=saisie` | `PointageSaisieTab` | `GET` | `/api/pointages/saisie-quotidienne` | `?periode={id}&date={Y-M-D}&site={id}` | `PointageSaisieQuotidienneView` | Grille de saisie quotidienne des heures par collaborateur |
| `/pointages?tab=saisie` | `PointageSaisieTab` | `POST` | `/api/pointages/saisie-quotidienne` | `List<PointageLigneRequest>` | `PointageSaisieResultView` | Enregistrement batch des heures, vérification des 24h max |
| `/pointages?tab=suivi` | `PointageSuiviTab` | `GET` | `/api/pointages/anomalies` | `?periode={id}` | `List<PointageAnomalieDTO>` | Détection et suivi des anomalies de présence et retards |
| `/pointages?tab=suivi` | `PointageSuiviTab` | `POST` | `/api/pointages/regulariser-anomalie` | `{"idPointageJour", "motif"}` | `PointageAnomalieDTO` | Régularisation motivée d'une anomalie bloquante |
| `/pointages?tab=suivi` | `PointageSuiviTab` | `POST` | `/api/periodes/{id}/actions/SUBMIT` | `{"version": N}` | `BusinessDecision` | Transmission officielle DAF (statut `EN_COURS` → `TRANSMIS_DAF`) |
| `/pointages?tab=validation` | `PointageValidationTab`| `POST` | `/api/periodes/{id}/actions/REJECT` | `{"version": N, "motif": "..."}` | `BusinessDecision` | Arbitrage DAF : renvoi pour correction avec motif obligatoire |
| `/pointages?tab=validation` | `PointageValidationTab`| `POST` | `/api/periodes/{id}/actions/APPROVE`| `{"version": N}` | `BusinessDecision` | Scellement du snapshot pointage et passage au statut `VALIDEE` |

---

## 3. Module Congés & Absences (`/conges/*`)

Protégé par `ProtectedRoute` (`requiredPermission="CONGE_READ"`).

| Route Frontend | Composant Page | Verbe | Endpoint Backend | Payload / Paramètres | DTO Réponse | Responsabilité Métier |
|---|---|---|---|---|---|---|
| `/conges?tab=soldes` | `CongesSoldesTab` | `GET` | `/api/conges/soldes` | Aucun | `List<CongeSoldeEmployeDTO>` | Consultation des soldes de congés et droits acquis par salarié |
| `/conges?tab=demandes` | `CongesDemandesTab` | `GET` | `/api/conges/demandes` | `?statut={code}` | `List<DemandeCongeDTO>` | Historique et liste des demandes de congés à valider |
| `/conges?tab=demandes` | `CongesDemandesTab` | `POST` | `/api/conges/demandes` | `{"idEmploye", "idTypeConge", "dateDebut", "dateFin", "commentaire"}` | `DemandeCongeDTO` | Dépôt d'une demande avec calcul automatique des jours ouvrables |
| `/conges?tab=demandes` | `ModalAuditComptable`| `GET` | `/api/conges/demandes/{id}/audit` | Aucun | `CongeAuditComptableDTO` | Explicabilité financière : déduction Base/30 vs maintien TCONGE |
| `/conges?tab=demandes` | `CongesDemandesTab` | `POST` | `/api/conges/demandes/{id}/valider` | Aucun | `DemandeCongeDTO` | Approbation DAF et imputation immédiate au journal comptable |

---

## 4. Authentification & Sécurité (`/`)

| Route Frontend | Composant Page | Verbe | Endpoint Backend | Payload / Paramètres | DTO Réponse | Responsabilité Métier |
|---|---|---|---|---|---|---|
| `/` | `Login` | `POST` | `/api/auth/login` | `{"identifiant", "motDePasse"}` | `AuthResponseDTO` (`accessToken`, `user`, `permissions`) | Authentification JWT HS512 avec expiration et extraction des rôles |
| `*` | `AuthContext` | `GET` | `/api/auth/me` | Bearer Token Header | `UserContextDTO` | Rafraîchissement et vérification de la session active |
