-- ============================================================
-- PATCH : Paramètres globaux pour le renouvellement et plafonds CDD
-- Date   : 2026-08-27
-- ============================================================

INSERT INTO parametre_global (code_parametre, categorie, valeur, description, date_debut) VALUES
('DUREE_MAX_CDD_MOIS', 'CONTRAT', '24', 'Durée maximale cumulée d''un CDD et de ses renouvellements (24 mois / 2 ans). Au-delà, requalification en CDI de plein droit.', '2026-01-01'),
('NB_MAX_RENOUVELLEMENTS_CDD', 'CONTRAT', '2', 'Nombre maximal de renouvellements d''un CDD par avenant (2 fois max). Au 3ème renouvellement, requalification en CDI.', '2026-01-01')
ON CONFLICT (code_parametre) DO UPDATE 
SET valeur = EXCLUDED.valeur, description = EXCLUDED.description;
