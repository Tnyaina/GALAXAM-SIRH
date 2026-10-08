-- Parcours des prestataires indépendants, distinct des bulletins de paie.
CREATE TABLE IF NOT EXISTS prestataire_independant (
    id_prestataire SERIAL PRIMARY KEY,
    nom VARCHAR(200) NOT NULL,
    nif VARCHAR(50) NOT NULL UNIQUE CHECK (BTRIM(nif) <> ''),
    stat VARCHAR(50),
    adresse TEXT,
    email VARCHAR(200),
    est_actif BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS prestataire_contrat (
    id_contrat_prestataire SERIAL PRIMARY KEY,
    id_prestataire INTEGER NOT NULL REFERENCES prestataire_independant(id_prestataire),
    reference VARCHAR(100) NOT NULL UNIQUE,
    objet TEXT NOT NULL,
    date_debut DATE NOT NULL,
    date_fin DATE,
    tarif_convenu NUMERIC(20,2),
    chemin_piece VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_prestataire_contrat_dates CHECK (date_fin IS NULL OR date_fin >= date_debut)
);
CREATE TABLE IF NOT EXISTS prestataire_facture (
    id_facture_prestataire SERIAL PRIMARY KEY,
    id_contrat_prestataire INTEGER NOT NULL REFERENCES prestataire_contrat(id_contrat_prestataire),
    reference VARCHAR(100) NOT NULL,
    date_facture DATE NOT NULL,
    montant NUMERIC(20,2) NOT NULL CHECK (montant > 0),
    chemin_piece VARCHAR(500),
    statut VARCHAR(20) NOT NULL DEFAULT 'BROUILLON'
        CHECK (statut IN ('BROUILLON','VALIDEE','PAYEE','ANNULEE')),
    valide_par INTEGER,
    date_validation TIMESTAMPTZ,
    date_paiement DATE,
    reference_paiement VARCHAR(150),
    paye_par INTEGER,
    motif_annulation TEXT,
    annule_par INTEGER,
    date_annulation TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_prestataire_facture_piece CHECK (statut NOT IN ('VALIDEE','PAYEE') OR chemin_piece IS NOT NULL),
    CONSTRAINT chk_prestataire_facture_paiement CHECK (statut <> 'PAYEE' OR
        (date_paiement IS NOT NULL AND reference_paiement IS NOT NULL AND date_paiement >= date_facture)),
    CONSTRAINT chk_prestataire_facture_annulation CHECK (statut <> 'ANNULEE' OR
        (motif_annulation IS NOT NULL AND annule_par IS NOT NULL AND date_annulation IS NOT NULL)),
    UNIQUE (id_contrat_prestataire, reference)
);
CREATE INDEX IF NOT EXISTS idx_prestataire_facture_statut ON prestataire_facture(statut);
-- Permet aussi de compléter un schéma SP63 créé avant l'ajout de l'annulation.
ALTER TABLE prestataire_facture ADD COLUMN IF NOT EXISTS motif_annulation TEXT;
ALTER TABLE prestataire_facture ADD COLUMN IF NOT EXISTS annule_par INTEGER;
ALTER TABLE prestataire_facture ADD COLUMN IF NOT EXISTS date_annulation TIMESTAMPTZ;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'prestataire_facture'::regclass
            AND conname = 'chk_prestataire_facture_annulation') THEN
        ALTER TABLE prestataire_facture ADD CONSTRAINT chk_prestataire_facture_annulation
            CHECK (statut <> 'ANNULEE' OR
                (motif_annulation IS NOT NULL AND annule_par IS NOT NULL AND date_annulation IS NOT NULL));
    END IF;
END $$;
