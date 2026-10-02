-- Versionner les quotités légales avec le millésime de paie, sans table supplémentaire.
ALTER TABLE paie_config_legale
    ADD COLUMN IF NOT EXISTS plafond_avance_employeur_pct NUMERIC(9,6) NOT NULL DEFAULT 10.000000,
    ADD COLUMN IF NOT EXISTS quotite_cessible_pct NUMERIC(9,6) NOT NULL DEFAULT 33.33;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='chk_plafond_avance_pct') THEN
        ALTER TABLE paie_config_legale ADD CONSTRAINT chk_plafond_avance_pct CHECK (plafond_avance_employeur_pct BETWEEN 0 AND 100);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='chk_quotite_cessible_pct') THEN
        ALTER TABLE paie_config_legale ADD CONSTRAINT chk_quotite_cessible_pct CHECK (quotite_cessible_pct BETWEEN 0 AND 100);
    END IF;
END $$;
