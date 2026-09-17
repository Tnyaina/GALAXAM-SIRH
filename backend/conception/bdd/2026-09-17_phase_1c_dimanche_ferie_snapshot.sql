-- Migration Versionnée Phase 1C : Ajout du stockage des heures Dimanche et Férié dans le snapshot Pointage
-- Table : pointage_cumul_periode
-- Date : 2026-09-17

BEGIN;

ALTER TABLE pointage_cumul_periode
    ADD COLUMN IF NOT EXISTS heures_dimanche_total DECIMAL(6,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS heures_ferie_total DECIMAL(6,2) NOT NULL DEFAULT 0;

COMMIT;
