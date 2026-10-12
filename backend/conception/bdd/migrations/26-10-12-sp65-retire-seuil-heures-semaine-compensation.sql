-- Le seuil hebdomadaire n'est plus consommé : la durée légale hebdomadaire
-- est pilotée par paie_config_legale.duree_hebdo_legale.
-- Garder les lignes en base pour l'audit, mais les rendre inactives et clore
-- la version courante afin qu'elles ne puissent plus être résolues.
BEGIN;

UPDATE parametre_global
SET est_actif = FALSE,
    date_fin = CASE
        WHEN date_debut <= CURRENT_DATE
         AND (date_fin IS NULL OR date_fin >= CURRENT_DATE)
        THEN CURRENT_DATE - 1
        ELSE date_fin
    END
WHERE code_parametre = 'SEUIL_HEURES_SEMAINE_COMPENSATION';

COMMIT;
