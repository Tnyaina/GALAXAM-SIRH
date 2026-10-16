-- SP74 : impose la cohérence contrat/salarié et service/département
-- Diagnostic préalable uniquement : cette migration ne corrige aucune donnée.
-- Toute anomalie interrompt le script avec un échantillon d'id_affectation.

BEGIN;

DO $$
DECLARE
    v_contrat_count BIGINT;
    v_service_count BIGINT;
    v_contrat_ids TEXT;
    v_service_ids TEXT;
BEGIN
    SELECT COUNT(*)
      INTO v_contrat_count
      FROM employe_affectation_carriere a
      LEFT JOIN contrat_travail c
        ON c.id_contrat = a.id_contrat
       AND c.id_employe = a.id_employe
     WHERE a.id_contrat IS NULL
        OR a.id_employe IS NULL
        OR c.id_contrat IS NULL;

    SELECT string_agg(x.id_affectation::TEXT, ', ' ORDER BY x.id_affectation)
      INTO v_contrat_ids
      FROM (
          SELECT a.id_affectation
            FROM employe_affectation_carriere a
            LEFT JOIN contrat_travail c
              ON c.id_contrat = a.id_contrat
             AND c.id_employe = a.id_employe
           WHERE a.id_contrat IS NULL
              OR a.id_employe IS NULL
              OR c.id_contrat IS NULL
           ORDER BY a.id_affectation
           LIMIT 20
      ) x;

    SELECT COUNT(*)
      INTO v_service_count
      FROM employe_affectation_carriere a
      LEFT JOIN ref_service s
        ON s.id_service = a.id_service
       AND s.id_departement = a.id_departement
     WHERE a.id_service IS NULL
        OR a.id_departement IS NULL
        OR s.id_service IS NULL;

    SELECT string_agg(x.id_affectation::TEXT, ', ' ORDER BY x.id_affectation)
      INTO v_service_ids
      FROM (
          SELECT a.id_affectation
            FROM employe_affectation_carriere a
            LEFT JOIN ref_service s
              ON s.id_service = a.id_service
             AND s.id_departement = a.id_departement
           WHERE a.id_service IS NULL
              OR a.id_departement IS NULL
              OR s.id_service IS NULL
           ORDER BY a.id_affectation
           LIMIT 20
      ) x;

    IF v_contrat_count > 0 OR v_service_count > 0 THEN
        RAISE EXCEPTION USING MESSAGE = format(
            'SP74 bloquée : contrat/salarié incohérents=%s (id_affectation: %s); service/département incohérents=%s (id_affectation: %s). Corriger explicitement les données puis relancer.',
            v_contrat_count, COALESCE(v_contrat_ids, 'aucun'),
            v_service_count, COALESCE(v_service_ids, 'aucun')
        );
    END IF;
END $$;

ALTER TABLE contrat_travail
    ADD CONSTRAINT uq_contrat_id_employe
    UNIQUE (id_contrat, id_employe);

ALTER TABLE ref_service
    ADD CONSTRAINT uq_ref_service_id_departement
    UNIQUE (id_service, id_departement);

ALTER TABLE employe_affectation_carriere
    ADD CONSTRAINT fk_affectation_contrat_employe
    FOREIGN KEY (id_contrat, id_employe)
    REFERENCES contrat_travail (id_contrat, id_employe);

ALTER TABLE employe_affectation_carriere
    ADD CONSTRAINT fk_affectation_service_departement
    FOREIGN KEY (id_service, id_departement)
    REFERENCES ref_service (id_service, id_departement);

COMMIT;
