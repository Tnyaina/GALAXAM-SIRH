BEGIN;

-- Les consommateurs ont été migrés vers CalendrierOuvrableEngine et
-- PaieConfigLegale.findActiveAt(targetDate). RESTRICT garantit qu'aucune
-- dépendance SQL non inventoriée n'est supprimée silencieusement.
DROP FUNCTION IF EXISTS fn_jours_ouvrables(DATE, DATE, INTEGER) RESTRICT;
DROP VIEW IF EXISTS v_config_legale_active RESTRICT;

-- Une absence d'assujettissement est une configuration manquante, jamais une
-- décision fiscale implicite. Les données existantes doivent être complètes
-- avant l'application de cette migration.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM ref_type_intervenant
        WHERE soumis_irsa IS NULL OR soumis_isi_5 IS NULL
           OR soumis_cnaps IS NULL OR soumis_sante IS NULL
    ) THEN
        RAISE EXCEPTION 'ref_type_intervenant contient des assujettissements non renseignés';
    END IF;

    IF EXISTS (
        SELECT 1 FROM paie_rubrique
        WHERE soumis_irsa IS NULL OR soumis_cnaps IS NULL OR soumis_sante IS NULL
    ) THEN
        RAISE EXCEPTION 'paie_rubrique contient des assujettissements non renseignés';
    END IF;
END $$;

ALTER TABLE ref_type_intervenant
    ALTER COLUMN soumis_irsa SET NOT NULL,
    ALTER COLUMN soumis_irsa DROP DEFAULT,
    ALTER COLUMN soumis_isi_5 SET NOT NULL,
    ALTER COLUMN soumis_isi_5 DROP DEFAULT,
    ALTER COLUMN soumis_cnaps SET NOT NULL,
    ALTER COLUMN soumis_cnaps DROP DEFAULT,
    ALTER COLUMN soumis_sante SET NOT NULL,
    ALTER COLUMN soumis_sante DROP DEFAULT;

ALTER TABLE paie_rubrique
    ALTER COLUMN soumis_irsa DROP DEFAULT,
    ALTER COLUMN soumis_cnaps DROP DEFAULT,
    ALTER COLUMN soumis_sante DROP DEFAULT;

-- Projection brute conservée pour les écrans historiques qui consomment la
-- vue : aucune valeur fiscale n'y est désormais inventée.
DROP VIEW IF EXISTS v_employe_actif_courant RESTRICT;
CREATE OR REPLACE VIEW v_employe_actif_courant AS
SELECT
    e.id_employe,
    e.matricule,
    e.nom,
    e.prenom,
    TRIM(COALESCE(e.nom, '') || ' ' || COALESCE(e.prenom, '')) AS nom_complet,
    e.cin_numero,
    e.cin_date_delivrance,
    e.cin_lieu_delivrance,
    e.num_affiliation_cnaps,
    e.est_actif,
    eac.id_affectation,
    eac.id_contrat,
    eac.id_fonction,
    eac.id_departement,
    eac.id_service,
    eac.id_site,
    eac.id_categorie_professionnelle,
    eac.id_mode_calcul_paie,
    eac.salaire_base,
    eac.date_debut AS date_debut_affectation,
    f.libelle AS fonction_nom,
    d.libelle AS departement_nom,
    s.code_site,
    s.libelle AS site_nom,
    cat.code AS categorie_code,
    cat.libelle AS categorie_nom,
    tri.code AS regime_code,
    tri.soumis_cnaps,
    tri.soumis_sante,
    tri.soumis_irsa,
    tri.soumis_isi_5,
    ct.date_debut AS date_debut_contrat,
    COALESCE(
        (SELECT MIN(ct_init.date_debut)
         FROM contrat_travail ct_init
         WHERE ct_init.id_employe = e.id_employe),
        ct.date_debut,
        e.date_declaration
    ) AS date_embauche,
    tc.code AS type_contrat_code
FROM employe e
JOIN employe_affectation_carriere eac ON e.id_employe = eac.id_employe
JOIN ref_statut_rh rsa ON eac.id_statut_rh = rsa.id_statut_rh AND rsa.code = 'ACTIF'
LEFT JOIN contrat_travail ct ON eac.id_contrat = ct.id_contrat
LEFT JOIN ref_type_contrat tc ON ct.id_type_contrat = tc.id_type_contrat
LEFT JOIN ref_type_intervenant tri ON ct.id_type_intervenant = tri.id_type_intervenant
LEFT JOIN ref_fonction f ON eac.id_fonction = f.id_fonction
LEFT JOIN ref_departement d ON eac.id_departement = d.id_departement
LEFT JOIN ref_site s ON eac.id_site = s.id_site
LEFT JOIN ref_categorie_professionnelle cat
    ON eac.id_categorie_professionnelle = cat.id_categorie_professionnelle
WHERE e.est_actif = TRUE;

COMMIT;
