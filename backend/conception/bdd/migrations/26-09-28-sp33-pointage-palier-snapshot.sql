-- Historise le paramètre légal réellement utilisé ; aucune valeur arbitraire pour l'historique.
ALTER TABLE pointage_cumul_periode ADD COLUMN IF NOT EXISTS minutes_palier_hs30 INTEGER;
COMMENT ON COLUMN pointage_cumul_periode.minutes_palier_hs30 IS
'Palier HS30 lu dans paie_config_legale.seuil_hs_tranche1 à la date de la période ; NULL = non historisé';
