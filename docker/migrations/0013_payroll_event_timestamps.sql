-- Payroll staleness checks compare these event instants with bulletin_paie.date_calcul.
-- Preserve the meaning of existing DEV values while giving future writes one type.
-- Two read views expose the columns being converted. Preserve their current
-- definitions while changing the underlying types in this same transaction.
DO $migration$
DECLARE
    leave_view text := pg_get_viewdef('v_demande_conge_cpl'::regclass, true);
    career_view text := pg_get_viewdef('v_historique_carriere_global'::regclass, true);
BEGIN
    DROP VIEW v_demande_conge_cpl;
    DROP VIEW v_historique_carriere_global;

    -- RH timestamps were stored as Antananarivo wall time by the application.
    ALTER TABLE contrat_travail
        ALTER COLUMN created_at TYPE timestamptz USING created_at AT TIME ZONE 'Indian/Antananarivo',
        ALTER COLUMN updated_at TYPE timestamptz USING updated_at AT TIME ZONE 'Indian/Antananarivo';

    ALTER TABLE employe_affectation_carriere
        ALTER COLUMN created_at TYPE timestamptz USING created_at AT TIME ZONE 'Indian/Antananarivo',
        ALTER COLUMN updated_at TYPE timestamptz USING updated_at AT TIME ZONE 'Indian/Antananarivo';

    -- Existing leave creation/update values were stored as UTC wall time.
    -- date_validation was supplied by the application as Antananarivo wall time.
    ALTER TABLE demande_conge
        ALTER COLUMN created_at TYPE timestamptz USING created_at AT TIME ZONE 'UTC',
        ALTER COLUMN updated_at TYPE timestamptz USING updated_at AT TIME ZONE 'UTC',
        ALTER COLUMN date_validation TYPE timestamptz USING date_validation AT TIME ZONE 'Indian/Antananarivo';

    EXECUTE 'CREATE VIEW v_demande_conge_cpl AS ' || leave_view;
    EXECUTE 'CREATE VIEW v_historique_carriere_global AS ' || career_view;
END
$migration$;
