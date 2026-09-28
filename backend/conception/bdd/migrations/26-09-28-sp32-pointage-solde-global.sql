-- Nouveaux snapshots : rapprochement HS30 + HS50 - déficit + ajustement DAF.
-- NULL distingue les snapshots historiques ; ne jamais réécrire une paie figée.
ALTER TABLE pointage_cumul_periode ADD COLUMN IF NOT EXISTS minutes_hs50_finales INTEGER;
COMMENT ON COLUMN pointage_cumul_periode.minutes_hs50_finales IS
'HS50 après rapprochement global de période ; NULL = ancien rapprochement HS30 seulement';
