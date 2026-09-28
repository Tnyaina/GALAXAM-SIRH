-- Enregistrement des décisions DAF sur les droits candidats à la prescription.
CREATE TABLE IF NOT EXISTS conge_expiration_examen (
    id_examen SERIAL PRIMARY KEY,
    id_employe INT NOT NULL REFERENCES employe(id_employe),
    decision VARCHAR(20) NOT NULL CHECK (decision IN ('REPORTE', 'EXPIRE')),
    jours_examines NUMERIC(8,2) NOT NULL CHECK (jours_examines > 0),
    motif TEXT NOT NULL,
    planification_verifiee BOOLEAN NOT NULL DEFAULT FALSE,
    date_prochaine_revue DATE,
    id_mouvement_expiration INT REFERENCES mouvement_solde_conge(id_mouvement),
    decide_par INT NOT NULL REFERENCES utilisateur(id_utilisateur),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_conge_expiration_decision CHECK (
        (decision = 'REPORTE' AND date_prochaine_revue IS NOT NULL AND id_mouvement_expiration IS NULL)
        OR (decision = 'EXPIRE' AND date_prochaine_revue IS NULL AND id_mouvement_expiration IS NOT NULL AND planification_verifiee)
    )
);
CREATE INDEX IF NOT EXISTS idx_conge_expiration_examen_employe_date
    ON conge_expiration_examen(id_employe, created_at DESC, id_examen DESC);

UPDATE parametre_global
SET description = 'Ancienneté en mois à partir de laquelle les droits disponibles sont signalés pour examen DAF au titre de la prescription ; aucune expiration automatique'
WHERE code_parametre = 'DUREE_PRESCRIPTION_SOLDE_CONGE_MOIS';

UPDATE parametre_global
SET description = 'Plafond technique configuré du solde cumulé ; limite les acquisitions au-delà de cette valeur'
WHERE code_parametre = 'PLAFOND_CUMUL_SOLDE_CONGE_JOURS';
