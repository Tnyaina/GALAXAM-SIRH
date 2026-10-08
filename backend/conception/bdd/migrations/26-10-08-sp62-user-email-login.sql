-- Adresse e-mail dédiée à la connexion; identifiant reste le nom d'affichage.
ALTER TABLE utilisateur ADD COLUMN IF NOT EXISTS email VARCHAR(254);
ALTER TABLE utilisateur DROP CONSTRAINT IF EXISTS utilisateur_identifiant_key;

-- Pour une base sans comptes, cette mise à jour ne touche aucune ligne.
-- Les comptes historiques dont identifiant était déjà l'e-mail conservent leur accès.
UPDATE utilisateur
SET email = LOWER(TRIM(identifiant))
WHERE email IS NULL;

ALTER TABLE utilisateur ALTER COLUMN email SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS idx_utilisateur_email_lower
    ON utilisateur (LOWER(email));
