-- Accès explicite au parcours indépendant depuis le menu Paie.
INSERT INTO ref_menu (id_menu, libelle, icone, href, rang, niveau, id_pere)
VALUES ('PAIE_PRESTATAIRES', 'Prestataires indépendants', 'Users', '/paie/prestataires', 4, 2, 'PAIE_MODULE')
ON CONFLICT (id_menu) DO UPDATE SET libelle=EXCLUDED.libelle, icone=EXCLUDED.icone,
    href=EXCLUDED.href, rang=EXCLUDED.rang, niveau=EXCLUDED.niveau, id_pere=EXCLUDED.id_pere;

UPDATE ref_menu SET rang=5 WHERE id_menu='PAIE_CONFIGURATION';
UPDATE ref_menu SET rang=6 WHERE id_menu='PAIE_DECLARATIONS';

-- Le même accès est proposé aux rôles autorisés à lire le module Paie.
INSERT INTO role_menu (id_role, id_menu)
SELECT DISTINCT rp.id_role, 'PAIE_PRESTATAIRES'
FROM role_permission rp JOIN ref_module m ON m.id_module=rp.id_module
WHERE m.code_module='PAIE' AND rp.droit_lecture=TRUE
ON CONFLICT DO NOTHING;

INSERT INTO role_menu (id_role, id_menu)
SELECT id_role, 'PAIE_PRESTATAIRES' FROM ref_role WHERE code_role='ROLE_ADMIN'
ON CONFLICT DO NOTHING;
