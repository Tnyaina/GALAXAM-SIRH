-- Réservé à sirh_galaxam_test dans ce chantier. Vérification explicite avant exécution.
DO $$ BEGIN
  IF current_database() <> 'sirh_galaxam_test' THEN
    RAISE EXCEPTION 'Migration congés interdite hors sirh_galaxam_test';
  END IF;
END $$;

DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='ck_conge_decompte_feries_valeur') THEN
    ALTER TABLE parametre_global
      ADD CONSTRAINT ck_conge_decompte_feries_valeur CHECK (
        code_parametre <> 'CONGE_DECOMPTE_FERIES'
        OR (valeur IN ('INCLUS', 'EXCLUS') AND est_desactivable = TRUE)
      );
  END IF;
END $$;

INSERT INTO parametre_global (code_parametre, categorie, valeur, description, date_debut, est_actif, est_desactivable)
SELECT 'CONGE_DECOMPTE_FERIES', 'CONGE', 'INCLUS',
       'Décompte des jours fériés dans les congés débitant le solde acquis : INCLUS ou EXCLUS. Version datée obligatoire.',
       DATE '2001-07-01', TRUE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM parametre_global WHERE code_parametre='CONGE_DECOMPTE_FERIES');
