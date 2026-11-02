-- Les demi-journées doivent rester fractionnaires dans le snapshot utilisé par la paie.
ALTER TABLE pointage_cumul_periode
    ALTER COLUMN nb_jours_presents TYPE NUMERIC(7,2) USING nb_jours_presents::NUMERIC(7,2),
    ALTER COLUMN nb_jours_conge TYPE NUMERIC(7,2) USING nb_jours_conge::NUMERIC(7,2);
