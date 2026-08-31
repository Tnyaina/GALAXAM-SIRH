-- ============================================================
-- PATCH MIGRATION : FRACTIONNEMENT DEMI-JOURNÉE INDEMNITÉS (POINTAGE)
-- Date : 31/08/2026
-- Description : Ajout de la colonne fractionnable dans ref_indemnite
-- ============================================================

-- 1. Ajout de la colonne fractionnable sur ref_indemnite
ALTER TABLE ref_indemnite 
    ADD COLUMN IF NOT EXISTS fractionnable BOOLEAN DEFAULT FALSE;

-- 2. Initialisation des indemnités de déplacement et transport comme fractionnables (Matin / Soir)
UPDATE ref_indemnite 
SET fractionnable = TRUE 
WHERE code IN ('DEPLACEMENT', 'TRANSPORT');

COMMENT ON COLUMN ref_indemnite.fractionnable IS 'Indique si l''indemnité est fractionnable par demi-journée (ex: trajet matin à 50% et trajet soir à 50%) lors de la saisie quotidienne du pointage';
