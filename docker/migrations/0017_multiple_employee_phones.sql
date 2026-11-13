ALTER TABLE employe
    ADD COLUMN telephones_secondaires TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[];

ALTER TABLE employe_contact_urgence
    ADD COLUMN telephones_secondaires TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[];
