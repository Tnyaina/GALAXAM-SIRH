# Workflow Congés : acquisition, contrôle, régularisation

## Trois opérations distinctes

| Opération | Déclencheur | Date métier | Responsable | Effet |
|---|---|---|---|---|
| Acquisition | Clôture de paie validée | Fin de campagne (ex. 25/01/2026) | Automatique | Assure la référence civile, crée le droit du mois et fige la valeur de son lot |
| Contrôle | Après clôture ou à la demande | Date de situation pour le solde ; mois de clôture pour le journal | DAF | Compare les 12 acquisitions et le stock FIFO avec l'Excel ; aucune écriture |
| Régularisation | Écart documenté | Mois acquis concerné | DAF, permission `CONGE_VALIDATE` | Corrige la seule valeur d'un lot intact, recalcule les cumuls du cycle, conserve avant/après/auteur/motif |

La référence `01/01/N → 31/12/N` est une enveloppe d'acquisitions. Elle ne représente pas le solde disponible : celui-ci regroupe les lots de plusieurs années, diminués des prises selon le FIFO. Pour la campagne `26/12/2025 → 25/01/2026`, le lot est acquis le 25/01/2026 et appartient à la référence 2026 ; les lots de 2025 ne changent pas de valeur.

La reprise initiale d'un historique Excel est une opération unique depuis la fiche salarié. Elle ne fait pas partie du renouvellement annuel. Les demandes et validations de congés restent dans leur workflow actuel.

## Actions retirées du parcours courant

Les boutons de création de cycle, de clôture manuelle, de création du cycle suivant et d'activation du taux moyen ne figurent plus dans l'écran de contrôle. Ils reproduisaient ou anticipaient le travail de la clôture de paie et pouvaient faire croire que le taux moyen annuel remplaçait les valeurs des lots. L'API redondante « cycle suivant » est supprimée. Les services backend de création manuelle, clôture exceptionnelle et activation datée restent disponibles pour les reprises et intégrations existantes ; la clôture manuelle refuse désormais une référence dont le dernier mois n'a pas d'acquisition.

## Tables nécessaires

| Table | Rôle conservé |
|---|---|
| `conge_periode_reference` | Une référence annuelle par salarié ; bornes et état |
| `conge_journal_droit` | Droit et valeur issus de chaque campagne de paie |
| `mouvement_solde_conge` | Lots, prises et solde FIFO avec valeur source |
| `conge_valorisation_mensuelle_correction` | Traçabilité obligatoire des changements DAF du journal et du lot : valeur avant/après, motif, auteur, date |

Aucune table supplémentaire n'est créée par ce nettoyage. La table de correction ajoutée au chantier précédent est justifiée par l'audit monétaire : la valeur originale doit pouvoir être reconstruite sans modifier un bulletin clôturé. Le cas d'un lot déjà consommé ou d'une référence clôturée reste bloqué : sa régularisation exige un processus distinct tenant compte des indemnités déjà payées. Aucun recalcul rétroactif silencieux.

## Limites métier encore ouvertes

- **Lot consommé ou année clôturée :** ne pas réécrire le lot, le journal annuel figé, ni le bulletin historique. La DAF compare valeur initiale, valeur correcte, jours consommés et paiement réellement versé. Le complément passe par `REGULARISATION_CONGE` dans une paie future, avec motif `JOURNAL_CONGE#id`, salarié vérifié et approbation normale de la variable. Une récupération d'un trop-perçu reste soumise à validation humaine et à la règle légale applicable. L'automatisation du contrôle du minimum légal et du STC reste ouverte. Aucun nouveau type de lot ni table spéciale par scénario.
- **Base légale de l'indemnité :** l'article 131 du Code du travail malgache 2024 fixe une allocation minimale liée aux rémunérations des douze mois précédant la prise. La valeur historique du lot est utile pour la traçabilité et le FIFO mais ne suffit pas, à elle seule, à démontrer que chaque paiement atteint ce minimum. Le contrôle à la date de prise doit être ajouté au workflow de validation/paie avant d'automatiser un complément.
- **Décompte des jours :** Galaxam conserve les jours calendaires avec week-ends et fériés inclus (vendredi, reprise lundi = 3 jours hors autre événement). L'article 127 du même Code exclut explicitement les jours fériés du congé payé. Cette divergence n'est pas résolue par le logiciel ; elle doit faire l'objet d'un arbitrage juridique documenté. La règle française en jours ouvrables ne s'applique pas à Galaxam.
- Définir si une campagne antérieure non clôturée doit bloquer strictement la clôture de janvier ou seulement remonter une anomalie de contrôle lors d'une reprise.

Les pratiques de SIRH comme [Odoo Time Off](https://www.odoo.com/documentation/20.0/applications/hr/time_off.html) distinguent acquisition, solde, approbation et reporting ; [SAP SuccessFactors](https://help.sap.com/docs/successfactors-employee-central/using-time-management-in-sap-successfactors/what-is-time-account-snapshot-and-how-do-i-use-it) présente aussi séparément acquis, pris, planifiés et solde. Leurs options de report/expiration ne constituent pas une raison de transférer ou effacer les lots Galaxam au 1er janvier. La séparation entre acquisition automatique, contrôle DAF et ajustement traçable reste le choix adapté au modèle existant. Source juridique : [Code du travail malgache 2024](https://cnlegis.gov.mg/uploads/L2024-014-VF.pdf), articles 125, 127, 129, 131 et 133.
