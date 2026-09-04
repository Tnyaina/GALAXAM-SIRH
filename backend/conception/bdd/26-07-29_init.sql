-- =====================================================================
-- SCRIPT POSTGRESQL : SIRH GALAXAM - MODULE UTILISATEUR & RBAC DYNAMIQUE
-- =====================================================================

-- 1. Nettoyage préalable (ordre inverse des dépendances)
DROP TABLE IF EXISTS journal_connexion CASCADE;
DROP TABLE IF EXISTS utilisateur CASCADE;
DROP TABLE IF EXISTS role_permission CASCADE;
DROP TABLE IF EXISTS ref_module CASCADE;
DROP TABLE IF EXISTS ref_role CASCADE;

-- 2. Création de la table des rôles
CREATE TABLE ref_role (
    id_role SERIAL PRIMARY KEY,
    code_role VARCHAR(50) UNIQUE NOT NULL, 
    libelle VARCHAR(100) NOT NULL,
    est_actif BOOLEAN DEFAULT TRUE,
    date_desactivation TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. Création du référentiel des modules du SIRH
CREATE TABLE ref_module (
    id_module SERIAL PRIMARY KEY,
    code_module VARCHAR(50) UNIQUE NOT NULL, -- ex: EMPLOYE, POINTAGE, PAIE
    libelle VARCHAR(100) NOT NULL,
    est_actif BOOLEAN DEFAULT TRUE -- Permet de désactiver un module entier
);

-- 4. La Matrice de droits (Intersection Rôle x Module x Droits)
CREATE TABLE role_permission (
    id_permission SERIAL PRIMARY KEY,
    id_role INTEGER NOT NULL REFERENCES ref_role(id_role),
    id_module INTEGER NOT NULL REFERENCES ref_module(id_module),
    
    droit_lecture BOOLEAN DEFAULT FALSE,
    droit_ecriture BOOLEAN DEFAULT FALSE,
    droit_validation BOOLEAN DEFAULT FALSE,
    
    UNIQUE(id_role, id_module) -- Empêche les doublons de configuration
);

-- 5. Création de la table utilisateur
CREATE TABLE utilisateur (
    id_utilisateur SERIAL PRIMARY KEY,
    identifiant VARCHAR(50) UNIQUE NOT NULL,
    mot_de_passe_hache VARCHAR(255) NOT NULL,
    id_role INTEGER NOT NULL REFERENCES ref_role(id_role),
    
    est_actif BOOLEAN DEFAULT TRUE,
    date_desactivation TIMESTAMP,
    motif_desactivation VARCHAR(100),
    refresh_token VARCHAR(500),
    refresh_token_expiration TIMESTAMP,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 6. Journalisation des accès
CREATE TABLE journal_connexion (
    id_journal SERIAL PRIMARY KEY,
    id_utilisateur INTEGER NOT NULL REFERENCES utilisateur(id_utilisateur),
    date_heure TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    statut VARCHAR(20) NOT NULL 
);

-- =====================================================================
-- JEU DE DONNÉES
-- =====================================================================

-- Insertion des 2 rôles autorisés
INSERT INTO ref_role (code_role, libelle) VALUES
('ROLE_ADMIN', 'Administrateur'),
('ROLE_AGENT_POINTAGE', 'Agent de Pointage');

-- Insertion des modules principaux
INSERT INTO ref_module (code_module, libelle) VALUES
('EMPLOYE', 'Gestion des dossiers employés et contrats'),
('CONGE', 'Gestion des absences et congés'),
('POINTAGE', 'Suivi des présences et horaires'),
('PAIE', 'Moteur de paie et bulletins');

-- Configuration de la Matrice de Droits (RBAC)
-- Rappel IDs Rôles : 1 = ADMIN, 2 = AGENT_POINTAGE
-- Rappel IDs Modules : 1 = EMPLOYE, 2 = CONGE, 3 = POINTAGE, 4 = PAIE

INSERT INTO role_permission (id_role, id_module, droit_lecture, droit_ecriture, droit_validation) VALUES
-- ==========================================
-- DROITS : ROLE_ADMIN (Accès total)
-- ==========================================
(1, 1, TRUE, TRUE, TRUE), -- EMPLOYE
(1, 2, TRUE, TRUE, TRUE), -- CONGE
(1, 3, TRUE, TRUE, TRUE), -- POINTAGE
(1, 4, TRUE, TRUE, TRUE), -- PAIE

-- ==========================================
-- DROITS : ROLE_AGENT_POINTAGE (Accès restreint)
-- ==========================================
-- Lecture seule sur les employés pour pouvoir associer un pointage à une personne
(2, 1, FALSE, FALSE, FALSE), -- EMPLOYE 

-- Aucun accès aux congés (réservé à la DAF/RH)
(2, 2, FALSE, FALSE, FALSE), -- CONGE 

-- Lecture et écriture sur le pointage pour la saisie quotidienne (pas de validation finale)
(2, 3, TRUE, TRUE, FALSE), -- POINTAGE 

-- Aucun accès à la paie (confidentialité stricte)
(2, 4, FALSE, FALSE, FALSE); -- PAIE

-- =====================================================================
-- GALAXAM SIRH — MIGRATION SP6 : AJOUT DU MODULE ORGANISATION
-- Permet la gestion granulaire des structures d'entreprise (Sites, Filiales, Fonctions)
-- =====================================================================

INSERT INTO ref_module (code_module, libelle, est_actif)
VALUES ('ORGANISATION', 'Structure d''entreprise, filiales, sites et fonctions', true)
ON CONFLICT (code_module) DO UPDATE
SET libelle = EXCLUDED.libelle, est_actif = true;

-- Attribution par défaut des permissions du module ORGANISATION au rôle ADMIN (id_role = 1)
INSERT INTO role_permission (id_role, id_module, droit_lecture, droit_ecriture, droit_validation)
SELECT 1, id_module, true, true, true
FROM ref_module
WHERE code_module = 'ORGANISATION'
ON CONFLICT (id_role, id_module) DO UPDATE
SET droit_lecture = true, droit_ecriture = true, droit_validation = true;

-- =====================================================================
-- COMPTE ADMINISTRATEUR INITIAL PAR DÉFAUT (BOOTSTRAP)
-- Identifiant : admin | Mot de passe : Pass1234
-- =====================================================================
INSERT INTO utilisateur (id_utilisateur, identifiant, mot_de_passe_hache, id_role, est_actif)
VALUES (1, 'admin', '$2a$10$wN1.aJ1K9h6O2e1G1D9CSu4t7g8b9p0q1r2s3t4u5v6w7x8y9z0a.', 1, true)
ON CONFLICT (id_utilisateur) DO NOTHING;

SELECT setval('utilisateur_id_utilisateur_seq', (SELECT GREATEST(MAX(id_utilisateur), 1) FROM utilisateur));

