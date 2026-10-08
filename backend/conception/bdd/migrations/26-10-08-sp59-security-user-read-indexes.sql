-- SP59 : accélère les recherches case-insensitive de comptes et la rotation
-- des refresh tokens sans modifier leur format ni la politique de session.
CREATE INDEX IF NOT EXISTS idx_utilisateur_identifiant_lower
    ON utilisateur (LOWER(identifiant));

CREATE INDEX IF NOT EXISTS idx_utilisateur_refresh_token
    ON utilisateur (refresh_token)
    WHERE refresh_token IS NOT NULL;
