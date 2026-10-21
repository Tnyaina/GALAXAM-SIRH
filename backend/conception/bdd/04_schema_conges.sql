-- =====================================================================
-- GALAXAM SIRH — MODULE 04 : CONGÉS & ABSENCES
-- Script canonique final (ALTER TABLE pour une FK interne différée, Zéro OWNER, Zéro public.)
-- Sources : 26-08-13-sp2-conges.sql, 26-09-16-sp8-indexes-foreign-keys.sql
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS btree_gist;

-- 1. Nettoyage préalable des vues et tables (ordre inverse des dépendances)
DROP VIEW IF EXISTS v_demande_conge_cpl CASCADE;
DROP VIEW IF EXISTS v_pool_conge_annuel_consommation CASCADE;
DROP VIEW IF EXISTS v_solde_conge_employe CASCADE;

DROP TABLE IF EXISTS conge_taux_applicable CASCADE;
DROP TABLE IF EXISTS conge_journal_droit CASCADE;
DROP TABLE IF EXISTS conge_periode_reference CASCADE;
DROP TABLE IF EXISTS conge_expiration_examen CASCADE;
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
    valeur_source NUMERIC(14,2),
    created_by INT REFERENCES utilisateur(id_utilisateur),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_mouvement_solde_employe ON mouvement_solde_conge(id_employe);
CREATE INDEX idx_mouvement_solde_demande ON mouvement_solde_conge(id_demande_conge);
CREATE INDEX idx_mouvement_solde_date ON mouvement_solde_conge(date_mouvement);
CREATE INDEX idx_mouvement_solde_conge_created_by ON mouvement_solde_conge(created_by);
CREATE UNIQUE INDEX idx_uniq_mouvement_prise_demande ON mouvement_solde_conge(id_demande_conge, type_mouvement) WHERE type_mouvement = 'PRISE' AND id_demande_conge IS NOT NULL;
CREATE UNIQUE INDEX idx_uniq_mouvement_annulation_demande ON mouvement_solde_conge(id_demande_conge, type_mouvement) WHERE type_mouvement = 'ANNULATION' AND id_demande_conge IS NOT NULL;
-- Une bonification d'ancienneté au plus par salarié et par année ; la contrainte
-- rend atomique la règle que le scheduler vérifie actuellement dans son ledger.
CREATE UNIQUE INDEX idx_uniq_mouvement_bonification_annee
    ON mouvement_solde_conge (id_employe, (EXTRACT(YEAR FROM date_mouvement)))
    WHERE type_mouvement = 'BONIFICATION';

-- Corrections DAF du reliquat de reprise : l'ancien taux et les bulletins restent historiques.
CREATE TABLE conge_cutover_correction (
    id_correction SERIAL PRIMARY KEY,
    id_employe INT NOT NULL REFERENCES employe(id_employe),
    id_mouvement_reprise INT NOT NULL REFERENCES mouvement_solde_conge(id_mouvement),
    id_mouvement_ajustement INT NOT NULL UNIQUE REFERENCES mouvement_solde_conge(id_mouvement),
    ancien_reliquat_jours NUMERIC(8,2) NOT NULL,
    nouveau_reliquat_jours NUMERIC(8,2) NOT NULL,
    ancien_reliquat_valeur NUMERIC(14,2) NOT NULL,
    nouveau_reliquat_valeur NUMERIC(14,2) NOT NULL,
    ancien_taux NUMERIC(14,6) NOT NULL,
    nouveau_taux NUMERIC(14,6) NOT NULL,
    regularisation_a_examiner NUMERIC(14,2) NOT NULL DEFAULT 0,
    motif TEXT NOT NULL CHECK (length(trim(motif)) > 0),
    corrige_par INT NOT NULL REFERENCES utilisateur(id_utilisateur),
    date_correction TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_cutover_correction_reliquat CHECK (nouveau_reliquat_jours >= 0 AND nouveau_reliquat_valeur >= 0)
);
CREATE INDEX idx_conge_cutover_correction_employe ON conge_cutover_correction(id_employe, date_correction);

-- Rectification DAF d'une valorisation mensuelle : les jours et le bulletin restent figés.
CREATE TABLE conge_valorisation_mensuelle_correction (
    id_correction SERIAL PRIMARY KEY,
    id_journal INT NOT NULL,
    id_mouvement_acquisition INT NOT NULL REFERENCES mouvement_solde_conge(id_mouvement),
    ancienne_valeur NUMERIC(20,2) NOT NULL,
    nouvelle_valeur NUMERIC(20,2) NOT NULL CHECK (nouvelle_valeur >= 0),
    motif TEXT NOT NULL CHECK (length(trim(motif)) > 0),
    corrige_par INT NOT NULL REFERENCES utilisateur(id_utilisateur),
    date_correction TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_conge_valorisation_correction_journal
    ON conge_valorisation_mensuelle_correction(id_journal, date_correction);

-- 4b. Décisions DAF relatives aux droits candidats à la prescription
CREATE TABLE conge_expiration_examen (
    id_examen SERIAL PRIMARY KEY,
    id_employe INT NOT NULL REFERENCES employe(id_employe),
    decision VARCHAR(20) NOT NULL CHECK (decision IN ('REPORTE', 'EXPIRE')),
    jours_examines NUMERIC(8,2) NOT NULL CHECK (jours_examines > 0),
    motif TEXT NOT NULL,
    planification_verifiee BOOLEAN NOT NULL DEFAULT FALSE,
    date_prochaine_revue DATE,
    id_mouvement_expiration INT REFERENCES mouvement_solde_conge(id_mouvement),
    decide_par INT NOT NULL REFERENCES utilisateur(id_utilisateur),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_conge_expiration_decision CHECK (
        (decision = 'REPORTE' AND date_prochaine_revue IS NOT NULL AND id_mouvement_expiration IS NULL)
        OR (decision = 'EXPIRE' AND date_prochaine_revue IS NULL AND id_mouvement_expiration IS NOT NULL AND planification_verifiee)
    )
);
CREATE INDEX idx_conge_expiration_examen_employe_date
    ON conge_expiration_examen(id_employe, created_at DESC, id_examen DESC);

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
    cumul_valorisation_ouverture NUMERIC(20,2),
    taux_calcule_cloture NUMERIC(20,2),
    cumul_droits_cloture NUMERIC(8,4),
    cumul_valorisation_cloture NUMERIC(20,2),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by INT REFERENCES utilisateur(id_utilisateur),
    CONSTRAINT chk_periode_dates CHECK (date_fin >= date_debut),
    CONSTRAINT chk_statut_ref CHECK (statut IN ('OUVERTE', 'CLOTUREE', 'ARCHIVEE')),
    CONSTRAINT chk_mode_reprise CHECK (mode_reprise IN ('FULL_HISTORY', 'AGGREGATED_OPENING', 'MINIMAL_OPENING', 'NEW_HIRE')),
    CONSTRAINT uk_conge_reference_temporal_overlap EXCLUDE USING gist (
        id_employe WITH =,
        daterange(date_debut, date_fin, '[]') WITH &&
    )
);
CREATE INDEX idx_conge_ref_emp_dates ON conge_periode_reference(id_employe, date_debut, date_fin);

-- Grand-Livre de Valorisation des Droits à Congés
CREATE TABLE conge_journal_droit (
    id_journal SERIAL PRIMARY KEY,
    id_employe INT NOT NULL REFERENCES employe(id_employe),
    id_periode_paie INT,
    id_periode_reference INT REFERENCES conge_periode_reference(id_periode_reference),
    numero_mois_reference INT,
    annee INT NOT NULL,
    mois INT NOT NULL,
    salaire_base NUMERIC(20,2) NOT NULL,
    jours_travailles_formule NUMERIC(5,2) NOT NULL DEFAULT 30.0,
    jours_droit_acquis NUMERIC(6,4) NOT NULL DEFAULT 2.5000,
    base_valorisation NUMERIC(20,2) NOT NULL,
    valorisation_mensuelle NUMERIC(20,2) NOT NULL,
    cumul_jours_droit NUMERIC(8,4) NOT NULL,
    cumul_valorisation NUMERIC(20,2) NOT NULL,
    taux_journalier_conge NUMERIC(20,2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_conge_journal_emp_annee_mois UNIQUE (id_employe, annee, mois)
);

CREATE INDEX idx_conge_journal_emp ON conge_journal_droit(id_employe);
CREATE INDEX idx_conge_journal_periode ON conge_journal_droit(id_periode_paie);
CREATE INDEX idx_conge_journal_ref ON conge_journal_droit(id_periode_reference);

ALTER TABLE conge_valorisation_mensuelle_correction
    ADD CONSTRAINT fk_conge_valorisation_correction_journal
    FOREIGN KEY (id_journal) REFERENCES conge_journal_droit(id_journal);

CREATE TABLE conge_taux_applicable (
    id_taux_applicable SERIAL PRIMARY KEY,
    id_employe INT NOT NULL REFERENCES employe(id_employe),
    taux_journalier NUMERIC(20,2) NOT NULL,
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
(1, 'BROUILLON',  'Brouillon'),
(2, 'EN_ATTENTE', 'En attente de validation'),
(3, 'VALIDE',     'Validée'),
(4, 'REFUSE',     'Refusée'),
(5, 'ANNULE',     'Annulée');
SELECT setval('ref_statut_demande_conge_id_statut_demande_conge_seq', (SELECT GREATEST(MAX(id_statut_demande_conge), 5) FROM ref_statut_demande_conge));

-- =====================================================================
-- REPRISE INITIALE / CUTOVER DE CONGÉS FICTIF (AU 25/12/2025)
-- Préparation du démarrage des cycles 2026 (Période 2026-01 dès le 26/12/2025)
-- =====================================================================

-- 1. Cycles de référence annuels 2026 ouverts avec report des droits acquis
INSERT INTO conge_periode_reference (id_periode_reference, id_employe, date_debut, date_fin, statut, mode_reprise, date_cutover, cumul_droits_ouverture, cumul_valorisation_ouverture) VALUES
(1, 1, '2026-01-01', '2026-12-31', 'OUVERTE', 'AGGREGATED_OPENING', '2025-12-25', 15.0000, 2500000.00),
(2, 2, '2026-01-01', '2026-12-31', 'OUVERTE', 'AGGREGATED_OPENING', '2025-12-25', 12.5000, 1458333.38);

SELECT setval('conge_periode_reference_id_periode_reference_seq', (SELECT GREATEST(MAX(id_periode_reference), 2) FROM conge_periode_reference));

-- 2. Taux journaliers initiaux applicables TCONGE (base salaire / 30)
INSERT INTO conge_taux_applicable (id_taux_applicable, id_employe, taux_journalier, date_effet, date_fin, source_provenance, id_periode_reference, commentaire) VALUES
(1, 1, 166666.67, '2025-12-25', NULL, 'REPRISE_CUTOVER', 1, 'Reprise cutover initial - Solde fictif arrêté au 25/12/2025'),
(2, 2, 116666.67, '2025-12-25', NULL, 'REPRISE_CUTOVER', 2, 'Reprise cutover initial - Solde fictif arrêté au 25/12/2025');

SELECT setval('conge_taux_applicable_id_taux_applicable_seq', (SELECT GREATEST(MAX(id_taux_applicable), 2) FROM conge_taux_applicable));

-- 3. Mouvements de reprise de solde consommable (format canonique [SOLDE_INITIAL])
INSERT INTO mouvement_solde_conge (id_mouvement, id_employe, type_mouvement, nb_jours, date_mouvement, commentaire, valeur_source) VALUES
(1, 1, 'AJUSTEMENT', 15.00, '2025-12-25', '[SOLDE_INITIAL] Reprise cutover - Solde initial au 2025-12-25 (exclut acquisition 2026-01)', 2500000.00),
(2, 2, 'AJUSTEMENT', 12.50, '2025-12-25', '[SOLDE_INITIAL] Reprise cutover - Solde initial au 2025-12-25 (exclut acquisition 2026-01)', 1458333.38);

SELECT setval('mouvement_solde_conge_id_mouvement_seq', (SELECT GREATEST(MAX(id_mouvement), 2) FROM mouvement_solde_conge));
