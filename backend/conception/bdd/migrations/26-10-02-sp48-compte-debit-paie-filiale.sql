-- Le compte débité dans l'ordre de virement appartient à la filiale employeur.
-- Aucune valeur de démonstration n'est reprise automatiquement.
ALTER TABLE ref_filiale ADD COLUMN IF NOT EXISTS rib_compte_debit_paie VARCHAR(50);
