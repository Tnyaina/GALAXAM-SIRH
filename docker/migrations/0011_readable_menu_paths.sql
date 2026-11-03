-- Les menus pointent directement vers les nouvelles pages à chemin lisible.
UPDATE ref_menu SET href = '/conges/planning' WHERE id_menu = 'CONGES_GENERAL';
UPDATE ref_menu SET href = '/conges/demandes' WHERE id_menu = 'CONGES_DEMANDES';
UPDATE ref_menu SET href = '/conges/droits' WHERE id_menu = 'CONGES_PERIODES';
UPDATE ref_menu SET href = '/pointages/saisie' WHERE id_menu = 'POINTAGE_JOURNALIER';
UPDATE ref_menu SET href = '/pointages/suivi' WHERE id_menu = 'POINTAGE_SUIVI';
UPDATE ref_menu SET href = '/pointages/arbitrage' WHERE id_menu = 'POINTAGE_VALIDATION';
UPDATE ref_menu SET href = '/paie/cycles' WHERE id_menu = 'PAIE_CAMPAGNE';
