BEGIN;

ALTER TABLE pointage_cumul_periode
    ADD COLUMN IF NOT EXISTS version_snapshot INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS statut_snapshot VARCHAR(20) NOT NULL DEFAULT 'GELE',
    ADD COLUMN IF NOT EXISTS remplace_id_cumul INTEGER REFERENCES pointage_cumul_periode(id_cumul);

ALTER TABLE pointage_cumul_periode DROP CONSTRAINT IF EXISTS uk_cumul_employe_periode;
ALTER TABLE pointage_cumul_periode DROP CONSTRAINT IF EXISTS uk_cumul_employe_periode_version;
ALTER TABLE pointage_cumul_periode DROP CONSTRAINT IF EXISTS chk_cumul_version_positive;
ALTER TABLE pointage_cumul_periode DROP CONSTRAINT IF EXISTS chk_cumul_statut;
ALTER TABLE pointage_cumul_periode
    ADD CONSTRAINT uk_cumul_employe_periode_version UNIQUE (id_employe, id_periode_paie, version_snapshot),
    ADD CONSTRAINT chk_cumul_version_positive CHECK (version_snapshot > 0),
    ADD CONSTRAINT chk_cumul_statut CHECK (statut_snapshot IN ('GELE', 'CONSOMME', 'REMPLACE'));

CREATE UNIQUE INDEX IF NOT EXISTS uk_cumul_snapshot_courant
    ON pointage_cumul_periode(id_employe, id_periode_paie) WHERE statut_snapshot = 'GELE';

CREATE TABLE IF NOT EXISTS pointage_snapshot_jour (
    id_cumul INTEGER NOT NULL REFERENCES pointage_cumul_periode(id_cumul) ON DELETE RESTRICT,
    id_pointage INTEGER NOT NULL REFERENCES pointage_jour(id_pointage) ON DELETE RESTRICT,
    version_pointage INTEGER NOT NULL CHECK (version_pointage > 0),
    date_pointage DATE NOT NULL,
    minutes_travaillees INTEGER NOT NULL CHECK (minutes_travaillees >= 0),
    minutes_retard INTEGER NOT NULL CHECK (minutes_retard >= 0),
    minutes_nuit INTEGER NOT NULL CHECK (minutes_nuit >= 0),
    statut_jour VARCHAR(40) NOT NULL,
    PRIMARY KEY (id_cumul, id_pointage)
);

CREATE TABLE IF NOT EXISTS pointage_snapshot_semaine (
    id_cumul INTEGER NOT NULL REFERENCES pointage_cumul_periode(id_cumul) ON DELETE RESTRICT,
    id_semaine_calcule INTEGER NOT NULL REFERENCES pointage_semaine_calcule(id_semaine_calcule) ON DELETE RESTRICT,
    debut_semaine DATE NOT NULL,
    minutes_travaillees INTEGER NOT NULL CHECK (minutes_travaillees >= 0),
    minutes_hs_30 INTEGER NOT NULL CHECK (minutes_hs_30 >= 0),
    minutes_hs_50 INTEGER NOT NULL CHECK (minutes_hs_50 >= 0),
    PRIMARY KEY (id_cumul, id_semaine_calcule)
);

ALTER TABLE bulletin_paie
    ADD COLUMN IF NOT EXISTS id_snapshot_pointage INTEGER REFERENCES pointage_cumul_periode(id_cumul),
    ADD COLUMN IF NOT EXISTS version_snapshot_pointage INTEGER;

ALTER TABLE bulletin_paie DROP CONSTRAINT IF EXISTS chk_bulletin_snapshot_version;
ALTER TABLE bulletin_paie ADD CONSTRAINT chk_bulletin_snapshot_version
    CHECK (version_snapshot_pointage IS NULL OR version_snapshot_pointage > 0);

CREATE INDEX IF NOT EXISTS idx_bulletin_snapshot_pointage ON bulletin_paie(id_snapshot_pointage);

COMMIT;
