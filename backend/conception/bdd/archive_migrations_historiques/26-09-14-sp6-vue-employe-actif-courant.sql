-- =============================================================================
-- Migration SQL 26-09-14-sp6 : Vue Standardisée Employé Actif Courant
-- Factorisation du pattern SQL employé + affectation active + contrat
-- Conforme audit global GALAXAM-SIRH (Axe DUP-005 & ACT-014)
-- =============================================================================

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
    COALESCE(tri.soumis_cnaps, TRUE) AS soumis_cnaps,
    COALESCE(tri.soumis_sante, TRUE) AS soumis_sante,
    COALESCE(tri.soumis_irsa, TRUE) AS soumis_irsa,
    COALESCE(tri.soumis_isi_5, FALSE) AS soumis_isi_5,
    ct.date_debut AS date_embauche,
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
LEFT JOIN ref_categorie_professionnelle cat ON eac.id_categorie_professionnelle = cat.id_categorie_professionnelle
WHERE e.est_actif = TRUE;

COMMENT ON VIEW v_employe_actif_courant IS 'Vue canonique des salariés actifs avec leur affectation et contrat en cours (audit ACT-014).';
