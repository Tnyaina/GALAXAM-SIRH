-- =====================================================================
-- GALAXAM SIRH — MODULE 02 : RÉFÉRENTIELS RH & ORGANISATION STRUCTURELLE
-- Script canonique final (Zéro ALTER TABLE, Zéro OWNER, Zéro public.)
-- Sources : 26-08-05-sp0-ref.sql, 26-08-10-sp1-contrat.sql, 26-09-02-sp4-paie.sql
-- Installation initiale uniquement sur une base ou un schéma cible vierge.
-- Exécuter les scripts 01 → 06 dans cet ordre ; ne pas utiliser pour une mise à jour
-- ni pour une réinitialisation.
-- =====================================================================

-- Extensions nécessaires
CREATE EXTENSION IF NOT EXISTS btree_gist;

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


-- 5. Départements & Services
CREATE TABLE ref_departement (
    id_departement SERIAL PRIMARY KEY,
    code_departement VARCHAR(10) NOT NULL UNIQUE,
    libelle VARCHAR(100) NOT NULL,
    est_actif BOOLEAN DEFAULT TRUE
);

CREATE TABLE ref_service (
    id_service SERIAL PRIMARY KEY,
    code_service VARCHAR(10) NOT NULL,
    libelle VARCHAR(100) NOT NULL,
    id_departement INT NOT NULL REFERENCES ref_departement(id_departement) ON DELETE CASCADE,
    est_actif BOOLEAN DEFAULT TRUE,
    CONSTRAINT uk_service_departement UNIQUE (code_service, id_departement),
    CONSTRAINT uq_ref_service_id_departement UNIQUE (id_service, id_departement)
);


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
    soumis_isi_5 BOOLEAN NOT NULL CHECK (soumis_isi_5 = FALSE),
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

-- 10. Calendriers Fériés
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


-- Index complémentaires
CREATE INDEX idx_ref_site_filiale_rattachement ON ref_site(id_filiale_rattachement);
CREATE INDEX idx_ref_service_departement ON ref_service(id_departement);
CREATE INDEX idx_site_calendrier_ferie_ferie ON site_calendrier_ferie(id_ferie);

-- =====================================================================
-- DONNÉES DE RÉFÉRENCE DE BASE (INITIAL SEEDS DES ARCHIVES)
-- =====================================================================

-- Filiales
INSERT INTO ref_filiale (id_filiale, code_filiale, raison_sociale, nif, stat, adresse_siege, rcs, telephone, email) VALUES
(1, 'GLX', 'GALAXAM Logistics', '3 000 019 43 4', '52 296 11 2001 0 00056', '3e étage, Tour Forello, Tanjombato', '2001B66', '+261 32 12 131 01', 'daf@galaxam.com');
SELECT setval('ref_filiale_id_filiale_seq', (SELECT GREATEST(MAX(id_filiale), 1) FROM ref_filiale));

-- Banques
INSERT INTO ref_banque (code_banque, nom_banque, code_swift) VALUES
('00009', 'Bank of Africa Madagascar', 'AFRIMGMG'),
('00005', 'BNI Madagascar', 'CLMDMGMG'),
('00008', 'BRED Madagasikara (ex-BFV)', 'BFAVMGMG');
SELECT setval('ref_banque_id_banque_seq', (SELECT GREATEST(MAX(id_banque), 3) FROM ref_banque));

-- Sites
INSERT INTO ref_site (id_site, code_site, libelle, id_filiale_rattachement, horaire_continu, heure_debut_matin, heure_fin_matin, heure_debut_apres_midi, heure_fin_apres_midi, plancher_arrivee_actif, heure_entree_minimale_valorisee) VALUES
(1, 'TNR', 'Antananarivo - Siège', 1, TRUE, '08:00', '12:30', '13:30', '17:00', FALSE, '07:30'),
(2, 'TMM', 'Toamasina - Agence', 1, FALSE, '08:00', '12:00', '14:00', '18:00', FALSE, '07:30');
SELECT setval('ref_site_id_site_seq', (SELECT GREATEST(MAX(id_site), 2) FROM ref_site));

-- Départements
INSERT INTO ref_departement (id_departement, code_departement, libelle) VALUES
(1, 'LOG', 'Logistics');
SELECT setval('ref_departement_id_departement_seq', (SELECT GREATEST(MAX(id_departement), 1) FROM ref_departement));

-- Services
INSERT INTO ref_service (id_service, code_service, libelle, id_departement) VALUES
(1, 'ADM', 'Administration & Comptabilité', 1),
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
(4, 'TEMPORAIRE', 'Contrat d Engagement Temporaire', TRUE),
(5, 'STAGE', 'Convention / Contrat de Stage', TRUE);
SELECT setval('ref_type_contrat_id_type_contrat_seq', (SELECT GREATEST(MAX(id_type_contrat), 5) FROM ref_type_contrat));

-- Types d'Intervenants
INSERT INTO ref_type_intervenant (id_type_intervenant, code, libelle, soumis_irsa, soumis_isi_5, soumis_cnaps, soumis_sante) VALUES
(1, 'PERMANENT', 'Salarié Permanent', TRUE, FALSE, TRUE, TRUE),
(2, 'CONSULTANT', 'Consultant indépendant (hors paie)', FALSE, FALSE, FALSE, FALSE),
(3, 'TEMPORAIRE', 'Travailleur temporaire salarié', TRUE, FALSE, TRUE, TRUE),
(4, 'STAGIAIRE', 'Stagiaire', FALSE, FALSE, FALSE, FALSE),
(5, 'CONSULTANT_SALARIE', 'Consultant salarié', TRUE, FALSE, TRUE, TRUE);
SELECT setval('ref_type_intervenant_id_type_intervenant_seq', (SELECT GREATEST(MAX(id_type_intervenant), 5) FROM ref_type_intervenant));

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
('LICENCIEMENT_ECONOMIQUE', 'Licenciement pour motif économique'),
('CESSATION_ACTIVITE', 'Licenciement pour cessation d activité'),
('FIN_CONTRAT', 'Fin du contrat (CDD / Stage)'),
('RUPTURE_ESSAI', 'Rupture de période d essai'),
('RETRAITE', 'Départ en retraite'),
('DEPART_RETRAITE', 'Départ en retraite (événement carrière)'),
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

-- Calendrier officiel des jours fériés légaux à Madagascar (2025 - 2026)
INSERT INTO ref_calendrier_ferie (id_ferie, date_ferie, libelle, est_chome, est_national, est_actif) VALUES
(1,  '2025-12-25', 'Noël',                                   TRUE, TRUE, TRUE),
(2,  '2026-01-01', 'Nouvel An',                              TRUE, TRUE, TRUE),
(3,  '2026-03-08', 'Journée Internationale de la Femme',     TRUE, TRUE, TRUE),
(4,  '2026-03-29', 'Commémoration des Événements de 1947',   TRUE, TRUE, TRUE),
(5,  '2026-03-31', 'Fête de l''Id al-Fitr (Fin du Ramadan)', TRUE, TRUE, TRUE),
(6,  '2026-04-06', 'Lundi de Pâques',                       TRUE, TRUE, TRUE),
(7,  '2026-05-01', 'Fête du Travail',                        TRUE, TRUE, TRUE),
(8,  '2026-05-14', 'Ascension',                              TRUE, TRUE, TRUE),
(9,  '2026-05-25', 'Lundi de Pentecôte',                     TRUE, TRUE, TRUE),
(10, '2026-06-07', 'Fête de l''Id al-Adha (Tabaski)',        TRUE, TRUE, TRUE),
(11, '2026-06-26', 'Fête Nationale de l''Indépendance',      TRUE, TRUE, TRUE),
(12, '2026-08-15', 'Assomption',                             TRUE, TRUE, TRUE),
(13, '2026-11-01', 'Toussaint',                              TRUE, TRUE, TRUE),
(14, '2026-12-25', 'Noël',                                   TRUE, TRUE, TRUE);

SELECT setval('ref_calendrier_ferie_id_ferie_seq', (SELECT GREATEST(MAX(id_ferie), 14) FROM ref_calendrier_ferie));
