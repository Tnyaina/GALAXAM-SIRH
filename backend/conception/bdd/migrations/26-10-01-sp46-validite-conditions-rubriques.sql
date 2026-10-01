BEGIN;
ALTER TABLE paie_rubrique ADD COLUMN IF NOT EXISTS id_rubrique_origine INT REFERENCES paie_rubrique(id_rubrique);
ALTER TABLE paie_rubrique ADD COLUMN IF NOT EXISTS date_fin_effet DATE;
ALTER TABLE paie_rubrique ADD COLUMN IF NOT EXISTS condition_application VARCHAR(30) NOT NULL DEFAULT 'TOUS';
DO $$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='chk_rubrique_validite') THEN
 ALTER TABLE paie_rubrique ADD CONSTRAINT chk_rubrique_validite CHECK (date_fin_effet IS NULL OR date_debut_effet IS NULL OR date_fin_effet >= date_debut_effet);
 END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='chk_rubrique_condition') THEN
 ALTER TABLE paie_rubrique ADD CONSTRAINT chk_rubrique_condition CHECK (condition_application IN ('TOUS','ASSUJETTI_IRSA','ASSUJETTI_CNAPS','ASSUJETTI_SANTE'));
 END IF;
END $$;
CREATE INDEX IF NOT EXISTS idx_rubrique_versions ON paie_rubrique ((COALESCE(id_rubrique_origine,id_rubrique)),date_debut_effet);

-- Sérialise les changements d’une même famille et interdit les versions ambiguës.
CREATE OR REPLACE FUNCTION verifier_versions_rubrique() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE famille INT;
BEGIN
 famille := COALESCE(NEW.id_rubrique_origine, NEW.id_rubrique);
 PERFORM pg_advisory_xact_lock(46001, famille);
 IF NEW.id_rubrique_origine IS NOT NULL AND (NEW.id_rubrique_origine=NEW.id_rubrique OR
    NOT EXISTS (SELECT 1 FROM paie_rubrique WHERE id_rubrique=NEW.id_rubrique_origine AND id_rubrique_origine IS NULL)) THEN
    RAISE EXCEPTION 'La version doit référencer la rubrique d’origine';
 END IF;
 IF EXISTS (SELECT 1 FROM paie_rubrique r WHERE COALESCE(r.id_rubrique_origine,r.id_rubrique)=famille
   AND r.id_rubrique<>NEW.id_rubrique AND daterange(r.date_debut_effet,r.date_fin_effet,'[]')
       && daterange(NEW.date_debut_effet,NEW.date_fin_effet,'[]')) THEN
   RAISE EXCEPTION 'Chevauchement des versions de rubrique';
 END IF;
 RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS trg_versions_rubrique ON paie_rubrique;
CREATE TRIGGER trg_versions_rubrique BEFORE INSERT OR UPDATE OF date_debut_effet,date_fin_effet,id_rubrique_origine
ON paie_rubrique FOR EACH ROW EXECUTE FUNCTION verifier_versions_rubrique();

COMMIT;
