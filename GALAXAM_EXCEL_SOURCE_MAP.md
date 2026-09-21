# CARTOGRAPHIE DES SOURCES EXCEL HISTORIQUES — SIRH GALAXAM
> Document d'inventaire technique exhaustif généré dans le cadre de la Phase 1 de certification.
> Analyse structurelle, dimensionnelle, typologique et sémantique des 3 classeurs historiques.

## 📁 Fichier : `model/ohatra presence svt clocker.xlsx` (OOXML (.xlsx))
- **Plages nommées (Named Ranges)** : `Aucune`
- **Nombre d'onglets (Sheets)** : `5`

### Onglet : `pointage extrait machine`
- **Dimensions max** : 1000 lignes × 59 colonnes
- **Plage réellement utilisée** : Lignes `2..43` | Colonnes `1..56` (41 lignes occupées, 54 colonnes occupées)
- **Cellules fusionnées (651)** : `['AS38:AT38', 'K33:L33', 'AU38:AV38', 'AW38:AX38', 'B12:C12', 'K35:L35', 'AU40:AV40', 'M35:N35', 'B11:F11', 'B14:C14']`
- **Typologie des cellules** :
  - Numériques : **145**
  - Chaînes de caractères : **210**
  - Formules : **227**
  - Dates : **192**
  - Booléens : **0**
  - Vides (dans la zone) : **58226**
- **En-têtes détectés** :
  - *Ligne 4* : `A4:Département` | `B4:admin` | `I4:Nom` | `J4:RAKOTO` | `T4:Département` | `U4:admin` | `AB4:Nom` | `AC4:RAKETAKA`
  - *Ligne 5* : `A5:Date` | `B5:26-11-2025～25-12-2025` | `I5:Matricule` | `J5:4.0` | `T5:Date` | `U5:26-11-2025～25-12-2025` | `AB5:Matricule` | `AC5:2.0`
- **Échantillon de formules significatives (227 formules distinctes)** :
  - `K13` : `=D13-B13` (Valeur en cache : `1899-12-29 16:10:00.046000`)
  - `M13` : `=I13-G13` (Valeur en cache : `16:36:59.974000`)
  - `O13` : `=K13+M13` (Valeur en cache : `08:47:00.020000`)
  - `P13` : `=O13/"01:00"` (Valeur en cache : `8.783338928`)
  - `AD13` : `=W13-U13` (Valeur en cache : `05:14:59.912000`)
  - `AF13` : `=AB13-Z13` (Valeur en cache : `03:36:00.044000`)
  - `AH13` : `=AD13+AF13` (Valeur en cache : `08:50:59.956000`)
  - `AI13` : `=AH13/"01:00"` (Valeur en cache : `8.849987793`)
  - `K14` : `=D14-B14` (Valeur en cache : `1899-12-29 16:09:00.060000`)
  - `M14` : `=I14-G14` (Valeur en cache : `16:49:59.949000`)

### Onglet : `calcul DEPLACT ET REPAS`
- **Dimensions max** : 1000 lignes × 26 colonnes
- **Plage réellement utilisée** : Lignes `1..42` | Colonnes `1..25` (27 lignes occupées, 20 colonnes occupées)
- **Cellules fusionnées (1)** : `['G1:I1']`
- **Typologie des cellules** :
  - Numériques : **17**
  - Chaînes de caractères : **105**
  - Formules : **23**
  - Dates : **0**
  - Booléens : **0**
  - Vides (dans la zone) : **25855**
- **En-têtes détectés** :
  - *Ligne 1* : `G1:Prorata des indemnites pour les site` | `J1:Jours ouvrable du mois` | `M1:deplact tnr` | `N1:31200.0`
  - *Ligne 2* : `F2:DESAMBRA 2025` | `G2:tnr (DPLCT)` | `H2:tmm (DPLCT)` | `I2:tmm moto` | `J2:22.0` | `K2:DES (REPAS)` | `M2:1000 * (jours ouvrables)` | `N2:=1000*J2` | `W2:20mn*nbre jr mensuel`
  - *Ligne 3* : `G3:=N1/J2` | `H3:=N3/J2` | `I3:=G3` | `K3:2000.0` | `M3:deplact tmm` | `N3:=N2+N1` | `O3:- 1000 Ar si reçoit repas midi` | `V3:nbre jour` | `W3:heure hs cadre` | `Y3:15mn*nbre jr mensuel`
  - *Ligne 4* : `K4:report automatique`
  - *Ligne 5* : `A5:HM` | `C5:Retard` | `E5:TNR` | `F5:NOMS ET PRENOMS` | `G5:DPLCT` | `H5:DPLCT ` | `I5:EXTRA` | `J5:deplact+autres Extra` | `K5:REPAS ` | `L5:REPAS`
- **Échantillon de formules significatives (23 formules distinctes)** :
  - `N2` : `=1000*J2` (Valeur en cache : `22000`)
  - `G3` : `=N1/J2` (Valeur en cache : `1418.181818`)
  - `H3` : `=N3/J2` (Valeur en cache : `2418.181818`)
  - `I3` : `=G3` (Valeur en cache : `1418.181818`)
  - `N3` : `=N2+N1` (Valeur en cache : `53200`)
  - `V5` : `=J2` (Valeur en cache : `22`)
  - `W5` : `=20/60*21` (Valeur en cache : `7`)
  - `Y5` : `=0.25*J2` (Valeur en cache : `5.5`)
  - `H6` : `=G6*$G$3` (Valeur en cache : `26945.45455`)
  - `J6` : `=I6+H6` (Valeur en cache : `26945.45455`)

### Onglet : `AJUSTEMENT`
- **Dimensions max** : 1000 lignes × 26 colonnes
- **Plage réellement utilisée** : Lignes `3..23` | Colonnes `3..21` (16 lignes occupées, 19 colonnes occupées)
- **Cellules fusionnées (0)** : `[]`
- **Typologie des cellules** :
  - Numériques : **22**
  - Chaînes de caractères : **72**
  - Formules : **17**
  - Dates : **0**
  - Booléens : **0**
  - Vides (dans la zone) : **25889**
- **En-têtes détectés** :
  - *Ligne 3* : `D3:PRORATA PRIME PERFO DESAMBRA 2025`
  - *Ligne 4* : `D4:mise en forme 02/12/2025` | `G4:jour ferié` | `H4:0.0` | `I4:depl Hors Wend` | `J4:22.0` | `K4:0.0` | `M4:(E)`
  - *Ligne 5* : `G5:PP-PA` | `H5:=+J4-H4` | `I5:A` | `J5:B` | `K5:C` | `L5:D` | `M5:A-B-C+D` | `N5:F` | `O5:F+E` | `S5:saisie manuelle`
- **Échantillon de formules significatives (17 formules distinctes)** :
  - `H5` : `=+J4-H4` (Valeur en cache : `22`)
  - `G7` : `=30000-30000` (Valeur en cache : `0`)
  - `H7` : `=G7*F7/$H$5` (Valeur en cache : `0`)
  - `I7` : `=G7-H7` (Valeur en cache : `0`)
  - `M7` : `=I7-J7-K7+L7` (Valeur en cache : `0`)
  - `O7` : `=N7+M7` (Valeur en cache : `0`)
  - `U7` : `=T7*S7` (Valeur en cache : `0`)
  - `H8` : `=G8*F8/$H$5` (Valeur en cache : `0`)
  - `I8` : `=G8-H8` (Valeur en cache : `0`)
  - `M8` : `=I8-J8-K8+L8` (Valeur en cache : `0`)

### Onglet : `EJOURNAL`
- **Dimensions max** : 1000 lignes × 44 colonnes
- **Plage réellement utilisée** : Lignes `1..11` | Colonnes `2..41` (9 lignes occupées, 37 colonnes occupées)
- **Cellules fusionnées (1)** : `['H7:I7']`
- **Typologie des cellules** :
  - Numériques : **33**
  - Chaînes de caractères : **112**
  - Formules : **92**
  - Dates : **0**
  - Booléens : **0**
  - Vides (dans la zone) : **43763**
- **En-têtes détectés** :
  - *Ligne 1* : `C1:ETAT KARAMA XXX 2025` | `O1:=2101440/100` | `P1:=2101440/100`
  - *Ligne 2* : `B2:MATR` | `C2:NOM` | `D2:formule` | `E2:BASE` | `F2:NBABS` | `G2:ABSENCES` | `H2:NBCONGE` | `I2:TCONGE` | `J2:CONGE` | `K2:DEPLACEMENT`
  - *Ligne 3* : `B3:2.0` | `C3:RAKETAKA` | `D3:=IF(F3-H3>0,F3-H3,0)` | `E3:625000.0` | `F3:11.52` | `G3:240052.06` | `H3:11.5` | `I3:19348.12` | `J3:=H3*I3` | `L3:0.0`
  - *Ligne 4* : `B4:3.0` | `C4:RASOA` | `D4:=IF(F4-H4>0,F4-H4,0)` | `E4:620000.0` | `F4:12.52` | `G4:266890.71` | `H4:12.5` | `I4:14176.51` | `J4:=H4*I4` | `L4:0.0`
  - *Ligne 5* : `B5:4.0` | `C5:RAKOTO` | `D5:=IF(F5-H5>0,F5-H5,0)` | `E5:400000.0` | `F5:1.51` | `G5:20184.61` | `H5:1.5` | `I5:12267.84` | `J5:=H5*I5` | `L5:49831.03`
- **Échantillon de formules significatives (91 formules distinctes)** :
  - `O1` : `=2101440/100` (Valeur en cache : `21014.4`)
  - `D3` : `=IF(F3-H3>0,F3-H3,0)` (Valeur en cache : `0.02`)
  - `J3` : `=H3*I3` (Valeur en cache : `222503.38`)
  - `N3` : `=E3-G3+J3+L3+M3` (Valeur en cache : `632708.4629`)
  - `O3` : `=IF(N3/100>$P$1,$P$1,N3/100)` (Valeur en cache : `6327.084629`)
  - `P3` : `=O3` (Valeur en cache : `6327.084629`)
  - `R3` : `=N3-O3-P3-Q3` (Valeur en cache : `620054.2936`)
  - `S3` : `=100*INT(R3/100)` (Valeur en cache : `620000`)
  - `T3` : `=IF(S3<=350000,2000,IF(S3<=400000,MAX(2000,(S3-350001)*5/100),IF(S3<=500000,2500+(S3-400001)*10/100,IF(S3<=600000,2500+10000+(S3-500001)*15/100,2500+10000+15000+(S3-600001)*20/100))))` (Valeur en cache : `31499.8`)
  - `U3` : `=_xlfn.CEILING.MATH(T3,10)` (Valeur en cache : `31500`)

### Onglet : `explication `
- **Dimensions max** : 1000 lignes × 6 colonnes
- **Plage réellement utilisée** : Lignes `1..74` | Colonnes `1..6` (60 lignes occupées, 6 colonnes occupées)
- **Cellules fusionnées (0)** : `[]`
- **Typologie des cellules** :
  - Numériques : **17**
  - Chaînes de caractères : **162**
  - Formules : **10**
  - Dates : **13**
  - Booléens : **0**
  - Vides (dans la zone) : **5798**
- **En-têtes détectés** :
  - *Ligne 1* : `B1:horaire théorique TNR` | `D1:horaire théorique TMM`
  - *Ligne 2* : `A2:clockage via machine digitale` | `B2:entrée matin` | `C2:08:00:00` | `D2:entrée matin` | `E2:08:00:00`
  - *Ligne 3* : `B3:sortie matin` | `C3:12:00:00` | `D3:sortie matin` | `E3:12:00:00`
  - *Ligne 4* : `B4:entrée pm` | `C4:13:00:00` | `D4:entrée pm` | `E4:14:00:00`
  - *Ligne 5* : `B5:sortie pm` | `C5:17:00:00` | `D5:sortie pm` | `E5:18:00:00`
- **Échantillon de formules significatives (9 formules distinctes)** :
  - `C18` : `=C17-C16` (Valeur en cache : `00:30:00`)
  - `C23` : `=C21-C22` (Valeur en cache : `01:00:00`)
  - `B33` : `=BASE - ABSENCES + CONGE + DEPLACEMENT + HS + AUTIMP` (Valeur en cache : `=BASE - ABSENCES + CONGE + DEPLACEMENT + HS + AUTIMP`)
  - `B34` : `=(BASE/30) * NBABS` (Valeur en cache : `=(BASE/30) * NBABS`)
  - `B35` : `=TCONGE * NBCONGE` (Valeur en cache : `=TCONGE * NBCONGE`)
  - `B36` : `=BRUTE * 1%` (Valeur en cache : `=BRUTE * 1%`)
  - `B38` : `=BRUTE - CNAPS - OSTIE - OMSI` (Valeur en cache : `=BRUTE - CNAPS - OSTIE - OMSI`)
  - `B39` : `=SI(Imposable>250000; (Imposable-250000)*20%; 0)` (Valeur en cache : `=SI(Imposable>250000; (Imposable-250000)*20%; 0)`)
  - `B40` : `=NETI - IRSA - AVANCE + REPAS` (Valeur en cache : `=NETI - IRSA - AVANCE + REPAS`)

---

## 📁 Fichier : `model/ohatra gita fichiers-23jan26.xlsx` (OOXML (.xlsx))
- **Plages nommées (Named Ranges)** : `Aucune`
- **Nombre d'onglets (Sheets)** : `5`

### Onglet : `pointage extrait machine efa vo`
- **Dimensions max** : 1000 lignes × 59 colonnes
- **Plage réellement utilisée** : Lignes `2..43` | Colonnes `1..56` (41 lignes occupées, 54 colonnes occupées)
- **Cellules fusionnées (651)** : `['AS38:AT38', 'K33:L33', 'AU38:AV38', 'AW38:AX38', 'B12:C12', 'K35:L35', 'AU40:AV40', 'M35:N35', 'B11:F11', 'B14:C14']`
- **Typologie des cellules** :
  - Numériques : **143**
  - Chaînes de caractères : **192**
  - Formules : **227**
  - Dates : **192**
  - Booléens : **0**
  - Vides (dans la zone) : **58246**
- **En-têtes détectés** :
  - *Ligne 4* : `A4:Département` | `I4:Nom` | `T4:Département` | `AB4:Nom`
  - *Ligne 5* : `A5:Date` | `I5:Matricule` | `T5:Date` | `AB5:Matricule`
- **Échantillon de formules significatives (227 formules distinctes)** :
  - `K13` : `=D13-B13` (Valeur en cache : `1899-12-29 16:10:00.046000`)
  - `M13` : `=I13-G13` (Valeur en cache : `16:36:59.974000`)
  - `O13` : `=K13+M13` (Valeur en cache : `08:47:00.020000`)
  - `P13` : `=O13/"01:00"` (Valeur en cache : `8.783338928`)
  - `AD13` : `=W13-U13` (Valeur en cache : `05:14:59.912000`)
  - `AF13` : `=AB13-Z13` (Valeur en cache : `03:36:00.044000`)
  - `AH13` : `=AD13+AF13` (Valeur en cache : `08:50:59.956000`)
  - `AI13` : `=AH13/"01:00"` (Valeur en cache : `8.849987793`)
  - `K14` : `=D14-B14` (Valeur en cache : `1899-12-29 16:09:00.060000`)
  - `M14` : `=I14-G14` (Valeur en cache : `16:49:59.949000`)

### Onglet : `Fanazavana`
- **Dimensions max** : 1000 lignes × 6 colonnes
- **Plage réellement utilisée** : Lignes `1..32` | Colonnes `1..6` (24 lignes occupées, 6 colonnes occupées)
- **Cellules fusionnées (0)** : `[]`
- **Typologie des cellules** :
  - Numériques : **4**
  - Chaînes de caractères : **43**
  - Formules : **2**
  - Dates : **12**
  - Booléens : **0**
  - Vides (dans la zone) : **5939**
- **En-têtes détectés** :
  - *Ligne 1* : `B1:horaire théorique TNR` | `D1:horaire théorique TMM`
  - *Ligne 2* : `A2:clockage via machine digitale` | `B2:entrée matin` | `C2:08:00:00` | `D2:entrée matin` | `E2:08:00:00`
  - *Ligne 3* : `B3:sortie matin` | `C3:12:00:00` | `D3:sortie matin` | `E3:12:00:00`
  - *Ligne 4* : `B4:entrée pm` | `C4:13:00:00` | `D4:entrée pm` | `E4:14:00:00`
  - *Ligne 5* : `B5:sortie pm` | `C5:17:00:00` | `D5:sortie pm` | `E5:18:00:00`
- **Échantillon de formules significatives (2 formules distinctes)** :
  - `C19` : `=C18-C17` (Valeur en cache : `00:30:00`)
  - `C24` : `=C22-C23` (Valeur en cache : `01:00:00`)

### Onglet : `OUTPUT Colonnes E F G K L M `
- **Dimensions max** : 1000 lignes × 43 colonnes
- **Plage réellement utilisée** : Lignes `1..13` | Colonnes `2..40` (11 lignes occupées, 36 colonnes occupées)
- **Cellules fusionnées (2)** : `['H7:I7', 'D9:G9']`
- **Typologie des cellules** :
  - Numériques : **33**
  - Chaînes de caractères : **108**
  - Formules : **69**
  - Dates : **0**
  - Booléens : **0**
  - Vides (dans la zone) : **42790**
- **En-têtes détectés** :
  - *Ligne 1* : `C1:ETAT KARAMA XXX 2025` | `P1:=2101440/100` | `Q1:=2101440/100` | `AH1:VIREMENT SALAIRE OKTOBRA 2025`
  - *Ligne 2* : `B2:MATR` | `C2:NOM` | `D2:formule` | `E2:BASE` | `F2:NBABS` | `G2:ABSENCES` | `H2:NBCONGE` | `I2:TCONGE` | `J2:CONGE` | `K2:HS`
  - *Ligne 3* : `B3:2.0` | `C3:RAKETAKA` | `D3:=IF(F3-H3>0,F3-H3,0)` | `E3:625000.0` | `F3:11.52` | `G3:240052.06` | `H3:11.5` | `I3:19348.12` | `J3:=H3*I3` | `K3:0.0`
  - *Ligne 4* : `B4:3.0` | `C4:RASOA` | `D4:=IF(F4-H4>0,F4-H4,0)` | `E4:620000.0` | `F4:12.52` | `G4:266890.71` | `H4:12.5` | `I4:14176.51` | `J4:=H4*I4` | `K4:0.0`
  - *Ligne 5* : `B5:4.0` | `C5:RAKOTO` | `D5:=IF(F5-H5>0,F5-H5,0)` | `E5:400000.0` | `F5:1.51` | `G5:20184.61` | `H5:1.5` | `I5:12267.84` | `J5:=H5*I5` | `K5:49831.03`
- **Échantillon de formules significatives (68 formules distinctes)** :
  - `P1` : `=2101440/100` (Valeur en cache : `21014.4`)
  - `D3` : `=IF(F3-H3>0,F3-H3,0)` (Valeur en cache : `0.02`)
  - `J3` : `=H3*I3` (Valeur en cache : `222503.38`)
  - `O3` : `=E3-G3+J3+K3+N3` (Valeur en cache : `632708.4629`)
  - `P3` : `=IF(O3/100>$Q$1,$Q$1,O3/100)` (Valeur en cache : `6327.084629`)
  - `Q3` : `=P3` (Valeur en cache : `6327.084629`)
  - `S3` : `=O3-P3-Q3-R3` (Valeur en cache : `620054.2936`)
  - `T3` : `=100*INT(S3/100)` (Valeur en cache : `620000`)
  - `U3` : `=IF(T3<=350000,2000,IF(T3<=400000,MAX(2000,(T3-350001)*5/100),IF(T3<=500000,2500+(T3-400001)*10/100,IF(T3<=600000,2500+10000+(T3-500001)*15/100,2500+10000+15000+(T3-600001)*20/100))))` (Valeur en cache : `31499.8`)
  - `V3` : `=_xlfn.CEILING.MATH(U3,10)` (Valeur en cache : `31500`)

### Onglet : `payroll`
- **Dimensions max** : 1000 lignes × 10 colonnes
- **Plage réellement utilisée** : Lignes `1..30` | Colonnes `2..5` (29 lignes occupées, 3 colonnes occupées)
- **Cellules fusionnées (0)** : `[]`
- **Typologie des cellules** :
  - Numériques : **0**
  - Chaînes de caractères : **31**
  - Formules : **25**
  - Dates : **0**
  - Booléens : **0**
  - Vides (dans la zone) : **9944**
- **En-têtes détectés** :
  - *Ligne 1* : `B1:BULLETIN DE PAIE ` | `C1:JANOARY 26- 26DES25to25JAN26` | `E1:2 exemplaires`
  - *Ligne 3* : `B3:MATR` | `C3:='OUTPUT Colonnes E F G K L M '!B3`
  - *Ligne 4* : `B4:NOM` | `C4:='OUTPUT Colonnes E F G K L M '!C3`
  - *Ligne 5* : `B5:formule`
- **Échantillon de formules significatives (25 formules distinctes)** :
  - `C3` : `='OUTPUT Colonnes E F G K L M '!B3` (Valeur en cache : `2`)
  - `C4` : `='OUTPUT Colonnes E F G K L M '!C3` (Valeur en cache : `RAKETAKA`)
  - `C6` : `='OUTPUT Colonnes E F G K L M '!E3` (Valeur en cache : `625000`)
  - `C7` : `='OUTPUT Colonnes E F G K L M '!F3` (Valeur en cache : `11.52`)
  - `C8` : `='OUTPUT Colonnes E F G K L M '!G3` (Valeur en cache : `240052.06`)
  - `C9` : `='OUTPUT Colonnes E F G K L M '!H3` (Valeur en cache : `11.5`)
  - `C10` : `='OUTPUT Colonnes E F G K L M '!I3` (Valeur en cache : `19348.12`)
  - `C11` : `='OUTPUT Colonnes E F G K L M '!J3` (Valeur en cache : `222503.38`)
  - `C12` : `='OUTPUT Colonnes E F G K L M '!K3` (Valeur en cache : `0`)
  - `C13` : `='OUTPUT Colonnes E F G K L M '!L3` (Valeur en cache : `None`)

### Onglet : `Explication`
- **Dimensions max** : 19 lignes × 4 colonnes
- **Plage réellement utilisée** : Lignes `1..19` | Colonnes `1..4` (18 lignes occupées, 4 colonnes occupées)
- **Cellules fusionnées (0)** : `[]`
- **Typologie des cellules** :
  - Numériques : **0**
  - Chaînes de caractères : **61**
  - Formules : **11**
  - Dates : **0**
  - Booléens : **0**
  - Vides (dans la zone) : **4**
- **En-têtes détectés** :
  - *Ligne 1* : `A1:Colonne` | `B1:Formule` | `C1:Explication` | `D1:Exemple de calcul`
  - *Ligne 2* : `A2:Heures Matin (ex: K13)` | `B2:'=D13-B13` | `C2:Calcule la durée travaillée le matin (Heure de sortie - Heure d'entrée).` | `D2:12:00 - 08:00`
  - *Ligne 3* : `A3:Heures Après-midi (ex: M13)` | `B3:'=I13-G13` | `C3:Calcule la durée travaillée l'après-midi (Heure de sortie - Heure d'entrée).` | `D3:17:00 - 13:00`
  - *Ligne 4* : `A4:Total Heures (ex: O13)` | `B4:'=K13+M13` | `C4:Additionne les heures du matin et de l'après-midi pour obtenir le total journalier.` | `D4:04:00 + 04:00`
  - *Ligne 5* : `A5:Heures en Décimal (ex: P13)` | `B5:'=O13/"01:00"` | `C5:Convertit le format d'heure (HH:MM) en valeur décimale (ex: 08:30 devient 8.5).` | `D5:08:30 / 01:00`
- **Échantillon de formules significatives (11 formules distinctes)** :
  - `B8` : `=IF(F3-H3>0;F3-H3;0)` (Valeur en cache : `=IF(F3-H3>0;F3-H3;0)`)
  - `B9` : `=H3*I3` (Valeur en cache : `=H3*I3`)
  - `B10` : `=E3-G3+J3+K3+N3` (Valeur en cache : `=E3-G3+J3+K3+N3`)
  - `B11` : `=IF(O3/100>$Q$1;$Q$1;O3/100)` (Valeur en cache : `=IF(O3/100>$Q$1;$Q$1;O3/100)`)
  - `B12` : `=P3` (Valeur en cache : `=P3`)
  - `B13` : `=O3-P3-Q3-R3` (Valeur en cache : `=O3-P3-Q3-R3`)
  - `B14` : `=100*INT(S3/100)` (Valeur en cache : `=100*INT(S3/100)`)
  - `B16` : `=CEILING.MATH(U3;10)` (Valeur en cache : `=CEILING.MATH(U3;10)`)
  - `B17` : `=+S3-V3-X3-Y3+W3` (Valeur en cache : `=+S3-V3-X3-Y3+W3`)
  - `B18` : `=Z3-AA3` (Valeur en cache : `=Z3-AA3`)

---

## 📁 Fichier : `model/OHATRA KAJY DROITS CONGES-given on sept26.xls` (BIFF8 (.xls))
- **Plages nommées (Named Ranges)** : `Aucune`
- **Nombre d'onglets (Sheets)** : `1`

### Onglet : `Feuille1`
- **Dimensions max** : 14 lignes × 24 colonnes
- **Plage réellement utilisée** : Lignes `1..14` | Colonnes `1..24` (14 lignes occupées, 23 colonnes occupées)
- **Cellules fusionnées (0)** : `[]`
- **Typologie des cellules** :
  - Numériques : **232**
  - Chaînes de caractères : **35**
  - Formules : **0**
  - Dates : **0**
  - Booléens : **0**
  - Vides (dans la zone) : **69**
- **En-têtes détectés** :
  - *Ligne 1* : `R1C16:CALCUL DROIT CONGES`
  - *Ligne 2* : `R2C1:MOIS` | `R2C2:MATR` | `R2C3:NOM` | `R2C4:formule` | `R2C5:BASE` | `R2C6:NBABS` | `R2C7:ABSENCES` | `R2C8:NBCONGE` | `R2C9:TCONGE` | `R2C10:CONGE`
  - *Ligne 3* : `R3C1:1.0` | `R3C2:61.0` | `R3C3:RASOANARIVO Havana Hideric` | `R3C4:30.0` | `R3C5:285000.0` | `R3C6:7.56` | `R3C7:72039.24` | `R3C8:7.56` | `R3C9:8602.88` | `R3C10:65037.77279999999`
  - *Ligne 4* : `R4C1:2.0` | `R4C2:61.0` | `R4C3:RASOANARIVO Havana Hideric` | `R4C4:29.99` | `R4C5:300000.0` | `R4C6:0.01` | `R4C7:121.16` | `R4C10:0.0` | `R4C11:299878.84` | `R4C12:12060.21`
  - *Ligne 5* : `R5C1:3.0` | `R5C2:61.0` | `R5C3:RASOANARIVO Havana Hideric` | `R5C4:29.92` | `R5C5:300000.0` | `R5C6:2.08` | `R5C7:21159.64` | `R5C8:2.0` | `R5C9:8328.95` | `R5C10:16657.9`

---

# 2. CLASSIFICATION EXHAUSTIVE DE CHAQUE COLONNE (PHASE 2)

Conformément à la directive de certification, aucune donnée HISTORICAL_CALCULATED ou HISTORICAL_FORMULA ne peut être utilisée directement comme entrée (input) de l'oracle ou du runtime.
Seules les données RAW_INPUT, REFERENCE_INPUT et MANUAL_ADJUSTMENT constituent les véritables entrées du problème.

---

## 2.1. Classeur Paie : `model/ohatra presence svt clocker.xlsx` (Onglet `EJOURNAL`)

| Col | Nom En-tête | Rôle Métier | Classification | Statut pour l'Oracle / Runtime |
| :---: | :--- | :--- | :---: | :--- |
| **B (2)** | `MATR` | Matricule salarié | **`REFERENCE_INPUT`** | Entrée d'identification |
| **C (3)** | `NOM` | Nom du salarié | **`REFERENCE_INPUT`** | Entrée d'identification |
| **D (4)** | `formule` | Différence brute `=IF(F-H>0,F-H,0)` | **`DERIVED_LEGACY`** | Non utilisé (erreur conceptuelle legacy) |
| **E (5)** | `BASE` | Salaire de base contractuel | **`REFERENCE_INPUT`** | Entrée canonique de paie |
| **F (6)** | `NBABS` | Absence brute déclarée | **`DERIVED_LEGACY`** | Non utilisé directement (mélange heures/jours) |
| **G (7)** | `ABSENCES` | Retenue financière d'absence | **`HISTORICAL_CALCULATED`** | Sortie à vérifier contre l'oracle |
| **H (8)** | `NBCONGE` | Jours de congés payés pris | **`RAW_INPUT`** | Entrée d'imputation congés |
| **I (9)** | `TCONGE` | Taux journalier applicable | **`REFERENCE_INPUT`** | Entrée de valorisation de congé |
| **J (10)**| `CONGE` | Indemnité congé payé | **`HISTORICAL_FORMULA`** | Sortie à vérifier contre l'oracle |
| **K (11)**| `DEPLACEMENT` | Indemnité transport | **`RAW_INPUT`** | Entrée variable |
| **L (12)**| `HS` | Rémunération des heures sup | **`HISTORICAL_CALCULATED`** | Sortie recalculée via pointage brut |
| **M (13)**| `AUTIMP` | Primes imposables diverses | **`RAW_INPUT`** | Entrée variable soumise |
| **N (14)**| `BRUTE` | Salaire brut total | **`HISTORICAL_FORMULA`** | Sortie à vérifier contre l'oracle |
| **O (15)**| `CNAPS/formule`| CNaPS salariale (1% plafonné) | **`HISTORICAL_FORMULA`** | Sortie sociale à recalculer |
| **P (16)**| `OSTIE/formule`| Cotisation médicale OSTIE | **`HISTORICAL_FORMULA`** | Sortie santé (assimilation legacy O=CNaPS) |
| **Q (17)**| `OMSI/formule` | Cotisation médicale OMSI (1.5%)| **`HISTORICAL_CALCULATED`** | Sortie santé propre à TMM |
| **R (18)**| `NETI` | Salaire net avant impôt | **`HISTORICAL_FORMULA`** | Sortie intermédiaire |
| **S (19)**| `Imposable` | Base IRSA (arrondi 100 Ar) | **`HISTORICAL_FORMULA`** | Sortie fiscale |
| **T (20)**| `IRSA/formule` | Impôt progressif | **`HISTORICAL_FORMULA`** | Sortie fiscale (ancien barème) |
| **U (21)**| `IRSA Arrondi` | Arrondi supérieur à 10 | **`HISTORICAL_FORMULA`** | Sortie fiscale |
| **V (22)**| `RED ENFT` | Déduction enfants à charge | **`REFERENCE_INPUT`** | Entrée fiscale (nb enfants) |
| **W (23)**| `AVANCE` | Avance sur salaire | **`MANUAL_ADJUSTMENT`** | Entrée de retenue nette |
| **X (24)**| `avce spec` | Avance spéciale | **`MANUAL_ADJUSTMENT`** | Entrée de retenue nette |
| **Y (25)**| `NET` | Net fiscal avant panier/call | **`HISTORICAL_FORMULA`** | Sortie intermédiaire |
| **Z (26)**| `repas` | Indemnité panier repas | **`RAW_INPUT`** | Entrée non soumise |
| **AA (27)**| `net avant call` | Net avec panier avant retenues | **`HISTORICAL_FORMULA`** | Sortie intermédiaire |
| **AB (28)**| `CALL PERSO` | Retenue comm. privées | **`MANUAL_ADJUSTMENT`** | Entrée de retenue nette |
| **AC (29)**| `NET À PAYER` | Net financier final | **`HISTORICAL_FORMULA`** | Sortie à vérifier |
| **AD (30)**| `ARRONDI NET` | Arrondi dizaine Ariary | **`HISTORICAL_FORMULA`** | Sortie finale payée |

---

## 2.2. Classeur Pointage : `model/ohatra presence svt clocker.xlsx` (Onglet `pointage extrait machine`)

| Plage Colonnes | Nom En-tête | Rôle Métier | Classification |
| :---: | :--- | :--- | :---: |
| **A / T / AM** | `Date / Jour` | Date calendaire du jour | **`REFERENCE_INPUT`** |
| **B / U / AN** | `Entrée Matin` | Horodatage brut machine (ex. 07:49:59) | **`RAW_INPUT`** |
| **D / W / AP** | `Sortie Matin` | Horodatage brut machine (ex. 12:00:00) | **`RAW_INPUT`** |
| **G / Z / AS** | `Entrée Après-midi` | Horodatage brut machine (ex. 13:00:00) | **`RAW_INPUT`** |
| **I / AB / AU** | `Sortie Après-midi` | Horodatage brut machine (ex. 16:36:59) | **`RAW_INPUT`** |
| **K / AD / AW** | `Heures Matin` | Durée travaillée matin | **`HISTORICAL_FORMULA`** |
| **M / AF / AY** | `Heures Après-midi` | Durée travaillée après-midi | **`HISTORICAL_FORMULA`** |
| **O / AH / BA** | `Total Heures` | Durée journalière brute | **`HISTORICAL_FORMULA`** |
| **P / AI / BB** | `Heures Décimal` | Conversion en heures décimales | **`HISTORICAL_FORMULA`** |

---

## 2.3. Classeur Congés KAJY : `model/OHATRA KAJY DROITS CONGES-given on sept26.xls` (Feuille 1)

| Col | En-tête | Rôle Métier | Classification |
| :---: | :--- | :--- | :---: |
| **0** | `MOIS` | Numéro d'ordre du mois (1 à 12) | **`REFERENCE_INPUT`** |
| **1** | `MATR` | Matricule salarié (61.0) | **`REFERENCE_INPUT`** |
| **2** | `NOM` | Nom salarié (Havana Hideric) | **`REFERENCE_INPUT`** |
| **3** | `formule` | Présence/jours ouvrés du mois | **`RAW_INPUT`** |
| **4** | `BASE` | Salaire de base contractuel du mois | **`REFERENCE_INPUT`** |
| **5** | `NBABS` | Jours d'absence du mois | **`RAW_INPUT`** |
| **6** | `ABSENCES` | Retenue pour absence du mois | **`HISTORICAL_CALCULATED`** |
| **7** | `NBCONGE` | Jours de congés payés pris | **`RAW_INPUT`** |
| **8** | `TCONGE` | Taux journalier appliqué aux congés | **`REFERENCE_INPUT`** |
| **9** | `CONGE` | Montant de l'indemnité de congé | **`HISTORICAL_FORMULA`** |
| **11** | `HS` | Heures sup rémunérées du mois | **`RAW_INPUT`** |
| **12** | `AUTIMP` | Primes imposables du mois | **`RAW_INPUT`** |
| **13** | `BRUTE` | Salaire brut du mois | **`HISTORICAL_CALCULATED`** |
| **17** | `base totale` | Base de valorisation du droit à congé | **`HISTORICAL_CALCULATED`** |
| **18** | `nbre droit congés` | Droit acquis du mois (`2.5 * formule / 30`) | **`HISTORICAL_CALCULATED`** |
| **19** | `R*S/30` | Valorisation financière du mois | **`HISTORICAL_CALCULATED`** |
| **20** | `cumul NB JR DROIT` | Cumul progressif des droits acquis | **`HISTORICAL_CALCULATED`** |
| **21** | `Cumul Valorisation` | Cumul progressif de la valorisation | **`HISTORICAL_CALCULATED`** |
| **22** | `taux journalier (W14)` | TCONGE de clôture (`CumulVal / CumulDroits`) | **`HISTORICAL_CALCULATED`** |
