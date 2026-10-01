-- Évolution du catalogue existant : aucune nouvelle table.
BEGIN;

ALTER TABLE paie_rubrique ADD COLUMN IF NOT EXISTS reference_reglementaire TEXT;
ALTER TABLE paie_rubrique ADD COLUMN IF NOT EXISTS date_debut_effet DATE;

INSERT INTO paie_rubrique (code, libelle, categorie, type_valeur, sens,
    soumis_cnaps, soumis_sante, soumis_irsa, est_fixe, ordre_affichage, description)
VALUES ('RETENUE_DIVERSE', 'Retenue exceptionnelle', 'RETENUE_DIVERSE', 'MONTANT',
    'RETENUE', FALSE, FALSE, FALSE, FALSE, 640, 'Retenue nette manuelle motivée')
ON CONFLICT (code) DO NOTHING;

-- Une réaffectation historique modifierait la lecture d'anciens mouvements : arrêt explicite.
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM paie_avance_acompte paa
    JOIN ref_type_mouvement_paie t ON t.id_type_mouvement = paa.id_type_mouvement
    JOIN paie_rubrique r ON r.id_rubrique = t.id_rubrique
    WHERE t.code = 'RETENUE_DIVERSE' AND r.code = 'REGULARISATION_PAIE'
  ) THEN
    RAISE EXCEPTION 'RETENUE_DIVERSE possède des mouvements historiques : examiner leur traitement avant de modifier son lien de rubrique';
  END IF;
END $$;

UPDATE ref_type_mouvement_paie
SET id_rubrique = (SELECT id_rubrique FROM paie_rubrique WHERE code = 'RETENUE_DIVERSE')
WHERE code = 'RETENUE_DIVERSE'
  AND id_rubrique = (SELECT id_rubrique FROM paie_rubrique WHERE code = 'REGULARISATION_PAIE');

COMMIT;
