-- ============================================================
-- SPRINT 9 : MIGRATION DES MENUS POINTAGE & PAIE
-- Refonte UX/UI : Suppression de vue-globale, ajout de suivi de période,
-- centralisation de la validation DAF sous le module Paie,
-- et restriction de l'onglet anomalies au rôle ADMIN / DAF.
-- Date : 2026-09-08
-- ============================================================

BEGIN;

-- 1. Nettoyage de l'ancien menu obsolète POINTAGE_GENERAL (vue-globale)
DELETE FROM role_menu WHERE id_menu = 'POINTAGE_GENERAL';
DELETE FROM ref_menu WHERE id_menu = 'POINTAGE_GENERAL';

-- Retirer POINTAGE_ANOMALIES du rôle agent de pointage (id_role = 2)
DELETE FROM role_menu WHERE id_role = 2 AND id_menu = 'POINTAGE_ANOMALIES';

-- 2. Sous-menus de Niveau 2 — Pointage & Présences
INSERT INTO ref_menu (id_menu, libelle, icone, href, rang, niveau, id_pere) VALUES
    ('POINTAGE_JOURNALIER',  'Saisie Quotidienne',              'ClockCheck',    '/pointages?tab=saisie',      1, 2, 'POINTAGE'),
    ('POINTAGE_SUIVI',       'Suivi de Période & Transmission', 'CalendarCheck', '/pointages?tab=suivi',       2, 2, 'POINTAGE'),
    ('POINTAGE_SYNTHESE',    'Synthèse & Clôture',              'Layers',        '/pointages?tab=synthese',    3, 2, 'POINTAGE'),
    ('POINTAGE_ANOMALIES',   'Anomalies & Retards',             'AlertTriangle', '/pointages?tab=anomalies',   4, 2, 'POINTAGE')
ON CONFLICT (id_menu) DO UPDATE SET 
    libelle = EXCLUDED.libelle, 
    href = EXCLUDED.href, 
    rang = EXCLUDED.rang, 
    icone = EXCLUDED.icone, 
    id_pere = EXCLUDED.id_pere;

-- 3. Sous-menus de Niveau 2 — Paie & Rémunération
INSERT INTO ref_menu (id_menu, libelle, icone, href, rang, niveau, id_pere) VALUES
    ('PAIE_VALIDATION',      'Pointage & Validation',           'ClipboardCheck', '/paie?tab=validation',      1, 2, 'PAIE_MODULE'),
    ('PAIE_PERIODES',        'Périodes de Paie & Clôture',      'CalendarRange',  '/paie?tab=periodes',        2, 2, 'PAIE_MODULE'),
    ('PAIE_VARIABLES',       'Variables & Ajustements',         'Sliders',        '/paie?tab=variables',       3, 2, 'PAIE_MODULE'),
    ('PAIE_BULLETINS',       'Bulletins de Paie',               'FileSpreadsheet','/paie?tab=bulletins',       4, 2, 'PAIE_MODULE')
ON CONFLICT (id_menu) DO UPDATE SET 
    libelle = EXCLUDED.libelle, 
    href = EXCLUDED.href, 
    rang = EXCLUDED.rang, 
    icone = EXCLUDED.icone, 
    id_pere = EXCLUDED.id_pere;

-- 4. Attribution des droits aux rôles :
-- id_role 1 = ROLE_ADMIN / DAF (accès complet : Saisie, Suivi, Synthèse, Anomalies, Validation Paie, Périodes, Variables, Bulletins)
INSERT INTO role_menu (id_role, id_menu) VALUES
    (1, 'POINTAGE_JOURNALIER'),
    (1, 'POINTAGE_SUIVI'),
    (1, 'POINTAGE_SYNTHESE'),
    (1, 'POINTAGE_ANOMALIES'),
    (1, 'PAIE_VALIDATION'),
    (1, 'PAIE_PERIODES'),
    (1, 'PAIE_VARIABLES'),
    (1, 'PAIE_BULLETINS')
ON CONFLICT DO NOTHING;

-- id_role 2 = ROLE_AGENT_POINTAGE (accès exploitation : Saisie Quotidienne, Suivi de Période, Synthèse & Clôture)
INSERT INTO role_menu (id_role, id_menu) VALUES
    (2, 'POINTAGE_JOURNALIER'),
    (2, 'POINTAGE_SUIVI'),
    (2, 'POINTAGE_SYNTHESE')
ON CONFLICT DO NOTHING;

COMMIT;
