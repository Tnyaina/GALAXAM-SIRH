-- Accès direct au parcours Rubriques → Types → Règles appliquées.
-- La configuration existante reste dans les tables métier ; cette migration ajoute seulement la navigation.
INSERT INTO ref_menu (id_menu, libelle, icone, href, rang, niveau, id_pere)
VALUES ('PAIE_CONFIGURATION', 'Configuration de paie', 'Settings2', '/paie/configuration', 4, 2, 'PAIE_MODULE')
ON CONFLICT (id_menu) DO UPDATE SET
    libelle = EXCLUDED.libelle, icone = EXCLUDED.icone, href = EXCLUDED.href,
    rang = EXCLUDED.rang, niveau = EXCLUDED.niveau, id_pere = EXCLUDED.id_pere;

INSERT INTO role_menu (id_role, id_menu)
VALUES (1, 'PAIE_CONFIGURATION')
ON CONFLICT DO NOTHING;
