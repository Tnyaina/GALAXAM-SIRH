-- =====================================================================
-- INSERTION DES EMPLOYÉS : SIRH GALAXAM LOGISTICS, VINA SY ASA & CONSULTANTS
-- =====================================================================

INSERT INTO employe (
    matricule,
    nom,
    prenom,
    sexe,
    date_naissance,
    lieu_naissance,
    nationalite,
    cin_numero,
    cin_date_delivrance,
    cin_lieu_delivrance,
    adresse,
    telephone,
    num_affiliation_cnaps,
    est_actif
) VALUES
-- =====================================================================
-- 1. GALAXAM LOGISTICS - SIÈGE TNR & AGENCE TMM
-- =====================================================================
('1', 'RANDRASANA', 'Eric Jean-Joel', 'M', '1992-02-01', 'Antananarivo', 'Malgache', '101211101179', '2013-01-01', 'Antananarivo', 'Lot III R 50 Mandroseza, Antananarivo', '0333321819', '780531', TRUE),
('35', 'RALAMBOMANANA', 'Tatamo Michele', 'F', '1983-04-08', 'Antananarivo', 'Malgache', '117392000208', '2003-01-25', 'Antananarivo', 'Lot TD 12 Mahamasina, Antananarivo', '0348386379', '819288', TRUE),
('51', 'RASOANIRINA', 'Lala', 'F', '1994-03-23', 'Antananarivo', 'Malgache', '117392000663', '2012-06-28', 'Antananarivo', 'Lot IV C 28 Analamahitsy, Antananarivo', '0334235116', '7228290019', TRUE),
('55', 'RAHARINIRINARIVELO', 'Tahiana', 'F', '1987-10-09', 'Antananarivo', 'Malgache', '105012004431', '2009-06-19', 'Antananarivo', 'Lot B 15 Talatamaty, Antananarivo', '0347816184', '7824260013', TRUE),
('61', 'RASOANARIVO', 'Havana Hideric', 'M', '1985-12-03', 'Toamasina', 'Malgache', '301091000464', '2005-03-12', 'Toamasina', 'Lot 12/24 Boulevard Joffre, Toamasina', '0324131647', '756704', TRUE),
('135', 'NDIMBIARISON', 'Mario', 'M', '1988-06-03', 'Antananarivo', 'Malgache', '117151014611', '2006-11-28', 'Antananarivo', 'Lot III R 50 Mandroseza, Antananarivo', '0333796695', '901022001210', TRUE),
('156', 'RAJERISON', 'Bonne', 'M', '1993-01-20', 'Antananarivo', 'Malgache', '214011011975', '2014-06-16', 'Antananarivo', 'Lot II M 35 Bis Andravoahangy, Antananarivo', '0385565349', '830729002621', TRUE),
('157', 'TSARAERY', 'Daniel', 'M', '1984-06-08', 'Antananarivo', 'Malgache', '214011007076', '2004-12-19', 'Antananarivo', 'Lot IV G 12 Itaosy, Antananarivo', '0341775796', '800126001563', TRUE),
('169', 'RAZAKARIVONY', 'Elisoa', 'F', '1993-11-28', 'Toamasina', 'Malgache', '101222101630', '2015-08-16', 'Toamasina', 'Lot 45 Bazar Be, Toamasina', '0383182104', '922511001900', TRUE),
('176', 'RAZAFINDRAKETAKA', 'Lalaina', 'F', '1983-02-14', 'Antananarivo', 'Malgache', '101242086122', '2003-08-01', 'Antananarivo', 'Lot II Y 14 Bis Ampasampito, Antananarivo', '0341398864', '723215002364', TRUE),
('194', 'RAZANAMIALISOA', 'T. Felaniaina O.', 'F', '1987-10-09', 'Antananarivo', 'Malgache', '101252180807', '2008-05-12', 'Antananarivo', 'Lot VT 85 Bis Ambohimanarina, Antananarivo', '0343360408', '952313002620', TRUE),
('204', 'RANDRIANAIVO', 'Angelo Hajatiana', 'M', '1992-06-03', 'Toamasina', 'Malgache', '301011018093', '2010-04-11', 'Toamasina', 'Lot 02 Parcelle 11/42 Ankirihiry, Toamasina', '0341071277', '950606000862', TRUE),
('213', 'NOMENJANAHARY', 'Olige', 'F', '1987-03-24', 'Antananarivo', 'Malgache', '214492100150', '2007-06-25', 'Antananarivo', 'Lot VC 22 Ambohipo, Antananarivo', '0342930438', '902101035775', TRUE),
('217', 'RAKOTOARISOA', 'Solo Vojanahary', 'M', '1984-05-24', 'Antananarivo', 'Malgache', '117391005857', '2004-10-03', 'Antananarivo', 'Lot VC 22 Ambohipo, Antananarivo', '0337340033', '940518004401', TRUE),
('219', 'RAMANAMPISOA', 'Riquemain Emile', 'M', '1993-01-20', 'Antananarivo', 'Malgache', '117391009875', '2015-11-20', 'Antananarivo', 'Lot III R 50 Mandroseza, Antananarivo', '0347313360', '010924001469', TRUE),
('223', 'RAHAJANIRINA', 'Johnson Harivelo', 'M', '1983-05-18', 'Toamasina', 'Malgache', '109171008686', '2005-09-02', 'Toamasina', 'Lot 18 Morafeno, Toamasina', '0334812328', '970810006782', TRUE),
('224', 'MIHORASOA', 'Arivololona Notiavina', 'F', '1990-10-23', 'Toamasina', 'Malgache', '301052031652', '2012-07-28', 'Toamasina', 'Lot 08 Mangarivotra, Toamasina', '0383793740', '983125003817', TRUE),
('225', 'RAKOTOHARIMANANA', 'Naly Ny Aina', 'M', '1988-12-21', 'Antananarivo', 'Malgache', '117031023421', '2009-01-09', 'Antananarivo', 'Lot B 15 Talatamaty, Antananarivo', '0322238478', '010908004062', TRUE),

-- =====================================================================
-- 2. GALAXAM LOGISTICS -  LMD
-- =====================================================================
('316', 'RAKOTONIRINA', 'Aina Jeremie', 'M', '1993-08-01', 'Antananarivo', 'Malgache', 'TEMP_316', '2011-09-25', 'Antananarivo', 'Lot TD 12 Mahamasina, Antananarivo', '0323381665', NULL, TRUE),
('317', 'RAFARASON', 'Andry Niriana Onja Toky Nantenaina', 'M', '1985-05-18', 'Antananarivo', 'Malgache', 'TEMP_317', '2006-03-24', 'Antananarivo', 'Lot II M 35 Bis Andravoahangy, Antananarivo', '0342371900', NULL, TRUE),
('318', 'RAKOTOMALALA', 'Andry Moise', 'M', '1990-09-03', 'Antananarivo', 'Malgache', 'TEMP_318', '2009-09-19', 'Antananarivo', 'Lot VA 10 Tanjombato, Antananarivo', '0322961858', NULL, TRUE),
('319', 'RAKOTONJANAHARY', 'Alain Patrick', 'M', '1991-03-08', 'Antananarivo', 'Malgache', 'TEMP_319', '2012-07-06', 'Antananarivo', 'Lot III R 50 Mandroseza, Antananarivo', '0323366838', NULL, TRUE),
('320', 'NARINDRA', 'Justin Florent Ismael', 'M', '1992-06-15', 'Antananarivo', 'Malgache', 'TEMP_320', '2012-05-24', 'Antananarivo', 'Lot III R 50 Mandroseza, Antananarivo', '0334812391', NULL, TRUE),
('321', 'RAZAFIMAHATRATRA', 'Hary Nirina Elie Famonjena', 'M', '1986-06-10', 'Antananarivo', 'Malgache', 'TEMP_321', '2007-06-19', 'Antananarivo', 'Lot IV G 12 Itaosy, Antananarivo', '0321287661', NULL, TRUE),
('322', 'RAKOTONIRINA', 'Parfait', 'M', '1990-11-20', 'Antananarivo', 'Malgache', 'TEMP_322', '2012-10-18', 'Antananarivo', 'Lot VC 22 Ambohipo, Antananarivo', '0388318721', NULL, TRUE),
('323', 'RAKOTONINDRINA', 'Pascal', 'M', '1988-12-12', 'Antananarivo', 'Malgache', 'TEMP_323', '2009-12-23', 'Antananarivo', 'Lot VA 10 Tanjombato, Antananarivo', '0342617757', NULL, TRUE),
('326', 'RAJAOBELINA', 'Clark Christian E', 'M', '1991-03-24', 'Antananarivo', 'Malgache', '401011023227', '2011-09-08', 'Antananarivo', 'Lot VC 22 Ambohipo, Antananarivo', '0384784260', '810504003473', TRUE),
('327', 'RAFARALAHIZAKA', 'Joba Fanantenana', 'M', '1994-06-08', 'Antananarivo', 'Malgache', 'TEMP_327', '2015-05-11', 'Antananarivo', 'Lot AB 120 67Ha Sud, Antananarivo', '0332219758', NULL, TRUE),
('328', 'RAHELINORO', 'Anjarasoa Astine', 'F', '1982-12-26', 'Antananarivo', 'Malgache', 'TEMP_328', '2004-03-25', 'Antananarivo', 'Lot IV G 12 Itaosy, Antananarivo', '0388046772', NULL, TRUE),
('329', 'TOLOJANAHARY', 'Santatra', 'F', '1988-06-18', 'Antananarivo', 'Malgache', 'TEMP_329', '2006-10-09', 'Antananarivo', 'Lot II K 19 Ambanidia, Antananarivo', '0388279407', NULL, TRUE),
('330', 'RAJAONARIVELO', 'Sitraka Fanomezantsoa', 'M', '1992-06-25', 'Antananarivo', 'Malgache', 'TEMP_330', '2012-07-06', 'Antananarivo', 'Lot B 15 Talatamaty, Antananarivo', '0349488349', NULL, TRUE),
('331', 'LAZARIVONDRAINY', 'Edson Joachim', 'M', '1986-09-17', 'Antananarivo', 'Malgache', 'TEMP_331', '2005-02-17', 'Antananarivo', 'Lot IV G 12 Itaosy, Antananarivo', '0336683882', NULL, TRUE),
('332', 'RAKOTOVELO', 'Welldo Rocky Walfred', 'M', '1984-06-08', 'Antananarivo', 'Malgache', 'TEMP_332', '2006-02-27', 'Antananarivo', 'Lot II K 19 Ambanidia, Antananarivo', '0322336440', NULL, TRUE),
('333', 'RAKOTOARISOA', 'Bruno Patrick', 'M', '1987-10-24', 'Antananarivo', 'Malgache', 'TEMP_333', '2006-08-25', 'Antananarivo', 'Lot VT 85 Bis Ambohimanarina, Antananarivo', '0387588147', NULL, TRUE),
('334', 'RANDRIAMANJAKA', 'Joseph', 'M', '1984-12-01', 'Antananarivo', 'Malgache', 'TEMP_334', '2003-03-24', 'Antananarivo', 'Lot II Y 14 Bis Ampasampito, Antananarivo', '0349880894', NULL, TRUE),

-- =====================================================================
-- 3. VINA SY ASA SARL (VA)
-- =====================================================================
('292', 'ROBINSON', 'Ranto Lalaina', 'M', '1990-11-20', 'Antananarivo', 'Malgache', '101221056094', '2009-08-01', 'Antananarivo', 'Lot II Y 14 Bis Ampasampito, Antananarivo', '0327092823', '75041010032D', TRUE),
('295', 'RANDRIANANTENAINA', 'Tsilavohery Tiana Nirina', 'M', '1987-03-24', 'Antananarivo', 'Malgache', '117011036317', '2006-06-15', 'Antananarivo', 'Lot IV G 12 Itaosy, Antananarivo', '0385966453', NULL, TRUE),
('296', 'RANDRIAMIHAJA', 'Fela Nantenaina', 'F', '1986-06-10', 'Antananarivo', 'Malgache', '117012022711', '2005-09-04', 'Antananarivo', 'Lot II Y 14 Bis Ampasampito, Antananarivo', '0328224097', '923028001994', TRUE),
('297', 'HAMAD', 'Najma', 'M', '1988-12-12', 'Antananarivo', 'Malgache', 'TEMP_297', '2009-07-28', 'Antananarivo', 'Lot II M 35 Bis Andravoahangy, Antananarivo', '0389330962', NULL, TRUE),

-- =====================================================================
-- 4. CONSULTANTS (GLX)
-- =====================================================================
('396', 'RAZAFINJATO', 'Serge Didier', 'M', '1961-04-14', 'Antananarivo', 'Malgache', 'TEMP_396', '1983-09-18', 'Antananarivo', 'Lot II K 19 Ambanidia, Antananarivo', '0342938830', NULL, TRUE),
('397', 'RAKOTOARISOA', 'Julien', 'M', '1959-10-09', 'Antananarivo', 'Malgache', 'TEMP_397', '1979-05-12', 'Antananarivo', 'Lot IV C 28 Analamahitsy, Antananarivo', '0328892463', NULL, TRUE)

ON CONFLICT (matricule) DO UPDATE 
SET nom = EXCLUDED.nom,
    prenom = EXCLUDED.prenom,
    sexe = EXCLUDED.sexe,
    date_naissance = EXCLUDED.date_naissance,
    lieu_naissance = EXCLUDED.lieu_naissance,
    nationalite = EXCLUDED.nationalite,
    cin_numero = EXCLUDED.cin_numero,
    cin_date_delivrance = EXCLUDED.cin_date_delivrance,
    cin_lieu_delivrance = EXCLUDED.cin_lieu_delivrance,
    adresse = EXCLUDED.adresse,
    telephone = EXCLUDED.telephone,
    num_affiliation_cnaps = EXCLUDED.num_affiliation_cnaps,
    est_actif = EXCLUDED.est_actif,
    updated_at = CURRENT_TIMESTAMP;

-- Situation familiale des consultants
UPDATE employe SET situation_familiale = 'Marie' WHERE matricule IN ('396', '397');