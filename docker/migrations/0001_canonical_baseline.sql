-- Point de depart : les six schemas canoniques existent deja.
-- Ne cree, ne vide et ne remplace aucune table metier.
DO $$ BEGIN
 IF to_regclass('public.utilisateur') IS NULL
    OR to_regclass('public.employe') IS NULL
    OR to_regclass('public.periode_paie') IS NULL
    OR to_regclass('public.paie_config_legale') IS NULL THEN
   RAISE EXCEPTION 'Schema canonique absent : initialiser les six schemas avant les migrations';
 END IF;
END $$;
