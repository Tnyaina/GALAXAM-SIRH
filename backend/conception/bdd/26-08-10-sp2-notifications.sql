-- ============================================================
-- Migration : Table notification_rh
-- Standard professionnel SIRH : les alertes métier sont persistées
-- en base, générées par le scheduler nocturne, et marquées lues
-- par l'utilisateur depuis le frontend.
-- ============================================================

DROP TABLE IF EXISTS notification_rh CASCADE;

CREATE TABLE notification_rh (
    id_notification     SERIAL PRIMARY KEY,
    id_utilisateur      INTEGER,          -- NULL = broadcast tous les utilisateurs RH
    type_alerte         VARCHAR(50) NOT NULL,
    -- CONTRAT_EXPIRE_J       : contrat dont date_fin = aujourd'hui
    -- CONTRAT_EXPIRE_7J      : contrat expirant dans 7 jours
    -- CONTRAT_EXPIRE_30J     : contrat expirant dans 30 jours
    -- ESSAI_EXPIRE_J         : période d'essai échéant aujourd'hui
    -- ESSAI_EXPIRE_7J        : essai expirant dans 7 jours
    -- BROUILLON_EN_ATTENTE   : workflow BROUILLON non traité depuis > 48h
    -- MOUVEMENT_APPLIQUE     : mouvement automatique effectué ce matin
    priorite            VARCHAR(10) NOT NULL DEFAULT 'NORMALE',
    -- CRITIQUE, HAUTE, NORMALE, INFO
    titre               VARCHAR(200) NOT NULL,
    message             TEXT NOT NULL,
    lien                VARCHAR(500),     -- URL frontend cible (ex: /employes/42)
    id_employe_concerne INTEGER,          -- Pour navigation directe
    est_lue             BOOLEAN DEFAULT FALSE,
    date_creation       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    date_lecture        TIMESTAMP,
    date_expiration     TIMESTAMP,        -- Après cette date, ne plus afficher

    CONSTRAINT fk_notif_utilisateur FOREIGN KEY (id_utilisateur) REFERENCES utilisateur(id_utilisateur) ON DELETE CASCADE,
    CONSTRAINT fk_notif_employe     FOREIGN KEY (id_employe_concerne) REFERENCES employe(id_employe) ON DELETE CASCADE,
    CONSTRAINT chk_notif_priorite   CHECK (priorite IN ('CRITIQUE', 'HAUTE', 'NORMALE', 'INFO')),
    CONSTRAINT chk_notif_type       CHECK (type_alerte IN (
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
    ))
);

-- Index de performance (requêtes fréquentes)
CREATE INDEX idx_notif_utilisateur ON notification_rh(id_utilisateur) WHERE est_lue = FALSE;
CREATE INDEX idx_notif_non_lues    ON notification_rh(est_lue, date_creation DESC);
CREATE INDEX idx_notif_type        ON notification_rh(type_alerte);
CREATE INDEX idx_notif_employe     ON notification_rh(id_employe_concerne);
