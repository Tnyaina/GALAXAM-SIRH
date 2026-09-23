# Domaine Paie

Ce dossier regroupe le moteur de paie, la préparation et le calcul des bulletins, les campagnes de paie, le solde de tout compte (STC) et les exports fiscaux/sociaux.

- `api/` : contrôleurs REST (`PaieController`, `PaieAjustementController`, `PaieRubriqueController`, `StcController`)
- `api/dto/` : contrats DTO (`PayrollCampaignStep`, `CalculPeriodeRequest`, `stc/*`, etc.)
- `application/` : services applicatifs (`PayrollService`, `StcService`, `workflow/`, `export/`)
- `domain/` : moteurs de calculs purs et invariants fiscaux (`engine/`, `dto/`)
- `persistence/` : entités de paie et tables historiques (`BulletinPaie`, `BulletinPaieLigne`, `PaiePret`, `PaieAvanceAcompte`, `PaieStc`, etc.)

Toutes les routes d'origine restent préservées sous `/paie/*`, `/stc/*`, `/api/paie/*`.
