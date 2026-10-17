-- =========================================================================
-- GALAXAM SIRH — Migration Sprint 79 : Idempotence Technique Règlements STC (Phase 3.1)
-- =========================================================================
-- Ajout de la clé d'idempotence technique et index d'unicité sur paie_stc_reglement
-- Porté par une migration versionnée dédiée sans modifier rétroactivement SP75.
-- =========================================================================

ALTER TABLE paie_stc_reglement 
ADD COLUMN IF NOT EXISTS cle_idempotence VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uk_stc_reglement_idempotence 
ON paie_stc_reglement(id_stc, cle_idempotence) 
WHERE cle_idempotence IS NOT NULL;
