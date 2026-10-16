-- =========================================================================
-- GALAXAM SIRH — Migration Sprint 73 : Intégration Sécurisée STC (Phase 2)
-- =========================================================================
-- 1. Verrouillage optimiste et référence de simulation sur paie_dossier_sortie
-- 2. Sécurisation du journal paie_stc_reglement (ON DELETE RESTRICT)
-- =========================================================================

-- 1. Colonne de version pour verrouillage optimiste
ALTER TABLE paie_dossier_sortie 
ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 1;

-- 2. Référence et snapshot déterministe de la dernière simulation vérifiée
ALTER TABLE paie_dossier_sortie 
ADD COLUMN IF NOT EXISTS reference_simulation VARCHAR(100);

ALTER TABLE paie_dossier_sortie 
ADD COLUMN IF NOT EXISTS snapshot_simulation JSONB;

-- 3. Sécurisation du journal comptable des règlements : interdiction formelle de suppression en cascade
ALTER TABLE paie_stc_reglement 
DROP CONSTRAINT IF EXISTS paie_stc_reglement_id_stc_fkey;

ALTER TABLE paie_stc_reglement 
ADD CONSTRAINT paie_stc_reglement_id_stc_fkey 
FOREIGN KEY (id_stc) REFERENCES paie_stc(id_stc) ON DELETE RESTRICT;
