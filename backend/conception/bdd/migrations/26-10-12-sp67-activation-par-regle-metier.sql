-- Les membres de ces règles restent versionnés séparément, mais leur activation
-- est désormais pilotée ensemble par une action métier atomique.
BEGIN;

UPDATE parametre_global
SET est_desactivable = TRUE
WHERE code_parametre IN (
    'MONTANT_PRIME_ASSIDUITE', 'SEUIL_RETARD_ASSIDUITE_MIN',
    'TAUX_PENALITE_PP', 'SEUIL_RETARD_PENALITE_PP_MIN', 'SEUIL_CONGES_PENALITE_PP_JOURS',
    'SEUIL_ANCIENNETE_BONIFICATION_ANS', 'PALIER_ANNEES_BONIFICATION', 'JOURS_BONIFICATION_PAR_PALIER',
    'SEUIL_2ND_REPAS_1', 'MONTANT_2ND_REPAS_1',
    'SEUIL_2ND_REPAS_2', 'MONTANT_2ND_REPAS_2'
);

COMMIT;
