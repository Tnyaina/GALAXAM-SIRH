-- Seuil de service effectif avant la prise du congé annuel.
-- Le paramètre global devient l'unique source de ce seuil pour ANNUEL.
INSERT INTO parametre_global
    (code_parametre, categorie, valeur, description, date_debut, date_fin, est_actif, est_desactivable)
VALUES
    ('ANCIENNETE_MIN_CONGE_ANNUEL_MOIS', 'CONGE', '12',
     'Ancienneté minimale en mois de service effectif pour prendre un congé annuel',
     '2001-07-01', NULL, TRUE, FALSE)
ON CONFLICT (code_parametre, date_debut) DO NOTHING;

UPDATE ref_type_conge
SET exige_anciennete_min_mois = 0
WHERE code = 'ANNUEL';
