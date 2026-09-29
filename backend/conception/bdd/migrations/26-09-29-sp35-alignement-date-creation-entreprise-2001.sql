-- =====================================================================
-- Migration 26-09-29-sp35 : Alignement des configurations légales et barèmes
-- sur la date de création de l'entreprise (01/07/2001)
-- =====================================================================

UPDATE paie_config_legale SET date_debut = '2001-07-01' WHERE id_config_legale = 1;
UPDATE bareme_irsa SET date_debut = '2001-07-01' WHERE id_config_legale = 1;
UPDATE ref_taux_majoration SET date_debut = '2001-07-01' WHERE id_config_legale = 1;
UPDATE ref_type_cotisation SET date_debut = '2001-07-01' WHERE date_debut = '2022-01-01';
UPDATE parametre_global SET date_debut = '2001-07-01' WHERE date_debut = '2022-01-01';
