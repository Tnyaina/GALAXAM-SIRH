-- ============================================================
-- NETTOYAGE PRÉALABLE DES TABLES
-- ============================================================
DROP TABLE IF EXISTS employe_affectation_carriere CASCADE;
DROP TABLE IF EXISTS workflow_rh CASCADE;
DROP TABLE IF EXISTS contrat_periode_essai CASCADE;
DROP TABLE IF EXISTS contrat_travail CASCADE;

DROP TABLE IF EXISTS ref_statut_contrat CASCADE;
DROP TABLE IF EXISTS ref_statut_affectation CASCADE;
DROP TABLE IF EXISTS ref_statut_workflow CASCADE;
DROP TABLE IF EXISTS ref_type_evenement_rh CASCADE;
DROP TABLE IF EXISTS ref_motif_sortie CASCADE;
DROP TABLE IF EXISTS ref_motif_mouvement CASCADE;
DROP TABLE IF EXISTS ref_mode_calcul_paie CASCADE;
DROP TABLE IF EXISTS ref_categorie_professionnelle CASCADE;

-- ============================================================
-- 1. TABLES DE RÉFÉRENCE (Satellites & Lookups)
-- ============================================================

CREATE TABLE ref_statut_contrat (
    id_statut_contrat SERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE, -- BROUILLON, A_SIGNER, ACTIF, SUSPENDU, CLOTURE, ROMPU
    libelle VARCHAR(100) NOT NULL
);

CREATE TABLE ref_statut_affectation (
    id_statut_affectation SERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE, -- PLANIFIEE, ACTIVE, HISTORISEE, ANNULEE
    libelle VARCHAR(100) NOT NULL
);

CREATE TABLE ref_statut_workflow (
    id_statut_workflow SERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE, -- BROUILLON, VALIDE, ANNULE
    libelle VARCHAR(100) NOT NULL
);

CREATE TABLE ref_type_evenement_rh (
    id_type_evenement_rh SERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    -- EMBAUCHE_INITIALE, RENOUVELLEMENT_CDD, TRANSFORMATION_CDI, EMBAUCHE_APRES_STAGE,
    -- MUTATION, PROMOTION, REVALORISATION, RENOUVELLEMENT_ESSAI, CONFIRMATION_ESSAI,
    -- RUPTURE_ESSAI, DEPART_RETRAITE, REPRISE_CONSULTANT, FIN_CONTRAT
    libelle VARCHAR(100) NOT NULL,
    description TEXT
);

CREATE TABLE ref_motif_sortie (
    id_motif_sortie SERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    libelle VARCHAR(100) NOT NULL,
    actif BOOLEAN DEFAULT TRUE
);

CREATE TABLE ref_motif_mouvement (
    id_motif_mouvement SERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    libelle VARCHAR(100) NOT NULL,
    actif BOOLEAN DEFAULT TRUE
);

CREATE TABLE ref_mode_calcul_paie (
    id_mode_calcul_paie SERIAL PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE, -- MENSUEL, HORAIRE, JOURNALIER
    libelle VARCHAR(100) NOT NULL,
    description TEXT,
    actif BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ref_categorie_professionnelle (
    id_categorie_professionnelle SERIAL PRIMARY KEY,
    code VARCHAR(10) NOT NULL UNIQUE, -- CADRE, A, etc.
    libelle VARCHAR(100) NOT NULL,
    description TEXT,
    actif BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- ============================================================
-- 2. CONTRAT DE TRAVAIL
-- ============================================================

CREATE TABLE contrat_travail (
    id_contrat SERIAL PRIMARY KEY,
    id_employe INTEGER NOT NULL,
    id_filiale INTEGER NOT NULL,
    id_type_contrat INTEGER NOT NULL,
    id_type_intervenant INTEGER NOT NULL,
    id_statut_contrat INTEGER NOT NULL,
    id_type_evenement_initial INTEGER,
    id_contrat_precedent INTEGER,
    
    numero_contrat VARCHAR(50) UNIQUE,
    date_signature DATE,
    date_debut DATE NOT NULL,
    date_fin DATE,
    duree_essai_mois INTEGER,
    date_fin_essai DATE,
    
    comptabilise_effectif BOOLEAN DEFAULT TRUE,
    id_motif_sortie INTEGER,
    
    description TEXT,
    commentaire TEXT,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_contrat_employe FOREIGN KEY (id_employe) REFERENCES employe(id_employe) ON DELETE CASCADE,
    CONSTRAINT fk_contrat_filiale FOREIGN KEY (id_filiale) REFERENCES ref_filiale(id_filiale),
    CONSTRAINT fk_contrat_type FOREIGN KEY (id_type_contrat) REFERENCES ref_type_contrat(id_type_contrat),
    CONSTRAINT fk_contrat_intervenant FOREIGN KEY (id_type_intervenant) REFERENCES ref_type_intervenant(id_type_intervenant),
    CONSTRAINT fk_contrat_statut FOREIGN KEY (id_statut_contrat) REFERENCES ref_statut_contrat(id_statut_contrat),
    CONSTRAINT fk_contrat_evenement FOREIGN KEY (id_type_evenement_initial) REFERENCES ref_type_evenement_rh(id_type_evenement_rh),
    CONSTRAINT fk_contrat_precedent FOREIGN KEY (id_contrat_precedent) REFERENCES contrat_travail(id_contrat),
    CONSTRAINT fk_contrat_motif_sortie FOREIGN KEY (id_motif_sortie) REFERENCES ref_motif_sortie(id_motif_sortie)
);

CREATE INDEX idx_contrat_employe ON contrat_travail(id_employe);
CREATE INDEX idx_contrat_statut ON contrat_travail(id_statut_contrat);

-- ============================================================
-- 3. PÉRIODE D'ESSAI
-- ============================================================

CREATE TABLE contrat_periode_essai (
    id_periode_essai SERIAL PRIMARY KEY,
    id_contrat INTEGER NOT NULL,
    numero_periode INTEGER DEFAULT 1,
    date_debut DATE NOT NULL,
    date_fin_prevue DATE NOT NULL,
    date_fin_reelle DATE,
    -- Statut géré en VARCHAR contraint (pas de table de référence, contexte unique)
    statut VARCHAR(30) DEFAULT 'EN_COURS',
    commentaire TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_essai_contrat FOREIGN KEY (id_contrat) REFERENCES contrat_travail(id_contrat) ON DELETE CASCADE,
    CONSTRAINT chk_statut_periode_essai CHECK (statut IN ('EN_COURS', 'CONFIRMEE', 'RENOUVELEE', 'ROMPUE')),
    CONSTRAINT chk_numero_periode CHECK (numero_periode >= 1)
);

-- ============================================================
-- 4. WORKFLOW RH (Moteur d'événements)
-- ============================================================

CREATE TABLE workflow_rh (
    id_workflow SERIAL PRIMARY KEY,
    id_employe INTEGER NOT NULL,
    id_type_evenement_rh INTEGER NOT NULL,
    id_statut_workflow INTEGER NOT NULL,
    
    -- Liens source / cible (contrat & affectation)
    -- NOTE : id_affectation_* ne sont pas référencés en retour depuis employe_affectation_carriere
    --        pour éviter la dépendance circulaire. La traçabilité inverse se fait via :
    --        SELECT * FROM workflow_rh WHERE id_affectation_cible = :id
    id_contrat_source INTEGER,
    id_contrat_cible INTEGER,
    id_affectation_source INTEGER,
    id_affectation_cible INTEGER,
    
    date_demande DATE NOT NULL DEFAULT CURRENT_DATE,
    date_effet DATE NOT NULL,
    
    motif TEXT,
    commentaire TEXT,
    
    -- Références vers le compte applicatif (utilisateur Spring Security/JWT)
    id_demandeur INTEGER,  -- utilisateur qui a saisi la demande (DAF/admin)
    id_valideur INTEGER,   -- utilisateur qui a validé (DAF/admin)
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_wf_employe FOREIGN KEY (id_employe) REFERENCES employe(id_employe) ON DELETE CASCADE,
    CONSTRAINT fk_wf_type FOREIGN KEY (id_type_evenement_rh) REFERENCES ref_type_evenement_rh(id_type_evenement_rh),
    CONSTRAINT fk_wf_statut FOREIGN KEY (id_statut_workflow) REFERENCES ref_statut_workflow(id_statut_workflow),
    CONSTRAINT fk_wf_contrat_src FOREIGN KEY (id_contrat_source) REFERENCES contrat_travail(id_contrat),
    CONSTRAINT fk_wf_contrat_cbl FOREIGN KEY (id_contrat_cible) REFERENCES contrat_travail(id_contrat),
    CONSTRAINT fk_wf_demandeur FOREIGN KEY (id_demandeur) REFERENCES utilisateur(id_utilisateur) ON DELETE SET NULL,
    CONSTRAINT fk_wf_valideur FOREIGN KEY (id_valideur) REFERENCES utilisateur(id_utilisateur) ON DELETE SET NULL
);

-- ============================================================
-- 5. AFFECTATION CARRIÈRE
-- ============================================================

CREATE TABLE employe_affectation_carriere (
    id_affectation SERIAL PRIMARY KEY,
    id_employe INTEGER NOT NULL,
    id_contrat INTEGER NOT NULL,
    id_statut_affectation INTEGER NOT NULL,
    id_motif_mouvement INTEGER NOT NULL,
    -- SUPPRIMÉ : id_workflow_origine (dépendance circulaire éliminée)
    -- Pour retrouver le workflow d'origine : SELECT * FROM workflow_rh WHERE id_affectation_cible = :id
    
    id_site INTEGER NOT NULL,
    id_departement INTEGER NOT NULL,
    id_service INTEGER NOT NULL,
    id_fonction INTEGER NOT NULL,
    id_responsable_hierarchique INTEGER,
    
    id_categorie_professionnelle INTEGER NOT NULL,
    id_mode_calcul_paie INTEGER NOT NULL,
    salaire_base NUMERIC(12, 2) NOT NULL,
    
    date_debut DATE NOT NULL,
    date_fin DATE,
    
    description TEXT,
    commentaire TEXT,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_affectation_employe FOREIGN KEY(id_employe) REFERENCES employe(id_employe) ON DELETE CASCADE,
    CONSTRAINT fk_affectation_contrat FOREIGN KEY(id_contrat) REFERENCES contrat_travail(id_contrat) ON DELETE CASCADE,
    CONSTRAINT fk_affectation_statut FOREIGN KEY(id_statut_affectation) REFERENCES ref_statut_affectation(id_statut_affectation),
    CONSTRAINT fk_affectation_motif FOREIGN KEY(id_motif_mouvement) REFERENCES ref_motif_mouvement(id_motif_mouvement),
    CONSTRAINT fk_affectation_site FOREIGN KEY(id_site) REFERENCES ref_site(id_site),
    CONSTRAINT fk_affectation_departement FOREIGN KEY(id_departement) REFERENCES ref_departement(id_departement),
    CONSTRAINT fk_affectation_service FOREIGN KEY(id_service) REFERENCES ref_service(id_service),
    CONSTRAINT fk_affectation_fonction FOREIGN KEY(id_fonction) REFERENCES ref_fonction(id_fonction),
    CONSTRAINT fk_affectation_responsable FOREIGN KEY(id_responsable_hierarchique) REFERENCES employe(id_employe) ON DELETE SET NULL,
    CONSTRAINT fk_affectation_categorie FOREIGN KEY(id_categorie_professionnelle) REFERENCES ref_categorie_professionnelle(id_categorie_professionnelle),
    CONSTRAINT fk_affectation_mode_paie FOREIGN KEY(id_mode_calcul_paie) REFERENCES ref_mode_calcul_paie(id_mode_calcul_paie)
);

CREATE INDEX idx_affectation_employe ON employe_affectation_carriere(id_employe);
CREATE INDEX idx_affectation_contrat ON employe_affectation_carriere(id_contrat);
CREATE INDEX idx_affectation_statut ON employe_affectation_carriere(id_statut_affectation);

-- Clés étrangères de workflow_rh vers employe_affectation_carriere
-- (sens unique : workflow → affectation, jamais affectation → workflow)
ALTER TABLE workflow_rh 
    ADD CONSTRAINT fk_wf_aff_src FOREIGN KEY (id_affectation_source) REFERENCES employe_affectation_carriere(id_affectation),
    ADD CONSTRAINT fk_wf_aff_cbl FOREIGN KEY (id_affectation_cible) REFERENCES employe_affectation_carriere(id_affectation);

-- ============================================================
-- 7. DONNÉES DE RÉFÉRENCE INITIALES (Seed Data)
-- ============================================================

INSERT INTO ref_statut_contrat (code, libelle) VALUES
    ('BROUILLON', 'Brouillon'),
    ('A_SIGNER', 'En attente de signature'),
    ('ACTIF', 'Contrat actif'),
    ('SUSPENDU', 'Contrat suspendu'),
    ('CLOTURE', 'Contrat arrivé à terme'),
    ('ROMPU', 'Contrat rompu anticipativement'),
    ('ANNULE', 'Contrat annulé')
ON CONFLICT (code) DO NOTHING;

INSERT INTO ref_statut_affectation (code, libelle) VALUES
    ('PLANIFIEE', 'Affectation future / planifiée'),
    ('ACTIVE', 'Affectation actuelle en cours'),
    ('HISTORISEE', 'Ancienne affectation clôturée'),
    ('ANNULEE', 'Affectation annulée')
ON CONFLICT (code) DO NOTHING;

INSERT INTO ref_statut_workflow (code, libelle) VALUES
    ('BROUILLON', 'Brouillon en préparation par la DAF'),
    ('VALIDE', 'Validé et appliqué par la DAF'),
    ('ANNULE', 'Annulé par la DAF')
ON CONFLICT (code) DO NOTHING;

INSERT INTO ref_type_evenement_rh (code, libelle) VALUES
    ('EMBAUCHE_INITIALE', 'Embauche initiale'),
    ('RENOUVELLEMENT_CDD', 'Renouvellement de CDD'),
    ('TRANSFORMATION_CDI', 'Passage CDD vers CDI'),
    ('EMBAUCHE_APRES_STAGE', 'Passage Stage vers CDD/CDI'),
    ('MUTATION', 'Changement de site ou de service'),
    ('PROMOTION', 'Promotion ou changement de fonction'),
    ('REVALORISATION', 'Revalorisation salariale'),
    ('RENOUVELLEMENT_ESSAI', 'Renouvellement de la période d essai'),
    ('CONFIRMATION_ESSAI', 'Confirmation de fin de période d essai'),
    ('RUPTURE_ESSAI', 'Rupture de la période d essai'),
    ('DEPART_RETRAITE', 'Départ à la retraite'),
    ('REPRISE_CONSULTANT', 'Embauche d un retraité comme consultant'),
    ('FIN_CONTRAT', 'Fin de contrat / Sortie de l effectif')
ON CONFLICT (code) DO NOTHING;

INSERT INTO ref_motif_sortie (code, libelle) VALUES
    ('DEMISSION', 'Démission de l employé'),
    ('LICENCIEMENT', 'Licenciement'),
    ('FIN_CONTRAT', 'Fin du contrat (CDD / Stage)'),
    ('RETRAITE', 'Départ en retraite'),
    ('DECES', 'Décès de l employé'),
    ('RUPTURE_CONVENTIONNELLE', 'Rupture conventionnelle'),
    ('AUTRE', 'Autre motif')
ON CONFLICT (code) DO NOTHING;

INSERT INTO ref_motif_mouvement (code, libelle) VALUES
    ('EMBAUCHE', 'Embauche initiale'),
    ('PROMOTION', 'Promotion ou changement de fonction'),
    ('MUTATION', 'Mutation de site/département/service'),
    ('AVENANT', 'Avenant au contrat'),
    ('REVALORISATION', 'Revalorisation du salaire'),
    ('REORGANISATION', 'Réorganisation interne'),
    ('AUTRE', 'Autre mouvement')
ON CONFLICT (code) DO NOTHING;

INSERT INTO ref_mode_calcul_paie (code, libelle, description) VALUES
    ('MENSUEL', 'Mensuel fixe', 'Calcul sur forfait mensuel standard'),
    ('HORAIRE', 'Taux horaire', 'Calcul au nombre d heures effectuées'),
    ('JOURNALIER', 'Forfait journalier', 'Calcul au tarif par jour travaillé')
ON CONFLICT (code) DO NOTHING;

INSERT INTO ref_categorie_professionnelle (code, libelle, description) VALUES
    ('CADRE', 'Cadre', 'Cadres et ingénieurs'),
    ('A', 'Agent', 'Techniciens et agents')
ON CONFLICT (code) DO NOTHING;

-- ============================================================
-- 8. VUE CPL ENRICHIE (Dédiée aux récapitulatifs & consultations)
-- ============================================================

DROP VIEW IF EXISTS v_workflow_rh_cpl CASCADE;

CREATE OR REPLACE VIEW v_workflow_rh_cpl AS
SELECT
    w.id_workflow,
    w.id_employe,
    e.nom || ' ' || e.prenom     AS nom_prenom_employe,
    e.matricule                   AS matricule_employe,
    tev.code                      AS code_type_evenement,
    tev.libelle                   AS libelle_type_evenement,
    rsw.code                      AS code_statut_workflow,
    rsw.libelle                   AS libelle_statut_workflow,

    w.id_contrat_source,
    ct_src.numero_contrat         AS numero_contrat_source,
    w.id_contrat_cible,
    ct_cbl.numero_contrat         AS numero_contrat_cible,
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

    -- Contrat cible
    ct_cbl.id_filiale,
    ct_cbl.id_type_contrat,
    ct_cbl.id_type_intervenant,
    rtc.libelle                   AS libelle_type_contrat,
    rti.libelle                   AS libelle_type_intervenant,
    rf.raison_sociale             AS libelle_filiale,
    ct_cbl.date_debut             AS date_debut_contrat,
    ct_cbl.date_fin               AS date_fin_contrat,
    ct_cbl.duree_essai_mois,
    ct_cbl.date_fin_essai,
    ct_cbl.comptabilise_effectif,

    -- Affectation cible
    eac.id_site,
    eac.id_departement,
    eac.id_service,
    eac.id_fonction,
    eac.id_responsable_hierarchique,
    eac.id_categorie_professionnelle,
    eac.id_mode_calcul_paie,
    rs.libelle                    AS libelle_site,
    rd.libelle                    AS libelle_departement,
    rsvc.libelle                  AS libelle_service,
    rfc.libelle                   AS libelle_fonction,
    resp.nom || ' ' || resp.prenom AS nom_prenom_responsable,
    rcp.libelle                   AS libelle_categorie_pro,
    rmp.libelle                   AS libelle_mode_calcul_paie,
    eac.salaire_base,
    eac.date_debut                AS date_debut_affectation,
    eac.date_fin                  AS date_fin_affectation,
    rsa.libelle                   AS libelle_statut_affectation

FROM workflow_rh w
JOIN employe e          ON w.id_employe = e.id_employe
JOIN ref_type_evenement_rh tev ON w.id_type_evenement_rh = tev.id_type_evenement_rh
JOIN ref_statut_workflow rsw    ON w.id_statut_workflow = rsw.id_statut_workflow

LEFT JOIN contrat_travail ct_src ON w.id_contrat_source = ct_src.id_contrat
LEFT JOIN contrat_travail ct_cbl ON w.id_contrat_cible  = ct_cbl.id_contrat
LEFT JOIN ref_type_contrat rtc  ON ct_cbl.id_type_contrat = rtc.id_type_contrat
LEFT JOIN ref_type_intervenant rti ON ct_cbl.id_type_intervenant = rti.id_type_intervenant
LEFT JOIN ref_filiale rf         ON ct_cbl.id_filiale = rf.id_filiale

LEFT JOIN employe_affectation_carriere eac ON w.id_affectation_cible = eac.id_affectation
LEFT JOIN ref_site rs             ON eac.id_site = rs.id_site
LEFT JOIN ref_departement rd      ON eac.id_departement = rd.id_departement
LEFT JOIN ref_service rsvc        ON eac.id_service = rsvc.id_service
LEFT JOIN ref_fonction rfc        ON eac.id_fonction = rfc.id_fonction
LEFT JOIN employe resp            ON eac.id_responsable_hierarchique = resp.id_employe
LEFT JOIN ref_categorie_professionnelle rcp ON eac.id_categorie_professionnelle = rcp.id_categorie_professionnelle
LEFT JOIN ref_mode_calcul_paie rmp ON eac.id_mode_calcul_paie = rmp.id_mode_calcul_paie
LEFT JOIN ref_statut_affectation rsa ON eac.id_statut_affectation = rsa.id_statut_affectation;
