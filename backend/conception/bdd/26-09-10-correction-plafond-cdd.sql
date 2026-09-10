-- Mise en conformité avec l'article 25 de la loi n°2024-014 :
-- un CDD peut être renouvelé deux fois et la durée cumulée ne peut dépasser six ans.
UPDATE parametre_global
SET valeur = '72',
    description = 'Durée maximale cumulée d un CDD et de ses deux renouvellements (72 mois / 6 ans). Chaque CDD ne peut excéder 24 mois.'
WHERE code_parametre = 'DUREE_MAX_CDD_MOIS';
