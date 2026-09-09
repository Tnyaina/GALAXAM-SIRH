-- ============================================================
-- SPRINT 11 : ALTERATIONS POINTAGE & WORKFLOW COCKPIT
-- Date : 2026-09-09
-- 1. Champs de traçabilité workflow transmission & arbitrage DAF
-- 2. Flags backend-driven catégories professionnelles (Cadre, Autonome)
-- 3. Nettoyage et consolidation des menus Pointage (3 étapes unifiées)
-- ============================================================

BEGIN;

-- ------------------------------------------------------------
-- 1. ALTER TABLE : periode_paie (transmission Agent -> DAF)
-- ------------------------------------------------------------
ALTER TABLE periode_paie 
    ADD COLUMN IF NOT EXISTS date_transmission TIMESTAMP,
    ADD COLUMN IF NOT EXISTS transmis_par INTEGER REFERENCES utilisateur(id_utilisateur);

-- Assurer la présence du statut TRANSMIS_DAF
INSERT INTO ref_statut_periode_paie (code, libelle) VALUES
    ('TRANSMIS_DAF', 'Transmise à la DAF')
ON CONFLICT (code) DO NOTHING;

-- ------------------------------------------------------------
-- 2. ALTER TABLE : pointage_jour (traçabilité ajustement DAF)
-- ------------------------------------------------------------
ALTER TABLE pointage_jour
    ADD COLUMN IF NOT EXISTS ajuste_par_daf BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS ajuste_par INTEGER REFERENCES utilisateur(id_utilisateur),
    ADD COLUMN IF NOT EXISTS date_ajustement TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_pointage_ajuste_daf ON pointage_jour(ajuste_par_daf) 
    WHERE ajuste_par_daf = TRUE;

-- ------------------------------------------------------------
-- 3. ALTER TABLE : ref_categorie_professionnelle (backend-driven)
-- ------------------------------------------------------------
ALTER TABLE ref_categorie_professionnelle
    ADD COLUMN IF NOT EXISTS est_cadre BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS dispense_pointage BOOLEAN NOT NULL DEFAULT FALSE;

-- Synchronisation des statuts légaux sur les catégories existantes
UPDATE ref_categorie_professionnelle 
SET est_cadre = TRUE, est_eligible_hs = FALSE 
WHERE UPPER(code) LIKE '%CADRE%' OR UPPER(code) IN ('C', 'HC', 'DIRIGEANT');

UPDATE ref_categorie_professionnelle 
SET est_cadre = FALSE, est_eligible_hs = TRUE 
WHERE UPPER(code) NOT LIKE '%CADRE%' AND UPPER(code) NOT IN ('C', 'HC', 'DIRIGEANT');

-- ------------------------------------------------------------
-- 4. CONSOLIDATION MENUS : Cockpit Pointage 3 étapes
-- Étape 1 : Saisie Quotidienne (/pointages?tab=saisie)
-- Étape 2 : Suivi & Anomalies (/pointages?tab=suivi)
-- Étape 3 : Arbitrage DAF (/pointages?tab=validation)
-- ------------------------------------------------------------

-- Suppression des anciens sous-menus autonomes désormais intégrés
DELETE FROM role_menu WHERE id_menu IN ('POINTAGE_ANOMALIES', 'POINTAGE_SYNTHESE');
DELETE FROM ref_menu WHERE id_menu IN ('POINTAGE_ANOMALIES', 'POINTAGE_SYNTHESE');

-- Mise à jour des 3 sous-menus officiels
INSERT INTO ref_menu (id_menu, libelle, icone, href, rang, niveau, id_pere) VALUES
    ('POINTAGE_JOURNALIER',  'Saisie Quotidienne',  'ClockCheck',    '/pointages?tab=saisie',     1, 2, 'POINTAGE'),
    ('POINTAGE_SUIVI',       'Suivi & Anomalies',   'CalendarCheck', '/pointages?tab=suivi',      2, 2, 'POINTAGE'),
    ('POINTAGE_VALIDATION',  'Arbitrage DAF',       'ShieldCheck',   '/pointages?tab=validation', 3, 2, 'POINTAGE')
ON CONFLICT (id_menu) DO UPDATE SET 
    libelle = EXCLUDED.libelle, 
    href = EXCLUDED.href, 
    rang = EXCLUDED.rang, 
    icone = EXCLUDED.icone, 
    id_pere = EXCLUDED.id_pere;

-- Attribution des droits aux rôles :
-- ROLE_ADMIN / DAF (id_role = 1) : Accès aux 3 étapes
INSERT INTO role_menu (id_role, id_menu) VALUES
    (1, 'POINTAGE_JOURNALIER'),
    (1, 'POINTAGE_SUIVI'),
    (1, 'POINTAGE_VALIDATION')
ON CONFLICT DO NOTHING;

-- ROLE_AGENT_POINTAGE (id_role = 2) : Accès exploitation uniquement (Saisie et Suivi)
DELETE FROM role_menu WHERE id_role = 2 AND id_menu = 'POINTAGE_VALIDATION';
INSERT INTO role_menu (id_role, id_menu) VALUES
    (2, 'POINTAGE_JOURNALIER'),
    (2, 'POINTAGE_SUIVI')
ON CONFLICT DO NOTHING;

-- ------------------------------------------------------------
-- 5. SÉCURISATION INTÉGRITÉ DES INDEMNITÉS DE DÉPLACEMENT
-- ------------------------------------------------------------
ALTER TABLE pointage_jour ALTER COLUMN deplacement_matin SET DEFAULT FALSE;
ALTER TABLE pointage_jour ALTER COLUMN deplacement_am SET DEFAULT FALSE;
ALTER TABLE pointage_jour ALTER COLUMN indemnite_deplacement_recue SET DEFAULT FALSE;

-- Nettoyage des pointages non travaillés (repos, fériés chômés, congés, absences) indûment crédités de déplacement
UPDATE pointage_jour p
SET deplacement_matin = FALSE,
    deplacement_am = FALSE,
    indemnite_deplacement_recue = FALSE,
    indemnite_repas_recue = FALSE,
    montant_repas = 0
FROM ref_statut_pointage_jour st
WHERE p.id_statut_jour = st.id_statut_jour
  AND (
      st.code IN ('CONGE', 'FERIE_CHOME', 'ABSENCE_NON_JUSTIFIEE', 'ABSENCE_JUSTIFIEE', 'REPOS')
      OR (p.total_heures = 0 AND EXTRACT(DOW FROM p.date_pointage) IN (0, 6))
  );


COMMIT;
