-- Plafond des avances et échéancier, distincts de la quotité cessible.
CREATE TABLE IF NOT EXISTS paie_avance_regle (
    id SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    plafond_salaire_base_pct NUMERIC(7,2) NOT NULL DEFAULT 100.00
        CHECK (plafond_salaire_base_pct BETWEEN 0 AND 500),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by INT REFERENCES utilisateur(id_utilisateur)
);
INSERT INTO paie_avance_regle (id) VALUES (1) ON CONFLICT (id) DO NOTHING;

ALTER TABLE paie_avance_acompte
    ADD COLUMN IF NOT EXISTS nombre_mensualites INT NOT NULL DEFAULT 1
        CHECK (nombre_mensualites BETWEEN 1 AND 120);

-- Un choix ne vaut que pour une avance et une campagne; le recalcul peut le relire.
CREATE TABLE IF NOT EXISTS paie_avance_retenue_choix (
    id_avance INT NOT NULL REFERENCES paie_avance_acompte(id_avance) ON DELETE CASCADE,
    id_periode_paie INT NOT NULL REFERENCES periode_paie(id_periode_paie),
    montant NUMERIC(20,2) NOT NULL CHECK (montant > 0),
    motif VARCHAR(255) NOT NULL CHECK (length(trim(motif)) >= 3),
    choisi_par INT NOT NULL REFERENCES utilisateur(id_utilisateur),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id_avance, id_periode_paie)
);
