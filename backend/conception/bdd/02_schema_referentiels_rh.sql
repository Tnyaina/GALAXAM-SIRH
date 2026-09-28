-- =====================================================================
-- GALAXAM SIRH — MODULE 02 : RÉFÉRENTIELS RH & ORGANISATION STRUCTURELLE
-- Script canonique final (Zéro ALTER TABLE, Zéro OWNER, Zéro public.)
-- Sources : 26-08-05-sp0-ref.sql, 26-08-10-sp1-contrat.sql, 26-09-02-sp4-paie.sql
-- =====================================================================

-- 1. Nettoyage préalable (ordre inverse des dépendances)
DROP TABLE IF EXISTS site_calendrier_ferie CASCADE;
DROP TABLE IF EXISTS ref_calendrier_ferie CASCADE;
DROP TABLE IF EXISTS ref_periode_chomee CASCADE;
DROP TABLE IF EXISTS ref_categorie_professionnelle CASCADE;
DROP TABLE IF EXISTS ref_mode_calcul_paie CASCADE;
DROP TABLE IF EXISTS ref_motif_mouvement CASCADE;
DROP TABLE IF EXISTS ref_motif_sortie CASCADE;
DROP TABLE IF EXISTS ref_type_evenement_rh CASCADE;
DROP TABLE IF EXISTS ref_statut_rh CASCADE;
DROP TABLE IF EXISTS ref_type_intervenant CASCADE;
DROP TABLE IF EXISTS ref_type_contrat CASCADE;
DROP TABLE IF EXISTS ref_fonction CASCADE;
DROP TABLE IF EXISTS ref_service CASCADE;
DROP TABLE IF EXISTS ref_departement CASCADE;
DROP TABLE IF EXISTS ref_site CASCADE;
DROP TABLE IF EXISTS ref_banque CASCADE;
DROP TABLE IF EXISTS ref_filiale CASCADE;
DROP TABLE IF EXISTS paie_config_legale CASCADE;

-- 2. Structure Juridique & Filiales
CREATE TABLE ref_filiale (
    id_filiale SERIAL PRIMARY KEY,
    code_filiale VARCHAR(10) NOT NULL UNIQUE,
    raison_sociale VARCHAR(100) NOT NULL,
    nif VARCHAR(30),
    stat VARCHAR(30),
    adresse_siege VARCHAR(255),
    rcs VARCHAR(80),
    telephone VARCHAR(40),
    email VARCHAR(255),
    seuil_effectif_alerte INT DEFAULT 19,
    est_actif BOOLEAN DEFAULT TRUE,
    date_desactivation TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. Référentiel Bancaire
CREATE TABLE ref_banque (
    id_banque SERIAL PRIMARY KEY,
    code_banque VARCHAR(10) NOT NULL UNIQUE,
    nom_banque VARCHAR(100) NOT NULL,
    code_swift VARCHAR(20),
    est_actif BOOLEAN DEFAULT TRUE
);

-- 4. Sites Géographiques & Établissements
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
    plancher_arrivee_actif BOOLEAN NOT NULL DEFAULT FALSE,
    heure_entree_minimale_valorisee TIME,
    est_actif BOOLEAN DEFAULT TRUE,
    CONSTRAINT chk_ref_site_plancher_arrivee CHECK (NOT plancher_arrivee_actif OR heure_entree_minimale_valorisee IS NOT NULL)
);

CREATE INDEX idx_ref_site_filiale_rattachement ON ref_site(id_filiale_rattachement);

-- 5. Départements & Services
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

CREATE INDEX idx_ref_service_departement ON ref_service(id_departement);

-- 6. Fonctions & Postes
CREATE TABLE ref_fonction (
    id_fonction SERIAL PRIMARY KEY,
    code_fonction VARCHAR(20) NOT NULL UNIQUE,
    libelle VARCHAR(100) NOT NULL,
    dispense_pointage BOOLEAN NOT NULL DEFAULT FALSE,
    est_actif BOOLEAN DEFAULT TRUE
);

-- 7. Types de Contrats & Types d'Intervenants
CREATE TABLE ref_type_contrat (
    id_type_contrat SERIAL PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    libelle VARCHAR(100) NOT NULL,
    exige_date_fin BOOLEAN DEFAULT FALSE,
    est_actif BOOLEAN DEFAULT TRUE
);

CREATE TABLE ref_type_intervenant (
    id_type_intervenant SERIAL PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    libelle VARCHAR(100) NOT NULL,
    soumis_irsa BOOLEAN NOT NULL,
    soumis_isi_5 BOOLEAN NOT NULL,
    soumis_cnaps BOOLEAN NOT NULL,
    soumis_sante BOOLEAN NOT NULL,
    est_actif BOOLEAN DEFAULT TRUE
);

-- 8. Statuts RH & Motifs (Cycle de Vie Salarié)
CREATE TABLE ref_statut_rh (
    id_statut_rh SERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    libelle VARCHAR(100) NOT NULL,
    ordre INTEGER NOT NULL,
    est_terminal BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE ref_type_evenement_rh (
    id_type_evenement_rh SERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    libelle VARCHAR(100) NOT NULL,
    description TEXT
);

CREATE TABLE ref_motif_sortie (
    id_motif_sortie SERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    libelle VARCHAR(100) NOT NULL,
    actif BOOLEAN DEFAULT TRUE
);

CREATE TABLE ref_motif_mouvement (
    id_motif_mouvement SERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    libelle VARCHAR(100) NOT NULL,
    actif BOOLEAN DEFAULT TRUE
);

-- 9. Mode de Calcul & Catégories Professionnelles (avec colonnes finales directes)
CREATE TABLE ref_mode_calcul_paie (
    id_mode_calcul_paie SERIAL PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    libelle VARCHAR(100) NOT NULL,
    description TEXT,
    actif BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ref_categorie_professionnelle (
    id_categorie_professionnelle SERIAL PRIMARY KEY,
    code VARCHAR(10) NOT NULL UNIQUE,
    libelle VARCHAR(100) NOT NULL,
    description TEXT,
    est_eligible_hs BOOLEAN NOT NULL DEFAULT TRUE,
    est_cadre BOOLEAN NOT NULL DEFAULT FALSE,
    est_eligible_exoneration_hs BOOLEAN NOT NULL DEFAULT TRUE,
    dispense_pointage BOOLEAN NOT NULL DEFAULT FALSE,
    actif BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- 10. Calendriers Fériés & Périodes Chômées
CREATE TABLE ref_calendrier_ferie (
    id_ferie SERIAL PRIMARY KEY,
    date_ferie DATE NOT NULL,
    libelle VARCHAR(100) NOT NULL,
    est_chome BOOLEAN DEFAULT TRUE,
    est_national BOOLEAN DEFAULT TRUE,
    est_actif BOOLEAN DEFAULT TRUE
);

CREATE TABLE site_calendrier_ferie (
    id_site_ferie SERIAL PRIMARY KEY,
    id_site INT NOT NULL REFERENCES ref_site(id_site),
    id_ferie INT NOT NULL REFERENCES ref_calendrier_ferie(id_ferie),
    CONSTRAINT uk_site_ferie UNIQUE (id_site, id_ferie)
);

CREATE INDEX idx_site_calendrier_ferie_ferie ON site_calendrier_ferie(id_ferie);

CREATE TABLE ref_periode_chomee (
    id_periode SERIAL PRIMARY KEY,
    libelle VARCHAR(150) NOT NULL,
    id_site INT REFERENCES ref_site(id_site),
    date_debut DATE NOT NULL,
    date_fin DATE,
    est_paye BOOLEAN DEFAULT TRUE,
    taux_paiement NUMERIC(5,2) DEFAULT 100.00,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ref_periode_chomee_site ON ref_periode_chomee(id_site);

-- 11. Configuration Légale & Fiscale (Millésimes annuels de paie)
CREATE TABLE paie_config_legale (
    id_config_legale SERIAL PRIMARY KEY,
    code_version VARCHAR(30) NOT NULL UNIQUE,
    libelle VARCHAR(200) NOT NULL,
    date_debut DATE NOT NULL,
    date_fin DATE,
    sme_national NUMERIC(12,2) NOT NULL,
    sme_agricole NUMERIC(12,2) NOT NULL,
    plafond_cotisation_nb_sme NUMERIC(4,2) NOT NULL DEFAULT 8.00,
    seuil_exoneration_irsa NUMERIC(12,2) NOT NULL,
    minimum_perception_irsa NUMERIC(12,2) NOT NULL,
    deduction_charge_famille NUMERIC(12,2) NOT NULL,
    arrondi_base_irsa VARCHAR(20) NOT NULL DEFAULT 'CENTAINE_INF',
    duree_hebdo_legale NUMERIC(5,2) NOT NULL DEFAULT 40.00,
    diviseur_mensuel_heures NUMERIC(7,4) NOT NULL DEFAULT 173.33,
    seuil_hs_tranche1 NUMERIC(5,2) NOT NULL DEFAULT 8.00,
    plafond_hs_hebdo NUMERIC(5,2) NOT NULL DEFAULT 20.00,
    acquisition_conge_mensuel_jours NUMERIC(4,2) NOT NULL DEFAULT 2.50,
    diviseur_valorisation_conge_jours INT NOT NULL DEFAULT 30,
    exoneration_hs_irsa_active BOOLEAN NOT NULL DEFAULT TRUE,
    plafond_mensuel_hs_exonerees NUMERIC(5,2) NOT NULL DEFAULT 20.00,
    source_type VARCHAR(50) DEFAULT 'CGI_MADAGASCAR',
    source_reference VARCHAR(255),
    article_reference VARCHAR(80) DEFAULT '01.03.16',
    publication_date DATE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_dates_config CHECK (date_fin IS NULL OR date_fin > date_debut)
);

CREATE INDEX idx_config_legale_actif ON paie_config_legale(date_debut, date_fin) WHERE date_fin IS NULL;

-- =====================================================================
-- DONNÉES DE RÉFÉRENCE DE BASE (INITIAL SEEDS DES ARCHIVES)
-- =====================================================================

-- Filiales
INSERT INTO ref_filiale (id_filiale, code_filiale, raison_sociale, nif, stat, adresse_siege, seuil_effectif_alerte,rcs) VALUES
(1, 'GLX', 'Galaxam Logistics', '3000019434', '9876543210', '3e étage, Tour Forello, Tanjombato', 19,"");
SELECT setval('ref_filiale_id_filiale_seq', (SELECT GREATEST(MAX(id_filiale), 1) FROM ref_filiale));

-- Banques
INSERT INTO ref_banque (code_banque, nom_banque, code_swift) VALUES
('BOA', 'Bank of Africa Madagascar', 'AFRIMGMG'),
('BNI', 'BNI Madagascar', 'CLMDMGMG'),
('BFV', 'BRED Madagasikara (ex-BFV)', 'BFAVMGMG');
SELECT setval('ref_banque_id_banque_seq', (SELECT GREATEST(MAX(id_banque), 3) FROM ref_banque));

-- Sites
INSERT INTO ref_site (id_site, code_site, libelle, id_filiale_rattachement, horaire_continu, heure_debut_matin, heure_fin_matin, heure_debut_apres_midi, heure_fin_apres_midi, plancher_arrivee_actif, heure_entree_minimale_valorisee) VALUES
(1, 'TNR', 'Antananarivo - Siège', 1, TRUE, '08:00', '12:00', '13:00', '17:00', TRUE, '07:30'),
(2, 'TMM', 'Toamasina - Agence', 1, FALSE, '08:00', '12:00', '14:00', '18:00', TRUE, '07:30');
SELECT setval('ref_site_id_site_seq', (SELECT GREATEST(MAX(id_site), 2) FROM ref_site));

-- Départements
INSERT INTO ref_departement (id_departement, code_departement, libelle) VALUES
(1, 'LOG', 'Logistics');
SELECT setval('ref_departement_id_departement_seq', (SELECT GREATEST(MAX(id_departement), 1) FROM ref_departement));

-- Services
INSERT INTO ref_service (id_service, code_service, libelle, id_departement) VALUES
(1, 'ADM', 'Administration & RH', 1),
(2, 'OPE', 'Opérations & Transit', 1);
SELECT setval('ref_service_id_service_seq', (SELECT GREATEST(MAX(id_service), 2) FROM ref_service));

-- Fonctions
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
('AGENT_FORMATION', 'Agent en formation', false);

-- Types de Contrat
INSERT INTO ref_type_contrat (id_type_contrat, code, libelle, exige_date_fin) VALUES
(1, 'CDI', 'Contrat à Durée Indéterminée', FALSE),
(2, 'CDD', 'Contrat à Durée Déterminée', TRUE),
(3, 'PRESTATION', 'Contrat de Prestation', TRUE),
(4, 'TEMPORAIRE', 'Contrat d Engagement Temporaire', TRUE),
(5, 'STAGE', 'Convention / Contrat de Stage', TRUE);
SELECT setval('ref_type_contrat_id_type_contrat_seq', (SELECT GREATEST(MAX(id_type_contrat), 5) FROM ref_type_contrat));

-- Types d'Intervenants
INSERT INTO ref_type_intervenant (id_type_intervenant, code, libelle, soumis_irsa, soumis_isi_5, soumis_cnaps, soumis_sante) VALUES
(1, 'PERMANENT', 'Salarié Permanent', TRUE, FALSE, TRUE, TRUE),
(2, 'CONSULTANT', 'Consultant Indépendant (+60 ans)', FALSE, TRUE, FALSE, TRUE),
(3, 'TEMPORAIRE', 'Travailleur Temporaire', FALSE, TRUE, FALSE, FALSE),
(4, 'STAGIAIRE', 'Stagiaire', FALSE, FALSE, FALSE, FALSE);
SELECT setval('ref_type_intervenant_id_type_intervenant_seq', (SELECT GREATEST(MAX(id_type_intervenant), 4) FROM ref_type_intervenant));

-- Statuts RH
INSERT INTO ref_statut_rh (id_statut_rh, code, libelle, ordre, est_terminal) VALUES
(1, 'BROUILLON', 'Brouillon', 10, FALSE),
(2, 'PLANIFIE', 'Planifié (effet futur)', 20, FALSE),
(3, 'A_SIGNER', 'En attente de signature', 25, FALSE),
(4, 'ACTIF', 'Actif', 30, FALSE),
(5, 'SUSPENDU', 'Suspendu', 35, FALSE),
(6, 'HISTORISE', 'Historisé', 40, TRUE),
(7, 'CLOTURE', 'Clôturé', 50, TRUE),
(8, 'ROMPU', 'Rompu', 60, TRUE),
(9, 'ANNULE', 'Annulé', 70, TRUE);
SELECT setval('ref_statut_rh_id_statut_rh_seq', (SELECT GREATEST(MAX(id_statut_rh), 9) FROM ref_statut_rh));

-- Événements RH
INSERT INTO ref_type_evenement_rh (code, libelle) VALUES
('EMBAUCHE_INITIALE', 'Embauche initiale'),
('RENOUVELLEMENT_CDD', 'Renouvellement de CDD'),
('TRANSFORMATION_CDI', 'Passage CDD vers CDI'),
('EMBAUCHE_APRES_STAGE', 'Passage Stage vers CDD/CDI'),
('MUTATION', 'Changement de site ou de service'),
('PROMOTION', 'Promotion ou changement de fonction'),
('REVALORISATION', 'Revalorisation salariale'),
('RENOUVELLEMENT_ESSAI', 'Renouvellement de la période d essai'),
('CONFIRMATION_ESSAI', 'Confirmation de fin de période d essai'),
('RUPTURE_ESSAI', 'Rupture de la période d essai'),
('DEMISSION', 'Démission du salarié'),
('LICENCIEMENT', 'Licenciement du salarié'),
('RUPTURE_CONVENTIONNELLE', 'Rupture conventionnelle'),
('DEPART_RETRAITE', 'Départ à la retraite'),
('REPRISE_CONSULTANT', 'Embauche d un retraité comme consultant'),
('REPRISE_ANCIENNETE', 'Réintégration / Reprise d ancienneté'),
('FIN_CONTRAT', 'Fin de contrat / Sortie de l effectif');

-- Motifs de Sortie
INSERT INTO ref_motif_sortie (code, libelle) VALUES
('DEMISSION', 'Démission de l employé'),
('LICENCIEMENT', 'Licenciement'),
('FIN_CONTRAT', 'Fin du contrat (CDD / Stage)'),
('RETRAITE', 'Départ en retraite'),
('DECES', 'Décès de l employé'),
('RUPTURE_CONVENTIONNELLE', 'Rupture conventionnelle'),
('AUTRE', 'Autre motif');

-- Motifs de Mouvement
INSERT INTO ref_motif_mouvement (code, libelle) VALUES
('EMBAUCHE', 'Embauche initiale'),
('PROMOTION', 'Promotion ou changement de fonction'),
('MUTATION', 'Mutation de site/département/service'),
('AVENANT', 'Avenant au contrat'),
('REVALORISATION', 'Revalorisation du salaire'),
('REORGANISATION', 'Réorganisation interne'),
('AUTRE', 'Autre mouvement');

-- Modes de Calcul de Paie
INSERT INTO ref_mode_calcul_paie (id_mode_calcul_paie, code, libelle, description) VALUES
(1, 'MENSUEL', 'Mensuel fixe', 'Calcul sur forfait mensuel standard'),
(2, 'HORAIRE', 'Taux horaire', 'Calcul au nombre d heures effectuées'),
(3, 'JOURNALIER', 'Forfait journalier', 'Calcul au tarif par jour travaillé');
SELECT setval('ref_mode_calcul_paie_id_mode_calcul_paie_seq', (SELECT GREATEST(MAX(id_mode_calcul_paie), 3) FROM ref_mode_calcul_paie));

-- Catégories Professionnelles
INSERT INTO ref_categorie_professionnelle (id_categorie_professionnelle, code, libelle, description, est_eligible_hs, est_cadre, est_eligible_exoneration_hs, dispense_pointage) VALUES
(1, 'CADRE', 'Cadre', 'Cadres et ingénieurs', FALSE, TRUE, FALSE, FALSE),
(2, 'A', 'Agent', 'Techniciens et agents', TRUE, FALSE, TRUE, FALSE);
SELECT setval('ref_categorie_professionnelle_id_categorie_professionnelle_seq', (SELECT GREATEST(MAX(id_categorie_professionnelle), 2) FROM ref_categorie_professionnelle));

-- Configuration Légale de Paie :
-- 1. LF_2024_2025 (applicable du 01/01/2024 au 31/12/2025) : SME 262 680 Ar, Plafond CNaPS 2 101 440 Ar, Barème 5 tranches (couvre les tests déc 2025 : 26 nov - 25 déc)
-- 2. LF_2026_JAN_FEB (applicable du 01/01/2026 au 28/02/2026) : Décret CNaPS SME 262 680 Ar maintenu jusqu'au 28/02/2026, Barème IRSA 6 tranches LF 2026 (Min IRSA 3 000 Ar, tranche 25%)
-- 3. LF_2026 (en vigueur depuis le 01/03/2026) : Revalorisation effective SME 300 000 Ar (agricole 304 300 Ar), Plafond CNaPS 2 400 000 Ar, Barème 6 tranches (25%), Min IRSA 3 000 Ar
INSERT INTO paie_config_legale (
    id_config_legale, code_version, libelle, date_debut, date_fin,
    sme_national, sme_agricole, plafond_cotisation_nb_sme,
    seuil_exoneration_irsa, minimum_perception_irsa, deduction_charge_famille, arrondi_base_irsa,
    duree_hebdo_legale, diviseur_mensuel_heures, seuil_hs_tranche1, plafond_hs_hebdo,
    acquisition_conge_mensuel_jours, diviseur_valorisation_conge_jours,
    exoneration_hs_irsa_active, plafond_mensuel_hs_exonerees,
    source_type, source_reference, article_reference, publication_date
) VALUES 
(
    1,
    'LF_2024_2025',
    'Loi de Finances / Réglementation 2022-2025 — SME 262 680 Ar, Plafond CNaPS 2 101 440 Ar',
    '2022-01-01', '2025-12-31',
    262680.00, 266500.00, 8.00,
    350000.00, 2000.00, 2000.00, 'CENTAINE_INF',
    40.00, 173.33, 8.00, 20.00,
    2.50, 30,
    TRUE, 20.00,
    'CGI_MADAGASCAR', 'Code des impôts 2025', '01.03.16', '2024-01-01'
),
(
    3,
    'LF_2026_JAN_FEB',
    'Loi de Finances 2026 (Janv-Févr 2026) — IRSA 6 Tranches, SME CNaPS 262 680 Ar',
    '2026-01-01', '2026-02-28',
    262680.00, 266500.00, 8.00,
    350000.00, 3000.00, 2000.00, 'CENTAINE_INF',
    40.00, 173.33, 8.00, 20.00,
    2.50, 30,
    TRUE, 20.00,
    'CGI_MADAGASCAR', 'Loi de finances 2026', '01.03.16', '2025-12-31'
),
(
    2,
    'LF_2026',
    'Loi de Finances 2026 — Revalorisation SME 300 000 Ar, Plafond CNaPS 2 400 000 Ar, Min IRSA 3 000 Ar',
    '2026-03-01', NULL,
    300000.00, 304300.00, 8.00,
    350000.00, 3000.00, 2000.00, 'CENTAINE_INF',
    40.00, 173.33, 8.00, 20.00,
    2.50, 30,
    TRUE, 20.00,
    'CGI_MADAGASCAR', 'Loi de finances 2026', '01.03.16', '2026-02-15'
);
SELECT setval('paie_config_legale_id_config_legale_seq', (SELECT GREATEST(MAX(id_config_legale), 3) FROM paie_config_legale));
