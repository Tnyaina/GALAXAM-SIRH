-- =====================================================================
-- GALAXAM SIRH — MIGRATION SP78 : CLÔTURE ADMINISTRATIVE DU DOSSIER DE SORTIE
-- Ajoute les colonnes de traçabilité de clôture et de dérogation éventuelle.
-- Date : 2026-10-16
-- =====================================================================

ALTER TABLE paie_dossier_sortie
    ADD COLUMN IF NOT EXISTS motif_cloture TEXT,
    ADD COLUMN IF NOT EXISTS derogation_reglement BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS justification_derogation TEXT;

COMMENT ON COLUMN paie_dossier_sortie.motif_cloture IS 'Motif ou observation finale renseignée lors de la clôture administrative du dossier';
COMMENT ON COLUMN paie_dossier_sortie.derogation_reglement IS 'Indique si la clôture a été autorisée par dérogation malgré un règlement financier non soldé';
COMMENT ON COLUMN paie_dossier_sortie.justification_derogation IS 'Justification formelle obligatoire en cas de clôture par dérogation avec solde restant';
