-- Réservé à sirh_galaxam_test dans ce chantier. Vérification avant toute modification.
DO $$ BEGIN
  IF current_database() <> 'sirh_galaxam_test' THEN
    RAISE EXCEPTION 'Migration congés interdite hors sirh_galaxam_test';
  END IF;
END $$;

ALTER TABLE parametre_global DROP CONSTRAINT IF EXISTS ck_conge_decompte_feries_valeur;
UPDATE parametre_global
SET est_desactivable=TRUE
WHERE code_parametre='CONGE_DECOMPTE_FERIES';

ALTER TABLE parametre_global ADD CONSTRAINT ck_conge_decompte_feries_valeur CHECK (
  code_parametre <> 'CONGE_DECOMPTE_FERIES'
  OR (valeur IN ('INCLUS', 'EXCLUS') AND est_desactivable = TRUE)
);
