-- Ajustements horaires de fin de période : ne modifient jamais les badges.
-- Les colonnes historiques en heures gardent assez de chiffres pour restituer
-- une minute entière après conversion ; deux décimales restent une présentation.
ALTER TABLE pointage_jour
    ALTER COLUMN heures_matin TYPE NUMERIC(10,8),
    ALTER COLUMN heures_am TYPE NUMERIC(10,8),
    ALTER COLUMN total_heures TYPE NUMERIC(10,8),
    ALTER COLUMN heures_nuit TYPE NUMERIC(10,8);
ALTER TABLE pointage_cumul_periode
    ALTER COLUMN total_heures_travaillees TYPE NUMERIC(12,8),
    ALTER COLUMN heures_hs_30 TYPE NUMERIC(12,8),
    ALTER COLUMN heures_hs_50 TYPE NUMERIC(12,8),
    ALTER COLUMN heures_nuit_total TYPE NUMERIC(12,8),
    ALTER COLUMN heures_dimanche_total TYPE NUMERIC(12,8),
    ALTER COLUMN heures_ferie_total TYPE NUMERIC(12,8),
    ALTER COLUMN heures_absence_retenues TYPE NUMERIC(12,8);

CREATE TABLE IF NOT EXISTS pointage_ajustement_periode (
    id_ajustement BIGSERIAL PRIMARY KEY,
    id_employe INTEGER NOT NULL REFERENCES employe(id_employe),
    id_periode_paie INTEGER NOT NULL REFERENCES periode_paie(id_periode_paie),
    minutes_signees INTEGER NOT NULL CHECK (minutes_signees <> 0),
    motif TEXT NOT NULL CHECK (length(btrim(motif)) >= 5),
    statut VARCHAR(12) NOT NULL DEFAULT 'VALIDE' CHECK (statut IN ('VALIDE', 'ANNULE')),
    saisi_par INTEGER REFERENCES utilisateur(id_utilisateur),
    valide_par INTEGER REFERENCES utilisateur(id_utilisateur),
    annule_par INTEGER REFERENCES utilisateur(id_utilisateur),
    date_saisie TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_annulation TIMESTAMP,
    motif_annulation TEXT
);
CREATE INDEX IF NOT EXISTS idx_pointage_ajustement_periode_employe
    ON pointage_ajustement_periode(id_periode_paie, id_employe, statut);

-- Les heures historiques restent dans les colonnes existantes. Les minutes
-- finales sont le contrat exact du rapprochement, sans arrondi à 0,01 h.
ALTER TABLE pointage_cumul_periode
    ADD COLUMN IF NOT EXISTS minutes_deficit_hebdo INTEGER,
    ADD COLUMN IF NOT EXISTS minutes_hs30_hebdo INTEGER,
    ADD COLUMN IF NOT EXISTS minutes_hs50_hebdo INTEGER,
    ADD COLUMN IF NOT EXISTS minutes_ajustement_periode INTEGER,
    ADD COLUMN IF NOT EXISTS minutes_solde_periode INTEGER,
    ADD COLUMN IF NOT EXISTS minutes_absence_finale INTEGER,
    ADD COLUMN IF NOT EXISTS minutes_hs30_finales INTEGER;

CREATE TABLE IF NOT EXISTS pointage_snapshot_ajustement (
    id_cumul INTEGER NOT NULL REFERENCES pointage_cumul_periode(id_cumul) ON DELETE RESTRICT,
    id_ajustement BIGINT NOT NULL REFERENCES pointage_ajustement_periode(id_ajustement) ON DELETE RESTRICT,
    minutes_signees INTEGER NOT NULL,
    motif TEXT NOT NULL,
    PRIMARY KEY (id_cumul, id_ajustement)
);
