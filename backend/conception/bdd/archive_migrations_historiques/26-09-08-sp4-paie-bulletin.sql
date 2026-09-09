-- ==============================================================================
-- SP4 — PAIE : Socle Relationnel et Immuable (Bulletins, Lignes, Rubriques, Avances)
-- ==============================================================================
-- Références légales :
-- - Code du Travail Malgache (Loi n° 2003-044) - Art. 68 (Quotité cessible 1/3)
-- - Code Général des Impôts (Loi de Finances 2026 n° 2025-021) - Art. 01.03.14 (IRSA)
-- - Code de Prévoyance Sociale (CNaPS plafonnée 8x SME, OSTIE / OMSI)
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. CATALOGUE DES RUBRIQUES DE PAIE (paie_rubrique)
-- Dictionnaire normalisé des composants de paie
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS paie_rubrique (
    id_rubrique         SERIAL PRIMARY KEY,
    code                VARCHAR(40)  NOT NULL UNIQUE,
    libelle             VARCHAR(150) NOT NULL,
    categorie           VARCHAR(40)  NOT NULL,
    type_valeur         VARCHAR(20)  NOT NULL DEFAULT 'MONTANT',
    sens                VARCHAR(10)  NOT NULL,
    soumis_cnaps        BOOLEAN      NOT NULL DEFAULT TRUE,
    soumis_sante        BOOLEAN      NOT NULL DEFAULT TRUE,
    soumis_irsa         BOOLEAN      NOT NULL DEFAULT TRUE,
    est_fixe            BOOLEAN      NOT NULL DEFAULT FALSE,
    ordre_affichage     INT          NOT NULL DEFAULT 10,
    est_actif           BOOLEAN      NOT NULL DEFAULT TRUE,
    description         TEXT,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_rubrique_categorie CHECK (categorie IN (
        'SALAIRE_BASE', 'HEURES_SUP', 'PRIME', 'INDEMNITE_IMPOSABLE',
        'INDEMNITE_NON_IMPOSABLE', 'COTISATION_SALARIALE', 'COTISATION_PATRONALE',
        'IMPOT', 'ACOMPTE_AVANCE', 'RETENUE_DIVERSE', 'NET'
    )),
    CONSTRAINT chk_rubrique_type_valeur CHECK (type_valeur IN ('MONTANT', 'HEURES', 'JOURS', 'POURCENTAGE')),
    CONSTRAINT chk_rubrique_sens CHECK (sens IN ('GAIN', 'RETENUE', 'PATRONAL', 'INFO', 'NEUTRE'))
);

CREATE INDEX IF NOT EXISTS idx_paie_rubrique_categorie ON paie_rubrique(categorie);
CREATE INDEX IF NOT EXISTS idx_paie_rubrique_ordre ON paie_rubrique(ordre_affichage);

-- ------------------------------------------------------------------------------
-- 2. EN-TÊTE DU BULLETIN DE PAIE (bulletin_paie)
-- Snapshotting intégral de l'employé, des compteurs et des totaux financiers
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bulletin_paie (
    id_bulletin                     SERIAL PRIMARY KEY,
    id_periode_paie                 INT NOT NULL REFERENCES periode_paie(id_periode_paie),
    id_employe                      INT NOT NULL REFERENCES employe(id_employe),
    reference_bulletin              VARCHAR(60) NOT NULL UNIQUE,

    -- Snapshot Employé & Organisation au moment du calcul
    matricule                       VARCHAR(20)  NOT NULL,
    nom_prenom                      VARCHAR(150) NOT NULL,
    cin_numero                      VARCHAR(30),
    cin_date_delivrance             DATE,
    cin_lieu_delivrance             VARCHAR(100),
    numero_cnaps                    VARCHAR(30),
    intitule_poste                  VARCHAR(100) NOT NULL,
    nom_departement                 VARCHAR(100),
    nom_site                        VARCHAR(100) NOT NULL,
    regime_intervenant              VARCHAR(30)  NOT NULL, -- PERMANENT, CONSULTANT, TEMPORAIRE, STAGIAIRE
    categorie_professionnelle       VARCHAR(30),           -- CADRE, NON_CADRE, AGENT
    mode_calcul_paie                VARCHAR(30)  NOT NULL DEFAULT 'MENSUEL', -- MENSUEL, HORAIRE, JOURNALIER
    date_embauche                   DATE,
    anciennete_mois                 INT          DEFAULT 0,
    nb_enfants_charge               INT          DEFAULT 0, -- Enfants fiscalement éligibles à la date de paie
    mode_paiement                   VARCHAR(30)  DEFAULT 'VIREMENT',
    banque_nom                      VARCHAR(50),
    banque_rib                      VARCHAR(50),

    -- Snapshot Compteurs & Temps de Travail
    heures_normales                 NUMERIC(6,2) DEFAULT 0,
    heures_hs_30                    NUMERIC(6,2) DEFAULT 0,
    heures_hs_50                    NUMERIC(6,2) DEFAULT 0,
    heures_nuit                     NUMERIC(6,2) DEFAULT 0,
    jours_ouvrables                 NUMERIC(4,1) DEFAULT 0,
    jours_travailles                NUMERIC(4,1) DEFAULT 0,
    jours_absence                   NUMERIC(4,1) DEFAULT 0,
    jours_conges                    NUMERIC(4,1) DEFAULT 0,
    minutes_retard                  INT          DEFAULT 0,

    -- Rémunération Brute
    salaire_base                    NUMERIC(12,2) NOT NULL,
    taux_horaire                    NUMERIC(12,4) NOT NULL,
    total_gains_bruts               NUMERIC(12,2) NOT NULL DEFAULT 0, -- BRUTE

    -- Cotisations Sociales
    total_cotisations_salariales    NUMERIC(12,2) NOT NULL DEFAULT 0, -- CNaPS + Santé
    total_cotisations_patronales    NUMERIC(12,2) NOT NULL DEFAULT 0, -- CNaPS + Santé + FMFP

    -- Assiette Fiscale & Impôt
    salaire_net_imposable           NUMERIC(12,2) NOT NULL DEFAULT 0, -- NETI
    base_irsa_arrondie              NUMERIC(12,2) NOT NULL DEFAULT 0, -- Centaine inférieure
    irsa_brut                       NUMERIC(12,2) NOT NULL DEFAULT 0,
    reduction_enfants               NUMERIC(12,2) NOT NULL DEFAULT 0, -- Nb enfants x 2000 Ar
    irsa_net                        NUMERIC(12,2) NOT NULL DEFAULT 0, -- >= minimum_perception
    retenue_isi_5                   NUMERIC(12,2) NOT NULL DEFAULT 0, -- Si prestataire/consultant

    -- Indemnités Non Imposables (Ajouts Nets)
    total_indemnites_non_imposables NUMERIC(12,2) NOT NULL DEFAULT 0, -- Repas net, déplacement

    -- Retenues de Trésorerie (Acomptes, Avances, Prêts, Telecom)
    total_retenues_acompte_pret     NUMERIC(12,2) NOT NULL DEFAULT 0,

    -- Totaux Finaux
    net_avant_arrondi               NUMERIC(12,2) NOT NULL DEFAULT 0,
    net_a_payer                     NUMERIC(12,2) NOT NULL DEFAULT 0, -- MROUND(net, 10)
    cout_global_employeur           NUMERIC(12,2) NOT NULL DEFAULT 0, -- Brut + Patronal + Indemnités

    -- Cycle de Vie & Audit
    statut                          VARCHAR(20)  NOT NULL DEFAULT 'CALCULE',
    commentaire                     TEXT,
    date_calcul                     TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    calcule_par                     INT REFERENCES utilisateur(id_utilisateur),
    date_validation                 TIMESTAMP WITH TIME ZONE,
    valide_par                      INT REFERENCES utilisateur(id_utilisateur),
    date_cloture                    TIMESTAMP WITH TIME ZONE,
    cloture_par                     INT REFERENCES utilisateur(id_utilisateur),
    created_at                      TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at                      TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_bulletin_statut CHECK (statut IN ('CALCULE', 'VALIDE', 'CLOTURE', 'ANNULE')),
    CONSTRAINT uk_bulletin_employe_periode UNIQUE (id_periode_paie, id_employe)
);

CREATE INDEX IF NOT EXISTS idx_bulletin_periode ON bulletin_paie(id_periode_paie);
CREATE INDEX IF NOT EXISTS idx_bulletin_employe ON bulletin_paie(id_employe);
CREATE INDEX IF NOT EXISTS idx_bulletin_statut ON bulletin_paie(statut);

-- ------------------------------------------------------------------------------
-- 3. LIGNES DU BULLETIN DE PAIE (bulletin_paie_ligne)
-- Ventilation détaillée de chaque élément de gain ou retenue
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bulletin_paie_ligne (
    id_ligne            SERIAL PRIMARY KEY,
    id_bulletin         INT          NOT NULL REFERENCES bulletin_paie(id_bulletin) ON DELETE CASCADE,
    id_rubrique         INT          REFERENCES paie_rubrique(id_rubrique),
    code_rubrique       VARCHAR(40)  NOT NULL,
    libelle_rubrique    VARCHAR(150) NOT NULL,
    sens                VARCHAR(10)  NOT NULL,
    categorie           VARCHAR(40)  NOT NULL,
    base_calcul         NUMERIC(12,2),
    taux_salarial       NUMERIC(6,3),
    taux_patronal       NUMERIC(6,3),
    quantite            NUMERIC(8,2),
    montant_salarial    NUMERIC(12,2) NOT NULL DEFAULT 0,
    montant_patronal    NUMERIC(12,2) NOT NULL DEFAULT 0,
    ordre               INT          NOT NULL DEFAULT 10,
    imposable           BOOLEAN      NOT NULL DEFAULT TRUE,
    soumis_cotisation   BOOLEAN      NOT NULL DEFAULT TRUE,
    commentaire         VARCHAR(255),
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_ligne_sens CHECK (sens IN ('GAIN', 'RETENUE', 'PATRONAL', 'INFO'))
);

CREATE INDEX IF NOT EXISTS idx_bulletin_ligne_bulletin ON bulletin_paie_ligne(id_bulletin);
CREATE INDEX IF NOT EXISTS idx_bulletin_ligne_code ON bulletin_paie_ligne(code_rubrique);

-- ------------------------------------------------------------------------------
-- 4. GESTION DES AVANCES, ACOMPTES ET RETENUES DAF (paie_avance_acompte)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS paie_avance_acompte (
    id_avance           SERIAL PRIMARY KEY,
    id_employe          INT          NOT NULL REFERENCES employe(id_employe),
    id_periode_paie     INT          REFERENCES periode_paie(id_periode_paie),
    type_mouvement      VARCHAR(30)  NOT NULL,
    sens                VARCHAR(10)  NOT NULL DEFAULT 'RETENUE',
    montant             NUMERIC(12,2) NOT NULL,
    date_demande        DATE         NOT NULL DEFAULT CURRENT_DATE,
    date_accord         DATE,
    date_paiement       DATE,
    mode_reglement      VARCHAR(30)  DEFAULT 'VIREMENT',
    motif               VARCHAR(255) NOT NULL,
    statut              VARCHAR(20)  NOT NULL DEFAULT 'EN_ATTENTE',
    id_bulletin         INT          REFERENCES bulletin_paie(id_bulletin) ON DELETE SET NULL,
    reference_pret      VARCHAR(50),
    numero_echeance     INT,
    total_echeances     INT,
    cree_par            INT          REFERENCES utilisateur(id_utilisateur),
    approuve_par        INT          REFERENCES utilisateur(id_utilisateur),
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_avance_type CHECK (type_mouvement IN (
        'ACOMPTE_15', 'AVANCE_SPECIALE', 'ECHEANCE_PRET', 'CALL_PERSO',
        'NOTE_DE_FRAIS', 'PRIME_EXCEPTIONNELLE', 'RETENUE_DIVERSE'
    )),
    CONSTRAINT chk_avance_sens CHECK (sens IN ('RETENUE', 'GAIN')),
    CONSTRAINT chk_avance_montant CHECK (montant > 0),
    CONSTRAINT chk_avance_statut CHECK (statut IN (
        'EN_ATTENTE', 'APPROUVE', 'REJETE', 'IMPUTE_PAIE', 'ANNULE'
    ))
);

CREATE INDEX IF NOT EXISTS idx_paie_avance_employe ON paie_avance_acompte(id_employe);
CREATE INDEX IF NOT EXISTS idx_paie_avance_periode ON paie_avance_acompte(id_periode_paie);
CREATE INDEX IF NOT EXISTS idx_paie_avance_statut ON paie_avance_acompte(statut);

-- ------------------------------------------------------------------------------
-- 5. TRIGGER DE SÉCURITÉ : IMMUTABILITÉ POST-CLÔTURE
-- Interdit formellement toute altération ou suppression dès lors que le statut est CLOTURE
-- ------------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION fn_bulletin_paie_immutabilite()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.statut = 'CLOTURE' THEN
        RAISE EXCEPTION 'Action interdite : Le bulletin % est clôturé et strictement immuable.', OLD.reference_bulletin;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_bulletin_paie_immutabilite ON bulletin_paie;
CREATE TRIGGER trg_bulletin_paie_immutabilite
    BEFORE UPDATE OR DELETE ON bulletin_paie
    FOR EACH ROW
    EXECUTE FUNCTION fn_bulletin_paie_immutabilite();

CREATE OR REPLACE FUNCTION fn_bulletin_paie_ligne_immutabilite()
RETURNS TRIGGER AS $$
DECLARE
    v_statut VARCHAR(20);
    v_ref    VARCHAR(60);
    v_id     INT;
BEGIN
    v_id := COALESCE(NEW.id_bulletin, OLD.id_bulletin);
    SELECT statut, reference_bulletin INTO v_statut, v_ref FROM bulletin_paie WHERE id_bulletin = v_id;
    IF v_statut = 'CLOTURE' THEN
        RAISE EXCEPTION 'Action interdite : Les lignes du bulletin % sont immuables car le bulletin est clôturé.', v_ref;
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_bulletin_paie_ligne_immutabilite ON bulletin_paie_ligne;
CREATE TRIGGER trg_bulletin_paie_ligne_immutabilite
    BEFORE INSERT OR UPDATE OR DELETE ON bulletin_paie_ligne
    FOR EACH ROW
    EXECUTE FUNCTION fn_bulletin_paie_ligne_immutabilite();

-- ------------------------------------------------------------------------------
-- 6. AJOUT DE LA COTISATION PATRONALE FMFP (1%) DANS ref_type_cotisation
-- ------------------------------------------------------------------------------
INSERT INTO ref_type_cotisation (id_config_legale, code, libelle, taux_salarial, taux_patronal, plafonne, ordre, est_actif, date_debut)
SELECT id_config_legale, 'FMFP', 'Fonds Malgache de Formation Professionnelle', 0.00, 1.00, FALSE, 5, TRUE, '2026-01-01'
FROM paie_config_legale WHERE code_version = 'LF_2026'
ON CONFLICT (code) DO UPDATE
SET taux_patronal = 1.00, taux_salarial = 0.00, plafonne = FALSE, est_actif = TRUE;

-- ------------------------------------------------------------------------------
-- 7. SEEDING INITIAL DU CATALOGUE DES RUBRIQUES (paie_rubrique)
-- ------------------------------------------------------------------------------
INSERT INTO paie_rubrique (code, libelle, categorie, type_valeur, sens, soumis_cnaps, soumis_sante, soumis_irsa, est_fixe, ordre_affichage, description) VALUES
-- Rémunération de base & temps de travail
('SALAIRE_BASE',                 'Salaire de Base',                                'SALAIRE_BASE',            'MONTANT',     'GAIN',     TRUE,  TRUE,  TRUE,  TRUE,   10, 'Salaire mensuel contractuel de référence'),
('RETENUE_ABSENCE',              'Retenue sur Salaire pour Absences Décomptées',   'SALAIRE_BASE',            'JOURS',       'RETENUE',  TRUE,  TRUE,  TRUE,  TRUE,   20, 'Déduction 1/30ème par jour d''absence non rémunéré'),
('INDEMNITE_CONGE',              'Indemnité de Congés Payés (Maintien)',           'SALAIRE_BASE',            'JOURS',       'GAIN',     TRUE,  TRUE,  TRUE,  TRUE,   30, 'Valorisation 1/30ème des jours de congés payés validés'),

-- Heures supplémentaires & majorations
('HEURES_SUP_30',                'Heures Supplémentaires (+30%)',                  'HEURES_SUP',              'HEURES',      'GAIN',     TRUE,  TRUE,  TRUE,  TRUE,   40, 'Heures travaillées entre 41h et 48h'),
('HEURES_SUP_50',                'Heures Supplémentaires (+50%)',                  'HEURES_SUP',              'HEURES',      'GAIN',     TRUE,  TRUE,  TRUE,  TRUE,   50, 'Heures travaillées au-delà de 48h'),
('HEURES_NUIT',                  'Majoration Travail de Nuit (+30%)',              'HEURES_SUP',              'HEURES',      'GAIN',     TRUE,  TRUE,  TRUE,  TRUE,   60, 'Heures effectuées entre 22h et 05h'),
('HEURES_DIMANCHE',              'Majoration Travail Dimanche (+40%)',             'HEURES_SUP',              'HEURES',      'GAIN',     TRUE,  TRUE,  TRUE,  TRUE,   70, 'Heures travaillées le dimanche'),
('HEURES_FERIE',                 'Majoration Jour Férié (+100%)',                  'HEURES_SUP',              'HEURES',      'GAIN',     TRUE,  TRUE,  TRUE,  TRUE,   80, 'Heures travaillées un jour férié chômé'),

-- Primes & Ajustements
('PRIME_PERFORMANCE',            'Prime de Performance (Proratisée)',              'PRIME',                   'MONTANT',     'GAIN',     TRUE,  TRUE,  TRUE,  TRUE,   90, 'Prime au prorata des présences avec malus si retards/congés'),
('PRIME_ASSIDUITE',              'Prime d''Assiduité',                             'PRIME',                   'MONTANT',     'GAIN',     TRUE,  TRUE,  TRUE,  TRUE,  100, 'Prime accordée si retards cumulés < 45 min'),
('PRIME_EXCEPTIONNELLE',         'Prime Exceptionnelle',                           'PRIME',                   'MONTANT',     'GAIN',     TRUE,  TRUE,  TRUE,  FALSE, 110, 'Gratification ou prime exceptionnelle ponctuelle'),

-- Cotisations Sociales Salariales
('COTISATION_CNAPS_SAL',         'Cotisation CNaPS Salariale (1%)',                'COTISATION_SALARIALE',    'POURCENTAGE', 'RETENUE',  FALSE, FALSE, FALSE, TRUE,  200, 'Part salariale 1% plafonnée à 8x SME'),
('COTISATION_OSTIE_SAL',         'Cotisation OSTIE Salariale (1%)',                'COTISATION_SALARIALE',    'POURCENTAGE', 'RETENUE',  FALSE, FALSE, FALSE, TRUE,  210, 'Santé au travail Antananarivo - Part salariale'),
('COTISATION_OMSI_SAL',          'Cotisation OMSI Salariale (1.5%)',               'COTISATION_SALARIALE',    'POURCENTAGE', 'RETENUE',  FALSE, FALSE, FALSE, TRUE,  220, 'Santé au travail Toamasina - Part salariale'),

-- Cotisations Sociales Patronales
('COTISATION_CNAPS_PAT',         'Cotisation CNaPS Patronale (13%)',               'COTISATION_PATRONALE',    'POURCENTAGE', 'PATRONAL', FALSE, FALSE, FALSE, TRUE,  300, 'Part patronale 13% plafonnée à 8x SME'),
('COTISATION_OSTIE_PAT',         'Cotisation OSTIE Patronale (5%)',                'COTISATION_PATRONALE',    'POURCENTAGE', 'PATRONAL', FALSE, FALSE, FALSE, TRUE,  310, 'Santé au travail Antananarivo - Part patronale'),
('COTISATION_OMSI_PAT',          'Cotisation OMSI Patronale (5.5%)',               'COTISATION_PATRONALE',    'POURCENTAGE', 'PATRONAL', FALSE, FALSE, FALSE, TRUE,  320, 'Santé au travail Toamasina - Part patronale'),
('COTISATION_FMFP_PAT',          'Cotisation FMFP Patronale (1%)',                 'COTISATION_PATRONALE',    'POURCENTAGE', 'PATRONAL', FALSE, FALSE, FALSE, TRUE,  330, 'Formation professionnelle continue patronale'),

-- Fiscalité IRSA & ISI
('IMPOT_IRSA_BRUT',              'IRSA Brut (Barème Progressif 6 Tranches)',       'IMPOT',                   'MONTANT',     'INFO',     FALSE, FALSE, FALSE, TRUE,  400, 'IRSA calculé avant déduction pour charges de famille'),
('REDUCTION_CHARGE_FAMILLE',     'Réduction IRSA pour Charges de Famille',         'IMPOT',                   'MONTANT',     'INFO',     FALSE, FALSE, FALSE, TRUE,  410, 'Déduction fiscale de 2 000 Ar par enfant à charge éligible'),
('IMPOT_IRSA_NET',               'IRSA Net à Payer (Retenu à la Source)',          'IMPOT',                   'MONTANT',     'RETENUE',  FALSE, FALSE, FALSE, TRUE,  420, 'Impôt sur le revenu net déduit (respect du minimum légal)'),
('RETENUE_ISI_5',                'Retenue Libératoire ISI (5%)',                   'IMPOT',                   'POURCENTAGE', 'RETENUE',  FALSE, FALSE, FALSE, TRUE,  430, 'Impôt synthétique intermédiaire pour prestataires/consultants'),

-- Indemnités Net Exonérées (Ajouts au Net)
('INDEMNITE_TRANSPORT',          'Indemnité Forfaitaire de Déplacement',           'INDEMNITE_NON_IMPOSABLE', 'MONTANT',     'GAIN',     FALSE, FALSE, FALSE, TRUE,  500, 'Indemnité transport proratisée selon jours présents'),
('INDEMNITE_REPAS',              'Indemnité Forfaitaire de Repas',                 'INDEMNITE_NON_IMPOSABLE', 'MONTANT',     'GAIN',     FALSE, FALSE, FALSE, TRUE,  510, 'Repas midi 2 000 Ar par jour de présence validé (non imposable)'),
('REMBOURSEMENT_NOTE_FRAIS',     'Remboursement Notes de Frais Validées',          'INDEMNITE_NON_IMPOSABLE', 'MONTANT',     'GAIN',     FALSE, FALSE, FALSE, FALSE, 520, 'Remboursement des frais professionnels avancés'),

-- Retenues de Trésorerie DAF
('RETENUE_ACOMPTE_15',           'Acompte de Quinzaine (du 15)',                   'ACOMPTE_AVANCE',          'MONTANT',     'RETENUE',  FALSE, FALSE, FALSE, TRUE,  600, 'Acompte versé en milieu de mois'),
('RETENUE_AVANCE_SPECIALE',      'Avance Exceptionnelle sur Salaire',              'ACOMPTE_AVANCE',          'MONTANT',     'RETENUE',  FALSE, FALSE, FALSE, TRUE,  610, 'Avance ponctuelle accordée par la direction'),
('RETENUE_ECHEANCE_PRET',        'Échéance Remboursement Prêt Entreprise',         'RETENUE_DIVERSE',         'MONTANT',     'RETENUE',  FALSE, FALSE, FALSE, TRUE,  620, 'Mensualité de prêt interne accordé au salarié'),
('RETENUE_CALL_PERSO',           'Retenue Télécommunications Privées (Call)',      'RETENUE_DIVERSE',         'MONTANT',     'RETENUE',  FALSE, FALSE, FALSE, TRUE,  630, 'Facturation flotte mobile communications personnelles'),

-- Solde Tout Compte (STC) & Régularisations
('INDEMNITE_COMPENSATRICE_CONGE','Indemnité Compensatrice de Congés Payés (ICCP)', 'SALAIRE_BASE',            'JOURS',       'GAIN',     TRUE,  TRUE,  TRUE,  FALSE, 700, 'Indemnité de solde de congés non pris à la rupture'),
('INDEMNITE_PREAVIS',            'Indemnité Compensatrice de Préavis',             'SALAIRE_BASE',            'MONTANT',     'GAIN',     TRUE,  TRUE,  TRUE,  FALSE, 710, 'Indemnité de préavis non exécuté'),
('INDEMNITE_LICENCIEMENT',       'Indemnité de Licenciement',                      'INDEMNITE_NON_IMPOSABLE', 'MONTANT',     'GAIN',     FALSE, FALSE, FALSE, FALSE, 720, 'Indemnité de rupture légale ou conventionnelle'),
('REGULARISATION_PAIE',          'Régularisation sur Mois Antérieur',              'RETENUE_DIVERSE',         'MONTANT',     'NEUTRE',   TRUE,  TRUE,  TRUE,  FALSE, 730, 'Ajustement positif ou négatif régularisé')

ON CONFLICT (code) DO UPDATE
SET libelle = EXCLUDED.libelle,
    categorie = EXCLUDED.categorie,
    type_valeur = EXCLUDED.type_valeur,
    sens = EXCLUDED.sens,
    soumis_cnaps = EXCLUDED.soumis_cnaps,
    soumis_sante = EXCLUDED.soumis_sante,
    soumis_irsa = EXCLUDED.soumis_irsa,
    ordre_affichage = EXCLUDED.ordre_affichage,
    description = EXCLUDED.description;
