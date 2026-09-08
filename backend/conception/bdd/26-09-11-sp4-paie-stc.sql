-- ==============================================================================
-- SP4 — PAIE : Solde de Tout Compte (STC) & Quittance Libératoire (Loi 2003-044)
-- ==============================================================================

CREATE SEQUENCE IF NOT EXISTS seq_numero_recu_stc START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS paie_stc (
    id_stc                          SERIAL PRIMARY KEY,
    id_employe                      INT NOT NULL REFERENCES employe(id_employe),
    id_periode_paie                 INT NOT NULL REFERENCES periode_paie(id_periode_paie),
    id_bulletin                     INT REFERENCES bulletin_paie(id_bulletin) ON DELETE SET NULL,
    numero_recu                     VARCHAR(60) NOT NULL UNIQUE,
    date_depart                     DATE NOT NULL,
    motif_rupture                   VARCHAR(50) NOT NULL, -- DEMISSION, LICENCIEMENT, FIN_CONTRAT, RETRAITE, RUPTURE_CONVENTIONNELLE, AUTRE
    
    -- Paramètres de base au départ
    salaire_base                    NUMERIC(12,2) NOT NULL,
    taux_horaire                    NUMERIC(12,4) NOT NULL,
    anciennete_mois                 INT NOT NULL DEFAULT 0,
    
    -- 1. Dernier salaire proratisé
    jours_travailles_dernier_mois   NUMERIC(4,1) NOT NULL DEFAULT 0,
    salaire_prorata                 NUMERIC(12,2) NOT NULL DEFAULT 0,
    
    -- 2. Congés payés (ICCP = solde * taux_journalier_conge)
    solde_conges_restant            NUMERIC(6,2) NOT NULL DEFAULT 0,
    taux_journalier_conge           NUMERIC(12,2) NOT NULL DEFAULT 0,
    indemnite_conges_payes          NUMERIC(12,2) NOT NULL DEFAULT 0,
    
    -- 3. Indemnités de rupture
    mois_preavis                    NUMERIC(4,2) NOT NULL DEFAULT 0,
    indemnite_preavis               NUMERIC(12,2) NOT NULL DEFAULT 0,
    indemnite_licenciement          NUMERIC(12,2) NOT NULL DEFAULT 0,
    autres_indemnites               NUMERIC(12,2) NOT NULL DEFAULT 0,
    motif_autres_indemnites         VARCHAR(255),
    
    -- 4. Totaux Bruts & Fiscaux
    total_brut_stc                  NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_cotisations_sociales      NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_irsa                      NUMERIC(12,2) NOT NULL DEFAULT 0,
    net_imposable                   NUMERIC(12,2) NOT NULL DEFAULT 0,
    
    -- 5. Apurements et Déductions
    solde_prets_deduit              NUMERIC(12,2) NOT NULL DEFAULT 0,
    solde_avances_deduit            NUMERIC(12,2) NOT NULL DEFAULT 0,
    autres_deductions               NUMERIC(12,2) NOT NULL DEFAULT 0,
    motif_autres_deductions         VARCHAR(255),
    
    -- 6. Net Final STC
    net_a_payer_stc                 NUMERIC(12,2) NOT NULL DEFAULT 0,
    
    -- Statut et Cycle de vie
    statut                          VARCHAR(20) NOT NULL DEFAULT 'SIMULATION', -- SIMULATION, VALIDE, REGLE, ANNULE
    mode_reglement                  VARCHAR(30) DEFAULT 'VIREMENT',
    commentaire                     TEXT,
    
    -- Audit & Traçabilité
    cree_par                        INT REFERENCES utilisateur(id_utilisateur),
    valide_par                      INT REFERENCES utilisateur(id_utilisateur),
    date_validation                 TIMESTAMP WITH TIME ZONE,
    created_at                      TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at                      TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_stc_statut CHECK (statut IN ('SIMULATION', 'VALIDE', 'REGLE', 'ANNULE'))
);

CREATE INDEX IF NOT EXISTS idx_paie_stc_employe ON paie_stc(id_employe);
CREATE INDEX IF NOT EXISTS idx_paie_stc_periode ON paie_stc(id_periode_paie);
CREATE INDEX IF NOT EXISTS idx_paie_stc_statut ON paie_stc(statut);
