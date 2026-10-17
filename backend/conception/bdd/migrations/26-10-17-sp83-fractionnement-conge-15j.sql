-- ============================================================================
-- SP83 : Mise en conformité du fractionnement de congé annuel (Loi 2024-014 Art. 130)
-- ----------------------------------------------------------------------------
-- En droit positif malgache (Loi n° 2024-014 du 14 août 2024, Art. 130), la première
-- fraction de congé annuel continu doit comporter au moins QUINZE (15) jours consécutifs
-- (et non 12 jours comme sous l'ancienne pratique historique de la Loi 2003-044 Art. 89).
-- ============================================================================

INSERT INTO parametre_global (code_parametre, categorie, valeur, description, date_debut, est_actif, est_desactivable)
VALUES ('DUREE_TRANCHE_MIN_FRACTIONNEMENT_JOURS', 'CONGE', '15',
        'Durée minimale de la première tranche continue de congé annuel (Art. 130 Loi n° 2024-014 portant Code du Travail).',
        '2001-07-01', TRUE, FALSE)
ON CONFLICT (code_parametre, date_debut) DO UPDATE
SET valeur = '15',
    description = 'Durée minimale de la première tranche continue de congé annuel (Art. 130 Loi n° 2024-014 portant Code du Travail).';

-- Mise à niveau également de l'alias historique éventuel
UPDATE parametre_global
SET valeur = '15',
    description = 'Durée minimale de la première tranche continue de congé annuel (Art. 130 Loi n° 2024-014 portant Code du Travail).'
WHERE code_parametre = 'FRACTIONNEMENT_TRANCHE_MIN_JOURS';
