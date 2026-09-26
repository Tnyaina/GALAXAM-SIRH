-- Les durées du bulletin conservent la précision du snapshot (minute entière).
-- Le rendu à deux décimales est une règle d'affichage uniquement.
ALTER TABLE bulletin_paie
    ALTER COLUMN heures_normales TYPE NUMERIC(12,8),
    ALTER COLUMN heures_hs_30 TYPE NUMERIC(12,8),
    ALTER COLUMN heures_hs_50 TYPE NUMERIC(12,8),
    ALTER COLUMN heures_nuit TYPE NUMERIC(12,8);
ALTER TABLE bulletin_paie
    ALTER COLUMN taux_horaire TYPE NUMERIC(12,8);

ALTER TABLE bulletin_paie_ligne
    ALTER COLUMN base_calcul TYPE NUMERIC(16,8),
    ALTER COLUMN quantite TYPE NUMERIC(12,8);
