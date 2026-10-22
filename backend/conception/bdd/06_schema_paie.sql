-- =====================================================================
-- GALAXAM SIRH — MODULE 06 : MOTEUR DE PAIE & QUITTANCES
-- Script canonique final (ALTER TABLE pour les dépendances différées, Zéro OWNER, Zéro public.)
-- Précision Ar : montants NUMERIC(20,2), taux horaires NUMERIC(20,8)
-- (NUMERIC(20,4) pour le taux STC), bases de ligne NUMERIC(20,8).
-- Les durées et pourcentages gardent leur précision propre.
-- Sources : 26-09-02-sp4-paie.sql, 26-09-08-sp4-paie-bulletin.sql,
--           26-09-10-sp4-paie-ajustements-audit.sql,
--           26-09-11-sp4-paie-stc.sql,
--           26-09-12-sp4-paie-variables-dynamiques.sql, 26-09-13-sp4-conge-valorisation-droits.sql,
--           26-09-15-sp7-regularisation-conge-mouvement.sql, 26-09-16-sp8-indexes-foreign-keys.sql
-- Installation initiale uniquement sur une base ou un schéma cible vierge.
-- Exécuter les scripts 01 → 06 dans cet ordre ; ne pas utiliser pour une mise à jour
-- ni pour une réinitialisation.
-- =====================================================================

-- Configuration Légale & Fiscale (Millésimes annuels de paie)
CREATE TABLE paie_config_legale (
    id_config_legale SERIAL PRIMARY KEY,
    code_version VARCHAR(30) NOT NULL UNIQUE,
    libelle VARCHAR(200) NOT NULL,
    date_debut DATE NOT NULL,
    date_fin DATE,
    sme_national NUMERIC(20,2) NOT NULL,
    sme_agricole NUMERIC(20,2) NOT NULL,
    plafond_cotisation_nb_sme NUMERIC(4,2) NOT NULL DEFAULT 8.00,
    plafond_avance_employeur_pct NUMERIC(9,6) NOT NULL DEFAULT 10.000000,
    quotite_cessible_pct NUMERIC(20,6) NOT NULL DEFAULT 33.33,
    seuil_exoneration_irsa NUMERIC(20,2) NOT NULL,
    minimum_perception_irsa NUMERIC(20,2) NOT NULL,
    deduction_charge_famille NUMERIC(20,2) NOT NULL,
    arrondi_base_irsa VARCHAR(20) NOT NULL DEFAULT 'CENTAINE_INF',
    duree_hebdo_legale NUMERIC(5,2) NOT NULL DEFAULT 40.00,
    diviseur_mensuel_heures NUMERIC(7,4) NOT NULL DEFAULT 173.33,
    seuil_hs_tranche1 NUMERIC(5,2) NOT NULL DEFAULT 8.00,
    plafond_hs_hebdo NUMERIC(5,2) NOT NULL DEFAULT 20.00,
    acquisition_conge_mensuel_jours NUMERIC(4,2) NOT NULL DEFAULT 2.50,
    exoneration_hs_irsa_active BOOLEAN NOT NULL DEFAULT FALSE,
    plafond_mensuel_hs_exonerees NUMERIC(5,2) NOT NULL DEFAULT 0.00,
    source_type VARCHAR(50) DEFAULT 'CGI_MADAGASCAR',
    source_reference VARCHAR(255),
    article_reference VARCHAR(80) DEFAULT '01.03.16',
    publication_date DATE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_dates_config CHECK (date_fin IS NULL OR date_fin > date_debut),
    CONSTRAINT chk_plafond_avance_pct CHECK (plafond_avance_employeur_pct BETWEEN 0 AND 100),
    CONSTRAINT chk_quotite_cessible_pct CHECK (quotite_cessible_pct BETWEEN 0 AND 100),
    CONSTRAINT uk_config_legale_temporal_overlap EXCLUDE USING gist (
        daterange(date_debut, COALESCE(date_fin, 'infinity'::date), '[]') WITH &&
    )
);


-- 2. Organismes Sociaux & Cotisations Sociales
CREATE TABLE ref_organisme_social (
    id_organisme SERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    libelle VARCHAR(150) NOT NULL,
    categorie VARCHAR(30) NOT NULL DEFAULT 'SANTE' CHECK (categorie IN ('PREVOYANCE', 'SANTE', 'FORMATION', 'AUTRE')),
    est_actif BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);


CREATE TABLE ref_type_cotisation (
    id_type_cotisation SERIAL PRIMARY KEY,
    id_config_legale INT REFERENCES paie_config_legale(id_config_legale) ON DELETE CASCADE,
    id_organisme INT REFERENCES ref_organisme_social(id_organisme),
    code VARCHAR(20) NOT NULL UNIQUE,
    libelle VARCHAR(100) NOT NULL,
    taux_salarial NUMERIC(5,2),
    taux_patronal NUMERIC(5,2),
    plafonne BOOLEAN DEFAULT TRUE,
    ordre INT DEFAULT 1,
    est_actif BOOLEAN DEFAULT TRUE,
    date_debut DATE NOT NULL DEFAULT '2026-01-01',
    date_fin DATE,
    portee VARCHAR(20) DEFAULT 'SITE' CHECK (portee IN ('SITE', 'ENTREPRISE')),
    categorie VARCHAR(30) DEFAULT 'SANTE' CHECK (categorie IN ('PREVOYANCE', 'SANTE', 'FORMATION'))
);


CREATE TABLE site_cotisation (
    id_site_cotisation SERIAL PRIMARY KEY,
    id_site INT NOT NULL REFERENCES ref_site(id_site) ON DELETE CASCADE,
    id_type_cotisation INT NOT NULL REFERENCES ref_type_cotisation(id_type_cotisation) ON DELETE CASCADE,
    id_organisme INT REFERENCES ref_organisme_social(id_organisme),
    est_actif BOOLEAN DEFAULT TRUE,
    est_plafonne_sante BOOLEAN DEFAULT TRUE,
    CONSTRAINT uk_site_cotisation UNIQUE (id_site, id_type_cotisation)
);


-- 3. Barèmes IRSA & Majorations Légales
CREATE TABLE bareme_irsa (
    id_tranche SERIAL PRIMARY KEY,
    id_config_legale INT REFERENCES paie_config_legale(id_config_legale) ON DELETE CASCADE,
    numero_tranche INT NOT NULL,
    tranche_min NUMERIC(20,2) NOT NULL,
    tranche_max NUMERIC(20,2),
    taux NUMERIC(5,2) NOT NULL,
    date_debut DATE NOT NULL,
    date_fin DATE
);


CREATE TABLE ref_taux_majoration (
    id_taux_majoration SERIAL PRIMARY KEY,
    id_config_legale INT REFERENCES paie_config_legale(id_config_legale) ON DELETE CASCADE,
    type_majoration VARCHAR(20) NOT NULL,
    taux NUMERIC(5,2) NOT NULL,
    description TEXT,
    date_debut DATE NOT NULL,
    date_fin DATE
);


-- 4. Catalogue des Rubriques & Types de Mouvements Variables
CREATE TABLE paie_rubrique (
    id_rubrique SERIAL PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    libelle VARCHAR(150) NOT NULL,
    categorie VARCHAR(40) NOT NULL,
    type_valeur VARCHAR(20) NOT NULL DEFAULT 'MONTANT',
    sens VARCHAR(10) NOT NULL,
    soumis_cnaps BOOLEAN NOT NULL,
    soumis_sante BOOLEAN NOT NULL,
    soumis_irsa BOOLEAN NOT NULL,
    est_fixe BOOLEAN NOT NULL DEFAULT FALSE,
    ordre_affichage INT NOT NULL DEFAULT 10,
    est_actif BOOLEAN NOT NULL DEFAULT TRUE,
    description TEXT,
    reference_reglementaire TEXT,
    date_debut_effet DATE,
    methode_calcul VARCHAR(32) NOT NULL DEFAULT 'MONTANT' CONSTRAINT chk_rubrique_methode CHECK(methode_calcul IN ('MONTANT','QUANTITE_TAUX','POURCENTAGE_SALAIRE_BASE')),
    politique_arrondi VARCHAR(16) NOT NULL DEFAULT 'CENTIME' CONSTRAINT chk_rubrique_arrondi CHECK(politique_arrondi IN ('CENTIME','ARIARY','DIZAINE')),
    soumis_fmfp BOOLEAN,
    date_fin_effet DATE,
    id_rubrique_origine INT REFERENCES paie_rubrique(id_rubrique),
    condition_application VARCHAR(30) NOT NULL DEFAULT 'TOUS'
        CONSTRAINT chk_rubrique_condition CHECK (condition_application IN ('TOUS','ASSUJETTI_IRSA','ASSUJETTI_CNAPS','ASSUJETTI_SANTE')),
    CONSTRAINT chk_rubrique_validite CHECK (date_fin_effet IS NULL OR date_debut_effet IS NULL OR date_fin_effet >= date_debut_effet),
    etape_calcul VARCHAR(20) CONSTRAINT chk_rubrique_etape_calcul CHECK (etape_calcul IN ('BRUT', 'GAIN_NET', 'RETENUE_NET')),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_rubrique_categorie CHECK (categorie IN (
        'SALAIRE_BASE', 'HEURES_SUP', 'PRIME', 'INDEMNITE_IMPOSABLE',
        'INDEMNITE_NON_IMPOSABLE', 'COTISATION_SALARIALE', 'COTISATION_PATRONALE',
        'IMPOT', 'ACOMPTE_AVANCE', 'RETENUE_DIVERSE', 'NET'
    )),
    CONSTRAINT chk_rubrique_type_valeur CHECK (type_valeur IN ('MONTANT', 'HEURES', 'JOURS', 'POURCENTAGE')),
    CONSTRAINT chk_rubrique_sens CHECK (sens IN ('GAIN', 'RETENUE', 'PATRONAL', 'INFO', 'NEUTRE'))
);


CREATE TABLE ref_type_mouvement_paie (
    id_type_mouvement SERIAL PRIMARY KEY,
    code VARCHAR(40) UNIQUE NOT NULL,
    libelle VARCHAR(150) NOT NULL,
    sens VARCHAR(10) NOT NULL CHECK (sens IN ('GAIN', 'RETENUE')),
    categorie VARCHAR(40) NOT NULL,
    id_rubrique INT REFERENCES paie_rubrique(id_rubrique),
    est_proratise BOOLEAN DEFAULT FALSE,
    est_plafonne_dixieme BOOLEAN DEFAULT FALSE,
    nature VARCHAR(30) NOT NULL DEFAULT 'VARIABLE_DAF',
    owner_code VARCHAR(30) NOT NULL DEFAULT 'DAF_PAIE',
    saisie_manuelle_autorisee BOOLEAN NOT NULL DEFAULT TRUE,
    periode_obligatoire BOOLEAN NOT NULL DEFAULT TRUE,
    motif_obligatoire BOOLEAN NOT NULL DEFAULT TRUE,
    provenance_defaut VARCHAR(20) NOT NULL DEFAULT 'VARIABLE_DAF',
    est_actif BOOLEAN DEFAULT TRUE,
    ordre_affichage INT DEFAULT 10,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);


-- 4b. Référentiels Typologiques de Paie & Règlements
CREATE TABLE ref_mode_paiement (
    id_mode_paiement SERIAL PRIMARY KEY,
    code VARCHAR(30) UNIQUE NOT NULL,
    libelle VARCHAR(100) NOT NULL,
    exige_rib BOOLEAN DEFAULT FALSE,
    inclus_fichier_virement BOOLEAN DEFAULT FALSE,
    actif BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 5. En-tête des Bulletins de Paie (avec audit de réouverture inline)
CREATE TABLE bulletin_paie (
    id_bulletin SERIAL PRIMARY KEY,
    id_periode_paie INT NOT NULL REFERENCES periode_paie(id_periode_paie),
    id_employe INT NOT NULL REFERENCES employe(id_employe),
    id_config_legale INT REFERENCES paie_config_legale(id_config_legale),
    id_snapshot_pointage INT REFERENCES pointage_cumul_periode(id_cumul),
    version_snapshot_pointage INT CHECK (version_snapshot_pointage IS NULL OR version_snapshot_pointage > 0),
    type_bulletin VARCHAR(20) NOT NULL DEFAULT 'MENSUEL',
    reference_bulletin VARCHAR(60) NOT NULL UNIQUE,
    matricule VARCHAR(20) NOT NULL,
    nom_prenom VARCHAR(150) NOT NULL,
    cin_numero VARCHAR(30),
    cin_date_delivrance DATE,
    cin_lieu_delivrance VARCHAR(100),
    numero_cnaps VARCHAR(30),
    intitule_poste VARCHAR(100) NOT NULL,
    nom_departement VARCHAR(100),
    nom_site VARCHAR(100) NOT NULL,
    regime_intervenant VARCHAR(30) NOT NULL,
    categorie_professionnelle VARCHAR(30),
    mode_calcul_paie VARCHAR(30) NOT NULL DEFAULT 'MENSUEL',
    date_embauche DATE,
    anciennete_mois INT DEFAULT 0,
    nb_enfants_charge INT DEFAULT 0,
    mode_paiement VARCHAR(30) DEFAULT 'VIREMENT',
    id_mode_paiement INT REFERENCES ref_mode_paiement(id_mode_paiement),
    banque_nom VARCHAR(50),
    banque_rib VARCHAR(50),
    heures_normales NUMERIC(12,8) DEFAULT 0,
    heures_hs_30 NUMERIC(12,8) DEFAULT 0,
    heures_hs_50 NUMERIC(12,8) DEFAULT 0,
    heures_nuit NUMERIC(12,8) DEFAULT 0,
    jours_ouvrables NUMERIC(4,1) DEFAULT 0,
    jours_travailles NUMERIC(4,1) DEFAULT 0,
    jours_absence NUMERIC(4,1) DEFAULT 0,
    jours_conges NUMERIC(4,1) DEFAULT 0,
    minutes_retard INT DEFAULT 0,
    salaire_base NUMERIC(20,2) NOT NULL,
    taux_horaire NUMERIC(20,8) NOT NULL,
    total_gains_bruts NUMERIC(20,2) NOT NULL DEFAULT 0,
    total_cotisations_salariales NUMERIC(20,2) NOT NULL DEFAULT 0,
    total_cotisations_patronales NUMERIC(20,2) NOT NULL DEFAULT 0,
    salaire_net_imposable NUMERIC(20,2) NOT NULL DEFAULT 0,
    base_irsa_arrondie NUMERIC(20,2) NOT NULL DEFAULT 0,
    irsa_brut NUMERIC(20,2) NOT NULL DEFAULT 0,
    reduction_enfants NUMERIC(20,2) NOT NULL DEFAULT 0,
    irsa_net NUMERIC(20,2) NOT NULL DEFAULT 0,
    retenue_isi_5 NUMERIC(20,2) NOT NULL DEFAULT 0,
    total_indemnites_non_imposables NUMERIC(20,2) NOT NULL DEFAULT 0,
    total_retenues_avances_acomptes NUMERIC(20,2) NOT NULL DEFAULT 0,
    net_avant_arrondi NUMERIC(20,2) NOT NULL DEFAULT 0,
    net_a_payer NUMERIC(20,2) NOT NULL DEFAULT 0,
    cout_global_employeur NUMERIC(20,2) NOT NULL DEFAULT 0,
    statut VARCHAR(20) NOT NULL DEFAULT 'CALCULE',
    motif_reouverture TEXT,
    date_reouverture TIMESTAMP WITH TIME ZONE,
    rouvert_par INT REFERENCES utilisateur(id_utilisateur),
    commentaire TEXT,
    date_calcul TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    calcule_par INT REFERENCES utilisateur(id_utilisateur),
    date_validation TIMESTAMP WITH TIME ZONE,
    valide_par INT REFERENCES utilisateur(id_utilisateur),
    date_cloture TIMESTAMP WITH TIME ZONE,
    cloture_par INT REFERENCES utilisateur(id_utilisateur),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_bulletin_statut CHECK (statut IN ('CALCULE', 'VALIDE', 'CLOTURE', 'ROUVERT', 'ANNULE')),
    CONSTRAINT chk_bulletin_type_bulletin CHECK (type_bulletin IN ('MENSUEL', 'STC', 'COMPLEMENTAIRE', 'RAPPEL', 'GRATIFICATION')),
    CONSTRAINT uk_bulletin_employe_periode_type UNIQUE (id_periode_paie, id_employe, type_bulletin)
);


CREATE TABLE paie_bulletin_virement_lot (
    id_lot BIGSERIAL PRIMARY KEY,
    id_periode_paie INT NOT NULL REFERENCES periode_paie(id_periode_paie),
    statut VARCHAR(20) NOT NULL DEFAULT 'PREPARE' CHECK (statut IN ('PREPARE', 'TRANSMIS', 'ANNULE')),
    cree_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cree_par INT REFERENCES utilisateur(id_utilisateur),
    transmis_at TIMESTAMPTZ,
    transmis_par INT REFERENCES utilisateur(id_utilisateur)
);
CREATE TABLE paie_bulletin_virement_ligne (
    id_ligne BIGSERIAL PRIMARY KEY,
    id_lot BIGINT NOT NULL REFERENCES paie_bulletin_virement_lot(id_lot),
    id_bulletin INT NOT NULL REFERENCES bulletin_paie(id_bulletin),
    montant NUMERIC(20,2) NOT NULL CHECK (montant > 0),
    beneficiaire VARCHAR(150) NOT NULL,
    banque VARCHAR(100) NOT NULL,
    rib VARCHAR(100) NOT NULL,
    statut VARCHAR(20) NOT NULL DEFAULT 'EN_ATTENTE' CHECK (statut IN ('EN_ATTENTE', 'CONFIRME', 'REJETE', 'ANNULE')),
    reference_banque VARCHAR(255),
    date_execution DATE,
    motif_rejet TEXT,
    traite_at TIMESTAMPTZ,
    traite_par INT REFERENCES utilisateur(id_utilisateur),
    UNIQUE (id_lot, id_bulletin)
);
CREATE TABLE paie_bulletin_reglement (
    id_reglement BIGSERIAL PRIMARY KEY,
    id_bulletin INT NOT NULL REFERENCES bulletin_paie(id_bulletin),
    id_ligne_virement BIGINT UNIQUE REFERENCES paie_bulletin_virement_ligne(id_ligne),
    montant NUMERIC(20,2) NOT NULL CHECK (montant > 0),
    mode_reglement VARCHAR(20) NOT NULL CHECK (mode_reglement IN ('VIREMENT', 'CHEQUE', 'ESPECES', 'MOBILE_MONEY')),
    date_reglement DATE NOT NULL,
    reference_reglement VARCHAR(255) NOT NULL,
    commentaire TEXT,
    nom_justificatif VARCHAR(255) NOT NULL,
    mime_justificatif VARCHAR(30) NOT NULL CHECK (mime_justificatif IN ('application/pdf', 'image/png', 'image/jpeg')),
    contenu_justificatif BYTEA NOT NULL,
    taille_justificatif INT NOT NULL CHECK (taille_justificatif BETWEEN 1 AND 5242880),
    cree_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cree_par INT REFERENCES utilisateur(id_utilisateur),
    annule_at TIMESTAMPTZ,
    annule_par INT REFERENCES utilisateur(id_utilisateur),
    motif_annulation TEXT
);

-- Identité de l'employeur conservée à la validation pour les réimpressions.
CREATE TABLE bulletin_paie_employeur_snapshot (
    id_bulletin INT PRIMARY KEY REFERENCES bulletin_paie(id_bulletin) ON DELETE CASCADE,
    id_filiale INT NOT NULL,
    raison_sociale VARCHAR(200) NOT NULL,
    nif VARCHAR(100),
    stat VARCHAR(100),
    adresse_siege TEXT,
    telephone VARCHAR(100),
    email VARCHAR(255),
    captured_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 6. Lignes du Bulletin de Paie
CREATE TABLE bulletin_paie_ligne (
    id_ligne SERIAL PRIMARY KEY,
    id_bulletin INT NOT NULL REFERENCES bulletin_paie(id_bulletin) ON DELETE CASCADE,
    id_rubrique INT REFERENCES paie_rubrique(id_rubrique),
    id_organisme INT REFERENCES ref_organisme_social(id_organisme),
    code_organisme VARCHAR(30),
    id_type_cotisation INT REFERENCES ref_type_cotisation(id_type_cotisation),
    code_rubrique VARCHAR(40) NOT NULL,
    libelle_rubrique VARCHAR(150) NOT NULL,
    sens VARCHAR(10) NOT NULL,
    categorie VARCHAR(40) NOT NULL,
    base_calcul NUMERIC(20,8),
    taux_salarial NUMERIC(20,6),
    taux_patronal NUMERIC(20,6),
    quantite NUMERIC(24,8),
    montant_salarial NUMERIC(20,2) NOT NULL DEFAULT 0,
    montant_patronal NUMERIC(20,2) NOT NULL DEFAULT 0,
    ordre INT NOT NULL DEFAULT 10,
    imposable BOOLEAN NOT NULL DEFAULT TRUE,
    soumis_cotisation BOOLEAN NOT NULL DEFAULT TRUE,
    etape_calcul VARCHAR(20) CONSTRAINT chk_ligne_etape_calcul CHECK (etape_calcul IN ('BRUT', 'GAIN_NET', 'RETENUE_NET')),
    commentaire VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_ligne_sens CHECK (sens IN ('GAIN', 'RETENUE', 'PATRONAL', 'INFO'))
);


-- 7. Avances, Acomptes et Variables DAF
CREATE TABLE paie_avance_acompte (
    id_avance SERIAL PRIMARY KEY,
    id_employe INT NOT NULL REFERENCES employe(id_employe),
    id_periode_paie INT REFERENCES periode_paie(id_periode_paie),
    type_mouvement VARCHAR(40) NOT NULL,
    id_type_mouvement INT REFERENCES ref_type_mouvement_paie(id_type_mouvement),
    id_conge_cutover_correction INT REFERENCES conge_cutover_correction(id_correction),
    sens VARCHAR(10) NOT NULL DEFAULT 'RETENUE',
    quantite_saisie NUMERIC(20,6),
    taux_saisi NUMERIC(20,6),
    montant NUMERIC(20,2) NOT NULL,
    montant_initial NUMERIC(20,2) NOT NULL,
    montant_impute NUMERIC(20,2) NOT NULL DEFAULT 0,
    solde_restant NUMERIC(20,2) NOT NULL,
    provenance VARCHAR(20) NOT NULL DEFAULT 'VARIABLE_DAF',
    date_demande DATE NOT NULL DEFAULT CURRENT_DATE,
    date_accord DATE,
    date_paiement DATE,
    versement_confirme_at TIMESTAMP WITH TIME ZONE,
    versement_confirme_par INT REFERENCES utilisateur(id_utilisateur),
    reference_versement VARCHAR(120),
    mode_reglement VARCHAR(30) DEFAULT 'VIREMENT',
    motif VARCHAR(255) NOT NULL,
    statut VARCHAR(20) NOT NULL DEFAULT 'EN_ATTENTE',
    id_bulletin INT REFERENCES bulletin_paie(id_bulletin) ON DELETE SET NULL,
    cree_par INT REFERENCES utilisateur(id_utilisateur),
    approuve_par INT REFERENCES utilisateur(id_utilisateur),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_avance_sens CHECK (sens IN ('RETENUE', 'GAIN')),
    CONSTRAINT chk_avance_montant CHECK (montant > 0),
    CONSTRAINT chk_avance_imputation CHECK (montant_initial > 0 AND montant_impute >= 0 AND solde_restant >= 0 AND montant_impute + solde_restant = montant_initial),
    CONSTRAINT chk_avance_statut CHECK (statut IN ('EN_ATTENTE', 'APPROUVE', 'REJETE', 'IMPUTE_PAIE', 'ANNULE'))
);

CREATE TABLE paie_mouvement_imputation (
    id_imputation SERIAL PRIMARY KEY,
    id_avance INT NOT NULL REFERENCES paie_avance_acompte(id_avance) ON DELETE RESTRICT,
    id_bulletin INT NOT NULL REFERENCES bulletin_paie(id_bulletin) ON DELETE CASCADE,
    montant NUMERIC(20,2) NOT NULL CHECK (montant > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_mouvement_imputation_bulletin UNIQUE (id_avance, id_bulletin)
);
CREATE TABLE paie_mouvement_justificatif (
    id_avance INT PRIMARY KEY REFERENCES paie_avance_acompte(id_avance) ON DELETE CASCADE,
    nom_fichier VARCHAR(255) NOT NULL,
    type_mime VARCHAR(60) NOT NULL,
    contenu BYTEA NOT NULL,
    taille_octets BIGINT NOT NULL CHECK (taille_octets > 0 AND taille_octets <= 5242880),
    depose_par INT REFERENCES utilisateur(id_utilisateur),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Prestataires indépendants : circuit contractuel et factures hors paie.
CREATE TABLE prestataire_independant (
    id_prestataire SERIAL PRIMARY KEY,
    nom VARCHAR(200) NOT NULL,
    nif VARCHAR(50) NOT NULL UNIQUE CHECK (BTRIM(nif) <> ''),
    stat VARCHAR(50),
    adresse TEXT,
    email VARCHAR(200),
    est_actif BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE prestataire_contrat (
    id_contrat_prestataire SERIAL PRIMARY KEY,
    id_prestataire INTEGER NOT NULL REFERENCES prestataire_independant(id_prestataire),
    reference VARCHAR(100) NOT NULL UNIQUE,
    objet TEXT NOT NULL,
    date_debut DATE NOT NULL,
    date_fin DATE,
    tarif_convenu NUMERIC(20,2),
    chemin_piece VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_prestataire_contrat_dates CHECK (date_fin IS NULL OR date_fin >= date_debut)
);
CREATE TABLE prestataire_facture (
    id_facture_prestataire SERIAL PRIMARY KEY,
    id_contrat_prestataire INTEGER NOT NULL REFERENCES prestataire_contrat(id_contrat_prestataire),
    reference VARCHAR(100) NOT NULL,
    date_facture DATE NOT NULL,
    montant NUMERIC(20,2) NOT NULL CHECK (montant > 0),
    chemin_piece VARCHAR(500),
    statut VARCHAR(20) NOT NULL DEFAULT 'BROUILLON'
        CHECK (statut IN ('BROUILLON','VALIDEE','PAYEE','ANNULEE')),
    valide_par INTEGER,
    date_validation TIMESTAMPTZ,
    date_paiement DATE,
    reference_paiement VARCHAR(150),
    paye_par INTEGER,
    motif_annulation TEXT,
    annule_par INTEGER,
    date_annulation TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_prestataire_facture_piece CHECK (statut NOT IN ('VALIDEE','PAYEE') OR chemin_piece IS NOT NULL),
    CONSTRAINT chk_prestataire_facture_paiement CHECK (statut <> 'PAYEE' OR
        (date_paiement IS NOT NULL AND reference_paiement IS NOT NULL AND date_paiement >= date_facture)),
    CONSTRAINT chk_prestataire_facture_annulation CHECK (statut <> 'ANNULEE' OR
        (motif_annulation IS NOT NULL AND annule_par IS NOT NULL AND date_annulation IS NOT NULL)),
    UNIQUE (id_contrat_prestataire, reference)
);

-- 10. Solde de tout compte (STC) et décompte détaillé
CREATE SEQUENCE IF NOT EXISTS seq_numero_recu_stc START WITH 1 INCREMENT BY 1;

CREATE TABLE paie_stc (
    id_stc SERIAL PRIMARY KEY,
    id_employe INT NOT NULL REFERENCES employe(id_employe),
    id_contrat INT REFERENCES contrat_travail(id_contrat),
    source_pointage VARCHAR(30),
    id_bulletin_mensuel INT REFERENCES bulletin_paie(id_bulletin),
    net_bulletin_mensuel NUMERIC(20,2) NOT NULL DEFAULT 0,
    montant_bulletin_deja_paye NUMERIC(20,2) NOT NULL DEFAULT 0,
    solde_bulletin_a_regler NUMERIC(20,2) NOT NULL DEFAULT 0,
    date_paiement_bulletin DATE,
    reference_paiement_bulletin VARCHAR(255),
    id_periode_paie INT NOT NULL REFERENCES periode_paie(id_periode_paie),
    id_bulletin INT REFERENCES bulletin_paie(id_bulletin) ON DELETE SET NULL,
    numero_recu VARCHAR(60) NOT NULL UNIQUE,
    date_depart DATE NOT NULL,
    motif_rupture VARCHAR(50) NOT NULL,
    salaire_base NUMERIC(20,2) NOT NULL,
    taux_horaire NUMERIC(20,4) NOT NULL,
    anciennete_mois INT NOT NULL DEFAULT 0,
    jours_travailles_dernier_mois NUMERIC(4,1) NOT NULL DEFAULT 0,
    salaire_prorata NUMERIC(20,2) NOT NULL DEFAULT 0,
    solde_conges_restant NUMERIC(6,2) NOT NULL DEFAULT 0,
    taux_journalier_conge NUMERIC(20,2) NOT NULL DEFAULT 0,
    indemnite_conges_payes NUMERIC(20,2) NOT NULL DEFAULT 0,
    mois_preavis NUMERIC(4,2) NOT NULL DEFAULT 0,
    indemnite_preavis NUMERIC(20,2) NOT NULL DEFAULT 0,
    indemnite_licenciement NUMERIC(20,2) NOT NULL DEFAULT 0,
    autres_indemnites NUMERIC(20,2) NOT NULL DEFAULT 0,
    motif_autres_indemnites VARCHAR(255),
    total_brut_stc NUMERIC(20,2) NOT NULL DEFAULT 0,
    total_cotisations_sociales NUMERIC(20,2) NOT NULL DEFAULT 0,
    total_irsa NUMERIC(20,2) NOT NULL DEFAULT 0,
    net_imposable NUMERIC(20,2) NOT NULL DEFAULT 0,
    solde_avances_deduit NUMERIC(20,2) NOT NULL DEFAULT 0,
    autres_deductions NUMERIC(20,2) NOT NULL DEFAULT 0,
    motif_autres_deductions VARCHAR(255),
    net_a_payer_stc NUMERIC(20,2) NOT NULL DEFAULT 0,
    statut VARCHAR(20) NOT NULL DEFAULT 'SIMULATION',
    mode_reglement VARCHAR(30) DEFAULT 'VIREMENT',
    id_mode_reglement INT REFERENCES ref_mode_paiement(id_mode_paiement),
    id_motif_sortie INT REFERENCES ref_motif_sortie(id_motif_sortie),
    commentaire TEXT,
    cree_par INT REFERENCES utilisateur(id_utilisateur),
    valide_par INT REFERENCES utilisateur(id_utilisateur),
    date_validation TIMESTAMP WITH TIME ZONE,
    date_reglement DATE,
    reference_reglement VARCHAR(255),
    regle_par INT REFERENCES utilisateur(id_utilisateur),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_stc_statut CHECK (statut IN ('SIMULATION', 'VALIDE', 'REGLE', 'ANNULE'))
);


-- 11. Dossier administratif de sortie et journal des règlements STC
CREATE TABLE paie_dossier_sortie (
    id_dossier_sortie SERIAL PRIMARY KEY,
    id_employe INT NOT NULL REFERENCES employe(id_employe),
    id_contrat INT REFERENCES contrat_travail(id_contrat),
    date_depart DATE NOT NULL,
    motif_rupture VARCHAR(50) NOT NULL,
    statut_dossier VARCHAR(30) NOT NULL DEFAULT 'EN_PREPARATION',
    donnees_preparation JSONB,
    certificat_delivre BOOLEAN DEFAULT FALSE,
    date_delivrance_certificat DATE,
    mode_remise_certificat VARCHAR(30),
    recu_stc_remis BOOLEAN DEFAULT FALSE,
    date_remise_recu DATE,
    id_stc INT UNIQUE REFERENCES paie_stc(id_stc) ON DELETE SET NULL,
    cree_par INT REFERENCES utilisateur(id_utilisateur),
    cloture_par INT REFERENCES utilisateur(id_utilisateur),
    date_cloture TIMESTAMP WITH TIME ZONE,
    motif_cloture TEXT,
    derogation_reglement BOOLEAN DEFAULT FALSE,
    justification_derogation TEXT,
    autorite_decision_derogation VARCHAR(100),
    reference_decision_derogation VARCHAR(100),
    date_decision_derogation DATE,
    version INT NOT NULL DEFAULT 1,
    reference_simulation VARCHAR(100),
    snapshot_simulation JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_dossier_sortie_statut CHECK (statut_dossier IN ('EN_PREPARATION', 'PRET_A_VALIDER', 'VALIDE', 'REGLE', 'CLOTURE', 'ANNULE')),
    CONSTRAINT chk_dossier_derogation_gouvernance CHECK (derogation_reglement IS NOT TRUE OR (
        autorite_decision_derogation IS NOT NULL AND TRIM(autorite_decision_derogation) <> '' AND
        reference_decision_derogation IS NOT NULL AND TRIM(reference_decision_derogation) <> '' AND
        date_decision_derogation IS NOT NULL
    )),
    CONSTRAINT uk_dossier_sortie_employe_depart UNIQUE (id_employe, date_depart)
);


CREATE TABLE paie_stc_reglement (
    id_reglement SERIAL PRIMARY KEY,
    id_stc INT NOT NULL,
    date_reglement DATE NOT NULL,
    montant_regle NUMERIC(20,2) NOT NULL CHECK (montant_regle >= 0),
    mode_reglement VARCHAR(30) NOT NULL DEFAULT 'VIREMENT',
    reference_reglement VARCHAR(255) NOT NULL,
    commentaire TEXT,
    nom_justificatif VARCHAR(255),
    mime_justificatif VARCHAR(30),
    contenu_justificatif BYTEA,
    taille_justificatif INT,
    est_annule BOOLEAN NOT NULL DEFAULT FALSE,
    annule_par INT REFERENCES utilisateur(id_utilisateur),
    date_annulation TIMESTAMP WITH TIME ZONE,
    motif_annulation TEXT,
    cle_idempotence VARCHAR(64),
    enregistre_par INT REFERENCES utilisateur(id_utilisateur),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT paie_stc_reglement_id_stc_fkey
        FOREIGN KEY (id_stc) REFERENCES paie_stc(id_stc) ON DELETE RESTRICT
);

-- Contraintes différées entre les règlements de bulletin et de STC
ALTER TABLE paie_bulletin_reglement
    ADD COLUMN id_stc_reglement INT UNIQUE REFERENCES paie_stc_reglement(id_reglement);


-- Index complémentaires
CREATE INDEX idx_config_legale_actif ON paie_config_legale(date_debut, date_fin) WHERE date_fin IS NULL;
CREATE INDEX idx_organisme_social_code ON ref_organisme_social(code);
CREATE INDEX idx_organisme_social_categorie ON ref_organisme_social(categorie);
CREATE INDEX idx_type_cotisation_config ON ref_type_cotisation(id_config_legale);
CREATE INDEX idx_type_cotisation_organisme ON ref_type_cotisation(id_organisme);
CREATE INDEX idx_site_cotisation_type ON site_cotisation(id_type_cotisation);
CREATE INDEX idx_site_cotisation_organisme ON site_cotisation(id_organisme);
CREATE INDEX idx_bareme_irsa_config ON bareme_irsa(id_config_legale);
CREATE UNIQUE INDEX uk_bareme_irsa_config_numero_debut
    ON bareme_irsa(id_config_legale, numero_tranche, date_debut);
CREATE INDEX idx_taux_majoration_config ON ref_taux_majoration(id_config_legale);
CREATE INDEX idx_paie_rubrique_categorie ON paie_rubrique(categorie);
CREATE INDEX idx_paie_rubrique_ordre ON paie_rubrique(ordre_affichage);
CREATE INDEX idx_ref_type_mvt_code ON ref_type_mouvement_paie(code);
CREATE INDEX idx_ref_type_mvt_sens ON ref_type_mouvement_paie(sens);
CREATE INDEX idx_ref_type_mouvement_paie_rubrique ON ref_type_mouvement_paie(id_rubrique);
CREATE INDEX idx_rubrique_versions ON paie_rubrique ((COALESCE(id_rubrique_origine,id_rubrique)),date_debut_effet);
CREATE INDEX idx_bulletin_periode ON bulletin_paie(id_periode_paie);
CREATE INDEX idx_bulletin_employe ON bulletin_paie(id_employe);
CREATE INDEX idx_bulletin_statut ON bulletin_paie(statut);
CREATE INDEX idx_bulletin_snapshot_pointage ON bulletin_paie(id_snapshot_pointage);
CREATE INDEX idx_bulletin_paie_calcule_par ON bulletin_paie(calcule_par);
CREATE INDEX idx_bulletin_paie_valide_par ON bulletin_paie(valide_par);
CREATE INDEX idx_bulletin_paie_cloture_par ON bulletin_paie(cloture_par);
CREATE INDEX idx_bulletin_paie_rouvert_par ON bulletin_paie(rouvert_par);
CREATE UNIQUE INDEX uk_paie_virement_bulletin_en_attente ON paie_bulletin_virement_ligne(id_bulletin) WHERE statut = 'EN_ATTENTE';
CREATE INDEX idx_paie_bulletin_reglement_actif ON paie_bulletin_reglement(id_bulletin) WHERE annule_at IS NULL;
CREATE UNIQUE INDEX uk_paie_bulletin_reglement_reference_active ON paie_bulletin_reglement(id_bulletin, mode_reglement, reference_reglement) WHERE annule_at IS NULL;
CREATE INDEX idx_bulletin_ligne_bulletin ON bulletin_paie_ligne(id_bulletin);
CREATE INDEX idx_bulletin_ligne_code ON bulletin_paie_ligne(code_rubrique);
CREATE INDEX idx_bulletin_paie_ligne_rubrique ON bulletin_paie_ligne(id_rubrique);
CREATE INDEX idx_bulletin_ligne_organisme ON bulletin_paie_ligne(id_organisme);
CREATE INDEX idx_bulletin_ligne_code_organisme ON bulletin_paie_ligne(code_organisme);
CREATE INDEX idx_bulletin_ligne_type_cotisation ON bulletin_paie_ligne(id_type_cotisation);
CREATE UNIQUE INDEX uk_paie_regularisation_conge_cutover_active
    ON paie_avance_acompte(id_conge_cutover_correction)
    WHERE id_conge_cutover_correction IS NOT NULL AND statut NOT IN ('REJETE', 'ANNULE');
CREATE INDEX idx_mouvement_imputation_bulletin ON paie_mouvement_imputation(id_bulletin);
CREATE INDEX idx_paie_avance_employe ON paie_avance_acompte(id_employe);
CREATE INDEX idx_paie_avance_periode ON paie_avance_acompte(id_periode_paie);
CREATE INDEX idx_paie_avance_type_mvt ON paie_avance_acompte(id_type_mouvement);
CREATE INDEX idx_paie_avance_statut ON paie_avance_acompte(statut);
CREATE INDEX idx_paie_avance_acompte_cree_par ON paie_avance_acompte(cree_par);
CREATE INDEX idx_paie_avance_acompte_approuve_par ON paie_avance_acompte(approuve_par);
CREATE INDEX idx_paie_avance_acompte_bulletin ON paie_avance_acompte(id_bulletin);
CREATE INDEX idx_prestataire_facture_statut ON prestataire_facture(statut);
CREATE INDEX idx_paie_stc_employe ON paie_stc(id_employe);
CREATE INDEX idx_paie_stc_periode ON paie_stc(id_periode_paie);
CREATE INDEX idx_paie_stc_statut ON paie_stc(statut);
CREATE INDEX idx_paie_stc_mode_reglement ON paie_stc(id_mode_reglement);
CREATE INDEX idx_paie_stc_motif_sortie ON paie_stc(id_motif_sortie);
CREATE INDEX idx_paie_stc_cree_par ON paie_stc(cree_par);
CREATE INDEX idx_paie_stc_valide_par ON paie_stc(valide_par);
CREATE INDEX idx_paie_stc_bulletin ON paie_stc(id_bulletin);
CREATE INDEX idx_dossier_sortie_employe ON paie_dossier_sortie(id_employe);
CREATE INDEX idx_dossier_sortie_statut ON paie_dossier_sortie(statut_dossier);
CREATE INDEX idx_dossier_sortie_contrat ON paie_dossier_sortie(id_contrat);
CREATE INDEX idx_dossier_sortie_stc ON paie_dossier_sortie(id_stc);
CREATE INDEX idx_stc_reglement_stc ON paie_stc_reglement(id_stc);
CREATE INDEX idx_stc_reglement_date ON paie_stc_reglement(date_reglement);
CREATE UNIQUE INDEX uk_stc_reglement_stc_ref_active ON paie_stc_reglement(id_stc, reference_reglement) WHERE est_annule = FALSE;
CREATE UNIQUE INDEX uk_stc_reglement_idempotence ON paie_stc_reglement(id_stc, cle_idempotence) WHERE cle_idempotence IS NOT NULL;

-- Fonctions et triggers des rubriques et avances
-- Sérialise les changements d’une même famille et interdit les versions ambiguës.
CREATE OR REPLACE FUNCTION verifier_versions_rubrique() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE famille INT;
BEGIN
 famille := COALESCE(NEW.id_rubrique_origine, NEW.id_rubrique);
 PERFORM pg_advisory_xact_lock(46001, famille);
 IF NEW.id_rubrique_origine IS NOT NULL AND (NEW.id_rubrique_origine=NEW.id_rubrique OR
    NOT EXISTS (SELECT 1 FROM paie_rubrique WHERE id_rubrique=NEW.id_rubrique_origine AND id_rubrique_origine IS NULL)) THEN
    RAISE EXCEPTION 'La version doit référencer la rubrique d’origine';
 END IF;
 IF EXISTS (SELECT 1 FROM paie_rubrique r WHERE COALESCE(r.id_rubrique_origine,r.id_rubrique)=famille
   AND r.id_rubrique<>NEW.id_rubrique AND daterange(r.date_debut_effet,r.date_fin_effet,'[]')
       && daterange(NEW.date_debut_effet,NEW.date_fin_effet,'[]')) THEN
   RAISE EXCEPTION 'Chevauchement des versions de rubrique';
 END IF;
 RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS trg_versions_rubrique ON paie_rubrique;
CREATE TRIGGER trg_versions_rubrique BEFORE INSERT OR UPDATE OF date_debut_effet,date_fin_effet,id_rubrique_origine
ON paie_rubrique FOR EACH ROW EXECUTE FUNCTION verifier_versions_rubrique();


-- Suivi financier distinct du statut de validation du bulletin.

CREATE OR REPLACE FUNCTION initialize_paie_avance_imputation()
RETURNS TRIGGER AS $$
BEGIN
    NEW.montant_initial := COALESCE(NEW.montant_initial, NEW.montant);
    NEW.montant_impute := COALESCE(NEW.montant_impute, 0);
    NEW.solde_restant := COALESCE(NEW.solde_restant, NEW.montant_initial - NEW.montant_impute);
    IF NEW.provenance IS NULL THEN
        SELECT provenance_defaut INTO NEW.provenance
        FROM ref_type_mouvement_paie
        WHERE id_type_mouvement = NEW.id_type_mouvement OR code = NEW.type_mouvement
        ORDER BY CASE WHEN id_type_mouvement = NEW.id_type_mouvement THEN 0 ELSE 1 END
        LIMIT 1;
        NEW.provenance := COALESCE(NEW.provenance, 'VARIABLE_DAF');
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_initialize_paie_avance_imputation
BEFORE INSERT ON paie_avance_acompte
FOR EACH ROW EXECUTE FUNCTION initialize_paie_avance_imputation();



-- =====================================================================
-- TRIGGERS DE SÉCURITÉ : IMMUTABILITÉ POST-CLÔTURE
-- =====================================================================

CREATE OR REPLACE FUNCTION fn_bulletin_paie_immutabilite()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.statut = 'CLOTURE' THEN
            RAISE EXCEPTION 'Action interdite : Suppression impossible d''un bulletin clôturé (%).', OLD.reference_bulletin;
        END IF;
        RETURN OLD;
    END IF;

    IF OLD.statut = 'CLOTURE' THEN
        IF NEW.statut = 'ROUVERT' AND NEW.motif_reouverture IS NOT NULL AND LENGTH(TRIM(NEW.motif_reouverture)) >= 5 THEN
            NEW.date_reouverture := CURRENT_TIMESTAMP;
            RETURN NEW;
        ELSE
            RAISE EXCEPTION 'Action interdite : Le bulletin % est clôturé et strictement immuable. Pour le réouvrir exceptionnellement, renseignez un motif_reouverture valide.', OLD.reference_bulletin;
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_bulletin_paie_immutabilite
    BEFORE UPDATE OR DELETE ON bulletin_paie
    FOR EACH ROW
    EXECUTE FUNCTION fn_bulletin_paie_immutabilite();

CREATE OR REPLACE FUNCTION fn_bulletin_paie_ligne_immutabilite()
RETURNS TRIGGER AS $$
DECLARE
    v_statut VARCHAR(20);
    v_ref VARCHAR(60);
    v_id INT;
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

CREATE TRIGGER trg_bulletin_paie_ligne_immutabilite
    BEFORE INSERT OR UPDATE OR DELETE ON bulletin_paie_ligne
    FOR EACH ROW
    EXECUTE FUNCTION fn_bulletin_paie_ligne_immutabilite();

-- =====================================================================
-- DONNÉES DE RÉFÉRENCE DE BASE (INITIAL SEEDS DES ARCHIVES)
-- =====================================================================

-- Configuration Légale de Paie :
-- 1. LF_2024_2025 (applicable du 01/01/2024 au 31/12/2025) : SME 262 680 Ar, Plafond CNaPS 2 101 440 Ar, Barème 5 tranches (couvre les tests déc 2025 : 26 nov - 25 déc)
-- 2. LF_2026_JAN_FEB (applicable du 01/01/2026 au 28/02/2026) : Décret CNaPS SME 262 680 Ar maintenu jusqu'au 28/02/2026, Barème IRSA 6 tranches LF 2026 (Min IRSA 3 000 Ar, tranche 25%)
-- 3. LF_2026 (en vigueur depuis le 01/03/2026) : Revalorisation effective SME 300 000 Ar (agricole 304 300 Ar), Plafond CNaPS 2 400 000 Ar, Barème 6 tranches (25%), Min IRSA 3 000 Ar
INSERT INTO paie_config_legale (
    id_config_legale, code_version, libelle, date_debut, date_fin,
    sme_national, sme_agricole, plafond_cotisation_nb_sme,
    seuil_exoneration_irsa, minimum_perception_irsa, deduction_charge_famille, arrondi_base_irsa,
    duree_hebdo_legale, diviseur_mensuel_heures, seuil_hs_tranche1, plafond_hs_hebdo,
    acquisition_conge_mensuel_jours,
    exoneration_hs_irsa_active, plafond_mensuel_hs_exonerees,
    source_type, source_reference, article_reference, publication_date
) VALUES
(
    1,
    'LF_2024_2025',
    'Loi de Finances / Réglementation 2022-2025 — SME 262 680 Ar, Plafond CNaPS 2 101 440 Ar',
    '2001-07-01', '2025-12-31',
    262680.00, 266500.00, 8.00,
    350000.00, 2000.00, 2000.00, 'CENTAINE_INF',
    40.00, 173.33, 8.00, 20.00,
    2.50,
    FALSE, 20.00,
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
    2.50,
    FALSE, 20.00,
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
    2.50,
    FALSE, 20.00,
    'CGI_MADAGASCAR', 'Loi de finances 2026', '01.03.16', '2026-02-15'
);
SELECT setval('paie_config_legale_id_config_legale_seq', (SELECT GREATEST(MAX(id_config_legale), 3) FROM paie_config_legale));


-- Modes de paiement
INSERT INTO ref_mode_paiement (code, libelle, exige_rib, inclus_fichier_virement, actif)
VALUES
    ('VIREMENT', 'Virement Bancaire', TRUE, TRUE, TRUE),
    ('CHEQUE', 'Chèque Bancaire', FALSE, FALSE, TRUE),
    ('ESPECES', 'Espèces / Caisse', FALSE, FALSE, TRUE),
    ('MOBILE_MONEY', 'Mobile Money (Mvola/Orange/Airtel)', FALSE, FALSE, TRUE)
ON CONFLICT (code) DO NOTHING;


-- Organismes Sociaux Déclaratifs
INSERT INTO ref_organisme_social (id_organisme, code, libelle, categorie, est_actif) VALUES
(1, 'CNAPS', 'Caisse Nationale de Prévoyance Sociale', 'PREVOYANCE', TRUE),
(2, 'OSTIE', 'Organisation Sanitaire Tananarivienne', 'SANTE', TRUE),
(3, 'OMSI',  'Organisation Médicale Inter-entreprises', 'SANTE', TRUE),
(4, 'FMFP',  'Fonds Malgache de Formation Professionnelle', 'FORMATION', TRUE),
(5, 'AMIT',  'Association Médicale Inter-entreprises de Tuléar', 'SANTE', TRUE);
SELECT setval('ref_organisme_social_id_organisme_seq', (SELECT GREATEST(MAX(id_organisme), 5) FROM ref_organisme_social));

-- Cotisations Sociales Référentielles
INSERT INTO ref_type_cotisation (id_type_cotisation, id_config_legale, id_organisme, code, libelle, taux_salarial, taux_patronal, plafonne, ordre, est_actif, date_debut, portee, categorie) VALUES
(1, 2, 1, 'CNAPS', 'Caisse Nationale de Prévoyance Sociale', 1.00, 13.00, TRUE, 1, TRUE, '2001-07-01', 'ENTREPRISE', 'PREVOYANCE'),
(2, 2, 2, 'OSTIE', 'Organisation Sanitaire Tananarivienne', 1.00, 5.00, TRUE, 1, TRUE, '2001-07-01', 'SITE', 'SANTE'),
(3, 2, 3, 'OMSI',  'Organisation Médicale Inter-entreprises', 1.50, 5.50, TRUE, 1, TRUE, '2001-07-01', 'SITE', 'SANTE'),
(4, 2, 4, 'FMFP',  'Fonds Malgache de Formation Professionnelle', 0.00, 1.00, FALSE, 4, TRUE, '2001-07-01', 'ENTREPRISE', 'FORMATION');
SELECT setval('ref_type_cotisation_id_type_cotisation_seq', (SELECT GREATEST(MAX(id_type_cotisation), 4) FROM ref_type_cotisation));

-- Cotisations par Site (TNR: CNAPS+OSTIE, TMM: CNAPS+OMSI)
INSERT INTO site_cotisation (id_site, id_type_cotisation, id_organisme, est_plafonne_sante) VALUES
(1, 1, 1, TRUE),
(1, 2, 2, TRUE),
(2, 1, 1, TRUE),
(2, 3, 3, TRUE);

-- Barèmes IRSA Officiels (LF_2024_2025: 5 Tranches jusqu'au 31/12/2025, LF_2026: 6 Tranches dès le 01/01/2026)
INSERT INTO bareme_irsa (id_config_legale, numero_tranche, tranche_min, tranche_max, taux, date_debut, date_fin) VALUES
(1, 1, 0.00, 350000.00, 0.00, '2001-07-01', '2025-12-31'),
(1, 2, 350000.00, 400000.00, 5.00, '2001-07-01', '2025-12-31'),
(1, 3, 400000.00, 500000.00, 10.00, '2001-07-01', '2025-12-31'),
(1, 4, 500000.00, 600000.00, 15.00, '2001-07-01', '2025-12-31'),
(1, 5, 600000.00, NULL, 20.00, '2001-07-01', '2025-12-31'),
(3, 1, 0.00, 350000.00, 0.00, '2026-01-01', '2026-02-28'),
(3, 2, 350000.00, 400000.00, 5.00, '2026-01-01', '2026-02-28'),
(3, 3, 400000.00, 500000.00, 10.00, '2026-01-01', '2026-02-28'),
(3, 4, 500000.00, 600000.00, 15.00, '2026-01-01', '2026-02-28'),
(3, 5, 600000.00, 4000000.00, 20.00, '2026-01-01', '2026-02-28'),
(3, 6, 4000000.00, NULL, 25.00, '2026-01-01', '2026-02-28'),
(2, 1, 0.00, 350000.00, 0.00, '2026-03-01', NULL),
(2, 2, 350000.00, 400000.00, 5.00, '2026-03-01', NULL),
(2, 3, 400000.00, 500000.00, 10.00, '2026-03-01', NULL),
(2, 4, 500000.00, 600000.00, 15.00, '2026-03-01', NULL),
(2, 5, 600000.00, 4000000.00, 20.00, '2026-03-01', NULL),
(2, 6, 4000000.00, NULL, 25.00, '2026-03-01', NULL);
SELECT setval('bareme_irsa_id_tranche_seq', (SELECT GREATEST(MAX(id_tranche), 17) FROM bareme_irsa));

-- Majorations d'Heures Supplémentaires & Spéciales (LF_2024_2025 et LF_2026)
INSERT INTO ref_taux_majoration (id_config_legale, type_majoration, taux, description, date_debut, date_fin) VALUES
-- Ancien barème (2001 - 2025) - LF_2024_2025 (ID 1)
(1, 'HS_30', 0.30, '41ᵉ à 48ᵉ heure de la semaine (+30%)', '2001-07-01', '2025-12-31'),
(1, 'HS_50', 0.50, 'Au-delà de la 48ᵉ heure (+50%)', '2001-07-01', '2025-12-31'),
(1, 'NUIT', 0.30, 'Heures de nuit (22h - 05h) (+30%)', '2001-07-01', '2025-12-31'),
(1, 'DIMANCHE', 0.40, 'Travail le dimanche (+40%)', '2001-07-01', '2025-12-31'),
(1, 'FERIE', 1.00, 'Travail jour férié (+100%)', '2001-07-01', '2025-12-31'),

-- Nouveau barème 2026 (Période transitoire Janv-Févr 2026 - ID 3)
(3, 'HS_30', 0.30, '41ᵉ à 48ᵉ heure de la semaine (+30%)', '2026-01-01', '2026-02-28'),
(3, 'HS_50', 0.50, 'Au-delà de la 48ᵉ heure (+50%)', '2026-01-01', '2026-02-28'),
(3, 'NUIT', 0.30, 'Heures de nuit (22h - 05h) (+30%)', '2026-01-01', '2026-02-28'),
(3, 'DIMANCHE', 0.40, 'Travail le dimanche (+40%)', '2026-01-01', '2026-02-28'),
(3, 'FERIE', 1.00, 'Travail jour férié (+100%)', '2026-01-01', '2026-02-28'),

-- Nouveau barème 2026 actif dès Mars 2026 (ID 2)
(2, 'HS_30', 0.30, '41ᵉ à 48ᵉ heure de la semaine (+30%)', '2026-03-01', NULL),
(2, 'HS_50', 0.50, 'Au-delà de la 48ᵉ heure (+50%)', '2026-03-01', NULL),
(2, 'NUIT', 0.30, 'Heures de nuit (22h - 05h) (+30%)', '2026-03-01', NULL),
(2, 'DIMANCHE', 0.40, 'Travail le dimanche (+40%)', '2026-03-01', NULL),
(2, 'FERIE', 1.00, 'Travail jour férié (+100%)', '2026-03-01', NULL);
SELECT setval('ref_taux_majoration_id_taux_majoration_seq', (SELECT GREATEST(MAX(id_taux_majoration), 15) FROM ref_taux_majoration));

-- Catalogue des Rubriques de Paie (Plan Standard 37 Rubriques Canoniques)
INSERT INTO paie_rubrique (id_rubrique, code, libelle, categorie, type_valeur, sens, soumis_cnaps, soumis_sante, soumis_irsa, est_fixe, ordre_affichage, description) VALUES
(1, 'SALAIRE_BASE', 'Salaire de Base', 'SALAIRE_BASE', 'MONTANT', 'GAIN', TRUE, TRUE, TRUE, TRUE, 10, 'Salaire mensuel contractuel de référence'),
(2, 'RETENUE_ABSENCE', 'Retenue sur Salaire pour Absences Décomptées', 'SALAIRE_BASE', 'JOURS', 'RETENUE', TRUE, TRUE, TRUE, TRUE, 20, 'Déduction 1/30ème par jour d''absence non rémunéré'),
(3, 'RETENUE_CONGE', 'Retenue sur Salaire pour Congés Pris (1/30ème)', 'SALAIRE_BASE', 'JOURS', 'RETENUE', TRUE, TRUE, TRUE, TRUE, 25, 'Déduction 1/30ème pour jours de congés indemnisés par INDEMNITE_CONGE'),
(4, 'INDEMNITE_CONGE', 'Indemnité de Congés Payés (Maintien)', 'SALAIRE_BASE', 'JOURS', 'GAIN', TRUE, TRUE, TRUE, TRUE, 30, 'Valorisation 1/30ème des jours de congés payés validés'),
(5, 'HEURES_SUP_30', 'Heures Supplémentaires (+30%)', 'HEURES_SUP', 'HEURES', 'GAIN', TRUE, TRUE, TRUE, TRUE, 40, 'Heures travaillées entre 41h et 48h'),
(6, 'HEURES_SUP_50', 'Heures Supplémentaires (+50%)', 'HEURES_SUP', 'HEURES', 'GAIN', TRUE, TRUE, TRUE, TRUE, 50, 'Heures travaillées au-delà de 48h'),
(7, 'HEURES_NUIT', 'Majoration Travail de Nuit (+30%)', 'HEURES_SUP', 'HEURES', 'GAIN', TRUE, TRUE, TRUE, TRUE, 60, 'Heures effectuées entre 22h et 05h'),
(8, 'HEURES_DIMANCHE', 'Majoration Travail Dimanche (+40%)', 'HEURES_SUP', 'HEURES', 'GAIN', TRUE, TRUE, TRUE, TRUE, 70, 'Heures travaillées le dimanche'),
(9, 'HEURES_FERIE', 'Majoration Jour Férié (+100%)', 'HEURES_SUP', 'HEURES', 'GAIN', TRUE, TRUE, TRUE, TRUE, 80, 'Heures travaillées un jour férié chômé'),
(10, 'PRIME_PERFORMANCE', 'Prime de Performance (Proratisée)', 'PRIME', 'MONTANT', 'GAIN', TRUE, TRUE, TRUE, TRUE, 90, 'Prime au prorata des présences avec malus si retards/congés'),
(11, 'PRIME_ASSIDUITE', 'Prime d''Assiduité', 'PRIME', 'MONTANT', 'GAIN', TRUE, TRUE, TRUE, TRUE, 100, 'Prime accordée si retards cumulés < 45 min'),
(12, 'PRIME_EXCEPTIONNELLE', 'Prime Exceptionnelle', 'PRIME', 'MONTANT', 'GAIN', TRUE, TRUE, TRUE, FALSE, 110, 'Gratification ou prime exceptionnelle ponctuelle'),
(13, 'COTISATION_CNAPS_SAL', 'Cotisation CNaPS Salariale (1%)', 'COTISATION_SALARIALE', 'POURCENTAGE', 'RETENUE', FALSE, FALSE, FALSE, TRUE, 200, 'Part salariale 1% plafonnée à 8x SME'),
(14, 'COTISATION_OSTIE_SAL', 'Cotisation OSTIE Salariale (1%)', 'COTISATION_SALARIALE', 'POURCENTAGE', 'RETENUE', FALSE, FALSE, FALSE, TRUE, 210, 'Santé au travail Antananarivo - Part salariale'),
(15, 'COTISATION_OMSI_SAL', 'Cotisation OMSI Salariale (1.5%)', 'COTISATION_SALARIALE', 'POURCENTAGE', 'RETENUE', FALSE, FALSE, FALSE, TRUE, 220, 'Santé au travail Toamasina - Part salariale'),
(16, 'COTISATION_CNAPS_PAT', 'Cotisation CNaPS Patronale (13%)', 'COTISATION_PATRONALE', 'POURCENTAGE', 'PATRONAL', FALSE, FALSE, FALSE, TRUE, 300, 'Part patronale 13% plafonnée à 8x SME'),
(17, 'COTISATION_OSTIE_PAT', 'Cotisation OSTIE Patronale (5%)', 'COTISATION_PATRONALE', 'POURCENTAGE', 'PATRONAL', FALSE, FALSE, FALSE, TRUE, 310, 'Santé au travail Antananarivo - Part patronale'),
(18, 'COTISATION_OMSI_PAT', 'Cotisation OMSI Patronale (5.5%)', 'COTISATION_PATRONALE', 'POURCENTAGE', 'PATRONAL', FALSE, FALSE, FALSE, TRUE, 320, 'Santé au travail Toamasina - Part patronale'),
(19, 'COTISATION_FMFP_PAT', 'Cotisation FMFP Patronale (1%)', 'COTISATION_PATRONALE', 'POURCENTAGE', 'PATRONAL', FALSE, FALSE, FALSE, TRUE, 330, 'Formation professionnelle continue patronale'),
(20, 'IMPOT_IRSA_BRUT', 'IRSA Brut (Barème Progressif 6 Tranches)', 'IMPOT', 'MONTANT', 'INFO', FALSE, FALSE, FALSE, TRUE, 400, 'IRSA calculé avant déduction pour charges de famille'),
(21, 'REDUCTION_CHARGE_FAMILLE', 'Réduction IRSA pour Charges de Famille', 'IMPOT', 'MONTANT', 'INFO', FALSE, FALSE, FALSE, TRUE, 410, 'Déduction fiscale de 2 000 Ar par enfant à charge éligible'),
(22, 'IMPOT_IRSA_NET', 'IRSA Net à Payer (Retenu à la Source)', 'IMPOT', 'MONTANT', 'RETENUE', FALSE, FALSE, FALSE, TRUE, 420, 'Impôt sur le revenu net déduit (respect du minimum légal)'),
(23, 'RETENUE_ISI_5', 'Ancienne retenue ISI (inactive)', 'IMPOT', 'POURCENTAGE', 'RETENUE', FALSE, FALSE, FALSE, TRUE, 430, 'Historique uniquement : ISI général supprimé par la loi de finances 2026'),
(24, 'INDEMNITE_TRANSPORT', 'Indemnité Forfaitaire de Déplacement', 'INDEMNITE_IMPOSABLE', 'MONTANT', 'GAIN', TRUE, TRUE, TRUE, TRUE, 500, 'Indemnité transport proratisée selon jours présents'),
(25, 'INDEMNITE_REPAS', 'Indemnité Forfaitaire de Repas', 'INDEMNITE_IMPOSABLE', 'MONTANT', 'GAIN', TRUE, TRUE, TRUE, TRUE, 510, 'Repas midi 2 000 Ar par jour de présence validé'),
(26, 'REMBOURSEMENT_NOTE_FRAIS', 'Remboursement Notes de Frais Validées', 'INDEMNITE_NON_IMPOSABLE', 'MONTANT', 'GAIN', FALSE, FALSE, FALSE, FALSE, 520, 'Remboursement des frais professionnels avancés'),
(27, 'RETENUE_ACOMPTE_15', 'Acompte de Quinzaine (du 15)', 'ACOMPTE_AVANCE', 'MONTANT', 'RETENUE', FALSE, FALSE, FALSE, TRUE, 600, 'Acompte versé en milieu de mois'),
(28, 'RETENUE_AVANCE_SPECIALE', 'Avance Exceptionnelle sur Salaire', 'ACOMPTE_AVANCE', 'MONTANT', 'RETENUE', FALSE, FALSE, FALSE, TRUE, 610, 'Avance ponctuelle accordée par la direction'),
(30, 'RETENUE_CALL_PERSO', 'Retenue Télécommunications Privées (Call)', 'RETENUE_DIVERSE', 'MONTANT', 'RETENUE', FALSE, FALSE, FALSE, TRUE, 630, 'Facturation flotte mobile communications personnelles'),
(31, 'INDEMNITE_COMPENSATRICE_CONGE', 'Indemnité Compensatrice de Congés Payés (ICCP)', 'SALAIRE_BASE', 'JOURS', 'GAIN', TRUE, TRUE, TRUE, FALSE, 700, 'Indemnité de solde de congés non pris à la rupture'),
(32, 'INDEMNITE_PREAVIS', 'Indemnité Compensatrice de Préavis', 'SALAIRE_BASE', 'MONTANT', 'GAIN', TRUE, TRUE, TRUE, FALSE, 710, 'Indemnité de préavis non exécuté'),
(33, 'INDEMNITE_LICENCIEMENT', 'Indemnité de Licenciement', 'INDEMNITE_NON_IMPOSABLE', 'MONTANT', 'GAIN', FALSE, FALSE, FALSE, FALSE, 720, 'Indemnité de rupture légale ou conventionnelle'),
(34, 'REGULARISATION_PAIE', 'Régularisation sur Mois Antérieur', 'RETENUE_DIVERSE', 'MONTANT', 'NEUTRE', TRUE, TRUE, TRUE, FALSE, 730, 'Ajustement positif ou négatif régularisé'),
(35, 'AUTIMP', 'Autres Gains Imposables', 'PRIME', 'MONTANT', 'GAIN', TRUE, TRUE, TRUE, FALSE, 740, 'Autres primes brutes imposables'),
(36, 'EXTRA_DEPLACEMENT', 'Indemnité de Déplacement Extra', 'INDEMNITE_NON_IMPOSABLE', 'MONTANT', 'GAIN', FALSE, FALSE, FALSE, FALSE, 750, 'Extra déplacement ou mission exceptionnelle non imposable'),
(37, 'AJUSTEMENT_ARRONDI', 'Ajustement Arrondi Monétaire (Net)', 'NET', 'MONTANT', 'NEUTRE', FALSE, FALSE, FALSE, TRUE, 999, 'Écart monétaire d''arrondi pour équilibre parfait'),
(38, 'RETENUE_DIVERSE', 'Retenue exceptionnelle', 'RETENUE_DIVERSE', 'MONTANT', 'RETENUE', FALSE, FALSE, FALSE, FALSE, 640, 'Retenue nette manuelle motivée');

SELECT setval('paie_rubrique_id_rubrique_seq', (SELECT GREATEST(MAX(id_rubrique), 38) FROM paie_rubrique));
UPDATE paie_rubrique SET est_actif = FALSE WHERE code = 'RETENUE_ISI_5';

UPDATE paie_rubrique SET etape_calcul = CASE
    WHEN sens = 'RETENUE' THEN 'RETENUE_NET'
    WHEN soumis_irsa THEN 'BRUT' ELSE 'GAIN_NET' END
WHERE NOT est_fixe AND sens IN ('GAIN', 'RETENUE');

-- Types de Mouvements Variables
INSERT INTO ref_type_mouvement_paie (id_type_mouvement, code, libelle, sens, categorie, id_rubrique, est_proratise, est_plafonne_dixieme, nature, owner_code, saisie_manuelle_autorisee, periode_obligatoire, motif_obligatoire, provenance_defaut, est_actif, ordre_affichage, description) VALUES
(1, 'ACOMPTE_15', 'Acompte sur salaire', 'RETENUE', 'ACOMPTE', (SELECT id_rubrique FROM paie_rubrique WHERE code = 'RETENUE_ACOMPTE_15'), FALSE, FALSE, 'ACOMPTE', 'TRESORERIE', TRUE, TRUE, TRUE, 'ACOMPTE', TRUE, 10, 'Acompte versé puis imputé sur une période déterminée'),
(2, 'AVANCE_SPECIALE', 'Avance exceptionnelle sur salaire', 'RETENUE', 'AVANCE', (SELECT id_rubrique FROM paie_rubrique WHERE code = 'RETENUE_AVANCE_SPECIALE'), FALSE, TRUE, 'AVANCE', 'TRESORERIE', TRUE, TRUE, TRUE, 'AVANCE', TRUE, 20, 'Avance employeur avec suivi du solde restant'),
(3, 'CALL_PERSO', 'Retenue téléphonie personnelle', 'RETENUE', 'RETENUE_DIVERSE', (SELECT id_rubrique FROM paie_rubrique WHERE code = 'RETENUE_CALL_PERSO'), FALSE, FALSE, 'RETENUE_NETTE', 'DAF_PAIE', TRUE, TRUE, TRUE, 'VARIABLE_DAF', TRUE, 30, 'Retenue nette manuelle avec source décrite dans le motif'),
(4, 'RETENUE_DIVERSE', 'Retenue exceptionnelle', 'RETENUE', 'RETENUE_DIVERSE', (SELECT id_rubrique FROM paie_rubrique WHERE code = 'RETENUE_DIVERSE'), FALSE, FALSE, 'VARIABLE_DAF', 'DAF_PAIE', TRUE, TRUE, TRUE, 'VARIABLE_DAF', TRUE, 40, 'Retenue exceptionnelle motivée'),
(5, 'PRIME_PERFORMANCE', 'Prime de performance', 'GAIN', 'PRIME', (SELECT id_rubrique FROM paie_rubrique WHERE code = 'PRIME_PERFORMANCE'), TRUE, FALSE, 'VARIABLE_DAF', 'DAF_PAIE', TRUE, TRUE, TRUE, 'VARIABLE_DAF', TRUE, 50, 'Montant attribué puis transformé par le moteur'),
(6, 'PRIME_ASSIDUITE', 'Prime d''assiduité', 'GAIN', 'PRIME', (SELECT id_rubrique FROM paie_rubrique WHERE code = 'PRIME_ASSIDUITE'), FALSE, FALSE, 'AUTOMATIQUE', 'SYSTEME', FALSE, TRUE, FALSE, 'SYSTEME', TRUE, 60, 'Non saisissable tant que le forfait daté n''est pas configuré'),
(7, 'PRIME_EXCEPTIONNELLE', 'Prime exceptionnelle', 'GAIN', 'PRIME', (SELECT id_rubrique FROM paie_rubrique WHERE code = 'PRIME_EXCEPTIONNELLE'), FALSE, FALSE, 'VARIABLE_DAF', 'DAF_PAIE', TRUE, TRUE, TRUE, 'VARIABLE_DAF', TRUE, 70, 'Gratification ponctuelle décidée par la direction'),
(8, 'AUTIMP', 'Autre gain imposable', 'GAIN', 'PRIME', (SELECT id_rubrique FROM paie_rubrique WHERE code = 'AUTIMP'), FALSE, FALSE, 'VARIABLE_DAF', 'DAF_PAIE', TRUE, TRUE, TRUE, 'VARIABLE_DAF', TRUE, 80, 'Compatibilité historique pour un gain imposable qualifié'),
(9, 'EXTRA_DEPLACEMENT', 'Déplacement exceptionnel', 'GAIN', 'EXTRA', (SELECT id_rubrique FROM paie_rubrique WHERE code = 'EXTRA_DEPLACEMENT'), FALSE, FALSE, 'VARIABLE_DAF', 'DAF_PAIE', TRUE, TRUE, TRUE, 'VARIABLE_DAF', TRUE, 90, 'Exception motivée, distincte du déplacement ordinaire automatique'),
(10, 'NOTE_DE_FRAIS', 'Remboursement de frais validés', 'GAIN', 'NOTE_DE_FRAIS', (SELECT id_rubrique FROM paie_rubrique WHERE code = 'REMBOURSEMENT_NOTE_FRAIS'), FALSE, FALSE, 'NOTE_DE_FRAIS', 'DAF_PAIE', TRUE, TRUE, TRUE, 'NOTE_DE_FRAIS', TRUE, 100, 'Remboursement hors brut avec justificatif obligatoire avant approbation'),
(11, 'REGULARISATION_CONGE', 'Régularisation de congé antérieur', 'GAIN', 'REGULARISATION', (SELECT id_rubrique FROM paie_rubrique WHERE code = 'REGULARISATION_PAIE'), FALSE, FALSE, 'REGULARISATION', 'CONGE_PAIE', TRUE, TRUE, TRUE, 'CONGE', TRUE, 110, 'Complément positif issu d''une correction de reliquat ; motif CORRECTION_CUTOVER#id obligatoire, montant issu de la source');

SELECT setval('ref_type_mouvement_paie_id_type_mouvement_seq', (SELECT GREATEST(MAX(id_type_mouvement), 11) FROM ref_type_mouvement_paie));
