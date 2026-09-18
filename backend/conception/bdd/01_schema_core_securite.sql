-- =====================================================================
-- GALAXAM SIRH — MODULE 01 : CORE, SÉCURITÉ & AUDIT
-- Script canonique final (Zéro ALTER TABLE, Zéro OWNER)
-- =====================================================================

-- 1. Nettoyage préalable (ordre inverse des dépendances)
DROP TABLE IF EXISTS role_menu CASCADE;
DROP TABLE IF EXISTS ref_menu CASCADE;
DROP TABLE IF EXISTS audit_log CASCADE;
DROP TABLE IF EXISTS journal_connexion CASCADE;
DROP TABLE IF EXISTS utilisateur CASCADE;
DROP TABLE IF EXISTS role_permission CASCADE;
DROP TABLE IF EXISTS ref_module CASCADE;
DROP TABLE IF EXISTS ref_role CASCADE;
DROP TABLE IF EXISTS parametre_global CASCADE;

-- 2. Référentiel des Rôles
CREATE TABLE ref_role (
    id_role SERIAL PRIMARY KEY,
    code_role VARCHAR(50) UNIQUE NOT NULL, 
    libelle VARCHAR(100) NOT NULL,
    est_actif BOOLEAN DEFAULT TRUE,
    date_desactivation TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. Référentiel des Modules SIRH
CREATE TABLE ref_module (
    id_module SERIAL PRIMARY KEY,
    code_module VARCHAR(50) UNIQUE NOT NULL,
    libelle VARCHAR(100) NOT NULL,
    est_actif BOOLEAN DEFAULT TRUE
);

-- 4. Matrice Rôle x Module x Droits
CREATE TABLE role_permission (
    id_permission SERIAL PRIMARY KEY,
    id_role INTEGER NOT NULL REFERENCES ref_role(id_role) ON DELETE CASCADE,
    id_module INTEGER NOT NULL REFERENCES ref_module(id_module) ON DELETE CASCADE,
    droit_lecture BOOLEAN DEFAULT FALSE,
    droit_ecriture BOOLEAN DEFAULT FALSE,
    droit_validation BOOLEAN DEFAULT FALSE,
    UNIQUE(id_role, id_module)
);

-- 5. Utilisateurs du Système
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

-- 6. Journal des Connexions
CREATE TABLE journal_connexion (
    id_journal SERIAL PRIMARY KEY,
    id_utilisateur INTEGER NOT NULL REFERENCES utilisateur(id_utilisateur) ON DELETE CASCADE,
    date_heure TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    statut VARCHAR(50) NOT NULL,
    adresse_ip VARCHAR(45),
    user_agent TEXT
);

-- 7. Piste d'Audit Globale
CREATE TABLE audit_log (
    id_audit SERIAL PRIMARY KEY,
    id_utilisateur INT REFERENCES utilisateur(id_utilisateur) ON DELETE SET NULL,
    action VARCHAR(50) NOT NULL,
    nom_table VARCHAR(50) NOT NULL,
    id_enregistrement VARCHAR(100) NOT NULL,
    anciennes_valeurs JSONB,
    nouvelles_valeurs JSONB,
    adresse_ip VARCHAR(45),
    date_action TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_table_rec ON audit_log(nom_table, id_enregistrement);
CREATE INDEX idx_audit_utilisateur ON audit_log(id_utilisateur);
CREATE INDEX idx_audit_date ON audit_log(date_action DESC);

-- 8. Menus Arborescents & Habilitations UI
CREATE TABLE ref_menu (
    id_menu VARCHAR(50) PRIMARY KEY,
    libelle VARCHAR(100) NOT NULL,
    icone VARCHAR(50),
    href VARCHAR(255),
    rang INT DEFAULT 1,
    niveau INT DEFAULT 1,
    id_pere VARCHAR(50) REFERENCES ref_menu(id_menu) ON DELETE CASCADE
);

CREATE TABLE role_menu (
    id_role INT NOT NULL REFERENCES ref_role(id_role) ON DELETE CASCADE,
    id_menu VARCHAR(50) NOT NULL REFERENCES ref_menu(id_menu) ON DELETE CASCADE,
    PRIMARY KEY (id_role, id_menu)
);

-- 9. Paramètres Globaux
CREATE TABLE parametre_global (
    id_parametre SERIAL PRIMARY KEY,
    code_parametre VARCHAR(50) NOT NULL,
    categorie VARCHAR(30) NOT NULL DEFAULT 'GENERAL',
    valeur VARCHAR(100) NOT NULL,
    description TEXT,
    date_debut DATE NOT NULL,
    date_fin DATE,
    est_actif BOOLEAN NOT NULL DEFAULT TRUE,
    est_desactivable BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_parametre_global_code_date UNIQUE (code_parametre, date_debut)
);

CREATE INDEX idx_parametre_actif ON parametre_global(code_parametre) WHERE date_fin IS NULL;
CREATE INDEX idx_parametre_categorie ON parametre_global(categorie);
CREATE INDEX idx_role_permission_module ON role_permission(id_module);
CREATE INDEX idx_utilisateur_role ON utilisateur(id_role);
CREATE INDEX idx_journal_connexion_utilisateur ON journal_connexion(id_utilisateur);
CREATE INDEX idx_ref_menu_pere ON ref_menu(id_pere);
CREATE INDEX idx_role_menu_menu ON role_menu(id_menu);

-- =====================================================================
-- DONNÉES DE RÉFÉRENCE DE BASE (INITIAL SEED)
-- =====================================================================

-- Rôles
INSERT INTO ref_role (id_role, code_role, libelle, est_actif) VALUES
(1, 'ROLE_ADMIN', 'Administrateur', true),
(2, 'ROLE_AGENT_POINTAGE', 'Agent de Pointage', true);

SELECT setval('ref_role_id_role_seq', (SELECT GREATEST(MAX(id_role), 1) FROM ref_role));

-- Modules (Archives 26-07-29_init.sql)
INSERT INTO ref_module (id_module, code_module, libelle, est_actif) VALUES
(1, 'EMPLOYE', 'Gestion des dossiers employés et contrats', true),
(2, 'CONGE', 'Gestion des absences et congés', true),
(3, 'POINTAGE', 'Suivi des présences et horaires', true),
(4, 'PAIE', 'Moteur de paie et bulletins', true),
(5, 'ORGANISATION', 'Structure d''entreprise, filiales, sites et fonctions', true);

SELECT setval('ref_module_id_module_seq', (SELECT GREATEST(MAX(id_module), 5) FROM ref_module));

-- Matrice Rôle x Permissions (Archives 26-07-29_init.sql)
INSERT INTO role_permission (id_role, id_module, droit_lecture, droit_ecriture, droit_validation) VALUES
(1, 1, true, true, true),
(1, 2, true, true, true),
(1, 3, true, true, true),
(1, 4, true, true, true),
(1, 5, true, true, true),
(2, 1, false, false, false),
(2, 2, false, false, false),
(2, 3, true, true, false),
(2, 4, false, false, false),
(2, 5, false, false, false);

-- Menus Niveau 1 (Archives 26-07-31-sp0.sql)
INSERT INTO ref_menu (id_menu, libelle, icone, href, rang, niveau, id_pere) VALUES
('DASHBOARD',         'Tableau de bord',             'LayoutDashboard', '/dashboard', 1, 1, NULL),
('GESTION_PERSONNEL', 'Gestion du Personnel',        'Users',           NULL,        2, 1, NULL),
('CONGES_MODULE',     'Congés & Absences',           'Palmtree',        '/conges',   3, 1, NULL),
('POINTAGE',          'Pointage & Présences',        'Clock',           '/pointages',4, 1, NULL),
('PAIE_MODULE',       'Paie & Rémunération',         'Banknote',        NULL,        5, 1, NULL),
('ADMIN_MODULE',      'Administration & Paramétrage', 'Settings',        NULL,        6, 1, NULL);

-- Menus Niveau 2 (Archives 26-07-31-sp0.sql)
-- Personnel
INSERT INTO ref_menu (id_menu, libelle, icone, href, rang, niveau, id_pere) VALUES
('PERS_EMPLOYES',     'Fiches Employés',             'Contact',         '/employes',                 1, 2, 'GESTION_PERSONNEL'),
('PERS_CARRIERES',    'Contrats & Carrières',         'Briefcase',       '/carrieres',                2, 2, 'GESTION_PERSONNEL');

-- Congés
INSERT INTO ref_menu (id_menu, libelle, icone, href, rang, niveau, id_pere) VALUES
('CONGES_GENERAL',    'Vue Générale',                'Calendar',        '/conges?tab=soldes',        1, 2, 'CONGES_MODULE'),
('CONGES_DEMANDES',   'Demandes & Validation',       'ClipboardCheck',  '/conges?tab=demandes',      2, 2, 'CONGES_MODULE');

-- Administration
INSERT INTO ref_menu (id_menu, libelle, icone, href, rang, niveau, id_pere) VALUES
('ADMIN_SECURITE',     'Sécurité & Accès',           'ShieldCheck',     '/admin/securite',           1, 2, 'ADMIN_MODULE'),
('ADMIN_ORGANISATION', 'Organisation & Structure',    'Network',         '/admin/organisation',       2, 2, 'ADMIN_MODULE'),
('ADMIN_REFERENTIELS', 'Référentiels RH',             'BookType',        '/admin/referentiels-rh',    3, 2, 'ADMIN_MODULE'),
('ADMIN_PARAMETRES',   'Paramétrage des Modules',     'SlidersHorizontal','/admin/parametres',       4, 2, 'ADMIN_MODULE');

-- Pointage (Consolidé Sprint 10 & 11 : Cockpit Unifié 3 étapes)
INSERT INTO ref_menu (id_menu, libelle, icone, href, rang, niveau, id_pere) VALUES
('POINTAGE_JOURNALIER',  'Saisie Quotidienne',    'ClockCheck',    '/pointages?tab=saisie',     1, 2, 'POINTAGE'),
('POINTAGE_SUIVI',       'Suivi & Anomalies',     'CalendarCheck', '/pointages?tab=suivi',      2, 2, 'POINTAGE'),
('POINTAGE_VALIDATION',  'Contrôle & Validation', 'ShieldCheck',   '/pointages?tab=validation', 3, 2, 'POINTAGE');

-- Paie (Consolidé Sprint 2B : sous-menus majeurs de gestion de paie)
INSERT INTO ref_menu (id_menu, libelle, icone, href, rang, niveau, id_pere) VALUES
('PAIE_CAMPAGNE',  'Campagne Active',         'LayoutDashboard',   '/paie?tab=campagne', 1, 2, 'PAIE_MODULE'),
('PAIE_VARIABLES', 'Variables & Primes',       'SlidersHorizontal', '/paie?tab=variables',2, 2, 'PAIE_MODULE'),
('PAIE_REVUE',     'Revue des Bulletins',      'Banknote',          '/paie?tab=cockpit',  3, 2, 'PAIE_MODULE'),
('PAIE_PERIODES',  'Historique des Périodes',  'CalendarRange',     '/paie?tab=periodes', 4, 2, 'PAIE_MODULE'),
('PAIE_EXPORTS',   'Déclarations & Sorties',   'FileText',          '/paie?tab=exports',  5, 2, 'PAIE_MODULE');

-- Habilitations Menus (Role x Menu) (Archives Finales Sprint 11)
-- ROLE_ADMIN (id_role = 1) : Accès complet
INSERT INTO role_menu (id_role, id_menu) VALUES
(1, 'DASHBOARD'),
(1, 'GESTION_PERSONNEL'),
(1, 'PERS_EMPLOYES'),
(1, 'PERS_CARRIERES'),
(1, 'CONGES_MODULE'),
(1, 'CONGES_GENERAL'),
(1, 'CONGES_DEMANDES'),
(1, 'POINTAGE'),
(1, 'POINTAGE_JOURNALIER'),
(1, 'POINTAGE_SUIVI'),
(1, 'POINTAGE_VALIDATION'),
(1, 'PAIE_MODULE'),
(1, 'PAIE_CAMPAGNE'),
(1, 'PAIE_VARIABLES'),
(1, 'PAIE_REVUE'),
(1, 'PAIE_PERIODES'),
(1, 'PAIE_EXPORTS'),
(1, 'ADMIN_MODULE'),
(1, 'ADMIN_SECURITE'),
(1, 'ADMIN_ORGANISATION'),
(1, 'ADMIN_REFERENTIELS'),
(1, 'ADMIN_PARAMETRES');

-- ROLE_AGENT_POINTAGE (id_role = 2) : Accès exploitation pointage
INSERT INTO role_menu (id_role, id_menu) VALUES
(2, 'POINTAGE'),
(2, 'POINTAGE_JOURNALIER'),
(2, 'POINTAGE_SUIVI');

-- Paramètres Globaux Opérationnels RH (Les constantes légales sont portées exclusivement par paie_config_legale)
INSERT INTO parametre_global (id_parametre, code_parametre, categorie, valeur, description, date_debut, date_fin, est_actif, est_desactivable) VALUES
(3, 'SEUIL_NUIT_DEBUT', 'POINTAGE', '22:00', 'Début du travail de nuit (Art. 83 Code Travail Malgache)', '2026-01-01', NULL, TRUE, FALSE),
(4, 'SEUIL_NUIT_FIN', 'POINTAGE', '05:00', 'Fin du travail de nuit', '2026-01-01', NULL, TRUE, FALSE),
(5, 'MONTANT_REPAS_MIDI', 'POINTAGE', '2000', 'Indemnité repas midi de référence en Ariary', '2026-01-01', NULL, TRUE, FALSE),
(6, 'SEUIL_2ND_REPAS_1', 'POINTAGE', '19:30', 'Heure sortie mini pour 2ᵉ repas tranche 1', '2026-01-01', NULL, TRUE, TRUE),
(7, 'MONTANT_2ND_REPAS_1', 'POINTAGE', '2000', '2ᵉ repas tranche 1 en Ariary (19h30-21h00)', '2026-01-01', NULL, TRUE, TRUE),
(8, 'SEUIL_2ND_REPAS_2', 'POINTAGE', '21:00', 'Heure sortie mini pour 2ᵉ repas tranche 2', '2026-01-01', NULL, TRUE, TRUE),
(9, 'MONTANT_2ND_REPAS_2', 'POINTAGE', '4000', '2ᵉ repas tranche 2 en Ariary (> 21h00)', '2026-01-01', NULL, TRUE, TRUE),
(10, 'SEUIL_PAUSE_MIDI_MIN', 'POINTAGE', '60', 'Pause midi minimale en minutes (détection « ne rentre pas midi » TMM)', '2026-01-01', NULL, TRUE, FALSE),
(11, 'REDUCTION_DEPLACT_REPAS_MIDI', 'POINTAGE', '1000', 'Déduction indemnité déplacement (en Ar) si repas midi reçu sur site TMM', '2026-01-01', NULL, TRUE, TRUE),
(12, 'SEUIL_HEURES_JOUR', 'RH_GENERAL', '8', 'Durée normale d une journée de travail (utilisée par Pointage)', '2026-01-01', NULL, TRUE, FALSE),
(14, 'DUREE_MAX_CDD_MOIS', 'CONTRAT', '72', 'Durée maximale cumulée de l ensemble des CDD successifs (72 mois / 6 ans).', '2026-01-01', NULL, TRUE, FALSE),
(15, 'NB_MAX_RENOUVELLEMENTS_CDD', 'CONTRAT', '2', 'Nombre maximal de renouvellements d un CDD par avenant (2 fois max). Au 3ème renouvellement, requalification en CDI.', '2026-01-01', NULL, TRUE, FALSE),
(37, 'DUREE_MAX_CONTRAT_CDD_MOIS', 'CONTRAT', '24', 'Durée maximale d un contrat CDD individuel ou d un avenant (24 mois selon le Code du Travail malgache, Art. 19).', '2026-01-01', NULL, TRUE, FALSE),
(16, 'MAX_RENOUVELLEMENTS_ESSAI', 'RH_GENERAL', '1', 'Nombre maximal de renouvellements autorisés pour une période d essai (droit malgache : 1 seul renouvellement)', '2026-01-01', NULL, TRUE, FALSE),
(18, 'PLAFOND_CUMUL_SOLDE_CONGE_JOURS', 'CONGE', '90', 'Plafond légal du solde cumulé de congés payés (30j acquis/an x 3 ans de prescription, Art. 85)', '2026-01-01', NULL, TRUE, FALSE),
(19, 'DUREE_PRESCRIPTION_SOLDE_CONGE_MOIS', 'CONGE', '36', 'Durée au-delà de laquelle un jour de congé acquis et non pris expire (prescription triennale) — alimente le job batch générant les mouvements EXPIRATION', '2026-01-01', NULL, TRUE, FALSE),
(20, 'PLAFOND_SOLDE_CONGE_NEGATIF', 'CONGE', '-10', 'Solde négatif maximum autorisable par dérogation RH lors d une prise de congé par anticipation (2 à 4 mois d acquisition future)', '2026-01-01', NULL, TRUE, TRUE),
(21, 'SEUIL_ALERTE_SOLDE_CONGE_FAIBLE', 'CONGE', '3', 'Nombre de jours de solde en-dessous duquel l alerte SOLDE_CONGE_FAIBLE se déclenche', '2026-01-01', NULL, TRUE, TRUE),
(22, 'DELAI_PREAVIS_COURT_JOURS', 'CONGE', '2', 'Préavis minimum (en jours avant date_debut) pour une demande courte durée (Art. 85)', '2026-01-01', NULL, TRUE, FALSE),
(23, 'DELAI_PREAVIS_LONG_JOURS', 'CONGE', '15', 'Préavis minimum pour une demande longue durée (seuil défini par SEUIL_JOURS_PREAVIS_LONG)', '2026-01-01', NULL, TRUE, FALSE),
(24, 'SEUIL_JOURS_PREAVIS_LONG', 'CONGE', '3', 'Nombre de jours posés à partir duquel le préavis long (DELAI_PREAVIS_LONG_JOURS) s applique au lieu du préavis court', '2026-01-01', NULL, TRUE, FALSE),
(26, 'DUREE_MAX_MATERNITE_STANDARD_JOURS', 'CONGE', '98', 'Durée légale standard du congé maternité (14 semaines calendaires, Art. 85)', '2026-01-01', NULL, TRUE, FALSE),
(27, 'DUREE_MAX_MATERNITE_PATHOLOGIQUE_JOURS', 'CONGE', '119', 'Durée maximale du congé maternité en cas d extension pour état pathologique (98 + 21j)', '2026-01-01', NULL, TRUE, FALSE),
(28, 'SEUIL_ANCIENNETE_BONIFICATION_ANS', 'CONGE', '3', 'Ancienneté minimale (en années) à partir de laquelle la bonification de congé s applique (Art. 86/87)', '2026-01-01', NULL, TRUE, TRUE),
(29, 'PALIER_ANNEES_BONIFICATION', 'CONGE', '5', 'Nombre d années par tranche donnant droit à un jour de bonification supplémentaire (Art. 86/87)', '2026-01-01', NULL, TRUE, TRUE),
(30, 'JOURS_BONIFICATION_PAR_PALIER', 'CONGE', '1', 'Nombre de jours de congé supplémentaires accordés par tranche d ancienneté atteinte (Art. 86/87)', '2026-01-01', NULL, TRUE, TRUE),
(31, 'SEUIL_RETARD_ASSIDUITE_MIN', 'PAIE', '45', 'Seuil maximal de retard mensuel en minutes pour bénéficier de la prime d assiduité', '2026-01-01', NULL, TRUE, TRUE),
(32, 'SEUIL_RETARD_PENALITE_PP_MIN', 'PAIE', '70', 'Seuil de retard mensuel en minutes déclenchant la pénalité sur la prime de performance si congés > seuil', '2026-01-01', NULL, TRUE, TRUE),
(33, 'SEUIL_CONGES_PENALITE_PP_JOURS', 'PAIE', '1.0', 'Nombre de jours de congés dans le mois au-delà duquel la pénalité sur PP s applique si retard > seuil', '2026-01-01', NULL, TRUE, TRUE),
(34, 'TAUX_PENALITE_PP', 'PAIE', '0.25', 'Taux de réduction appliqué sur la prime de performance (ex: 0.25 = 1/4 soit 25%)', '2026-01-01', NULL, TRUE, TRUE),
(35, 'SEUIL_HEURES_SEMAINE_COMPENSATION', 'POINTAGE', '40.0', 'Seuil hebdomadaire normal d heures travaillées pour compensation des retards et déclenchement des HS', '2026-01-01', NULL, TRUE, TRUE),
(38, 'DUREE_REFERENCE_TCONGE_MOIS', 'CONGE', '12', 'Borne technique de validité du paramètre. Ne représente ni le début de la règle métier, ni le début d une campagne TCONGE, ni une date historique GALAXAM.', '2000-01-01', NULL, TRUE, FALSE)
ON CONFLICT (code_parametre, date_debut) DO UPDATE SET valeur = EXCLUDED.valeur, description = EXCLUDED.description, est_actif = EXCLUDED.est_actif, est_desactivable = EXCLUDED.est_desactivable;
SELECT setval('parametre_global_id_parametre_seq', (SELECT GREATEST(MAX(id_parametre), 38) FROM parametre_global));
