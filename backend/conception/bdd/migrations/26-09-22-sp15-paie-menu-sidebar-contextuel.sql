-- ============================================================
-- SPRINT UX PAIE 1 : MIGRATION DES SOUS-MENUS DU MODULE PAIE
-- Refonte contextuelle : Suppression des étapes internes de stepper dans la sidebar
-- (PAIE_REVUE retiré car étape Contrôle du Stepper),
-- Libellés et routes canoniques alignés :
-- - PAIE_CAMPAGNE  : Centre de Paie & Campagne (/paie)
-- - PAIE_VARIABLES : Acomptes & Avances (/paie?tab=variables)
-- - PAIE_PERIODES  : Historique des Périodes (/paie/periodes)
-- - PAIE_EXPORTS   : Déclarations & Sorties (/paie/declarations)
-- - PAIE_PARAMETRES: Paramétrage de Paie (/admin/parametres?module=paie)
-- Date : 2026-09-22
-- ============================================================

BEGIN;

-- 1. Nettoyage des anciens menus obsolètes ou renommés
DELETE FROM role_menu WHERE id_menu IN ('PAIE_REVUE', 'PAIE_VARIABLES', 'PAIE_EXPORTS', 'PAIE_PERIODES', 'PAIE_BULLETINS');
DELETE FROM ref_menu WHERE id_menu IN ('PAIE_REVUE', 'PAIE_VARIABLES', 'PAIE_EXPORTS', 'PAIE_PERIODES', 'PAIE_BULLETINS');

-- 2. Mise à jour / insertion des sous-menus canoniques V1 Full V3 de PAIE_MODULE
INSERT INTO ref_menu (id_menu, libelle, icone, href, rang, niveau, id_pere) VALUES
    ('PAIE_CENTRE',       'Centre de paie',          'LayoutDashboard',   '/paie',                           1, 2, 'PAIE_MODULE'),
    ('PAIE_CAMPAGNE',     'Campagne active',         'CalendarRange',     '/paie/periodes',                  2, 2, 'PAIE_MODULE'),
    ('PAIE_ACOMPTES',     'Acomptes & avances',      'SlidersHorizontal', '/paie/acomptes-avances',           3, 2, 'PAIE_MODULE'),
    ('PAIE_HISTORIQUE',   'Historique',              'History',           '/paie/historique',                4, 2, 'PAIE_MODULE'),
    ('PAIE_DECLARATIONS', 'Déclarations & sorties',  'FileText',          '/paie/declarations',              5, 2, 'PAIE_MODULE'),
    ('PAIE_PARAMETRES',   'Administration',          'Settings',          '/admin/parametres?module=paie',   6, 2, 'PAIE_MODULE')
ON CONFLICT (id_menu) DO UPDATE SET
    libelle = EXCLUDED.libelle,
    href = EXCLUDED.href,
    rang = EXCLUDED.rang,
    icone = EXCLUDED.icone,
    id_pere = EXCLUDED.id_pere;

-- 3. Habilitation pour ROLE_ADMIN (id_role = 1)
INSERT INTO role_menu (id_role, id_menu) VALUES
    (1, 'PAIE_CENTRE'),
    (1, 'PAIE_CAMPAGNE'),
    (1, 'PAIE_ACOMPTES'),
    (1, 'PAIE_HISTORIQUE'),
    (1, 'PAIE_DECLARATIONS'),
    (1, 'PAIE_PARAMETRES')
ON CONFLICT DO NOTHING;

COMMIT;
