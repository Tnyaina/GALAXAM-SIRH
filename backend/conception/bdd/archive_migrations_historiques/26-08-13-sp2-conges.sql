-- =====================================================================
-- SCRIPT POSTGRESQL : SIRH GALAXAM - MODULE CONGÉS & ABSENCES (COMPLET)
-- Réf. conges.md + conges-cpl.md + Code du Travail malgache
-- (Loi n° 2003-044, Art. 13, 85, 86, 87, 88) + arbitrages métier confirmés.
--
-- Hors périmètre (arbitrage confirmé) :
--   - Pauses d'allaitement (non retenu)
--   - Co-financement CNaPS / IJM maternité (reporté au module Paie)
-- =====================================================================

-- ------------------------------------------
-- 1. NETTOYAGE PRÉALABLE (ordre inverse des dépendances)
-- ------------------------------------------
DROP VIEW IF EXISTS v_pool_conge_annuel_consommation CASCADE;
DROP VIEW IF EXISTS v_demande_conge_cpl CASCADE;
DROP VIEW IF EXISTS v_solde_conge_employe CASCADE;
DROP TABLE IF EXISTS mouvement_solde_conge CASCADE;
DROP TABLE IF EXISTS demande_conge CASCADE;
DROP TABLE IF EXISTS ref_statut_demande_conge CASCADE;
DROP TABLE IF EXISTS ref_motif_evenement_familial CASCADE;
DROP TABLE IF EXISTS ref_type_conge CASCADE;
DROP TABLE IF EXISTS ref_pool_conge_annuel CASCADE;

-- ------------------------------------------
-- 2. RÉFÉRENTIELS
-- ------------------------------------------

-- Pool annuel partagé : les "permissions exceptionnelles" (Art. 88) sont un
-- quota GLOBAL de 10j/an partagé entre EXCEPTIONNEL et PATERNITE (le congé
-- paternité fait légalement partie de ce même pool, pas un quota séparé).
CREATE TABLE ref_pool_conge_annuel (
    id_pool SERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,        -- PERMISSIONS_FAMILIALES
    libelle VARCHAR(100) NOT NULL,
    plafond_jours_an NUMERIC(5,1) NOT NULL
);

-- Typologie des congés (Art. 13/85/86/87/88).
CREATE TABLE ref_type_conge (
    id_type_conge SERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,              -- ANNUEL, EXCEPTIONNEL, MALADIE, MALADIE_SANS_SOLDE, MATERNITE, PATERNITE, SABBATIQUE, FORMATION, SANS_SOLDE
    libelle VARCHAR(100) NOT NULL,

    est_paye BOOLEAN DEFAULT TRUE,
    decompte_solde_acquis BOOLEAN DEFAULT TRUE,    -- FALSE : le congé n'entame pas le solde de congé annuel (droit légal distinct)
    exige_anciennete_min_mois INT DEFAULT 0,       -- 12 pour ANNUEL

    duree_fixe_jours NUMERIC(5,1),                 -- durée légale de référence : 98 (MATERNITE, extensible à 119 via demande_conge.extension_pathologique_maternite)

    -- Maladie : plafond par ÉPISODE continu (pas remis à zéro chaque année civile,
    -- c'est la durée d'un arrêt donné qui compte, Art. 13). Au-delà, bascule
    -- automatique vers un autre type (ex: MALADIE -> MALADIE_SANS_SOLDE à 30j).
    plafond_jours_episode NUMERIC(5,1),
    id_type_conge_bascule INT REFERENCES ref_type_conge(id_type_conge),

    -- Congés exceptionnels + paternité : pool annuel global partagé (10j/an, Art. 88).
    id_pool_conge_annuel INT REFERENCES ref_pool_conge_annuel(id_pool),

    -- Préavis (Art. 85) : ne s'applique qu'aux congés planifiables à l'avance
    -- (ANNUEL, FORMATION, SABBATIQUE) — pas aux événements imprévisibles
    -- (MALADIE, MATERNITE, PATERNITE, EXCEPTIONNEL). Les seuils réels (2j / 15j)
    -- sont dans parametre_global pour rester ajustables sans migration.
    soumis_delai_preavis BOOLEAN DEFAULT FALSE,

    -- TRUE = la période n'est PAS assimilée à du temps de travail effectif
    -- (Art. 86/87 + assimilation légale) : le batch mensuel ACQUISITION doit
    -- sauter l'employé sur cette période, et le calcul d'ancienneté
    -- (éligibilité ANNUEL 12 mois, bonification 3 ans/5 ans) doit neutraliser
    -- ces jours. FALSE (défaut) = assimilé à du temps de travail effectif
    -- (ANNUEL, MALADIE dans l'épisode, MATERNITE, EXCEPTIONNEL, PATERNITE).
    suspend_acquisition_solde BOOLEAN DEFAULT FALSE,

    exige_justificatif BOOLEAN DEFAULT FALSE,
    est_actif BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Sous-référentiel des événements familiaux (Art. 88) — quota par motif,
-- imputé sur le pool global ci-dessus.
CREATE TABLE ref_motif_evenement_familial (
    id_motif SERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,   -- MARIAGE, NAISSANCE, DECES_CONJOINT_ENFANT, DECES_PARENT
    libelle VARCHAR(100) NOT NULL,
    nb_jours_autorises NUMERIC(4,1) NOT NULL,
    justificatif_requis BOOLEAN DEFAULT TRUE,
    est_actif BOOLEAN DEFAULT TRUE
);

-- Statuts du circuit de demande — même pattern que ref_statut_workflow.
CREATE TABLE ref_statut_demande_conge (
    id_statut_demande_conge SERIAL PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,   -- BROUILLON, EN_ATTENTE, VALIDE, REFUSE, ANNULE
    libelle VARCHAR(100) NOT NULL
);

-- ------------------------------------------
-- 3. DEMANDES DE CONGÉ
-- ------------------------------------------

CREATE TABLE demande_conge (
    id_demande_conge SERIAL PRIMARY KEY,
    id_employe INT NOT NULL REFERENCES employe(id_employe),
    id_type_conge INT NOT NULL REFERENCES ref_type_conge(id_type_conge),
    id_statut_demande_conge INT NOT NULL REFERENCES ref_statut_demande_conge(id_statut_demande_conge),
    id_motif_evenement_familial INT REFERENCES ref_motif_evenement_familial(id_motif),  -- renseigné si EXCEPTIONNEL/PATERNITE

    date_debut DATE NOT NULL,
    date_fin DATE NOT NULL,
    date_demande DATE NOT NULL DEFAULT CURRENT_DATE,   -- nécessaire pour contrôler le délai de préavis

    -- Calendaire strict confirmé : samedi, dimanche ET jours fériés comptent
    -- tous dans le décompte (aucune exclusion). => nb_jours_decomptes = date_fin - date_debut + 1
    nb_jours_decomptes NUMERIC(5,1) NOT NULL,

    -- Dérogations RH tracées explicitement (transparence + reporting), plutôt
    -- que bloquées silencieusement ou validées sans trace.
    derogation_solde_negatif BOOLEAN DEFAULT FALSE,   -- validé malgré solde insuffisant (anticipation)
    derogation_delai_preavis BOOLEAN DEFAULT FALSE,   -- validé malgré préavis non respecté
    derogation_continuite_service BOOLEAN DEFAULT FALSE, -- validé malgré collision de poste (continuité de service)

    -- Extension maternité pour état pathologique (Art. 6 CNaPS) : porte le
    -- plafond de 98j à 119j (98 + 21j max). Applicable uniquement si
    -- id_type_conge = MATERNITE. Justificatif obligatoire dans ce cas.
    extension_pathologique_maternite BOOLEAN DEFAULT FALSE,

    -- Demi-journée de congé (0,5 j) : si TRUE, date_debut doit égaler date_fin et moment_demi_journee ('MATIN' ou 'APRES_MIDI') est renseigné.
    est_demi_journee BOOLEAN DEFAULT FALSE,
    moment_demi_journee VARCHAR(10) CHECK (moment_demi_journee IN ('MATIN', 'APRES_MIDI')),

    est_remunere BOOLEAN DEFAULT NULL, -- NULL = selon ref_type_conge, TRUE = rémunérée (maintien salaire), FALSE = non rémunérée (sans solde)
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

-- ------------------------------------------
-- 4. SOLDE DE CONGÉ — approche "mouvements", pas un compteur figé
-- ------------------------------------------
--   ACQUISITION  : +2,5j générés automatiquement chaque mois travaillé
--   BONIFICATION : +jours, majoration d'ancienneté (Art. 86/87 : +1j / tranche
--                  de 5 ans au-delà de 3 ans d'ancienneté). Distinct
--                  d'ACQUISITION pour garder un reporting propre entre le
--                  crédit mensuel de base et la majoration légale.
--   PRISE        : -nb_jours_decomptes, généré à la validation d'une demande
--   AJUSTEMENT   : +/- régularisation manuelle DAF (avec commentaire obligatoire)
--   ANNULATION   : +nb_jours_decomptes, si une demande validée est annulée après coup
--   EXPIRATION   : -jours, prescription triennale (job batch : les jours acquis
--                  il y a plus de 36 mois et non consommés expirent, Art. 85 --
--                  cf. PLAFOND_CUMUL_SOLDE_CONGE_JOURS / DUREE_PRESCRIPTION_SOLDE_CONGE_MOIS)
CREATE TABLE mouvement_solde_conge (
    id_mouvement SERIAL PRIMARY KEY,
    id_employe INT NOT NULL REFERENCES employe(id_employe),
    type_mouvement VARCHAR(20) NOT NULL CHECK (type_mouvement IN ('ACQUISITION','BONIFICATION','PRISE','AJUSTEMENT','ANNULATION','EXPIRATION')),
    nb_jours NUMERIC(5,2) NOT NULL,          -- signé : positif pour ACQUISITION/BONIFICATION/AJUSTEMENT(+)/ANNULATION, négatif pour PRISE/EXPIRATION
    id_demande_conge INT REFERENCES demande_conge(id_demande_conge) ON DELETE SET NULL,
    date_mouvement DATE NOT NULL DEFAULT CURRENT_DATE,
    commentaire TEXT,
    created_by INT REFERENCES utilisateur(id_utilisateur),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_mouvement_solde_employe ON mouvement_solde_conge(id_employe);
CREATE INDEX idx_mouvement_solde_demande ON mouvement_solde_conge(id_demande_conge);
CREATE INDEX idx_mouvement_solde_date ON mouvement_solde_conge(date_mouvement);
CREATE UNIQUE INDEX idx_uniq_mouvement_prise_demande ON mouvement_solde_conge(id_demande_conge, type_mouvement) WHERE type_mouvement = 'PRISE' AND id_demande_conge IS NOT NULL;
CREATE UNIQUE INDEX idx_uniq_mouvement_annulation_demande ON mouvement_solde_conge(id_demande_conge, type_mouvement) WHERE type_mouvement = 'ANNULATION' AND id_demande_conge IS NOT NULL;

-- ------------------------------------------
-- 5. VUES DE SYNTHÈSE
-- ------------------------------------------

CREATE OR REPLACE VIEW v_solde_conge_employe AS
SELECT
    id_employe,
    COALESCE(SUM(nb_jours), 0) AS solde_actuel,   -- somme algébrique : nb_jours déjà signé (négatif pour PRISE/EXPIRATION)
    COALESCE(SUM(nb_jours) FILTER (WHERE type_mouvement = 'ACQUISITION'), 0) AS total_acquis,
    COALESCE(SUM(nb_jours) FILTER (WHERE type_mouvement = 'BONIFICATION'), 0) AS total_bonification,
    COALESCE(SUM(-nb_jours) FILTER (WHERE type_mouvement = 'PRISE'), 0) AS total_pris,
    COALESCE(SUM(-nb_jours) FILTER (WHERE type_mouvement = 'EXPIRATION'), 0) AS total_expire,
    MAX(date_mouvement) FILTER (WHERE type_mouvement = 'ACQUISITION') AS derniere_acquisition
FROM mouvement_solde_conge
GROUP BY id_employe;

-- Consommation du pool annuel partagé (permissions exceptionnelles + paternité),
-- par employé / pool / année civile — comparée à ref_pool_conge_annuel.plafond_jours_an
-- côté application pour bloquer/alerter une fois le quota atteint.
CREATE OR REPLACE VIEW v_pool_conge_annuel_consommation AS
SELECT
    d.id_employe,
    tc.id_pool_conge_annuel AS id_pool,
    EXTRACT(YEAR FROM d.date_debut)::INT AS annee,
    SUM(d.nb_jours_decomptes) AS jours_consommes
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

-- ------------------------------------------
-- 6. EXTENSION notification_rh (types d'alerte congés)
-- ------------------------------------------
-- NOTE : La contrainte chk_notif_type est définie de façon exhaustive
-- dans 26-08-10-sp2-notifications.sql. Aucune modification nécessaire ici.

-- ------------------------------------------
-- 7. PARAMÈTRES GLOBAUX
-- ------------------------------------------
-- NB : DIVISEUR_HORAIRE_ABSENCE (=173.33) déjà défini dans 26-08-05-sp0-ref.sql
-- (sert à valoriser les ABSENCES, pas les congés).
INSERT INTO parametre_global (code_parametre, categorie, valeur, description, date_debut) VALUES
('DIVISEUR_JOURS_CALENDAIRE_CONGE', 'CONGE', '30', 'Diviseur en jours calendaires/mois pour valoriser un jour de congé payé : (Salaire de base + HS) ÷ 30. Cohérent avec le décompte calendaire (sam/dim/fériés comptés).', '2026-01-01'),
('PLAFOND_CUMUL_SOLDE_CONGE_JOURS', 'CONGE', '90', 'Plafond légal du solde cumulé de congés payés (30j acquis/an x 3 ans de prescription, Art. 85)', '2026-01-01'),
('DUREE_PRESCRIPTION_SOLDE_CONGE_MOIS', 'CONGE', '36', 'Durée au-delà de laquelle un jour de congé acquis et non pris expire (prescription triennale) — alimente le job batch générant les mouvements EXPIRATION', '2026-01-01'),
('PLAFOND_SOLDE_CONGE_NEGATIF', 'CONGE', '-10', 'Solde négatif maximum autorisable par dérogation RH lors d une prise de congé par anticipation (2 à 4 mois d acquisition future)', '2026-01-01'),
('SEUIL_ALERTE_SOLDE_CONGE_FAIBLE', 'CONGE', '3', 'Nombre de jours de solde en-dessous duquel l alerte SOLDE_CONGE_FAIBLE se déclenche', '2026-01-01'),
('DELAI_PREAVIS_COURT_JOURS', 'CONGE', '2', 'Préavis minimum (en jours avant date_debut) pour une demande courte durée (Art. 85)', '2026-01-01'),
('DELAI_PREAVIS_LONG_JOURS', 'CONGE', '15', 'Préavis minimum pour une demande longue durée (seuil défini par SEUIL_JOURS_PREAVIS_LONG)', '2026-01-01'),
('SEUIL_JOURS_PREAVIS_LONG', 'CONGE', '3', 'Nombre de jours posés à partir duquel le préavis long (DELAI_PREAVIS_LONG_JOURS) s applique au lieu du préavis court', '2026-01-01'),
('FRACTIONNEMENT_TRANCHE_MIN_JOURS', 'CONGE', '12', 'Nombre de jours consécutifs minimum devant être posés en une seule tranche au moins une fois par exercice pour le congé annuel (Art. 85)', '2026-01-01'),
('DUREE_MAX_MATERNITE_STANDARD_JOURS', 'CONGE', '98', 'Durée légale standard du congé maternité (14 semaines calendaires, Art. 85)', '2026-01-01'),
('DUREE_MAX_MATERNITE_PATHOLOGIQUE_JOURS', 'CONGE', '119', 'Durée maximale du congé maternité en cas d extension pour état pathologique (98 + 21j)', '2026-01-01'),
('SEUIL_ANCIENNETE_BONIFICATION_ANS', 'CONGE', '3', 'Ancienneté minimale (en années) à partir de laquelle la bonification de congé s applique (Art. 86/87)', '2026-01-01'),
('PALIER_ANNEES_BONIFICATION', 'CONGE', '5', 'Nombre d années par tranche donnant droit à un jour de bonification supplémentaire (Art. 86/87)', '2026-01-01'),
('JOURS_BONIFICATION_PAR_PALIER', 'CONGE', '1', 'Nombre de jours de congé supplémentaires accordés par tranche d ancienneté atteinte (Art. 86/87)', '2026-01-01')
ON CONFLICT (code_parametre, date_debut) DO NOTHING;

-- ------------------------------------------
-- 8. DONNÉES INITIALES
-- ------------------------------------------

-- Pool annuel partagé "permissions exceptionnelles" (Art. 88) : 10j/an, EXCEPTIONNEL + PATERNITE
INSERT INTO ref_pool_conge_annuel (code, libelle, plafond_jours_an) VALUES
('PERMISSIONS_FAMILIALES', 'Permissions exceptionnelles pour événements familiaux (Art. 88)', 10);

-- Types de congé — ordre volontaire : MALADIE_SANS_SOLDE créé avant MALADIE
-- pour permettre la référence id_type_conge_bascule dès l'insertion.
-- suspend_acquisition_solde = TRUE uniquement pour les congés NON assimilés
-- à du temps de travail effectif (SANS_SOLDE, SABBATIQUE, FORMATION,
-- MALADIE_SANS_SOLDE). Les autres (défaut FALSE) continuent de générer
-- de l'acquisition et de compter dans l'ancienneté.
INSERT INTO ref_type_conge (code, libelle, est_paye, decompte_solde_acquis, exige_anciennete_min_mois, duree_fixe_jours, plafond_jours_episode, exige_justificatif, soumis_delai_preavis, suspend_acquisition_solde) VALUES
('MALADIE_SANS_SOLDE', 'Congé maladie au-delà du maintien de salaire (prise en charge CNaPS)', FALSE, FALSE, 0, NULL, 150, TRUE, FALSE, TRUE);

INSERT INTO ref_type_conge (code, libelle, est_paye, decompte_solde_acquis, exige_anciennete_min_mois, duree_fixe_jours, plafond_jours_episode, id_type_conge_bascule, exige_justificatif, soumis_delai_preavis, suspend_acquisition_solde) VALUES
('MALADIE', 'Congé maladie (maintien de salaire)', TRUE, FALSE, 0, NULL, 30,
    (SELECT id_type_conge FROM ref_type_conge WHERE code = 'MALADIE_SANS_SOLDE'),
    TRUE, FALSE, FALSE);

INSERT INTO ref_type_conge (code, libelle, est_paye, decompte_solde_acquis, exige_anciennete_min_mois, duree_fixe_jours, id_pool_conge_annuel, exige_justificatif, soumis_delai_preavis, suspend_acquisition_solde) VALUES
('EXCEPTIONNEL', 'Congé exceptionnel (événement familial)', TRUE, FALSE, 0, NULL,
    (SELECT id_pool FROM ref_pool_conge_annuel WHERE code = 'PERMISSIONS_FAMILIALES'),
    TRUE, FALSE, FALSE),
('PATERNITE', 'Congé paternité', TRUE, FALSE, 0, 3,
    (SELECT id_pool FROM ref_pool_conge_annuel WHERE code = 'PERMISSIONS_FAMILIALES'),
    TRUE, FALSE, FALSE);

INSERT INTO ref_type_conge (code, libelle, est_paye, decompte_solde_acquis, exige_anciennete_min_mois, duree_fixe_jours, exige_justificatif, soumis_delai_preavis, suspend_acquisition_solde) VALUES
('ANNUEL',     'Congé annuel payé',  TRUE,  TRUE,  12, NULL, FALSE, TRUE,  FALSE),
('MATERNITE',  'Congé maternité',    TRUE,  FALSE, 0,  98,   TRUE,  FALSE, FALSE),
('PERMISSION', 'Permission exceptionnelle autorisée', TRUE, FALSE, 0, NULL, TRUE, FALSE, FALSE),
('SABBATIQUE', 'Congé sabbatique',   FALSE, FALSE, 0,  NULL, FALSE, TRUE,  TRUE),
('FORMATION',  'Congé formation',    FALSE, FALSE, 0,  NULL, FALSE, TRUE,  TRUE),
('SANS_SOLDE', 'Congé sans solde',   FALSE, FALSE, 0,  NULL, FALSE, FALSE, TRUE);

-- Sous-référentiel des motifs d'événements familiaux (Art. 88 / convention collective)
INSERT INTO ref_motif_evenement_familial (code, libelle, nb_jours_autorises, justificatif_requis) VALUES
('MARIAGE',              'Mariage du salarié',                  3, TRUE),
('NAISSANCE',             'Naissance / baptême d un enfant',     2, TRUE),
('DECES_CONJOINT_ENFANT', 'Décès du conjoint ou d un enfant',    3, TRUE),
('DECES_PARENT',          'Décès d un parent ou beau-parent',    2, TRUE);

INSERT INTO ref_statut_demande_conge (code, libelle) VALUES
('BROUILLON',  'Brouillon (saisie RH en cours)'),
('EN_ATTENTE', 'En attente de validation DAF'),
('VALIDE',     'Validée'),
('REFUSE',     'Refusée'),
('ANNULE',     'Annulée');