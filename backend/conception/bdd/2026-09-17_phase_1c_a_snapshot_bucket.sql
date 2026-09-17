-- Migration Phase 1C-A : QualifiedTimeBucket Snapshot Table
-- Table : pointage_snapshot_bucket
-- Date : 2026-09-17

BEGIN;

CREATE TABLE IF NOT EXISTS pointage_snapshot_bucket (
    id_snapshot_bucket SERIAL PRIMARY KEY,
    id_cumul INTEGER NOT NULL REFERENCES pointage_cumul_periode(id_cumul) ON DELETE RESTRICT,
    id_pointage INTEGER REFERENCES pointage_jour(id_pointage) ON DELETE RESTRICT,
    date_debut TIMESTAMP NOT NULL,
    date_fin TIMESTAMP NOT NULL,
    minutes INTEGER NOT NULL CHECK (minutes > 0),
    overtime_tier VARCHAR(20) NOT NULL,
    day_special_type VARCHAR(20) NOT NULL,
    night_work_type VARCHAR(30) NOT NULL,
    id_periode_paie INTEGER NOT NULL REFERENCES periode_paie(id_periode_paie),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_snapshot_bucket_cumul ON pointage_snapshot_bucket(id_cumul);
CREATE INDEX IF NOT EXISTS idx_snapshot_bucket_periode ON pointage_snapshot_bucket(id_periode_paie);
CREATE INDEX IF NOT EXISTS idx_snapshot_bucket_dates ON pointage_snapshot_bucket(date_debut, date_fin);

COMMIT;
