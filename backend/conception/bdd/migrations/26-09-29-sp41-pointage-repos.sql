-- Statut de repos pour les bases déjà initialisées.
INSERT INTO ref_statut_pointage_jour (code, libelle, decompte_absence, necessite_saisie)
VALUES ('REPOS', 'Repos hebdomadaire', FALSE, FALSE)
ON CONFLICT (code) DO NOTHING;
