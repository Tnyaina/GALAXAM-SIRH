-- ============================================================
-- MODULE POINTAGE & PRÉSENCE
-- Dépend de : ref_site, ref_fonction, employe, ref_indemnite,
--             site_indemnite_config, ref_calendrier_ferie,
--             site_calendrier_ferie, utilisateur, ref_categorie_professionnelle,
--             demande_conge (26-08-13-sp2-conges.sql — matérialisation des jours CONGE)
-- ============================================================

-- ------------------------------------------
-- 0. NETTOYAGE PRÉALABLE (ordre inverse des dépendances)
-- ------------------------------------------
DROP VIEW IF EXISTS v_synthese_pointage_mensuelle CASCADE;
DROP VIEW IF EXISTS v_pointage_semaine CASCADE;
DROP VIEW IF EXISTS v_pointage_jour_cpl CASCADE;

DROP TABLE IF EXISTS pointage_jour CASCADE;
DROP TABLE IF EXISTS employe_indemnite_derogation CASCADE;
DROP TABLE IF EXISTS ref_grille_arrondi_pointage CASCADE;
DROP TABLE IF EXISTS ref_tolerance_arrivee_pointage CASCADE;
DROP TABLE IF EXISTS ref_statut_pointage_jour CASCADE;
DROP TABLE IF EXISTS periode_paie CASCADE;
DROP TABLE IF EXISTS ref_statut_periode_paie CASCADE;

-- Ajout de la colonne "dispense_pointage" sur ref_fonction (poste autonome)
ALTER TABLE ref_fonction ADD COLUMN IF NOT EXISTS dispense_pointage BOOLEAN DEFAULT FALSE;

-- ============================================================
-- 1. RÉFÉRENTIELS
-- ============================================================

CREATE TABLE ref_statut_periode_paie (
    id_statut_periode SERIAL PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,   -- BROUILLON, EN_COURS, A_VALIDER, VALIDEE, CLOTUREE
    libelle VARCHAR(100) NOT NULL
);

INSERT INTO ref_statut_periode_paie (code, libelle) VALUES
    ('BROUILLON', 'Période créée, non ouverte'),
    ('EN_COURS', 'Saisie en cours'),
    ('A_VALIDER', 'Terminée, en attente de validation DAF'),
    ('VALIDEE', 'Validée — verrouillée pour saisie'),
    ('CLOTUREE', 'Clôturée — consommée par la Paie, immuable');

CREATE TABLE periode_paie (
    id_periode_paie SERIAL PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,          -- ex '2026-01'
    libelle VARCHAR(100) NOT NULL,             -- ex 'Décembre 2025 -> Janvier 2026'
    date_debut DATE NOT NULL,
    date_fin DATE NOT NULL,
    id_statut_periode INTEGER NOT NULL REFERENCES ref_statut_periode_paie(id_statut_periode),
    date_validation TIMESTAMP,
    valide_par INTEGER REFERENCES utilisateur(id_utilisateur),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_periode_dates CHECK (date_fin > date_debut)
);

CREATE TABLE ref_statut_pointage_jour (
    id_statut_jour SERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    libelle VARCHAR(100) NOT NULL,
    decompte_absence BOOLEAN NOT NULL DEFAULT FALSE,
    necessite_saisie BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO ref_statut_pointage_jour (code, libelle, decompte_absence, necessite_saisie) VALUES
    ('PRESENT', 'Présent', FALSE, TRUE),
    ('ABSENCE_NON_JUSTIFIEE', 'Absence non justifiée', TRUE, FALSE),
    ('CONGE', 'En congé (matérialisé depuis demande_conge)', FALSE, FALSE),
    ('FERIE_TRAVAILLE', 'Jour férié travaillé', FALSE, TRUE),
    ('FERIE_CHOME', 'Jour férié chômé', FALSE, FALSE),
    ('DISPENSE_POINTAGE', 'Poste autonome — dispense de pointage', FALSE, FALSE);

-- Plafond d'arrivée anticipée : une seule règle par site (pas de paliers)
CREATE TABLE ref_tolerance_arrivee_pointage (
    id_tolerance SERIAL PRIMARY KEY,
    id_site INTEGER NOT NULL UNIQUE REFERENCES ref_site(id_site),
    heure_plafond TIME NOT NULL   -- ex 07:30 : toute arrivée avant cette heure compte comme 07:30
);

-- Exemple de seed pour TNR (id_site = 1) : théorique 08:00, plafond 07:30
INSERT INTO ref_tolerance_arrivee_pointage (id_site, heure_plafond) VALUES
    (1, '07:30');

-- Dérogation individuelle d'indemnité (Niveau 2 de la cascade — module-structure.md §4.1)
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

CREATE INDEX idx_derogation_employe ON employe_indemnite_derogation(id_employe, id_indemnite)
    WHERE date_fin IS NULL;

-- ============================================================
-- 2. SAISIE QUOTIDIENNE
-- ============================================================

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

    -- Calculés en Java à la saisie (RG-PTG-01 à 04), source de vérité = ce qui est stocké
    heures_matin DECIMAL(4,2) DEFAULT 0,
    heures_am DECIMAL(4,2) DEFAULT 0,
    total_heures DECIMAL(4,2) DEFAULT 0,
    minutes_retard INTEGER DEFAULT 0,
    minutes_depart_anticipe INTEGER DEFAULT 0,

    -- Indemnités (Niveau 3 de la cascade — ajustement du jour)
    indemnite_deplacement_recue BOOLEAN DEFAULT TRUE,
    indemnite_repas_recue BOOLEAN DEFAULT FALSE,
    montant_repas DECIMAL(10,2) DEFAULT 0,
    utilise_vehicule_fonction_jour BOOLEAN DEFAULT FALSE,

    motif_ajustement TEXT,
    saisi_par INTEGER REFERENCES utilisateur(id_utilisateur),

    -- Traçabilité des lignes matérialisées automatiquement (congé validé / jour férié chômé)
    id_demande_conge INTEGER REFERENCES demande_conge(id_demande_conge),
    genere_automatiquement BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_pointage_employe_jour UNIQUE (id_employe, date_pointage)
);

CREATE INDEX idx_pointage_demande_conge ON pointage_jour(id_demande_conge) WHERE id_demande_conge IS NOT NULL;

CREATE INDEX idx_pointage_employe ON pointage_jour(id_employe);
CREATE INDEX idx_pointage_periode ON pointage_jour(id_periode_paie);
CREATE INDEX idx_pointage_date ON pointage_jour(date_pointage);
CREATE INDEX idx_pointage_statut ON pointage_jour(id_statut_jour);

-- ============================================================
-- 3. FONCTION : JOURS OUVRABLES DU MOIS (pour prorata des indemnités)
-- ============================================================

CREATE OR REPLACE FUNCTION fn_jours_ouvrables(
    p_date_debut DATE, p_date_fin DATE, p_id_site INT
) RETURNS INTEGER AS $$
DECLARE
    v_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO v_count
    FROM generate_series(p_date_debut, p_date_fin, '1 day') AS d
    WHERE EXTRACT(ISODOW FROM d) < 6   -- 1=lundi ... 5=vendredi
      AND NOT EXISTS (
          SELECT 1 FROM site_calendrier_ferie scf
          JOIN ref_calendrier_ferie f ON scf.id_ferie = f.id_ferie
          WHERE scf.id_site = p_id_site AND f.date_ferie = d AND f.est_chome = TRUE
      );
    RETURN v_count;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- ============================================================
-- 4. VUES
-- ============================================================

CREATE OR REPLACE VIEW v_pointage_jour_cpl AS
SELECT
    p.id_pointage,
    p.id_employe,
    e.matricule,
    e.nom || ' ' || e.prenom AS nom_prenom,
    p.id_periode_paie,
    pp.code AS code_periode,
    p.id_site,
    rs.libelle AS libelle_site,
    p.date_pointage,
    p.heure_entree_matin, p.heure_sortie_matin,
    p.heure_entree_am, p.heure_sortie_am,
    st.code AS code_statut, st.libelle AS libelle_statut,
    p.heures_matin, p.heures_am, p.total_heures,
    p.minutes_retard, p.minutes_depart_anticipe,
    p.indemnite_deplacement_recue, p.indemnite_repas_recue, p.montant_repas,
    p.utilise_vehicule_fonction_jour,
    p.motif_ajustement, p.saisi_par, p.created_at, p.updated_at
FROM pointage_jour p
JOIN employe e ON p.id_employe = e.id_employe
JOIN periode_paie pp ON p.id_periode_paie = pp.id_periode_paie
JOIN ref_site rs ON p.id_site = rs.id_site
JOIN ref_statut_pointage_jour st ON p.id_statut_jour = st.id_statut_jour;

-- Compensation hebdomadaire (RG-PTG-04) : base légale 40h/semaine
CREATE OR REPLACE VIEW v_pointage_semaine AS
SELECT
    p.id_employe,
    date_trunc('week', p.date_pointage)::date AS debut_semaine,
    SUM(p.total_heures) AS total_heures_semaine,
    SUM(p.minutes_retard) / 60.0 AS heures_retard_semaine,
    GREATEST(SUM(p.total_heures) - 40, 0) AS heures_hs_brutes,
    GREATEST(40 - SUM(p.total_heures), 0) AS heures_deficit_non_compense
FROM pointage_jour p
JOIN ref_statut_pointage_jour s ON p.id_statut_jour = s.id_statut_jour
WHERE s.code IN ('PRESENT', 'FERIE_TRAVAILLE')
GROUP BY p.id_employe, date_trunc('week', p.date_pointage);

-- Synthèse mensuelle -> interface de sortie vers la Paie
CREATE OR REPLACE VIEW v_synthese_pointage_mensuelle AS
SELECT
    p.id_employe,
    p.id_periode_paie,
    COUNT(*) FILTER (WHERE s.code = 'ABSENCE_NON_JUSTIFIEE')        AS nb_jours_absence,
    COUNT(*) FILTER (WHERE s.code = 'FERIE_TRAVAILLE')              AS nb_jours_ferie_travaille,
    COALESCE(SUM(p.total_heures) FILTER (WHERE s.decompte_absence = FALSE), 0) AS total_heures_travaillees,
    COUNT(*) FILTER (WHERE p.indemnite_deplacement_recue)           AS nb_jours_deplacement,
    COUNT(*) FILTER (WHERE p.indemnite_repas_recue)                 AS nb_jours_repas,
    COALESCE(SUM(p.montant_repas), 0)                               AS montant_repas_total
FROM pointage_jour p
JOIN ref_statut_pointage_jour s ON p.id_statut_jour = s.id_statut_jour
GROUP BY p.id_employe, p.id_periode_paie;

-- Heures HS mensuelles (agrégation des semaines contenues dans la période)
-- NOTE : jointe séparément car le découpage semaine/période n'est pas 1:1 (voir §11 du cahier des charges)
CREATE OR REPLACE VIEW v_hs_mensuelle AS
SELECT
    pp.id_periode_paie,
    w.id_employe,
    SUM(w.heures_hs_brutes) AS heures_hs_mensuelles
FROM v_pointage_semaine w
JOIN periode_paie pp
    ON w.debut_semaine BETWEEN pp.date_debut AND pp.date_fin
GROUP BY pp.id_periode_paie, w.id_employe;

-- ============================================================
-- 5. EXTENSION DE notification_rh (nouveaux types d'alerte pointage)
-- ============================================================

ALTER TABLE notification_rh DROP CONSTRAINT IF EXISTS chk_notif_type;
ALTER TABLE notification_rh ADD CONSTRAINT chk_notif_type CHECK (type_alerte IN (
    'CONTRAT_EXPIRE_J', 'CONTRAT_EXPIRE_7J', 'CONTRAT_EXPIRE_30J',
    'ESSAI_EXPIRE_J', 'ESSAI_EXPIRE_7J', 'ESSAI_EXPIRE_15J', 'ESSAI_EXPIRE_30J',
    'BROUILLON_EN_ATTENTE', 'MOUVEMENT_APPLIQUE',
    'DOCUMENTS_MANQUANTS', 'EMPLOYE_CREE', 'BROUILLON_CREE',
    'CONGE_EN_ATTENTE', 'SOLDE_CONGE_FAIBLE', 'SOLDE_CONGE_NEGATIF',
    'RETOUR_CONGE_J', 'FRACTIONNEMENT_NON_RESPECTE',
    'MALADIE_PLAFOND_EPISODE_ATTEINT', 'CONGE_EXPIRATION_PROCHE',
    'POOL_EXCEPTIONNEL_EPUISE', 'BONIFICATION_ANCIENNETE_APPLIQUEE',
    -- Nouveaux types Pointage
    'POINTAGE_MANQUANT', 'PERIODE_POINTAGE_A_VALIDER',
    'PERIODE_POINTAGE_VALIDEE', 'RETARD_RECURRENT'
));

-- ============================================================
-- 6. AJOUT DU MODULE POINTAGE AU RBAC (si non présent)
-- ============================================================
-- Rappel : ref_module contient déjà EMPLOYE, CONGE, POINTAGE, PAIE (26-07-29_init.sql)
-- La matrice role_permission pour ROLE_AGENT_POINTAGE (lecture+écriture, sans validation)
-- et ROLE_ADMIN (accès total) est déjà en place — aucune action requise ici.

-- ============================================================
-- 7. SEED DE DÉMARRAGE
-- ============================================================

INSERT INTO periode_paie (code, libelle, date_debut, date_fin, id_statut_periode)
VALUES ('2026-01', 'Décembre 2025 -> Janvier 2026', '2025-11-26', '2025-12-25',
        (SELECT id_statut_periode FROM ref_statut_periode_paie WHERE code = 'EN_COURS'));

-- Exemple de dérogation permanente (véhicule de fonction) — à adapter aux 4 employés réels
-- INSERT INTO employe_indemnite_derogation (id_employe, id_indemnite, beneficie, motif, date_debut)
-- VALUES (<id_employe>, (SELECT id_indemnite FROM ref_indemnite WHERE code='DEPLACEMENT'), FALSE,
--         'Véhicule de fonction', '2026-01-01');
