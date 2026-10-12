-- PaieConfigLegale est l'unique source de l'exonération HS et de son plafond.
-- TCONGE valorise les congés payés ; la retenue de base reste calculée au 1/30e.
-- Les anciennes lignes globales sont clôturées et conservées pour l'audit.
BEGIN;

UPDATE parametre_global
SET est_actif = FALSE,
    date_fin = CASE
        WHEN date_debut <= CURRENT_DATE
         AND (date_fin IS NULL OR date_fin >= CURRENT_DATE)
        THEN CURRENT_DATE - 1
        ELSE date_fin
    END
WHERE code_parametre IN ('EXONERATION_HS_IRSA_ACTIVE', 'PLAFOND_MENSUEL_HS_EXONEREES');

ALTER TABLE paie_config_legale
    DROP COLUMN IF EXISTS diviseur_valorisation_conge_jours;

COMMIT;
