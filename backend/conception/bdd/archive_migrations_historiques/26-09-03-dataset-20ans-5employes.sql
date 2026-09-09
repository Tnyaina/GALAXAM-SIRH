-- =====================================================================
-- GALAXAM SIRH — DATASET RÉALISTE : 5 SALARIÉS SUR 20 ANS D'HISTOIRE RH
-- PÉRIODE DE TEST & CONVERGENCE PAIE : AOÛT 2026 (2026-08)
-- =====================================================================

-- 1. PÉRIODE DE PAIE ACTIVE : AOÛT 2026
INSERT INTO periode_paie (id_periode_paie, id_statut_periode, code, libelle, date_debut, date_fin)
VALUES (1, 2, '2026-08', 'Août 2026', '2026-08-01', '2026-08-31')
ON CONFLICT (id_periode_paie) DO UPDATE 
SET id_statut_periode = 2, code = '2026-08', libelle = 'Août 2026', date_debut = '2026-08-01', date_fin = '2026-08-31';
SELECT setval('periode_paie_id_periode_paie_seq', (SELECT GREATEST(MAX(id_periode_paie), 1) FROM periode_paie));


-- 2. JOUR FÉRIÉ LÉGAL : 15 AOÛT (ASSOMPTION)
INSERT INTO ref_calendrier_ferie (id_ferie, date_ferie, libelle, est_chome, est_national)
VALUES (1, '2026-08-15', 'Assomption', true, true)
ON CONFLICT (id_ferie) DO UPDATE SET date_ferie = '2026-08-15', libelle = 'Assomption';

INSERT INTO site_calendrier_ferie (id_site, id_ferie)
VALUES (1, 1), (2, 1)
ON CONFLICT (id_site, id_ferie) DO NOTHING;

-- 3. UTILISATEURS ADMINISTRATEUR & AGENT DE POINTAGE (Hash BCrypt de 'Pass1234')
INSERT INTO utilisateur (identifiant, mot_de_passe_hache, id_role, est_actif)
VALUES 
('admin', '$2a$10$wN1.aJ1K9h6O2e1G1D9CSu4t7g8b9p0q1r2s3t4u5v6w7x8y9z0a.', 1, true),
('pointage_agent', '$2a$10$wN1.aJ1K9h6O2e1G1D9CSu4t7g8b9p0q1r2s3t4u5v6w7x8y9z0a.', 2, true)
ON CONFLICT (identifiant) DO UPDATE
SET mot_de_passe_hache = EXCLUDED.mot_de_passe_hache, id_role = EXCLUDED.id_role, est_actif = true;

SELECT setval('utilisateur_id_utilisateur_seq', (SELECT GREATEST(MAX(id_utilisateur), 2) FROM utilisateur));

-- =====================================================================
-- 4. INSERTION DES 5 SALARIÉS RÉALISTES
-- =====================================================================

-- EMP 1 : RAKOTONDRAZAKA Andry Nirina (Cadre DAF, 20 ans d'ancienneté, dispense pointage)
INSERT INTO employe (id_employe, matricule, nom, prenom, sexe, date_naissance, lieu_naissance, nationalite, cin_numero, cin_date_delivrance, cin_lieu_delivrance, adresse, ville, telephone, email, est_actif)
VALUES (1, 'EMP-2006-0001', 'RAKOTONDRAZAKA', 'Andry Nirina', 'M', '1980-05-14', 'Antananarivo', 'Malgache', '101006000001', '1998-06-10', 'Antananarivo', 'Lot II B 24 Ankadifotsy', 'Antananarivo', '+261340100001', 'andry.rakoto@galaxam.mg', true)
ON CONFLICT (id_employe) DO NOTHING;

-- EMP 2 : RANDRIAMAMPIONONA Jean Jacques (OPE Manager, 12 ans d'ancienneté, muté Tamatave en 2022)
INSERT INTO employe (id_employe, matricule, nom, prenom, sexe, date_naissance, lieu_naissance, nationalite, cin_numero, cin_date_delivrance, cin_lieu_delivrance, adresse, ville, telephone, email, est_actif)
VALUES (2, 'EMP-2014-0012', 'RANDRIAMAMPIONONA', 'Jean Jacques', 'M', '1987-11-22', 'Tamatave', 'Malgache', '201014000012', '2005-12-15', 'Tamatave', 'Boulevard Ratsimilaho', 'Tamatave', '+261320100012', 'jeanjacques.randria@galaxam.mg', true)
ON CONFLICT (id_employe) DO NOTHING;

-- EMP 3 : RASOANIRINA Voahirana Mialy (Admin RH, 7 ans d'ancienneté, congé annuel en Août 2026)
INSERT INTO employe (id_employe, matricule, nom, prenom, sexe, date_naissance, lieu_naissance, nationalite, cin_numero, cin_date_delivrance, cin_lieu_delivrance, adresse, ville, telephone, email, est_actif)
VALUES (3, 'EMP-2019-0028', 'RASOANIRINA', 'Voahirana Mialy', 'F', '1992-03-08', 'Antananarivo', 'Malgache', '101019000028', '2010-04-12', 'Antananarivo', 'Lot IV G 55 Itaosy', 'Antananarivo', '+261330100028', 'voahirana.rasoa@galaxam.mg', true)
ON CONFLICT (id_employe) DO NOTHING;

-- EMP 4 : ANDRIANARIVELO Patrick (Chauffeur / Transit, 3 ans d'ancienneté, travail de nuit et HS en Août)
INSERT INTO employe (id_employe, matricule, nom, prenom, sexe, date_naissance, lieu_naissance, nationalite, cin_numero, cin_date_delivrance, cin_lieu_delivrance, adresse, ville, telephone, email, est_actif)
VALUES (4, 'EMP-2023-0055', 'ANDRIANARIVELO', 'Patrick', 'M', '1995-09-18', 'Tamatave', 'Malgache', '201023000055', '2013-10-05', 'Tamatave', 'Ankirihiry Nord', 'Tamatave', '+261320100055', 'patrick.andria@galaxam.mg', true)
ON CONFLICT (id_employe) DO NOTHING;

-- EMP 5 : RAKOTONDRASOA Faly Christian (Aide-Comptable, nouvel embauché au 17 Août 2026, prorata paie)
INSERT INTO employe (id_employe, matricule, nom, prenom, sexe, date_naissance, lieu_naissance, nationalite, cin_numero, cin_date_delivrance, cin_lieu_delivrance, adresse, ville, telephone, email, est_actif)
VALUES (5, 'EMP-2026-0089', 'RAKOTONDRASOA', 'Faly Christian', 'M', '2001-07-30', 'Antananarivo', 'Malgache', '101026000089', '2019-08-10', 'Antananarivo', 'Lot III Y 18 67Ha Sud', 'Antananarivo', '+261340100089', 'faly.rakoto@vina-asa.mg', true)
ON CONFLICT (id_employe) DO NOTHING;

SELECT setval('employe_id_employe_seq', (SELECT GREATEST(MAX(id_employe), 5) FROM employe));

-- =====================================================================
-- 5. COMPTES BANCAIRES BNI & ENFANTS À CHARGE (DÉDUCTION IRSA)
-- =====================================================================

-- Comptes BNI
INSERT INTO employe_compte_banque (id_employe, id_banque, titulaire_compte, num_compte_rib, est_principal)
VALUES 
(1, 2, 'RAKOTONDRAZAKA Andry Nirina', '00004 00123 00000000001 45', true),
(2, 2, 'RANDRIAMAMPIONONA Jean Jacques', '00004 00123 00000000002 45', true),
(3, 2, 'RASOANIRINA Voahirana Mialy', '00004 00123 00000000003 45', true),
(4, 2, 'ANDRIANARIVELO Patrick', '00004 00123 00000000004 45', true),
(5, 2, 'RAKOTONDRASOA Faly Christian', '00004 00123 00000000005 45', true)
ON CONFLICT DO NOTHING;

-- Enfants à charge pour déductions IRSA (2 000 Ar par enfant)
-- Emp 1 a 2 enfants (-4 000 Ar IRSA)
INSERT INTO employe_enfant (id_employe, nom, prenom, date_naissance, a_charge)
VALUES 
(1, 'RAKOTONDRAZAKA', 'Ialy', '2012-04-10', true),
(1, 'RAKOTONDRAZAKA', 'Finoana', '2016-09-18', true),
-- Emp 2 a 1 enfant (-2 000 Ar IRSA)
(2, 'RANDRIAMAMPIONONA', 'Kevin', '2018-07-25', true),
-- Emp 3 a 1 enfant (-2 000 Ar IRSA)
(3, 'RAZANAKOTO', 'Aina', '2024-02-14', true)
ON CONFLICT DO NOTHING;

-- =====================================================================
-- 6. HISTORIQUE DES CONTRATS & CARRIÈRES SUR 20 ANS (2006 - 2026)
-- =====================================================================

-- --- EMPLOYE 1 (20 ans de carrière chez GLX TNR) ---
-- Contrat 1 : 2006-2011 (Aide-Comptable)
INSERT INTO contrat_travail (id_contrat, id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_rh, numero_contrat, date_signature, date_debut, date_fin, description)
VALUES (101, 1, 1, 1, 1, 7, 'CT-2006-CDI-001', '2006-01-25', '2006-02-01', '2011-02-28', 'Embauche initiale Aide-Comptable')
ON CONFLICT (id_contrat) DO NOTHING;

INSERT INTO employe_affectation_carriere (id_affectation, id_employe, id_contrat, id_statut_rh, id_motif_mouvement, id_site, id_departement, id_service, id_fonction, id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut, date_fin)
VALUES (101, 1, 101, 7, 1, 1, 1, 1, 4, 2, 1, 450000.00, '2006-02-01', '2011-02-28')
ON CONFLICT (id_affectation) DO NOTHING;

-- Contrat 2 : 2011-2017 (Promotion Chef Comptable)
INSERT INTO contrat_travail (id_contrat, id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_rh, numero_contrat, date_signature, date_debut, date_fin, description)
VALUES (102, 1, 1, 1, 1, 7, 'CT-2011-CDI-002', '2011-02-25', '2011-03-01', '2017-12-31', 'Avenant Promotion Chef Comptable')
ON CONFLICT (id_contrat) DO NOTHING;

INSERT INTO employe_affectation_carriere (id_affectation, id_employe, id_contrat, id_statut_rh, id_motif_mouvement, id_site, id_departement, id_service, id_fonction, id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut, date_fin)
VALUES (102, 1, 102, 7, 2, 1, 1, 1, 3, 2, 1, 1200000.00, '2011-03-01', '2017-12-31')
ON CONFLICT (id_affectation) DO NOTHING;

-- Contrat 3 : 2018-En cours (Promotion DAF Cadre autonome)
INSERT INTO contrat_travail (id_contrat, id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_rh, numero_contrat, date_signature, date_debut, date_fin, description)
VALUES (103, 1, 1, 1, 1, 4, 'CT-2018-CDI-003', '2017-12-20', '2018-01-01', NULL, 'Avenant DAF - Cadre autonome')
ON CONFLICT (id_contrat) DO NOTHING;

INSERT INTO employe_affectation_carriere (id_affectation, id_employe, id_contrat, id_statut_rh, id_motif_mouvement, id_site, id_departement, id_service, id_fonction, id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut, date_fin)
VALUES (103, 1, 103, 4, 2, 1, 1, 1, 2, 1, 1, 3800000.00, '2018-01-01', NULL)
ON CONFLICT (id_affectation) DO NOTHING;

-- --- EMPLOYE 2 (12 ans : 2014 CDD -> 2015 CDI TNR -> 2022 Mutation TMM) ---
-- Contrat 1 : 2014-2015 (CDD Agent Transit TNR)
INSERT INTO contrat_travail (id_contrat, id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_rh, numero_contrat, date_signature, date_debut, date_fin, description)
VALUES (201, 2, 1, 2, 1, 7, 'CT-2014-CDD-001', '2014-05-20', '2014-06-01', '2015-05-31', 'CDD Opérations Transit')
ON CONFLICT (id_contrat) DO NOTHING;

INSERT INTO employe_affectation_carriere (id_affectation, id_employe, id_contrat, id_statut_rh, id_motif_mouvement, id_site, id_departement, id_service, id_fonction, id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut, date_fin)
VALUES (201, 2, 201, 7, 1, 1, 1, 2, 18, 2, 1, 700000.00, '2014-06-01', '2015-05-31')
ON CONFLICT (id_affectation) DO NOTHING;

-- Contrat 2 : 2015-2021 (CDI OPE Manager TNR)
INSERT INTO contrat_travail (id_contrat, id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_rh, numero_contrat, date_signature, date_debut, date_fin, description)
VALUES (202, 2, 1, 1, 1, 7, 'CT-2015-CDI-002', '2015-05-25', '2015-06-01', '2021-12-31', 'CDI OPE Manager Siège TNR')
ON CONFLICT (id_contrat) DO NOTHING;

INSERT INTO employe_affectation_carriere (id_affectation, id_employe, id_contrat, id_statut_rh, id_motif_mouvement, id_site, id_departement, id_service, id_fonction, id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut, date_fin)
VALUES (202, 2, 202, 7, 1, 1, 1, 2, 5, 2, 1, 1100000.00, '2015-06-01', '2021-12-31')
ON CONFLICT (id_affectation) DO NOTHING;

-- Contrat 3 : 2022-En cours (Mutation Tamatave TMM)
INSERT INTO contrat_travail (id_contrat, id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_rh, numero_contrat, date_signature, date_debut, date_fin, description)
VALUES (203, 2, 1, 1, 1, 4, 'CT-2022-CDI-003', '2021-12-15', '2022-01-01', NULL, 'Avenant Mutation Tamatave Port')
ON CONFLICT (id_contrat) DO NOTHING;

INSERT INTO employe_affectation_carriere (id_affectation, id_employe, id_contrat, id_statut_rh, id_motif_mouvement, id_site, id_departement, id_service, id_fonction, id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut, date_fin)
VALUES (203, 2, 203, 4, 3, 2, 1, 2, 5, 2, 1, 1850000.00, '2022-01-01', NULL)
ON CONFLICT (id_affectation) DO NOTHING;

-- --- EMPLOYE 3 (7 ans : CDI Admin RH TNR depuis 2019) ---
INSERT INTO contrat_travail (id_contrat, id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_rh, numero_contrat, date_signature, date_debut, date_fin, description)
VALUES (301, 3, 1, 1, 1, 4, 'CT-2019-CDI-001', '2019-03-25', '2019-04-01', NULL, 'CDI Administration & RH')
ON CONFLICT (id_contrat) DO NOTHING;

INSERT INTO employe_affectation_carriere (id_affectation, id_employe, id_contrat, id_statut_rh, id_motif_mouvement, id_site, id_departement, id_service, id_fonction, id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut, date_fin)
VALUES (301, 3, 301, 4, 1, 1, 1, 1, 16, 2, 1, 1350000.00, '2019-04-01', NULL)
ON CONFLICT (id_affectation) DO NOTHING;

-- --- EMPLOYE 4 (3 ans : CDD 2023 -> CDI 2024 Chauffeur/Transit TMM) ---
INSERT INTO contrat_travail (id_contrat, id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_rh, numero_contrat, date_signature, date_debut, date_fin, description)
VALUES (401, 4, 1, 2, 1, 7, 'CT-2023-CDD-001', '2023-09-20', '2023-10-01', '2024-09-30', 'CDD Initial Chauffeur')
ON CONFLICT (id_contrat) DO NOTHING;

INSERT INTO employe_affectation_carriere (id_affectation, id_employe, id_contrat, id_statut_rh, id_motif_mouvement, id_site, id_departement, id_service, id_fonction, id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut, date_fin)
VALUES (401, 4, 401, 7, 1, 2, 1, 2, 13, 2, 1, 800000.00, '2023-10-01', '2024-09-30')
ON CONFLICT (id_affectation) DO NOTHING;

INSERT INTO contrat_travail (id_contrat, id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_rh, numero_contrat, date_signature, date_debut, date_fin, description)
VALUES (402, 4, 1, 1, 1, 4, 'CT-2024-CDI-002', '2024-09-25', '2024-10-01', NULL, 'CDI Confirmation Chauffeur Logistique')
ON CONFLICT (id_contrat) DO NOTHING;

INSERT INTO employe_affectation_carriere (id_affectation, id_employe, id_contrat, id_statut_rh, id_motif_mouvement, id_site, id_departement, id_service, id_fonction, id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut, date_fin)
VALUES (402, 4, 402, 4, 1, 2, 1, 2, 13, 2, 1, 950000.00, '2024-10-01', NULL)
ON CONFLICT (id_affectation) DO NOTHING;

-- --- EMPLOYE 5 (Nouvel embauché au 17 Août 2026 : VA TNR) ---
INSERT INTO contrat_travail (id_contrat, id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_rh, numero_contrat, date_signature, date_debut, date_fin, duree_essai_mois, date_fin_essai, description)
VALUES (501, 5, 2, 1, 1, 4, 'CT-2026-CDI-005', '2026-08-10', '2026-08-17', NULL, 3, '2026-11-16', 'CDI Embauche Aide-Comptable VA')
ON CONFLICT (id_contrat) DO NOTHING;

INSERT INTO employe_affectation_carriere (id_affectation, id_employe, id_contrat, id_statut_rh, id_motif_mouvement, id_site, id_departement, id_service, id_fonction, id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut, date_fin)
VALUES (501, 5, 501, 4, 1, 1, 1, 1, 4, 2, 1, 1050000.00, '2026-08-17', NULL)
ON CONFLICT (id_affectation) DO NOTHING;

SELECT setval('contrat_travail_id_contrat_seq', 550);
SELECT setval('employe_affectation_carriere_id_affectation_seq', 550);

-- =====================================================================
-- 7. GRAND LIVRE DES CONGÉS & DEMANDE D'AOÛT 2026 (EMPLOYÉ 3)
-- =====================================================================

-- Acquisitions historiques cumulées et bonifications d'ancienneté
-- Emp 1 (20 ans) : Solde net 35j (+4j bonification d'ancienneté)
INSERT INTO mouvement_solde_conge (id_employe, date_mouvement, type_mouvement, nb_jours, commentaire)
VALUES 
(1, '2026-01-01', 'ACQUISITION', 31.0, 'Acquisitions annuelles reportées'),
(1, '2026-01-01', 'BONIFICATION', 4.0, 'Bonification légale 20 ans d ancienneté (+4 jours)')
ON CONFLICT DO NOTHING;

-- Emp 2 (12 ans) : Solde net 28j (+2j bonification d'ancienneté)
INSERT INTO mouvement_solde_conge (id_employe, date_mouvement, type_mouvement, nb_jours, commentaire)
VALUES 
(2, '2026-01-01', 'ACQUISITION', 26.0, 'Acquisitions annuelles reportées'),
(2, '2026-01-01', 'BONIFICATION', 2.0, 'Bonification légale 10 ans d ancienneté (+2 jours)')
ON CONFLICT DO NOTHING;

-- Emp 3 (7 ans) : Solde initial 25j (+1j bonification d'ancienneté)
INSERT INTO mouvement_solde_conge (id_employe, date_mouvement, type_mouvement, nb_jours, commentaire)
VALUES 
(3, '2026-01-01', 'ACQUISITION', 24.0, 'Acquisitions annuelles reportées'),
(3, '2026-01-01', 'BONIFICATION', 1.0, 'Bonification légale 5 ans d ancienneté (+1 jour)')
ON CONFLICT DO NOTHING;

-- Demande de congé annuel validée en Août 2026 pour Emp 3 (du 03/08 au 07/08 = 5 jours ouvrables)
INSERT INTO demande_conge (id_demande_conge, id_employe, id_type_conge, id_statut_demande_conge, date_demande, date_debut, date_fin, nb_jours_decomptes, commentaire, valide_par, date_validation)
VALUES (1, 3, 5, 3, '2026-07-15', '2026-08-03', '2026-08-07', 5.0, 'Congé d été 2026', 1, '2026-07-20 10:00:00')
ON CONFLICT (id_demande_conge) DO NOTHING;

INSERT INTO mouvement_solde_conge (id_employe, id_demande_conge, date_mouvement, type_mouvement, nb_jours, commentaire)
VALUES (3, 1, '2026-08-03', 'PRISE', -5.0, 'Prise congé annuel du 03/08/2026 au 07/08/2026')
ON CONFLICT DO NOTHING;

SELECT setval('demande_conge_id_demande_conge_seq', 10);
SELECT setval('mouvement_solde_conge_id_mouvement_seq', 20);

-- =====================================================================
-- 8. POINTAGE QUOTIDIEN COMPLET D'AOÛT 2026 (Du 01 au 31 Août)
-- =====================================================================

-- A. EMPLOYE 1 (DAF Cadre) : Dispense de pointage sur tous les jours d'Août (id_statut_jour = 10)
INSERT INTO pointage_jour (id_employe, id_periode_paie, id_site, date_pointage, id_statut_jour, version)
SELECT 1, 1, 1, d::date, 10, 1
FROM generate_series('2026-08-01'::date, '2026-08-31'::date, '1 day'::interval) d
ON CONFLICT (id_employe, date_pointage) DO NOTHING;

-- B. EMPLOYE 2 (OPE Manager Tamatave, Site 2 TMM) : Présent du lundi au vendredi
-- Horaires : 08:00 - 12:00 et 13:00 - 17:00 (8h).
-- Mercredi 12 Août : Heures sup (13:00 - 20:00 -> total 11h = 8h normales + 3h sup).
-- Lundi 17 Août : Retard 10 min (arrivée 08:10 -> 10 minutes retard).
-- Vendredi 21 Août : Compensation retard (départ 17:30 -> total 8.5h).
-- Tous les midis ouvrés : Repas pris sur site -> Déduction Tamatave active.
INSERT INTO pointage_jour (id_employe, id_periode_paie, id_site, date_pointage, id_statut_jour, heure_entree_matin, heure_sortie_matin, heure_entree_am, heure_sortie_am, heures_matin, heures_am, total_heures, minutes_retard, montant_total_indemnites, version)
SELECT 
    2, 1, 2, d::date, 1,
    CASE 
        WHEN d::date = '2026-08-17' THEN '08:10:00'::time -- Retard 10 min
        ELSE '08:00:00'::time 
    END,
    '12:00:00'::time,
    '13:00:00'::time,
    CASE 
        WHEN d::date = '2026-08-12' THEN '20:00:00'::time -- +3h sup
        WHEN d::date = '2026-08-21' THEN '17:30:00'::time -- Compensation retard
        ELSE '17:00:00'::time 
    END,
    CASE WHEN d::date = '2026-08-17' THEN 3.83 ELSE 4.00 END,
    CASE WHEN d::date = '2026-08-12' THEN 7.00 WHEN d::date = '2026-08-21' THEN 4.50 ELSE 4.00 END,
    CASE WHEN d::date = '2026-08-12' THEN 11.00 WHEN d::date = '2026-08-17' THEN 7.83 WHEN d::date = '2026-08-21' THEN 8.50 ELSE 8.00 END,
    CASE WHEN d::date = '2026-08-17' THEN 10 ELSE 0 END,
    2000.00, 1
FROM generate_series('2026-08-01'::date, '2026-08-31'::date, '1 day'::interval) d
WHERE EXTRACT(DOW FROM d) BETWEEN 1 AND 5 AND d::date != '2026-08-15'
ON CONFLICT (id_employe, date_pointage) DO NOTHING;

-- C. EMPLOYE 3 (Admin RH TNR, Site 1 TNR) :
-- Semaine du 03 au 07 Août : CONGE (Statut 5)
INSERT INTO pointage_jour (id_employe, id_periode_paie, id_site, date_pointage, id_statut_jour, id_demande_conge, version)
SELECT 3, 1, 1, d::date, 5, 1, 1
FROM generate_series('2026-08-03'::date, '2026-08-07'::date, '1 day'::interval) d
ON CONFLICT (id_employe, date_pointage) DO NOTHING;

-- Reste du mois (du 10 au 31 Août) : Présente normalement
INSERT INTO pointage_jour (id_employe, id_periode_paie, id_site, date_pointage, id_statut_jour, heure_entree_matin, heure_sortie_matin, heure_entree_am, heure_sortie_am, heures_matin, heures_am, total_heures, montant_total_indemnites, version)
SELECT 
    3, 1, 1, d::date, 1,
    '08:00:00'::time, '12:00:00'::time, '13:00:00'::time, '17:00:00'::time,
    4.00, 4.00, 8.00,
    2000.00, 1
FROM generate_series('2026-08-10'::date, '2026-08-31'::date, '1 day'::interval) d
WHERE EXTRACT(DOW FROM d) BETWEEN 1 AND 5 AND d::date != '2026-08-15'
ON CONFLICT (id_employe, date_pointage) DO NOTHING;

-- D. EMPLOYE 4 (Chauffeur / Transit TMM, Site 2 TMM) : Travail intensif et nuit en Semaine 35
-- Semaines normales (01-21 Août & 31 Août) : 08:00-12:00, 13:00-17:00
INSERT INTO pointage_jour (id_employe, id_periode_paie, id_site, date_pointage, id_statut_jour, heure_entree_matin, heure_sortie_matin, heure_entree_am, heure_sortie_am, heures_matin, heures_am, total_heures, montant_total_indemnites, version)
SELECT 
    4, 1, 2, d::date, 1,
    '08:00:00'::time, '12:00:00'::time, '13:00:00'::time, '17:00:00'::time,
    4.00, 4.00, 8.00,
    2000.00, 1
FROM generate_series('2026-08-01'::date, '2026-08-31'::date, '1 day'::interval) d
WHERE EXTRACT(DOW FROM d) BETWEEN 1 AND 5 
  AND d::date NOT BETWEEN '2026-08-24' AND '2026-08-28'
  AND d::date != '2026-08-15'
ON CONFLICT (id_employe, date_pointage) DO NOTHING;

-- Semaine 35 (du 24 au 28 Août) : Déchargements portuaires nocturnes
-- Lundi 24 : 08:00-12:00 (4h) & 13:00-23:30 (10.5h) -> total 14.5h, dont 1.5h de nuit (>22h), 2 paniers repas
INSERT INTO pointage_jour (id_employe, id_periode_paie, id_site, date_pointage, id_statut_jour, heure_entree_matin, heure_sortie_matin, heure_entree_am, heure_sortie_am, heures_matin, heures_am, total_heures, heures_nuit, montant_total_indemnites, version)
VALUES (4, 1, 2, '2026-08-24', 1, '08:00:00', '12:00:00', '13:00:00', '23:30:00', 4.00, 10.50, 14.50, 1.50, 4000.00, 1)
ON CONFLICT (id_employe, date_pointage) DO NOTHING;

-- Mardi 25 : 08:00-12:00 (4h) & 13:00-23:00 (10h) -> total 14h, dont 1h de nuit (>22h), 2 paniers repas
INSERT INTO pointage_jour (id_employe, id_periode_paie, id_site, date_pointage, id_statut_jour, heure_entree_matin, heure_sortie_matin, heure_entree_am, heure_sortie_am, heures_matin, heures_am, total_heures, heures_nuit, montant_total_indemnites, version)
VALUES (4, 1, 2, '2026-08-25', 1, '08:00:00', '12:00:00', '13:00:00', '23:00:00', 4.00, 10.00, 14.00, 1.00, 4000.00, 1)
ON CONFLICT (id_employe, date_pointage) DO NOTHING;

-- Mercredi 26 & Jeudi 27 : 08:00-12:00 & 13:00-18:00 (9h/jour)
INSERT INTO pointage_jour (id_employe, id_periode_paie, id_site, date_pointage, id_statut_jour, heure_entree_matin, heure_sortie_matin, heure_entree_am, heure_sortie_am, heures_matin, heures_am, total_heures, montant_total_indemnites, version)
VALUES 
(4, 1, 2, '2026-08-26', 1, '08:00:00', '12:00:00', '13:00:00', '18:00:00', 4.00, 5.00, 9.00, 2000.00, 1),
(4, 1, 2, '2026-08-27', 1, '08:00:00', '12:00:00', '13:00:00', '18:00:00', 4.00, 5.00, 9.00, 2000.00, 1),
-- Vendredi 28 : 08:00-12:00 & 13:00-17:00 (8h)
(4, 1, 2, '2026-08-28', 1, '08:00:00', '12:00:00', '13:00:00', '17:00:00', 4.00, 4.00, 8.00, 2000.00, 1)
ON CONFLICT (id_employe, date_pointage) DO NOTHING;

-- E. EMPLOYE 5 (Aide-Comptable VA, Site 1 TNR) : Présent uniquement à partir du 17 Août
INSERT INTO pointage_jour (id_employe, id_periode_paie, id_site, date_pointage, id_statut_jour, heure_entree_matin, heure_sortie_matin, heure_entree_am, heure_sortie_am, heures_matin, heures_am, total_heures, montant_total_indemnites, version)
SELECT 
    5, 1, 1, d::date, 1,
    '08:00:00'::time, '12:00:00'::time, '13:00:00'::time, '17:00:00'::time,
    4.00, 4.00, 8.00,
    2000.00, 1
FROM generate_series('2026-08-17'::date, '2026-08-31'::date, '1 day'::interval) d
WHERE EXTRACT(DOW FROM d) BETWEEN 1 AND 5
ON CONFLICT (id_employe, date_pointage) DO NOTHING;

-- F. FÉRIÉ DU 15 AOÛT CHÔMÉ POUR TOUS
INSERT INTO pointage_jour (id_employe, id_periode_paie, id_site, date_pointage, id_statut_jour, version)
VALUES 
(2, 1, 2, '2026-08-15', 7, 1),
(3, 1, 1, '2026-08-15', 7, 1),
(4, 1, 2, '2026-08-15', 7, 1)
ON CONFLICT (id_employe, date_pointage) DO NOTHING;

-- G. ALIMENTATION DES INDEMNITÉS JOURNALIÈRES DANS LA TABLE DÉDIÉE (pointage_indemnite_jour)
INSERT INTO pointage_indemnite_jour (id_pointage, id_indemnite, fraction, quantite, montant_calcule, accorde)
SELECT p.id_pointage, 2, 'JOUR_ENTIER', CASE WHEN p.montant_total_indemnites >= 4000.00 THEN 2.0 ELSE 1.0 END, p.montant_total_indemnites, true
FROM pointage_jour p
WHERE p.montant_total_indemnites > 0
ON CONFLICT DO NOTHING;

SELECT setval('pointage_jour_id_pointage_seq', (SELECT GREATEST(MAX(id_pointage), 1) FROM pointage_jour));
SELECT setval('pointage_indemnite_jour_id_pointage_indemnite_seq', (SELECT GREATEST(MAX(id_pointage_indemnite), 1) FROM pointage_indemnite_jour));

