-- =====================================================================
-- INSERTION DES CONTRATS, AFFECTATIONS, WORKFLOWS ET CONGÉS INITIAUX
-- (Généré avec respect des règles de gestion SIRH Galaxam)
-- =====================================================================

INSERT INTO contrat_travail (
    id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_contrat, 
    date_debut, date_signature, numero_contrat
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '1'), 
    (SELECT id_filiale FROM ref_filiale WHERE code_filiale = 'GLX'), 
    (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDI'), 
    (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT'), 
    (SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'ACTIF'),
    '2002-01-01', '2002-01-01', 'CT-1-01'
) ON CONFLICT (numero_contrat) DO NOTHING;
INSERT INTO employe_affectation_carriere (
    id_employe, id_contrat, id_statut_affectation, id_motif_mouvement, 
    id_site, id_departement, id_service, id_fonction, 
    id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '1'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-1-01'),
    (SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = 'ACTIVE'),
    (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'),
    (SELECT id_site FROM ref_site WHERE code_site = 'TNR'),
    (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG'),
    (SELECT id_service FROM ref_service WHERE code_service = 'ADM' AND id_departement = (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG')),
    (SELECT id_fonction FROM ref_fonction WHERE code_fonction = 'DG'),
    (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'CADRE'),
    (SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie WHERE code = 'MENSUEL'),
    0.00,
    '2002-01-01'
);
INSERT INTO workflow_rh (
    id_employe, id_type_evenement_rh, id_statut_workflow, 
    id_contrat_cible, id_affectation_cible, 
    date_demande, date_effet, motif, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '1'),
    (SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'EMBAUCHE_INITIALE'),
    (SELECT id_statut_workflow FROM ref_statut_workflow WHERE code = 'VALIDE'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-1-01'),
    (SELECT id_affectation FROM employe_affectation_carriere WHERE id_contrat = (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-1-01') AND id_motif_mouvement = (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE') LIMIT 1),
    '2002-01-01', '2002-01-01', 'Reprise des données existantes (Embauche Initiale)', 'Migration historique'
);
INSERT INTO mouvement_solde_conge (
    id_employe, type_mouvement, nb_jours, date_mouvement, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '1'), 'ACQUISITION', 15.0, '2002-01-01', 'Solde initial repris'
);

INSERT INTO contrat_travail (
    id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_contrat, 
    date_debut, date_signature, numero_contrat
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '35'), 
    (SELECT id_filiale FROM ref_filiale WHERE code_filiale = 'GLX'), 
    (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDI'), 
    (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT'), 
    (SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'ACTIF'),
    '2002-09-02', '2002-09-02', 'CT-35-01'
) ON CONFLICT (numero_contrat) DO NOTHING;
INSERT INTO employe_affectation_carriere (
    id_employe, id_contrat, id_statut_affectation, id_motif_mouvement, 
    id_site, id_departement, id_service, id_fonction, 
    id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '35'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-35-01'),
    (SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = 'ACTIVE'),
    (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'),
    (SELECT id_site FROM ref_site WHERE code_site = 'TNR'),
    (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG'),
    (SELECT id_service FROM ref_service WHERE code_service = 'ADM' AND id_departement = (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG')),
    (SELECT id_fonction FROM ref_fonction WHERE code_fonction = 'DAF'),
    (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'CADRE'),
    (SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie WHERE code = 'MENSUEL'),
    0.00,
    '2002-09-02'
);
INSERT INTO workflow_rh (
    id_employe, id_type_evenement_rh, id_statut_workflow, 
    id_contrat_cible, id_affectation_cible, 
    date_demande, date_effet, motif, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '35'),
    (SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'EMBAUCHE_INITIALE'),
    (SELECT id_statut_workflow FROM ref_statut_workflow WHERE code = 'VALIDE'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-35-01'),
    (SELECT id_affectation FROM employe_affectation_carriere WHERE id_contrat = (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-35-01') AND id_motif_mouvement = (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE') LIMIT 1),
    '2002-09-02', '2002-09-02', 'Reprise des données existantes (Embauche Initiale)', 'Migration historique'
);
INSERT INTO mouvement_solde_conge (
    id_employe, type_mouvement, nb_jours, date_mouvement, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '35'), 'ACQUISITION', 15.0, '2002-09-02', 'Solde initial repris'
);

INSERT INTO contrat_travail (
    id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_contrat, 
    date_debut, date_signature, numero_contrat
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '51'), 
    (SELECT id_filiale FROM ref_filiale WHERE code_filiale = 'GLX'), 
    (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDI'), 
    (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT'), 
    (SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'ACTIF'),
    '2003-02-10', '2003-02-10', 'CT-51-01'
) ON CONFLICT (numero_contrat) DO NOTHING;
INSERT INTO employe_affectation_carriere (
    id_employe, id_contrat, id_statut_affectation, id_motif_mouvement, 
    id_site, id_departement, id_service, id_fonction, 
    id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '51'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-51-01'),
    (SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = 'ACTIVE'),
    (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'),
    (SELECT id_site FROM ref_site WHERE code_site = 'TNR'),
    (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG'),
    (SELECT id_service FROM ref_service WHERE code_service = 'ADM' AND id_departement = (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG')),
    (SELECT id_fonction FROM ref_fonction WHERE code_fonction = 'AGENT_SURFACE'),
    (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'A'),
    (SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie WHERE code = 'MENSUEL'),
    0.00,
    '2003-02-10'
);
INSERT INTO workflow_rh (
    id_employe, id_type_evenement_rh, id_statut_workflow, 
    id_contrat_cible, id_affectation_cible, 
    date_demande, date_effet, motif, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '51'),
    (SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'EMBAUCHE_INITIALE'),
    (SELECT id_statut_workflow FROM ref_statut_workflow WHERE code = 'VALIDE'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-51-01'),
    (SELECT id_affectation FROM employe_affectation_carriere WHERE id_contrat = (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-51-01') AND id_motif_mouvement = (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE') LIMIT 1),
    '2003-02-10', '2003-02-10', 'Reprise des données existantes (Embauche Initiale)', 'Migration historique'
);
INSERT INTO mouvement_solde_conge (
    id_employe, type_mouvement, nb_jours, date_mouvement, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '51'), 'ACQUISITION', 15.0, '2003-02-10', 'Solde initial repris'
);

INSERT INTO contrat_travail (
    id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_contrat, 
    date_debut, date_signature, numero_contrat
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '55'), 
    (SELECT id_filiale FROM ref_filiale WHERE code_filiale = 'GLX'), 
    (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDI'), 
    (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT'), 
    (SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'ACTIF'),
    '2003-06-16', '2003-06-16', 'CT-55-01'
) ON CONFLICT (numero_contrat) DO NOTHING;
INSERT INTO employe_affectation_carriere (
    id_employe, id_contrat, id_statut_affectation, id_motif_mouvement, 
    id_site, id_departement, id_service, id_fonction, 
    id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '55'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-55-01'),
    (SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = 'ACTIVE'),
    (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'),
    (SELECT id_site FROM ref_site WHERE code_site = 'TNR'),
    (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG'),
    (SELECT id_service FROM ref_service WHERE code_service = 'OPE' AND id_departement = (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG')),
    (SELECT id_fonction FROM ref_fonction WHERE code_fonction = 'OPE_MGR'),
    (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'CADRE'),
    (SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie WHERE code = 'MENSUEL'),
    0.00,
    '2003-06-16'
);
INSERT INTO workflow_rh (
    id_employe, id_type_evenement_rh, id_statut_workflow, 
    id_contrat_cible, id_affectation_cible, 
    date_demande, date_effet, motif, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '55'),
    (SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'EMBAUCHE_INITIALE'),
    (SELECT id_statut_workflow FROM ref_statut_workflow WHERE code = 'VALIDE'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-55-01'),
    (SELECT id_affectation FROM employe_affectation_carriere WHERE id_contrat = (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-55-01') AND id_motif_mouvement = (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE') LIMIT 1),
    '2003-06-16', '2003-06-16', 'Reprise des données existantes (Embauche Initiale)', 'Migration historique'
);
INSERT INTO mouvement_solde_conge (
    id_employe, type_mouvement, nb_jours, date_mouvement, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '55'), 'ACQUISITION', 15.0, '2003-06-16', 'Solde initial repris'
);

INSERT INTO contrat_travail (
    id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_contrat, 
    date_debut, date_signature, numero_contrat
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '61'), 
    (SELECT id_filiale FROM ref_filiale WHERE code_filiale = 'GLX'), 
    (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDI'), 
    (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT'), 
    (SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'ACTIF'),
    '2003-08-01', '2003-08-01', 'CT-61-01'
) ON CONFLICT (numero_contrat) DO NOTHING;
INSERT INTO employe_affectation_carriere (
    id_employe, id_contrat, id_statut_affectation, id_motif_mouvement, 
    id_site, id_departement, id_service, id_fonction, 
    id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '61'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-61-01'),
    (SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = 'ACTIVE'),
    (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'),
    (SELECT id_site FROM ref_site WHERE code_site = 'TMM'),
    (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG'),
    (SELECT id_service FROM ref_service WHERE code_service = 'OPE' AND id_departement = (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG')),
    (SELECT id_fonction FROM ref_fonction WHERE code_fonction = 'AGENT_B_D'),
    (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'A'),
    (SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie WHERE code = 'MENSUEL'),
    0.00,
    '2003-08-01'
);
INSERT INTO workflow_rh (
    id_employe, id_type_evenement_rh, id_statut_workflow, 
    id_contrat_cible, id_affectation_cible, 
    date_demande, date_effet, motif, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '61'),
    (SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'EMBAUCHE_INITIALE'),
    (SELECT id_statut_workflow FROM ref_statut_workflow WHERE code = 'VALIDE'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-61-01'),
    (SELECT id_affectation FROM employe_affectation_carriere WHERE id_contrat = (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-61-01') AND id_motif_mouvement = (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE') LIMIT 1),
    '2003-08-01', '2003-08-01', 'Reprise des données existantes (Embauche Initiale)', 'Migration historique'
);
INSERT INTO mouvement_solde_conge (
    id_employe, type_mouvement, nb_jours, date_mouvement, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '61'), 'ACQUISITION', 15.0, '2003-08-01', 'Solde initial repris'
);

INSERT INTO contrat_travail (
    id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_contrat, 
    date_debut, date_signature, numero_contrat
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '135'), 
    (SELECT id_filiale FROM ref_filiale WHERE code_filiale = 'GLX'), 
    (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDI'), 
    (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT'), 
    (SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'ACTIF'),
    '2011-12-26', '2011-12-26', 'CT-135-01'
) ON CONFLICT (numero_contrat) DO NOTHING;
INSERT INTO employe_affectation_carriere (
    id_employe, id_contrat, id_statut_affectation, id_motif_mouvement, 
    id_site, id_departement, id_service, id_fonction, 
    id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '135'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-135-01'),
    (SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = 'ACTIVE'),
    (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'),
    (SELECT id_site FROM ref_site WHERE code_site = 'TNR'),
    (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG'),
    (SELECT id_service FROM ref_service WHERE code_service = 'OPE' AND id_departement = (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG')),
    (SELECT id_fonction FROM ref_fonction WHERE code_fonction = 'CHEF_DECLARANT'),
    (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'CADRE'),
    (SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie WHERE code = 'MENSUEL'),
    0.00,
    '2011-12-26'
);
INSERT INTO workflow_rh (
    id_employe, id_type_evenement_rh, id_statut_workflow, 
    id_contrat_cible, id_affectation_cible, 
    date_demande, date_effet, motif, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '135'),
    (SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'EMBAUCHE_INITIALE'),
    (SELECT id_statut_workflow FROM ref_statut_workflow WHERE code = 'VALIDE'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-135-01'),
    (SELECT id_affectation FROM employe_affectation_carriere WHERE id_contrat = (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-135-01') AND id_motif_mouvement = (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE') LIMIT 1),
    '2011-12-26', '2011-12-26', 'Reprise des données existantes (Embauche Initiale)', 'Migration historique'
);
INSERT INTO mouvement_solde_conge (
    id_employe, type_mouvement, nb_jours, date_mouvement, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '135'), 'ACQUISITION', 15.0, '2011-12-26', 'Solde initial repris'
);

INSERT INTO contrat_travail (
    id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_contrat, 
    date_debut, date_signature, numero_contrat
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '156'), 
    (SELECT id_filiale FROM ref_filiale WHERE code_filiale = 'GLX'), 
    (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDI'), 
    (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT'), 
    (SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'ACTIF'),
    '2013-03-26', '2013-03-26', 'CT-156-01'
) ON CONFLICT (numero_contrat) DO NOTHING;
INSERT INTO employe_affectation_carriere (
    id_employe, id_contrat, id_statut_affectation, id_motif_mouvement, 
    id_site, id_departement, id_service, id_fonction, 
    id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '156'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-156-01'),
    (SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = 'ACTIVE'),
    (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'),
    (SELECT id_site FROM ref_site WHERE code_site = 'TNR'),
    (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG'),
    (SELECT id_service FROM ref_service WHERE code_service = 'ADM' AND id_departement = (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG')),
    (SELECT id_fonction FROM ref_fonction WHERE code_fonction = 'AGENT_TOUT_FAIRE'),
    (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'A'),
    (SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie WHERE code = 'MENSUEL'),
    0.00,
    '2013-03-26'
);
INSERT INTO workflow_rh (
    id_employe, id_type_evenement_rh, id_statut_workflow, 
    id_contrat_cible, id_affectation_cible, 
    date_demande, date_effet, motif, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '156'),
    (SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'EMBAUCHE_INITIALE'),
    (SELECT id_statut_workflow FROM ref_statut_workflow WHERE code = 'VALIDE'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-156-01'),
    (SELECT id_affectation FROM employe_affectation_carriere WHERE id_contrat = (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-156-01') AND id_motif_mouvement = (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE') LIMIT 1),
    '2013-03-26', '2013-03-26', 'Reprise des données existantes (Embauche Initiale)', 'Migration historique'
);
INSERT INTO mouvement_solde_conge (
    id_employe, type_mouvement, nb_jours, date_mouvement, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '156'), 'ACQUISITION', 15.0, '2013-03-26', 'Solde initial repris'
);

INSERT INTO contrat_travail (
    id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_contrat, 
    date_debut, date_signature, numero_contrat
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '157'), 
    (SELECT id_filiale FROM ref_filiale WHERE code_filiale = 'GLX'), 
    (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDI'), 
    (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT'), 
    (SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'ACTIF'),
    '2013-03-26', '2013-03-26', 'CT-157-01'
) ON CONFLICT (numero_contrat) DO NOTHING;
INSERT INTO employe_affectation_carriere (
    id_employe, id_contrat, id_statut_affectation, id_motif_mouvement, 
    id_site, id_departement, id_service, id_fonction, 
    id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '157'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-157-01'),
    (SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = 'ACTIVE'),
    (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'),
    (SELECT id_site FROM ref_site WHERE code_site = 'TNR'),
    (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG'),
    (SELECT id_service FROM ref_service WHERE code_service = 'ADM' AND id_departement = (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG')),
    (SELECT id_fonction FROM ref_fonction WHERE code_fonction = 'AGENT_TOUT_FAIRE'),
    (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'A'),
    (SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie WHERE code = 'MENSUEL'),
    0.00,
    '2013-03-26'
);
INSERT INTO workflow_rh (
    id_employe, id_type_evenement_rh, id_statut_workflow, 
    id_contrat_cible, id_affectation_cible, 
    date_demande, date_effet, motif, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '157'),
    (SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'EMBAUCHE_INITIALE'),
    (SELECT id_statut_workflow FROM ref_statut_workflow WHERE code = 'VALIDE'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-157-01'),
    (SELECT id_affectation FROM employe_affectation_carriere WHERE id_contrat = (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-157-01') AND id_motif_mouvement = (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE') LIMIT 1),
    '2013-03-26', '2013-03-26', 'Reprise des données existantes (Embauche Initiale)', 'Migration historique'
);
INSERT INTO mouvement_solde_conge (
    id_employe, type_mouvement, nb_jours, date_mouvement, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '157'), 'ACQUISITION', 15.0, '2013-03-26', 'Solde initial repris'
);

INSERT INTO contrat_travail (
    id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_contrat, 
    date_debut, date_signature, numero_contrat
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '169'), 
    (SELECT id_filiale FROM ref_filiale WHERE code_filiale = 'GLX'), 
    (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDI'), 
    (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT'), 
    (SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'ACTIF'),
    '2015-03-31', '2015-03-31', 'CT-169-01'
) ON CONFLICT (numero_contrat) DO NOTHING;
INSERT INTO employe_affectation_carriere (
    id_employe, id_contrat, id_statut_affectation, id_motif_mouvement, 
    id_site, id_departement, id_service, id_fonction, 
    id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '169'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-169-01'),
    (SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = 'ACTIVE'),
    (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'),
    (SELECT id_site FROM ref_site WHERE code_site = 'TMM'),
    (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG'),
    (SELECT id_service FROM ref_service WHERE code_service = 'OPE' AND id_departement = (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG')),
    (SELECT id_fonction FROM ref_fonction WHERE code_fonction = 'RESP_TOAMASINA'),
    (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'CADRE'),
    (SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie WHERE code = 'MENSUEL'),
    0.00,
    '2015-03-31'
);
INSERT INTO workflow_rh (
    id_employe, id_type_evenement_rh, id_statut_workflow, 
    id_contrat_cible, id_affectation_cible, 
    date_demande, date_effet, motif, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '169'),
    (SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'EMBAUCHE_INITIALE'),
    (SELECT id_statut_workflow FROM ref_statut_workflow WHERE code = 'VALIDE'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-169-01'),
    (SELECT id_affectation FROM employe_affectation_carriere WHERE id_contrat = (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-169-01') AND id_motif_mouvement = (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE') LIMIT 1),
    '2015-03-31', '2015-03-31', 'Reprise des données existantes (Embauche Initiale)', 'Migration historique'
);
INSERT INTO mouvement_solde_conge (
    id_employe, type_mouvement, nb_jours, date_mouvement, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '169'), 'ACQUISITION', 15.0, '2015-03-31', 'Solde initial repris'
);

INSERT INTO contrat_travail (
    id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_contrat, 
    date_debut, date_signature, numero_contrat
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '176'), 
    (SELECT id_filiale FROM ref_filiale WHERE code_filiale = 'GLX'), 
    (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDI'), 
    (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT'), 
    (SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'ACTIF'),
    '2015-11-03', '2015-11-03', 'CT-176-01'
) ON CONFLICT (numero_contrat) DO NOTHING;
INSERT INTO employe_affectation_carriere (
    id_employe, id_contrat, id_statut_affectation, id_motif_mouvement, 
    id_site, id_departement, id_service, id_fonction, 
    id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '176'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-176-01'),
    (SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = 'ACTIVE'),
    (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'),
    (SELECT id_site FROM ref_site WHERE code_site = 'TNR'),
    (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG'),
    (SELECT id_service FROM ref_service WHERE code_service = 'ADM' AND id_departement = (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG')),
    (SELECT id_fonction FROM ref_fonction WHERE code_fonction = 'ASSIST_DIR'),
    (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'CADRE'),
    (SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie WHERE code = 'MENSUEL'),
    0.00,
    '2015-11-03'
);
INSERT INTO workflow_rh (
    id_employe, id_type_evenement_rh, id_statut_workflow, 
    id_contrat_cible, id_affectation_cible, 
    date_demande, date_effet, motif, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '176'),
    (SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'EMBAUCHE_INITIALE'),
    (SELECT id_statut_workflow FROM ref_statut_workflow WHERE code = 'VALIDE'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-176-01'),
    (SELECT id_affectation FROM employe_affectation_carriere WHERE id_contrat = (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-176-01') AND id_motif_mouvement = (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE') LIMIT 1),
    '2015-11-03', '2015-11-03', 'Reprise des données existantes (Embauche Initiale)', 'Migration historique'
);
INSERT INTO mouvement_solde_conge (
    id_employe, type_mouvement, nb_jours, date_mouvement, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '176'), 'ACQUISITION', 15.0, '2015-11-03', 'Solde initial repris'
);

INSERT INTO contrat_travail (
    id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_contrat, 
    date_debut, date_signature, numero_contrat
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '194'), 
    (SELECT id_filiale FROM ref_filiale WHERE code_filiale = 'GLX'), 
    (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDI'), 
    (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT'), 
    (SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'ACTIF'),
    '2018-07-04', '2018-07-04', 'CT-194-01'
) ON CONFLICT (numero_contrat) DO NOTHING;
INSERT INTO employe_affectation_carriere (
    id_employe, id_contrat, id_statut_affectation, id_motif_mouvement, 
    id_site, id_departement, id_service, id_fonction, 
    id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '194'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-194-01'),
    (SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = 'ACTIVE'),
    (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'),
    (SELECT id_site FROM ref_site WHERE code_site = 'TNR'),
    (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG'),
    (SELECT id_service FROM ref_service WHERE code_service = 'ADM' AND id_departement = (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG')),
    (SELECT id_fonction FROM ref_fonction WHERE code_fonction = 'ASSIST_ADMIN'),
    (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'A'),
    (SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie WHERE code = 'MENSUEL'),
    0.00,
    '2018-07-04'
);
INSERT INTO workflow_rh (
    id_employe, id_type_evenement_rh, id_statut_workflow, 
    id_contrat_cible, id_affectation_cible, 
    date_demande, date_effet, motif, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '194'),
    (SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'EMBAUCHE_INITIALE'),
    (SELECT id_statut_workflow FROM ref_statut_workflow WHERE code = 'VALIDE'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-194-01'),
    (SELECT id_affectation FROM employe_affectation_carriere WHERE id_contrat = (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-194-01') AND id_motif_mouvement = (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE') LIMIT 1),
    '2018-07-04', '2018-07-04', 'Reprise des données existantes (Embauche Initiale)', 'Migration historique'
);
INSERT INTO mouvement_solde_conge (
    id_employe, type_mouvement, nb_jours, date_mouvement, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '194'), 'ACQUISITION', 15.0, '2018-07-04', 'Solde initial repris'
);

INSERT INTO contrat_travail (
    id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_contrat, 
    date_debut, date_signature, numero_contrat
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '204'), 
    (SELECT id_filiale FROM ref_filiale WHERE code_filiale = 'GLX'), 
    (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDI'), 
    (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT'), 
    (SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'ACTIF'),
    '2019-08-26', '2019-08-26', 'CT-204-01'
) ON CONFLICT (numero_contrat) DO NOTHING;
INSERT INTO employe_affectation_carriere (
    id_employe, id_contrat, id_statut_affectation, id_motif_mouvement, 
    id_site, id_departement, id_service, id_fonction, 
    id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '204'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-204-01'),
    (SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = 'ACTIVE'),
    (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'),
    (SELECT id_site FROM ref_site WHERE code_site = 'TMM'),
    (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG'),
    (SELECT id_service FROM ref_service WHERE code_service = 'OPE' AND id_departement = (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG')),
    (SELECT id_fonction FROM ref_fonction WHERE code_fonction = 'PASSEUR_PIECES'),
    (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'A'),
    (SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie WHERE code = 'MENSUEL'),
    0.00,
    '2019-08-26'
);
INSERT INTO workflow_rh (
    id_employe, id_type_evenement_rh, id_statut_workflow, 
    id_contrat_cible, id_affectation_cible, 
    date_demande, date_effet, motif, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '204'),
    (SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'EMBAUCHE_INITIALE'),
    (SELECT id_statut_workflow FROM ref_statut_workflow WHERE code = 'VALIDE'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-204-01'),
    (SELECT id_affectation FROM employe_affectation_carriere WHERE id_contrat = (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-204-01') AND id_motif_mouvement = (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE') LIMIT 1),
    '2019-08-26', '2019-08-26', 'Reprise des données existantes (Embauche Initiale)', 'Migration historique'
);
INSERT INTO mouvement_solde_conge (
    id_employe, type_mouvement, nb_jours, date_mouvement, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '204'), 'ACQUISITION', 15.0, '2019-08-26', 'Solde initial repris'
);

INSERT INTO contrat_travail (
    id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_contrat, 
    date_debut, date_signature, numero_contrat
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '213'), 
    (SELECT id_filiale FROM ref_filiale WHERE code_filiale = 'GLX'), 
    (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDI'), 
    (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT'), 
    (SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'ACTIF'),
    '2017-07-26', '2017-07-26', 'CT-213-01'
) ON CONFLICT (numero_contrat) DO NOTHING;
INSERT INTO employe_affectation_carriere (
    id_employe, id_contrat, id_statut_affectation, id_motif_mouvement, 
    id_site, id_departement, id_service, id_fonction, 
    id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '213'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-213-01'),
    (SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = 'ACTIVE'),
    (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'),
    (SELECT id_site FROM ref_site WHERE code_site = 'TNR'),
    (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG'),
    (SELECT id_service FROM ref_service WHERE code_service = 'ADM' AND id_departement = (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG')),
    (SELECT id_fonction FROM ref_fonction WHERE code_fonction = 'AGENT_SURFACE'),
    (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'A'),
    (SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie WHERE code = 'MENSUEL'),
    0.00,
    '2017-07-26'
);
INSERT INTO workflow_rh (
    id_employe, id_type_evenement_rh, id_statut_workflow, 
    id_contrat_cible, id_affectation_cible, 
    date_demande, date_effet, motif, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '213'),
    (SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'EMBAUCHE_INITIALE'),
    (SELECT id_statut_workflow FROM ref_statut_workflow WHERE code = 'VALIDE'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-213-01'),
    (SELECT id_affectation FROM employe_affectation_carriere WHERE id_contrat = (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-213-01') AND id_motif_mouvement = (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE') LIMIT 1),
    '2017-07-26', '2017-07-26', 'Reprise des données existantes (Embauche Initiale)', 'Migration historique'
);
INSERT INTO mouvement_solde_conge (
    id_employe, type_mouvement, nb_jours, date_mouvement, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '213'), 'ACQUISITION', 15.0, '2017-07-26', 'Solde initial repris'
);

INSERT INTO contrat_travail (
    id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_contrat, 
    date_debut, date_signature, numero_contrat
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '217'), 
    (SELECT id_filiale FROM ref_filiale WHERE code_filiale = 'GLX'), 
    (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDI'), 
    (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT'), 
    (SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'ACTIF'),
    '2021-04-26', '2021-04-26', 'CT-217-01'
) ON CONFLICT (numero_contrat) DO NOTHING;
INSERT INTO employe_affectation_carriere (
    id_employe, id_contrat, id_statut_affectation, id_motif_mouvement, 
    id_site, id_departement, id_service, id_fonction, 
    id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '217'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-217-01'),
    (SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = 'ACTIVE'),
    (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'),
    (SELECT id_site FROM ref_site WHERE code_site = 'TNR'),
    (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG'),
    (SELECT id_service FROM ref_service WHERE code_service = 'OPE' AND id_departement = (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG')),
    (SELECT id_fonction FROM ref_fonction WHERE code_fonction = 'PASSEUR_PIECES'),
    (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'A'),
    (SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie WHERE code = 'MENSUEL'),
    0.00,
    '2021-04-26'
);
INSERT INTO workflow_rh (
    id_employe, id_type_evenement_rh, id_statut_workflow, 
    id_contrat_cible, id_affectation_cible, 
    date_demande, date_effet, motif, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '217'),
    (SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'EMBAUCHE_INITIALE'),
    (SELECT id_statut_workflow FROM ref_statut_workflow WHERE code = 'VALIDE'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-217-01'),
    (SELECT id_affectation FROM employe_affectation_carriere WHERE id_contrat = (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-217-01') AND id_motif_mouvement = (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE') LIMIT 1),
    '2021-04-26', '2021-04-26', 'Reprise des données existantes (Embauche Initiale)', 'Migration historique'
);
INSERT INTO mouvement_solde_conge (
    id_employe, type_mouvement, nb_jours, date_mouvement, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '217'), 'ACQUISITION', 15.0, '2021-04-26', 'Solde initial repris'
);

INSERT INTO contrat_travail (
    id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_contrat, 
    date_debut, date_signature, numero_contrat
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '219'), 
    (SELECT id_filiale FROM ref_filiale WHERE code_filiale = 'GLX'), 
    (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDI'), 
    (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT'), 
    (SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'ACTIF'),
    '2022-08-01', '2022-08-01', 'CT-219-01'
) ON CONFLICT (numero_contrat) DO NOTHING;
INSERT INTO employe_affectation_carriere (
    id_employe, id_contrat, id_statut_affectation, id_motif_mouvement, 
    id_site, id_departement, id_service, id_fonction, 
    id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '219'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-219-01'),
    (SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = 'ACTIVE'),
    (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'),
    (SELECT id_site FROM ref_site WHERE code_site = 'TNR'),
    (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG'),
    (SELECT id_service FROM ref_service WHERE code_service = 'OPE' AND id_departement = (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG')),
    (SELECT id_fonction FROM ref_fonction WHERE code_fonction = 'PASSEUR_PIECES'),
    (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'A'),
    (SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie WHERE code = 'MENSUEL'),
    0.00,
    '2022-08-01'
);
INSERT INTO workflow_rh (
    id_employe, id_type_evenement_rh, id_statut_workflow, 
    id_contrat_cible, id_affectation_cible, 
    date_demande, date_effet, motif, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '219'),
    (SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'EMBAUCHE_INITIALE'),
    (SELECT id_statut_workflow FROM ref_statut_workflow WHERE code = 'VALIDE'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-219-01'),
    (SELECT id_affectation FROM employe_affectation_carriere WHERE id_contrat = (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-219-01') AND id_motif_mouvement = (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE') LIMIT 1),
    '2022-08-01', '2022-08-01', 'Reprise des données existantes (Embauche Initiale)', 'Migration historique'
);
INSERT INTO mouvement_solde_conge (
    id_employe, type_mouvement, nb_jours, date_mouvement, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '219'), 'ACQUISITION', 15.0, '2022-08-01', 'Solde initial repris'
);

INSERT INTO contrat_travail (
    id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_contrat, 
    date_debut, date_signature, numero_contrat
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '223'), 
    (SELECT id_filiale FROM ref_filiale WHERE code_filiale = 'GLX'), 
    (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDI'), 
    (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT'), 
    (SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'ACTIF'),
    '2024-10-26', '2024-10-26', 'CT-223-01'
) ON CONFLICT (numero_contrat) DO NOTHING;
INSERT INTO employe_affectation_carriere (
    id_employe, id_contrat, id_statut_affectation, id_motif_mouvement, 
    id_site, id_departement, id_service, id_fonction, 
    id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '223'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-223-01'),
    (SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = 'ACTIVE'),
    (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'),
    (SELECT id_site FROM ref_site WHERE code_site = 'TMM'),
    (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG'),
    (SELECT id_service FROM ref_service WHERE code_service = 'OPE' AND id_departement = (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG')),
    (SELECT id_fonction FROM ref_fonction WHERE code_fonction = 'PASSEUR_PIECES'),
    (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'A'),
    (SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie WHERE code = 'MENSUEL'),
    0.00,
    '2024-10-26'
);
INSERT INTO workflow_rh (
    id_employe, id_type_evenement_rh, id_statut_workflow, 
    id_contrat_cible, id_affectation_cible, 
    date_demande, date_effet, motif, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '223'),
    (SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'EMBAUCHE_INITIALE'),
    (SELECT id_statut_workflow FROM ref_statut_workflow WHERE code = 'VALIDE'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-223-01'),
    (SELECT id_affectation FROM employe_affectation_carriere WHERE id_contrat = (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-223-01') AND id_motif_mouvement = (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE') LIMIT 1),
    '2024-10-26', '2024-10-26', 'Reprise des données existantes (Embauche Initiale)', 'Migration historique'
);
INSERT INTO mouvement_solde_conge (
    id_employe, type_mouvement, nb_jours, date_mouvement, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '223'), 'ACQUISITION', 15.0, '2024-10-26', 'Solde initial repris'
);

INSERT INTO contrat_travail (
    id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_contrat, 
    date_debut, date_signature, numero_contrat
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '224'), 
    (SELECT id_filiale FROM ref_filiale WHERE code_filiale = 'GLX'), 
    (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDI'), 
    (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT'), 
    (SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'ACTIF'),
    '2025-02-26', '2025-02-26', 'CT-224-01'
) ON CONFLICT (numero_contrat) DO NOTHING;
INSERT INTO employe_affectation_carriere (
    id_employe, id_contrat, id_statut_affectation, id_motif_mouvement, 
    id_site, id_departement, id_service, id_fonction, 
    id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '224'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-224-01'),
    (SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = 'ACTIVE'),
    (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'),
    (SELECT id_site FROM ref_site WHERE code_site = 'TMM'),
    (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG'),
    (SELECT id_service FROM ref_service WHERE code_service = 'ADM' AND id_departement = (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG')),
    (SELECT id_fonction FROM ref_fonction WHERE code_fonction = 'ASSIST_ADMIN'),
    (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'A'),
    (SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie WHERE code = 'MENSUEL'),
    0.00,
    '2025-02-26'
);
INSERT INTO workflow_rh (
    id_employe, id_type_evenement_rh, id_statut_workflow, 
    id_contrat_cible, id_affectation_cible, 
    date_demande, date_effet, motif, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '224'),
    (SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'EMBAUCHE_INITIALE'),
    (SELECT id_statut_workflow FROM ref_statut_workflow WHERE code = 'VALIDE'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-224-01'),
    (SELECT id_affectation FROM employe_affectation_carriere WHERE id_contrat = (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-224-01') AND id_motif_mouvement = (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE') LIMIT 1),
    '2025-02-26', '2025-02-26', 'Reprise des données existantes (Embauche Initiale)', 'Migration historique'
);
INSERT INTO mouvement_solde_conge (
    id_employe, type_mouvement, nb_jours, date_mouvement, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '224'), 'ACQUISITION', 15.0, '2025-02-26', 'Solde initial repris'
);

INSERT INTO contrat_travail (
    id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_contrat, 
    date_debut, date_signature, numero_contrat
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '225'), 
    (SELECT id_filiale FROM ref_filiale WHERE code_filiale = 'GLX'), 
    (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDI'), 
    (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT'), 
    (SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'ACTIF'),
    '2025-08-26', '2025-08-26', 'CT-225-01'
) ON CONFLICT (numero_contrat) DO NOTHING;
INSERT INTO employe_affectation_carriere (
    id_employe, id_contrat, id_statut_affectation, id_motif_mouvement, 
    id_site, id_departement, id_service, id_fonction, 
    id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '225'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-225-01'),
    (SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = 'ACTIVE'),
    (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'),
    (SELECT id_site FROM ref_site WHERE code_site = 'TNR'),
    (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG'),
    (SELECT id_service FROM ref_service WHERE code_service = 'ADM' AND id_departement = (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG')),
    (SELECT id_fonction FROM ref_fonction WHERE code_fonction = 'AIDE_COMPTABLE'),
    (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'A'),
    (SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie WHERE code = 'MENSUEL'),
    0.00,
    '2025-08-26'
);
INSERT INTO workflow_rh (
    id_employe, id_type_evenement_rh, id_statut_workflow, 
    id_contrat_cible, id_affectation_cible, 
    date_demande, date_effet, motif, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '225'),
    (SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'EMBAUCHE_INITIALE'),
    (SELECT id_statut_workflow FROM ref_statut_workflow WHERE code = 'VALIDE'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-225-01'),
    (SELECT id_affectation FROM employe_affectation_carriere WHERE id_contrat = (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-225-01') AND id_motif_mouvement = (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE') LIMIT 1),
    '2025-08-26', '2025-08-26', 'Reprise des données existantes (Embauche Initiale)', 'Migration historique'
);
INSERT INTO mouvement_solde_conge (
    id_employe, type_mouvement, nb_jours, date_mouvement, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '225'), 'ACQUISITION', 15.0, '2025-08-26', 'Solde initial repris'
);

INSERT INTO contrat_travail (
    id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_contrat, 
    date_debut, date_signature, numero_contrat
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '316'), 
    (SELECT id_filiale FROM ref_filiale WHERE code_filiale = 'GLX'), 
    (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDD'), 
    (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT'), 
    (SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'ACTIF'),
    '2026-06-03', '2026-06-03', 'CT-316-01'
) ON CONFLICT (numero_contrat) DO NOTHING;
INSERT INTO employe_affectation_carriere (
    id_employe, id_contrat, id_statut_affectation, id_motif_mouvement, 
    id_site, id_departement, id_service, id_fonction, 
    id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '316'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-316-01'),
    (SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = 'ACTIVE'),
    (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'),
    (SELECT id_site FROM ref_site WHERE code_site = 'TNR'),
    (SELECT id_departement FROM ref_departement WHERE code_departement = 'LMD'),
    (SELECT id_service FROM ref_service WHERE code_service = 'ADM' AND id_departement = (SELECT id_departement FROM ref_departement WHERE code_departement = 'LMD')),
    (SELECT id_fonction FROM ref_fonction WHERE code_fonction = 'MANUTENTIONNAIRE'),
    (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'A'),
    (SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie WHERE code = 'JOURNALIER'),
    0.00,
    '2026-06-03'
);
INSERT INTO workflow_rh (
    id_employe, id_type_evenement_rh, id_statut_workflow, 
    id_contrat_cible, id_affectation_cible, 
    date_demande, date_effet, motif, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '316'),
    (SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'EMBAUCHE_INITIALE'),
    (SELECT id_statut_workflow FROM ref_statut_workflow WHERE code = 'VALIDE'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-316-01'),
    (SELECT id_affectation FROM employe_affectation_carriere WHERE id_contrat = (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-316-01') AND id_motif_mouvement = (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE') LIMIT 1),
    '2026-06-03', '2026-06-03', 'Reprise des données existantes (Embauche Initiale)', 'Migration historique'
);
INSERT INTO mouvement_solde_conge (
    id_employe, type_mouvement, nb_jours, date_mouvement, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '316'), 'ACQUISITION', 0.0, '2026-06-03', 'Solde initial repris'
);

INSERT INTO contrat_travail (
    id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_contrat, 
    date_debut, date_signature, numero_contrat
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '317'), 
    (SELECT id_filiale FROM ref_filiale WHERE code_filiale = 'GLX'), 
    (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDD'), 
    (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT'), 
    (SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'ACTIF'),
    '2026-06-03', '2026-06-03', 'CT-317-01'
) ON CONFLICT (numero_contrat) DO NOTHING;
INSERT INTO employe_affectation_carriere (
    id_employe, id_contrat, id_statut_affectation, id_motif_mouvement, 
    id_site, id_departement, id_service, id_fonction, 
    id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '317'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-317-01'),
    (SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = 'ACTIVE'),
    (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'),
    (SELECT id_site FROM ref_site WHERE code_site = 'TNR'),
    (SELECT id_departement FROM ref_departement WHERE code_departement = 'LMD'),
    (SELECT id_service FROM ref_service WHERE code_service = 'ADM' AND id_departement = (SELECT id_departement FROM ref_departement WHERE code_departement = 'LMD')),
    (SELECT id_fonction FROM ref_fonction WHERE code_fonction = 'MANUTENTIONNAIRE'),
    (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'A'),
    (SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie WHERE code = 'JOURNALIER'),
    0.00,
    '2026-06-03'
);
INSERT INTO workflow_rh (
    id_employe, id_type_evenement_rh, id_statut_workflow, 
    id_contrat_cible, id_affectation_cible, 
    date_demande, date_effet, motif, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '317'),
    (SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'EMBAUCHE_INITIALE'),
    (SELECT id_statut_workflow FROM ref_statut_workflow WHERE code = 'VALIDE'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-317-01'),
    (SELECT id_affectation FROM employe_affectation_carriere WHERE id_contrat = (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-317-01') AND id_motif_mouvement = (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE') LIMIT 1),
    '2026-06-03', '2026-06-03', 'Reprise des données existantes (Embauche Initiale)', 'Migration historique'
);
INSERT INTO mouvement_solde_conge (
    id_employe, type_mouvement, nb_jours, date_mouvement, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '317'), 'ACQUISITION', 0.0, '2026-06-03', 'Solde initial repris'
);

INSERT INTO contrat_travail (
    id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_contrat, 
    date_debut, date_signature, numero_contrat
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '318'), 
    (SELECT id_filiale FROM ref_filiale WHERE code_filiale = 'GLX'), 
    (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDD'), 
    (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT'), 
    (SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'ACTIF'),
    '2026-06-03', '2026-06-03', 'CT-318-01'
) ON CONFLICT (numero_contrat) DO NOTHING;
INSERT INTO employe_affectation_carriere (
    id_employe, id_contrat, id_statut_affectation, id_motif_mouvement, 
    id_site, id_departement, id_service, id_fonction, 
    id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '318'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-318-01'),
    (SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = 'ACTIVE'),
    (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'),
    (SELECT id_site FROM ref_site WHERE code_site = 'TNR'),
    (SELECT id_departement FROM ref_departement WHERE code_departement = 'LMD'),
    (SELECT id_service FROM ref_service WHERE code_service = 'ADM' AND id_departement = (SELECT id_departement FROM ref_departement WHERE code_departement = 'LMD')),
    (SELECT id_fonction FROM ref_fonction WHERE code_fonction = 'MANUTENTIONNAIRE'),
    (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'A'),
    (SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie WHERE code = 'JOURNALIER'),
    0.00,
    '2026-06-03'
);
INSERT INTO workflow_rh (
    id_employe, id_type_evenement_rh, id_statut_workflow, 
    id_contrat_cible, id_affectation_cible, 
    date_demande, date_effet, motif, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '318'),
    (SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'EMBAUCHE_INITIALE'),
    (SELECT id_statut_workflow FROM ref_statut_workflow WHERE code = 'VALIDE'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-318-01'),
    (SELECT id_affectation FROM employe_affectation_carriere WHERE id_contrat = (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-318-01') AND id_motif_mouvement = (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE') LIMIT 1),
    '2026-06-03', '2026-06-03', 'Reprise des données existantes (Embauche Initiale)', 'Migration historique'
);
INSERT INTO mouvement_solde_conge (
    id_employe, type_mouvement, nb_jours, date_mouvement, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '318'), 'ACQUISITION', 0.0, '2026-06-03', 'Solde initial repris'
);

INSERT INTO contrat_travail (
    id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_contrat, 
    date_debut, date_signature, numero_contrat
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '319'), 
    (SELECT id_filiale FROM ref_filiale WHERE code_filiale = 'GLX'), 
    (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDD'), 
    (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT'), 
    (SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'ACTIF'),
    '2026-06-03', '2026-06-03', 'CT-319-01'
) ON CONFLICT (numero_contrat) DO NOTHING;
INSERT INTO employe_affectation_carriere (
    id_employe, id_contrat, id_statut_affectation, id_motif_mouvement, 
    id_site, id_departement, id_service, id_fonction, 
    id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '319'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-319-01'),
    (SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = 'ACTIVE'),
    (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'),
    (SELECT id_site FROM ref_site WHERE code_site = 'TNR'),
    (SELECT id_departement FROM ref_departement WHERE code_departement = 'LMD'),
    (SELECT id_service FROM ref_service WHERE code_service = 'ADM' AND id_departement = (SELECT id_departement FROM ref_departement WHERE code_departement = 'LMD')),
    (SELECT id_fonction FROM ref_fonction WHERE code_fonction = 'MANUTENTIONNAIRE'),
    (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'A'),
    (SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie WHERE code = 'JOURNALIER'),
    0.00,
    '2026-06-03'
);
INSERT INTO workflow_rh (
    id_employe, id_type_evenement_rh, id_statut_workflow, 
    id_contrat_cible, id_affectation_cible, 
    date_demande, date_effet, motif, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '319'),
    (SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'EMBAUCHE_INITIALE'),
    (SELECT id_statut_workflow FROM ref_statut_workflow WHERE code = 'VALIDE'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-319-01'),
    (SELECT id_affectation FROM employe_affectation_carriere WHERE id_contrat = (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-319-01') AND id_motif_mouvement = (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE') LIMIT 1),
    '2026-06-03', '2026-06-03', 'Reprise des données existantes (Embauche Initiale)', 'Migration historique'
);
INSERT INTO mouvement_solde_conge (
    id_employe, type_mouvement, nb_jours, date_mouvement, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '319'), 'ACQUISITION', 0.0, '2026-06-03', 'Solde initial repris'
);

INSERT INTO contrat_travail (
    id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_contrat, 
    date_debut, date_signature, numero_contrat
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '320'), 
    (SELECT id_filiale FROM ref_filiale WHERE code_filiale = 'GLX'), 
    (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDD'), 
    (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT'), 
    (SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'ACTIF'),
    '2026-06-03', '2026-06-03', 'CT-320-01'
) ON CONFLICT (numero_contrat) DO NOTHING;
INSERT INTO employe_affectation_carriere (
    id_employe, id_contrat, id_statut_affectation, id_motif_mouvement, 
    id_site, id_departement, id_service, id_fonction, 
    id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '320'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-320-01'),
    (SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = 'ACTIVE'),
    (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'),
    (SELECT id_site FROM ref_site WHERE code_site = 'TNR'),
    (SELECT id_departement FROM ref_departement WHERE code_departement = 'LMD'),
    (SELECT id_service FROM ref_service WHERE code_service = 'ADM' AND id_departement = (SELECT id_departement FROM ref_departement WHERE code_departement = 'LMD')),
    (SELECT id_fonction FROM ref_fonction WHERE code_fonction = 'MANUTENTIONNAIRE'),
    (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'A'),
    (SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie WHERE code = 'JOURNALIER'),
    0.00,
    '2026-06-03'
);
INSERT INTO workflow_rh (
    id_employe, id_type_evenement_rh, id_statut_workflow, 
    id_contrat_cible, id_affectation_cible, 
    date_demande, date_effet, motif, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '320'),
    (SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'EMBAUCHE_INITIALE'),
    (SELECT id_statut_workflow FROM ref_statut_workflow WHERE code = 'VALIDE'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-320-01'),
    (SELECT id_affectation FROM employe_affectation_carriere WHERE id_contrat = (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-320-01') AND id_motif_mouvement = (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE') LIMIT 1),
    '2026-06-03', '2026-06-03', 'Reprise des données existantes (Embauche Initiale)', 'Migration historique'
);
INSERT INTO mouvement_solde_conge (
    id_employe, type_mouvement, nb_jours, date_mouvement, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '320'), 'ACQUISITION', 0.0, '2026-06-03', 'Solde initial repris'
);

INSERT INTO contrat_travail (
    id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_contrat, 
    date_debut, date_signature, numero_contrat
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '321'), 
    (SELECT id_filiale FROM ref_filiale WHERE code_filiale = 'GLX'), 
    (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDD'), 
    (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT'), 
    (SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'ACTIF'),
    '2026-06-03', '2026-06-03', 'CT-321-01'
) ON CONFLICT (numero_contrat) DO NOTHING;
INSERT INTO employe_affectation_carriere (
    id_employe, id_contrat, id_statut_affectation, id_motif_mouvement, 
    id_site, id_departement, id_service, id_fonction, 
    id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '321'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-321-01'),
    (SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = 'ACTIVE'),
    (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'),
    (SELECT id_site FROM ref_site WHERE code_site = 'TNR'),
    (SELECT id_departement FROM ref_departement WHERE code_departement = 'LMD'),
    (SELECT id_service FROM ref_service WHERE code_service = 'ADM' AND id_departement = (SELECT id_departement FROM ref_departement WHERE code_departement = 'LMD')),
    (SELECT id_fonction FROM ref_fonction WHERE code_fonction = 'MANUTENTIONNAIRE'),
    (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'A'),
    (SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie WHERE code = 'JOURNALIER'),
    0.00,
    '2026-06-03'
);
INSERT INTO workflow_rh (
    id_employe, id_type_evenement_rh, id_statut_workflow, 
    id_contrat_cible, id_affectation_cible, 
    date_demande, date_effet, motif, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '321'),
    (SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'EMBAUCHE_INITIALE'),
    (SELECT id_statut_workflow FROM ref_statut_workflow WHERE code = 'VALIDE'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-321-01'),
    (SELECT id_affectation FROM employe_affectation_carriere WHERE id_contrat = (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-321-01') AND id_motif_mouvement = (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE') LIMIT 1),
    '2026-06-03', '2026-06-03', 'Reprise des données existantes (Embauche Initiale)', 'Migration historique'
);
INSERT INTO mouvement_solde_conge (
    id_employe, type_mouvement, nb_jours, date_mouvement, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '321'), 'ACQUISITION', 0.0, '2026-06-03', 'Solde initial repris'
);

INSERT INTO contrat_travail (
    id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_contrat, 
    date_debut, date_signature, numero_contrat
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '322'), 
    (SELECT id_filiale FROM ref_filiale WHERE code_filiale = 'GLX'), 
    (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDD'), 
    (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT'), 
    (SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'ACTIF'),
    '2026-06-03', '2026-06-03', 'CT-322-01'
) ON CONFLICT (numero_contrat) DO NOTHING;
INSERT INTO employe_affectation_carriere (
    id_employe, id_contrat, id_statut_affectation, id_motif_mouvement, 
    id_site, id_departement, id_service, id_fonction, 
    id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '322'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-322-01'),
    (SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = 'ACTIVE'),
    (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'),
    (SELECT id_site FROM ref_site WHERE code_site = 'TNR'),
    (SELECT id_departement FROM ref_departement WHERE code_departement = 'LMD'),
    (SELECT id_service FROM ref_service WHERE code_service = 'ADM' AND id_departement = (SELECT id_departement FROM ref_departement WHERE code_departement = 'LMD')),
    (SELECT id_fonction FROM ref_fonction WHERE code_fonction = 'MANUTENTIONNAIRE'),
    (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'A'),
    (SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie WHERE code = 'JOURNALIER'),
    0.00,
    '2026-06-03'
);
INSERT INTO workflow_rh (
    id_employe, id_type_evenement_rh, id_statut_workflow, 
    id_contrat_cible, id_affectation_cible, 
    date_demande, date_effet, motif, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '322'),
    (SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'EMBAUCHE_INITIALE'),
    (SELECT id_statut_workflow FROM ref_statut_workflow WHERE code = 'VALIDE'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-322-01'),
    (SELECT id_affectation FROM employe_affectation_carriere WHERE id_contrat = (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-322-01') AND id_motif_mouvement = (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE') LIMIT 1),
    '2026-06-03', '2026-06-03', 'Reprise des données existantes (Embauche Initiale)', 'Migration historique'
);
INSERT INTO mouvement_solde_conge (
    id_employe, type_mouvement, nb_jours, date_mouvement, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '322'), 'ACQUISITION', 0.0, '2026-06-03', 'Solde initial repris'
);

INSERT INTO contrat_travail (
    id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_contrat, 
    date_debut, date_signature, numero_contrat
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '323'), 
    (SELECT id_filiale FROM ref_filiale WHERE code_filiale = 'GLX'), 
    (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDD'), 
    (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT'), 
    (SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'ACTIF'),
    '2026-06-03', '2026-06-03', 'CT-323-01'
) ON CONFLICT (numero_contrat) DO NOTHING;
INSERT INTO employe_affectation_carriere (
    id_employe, id_contrat, id_statut_affectation, id_motif_mouvement, 
    id_site, id_departement, id_service, id_fonction, 
    id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '323'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-323-01'),
    (SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = 'ACTIVE'),
    (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'),
    (SELECT id_site FROM ref_site WHERE code_site = 'TNR'),
    (SELECT id_departement FROM ref_departement WHERE code_departement = 'LMD'),
    (SELECT id_service FROM ref_service WHERE code_service = 'ADM' AND id_departement = (SELECT id_departement FROM ref_departement WHERE code_departement = 'LMD')),
    (SELECT id_fonction FROM ref_fonction WHERE code_fonction = 'MANUTENTIONNAIRE'),
    (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'A'),
    (SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie WHERE code = 'JOURNALIER'),
    0.00,
    '2026-06-03'
);
INSERT INTO workflow_rh (
    id_employe, id_type_evenement_rh, id_statut_workflow, 
    id_contrat_cible, id_affectation_cible, 
    date_demande, date_effet, motif, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '323'),
    (SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'EMBAUCHE_INITIALE'),
    (SELECT id_statut_workflow FROM ref_statut_workflow WHERE code = 'VALIDE'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-323-01'),
    (SELECT id_affectation FROM employe_affectation_carriere WHERE id_contrat = (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-323-01') AND id_motif_mouvement = (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE') LIMIT 1),
    '2026-06-03', '2026-06-03', 'Reprise des données existantes (Embauche Initiale)', 'Migration historique'
);
INSERT INTO mouvement_solde_conge (
    id_employe, type_mouvement, nb_jours, date_mouvement, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '323'), 'ACQUISITION', 0.0, '2026-06-03', 'Solde initial repris'
);

INSERT INTO contrat_travail (
    id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_contrat, 
    date_debut, date_signature, numero_contrat
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '326'), 
    (SELECT id_filiale FROM ref_filiale WHERE code_filiale = 'GLX'), 
    (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDD'), 
    (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT'), 
    (SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'ACTIF'),
    '2026-04-26', '2026-04-26', 'CT-326-01'
) ON CONFLICT (numero_contrat) DO NOTHING;
INSERT INTO employe_affectation_carriere (
    id_employe, id_contrat, id_statut_affectation, id_motif_mouvement, 
    id_site, id_departement, id_service, id_fonction, 
    id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '326'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-326-01'),
    (SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = 'ACTIVE'),
    (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'),
    (SELECT id_site FROM ref_site WHERE code_site = 'TNR'),
    (SELECT id_departement FROM ref_departement WHERE code_departement = 'LMD'),
    (SELECT id_service FROM ref_service WHERE code_service = 'ADM' AND id_departement = (SELECT id_departement FROM ref_departement WHERE code_departement = 'LMD')),
    (SELECT id_fonction FROM ref_fonction WHERE code_fonction = 'AGENT_PLANIF'),
    (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'CADRE'),
    (SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie WHERE code = 'MENSUEL'),
    0.00,
    '2026-04-26'
);
INSERT INTO workflow_rh (
    id_employe, id_type_evenement_rh, id_statut_workflow, 
    id_contrat_cible, id_affectation_cible, 
    date_demande, date_effet, motif, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '326'),
    (SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'EMBAUCHE_INITIALE'),
    (SELECT id_statut_workflow FROM ref_statut_workflow WHERE code = 'VALIDE'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-326-01'),
    (SELECT id_affectation FROM employe_affectation_carriere WHERE id_contrat = (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-326-01') AND id_motif_mouvement = (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE') LIMIT 1),
    '2026-04-26', '2026-04-26', 'Reprise des données existantes (Embauche Initiale)', 'Migration historique'
);
INSERT INTO mouvement_solde_conge (
    id_employe, type_mouvement, nb_jours, date_mouvement, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '326'), 'ACQUISITION', 0.0, '2026-04-26', 'Solde initial repris'
);

INSERT INTO contrat_travail (
    id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_contrat, 
    date_debut, date_signature, numero_contrat
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '327'), 
    (SELECT id_filiale FROM ref_filiale WHERE code_filiale = 'GLX'), 
    (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDD'), 
    (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT'), 
    (SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'ACTIF'),
    '2026-05-26', '2026-05-26', 'CT-327-01'
) ON CONFLICT (numero_contrat) DO NOTHING;
INSERT INTO employe_affectation_carriere (
    id_employe, id_contrat, id_statut_affectation, id_motif_mouvement, 
    id_site, id_departement, id_service, id_fonction, 
    id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '327'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-327-01'),
    (SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = 'ACTIVE'),
    (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'),
    (SELECT id_site FROM ref_site WHERE code_site = 'TNR'),
    (SELECT id_departement FROM ref_departement WHERE code_departement = 'LMD'),
    (SELECT id_service FROM ref_service WHERE code_service = 'OPE' AND id_departement = (SELECT id_departement FROM ref_departement WHERE code_departement = 'LMD')),
    (SELECT id_fonction FROM ref_fonction WHERE code_fonction = 'CHEF_ZONE'),
    (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'CADRE'),
    (SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie WHERE code = 'MENSUEL'),
    0.00,
    '2026-05-26'
);
INSERT INTO workflow_rh (
    id_employe, id_type_evenement_rh, id_statut_workflow, 
    id_contrat_cible, id_affectation_cible, 
    date_demande, date_effet, motif, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '327'),
    (SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'EMBAUCHE_INITIALE'),
    (SELECT id_statut_workflow FROM ref_statut_workflow WHERE code = 'VALIDE'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-327-01'),
    (SELECT id_affectation FROM employe_affectation_carriere WHERE id_contrat = (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-327-01') AND id_motif_mouvement = (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE') LIMIT 1),
    '2026-05-26', '2026-05-26', 'Reprise des données existantes (Embauche Initiale)', 'Migration historique'
);
INSERT INTO mouvement_solde_conge (
    id_employe, type_mouvement, nb_jours, date_mouvement, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '327'), 'ACQUISITION', 0.0, '2026-05-26', 'Solde initial repris'
);

INSERT INTO contrat_travail (
    id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_contrat, 
    date_debut, date_signature, numero_contrat
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '328'), 
    (SELECT id_filiale FROM ref_filiale WHERE code_filiale = 'GLX'), 
    (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDD'), 
    (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT'), 
    (SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'ACTIF'),
    '2026-06-01', '2026-06-01', 'CT-328-01'
) ON CONFLICT (numero_contrat) DO NOTHING;
INSERT INTO employe_affectation_carriere (
    id_employe, id_contrat, id_statut_affectation, id_motif_mouvement, 
    id_site, id_departement, id_service, id_fonction, 
    id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '328'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-328-01'),
    (SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = 'ACTIVE'),
    (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'),
    (SELECT id_site FROM ref_site WHERE code_site = 'TNR'),
    (SELECT id_departement FROM ref_departement WHERE code_departement = 'LMD'),
    (SELECT id_service FROM ref_service WHERE code_service = 'ADM' AND id_departement = (SELECT id_departement FROM ref_departement WHERE code_departement = 'LMD')),
    (SELECT id_fonction FROM ref_fonction WHERE code_fonction = 'RESP_ENTREPOT'),
    (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'CADRE'),
    (SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie WHERE code = 'MENSUEL'),
    0.00,
    '2026-06-01'
);
INSERT INTO workflow_rh (
    id_employe, id_type_evenement_rh, id_statut_workflow, 
    id_contrat_cible, id_affectation_cible, 
    date_demande, date_effet, motif, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '328'),
    (SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'EMBAUCHE_INITIALE'),
    (SELECT id_statut_workflow FROM ref_statut_workflow WHERE code = 'VALIDE'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-328-01'),
    (SELECT id_affectation FROM employe_affectation_carriere WHERE id_contrat = (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-328-01') AND id_motif_mouvement = (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE') LIMIT 1),
    '2026-06-01', '2026-06-01', 'Reprise des données existantes (Embauche Initiale)', 'Migration historique'
);
INSERT INTO mouvement_solde_conge (
    id_employe, type_mouvement, nb_jours, date_mouvement, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '328'), 'ACQUISITION', 0.0, '2026-06-01', 'Solde initial repris'
);

INSERT INTO contrat_travail (
    id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_contrat, 
    date_debut, date_signature, numero_contrat
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '329'), 
    (SELECT id_filiale FROM ref_filiale WHERE code_filiale = 'GLX'), 
    (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDD'), 
    (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT'), 
    (SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'ACTIF'),
    '2026-06-03', '2026-06-03', 'CT-329-01'
) ON CONFLICT (numero_contrat) DO NOTHING;
INSERT INTO employe_affectation_carriere (
    id_employe, id_contrat, id_statut_affectation, id_motif_mouvement, 
    id_site, id_departement, id_service, id_fonction, 
    id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '329'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-329-01'),
    (SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = 'ACTIVE'),
    (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'),
    (SELECT id_site FROM ref_site WHERE code_site = 'TNR'),
    (SELECT id_departement FROM ref_departement WHERE code_departement = 'LMD'),
    (SELECT id_service FROM ref_service WHERE code_service = 'OPE' AND id_departement = (SELECT id_departement FROM ref_departement WHERE code_departement = 'LMD')),
    (SELECT id_fonction FROM ref_fonction WHERE code_fonction = 'CHEF_ZONE'),
    (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'CADRE'),
    (SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie WHERE code = 'MENSUEL'),
    0.00,
    '2026-06-03'
);
INSERT INTO workflow_rh (
    id_employe, id_type_evenement_rh, id_statut_workflow, 
    id_contrat_cible, id_affectation_cible, 
    date_demande, date_effet, motif, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '329'),
    (SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'EMBAUCHE_INITIALE'),
    (SELECT id_statut_workflow FROM ref_statut_workflow WHERE code = 'VALIDE'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-329-01'),
    (SELECT id_affectation FROM employe_affectation_carriere WHERE id_contrat = (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-329-01') AND id_motif_mouvement = (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE') LIMIT 1),
    '2026-06-03', '2026-06-03', 'Reprise des données existantes (Embauche Initiale)', 'Migration historique'
);
INSERT INTO mouvement_solde_conge (
    id_employe, type_mouvement, nb_jours, date_mouvement, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '329'), 'ACQUISITION', 0.0, '2026-06-03', 'Solde initial repris'
);

INSERT INTO contrat_travail (
    id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_contrat, 
    date_debut, date_signature, numero_contrat
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '330'), 
    (SELECT id_filiale FROM ref_filiale WHERE code_filiale = 'GLX'), 
    (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDD'), 
    (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT'), 
    (SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'ACTIF'),
    '2026-06-15', '2026-06-15', 'CT-330-01'
) ON CONFLICT (numero_contrat) DO NOTHING;
INSERT INTO employe_affectation_carriere (
    id_employe, id_contrat, id_statut_affectation, id_motif_mouvement, 
    id_site, id_departement, id_service, id_fonction, 
    id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '330'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-330-01'),
    (SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = 'ACTIVE'),
    (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'),
    (SELECT id_site FROM ref_site WHERE code_site = 'TNR'),
    (SELECT id_departement FROM ref_departement WHERE code_departement = 'LMD'),
    (SELECT id_service FROM ref_service WHERE code_service = 'OPE' AND id_departement = (SELECT id_departement FROM ref_departement WHERE code_departement = 'LMD')),
    (SELECT id_fonction FROM ref_fonction WHERE code_fonction = 'CHEF_ZONE'),
    (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'CADRE'),
    (SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie WHERE code = 'MENSUEL'),
    0.00,
    '2026-06-15'
);
INSERT INTO workflow_rh (
    id_employe, id_type_evenement_rh, id_statut_workflow, 
    id_contrat_cible, id_affectation_cible, 
    date_demande, date_effet, motif, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '330'),
    (SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'EMBAUCHE_INITIALE'),
    (SELECT id_statut_workflow FROM ref_statut_workflow WHERE code = 'VALIDE'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-330-01'),
    (SELECT id_affectation FROM employe_affectation_carriere WHERE id_contrat = (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-330-01') AND id_motif_mouvement = (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE') LIMIT 1),
    '2026-06-15', '2026-06-15', 'Reprise des données existantes (Embauche Initiale)', 'Migration historique'
);
INSERT INTO mouvement_solde_conge (
    id_employe, type_mouvement, nb_jours, date_mouvement, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '330'), 'ACQUISITION', 0.0, '2026-06-15', 'Solde initial repris'
);

INSERT INTO contrat_travail (
    id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_contrat, 
    date_debut, date_signature, numero_contrat
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '331'), 
    (SELECT id_filiale FROM ref_filiale WHERE code_filiale = 'GLX'), 
    (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDD'), 
    (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT'), 
    (SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'ACTIF'),
    '2026-06-15', '2026-06-15', 'CT-331-01'
) ON CONFLICT (numero_contrat) DO NOTHING;
INSERT INTO employe_affectation_carriere (
    id_employe, id_contrat, id_statut_affectation, id_motif_mouvement, 
    id_site, id_departement, id_service, id_fonction, 
    id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '331'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-331-01'),
    (SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = 'ACTIVE'),
    (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'),
    (SELECT id_site FROM ref_site WHERE code_site = 'TNR'),
    (SELECT id_departement FROM ref_departement WHERE code_departement = 'LMD'),
    (SELECT id_service FROM ref_service WHERE code_service = 'OPE' AND id_departement = (SELECT id_departement FROM ref_departement WHERE code_departement = 'LMD')),
    (SELECT id_fonction FROM ref_fonction WHERE code_fonction = 'CHEF_ZONE'),
    (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'CADRE'),
    (SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie WHERE code = 'MENSUEL'),
    0.00,
    '2026-06-15'
);
INSERT INTO workflow_rh (
    id_employe, id_type_evenement_rh, id_statut_workflow, 
    id_contrat_cible, id_affectation_cible, 
    date_demande, date_effet, motif, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '331'),
    (SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'EMBAUCHE_INITIALE'),
    (SELECT id_statut_workflow FROM ref_statut_workflow WHERE code = 'VALIDE'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-331-01'),
    (SELECT id_affectation FROM employe_affectation_carriere WHERE id_contrat = (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-331-01') AND id_motif_mouvement = (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE') LIMIT 1),
    '2026-06-15', '2026-06-15', 'Reprise des données existantes (Embauche Initiale)', 'Migration historique'
);
INSERT INTO mouvement_solde_conge (
    id_employe, type_mouvement, nb_jours, date_mouvement, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '331'), 'ACQUISITION', 0.0, '2026-06-15', 'Solde initial repris'
);

INSERT INTO contrat_travail (
    id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_contrat, 
    date_debut, date_signature, numero_contrat
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '332'), 
    (SELECT id_filiale FROM ref_filiale WHERE code_filiale = 'GLX'), 
    (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDD'), 
    (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT'), 
    (SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'ACTIF'),
    '2026-06-23', '2026-06-23', 'CT-332-01'
) ON CONFLICT (numero_contrat) DO NOTHING;
INSERT INTO employe_affectation_carriere (
    id_employe, id_contrat, id_statut_affectation, id_motif_mouvement, 
    id_site, id_departement, id_service, id_fonction, 
    id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '332'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-332-01'),
    (SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = 'ACTIVE'),
    (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'),
    (SELECT id_site FROM ref_site WHERE code_site = 'TNR'),
    (SELECT id_departement FROM ref_departement WHERE code_departement = 'LMD'),
    (SELECT id_service FROM ref_service WHERE code_service = 'OPE' AND id_departement = (SELECT id_departement FROM ref_departement WHERE code_departement = 'LMD')),
    (SELECT id_fonction FROM ref_fonction WHERE code_fonction = 'CHEF_ZONE'),
    (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'CADRE'),
    (SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie WHERE code = 'MENSUEL'),
    0.00,
    '2026-06-23'
);
INSERT INTO workflow_rh (
    id_employe, id_type_evenement_rh, id_statut_workflow, 
    id_contrat_cible, id_affectation_cible, 
    date_demande, date_effet, motif, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '332'),
    (SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'EMBAUCHE_INITIALE'),
    (SELECT id_statut_workflow FROM ref_statut_workflow WHERE code = 'VALIDE'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-332-01'),
    (SELECT id_affectation FROM employe_affectation_carriere WHERE id_contrat = (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-332-01') AND id_motif_mouvement = (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE') LIMIT 1),
    '2026-06-23', '2026-06-23', 'Reprise des données existantes (Embauche Initiale)', 'Migration historique'
);
INSERT INTO mouvement_solde_conge (
    id_employe, type_mouvement, nb_jours, date_mouvement, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '332'), 'ACQUISITION', 0.0, '2026-06-23', 'Solde initial repris'
);

INSERT INTO contrat_travail (
    id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_contrat, 
    date_debut, date_signature, numero_contrat
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '333'), 
    (SELECT id_filiale FROM ref_filiale WHERE code_filiale = 'GLX'), 
    (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDD'), 
    (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT'), 
    (SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'ACTIF'),
    '2026-07-16', '2026-07-16', 'CT-333-01'
) ON CONFLICT (numero_contrat) DO NOTHING;
INSERT INTO employe_affectation_carriere (
    id_employe, id_contrat, id_statut_affectation, id_motif_mouvement, 
    id_site, id_departement, id_service, id_fonction, 
    id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '333'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-333-01'),
    (SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = 'ACTIVE'),
    (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'),
    (SELECT id_site FROM ref_site WHERE code_site = 'TNR'),
    (SELECT id_departement FROM ref_departement WHERE code_departement = 'LMD'),
    (SELECT id_service FROM ref_service WHERE code_service = 'OPE' AND id_departement = (SELECT id_departement FROM ref_departement WHERE code_departement = 'LMD')),
    (SELECT id_fonction FROM ref_fonction WHERE code_fonction = 'CHEF_ZONE'),
    (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'CADRE'),
    (SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie WHERE code = 'MENSUEL'),
    0.00,
    '2026-07-16'
);
INSERT INTO workflow_rh (
    id_employe, id_type_evenement_rh, id_statut_workflow, 
    id_contrat_cible, id_affectation_cible, 
    date_demande, date_effet, motif, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '333'),
    (SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'EMBAUCHE_INITIALE'),
    (SELECT id_statut_workflow FROM ref_statut_workflow WHERE code = 'VALIDE'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-333-01'),
    (SELECT id_affectation FROM employe_affectation_carriere WHERE id_contrat = (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-333-01') AND id_motif_mouvement = (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE') LIMIT 1),
    '2026-07-16', '2026-07-16', 'Reprise des données existantes (Embauche Initiale)', 'Migration historique'
);
INSERT INTO mouvement_solde_conge (
    id_employe, type_mouvement, nb_jours, date_mouvement, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '333'), 'ACQUISITION', 0.0, '2026-07-16', 'Solde initial repris'
);

INSERT INTO contrat_travail (
    id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_contrat, 
    date_debut, date_signature, numero_contrat
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '334'), 
    (SELECT id_filiale FROM ref_filiale WHERE code_filiale = 'GLX'), 
    (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDD'), 
    (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT'), 
    (SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'ACTIF'),
    '2026-07-20', '2026-07-20', 'CT-334-01'
) ON CONFLICT (numero_contrat) DO NOTHING;
INSERT INTO employe_affectation_carriere (
    id_employe, id_contrat, id_statut_affectation, id_motif_mouvement, 
    id_site, id_departement, id_service, id_fonction, 
    id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '334'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-334-01'),
    (SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = 'ACTIVE'),
    (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'),
    (SELECT id_site FROM ref_site WHERE code_site = 'TNR'),
    (SELECT id_departement FROM ref_departement WHERE code_departement = 'LMD'),
    (SELECT id_service FROM ref_service WHERE code_service = 'OPE' AND id_departement = (SELECT id_departement FROM ref_departement WHERE code_departement = 'LMD')),
    (SELECT id_fonction FROM ref_fonction WHERE code_fonction = 'CHEF_ZONE'),
    (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'CADRE'),
    (SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie WHERE code = 'MENSUEL'),
    0.00,
    '2026-07-20'
);
INSERT INTO workflow_rh (
    id_employe, id_type_evenement_rh, id_statut_workflow, 
    id_contrat_cible, id_affectation_cible, 
    date_demande, date_effet, motif, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '334'),
    (SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'EMBAUCHE_INITIALE'),
    (SELECT id_statut_workflow FROM ref_statut_workflow WHERE code = 'VALIDE'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-334-01'),
    (SELECT id_affectation FROM employe_affectation_carriere WHERE id_contrat = (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-334-01') AND id_motif_mouvement = (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE') LIMIT 1),
    '2026-07-20', '2026-07-20', 'Reprise des données existantes (Embauche Initiale)', 'Migration historique'
);
INSERT INTO mouvement_solde_conge (
    id_employe, type_mouvement, nb_jours, date_mouvement, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '334'), 'ACQUISITION', 0.0, '2026-07-20', 'Solde initial repris'
);

INSERT INTO contrat_travail (
    id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_contrat, 
    date_debut, date_signature, numero_contrat
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '292'), 
    (SELECT id_filiale FROM ref_filiale WHERE code_filiale = 'VA'), 
    (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDI'), 
    (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT'), 
    (SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'ACTIF'),
    '2026-01-01', '2026-01-01', 'CT-292-01'
) ON CONFLICT (numero_contrat) DO NOTHING;
INSERT INTO employe_affectation_carriere (
    id_employe, id_contrat, id_statut_affectation, id_motif_mouvement, 
    id_site, id_departement, id_service, id_fonction, 
    id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '292'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-292-01'),
    (SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = 'ACTIVE'),
    (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'),
    (SELECT id_site FROM ref_site WHERE code_site = 'TNR'),
    (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG'),
    (SELECT id_service FROM ref_service WHERE code_service = 'OPE' AND id_departement = (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG')),
    (SELECT id_fonction FROM ref_fonction WHERE code_fonction = 'COMMERCIAL_EXEC'),
    (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'CADRE'),
    (SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie WHERE code = 'MENSUEL'),
    0.00,
    '2026-01-01'
);
INSERT INTO workflow_rh (
    id_employe, id_type_evenement_rh, id_statut_workflow, 
    id_contrat_cible, id_affectation_cible, 
    date_demande, date_effet, motif, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '292'),
    (SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'EMBAUCHE_INITIALE'),
    (SELECT id_statut_workflow FROM ref_statut_workflow WHERE code = 'VALIDE'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-292-01'),
    (SELECT id_affectation FROM employe_affectation_carriere WHERE id_contrat = (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-292-01') AND id_motif_mouvement = (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE') LIMIT 1),
    '2026-01-01', '2026-01-01', 'Reprise des données existantes (Embauche Initiale)', 'Migration historique'
);
INSERT INTO mouvement_solde_conge (
    id_employe, type_mouvement, nb_jours, date_mouvement, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '292'), 'ACQUISITION', 0.0, '2026-01-01', 'Solde initial repris'
);

INSERT INTO contrat_travail (
    id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_contrat, 
    date_debut, date_signature, numero_contrat
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '295'), 
    (SELECT id_filiale FROM ref_filiale WHERE code_filiale = 'VA'), 
    (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDI'), 
    (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT'), 
    (SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'ACTIF'),
    '2026-01-01', '2026-01-01', 'CT-295-01'
) ON CONFLICT (numero_contrat) DO NOTHING;
INSERT INTO employe_affectation_carriere (
    id_employe, id_contrat, id_statut_affectation, id_motif_mouvement, 
    id_site, id_departement, id_service, id_fonction, 
    id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '295'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-295-01'),
    (SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = 'ACTIVE'),
    (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'),
    (SELECT id_site FROM ref_site WHERE code_site = 'TNR'),
    (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG'),
    (SELECT id_service FROM ref_service WHERE code_service = 'OPE' AND id_departement = (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG')),
    (SELECT id_fonction FROM ref_fonction WHERE code_fonction = 'AIDE_DECLARANT'),
    (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'A'),
    (SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie WHERE code = 'MENSUEL'),
    0.00,
    '2026-01-01'
);
INSERT INTO workflow_rh (
    id_employe, id_type_evenement_rh, id_statut_workflow, 
    id_contrat_cible, id_affectation_cible, 
    date_demande, date_effet, motif, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '295'),
    (SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'EMBAUCHE_INITIALE'),
    (SELECT id_statut_workflow FROM ref_statut_workflow WHERE code = 'VALIDE'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-295-01'),
    (SELECT id_affectation FROM employe_affectation_carriere WHERE id_contrat = (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-295-01') AND id_motif_mouvement = (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE') LIMIT 1),
    '2026-01-01', '2026-01-01', 'Reprise des données existantes (Embauche Initiale)', 'Migration historique'
);
INSERT INTO mouvement_solde_conge (
    id_employe, type_mouvement, nb_jours, date_mouvement, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '295'), 'ACQUISITION', 0.0, '2026-01-01', 'Solde initial repris'
);

INSERT INTO contrat_travail (
    id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_contrat, 
    date_debut, date_signature, numero_contrat
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '296'), 
    (SELECT id_filiale FROM ref_filiale WHERE code_filiale = 'VA'), 
    (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDI'), 
    (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT'), 
    (SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'ACTIF'),
    '2026-01-01', '2026-01-01', 'CT-296-01'
) ON CONFLICT (numero_contrat) DO NOTHING;
INSERT INTO employe_affectation_carriere (
    id_employe, id_contrat, id_statut_affectation, id_motif_mouvement, 
    id_site, id_departement, id_service, id_fonction, 
    id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '296'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-296-01'),
    (SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = 'ACTIVE'),
    (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'),
    (SELECT id_site FROM ref_site WHERE code_site = 'TNR'),
    (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG'),
    (SELECT id_service FROM ref_service WHERE code_service = 'OPE' AND id_departement = (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG')),
    (SELECT id_fonction FROM ref_fonction WHERE code_fonction = 'AGENT_FORMATION'),
    (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'A'),
    (SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie WHERE code = 'MENSUEL'),
    0.00,
    '2026-01-01'
);
INSERT INTO workflow_rh (
    id_employe, id_type_evenement_rh, id_statut_workflow, 
    id_contrat_cible, id_affectation_cible, 
    date_demande, date_effet, motif, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '296'),
    (SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'EMBAUCHE_INITIALE'),
    (SELECT id_statut_workflow FROM ref_statut_workflow WHERE code = 'VALIDE'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-296-01'),
    (SELECT id_affectation FROM employe_affectation_carriere WHERE id_contrat = (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-296-01') AND id_motif_mouvement = (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE') LIMIT 1),
    '2026-01-01', '2026-01-01', 'Reprise des données existantes (Embauche Initiale)', 'Migration historique'
);
INSERT INTO mouvement_solde_conge (
    id_employe, type_mouvement, nb_jours, date_mouvement, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '296'), 'ACQUISITION', 0.0, '2026-01-01', 'Solde initial repris'
);

INSERT INTO contrat_travail (
    id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_contrat, 
    date_debut, date_signature, numero_contrat
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '297'), 
    (SELECT id_filiale FROM ref_filiale WHERE code_filiale = 'VA'), 
    (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDI'), 
    (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT'), 
    (SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'ACTIF'),
    '2026-01-01', '2026-01-01', 'CT-297-01'
) ON CONFLICT (numero_contrat) DO NOTHING;
INSERT INTO employe_affectation_carriere (
    id_employe, id_contrat, id_statut_affectation, id_motif_mouvement, 
    id_site, id_departement, id_service, id_fonction, 
    id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '297'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-297-01'),
    (SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = 'ACTIVE'),
    (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'),
    (SELECT id_site FROM ref_site WHERE code_site = 'TNR'),
    (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG'),
    (SELECT id_service FROM ref_service WHERE code_service = 'OPE' AND id_departement = (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG')),
    (SELECT id_fonction FROM ref_fonction WHERE code_fonction = 'AGENT_FORMATION'),
    (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'A'),
    (SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie WHERE code = 'MENSUEL'),
    0.00,
    '2026-01-01'
);
INSERT INTO workflow_rh (
    id_employe, id_type_evenement_rh, id_statut_workflow, 
    id_contrat_cible, id_affectation_cible, 
    date_demande, date_effet, motif, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '297'),
    (SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'EMBAUCHE_INITIALE'),
    (SELECT id_statut_workflow FROM ref_statut_workflow WHERE code = 'VALIDE'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-297-01'),
    (SELECT id_affectation FROM employe_affectation_carriere WHERE id_contrat = (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-297-01') AND id_motif_mouvement = (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE') LIMIT 1),
    '2026-01-01', '2026-01-01', 'Reprise des données existantes (Embauche Initiale)', 'Migration historique'
);
INSERT INTO mouvement_solde_conge (
    id_employe, type_mouvement, nb_jours, date_mouvement, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '297'), 'ACQUISITION', 0.0, '2026-01-01', 'Solde initial repris'
);

INSERT INTO contrat_travail (
    id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_contrat, 
    date_debut, date_signature, numero_contrat
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '396'), 
    (SELECT id_filiale FROM ref_filiale WHERE code_filiale = 'GLX'), 
    (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'PRESTATION'), 
    (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'CONSULTANT'), 
    (SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'ACTIF'),
    '2026-01-01', '2026-01-01', 'CT-396-01'
) ON CONFLICT (numero_contrat) DO NOTHING;
INSERT INTO employe_affectation_carriere (
    id_employe, id_contrat, id_statut_affectation, id_motif_mouvement, 
    id_site, id_departement, id_service, id_fonction, 
    id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '396'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-396-01'),
    (SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = 'ACTIVE'),
    (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'),
    (SELECT id_site FROM ref_site WHERE code_site = 'TNR'),
    (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG'),
    (SELECT id_service FROM ref_service WHERE code_service = 'ADM' AND id_departement = (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG')),
    (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'A'),
    (SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie WHERE code = 'MENSUEL'),
    0.00,
    '2026-01-01'
);
INSERT INTO workflow_rh (
    id_employe, id_type_evenement_rh, id_statut_workflow, 
    id_contrat_cible, id_affectation_cible, 
    date_demande, date_effet, motif, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '396'),
    (SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'EMBAUCHE_INITIALE'),
    (SELECT id_statut_workflow FROM ref_statut_workflow WHERE code = 'VALIDE'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-396-01'),
    (SELECT id_affectation FROM employe_affectation_carriere WHERE id_contrat = (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-396-01') AND id_motif_mouvement = (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE') LIMIT 1),
    '2026-01-01', '2026-01-01', 'Reprise des données existantes (Embauche Initiale)', 'Migration historique'
);
INSERT INTO mouvement_solde_conge (
    id_employe, type_mouvement, nb_jours, date_mouvement, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '396'), 'ACQUISITION', 0.0, '2026-01-01', 'Solde initial repris'
);

INSERT INTO contrat_travail (
    id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_contrat, 
    date_debut, date_signature, numero_contrat
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '397'), 
    (SELECT id_filiale FROM ref_filiale WHERE code_filiale = 'GLX'), 
    (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'PRESTATION'), 
    (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'CONSULTANT'), 
    (SELECT id_statut_contrat FROM ref_statut_contrat WHERE code = 'ACTIF'),
    '2026-01-01', '2026-01-01', 'CT-397-01'
) ON CONFLICT (numero_contrat) DO NOTHING;
INSERT INTO employe_affectation_carriere (
    id_employe, id_contrat, id_statut_affectation, id_motif_mouvement, 
    id_site, id_departement, id_service, id_fonction, 
    id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '397'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-397-01'),
    (SELECT id_statut_affectation FROM ref_statut_affectation WHERE code = 'ACTIVE'),
    (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'),
    (SELECT id_site FROM ref_site WHERE code_site = 'TNR'),
    (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG'),
    (SELECT id_service FROM ref_service WHERE code_service = 'ADM' AND id_departement = (SELECT id_departement FROM ref_departement WHERE code_departement = 'LOG')),
    (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'A'),
    (SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie WHERE code = 'MENSUEL'),
    0.00,
    '2026-01-01'
);
INSERT INTO workflow_rh (
    id_employe, id_type_evenement_rh, id_statut_workflow, 
    id_contrat_cible, id_affectation_cible, 
    date_demande, date_effet, motif, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '397'),
    (SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'EMBAUCHE_INITIALE'),
    (SELECT id_statut_workflow FROM ref_statut_workflow WHERE code = 'VALIDE'),
    (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-397-01'),
    (SELECT id_affectation FROM employe_affectation_carriere WHERE id_contrat = (SELECT id_contrat FROM contrat_travail WHERE numero_contrat = 'CT-397-01') AND id_motif_mouvement = (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE') LIMIT 1),
    '2026-01-01', '2026-01-01', 'Reprise des données existantes (Embauche Initiale)', 'Migration historique'
);
INSERT INTO mouvement_solde_conge (
    id_employe, type_mouvement, nb_jours, date_mouvement, commentaire
) VALUES (
    (SELECT id_employe FROM employe WHERE matricule = '397'), 'ACQUISITION', 0.0, '2026-01-01', 'Solde initial repris'
);
