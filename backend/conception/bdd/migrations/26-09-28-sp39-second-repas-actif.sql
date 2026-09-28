-- Réactive les réglages du supplément de second repas sur une base ayant déjà reçu sp38.
-- Ne modifie pas est_actif : les désactivations existantes restent sans supplément ni repli.
UPDATE parametre_global
SET est_desactivable = TRUE,
    description = CASE code_parametre
        WHEN 'SEUIL_2ND_REPAS_1' THEN 'Heure minimale de sortie pour un deuxième repas distinct, première tranche'
        WHEN 'MONTANT_2ND_REPAS_1' THEN 'Montant du deuxième repas en Ariary, première tranche'
        WHEN 'SEUIL_2ND_REPAS_2' THEN 'Heure minimale de sortie pour le deuxième repas majoré'
        WHEN 'MONTANT_2ND_REPAS_2' THEN 'Montant du deuxième repas majoré en Ariary'
    END
WHERE code_parametre IN ('SEUIL_2ND_REPAS_1', 'MONTANT_2ND_REPAS_1',
                         'SEUIL_2ND_REPAS_2', 'MONTANT_2ND_REPAS_2');
