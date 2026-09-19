-- =====================================================================
-- GALAXAM SIRH — MIGRATION : PARAMÉTRAGE EXONÉRATION FISCALE HS (CGI 01.03.03-9°)
-- Rend paramétrables les seuils, l'activation fiscale et l'éligibilité par catégorie.
-- =====================================================================

-- 1. Paramètres fiscaux légaux par exercice (paie_config_legale)
ALTER TABLE paie_config_legale 
ADD COLUMN IF NOT EXISTS exoneration_hs_irsa_active BOOLEAN NOT NULL DEFAULT TRUE,
ADD COLUMN IF NOT EXISTS plafond_mensuel_hs_exonerees NUMERIC(5,2) NOT NULL DEFAULT 20.00;

-- 2. Paramétrage d'éligibilité par catégorie professionnelle (ref_categorie_professionnelle)
ALTER TABLE ref_categorie_professionnelle 
ADD COLUMN IF NOT EXISTS est_eligible_exoneration_hs BOOLEAN NOT NULL DEFAULT TRUE;

-- Par défaut, les cadres sont exclus de l'exonération conformément au texte fiscal
UPDATE ref_categorie_professionnelle 
SET est_eligible_exoneration_hs = FALSE 
WHERE est_cadre = TRUE OR code = 'CADRE';

-- 3. Paramètres dynamiques globaux (parametre_global)
INSERT INTO parametre_global (code_parametre, categorie, valeur, description, date_debut, est_actif, est_desactivable) 
VALUES 
('EXONERATION_HS_IRSA_ACTIVE', 'PAIE', 'TRUE', 'Activation globale de l exonération fiscale des heures supplémentaires (CGI 01.03.03-9°)', '2026-01-01', TRUE, TRUE),
('PLAFOND_MENSUEL_HS_EXONEREES', 'PAIE', '20.00', 'Plafond mensuel légal d heures supplémentaires exonérées d IRSA (CGI 01.03.03-9°)', '2026-01-01', TRUE, TRUE)
ON CONFLICT (code_parametre, date_debut) DO UPDATE 
SET valeur = EXCLUDED.valeur, description = EXCLUDED.description, est_actif = EXCLUDED.est_actif;
