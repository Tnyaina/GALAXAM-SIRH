# Domaine Contrat & Gestion du Personnel

`application/EmploymentContextResolver` porte la résolution commune contrat /
affectation à une date ou sur une plage. Les méthodes historiques exigent des
dates métier explicites ; `resolveCurrent` est le seul raccourci pour aujourd'hui.
Les contrats et affectations aujourd'hui clôturés restent résolus aux dates
qu'ils couvraient. La résolution groupée des sites par jour charge les segments
en une requête et bloque les chevauchements. Les domaines clients gardent leurs
règles propres (tarifs, calendrier, cotisations).

Ce dossier regroupe le cycle complet du personnel, des contrats et de l'évolution de carrière.

- `api/` : contrôleurs REST (`EmployeController`, `ContratTravailController`, `ContratGlobalController`, `WorkflowRhController`, `RefCarriereController`, `DocumentRhController`)
- `api/dto/` : contrats DTO (`EmployeRequest`, `ContratTravailGlobalDTO`, `ContratTravailDocumentDTO`, `EmbaucheInitialeRequest`, `TransitionCarriereRequest`, `ValiderWorkflowRequest`, etc.)
- `application/` : services applicatifs (`EmployeService`, `WorkflowRhService`, `DocumentRhService` - moteur de génération contractuelle Word .docx via Apache POI, `EmploymentContextResolver`, `EmployerIdentityContextResolver`, `CarriereExportService`)
- `domain/` : moteurs et règles métier (`CarriereTransitionValidator`, `ContratValidationEngine`, `AncienneteEngine`, `EmployeValidationEngine`, `MatriculeGeneratorEngine`)
- `persistence/` : entités Active Record et requêtes SQL (`Employe`, `EmployeCPL`, `ContratTravail`, `WorkflowRh`, `DocumentRh`, `DocumentRhCPL`, `EmployeConjoint`, `EmployeEnfant`, etc.)

Les routes `/employes/*`, `/contrats/*`, `/documents/*`, `/ref-carriere/*` et
`/ref-types-documents/*` exigent une authentification (`SecurityConfig`). Les
contrôleurs appliquent ensuite leurs permissions : `EMPLOYE_READ` pour les
lectures de fiches, contrats et workflows, `EMPLOYE_WRITE` pour leurs écritures,
et `EMPLOYE_VALIDATE` pour la validation des workflows (ou `ROLE_ADMIN` selon
les annotations). La désactivation et la réactivation d'un employé acceptent
`EMPLOYE_VALIDATE` ou `EMPLOYE_WRITE` ; les coordonnées bancaires nécessitent
`PAIE_READ` en lecture et `PAIE_WRITE` en écriture. La lecture et le téléchargement
des documents RH et des contrats générés exigent ensemble `EMPLOYE_READ` et
`PAIE_READ`, ou `ROLE_ADMIN` ; les attestations et certificats générés exigent
`EMPLOYE_READ` ou `ROLE_ADMIN`. Les lectures des référentiels de carrière et des
types de documents exigent une authentification ; leurs mutations exposées sont
réservées à `ROLE_ADMIN`.

`DocumentRhController` expose `GET /documents/contrat/{idContrat}` et
`GET /documents/contrat/{idContrat}/docx` ; aucune route `/preview` distincte
n'est déclarée. Modèles de contrats RH :
`resources/templates/documents/Contrat_{TYPE}_modele.docx` (CDI, CDD, STAGE,
PRESTATION, TEMPORAIRE). Le modèle RH `PRESTATION` est conservé pour les dossiers
historiques ; les nouvelles missions indépendantes passent par le module
Prestataires indépendants, avec dépôt du contrat signé en PDF (voir
`../paie/PRESTATAIRES_2026.md`).
