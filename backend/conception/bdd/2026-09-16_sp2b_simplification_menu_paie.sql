-- Sprint 2B : Simplification de l'entrée de menu Paie dans la Sidebar
-- La Sidebar répond uniquement à "Dans quelle grande partie du SIRH suis-je ?"
-- Les étapes internes d'une campagne de paie (Préparation, Population, Variables, Calcul, Contrôle, Validation, Sorties)
-- sont pilotées par le stepper local de campagne et ne doivent pas encombrer la Sidebar principale.

DELETE FROM role_menu WHERE id_menu IN ('PAIE_PERIODES', 'PAIE_VARIABLES', 'PAIE_BULLETINS');
DELETE FROM ref_menu WHERE id_menu IN ('PAIE_PERIODES', 'PAIE_VARIABLES', 'PAIE_BULLETINS');

-- S'assurer que PAIE_MODULE pointe sur /paie comme module racine
UPDATE ref_menu SET href = '/paie', id_pere = NULL, niveau = 1 WHERE id_menu = 'PAIE_MODULE';

INSERT INTO role_menu (id_role, id_menu) 
SELECT 1, 'PAIE_MODULE' 
WHERE NOT EXISTS (SELECT 1 FROM role_menu WHERE id_role = 1 AND id_menu = 'PAIE_MODULE');
