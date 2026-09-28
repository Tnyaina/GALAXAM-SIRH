# Domaine Contrat & Gestion du Personnel

Ce dossier regroupe le cycle complet du personnel, des contrats et de l'évolution de carrière.

- `api/` : contrôleurs REST (`EmployeController`, `ContratTravailController`, `ContratGlobalController`, `WorkflowRhController`, `RefCarriereController`, `DocumentRhController`, `DocumentOfficielRhController`, `EmployePhotoController`, `RefTypeDocumentController`)
- `api/dto/` : contrats DTO (`EmployeRequest`, `ContratTravailGlobalDTO`, `ContratTravailDocumentDTO`, `EmbaucheInitialeRequest`, `TransitionCarriereRequest`, `ValiderWorkflowRequest`, etc.)
- `application/` : services applicatifs (`EmployeService`, `WorkflowRhService`, `DocumentRhService` - moteur de génération contractuelle Word .docx via Apache POI)
- `domain/` : moteurs et règles métier (`CarriereTransitionValidator`, `ContratValidationEngine`, `AncienneteEngine`, `EmployeValidationEngine`, `MatriculeGeneratorEngine`)
- `persistence/` : entités Active Record et requêtes SQL (`Employe`, `EmployeCPL`, `ContratTravail`, `WorkflowRh`, `DocumentRh`, `DocumentRhCPL`, `EmployeConjoint`, `EmployeEnfant`, etc.)

Les routes publiques restent sous `/employes/*`, `/contrats/*`, `/documents/*` (dont `/documents/contrat/{idContrat}/preview` et `/documents/contrat/{idContrat}/docx`), `/ref-carriere/*`, `/ref-types-documents/*`. Modèles de contrats officiels : `resources/templates/documents/Contrat_{TYPE}_modele.docx` (CDI, CDD, STAGE, PRESTATION, TEMPORAIRE).
