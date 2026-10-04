-- Une date civile appartient à une seule campagne de paie, bornes inclusives.
-- Avant application : SELECT current_database();
-- Les chevauchements historiques doivent être résolus explicitement ; aucune date n'est déplacée ici.
ALTER TABLE periode_paie
    ADD CONSTRAINT ex_periode_paie_dates
    EXCLUDE USING gist (daterange(date_debut, date_fin, '[]') WITH &&);
