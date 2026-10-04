-- Mise à niveau du libellé d'un menu existant ; aucune table ni permission nouvelle.
-- Vérifier current_database() avant toute application en environnement de test.
UPDATE ref_menu
SET libelle = 'Contrôle des droits'
WHERE id_menu = 'CONGES_PERIODES'
  AND libelle IS DISTINCT FROM 'Contrôle des droits';
