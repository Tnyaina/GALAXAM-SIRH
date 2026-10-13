-- =====================================================================
-- Migration 26-10-13-sp68-recapitulatif-social-organismes-dynamiques.sql
-- Référentiel des organismes déclaratifs sociaux & conservation sur lignes de paie
-- =====================================================================

-- 1. Référentiel des Organismes Sociaux
CREATE TABLE IF NOT EXISTS ref_organisme_social (
    id_organisme SERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    libelle VARCHAR(150) NOT NULL,
    categorie VARCHAR(30) NOT NULL DEFAULT 'SANTE' CHECK (categorie IN ('PREVOYANCE', 'SANTE', 'FORMATION', 'AUTRE')),
    est_actif BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_organisme_social_code ON ref_organisme_social(code);
CREATE INDEX IF NOT EXISTS idx_organisme_social_categorie ON ref_organisme_social(categorie);

-- 2. Insertion des organismes sociaux initiaux
INSERT INTO ref_organisme_social (id_organisme, code, libelle, categorie, est_actif) VALUES
(1, 'CNAPS', 'Caisse Nationale de Prévoyance Sociale', 'PREVOYANCE', TRUE),
(2, 'OSTIE', 'Organisation Sanitaire Tananarivienne', 'SANTE', TRUE),
(3, 'OMSI',  'Organisation Médicale Inter-entreprises', 'SANTE', TRUE),
(4, 'FMFP',  'Fonds Malgache de Formation Professionnelle', 'FORMATION', TRUE),
(5, 'AMIT',  'Association Médicale Inter-entreprises de Tuléar', 'SANTE', TRUE)
ON CONFLICT (code) DO UPDATE SET libelle = EXCLUDED.libelle, categorie = EXCLUDED.categorie;

SELECT setval('ref_organisme_social_id_organisme_seq', (SELECT GREATEST(MAX(id_organisme), 5) FROM ref_organisme_social));

-- 3. Rattachement organisme sur ref_type_cotisation
ALTER TABLE ref_type_cotisation
    ADD COLUMN IF NOT EXISTS id_organisme INT REFERENCES ref_organisme_social(id_organisme);

CREATE INDEX IF NOT EXISTS idx_type_cotisation_organisme ON ref_type_cotisation(id_organisme);

-- Rattachement organisme optionnel sur site_cotisation (pour surcharge par site)
ALTER TABLE site_cotisation
    ADD COLUMN IF NOT EXISTS id_organisme INT REFERENCES ref_organisme_social(id_organisme);

CREATE INDEX IF NOT EXISTS idx_site_cotisation_organisme ON site_cotisation(id_organisme);

-- 4. Initialisation des liens existants
UPDATE ref_type_cotisation SET id_organisme = 1 WHERE code = 'CNAPS' AND id_organisme IS NULL;
UPDATE ref_type_cotisation SET id_organisme = 2 WHERE code = 'OSTIE' AND id_organisme IS NULL;
UPDATE ref_type_cotisation SET id_organisme = 3 WHERE code = 'OMSI' AND id_organisme IS NULL;
UPDATE ref_type_cotisation SET id_organisme = 4 WHERE code = 'FMFP' AND id_organisme IS NULL;

UPDATE site_cotisation sc
SET id_organisme = tc.id_organisme
FROM ref_type_cotisation tc
WHERE sc.id_type_cotisation = tc.id_type_cotisation
  AND sc.id_organisme IS NULL;

-- 5. Conservation de l'organisme sur les lignes de bulletin
ALTER TABLE bulletin_paie_ligne
    ADD COLUMN IF NOT EXISTS id_organisme INT REFERENCES ref_organisme_social(id_organisme),
    ADD COLUMN IF NOT EXISTS code_organisme VARCHAR(30),
    ADD COLUMN IF NOT EXISTS id_type_cotisation INT REFERENCES ref_type_cotisation(id_type_cotisation);

CREATE INDEX IF NOT EXISTS idx_bulletin_ligne_organisme ON bulletin_paie_ligne(id_organisme);
CREATE INDEX IF NOT EXISTS idx_bulletin_ligne_code_organisme ON bulletin_paie_ligne(code_organisme);
CREATE INDEX IF NOT EXISTS idx_bulletin_ligne_type_cotisation ON bulletin_paie_ligne(id_type_cotisation);

-- 6. Backfill historique des lignes de bulletins existantes
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_trigger WHERE tgname = 'trg_bulletin_paie_ligne_immutabilite'
    ) THEN
        ALTER TABLE bulletin_paie_ligne DISABLE TRIGGER trg_bulletin_paie_ligne_immutabilite;
    END IF;
END $$;

UPDATE bulletin_paie_ligne
SET id_organisme = 1, code_organisme = 'CNAPS'
WHERE (code_rubrique ILIKE '%CNAPS%' OR libelle_rubrique ILIKE '%CNAPS%')
  AND id_organisme IS NULL;

UPDATE bulletin_paie_ligne
SET id_organisme = 2, code_organisme = 'OSTIE'
WHERE (code_rubrique ILIKE '%OSTIE%' OR libelle_rubrique ILIKE '%OSTIE%')
  AND id_organisme IS NULL;

UPDATE bulletin_paie_ligne
SET id_organisme = 3, code_organisme = 'OMSI'
WHERE (code_rubrique ILIKE '%OMSI%' OR libelle_rubrique ILIKE '%OMSI%')
  AND id_organisme IS NULL;

UPDATE bulletin_paie_ligne
SET id_organisme = 4, code_organisme = 'FMFP'
WHERE (code_rubrique ILIKE '%FMFP%' OR libelle_rubrique ILIKE '%FMFP%')
  AND id_organisme IS NULL;

UPDATE bulletin_paie_ligne
SET id_organisme = 5, code_organisme = 'AMIT'
WHERE (code_rubrique ILIKE '%AMIT%' OR libelle_rubrique ILIKE '%AMIT%')
  AND id_organisme IS NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_trigger WHERE tgname = 'trg_bulletin_paie_ligne_immutabilite'
    ) THEN
        ALTER TABLE bulletin_paie_ligne ENABLE TRIGGER trg_bulletin_paie_ligne_immutabilite;
    END IF;
END $$;
