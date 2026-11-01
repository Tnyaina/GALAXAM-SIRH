-- Les taux journaliers gardent leur précision ; les valeurs monétaires restent à 2 décimales.
ALTER TABLE conge_periode_reference
    ALTER COLUMN taux_calcule_cloture TYPE NUMERIC(20,6);
ALTER TABLE conge_journal_droit
    ALTER COLUMN taux_journalier_conge TYPE NUMERIC(20,6);
ALTER TABLE conge_taux_applicable
    ALTER COLUMN taux_journalier TYPE NUMERIC(20,6);
ALTER TABLE paie_stc
    ALTER COLUMN taux_journalier_conge TYPE NUMERIC(20,6);
