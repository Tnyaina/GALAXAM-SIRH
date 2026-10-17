-- =====================================================================
-- GALAXAM SIRH — MIGRATION SP72 : DOSSIER DE SORTIE & JOURNAL REGLEMENTS STC
-- Sépare l'orchestration du départ (paie_dossier_sortie) du décompte
-- financier scellé (paie_stc), et ajoute le journal minimal des règlements.
-- Date : 2026-10-16
-- =====================================================================

-- 1. Table d'orchestration du dossier de sortie préparatoire et administratif
ALTER TABLE paie_dossier_sortie DROP COLUMN IF EXISTS checklist_offboarding;

CREATE TABLE IF NOT EXISTS paie_dossier_sortie (
    id_dossier_sortie SERIAL PRIMARY KEY,
    id_employe INT NOT NULL REFERENCES employe(id_employe),
    id_contrat INT REFERENCES contrat_travail(id_contrat),
    date_depart DATE NOT NULL,
    motif_rupture VARCHAR(50) NOT NULL,
    statut_dossier VARCHAR(30) NOT NULL DEFAULT 'EN_PREPARATION',
    donnees_preparation JSONB,
    decision_pret VARCHAR(40),
    justification_decision_pret TEXT,
    montant_pret_retenu NUMERIC(20,2) DEFAULT 0,
    certificat_delivre BOOLEAN DEFAULT FALSE,
    date_delivrance_certificat DATE,
    mode_remise_certificat VARCHAR(30),
    recu_stc_remis BOOLEAN DEFAULT FALSE,
    date_remise_recu DATE,
    id_stc INT UNIQUE REFERENCES paie_stc(id_stc) ON DELETE SET NULL,
    cree_par INT REFERENCES utilisateur(id_utilisateur),
    cloture_par INT REFERENCES utilisateur(id_utilisateur),
    date_cloture TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_dossier_sortie_statut CHECK (statut_dossier IN ('EN_PREPARATION', 'PRET_A_VALIDER', 'VALIDE', 'REGLE', 'CLOTURE', 'ANNULE')),
    CONSTRAINT uk_dossier_sortie_employe_depart UNIQUE (id_employe, date_depart)
);

CREATE INDEX IF NOT EXISTS idx_dossier_sortie_employe ON paie_dossier_sortie(id_employe);
CREATE INDEX IF NOT EXISTS idx_dossier_sortie_statut ON paie_dossier_sortie(statut_dossier);
CREATE INDEX IF NOT EXISTS idx_dossier_sortie_contrat ON paie_dossier_sortie(id_contrat);
CREATE INDEX IF NOT EXISTS idx_dossier_sortie_stc ON paie_dossier_sortie(id_stc);

-- 2. Journal minimal des règlements réels du STC (paiements échelonnés/partiels traçables)
CREATE TABLE IF NOT EXISTS paie_stc_reglement (
    id_reglement SERIAL PRIMARY KEY,
    id_stc INT NOT NULL REFERENCES paie_stc(id_stc) ON DELETE CASCADE,
    date_reglement DATE NOT NULL,
    montant_regle NUMERIC(20,2) NOT NULL CHECK (montant_regle > 0),
    mode_reglement VARCHAR(30) NOT NULL DEFAULT 'VIREMENT',
    reference_reglement VARCHAR(255) NOT NULL,
    enregistre_par INT REFERENCES utilisateur(id_utilisateur),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_stc_reglement_stc ON paie_stc_reglement(id_stc);
CREATE INDEX IF NOT EXISTS idx_stc_reglement_date ON paie_stc_reglement(date_reglement);

-- 3. Reprise historique vérifiable et idempotente pour les STC déjà validés/réglés
INSERT INTO paie_dossier_sortie (id_employe, id_contrat, date_depart, motif_rupture, statut_dossier, id_stc, cree_par, created_at, updated_at)
SELECT s.id_employe, s.id_contrat, s.date_depart, s.motif_rupture,
       CASE 
           WHEN s.statut = 'REGLE' THEN 'REGLE'
           WHEN s.statut = 'VALIDE' THEN 'VALIDE'
           WHEN s.statut = 'ANNULE' THEN 'ANNULE'
           ELSE 'EN_PREPARATION'
       END,
       s.id_stc, s.cree_par, s.created_at, s.updated_at
FROM paie_stc s
WHERE NOT EXISTS (
    SELECT 1 FROM paie_dossier_sortie d WHERE d.id_stc = s.id_stc
)
ON CONFLICT (id_employe, date_depart) DO UPDATE 
SET id_stc = EXCLUDED.id_stc, 
    statut_dossier = EXCLUDED.statut_dossier,
    updated_at = CURRENT_TIMESTAMP
WHERE paie_dossier_sortie.id_stc IS NULL;

-- 4. Reprise historique des règlements déjà constatés dans paie_stc
INSERT INTO paie_stc_reglement (id_stc, date_reglement, montant_regle, mode_reglement, reference_reglement, enregistre_par, created_at)
SELECT s.id_stc, s.date_reglement, s.net_a_payer_stc, COALESCE(s.mode_reglement, 'VIREMENT'), s.reference_reglement, s.regle_par, CURRENT_TIMESTAMP
FROM paie_stc s
WHERE s.statut = 'REGLE' 
  AND s.date_reglement IS NOT NULL 
  AND s.reference_reglement IS NOT NULL
  AND s.net_a_payer_stc > 0
  AND NOT EXISTS (
      SELECT 1 FROM paie_stc_reglement r WHERE r.id_stc = s.id_stc
  );
