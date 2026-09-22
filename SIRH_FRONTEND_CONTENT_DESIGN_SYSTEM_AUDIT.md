# SIRH — Frontend Content & Design System Audit

## BACKEND-DRIVEN TRACEABILITY

| Fonction UI | Source backend | DTO/API | Interprétation frontend | Verdict |
| --- | --- | --- | --- | --- |
| Étapes de campagne | `PayrollPeriodWorkflowService.computeCampaignSteps` | `GET /periodes/context` → `PayrollPeriodContextView.campaignSteps` | `usePayrollCampaign` transmet le DTO sans reconstruire les étapes; le stepper l'affiche | PASS |
| Reprise de campagne | `PayrollPeriodWorkflowService.computeNextStep` | `GET /periodes/context` → `nextStep.destination` | `GestionPaiePage` navigue vers la destination retournée; aucun switch de statuts ou de transition React | PASS |
| Lecture seule du calcul | `computeCampaignSteps` : `CLOTUREE` → `CALCULATION.state=LOCKED` | même contexte → `PayrollCampaignStep.state` | `CalculStepPage` utilise uniquement `state === LOCKED`; `BLOCKED` reste un prérequis manquant | PASS |
| Validation depuis contrôle | `payrollCapability(..., VALIDATE)` puis `requirePayrollAction` | capability `VALIDATE`; `POST /paie/valider-periode` | le bouton « Continuer vers Validation » reflète la capacité de la transition appelée sur l'écran suivant, sans lui attribuer le statut de période | PASS |
| Validation de paie | `PayrollPeriodWorkflowService.requirePayrollAction(VALIDATE)` | `POST /paie/valider-periode` | `ValidationStepPage` active l'action par `VALIDATE.allowed`, puis appelle cet endpoint | PASS |
| Sorties mensuelles | `payrollCapability(..., GENERATE_OUTPUT)` puis `requireOutput` | `GET /paie/periode/{id}/export/*` | `GENERATE_OUTPUT.allowed` pilote uniquement les téléchargements; le badge provient de `selectedPeriod.state` | PASS |
| Taux sociaux historiques | `RefTypeCotisation.listActiveAt` sur `ref_type_cotisation` | `GET /referentiel/cotisations?at=YYYY-MM-DD` | `paieConfigService.getCotisations(period.startDate)` affiche le taux effectif, sans calcul frontend | PASS, sous réserve que la période dispose de sa date de début |
| Déclarations trimestrielles | aucun agrégat de readiness / entité déclarante / mois sources | pas de DTO déclaratif utilisable | la page affiche « Données de consolidation indisponibles » et ne produit pas de trimestre fictif | BLOCKED — `TEMPORAL_BACKEND_GAP` |

### Preuve sur `CALCULATION.state=LOCKED`

La valeur est calculée côté backend dans `PayrollPeriodWorkflowService.computeCampaignSteps` : lorsque `periode_paie.state` vaut `CLOTUREE`, l'étape `CALCULATION` reçoit `LOCKED`. Elle traverse `PayrollPeriodContextView` via `GET /periodes/context`, est chargée par `usePayrollCampaign`, puis consommée telle quelle par `CalculStepPage`. Aucune liste de statuts n'est reconstruite dans React pour ce verrou.

### Inventaire des sorties

| Sortie | État |
| --- | --- |
| Bulletins | CONNECTED — consultation des bulletins backend |
| Journal / état nominatif | CONNECTED — écran de contrôle backend |
| État fiscal IRSA / SAFI | CONNECTED |
| EDI ISI | CONNECTED |
| Ordre de virement | CONNECTED |
| OD / comptabilité | BACKEND_GAP — bouton désactivé, aucun succès simulé |
| DNS CNaPS / FMFP | BACKEND_AVAILABLE_NOT_CONNECTED pour le dossier trimestriel : export existe mais absence de contrat de readiness/trimestre utilisable par la page |
| Santé | BUSINESS_RULE_UNCERTAIN — données de lignes existent dans DNTS, aucun dossier déclaratif propre ni readiness organisme |

### Erreurs et tokens

`getErrorMessage` distingue un code métier stable (traduit, par exemple `MISSING_WORKING_CALENDAR`) d'un diagnostic. Les chaînes JDBC/SQL/stack ne sont pas rendues; les détails restent attachés à l'erreur pour les logs. Les tokens `success-*`, `warning-*`, `sky-*` et `orange-*` sont définis dans `frontend/src/index.css`; `success` est sémantique et réemployé pour les états positifs, avec `#15803d` sur fond clair.

### Vérifications exécutées

- `npm test -- --run src/tests/apiErrorMessage.test.js src/tests/PayrollWorkflowStepper.test.jsx`
- `npm run build`
- `mvn -q -Dtest=BackendDrivenFrontendArchitectureTest,DesignSystemArchitectureTest test` : passe après correction des routes de compatibilité et normalisation des rayons hors token.

Les tests de compilation ne certifient pas les contrats runtime; l'accès aux pages et aux endpoints authentifiés reste à vérifier dans un environnement démarré avec des données de campagne.

## Matrice globale d'audit

| Module | Pages contrôlées | Textes / données / UX | Backend-driven | Verdict |
| --- | --- | --- | --- | --- |
| Dashboard | Dashboard, en-tête | Synthèse et prochaine destination lues depuis le contexte; aucun fallback de période active côté client | `DashboardService`, `/periodes/context` | PARTIAL — rendu authentifié à rejouer avec données admin/agent |
| Organisation | Filiales, sites, départements, services | CRUD référentiel existant; erreurs à centraliser progressivement dans les panneaux structure | APIs organisation | PARTIAL — revue runtime requiert session admin |
| Employés | Liste, fiche, documents | Désactivation/réactivation/téléversement passent par `getErrorMessage`; pagination et recherches existantes conservées | APIs employés/documents | PARTIAL — parcours runtime authentifié requis |
| Congés | Demandes, soldes, validations | Calculs restent backend; messages centralisés sur les parcours déjà modifiés | APIs congés | PARTIAL — données réelles à inspecter |
| Pointage | Saisie, anomalies, période, arbitrage | Pas de fetch cellule ajouté; hook période utilise les erreurs centralisées | APIs pointage/périodes | PARTIAL — données rôle agent nécessaires |
| Paie | Campagne, calcul, contrôle, validation, sorties | Steps, next step et capabilities backend; exports disponibles connectés; OD gap explicite | APIs paie/périodes | PARTIAL — session admin nécessaire |
| Administration | Organisation, référentiels, sécurité, paramètres | Sources configurables conservées côté API; audit des erreurs directes engagé | APIs admin/configuration | PARTIAL — plusieurs panneaux demandent une passe de runtime admin |
| Authentification | Login, refresh, logout, réseau | Erreurs HTTP normalisées; diagnostics techniques filtrés | `/auth/*` | PARTIAL — session expirée à jouer avec compte de test |
| Composants transversaux | Badge, alertes, erreurs, routes | Tokens/rayons canoniques, routes legacy préservées par redirection | n/a | PASS |

### Routes legacy Paie

`/paie/periods/:periodId/:step` et `/paie/periods/:periodId/:step/:employeeId` sont **REDIRECT_COMPATIBILITY** : elles redirigent vers `/paie/periodes/:periodId/<étape canonique>` au lieu de tomber sur le centre de paie. Les nouveaux liens internes utilisent exclusivement les routes canoniques.
