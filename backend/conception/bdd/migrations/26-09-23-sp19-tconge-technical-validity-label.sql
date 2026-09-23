-- Rend explicite la nature technique de la borne 2000-01-01 sans modifier
-- la valeur ni la période de validité du paramètre TCONGE.
UPDATE parametre_global
SET description = 'TECHNICAL_VALIDITY_LOWER_BOUND — borne technique de validité du paramètre. Ne représente ni le début de la règle métier, ni le début d une campagne TCONGE, ni une date historique.'
WHERE code_parametre = 'DUREE_REFERENCE_TCONGE_MOIS'
  AND date_debut = DATE '2000-01-01'
  AND description NOT LIKE '%TECHNICAL_VALIDITY_LOWER_BOUND%';
