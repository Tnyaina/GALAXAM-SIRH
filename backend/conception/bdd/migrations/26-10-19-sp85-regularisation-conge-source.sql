-- Lie toute nouvelle régularisation de reliquat à sa correction DAF source.
-- Les anciennes lignes au motif libre restent historiques sans rattachement inventé.
ALTER TABLE paie_avance_acompte
    ADD COLUMN IF NOT EXISTS id_conge_cutover_correction INT
        REFERENCES conge_cutover_correction(id_correction);
CREATE UNIQUE INDEX IF NOT EXISTS uk_paie_regularisation_conge_cutover_active
    ON paie_avance_acompte(id_conge_cutover_correction)
    WHERE id_conge_cutover_correction IS NOT NULL AND statut NOT IN ('REJETE', 'ANNULE');
UPDATE ref_type_mouvement_paie
SET description = 'Complément positif issu d''une correction de reliquat ; motif CORRECTION_CUTOVER#id obligatoire, montant issu de la source'
WHERE code = 'REGULARISATION_CONGE';
