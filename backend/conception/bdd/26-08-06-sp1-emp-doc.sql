DROP TABLE IF EXISTS document_rh CASCADE;
DROP TABLE IF EXISTS ref_type_document CASCADE;
-- ============================================================
-- TABLE TYPE DOCUMENT
-- ============================================================
CREATE TABLE ref_type_document (
    id_type_document SERIAL PRIMARY KEY,
    libelle VARCHAR(100) NOT NULL UNIQUE,
    categorie VARCHAR(50) NOT NULL,
    description TEXT,
    obligatoire BOOLEAN DEFAULT FALSE,
    extension_autorisee VARCHAR(100) DEFAULT 'pdf,jpg,jpeg,png',
    taille_max_mo INTEGER DEFAULT 10,
    actif BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
-- ============================================================
-- TABLE DOCUMENT RH
-- ============================================================
CREATE TABLE document_rh (
    id_document SERIAL PRIMARY KEY,
    id_employe INTEGER NOT NULL,
    id_type_document INTEGER NOT NULL,
    -- Informations fichier
    nom_original VARCHAR(255) NOT NULL,
    nom_stockage VARCHAR(255) NOT NULL,
    chemin_fichier VARCHAR(500) NOT NULL,
    extension VARCHAR(10),
    taille_octet BIGINT,
    -- Informations document
    date_document DATE,
    commentaire TEXT,
    -- Utilisateur ayant effectué l'ajout
    uploaded_by INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_document_employe FOREIGN KEY(id_employe) REFERENCES employe(id_employe) ON DELETE CASCADE,
    CONSTRAINT fk_document_type FOREIGN KEY(id_type_document) REFERENCES ref_type_document(id_type_document)
);
-- ============================================================
-- INDEX
-- ============================================================
CREATE INDEX idx_document_employe ON document_rh(id_employe);
CREATE INDEX idx_document_type ON document_rh(id_type_document);
-- ============================================================
-- DONNEES INITIALES TYPES DOCUMENTS
-- ============================================================
INSERT INTO ref_type_document (
        libelle,
        categorie,
        description,
        obligatoire
    )
VALUES (
        'Fiche renseignement',
        'Administratif',
        'Fiche personnelle remplie par l employé',
        TRUE
    ),
    (
        'CIN Recto',
        'Identité',
        'Copie recto de la carte identité nationale',
        TRUE
    ),
    (
        'CIN Verso',
        'Identité',
        'Copie verso de la carte identité nationale',
        TRUE
    ),
    (
        'Permis de conduire',
        'Identité',
        'Copie du permis de conduire',
        FALSE
    ),
    (
        'CV',
        'Recrutement',
        'Curriculum vitae',
        FALSE
    ),
    (
        'Contrat de travail',
        'Contrat',
        'Contrat signé entre employé et entreprise',
        FALSE
    ),
    (
        'Diplôme',
        'Formation',
        'Diplôme ou certificat de formation',
        FALSE
    ),
    (
        'RIB',
        'Paie',
        'Coordonnées bancaires',
        FALSE
    );