# CONTRE-AUDIT UI/UX — SIRH GALAXAM

Date : 2026-09-01. Analyse statique complète des flux frontend → API → contrôleurs → services métier → modèles. Aucun code modifié.

## 1. Résumé exécutif

L'audit Claude est utile mais surestime plusieurs défauts UX et manque trois risques d'intégrité métier. Les workflows contrats sont plus robustes qu'annoncé : le backend bloque contrat actif et brouillon concurrent, et l'endpoint de transition est aussi celui des ruptures. En revanche, la création de congé ne lance pas ses contrôles métier, le pointage n'exige pas un contrat actif, et le verrouillage de période n'est pas imposé côté serveur. Le SIRH n'est pas prêt pour paie ni pour des modifications UX avant ces P0.

## 2. Verdict sur l'audit Claude Opus

| ID | Verdict | Justification |
| -- | -- | -- |
| UX-EMP-001 | CONFIRMÉ | `PUT /employes/{id}/desactiver` modifie directement l'employé, sans clôture de contrat ni workflow. |
| UX-EMP-002 | CONFIRMÉ | `EmployeList.jsx` utilise `getEmployes({size:1000})`, puis filtre/page en mémoire. |
| UX-EMP-003 | FAUX | Après création, redirection vers `/employes/:id?tab=carriere&action=new_contract` et toast. |
| UX-EMP-004 | PARTIELLEMENT CONFIRMÉ | Pas de brouillon persistant ; ce n'est pas un défaut métier systématique. |
| UX-CON-001 | PARTIELLEMENT CONFIRMÉ | Pas de garde UX, mais `WorkflowRhService.creerBrouillonEmbauche` refuse contrat ACTIF et brouillon. |
| UX-CON-002 / BUG-001 | FAUX | `initierRupture` est erronée mais inutilisée. `RuptureContratModal` appelle la transition générique, qui traite les événements de rupture. |
| UX-CON-003/004 | PARTIELLEMENT CONFIRMÉ | Modal standard scrollable, mais écrans longs et récapitulatif monolithique. |
| UX-CNG-001 | FAUX | `CongesTab` dans la fiche employé relie demandes, solde et ledger au salarié. |
| UX-CNG-002/003 | CONFIRMÉ | Modal congé hors primitive standard et chargements de demandes redondants. |
| UX-PTG-001 | FAUX | Le constat `size:1000` ne correspond pas à la synthèse pointage. |
| UX-PTG-002 | CONFIRMÉ, gravité P0 | La grille joint affectation + `est_actif`, pas le contrat ACTIF ni le statut d'affectation. |
| UX-PTG-003 | FAUX | `SaisieQuotidiennePage` utilise `PointageGuideStepper`. |
| UX-NAV-001/002 | PARTIELLEMENT CONFIRMÉ | Couverture inégale, mais breadcrumbs/pages contextuelles existent. |
| UX-DSN-001 | CONFIRMÉ | `slate`, `emerald`, `amber`, etc. sont utilisés hors tokens. |
| UX-DSN-002 | FAUX | `Badge` replie volontairement une variante inconnue vers `default`; dette de nommage, pas panne. |
| UX-FRM-001 | PARTIELLEMENT CONFIRMÉ | Le congé a des erreurs inline; leur couverture est inégale. |
| UX-TBL-001/RSP-001 | PARTIELLEMENT CONFIRMÉ | Plusieurs tables ont `overflow-x-auto`; la grille pointage reste à tester sur mobile. |
| UX-ACC-001 | CONFIRMÉ | Focus visible présent, mais Dialog sans focus trap, `role=dialog` ni `aria-modal`. |
| UX-BUG-003 | CONFIRMÉ | Les breadcrumbs protégés pointent « Tableau de bord » vers `/`, route Login. |
| UX-BUG-004 | NON VÉRIFIABLE | À ne pas prioriser sans reproduction du composant. |

## 3. Bugs critiques supplémentaires

| ID | Gravité | Module | Problème | Fichiers |
| -- | -- | -- | -- | -- |
| P0-CNG-001 | P0 | Congés | Création sans `DemandeConge.controler()` : dates, chevauchements, préavis, ancienneté, maternité et pool peuvent être persistés. | `DemandeCongeController.java`, `CongeService.java`, `DemandeConge.java` |
| P0-PTG-001 | P0 | Pointage | Éligibilité sans contrat ACTIF ni affectation ACTIVE. | `PointageSaisieService.java`, `PointageSaisieController.java`, `usePointageSaisie.js` |
| P0-PTG-002 | P0 | Pointage/Paie | Batch sans verrou serveur explicite par statut de période. | `PointageSaisieService.java`, `PointageSaisieController.java`, `SaisieQuotidiennePage.jsx` |
| P1-PTG-003 | P1 | Permissions | `canManage = ... || true` affiche la gestion de période à tous. | `PeriodePaiePage.jsx` |
| P1-EMP-001 | P1 | Employés | Réactivation directe sans contrat ni affectation. | `EmployeController.java`, `employeService.js` |

## 4. Analyse Employés

### Workflow actuel

Création → fiche carrière → brouillon embauche → validation DAF → contrat/affectation actifs. Le backend interdit ACTIF ou BROUILLON concurrent.

### Problèmes confirmés

Toggle de désactivation hors workflow, permission trop large (`EMPLOYE_WRITE`), pagination locale 1 000 lignes.

### Faux positifs

Le guidage post-création est implémenté.

### Nouveaux problèmes

La réactivation directe remet `est_actif` sans reconstruire la relation emploi.

### Workflow recommandé

Fiche civile séparée du statut d'emploi ; entrée/sortie/réintégration uniquement par workflow RH.

## 5. Analyse Contrats & Carrière

### Workflow actuel

`WorkflowRhService` gère embauche, transitions, CDD, essai et ruptures transactionnellement.

### Problèmes confirmés

Garde UI insuffisante, méthodes mortes, modales surchargées.

### Faux positifs

Un endpoint `/rupture` distinct n'est pas nécessaire : la transition générique est le contrat API implémenté.

### Nouveaux problèmes

Les gardes importantes sont serveur seulement ; l'UI doit afficher l'action permise et le brouillon en cours.

### Workflow recommandé

Carte statut d'emploi avec contrat, affectation, prochaine prise d'effet et actions calculées serveur.

## 6. Analyse Congés & Absences

### Workflow actuel

Demande → EN_ATTENTE → validation/refus/annulation. Validation crée PRISE dans le ledger ; annulation compensatrice crée ANNULATION.

### Problèmes confirmés

Création et modification n'ont pas les mêmes règles : seule la modification appelle `controler`.

### Faux positifs

Le congé est contextualisé dans la fiche salarié.

### Nouveaux problèmes

Le calcul local des jours n'appelle pas la simulation serveur ; la dérogation de préavis est activée automatiquement pour une date passée. Aucun contrôle explicite de couverture contractuelle n'est visible dans `DemandeConge.controler`.

### Workflow recommandé

Simulation serveur → validation serveur atomique à création et validation → ledger ; dérogation soumise à permission, motif et trace.

## 7. Analyse Pointage

### Workflow actuel

Date/site → grille → batch → synthèse → transmission DAF. Congés validés et fériés sont lus.

### Problèmes confirmés

Éligibilité fondée sur affectation/date et `est_actif`, sans contrat ACTIF. Le stepper, lui, existe.

### Nouveaux problèmes

Écrasement possible entre deux batchs, montants partiellement client, absence de verrou serveur de période démontrée.

### Workflow recommandé

Éligibilité serveur (contrat, affectation, période, absence), batch versionné/idempotent, période ouverte obligatoire puis snapshot immuable.

## 8. Préparation Paie

Les données salaire/catégorie/périodes/congés/pointages existent mais ne sont pas encore fiables pour une paie : pointage hors contrat, correction post-transmission et indemnités à fiabiliser serveur. Prévoir snapshot de période, version des paramètres et traçabilité de chaque élément.

## 9. Analyse cross-module

| Relation | Constat | Risque |
| -- | -- | -- |
| Employé ↔ Contrat | toggle hors workflow | statuts incompatibles |
| Contrat ↔ Congés | couverture non vérifiée | congé hors emploi |
| Contrat ↔ Pointage | aucune jointure contrat | heures/indemnités indues |
| Pointage ↔ Paie | verrou serveur absent | données modifiables après transmission |
| Congés ↔ Paie | ledger bon, entrée invalide possible | débit/crédit contestable |

## 10. Design System

`index.css` fournit tokens, dark mode et focus. Adopter des tokens sémantiques pour toutes les couleurs, documenter les variantes de Badge et interdire les primitives visuelles locales.

## 11. Navigation

Corriger le breadcrumb `/` vers la route dashboard authentifiée. Relier explicitement fiche, contrat actif, congés et pointages filtrés ; conserver filtres/pagination dans l'URL.

## 12. Formulaires

La validation inline est partielle, pas absente. Priorités : simulation serveur, erreurs par champ structurées, idempotence backend, abandon guidé et dérogations explicites.

## 13. Accessibilité

Ajouter à `Modal.jsx` rôle, modalité, association titre/description, focus trap et restauration. Auditer boutons icône, toasts `aria-live`, tables au clavier et erreurs annoncées.

## 14. Responsive

Tester grille pointage, modales carrière et synthèses à 320/768/1024 px. Les wrappers horizontaux existants sont une base, pas une garantie d'utilisabilité.

## 15. Matrice des workflows

| Workflow | Happy path | Erreur | Edge cases | Permissions | Cohérence |
| -- | -- | -- | -- | -- | -- |
| Création employé | oui | serveur | double clic | WRITE | contrat à compléter |
| Embauche | brouillon/DAF | ACTIF/brouillon bloqué | réembauche | WRITE/VALIDATE | robuste serveur |
| Rupture | transition générique | type invalide | future/annulation | WRITE/VALIDATE | cohérent |
| Congé | demande/ledger | tardive | chevauchement | WRITE/VALIDATE | P0 création |
| Pointage | grille/batch | réseau | hors contrat/période fermée | AGENT/ADMIN | P0 |

## 16. Wireframes recommandés

### Fiche salarié

Objectif : action RH suivante certaine.

```
Employé · matricule                         [Modifier]
[ACTIF] CDI depuis 01/01/2026 · Affectation Site A
Prochaine action : [Mutation] [Rupture]     Historique
Contrat | Affectation | Congés | Pointages | Documents
```

### Décision congé

```
Demande #124 · Contrat couvrant la période : oui
Type | Dates | Jours serveur | Solde avant/après | Chevauchements : aucun
[Refuser] [Justificatif] [Valider]
```

## 17. Workflows recommandés

1. Fiche → embauche préparée → validation DAF → activation automatique.
2. Congé : simulation, contrôle atomique, validation recontrôlée, ledger, annulation compensatrice.
3. Pointage : éligibilité, période ouverte, batch versionné, validation, verrou/snapshot, transmission paie.

## 18. Design System cible

Tokens sémantiques uniques ; Dialog, Field et Table accessibles ; états loading/empty/error/success standardisés ; pas de couleurs ni modales métier ad hoc.

## 19. Backlog priorisé

- P0 : `d.controler(c)` à création et recontrôle à validation ; contrôle contrat/période congé.
- P0 : contrat ACTIF + affectation ACTIVE requis au chargement et batch pointage.
- P0 : refus serveur d'écriture hors période OUVERTE.
- P1 : supprimer toggles directs, corriger `|| true`, supprimer méthode API morte.
- P2 : pagination employé serveur, simulation congé serveur, contexte cross-module.
- P3 : Dialog accessible, responsive, tokens et découpage des gros composants.

## 20. Plan de modification

### Phase 1 — Corrections critiques

P0 congés, éligibilité/verrou pointage, tests d'intégration.

### Phase 2 — Cohérence cross-module

Cycle salarié/contrat, couverture congés, snapshots paie.

### Phase 3 — UX

Actions conditionnelles, navigation contextuelle et erreurs exploitables.

### Phase 4 — Design System

Tokens et primitives partagées.

### Phase 5 — Accessibilité

Dialog, focus, ARIA et navigation clavier.

### Phase 6 — Responsive

Validation aux largeurs mobile/tablette/desktop.

### Phase 7 — Préparation Paie

Snapshots, moteur serveur, traçabilité et verrouillage.

## Verdict global

**Audit supplémentaire nécessaire.** Base contrats solide, mais les P0 congés et pointage compromettent l'intégrité des données et la future paie. Après leurs corrections et tests d'intégration, le système sera **prêt pour modification UX**.
