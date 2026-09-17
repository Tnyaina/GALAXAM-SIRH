-- Migration Phase 1C-A : Extension day_special_type pour coexistence SUNDAY_AND_PUBLIC_HOLIDAY
-- Date : 2026-09-17

BEGIN;

ALTER TABLE pointage_snapshot_bucket ALTER COLUMN day_special_type TYPE VARCHAR(40);

COMMIT;
