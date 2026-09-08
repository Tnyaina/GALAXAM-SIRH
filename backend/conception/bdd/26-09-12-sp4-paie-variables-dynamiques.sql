-- ==============================================================================
-- SP4 — PAIE : Table Référentielle des Types de Mouvements & Primes Dynamiques
-- Date : 2026-09-12
-- Description :
--   1. Création de la table référentielle ref_type_mouvement_paie avec liaison paie_rubrique.
--   2. Insertion des types de mouvements pour tous les cas modèles (Acomptes, Avances,
--      Performance, Assiduité, AUTIMP, Extras, Call Perso, Notes de frais).
--   3. Évolution de paie_avance_acompte avec clé étrangère id_type_mouvement.
-- ==============================================================================

-- 1. CRÉATION DE LA TABLE RÉFÉRENTIELLE ref_type_mouvement_paie
CREATE TABLE IF NOT EXISTS ref_type_mouvement_paie (
    id_type_mouvement SERIAL PRIMARY KEY,
    code VARCHAR(40) UNIQUE NOT NULL,
    libelle VARCHAR(150) NOT NULL,
    sens VARCHAR(10) NOT NULL CHECK (sens IN ('GAIN', 'RETENUE')),
    categorie VARCHAR(40) NOT NULL, -- 'ACOMPTE', 'AVANCE', 'PRIME', 'RETENUE_DIVERSE', 'NOTE_DE_FRAIS', 'EXTRA'
    id_rubrique INT REFERENCES paie_rubrique(id_rubrique),
    est_proratise BOOLEAN DEFAULT FALSE,
    est_plafonne_dixieme BOOLEAN DEFAULT FALSE,
    est_actif BOOLEAN DEFAULT TRUE,
    ordre_affichage INT DEFAULT 10,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ref_type_mvt_code ON ref_type_mouvement_paie(code);
CREATE INDEX IF NOT EXISTS idx_ref_type_mvt_sens ON ref_type_mouvement_paie(sens);

-- 2. SEEDING DE ref_type_mouvement_paie ASSOCIÉ AU PLAN DE PAIE (paie_rubrique)
INSERT INTO ref_type_mouvement_paie (code, libelle, sens, categorie, id_rubrique, est_proratise, est_plafonne_dixieme, ordre_affichage, description)
VALUES
    -- Retenues de trésorerie & Déductions
    ('ACOMPTE_15', 'Acompte de Quinzaine (du 15)', 'RETENUE', 'ACOMPTE', 
        (SELECT id_rubrique FROM paie_rubrique WHERE code = 'RETENUE_ACOMPTE_15'), 
        FALSE, FALSE, 10, 'Acompte sur salaire échu versé en cours de mois'),
    
    ('AVANCE_SPECIALE', 'Avance Exceptionnelle sur Salaire', 'RETENUE', 'AVANCE', 
        (SELECT id_rubrique FROM paie_rubrique WHERE code = 'RETENUE_AVANCE_SPECIALE'), 
        FALSE, TRUE, 20, 'Avance en espèces consentie par employeur (plafonnée au 1/10ème légal Art. 68)'),
    
    ('CALL_PERSO', 'Retenue Télécommunications Privées (Call Perso)', 'RETENUE', 'RETENUE_DIVERSE', 
        (SELECT id_rubrique FROM paie_rubrique WHERE code = 'RETENUE_CALL_PERSO'), 
        FALSE, FALSE, 30, 'Prélèvement sur le net pour dépassement forfait téléphonique privé'),
    
    ('RETENUE_DIVERSE', 'Retenue Diverse sur Salaire', 'RETENUE', 'RETENUE_DIVERSE', 
        (SELECT id_rubrique FROM paie_rubrique WHERE code = 'RETENUE_DIVERSE'), 
        FALSE, FALSE, 40, 'Pénalité ou retenue ponctuelle diverse sur le net à payer'),

    -- Gains & Primes d'établissement
    ('PRIME_PERFORMANCE', 'Prime de Performance (Proratisée)', 'GAIN', 'PRIME', 
        (SELECT id_rubrique FROM paie_rubrique WHERE code = 'PRIME_PERFORMANCE'), 
        TRUE, FALSE, 50, 'Prime soumise au prorata de présence avec malus -25% si retard > 70mn et congés > 1j'),
    
    ('PRIME_ASSIDUITE', 'Prime d''Assiduité', 'GAIN', 'PRIME', 
        (SELECT id_rubrique FROM paie_rubrique WHERE code = 'PRIME_ASSIDUITE'), 
        FALSE, FALSE, 60, 'Prime accordée si le total des retards du mois est strictement inférieur à 45 minutes'),
    
    ('PRIME_EXCEPTIONNELLE', 'Prime Exceptionnelle', 'GAIN', 'PRIME', 
        (SELECT id_rubrique FROM paie_rubrique WHERE code = 'PRIME_EXCEPTIONNELLE'), 
        FALSE, FALSE, 70, 'Gratification ou prime exceptionnelle brute imposable'),
    
    ('AUTIMP', 'Autres Gains Imposables (AUTIMP)', 'GAIN', 'PRIME', 
        (SELECT id_rubrique FROM paie_rubrique WHERE code = 'AUTIMP'), 
        FALSE, FALSE, 80, 'Autres primes et indemnités brutes imposables selon modèle paie'),

    -- Indemnités & Frais non imposables
    ('EXTRA_DEPLACEMENT', 'Indemnité de Déplacement Extra (>19h30 / Mission)', 'GAIN', 'EXTRA', 
        (SELECT id_rubrique FROM paie_rubrique WHERE code = 'EXTRA_DEPLACEMENT'), 
        FALSE, FALSE, 90, 'Extra déplacement ou mission exceptionnelle non imposable'),
    
    ('NOTE_DE_FRAIS', 'Remboursement Notes de Frais', 'GAIN', 'NOTE_DE_FRAIS', 
        (SELECT id_rubrique FROM paie_rubrique WHERE code = 'REMBOURSEMENT_NOTE_FRAIS'), 
        FALSE, FALSE, 100, 'Remboursement de frais professionnels engagés par le collaborateur')

ON CONFLICT (code) DO UPDATE
SET libelle = EXCLUDED.libelle,
    sens = EXCLUDED.sens,
    categorie = EXCLUDED.categorie,
    id_rubrique = EXCLUDED.id_rubrique,
    est_proratise = EXCLUDED.est_proratise,
    est_plafonne_dixieme = EXCLUDED.est_plafonne_dixieme,
    ordre_affichage = EXCLUDED.ordre_affichage,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

-- 3. ÉVOLUTION DE paie_avance_acompte POUR RÉFÉRENCER ref_type_mouvement_paie
ALTER TABLE paie_avance_acompte 
    ADD COLUMN IF NOT EXISTS id_type_mouvement INT REFERENCES ref_type_mouvement_paie(id_type_mouvement);

-- Supprimer l'ancienne contrainte CHECK rigide pour la remplacer par la clé étrangère dynamique
ALTER TABLE paie_avance_acompte DROP CONSTRAINT IF EXISTS chk_avance_type;

-- Synchroniser les éventuelles lignes existantes
UPDATE paie_avance_acompte paa
SET id_type_mouvement = rtmp.id_type_mouvement
FROM ref_type_mouvement_paie rtmp
WHERE paa.type_mouvement = rtmp.code AND paa.id_type_mouvement IS NULL;

CREATE INDEX IF NOT EXISTS idx_paie_avance_type_mvt ON paie_avance_acompte(id_type_mouvement);
