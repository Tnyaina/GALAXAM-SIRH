-- SP60 : motifs distincts nécessaires au calcul des droits de sortie.
-- Compatible avec le motif historique LICENCIEMENT, conservé pour les dossiers existants.
INSERT INTO ref_motif_sortie (code, libelle)
SELECT v.code, v.libelle
FROM (VALUES
    ('LICENCIEMENT_ECONOMIQUE', 'Licenciement pour motif économique'),
    ('CESSATION_ACTIVITE', 'Licenciement pour cessation d activité'),
    ('RUPTURE_ESSAI', 'Rupture de période d essai'),
    ('DEPART_RETRAITE', 'Départ en retraite (événement carrière)')
) AS v(code, libelle)
WHERE NOT EXISTS (SELECT 1 FROM ref_motif_sortie r WHERE r.code = v.code);
