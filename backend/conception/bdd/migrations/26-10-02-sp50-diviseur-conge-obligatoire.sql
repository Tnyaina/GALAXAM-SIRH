-- Assurer la présence du paramètre avant de rendre son absence bloquante dans le moteur.
-- Ne modifie pas une valeur existante : une configuration historique reste auditée.
INSERT INTO parametre_global (code_parametre, categorie, valeur, description, date_debut, est_actif, est_desactivable)
SELECT 'DIVISEUR_JOURS_CALENDAIRE_CONGE', 'CONGE', '30',
       'Diviseur de la période de référence utilisé pour l acquisition et la valorisation des droits à congé.',
       DATE '2001-07-01', TRUE, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM parametre_global WHERE code_parametre = 'DIVISEUR_JOURS_CALENDAIRE_CONGE'
);
