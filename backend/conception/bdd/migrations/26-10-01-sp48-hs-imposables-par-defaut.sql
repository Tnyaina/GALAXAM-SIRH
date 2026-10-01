-- Décision de paramétrage : HS imposables par défaut.
-- Les bulletins enregistrés ne sont pas recalculés par cette migration.
-- L'exonération reste disponible via une activation explicite du paramètre global
-- et de la configuration légale applicable.
BEGIN;
ALTER TABLE paie_config_legale
    ALTER COLUMN exoneration_hs_irsa_active SET DEFAULT FALSE;
UPDATE parametre_global
SET valeur = 'FALSE'
WHERE code_parametre = 'EXONERATION_HS_IRSA_ACTIVE';
COMMIT;
