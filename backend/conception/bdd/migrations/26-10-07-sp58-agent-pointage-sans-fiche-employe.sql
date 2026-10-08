-- SP58 : l'agent de pointage travaille dans le module Pointage.
-- Révoque les accès à la fiche employé accordés par SP57 sur les bases existantes.
-- Idempotent sur les bases où SP57 n'a jamais été appliquée.
UPDATE role_permission
SET droit_lecture = FALSE
WHERE id_role = 2 AND id_module = 1;

DELETE FROM role_menu
WHERE id_role = 2 AND id_menu IN ('GESTION_PERSONNEL', 'PERS_EMPLOYES');
