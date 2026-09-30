-- Élargit les montants en ariary sans changer leur échelle (centimes conservés).
-- NUMERIC(12,8) ne permettait que 4 chiffres entiers pour le taux horaire.
-- À exécuter sur les bases existantes ; le schéma canonique 06 inclut ces types.
BEGIN;

DO $$
DECLARE
    target record;
BEGIN
    FOR target IN
        SELECT table_name,
               string_agg(format('ALTER COLUMN %I TYPE NUMERIC(20,2)', column_name), ', ' ORDER BY ordinal_position) AS alterations
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND data_type = 'numeric'
          AND numeric_precision = 12
          AND numeric_scale = 2
          AND table_name IN (
              'bareme_irsa', 'bulletin_paie', 'bulletin_paie_ligne',
              'paie_avance_acompte', 'paie_config_legale',
              'paie_honoraire_prestation', 'paie_mouvement_imputation',
              'paie_pret', 'paie_pret_echeance', 'paie_stc',
              'conge_journal_droit'
          )
        GROUP BY table_name
    LOOP
        EXECUTE format('ALTER TABLE %I %s', target.table_name, target.alterations);
    END LOOP;
END $$;

ALTER TABLE bulletin_paie
    ALTER COLUMN taux_horaire TYPE NUMERIC(20,8);

ALTER TABLE bulletin_paie_ligne
    ALTER COLUMN base_calcul TYPE NUMERIC(20,8);

ALTER TABLE paie_stc
    ALTER COLUMN taux_horaire TYPE NUMERIC(20,4);

COMMIT;
