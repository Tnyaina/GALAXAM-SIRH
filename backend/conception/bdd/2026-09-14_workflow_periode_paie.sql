BEGIN;

ALTER TABLE periode_paie
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 1;

CREATE TABLE IF NOT EXISTS periode_paie_transition (
    id_transition BIGSERIAL PRIMARY KEY,
    id_periode_paie INTEGER NOT NULL REFERENCES periode_paie(id_periode_paie),
    commande VARCHAR(30) NOT NULL,
    statut_source VARCHAR(20) NOT NULL,
    statut_cible VARCHAR(20) NOT NULL,
    version_source INTEGER NOT NULL,
    version_cible INTEGER NOT NULL,
    id_utilisateur INTEGER REFERENCES utilisateur(id_utilisateur),
    motif TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_periode_paie_transition_periode
    ON periode_paie_transition(id_periode_paie, version_cible);

COMMIT;
