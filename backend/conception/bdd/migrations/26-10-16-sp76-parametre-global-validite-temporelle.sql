-- Garantit une seule version applicable par code, en respectant les bornes inclusives.
-- La migration ne corrige aucune donnée : elle échoue avec les identifiants concernés.

DO $$
DECLARE
    diagnostic TEXT;
BEGIN
    SELECT string_agg(format('id_parametre=%s code=%L debut=%s fin=%s',
                             id_parametre, code_parametre, date_debut, date_fin), E'\n')
      INTO diagnostic
      FROM (
          SELECT id_parametre, code_parametre, date_debut, date_fin
          FROM parametre_global
          WHERE date_fin IS NOT NULL AND date_fin < date_debut
          ORDER BY code_parametre, date_debut, id_parametre
          LIMIT 20
      ) invalid_dates;

    IF diagnostic IS NOT NULL THEN
        RAISE EXCEPTION 'parametre_global: dates de fin antérieures au début. Corriger explicitement avant sp76:%', E'\n' || diagnostic
            USING ERRCODE = '23514';
    END IF;

    SELECT string_agg(format('code=%L id1=%s [%s,%s] id2=%s [%s,%s]',
                             code_parametre, id1, debut1, fin1, id2, debut2, fin2), E'\n')
      INTO diagnostic
      FROM (
          SELECT a.code_parametre, a.id_parametre AS id1, a.date_debut AS debut1, a.date_fin AS fin1,
                 b.id_parametre AS id2, b.date_debut AS debut2, b.date_fin AS fin2
          FROM parametre_global a
          JOIN parametre_global b
            ON UPPER(a.code_parametre) = UPPER(b.code_parametre)
           AND a.id_parametre < b.id_parametre
           AND daterange(a.date_debut, a.date_fin, '[]')
               && daterange(b.date_debut, b.date_fin, '[]')
          ORDER BY UPPER(a.code_parametre), a.date_debut, b.date_debut, a.id_parametre, b.id_parametre
          LIMIT 20
      ) overlapping_periods;

    IF diagnostic IS NOT NULL THEN
        RAISE EXCEPTION 'parametre_global: périodes qui se chevauchent (bornes inclusives). Corriger explicitement avant sp76:%', E'\n' || diagnostic
            USING ERRCODE = '23P01';
    END IF;
END;
$$;

CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE parametre_global
    ADD CONSTRAINT ck_parametre_global_dates
    CHECK (date_fin IS NULL OR date_fin >= date_debut);

ALTER TABLE parametre_global
    ADD CONSTRAINT ex_parametre_global_validite
    EXCLUDE USING gist (
        UPPER(code_parametre) WITH =,
        daterange(date_debut, date_fin, '[]') WITH &&
    );
