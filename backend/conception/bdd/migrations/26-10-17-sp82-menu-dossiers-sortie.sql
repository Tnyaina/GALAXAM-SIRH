-- SP82 : accès explicite aux dossiers de sortie dans le menu Paie.
-- À appliquer sur les bases existantes après SP81. Les droits API restent
-- PAIE_READ pour la consultation et PAIE_WRITE pour les mutations.

INSERT INTO ref_menu (id_menu, libelle, icone, href, rang, niveau, id_pere)
VALUES ('PAIE_DOSSIERS_SORTIE', 'Dossiers de sortie & STC', 'UserMinus',
        '/paie/dossiers-sortie', 6, 2, 'PAIE_MODULE')
ON CONFLICT (id_menu) DO UPDATE SET
    libelle = EXCLUDED.libelle,
    icone = EXCLUDED.icone,
    href = EXCLUDED.href,
    rang = EXCLUDED.rang,
    niveau = EXCLUDED.niveau,
    id_pere = EXCLUDED.id_pere;

UPDATE ref_menu SET rang = 7 WHERE id_menu = 'PAIE_DECLARATIONS';

-- Le parent et cette seule entrée sont attribués aux rôles qui possèdent
-- déjà le droit de lecture Paie. Les autres entrées Paie restent distinctes.
INSERT INTO role_menu (id_role, id_menu)
SELECT DISTINCT rp.id_role, entree.id_menu
FROM role_permission rp
JOIN ref_module module_paie ON module_paie.id_module = rp.id_module
CROSS JOIN (VALUES ('PAIE_MODULE'), ('PAIE_DOSSIERS_SORTIE')) AS entree(id_menu)
WHERE module_paie.code_module = 'PAIE' AND rp.droit_lecture = TRUE
ON CONFLICT DO NOTHING;

