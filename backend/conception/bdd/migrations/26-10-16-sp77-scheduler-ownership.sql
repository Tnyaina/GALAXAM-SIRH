-- Possession des tentatives scheduler et idempotence annuelle des bonifications.
-- Arrêter ou mettre à l'échelle zéro les anciennes instances applicatives avant
-- cette migration : leur ancien UPDATE par seule clé métier ne connaît pas le jeton.
BEGIN;

ALTER TABLE scheduler_execution
    ADD COLUMN IF NOT EXISTS attempt_token UUID,
    ADD COLUMN IF NOT EXISTS lease_until TIMESTAMP;

-- Les exécutions RUNNING existantes reçoivent un bail calculé depuis leur début.
-- Un worker démarré avant la migration ne connaît pas ce jeton et ne pourra pas
-- finaliser la ligne après le déploiement du nouveau code.
UPDATE scheduler_execution
SET attempt_token = COALESCE(attempt_token, gen_random_uuid()),
    lease_until = COALESCE(lease_until, started_at + INTERVAL '30 minutes')
WHERE status = 'RUNNING';

UPDATE scheduler_execution
SET lease_until = NULL
WHERE status <> 'RUNNING' AND lease_until IS NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ck_scheduler_execution_lease'
          AND conrelid = 'scheduler_execution'::regclass
    ) THEN
        ALTER TABLE scheduler_execution
            ADD CONSTRAINT ck_scheduler_execution_lease CHECK (
                (status = 'RUNNING' AND attempt_token IS NOT NULL AND lease_until IS NOT NULL)
                OR (status <> 'RUNNING' AND lease_until IS NULL)
            );
    END IF;
END $$;

DO $$
DECLARE
    conflits TEXT;
BEGIN
    SELECT string_agg(format('salarié %s, année %s : %s lignes', id_employe, annee, nombre), '; ' ORDER BY id_employe, annee)
      INTO conflits
      FROM (
          SELECT id_employe, EXTRACT(YEAR FROM date_mouvement)::INTEGER AS annee, COUNT(*) AS nombre
          FROM mouvement_solde_conge
          WHERE type_mouvement = 'BONIFICATION'
          GROUP BY id_employe, EXTRACT(YEAR FROM date_mouvement)
          HAVING COUNT(*) > 1
          ORDER BY id_employe, annee
          LIMIT 100
      ) doublons;
    IF conflits IS NOT NULL THEN
        RAISE EXCEPTION 'Migration SP77 bloquée : bonifications annuelles en doublon.'
            USING DETAIL = conflits,
                  HINT = 'Examiner les mouvements indiqués et arbitrer leur validité avant de rejouer la migration.';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS idx_uniq_mouvement_bonification_annee
    ON mouvement_solde_conge (id_employe, (EXTRACT(YEAR FROM date_mouvement)))
    WHERE type_mouvement = 'BONIFICATION';

COMMIT;
