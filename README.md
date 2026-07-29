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
*   **Gestion des Congés :** Calcul automatique des droits (2,5 jours/mois), suivi des soldes et circuit de validation par la hiérarchie.
*   **Moteur de Paie Dual :** Calcul automatisé selon le statut fiscal de l'employé (IRSA progressif à 6 tranches ou ISI 5%) et édition des bulletins individuels au format PDF.
*   **Sécurité (RBAC) :** Séparation stricte des accès entre le profil Administrateur/DAF (accès complet aux données salariales) et le profil Agent de Pointage (restreint à la saisie des présences).

## 🛠 Architecture Technique et Choix Conceptuels

Le projet est structuré en **Monorepo** et adopte une approche de conception par **Architecture Hexagonale (Ports et Adaptateurs)** couplée au Domain-Driven Design (DDD).

*   **Frontend :** React.js propulsé par Vite.
*   **Backend (Cœur Métier) :** Logique implémentée en Programmation Orientée Objet (POO) pure en **Java 17**, sans dépendance aux frameworks externes (exclusion délibérée de Lombok au profit des `Records` natifs).
*   **Backend (Infrastructure) :** API REST propulsée par **Spring Boot**. L'accès à la base de données s'effectue via **Spring Data JDBC** (rejet de l'auto-magie de JPA/Hibernate) pour garantir des transactions explicites et un contrôle total des requêtes SQL.
*   **Base de Données :** PostgreSQL.
*   **Déploiement :** Environnement conteneurisé via **Docker** et **Docker Compose** pour une installation locale autonome sur le serveur de production de l'entreprise.

## 📁 Structure du Monorepo

```text
galaxam-sirh/
├── frontend/                  # Application React / Vite
├── backend/                   # API Spring Boot (Java 17)
│   ├── src/main/java/mg/galaxam/sirh/
├── docker-compose.yml         # Déclaration des services (PostgreSQL)
├── .gitignore                 # Règles d'exclusion globales
└── README.md                  # Documentation globale du projet