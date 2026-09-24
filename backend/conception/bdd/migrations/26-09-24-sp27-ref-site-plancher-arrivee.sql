-- =====================================================================
-- Migration sp27 : Plancher d'arrivée dans ref_site & précision décompte congés
-- =====================================================================
-- 1. Migration de ref_tolerance_arrivee_pointage vers ref_site
--    Le concept est un plancher d'arrivée valorisé (earliest_paid_start),
--    propre au site géographique. Cette migration unifie la configuration
--    dans ref_site et supprime la table isolée ref_tolerance_arrivee_pointage.
--
-- 2. Précision de nb_jours_decomptes dans demande_conge
--    Permet de stocker des fractions fines (ex: 0.25j ou 0.75j) lors
--    de régularisations de congés partielles sans perte de précision.
-- =====================================================================

-- 1. Ajout des colonnes de plancher d'arrivée dans ref_site
ALTER TABLE ref_site
    ADD COLUMN IF NOT EXISTS plancher_arrivee_actif BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS heure_entree_minimale_valorisee TIME NULL;

COMMENT ON COLUMN ref_site.plancher_arrivee_actif IS
    'Active ou désactive le plancher d''arrivée valorisé pour le site.';

COMMENT ON COLUMN ref_site.heure_entree_minimale_valorisee IS
    'Heure minimale matinale comptabilisée pour le calcul de présence (earliest_paid_start).';

-- 2. Migration des données existantes depuis ref_tolerance_arrivee_pointage (si elle existe)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'ref_tolerance_arrivee_pointage') THEN
        UPDATE ref_site s
        SET plancher_arrivee_actif = COALESCE(t.est_actif, FALSE),
            heure_entree_minimale_valorisee = t.heure_plafond
        FROM ref_tolerance_arrivee_pointage t
        WHERE s.id_site = t.id_site;
    END IF;
END $$;

-- 3. Ajout de la contrainte de cohérence :
--    Si le plancher d'arrivée est actif, l'heure minimale doit être renseignée.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_ref_site_plancher_arrivee'
    ) THEN
        ALTER TABLE ref_site
            ADD CONSTRAINT chk_ref_site_plancher_arrivee
            CHECK (NOT plancher_arrivee_actif OR heure_entree_minimale_valorisee IS NOT NULL);
    END IF;
END $$;

-- 4. Suppression de l'ancienne table ref_tolerance_arrivee_pointage
DROP TABLE IF EXISTS ref_tolerance_arrivee_pointage CASCADE;

-- 5. Adaptation du type de nb_jours_decomptes pour les fractions partielles (ex: 0.25j)
DROP VIEW IF EXISTS v_demande_conge_cpl CASCADE;
DROP VIEW IF EXISTS v_pool_conge_annuel_consommation CASCADE;

ALTER TABLE demande_conge
    ALTER COLUMN nb_jours_decomptes TYPE NUMERIC(5,2);

CREATE OR REPLACE VIEW v_pool_conge_annuel_consommation AS
SELECT
    d.id_employe,
    tc.id_pool_conge_annuel AS id_pool,
    EXTRACT(YEAR FROM d.date_debut)::INT AS annee,
    SUM(CASE WHEN sd.code = 'VALIDE' THEN d.nb_jours_decomptes ELSE 0 END) AS jours_consommes,
    SUM(CASE WHEN sd.code = 'EN_ATTENTE' THEN d.nb_jours_decomptes ELSE 0 END) AS jours_en_attente
FROM demande_conge d
JOIN ref_type_conge tc ON tc.id_type_conge = d.id_type_conge
JOIN ref_statut_demande_conge sd ON sd.id_statut_demande_conge = d.id_statut_demande_conge
WHERE tc.id_pool_conge_annuel IS NOT NULL
  AND sd.code IN ('VALIDE', 'EN_ATTENTE')
GROUP BY d.id_employe, tc.id_pool_conge_annuel, EXTRACT(YEAR FROM d.date_debut);

CREATE OR REPLACE VIEW v_demande_conge_cpl AS
SELECT
    d.id_demande_conge,
    d.id_employe,
    e.matricule,
    e.nom,
    e.prenom,
    d.id_type_conge,
    tc.code AS code_type_conge,
    tc.libelle AS libelle_type_conge,
    COALESCE(d.est_remunere, tc.est_paye) AS est_paye,
    d.est_remunere,
    tc.suspend_acquisition_solde,
    d.id_motif_evenement_familial,
    m.code AS code_motif_evenement,
    m.libelle AS libelle_motif_evenement,
    d.id_statut_demande_conge,
    sd.code AS code_statut_demande,
    sd.libelle AS libelle_statut_demande,
    d.date_debut,
    d.date_fin,
    d.date_demande,
    d.nb_jours_decomptes,
    d.derogation_solde_negatif,
    d.derogation_delai_preavis,
    d.extension_pathologique_maternite,
    d.est_demi_journee,
    d.moment_demi_journee,
    d.justificatif_fourni,
    d.commentaire,
    d.saisi_par,
    us.identifiant AS saisi_par_identifiant,
    d.valide_par,
    uv.identifiant AS valide_par_identifiant,
    d.date_validation,
    d.motif_refus,
    d.created_at,
    d.updated_at
FROM demande_conge d
JOIN employe e ON e.id_employe = d.id_employe
JOIN ref_type_conge tc ON tc.id_type_conge = d.id_type_conge
JOIN ref_statut_demande_conge sd ON sd.id_statut_demande_conge = d.id_statut_demande_conge
LEFT JOIN ref_motif_evenement_familial m ON m.id_motif = d.id_motif_evenement_familial
LEFT JOIN utilisateur us ON us.id_utilisateur = d.saisi_par
LEFT JOIN utilisateur uv ON uv.id_utilisateur = d.valide_par;
