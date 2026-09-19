-- =====================================================================
-- GALAXAM SIRH — MODULE 06 : MOTEUR DE PAIE, PRÊTS & QUITTANCES
-- Script canonique final (Zéro ALTER TABLE, Zéro OWNER, Zéro public.)
-- Sources : 26-09-02-sp4-paie.sql, 26-09-08-sp4-paie-bulletin.sql,
--           26-09-09-sp4-paie-prets.sql, 26-09-10-sp4-paie-ajustements-audit.sql,
--           26-09-11-sp4-paie-honoraires-ajustements.sql, 26-09-11-sp4-paie-stc.sql,
--           26-09-12-sp4-paie-variables-dynamiques.sql, 26-09-13-sp4-conge-valorisation-droits.sql,
--           26-09-15-sp7-regularisation-conge-mouvement.sql, 26-09-16-sp8-indexes-foreign-keys.sql
-- =====================================================================

-- 1. Nettoyage préalable des vues, triggers, tables et séquences
DROP VIEW IF EXISTS v_config_legale_active CASCADE;

DROP TABLE IF EXISTS ref_mode_paiement CASCADE;
DROP TABLE IF EXISTS ref_type_bulletin CASCADE;
DROP TABLE IF EXISTS conge_journal_droit CASCADE;
DROP TABLE IF EXISTS paie_stc CASCADE;
DROP TABLE IF EXISTS paie_honoraire_prestation CASCADE;
DROP TABLE IF EXISTS paie_pret_echeance CASCADE;
DROP TABLE IF EXISTS paie_pret CASCADE;
DROP TABLE IF EXISTS paie_avance_acompte CASCADE;
DROP TABLE IF EXISTS ref_type_mouvement_paie CASCADE;
DROP TABLE IF EXISTS bulletin_paie_ligne CASCADE;
DROP TABLE IF EXISTS bulletin_paie CASCADE;
DROP TABLE IF EXISTS paie_rubrique CASCADE;
DROP TABLE IF EXISTS ref_taux_majoration CASCADE;
DROP TABLE IF EXISTS bareme_irsa CASCADE;
DROP TABLE IF EXISTS site_cotisation CASCADE;
DROP TABLE IF EXISTS ref_type_cotisation CASCADE;

DROP SEQUENCE IF EXISTS seq_numero_recu_stc;

-- 2. Cotisations Sociales & Rattachement Sites
CREATE TABLE ref_type_cotisation (
    id_type_cotisation SERIAL PRIMARY KEY,
    id_config_legale INT REFERENCES paie_config_legale(id_config_legale) ON DELETE CASCADE,
    code VARCHAR(20) NOT NULL UNIQUE,
    libelle VARCHAR(100) NOT NULL,
    taux_salarial NUMERIC(5,2),
    taux_patronal NUMERIC(5,2),
    plafonne BOOLEAN DEFAULT TRUE,
    ordre INT DEFAULT 1,
    est_actif BOOLEAN DEFAULT TRUE,
    date_debut DATE NOT NULL DEFAULT '2026-01-01',
    date_fin DATE,
    portee VARCHAR(20) DEFAULT 'SITE' CHECK (portee IN ('SITE', 'ENTREPRISE'))
);

CREATE INDEX idx_type_cotisation_config ON ref_type_cotisation(id_config_legale);

CREATE TABLE site_cotisation (
    id_site_cotisation SERIAL PRIMARY KEY,
    id_site INT NOT NULL REFERENCES ref_site(id_site) ON DELETE CASCADE,
    id_type_cotisation INT NOT NULL REFERENCES ref_type_cotisation(id_type_cotisation) ON DELETE CASCADE,
    est_actif BOOLEAN DEFAULT TRUE,
    est_plafonne_sante BOOLEAN DEFAULT TRUE,
    CONSTRAINT uk_site_cotisation UNIQUE (id_site, id_type_cotisation)
);

CREATE INDEX idx_site_cotisation_type ON site_cotisation(id_type_cotisation);

-- 3. Barèmes IRSA & Majorations Légales
CREATE TABLE bareme_irsa (
    id_tranche SERIAL PRIMARY KEY,
    id_config_legale INT REFERENCES paie_config_legale(id_config_legale) ON DELETE CASCADE,
    numero_tranche INT NOT NULL,
    tranche_min NUMERIC(12,2) NOT NULL,
    tranche_max NUMERIC(12,2),
    taux NUMERIC(5,2) NOT NULL,
    date_debut DATE NOT NULL,
    date_fin DATE
);

CREATE INDEX idx_bareme_irsa_config ON bareme_irsa(id_config_legale);

CREATE TABLE ref_taux_majoration (
    id_taux_majoration SERIAL PRIMARY KEY,
    id_config_legale INT REFERENCES paie_config_legale(id_config_legale) ON DELETE CASCADE,
    type_majoration VARCHAR(20) NOT NULL,
    taux NUMERIC(5,2) NOT NULL,
    description TEXT,
    date_debut DATE NOT NULL,
    date_fin DATE
);

CREATE INDEX idx_taux_majoration_config ON ref_taux_majoration(id_config_legale);

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
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_rubrique_categorie CHECK (categorie IN (
        'SALAIRE_BASE', 'HEURES_SUP', 'PRIME', 'INDEMNITE_IMPOSABLE',
        'INDEMNITE_NON_IMPOSABLE', 'COTISATION_SALARIALE', 'COTISATION_PATRONALE',
        'IMPOT', 'ACOMPTE_AVANCE', 'RETENUE_DIVERSE', 'NET'
    )),
    CONSTRAINT chk_rubrique_type_valeur CHECK (type_valeur IN ('MONTANT', 'HEURES', 'JOURS', 'POURCENTAGE')),
    CONSTRAINT chk_rubrique_sens CHECK (sens IN ('GAIN', 'RETENUE', 'PATRONAL', 'INFO', 'NEUTRE'))
);

CREATE INDEX idx_paie_rubrique_categorie ON paie_rubrique(categorie);
CREATE INDEX idx_paie_rubrique_ordre ON paie_rubrique(ordre_affichage);

CREATE TABLE ref_type_mouvement_paie (
    id_type_mouvement SERIAL PRIMARY KEY,
    code VARCHAR(40) UNIQUE NOT NULL,
    libelle VARCHAR(150) NOT NULL,
    sens VARCHAR(10) NOT NULL CHECK (sens IN ('GAIN', 'RETENUE')),
    categorie VARCHAR(40) NOT NULL,
    id_rubrique INT REFERENCES paie_rubrique(id_rubrique),
    est_proratise BOOLEAN DEFAULT FALSE,
    est_plafonne_dixieme BOOLEAN DEFAULT FALSE,
    est_actif BOOLEAN DEFAULT TRUE,
    ordre_affichage INT DEFAULT 10,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ref_type_mvt_code ON ref_type_mouvement_paie(code);
CREATE INDEX idx_ref_type_mvt_sens ON ref_type_mouvement_paie(sens);
CREATE INDEX idx_ref_type_mouvement_paie_rubrique ON ref_type_mouvement_paie(id_rubrique);

-- 4b. Référentiels Typologiques de Paie & Règlements
CREATE TABLE ref_type_bulletin (
    id_type_bulletin SERIAL PRIMARY KEY,
    code VARCHAR(30) UNIQUE NOT NULL,
    libelle VARCHAR(100) NOT NULL,
    prefixe_reference VARCHAR(10) DEFAULT 'BP',
    est_reouvrable BOOLEAN DEFAULT TRUE,
    exige_pointage_scelle BOOLEAN DEFAULT TRUE,
    est_inclus_dnts BOOLEAN DEFAULT TRUE,
    est_inclus_safi BOOLEAN DEFAULT TRUE,
    actif BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO ref_type_bulletin (code, libelle, prefixe_reference, est_reouvrable, exige_pointage_scelle, est_inclus_dnts, est_inclus_safi, actif)
VALUES
    ('MENSUEL', 'Bulletin Mensuel Ordinaire', 'BP', TRUE, TRUE, TRUE, TRUE, TRUE),
    ('STC', 'Solde de Tout Compte Libératoire', 'STC', FALSE, FALSE, TRUE, TRUE, TRUE),
    ('COMPLEMENTAIRE', 'Bulletin Complémentaire', 'BPC', TRUE, FALSE, TRUE, TRUE, TRUE),
    ('RAPPEL', 'Rappel de Salaire / Régularisation', 'BPR', TRUE, FALSE, TRUE, TRUE, TRUE),
    ('GRATIFICATION', 'Gratification / 13ème Mois', 'BPG', FALSE, FALSE, TRUE, TRUE, TRUE)
ON CONFLICT (code) DO NOTHING;

CREATE TABLE ref_mode_paiement (
    id_mode_paiement SERIAL PRIMARY KEY,
    code VARCHAR(30) UNIQUE NOT NULL,
    libelle VARCHAR(100) NOT NULL,
    exige_rib BOOLEAN DEFAULT FALSE,
    inclus_fichier_virement BOOLEAN DEFAULT FALSE,
    actif BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO ref_mode_paiement (code, libelle, exige_rib, inclus_fichier_virement, actif)
VALUES
    ('VIREMENT', 'Virement Bancaire', TRUE, TRUE, TRUE),
    ('CHEQUE', 'Chèque Bancaire', FALSE, FALSE, TRUE),
    ('ESPECES', 'Espèces / Caisse', FALSE, FALSE, TRUE),
    ('MOBILE_MONEY', 'Mobile Money (Mvola/Orange/Airtel)', FALSE, FALSE, TRUE)
ON CONFLICT (code) DO NOTHING;

-- 5. En-tête des Bulletins de Paie (avec audit de réouverture inline)
CREATE TABLE bulletin_paie (
    id_bulletin SERIAL PRIMARY KEY,
    id_periode_paie INT NOT NULL REFERENCES periode_paie(id_periode_paie),
    id_employe INT NOT NULL REFERENCES employe(id_employe),
    id_config_legale INT REFERENCES paie_config_legale(id_config_legale),
    id_snapshot_pointage INT REFERENCES pointage_cumul_periode(id_cumul),
    version_snapshot_pointage INT CHECK (version_snapshot_pointage IS NULL OR version_snapshot_pointage > 0),
    type_bulletin VARCHAR(20) NOT NULL DEFAULT 'MENSUEL',
    id_type_bulletin INT REFERENCES ref_type_bulletin(id_type_bulletin),
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
    heures_normales NUMERIC(6,2) DEFAULT 0,
    heures_hs_30 NUMERIC(6,2) DEFAULT 0,
    heures_hs_50 NUMERIC(6,2) DEFAULT 0,
    heures_nuit NUMERIC(6,2) DEFAULT 0,
    jours_ouvrables NUMERIC(4,1) DEFAULT 0,
    jours_travailles NUMERIC(4,1) DEFAULT 0,
    jours_absence NUMERIC(4,1) DEFAULT 0,
    jours_conges NUMERIC(4,1) DEFAULT 0,
    minutes_retard INT DEFAULT 0,
    salaire_base NUMERIC(12,2) NOT NULL,
    taux_horaire NUMERIC(12,4) NOT NULL,
    total_gains_bruts NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_cotisations_salariales NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_cotisations_patronales NUMERIC(12,2) NOT NULL DEFAULT 0,
    salaire_net_imposable NUMERIC(12,2) NOT NULL DEFAULT 0,
    base_irsa_arrondie NUMERIC(12,2) NOT NULL DEFAULT 0,
    irsa_brut NUMERIC(12,2) NOT NULL DEFAULT 0,
    reduction_enfants NUMERIC(12,2) NOT NULL DEFAULT 0,
    irsa_net NUMERIC(12,2) NOT NULL DEFAULT 0,
    retenue_isi_5 NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_indemnites_non_imposables NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_retenues_acompte_pret NUMERIC(12,2) NOT NULL DEFAULT 0,
    net_avant_arrondi NUMERIC(12,2) NOT NULL DEFAULT 0,
    net_a_payer NUMERIC(12,2) NOT NULL DEFAULT 0,
    cout_global_employeur NUMERIC(12,2) NOT NULL DEFAULT 0,
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
    CONSTRAINT uk_bulletin_employe_periode_type UNIQUE (id_periode_paie, id_employe, type_bulletin)
);

CREATE INDEX idx_bulletin_periode ON bulletin_paie(id_periode_paie);
CREATE INDEX idx_bulletin_employe ON bulletin_paie(id_employe);
CREATE INDEX idx_bulletin_statut ON bulletin_paie(statut);
CREATE INDEX idx_bulletin_snapshot_pointage ON bulletin_paie(id_snapshot_pointage);
CREATE INDEX idx_bulletin_paie_calcule_par ON bulletin_paie(calcule_par);
CREATE INDEX idx_bulletin_paie_valide_par ON bulletin_paie(valide_par);
CREATE INDEX idx_bulletin_paie_cloture_par ON bulletin_paie(cloture_par);
CREATE INDEX idx_bulletin_paie_rouvert_par ON bulletin_paie(rouvert_par);

-- 6. Lignes du Bulletin de Paie
CREATE TABLE bulletin_paie_ligne (
    id_ligne SERIAL PRIMARY KEY,
    id_bulletin INT NOT NULL REFERENCES bulletin_paie(id_bulletin) ON DELETE CASCADE,
    id_rubrique INT REFERENCES paie_rubrique(id_rubrique),
    code_rubrique VARCHAR(40) NOT NULL,
    libelle_rubrique VARCHAR(150) NOT NULL,
    sens VARCHAR(10) NOT NULL,
    categorie VARCHAR(40) NOT NULL,
    base_calcul NUMERIC(12,2),
    taux_salarial NUMERIC(6,3),
    taux_patronal NUMERIC(6,3),
    quantite NUMERIC(8,2),
    montant_salarial NUMERIC(12,2) NOT NULL DEFAULT 0,
    montant_patronal NUMERIC(12,2) NOT NULL DEFAULT 0,
    ordre INT NOT NULL DEFAULT 10,
    imposable BOOLEAN NOT NULL DEFAULT TRUE,
    soumis_cotisation BOOLEAN NOT NULL DEFAULT TRUE,
    commentaire VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_ligne_sens CHECK (sens IN ('GAIN', 'RETENUE', 'PATRONAL', 'INFO'))
);

CREATE INDEX idx_bulletin_ligne_bulletin ON bulletin_paie_ligne(id_bulletin);
CREATE INDEX idx_bulletin_ligne_code ON bulletin_paie_ligne(code_rubrique);
CREATE INDEX idx_bulletin_paie_ligne_rubrique ON bulletin_paie_ligne(id_rubrique);

-- 7. Avances, Acomptes et Variables DAF
CREATE TABLE paie_avance_acompte (
    id_avance SERIAL PRIMARY KEY,
    id_employe INT NOT NULL REFERENCES employe(id_employe),
    id_periode_paie INT REFERENCES periode_paie(id_periode_paie),
    type_mouvement VARCHAR(30) NOT NULL,
    id_type_mouvement INT REFERENCES ref_type_mouvement_paie(id_type_mouvement),
    sens VARCHAR(10) NOT NULL DEFAULT 'RETENUE',
    montant NUMERIC(12,2) NOT NULL,
    date_demande DATE NOT NULL DEFAULT CURRENT_DATE,
    date_accord DATE,
    date_paiement DATE,
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
    CONSTRAINT chk_avance_statut CHECK (statut IN ('EN_ATTENTE', 'APPROUVE', 'REJETE', 'IMPUTE_PAIE', 'ANNULE'))
);

CREATE INDEX idx_paie_avance_employe ON paie_avance_acompte(id_employe);
CREATE INDEX idx_paie_avance_periode ON paie_avance_acompte(id_periode_paie);
CREATE INDEX idx_paie_avance_type_mvt ON paie_avance_acompte(id_type_mouvement);
CREATE INDEX idx_paie_avance_statut ON paie_avance_acompte(statut);
CREATE INDEX idx_paie_avance_acompte_cree_par ON paie_avance_acompte(cree_par);
CREATE INDEX idx_paie_avance_acompte_approuve_par ON paie_avance_acompte(approuve_par);
CREATE INDEX idx_paie_avance_acompte_bulletin ON paie_avance_acompte(id_bulletin);

-- 8. Prêts d'Entreprise & Échéanciers
CREATE TABLE paie_pret (
    id_pret SERIAL PRIMARY KEY,
    id_employe INT NOT NULL REFERENCES employe(id_employe),
    reference_pret VARCHAR(50) NOT NULL UNIQUE,
    montant_total NUMERIC(12,2) NOT NULL CHECK (montant_total > 0),
    montant_mensualite NUMERIC(12,2) NOT NULL CHECK (montant_mensualite > 0),
    nb_echeances INT NOT NULL CHECK (nb_echeances > 0),
    date_accord DATE NOT NULL DEFAULT CURRENT_DATE,
    date_deblocage DATE,
    date_premiere_echeance DATE NOT NULL,
    capital_rembourse NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (capital_rembourse >= 0),
    capital_restant_du NUMERIC(12,2) NOT NULL CHECK (capital_restant_du >= 0),
    statut VARCHAR(20) NOT NULL DEFAULT 'EN_COURS',
    motif VARCHAR(255) NOT NULL,
    accorde_par INT REFERENCES utilisateur(id_utilisateur),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_pret_statut CHECK (statut IN ('DEMANDE', 'APPROUVE', 'EN_COURS', 'SOLDE', 'SUSPENDU', 'ANNULE')),
    CONSTRAINT chk_pret_coherence_capital CHECK (capital_rembourse + capital_restant_du <= montant_total + 1.00)
);

CREATE INDEX idx_paie_pret_employe ON paie_pret(id_employe);
CREATE INDEX idx_paie_pret_statut ON paie_pret(statut);
CREATE INDEX idx_paie_pret_accorde_par ON paie_pret(accorde_par);

CREATE TABLE paie_pret_echeance (
    id_echeance SERIAL PRIMARY KEY,
    id_pret INT NOT NULL REFERENCES paie_pret(id_pret) ON DELETE CASCADE,
    numero_echeance INT NOT NULL CHECK (numero_echeance > 0),
    id_periode_paie INT REFERENCES periode_paie(id_periode_paie),
    date_echeance DATE NOT NULL,
    montant_echeance NUMERIC(12,2) NOT NULL CHECK (montant_echeance > 0),
    capital_restant_apres NUMERIC(12,2) NOT NULL CHECK (capital_restant_apres >= 0),
    montant_preleve NUMERIC(12,2) DEFAULT 0,
    montant_restant_echeance NUMERIC(12,2),
    statut VARCHAR(25) NOT NULL DEFAULT 'A_ECHOIR',
    id_bulletin INT REFERENCES bulletin_paie(id_bulletin) ON DELETE SET NULL,
    date_prelevement TIMESTAMP WITH TIME ZONE,
    commentaire VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_echeance_statut CHECK (statut IN (
        'A_ECHOIR', 'PRELEVEE_EN_PAIE', 'PRELEVEE_PARTIELLEMENT', 'REMBOURSEE_ANTICIPEE', 'REPORTEE', 'ANNULEE'
    )),
    CONSTRAINT uk_pret_echeance UNIQUE (id_pret, numero_echeance)
);

CREATE INDEX idx_pret_echeance_pret ON paie_pret_echeance(id_pret);
CREATE INDEX idx_pret_echeance_periode ON paie_pret_echeance(id_periode_paie);
CREATE INDEX idx_pret_echeance_statut ON paie_pret_echeance(statut);
CREATE INDEX idx_paie_pret_echeance_bulletin ON paie_pret_echeance(id_bulletin);

-- 9. Honoraires et Prestations Externes (Régime ISI 5%)
CREATE TABLE paie_honoraire_prestation (
    id_prestation SERIAL PRIMARY KEY,
    id_periode_paie INTEGER REFERENCES periode_paie(id_periode_paie) ON DELETE SET NULL,
    id_intervenant INTEGER REFERENCES employe(id_employe) ON DELETE SET NULL,
    nom_complet VARCHAR(150) NOT NULL,
    cin_ou_nif VARCHAR(30) NOT NULL,
    intitule_prestation VARCHAR(150) NOT NULL,
    nature_service VARCHAR(100) DEFAULT 'SERVICE SYSTEMATIQUE',
    date_prestation DATE NOT NULL,
    date_paiement DATE,
    montant_brut NUMERIC(12, 2) NOT NULL,
    taux_isi NUMERIC(5, 2) DEFAULT 5.00,
    montant_retenue_isi NUMERIC(12, 2) NOT NULL,
    montant_net_paye NUMERIC(12, 2) NOT NULL,
    adresse VARCHAR(200),
    commune VARCHAR(100) DEFAULT 'ANTANANARIVO RENIVOHITRA C.U',
    region VARCHAR(100) DEFAULT 'ANALAMANGA',
    district VARCHAR(100) DEFAULT 'ANTANANARIVO RENIVOHITRA',
    province VARCHAR(100) DEFAULT 'ANTANANARIVO',
    reference_facture VARCHAR(50),
    statut VARCHAR(30) DEFAULT 'VALIDE' CHECK (statut IN ('BROUILLON', 'VALIDE', 'PAYE', 'ANNULE')),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_honoraire_periode ON paie_honoraire_prestation(id_periode_paie);
CREATE INDEX idx_honoraire_intervenant ON paie_honoraire_prestation(id_intervenant);

-- 10. Solde de Tout Compte (STC) & Reçu Libératoire
CREATE SEQUENCE IF NOT EXISTS seq_numero_recu_stc START WITH 1 INCREMENT BY 1;

CREATE TABLE paie_stc (
    id_stc SERIAL PRIMARY KEY,
    id_employe INT NOT NULL REFERENCES employe(id_employe),
    id_periode_paie INT NOT NULL REFERENCES periode_paie(id_periode_paie),
    id_bulletin INT REFERENCES bulletin_paie(id_bulletin) ON DELETE SET NULL,
    numero_recu VARCHAR(60) NOT NULL UNIQUE,
    date_depart DATE NOT NULL,
    motif_rupture VARCHAR(50) NOT NULL,
    salaire_base NUMERIC(12,2) NOT NULL,
    taux_horaire NUMERIC(12,4) NOT NULL,
    anciennete_mois INT NOT NULL DEFAULT 0,
    jours_travailles_dernier_mois NUMERIC(4,1) NOT NULL DEFAULT 0,
    salaire_prorata NUMERIC(12,2) NOT NULL DEFAULT 0,
    solde_conges_restant NUMERIC(6,2) NOT NULL DEFAULT 0,
    taux_journalier_conge NUMERIC(12,2) NOT NULL DEFAULT 0,
    indemnite_conges_payes NUMERIC(12,2) NOT NULL DEFAULT 0,
    mois_preavis NUMERIC(4,2) NOT NULL DEFAULT 0,
    indemnite_preavis NUMERIC(12,2) NOT NULL DEFAULT 0,
    indemnite_licenciement NUMERIC(12,2) NOT NULL DEFAULT 0,
    autres_indemnites NUMERIC(12,2) NOT NULL DEFAULT 0,
    motif_autres_indemnites VARCHAR(255),
    total_brut_stc NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_cotisations_sociales NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_irsa NUMERIC(12,2) NOT NULL DEFAULT 0,
    net_imposable NUMERIC(12,2) NOT NULL DEFAULT 0,
    solde_prets_deduit NUMERIC(12,2) NOT NULL DEFAULT 0,
    solde_avances_deduit NUMERIC(12,2) NOT NULL DEFAULT 0,
    autres_deductions NUMERIC(12,2) NOT NULL DEFAULT 0,
    motif_autres_deductions VARCHAR(255),
    net_a_payer_stc NUMERIC(12,2) NOT NULL DEFAULT 0,
    statut VARCHAR(20) NOT NULL DEFAULT 'SIMULATION',
    mode_reglement VARCHAR(30) DEFAULT 'VIREMENT',
    id_mode_reglement INT REFERENCES ref_mode_paiement(id_mode_paiement),
    id_motif_sortie INT REFERENCES ref_motif_sortie(id_motif_sortie),
    commentaire TEXT,
    cree_par INT REFERENCES utilisateur(id_utilisateur),
    valide_par INT REFERENCES utilisateur(id_utilisateur),
    date_validation TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_stc_statut CHECK (statut IN ('SIMULATION', 'VALIDE', 'REGLE', 'ANNULE'))
);

CREATE INDEX idx_paie_stc_employe ON paie_stc(id_employe);
CREATE INDEX idx_paie_stc_periode ON paie_stc(id_periode_paie);
CREATE INDEX idx_paie_stc_statut ON paie_stc(statut);
CREATE INDEX idx_paie_stc_mode_reglement ON paie_stc(id_mode_reglement);
CREATE INDEX idx_paie_stc_motif_sortie ON paie_stc(id_motif_sortie);
CREATE INDEX idx_paie_stc_cree_par ON paie_stc(cree_par);
CREATE INDEX idx_paie_stc_valide_par ON paie_stc(valide_par);
CREATE INDEX idx_paie_stc_bulletin ON paie_stc(id_bulletin);

-- 11. Grand-Livre de Valorisation des Droits à Congés
CREATE TABLE conge_journal_droit (
    id_journal SERIAL PRIMARY KEY,
    id_employe INT NOT NULL REFERENCES employe(id_employe),
    id_periode_paie INT REFERENCES periode_paie(id_periode_paie),
    id_periode_reference INT REFERENCES conge_periode_reference(id_periode_reference),
    numero_mois_reference INT,
    annee INT NOT NULL,
    mois INT NOT NULL,
    salaire_base NUMERIC(12,2) NOT NULL,
    jours_travailles_formule NUMERIC(5,2) NOT NULL DEFAULT 30.0,
    jours_droit_acquis NUMERIC(6,4) NOT NULL DEFAULT 2.5000,
    base_valorisation NUMERIC(12,2) NOT NULL,
    valorisation_mensuelle NUMERIC(12,2) NOT NULL,
    cumul_jours_droit NUMERIC(8,4) NOT NULL,
    cumul_valorisation NUMERIC(12,2) NOT NULL,
    taux_journalier_conge NUMERIC(12,2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_conge_journal_emp_annee_mois UNIQUE (id_employe, annee, mois)
);

CREATE INDEX idx_conge_journal_emp ON conge_journal_droit(id_employe);
CREATE INDEX idx_conge_journal_periode ON conge_journal_droit(id_periode_paie);
CREATE INDEX idx_conge_journal_ref ON conge_journal_droit(id_periode_reference);

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

-- Cotisations Sociales Référentielles
INSERT INTO ref_type_cotisation (id_type_cotisation, id_config_legale, code, libelle, taux_salarial, taux_patronal, plafonne, ordre, est_actif, date_debut, portee) VALUES
(1, 2, 'CNAPS', 'Caisse Nationale de Prévoyance Sociale', 1.00, 13.00, TRUE, 1, TRUE, '2022-01-01', 'SITE'),
(2, 2, 'OSTIE', 'Organisation Sanitaire Tananarivienne', 1.00, 5.00, TRUE, 1, TRUE, '2022-01-01', 'SITE'),
(3, 2, 'OMSI',  'Organisation Médicale Inter-entreprises', 1.50, 5.50, TRUE, 1, TRUE, '2022-01-01', 'SITE'),
(4, 2, 'ISI',   'Impôt Synthétique Intermédiaire', 0.00, 5.00, TRUE, 1, TRUE, '2022-01-01', 'ENTREPRISE'),
(5, 2, 'FMFP',  'Fonds Malgache de Formation Professionnelle', 0.00, 1.00, FALSE, 5, TRUE, '2022-01-01', 'ENTREPRISE');
SELECT setval('ref_type_cotisation_id_type_cotisation_seq', (SELECT GREATEST(MAX(id_type_cotisation), 5) FROM ref_type_cotisation));

-- Cotisations par Site (TNR: CNAPS+OSTIE, TMM: CNAPS+OMSI)
INSERT INTO site_cotisation (id_site, id_type_cotisation, est_plafonne_sante) VALUES
(1, 1, TRUE),
(1, 2, TRUE),
(2, 1, TRUE),
(2, 3, TRUE);

-- Barèmes IRSA Officiels (LF_2024_2025: 5 Tranches jusqu'au 31/12/2025, LF_2026: 6 Tranches dès le 01/01/2026)
INSERT INTO bareme_irsa (id_config_legale, numero_tranche, tranche_min, tranche_max, taux, date_debut, date_fin) VALUES
(1, 1, 0.00, 350000.00, 0.00, '2022-01-01', '2025-12-31'),
(1, 2, 350001.00, 400000.00, 5.00, '2022-01-01', '2025-12-31'),
(1, 3, 400001.00, 500000.00, 10.00, '2022-01-01', '2025-12-31'),
(1, 4, 500001.00, 600000.00, 15.00, '2022-01-01', '2025-12-31'),
(1, 5, 600001.00, NULL, 20.00, '2022-01-01', '2025-12-31'),
(2, 1, 0.00, 350000.00, 0.00, '2026-01-01', NULL),
(2, 2, 350001.00, 400000.00, 5.00, '2026-01-01', NULL),
(2, 3, 400001.00, 500000.00, 10.00, '2026-01-01', NULL),
(2, 4, 500001.00, 600000.00, 15.00, '2026-01-01', NULL),
(2, 5, 600001.00, 4000000.00, 20.00, '2026-01-01', NULL),
(2, 6, 4000001.00, NULL, 25.00, '2026-01-01', NULL);
SELECT setval('bareme_irsa_id_tranche_seq', (SELECT GREATEST(MAX(id_tranche), 11) FROM bareme_irsa));

-- Majorations d'Heures Supplémentaires & Spéciales (LF_2024_2025 et LF_2026)
INSERT INTO ref_taux_majoration (id_config_legale, type_majoration, taux, description, date_debut, date_fin) VALUES
(1, 'HS_30', 0.30, '41ᵉ à 48ᵉ heure de la semaine', '2022-01-01', '2025-12-31'),
(1, 'HS_50', 0.50, 'Au-delà de la 48ᵉ heure', '2022-01-01', '2025-12-31'),
(1, 'NUIT', 0.30, 'Heures de nuit (22h - 05h)', '2022-01-01', '2025-12-31'),
(1, 'DIMANCHE', 0.40, 'Travail le dimanche', '2022-01-01', '2025-12-31'),
(1, 'FERIE', 1.00, 'Travail jour férié', '2022-01-01', '2025-12-31'),
(2, 'HS_30', 0.30, '41ᵉ à 48ᵉ heure de la semaine', '2026-01-01', NULL),
(2, 'HS_50', 0.50, 'Au-delà de la 48ᵉ heure', '2026-01-01', NULL),
(2, 'NUIT', 0.30, 'Heures de nuit (22h - 05h)', '2026-01-01', NULL),
(2, 'DIMANCHE', 0.40, 'Travail le dimanche', '2026-01-01', NULL),
(2, 'FERIE', 1.00, 'Travail jour férié', '2026-01-01', NULL);
SELECT setval('ref_taux_majoration_id_taux_majoration_seq', (SELECT GREATEST(MAX(id_taux_majoration), 10) FROM ref_taux_majoration));

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
(23, 'RETENUE_ISI_5', 'Retenue Libératoire ISI (5%)', 'IMPOT', 'POURCENTAGE', 'RETENUE', FALSE, FALSE, FALSE, TRUE, 430, 'Impôt synthétique intermédiaire pour prestataires/consultants'),
(24, 'INDEMNITE_TRANSPORT', 'Indemnité Forfaitaire de Déplacement', 'INDEMNITE_NON_IMPOSABLE', 'MONTANT', 'GAIN', FALSE, FALSE, FALSE, TRUE, 500, 'Indemnité transport proratisée selon jours présents'),
(25, 'INDEMNITE_REPAS', 'Indemnité Forfaitaire de Repas', 'INDEMNITE_NON_IMPOSABLE', 'MONTANT', 'GAIN', FALSE, FALSE, FALSE, TRUE, 510, 'Repas midi 2 000 Ar par jour de présence validé'),
(26, 'REMBOURSEMENT_NOTE_FRAIS', 'Remboursement Notes de Frais Validées', 'INDEMNITE_NON_IMPOSABLE', 'MONTANT', 'GAIN', FALSE, FALSE, FALSE, FALSE, 520, 'Remboursement des frais professionnels avancés'),
(27, 'RETENUE_ACOMPTE_15', 'Acompte de Quinzaine (du 15)', 'ACOMPTE_AVANCE', 'MONTANT', 'RETENUE', FALSE, FALSE, FALSE, TRUE, 600, 'Acompte versé en milieu de mois'),
(28, 'RETENUE_AVANCE_SPECIALE', 'Avance Exceptionnelle sur Salaire', 'ACOMPTE_AVANCE', 'MONTANT', 'RETENUE', FALSE, FALSE, FALSE, TRUE, 610, 'Avance ponctuelle accordée par la direction'),
(29, 'RETENUE_ECHEANCE_PRET', 'Échéance Remboursement Prêt Entreprise', 'RETENUE_DIVERSE', 'MONTANT', 'RETENUE', FALSE, FALSE, FALSE, TRUE, 620, 'Mensualité de prêt interne accordé au salarié'),
(30, 'RETENUE_CALL_PERSO', 'Retenue Télécommunications Privées (Call)', 'RETENUE_DIVERSE', 'MONTANT', 'RETENUE', FALSE, FALSE, FALSE, TRUE, 630, 'Facturation flotte mobile communications personnelles'),
(31, 'INDEMNITE_COMPENSATRICE_CONGE', 'Indemnité Compensatrice de Congés Payés (ICCP)', 'SALAIRE_BASE', 'JOURS', 'GAIN', TRUE, TRUE, TRUE, FALSE, 700, 'Indemnité de solde de congés non pris à la rupture'),
(32, 'INDEMNITE_PREAVIS', 'Indemnité Compensatrice de Préavis', 'SALAIRE_BASE', 'MONTANT', 'GAIN', TRUE, TRUE, TRUE, FALSE, 710, 'Indemnité de préavis non exécuté'),
(33, 'INDEMNITE_LICENCIEMENT', 'Indemnité de Licenciement', 'INDEMNITE_NON_IMPOSABLE', 'MONTANT', 'GAIN', FALSE, FALSE, FALSE, FALSE, 720, 'Indemnité de rupture légale ou conventionnelle'),
(34, 'REGULARISATION_PAIE', 'Régularisation sur Mois Antérieur', 'RETENUE_DIVERSE', 'MONTANT', 'NEUTRE', TRUE, TRUE, TRUE, FALSE, 730, 'Ajustement positif ou négatif régularisé'),
(35, 'AUTIMP', 'Autres Gains Imposables', 'PRIME', 'MONTANT', 'GAIN', TRUE, TRUE, TRUE, FALSE, 740, 'Autres primes brutes imposables'),
(36, 'EXTRA_DEPLACEMENT', 'Indemnité de Déplacement Extra', 'INDEMNITE_NON_IMPOSABLE', 'MONTANT', 'GAIN', FALSE, FALSE, FALSE, FALSE, 750, 'Extra déplacement ou mission exceptionnelle non imposable'),
(37, 'AJUSTEMENT_ARRONDI', 'Ajustement Arrondi Monétaire (Net)', 'NET', 'MONTANT', 'NEUTRE', FALSE, FALSE, FALSE, TRUE, 999, 'Écart monétaire d''arrondi pour équilibre parfait');

SELECT setval('paie_rubrique_id_rubrique_seq', (SELECT GREATEST(MAX(id_rubrique), 37) FROM paie_rubrique));

-- Types de Mouvements Variables
INSERT INTO ref_type_mouvement_paie (id_type_mouvement, code, libelle, sens, categorie, id_rubrique, est_proratise, est_plafonne_dixieme, est_actif, ordre_affichage, description) VALUES
(1, 'ACOMPTE_15', 'Acompte de Quinzaine (du 15)', 'RETENUE', 'ACOMPTE', (SELECT id_rubrique FROM paie_rubrique WHERE code = 'RETENUE_ACOMPTE_15'), FALSE, FALSE, TRUE, 10, 'Acompte sur salaire échu versé en cours de mois'),
(2, 'AVANCE_SPECIALE', 'Avance Exceptionnelle sur Salaire', 'RETENUE', 'AVANCE', (SELECT id_rubrique FROM paie_rubrique WHERE code = 'RETENUE_AVANCE_SPECIALE'), FALSE, TRUE, TRUE, 20, 'Avance en espèces consentie par employeur (plafonnée au 1/10ème légal Art. 68)'),
(3, 'CALL_PERSO', 'Retenue Télécommunications Privées (Call Perso)', 'RETENUE', 'RETENUE_DIVERSE', (SELECT id_rubrique FROM paie_rubrique WHERE code = 'RETENUE_CALL_PERSO'), FALSE, FALSE, TRUE, 30, 'Prélèvement sur le net pour dépassement forfait téléphonique privé'),
(4, 'RETENUE_DIVERSE', 'Retenue Diverse sur Salaire', 'RETENUE', 'RETENUE_DIVERSE', (SELECT id_rubrique FROM paie_rubrique WHERE code = 'REGULARISATION_PAIE'), FALSE, FALSE, TRUE, 40, 'Pénalité ou retenue ponctuelle diverse sur le net à payer'),
(5, 'PRIME_PERFORMANCE', 'Prime de Performance (Proratisée)', 'GAIN', 'PRIME', (SELECT id_rubrique FROM paie_rubrique WHERE code = 'PRIME_PERFORMANCE'), TRUE, FALSE, TRUE, 50, 'Prime soumise au prorata de présence avec malus -25% si retard > 70mn et congés > 1j'),
(6, 'PRIME_ASSIDUITE', 'Prime d''Assiduité', 'GAIN', 'PRIME', (SELECT id_rubrique FROM paie_rubrique WHERE code = 'PRIME_ASSIDUITE'), FALSE, FALSE, TRUE, 60, 'Prime accordée si le total des retards du mois est strictement inférieur à 45 minutes'),
(7, 'PRIME_EXCEPTIONNELLE', 'Prime Exceptionnelle', 'GAIN', 'PRIME', (SELECT id_rubrique FROM paie_rubrique WHERE code = 'PRIME_EXCEPTIONNELLE'), FALSE, FALSE, TRUE, 70, 'Gratification ou prime exceptionnelle brute imposable'),
(8, 'AUTIMP', 'Autres Gains Imposables (AUTIMP)', 'GAIN', 'PRIME', (SELECT id_rubrique FROM paie_rubrique WHERE code = 'AUTIMP'), FALSE, FALSE, TRUE, 80, 'Autres primes et indemnités brutes imposables selon modèle paie'),
(9, 'EXTRA_DEPLACEMENT', 'Indemnité de Déplacement Extra (>19h30 / Mission)', 'GAIN', 'EXTRA', (SELECT id_rubrique FROM paie_rubrique WHERE code = 'EXTRA_DEPLACEMENT'), FALSE, FALSE, TRUE, 90, 'Extra déplacement ou mission exceptionnelle non imposable'),
(10, 'NOTE_DE_FRAIS', 'Remboursement Notes de Frais', 'GAIN', 'NOTE_DE_FRAIS', (SELECT id_rubrique FROM paie_rubrique WHERE code = 'REMBOURSEMENT_NOTE_FRAIS'), FALSE, FALSE, TRUE, 100, 'Remboursement de frais professionnels engagés par le collaborateur'),
(11, 'REGULARISATION_CONGE', 'Régularisation Congé au Retour (Restitution Absence)', 'GAIN', 'REGULARISATION', (SELECT id_rubrique FROM paie_rubrique WHERE code = 'REGULARISATION_PAIE'), FALSE, FALSE, TRUE, 110, 'Restitution d''une retenue d''absence décomptée au taux horaire sur mois antérieur suite au dépôt/validation du titre de congé au retour');

SELECT setval('ref_type_mouvement_paie_id_type_mouvement_seq', (SELECT GREATEST(MAX(id_type_mouvement), 11) FROM ref_type_mouvement_paie));
