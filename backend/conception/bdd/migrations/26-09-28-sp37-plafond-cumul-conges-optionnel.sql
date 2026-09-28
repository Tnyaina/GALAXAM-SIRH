-- Rend optionnel le plafond de cumul, sans fallback lorsque la règle est désactivée.
UPDATE parametre_global
SET est_desactivable = TRUE,
    description = 'Plafond optionnel du solde cumulé ; désactivé, aucun plafonnement des acquisitions et bonifications'
WHERE code_parametre = 'PLAFOND_CUMUL_SOLDE_CONGE_JOURS';
