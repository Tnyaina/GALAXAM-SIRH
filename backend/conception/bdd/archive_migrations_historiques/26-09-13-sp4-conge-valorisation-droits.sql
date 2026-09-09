-- ==============================================================================
-- SP4 — PAIE & CONGES : Valorisation Dynamique des Droits à Congés (Art. 89)
-- Date : 2026-09-13
-- Description :
--   1. Création de la rubrique RETENUE_CONGE pour déduction transparente de base
--      évitant tout double paiement des congés payés.
--   2. Création de la table conge_journal_droit pour le suivi mensuel des droits
--      acquis et de la valorisation dynamique (OHATRA KAJY DROITS CONGES).
-- ==============================================================================

-- 1. AJOUT DE LA RUBRIQUE RETENUE_CONGE
INSERT INTO paie_rubrique (code, libelle, categorie, type_valeur, sens, soumis_cnaps, soumis_sante, soumis_irsa, est_fixe, ordre_affichage, description)
VALUES (
    'RETENUE_CONGE',
    'Retenue sur Salaire pour Congés Pris (1/30ème)',
    'SALAIRE_BASE',
    'JOURS',
    'RETENUE',
    TRUE,
    TRUE,
    TRUE,
    TRUE,
    25,
    'Déduction de base contractuelle 1/30ème pour jours de congés indemnisés par INDEMNITE_CONGE'
)
ON CONFLICT (code) DO UPDATE
SET libelle = EXCLUDED.libelle,
    sens = EXCLUDED.sens,
    categorie = EXCLUDED.categorie,
    soumis_cnaps = EXCLUDED.soumis_cnaps,
    soumis_sante = EXCLUDED.soumis_sante,
    soumis_irsa = EXCLUDED.soumis_irsa;

-- 2. TABLE DU GRAND-LIVRE DE VALORISATION DES CONGES (conge_journal_droit)
CREATE TABLE IF NOT EXISTS conge_journal_droit (
    id_journal SERIAL PRIMARY KEY,
    id_employe INT NOT NULL REFERENCES employe(id_employe),
    id_periode_paie INT REFERENCES periode_paie(id_periode_paie),
    annee INT NOT NULL,
    mois INT NOT NULL,
    salaire_base NUMERIC(12,2) NOT NULL,
    jours_travailles_formule NUMERIC(5,2) NOT NULL DEFAULT 30.0,
    jours_droit_acquis NUMERIC(6,4) NOT NULL DEFAULT 2.5000,
    base_valorisation NUMERIC(12,2) NOT NULL, -- NET_BASE + HS
    valorisation_mensuelle NUMERIC(12,2) NOT NULL, -- base_valorisation * jours_droit_acquis / 30
    cumul_jours_droit NUMERIC(8,4) NOT NULL,
    cumul_valorisation NUMERIC(12,2) NOT NULL,
    taux_journalier_conge NUMERIC(12,2) NOT NULL, -- cumul_valorisation / cumul_jours_droit
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_conge_journal_emp_annee_mois UNIQUE (id_employe, annee, mois)
);

CREATE INDEX IF NOT EXISTS idx_conge_journal_emp ON conge_journal_droit(id_employe);
CREATE INDEX IF NOT EXISTS idx_conge_journal_periode ON conge_journal_droit(id_periode_paie);
