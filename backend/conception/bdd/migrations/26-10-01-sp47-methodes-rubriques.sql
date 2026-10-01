BEGIN;
ALTER TABLE paie_rubrique ADD COLUMN IF NOT EXISTS methode_calcul VARCHAR(32) NOT NULL DEFAULT 'MONTANT';
ALTER TABLE paie_rubrique ADD COLUMN IF NOT EXISTS politique_arrondi VARCHAR(16) NOT NULL DEFAULT 'CENTIME';
ALTER TABLE paie_rubrique ADD COLUMN IF NOT EXISTS soumis_fmfp BOOLEAN;
ALTER TABLE paie_avance_acompte ADD COLUMN IF NOT EXISTS quantite_saisie NUMERIC(20,6);
ALTER TABLE paie_avance_acompte ADD COLUMN IF NOT EXISTS taux_saisi NUMERIC(20,6);
DO $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conname='chk_rubrique_methode') THEN
 ALTER TABLE paie_rubrique ADD CONSTRAINT chk_rubrique_methode CHECK(methode_calcul IN ('MONTANT','QUANTITE_TAUX','POURCENTAGE_SALAIRE_BASE'));
 ALTER TABLE paie_rubrique ADD CONSTRAINT chk_rubrique_arrondi CHECK(politique_arrondi IN ('CENTIME','ARIARY','DIZAINE'));
 END IF;
END $$;
ALTER TABLE paie_avance_acompte ALTER COLUMN type_mouvement TYPE VARCHAR(40);
ALTER TABLE bulletin_paie_ligne ALTER COLUMN taux_salarial TYPE NUMERIC(20,6);
ALTER TABLE bulletin_paie_ligne ALTER COLUMN taux_patronal TYPE NUMERIC(20,6);
ALTER TABLE bulletin_paie_ligne ALTER COLUMN quantite TYPE NUMERIC(24,8);
COMMIT;
