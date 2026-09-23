-- Distingue la consommation validée de la réservation en attente.
-- Le rattachement PATERNITE reste inchangé : les sources métier du dépôt se contredisent.
CREATE OR REPLACE VIEW v_pool_conge_annuel_consommation AS
SELECT
    d.id_employe,
    tc.id_pool_conge_annuel AS id_pool,
    EXTRACT(YEAR FROM d.date_debut)::INT AS annee,
    SUM(CASE WHEN sd.code = 'VALIDE' THEN d.nb_jours_decomptes ELSE 0 END) AS jours_consommes,
    SUM(CASE WHEN sd.code = 'EN_ATTENTE' THEN d.nb_jours_decomptes ELSE 0 END) AS jours_en_attente
FROM demande_conge d
JOIN ref_type_conge tc ON tc.id_type_conge = d.id_type_conge
JOIN ref_statut_demande_conge sd ON sd.id_statut_demande_conge = d.id_statut_demande_conge
WHERE tc.id_pool_conge_annuel IS NOT NULL
  AND sd.code IN ('VALIDE', 'EN_ATTENTE')
GROUP BY d.id_employe, tc.id_pool_conge_annuel, EXTRACT(YEAR FROM d.date_debut);

COMMENT ON VIEW v_pool_conge_annuel_consommation IS
    'Quota annuel par année de date_debut : VALIDE consommé, EN_ATTENTE réservé. Le traitement des demandes chevauchant deux années requiert une décision métier.';
