
# Documentation d'Initialisation du Backend - Projet SIRH Galaxam

## 1. Contexte et Philosophie Technologique

L'initialisation de l'API backend du Système d'Information des Ressources Humaines (SIRH) repose sur l'écosystème **Spring Boot (Java 17 LTS)** via le gestionnaire de dépendances **Maven**.

Contrairement aux approches classiques (MVC transactionnel) générant souvent un modèle de domaine anémique, l'architecture du projet est conçue autour des principes de la **Programmation Orientée Objet pure (POO)** et de l'**Architecture Hexagonale**. Ce choix impose une isolation stricte de la logique métier (cœur du domaine) vis-à-vis des détails d'infrastructure (base de données, framework web).

Les choix de dépendances opérés lors du bootstrap (Spring Initializr) reflètent cette exigence de contrôle absolu sur les comportements de l'application.

---

## 2. Dépendances Intégrées

Les modules suivants ont été sélectionnés pour constituer l'infrastructure technique du projet :

* **Spring Web :**
  * *Rôle :* Fournit le serveur web embarqué (Apache Tomcat) et les composants nécessaires pour exposer les interfaces (Ports d'entrée) sous forme d'API REST.
  * *Justification :* Permet la communication asynchrone (format JSON) avec l'interface utilisateur développée en React.
* **Spring Data JDBC :**
  * *Rôle :* Assure la couche de persistance et l'interaction avec la base de données relationnelle via le `JdbcClient` et les `CrudRepository`.
  * *Justification :* Alternative choisie délibérément face à JPA. Offre un contrôle total sur les requêtes SQL, l'absence de "Dirty Checking" (sauvegardes implicites) et de "Lazy Loading" imprédictible, garantissant des transactions maîtrisées.
* **PostgreSQL Driver :**
  * *Rôle :* Pilote natif permettant l'interopérabilité entre l'application Java et le système de gestion de base de données relationnelle PostgreSQL.
* **Validation (Jakarta Bean Validation) :**
  * *Rôle :* Assure la validation des requêtes entrantes (Data Transfer Objects).
  * *Justification :* Agit comme une barrière de sécurité dans la couche d'infrastructure (Controllers). Bloque les données malformées avant qu'elles n'atteignent le code métier, protégeant ainsi l'intégrité des entités du domaine.
* **Spring Boot DevTools :**
  * *Rôle :* Outil d'optimisation de l'environnement de développement.
  * *Justification :* Permet le rechargement à chaud (Hot Reload) des modifications du code source, accélérant considérablement le cycle d'itération lors de l'implémentation, sans impacter l'artefact de production.

---

## 3. Dépendances Délibérément Exclues

La rigueur architecturale du projet a nécessité d'écarter certaines technologies extrêmement populaires mais incompatibles avec les objectifs de qualité logicielle visés pour ce SIRH :

* **Spring Data JPA / Hibernate :**
  * *Motif d'exclusion :* Hibernate impose des contraintes anti-POO (constructeurs vides obligatoires, getters/setters exposant l'état interne) et automatise la gestion du schéma (Auto-DDL) et des transactions, masquant la réalité des accès aux données. L'approche JDBC est privilégiée pour une traduction manuelle mais explicite et performante entre le modèle objet métier et le modèle relationnel de données.
* **Lombok :**
  * *Motif d'exclusion :* Encourage la rupture de l'encapsulation via la génération systématique d'accesseurs (`@Data`, `@Setter`). L'utilisation de **Java 17** permet de remplacer élégamment le besoin de classes de transfert via l'utilisation des `Records` (structures de données immuables natives), conservant ainsi un code propre sans ajouter de dépendance externe au compilateur.
* **Docker Compose Support :**
  * *Motif d'exclusion :* Bien que la base de données PostgreSQL soit conteneurisée via Docker, la gestion du cycle de vie du conteneur est maintenue manuellement (`docker compose up -d`). Cela permet de dissocier le cycle de redémarrage de l'application Spring Boot du cycle de vie du moteur de base de données, optimisant l'expérience de développement local.

---

## 4. Conclusion sur l'Infrastructure

* [ ] Cette configuration minimaliste mais hautement contrôlée permet de reléguer Spring Boot à son rôle originel : un simple fournisseur d'infrastructure (livraison HTTP et gestion de pool de connexions). Le cœur de l'application, contenant les entités métiers et les règles de gestion des ressources humaines, reste indépendant, testable unitairement et purement orienté objet.
