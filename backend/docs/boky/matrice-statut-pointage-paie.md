# Matrice des statuts Pointage et Paie

Source de vérité : `PayrollPeriodWorkflowService` pour les transitions, capacités, étapes et prochaine destination ; `PayrollWorkflowStatusResolver` pour les deux statuts affichés. Le frontend consomme `workflowStatuses`, `campaignSteps`, `availableActions` et `nextStep`. Il choisit seulement la présentation visuelle.

| État de période | Pointage affiché | Paie affichée | Étape suivante possible |
| --- | --- | --- | --- |
| `BROUILLON` | À ouvrir | En attente du pointage | Ouverture / saisie selon les capacités |
| `EN_COURS` | Saisie en cours | En attente du pointage | Saisie ou transmission |
| `A_VALIDER`, `TRANSMIS_DAF` | À valider | En attente du pointage | Validation ou correction du pointage |
| `VALIDEE`, aucun bulletin | Pointage figé | À préparer | Préparation de la paie |
| `VALIDEE`, bulletins incomplets pour la population éligible | Pointage figé | Calcul incomplet | Reprendre le calcul des salariés manquants |
| `VALIDEE`, bulletins calculés | Pointage figé | À contrôler | Contrôle et validation des bulletins |
| `VALIDEE`, calcul obsolète | Pointage figé | À recalculer | Nouveau calcul |
| `VALIDEE`, bulletins tous validés | Pointage figé | Validation à finaliser | Finalisation de la paie |
| `PAIE_VALIDEE` | Pointage figé | Paie validée | Sorties réglementaires |
| `CLOTUREE` | Pointage figé | Paie clôturée | Consultation des sorties |

`VALIDEE` valide le pointage et fige son snapshot. Ce code ne signifie pas que la paie est validée. Le statut Paie dépend aussi des bulletins et de l'obsolescence du calcul. `PAIE_VALIDEE` est l'état de validation de la paie. La clôture exige des bulletins validés.

Les transitions autorisées sont `BROUILLON → EN_COURS → TRANSMIS_DAF/A_VALIDER → VALIDEE → PAIE_VALIDEE → CLOTUREE`, avec rejet vers `EN_COURS`, réouverture vers `EN_COURS` et retour de `PAIE_VALIDEE` à `VALIDEE` lors d'un recalcul. Les actions restent conditionnées aux capacités et prérequis retournés par le backend ; cette table ne doit pas servir à autoriser une action côté frontend.
