# GALAXAM-SIRH

Système Intégré de Gestion des Ressources Humaines et de la Paie pour **GALAXAM LOGISTICS**.
L'application web centralise la gestion multi-filiales du personnel, simplifie le suivi des pointages et des heures supplémentaires, orchestre les droits et soldes de congés sur grand-livre immuable (ledger FIFO), et intègre un moteur de paie complet avec génération des bulletins PDF et déclaratifs légaux malagasy (IRSA, CNaPS, OSTIE/OMSI, FMFP).

---

## 📖 Contexte du Projet

Ce projet est réalisé par **ANDRIAMALALA Rivo Ny Aina Tojonirina**, étudiant à **IT University**, dans le cadre d'un stage de fin d'études de Licence Informatique & Développement (période du 06 Juillet au 06 Octobre 2026).

L'objectif fondamental est de remplacer une gestion RH historiquement dispersée sur de multiples classeurs Excel par une solution web unifiée, auditable, transactionnellement robuste et strictement conforme au cadre légal et réglementaire de Madagascar :
- **Code du Travail malgache** (Loi n° 2003-044 du 28 juillet 2004) ;
- **Durée légale du travail** (Décret n° 2007-563, base 40h/semaine = 173,33h/mois) ;
- **Réglementation fiscale et sociale** (CGI, IRSA SAFI/e-Hetra, CNaPS, FMFP, OSTIE/OMSI) ;
- **Modèle canonique d'acquisition et valorisation de congés** (`OHATRA KAJY DROITS CONGES-given on sept26.xls`).

---

## 🚀 Périmètre Fonctionnel & Modules Métier

*   **Gestion Multi-Entités & Carrières :**
    Rattachement des employés aux filiales du groupe (GALAXAM, Vina sy Asa, LMD). Historisation stricte des parcours professionnels (salaires de base, affectations de postes, sites et départements) avec maintien de la traçabilité continue de l'ancienneté.
*   **Gestion des Contrats & Statuts :**
    Prise en charge des salariés permanents (régime général IRSA + CNaPS/OSTIE), des consultants retraités (régime ISI 5% + OSTIE) et du personnel temporaire ou prestataire (régime horaire ISI 5%).
*   **Pointage & Gestion des Temps (Présence) :**
    Interface de saisie et de supervision des présences (remplaçant les exports de pointeuses physiques). Calcul automatique des heures majorées (30%, 50%, nuit, dimanche, jours fériés) pour les agents non-cadres.
*   **Congés & Absences (Certifié Sprint 3 — Verdict `CERTIFIED`) :**
    *   Acquisition mensuelle légale à **2,5 jours calendaires par mois** de service effectif (Art. 85), proratisée au trentième en cas d'absences injustifiées.
    *   **Grand-livre comptable (Ledger)** sur `mouvement_solde_conge` : traçabilité intégrale de chaque débit (`PRISE`) et crédit (`ACQUISITION`, `BONIFICATION`, `ANNULATION`, `EXPIRATION`).
    *   **Purge triennale FIFO autonome (`PurgeFifoEngine`)** : prescription des droits au bout de 36 mois conformément à l'Article 85.
    *   **Pool partagé d'événements familiaux (10j/an)** : gestion du quota partagé de l'Article 88 (mariage, naissance, décès) avec contrôle cumulatif et exemption stricte du congé de paternité (3 jours — Art. 134).
    *   **Valorisation financière canonique `TCONGE`** : source de vérité unique pour la valorisation en paie et la liquidation au Solde de Tout Compte (STC).
*   **Moteur de Paie Dual & STC :**
    Calcul automatisé selon le statut fiscal de l'employé (IRSA progressif à 6 tranches ou ISI 5%), calcul des retenues au trentième et indemnités congés, déductions d'acomptes/prêts et édition des bulletins individuels au format PDF.
*   **Sécurité & Habilitations (RBAC) :**
    Cloisonnement strict des accès par rôles (`ROLE_USER`, `ROLE_ADMIN`, `CONGE_VALIDATE`, `POINTAGE_AGENT`...) et protection des opérations sensibles (périodes de paie clôturées verrouillant toute modification rétroactive).

---

## 🛠 Architecture Technique & Choix Conceptuels

Le projet est conçu en **monorepo**. Toutes les règles de gestion et calculs critiques sont portés par le backend Java ; PostgreSQL assure le stockage relationnel, l'intégrité transactionnelle et les projections analytiques.

### Frontend
- **Framework :** React 19 propulsé par **Vite**.
- **Abonnement Backend-Driven :** Les composants React ne réimplémentent aucune règle métier. Les formulaires et boutons d'action s'appuient exclusivement sur les contrats fournis par les endpoints backend (`/simuler`, `availableActions`).
- **Organisation :** Architecture modulaire par fonctionnalités (`src/features/leave`, `src/features/administration`, `src/pages`, `src/components`).

### Backend (Java 17 / Spring Boot)
- **Zero ORM / JPA / Hibernate :** Accès aux données 100% JDBC direct natif, garantissant un contrôle rigoureux du SQL généré, des plans d'exécution et des temps de réponse.
- **Transactions Centralisées :** Utilisation systématique de `TransactionRunner` pour garantir l'atomicité complète des parcours critiques (validation, annulation, acquisition, purge).
- **Verrouillage Pessimiste (`SELECT ... FOR UPDATE`) :** Protection contre les accès concurrentiels lors de la validation des congés ou de la génération des débits de solde.
- **Moteurs Purs de Domaine :**
  - `CongeCalculateurEngine` : calculs calendaires stricts, seuils de préavis (2j court / 15j long), bonification d'ancienneté (+1j par palier de 5 ans), bascule maladie > 30 jours (Art. 13).
  - `PurgeFifoEngine` : algorithme mathématique d'imputation FIFO chronologique et prescription triennale des droits.
  - `CongeTauxApplicableService` : moteur canonique de résolution temporelle du taux journalier `TCONGE` avec convention $[date\_effet, date\_fin[$.
  - `DailyAttendanceCalculator`, `WeeklyAttendanceCalculator`, `AttendancePeriodCalculator` : composition canonique des pointages.
  - `PayrollExecutionEngine` : composition unitaire de la paie individuelle et exécution du batch mensuel.

### Base de Données (PostgreSQL 15)
- Schémas canoniques partitionnés dans `backend/conception/bdd/` (Core, Référentiels, Employés, Congés, Pointage, Paie).
- Contraintes relationnelles fortes, contraintes `CHECK`, et index partiels uniques anti-doublon (`idx_uniq_mouvement_prise_demande`, `idx_uniq_mouvement_annulation_demande`).
- Vues SQL de projection temps réel : `v_solde_conge_employe`, `v_demande_conge_cpl`, `v_pool_conge_annuel_consommation`.

---

## 📚 Cartographie de la Documentation

Pour appréhender immédiatement et exhaustivement le projet, voici les documents de référence classés par périmètre :

### 1. Ouvrages de Référence & Spécifications Métier ("Boky")
| Document | Emplacement | Périmètre & Description |
| :--- | :--- | :--- |
| **Sommaire Général Boky** | [`backend/docs/boky/INDEX.md`](backend/docs/boky/INDEX.md) | Index général des manuels techniques et fonctionnels du SIRH. |
| **Manuel Congés & Absences** | [`backend/docs/boky/module-conges-absences.md`](backend/docs/boky/module-conges-absences.md) | **Spécification juridique et mathématique complète** : Code du Travail (Art. 85 à 98, 13, 134), modèle Excel `OHATRA KAJY DROITS CONGES`, grand-livre ledger, architecture des 18 classes Java, intégrations Pointage/Paie/STC. |
| **Manuel Contrats RH** | [`backend/docs/boky/module-contrat.md`](backend/docs/boky/module-contrat.md) | Spécification des types de contrats (CDI, CDD, consultants), périodes d'essai et avenants. |
| **Manuel Employé & Carrière** | [`backend/docs/boky/module-employe.md`](backend/docs/boky/module-employe.md) | Gestion des dossiers collaborateurs, matricules, CIN, RIB et affectations. |
| **Manuel Moteur de Paie** | [`backend/docs/boky/module-paie.md`](backend/docs/boky/module-paie.md) | Pipeline de paie, assiettes de cotisations CNaPS/OSTIE, barème IRSA à 6 tranches. |
| **Réglementation Fiscale Paie** | [`backend/docs/boky/module-paie-reglementation-fiscale.md`](backend/docs/boky/module-paie-reglementation-fiscale.md) | Cadre fiscal malgache, barèmes officiels DGI et déclarations SAFI / e-Hetra. |

### 2. Rapports de Certification & Architecture
| Document | Emplacement | Périmètre & Description |
| :--- | :--- | :--- |
| **Rapport de Certification Congés** | [`CONGE_CERTIFICATION_REPORT.md`](CONGE_CERTIFICATION_REPORT.md) | **Verdict `CERTIFIED`** : Certification d'intégration réelle sur PostgreSQL (32 tests JUnit 5 + 4 tests Vitest verts, tests de concurrence `FOR UPDATE`, idempotence, Pointage, Paie, STC). |
| **Dictionnaire Métier & Scénarios** | [`docs/GUIDE_REGLES_METIER_ET_MODIFICATIONS.md`](docs/GUIDE_REGLES_METIER_ET_MODIFICATIONS.md) | Répertoire exhaustif des règles de gestion et guide pas à pas pour 19 scénarios de modification. |
| **Conception Architecture Paie** | [`docs/CONCEPTION_ARCHITECTURE_MODULE_PAIE.md`](docs/CONCEPTION_ARCHITECTURE_MODULE_PAIE.md) | Dossier technique du moteur de paie, modèle relationnel (`bulletin_paie`, `stc`) et exports. |
| **Guide Opérationnel Paie** | [`docs/GUIDE_COMPLET_MODULE_PAIE_GALAXAM.md`](docs/GUIDE_COMPLET_MODULE_PAIE_GALAXAM.md) | Guide utilisateur RH et DAF : clôture de pointage, génération de paie, acomptes, prêts et PDF. |
| **Architecture Frontend** | [`frontend/docs/ARCHITECTURE_GUIDE.md`](frontend/docs/ARCHITECTURE_GUIDE.md) | Architecture React 19 / Vite, contrats backend-driven, gestion des états et requêtes. |

---

## 📁 Structure du Monorepo

```text
GALAXAM-SIRH/
├── frontend/                                # Application Web React 19 / Vite
│   ├── src/
│   │   ├── features/leave/                  # Module Congés & Absences (Backend-Driven)
│   │   │   ├── components/                  # Modales de saisie, widgets soldes, validation
│   │   │   ├── api/congeService.js          # Client Axios vers /api/conges/*
│   │   │   └── __tests__/                   # Tests Vitest du contrat backend-driven
│   │   ├── features/administration/         # Gestion des référentiels (types, motifs, barèmes)
│   │   ├── pages/                           # Cockpit RH, Paie, Pointage, Employés
│   │   ├── components/                      # Design System & UI globale
│   │   └── utils/                           # Formatters & utilitaires dates
├── backend/                                 # API REST Spring Boot (Java 17 / Maven)
│   ├── src/main/java/mg/galaxam/sirh/
│   │   ├── conge/                           # Module Congés & Absences (18 fichiers consolidés)
│   │   │   ├── api/                         # 4 Contrôleurs REST (Demande, Solde, Ref, PeriodeRef)
│   │   │   ├── application/                 # CongeService & CongeTauxApplicableService
│   │   │   ├── domain/                      # CongeCalculateurEngine & PurgeFifoEngine
│   │   │   └── persistence/                 # Modèles JDBC directs (Demande, Mouvement, Ref, Vues)
│   │   ├── model/                           # Domaines Employés, Contrats, Pointage, Référentiels
│   │   ├── paie/                            # Moteur de Paie, STC, Cotisations, Fiscalité & Exports
│   │   ├── cron/                            # Schedulers nocturnes (Acquisition, Purge, Alertes RH)
│   │   └── securite/                        # Spring Security, JWT, Refresh Cookies & RBAC
│   ├── conception/bdd/                      # Scripts SQL ordonnancés (01 à 06) et migrations
│   ├── docs/boky/                           # Collection des manuels de référence exhaustifs
│   └── src/test/java/mg/galaxam/sirh/conge/ # Suites de certification d'intégration PostgreSQL
├── docker-compose.yml                       # Conteneur PostgreSQL 15 & services locaux
├── CONGE_CERTIFICATION_REPORT.md            # Rapport officiel de certification du module Congés
└── README.md                                # Point d'entrée et documentation principale
```

---

## ⚡ Commandes Utiles

```bash
# 1. Démarrer la base de données PostgreSQL conteneurisée
docker compose up -d

# 2. Exécuter la suite complète de tests Backend (JUnit 5 / Spring Boot)
cd backend && ./mvnw test

# 3. Exécuter les tests ciblés de certification Congés (PostgreSQL réel)
cd backend && ./mvnw test -Dtest=Conge*Test

# 4. Lancer l'API Spring Boot en local
cd backend && ./mvnw spring-boot:run

# 5. Exécuter les tests Frontend (Vitest)
cd frontend && npm test

# 6. Vérifier la qualité du code Frontend (ESLint)
cd frontend && npm run lint

# 7. Générer le bundle de production Frontend (Vite)
cd frontend && npm run build
```

---

*GALAXAM SIRH — Vérifier les paramètres légaux, les données reprises et les bulletins de référence avant chaque mise en production.*
