-- Décret 2026-1358 : SME applicable dès le 1er octobre 2026.
-- La configuration fiscale 2026 et les taux de majoration restent identiques.
DO $$
DECLARE
    previous_config paie_config_legale%ROWTYPE;
    new_config_id integer;
BEGIN
    SELECT * INTO STRICT previous_config
      FROM paie_config_legale WHERE code_version = 'LF_2026' FOR UPDATE;
    IF previous_config.date_debut <> DATE '2026-03-01'
       OR previous_config.date_fin IS NOT NULL
       OR previous_config.sme_national <> 300000
       OR previous_config.sme_agricole <> 304300
       OR previous_config.plafond_cotisation_nb_sme <> 8 THEN
        RAISE EXCEPTION 'Configuration LF_2026 inattendue : migration SME octobre annulée';
    END IF;

    UPDATE paie_config_legale SET date_fin = DATE '2026-09-30'
      WHERE id_config_legale = previous_config.id_config_legale;
    UPDATE bareme_irsa SET date_fin = DATE '2026-09-30'
      WHERE id_config_legale = previous_config.id_config_legale AND date_fin IS NULL;
    UPDATE ref_taux_majoration SET date_fin = DATE '2026-09-30'
      WHERE id_config_legale = previous_config.id_config_legale AND date_fin IS NULL;

    INSERT INTO paie_config_legale (
        code_version, libelle, date_debut, sme_national, sme_agricole,
        plafond_cotisation_nb_sme, plafond_avance_employeur_pct, quotite_cessible_pct,
        seuil_exoneration_irsa, minimum_perception_irsa, deduction_charge_famille,
        arrondi_base_irsa, duree_hebdo_legale, diviseur_mensuel_heures,
        seuil_hs_tranche1, plafond_hs_hebdo, acquisition_conge_mensuel_jours,
        exoneration_hs_irsa_active, plafond_mensuel_hs_exonerees,
        source_type, source_reference, article_reference, publication_date)
    VALUES (
        'LF_2026_OCT', 'SME octobre 2026 — 315 000 Ar non agricole, 320 000 Ar agricole',
        DATE '2026-10-01', 315000, 320000,
        previous_config.plafond_cotisation_nb_sme,
        previous_config.plafond_avance_employeur_pct,
        previous_config.quotite_cessible_pct,
        previous_config.seuil_exoneration_irsa,
        previous_config.minimum_perception_irsa,
        previous_config.deduction_charge_famille,
        previous_config.arrondi_base_irsa,
        previous_config.duree_hebdo_legale,
        previous_config.diviseur_mensuel_heures,
        previous_config.seuil_hs_tranche1,
        previous_config.plafond_hs_hebdo,
        previous_config.acquisition_conge_mensuel_jours,
        previous_config.exoneration_hs_irsa_active,
        previous_config.plafond_mensuel_hs_exonerees,
        'DECRET', 'Décret n°2026-1358 du 02/06/2026',
        previous_config.article_reference, NULL)
    RETURNING id_config_legale INTO new_config_id;

    INSERT INTO bareme_irsa (
        id_config_legale, numero_tranche, tranche_min, tranche_max, taux, date_debut)
    SELECT new_config_id, numero_tranche, tranche_min, tranche_max, taux, DATE '2026-10-01'
      FROM bareme_irsa WHERE id_config_legale = previous_config.id_config_legale;

    INSERT INTO ref_taux_majoration (
        id_config_legale, type_majoration, taux, description, date_debut)
    SELECT new_config_id, type_majoration, taux, description, DATE '2026-10-01'
      FROM ref_taux_majoration WHERE id_config_legale = previous_config.id_config_legale;
END $$;
