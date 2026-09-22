# Architecture UX du module Paie

## Workflow

La période de paie est le contexte principal. Les étapes sont des routes stables alimentées par le contexte backend `PayrollPeriodContextView` : préparation, population, variables, calcul, contrôle, validation et sorties.

Le frontend ne déduit pas les transitions autorisées. `campaignSteps`, `availableActions`, `issues`, `warnings` et l'état de période pilotent le stepper, les actions et les messages.

## Cartographie

| Route | Page | Objectif | API principale | Décision |
|---|---|---|---|---|
| `/paie` | `GestionPaiePage` | Choisir et suivre une période | périodes, contexte, cockpit | Improve |
| `/paie/periodes/:id/preparation` | `PreparationStepPage` | Vérifier les prérequis | contexte, configuration légale datée | Improve |
| `/paie/periodes/:id/population` | `PopulationStepPage` | Contrôler les salariés inclus | cockpit léger | Keep |
| `/paie/periodes/:id/variables` | `VariablesStepPage` | Gérer les mouvements variables | ajustements, types configurés | Improve |
| `/paie/periodes/:id/calcul` | `CalculStepPage` | Simuler ou calculer officiellement | calcul période, cockpit | Improve |
| `/paie/periodes/:id/controle` | `ControleStepPage` | Scanner et expliquer les bulletins | cockpit, bulletin complet | Improve |
| `/paie/periodes/:id/validation` | `ValidationStepPage` | Valider puis clôturer | contexte, commandes backend | Keep |
| `/paie/periodes/:id/sorties` | `SortiesStepPage` | Produire les documents et déclarations | endpoints exports | Keep |

## Composants structurants

- `PayrollCampaignHeader` conserve période, statut, dates, population, blockers, progression et fil d'Ariane.
- `PayrollPeriodStepper` rend les états backend `COMPLETED`, `CURRENT`, `AVAILABLE`, `WARNING`, `BLOCKED` et `LOCKED`.
- `BulletinPaieDrawer` expose la cascade canonique et le détail IRSA provenant de `explicationFiscalite`.
- `PayrollNavigationFooter` guide la transition entre pages et explique les indisponibilités.

## Contrat frontend

- Les montants sont formatés avec `formatMoneyAr` sans modification de précision du DTO.
- Les taux, minima, plafonds, tranches et types de mouvements ne possèdent aucun fallback réglementaire React.
- Les sous-totaux financiers ne sont pas reconstruits à partir de montants partiels.
- Les tranches affichées utilisent directement `explicationFiscalite.detailsTranches`.

## Matrice des écrans

| Étape | Interface | Objectif | Action principale | Interface secondaire |
|---|---|---|---|---|
| Périodes | Page | Choisir et suivre | Ouvrir la période | — |
| Préparation | Page | Vérifier la readiness | Continuer | actions de résolution |
| Population | Page | Contrôler l'éligibilité | Continuer | recherche et filtres |
| Variables | Page | Gérer les mouvements | Ajouter une variable | formulaire contextuel |
| Calcul | Page | Simuler puis calculer | Calculer la paie | résultats et erreurs |
| Contrôle | Page | Comparer les bulletins | Préparer la validation | drawer bulletin |
| Validation | Page | Décider et sceller | Valider / clôturer | confirmation |
| Sorties | Page | Produire les livrables | Générer | aperçu des données |
