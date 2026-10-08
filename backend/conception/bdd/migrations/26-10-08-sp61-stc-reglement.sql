-- SP61 : séparation du calcul validé et du paiement effectivement constaté.
ALTER TABLE paie_stc ADD COLUMN IF NOT EXISTS date_reglement DATE;
ALTER TABLE paie_stc ADD COLUMN IF NOT EXISTS reference_reglement VARCHAR(255);
ALTER TABLE paie_stc ADD COLUMN IF NOT EXISTS regle_par INT REFERENCES utilisateur(id_utilisateur);

UPDATE ref_type_bulletin SET libelle = 'Bulletin de sortie et décompte STC'
WHERE code = 'STC' AND libelle = 'Solde de Tout Compte Libératoire';
