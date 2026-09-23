# Audit de structure PostgreSQL

Périmètre : les six schémas canoniques versionnés. Il n'y a pas de connexion à une base de production dans cet audit ; `volume attendu` et références externes restent donc `UNCERTAIN` hors tests et SQL versionné.

## Décision par groupe

| Domaine / tables | Décision | Propriétaire et justification |
| --- | --- | --- |
| `ref_role`, `ref_module`, `role_permission`, `utilisateur`, `journal_connexion`, `audit_log`, `scheduler_execution`, `ref_menu`, `role_menu`, `parametre_global` | KEEP | sécurité/administration ; accès, audit, configuration et jobs ont des cycles distincts |
| `ref_filiale`, `ref_banque`, `ref_site`, `ref_departement`, `ref_service`, `ref_fonction`, référentiels RH et calendrier | KEEP | organisation/référentiels ; identités et références partagées |
| `employe`, proches, `contrat_travail`, `contrat_periode_essai`, `employe_affectation_carriere`, `workflow_rh`, documents, notifications | KEEP | employés/carrière ; historique contractuel et workflow ne doivent pas être écrasés |
| tables congés, mouvements, périodes de référence et taux applicables | KEEP | congés ; les mouvements et taux datés sont la piste d'audit du solde |
| `periode_paie`, transitions, pointages, cumuls, snapshots et indemnités | KEEP | pointage/présence ; snapshots et cumuls ont un rôle de clôture distinct de la saisie journalière |
| barèmes, cotisations, rubriques, bulletins, lignes, avances, prêts, prestations, STC et journal de droits | KEEP | paie ; configurations datées et résultats validés doivent rester historiques |

## Candidats et garde-fous

Aucune table `DROP_CANDIDATE` ou `MERGE_CANDIDATE` sûre n'a été trouvée dans le schéma versionné. Les couples apparemment redondants (`pointage_jour`/snapshots, bulletin/lignes, période/transitions, solde/mouvements) sont en réalité des séparations entre saisie, agrégation, transition et preuve historique.

Toute proposition future doit fournir : dépendances SQL et Java, comptage avant/après, plan de reprise, contraintes/FK/index, et test PostgreSQL. Les scripts sous `archive_migrations_historiques/` sont historiques ; les six `0N_schema_*.sql` sont le bootstrap, et les changements incrémentaux vont sous `migrations/`.
