-- ============================================================
-- MODULE POINTAGE & PRÉSENCE
-- Dépend de : ref_site, ref_fonction, employe, ref_indemnite,
--             site_indemnite_config, ref_calendrier_ferie,
--             site_calendrier_ferie, utilisateur, ref_categorie_professionnelle,
--             demande_conge (26-08-13-sp2-conges.sql — matérialisation des jours CONGE)
-- Version : 1.1.0 — Gel des données à la clôture, proratisation seuil hebdo
-- ============================================================

-- ------------------------------------------
-- 0. NETTOYAGE PRÉALABLE (ordre inverse des dépendances)
-- ------------------------------------------
DROP VIEW IF EXISTS v_synthese_pointage_mensuelle CASCADE;
DROP VIEW IF EXISTS v_hs_mensuelle CASCADE;
DROP VIEW IF EXISTS v_pointage_semaine CASCADE;
DROP VIEW IF EXISTS v_pointage_jour_cpl CASCADE;

DROP TABLE IF EXISTS pointage_cumul_periode CASCADE;
DROP TABLE IF EXISTS pointage_semaine_calcule CASCADE;
DROP TABLE IF EXISTS pointage_indemnite_jour CASCADE;
DROP TABLE IF EXISTS pointage_jour CASCADE;
DROP TABLE IF EXISTS employe_indemnite_derogation CASCADE;
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
    ('TRANSMIS_DAF', 'Transmise à la DAF'),
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
    motif_rejet TEXT,
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
    ('ANOMALIE_SAISIE', 'Pointage incomplet (anomalie)', FALSE, TRUE),
    ('ABSENCE_JUSTIFIEE', 'Absence justifiée (avec motif)', TRUE, FALSE),
    ('CONGE', 'En congé (matérialisé depuis demande_conge)', FALSE, FALSE),
    ('FERIE_TRAVAILLE', 'Jour férié travaillé', FALSE, TRUE),
    ('FERIE_CHOME', 'Jour férié chômé', FALSE, FALSE),
    ('SAMEDI_TRAVAILLE', 'Samedi travaillé', FALSE, TRUE),
    ('DIMANCHE_TRAVAILLE', 'Dimanche travaillé', FALSE, TRUE),
    ('DISPENSE_POINTAGE', 'Poste autonome — dispense de pointage', FALSE, FALSE);

-- Plafond d'arrivée anticipée : une seule règle par site (pas de paliers)
CREATE TABLE ref_tolerance_arrivee_pointage (
    id_tolerance SERIAL PRIMARY KEY,
    id_site INTEGER NOT NULL UNIQUE REFERENCES ref_site(id_site),
    heure_plafond TIME NOT NULL   -- ex 07:30 : toute arrivée avant cette heure compte comme 07:30
);

-- Seed tolérance d'arrivée anticipée (TNR et TMM à 07:30)
INSERT INTO ref_tolerance_arrivee_pointage (id_site, heure_plafond) VALUES
    (1, '07:30'),
    (2, '07:30');

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
    heures_nuit DECIMAL(4,2) NOT NULL DEFAULT 0,
    minutes_retard INTEGER DEFAULT 0,
    minutes_depart_anticipe INTEGER DEFAULT 0,

    -- Indemnités (Niveau 3 de la cascade — ajustement du jour)
    indemnite_deplacement_recue BOOLEAN DEFAULT TRUE,
    deplacement_matin BOOLEAN DEFAULT TRUE,
    deplacement_am BOOLEAN DEFAULT TRUE,
    montant_deplacement_jour DECIMAL(10,2),
    indemnite_repas_recue BOOLEAN DEFAULT FALSE,
    montant_repas DECIMAL(10,2) DEFAULT 0,
    utilise_vehicule_fonction_jour BOOLEAN DEFAULT FALSE,
    horaire_continu_jour BOOLEAN,

    motif_ajustement TEXT,
    saisi_par INTEGER REFERENCES utilisateur(id_utilisateur),

    -- Traçabilité des lignes matérialisées automatiquement (congé validé / jour férié chômé)
    id_demande_conge INTEGER REFERENCES demande_conge(id_demande_conge),
    genere_automatiquement BOOLEAN NOT NULL DEFAULT FALSE,

    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_pointage_employe_jour UNIQUE (id_employe, date_pointage)
);

CREATE INDEX idx_pointage_demande_conge ON pointage_jour(id_demande_conge) WHERE id_demande_conge IS NOT NULL;

CREATE INDEX idx_pointage_employe ON pointage_jour(id_employe);
CREATE INDEX idx_pointage_periode ON pointage_jour(id_periode_paie);
CREATE INDEX idx_pointage_date ON pointage_jour(date_pointage);
CREATE INDEX idx_pointage_statut ON pointage_jour(id_statut_jour);

-- Table universelle des indemnités journalières (Source unique de vérité des primes et indemnités)
CREATE TABLE IF NOT EXISTS pointage_indemnite_jour (
    id_pointage_indemnite SERIAL PRIMARY KEY,
    id_pointage INT NOT NULL REFERENCES pointage_jour(id_pointage) ON DELETE CASCADE,
    id_indemnite INT NOT NULL REFERENCES ref_indemnite(id_indemnite),
    fraction VARCHAR(20) NOT NULL DEFAULT 'JOUR_ENTIER', -- 'MATIN', 'APRES_MIDI', 'JOUR_ENTIER'
    quantite NUMERIC(4,2) NOT NULL DEFAULT 1.0,          -- 0.5, 1.0, 2.0
    montant_calcule NUMERIC(12,2) DEFAULT 0,
    accorde BOOLEAN NOT NULL DEFAULT TRUE,
    motif_ajustement TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_pointage_indemnite_fraction UNIQUE (id_pointage, id_indemnite, fraction)
);

CREATE INDEX IF NOT EXISTS idx_pointage_indemnite_ptg ON pointage_indemnite_jour(id_pointage);
CREATE INDEX IF NOT EXISTS idx_pointage_indemnite_ref ON pointage_indemnite_jour(id_indemnite);

-- Vue unifiée des indemnités par pointage pour faciliter les requêtes de synthèse
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
-- 4. VUES (temps réel — affichage avant clôture)
-- ============================================================

DROP VIEW IF EXISTS v_pointage_jour_cpl CASCADE;
CREATE VIEW v_pointage_jour_cpl AS
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
    p.heures_matin, p.heures_am, p.total_heures, p.heures_nuit,
    p.minutes_retard, p.minutes_depart_anticipe,
    p.indemnite_deplacement_recue, p.deplacement_matin, p.deplacement_am, p.montant_deplacement_jour,
    p.indemnite_repas_recue, p.montant_repas,
    p.utilise_vehicule_fonction_jour, p.horaire_continu_jour,
    p.motif_ajustement, p.saisi_par, p.created_at, p.updated_at
FROM pointage_jour p
JOIN employe e ON p.id_employe = e.id_employe
JOIN periode_paie pp ON p.id_periode_paie = pp.id_periode_paie
JOIN ref_site rs ON p.id_site = rs.id_site
JOIN ref_statut_pointage_jour st ON p.id_statut_jour = st.id_statut_jour;

-- Vue hebdomadaire avec proratisation du seuil par tronçon
-- Partition par id_periode_paie : une semaine à cheval génère 2 lignes distinctes (une par période)
-- Seuil proratisé : 8h × nb_jours_ouvrés_du_tronçon (pas un seuil fixe de 40h)
DROP VIEW IF EXISTS v_pointage_semaine CASCADE;
CREATE VIEW v_pointage_semaine AS
SELECT
    p.id_employe,
    p.id_periode_paie,
    date_trunc('week', p.date_pointage)::date AS debut_semaine,
    COUNT(*)                                  AS nb_jours_troncon,
    COUNT(*) * 8.0                            AS seuil_heures_troncon,
    SUM(p.total_heures)                       AS total_heures_semaine,
    SUM(p.minutes_retard) / 60.0              AS heures_retard_semaine,
    GREATEST(SUM(p.total_heures) - COUNT(*) * 8.0, 0)
                                              AS heures_hs_brutes,
    GREATEST(COUNT(*) * 8.0 - SUM(p.total_heures), 0)
                                              AS heures_deficit_non_compense
FROM pointage_jour p
JOIN ref_statut_pointage_jour s ON p.id_statut_jour = s.id_statut_jour
WHERE s.code IN ('PRESENT', 'FERIE_TRAVAILLE', 'SAMEDI_TRAVAILLE', 'DIMANCHE_TRAVAILLE')
GROUP BY p.id_employe, p.id_periode_paie, date_trunc('week', p.date_pointage);

-- Vue mensuelle temps réel (affichage saisie). Remplacée par pointage_cumul_periode à la clôture.
DROP VIEW IF EXISTS v_synthese_pointage_mensuelle CASCADE;
CREATE VIEW v_synthese_pointage_mensuelle AS
SELECT
    p.id_employe,
    p.id_periode_paie,
    COUNT(*) FILTER (WHERE s.code IN ('ABSENCE_NON_JUSTIFIEE', 'ABSENCE_JUSTIFIEE')) AS nb_jours_absence,
    COUNT(*) FILTER (WHERE s.code = 'FERIE_TRAVAILLE')              AS nb_jours_ferie_travaille,
    COUNT(*) FILTER (WHERE s.code = 'SAMEDI_TRAVAILLE')             AS nb_jours_samedi_travaille,
    COUNT(*) FILTER (WHERE s.code = 'DIMANCHE_TRAVAILLE')           AS nb_jours_dimanche_travaille,
    COALESCE(SUM(p.total_heures) FILTER (WHERE s.decompte_absence = FALSE), 0) AS total_heures_travaillees,
    COALESCE(SUM(p.heures_nuit), 0)                                 AS heures_nuit_total,
    COALESCE(SUM(
        CASE 
            WHEN pij_dep.quantite IS NOT NULL THEN (CASE WHEN pij_dep.accorde THEN pij_dep.quantite ELSE 0.0 END)
            WHEN p.deplacement_matin IS NOT NULL AND p.deplacement_am IS NOT NULL 
                THEN (CASE WHEN p.deplacement_matin = TRUE THEN 0.5 ELSE 0.0 END + CASE WHEN p.deplacement_am = TRUE THEN 0.5 ELSE 0.0 END)
            WHEN p.indemnite_deplacement_recue = TRUE THEN 1.0
            ELSE 0.0 
        END
    ), 0) AS nb_jours_deplacement,
    COUNT(*) FILTER (WHERE COALESCE(pij_repas.accorde, p.indemnite_repas_recue)) AS nb_jours_repas,
    COALESCE(SUM(COALESCE(pij_repas.montant_calcule, p.montant_repas)), 0)       AS montant_repas_total
FROM pointage_jour p
JOIN ref_statut_pointage_jour s ON p.id_statut_jour = s.id_statut_jour
LEFT JOIN pointage_indemnite_jour pij_repas ON p.id_pointage = pij_repas.id_pointage 
    AND pij_repas.id_indemnite = (SELECT id_indemnite FROM ref_indemnite WHERE code = 'REPAS' LIMIT 1)
LEFT JOIN pointage_indemnite_jour pij_dep ON p.id_pointage = pij_dep.id_pointage 
    AND pij_dep.id_indemnite IN (SELECT id_indemnite FROM ref_indemnite WHERE code IN ('DEPLACEMENT', 'TRANSPORT') LIMIT 1)
GROUP BY p.id_employe, p.id_periode_paie;

-- Agrégat des HS brutes mensuelle depuis les tronçons hebdomadaires
DROP VIEW IF EXISTS v_hs_mensuelle CASCADE;
CREATE VIEW v_hs_mensuelle AS
SELECT
    w.id_periode_paie,
    w.id_employe,
    SUM(w.heures_hs_brutes)      AS heures_hs_brutes_mensuelles,
    SUM(w.heures_deficit_non_compense) AS heures_deficit_mensuelles
FROM v_pointage_semaine w
GROUP BY w.id_periode_paie, w.id_employe;

-- ============================================================
-- 5. TABLES DE SYNTHÈSE GELÉES À LA CLÔTURE (RG-PTG-14)
-- Alimentées au passage en VALIDEE. Immuables après CLOTUREE.
-- Garantissent la reproductibilité légale des bulletins de paie.
-- ============================================================

-- Tronçons hebdomadaires figés (un par semaine ISO × période × employé)
CREATE TABLE pointage_semaine_calcule (
    id_semaine_calcule       SERIAL PRIMARY KEY,
    id_employe               INTEGER NOT NULL REFERENCES employe(id_employe),
    id_periode_paie          INTEGER NOT NULL REFERENCES periode_paie(id_periode_paie),
    debut_semaine            DATE NOT NULL,          -- lundi ISO, peut être avant la période
    nb_jours_troncon         INTEGER NOT NULL,        -- jours ouvrés réels du tronçon dans la période
    seuil_heures_troncon     DECIMAL(5,2) NOT NULL,  -- nb_jours_troncon × 8
    total_heures_travaillees DECIMAL(5,2) NOT NULL DEFAULT 0,
    heures_retard            DECIMAL(5,2) NOT NULL DEFAULT 0,
    heures_hs_brutes         DECIMAL(5,2) NOT NULL DEFAULT 0,  -- surplus au-dessus du seuil proratisé
    heures_hs_nettes         DECIMAL(5,2) NOT NULL DEFAULT 0,  -- après déduction retards (0 si Cadre)
    heures_deficit           DECIMAL(5,2) NOT NULL DEFAULT 0,  -- retard net non compensé
    taux_hs_30_applique      DECIMAL(5,2) NOT NULL DEFAULT 0,
    taux_hs_50_applique      DECIMAL(5,2) NOT NULL DEFAULT 0,
    taux_nuit_applique       DECIMAL(5,2) NOT NULL DEFAULT 0,
    taux_dimanche_applique   DECIMAL(5,2) NOT NULL DEFAULT 0,
    taux_ferie_applique      DECIMAL(5,2) NOT NULL DEFAULT 0,
    heures_hs_30             DECIMAL(5,2) NOT NULL DEFAULT 0,  -- MIN(heures_hs_nettes, 8)
    heures_hs_50             DECIMAL(5,2) NOT NULL DEFAULT 0,  -- MAX(heures_hs_nettes - 8, 0)
    gele_par                 INTEGER REFERENCES utilisateur(id_utilisateur),
    gele_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_semaine_calcule UNIQUE (id_employe, id_periode_paie, debut_semaine)
);

CREATE INDEX idx_semaine_calcule_employe_periode
    ON pointage_semaine_calcule(id_employe, id_periode_paie);

-- Cumul mensuel figé (une ligne par employé × période) — source de vérité pour la Paie
CREATE TABLE pointage_cumul_periode (
    id_cumul                 SERIAL PRIMARY KEY,
    id_employe               INTEGER NOT NULL REFERENCES employe(id_employe),
    id_periode_paie          INTEGER NOT NULL REFERENCES periode_paie(id_periode_paie),
    nb_jours_ouvrables       INTEGER NOT NULL,          -- fn_jours_ouvrables() à la date de gel
    nb_jours_presents        INTEGER NOT NULL DEFAULT 0,
    nb_jours_absence         INTEGER NOT NULL DEFAULT 0,
    nb_jours_conge           INTEGER NOT NULL DEFAULT 0,
    nb_jours_ferie_chome     INTEGER NOT NULL DEFAULT 0,
    nb_jours_ferie_travaille INTEGER NOT NULL DEFAULT 0,
    total_heures_travaillees DECIMAL(6,2) NOT NULL DEFAULT 0,
    heures_hs_30             DECIMAL(6,2) NOT NULL DEFAULT 0,
    heures_hs_50             DECIMAL(6,2) NOT NULL DEFAULT 0,
    heures_nuit_total        DECIMAL(6,2) NOT NULL DEFAULT 0,
    heures_absence_retenues  DECIMAL(6,2) NOT NULL DEFAULT 0,  -- retards nets non compensés → déduction paie
    nb_jours_deplacement     DECIMAL(5,2) NOT NULL DEFAULT 0,
    montant_deplacement      DECIMAL(10,2) NOT NULL DEFAULT 0,
    nb_jours_repas           INTEGER NOT NULL DEFAULT 0,
    montant_repas            DECIMAL(10,2) NOT NULL DEFAULT 0,
    gele_par                 INTEGER REFERENCES utilisateur(id_utilisateur),
    gele_at                  TIMESTAMP NOT NULL,
    CONSTRAINT uk_cumul_employe_periode UNIQUE (id_employe, id_periode_paie)
);

CREATE INDEX idx_cumul_periode_employe
    ON pointage_cumul_periode(id_employe, id_periode_paie);

-- Cumul mensuel dynamique des indemnités figé à la clôture (extensibilité totale pour toutes indemnités)
CREATE TABLE IF NOT EXISTS pointage_cumul_indemnite (
    id_cumul_indemnite   SERIAL PRIMARY KEY,
    id_periode_paie      INTEGER NOT NULL REFERENCES periode_paie(id_periode_paie) ON DELETE CASCADE,
    id_employe           INTEGER NOT NULL REFERENCES employe(id_employe) ON DELETE CASCADE,
    id_indemnite         INTEGER NOT NULL REFERENCES ref_indemnite(id_indemnite),
    quantite_totale      DECIMAL(6,2) NOT NULL DEFAULT 0,
    montant_total        DECIMAL(12,2) NOT NULL DEFAULT 0,
    gele_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_cumul_indemnite UNIQUE (id_periode_paie, id_employe, id_indemnite)
);

CREATE INDEX IF NOT EXISTS idx_cumul_indemnite_periode
    ON pointage_cumul_indemnite(id_periode_paie, id_employe);

-- ============================================================
-- 6. EXTENSION DE notification_rh (nouveaux types d'alerte pointage)
-- ============================================================
-- NOTE : La contrainte chk_notif_type est définie de façon exhaustive
-- dans 26-08-10-sp2-notifications.sql. Aucune modification nécessaire ici.


