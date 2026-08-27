-- ============================================================
-- PATCH : Paramètres globaux pour le renouvellement et plafonds CDD
-- Date   : 2026-08-27
-- ============================================================

INSERT INTO parametre_global (code_parametre, categorie, valeur, description, date_debut) VALUES
('DUREE_MAX_CDD_MOIS', 'CONTRAT', '24', 'Durée maximale cumulée d''un CDD et de ses renouvellements (24 mois / 2 ans). Au-delà, requalification en CDI de plein droit.', '2026-01-01'),
('NB_MAX_RENOUVELLEMENTS_CDD', 'CONTRAT', '2', 'Nombre maximal de renouvellements d''un CDD par avenant (2 fois max). Au 3ème renouvellement, requalification en CDI.', '2026-01-01')
ON CONFLICT (code_parametre) DO UPDATE 
SET valeur = EXCLUDED.valeur, description = EXCLUDED.description;

-- Création d'une vue globale d'audit de carrière
CREATE OR REPLACE VIEW v_historique_carriere_global AS

SELECT 
    e.id_employe,
    e.matricule,
    e.nom || ' ' || e.prenom AS nom_complet,
    'CONTRAT' AS type_element,
    ct.id_contrat AS id_reference,
    ct.numero_contrat AS reference_code,
    rtc.libelle AS libelle_type,
    rsc.code AS code_statut,
    rsc.libelle AS libelle_statut,
    ct.date_debut AS date_debut_validite,
    ct.date_fin AS date_fin_validite,
    NULL::numeric AS valeur_financiere,
    ct.commentaire AS motif_ou_commentaire,
    ct.created_at AS date_enregistrement
FROM contrat_travail ct
JOIN employe e ON ct.id_employe = e.id_employe
LEFT JOIN ref_type_contrat rtc ON ct.id_type_contrat = rtc.id_type_contrat
LEFT JOIN ref_statut_rh rsc ON ct.id_statut_rh = rsc.id_statut_rh

UNION ALL

SELECT 
    e.id_employe,
    e.matricule,
    e.nom || ' ' || e.prenom AS nom_complet,
    'AFFECTATION' AS type_element,
    eac.id_affectation AS id_reference,
    rf.code_fonction AS reference_code,
    rf.libelle AS libelle_type,
    rsa.code AS code_statut,
    rsa.libelle AS libelle_statut,
    eac.date_debut AS date_debut_validite,
    eac.date_fin AS date_fin_validite,
    eac.salaire_base AS valeur_financiere,
    rs.libelle || ' / ' || rd.libelle AS motif_ou_commentaire,
    eac.created_at AS date_enregistrement
FROM employe_affectation_carriere eac
JOIN employe e ON eac.id_employe = e.id_employe
LEFT JOIN ref_fonction rf ON eac.id_fonction = rf.id_fonction
LEFT JOIN ref_site rs ON eac.id_site = rs.id_site
LEFT JOIN ref_departement rd ON eac.id_departement = rd.id_departement
LEFT JOIN ref_statut_rh rsa ON eac.id_statut_rh = rsa.id_statut_rh

UNION ALL

SELECT 
    e.id_employe,
    e.matricule,
    e.nom || ' ' || e.prenom AS nom_complet,
    'PERIODE_ESSAI' AS type_element,
    pe.id_periode_essai AS id_reference,
    'ESSAI-N' || pe.numero_periode AS reference_code,
    'Période d''essai N°' || pe.numero_periode AS libelle_type,
    pe.statut AS code_statut,
    pe.statut AS libelle_statut,
    pe.date_debut AS date_debut_validite,
    pe.date_fin_prevue AS date_fin_validite,
    NULL::numeric AS valeur_financiere,
    pe.commentaire AS motif_ou_commentaire,
    pe.created_at AS date_enregistrement
FROM contrat_periode_essai pe
JOIN contrat_travail ct ON pe.id_contrat = ct.id_contrat
JOIN employe e ON ct.id_employe = e.id_employe

UNION ALL

SELECT 
    e.id_employe,
    e.matricule,
    e.nom || ' ' || e.prenom AS nom_complet,
    'WORKFLOW_RH' AS type_element,
    w.id_workflow AS id_reference,
    tev.code AS reference_code,
    tev.libelle AS libelle_type,
    rsw.code AS code_statut,
    rsw.libelle AS libelle_statut,
    w.date_effet AS date_debut_validite,
    NULL::date AS date_fin_validite,
    NULL::numeric AS valeur_financiere,
    w.motif AS motif_ou_commentaire,
    w.created_at AS date_enregistrement
FROM workflow_rh w
JOIN employe e ON w.id_employe = e.id_employe
LEFT JOIN ref_type_evenement_rh tev ON w.id_type_evenement_rh = tev.id_type_evenement_rh
LEFT JOIN ref_statut_rh rsw ON w.id_statut_rh = rsw.id_statut_rh;