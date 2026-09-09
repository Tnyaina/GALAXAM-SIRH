-- ============================================================================
-- SCRIPT DE MISE A JOUR DU MODULE PAIE - AJUSTEMENTS CONTRE-AUDIT
-- Date : 2026-09-11
-- Description : 
--   1. Création de la table des honoraires et prestations externes (paie_honoraire_prestation)
--      pour isoler juridiquement les consultants du registre des salariés.
--   2. Ajout de la colonne est_plafonne_sante dans site_cotisation et ref_site.
--   3. Actualisation de la rubrique AJUSTEMENT_ARRONDI (sens AJUSTEMENT, non brut).
-- ============================================================================

-- 1. Table des Honoraires et Prestations Externes (Régime ISI 5%)
CREATE TABLE IF NOT EXISTS paie_honoraire_prestation (
    id_prestation SERIAL PRIMARY KEY,
    id_periode_paie INTEGER REFERENCES periode_paie(id_periode_paie) ON DELETE SET NULL,
    id_intervenant INTEGER REFERENCES employe(id_employe) ON DELETE SET NULL,
    nom_complet VARCHAR(150) NOT NULL,
    cin_ou_nif VARCHAR(30) NOT NULL,
    intitule_prestation VARCHAR(150) NOT NULL,
    nature_service VARCHAR(100) DEFAULT 'SERVICE SYSTEMATIQUE',
    date_prestation DATE NOT NULL,
    date_paiement DATE,
    montant_brut NUMERIC(12, 2) NOT NULL,
    taux_isi NUMERIC(5, 2) DEFAULT 5.00,
    montant_retenue_isi NUMERIC(12, 2) NOT NULL,
    montant_net_paye NUMERIC(12, 2) NOT NULL,
    adresse VARCHAR(200),
    commune VARCHAR(100) DEFAULT 'ANTANANARIVO RENIVOHITRA C.U',
    region VARCHAR(100) DEFAULT 'ANALAMANGA',
    district VARCHAR(100) DEFAULT 'ANTANANARIVO RENIVOHITRA',
    province VARCHAR(100) DEFAULT 'ANTANANARIVO',
    reference_facture VARCHAR(50),
    statut VARCHAR(30) DEFAULT 'VALIDE' CHECK (statut IN ('BROUILLON', 'VALIDE', 'PAYE', 'ANNULE')),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_honoraire_periode ON paie_honoraire_prestation(id_periode_paie);
CREATE INDEX IF NOT EXISTS idx_honoraire_intervenant ON paie_honoraire_prestation(id_intervenant);

-- 2. Ajout de l'indicateur de plafonnement santé dans site_cotisation
ALTER TABLE site_cotisation ADD COLUMN IF NOT EXISTS est_plafonne_sante BOOLEAN DEFAULT true;

-- 3. Mise à jour du libellé et sens de la rubrique AJUSTEMENT_ARRONDI dans paie_rubrique
UPDATE paie_rubrique 
SET libelle = 'Ajustement Arrondi Monétaire (Écart de Règlement)',
    sens = 'NEUTRE',
    categorie = 'NET',
    soumis_cnaps = false,
    soumis_sante = false,
    soumis_irsa = false
WHERE code = 'AJUSTEMENT_ARRONDI';
