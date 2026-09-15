-- =====================================================================
-- GALAXAM SIRH : Initialisation PostgreSQL multi-bases (Dev + Test)
-- Fichier SQL natif sans script shell (.sh)
-- =====================================================================

-- 1. Création de la base de données de test dédiée
CREATE DATABASE sirh_galaxam_test;
GRANT ALL PRIVILEGES ON DATABASE sirh_galaxam_test TO galaxam_admin;

-- 2. Chargement des schémas et référentiels sur la base de développement (sirh_galaxam)
\c sirh_galaxam
\i /bdd-schemas/01_schema_core_securite.sql
\i /bdd-schemas/02_schema_referentiels_rh.sql
\i /bdd-schemas/03_schema_employes_contrats.sql
\i /bdd-schemas/04_schema_conges.sql
\i /bdd-schemas/05_schema_pointage.sql
\i /bdd-schemas/06_schema_paie.sql
\i /bdd-schemas/2026-09-13_snapshot_pointage_versionne.sql
\i /bdd-schemas/2026-09-14_workflow_periode_paie.sql
\i /bdd-schemas/2026-09-14_remove_business_sql.sql


-- 3. Chargement des mêmes schémas sur la base de test (sirh_galaxam_test)
\c sirh_galaxam_test
\i /bdd-schemas/01_schema_core_securite.sql
\i /bdd-schemas/02_schema_referentiels_rh.sql
\i /bdd-schemas/03_schema_employes_contrats.sql
\i /bdd-schemas/04_schema_conges.sql
\i /bdd-schemas/05_schema_pointage.sql
\i /bdd-schemas/06_schema_paie.sql
\i /bdd-schemas/2026-09-13_snapshot_pointage_versionne.sql
\i /bdd-schemas/2026-09-14_workflow_periode_paie.sql
\i /bdd-schemas/2026-09-14_remove_business_sql.sql
