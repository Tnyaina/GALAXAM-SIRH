-- SP80 : une journée appartient aux bornes inclusives de sa période.
-- Pré-requis : arrêter les écritures Pointage pendant la migration et examiner
-- le diagnostic ci-dessous sur chaque base cible. Aucune donnée n'est corrigée.
BEGIN;

LOCK TABLE periode_paie, pointage_jour IN SHARE ROW EXCLUSIVE MODE;

DO $$
DECLARE
    v_count BIGINT;
    v_ids TEXT;
BEGIN
    SELECT COUNT(*) INTO v_count
    FROM pointage_jour j
    JOIN periode_paie p ON p.id_periode_paie = j.id_periode_paie
    WHERE j.date_pointage NOT BETWEEN p.date_debut AND p.date_fin;

    IF v_count > 0 THEN
        SELECT string_agg(id_pointage::TEXT, ', ' ORDER BY id_pointage)
        INTO v_ids
        FROM (
            SELECT j.id_pointage
            FROM pointage_jour j
            JOIN periode_paie p ON p.id_periode_paie = j.id_periode_paie
            WHERE j.date_pointage NOT BETWEEN p.date_debut AND p.date_fin
            ORDER BY j.id_pointage
            LIMIT 20
        ) sample;
        RAISE EXCEPTION 'SP80 bloquée : % journée(s) hors période (id_pointage : %). Corriger explicitement avant de relancer.',
            v_count, v_ids;
    END IF;
END $$;

CREATE OR REPLACE FUNCTION verifier_pointage_jour_periode()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    v_debut DATE;
    v_fin DATE;
BEGIN
    -- FOR SHARE empêche un changement concurrent des colonnes non clés du parent.
    SELECT date_debut, date_fin INTO v_debut, v_fin
    FROM periode_paie
    WHERE id_periode_paie = NEW.id_periode_paie
    FOR SHARE;

    IF NOT FOUND OR NEW.date_pointage NOT BETWEEN v_debut AND v_fin THEN
        RAISE EXCEPTION USING ERRCODE = '23514',
            CONSTRAINT = 'ck_pointage_jour_periode_dates',
            MESSAGE = format('La date %s du pointage est hors de la période %s.',
                NEW.date_pointage, NEW.id_periode_paie);
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_pointage_jour_periode_dates
BEFORE INSERT OR UPDATE OF date_pointage, id_periode_paie ON pointage_jour
FOR EACH ROW EXECUTE FUNCTION verifier_pointage_jour_periode();

CREATE OR REPLACE FUNCTION interdire_modification_bornes_periode_paie()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(NEW.date_debut, NEW.date_fin) IS DISTINCT FROM ROW(OLD.date_debut, OLD.date_fin) THEN
        RAISE EXCEPTION USING ERRCODE = '23514',
            CONSTRAINT = 'ck_periode_paie_bornes_immuables',
            MESSAGE = format('Les bornes de la période %s sont immuables après création.', OLD.id_periode_paie);
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_periode_paie_bornes_immuables
BEFORE UPDATE OF date_debut, date_fin ON periode_paie
FOR EACH ROW EXECUTE FUNCTION interdire_modification_bornes_periode_paie();

COMMIT;
