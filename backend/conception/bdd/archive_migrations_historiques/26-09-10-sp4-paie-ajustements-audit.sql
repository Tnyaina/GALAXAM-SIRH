-- ==============================================================================
-- SP4 — PAIE : Ajustements & Résolutions du Contre-Audit
-- ==============================================================================
-- 1. Procédure de Réouverture Exceptionnelle Tracée post-clôture
-- 2. Prélèvement Partiel d'Échéance de Prêt & Report de Reliquat
-- 3. Rubrique Système d'Équilibrage Comptable d'Arrondi (AJUSTEMENT_ARRONDI)
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. AJOUT DES COLONNES D'AUDIT DE RÉOUVERTURE SUR bulletin_paie
-- ------------------------------------------------------------------------------
ALTER TABLE bulletin_paie 
    ADD COLUMN IF NOT EXISTS motif_reouverture TEXT,
    ADD COLUMN IF NOT EXISTS date_reouverture TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS rouvert_par INT REFERENCES utilisateur(id_utilisateur);

ALTER TABLE bulletin_paie DROP CONSTRAINT IF EXISTS chk_bulletin_statut;
ALTER TABLE bulletin_paie ADD CONSTRAINT chk_bulletin_statut CHECK (statut IN (
    'CALCULE', 'VALIDE', 'CLOTURE', 'ROUVERT', 'ANNULE'
));

-- ------------------------------------------------------------------------------
-- 2. MISE À JOUR DU TRIGGER D'IMMUTABILITÉ AVEC PORTE DE RÉOUVERTURE TRACÉE
-- ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_bulletin_paie_immutabilite()
RETURNS TRIGGER AS $$
BEGIN
    -- Suppression : strictement interdite si le bulletin était clôturé
    IF TG_OP = 'DELETE' THEN
        IF OLD.statut = 'CLOTURE' THEN
            RAISE EXCEPTION 'Action interdite : Suppression impossible d''un bulletin clôturé (%).', OLD.reference_bulletin;
        END IF;
        RETURN OLD;
    END IF;

    -- Modification :
    IF OLD.statut = 'CLOTURE' THEN
        -- Réouverture d'urgence autorisée UNIQUEMENT avec motif explicite (au moins 5 caractères)
        IF NEW.statut = 'ROUVERT' AND NEW.motif_reouverture IS NOT NULL AND LENGTH(TRIM(NEW.motif_reouverture)) >= 5 THEN
            NEW.date_reouverture := CURRENT_TIMESTAMP;
            RETURN NEW;
        ELSE
            RAISE EXCEPTION 'Action interdite : Le bulletin % est clôturé et strictement immuable. Pour le réouvrir exceptionnellement, renseignez un motif_reouverture valide.', OLD.reference_bulletin;
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ------------------------------------------------------------------------------
-- 3. AJOUT DU PRÉLÈVEMENT PARTIEL SUR paie_pret_echeance
-- ------------------------------------------------------------------------------
ALTER TABLE paie_pret_echeance
    ADD COLUMN IF NOT EXISTS montant_preleve NUMERIC(12,2) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS montant_restant_echeance NUMERIC(12,2);

ALTER TABLE paie_pret_echeance DROP CONSTRAINT IF EXISTS chk_echeance_statut;
ALTER TABLE paie_pret_echeance ADD CONSTRAINT chk_echeance_statut CHECK (statut IN (
    'A_ECHOIR', 'PRELEVEE_EN_PAIE', 'PRELEVEE_PARTIELLEMENT', 'REMBOURSEE_ANTICIPEE', 'REPORTEE', 'ANNULEE'
));

-- ------------------------------------------------------------------------------
-- 4. RUBRIQUE SYSTÈME D'ÉQUILIBRAGE DE L'ARRONDI MONÉTAIRE (AJUSTEMENT_ARRONDI)
-- ------------------------------------------------------------------------------
INSERT INTO paie_rubrique (code, libelle, categorie, type_valeur, sens, soumis_cnaps, soumis_sante, soumis_irsa, est_fixe, ordre_affichage, description)
VALUES ('AJUSTEMENT_ARRONDI', 'Ajustement Arrondi Monétaire (Net)', 'NET', 'MONTANT', 'NEUTRE', FALSE, FALSE, FALSE, TRUE, 999, 'Écart monétaire d''arrondi à la dizaine d''Ariary pour équilibre comptable parfait')
ON CONFLICT (code) DO UPDATE
SET libelle = EXCLUDED.libelle, 
    categorie = EXCLUDED.categorie, 
    sens = EXCLUDED.sens, 
    ordre_affichage = EXCLUDED.ordre_affichage,
    description = EXCLUDED.description;
