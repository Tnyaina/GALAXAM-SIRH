-- Le seuil hebdomadaire historique n'est pas consommé par les moteurs.
-- Le seuil opérationnel est calculé selon les jours requis et la durée journalière configurée.
UPDATE parametre_global
SET est_desactivable = FALSE,
    est_actif = TRUE,
    description = 'Ancien paramètre non consommé ; objectif hebdomadaire issu de la durée journalière et des jours requis'
WHERE code_parametre = 'SEUIL_HEURES_SEMAINE_COMPENSATION';
