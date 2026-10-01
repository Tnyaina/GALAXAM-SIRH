-- Étape du calcul indépendante des assiettes, dans le catalogue existant.
BEGIN;
ALTER TABLE paie_rubrique ADD COLUMN IF NOT EXISTS etape_calcul VARCHAR(20);
ALTER TABLE bulletin_paie_ligne ADD COLUMN IF NOT EXISTS etape_calcul VARCHAR(20);
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'paie_rubrique'::regclass AND conname = 'chk_rubrique_etape_calcul') THEN
        ALTER TABLE paie_rubrique ADD CONSTRAINT chk_rubrique_etape_calcul
            CHECK (etape_calcul IN ('BRUT', 'GAIN_NET', 'RETENUE_NET'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'bulletin_paie_ligne'::regclass AND conname = 'chk_ligne_etape_calcul') THEN
        ALTER TABLE bulletin_paie_ligne ADD CONSTRAINT chk_ligne_etape_calcul
            CHECK (etape_calcul IN ('BRUT', 'GAIN_NET', 'RETENUE_NET'));
    END IF;
END $$;
-- Reprise strictement identique au routage historique : aucune réinterprétation.
UPDATE paie_rubrique SET etape_calcul = CASE
    WHEN sens = 'RETENUE' THEN 'RETENUE_NET'
    WHEN soumis_irsa THEN 'BRUT' ELSE 'GAIN_NET' END
WHERE etape_calcul IS NULL AND NOT est_fixe AND sens IN ('GAIN', 'RETENUE');
COMMIT;
