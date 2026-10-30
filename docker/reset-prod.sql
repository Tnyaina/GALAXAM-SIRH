DROP SCHEMA IF EXISTS public CASCADE;
CREATE SCHEMA public AUTHORIZATION galaxam_admin;
\i /bdd-schemas/01_schema_core_securite.sql
\i /bdd-schemas/02_schema_referentiels_rh.sql
\i /bdd-schemas/03_schema_employes_contrats.sql
\i /bdd-schemas/04_schema_conges.sql
\i /bdd-schemas/05_schema_pointage.sql
\i /bdd-schemas/06_schema_paie.sql
