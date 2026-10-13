-- Rapprochement vérifiable du dernier bulletin mensuel et du STC.
ALTER TABLE paie_stc ADD COLUMN IF NOT EXISTS id_bulletin_mensuel INT REFERENCES bulletin_paie(id_bulletin);
ALTER TABLE paie_stc ADD COLUMN IF NOT EXISTS net_bulletin_mensuel NUMERIC(20,2) NOT NULL DEFAULT 0;
ALTER TABLE paie_stc ADD COLUMN IF NOT EXISTS montant_bulletin_deja_paye NUMERIC(20,2) NOT NULL DEFAULT 0;
ALTER TABLE paie_stc ADD COLUMN IF NOT EXISTS solde_bulletin_a_regler NUMERIC(20,2) NOT NULL DEFAULT 0;
ALTER TABLE paie_stc ADD COLUMN IF NOT EXISTS date_paiement_bulletin DATE;
ALTER TABLE paie_stc ADD COLUMN IF NOT EXISTS reference_paiement_bulletin VARCHAR(255);
