# GALAXAM-SIRH

Système Intégré de Gestion des Ressources Humaines, du Pointage, des Congés et de la Paie pour le groupe **GALAXAM LOGISTICS**.

L'application centralise la gestion multi-filiales du personnel, le suivi opérationnel des présences et des heures majorées, l'administration des congés sur grand-livre comptable immuable (ledger FIFO), l'exécution du moteur de paie malagasy et l'édition automatique des déclaratifs légaux et fiscaux.

---

## 1. Contexte et Objectifs du Système

Ce système remplace l'ancienne gestion dispersée sur tableurs par une plateforme d'entreprise unifiée, déterministe, auditable et strictement alignée sur le cadre légal et réglementaire de Madagascar :

- **Code du Travail malgache** (Loi n° 2003-044 du 28 juillet 2004) ;
- **Réglementation de la durée du travail** (Décret n° 2007-563, base hebdomadaire de 40 heures = 173,33 heures par mois) ;
- **Réglementation fiscale et sociale** (Code Général des Impôts, barème progressif IRSA, déclarations SAFI / e-Hetra, CNaPS, FMFP, organismes sanitaires interentreprises OSTIE, AMIT, FUNHECE) ;
- **Valorisation financière des congés** : Modèle canonique `OHATRA KAJY DROITS CONGES` avec calcul du taux journalier unitaire `TCONGE` et imputation chronologique FIFO.

Le système est conçu pour une **durabilité pluriannuelle autonome** (exercices 2025, 2026, 2027 et suivants) sans nécessiter de modification de code, de duplication manuelle de constantes ou d'interventions techniques lors des changements d'année.

---

## 2. Périmètre Fonctionnel

### Gestion des Collaborateurs et Multi-Filiales
- Gestion multi-entités : GALAXAM LOGISTICS, Vina sy Asa, LMD.
- Dossiers administratifs complets : état civil, CIN, coordonnées bancaires (RIB/IBAN), personnes à charge éligibles fiscalement (règle des 21 ans).
- Historisation continue de la carrière : affectations de postes, départements, sites géographiques et évolutions des salaires de base avec conservation inaltérable de l'ancienneté.

### Gestion des Contrats de Travail et Événements RH
- Types de contrats supportés : Contrat à Durée Indéterminée (CDI), Contrat à Durée Déterminée (CDD), Consultants et Prestataires (régimes journalier ou horaire).
- Suivi du cycle de vie contractuel : gestion des périodes d'essai, renouvellements encadrés par la réglementation, ruptures conventionnelles, démissions, licenciements et départs à la retraite.
- Génération automatisée de documents officiels au format DOCX/PDF : contrats d'embauche personnalisés, attestations de travail, certificats de fin de travail, avec résolution temporelle du signataire habilité (DAF en fonction à la date de signature).

### Pointage et Gestion des Temps (GTA)
- Modalités de saisie : saisie quotidienne, saisie hebdomadaire groupée, pointage terrain via badges ou QR codes.
- Moteur de calcul des temps : détection automatique des retards, pauses, absences injustifiées et dispenses autorisées.
- Ventilation auditable des heures majorées : heures supplémentaires à 30 % (huit premières heures au-delà de 40h), heures supplémentaires à 50 % (au-delà de 48h), travail de nuit, dimanches et jours fériés chômés et payés.
- Indemnités opérationnelles : gestion des paniers repas et indemnités de déplacement/chantier rattachées aux sites d'affectation.
- Scellement mensuel : génération d'un snapshot de pointage figé (`GELE` puis `CONSOMME`), ventilé par tranches horaires auditables (`pointage_snapshot_bucket`) servant de base inviolable au calcul de la paie.

### Congés et Absences (Certification Grand-Livre FIFO)
- Acquisition légale : **2,5 jours calendaires par mois** de travail effectif, proratisée au trentième en cas d'absences injustifiées.
- Grand-livre comptable (`mouvement_solde_conge`) : chaque opération constitue une écriture signée et inaltérable (`ACQUISITION`, `PRISE`, `BONIFICATION`, `ANNULATION`, `EXPIRATION`, `REGULARISATION`).
- Traitement de fin d'exercice : purge triennale automatique des droits prescrits (36 mois conformément à l'Article 85).
- Événements familiaux : pool annuel partagé de 10 jours ouvrables (Article 88) avec contrôle cumulatif strict et exclusion du congé de paternité légal de 3 jours (Article 134).
- Résolution temporelle `TCONGE` : détermination unitaire du taux journalier applicable pour l'indemnisation de congé payé et la liquidation en Solde de Tout Compte.
- Renouvellement automatique : méthode `ensureReferencePeriod` garantissant l'ouverture idempotente de chaque exercice annuel (du 01/01/N au 31/12/N) sans duplication.

### Paie salariée, prestataires et immutabilité
- Régimes fiscaux traités :
  - **Régime Général des Salariés :** cotisations salariales et patronales CNaPS (plafonné à 8 fois le SME), FMFP (1 %), organisme de santé (1 % salarié / 5 % à 6 % employeur selon barème de site), déduction de l'IRSA progressif par tranches avec minimum de perception légal.
  - **Consultant salarié :** traité comme salarié selon son contrat, avec IRSA et cotisations applicables.
  - **Prestataire indépendant :** contrat de prestation, facture PDF et paiement hors bulletin. La retenue ISI générale est désactivée pour le démarrage 2026.
- Traitement des variables : intégration automatique des acomptes sur salaire, retenues sur prêts en cours, primes et gratifications exceptionnelles.
- Périodes de paie chevauchantes (ex. 26/12/2025 au 25/01/2026) : résolution journalière des salaires contractuels multiples au prorata temporis exact.
- Règle d'immutabilité : tout bulletin validé (`VALIDE`) est sanctuarisé contre tout recalcul automatisé silencieux. Les réouvertures requièrent une action tracée avec audit trail.
- Reproductibilité absolue : la réouverture d'un bulletin historique face à de nouveaux barèmes fiscaux ou sociaux garantit un **écart de calcul strictement nul**.

### Solde de Tout Compte (STC)
- Calcul événementiel déclenché à la date de fin de contrat ou de départ effectif.
- Décomposition : salaire au prorata du dernier mois, indemnité compensatrice de congés payés valorisée au `TCONGE`, montants de préavis/licenciement instruits selon la décision applicable. Le capital restant dû sur prêt peut être consulté, mais le moteur STC n'applique pas de retenue de prêt.
- Numérotation officielle des reçus pour solde (`STC-AAAA-XXXX`) indexée sur l'année de départ effectif.

### Déclarations Légales et Fiscales
- **IRSA & SAFI / e-Hetra :** génération de la déclaration fiscale mensuelle au format conforme aux exigences de la Direction Générale des Impôts (DGI).
- **CNaPS (DNTS) :** déclaration nominative trimestrielle des salaires et cotisations.
- **FMFP :** état récapitulatif trimestriel pour le Fonds Malgache de Formation Professionnelle.
- **Organismes Sanitaires :** états nominatifs périodiques pour OSTIE, AMIT ou FUNHECE selon l'organisme assigné à chaque site.
- **Fichiers de Virement Bancaire :** génération des fichiers d'ordres de virement conformes aux formats bancaires interbancaires.
- Principe de découplage : les déclarations agrègent les montants scellés des bulletins validés sans déclencher de recalcul sous-jacent.

---

## 3. Architecture Technique

Le projet est structuré sous forme de monorepo intégrant les composants suivants :

### Frontend
- **Socle :** React 19, Vite, Tailwind CSS et Vanilla CSS.
- **Architecture Applicative :** Découpage modulaire par domaine fonctionnel (`features/employees`, `features/attendance`, `features/leave`, `features/payroll`, `features/contracts`, `features/dashboard`).
- **Principe Backend-Driven :** L'interface utilisateur ne duplique aucune logique métier ni barème légal. Les états, montants simulés et actions autorisées sont fournis par les endpoints de l'API REST.

### Backend
- **Socle :** Java 17, Spring Boot, Spring Security (authentification par JWT et contrôle d'accès basé sur les rôles RBAC).
- **Accès aux Données :** Accès direct via JDBC natif (`java.sql.*`) avec pool de connexions HikariCP. Aucun framework ORM lourd (JPA/Hibernate) n'est employé afin de garantir la maîtrise des requêtes SQL, l'absence de requêtes N+1 et l'auditabilité des verrous de concurrence.
- **Gestion des Concurrences :** Utilisation systématique de verrous pessimistes (`SELECT ... FOR UPDATE`) sur les opérations de débit de soldes et d'allocation de périodes.
- **Tâches Automatiques & Schedulers :** Rattrapage quotidien autonome, acquisition périodique des congés, notification des alertes d'échéances de contrats et d'essais.

### Base de Données
- **Système :** PostgreSQL 15.
- **Partitionnement Logique :** Scripts SQL versionnés organisés dans `backend/conception/bdd/` couvrant les schémas Core, Référentiels, Collaborateurs, Pointages, Congés, Paie et STC.
- **Intégrité Relationnelle :** Contraintes d'exclusion temporelle (`daterange`), contraintes d'unicité partielles, clés étrangères strictes et vues de projection analytique temps réel.

---

## 4. Organisation du Répertoire

```text
GALAXAM-SIRH/
├── frontend/                                # Application Web React 19
│   ├── src/
│   │   ├── features/                        # Modules fonctionnels par domaine
│   │   │   ├── attendance/                  # Pointage, présences et feuilles de temps
│   │   │   ├── contracts/                   # Contrats, avenants et alertes d'échéances
│   │   │   ├── dashboard/                   # Tableaux de bord RH et supervision
│   │   │   ├── employees/                   # Dossiers du personnel et carrières
│   │   │   ├── leave/                       # Demandes de congés, soldes et grand-livre
│   │   │   └── payroll/                     # Paie, variables, STC, déclaratifs
│   │   ├── components/                      # Composants d'interface transverses
│   │   └── services/                        # Clients HTTP et gestion réseau
├── backend/                                 # API REST Spring Boot (Java 17)
│   ├── src/main/java/mg/galaxam/sirh/
│   │   ├── conge/                           # Domaine Congés & Absences
│   │   ├── contrat/                         # Domaine Contrats, Carrières et Documents
│   │   ├── cron/                            # Schedulers et rattrapages automatisés
│   │   ├── dashboard/                       # Indicateurs clés et reporting
│   │   ├── employe/                         # Gestion administrative du collaborateur
│   │   ├── paie/                            # Paie salariée IRSA, STC et factures prestataires distinctes
│   │   ├── pointage/                        # Saisie des temps, snapshots et indemnités
│   │   └── securite/                        # Authentification, habilitations et audit
│   ├── src/main/resources/                  # Configuration Spring et modèles de documents
│   ├── conception/bdd/                      # Schémas relationnels et scripts DDL/DML
│   └── src/test/java/mg/galaxam/sirh/       # Suites de tests unitaires et d'intégration
├── docs/                                    # Documentation technique et réglementaire
│   ├── TEMPORAL_RULES.md                    # Référentiel central des règles temporelles
│   ├── TEMPORAL_DURABILITY_AUDIT.md         # Rapport d'audit de durabilité temporelle
│   └── CAHIER_DES_CHARGES_GALAXAM.md        # Spécifications initiales du projet
├── docker-compose.yml                       # Définition des conteneurs locaux (PostgreSQL)
└── README.md                                # Document principal de présentation
```

---

## 5. Guides et Références Documentaires

Les règles de gestion, protocoles de calcul et spécifications techniques sont détaillés dans les documents suivants :

- [docs/TEMPORAL_RULES.md](docs/TEMPORAL_RULES.md) : Standardisation des règles temporelles, dates métiers canoniques, résolution des versions actives et garanties d'immutabilité.
- [docs/TEMPORAL_DURABILITY_AUDIT.md](docs/TEMPORAL_DURABILITY_AUDIT.md) : Rapport d'audit de durabilité, matrice de conformité par module et résultats des tests de non-régression.
- [CONGE_CERTIFICATION_REPORT.md](CONGE_CERTIFICATION_REPORT.md) : Rapport de certification du grand-livre des congés et de l'algorithme FIFO triennal.
- [docs/CONCEPTION_ARCHITECTURE_MODULE_PAIE.md](docs/CONCEPTION_ARCHITECTURE_MODULE_PAIE.md) : Architecture du moteur de paie et modélisation des flux financiers.
- [docs/GUIDE_COMPLET_MODULE_PAIE_GALAXAM.md](docs/GUIDE_COMPLET_MODULE_PAIE_GALAXAM.md) : Manuel d'exploitation opérationnelle à destination des gestionnaires RH et DAF.

---

## 6. Environnement et Commandes d'Exploitation

### Prérequis
- Java Development Kit (JDK) 17 ou supérieur.
- Node.js version 18 ou supérieure avec npm.
- Docker et Docker Compose.

### Commandes Backend

```bash
# Démarrage de l'instance PostgreSQL
docker compose up -d

# Compilation complète du backend
mvn -f backend/pom.xml clean compile

# Exécution de l'ensemble des tests automatisés sur la base sirh_galaxam_test
mvn -f backend/pom.xml test

# Exécution ciblée des tests de durabilité temporelle et de reproductibilité
mvn -f backend/pom.xml test -Dtest=TemporalDurabilityReproducibilityIntegrationTest

# Exécution de la simulation exhaustive du cycle de vie d'entreprise
mvn -f backend/pom.xml test -Dtest=PayrollFullEnterpriseLifecycleSimulationIntegrationTest

# Lancement de l'application Spring Boot en local
mvn -f backend/pom.xml spring-boot:run
```

### Commandes Frontend

```bash
# Installation des dépendances
cd frontend && npm install

# Lancement du serveur de développement local (Vite)
cd frontend && npm run dev

# Exécution des tests unitaires et fonctionnels (Vitest)
cd frontend && npm test

# Contrôle qualité et conformité du code (ESLint)
cd frontend && npm run lint

# Génération du bundle optimisé pour la production
cd frontend && npm run build
```

---

## 7. Directives d'Exploitation en Production

1. **Isolation des Environnements :** Les suites de tests d'intégration automatisés s'exécutent impérativement sur la base `sirh_galaxam_test`. L'exécution de scripts destructifs sur la base de production `sirh_galaxam` est formellement bloquée au niveau applicatif.
2. **Évolution Légale :** Toute révision future des barèmes officiels (revalorisation du SME, révision des tranches IRSA ou ajustement des taux de cotisations) s'effectue par insertion d'une nouvelle ligne datée (`date_debut_effet`) dans les référentiels applicables, sans modifier les enregistrements historiques.
3. **Clôture Périodique :** La clôture définitive d'une période de paie scelle l'ensemble des bulletins associés. Aucune modification d'état civil, d'affectation ou de salaire postérieur à la date de clôture ne peut rétroagir sur une période close.
