-- =========================================================================
-- SEED SCRIPT DÉTERMINISTE POUR TESTS E2E BROWSER — SIRH GALAXAM
-- Base cible : sirh_galaxam_test
-- =========================================================================

BEGIN;

-- 1. Nettoyage des tables transactionnelles
TRUNCATE TABLE bulletin_paie_ligne CASCADE;
TRUNCATE TABLE bulletin_paie CASCADE;
TRUNCATE TABLE paie_stc CASCADE;
TRUNCATE TABLE paie_pret_echeance CASCADE;
TRUNCATE TABLE paie_pret CASCADE;
TRUNCATE TABLE paie_avance_acompte CASCADE;
TRUNCATE TABLE paie_honoraire_prestation CASCADE;
TRUNCATE TABLE pointage_indemnite_jour CASCADE;
TRUNCATE TABLE pointage_jour CASCADE;
TRUNCATE TABLE pointage_semaine_calcule CASCADE;
TRUNCATE TABLE pointage_cumul_indemnite CASCADE;
TRUNCATE TABLE pointage_cumul_periode CASCADE;
TRUNCATE TABLE demande_conge CASCADE;
TRUNCATE TABLE mouvement_solde_conge CASCADE;
TRUNCATE TABLE conge_taux_applicable CASCADE;
TRUNCATE TABLE conge_journal_droit CASCADE;
TRUNCATE TABLE conge_periode_reference CASCADE;
TRUNCATE TABLE workflow_rh CASCADE;
TRUNCATE TABLE contrat_periode_essai CASCADE;
TRUNCATE TABLE employe_affectation_carriere CASCADE;
TRUNCATE TABLE contrat_travail CASCADE;
TRUNCATE TABLE employe_enfant CASCADE;
TRUNCATE TABLE employe_compte_banque CASCADE;
TRUNCATE TABLE employe CASCADE;
TRUNCATE TABLE notification_rh CASCADE;
TRUNCATE TABLE journal_connexion CASCADE;
TRUNCATE TABLE audit_log CASCADE;
TRUNCATE TABLE periode_paie CASCADE;

-- 2. Utilisateurs de test E2E avec mots de passe certifiés
-- daf@galaxam.com / Test-Admin-Only-Password-2026!
-- pointage@galaxam.com / Test-Pointage-Only-Password-2026!
INSERT INTO utilisateur (id_utilisateur, identifiant, mot_de_passe_hache, id_role, est_actif, updated_at)
VALUES 
  (1, 'daf@galaxam.com', '$2a$10$sqdPRtowy53e2XfENzXzE.n2dKUuLXUyLi74CcDdfvsHii9Vk0lcO', (SELECT id_role FROM ref_role WHERE code_role = 'ROLE_ADMIN'), true, CURRENT_TIMESTAMP),
  (2, 'pointage@galaxam.com', '$2a$10$hMDG7fsGU91nSCH0c0r..OjHrJ1G5KAEz3WaCDCyVmkufVKq2UZyS', (SELECT id_role FROM ref_role WHERE code_role = 'ROLE_AGENT_POINTAGE'), true, CURRENT_TIMESTAMP)
ON CONFLICT (identifiant) DO UPDATE 
SET mot_de_passe_hache = EXCLUDED.mot_de_passe_hache,
    id_role = EXCLUDED.id_role,
    est_actif = true;

-- Assurer les permissions pour ROLE_AGENT_POINTAGE sur le module POINTAGE (READ=true, WRITE=true, VALIDATE=false)
INSERT INTO role_permission (id_role, id_module, droit_lecture, droit_ecriture, droit_validation)
VALUES (
  (SELECT id_role FROM ref_role WHERE code_role = 'ROLE_AGENT_POINTAGE'),
  (SELECT id_module FROM ref_module WHERE code_module = 'POINTAGE'),
  true, true, false
)
ON CONFLICT (id_role, id_module) DO UPDATE
SET droit_lecture = true, droit_ecriture = true, droit_validation = false;

-- 3. Période de paie active de test E2E (Septembre 2026 E2E : 01/09/2026 au 15/09/2026 terminée)
INSERT INTO periode_paie (id_periode_paie, code, libelle, date_debut, date_fin, id_statut_periode, version)
VALUES 
  (101, 'PER-2026-09-E2E', 'Période Septembre 2026 E2E', DATE '2026-09-01', DATE '2026-09-15', (SELECT id_statut_periode FROM ref_statut_periode_paie WHERE code = 'EN_COURS'), 1),
  (102, 'PER-2026-08-CLOT', 'Période Août 2026 Clôturée', DATE '2026-08-01', DATE '2026-08-31', (SELECT id_statut_periode FROM ref_statut_periode_paie WHERE code = 'CLOTUREE'), 1)
ON CONFLICT (id_periode_paie) DO UPDATE
SET code = EXCLUDED.code, date_debut = EXCLUDED.date_debut, date_fin = EXCLUDED.date_fin, id_statut_periode = EXCLUDED.id_statut_periode, version = 1;

-- 4. Profils Synthétiques de Test E2E

-- SYN_EMP001_STANDARD (RAZAFY Jean)
INSERT INTO employe (id_employe, matricule, nom, prenom, sexe, date_naissance, lieu_naissance, cin_numero, cin_date_delivrance, cin_lieu_delivrance, adresse, telephone, email, est_actif)
VALUES (1, 'EMP001', 'RAZAFY', 'Jean', 'M', DATE '1990-01-01', 'Antananarivo', '101000111222', DATE '2010-01-01', 'Antananarivo', 'Lot II Test 1', '0340000001', 'emp001@galaxam.com', true);

INSERT INTO contrat_travail (id_contrat, id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_rh, numero_contrat, date_debut, comptabilise_effectif)
VALUES (1, 1, 1, (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDI'), (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT'), (SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'ACTIF'), 'CT-EMP001', DATE '2024-01-15', true);

INSERT INTO employe_affectation_carriere (id_affectation, id_employe, id_contrat, id_site, id_statut_rh, id_motif_mouvement, id_departement, id_service, id_fonction, id_categorie_professionnelle, id_mode_calcul_paie, date_debut, salaire_base)
VALUES (1, 1, 1, 1, (SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'ACTIF'), (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'), (SELECT id_departement FROM ref_departement LIMIT 1), (SELECT id_service FROM ref_service LIMIT 1), (SELECT id_fonction FROM ref_fonction WHERE code_fonction = 'AIDE_COMPTABLE'), (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'A'), (SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie LIMIT 1), DATE '2024-01-15', 400000.00);

-- SYN_EMP002_ABSENCE (RABE Paul)
INSERT INTO employe (id_employe, matricule, nom, prenom, sexe, date_naissance, lieu_naissance, cin_numero, cin_date_delivrance, cin_lieu_delivrance, adresse, telephone, email, est_actif)
VALUES (2, 'EMP002', 'RABE', 'Paul', 'M', DATE '1988-05-12', 'Antananarivo', '101000111223', DATE '2010-01-01', 'Antananarivo', 'Lot II Test 2', '0340000002', 'emp002@galaxam.com', true);

INSERT INTO contrat_travail (id_contrat, id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_rh, numero_contrat, date_debut, comptabilise_effectif)
VALUES (2, 2, 1, (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDI'), (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT'), (SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'ACTIF'), 'CT-EMP002', DATE '2023-06-01', true);

INSERT INTO employe_affectation_carriere (id_affectation, id_employe, id_contrat, id_site, id_statut_rh, id_motif_mouvement, id_departement, id_service, id_fonction, id_categorie_professionnelle, id_mode_calcul_paie, date_debut, salaire_base)
VALUES (2, 2, 2, 1, (SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'ACTIF'), (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'), (SELECT id_departement FROM ref_departement LIMIT 1), (SELECT id_service FROM ref_service LIMIT 1), (SELECT id_fonction FROM ref_fonction WHERE code_fonction = 'AIDE_COMPTABLE'), (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'A'), (SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie LIMIT 1), DATE '2023-06-01', 500000.00);

-- SYN_EMP003_CONGE (RASOA Marie)
INSERT INTO employe (id_employe, matricule, nom, prenom, sexe, date_naissance, lieu_naissance, cin_numero, cin_date_delivrance, cin_lieu_delivrance, adresse, telephone, email, est_actif)
VALUES (3, 'EMP003', 'RASOA', 'Marie', 'F', DATE '1992-08-20', 'Antananarivo', '101000111224', DATE '2012-01-01', 'Antananarivo', 'Lot II Test 3', '0340000003', 'emp003@galaxam.com', true);

INSERT INTO contrat_travail (id_contrat, id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_rh, numero_contrat, date_debut, comptabilise_effectif)
VALUES (3, 3, 1, (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDI'), (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT'), (SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'ACTIF'), 'CT-EMP003', DATE '2023-01-10', true);

INSERT INTO employe_affectation_carriere (id_affectation, id_employe, id_contrat, id_site, id_statut_rh, id_motif_mouvement, id_departement, id_service, id_fonction, id_categorie_professionnelle, id_mode_calcul_paie, date_debut, salaire_base)
VALUES (3, 3, 3, 1, (SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'ACTIF'), (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'), (SELECT id_departement FROM ref_departement LIMIT 1), (SELECT id_service FROM ref_service LIMIT 1), (SELECT id_fonction FROM ref_fonction WHERE code_fonction = 'AIDE_COMPTABLE'), (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'A'), (SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie LIMIT 1), DATE '2023-01-10', 450000.00);

-- Solde initial congé pour EMP003 (Solde: 15.0 jours, TCONGE: 15000 Ar)
INSERT INTO mouvement_solde_conge (id_employe, type_mouvement, nb_jours, date_mouvement, commentaire)
VALUES (3, 'ACQUISITION', 15.0, DATE '2026-09-01', 'Solde initial E2E');

INSERT INTO conge_taux_applicable (id_employe, taux_journalier, date_effet, source_provenance, commentaire)
VALUES (3, 15000.00, DATE '2026-01-01', 'REPRISE_CUTOVER', 'TCONGE initial E2E');

-- SYN_EMP006_COMPLEX (RAKOTO_COMPLEX Herizo)
INSERT INTO employe (id_employe, matricule, nom, prenom, sexe, date_naissance, lieu_naissance, cin_numero, cin_date_delivrance, cin_lieu_delivrance, adresse, telephone, email, est_actif)
VALUES (6, 'EMP006', 'RAKOTO_COMPLEX', 'Herizo', 'M', DATE '1985-03-14', 'Tamatave', '101000111226', DATE '2005-01-01', 'Tamatave', 'Lot II Test 6', '0340000006', 'emp006@galaxam.com', true);

INSERT INTO contrat_travail (id_contrat, id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_rh, numero_contrat, date_debut, comptabilise_effectif)
VALUES (6, 6, 1, (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDI'), (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT'), (SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'ACTIF'), 'CT-EMP006', DATE '2022-01-01', true);

INSERT INTO employe_affectation_carriere (id_affectation, id_employe, id_contrat, id_site, id_statut_rh, id_motif_mouvement, id_departement, id_service, id_fonction, id_categorie_professionnelle, id_mode_calcul_paie, date_debut, salaire_base)
VALUES (6, 6, 6, 2, (SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'ACTIF'), (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'), (SELECT id_departement FROM ref_departement LIMIT 1), (SELECT id_service FROM ref_service LIMIT 1), (SELECT id_fonction FROM ref_fonction WHERE code_fonction = 'CHEF_COMPTABLE'), (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'CADRE'), (SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie LIMIT 1), DATE '2022-01-01', 600000.00);

-- Solde initial congé pour EMP006 (Solde: 12.0 jours, TCONGE: 20000 Ar)
INSERT INTO mouvement_solde_conge (id_employe, type_mouvement, nb_jours, date_mouvement, commentaire)
VALUES (6, 'ACQUISITION', 12.0, DATE '2026-09-01', 'Solde initial E2E EMP006');

INSERT INTO conge_taux_applicable (id_employe, taux_journalier, date_effet, source_provenance, commentaire)
VALUES (6, 20000.00, DATE '2026-01-01', 'REPRISE_CUTOVER', 'TCONGE initial E2E EMP006');

-- 5. Paramètres fiscaux et sociaux indispensables
INSERT INTO parametre_global (code_parametre, categorie, valeur, description, date_debut, est_actif)
VALUES 
  ('TAUX_ISI', 'FISCAL', '5.00', 'Taux ISI de test', DATE '2000-01-01', true),
  ('SMIC_NATIONAL', 'SALARIAL', '262680.00', 'SMIC National Madagascar', DATE '2024-01-01', true),
  ('SME_NATIONAL', 'SALARIAL', '262680.00', 'SME National', DATE '2024-01-01', true)
ON CONFLICT (code_parametre, date_debut) DO UPDATE 
SET valeur = EXCLUDED.valeur, est_actif = true;

COMMIT;
