-- =========================================================================
-- GALAXAM SIRH — Migration Sprint 75 : Sécurisation Journal STC Règlement (Phase 3.1)
-- =========================================================================
-- 1. Autorisation des montants >= 0 pour gestion explicite des STC à solde nul
-- 2. Colonnes de traçabilité pour annulation/correction de paiement
-- 3. Index d'unicité partiel anti-double soumission sur référence active
-- =========================================================================

-- 1. Autoriser montant_regle >= 0 (nécessaire pour STC net à payer nul)
ALTER TABLE paie_stc_reglement 
DROP CONSTRAINT IF EXISTS paie_stc_reglement_montant_regle_check;

ALTER TABLE paie_stc_reglement 
ADD CONSTRAINT paie_stc_reglement_montant_regle_check CHECK (montant_regle >= 0);

-- 2. Colonnes de traçabilité des corrections et annulations
ALTER TABLE paie_stc_reglement 
ADD COLUMN IF NOT EXISTS commentaire TEXT;

ALTER TABLE paie_stc_reglement 
ADD COLUMN IF NOT EXISTS est_annule BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE paie_stc_reglement 
ADD COLUMN IF NOT EXISTS annule_par INT REFERENCES utilisateur(id_utilisateur);

ALTER TABLE paie_stc_reglement 
ADD COLUMN IF NOT EXISTS date_annulation TIMESTAMP WITH TIME ZONE;

ALTER TABLE paie_stc_reglement 
ADD COLUMN IF NOT EXISTS motif_annulation TEXT;

-- 3. Protection stricte contre les doubles règlements / double clics sur une même référence active
CREATE UNIQUE INDEX IF NOT EXISTS uk_stc_reglement_stc_ref_active 
ON paie_stc_reglement(id_stc, reference_reglement) 
WHERE est_annule = FALSE;
