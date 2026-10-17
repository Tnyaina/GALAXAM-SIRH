-- =====================================================================
-- GALAXAM SIRH — MIGRATION SP81 : GOUVERNANCE ET AUDITABILITÉ DE LA DÉROGATION DE CLÔTURE
-- Exige que la dérogation de règlement incomplet repose sur une décision formelle,
-- autorisée, identifiable (autorité, référence, date) et auditée.
-- Date : 2026-10-16
-- =====================================================================

ALTER TABLE paie_dossier_sortie
    ADD COLUMN IF NOT EXISTS autorite_decision_derogation VARCHAR(100),
    ADD COLUMN IF NOT EXISTS reference_decision_derogation VARCHAR(100),
    ADD COLUMN IF NOT EXISTS date_decision_derogation DATE;

COMMENT ON COLUMN paie_dossier_sortie.autorite_decision_derogation IS 'Autorité décisionnaire ayant formellement approuvé la dérogation de clôture avec solde restant (ex: DIRECTION_GENERALE, DAF, TRIBUNAL_DU_TRAVAIL)';
COMMENT ON COLUMN paie_dossier_sortie.reference_decision_derogation IS 'Numéro d''acte, protocole d''accord ou décision formelle écrite autorisant la dérogation';
COMMENT ON COLUMN paie_dossier_sortie.date_decision_derogation IS 'Date de signature ou d''effet de la décision formelle de dérogation';

ALTER TABLE paie_dossier_sortie
    DROP CONSTRAINT IF EXISTS chk_dossier_derogation_gouvernance;

ALTER TABLE paie_dossier_sortie
    ADD CONSTRAINT chk_dossier_derogation_gouvernance
    CHECK (derogation_reglement IS NOT TRUE OR (
        autorite_decision_derogation IS NOT NULL AND TRIM(autorite_decision_derogation) <> '' AND
        reference_decision_derogation IS NOT NULL AND TRIM(reference_decision_derogation) <> '' AND
        date_decision_derogation IS NOT NULL
    ));
