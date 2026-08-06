-- ============================================================
-- SUPPRESSION DES OBJETS EXISTANTS
-- ============================================================
DROP VIEW IF EXISTS v_employe_cpl CASCADE;
DROP TABLE IF EXISTS employe_compte_banque CASCADE;
DROP TABLE IF EXISTS employe_contact_urgence CASCADE;
DROP TABLE IF EXISTS employe_enfant CASCADE;
DROP TABLE IF EXISTS employe_conjoint CASCADE;
DROP TABLE IF EXISTS employe CASCADE;
DROP TYPE IF EXISTS lien_parente_enum CASCADE;
DROP TYPE IF EXISTS motif_desactivation_enum CASCADE;
DROP TYPE IF EXISTS situation_familiale_enum CASCADE;
DROP TYPE IF EXISTS sexe_enum CASCADE;
-- ============================================================
-- ENUMS
-- ============================================================
CREATE TYPE sexe_enum AS ENUM ('M', 'F');
CREATE TYPE situation_familiale_enum AS ENUM (
    'Celibataire',
    'Marie',
    'Divorce',
    'Veuf'
);
CREATE TYPE motif_desactivation_enum AS ENUM (
    'Demission',
    'Licenciement',
    'Fin_contrat',
    'Retraite',
    'Deces',
    'Autre'
);
CREATE TYPE lien_parente_enum AS ENUM (
    'Pere',
    'Mere',
    'Conjoint',
    'Frere',
    'Soeur',
    'Enfant',
    'Autre'
);
-- ============================================================
-- TABLE EMPLOYE
-- ============================================================
CREATE TABLE employe (
    id_employe SERIAL PRIMARY KEY,
    matricule VARCHAR(20) NOT NULL UNIQUE,
    nom VARCHAR(100) NOT NULL,
    prenom VARCHAR(100) NOT NULL,
    sexe sexe_enum NOT NULL,
    date_naissance DATE NOT NULL,
    lieu_naissance VARCHAR(100) NOT NULL,
    nationalite VARCHAR(50) DEFAULT 'Malgache',
    pere_nom_prenom VARCHAR(150),
    mere_nom_prenom VARCHAR(150),
    situation_familiale situation_familiale_enum,
    -- CIN
    cin_numero VARCHAR(30) NOT NULL UNIQUE,
    cin_date_delivrance DATE NOT NULL,
    cin_lieu_delivrance VARCHAR(100) NOT NULL,
    cin_duplicata_date DATE,
    cin_duplicata_lieu VARCHAR(100),
    -- PERMIS
    permis_conduire_numero VARCHAR(50),
    permis_conduire_date_delivrance DATE,
    permis_conduire_lieu_delivrance VARCHAR(100),
    -- COORDONNEES
    adresse TEXT NOT NULL,
    ville VARCHAR(100),
    telephone VARCHAR(30) NOT NULL,
    email VARCHAR(150),
    -- IDENTIFIANTS SOCIAUX
    num_affiliation_cnaps VARCHAR(30) UNIQUE,
    -- INFORMATIONS FICHE
    photo_url VARCHAR(255),
    date_declaration DATE,
    -- SOFT DELETE
    est_actif BOOLEAN DEFAULT TRUE,
    date_desactivation TIMESTAMP,
    motif_desactivation motif_desactivation_enum,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_employe_nom_prenom ON employe(nom, prenom);
CREATE INDEX idx_employe_matricule ON employe(matricule);
CREATE INDEX idx_employe_cin ON employe(cin_numero);
-- ============================================================
-- CONJOINT
-- ============================================================
CREATE TABLE employe_conjoint (
    id_conjoint SERIAL PRIMARY KEY,
    id_employe INTEGER NOT NULL,
    nom VARCHAR(100) NOT NULL,
    prenom VARCHAR(100) NOT NULL,
    date_naissance DATE,
    lieu_naissance VARCHAR(100),
    societe VARCHAR(100),
    fonction VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_conjoint_employe FOREIGN KEY(id_employe) REFERENCES employe(id_employe) ON DELETE CASCADE
);
-- ============================================================
-- ENFANT
-- ============================================================
CREATE TABLE employe_enfant (
    id_enfant SERIAL PRIMARY KEY,
    id_employe INTEGER NOT NULL,
    nom VARCHAR(100) NOT NULL,
    prenom VARCHAR(100) NOT NULL,
    sexe sexe_enum,
    date_naissance DATE NOT NULL,
    lieu_naissance VARCHAR(100),
    a_charge BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_enfant_employe FOREIGN KEY(id_employe) REFERENCES employe(id_employe) ON DELETE CASCADE
);
-- ============================================================
-- CONTACT URGENCE
-- ============================================================
CREATE TABLE employe_contact_urgence (
    id_contact SERIAL PRIMARY KEY,
    id_employe INTEGER NOT NULL,
    nom VARCHAR(100) NOT NULL,
    prenom VARCHAR(100) NOT NULL,
    lien_parente lien_parente_enum,
    adresse TEXT,
    telephone VARCHAR(30) NOT NULL,
    societe VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_contact_employe FOREIGN KEY(id_employe) REFERENCES employe(id_employe) ON DELETE CASCADE
);
-- ============================================================
-- COMPTE BANCAIRE
-- ============================================================
CREATE TABLE employe_compte_banque (
    id_compte_banque SERIAL PRIMARY KEY,
    id_employe INTEGER NOT NULL,
    id_banque INTEGER NOT NULL,
    agence VARCHAR(100),
    num_compte_rib VARCHAR(50) NOT NULL,
    titulaire_compte VARCHAR(150),
    est_principal BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_compte_employe FOREIGN KEY(id_employe) REFERENCES employe(id_employe) ON DELETE CASCADE,
    CONSTRAINT fk_compte_banque FOREIGN KEY(id_banque) REFERENCES ref_banque(id_banque)
);
-- ============================================================
-- UN SEUL COMPTE PRINCIPAL PAR EMPLOYE
-- ============================================================
CREATE UNIQUE INDEX uq_compte_principal_employe ON employe_compte_banque(id_employe)
WHERE est_principal = TRUE;

-- ============================================================
-- VUE EMPLOYE COMPLET (v_employe_cpl)
-- ============================================================
CREATE OR REPLACE VIEW v_employe_cpl AS
SELECT 
    e.id_employe,
    e.matricule,
    e.nom,
    e.prenom,
    CONCAT(e.nom, ' ', e.prenom) AS nom_complet,
    e.sexe,
    e.date_naissance,
    EXTRACT(YEAR FROM AGE(CURRENT_DATE, e.date_naissance))::INTEGER AS age,
    e.lieu_naissance,
    e.nationalite,
    e.pere_nom_prenom,
    e.mere_nom_prenom,
    e.situation_familiale,
    -- CIN
    e.cin_numero,
    e.cin_date_delivrance,
    e.cin_lieu_delivrance,
    e.cin_duplicata_date,
    e.cin_duplicata_lieu,
    -- PERMIS
    e.permis_conduire_numero,
    e.permis_conduire_date_delivrance,
    e.permis_conduire_lieu_delivrance,
    -- COORDONNEES
    e.adresse,
    e.ville,
    e.telephone,
    e.email,
    -- IDENTIFIANTS SOCIAUX
    e.num_affiliation_cnaps,
    -- FICHE & STATUT
    e.photo_url,
    e.date_declaration,
    e.est_actif,
    e.date_desactivation,
    e.motif_desactivation,
    e.created_at,
    e.updated_at,
    -- CONJOINT SYNTHESE
    cj.nom AS conjoint_nom,
    cj.prenom AS conjoint_prenom,
    CONCAT(cj.nom, ' ', cj.prenom) AS conjoint_nom_complet,
    cj.societe AS conjoint_societe,
    cj.fonction AS conjoint_fonction,
    -- COMPTE BANCAIRE PRINCIPAL SYNTHESE
    cb.id_compte_banque AS id_compte_banque_principal,
    cb.id_banque AS id_banque_principal,
    rb.code_banque AS code_banque_principal,
    rb.nom_banque AS nom_banque_principal,
    cb.agence AS agence_banque_principal,
    cb.num_compte_rib AS num_compte_rib_principal,
    cb.titulaire_compte AS titulaire_compte_principal,
    -- METRIQUES
    COALESCE(enf.nb_enfants, 0) AS nb_enfants,
    COALESCE(enf.nb_enfants_a_charge, 0) AS nb_enfants_a_charge,
    COALESCE(ct.nb_contacts_urgence, 0) AS nb_contacts_urgence
FROM employe e
LEFT JOIN employe_conjoint cj ON e.id_employe = cj.id_employe
LEFT JOIN employe_compte_banque cb ON e.id_employe = cb.id_employe AND cb.est_principal = TRUE
LEFT JOIN ref_banque rb ON cb.id_banque = rb.id_banque
LEFT JOIN (
    SELECT 
        id_employe,
        COUNT(*)::INTEGER AS nb_enfants,
        COUNT(CASE WHEN a_charge = TRUE THEN 1 END)::INTEGER AS nb_enfants_a_charge
    FROM employe_enfant
    GROUP BY id_employe
) enf ON e.id_employe = enf.id_employe
LEFT JOIN (
    SELECT 
        id_employe,
        COUNT(*)::INTEGER AS nb_contacts_urgence
    FROM employe_contact_urgence
    GROUP BY id_employe
) ct ON e.id_employe = ct.id_employe;