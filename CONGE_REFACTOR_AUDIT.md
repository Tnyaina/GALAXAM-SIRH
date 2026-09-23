# Audit pragmatique du module Congés

## Cartographie et décisions

| Zone | Fichiers principaux | Décision | Motif |
|---|---|---|---|
| Demandes | `DemandeConge`, `DemandeCongeController`, `CongeService` | REWRITE ciblé | Le modèle possède légitimement le mapping et le SQL d'une demande, mais contient encore une orchestration transactionnelle historique. Le service reste le point d'entrée des workflows. |
| Calcul | `CongeCalculateurEngine`, `PurgeFifoEngine` | KEEP + CONSOLIDATE | L'Engine est la source des calculs déterministes. `PurgeFifoEngine` reste séparé car il traite l'imputation FIFO du ledger, responsabilité autonome. |
| Droits | `MouvementSoldeConge`, `VSoldeCongeEmploye`, `VPoolCongeAnnuelConsommation` | KEEP | Ledger auditable, solde dérivé et pool annuel partagé sont des concepts métier réels. |
| Valorisation | `CongeTauxApplicableService`, DTO et enums TCONGE | KEEP | Historique, cutover, résolution temporelle et SQL justifient un service autonome. La formule pure TCONGE reste dans `CongeCalculateurEngine`. |
| Référentiel | `RefTypeConge`, `RefMotifEvenementFamilial`, `RefPoolCongeAnnuel` | KEEP | Chaque classe possède son SQL manuel et correspond à un référentiel administrable. |
| Statuts | `RefStatutDemandeConge` | KEEP pour cette passe | La table est utilisée par les vues Congés, trois services Pointage, les notifications et le dashboard. La supprimer imposerait une migration transversale sans réduire les règles dupliquées les plus risquées. |
| Schedulers | `AcquisitionCongeScheduler`, `PurgeCongeScheduler` | KEEP, ownership à poursuivre | L'acquisition délègue déjà au service. La purge s'appuie sur le moteur FIFO mais conserve encore de l'orchestration à rapprocher du service. |
| Frontend | `features/leave` | KEEP feature-first, REWRITE des règles | Les composants restent proches de la feature. Les calculs durée/préavis/solde/quota doivent provenir de la simulation backend. |

## `DemandeConge.java`

- A/B/C — mapping, SQL et comportement d'une demande : conservés.
- D — calculs purs : délégation au `CongeCalculateurEngine` (jours, solde projeté, préavis, plafond de pool).
- E — orchestration : les contrôleurs appellent déjà `CongeService`; déplacer la totalité de la validation en une fois serait risqué pour les transactions et sera une dette explicite.
- F — notifications : la méthode historique ne fait plus que déléguer à `NotificationRhService`.

## Tables

| Table/vue | Décision |
|---|---|
| `demande_conge` | KEEP |
| `mouvement_solde_conge` | KEEP |
| `ref_type_conge` | KEEP |
| `ref_motif_evenement_familial` | KEEP |
| `ref_pool_conge_annuel` | KEEP |
| `conge_periode_reference` | KEEP |
| `conge_taux_applicable` | KEEP |
| `ref_statut_demande_conge` | KEEP (dépendances runtime transversales confirmées) |
| `v_solde_conge_employe`, `v_pool_conge_annuel_consommation`, `v_demande_conge_cpl` | KEEP |

## Duplications confirmées

- React recalculait le préavis, la durée fixe et la suffisance du solde.
- Le plafond du pool était parfois paramétré en base et parfois codé à `10`.
- Le workflow calculait directement le solde projeté et le préavis au lieu de passer par l'Engine.
- La simulation initiale ne renvoyait qu'un nombre de jours et ne permettait pas d'expliquer les blocages.

## TCONGE

La formule canonique est `CongeCalculateurEngine.calculerTauxJournalierMoyen`. `CongeTauxApplicableService` conserve la résolution historique, le cutover et le SQL. Paie persiste les snapshots nécessaires à la reproductibilité et ne contient pas de seconde formule de division concurrente détectée.
