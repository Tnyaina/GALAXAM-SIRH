# PLAN DÉTAILLÉ DU MÉMOIRE DE FIN D'ÉTUDES — IT UNIVERSITY

## Option : Développement d'Applications / Génie Logiciel

### Conforme au Modèle Officiel ITU (`MémoireITU-v3r4.docx`) & Réf. Prisca Fehiarisoa (OceanTrade)

> **Candidat** : ANDRIAMALALA Rivo Ny Aina Tojonirina
> **Thème** : Conception et réalisation d'un Système d'Information des Ressources Humaines (SIRH) multi-sites et multi-filiales conforme au cadre juridique malgache
> **Entreprise d'accueil** : GALAXAM Logistics Madagascar
> **Établissement** : IT University (ITU) Andoharanofotsy — Antananarivo
> **Diplôme visé** : Licence en Informatique — Option Développement
> **Date de référence** : Session 2026

---

## Structure Normative du Manuscrit (Checklist ITU)

Conformément au guide de rédaction officiel de l'IT University :

- **Police & Mise en page** : Times New Roman / Calibri 11 ou 12 pt, interligne 1.15 ou 1.5, marges 2.5 cm.
- **Taille cible** : Entre 45 et 55 pages (hors annexes).
- **Règle d'or académique ITU** : *Aucune affirmation gratuite*. Chaque chiffre, seuil légal ou choix technologique doit citer sa source vérifiable (Loi malgache n° 2024-014, CGI, Décrets CNaPS, documentation officielle Spring/PostgreSQL/React, benchmark daté).
- **Introduction et Conclusion** : 1 page maximum chacune (règle stricte ITU).
- **Avant-propos** : Présentation obligatoire de l'IT University (1 page) et de l'entreprise d'accueil GALAXAM (1 page max).

---

## TABLES LIMINAIRES (Numérotation en chiffres romains minuscules : i à vii)

- **Page de Garde Officielle** (Logos ITU & GALAXAM, Titre, Nom étudiant, Option, Date, Composition du Jury : Président, Examinateur, Encadreur Professionnel).
- **Table des matières** (Générée automatiquement avec pagination exacte).
- **Liste des tableaux** (ex: Tableau comparatif Java/NodeJS, Tableau des tranches IRSA, Tableau des taux de cotisations, Tableau des seuils d'heures supplémentaires).
- **Liste des figures** (ex: Diagrammes de Gantt, Architecture n-tiers, Schéma entité-association, Diagrammes de séquence UML, Captures d'écrans de l'application).
- **Glossaire** (Ordre alphabétique strict) :
  - *API (Application Programming Interface)*
  - *CNaPS (Caisse Nationale de Prévoyance Sociale)*
  - *CGI (Code Général des Impôts)*
  - *FIFO (First In, First Out)*
  - *FMFP (Fonds Malgache de Formation Professionnelle)*
  - *IRSA (Impôt sur les Revenus Salariaux et Assimilés)*
  - *ISI (Impôt Synthétique Intermittent)*
  - *JWT (JSON Web Token)*
  - *OSTIE (Organisation Sanitaire Tananarivienne Inter-Entreprises)*
  - *RBAC (Role-Based Access Control)*
  - *SIRH (Système d'Information des Ressources Humaines)*
  - *SME (Salaire Minima d'Embauche)*
  - *STC (Solde de Tout Compte)*

---

## CORPS DU MÉMOIRE (Numérotation en chiffres arabes : 1 à 50+)

### Avant-propos (Pages 1 à 2)

1. **L’IT University (ITU)** :
   - Historique depuis 2011, mission d'excellence dans le secteur des TIC à Madagascar.
   - Filières de formation (Licence Développement/Web/Réseaux, Master MBDS Nice Sophia-Antipolis, Master BIHAR ESTIA).
   - Partenariats industriels et corps enseignant composé de professionnels actifs.
2. **L’Entreprise d’accueil : GALAXAM Logistics Madagascar** :
   - Historique, domaine d'activité (logistique, transport de marchandises, chaîne du froid, entreposage multi-sites : Antananarivo, Toamasina).
   - Organisation humaine : effectif en croissance, pluralité des statuts (cadres, chauffeurs longue distance, manutentionnaires, agents de quai).
   - Importance du capital humain et modernisation des outils de gestion.
3. **Remerciements (Page 3)** :
   - À l'égard des membres du jury, de l'encadreur professionnel GALAXAM, du corps professoral de l'ITU et des proches.

---

### Introduction (Page 4 — 1 page maximum obligatoire)

- **Contexte général** : Rôle stratégique de la digitalisation RH à Madagascar et complexité croissante de la législation sociale et fiscale.
- **Problématique d'entreprise** : Gestion artisanale historique sous classeurs Excel dispersés chez GALAXAM Logistics, entraînant des risques critiques :
  - Divergences de soldes de congés entre sites et contestations prud'homales.
  - Dérives de saisie de pointage (dépassements horaires non maîtrisés).
  - Risques de redressement fiscal DGI (IRSA mal ventilé) et social CNaPS (dépassement du plafond 8×SME).
- **Mission assignée durant le stage** : Conception et développement intégral d'un SIRH sur-mesure, robuste, multi-sites, multi-filiales et auditable.
- **Grandes réalisations obtenues** :
  1. Module Structure & Référentiels (32 tables de paramétrage temporel, planchers horaires par site).
  2. Module Employé, Contrat & Carrière (Cycle de vie, transitions strictes, génération contrats DOCX).
  3. Moteur de Congés par lots FIFO (décompte dynamique calendaire, blocage solde négatif, récap DAF 12 mois).
  4. Moteur de Pointage & Présence (Cycle décalé 26-25, ventilation hebdo Art. 109-110, qualification nuit/dimanche/férié).
  5. Moteur de Paie en 6 paliers (IRSA 6 tranches, CNaPS/Santé/FMFP, STC, déclarations SAFI DGI & DNTS).
  6. Sécurité & Piste d'audit légale immuable (JWT HS512, RBAC, conformité Loi n° 2014-038).
- **Structure du document** : Présentation du projet $\rightarrow$ Réalisation technique $\rightarrow$ Évaluation et bilan.

---

### Chapitre 1 : Présentation du Projet (Pages 5 à 15)

#### 1.1 Objectifs du projet

- Centraliser les dossiers collaborateurs de toutes les filiales et sites de GALAXAM.
- Automatiser le décompte de présence et le calcul de paie en supprimant les ressaisies manuelles.
- Garantir une conformité totale avec le Code du Travail malgache (**Loi n° 2024-014** du 1er juillet 2024 et **Loi n° 2003-044**) et le Code Général des Impôts (CGI LF 2026/2025).
- Fournir à la Direction Administrative et Financière (DAF) des états récapitulatifs certifiés conformes (Export DAF congés 12 mois, SAFI ODS, DNTS CNaPS, virements bancaires).

#### 1.2 Planning de réalisation (Diagramme de GANTT)

- Phase 1 : Étude de l'existant, recueil des besoins RH et audit des classeurs Excel (Mois 1).
- Phase 2 : Modélisation des données (MCD/MLD) et conception de l'architecture backend (Mois 1-2).
- Phase 3 : Développement des modules Core, Employé, Structure et Sécurité (Mois 2).
- Phase 4 : Développement des moteurs algorithmiques (Congés FIFO, Pointage temporel, Paie 6 paliers) (Mois 2-3).
- Phase 5 : Développement du frontend React, tests de non-régression (TNR) et rapprochement comptable (Mois 3-4).
- *Insérer le diagramme de GANTT commenté*.

#### 1.3 Technologies utilisées & Étude comparative obligatoire (Tableaux ITU)

- **1.3.1 Backend : Java (Spring Boot 3.x) vs Python (Django/FastAPI) vs Node.js**
  - *Tableau comparatif* : Typage statique fort, robustesse pour calculs financiers (BigDecimal), écosystème d'entreprise, performance multithread.
  - *Justification du choix* : Java 21 LTS + Spring Boot 3 garantit l'absence d'erreurs d'arrondis en virgule flottante sur la paie et offre une architecture orientée objet rigoureuse.
- **1.3.2 Persistance : Spring Data JDBC vs Hibernate / JPA**
  - *Justification académique forte* : Rejet délibéré d'Hibernate pour éviter le dirty-checking implicite et les requêtes N+1 imprévisibles sur les calculs de paie. Choix de Spring Data JDBC et `JdbcClient` pour un contrôle SQL transactionnel absolu.
- **1.3.3 Base de données : PostgreSQL 16 vs MySQL**
  - *Tableau comparatif* : Gestion native du type `JSONB` pour la piste d'audit, isolation transactionnelle Serializable/Read Committed, extensibilité, conformité ACID.
- **1.3.4 Frontend : React.js (Vite) vs Angular vs Vue.js**
  - *Tableau comparatif* : Composabilité, écosystème TailwindCSS, rapidité de build avec Vite, DOM virtuel et gestion fine du cache client.
- **1.3.5 Outils d'infrastructure et qualité** : Git, Docker, Apache POI (génération Excel DAF), Docx4j/POIXML (contrats Word), JUnit 5 (300+ tests automatisés).

---

### Chapitre 2 : Réalisation de l’Application (Pages 16 à 40)

#### 2.1 Analyse et Conception

- **2.1.1 Analyse de l'existant & diagnostic critique** :
  - Analyse des classeurs Excel historiques de GALAXAM (ex. classeurs d'Antananarivo et de Toamasina).
  - Détection des failles majeures : divisions par zéro lors de lots de congés épuisés, formules IRSA erronées ne garantissant pas le plancher de perception de 3 000 Ar, déconnexion entre pointage et paie.
- **2.1.2 Conception générale & Métriques des travaux réalisés** :
  - *Chiffrage de la réalisation logicielle (statistiques réelles vérifiées)* :
    - Nombre de tables PostgreSQL créées : **84 tables relationnelles** (dont **32 tables de référentiels `ref_*`** et **52 tables métiers** couvrant Employé, Carrière, Congés, Pointage, Paie, Déclarations et Audit).
    - Nombre de classes Java backend (Spring Boot 3) : **273 classes de production** structurées en architecture hexagonale.
    - Nombre de classes de test automatisés : **77 classes de test JUnit 5** cumulant plus de **300 cas de tests validés** (tests unitaires de domaine, intégration et simulation cycle de vie 20 ans).
    - Nombre d'interfaces et composants frontend React : **304 fichiers de composants et pages** (Vite / TailwindCSS).
    - Nombre de points d'API REST : **32 contrôleurs Spring Boot** exposés et sécurisés.
- **2.1.3 Architecture globale du système (Schéma n-tiers / hexagonal)** :
  - Frontend React $\longleftrightarrow$ REST API Spring Boot (Contrôleurs `/referentiel`, `/employes`, `/conges`, `/pointages`, `/paie`, `/auth`, `/admin`) $\longleftrightarrow$ Couche Métier pure $\longleftrightarrow$ PostgreSQL 16.
- **2.1.4 Schéma Entité-Association global (MCD / MLD de synthèse)**.

#### 2.2 Développement par fonctionnalité ou module métier

##### 2.2.1 Module 1 : Authentification, Sécurité & Piste d'Audit

- Description fonctionnelle : Sécurisation des accès et cloisonnement des données du personnel.
- Choix d'implémentation : Tokens JWT signés en **HMAC-SHA512** (conformité NIST SP 800-107), cookies refresh sécurisés `HttpOnly`, hachage des mots de passe en **BCrypt**.
- Matrice des rôles RBAC : Profils `ADMIN`, `DAF`, `RH`, `MANAGER`, `SALARIE` avec permissions granulaires (`READ`, `WRITE`, `VALIDATE`).
- Piste d'audit immuable (`audit_log`) : Traçabilité des actions sensibles en `JSONB` (`anciennes_valeurs`, `nouvelles_valeurs`, IP, horodatage) et interdiction applicative des modifications/suppressions (Loi malgache n° 2014-038).
- *Diagramme de séquence UML* : Processus de connexion, émission et rafraîchissement des tokens.
- *Capture d'écran* : Interface de connexion et journal de sécurité.

##### 2.2.2 Module 2 : Structure Organisationnelle & Référentiels d'Entreprise

- Description fonctionnelle : Modélisation des filiales juridiques, sites géographiques, départements, postes et devises.
- Innovations techniques :
  - Plancher d'arrivée valorisé directement rattaché au site (`ref_site.plancher_arrivee_actif`, `heure_entree_minimale_valorisee`) pour éviter la rémunération abusive des arrivées trop matinales.
  - Table `parametre_global` gérant 37 paramètres versionnés par plage de dates d'effet ($[date\_debut, date\_fin[$).
- *Capture d'écran* : Interface d'administration des sites et paramètres globaux.

##### 2.2.3 Module 3 : Gestion des Employés, Contrats & Carrières

- Description fonctionnelle : Dossier individuel complet (Art. 18-20 Loi 2024-014), immatriculation séquentielle automatique.
- Cycle de vie contractuel et machine à états : Validation stricte des transitions (`CarriereTransitionValidator`) : `BROUILLON` $\rightarrow$ `ACTIF` $\rightarrow$ `SUSPENDU` $\rightarrow$ `CLOTURE`.
- Génération automatisée de documents : Génération de contrats de travail Word (.docx) avec publipostage dynamique et fiches individuelles du personnel.
- *Capture d'écran* : Fiche collaborateur et modale de gestion de contrat.

##### 2.2.4 Module 4 : Moteur de Congés, Absences & Droits Réglementaires

- Description fonctionnelle : Décompte, validation et valorisation financière des congés payés et permissions exceptionnelles.
- Règles de gestion majeures :
  - **RG-CON-01** : Décompte calendaire dynamique avec exclusion paramétrable des jours fériés légaux (**Art. 127 Loi 2024-014**).
  - **RG-CON-05** : Interdiction absolue et blocage physique des soldes de congés négatifs.
  - **RG-CON-07** : Gestion du pool familial de 10 jours d'événements familiaux (**Art. 133 Loi 2024-014**) et autonomie du congé paternité de 3 jours (**Art. 134**).
  - **RG-CON-04** : Traitement de la prescription triennale (**Art. 129 Loi 2024-014**) par examen contradictoire DAF (aucun cron de purge destructive unilatérale).
- Moteur FIFO par lots de valorisation financière (`CongeLotValorisationService`) pour la comptabilité analytique.
- *Diagramme de séquence UML* : Circuit de demande, validation hiérarchique N+1 et imputation sur les lots de congés.
- *Capture d'écran* : Calendrier des absences et solde des droits.

##### 2.2.5 Module 5 : Moteur de Pointage, Présence & Gestion Temporelle

- Description fonctionnelle : Saisie quotidienne décentralisée et suivi de la durée effective du travail (**Art. 109 Loi 2024-014**).
- Spécificités d'ingénierie :
  - Cycle de paie décalé : du 26 du mois $M-1$ au 25 du mois $M$.
  - Moteur temporel orthogonal (`TemporalAttendanceEngine`) qualifiant automatiquement les heures de nuit (22h-05h, **Art. 115**), heures de dimanche (**Art. 120**) et jours fériés chômés (**Art. 122**).
  - Ventilation hebdomadaire des heures supplémentaires en tranches légitimes (HS 130% et HS 150%, **Art. 110**).
  - Ajustements de fin de période signés DAF (`pointage_ajustement_periode`) et scellement dans 4 tables de snapshots immuables.
- *Capture d'écran* : Feuille de saisie quotidienne et récapitulatif mensuel de présence.

##### 2.2.6 Module 6 : Moteur de Paie, Cotisations, IRSA & STC

- Description fonctionnelle : Pipeline de calcul de la rémunération brute et nette, cotisations sociales, fiscalité malgache et solde de tout compte.
- Pipeline d'exécution en 6 paliers mathématiques rigoureux :
  1. *Palier 1 : Brut Contractuel & Ajustements* (Base, HS 130%/150%, primes, déductions absences, indemnités congés).
  2. *Palier 2 : Cotisations Sociales* : CNaPS 1% salarial plafonné à 8×SME et 13% patronal (Décret 69-145) ; OSTIE 1% salarial et 5% patronal ; FMFP 1% patronal (**Loi 2017-025**).
  3. *Palier 3 : Détermination du Net Imposable*.
  4. *Palier 4 : Fiscalité Malgache* : Barème progressif IRSA à 6 tranches (0%, 5%, 10%, 15%, 20%, **CGI Art. 01.03.14**), déduction de 2 000 Ar par enfant à charge, minimum légal de perception de 3 000 Ar ; régime ISI à 5% (**CGI Art. 01.03.22**).
  5. *Palier 5 : Indemnités Nettes Non Imposables & Gains Nets*.
  6. *Palier 6 : Retenues de Trésorerie & Net à Payer* (Remboursement d'avances et prêts avec respect de la quotité cessible, **Art. 140 Loi 2024-014**, arrondi à la dizaine d'ariary supérieure).
- Calcul automatique du Solde de Tout Compte (STC) lors d'un départ (préavis Art. 33, licenciement Art. 35, liquidation congés Art. 131).
- Génération des déclarations officielles : Fichier SAFI DGI (ODS/CSV), bordereau CNaPS DNTS, ordre de virement interbancaire.
- *Capture d'écran* : Cockpit de campagne de paie et bulletin de paie généré.

#### 2.3 États d’Analyse et Statistiques (Exigence stricte ITU)

- **2.3.1 État n° 1 : Grand Récapitulatif Annuel des Congés DAF (12 mois)**
  - Analyse financière des passifs sociaux de l'entreprise (valorisation en Ariary des droits acquis, consommés et restants par salarié sur l'exercice).
- **2.3.2 État n° 2 : État Social Mensuel des Rémunérations & Charges Patronales**
  - Ventilation par filiale et par site de la masse salariale brute, des charges patronales (CNaPS, Santé, FMFP) et du coût total employeur.
- **2.3.3 Statistique n° 1 : Pyramide des Âges & Répartition par Genre / Ancienneté**
  - Analyse démographique du personnel de GALAXAM Logistics.
- **2.3.4 Statistique n° 2 : Évolution des Heures Supplémentaires et Taux d'Absentéisme**
  - Corrélation entre l'activité logistique saisonnière et le recours aux heures majorées (HS 130% vs HS 150%).

#### 2.4 Problèmes rencontrés et solutions apportées (Exigence stricte ITU)

- **2.4.1 Problème n° 1 : Divergence et cumul des arrondis financiers (Excel vs Moteur Java)**
  - *Problème* : Écart de quelques centimes constaté lors du rapprochement entre les calculs historiques Excel (calculés sur des valeurs intermédiaires arrondies) et le moteur Java.
  - *Solution* : Utilisation systématique de `BigDecimal` avec une précision à 4 décimales en calcul intermédiaire et application d'une stratégie d'arrondi normalisée `HALF_UP` au centime près, complétée par l'arrondi légal final à la dizaine d'Ariary.
- **2.4.2 Problème n° 2 : Effet rétroactif et falsification historique sur les données temporelles**
  - *Problème* : Risque qu'une modification de taux de cotisation en 2026 ne vienne recalculer ou altérer un bulletin de paie archivé de 2024.
  - *Solution* : Introduction de tables de snapshots immuables (`pointage_cumul_periode`, `bulletin_paie_ligne`, `tariffsnapshot`) scellées dès validation et découplage absolu entre les règles de calcul courantes et l'historique validé.
- **2.4.3 Problème n° 3 : Gestion des arrivées ultra-matinales et dérive de pointage sur site**
  - *Problème* : Des chauffeurs arrivant à 05h00 pour une prise de poste prévue à 08h00 généraient des heures supplémentaires non désirées par l'employeur.
  - *Solution* : Implémentation du plancher d'arrivée (*Earliest Paid Start*) paramétrable par site dans `ref_site`, écrêtant automatiquement l'heure d'entrée valorisée sans pénaliser la traçabilité de présence réelle.

---

### Chapitre 3 : Évaluation du Projet et Connaissances Acquises (Pages 41 à 45)

#### 3.1 Bilan pour l'entreprise GALAXAM Logistics

- **Sécurisation juridique intégrale** : Élimination complète des risques d'infraction au Code du Travail (Loi n° 2024-014) et de redressement fiscal DGI.
- **Gain de productivité massif** : Réduction du temps de traitement de la paie mensuelle de 5 jours de saisies manuelles à moins de 2 heures de contrôle automatisé.
- **Zéro litige sur les congés** : Traçabilité absolue des 10 jours de pool familial et des soldes FIFO sans contestation possible des salariés.
- **Précision comptable** : Déclarations sociales (CNaPS DNTS) et fiscales (SAFI DGI) générées en un clic au format officiel.

#### 3.2 Bilan personnel

- **Maîtrise approfondie des architectures d'entreprise** : Conception d'un système complexe n-tiers combinant Spring Boot 3, PostgreSQL et React.
- **Expertise métier acquise** : Compréhension exhaustive du droit du travail et de la fiscalité des salaires en République de Madagascar.
- **Rigueur algorithmique & Qualité logicielle** : Pratique du Test-Driven Development (TDD) avec plus de 300 tests d'intégration simulant 20 ans de vie d'entreprise.
- **Gestion des responsabilités professionnelles** : Capacité à dialoguer avec une direction générale, des responsables RH et des contrôleurs de gestion DAF.

#### 3.3 Extensions et évolutions futures de l'application

- **Application Mobile Salarié (React Native / PWA)** : Consultation des bulletins de paie électroniques et soumission des demandes de congés directement sur smartphone.
- **Pointage biométrique IoT en temps réel** : Interfaçage direct des pointeuses physiques d'ateliers et de quais via protocole MQTT / WebSockets.
- **Module Recrutement & Évaluation des compétences** : Gestion des offres d'emploi, suivi des candidatures (ATS) et entretiens annuels d'évaluation.
- **Dashboard Décisionnel / BI (Business Intelligence)** : Tableaux de bord prédictifs sur la masse salariale et analyse prévisionnelle des départs à la retraite.

---

### Conclusion (Page 46 — 1 page maximum obligatoire)

- Bilan synthétique de la réalisation du SIRH GALAXAM.
- Confirmation de l'atteinte de 100% des objectifs initiaux fixés par l'entreprise et l'IT University.
- Satisfaction de la direction générale et des utilisateurs RH.
- Impressions personnelles sur le passage du statut d'étudiant à celui d'ingénieur concepteur logiciel.
- Ouverture sur l'avenir de la transformation numérique des entreprises à Madagascar.

---

### Bibliographie & Webographie (Pages 47 à 49 — Format académique strict ITU)

#### 1. Textes de Lois et Règlements Officiels (Madagascar)

- RÉPUBLIQUE DE MADAGASCAR. *Loi n° 2024-014 du 1er juillet 2024 portant Code du Travail malgache*. Journal Officiel de la République de Madagascar.
- RÉPUBLIQUE DE MADAGASCAR. *Loi n° 2003-044 du 28 juillet 2004 portant Code du Travail*. Journal Officiel de la République de Madagascar.
- MINISTÈRE DE L'ÉCONOMIE ET DES FINANCES. *Code Général des Impôts (CGI) — Dispositions relatives à l'IRSA (Art. 01.03.01 à 01.03.24) et à l'ISI*. Direction Générale des Impôts (DGI), Loi de Finances 2025/2026.
- CAISSE NATIONALE DE PRÉVOYANCE SOCIALE (CNaPS). *Décret n° 69-145 fixant les taux et plafonds des cotisations du régime de prévoyance sociale et Décret n° 2010-386*.
- RÉPUBLIQUE DE MADAGASCAR. *Loi n° 2017-025 sur la formation professionnelle continue et contribution FMFP*.
- RÉPUBLIQUE DE MADAGASCAR. *Loi n° 2014-038 du 9 janvier 2015 sur la protection des données à caractère personnel*.

#### 2. Ouvrages et Références Académiques

- MARTIN, Robert C. *Clean Architecture: A Craftsman's Guide to Software Structure and Design*. Prentice Hall, 2017. 432 p.
- FOWLER, Martin. *Patterns of Enterprise Application Architecture*. Addison-Wesley Professional, 2002. 560 p.
- WALLS, Craig. *Spring in Action, Sixth Edition*. Manning Publications, 2022. 520 p.

#### 3. Références Techniques & Webographie (Datées selon les normes ITU)

- SPRING FRAMEWORK. *Spring Boot Reference Documentation (v3.2)* [en ligne]. Disponible sur : `https://docs.spring.io/spring-boot/docs/current/reference/html/` (consulté le 15-09-2026).
- POSTGRESQL GLOBAL DEVELOPMENT GROUP. *PostgreSQL 16 Documentation: Concurrency Control and JSON Functions* [en ligne]. Disponible sur : `https://www.postgresql.org/docs/16/` (consulté le 20-09-2026).
- REACT DOCUMENTATION. *React 18 & 19 Architecture and Hooks* [en ligne]. Disponible sur : `https://react.dev/` (consulté le 10-09-2026).
- NATIONAL INSTITUTE OF STANDARDS AND TECHNOLOGY (NIST). *SP 800-107: Recommendation for Applications Using Approved Hash Algorithms* [en ligne]. Disponible sur : `https://csrc.nist.gov/` (consulté le 25-09-2026).

---

### Annexes (Pagination : Annexe i à Annexe v)

- **Annexe 1** : Modèle de bulletin de paie officiel généré par le SIRH.
- **Annexe 2** : Extrait de la déclaration fiscale SAFI (DGI Madagascar) au format ODS.
- **Annexe 3** : Reçu légal pour Solde de Tout Compte (STC).
- **Annexe 4** : Grand Récapitulatif Annuel des Congés DAF (format Excel 12 mois).
- **Annexe 5** : Rapport d'exécution des 300+ tests automatisés JUnit (couverture de code).
