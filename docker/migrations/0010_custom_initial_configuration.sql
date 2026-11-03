-- Personnalisation initialement saisie dans les DDL canoniques.
-- Ces DDL restent figes ; les installations existantes recoivent les memes changements ici.

DELETE FROM ref_menu
WHERE id_menu IN ('PAIE_PRESTATAIRES', 'PAIE_DOSSIERS_SORTIE');

UPDATE parametre_global
SET description = 'Début du travail de nuit'
WHERE code_parametre = 'SEUIL_NUIT_DEBUT'
  AND date_debut = DATE '2001-07-01';

INSERT INTO ref_filiale (code_filiale, raison_sociale, adresse_siege, telephone, email)
VALUES ('VA', 'Vina sy Asa', '6e étage, Tour Forello, Tanjombato',
        '+261 32 12 131 01', 'daf@galaxam.com')
ON CONFLICT (code_filiale) DO NOTHING;

-- Le retrait est refuse par les cles etrangeres si la periode a deja ete utilisee.
DELETE FROM periode_paie
WHERE code = '2026-08'
  AND date_debut = DATE '2026-07-26'
  AND date_fin = DATE '2026-08-25';

SELECT setval(
    pg_get_serial_sequence('periode_paie', 'id_periode_paie'),
    COALESCE((SELECT MAX(id_periode_paie) FROM periode_paie), 1),
    EXISTS (SELECT 1 FROM periode_paie)
);
