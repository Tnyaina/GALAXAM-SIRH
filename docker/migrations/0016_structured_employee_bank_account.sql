-- Les segments du RIB appartiennent au compte de l'employé, pas à la banque.
ALTER TABLE employe_compte_banque
    ADD COLUMN code_guichet VARCHAR(5),
    ADD COLUMN numero_compte VARCHAR(11),
    ADD COLUMN cle_rib VARCHAR(2);

-- Compatible avec une base déjà peuplée lorsque le RIB historique est numérique et complet.
UPDATE employe_compte_banque c
SET code_guichet = SUBSTRING(REGEXP_REPLACE(c.num_compte_rib, '[[:space:]-]', '', 'g') FROM 6 FOR 5),
    numero_compte = SUBSTRING(REGEXP_REPLACE(c.num_compte_rib, '[[:space:]-]', '', 'g') FROM 11 FOR 11),
    cle_rib = SUBSTRING(REGEXP_REPLACE(c.num_compte_rib, '[[:space:]-]', '', 'g') FROM 22 FOR 2)
WHERE REGEXP_REPLACE(c.num_compte_rib, '[[:space:]-]', '', 'g') ~ '^[0-9]{23}$'
  AND SUBSTRING(REGEXP_REPLACE(c.num_compte_rib, '[[:space:]-]', '', 'g') FROM 1 FOR 5)
      = (SELECT b.code_banque FROM ref_banque b WHERE b.id_banque = c.id_banque);
