BEGIN;

CREATE TABLE IF NOT EXISTS paie_mouvement_justificatif (
    id_avance INT PRIMARY KEY REFERENCES paie_avance_acompte(id_avance) ON DELETE CASCADE,
    nom_fichier VARCHAR(255) NOT NULL,
    type_mime VARCHAR(60) NOT NULL,
    contenu BYTEA NOT NULL,
    taille_octets BIGINT NOT NULL CHECK (taille_octets > 0 AND taille_octets <= 5242880),
    depose_par INT REFERENCES utilisateur(id_utilisateur),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

UPDATE ref_type_mouvement_paie
SET libelle = 'Remboursement de frais validés',
    nature = 'NOTE_DE_FRAIS', owner_code = 'DAF_PAIE',
    saisie_manuelle_autorisee = TRUE, periode_obligatoire = TRUE,
    motif_obligatoire = TRUE, provenance_defaut = 'NOTE_DE_FRAIS',
    description = 'Remboursement hors brut avec justificatif obligatoire avant approbation'
WHERE code = 'NOTE_DE_FRAIS';

COMMIT;
