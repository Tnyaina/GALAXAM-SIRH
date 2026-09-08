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
    *   `CarriereTransitionValidator` : Contrôle des transitions d'affectation, de salaires et de postes.
    *   `CongeCalculateurEngine` : Décompte en jours calendaires, acquisition mensuelle (2.5j), bonifications d'ancienneté (+1j par 5 ans), fractionnement et scission maladie > 30 jours.
    *   `PurgeFifoEngine` : Algorithme FIFO exact d'expiration des congés acquis il y a plus de 36 mois (Art. 85).
    *   `PointageCalculateurEngine` : Règle de 0 tolérance aux retards, heures de nuit (>22h), déductions repas Tamatave (-1 000 Ar/j) et péréquation hebdomadaire 40h (compensation 1:1).
    *   `CalendrierOuvrableEngine` : Calcul des jours ouvrables, chômés et fériés légaux par site.
    *   `CalculateurPaieEngine` : Cotisations CNaPS (1%/13% plafonné à 8x SMIG), OSTIE (1%/5%), barème progressif IRSA et perception minimale (3 000 Ar).
    *   `PayrollExecutionEngine` : Moteur complet d'orchestration paie, calcul du brut, décompte d'absence horaire réel, workflow de régularisation congés DAF (`REGULARISATION_CONGE`), quotité cessible (Art. 72 - tiers légal max), avances spéciales (Art. 68 - dixième max), quittance STC et équilibre bancaire strict à la dizaine d'Ariary.
*   **Exports Réglementaires & Déclaratifs :**
    *   **État 301 IRSA SAFI / e-Hetra** : Fichier matriciel officiel DGI à 25 colonnes.
    *   **EDI ISI 5%** : Format XML normalisé (`TradXML`) et feuille tabulaire pour macro DGI.
    *   **DNTS CNaPS & FMFP** : Déclarations nominatives trimestrielles consolidées (3 mois civils).
    *   **Organismes de Santé** : Bordereaux nominatifs trimestriels pour OSTIE (Antananarivo) et OMSI (Toamasina).
*   **Backend (Accès Données & Infrastructure) :** API REST avec **Spring Boot** et **Spring Data JDBC** avec transactions explicites. La base de données PostgreSQL sert uniquement au stockage et aux projections relationnelles (vues `*CPL`), sans aucune logique de calcul en SQL (`INTERVAL`, `FILTER (WHERE)`, `CASE WHEN` éliminés au profit du code Java typé).
*   **Base de Données :** PostgreSQL 15 conteneurisé.
*   **Qualité & Tests :** **324 tests automatisés avec 100% de succès** (0 échec, 0 erreur) couvrant audits contradictoires, cas limites légaux, calculs fiscaux, STC et exports.

## 📁 Structure du Monorepo

```text
galaxam-sirh/
├── frontend/                  # Application React / Vite
│   ├── src/pages/             # Cockpit RH, Paie, Pointage, Congés, Administration
│   ├── src/components/        # Design System & Composants UI
│   └── src/utils/             # Validations et utilitaires financiers/dates
├── backend/                   # API Spring Boot (Java 17)
│   ├── src/main/java/mg/galaxam/sirh/
│   │   ├── api/               # Contrôleurs REST & DTOs
│   │   ├── model/             # Modèles de données & Services par domaine
│   │   │   ├── <domaine>/engine/  # Moteurs de calcul et règles métier Java purs
│   │   │   ├── paie/service/export/ # Générateurs d'exports légaux (SAFI, EDI, CNaPS)
│   │   ├── cron/              # Schedulers automatiques de maintenance et alertes
│   │   └── securite/          # Authentification JWT & RBAC
│   └── src/test/java/         # 324 tests automatisés (Unitaires, Intégration, Audits)
├── docs/                      # Documentation complète d'architecture, règles métier et guides
├── docker-compose.yml         # Déclaration des services (PostgreSQL)
└── README.md                  # Documentation globale du projet
```

## ⚡ Commandes Utiles

```bash
# Lancer l'environnement de base de données
docker compose up -d

# Exécuter les 324 tests backend
cd backend && mvn test

# Lancer l'API Spring Boot
cd backend && mvn spring-boot:run

# Build et validation de l'interface Frontend
cd frontend && npm run build
```