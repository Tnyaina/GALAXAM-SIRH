-- À appliquer uniquement après SELECT current_database() = 'sirh_galaxam_test' en test.
CREATE TABLE IF NOT EXISTS conge_valorisation_mensuelle_correction (
    id_correction SERIAL PRIMARY KEY,
    id_journal INT NOT NULL REFERENCES conge_journal_droit(id_journal),
    id_mouvement_acquisition INT NOT NULL REFERENCES mouvement_solde_conge(id_mouvement),
    ancienne_valeur NUMERIC(20,2) NOT NULL,
    nouvelle_valeur NUMERIC(20,2) NOT NULL CHECK (nouvelle_valeur >= 0),
    motif TEXT NOT NULL CHECK (length(trim(motif)) > 0),
    corrige_par INT NOT NULL REFERENCES utilisateur(id_utilisateur),
    date_correction TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_conge_valorisation_correction_journal
    ON conge_valorisation_mensuelle_correction(id_journal, date_correction);
