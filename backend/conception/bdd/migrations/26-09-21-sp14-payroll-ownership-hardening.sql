BEGIN;

ALTER TABLE ref_type_mouvement_paie
    ADD COLUMN IF NOT EXISTS nature VARCHAR(30) NOT NULL DEFAULT 'VARIABLE_DAF',
    ADD COLUMN IF NOT EXISTS owner_code VARCHAR(30) NOT NULL DEFAULT 'DAF_PAIE',
    ADD COLUMN IF NOT EXISTS saisie_manuelle_autorisee BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS periode_obligatoire BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS motif_obligatoire BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS provenance_defaut VARCHAR(20) NOT NULL DEFAULT 'VARIABLE_DAF';

UPDATE ref_type_mouvement_paie SET
    nature = CASE code WHEN 'ACOMPTE_15' THEN 'ACOMPTE' WHEN 'AVANCE_SPECIALE' THEN 'AVANCE' WHEN 'CALL_PERSO' THEN 'RETENUE_NETTE' WHEN 'PRIME_ASSIDUITE' THEN 'AUTOMATIQUE' WHEN 'NOTE_DE_FRAIS' THEN 'DOMAINE_FUTUR' WHEN 'REGULARISATION_CONGE' THEN 'REGULARISATION' ELSE 'VARIABLE_DAF' END,
    owner_code = CASE code WHEN 'ACOMPTE_15' THEN 'TRESORERIE' WHEN 'AVANCE_SPECIALE' THEN 'TRESORERIE' WHEN 'PRIME_ASSIDUITE' THEN 'SYSTEME' WHEN 'NOTE_DE_FRAIS' THEN 'FRAIS' WHEN 'REGULARISATION_CONGE' THEN 'CONGE_PAIE' ELSE 'DAF_PAIE' END,
    saisie_manuelle_autorisee = code NOT IN ('PRIME_ASSIDUITE', 'NOTE_DE_FRAIS'),
    periode_obligatoire = TRUE,
    motif_obligatoire = code <> 'PRIME_ASSIDUITE',
    provenance_defaut = CASE code WHEN 'ACOMPTE_15' THEN 'ACOMPTE' WHEN 'AVANCE_SPECIALE' THEN 'AVANCE' WHEN 'PRIME_ASSIDUITE' THEN 'SYSTEME' WHEN 'NOTE_DE_FRAIS' THEN 'IMPORT' WHEN 'REGULARISATION_CONGE' THEN 'CONGE' ELSE 'VARIABLE_DAF' END;

ALTER TABLE paie_avance_acompte
    ADD COLUMN IF NOT EXISTS montant_initial NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS montant_impute NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS solde_restant NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS provenance VARCHAR(20) NOT NULL DEFAULT 'VARIABLE_DAF';

UPDATE paie_avance_acompte SET
    montant_initial = COALESCE(montant_initial, montant),
    montant_impute = CASE WHEN statut = 'IMPUTE_PAIE' THEN COALESCE(montant_initial, montant) ELSE montant_impute END,
    solde_restant = CASE WHEN statut = 'IMPUTE_PAIE' THEN 0 ELSE COALESCE(solde_restant, montant) END
WHERE montant_initial IS NULL OR solde_restant IS NULL;

ALTER TABLE paie_avance_acompte ALTER COLUMN montant_initial SET NOT NULL;
ALTER TABLE paie_avance_acompte ALTER COLUMN solde_restant SET NOT NULL;

CREATE OR REPLACE FUNCTION initialize_paie_avance_imputation()
RETURNS TRIGGER AS $$
BEGIN
    NEW.montant_initial := COALESCE(NEW.montant_initial, NEW.montant);
    NEW.montant_impute := COALESCE(NEW.montant_impute, 0);
    NEW.solde_restant := COALESCE(NEW.solde_restant, NEW.montant_initial - NEW.montant_impute);
    IF NEW.provenance IS NULL THEN
        SELECT provenance_defaut INTO NEW.provenance
        FROM ref_type_mouvement_paie
        WHERE id_type_mouvement = NEW.id_type_mouvement OR code = NEW.type_mouvement
        ORDER BY CASE WHEN id_type_mouvement = NEW.id_type_mouvement THEN 0 ELSE 1 END
        LIMIT 1;
        NEW.provenance := COALESCE(NEW.provenance, 'VARIABLE_DAF');
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_initialize_paie_avance_imputation ON paie_avance_acompte;
CREATE TRIGGER trg_initialize_paie_avance_imputation
BEFORE INSERT ON paie_avance_acompte
FOR EACH ROW EXECUTE FUNCTION initialize_paie_avance_imputation();

COMMIT;
