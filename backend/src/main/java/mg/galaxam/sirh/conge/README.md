# Domaine Congé

Ce dossier regroupe l'ensemble du cycle de vie des congés : demandes, validation, calcul des droits, gestion des soldes et des périodes de référence.

- `api/` : contrôleurs REST (`DemandeCongeController`, `CongeSoldeController`, `CongePeriodeReferenceController`, `CongeRefController`)
- `application/` : `CongeService` porte le workflow des demandes et ses intégrations pointage ; `CongeContinuiteService` vérifie le poste/site sur chaque segment d'affectation d'une demande ; `CongeTauxApplicableService` porte le cycle civil de référence, le TCONGE et le cutover ; `CongeDecompteCalendaireResolver` applique la politique datée par jour ; `CongeValorisationMensuelleService` porte les corrections mensuelles auditées ; `CongeLotValorisationService` porte les lots FIFO et leurs valorisations. Les services d'export restent séparés de ces écritures.
- `domain/` : moteurs de calcul sans JDBC (`CongeCalculateurEngine`, `PurgeFifoEngine`, `CongeLotValorisationEngine`) et contrats de requête/réponse.
- Exports Excel : `CongeAnnuelExcelExportService` et `CongeGrandRecapExcelExportService` ; génération seule, sans écriture de règles métier.
- `persistence/` : entités et requêtes SQL (`DemandeConge`, `MouvementSoldeConge`, `RefTypeConge`, `VSoldeCongeEmploye`, `VDemandeCongeCpl`, etc.)

Les routes publiques restent sous `/conges/*`, notamment `/conges/periodes-reference/employe/{idEmploye}/export-excel` (canevas `resources/templates/annuel/ETAT_CONGES_PAR_PERSONNE.xlsx`).

La reprise historique exige la référence identifiable de l'arrêté DAF ; elle
reste conservée dans les commentaires du taux de reprise et du mouvement de
stock initial. Le grand récapitulatif lit filiale, site et poste depuis
`EmploymentContextResolver` à la date de fin du rapport.
