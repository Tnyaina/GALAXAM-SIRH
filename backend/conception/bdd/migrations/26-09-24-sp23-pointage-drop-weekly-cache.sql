-- Weekly attendance is calculated from pointage_jour on demand.
-- All runtime readers/writers were removed before this forward migration.
DROP TABLE IF EXISTS pointage_semaine_calcule;
