-- Répare les bases existantes dont les configurations légales ont été chargées
-- sans les taux présents dans le schéma canonique. Ne remplace aucun taux saisi.
WITH taux(type_majoration, taux, description) AS (
    VALUES
        ('HS_30', 0.30::numeric, '41ᵉ à 48ᵉ heure de la semaine (+30%)'),
        ('HS_50', 0.50::numeric, 'Au-delà de la 48ᵉ heure (+50%)'),
        ('NUIT', 0.30::numeric, 'Heures de nuit (22h - 05h) (+30%)'),
        ('DIMANCHE', 0.40::numeric, 'Travail le dimanche (+40%)'),
        ('FERIE', 1.00::numeric, 'Travail jour férié (+100%)')
)
INSERT INTO ref_taux_majoration
    (id_config_legale, type_majoration, taux, description, date_debut, date_fin)
SELECT config.id_config_legale, taux.type_majoration, taux.taux,
       taux.description, config.date_debut, config.date_fin
FROM paie_config_legale config
CROSS JOIN taux
WHERE ((config.code_version = 'LF_2024_2025'
       AND config.date_debut = DATE '2022-01-01'
       AND config.date_fin = DATE '2025-12-31')
   OR (config.code_version = 'LF_2026_JAN_FEB'
       AND config.date_debut = DATE '2026-01-01'
       AND config.date_fin = DATE '2026-02-28')
   OR (config.code_version = 'LF_2026'
       AND config.date_debut = DATE '2026-03-01'
       AND config.date_fin IS NULL))
  AND NOT EXISTS (
      SELECT 1 FROM ref_taux_majoration existant
      WHERE existant.id_config_legale = config.id_config_legale
        AND existant.type_majoration = taux.type_majoration
        AND existant.date_debut <= config.date_debut
        AND (existant.date_fin IS NULL OR existant.date_fin >= COALESCE(config.date_fin, config.date_debut))
  );
