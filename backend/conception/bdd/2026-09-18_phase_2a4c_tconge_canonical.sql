-- ==============================================================================
-- PHASE 2A-4C : IMPLÉMENTATION CANONIQUE TCONGE & PÉRIODE DE RÉFÉRENCE BORNÉE KAJY
-- Date : 2026-09-18
-- Référence Métier : GALAXAM_CURRENT_BUSINESS_REFERENCE (KAJY 12 MOIS)
-- ==============================================================================

-- 1. PARAMETRE GLOBAL CANONIQUE : DUREE_REFERENCE_TCONGE_MOIS = 12
-- Note technique : date_debut (2000-01-01) est classifiée exclusivement comme TECHNICAL_VALIDITY_LOWER_BOUND.
-- Elle ne représente ni le début de la règle métier, ni le début d'une campagne TCONGE, ni une date historique GALAXAM.
INSERT INTO parametre_global (
    code_parametre, categorie, valeur, description, date_debut, est_actif, est_desactivable
) VALUES (
    'DUREE_REFERENCE_TCONGE_MOIS',
    'CONGE',
    '12',
    'TECHNICAL_VALIDITY_LOWER_BOUND: Borne technique de validité du paramètre. Ne représente ni le début de la règle métier, ni le début d une campagne TCONGE, ni une date historique GALAXAM.',
    DATE '2000-01-01',
    TRUE,
    FALSE
) ON CONFLICT (code_parametre, date_debut) DO UPDATE
SET valeur = EXCLUDED.valeur, description = EXCLUDED.description;

-- 2. TABLE DES PERIODES DE REFERENCE BORNÉES (conge_periode_reference)
CREATE TABLE IF NOT EXISTS conge_periode_reference (
    id_periode_reference SERIAL PRIMARY KEY,
    id_employe INT NOT NULL REFERENCES employe(id_employe),
    date_debut DATE NOT NULL,
    date_fin DATE NOT NULL,
    statut VARCHAR(30) NOT NULL DEFAULT 'OUVERTE', -- 'OUVERTE', 'CLOTUREE', 'ARCHIVEE'
    taux_calcule_cloture NUMERIC(12,2),
    cumul_droits_cloture NUMERIC(8,4),
    cumul_valorisation_cloture NUMERIC(12,2),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by INT REFERENCES utilisateur(id_utilisateur),
    CONSTRAINT chk_periode_dates CHECK (date_fin >= date_debut),
    CONSTRAINT chk_statut_ref CHECK (statut IN ('OUVERTE', 'CLOTUREE', 'ARCHIVEE'))
);
CREATE INDEX IF NOT EXISTS idx_conge_ref_emp_dates ON conge_periode_reference(id_employe, date_debut, date_fin);

-- 3. TABLE DES TAUX APPLICABLES (conge_taux_applicable)
-- Porte les taux en vigueur pour payer les congés pris ([date_effet, date_fin[)
CREATE TABLE IF NOT EXISTS conge_taux_applicable (
    id_taux_applicable SERIAL PRIMARY KEY,
    id_employe INT NOT NULL REFERENCES employe(id_employe),
    taux_journalier NUMERIC(12,2) NOT NULL,
    date_effet DATE NOT NULL,
    date_fin DATE, -- convention [date_effet, date_fin[
    source_provenance VARCHAR(50) NOT NULL,
    id_periode_reference INT REFERENCES conge_periode_reference(id_periode_reference),
    commentaire TEXT,
    cree_par INT REFERENCES utilisateur(id_utilisateur),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_taux_positif CHECK (taux_journalier > 0),
    CONSTRAINT chk_dates_taux CHECK (date_fin IS NULL OR date_fin > date_effet),
    CONSTRAINT chk_source_provenance CHECK (source_provenance IN ('KAJY_MIGRATION', 'CALCUL_PERIODE_REFERENCE', 'INITIALISATION_BASE_30'))
);
CREATE INDEX IF NOT EXISTS idx_taux_app_emp_dates ON conge_taux_applicable(id_employe, date_effet, date_fin);

-- 4. EVOLUTION DE CONGE_JOURNAL_DROIT : RATTACHEMENT A LA REFERENCE BORNEE
ALTER TABLE conge_journal_droit
ADD COLUMN IF NOT EXISTS id_periode_reference INT REFERENCES conge_periode_reference(id_periode_reference),
ADD COLUMN IF NOT EXISTS numero_mois_reference INT;

COMMENT ON COLUMN conge_journal_droit.id_periode_reference IS 'Période de référence bornée KAJY. NULL pour les anciens enregistrements non rattachés (LEGACY_UNSCOPED).';
