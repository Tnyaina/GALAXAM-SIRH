-- Source du calcul figée lors de la validation du décompte.
ALTER TABLE paie_stc ADD COLUMN IF NOT EXISTS id_contrat INT REFERENCES contrat_travail(id_contrat);
ALTER TABLE paie_stc ADD COLUMN IF NOT EXISTS source_pointage VARCHAR(30);
