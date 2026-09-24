-- sp26 : Enrichissement de pointage_snapshot_jour avec l'heure effective utilisée pour le calcul.
--
-- Contexte :
--   sp25 a introduit heure_entree_matin_effective dans pointage_jour pour figer le plancher
--   d'arrivée valorisé (earliest_paid_start) au moment de la saisie. Sans cette colonne dans
--   le snapshot, il est impossible de reconstituer exactement quel temps a été comptabilisé
--   lors d'un gel donné si la règle site ou la saisie ont été modifiées avant un re-gel.
--
-- Valeur historique :
--   Les snapshots existants auront NULL ici — acceptable pour les anciens gels (avant sp25,
--   le badgeage brut était utilisé directement). Seuls les nouveaux gels bénéficieront
--   de la provenance complète.
--
-- Lecture :
--   Cette colonne est une colonne d'audit. Elle n'est pas lue en runtime applicatif.
--   Elle sert à répondre à : "quelle heure effective a été valorisée pour ce salarié
--   ce jour lors du gel V1 ?"

ALTER TABLE pointage_snapshot_jour
    ADD COLUMN IF NOT EXISTS heure_entree_effective TIME;

COMMENT ON COLUMN pointage_snapshot_jour.heure_entree_effective IS
    'Heure effective utilisée pour le calcul au moment du gel (max(badge, plancher_arrivee)). '
    'NULL sur les gels antérieurs à sp25 : le badge brut était utilisé directement.';
