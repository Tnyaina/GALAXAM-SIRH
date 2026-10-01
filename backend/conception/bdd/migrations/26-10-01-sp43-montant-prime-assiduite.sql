-- =====================================================================
-- GALAXAM SIRH — MIGRATION 26-10-01-sp43 : MONTANT FORFAITAIRE PRIME ASSIDUITE
-- Ajout du paramètre global pour le montant forfaitaire de la prime d'assiduité.
-- =====================================================================

INSERT INTO parametre_global (code_parametre, categorie, valeur, description, date_debut, date_fin, est_actif, est_desactivable)
VALUES 
('MONTANT_PRIME_ASSIDUITE', 'PAIE', '50000', 'Montant forfaitaire mensuel de la prime d''assiduité (accordé si retards < seuil)', '2001-07-01', NULL, TRUE, TRUE)
ON CONFLICT (code_parametre, date_debut) DO UPDATE 
SET valeur = EXCLUDED.valeur, description = EXCLUDED.description, est_actif = EXCLUDED.est_actif, est_desactivable = EXCLUDED.est_desactivable;
