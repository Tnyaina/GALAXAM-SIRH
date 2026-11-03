-- Un choix explicite de 0 reporte la retenue de cette campagne sans effacer la dette.
ALTER TABLE paie_avance_retenue_choix
    DROP CONSTRAINT IF EXISTS paie_avance_retenue_choix_montant_check;
ALTER TABLE paie_avance_retenue_choix
    ADD CONSTRAINT paie_avance_retenue_choix_montant_check CHECK (montant >= 0);
