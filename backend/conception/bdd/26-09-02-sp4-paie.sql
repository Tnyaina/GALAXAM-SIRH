-- ==============================================================================
-- SP4 — PAIE : Réglementation Fiscale & Sociale Évolutive (Conception Finale)
-- ==============================================================================
-- PRINCIPE : Chaque millésime fiscal (ex: LF_2026) est centralisé dans
-- paie_config_legale. Les tables fiscales filles (bareme_irsa,
-- ref_type_cotisation, ref_taux_majoration) référencent directement cet
-- identifiant à la création, garantissant une autonomie totale du module.
-- ==============================================================================

-- ------------------------------------------
-- 1. NETTOYAGE DES TABLES DU MODULE PAIE
-- ------------------------------------------
DROP VIEW IF EXISTS v_config_legale_active CASCADE;
DROP TABLE IF EXISTS site_cotisation CASCADE;
DROP TABLE IF EXISTS bareme_irsa CASCADE;
DROP TABLE IF EXISTS ref_type_cotisation CASCADE;
DROP TABLE IF EXISTS ref_taux_majoration CASCADE;
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

    -- 1. Salaire Minimum et Plafonds Sociaux
    sme_national                NUMERIC(12,2) NOT NULL,
    sme_agricole                NUMERIC(12,2) NOT NULL,
    plafond_cotisation_nb_sme   NUMERIC(4,2)  NOT NULL DEFAULT 8.00,

    -- 2. Règles Fiscales IRSA (CGI — Loi de Finances)
    seuil_exoneration_irsa      NUMERIC(12,2) NOT NULL,
    minimum_perception_irsa     NUMERIC(12,2) NOT NULL,
    deduction_charge_famille    NUMERIC(12,2) NOT NULL,
    arrondi_base_irsa           VARCHAR(20)   NOT NULL DEFAULT 'CENTAINE_INF',

    -- 3. Cadre du Temps de Travail (Code du Travail L. 2003-044)
    duree_hebdo_legale          NUMERIC(5,2)  NOT NULL DEFAULT 40.00,
    diviseur_mensuel_heures     NUMERIC(7,4)  NOT NULL DEFAULT 173.3333,
    seuil_hs_tranche1           NUMERIC(5,2)  NOT NULL DEFAULT 8.00,
    plafond_hs_hebdo            NUMERIC(5,2)  NOT NULL DEFAULT 20.00,

    -- 4. Congés Payés (Code du Travail Art. 85-88)
    acquisition_conge_mensuel_jours   NUMERIC(4,2) NOT NULL DEFAULT 2.50,
    diviseur_valorisation_conge_jours INT          NOT NULL DEFAULT 30,

    created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_dates_config CHECK (date_fin IS NULL OR date_fin > date_debut)
);

CREATE INDEX idx_config_legale_actif ON paie_config_legale(date_debut, date_fin)
    WHERE date_fin IS NULL;

-- ------------------------------------------
-- 3. COTISATIONS SOCIALES (Légales & Conventionnelles)
-- ------------------------------------------
CREATE TABLE ref_type_cotisation (
    id_type_cotisation  SERIAL PRIMARY KEY,
    id_config_legale    INT REFERENCES paie_config_legale(id_config_legale) ON DELETE CASCADE,
    code                VARCHAR(20) NOT NULL UNIQUE,
    libelle             VARCHAR(100) NOT NULL,
    taux_salarial       NUMERIC(5,2),
    taux_patronal       NUMERIC(5,2),
    plafonne            BOOLEAN DEFAULT TRUE,
    ordre               INT DEFAULT 1,
    est_actif           BOOLEAN DEFAULT TRUE,
    date_debut          DATE NOT NULL DEFAULT '2026-01-01',
    date_fin            DATE
);

CREATE INDEX idx_type_cotisation_config ON ref_type_cotisation(id_config_legale);

-- ------------------------------------------
-- 4. BARÈME OFFICIEL DE L'IRSA
-- ------------------------------------------
CREATE TABLE bareme_irsa (
    id_tranche          SERIAL PRIMARY KEY,
    id_config_legale    INT REFERENCES paie_config_legale(id_config_legale) ON DELETE CASCADE,
    numero_tranche      INT NOT NULL,
    tranche_min         NUMERIC(12,2) NOT NULL,
    tranche_max         NUMERIC(12,2), -- NULL si tranche supérieure sans plafond
    taux                NUMERIC(5,2) NOT NULL,
    date_debut          DATE NOT NULL,
    date_fin            DATE
);

CREATE INDEX idx_bareme_irsa_config ON bareme_irsa(id_config_legale);

-- ------------------------------------------
-- 5. MAJORATIONS HEURES SUPPLÉMENTAIRES & SPÉCIALES
-- ------------------------------------------
CREATE TABLE ref_taux_majoration (
    id_taux_majoration  SERIAL PRIMARY KEY,
    id_config_legale    INT REFERENCES paie_config_legale(id_config_legale) ON DELETE CASCADE,
    type_majoration     VARCHAR(20) NOT NULL,
    taux                NUMERIC(5,2) NOT NULL,
    description         TEXT,
    date_debut          DATE NOT NULL,
    date_fin            DATE
);

CREATE INDEX idx_taux_majoration_config ON ref_taux_majoration(id_config_legale);

-- ------------------------------------------
-- 6. RATTACHEMENT DES COTISATIONS PAR SITE
-- ------------------------------------------
CREATE TABLE site_cotisation (
    id_site_cotisation  SERIAL PRIMARY KEY,
    id_site             INT NOT NULL REFERENCES ref_site(id_site) ON DELETE CASCADE,
    id_type_cotisation  INT NOT NULL REFERENCES ref_type_cotisation(id_type_cotisation) ON DELETE CASCADE,
    est_actif           BOOLEAN DEFAULT TRUE,
    CONSTRAINT uk_site_cotisation UNIQUE (id_site, id_type_cotisation)
);

-- ------------------------------------------
-- 7. SEEDING INITIAL : EXERCICES FISCAUX
-- ------------------------------------------

-- ---- EXERCICE 2024 (historique, clôturé au 31/12/2025) ----
INSERT INTO paie_config_legale (
    code_version, libelle, date_debut, date_fin,
    sme_national, sme_agricole, plafond_cotisation_nb_sme,
    seuil_exoneration_irsa, minimum_perception_irsa, deduction_charge_famille, arrondi_base_irsa,
    duree_hebdo_legale, diviseur_mensuel_heures, seuil_hs_tranche1, plafond_hs_hebdo,
    acquisition_conge_mensuel_jours, diviseur_valorisation_conge_jours
) VALUES (
    'LF_2024',
    'Loi de Finances 2024 — Barème IRSA 5 tranches, SME 200 000 Ar',
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
    'Loi de Finances 2026 (Loi n°2025-021) — Nouveau barème IRSA 6 tranches (25%), SME 262 680 Ar',
    '2026-01-01', NULL,
    262680.00, 266500.00, 8.00,
    350000.00, 3000.00, 2000.00, 'CENTAINE_INF',
    40.00, 173.3333, 8.00, 20.00,
    2.50, 30
);

-- ------------------------------------------
-- 8. SEEDING TABLES FILLES (Rattachées au Millésime LF_2026)
-- ------------------------------------------

-- Cotisations Sociales
INSERT INTO ref_type_cotisation (id_config_legale, code, libelle, taux_salarial, taux_patronal, date_debut) VALUES
((SELECT id_config_legale FROM paie_config_legale WHERE code_version = 'LF_2026'), 'CNAPS', 'Caisse Nationale de Prévoyance Sociale', 1.00, 13.00, '2026-01-01'),
((SELECT id_config_legale FROM paie_config_legale WHERE code_version = 'LF_2026'), 'OSTIE', 'Organisation Sanitaire Tananarivienne', 1.00, 5.00, '2026-01-01'),
((SELECT id_config_legale FROM paie_config_legale WHERE code_version = 'LF_2026'), 'OMSI', 'Organisation Médicale Inter-entreprises', 1.50, 5.50, '2026-01-01'),
((SELECT id_config_legale FROM paie_config_legale WHERE code_version = 'LF_2026'), 'ISI', 'Impôt Synthétique Intermédiaire', 0.00, 5.00, '2026-01-01');

-- Barème IRSA Officiel (6 Tranches - Loi de Finances 2026)
INSERT INTO bareme_irsa (id_config_legale, numero_tranche, tranche_min, tranche_max, taux, date_debut) VALUES
((SELECT id_config_legale FROM paie_config_legale WHERE code_version = 'LF_2026'), 1, 0.00, 350000.00, 0.00, '2026-01-01'),
((SELECT id_config_legale FROM paie_config_legale WHERE code_version = 'LF_2026'), 2, 350001.00, 400000.00, 5.00, '2026-01-01'),
((SELECT id_config_legale FROM paie_config_legale WHERE code_version = 'LF_2026'), 3, 400001.00, 500000.00, 10.00, '2026-01-01'),
((SELECT id_config_legale FROM paie_config_legale WHERE code_version = 'LF_2026'), 4, 500001.00, 600000.00, 15.00, '2026-01-01'),
((SELECT id_config_legale FROM paie_config_legale WHERE code_version = 'LF_2026'), 5, 600001.00, 4000000.00, 20.00, '2026-01-01'),
((SELECT id_config_legale FROM paie_config_legale WHERE code_version = 'LF_2026'), 6, 4000001.00, NULL, 25.00, '2026-01-01');

-- Majorations HS / Spéciales
INSERT INTO ref_taux_majoration (id_config_legale, type_majoration, taux, description, date_debut) VALUES
((SELECT id_config_legale FROM paie_config_legale WHERE code_version = 'LF_2026'), 'HS_30', 0.30, '41ᵉ à 48ᵉ heure de la semaine', '2026-01-01'),
((SELECT id_config_legale FROM paie_config_legale WHERE code_version = 'LF_2026'), 'HS_50', 0.50, 'Au-delà de la 48ᵉ heure', '2026-01-01'),
((SELECT id_config_legale FROM paie_config_legale WHERE code_version = 'LF_2026'), 'NUIT', 0.30, 'Heures de nuit (22h - 05h)', '2026-01-01'),
((SELECT id_config_legale FROM paie_config_legale WHERE code_version = 'LF_2026'), 'DIMANCHE', 0.40, 'Travail le dimanche', '2026-01-01'),
((SELECT id_config_legale FROM paie_config_legale WHERE code_version = 'LF_2026'), 'FERIE', 1.00, 'Travail jour férié', '2026-01-01');

-- Cotisations de Santé et Prévoyance rattachées par Site
INSERT INTO site_cotisation (id_site, id_type_cotisation) VALUES
-- Antananarivo - Siège (TNR) : CNaPS (1) + OSTIE (2)
(1, 1),
(1, 2),
-- Toamasina - Agence (TMM) : CNaPS (1) + OMSI (3)
(2, 1),
(2, 3);

-- ------------------------------------------
-- 9. VUE UTILITAIRE : config active courante
-- ------------------------------------------
CREATE OR REPLACE VIEW v_config_legale_active AS
SELECT
    pcl.*,
    ROUND(pcl.sme_national * pcl.plafond_cotisation_nb_sme, 2) AS plafond_cotisation_mensuel
FROM paie_config_legale pcl
WHERE CURRENT_DATE BETWEEN pcl.date_debut AND COALESCE(pcl.date_fin, '9999-12-31'::DATE)
ORDER BY pcl.date_debut DESC
LIMIT 1;
