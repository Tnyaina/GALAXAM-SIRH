-- =====================================================================
-- GALAXAM SIRH — MODULE 03 : GESTION DU PERSONNEL & CONTRATS
-- Script canonique final (Zéro ALTER TABLE, Zéro OWNER, Zéro public.)
-- Sources : 26-08-06-sp1-emp.sql, 26-08-10-sp1-contrat.sql,
--           26-08-11-sp1-emp-doc.sql, 26-08-12-sp2-notifications.sql,
--           26-09-14-sp6-vue-employe-actif-courant.sql, 26-09-16-sp8-indexes-foreign-keys.sql
-- =====================================================================

-- 1. Nettoyage préalable des vues et tables (ordre inverse des dépendances)
DROP VIEW IF EXISTS v_employe_actif_courant CASCADE;
DROP VIEW IF EXISTS v_historique_carriere_global CASCADE;
DROP VIEW IF EXISTS v_workflow_rh_cpl CASCADE;
DROP VIEW IF EXISTS v_document_rh_cpl CASCADE;
DROP VIEW IF EXISTS v_employe_cpl CASCADE;

DROP TABLE IF EXISTS notification_rh CASCADE;
DROP TABLE IF EXISTS document_rh CASCADE;
DROP TABLE IF EXISTS ref_type_document CASCADE;
DROP TABLE IF EXISTS employe_affectation_carriere CASCADE;
DROP TABLE IF EXISTS workflow_rh CASCADE;
DROP TABLE IF EXISTS contrat_periode_essai CASCADE;
DROP TABLE IF EXISTS contrat_travail CASCADE;
DROP TABLE IF EXISTS employe_compte_banque CASCADE;
DROP TABLE IF EXISTS employe_contact_urgence CASCADE;
DROP TABLE IF EXISTS employe_enfant CASCADE;
DROP TABLE IF EXISTS employe_conjoint CASCADE;
DROP TABLE IF EXISTS employe CASCADE;

DROP TYPE IF EXISTS lien_parente_enum CASCADE;
DROP TYPE IF EXISTS motif_desactivation_enum CASCADE;
DROP TYPE IF EXISTS situation_familiale_enum CASCADE;
DROP TYPE IF EXISTS sexe_enum CASCADE;

-- 2. Énumérations Métier RH
CREATE TYPE sexe_enum AS ENUM ('M', 'F');
CREATE TYPE situation_familiale_enum AS ENUM (
    'Celibataire', 'Marie', 'Divorce', 'Veuf',
    'CELIBATAIRE', 'MARIE', 'DIVORCE', 'VEUF'
);
CREATE TYPE motif_desactivation_enum AS ENUM (
    'Demission', 'Licenciement', 'Fin_contrat', 'Retraite', 'Deces', 'Autre'
);
CREATE TYPE lien_parente_enum AS ENUM (
    'Pere', 'Mere', 'Conjoint', 'Frere', 'Soeur', 'Enfant', 'Autre'
);

-- 3. Fiche Employé Fondamentale
CREATE TABLE employe (
    id_employe SERIAL PRIMARY KEY,
    matricule VARCHAR(20) NOT NULL UNIQUE,
    nom VARCHAR(100) NOT NULL,
    prenom VARCHAR(100) NOT NULL,
    sexe sexe_enum NOT NULL,
    date_naissance DATE NOT NULL,
    lieu_naissance VARCHAR(100) NOT NULL,
    nationalite VARCHAR(50) DEFAULT 'Malgache',
    pere_nom_prenom VARCHAR(150),
    mere_nom_prenom VARCHAR(150),
    situation_familiale situation_familiale_enum,
    cin_numero VARCHAR(30) NOT NULL UNIQUE,
    cin_date_delivrance DATE NOT NULL,
    cin_lieu_delivrance VARCHAR(100) NOT NULL,
    cin_duplicata_date DATE,
    cin_duplicata_lieu VARCHAR(100),
    permis_conduire_numero VARCHAR(50),
    permis_conduire_date_delivrance DATE,
    permis_conduire_lieu_delivrance VARCHAR(100),
    adresse TEXT NOT NULL,
    ville VARCHAR(100),
    telephone VARCHAR(30) NOT NULL,
    email VARCHAR(150),
    num_affiliation_cnaps VARCHAR(30) UNIQUE,
    photo_url VARCHAR(255),
    date_declaration DATE,
    est_actif BOOLEAN DEFAULT TRUE,
    date_desactivation TIMESTAMP,
    motif_desactivation motif_desactivation_enum,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_employe_nom_prenom ON employe(nom, prenom);
CREATE INDEX idx_employe_matricule ON employe(matricule);
CREATE INDEX idx_employe_cin ON employe(cin_numero);

-- 4. Satellites Familiaux & Personnels
CREATE TABLE employe_conjoint (
    id_conjoint SERIAL PRIMARY KEY,
    id_employe INTEGER NOT NULL REFERENCES employe(id_employe) ON DELETE CASCADE,
    nom VARCHAR(100) NOT NULL,
    prenom VARCHAR(100) NOT NULL,
    date_naissance DATE,
    lieu_naissance VARCHAR(100),
    societe VARCHAR(100),
    fonction VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_employe_conjoint_emp ON employe_conjoint(id_employe);

CREATE TABLE employe_enfant (
    id_enfant SERIAL PRIMARY KEY,
    id_employe INTEGER NOT NULL REFERENCES employe(id_employe) ON DELETE CASCADE,
    nom VARCHAR(100) NOT NULL,
    prenom VARCHAR(100) NOT NULL,
    sexe sexe_enum,
    date_naissance DATE NOT NULL,
    lieu_naissance VARCHAR(100),
    a_charge BOOLEAN DEFAULT TRUE,
    est_etudiant BOOLEAN DEFAULT FALSE,
    date_fin_scolarite DATE,
    est_handicape BOOLEAN DEFAULT FALSE,
    certificat_fourni BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_employe_enfant_emp ON employe_enfant(id_employe);

CREATE TABLE employe_contact_urgence (
    id_contact SERIAL PRIMARY KEY,
    id_employe INTEGER NOT NULL REFERENCES employe(id_employe) ON DELETE CASCADE,
    nom VARCHAR(100) NOT NULL,
    prenom VARCHAR(100) NOT NULL,
    lien_parente lien_parente_enum,
    adresse TEXT,
    telephone VARCHAR(30) NOT NULL,
    societe VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_employe_contact_urgence_emp ON employe_contact_urgence(id_employe);

CREATE TABLE employe_compte_banque (
    id_compte_banque SERIAL PRIMARY KEY,
    id_employe INTEGER NOT NULL REFERENCES employe(id_employe) ON DELETE CASCADE,
    id_banque INTEGER NOT NULL REFERENCES ref_banque(id_banque),
    agence VARCHAR(100),
    num_compte_rib VARCHAR(50) NOT NULL,
    titulaire_compte VARCHAR(150),
    est_principal BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uq_compte_principal_employe ON employe_compte_banque(id_employe) WHERE est_principal = TRUE;
CREATE INDEX idx_employe_compte_banque_banque ON employe_compte_banque(id_banque);

-- 5. Contrats de Travail
CREATE TABLE contrat_travail (
    id_contrat SERIAL PRIMARY KEY,
    id_employe INTEGER NOT NULL REFERENCES employe(id_employe) ON DELETE CASCADE,
    id_filiale INTEGER NOT NULL REFERENCES ref_filiale(id_filiale),
    id_type_contrat INTEGER NOT NULL REFERENCES ref_type_contrat(id_type_contrat),
    id_type_intervenant INTEGER NOT NULL REFERENCES ref_type_intervenant(id_type_intervenant),
    id_statut_rh INTEGER NOT NULL REFERENCES ref_statut_rh(id_statut_rh),
    id_type_evenement_initial INTEGER REFERENCES ref_type_evenement_rh(id_type_evenement_rh),
    id_contrat_precedent INTEGER REFERENCES contrat_travail(id_contrat),
    numero_contrat VARCHAR(50) UNIQUE,
    date_signature DATE,
    date_debut DATE NOT NULL,
    date_fin DATE,
    duree_essai_mois INTEGER,
    date_fin_essai DATE,
    comptabilise_effectif BOOLEAN DEFAULT TRUE,
    id_motif_sortie INTEGER REFERENCES ref_motif_sortie(id_motif_sortie),
    description TEXT,
    commentaire TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_contrat_employe ON contrat_travail(id_employe);
CREATE INDEX idx_contrat_statut ON contrat_travail(id_statut_rh);
CREATE INDEX idx_contrat_travail_precedent ON contrat_travail(id_contrat_precedent);
CREATE INDEX idx_contrat_travail_filiale ON contrat_travail(id_filiale);
CREATE INDEX idx_contrat_travail_motif_sortie ON contrat_travail(id_motif_sortie);
CREATE INDEX idx_contrat_travail_type_contrat ON contrat_travail(id_type_contrat);
CREATE INDEX idx_contrat_travail_evt_init ON contrat_travail(id_type_evenement_initial);
CREATE INDEX idx_contrat_travail_type_interv ON contrat_travail(id_type_intervenant);

CREATE TABLE contrat_periode_essai (
    id_periode_essai SERIAL PRIMARY KEY,
    id_contrat INTEGER NOT NULL REFERENCES contrat_travail(id_contrat) ON DELETE CASCADE,
    numero_periode INTEGER NOT NULL DEFAULT 1,
    date_debut DATE NOT NULL,
    date_fin_prevue DATE NOT NULL,
    date_fin_reelle DATE,
    statut VARCHAR(30) NOT NULL DEFAULT 'EN_COURS',
    motif_renouvellement TEXT,
    motif_rupture TEXT,
    commentaire TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_statut_periode_essai CHECK (statut IN ('EN_COURS', 'CONFIRMEE', 'RENOUVELEE', 'ROMPUE')),
    CONSTRAINT chk_numero_periode CHECK (numero_periode >= 1),
    CONSTRAINT uk_contrat_periode UNIQUE (id_contrat, numero_periode)
);

CREATE INDEX idx_contrat_periode_essai_contrat ON contrat_periode_essai(id_contrat);

-- 6. Affectations de Carrière
CREATE TABLE employe_affectation_carriere (
    id_affectation SERIAL PRIMARY KEY,
    id_employe INTEGER NOT NULL REFERENCES employe(id_employe) ON DELETE CASCADE,
    id_contrat INTEGER NOT NULL REFERENCES contrat_travail(id_contrat) ON DELETE CASCADE,
    id_statut_rh INTEGER NOT NULL REFERENCES ref_statut_rh(id_statut_rh),
    id_motif_mouvement INTEGER NOT NULL REFERENCES ref_motif_mouvement(id_motif_mouvement),
    id_site INTEGER NOT NULL REFERENCES ref_site(id_site),
    id_departement INTEGER NOT NULL REFERENCES ref_departement(id_departement),
    id_service INTEGER NOT NULL REFERENCES ref_service(id_service),
    id_fonction INTEGER NOT NULL REFERENCES ref_fonction(id_fonction),
    id_responsable_hierarchique INTEGER REFERENCES employe(id_employe),
    id_categorie_professionnelle INTEGER NOT NULL REFERENCES ref_categorie_professionnelle(id_categorie_professionnelle),
    id_mode_calcul_paie INTEGER NOT NULL REFERENCES ref_mode_calcul_paie(id_mode_calcul_paie),
    salaire_base NUMERIC(20,2) NOT NULL,
    date_debut DATE NOT NULL,
    date_fin DATE,
    description TEXT,
    commentaire TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_salaire_base_positif CHECK (salaire_base > 0)
);

CREATE INDEX idx_affectation_employe ON employe_affectation_carriere(id_employe);
CREATE INDEX idx_affectation_contrat ON employe_affectation_carriere(id_contrat);
CREATE INDEX idx_affectation_statut ON employe_affectation_carriere(id_statut_rh);
CREATE INDEX idx_eac_site ON employe_affectation_carriere(id_site);
CREATE INDEX idx_eac_departement ON employe_affectation_carriere(id_departement);
CREATE INDEX idx_eac_service ON employe_affectation_carriere(id_service);
CREATE INDEX idx_eac_fonction ON employe_affectation_carriere(id_fonction);
CREATE INDEX idx_eac_responsable_hie ON employe_affectation_carriere(id_responsable_hierarchique);
CREATE INDEX idx_eac_categorie_pro ON employe_affectation_carriere(id_categorie_professionnelle);
CREATE INDEX idx_eac_mode_calcul_paie ON employe_affectation_carriere(id_mode_calcul_paie);
CREATE INDEX idx_eac_motif_mouvement ON employe_affectation_carriere(id_motif_mouvement);

-- 7. Workflows de Transition RH
CREATE TABLE workflow_rh (
    id_workflow SERIAL PRIMARY KEY,
    id_employe INTEGER NOT NULL REFERENCES employe(id_employe) ON DELETE CASCADE,
    id_type_evenement_rh INTEGER NOT NULL REFERENCES ref_type_evenement_rh(id_type_evenement_rh),
    id_statut_rh INTEGER NOT NULL REFERENCES ref_statut_rh(id_statut_rh),
    id_contrat_source INTEGER REFERENCES contrat_travail(id_contrat),
    id_contrat_cible INTEGER REFERENCES contrat_travail(id_contrat),
    id_affectation_source INTEGER REFERENCES employe_affectation_carriere(id_affectation),
    id_affectation_cible INTEGER REFERENCES employe_affectation_carriere(id_affectation),
    date_demande DATE NOT NULL DEFAULT CURRENT_DATE,
    date_effet DATE NOT NULL,
    motif TEXT,
    commentaire TEXT,
    id_demandeur INTEGER REFERENCES utilisateur(id_utilisateur),
    id_valideur INTEGER REFERENCES utilisateur(id_utilisateur),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_workflow_rh_employe ON workflow_rh(id_employe);
CREATE INDEX idx_workflow_rh_type_evenement ON workflow_rh(id_type_evenement_rh);
CREATE INDEX idx_workflow_rh_statut_rh ON workflow_rh(id_statut_rh);
CREATE INDEX idx_workflow_rh_contrat_source ON workflow_rh(id_contrat_source);
CREATE INDEX idx_workflow_rh_contrat_cible ON workflow_rh(id_contrat_cible);
CREATE INDEX idx_workflow_rh_affectation_source ON workflow_rh(id_affectation_source);
CREATE INDEX idx_workflow_rh_affectation_cible ON workflow_rh(id_affectation_cible);
CREATE INDEX idx_workflow_rh_demandeur ON workflow_rh(id_demandeur);
CREATE INDEX idx_workflow_rh_valideur ON workflow_rh(id_valideur);

-- 8. Gestion Documentaire RH
CREATE TABLE ref_type_document (
    id_type_document SERIAL PRIMARY KEY,
    libelle VARCHAR(100) NOT NULL UNIQUE,
    categorie VARCHAR(50) NOT NULL,
    description TEXT,
    obligatoire BOOLEAN DEFAULT FALSE,
    extension_autorisee VARCHAR(100) DEFAULT 'pdf,jpg,jpeg,png',
    taille_max_mo INTEGER DEFAULT 10,
    actif BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE document_rh (
    id_document SERIAL PRIMARY KEY,
    id_employe INTEGER NOT NULL REFERENCES employe(id_employe) ON DELETE CASCADE,
    id_type_document INTEGER NOT NULL REFERENCES ref_type_document(id_type_document),
    id_contrat INTEGER REFERENCES contrat_travail(id_contrat) ON DELETE SET NULL,
    id_workflow INTEGER REFERENCES workflow_rh(id_workflow) ON DELETE SET NULL,
    nom_original VARCHAR(255) NOT NULL,
    nom_stockage VARCHAR(255) NOT NULL,
    chemin_fichier VARCHAR(500) NOT NULL,
    extension VARCHAR(10),
    taille_octet BIGINT,
    date_document DATE,
    commentaire TEXT,
    uploaded_by INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_document_employe ON document_rh(id_employe);
CREATE INDEX idx_document_type ON document_rh(id_type_document);
CREATE INDEX idx_document_contrat ON document_rh(id_contrat);
CREATE INDEX idx_document_workflow ON document_rh(id_workflow);

-- 9. Notifications RH & Alertes Échéances
CREATE TABLE notification_rh (
    id_notification SERIAL PRIMARY KEY,
    id_utilisateur INTEGER REFERENCES utilisateur(id_utilisateur) ON DELETE CASCADE,
    cible_role VARCHAR(50),
    type_alerte VARCHAR(50) NOT NULL,
    priorite VARCHAR(10) NOT NULL DEFAULT 'NORMALE',
    titre VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    lien VARCHAR(500),
    id_employe_concerne INTEGER REFERENCES employe(id_employe) ON DELETE CASCADE,
    est_lue BOOLEAN DEFAULT FALSE,
    date_creation TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    date_lecture TIMESTAMP,
    date_expiration TIMESTAMP,
    CONSTRAINT chk_notif_priorite CHECK (priorite IN ('CRITIQUE', 'HAUTE', 'NORMALE', 'INFO')),
    CONSTRAINT chk_notif_type CHECK (type_alerte IN (
        'CONTRAT_EXPIRE_J', 'CONTRAT_EXPIRE_7J', 'CONTRAT_EXPIRE_30J', 'CONTRAT_ECHU_NON_REGULARISE',
        'ESSAI_EXPIRE_J', 'ESSAI_EXPIRE_7J', 'ESSAI_EXPIRE_15J', 'ESSAI_EXPIRE_30J', 'ESSAI_ECHU_SANS_DECISION',
        'BROUILLON_EN_ATTENTE', 'MOUVEMENT_APPLIQUE',
        'DOCUMENTS_MANQUANTS', 'EMPLOYE_CREE', 'BROUILLON_CREE',
        'CONGE_EN_ATTENTE', 'DEMANDE_CONGE_EN_ATTENTE', 'CONGE_VALIDE', 'CONGE_REFUSE', 'CONGE_ANNULE',
        'SOLDE_CONGE_FAIBLE', 'SOLDE_CONGE_NEGATIF',
        'RETOUR_CONGE_J', 'FRACTIONNEMENT_NON_RESPECTE',
        'MALADIE_PLAFOND_EPISODE_ATTEINT', 'CONGE_EXPIRATION_PROCHE',
        'POOL_EXCEPTIONNEL_EPUISE', 'BONIFICATION_ANCIENNETE_APPLIQUEE',
        'POINTAGE_MANQUANT', 'PERIODE_POINTAGE_A_VALIDER',
        'PERIODE_POINTAGE_VALIDEE', 'PERIODE_POINTAGE_REJETEE', 'RETARD_RECURRENT',
        'ALERTE_ECHEANCE'
    ))
);

CREATE INDEX idx_notif_utilisateur ON notification_rh(id_utilisateur) WHERE est_lue = FALSE;
CREATE INDEX idx_notif_cible_role ON notification_rh(cible_role) WHERE est_lue = FALSE;
CREATE INDEX idx_notif_non_lues ON notification_rh(est_lue, date_creation DESC);
CREATE INDEX idx_notif_type ON notification_rh(type_alerte);
CREATE INDEX idx_notif_employe ON notification_rh(id_employe_concerne);

-- =====================================================================
-- VUES DU MODULE GESTION DU PERSONNEL
-- =====================================================================

CREATE OR REPLACE VIEW v_employe_cpl AS
SELECT 
    e.id_employe,
    e.matricule,
    e.nom,
    e.prenom,
    CONCAT(e.nom, ' ', e.prenom) AS nom_complet,
    e.sexe,
    e.date_naissance,
    EXTRACT(YEAR FROM AGE(CURRENT_DATE, e.date_naissance))::INTEGER AS age,
    e.lieu_naissance,
    e.nationalite,
    e.pere_nom_prenom,
    e.mere_nom_prenom,
    e.situation_familiale,
    e.cin_numero,
    e.cin_date_delivrance,
    e.cin_lieu_delivrance,
    e.cin_duplicata_date,
    e.cin_duplicata_lieu,
    e.permis_conduire_numero,
    e.permis_conduire_date_delivrance,
    e.permis_conduire_lieu_delivrance,
    e.adresse,
    e.ville,
    e.telephone,
    e.email,
    e.num_affiliation_cnaps,
    e.photo_url,
    e.date_declaration,
    e.est_actif,
    e.date_desactivation,
    e.motif_desactivation,
    e.created_at,
    e.updated_at,
    cj.nom AS conjoint_nom,
    cj.prenom AS conjoint_prenom,
    CONCAT(cj.nom, ' ', cj.prenom) AS conjoint_nom_complet,
    cj.societe AS conjoint_societe,
    cj.fonction AS conjoint_fonction,
    cb.id_compte_banque AS id_compte_banque_principal,
    cb.id_banque AS id_banque_principal,
    rb.code_banque AS code_banque_principal,
    rb.nom_banque AS nom_banque_principal,
    cb.agence AS agence_banque_principal,
    cb.num_compte_rib AS num_compte_rib_principal,
    cb.titulaire_compte AS titulaire_compte_principal,
    COALESCE(enf.nb_enfants, 0) AS nb_enfants,
    COALESCE(enf.nb_enfants_a_charge, 0) AS nb_enfants_a_charge,
    COALESCE(ct.nb_contacts_urgence, 0) AS nb_contacts_urgence
FROM employe e
LEFT JOIN employe_conjoint cj ON e.id_employe = cj.id_employe
LEFT JOIN employe_compte_banque cb ON e.id_employe = cb.id_employe AND cb.est_principal = TRUE
LEFT JOIN ref_banque rb ON cb.id_banque = rb.id_banque
LEFT JOIN (
    SELECT id_employe, COUNT(*)::INTEGER AS nb_enfants,
           COUNT(CASE WHEN a_charge = TRUE THEN 1 END)::INTEGER AS nb_enfants_a_charge
    FROM employe_enfant GROUP BY id_employe
) enf ON e.id_employe = enf.id_employe
LEFT JOIN (
    SELECT id_employe, COUNT(*)::INTEGER AS nb_contacts_urgence
    FROM employe_contact_urgence GROUP BY id_employe
) ct ON e.id_employe = ct.id_employe;

CREATE OR REPLACE VIEW v_workflow_rh_cpl AS
SELECT
    w.id_workflow,
    w.id_employe,
    e.nom || ' ' || e.prenom AS nom_prenom_employe,
    e.matricule AS matricule_employe,
    tev.code AS code_type_evenement,
    tev.libelle AS libelle_type_evenement,
    rsw.code AS code_statut_workflow,
    rsw.libelle AS libelle_statut_workflow,
    rsw.ordre AS ordre_statut_workflow,
    w.id_contrat_source,
    ct_src.numero_contrat AS numero_contrat_source,
    w.id_contrat_cible,
    ct_cbl.numero_contrat AS numero_contrat_cible,
    w.id_affectation_source,
    w.id_affectation_cible,
    w.date_demande,
    w.date_effet,
    w.motif,
    w.commentaire,
    w.id_demandeur,
    w.id_valideur,
    w.created_at,
    w.updated_at,
    ct_cbl.id_filiale,
    ct_cbl.id_type_contrat,
    ct_cbl.id_type_intervenant,
    rtc.libelle AS libelle_type_contrat,
    rti.libelle AS libelle_type_intervenant,
    rf.raison_sociale AS libelle_filiale,
    ct_cbl.date_signature AS date_signature_contrat,
    ct_cbl.date_debut AS date_debut_contrat,
    ct_cbl.date_fin AS date_fin_contrat,
    ct_cbl.duree_essai_mois,
    ct_cbl.date_fin_essai,
    ct_cbl.comptabilise_effectif,
    eac.id_site,
    eac.id_departement,
    eac.id_service,
    eac.id_fonction,
    eac.id_responsable_hierarchique,
    eac.id_categorie_professionnelle,
    eac.id_mode_calcul_paie,
    rs.libelle AS libelle_site,
    rd.libelle AS libelle_departement,
    rsvc.libelle AS libelle_service,
    rfc.libelle AS libelle_fonction,
    resp.nom || ' ' || resp.prenom AS nom_prenom_responsable,
    rcp.libelle AS libelle_categorie_pro,
    rmp.libelle AS libelle_mode_calcul_paie,
    eac.salaire_base,
    eac.date_debut AS date_debut_affectation,
    eac.date_fin AS date_fin_affectation,
    rsa.libelle AS libelle_statut_affectation,
    rsa.ordre AS ordre_statut_affectation
FROM workflow_rh w
JOIN employe e ON w.id_employe = e.id_employe
JOIN ref_type_evenement_rh tev ON w.id_type_evenement_rh = tev.id_type_evenement_rh
JOIN ref_statut_rh rsw ON w.id_statut_rh = rsw.id_statut_rh
LEFT JOIN contrat_travail ct_src ON w.id_contrat_source = ct_src.id_contrat
LEFT JOIN contrat_travail ct_cbl ON COALESCE(w.id_contrat_cible, w.id_contrat_source) = ct_cbl.id_contrat
LEFT JOIN ref_type_contrat rtc ON ct_cbl.id_type_contrat = rtc.id_type_contrat
LEFT JOIN ref_type_intervenant rti ON ct_cbl.id_type_intervenant = rti.id_type_intervenant
LEFT JOIN ref_filiale rf ON ct_cbl.id_filiale = rf.id_filiale
LEFT JOIN employe_affectation_carriere eac ON COALESCE(w.id_affectation_cible, w.id_affectation_source) = eac.id_affectation
LEFT JOIN ref_statut_rh rsa ON eac.id_statut_rh = rsa.id_statut_rh
LEFT JOIN ref_site rs ON eac.id_site = rs.id_site
LEFT JOIN ref_departement rd ON eac.id_departement = rd.id_departement
LEFT JOIN ref_service rsvc ON eac.id_service = rsvc.id_service
LEFT JOIN ref_fonction rfc ON eac.id_fonction = rfc.id_fonction
LEFT JOIN employe resp ON eac.id_responsable_hierarchique = resp.id_employe
LEFT JOIN ref_categorie_professionnelle rcp ON eac.id_categorie_professionnelle = rcp.id_categorie_professionnelle
LEFT JOIN ref_mode_calcul_paie rmp ON eac.id_mode_calcul_paie = rmp.id_mode_calcul_paie;

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
    COALESCE((SELECT MIN(ct_init.date_debut) FROM contrat_travail ct_init WHERE ct_init.id_employe = e.id_employe), ct.date_debut, e.date_declaration) AS date_embauche,
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

CREATE OR REPLACE VIEW v_document_rh_cpl AS
SELECT 
    d.id_document,
    d.id_employe,
    d.id_type_document,
    d.id_contrat,
    d.id_workflow,
    t.libelle AS type_document_libelle,
    t.categorie AS type_document_categorie,
    t.description AS type_document_description,
    t.obligatoire AS type_document_obligatoire,
    d.nom_original,
    d.nom_stockage,
    d.chemin_fichier,
    d.extension,
    d.taille_octet,
    d.date_document,
    d.commentaire,
    d.uploaded_by,
    u.identifiant AS uploaded_by_identifiant,
    d.created_at,
    d.updated_at
FROM document_rh d
JOIN ref_type_document t ON d.id_type_document = t.id_type_document
LEFT JOIN utilisateur u ON d.uploaded_by = u.id_utilisateur;

-- =====================================================================
-- DONNÉES DE RÉFÉRENCE DE BASE (INITIAL SEEDS DES ARCHIVES)
-- =====================================================================

-- Types de Documents RH
INSERT INTO ref_type_document (id_type_document, libelle, categorie, description, obligatoire) VALUES
(1, 'Fiche renseignement', 'Administratif', 'Fiche personnelle remplie par l employé', TRUE),
(2, 'CIN Recto', 'Identité', 'Copie recto de la carte identité nationale', TRUE),
(3, 'CIN Verso', 'Identité', 'Copie verso de la carte identité nationale', TRUE),
(4, 'Permis de conduire', 'Identité', 'Copie du permis de conduire', FALSE),
(5, 'CV', 'Recrutement', 'Curriculum vitae', TRUE),
(6, 'Contrat de travail', 'Contrat', 'Contrat signé entre employé et entreprise', FALSE),
(7, 'Diplôme', 'Formation', 'Diplôme ou certificat de formation', FALSE),
(8, 'RIB', 'Paie', 'Coordonnées bancaires', FALSE);

SELECT setval('ref_type_document_id_type_document_seq', (SELECT GREATEST(MAX(id_type_document), 8) FROM ref_type_document));

INSERT INTO employe (id_employe, matricule, nom, prenom, sexe, date_naissance, lieu_naissance, nationalite, situation_familiale, cin_numero, cin_date_delivrance, cin_lieu_delivrance, adresse, ville, telephone, email, num_affiliation_cnaps, date_declaration, est_actif) VALUES (1, '1', 'RANDRASANA', 'Eric Jean-Joel', 'M', '1966-02-14', 'Antananarivo', 'Malgache', 'Marie', '101211101179', '1984-03-20', 'Antananarivo', 'Antananarivo', 'Antananarivo', '+261340000001', NULL, '780531', '2002-01-01', TRUE);

INSERT INTO employe (id_employe, matricule, nom, prenom, sexe, date_naissance, lieu_naissance, nationalite, situation_familiale, cin_numero, cin_date_delivrance, cin_lieu_delivrance, adresse, ville, telephone, email, num_affiliation_cnaps, date_declaration, est_actif) VALUES (2, '35', 'RALAMBOMANANA', 'Tatamo Michele', 'F', '1967-06-03', 'Antananarivo', 'Malgache', 'Marie', '117392000208', '1985-08-15', 'Antananarivo', 'Antananarivo', 'Antananarivo', '+261340000035', 'daf@galaxam.com', '819288', '2002-09-02', TRUE);

INSERT INTO contrat_travail (id_contrat, id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_rh, id_type_evenement_initial, numero_contrat, date_signature, date_debut, date_fin, duree_essai_mois, date_fin_essai, comptabilise_effectif, description, commentaire) VALUES (1, 1, 1, 1, 1, 4, (SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'EMBAUCHE_INITIALE'), 'CTR-2002-0001', '2002-01-01', '2002-01-01', NULL, 0, NULL, TRUE, 'Directeur Général', 'Seed historique - embauche initiale validée');

INSERT INTO contrat_travail (id_contrat, id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_rh, id_type_evenement_initial, numero_contrat, date_signature, date_debut, date_fin, duree_essai_mois, date_fin_essai, comptabilise_effectif, description, commentaire) VALUES (2, 2, 1, 1, 1, 4, (SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'EMBAUCHE_INITIALE'), 'CTR-2002-0035', '2002-09-02', '2002-09-02', NULL, 0, NULL, TRUE, 'Directrice Administrative et Financière', 'Seed historique - embauche initiale validée');

INSERT INTO employe_affectation_carriere (id_affectation, id_employe, id_contrat, id_statut_rh, id_motif_mouvement, id_site, id_departement, id_service, id_fonction, id_responsable_hierarchique, id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut, date_fin, description, commentaire) VALUES (1, 1, 1, 4, (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'), 1, 1, 1, (SELECT id_fonction FROM ref_fonction WHERE code_fonction = 'DG'), NULL, 1, 1, 5000000.00, '2002-01-01', NULL, 'Direction générale de GALAXAM Logistics', 'Seed historique');

INSERT INTO employe_affectation_carriere (id_affectation, id_employe, id_contrat, id_statut_rh, id_motif_mouvement, id_site, id_departement, id_service, id_fonction, id_responsable_hierarchique, id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut, date_fin, description, commentaire) VALUES (2, 2, 2, 4, (SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'), 1, 1, 1, (SELECT id_fonction FROM ref_fonction WHERE code_fonction = 'DAF'), 1, 1, 1, 3500000.00, '2002-09-02', NULL, 'Direction administrative et financière', 'Seed historique');

INSERT INTO workflow_rh (id_workflow, id_employe, id_type_evenement_rh, id_statut_rh, id_contrat_source, id_contrat_cible, id_affectation_source, id_affectation_cible, date_demande, date_effet, motif, commentaire, id_demandeur, id_valideur) VALUES (1, 1, (SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'EMBAUCHE_INITIALE'), 4, NULL, 1, NULL, 1, '2002-01-01', '2002-01-01', 'Embauche initiale', 'Workflow historique simulé comme déjà validé', NULL, NULL);

INSERT INTO workflow_rh (id_workflow, id_employe, id_type_evenement_rh, id_statut_rh, id_contrat_source, id_contrat_cible, id_affectation_source, id_affectation_cible, date_demande, date_effet, motif, commentaire, id_demandeur, id_valideur) VALUES (2, 2, (SELECT id_type_evenement_rh FROM ref_type_evenement_rh WHERE code = 'EMBAUCHE_INITIALE'), 4, NULL, 2, NULL, 2, '2002-09-02', '2002-09-02', 'Embauche initiale', 'Workflow historique simulé comme déjà validé', NULL, NULL);

INSERT INTO employe_conjoint (id_conjoint, id_employe, nom, prenom, date_naissance, lieu_naissance, societe, fonction) VALUES (1, 1, 'RALAMBOMANANA', 'Tatamo Michele', '1967-06-03', 'Antananarivo', 'Galaxam Logistics', 'DAF');

INSERT INTO employe_conjoint (id_conjoint, id_employe, nom, prenom, date_naissance, lieu_naissance, societe, fonction) VALUES (2, 2, 'RANDRASANA', 'Eric Jean-Joel', '1966-02-14', 'Antananarivo', 'Galaxam Logistics', 'DG');

SELECT setval('employe_id_employe_seq', 2, TRUE);
SELECT setval('contrat_travail_id_contrat_seq', 2, TRUE);
SELECT setval('employe_affectation_carriere_id_affectation_seq', 2, TRUE);
SELECT setval('workflow_rh_id_workflow_seq', 2, TRUE);
SELECT setval('employe_conjoint_id_conjoint_seq', 2, TRUE);