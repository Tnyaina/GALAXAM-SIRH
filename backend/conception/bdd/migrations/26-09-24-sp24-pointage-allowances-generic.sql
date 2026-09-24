ALTER TABLE site_indemnite_config
    ADD COLUMN IF NOT EXISTS code_parametre_exception VARCHAR(100);

ALTER TABLE ref_indemnite
    ADD COLUMN IF NOT EXISTS code_rubrique_paie VARCHAR(50);
ALTER TABLE ref_indemnite
    ADD COLUMN IF NOT EXISTS soumis_cotisation BOOLEAN;

-- Seuls les deux anciens flux de Paie ont un traitement historique connu.
-- Toute autre indemnité doit être qualifiée explicitement avant gel.
UPDATE ref_indemnite SET soumis_cotisation = FALSE
WHERE code IN ('REPAS', 'DEPLACEMENT') AND soumis_cotisation IS NULL;

UPDATE ref_indemnite SET code_rubrique_paie = 'INDEMNITE_REPAS'
WHERE code = 'REPAS' AND code_rubrique_paie IS NULL;
UPDATE ref_indemnite SET code_rubrique_paie = 'INDEMNITE_TRANSPORT'
WHERE code = 'DEPLACEMENT' AND code_rubrique_paie IS NULL;

-- Un montant de site nul ne préaccorde rien. Cette règle permet l'octroi manuel
-- au tarif de référence global, sans branche applicative sur le code REPAS.
UPDATE site_indemnite_config c
SET code_parametre_exception = 'MONTANT_REPAS_MIDI'
FROM ref_indemnite i
WHERE c.id_indemnite = i.id_indemnite
  AND i.code = 'REPAS'
  AND c.montant_reference = 0
  AND c.code_parametre_exception IS NULL;

CREATE TABLE IF NOT EXISTS pointage_snapshot_indemnite (
    id_cumul INT NOT NULL REFERENCES pointage_cumul_periode(id_cumul) ON DELETE CASCADE,
    id_indemnite INT NOT NULL REFERENCES ref_indemnite(id_indemnite),
    code VARCHAR(20) NOT NULL,
    libelle VARCHAR(100) NOT NULL,
    imposable_irsa BOOLEAN NOT NULL,
    soumis_cotisation BOOLEAN NOT NULL,
    code_rubrique_paie VARCHAR(50) NOT NULL,
    montant NUMERIC(12,2) NOT NULL CHECK (montant >= 0),
    PRIMARY KEY (id_cumul, id_indemnite)
);

ALTER TABLE pointage_cumul_periode
    ADD COLUMN IF NOT EXISTS contrat_indemnites_version INTEGER NOT NULL DEFAULT 1;
