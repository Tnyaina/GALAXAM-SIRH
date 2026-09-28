BEGIN;

-- Une date de calcul du bulletin ne prouve pas un versement. Les anciennes dates
-- restent conservées pour contrôle, mais ne confirment aucun paiement historique.
ALTER TABLE paie_avance_acompte
    ADD COLUMN IF NOT EXISTS versement_confirme_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS versement_confirme_par INT REFERENCES utilisateur(id_utilisateur),
    ADD COLUMN IF NOT EXISTS reference_versement VARCHAR(120);

-- Chaque retenue de bulletin est tracée séparément pour suivre un reliquat sur
-- plusieurs périodes et annuler exactement une imputation lors d'un recalcul.
CREATE TABLE IF NOT EXISTS paie_mouvement_imputation (
    id_imputation SERIAL PRIMARY KEY,
    id_avance INT NOT NULL REFERENCES paie_avance_acompte(id_avance) ON DELETE RESTRICT,
    id_bulletin INT NOT NULL REFERENCES bulletin_paie(id_bulletin) ON DELETE CASCADE,
    montant NUMERIC(12,2) NOT NULL CHECK (montant > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_mouvement_imputation_bulletin UNIQUE (id_avance, id_bulletin)
);
CREATE INDEX IF NOT EXISTS idx_mouvement_imputation_bulletin ON paie_mouvement_imputation(id_bulletin);

COMMIT;
