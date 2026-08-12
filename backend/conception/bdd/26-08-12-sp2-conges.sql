-- =====================================================================
-- SCRIPT POSTGRESQL : SIRH GALAXAM - MODULE CONGÉS & ABSENCES
-- Réf. §7 (et §6.3) du Recueil consolidé des règles de gestion v3
-- =====================================================================

-- ------------------------------------------
-- 1. NETTOYAGE PRÉALABLE (ordre inverse des dépendances)
-- ------------------------------------------
DROP VIEW IF EXISTS v_solde_conge_employe CASCADE;
DROP VIEW IF EXISTS v_demande_conge_cpl CASCADE;
DROP TABLE IF EXISTS mouvement_solde_conge CASCADE;
DROP TABLE IF EXISTS demande_conge CASCADE;
DROP TABLE IF EXISTS ref_statut_demande_conge CASCADE;
DROP TABLE IF EXISTS ref_type_conge CASCADE;

-- ------------------------------------------
-- 2. RÉFÉRENTIELS
-- ------------------------------------------

-- Typologie des congés (§7.2) — paramétrable pour absorber les évolutions
-- du règlement intérieur sans migration.
CREATE TABLE ref_type_conge (
    id_type_conge SERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,              -- ANNUEL, EXCEPTIONNEL, MALADIE, MATERNITE, PATERNITE, SABBATIQUE, FORMATION, SANS_SOLDE
    libelle VARCHAR(100) NOT NULL,

    est_paye BOOLEAN DEFAULT TRUE,
    decompte_solde_acquis BOOLEAN DEFAULT TRUE,    -- FALSE pour MATERNITE/PATERNITE/SABBATIQUE/SANS_SOLDE (droit distinct du solde congé annuel)
    exige_anciennete_min_mois INT DEFAULT 0,       -- 12 pour ANNUEL (§7.2 : sinon traité en sans solde)
    plafond_jours_an NUMERIC(5,1),                 -- 10 pour EXCEPTIONNEL — NULL = pas de plafond annuel fixe
    duree_fixe_jours NUMERIC(5,1),                 -- 98 (14 sem.) MATERNITE, 3 PATERNITE — NULL sinon (durée libre)
    exige_justificatif BOOLEAN DEFAULT FALSE,      -- TRUE pour MALADIE (repos OSTIE/OMSI ou ordonnance)

    est_actif BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Statuts du circuit de demande — même pattern que ref_statut_workflow.
-- Circuit (§7.5) : saisie par un RH pour le compte de l'employé, validation DAF.
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

    date_debut DATE NOT NULL,
    date_fin DATE NOT NULL,

    -- Nombre de jours réellement décomptés du solde (ou constaté pour les congés
    -- à durée fixe / sans solde). Calculé côté application selon le mode de décompte
    -- configuré (cf. paramètre MODE_DECOMPTE_WEEKEND_CONGE ci-dessous) et en excluant
    -- systématiquement les jours fériés (règle confirmée §7.1 : jamais décomptés).
    nb_jours_decomptes NUMERIC(5,1) NOT NULL,

    justificatif_fourni BOOLEAN DEFAULT FALSE,
    commentaire TEXT,

    saisi_par INT REFERENCES utilisateur(id_utilisateur),      -- le RH qui a enregistré la demande (§7.5)
    valide_par INT REFERENCES utilisateur(id_utilisateur),
    date_validation TIMESTAMP,
    motif_refus TEXT,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_demande_conge_dates CHECK (date_fin >= date_debut)
);

CREATE INDEX idx_demande_conge_employe ON demande_conge(id_employe);
CREATE INDEX idx_demande_conge_statut ON demande_conge(id_statut_demande_conge);
CREATE INDEX idx_demande_conge_periode ON demande_conge(date_debut, date_fin);

-- ------------------------------------------
-- 4. SOLDE DE CONGÉ — approche "mouvements", pas un compteur figé
-- ------------------------------------------
-- Permet l'historique complet, le cumul sur 3 ans (§7.1), l'audit, et les
-- régularisations manuelles sans jamais recalculer un solde stocké.
--   ACQUISITION  : +2,5j générés automatiquement chaque mois travaillé
--   PRISE        : -nb_jours_decomptes, généré à la validation d'une demande
--   AJUSTEMENT   : +/- régularisation manuelle DAF (avec commentaire obligatoire)
--   ANNULATION   : +nb_jours_decomptes, si une demande validée est annulée après coup
CREATE TABLE mouvement_solde_conge (
    id_mouvement SERIAL PRIMARY KEY,
    id_employe INT NOT NULL REFERENCES employe(id_employe),
    type_mouvement VARCHAR(20) NOT NULL CHECK (type_mouvement IN ('ACQUISITION','PRISE','AJUSTEMENT','ANNULATION')),
    nb_jours NUMERIC(5,2) NOT NULL,
    id_demande_conge INT REFERENCES demande_conge(id_demande_conge) ON DELETE SET NULL,
    date_mouvement DATE NOT NULL DEFAULT CURRENT_DATE,
    commentaire TEXT,
    created_by INT REFERENCES utilisateur(id_utilisateur),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_mouvement_solde_employe ON mouvement_solde_conge(id_employe);
CREATE INDEX idx_mouvement_solde_demande ON mouvement_solde_conge(id_demande_conge);

-- ------------------------------------------
-- 5. VUES DE SYNTHÈSE
-- ------------------------------------------

CREATE OR REPLACE VIEW v_solde_conge_employe AS
SELECT
    id_employe,
    COALESCE(SUM(nb_jours), 0) AS solde_actuel,
    COALESCE(SUM(nb_jours) FILTER (WHERE type_mouvement = 'ACQUISITION'), 0) AS total_acquis,
    COALESCE(SUM(-nb_jours) FILTER (WHERE type_mouvement = 'PRISE'), 0) AS total_pris,
    MAX(date_mouvement) FILTER (WHERE type_mouvement = 'ACQUISITION') AS derniere_acquisition
FROM mouvement_solde_conge
GROUP BY id_employe;

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
    tc.est_paye,
    d.id_statut_demande_conge,
    sd.code AS code_statut_demande,
    sd.libelle AS libelle_statut_demande,
    d.date_debut,
    d.date_fin,
    d.nb_jours_decomptes,
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
LEFT JOIN utilisateur us ON us.id_utilisateur = d.saisi_par
LEFT JOIN utilisateur uv ON uv.id_utilisateur = d.valide_par;

-- ------------------------------------------
-- 6. EXTENSION notification_rh (types d'alerte congés)
-- ------------------------------------------
-- La contrainte CHECK existante limite les valeurs autorisées : on l'élargit
-- plutôt que de la dupliquer, pour rester cohérent avec le module notifications.
ALTER TABLE notification_rh DROP CONSTRAINT IF EXISTS chk_notif_type;
ALTER TABLE notification_rh ADD CONSTRAINT chk_notif_type CHECK (type_alerte IN (
    'CONTRAT_EXPIRE_J', 'CONTRAT_EXPIRE_7J', 'CONTRAT_EXPIRE_30J',
    'ESSAI_EXPIRE_J', 'ESSAI_EXPIRE_7J', 'ESSAI_EXPIRE_15J', 'ESSAI_EXPIRE_30J',
    'BROUILLON_EN_ATTENTE', 'MOUVEMENT_APPLIQUE',
    'DOCUMENTS_MANQUANTS', 'EMPLOYE_CREE', 'BROUILLON_CREE',
    -- Congés & absences
    'DEMANDE_CONGE_EN_ATTENTE',    -- demande EN_ATTENTE non traitée depuis > 48h (miroir de BROUILLON_EN_ATTENTE)
    'SOLDE_CONGE_FAIBLE',          -- solde employé passé sous un seuil configurable
    'RETOUR_CONGE_J'               -- fin de congé = aujourd'hui (rappel opérationnel)
));

-- ------------------------------------------
-- 7. PARAMÈTRES GLOBAUX
-- ------------------------------------------
-- NB : DIVISEUR_TAUX_CONGES (=173.33) existe déjà depuis 26-08-05-sp0-ref.sql.
-- Malgré son nom, c'est en réalité le diviseur HORAIRE utilisé pour valoriser les
-- ABSENCES d'un employé sans droit à congé acquis (§7.4) — on ne le renomme pas
-- (pas de migration destructive sur un paramètre déjà utilisé), mais le vrai
-- diviseur JOURS pour les CONGÉS est distinct et ajouté ci-dessous (confirmé : 26j).
INSERT INTO parametre_global (code_parametre, valeur, description, date_debut) VALUES
('DIVISEUR_JOURS_OUVRABLES_CONGE', '26', 'Diviseur en jours ouvrables/mois pour valoriser un jour de congé payé : (Salaire de base + HS) ÷ 26', '2026-01-01'),
('MODE_DECOMPTE_WEEKEND_CONGE', 'JOURS_OUVRES', '⚠ PROVISOIRE, à confirmer avec la DAF. Mode de décompte des jours de congé posés : JOURS_OUVRES (exclut samedi/dimanche) ou CALENDAIRE (les inclut). Modifiable sans migration.', '2026-01-01'),
('SEUIL_ALERTE_SOLDE_CONGE_FAIBLE', '3', 'Nombre de jours de solde en-dessous duquel l alerte SOLDE_CONGE_FAIBLE se déclenche', '2026-01-01');

-- ------------------------------------------
-- 8. RBAC — module CONGE déjà créé (26-07-29_init.sql), on vérifie juste
-- que la matrice de droits couvre bien ce module (déjà le cas : id_module 2).
-- Rien à insérer ici, les droits ROLE_ADMIN / ROLE_AGENT_POINTAGE existent déjà.
-- ------------------------------------------

-- ------------------------------------------
-- 9. DONNÉES INITIALES — typologie des congés (§7.2)
-- ------------------------------------------
INSERT INTO ref_type_conge (code, libelle, est_paye, decompte_solde_acquis, exige_anciennete_min_mois, plafond_jours_an, duree_fixe_jours, exige_justificatif) VALUES
('ANNUEL',      'Congé annuel payé',            TRUE,  TRUE,  12, NULL, NULL, FALSE),
('EXCEPTIONNEL','Congé exceptionnel',           TRUE,  FALSE, 0,  10,   NULL, TRUE),
('MALADIE',     'Congé maladie',                TRUE,  FALSE, 0,  NULL, NULL, TRUE),
('MATERNITE',   'Congé maternité',              TRUE,  FALSE, 0,  NULL, 98,   TRUE),
('PATERNITE',   'Congé paternité',              TRUE,  FALSE, 0,  NULL, 3,    TRUE),
('SABBATIQUE',  'Congé sabbatique',             FALSE, FALSE, 0,  NULL, NULL, FALSE),
('FORMATION',   'Congé formation',              FALSE, FALSE, 0,  NULL, NULL, FALSE),
('SANS_SOLDE',  'Congé sans solde',             FALSE, FALSE, 0,  NULL, NULL, FALSE);

INSERT INTO ref_statut_demande_conge (code, libelle) VALUES
('BROUILLON',  'Brouillon (saisie RH en cours)'),
('EN_ATTENTE', 'En attente de validation DAF'),
('VALIDE',     'Validée'),
('REFUSE',     'Refusée'),
('ANNULE',     'Annulée');