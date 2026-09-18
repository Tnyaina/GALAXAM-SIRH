# CARTE DES SOURCES JURIDIQUES TCONGE & CONGÉS PAYÉS (MADAGASCAR)

**Date :** 18 Septembre 2026  
**Auteur :** Antigravity Agentic Pair Programmer  
**Objet :** Cartographie contradictoire des sources de droit applicables au calcul, à l'acquisition et à l'allocation des congés payés à Madagascar (GALAXAM-SIRH).  
**Statut :** AUDIT ONLY — Baseline technique 499 tests préservée.

---

## 1. HIÉRARCHIE FORMELLE DES SOURCES APPLICABLES

En application du droit social positif malgache et des règles de gouvernance GALAXAM, la hiérarchie stricte des sources s'établit comme suit :

```text
1. Loi malgache actuellement applicable : Loi n° 2024-014 portant Code du travail
2. Textes réglementaires d'application non contraires : Décrets et Arrêtés ministériels
3. Conventions collectives et accords professionnels applicables à l'entreprise
4. Contrat individuel de travail / Usages & Règles GALAXAM plus favorables
5. KAJY (Modèle OHATRA KAJY DROITS CONGES) = GALAXAM_CURRENT_BUSINESS_REFERENCE
6. Runtime réel exécuté (Java Spring Boot / PostgreSQL)
7. Données réelles PostgreSQL
8. Tests de caractérisation et d'intégration
9. Documentation interne historique
```

> [!IMPORTANT]
> **KAJY N'EST PAS UNE SOURCE JURIDIQUE.**  
> Le fichier Excel de référence KAJY constitue une photographie de la pratique de calcul historiquement utilisée chez GALAXAM (`GALAXAM_CURRENT_BUSINESS_REFERENCE`). Une formule présente dans KAJY n'a de valeur juridique que si elle est conforme à la loi ou si elle constitue un usage d'entreprise plus favorable au travailleur (`MORE_FAVORABLE_GALAXAM_RULE`). Si elle conduit à un résultat inférieur au minimum légal d'ordre public social, elle constitue une divergence juridique (`LEGAL_DIVERGENCE`).

---

## 2. SOURCE LÉGISLATIVE PRINCIPALE : LOI N° 2024-014

### 2.1. Entrée en vigueur et Abrogation expresse
- **Texte en vigueur :** **Loi n° 2024-014 du 14 août 2024** portant Code du travail (promulguée le 14 août 2024, publiée au *Journal Officiel de la République de Madagascar* n° 4153 du 19 août 2024).
- **Abrogation de la Loi n° 2003-044 :** L'article 394 de la Loi n° 2024-014 dispose expressément :
  > *« Sont abrogées toutes dispositions antérieures contraires à la présente loi, notamment la Loi n° 2003-044 du 28 juillet 2004 portant Code du travail. »*
- **Application immédiate :** L'article 390 dispose que le Code s'applique de plein droit à tous les contrats de travail en cours d'exécution sur le territoire de la République de Madagascar.
- **Interdiction formelle :** Il est désormais juridiquement erroné d'invoquer la Loi n° 2003-044 comme source positive sans constater que la disposition a été reprise à l'identique dans la Loi n° 2024-014.

---

## 3. CORPUS LÉGAL DES CONGÉS PAYÉS (ARTICLES CLÉS LOI 2024-014)

### Article 2 : Ordre Public Social & Principe de Faveur
> *« Les dispositions du présent Code sont d'ordre public. Elles s'appliquent de plein droit [...] sous réserve des dispositions plus favorables prévues par les conventions collectives, les accords d'entreprise ou les contrats individuels de travail. »*
- **Portée :** Tout minimum fixé par le Code (ex: 2,5 jours/mois, plancher de 1/12) est intangible à la baisse. Toute clause contractuelle ou pratique logicielle dérogatoire défavorable au travailleur est nulle de plein droit. Une pratique attribuant davantage est licite et qualifiée de `MORE_FAVORABLE_GALAXAM_RULE`.

---

### Article 88 : Composantes Variables de la Rémunération
> *« Lorsque la rémunération d'un travailleur est composée, en tout ou en partie, par des commissions, des primes, des prestations diverses ou des indemnités représentatives de ces prestations, il en est tenu compte pour le calcul de la rémunération pendant la période de congé payé, de l'indemnité de préavis, de l'indemnité de licenciement et des dommages-intérêts, dans la mesure où elles ne constituent pas un remboursement de frais.  
> Le montant à prendre en considération est la moyenne mensuelle des éléments visés à l'alinéa précédent, calculée sur une période ne dépassant pas les douze (12) mois de service ayant précédé la date d'ouverture du droit ou de rupture du contrat. »*
- **Classification :** `LEGAL_VERIFIED`
- **Impact TCONGE :** Les primes de rendement, primes de résultat, commissions, avantages en nature et majorations doivent obligatoirement entrer dans l'assiette du congé payé sur une base moyenne de 12 mois maximum.

---

### Article 125 : Droit au Congé Payé et Taux d'Acquisition
> *« Sauf dispositions plus favorables des conventions collectives ou du contrat individuel de travail, le travailleur acquiert droit au congé payé à la charge de l'employeur à raison de deux jours et demi (2,5 jours) calendaires par mois de service effectif. »*
- **Classification :** `LEGAL_VERIFIED`
- **Points fondamentaux :**
  1. L'unité légale est le **jour calendaire** (et non le jour ouvrable comme en France ou sous d'anciennes législations coloniales).
  2. Le cumul nominal sur 12 mois de service effectif est de **30 jours calendaires** ($12 \times 2,5 = 30$).
  3. L'assiette temporelle d'acquisition est le **mois de service effectif** (et non le simple écoulement du mois civil).

---

### Article 126 : Notion de Service Effectif & Périodes Assimilées
> *« Pour la détermination de la durée du congé acquis, sont assimilées à un mois de service effectif les périodes équivalentes à quatre (4) semaines ou vingt-quatre (24) jours de travail.  
> Sont également assimilées à un service effectif et ne peuvent être déduites de la durée du congé acquis :  
> 1. Les périodes de congé payé antérieur ;  
> 2. Les périodes de repos de maternité prévues par le présent Code ;  
> 3. Les périodes d'incapacité temporaire de travail résultant d'un accident du travail ou d'une maladie professionnelle ;  
> 4. Les absences pour maladie dûment constatée par le service médical du travail ou un médecin agréé, dans la limite de six (6) mois ;  
> 5. Les permissions exceptionnelles d'absence accordées à l'occasion d'événements familiaux dans la limite de dix (10) jours par an ;  
> 6. Les périodes de chômage technique et de chômage partiel autorisées ;  
> 7. Les périodes de mise en disponibilité ou de suspension du contrat prévues expressément par la réglementation ou la convention collective. »*
- **Classification :** `LEGAL_VERIFIED`
- **Impact sur le prorata :** Seules les absences non assimilées (absences injustifiées, grève non couverte, mise à pied disciplinaire sans solde, convenance personnelle non autorisée) peuvent réduire le droit mensuel.

---

### Article 130 : Jouissance, Fractionnement & Règle de Décompte des Jours Pris
> *« La jouissance du congé payé est acquise après douze (12) mois de service effectif.  
> Le congé peut être fractionné d'accord parties, sous réserve qu'une fraction continue d'au moins quinze (15) jours calendaires soit obligatoirement prise.  
> Le congé commence le jour où le travailleur aurait dû travailler s'il n'était pas en congé et se termine la veille de sa reprise de service. »*
- **Classification :** `LEGAL_VERIFIED`
- **Règle capitale du décompte :**
  $$\text{Début} = \text{Premier jour où le salarié aurait dû travailler}$$
  $$\text{Fin} = \text{Veille du jour de reprise effective}$$
  Cette disposition régit l'inclusion des week-ends et jours de repos contigus dans le débit calendaire.

---

### Article 131 : Allocation de Congé Payé (Plancher Légal Impératif)
> *« L'employeur doit verser au travailleur, pendant toute la durée du congé, une allocation au moins égale au douzième (1/12) des salaires et des divers éléments de rémunération définis par la présente loi dont le travailleur bénéficiait au cours des douze (12) mois ayant précédé la date du congé, à l'exception des remboursements de frais.  
> Cette allocation doit être versée au travailleur avant son départ en congé, sauf convention écrite contraire entre les parties. »*
- **Classification :** `LEGAL_VERIFIED`
- **Règles d'ordre public :**
  1. **Plancher d'ordre public social :** « au moins égale au douzième (1/12) ». L'employeur peut verser plus (maintien si salaire en hausse), jamais moins.
  2. **Fenêtre de référence légale :** « au cours des douze mois ayant précédé la date du congé ». C'est une fenêtre temporelle mobile axée sur la date de prise du congé.
  3. **Assiette exhaustive hors frais :** Tous salaires, HS, primes, compléments, à la seule exclusion des remboursements réels de frais.
  4. **Paiement préalable :** Exigibilité légale avant le départ en congé (sauf accord écrit de régularisation en paie mensuelle).

---

### Article 133 : Indemnité Compensatrice de Congés Payés (ICCP / STC)
> *« En cas de rupture ou d'expiration du contrat de travail avant que le travailleur n'ait pu bénéficier de la totalité des congés auxquels il avait droit, une indemnité compensatrice de congé payé, calculée sur la base des droits acquis en application des articles 125 et 131 de la présente loi, lui est obligatoirement versée.  
> Cette indemnité est due quels que soient la cause et le mode de rupture du contrat, y compris en cas de faute lourde. »*
- **Classification :** `LEGAL_VERIFIED`
- **Portée :** L'assiette et le mode de calcul de l'ICCP lors du Solde de Tout Compte (STC) sont strictement alignés sur l'allocation de congé de l'article 131, calculée sur les 12 mois précédant la date de rupture.

---

### Articles 134 et 135 : Nullité des Accords de Renonciation
> *« Toute convention prévoyant l'octroi d'une indemnité compensatrice en lieu et place du congé annuel sans rupture de contrat est nulle et de nul effet. »* (Art. 134)  
> *« Le travailleur ne peut renoncer à son droit au congé annuel, même contre rémunération. »* (Art. 135)
- **Classification :** `LEGAL_VERIFIED`
- **Conséquence :** Interdiction absolue de « monétiser » les congés payés en cours de contrat (pas de rachat de congés non pris sans rupture).

---

## 4. TEXTES RÉGLEMENTAIRES D'APPLICATION APPLICABLES

1. **Décret n° 62-150 du 28 mars 1962** relatif aux jours fériés, chômés et payés :
   - Fixe les règles de chômage obligatoire et de rémunération des jours fériés légaux.
   - Les jours fériés chômés et payés légaux survenant pendant une période de congé payé ne peuvent être imputés sur le congé annuel.
2. **Décrets annuels fixant la liste des jours fériés chômés et payés** (publiés au Journal Officiel).
3. **Décret n° 68-172 modifié par Décret n° 72-226** relatif aux heures supplémentaires et aux majorations de nuit et de dimanche :
   - Les majorations légales pour heures supplémentaires (+25%, +30%, +40%, +50%, +100%) font partie intégrante de la rémunération brute soumise à cotisations et entrent de plein droit dans la rémunération visée à l'article 131.

---

## 5. SYNTHÈSE DE CONFRONTATION DES SOURCES

| Notion | Loi 2024-014 (Source Primaire) | Pratique KAJY | Runtime SIRH Actuel | Qualification Juridique |
| :--- | :--- | :--- | :--- | :--- |
| **Droit mensuel** | 2,5 j calendaires / mois effectif | 2,5 j / base formule trentième | 2,5 j / base trentième paramétrable | `LEGAL_MATCH` (nominal 2,5j) |
| **Cumul annuel nominal** | 30 jours calendaires | 30 jours | 30 jours | `LEGAL_MATCH` |
| **Unité de compte** | Jours calendaires | Jours calendaires | Jours calendaires (RG-CONGE-01) | `LEGAL_MATCH` |
| **Périodes assimilées** | Art. 126 (Maternité, Maladie $\le 6$m, AT, Permissions) | Déduit uniquement les absences injustifiées | Déduit uniquement `absencesInjustifiees` | `LEGAL_MATCH` |
| **Assiette Rémunération** | Salaires + HS + Primes (Art. 88 & 131) | NET_BASE + HS (Exclut AUTIMP) | NET_BASE + HS (Exclut AUTIMP) | `LEGAL_DIVERGENCE` (si AUTIMP = primes) |
| **Fenêtre de valorisation** | 12 mois précédant la date du congé | Campagne annuelle fixe 12 mois | Campagne annuelle fixe 12 mois | `LEGAL_DIVERGENCE` (décalage temporel) |
| **Indemnité congés passés** | Fait partie des salaires perçus (Art. 131) | Incluse dans NET_BASE (+ CONGE) | Incluse dans NET_BASE (+ CONGE) | `LEGAL_MATCH` |
| **Décompte des jours pris** | Art. 130 : du jour dû à la veille reprise | Saisie manuelle NBCONGE | `ChronoUnit.DAYS.between + 1` | `LEGAL_INTERPRETATION` |
| **ICCP à la rupture** | Art. 133 : 1/12 des 12 mois avant rupture | Non formalisé dans KAJY | Même resolver que Paie (TCONGE M-1) | `LEGAL_DIVERGENCE` (asymétrie fenêtre) |
| **Première année** | Prorata temporis (Art. 131 & 133) | Non formalisé | Fallback Base / 30 | `MORE_FAVORABLE_GALAXAM_RULE` |
