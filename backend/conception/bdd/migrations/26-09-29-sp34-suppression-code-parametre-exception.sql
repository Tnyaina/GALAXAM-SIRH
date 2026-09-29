-- Migration : Suppression de la colonne obsolète code_parametre_exception dans site_indemnite_config
-- Règle métier obsolète : suppression du fallback vers un paramètre global lorsque le tarif site = 0.

ALTER TABLE site_indemnite_config DROP COLUMN IF EXISTS code_parametre_exception;
