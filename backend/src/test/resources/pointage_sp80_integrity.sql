-- Exécuter uniquement sur une base PostgreSQL isolée initialisée avec les
-- six schémas canoniques. Tout ce script est annulé à la fin.
BEGIN;

INSERT INTO periode_paie (code, libelle, date_debut, date_fin, id_statut_periode)
VALUES ('SP80-TEST', 'SP80 test', DATE '2035-03-01', DATE '2035-03-31',
        (SELECT id_statut_periode FROM ref_statut_periode_paie WHERE code = 'BROUILLON'));

DO $$
DECLARE
    v_period INTEGER;
    v_other INTEGER;
    v_day INTEGER;
    v_state INTEGER;
BEGIN
    SELECT id_periode_paie INTO v_period FROM periode_paie WHERE code = 'SP80-TEST';
    SELECT id_periode_paie INTO v_other FROM periode_paie WHERE code = '2026-08';
    SELECT id_statut_jour INTO v_state FROM ref_statut_pointage_jour WHERE code = 'PRESENT';

    -- Les deux bornes inclusives sont acceptées, y compris par insertion SQL directe.
    INSERT INTO pointage_jour (id_employe, id_site, id_periode_paie, date_pointage, id_statut_jour)
    VALUES (1, 1, v_period, DATE '2035-03-01', v_state) RETURNING id_pointage INTO v_day;
    INSERT INTO pointage_jour (id_employe, id_site, id_periode_paie, date_pointage, id_statut_jour)
    VALUES (1, 1, v_period, DATE '2035-03-31', v_state);

    BEGIN
        INSERT INTO pointage_jour (id_employe, id_site, id_periode_paie, date_pointage, id_statut_jour)
        VALUES (1, 1, v_period, DATE '2035-02-28', v_state);
        RAISE EXCEPTION 'Insertion hors période acceptée';
    EXCEPTION WHEN check_violation THEN
        IF SQLERRM = 'Insertion hors période acceptée' THEN RAISE; END IF;
    END;

    BEGIN
        UPDATE pointage_jour SET date_pointage = DATE '2035-04-01' WHERE id_pointage = v_day;
        RAISE EXCEPTION 'Changement de date hors période accepté';
    EXCEPTION WHEN check_violation THEN
        IF SQLERRM = 'Changement de date hors période accepté' THEN RAISE; END IF;
    END;

    BEGIN
        UPDATE pointage_jour SET id_periode_paie = v_other WHERE id_pointage = v_day;
        RAISE EXCEPTION 'Rattachement à une période incompatible accepté';
    EXCEPTION WHEN check_violation THEN
        IF SQLERRM = 'Rattachement à une période incompatible accepté' THEN RAISE; END IF;
    END;

    BEGIN
        UPDATE periode_paie SET date_fin = DATE '2035-04-01' WHERE id_periode_paie = v_period;
        RAISE EXCEPTION 'Modification des bornes acceptée';
    EXCEPTION WHEN check_violation THEN
        IF SQLERRM = 'Modification des bornes acceptée' THEN RAISE; END IF;
    END;

    -- Une affectation identique est sans effet et reste permise.
    UPDATE periode_paie SET date_fin = DATE '2035-03-31' WHERE id_periode_paie = v_period;

    -- Même insertion directe que la matérialisation d'un congé : statut et lien
    -- n'affaiblissent pas le contrôle de l'intervalle.
    BEGIN
        INSERT INTO pointage_jour (id_employe, id_site, id_periode_paie, date_pointage, id_statut_jour)
        VALUES (2, 1, v_period, DATE '2035-04-01',
                (SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = 'CONGE'));
        RAISE EXCEPTION 'Congé hors période accepté';
    EXCEPTION WHEN check_violation THEN
        IF SQLERRM = 'Congé hors période accepté' THEN RAISE; END IF;
    END;
END $$;

ROLLBACK;
