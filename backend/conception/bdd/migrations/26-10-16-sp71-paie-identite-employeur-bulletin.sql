-- Identité de l'employeur figée à la validation du bulletin.
-- Aucun remplissage rétroactif : les anciens documents restent explicitement non figés.
CREATE TABLE IF NOT EXISTS bulletin_paie_employeur_snapshot (
    id_bulletin INT PRIMARY KEY REFERENCES bulletin_paie(id_bulletin) ON DELETE CASCADE,
    id_filiale INT NOT NULL,
    raison_sociale VARCHAR(200) NOT NULL,
    nif VARCHAR(100),
    stat VARCHAR(100),
    adresse_siege TEXT,
    telephone VARCHAR(100),
    email VARCHAR(255),
    captured_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE bulletin_paie_employeur_snapshot ALTER COLUMN nif DROP NOT NULL;
