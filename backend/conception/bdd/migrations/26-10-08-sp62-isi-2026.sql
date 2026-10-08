-- LFI 2026 : fin de la retenue ISI générale sur les prestations.
-- Ne modifie aucune ligne de bulletin ni de déclaration historique.
UPDATE parametre_global
SET est_actif = FALSE, date_fin = CASE WHEN date_debut <= DATE '2025-12-31'
    THEN LEAST(COALESCE(date_fin, DATE '2025-12-31'), DATE '2025-12-31') ELSE date_fin END
WHERE code_parametre IN ('TAUX_ISI', 'TAUX_ISI_POURCENT');

UPDATE ref_type_intervenant SET soumis_irsa = FALSE, soumis_isi_5 = FALSE,
    soumis_cnaps = FALSE, soumis_sante = FALSE,
    libelle = 'Consultant indépendant (hors paie)'
WHERE code = 'CONSULTANT';
UPDATE ref_type_intervenant SET soumis_isi_5 = FALSE, soumis_irsa = TRUE,
    soumis_cnaps = TRUE, soumis_sante = TRUE,
    libelle = 'Travailleur temporaire salarié'
WHERE code = 'TEMPORAIRE';
UPDATE ref_type_intervenant SET soumis_isi_5 = FALSE
WHERE soumis_isi_5 = TRUE;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_type_intervenant_isi_2026') THEN
        ALTER TABLE ref_type_intervenant ADD CONSTRAINT chk_type_intervenant_isi_2026
            CHECK (soumis_isi_5 = FALSE);
    END IF;
END $$;
INSERT INTO ref_type_intervenant
    (code, libelle, soumis_irsa, soumis_isi_5, soumis_cnaps, soumis_sante, est_actif)
VALUES ('CONSULTANT_SALARIE', 'Consultant salarié', TRUE, FALSE, TRUE, TRUE, TRUE)
ON CONFLICT (code) DO UPDATE SET libelle = EXCLUDED.libelle,
    soumis_irsa = EXCLUDED.soumis_irsa, soumis_isi_5 = FALSE,
    soumis_cnaps = EXCLUDED.soumis_cnaps, soumis_sante = EXCLUDED.soumis_sante;

UPDATE paie_rubrique SET est_actif = FALSE,
    description = 'Historique uniquement : retenue ISI générale supprimée en 2026'
WHERE code = 'RETENUE_ISI_5';
