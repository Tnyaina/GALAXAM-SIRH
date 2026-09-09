-- Nettoyage préalable des tables pour permettre l'exécution répétée du script
DROP TABLE IF EXISTS role_menu;
DROP TABLE IF EXISTS ref_menu;
DROP TABLE IF EXISTS audit_log;

CREATE TABLE audit_log (
    id_audit SERIAL PRIMARY KEY,
    id_utilisateur INT,                                -- NULL si action système automatisée
    action VARCHAR(50) NOT NULL,                       -- 'CREATION', 'MODIFICATION', 'DESACTIVATION', 'ACTIVATION', 'SUPPRESSION'
    nom_table VARCHAR(50) NOT NULL,                     -- Ex: 'pointage', 'employe'
    id_enregistrement VARCHAR(100) NOT NULL,          -- Converti en String pour tolérer INT ou UUID
    anciennes_valeurs JSONB,                          -- State AVANT
    nouvelles_valeurs JSONB,                          -- State APRÈS
    adresse_ip VARCHAR(45),                            -- IPv4 / IPv6
    date_action TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_audit_utilisateur 
        FOREIGN KEY (id_utilisateur) 
        REFERENCES utilisateur(id_utilisateur) 
        ON DELETE SET NULL                            -- Conserve l'historique si un utilisateur est supprimé
);

-- Index pour accélérer les recherches fréquentes dans le Back-Office Admin
CREATE INDEX idx_audit_table_rec ON audit_log(nom_table, id_enregistrement);
CREATE INDEX idx_audit_utilisateur ON audit_log(id_utilisateur);
CREATE INDEX idx_audit_date ON audit_log(date_action DESC);

-- 1. Table des Menus (Arborescente)
CREATE TABLE ref_menu (
    id_menu VARCHAR(50) PRIMARY KEY,
    libelle VARCHAR(100) NOT NULL,
    icone VARCHAR(50),                     -- Nom de l'icône Lucide React (ex: 'Clock', 'Users')
    href VARCHAR(255),                      -- Route React Router (ex: '/pointages', '/employes')
    rang INT DEFAULT 1,                     -- Ordre d'affichage
    niveau INT DEFAULT 1,                   -- 1: Racine, 2: Sous-menu, 3: Action
    id_pere VARCHAR(50),                    -- Auto-référence pour l'arborescence
    CONSTRAINT fk_menu_pere FOREIGN KEY (id_pere) REFERENCES ref_menu(id_menu) ON DELETE CASCADE
);

-- 2. Table de liaison pour la Sécurité : 'Qui voit quoi"
CREATE TABLE role_menu (
    id_role INT NOT NULL,
    id_menu VARCHAR(50) NOT NULL,
    PRIMARY KEY (id_role, id_menu),
    CONSTRAINT fk_role_menu_role FOREIGN KEY (id_role) REFERENCES ref_role(id_role) ON DELETE CASCADE,
    CONSTRAINT fk_role_menu_menu FOREIGN KEY (id_menu) REFERENCES ref_menu(id_menu) ON DELETE CASCADE
);

-- =====================================================================
-- DONNÉES D'INITIALISATION : MENUS & HABILITATIONS (ROLE_MENU)
-- =====================================================================

-- 1. Menus de Niveau 1 (Navigation principale SIRH)
INSERT INTO ref_menu (id_menu, libelle, icone, href, rang, niveau, id_pere) VALUES
('DASHBOARD', 'Tableau de bord', 'LayoutDashboard', '/dashboard', 1, 1, NULL),
('GESTION_PERSONNEL', 'Gestion du Personnel', 'Users', NULL, 2, 1, NULL),
('CONGES_MODULE', 'Congés & Absences', 'Palmtree', '/conges', 3, 1, NULL),
('POINTAGE', 'Pointage & Présences', 'Clock', '/pointages', 4, 1, NULL),
('PAIE_MODULE', 'Paie & Rémunération', 'Banknote', '/paie', 5, 1, NULL),
('ADMIN_MODULE', 'Administration & Paramétrage', 'Settings', NULL, 6, 1, NULL);

-- 2. Sous-menus de Niveau 2 - Gestion du Personnel
INSERT INTO ref_menu (id_menu, libelle, icone, href, rang, niveau, id_pere) VALUES
('PERS_EMPLOYES', 'Fiches Employés', 'Contact', '/employes', 1, 2, 'GESTION_PERSONNEL'),
('PERS_CARRIERES', 'Contrats & Carrières', 'Briefcase', '/carrieres', 2, 2, 'GESTION_PERSONNEL');

-- 3. Sous-menus de Niveau 2 - Congés & Absences
INSERT INTO ref_menu (id_menu, libelle, icone, href, rang, niveau, id_pere) VALUES
('CONGES_GENERAL', 'Vue Générale', 'Calendar', '/conges?tab=soldes', 1, 2, 'CONGES_MODULE'),
('CONGES_DEMANDES','Demandes & Validation', 'ClipboardCheck', '/conges?tab=demandes', 2, 2, 'CONGES_MODULE');

-- 4. Sous-menus de Niveau 2 - Administration & Paramétrage
INSERT INTO ref_menu (id_menu, libelle, icone, href, rang, niveau, id_pere) VALUES
('ADMIN_SECURITE', 'Sécurité & Accès', 'ShieldCheck', '/admin/securite', 1, 2, 'ADMIN_MODULE'),
('ADMIN_ORGANISATION', 'Organisation & Structure', 'Network', '/admin/organisation', 2, 2, 'ADMIN_MODULE'),
('ADMIN_REFERENTIELS', 'Référentiels RH', 'BookType', '/admin/referentiels-rh', 3, 2, 'ADMIN_MODULE'),
('ADMIN_PARAMETRES', 'Paramétrage des Modules', 'SlidersHorizontal', '/admin/parametres', 4, 2, 'ADMIN_MODULE');

-- 5. Attribution des Menus aux Rôles (ROLE_ADMIN = id_role 1, ROLE_AGENT_POINTAGE = id_role 2)

-- ROLE_ADMIN (id_role 1) : Accès complet à l'ensemble du SIRH
INSERT INTO role_menu (id_role, id_menu) VALUES
(1, 'DASHBOARD'),
(1, 'GESTION_PERSONNEL'),
(1, 'PERS_EMPLOYES'),
(1, 'PERS_CARRIERES'),
(1, 'CONGES_MODULE'),
(1, 'CONGES_GENERAL'),
(1, 'CONGES_DEMANDES'),
(1, 'POINTAGE'),
(1, 'PAIE_MODULE'),
(1, 'ADMIN_MODULE'),
(1, 'ADMIN_SECURITE'),
(1, 'ADMIN_ORGANISATION'),
(1, 'ADMIN_REFERENTIELS'),
(1, 'ADMIN_PARAMETRES');

-- ROLE_AGENT_POINTAGE (id_role 2) : Accès au Tableau de bord et au module de Pointage
INSERT INTO role_menu (id_role, id_menu) VALUES
(2, 'POINTAGE');

DELETE FROM ref_menu WHERE id_menu IN ('POINTAGE_CLOTURE', 'POINTAGE_DEROGATIONS', 'POINTAGE_GENERAL');

INSERT INTO ref_menu (id_menu, libelle, icone, href, rang, niveau, id_pere) VALUES
    ('POINTAGE_JOURNALIER',  'Saisie Quotidienne',              'ClockCheck',    '/pointages?tab=saisie',      1, 2, 'POINTAGE'),
    ('POINTAGE_SUIVI',       'Suivi de Période & Transmission', 'CalendarCheck', '/pointages?tab=suivi',       2, 2, 'POINTAGE'),
    ('POINTAGE_ANOMALIES',   'Anomalies & Retards',             'AlertTriangle', '/pointages?tab=anomalies',   3, 2, 'POINTAGE'),
    ('POINTAGE_SYNTHESE',    'Synthèse & Clôture',              'Layers',        '/pointages?tab=synthese',    4, 2, 'POINTAGE'),
    ('POINTAGE_VALIDATION',  'Arbitrage DAF',                   'ShieldCheck',   '/pointages?tab=validation',  5, 2, 'POINTAGE')
ON CONFLICT (id_menu) DO UPDATE SET libelle = EXCLUDED.libelle, href = EXCLUDED.href, rang = EXCLUDED.rang, icone = EXCLUDED.icone, id_pere = EXCLUDED.id_pere;

DELETE FROM ref_menu WHERE id_menu = 'PAIE_VALIDATION';

INSERT INTO ref_menu (id_menu, libelle, icone, href, rang, niveau, id_pere) VALUES
    ('PAIE_PERIODES',        'Périodes de Paie & Clôture',      'CalendarRange',  '/paie?tab=periodes',        1, 2, 'PAIE_MODULE'),
    ('PAIE_VARIABLES',       'Variables & Ajustements',         'Sliders',        '/paie?tab=variables',       2, 2, 'PAIE_MODULE'),
    ('PAIE_BULLETINS',       'Bulletins de Paie',               'FileSpreadsheet','/paie?tab=bulletins',       3, 2, 'PAIE_MODULE')
ON CONFLICT (id_menu) DO UPDATE SET libelle = EXCLUDED.libelle, href = EXCLUDED.href, rang = EXCLUDED.rang, icone = EXCLUDED.icone, id_pere = EXCLUDED.id_pere;

-- Attribution fine par rôle :
-- id_role 1 = ROLE_ADMIN / DAF (accès complet : Saisie, Suivi, Anomalies, Synthèse, Arbitrage DAF, Périodes, Variables, Bulletins)
-- id_role 2 = ROLE_AGENT_POINTAGE (accès exploitation : Saisie Quotidienne, Suivi de Période, Synthèse & Clôture)
INSERT INTO role_menu (id_role, id_menu) VALUES
    (1, 'POINTAGE_JOURNALIER'),
    (1, 'POINTAGE_SUIVI'),
    (1, 'POINTAGE_ANOMALIES'),
    (1, 'POINTAGE_SYNTHESE'),
    (1, 'POINTAGE_VALIDATION'),
    (1, 'PAIE_PERIODES'),
    (1, 'PAIE_VARIABLES'),
    (1, 'PAIE_BULLETINS'),
    (2, 'POINTAGE_JOURNALIER'),
    (2, 'POINTAGE_SUIVI'),
    (2, 'POINTAGE_SYNTHESE')
ON CONFLICT DO NOTHING;


