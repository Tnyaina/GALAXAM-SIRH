-- =====================================================================
-- GALAXAM SIRH — TYPE DE MOUVEMENT : RÉGULARISATION CONGÉ AU RETOUR
-- =====================================================================

INSERT INTO ref_type_mouvement_paie (
    code, libelle, sens, categorie, id_rubrique,
    est_proratise, est_plafonne_dixieme, est_actif, ordre_affichage, description
) VALUES (
    'REGULARISATION_CONGE',
    'Régularisation Congé au Retour (Restitution Absence)',
    'GAIN',
    'REGULARISATION',
    (SELECT id_rubrique FROM paie_rubrique WHERE code = 'REGULARISATION_PAIE' LIMIT 1),
    false,
    false,
    true,
    15,
    'Restitution d''une retenue d''absence décomptée au taux horaire sur mois antérieur suite au dépôt/validation du titre de congé au retour du salarié.'
)
ON CONFLICT (code) DO UPDATE SET
    libelle = EXCLUDED.libelle,
    sens = EXCLUDED.sens,
    categorie = EXCLUDED.categorie,
    id_rubrique = EXCLUDED.id_rubrique,
    description = EXCLUDED.description;
