-- =====================================================================
-- SCRIPT POSTGRESQL : JEU DE DONNÉES DE TEST SIRH RÉALISTE (EMPLOYÉS A À H)
-- =====================================================================

-- 1. Nettoyage ciblé des employés A à H pour idempotence
DELETE FROM employe WHERE matricule IN (
    'EMP-00001', 'EMP-00002', 'EMP-00003', 'EMP-00004',
    'EMP-00005', 'EMP-00006', 'EMP-00007', 'EMP-00008'
);

-- =====================================================================
-- INSERTION DES EMPLOYÉS A À H
-- =====================================================================

-- EMPLOYÉ A : Actif avec CDD (6 mois)
INSERT INTO employe (matricule, nom, prenom, sexe, date_naissance, lieu_naissance, nationalite, cin_numero, cin_date_delivrance, cin_lieu_delivrance, adresse, ville, telephone, email, est_actif)
VALUES ('EMP-00001', 'ANDRIANINA', 'Mamy', 'M', '1995-05-10', 'Antananarivo', 'Malgache', '101234567890', '2013-06-15', 'Antananarivo', 'Lot II M 45 Antanimena', 'Antananarivo', '+261340123456', 'mamy.andrianina@galaxam.mg', true);

-- EMPLOYÉ B : Actif avec CDI Cadre (DAF - Dispense pointage)
INSERT INTO employe (matricule, nom, prenom, sexe, date_naissance, lieu_naissance, nationalite, cin_numero, cin_date_delivrance, cin_lieu_delivrance, adresse, ville, telephone, email, est_actif)
VALUES ('EMP-00002', 'RAZAFY', 'Hery', 'M', '1988-03-20', 'Tamatave', 'Malgache', '201234567891', '2006-04-10', 'Tamatave', 'Lot IV B 12 Ankadifotsy', 'Antananarivo', '+261320123457', 'hery.razafy@galaxam.mg', true);

-- EMPLOYÉ C : Contrat clôturé / inactif
INSERT INTO employe (matricule, nom, prenom, sexe, date_naissance, lieu_naissance, nationalite, cin_numero, cin_date_delivrance, cin_lieu_delivrance, adresse, ville, telephone, email, est_actif, date_desactivation, motif_desactivation)
VALUES ('EMP-00003', 'RABEMANANJARA', 'Faly', 'M', '1992-11-15', 'Majunga', 'Malgache', '401234567892', '2010-12-05', 'Majunga', 'Quartier Mangarivotra', 'Majunga', '+261330123458', 'faly.rabe@galaxam.mg', false, '2025-12-31 17:00:00', 'Fin_contrat');

-- EMPLOYÉ D : Nouvel employé embauché en cours de mois (Août 2026)
INSERT INTO employe (matricule, nom, prenom, sexe, date_naissance, lieu_naissance, nationalite, cin_numero, cin_date_delivrance, cin_lieu_delivrance, adresse, ville, telephone, email, est_actif)
VALUES ('EMP-00004', 'RAMANANTSOA', 'Bakoly', 'F', '1999-07-08', 'Antsirabe', 'Malgache', '111234567893', '2017-08-15', 'Antsirabe', 'Avenue de la Gare', 'Antsirabe', '+261340123459', 'bakoly.ram@galaxam.mg', true);

-- EMPLOYÉ E : Employé avec mutation / changement d'affectation
INSERT INTO employe (matricule, nom, prenom, sexe, date_naissance, lieu_naissance, nationalite, cin_numero, cin_date_delivrance, cin_lieu_delivrance, adresse, ville, telephone, email, est_actif)
VALUES ('EMP-00005', 'RAKOTOMALALA', 'Tojo', 'M', '1993-09-12', 'Fianarantsoa', 'Malgache', '301234567894', '2011-10-20', 'Fianarantsoa', 'Boulevard Joffre', 'Tamatave', '+261320123460', 'tojo.rakoto@galaxam.mg', true);

-- EMPLOYÉ F : Employé ayant des congés
INSERT INTO employe (matricule, nom, prenom, sexe, date_naissance, lieu_naissance, nationalite, cin_numero, cin_date_delivrance, cin_lieu_delivrance, adresse, ville, telephone, email, est_actif)
VALUES ('EMP-00006', 'RASOLOFONIRINA', 'Soa', 'F', '1990-04-25', 'Antananarivo', 'Malgache', '101234567895', '2008-05-30', 'Antananarivo', 'Lot VB 78 Ambohibao', 'Antananarivo', '+261340123461', 'soa.rasolo@galaxam.mg', true);

-- EMPLOYÉ G : Employé ayant des absences
INSERT INTO employe (matricule, nom, prenom, sexe, date_naissance, lieu_naissance, nationalite, cin_numero, cin_date_delivrance, cin_lieu_delivrance, adresse, ville, telephone, email, est_actif)
VALUES ('EMP-00007', 'RAJAONARISON', 'Eric', 'M', '1996-01-14', 'Antananarivo', 'Malgache', '101234567896', '2014-02-18', 'Antananarivo', 'Cité des 67Ha', 'Antananarivo', '+261330123462', 'eric.rajao@galaxam.mg', true);

-- EMPLOYÉ H : Employé utilisé pour les tests de pointage (Non-cadre)
INSERT INTO employe (matricule, nom, prenom, sexe, date_naissance, lieu_naissance, nationalite, cin_numero, cin_date_delivrance, cin_lieu_delivrance, adresse, ville, telephone, email, est_actif)
VALUES ('EMP-00008', 'ANDRIAMIFIDY', 'Lova', 'M', '1994-08-03', 'Tamatave', 'Malgache', '201234567897', '2012-09-12', 'Tamatave', 'Ankirihiry Nord', 'Tamatave', '+261320123463', 'lova.andria@galaxam.mg', true);

-- =====================================================================
-- COMPTES BANCAIRES & CONTACTS D'URGENCE
-- =====================================================================
INSERT INTO employe_compte_banque (id_employe, id_banque, titulaire_compte, num_compte_rib, est_principal)
SELECT id_employe, 1, nom || ' ' || prenom, '00004 00123 ' || lpad(id_employe::text, 11, '0') || ' 45', true
FROM employe WHERE matricule IN ('EMP-00001', 'EMP-00002', 'EMP-00004', 'EMP-00005', 'EMP-00006', 'EMP-00007', 'EMP-00008');

INSERT INTO employe_contact_urgence (id_employe, nom, prenom, lien_parente, telephone, adresse)
SELECT id_employe, 'RAZAFINDRAKOTO', 'Pauline', 'Conjoint', '+261340099887', adresse
FROM employe WHERE matricule IN ('EMP-00001', 'EMP-00002', 'EMP-00005', 'EMP-00006');

-- =====================================================================
-- CONTRATS DE TRAVAIL
-- =====================================================================

-- EMP A : CDD 1 an (2026)
INSERT INTO contrat_travail (id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_rh, numero_contrat, date_debut, date_fin, comptabilise_effectif)
SELECT id_employe, 1, 2, 1, 4, 'CT-2026-CDD-0001', '2026-01-01', '2026-12-31', true
FROM employe WHERE matricule = 'EMP-00001';

-- EMP B : CDI Cadre DAF
INSERT INTO contrat_travail (id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_rh, numero_contrat, date_debut, comptabilise_effectif)
SELECT id_employe, 1, 1, 1, 4, 'CT-2024-CDI-0002', '2024-02-01', true
FROM employe WHERE matricule = 'EMP-00002';

-- EMP C : CDD 2025 Clôturé
INSERT INTO contrat_travail (id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_rh, numero_contrat, date_debut, date_fin, comptabilise_effectif, id_motif_sortie)
SELECT id_employe, 1, 2, 1, 7, 'CT-2025-CDD-0003', '2025-01-01', '2025-12-31', false, 3
FROM employe WHERE matricule = 'EMP-00003';

-- EMP D : CDI Août 2026
INSERT INTO contrat_travail (id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_rh, numero_contrat, date_debut, comptabilise_effectif, duree_essai_mois, date_fin_essai)
SELECT id_employe, 2, 1, 1, 4, 'CT-2026-CDI-0004', '2026-08-15', true, 3, '2026-11-14'
FROM employe WHERE matricule = 'EMP-00004';

-- EMP E : CDI 2023 (Muté)
INSERT INTO contrat_travail (id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_rh, numero_contrat, date_debut, comptabilise_effectif)
SELECT id_employe, 1, 1, 1, 4, 'CT-2023-CDI-0005', '2023-01-01', true
FROM employe WHERE matricule = 'EMP-00005';

-- EMP F : CDI 2022 (Congés)
INSERT INTO contrat_travail (id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_rh, numero_contrat, date_debut, comptabilise_effectif)
SELECT id_employe, 1, 1, 1, 4, 'CT-2022-CDI-0006', '2022-01-01', true
FROM employe WHERE matricule = 'EMP-00006';

-- EMP G : CDI 2025 (Absences)
INSERT INTO contrat_travail (id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_rh, numero_contrat, date_debut, comptabilise_effectif)
SELECT id_employe, 1, 1, 1, 4, 'CT-2025-CDI-0007', '2025-01-01', true
FROM employe WHERE matricule = 'EMP-00007';

-- EMP H : CDI 2024 (Pointage)
INSERT INTO contrat_travail (id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_rh, numero_contrat, date_debut, comptabilise_effectif)
SELECT id_employe, 1, 1, 1, 4, 'CT-2024-CDI-0008', '2024-01-01', true
FROM employe WHERE matricule = 'EMP-00008';

-- =====================================================================
-- AFFECTATIONS DE CARRIÈRE
-- =====================================================================

-- EMP A : TNR / OPE_MGR
INSERT INTO employe_affectation_carriere (id_employe, id_contrat, id_statut_rh, id_motif_mouvement, id_site, id_departement, id_service, id_fonction, id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut)
SELECT e.id_employe, c.id_contrat, 4, 1, 1, 1, 2, 5, 1, 1, 1500000.00, '2026-01-01'
FROM employe e JOIN contrat_travail c ON e.id_employe = c.id_employe WHERE e.matricule = 'EMP-00001';

-- EMP B : TNR / DAF
INSERT INTO employe_affectation_carriere (id_employe, id_contrat, id_statut_rh, id_motif_mouvement, id_site, id_departement, id_service, id_fonction, id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut)
SELECT e.id_employe, c.id_contrat, 4, 1, 1, 1, 1, 2, 1, 1, 3500000.00, '2024-02-01'
FROM employe e JOIN contrat_travail c ON e.id_employe = c.id_employe WHERE e.matricule = 'EMP-00002';

-- EMP C : Clôturé
INSERT INTO employe_affectation_carriere (id_employe, id_contrat, id_statut_rh, id_motif_mouvement, id_site, id_departement, id_service, id_fonction, id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut, date_fin)
SELECT e.id_employe, c.id_contrat, 6, 1, 1, 1, 1, 4, 2, 1, 800000.00, '2025-01-01', '2025-12-31'
FROM employe e JOIN contrat_travail c ON e.id_employe = c.id_employe WHERE e.matricule = 'EMP-00003';

-- EMP D : TNR / AIDE_COMPTABLE
INSERT INTO employe_affectation_carriere (id_employe, id_contrat, id_statut_rh, id_motif_mouvement, id_site, id_departement, id_service, id_fonction, id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut)
SELECT e.id_employe, c.id_contrat, 4, 1, 1, 1, 1, 4, 2, 1, 900000.00, '2026-08-15'
FROM employe e JOIN contrat_travail c ON e.id_employe = c.id_employe WHERE e.matricule = 'EMP-00004';

-- EMP E : Affectation 1 (TNR) puis Affectation 2 (TMM)
INSERT INTO employe_affectation_carriere (id_employe, id_contrat, id_statut_rh, id_motif_mouvement, id_site, id_departement, id_service, id_fonction, id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut, date_fin)
SELECT e.id_employe, c.id_contrat, 6, 1, 1, 1, 1, 3, 1, 1, 1800000.00, '2023-01-01', '2026-06-30'
FROM employe e JOIN contrat_travail c ON e.id_employe = c.id_employe WHERE e.matricule = 'EMP-00005';

INSERT INTO employe_affectation_carriere (id_employe, id_contrat, id_statut_rh, id_motif_mouvement, id_site, id_departement, id_service, id_fonction, id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut)
SELECT e.id_employe, c.id_contrat, 4, 3, 2, 1, 2, 3, 1, 1, 2200000.00, '2026-07-01'
FROM employe e JOIN contrat_travail c ON e.id_employe = c.id_employe WHERE e.matricule = 'EMP-00005';

-- EMP F : TNR / OPE_MGR
INSERT INTO employe_affectation_carriere (id_employe, id_contrat, id_statut_rh, id_motif_mouvement, id_site, id_departement, id_service, id_fonction, id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut)
SELECT e.id_employe, c.id_contrat, 4, 1, 1, 1, 2, 5, 1, 1, 1600000.00, '2022-01-01'
FROM employe e JOIN contrat_travail c ON e.id_employe = c.id_employe WHERE e.matricule = 'EMP-00006';

-- EMP G : TNR / AIDE_COMPTABLE
INSERT INTO employe_affectation_carriere (id_employe, id_contrat, id_statut_rh, id_motif_mouvement, id_site, id_departement, id_service, id_fonction, id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut)
SELECT e.id_employe, c.id_contrat, 4, 1, 1, 1, 1, 4, 2, 1, 850000.00, '2025-01-01'
FROM employe e JOIN contrat_travail c ON e.id_employe = c.id_employe WHERE e.matricule = 'EMP-00007';

-- EMP H : TMM / OPE_MGR (Non-cadre avec indemnités Tamatave)
INSERT INTO employe_affectation_carriere (id_employe, id_contrat, id_statut_rh, id_motif_mouvement, id_site, id_departement, id_service, id_fonction, id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut)
SELECT e.id_employe, c.id_contrat, 4, 1, 2, 1, 2, 5, 2, 1, 1400000.00, '2024-01-01'
FROM employe e JOIN contrat_travail c ON e.id_employe = c.id_employe WHERE e.matricule = 'EMP-00008';

-- =====================================================================
-- CONGÉS : ACQUISITIONS ET DEMANDES VALIDÉES POUR EMP F
-- =====================================================================
INSERT INTO mouvement_solde_conge (id_employe, type_mouvement, nb_jours, date_mouvement, commentaire)
SELECT id_employe, 'ACQUISITION', 50.0, '2026-06-30', 'Acquisition mensuelle cumulée 2024-2026'
FROM employe WHERE matricule = 'EMP-00006';

INSERT INTO demande_conge (id_employe, id_type_conge, id_statut_demande_conge, date_debut, date_fin, nb_jours_decomptes, commentaire, date_validation, valide_par, saisi_par)
SELECT id_employe, 1, 2, '2026-07-06', '2026-07-17', 10.0, 'Congé annuel été', '2026-07-01 10:00:00', 1, 1
FROM employe WHERE matricule = 'EMP-00006';

INSERT INTO mouvement_solde_conge (id_employe, id_demande_conge, type_mouvement, nb_jours, date_mouvement, commentaire)
SELECT d.id_employe, d.id_demande_conge, 'PRISE', -10.0, '2026-07-01', 'Débit congé validé'
FROM demande_conge d JOIN employe e ON d.id_employe = e.id_employe WHERE e.matricule = 'EMP-00006';

-- =====================================================================
-- PÉRIODE DE PAIE D'AOÛT 2026
-- =====================================================================
INSERT INTO periode_paie (code, libelle, date_debut, date_fin, id_statut_periode)
VALUES ('2026-08', 'Période Août 2026', '2026-08-01', '2026-08-31', 1)
ON CONFLICT (code) DO NOTHING;
