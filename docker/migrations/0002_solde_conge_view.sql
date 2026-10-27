-- Comptabiliser les bonifications et ajustements positifs dans le total acquis.
CREATE OR REPLACE VIEW v_solde_conge_employe AS
SELECT
    id_employe,
    COALESCE(SUM(nb_jours), 0) AS solde_actuel,
    COALESCE(SUM(nb_jours) FILTER (WHERE type_mouvement IN ('ACQUISITION', 'BONIFICATION') OR (type_mouvement = 'AJUSTEMENT' AND nb_jours > 0)), 0) AS total_acquis,
    COALESCE(SUM(nb_jours) FILTER (WHERE type_mouvement = 'BONIFICATION'), 0) AS total_bonification,
    COALESCE(SUM(-nb_jours) FILTER (WHERE type_mouvement = 'PRISE'), 0) AS total_pris,
    COALESCE(SUM(-nb_jours) FILTER (WHERE type_mouvement = 'EXPIRATION'), 0) AS total_expire,
    MAX(date_mouvement) FILTER (WHERE type_mouvement IN ('ACQUISITION', 'AJUSTEMENT')) AS derniere_acquisition
FROM mouvement_solde_conge
GROUP BY id_employe;
