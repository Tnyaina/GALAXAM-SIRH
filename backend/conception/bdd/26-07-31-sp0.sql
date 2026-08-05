CREATE TABLE audit_log (
    id_audit SERIAL PRIMARY KEY,
    id_utilisateur INT,                                -- NULL si action système automatisée
    action VARCHAR(10) NOT NULL,                       -- 'INSERT', 'UPDATE', 'DELETE'
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

-- 2. Table de liaison pour la Sécurité : "Qui voit quoi"
CREATE TABLE role_menu (
    id_role INT NOT NULL,
    id_menu VARCHAR(50) NOT NULL,
    PRIMARY KEY (id_role, id_menu),
    CONSTRAINT fk_role_menu_role FOREIGN KEY (id_role) REFERENCES ref_role(id_role) ON DELETE CASCADE,
    CONSTRAINT fk_role_menu_menu FOREIGN KEY (id_menu) REFERENCES ref_menu(id_menu) ON DELETE CASCADE
);