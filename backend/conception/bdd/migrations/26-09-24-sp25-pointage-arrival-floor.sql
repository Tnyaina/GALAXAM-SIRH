-- La saisie brute reste conservée; l'heure effective utilisée pour les calculs
-- est figée par journée afin que le gel ne dépende pas d'un réglage de site futur.
ALTER TABLE ref_tolerance_arrivee_pointage
    ADD COLUMN IF NOT EXISTS est_actif BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE pointage_jour
    ADD COLUMN IF NOT EXISTS heure_entree_matin_effective TIME;

COMMENT ON COLUMN pointage_jour.heure_entree_matin_effective IS
    'Heure matinale retenue pour les calculs au moment de la saisie; NULL sur les anciens pointages (heure brute applicable).';
