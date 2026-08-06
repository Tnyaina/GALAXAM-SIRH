-- ============================================================
-- SUPPRESSION DES OBJETS EXISTANTS
-- ============================================================
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