-- ============================================================
-- PATCH (OBSOLÈTE — maintenu uniquement pour traçabilité)
-- Date   : 2026-08-26
-- Motif  : 'ALERTE_ECHEANCE' a été ajouté directement dans
--          26-08-10-sp2-notifications.sql (source unique).
--
-- Si tu travailles sur une base déjà initialisée SANS avoir
-- relancé sp2-notifications.sql depuis le début, exécute
-- uniquement ce bloc :
-- ============================================================

ALTER TABLE notification_rh DROP CONSTRAINT IF EXISTS chk_notif_type;

ALTER TABLE notification_rh ADD CONSTRAINT chk_notif_type CHECK (type_alerte IN (
    -- Contrats & Essais
    'CONTRAT_EXPIRE_J', 'CONTRAT_EXPIRE_7J', 'CONTRAT_EXPIRE_30J',
    'ESSAI_EXPIRE_J', 'ESSAI_EXPIRE_7J', 'ESSAI_EXPIRE_15J', 'ESSAI_EXPIRE_30J',
    -- Mouvements RH
    'BROUILLON_EN_ATTENTE', 'MOUVEMENT_APPLIQUE',
    'DOCUMENTS_MANQUANTS', 'EMPLOYE_CREE', 'BROUILLON_CREE',
    -- Congés & Absences
    'CONGE_EN_ATTENTE', 'DEMANDE_CONGE_EN_ATTENTE', 'CONGE_VALIDE', 'CONGE_REFUSE', 'CONGE_ANNULE',
    'SOLDE_CONGE_FAIBLE', 'SOLDE_CONGE_NEGATIF',
    'RETOUR_CONGE_J', 'FRACTIONNEMENT_NON_RESPECTE',
    'MALADIE_PLAFOND_EPISODE_ATTEINT', 'CONGE_EXPIRATION_PROCHE',
    'POOL_EXCEPTIONNEL_EPUISE', 'BONIFICATION_ANCIENNETE_APPLIQUEE',
    -- Pointage
    'POINTAGE_MANQUANT', 'PERIODE_POINTAGE_A_VALIDER',
    'PERIODE_POINTAGE_VALIDEE', 'RETARD_RECURRENT',
    -- Alertes génériques
    'ALERTE_ECHEANCE'
));
