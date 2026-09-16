-- ==============================================================================
-- SPRINT 2B : ALIGNEMENT DU WORKFLOW POINTAGE ➔ PAIE (CYCLE RECOMMANDÉ)
-- Date : 2026-09-16
-- Auteur : Équipe SIRH GALAXAM
-- Objectif : 
--   1. Clarifier l'état de fin de pointage (VALIDEE -> Pointage Scellé & Figé).
--   2. Ajouter l'état officiel de fin de paie PAIE_VALIDEE (id 7).
--   3. Mettre à jour la séquence de statut de période.
-- ==============================================================================

BEGIN;

-- 1. Mise à jour des libellés des statuts existants pour supprimer les ambiguïtés
UPDATE ref_statut_periode_paie 
SET libelle = 'Transmise à la DAF — Arbitrage' 
WHERE code = 'TRANSMIS_DAF';

UPDATE ref_statut_periode_paie 
SET libelle = 'Pointage Scellé & Figé — Prêt pour calcul Paie' 
WHERE code = 'VALIDEE';

UPDATE ref_statut_periode_paie 
SET libelle = 'Clôturée — archivée, immuable' 
WHERE code = 'CLOTUREE';

-- 2. Insertion du statut officiel de validation des bulletins de paie
INSERT INTO ref_statut_periode_paie (id_statut_periode, code, libelle)
VALUES (7, 'PAIE_VALIDEE', 'Paie Validée & Approuvée — Prête pour virement et clôture')
ON CONFLICT (code) DO UPDATE SET 
    libelle = EXCLUDED.libelle;

-- 3. Réajustement de la séquence pour les insertions futures
SELECT setval(
    'ref_statut_periode_paie_id_statut_periode_seq', 
    (SELECT GREATEST(MAX(id_statut_periode), 7) FROM ref_statut_periode_paie)
);

COMMIT;
