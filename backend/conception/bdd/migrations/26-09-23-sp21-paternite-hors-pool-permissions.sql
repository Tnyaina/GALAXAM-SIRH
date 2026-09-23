-- Le Code du travail 2024 distingue le congé de paternité (art. 134)
-- des permissions exceptionnelles de l'article 126, plafonnées à 10 jours/an.
-- Retire PATERNITE du pool commun des permissions familiales.
UPDATE ref_type_conge
SET id_pool_conge_annuel = NULL
WHERE code = 'PATERNITE'
  AND id_pool_conge_annuel = (
      SELECT id_pool
      FROM ref_pool_conge_annuel
      WHERE code = 'PERMISSIONS_FAMILIALES'
  );

COMMENT ON COLUMN ref_type_conge.id_pool_conge_annuel IS
    'Pool annuel partagé auquel ce type contribue. Le congé légal de paternité (Code du travail 2024, art. 134) est distinct des permissions familiales plafonnées (art. 126).';
