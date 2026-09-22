-- ============================================================================
-- Migration : 26-09-22-sp16-pointage-cumul-heures-dimanche-ferie.sql
-- Description : Ajout des colonnes heures_dimanche_total et heures_ferie_total
--               dans pointage_cumul_periode pour alignement strict avec le schéma
--               05_schema_pointage.sql et le moteur PayrollService.
-- ============================================================================

ALTER TABLE pointage_cumul_periode
    ADD COLUMN IF NOT EXISTS heures_dimanche_total DECIMAL(6,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS heures_ferie_total DECIMAL(6,2) NOT NULL DEFAULT 0;

COMMENT ON COLUMN pointage_cumul_periode.heures_dimanche_total IS 'Cumul officiel des heures travaillées le dimanche scellées pour la paie.';
COMMENT ON COLUMN pointage_cumul_periode.heures_ferie_total IS 'Cumul officiel des heures travaillées un jour férié scellées pour la paie.';
