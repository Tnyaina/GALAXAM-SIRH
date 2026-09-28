-- Le deuxième repas est une règle automatique du pointage et non une indemnité à saisir.
-- Son montant est inclus dans la ligne REPAS pour la paie ; le détail reste consultable au pointage.
ALTER TABLE pointage_jour
    ADD COLUMN IF NOT EXISTS code_second_repas VARCHAR(20),
    ADD COLUMN IF NOT EXISTS montant_second_repas NUMERIC(12,2) NOT NULL DEFAULT 0;

ALTER TABLE pointage_jour
    ADD CONSTRAINT ck_pointage_second_repas_code
    CHECK (code_second_repas IS NULL OR code_second_repas IN ('REPAS_2', 'REPAS_2_MAJORE'));
