-- Journal des règlements de bulletins mensuels. À appliquer avant le code SP84.
CREATE TABLE IF NOT EXISTS paie_bulletin_virement_lot (
    id_lot BIGSERIAL PRIMARY KEY,
    id_periode_paie INT NOT NULL REFERENCES periode_paie(id_periode_paie),
    statut VARCHAR(20) NOT NULL DEFAULT 'PREPARE' CHECK (statut IN ('PREPARE', 'TRANSMIS', 'ANNULE')),
    cree_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cree_par INT REFERENCES utilisateur(id_utilisateur),
    transmis_at TIMESTAMPTZ,
    transmis_par INT REFERENCES utilisateur(id_utilisateur)
);

CREATE TABLE IF NOT EXISTS paie_bulletin_virement_ligne (
    id_ligne BIGSERIAL PRIMARY KEY,
    id_lot BIGINT NOT NULL REFERENCES paie_bulletin_virement_lot(id_lot),
    id_bulletin INT NOT NULL REFERENCES bulletin_paie(id_bulletin),
    montant NUMERIC(20,2) NOT NULL CHECK (montant > 0),
    beneficiaire VARCHAR(150) NOT NULL,
    banque VARCHAR(100) NOT NULL,
    rib VARCHAR(100) NOT NULL,
    statut VARCHAR(20) NOT NULL DEFAULT 'EN_ATTENTE' CHECK (statut IN ('EN_ATTENTE', 'CONFIRME', 'REJETE', 'ANNULE')),
    reference_banque VARCHAR(255),
    date_execution DATE,
    motif_rejet TEXT,
    traite_at TIMESTAMPTZ,
    traite_par INT REFERENCES utilisateur(id_utilisateur),
    UNIQUE (id_lot, id_bulletin)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_paie_virement_bulletin_en_attente
    ON paie_bulletin_virement_ligne(id_bulletin) WHERE statut = 'EN_ATTENTE';

CREATE TABLE IF NOT EXISTS paie_bulletin_reglement (
    id_reglement BIGSERIAL PRIMARY KEY,
    id_bulletin INT NOT NULL REFERENCES bulletin_paie(id_bulletin),
    id_ligne_virement BIGINT UNIQUE REFERENCES paie_bulletin_virement_ligne(id_ligne),
    montant NUMERIC(20,2) NOT NULL CHECK (montant > 0),
    mode_reglement VARCHAR(20) NOT NULL CHECK (mode_reglement IN ('VIREMENT', 'CHEQUE', 'ESPECES', 'MOBILE_MONEY')),
    date_reglement DATE NOT NULL,
    reference_reglement VARCHAR(255) NOT NULL,
    commentaire TEXT,
    nom_justificatif VARCHAR(255) NOT NULL,
    mime_justificatif VARCHAR(30) NOT NULL CHECK (mime_justificatif IN ('application/pdf', 'image/png', 'image/jpeg')),
    contenu_justificatif BYTEA NOT NULL,
    taille_justificatif INT NOT NULL CHECK (taille_justificatif BETWEEN 1 AND 5242880),
    cree_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cree_par INT REFERENCES utilisateur(id_utilisateur),
    annule_at TIMESTAMPTZ,
    annule_par INT REFERENCES utilisateur(id_utilisateur),
    motif_annulation TEXT
);
CREATE INDEX IF NOT EXISTS idx_paie_bulletin_reglement_actif
    ON paie_bulletin_reglement(id_bulletin) WHERE annule_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_paie_bulletin_reglement_reference_active
    ON paie_bulletin_reglement(id_bulletin, mode_reglement, reference_reglement) WHERE annule_at IS NULL;

-- Compatibilité avec une première version locale de SP84 qui a pu enregistrer
-- un paiement avant que l'ajout obligatoire des justificatifs soit livré.
-- Ces lignes restent visibles comme historiques sans preuve et doivent être
-- rapprochées manuellement sans supprimer ni fabriquer de pièce.
ALTER TABLE paie_bulletin_reglement ADD COLUMN IF NOT EXISTS nom_justificatif VARCHAR(255);
ALTER TABLE paie_bulletin_reglement ADD COLUMN IF NOT EXISTS mime_justificatif VARCHAR(30);
ALTER TABLE paie_bulletin_reglement ADD COLUMN IF NOT EXISTS contenu_justificatif BYTEA;
ALTER TABLE paie_bulletin_reglement ADD COLUMN IF NOT EXISTS taille_justificatif INT;
-- Une ancienne écriture sans pièce peut encore être annulée ou corrigée ; la
-- contrainte applicative impose la pièce à toute nouvelle confirmation.
ALTER TABLE paie_bulletin_reglement DROP CONSTRAINT IF EXISTS ck_paie_bulletin_reglement_justificatif;

-- Rapprochement d'un versement STC avec sa part de bulletin mensuel.
-- Les pièces historiques restent absentes jusqu'à une saisie réelle.
ALTER TABLE paie_stc_reglement ADD COLUMN IF NOT EXISTS nom_justificatif VARCHAR(255);
ALTER TABLE paie_stc_reglement ADD COLUMN IF NOT EXISTS mime_justificatif VARCHAR(30);
ALTER TABLE paie_stc_reglement ADD COLUMN IF NOT EXISTS contenu_justificatif BYTEA;
ALTER TABLE paie_stc_reglement ADD COLUMN IF NOT EXISTS taille_justificatif INT;
ALTER TABLE paie_bulletin_reglement ADD COLUMN IF NOT EXISTS id_stc_reglement INT REFERENCES paie_stc_reglement(id_reglement);
CREATE UNIQUE INDEX IF NOT EXISTS uk_paie_bulletin_reglement_stc_source
    ON paie_bulletin_reglement(id_stc_reglement) WHERE id_stc_reglement IS NOT NULL;
