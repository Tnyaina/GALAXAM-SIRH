-- ==============================================================================
-- SP4 — PAIE : Gestion Dédiée des Prêts d'Entreprise & Échéanciers
-- ==============================================================================
-- Permet la traçabilité plurimensuelle des prêts accordés au personnel :
-- - Suivi du capital emprunté, mensualités et capital restant dû en temps réel
-- - Échéancier prévisionnel (1/N, ..., N/N) avec rattachement aux périodes de paie
-- - Prélèvement automatique lors du calcul de paie et gestion du STC (Solde Tout Compte)
-- ==============================================================================

-- 1. TABLE DES PRÊTS AU PERSONNEL (paie_pret)
CREATE TABLE IF NOT EXISTS paie_pret (
    id_pret                 SERIAL PRIMARY KEY,
    id_employe              INT NOT NULL REFERENCES employe(id_employe),
    reference_pret          VARCHAR(50) NOT NULL UNIQUE,
    montant_total           NUMERIC(12,2) NOT NULL CHECK (montant_total > 0),
    montant_mensualite      NUMERIC(12,2) NOT NULL CHECK (montant_mensualite > 0),
    nb_echeances            INT NOT NULL CHECK (nb_echeances > 0),
    date_accord             DATE NOT NULL DEFAULT CURRENT_DATE,
    date_deblocage          DATE,
    date_premiere_echeance  DATE NOT NULL,
    capital_rembourse       NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (capital_rembourse >= 0),
    capital_restant_du      NUMERIC(12,2) NOT NULL CHECK (capital_restant_du >= 0),
    statut                  VARCHAR(20) NOT NULL DEFAULT 'EN_COURS',
    motif                   VARCHAR(255) NOT NULL,
    accorde_par             INT REFERENCES utilisateur(id_utilisateur),
    created_at              TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_pret_statut CHECK (statut IN (
        'DEMANDE', 'APPROUVE', 'EN_COURS', 'SOLDE', 'SUSPENDU', 'ANNULE'
    )),
    CONSTRAINT chk_pret_coherence_capital CHECK (capital_rembourse + capital_restant_du <= montant_total + 1.00)
);

CREATE INDEX IF NOT EXISTS idx_paie_pret_employe ON paie_pret(id_employe);
CREATE INDEX IF NOT EXISTS idx_paie_pret_statut ON paie_pret(statut);

-- 2. TABLE DES ÉCHÉANCES DE PRÊT (paie_pret_echeance)
CREATE TABLE IF NOT EXISTS paie_pret_echeance (
    id_echeance             SERIAL PRIMARY KEY,
    id_pret                 INT NOT NULL REFERENCES paie_pret(id_pret) ON DELETE CASCADE,
    numero_echeance         INT NOT NULL CHECK (numero_echeance > 0),
    id_periode_paie         INT REFERENCES periode_paie(id_periode_paie),
    date_echeance           DATE NOT NULL,
    montant_echeance        NUMERIC(12,2) NOT NULL CHECK (montant_echeance > 0),
    capital_restant_apres   NUMERIC(12,2) NOT NULL CHECK (capital_restant_apres >= 0),
    statut                  VARCHAR(25) NOT NULL DEFAULT 'A_ECHOIR',
    id_bulletin             INT REFERENCES bulletin_paie(id_bulletin) ON DELETE SET NULL,
    date_prelevement        TIMESTAMP WITH TIME ZONE,
    commentaire             VARCHAR(255),
    created_at              TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_echeance_statut CHECK (statut IN (
        'A_ECHOIR', 'PRELEVEE_EN_PAIE', 'REMBOURSEE_ANTICIPEE', 'REPORTEE', 'ANNULEE'
    )),
    CONSTRAINT uk_pret_echeance UNIQUE (id_pret, numero_echeance)
);

CREATE INDEX IF NOT EXISTS idx_pret_echeance_pret ON paie_pret_echeance(id_pret);
CREATE INDEX IF NOT EXISTS idx_pret_echeance_periode ON paie_pret_echeance(id_periode_paie);
CREATE INDEX IF NOT EXISTS idx_pret_echeance_statut ON paie_pret_echeance(statut);

-- 3. RECENTRAGE DE paie_avance_acompte (VARIABLES MENSUELLES UNIQUEMENT)
ALTER TABLE paie_avance_acompte 
    DROP COLUMN IF EXISTS reference_pret,
    DROP COLUMN IF EXISTS numero_echeance,
    DROP COLUMN IF EXISTS total_echeances;

ALTER TABLE paie_avance_acompte DROP CONSTRAINT IF EXISTS chk_avance_type;
ALTER TABLE paie_avance_acompte ADD CONSTRAINT chk_avance_type CHECK (type_mouvement IN (
    'ACOMPTE_15', 'AVANCE_SPECIALE', 'CALL_PERSO', 'NOTE_DE_FRAIS', 'PRIME_EXCEPTIONNELLE', 'RETENUE_DIVERSE'
));
