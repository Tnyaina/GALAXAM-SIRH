-- ============================================================================
-- MIGRATION : Ajout des drapeaux d'activation et de protection des paramètres
-- Date : 2026-09-11
-- Objet : 
--   1. Distinction sémantique INACTIF vs INTROUVABLE
--   2. Protection des constantes système (est_desactivable = FALSE)
--   3. Possibilité de débrayage pour les règles RH opérationnelles (est_desactivable = TRUE)
-- ============================================================================

-- 1. Ajout des colonnes si inexistantes
ALTER TABLE parametre_global 
    ADD COLUMN IF NOT EXISTS est_actif BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE parametre_global 
    ADD COLUMN IF NOT EXISTS est_desactivable BOOLEAN NOT NULL DEFAULT FALSE;

-- 2. Configuration des règles opérationnelles débrayables (est_desactivable = TRUE)
-- Ces règles peuvent être coupées individuellement par l'administrateur RH :
UPDATE parametre_global SET est_desactivable = TRUE WHERE code_parametre IN (
    -- Pointage & Restauration
    'REDUCTION_DEPLACT_REPAS_MIDI',
    'SEUIL_2ND_REPAS_1',
    'MONTANT_2ND_REPAS_1',
    'SEUIL_2ND_REPAS_2',
    'MONTANT_2ND_REPAS_2',
    
    -- Congés & Dérogations
    'PLAFOND_SOLDE_CONGE_NEGATIF',
    'SEUIL_ALERTE_SOLDE_CONGE_FAIBLE',
    'SEUIL_ANCIENNETE_BONIFICATION_ANS',
    'PALIER_ANNEES_BONIFICATION',
    'JOURS_BONIFICATION_PAR_PALIER'
);

-- 3. Verrouillage des constantes système incompressibles (est_desactivable = FALSE)
-- Ces constantes sont indispensables aux calculs fondamentaux et ne doivent jamais être coupées :
UPDATE parametre_global SET est_desactivable = FALSE WHERE code_parametre IN (
    'SEUIL_HEURES_JOUR',
    'SEUIL_NUIT_DEBUT',
    'SEUIL_NUIT_FIN',
    'MONTANT_REPAS_MIDI',
    'SEUIL_PAUSE_MIDI_MIN',
    'DUREE_MAX_CDD_MOIS',
    'NB_MAX_RENOUVELLEMENTS_CDD',
    'MAX_RENOUVELLEMENTS_ESSAI',
    'PLAFOND_CUMUL_SOLDE_CONGE_JOURS',
    'DUREE_PRESCRIPTION_SOLDE_CONGE_MOIS',
    'DELAI_PREAVIS_COURT_JOURS',
    'DELAI_PREAVIS_LONG_JOURS',
    'SEUIL_JOURS_PREAVIS_LONG',
    'DUREE_MAX_MATERNITE_STANDARD_JOURS',
    'DUREE_MAX_MATERNITE_PATHOLOGIQUE_JOURS'
);
