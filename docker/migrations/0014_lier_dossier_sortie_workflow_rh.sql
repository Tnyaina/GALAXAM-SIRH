-- Lie chaque dossier de sortie à la rupture RH qui porte sa date et son motif.
-- À exécuter une fois sur les bases existantes avant de déployer le code applicatif.
ALTER TABLE paie_dossier_sortie
    ADD COLUMN IF NOT EXISTS id_workflow_rh INT;

ALTER TABLE paie_dossier_sortie
    ADD CONSTRAINT fk_dossier_sortie_workflow_rh
    FOREIGN KEY (id_workflow_rh) REFERENCES workflow_rh(id_workflow);

CREATE UNIQUE INDEX IF NOT EXISTS uk_dossier_sortie_workflow_rh
    ON paie_dossier_sortie (id_workflow_rh)
    WHERE id_workflow_rh IS NOT NULL;
