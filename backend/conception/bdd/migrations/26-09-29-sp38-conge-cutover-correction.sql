-- Appliquer uniquement après vérification explicite de la base cible.
ALTER TABLE mouvement_solde_conge ADD COLUMN IF NOT EXISTS valeur_source NUMERIC(14,2);
UPDATE mouvement_solde_conge m SET valeur_source = j.valorisation_mensuelle
FROM conge_journal_droit j
WHERE m.id_employe = j.id_employe AND m.type_mouvement = 'ACQUISITION'
  AND EXTRACT(YEAR FROM m.date_mouvement) = j.annee
  AND EXTRACT(MONTH FROM m.date_mouvement) = j.mois
  AND m.nb_jours = ROUND(j.jours_droit_acquis, 2) AND m.valeur_source IS NULL;
-- Figer les taux historiques existants lorsque la source est non ambiguë.
UPDATE mouvement_solde_conge m SET valeur_source = (
    SELECT t.taux_journalier FROM conge_taux_applicable t
    WHERE t.id_employe = m.id_employe
      AND t.date_effet <= COALESCE(d.date_debut, m.date_mouvement)
      AND (t.date_fin IS NULL OR t.date_fin > COALESCE(d.date_debut, m.date_mouvement))
    ORDER BY t.date_effet DESC, t.id_taux_applicable DESC LIMIT 1
)
FROM demande_conge d
WHERE m.id_demande_conge = d.id_demande_conge AND m.type_mouvement = 'PRISE'
  AND m.valeur_source IS NULL;
UPDATE mouvement_solde_conge m SET valeur_source = m.nb_jours * (
    SELECT t.taux_journalier FROM conge_taux_applicable t
    WHERE t.id_employe = m.id_employe AND t.date_effet <= m.date_mouvement
      AND (t.date_fin IS NULL OR t.date_fin > m.date_mouvement)
    ORDER BY t.date_effet DESC, t.id_taux_applicable DESC LIMIT 1
)
WHERE m.nb_jours > 0 AND m.type_mouvement IN ('BONIFICATION', 'AJUSTEMENT')
  AND m.id_demande_conge IS NULL AND m.valeur_source IS NULL;
CREATE TABLE IF NOT EXISTS conge_cutover_correction (
    id_correction SERIAL PRIMARY KEY,
    id_employe INT NOT NULL REFERENCES employe(id_employe),
    id_mouvement_reprise INT NOT NULL REFERENCES mouvement_solde_conge(id_mouvement),
    id_mouvement_ajustement INT NOT NULL UNIQUE REFERENCES mouvement_solde_conge(id_mouvement),
    ancien_reliquat_jours NUMERIC(8,2) NOT NULL,
    nouveau_reliquat_jours NUMERIC(8,2) NOT NULL,
    ancien_reliquat_valeur NUMERIC(14,2) NOT NULL,
    nouveau_reliquat_valeur NUMERIC(14,2) NOT NULL,
    ancien_taux NUMERIC(14,6) NOT NULL,
    nouveau_taux NUMERIC(14,6) NOT NULL,
    regularisation_a_examiner NUMERIC(14,2) NOT NULL DEFAULT 0,
    motif TEXT NOT NULL CHECK (length(trim(motif)) > 0),
    corrige_par INT NOT NULL REFERENCES utilisateur(id_utilisateur),
    date_correction TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_cutover_correction_reliquat CHECK (nouveau_reliquat_jours >= 0 AND nouveau_reliquat_valeur >= 0)
);
CREATE INDEX IF NOT EXISTS idx_conge_cutover_correction_employe
    ON conge_cutover_correction(id_employe, date_correction);
