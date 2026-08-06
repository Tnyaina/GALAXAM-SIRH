DROP TABLE IF EXISTS employe_affectation_carriere CASCADE;
DROP TABLE IF EXISTS contrat_travail CASCADE;
DROP TABLE IF EXISTS ref_motif_sortie CASCADE;
DROP TABLE IF EXISTS ref_motif_mouvement CASCADE;
DROP TABLE IF EXISTS ref_mode_calcul_paie CASCADE;
DROP TABLE IF EXISTS ref_categorie_professionnelle CASCADE;

-- ============================================================
-- TABLES DE REFERENCE
-- ============================================================
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
    code VARCHAR(20) NOT NULL UNIQUE,
    libelle VARCHAR(100) NOT NULL,
    description TEXT,
    actif BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ref_categorie_professionnelle (
    id_categorie_professionnelle SERIAL PRIMARY KEY,
    code VARCHAR(10) NOT NULL UNIQUE,
    libelle VARCHAR(100) NOT NULL,
    description TEXT,
    actif BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- ============================================================
-- CONTRAT DE TRAVAIL
-- ============================================================
CREATE TABLE contrat_travail (
    id_contrat SERIAL PRIMARY KEY,
    id_employe INTEGER NOT NULL,
    id_filiale INTEGER NOT NULL,
    id_type_contrat INTEGER NOT NULL,
    id_type_intervenant INTEGER NOT NULL,
    id_contrat_precedent INTEGER,
    numero_contrat VARCHAR(50) UNIQUE,
    date_signature DATE,
    date_debut DATE NOT NULL,
    date_fin DATE,
    date_fin_essai DATE,
    essai_renouvele BOOLEAN DEFAULT FALSE,
    comptabilise_effectif BOOLEAN DEFAULT TRUE,
    est_actif BOOLEAN DEFAULT TRUE,
    id_motif_sortie INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_contrat_employe FOREIGN KEY (id_employe) REFERENCES employe(id_employe) ON DELETE CASCADE,
    CONSTRAINT fk_contrat_filiale FOREIGN KEY (id_filiale) REFERENCES ref_filiale(id_filiale),
    CONSTRAINT fk_contrat_type FOREIGN KEY (id_type_contrat) REFERENCES ref_type_contrat(id_type_contrat),
    CONSTRAINT fk_contrat_intervenant FOREIGN KEY (id_type_intervenant) REFERENCES ref_type_intervenant(id_type_intervenant),
    CONSTRAINT fk_contrat_precedent FOREIGN KEY (id_contrat_precedent) REFERENCES contrat_travail(id_contrat),
    CONSTRAINT fk_contrat_motif_sortie FOREIGN KEY (id_motif_sortie) REFERENCES ref_motif_sortie(id_motif_sortie)
);

CREATE INDEX idx_contrat_employe ON contrat_travail(id_employe);
CREATE INDEX idx_contrat_actif ON contrat_travail(est_actif);

-- ============================================================
-- AFFECTATION CARRIERE
-- ============================================================
CREATE TABLE employe_affectation_carriere (
    id_affectation SERIAL PRIMARY KEY,
    id_employe INTEGER NOT NULL,
    id_contrat INTEGER NOT NULL,
    id_motif_mouvement INTEGER NOT NULL,
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
    commentaire TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_affectation_employe FOREIGN KEY(id_employe) REFERENCES employe(id_employe) ON DELETE CASCADE,
    CONSTRAINT fk_affectation_contrat FOREIGN KEY(id_contrat) REFERENCES contrat_travail(id_contrat) ON DELETE CASCADE,
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
CREATE INDEX idx_affectation_active ON employe_affectation_carriere(date_fin);

-- ============================================================
-- DONNEES INITIALES REFERENCES
-- ============================================================

-- Motifs de sortie
INSERT INTO ref_motif_sortie (code, libelle) VALUES
    ('DEMISSION', 'Démission de l employé'),
    ('LICENCIEMENT', 'Licenciement pour motif personnel ou économique'),
    ('FIN_CONTRAT', 'Fin du contrat à durée déterminée (CDD / Essai)'),
    ('RETRAITE', 'Départ en retraite'),
    ('DECES', 'Décès de l employé'),
    ('RUPTURE_CONVENTIONNELLE', 'Rupture conventionnelle d un commun accord'),
    ('AUTRE', 'Autre motif de fin de contrat')
ON CONFLICT (code) DO NOTHING;

-- Motifs de mouvement
INSERT INTO ref_motif_mouvement (code, libelle) VALUES
    ('EMBAUCHE', 'Embauche initiale'),
    ('PROMOTION', 'Promotion ou changement de grade/fonction'),
    ('MUTATION', 'Mutation de site, département ou service'),
    ('AVENANT', 'Modification contractuelle / Avenant'),
    ('REVALORISATION', 'Revalorisation du salaire de base'),
    ('REORGANISATION', 'Réorganisation interne du service'),
    ('AUTRE', 'Autre mouvement de carrière')
ON CONFLICT (code) DO NOTHING;

-- Modes de calcul de paie
INSERT INTO ref_mode_calcul_paie (code, libelle, description) VALUES
    ('MENSUEL', 'Mensuel fixe', 'Calcul sur base d un forfait mensuel standard (173.33h)'),
    ('HORAIRE', 'Taux horaire', 'Calcul sur le nombre d heures réellement effectuées'),
    ('JOURNALIER', 'Forfait journalier / Pige', 'Calcul au tarif par jour travaillé')
ON CONFLICT (code) DO NOTHING;

-- Catégories professionnelles
INSERT INTO ref_categorie_professionnelle (code, libelle, description) VALUES
    ('CADRE', 'Cadre', 'Cadres supérieurs, ingénieurs et managers'),
    ('A', 'Agent', 'Techniciens supérieurs, chefs d équipe et superviseurs')
ON CONFLICT (code) DO NOTHING;

-- ============================================================
-- VUES DE SYNTHESE
-- ============================================================

DROP VIEW IF EXISTS v_contrat_cpl CASCADE;
CREATE OR REPLACE VIEW v_contrat_cpl AS
SELECT 
    c.id_contrat,
    c.id_employe,
    e.matricule AS employe_matricule,
    CONCAT(e.nom, ' ', e.prenom) AS employe_nom_complet,
    c.id_filiale,
    f.code_filiale,
    f.raison_sociale AS filiale_raison_sociale,
    c.id_type_contrat,
    tc.code AS type_contrat_code,
    tc.libelle AS type_contrat_libelle,
    c.id_type_intervenant,
    ti.code AS type_intervenant_code,
    ti.libelle AS type_intervenant_libelle,
    c.id_contrat_precedent,
    c.numero_contrat,
    c.date_signature,
    c.date_debut,
    c.date_fin,
    c.date_fin_essai,
    c.essai_renouvele,
    c.comptabilise_effectif,
    c.est_actif,
    c.id_motif_sortie,
    ms.code AS motif_sortie_code,
    ms.libelle AS motif_sortie_libelle,
    c.created_at,
    c.updated_at
FROM contrat_travail c
JOIN employe e ON c.id_employe = e.id_employe
JOIN ref_filiale f ON c.id_filiale = f.id_filiale
JOIN ref_type_contrat tc ON c.id_type_contrat = tc.id_type_contrat
JOIN ref_type_intervenant ti ON c.id_type_intervenant = ti.id_type_intervenant
LEFT JOIN ref_motif_sortie ms ON c.id_motif_sortie = ms.id_motif_sortie;

DROP VIEW IF EXISTS v_affectation_carriere_cpl CASCADE;
CREATE OR REPLACE VIEW v_affectation_carriere_cpl AS
SELECT 
    a.id_affectation,
    a.id_employe,
    e.matricule AS employe_matricule,
    CONCAT(e.nom, ' ', e.prenom) AS employe_nom_complet,
    a.id_contrat,
    ct.numero_contrat,
    ct.id_filiale,
    fil.code_filiale,
    fil.raison_sociale AS filiale_raison_sociale,
    a.id_motif_mouvement,
    mm.code AS motif_mouvement_code,
    mm.libelle AS motif_mouvement_libelle,
    a.id_site,
    s.code_site,
    s.libelle AS site_libelle,
    a.id_departement,
    d.code_departement,
    d.libelle AS departement_libelle,
    a.id_service,
    srv.code_service,
    srv.libelle AS service_libelle,
    a.id_fonction,
    fnc.code_fonction,
    fnc.libelle AS fonction_libelle,
    a.id_responsable_hierarchique,
    CONCAT(resp.nom, ' ', resp.prenom) AS responsable_nom_complet,
    a.id_categorie_professionnelle,
    cp.code AS categorie_code,
    cp.libelle AS categorie_libelle,
    a.id_mode_calcul_paie,
    mp.code AS mode_paie_code,
    mp.libelle AS mode_paie_libelle,
    a.salaire_base,
    a.date_debut,
    a.date_fin,
    (a.date_fin IS NULL OR a.date_fin >= CURRENT_DATE) AS est_affectation_courante,
    a.commentaire,
    a.created_at,
    a.updated_at
FROM employe_affectation_carriere a
JOIN employe e ON a.id_employe = e.id_employe
JOIN contrat_travail ct ON a.id_contrat = ct.id_contrat
JOIN ref_filiale fil ON ct.id_filiale = fil.id_filiale
JOIN ref_motif_mouvement mm ON a.id_motif_mouvement = mm.id_motif_mouvement
JOIN ref_site s ON a.id_site = s.id_site
JOIN ref_departement d ON a.id_departement = d.id_departement
JOIN ref_service srv ON a.id_service = srv.id_service
JOIN ref_fonction fnc ON a.id_fonction = fnc.id_fonction
LEFT JOIN employe resp ON a.id_responsable_hierarchique = resp.id_employe
JOIN ref_categorie_professionnelle cp ON a.id_categorie_professionnelle = cp.id_categorie_professionnelle
JOIN ref_mode_calcul_paie mp ON a.id_mode_calcul_paie = mp.id_mode_calcul_paie;