# NOTES DE TERRAIN & CONTEXTE DU MÉMOIRE DE FIN D'ÉTUDES
## Système d'Information des Ressources Humaines (SIRH) — GALAXAM Logistics Madagascar

> **Candidat** : ANDRIAMALALATOJO Tny Aina  
> **Diplôme** : Licence en Informatique — Option Développement (IT University)  
> **Période de Stage** : Du 06 juillet 2026 au 06 octobre 2026 (3 mois)  
> **Entreprise d'Accueil** : GALAXAM Logistics SARL  
> **Encadreur Professionnel** : Mme RALAMBOMANANA Tatamo Michele (Gérante & Directrice Administrative, Financière et RH)  
> **Encadreur Pédagogique (ITU)** : Dr RAKOTOARIMALALA Tsinjo Tony  

---

## 1. Fiche d'Identité & Profil de l'Entreprise d'Accueil

### 1.1 Informations Générales & Historique
- **Raison Sociale** : GALAXAM Logistics SARL (code filiale `GLX`).
- **Fondateur & Création** : M. RANDRASANA Eric Jean-Joël en juillet 2001 (25 ans d'expérience opérationnelle en 2026).
- **Identifiants Légaux** :
  - **NIF** : `3 000 019 43 4`
  - **STAT** : `52 296 11 2001 0 00056`
  - **RCS** : `2001B66`
  - **Siège Social** : 3e étage, Tour Forello, Tanjombato, Antananarivo (`TNR`).
- **Affiliation Professionnelle** : Membre du **GPCAD** (Groupement Professionnel des Commissionnaires Agréés en Douane).
- **Réseau International** : Partenaires logistiques sur 4 continents (États-Unis : MEBS Global Reach / New York ; Danemark : Blue Water Shipping / Copenhague ; Afrique du Sud : Intraspeed Ltd ; Chine : Réseau ports & aéroports).

### 1.2 Métiers & Activités Opérationnelles
- **Transit & Dédouanement (Customs Clearance)** : Formalités portuaires et aéroportuaires, conformité documentaire import/export.
- **Freight Forwarding** : Expéditions maritimes, aériennes et multimodales.
- **Inland Haulage & Door-to-Door** : Transport intérieur sécurisé reliant les ports, entrepôts régionaux et sites clients.
- **Distribution du Dernier Kilomètre (Last Mile Delivery)** : Traçabilité et livraison finale jusqu'au destinataire.
- **Projet Phare 2026** : Acheminement et distribution nationale des Moustiquaires Imprégnées d'Insecticide à Longue Durée d'Action (**MID**) vers les Centres de Santé de Base (**CSB**) sur l'ensemble du territoire malgache.

### 1.3 Implantation Géographique & Organisation Interne
- **3 Bureaux / Sites à Madagascar** :
  1. `TNR` : Antananarivo (Siège administratif, Tour Forello Tanjombato — horaire continu 08h00-12h30 / 13h30-17h00).
  2. `TMM` : Toamasina (Agence portuaire, coordination maritime — horaire 08h00-12h00 / 14h00-18h00).
  3. `TLE` : Toliara (Bureau Sud).
- **Structure Humaine & Département Logistics** :
  - L'entreprise ne dispose **pas de département informatique dédié**. L'étudiant est rattaché directement au **Département Logistics**, sous la tutelle directe de la gérante / DAF / RH (**Mme RALAMBOMANANA Tatamo Michele**).
  - Effectif du département logistique : **~19 collaborateurs** (sur un effectif global d'environ 25 salariés permanents et agents temporaires/déclarants).
  - Deux grandes catégories professionnelles issues du référentiel (`ref_categorie_professionnelle`) :
    - **Cadres** (DG, DAF, Chef Comptable, OPE Manager) : forfaitisés, sans heures supplémentaires courantes.
    - **Agents** (Chef Déclarant, Aide-Déclarant, Passeur de pièces, Responsable Entrepôt, Chef de zone, Manutentionnaires, Agents de transmission, Agents à tout faire, Gardiens) : soumis au pointage horaire, éligibles aux heures supplémentaires (HS 130% et 150%) et indemnités de déplacement.

---

## 2. Diagnostic Critique de l'Existant (« Avant SIRH »)

Avant l'intervention du projet, la totalité de la gestion RH était réalisée de manière artisanale au moyen de classeurs Microsoft Excel dispersés et non reliés entre eux. Ce fonctionnement manuel présentait des défaillances opérationnelles et juridiques majeures :

### 2.1 Les Défaillances Concrètes Vécues sur le Terrain

1. **Lenteur et Opacité de la Paie** :
   - L'édition et la remise des fiches de paie étaient extrêmement lentes et fastidieuses pour la DAF (chaque bulletin devait être calculé, composé et imprimé manuellement).
   - Sentiment récurrent d'incompréhension et de contestation parmi les employés (salaires perçus perçus comme « incomplets » en raison d'absences déduites sans explication, d'acomptes mal réconciliés ou d'heures supplémentaires omises).
   - Absence de justificatifs clairs remis aux salariés lors des retenues, alimentant un climat de méfiance.

2. **Divergences et Erreurs sur le Pointage** :
   - Fiches de présence sur tableurs non cadrées, saisies manuellement par les chefs de site ou d'agence (Antananarivo vs Toamasina).
   - Impossibilité de vérifier objectivement les heures réelles face aux arrivées trop matinales ou aux décalages horaires entre le transport intérieur et le travail de quai.
   - Perte de temps considérable pour la DAF lors de la collecte des heures de fin de mois.

3. **Gestion Défaillante des Droits à Congés** :
   - Calcul des droits à congés tenu « à la main » sur un fichier Excel distinct.
   - Risque de division par zéro constaté dans les formules lors de lots épuisés.
   - Impossibilité de tracer avec certitude si une absence était un congé payé légal, une permission pour événement familial ou une absence non autorisée, provoquant des litiges lors des soldes de tout compte (STC).

4. **Non-Conformité Fiscale et Risque de Redressement (LF 2026 / LF 2025)** :
   - **Non-respect du barème IRSA actualisé** : Constat flagrant sur les bulletins Excel réels que les formules n'appliquaient pas les tranches à jour de la Loi de Finances (**CGI Art. 01.03.14**) et ignoraient le plancher légal de perception minimale (**3 000 Ar**).
   - Formules CNaPS comparant les assiettes à des libellés textuels plutôt qu'au plafond légal de **8×SME** (**Décret n° 69-145**).
   - Menace permanente de redressement fiscal lors des contrôles DGI et de pénalités CNaPS.

---

## 3. Mission & Réalisations Obtenues par le Projet SIRH

Pour répondre à ces dysfonctionnements, l'objectif du stage a consisté à concevoir et développer une plateforme logicielle unique, web et centralisée :

1. **Suppression intégrale du papier et des fichiers Excel isolés** : Base de données PostgreSQL unifiée de 84 tables liant automatiquement contrat, pointage, congés et paie.
2. **Conformité Juridique Certifiée (Code du Travail 2024 & CGI 2026)** :
   - Respect strict de la **Loi n° 2024-014** (Art. 18-38 contrats, Art. 109-123 pointage/HS/nuit, Art. 124-136 congés et pool familial, Art. 137-147 paie et quotité cessible).
   - Moteur fiscal IRSA progressif à 6 tranches avec minimum de perception de 3 000 Ar.
3. **Transparence et Clarté pour les Salariés** :
   - Bulletins de paie détaillés générés en un clic avec toutes les lignes explicatives (salaire de base, HS détaillées, paniers, déplacements, déductions légales, acomptes retracés).
   - Espace de consultation individuel des droits et soldes de congés.
4. **Gain de Temps Radical pour la Direction (DAF/RH)** :
   - Automatisation du cycle mensuel de paie décalé (du 26 au 25).
   - Édition immédiate des fichiers réglementaires légaux : Déclaration **SAFI DGI** (format ODS/CSV), Déclaration **CNaPS DNTS**, ordres de virement bancaire interbancaires.
   - Piste d'audit médico-légale (`audit_log`) protégeant la DAF contre toute contestation prud'homale.
