# Domaine Contrat & Gestion du Personnel

Ce dossier regroupe le cycle complet du personnel, des contrats et de l'évolution de carrière.

- `api/` : contrôleurs REST (`EmployeController`, `ContratTravailController`, `ContratGlobalController`, `WorkflowRhController`, `RefCarriereController`, `DocumentRhController`, `DocumentOfficielRhController`, `EmployePhotoController`, `RefTypeDocumentController`)
- `api/dto/` : contrats DTO (`EmployeRequest`, `ContratTravailGlobalDTO`, `EmbaucheInitialeRequest`, `TransitionCarriereRequest`, `ValiderWorkflowRequest`, etc.)
- `application/` : services applicatifs (`EmployeService`, `WorkflowRhService`, `DocumentRhService`)
- `domain/` : moteurs et règles métier (`CarriereTransitionValidator`, `ContratValidationEngine`, `AncienneteEngine`, `EmployeValidationEngine`, `MatriculeGeneratorEngine`)
- `persistence/` : entités Active Record et requêtes SQL (`Employe`, `EmployeCPL`, `ContratTravail`, `WorkflowRh`, `DocumentRh`, `EmployeConjoint`, `EmployeEnfant`, etc.)

Les routes publiques restent sous `/employes/*`, `/contrats/*`, `/documents/*`, `/ref-carriere/*`, `/ref-types-documents/*`.
