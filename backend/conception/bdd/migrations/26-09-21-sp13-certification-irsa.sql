-- Normalise les minima en bornes inférieures exclusives du barème progressif.
-- Les valeurs restent datées et administrées en BDD; aucune borne n'est portée par Java.
UPDATE paie_config_legale
SET date_debut = DATE '2026-03-01'
WHERE code_version = 'LF_2026' AND date_debut = DATE '2026-01-01';

INSERT INTO paie_config_legale (
    code_version, libelle, date_debut, date_fin, sme_national, sme_agricole,
    plafond_cotisation_nb_sme, seuil_exoneration_irsa, minimum_perception_irsa,
    deduction_charge_famille, arrondi_base_irsa, duree_hebdo_legale,
    diviseur_mensuel_heures, seuil_hs_tranche1, plafond_hs_hebdo,
    acquisition_conge_mensuel_jours, diviseur_valorisation_conge_jours,
    exoneration_hs_irsa_active, plafond_mensuel_hs_exonerees
)
SELECT 'LF_2026_JAN_FEB',
       'Loi de Finances 2026 (Janv-Févr 2026) — IRSA 6 Tranches, SME CNaPS 262 680 Ar',
       DATE '2026-01-01', DATE '2026-02-28', 262680.00, 266500.00,
       8.00, 350000.00, 3000.00, 2000.00, 'CENTAINE_INF', 40.00,
       173.33, 8.00, 20.00, 2.50, 30, TRUE, 20.00
WHERE NOT EXISTS (SELECT 1 FROM paie_config_legale WHERE code_version = 'LF_2026_JAN_FEB');

INSERT INTO bareme_irsa (id_config_legale, numero_tranche, tranche_min, tranche_max, taux, date_debut, date_fin)
SELECT january.id_config_legale, bracket.numero_tranche, bracket.tranche_min, bracket.tranche_max,
       bracket.taux, DATE '2026-01-01', DATE '2026-02-28'
FROM paie_config_legale january
JOIN paie_config_legale march_config ON march_config.code_version = 'LF_2026'
JOIN bareme_irsa bracket ON bracket.id_config_legale = march_config.id_config_legale
WHERE january.code_version = 'LF_2026_JAN_FEB'
  AND NOT EXISTS (SELECT 1 FROM bareme_irsa existing WHERE existing.id_config_legale = january.id_config_legale);

INSERT INTO ref_taux_majoration (
    id_config_legale, type_majoration, taux, description, date_debut, date_fin
)
SELECT DISTINCT ON (rate.type_majoration)
       january.id_config_legale, rate.type_majoration, rate.taux, rate.description,
       DATE '2026-01-01', DATE '2026-02-28'
FROM paie_config_legale january
JOIN paie_config_legale march_config ON march_config.code_version = 'LF_2026'
JOIN ref_taux_majoration rate ON rate.id_config_legale = march_config.id_config_legale
WHERE january.code_version = 'LF_2026_JAN_FEB'
  AND NOT EXISTS (
      SELECT 1
      FROM ref_taux_majoration existing
      WHERE existing.id_config_legale = january.id_config_legale
        AND existing.type_majoration = rate.type_majoration
  )
ORDER BY rate.type_majoration, rate.date_debut DESC, rate.id_taux_majoration DESC;

UPDATE bareme_irsa current_bracket
SET tranche_min = previous_bracket.tranche_max
FROM bareme_irsa previous_bracket
WHERE current_bracket.id_config_legale = previous_bracket.id_config_legale
  AND current_bracket.numero_tranche = previous_bracket.numero_tranche + 1
  AND previous_bracket.tranche_max IS NOT NULL
  AND current_bracket.tranche_min = previous_bracket.tranche_max + 1;

ALTER TABLE paie_config_legale
    ADD COLUMN IF NOT EXISTS source_type VARCHAR(50),
    ADD COLUMN IF NOT EXISTS source_reference VARCHAR(255),
    ADD COLUMN IF NOT EXISTS article_reference VARCHAR(80),
    ADD COLUMN IF NOT EXISTS publication_date DATE;

UPDATE paie_config_legale
SET source_type = COALESCE(source_type, 'CGI_MADAGASCAR'),
    source_reference = COALESCE(source_reference, CASE WHEN code_version LIKE 'LF_2026%' THEN 'Loi de finances 2026' ELSE 'Code des impôts 2025' END),
    article_reference = COALESCE(article_reference, '01.03.16')
WHERE source_type IS NULL OR source_reference IS NULL OR article_reference IS NULL;
