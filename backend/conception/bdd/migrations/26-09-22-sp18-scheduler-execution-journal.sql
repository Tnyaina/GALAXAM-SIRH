-- Journal persistant des traitements planifiés.
-- Une clé métier par job empêche un double traitement après redémarrage ou
-- lorsque plusieurs instances applicatives partagent la même base.
CREATE TABLE IF NOT EXISTS scheduler_execution (
    job_code VARCHAR(80) NOT NULL,
    execution_key DATE NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED')),
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    attempt_count INTEGER NOT NULL DEFAULT 1,
    error_message TEXT NULL,
    PRIMARY KEY (job_code, execution_key)
);

CREATE INDEX IF NOT EXISTS idx_scheduler_execution_status
    ON scheduler_execution (status, started_at DESC);
