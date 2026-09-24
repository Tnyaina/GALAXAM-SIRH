-- =====================================================================
-- GALAXAM SIRH — MODULE 04 : CONGÉS & ABSENCES
-- Script canonique final (Zéro ALTER TABLE, Zéro OWNER, Zéro public.)
-- Sources : 26-08-13-sp2-conges.sql, 26-09-16-sp8-indexes-foreign-keys.sql
-- =====================================================================

-- 1. Nettoyage préalable des vues et tables (ordre inverse des dépendances)
DROP VIEW IF EXISTS v_demande_conge_cpl CASCADE;
DROP VIEW IF EXISTS v_pool_conge_annuel_consommation CASCADE;
DROP VIEW IF EXISTS v_solde_conge_employe CASCADE;

DROP TABLE IF EXISTS conge_taux_applicable CASCADE;
DROP TABLE IF EXISTS conge_periode_reference CASCADE;
DROP TABLE IF EXISTS mouvement_solde_conge CASCADE;
DROP TABLE IF EXISTS demande_conge CASCADE;
DROP TABLE IF EXISTS ref_statut_demande_conge CASCADE;
DROP TABLE IF EXISTS ref_motif_evenement_familial CASCADE;
DROP TABLE IF EXISTS ref_type_conge CASCADE;
DROP TABLE IF EXISTS ref_pool_conge_annuel CASCADE;

-- 2. Référentiels des Congés & Quotas Partagés
CREATE TABLE ref_pool_conge_annuel (
    id_pool SERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    libelle VARCHAR(100) NOT NULL,
    plafond_jours_an NUMERIC(5,1) NOT NULL
);

CREATE TABLE ref_type_conge (
    id_type_conge SERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    libelle VARCHAR(100) NOT NULL,
    est_paye BOOLEAN DEFAULT TRUE,
    decompte_solde_acquis BOOLEAN DEFAULT TRUE,
    exige_anciennete_min_mois INT DEFAULT 0,
    duree_fixe_jours NUMERIC(5,1),
    plafond_jours_episode NUMERIC(5,1),
    id_type_conge_bascule INT REFERENCES ref_type_conge(id_type_conge),
    id_pool_conge_annuel INT REFERENCES ref_pool_conge_annuel(id_pool),
    soumis_delai_preavis BOOLEAN DEFAULT FALSE,
    suspend_acquisition_solde BOOLEAN DEFAULT FALSE,
    exige_justificatif BOOLEAN DEFAULT FALSE,
    est_actif BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ref_type_conge_pool ON ref_type_conge(id_pool_conge_annuel);
CREATE INDEX idx_ref_type_conge_bascule ON ref_type_conge(id_type_conge_bascule);

CREATE TABLE ref_motif_evenement_familial (
    id_motif SERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    libelle VARCHAR(100) NOT NULL,
    nb_jours_autorises NUMERIC(4,1) NOT NULL,
    justificatif_requis BOOLEAN DEFAULT TRUE,
    est_actif BOOLEAN DEFAULT TRUE
);

CREATE TABLE ref_statut_demande_conge (
    id_statut_demande_conge SERIAL PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    libelle VARCHAR(100) NOT NULL
);

-- 3. Demandes de Congé & Circuit de Validation
CREATE TABLE demande_conge (
    id_demande_conge SERIAL PRIMARY KEY,
    id_employe INT NOT NULL REFERENCES employe(id_employe),
    id_type_conge INT NOT NULL REFERENCES ref_type_conge(id_type_conge),
    id_statut_demande_conge INT NOT NULL REFERENCES ref_statut_demande_conge(id_statut_demande_conge),
    id_motif_evenement_familial INT REFERENCES ref_motif_evenement_familial(id_motif),
    date_debut DATE NOT NULL,
    date_fin DATE NOT NULL,
    date_demande DATE NOT NULL DEFAULT CURRENT_DATE,
    nb_jours_decomptes NUMERIC(5,2) NOT NULL,
    derogation_solde_negatif BOOLEAN DEFAULT FALSE,
    derogation_delai_preavis BOOLEAN DEFAULT FALSE,
    derogation_continuite_service BOOLEAN DEFAULT FALSE,
    extension_pathologique_maternite BOOLEAN DEFAULT FALSE,
    est_demi_journee BOOLEAN DEFAULT FALSE,
    moment_demi_journee VARCHAR(10) CHECK (moment_demi_journee IN ('MATIN', 'APRES_MIDI')),
    est_remunere BOOLEAN DEFAULT NULL,
    justificatif_fourni BOOLEAN DEFAULT FALSE,
    commentaire TEXT,
    saisi_par INT REFERENCES utilisateur(id_utilisateur),
    valide_par INT REFERENCES utilisateur(id_utilisateur),
    date_validation TIMESTAMP,
    motif_refus TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_demande_conge_dates CHECK (date_fin >= date_debut),
    CONSTRAINT chk_demande_conge_demi_journee CHECK (
        (est_demi_journee IS FALSE AND moment_demi_journee IS NULL) OR
        (est_demi_journee IS TRUE AND date_debut = date_fin AND moment_demi_journee IN ('MATIN', 'APRES_MIDI'))
    )
);

CREATE INDEX idx_demande_conge_employe ON demande_conge(id_employe);
CREATE INDEX idx_demande_conge_statut ON demande_conge(id_statut_demande_conge);
CREATE INDEX idx_demande_conge_periode ON demande_conge(date_debut, date_fin);
CREATE INDEX idx_demande_conge_motif ON demande_conge(id_motif_evenement_familial);
CREATE INDEX idx_demande_conge_type_conge ON demande_conge(id_type_conge);
CREATE INDEX idx_demande_conge_saisi_par ON demande_conge(saisi_par);
CREATE INDEX idx_demande_conge_valide_par ON demande_conge(valide_par);

-- 4. Journal des Mouvements de Solde de Congé
CREATE TABLE mouvement_solde_conge (
    id_mouvement SERIAL PRIMARY KEY,
    id_employe INT NOT NULL REFERENCES employe(id_employe),
    type_mouvement VARCHAR(20) NOT NULL CHECK (type_mouvement IN ('ACQUISITION','BONIFICATION','PRISE','AJUSTEMENT','ANNULATION','EXPIRATION')),
    nb_jours NUMERIC(5,2) NOT NULL,
    id_demande_conge INT REFERENCES demande_conge(id_demande_conge) ON DELETE SET NULL,
    date_mouvement DATE NOT NULL DEFAULT CURRENT_DATE,
    commentaire TEXT,
    created_by INT REFERENCES utilisateur(id_utilisateur),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_mouvement_solde_employe ON mouvement_solde_conge(id_employe);
CREATE INDEX idx_mouvement_solde_demande ON mouvement_solde_conge(id_demande_conge);
CREATE INDEX idx_mouvement_solde_date ON mouvement_solde_conge(date_mouvement);
CREATE INDEX idx_mouvement_solde_conge_created_by ON mouvement_solde_conge(created_by);
CREATE UNIQUE INDEX idx_uniq_mouvement_prise_demande ON mouvement_solde_conge(id_demande_conge, type_mouvement) WHERE type_mouvement = 'PRISE' AND id_demande_conge IS NOT NULL;
CREATE UNIQUE INDEX idx_uniq_mouvement_annulation_demande ON mouvement_solde_conge(id_demande_conge, type_mouvement) WHERE type_mouvement = 'ANNULATION' AND id_demande_conge IS NOT NULL;

-- 5. Périodes de Référence Bornées & Taux Applicables (TCONGE Canonical)
CREATE TABLE conge_periode_reference (
    id_periode_reference SERIAL PRIMARY KEY,
    id_employe INT NOT NULL REFERENCES employe(id_employe),
    date_debut DATE NOT NULL,
    date_fin DATE NOT NULL,
    statut VARCHAR(30) NOT NULL DEFAULT 'OUVERTE', -- 'OUVERTE', 'CLOTUREE', 'ARCHIVEE'
    mode_reprise VARCHAR(30) NOT NULL DEFAULT 'FULL_HISTORY', -- 'FULL_HISTORY', 'AGGREGATED_OPENING', 'MINIMAL_OPENING', 'NEW_HIRE'
    date_cutover DATE,
    cumul_droits_ouverture NUMERIC(8,4),
    cumul_valorisation_ouverture NUMERIC(12,2),
    taux_calcule_cloture NUMERIC(12,2),
    cumul_droits_cloture NUMERIC(8,4),
    cumul_valorisation_cloture NUMERIC(12,2),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by INT REFERENCES utilisateur(id_utilisateur),
    CONSTRAINT chk_periode_dates CHECK (date_fin >= date_debut),
    CONSTRAINT chk_statut_ref CHECK (statut IN ('OUVERTE', 'CLOTUREE', 'ARCHIVEE')),
    CONSTRAINT chk_mode_reprise CHECK (mode_reprise IN ('FULL_HISTORY', 'AGGREGATED_OPENING', 'MINIMAL_OPENING', 'NEW_HIRE'))
);
CREATE INDEX idx_conge_ref_emp_dates ON conge_periode_reference(id_employe, date_debut, date_fin);

CREATE TABLE conge_taux_applicable (
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
    CONSTRAINT chk_source_provenance CHECK (source_provenance IN ('KAJY_MIGRATION', 'CALCUL_PERIODE_REFERENCE', 'INITIALISATION_BASE_30', 'REPRISE_CUTOVER'))
);
CREATE INDEX idx_taux_app_emp_dates ON conge_taux_applicable(id_employe, date_effet, date_fin);

-- =====================================================================
-- VUES DU MODULE CONGÉS
-- =====================================================================

CREATE OR REPLACE VIEW v_solde_conge_employe AS
SELECT
    id_employe,
    COALESCE(SUM(nb_jours), 0) AS solde_actuel,
    COALESCE(SUM(nb_jours) FILTER (WHERE type_mouvement = 'ACQUISITION'), 0) AS total_acquis,
    COALESCE(SUM(nb_jours) FILTER (WHERE type_mouvement = 'BONIFICATION'), 0) AS total_bonification,
    COALESCE(SUM(-nb_jours) FILTER (WHERE type_mouvement = 'PRISE'), 0) AS total_pris,
    COALESCE(SUM(-nb_jours) FILTER (WHERE type_mouvement = 'EXPIRATION'), 0) AS total_expire,
    MAX(date_mouvement) FILTER (WHERE type_mouvement = 'ACQUISITION') AS derniere_acquisition
FROM mouvement_solde_conge
GROUP BY id_employe;

CREATE OR REPLACE VIEW v_pool_conge_annuel_consommation AS
SELECT
    d.id_employe,
    tc.id_pool_conge_annuel AS id_pool,
    EXTRACT(YEAR FROM d.date_debut)::INT AS annee,
    SUM(CASE WHEN sd.code = 'VALIDE' THEN d.nb_jours_decomptes ELSE 0 END) AS jours_consommes,
    SUM(CASE WHEN sd.code = 'EN_ATTENTE' THEN d.nb_jours_decomptes ELSE 0 END) AS jours_en_attente
FROM demande_conge d
JOIN ref_type_conge tc ON tc.id_type_conge = d.id_type_conge
JOIN ref_statut_demande_conge sd ON sd.id_statut_demande_conge = d.id_statut_demande_conge
WHERE tc.id_pool_conge_annuel IS NOT NULL
  AND sd.code IN ('VALIDE', 'EN_ATTENTE')
GROUP BY d.id_employe, tc.id_pool_conge_annuel, EXTRACT(YEAR FROM d.date_debut);

CREATE OR REPLACE VIEW v_demande_conge_cpl AS
SELECT
    d.id_demande_conge,
    d.id_employe,
    e.matricule,
    e.nom,
    e.prenom,
    d.id_type_conge,
    tc.code AS code_type_conge,
    tc.libelle AS libelle_type_conge,
    COALESCE(d.est_remunere, tc.est_paye) AS est_paye,
    d.est_remunere,
    tc.suspend_acquisition_solde,
    d.id_motif_evenement_familial,
    m.code AS code_motif_evenement,
    m.libelle AS libelle_motif_evenement,
    d.id_statut_demande_conge,
    sd.code AS code_statut_demande,
    sd.libelle AS libelle_statut_demande,
    d.date_debut,
    d.date_fin,
    d.date_demande,
    d.nb_jours_decomptes,
    d.derogation_solde_negatif,
    d.derogation_delai_preavis,
    d.extension_pathologique_maternite,
    d.est_demi_journee,
    d.moment_demi_journee,
    d.justificatif_fourni,
    d.commentaire,
    d.saisi_par,
    us.identifiant AS saisi_par_identifiant,
    d.valide_par,
    uv.identifiant AS valide_par_identifiant,
    d.date_validation,
    d.motif_refus,
    d.created_at,
    d.updated_at
FROM demande_conge d
JOIN employe e ON e.id_employe = d.id_employe
JOIN ref_type_conge tc ON tc.id_type_conge = d.id_type_conge
JOIN ref_statut_demande_conge sd ON sd.id_statut_demande_conge = d.id_statut_demande_conge
LEFT JOIN ref_motif_evenement_familial m ON m.id_motif = d.id_motif_evenement_familial
LEFT JOIN utilisateur us ON us.id_utilisateur = d.saisi_par
LEFT JOIN utilisateur uv ON uv.id_utilisateur = d.valide_par;

-- =====================================================================
-- DONNÉES DE RÉFÉRENCE DE BASE (INITIAL SEEDS DES ARCHIVES)
-- =====================================================================

-- Pool partagé "permissions exceptionnelles"
INSERT INTO ref_pool_conge_annuel (id_pool, code, libelle, plafond_jours_an) VALUES
(1, 'PERMISSIONS_FAMILIALES', 'Permissions exceptionnelles pour événements familiaux (Art. 88)', 10);
SELECT setval('ref_pool_conge_annuel_id_pool_seq', (SELECT GREATEST(MAX(id_pool), 1) FROM ref_pool_conge_annuel));

-- Types de congé
INSERT INTO ref_type_conge (id_type_conge, code, libelle, est_paye, decompte_solde_acquis, exige_anciennete_min_mois, duree_fixe_jours, plafond_jours_episode, exige_justificatif, soumis_delai_preavis, suspend_acquisition_solde) VALUES
(1, 'MALADIE_SANS_SOLDE', 'Congé maladie au-delà du maintien de salaire (prise en charge CNaPS)', FALSE, FALSE, 0, NULL, 150, TRUE, FALSE, TRUE);

INSERT INTO ref_type_conge (id_type_conge, code, libelle, est_paye, decompte_solde_acquis, exige_anciennete_min_mois, duree_fixe_jours, plafond_jours_episode, id_type_conge_bascule, exige_justificatif, soumis_delai_preavis, suspend_acquisition_solde) VALUES
(2, 'MALADIE', 'Congé maladie (maintien de salaire)', TRUE, FALSE, 0, NULL, 30, 1, TRUE, FALSE, FALSE);

INSERT INTO ref_type_conge (id_type_conge, code, libelle, est_paye, decompte_solde_acquis, exige_anciennete_min_mois, duree_fixe_jours, id_pool_conge_annuel, exige_justificatif, soumis_delai_preavis, suspend_acquisition_solde) VALUES
(3, 'EXCEPTIONNEL', 'Congé exceptionnel (événement familial)', TRUE, FALSE, 0, NULL, 1, TRUE, FALSE, FALSE),
(4, 'PATERNITE', 'Congé paternité', TRUE, FALSE, 0, 3, NULL, TRUE, FALSE, FALSE);

INSERT INTO ref_type_conge (id_type_conge, code, libelle, est_paye, decompte_solde_acquis, exige_anciennete_min_mois, duree_fixe_jours, exige_justificatif, soumis_delai_preavis, suspend_acquisition_solde) VALUES
(5, 'ANNUEL',     'Congé annuel payé',  TRUE,  TRUE,  12, NULL, FALSE, TRUE,  FALSE),
(6, 'MATERNITE',  'Congé maternité',    TRUE,  FALSE, 0,  98,   TRUE,  FALSE, FALSE),
(7, 'PERMISSION', 'Permission exceptionnelle autorisée', TRUE, FALSE, 0, NULL, TRUE, FALSE, FALSE),
(8, 'SABBATIQUE', 'Congé sabbatique',   FALSE, FALSE, 0,  NULL, FALSE, TRUE,  TRUE),
(9, 'FORMATION',  'Congé formation',    FALSE, FALSE, 0,  NULL, FALSE, TRUE,  TRUE),
(10,'SANS_SOLDE', 'Congé sans solde',   FALSE, FALSE, 0,  NULL, FALSE, FALSE, TRUE);

SELECT setval('ref_type_conge_id_type_conge_seq', (SELECT GREATEST(MAX(id_type_conge), 10) FROM ref_type_conge));

-- Motifs d'événements familiaux
INSERT INTO ref_motif_evenement_familial (id_motif, code, libelle, nb_jours_autorises, justificatif_requis) VALUES
(1, 'MARIAGE',              'Mariage du salarié',               3, TRUE),
(2, 'NAISSANCE',            'Naissance / baptême d un enfant',  2, TRUE),
(3, 'DECES_CONJOINT_ENFANT','Décès du conjoint ou d un enfant', 3, TRUE),
(4, 'DECES_PARENT',         'Décès d un parent ou beau-parent', 2, TRUE);
SELECT setval('ref_motif_evenement_familial_id_motif_seq', (SELECT GREATEST(MAX(id_motif), 4) FROM ref_motif_evenement_familial));

-- Statuts du circuit de demande
INSERT INTO ref_statut_demande_conge (id_statut_demande_conge, code, libelle) VALUES
(1, 'BROUILLON',  'Brouillon (saisie RH en cours)'),
(2, 'EN_ATTENTE', 'En attente de validation DAF'),
(3, 'VALIDE',     'Validée'),
(4, 'REFUSE',     'Refusée'),
(5, 'ANNULE',     'Annulée');
SELECT setval('ref_statut_demande_conge_id_statut_demande_conge_seq', (SELECT GREATEST(MAX(id_statut_demande_conge), 5) FROM ref_statut_demande_conge));
