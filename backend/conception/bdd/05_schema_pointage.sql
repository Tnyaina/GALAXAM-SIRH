-- =====================================================================
-- GALAXAM SIRH — MODULE 05 : POINTAGE & SUIVI DU TEMPS DE TRAVAIL
-- Script canonique final (Zéro ALTER TABLE, Zéro OWNER, Zéro public.)
-- Schéma purifié : pointage_indemnite_jour source de vérité unique
-- Sources : 26-08-05-sp0-ref.sql, 26-08-20-sp3-pointage.sql,
--           26-09-16-sp8-indexes-foreign-keys.sql, 26-09-19-sp11-workflow-pointage-alter.sql
-- =====================================================================

-- 1. Nettoyage préalable des vues et tables (ordre inverse des dépendances)
DROP VIEW IF EXISTS v_synthese_pointage_mensuelle CASCADE;
DROP VIEW IF EXISTS v_hs_mensuelle CASCADE;
DROP VIEW IF EXISTS v_pointage_semaine CASCADE;
DROP VIEW IF EXISTS v_pointage_jour_cpl CASCADE;
DROP VIEW IF EXISTS v_pointage_indemnites CASCADE;

DROP TABLE IF EXISTS pointage_cumul_indemnite CASCADE;
DROP TABLE IF EXISTS pointage_cumul_periode CASCADE;
DROP TABLE IF EXISTS pointage_semaine_calcule CASCADE;
DROP TABLE IF EXISTS pointage_indemnite_jour CASCADE;
DROP TABLE IF EXISTS pointage_jour CASCADE;
DROP TABLE IF EXISTS employe_indemnite_derogation CASCADE;
DROP TABLE IF EXISTS ref_tolerance_arrivee_pointage CASCADE;
DROP TABLE IF EXISTS site_indemnite_config CASCADE;
DROP TABLE IF EXISTS ref_indemnite CASCADE;
DROP TABLE IF EXISTS ref_statut_pointage_jour CASCADE;
DROP TABLE IF EXISTS periode_paie CASCADE;
DROP TABLE IF EXISTS ref_statut_periode_paie CASCADE;

-- 2. Périodes de Paie & Statuts
CREATE TABLE ref_statut_periode_paie (
    id_statut_periode SERIAL PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    libelle VARCHAR(100) NOT NULL
);

CREATE TABLE periode_paie (
    id_periode_paie SERIAL PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    libelle VARCHAR(100) NOT NULL,
    date_debut DATE NOT NULL,
    date_fin DATE NOT NULL,
    id_statut_periode INTEGER NOT NULL REFERENCES ref_statut_periode_paie(id_statut_periode),
    date_transmission TIMESTAMP,
    transmis_par INTEGER REFERENCES utilisateur(id_utilisateur),
    date_validation TIMESTAMP,
    valide_par INTEGER REFERENCES utilisateur(id_utilisateur),
    motif_rejet TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_periode_dates CHECK (date_fin > date_debut)
);

CREATE INDEX idx_periode_paie_statut ON periode_paie(id_statut_periode);
CREATE INDEX idx_periode_paie_valide_par ON periode_paie(valide_par);

-- 3. Statuts Journaliers & Indemnités de Référence
CREATE TABLE ref_statut_pointage_jour (
    id_statut_jour SERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    libelle VARCHAR(100) NOT NULL,
    decompte_absence BOOLEAN NOT NULL DEFAULT FALSE,
    necessite_saisie BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE ref_indemnite (
    id_indemnite SERIAL PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    libelle VARCHAR(100) NOT NULL,
    imposable_irsa BOOLEAN DEFAULT FALSE,
    fractionnable BOOLEAN DEFAULT FALSE,
    est_actif BOOLEAN DEFAULT TRUE
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

CREATE INDEX idx_site_indemnite_actif ON site_indemnite_config(id_site, id_indemnite) WHERE date_fin IS NULL AND est_actif IS TRUE;
CREATE INDEX idx_site_indemnite_config_ind ON site_indemnite_config(id_indemnite);

CREATE TABLE ref_tolerance_arrivee_pointage (
    id_tolerance SERIAL PRIMARY KEY,
    id_site INTEGER NOT NULL UNIQUE REFERENCES ref_site(id_site),
    heure_plafond TIME NOT NULL
);

CREATE TABLE employe_indemnite_derogation (
    id_derogation SERIAL PRIMARY KEY,
    id_employe INTEGER NOT NULL REFERENCES employe(id_employe) ON DELETE CASCADE,
    id_indemnite INTEGER NOT NULL REFERENCES ref_indemnite(id_indemnite),
    beneficie BOOLEAN NOT NULL,
    motif VARCHAR(200),
    date_debut DATE NOT NULL DEFAULT CURRENT_DATE,
    date_fin DATE,
    created_by INTEGER REFERENCES utilisateur(id_utilisateur),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_derogation_employe ON employe_indemnite_derogation(id_employe, id_indemnite) WHERE date_fin IS NULL;
CREATE INDEX idx_employe_indemnite_derog_created_by ON employe_indemnite_derogation(created_by);
CREATE INDEX idx_employe_indemnite_derog_ind ON employe_indemnite_derogation(id_indemnite);

-- 4. Pointage Journalier (Purifié : Zéro colonne obsolète, montant_total_indemnites dénormalisé)
CREATE TABLE pointage_jour (
    id_pointage SERIAL PRIMARY KEY,
    id_employe INTEGER NOT NULL REFERENCES employe(id_employe) ON DELETE CASCADE,
    id_periode_paie INTEGER NOT NULL REFERENCES periode_paie(id_periode_paie),
    id_site INTEGER NOT NULL REFERENCES ref_site(id_site),
    date_pointage DATE NOT NULL,
    heure_entree_matin TIME,
    heure_sortie_matin TIME,
    heure_entree_am TIME,
    heure_sortie_am TIME,
    id_statut_jour INTEGER NOT NULL REFERENCES ref_statut_pointage_jour(id_statut_jour),
    heures_matin DECIMAL(4,2) DEFAULT 0,
    heures_am DECIMAL(4,2) DEFAULT 0,
    total_heures DECIMAL(4,2) DEFAULT 0,
    heures_nuit DECIMAL(4,2) NOT NULL DEFAULT 0,
    minutes_retard INTEGER DEFAULT 0,
    minutes_depart_anticipe INTEGER DEFAULT 0,
    utilise_vehicule_fonction_jour BOOLEAN DEFAULT FALSE,
    horaire_continu_jour BOOLEAN,
    montant_total_indemnites NUMERIC(12,2) DEFAULT 0,
    motif_ajustement TEXT,
    saisi_par INTEGER REFERENCES utilisateur(id_utilisateur),
    ajuste_par_daf BOOLEAN DEFAULT FALSE,
    ajuste_par INTEGER REFERENCES utilisateur(id_utilisateur),
    date_ajustement TIMESTAMP,
    id_demande_conge INTEGER REFERENCES demande_conge(id_demande_conge),
    genere_automatiquement BOOLEAN NOT NULL DEFAULT FALSE,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_pointage_employe_jour UNIQUE (id_employe, date_pointage)
);

CREATE INDEX idx_pointage_employe ON pointage_jour(id_employe);
CREATE INDEX idx_pointage_periode ON pointage_jour(id_periode_paie);
CREATE INDEX idx_pointage_date ON pointage_jour(date_pointage);
CREATE INDEX idx_pointage_statut ON pointage_jour(id_statut_jour);
CREATE INDEX idx_pointage_jour_site ON pointage_jour(id_site);
CREATE INDEX idx_pointage_jour_saisi_par ON pointage_jour(saisi_par);
CREATE INDEX idx_pointage_demande_conge ON pointage_jour(id_demande_conge) WHERE id_demande_conge IS NOT NULL;
CREATE INDEX idx_pointage_ajuste_daf ON pointage_jour(ajuste_par_daf) WHERE ajuste_par_daf = TRUE;

-- 5. Indemnités Journalières (Source Unique de Vérité)
CREATE TABLE pointage_indemnite_jour (
    id_pointage_indemnite SERIAL PRIMARY KEY,
    id_pointage INT NOT NULL REFERENCES pointage_jour(id_pointage) ON DELETE CASCADE,
    id_indemnite INT NOT NULL REFERENCES ref_indemnite(id_indemnite),
    fraction VARCHAR(20) NOT NULL DEFAULT 'JOUR_ENTIER',
    quantite NUMERIC(4,2) NOT NULL DEFAULT 1.0,
    montant_calcule NUMERIC(12,2) DEFAULT 0,
    accorde BOOLEAN NOT NULL DEFAULT TRUE,
    motif_ajustement TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_pointage_indemnite_fraction UNIQUE (id_pointage, id_indemnite, fraction)
);

CREATE INDEX idx_pointage_indemnite_ptg ON pointage_indemnite_jour(id_pointage);
CREATE INDEX idx_pointage_indemnite_ref ON pointage_indemnite_jour(id_indemnite);

-- 6. Tables de Synthèse & Clôture de Période (RG-PTG-14)
CREATE TABLE pointage_semaine_calcule (
    id_semaine_calcule SERIAL PRIMARY KEY,
    id_employe INTEGER NOT NULL REFERENCES employe(id_employe),
    id_periode_paie INTEGER NOT NULL REFERENCES periode_paie(id_periode_paie),
    debut_semaine DATE NOT NULL,
    nb_jours_troncon INTEGER NOT NULL,
    seuil_heures_troncon DECIMAL(5,2) NOT NULL,
    total_heures_travaillees DECIMAL(5,2) NOT NULL DEFAULT 0,
    heures_retard DECIMAL(5,2) NOT NULL DEFAULT 0,
    heures_hs_brutes DECIMAL(5,2) NOT NULL DEFAULT 0,
    heures_hs_nettes DECIMAL(5,2) NOT NULL DEFAULT 0,
    heures_deficit DECIMAL(5,2) NOT NULL DEFAULT 0,
    taux_hs_30_applique DECIMAL(5,2) NOT NULL DEFAULT 0,
    taux_hs_50_applique DECIMAL(5,2) NOT NULL DEFAULT 0,
    taux_nuit_applique DECIMAL(5,2) NOT NULL DEFAULT 0,
    taux_dimanche_applique DECIMAL(5,2) NOT NULL DEFAULT 0,
    taux_ferie_applique DECIMAL(5,2) NOT NULL DEFAULT 0,
    heures_hs_30 DECIMAL(5,2) NOT NULL DEFAULT 0,
    heures_hs_50 DECIMAL(5,2) NOT NULL DEFAULT 0,
    gele_par INTEGER REFERENCES utilisateur(id_utilisateur),
    gele_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_semaine_calcule UNIQUE (id_employe, id_periode_paie, debut_semaine)
);

CREATE INDEX idx_semaine_calcule_employe_periode ON pointage_semaine_calcule(id_employe, id_periode_paie);
CREATE INDEX idx_pointage_semaine_calcule_periode ON pointage_semaine_calcule(id_periode_paie);
CREATE INDEX idx_pointage_semaine_calcule_gele_par ON pointage_semaine_calcule(gele_par);

CREATE TABLE pointage_cumul_periode (
    id_cumul SERIAL PRIMARY KEY,
    id_employe INTEGER NOT NULL REFERENCES employe(id_employe),
    id_periode_paie INTEGER NOT NULL REFERENCES periode_paie(id_periode_paie),
    nb_jours_ouvrables INTEGER NOT NULL,
    nb_jours_presents INTEGER NOT NULL DEFAULT 0,
    nb_jours_absence INTEGER NOT NULL DEFAULT 0,
    nb_jours_conge INTEGER NOT NULL DEFAULT 0,
    nb_jours_ferie_chome INTEGER NOT NULL DEFAULT 0,
    nb_jours_ferie_travaille INTEGER NOT NULL DEFAULT 0,
    total_heures_travaillees DECIMAL(6,2) NOT NULL DEFAULT 0,
    heures_hs_30 DECIMAL(6,2) NOT NULL DEFAULT 0,
    heures_hs_50 DECIMAL(6,2) NOT NULL DEFAULT 0,
    heures_nuit_total DECIMAL(6,2) NOT NULL DEFAULT 0,
    heures_absence_retenues DECIMAL(6,2) NOT NULL DEFAULT 0,
    nb_jours_deplacement DECIMAL(5,2) NOT NULL DEFAULT 0,
    montant_deplacement DECIMAL(10,2) NOT NULL DEFAULT 0,
    nb_jours_repas INTEGER NOT NULL DEFAULT 0,
    montant_repas DECIMAL(10,2) NOT NULL DEFAULT 0,
    gele_par INTEGER REFERENCES utilisateur(id_utilisateur),
    gele_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_cumul_employe_periode UNIQUE (id_employe, id_periode_paie)
);

CREATE INDEX idx_cumul_periode_employe ON pointage_cumul_periode(id_employe, id_periode_paie);
CREATE INDEX idx_pointage_cumul_periode_periode ON pointage_cumul_periode(id_periode_paie);
CREATE INDEX idx_pointage_cumul_periode_gele_par ON pointage_cumul_periode(gele_par);

CREATE TABLE pointage_cumul_indemnite (
    id_cumul_indemnite SERIAL PRIMARY KEY,
    id_periode_paie INTEGER NOT NULL REFERENCES periode_paie(id_periode_paie) ON DELETE CASCADE,
    id_employe INTEGER NOT NULL REFERENCES employe(id_employe) ON DELETE CASCADE,
    id_indemnite INTEGER NOT NULL REFERENCES ref_indemnite(id_indemnite),
    quantite_totale DECIMAL(6,2) NOT NULL DEFAULT 0,
    montant_total DECIMAL(12,2) NOT NULL DEFAULT 0,
    gele_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_cumul_indemnite UNIQUE (id_periode_paie, id_employe, id_indemnite)
);

CREATE INDEX idx_cumul_indemnite_periode ON pointage_cumul_indemnite(id_periode_paie, id_employe);
CREATE INDEX idx_pointage_cumul_indemnite_emp ON pointage_cumul_indemnite(id_employe);
CREATE INDEX idx_pointage_cumul_indemnite_ind ON pointage_cumul_indemnite(id_indemnite);

-- =====================================================================
-- FONCTIONS & VUES DU MODULE POINTAGE
-- =====================================================================

CREATE OR REPLACE FUNCTION fn_jours_ouvrables(
    p_date_debut DATE, p_date_fin DATE, p_id_site INT
) RETURNS INTEGER AS $$
DECLARE
    v_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO v_count
    FROM generate_series(p_date_debut, p_date_fin, '1 day') AS d
    WHERE EXTRACT(ISODOW FROM d) < 6
      AND NOT EXISTS (
          SELECT 1 FROM site_calendrier_ferie scf
          JOIN ref_calendrier_ferie f ON scf.id_ferie = f.id_ferie
          WHERE scf.id_site = p_id_site AND f.date_ferie = d AND f.est_chome = TRUE
      );
    RETURN v_count;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

CREATE OR REPLACE VIEW v_pointage_indemnites AS
SELECT 
    pij.id_pointage_indemnite,
    pij.id_pointage,
    pij.id_indemnite,
    ri.code AS code_indemnite,
    ri.libelle AS libelle_indemnite,
    pij.fraction,
    pij.quantite,
    pij.montant_calcule,
    pij.accorde,
    pij.motif_ajustement
FROM pointage_indemnite_jour pij
JOIN ref_indemnite ri ON pij.id_indemnite = ri.id_indemnite;

-- =====================================================================
-- DONNÉES DE RÉFÉRENCE DE BASE (INITIAL SEEDS DES ARCHIVES)
-- =====================================================================

-- Statuts Période de Paie
INSERT INTO ref_statut_periode_paie (id_statut_periode, code, libelle) VALUES
(1, 'BROUILLON', 'Période créée, non ouverte'),
(2, 'EN_COURS', 'Saisie en cours'),
(3, 'A_VALIDER', 'Terminée, en attente de validation DAF'),
(4, 'TRANSMIS_DAF', 'Transmise à la DAF'),
(5, 'VALIDEE', 'Validée — verrouillée pour saisie'),
(6, 'CLOTUREE', 'Clôturée — consommée par la Paie, immuable');
SELECT setval('ref_statut_periode_paie_id_statut_periode_seq', (SELECT GREATEST(MAX(id_statut_periode), 6) FROM ref_statut_periode_paie));

-- Statuts Pointage Journalier
INSERT INTO ref_statut_pointage_jour (id_statut_jour, code, libelle, decompte_absence, necessite_saisie) VALUES
(1, 'PRESENT', 'Présent', FALSE, TRUE),
(2, 'ABSENCE_NON_JUSTIFIEE', 'Absence non justifiée', TRUE, FALSE),
(3, 'ANOMALIE_SAISIE', 'Pointage incomplet (anomalie)', FALSE, TRUE),
(4, 'ABSENCE_JUSTIFIEE', 'Absence justifiée (avec motif)', TRUE, FALSE),
(5, 'CONGE', 'En congé (matérialisé depuis demande_conge)', FALSE, FALSE),
(6, 'FERIE_TRAVAILLE', 'Jour férié travaillé', FALSE, TRUE),
(7, 'FERIE_CHOME', 'Jour férié chômé', FALSE, FALSE),
(8, 'SAMEDI_TRAVAILLE', 'Samedi travaillé', FALSE, TRUE),
(9, 'DIMANCHE_TRAVAILLE', 'Dimanche travaillé', FALSE, TRUE),
(10, 'DISPENSE_POINTAGE', 'Poste autonome — dispense de pointage', FALSE, FALSE);
SELECT setval('ref_statut_pointage_jour_id_statut_jour_seq', (SELECT GREATEST(MAX(id_statut_jour), 10) FROM ref_statut_pointage_jour));

-- Tolérances d'Arrivée
INSERT INTO ref_tolerance_arrivee_pointage (id_site, heure_plafond) VALUES
(1, '07:30'),
(2, '07:30');

-- Indemnités de Référence
INSERT INTO ref_indemnite (id_indemnite, code, libelle, imposable_irsa, fractionnable) VALUES
(1, 'DEPLACEMENT', 'Indemnité de Déplacement', FALSE, TRUE),
(2, 'REPAS', 'Indemnité de Repas', FALSE, FALSE);
SELECT setval('ref_indemnite_id_indemnite_seq', (SELECT GREATEST(MAX(id_indemnite), 2) FROM ref_indemnite));

-- Configurations d'Indemnités par Site
INSERT INTO site_indemnite_config (id_site_indemnite, id_site, id_indemnite, periodicite, montant_reference, date_debut) VALUES
(1, 1, 1, 'MENSUEL', 31200.00, '2026-01-01'),
(2, 2, 1, 'MENSUEL', 53200.00, '2026-01-01'),
(3, 1, 2, 'JOURNALIER', 2000.00, '2026-01-01'),
(4, 2, 2, 'JOURNALIER', 0.00, '2026-01-01');
SELECT setval('site_indemnite_config_id_site_indemnite_seq', (SELECT GREATEST(MAX(id_site_indemnite), 4) FROM site_indemnite_config));
