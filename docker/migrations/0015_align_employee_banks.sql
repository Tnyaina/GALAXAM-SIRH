-- Aligne le référentiel bancaire sur les banques présentes dans le fichier
-- de préparation des salariés. Les comptes existants restent intacts.
INSERT INTO ref_banque (code_banque, nom_banque, code_swift, est_actif)
VALUES
    ('00004', 'Banque Malgache de l''Océan Indien', 'BMOIMGMG', TRUE),
    ('00006', 'The Mauritius Commercial Bank (Madagascar) SA', 'MCBLMGMG', TRUE)
ON CONFLICT (code_banque) DO UPDATE
SET nom_banque = EXCLUDED.nom_banque,
    code_swift = EXCLUDED.code_swift,
    est_actif = TRUE;

-- Supprime les banques d'amorçage si aucun compte ne les utilise.
DELETE FROM ref_banque b
WHERE b.code_banque IN ('00005', '00008')
  AND NOT EXISTS (
      SELECT 1 FROM employe_compte_banque c WHERE c.id_banque = b.id_banque
  );

-- Si une installation possède déjà des comptes liés, conserve leur intégrité
-- historique et retire ces banques des choix actifs.
UPDATE ref_banque
SET est_actif = FALSE
WHERE code_banque IN ('00005', '00008');
