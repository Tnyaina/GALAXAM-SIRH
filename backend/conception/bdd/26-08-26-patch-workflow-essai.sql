-- ============================================================
-- PATCH : Correction Workflow RH — Période d'Essai & Planification
-- Date   : 2026-08-26
-- ============================================================

-- 1. Paramètre : nombre maximal de renouvellements de la période d'essai
--    Valeur par défaut : 1 (conforme au droit du travail malgache)
INSERT INTO parametre_global (code_parametre, categorie, valeur, description, date_debut)
VALUES ('MAX_RENOUVELLEMENTS_ESSAI', 'RH_GENERAL', '1',
        'Nombre maximal de renouvellements autorisés pour une période d essai (droit malgache : 1 seul renouvellement)',
        '2026-01-01')
ON CONFLICT (code_parametre) DO NOTHING;

-- 2. Corriger les workflows de tous types dont la date d'effet est dans le futur
--    mais qui ont été mis par erreur en statut ACTIF.
--    Exception : EMBAUCHE_INITIALE reste ACTIF (cas normal).
UPDATE workflow_rh
SET id_statut_rh = (SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'PLANIFIE'),
    updated_at   = CURRENT_TIMESTAMP
WHERE id_statut_rh = (SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'ACTIF')
  AND date_effet > CURRENT_DATE
  AND id_type_evenement_rh NOT IN (
      SELECT id_type_evenement_rh FROM ref_type_evenement_rh
      WHERE code = 'EMBAUCHE_INITIALE'
  );

