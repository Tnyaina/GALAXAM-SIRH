-- ==============================================================================
-- SP4 — PAIE : Réglementation Fiscale & Sociale Évolutive
-- Table : paie_config_legale (Millésime fiscal : SME, Plafonds, Seuils IRSA, HS)
-- ==============================================================================
-- PRINCIPE : Chaque ligne = 1 exercice fiscal (ex: LF_2026).
-- Les tables filles (bareme_irsa, ref_type_cotisation, ref_taux_majoration)
-- référencent cet identifiant pour une cohérence millésimée complète.
-- Le moteur de paie lit TOUJOURS la ligne active à la date de la période.
-- ==============================================================================

-- ------------------------------------------
-- 1. NETTOYAGE
-- ------------------------------------------
DROP TABLE IF EXISTS paie_config_legale CASCADE;

-- ------------------------------------------
-- 2. TABLE PRINCIPALE : MILLÉSIME FISCAL
-- ------------------------------------------
CREATE TABLE paie_config_legale (
    id_config_legale    SERIAL PRIMARY KEY,

    -- Identifiant du millésime (ex: 'LF_2026', 'LF_2027_V1')
    code_version        VARCHAR(30)  NOT NULL UNIQUE,
    libelle             VARCHAR(200) NOT NULL,

    -- Plage temporelle de validité (date_fin NULL = en vigueur)
    date_debut          DATE         NOT NULL,
    date_fin            DATE,

    -- -------------------------------------------------------
    -- 1. SALAIRE MINIMUM ET PLAFONDS SOCIAUX
    -- -------------------------------------------------------
    sme_national                NUMERIC(12,2) NOT NULL,
    sme_agricole                NUMERIC(12,2) NOT NULL,
    -- Plafond de cotisation sociale = N x SME (pour CNaPS, OSTIE, FMFP)
    plafond_cotisation_nb_sme   NUMERIC(4,2)  NOT NULL DEFAULT 8.00,

    -- -------------------------------------------------------
    -- 2. RÈGLES FISCALES IRSA (CGI — Loi de Finances)
    -- -------------------------------------------------------
    seuil_exoneration_irsa      NUMERIC(12,2) NOT NULL,
    minimum_perception_irsa     NUMERIC(12,2) NOT NULL,
    deduction_charge_famille    NUMERIC(12,2) NOT NULL,
    arrondi_base_irsa           VARCHAR(20)   NOT NULL DEFAULT 'CENTAINE_INF',

    -- -------------------------------------------------------
    -- 3. CADRE DU TEMPS DE TRAVAIL (Code du Travail L. 2003-044)
    -- -------------------------------------------------------
    duree_hebdo_legale          NUMERIC(5,2)  NOT NULL DEFAULT 40.00,
    -- Diviseur mensuel moyen : (40h x 52 semaines) / 12 mois
    diviseur_mensuel_heures     NUMERIC(7,4)  NOT NULL DEFAULT 173.3333,
    -- Nombre de HS/semaine avant bascule vers tranche HS_50
    seuil_hs_tranche1           NUMERIC(5,2)  NOT NULL DEFAULT 8.00,
    plafond_hs_hebdo            NUMERIC(5,2)  NOT NULL DEFAULT 20.00,

    -- -------------------------------------------------------
    -- 4. CONGÉS PAYÉS (Code du Travail Art. 85-88)
    -- -------------------------------------------------------
    acquisition_conge_mensuel_jours   NUMERIC(4,2) NOT NULL DEFAULT 2.50,
    diviseur_valorisation_conge_jours INT          NOT NULL DEFAULT 30,

    created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_dates_config CHECK (date_fin IS NULL OR date_fin > date_debut)
);

CREATE INDEX idx_config_legale_actif ON paie_config_legale(date_debut, date_fin)
    WHERE date_fin IS NULL;

-- ------------------------------------------
-- 3. AJOUT FK MILLÉSIME SUR LES TABLES FILLES
-- ------------------------------------------
ALTER TABLE bareme_irsa
    ADD COLUMN IF NOT EXISTS id_config_legale INT REFERENCES paie_config_legale(id_config_legale);

ALTER TABLE ref_type_cotisation
    ADD COLUMN IF NOT EXISTS id_config_legale INT REFERENCES paie_config_legale(id_config_legale);

ALTER TABLE ref_taux_majoration
    ADD COLUMN IF NOT EXISTS id_config_legale INT REFERENCES paie_config_legale(id_config_legale);

-- ------------------------------------------
-- 4. SEEDING : EXERCICES FISCAUX
-- ------------------------------------------

-- ---- EXERCICE 2024 (historique, cloture au 31/12/2025) ----
INSERT INTO paie_config_legale (
    code_version, libelle, date_debut, date_fin,
    sme_national, sme_agricole, plafond_cotisation_nb_sme,
    seuil_exoneration_irsa, minimum_perception_irsa, deduction_charge_famille, arrondi_base_irsa,
    duree_hebdo_legale, diviseur_mensuel_heures, seuil_hs_tranche1, plafond_hs_hebdo,
    acquisition_conge_mensuel_jours, diviseur_valorisation_conge_jours
) VALUES (
    'LF_2024',
    'Loi de Finances 2024 — Bareme IRSA 5 tranches, SME 200 000 Ar',
    '2024-01-01', '2025-12-31',
    200000.00, 205000.00, 8.00,
    350000.00, 2000.00, 2000.00, 'CENTAINE_INF',
    40.00, 173.3333, 8.00, 20.00,
    2.50, 30
);

-- ---- EXERCICE 2026 (en vigueur — Loi de Finances 2026 n°2025-021) ----
INSERT INTO paie_config_legale (
    code_version, libelle, date_debut, date_fin,
    sme_national, sme_agricole, plafond_cotisation_nb_sme,
    seuil_exoneration_irsa, minimum_perception_irsa, deduction_charge_famille, arrondi_base_irsa,
    duree_hebdo_legale, diviseur_mensuel_heures, seuil_hs_tranche1, plafond_hs_hebdo,
    acquisition_conge_mensuel_jours, diviseur_valorisation_conge_jours
) VALUES (
    'LF_2026',
    'Loi de Finances 2026 (Loi n°2025-021) — Nouveau bareme IRSA 6 tranches (25%), SME 262 680 Ar',
    '2026-01-01', NULL,
    262680.00, 266500.00, 8.00,
    350000.00, 3000.00, 2000.00, 'CENTAINE_INF',
    40.00, 173.3333, 8.00, 20.00,
    2.50, 30
);

-- ------------------------------------------
-- 5. MISE A JOUR COHERENCE TABLES FILLES
-- ------------------------------------------
UPDATE bareme_irsa
    SET id_config_legale = (SELECT id_config_legale FROM paie_config_legale WHERE code_version = 'LF_2026')
    WHERE date_debut >= '2026-01-01';

UPDATE ref_type_cotisation
    SET id_config_legale = (SELECT id_config_legale FROM paie_config_legale WHERE code_version = 'LF_2026')
    WHERE date_debut >= '2026-01-01';

UPDATE ref_taux_majoration
    SET id_config_legale = (SELECT id_config_legale FROM paie_config_legale WHERE code_version = 'LF_2026')
    WHERE date_debut >= '2026-01-01';

-- ------------------------------------------
-- 6. SUPPRESSION des parametres PAIE migres dans paie_config_legale
-- ------------------------------------------
DELETE FROM parametre_global WHERE code_parametre IN (
    'SME_COURANT',
    'DIVISEUR_HORAIRE_ABSENCE',
    'PLAFOND_COTISATION_SME_MULT',
    'MINIMUM_PERCEPTION_IRSA',
    'REDUCTION_IRSA_PAR_ENFANT'
);
-- NB : SEUIL_HS_TRANCHE_1 et PLAFOND_HEURES_SUPP_SEMAINE restent dans RH_GENERAL
--      car lus par le module Pointage avant d'acceder au moteur de paie.
--      SEUIL_HEURES_JOUR (8h) et SEUIL_HEURES_SEMAINE (40h) aussi conserves.

-- ------------------------------------------
-- 7. VUE UTILITAIRE : config active courante
-- ------------------------------------------
CREATE OR REPLACE VIEW v_config_legale_active AS
SELECT
    pcl.*,
    ROUND(pcl.sme_national * pcl.plafond_cotisation_nb_sme, 2) AS plafond_cotisation_mensuel
FROM paie_config_legale pcl
WHERE CURRENT_DATE BETWEEN pcl.date_debut AND COALESCE(pcl.date_fin, '9999-12-31'::DATE)
ORDER BY pcl.date_debut DESC
LIMIT 1;
