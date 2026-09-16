-- ==============================================================================
-- SPRINT 2B : RESTAURATION ET ENRICHISSEMENT DES SOUS-MENUS PAIE & RÉMUNÉRATION
-- ==============================================================================
-- Sous-menus indispensables pour un pilotage clair et complet de la paie :
-- 1. PAIE_CAMPAGNE  : Campagne Active (stepper, prérequis, calcul)
-- 2. PAIE_VARIABLES : Variables & Primes (primes, retenues, acomptes, indemnités)
-- 3. PAIE_REVUE     : Revue des Bulletins (cockpit DAF, KPIs, écarts, bulletins)
-- 4. PAIE_PERIODES  : Historique des Périodes (calendrier, ouverture, clôture)
-- 5. PAIE_EXPORTS   : Déclarations & Sorties (SAFI, CNaPS DNTS, virement, EDI-ISI)
-- ==============================================================================

BEGIN;

-- 1. PAIE_MODULE devient une catégorie parente avec sous-menus
UPDATE ref_menu 
SET href = NULL, niveau = 1, id_pere = NULL, icone = 'Banknote', rang = 5
WHERE id_menu = 'PAIE_MODULE';

-- 2. Nettoyage préventif des identifiants de sous-menus
DELETE FROM role_menu WHERE id_menu IN ('PAIE_CAMPAGNE', 'PAIE_VARIABLES', 'PAIE_REVUE', 'PAIE_PERIODES', 'PAIE_EXPORTS', 'PAIE_BULLETINS');
DELETE FROM ref_menu WHERE id_menu IN ('PAIE_CAMPAGNE', 'PAIE_VARIABLES', 'PAIE_REVUE', 'PAIE_PERIODES', 'PAIE_EXPORTS', 'PAIE_BULLETINS');

-- 3. Insertion des sous-menus Niveau 2 sous PAIE_MODULE
INSERT INTO ref_menu (id_menu, libelle, icone, href, rang, niveau, id_pere) VALUES
    ('PAIE_CAMPAGNE',  'Campagne Active',         'LayoutDashboard',   '/paie?tab=campagne', 1, 2, 'PAIE_MODULE'),
    ('PAIE_VARIABLES', 'Variables & Primes',       'SlidersHorizontal', '/paie?tab=variables',2, 2, 'PAIE_MODULE'),
    ('PAIE_REVUE',     'Revue des Bulletins',      'Banknote',          '/paie?tab=cockpit',  3, 2, 'PAIE_MODULE'),
    ('PAIE_PERIODES',  'Historique des Périodes',  'CalendarRange',     '/paie?tab=periodes', 4, 2, 'PAIE_MODULE'),
    ('PAIE_EXPORTS',   'Déclarations & Sorties',   'FileText',          '/paie?tab=exports',  5, 2, 'PAIE_MODULE')
ON CONFLICT (id_menu) DO UPDATE SET
    libelle = EXCLUDED.libelle,
    icone = EXCLUDED.icone,
    href = EXCLUDED.href,
    rang = EXCLUDED.rang,
    niveau = EXCLUDED.niveau,
    id_pere = EXCLUDED.id_pere;

-- 4. Attribution des habilitations UI à ROLE_ADMIN (id_role = 1)
INSERT INTO role_menu (id_role, id_menu) VALUES
    (1, 'PAIE_MODULE'),
    (1, 'PAIE_CAMPAGNE'),
    (1, 'PAIE_VARIABLES'),
    (1, 'PAIE_REVUE'),
    (1, 'PAIE_PERIODES'),
    (1, 'PAIE_EXPORTS')
ON CONFLICT DO NOTHING;

COMMIT;
