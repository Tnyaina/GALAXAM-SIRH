-- =====================================================================
-- GALAXAM SIRH — MIGRATION SP52 : SÉCURISATION DES TRANSITIONS TEMPORELLES & SOUS-MENU CONGÉS
-- Date : 2026-10-03
--
-- 1. Extension btree_gist pour contraintes d'exclusion temporelle
-- 2. Guard anti-chevauchement temporel sur paie_config_legale
-- 3. Guard anti-chevauchement temporel sur site_indemnite_config
-- 4. Ajout du sous-menu CONGES_PERIODES dans ref_menu et role_menu
-- 5. Guard anti-chevauchement des références de congé par employé
-- =====================================================================

-- 1. Extension btree_gist
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- 2. Guard anti-chevauchement temporel sur paie_config_legale
ALTER TABLE paie_config_legale
    DROP CONSTRAINT IF EXISTS uk_config_legale_temporal_overlap;

ALTER TABLE paie_config_legale
    ADD CONSTRAINT uk_config_legale_temporal_overlap
    EXCLUDE USING gist (
        daterange(date_debut, COALESCE(date_fin, 'infinity'::date), '[]') WITH &&
    );

-- 3. Guard anti-chevauchement temporel sur site_indemnite_config (par site et type d'indemnité actif)
ALTER TABLE site_indemnite_config
    DROP CONSTRAINT IF EXISTS uk_site_indemnite_temporal_overlap;

ALTER TABLE site_indemnite_config
    ADD CONSTRAINT uk_site_indemnite_temporal_overlap
    EXCLUDE USING gist (
        id_site WITH =,
        id_indemnite WITH =,
        daterange(date_debut, COALESCE(date_fin, 'infinity'::date), '[]') WITH &&
    ) WHERE (est_actif IS TRUE);

-- Empêche aussi deux créations manuelles concurrentes d'une référence pour le même salarié.
ALTER TABLE conge_periode_reference
    DROP CONSTRAINT IF EXISTS uk_conge_reference_temporal_overlap;

ALTER TABLE conge_periode_reference
    ADD CONSTRAINT uk_conge_reference_temporal_overlap
    EXCLUDE USING gist (
        id_employe WITH =,
        daterange(date_debut, date_fin, '[]') WITH &&
    );

-- 4. Navigation : contrôle des droits acquis
INSERT INTO ref_menu (id_menu, libelle, icone, href, rang, niveau, id_pere)
VALUES ('CONGES_PERIODES', 'Contrôle des droits', 'CalendarRange', '/conges?tab=periodes', 3, 2, 'CONGES_MODULE')
ON CONFLICT (id_menu) DO UPDATE SET
    libelle = EXCLUDED.libelle,
    icone = EXCLUDED.icone,
    href = EXCLUDED.href,
    rang = EXCLUDED.rang,
    niveau = EXCLUDED.niveau,
    id_pere = EXCLUDED.id_pere;

INSERT INTO role_menu (id_role, id_menu)
VALUES (1, 'CONGES_PERIODES')
ON CONFLICT DO NOTHING;
