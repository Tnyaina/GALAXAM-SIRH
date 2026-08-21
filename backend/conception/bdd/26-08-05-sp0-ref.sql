-- ------------------------------------------
-- 1. NETTOYAGE DES TABLES (Ordre des contraintes)
-- ------------------------------------------

DROP TABLE IF EXISTS site_indemnite_config CASCADE;
DROP TABLE IF EXISTS site_calendrier_ferie CASCADE;
DROP TABLE IF EXISTS site_cotisation CASCADE;
DROP TABLE IF EXISTS parametre_global CASCADE;
DROP TABLE IF EXISTS ref_taux_majoration CASCADE;
DROP TABLE IF EXISTS ref_calendrier_ferie CASCADE;
DROP TABLE IF EXISTS ref_periode_chomee CASCADE;
DROP TABLE IF EXISTS ref_site CASCADE;
DROP TABLE IF EXISTS ref_type_intervenant CASCADE;
DROP TABLE IF EXISTS ref_indemnite CASCADE;
DROP TABLE IF EXISTS bareme_irsa CASCADE;
DROP TABLE IF EXISTS ref_type_cotisation CASCADE;
DROP TABLE IF EXISTS ref_banque CASCADE;
DROP TABLE IF EXISTS ref_type_contrat CASCADE;
DROP TABLE IF EXISTS ref_fonction CASCADE;
DROP TABLE IF EXISTS ref_service CASCADE;
DROP TABLE IF EXISTS ref_departement CASCADE;
DROP TABLE IF EXISTS ref_filiale CASCADE;

-- ------------------------------------------
-- 2. STRUCTURE ENTREPRISE & CATALOGUES
-- ------------------------------------------

CREATE TABLE ref_filiale (
    id_filiale SERIAL PRIMARY KEY,
    code_filiale VARCHAR(10) NOT NULL UNIQUE,
    raison_sociale VARCHAR(100) NOT NULL,
    nif VARCHAR(30),
    stat VARCHAR(30),
    seuil_effectif_alerte INT DEFAULT 19,
    est_actif BOOLEAN DEFAULT TRUE,
    date_desactivation TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ref_departement (
    id_departement SERIAL PRIMARY KEY,
    code_departement VARCHAR(10) NOT NULL UNIQUE,
    libelle VARCHAR(100) NOT NULL,
    date_fin_prevue DATE,
    est_actif BOOLEAN DEFAULT TRUE
);

CREATE TABLE ref_service (
    id_service SERIAL PRIMARY KEY,
    code_service VARCHAR(10) NOT NULL,
    libelle VARCHAR(100) NOT NULL,
    id_departement INT NOT NULL REFERENCES ref_departement(id_departement) ON DELETE CASCADE,
    est_actif BOOLEAN DEFAULT TRUE,
    CONSTRAINT uk_service_departement UNIQUE (code_service, id_departement)
);

CREATE TABLE ref_fonction (
    id_fonction SERIAL PRIMARY KEY,
    code_fonction VARCHAR(20) NOT NULL UNIQUE,
    libelle VARCHAR(100) NOT NULL,
    dispense_pointage BOOLEAN NOT NULL DEFAULT FALSE,  -- TRUE = poste autonome (présomption de présence 8h/j)
    est_actif BOOLEAN DEFAULT TRUE
);

CREATE TABLE ref_type_contrat (
    id_type_contrat SERIAL PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    libelle VARCHAR(100) NOT NULL,
    exige_date_fin BOOLEAN DEFAULT FALSE,
    est_actif BOOLEAN DEFAULT TRUE
);

CREATE TABLE ref_banque (
    id_banque SERIAL PRIMARY KEY,
    code_banque VARCHAR(10) NOT NULL UNIQUE,
    nom_banque VARCHAR(100) NOT NULL,
    code_swift VARCHAR(20),
    est_actif BOOLEAN DEFAULT TRUE
);

-- ------------------------------------------
-- 3. REGLES FISCALES, SOCIALES & BAREMES
-- ------------------------------------------

CREATE TABLE ref_type_cotisation (
    id_type_cotisation SERIAL PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    libelle VARCHAR(100) NOT NULL,
    taux_salarial NUMERIC(5,2),
    taux_patronal NUMERIC(5,2),
    date_debut DATE NOT NULL,
    date_fin DATE
);

CREATE TABLE bareme_irsa (
    id_tranche SERIAL PRIMARY KEY,
    numero_tranche INT NOT NULL,
    tranche_min NUMERIC(12,2) NOT NULL,
    tranche_max NUMERIC(12,2), -- NULL si tranche sans plafond
    taux NUMERIC(5,2) NOT NULL,
    date_debut DATE NOT NULL,
    date_fin DATE
);

CREATE TABLE ref_indemnite (
    id_indemnite SERIAL PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    libelle VARCHAR(100) NOT NULL,
    imposable_irsa BOOLEAN DEFAULT FALSE,
    est_actif BOOLEAN DEFAULT TRUE
);

CREATE TABLE ref_type_intervenant (
    id_type_intervenant SERIAL PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    libelle VARCHAR(100) NOT NULL,
    soumis_irsa BOOLEAN DEFAULT TRUE,
    soumis_isi_5 BOOLEAN DEFAULT FALSE,
    soumis_cnaps BOOLEAN DEFAULT TRUE,
    soumis_sante BOOLEAN DEFAULT TRUE,
    est_actif BOOLEAN DEFAULT TRUE
);

CREATE TABLE ref_taux_majoration (
    id_taux_majoration SERIAL PRIMARY KEY,
    type_majoration VARCHAR(20) NOT NULL,
    taux NUMERIC(5,2) NOT NULL,
    description TEXT,
    date_debut DATE NOT NULL,
    date_fin DATE
);

-- ------------------------------------------
-- 4. CONFIGURATION SITES & CALENDRIERS
-- ------------------------------------------

CREATE TABLE ref_site (
    id_site SERIAL PRIMARY KEY,
    code_site VARCHAR(10) NOT NULL UNIQUE,
    libelle VARCHAR(100) NOT NULL,
    id_filiale_rattachement INT REFERENCES ref_filiale(id_filiale),
    horaire_continu BOOLEAN DEFAULT TRUE,
    heure_debut_matin TIME,
    heure_fin_matin TIME,
    heure_debut_apres_midi TIME,
    heure_fin_apres_midi TIME,
    est_actif BOOLEAN DEFAULT TRUE
);

CREATE TABLE site_cotisation (
    id_site_cotisation SERIAL PRIMARY KEY,
    id_site INT NOT NULL REFERENCES ref_site(id_site),
    id_type_cotisation INT NOT NULL REFERENCES ref_type_cotisation(id_type_cotisation),
    est_actif BOOLEAN DEFAULT TRUE,
    CONSTRAINT uk_site_cotisation UNIQUE (id_site, id_type_cotisation)
);

CREATE TABLE site_indemnite_config (
    id_site_indemnite SERIAL PRIMARY KEY,
    id_site INT NOT NULL REFERENCES ref_site(id_site),
    id_indemnite INT NOT NULL REFERENCES ref_indemnite(id_indemnite),
    periodicite VARCHAR(20) NOT NULL DEFAULT 'JOURNALIER',
    montant_reference NUMERIC(12,2) NOT NULL,
    date_debut DATE NOT NULL,
    date_fin DATE,
    est_actif BOOLEAN DEFAULT TRUE
);

CREATE TABLE ref_calendrier_ferie (
    id_ferie SERIAL PRIMARY KEY,
    date_ferie DATE NOT NULL,
    libelle VARCHAR(100) NOT NULL,
    est_chome BOOLEAN DEFAULT TRUE,
    est_national BOOLEAN DEFAULT TRUE
);

CREATE TABLE site_calendrier_ferie (
    id_site_ferie SERIAL PRIMARY KEY,
    id_site INT NOT NULL REFERENCES ref_site(id_site),
    id_ferie INT NOT NULL REFERENCES ref_calendrier_ferie(id_ferie),
    CONSTRAINT uk_site_ferie UNIQUE (id_site, id_ferie)
);

CREATE TABLE ref_periode_chomee (
    id_periode SERIAL PRIMARY KEY,
    libelle VARCHAR(150) NOT NULL,
    id_site INT REFERENCES ref_site(id_site),
    date_debut DATE NOT NULL,
    date_fin DATE,
    est_paye BOOLEAN DEFAULT TRUE,
    taux_paiement NUMERIC(5,2) DEFAULT 100.00
);

CREATE TABLE parametre_global (
    id_parametre SERIAL PRIMARY KEY,
    code_parametre VARCHAR(50) NOT NULL UNIQUE,
    categorie VARCHAR(30) NOT NULL DEFAULT 'GENERAL',
    valeur VARCHAR(100) NOT NULL,
    description TEXT,
    date_debut DATE NOT NULL,
    date_fin DATE
);

-- Index d'optimisation
CREATE INDEX idx_parametre_actif ON parametre_global(code_parametre) WHERE date_fin IS NULL;
CREATE INDEX idx_parametre_categorie ON parametre_global(categorie);
CREATE INDEX idx_site_indemnite_actif ON site_indemnite_config(id_site, id_indemnite) WHERE date_fin IS NULL AND est_actif IS TRUE;

-- ==========================================
-- SEEDING DATA (VALEURS RÉELLES CONFORMES MADAGASCAR 2026)
-- ==========================================

-- 1. Filiales
INSERT INTO ref_filiale (code_filiale, raison_sociale, nif, stat, seuil_effectif_alerte) VALUES
('GLX', 'Galaxam Logistics', '1234567890', '9876543210', 19),
('VA', 'Vina sy Asa SARL', '0987654321', '0123456789', 0);

-- 2. Départements
INSERT INTO ref_departement (code_departement, libelle) VALUES
('LOG', 'Logistics'),
('LMD', 'Projet LMD');

-- 3. Services (Hierarchie Département)
INSERT INTO ref_service (code_service, libelle, id_departement) VALUES
('ADM', 'Administration & RH', 1),
('OPE', 'Opérations & Transit', 1),
('ADM', 'Administration Projet', 2),
('OPE', 'Opérations Terrain LMD', 2);

-- 4. Fonctions (Liste officielle de l'entreprise)
INSERT INTO ref_fonction (code_fonction, libelle, dispense_pointage) VALUES
('DG', 'DG', true),
('DAF', 'DAF', true),
('CHEF_COMPTABLE', 'Chef Comptable', false),
('AIDE_COMPTABLE', 'Aide-Comptable', false),
('OPE_MGR', 'OPE Manager', false),
('CHEF_DECLARANT', 'Chef Déclarant', false),
('AIDE_DECLARANT', 'Aide-Déclarant', false),
('PASSEUR_PIECES', 'Passeur de pièces', false),
('RESP_TOAMASINA', 'Resp bureau Toamasina', false),
('RESP_ENTREPOT', 'Resp Entrepôt', false),
('CHEF_ZONE', 'Chef de zone', false),
('AGENT_PLANIF', 'Agent de planification', false),
('MANUTENTIONNAIRE', 'Manutentionnaire', false),
('COMMERCIAL_EXEC', 'Commercial Executive', false),
('ASSIST_DIR', 'Assist de Direction', false),
('ASSIST_ADMIN', 'Assistante Admin', false),
('AGENT_B_D', 'Agent B à D', false),
('AGENT_TRANSMISSION', 'Agent de transmission', false),
('AGENT_TOUT_FAIRE', 'Agent à tout faire', false),
('AGENT_SURFACE', 'Agent de surface', false),
('GARDIEN', 'Gardien', false),
('AGENT_FORMATION', 'Agent en formation', false)
ON CONFLICT (code_fonction) DO NOTHING;

-- 5. Types de contrat & Intervenants
INSERT INTO ref_type_contrat (code, libelle, exige_date_fin) VALUES
('CDI', 'Contrat à Durée Indéterminée', FALSE),
('CDD', 'Contrat à Durée Déterminée', TRUE),
('PRESTATION', 'Contrat de Prestation', TRUE),
('TEMPORAIRE', 'Contrat d Engagement Temporaire', TRUE),
('STAGE', 'Convention / Contrat de Stage', TRUE);

INSERT INTO ref_type_intervenant (code, libelle, soumis_irsa, soumis_isi_5, soumis_cnaps, soumis_sante) VALUES
('PERMANENT', 'Salarié Permanent', TRUE, FALSE, TRUE, TRUE),
('CONSULTANT', 'Consultant Indépendant (+60 ans)', FALSE, TRUE, FALSE, TRUE),
('TEMPORAIRE', 'Travailleur Temporaire', FALSE, TRUE, FALSE, FALSE),
('STAGIAIRE',  'Stagiaire (Gratification)',FALSE, FALSE, FALSE, FALSE);

-- 6. Banques
INSERT INTO ref_banque (code_banque, nom_banque, code_swift) VALUES
('BOA', 'Bank of Africa Madagascar', 'AFRIMGMG'),
('BNI', 'BNI Madagascar', 'BNIMA3MG'),
('BFV', 'Société Générale Madagasikara', 'SOGEMGMG');

-- 7. Cotisations Sociales
INSERT INTO ref_type_cotisation (code, libelle, taux_salarial, taux_patronal, date_debut) VALUES
('CNAPS', 'Caisse Nationale de Prévoyance Sociale', 1.00, 13.00, '2026-01-01'),
('OSTIE', 'Organisation Sanitaire Tananarivienne', 1.00, 5.00, '2026-01-01'),
('OMSI', 'Organisation Médicale Inter-entreprises', 1.50, 5.50, '2026-01-01'),
('ISI', 'Impôt Synthétique Intermédiaire', 0.00, 5.00, '2026-01-01');

-- 8. Barème IRSA Officiel (6 Tranches - Loi de Finances 2026)
INSERT INTO bareme_irsa (numero_tranche, tranche_min, tranche_max, taux, date_debut) VALUES
(1, 0.00, 350000.00, 0.00, '2026-01-01'),
(2, 350001.00, 400000.00, 5.00, '2026-01-01'),
(3, 400001.00, 500000.00, 10.00, '2026-01-01'),
(4, 500001.00, 600000.00, 15.00, '2026-01-01'),
(5, 600001.00, 4000000.00, 20.00, '2026-01-01'),
(6, 4000001.00, NULL, 25.00, '2026-01-01');

-- 9. Majorations HS / Spéciales
INSERT INTO ref_taux_majoration (type_majoration, taux, description, date_debut) VALUES
('HS_30', 0.30, '41ᵉ à 48ᵉ heure de la semaine', '2026-01-01'),
('HS_50', 0.50, 'Au-delà de la 48ᵉ heure', '2026-01-01'),
('NUIT', 0.30, 'Heures de nuit (22h - 05h)', '2026-01-01'),
('DIMANCHE', 0.40, 'Travail le dimanche', '2026-01-01'),
('FERIE', 1.00, 'Travail jour férié', '2026-01-01');

-- 10. Indemnités & Configuration des Sites (Conforme aux spécifications réelles)
INSERT INTO ref_indemnite (code, libelle, imposable_irsa) VALUES
('DEPLACEMENT', 'Indemnité de Déplacement', FALSE),
('REPAS', 'Indemnité de Repas', FALSE);

INSERT INTO ref_site (code_site, libelle, id_filiale_rattachement, horaire_continu, heure_debut_matin, heure_fin_matin, heure_debut_apres_midi, heure_fin_apres_midi) VALUES
('TNR', 'Antananarivo - Siège', 1, TRUE, '08:00', '12:00', '13:00', '17:00'),
('TMM', 'Toamasina - Agence', 1, FALSE, '08:00', '12:00', '14:00', '18:00');

INSERT INTO site_indemnite_config (id_site, id_indemnite, periodicite, montant_reference, date_debut) VALUES
-- TNR Déplacement Mensuel : 31 200 Ar
(1, 1, 'MENSUEL', 31200.00, '2026-01-01'),
-- TMM Déplacement Mensuel : 53 200 Ar
(2, 1, 'MENSUEL', 53200.00, '2026-01-01'),
-- TNR Repas Journalier : 2 000 Ar
(1, 2, 'JOURNALIER', 2000.00, '2026-01-01'),
-- TMM Repas Journalier : 0 Ar
(2, 2, 'JOURNALIER', 0.00, '2026-01-01');

-- Cotisations de Santé et Prévoyance rattachées par Site
INSERT INTO site_cotisation (id_site, id_type_cotisation) VALUES
-- Antananarivo - Siège (TNR) : CNaPS (1) + OSTIE (2)
(1, 1),
(1, 2),
-- Toamasina - Agence (TMM) : CNaPS (1) + OMSI (3)
(2, 1),
(2, 3);

-- 11. Paramètres Globaux Légaux et Opérationnels
-- ============================================================
-- CONVENTION : séparés par CATEGORIE pour faciliter la lecture du code
--   RH_GENERAL  : règles métier communes (pointage, temps de travail)
--   POINTAGE    : seuils et montants spécifiques au module Pointage
--   PAIE        : paramètres utilisés UNIQUEMENT par le module Paie (futur)
-- ============================================================
INSERT INTO parametre_global (code_parametre, categorie, valeur, description, date_debut) VALUES

-- ---- RH_GENERAL : temps de travail (utilisés par Pointage) ----
('SEUIL_HEURES_JOUR',          'RH_GENERAL', '8',      'Heures normales quotidiennes', '2026-01-01'),
('SEUIL_HEURES_SEMAINE',       'RH_GENERAL', '40',     'Heures normales hebdomadaires', '2026-01-01'),
('PLAFOND_HEURES_SUPP_SEMAINE','RH_GENERAL', '20',     'Plafond légal HS/semaine (Décret 2007-563)', '2026-01-01'),
('SEUIL_HS_TRANCHE_1',         'RH_GENERAL', '8',      'Premières Xh HS/tronçon au taux 30% (41e−48e heure)', '2026-01-01'),

-- ---- POINTAGE : seuils et montants spécifiques au module Pointage ----
('SEUIL_NUIT_DEBUT',           'POINTAGE', '22:00', 'Début du travail de nuit (Art. 83 Code Travail Malgache)', '2026-01-01'),
('SEUIL_NUIT_FIN',             'POINTAGE', '05:00', 'Fin du travail de nuit', '2026-01-01'),
('SEUIL_2ND_REPAS_1',          'POINTAGE', '19:30', 'Heure sortie mini pour 2ᵉ repas tranche 1', '2026-01-01'),
('MONTANT_2ND_REPAS_1',        'POINTAGE', '2000',  '2ᵉ repas tranche 1 en Ariary (19h30-21h00)', '2026-01-01'),
('SEUIL_2ND_REPAS_2',          'POINTAGE', '21:00', 'Heure sortie mini pour 2ᵉ repas tranche 2', '2026-01-01'),
('MONTANT_2ND_REPAS_2',        'POINTAGE', '4000',  '2ᵉ repas tranche 2 en Ariary (> 21h00)', '2026-01-01'),
('SEUIL_PAUSE_MIDI_MIN',       'POINTAGE', '60',    'Pause midi minimale en minutes (détection « ne rentre pas midi » TMM)', '2026-01-01'),
('REDUCTION_DEPLACT_REPAS_MIDI','POINTAGE', '1000', 'Déduction indemnité déplacement (en Ar) si repas midi reçu sur site TMM', '2026-01-01'),

-- ---- PAIE : utilisés uniquement par le module Paie (futur) ----
-- Ces paramètres sont déjà stockés ici pour anticiper, mais ne seront lus que par le module Paie.
('SME_COURANT',                 'PAIE', '300000.00', 'Salaire Minimum Embauche en Ariary (2026)', '2026-01-01'),
('DIVISEUR_HORAIRE_ABSENCE',    'PAIE', '173.33',    'Heures/mois pour valoriser une absence (40h x 52/12)', '2026-01-01'),
('PLAFOND_COTISATION_SME_MULT', 'PAIE', '8',         '8 x SME = plafond de la base CNaPS/OSTIE', '2026-01-01'),
('MINIMUM_PERCEPTION_IRSA',     'PAIE', '3000.00',   'Minimum de perception légal IRSA en Ariary', '2026-01-01'),
('REDUCTION_IRSA_PAR_ENFANT',   'PAIE', '2000.00',   'Déduction IRSA par enfant à charge', '2026-01-01');