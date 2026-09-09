-- ============================================================================
-- SCRIPT DE MIGRATION SP8 : INDEXATION SYSTÉMATIQUE DES CLÉS ÉTRANGÈRES
-- Date : 2026-09-16
-- Auteur : Audit Architecture GALAXAM SIRH
-- Objectif : Supprimer les 71 clés étrangères non indexées détectées lors de l'audit.
--            Évite les verrous exclusifs de table (table locks) lors des cascades
--            et élimine les sequential scans sur les jointures relationnelles.
-- ============================================================================

-- 1. MODULE PAIE & STC & AVANCES
CREATE INDEX IF NOT EXISTS idx_bulletin_paie_calcule_par ON bulletin_paie(calcule_par);
CREATE INDEX IF NOT EXISTS idx_bulletin_paie_cloture_par ON bulletin_paie(cloture_par);
CREATE INDEX IF NOT EXISTS idx_bulletin_paie_rouvert_par ON bulletin_paie(rouvert_par);
CREATE INDEX IF NOT EXISTS idx_bulletin_paie_valide_par ON bulletin_paie(valide_par);
CREATE INDEX IF NOT EXISTS idx_bulletin_paie_ligne_rubrique ON bulletin_paie_ligne(id_rubrique);
CREATE INDEX IF NOT EXISTS idx_paie_avance_acompte_approuve_par ON paie_avance_acompte(approuve_par);
CREATE INDEX IF NOT EXISTS idx_paie_avance_acompte_cree_par ON paie_avance_acompte(cree_par);
CREATE INDEX IF NOT EXISTS idx_paie_avance_acompte_bulletin ON paie_avance_acompte(id_bulletin);
CREATE INDEX IF NOT EXISTS idx_paie_pret_accorde_par ON paie_pret(accorde_par);
CREATE INDEX IF NOT EXISTS idx_paie_pret_echeance_bulletin ON paie_pret_echeance(id_bulletin);
CREATE INDEX IF NOT EXISTS idx_paie_stc_cree_par ON paie_stc(cree_par);
CREATE INDEX IF NOT EXISTS idx_paie_stc_bulletin ON paie_stc(id_bulletin);
CREATE INDEX IF NOT EXISTS idx_paie_stc_valide_par ON paie_stc(valide_par);
CREATE INDEX IF NOT EXISTS idx_periode_paie_statut ON periode_paie(id_statut_periode);
CREATE INDEX IF NOT EXISTS idx_periode_paie_valide_par ON periode_paie(valide_par);
CREATE INDEX IF NOT EXISTS idx_ref_type_mouvement_paie_rubrique ON ref_type_mouvement_paie(id_rubrique);

-- 2. MODULE WORKFLOW RH & CARRIÈRE
CREATE INDEX IF NOT EXISTS idx_workflow_rh_affectation_cible ON workflow_rh(id_affectation_cible);
CREATE INDEX IF NOT EXISTS idx_workflow_rh_affectation_source ON workflow_rh(id_affectation_source);
CREATE INDEX IF NOT EXISTS idx_workflow_rh_contrat_cible ON workflow_rh(id_contrat_cible);
CREATE INDEX IF NOT EXISTS idx_workflow_rh_contrat_source ON workflow_rh(id_contrat_source);
CREATE INDEX IF NOT EXISTS idx_workflow_rh_demandeur ON workflow_rh(id_demandeur);
CREATE INDEX IF NOT EXISTS idx_workflow_rh_employe ON workflow_rh(id_employe);
CREATE INDEX IF NOT EXISTS idx_workflow_rh_statut_rh ON workflow_rh(id_statut_rh);
CREATE INDEX IF NOT EXISTS idx_workflow_rh_type_evenement ON workflow_rh(id_type_evenement_rh);
CREATE INDEX IF NOT EXISTS idx_workflow_rh_valideur ON workflow_rh(id_valideur);

-- 3. MODULE POINTAGE & CALENDRIER
CREATE INDEX IF NOT EXISTS idx_pointage_jour_site ON pointage_jour(id_site);
CREATE INDEX IF NOT EXISTS idx_pointage_jour_saisi_par ON pointage_jour(saisi_par);
CREATE INDEX IF NOT EXISTS idx_pointage_cumul_indemnite_emp ON pointage_cumul_indemnite(id_employe);
CREATE INDEX IF NOT EXISTS idx_pointage_cumul_indemnite_ind ON pointage_cumul_indemnite(id_indemnite);
CREATE INDEX IF NOT EXISTS idx_pointage_cumul_periode_gele_par ON pointage_cumul_periode(gele_par);
CREATE INDEX IF NOT EXISTS idx_pointage_cumul_periode_periode ON pointage_cumul_periode(id_periode_paie);
CREATE INDEX IF NOT EXISTS idx_pointage_semaine_calcule_gele_par ON pointage_semaine_calcule(gele_par);
CREATE INDEX IF NOT EXISTS idx_pointage_semaine_calcule_periode ON pointage_semaine_calcule(id_periode_paie);
CREATE INDEX IF NOT EXISTS idx_ref_periode_chomee_site ON ref_periode_chomee(id_site);
CREATE INDEX IF NOT EXISTS idx_site_calendrier_ferie_ferie ON site_calendrier_ferie(id_ferie);
CREATE INDEX IF NOT EXISTS idx_site_cotisation_type ON site_cotisation(id_type_cotisation);
CREATE INDEX IF NOT EXISTS idx_site_indemnite_config_ind ON site_indemnite_config(id_indemnite);

-- 4. MODULE CONGÉS & ABSENCES
CREATE INDEX IF NOT EXISTS idx_demande_conge_type_conge ON demande_conge(id_type_conge);
CREATE INDEX IF NOT EXISTS idx_demande_conge_saisi_par ON demande_conge(saisi_par);
CREATE INDEX IF NOT EXISTS idx_demande_conge_valide_par ON demande_conge(valide_par);
CREATE INDEX IF NOT EXISTS idx_mouvement_solde_conge_created_by ON mouvement_solde_conge(created_by);
CREATE INDEX IF NOT EXISTS idx_ref_type_conge_pool ON ref_type_conge(id_pool_conge_annuel);
CREATE INDEX IF NOT EXISTS idx_ref_type_conge_bascule ON ref_type_conge(id_type_conge_bascule);

-- 5. MODULE CONTRATS & CARRIÈRE & FAMILLE
CREATE INDEX IF NOT EXISTS idx_contrat_periode_essai_contrat ON contrat_periode_essai(id_contrat);
CREATE INDEX IF NOT EXISTS idx_contrat_travail_precedent ON contrat_travail(id_contrat_precedent);
CREATE INDEX IF NOT EXISTS idx_contrat_travail_filiale ON contrat_travail(id_filiale);
CREATE INDEX IF NOT EXISTS idx_contrat_travail_motif_sortie ON contrat_travail(id_motif_sortie);
CREATE INDEX IF NOT EXISTS idx_contrat_travail_type_contrat ON contrat_travail(id_type_contrat);
CREATE INDEX IF NOT EXISTS idx_contrat_travail_evt_init ON contrat_travail(id_type_evenement_initial);
CREATE INDEX IF NOT EXISTS idx_contrat_travail_type_interv ON contrat_travail(id_type_intervenant);
CREATE INDEX IF NOT EXISTS idx_eac_categorie_pro ON employe_affectation_carriere(id_categorie_professionnelle);
CREATE INDEX IF NOT EXISTS idx_eac_departement ON employe_affectation_carriere(id_departement);
CREATE INDEX IF NOT EXISTS idx_eac_fonction ON employe_affectation_carriere(id_fonction);
CREATE INDEX IF NOT EXISTS idx_eac_mode_calcul_paie ON employe_affectation_carriere(id_mode_calcul_paie);
CREATE INDEX IF NOT EXISTS idx_eac_motif_mouvement ON employe_affectation_carriere(id_motif_mouvement);
CREATE INDEX IF NOT EXISTS idx_eac_responsable_hie ON employe_affectation_carriere(id_responsable_hierarchique);
CREATE INDEX IF NOT EXISTS idx_eac_service ON employe_affectation_carriere(id_service);
CREATE INDEX IF NOT EXISTS idx_eac_site ON employe_affectation_carriere(id_site);
CREATE INDEX IF NOT EXISTS idx_employe_compte_banque_banque ON employe_compte_banque(id_banque);
CREATE INDEX IF NOT EXISTS idx_employe_conjoint_emp ON employe_conjoint(id_employe);
CREATE INDEX IF NOT EXISTS idx_employe_contact_urgence_emp ON employe_contact_urgence(id_employe);
CREATE INDEX IF NOT EXISTS idx_employe_enfant_emp ON employe_enfant(id_employe);
CREATE INDEX IF NOT EXISTS idx_employe_indemnite_derog_created_by ON employe_indemnite_derogation(created_by);
CREATE INDEX IF NOT EXISTS idx_employe_indemnite_derog_ind ON employe_indemnite_derogation(id_indemnite);

-- 6. MODULE SÉCURITÉ & ORGANISATION STRUCTURELLE
CREATE INDEX IF NOT EXISTS idx_journal_connexion_utilisateur ON journal_connexion(id_utilisateur);
CREATE INDEX IF NOT EXISTS idx_ref_menu_pere ON ref_menu(id_pere);
CREATE INDEX IF NOT EXISTS idx_ref_service_departement ON ref_service(id_departement);
CREATE INDEX IF NOT EXISTS idx_ref_site_filiale_rattachement ON ref_site(id_filiale_rattachement);
CREATE INDEX IF NOT EXISTS idx_role_menu_menu ON role_menu(id_menu);
CREATE INDEX IF NOT EXISTS idx_role_permission_module ON role_permission(id_module);
CREATE INDEX IF NOT EXISTS idx_utilisateur_role ON utilisateur(id_role);
