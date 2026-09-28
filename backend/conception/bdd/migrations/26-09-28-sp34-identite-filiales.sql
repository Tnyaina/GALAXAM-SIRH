-- Identité juridique et coordonnées propres à chaque filiale.
ALTER TABLE ref_filiale ADD COLUMN IF NOT EXISTS adresse_siege VARCHAR(255);
ALTER TABLE ref_filiale ADD COLUMN IF NOT EXISTS rcs VARCHAR(80);
ALTER TABLE ref_filiale ADD COLUMN IF NOT EXISTS telephone VARCHAR(40);
ALTER TABLE ref_filiale ADD COLUMN IF NOT EXISTS email VARCHAR(255);

-- Adresse du siège confirmée pour GLX ; ne pas écraser une valeur déjà renseignée.
UPDATE ref_filiale SET adresse_siege = '3e étage, Tour Forello, Tanjombato'
WHERE code_filiale = 'GLX' AND NULLIF(BTRIM(adresse_siege), '') IS NULL;
