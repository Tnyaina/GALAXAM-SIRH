-- ============================================================
-- SPRINT 10 : PHASE 1 — MIGRATION DU MENU VALIDATION DAF
-- Déplacement de la validation DAF sous le module Pointage & Présences
-- Date : 2026-09-08
-- ============================================================

BEGIN;

-- 1. Nettoyage de l'ancien sous-menu dans le module Paie
DELETE FROM role_menu WHERE id_menu = 'PAIE_VALIDATION';
DELETE FROM ref_menu WHERE id_menu = 'PAIE_VALIDATION';

-- 2. Insertion / mise à jour du sous-menu sous le module Pointage
INSERT INTO ref_menu (id_menu, libelle, icone, href, rang, niveau, id_pere) VALUES
    ('POINTAGE_JOURNALIER',  'Saisie Quotidienne',              'ClockCheck',    '/pointages?tab=saisie',      1, 2, 'POINTAGE'),
    ('POINTAGE_SUIVI',       'Suivi de Période & Transmission', 'CalendarCheck', '/pointages?tab=suivi',       2, 2, 'POINTAGE'),
    ('POINTAGE_ANOMALIES',   'Anomalies & Retards',             'AlertTriangle', '/pointages?tab=anomalies',   3, 2, 'POINTAGE'),
    ('POINTAGE_SYNTHESE',    'Synthèse & Clôture',              'Layers',        '/pointages?tab=synthese',    4, 2, 'POINTAGE'),
    ('POINTAGE_VALIDATION',  'Arbitrage DAF',                   'ShieldCheck',   '/pointages?tab=validation',  5, 2, 'POINTAGE')
ON CONFLICT (id_menu) DO UPDATE SET 
    libelle = EXCLUDED.libelle, 
    href = EXCLUDED.href, 
    rang = EXCLUDED.rang, 
    icone = EXCLUDED.icone, 
    id_pere = EXCLUDED.id_pere;

-- 3. Réorganisation des sous-menus de Paie
INSERT INTO ref_menu (id_menu, libelle, icone, href, rang, niveau, id_pere) VALUES
    ('PAIE_PERIODES',        'Périodes de Paie & Clôture',      'CalendarRange',  '/paie?tab=periodes',        1, 2, 'PAIE_MODULE'),
    ('PAIE_VARIABLES',       'Variables & Ajustements',         'Sliders',        '/paie?tab=variables',       2, 2, 'PAIE_MODULE'),
    ('PAIE_BULLETINS',       'Bulletins de Paie',               'FileSpreadsheet','/paie?tab=bulletins',       3, 2, 'PAIE_MODULE')
ON CONFLICT (id_menu) DO UPDATE SET 
    libelle = EXCLUDED.libelle, 
    href = EXCLUDED.href, 
    rang = EXCLUDED.rang, 
    icone = EXCLUDED.icone, 
    id_pere = EXCLUDED.id_pere;

-- 4. Attribution des droits aux rôles :
-- id_role 1 = ROLE_ADMIN / DAF (accès complet avec Arbitrage DAF)
INSERT INTO role_menu (id_role, id_menu) VALUES
    (1, 'POINTAGE_JOURNALIER'),
    (1, 'POINTAGE_SUIVI'),
    (1, 'POINTAGE_ANOMALIES'),
    (1, 'POINTAGE_SYNTHESE'),
    (1, 'POINTAGE_VALIDATION'),
    (1, 'PAIE_PERIODES'),
    (1, 'PAIE_VARIABLES'),
    (1, 'PAIE_BULLETINS')
ON CONFLICT DO NOTHING;

-- id_role 2 = ROLE_AGENT_POINTAGE (accès exploitation sans anomalies ni validation DAF)
INSERT INTO role_menu (id_role, id_menu) VALUES
    (2, 'POINTAGE_JOURNALIER'),
    (2, 'POINTAGE_SUIVI'),
    (2, 'POINTAGE_SYNTHESE')
ON CONFLICT DO NOTHING;

COMMIT;
