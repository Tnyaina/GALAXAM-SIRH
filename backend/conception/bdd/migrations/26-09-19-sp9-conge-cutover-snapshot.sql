-- ==============================================================================
-- SP9 — CONGES & PAIE : Extension Cutover Snapshot & Périodes de Référence Découplées
-- Date : 2026-09-19
-- Description :
--   1. Extension de conge_periode_reference pour supporter les modes de reprise
--      sans fabrication d'historique (FULL_HISTORY, AGGREGATED_OPENING, MINIMAL_OPENING, NEW_HIRE).
--   2. Ajout des colonnes date_cutover, cumul_droits_ouverture, cumul_valorisation_ouverture.
--   3. Extension de la contrainte chk_source_provenance sur conge_taux_applicable
--      pour inclure 'REPRISE_CUTOVER'.
-- ==============================================================================

-- 1. Ajout des colonnes de Cutover sur conge_periode_reference
ALTER TABLE conge_periode_reference
    ADD COLUMN IF NOT EXISTS mode_reprise VARCHAR(30) NOT NULL DEFAULT 'FULL_HISTORY',
    ADD COLUMN IF NOT EXISTS date_cutover DATE,
    ADD COLUMN IF NOT EXISTS cumul_droits_ouverture NUMERIC(8,4),
    ADD COLUMN IF NOT EXISTS cumul_valorisation_ouverture NUMERIC(12,2);

-- Contrainte de cohérence sur mode_reprise
ALTER TABLE conge_periode_reference
    DROP CONSTRAINT IF EXISTS chk_mode_reprise;

ALTER TABLE conge_periode_reference
    ADD CONSTRAINT chk_mode_reprise CHECK (
        mode_reprise IN ('FULL_HISTORY', 'AGGREGATED_OPENING', 'MINIMAL_OPENING', 'NEW_HIRE')
    );

-- 2. Mise à jour de la contrainte sur conge_taux_applicable pour accepter 'REPRISE_CUTOVER'
ALTER TABLE conge_taux_applicable
    DROP CONSTRAINT IF EXISTS chk_source_provenance;

ALTER TABLE conge_taux_applicable
    ADD CONSTRAINT chk_source_provenance CHECK (
        source_provenance IN ('KAJY_MIGRATION', 'CALCUL_PERIODE_REFERENCE', 'INITIALISATION_BASE_30', 'REPRISE_CUTOVER')
    );
