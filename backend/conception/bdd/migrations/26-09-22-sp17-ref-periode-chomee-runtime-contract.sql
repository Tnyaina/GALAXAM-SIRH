-- ============================================================================
-- Migration : 26-09-22-sp17-ref-periode-chomee-runtime-contract.sql
-- Description : Aligne ref_periode_chomee avec le contrat runtime
--               RefPeriodeChomee / CalendrierFerieController.
-- ============================================================================

ALTER TABLE ref_periode_chomee
    ADD COLUMN IF NOT EXISTS libelle VARCHAR(150),
    ADD COLUMN IF NOT EXISTS taux_paiement NUMERIC(5,2) DEFAULT 100.00;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'ref_periode_chomee'
          AND column_name = 'motif'
    ) THEN
        UPDATE ref_periode_chomee
        SET libelle = COALESCE(libelle, motif, 'Période chômée')
        WHERE libelle IS NULL;
    ELSE
        UPDATE ref_periode_chomee
        SET libelle = COALESCE(libelle, 'Période chômée')
        WHERE libelle IS NULL;
    END IF;
END $$;

UPDATE ref_periode_chomee
SET taux_paiement = 100.00
WHERE taux_paiement IS NULL;

ALTER TABLE ref_periode_chomee
    ALTER COLUMN libelle SET NOT NULL,
    ALTER COLUMN date_fin DROP NOT NULL,
    ALTER COLUMN taux_paiement SET DEFAULT 100.00;

COMMENT ON COLUMN ref_periode_chomee.libelle IS 'Libellé métier de la période chômée exceptionnelle.';
COMMENT ON COLUMN ref_periode_chomee.taux_paiement IS 'Taux de paiement appliqué à la période chômée, 100.00 par défaut.';
