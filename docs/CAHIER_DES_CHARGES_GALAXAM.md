# **CAHIER DES CHARGES FONCTIONNEL ET TECHNIQUE**

* **Intitulé du Projet :** Conception et Développement d’un Système Intégré de Gestion des Ressources Humaines et de la Paie
* **Organisme d’Accueil :** GALAXAM LOGISTICS
* **Rédacteur / Étudiant :** ANDRIAMALALA Rivo Ny Aina Tojonirina
* **Établissement :** IT University (Licence Informatique & Développement)
* **Période de Réalisation :** 06 Juillet 2026 – 06 Octobre 2026 (3 mois / 66 jours ouvrables)

## **1\. Contexte et Problématique du Projet**

### **1.1 Contexte de l'Entreprise**

GALAXAM LOGISTICS assure la gestion administrative et le traitement financier de son personnel. L'entreprise fonctionne selon des contraintes spécifiques :

* **Structure Multi-Entités :** Répartition du personnel sur différentes entités juridiques (ex. GALAXAM, Vina sy Asa) pour optimiser le suivi administratif et maintenir les effectifs sous la barre des 19/20 employés par structure.
* **Trois Catégories du Personnel :**

  1. Salariés permanents : Régime général (IRSA, CNAPS, OSTIE, OMSI).
  2. Consultants (+60 ans) : Retraités réengagés, soumis à l'Impôt Synthétique Intermédiaire (ISI 5%).
  3. Personnel temporaire : Rémunéré au taux horaire selon le pointage, soumis à l'ISI 5%.
* **Suivi des Présences :** Utilisation d'une pointeuse physique dont les données sont extraites manuellement via clé USB pour TMM et extraction sous excel pour TNR..

### **1.2 Problématique**

La gestion actuelle s'effectue sur plusieurs fichiers Excel indépendants. Cela provoque une dispersion des données, des risques d'erreurs lors des calculs de paie (IRSA, ISI 5%, heures supplémentaires) et une perte de temps importante lors des opérations mensuelles.

### **1.3 Objectifs**

Développer une application web unique et centralisée pour :

* Centraliser la gestion du personnel et des contrats sur l'ensemble des filiales.
* Simplifier la saisie des pointages et le calcul des heures majorées.
* Calculer automatiquement les paies (IRSA / ISI 5%) et générer les bulletins de paie en PDF.

## **2\. Utilisateurs et Profils d'Accès**

| Rôle Utilisateur              | Profil Associé           | Droits et Responsabilités                                                                                                |
| :----------------------------- | :------------------------ | :------------------------------------------------------------------------------------------------------------------------ |
| **Administrateur / DAF** | Gérante / Responsable RH | Accès complet : Gestion multi-filiales, contrats, validation des congés, calcul de la paie, édition des bulletins PDF. |
| **Agent de Pointage**    | Agent de Saisie           | Accès restreint : saisie et correction des horaires, suivi quotidien des présences.                                     |

## **3\. Périmètre des Fonctionnalités**

### **Module 0 : Authentification & Sécurité des Accès**

* **Connexion Sécurisée :** Authentification par identifiant/mot de passe pour chaque utilisateur, avec stockage des mots de passe sous forme hashée.
* **Gestion des Rôles (RBAC) :** Attribution d'un rôle unique par utilisateur définissant les droits d'accès aux modules (lecture/écriture/validation) selon la matrice définie.
* **Gestion de Session :** Déconnexion automatique après une période d'inactivité, journal des connexions (date, heure, utilisateur).
* **Confidentialité des Données :** Restriction d'accès aux données salariales et bulletins de paie strictement au profil Administrateur/DAF ; l'Agent de Pointage n'a accès qu'aux données de présence.
* **Traçabilité :** Historique des modifications sensibles (contrats, bulletins de paie, congés validés) avec horodatage et identification de l'auteur.

### **Module 1 : Gestion des Employés & Filiales**

* **Multi-Filiales :** Rattachement des employés à leur filiale (GALAXAM, Vina sy Asa) et historique des transferts internes.
* **Fiche Employé Centralisée :** Informations personnelles, matricule automatique mais ajustables (les numéros matricules actuels étant déjà enregistrés au niveau de la Cnaps et de organismes médicaux donc non modifiables), coordonnées personnelles, CIN, contact en d’urgences.
* **Gestion des Contrats :** Prise en compte des 3 statuts (Salarié permanent, Consultant \+60 ans, temporaire).
* **Site vs Filiale :** Gestion d'un référentiel Site (localisation physique de travail) indépendant de la Filiale juridique, un même site pouvant héberger du personnel de plusieurs entités.
* **Organigramme :** Attribution d'un responsable hiérarchique par employé, avec historique des changements et génération d'un organigramme.
* **Historique de carrière :** Traçabilité des évolutions de poste, salaire, site et catégorie professionnelle (Agent/Cadre) avec dates de début/fin, sans perte de l'historique.

### **Module 2 : Congés & Absences**

* **Suivi des Soldes :** Calcul automatique des droits à congés (2,5 jours/mois pour les salariés).
* **Gestion des Demandes :** Saisie et validation des congés et autorisations d'absence.
* **Liaison Paie :** Déduction automatique des absences non payées sur le bulletin.
* **Égalité des Droits :** Les consultants et le personnel temporaire bénéficient des mêmes droits à congés que les salariés permanents ; seul le régime fiscal (IRSA/ISI) diffère.
* **Typologie des Congés :** Prise en charge des différents types (congé annuel, maladie, maternité/paternité, sans solde, exceptionnel)

### **Module 3 : Suivi du Pointage et des Horaires**

* **Saisie et Gestion des Pointages :** Mise à disposition d'une interface intuitive pour la saisie manuelle et la correction des données de présence, afin de réduire les erreurs et la perte de temps liées aux fichiers Excel.
* **Moteur d'Heures Supplémentaires :** Calcul automatique des heures majorées selon le Code du travail malagasy (30%, 50%, heures de nuit 30%, dimanche 40%, jours fériés 100%).
* **Calendrier de l’entreprise :** Définition du calendrier de travail de l’entreprise, incluant les jours ouvrés, les jours de repos et les jours fériés (possibilité de rajout en cours d’année).
* **Catégorie Agent / Cadre :** Application différenciée des heures supplémentaires selon la catégorie professionnelle (les cadres n'y sont pas éligibles).
* **Indemnités de Présence :** Gestion des indemnités de repas et de déplacement rattachées au pointage journalier, avec règles configurables par site et exceptions individuelles.

### **Module 4 : Moteur de Paie Duale & Bulletins**

* **Calculs salariés (IRSA) :** Salaire de base, pointages et heures supplémentaires (30%, 50%, nuit), indemnités légales de transport et repas, cotisations sociales (CNaPS plafonnée à 8x SME, OSTIE, OMSI) et impôt progressif IRSA selon le barème officiel CGI à 6 tranches avec minimum fiscal de 2 000 Ar et réduction pour charge de famille (2 000 Ar / enfant).
* **Calcul Consultants & Prestataires Temporaires (ISI 5%) :** Rémunération au forfait ou prestations occasionnelles de services, retenue à la source de 5% au titre de l'Impôt Synthétique Intermittent (ISINT), génération du bordereau de saisie et de l'annexe XML EDI officielle pour téléversement e-Hetra.
* **Déclarations Mensuelles vs Trimestrielles (Règles Légales Madagascar) :**

  **Mensuelle (avant le 15 du mois M+1) :** Grand-Livre de Paie, bordereau officiel nominatif **État 301 IRSA** conforme au modèle DGI SAFI / e-Hetra Grandes Entreprises (25 colonnes réglementaires), annexe EDI ISI (5%), et ordre de virement bancaire interbancaire collectif aux 10 Ariary inférieurs.

  **Trimestrielle (par tranche de 3 mois civils : T1 au 30 Avril, T2 au 31 Juillet, T3 au 31 Octobre, T4 au 31 Janvier) :** Déclaration Nominative Trimestrielle des Salaires (**DNTS CNaPS**) et contribution au fonds de formation professionnelle (**FMFP 1% patronal** adossé), ainsi que les bordereaux nominatifs des organismes de santé au travail (**OSTIE, OMSI, AMIT, FUNHECE**) consolidant les mois M1, M2 et M3 de chaque collaborateur.
* **Workflow Intégré & Continuité Sans Ressaisie (Rupture Contrat ➔ Solde de Tout Compte STC) :**

  - À la clôture ou rupture d'un contrat (démission, licenciement, rupture conventionnelle, fin de contrat, départ retraite), l'action rapide **« Établir le STC »** déverse automatiquement les données contractuelles (salarié, date de départ, motif de rupture, catégorie professionnelle).
  - Le moteur STC calcule instantanément les droits légaux : salaire au prorata du dernier mois, Indemnité Compensatrice de Congés Payés (**ICCP** = solde réel x salaire / diviseur légal), indemnité compensatrice de préavis (dispense payée), indemnité légale de licenciement selon l'ancienneté réelle, et déduction automatique du solde restant dû des prêts d'entreprise.
  - Émission de la quittance libératoire et du reçu de Solde de Tout Compte avec archivage et mise à jour automatique des états administratifs.
* **Paramétrage des Barèmes & Multi-Organismes :** Gestion des taux et plafonds (CNaPS 1% salarial / 13% patronal, plafond 8x SME = 2 101 440 Ar, FMFP 1%, OSTIE/OMSI 1% salarial / 5% patronal, barème IRSA, dérogations d'indemnités) historisés par date d'application.
* **Prêts d'Entreprise & Avances :** Suivi des prêts accordés aux salariés, génération de l'échéancier mensuel, retenue automatique sur bulletin et apurement obligatoire sur Solde de Tout Compte.
* **Documentation Métier Complète :** Pour le détail exhaustif des formules mathématiques, des barèmes IRSA, du calendrier trimestriel et des 25 colonnes SAFI DGI, se référer au document officiel : [GUIDE_COMPLET_MODULE_PAIE_GALAXAM.md](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/docs/GUIDE_COMPLET_MODULE_PAIE_GALAXAM.md).

## **4\. Choix Techniques et Infrastructure**

### **4.1 Technologies Retenues**

* **Interface Utilisateur (Frontend) : React.js**
* **Serveur & Logique Métier (Backend) : Spring Boot (Java)**
* **Base de Données : PostgreSQL**
* **Déploiement Conteneurisé : Docker & Docker Compose**

### **4.2 Environnements de Déploiement**

* **Serveur de Production (GALAXAM) :** Installation locale dans les bureaux de l'entreprise sur une machine dédiée via Docker.

## **5\. Planning de Réalisation (Méthodologie Agile Scrum)**

Le projet se déroulera sur **3 mois (du 6 Juillet au 6 Octobre 2026\)**, découpés en **6 étapes (Sprints)** :

* **Sprint 1 (Analyse & Base de Données) :**Analyse des fichiers Excel et de l'existant, compréhension des règles de gestion, et création de la structure de la base de données.
* **Sprint 2 (Accès & Module RH) :** Connexion sécurisée, gestion des fiches employés, contrats et filiales.
* **Sprint 3 (Module Pointage & Présences) :** Développement de l'interface de saisie et de suivi des pointages, gestion des absences et calcul des heures de présence.
* **Sprint 4 (Congés et Heures Sup) :** Traitement des demandes de congés et calcul des majorations d'heures supplémentaires.
* **Sprint 5 (Module Paie & Bulletins) :** Calculs IRSA et ISI 5%, génération des bulletins de paie en PDF. Génération des états de cotisations trimestrielles.
* **Sprint 6 (Tests & Finalisation) :** Vérification globale avec la DAF, déploiement final et rédaction de la documentation.

## **6\. Livrables et Évolutions Futures**

### **6.1 Livrables Officiels**

1. **L'Application Web SIRH :** Système complet et fonctionnel prêt à l'emploi.
2. **La Documentation :** Guide d'utilisation pour la DAF et l'Agent de Saisie, plus le Manuel d'Exploitation Technique.
3. **Le Code Source :** Code complet du projet archivé et documenté.

### **6.2 Évolutions Futures**

* **Génération Automatique des Fichiers SAFI :** Export automatique au format macro Excel exigé par le logiciel fiscal d'État.
* **Portail Espace Employé :** Possibilité pour chaque salarié de consulter son compte et faire ses demandes de congé depuis son PC.
