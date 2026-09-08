# GALAXAM-SIRH

Développement d’un Système Intégré de Gestion des Ressources Humaines et de la Paie pour GALAXAM LOGISTICS. 
L'application web centralise la gestion multi-filiales du personnel, simplifie le suivi des pointages et des heures supplémentaires, gère les soldes de congés et intègre un moteur de paie avec génération des bulletins au format PDF.

## 📖 Contexte du Projet

Ce projet est réalisé par ANDRIAMALALA Rivo Ny Aina Tojonirina, étudiant à IT University, dans le cadre d'un stage de fin d'études de Licence Informatique & Développement (période du 06 Juillet au 06 Octobre 2026). 

L'objectif est de remplacer une gestion RH actuellement dispersée sur de multiples fichiers Excel par une solution unifiée, sécurisée et adaptée au cadre légal malgache (Code du travail, IRSA, CNaPS, OSTIE/OMSI).

## 🚀 Fonctionnalités Principales (Périmètre V1)

*   **Gestion Multi-Entités & Carrières :** Rattachement des employés à des filiales distinctes (GALAXAM, Vina sy Asa, LMD) avec calcul continu de l'effectif pour la gestion des seuils. Historisation stricte des évolutions de carrière (salaires, postes, sites) sans perte de données.
*   **Gestion des Contrats & Statuts :** Prise en charge des salariés permanents (régime IRSA + CNaPS/OSTIE), des consultants retraités (régime ISI 5% + OSTIE) et du personnel temporaire (régime ISI 5% sur taux horaire).
*   **Pointage & Heures Supplémentaires :** Interface de saisie et de correction des présences (remplaçant les exports de la pointeuse physique). Calcul automatique des heures majorées (30%, 50%, nuit, dimanche, jours fériés) pour les agents non-cadres.
*   **Gestion des Congés :** Calcul automatique des droits (2,5 jours/mois), suivi des soldes et circuit de validation par la hiérarchie avec purge légale triennale FIFO.
*   **Moteur de Paie Dual :** Calcul automatisé selon le statut fiscal de l'employé (IRSA progressif à 6 tranches ou ISI 5%) et édition des bulletins individuels au format PDF.
*   **Sécurité (RBAC) :** Séparation stricte des accès entre le profil Administrateur/DAF (accès complet aux données salariales) et le profil Agent de Pointage (restreint à la saisie des présences).

## 🛠 Architecture Technique et Choix Conceptuels

Le projet est structuré en **Monorepo** et applique les principes du **Domain-Driven Design (DDD)** et de l'architecture **Backend-Driven** avec 100% de la logique métier exécutée en code Java pur :

*   **Frontend :** React.js propulsé par Vite (Architecture 3 couches : Pages / Hooks / Components UI).
*   **Backend (Cœur Métier & Moteurs de Domaine) :** Logique implémentée en POO pure en **Java 17**, isolée dans des composants spécialisés (`mg.galaxam.sirh.model.<domaine>.engine`) :
    *   `EmployeValidationEngine` : Validation légale de l'âge minimum (16 ans), CIN 12 chiffres, coordonnées bancaires (RIB).
    *   `MatriculeGeneratorEngine` : Numérotation séquentielle et génération matricule standardisée.
    *   `ContratValidationEngine` : Gestion de la complétude contractuelle, calculs de fin de période d'essai et transitions de statuts RH.
    *   `AncienneteEngine` : Calcul unifié d'ancienneté continue multi-contrats en mémoire Java (partagé entre Supervision Carrière et STC).
    *   `CarriereTransitionValidator` : Contrôle des transitions d'affectation, de salaires et de postes.
    *   `CongeCalculateurEngine` : Décompte en jours calendaires, acquisition mensuelle (2.5j), bonifications d'ancienneté (+1j par 5 ans), fractionnement et scission maladie > 30 jours.
    *   `PurgeFifoEngine` : Algorithme FIFO exact d'expiration des congés acquis il y a plus de 36 mois (Art. 85).
    *   `PointageCalculateurEngine` : Règle de 0 tolérance aux retards, heures de nuit (>22h), déductions repas Tamatave (-1 000 Ar/j) et péréquation hebdomadaire 40h (compensation 1:1).
    *   `CalendrierOuvrableEngine` : Calcul des jours ouvrables, chômés et fériés légaux par site (injecté dans Pointage et Congés).
    *   `CalculateurPaieEngine` : Cotisations CNaPS (1%/13% plafonné à 8x SMIG), OSTIE (1%/5%), barème progressif canonique IRSA (CGI Art. 01.03.18, 5 tranches, déduction enfants, min. perception 3 000 Ar) avec parité exacte entre Paie Mensuelle et STC.
    *   `PayrollExecutionEngine` : Moteur complet d'orchestration paie, calcul du brut, décompte d'absence horaire réel, workflow de régularisation congés DAF (`REGULARISATION_CONGE`), quotité cessible (Art. 72 - tiers légal max), avances spéciales (Art. 68 - dixième max), quittance STC et équilibre bancaire strict à la dizaine d'Ariary.
*   **Exports Réglementaires & Déclaratifs :**
    *   **État 301 IRSA SAFI / e-Hetra** : Fichier matriciel officiel DGI à 25 colonnes.
    *   **EDI ISI 5%** : Format XML normalisé (`TradXML`) et feuille tabulaire pour macro DGI.
    *   **DNTS CNaPS & FMFP** : Déclarations nominatives trimestrielles consolidées (3 mois civils).
    *   **Organismes de Santé** : Bordereaux nominatifs trimestriels pour OSTIE (Antananarivo) et OMSI (Toamasina).
*   **Backend (Accès Données & Infrastructure) :** API REST avec **Spring Boot 3.3.4** et **Spring Data JDBC** avec transactions explicites. La base de données PostgreSQL sert uniquement au stockage et aux projections relationnelles (vues `*CPL`), sans aucune logique de calcul en SQL (`INTERVAL`, `FILTER (WHERE)`, `CASE WHEN` éliminés au profit du code Java typé).
*   **Base de Données & Performance :** PostgreSQL 15 conteneurisé. Migration SP8 (`26-09-16-sp8-indexes-foreign-keys.sql`) appliquée couvrant **100% des clés étrangères avec 71 index B-Tree dédiés** (0 table scan sur les jointures volumineuses).
*   **Qualité & Tests :** **328 tests automatisés avec 100% de succès** (0 échec, 0 erreur) couvrant audits contradictoires, cas limites légaux, calculs fiscaux, STC et exports. **0 erreur et 0 avertissement ESLint** côté frontend.

## 📚 Cartographie de la Documentation (Puits de Contexte pour le Repository)

Pour appréhender immédiatement et exhaustivement le projet, voici les documents Markdown de référence classés par périmètre d'expertise :

| Document | Emplacement | Périmètre & Rôle Clé |
| :--- | :--- | :--- |
| **Livre Blanc d'Architecture** | [`docs/cartographie_architecturale_sirh.md`](docs/cartographie_architecturale_sirh.md) | **Le document maître (1 300+ lignes)** : Cartographie complète Front + Back + BDD, inventaire des 44 contrôleurs REST, 14 services API, Domain Engines Java, entités Active Record, vues `*CPL` et traçabilité bidirectionnelle. |
| **Dictionnaire Métier & Scénarios** | [`docs/GUIDE_REGLES_METIER_ET_MODIFICATIONS.md`](docs/GUIDE_REGLES_METIER_ET_MODIFICATIONS.md) | **Le manuel pratique "Où modifier quoi ?"** : Répertoire exhaustif des règles de gestion (IRSA, CNaPS, OSTIE, Ancienneté, Pointage, Congés, STC, RBAC) et guide pas à pas pour 19 scénarios de modification réelle. |
| **Spécification Technique Paie** | [`docs/CONCEPTION_ARCHITECTURE_MODULE_PAIE.md`](docs/CONCEPTION_ARCHITECTURE_MODULE_PAIE.md) | **Dossier de conception du moteur de paie** : Pipelines de calcul du brut, barèmes fiscaux, déductions légales, modèle de persistance (`bulletin_paie`, `stc`), formats d'exports déclaratifs DGI/CNaPS. |
| **Guide Opérationnel Paie** | [`docs/GUIDE_COMPLET_MODULE_PAIE_GALAXAM.md`](docs/GUIDE_COMPLET_MODULE_PAIE_GALAXAM.md) | **Guide utilisateur et DAF** : Procédure de clôture de pointage, génération de la paie, acomptes, prêts, validation DAF et émission des bulletins PDF. |
| **Cahier des Charges Fonctionnel** | [`docs/CAHIER_DES_CHARGES_GALAXAM.md`](docs/CAHIER_DES_CHARGES_GALAXAM.md) | **Exigences métier & cadre juridique** : Contexte légal malgache (Code du Travail, CGI), périmètre fonctionnel des 9 modules et critères d'acceptation. |
| **Architecture Backend** | [`backend/docs/ARCHITECTURE_BACKEND.md`](backend/docs/ARCHITECTURE_BACKEND.md) | **Conception technique Java/Spring Boot** : Structure des packages `engine/`, suppression du SQL procédural, transactions, schedulers de fond et suite de 328 tests. |
| **Architecture Frontend** | [`frontend/docs/ARCHITECTURE_GUIDE.md`](frontend/docs/ARCHITECTURE_GUIDE.md) | **Conception technique React 19/Vite** : Architecture 4 couches (Pages / Hooks / Utilitaires Purs / Composants UI), source unique de dates `dateUtils.js`, gestion du state. |
| **Guide de Déploiement** | [`Deployement.md`](Deployement.md) | **Infrastructure & Sécurité Production** : Déploiement Cloud/Render, Docker Compose, cookies HTTPS Cross-Site, en-têtes CSP/HSTS et migration des 71 index BDD. |

## 📁 Structure du Monorepo

```text
galaxam-sirh/
├── frontend/                  # Application React / Vite
│   ├── src/pages/             # Cockpit RH, Paie, Pointage, Congés, Administration
│   ├── src/components/        # Design System & Composants UI (découplés en 4 couches)
│   ├── src/hooks/             # Hooks réactifs métier avec requêtes annulables
│   └── src/utils/             # Moteurs mathématiques & utilitaires pures (dateUtils, congeBusinessRules...)
├── backend/                   # API Spring Boot (Java 17)
│   ├── src/main/java/mg/galaxam/sirh/
│   │   ├── api/               # 44 Contrôleurs REST & DTOs typés
│   │   ├── model/             # Entités Active Record & Services de domaine
│   │   │   ├── <domaine>/engine/  # Moteurs de calcul et règles métier Java purs
│   │   │   ├── <domaine>/service/ # Orchestration transactionnelle métier
│   │   │   └── paie/service/export/ # Générateurs d'exports légaux (SAFI, EDI, CNaPS)
│   │   ├── cron/              # Schedulers automatiques de maintenance et alertes
│   │   └── securite/          # Authentification JWT, Refresh Cookies & RBAC
│   ├── conception/bdd/        # Scripts SQL de migration ordonnancés (dont SP8 indexation)
│   └── src/test/java/         # 328 tests automatisés (Unitaires, Intégration, Audits)
├── docs/                      # Documentation complète d'architecture, règles métier et guides
├── docker-compose.yml         # Déclaration des services (PostgreSQL 15 conteneurisé)
└── README.md                  # Point d'entrée et cartographie du projet
```

## ⚡ Commandes Utiles

```bash
# Lancer l'environnement de base de données
docker compose up -d

# Exécuter les 328 tests backend
cd backend && mvn test

# Lancer l'API Spring Boot
cd backend && mvn spring-boot:run

# Linter et validation du code Frontend (0 erreur, 0 warning)
cd frontend && npm run lint

# Build de production de l'interface Frontend
cd frontend && npm run build
```