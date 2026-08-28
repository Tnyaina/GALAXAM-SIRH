-- ============================================================
-- PATCH MIGRATION : FRACTIONNEMENT INDEMNITÉ DÉPLACEMENT POINTAGE
-- Date : 28/08/2026
-- Description : Ajout des colonnes deplacement_matin et deplacement_am
-- ============================================================

-- 1. Ajout des colonnes dans pointage_jour
ALTER TABLE pointage_jour 
    ADD COLUMN IF NOT EXISTS deplacement_matin BOOLEAN DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS deplacement_am BOOLEAN DEFAULT TRUE;

-- 2. Initialisation des lignes existantes à partir de l'ancienne colonne indemnite_deplacement_recue
UPDATE pointage_jour
SET deplacement_matin = COALESCE(indemnite_deplacement_recue, TRUE),
    deplacement_am = COALESCE(indemnite_deplacement_recue, TRUE)
WHERE deplacement_matin IS NULL OR deplacement_am IS NULL;

-- 3. Mise à jour de la vue v_pointage_complet
CREATE OR REPLACE VIEW v_pointage_complet AS
SELECT
    p.id_pointage,
    p.id_employe,
    e.matricule,
    e.nom || ' ' || e.prenom AS nom_prenom,
    p.id_periode_paie,
    pp.code AS code_periode,
    p.id_site,
    rs.libelle AS libelle_site,
    p.date_pointage,
    p.heure_entree_matin, p.heure_sortie_matin,
    p.heure_entree_am, p.heure_sortie_am,
    st.code AS code_statut, st.libelle AS libelle_statut,
    p.heures_matin, p.heures_am, p.total_heures, p.heures_nuit,
    p.minutes_retard, p.minutes_depart_anticipe,
    p.indemnite_deplacement_recue, p.deplacement_matin, p.deplacement_am, p.montant_deplacement_jour,
    p.indemnite_repas_recue, p.montant_repas,
    p.utilise_vehicule_fonction_jour, p.horaire_continu_jour,
    p.motif_ajustement, p.saisi_par, p.created_at, p.updated_at
FROM pointage_jour p
JOIN employe e ON p.id_employe = e.id_employe
JOIN periode_paie pp ON p.id_periode_paie = pp.id_periode_paie
JOIN ref_site rs ON p.id_site = rs.id_site
JOIN ref_statut_pointage_jour st ON p.id_statut_jour = st.id_statut_jour;

-- 4. Mise à jour de la vue v_synthese_pointage_mensuelle avec décompte fractionné
CREATE OR REPLACE VIEW v_synthese_pointage_mensuelle AS
SELECT
    p.id_employe,
    p.id_periode_paie,
    COUNT(*) FILTER (WHERE s.code IN ('ABSENCE_NON_JUSTIFIEE', 'ABSENCE_JUSTIFIEE')) AS nb_jours_absence,
    COUNT(*) FILTER (WHERE s.code = 'FERIE_TRAVAILLE')              AS nb_jours_ferie_travaille,
    COUNT(*) FILTER (WHERE s.code = 'SAMEDI_TRAVAILLE')             AS nb_jours_samedi_travaille,
    COUNT(*) FILTER (WHERE s.code = 'DIMANCHE_TRAVAILLE')           AS nb_jours_dimanche_travaille,
    COALESCE(SUM(p.total_heures) FILTER (WHERE s.decompte_absence = FALSE), 0) AS total_heures_travaillees,
    COALESCE(SUM(p.heures_nuit), 0)                                 AS heures_nuit_total,
    COALESCE(SUM(
        CASE 
            WHEN p.deplacement_matin IS NOT NULL AND p.deplacement_am IS NOT NULL 
                THEN (CASE WHEN p.deplacement_matin = TRUE THEN 0.5 ELSE 0.0 END + CASE WHEN p.deplacement_am = TRUE THEN 0.5 ELSE 0.0 END)
            WHEN p.indemnite_deplacement_recue = TRUE THEN 1.0
            ELSE 0.0 
        END
    ), 0) AS nb_jours_deplacement,
    COUNT(*) FILTER (WHERE p.indemnite_repas_recue)                 AS nb_jours_repas,
    COALESCE(SUM(p.montant_repas), 0)                               AS montant_repas_total
FROM pointage_jour p
JOIN ref_statut_pointage_jour s ON p.id_statut_jour = s.id_statut_jour
GROUP BY p.id_employe, p.id_periode_paie;
