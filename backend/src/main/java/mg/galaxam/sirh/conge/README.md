# Domaine Congé

Ce dossier regroupe l'ensemble du cycle de vie des congés : demandes, validation, calcul des droits, gestion des soldes et des périodes de référence.

- `api/` : contrôleurs REST (`DemandeCongeController`, `CongeSoldeController`, `CongePeriodeReferenceController`, `CongeRefController`)
- `api/dto/` : contrats DTO pour la création de période et l'activation de taux
- `application/` : services applicatifs (`CongeService`, `CongeAnnuelExcelExportService` - export officiel Excel du bilan annuel sur 12 mois, `CongeTauxApplicableService`)
- `domain/` : moteurs de règles et calculs purs sans JDBC (`CongeCalculateurEngine`, `PurgeFifoEngine`, `ReferencePeriodPolicy`, `TauxCongeResolution`, etc.)
- `persistence/` : entités et requêtes SQL (`DemandeConge`, `MouvementSoldeConge`, `RefTypeConge`, `VSoldeCongeEmploye`, `VDemandeCongeCpl`, etc.)

Les routes publiques restent sous `/conges/*`, notamment `/conges/periodes-reference/employe/{idEmploye}/export-excel` (canevas `resources/templates/annuel/ETAT_CONGES_PAR_PERSONNE.xlsx`).
